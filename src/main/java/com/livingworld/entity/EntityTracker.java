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
        private final Map<UUID, EntityData> entities = new HashMap<UUID, EntityData>();
        private final Map<UUID, AnimalHerd> herds = new HashMap<UUID, AnimalHerd>();
        private ALifeWorldData savedData;
        private long ticksUntilUpdate;
        private long lastHerdUpdateWorldTime = Long.MIN_VALUE;
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
        Set<UUID> loadedNow = new HashSet<UUID>();

        for (Entity entity : new ArrayList<Entity>(world.loadedEntityList))
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

            // Virtual offspring from older saves have no real entity to load.
            // Discard those unsupported records rather than retaining phantom life.
            if (data.isVirtual())
            {
                iterator.remove();
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

        if (state.lastHerdUpdateWorldTime == Long.MIN_VALUE
                || worldTime - state.lastHerdUpdateWorldTime >= HERD_UPDATE_INTERVAL)
        {
            HerdManager.update(state.entities, state.herds, dimension, worldTime);
            state.lastHerdUpdateWorldTime = worldTime;
        }

        HerdManager.refreshCenters(state.entities, state.herds, worldTime);

        for (Entity entity : world.loadedEntityList)
        {
            if (!(entity instanceof EntityAnimal) || entity.isDead)
            {
                continue;
            }

            EntityAnimal animal = (EntityAnimal) entity;
            EntityData animalData = state.entities.get(entity.getUniqueID());
            if (animalData == null)
            {
                continue;
            }

            AnimalDailyRoutine.update(animal, animalData);
            AnimalFoodBehavior.update(animal, animalData, world);
            AnimalForagingBehavior.update(animal, animalData);
            AnimalNaturalForagingBehavior.update(animal, animalData);
            AnimalHuntBehavior.update(animal, animalData, world, worldTime);
            AnimalBreedingBehavior.update(animal, animalData, state.entities, world, worldTime);

            AnimalHerd herd = findHerd(state.herds.values(), entity.getUniqueID());
            if (herd != null)
            {
                AnimalHerdBehavior.update(animal, animalData, herd, world, worldTime);
            }
        }

        for (Entity entity : world.loadedEntityList)
        {
            if (entity instanceof EntityVillager && !entity.isDead)
            {
                EntityData villagerData = state.entities.get(entity.getUniqueID());
                if (villagerData != null)
                {
                    VillagerBehavior.updateLoadedVillager(
                            (EntityVillager) entity, villagerData, world);
                }
            }
        }

        state.savedData.setHerds(state.herds.values());
        state.savedData.setEntities(state.entities.values());
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
        else
        {
            state.entities.putAll(state.savedData.getEntities());
            state.herds.putAll(state.savedData.getHerds());
        }

        dimensions.put(dimension, state);
        return state;
    }

    private AnimalHerd findHerd(Collection<AnimalHerd> herds, UUID memberId)
    {
        for (AnimalHerd herd : herds)
        {
            if (herd.getMembers().contains(memberId))
            {
                return herd;
            }
        }
        return null;
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
