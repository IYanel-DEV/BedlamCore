# BedlamCore

BedlamCore is an original, GUI-first Bed Wars minigame for Spigot and Paper. It targets one jar from Minecraft 1.8.8 through 26.2 without NMS.

## Build

```powershell
.\gradlew.bat clean check build
```

The jar is written to `build/libs/BedlamCore-0.1.0.jar`.

## Configure without commands

1. Join as an operator. The **Bedlam Setup** compass is placed in slot 9.
2. Right-click it and choose **Arena Setup**.
3. Set the lobby and spectator spawn.
4. Configure at least Red and Blue. Stand where players should spawn or where a generator/shop should be placed, then click the matching GUI entry. Look directly at a bed before choosing **Set Bed**.
5. Add diamond and emerald generators, then choose **Validate & Save**.
6. Open the compass again and use **Quick Join**. The game begins when the configured minimum player count is reached.

`/bedlam menu`, `join`, `leave`, `start`, and `reload` remain as recovery and console-friendly fallbacks.

## Local compatibility servers

Run `servers/setup.ps1` once. It downloads official Paper jars, copies BedlamCore, accepts the local test EULA, and creates two isolated servers:

- `servers/legacy-1.8.8` on port 25565
- `servers/current-26.2` on port 25566

Each folder receives a `start.bat`. Paper 1.8.8 needs Java 8; Paper 26.2 needs Java 25. The setup script can use `JAVA8_HOME` and `JAVA25_HOME` when those runtimes are not your default.

This project is not affiliated with Hypixel, Mojang, or Microsoft and includes no copied server code, maps, branding, or assets.
