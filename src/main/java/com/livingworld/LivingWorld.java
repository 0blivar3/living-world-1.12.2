package com.livingworld;

import net.minecraft.init.Blocks;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

@Mod(modid = LivingWorld.MODID, name = LivingWorld.NAME, version = LivingWorld.VERSION)
public class LivingWorld
{
    public static final String MODID = "livingworld";
    public static final String NAME = "Living World";
    public static final String VERSION = "1.0";

    private static Logger logger;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
        logger = event.getModLog();
    }

    @EventHandler
    public void init(FMLInitializationEvent event)
    {
        logger.info("Living World initialized.");
        logger.info("DIRT BLOCK >> {}", Blocks.DIRT.getRegistryName());
    }
}
