package com.livingworld.entity;

/**
 * Provides a lightweight daily schedule for villagers in the offline simulation.
 */
public final class VillagerBehavior
{
    public static final String SLEEPING = "SLEEPING";
    public static final String WORKING = "WORKING";
    public static final String SOCIALIZING = "SOCIALIZING";
    public static final String WANDERING = "WANDERING";

    private VillagerBehavior()
    {
    }

    /**
     * Determines a villager's current activity from the Minecraft day cycle.
     * The supplied time is measured in world ticks.
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
}
