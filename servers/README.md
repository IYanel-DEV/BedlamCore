# Local servers

Run `./setup.ps1` from this directory after building the plugin. The script creates:

- `legacy-1.8.8/start.bat` on port 25565
- `current-26.2/start.bat` on port 25566

Set `JAVA8_HOME` and `JAVA25_HOME` if the matching Java executables are not on `PATH`. Both servers use `online-mode=true`, so test clients must use a valid Microsoft-authenticated Minecraft session.

## Citizens (soft dependency)

`setup.ps1` pins these builds so queue/player NPCs stay silent across remounts (NMS silent can clear; BedlamCore remutes on Citizens `NPCSpawnEvent` + a 20-tick safety net). Use the same jars on operator servers to avoid ambient NPC noise fights:

| Test server | Citizens build | URL |
|---|---|---|
| `legacy-1.8.8` | **2.0.33-b3219** | https://ci.citizensnpcs.co/job/Citizens2/3219/artifact/dist/target/Citizens-2.0.33-b3219.jar |
| `current-26.2` | **2.0.43-b4232** (lastSuccessful at setup time) | https://ci.citizensnpcs.co/job/Citizens2/lastSuccessfulBuild/artifact/dist/target/Citizens-2.0.43-b4232.jar |

Skip with `./setup.ps1 -SkipCitizens` to exercise the built-in armor-stand / mob fallback.
