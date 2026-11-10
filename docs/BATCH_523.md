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
**Active** since the owner restarted the Claude desktop app (10 Nov): the PC tool offers `install_production` / `uninstall_qa`;
refusals checked by hand — `launch_app com.shelf.launcher`, `install_build release`, an install on a wireless serial and
`pm clear com.shelf.launcher` are refused. Every 523 install went through `install_build debug` (QA app, debug signer
a48b27ed, SHA-256 compared on the device afterwards; the tool reports the real app unchanged). GT78: the real Voltcap was
updated to 0.6.69 at 20:08 on 10 Nov by **its own updater** (installer = com.shelf.launcher) — not by these tools; the GT78
and the RP Classic now run the same production version, so the mixed-version concern is gone.

## 5. Owner's UX batch (items 1–18) and the later requests

| # | Item | Status | Root cause / change | Evidence |
|---|---|---|---|---|
| 1 | Pickup / return as one object | FIXED (523f) | Coverflow details: the slot's spine and the big case cross-faded (two copies for ~110 ms). Now the slot is empty the moment the case lifts, the case is whole from its first frame and turns from spine-on to its front; the same case goes back. Lite mode keeps its instant swap | `Batch523PickupTest`; recordings km523l2 (GT78, Full), m523f (Classic) |
| 2 | Natural Shelf extraction | FIXED (523f), owner's eye | Game shelf: the spine turned away edge-on while a second picture came round from the other edge (both half visible — a flip), then grew. Now one box: slide up, turn in place at shelf size with spine and front side by side (`shelfBoxFaces`), then come forward | unit test; Classic m523f / GT78 km523f recordings |
| 3 | 318-combination matrix | see §6 | p523f (×1.0) / p523fb (×1.3), RP Classic, build 523f | |
| 4 | Details / More keep the popup's look | FIXED (523f / 523k) | Row opens, Row reveal and Shared opened the generic full-screen details. Now Details shows the whole story and every fact in the window itself (Row reveal: where the short story was; Row opens: under the buttons; Shared: in its page); B / Less goes back with focus on Details. Classic style keeps its full screen by design; the grid's side panel (no room) still opens it | m523f (Classic) + km523l (GT78): open → Less + story → B back on Details → B closed |
| 5 | Storage box popup | FIXED (523d) | the main action alone, an even grid of the rest, Back last, whole words | 523d |
| 6 | Smart Fill | DONE (523b–e) | WWE portrait in Tiles: no grey bars; Hero portrait whole over its soft copy; Spot wide whole | s523before vs s523after |
| 7 | Inspect zoom on extracted media | FIXED (523b) | real multi-touch: disc 2.83×, cartridge 2.73× | ins523.tar |
| 8 | Endless duplicates / oscillation | FIXED (523d / 523f) | each game once with Fill the row off (the owner's earlier "fill" stays the default), fast steps never bounce back; Case flow joins | e523f / ke523f (fixture: 1, 2, 3 and 36 games; fill on / off) |
| 9 | CoverFlow continuous movement | FIXED (523f) | Case flow drew 2 games as 5 cases, 5 as 7 (copies sliding in): now each once, crossing round the loop with a fade, keyed by game | `Batch523CaseFlowTest`; GT78 recordings, 2 / 3 games |
| 10 | Cinema platform icon | FIXED (523d) | | |
| 11 | Save Vault art for games not here | FIXED (523g) | see `docs/qa523/vault_audit.md` | GT78 kv523h / i / j before / after |
| 12 | Icons only / PS2 look | FIXED (523f / 523j) | Icons only = flat icons + console tile; new **PS2 browser** look | kv523j |
| 13 | Smart App Artwork | IMPROVED (523d / 523g) | tones measured once per app (cached); wide / hero cards: icon on the right third with two faint rings; the home header no longer blurs an enlarged icon by 28 dp; apps without a readable icon get a neutral plate. DroidDeck / PXPlay / Moonlight are not installed on a connected handheld (nothing was installed for the test) | |
| 18 | Motion pass | PARTIAL | (a) soft copies decoded tiny instead of RenderEffect blurs behind moving cards and the Hero cover-only backdrop; (b) Coverflow details case tops reuse their path / gradients (~200 allocations a frame removed) — GT78 burst janky 12.8 % → 0.6 %, steps median unchanged (15–16 ms CPU; cause not found); (c) **crash** switching Performance between Automatic and Full / Lite FIXED (523l) | perf514 k523g / h / i; flip523 |
| — | Deleted games stay after a rescan | FIXED (523g) | a playlist (.m3u — Voltcap writes them) or cue / gdi sheet left behind without its discs kept listing the game; an explicit rescan that found nothing kept the old list | `DeletedRoms523Test` (3); the device test was inconclusive (files made over adb are unreadable to the app) |

## 6. Popup gate (318 combinations) — RP Classic

### ×1.0, build 523f (p523f, 22:04–00:07, `popclip.py --expect-build qa-0.6.69-523f` → exit 1)
**PASS 244 · FAIL 52 · NOT TESTED 1 · SKIP 21 (row styles in layouts without a row) · INFRA 0.** Real Voltcap pid unchanged, 0 fatal, settings restored.

| FAIL | Count | Cause | Status |
|---|---|---|---|
| Shared style, every layout with a row | 46 | Details (new in 523f): the focusable story box under the actions was 238×38 px with a one-line story — under the 24 dp (48 px) touch minimum | **FIXED 523o** (box at least 48 dp; `Batch523oTouchTest`); re-graded in the ×1.0 row run of chain523n |
| Crate, all five other styles | 5 | readability "overlap" on the **home** step: the three back sleeves' fallback titles (stand-in games have no covers) lie on each other — all under the front sleeve; the screenshot shows none of them | checker false positive (occluded text; the dump has no z-order) — no product change; noted in tools/qa/defects.json |
| Twin showcase × Row opens | 1 | the home's clock cut by 6 px by its parent on the page step, under the full-screen details | 523k replaced that page for Row opens (details in the window); re-graded in chain523n |
| NOT TESTED: Classic layout × Banner | 1 | page step had no window buttons for the pixel comparison | — |

### ×1.3 / density ×1.15 (p523fb)
Stopped after 12 layouts when ×1.0 ended (the chain ran past its slot); not graded. Superseded by chain523n.

### chain523n (build 523n, 00:13–00:24 11 Nov) → chain523o (build 523o, from 00:25)
523n ran the Smart App Artwork part (a523n: JoiPlay / FolderSync / Obtainium) and the Save Vault looks (v523n), then was stopped
so the gate runs on 523o with the Shared fix: chain523o = ×1.0 Row opens / Row reveal / Shared (p523or), then **×1.3 all 318**
(p523ob). Results below when graded.

