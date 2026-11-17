package com.livingworld.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.EntityAgeable;
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
    private AnimalDiet diet = AnimalDiet.UNKNOWN;
    private int growingAge;
    private long breedingCooldownUntil;
    private boolean virtual;

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
    private long lastWorkstationSearchWorldTime = Long.MIN_VALUE;
    private int pendingStarvationDamage;

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
        this.virtual = false;
        if (entity instanceof EntityAgeable)
        {
            growingAge = ((EntityAgeable) entity).getGrowingAge();
        }

        if (animal)
        {
            diet = AnimalDiet.fromEntityType(entityType);
        }

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

            if (lastWorkstationSearchWorldTime == Long.MIN_VALUE
                    || worldTime - lastWorkstationSearchWorldTime >= 1200L)
            {
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
                lastWorkstationSearchWorldTime = worldTime;
            }
        }

        boolean firstUpdate = lastUpdateWorldTime == 0L && lastSimulationWorldTime == 0L;
        this.loaded = true;
        this.lastUpdateWorldTime = worldTime;
        if (firstUpdate)
        {
            this.lastSimulationWorldTime = worldTime;
        }
    }

    /**
     * Advances the abstract state while the entity is not loaded.
     * Animals consume one hunger point every 2,400 world ticks.
     * Villagers update their daily activity according to the world time.
     */
    public void simulateOffline(long worldTime)
    {
        if (worldTime <= lastSimulationWorldTime)
        {
            return;
        }

        long elapsed = worldTime - lastSimulationWorldTime;

        if (!loaded && villager)
        {
            villagerActivity = VillagerBehavior.getActivity(worldTime);
            simulateVillagerMovement(elapsed);
        }

        if (!animal)
        {
            lastSimulationWorldTime = worldTime;
            return;
        }

        long intervals = elapsed / HUNGER_INTERVAL;

        if (intervals <= 0)
        {
            return;
        }

        long simulatedTicks = intervals * HUNGER_INTERVAL;
        if (!loaded)
        {
            if (growingAge < 0)
            {
                growingAge = (int) Math.min(0L, growingAge + simulatedTicks);
            }
            else if (growingAge > 0)
            {
                growingAge = (int) Math.max(0L, growingAge - simulatedTicks);
            }
        }

        long hungerToConsume = Math.min(intervals, hunger);
        hunger -= (int) hungerToConsume;

        // Loaded animals retain vanilla age and health handling. Offline health
        // loss is applied only when the entity is materialized again.
        long starvingIntervals = intervals - hungerToConsume;
        if (starvingIntervals > 0)
        {
            if (loaded)
            {
                pendingStarvationDamage += (int) Math.min(
                        Integer.MAX_VALUE - pendingStarvationDamage, starvingIntervals);
            }
            else
            {
                health = Math.max(0.0F, health - (float) starvingIntervals);
            }
        }

        lastSimulationWorldTime += intervals * HUNGER_INTERVAL;
    }

    /**
     * Applies the simulated health to the real entity when it is loaded again.
     * Returns false when the offline simulation has killed the entity.
     */
    public boolean applyOfflineState(Entity entity, boolean wasOffline)
    {
        if (!animal)
        {
            return true;
        }

        if (wasOffline && entity instanceof EntityAgeable)
        {
            ((EntityAgeable) entity).setGrowingAge(growingAge);
        }

        if (!(entity instanceof EntityLivingBase))
        {
            return true;
        }

        EntityLivingBase living = (EntityLivingBase) entity;
        if (wasOffline)
        {
            if (health <= 0.0F)
            {
                living.setHealth(0.0F);
                living.setDead();
                loaded = false;
                return false;
            }

            living.setHealth(Math.min(health, living.getMaxHealth()));
        }
        else if (pendingStarvationDamage > 0)
        {
            living.setHealth(Math.max(0.0F,
                    living.getHealth() - pendingStarvationDamage));
            pendingStarvationDamage = 0;
            if (living.getHealth() <= 0.0F)
            {
                living.setDead();
                loaded = false;
                return false;
            }
        }

        return true;
    }


    /**
     * Moves the abstract villager position toward its current offline destination.
     * No chunks or Minecraft entities are loaded for this calculation.
     */
    private void simulateVillagerMovement(long elapsedTicks)
    {
        if (elapsedTicks <= 0L)
        {
            return;
        }

        int targetX;
        int targetY;
        int targetZ;

        if (VillagerBehavior.WORKING.equals(villagerActivity) && hasWorkstation)
        {
            targetX = workstationX;
            targetY = workstationY;
            targetZ = workstationZ;
        }
        else if (VillagerBehavior.SLEEPING.equals(villagerActivity) && hasHome)
        {
            targetX = homeX;
            targetY = homeY;
            targetZ = homeZ;
        }
        else
        {
            return;
        }

        double dx = targetX + 0.5D - x;
        double dy = targetY - y;
        double dz = targetZ + 0.5D - z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 0.5D)
        {
            return;
        }

        double maxTravel = Math.min(distance, elapsedTicks * 0.035D);
        double scale = maxTravel / distance;
        x += dx * scale;
        y += dy * scale;
        z += dz * scale;
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
        tag.setString("AnimalDiet", diet.name());
        tag.setInteger("GrowingAge", growingAge);
        tag.setLong("BreedingCooldownUntil", breedingCooldownUntil);
        tag.setBoolean("Virtual", virtual);

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
        tag.setLong("LastWorkstationSearch", lastWorkstationSearchWorldTime);
        tag.setInteger("PendingStarvationDamage", pendingStarvationDamage);
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
        data.growingAge = tag.hasKey("GrowingAge") ? tag.getInteger("GrowingAge") : 0;
        data.breedingCooldownUntil = tag.getLong("BreedingCooldownUntil");
        data.virtual = tag.getBoolean("Virtual");
        if (tag.hasKey("AnimalDiet"))
        {
            try
            {
                data.diet = AnimalDiet.valueOf(tag.getString("AnimalDiet"));
            }
            catch (IllegalArgumentException ignored)
            {
                data.diet = AnimalDiet.UNKNOWN;
            }
        }
        else if (data.animal)
        {
            // Compatibility with worlds saved before dietary categories existed.
            data.diet = AnimalDiet.fromEntityType(data.entityType);
        }

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
        data.lastWorkstationSearchWorldTime = tag.hasKey("LastWorkstationSearch")
                ? tag.getLong("LastWorkstationSearch") : Long.MIN_VALUE;
        data.pendingStarvationDamage = tag.getInteger("PendingStarvationDamage");
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
    public void feed(int amount)
    {
        if (amount > 0)
        {
            hunger = Math.min(100, hunger + amount);
        }
    }
    public void consumeHunger(int amount)
    {
        if (amount > 0)
        {
            hunger = Math.max(0, hunger - amount);
        }
    }
    public AnimalDiet getDiet() { return diet; }
    public int getGrowingAge() { return growingAge; }
    public void setGrowingAge(int age) { growingAge = age; }
    public long getBreedingCooldownUntil() { return breedingCooldownUntil; }
    public void setBreedingCooldownUntil(long time) { breedingCooldownUntil = time; }
    public boolean isVirtual() { return virtual; }
    public void setVirtual(boolean virtual) { this.virtual = virtual; }

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
