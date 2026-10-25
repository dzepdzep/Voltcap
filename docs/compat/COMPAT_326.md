# Voltcap — compatibility report, update 326 (25 Oct 2026)

R1 Quick Settings × home layouts × game looks, Eden Duo, and the second screen. Built on 0.6.41 (commit ebcc767).
Device: AYN Thor `3140972f` (Android 13, 1920×1080 top / 1240×1080 bottom). Automated = JVM unit tests
(`R1Compat326Test`, `EdenDuo326Test`, all 232 tests pass). Device = checked with adb on the Thor, screenshots in
`screens\audit\` on the PC.

Status words: **PASS** works as intended · **FIXED** was wrong, fixed in 326 · **HIDDEN** the tile does nothing in that
combination and is hidden (its saved value is kept and comes back with a layout/look where it works) · **FAILED** still
wrong · **UNTESTED** not checked (said why).

## 1. How the R1 panel decides (unchanged design, now enforced)

`r1TileFit` (`ui/R1TileRules.kt`) is the one place that decides, for every tile, Show / Hide / Grey from the layout,
look, the switches the layout reads and the device. 326 adds:

- `R1_RULED_TILES` — every tile id with a rule. `R1Compat326Test.everyBuiltTileHasItsOwnRule` reads `QuickSettings.kt`
  and fails when a tile is added without a rule (that is how 325's **Outline** tile ended up "shown everywhere").
- An exhaustive automated sweep: **34 layouts × 8 looks × carousel on/off (where a carousel exists) × Spines look on/off ×
  Game window on/off × all 55 tiles** (≈ 120 000 decisions) — nothing throws, and the invariants hold (look tiles never
  on a layout with its own look, Outline only where the frame is drawn, Endless everywhere but Hero tiles, Popup style
  greyed with a reason when the game window is off…).
- The full matrix with default switches: `docs/compat/r1_matrix_326.csv` (layout × look × tile → SHOW / HIDE / GREY).

## 2. Findings and fixes (R1 / settings)

| # | Combination | What was wrong | Status |
|---|---|---|---|
| 1 | **Outline** tile (325) on every style home except Side menu / Case stacks with their carousel | Tile shown, but those layouts draw their own focus ring — pressing it changed nothing | **FIXED** → HIDDEN there (shown on Voltcap row, Tile home, Grid, Hero carousel, TV room, and Side menu / Case stacks while their carousel is on) |
| 2 | **Badges** on Hero carousel | Hero draws console badges since 317al, but the tile was hidden — badges on with no way to switch them there | **FIXED** → shown |
| 3 | **Badges / Achievements chip** on Pocket ribbon with its cartridge on | The cartridge is drawn with StyleArt (badge + chip), tiles hidden | **FIXED** → shown while Layout options › cartridge is on, hidden otherwise |
| 4 | **Endless** saved on, layout Hero tiles | Tile hidden ("no Endless") but the focus still went round from the last tile to the first (hidden setting still active) | **FIXED** (`moveFocus` asks `endlessApplies`) |
| 5 | **Case / Spine / Disc for every game** picked, then the look changed | Tile hidden in the new look, but the 3D cases in the game window, Inspect, carousels, launch throw still wore the picked console's case / spine | **FIXED** — `LookStyles` applies a pick only where its tile shows (same rule, `LookStyles.liveFor`); the pick is kept and comes back with its look |
| 6 | Background videos over a picked background | Checked: clips only replace Game art / Blurred art (313/314), never Your wallpaper, Animated theme, Black, or a picture picked for the game; Hero carousel the same; Gaming room's monitor and the game window's own clip are embedded by design | **PASS** (code; not reproduced on the device) |
| 7 | Every other tile × layout × look | Matrix below | **PASS** (automated); device spot-check below |
| 8 | Cartridge (cartlook) tile | Needs a downloaded cartridge pack — the JVM test has none, so the matrix shows it HIDE everywhere | **PASS** (rule unchanged); matrix value is a test-environment artefact |
| 9 | Layout Studio (CUSTOM) × Background / Picture fit | Its background comes from the layout file (org.json, only a stub in JVM tests) | **UNTESTED** (automated) — marked N/A in the CSV |

Device spot-check (Thor, Case stacks + Case + media look, carousel on): every tile the panel showed matches the matrix
row STACKS/CASE (Outline shown because the carousel is on). **PASS**.

Not done on the device (time): walking every layout and look by hand. The rules are exercised exhaustively in the JVM
sweep; visual checks of every case / cartridge / disc / spine / 3D animation per look were **UNTESTED** on the device in
this update.

## 3. Second screen (AYN Thor)

| # | Check | Result | Status |
|---|---|---|---|
| S1 | Root cause of "second screen stops working during games" | Reproduced with adb: Voltcap's second-screen window (a Presentation, always above apps on its display) stayed on screen while a game covered Voltcap. Its Compose drawing is paused while Voltcap is stopped, so it showed a **frozen picture** (clock stuck at 13:24 at 13:26, video stopped, taps did nothing) — and it covered the live in-game second screen Voltcap had opened under it (CompanionActivity: pages, widgets, music, volume). Same when another app covered Voltcap ("sometimes inside Voltcap") | **FIXED** — it steps aside while Voltcap is stopped and comes back (same window) when Voltcap is in front |
| S2 | In-game second screen live (WWE All Stars, Game + info) | Timer 0:11 → 1:25 → 1:52, clock moving, tap on ▶ changed the page | **PASS** (device) |
| S3 | Back to Voltcap after the game | Home second screen back at once, live (clock moving), page changed in-game kept | **PASS** (device) |
| S4 | Sleep / wake | Hidden while asleep, back and live after wake | **PASS** (device) |
| S5 | Android closing the window by itself (display metrics / state change, display gone a moment) | The home kept believing it was up (Twin layouts kept their bottom half "on" an empty screen) until a restart. Now `SecondScreenShown` goes false at once and the window is rebuilt (at most 5 times in 30 s; only while Voltcap is in front) | **FIXED** (code); density change via `wm density -d 4` didn't make Android close it on this firmware, so the rebuild path was **UNTESTED** on the device |
| S6 | In-game second screen stuck black-and-white | The second screen's dimming kept running while hidden and its grey art is shared with the in-game screen, which nothing woke | **FIXED** (`SecondDim.hold`) — device: still in colour after 1:40 of play (it went grey after ~1 min before) |
| S7 | Dual-screen emulators (Azahar, 3DS) | Their own bottom screen on top, nothing of Voltcap over it | **PASS** (device, Naruto Powerful Shippuden) |
| S8 | "Leave as is" layout | The second screen now shows what's under Voltcap's window (the Thor's own launcher) during the game instead of a frozen Voltcap picture | **PASS** (behaviour change — use Game + info for live Voltcap pages during play) |
| S9 | Display reconnection (TV out) | Display add/remove already rebuilt the window; unchanged | **UNTESTED** (Thor has no hot-plug second screen) |
| S10 | Extended gameplay (hours) | **UNTESTED** |

## 4. Eden Duo

| # | Check | Status |
|---|---|---|
| E1 | Package verified from its source (`duo.gradle`: `dev.igawa6.edenduo`, dev builds `.dev`) | **PASS** |
| E2 | Launch intent: same `org.yuzu.yuzu_emu.activities.EmulationActivity`, game as the intent's content URI (its `EmulationFragment` resolves a URI whose name ends in .nsp/.xci…) | **PASS** (automated + source) — **UNTESTED** on the device: Eden Duo isn't installed on the Thor |
| E3 | Detected as a Switch emulator (picker suggestion, emulator list, app kind) | **PASS** (automated) |
| E4 | Global: Settings › Emulators › Switch; new installs pick Eden Duo › Eden › Citron as the Switch default | **PASS** (automated) |
| E5 | Per game: Game settings › Emulator (new) — Console's emulator or any installed Switch emulator, for this game only | **PASS** (compiled; UI not walked on the device) |
| E6 | Second screen left to Eden Duo's companion: layout defaults to "Emulator uses both screens" on the built-in screen (a rule you set still wins); Voltcap's own second screen steps aside during the game (S1) | **PASS** (automated + S1/S7 on device) — **UNTESTED** with Eden Duo itself |
| E7 | Eden, Citron and the other Switch emulators unchanged | **PASS** (automated) |
| E8 | Eden Duo's screen swap (game moved to the bottom screen) needs a lasting file grant; Voltcap's default "Shared file link" is one-time, so Eden Duo keeps the game on the main screen. Setting Switch's link to "Storage link" (Settings › Emulators › Switch) with the folder given to Eden Duo allows it | **UNTESTED** |

## 5. R1 matrix by layout (default switches; per look in the CSV)

| Layout | Home tiles shown (every look) | Shown only with some looks | Greyed (with reason) | Hidden (does nothing here) |
|---|---|---|---|---|
| CLASSIC | background, badges, carousel, categories, corners, edithome, endless, gamelook, gamesize, headertight, herofit, layout, magnifier, musicspot, outline, popupstyle, progressbar, roundshort, rowpos, sort | artdrop (case/case_real); casestyle (case/case_real); discstyle (disc); drawnmedia (cartridge_disc/disc) | — | bartabs, cartlook, crosscut, pqtheme, shelflook, spinestyle, spotlight, twingap |
| TILES | background, badges, carousel, categories, corners, edithome, endless, gamelook, gamesize, herofit, layout, magnifier, musicspot, outline, popupstyle, progressbar, roundshort, rowpos, sort | artdrop (case/case_real); casestyle (case/case_real); discstyle (disc); drawnmedia (cartridge_disc/disc) | — | bartabs, cartlook, crosscut, headertight, pqtheme, shelflook, spinestyle, spotlight, twingap |
| HERO | background, badges, carousel, categories, corners, edithome, endless, gamelook, gamesize, layout, magnifier, musicspot, outline, popupstyle, roundshort, rowpos, sort | artdrop (case/case_real); casestyle (case/case_real); discstyle (disc); drawnmedia (cartridge_disc/disc) | — | bartabs, cartlook, crosscut, headertight, herofit, pqtheme, progressbar, shelflook, spinestyle, spotlight, twingap |
| GRID | background, badges, carousel, categories, corners, edithome, endless, gamelook, gamesize, herofit, layout, magnifier, musicspot, outline, popupstyle, progressbar, roundshort, sort | artdrop (case/case_real); casestyle (case/case_real); discstyle (disc); drawnmedia (cartridge_disc/disc) | — | bartabs, cartlook, crosscut, headertight, pqtheme, rowpos, shelflook, spinestyle, spotlight, twingap |
| TV | background, badges, carousel, categories, corners, edithome, endless, gamelook, gamesize, layout, magnifier, musicspot, outline, popupstyle, roundshort, sort | artdrop (case/case_real); casestyle (case/case_real); discstyle (disc); drawnmedia (cartridge_disc/disc) | — | bartabs, cartlook, crosscut, headertight, herofit, pqtheme, progressbar, rowpos, shelflook, spinestyle, spotlight, twingap |
| DASH | background, badges, categories, corners, edithome, endless, gamelook, gamesize, herofit, layout, magnifier, musicspot, popupstyle, progressbar, sort | artdrop (case/case_real); casestyle (case/case_real); discstyle (disc); drawnmedia (cartridge_disc/disc) | — | bartabs, carousel, cartlook, crosscut, headertight, outline, pqtheme, roundshort, rowpos, shelflook, spinestyle, spotlight, twingap |
| LIVE | badges, corners, edithome, endless, gamesize, layout, magnifier, musicspot, popupstyle, progressbar, sort | — | categories | artdrop, background, bartabs, carousel, cartlook, casestyle, crosscut, discstyle, drawnmedia, gamelook, headertight, herofit, outline, pqtheme, roundshort, rowpos, shelflook, spinestyle, spotlight, twingap |
| SIDE | background, badges, carousel, categories, corners, edithome, endless, gamelook, gamesize, layout, magnifier, musicspot, popupstyle, progressbar, sort | artdrop (case/case_real); casestyle (case/case_real); discstyle (disc); drawnmedia (cartridge_disc/disc) | — | bartabs, cartlook, crosscut, headertight, herofit, outline, pqtheme, roundshort, rowpos, shelflook, spinestyle, spotlight, twingap |
| CROSS | background, badges, corners, crosscut, edithome, endless, gamelook, gamesize, herofit, layout, magnifier, musicspot, popupstyle, progressbar, roundshort, sort | artdrop (case/case_real); casestyle (case/case_real); discstyle (disc); drawnmedia (cartridge_disc/disc) | categories | bartabs, carousel, cartlook, headertight, outline, pqtheme, rowpos, shelflook, spinestyle, spotlight, twingap |
| SPOT | background, badges, corners, edithome, endless, gamelook, gamesize, layout, magnifier, musicspot, popupstyle, progressbar, roundshort, sort, spotlight | artdrop (case/case_real); casestyle (case/case_real); discstyle (disc); drawnmedia (cartridge_disc/disc) | categories | bartabs, carousel, cartlook, crosscut, headertight, herofit, outline, pqtheme, rowpos, shelflook, spinestyle, twingap |
| BAR | background, badges, bartabs, corners, edithome, endless, gamelook, gamesize, herofit, layout, magnifier, musicspot, popupstyle, progressbar, roundshort, sort | artdrop (case/case_real); casestyle (case/case_real); discstyle (disc); drawnmedia (cartridge_disc/disc) | categories | carousel, cartlook, crosscut, headertight, outline, pqtheme, rowpos, shelflook, spinestyle, spotlight, twingap |
| SHELF | casestyle, categories, corners, edithome, endless, gamesize, layout, magnifier, musicspot, popupstyle, roundshort, shelflook, sort, spinestyle | — | — | artdrop, background, badges, bartabs, carousel, cartlook, crosscut, discstyle, drawnmedia, gamelook, headertight, herofit, outline, pqtheme, progressbar, rowpos, spotlight, twingap |
| HUB | background, badges, categories, corners, edithome, endless, gamesize, layout, magnifier, musicspot, popupstyle, progressbar, sort | — | — | artdrop, bartabs, carousel, cartlook, casestyle, crosscut, discstyle, drawnmedia, gamelook, headertight, herofit, outline, pqtheme, roundshort, rowpos, shelflook, spinestyle, spotlight, twingap |
| RESUME | background, badges, categories, corners, edithome, endless, gamesize, layout, magnifier, musicspot, popupstyle, progressbar, sort | — | — | artdrop, bartabs, carousel, cartlook, casestyle, crosscut, discstyle, drawnmedia, gamelook, headertight, herofit, outline, pqtheme, roundshort, rowpos, shelflook, spinestyle, spotlight, twingap |
| CHAN | badges, categories, corners, edithome, endless, gamesize, layout, magnifier, musicspot, popupstyle, progressbar, roundshort, sort | — | — | artdrop, background, bartabs, carousel, cartlook, casestyle, crosscut, discstyle, drawnmedia, gamelook, headertight, herofit, outline, pqtheme, rowpos, shelflook, spinestyle, spotlight, twingap |
| CIRCUIT | badges, corners, edithome, endless, gamelook, gamesize, layout, magnifier, musicspot, popupstyle, roundshort, sort | artdrop (case/case_real); casestyle (case/case_real); discstyle (disc); drawnmedia (cartridge_disc/disc) | categories | background, bartabs, carousel, cartlook, crosscut, headertight, herofit, outline, pqtheme, progressbar, rowpos, shelflook, spinestyle, spotlight, twingap |
| LIBRARY | background, badges, corners, edithome, endless, gamelook, gamesize, herofit, layout, magnifier, musicspot, popupstyle, progressbar, roundshort, sort | artdrop (case/case_real); casestyle (case/case_real); discstyle (disc); drawnmedia (cartridge_disc/disc) | categories | bartabs, carousel, cartlook, crosscut, headertight, outline, pqtheme, rowpos, shelflook, spinestyle, spotlight, twingap |
| STACKS | background, badges, carousel, categories, corners, edithome, endless, gamelook, gamesize, herofit, layout, magnifier, musicspot, popupstyle, progressbar, roundshort, sort | artdrop (case/case_real); casestyle (case/case_real); discstyle (disc); drawnmedia (cartridge_disc/disc) | — | bartabs, cartlook, crosscut, headertight, outline, pqtheme, rowpos, shelflook, spinestyle, spotlight, twingap |
| TIMELINE | background, badges, categories, corners, edithome, endless, gamelook, gamesize, herofit, layout, magnifier, musicspot, popupstyle, progressbar, roundshort, sort | artdrop (case/case_real); casestyle (case/case_real); discstyle (disc); drawnmedia (cartridge_disc/disc) | — | bartabs, carousel, cartlook, crosscut, headertight, outline, pqtheme, rowpos, shelflook, spinestyle, spotlight, twingap |
| ARCADE | background, corners, edithome, endless, gamesize, herofit, layout, magnifier, musicspot, popupstyle, progressbar, roundshort, sort | — | categories | artdrop, badges, bartabs, carousel, cartlook, casestyle, crosscut, discstyle, drawnmedia, gamelook, headertight, outline, pqtheme, rowpos, shelflook, spinestyle, spotlight, twingap |
| CONSOLES | background, badges, categories, corners, edithome, endless, gamelook, gamesize, herofit, layout, magnifier, musicspot, popupstyle, progressbar, roundshort, sort | artdrop (case/case_real); casestyle (case/case_real); discstyle (disc); drawnmedia (cartridge_disc/disc) | — | bartabs, carousel, cartlook, crosscut, headertight, outline, pqtheme, rowpos, shelflook, spinestyle, spotlight, twingap |
| DISCS | background, categories, corners, discstyle, edithome, endless, herofit, layout, magnifier, musicspot, popupstyle, roundshort, sort | — | — | artdrop, badges, bartabs, carousel, cartlook, casestyle, crosscut, drawnmedia, gamelook, gamesize, headertight, outline, pqtheme, progressbar, rowpos, shelflook, spinestyle, spotlight, twingap |
| HERO_TILES | badges, categories, corners, edithome, gamesize, layout, magnifier, musicspot, popupstyle, progressbar, roundshort, sort | — | — | artdrop, background, bartabs, carousel, cartlook, casestyle, crosscut, discstyle, drawnmedia, endless, gamelook, headertight, herofit, outline, pqtheme, rowpos, shelflook, spinestyle, spotlight, twingap |
| TWIN_DESK | background, badges, categories, corners, edithome, endless, herofit, layout, magnifier, musicspot, popupstyle, progressbar, roundshort, sort, twingap | — | — | artdrop, bartabs, carousel, cartlook, casestyle, crosscut, discstyle, drawnmedia, gamelook, gamesize, headertight, outline, pqtheme, rowpos, shelflook, spinestyle, spotlight |
| TWIN_ICONS | background, badges, categories, corners, edithome, endless, herofit, layout, magnifier, musicspot, popupstyle, progressbar, sort, twingap | — | — | artdrop, bartabs, carousel, cartlook, casestyle, crosscut, discstyle, drawnmedia, gamelook, gamesize, headertight, outline, pqtheme, roundshort, rowpos, shelflook, spinestyle, spotlight |
| TWIN_SHOWCASE | background, badges, categories, corners, edithome, endless, herofit, layout, magnifier, musicspot, popupstyle, progressbar, roundshort, sort, twingap | — | — | artdrop, bartabs, carousel, cartlook, casestyle, crosscut, discstyle, drawnmedia, gamelook, gamesize, headertight, outline, pqtheme, rowpos, shelflook, spinestyle, spotlight |
| CRATE | background, badges, categories, corners, edithome, endless, gamesize, herofit, layout, magnifier, musicspot, popupstyle, roundshort, sort | — | — | artdrop, bartabs, carousel, cartlook, casestyle, crosscut, discstyle, drawnmedia, gamelook, headertight, outline, pqtheme, progressbar, rowpos, shelflook, spinestyle, spotlight, twingap |
| CARDS | background, categories, corners, edithome, endless, gamesize, herofit, layout, magnifier, musicspot, popupstyle, roundshort, sort | — | — | artdrop, badges, bartabs, carousel, cartlook, casestyle, crosscut, discstyle, drawnmedia, gamelook, headertight, outline, pqtheme, progressbar, rowpos, shelflook, spinestyle, spotlight, twingap |
| CUSTOM | badges, corners, edithome, endless, gamelook, layout, magnifier, musicspot, popupstyle, progressbar, roundshort, sort | artdrop (case/case_real); background (); casestyle (case/case_real); discstyle (disc); drawnmedia (cartridge_disc/disc); herofit () | categories | bartabs, carousel, cartlook, crosscut, gamesize, headertight, outline, pqtheme, rowpos, shelflook, spinestyle, spotlight, twingap |
| POCKET_CLASSIC | corners, edithome, endless, gamelook, layout, magnifier, musicspot, popupstyle, roundshort, sort | artdrop (case/case_real); casestyle (case/case_real); discstyle (disc); drawnmedia (cartridge_disc/disc) | categories | background, badges, bartabs, carousel, cartlook, crosscut, gamesize, headertight, herofit, outline, pqtheme, progressbar, rowpos, shelflook, spinestyle, spotlight, twingap |
| POCKET_RIBBON | badges, categories, corners, edithome, endless, gamelook, layout, magnifier, musicspot, popupstyle, progressbar, roundshort, sort | artdrop (case/case_real); casestyle (case/case_real); discstyle (disc); drawnmedia (cartridge_disc/disc) | — | background, bartabs, carousel, cartlook, crosscut, gamesize, headertight, herofit, outline, pqtheme, rowpos, shelflook, spinestyle, spotlight, twingap |
| GREENCORE | badges, corners, edithome, endless, gamelook, layout, magnifier, musicspot, popupstyle, progressbar, roundshort, sort | artdrop (case/case_real); casestyle (case/case_real); discstyle (disc); drawnmedia (cartridge_disc/disc) | categories | background, bartabs, carousel, cartlook, crosscut, gamesize, headertight, herofit, outline, pqtheme, rowpos, shelflook, spinestyle, spotlight, twingap |
| POCKET | badges, corners, edithome, endless, layout, magnifier, musicspot, popupstyle, pqtheme, progressbar, roundshort, sort | — | categories | artdrop, background, bartabs, carousel, cartlook, casestyle, crosscut, discstyle, drawnmedia, gamelook, gamesize, headertight, herofit, outline, rowpos, shelflook, spinestyle, spotlight, twingap |
| CUBE | badges, corners, edithome, endless, gamelook, gamesize, layout, magnifier, musicspot, popupstyle, progressbar, roundshort, sort | artdrop (case/case_real); casestyle (case/case_real); discstyle (disc); drawnmedia (cartridge_disc/disc) | categories | background, bartabs, carousel, cartlook, crosscut, headertight, herofit, outline, pqtheme, rowpos, shelflook, spinestyle, spotlight, twingap |
