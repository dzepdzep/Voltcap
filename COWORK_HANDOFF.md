# COWORK_HANDOFF — resume here (updated 28 Oct 2026, 0.6.54 built and CI-green but NOT yet published)

**UPDATE 405 (29 Oct 2026, NOT released, newest):** Box side faces now a thin attached rim (BOX_SIDE_KEEP, D97); Cover details flight = one ramp / ease / lift arc, slot empty while its case is out (D98); Thor sheet qa_reference_update405/sheet2.png. OPEN: Game shelf one-curve + side-slab audit of Shelf / Stacks, rapid reversal / Lite / touch re-run, 30 fps capture. v0.6.58 still latest. Details SUMMARY 405.

**UPDATE 404 (29 Oct 2026, release v0.6.58) - read first; the 1 Nov Cowork session needs no chat history:** (1) Code: `ui/StyleCoverDetails.kt` `CoverFlight` / `CoverBigCases` (D95: case flies slot -> big place, old one back; immediate on category change / animations off / Lite). Test `CoverFlight404Test`. (2) Facts: the ten category looks of 350-352 EXIST (`PlatformBrowser.kt` + `CategoryLooks.kt`); System Grid / Side Banner / Retro Timeline / Compact List do NOT exist; do not rebuild the ten. Full status matrix: `qa_reference_update403/Update404_StatusMatrix.md`. (3) Thor evidence in `.shots/404/` (local). Test intents need the hooks file: `adb -s 3140972f shell "cd /data/local/tmp/vqa && sh vqa.sh hooks on"` (restarts the app; press B on the profile picker), then `cat_layout_default=<LAYOUT>` pref, preview intent, category ALL; hooks off afterwards; put `cat_layout_default` back to BOX and `box_rows` to AUTO. (4) OPEN, in order: D91 PS2 Yu-Gi-Oh, N64 raw scan (saved_art is hash-named and huge: pull one file with the URL from a log, not a tar), console fit audit + Switch cartridge, Cover details reference parity, Box 1-3 games / empty, Lite + touch on the flight, idle wait before the pickup flight, D96, then the 403 backlog table. (5) PUBLISHED: v0.6.58 (normal, public, not a draft; tag on f9567b8 = update 404 commit, which also pushed the local 401-403 commits; CI 'QA tooling checks' green; signed APK 53,028,184 bytes: https://github.com/dzepdzep/Voltcap/releases/download/v0.6.58/Voltcap-0.6.58-release.apk). Update 403 = 81c2da9.

**UPDATE 403 (29 Oct 2026, no release) - read first:** see SUMMARY.md top. Done and Thor-checked: Box slabs / black sides removed (`StyleCardboardBox.kt`: per-case sheen + contact shadow, `boxSideTone`), veil = flight progress (`GameWindow.kt` `flightVeil`), Cover details yaw / pitch / `bigCaseFill` (spine + top, no clip), tests `Physical403Test` + `CoverDetails403Test`, defects D92-D95, coverage BOX / COVERDETAILS. OPEN: continuous featured-case curve in Shelf / Stacks / Cover details (D95), Box 1 / 3 rows + 1-3 games + empty, N64 raw scan + other platforms' fit, PS2 Yu-Gi-Oh (D91), literal D A B A B A B (needs a no-Play guard), backlog table in qa_reference_update403/Update403_Backlog.md. Layout preview via intent needs two sends 7 s apart; profile picker after an install: B.

**UPDATE 402 (29 Oct 2026, no release) - read first:** see SUMMARY.md top. Done: Box pickup one movement (`BoxNav.startYaw`, no in-box rise, 680 ms flight, card back at 6 %), Box selection block removed, `fillCropOrFit` / `CropUnlessMuchCut` (portrait cover on landscape face shown whole), `Case402Test`, defects D88-D91. OPEN: true 3D Cover details, PS2 doubled band is in the source picture (D91), D90 on other consoles, D86 original sequence, Cover details / Box closing frames. v0.6.57 is still the latest release.

**UPDATE 401 (29 Oct 2026, no release) - read first:** see SUMMARY.md top. Done: Box shading / selection cleanup, Pocket ribbon top screen single centred cartridge + directional slide + no Endless, D86 fix (fixed-unverified), tests. OPEN: stretched artwork (cause not found), Cover details 3D, N64 / Switch mapping, visual regression tests, universal pickup audit. v0.6.57 is still the latest release.

**UPDATE 400 (29 Oct 2026, v0.6.57) — read first:** see SUMMARY.md top. Done and Thor-verified: pickup transitions (GameWindow `fullInspect` no longer tied to Info animation; `PICKUP_CARD_LAYOUTS` cardFlight; Case3D `onFront` gate), Cardboard box Rows Auto / 1 / 2 / 3 (`ui/BoxRows.kt`, tests `BoxRows400Test`), Cover details yaw / pitch per Spine depth. OPEN: D86 rapid re-open duplicate, Artwork library Endless / rail / touch, true 3D geometry vs `reference-cover-details.png` (kept local, not committed), N64 spine / back + Switch cart mapping, per-console sizes, 3-row small-category / portrait / second-screen checks, Pocket Quest FPS, Disc Station labels, new category looks. Thor left on 0.6.57 with its settings restored; `.shots/400/` holds the evidence (local, not committed).

**STANDING POLICY (28 Oct 2026, supersedes older notes below):** the owner authorized ordinary QA on the AYN Thor (ADB serial 3140972f) for current and future tasks, no separate permission needed: bounded ADB, APK install over the existing app, controller / touch tests, screenshots, short recordings, temporary layout / pref changes restored afterwards. Limits: 15 min physical QA per task; never clear data / uninstall; no charge-limit changes; preserve games, saves, profiles, artwork; restore volume, DND, display; stop all test processes and recorders; NEVER touch any other device (Google TV Streamer, Odin 2 Portal). Older "physical QA only with separate explicit permission" notes (section 2d, SUMMARY 369 / 379) are historical. Full text: CLAUDE.md.

**WORKFLOW RULE:** large master prompts have been truncated. Work in shorter, complete stages; read this handoff and continue unfinished tasks; never assume missing prompt sections are done (SUMMARY 379: sections 2, 4-9 and part of 1 of the second-screen master polish are NOT done, section 10 arrived cut off); avoid repeating successful QA (378: Only-first-wide and R1 D-pad Apply PASS).

**UPDATE 394 (29 Oct 2026, newest):** category change = no visual reload (CLAUDE.md rule 14, SUMMARY 394). Done: shared fix (`CategorySlide.justChanged`, shells without the slide, category-keyed fades removed), Thor prefs static, QA (`device/catswitch.sh`, `tools/catswitch.py`, CATSHELL, `CategoryStable394Test`, coverage CS-01..03). **OPEN for Cowork:** catswitch on the 48 untested layouts (batches of 5-8; the script gives each its own `cat_layout_default`), empty categories, mixed systems, missing artwork, per-category assigned layouts (two different layouts), dual screen / companion, touch tabs; a frame-exact check of the step itself (the tool judges blank / dip frames only). Trap found: with `cat_layout_default` set (the Thor has Artwork library) a layout preview is replaced on the first category step, so a run that looks green may show another layout (the tool now returns INFRA for equal stills).

**UNFINISHED PRIORITIES (in order; set 28 Oct 2026; rules in CLAUDE.md; next task in CURRENT_TASK.md):**
**UPDATE 399 (29 Oct 2026, newest; handoff for 1 Nov):** v0.6.56 released (updates 393-398; build + unit tests + Thor smoke OK). Category L2/R2 vs screen audited, shared CategoryStops, tested, no defect found. Large visual items (true 3D boxes, Artwork library hardware checks, Pocket Quest FPS, Disc labels, TWIN_*/HERO_TILES, N64/Switch art mapping) NOT started/verified: list in SUMMARY 399. Emulator AVD needs ANDROID_AVD_HOME=C:\Users\User\.android\avd and was unstable. Reference image missing from repo.
**UPDATE 398 (29 Oct 2026, newest):** see SUMMARY.md top; D-pocket and Disc label still OPEN until frame-verified; Artwork library, Box, TWIN_* / HERO_TILES hardware checks NOT TESTED.
**UPDATE 397 (29 Oct 2026, newest):** see SUMMARY.md top (setFocus ends the category window; catswitch Pocket / Disc Station batch; open D-pocket; Artwork library / Box hardware checks still NOT TESTED).
**UPDATE 396 (29 Oct 2026, newest):** see SUMMARY.md top. Open: category-switch on the remaining layouts, update 395 hardware leftovers (Artwork library Endless ON, Box Neat / Messy), rail closing frames. Run `sh tools/qa/tools/pushqa.sh` before using device scripts (CRLF).
1. ~~**Endless navigation**~~ DONE in update 381 (SUMMARY.md): Dashboard's ◀ on the first game went into the menu instead of round to the last card the row draws left of it; fixed, Thor-verified. Not re-verified: other looping layouts on hardware, 9-12 game categories.
2b. **FIXED (update 387, D84):** the screensaver timer ignored the profile selector (`whoAsk` was not in the idle guard); `ScreensaverGate` + full restart on open / pick / skip, Thor-verified (start-up and manual selector). PIN pad not tested on hardware. See SUMMARY.md top.
2. **Second-screen in-game widget reliability** (UPDATE 386: third-party widgets at home / in-game / after returning home VERIFIED on the Thor, Hub controller A / B and Widget Board reach FIXED; still open: Side menu FollowFocus on hardware with Endless on / off, code test only). Older text: (SUMMARY 379 covered Now Playing + third-party widget host in code only; hardware NOT verified; sections 2, 4-9 and part of 1 of the master polish still open).
3. **Quick page switcher and controller navigation.**
4. **Showcase, companion, widgets, Games page and dock polish.**
5. **Music transitions** without redesigning the players or touching their toggles.
6. **Dual-screen integration and final Thor QA** (then release only per the checklist).
Second-screen work of 379 is committed (update 384).

**Update 395 (29 Oct 2026, Thor ~9 min):** Artwork library Endless-off list starts at the top (`artLibRowTop`, `BoxArtLib395Test`), contrast scrim; Cardboard box side faces; catswitch C3 PASS for ARTLIB, ARCTIC, SHELF, SIDE, STACKS, BOX (43 layouts still NOT TESTED; justChanged-guard and rapid-reversal sync not checked frame by frame). Not committed as a release; no release. Details: SUMMARY.md top.

**Update 391 (29 Oct 2026, Thor ~14 min):** `Features.spineDepth` (Off / Subtle default / Realistic 3D, drawn over every spine picture via `GameSpine`), custom spine names fitted without sliding or "…" (`SpineName`, `SpineTitleFit`), Artwork library top gap 210 -> 138 px, "Game you're on" labels unified (Left = first card, Dashboard + Side menu Thor-verified). Static check SPINE-SLIDE. Untested: see SUMMARY 391.

**Update 390 (29 Oct 2026, Thor ~13 min, session 1 of the row audit):** `Features.rowFocus` (Dashboard + Side menu: As designed / Left / Middle / Right), settled x per value measured Endless on / off; `rowaudit.py` selection check (any layout): 13 layouts PASS, Channel grid (3 per press) and Live tiles (2-D mosaic) by design; no defect found. Untested: 37 layouts + Hero carousel (no trace), Endless off elsewhere, first / last game, few-game categories, second screen. Plan S2-S4 in SUMMARY 390. COVERAGE RA-01..05.

**Update 386 (29 Oct 2026, Thor ~17 min):** real third-party widget (Clock › Analog clock) updates on the Hub at home, in-game and after returning home (PASS, removed again). Hub controller: the right routing is keys on display 0 (the main screen), ▼ past the bottom enters the Hub. Fixed: D82 (A never pressed, B never left the Hub: `keysDown` swallowed their key-up), D81 (Widget Board tiles / widgets below the fold unreachable: tile pad targets + `SecondPad.focus`), D80 (reproduced: Wall "0 Games", category tiles built before the library loaded; `categoryTilesStale`). Tests: 4 new classes, 375 pass. NOT done: Side menu FollowFocus on hardware (`SideRowFollow386Test` only), D83 more "…" (R1 panel artist, Settings header subtitle, widget editor name), **D84 profile-selector screensaver timer (unresolved, see 2b)**.

**Update 385 (29 Oct 2026, Thor ~10 min):** D79 fixed (Now Playing title / artist: FullText wrap-shrink-slide in NowPlayingPanel, LyricsStage = the Hub Music page, MusicPlayer, BigMusicPlayer, FullMusicScreen, ScreenSaver, WidgetBoard; marquee in MusicOverlayView), Thor-verified on the Hub (home + in-game). Open: third-party widgets, Hub controller focus, Side menu FollowFocus, D80 (not reproduced), game popup description "…".

**Update 384 (29 Oct 2026, Thor ~15 min):** in-game Hub verified: Music page + Now Playing widget follow a track skip while a game runs (379 fix PASS), second-screen files committed. Open: D79 Hub Music title / artist ellipsis (NowPlayingPanel, needs marquee), D80 Wall page "0 Games" once; NOT verified: third-party widgets in the host, Hub controller focus, Side menu after FollowFocus.

**Update 383 (29 Oct 2026, Thor ~6 min):** D77 (Dashboard Endless: two games left of the selection, row moving out of step) fixed at the source: `FollowFocus` skipped the scroll for any card already in view; now `followTarget` keeps the selected card in one place (Dashboard + Side menu rows). `FollowFocus383Test` (357 unit tests pass), Dashboard checked on the Thor (▶▶▶◀: one game left each time). Old behaviour not recorded on hardware; Side menu row / second screen NOT tested. New CLAUDE.md rule 13: read CLAUDE.md, CURRENT_TASK.md, these priorities and the newest SUMMARY entries at the start of every task. Second-screen files still uncommitted.

**Update 382 (29 Oct 2026, Thor ~15 min):** Thor permission rule in `tools/qa/README.md` now points at the standing authorization (CLAUDE.md). Dashboard Endless 'second press' report NOT reproduced on the Thor (D77; one press = one game, both directions, wraps, 140 / 6 / 3 games), nothing changed in the app. New permanent rule: self-directed UX QA (CLAUDE.md + `tools/qa/RULES.md` R23); `vqa.sh onepress` + `focuscheck.py --size N [--edge-ok] [--max-latency]` + static ONEPRESS + 5 self-tests (67 OK). Resume & discover keeps its deliberate knock-then-menu (D78). Next for the one-press sweep: run `onepress` over the other looping layouts, Only-first-wide, Endless off and the second screen. Uncommitted second-screen files preserved.

**Update 378 (28 Oct 2026, Thor ~12.5 min):** D76 not reproduced in 4 recordings (before and after), hardened in `GameCard.kt` (wide art shows whole while the cover under the Roll-out isn't drawn; `RollReveal76Test`). Only-first-wide PASS on the Thor (set through Settings › Home › Layout options, restored to Wide capsule): normal, rapid, reversals, last-to-first and first-to-last wrap, art / title / focus aligned. R1 picker: choosing a layout (A) now moves the focus to Apply, D-pad Apply PASS on the Thor (2 A presses). v0.6.55 release: see SUMMARY.md top.

**Update 377 (28 Oct 2026, Thor ~10.5 min):** final QA, NO RELEASE (v0.6.54 latest, v0.6.55 unused), no code changed. R1 hold picker (preview / choose / cancel / apply / tap-cycle) PASS, per-category assignment + Follow default survive an app restart PASS, Wide Capsules Slides in clean except one 3-frame black bar in the capsule (defect D76, suspect, unfixed); Only-first-wide NOT VERIFIED. Thor left clean. Details: SUMMARY.md top.

**Update 376 (28 Oct 2026, Thor ~15 min):** 374 / 375 checked on the real Thor (Coverflow details, Artwork library, status bars, per-category manager Apply / Cancel / Follow default PASS). NOT checked: Wide Capsules, R1 hold, restart persistence, light / warm themes, dual screen -> **no release; v0.6.54 stays latest** (next tag v0.6.55). Three small fixes (ArtLib mixed row height, one-line slim-spine name, own preset mocks) unverified on hardware. Cinema black band = shared status-bar space: SCREENSHOT NEEDED. Details: SUMMARY.md top.

**Update 373 (28 Oct 2026, Thor ~4 min):** Thor crash fixed at the source: `TuneStore.edits` is a StateFlow (no snapshot state), `Tuner.supportedState` + `probeSupported()` keep the blocking root probe off composition (mechanism proven by unit test; exact app path not reproduced). L1 icons: extracted symbol / initials fallback, never full colour; `shortcut_themed_icons` default on and switched on on the Thor (12 L1 icons PASS; ARMSX2 not installed). Wide capsule: the fixed slot now only for Fades in / Just appears, default Slides in = continuous row; portrait-only art shown whole. Wide motion NOT TESTED on hardware. 336 unit tests, debug build, strict static checks OK. See SUMMARY top. 0.6.54 still unpublished.

**Update 372 (28 Oct 2026, Thor ~11 min):** 371 icons verified on the Thor (L1 themed + dock good, drawer originals crisp); second-screen drawer / dock own-colours used card art (dim squares) -> `PlainAppIcon`; themed icon tint follows a settled accent (THEME-COMPOSE fixed); one TuneStore.edits snapshot crash guarded in onCreate (cause inferred). ARMSX2 themed not seen. 0.6.54 still published unchanged; no new release. See SUMMARY top.

**Update 371 (28 Oct 2026, no device session):** smart themed icons: `data/IconQuality.kt` refuses unsuitable one-colour candidates (solid squares, plates) and falls back to the original icon; L1 shortcuts and the second dock keep their themed options; drawer and all home layouts are always original (`rememberAppIcon` = own colours); icon presentation baked and cached, no colour filter on top. Unit tests `ThemedIcons371Test`; HARDWARE NOT TESTED (see SUMMARY top). 0.6.54 still unpublished.

**Update 370 (28 Oct 2026, Thor session, see SUMMARY.md top):** 369 verified on the Thor for Tile home (PASS: centred at x=960, even, no drift) and one-down-stays on the category screen; NOT TESTED on hardware: second-down hand-over, custom spines, the 13 carousel modes. New: `ui/QaGeom.kt` geometry trace (`--ez voltcap_test_geom true`, `geomcheck.py LOG --logcat`), PerfPage sub line two lines (second-screen 'No game r…' was cut). 0.6.54 still unpublished; no release prepared until B / C / A are verified on the Thor (physical QA budget ~15 min per run). Thor left clean: hooks paused, snapshot restored, no recorder.

Read this first after the Cowork timeout (28 Oct 2026; Cowork is available again Sunday 1 Nov). It needs no other conversation context.
Then read the top entries of `SUMMARY.md` (updates 363-366), `tools/qa/README.md` ("Trusting a result") and `tools/qa/COVERAGE.md`.

## 1. Repository and release status
| | |
|---|---|
| Branch | `main` (working branch; old: `claude/laughing-franklin-xci4p0`, `master`, `qa-pass-1`, `test-stacks-314`, `voltcap-314`) |
| App version | **0.6.54 (versionCode 61)** (0.6.53 = 60; 0.6.52 was identical to 0.6.51 (version lines only); 0.6.53 changes category handling (section 2b); 0.6.54 fixes the empty-collection stop (section 2c)) |
| Releases | v0.6.49 (28 Oct 07:42 UTC), v0.6.50, v0.6.51, v0.6.52, **v0.6.53 "Latest"** https://github.com/dzepdzep/Voltcap/releases/tag/v0.6.53 - normal release (not draft, not prerelease), tag on `f7d9a13`; assets Voltcap-0.6.53.apk (sha256 673d8ccf...2a6a, release key "Toni Dzepina", verified by apksigner, aapt2 versionCode 60, downloaded copy hash identical), voltcap-profile-api28.dm, -api31.dm |
| Commits | `7c1498d` 0.6.51; `4f4375f` + `0242a2a` QA overhaul; `3dd851b` 0.6.52; `1782fc7` handoff; **`f7d9a13` 0.6.53 (CategoryStops)**; the commit after it only updates docs |
| CI | `qa.yml` success on 4f4375f, 0242a2a, 3dd851b and f7d9a13 (46 self-tests, strict static checks, coverage check). Hardware and Gradle unit tests are not run in CI (run locally: 289 unit tests pass) |

## 2. What happened after Cowork timed out
1. Claude Code (API credits) released 0.6.50 and 0.6.51 on 28 Oct, then did the QA overhaul (update 365: tools only), then this run (update 366).
2. This run: picker.sh / pop.sh false passes fixed, first real Thor sweep of all 51 layouts, stress on Cinema / Arctic, release 0.6.52.
3. Then (0.6.53 run, same day): the category synchronization investigation and fix, a Thor test of the L2 / R2 cycle and all 14 category looks, release 0.6.53 (section 2b).
   **No Kotlin / resource file was changed**; the only app-side change is the version in `app/build.gradle.kts` (and `project_copy/`).


## 2b. Category synchronization investigation (update 367, release 0.6.53)
Task: "categories appear with L2 / R2 but not on the category-selection screen". Full details: top entry of `SUMMARY.md`.
- Four independent copies built the category list: `LauncherViewModel.categoryStops` (L2 / R2, `jumpCategory`), `styleStops` (tabs), `buildCategoryTiles` + the hidden filter in
  `LauncherScreen`, `CategoryWarmup`. Now `data/CategoryStops.kt` is the single list (identity "ALL" / special name / "p:<key>"; order, hidden groups, tile order,
  console order, tiles taken off via Add category). Empty consoles / specials are still tiles; L2 / R2 step past empty ones (update 211); an empty collection is a stop but no tile.
- **Confirmed and fixed:** (1) tabs kept tiles taken off the screen, `jumpCategory` indexes another list, so a tapped tab could open the wrong category (reproduced with the unit test on the old
  code); (2) R2 from a category outside the list skipped the first stop (`coerceAtLeast(0)`); (3) start / last / restored category could be a tile taken off (state is built before
  `CategoryTileOrder.init`); (4) tiles rebuilt only after a 150 ms debounce when the screen opened (stale list). Test: `CategoryStopsTest` (9 tests: tiles == stops == tabs, 7 configurations + `stepFrom`).
- **Not reproduced:** the user's exact symptom. On the Thor with the real library (2213 games) L2 / R2 = 20 stops = 20 tiles, same order; all 14 category looks show "n / 20"; cold open shows all tiles at once.
  If it still happens, ask for: which look, which categories, whether "Other apps" / a collection / an empty console is involved, and whether a tile was removed (Add category lists them).
  Likely remaining suspects: an empty collection (stop but no tile) and a category that gains games while the screen is closed.
- **Tested on the AYN Thor 3140972f only** (0.6.53 `install -r` over 0.6.52, 28 C, battery 79 -> 77 %): full R2 cycles (20 stops), cold open of the screen, real "Remove from categories" on PlayStation 3 and
  Add category to restore it (cycle 19 -> 20 stops; the user's earlier removal of "Other apps" untouched), `looks.sh` over the 14 looks. Hooks off, snapshot restored, no recorder running.
  The Google TV Streamer and emulator-5554 were not touched.
- **NOT tested (still open):** dual-screen category selection, mixed-category / per-console overrides beyond the cycle, Cinema / Arctic text + artwork pass after this change, the 9 Settings
  categories, R1 option visibility, second-screen widgets, missing-artwork / metadata fixtures, profile launch / return, other aspect ratios (emulator), Arctic one-frame title question, TERMINAL "..." (rule 11).
- Seen, not changed: Collector's Drawers and Archive looks clip long console names in fixed labels ("Entertainment S", "itendo Entertainmen"): rule 11 candidate.
## 2d. Update 369 (device-free run, NOT published, NO device was touched)
[HISTORICAL, superseded by the standing Thor authorization at the top] User rule: no ADB / key injection / install / recorder on the Thor or Google TV Streamer in Claude Code sessions - **physical QA only with the user's separate explicit permission, and it must not interrupt normal use of the device**; mark such items NOT TESTED. See SUMMARY.md (369): `SecondPadGate` (category screen needs a deliberate double ▼ at the edge to reach the second screen; header / reorder never), own-spine flicker (2 causes), Tile home CENTER-anchor drift, Case Stacks views audit (all 13 modes present; no code removal found), `tools/qa/tools/geomcheck.py` (opt-in geometry check, 57 self-tests). 309 unit tests 0 failures. **NOT tested on hardware:** dual-screen category navigation, spine flicker, Tile home motion, Case Stacks view list on the Thor. Version unchanged (0.6.54; its release is still pending, was not published here).

## 2c. Update 368 (0.6.54, release pending: run tools
elease
elease.ps1 0.6.54 "notes" after approval; commit eb1083d, CI success): remaining category mismatch + combination matrix
- **Confirmed bug, fixed:** a collection with no games was an L2 / R2 stop and a tab but not a tile (`CategoryStops.stops` vs `buildCategoryTiles`). Red test `emptyCollectionIsNotAStopOrTab` on the 0.6.53 code, green now (`CategoryStopsTest`, 12 tests, 292 unit tests total 0 failures). Empty consoles / specials remain tiles, stops and tabs (no "hide empty" setting exists). The user's exact on-device symptom is still unreproduced; if it recurs ask for the look, the categories, and whether an empty collection / removed tile is involved.
- **Matrix:** `tools/qa/combos.json` -> `COVERAGE.md` (35 rows: PASS 19, NOT TESTED 16, FAIL 0), `COMBOS.md` (method, priorities, next), `device/combos.sh`, `tools/combocheck.py`, self-tests 53 (CI). App side: `QaTrace` "category stop" / "category tiles" lines (hooks on only).
- **Thor 3140972f only (28 Oct, 0.6.54):** 12 runs, all PASS (structural verdict): Voltcap row / Tile home x parity, L2-R2 mash x60, screen open-close x8, selection+category; Cinema + Arctic x parity + mash. 20 tiles = 20 stops. SoC reached 55 C during runs (waited to cool); battery 29 C. Hooks off, snapshot restored. Google TV Streamer untouched; no emulator.
- **Not tested:** visual jumps / stale artwork under the mash (no recordings), Cinema / Arctic screenmash + navcat, dual-screen category selection, R1 option matrix, 9 Settings categories, missing-artwork / metadata fixtures, profiles + game launch / return, other aspect ratios, per-console override sweep, Arctic one-frame title, Terminal "..." (rule 11), Collector's Drawers / Archive clipped console names.
- **Windows tip:** run device scripts through `MSYS_NO_PATHCONV=1 adb ...` in Git Bash; `py` is Python (`python3` is a Store stub); do not stack background sweeps on the device.
- **Recommended for Sunday 1 Nov:** (1) fixture library (no cover / no metadata / 300-char title / wide-tall-square covers / collections of 0, 1, 500 games) on a test profile; (2) record + `frames.py` a mash on Cinema / Arctic for the visual verdict; (3) R1 walk per layout family; (4) second-screen `screencap -d`; (5) the 9 Settings categories; (6) emulator aspect ratios (needs a debug build with hooks).

## 3. The 365ff8e fixes (update 363, in 0.6.51): Cinema / Arctic hub steadied
- `LogoOrName`: **one slot of fixed size** for the logo or the name (also while the logo loads, also in the two windows), so a game with a
  logo after one without no longer moves facts, buttons and rows.
- **`StableFullText`** (`ui/FitText.kt`): the text size is measured in the same layout pass (the old `FullText` auto-size drew nothing for
  a few frames after each change); same wrapping, readable minimum and slide. Used for name, console and facts.
- Reserved room for the facts line and story before details arrive (buttons / widgets no longer drop).
- Info block settles from 40 % in 240 ms with alpha applied straight to the text (no offscreen buffer); same for Live tiles, Side menu,
  Cube hub titles (50 %, 180 ms). `riseOnChange(floor = ...)` in `ui/Motion.kt`.
- Focused name under a card takes its width from where the row settles; position read in layout.
- **`HeldImage(zoom = true)`** (`ui/HomeCollections.kt`): each picture has its own slow-zoom state, so the old art no longer snaps smaller
  while the next one loads. Used by Cinema, Dashboard, Hero tiles.
- Files: FitText, HomeCollections, Motion, StyleArctic, StyleCinema, StyleDashboard, StyleHeroTiles, StyleLiveTiles, StyleSideMenu,
  CubeHub, WinScenesHomes.

## 4. Testing already done (before this run)
- **AYN Thor** 3140972f, 1920x1080 @120 Hz (28 Oct): release build over 0.6.50 with `adb install -r`; Cinema and Arctic hub 11 s of navigation each,
  read as contact sheets plus rest stills (long names, logos, games with / without facts): no shift of name / facts / buttons / rows, no flicker.
  Tile home, Dashboard, Hero, Live tiles, Side menu: no layout shift, jank 3-14 %. **No confirmed Cinema / Arctic regression; none claimed.**
- **Emulator** (debug build, 2400x1080, update 363): Cinema and Arctic, 12x Right, 6x Left/Right, Down/Up, hero buttons: title drawn in every frame.
- **RP Classic** 1240x1080 @60 (27 Oct): settings walk 12 of 21 categories, home layouts, charge limit.
- Limited Thor testing for the other recent layouts. Not covered: other aspect ratios, second screen.

## 5. QA overhaul (update 365) and the 366 additions
Root cause of the `frames.py` IndexError: **ffmpeg 8+/9 removed `-vsync`**; the decode returned 0 frames and `times[-1]` crashed. Fixed with
`-fps_mode passthrough` (+ fallback); an empty decode is now INFRA. Regression tests use Thor 960x540@120, 1920x1080@120 and emulator 540x1200@60.

False passes removed (all covered by `tools/qa/selftest/test_qa.py`): missing / empty / truncated / garbage / stale recordings and logs; empty or key-less
walk logs; a source folder with no files; an interrupted sweep; an expected-but-never-run layout; the profile picker or a black screen
recorded as a layout; SKIP / NOT TESTED / empty suites reading as success; `walkcheck` merging controls that share a label (identity is now label +
reading order, focus matched by box). Statuses/exit codes: PASS 0, FAIL 1, INFRA 2, NOT TESTED / SKIP 3 (`tools/qa/tools/qalib.py`).
Restored `counter.py`, `focuscheck.py`, `systrace.py` (they were only in the gitignored `Claude outputs/`).

Results: **42/42 self-tests OK** (Python 3.13 + ffmpeg 9.0.2). Strict static checks exit 0 (649 files, no HIGH). 188 of 188 recordings in `qa_runs` are valid
evidence. 46 of 51 layouts' Thor recordings re-scanned: 0 black / white / dip frames. **5 layouts have never been recorded: BUBBLES, CAMERA, MAGAZINE,
SHOP, TERMINAL.** Real E6 settings walk: 374/578 focusable controls reached (the old label-merged tool said 363/551); strict mode FAILs it
(unreached controls are mostly "not visited" - unconfirmed as defects). Exact matrix: `tools/qa/COVERAGE.md`.

### 5b. Update 366 (this run): verified results
- `picker.sh` now prints PICKER / HOME / UNKNOWN itself (5 pixels + frame-size check; black / missing / short / garbage = UNKNOWN); `guard.sh`
  delegates; `pop.sh` needs a verified HOME; `layouts.sh` presses B (keeps the profile) up to 3x on the picker. 46 self-tests (4 new).
- **Real Thor sweep** (3140972f, battery 83 -> 80 %, 26 -> 29 C): S4 46/46 PASS, S1 3 PASS + 2 SKIP (real profile picker on BUBBLES / SHOP, correctly
  skipped), S2 SHOP PASS, S3 BUBBLES PASS: **51 of 51 layouts recorded and validated**, 0 black / white / dip frames, 0 crashes. Janky %: ARCADE 25.4,
  SHELF 24.3, STACKS 19.9, MEMCARD 18.1, TV 18.1, TWIN_ICONS 16.9, GRID 12.8, CIRCUIT 12.7, DASH 12.5, BOX 12.1, MAGAZINE 11.5, CAMERA 10.0; median 5.2.
- **Stress** 80 x 70 ms on Cinema / Arctic: PASS (janky 3.1 / 2.7 %). Cinema's one standard-sweep transient (t 10.7 s) = intended card-row slide-in after R2.
- Eyeballed: BUBBLES / SHOP open on their category chooser; TERMINAL shows "..." on long names in the fixed-width list (full name in the preview pane;
  CLAUDE.md rule 11 / R18 question, unchanged). Other 46 layouts not human-reviewed.
- Coverage: `tools/qa/COVERAGE.md` (generated; QA-06 and LY-04 now PASS, LY-01 51/51). Raw evidence is in the gitignored `qa_runs/r52a`.
- Release checks: release APK builds (`./gradlew assembleRelease` from Git Bash works; `gradlew.bat` via cmd did not resolve the path), verified signature,
  `adb install -r` over 0.6.51 on the Thor (data kept), app starts, no crash.

## 6. Application changes
Through 0.6.52 none. **0.6.53:** `data/CategoryStops.kt` (new), `LauncherViewModel.kt`, `ui/StyleHomes.kt`, `ui/CategoryWarmup.kt`, `ui/LauncherScreen.kt` (section 2b). Open MED static findings (not gating): TRUNCATE 397,
SNAP-ANIMATE 79, THEME-COMPOSE 38, GLIDE-COMPOSE 35.

## 7. Outstanding issues and what needs a physical device
Not tested at all (listed NOT TESTED in COVERAGE.md; nothing claimed): the 9 Settings categories not walked (ST-02), R1 quick-menu dynamic options (ST-03),
second screen / dual-screen modes (DV-03), portrait (DV-02), other aspect ratios (phones, Odin 2 Portal, RP Flip 2), missing-artwork and long-name fixture
library (AR-01), return from a launched game (RT-02), profile selection flow (PF-01; user data, only with permission), controller walk and focus order on
Cinema / Arctic and the other layouts, human review of the 46 other layout looks, widgets, emulator runs (not used this run).
Open findings: (a) Settings walk E6 FAIL 374/578 controls reached (mostly "not visited"; focus lost 4x unconfirmed); (b) frame pacing of ARCADE / SHELF /
STACKS / MEMCARD / TV (no threshold, do not lower quality); D64 idle gameplay clip ~30 fps GPU (product decision); (c) **unconfirmed**: Arctic stress,
t 11.28 s, one frame with the title missing beside dimmed facts (seen once in a contact sheet, a pixel check did not reproduce it; a per-frame title check on
a stress recording would settle it); (d) BG-01 background video interactions FAIL (see COVERAGE.md).

## 8. Safety instructions and known limitations
- Dev devices: AYN Thor 3140972f (USB), RP Classic, the emulator. **The Google TV Streamer (adb over Wi-Fi) is NOT a dev device: never touch it.**
- Never uninstall Voltcap, clear its data, change storage, overwrite saves / profiles, touch charging or power settings, or disable safety limits.
  Sweeps use only `/data/local/tmp/vqa`, preview mode (nothing saved), the settings snapshot save / restore, and restore brightness, volume and refresh rate.
  Turn the test hooks off afterwards (`vqa.sh hooks off`), leave no recorder running, check battery and temperature first (Thor was 83 %, 27 C).
- Git Bash on Windows rewrites `/data/...` adb paths: `export MSYS_NO_PATHCONV=1`.
- `picker.sh` / `guard.sh screen_state` / `pop.sh` now treat a black screen as UNKNOWN. This run used the run folder `/data/local/tmp/vqa_r52a` (its own `vqa.sh` copy with `D=` changed) and deleted it afterwards; the older `/data/local/tmp/vqa` was not modified. `/data/local/tmp/pk.raw` was also removed (it is recreated by picker.sh).
- This run used only the Thor (volume, brightness, refresh rate 120/120 and the settings snapshot file were unchanged; hooks left off; no recorder left). Google TV Streamer and emulator-5554 were not touched.
- Instrumentation (QaTrace / TestHooks) only runs when the `voltcap_test_hooks` file exists; nothing was added to release hot paths.
- Hardware rows in `COVERAGE.md` ("hardware": true) never run in CI; their status is the last recorded device run.
- Workflow reminders (CLAUDE.md): edit `src_copy/` only (it equals `app/src/main/java/...`, gitignored); log every update at the top of `SUMMARY.md`; no truncated text.

## 9. Recommended next steps for Cowork
1. `git pull`; `python3 tools/qa/selftest/test_qa.py`; check the Thor battery / temperature first.
2. ST-02 / ST-03: walk the 9 remaining Settings categories and the R1 quick menu with `walk.sh` + `walkcheck.py`.
3. Controller walk + focus-order check on Cinema / Arctic; settle (c) with a per-frame title check.
4. Emulator fixtures: other aspect ratios, portrait, missing artwork, very long names; second screen on the Thor bottom display.
5. Human review of the 46 layout looks from `qa_runs/r52a` stills (kept locally, gitignored); decide the TERMINAL "..." question against rule 11.
6. Update `tools/qa/coverage.json`, run `coverage.py render`, commit.
API cost of this run: about $1.6 of the $20 budget (tool-reported).

### Recommended for Sunday 1 Nov (after 0.6.53)
1. `git pull`; unit tests (`./gradlew :app:testDebugUnitTest`), `py tools/qa/selftest/test_qa.py`; Thor battery / temperature first. On Windows use `py` (no `python3`); push scripts to the Thor with CRs stripped
   (`tr -d '\r'`) from a Windows-visible folder (adb cannot see Git Bash `/tmp`).
2. Ask the user for the exact category symptom (see 2b) and test a collection / empty console / category that gains games while the screen is closed.
3. Dual-screen category selection on the Thor; per-console overrides; the 9 Settings categories; R1 options (`walk.sh` + `walkcheck.py`).
4. Emulator: aspect ratios, missing artwork fixtures; Cinema / Arctic controller walk; rule-11 look at Drawers / Archive labels and TERMINAL.
API cost of the 0.6.53 run: about $3.1 of the $15 budget (tool-reported).
