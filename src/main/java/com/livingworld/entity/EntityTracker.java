package com.livingworld.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps lightweight records of entities encountered in a server world.
 * This is an in-memory first step; persistent storage will be added later.
 */
public class EntityTracker
{
    private static final int UPDATE_INTERVAL = 100;

    private final Map<UUID, EntityData> entities = new HashMap<UUID, EntityData>();
    private long ticksUntilUpdate;

    public void tick(World world)
    {
        if (world == null || world.isRemote)
        {
            return;
        }

        if (ticksUntilUpdate > 0)
        {
            ticksUntilUpdate--;
            return;
        }

        ticksUntilUpdate = UPDATE_INTERVAL;
        markDimensionUnloaded(world.provider.getDimension());

        for (Entity entity : world.loadedEntityList)
        {
            ResourceLocation registryName = EntityList.getKey(entity);
            String type = registryName == null
                    ? entity.getClass().getName()
                    : registryName.toString();

            UUID id = entity.getUniqueID();
            EntityData data = entities.get(id);

            if (data == null)
            {
                data = new EntityData(id, type);
                entities.put(id, data);
            }

            data.update(entity, world.getTotalWorldTime(), world.provider.getDimension());
        }
    }

    private void markDimensionUnloaded(int dimension)
    {
        for (EntityData data : entities.values())
        {
            if (data.getDimension() == dimension)
            {
                data.setLoaded(false);
            }
        }
    }

    public EntityData getEntity(UUID id)
    {
        return entities.get(id);
    }

    public Collection<EntityData> getAllEntities()
    {
        return entities.values();
    }

    public int getTrackedEntityCount()
    {
        return entities.size();
    }
}
