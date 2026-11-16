package com.livingworld.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

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
    private boolean villager;
    private int hunger = 100;

    private String villagerActivity = VillagerBehavior.WANDERING;
    private int villagerProfession = -1;
    private boolean hasHome;
    private int homeX;
    private int homeY;
    private int homeZ;
    private boolean hasWorkstation;
    private int workstationX;
    private int workstationY;
    private int workstationZ;

    private long lastUpdateWorldTime;
    private long lastSimulationWorldTime;

    public EntityData(UUID id, String entityType)
    {
        this.id = id;
        this.entityType = entityType;
    }

    public void update(Entity entity, long worldTime, int dimension, World world)
    {
        this.dimension = dimension;
        this.x = entity.posX;
        this.y = entity.posY;
        this.z = entity.posZ;
        this.health = entity instanceof EntityLivingBase
                ? ((EntityLivingBase) entity).getHealth()
                : 0.0F;
        this.animal = entity instanceof EntityAnimal;
        this.villager = entity instanceof EntityVillager;

        if (entity instanceof EntityVillager)
        {
            EntityVillager villagerEntity = (EntityVillager) entity;
            villagerActivity = VillagerBehavior.getActivity(worldTime);
            villagerProfession = villagerEntity.getProfession();

            if (villagerEntity.hasHome())
            {
                BlockPos home = villagerEntity.getHomePosition();
                hasHome = true;
                homeX = home.getX();
                homeY = home.getY();
                homeZ = home.getZ();
            }
            else
            {
                hasHome = false;
            }

            BlockPos workstation = VillagerWorkstationFinder.findNearest(villagerEntity, world);
            if (workstation != null)
            {
                hasWorkstation = true;
                workstationX = workstation.getX();
                workstationY = workstation.getY();
                workstationZ = workstation.getZ();
            }
            else
            {
                hasWorkstation = false;
            }
        }

        this.loaded = true;
        this.lastUpdateWorldTime = worldTime;
        this.lastSimulationWorldTime = worldTime;
    }

    /**
     * Advances the abstract state while the entity is not loaded.
     * Animals consume one hunger point every 2,400 world ticks.
     * Villagers update their daily activity according to the world time.
     */
    public void simulateOffline(long worldTime)
    {
        if (loaded || worldTime <= lastSimulationWorldTime)
        {
            return;
        }

        if (villager)
        {
            villagerActivity = VillagerBehavior.getActivity(worldTime);
        }

        if (!animal)
        {
            lastSimulationWorldTime = worldTime;
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

    /**
     * Applies the simulated health to the real entity when it is loaded again.
     * Returns false when the offline simulation has killed the entity.
     */
    public boolean applyOfflineState(Entity entity)
    {
        if (!animal || !(entity instanceof EntityLivingBase))
        {
            return true;
        }

        EntityLivingBase living = (EntityLivingBase) entity;
        if (health <= 0.0F)
        {
            living.setHealth(0.0F);
            living.setDead();
            loaded = false;
            return false;
        }

        float appliedHealth = Math.min(health, living.getMaxHealth());
        living.setHealth(appliedHealth);
        return true;
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
        tag.setBoolean("Villager", villager);
        tag.setInteger("Hunger", hunger);

        tag.setString("VillagerActivity", villagerActivity);
        tag.setInteger("VillagerProfession", villagerProfession);
        tag.setBoolean("HasHome", hasHome);
        tag.setInteger("HomeX", homeX);
        tag.setInteger("HomeY", homeY);
        tag.setInteger("HomeZ", homeZ);
        tag.setBoolean("HasWorkstation", hasWorkstation);
        tag.setInteger("WorkstationX", workstationX);
        tag.setInteger("WorkstationY", workstationY);
        tag.setInteger("WorkstationZ", workstationZ);

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
        data.villager = tag.getBoolean("Villager");
        data.hunger = tag.hasKey("Hunger") ? tag.getInteger("Hunger") : 100;

        data.villagerActivity = tag.hasKey("VillagerActivity")
                ? tag.getString("VillagerActivity")
                : VillagerBehavior.WANDERING;
        data.villagerProfession = tag.hasKey("VillagerProfession")
                ? tag.getInteger("VillagerProfession")
                : -1;
        data.hasHome = tag.getBoolean("HasHome");
        data.homeX = tag.getInteger("HomeX");
        data.homeY = tag.getInteger("HomeY");
        data.homeZ = tag.getInteger("HomeZ");
        data.hasWorkstation = tag.getBoolean("HasWorkstation");
        data.workstationX = tag.getInteger("WorkstationX");
        data.workstationY = tag.getInteger("WorkstationY");
        data.workstationZ = tag.getInteger("WorkstationZ");

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
    public boolean isVillager() { return villager; }
    public int getHunger() { return hunger; }
    public String getVillagerActivity() { return villagerActivity; }
    public int getVillagerProfession() { return villagerProfession; }
    public boolean hasHome() { return hasHome; }
    public int getHomeX() { return homeX; }
    public int getHomeY() { return homeY; }
    public int getHomeZ() { return homeZ; }
    public boolean hasWorkstation() { return hasWorkstation; }
    public int getWorkstationX() { return workstationX; }
    public int getWorkstationY() { return workstationY; }
    public int getWorkstationZ() { return workstationZ; }
    public long getLastUpdateWorldTime() { return lastUpdateWorldTime; }
}
