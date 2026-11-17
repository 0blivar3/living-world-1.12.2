package com.livingworld.entity;

import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.world.World;


/**
 * Daily schedule and loaded-world behavior for vanilla villagers.
 */
public final class VillagerBehavior
{
    public static final String SLEEPING = "SLEEPING";
    public static final String WORKING = "WORKING";
    public static final String SOCIALIZING = "SOCIALIZING";
    public static final String WANDERING = "WANDERING";
    public static final String FLEEING = "FLEEING";

    private static final double THREAT_RADIUS = 12.0D;
    private static final double FLEE_DISTANCE = 12.0D;

    private VillagerBehavior()
    {
    }

    /**
     * Determines a villager's current activity from the Minecraft day cycle.
     */
    public static String getActivity(long worldTime)
    {
        long timeOfDay = Math.floorMod(worldTime, 24000L);

        if (timeOfDay >= 13000L || timeOfDay < 1000L)
        {
            return SLEEPING;
        }

        if (timeOfDay >= 2000L && timeOfDay < 9000L)
        {
            return WORKING;
        }

        if (timeOfDay >= 9000L && timeOfDay < 12000L)
        {
            return SOCIALIZING;
        }

        return WANDERING;
    }

    /**
     * Applies schedule destinations and threat reactions to a loaded villager.
     * This does not disable vanilla AI: the navigator receives a destination,
     * while vanilla behavior remains responsible for path execution.
     */
    public static void updateLoadedVillager(EntityVillager villager, EntityData data, World world, ThreatIndex threats)
    {
        if (villager == null || data == null || world == null || world.isRemote
                || villager.isDead)
        {
            return;
        }

        EntityMob threat = threats == null ? null : threats.findNearest(villager, THREAT_RADIUS);

        if (threat != null)
        {
            fleeFrom(villager, threat);
            return;
        }

        if (!villager.getNavigator().noPath())
        {
            return;
        }

        String activity = data.getVillagerActivity();
        if (WORKING.equals(activity) && data.hasWorkstation())
        {
            villager.getNavigator().tryMoveToXYZ(
                    data.getWorkstationX() + 0.5D,
                    data.getWorkstationY(),
                    data.getWorkstationZ() + 0.5D,
                    0.8D);
        }
        else if (SLEEPING.equals(activity) && data.hasHome())
        {
            villager.getNavigator().tryMoveToXYZ(
                    data.getHomeX() + 0.5D,
                    data.getHomeY(),
                    data.getHomeZ() + 0.5D,
                    0.7D);
        }
    }

    private static void fleeFrom(EntityVillager villager, EntityMob threat)
    {
        double dx = villager.posX - threat.posX;
        double dz = villager.posZ - threat.posZ;
        double length = Math.sqrt(dx * dx + dz * dz);

        if (length < 0.001D)
        {
            double angle = Math.toRadians(villager.rotationYaw + 180.0F);
            dx = Math.cos(angle);
            dz = Math.sin(angle);
            length = 1.0D;
        }

        double targetX = villager.posX + (dx / length) * FLEE_DISTANCE;
        double targetZ = villager.posZ + (dz / length) * FLEE_DISTANCE;

        // Do not ask pathfinding to navigate into an unloaded chunk.
        net.minecraft.util.math.BlockPos destination =
                new net.minecraft.util.math.BlockPos(targetX, villager.posY, targetZ);
        if (!villager.world.isBlockLoaded(destination))
        {
            return;
        }

        villager.getNavigator().clearPath();
        villager.getNavigator().tryMoveToXYZ(targetX, villager.posY, targetZ, 1.25D);
    }
}
