package com.livingworld.entity;

import net.minecraft.block.BlockTallGrass;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Natural ground-foraging for chickens and pigs.
 *
 * Chickens peck at patches of tall grass. Pigs root through tall grass and
 * mushrooms. Food is consumed from loaded blocks only; no chunks are loaded.
 */
public final class AnimalNaturalForagingBehavior
{
    private static final int SEARCH_RADIUS = 8;
    private static final int HUNGER_THRESHOLD = 75;
    private static final int FOOD_VALUE = 30;
    private static final double EAT_DISTANCE_SQ = 2.25D;

    private static final Set<EntityAnimal> INSTALLED =
            Collections.newSetFromMap(new WeakHashMap<EntityAnimal, Boolean>());

    private AnimalNaturalForagingBehavior()
    {
    }

    public static void update(EntityAnimal animal, EntityData data)
    {
        if (animal == null || data == null || animal.world == null
                || animal.world.isRemote || !animal.isEntityAlive())
        {
            return;
        }

        if (!(animal instanceof net.minecraft.entity.passive.EntityChicken)
                && !(animal instanceof net.minecraft.entity.passive.EntityPig))
        {
            return;
        }

        if (INSTALLED.add(animal))
        {
            // Higher priority than dropped-item foraging: natural food comes first.
            animal.tasks.addTask(3, new GroundForageTask(animal, data));
        }
    }

    private static boolean isFood(EntityAnimal animal, IBlockState state)
    {
        if (state == null)
        {
            return false;
        }

        if (state.getBlock() == Blocks.TALLGRASS)
        {
            return state.getValue(BlockTallGrass.TYPE) != BlockTallGrass.EnumType.DEAD_BUSH;
        }

        return animal instanceof net.minecraft.entity.passive.EntityPig
                && (state.getBlock() == Blocks.RED_MUSHROOM
                || state.getBlock() == Blocks.BROWN_MUSHROOM);
    }

    private static final class GroundForageTask extends EntityAIBase
    {
        private final EntityAnimal animal;
        private final EntityData data;
        private BlockPos target;
        private int searchCooldown;

        private GroundForageTask(EntityAnimal animal, EntityData data)
        {
            this.animal = animal;
            this.data = data;
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

            // Avoid scanning the surrounding blocks every single AI tick.
            searchCooldown = 100;
            target = findFood();
            return target != null;
        }

        @Override
        public boolean shouldContinueExecuting()
        {
            return canForage() && isValidFood(target);
        }

        @Override
        public void startExecuting()
        {
            moveToFood();
        }

        @Override
        public void updateTask()
        {
            if (!isValidFood(target))
            {
                target = null;
                animal.getNavigator().clearPath();
                return;
            }

            if (animal.getDistanceSq(
                    target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D)
                    <= EAT_DISTANCE_SQ)
            {
                World world = animal.world;
                if (world.isBlockLoaded(target) && isFood(animal, world.getBlockState(target)))
                {
                    world.setBlockToAir(target);
                    data.feed(FOOD_VALUE);
                }

                target = null;
                animal.getNavigator().clearPath();
                return;
            }

            if (animal.getNavigator().noPath())
            {
                moveToFood();
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
            return animal.isEntityAlive()
                    && data.getHunger() < HUNGER_THRESHOLD
                    && !animal.isInLove()
                    && !animal.isBurning()
                    && animal.getAttackTarget() == null;
        }

        private boolean isValidFood(BlockPos pos)
        {
            return pos != null && animal.world.isBlockLoaded(pos)
                    && isFood(animal, animal.world.getBlockState(pos));
        }

        private BlockPos findFood()
        {
            BlockPos origin = animal.getPosition();
            BlockPos nearest = null;
            double nearestDistance = SEARCH_RADIUS * SEARCH_RADIUS;

            for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++)
            {
                for (int y = -2; y <= 2; y++)
                {
                    for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++)
                    {
                        BlockPos candidate = origin.add(x, y, z);
                        if (!animal.world.isBlockLoaded(candidate)
                                || !isFood(animal, animal.world.getBlockState(candidate)))
                        {
                            continue;
                        }

                        double distance = animal.getDistanceSq(
                                candidate.getX() + 0.5D,
                                candidate.getY() + 0.5D,
                                candidate.getZ() + 0.5D);
                        if (distance < nearestDistance)
                        {
                            nearestDistance = distance;
                            nearest = candidate;
                        }
                    }
                }
            }

            return nearest;
        }

        private void moveToFood()
        {
            if (target != null)
            {
                animal.getNavigator().tryMoveToXYZ(
                        target.getX() + 0.5D,
                        target.getY(),
                        target.getZ() + 0.5D,
                        1.0D);
            }
        }
    }
}
