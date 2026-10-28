# COWORK_HANDOFF — resume here (written 28 Oct 2026, final state of the QA overhaul run)

Read this first after the Cowork timeout (28 Oct 2026; Cowork is available again Sunday 1 Nov). It needs no other conversation context.
Then read the top entries of `SUMMARY.md` (updates 363, 364, 365), `tools/qa/README.md` ("Trusting a result") and `tools/qa/COVERAGE.md`.

## 1. Repository and release status
| | |
|---|---|
| Branch | `main` (the working branch; other branches are old: `claude/laughing-franklin-xci4p0`, `master`, `qa-pass-1`, `test-stacks-314`, `voltcap-314`) |
| App version | **0.6.51 (versionCode 58)** - unchanged by this run |
| Releases | v0.6.49 (28 Oct 07:42 UTC), v0.6.50 (08:23), **v0.6.51 "Latest" (11:05)**, all normal releases; no prerelease, nothing published in this run |
| Last app commit | `7c1498d` "Voltcap 0.6.51" (before it: `1e9864f` update 364 notes, `365ff8e` update 363 fixes, `4f85284` 0.6.50, `718d4fc` update 362) |
| QA overhaul | `4f4375f` "QA overhaul ..." (pushed to origin/main) and the commit right after it, "QA overhaul: record CI result and hashes" (this file's final state) |
| CI | `.github/workflows/qa.yml` (new) and `verify-update-310.yml` (existing Gradle source-check). **First qa.yml run (run 40680336896, on 4f4375f): success** - 42 self-tests OK on ffmpeg 6.1 (Linux), strict static checks PASS, coverage check PASS. Hardware tests are not run in CI |

## 2. What happened after Cowork timed out
1. The user continued with Claude Code on API credits. 0.6.50 and 0.6.51 were released the same day (28 Oct).
2. This run (update 365) repaired and extended the **QA tooling only**. No Kotlin / resource file was changed, no version bump.
3. The old defect "frames.py fails with IndexError" is fixed (cause below). The two untracked scripts `picker.sh` / `pop.sh` were
   inspected, kept unchanged and are now tracked.

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

## 5. This QA overhaul (update 365): fixes and real results
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

## 6. Application changes in this run
**None.** No defect was confirmed with reproducible evidence that required an app change. (`static_checks` open MED findings, not gating: TRUNCATE 397,
SNAP-ANIMATE 79, THEME-COMPOSE 38, GLIDE-COMPOSE 35 - CLAUDE.md rule 11 vs the 397 R18 hits needs a look.)

## 7. Outstanding issues and what needs a physical device
Priority order:
1. **Run a real sweep with the new manifest** on the Thor: push `guard.sh` + `picker.sh` next to `vqa.sh`, `sh layouts.sh RUN "<layouts>"`, pull `out/`,
   `python3 tools/qa/tools/runcheck.py qa_runs/<run>/out RUN`. This path is fixture-tested only.
2. Record the 5 never-recorded layouts; stress (`vqa.sh stress NAME 80 70`) + controller walk on Cinema / Arctic; human review of the other 44 layouts.
3. Second screen (Thor bottom), portrait, other aspect ratios (phones, Odin 2 Portal, RP Flip 2), the 9 settings categories not walked, R1 quick-menu dynamic options.
4. Missing-artwork / long-title / unusual-name fixture library; return from a launched game; profile flows (user data: only with explicit permission).
5. Open performance findings (measured, no pass threshold): janky % STACKS 28.2, ARCADE 25.7, SHELF 22.2, MEMCARD 18.2; D64 idle gameplay clip ~30 fps GPU (needs a product decision). Do not lower quality to improve numbers.
6. A dedicated focus-order check per layout (focuscheck's model only fits the Classic row, so it is not run per layout).

## 8. Safety instructions and known limitations
- Dev devices: AYN Thor 3140972f (USB), RP Classic, the emulator. **The Google TV Streamer (adb over Wi-Fi) is NOT a dev device: never touch it.**
- Never uninstall Voltcap, clear its data, change storage, overwrite saves / profiles, touch charging or power settings, or disable safety limits.
  Sweeps use only `/data/local/tmp/vqa`, preview mode (nothing saved), the settings snapshot save / restore, and restore brightness, volume and refresh rate.
  Turn the test hooks off afterwards (`vqa.sh hooks off`), leave no recorder running, check battery and temperature first (Thor was 83 %, 27 C).
- Git Bash on Windows rewrites `/data/...` adb paths: `export MSYS_NO_PATHCONV=1`.
- `picker.sh` reads one pixel (a black screen prints HOME); use `guard.sh` `screen_state`. `pop.sh` still uses the bare check.
- In this run only the Thor was used, read-only (one screencap, no app launch). A scratch-file `rm` there may have removed a pre-existing
  `/data/local/tmp/picker.sh` (the repo copy is the source).
- Instrumentation (QaTrace / TestHooks) only runs when the `voltcap_test_hooks` file exists; nothing was added to release hot paths.
- Hardware rows in `COVERAGE.md` ("hardware": true) never run in CI; their status is the last recorded device run.
- Workflow reminders (CLAUDE.md): edit `src_copy/` only (it equals `app/src/main/java/...`, gitignored); log every update at the top of `SUMMARY.md`; no truncated text.

## 9. Recommended next steps
Pull `main`; run `python3 tools/qa/selftest/test_qa.py`; do item 1-2 above on the Thor; update `tools/qa/coverage.json`, `render`, commit.
API cost of this run: see the final report of the session (not recorded in the repo).
