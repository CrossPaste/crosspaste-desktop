# Boot the CrossPaste desktop app for a short time and fail if it crashes.
#
# Catches dependency / binary-compat regressions that `./gradlew app:build`
# can't see, because `build` never triggers the first Compose composition.
#
# Usage: powershell -ExecutionPolicy Bypass -File smoke-test.ps1 [bootTimeout] [composeWait]
#   bootTimeout  max seconds to wait for the "CrossPaste started" log line (default 120)
#   composeWait  extra seconds after boot to let the first Composition render (default 25)
#
# Exit code: 0 success, 1 detected failure.

param(
    [int]$BootTimeout = 120,
    [int]$ComposeWait = 25
)

$ErrorActionPreference = 'Continue'

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Root

$LogDir  = Join-Path $Root 'build\smoke-logs'
$LogFile = Join-Path $LogDir 'app.log'
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
Set-Content -Path $LogFile -Value '' -Encoding UTF8

Write-Host "[smoke] os=windows boot_timeout=${BootTimeout}s compose_wait=${ComposeWait}s"
Write-Host "[smoke] log file: $LogFile"
Write-Host "[smoke] launching: .\gradlew.bat --no-daemon app:run"

$proc = Start-Process -FilePath '.\gradlew.bat' `
    -ArgumentList '--no-daemon', 'app:run' `
    -NoNewWindow -PassThru `
    -RedirectStandardOutput $LogFile `
    -RedirectStandardError "$LogFile.err"

Write-Host "[smoke] launched, pid=$($proc.Id)"

$started = $false
for ($i = 0; $i -lt $BootTimeout; $i++) {
    if (Test-Path $LogFile) {
        if (Select-String -Path $LogFile -Pattern 'CrossPaste started' -Quiet) {
            $started = $true
            Write-Host "[smoke] boot marker detected at ${i}s"
            break
        }
    }
    if ($proc.HasExited) {
        Write-Host "[smoke] launcher process exited before boot marker (after ${i}s)"
        break
    }
    Start-Sleep -Seconds 1
}

if ($started) {
    Write-Host "[smoke] giving Compose ${ComposeWait}s to render first frames"
    Start-Sleep -Seconds $ComposeWait
}

# Tear down: kill the gradle process tree, then any orphan JVM the wrapper spawned.
if (-not $proc.HasExited) {
    Write-Host "[smoke] stopping launcher tree (pid=$($proc.Id))"
    try { Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue } catch {}
    Get-CimInstance Win32_Process -Filter "ParentProcessId=$($proc.Id)" -ErrorAction SilentlyContinue |
        ForEach-Object { try { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue } catch {} }
}
# Best-effort: kill any java.exe whose command line references CrossPaste.
Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -match '^(java|javaw)\.exe$' -and $_.CommandLine -match 'com\.crosspaste\.CrossPaste' } |
    ForEach-Object { try { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue } catch {} }

if (Test-Path "$LogFile.err") {
    Add-Content -Path $LogFile -Value (Get-Content "$LogFile.err" -Raw)
    Remove-Item "$LogFile.err" -ErrorAction SilentlyContinue
}

Write-Host '----- smoke app log tail -----'
if (Test-Path $LogFile) { Get-Content $LogFile -Tail 400 }
Write-Host '----- end smoke app log tail -----'

$failures = 0

if (-not $started) {
    if ((Test-Path $LogFile) -and (Select-String -Path $LogFile -Pattern 'Another instance of the application is already running' -Quiet)) {
        Write-Host '[smoke] FAIL: another CrossPaste DEV instance is already running and holds app.lock.'
        Write-Host '[smoke]       Quit it and rerun.'
    } else {
        Write-Host "[smoke] FAIL: app never logged 'CrossPaste started' within ${BootTimeout}s"
    }
    $failures++
}

# Fatal markers: real uncaught throwables + classic class-loading / binary-compat errors.
$fatalRe = '^(Exception in thread|Caused by:)|Uncaught exception in thread|UnsupportedClassVersionError|NoSuchMethodError|NoClassDefFoundError|ClassNotFoundException|IncompatibleClassChangeError|AbstractMethodError|LinkageError'

if (Test-Path $LogFile) {
    $hits = Select-String -Path $LogFile -Pattern $fatalRe -AllMatches
    if ($hits) {
        Write-Host '[smoke] FAIL: fatal pattern detected in app log:'
        $hits | Select-Object -First 50 | ForEach-Object { "$($_.LineNumber): $($_.Line)" } | Write-Host
        $failures++
    }
}

if ($failures -gt 0) {
    Write-Host "[smoke] smoke test FAILED (full log at $LogFile)"
    exit 1
}

Write-Host '[smoke] smoke test PASSED'
exit 0
