# Batch 518c: Cloudflare STAGING tasks for voltcap-api, run on the owner's PC (fake credentials only).
# The action is the first line of runtime/.run-now: login | whoami | deploy-staging | check-staging | delete-staging
# Output: runtime/task-result.log (e-mail addresses are masked). Nothing here touches the production Worker "voltcap-api".
$ErrorActionPreference = "Continue"
$api = Split-Path -Parent $PSScriptRoot
Set-Location $api
$log = Join-Path $PSScriptRoot "task-result.log"
$urls = Join-Path $PSScriptRoot "staging-urls.txt"
function Mask($t) { $t -replace '[\w.+-]+@[\w-]+\.[\w.]+', '<email>' }
function L($m) { $m = Mask "$m"; Write-Host $m; $m | Out-File $log -Append -Encoding utf8 }
function W([string]$cmdline) { $o = cmd /c "$cmdline 2>&1"; $script:code = $LASTEXITCODE; return $o }
$action = (Get-Content (Join-Path $PSScriptRoot ".run-now") -TotalCount 1).Trim()
"=== $(Get-Date -Format o) action=$action" | Out-File $log -Encoding utf8
$env:WRANGLER_SEND_METRICS = "false"; $env:NO_COLOR = "1"
switch ($action) {
  "login" {
    $o = W "npx --yes wrangler@4 login"; L "login exit=$code"; $o | Select-Object -Last 8 | ForEach-Object { L $_ }
    $o = W "npx --yes wrangler@4 whoami"; $o | ForEach-Object { L $_ }
  }
  "subdomain" {
    # The account's workers.dev subdomain (needed once before any deploy): read it, or register a neutral one.
    $cfg = Get-Content "$env:APPDATA\xdg.config\.wrangler\config\default.toml" -Raw
    $tok = ([regex]'oauth_token\s*=\s*"([^"]+)"').Match($cfg).Groups[1].Value
    $acc = "a4553ca28e18a670b602472b2afb6556"
    $h = @{ Authorization = "Bearer $tok"; "Content-Type" = "application/json" }
    $base = "https://api.cloudflare.com/client/v4/accounts/$acc/workers/subdomain"
    try { $r = Invoke-RestMethod -Uri $base -Headers $h -Method GET; L ("current=" + $r.result.subdomain) } catch { L ("get failed: " + $_.Exception.Response.StatusCode.value__) ; $r = $null }
    if (-not ($r -and $r.result.subdomain)) {
      foreach ($name in @("voltcap", "voltcap-api", ("voltcap-" + (Get-Random -Minimum 1000 -Maximum 9999)))) {
        try { $p = Invoke-RestMethod -Uri $base -Headers $h -Method PUT -Body (@{ subdomain = $name } | ConvertTo-Json); L ("registered=" + $p.result.subdomain); break }
        catch { L ("$name refused: " + $_.ErrorDetails.Message) }
      }
    }
  }
  "diag" {
    # workers.dev status of the account and of the two STAGING scripts (enabled for them if it isn't).
    $cfg = Get-Content "$env:APPDATA\xdg.config\.wrangler\config\default.toml" -Raw
    $tok = ([regex]'oauth_token\s*=\s*"([^"]+)"').Match($cfg).Groups[1].Value
    $acc = "a4553ca28e18a670b602472b2afb6556"
    $h = @{ Authorization = "Bearer $tok"; "Content-Type" = "application/json" }
    try { $r = Invoke-RestMethod -Uri "https://api.cloudflare.com/client/v4/accounts/$acc/workers/subdomain" -Headers $h; L ("account subdomain=" + ($r.result | ConvertTo-Json -Compress)) } catch { L ("account subdomain error " + $_.ErrorDetails.Message) }
    foreach ($w in @("voltcap-api-staging", "voltcap-mock-staging")) {
      $u = "https://api.cloudflare.com/client/v4/accounts/$acc/workers/scripts/$w/subdomain"
      try { $r = Invoke-RestMethod -Uri $u -Headers $h; L ("$w subdomain=" + ($r.result | ConvertTo-Json -Compress))
        if (-not $r.result.enabled) { $p = Invoke-RestMethod -Uri $u -Headers $h -Method POST -Body '{"enabled":true}'; L ("$w enabled -> " + ($p.result | ConvertTo-Json -Compress)) } }
      catch { L ("$w error " + $_.ErrorDetails.Message) }
    }
    try { $x = Invoke-WebRequest -UseBasicParsing "https://voltcap-api-staging.voltcap-api.workers.dev/v1/health" -TimeoutSec 10; L ("health " + $x.StatusCode + " " + $x.Content) } catch { L ("health " + $_.Exception.Response.StatusCode.value__) }
  }
  "usage" {
    # Requests, errors and CPU time of the account's Workers in the last 6 hours (Cloudflare GraphQL Analytics API).
    $null = W "npx --yes wrangler@4 whoami"   # refreshes wrangler's OAuth token when it expired
    $cfg = Get-Content "$env:APPDATA\xdg.config\.wrangler\config\default.toml" -Raw
    $tok = ([regex]'oauth_token\s*=\s*"([^"]+)"').Match($cfg).Groups[1].Value
    $acc = "a4553ca28e18a670b602472b2afb6556"
    $since = (Get-Date).ToUniversalTime().AddHours(-6).ToString("yyyy-MM-ddTHH:mm:ssZ"); $until = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    $q = @"
query { viewer { accounts(filter: {accountTag: "$acc"}) {
  workersInvocationsAdaptive(limit: 20, filter: {datetime_geq: "$since", datetime_leq: "$until"}) {
    dimensions { scriptName status } sum { requests errors subrequests } quantiles { cpuTimeP50 cpuTimeP99 wallTimeP99 } }
  durableObjectsInvocationsAdaptiveGroups(limit: 20, filter: {datetime_geq: "$since", datetime_leq: "$until"}) {
    dimensions { scriptName } sum { requests } }
} } }
"@
    $body = @{ query = $q } | ConvertTo-Json -Depth 4
    try { $r = Invoke-RestMethod -Uri "https://api.cloudflare.com/client/v4/graphql" -Method POST -Headers @{ Authorization = "Bearer $tok"; "Content-Type" = "application/json" } -Body $body
      L ($r | ConvertTo-Json -Depth 12 -Compress) } catch { L ("graphql error " + $_.ErrorDetails.Message) }
    foreach ($ep in @("subscriptions", "workers/scripts")) {
      try { $x = Invoke-RestMethod -Uri "https://api.cloudflare.com/client/v4/accounts/$acc/$ep" -Headers @{ Authorization = "Bearer $tok" }
        if ($ep -eq "subscriptions") { @($x.result) | ForEach-Object { L ("subscription: " + $_.rate_plan.id + " / " + $_.rate_plan.public_name + " price=" + $_.price + " state=" + $_.state) }; if (-not @($x.result).Count) { L "subscriptions: none" } }
        else { L ("workers: " + ((@($x.result) | ForEach-Object { $_.id + "(" + $_.usage_model + ")" }) -join ", ")) } }
      catch { L ("$ep status " + $_.Exception.Response.StatusCode.value__) }
    }
  }
  "reset" { $u = Get-Content $urls; Invoke-WebRequest -UseBasicParsing -Method POST "$($u[1])/__reset" | Out-Null; L "mock calls reset" }
  "calls" {
    # What the fake upstream services received since "reset" (paths only), i.e. what one app action cost upstream.
    $u = Get-Content $urls; $c = Invoke-RestMethod "$($u[1])/__calls"
    L ("upstream calls=" + @($c).Count)
    @($c) | Group-Object { $_.path + " " + ([regex]'media=([^&]+)').Match($_.query).Groups[1].Value } | Sort-Object Count -Descending | ForEach-Object { L ("  " + $_.Count + "  " + $_.Name) }
  }
  "hash-backup" {
    # P2 check: sha256 + size of every file in the newest Claude outputs\backup_*_P2 folder (read only), into a NEW manifest next to it.
    $root = Split-Path -Parent $api | Split-Path -Parent
    $b = Get-ChildItem (Join-Path $root "Claude outputs") -Directory -Filter "backup_*_P2" | Sort-Object Name | Select-Object -Last 1
    if (-not $b) { L "NO_BACKUP_FOLDER"; break }
    $out = Join-Path $b.FullName ("SHA256SUMS_pc_" + (Get-Date -Format "HHmmss") + ".txt")
    $files = Get-ChildItem $b.FullName -Recurse -File | Where-Object { $_.Name -notlike "SHA256SUMS*" }
    $lines = foreach ($f in $files) { $h = (Get-FileHash -Algorithm SHA256 -LiteralPath $f.FullName).Hash.ToLower(); $rel = $f.FullName.Substring($b.FullName.Length + 1); "$h  $($f.Length)  $rel" }
    $lines | Out-File $out -Encoding utf8
    L ("backup=" + $b.Name + " files=" + @($files).Count + " bytes=" + (($files | Measure-Object Length -Sum).Sum) + " manifest=" + (Split-Path $out -Leaf))
  }
  "guard-tests" {
    # Batch 521: the QA install guard's regression tests and a DRY RUN against what is on this PC and plugged in (installs
    # nothing): every built APK's package / signer verdict, and every adb serial's target verdict.
    $root = Split-Path -Parent $api | Split-Path -Parent
    Set-Location $root
    foreach ($t in @("tools\adb_mcp\test\guard.test.js", "tools\adb_mcp\test\helpers.test.js")) {
      $o = W "node $t"; L "$t exit=$code"; $o | Select-Object -Last 4 | ForEach-Object { L "  $_" }
    }
    Get-ChildItem "app\build\outputs\apk" -Recurse -Filter *.apk | ForEach-Object {
      $o = W "node tools\adb_mcp\guard.js check-apk `"$($_.FullName)`""; L ("apk " + $_.Directory.Name + "\" + $_.Name + ": " + ($o -join " "))
    }
    $adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"; $env:ADB = $adb
    $ser = (& $adb devices) | Select-Object -Skip 1 | Where-Object { $_ -match "\S" } | ForEach-Object { ($_ -split "\s+")[0] }
    foreach ($s in $ser) { $o = W "node tools\adb_mcp\guard.js check-target `"$s`""; L ("target " + ($o -join " ")) }
  }
  "hash-files" {
    # Batch 521 (P3 / P4): sha256 + size of the files named on lines 2.. of .run-now (paths relative to the project root), read only.
    $root = Split-Path -Parent $api | Split-Path -Parent
    Get-Content (Join-Path $PSScriptRoot ".run-now") | Select-Object -Skip 1 | Where-Object { $_.Trim() } | ForEach-Object {
      $f = Join-Path $root $_.Trim()
      if (Test-Path -LiteralPath $f -PathType Leaf) { $h = (Get-FileHash -Algorithm SHA256 -LiteralPath $f).Hash.ToLower(); L ("$h  " + (Get-Item -LiteralPath $f).Length + "  " + $_.Trim()) }
      else { L ("MISSING  " + $_.Trim()) }
    }
  }
  "redeploy-production" {
    # Code / vars only: the existing secrets (and the media-signing key, so handed-out addresses stay valid) are kept.
    $o = W "npx --yes wrangler@4 deploy --var PUBLIC_BASE:https://voltcap-api.voltcap-api.workers.dev"
    L "production redeploy exit=$code"; $o | Select-Object -Last 14 | ForEach-Object { L $_ }
    $o = W "npx --yes wrangler@4 secret list"; L ("secret names: " + (($o | Select-String -Pattern '"name":\s*"([A-Z_]+)"' -AllMatches | ForEach-Object { $_.Matches } | ForEach-Object { $_.Groups[1].Value }) -join ","))
  }
  "deploy-production" {
    # The PRODUCTION Worker "voltcap-api" (top-level config: no STAGE, no MOCK_UPSTREAM, no service bindings), then its
    # secrets from the gitignored properties files plus a new random MEDIA_SIGNING_KEY. Values are never printed; the
    # temporary JSON is deleted at once.
    $o = W "npx --yes wrangler@4 deploy --var PUBLIC_BASE:https://voltcap-api.voltcap-api.workers.dev"
    L "production deploy exit=$code"; $o | Select-Object -Last 22 | ForEach-Object { L $_ }
    $prod = ($o | Select-String -Pattern 'https://voltcap-api\.[a-z0-9-]+\.workers\.dev' -AllMatches | ForEach-Object { $_.Matches.Value } | Select-Object -First 1)
    if (-not $prod) { L "PRODUCTION_URL_NOT_FOUND"; break }
    $root = Split-Path -Parent $api | Split-Path -Parent
    function Props($f) { $h = @{}; Get-Content (Join-Path $root $f) | Where-Object { $_ -match '^\s*([A-Za-z]+)\s*=\s*(.*?)\s*$' } | ForEach-Object { $h[$Matches[1]] = $Matches[2] }; $h }
    $ss = Props "screenscraper.properties"; $ig = Props "igdb.properties"
    $bytes = New-Object byte[] 32; [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $sec = [ordered]@{ SS_DEV_ID = $ss["devId"]; SS_DEV_PASSWORD = $ss["devPassword"]; SS_SOFT_NAME = $ss["softName"];
      TWITCH_CLIENT_ID = $ig["clientId"]; TWITCH_CLIENT_SECRET = $ig["clientSecret"]; MEDIA_SIGNING_KEY = [Convert]::ToBase64String($bytes) }
    $missing = @($sec.Keys | Where-Object { -not $sec[$_] }); if ($missing.Count) { L ("MISSING " + ($missing -join ",")); break }
    $tmp = Join-Path $env:TEMP ("vc-" + [guid]::NewGuid().ToString("N") + ".json")
    $sec | ConvertTo-Json | Out-File $tmp -Encoding ascii
    $o = W "npx --yes wrangler@4 secret bulk `"$tmp`""; Remove-Item $tmp -Force
    L ("secrets exit=$code temp_file_left=" + (Test-Path $tmp)); $o | Select-String -Pattern "secret|Success|error" | ForEach-Object { L ("  " + $_.Line) }
    $o = W "npx --yes wrangler@4 secret list"; L "secret names:"; $o | Select-String -Pattern '"name"' | ForEach-Object { L ("  " + $_.Line.Trim()) }
    $prod | Out-File (Join-Path $PSScriptRoot "production-url.txt") -Encoding ascii
    L "PRODUCTION_URL=$prod"
  }
  "ss-diag" {
    $root = Split-Path -Parent $api | Split-Path -Parent; $ss = @{}
    Get-Content (Join-Path $root "screenscraper.properties") | Where-Object { $_ -match '^\s*([A-Za-z]+)\s*=\s*(.*?)\s*$' } | ForEach-Object { $ss[$Matches[1]] = $Matches[2] }
    $env:SS_DIAG = ($ss["devId"], $ss["devPassword"], $ss["softName"]) -join "`n"
    $prod = (Get-Content (Join-Path $PSScriptRoot "production-url.txt")).Trim()
    $o = cmd /c "node runtime\ss-diag.mjs $prod 2>&1"; $c = $LASTEXITCODE; $env:SS_DIAG = ""
    $o | ForEach-Object { L $_ }; L "SS_DIAG_EXIT=$c"
  }
  "prod-check" {
    $root = Split-Path -Parent $api | Split-Path -Parent
    $vals = @()
    foreach ($f in @("screenscraper.properties", "igdb.properties")) { Get-Content (Join-Path $root $f) | Where-Object { $_ -match '^\s*(devId|devPassword|clientSecret)\s*=\s*(.+?)\s*$' } | ForEach-Object { $vals += $Matches[2] } }
    $dl = Join-Path $root "secret_denylist.txt"; if (Test-Path $dl) { $vals += Get-Content $dl | Where-Object { $_ -and -not $_.StartsWith("#") } }
    $env:CHECK_SECRETS = ($vals -join "`n")
    $prod = (Get-Content (Join-Path $PSScriptRoot "production-url.txt")).Trim()
    Start-Sleep -Seconds 3
    $o = cmd /c "node runtime\prod-check.mjs $prod 2>&1"; $c = $LASTEXITCODE; $env:CHECK_SECRETS = ""
    $o | ForEach-Object { L $_ }; L "PROD_CHECK_EXIT=$c"
  }
  "denylist-old" {
    # Keeps the CURRENT (about to be replaced) Twitch secret in the local, gitignored denylist, so APK scans keep checking it.
    $root = Split-Path -Parent $api | Split-Path -Parent
    $old = (Get-Content (Join-Path $root "igdb.properties") | Where-Object { $_ -match '^\s*clientSecret\s*=\s*(.+?)\s*$' } | ForEach-Object { $Matches[1] })
    $dl = Join-Path $root "secret_denylist.txt"
    $have = (Test-Path $dl) -and ((Get-Content $dl) -contains $old)
    if (-not $have) { $old | Out-File $dl -Append -Encoding ascii }
    L ("old Twitch secret in denylist=" + ((Get-Content $dl) -contains $old))
  }
  "twitch-mint-old" {
    # One app token made with the CURRENT secret, kept locally (gitignored) to test after the rotation whether old tokens die.
    $root = Split-Path -Parent $api | Split-Path -Parent; $ig = @{}
    Get-Content (Join-Path $root "igdb.properties") | Where-Object { $_ -match '^\s*([A-Za-z]+)\s*=\s*(.*?)\s*$' } | ForEach-Object { $ig[$Matches[1]] = $Matches[2] }
    try { $r = Invoke-RestMethod -Method POST "https://id.twitch.tv/oauth2/token" -Body @{ client_id = $ig["clientId"]; client_secret = $ig["clientSecret"]; grant_type = "client_credentials" }
      $r.access_token | Out-File (Join-Path $PSScriptRoot ".old-token") -Encoding ascii; L ("old token minted, expires_in=" + $r.expires_in) } catch { L ("mint failed " + $_.Exception.Response.StatusCode.value__) }
  }
  "twitch-upload-new" {
    # After the owner put the NEW secret into igdb.properties: only that secret is replaced on the production Worker.
    $root = Split-Path -Parent $api | Split-Path -Parent
    $new = (Get-Content (Join-Path $root "igdb.properties") | Where-Object { $_ -match '^\s*clientSecret\s*=\s*(.+?)\s*$' } | ForEach-Object { $Matches[1] })
    $dl = Join-Path $root "secret_denylist.txt"
    if ((Test-Path $dl) -and ((Get-Content $dl) -contains $new)) { L "NEW_SECRET_IS_THE_OLD_ONE (igdb.properties not updated yet)"; break }
    $tmp = Join-Path $env:TEMP ("vc-" + [guid]::NewGuid().ToString("N") + ".json")
    @{ TWITCH_CLIENT_SECRET = $new } | ConvertTo-Json | Out-File $tmp -Encoding ascii
    $o = W "npx --yes wrangler@4 secret bulk `"$tmp`""; Remove-Item $tmp -Force
    L ("new secret uploaded exit=$code temp_file_left=" + (Test-Path $tmp)); $o | Select-String -Pattern "secret|Success|error" | ForEach-Object { L ("  " + $_.Line) }
  }
  "cleanup-old-token" {
    # The test token kept for the rotation check: revoked at Twitch, then the local file deleted.
    $root = Split-Path -Parent $api | Split-Path -Parent; $ig = @{}
    Get-Content (Join-Path $root "igdb.properties") | Where-Object { $_ -match '^\s*([A-Za-z]+)\s*=\s*(.*?)\s*$' } | ForEach-Object { $ig[$Matches[1]] = $Matches[2] }
    $ot = Join-Path $PSScriptRoot ".old-token"
    if (Test-Path $ot) {
      $t = (Get-Content $ot).Trim()
      try { Invoke-WebRequest -UseBasicParsing -Method POST "https://id.twitch.tv/oauth2/revoke" -Body @{ client_id = $ig["clientId"]; token = $t } | Out-Null; L "test token revoked" } catch { L ("revoke status " + $_.Exception.Response.StatusCode.value__) }
      try { Invoke-WebRequest -UseBasicParsing "https://id.twitch.tv/oauth2/validate" -Headers @{ Authorization = "OAuth $t" } | Out-Null; L "test token still valid" } catch { L ("test token validate after revoke " + $_.Exception.Response.StatusCode.value__) }
      Remove-Item $ot -Force; L ("token file left=" + (Test-Path $ot))
    } else { L "no token file" }
  }
  "twitch-verify" {
    $root = Split-Path -Parent $api | Split-Path -Parent; $ig = @{}
    Get-Content (Join-Path $root "igdb.properties") | Where-Object { $_ -match '^\s*([A-Za-z]+)\s*=\s*(.*?)\s*$' } | ForEach-Object { $ig[$Matches[1]] = $Matches[2] }
    $old = (Get-Content (Join-Path $root "secret_denylist.txt") | Where-Object { $_ -and -not $_.StartsWith("#") } | Select-Object -Last 1)
    function Tok($secret) { try { $r = Invoke-WebRequest -UseBasicParsing -Method POST "https://id.twitch.tv/oauth2/token" -Body @{ client_id = $ig["clientId"]; client_secret = $secret; grant_type = "client_credentials" }; return @($r.StatusCode, ($r.Content | ConvertFrom-Json).access_token) } catch { return @($_.Exception.Response.StatusCode.value__, $null) } }
    $a = Tok $old; L ("old secret -> token request status " + $a[0])
    $b = Tok $ig["clientSecret"]; L ("new secret -> token request status " + $b[0])
    $ot = Join-Path $PSScriptRoot ".old-token"
    if (Test-Path $ot) {
      $t = (Get-Content $ot).Trim()
      try { $x = Invoke-WebRequest -UseBasicParsing -Method POST "https://api.igdb.com/v4/games" -Headers @{ "Client-ID" = $ig["clientId"]; Authorization = "Bearer $t" } -Body "fields name; limit 1;"; L ("token made before the rotation -> IGDB status " + $x.StatusCode) } catch { L ("token made before the rotation -> IGDB status " + $_.Exception.Response.StatusCode.value__) }
      try { $v = Invoke-WebRequest -UseBasicParsing "https://id.twitch.tv/oauth2/validate" -Headers @{ Authorization = "OAuth $t" }; L ("old token validate " + $v.StatusCode) } catch { L ("old token validate " + $_.Exception.Response.StatusCode.value__) }
    }
    if ($b[1]) { try { Invoke-WebRequest -UseBasicParsing -Method POST "https://id.twitch.tv/oauth2/revoke" -Body @{ client_id = $ig["clientId"]; token = $b[1] } | Out-Null; L "test token from the new secret revoked" } catch {} }
    $prod = (Get-Content (Join-Path $PSScriptRoot "production-url.txt")).Trim()
    try { $w = Invoke-WebRequest -UseBasicParsing -Method POST "$prod/v1/igdb/games" -Headers @{ "x-voltcap-install" = "twitch-verify-0000000000" } -Body ('fields name; where id = ' + (Get-Random -Minimum 1000 -Maximum 99999) + ';'); L ("production Worker IGDB after rotation -> " + $w.StatusCode) } catch { L ("production Worker IGDB after rotation -> " + $_.Exception.Response.StatusCode.value__) }
  }
  "runtime-test" { & (Join-Path $PSScriptRoot "run-runtime-test.ps1"); Get-Content (Join-Path $PSScriptRoot "runtime-result.log") | Select-String -Pattern "FAIL|PASSED|CHECK_EXIT|burst|DRY_RUN" | ForEach-Object { L $_.Line } }
  "whoami" { $o = W "npx --yes wrangler@4 whoami"; $o | ForEach-Object { L $_ } }
  "deploy-staging" {
    # 1. the fake upstream services
    Push-Location (Join-Path $api "mock")
    $o = W "npx --yes wrangler@4 deploy"; Pop-Location
    L "mock deploy exit=$code"; $o | Select-Object -Last 25 | ForEach-Object { L $_ }
    $mock = ($o | Select-String -Pattern 'https://voltcap-mock-staging\.[a-z0-9-]+\.workers\.dev' -AllMatches | ForEach-Object { $_.Matches.Value } | Select-Object -First 1)
    if (-not $mock) { L "MOCK_URL_NOT_FOUND"; break }
    # 2. the staging Worker, pointed at them (MOCK_UPSTREAM is honoured only with STAGE=staging and that host)
    $o = W "npx --yes wrangler@4 deploy --env staging --var MOCK_UPSTREAM:$mock"
    L "staging deploy exit=$code"; $o | Select-Object -Last 30 | ForEach-Object { L $_ }
    $stg = ($o | Select-String -Pattern 'https://voltcap-api-staging\.[a-z0-9-]+\.workers\.dev' -AllMatches | ForEach-Object { $_.Matches.Value } | Select-Object -First 1)
    if (-not $stg) { L "STAGING_URL_NOT_FOUND"; break }
    # 3. FAKE secrets only (from dev.vars.example; MOCK_UPSTREAM / ALLOW_UNSIGNED_MEDIA are vars, not secrets)
    $sec = @{}
    Get-Content (Join-Path $PSScriptRoot "dev.vars.example") | Where-Object { $_ -match '^([A-Z_]+)=(.*)$' } | ForEach-Object {
      if ($Matches[1] -notin @("MOCK_UPSTREAM", "ALLOW_UNSIGNED_MEDIA")) { $sec[$Matches[1]] = $Matches[2] } }
    $tmp = Join-Path $env:TEMP "voltcap-staging-fake-secrets.json"
    $sec | ConvertTo-Json | Out-File $tmp -Encoding ascii
    $o = W "npx --yes wrangler@4 secret bulk `"$tmp`" --env staging"; Remove-Item $tmp -Force
    L "fake secrets exit=$code"; $o | Select-Object -Last 10 | ForEach-Object { L $_ }
    "$stg`n$mock" | Out-File $urls -Encoding ascii
    L "STAGING_URL=$stg"; L "MOCK_URL=$mock"
  }
  "check-staging" {
    $u = Get-Content $urls
    Start-Sleep -Seconds 5
    try { Invoke-WebRequest -UseBasicParsing -Method POST "$($u[1])/__reset" | Out-Null } catch {}
    $o = cmd /c "node runtime\check.mjs $($u[0]) $($u[1]) 2>&1"; $c = $LASTEXITCODE
    $o | ForEach-Object { L $_ }; L "CHECK_EXIT=$c"
  }
  "delete-staging" {
    $o = W "npx --yes wrangler@4 delete --env staging --force"; L "delete staging exit=$code"; $o | Select-Object -Last 5 | ForEach-Object { L $_ }
    Push-Location (Join-Path $api "mock"); $o = W "npx --yes wrangler@4 delete --force"; Pop-Location
    L "delete mock exit=$code"; $o | Select-Object -Last 5 | ForEach-Object { L $_ }
  }
  default { L "unknown action" }
}
exit 0
