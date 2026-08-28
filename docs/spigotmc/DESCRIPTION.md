[CENTER][COLOR=#C0392B][B]⚠ BETA — report any errors[/B][/COLOR]
This plugin is in active [B]beta[/B]. Every release is tested on Spigot/Paper 1.8.8 through 26.2, but you may still hit rough edges. Found a bug or got an error? Please report it on [URL='https://github.com/IYanel-DEV/BedlamCore/issues']GitHub Issues[/URL] so it gets fixed fast.[/CENTER]

[CENTER]
[IMG]https://raw.githubusercontent.com/IYanel-DEV/BedlamCore/main/docs/spigotmc/images/banner-bedlamcore.jpg[/IMG]

[SIZE=6][B][COLOR=#9B59B6]BedlamCore[/COLOR][/B][/SIZE]
[SIZE=4][I]Bed Wars that feels finished[/I][/SIZE]

[B]GUI-first setup · Solo & Doubles · One jar · Spigot 1.8.8 → Paper 26.2[/B]

Author: [URL='https://github.com/IYanel-DEV']IYanel-DEV[/URL] · Current: [B]v0.10.91[/B]
[URL='https://github.com/IYanel-DEV/BedlamCore']GitHub[/URL] · [URL='https://github.com/IYanel-DEV/BedlamCore/releases']Download jar[/URL]

[IMG]https://raw.githubusercontent.com/IYanel-DEV/BedlamCore/main/docs/spigotmc/images/logo-bedlamcore-spigot.png[/IMG]
[/CENTER]

[CENTER][COLOR=#CCCCCC]━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━[/COLOR]
[SIZE=5][B]Purple bed. Real matches. Compass setup.[/B][/SIZE]
[COLOR=#CCCCCC]━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━[/COLOR][/CENTER]

[B]BedlamCore[/B] is a GUI-first Bed Wars engine for Spigot and Paper.

Lobby and arenas are configured with a [B]hotbar compass[/B] — drafts, Apply, Cancel — not a command maze. Players queue on [B]Solo / Doubles NPCs[/B], earn [B]tokens / XP / levels[/B], buy [B]cosmetics[/B], and play the full loop: waiting structure → forge & gens → Quick Buy → upgrades & traps → beds → [B]soft spectate[/B] → victory → Play Again.

Bundled map template: [B]bedwars-e2560[/B]. Import your own folder worlds too.

[B]One jar.[/B] Spigot [B]1.8.8[/B] through Paper [B]26.2[/B].

Independent project. Not affiliated with Hypixel, Mojang, or Microsoft.

[CENTER]
[IMG]https://raw.githubusercontent.com/IYanel-DEV/BedlamCore/main/docs/spigotmc/images/banner-bedlamcore-features.jpg[/IMG]
[/CENTER]

[CENTER][COLOR=#CCCCCC]━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━[/COLOR][/CENTER]
[SIZE=5][B][COLOR=#9B59B6]◆ What's new[/COLOR][/B][/SIZE]

[SIZE=4][B]v0.10.91[/B][/SIZE]
[LIST]
[*][B]SQLite / MySQL storage[/B] — keep stats in a database instead of [ICODE]stats.yml[/ICODE] (drivers bundled); share one MySQL across servers; migrate with [ICODE]/bc storage migrate[/ICODE]. YAML stays the default and byte-identical
[*][B]PlaceholderAPI[/B] — [ICODE]%bedlamcore_*%[/ICODE] placeholders for tab, scoreboards & holograms (tokens, level, KDR/FKDR, per-mode stats, prestige, live player counts). Optional soft dependency
[*][B]Prestige in lobby chat[/B] — your equipped Prestige colours your chat name, not just the tab list ([ICODE]chat.prestige-in-lobby[/ICODE])
[*][B]Fix[/B] — Party / Party Invite menus no longer let you pull the buttons out of the GUI
[/LIST]

[SIZE=4][B]Since v0.10.37[/B][/SIZE]
[LIST]
[*][B]3v3v3v3 Trios & 4v4v4v4 Quads[/B] — new game modes: queue NPCs, quick join / browse, import & templates, per-mode stats and leaderboard tabs
[*][B]Party system[/B] — invite friends with [ICODE]/party[/ICODE], queue together onto the same team (works via the built-in provider, or bridge BungeeParties / Party and Friends)
[*][B]Leaderboards[/B] — Hypixel-style floating lobby board + [ICODE]/leaderboard[/ICODE] GUI / chat rankings with per-mode tabs
[*][B]Reconnect grace[/B] — keep your bed and team slot reserved for 60s after a disconnect; rejoin with [ICODE]/rejoin[/ICODE]
[*][B]Bundled "Chained" map[/B] — a 4-island template, playable only as Trios (4×3) or Quads (4×4)
[*][B]Refreshed waiting spawn building[/B] — new default build, now pixel-identical on 1.8 and 1.13+ (stair/slab facing + fence connections fixed)
[*][B]Citizens-free NPCs[/B] — queue NPCs use the built-in system; Citizens optional
[*][B]New cosmetics[/B] — Wood Skins, Final Kill Effects, Prestige, Projectile Trails, Bed Destroys; rideable win Dragon & Wither
[*]Stability fixes for Paper 1.21+/26.2, cosmetics shop pagination, and the whole match loop
[/LIST]

[CENTER][COLOR=#CCCCCC]━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━[/COLOR][/CENTER]
[SIZE=5][B][COLOR=#9B59B6]◆ Features[/COLOR][/B][/SIZE]

[SIZE=4][B]Lobby[/B][/SIZE]
[LIST]
[*]Solo & Doubles [B]queue NPCs[/B] (Citizens, or built-in armor-stand / mob fallback)
[*][B]Profile NPC[/B] + statistics GUI
[*][B]Cosmetics NPC[/B] — token shop (kill-message packs and more)
[*]Lobby [B]scoreboard[/B] — level / tokens / kills / wins
[*]Configurable footer ([ICODE]play.bedlam[/ICODE])
[/LIST]

[SIZE=4][B]Setup[/B][/SIZE]
[LIST]
[*]Compass-driven [B]Lobby Setup[/B] and [B]Game World Setup[/B]
[*]Transactional drafts — [B]Apply[/B] saves, [B]Cancel[/B] discards
[*]Waiting structure via [ICODE]/bc spawnbuild[/ICODE] (two-click golden axe)
[*]Spectator spawn, teams, beds, forges, shops, chests, diamond/emerald gens
[*][B]Build border[/B] from waiting + spectator midpoint (setup outline; match builds stay inside)
[/LIST]

[SIZE=4][B]Arenas & templates[/B][/SIZE]
[LIST]
[*]Multi-world [B]Solo[/B] and [B]Doubles[/B]
[*]Bundled template [B]bedwars-e2560[/B]
[*][B]Import Maps[/B] for folder worlds
[*]Pristine world snapshots between matches · crash-safe YAML / world replace
[/LIST]

[SIZE=4][B]Match[/B][/SIZE]
[LIST]
[*]Item Shop [B]Quick Buy[/B] (Blocks / Melee / Armor / Tools / Ranged / Potions / Utility)
[*][B]Upgrades & Traps[/B] GUI
[*]Team forge share · diamond & emerald [B]tier[/B] gens
[*]Bridge Egg · Dream Defender
[*]Punch-to-deposit [B]team chests[/B]
[*][B]Soft spectate[/B] — adventure + flight + invis (not SPECTATOR gamemode)
[*]Match rewards · win effects / dragon · Play Again
[/LIST]

[SIZE=4][B]Economy & data[/B][/SIZE]
[LIST]
[*]Tokens / XP / levels — stored in [ICODE]stats.yml[/ICODE], [B]SQLite[/B] or [B]MySQL[/B] ([ICODE]storage.backend[/ICODE]; drivers bundled)
[*][B]PlaceholderAPI[/B] [ICODE]%bedlamcore_*%[/ICODE] expansion (optional)
[*]Admin: [ICODE]/bc token add[/ICODE] · [ICODE]/bc xp add[/ICODE] · [ICODE]/bc storage migrate[/ICODE]
[/LIST]

[CENTER][COLOR=#CCCCCC]━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━[/COLOR][/CENTER]
[SIZE=5][B][COLOR=#9B59B6]◆ Screenshots[/COLOR][/B][/SIZE]

[CENTER]
[IMG]https://raw.githubusercontent.com/IYanel-DEV/BedlamCore/main/docs/spigotmc/images/lobby.png[/IMG]
[IMG]https://raw.githubusercontent.com/IYanel-DEV/BedlamCore/main/docs/spigotmc/images/queue-npcs.png[/IMG]

[IMG]https://raw.githubusercontent.com/IYanel-DEV/BedlamCore/main/docs/spigotmc/images/cosmetics.png[/IMG]
[IMG]https://raw.githubusercontent.com/IYanel-DEV/BedlamCore/main/docs/spigotmc/images/profile.png[/IMG]

[IMG]https://raw.githubusercontent.com/IYanel-DEV/BedlamCore/main/docs/spigotmc/images/setup.png[/IMG]
[IMG]https://raw.githubusercontent.com/IYanel-DEV/BedlamCore/main/docs/spigotmc/images/game-worlds.png[/IMG]

[IMG]https://raw.githubusercontent.com/IYanel-DEV/BedlamCore/main/docs/spigotmc/images/templates.png[/IMG]
[IMG]https://raw.githubusercontent.com/IYanel-DEV/BedlamCore/main/docs/spigotmc/images/waiting.png[/IMG]

[IMG]https://raw.githubusercontent.com/IYanel-DEV/BedlamCore/main/docs/spigotmc/images/team-island.png[/IMG]
[IMG]https://raw.githubusercontent.com/IYanel-DEV/BedlamCore/main/docs/spigotmc/images/forge.png[/IMG]

[IMG]https://raw.githubusercontent.com/IYanel-DEV/BedlamCore/main/docs/spigotmc/images/diamond-gen.png[/IMG]
[IMG]https://raw.githubusercontent.com/IYanel-DEV/BedlamCore/main/docs/spigotmc/images/emerald-gen.png[/IMG]

[IMG]https://raw.githubusercontent.com/IYanel-DEV/BedlamCore/main/docs/spigotmc/images/quick-buy.png[/IMG]
[IMG]https://raw.githubusercontent.com/IYanel-DEV/BedlamCore/main/docs/spigotmc/images/upgrades.png[/IMG]

[IMG]https://raw.githubusercontent.com/IYanel-DEV/BedlamCore/main/docs/spigotmc/images/punch-deposit.png[/IMG]
[IMG]https://raw.githubusercontent.com/IYanel-DEV/BedlamCore/main/docs/spigotmc/images/victory.png[/IMG]
[/CENTER]

[SPOILER="More stills (optional)"]
[CENTER]
[IMG]https://raw.githubusercontent.com/IYanel-DEV/BedlamCore/main/docs/spigotmc/images/cosmetics-shop.png[/IMG]
[IMG]https://raw.githubusercontent.com/IYanel-DEV/BedlamCore/main/docs/spigotmc/images/stats.png[/IMG]
[IMG]https://raw.githubusercontent.com/IYanel-DEV/BedlamCore/main/docs/spigotmc/images/play-menu.png[/IMG]
[IMG]https://raw.githubusercontent.com/IYanel-DEV/BedlamCore/main/docs/spigotmc/images/shops.png[/IMG]
[IMG]https://raw.githubusercontent.com/IYanel-DEV/BedlamCore/main/docs/spigotmc/images/logo-bedlamcore-spigot.png[/IMG]
[/CENTER]
[/SPOILER]

[CENTER][COLOR=#CCCCCC]━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━[/COLOR][/CENTER]
[SIZE=5][B][COLOR=#9B59B6]◆ Installation[/COLOR][/B][/SIZE]
[LIST=1]
[*]Download [B]BedlamCore-0.10.91.jar[/B] from [URL='https://github.com/IYanel-DEV/BedlamCore/releases']GitHub Releases[/URL]
[*]Drop into [ICODE]plugins/[/ICODE] and restart
[*]Join as op — hotbar [B]Bedlam Setup[/B] compass opens Lobby Setup (lobby) or that world’s arena draft (game world)
[*]Finish Lobby Setup (spawn + NPCs), then create or import game worlds — [B]Apply[/B] saves, [B]Cancel[/B] discards
[*]Players join via queue NPCs (or [ICODE]/bedlam[/ICODE] fallbacks). Ops: [ICODE]/bedlam forcestart[/ICODE] in a waiting arena for solo testing
[/LIST]

[CENTER][COLOR=#CCCCCC]━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━[/COLOR][/CENTER]
[SIZE=5][B][COLOR=#9B59B6]◆ Quick setup[/COLOR][/B][/SIZE]

[SPOILER="Lobby setup"]
[LIST=1]
[*]Op in lobby → open the [B]compass[/B]
[*][B]Lobby Setup[/B] → set network spawn
[*]Place [B]Solo[/B] and [B]Doubles[/B] queue NPCs (placer → place; shift-click edits mob/skin/look-at)
[*]Optional: Profile + Cosmetics NPCs
[*][B]Apply[/B]
[/LIST]
[/SPOILER]

[SPOILER="Game world / templates / border"]
[LIST=1]
[*]Compass → [B]Game World Setup[/B] → create Solo/Doubles void world, or [B]Templates[/B] / [B]Import Map[/B]
[*]Set waiting spawn, spectator, teams, beds, forges, shops, chests, gens
[*][ICODE]/bc spawnbuild[/ICODE] — waiting cuboid (glass ≠ valid corner; diamond block = relative spawn anchor)
[*]Set [B]build border[/B] radius once waiting + spectator exist (default [B]64[/B])
[*][B]Apply[/B] — waiting paste stripped, pristine snapshot taken, back to lobby
[/LIST]
Bundled showcase map: [B]bedwars-e2560[/B].
[/SPOILER]

[SPOILER="Match flow"]
Queue NPC → waiting + countdown → team spawn → forge / shop / upgrades → beds → soft spectate on final death → victory → Play Again / lobby.

Inventory cleared on leave. Ender chests do not persist across matches.
[/SPOILER]

[CENTER][COLOR=#CCCCCC]━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━[/COLOR][/CENTER]
[SIZE=5][B][COLOR=#9B59B6]◆ Commands[/COLOR][/B][/SIZE]
[LIST]
[*][ICODE]/bedlam[/ICODE] ([ICODE]/bc[/ICODE]) — [ICODE]menu[/ICODE] · [ICODE]solo[/ICODE] · [ICODE]doubles[/ICODE] · [ICODE]leave[/ICODE] · [ICODE]spawnbuild[/ICODE] · [ICODE]forcestart[/ICODE] · [ICODE]reload[/ICODE] · [ICODE]token[/ICODE] · [ICODE]xp[/ICODE]
[*][ICODE]/leave[/ICODE] — leave match / return to lobby
[*][ICODE]/bc token add <player> <amount>[/ICODE] — admin tokens
[*][ICODE]/bc xp add <player> <amount>[/ICODE] — admin XP
[/LIST]
Primary UX: setup compass + lobby NPCs. Commands are fallbacks and admin tools.
Console may use [ICODE]reload[/ICODE], [ICODE]token[/ICODE], and [ICODE]xp[/ICODE].

[SPOILER="Permissions"]
[LIST]
[*][ICODE]bedlam.admin[/ICODE] — default [B]op[/B] — configure arenas, force-start, setup (includes token/xp)
[*][ICODE]bedlam.token.add[/ICODE] — default [B]op[/B] — [ICODE]/bc token add[/ICODE]
[*][ICODE]bedlam.xp.add[/ICODE] — default [B]op[/B] — [ICODE]/bc xp add[/ICODE]
[*][ICODE]bedlam.play[/ICODE] — default [B]true[/B] — join games
[*][ICODE]bedlam.lobby.build[/ICODE] — default [B]false[/B] — break/place in lobby world
[/LIST]
[/SPOILER]

[CENTER][COLOR=#CCCCCC]━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━[/COLOR][/CENTER]
[SIZE=5][B][COLOR=#9B59B6]◆ Requirements[/COLOR][/B][/SIZE]
[LIST]
[*][B]Java 8+[/B] runtime (Java 8 bytecode)
[*][B]Spigot / Paper 1.8.8 → 26.2[/B] — one jar
[*]Optional: [B]Citizens[/B] for real fake-player queue NPCs
[*]Optional: [B]PlaceholderAPI[/B] for [ICODE]%bedlamcore_*%[/ICODE] placeholders; [B]MySQL[/B] server if you use the MySQL backend
[/LIST]

[CENTER][COLOR=#CCCCCC]━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━[/COLOR][/CENTER]
[SIZE=5][B][COLOR=#9B59B6]◆ License[/COLOR][/B][/SIZE]
[B]Proprietary — All Rights Reserved[/B] © 2026 IYanel-DEV

[LIST]
[*][B]Use freely unmodified[/B] — download, install, and run the official jar on your servers without asking
[*]Public mirrors of the [B]unmodified[/B] official jar OK with attribution + link to the repo
[*][B]Modification[/B], derivative works, redistributing modified builds, and rebranding need [B]prior written permission[/B]
[/LIST]
Full text: [URL='https://github.com/IYanel-DEV/BedlamCore/blob/main/LICENSE']LICENSE on GitHub[/URL]

[CENTER][COLOR=#CCCCCC]━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━[/COLOR]

[SIZE=4][B]Download[/B][/SIZE]
[URL='https://github.com/IYanel-DEV/BedlamCore/releases']github.com/IYanel-DEV/BedlamCore/releases[/URL]

[SIZE=4][B]Source & issues[/B][/SIZE]
[URL='https://github.com/IYanel-DEV/BedlamCore']github.com/IYanel-DEV/BedlamCore[/URL]

[IMG]https://raw.githubusercontent.com/IYanel-DEV/BedlamCore/main/docs/spigotmc/images/logo-bedlamcore-spigot.png[/IMG]
[/CENTER]