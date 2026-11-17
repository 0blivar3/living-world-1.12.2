# Living World

**Living World** is an A-Life simulation mod for **Minecraft 1.12.2**. Its goal is to make the world feel more alive by giving creatures and villagers routines and interactions that continue beyond the player's immediate attention.

## Requirements

- Minecraft **1.12.2**
- Minecraft Forge **14.23.5.2859**
- Java **8**

## Features in 1.0.0

### Animal behavior
- Hunger and food-seeking behavior.
- Natural foraging: suitable animals can consume nearby edible blocks.
- Herbivores, omnivores, and carnivores have different behavior profiles.
- Predators can hunt selected prey, including sheep, rabbits, and chickens.
- Adult animals can find mates and reproduce when their needs and cooldowns allow.
- Animals can form small herds; herd members can react to nearby threats.
- Herbivores and omnivores follow a simple daily rest routine.

### World simulation
- The mod tracks entities and stores their simulated state per dimension.
- Creatures in unloaded areas can continue selected parts of their simulation without forcing chunks to load.
- Offline animal aging, hunger, starvation, and breeding are simulated from saved data.
- Offspring created by offline simulation are stored as virtual entities and materialized when their chunk is naturally loaded again.
- UUID checks help prevent duplicate offspring when virtual entities are materialized.

### Villagers
- Loaded villagers can react to nearby hostile mobs.
- Villager behavior can use nearby homes and profession-appropriate workstations.
- Basic activity and movement are simulated when villager data is offline.

## Installation

1. Install Minecraft Forge **1.12.2-14.23.5.2859**.
2. Download the `LivingWorld-1.0.0.jar` file from the GitHub release.
3. Place the JAR in your Minecraft `mods` folder.
4. Launch Minecraft with the Forge 1.12.2 profile.

## Development

This repository contains the Forge mod source code. To build it with Java 8:

```bash
./gradlew clean build
```

The built mod JAR is generated in `build/libs/`.

## Compatibility and saves

Living World 1.0.0 is an initial release. Back up important worlds before testing new versions. Because the mod stores simulation data in world saves, keep a backup when updating or downgrading.

## Links

- [Source code](https://github.com/0blivar3/living-world-1.12.2)
- [Releases](https://github.com/0blivar3/living-world-1.12.2/releases)

## Author

**0blivar3**
