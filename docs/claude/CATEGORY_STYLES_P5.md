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
| 2 | Slides | SLIDES | SlidesView | ◀ ▶ / swipe | - | 678j (text) | KONKR 12 Nov | no |
| 3 | Wall | WALL | WallView | ◀ ▶ | Arcade | 527 (slices under the header) | KONKR 12 Nov; header fix (Sprint 528) not re-run | no |
| 4 | Grid | GRID | GridView | ◀ ▶ ▲ ▼ (2 rows) + Start move | Grid, Hub, Live | - | KONKR 12 Nov | no |
| 5 | System Stage | STAGE | StageLook | ◀ ▶ | Shelf, Stacks, Caseflow, Hero, Spot | 527 (plate wraps) | KONKR 12 Nov | no |
| 6 | System Blades | BLADES | BladesLook | ◀ ▶ | Blades, Side, Cross, Night | 759a | KONKR 12 Nov | no |
| 7 | Collector's Drawers | DRAWERS | DrawersLook | ◀ ▶ ▲ ▼ (4 across) | Box, Storage, Crate, Cards | 527 (whole names) | KONKR 12 Nov (names on two lines fixed + re-checked) | no |
| 8 | Pocket Worlds | WORLDS | WorldsLook | ◀ ▶ | Pocket, Camera, Green Core | - | KONKR 12 Nov | no |
| 9 | Archive 1994 | ARCHIVE | ArchiveLook | ◀ ▶ | Library, Airy, Resume | - | KONKR 12 Nov | no |
| 10 | Channel Surf | CHANNEL | ChannelLook | ▲ ▼ | TV room | 527 (CRT roll) | Flip 2 before / after + KONKR 12 Nov | no |
| 11 | Generations | GENERATIONS | GenerationsLook | ◀ ▶ (release order) | Timeline, Console stack | - | KONKR 12 Nov | no |
| 12 | Constellation | CONSTELLATION | ConstellationLook | ◀ ▶ | Orbit, Circuit, Cube, Bubbles | - | KONKR 12 Nov | no |
| 13 | System Vault | VAULT | VaultLook | ▲ ▼ | Memory card, File select, Disc station | - | KONKR 12 Nov | no |
| 14 | Pocket Dashboard | DASHBOARD | DashboardLook (Thor: row on the lower screen) | ◀ ▶ | Pocket classic, Pocket ribbon, Twin showcase / desk / icons, Panels, Dash | 759a | KONKR 12 Nov; Thor two-screen NOT TESTED | no |
| 15 | Channel Menu | WIICHAN | WiiChannelLook | ◀ ▶ ▲ ▼ (4 across) | Channel grid (also its themed picker) | - | KONKR 12 Nov | no |
| 16 | Cinema | CINEMA | CinemaLook | ◀ ▶ | Cinema (also its themed picker) | 527 (cross-fade, art under header) | Flip 2 before / after + KONKR 12 Nov | no |
| 17 | Arctic Panes | ARCTIC | ArcticLook | ▲ ▼ | Arctic hub (also its themed picker) | 527 (cross-fade) | Flip 2 before / after + KONKR 12 Nov | no |

"Follows these home layouts" applies only with Settings > Categories > Category screen > Follow home layout on
(`Features.followLookFor`); "themed picker" = `pbThemed` (on by default) for Channel grid, Cinema, Arctic hub (+ Pocket Quest,
Green Core, Pocket classic).

## Counts (evidence only)
- Implemented 17 / 17 (code). Look-specific unit checks 10 / 17 (527, 678j, 759a); the 759a inventory guard covers all 17. Emulator 0 / 17.
- Hardware: all 17 opened on KONKR x1.0 / x1.3 on 12 Nov (Sprint 527, branch feature/popup-native) - NOT re-run on the dev line
  since; the dev line has changed the looks since (Sprint 528 Wall header, rule 11 FullText in LookTitle, 759a key notes).
- Owner visual approval 0 / 17.

## Found and fixed in this reconciliation (759a)
- System Blades and Pocket Dashboard: the Settings explanation said "▲ ▼" but both step with ◀ ▶ only (`lookNavOf` = ROW; ▲ ▼
  do nothing there). The explanations now say ◀ ▶, and Pocket Dashboard's no longer calls its row of round icons "a compact
  list". Guarded by `CategoryLooksInventory759aTest.theArrowsInANoteAreTheKeysThatMoveTheLook` (fails on the old text).

## Remaining (from Sprint 527 + this pass)
- Third-priority polish never started: Stage, Generations, Slides, Vault, Grid / Dashboard / Constellation (Wall's header
  collision was fixed in Sprint 528 - not re-checked on hardware).
- One focused hardware pass of the 17 on the current dev build (KONKR x1.0 / x1.3; open each, ◀ ▶ / ▲ ▼ per the Keys column,
  A opens the category, B back) - queued as part of HW-24.
- Owner visual review of the 17 (none approved yet).
- Real cover art: covers in the QA fixtures are placeholders (Sprint 527 note).
