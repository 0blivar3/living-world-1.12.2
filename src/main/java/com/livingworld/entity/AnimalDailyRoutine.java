package com.livingworld.entity;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.passive.EntityAnimal;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Adds a simple daily rest schedule to passive herbivores and omnivores.
 *
 * Resting is implemented as a movement task so ordinary wandering is
 * suppressed at night, while higher-priority panic and mating tasks can
 * still interrupt it.
 */
public final class AnimalDailyRoutine
{
    private static final int REST_START = 13000;
    private static final int REST_END = 23000;
    private static final int SCHEDULE_VARIATION = 1200;

    // AI tasks are runtime objects and must be reinstalled after an entity reload.
    private static final Set<EntityAnimal> INSTALLED =
            Collections.newSetFromMap(new WeakHashMap<EntityAnimal, Boolean>());

    private AnimalDailyRoutine()
    {
    }

    /**
     * Installs the rest task once for each currently loaded animal instance.
     */
    public static void update(EntityAnimal animal, EntityData data)
    {
        if (animal == null || data == null || animal.world == null
                || animal.world.isRemote || !animal.isEntityAlive())
        {
            return;
        }

        if (data.getDiet() == AnimalDiet.CARNIVORE
                || data.getDiet() == AnimalDiet.UNKNOWN
                || !INSTALLED.add(animal))
        {
            return;
        }

        animal.tasks.addTask(5, new NightRestTask(animal, data));
    }

    /**
     * Returns whether this animal is currently inside its individual rest window.
     */
    public static boolean isRestTime(EntityAnimal animal)
    {
        if (animal == null || animal.world == null)
        {
            return false;
        }

        int offset = Math.floorMod(animal.getUniqueID().hashCode(), SCHEDULE_VARIATION);
        long localTime = (animal.world.getWorldTime() + offset) % 24000L;
        return localTime >= REST_START && localTime < REST_END;
    }

    private static final class NightRestTask extends EntityAIBase
    {
        private final EntityAnimal animal;
        private final EntityData data;

        private NightRestTask(EntityAnimal animal, EntityData data)
        {
            this.animal = animal;
            this.data = data;
            setMutexBits(1);
        }

        @Override
        public boolean shouldExecute()
        {
            return canRest();
        }

        @Override
        public boolean shouldContinueExecuting()
        {
            return canRest();
        }

        @Override
        public void startExecuting()
        {
            animal.getNavigator().clearPath();
        }

        private boolean canRest()
        {
            return animal.isEntityAlive()
                    && data.getHunger() >= 75
                    && !animal.isInLove()
                    && !animal.isBurning()
                    && animal.getAttackTarget() == null
                    && isRestTime(animal);
        }
    }
}
