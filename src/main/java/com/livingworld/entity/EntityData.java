package com.livingworld.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;

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

    public NBTTagCompound writeToNBT()
    {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Id", id.toString());
        tag.setString("Type", entityType);
        tag.setInteger("Dimension", dimension);
        tag.setDouble("X", x);
        tag.setDouble("Y", y);
        tag.setDouble("Z", z);
        tag.setFloat("Health", health);
        tag.setBoolean("Loaded", loaded);
        tag.setLong("LastUpdate", lastUpdateWorldTime);
        return tag;
    }

    public static EntityData readFromNBT(NBTTagCompound tag)
    {
        UUID id = UUID.fromString(tag.getString("Id"));
        EntityData data = new EntityData(id, tag.getString("Type"));
        data.dimension = tag.getInteger("Dimension");
        data.x = tag.getDouble("X");
        data.y = tag.getDouble("Y");
        data.z = tag.getDouble("Z");
        data.health = tag.getFloat("Health");
        // An entity is not considered loaded merely because its record was saved.
        data.loaded = false;
        data.lastUpdateWorldTime = tag.getLong("LastUpdate");
        return data;
    }

    public UUID getId() { return id; }
    public String getEntityType() { return entityType; }
    public int getDimension() { return dimension; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getHealth() { return health; }
    public boolean isLoaded() { return loaded; }
    public void setLoaded(boolean loaded) { this.loaded = loaded; }
    public long getLastUpdateWorldTime() { return lastUpdateWorldTime; }
}
