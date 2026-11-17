package com.livingworld.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityMob;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spatial index of loaded hostile mobs, rebuilt once per A-Life update.
 * Queries inspect only nearby 16-block cells instead of asking the world
 * to search its entity list separately for every animal and villager.
 */
public final class ThreatIndex
{
    private static final double CELL_SIZE = 16.0D;
    private final Map<Cell, List<EntityMob>> cells = new HashMap<Cell, List<EntityMob>>();

    // Used only for map lookups; it is never inserted into the map.
    // Reusing it avoids creating a Cell object for every neighboring cell query.
    private final Cell lookupCell = new Cell(0, 0, 0);

    public void clear()
    {
        cells.clear();
    }

    public void add(EntityMob mob)
    {
        if (mob == null || mob.isDead)
        {
            return;
        }

        Cell cell = Cell.from(mob.posX, mob.posY, mob.posZ);
        List<EntityMob> bucket = cells.get(cell);
        if (bucket == null)
        {
            bucket = new ArrayList<EntityMob>();
            cells.put(cell, bucket);
        }
        bucket.add(mob);
    }

    public EntityMob findNearest(Entity origin, double radius)
    {
        if (origin == null || radius < 0.0D)
        {
            return null;
        }

        int range = (int) Math.ceil(radius / CELL_SIZE);
        int centerX = (int) Math.floor(origin.posX / CELL_SIZE);
        int centerY = (int) Math.floor(origin.posY / CELL_SIZE);
        int centerZ = (int) Math.floor(origin.posZ / CELL_SIZE);
        double radiusSq = radius * radius;
        double nearestSq = radiusSq;
        EntityMob nearest = null;

        for (int dx = -range; dx <= range; dx++)
        {
            for (int dy = -range; dy <= range; dy++)
            {
                for (int dz = -range; dz <= range; dz++)
                {
                    List<EntityMob> bucket = cells.get(
                            lookupCell.set(centerX + dx, centerY + dy, centerZ + dz));
                    if (bucket == null)
                    {
                        continue;
                    }

                    for (EntityMob mob : bucket)
                    {
                        if (mob.isDead)
                        {
                            continue;
                        }

                        double distance = origin.getDistanceSq(mob);
                        if (distance < nearestSq)
                        {
                            nearestSq = distance;
                            nearest = mob;
                        }
                    }
                }
            }
        }

        return nearest;
    }

    private static final class Cell
    {
        private int x;
        private int y;
        private int z;

        private Cell(int x, int y, int z)
        {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private static Cell from(double x, double y, double z)
        {
            return new Cell((int) Math.floor(x / CELL_SIZE),
                    (int) Math.floor(y / CELL_SIZE),
                    (int) Math.floor(z / CELL_SIZE));
        }

        private Cell set(int x, int y, int z)
        {
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }

        @Override
        public boolean equals(Object object)
        {
            if (this == object) return true;
            if (!(object instanceof Cell)) return false;
            Cell other = (Cell) object;
            return x == other.x && y == other.y && z == other.z;
        }

        @Override
        public int hashCode()
        {
            int result = x;
            result = 31 * result + y;
            result = 31 * result + z;
            return result;
        }
    }
}
