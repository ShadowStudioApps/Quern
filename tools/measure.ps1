# Quern TV performance measurement (see CLAUDE.md, "Performance measurement protocol").
# Usage (from repo root, TV connected over adb, release build installed + speed-profile compiled):
#   .\tools\measure.ps1
# Never uninstalls or clears app data.

param(
    [string]$Package = "com.quern.app",
    [int]$Runs = 3,
    [int]$MinFrames = 100
)

$ErrorActionPreference = "Stop"
$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) { $adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe" }
if (-not (Test-Path $adb)) { throw "adb not found on PATH or in the default Android SDK folder." }

# 1. Three cold starts
$starts = @()
for ($i = 1; $i -le 3; $i++) {
    & $adb shell am force-stop $Package
    Start-Sleep -Seconds 2
    $line = & $adb shell am start -W -n "$Package/.MainActivity" | Select-String "TotalTime"
    $starts += [int]($line.ToString().Split(":")[1].Trim())
    Start-Sleep -Seconds 3
}

# 2. Wait for Home content to load
Start-Sleep -Seconds 10

# 3. Fixed start position: focus begins on rail Home -> RIGHT into content -> DOWN, DOWN = first card of row 2
foreach ($k in 22, 20, 20) { & $adb shell input keyevent $k; Start-Sleep -Milliseconds 800 }
Start-Sleep -Seconds 2

# 4. Fixed path: 20x DPAD_RIGHT then 20x DPAD_LEFT (ends where it started), 120 ms sleep between keys.
#    Note: each `input keyevent` also costs ~150 ms to spawn, so the real interval is ~265 ms.
$keys = (@(22) * 20) + (@(21) * 20)
$loop = "for k in $($keys -join ' '); do input keyevent `$k; sleep 0.12; done"

function Get-Num($text, $pattern) {
    $m = [regex]::Match(($text -join "`n"), $pattern)
    if ($m.Success) { return $m.Groups[1].Value } else { return "?" }
}

$results = @()
for ($run = 1; $run -le $Runs; $run++) {
    & $adb shell dumpsys gfxinfo $Package reset | Out-Null
    $sw = [Diagnostics.Stopwatch]::StartNew()
    & $adb shell $loop
    $sw.Stop()
    Start-Sleep -Seconds 1
    $g = & $adb shell dumpsys gfxinfo $Package
    $frames = [int](Get-Num $g "Total frames rendered:\s*(\d+)")
    $results += [pscustomobject]@{
        Run      = $run
        Frames   = $frames
        Janky    = [int](Get-Num $g "Janky frames:\s*(\d+)")
        JankyPct = Get-Num $g "Janky frames:\s*\d+\s*\(([\d.]+)%\)"
        P50      = Get-Num $g "50th percentile:\s*(\d+)ms"
        P90      = Get-Num $g "90th percentile:\s*(\d+)ms"
        P95      = Get-Num $g "95th percentile:\s*(\d+)ms"
        P99      = Get-Num $g "99th percentile:\s*(\d+)ms"
        Seconds  = [math]::Round($sw.Elapsed.TotalSeconds, 1)
        Kept     = $frames -ge $MinFrames
    }
    Start-Sleep -Seconds 2
}

# 5. Memory
$pss = Get-Num (& $adb shell dumpsys meminfo $Package) "TOTAL PSS:\s*(\d+)"

# Report
"Cold start (ms): $($starts -join ' / ')"
$results | Format-Table -AutoSize | Out-String
$kept = $results | Where-Object Kept
if ($kept) {
    $tf = ($kept | Measure-Object Frames -Sum).Sum
    $tj = ($kept | Measure-Object Janky -Sum).Sum
    "Kept runs: $($kept.Count)/$Runs  Total frames: $tf  Janky: $tj ($([math]::Round(100 * $tj / $tf, 1))%)"
} else {
    "No run reached $MinFrames frames; repeat the measurement."
}
if ($pss -ne "?") { "TOTAL PSS: $([math]::Round([int]$pss / 1024, 1)) MB" }
