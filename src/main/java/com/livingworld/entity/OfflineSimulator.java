package com.livingworld.entity;

import java.util.Collection;

/**
 * Runs lightweight simulation for entities that are currently not loaded.
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
