# BedlamCore status report (0.10.5 working tree)

**Date:** 2026-08-14  
**Repo:** `C:\Users\FUFU\Downloads\Bedwarsplugin`  
**Authorship:** N/A (report only; no commit)

---

## Full report

### What it is

BedlamCore is an original, GUI-first Bed Wars minigame for Spigot/Paper: lobby + multi-world arena setup, Solo/Doubles queues, Hypixel-adjacent match loop (gens, shops, upgrades/traps, beds, soft spectate), and a flat YAML stats economy. Modern servers use Bukkit APIs; 1.8 entity visibility/equipment/silence uses an isolated reflective CraftBukkit/NMS fallback with no compile-time dependency.

### Compatibility

- **Target:** Spigot/Paper **1.8.8 → 26.2**, single jar.
- **Compile:** Spigot API `1.8.8-R0.1-SNAPSHOT`; toolchain Java 21; `options.release = 8` (Java 8 bytecode).
- **Load:** no `api-version` in `plugin.yml` (legacy plugin on modern Paper).
- **Soft dep:** Citizens (real player NPCs); built-in armor-stand / mob fallback.
- **Verify:** `servers/setup.ps1` → `servers/legacy-1.8.8` (port 25565, Java 8) and `servers/current-26.2` (port 25566, Java 25); `online-mode=true`.

### Current version

| Source | Version |
|--------|---------|
| `build.gradle.kts` | **0.10.5** |
| Local jar name | `BedlamCore-0.10.5.jar` |
| Public release at review time | **v0.10.1** |
| Release procedure | `docs/RELEASING.md` |

The working tree carries the unreleased **0.10.5** changes. Do not publish until the exact jar passes both endpoint checks.

### Major systems working now

- **Lobby / Game Setup GUIs** — transactional drafts, Apply/`saveOnce`, Cancel; contextual setup compass.
- **Queue NPCs** — Solo/Doubles; Citizens soft-dep or fallback; silent NPCs; look-at optional; holograms + 20-block hide.
- **Multi-world arenas** — Solo (1/team) / Doubles (2/team); force-start for testing.
- **Waiting structure** — `/bc spawnbuild` cuboid (no glass corners); paste in waiting; restore on play.
- **World isolation** — lobby/arena chat + tab channels; team-colored in-match tab names.
- **Scoreboards** — lobby (level/progress/tokens/kills/wins), waiting, in-game (next gen event + team bed lines).
- **Stats economy** — `stats.yml` tokens/XP/level/kills/wins/beds/games; awards on win/bed/kill/final/play; end-of-match summary.
- **Item Shop** — Quick Buy categories (Blocks/Melee/Armor/Tools/Ranged/Potions/Utility); tool tiers; potions (Speed/Jump/Invis).
- **Upgrades & Traps** — 45-slot Hypixel-style layout with Sharpened, Reinforced, Maniac Miner, Iron Forge, Heal Pool, Cushioned Boots I-II; 3-slot 1/2/4-diamond trap queue (Blindness / Counter-Offensive / Miner Fatigue / Reveal).
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

- **Tokens have no lobby sink** (earn only; nothing cosmetic/rank to buy).
- Modes are **Solo + Doubles only** (no 3s/4s/squads).

- 1.8 still needs careful GUI/open deferral and bed-place interact quirks; modern path uses reflection/`hideEntity` where available.
- No parties, ranked, map vote beyond simple selector, replay, or mobile defenders (golem/tower).
- Public GitHub release remains **v0.10.1** until the tested 0.10.5 working tree is committed/tagged by the human maintainer.
- Endpoint startup passed for 0.10.5; full two-player gameplay and clean console shutdown remain manual acceptance checks.

---

## 0.10.5 improvement completion

1. **Release discipline** — one version source, dynamic README paths, and `docs/RELEASING.md` exact-jar gate.

2. **Two-endpoint proof** — the exact 0.10.5 jar enabled on Paper 1.8.8 and Paper 26.2 with explicit capability logs.

3. **Crash-safe persistence** — YAML and pristine world replacement use temporary files/directories plus replace/rollback.

4. **Magic Milk** — named shop milk now grants 30 seconds of trap immunity.

5. **Permanent Chainmail** — owned legs/boots are match state and are rebuilt on every respawn.

6. **Single shop catalog** — fixed layout, keys, prices, currency, and lore drive categories, Quick Buy, and purchases.

7. **Combat/reward edge cases** — void credit expires after 15 seconds and full-inventory loot drops safely.

8. **Entity hot paths** — generators and NPC pins retain direct references; tagged NPC silence no longer scans all worlds.

9. **Compatibility truth** — reflective 1.8 fallbacks are documented and the active path is logged at startup.

10. **Runnable checks** — rules now cover immunity, combat timeout, catalog consistency, and atomic persistence replacement.

---

## 10 things to ADD (new, solo-dev practical)

1. **Reconnect grace** — Reserve a disconnected player's match slot briefly and expose `/rejoin` so a network hiccup does not remove the team bed.

2. **Pop-up tower (utility)** — Placeable wool/ladder column with a short build animation and `placeDenyReason` reuse. Classic Bed Wars macro without new AI.

3. **Iron golem (or silverfish) defender** — Shop spawn at base, team-tagged, dies on bed break / match end. One mob type + target filter; do not invent a pet framework.

4. **Party system (lobby)** — Invite/kick/ready; queue NPCs join as a unit into Doubles first. Unblocks real social play without ranked complexity.

5. **Cosmetics shop that spends tokens** — Kill-message styles, lobby particle trails, victory titles. Tokens currently have no sink; this makes `stats.yml` matter between matches.

6. **Map voting UI polish** — After countdown threshold, 3-map vote chest with live tallies on the waiting sidebar. You already have Map Selector; promote it into the wait loop.

7. **Leaderboard GUI** — Show wins, kills, final kills, beds, and streaks from the existing stats store.

8. **Triples / 3v3v3v3 mode** — Copy Doubles path with `playersPerTeam = 3` and lobby NPC #3. Same arenas if maps have 4 islands; almost pure config + GUI clone.

9. **Lite "last death" replay** — On final kill, soft-spec teleports along 3–5s of recorded positions (or killer POV). No full demo format — just a short spectator camera.

10. **Play Again flow** — Results item queues the same mode or returns to the lobby selector without command typing.

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

Uncommitted / in-progress work is versioned **0.10.5** and must not be published until the exact commit completes the release checklist.

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


## 0.10.2
- ArenaManager split into collaborators (~739 LOC coordinator + 8 services).
- SidebarService reuses scoreboard per player / diffs lines.


## 0.10.3
- Parallel improves #3–#5: StatsStore dirty + 5s flush; fireball yield 0 + fixed KB; hologram labelY helper + visibility every 5 ticks.
