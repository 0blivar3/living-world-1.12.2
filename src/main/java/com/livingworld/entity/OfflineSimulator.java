package com.livingworld.entity;

import java.util.Collection;

/**
 * Runs lightweight state simulation for entities that are currently unloaded.
 *
 * Offline births are intentionally not simulated in this release: without
 * safely materializing a real Minecraft entity, a virtual offspring would
 * exist only in saved data and could never participate in the world.
 */
public final class OfflineSimulator
{
    private OfflineSimulator()
    {
    }

    public static void simulate(Collection<EntityData> entities, long worldTime)
    {
        for (EntityData data : entities)
        {
            if (!data.isLoaded())
            {
                data.simulateOffline(worldTime);
            }
        }
    }
}
