param(
    [switch]$Force,
    [switch]$SkipCitizens
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$pluginJar = Join-Path $root "build\libs\BedlamCore-0.3.0.jar"

if (-not (Test-Path $pluginJar)) {
    & (Join-Path $root "gradlew.bat") clean check build
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

function Install-PaperServer {
    param(
        [string]$Name,
        [string]$Version,
        [int]$Port,
        [string]$JavaVariable,
        [string]$CitizensUrl
    )

    $server = Join-Path $PSScriptRoot $Name
    $plugins = Join-Path $server "plugins"
    New-Item -ItemType Directory -Force -Path $plugins | Out-Null
    $jar = Join-Path $server "paper.jar"

    $listener = netstat -ano -p tcp | Select-String "^\s*TCP\s+\S+:$Port\s+\S+\s+LISTENING\s+\d+\s*$"
    if ($listener) {
        throw "$Name is running on port $Port. Stop it before replacing BedlamCore.jar. Replacing a loaded plugin jar breaks Bukkit's classloader."
    }

    if ($Force -or -not (Test-Path $jar)) {
        $headers = @{ "User-Agent" = "BedlamCore/0.3.0 (https://github.com/IYanel-DEV/BedlamCore)" }
        $builds = Invoke-RestMethod -Headers $headers -Uri "https://fill.papermc.io/v3/projects/paper/versions/$Version/builds"
        $build = $builds | Where-Object { $_.channel -in @("STABLE", "RECOMMENDED", "stable", "recommended") } | Select-Object -First 1
        if (-not $build) { $build = $builds | Select-Object -First 1 }
        if (-not $build) { throw "No Paper build found for $Version" }
        $url = $build.downloads.'server:default'.url
        Invoke-WebRequest -Headers $headers -Uri $url -OutFile $jar
    }

    Copy-Item $pluginJar (Join-Path $plugins "BedlamCore.jar") -Force
    $citizensJar = Join-Path $plugins "Citizens.jar"
    if (-not $SkipCitizens -and ($Force -or -not (Test-Path $citizensJar))) {
        Invoke-WebRequest -Uri $CitizensUrl -OutFile $citizensJar
    }
    Set-Content -Path (Join-Path $server "eula.txt") -Value "eula=true" -Encoding ASCII
    @(
        "server-port=$Port"
        "online-mode=true"
        "motd=BedlamCore $Version test server"
        "gamemode=adventure"
        "spawn-protection=0"
        "view-distance=6"
        "simulation-distance=6"
        "allow-flight=true"
    ) | Set-Content -Path (Join-Path $server "server.properties") -Encoding ASCII

    $localPattern = if ($JavaVariable -eq "JAVA8_HOME") { "jdk8*" } else { "jdk25\jdk-25*" }
    $programPattern = if ($JavaVariable -eq "JAVA8_HOME") { "jre1.8*" } else { "jdk-25*" }
    $start = @'
@echo off
setlocal
set "JAVA_EXE="
if defined __JAVA_VARIABLE__ if exist "%__JAVA_VARIABLE__%\bin\java.exe" set "JAVA_EXE=%__JAVA_VARIABLE__%\bin\java.exe"
for /d %%D in ("%~dp0..\..\.tools\__LOCAL_PATTERN__") do if not defined JAVA_EXE if exist "%%~fD\bin\java.exe" set "JAVA_EXE=%%~fD\bin\java.exe"
for /d %%D in ("C:\Program Files\Java\__PROGRAM_PATTERN__") do if not defined JAVA_EXE if exist "%%~fD\bin\java.exe" set "JAVA_EXE=%%~fD\bin\java.exe"
if not defined JAVA_EXE set "JAVA_EXE=java"
"%JAVA_EXE%" -Xms1G -Xmx2G -jar paper.jar nogui
pause
'@.Replace("__JAVA_VARIABLE__", $JavaVariable).Replace("__LOCAL_PATTERN__", $localPattern).Replace("__PROGRAM_PATTERN__", $programPattern)
    Remove-Item (Join-Path $server "start.ps1") -ErrorAction SilentlyContinue
    Set-Content -Path (Join-Path $server "start.bat") -Value $start -Encoding ASCII
}

Install-PaperServer -Name "legacy-1.8.8" -Version "1.8.8" -Port 25565 -JavaVariable "JAVA8_HOME" -CitizensUrl "https://ci.citizensnpcs.co/job/Citizens2/3219/artifact/dist/target/Citizens-2.0.33-b3219.jar"
Install-PaperServer -Name "current-26.2" -Version "26.2" -Port 25566 -JavaVariable "JAVA25_HOME" -CitizensUrl "https://ci.citizensnpcs.co/job/Citizens2/lastSuccessfulBuild/artifact/dist/target/Citizens-2.0.43-b4232.jar"

Write-Host "BedlamCore servers are ready. Run each server's start.bat in a separate terminal."
