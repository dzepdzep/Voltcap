# Update 330 — retro layout expansion: tracking matrices

Device: AYN Thor (adb 3140972f), two screens (main 1920 × 1080, second 1240 × 1080). Release builds installed in place.
"Rendered" means captured on the Thor with `screencap` and looked at; "recorded" means a `screenrecord` clip cut into
frames and looked at (and run through the flash finder). Nothing here is marked tested from source reading alone.

Layout names follow the no-trademarks rule (Toni picked "Original names"): Memory card (2000), Case flow, Cardboard box,
Storage boxes, File select (1996), Orbit deck (1995), Airy home (1999), Night menu (2006).

## Phase plan

| Phase | Content | State |
|---|---|---|
| 1 | Pocket classic popup (PR #12), Memory card (2000), Case flow | Done — built, tested on the Thor, pushed |
| 2 | Cardboard box, Storage boxes, File select (1996); layout-made game windows (box, file select, storage, memory card, case flow); PS2IODB icons | Done — built, tested on the Thor, pushed |
| 3 | Orbit deck (1995), Airy home (1999), Night menu (2006), each with its own window | Done — built, tested on the Thor, pushed |
| 4 | Popup review of all 33 layouts, Cube docking, return-from-game restore | Not started |
| 5 | Layout-styled Voltcap dialogs (opt-in, Standard by default) | Not started |

## A. Layout and popup matrix (phase 1 rows)

| Layout | Implementation | Popup | Visual check | Bugs found → fixed | Status |
|---|---|---|---|---|---|
| Pocket classic | Existing; A on the game panel now opens the game window (PR #12, `StyleActions.open` — Game window off: starts the game) | Paper screen: pale panel with pinstripes, sharp grey frame, monospace capitals, START / GAME INFO / BACK, accent from Layout options › Accent | Rendered on both screens: home, window open, D-pad moved inside the window (▼ ▶ to Options), B closed it and the focus was back on the game panel on the bottom screen | With "The game's colours" (the default) the window was re-coloured whole and the paper was lost → `keepsPalette`: the game's colour lights only the accent | Passed |
| Memory card (2000) | New (`StyleMemoryCard.kt`) | Smoky grey-blue glass, white words, lit button in the game's colour (or yellow with "The layout's own"), Start / Details / Back | Rendered: every option value (20 shots), PS2 + PS3 categories, window open / closed; recorded: moving, scrolling rows, L2 / R2, A / B — no flash, the piece flies back to its icon | Square achievement icons made it a cover grid → the game's clear logo first; icons too big / wide logos touching → smaller cells and narrower logo boxes; long titles pushed the sub line down → taller header; category name drawn twice → layout's own label (CategoryTitle.Native), hidden by R1 › Categories › Off; rows cut hard at the edges → faded | Passed |
| Case flow | New (`StyleCaseFlow.kt`) | Graphite glass beside the case that came forward, round buttons | Rendered: every option (15 shots); recorded: gliding 3 cases, back, A (case comes forward, window grows out of it, Inspect turns it to its back), B (case goes back) — smooth, nothing pops | Floor band's hard edge ran through the reflections → soft floor; the category name sat over the round shortcuts' ☰ in the top-left corner (R1 tile walk: Round shortcuts changed nothing) → name at the top middle | Passed |

### Phase 2 rows

| Layout | Implementation | Popup | Visual check | Bugs found → fixed | Status |
|---|---|---|---|---|---|
| Cardboard box | Rebuilt three times on Toni's corrections: upright fronts → spine rows (shelf-like) → top-down box drawn through a measured camera with generated cardboard materials (`StyleCardboardBox.kt`) | Insert sheet taped beside the case pulled out of the box (the window's piece) | Rendered + recorded: packed box, spine selected, 25 / 50 / 100 % out, window, reverse into the slot, other row, Box 2 by D-pad and swipe, last box partly filled | Window froze on the Thor (turned text) → flat first; the case flew out as a plain slab, then showed its whole box scan → its pictures readied while resting on it; a long swipe skipped a box → one per swipe; lifted case drifted off over the flap → stays over its slot | Passed |
| File select (1996) | Console names on the blocks | The block turns over, its back grows into the window | Rendered + recorded: page, selected, window (flip), reverse, other game; window open 15 s | FILE A–D labels → console; window buttons ran off the side when the case was out → flow onto two lines; the cover twice beside the case → hidden while the piece is out | Passed |
| Storage boxes | Pixel sprites in slots, pixel-framed box / header / preview | Stored-entry record with tabs and a pointer menu | Rendered + recorded: box, selected, record open, closing into the slot, other game | Record cramped beside a case piece → opens from the icon itself (piece via Inspect only); title over the tabs when narrow → hidden there | Passed |
| Memory card (window) | PS2IODB icons, animated | Browser info screen | Rendered + recorded | Games without an icon blank (logos never loaded) → fixed | Passed |
| Case flow (window) | — | The case out, its media beside it | Rendered + recorded | — | Passed (tools scroll into view below the buttons on the Thor) |

### Phase 3 rows

| Layout | Implementation | Popup | Visual check | Bugs found → fixed | Status |
|---|---|---|---|---|---|
| Orbit deck (1995) | New (`StyleOrbitDeck.kt`), refined on Toni's review: lit 3D discs, real disc scans, front/back orbit halves, focus lift, smooth Endless | The disc flies out of the orbit; rings, an arc of chrome keys and a glass console open round it (CD-player screen); closes back into the orbit | Rendered + recorded: home, move, Endless wrap, open (3 frames), focus, close, real discs; 1280 × 720 | Front disc covered the name → orbit measured against the name block; glow too strong → small core; header scrolled away on narrow → opens at the top; Endless rebuilt discs → never wrapped | Passed |
| Airy home (1999) | New (`StyleAiryHome.kt`): bowed row of rounded tiles, bubbles | Floating white panel, pixel LCD, round buttons | Rendered + recorded | — | Passed |
| Night menu (2006) | New (`StyleNightMenu.kt`): slanted bar list, moon over water | Status screen of staggered slanted panels | Rendered + recorded | Command bars ran into the stats panel → chips under the stats, narrower bars | Passed |

## B. Settings compatibility matrix (phase 1)

Legend: P = passed (rendered, the option visibly changes what it should), F = fixed and retested, N/A = not applicable.

### Memory card

| Option / tile | Values tried | Result |
|---|---|---|
| Icon size | Small / Medium / Large | P |
| Spacing | Tight / Normal / Roomy | P |
| Arrangement | Loose / Straight rows | P |
| Depth | Flat / Shallow / Deep | P (edge + turn of drawn icons; save icons keep their model) |
| The icon you're on | Turns (recorded) | P; Sways / Lifts only — not recorded separately |
| Background | Grey / Grey-blue / Your theme | P |
| Save icons | On (Mr Bean's own 3D icon and two-line title) / Off (its logo, its game name) | P |
| Title at the top right | On / Off | P |
| Button hints | On / Off | P |
| Floating icons | On | P (recorded); Off not recorded |
| R1 tiles shown | Popup style, Magnifier, Music player, Endless, Round shortcuts, Corners, Categories, Sort, Edit Home | Each changed the screen (tile walk, Cover + Case looks) |
| R1 tiles hidden | Game look, Game size, Console badges, Achievements place, Background (own backdrop) | Hidden on purpose — nothing to change there (unit test) |
| Categories tile | Off | F: the card's name now follows it |
| Second screen | Companion pages as on every style home | P |

### Case flow

| Option / tile | Values tried | Result |
|---|---|---|
| Cases either side show | Fronts / Spines | P |
| Curve | 0 / 45 / 100 | P |
| Spacing | 60 / 100 / 160 % | P |
| Case size | 70 / 100 / 120 % | P |
| Turn of the cases either side | 25° / 60° / 80° | P |
| Looking down on them | 0° / 5° / 14° | P |
| Speed | Normal (recorded) | P; Calm / Quick not recorded |
| Reflections | On / Off | P |
| Dark stage | On / Off (your theme's background) | P |
| Name under the cases | On / Off | P |
| R1 › Case for every game | Cover and Case looks | P (tile walk: 0.19 of the screen changed) |
| R1 › Round shortcuts | — | F (☰ was under the category name) |
| Endless | Last → first | P (tile walk) |
| Second screen | Companion pages | P |

### Not tested in phase 1

- The Retroid Pocket Classic / Flip 2 (not connected); other aspect ratios than the Thor's two screens.
- Settings persistence across an app restart was not checked per option (they are ordinary Features prefs, backed up with
  the rest); backup / restore not run.
- Memory card's Sways / Lifts only, Floating off; Case flow's Calm / Quick speeds — not recorded.

## Phase 4 — the older layouts

### Game windows (Settings › Game window, opened with `voltcap_test_window` on 25 To Life)

| Layout | Before | Now | Result |
|---|---|---|---|
| Arcade | plain window | neon marquee frame, PRESS START / INFO / BACK in caps | P |
| Consoles, Library, Timeline, Case stacks, Twin showcase | plain window | their own warm panels (theme / game colours keep working) | P |
| Disc station | plain window | silver panel, ▶ Play / Info / ■ Stop | P |
| Hero tiles | plain window | flat dark acrylic, square keys | F → P (facts broke a word per line beside the case) |
| Twin desk / Twin icons | plain window | the desk's white paper / the icon grid's pale shell | P |
| Crate, Cards | plain window | sleeve-note cream / card stock | P |
| Pocket ribbon | plain window | white paper, outlined keys | P |
| Cube | plain window | the cube's violet glass | P |

### R1 › Round shortcuts (☰), Top left and Top right

| Layouts | Before | Now | Result |
|---|---|---|---|
| Airy, Grid, File select, Green Core, Shelf, Timeline, Memory card, Orbit, Library, Hero, Circuit, Classic | in the corner, on the title / category / profile | in the status bar (left end / before the clock) | P |
| TV room | on the category name | before the clock (both spots) | P |
| Channel grid (no status bar) | under the category name / on the cloud button | its top row, before the clock | P |
| Pocket Quest | on the VOLTCAP plate / the clock | its bar, before the battery | P |
| Pocket classic / ribbon | on the name / the battery | the bar's left end, the name after it | P |
| Hero tiles (status pill) | on the pill's clock | just before the pill | P |
| Spotlight (Home menu, Row & menu) | on the profile / the clock | none — the shortcuts are its top row | P |
| Resume | none (its own shortcuts) | unchanged | P |

### Coming back from a game

| Layout | Kept | Result |
|---|---|---|
| Cube (cube / games, face, Hub part and zone) | page, ring, tip, section, zone, console | P (games page and the 3rd game back after a force-stop) |
| Green Core, Pocket Quest, Pocket classic | page, menu / zone, console, cursor | by the same snapshot (not run one by one) |
