package com.livingworld.world;

import com.livingworld.entity.AnimalHerd;
import com.livingworld.entity.EntityData;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent A-Life data stored in the world's save folder.
 */
public class ALifeWorldData extends WorldSavedData
{
    public static final String DATA_NAME = "livingworld_alife";

    private final Map<UUID, EntityData> entities = new HashMap<UUID, EntityData>();
    private final Map<UUID, AnimalHerd> herds = new HashMap<UUID, AnimalHerd>();

    public ALifeWorldData()
    {
        super(DATA_NAME);
    }

    public ALifeWorldData(String name)
    {
        super(name);
    }

    @Override
    public void readFromNBT(NBTTagCompound compound)
    {
        entities.clear();
        herds.clear();

        NBTTagList entityList = compound.getTagList("Entities", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < entityList.tagCount(); i++)
        {
            NBTTagCompound entityTag = entityList.getCompoundTagAt(i);
            try
            {
                EntityData data = EntityData.readFromNBT(entityTag);
                entities.put(data.getId(), data);
            }
            catch (IllegalArgumentException exception)
            {
                // Ignore malformed records rather than preventing the world from loading.
            }
        }

        NBTTagList herdList = compound.getTagList("Herds", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < herdList.tagCount(); i++)
        {
            try
            {
                AnimalHerd herd = AnimalHerd.readFromNBT(herdList.getCompoundTagAt(i));
                herds.put(herd.getId(), herd);
            }
            catch (IllegalArgumentException exception)
            {
                // Ignore malformed herd records.
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound)
    {
        NBTTagList entityList = new NBTTagList();
        for (EntityData data : entities.values())
        {
            entityList.appendTag(data.writeToNBT());
        }
        compound.setTag("Entities", entityList);

        NBTTagList herdList = new NBTTagList();
        for (AnimalHerd herd : herds.values())
        {
            herdList.appendTag(herd.writeToNBT());
        }
        compound.setTag("Herds", herdList);
        return compound;
    }

    public void setEntities(Collection<EntityData> updatedEntities)
    {
        entities.clear();
        for (EntityData data : updatedEntities)
        {
            entities.put(data.getId(), data);
        }
        markDirty();
    }

    public void setHerds(Collection<AnimalHerd> updatedHerds)
    {
        herds.clear();
        for (AnimalHerd herd : updatedHerds)
        {
            herds.put(herd.getId(), herd);
        }
        markDirty();
    }

    /**
     * Marks the shared simulation maps as changed so Minecraft persists them.
     */
    public void markSimulationDataDirty()
    {
        markDirty();
    }

    public Map<UUID, EntityData> getEntities()
    {
        return entities;
    }

    public Map<UUID, AnimalHerd> getHerds()
    {
        return herds;
    }
}
