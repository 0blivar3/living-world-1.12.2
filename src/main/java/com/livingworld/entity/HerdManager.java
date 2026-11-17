package com.livingworld.entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Forms and maintains lightweight animal herds without loading chunks.
 */
public final class HerdManager
{
    private static final double HERD_RADIUS = 16.0D;
    private static final double JOIN_RADIUS = 24.0D;
    private static final int MIN_HERD_SIZE = 2;
    private static final int MAX_HERD_SIZE = 8;

    private HerdManager()
    {
    }

    public static void update(Map<UUID, EntityData> entities,
                              Map<UUID, AnimalHerd> herds,
                              int dimension,
                              long worldTime)
    {
        Set<UUID> assigned = new HashSet<UUID>();
        Map<String, List<EntityData>> animalsByType = new HashMap<String, List<EntityData>>();
        Map<SpatialCell, List<EntityData>> spatialIndex =
                new HashMap<SpatialCell, List<EntityData>>();
        for (EntityData data : entities.values())
        {
            if (!isHerdAnimal(data, dimension))
            {
                continue;
            }

            List<EntityData> sameType = animalsByType.get(data.getEntityType());
            if (sameType == null)
            {
                sameType = new ArrayList<EntityData>();
                animalsByType.put(data.getEntityType(), sameType);
            }
            sameType.add(data);

            SpatialCell cell = SpatialCell.from(data);
            List<EntityData> bucket = spatialIndex.get(cell);
            if (bucket == null)
            {
                bucket = new ArrayList<EntityData>();
                spatialIndex.put(cell, bucket);
            }
            bucket.add(data);
        }

        // Keep existing herds stable, removing only members that are no longer valid.
        Iterator<Map.Entry<UUID, AnimalHerd>> herdIterator = herds.entrySet().iterator();
        while (herdIterator.hasNext())
        {
            AnimalHerd herd = herdIterator.next().getValue();
            if (herd.getDimension() != dimension)
            {
                continue;
            }

            List<UUID> members = new ArrayList<UUID>(herd.getMembers());
            for (UUID memberId : members)
            {
                EntityData data = entities.get(memberId);
                if (!isHerdAnimal(data, dimension) || !herd.getEntityType().equals(data.getEntityType()))
                {
                    herd.removeMember(memberId);
                }
                else
                {
                    assigned.add(memberId);
                }
            }

            if (herd.getMembers().size() < MIN_HERD_SIZE)
            {
                herdIterator.remove();
            }
            else
            {
                updateCenter(herd, entities, worldTime);
            }
        }

        // First, allow nearby unassigned animals to join an established herd.
        for (AnimalHerd herd : herds.values())
        {
            if (herd.getDimension() != dimension || herd.getMembers().size() >= MAX_HERD_SIZE)
            {
                continue;
            }

            List<EntityData> candidates = new ArrayList<EntityData>();
            SpatialCell centerCell = SpatialCell.from(herd.getDimension(),
                    herd.getEntityType(), herd.getCenterX(), herd.getCenterY(), herd.getCenterZ());

            // JOIN_RADIUS is 24 blocks; with 16-block cells, checking two
            // cells in each direction covers every possible candidate.
            for (int dx = -2; dx <= 2; dx++)
            {
                for (int dy = -2; dy <= 2; dy++)
                {
                    for (int dz = -2; dz <= 2; dz++)
                    {
                        List<EntityData> bucket = spatialIndex.get(centerCell.offset(dx, dy, dz));
                        if (bucket == null) continue;

                        for (EntityData data : bucket)
                        {
                            if (!assigned.contains(data.getId())
                                    && distanceSq(data.getX(), data.getY(), data.getZ(),
                                    herd.getCenterX(), herd.getCenterY(), herd.getCenterZ())
                                    <= JOIN_RADIUS * JOIN_RADIUS)
                            {
                                candidates.add(data);
                            }
                        }
                    }
                }
            }

            Collections.sort(candidates, new Comparator<EntityData>()
            {
                @Override
                public int compare(EntityData first, EntityData second)
                {
                    double a = distanceSq(first.getX(), first.getY(), first.getZ(),
                            herd.getCenterX(), herd.getCenterY(), herd.getCenterZ());
                    double b = distanceSq(second.getX(), second.getY(), second.getZ(),
                            herd.getCenterX(), herd.getCenterY(), herd.getCenterZ());
                    return Double.compare(a, b);
                }
            });

            for (EntityData candidate : candidates)
            {
                if (herd.getMembers().size() >= MAX_HERD_SIZE)
                {
                    break;
                }
                herd.addMember(candidate.getId());
                assigned.add(candidate.getId());
                updateCenter(herd, entities, worldTime);
            }
        }

        // Form new herds from the remaining nearby animals of the same species.
        List<EntityData> unassigned = new ArrayList<EntityData>();
        for (EntityData data : entities.values())
        {
            if (!assigned.contains(data.getId()) && isHerdAnimal(data, dimension))
            {
                unassigned.add(data);
            }
        }

        Collections.sort(unassigned, new Comparator<EntityData>()
        {
            @Override
            public int compare(EntityData first, EntityData second)
            {
                return first.getEntityType().compareTo(second.getEntityType());
            }
        });

        Set<UUID> consumed = new HashSet<UUID>();
        for (EntityData seed : unassigned)
        {
            if (consumed.contains(seed.getId()))
            {
                continue;
            }

            List<EntityData> cluster = new ArrayList<EntityData>();
            cluster.add(seed);
            consumed.add(seed.getId());

            boolean expanded = true;
            while (expanded && cluster.size() < MAX_HERD_SIZE)
            {
                expanded = false;
                double centerX = averageX(cluster);
                double centerY = averageY(cluster);
                double centerZ = averageZ(cluster);

                EntityData nearest = null;
                double nearestDistance = HERD_RADIUS * HERD_RADIUS;
                SpatialCell centerCell = SpatialCell.from(dimension,
                        seed.getEntityType(), centerX, centerY, centerZ);
                for (int dx = -1; dx <= 1; dx++)
                {
                    for (int dy = -1; dy <= 1; dy++)
                    {
                        for (int dz = -1; dz <= 1; dz++)
                        {
                            List<EntityData> bucket = spatialIndex.get(centerCell.offset(dx, dy, dz));
                            if (bucket == null) continue;

                            for (EntityData candidate : bucket)
                            {
                                if (assigned.contains(candidate.getId())
                                        || consumed.contains(candidate.getId()))
                                {
                                    continue;
                                }

                                double distance = distanceSq(candidate.getX(), candidate.getY(),
                                        candidate.getZ(), centerX, centerY, centerZ);
                                if (distance <= nearestDistance)
                                {
                                    nearestDistance = distance;
                                    nearest = candidate;
                                }
                            }
                        }
                    }
                }

                if (nearest != null)
                {
                    cluster.add(nearest);
                    consumed.add(nearest.getId());
                    expanded = true;
                }
            }

            if (cluster.size() >= MIN_HERD_SIZE)
            {
                AnimalHerd herd = new AnimalHerd(UUID.randomUUID(), seed.getEntityType(), dimension);
                for (EntityData member : cluster)
                {
                    herd.addMember(member.getId());
                    assigned.add(member.getId());
                }
                updateCenter(herd, entities, worldTime);
                herds.put(herd.getId(), herd);
            }
        }
    }

    public static void refreshCenters(Map<UUID, EntityData> entities,
                                      Map<UUID, AnimalHerd> herds,
                                      long worldTime)
    {
        for (AnimalHerd herd : herds.values())
        {
            updateCenter(herd, entities, worldTime);
        }
    }

    private static final class SpatialCell
    {
        private final int dimension;
        private final String entityType;
        private final int x;
        private final int y;
        private final int z;

        private SpatialCell(int dimension, String entityType, int x, int y, int z)
        {
            this.dimension = dimension;
            this.entityType = entityType;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private static SpatialCell from(EntityData data)
        {
            return from(data.getDimension(), data.getEntityType(),
                    data.getX(), data.getY(), data.getZ());
        }

        private static SpatialCell from(int dimension, String entityType,
                                        double x, double y, double z)
        {
            return new SpatialCell(dimension, entityType, cell(x), cell(y), cell(z));
        }

        private SpatialCell offset(int dx, int dy, int dz)
        {
            return new SpatialCell(dimension, entityType, x + dx, y + dy, z + dz);
        }

        private static int cell(double coordinate)
        {
            return (int) Math.floor(coordinate / 16.0D);
        }

        @Override
        public boolean equals(Object object)
        {
            if (this == object) return true;
            if (!(object instanceof SpatialCell)) return false;
            SpatialCell other = (SpatialCell) object;
            return dimension == other.dimension && x == other.x && y == other.y
                    && z == other.z && entityType.equals(other.entityType);
        }

        @Override
        public int hashCode()
        {
            int result = dimension;
            result = 31 * result + entityType.hashCode();
            result = 31 * result + x;
            result = 31 * result + y;
            result = 31 * result + z;
            return result;
        }
    }

    private static boolean isHerdAnimal(EntityData data, int dimension)
    {
        if (data == null || !data.isAnimal() || data.isVirtual() || data.getHealth() <= 0.0F
                || data.getDimension() != dimension)
        {
            return false;
        }

        return data.getDiet() == AnimalDiet.HERBIVORE
                || data.getDiet() == AnimalDiet.OMNIVORE;
    }

    private static void updateCenter(AnimalHerd herd, Map<UUID, EntityData> entities, long worldTime)
    {
        double x = 0.0D;
        double y = 0.0D;
        double z = 0.0D;
        int count = 0;

        for (UUID memberId : herd.getMembers())
        {
            EntityData data = entities.get(memberId);
            if (data != null)
            {
                x += data.getX();
                y += data.getY();
                z += data.getZ();
                count++;
            }
        }

        if (count > 0)
        {
            herd.updateCenter(x / count, y / count, z / count, worldTime);
        }
    }

    private static double averageX(Collection<EntityData> entities)
    {
        double total = 0.0D;
        for (EntityData data : entities) total += data.getX();
        return total / entities.size();
    }

    private static double averageY(Collection<EntityData> entities)
    {
        double total = 0.0D;
        for (EntityData data : entities) total += data.getY();
        return total / entities.size();
    }

    private static double averageZ(Collection<EntityData> entities)
    {
        double total = 0.0D;
        for (EntityData data : entities) total += data.getZ();
        return total / entities.size();
    }

    private static double distanceSq(double x1, double y1, double z1,
                                      double x2, double y2, double z2)
    {
        double dx = x1 - x2;
        double dy = y1 - y2;
        double dz = z1 - z2;
        return dx * dx + dy * dy + dz * dz;
    }
}
