package com.livingworld.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;

import java.util.List;

/**
 * Simple predator hunting for animals in already-loaded chunks.
 *
 * Wolves hunt sheep and rabbits. Ocelots hunt chickens and rabbits.
 * Hunting is driven by the shared hunger value and never loads chunks.
 */
public final class AnimalHuntBehavior
{
    private static final int SEARCH_RADIUS = 16;
    private static final int HUNGER_THRESHOLD = 65;
    private static final double ATTACK_DISTANCE_SQ = 2.25D;
    private static final double CHASE_SPEED = 1.15D;
    private static final float ATTACK_DAMAGE = 3.0F;
    private static final long ATTACK_COOLDOWN = 100L;
    private static final int FOOD_REWARD = 40;
    private static final String LAST_ATTACK_KEY = "LivingWorldLastHunt";

    private AnimalHuntBehavior()
    {
    }

    public static void update(EntityAnimal predator, EntityData data, World world, long worldTime)
    {
        if (predator == null || data == null || world == null || world.isRemote
                || !predator.isEntityAlive()
                || data.getDiet() != AnimalDiet.CARNIVORE
                || data.getHunger() >= HUNGER_THRESHOLD)
        {
            return;
        }

        String predatorType = getType(predator);
        if (!"wolf".equals(predatorType) && !"ocelot".equals(predatorType))
        {
            return;
        }

        AxisAlignedBB searchBox = predator.getEntityBoundingBox().grow(SEARCH_RADIUS);
        List<EntityAnimal> nearby = world.getEntitiesWithinAABB(EntityAnimal.class, searchBox);
        EntityAnimal prey = null;
        double nearestDistance = SEARCH_RADIUS * SEARCH_RADIUS;

        for (EntityAnimal candidate : nearby)
        {
            if (candidate == predator || !candidate.isEntityAlive() || candidate.isChild())
            {
                continue;
            }

            String preyType = getType(candidate);
            if (!isValidPrey(predatorType, preyType))
            {
                continue;
            }

            double distance = predator.getDistanceSq(candidate);
            if (distance < nearestDistance)
            {
                nearestDistance = distance;
                prey = candidate;
            }
        }

        if (prey == null)
        {
            return;
        }

        if (nearestDistance > ATTACK_DISTANCE_SQ)
        {
            predator.getNavigator().tryMoveToEntityLiving(prey, CHASE_SPEED);
            return;
        }

        long lastAttack = predator.getEntityData().getLong(LAST_ATTACK_KEY);
        if (worldTime - lastAttack < ATTACK_COOLDOWN)
        {
            return;
        }

        predator.getEntityData().setLong(LAST_ATTACK_KEY, worldTime);
        prey.attackEntityFrom(DamageSource.causeMobDamage(predator), ATTACK_DAMAGE);

        if (!prey.isEntityAlive())
        {
            predator.getNavigator().clearPath();
            data.feed(FOOD_REWARD);
        }
    }

    private static boolean isValidPrey(String predator, String prey)
    {
        if ("wolf".equals(predator))
        {
            return "sheep".equals(prey) || "rabbit".equals(prey);
        }

        if ("ocelot".equals(predator))
        {
            return "chicken".equals(prey) || "rabbit".equals(prey);
        }

        return false;
    }

    private static String getType(Entity entity)
    {
        ResourceLocation key = EntityList.getKey(entity);
        if (key == null)
        {
            return "";
        }
        return key.getResourcePath().toLowerCase();
    }
}