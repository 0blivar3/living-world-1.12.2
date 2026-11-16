package com.livingworld.entity;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Basic foraging behavior for herbivores in loaded chunks.
 *
 * Food is only searched for around already-loaded animals. This class never
 * force-loads chunks and does not create entities for offline simulation.
 */
public final class AnimalFoodBehavior
{
    private static final int SEARCH_RADIUS = 8;
    private static final int HUNGER_THRESHOLD = 75;
    private static final int FOOD_VALUE = 30;

    private AnimalFoodBehavior()
    {
    }

    public static void update(EntityAnimal animal, EntityData data, World world)
    {
        if (animal == null || data == null || world == null || world.isRemote
                || !animal.isEntityAlive()
                || data.getDiet() != AnimalDiet.HERBIVORE
                || data.getHunger() >= HUNGER_THRESHOLD)
        {
            return;
        }

        BlockPos food = findGrass(animal, world);
        if (food == null)
        {
            return;
        }

        double dx = food.getX() + 0.5D - animal.posX;
        double dz = food.getZ() + 0.5D - animal.posZ;
        double distanceSq = dx * dx + dz * dz;

        if (distanceSq <= 2.25D
                && Math.abs(animal.posY - (food.getY() + 1.0D)) <= 2.0D)
        {
            IBlockState state = world.getBlockState(food);
            if (state.getBlock() == Blocks.GRASS)
            {
                world.setBlockState(food, Blocks.DIRT.getDefaultState(), 2);
                data.feed(FOOD_VALUE);
                animal.getNavigator().clearPath();
            }
            return;
        }

        animal.getNavigator().tryMoveToXYZ(
                food.getX() + 0.5D,
                food.getY() + 1.0D,
                food.getZ() + 0.5D,
                1.0D);
    }

    private static BlockPos findGrass(EntityAnimal animal, World world)
    {
        BlockPos origin = animal.getPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++)
        {
            for (int y = -2; y <= 2; y++)
            {
                for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++)
                {
                    BlockPos candidate = origin.add(x, y, z);
                    if (!world.isBlockLoaded(candidate)
                            || world.getBlockState(candidate).getBlock() != Blocks.GRASS)
                    {
                        continue;
                    }

                    double dx = candidate.getX() + 0.5D - animal.posX;
                    double dy = candidate.getY() + 1.0D - animal.posY;
                    double dz = candidate.getZ() + 0.5D - animal.posZ;
                    double distance = dx * dx + dy * dy + dz * dz;

                    if (distance < bestDistance)
                    {
                        bestDistance = distance;
                        best = candidate;
                    }
                }
            }
        }

        return best;
    }
}
