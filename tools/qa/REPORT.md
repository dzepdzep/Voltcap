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
