# BedlamCore implementation prompt

Build **BedlamCore**, an original Bed Wars minigame inspired by the familiar four-team network format. Do not copy proprietary code, maps, text, branding, or assets.

## Compatibility

- Produce one Java 8 bytecode jar that compiles against the Spigot 1.8.8 API and loads on Spigot/Paper 1.8.8 through Paper 26.2.
- Use only long-lived Bukkit APIs in shared code. Do not use NMS or CraftBukkit internals.
- Put renamed materials and sounds behind one small compatibility class.
- Omit `api-version` intentionally so current Paper loads the jar as a legacy plugin.
- Verify both endpoints with separate local servers; do not infer compatibility from compilation alone.

## Network-ready gameplay (current)

1. Keep **Lobby Setup** and **Game Setup** as separate inventory-GUI workflows.
2. Lobby Setup records the network spawn and places one Solo and one Doubles queue NPC. Selecting an NPC placer gives the operator a marked armor-stand item; placing it creates the configured NPC without discarding the active setup draft. Shift-left-click opens a deterministic editor for mob type, adult/baby state, look-at-player behavior (OFF by default), or a human player appearance and skin. Use Citizens as an optional soft dependency for real fake-player NPCs and as the controller for immobile mob NPCs; retain a built-in fallback. NPCs cannot take damage, target players, move, or rotate toward players while look-at-player is disabled. Queue NPCs show compact Solo/Doubles hologram lines; hide NPC/hologram labels for viewers farther than 20 blocks (`hideEntity` when available, shared name-visibility fallback on 1.8).
3. Game Setup manages multiple dedicated void worlds. New worlds contain one spawn block only. Operators can create Solo or Doubles worlds, see the current world, teleport to an arena, edit it, or delete it after confirmation.
4. Teleporting to a game world opens its setup automatically. New and incomplete arenas print every missing field in chat. Existing arenas remain editable.
5. Setup is transactional: all changes are held in a per-operator draft. Apply validates every required field, saves the world once, disables auto-save again, persists configuration, and returns the operator to the lobby; Cancel discards edits and deletes a newly-created draft world.
6. The setup flow records a waiting-structure spawn, spectator location, team spawns, beds, forges, item shops, upgrade shops, and diamond/emerald generators. `/bc spawnbuild` gives an operator a two-point golden-axe selector that saves a reusable waiting-building cuboid. Require exactly one diamond block inside it as the relative player-spawn anchor. Paste the saved structure during waiting/countdown and restore the original blocks when play starts.
7. Players can quick-join or choose a waiting arena from Solo/Doubles NPC GUIs, with commands retained only as fallback and automation controls.
8. Solo uses one player per team and Doubles uses two. Admin force-start accepts one player for testing.
9. Lobby players, separate arenas, chat, and tab visibility are isolated by configurable world channels. Team chat uses configurable colored prefixes and a suffix after the player name.
10. Hypixel-like sidebars: lobby / waiting / in-game. In-game lines show match timer, per-team bed status (+/X) with alive count, next diamond/emerald upgrade countdown, mode, and map id.
11. Give operators one contextual setup compass instead of separate menu/setup navigation items.
12. Implement balanced teams, countdown, resource generation, bed destruction without sleep messages or bed-item drops, respawn while the bed survives, final death, winner detection, and automatic reset. `/leave` returns the player to the lobby and immediately resolves an empty arena or awards the remaining team. Match death must skip the vanilla respawn screen (`Player.spigot().respawn()` next tick).
13. Item shop + team upgrades with frozen villager NPCs. Upgrades include sharpened swords, reinforced armor, forge speed, maniac miner (haste), and heal pool. Rotating diamond/emerald block markers sit centered ~3 blocks above generators with tier/name holograms; hide those displays past 20 blocks. Timed Diamond/Emerald tier upgrades announce and speed production; sidebar shows next upgrade.
14. Build protection (place and break):
    - generators: 3-block radius
    - team spawn: 4 blocks
    - forge: 3 blocks
    - item/upgrade shops: 2 blocks
    - outside arena bounds (padded AABB of setup points)
    - above waiting-spawn Y
    - spectators cannot build
    Only match-placed blocks and enemy beds may be broken otherwise. On reset, remove placed blocks and restore beds.
15. Arena worlds: `setAutoSave(false)` after load/play; setup Apply uses `saveOnce`; plugin disable unloads arena worlds **without** saving so player builds never persist across crash/restart.
16. Starter kit: wooden sword + full armor. Leather helmet/chest/legs/boots are team-colored. Armor shop upgrades only helmet + chestplate (iron then diamond); legs and boots stay team leather. Sword shop purchases replace the existing sword in-slot (no duplicate swords).
17. Faster void/deep-fall kills: void damage is fatal immediately; falling at/below waiting-spawn Y − 30 forces death instead of slow drain.
18. Keep lobby and arena configuration in `arenas.yml`; keep transient match and setup-draft state in memory. No free wool at start — blocks come from the shop.

## Engineering bar

- Prefer small concrete classes over speculative frameworks.
- Give state transitions one owner and keep event listeners thin.
- Validate all player-controlled inventory clicks and setup actions.
- Leave one runnable dependency-free check for game rules (`GameRulesCheck`).
- Use descriptive names and ordinary comments only where intent is not obvious.
- Commit only as the human author **Iyanel-dev**. Never add Co-authored-by Cursor, Cursor/AI attribution, or extra authorship trailers.

## Acceptance

- `./gradlew clean check build` succeeds.
- The produced jar enables without errors on local 1.8.8 and 26.2 servers (`servers/setup.ps1`).
- An operator can complete lobby and multi-world game setup without a command.
- Two players can join, start, buy blocks/swords/armor, break an enemy bed, receive a final death without a respawn UI, and trigger reset with builds cleared.
- Generator markers and lobby holograms hide beyond 20 blocks.
- Both test servers run with online authentication enabled.
