package com.livingworld.entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Runs lightweight simulation for entities that are currently not loaded.
 */
public final class OfflineSimulator
{
    private static final long BREEDING_COOLDOWN = 6000L;
    private static final int MIN_HUNGER = 75;
    private static final int BREEDING_COST = 25;
    private static final double BREEDING_RADIUS_SQ = 64.0D;

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
     * Simulates reproduction using snapshots only. It never creates or loads
     * Minecraft entities and therefore works while the animals' chunks are unloaded.
     */
    public static void simulateBreeding(Map<UUID, EntityData> entities, long worldTime)
    {
        if (entities == null || entities.isEmpty())
        {
            return;
        }

        ArrayList<EntityData> animals = new ArrayList<EntityData>(entities.values());
        Set<UUID> paired = new HashSet<UUID>();

        for (int i = 0; i < animals.size(); i++)
        {
            EntityData first = animals.get(i);
            if (!eligible(first, worldTime) || paired.contains(first.getId()))
            {
                continue;
            }

            for (int j = i + 1; j < animals.size(); j++)
            {
                EntityData second = animals.get(j);
                if (!eligible(second, worldTime) || paired.contains(second.getId())
                        || !first.getEntityType().equals(second.getEntityType())
                        || first.getDimension() != second.getDimension())
                {
                    continue;
                }

                double dx = first.getX() - second.getX();
                double dy = first.getY() - second.getY();
                double dz = first.getZ() - second.getZ();
                if (dx * dx + dy * dy + dz * dz > BREEDING_RADIUS_SQ)
                {
                    continue;
                }

                first.consumeHunger(BREEDING_COST);
                second.consumeHunger(BREEDING_COST);
                first.setBreedingCooldownUntil(worldTime + BREEDING_COOLDOWN);
                second.setBreedingCooldownUntil(worldTime + BREEDING_COOLDOWN);

                EntityData offspring = EntityData.createVirtualOffspring(first, worldTime);
                entities.put(offspring.getId(), offspring);
                paired.add(first.getId());
                paired.add(second.getId());
                break;
            }
        }
    }

    private static boolean eligible(EntityData data, long worldTime)
    {
        return data != null && data.isAnimal() && !data.isLoaded()
                && !data.isVirtual() && data.getHealth() > 0.0F
                && data.getDiet() != AnimalDiet.UNKNOWN
                && data.getDiet() != AnimalDiet.CARNIVORE
                && data.getGrowingAge() == 0
                && data.getHunger() >= MIN_HUNGER
                && data.getBreedingCooldownUntil() <= worldTime;
    }
}
