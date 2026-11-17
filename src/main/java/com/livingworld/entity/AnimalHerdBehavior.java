package com.livingworld.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;
import java.util.UUID;

/**
 * Herd cohesion, predator detection and coordinated fleeing for loaded animals.
 */
public final class AnimalHerdBehavior
{
    private static final double FOLLOW_START_DISTANCE_SQ = 25.0D;
    private static final double MAX_FOLLOW_DISTANCE_SQ = 4096.0D;
    private static final double FOLLOW_SPEED = 0.85D;
    private static final double DANGER_RADIUS = 12.0D;
    private static final double FLEE_DISTANCE = 14.0D;
    private static final double FLEE_SPEED = 1.25D;
    private static final long ALARM_DURATION = 200L;

    private AnimalHerdBehavior() { }

    public static void update(EntityAnimal animal, EntityData data, AnimalHerd herd, World world, long worldTime)
    {
        if (animal == null || data == null || herd == null || world == null || world.isRemote
                || animal.isDead || herd.getMembers().size() < 2) return;

        // Threat scans are staggered per animal and run at most once every
        // 200 ticks, rather than querying nearby mobs on every tracker update.
        long lastThreatScan = animal.getEntityData().getLong("LivingWorldLastHerdThreatScan");
        int scanOffset = Math.floorMod(animal.getUniqueID().hashCode(), 200);
        if (worldTime - lastThreatScan >= 200L + scanOffset)
        {
            animal.getEntityData().setLong("LivingWorldLastHerdThreatScan", worldTime);
            List<EntityMob> threats = world.getEntitiesWithinAABB(
                    EntityMob.class, animal.getEntityBoundingBox().grow(DANGER_RADIUS));
            EntityMob nearestThreat = null;
            double nearestDistance = DANGER_RADIUS * DANGER_RADIUS;
            for (EntityMob threat : threats)
            {
                if (threat.isDead) continue;
                double distance = animal.getDistanceSq(threat);
                if (distance < nearestDistance)
                {
                    nearestDistance = distance;
                    nearestThreat = threat;
                }
            }

            if (nearestThreat != null)
            {
                herd.raiseAlarm(nearestThreat.posX, nearestThreat.posY, nearestThreat.posZ,
                        worldTime + ALARM_DURATION);
            }
        }

        if (herd.isAlarmActive(worldTime))
        {
            fleeFromThreat(animal, herd, world);
            return;
        }

        // Resting animals stay with the group unless a threat interrupts their rest.
        // The daily routine is checked through the entity's current schedule.
        if (AnimalDailyRoutine.isRestTime(animal) && data.getHunger() >= 75)
        {
            animal.getNavigator().clearPath();
            return;
        }

        // Do not replace a route currently owned by a feeding or other AI task.
        if (!animal.getNavigator().noPath())
        {
            return;
        }

        UUID leaderId = herd.getLeaderId();
        if (leaderId == null || leaderId.equals(animal.getUniqueID())) return;

        // In 1.12.2, UUID lookup is exposed by WorldServer rather than World.
        Entity leader = world instanceof WorldServer
                ? ((WorldServer) world).getEntityFromUuid(leaderId)
                : null;
        if (leader instanceof EntityAnimal && !leader.isDead)
        {
            double distance = animal.getDistanceSq(leader);
            if (distance > FOLLOW_START_DISTANCE_SQ && distance <= MAX_FOLLOW_DISTANCE_SQ)
                animal.getNavigator().tryMoveToEntityLiving(leader, FOLLOW_SPEED);
            return;
        }

        double distanceToCenter = animal.getDistanceSq(
                herd.getCenterX(), herd.getCenterY(), herd.getCenterZ());
        if (distanceToCenter <= FOLLOW_START_DISTANCE_SQ
                || distanceToCenter > MAX_FOLLOW_DISTANCE_SQ) return;

        BlockPos destination = new BlockPos(herd.getCenterX(), herd.getCenterY(), herd.getCenterZ());
        if (world.isBlockLoaded(destination))
            animal.getNavigator().tryMoveToXYZ(
                    herd.getCenterX(), herd.getCenterY(), herd.getCenterZ(), FOLLOW_SPEED);
    }

    private static void fleeFromThreat(EntityAnimal animal, AnimalHerd herd, World world)
    {
        double dx = animal.posX - herd.getThreatX();
        double dz = animal.posZ - herd.getThreatZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.001D)
        {
            dx = animal.getRNG().nextDouble() - 0.5D;
            dz = animal.getRNG().nextDouble() - 0.5D;
            length = Math.sqrt(dx * dx + dz * dz);
        }

        double targetX = animal.posX + dx / length * FLEE_DISTANCE;
        double targetZ = animal.posZ + dz / length * FLEE_DISTANCE;
        BlockPos destination = new BlockPos(targetX, animal.posY, targetZ);
        if (world.isBlockLoaded(destination))
        {
            animal.getNavigator().clearPath();
            animal.getNavigator().tryMoveToXYZ(targetX, animal.posY, targetZ, FLEE_SPEED);
        }
    }
}
