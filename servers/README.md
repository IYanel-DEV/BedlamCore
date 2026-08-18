# Local servers

Run `./setup.ps1` from this directory after building the plugin. It creates one Paper
test server per commonly-used Minecraft version, each with its own port and Java runtime.

Set up everything at once:

```powershell
.\setup.ps1
```

Or set up a single version:

```powershell
.\setup.ps1 -Version 1.16.5
```

## Servers and ports

| Server | Paper version | Port | Java | Notes |
|---|---|---|---|---|
| `legacy-1.8.8`  | 1.8.8  | 25565 | Java 8 (`JAVA8_HOME`)   | Classic PvP community, Bed Wars origin |
| `stable-1.12.2` | 1.12.2 | 25567 | Java 8 (`JAVA8_HOME`)   | Huge plugin ecosystem, still widely hosted |
| `stable-1.16.5` | 1.16.5 | 25568 | Java 11+ (`JAVA11_HOME`) | Nether Update, massive player base |
| `stable-1.20.4` | 1.20.4 | 25569 | Java 17+ (`JAVA17_HOME`) | Latest widely-used stable before chat reports |
| `latest-26.2`   | 26.2   | 25570 | Java 25+ (`JAVA25_HOME`) | Bleeding edge Paper, regression testing |

Ports start at 25565 and increment; 25566 is intentionally skipped (legacy allocation).
All servers use `online-mode=true`, so test clients need a valid Microsoft-authenticated
Minecraft session.

## Java runtimes

Each `start.bat` resolves Java in this order: the matching `JAVA*_HOME` environment
variable, then a local `.tools/` JDK, then `C:\Program Files\Java\`, then `java` on `PATH`.
Set the relevant variable if the runtime is not discovered automatically:

- `JAVA8_HOME`  → 1.8.8, 1.12.2
- `JAVA11_HOME` → 1.16.5
- `JAVA17_HOME` → 1.20.4
- `JAVA25_HOME` → 26.2

## Citizens (soft dependency)

`setup.ps1` pins these builds so queue/player NPCs stay silent across remounts (NMS silent
can clear; BedlamCore remutes on Citizens `NPCSpawnEvent` + a 20-tick safety net). Use the
same jars on operator servers to avoid ambient NPC noise fights:

| Test server | Citizens build | Notes |
|---|---|---|
| `legacy-1.8.8`  | **2.0.33-b3219** | Pinned, verified silent |
| `stable-1.12.2` | **2.0.33-b3219** | Same legacy line |
| `stable-1.16.5` | **2.0.43-b4232+** (lastSuccessful) | Modern Citizens |
| `stable-1.20.4` | **2.0.43-b4232+** (lastSuccessful) | Modern Citizens |
| `latest-26.2`   | **2.0.43-b4232+** (lastSuccessful) | Modern Citizens |

Pinned URLs:

- 2.0.33-b3219: https://ci.citizensnpcs.co/job/Citizens2/3219/artifact/dist/target/Citizens-2.0.33-b3219.jar
- lastSuccessful: https://ci.citizensnpcs.co/job/Citizens2/lastSuccessfulBuild/artifact/dist/target/Citizens-2.0.43-b4232.jar

Skip with `./setup.ps1 -SkipCitizens` to exercise the built-in armor-stand / mob fallback.
