# BedlamCore

Original, GUI-first Bed Wars minigame plugin for **Spigot 1.8.8** through **Paper 26.2**. One jar, **Java 8** bytecode (build with a modern JDK + Gradle toolchain). Current release: **0.10.1**.

BedlamCore is an independent project. It is **not** affiliated with Hypixel, Mojang, or Microsoft, and ships no copied server code, maps, branding, or assets.

## Features

- **Lobby NPCs** — Solo/Doubles join NPCs (Citizens fake-player when available, armor-stand fallback), setup compass for operators
- **Multi-arena** — Solo and Doubles worlds, draft/apply setup, waiting structures, spectator spawn, teams/beds/forges/shops/gens
- **Hypixel-like shop & upgrades** — category GUIs, tools/armor tiers, team upgrades & traps
- **Forge share** — shared forge pickup / split behavior; voided ores return to the forge pile
- **Bridge egg** — 3-wide bridge egg with trail placement
- **Soft spectate** — adventure + flight + invisibility (not `GameMode.SPECTATOR`)
- **Stats / tokens / XP** — match rewards, lobby scoreboard
- **Kill messages**, potions, and **invis armor** (armor hidden to others; nametag / arrow polish)
- **World pristine reset** — arenas restore cleanly between matches (waiting paste stripped before save)
- Sounds for chests, NPCs, pearls, buys, gens, countdown; soft adventure spectate and win checks that ignore empty teams

## Install

1. Build or download `BedlamCore-0.10.1.jar` from [Releases](https://github.com/IYanel-DEV/BedlamCore/releases).
2. Drop the jar into your server `plugins/` folder and restart.
3. Join as an operator — the **Bedlam Setup** compass (hotbar slot 9) opens network/lobby setup in the lobby, or that world's arena draft/setup in a game world.
4. Set lobby spawn + Solo/Doubles NPCs, then create or edit game worlds (waiting spawn, spectator, teams, beds, forges, shops, diamond/emerald gens). **Apply** saves; **Cancel** discards drafts.
5. Players join via lobby NPCs (or `/bedlam` fallbacks). Admins can `/bedlam forcestart` in a waiting arena for solo testing.

`config.yml` covers lobby-on-join teleport, world/chat/tab isolation, team chat, mode minimums, and scoreboard footer text.

## Build

```powershell
.\gradlew.bat clean check build
```

```bash
./gradlew clean check build
```

Jar output: `build/libs/BedlamCore-0.10.1.jar`.

## Local test servers

Run `servers/setup.ps1` once. It downloads Paper jars, copies BedlamCore, optionally installs Citizens, accepts the EULA, and creates:

- `servers/legacy-1.8.8` — port **25565** (Java 8; set `JAVA8_HOME` if needed)
- `servers/current-26.2` — port **25566** (Java 25; set `JAVA25_HOME` if needed)

Pass `-SkipCitizens` to exercise the built-in NPC fallback. Each folder gets a `start.bat`. Both use `online-mode=true`.

## Commands

`/bedlam` (`/bc`) — `menu`, `solo`, `doubles`, `leave`, `spawnbuild`, `forcestart`, `reload`  
`/leave` — return to lobby

## License / affiliation

Not affiliated with Hypixel, Mojang, or Microsoft.
