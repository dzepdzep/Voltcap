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
| 2 | Cardboard box, Storage boxes, File select (1996) | Not started |
| 3 | Orbit deck (1995), Airy home (1999), Night menu (2006) | Not started |
| 4 | Popup review of all 33 layouts, Cube docking, return-from-game restore | Not started |
| 5 | Layout-styled Voltcap dialogs (opt-in, Standard by default) | Not started |

## A. Layout and popup matrix (phase 1 rows)

| Layout | Implementation | Popup | Visual check | Bugs found → fixed | Status |
|---|---|---|---|---|---|
| Pocket classic | Existing; A on the game panel now opens the game window (PR #12, `StyleActions.open` — Game window off: starts the game) | Paper screen: pale panel with pinstripes, sharp grey frame, monospace capitals, START / GAME INFO / BACK, accent from Layout options › Accent | Rendered on both screens: home, window open, D-pad moved inside the window (▼ ▶ to Options), B closed it and the focus was back on the game panel on the bottom screen | With "The game's colours" (the default) the window was re-coloured whole and the paper was lost → `keepsPalette`: the game's colour lights only the accent | Passed |
| Memory card (2000) | New (`StyleMemoryCard.kt`) | Smoky grey-blue glass, white words, lit button in the game's colour (or yellow with "The layout's own"), Start / Details / Back | Rendered: every option value (20 shots), PS2 + PS3 categories, window open / closed; recorded: moving, scrolling rows, L2 / R2, A / B — no flash, the piece flies back to its icon | Square achievement icons made it a cover grid → the game's clear logo first; icons too big / wide logos touching → smaller cells and narrower logo boxes; long titles pushed the sub line down → taller header; category name drawn twice → layout's own label (CategoryTitle.Native), hidden by R1 › Categories › Off; rows cut hard at the edges → faded | Passed |
| Case flow | New (`StyleCaseFlow.kt`) | Graphite glass beside the case that came forward, round buttons | Rendered: every option (15 shots); recorded: gliding 3 cases, back, A (case comes forward, window grows out of it, Inspect turns it to its back), B (case goes back) — smooth, nothing pops | Floor band's hard edge ran through the reflections → soft floor; the category name sat over the round shortcuts' ☰ in the top-left corner (R1 tile walk: Round shortcuts changed nothing) → name at the top middle | Passed |

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
