# VoltCap QA report — pass 1 (27 Oct 2026)

Device: AYN Thor (serial 3140972f), main panel 1920×1080 @ **120 Hz** (measured: `mActiveSfDisplayMode refreshRate=120`,
frame interval 8.33 ms), second panel 1080×1240 @ 120 Hz. Controller: built-in pad (evdev "Xbox Wireless Controller",
event9). Builds: release (R8) 0.6.45, all AOT-compiled (`cmd package compile -m speed -f`) before measuring so both
builds start from the same state. Layout tested: **Classic / Voltcap row only**, Smart Continue + All + Nintendo 3DS +
Continue playing categories. Screen recording captured 119.4–119.8 fps (every panel frame).

## What was done
1. Repo, ADB tooling and existing hooks audited (TestHooks, PerfTrace/vcTrace, FrameWatch, compile_check, adb MCP).
2. Four independent code reviews (visual glitches, controller access, settings wiring, performance) — reports in
   `audit/2026-10-27/`. Reviewers read code only; every runtime claim below comes from the device.
3. QA tooling built (README.md): on-device runner `vqa.sh` (real pad input, recording, frame stats, Perfetto,
   memory, a full `suite`), `frames.py` (transient/blank/hitch detector with contact sheets), `framestats.py`,
   `compare.py`, `static_checks.py` (rules R1–R6), `settings_inventory.py` (851 settings), in-app `QaTrace`
   (logcat `VoltcapQA`: focus/category events + every frame over 1.5× the panel's frame time, with its phase breakdown).
4. Baseline recorded on 0.6.45, defects found on the recordings, fixed, rebuilt, re-run with the same scenario.

## Confirmed on device, fixed, re-verified
| id | defect | before | after |
|---|---|---|---|
| D1 | L2/R2: the new category's row drawn in place at full strength for 1–3 frames, then jumping aside and sliding back | seen on every category switch in 3 recordings (r1 frames 438, 698; r2 base 702) | gone: old row → one empty frame → new row slides in from its start position (C_nav frames 518, 764) |
| D14 | Category caption (e.g. "Nintendo 3DS") kept the old category's name for ~16 frames after the row changed | B_nav frames 712–720 | caption hidden in the switch frame, new name fades in (C_nav 517–545) |
| D15 | Game title jumped right when the logo arrived (logo slot 0 px wide while loading), then again when the logo changed size | B_nav 720→730→745 | title no longer moves while the logo loads (slot reserved); remaining motion is the intended category slide |
| D2 | Game header shown fully for one frame on each move, then hidden and risen in | code + r1 ev_572 | header starts at its rise-start state (C_nav: no transient on ◀ ▶ moves) |

## Fixed in code (compiled, installed), not separately provable from these recordings
D3 case/cartridge textures from the previous game (rememberSoftBitmap/Load), D4 endless rows keyed by place, D5 sticker
name flash, D6 27 more stale `produceState` sites → `produceKeyed`, D7 theme thumbnails stuck on the old theme, D8 focus
pulse rebuilding the card 20×/s, D9 sliders (step 1 on small ranges, focus leaves at the ends), D10 two pickers missing from
the "dialog open" list, controller paths: Background work + Sync status (R1 panel), Twin Showcase / Pocket Classic
dashboard chip, second-screen Wall edit mode, second-screen X = menu, Shortcuts ✕ badge, Edit Home hint text.

## Performance (same scenarios, A = 0.6.45 + QA logging, B = this pass; both AOT-compiled)
| metric | A | B | change |
|---|---|---|---|
| idle calm (35 s untouched): janky % | 45.6 | 31.2 | −14.4 |
| idle calm: p99 ms | 22 | 19 | −3 |
| idle just after input: p99 ms | 14 | 11 | −3 |
| home_nav ×3: janky % | 7.4 | 8.0 | +0.7 (noise level) |
| home_nav ×3: p90 / p99 ms | 19.3 / 26.0 | 19.7 / 26.3 | ≈ |
| stress ×2 (rapid input): janky % | 5.9 | 6.3 | +0.4 (noise level) |
| Java heap MB | 116.9 | 60.4 | −56.5 |
| total PSS MB | 610 | 567 | −44 |
**Navigation smoothness did not improve yet** — this pass fixed correctness/glitches, not the frame-time causes below.
At 120 Hz the home row runs p90 ≈ 19 ms (budget 8.3 ms): many frames take two refreshes.

## Open (evidence, next pass)
- D11: the home screen content recomposes ~50×/s while navigating (QaTrace: 409 focus-SideEffect runs in 8 s).
  LauncherScreenContent collects the whole uiState (perf review P2). Biggest lever for smoothness.
- D13: composition (ANIMATION phase) spikes 25–54 ms after moves / category switches (QaTrace slow lines).
- D12: render-thread CommandIssue spikes 36–48 ms after focus moves — suspected bitmap uploads (P4: trim transform keeps
  covers in software) or shader compiles; needs a Perfetto trace (`vqa.sh trace`).
- idle "calm" still draws 32 fps with 31 % janky frames although nothing should move — find what animates when calm.
- D17 (low): one empty frame of the row before the category slide starts.
- Static review lists still open: 77 SNAP-ANIMATE review sites (mostly per-layout animations, visual review V3),
  9 MAIN-DECODE, 15 PULSE-COMPOSE (Grid/TV/Side menu/Spotlight/Tile), 141 TOUCH-ONLY (review list, mostly fine),
  settings S1–S13 (`audit/2026-10-27/settings.md`), controller C1, C8, C11, C13, C14, C16, C17.

## Not tested in this pass (no claim made)
Every layout other than Classic; Looks other than the current one; R1 panel, Settings pages and dialogs on device (only
code review); game launch / return; second screen on device; profiles; scanning, sync, cloud saves; sleep/resume,
lid, configuration changes; long sessions (> 2 min); other handhelds (Odin 2 Portal, RP Classic, RP Flip 2);
artwork/3D model accuracy against references. The controller focus-graph walk (focuswalk) is designed in RULES R6 but
not yet automated.

---

# Pass 2 (27 Oct 2026, update 350) — Classic smoothness, testing the tester

Same device and method as pass 1. Every number below comes from the Thor. Builds are release 0.6.45 with R8,
AOT-compiled with `cmd package compile -m speed -f` before every measurement.

## 1. Does the QA system catch what it should? (known faults injected, QaFault)
| check | injected | caught | evidence |
|---|---|---|---|
| recording resolution | frame counter on every app frame | 1391/1391 frames recorded (100 %), 0 missing, 0 repeated | ST3 + counter.py |
| single-frame flash | 12 one-frame magenta squares | 11/11 recorded flashes found by frames.py, 0 false transients (1 flash fell before the recording started) | ST3 |
| dropped frames | 6 × 45 ms main-thread stalls | 6/6 in QaTrace; video hitches at each | ST1/ST3 |
| wrong focus | 2 × focus skips (+2 instead of +1) | 2/2 (focuscheck.py, after the "R L" warm-up fix) | ST3 |
| stale frame on category switch (D1 regression re-introduced) | `slide_old` | 2/2 switches: frames.py transients at 720 and 1068 show the new row at full strength for one frame | ST4 |
| one-frame header (D2 regression) | `rise_old` | **not exercised**: Classic doesn't use riseOnChange (its counter never ran) — no claim | ST4 |
| stale 3D-case texture | `stale_soft` | **not exercised**: no 3D cases in the tested categories — no claim | ST4 |
| QA cost | — | found and fixed D21 (churn observer made the app crawl); diagnostics are opt-in now | R8 |

## 2. Classic: verified on device
| id | what | before → after |
|---|---|---|
| D22 | colour glide re-composing everything | recompositions/s while navigating: home screen 113 → 8, game cards ~1000 → 54 (one rebuild per press). Cause proven two ways: the `snap_colors` A/B fault, and QaInval naming the ShelfColors state |
| D17 | empty frame before the category slide | G_nav and H_nav: 0 transients, 0 blank frames; the old row is followed directly by the new row sliding in |
| D20 | Tile home "Middle" not centred | tiles centred on screen (screenshot) |

### Same scenarios, A (pass-1 baseline) → H (now)
| metric | A | H |
|---|---|---|
| home_nav ×3: janky % | 7.4 | **1.6** |
| home_nav ×3: p90 / p99 ms | 19.3 / 26.0 | **12.0 / 19.3** |
| home_nav ×3: frames drawn | 1812 | 2048 |
| stress ×2: janky % | 5.9 | **1.7** |
| stress ×2: p90 / p99 ms | 18.5 / 25.0 | **12.0 / 20.5** |
| slow UI-thread frames (nav / stress) | 133 / 243 | 31 / 80 |
| frames > 2× budget (QaTrace) | 1210 | 237 |
| video hitches in the recorded nav run | 24 (G) | 2 |
| total PSS / graphics MB at the end | 610 / 243 | 542 / 216 |
| idle calm (35 s): janky % | 45.6 | 43.4 (unchanged — D18 open) |

The 120 Hz budget is 8.3 ms. The median frame now fits; p90 is 12 ms (one frame in ten takes two refreshes).

## 3. Fixed in code but not proven on device
- D23: template pictures (case, cartridge and box frames, masks, spines) are now shared and decoded once
  (`cachedImageResource` / `cachedPainterResource`). The cold-start traces T4/T5 show none of these decodes on the
  main thread, but this category set may simply not use them.
- D24: SecureStore returns an already-open store without taking the lock.

## 4. Open, with evidence
- **D24, start-up.** The first frame takes 830–880 ms. 314 ms of it is LauncherViewModel opening 3 encrypted
  credential stores on the main thread (tgdb 144 ms, screenscraper 122, rawg 49), queued behind the prewarm thread
  (trace T5, `VC:secureOpen:*`).
  - Fix idea: build the first UI state without the "connected" flags and fill them in from IO.
  - This changes behaviour, so it was not done this pass.
- **D25.** One 47–58 ms main-thread `decodeBitmap` inside a recomposition the first time "Nintendo 3DS" appears after a
  cold start (T3/T4/T5).
  - It is not imageResource, painterResource, PlatformLogos or PackSpines; all four are now instrumented.
  - Next step: a sampling profile.
- **D18.** Calm idle still draws ~32 fps, 43 % of it janky. Churn shows 3 states rising from 1.0 at ~120/s after input:
  something scales continuously.
- **THEME-COMPOSE.** The static rule lists 37 more sites, mostly other layouts and dialogs.
- **SNAP-ANIMATE.** 77 review sites, mostly per-layout.

## 5. Not tested in this pass (no claim)
- Every layout other than Classic, except the Tile "Middle" placement.
- On the device: the R1 panel, Settings and dialogs, game launch and return, the second screen, profiles, sync.
- Controller items C1–C17. They are fixed in code (pass 1) but not walked on the device yet.
- Other handhelds.


---

# Pass 3 (27 Oct 2026): controller walk, the other layouts, start-up

Device, builds and method are the same as before. Every number below comes from the Thor.

## New tools (all opt-in, test builds only)
- **QaWalk** (`--ez voltcap_test_walk true`). Reads focus and the controls of the screen that has the controller straight
  from Compose's semantics tree (`uiautomator` can't read Voltcap: it never goes idle).
  - Driven by real pad presses: `device/walk.sh`.
  - Reports with `tools/walkcheck.py`: TOUCH-ONLY, NOT REACHED and STUCK, per window and per page.
- **QaExplore** (`--es voltcap_test_explore "SELECT [from=N]"`). Voltcap drives the D-pad itself (Instrumentation key
  events, the same input path as a pad press) through every Settings category and its "›" sub-pages.
  - It presses A only on navigation rows whose name isn't destructive.
  - It stops on `--es voltcap_test_explore stop`.
  - Wrapper: `device/explore.sh`. It saves and restores settings, brightness and volume (rule R11).
- **QaSampler** (file `voltcap_test_sampler`). A stall profiler that works on release builds, where simpleperf and
  `am profile` can't attach. It samples the main-thread stack every 3 ms and logs stalls over 24 ms and every picture
  decode on the main thread.
  - `tools/retrace.py` turns R8's short names back into real ones with the build's mapping.txt.
  - `device/coldsample.sh` runs it over a cold start.
- **QaInval themeread.** The stack of the code that reads the theme colours in a watched scope (`device/lchurn.sh`, per layout).
- **Layout sweep** (`device/layouts.sh`). Every layout for this session only (nothing saved): the same navigation, a
  recording at 120 fps (half size), frame stats and the QaTrace log.
  - Reports: `tools/layoutreport.py --frames` (with frames.py) and `tools/layoutcompare.py` (before / after).
- `tools/coverage.py`: tested settings coverage, from what the walks reached, against the 1169-row inventory.

## Verified on the device
| id | what | evidence |
|---|---|---|
| D26 | the R1 notification's ✕ couldn't be reached with the D-pad | walk W5: ▶ → Dismiss, ◀ → back |
| D25 | 50 ms decode on the main thread the first time a pack cartridge showed (second screen) | coldsample CS1: 1 main-thread decode → CS2 / CS3: 0 |
| D30 | Tile home Middle: the row jumped for one frame | LA_TILES: 4 transients → LD_TILES: 0 |
| D32 | Resume & discover: the next game in the big spot for one frame on every move | LB_RESUME: 10 transients → LD_RESUME: 1 (the background clip) |
| D31 + D34 | 26 layouts re-composed the whole home screen ~80–110×/s (colour glide read via the category bar and the status bar's look) | table below |

### Layout sweep before → after
Same navigation, AOT-compiled.
- Before = runs LA–LD.
- After = run LE (D31 + D34 + D30 + D32 + D33).

| layout | janky % before → after | p90 ms | p99 ms | frames >2× budget | Screen recomp/s |
|---|---|---|---|---|---|
| ARCADE | 35.6 → **25.7** | 46 → 36 | 61 → 53 | 767 → 592 | 42 → 9 |
| BOX | 18.7 → **11.7** | 26 → 21 | 36 → 32 | 565 → 703 | 67 → 10 |
| CARDS | 9.7 → **3.7** | 21 → 15 | 42 → 28 | 410 → 134 | 98 → 10 |
| CIRCUIT | 17.0 → **14.1** | 24 → 22 | 32 → 28 | 526 → 697 | 77 → 10 |
| CLASSIC | 1.2 → **1.1** | 12 → 12 | 19 → 18 | 77 → 91 | 12 → 9 |
| CONSOLES | 13.5 → **9.3** | 21 → 20 | 26 → 27 | 626 → 610 | 96 → 10 |
| CRATE | 8.1 → **2.9** | 20 → 17 | 25 → 23 | 450 → 229 | 101 → 11 |
| CROSS | 12.2 → **3.4** | 21 → 15 | 26 → 23 | 632 → 157 | 94 → 9 |
| CUBE | 14.1 → **5.4** | 23 → 18 | 29 → 26 | 712 → 439 | 83 → 10 |
| CUSTOM | 10.3 → **4.9** | 21 → 18 | 26 → 24 | 666 → 289 | 91 → 9 |
| DASH | 13.2 → **11.1** | 24 → 22 | 29 → 29 | 460 → 449 | 84 → 10 |
| DISCS | 11.0 → **6.3** | 21 → 17 | 27 → 24 | 660 → 413 | 93 → 10 |
| FILESELECT | 7.2 → **3.1** | 18 → 16 | 26 → 26 | 340 → 307 | 102 → 10 |
| GREENCORE | 17.3 → **5.7** | 24 → 18 | 48 → 44 | 885 → 447 | 88 → 13 |
| HUB | 9.7 → **4.2** | 21 → 18 | 32 → 27 | 412 → 272 | 95 → 11 |
| LIBRARY | 13.9 → **11.2** | 24 → 20 | 36 → 26 | 442 → 443 | 79 → 9 |
| LIVE | 4.8 → **1.3** | 17 → 12 | 23 → 18 | 392 → 50 | 112 → 10 |
| MEMCARD | 23.3 → **18.2** | 28 → 24 | 34 → 34 | 816 → 1335 | 71 → 10 |
| RESUME | 10.1 → **4.4** | 20 → 17 | 28 → 30 | 495 → 342 | 92 → 13 |
| SHELF | 26.4 → **22.2** | 29 → 26 | 61 → 65 | 603 → 502 | 85 → 10 |
| SIDE | 8.4 → **3.4** | 19 → 17 | 24 → 23 | 477 → 278 | 98 → 10 |
| STACKS | 29.7 → **28.2** | 48 → 44 | 125 → 117 | 512 → 443 | 51 → 10 |
| STORAGE | 13.5 → **6.3** | 22 → 18 | 34 → 32 | 702 → 529 | 84 → 10 |
| TIMELINE | 10.9 → **5.7** | 22 → 17 | 27 → 24 | 531 → 292 | 96 → 10 |
| TV | 17.0 → **10.5** | 24 → 20 | 44 → 40 | 822 → 469 | 90 → 14 |
| TWIN_DESK | 0.6 → **1.9** | 10 → 16 | 15 → 18 | 16 → 1101 | 68 → 5 |
| TWIN_ICONS | 21.9 → **16.6** | 27 → 23 | 34 → 30 | 628 → 719 | 74 → 10 |


TWIN_DESK: its first run drew only 2 focus events, so that row doesn't compare like with like.

## Fixed in code, compiled and installed, but not separately proven
- D27: the panels' ✕ is named "Close".
- D28: RomHacks regular expressions compiled once.
- D29: controller-light flashes off the main thread.
- D33: Layout Studio part entrance (LE_CUSTOM: 9 → 3 transients; the 3 left are D35).
- D35: Layout Studio's row spinning through games on L2 / R2. Fixed after the sweep, not yet re-run.

## Measured, root cause known, not fixed
- **D24.** Cold start waits ~260–320 ms for 3–5 credential stores in LauncherViewModel's constructor (QaSampler stacks).
  - Tried and reverted: a different prewarm order (worse) and parallel opening (Keystore-bound, ~10 ms gain).
  - The fix needs a deferred first state (see defects.json).
- **D18.** Idle redraws are the 'Art + videos' background clip (heatmap): by design.
- **Render-bound layouts** (QaTrace phase breakdown of the slow frames, run LE).
  - Arcade: issue-commands 6 ms + GPU 6 ms per frame.
  - Case stacks: frames of 150–270 ms with command issue 35–53 ms.
  - Memory card, Twin icons, Box: issue-commands 5–6 ms.
  - Next step: a render-thread trace per layout.

## Not yet done in this pass
- The full Settings explorer run (in progress).
- The dialogs opened from home: Start (game options), Y (achievements), L1 (shortcuts).
- Game launch / return, profiles, the second screen on its own, RetroAchievements screens.
- Settings behaviour tests (each setting's effect), and the other handhelds.

## Pass 3, batch 2 (2026-10-27 afternoon), Thor, release builds 14:10–14:39

### Verified on the device
| id | what | evidence |
|---|---|---|
| D36 | Settings › Categories: after ▲ / ▼ the focus now follows the category that moved (before, it stayed on the row and the next press moved the neighbouring category) | walk D36: focus y 945 → 822 → 699 → 822 → 945, one row per press |
| D37 | Walk snapshot now also keeps the category order and the hidden list (a walk had moved one category; put back by hand at once) | snapshot file = order shown after the walk |
| D39 | Tile home: the round shortcuts moved in Edit Home were drawn over the tiles. Toni's saved spot removed, so the bar is by the tiles again | screens tiles_sc / tiles_sc2 |
| D40 | Tile home Middle: tiles + round buttons centred as one group (Switch home); the tiles alone when there are no buttons | tilemid log: group 721, mid 425.8, y 114 |
| D41 | Carousel: each game re-composed every glide frame (≈1,000 re-runs/s) → ≈50/s | STACKS janky 26.6 → **22.7 %**, p90 42 → 32 ms, p99 85 → 77 ms |

### Found to be a tool artefact, not an app defect
- E4 "not reached" chips in Theme › Size & text: a hand walk (ST2) shows they are reachable with ◀ (the row is entered on its picked chip). The explorer now walks ◀ and ▶ along each row, finds its way back by label@x, starts the categories at the top of the list and waits for scrolls to settle (D38). Re-run E6 is in progress.
- A check of the other layouts' glide-derived states (ARCADE, CH4): no per-frame re-runs. So D41 is specific to that one carousel state, not derivedStateOf in general.

### Still open
- STACKS / ARCADE etc.: the rest is render-bound (L2/R2 frames of 100–136 ms: command issue 30–48 ms, while the new collection's pictures are uploaded).
- D24 cold start, as before.
