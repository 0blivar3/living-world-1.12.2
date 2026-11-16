package com.livingworld.entity;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Finds a nearby vanilla work block for a villager's profession.
 *
 * This is an approximation for Minecraft 1.12.2: vanilla villagers do not
 * expose a dedicated POI/workstation system like newer Minecraft versions.
 */
public final class VillagerWorkstationFinder
{
    private static final int HORIZONTAL_RADIUS = 8;
    private static final int VERTICAL_RADIUS = 3;

    private VillagerWorkstationFinder()
    {
    }

    public static BlockPos findNearest(EntityVillager villager, World world)
    {
        if (villager == null || world == null || world.isRemote)
        {
            return null;
        }

        int profession = villager.getProfession();
        BlockPos origin = new BlockPos(villager);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int x = -HORIZONTAL_RADIUS; x <= HORIZONTAL_RADIUS; x++)
        {
            for (int y = -VERTICAL_RADIUS; y <= VERTICAL_RADIUS; y++)
            {
                for (int z = -HORIZONTAL_RADIUS; z <= HORIZONTAL_RADIUS; z++)
                {
                    BlockPos candidate = origin.add(x, y, z);

                    // Never force-load chunks just to search for a work block.
                    if (!world.isBlockLoaded(candidate))
                    {
                        continue;
                    }

                    IBlockState state = world.getBlockState(candidate);
                    if (!isWorkstation(profession, state.getBlock()))
                    {
                        continue;
                    }

                    double distance = villager.getDistanceSqToCenter(candidate);
                    if (distance < bestDistance)
                    {
                        bestDistance = distance;
                        best = candidate.toImmutable();
                    }
                }
            }
        }

        return best;
    }

    private static boolean isWorkstation(int profession, net.minecraft.block.Block block)
    {
        switch (profession)
        {
            case 0: // Farmer
                return block == Blocks.FARMLAND;
            case 1: // Librarian
                return block == Blocks.BOOKSHELF;
            case 2: // Priest
                return block == Blocks.BREWING_STAND;
            case 3: // Blacksmith
            case 4: // Butcher
                return block == Blocks.FURNACE;
            default:
                return false;
        }
    }
}
