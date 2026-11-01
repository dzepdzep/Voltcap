# Voltcap 0.6.65 (release candidate — not published)

Changes since 0.6.64 (updates 423–434). Only items verified on a device or by tests are listed as fixed.

## Performance tuner / AutoTDP (unit-tested; frame / clock behaviour checked plugged in on the Odin 2 Portal)
- AutoTDP no longer cuts clocks hard when a game is already below its frame-rate target; over the power budget it eases down slowly and the
  tuner page says "target not reachable within the power budget".
- Power is only read on battery: charging current is no longer mistaken for the handheld's draw.
- The screen's refresh rate is never treated as a game's frame rate (tuner page and overlay label it as the screen rate).
- Faster recovery after a heavy scene, no up-and-down "sawtooth"; each game starts with a fresh controller.
- Without a reliable frame-rate reading AutoTDP stops at 60 % instead of lowering clocks further.
- AutoTDP's own underclock no longer drops out during short loading screens, while a lasting slowdown still raises it.
- Battery savings have NOT been measured yet (all tests ran plugged in).

## Themes and home layouts (checked on the Odin 2 Portal and the GT78)
- Bright animated themes and Lobby keep titles, chips and the clock readable (shade behind the home's writing).
- Twin desk: whole game names, nothing cut under the card; Disc station: the play button fits on small screens (symbol only when narrow).
- Cover details' description no longer runs under the shelf of cases.
- Theme thumbnails show sample artwork; covers are fitted, not cropped; per-layout game windows and readable expanded pages (423–428).

## Known limitations
- Hero layout shortens names / summary with "…" on small screens; Disc station's game window shows a generic disc; the Cardboard box looks
  empty with only two games; on Android 12 some animated themes fall back to a dark frame.
- Popup transitions, dual-screen (Thor) and round-shortcut positions were not re-verified for this build.
