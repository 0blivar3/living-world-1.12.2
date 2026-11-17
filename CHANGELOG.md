# Changelog

All notable changes to Living World are documented here.

## [1.0.0] - Initial release

### Added
- Core A-Life manager and per-dimension entity tracking.
- Persistent entity data, including hunger, health, age, diet, and simulation timestamps.
- Animal diet profiles: herbivores, omnivores, and carnivores.
- Animal food-seeking and natural foraging behavior.
- Hunting behavior for wolves and ocelots.
- Animal breeding with hunger costs and breeding cooldowns.
- Herd creation, membership management, and threat response.
- Daily resting routine for herbivores and omnivores.
- Villager routines, including movement toward homes and workstations and fleeing from hostile mobs.
- Offline simulation for entities in unloaded areas without force-loading chunks.
- Offline animal aging, hunger, starvation, and breeding.
- Virtual offspring records that are materialized when their chunks are naturally loaded.
- UUID-based duplicate protection for virtual offspring materialization.
- Support for registered custom animal entity types in offline breeding, with sterile mules excluded.

### Technical
- Minecraft 1.12.2 support.
- Forge 14.23.5.2859 target.
- Java 8 compatibility.
