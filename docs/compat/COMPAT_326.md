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

Every layout × look was walked on the device in update 328 — section 6.

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
| S10 | Extended gameplay | 7 min of WWE All Stars with Twin desk + Game + info (update 328): in-game second screen live the whole time, Voltcap 306–342 MB and steady, no crash. Hours of play **UNTESTED** | **PASS** (minutes) |

## 4. Eden Duo

| # | Check | Status |
|---|---|---|
| E1 | Package verified from its source (`duo.gradle`: `dev.igawa6.edenduo`, dev builds `.dev`) | **PASS** |
| E2 | Launch intent: same `org.yuzu.yuzu_emu.activities.EmulationActivity`, game as a storage link through the game's folder (update 327: Eden's family refused Voltcap's shared link — "No game found") | **FIXED** — device: Voltcap sends the intent to Eden Duo; the same launch boots Mario Golf Super Rush in Eden. Eden Duo booting a game **UNTESTED**: it needs its own keys / firmware / games folder set up first |
| E3 | Detected as a Switch emulator (picker suggestion, emulator list, app kind) | **PASS** (automated) |
| E4 | Global: Settings › Emulators › Switch; new installs pick Eden Duo › Eden › Citron as the Switch default | **PASS** (automated) |
| E5 | Per game: Game settings › Emulator (new) — Console's emulator or any installed Switch emulator, for this game only | **PASS** (device: lists citron / Eden / Eden Duo, choice kept, used at launch; Display row now refreshes — FIXED in 327) |
| E6 | Second screen left to Eden Duo's companion: layout defaults to "Emulator uses both screens" on the built-in screen (a rule you set still wins); Voltcap's own second screen steps aside during the game (S1) | **PASS** (automated + S1/S7 on device) — **UNTESTED** with Eden Duo itself |
| E7 | Eden, Citron and the other Switch emulators unchanged | **PASS** (automated) |
| E8 | Eden Duo's screen swap needs a lasting file grant — the storage link (327) is one | **UNTESTED** (needs Eden Duo set up) |

## 5. R1 matrix by layout (default switches; per look in the CSV)

| Layout | Home tiles shown (every look) | Shown only with some looks | Greyed (with reason) | Hidden (does nothing here) |
|---|---|---|---|---|
| CLASSIC | background, badges, carousel, categories, corners, edithome, endless, gamelook, gamesize, headertight, herofit, layout, magnifier, musicspot, outline, popupstyle, progressbar, roundshort, rowpos, sort | artdrop (case/case_real); casestyle (case/case_real); discstyle (disc) | — | bartabs, cartlook, crosscut, pqtheme, shelflook, spinestyle, spotlight, twingap |
| TILES | background, badges, carousel, categories, corners, edithome, endless, gamelook, gamesize, herofit, layout, magnifier, musicspot, outline, popupstyle, progressbar, roundshort, rowpos, sort | artdrop (case/case_real); casestyle (case/case_real); discstyle (disc) | — | bartabs, cartlook, crosscut, headertight, pqtheme, shelflook, spinestyle, spotlight, twingap |
| HERO | background, badges, carousel, categories, corners, edithome, endless, gamelook, gamesize, layout, magnifier, musicspot, outline, popupstyle, roundshort, rowpos, sort | artdrop (case/case_real); casestyle (case/case_real); discstyle (disc) | — | bartabs, cartlook, crosscut, headertight, herofit, pqtheme, progressbar, shelflook, spinestyle, spotlight, twingap |
| GRID | background, badges, carousel, categories, corners, edithome, endless, gamelook, gamesize, herofit, layout, magnifier, musicspot, outline, popupstyle, progressbar, roundshort, sort | artdrop (case/case_real); casestyle (case/case_real); discstyle (disc) | — | bartabs, cartlook, crosscut, headertight, pqtheme, rowpos, shelflook, spinestyle, spotlight, twingap |
| TV | background, badges, carousel, categories, corners, edithome, endless, gamelook, gamesize, layout, magnifier, musicspot, outline, popupstyle, roundshort, sort | artdrop (case/case_real); casestyle (case/case_real); discstyle (disc) | — | bartabs, cartlook, crosscut, headertight, herofit, pqtheme, progressbar, rowpos, shelflook, spinestyle, spotlight, twingap |
| DASH | background, badges, categories, corners, edithome, endless, gamelook, gamesize, herofit, layout, magnifier, musicspot, popupstyle, progressbar, sort | artdrop (case/case_real); casestyle (case/case_real); discstyle (disc) | — | bartabs, carousel, cartlook, crosscut, headertight, outline, pqtheme, roundshort, rowpos, shelflook, spinestyle, spotlight, twingap |
| LIVE | badges, corners, edithome, endless, gamesize, layout, magnifier, musicspot, popupstyle, progressbar, sort | — | categories | artdrop, background, bartabs, carousel, cartlook, casestyle, crosscut, discstyle, gamelook, headertight, herofit, outline, pqtheme, roundshort, rowpos, shelflook, spinestyle, spotlight, twingap |
| SIDE | background, badges, carousel, categories, corners, edithome, endless, gamelook, gamesize, layout, magnifier, musicspot, popupstyle, progressbar, sort | artdrop (case/case_real); casestyle (case/case_real); discstyle (disc) | — | bartabs, cartlook, crosscut, headertight, herofit, outline, pqtheme, roundshort, rowpos, shelflook, spinestyle, spotlight, twingap |
| CROSS | background, badges, corners, crosscut, edithome, endless, gamelook, gamesize, herofit, layout, magnifier, musicspot, popupstyle, progressbar, roundshort, sort | artdrop (case/case_real); casestyle (case/case_real); discstyle (disc) | categories | bartabs, carousel, cartlook, headertight, outline, pqtheme, rowpos, shelflook, spinestyle, spotlight, twingap |
| SPOT | background, badges, corners, edithome, endless, gamelook, gamesize, layout, magnifier, musicspot, popupstyle, progressbar, roundshort, sort, spotlight | artdrop (case/case_real); casestyle (case/case_real); discstyle (disc) | categories | bartabs, carousel, cartlook, crosscut, headertight, herofit, outline, pqtheme, rowpos, shelflook, spinestyle, twingap |
| BAR | background, badges, bartabs, corners, edithome, endless, gamelook, gamesize, herofit, layout, magnifier, musicspot, popupstyle, progressbar, roundshort, sort | artdrop (case/case_real); casestyle (case/case_real); discstyle (disc) | categories | carousel, cartlook, crosscut, headertight, outline, pqtheme, rowpos, shelflook, spinestyle, spotlight, twingap |
| SHELF | casestyle, categories, corners, edithome, endless, gamesize, layout, magnifier, musicspot, popupstyle, roundshort, shelflook, sort, spinestyle | — | — | artdrop, background, badges, bartabs, carousel, cartlook, crosscut, discstyle, gamelook, headertight, herofit, outline, pqtheme, progressbar, rowpos, spotlight, twingap |
| HUB | background, badges, categories, corners, edithome, endless, gamesize, layout, magnifier, musicspot, popupstyle, progressbar, sort | — | — | artdrop, bartabs, carousel, cartlook, casestyle, crosscut, discstyle, gamelook, headertight, herofit, outline, pqtheme, roundshort, rowpos, shelflook, spinestyle, spotlight, twingap |
| RESUME | background, badges, categories, corners, edithome, endless, gamesize, layout, magnifier, musicspot, popupstyle, progressbar, sort | — | — | artdrop, bartabs, carousel, cartlook, casestyle, crosscut, discstyle, gamelook, headertight, herofit, outline, pqtheme, roundshort, rowpos, shelflook, spinestyle, spotlight, twingap |
| CHAN | badges, categories, corners, edithome, endless, gamesize, layout, magnifier, musicspot, popupstyle, progressbar, roundshort, sort | — | — | artdrop, background, bartabs, carousel, cartlook, casestyle, crosscut, discstyle, gamelook, headertight, herofit, outline, pqtheme, rowpos, shelflook, spinestyle, spotlight, twingap |
| CIRCUIT | badges, corners, edithome, endless, gamelook, gamesize, layout, magnifier, musicspot, popupstyle, roundshort, sort | artdrop (case/case_real); casestyle (case/case_real); discstyle (disc) | categories | background, bartabs, carousel, cartlook, crosscut, headertight, herofit, outline, pqtheme, progressbar, rowpos, shelflook, spinestyle, spotlight, twingap |
| LIBRARY | background, badges, corners, edithome, endless, gamelook, gamesize, herofit, layout, magnifier, musicspot, popupstyle, progressbar, roundshort, sort | artdrop (case/case_real); casestyle (case/case_real); discstyle (disc) | categories | bartabs, carousel, cartlook, crosscut, headertight, outline, pqtheme, rowpos, shelflook, spinestyle, spotlight, twingap |
| STACKS | background, badges, carousel, categories, corners, edithome, endless, gamelook, gamesize, herofit, layout, magnifier, musicspot, popupstyle, progressbar, roundshort, sort | artdrop (case/case_real); casestyle (case/case_real); discstyle (disc) | — | bartabs, cartlook, crosscut, headertight, outline, pqtheme, rowpos, shelflook, spinestyle, spotlight, twingap |
| TIMELINE | background, badges, categories, corners, edithome, endless, gamelook, gamesize, herofit, layout, magnifier, musicspot, popupstyle, progressbar, roundshort, sort | artdrop (case/case_real); casestyle (case/case_real); discstyle (disc) | — | bartabs, carousel, cartlook, crosscut, headertight, outline, pqtheme, rowpos, shelflook, spinestyle, spotlight, twingap |
| ARCADE | background, corners, edithome, endless, gamesize, herofit, layout, magnifier, musicspot, popupstyle, progressbar, roundshort, sort | — | categories | artdrop, badges, bartabs, carousel, cartlook, casestyle, crosscut, discstyle, gamelook, headertight, outline, pqtheme, rowpos, shelflook, spinestyle, spotlight, twingap |
| CONSOLES | background, badges, categories, corners, edithome, endless, gamelook, gamesize, herofit, layout, magnifier, musicspot, popupstyle, progressbar, roundshort, sort | artdrop (case/case_real); casestyle (case/case_real); discstyle (disc) | — | bartabs, carousel, cartlook, crosscut, headertight, outline, pqtheme, rowpos, shelflook, spinestyle, spotlight, twingap |
| DISCS | background, categories, corners, discstyle, edithome, endless, herofit, layout, magnifier, musicspot, popupstyle, roundshort, sort | — | — | artdrop, badges, bartabs, carousel, cartlook, casestyle, crosscut, gamelook, gamesize, headertight, outline, pqtheme, progressbar, rowpos, shelflook, spinestyle, spotlight, twingap |
| HERO_TILES | badges, categories, corners, edithome, gamesize, layout, magnifier, musicspot, popupstyle, progressbar, roundshort, sort | — | — | artdrop, background, bartabs, carousel, cartlook, casestyle, crosscut, discstyle, endless, gamelook, headertight, herofit, outline, pqtheme, rowpos, shelflook, spinestyle, spotlight, twingap |
| TWIN_DESK | background, badges, categories, corners, edithome, endless, herofit, layout, magnifier, musicspot, popupstyle, progressbar, roundshort, sort, twingap | — | — | artdrop, bartabs, carousel, cartlook, casestyle, crosscut, discstyle, gamelook, gamesize, headertight, outline, pqtheme, rowpos, shelflook, spinestyle, spotlight |
| TWIN_ICONS | background, badges, categories, corners, edithome, endless, herofit, layout, magnifier, musicspot, popupstyle, progressbar, sort, twingap | — | — | artdrop, bartabs, carousel, cartlook, casestyle, crosscut, discstyle, gamelook, gamesize, headertight, outline, pqtheme, roundshort, rowpos, shelflook, spinestyle, spotlight |
| TWIN_SHOWCASE | background, badges, categories, corners, edithome, endless, herofit, layout, magnifier, musicspot, popupstyle, progressbar, roundshort, sort, twingap | — | — | artdrop, bartabs, carousel, cartlook, casestyle, crosscut, discstyle, gamelook, gamesize, headertight, outline, pqtheme, rowpos, shelflook, spinestyle, spotlight |
| CRATE | background, badges, categories, corners, edithome, endless, gamesize, herofit, layout, magnifier, musicspot, popupstyle, roundshort, sort | — | — | artdrop, bartabs, carousel, cartlook, casestyle, crosscut, discstyle, gamelook, headertight, outline, pqtheme, progressbar, rowpos, shelflook, spinestyle, spotlight, twingap |
| CARDS | background, categories, corners, edithome, endless, gamesize, herofit, layout, magnifier, musicspot, popupstyle, roundshort, sort | — | — | artdrop, badges, bartabs, carousel, cartlook, casestyle, crosscut, discstyle, gamelook, headertight, outline, pqtheme, progressbar, rowpos, shelflook, spinestyle, spotlight, twingap |
| CUSTOM | badges, corners, edithome, endless, gamelook, layout, magnifier, musicspot, popupstyle, progressbar, roundshort, sort | artdrop (case/case_real); background (); casestyle (case/case_real); discstyle (disc); herofit () | categories | bartabs, carousel, cartlook, crosscut, gamesize, headertight, outline, pqtheme, rowpos, shelflook, spinestyle, spotlight, twingap |
| POCKET_CLASSIC | corners, edithome, endless, gamelook, layout, magnifier, musicspot, popupstyle, roundshort, sort | artdrop (case/case_real); casestyle (case/case_real); discstyle (disc) | categories | background, badges, bartabs, carousel, cartlook, crosscut, gamesize, headertight, herofit, outline, pqtheme, progressbar, rowpos, shelflook, spinestyle, spotlight, twingap |
| POCKET_RIBBON | badges, categories, corners, edithome, endless, gamelook, layout, magnifier, musicspot, popupstyle, progressbar, roundshort, sort | artdrop (case/case_real); casestyle (case/case_real); discstyle (disc) | — | background, bartabs, carousel, cartlook, crosscut, gamesize, headertight, herofit, outline, pqtheme, rowpos, shelflook, spinestyle, spotlight, twingap |
| GREENCORE | badges, corners, edithome, endless, gamelook, layout, magnifier, musicspot, popupstyle, progressbar, roundshort, sort | artdrop (case/case_real); casestyle (case/case_real); discstyle (disc) | categories | background, bartabs, carousel, cartlook, crosscut, gamesize, headertight, herofit, outline, pqtheme, rowpos, shelflook, spinestyle, spotlight, twingap |
| POCKET | badges, corners, edithome, endless, layout, magnifier, musicspot, popupstyle, pqtheme, progressbar, roundshort, sort | — | categories | artdrop, background, bartabs, carousel, cartlook, casestyle, crosscut, discstyle, gamelook, gamesize, headertight, herofit, outline, rowpos, shelflook, spinestyle, spotlight, twingap |
| CUBE | badges, corners, edithome, endless, gamelook, gamesize, layout, magnifier, musicspot, popupstyle, progressbar, roundshort, sort | artdrop (case/case_real); casestyle (case/case_real); discstyle (disc) | categories | background, bartabs, carousel, cartlook, crosscut, headertight, herofit, outline, pqtheme, rowpos, shelflook, spinestyle, spotlight, twingap |

## 6. Device walk and in-game checks (update 328, AYN Thor)

Automated results above are JVM tests; everything in this section was done on the Thor with adb (test hooks: a layout or
look is shown for the session only, nothing saved; screenshots in `screens\audit\` and `screens\` on the PC).

| # | Check | Result | Status |
|---|---|---|---|
| D1 | Every home layout × every game look (33 × 8 = 264), R1 opened and closed with B in each | No crash, no freeze, R1 closed every time | **PASS** |
| D2 | R1 tiles on the device vs the matrix | Logged per combination (test hook) and compared. Differences only where the Thor's switches differ from the defaults the matrix uses: Cartridge look (packs downloaded), Header tight (header off), Twin layouts' Background / Picture fit (Twin full top), Bar's Round shortcuts, Shelf's backdrop, Case stacks' Outline (carousel on). Plus one test-side ghost: the matrix still listed "Drawn media", removed in 325 → rule list fixed, test added | **PASS** (+ ghost **FIXED**) |
| D3 | Screenshots of every layout, both screens (cases, cartridges, discs, spines, covers) | Looked through: no clipping, missing art or broken 3D cases / spines; second screen followed the focused game | **PASS** |
| D4 | Saved settings after the walk | A restart brought your own layout and look back (Case stacks + Case) | **PASS** |
| D5 | Twin desk during a game (Game + info) | In-game second screen live: timer counting, pages turn, Widgets page (Now playing, System: clock, battery, temperature) live | **PASS** |
| D6 | Back home from the game | Twin desk's lower half straight back, live | **PASS** |
| D7 | Starting a game that is still open in the background (Running games / Quick resume) | **Was wrong:** the game came back on the lower screen (the info page opened there first and had the focus; the resume didn't say which screen). WWE All Stars' launcher opened on the lower screen, its game window restarted and the old one crashed; the info page then stayed on the lower screen with no game | **FIXED** — resume passes the game's screen. Device: launch → Home → launch again → the game came back on the top screen, same task, no restart, no crash |
| D8 | Asleep during a game (150 s, charging) | **Was wrong:** performance readings every second (CPU / GPU / FPS files), ≈ 4.3 s CPU in 150 s, a 35 MB + 59 MB garbage collection; the freeze watchdog read every thread's CPU every 5 s | **FIXED** — after: 0.6 s CPU on the worker threads, no file reads, no garbage collection, watchdog 0.09 s |
| D9 | Voltcap's CPU during a game, screen on | ≈ 2.3 % of one core on worker threads (readings for the overlay / tuner), ≈ 1.4 % main thread (the live second screen) | **PASS** |
| D10 | Memory during play | 306–342 MB, steady over 7 min of play | **PASS** |
| D11 | Leak check: all 33 layouts walked 5 times from a fresh start (focus moves in each) | Total memory rose 398 MB → 0.9–1.2 GB and peaked once at 1.56 GB, but the memory in use did not pile up: native memory in use (decoded pictures) went 705 → 304 MB and later 172 MB after Android's garbage collections — it is freed, only late. What stays high is memory the allocator keeps for reuse (≈ 600 MB "free" inside the native heap). No swap, no low-memory kills, no crash. Not a leak; a large, lazily-freed picture footprint (see note) | **PASS** (no leak) |
| D12 | Background videos over wallpapers | Code check only (item 6) | **PASS** (code) / device **UNTESTED** |
| D13 | Eden Duo booting a game | Needs Eden Duo's own setup (keys, firmware, games folder) | **UNTESTED** |
| D14 | Test previews leaving your settings alone | **Was wrong:** turning the in-game second screen's page during a preview saved the previewed layout (Twin desk) as yours | **FIXED** — device: page turned during a Twin desk preview, restart → your Case stacks; your layout was put back |

Note on D11: moving the focus on Twin desk / Hero carousel decodes a lot of pictures (hundreds of MB between collections),
and many of Voltcap's own picture caches are limited by count rather than size and don't react to Android asking for
memory. Fine on the Thor (16 GB); a byte budget for those caches would lower the peak on 4–6 GB handhelds. Not changed
here (it touches many systems) — a follow-up if you want it.


## 7. R1 tile walk: every tile pressed in every layout × look (update 329, AYN Thor)

How: test hook `voltcap_test_tilewalk_all "LAYOUTS|LOOKS|TILES"` (`ui/TileCheck.kt`) — inside Voltcap, every tile the R1
panel shows is pressed through the panel, the panel closed, both screens captured before / after (PixelCopy) and
compared outside the moving parts (two shots per state make the mask); a tile with no change gets its next value too.
A control (panel opened and closed, nothing pressed) runs first. Everything is a preview, put back after each tile.
Results: `<external files>/tilecheck/results.log`, pictures of every "no change" next to it; on the PC
`screens\tw\` (`results.log`, `results3.log`, `png_run2.tgz`).

| Pass | Scope | Presses | Changed | No change |
|---|---|---|---|---|
| 1 | 33 layouts × 8 looks, every home tile (192 × 108, 0.15 %) | 3,754 | 2,739 | 1,015 |
| 2 | 33 layouts × Covers / Cases, the tiles with "no change" in pass 1 (480 × 270, 0.04 %) | 599 | 440 | 159 |
| 3 | 19 layouts × Cartridges & discs / Discs in the N64 category: Cartridge shape, Disc for every game | 30 (stopped) | 2 | 28 |

**Fixed / hidden for that combination only** (status **FIXED** or **HIDDEN** / greyed with why):

| Tile | Where it did nothing | Now |
|---|---|---|
| Gaps | Twin showcase (changed Twin desk's gap) | **FIXED** — its own gap, from 4 dp |
| Magnifier | Twin desk / icons / showcase, Pocket classic / ribbon (their lower half owns the second screen) | greyed with why, unless Voltcap's normal second screen is opened from the layout |
| Music player | Style homes without Voltcap's status bar (Hero tiles, Channel grid, Spotlight Home menu, Pocket layouts, Twin showcase) | **FIXED** — the tap puts it at the top right on its own / off |
| Music player | Player taken off in Edit Home (✕) | **FIXED** — status bar ↔ off; greyed with why where there is no status bar |
| Endless | Twin showcase (its grid stops at the ends) | **HIDDEN** |
| Categories | Twin desk / showcase: "All categories" = "Current only" without the Additional category bar | **FIXED** — Current only ↔ Off there |
| Categories | Twin icons with its Category name taken off in Edit Home, no Additional category bar | greyed with why |
| Achievements | Voltcap row, Grid, Side menu, Case stacks with the carousel view (no chip there) | **HIDDEN** |
| Game size | Case stacks / Grid with the carousel view (the carousel has its own size) | **FIXED** — steps the carousel's size |
| Outline | Cartridges / discs (free-standing) on Voltcap row, Tile home, Hero carousel, carousel views | **HIDDEN** |
| Outline | Grid without "The game you're on: Glow" (not a case look) | greyed with why |
| Background | Style homes with the game's art across the screen (theme animation / still covered) | **FIXED** — the tap switches the clips (Art + videos ↔ Art only) |
| Round shortcuts | Off the Tile home with "By the row" | **FIXED** — corner / off |
| (label) | The category title showed the console's key ("PS2") | **FIXED** — "PlayStation 2" (short with Short console names) |

**No change on screen, by design or content (left as they are):** Corners (round the Categories screen's tiles);
Achievements (only on a game with achievements); console badges over a case that prints its console; the game window's
motion styles on style homes (fall back to the layout's window); Green Core (its menu page shows no games); Cross menu
(the walk sat on the apps column); Round shortcuts' corner ☰ under the pass-1 line (seen on the device); Hero carousel /
TV room / Game shelf controls not steady (their own motion).

**Open question — Cartridge shape and Disc for every game:** the setting changes, but a game with its own scraped
cartridge / disc photo shows that photo, so in the Thor's N64 and PS2 categories (all with photos) nothing on screen
changed. Only games without a photo take the pack shape / picked disc. Options: leave it, or have these two tiles also
switch the photos off (back to drawn pieces) — that changes how your library looks, so not done without asking.

**Flashing check (screen recordings, all 33 layouts, both screens, animations on):** moving along (◀ ▶), L2 / R2, A to
open the game window and B to close it — no flash or flicker found on any layout (frame-by-frame check for a change that
reverts within 300 ms). Green Core included (310's fix holds). The only quick changes: the category change's own fade
(Channel grid) and Hero tiles' end-of-row glow.

**Second-screen controls, pressed for real:** home (page arrows, Play) and during a PS2 game (ARMSX2): all ten in-game
pages in turn, the timer, the Music page's volume slider (Android's media volume went 1 → 2 → 1), the dock's All apps and
Chrome (opened on the second screen; the game kept the main screen) — **PASS**.
