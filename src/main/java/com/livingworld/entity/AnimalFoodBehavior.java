package com.livingworld.entity;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Grass foraging task for herbivores. Navigation is owned by the AI scheduler
 * instead of being overwritten by the periodic entity tracker.
 */
public final class AnimalFoodBehavior
{
    private static final int SEARCH_RADIUS = 6;
    private static final int HUNGER_THRESHOLD = 75;
    private static final int FOOD_VALUE = 30;
    private static final double EAT_DISTANCE_SQ = 2.25D;
    private static final Set<EntityAnimal> INSTALLED =
            Collections.newSetFromMap(new WeakHashMap<EntityAnimal, Boolean>());

    private AnimalFoodBehavior()
    {
    }

    public static void update(EntityAnimal animal, EntityData data, World world)
    {
        if (animal == null || data == null || world == null || world.isRemote
                || !animal.isEntityAlive() || data.getDiet() != AnimalDiet.HERBIVORE)
        {
            return;
        }

        if (INSTALLED.add(animal))
        {
            animal.tasks.addTask(4, new GrassForageTask(animal, data));
        }
    }

    private static final class GrassForageTask extends EntityAIBase
    {
        private final EntityAnimal animal;
        private final EntityData data;
        private BlockPos target;
        private int searchCooldown;

        private GrassForageTask(EntityAnimal animal, EntityData data)
        {
            this.animal = animal;
            this.data = data;
            // Spread initial scans across time instead of making every animal
            // search for food on the same AI tick.
            this.searchCooldown = Math.floorMod(animal.getUniqueID().hashCode(), 200);
            setMutexBits(1);
        }

        @Override
        public boolean shouldExecute()
        {
            if (!canForage())
            {
                searchCooldown = 0;
                return false;
            }

            if (searchCooldown > 0)
            {
                searchCooldown--;
                return false;
            }

            searchCooldown = 200;
            target = findGrass();
            return target != null;
        }

        @Override
        public boolean shouldContinueExecuting()
        {
            return canForage() && isValidGrass(target);
        }

        @Override
        public void startExecuting()
        {
            moveToGrass();
        }

        @Override
        public void updateTask()
        {
            if (!isValidGrass(target))
            {
                target = null;
                animal.getNavigator().clearPath();
                return;
            }

            if (animal.getDistanceSq(target.getX() + 0.5D, target.getY() + 1.0D,
                    target.getZ() + 0.5D) <= EAT_DISTANCE_SQ)
            {
                World world = animal.world;
                if (world.isBlockLoaded(target)
                        && world.getBlockState(target).getBlock() == Blocks.GRASS)
                {
                    world.setBlockState(target, Blocks.DIRT.getDefaultState(), 2);
                    data.feed(FOOD_VALUE);
                }
                target = null;
                animal.getNavigator().clearPath();
                return;
            }

            if (animal.getNavigator().noPath())
            {
                moveToGrass();
            }
        }

        @Override
        public void resetTask()
        {
            target = null;
            animal.getNavigator().clearPath();
        }

        private boolean canForage()
        {
            return animal.isEntityAlive() && data.getHunger() < HUNGER_THRESHOLD
                    && !animal.isInLove() && !animal.isBurning()
                    && animal.getAttackTarget() == null;
        }

        private boolean isValidGrass(BlockPos pos)
        {
            return pos != null && animal.world.isBlockLoaded(pos)
                    && animal.world.getBlockState(pos).getBlock() == Blocks.GRASS;
        }

        private BlockPos findGrass()
        {
            BlockPos origin = animal.getPosition();
            BlockPos.MutableBlockPos candidate = new BlockPos.MutableBlockPos();
            BlockPos nearest = null;
            double nearestDistance = SEARCH_RADIUS * SEARCH_RADIUS;

            for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++)
            {
                for (int y = -2; y <= 2; y++)
                {
                    for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++)
                    {
                        // Ignore the corners of the search square; they are
                        // outside the actual circular foraging radius.
                        if (x * x + z * z > SEARCH_RADIUS * SEARCH_RADIUS)
                        {
                            continue;
                        }

                        candidate.setPos(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                        if (!animal.world.isBlockLoaded(candidate)
                                || animal.world.getBlockState(candidate).getBlock() != Blocks.GRASS)
                        {
                            continue;
                        }

                        double distance = animal.getDistanceSq(
                                candidate.getX() + 0.5D,
                                candidate.getY() + 1.0D,
                                candidate.getZ() + 0.5D);
                        if (distance < nearestDistance)
                        {
                            nearestDistance = distance;
                            nearest = new BlockPos(candidate);
                        }
                    }
                }
            }

            return nearest;
        }

        private void moveToGrass()
        {
            if (target != null)
            {
                animal.getNavigator().tryMoveToXYZ(
                        target.getX() + 0.5D, target.getY() + 1.0D,
                        target.getZ() + 0.5D, 1.0D);
            }
        }
    }
}
