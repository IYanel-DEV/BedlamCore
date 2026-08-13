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
2. Lobby Setup records the network spawn and places one Solo and one Doubles queue NPC. Selecting an NPC placer gives the operator a marked armor-stand item; placing it creates the configured NPC without discarding the active setup draft. Shift-left-click opens a deterministic editor for mob type, adult/baby state, look-at-player behavior (OFF by default), or a human player appearance and skin. Use Citizens as an optional soft dependency for real fake-player NPCs and as the controller for immobile mob NPCs; retain a built-in fallback. NPCs cannot take damage, target players, move, or rotate toward players while look-at-player is disabled. Queue NPC holograms use shop-like head offsets (`LOBBY_HOLO_*` ≈2.25/1.95/1.65); hide NPC/hologram labels for viewers farther than 20 blocks (`hideEntity` when available, shared name-visibility fallback on 1.8). **All plugin NPCs are silent** (`setSilent` + cancel `EntitySoundEvent` when present; damage cancelled so no hurt sounds).
3. Game Setup manages multiple dedicated void worlds. New worlds contain one spawn block only. Operators can create Solo or Doubles worlds, see the current world, teleport to an arena, edit it, or delete it after confirmation.
4. Teleporting to a game world opens its setup automatically. New and incomplete arenas print every missing field in chat. Existing arenas remain editable.
5. Setup is transactional: all changes are held in a per-operator draft. Apply validates every required field, strips the waiting paste, **saveOnce while the world is still loaded** (never unload/remove first), then register/replace the arena (locations reattached after reload), persists configuration, and returns the operator to the lobby; Cancel discards edits and deletes a newly-created draft world.
6. The setup flow records a waiting-structure spawn, spectator location, team spawns, beds, forges, item shops, upgrade shops, **team chests**, **ender chests**, and diamond/emerald generators. `/bc spawnbuild` gives an operator a two-point golden-axe selector that saves a reusable waiting-building cuboid. **Glass / stained glass / panes are not valid selection points** (click a real map block). Require exactly one diamond block inside it as the relative player-spawn anchor. Paste the saved structure during waiting/countdown and restore the original blocks when play starts (strip leftover selector glass at restore / PLAY start).
7. Players can quick-join or choose a waiting arena from Solo/Doubles NPC GUIs (**Play Bed Wars** layout: red bed quick play + sign map selector with green title / white lore / yellow CTA), with commands retained only as fallback and automation controls.
8. Solo uses one player per team and Doubles uses two. Admin force-start accepts one player for testing.
9. Lobby players, separate arenas, chat, and tab visibility are isolated by configurable world channels. Team chat uses configurable colored prefixes and a suffix after the player name. **In-match tab list names use that player's team color** (scoreboard teams + `setPlayerListName`).
10. Hypixel-like sidebars: **lobby** (date + instance tag, Level `N*`, Progress `3.4k/5k` + aqua/gray bar, Tokens, Total Kills/Wins, `play.bedlam` footer), waiting, in-game. In-game lines show date + map id, next generator event as `Diamond X in M:SS` (time green), team lines `R Red: ✓` (or alive count when bed gone) with YOU marker, and `play.bedlam` footer. Persist per-UUID stats in `stats.yml` (tokens, xp, level, kills, wins, beds, games). Award tokens/XP on win (50/100), bed (25/50), kill (5/10), final kill (10/25), play (10/25); compact chat on bed/kill/win plus end-of-match summary. Lobby inventory reset does not wipe stats.
11. Give operators one contextual setup compass instead of separate menu/setup navigation items.
12. Implement balanced teams, countdown, resource generation, bed destruction without sleep messages or bed-item drops, respawn while the bed survives, final death, winner detection, and automatic reset. `/leave` and Return-to-Lobby **clear match inventory** then give lobby items only. Match death must skip the vanilla respawn screen (`Player.spigot().respawn()` next tick).
13. **Shop / upgrade NPCs:** frozen villagers with **no vanilla nametag**. Two centered hologram lines just above the head (`SHOP_HOLO_TITLE_Y`≈2.25 / `SHOP_HOLO_SUB_Y`≈1.95): title (`ITEM SHOP` / `TEAM UPGRADES`) and `Right Click`. Chest deposit line ≈1.1 above block (just above lid). Gen floating block pin ≈2.5 (`GEN_STAND_Y`, **full-size** armor stand); labels ≈3.15/2.85 just above the pin. Hologram armor stands use Marker/Invisible/no-arms (nametag only). Hide holograms past 20 blocks. Shopkeepers must **never** despawn (`setRemoveWhenFarAway(false)` / freeze) and must be excluded from mob-clear / spawn-cancel rules; mute ambient/hurt via silent + sound event cancel.
14. **Item Shop GUI** uses Hypixel-style Quick Buy categories (Nether Star / Blocks / Melee / Armor / Tools / Ranged / Potions / Utility) with lime/gray selection bar, fuller offers (wool/clay/glass/endstone/ladder/wood/obsidian/ice; swords+KB stick; armor path; shears + upgradable pick/axe; bow/arrows/punch; potions; apple/snowball/fireball/TNT/pearl/water/milk/sponge), Cost lore + cannot-afford red line. **Tools:** Wood→Stone→Iron→Diamond pick (Efficiency) and axe; shop shows next tier only; death drops 1 tier (min wooden once owned); respawn restores current tier; unbreakable replace-in-inventory. **Upgrades & Traps GUI:** left team upgrades (Sharpened / Reinforced / Maniac Miner / Iron Forge / Heal Pool / Dragon Buff / Cushioned Boots), right traps (Blindness / Counter-Offensive / Alarm / Miner Fatigue / Reveal) with escalating diamond costs into a 3-slot queue; gray glass separator; trap-queue wool slots; traps fire on base entry with brief cooldown.
15. **Heal Pool:** while active, green particles (`Effect.HAPPY_VILLAGER`) ring the team base; players in radius regenerate.
16. Build protection (place and break):
    - generators: 3-block radius
    - team spawn: 4 blocks
    - forge: 3 blocks
    - item/upgrade shops / team & ender chests: 2 blocks
    - outside arena bounds (padded AABB of setup points)
    - above waiting-spawn Y
    - spectators cannot build
    Only match-placed blocks and enemy beds may be broken otherwise. On reset, remove placed blocks and restore beds.
17. Arena worlds: `setAutoSave(false)` + `setSpawnFlags(false, false)` after load/play; cancel non-`CUSTOM` `CreatureSpawnEvent` in arena worlds; clear wild mobs on match start; setup Apply uses `saveOnce` and snapshots a pristine copy under `plugins/BedlamCore/pristine/<world>`; load restores that snapshot before createWorld so crash/prior saves never leave builds or AIR beds; plugin disable unloads arena worlds **without** saving (`unloadWorld(..., false)`).
18. Starter kit: unbreakable wooden sword + full unbreakable armor. Leather helmet/chest/legs/boots are team-colored. Permanent chainmail upgrades boots+legs; iron/diamond shop upgrades helmet+chestplate. **Armor cannot leave armor slots** during a match. **Swords/armor never take durability.** Buying a stronger sword replaces a weaker one; buying another equal/extra sword is allowed so teammates can be gifted. **Cannot drop (or death-drop) the last sword** — only when the player owns 2+ swords.
19. **Water bucket** from the shop is one-use: placing water must not leave an empty bucket.
20. Faster void/deep-fall kills: void damage is fatal immediately; falling at/below waiting-spawn Y − 30 forces death instead of slow drain.
21. **Death / spectator flow:**
    - Bed alive: skip respawn UI; respawn at **team spawn** (not death location / not spectator point while still fighting). During the configured respawn delay, soft-spectate at the island (adventure + flight + invis + hidePlayer; damage cancelled), then `spawnPlayer`.
    - Bed gone / final kill: **soft spectate** (not `GameMode.SPECTATOR`) at the arena **spectator location** (rebinding world + next-tick re-teleport); give a **Bed** item (`Return to Lobby`) and a **Compass** that opens a **Spectate** GUI of alive players’ heads; click teleports while staying soft-spectate.
    - Own bed: cancel break with `You cannot break your own bed!`. Enemy beds break even in solo/force-start (do not cancel left-click on beds in interact).
22. Keep lobby and arena configuration in `arenas.yml`; keep transient match and setup-draft state in memory. No free wool at start — blocks come from the shop.
23. **Match start message:** title/subtitle plus green dashed chat box explaining protect bed / destroy beds / collect resources (original Bedlam wording).
24. **Team chest + ender chest:** setup per team; at match start place both with **PUNCH TO DEPOSIT** holograms (hide >20 blocks). Team chest inventory is shared among teammates; ender chest is personal (`Player#getEnderChest`) and **cleared** on match join, match start, and lobby return (never persists across matches). Left-click/punch deposits held stack (exclude sword/armor/tools) with `Deposited xN …` chat. Enemies cannot open a team's normal chest while that team's bed is alive.
25. **Forge share:** teammates within ~2.5 horizontal blocks of their forge each receive a copy directly to inventory (enemies excluded). Standing at forge = item pickup sound; farther in range = quieter orb share sound. Ground fallback uses a lower Y (`forgeDropPoint`). Forge L2/L3 ticks can rarely grant diamond/emerald (L3 > L2).
26. **Sounds (compat helper):** bed break = Ender Dragon growl; kill = orb; death = wither hurt; shop/upgrade buy = orb; cannot afford = villager no; waiting countdown ticks = note pling; match start = level-up; diamond/emerald tier upgrade = note/chime; forge collect/share via `Sounds.forgeCollect` / `Sounds.forgeShare`.

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
- An operator can complete lobby and multi-world game setup without a command (including team/ender chests).
- Two players can join via Play Bed Wars GUI, start, see start message + Hypixel-like scoreboard, buy from category shops (incl. pick/axe tier upgrades that persist/downgrade on death), purchase team upgrades + queued traps (heal pool particles; blindness/alarm/fatigue/reveal/counter-offensive fire), deposit into chests, break an enemy bed (dragon growl), receive kill/death sounds, final death into spectator with bed+compass tools, and return to lobby with **cleared match inventory**.
- Shop holograms sit just above villagers (`SHOP_HOLO_*`); chest/gen/lobby offsets as in §13/§2; marker armor stands; no vanilla villager nametag; hide past 20 blocks.
- Plugin NPCs (shop, lobby queue, Citizens/fallback) never play ambient/hurt/death sounds.
- Buy success/fail, countdown tick/start, and generator tier-upgrade sounds fire via `Sounds` helper.
- Final death / respawn wait use soft spectate (adventure flight + invis), not `GameMode.SPECTATOR`.
- Arena worlds spawn no sheep/hostile mobs; shopkeepers remain.
- Match armor is locked in slots; swords/armor show no durability bar wear; last sword cannot be dropped.
- Tab list shows team-colored names in-match.
- Water bucket consumes fully on place.
- Bridge Egg: 3-wide team wool under the projectile, ~20 path / 40 ticks / 20 blocks, `placeDenyReason` (skip illegal cells, continue), consume on throw.
- `/bc spawnbuild` rejects glass corners; leftover selector glass is aired on waiting restore / PLAY start.
- Lobby scoreboard shows persisted level/progress/tokens/kills/wins; `stats.yml` survives restart.
- Generator markers and lobby holograms hide beyond 20 blocks.
- Both test servers run with online authentication enabled.
