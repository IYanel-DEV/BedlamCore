# BedlamCore implementation prompt

Build **BedlamCore**, an original Bed Wars minigame inspired by the familiar four-team network format. Do not copy proprietary code, maps, text, branding, or assets.

## Compatibility

- Produce one Java 8 bytecode jar that compiles against the Spigot 1.8.8 API and loads on Spigot/Paper 1.8.8 through Paper 26.2.
- Use only long-lived Bukkit APIs in shared code. Do not use NMS or CraftBukkit internals.
- Put renamed materials and sounds behind one small compatibility class.
- Omit `api-version` intentionally so current Paper loads the jar as a legacy plugin.
- Verify both endpoints with separate local servers; do not infer compatibility from compilation alone.

## First playable release

1. Operators receive a setup item and configure the arena entirely through inventory GUIs.
2. The setup flow records lobby/spectator locations, team spawns, beds, forges, item shops, upgrade shops, and diamond/emerald generators.
3. Validation clearly lists missing setup instead of starting a broken match.
4. Players can browse/join/leave from GUIs, with commands retained only as fallback and automation controls.
5. Implement balanced teams, countdown, resource generation, bed destruction, respawn while the bed survives, final death, winner detection, and automatic reset.
6. Implement a practical item shop and team upgrades with iron, gold, diamonds, and emeralds.
7. Protect the arena: only match-placed blocks and enemy beds may be broken, and placed blocks are removed during reset.
8. Keep arena configuration in `arenas.yml`; keep transient match state in memory.

## Engineering bar

- Prefer small concrete classes over speculative frameworks.
- Give state transitions one owner and keep event listeners thin.
- Validate all player-controlled inventory clicks and setup actions.
- Leave one runnable dependency-free check for game rules.
- Use descriptive names and ordinary comments only where intent is not obvious.
- Commit with the configured human Git identity. Do not add automated attribution, extra authorship trailers, or hidden metadata.

## Acceptance

- `./gradlew clean check build` succeeds.
- The produced jar enables without errors on local 1.8.8 and 26.2 servers.
- An operator can complete setup without a command.
- Two players can join, start, buy blocks, break an enemy bed, receive a final death, and trigger reset.
- The repository includes reproducible setup/start scripts for both compatibility servers.
