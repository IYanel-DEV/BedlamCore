# BedlamCore implementation prompt

Build **BedlamCore**, an original Bed Wars minigame inspired by the familiar four-team network format. Do not copy proprietary code, maps, text, branding, or assets.

## Compatibility

- Produce one Java 8 bytecode jar that compiles against the Spigot 1.8.8 API and loads on Spigot/Paper 1.8.8 through Paper 26.2.
- Use only long-lived Bukkit APIs in shared code. Do not use NMS or CraftBukkit internals.
- Put renamed materials and sounds behind one small compatibility class.
- Omit `api-version` intentionally so current Paper loads the jar as a legacy plugin.
- Verify both endpoints with separate local servers; do not infer compatibility from compilation alone.

## Network-ready v0.2

1. Keep **Lobby Setup** and **Game Setup** as separate inventory-GUI workflows.
2. Lobby Setup records the network spawn and places one Solo and one Doubles queue NPC. Selecting an NPC placer gives the operator a marked armor-stand item; placing it creates the configured NPC. Shift-left-click cycles its supported entity type. NPCs cannot take damage, target players, move, or make sounds where the server API supports silence.
3. Game Setup manages multiple dedicated worlds. Operators can create Solo or Doubles worlds, see the current world, teleport to an arena, edit it, or delete it after confirmation.
4. Teleporting to a game world opens its setup automatically. New and incomplete arenas print every missing field in chat. Existing arenas remain editable.
5. Setup is transactional: all changes are held in a per-operator draft. Apply validates and persists; Cancel discards edits and deletes a newly-created draft world.
6. The setup flow records spectator location, team spawns, beds, forges, item shops, upgrade shops, and diamond/emerald generators.
7. Players can quick-join or choose a waiting arena from Solo/Doubles NPC GUIs, with commands retained only as fallback and automation controls.
8. Solo uses one player per team and Doubles uses two. Admin force-start accepts one player for testing.
9. Lobby players, separate arenas, chat, and tab visibility are isolated by configurable world channels. Team chat uses configurable colored prefixes and a suffix after the player name.
10. Show distinct sidebar scoreboards in the lobby, waiting room, and running game.
11. Implement balanced teams, countdown, resource generation, bed destruction, respawn while the bed survives, final death, winner detection, and automatic reset.
12. Implement a practical item shop and team upgrades with iron, gold, diamonds, and emeralds.
13. Protect the arena: only match-placed blocks and enemy beds may be broken, and placed blocks are removed during reset.
14. Keep lobby and arena configuration in `arenas.yml`; keep transient match and setup-draft state in memory.

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
- An operator can complete lobby and multi-world game setup without a command.
- Two players can join, start, buy blocks, break an enemy bed, receive a final death, and trigger reset.
- The repository includes reproducible setup/start scripts for both compatibility servers.
