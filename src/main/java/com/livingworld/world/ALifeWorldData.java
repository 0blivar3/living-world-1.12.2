package com.livingworld.world;

import com.livingworld.entity.EntityData;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
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
        NBTTagList list = compound.getTagList("Entities", Constants.NBT.TAG_COMPOUND);

        for (int i = 0; i < list.tagCount(); i++)
        {
            NBTTagCompound entityTag = list.getCompoundTagAt(i);
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
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound)
    {
        NBTTagList list = new NBTTagList();

        for (EntityData data : entities.values())
        {
            list.appendTag(data.writeToNBT());
        }

        compound.setTag("Entities", list);
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

    public Map<UUID, EntityData> getEntities()
    {
        return entities;
    }
}
