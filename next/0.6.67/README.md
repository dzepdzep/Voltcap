# v0.6.67 staging — NOT part of 0.6.66 (nothing here is compiled)

Kept outside `app/` so it can never be built into the frozen 0.6.66 release candidate. Updates 446–447.

## What is in it (one patch for all three: `profile_picker.patch`)
1. **"Who's playing?" looks (update 446, owner decisions in 447).** Six looks: Aurora glass, Console classic, Neon arcade,
   Minimal OLED, Follow Voltcap theme, Midnight blue (the 0.6.66 look). Settings › Home › Animations: thumbnails +
   "Preview the looks" (L1 / R1), in Settings search ("Who"). Saved in `voltcap_features` › `picker_look` (backed up).
   **Default (owner, update 447):** an existing install keeps Midnight blue; a new install starts on Aurora glass. Chosen once
   at start only when nothing is saved (`Features.firstPickerLook(FirstRun.isDone)`), then saved — a saved choice is never
   overwritten. Per-profile colour presets (reuse `hue`), whole-word names (never "…"), 76–128 dp pictures in one scrolling
   row, pick animation, bigger PIN pad, D-pad + touch, Lite mode. Switching / storage unchanged.
2. **D144 — QA picker detection by the app's own mark (update 447).** With the test-hooks file, Voltcap writes
   `<external files>/voltcap_qa_screen` = `PICKER <pid>` while a picker is up, `HOME <pid>` otherwise (and at every start).
   `tools/qa/device/picker.sh` reads it (display awake + Voltcap in front + same pid; HOME also needs a lit screen) — no
   background colours. Old builds (no mark) fall back to the Midnight-blue corner. Device check: `pickdetect.sh`.
3. **Dual-screen return button (update 447, D146).** The way back from the second screen's dashboard to a two-screen layout
   (Twin desk / icons / showcase, Pocket classic / ribbon) is a compact button at the end of the dashboard's header row
   (32 dp visible, 44 dp touch / controller target, layout-styled; "Back to <layout>" for TalkBack) instead of the wide pill
   in the bottom-left corner that covered the dock. The header keeps room for it (`SecondScreenHome(headerEnd = …)`), so it
   never overlaps tabs, dock, apps or page arrows; overlays (drawer, app picker, big player) draw over it. Same controller id
   (`twin-back`), same action (`TwinScreens.dashboard = false`), no setting changed. QA hook `--es voltcap_test_twindash on|off`;
   device check `twinback.sh`.

4. **D147 — controller onto the dashboard (update 448).** With the dashboard open, ▼ on any two-screen layout goes past its hidden
   lower part (HomeReach, then the dashboard); the return button hands the controller back to the layout (`SecondPad.leave()`).
   `StyleHomes.kt` + `TwinScreens.kt`; TwinBack447Test.

## Placeholders in the 0.6.66 tree
`app/src/main/java/com/shelf/launcher/ui/ProfileLooks.kt`, `app/src/test/java/com/shelf/launcher/ui/ProfilePicker445Test.kt` and
`TwinBack447Test.kt` (and their `src_copy/` / `project_copy/` copies) are EMPTY placeholders (only a package line); they are listed in `.gitignore`,
never committed, and add nothing to a 0.6.66 build. Safe to delete by hand.

## Apply when 0.6.67 starts (after 0.6.66 is published)
    cd C:\Users\User\AndroidStudioProjects\ShelfLauncher
    del app\src\main\java\com\shelf\launcher\ui\ProfileLooks.kt, app\src\test\java\com\shelf\launcher\ui\ProfilePicker445Test.kt, app\src\test\java\com\shelf\launcher\ui\TwinBack447Test.kt
    git apply --ignore-whitespace next/0.6.67/profile_picker.patch
(the patch also removes the three `.gitignore` lines). Or copy `next/0.6.67/src_copy/*` over `src_copy/` and
`next/0.6.67/project_copy/*` over `project_copy/`, then sync. **Also copy the same files into `project_copy/` (or delete the stale
`project_copy` copies of SUMMARY.md / .gitignore / defects.json): `sync_sources` copies project_copy over the root and put older
docs back once (update 447).** Then: testDebugUnitTest (ProfilePicker445Test, TwinBack447Test), `python3 tools/qa/selftest/test_qa.py`,
assembleRelease, device QA `pickdetect.sh`, `pickerlooks.sh`, `pickerux.sh`, `twinback.sh` (Thor).

## Notes
- The Odin 2 Portal saved `picker_look = AURORA` during update 446's Settings-preview test, so 0.6.67 would show Aurora there
  (a saved choice is kept). If you want Midnight there, pick it in Settings (or QA: `--es voltcap_test_pref picker_look=MIDNIGHT`).
- `PICKER_LOOKS_446.pdf`: before / after and all six looks on the GT78 and the Odin. Update 447 sheet: `TWIN_BACK_447.pdf`.
