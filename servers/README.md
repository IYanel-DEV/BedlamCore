# Local servers

Run `./setup.ps1` from this directory after building the plugin. The script creates:

- `legacy-1.8.8/start.bat` on port 25565
- `current-26.2/start.bat` on port 25566

Set `JAVA8_HOME` and `JAVA25_HOME` if the matching Java executables are not on `PATH`. Both servers use `online-mode=true`, so test clients must use a valid Microsoft-authenticated Minecraft session.
