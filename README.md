# BedlamCore

BedlamCore is an original, GUI-first Bed Wars minigame for Spigot and Paper. It targets one jar from Minecraft 1.8.8 through 26.2 without NMS.

## Build

```powershell
.\gradlew.bat clean check build
```

The jar is written to `build/libs/BedlamCore-0.2.0.jar`.

## Configure without commands

1. Join as an operator. The **Bedlam Setup** compass is placed in slot 9.
2. Choose **Lobby Setup** to set the join spawn and place the Solo and Doubles NPCs. Right-click a block with the supplied armor stand to place an NPC; shift-left-click it to cycle its entity type. Choose **Apply** to save or **Cancel** to discard the draft.
3. Choose **Game World Setup** to create a Solo or Doubles world, view every arena, teleport to one, edit it, or delete it after confirmation.
4. Entering an existing game world opens its setup for administrators. New and incomplete setups print every missing field in chat.
5. Configure spectator spawn, teams, beds, forges, shops, and diamond/emerald generators. Changes remain drafts until **Apply**; **Cancel** discards them and removes a newly-created draft world.
6. Players use an NPC or the menu for quick join or a list of waiting games. Admins may run `/bedlam forcestart` inside a waiting arena with one player for testing.

`/bedlam menu`, `solo`, `doubles`, `leave`, `forcestart`, and `reload` remain as recovery and console-friendly fallbacks.

`config.yml` controls lobby-on-join teleporting, world/chat/tab isolation, team chat prefixes and suffix, mode minimum-player counts, and scoreboard footer text.

## Local compatibility servers

Run `servers/setup.ps1` once. It downloads official Paper jars, copies BedlamCore, accepts the local test EULA, and creates two isolated servers:

- `servers/legacy-1.8.8` on port 25565
- `servers/current-26.2` on port 25566

Each folder receives a `start.bat`. Paper 1.8.8 needs Java 8; Paper 26.2 needs Java 25. The setup script can use `JAVA8_HOME` and `JAVA25_HOME` when those runtimes are not your default.

This project is not affiliated with Hypixel, Mojang, or Microsoft and includes no copied server code, maps, branding, or assets.
