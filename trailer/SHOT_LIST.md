# Voltcap trailer: what to record

Put the clips in `trailer\clips\` with the number at the front (e.g. `03_discs.mp4`).
Any resolution or aspect ratio works: 4:3 clips from the Odin or RP Classic are framed to 16:9 automatically.

**Music:** the trailer uses our own track, **Volt · bright synth arps** (`bgm_volt`, 88 BPM, cut on the beat).
**Ending:** our intro, with `voltcap_intro.ogg`. It holds on the VOLTCAP wordmark at the end.

## How to record

- **Screen recorder:** use the handheld's own (Quick Settings › Screen record), with **no audio** and **touches off**.
  - You can also use adb: `adb shell screenrecord --bit-rate 20000000 /sdcard/01.mp4` (Ctrl+C stops it).
  - For the Thor's lower screen, find its id with `adb shell dumpsys SurfaceFlinger --display-id`, then add `--display-id <id>`.
- **Before recording:**
  - Turn on Do Not Disturb so no notifications pop in.
  - Lite effects off, 60 fps on.
  - Pick games with nice art.
- **While recording:**
  - Hold still for about 1 second at the start and end of every clip.
  - Move **slowly and evenly**, about one step every second. Fast scrolling looks messy in a trailer.
  - 6–15 seconds per clip is plenty. I only use 2–5 s of each.
- **More clips are fine.** If one looks bad, just record it again with the same number.

## Shots (in trailer order)

| # | File | What to show | Used |
|---|------|--------------|------|
| 01 | `01_iconbar` | Icon bar home (PS5 style), hero art behind. Move right through 5–6 games so the tile pops out each time. | ~5 s |
| 02 | `02_crossmenu` | Cross menu: step through 3–4 categories (the slide), then down a few games. | ~3 s |
| 03 | `03_discs` | A mixed row (Continue playing) with discs spinning, cartridges bouncing and cases turning. Go slowly across it. | ~5 s |
| 04 | `04_spotlight` | Spotlight or Hero carousel: full-screen art, change game 2–3 times. | ~3 s |
| 05 | `05_themes` | Settings › Theme: flip through 5–6 animated themes (the live preview). | ~5 s |
| 06 | `06_colours` | Colours following the game: move between 3–4 games with very different art so the colours glide. | ~3 s |
| 07 | `07_achievements` | Achievements: open a game's trophies (the new view), scroll a bit; an unlock / MASTERED animation if you can (Replay latest). | ~4 s |
| 08 | `08_music` | Music: the Spotify player or a lyric theme (Karaoke / Neon lyrics) playing a song. | ~4 s |
| 09 | `09_dualscreen` | Thor second screen: the case flip on the details page, or the achievements board. A phone video of both screens is great here too. | ~4 s |
| 10 | `10_extra` | Your pick: TV room, Save Vault, Edit Home dragging the player, Quick Settings tiles… | ~3 s |
| 99 | `99_intro` | The intro: Settings › Home › Animations › Startup intro › **Preview**. Start recording first, then tap Preview. | end |

Then tell Claude "clips are in". The trailer gets cut, a draft is sent for review, and the Reddit description is written with you.
