package com.livingworld.entity;

import com.livingworld.world.ALifeWorldData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks only entities that participate in the current A-Life simulation.
 * Unloaded entities remain in saved data; records are removed only after their
 * last known chunk is loaded and the entity is confirmed absent.
 */
public class EntityTracker
{
    private static final int UPDATE_INTERVAL = 100;
    private static final int HERD_UPDATE_INTERVAL = 1200;

    private static class DimensionState
    {
        // Share the maps owned by WorldSavedData to avoid duplicate HashMap storage.
        private Map<UUID, EntityData> entities;
        private Map<UUID, AnimalHerd> herds;
        private ALifeWorldData savedData;
        private long ticksUntilUpdate;
        private long lastHerdUpdateWorldTime = Long.MIN_VALUE;
        private long lastOfflineBreedingWorldTime = Long.MIN_VALUE;

        // Reusable scratch collections avoid allocating large temporary
        // collections on every simulation update.
        private final List<Entity> loadedEntitySnapshot = new ArrayList<Entity>();
        private final Set<UUID> loadedNow = new HashSet<UUID>();
        private final Map<UUID, AnimalHerd> herdByMember = new HashMap<UUID, AnimalHerd>();
    }

    private final Map<Integer, DimensionState> dimensions = new HashMap<Integer, DimensionState>();

    public void tick(World world)
    {
        if (world == null || world.isRemote)
        {
            return;
        }

        int dimension = world.provider.getDimension();
        DimensionState state = getOrCreateState(world, dimension);

        if (state.ticksUntilUpdate > 0)
        {
            state.ticksUntilUpdate--;
            return;
        }

        state.ticksUntilUpdate = UPDATE_INTERVAL;
        long worldTime = world.getTotalWorldTime();

        // Convert virtual offspring only in chunks that are already loaded.
        OfflineSimulator.materializeVirtualOffspring(world, state.entities.values());

        // Take one stable snapshot for both passes. Behavior updates may mutate
        // the world's live entity list, so iterating the snapshot avoids CME.
        state.loadedEntitySnapshot.clear();
        state.loadedEntitySnapshot.addAll(world.loadedEntityList);
        state.loadedNow.clear();

        for (Entity entity : state.loadedEntitySnapshot)
        {
            if (!isTrackedEntity(entity) || entity.isDead)
            {
                continue;
            }

            UUID id = entity.getUniqueID();
            ResourceLocation registryName = EntityList.getKey(entity);
            String type = registryName == null
                    ? entity.getClass().getName()
                    : registryName.toString();

            EntityData data = state.entities.get(id);
            if (data == null)
            {
                data = new EntityData(id, type);
                state.entities.put(id, data);
                data.update(entity, worldTime, dimension, world);
            }
            else
            {
                boolean wasOffline = !data.isLoaded();

                // Catch up offline hunger/age before marking the entity loaded.
                data.simulateOffline(worldTime);
                if (!data.applyOfflineState(entity, wasOffline))
                {
                    continue;
                }

                data.update(entity, worldTime, dimension, world);
            }

            loadedNow.add(id);
        }

        Iterator<Map.Entry<UUID, EntityData>> iterator = state.entities.entrySet().iterator();
        while (iterator.hasNext())
        {
            EntityData data = iterator.next().getValue();
            if (loadedNow.contains(data.getId()))
            {
                continue;
            }

            data.setLoaded(false);

            // Offline starvation can kill both ordinary animals and virtual
            // offspring. Remove dead records before they can be materialized.
            if (data.isAnimal() && data.getHealth() <= 0.0F)
            {
                iterator.remove();
                continue;
            }

            // A living virtual offspring remains in saved data until its chunk
            // is naturally loaded and materialization succeeds.
            if (data.isVirtual())
            {
                continue;
            }

            // Absence from loadedEntityList alone means only "unloaded". If the
            // last known position is in a loaded chunk, absence confirms removal/death.
            BlockPos lastPosition = new BlockPos(data.getX(), data.getY(), data.getZ());
            if (world.isBlockLoaded(lastPosition))
            {
                iterator.remove();
            }
        }

        OfflineSimulator.simulate(state.entities.values(), worldTime);

        if (state.lastOfflineBreedingWorldTime == Long.MIN_VALUE
                || worldTime - state.lastOfflineBreedingWorldTime >= 1200L)
        {
            OfflineSimulator.simulateBreeding(state.entities, worldTime);
            state.lastOfflineBreedingWorldTime = worldTime;
        }

        if (state.lastHerdUpdateWorldTime == Long.MIN_VALUE
                || worldTime - state.lastHerdUpdateWorldTime >= HERD_UPDATE_INTERVAL)
        {
            HerdManager.update(state.entities, state.herds, dimension, worldTime);
            state.lastHerdUpdateWorldTime = worldTime;
        }

        HerdManager.refreshCenters(state.entities, state.herds, worldTime);

        // Build a direct membership index once instead of scanning every herd
        // for every loaded animal (which becomes expensive in populated worlds).
        state.herdByMember.clear();
        for (AnimalHerd herd : state.herds.values())
        {
            for (UUID memberId : herd.getMembers())
            {
                state.herdByMember.put(memberId, herd);
            }
        }

        // One pass over loaded entities handles both animals and villagers.
        // Iterate over a snapshot: behavior updates below may cause entities
        // to be added to or removed from the world's live entity list.
        for (Entity entity : state.loadedEntitySnapshot)
        {
            if (entity.isDead)
            {
                continue;
            }

            EntityData entityData = state.entities.get(entity.getUniqueID());
            if (entityData == null)
            {
                continue;
            }

            if (entity instanceof EntityAnimal)
            {
                EntityAnimal animal = (EntityAnimal) entity;
                AnimalDailyRoutine.update(animal, entityData);
                AnimalFoodBehavior.update(animal, entityData, world);
                AnimalForagingBehavior.update(animal, entityData);
                AnimalNaturalForagingBehavior.update(animal, entityData);
                AnimalHuntBehavior.update(animal, entityData, world, worldTime);
                AnimalBreedingBehavior.update(animal, entityData, state.entities, world, worldTime);

                AnimalHerd herd = state.herdByMember.get(entity.getUniqueID());
                if (herd != null)
                {
                    AnimalHerdBehavior.update(animal, entityData, herd, world, worldTime);
                }
            }
            else if (entity instanceof EntityVillager)
            {
                VillagerBehavior.updateLoadedVillager(
                        (EntityVillager) entity, entityData, world);
            }
        }

        // Release entity references held by the reusable snapshot. Its backing
        // array remains reusable without keeping unloaded entities reachable.
        state.loadedEntitySnapshot.clear();

        // The maps are shared with WorldSavedData; only mark them dirty.
        state.savedData.markSimulationDataDirty();
    }

    private boolean isTrackedEntity(Entity entity)
    {
        return entity instanceof EntityAnimal || entity instanceof EntityVillager;
    }

    private DimensionState getOrCreateState(World world, int dimension)
    {
        DimensionState state = dimensions.get(dimension);
        if (state != null)
        {
            return state;
        }

        state = new DimensionState();
        MapStorage storage = world.getPerWorldStorage();
        state.savedData = (ALifeWorldData) storage.getOrLoadData(
                ALifeWorldData.class, ALifeWorldData.DATA_NAME);

        if (state.savedData == null)
        {
            state.savedData = new ALifeWorldData();
            storage.setData(ALifeWorldData.DATA_NAME, state.savedData);
        }

        // Keep one authoritative copy of each map. EntityData and AnimalHerd
        // instances are mutable, so the tracker and WorldSavedData can share them.
        state.entities = state.savedData.getEntities();
        state.herds = state.savedData.getHerds();

        dimensions.put(dimension, state);
        return state;
    }

    public EntityData getEntity(int dimension, UUID id)
    {
        DimensionState state = dimensions.get(dimension);
        return state == null ? null : state.entities.get(id);
    }

    public Collection<EntityData> getAllEntities(int dimension)
    {
        DimensionState state = dimensions.get(dimension);
        return state == null ? Collections.<EntityData>emptyList() : state.entities.values();
    }

    public int getTrackedEntityCount(int dimension)
    {
        DimensionState state = dimensions.get(dimension);
        return state == null ? 0 : state.entities.size();
    }

    public Collection<AnimalHerd> getAllHerds(int dimension)
    {
        DimensionState state = dimensions.get(dimension);
        return state == null ? Collections.<AnimalHerd>emptyList() : state.herds.values();
    }

    public int getHerdCount(int dimension)
    {
        DimensionState state = dimensions.get(dimension);
        return state == null ? 0 : state.herds.size();
    }
}
