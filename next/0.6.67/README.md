# v0.6.67 staging — "Who's playing?" look overhaul (NOT part of 0.6.66)

Kept outside `app/` so it can never be built into the frozen 0.6.66 release candidate (fc1fe35). Nothing here is compiled.

## What it is
- Six looks for the profile picker (Settings › Home › Start-up › "Who's playing? look"): Aurora glass (default), Console classic,
  Neon arcade, Minimal OLED, Follow Voltcap theme, Midnight blue (the 0.6.66 look). Live thumbnails + "Preview the looks"
  (your profiles, L1 / R1 change the look, nothing switched or saved). Saved in `voltcap_features` › `picker_look` (backed up).
- Per-profile colour: 12 presets in the profile editor; reuses the existing `hue` field (a saved colour that is no preset stays offered).
  It tints only the ring, glow, "Playing" pill and accents.
- One row for 1–8 profiles (+ Add): pictures 76–128 dp (64 on compact screens), never smaller; scrolls with edge fades when
  they don't fit; names wrap between words / after a hyphen and shrink to 10 sp, never "…"; lock badge; clear "Playing" pill;
  pick animation (picked one grows, the others drop away); bigger PIN pad (56 dp keys, accent dots); D-pad + touch; Lite
  mode = no backdrop animation, shorter transitions.
- Unchanged: profile switching (`onPick(id)` → LauncherScreen → `viewModel.switchProfile(id)`), Profiles storage fields,
  PINs, pictures, startup modes, rollout transitions.
- QA hooks (only with the hooks file): `--es voltcap_test_pickerlook LOOK|off`,
  `--es voltcap_test_profiles "N[:long][:nophoto][:locked][:active=K]"|off` (sample profiles, ids 900+, nothing saved).

## Placeholders in the 0.6.66 tree
`app/src/main/java/com/shelf/launcher/ui/ProfileLooks.kt` and `app/src/test/java/com/shelf/launcher/ui/ProfilePicker445Test.kt`
(and their `src_copy/` / `project_copy/` copies) are EMPTY placeholders (only a package line) left from the device tests; they are
listed in `.gitignore` so they are never committed and they add nothing to a 0.6.66 build. Safe to delete by hand.
The patch below fills them and removes the two `.gitignore` lines.

## Apply when 0.6.67 starts (after 0.6.66 is published)
    cd C:\Users\User\AndroidStudioProjects\ShelfLauncher
    del app\src\main\java\com\shelf\launcher\ui\ProfileLooks.kt, app\src\test\java\com\shelf\launcher\ui\ProfilePicker445Test.kt
    git apply --ignore-whitespace next/0.6.67/profile_picker.patch
or copy `next/0.6.67/src_copy/*` over `src_copy/` and `next/0.6.67/project_copy/*` over `project_copy/`, then sync.
Then: `testDebugUnitTest` (ProfilePicker445Test), assembleRelease, device QA `tools/qa/device/pickerlooks.sh` + `pickerux.sh`.

## Open items
- `tools/qa/device/picker.sh` detects the picker by its deep-blue corner: Minimal OLED (black) reads UNKNOWN and Console
  classic (graphite) reads HOME. Update it (or force Midnight via `voltcap_test_pickerlook MIDNIGHT` in QA runs) before relying on it.
- Thor (dual screen): not tested. Settings thumbnails + "Preview the looks": see SUMMARY update 446 for what was checked on hardware.
