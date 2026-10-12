# Update 144: one-time set-up, run it once:
#   powershell -ExecutionPolicy Bypass -File "C:\Users\User\AndroidStudioProjects\ShelfLauncher\tools\release\setup_github.ps1"
# 1. Your release key for every build (Android Studio's debug installs, GitHub releases, the in-app updater): it asks for
#    the .jks file Android Studio's "Generate Signed APK" made and writes keystore.properties (never goes to GitHub).
# 2. GitHub's command-line tool, signed in as dzepdzep, and git set up to commit as dzepdzep.
# 3. This folder then follows dzepdzep/Voltcap's main branch (where packs/v1 already is). Your files aren't touched.
$ErrorActionPreference = "Continue"
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $root

if (-not (Test-Path "$root\keystore.properties")) {
    Write-Host "Your release key: the .jks file Android Studio's Generate Signed APK made."
    $ks = (Read-Host "Full path of the .jks file").Trim().Trim('"')
    if ($ks -ne "" -and (Test-Path -LiteralPath $ks)) {
        $alias = Read-Host "Key alias"
        $sp = Read-Host "Key store password"
        $kp = Read-Host "Key password (just Enter if it's the same)"
        if ($kp -eq "") { $kp = $sp }
        "storeFile=$($ks -replace '\\','/')`nstorePassword=$sp`nkeyAlias=$alias`nkeyPassword=$kp`n" | Set-Content "$root\keystore.properties" -Encoding ASCII
        Write-Host "Saved keystore.properties. Keep a copy of the .jks somewhere safe: without it no update installs over Voltcap."
    } else {
        Write-Host "No .jks file there - skipped (builds stay on this PC's debug key)."
    }
}

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Write-Host "Installing GitHub's command-line tool (once)..."
    winget install --id GitHub.cli -e --silent --accept-package-agreements --accept-source-agreements
    $env:Path += ";C:\Program Files\GitHub CLI"
}
cmd /c "gh auth status >nul 2>nul"
if ($LASTEXITCODE -ne 0) {
    Write-Host "Sign in to GitHub once (as dzepdzep): a code shows here, the browser opens, paste it there."
    gh auth login --web --git-protocol https --hostname github.com
}
gh auth setup-git

# Commits as dzepdzep, with GitHub's no-reply address (your e-mail stays private on the public repo).
$id = gh api user --jq .id
git config user.name "dzepdzep"
if ($id) { git config user.email "$id+dzepdzep@users.noreply.github.com" }

$cur = git rev-parse --abbrev-ref HEAD
if ($cur -ne "main") {
    git fetch origin
    git branch -f main origin/main
    git symbolic-ref HEAD refs/heads/main
    git reset -q
}
Write-Host "Done. From now on each update's PowerShell block commits and pushes to github.com/dzepdzep/Voltcap."
