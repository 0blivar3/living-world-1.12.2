package com.livingworld.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * Makes loaded herd members loosely follow their leader or the herd's
 * last known center when the leader is not currently loaded.
 */
public final class AnimalHerdBehavior
{
    private static final double FOLLOW_START_DISTANCE_SQ = 25.0D;
    private static final double MAX_FOLLOW_DISTANCE_SQ = 4096.0D;
    private static final double FOLLOW_SPEED = 0.85D;

    private AnimalHerdBehavior()
    {
    }

    public static void update(EntityAnimal animal, AnimalHerd herd, World world)
    {
        if (animal == null || herd == null || world == null || world.isRemote
                || animal.isDead || herd.getMembers().size() < 2)
        {
            return;
        }

        UUID leaderId = herd.getLeaderId();
        if (leaderId == null || leaderId.equals(animal.getUniqueID()))
        {
            return;
        }

        Entity leader = world.getEntityFromUuid(leaderId);
        if (leader instanceof EntityAnimal && !leader.isDead)
        {
            double distance = animal.getDistanceSq(leader);
            if (distance > FOLLOW_START_DISTANCE_SQ && distance <= MAX_FOLLOW_DISTANCE_SQ)
            {
                animal.getNavigator().tryMoveToEntityLiving(leader, FOLLOW_SPEED);
            }
            return;
        }

        // The leader may be in an unloaded chunk. Follow the saved herd center
        // only when that destination is already loaded; never force-load chunks.
        double distanceToCenter = animal.getDistanceSq(
                herd.getCenterX(), herd.getCenterY(), herd.getCenterZ());
        if (distanceToCenter <= FOLLOW_START_DISTANCE_SQ
                || distanceToCenter > MAX_FOLLOW_DISTANCE_SQ)
        {
            return;
        }

        BlockPos destination = new BlockPos(
                herd.getCenterX(), herd.getCenterY(), herd.getCenterZ());
        if (world.isBlockLoaded(destination))
        {
            animal.getNavigator().tryMoveToXYZ(
                    herd.getCenterX(), herd.getCenterY(), herd.getCenterZ(), FOLLOW_SPEED);
        }
    }
}
