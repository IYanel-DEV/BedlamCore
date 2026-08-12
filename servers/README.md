# Local servers

Run `./setup.ps1` from this directory after building the plugin. The script creates:

- `legacy-1.8.8/start.ps1` on port 25565
- `current-26.2/start.ps1` on port 25566

Set `JAVA8_HOME` and `JAVA25_HOME` if the matching Java executables are not on `PATH`. Both servers use offline mode only so local test clients can connect easily; never expose these test servers to the internet.
