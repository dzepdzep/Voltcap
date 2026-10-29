# VoltCap QA system

Finds what a screenshot can't: 1–3-frame flashes, stale artwork, stutter, settings that do nothing, controls a
controller can't reach. Everything here is read-only towards your data: no saves, profiles or settings are changed
(a run that switches a setting uses `voltcap_test_snapshot save/restore`), the refresh rate is put back after `rate60`.

```
tools/qa/
  README.md            this file — how to run a pass
  RULES.md             recurring bug patterns (one per real defect), how each is detected and fixed
  defects.json         the defect database (id, status, evidence, root cause, fix, regression check)
  REPORT.md            the latest pass: what was tested, measured, fixed, and what wasn't covered
  device/vqa.sh        on the handheld: real controller input (evdev), recordings, frame stats, Perfetto, memory
  tools/frames.py      frame-by-frame flash / black-frame / hitch detector for a recording (+ contact sheets)
  tools/qalib.py       shared statuses (PASS / FAIL / INFRA / NOT TESTED / SKIP), exit codes, recording / log / screen validators
  tools/runcheck.py    validates a pulled device sweep through its manifest (device/guard.sh); tools/batch_frames.py, coverage.py
  selftest/test_qa.py  the QA tools run on intentionally broken fixtures (CI: .github/workflows/qa.yml)
  COVERAGE.md          generated coverage matrix (coverage.json + HomeLayout + audit/*/layouts_auto.json); never hand-edited
  tools/framestats.py  gfxinfo framestats → frame-pacing numbers (budget = the panel's real refresh rate)
  tools/static_checks.py  code checks for RULES.md patterns (--strict fails on HIGH)
  tools/settings_inventory.py  every setting: key, default, readers, UI, search index, backup scope → CSV
  audit/<date>/        reviewer reports (visual, controller, settings, perf) for that pass
qa_runs/<run>/         recordings and dumps pulled from the handheld (not in git)
```

## App side (test builds only when the flag file exists)
`Android/data/com.shelf.launcher/files/voltcap_test_hooks` turns on `TestHooks` (adb intents: layout, game, look,
settings page, snapshot…) and `QaTrace` (logcat tag `VoltcapQA`: focus / category events, and every frame that missed
the panel's frame time with its breakdown). Nothing is written without the file.

## A pass (what Claude runs; ~10 min per layout)
1. `adb push tools/qa/device/vqa.sh /data/local/tmp/vqa/vqa.sh`
2. Per scenario: `sh vqa.sh perf_reset; sh vqa.sh rec NAME 8; sh vqa.sh seq "R R R R R R W400 L L L L R2 W800 R R R L2 W800" 160; sh vqa.sh perf_dump NAME`
   plus `stress NAME 80 70` (rapid navigation), idle (`perf_reset`, 5 s, `perf_dump`), `mem NAME`.
   `logcat -d -s VoltcapQA > out/NAME_qa.txt` for the app's own events.
3. Pull `out/*` to `qa_runs/<run>/`, then `python3 tools/qa/tools/frames.py qa_runs/<run>/NAME.mp4` and look at every
   `ev_*.png` contact sheet. `framestats.py` for the numbers.
4. `python3 tools/qa/tools/static_checks.py src_copy --strict` before shipping.
5. Record results in REPORT.md and defects.json; a new pattern → RULES.md + a check.

### Capture notes
- The AYN Thor's recorder keeps every 120 Hz frame (frames.py prints the capture rate: check `fps`).
  On devices where it doesn't, run `vqa.sh rate60` first (each app frame becomes a video frame) and `rate_restore` after.
- `vqa.sh` sends real gamepad events to the built-in controller's evdev node (found automatically), so the app sees
  exactly what a button press gives; `K<code>` sends a system key through `input` when needed.

## Scenarios (keys: U D L R A B X Y L1 R1 L2 R2 START SELECT, Wnnn = wait)
| name | sequence | checks |
|---|---|---|
| home_nav | `R R R R R R W400 L L L L R2 W800 R R R L2 W800` @160 | flashes on focus move / category switch, hitches |
| stress | `stress NAME 80 70` | stale covers under rapid input, cumulative jank |
| idle | 5 s untouched | frames drawn while idle (should be near 0 when calm) |
| r1_open | `R1 W600 D D U U B W400` | R1 panel first frame, focus on open, B closes |
| window | `A W800 B W600` | game window open/close flash, focus restore |
| layouts | `am start … --es voltcap_test_layout X --ez voltcap_test_preview true` then home_nav | every layout, nothing saved |

## Pass 2 additions
- `device/rc.sh NAME [FAULTS]` — recompositions per second while navigating (`recomp Screen=… GameCard=… GameCardF=…`).
  Run it with `snap_colors` as the A/B fault (the colour glide off).
- `device/churn.sh` — why parts re-compose. It writes `churn` lines (the busiest states) and `inval <part>: Nx <state>`
  lines (QaInval names the state that invalidated each watched part). Opt-in only, rule R8.
- `device/tcold.sh NAME` — cold start plus category switches under atrace. It leaves Voltcap's main-thread trace for
  `tools/systrace.py`; `VC:*` sections name Voltcap's own work (resDecode, logoDecode, spineDecode, secureOpen:<store>).
- `vqa.sh memtest NAME` — memory before and after 3 rapid-navigation bursts and 20 s of idle.
- Test faults (`--es voltcap_test_fault`): `counter`, `flash:N`, `jank:N`, `focus_skip:N`, `slide_old`, `rise_old`,
  `stale_soft`, `snap_colors`. Self-test: `vqa.sh selftest NAME "<faults>"`, then counter.py, frames.py and
  focuscheck.py. One-press check (R23, RULES.md): `sh vqa.sh onepress "L L R R" 1200` then `focuscheck.py LOG --size N [--edge-ok] [--max-latency MS]`. See REPORT.md pass 2 for what each one is proven to catch.
- static_checks.py rule THEME-COMPOSE (R10).

## Trusting a result (QA overhaul, 28 Oct 2026)
Exit codes of every tool: **0 PASS**, **1 FAIL** (a defect / failed assertion), **2 INFRA** (the evidence can't be used: missing,
empty, truncated, stale, undecodable, or the tool broke), **3 nothing ran** (SKIP / NOT TESTED). Only 0 is a pass; an empty
suite is NOT TESTED. A tool that only inspects (frames.py without `--fail-on`, walkcheck without `--strict`) prints `[REPORT]`,
never PASS. Evidence that predates the run (`--not-before` / `--max-age`, the sweep manifest's `start`) is STALE = INFRA.
- **Self-tests** (stdlib `unittest`, ffmpeg + numpy + Pillow): `python3 tools/qa/selftest/test_qa.py` - run it after touching any tool.
- **Combination matrix** (0.6.54): `COMBOS.md`, `combos.json`, `device/combos.sh RUN LAYOUT SCENARIO` + `tools/combocheck.py` (see COMBOS.md).
- **Category parity** (0.6.53): JVM test `app/src/test/.../data/CategoryStopsTest.kt` (`./gradlew :app:testDebugUnitTest --tests "*CategoryStopsTest"`) compares the category screen tiles, L2 / R2 stops and the style-home tabs; on a device `looks.sh RUN "TILES SLIDES ... DASHBOARD"` shows "n / N" in every look and N must equal the number of R2 stops (20 on the Thor, 28 Oct). On Windows run the device scripts with CRs stripped (`tr -d "
"`).
  CI runs it with `QA_REQUIRE_FFMPEG=1` (a missing ffmpeg is then a failure, not a skip).
- **frames.py** needs `-fps_mode passthrough` (ffmpeg 8+ removed `-vsync`; that was the IndexError on every recent recording).
  `--fail-on transient,black,white,dip,hitch,repeat` turns events into a FAIL; `black,white,dip` are the unambiguous ones. A slide or
  crossfade is a transient too, so transients are for human review of the contact sheets.
- **Device sweeps**: `device/layouts.sh` sources `device/guard.sh` (needs `guard.sh` + `picker.sh` next to `vqa.sh`), writes
  `out/RUN_manifest.txt`, skips a layout showing "Who's playing?" (SKIP) or a black screen (UNKNOWN). After pulling `out/`:
  `python3 tools/qa/tools/runcheck.py qa_runs/<run>/out RUN`. `picker.sh` prints PICKER / HOME / UNKNOWN (a black, missing, short or garbage capture is UNKNOWN, never HOME);
  `guard.sh screen_state` and `pop.sh` use it (pop.sh needs a verified HOME). `layouts.sh` presses B (keeps the profile) up to 3 times on
  the picker, then skips what is still not HOME. HOME = lit and not the picker: it does not prove the screen is Voltcap. Use a run-specific
  folder on the device and copy `vqa.sh` with its `D=` line changed if an older `/data/local/tmp/vqa` must stay untouched.
- **Controller walk**: `walkcheck.py LOG [--strict]` tells controls with the same label apart (label + reading order, focus matched
  by box); reports TOUCH-ONLY / NOT REACHED / DUPLICATE / SAME LABEL / FOCUS ON UNKNOWN / focus lost. `--strict` fails on those.
- **Coverage**: edit `coverage.json`, then `python3 tools/qa/tools/coverage.py render`; `coverage.py collect OUT_DIR audit/<date>/layouts_auto.json`
  re-analyses the newest recording of every layout. Hardware rows (`"hardware": true`) never run in CI.
- **Hardware safety**: sweeps only use QA scratch (`/data/local/tmp/vqa`), preview mode, the settings snapshot, and put brightness /
  volume / refresh rate back. Never uninstall, clear data, touch charging or run on a device that is not a known dev target
  (the Google TV Streamer is not one).

## Physical QA on the Thor (standing authorization, CLAUDE.md)
The owner's standing authorization in `CLAUDE.md` ("Standing Thor QA authorization") covers routine QA on the AYN Thor, ADB serial **3140972f** (always `adb -s 3140972f`): no per-session permission is needed. Safeguards stay: max 15 minutes physical QA per task; never clear app data or uninstall; no charging-limit changes; preserve games, saves, profiles, artwork; restore volume / DND / display; stop test processes and recorders; never touch any other device (Google TV Streamer, Odin 2 Portal). A simulated test is still not hardware verification: mark untested rows NOT TESTED. `tools/geomcheck.py TRACE.json` is the device-free element geometry check.
