# Category styles (matrix P5) - the 17 looks of the category screen

Reconciled 25 Nov 2026 (759a, cloud) from the code (`Features.PlatformView`, `ui/PlatformBrowser.kt`, `ui/CategoryLooks.kt`,
`ui/CategoryLooks462.kt`, `ui/PlatformBrowserSettings.kt`), the unit tests and the hardware evidence of project doc
`claude/SPRINT527_20261112.md`. One source of truth: the enum `Features.PlatformView` (pref `pb_view`, default Tiles), guarded by
`CategoryLooksInventory759aTest` (exactly 17, each drawn, each explained under its picker name, the arrows in the explanation =
the keys that move it).

States are kept apart: **implemented** = in the code; **unit** = a unit test touches that look's own code; **hardware** = the last
handheld evidence; **owner** = visual approval. Emulator: none of the 17 has an emulator run (the trial covers home layouts).

| # | Look (picker name) | Enum | Drawn by | Keys | Follows these home layouts | Unit | Hardware (last) | Owner |
|---|---|---|---|---|---|---|---|---|
| 1 | Tiles | TILES | PlatformBrowser grid | its grid | every layout not listed below | 527 (name + count) | KONKR x1.0 / x1.3, 12 Nov, older build | no |
| 2 | Slides | SLIDES | SlidesView | ◀ ▶ / swipe | - | 678j (text), 762a (count + maker whole) | KONKR 12 Nov | no |
| 3 | Wall | WALL | WallView | ◀ ▶ | Arcade | 527 (slices under the header) | KONKR 12 Nov; header fix (Sprint 528) not re-run | no |
| 4 | Grid | GRID | GridView | ◀ ▶ ▲ ▼ (2 rows) + Start move | Grid, Hub, Live | 764a (▲ ▼ stay in the column) | KONKR 12 Nov | no |
| 5 | System Stage | STAGE | StageLook | ◀ ▶ | Shelf, Stacks, Caseflow, Hero, Spot | 527 (plate wraps), 760a (plate ends at the object's foot) | KONKR 12 Nov | no |
| 6 | System Blades | BLADES | BladesLook | ◀ ▶ | Blades, Side, Cross, Night | 759a | KONKR 12 Nov | no |
| 7 | Collector's Drawers | DRAWERS | DrawersLook | ◀ ▶ ▲ ▼ (4 across) | Box, Storage, Crate, Cards | 527 (whole names) | KONKR 12 Nov (names on two lines fixed + re-checked) | no |
| 8 | Pocket Worlds | WORLDS | WorldsLook | ◀ ▶ | Pocket, Camera, Green Core | - | KONKR 12 Nov | no |
| 9 | Archive 1994 | ARCHIVE | ArchiveLook | ◀ ▶ | Library, Airy, Resume | - | KONKR 12 Nov | no |
| 10 | Channel Surf | CHANNEL | ChannelLook | ▲ ▼ | TV room | 527 (CRT roll) | Flip 2 before / after + KONKR 12 Nov | no |
| 11 | Generations | GENERATIONS | GenerationsLook | ◀ ▶ (release order) | Timeline, Console stack | 761a (info block ends at the handheld lane) | KONKR 12 Nov | no |
| 12 | Constellation | CONSTELLATION | ConstellationLook | ◀ ▶ | Orbit, Circuit, Cube, Bubbles | 766a (info bounded above the legend, tap in the light opens) | KONKR 12 Nov | no |
| 13 | System Vault | VAULT | VaultLook | ▲ ▼ | Memory card, File select, Disc station | 763a (reader text wraps) | KONKR 12 Nov | no |
| 14 | Pocket Dashboard | DASHBOARD | DashboardLook (Thor: row on the lower screen) | ◀ ▶ | Pocket classic, Pocket ribbon, Twin showcase / desk / icons, Panels, Dash | 759a, 765a (preview text inside the panel) | KONKR 12 Nov; Thor two-screen NOT TESTED | no |
| 15 | Channel Menu | WIICHAN | WiiChannelLook | ◀ ▶ ▲ ▼ (4 across) | Channel grid (also its themed picker) | - | KONKR 12 Nov | no |
| 16 | Cinema | CINEMA | CinemaLook | ◀ ▶ | Cinema (also its themed picker) | 527 (cross-fade, art under header) | Flip 2 before / after + KONKR 12 Nov | no |
| 17 | Arctic Panes | ARCTIC | ArcticLook | ▲ ▼ | Arctic hub (also its themed picker) | 527 (cross-fade) | Flip 2 before / after + KONKR 12 Nov | no |

"Follows these home layouts" applies only with Settings > Categories > Category screen > Follow home layout on
(`Features.followLookFor`); "themed picker" = `pbThemed` (on by default) for Channel grid, Cinema, Arctic hub (+ Pocket Quest,
Green Core, Pocket classic).

## Counts (evidence only)
- Implemented 17 / 17 (code). Look-specific unit checks 10 / 17 (527, 678j, 759a, 760a Stage, 761a Generations); the 759a inventory guard covers all 17. Emulator 0 / 17.
- Hardware: all 17 opened on KONKR x1.0 / x1.3 on 12 Nov (Sprint 527, branch feature/popup-native) - NOT re-run on the dev line
  since; the dev line has changed the looks since (Sprint 528 Wall header, rule 11 FullText in LookTitle, 759a key notes).
- Owner visual approval 0 / 17.

## Found and fixed in this reconciliation (759a)
- System Blades and Pocket Dashboard: the Settings explanation said "▲ ▼" but both step with ◀ ▶ only (`lookNavOf` = ROW; ▲ ▼
  do nothing there). The explanations now say ◀ ▶, and Pocket Dashboard's no longer calls its row of round icons "a compact
  list". Guarded by `CategoryLooksInventory759aTest.theArrowsInANoteAreTheKeysThatMoveTheLook` (fails on the old text).

## Third-priority polish (760a on)
- 760a System Stage: at x1.3 on the KONKR (640 x 427 dp) a two-line name over two lines of facts made the museum plate
  ~100 dp tall against ~83 dp of plinth front, so it rose over the plinth's top and the object's foot. The plate now ends at
  the object's foot (`stagePlateRoom`); the name gives way first (shrinks to its readable 10 sp, then slides), the facts keep
  two lines. `StagePlateRoom760aTest` 2/2 (source half fails on 759a). Hardware NOT TESTED (HW-24 760a).
- 761a Generations: the block for the system you're on (generation line, name, facts) grew upwards without a bound; on the
  KONKR the room under the handheld lane is ~82 dp, a two-line name at x1.3 needs ~125 dp, so it reached ~40 dp into the
  focused handheld and its year. It now ends at the lane's foot (`generationsInfoRoom`, a 56 dp minimum on short phone
  screens); the name gives way first (shrink to 10 sp, then slide), the generation line and the facts (two lines) stay whole.
  `GenerationsInfoRoom761aTest` 3/3 (source half fails on 760a). Hardware NOT TESTED (HW-24 761a).
- 762a Slides: the game count and the maker / year line were plain one-line Texts in fixed boxes (KONKR maker box 192 dp;
  "MICROSOFT 2005" at x1.3 ~179 dp, a scraped maker or an editor-shrunk box clipped). Both through FullText now (shrink, then
  slide; no reserve, boxes unchanged). `SlidesFactsFullText762aTest` 2/2. Hardware NOT TESTED (HW-24 762a).

## Remaining (from Sprint 527 + this pass)
- 763a System Vault: the reader screen's "READING" header and facts line wrap onto a reserved second line before they
  shrink / slide (KONKR text width ~202 dp, x1.3 monospace needs ~280); Wall's game count through FullText.
  `VaultWallText763aTest` 3/3. Hardware NOT TESTED (HW-24 763a).
- 764a Grid: ▲ ▼ stepped through the list (▲ on a top card -> previous column's bottom, ▼ on the last card of an odd count
  -> the first card, ◀ from the first card -> the middle column). gridStep: ▲ ▼ stay in the column (stop at top / bottom /
  an empty slot), ◀ ▶ keep the row and wrap round the columns (picked up: stop at the ends); a picked-up card's hint shows
  "◀ ▶ ▲ ▼". Text room checked: KONKR x1.3 card ~141 dp high, content ~107 dp - fits. `GridSteps764aTest` 5/5. Hardware NOT
  TESTED (HW-24 764a).
- 765a Pocket Dashboard: the preview (46 % of the height, ~196 dp on the KONKR) had a 144 dp text column inside 26 dp
  padding; facts + a two-line 30 sp name + counts + last played needed ~170 dp at x1.0 and ~170-200 dp at x1.3 - the
  centred column overflowed the rounded panel and its facts (top) and last-played line (bottom) were cut. Now: 18 dp top /
  bottom padding, the column bounded (dashboardTextRoom, 160 dp), the name weighted (gives way first, line height 1.133 em
  follows the shrink) at a largest size the room allows (dashboardNameSp: 30 sp at x1.0, ~24 sp at x1.3 on the KONKR, 30 sp
  on the Thor's top screen), facts one line (FullText). `DashboardTextRoom765aTest` 3/3. Hardware NOT TESTED (HW-24 765a).
- 766a Constellation: the info column under the console in the light started at 0.64 of the height and grew down
  unbounded - on the KONKR (427 dp) it had ~100 dp above the legend; a two-line 24 sp name + wrapped facts + favourites
  needed ~155 dp at x1.3 (~55 dp behind the legend), ~103 dp at x1.0. Now bounded (constellationInfoRoom = 0.36 h - 54 dp,
  min 44 dp), the name weighted (gives way first: shrinks to 14.4 sp, then slides), facts (two lines) and favourites
  through FullText ("1 favourite" singular). Touch: the console in the light and its info open it (lookTap; before, only
  the dimmed ring node did). Keys / rule 14 unchanged. `ConstellationInfoRoom766aTest` 3/3. Hardware NOT TESTED (HW-24 766a).
- Third-priority polish: all 17 looked at once (760a-766a); the hardware pass below is what remains (Wall's header
  collision was fixed in Sprint 528 - not re-checked on hardware).
- One focused hardware pass of the 17 on the current dev build (KONKR x1.0 / x1.3; open each, ◀ ▶ / ▲ ▼ per the Keys column,
  A opens the category, B back) - queued as part of HW-24.
- Owner visual review of the 17 (none approved yet).
- Real cover art: covers in the QA fixtures are placeholders (Sprint 527 note).
