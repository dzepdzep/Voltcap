# Update 141: puts a new Voltcap on GitHub (dzepdzep/Voltcap › Releases), where Settings › Updates & downloads finds it.
#
# Usage, in PowerShell from anywhere:
#   & "C:\Users\User\AndroidStudioProjects\ShelfLauncher\tools\release\release.ps1" 0.3.0 "What's new in this one"
#
# It raises versionCode by one and sets versionName in app\build.gradle.kts, builds the release APK, and makes the
# GitHub release "Voltcap <version>" (tag v<version>) with Voltcap-<version>.apk attached. The first time it installs GitHub's command-line tool (gh) and
# asks you to sign in once in the browser (as dzepdzep).
# The APK is signed with keystore.properties' key if you have one, else this PC's debug key — the same key as the
# Voltcap you install from Android Studio on this PC, so the update installs over it.
param(
    [Parameter(Mandatory = $true)][string]$Version,
    [string]$Notes = ""
)
# (Update 142: "Continue", not "Stop" — Windows PowerShell treats anything gh or Gradle print as a warning on their
# error stream as a failure under "Stop". Real failures are caught from the exit codes below.)
$ErrorActionPreference = "Continue"
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $root

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Write-Host "Installing GitHub's command-line tool (once)..."
    winget install --id GitHub.cli -e --silent --accept-package-agreements --accept-source-agreements
    $env:Path += ";C:\Program Files\GitHub CLI"
}
cmd /c "gh auth status >nul 2>nul"
if ($LASTEXITCODE -ne 0) {
    Write-Host "Sign in to GitHub once (as dzepdzep): a code shows here, the browser opens, paste it there."
    gh auth login --web --git-protocol https --hostname github.com
    cmd /c "gh auth status >nul 2>nul"
    if ($LASTEXITCODE -ne 0) { throw "Not signed in to GitHub - nothing was changed." }
}

$g = Join-Path $root "app\build.gradle.kts"
$t = Get-Content $g -Raw
$code = [int]([regex]::Match($t, 'versionCode\s*=\s*(\d+)').Groups[1].Value) + 1
$t = [regex]::Replace($t, 'versionCode\s*=\s*\d+', "versionCode = $code")
$t = [regex]::Replace($t, 'versionName\s*=\s*"[^"]*"', "versionName = `"$Version`"")
Set-Content -Path $g -Value $t -NoNewline
# (Update 144: the copy in project_copy too, so the next robocopy doesn't put the old version back.)
$gc = Join-Path $root "project_copy\app\build.gradle.kts"
if (Test-Path $gc) { Set-Content -Path $gc -Value $t -NoNewline }
Write-Host "Version $Version (build $code)"

& "$root\gradlew.bat" assembleRelease
if ($LASTEXITCODE -ne 0) { throw "The build failed - nothing was published." }
$apk = Get-ChildItem "$root\app\build\outputs\apk\release\*.apk" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
# (Update 209: attached as "Voltcap-<version>.apk".)
$out = Join-Path $env:TEMP "Voltcap-$Version.apk"
Copy-Item $apk.FullName $out -Force

# (Update 209: the new version number committed and pushed first, so the release's tag is on it.)
git add app/build.gradle.kts
git commit -q -m "Voltcap $Version" 2>$null
git push -q origin main

if ($Notes -eq "") { $Notes = "Voltcap $Version" }
gh release create "v$Version" $out -R dzepdzep/Voltcap --target main --title "Voltcap $Version" --notes $Notes
if ($LASTEXITCODE -ne 0) { throw "GitHub didn't take the release (see above)." }
Write-Host "Done: https://github.com/dzepdzep/Voltcap/releases/tag/v$Version"
