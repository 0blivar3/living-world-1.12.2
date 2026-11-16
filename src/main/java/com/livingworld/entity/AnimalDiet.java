package com.livingworld.entity;

/**
 * Broad dietary categories used by the A-Life animal simulation.
 *
 * This is intentionally a small classification layer. Actual food searching,
 * feeding and predator-prey interactions will be implemented separately.
 */
public enum AnimalDiet
{
    HERBIVORE,
    OMNIVORE,
    CARNIVORE,
    UNKNOWN;

    public static AnimalDiet fromEntityType(String entityType)
    {
        if (entityType == null || entityType.isEmpty())
        {
            return UNKNOWN;
        }

        int separator = entityType.lastIndexOf(':');
        String path = separator >= 0
                ? entityType.substring(separator + 1).toLowerCase()
                : entityType.toLowerCase();

        switch (path)
        {
            // Animals that primarily feed on plants.
            case "cow":
            case "sheep":
            case "horse":
            case "donkey":
            case "mule":
            case "rabbit":
                return HERBIVORE;

            // Animals with a varied diet.
            case "pig":
            case "chicken":
                return OMNIVORE;

            // Predators and animals that primarily eat other animals.
            case "wolf":
            case "ocelot":
            case "polar_bear":
                return CARNIVORE;

            default:
                return UNKNOWN;
        }
    }
}
