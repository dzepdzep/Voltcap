# R1 Focus position - 53-layout compatibility matrix (723a, owner Task 2 / 23 Nov 22:43 item 4)

**What it is:** a separate R1 tile, **Focus**, for where the game you're on stays across the screen while you browse (not the
vertical Row position tile). Press (A): the next choice; hold: the list of choices with the current one marked. It reuses the
existing settings - no new preference, nothing migrated: the Voltcap row's `focus_position` and Tile home's `tile_focus`
(the same values Settings › Layout options › Game you're on writes). A tile new since the owner arranged R1 goes after the
saved tiles (R1_APPENDED), so the saved R1 order is unchanged; it can also be pinned to L1 ("Focus position").

**How the audit was done (cloud, code only):** `FocusPosition` / `TileFocus` are read only by `ui/GameDock.kt` (Voltcap row)
and `ui/TileHome.kt` (Tile home) - plus their category-name / header alignment in LauncherScreen. No other layout has a
horizontal focus anchor setting; offering the tile there would be a dead control (r1TileFit hides it). Where a layout's look
was not checked by eye this says "code"; handheld behaviour of every row: NOT TESTED.

| # | Layout | id | Focus tile | Why |
|---|---|---|---|---|
| 1 | Voltcap row | `CLASSIC` | SUPPORTED | Settings › Home › Layout options › Game you're on (Left / Middle), read by GameDock. Greyed while the carousel look is on (always middle) or "Covers · first one wide" (game 1 pinned left). |
| 2 | Tile home | `TILES` | SUPPORTED | Tile home's Game you're on (Moves to the edges / Left / Middle / Right), read by TileHome. Greyed while its carousel look is on (always middle). |
| 3 | Hero carousel | `HERO` | NOT OFFERED | Its R1 Row tile switches the covers' side - a cover side, not a focus position (owner: "Hero's cover side is not focus position"). |
| 4 | Grid | `GRID` | NOT OFFERED | Two-dimensional grid; no horizontal anchor setting. Carousel look centres. |
| 5 | TV room | `TV` | NOT OFFERED | Games beside the TV; carousel look centres; no anchor setting. |
| 6 | Dashboard | `DASH` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 7 | Live tiles | `LIVE` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 8 | Side menu | `SIDE` | NOT OFFERED | Vertical menu; carousel look centres; no anchor setting. |
| 9 | Cross menu | `CROSS` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 10 | Spotlight | `SPOT` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 11 | Icon bar | `BAR` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 12 | Game shelf | `SHELF` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 13 | Console hub | `HUB` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 14 | Resume & discover | `RESUME` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 15 | Channel grid | `CHAN` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 16 | Circuit board | `CIRCUIT` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 17 | Library panel | `LIBRARY` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 18 | Case stacks | `STACKS` | NOT OFFERED | Case stacks; carousel look centres; no anchor setting. |
| 19 | Timeline | `TIMELINE` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 20 | Arcade cabinets | `ARCADE` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 21 | Console stack | `CONSOLES` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 22 | Disc station | `DISCS` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 23 | Hero tiles | `HERO_TILES` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 24 | Twin desk | `TWIN_DESK` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 25 | Twin icons | `TWIN_ICONS` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 26 | Twin showcase | `TWIN_SHOWCASE` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 27 | Record crate | `CRATE` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 28 | Card binder | `CARDS` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 29 | Layout Studio | `CUSTOM` | NOT OFFERED | Layout Studio: parts placed by the user's layout file. |
| 30 | Pocket classic | `POCKET_CLASSIC` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 31 | Pocket ribbon | `POCKET_RIBBON` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 32 | Green Core (2001) | `GREENCORE` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 33 | Pocket Quest | `POCKET` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 34 | Cube menu | `CUBE` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 35 | Memory card (2000) | `MEMCARD` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 36 | Case flow | `CASEFLOW` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 37 | Cardboard box | `BOX` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 38 | Storage boxes | `STORAGE` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 39 | File select (1996) | `FILESELECT` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 40 | Orbit deck (1995) | `ORBIT` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 41 | Airy home (1999) | `AIRY` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 42 | Night menu (2006) | `NIGHT` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 43 | Blades (2005) | `BLADES` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 44 | Bubble home (2011) | `BUBBLES` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 45 | Pocket camera (1998) | `CAMERA` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 46 | Panel dashboard (2008) | `PANELS` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 47 | Game magazine | `MAGAZINE` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 48 | Rental shop | `SHOP` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 49 | Terminal | `TERMINAL` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 50 | Cinema | `CINEMA` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 51 | Arctic hub | `ARCTIC` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 52 | Coverflow details | `COVERDETAILS` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |
| 53 | Artwork library | `ARTLIB` | NOT OFFERED | Own fixed browsing; no horizontal focus-anchor setting in the code. |

**Totals:** SUPPORTED 2 / 53 (Voltcap row, Tile home); NOT OFFERED 51 / 53.

**Evidence:** FocusPosTile723aTest 4/4 (cloud unit: steps / round trip / direct pick, shown on exactly these 2 of the 53,
greyed with carousel and first-one-wide), R1Compat326Test 6/6, Batch491Test 6/6, TileWalk329Test 7/7 (cloud).
**Not tested:** on a handheld - focus movement with each choice, Endless, animations, category-label alignment, 0 / 1 / 2 /
many games, controller + touch on the tile and its hold list (HW-24 723a). Owner approval: not asked yet (no design change -
it exposes existing settings).
