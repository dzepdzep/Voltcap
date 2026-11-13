# VoltCap request coverage matrix (13 Nov 2026, update 529c)

One row per request I could trace. Columns: **Requested** (what you asked) · **Impl** (implemented?) · **Tested / visual** (PC tests,
hardware, visual review) · **Released** (in a published release?) · **Remaining** · **Next action**.
Status words: DONE (with evidence) · IMPL (code exists, not fully verified) · PARTIAL · OPEN · BLOCKED-HW / -REF / -OWNER · DEFER.
Nothing below is marked DONE without the evidence named in its row or in the source document it cites.

## How complete this is (read first)

| Source | Read? |
|---|---|
| This session's requests (529: save sync P0 + mid-game, achievements compact, ••• last, Moonlight motion, 529b approval, 529c "finish everything") | Yes, fully |
| `docs/MASTER_BACKLOG.md` (repo, reconciled 11 Nov against REQUESTS_CHECKLIST, ROADMAP_070, CARTS_468/469, E1E2 refs, batches 505–523) | Yes, sections 0–7 |
| `tools/qa/defects.json` (309 entries) | Yes: every entry not marked verified / closed / wontfix / deliberate (≈ 95) was listed and triaged below |
| Sprint reports 525–528, RELEASE_0.6.71, SAVE_SYNC_529, UPDATE_529B (project) | Yes, their open / remaining sections |
| REQUESTS_CHECKLIST (updates 453–466) | Yes; its open rows are already carried by MASTER_BACKLOG / defects.json (checked) |
| LEFT_TO_DO (20 Oct), ROADMAP_STATUS (19 Oct), NEXT_OVERHAUL (4 Nov), UPDATE_454_PENDING | **Re-read in full 13 Nov (529c continued).** MASTER_BACKLOG had NOT carried most of their open items: they are now section F below. |
| ROADMAP_070, POPUP_BATCH_505–516, QA_PHASE*, CARTS_468/469, older audits | Not re-read line by line. MASTER_BACKLOG cites them as reconciled 8–11 Nov; spot checks only. **GAP (smaller).** |
| `SUMMARY.md` (2 MB, ~1,000 update entries), `COWORK_HANDOFF.md`, `CURRENT_TASK.md` | Top entries only. **GAP:** a request mentioned only inside an old SUMMARY entry and never carried into the backlog or defects would be missed here |
| Earlier chat conversations (before this session) | **Not accessible** from here: only what they wrote into the repo / project docs |
| GitHub issues | **Not accessible** (this session has no GitHub API access to dzepdzep/Voltcap; `gh` → 403). Branches were audited from the local clone |

## A. Data safety (highest priority)

| # | Requested | Impl | Tested / visual | Released | Remaining | Next action |
|---|---|---|---|---|---|---|
| A1 | Save sync P0: never lose progress between handhelds (queues, change detection, verified backups, strict upload check, twin lineage, multi-file all-or-none, Play waits for Drive) | DONE in code (`fix/save-sync`) | PC: full `testDebugUnitTest` green (13 Nov 10:56); JVM two/three-handheld simulation 18 scenarios + 25 random runs; mutation-checked | No | Real Drive, two handhelds | BLOCKED-OWNER: register the QA app's OAuth client + sign it in (SAVE_SYNC_529 §8), then the hardware run |
| A2 | Mid-game: detect saves already on disk, upload verified, several per game, never torn, survive process death | DONE in code | Flip 2 dry run (QA app, synthetic): 8.2 s / 3 saves → last only / 8 MB card never half / 30.5 s after process death | No | Real Drive; KONKR RetroArch autosave setting unknown | BLOCKED-HW (KONKR) + A1's owner step |
| A3 | Never overwrite an active emulator's save with Drive's version | DONE (HELD while a game runs; EmulatorGuard after) | Unit + simulation | No | as A1 | as A1 |
| A4 | Simultaneous uploads (last moment) | IMPL 529c (revision list, park + pin + announce) | JVM: 5 rule tests + 3-handheld scenario (fails without fix) | No | **Not compiled for Android yet** (PC offline) | PC build + tests; then A1 hardware |
| A5 | Multi-file saves consistent on Drive | IMPL 529c (manifest last, hold, 24 h → player) | JVM: rule tests + scenario (fails without the gate); random runs green | No | Not compiled | as A4 |
| A6 | Conflicts must not rely on unreliable timestamps | IMPL 529c (Google-clock correction, no guess when close) | JVM: 2 tests (old rule picks wrong) | No | Not compiled; dialog text not seen on screen | as A4 + one screenshot of the conflict dialog |
| A7 | Recover / restore previous save versions | IMPL 529c (Save sync › Earlier versions › Restore, verified, current kept first) | JVM: 4 restore tests | No | Not compiled; UI not seen | as A4 + Flip 2 QA run with synthetic backups |
| A8 | Pending uploads survive process death and restarts | DONE (WorkManager per-game queues; watcher revival) + IMPL 529c (session from before a restart) | Flip 2 process-kill run; JVM boot test | No | Restart itself not run on hardware (never reboot your handhelds) | Leave as unit-proven; note in release |
| A9 | Never rename save files; regions / hacks separate | DONE | Simulation scenario "similar names stay separate" | No (`fix/save-sync`) | — | — |
| A10 | Syncthing markers on GT78 (two sync engines on one folder) | Finding only | Read-only inspection | — | Your decision | BLOCKED-OWNER: check KONKR for Syncthing |
| A11 | Production backups P2–P7, real install B2–B6 (DATA_BACKUP_513, REAL_INSTALL_510) | PARTIAL (P2 done 520) | — | — | Each step needs your approval | BLOCKED-OWNER |

## B. This session's UI requests (529b, approved provisionally 13 Nov)

| # | Requested | Impl | Tested / visual | Released | Remaining | Next action |
|---|---|---|---|---|---|---|
| B1 | ••• is the last option in every popup | DONE (`feature/popup-native` c819139a, fd7bcc52) | Flip 2 popclip: 10 layouts More shut, 6 More open, 4 on final build - all PASS (after the 529c gate fixes); you approved | No | KONKR not run; ~40 other layouts verified by code path only (shared `sceneKeys` / SceneBody) | KONKR popclip sweep when connected |
| B2 | Save status text on its own line under the buttons | DONE (your decision; fd7bcc52 keeps ••• after it) | Flip 2 mlast529c | No | — | — |
| B3 | Moonlight (Night menu) Persona-3-inspired motion | DONE on Flip 2 | Flip 2 before/after videos, frame times equal or better; you kept it | No | KONKR; reduced-motion run on hardware | BLOCKED-HW (KONKR) |
| B4 | Compact Achievements (folded sections, A opens, B closes inner first, comments on request, Stats panel) | DONE on Flip 2 | Flip 2 ach_after4: 1/10/120/none, ×1.3, page + list, fast moves; B order fixed and re-checked; you approved | No | KONKR ×1.0 / ×1.3; **Steam page** (no Steam account in the QA app); "before" list shots impossible | BLOCKED-HW (KONKR), BLOCKED-OWNER (Steam data on a QA device) |
| B5 | Storage INFO/PLAY hierarchy finding | Triaged: checker false positive (page tabs = approved Storage record layout) | Gate fixed 529c (`page_tabs`), selftest with the real Flip 2 dump; mlast529c → PASS | — | — | DONE (tools) |
| B6 | Spotlight SELECT centring finding | Triaged: checker false positive (see-through pill's edge under T) | `glyphcheck` sharp-step edge 529c, selftest with the real screenshot + a moved-glyph negative; 529 runs re-graded PASS | — | Pocket ribbon START 3 px (MASTER_BACKLOG) not re-checked | Re-grade the Pocket ribbon dump with the new checker on the next Flip 2 sweep |
| B7 | Controller focus, B, rapid presses, reduced motion on affected popups | PARTIAL | B order + fast moves on Flip 2 (achievements); popclip rapid / shoulders steps PASS on 10 layouts | No | Reduced motion (a system setting - I may not change it) | BLOCKED-OWNER: turn on "Remove animations" yourself for one QA run, or accept unit-level `MotionLevel` coverage |

## C. Release / integration

| # | Requested | Status | Next action |
|---|---|---|---|
| C1 | Save-sync work must not exist only locally | Bundles: PC `Claude outputs\backups\save_sync_529_from_main.bundle`, cloud outputs, **and the project** (`claude/backups/fix_save_sync_bundle_b64.txt`, round-trip verified). Not on GitHub: this session can't push, and the PC push tool allows only pnat / s3d / ci | Add `fix/save-sync` (and `integration/*`) to the push tool's allow-list on the PC (tooling, backup first), or push it yourself |
| C2 | Integration branch, conflicts resolved properly, experiments excluded | `integration/0.6.72` f52f21e9 (cloud): popup-native 98c7b86d + fix/save-sync (incl. blur hotfix); TestHooks / SUMMARY conflicts kept both sides; Connected Ignition and 3D Shelf excluded | PC build + tests + CI, then hardware |
| C3 | Release only when every gate passes (incl. real Drive E2E) | Not released | Blocked by A1 owner step and the PC build |

## D. MASTER_BACKLOG items still open (11 Nov reconciliation, re-checked 13 Nov)

| # | Requested / found | Impl | Tested / visual | Released | Remaining / next action |
|---|---|---|---|---|---|
| D1 | Custom spine flicker (Original Shelf) | IMPROVED 525 | Flip 2 fixture walks: 0 flashes | 0.6.71 | Your library / video scenario - BLOCKED-OWNER (your eyes) |
| D2 | Cold / delayed artwork flashes | IMPROVED 525 | warm 0 flashes; cold run only first 2 s (recorder) | 0.6.71 | Full-length cold run with `rec.sh REC_SEG` (Flip 2) |
| D3 | Shared Shelf slot-jump / extraction + return polish | FIXED on PC 525 | Flip 2 slow-step frames | 0.6.71 | Your visual approval |
| D4 | 3D Shelf (experimental) first-pull frame spike (29 ms) | OPEN | exp 3567df4 slow-mo video | No (off) | Investigate first-pull warm-up on `exp/shelf-3d-flip`; your review of the video |
| D5 | Blur: smooth, no pixelation | IMPL (`hotfix/0.6.72-blur`: native-resolution captures, lowResBlur kept) | BlurHotfix529Test; Flip 2 blur529 runs (earlier this session) | No (in integration/0.6.72) | Visual sign-off on KONKR |
| D6 | Save Vault backup / restore usability | A7 adds restore of earlier versions (Save sync) | JVM | No | Hardware + visual |
| D7 | Artwork / console model inconsistencies (PS5 / Xbox One spine logos, missing CD logos FDS / SG-1000 / 5200 / Xbox One / Series / Switch 2 / PS5 / CD-i, E1/E2 cartridge refs, GBC MBC1/5, D185, D195, D196, D218) | — | — | — | BLOCKED-REF (authentic references needed, rule R25 forbids guessing) |
| D8 | Connected Ignition QA and integration | HARDWARE FUNCTIONAL (525/527/528) | Flip 2 + KONKR | No (by your decision, not merged) | Your visual approval + your merge decision; reduced motion, several profiles |
| D9 | Thor dual-screen (FW1 welcome, D2 lifecycle, Ribbon two-screen, D270 duplicate title) | IMPL (FW1 built) | — | No | BLOCKED-HW (Thor not on USB) |
| D10 | Performance: Hero GPU on GT78 (open low), D61 Case stacks / Arcade render-bound, D24 cold start 830–960 ms, D64 RP Classic idle clip, D165 / D172 Settings frames on RP Classic, D12 / D13 spikes (suspect) | OPEN | measured earlier | — | Hero / D61 / D24 need device profiles (Flip 2 / KONKR); RP Classic items BLOCKED-HW (away) |
| D11 | Controller regressions: D207 Twin first touch on Play (partly) | PARTIAL | — | — | Re-test on Flip 2 |
| D12 | Classic details on Hub / Resume: achievements squeezed with 10 (Flip 2) | OPEN low | popclip FAIL earlier | — | Check after 529b (achievement page changed) on the next Flip 2 sweep |
| D13 | Airy: no-art title white on white (D298) | IMPL 529c (dark ink) | Not built | No | PC build + one Flip 2 Airy screenshot |
| D14 | KONKR memory growth | EXPLAINED (cache plateau) | GT78 soak | — | Thor / Odin comparison - BLOCKED-HW |
| D15 | ScreenScraper slow / refusing answers | PARTIAL (notes) | unit | 0.6.70+ | Can't force a slow production service - leave |
| D16 | K1 game spacing in ~35 more layouts | PARTIAL | — | — | Each needs its own design; DEFER unless you want it |
| D17 | Odin 3 nav bar, in-game overlays (HUD, perf, music), AutoTDP D113–D119 / D124, RetroAchievements real account, GameNative / DroidDeck real launches | IMPL (most) | unit | partly | BLOCKED-HW / BLOCKED-OWNER (real game sessions, accounts) |
| D18 | DONE-O owner checks: Inspect pinch / pan, K2 chips above screensaver, K4 spine flicker, case tops / Stacks depth, title-slot memory, header cross-fade, flat Game shelf strips, Game shelf extraction motion, TV room "IRON TIDE" watermark, Endless Fill offered where it does nothing, L1 / R1 in shared popups | IMPL | — | mostly 0.6.70/71 | BLOCKED-OWNER (your look / decision) |
| D19 | Sprint 528 leftovers: System Vault spine labels slide on KONKR; Wall's bottom legend over the slices; Settings / R1 overlap sweep; KONKR video A/B; flaky EndlessOnceRoboTest PANELS n=5 | OPEN | — | — | Flaky test: try a synchronous image loader (PC); the rest need KONKR / Flip 2 |
| D20 | QA tooling: D138 focuscheck linear indices, D297 readcheck occlusion, screenrecord stops after ~2 s (root cause), D73 settings explorer coverage | OPEN (tools) | — | — | Tools-only work, PC / device |

## E. defects.json entries not yet verified (triage)

- **fixed / fixed-compiles / fixed-built but never marked verified (≈ 80, D3–D294):** most are covered by later hardware gates (popup gates 461–523z, controller contract 520–523) that verified the same paths, but the entries themselves were never updated. **Next action:** a ledger pass that links each to the gate that covers it, or re-tests it. Not done this session (no device / PC).
- **fixed-unverified (AutoTDP D113–D119, D124; D86 Cardboard box rapid A/B; D107 popup audit notes; D292):** need real game sessions (BLOCKED-OWNER) or a device run (D86, D107).
- **suspect (D12, D13, D91, D96, D134, D168, D169, D297):** need a reproduction on hardware.
- **open:** D24, D60 (Thor accessibility service), D61, D64, D67, D73, D109 (Cardboard Box cold category empty box), D138, D165, D172, D185, D196, D218 - see D10 / D7 / D20.
- **partly:** D207 (D11), D270 / D271 (Thor / long names in about a dozen window scenes).

## F. Older requests that MASTER_BACKLOG had dropped (re-read 13 Nov from LEFT_TO_DO, ROADMAP_STATUS, NEXT_OVERHAUL)

Status from the code and SUMMARY as they are now; nothing here was hardware-tested this session.

| # | Requested (where) | Done so far | Status | Next action |
|---|---|---|---|---|
| F1 | #99 RP Classic overheats while charging (19–20 Oct) | Read-only charging log (271). The fix ("Cool charging": lower current while hot) was never written (no code) | **BLOCKED-OWNER** (changes charging) | Your yes/no on a charging change; then RP Classic on USB to read the log |
| F2 | #101 Pokémon Silver older-save mystery | Every copy compared, nothing lost (Oct) | **BLOCKED-OWNER** | Only Drive's "Manage versions" of the Silver `.srm` (17 Oct) is left; needs your Google sign-in in a browser. Read-only |
| F3 | #102 launch every console | Thor, RP Classic done; Sega CD works | **BLOCKED-OWNER** | Switch: your OK + Eden or Citron. PS3 / RP Classic PS2: only games with real saves - not started (save rule) |
| F4 | #104 stability matrix: two-device save conflict, Bluetooth reconnect, Doze | Two-device conflict is now the real-Drive QA (A-section, `QaDriveTest`) | Two-device: waits for the OAuth step. Bluetooth reconnect, Doze: **NOT TESTED**, never scheduled | Added to the hardware plan (Flip 2 / KONKR, QA app only, no system setting changed: Doze via `dumpsys deviceidle` force-idle is a system change → needs your OK) |
| F5 | #68 first sync on a new device | Fresh install checked on the PC emulator (259) | **NOT TESTED** on real Drive | Part of the real-Drive QA (spare account, synthetic saves) |
| F6 | #118 iiDB artwork | - | **BLOCKED** (no public API) | none |
| F7 | #120 every popup / control on Odin 2 Portal; questions 45–47 | Superseded by the popclip gate (R26) on Flip 2 / KONKR / RP Classic / GT78 (505–529) | Odin 2 Portal: **NOT TESTED** (not connected since Oct). `QUESTIONS_FOR_TONI.md` is not in the repo any more: Q45–47 text lost | Odin 2 on USB when convenient |
| F8 | Optical-system pack: `packs\v1\disc_playstation_3.webp` is 0 bytes | App treats an empty picture as missing (PS3 uses the cartridge pack) | **OPEN-OWNER**, not re-checked since 20 Oct | Re-upload that pack file (it's your asset; not in the repo by design) |
| F9 | Real cartridge labels: Thor label audit per platform (GB…Switch), fallbacks, per-label metadata, gallery, report (20 Oct, A3) | ScreenScraper support-texture printed; support-2D path and a label folder exist in code; MobyGames dropped (your decision) | **PARTIAL**: the per-platform "which games lack a label" count on the Thor was never reported | Thor on USB: `voltcap_test_labelaudit all` (QA app), then a counts table |
| F10 | Custom cases "turned" side view (your check on 0.6.20) | - | **OWNER** (look check) | Say if it still looks wrong |
| F11 | Thor ANR while inspecting (0.6.21) | 285 precaution; 492: the ANR screens seen in QA came from key presses while the Thor slept | **CLOSED-UNVERIFIED** (your exported crash report never came in) | Only if it happens again: crash screen > Export crash report |
| F12 | Folder icon pack (Folders view logos: Android robot, "OTHER APPS") | Second-screen dock got a one-colour icon pack; the Folders view did not | **OPEN-OWNER** (style to agree) | Your pick of style; then implement |
| F13 | Layout Studio ("we gotta work on that layout creator") | Layout Studio's own window (506); PC / phone Layout Maker page | **PARTIAL** - scope never agreed | Name what the creator must do next |
| F14 | Q3: RetroArch video driver → GL on the RP Classic | Decided 20 Oct | **NOT DONE** on purpose: it changes your real emulator configuration | Needs your explicit go for that one change (config backed up first, reversible) |
| F15 | Design questions Q12–Q14 (SteamGridDB vs box scan, discs in wide capsules, plain case PS3/PS4/PS5/Xbox/Neo Geo CD), Q17 / Q18 / Q21 (Palette Ribbon, TwinDex screens), Q19 (Save Vault colours), Q28 (three RP Classic spots), Q31 (3DS 60 Hz profile), Q32 (Azahar touch buttons), Q33 (try Shared move) | Shared move shipped (Glance) | **OWNER** (unanswered as far as the repo shows) | Answer any that still matter; the rest close as "no longer relevant" |
| F16 | Performance: Game shelf first-visit hitch, Pocket (~20 % late) / Green Core (~11 %), Twin icons animated covers | Game shelf re-measured on the RP Classic in 514: 98–100 % on time, no change needed | Pocket / Green Core / Twin icons: **NOT RE-MEASURED** since Oct | frames.py run on the Flip 2 (QA app) |
| F17 | Clean-up of ~15 settings nothing reads | Inventory + classification 12 Nov (`SETTINGS_CLASSIFICATION.md`): obsolete keys kept on purpose (a saved value is ignored; removing them risks restores) | **DONE** (by design: keep keys) | none |
| F18 | Ideas: Recently added category; Time-to-beat sort; random game; per-game L2 / R2 mode and Mode / Fan; "Fixes for this handheld"; per-game notes | Recently added (`NEWLY_ADDED`), "How long to beat" sort (`TIME_TO_BEAT`), random game (platform screen) exist | Per-game L2/R2 + Mode/Fan, handheld fixes, per-game notes: **NOT DONE** (ideas, never picked) | Your pick |
| F19 | NEXT_OVERHAUL V1–V8 (471 review) | V1 crop → Smart Fill (523) + QA image-fit check (474); V6 "…" → rule-11 work; V7 documented 24 dp limit; V8 text-cut / zero-height checks (474, D244–D246 verified) | V2 Case flow short bars, V3 Box Manual alone at 280 dpi, V4 "0 / 96" wraps, V5 Bubbles logo gap: **NOT RE-CHECKED** since 4 Nov | KONKR (280 dpi) popclip pass on those four |
| F20 | NEXT_OVERHAUL R1–R8 (carried requests) | R2 image fitting DONE (523 Smart Fill); R7 GameNative `.steam` DONE-O (522); R1 category logos / looks largely done (527 / 528; Sega CD blocked); R3 row stability (Endless 523) | R4 dual-screen lifecycle **BLOCKED-HW** (Thor); R5 independent brightness per screen **NOT DONE** (only the second-screen dim); R6 performance-reading accuracy **NOT RE-CHECKED**; R8 per-layout colours **PARTIAL** | R5 / R8: your go on scope |
| F21 | Rule 11, no "…" anywhere (standing) | 585 places (Oct) → 281 (13 Nov) → **259** after 529c's save screens (22 places) | **OPEN**, tracked by static TRUNCATE | Next by size: SettingsDialog 17, GameDetailsOverlay 13, GameWindowBanner 12 - each needs a popclip / visual pass |

## Gaps I can't close from here

1. Older history: LEFT_TO_DO / ROADMAP_STATUS / NEXT_OVERHAUL now re-read (section F). Still only spot-checked: ROADMAP_070, POPUP_BATCH_505–516, QA_PHASE*, CARTS_468/469, and requests that live only inside old SUMMARY entries. If you remember one that isn't here, name it.
2. GitHub issues: not readable in this session.
3. Everything marked "not compiled" waits for the PC link (the remote-devices bridge failed to connect this session).
