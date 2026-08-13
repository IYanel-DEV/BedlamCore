# BedlamCore status report (~v0.10.1)

**Date:** 2026-08-14  
**Repo:** `C:\Users\FUFU\Downloads\Bedwarsplugin`  
**Authorship:** N/A (report only; no commit)

---

## Full report

### What it is

BedlamCore is an original, GUI-first Bed Wars minigame for Spigot/Paper: lobby + multi-world arena setup, Solo/Doubles queues, Hypixel-adjacent match loop (gens, shops, upgrades/traps, beds, soft spectate), and a flat YAML stats economy — one jar, no NMS, no copied proprietary assets.

### Compatibility

- **Target:** Spigot/Paper **1.8.8 → 26.2**, single jar.
- **Compile:** Spigot API `1.8.8-R0.1-SNAPSHOT`; toolchain Java 21; `options.release = 8` (Java 8 bytecode).
- **Load:** no `api-version` in `plugin.yml` (legacy plugin on modern Paper).
- **Soft dep:** Citizens (real player NPCs); built-in armor-stand / mob fallback.
- **Verify:** `servers/setup.ps1` → `servers/legacy-1.8.8` (port 25565, Java 8) and `servers/current-26.2` (port 25566, Java 25); `online-mode=true`.

### Current version

| Source | Version |
|--------|---------|
| `build.gradle.kts` | **0.10.1** |
| Jar name | `BedlamCore-0.10.1.jar` |
| Last committed bump in log | **v0.9.0** (`8bb5393` — Bridge Eggs, stats, lobby scoreboard) |
| `README.md` | Stale (**0.4.0**) — docs lag code |

Working tree carries post-0.9.0 work (invis armor, traps polish, forge share, NPC mute, pristine/setup fixes, etc.) under the **0.10.1** gradle version.

### Major systems working now

- **Lobby / Game Setup GUIs** — transactional drafts, Apply/`saveOnce`, Cancel; contextual setup compass.
- **Queue NPCs** — Solo/Doubles; Citizens soft-dep or fallback; silent NPCs; look-at optional; holograms + 20-block hide.
- **Multi-world arenas** — Solo (1/team) / Doubles (2/team); force-start for testing.
- **Waiting structure** — `/bc spawnbuild` cuboid (no glass corners); paste in waiting; restore on play.
- **World isolation** — lobby/arena chat + tab channels; team-colored in-match tab names.
- **Scoreboards** — lobby (level/progress/tokens/kills/wins), waiting, in-game (next gen event + team bed lines).
- **Stats economy** — `stats.yml` tokens/XP/level/kills/wins/beds/games; awards on win/bed/kill/final/play; end-of-match summary.
- **Item Shop** — Quick Buy categories (Blocks/Melee/Armor/Tools/Ranged/Potions/Utility); tool tiers; potions (Speed/Jump/Invis).
- **Upgrades & Traps** — Sharpened, Reinforced, Maniac Miner, Iron Forge, Heal Pool, Dragon Buff (max HP), Cushioned Boots; 3-slot trap queue (Blindness / Counter-Offensive / Alarm / Miner Fatigue / Reveal).
- **Heal Pool** — base regen + green particles.
- **Team + ender chests** — punch-to-deposit holograms; shared team inv; personal ender cleared per match.
- **Forge share** — ~2.5 horizontal teammate copies; standing vs share sounds; L2/L3 rare diamond/emerald; ground fallback Y.
- **Diamond/emerald gens** — timed tier upgrades; sidebar next-event line; floating pin holograms.
- **Bridge Egg** — 3-wide team wool trail, path/tick/distance caps, end dip, `placeDenyReason` skip-continue.
- **Soft spectate** — adventure + flight + invis (not `GameMode.SPECTATOR`); respawn delay at island; final death bed+compass spectate GUI.
- **Invis potions** — armor equipment packets emptied for others (`InvisArmor`); restore on expire/milk/death/reveal/hit.
- **Combat / deaths** — Hypixel-layout kill + bed-break messages; kill loot / forge return; instant void / deep-fall kills.
- **Build protection** — gens/spawn/forge/shops/chests/bounds/height; match-placed + enemy beds only.
- **World reset / pristine** — `setAutoSave(false)`; pristine snapshot under `plugins/BedlamCore/pristine/<world>`; unload without save; strip waiting paste before Apply bake.
- **Empty-team win** — `teamContending`: living > 0 OR (bed + occupied this match); never-occupied fillers do not stall; no win-check at match start.
- **Kit rules** — team leather, armor locked in slots, unbreakable swords/armor, last-sword no-drop, water bucket one-use.
- **Sounds** — compat helper (bed/kill/death/shop/countdown/gen/forge).
- **Runnable check** — `GameRulesCheck` via Gradle `coreCheck`.

### Test servers / how to run

```powershell
.\gradlew.bat clean check build
.\servers\setup.ps1          # once; -SkipCitizens for fallback NPCs
# then:
.\servers\legacy-1.8.8\start.bat    # :25565, Java 8
.\servers\current-26.2\start.bat    # :25566, Java 25
```

Op: setup compass → Lobby Setup + Game World Setup. Players: queue NPCs / Play Bed Wars GUI. Fallback: `/bedlam`, `/leave`.

### Known limitations / gaps

- Docs drift: README still cites **0.4.0**; last git message **v0.9.0** while gradle is **0.10.1**.
- **Quick Buy Settings** is a "Coming soon" stub (no per-player layout).
- **Tokens have no lobby sink** (earn only; nothing cosmetic/rank to buy).
- Modes are **Solo + Doubles only** (no 3s/4s/squads).
- **Fireball** is stock Bukkit projectile (`yield 2`, non-incendiary) — no custom KB curve.
- **Dragon Buff** = +4 max HP only (no endgame dragon event).
- **`ArenaManager` (~1.6k LOC) + `GuiController` (~1.2k)** own almost all match/GUI logic — hard to change safely.
- Sidebar rebuilds a **new scoreboard every second** for every online player.
- `StatsStore.apply` **saves YAML on every grant** (fine for small servers; will stutter under load).
- Citizens mute needs a **per-tick silent re-apply** (remount clears flags).
- 1.8 still needs careful GUI/open deferral and bed-place interact quirks; modern path uses reflection/`hideEntity` where available.
- No parties, ranked, map vote beyond simple selector, replay, or mobile defenders (golem/tower).

---

## 10 things to IMPROVE (existing, rough)

1. **`ArenaManager` god class (~1611 lines)** — Match lifecycle, gens, forge, traps, spectate, bridge egg, chests, displays, and reset live in one type. One wrong edit risks half the game; peel gens/forge and soft-spectate into small collaborators when the next feature forces a touch.

2. **Sidebar recreates the full scoreboard every 20 ticks** — Flicker risk and needless allocation on busy lobbies. Diff-update lines / reuse one board per player instead of `getNewScoreboard()` each pass.

3. **`StatsStore` writes disk on every token/XP grant** — Kill spam = YAML spam. Batch to a periodic flush (or dirty-flag + shutdown save you already have).

4. **Fireball is vanilla yield-2** — Feels inconsistent across 1.8 vs modern Paper (KB, block damage, self-boost). Cancel explosion damage you do not want and apply a fixed horizontal impulse + small vertical; keep yield low or zero.

5. **Hologram / display visibility is O(displays × players) + magic Y constants** — Works, but gen/shop/lobby heights (`SHOP_HOLO_*`, `GEN_*`) will keep drifting per map scale. Centralize one "label stack" helper and optionally throttle visibility to every N ticks.

6. **Quick Buy Settings stub** — Dead compass slot trains players to click nothing. Either wire a simple 9-slot favorite bar persisted in `stats.yml`, or remove the item until real.

7. **Citizens / NPC silence is a tick patch** — Remount clears `silent`; you re-mute every tick and scan entities for sound cancel. Prefer metadata + event cancel only; document Citizens version pin in `servers/README` so operators do not fight ambient noise.

8. **1.8 shop GUI open path is still fragile** — History of IndexOutOfBounds / desync and `pendingOpen` / deferred open. One shared `openChestGui(player, inv)` with title length clamp + next-tick open on 1.8 only would cut repeat bugs.

9. **Trap trigger is flat radius around team spawn** — No bed-centric zone, vertical nuance, or "already in base" grace. Players trip traps camping height or miss edge rushes; define a base AABB from bed+spawn and optional re-entry flag.

10. **Forge L2/L3 rare ores are silent RNG** — No chat/sound when a diamond/emerald bonus hits, so upgrades feel placebo. Play `Sounds.forgeCollect` + a one-line team message on bonus so Iron Forge spends feel earned.

---

## 10 things to ADD (new, solo-dev practical)

1. **Fireball knockback polish** — Highest ROI combat feel: custom boost when hitting players/self, predictable arc, optional no-terrain grief near gens. Bridge Egg already exists; this is the matching Utility skill ceiling.

2. **Pop-up tower (utility)** — Placeable wool/ladder column with a short build animation and `placeDenyReason` reuse. Classic Bed Wars macro without new AI.

3. **Iron golem (or silverfish) defender** — Shop spawn at base, team-tagged, dies on bed break / match end. One mob type + target filter; do not invent a pet framework.

4. **Party system (lobby)** — Invite/kick/ready; queue NPCs join as a unit into Doubles first. Unblocks real social play without ranked complexity.

5. **Cosmetics shop that spends tokens** — Kill-message styles, lobby particle trails, victory titles. Tokens currently have no sink; this makes `stats.yml` matter between matches.

6. **Map voting UI polish** — After countdown threshold, 3-map vote chest with live tallies on the waiting sidebar. You already have Map Selector; promote it into the wait loop.

7. **Per-player Quick Buy layouts** — Persist 9 favorite offer ids on the player record; ship the stubbed Settings compass. Small YAML, big daily UX.

8. **Triples / 3v3v3v3 mode** — Copy Doubles path with `playersPerTeam = 3` and lobby NPC #3. Same arenas if maps have 4 islands; almost pure config + GUI clone.

9. **Lite "last death" replay** — On final kill, soft-spec teleports along 3–5s of recorded positions (or killer POV). No full demo format — just a short spectator camera.

10. **Seasonal ranked stub** — Elo or simple MMR in `stats.yml`, separate Solo ranked queue, end-of-match ±points. Skip leagues/divisions until parties + enough maps exist.

---

## Version history skim (git)

```
8bb5393 feat: 3-wide Bridge Eggs, stats economy, lobby scoreboard (v0.9.0)
32c7229 fix: place on beds, trail Bridge Eggs, restore waiting cuboid, 1.8 shop GUI (v0.8.2)
fc2384b fix: 1.8 GUI desync, Bridge Egg, bed-adjacent builds (v0.8.1)
bbd7ca4 fix: defer 1.8 shop GUI open to avoid IndexOutOfBounds (v0.8.1)
409ec34 fix: null-guard purgeStrayArmorStands during onEnable (v0.7.6)
03bbca4 fix: save arena world before Apply unload (v0.7.1)
725c82f feat: Hypixel-like lobby, shop, chests, and scoreboard (v0.7.0)
7deeace feat: playtest polish for shops, combat, and spectator (v0.6.0)
7b239b1 feat: Hypixel-style match polish for v0.5
488e1c3 feat: improve setup worlds and NPCs
372ea92 feat: add multi-arena GUI setup and lobby NPCs
4cb83c0 feat: build BedlamCore bed wars foundation
```

Uncommitted / in-progress relative to v0.9.0 includes invis armor, trap/forge/chest/NPC sound work, and gradle **0.10.1**.

---

## Package map (quick)

| Package | Role |
|---------|------|
| `arena` | Arena state, manager, settings, waiting paste/template |
| `game` | Listeners, rules, sidebar, stats, network view, sounds for chests/NPCs/pearls |
| `lobby` | Lobby settings + queue NPC service |
| `gui` | All setup / play / shop / upgrade inventories |
| `compat` | Items, Sounds, Enchantments, Skins, EntityVisibility, InvisArmor |
| `command` | `/bedlam`, `/leave` |
| `world` | Game world create/load/pristine (`GameWorlds`) |

---

*End of report. No commit, no push.*
