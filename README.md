# BedlamCore

BedlamCore is an original, GUI-first Bed Wars minigame for Spigot and Paper. It targets one jar from Minecraft 1.8.8 through 26.2 without NMS.

## Build

```powershell
.\gradlew.bat clean check build
```

The jar is written to `build/libs/BedlamCore-0.4.0.jar`.

## Configure without commands

1. Join as an operator. One contextual **Bedlam Setup** compass is placed in slot 9; the old menu star is removed. In the lobby it opens network/world setup, and inside a game world it opens that world's draft or saved setup.
2. Choose **Lobby Setup** to set the join spawn and place the Solo and Doubles NPCs. Right-click a block with the supplied armor stand to place an NPC; shift-left-click it to cycle its entity type. Choose **Apply** to save or **Cancel** to discard the draft.
3. Choose **Game World Setup** to create a Solo or Doubles world, view every arena, teleport to one, edit it, or delete it after confirmation.
4. Entering an existing game world opens its setup for administrators. New and incomplete setups print every missing field in chat.
5. Configure the temporary waiting structure spawn, spectator spawn, teams, beds, forges, shops, and diamond/emerald generators. The glass waiting structure is built automatically and restored when the match starts. Changes remain drafts until **Apply**; **Cancel** discards them and removes a newly-created draft world. Apply is blocked until every required field is complete, saves the world, and returns the operator to the lobby.
6. Players use an NPC or the menu for quick join or a list of waiting games. Admins may run `/bedlam forcestart` inside a waiting arena with one player for testing.

Shift-left-click a lobby NPC to choose its mob type, adult/baby state, and whether it looks at players (OFF by default). **Human Player** uses a real fake-player NPC when a compatible Citizens build is installed; otherwise it falls back to a human-shaped armor stand with the selected player head. Enter a Minecraft username or paste the direct `https://textures.minecraft.net/texture/...` link shown by Minecraft-Heads.

Use `/bc spawnbuild` as an operator to receive the waiting-build selector. Left-click one corner and right-click the opposite corner of a building. The selection must contain exactly one diamond block; that block becomes the player-spawn anchor when the saved building is pasted at each arena's waiting spawn.

Running games announce timed Diamond and Emerald generator tier upgrades and show the next upgrade on the sidebar. `/leave` immediately returns a player to the lobby and resolves the match when only one team remains. Beds cannot be entered or collected as drops, and players start with a wooden sword and armor but no free wool.

`/bedlam menu`, `solo`, `doubles`, `leave`, `spawnbuild`, `forcestart`, and `reload` remain as recovery and console-friendly fallbacks.

`config.yml` controls lobby-on-join teleporting, world/chat/tab isolation, team chat prefixes and suffix, mode minimum-player counts, and scoreboard footer text.

## Local compatibility servers

Run `servers/setup.ps1` once. It downloads official Paper jars, copies BedlamCore, installs the version-matched Citizens builds used for real player NPC testing, accepts the local test EULA, and creates two isolated servers. Pass `-SkipCitizens` to test BedlamCore's built-in NPC fallback instead.

- `servers/legacy-1.8.8` on port 25565
- `servers/current-26.2` on port 25566

Each folder receives a `start.bat`. Paper 1.8.8 needs Java 8; Paper 26.2 needs Java 25. The setup script can use `JAVA8_HOME` and `JAVA25_HOME` when those runtimes are not your default.
Both generated test servers use `online-mode=true`.

This project is not affiliated with Hypixel, Mojang, or Microsoft and includes no copied server code, maps, branding, or assets.
