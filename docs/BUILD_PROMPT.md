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
9. Lobby players, separate arenas, chat, and tab visibility are isolated by configurable world channels. Team chat uses configurable colored prefixes and a suffix after the player name. **In-match tab list names use that player's team color** (scoreboard teams + `setPlayerListName`).
10. Hypixel-like sidebars: lobby / waiting / in-game. In-game lines show match timer, per-team bed status (+/X) with alive count, next diamond/emerald upgrade countdown, mode, and map id.
11. Give operators one contextual setup compass instead of separate menu/setup navigation items.
12. Implement balanced teams, countdown, resource generation, bed destruction without sleep messages or bed-item drops, respawn while the bed survives, final death, winner detection, and automatic reset. `/leave` returns the player to the lobby and immediately resolves an empty arena or awards the remaining team. Match death must skip the vanilla respawn screen (`Player.spigot().respawn()` next tick).
13. **Shop / upgrade NPCs:** frozen villagers with **no vanilla nametag**. Two centered hologram lines just above the head (~`SHOP_HOLO_TITLE_Y` / `SHOP_HOLO_SUB_Y`): title (`ITEM SHOP` / `TEAM UPGRADES`) and `Right Click`. Hide holograms past 20 blocks. Shopkeepers must **never** despawn (`setRemoveWhenFarAway(false)` / freeze) and must be excluded from mob-clear / spawn-cancel rules.
14. **Item Shop GUI** uses Hypixel-style category tabs (Quick Buy / Blocks / Melee / Armor / Tools / Ranged / Potions / Utility) with sensible offers. **Team Upgrades GUI** uses a clearer team-upgrade layout (sharpened swords, reinforced armor, forge, maniac miner, heal pool).
15. **Heal Pool:** while active, green particles (`Effect.HAPPY_VILLAGER`) ring the team base; players in radius regenerate.
16. Build protection (place and break):
    - generators: 3-block radius
    - team spawn: 4 blocks
    - forge: 3 blocks
    - item/upgrade shops: 2 blocks
    - outside arena bounds (padded AABB of setup points)
    - above waiting-spawn Y
    - spectators cannot build
    Only match-placed blocks and enemy beds may be broken otherwise. On reset, remove placed blocks and restore beds.
17. Arena worlds: `setAutoSave(false)` + `setSpawnFlags(false, false)` after load/play; cancel non-`CUSTOM` `CreatureSpawnEvent` in arena worlds; clear wild mobs on match start; setup Apply uses `saveOnce`; plugin disable unloads arena worlds **without** saving so player builds never persist across crash/restart.
18. Starter kit: unbreakable wooden sword + full unbreakable armor. Leather helmet/chest/legs/boots are team-colored. Permanent chainmail upgrades boots+legs; iron/diamond shop upgrades helmet+chestplate. **Armor cannot leave armor slots** during a match. **Swords/armor never take durability.** Buying a stronger sword replaces a weaker one; buying another equal/extra sword is allowed so teammates can be gifted. **Cannot drop (or death-drop) the last sword** — only when the player owns 2+ swords.
19. **Water bucket** from the shop is one-use: placing water must not leave an empty bucket.
20. Faster void/deep-fall kills: void damage is fatal immediately; falling at/below waiting-spawn Y − 30 forces death instead of slow drain.
21. **Death / spectator flow:**
    - Bed alive: skip respawn UI; respawn at **team spawn** (not death location / not spectator point while still fighting). During the configured respawn delay, hold the player in spectator at the island with damage cancelled, then `spawnPlayer`.
    - Bed gone / final kill: **Spectator** mode; give a **Bed** item (`Return to Lobby`) and a **Compass** that opens a **Spectate** GUI of alive players’ heads; click teleports/spectates that player.
22. Keep lobby and arena configuration in `arenas.yml`; keep transient match and setup-draft state in memory. No free wool at start — blocks come from the shop.

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
- Two players can join, start, buy from category shops, purchase upgrades (heal pool shows green base particles), break an enemy bed, receive a final death into spectator with bed+compass tools (no respawn UI / no survival death-loop), and trigger reset with builds cleared.
- Shop holograms sit close above villagers with a Right Click line; no vanilla villager nametag; hide past 20 blocks.
- Arena worlds spawn no sheep/hostile mobs; shopkeepers remain.
- Match armor is locked in slots; swords/armor show no durability bar wear; last sword cannot be dropped.
- Tab list shows team-colored names in-match.
- Water bucket consumes fully on place.
- Generator markers and lobby holograms hide beyond 20 blocks.
- Both test servers run with online authentication enabled.
