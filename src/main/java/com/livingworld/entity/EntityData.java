package com.livingworld.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAnimal;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;

import java.util.UUID;

/**
 * Lightweight snapshot of an entity for the A-Life simulation.
 */
public class EntityData
{
    private static final long HUNGER_INTERVAL = 2400L;

    private final UUID id;
    private final String entityType;

    private int dimension;
    private double x;
    private double y;
    private double z;
    private float health;
    private boolean loaded;
    private boolean animal;
    private int hunger = 100;
    private long lastUpdateWorldTime;
    private long lastSimulationWorldTime;

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
        this.animal = entity instanceof EntityAnimal;
        this.loaded = true;
        this.lastUpdateWorldTime = worldTime;
        this.lastSimulationWorldTime = worldTime;
    }

    /**
     * Advances the abstract survival state while the entity is not loaded.
     * One hunger point is consumed every 2,400 world ticks.
     */
    public void simulateOffline(long worldTime)
    {
        if (loaded || !animal || worldTime <= lastSimulationWorldTime)
        {
            return;
        }

        long elapsed = worldTime - lastSimulationWorldTime;
        long intervals = elapsed / HUNGER_INTERVAL;

        if (intervals <= 0)
        {
            return;
        }

        long hungerToConsume = Math.min(intervals, hunger);
        hunger -= (int) hungerToConsume;

        long starvingIntervals = intervals - hungerToConsume;
        if (starvingIntervals > 0)
        {
            health = Math.max(0.0F, health - (float) starvingIntervals);
        }

        lastSimulationWorldTime += intervals * HUNGER_INTERVAL;
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
        tag.setBoolean("Animal", animal);
        tag.setInteger("Hunger", hunger);
        tag.setLong("LastUpdate", lastUpdateWorldTime);
        tag.setLong("LastSimulation", lastSimulationWorldTime);
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
        data.loaded = false;
        data.animal = tag.getBoolean("Animal");
        data.hunger = tag.hasKey("Hunger") ? tag.getInteger("Hunger") : 100;
        data.lastUpdateWorldTime = tag.getLong("LastUpdate");
        data.lastSimulationWorldTime = tag.hasKey("LastSimulation")
                ? tag.getLong("LastSimulation")
                : data.lastUpdateWorldTime;
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
    public boolean isAnimal() { return animal; }
    public int getHunger() { return hunger; }
    public long getLastUpdateWorldTime() { return lastUpdateWorldTime; }
}
