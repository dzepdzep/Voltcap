# Update 321: installs the Voltcap APK built last onto the handheld plugged in, then compiles it for speed — the step
# `adb install` skips (the in-app updater does it by itself). No file names to type.
#
# Usage, in PowerShell from anywhere:
#   & "C:\Users\User\AndroidStudioProjects\ShelfLauncher\tools\install.ps1"
#   & "C:\Users\User\AndroidStudioProjects\ShelfLauncher\tools\install.ps1" -Build      # build the release first
# With two handhelds plugged in, add -Serial <id> (the id `adb devices` lists).
param(
    [switch]$Build,
    [string]$Serial = ""
)
$ErrorActionPreference = "Continue"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if ($Build) {
    & "$root\gradlew.bat" assembleRelease
    if ($LASTEXITCODE -ne 0) { throw "The build failed - nothing was installed." }
}

$apk = Get-ChildItem "$root\app\build\outputs\apk\release\*.apk" -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($null -eq $apk) { throw "No release APK built yet - run with -Build, or .\gradlew.bat assembleRelease first." }

$adbArgs = @()
if ($Serial -ne "") { $adbArgs = @("-s", $Serial) }

Write-Host "Installing $($apk.Name) (built $($apk.LastWriteTime))"
# -r keeps the data, -d allows a lower version number than the one installed (a branch build over a release).
& adb @adbArgs install -r -d "$($apk.FullName)"
if ($LASTEXITCODE -ne 0) { throw "adb couldn't install it (is the handheld plugged in and allowed? 'adb devices' lists it)." }

Write-Host "Compiling for speed (the step a plain install skips)..."
& adb @adbArgs shell cmd package compile -m speed -f com.shelf.launcher
Write-Host "Done."
