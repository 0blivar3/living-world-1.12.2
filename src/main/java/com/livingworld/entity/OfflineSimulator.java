package com.livingworld.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAgeable;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lightweight simulation for entities outside loaded chunks.
 *
 * Offspring remain as saved virtual records until their destination chunk is
 * naturally loaded. They are then converted into ordinary Minecraft entities.
 */
public final class OfflineSimulator
{
    private static final double BREEDING_RADIUS_SQ = 8.0D * 8.0D;
    private static final int MIN_BREEDING_HUNGER = 75;
    private static final int BREEDING_HUNGER_COST = 25;
    private static final long BREEDING_COOLDOWN = 6000L;
    private static final int MAX_VIRTUAL_OFFSPRING = 64;
    private static final int MAX_BIRTHS_PER_UPDATE = 4;

    private OfflineSimulator()
    {
    }

    public static void simulate(Collection<EntityData> entities, long worldTime)
    {
        for (EntityData data : entities)
        {
            if (!data.isLoaded())
            {
                data.simulateOffline(worldTime);
            }
        }
    }

    /**
     * Simulates mating between compatible adult animals without loading chunks.
     * A pair can produce only one offspring per cooldown, and virtual population
     * is capped to prevent runaway entity growth.
     */
    public static void simulateBreeding(Map<UUID, EntityData> entities, long worldTime)
    {
        if (entities == null || entities.isEmpty())
        {
            return;
        }

        List<EntityData> animals = new ArrayList<EntityData>(entities.values());
        int virtualCount = 0;
        for (EntityData data : animals)
        {
            if (data.isVirtual())
            {
                virtualCount++;
            }
        }

        int births = 0;
        for (int i = 0; i < animals.size(); i++)
        {
            if (births >= MAX_BIRTHS_PER_UPDATE
                    || virtualCount >= MAX_VIRTUAL_OFFSPRING)
            {
                break;
            }

            EntityData first = animals.get(i);
            if (!canBreed(first, worldTime))
            {
                continue;
            }

            for (int j = i + 1; j < animals.size(); j++)
            {
                EntityData second = animals.get(j);
                if (!canBreed(second, worldTime)
                        || first.getDimension() != second.getDimension()
                        || !first.getEntityType().equals(second.getEntityType())
                        || first.getDiet() != second.getDiet()
                        || distanceSquared(first, second) > BREEDING_RADIUS_SQ)
                {
                    continue;
                }

                first.consumeHunger(BREEDING_HUNGER_COST);
                second.consumeHunger(BREEDING_HUNGER_COST);
                // Mirror vanilla's post-breeding age as well as the persisted
                // A-Life cooldown, so both systems agree when the chunk reloads.
                first.setGrowingAge((int) BREEDING_COOLDOWN);
                second.setGrowingAge((int) BREEDING_COOLDOWN);
                first.setBreedingCooldownUntil(worldTime + BREEDING_COOLDOWN);
                second.setBreedingCooldownUntil(worldTime + BREEDING_COOLDOWN);

                EntityData offspring = EntityData.createVirtualOffspring(first, second, worldTime);
                entities.put(offspring.getId(), offspring);
                virtualCount++;
                births++;
                break;
            }
        }
    }

    private static boolean canBreed(EntityData data, long worldTime)
    {
        if (data == null || !data.isAnimal() || data.isLoaded() || data.isVirtual()
                || data.getHealth() <= 0.0F || data.getGrowingAge() != 0
                || data.getHunger() < MIN_BREEDING_HUNGER
                || worldTime < data.getBreedingCooldownUntil())
        {
            return false;
        }

        // Match the loaded breeding system: every registered animal diet can
        // reproduce, while unknown custom entity types remain unsupported.
        return data.getDiet() != AnimalDiet.UNKNOWN;
    }

    private static double distanceSquared(EntityData first, EntityData second)
    {
        double dx = first.getX() - second.getX();
        double dy = first.getY() - second.getY();
        double dz = first.getZ() - second.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Materializes virtual offspring only when their destination chunk is
     * already loaded. This method never requests or force-loads a chunk.
     */
    public static void materializeVirtualOffspring(World world,
                                                    Collection<EntityData> entities)
    {
        if (world == null || world.isRemote || entities == null)
        {
            return;
        }

        int dimension = world.provider.getDimension();
        List<EntityData> snapshot = new ArrayList<EntityData>(entities);

        for (EntityData data : snapshot)
        {
            if (!data.isVirtual() || data.getDimension() != dimension)
            {
                continue;
            }

            BlockPos position = new BlockPos(data.getX(), data.getY(), data.getZ());
            if (!world.isBlockLoaded(position))
            {
                continue;
            }

            boolean alreadyPresent = false;
            for (Entity loadedEntity : world.loadedEntityList)
            {
                if (data.getId().equals(loadedEntity.getUniqueID()))
                {
                    alreadyPresent = true;
                    break;
                }
            }

            if (alreadyPresent)
            {
                data.setVirtual(false);
                continue;
            }

            ResourceLocation entityType;
            try
            {
                entityType = new ResourceLocation(data.getEntityType());
            }
            catch (RuntimeException exception)
            {
                continue;
            }

            Entity created = EntityList.createEntityByIDFromName(entityType, world);
            if (!(created instanceof EntityAnimal))
            {
                continue;
            }

            EntityAnimal offspring = (EntityAnimal) created;
            offspring.setUniqueId(data.getId());
            offspring.setPosition(data.getX(), data.getY(), data.getZ());

            if (offspring instanceof EntityAgeable)
            {
                ((EntityAgeable) offspring).setGrowingAge(data.getGrowingAge());
            }

            if (offspring instanceof EntityLivingBase)
            {
                EntityLivingBase living = (EntityLivingBase) offspring;
                living.setHealth(Math.min(data.getHealth(), living.getMaxHealth()));
            }

            // spawnEntity returns false if Forge rejects the spawn. In that case
            // keep the virtual record and retry on a later tracker update.
            if (world.spawnEntity(offspring))
            {
                data.setVirtual(false);
            }
        }
    }
}
