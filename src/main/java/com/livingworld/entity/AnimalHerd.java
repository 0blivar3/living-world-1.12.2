package com.livingworld.entity;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Persistent group of animals of the same registered entity type.
 */
public class AnimalHerd
{
    private final UUID id;
    private final String entityType;
    private final int dimension;
    private final List<UUID> members = new ArrayList<UUID>();
    private UUID leaderId;
    private double centerX;
    private double centerY;
    private double centerZ;
    private long lastUpdateWorldTime;

    public AnimalHerd(UUID id, String entityType, int dimension)
    {
        this.id = id;
        this.entityType = entityType;
        this.dimension = dimension;
    }

    public void addMember(UUID memberId)
    {
        if (memberId != null && !members.contains(memberId))
        {
            members.add(memberId);
            if (leaderId == null)
            {
                leaderId = memberId;
            }
        }
    }

    public void removeMember(UUID memberId)
    {
        members.remove(memberId);
        if (memberId != null && memberId.equals(leaderId))
        {
            leaderId = members.isEmpty() ? null : members.get(0);
        }
    }

    public void updateCenter(double x, double y, double z, long worldTime)
    {
        centerX = x;
        centerY = y;
        centerZ = z;
        lastUpdateWorldTime = worldTime;
    }

    public NBTTagCompound writeToNBT()
    {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Id", id.toString());
        tag.setString("EntityType", entityType);
        tag.setInteger("Dimension", dimension);
        tag.setDouble("CenterX", centerX);
        tag.setDouble("CenterY", centerY);
        tag.setDouble("CenterZ", centerZ);
        tag.setLong("LastUpdate", lastUpdateWorldTime);
        if (leaderId != null)
        {
            tag.setString("Leader", leaderId.toString());
        }

        NBTTagList list = new NBTTagList();
        for (UUID member : members)
        {
            NBTTagCompound memberTag = new NBTTagCompound();
            memberTag.setString("Id", member.toString());
            list.appendTag(memberTag);
        }
        tag.setTag("Members", list);
        return tag;
    }

    public static AnimalHerd readFromNBT(NBTTagCompound tag)
    {
        UUID id = UUID.fromString(tag.getString("Id"));
        AnimalHerd herd = new AnimalHerd(
                id, tag.getString("EntityType"), tag.getInteger("Dimension"));
        herd.centerX = tag.getDouble("CenterX");
        herd.centerY = tag.getDouble("CenterY");
        herd.centerZ = tag.getDouble("CenterZ");
        herd.lastUpdateWorldTime = tag.getLong("LastUpdate");

        if (tag.hasKey("Leader"))
        {
            try
            {
                herd.leaderId = UUID.fromString(tag.getString("Leader"));
            }
            catch (IllegalArgumentException ignored)
            {
                herd.leaderId = null;
            }
        }

        NBTTagList list = tag.getTagList("Members", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++)
        {
            try
            {
                herd.addMember(UUID.fromString(list.getCompoundTagAt(i).getString("Id")));
            }
            catch (IllegalArgumentException ignored)
            {
                // Ignore malformed member IDs.
            }
        }
        if (herd.leaderId == null && !herd.members.isEmpty())
        {
            herd.leaderId = herd.members.get(0);
        }
        return herd;
    }

    public UUID getId() { return id; }
    public String getEntityType() { return entityType; }
    public int getDimension() { return dimension; }
    public List<UUID> getMembers() { return Collections.unmodifiableList(members); }
    public UUID getLeaderId() { return leaderId; }
    public double getCenterX() { return centerX; }
    public double getCenterY() { return centerY; }
    public double getCenterZ() { return centerZ; }
    public long getLastUpdateWorldTime() { return lastUpdateWorldTime; }
}
