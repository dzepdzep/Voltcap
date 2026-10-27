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
  focuscheck.py. See REPORT.md pass 2 for what each one is proven to catch.
- static_checks.py rule THEME-COMPOSE (R10).
