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

    private static class DimensionState
    {
        private final Map<UUID, EntityData> entities = new HashMap<UUID, EntityData>();
        private ALifeWorldData savedData;
        private long ticksUntilUpdate;
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
                loadedNow.add(id);
                continue;
            }

            // A previously unloaded entity has accumulated offline changes.
            if (!data.isLoaded() && !data.applyOfflineState(entity))
            {
                continue;
            }

            data.update(entity, worldTime, dimension, world);
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
        }

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
}
