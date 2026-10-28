# COWORK_HANDOFF — resume here (updated 28 Oct 2026, after release 0.6.52)

Read this first after the Cowork timeout (28 Oct 2026; Cowork is available again Sunday 1 Nov). It needs no other conversation context.
Then read the top entries of `SUMMARY.md` (updates 363-366), `tools/qa/README.md` ("Trusting a result") and `tools/qa/COVERAGE.md`.

## 1. Repository and release status
| | |
|---|---|
| Branch | `main` (working branch; old: `claude/laughing-franklin-xci4p0`, `master`, `qa-pass-1`, `test-stacks-314`, `voltcap-314`) |
| App version | **0.6.52 (versionCode 59)**; the code is identical to 0.6.51 (only the version lines changed) |
| Releases | v0.6.49 (28 Oct 07:42 UTC), v0.6.50 (08:23), v0.6.51 (11:05), **v0.6.52 "Latest" (12:22)** https://github.com/dzepdzep/Voltcap/releases/tag/v0.6.52 - normal release (not draft, not prerelease), tag on `3dd851b`; assets Voltcap-0.6.52.apk (sha256 2e9c1ea5...c524, release key "Toni Dzepina"), voltcap-profile-api28.dm, -api31.dm |
| Commits | `7c1498d` 0.6.51; `4f4375f` + `0242a2a` QA overhaul; **`3dd851b` 0.6.52 (QA reliability, 51-layout sweep, version bump)**; the commit after it only updates this handoff |
| CI | `qa.yml` success on 4f4375f, 0242a2a and 3dd851b (46 self-tests, strict static checks, coverage check). Hardware tests are not run in CI |

## 2. What happened after Cowork timed out
1. Claude Code (API credits) released 0.6.50 and 0.6.51 on 28 Oct, then did the QA overhaul (update 365: tools only), then this run (update 366).
2. This run: picker.sh / pop.sh false passes fixed, first real Thor sweep of all 51 layouts, stress on Cinema / Arctic, release 0.6.52.
   **No Kotlin / resource file was changed**; the only app-side change is the version in `app/build.gradle.kts` (and `project_copy/`).

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
**None** in this and the previous QA run. No defect was confirmed with reproducible evidence. Open MED static findings (not gating): TRUNCATE 397,
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
