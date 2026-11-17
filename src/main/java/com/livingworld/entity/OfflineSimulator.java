package com.livingworld.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
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
    private static final long BREEDING_COOLDOWN = 24000L;
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

        List<EntityData> eligibleAnimals = new ArrayList<EntityData>();
        Map<BreedingCell, List<EntityData>> cells =
                new java.util.HashMap<BreedingCell, List<EntityData>>();
        int virtualCount = 0;

        for (EntityData data : entities.values())
        {
            if (data.isVirtual())
            {
                virtualCount++;
            }

            if (!canBreed(data, worldTime))
            {
                continue;
            }

            eligibleAnimals.add(data);
            BreedingCell cell = BreedingCell.from(data);
            List<EntityData> bucket = cells.get(cell);
            if (bucket == null)
            {
                bucket = new ArrayList<EntityData>();
                cells.put(cell, bucket);
            }
            bucket.add(data);
        }

        int births = 0;
        for (EntityData first : eligibleAnimals)
        {
            if (births >= MAX_BIRTHS_PER_UPDATE
                    || virtualCount >= MAX_VIRTUAL_OFFSPRING)
            {
                break;
            }

            BreedingCell origin = BreedingCell.from(first);
            boolean paired = false;

            // Only inspect the current cell and its 26 neighbors. Since each
            // cell is eight blocks wide, every possible partner within the
            // breeding radius must be in one of these buckets.
            for (int dx = -1; dx <= 1 && !paired; dx++)
            {
                for (int dy = -1; dy <= 1 && !paired; dy++)
                {
                    for (int dz = -1; dz <= 1 && !paired; dz++)
                    {
                        List<EntityData> candidates = cells.get(
                                origin.offset(dx, dy, dz));
                        if (candidates == null)
                        {
                            continue;
                        }

                        for (EntityData second : candidates)
                        {
                            // UUID ordering makes each pair considered once.
                            if (first.getId().compareTo(second.getId()) >= 0
                                    || !canBreed(first, worldTime)
                                    || !canBreed(second, worldTime)
                                    || distanceSquared(first, second) > BREEDING_RADIUS_SQ)
                            {
                                continue;
                            }

                            first.consumeHunger(BREEDING_HUNGER_COST);
                            second.consumeHunger(BREEDING_HUNGER_COST);
                            first.setGrowingAge((int) BREEDING_COOLDOWN);
                            second.setGrowingAge((int) BREEDING_COOLDOWN);
                            first.setBreedingCooldownUntil(worldTime + BREEDING_COOLDOWN);
                            second.setBreedingCooldownUntil(worldTime + BREEDING_COOLDOWN);

                            EntityData offspring = EntityData.createVirtualOffspring(
                                    first, second, worldTime);
                            entities.put(offspring.getId(), offspring);
                            virtualCount++;
                            births++;
                            paired = true;
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * Spatial bucket for offline breeding. Species, dimension and diet are part
     * of the key, so incompatible animals are never compared.
     */
    private static final class BreedingCell
    {
        private final int dimension;
        private final String entityType;
        private final AnimalDiet diet;
        private final int x;
        private final int y;
        private final int z;

        private BreedingCell(int dimension, String entityType, AnimalDiet diet,
                             int x, int y, int z)
        {
            this.dimension = dimension;
            this.entityType = entityType;
            this.diet = diet;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private static BreedingCell from(EntityData data)
        {
            return new BreedingCell(data.getDimension(), data.getEntityType(),
                    data.getDiet(), floorCell(data.getX()), floorCell(data.getY()),
                    floorCell(data.getZ()));
        }

        private BreedingCell offset(int dx, int dy, int dz)
        {
            return new BreedingCell(dimension, entityType, diet,
                    x + dx, y + dy, z + dz);
        }

        private static int floorCell(double coordinate)
        {
            return (int) Math.floor(coordinate / 8.0D);
        }

        @Override
        public boolean equals(Object object)
        {
            if (this == object)
            {
                return true;
            }
            if (!(object instanceof BreedingCell))
            {
                return false;
            }

            BreedingCell other = (BreedingCell) object;
            return dimension == other.dimension && x == other.x && y == other.y
                    && z == other.z && entityType.equals(other.entityType)
                    && diet == other.diet;
        }

        @Override
        public int hashCode()
        {
            int result = dimension;
            result = 31 * result + entityType.hashCode();
            result = 31 * result + (diet == null ? 0 : diet.hashCode());
            result = 31 * result + x;
            result = 31 * result + y;
            result = 31 * result + z;
            return result;
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

        // Most EntityAnimal subclasses can reproduce in vanilla. Mules are
        // sterile, and virtual offspring require a registered entity type so
        // they can later be reconstructed safely.
        if ("minecraft:mule".equals(data.getEntityType()))
        {
            return false;
        }

        try
        {
            return EntityList.isRegistered(new ResourceLocation(data.getEntityType()));
        }
        catch (RuntimeException exception)
        {
            return false;
        }
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
        Set<UUID> loadedIds = new HashSet<UUID>();
        for (Entity loadedEntity : world.loadedEntityList)
        {
            loadedIds.add(loadedEntity.getUniqueID());
        }

        for (EntityData data : snapshot)
        {
            if (!data.isVirtual() || data.getDimension() != dimension
                    || data.getHealth() <= 0.0F)
            {
                continue;
            }

            BlockPos position = new BlockPos(data.getX(), data.getY(), data.getZ());
            if (!world.isBlockLoaded(position))
            {
                continue;
            }

            if (loadedIds.contains(data.getId()))
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

            offspring.setGrowingAge(data.getGrowingAge());
            offspring.setHealth(Math.min(data.getHealth(), offspring.getMaxHealth()));

            // spawnEntity returns false if Forge rejects the spawn. In that case
            // keep the virtual record and retry on a later tracker update.
            if (world.spawnEntity(offspring))
            {
                data.setVirtual(false);
            }
        }
    }
}
