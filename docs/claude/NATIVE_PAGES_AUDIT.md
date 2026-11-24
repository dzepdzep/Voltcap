# Native pages audit - which layouts really have their own Details / Achievements / Options (682z, 22 Nov 2026)

Owner, 22 Nov: "Audit whether these truly have their own complete Details, Achievements and Options designs, or only shared
page bodies inside native-looking shells. Track this separately so the 31/53 count does not misrepresent completion."

Method: code read of every scene in `WinPageFlow.NATIVE_PAGE_SCENES` (branch claude/v0674-native-pages 2763331). A page body is
**shared** when the scene draws it with `ctx.pageFor(...)` / `ctx.pageBody(...)` - GameWindow's `WindowDetails`,
`WindowTrophies`, `WindowOptions` (one design for every layout, only tinted by the layout's `WinLook` / `NativePageHost`).
A page is **own** when the layout draws its rows itself (WinPages*.kt on the NativePageKit).

## Result

| Class | Layouts | Count |
|---|---|---|
| A. Own Details + Achievements + Options (P2 batches 1-4) | Record crate, Classic, Console hub, Resume & discover, TV room, Game magazine, Cinema, Arcade cabinets, Cube menu, Spotlight, Terminal, Dashboard, Library, Card binder, Side menu, Icon bar, Arctic hub, Storage boxes | **18** |
| B. Native shell, SHARED page bodies (own tabs / frame / motion, shared rows) | Night status (NIGHT_STATUS), Blades (BLADE_GUIDE), Tiles + Grid (TILE_CARD), Game shelf + Case stacks + Console stack + Coverflow details (INDEX_CARD, ShelfFamilyPages), Twin desk + Twin icons + Twin showcase (TWIN_HINGE, TwinTouchPages), Disc station (DISC_PANEL) | **12** |
| C. Shared expanded page (no native pages yet) | the other layouts with a scene (Box, File select, Memory card, Case flow, Orbit, Airy, Bubbles, Camera, Panels, Shop, Channel grid, Green core, Pocket x3, Art library, Live, Circuit, Timeline, Hero + Hero tiles, Layout Studio) and Cross (XMB options) | **23** |

So the honest count against the original requirement ("every popup its own Details / Achievements / Options"):
**18 / 53 complete**, 12 / 53 shell-only (counted as NOT complete), 23 / 53 not started. The earlier "31 / 53" counted
class A + class B (+ Storage twice) and over-states completion; it is retired.

Notes per class-B layout (all draw `ctx.pageFor` inside their own frame):
- Night status: `NightPagePanel` - its tab strip and panel, shared rows inside.
- Blades: `BladePageBlade` - the blade with the page name in light type, shared rows inside.
- Tiles / Grid: `TileLiftPage` - the lifted tile's header, shared rows (NativePageHost at x1.3).
- Shelf family (4): `ShelfFamilyPages` - the index card's frame, shared rows.
- Twin family (3): `TwinTouchPages` - pages on the touch screen, shared rows (NativePageHost).
- Disc station: `DiscPlayerPanel` - the deck's LCD tracks name the pages, shared rows inside.
- Storage boxes was class B until 682y (its sheets held the shared page); it is class A since 682y.

## Queue (P2 batch 5 onward)
Class B first where the shell already sets the identity (smaller risk, one family per batch can cover several layouts):
1. Shelf family (4 layouts, one scene) 2. Twin family (3) 3. Tiles + Grid (2) 4. Night status, Blades, Disc station.
Then class C by family.

## Update 682aa (batch 5)
Shelf family (4) and Twin family (3) now draw their own pages: class A 25, class B 5 (Night status, Blades, Tiles, Grid, Disc
station), class C 23. Batch 5 is implemented only - hardware NOT TESTED, not visually approved.

## Update 682ad (batch 6)
Tile home and Grid draw their own pages: class A 27, class B 3 (Night status, Blades, Disc station), class C 23. Batches 5-6
implemented only - hardware: batch 5 in the 22 Nov evening capture (gk10 / gr10), batch 6 NOT TESTED; none visually approved.

## Update 682ae (batch 7)
Night menu and Blades draw their own pages: class A 29, class B 1 (Disc station), class C 23. Batches 6-7 hardware NOT TESTED.

## Update 731a (class C)
Cardboard box draws its own sheets (CONTENTS / CHECKLIST / SERVICE on the insert's paper): class A 36, class B 1 (Disc station),
class C 16. Implemented only - hardware NOT TESTED (HW-28), not visually approved.

## Update 732a (class C)
File select draws its own screens (DETAILS / STARS / OPTIONS on the file's inset panel): class A 37, class B 1 (Disc station),
class C 15. Implemented only - hardware NOT TESTED (HW-28), not visually approved.

## Update 733a (class C)
Case flow draws its own pages (DETAILS / EXTRAS / SETUP in the picture's glass well): class A 38, class B 1 (Disc station),
class C 14. Implemented only - hardware NOT TESTED (HW-28), not visually approved.

## Update 734b (class C)
Orbit deck draws its own console screens (DATA / TRACKS / COMMANDS in the console): class A 39, class B 1 (Disc station),
class C 13. Implemented only - hardware NOT TESTED (HW-28), not visually approved.
