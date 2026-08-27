param(
    [Parameter(Mandatory = $true)]
    [string]$Name,
    [int]$Port = 0
)
$ErrorActionPreference = "Continue"
$dir = Join-Path $PSScriptRoot $Name
$log = Join-Path $dir "logs\latest.log"
if (Test-Path $log) { Remove-Item $log -Force }

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = "cmd.exe"
$psi.Arguments = "/c cd /d `"$dir`" && call start.bat"
$psi.WorkingDirectory = $dir
$psi.UseShellExecute = $false
$psi.RedirectStandardInput = $true
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$proc = [System.Diagnostics.Process]::Start($psi)

$deadline = (Get-Date).AddSeconds(180)
$enabled = $false
$failed = $false
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 3
    if ($proc.HasExited) { break }
    if (Test-Path $log) {
        $tail = Get-Content $log -Tail 30 -ErrorAction SilentlyContinue
        if ($tail -match "BedlamCore \d+\.\d+\.\d+ enabled on") { $enabled = $true; break }
        if (($tail -join "`n") -match "FAILED TO ENABLE") { $failed = $true; break }
    }
}

Start-Sleep -Seconds 12
Write-Host "=== $Name : enabled=$enabled failed=$failed ==="
if (Test-Path $log) {
    Select-String -Path $log -Pattern "BedlamCore|PacketNpcs|Ambiguous|SEVERE|FAILED" | ForEach-Object { $_.Line } | Select-Object -First 25
    $errs = Select-String -Path $log -Pattern "Error occurred while enabling|Could not load plugin" | ForEach-Object { $_.Line }
    if ($errs) { Write-Host "ENABLE ERRORS:"; $errs | Select-Object -First 10 }
}

if (-not $proc.HasExited) {
    try { $proc.StandardInput.WriteLine("stop") } catch { }
    if (-not $proc.WaitForExit(45000)) { $proc.Kill() }
}
Write-Host "=== $Name done ==="
