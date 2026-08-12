param(
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$pluginJar = Join-Path $root "build\libs\BedlamCore-0.1.0.jar"

if (-not (Test-Path $pluginJar)) {
    & (Join-Path $root "gradlew.bat") clean check build
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

function Install-PaperServer {
    param(
        [string]$Name,
        [string]$Version,
        [int]$Port,
        [string]$JavaVariable
    )

    $server = Join-Path $PSScriptRoot $Name
    $plugins = Join-Path $server "plugins"
    New-Item -ItemType Directory -Force -Path $plugins | Out-Null
    $jar = Join-Path $server "paper.jar"

    if ($Force -or -not (Test-Path $jar)) {
        $headers = @{ "User-Agent" = "BedlamCore/0.1.0 (https://github.com/IYanel-DEV/BedlamCore)" }
        $builds = Invoke-RestMethod -Headers $headers -Uri "https://fill.papermc.io/v3/projects/paper/versions/$Version/builds"
        $build = $builds | Where-Object { $_.channel -in @("STABLE", "RECOMMENDED", "stable", "recommended") } | Select-Object -First 1
        if (-not $build) { $build = $builds | Select-Object -First 1 }
        if (-not $build) { throw "No Paper build found for $Version" }
        $url = $build.downloads.'server:default'.url
        Invoke-WebRequest -Headers $headers -Uri $url -OutFile $jar
    }

    Copy-Item $pluginJar (Join-Path $plugins "BedlamCore.jar") -Force
    Set-Content -Path (Join-Path $server "eula.txt") -Value "eula=true" -Encoding ASCII
    @(
        "server-port=$Port"
        "online-mode=false"
        "motd=BedlamCore $Version test server"
        "gamemode=adventure"
        "spawn-protection=0"
        "view-distance=6"
        "simulation-distance=6"
        "allow-flight=true"
    ) | Set-Content -Path (Join-Path $server "server.properties") -Encoding ASCII

    $start = @'
$ErrorActionPreference = "Stop"
$javaHome = [Environment]::GetEnvironmentVariable("__JAVA_VARIABLE__")
$candidates = @()
if ($javaHome) { $candidates += Join-Path $javaHome "bin\java.exe" }
$tools = Join-Path $PSScriptRoot "..\..\.tools"
$candidates += Get-ChildItem $tools -Recurse -File -Filter java.exe -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -match "__JAVA_PATTERN__" } | Select-Object -ExpandProperty FullName
$candidates += Get-ChildItem "C:\Program Files\Java" -Recurse -File -Filter java.exe -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -match "__JAVA_PATTERN__" } | Select-Object -ExpandProperty FullName
$java = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $java) { $java = (Get-Command java -ErrorAction Stop).Source }
& $java -Xms1G -Xmx2G -jar paper.jar nogui
'@.Replace("__JAVA_VARIABLE__", $JavaVariable).Replace("__JAVA_PATTERN__", $(if ($JavaVariable -eq "JAVA8_HOME") { "(?i)(jdk|jre)[-_]?1?\.?8" } else { "(?i)jdk[-_]?25" }))
    Set-Content -Path (Join-Path $server "start.ps1") -Value $start -Encoding UTF8
}

Install-PaperServer -Name "legacy-1.8.8" -Version "1.8.8" -Port 25565 -JavaVariable "JAVA8_HOME"
Install-PaperServer -Name "current-26.2" -Version "26.2" -Port 25566 -JavaVariable "JAVA25_HOME"

Write-Host "BedlamCore servers are ready. Run each server's start.ps1 in a separate terminal."
