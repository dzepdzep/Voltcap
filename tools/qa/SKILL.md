---
name: voltcap-qa
description: Run VoltCap's QA pass on a real handheld — frame-by-frame flash detection, frame pacing, settings and controller checks, static bug-pattern checks — then fix, rebuild, re-verify and record results. Use for "/voltcap-qa", "check VoltCap for glitches/stutter", or after a VoltCap update.
---

# VoltCap QA pass

Project: `C:\Users\User\AndroidStudioProjects\ShelfLauncher` (VoltCap, package com.shelf.launcher). Read `CLAUDE.md`,
then `tools/qa/README.md`, `tools/qa/RULES.md`, `tools/qa/defects.json`, the top of `tools/qa/REPORT.md`.
Edit Kotlin only in `src_copy/` (CRLF line endings — keep them), never `app/src/...` directly.

## 1. Device
- `voltcap-adb list_handhelds` → pick the handheld (Thor serial 3140972f). Wake it (`input keyevent KEYCODE_WAKEUP`) and
  check a screenshot shows Voltcap's home.
- `push tools/qa/device/vqa.sh → /data/local/tmp/vqa/vqa.sh`. Check `sh vqa.sh dev` finds the gamepad.
- Test-hooks flag file must exist: `/sdcard/Android/data/com.shelf.launcher/files/voltcap_test_hooks`.
- Measure the real refresh rate (`dumpsys display | grep mActiveSfDisplayMode`) — never assume 60 Hz.

## 2. Static checks (before any device run)
`python3 tools/qa/tools/static_checks.py src_copy` — every HIGH must be fixed or marked `qa-ok: RULE (reason)`.
`python3 tools/qa/tools/settings_inventory.py src_copy -o qa_runs/<run>/settings.csv` for setting wiring.

## 3. Device scenarios (README table): baseline FIRST on the installed build, same scenario after the fix
For each: `perf_reset` → `rec NAME 8` → `seq …` → `perf_dump NAME`; also `stress`, idle, `mem`,
`logcat -d -s VoltcapQA`. Pull to `qa_runs/<run>/` (voltcap-adb pull), stage to the cloud workspace, run
`frames.py` and LOOK at every contact sheet (`ev_*.png`). A transient flagged on a category switch / focus move is a
defect until explained. Run each layout (TestHooks `voltcap_test_layout` + `voltcap_test_preview true`, nothing saved).

## 4. Fix loop (per defect)
Evidence (sheet / numbers) → root cause in code (RULES.md patterns first) → minimal fix in src_copy → regression check
(static rule or scenario) → `sync_sources` → `gradle_start :app:compileReleaseKotlin` → `assembleRelease` →
`install_build release` → same scenario again → compare with the baseline. Never claim a fix without the re-run.
Do not hide glitches with delays; do not lower visual quality to win numbers.

## 5. Record
- `tools/qa/defects.json`: id, status (open/fixed/verified/wontfix), evidence path, root cause, files, fix, check.
- `tools/qa/REPORT.md`: tested / not tested, before → after numbers, open suspects, hardware limits.
- New pattern from Toni's report: why the pass missed it → new RULES.md rule + check → search the app for it.
- SUMMARY.md entry at the top (what changed, files, checked / not tested). Commit + push only builds that compiled
  and were checked on the device; never publish a release without Toni's approval.

## Never
Uninstall Voltcap, clear its data, touch saves/profiles, change charging or emulator settings, or report a test as
passed that wasn't run.
