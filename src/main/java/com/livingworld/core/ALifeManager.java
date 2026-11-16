package com.livingworld.core;

import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Central entry point for the A-Life simulation.
 *
 * The manager currently observes server world ticks. Actual entity tracking
 * and simulation systems will be added in subsequent steps.
 */
public class ALifeManager
{
    private long processedWorldTicks;

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote)
        {
            return;
        }

        processWorldTick(event.world);
    }

    private void processWorldTick(World world)
    {
        processedWorldTicks++;
    }

    public long getProcessedWorldTicks()
    {
        return processedWorldTicks;
    }
}
