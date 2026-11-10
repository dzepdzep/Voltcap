# Batch 523 (10 Nov 2026, local on next/0.6.70): final badge polish, complete popup regression gate, KONKR QA, release prep

All device work ran in the **isolated QA app** (`com.shelf.launcher.qa`, debug key a48b27ed…; build line
`verifyQaIsolation: Voltcap-0.6.69-debug.apk = com.shelf.launcher.qa, signer a48b27ed5b51f56d... (not the release key)` checked
before each install; installed APK sha256 compared on the device). Real Voltcap: RP Classic 0.6.69 (pid 10087 before / after
every run), GT78 / KONKR 0.6.67 (lastUpdateTime 2026-11-08 17:06:09 before and after; never launched, installed, synced or
upgraded — no mixed-version sync). No system setting changed (font / density only through the QA-only `voltcap_test_scale`).
No push, merge or publish.

QA build: `qa-0.6.69-523a`, APK sha256 66bf42f0f4c8f41e1339be17457e9d8cafaf9201f8fbce73ed46bb9e1858a421 (tools/qa/builds.json).

## 1. Console badges — final polish (owner: some 96 dp logos read smaller / unbalanced)

Measured, not guessed: every badge-set wordmark rendered (rsvg) at 200 px; each logo's **lettering height** (connected shapes,
emblems left out, width-weighted) ÷ the logo's height. Emblem + small words read small at the same pill height:
Atomiswave 0.29, Master System 0.33, GameCube 0.34, N64 0.36, NES 0.38, NGP 0.40, Xbox 0.43 … (all-lettering logos ≈ 1).

| Change | Code |
|---|---|
| Wordmarks with lettering < 0.75 drawn up to 30 % taller **inside the same pill** (√ correction keeps each logo's proportions; never past the pill, never wider than the pill cap). Artwork unchanged | `BadgeLogos.artHeight`, `LETTERING` |
| Logo look: the wordmark only where it fits (90 % rule, 522) **and** its words are ≥ ¾ of the Text look's capitals on that badge; otherwise the compact form (symbol, else name) — big tiles show the whole logo | `BadgeLogos.showsWordmark` |
| One pill height per look: Symbol and Logo looks draw name / compact / ES-DE pills as tall as their artwork pills (they were 13 % lower); Text look unchanged; symbols the same size as before | `ART_PILL`, `ConsoleBadge` |
| ES-DE fallback (consoles without a set wordmark: TG16 / TGCD, NGPC, WSC, C64, Amiga) sized by its measured shape with the same rules (was a fixed 3.4 × pill: TurboGrafx tiny). NGPC's lettering doesn't read even at 200 dp → "NGPC" | `ESDE_FORM`, `esdeForm` |
| ScummVM (outlined lettering fills into a blob) and Steam (emblem becomes a white disc) → name, like 3DO / MSX | `SystemLogos.SILHOUETTE_UNREADABLE` |
| PC stores: Steam named, brand colours Steam / GOG / Epic / Amazon (PC games stay grey), 16th gallery page | `consoleColor`, `ConsoleNames`, `QaBadgeGallery` |

Device (RP Classic, b523 vs b522final, same QA screen): Logo 96 dp lettering height p10 5 → 7 px, median 9 → 10 px,
spread p90/p10 3.2 → 2.5; Text column unchanged (1.5). Regional identity kept (Mega Drive / Famicom / SFC / TG16 / NGPC never
wear another console's mark — re-checked by `badge_audit.py` as regressions: 0). Unit tests: `Batch523Test` (6).
Gallery: `Claude outputs\b523\badge_gallery_523.html` (before 522g / after 523a, 16 pages + real layouts) and the
"Voltcap Badge Audit" artifact (version 3).

### Counts, reconciled (one source: tools/qa/tools/badge_audit.py)
68 consoles + 5 PC store categories (Steam, Epic, GOG, Amazon, PC) = **73 gallery rows** (16 pages). Voltcap names **74
platform keys** = those 73 + `Pico8` (second key for PICO-8). The 522 docs' "69" = 68 consoles + Pico8 (before the store
categories); the 522 gallery header's "73" = 69 + Epic / GOG / Amazon / PC (Steam was not named yet).

### Coverage table: open vs history
`badge_audit.py` now models the three looks of the current code (Text / Symbol / Logo at 96 and 200 dp). Its table
(docs/qa523/badge_coverage.md) has an **Open** column (only what is still true: no artwork → name, compact at 96 dp, ES-DE
logo used, no folder logo, shared SNES / WonderSwan emblem) and a **Regression** column; the fixed 521b–523 defects
("text badge draws a logo", borrowed branding, block logos, grey pills, …) are a separate history table, re-checked each run.
Exit code 1 on any regression.

## 2. Popup final regression gate (RP Classic, build 523a)
(results in §5)

## 3. KONKR (GT78, BW0306N230000845, Android 12, 960×640, 3 GB)

Owner decision (10 Nov): **replace** the old QA app. Removed only `com.shelf.launcher.qa` 0.6.68-qa516a (release-key signed,
its own test data); installed QA build 523a (sha verified on the device). Real Voltcap 0.6.67 unchanged. GT78 added to
tools/adb_mcp/qa_devices.json (USB serial).

First run (konkr523 / konkr523b) was **void**: the QA app restarted (mem520's force-stop) without its stand-in library —
`voltcap_test_fixture_roms` is not saved — so every screen said "No games yet". Fixed in the scripts (`QA_FIX=true` passes the
hook on every start; default off, the Classic runs are unchanged) and re-run as konkr523c with a library check screenshot first.

| Test (konkr523c, 25 stand-in games) | Result |
|---|---|
| Memory soak `mem520.sh 3` (10 layouts × 20 moves + window per round) | PSS 161 → 428 → 440 → 440 MB, idle 60 s 374, after critical trim 373. Native 32 → 206 → 213 → 221 → 185; graphics 23 → 100 → 107 → 99 → 69. **Plateau after round 1** (round 3 +0 MB), 66 MB released at idle: cache, not a growing leak. Higher than the Classic (108 → 200) — matches the earlier KONKR note (183 → 433); MediaTek driver / texture caching still the likely cause. 0 fatal, 0 launches |
| Case Stacks scroll (perf514) | steps p90 19 ms / 3.6 % janky; **burst p90 26 ms / 3.2 % janky** (494: p90 36 ms / 10.4 %); window 18 ms / 5.6 % |
| Game shelf scroll (perf514) | steps 13 ms / 0 %, burst 9 ms / 0 %, window 8 ms / 0.65 % |
| Controller contract (ctl516) on GRID HERO_TILES CINEMA CASEFLOW BOX STORAGE FILESELECT SHELF TILES STACKS CHAN | **55 / 55**: R1, L1, Select, Start opened and B returned focus; R2 / L2 kept the home focus |
| Game shelf pulled case, wide / portrait art (q520b) | 18 screenshots, settings restored |

## 4. Install guard (PC tool)
**Still not active after the restart.** The running `voltcap-adb` server is the pre-521 one: its tools still offer
`install_build` with release / compat, `launch_app` defaulting to `com.shelf.launcher`, and no `install_production` /
`uninstall_qa`. The PC's `tools\adb_mcp\server.js` IS the new one (sha1 827b34aa…, same as the branch; guard self-test
93 checks OK). So the desktop app either was not fully restarted after 11:57 or its MCP config points at another copy of
server.js. Owner step: Claude desktop › Settings › Developer › Edit config: `voltcap-adb` → `…\ShelfLauncher\tools\adb_mcp\server.js`;
quit from the tray, start again. Until then every install this batch was checked by hand (isolation build line, package,
signer, sha256 on the device).
