package com.livingworld.entity;

import com.livingworld.world.ALifeWorldData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks entities separately for each dimension and persists their snapshots.
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
        private long lastOfflineBreedingWorldTime = Long.MIN_VALUE;
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

        for (Entity entity : world.loadedEntityList)
        {
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
                if (entity instanceof net.minecraft.entity.passive.EntityVillager)
                {
                    VillagerBehavior.updateLoadedVillager(
                            (net.minecraft.entity.passive.EntityVillager) entity, data, world);
                }
                loadedNow.add(id);
                continue;
            }

            // Advance hunger for continuously loaded animals as well as offline entities.
            if (data.isLoaded())
            {
                data.simulateOffline(worldTime);
            }

            // Apply any simulated starvation damage before refreshing the snapshot.
            if (!data.applyOfflineState(entity))
            {
                continue;
            }

            data.update(entity, worldTime, dimension, world);
            if (entity instanceof net.minecraft.entity.passive.EntityAnimal)
            {
                AnimalFoodBehavior.update(
                        (net.minecraft.entity.passive.EntityAnimal) entity, data, world);
            }
            if (entity instanceof net.minecraft.entity.passive.EntityVillager)
            {
                VillagerBehavior.updateLoadedVillager(
                        (net.minecraft.entity.passive.EntityVillager) entity, data, world);
            }
            loadedNow.add(id);
        }

        // Only entities absent from the current loaded-entity list are offline.
        for (EntityData data : state.entities.values())
        {
            if (!loadedNow.contains(data.getId()))
            {
                data.setLoaded(false);
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
            state.savedData.setHerds(state.herds.values());
        }

        // Keep the center current so followers can react to the group's movement.
        HerdManager.refreshCenters(state.entities, state.herds, worldTime);

        for (Entity entity : world.loadedEntityList)
        {
            if (entity instanceof net.minecraft.entity.passive.EntityAnimal)
            {
                net.minecraft.entity.passive.EntityAnimal animal =
                        (net.minecraft.entity.passive.EntityAnimal) entity;
                EntityData animalData = state.entities.get(entity.getUniqueID());
                if (animalData != null)
                {
                    AnimalDailyRoutine.update(animal, animalData);
                    AnimalForagingBehavior.update(animal, animalData);
                    AnimalHuntBehavior.update(animal, animalData, world, worldTime);
                    AnimalBreedingBehavior.update(
                            animal, animalData, state.entities, world, worldTime);
                }

                AnimalHerd herd = findHerd(state.herds.values(), entity.getUniqueID());
                if (herd != null)
                {
                    AnimalHerdBehavior.update(animal, herd, world, worldTime);
                }
            }
        }

        state.savedData.setHerds(state.herds.values());
        state.savedData.setEntities(state.entities.values());
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
        state.savedData = (ALifeWorldData) storage.getOrLoadData(ALifeWorldData.class, ALifeWorldData.DATA_NAME);

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
