# Batch 518: runs the Worker in Cloudflare's own local runtime (wrangler dev -> workerd, Durable Objects, Cache API,
# Rate Limiting bindings, all local) against runtime/mock-upstream.mjs with the FAKE values from dev.vars.example, then
# runtime/check.mjs. Nothing is deployed and no Cloudflare login is used. Result: runtime/runtime-result.log
$ErrorActionPreference = "Continue"
$api = Split-Path -Parent $PSScriptRoot
Set-Location $api
$log = Join-Path $PSScriptRoot "runtime-result.log"
function L($m) { Write-Host $m; $m | Out-File $log -Append -Encoding utf8 }
"=== $(Get-Date -Format o)" | Out-File $log -Encoding utf8
$env:WRANGLER_SEND_METRICS = "false"; $env:CI = "1"; $env:NO_COLOR = "1"
if (-not (Get-Command node -ErrorAction SilentlyContinue)) { L "NODE_MISSING (install Node.js LTS from nodejs.org, then run again)"; exit 0 }
L ("node " + (node --version) + "  npm " + (cmd /c "npm --version"))
# Anything left from an earlier run (wrangler hot-reloads and keeps its local state) is stopped, and the local state
# (Durable Objects, Cache) starts empty.
function Stop-Port($port) { Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | ForEach-Object { cmd /c "taskkill /PID $($_.OwningProcess) /T /F" | Out-Null } }
Stop-Port 8787; Stop-Port 8788
Get-Process workerd -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Remove-Item (Join-Path $api ".wrangler\state") -Recurse -Force -ErrorAction SilentlyContinue
Copy-Item (Join-Path $PSScriptRoot "dev.vars.example") (Join-Path $api ".dev.vars") -Force
$mock = Start-Process node -ArgumentList "runtime/mock-upstream.mjs" -PassThru -WindowStyle Hidden -RedirectStandardOutput "runtime\mock.out" -RedirectStandardError "runtime\mock.err"
$wr = Start-Process cmd.exe -ArgumentList "/c", "npx --yes wrangler@4 dev --ip 127.0.0.1 --port 8787 --show-interactive-dev-session=false > runtime\wrangler.out 2>&1" -PassThru -WindowStyle Hidden
$up = $false
for ($i = 0; $i -lt 150; $i++) {
  try { $r = Invoke-WebRequest -UseBasicParsing "http://127.0.0.1:8787/v1/health" -TimeoutSec 3; if ($r.StatusCode -eq 200) { $up = $true; break } } catch {}
  Start-Sleep -Seconds 2
}
L "WRANGLER_UP=$up"
if ($up) {
  $out = cmd /c "node runtime\check.mjs http://127.0.0.1:8787 2>&1"
  $code = $LASTEXITCODE
  $out | ForEach-Object { L $_ }
  L "CHECK_EXIT=$code"
}
cmd /c "taskkill /PID $($wr.Id) /T /F" | Out-Null
cmd /c "taskkill /PID $($mock.Id) /T /F" | Out-Null
Stop-Port 8787; Stop-Port 8788
Get-Process workerd -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
L ("STILL_LISTENING_8787=" + [bool](Get-NetTCPConnection -LocalPort 8787 -State Listen -ErrorAction SilentlyContinue))
Remove-Item (Join-Path $api ".dev.vars") -Force -ErrorAction SilentlyContinue
# Config and bundle check for production and staging, without deploying or logging in.
foreach ($envName in @("", "staging")) {
  $args2 = if ($envName) { "--env $envName" } else { "" }
  $o = cmd /c "npx --yes wrangler@4 deploy --dry-run $args2 2>&1"
  L ("DRY_RUN[" + ($(if ($envName) { $envName } else { "production" })) + "] exit=" + $LASTEXITCODE)
  $o | Select-String -Pattern "Total Upload|gzip|error|warn|binding|Durable|Rate Limit|dry-run" | ForEach-Object { L ("  " + $_.Line) }
}
L "--- wrangler output (last 40 lines)"
if (Test-Path "runtime\wrangler.out") { Get-Content "runtime\wrangler.out" -Tail 40 | ForEach-Object { L $_ } }
exit 0
