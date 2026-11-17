package com.livingworld.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.world.World;

import java.util.Map;

/**
 * Allows well-fed adult animals of the same species to reproduce naturally
 * in loaded chunks. Vanilla EntityAIMate handles the actual approach and birth.
 */
public final class AnimalBreedingBehavior
{
    private static final int SEARCH_RADIUS = 8;
    private static final int MIN_HUNGER = 75;
    private static final int HUNGER_COST = 25;
    private static final long BREEDING_COOLDOWN = 6000L;
    private static final String LAST_BREEDING_KEY = "LivingWorldLastBreeding";
    private static final String LAST_SEARCH_KEY = "LivingWorldLastBreedSearch";

    private AnimalBreedingBehavior()
    {
    }

    public static void update(EntityAnimal animal, EntityData data,
                              Map<java.util.UUID, EntityData> trackedEntities,
                              World world, long worldTime)
    {
        if (animal == null || data == null || trackedEntities == null
                || world == null || world.isRemote || !animal.isEntityAlive()
                || animal.getGrowingAge() != 0 || animal.isInLove()
                || data.getHunger() < MIN_HUNGER)
        {
            return;
        }

        if (!isCooldownComplete(animal, data, worldTime))
        {
            return;
        }

        long lastSearch = animal.getEntityData().getLong(LAST_SEARCH_KEY);
        if (worldTime - lastSearch < 200L)
        {
            return;
        }
        animal.getEntityData().setLong(LAST_SEARCH_KEY, worldTime);

        EntityAnimal partner = null;
        EntityData partnerData = null;
        double nearestDistance = SEARCH_RADIUS * SEARCH_RADIUS;

        for (Entity entity : world.getEntitiesWithinAABB(
                EntityAnimal.class, animal.getEntityBoundingBox().grow(SEARCH_RADIUS)))
        {
            if (!(entity instanceof EntityAnimal) || entity == animal)
            {
                continue;
            }

            EntityAnimal candidate = (EntityAnimal) entity;
            // Only the lower UUID initiates a pair search, avoiding duplicate
            // scans and preventing both partners from starting the same pairing.
            if (animal.getUniqueID().compareTo(candidate.getUniqueID()) >= 0)
            {
                continue;
            }
            EntityData candidateData = trackedEntities.get(candidate.getUniqueID());
            if (!candidate.isEntityAlive() || candidate.getGrowingAge() != 0
                    || candidate.isInLove() || candidate.getClass() != animal.getClass()
                    || candidateData == null || candidateData.getHunger() < MIN_HUNGER
                    || !isCooldownComplete(candidate, candidateData, worldTime))
            {
                continue;
            }

            double distance = animal.getDistanceSq(candidate);
            if (distance < nearestDistance)
            {
                nearestDistance = distance;
                partner = candidate;
                partnerData = candidateData;
            }
        }

        if (partner == null || partnerData == null)
        {
            return;
        }

        // Vanilla's EntityAIMate will find the other animal and complete breeding.
        animal.setInLove(null);
        partner.setInLove(null);

        data.consumeHunger(HUNGER_COST);
        partnerData.consumeHunger(HUNGER_COST);
        data.setBreedingCooldownUntil(worldTime + BREEDING_COOLDOWN);
        partnerData.setBreedingCooldownUntil(worldTime + BREEDING_COOLDOWN);
        animal.getEntityData().setLong(LAST_BREEDING_KEY, worldTime);
        partner.getEntityData().setLong(LAST_BREEDING_KEY, worldTime);
    }

    private static boolean isCooldownComplete(EntityAnimal animal, EntityData data,
                                              long worldTime)
    {
        long cooldownUntil = data.getBreedingCooldownUntil();
        if (animal.getEntityData().hasKey(LAST_BREEDING_KEY))
        {
            long lastBreeding = animal.getEntityData().getLong(LAST_BREEDING_KEY);
            cooldownUntil = Math.max(cooldownUntil, lastBreeding + BREEDING_COOLDOWN);
        }

        return worldTime >= cooldownUntil;
    }
}
