# Voltcap 0.6.65 (draft — not published)

Changes since 0.6.64 (updates 423–433).

## Performance tuner / AutoTDP
- AutoTDP no longer cuts clocks hard when a game is already below its frame-rate target; over the power budget it eases down slowly and the
  tuner page says "target not reachable within the power budget".
- Power is only read on battery. While charging (or held at a charge limit) the battery current is no longer mistaken for the handheld's draw.
- The screen's refresh rate is never treated as a game's frame rate; the tuner page and overlay label it as the screen rate.
- Faster recovery after a heavy scene, no up-and-down "sawtooth", and every game starts with a fresh controller (no target carried over).
- Display-level frame counters must prove they follow the game before AutoTDP lowers clocks far; without a reliable reading it stops at 60 %.
- AutoTDP's own underclock no longer drops out during loading screens, but a lasting slowdown (e.g. a CPU-bound emulator) still raises it.

## Themes and home layouts
- Bright animated themes (Manga beat, Comic pages, Magical sparkle, Blue sky, Meadow, Pixel sky, Waterfall, Filmstrip, Badge bounce, Lobby,
  32-bit Frost / Gale / Alloy) keep titles, chips and the clock readable.
- Theme thumbnails show sample artwork; covers are fitted, not cropped.
- Twin desk shows whole game names (no "…"); Cover details' description no longer runs under the shelf; Disc station's Continue button fits on
  small screens.
- Per-layout game windows, readable expanded pages, Wii channel "More" page, Live tiles full-screen backdrop (updates 425–426).

## Known limitations
- Battery savings of AutoTDP are not measured yet (all tests ran plugged in).
- Popup modes per layout and dual-screen (Thor) were not re-verified for this build.
