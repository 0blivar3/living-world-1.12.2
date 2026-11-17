package com.livingworld.entity;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Items;
import net.minecraft.item.ItemSeeds;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Allows hungry chickens and pigs to search for suitable dropped food.
 *
 * The task only consumes real item entities in loaded areas. It never creates
 * items or loads chunks, and the normal Minecraft item entity is consumed.
 */
public final class AnimalForagingBehavior
{
    private static final double SEARCH_RADIUS = 8.0D;
    private static final double EAT_DISTANCE_SQ = 2.25D;
    private static final int HUNGER_THRESHOLD = 75;
    private static final int FOOD_VALUE = 30;
    private static final Set<EntityAnimal> INSTALLED =
            Collections.newSetFromMap(new WeakHashMap<EntityAnimal, Boolean>());

    private AnimalForagingBehavior()
    {
    }

    public static void update(EntityAnimal animal, EntityData data)
    {
        if (animal == null || data == null || animal.world == null
                || animal.world.isRemote || !animal.isEntityAlive())
        {
            return;
        }

        if (animal instanceof net.minecraft.entity.passive.EntityChicken
                || animal instanceof net.minecraft.entity.passive.EntityPig)
        {
            // AI tasks are runtime objects, so reinstall them after an entity reload.
            if (INSTALLED.add(animal))
            {
                animal.tasks.addTask(4, new ForageTask(animal, data));
            }
        }
    }

    private static boolean isSuitableFood(EntityAnimal animal, ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return false;
        }

        if (animal instanceof net.minecraft.entity.passive.EntityChicken)
        {
            return stack.getItem() instanceof ItemSeeds;
        }

        if (animal instanceof net.minecraft.entity.passive.EntityPig)
        {
            return stack.getItem() == Items.CARROT
                    || stack.getItem() == Items.POTATO
                    || stack.getItem() == Items.BEETROOT;
        }

        return false;
    }

    private static final class ForageTask extends EntityAIBase
    {
        private final EntityAnimal animal;
        private final EntityData data;
        private EntityItem target;
        private int searchCooldown;

        private ForageTask(EntityAnimal animal, EntityData data)
        {
            this.animal = animal;
            this.data = data;
            this.searchCooldown = Math.floorMod(animal.getUniqueID().hashCode(), 60);
            setMutexBits(1);
        }

        @Override
        public boolean shouldExecute()
        {
            if (!canForage())
            {
                searchCooldown = 0;
                return false;
            }

            if (searchCooldown > 0)
            {
                searchCooldown--;
                return false;
            }

            searchCooldown = 60;
            target = findFood();
            return target != null;
        }

        @Override
        public boolean shouldContinueExecuting()
        {
            return canForage() && isValidTarget(target);
        }

        @Override
        public void startExecuting()
        {
            if (target != null)
            {
                animal.getNavigator().tryMoveToEntityLiving(target, 1.0D);
            }
        }

        @Override
        public void updateTask()
        {
            if (!isValidTarget(target))
            {
                animal.getNavigator().clearPath();
                return;
            }

            if (animal.getDistanceSq(target) <= EAT_DISTANCE_SQ)
            {
                ItemStack stack = target.getItem();
                if (isSuitableFood(animal, stack))
                {
                    stack.shrink(1);
                    if (stack.isEmpty())
                    {
                        target.setDead();
                    }
                    data.feed(FOOD_VALUE);
                }

                animal.getNavigator().clearPath();
                target = null;
                return;
            }

            if (animal.getNavigator().noPath())
            {
                animal.getNavigator().tryMoveToEntityLiving(target, 1.0D);
            }
        }

        @Override
        public void resetTask()
        {
            animal.getNavigator().clearPath();
            target = null;
        }

        private boolean canForage()
        {
            return animal.isEntityAlive()
                    && data.getHunger() < HUNGER_THRESHOLD
                    && !animal.isInLove()
                    && !animal.isBurning()
                    && animal.getAttackTarget() == null;
        }

        private boolean isValidTarget(EntityItem item)
        {
            return item != null && !item.isDead
                    && isSuitableFood(animal, item.getItem());
        }

        private EntityItem findFood()
        {
            World world = animal.world;
            List<EntityItem> items = world.getEntitiesWithinAABB(
                    EntityItem.class, animal.getEntityBoundingBox().grow(SEARCH_RADIUS));

            EntityItem nearest = null;
            double nearestDistance = SEARCH_RADIUS * SEARCH_RADIUS;

            for (EntityItem item : items)
            {
                if (!isValidTarget(item))
                {
                    continue;
                }

                double distance = animal.getDistanceSq(item);
                if (distance < nearestDistance)
                {
                    nearestDistance = distance;
                    nearest = item;
                }
            }

            return nearest;
        }
    }
}
