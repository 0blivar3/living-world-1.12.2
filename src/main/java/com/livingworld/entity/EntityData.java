package com.livingworld.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

import java.util.UUID;

/**
 * Lightweight snapshot of an entity for the A-Life simulation.
 */
public class EntityData
{
    private final UUID id;
    private final String entityType;

    private int dimension;
    private double x;
    private double y;
    private double z;
    private float health;
    private boolean loaded;
    private long lastUpdateWorldTime;

    public EntityData(UUID id, String entityType)
    {
        this.id = id;
        this.entityType = entityType;
    }

    public void update(Entity entity, long worldTime, int dimension)
    {
        this.dimension = dimension;
        this.x = entity.posX;
        this.y = entity.posY;
        this.z = entity.posZ;
        this.health = entity instanceof EntityLivingBase
                ? ((EntityLivingBase) entity).getHealth()
                : 0.0F;
        this.loaded = true;
        this.lastUpdateWorldTime = worldTime;
    }

    public UUID getId()
    {
        return id;
    }

    public String getEntityType()
    {
        return entityType;
    }

    public int getDimension()
    {
        return dimension;
    }

    public double getX()
    {
        return x;
    }

    public double getY()
    {
        return y;
    }

    public double getZ()
    {
        return z;
    }

    public float getHealth()
    {
        return health;
    }

    public boolean isLoaded()
    {
        return loaded;
    }

    public void setLoaded(boolean loaded)
    {
        this.loaded = loaded;
    }

    public long getLastUpdateWorldTime()
    {
        return lastUpdateWorldTime;
    }
}
