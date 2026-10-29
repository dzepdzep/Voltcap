# VoltCap theme and layout audit — source baseline 29 October 2026

> **Scope:** static inspection of the public GitHub `main` source at `1816b9be`, existing QA records and status matrix. No claim that all looks have been viewed or tested on actual hardware. This is a backlog and patch review, **not** a release approval.

## Inventory

- **53 selectable home layouts**, catalogued by `HomeLayout` in `data/AppearanceStore.kt`.
- **203 built-in scene/palette theme variants**, declared in `ui/theme/ShelfColors.kt` (`AppTheme`). Many share scene motifs with different colours. Source presence does not establish visual parity or performance.
- **10 implemented category-selection looks** (`ui/CategoryLooks.kt`, `ui/PlatformBrowser.kt`): System Stage, System Blades, Collector's Drawers, Pocket Worlds, Archive 1994, Channel Surf, Generations, Constellation, System Vault, Pocket Dashboard. The 404 QA matrix marks them implemented but **not reverified on hardware**.
- **System Grid, Side Banner, Retro Timeline, Compact List**: not present as their own category-selection look names in the 404 status matrix. Evaluate overlap before implementing.

## Home layouts — complete source inventory

Legend: `Recent manual QA` means the recorded coverage has a hand-checked run on Thor/RP; `Needs full review` does not mean a defect was proven. All 53 still need a cross-layout controller, popup and small-category matrix.

| ID | Visible label | QA summary |
|---|---|---|
| `CLASSIC` | Voltcap row | Needs full review (some subtests may have passed) |
| `TILES` | Tile home | Recent manual QA (limited scenarios) |
| `HERO` | Hero carousel | Recent manual QA (limited scenarios) |
| `GRID` | Grid | Needs full review (some subtests may have passed) |
| `TV` | TV room | Needs full review (some subtests may have passed) |
| `DASH` | Dashboard | Recent manual QA (limited scenarios) |
| `LIVE` | Live tiles | Recent manual QA (limited scenarios) |
| `SIDE` | Side menu | Recent manual QA (limited scenarios) |
| `CROSS` | Cross menu | Needs full review (some subtests may have passed) |
| `SPOT` | Spotlight | Needs full review (some subtests may have passed) |
| `BAR` | Icon bar | Needs full review (some subtests may have passed) |
| `SHELF` | Game shelf | Needs full review (some subtests may have passed) |
| `HUB` | Console hub | Needs full review (some subtests may have passed) |
| `RESUME` | Resume & discover | Needs full review (some subtests may have passed) |
| `CHAN` | Channel grid | Needs full review (some subtests may have passed) |
| `CIRCUIT` | Circuit board | Needs full review (some subtests may have passed) |
| `LIBRARY` | Library panel | Needs full review (some subtests may have passed) |
| `STACKS` | Case stacks | Needs full review (some subtests may have passed) |
| `TIMELINE` | Timeline | Needs full review (some subtests may have passed) |
| `ARCADE` | Arcade cabinets | Needs full review (some subtests may have passed) |
| `CONSOLES` | Console stack | Needs full review (some subtests may have passed) |
| `DISCS` | Disc station | Needs full review (some subtests may have passed) |
| `HERO_TILES` | Hero tiles | Needs full review (some subtests may have passed) |
| `TWIN_DESK` | Twin desk | Needs full review (some subtests may have passed) |
| `TWIN_ICONS` | Twin icons | Needs full review (some subtests may have passed) |
| `TWIN_SHOWCASE` | Twin showcase | Needs full review (some subtests may have passed) |
| `CRATE` | Record crate | Needs full review (some subtests may have passed) |
| `CARDS` | Card binder | Needs full review (some subtests may have passed) |
| `CUSTOM` | Layout Studio | Needs full review (some subtests may have passed) |
| `POCKET_CLASSIC` | Pocket classic | Needs full review (some subtests may have passed) |
| `POCKET_RIBBON` | Pocket ribbon | Needs full review (some subtests may have passed) |
| `GREENCORE` | Green Core (2001) | Needs full review (some subtests may have passed) |
| `POCKET` | Pocket Quest | Needs full review (some subtests may have passed) |
| `CUBE` | Cube menu | Needs full review (some subtests may have passed) |
| `MEMCARD` | Memory card (2000) | Needs full review (some subtests may have passed) |
| `CASEFLOW` | Case flow | Needs full review (some subtests may have passed) |
| `BOX` | Cardboard box | Recent manual QA (limited scenarios) |
| `STORAGE` | Storage boxes | Needs full review (some subtests may have passed) |
| `FILESELECT` | File select (1996) | Needs full review (some subtests may have passed) |
| `ORBIT` | Orbit deck (1995) | Needs full review (some subtests may have passed) |
| `AIRY` | Airy home (1999) | Needs full review (some subtests may have passed) |
| `NIGHT` | Night menu (2006) | Needs full review (some subtests may have passed) |
| `BLADES` | Blades (2005) | Needs full review (some subtests may have passed) |
| `BUBBLES` | Bubble home (2011) | Needs full review (some subtests may have passed) |
| `CAMERA` | Pocket camera (1998) | Needs full review (some subtests may have passed) |
| `PANELS` | Panel dashboard (2008) | Needs full review (some subtests may have passed) |
| `MAGAZINE` | Game magazine | Needs full review (some subtests may have passed) |
| `SHOP` | Rental shop | Needs full review (some subtests may have passed) |
| `TERMINAL` | Terminal | Needs full review (some subtests may have passed) |
| `CINEMA` | Cinema | Recent manual QA (limited scenarios) |
| `ARCTIC` | Arctic hub | Recent manual QA (limited scenarios) |
| `COVERDETAILS` | Coverflow details | Recent manual QA (limited scenarios) |
| `ARTLIB` | Artwork library | Needs full review (some subtests may have passed) |

## Background-theme catalog by group

Built-in variants include VoltCap classics, still/calm themes, music/album visualizers and lyrics, creature-type and pixel palettes, gaming eras, space/nature, anime-inspired, showcase and trophy scenes. Grouping is in `ThemeGroup`; *theme count is a code count, not a hardware validation count*. The full-screen preview (`ui/ThemePreview.kt`) is 10 seconds per theme. Before merging a redesign, use automated scene captures instead of assuming that an entry in the picker renders well.

## Review findings and improvements (priority order)

1. **Icon bar typography — patch included in this branch.** The tab row and category label use fixed 19sp text on noncompact screens, and large titles can overwhelm small handhelds. Updated the width-aware compact break point and reduced tab/title sizes without changing saved settings, controller handling or layout identity (`ui/StyleIconBar.kt`). Build/device verification pending.
2. **Channel grid (Wii-channel-inspired) popup — design needs its own window.** `GameWindow.kt::lookFor(CHAN)` configures the shared `Arrange.STACK` / pill-button window; `WinScenes.kt::winSceneFor` has no CHAN-specific scene. This explains why the popup can feel generic even with light colours. Design an optional dedicated Channel grid scene: white/light-blue TV-like preview frame, unmistakable rounded `Wii Menu` / `Start` actions, native proportions and restrained animation; dark variant parity, console-sound semantics, safe focus and touch. Preserve existing game-data actions, override options and accessibility; do not globally change `GameWindow` or other layouts.
3. **Controller and one-press reliability — incomplete coverage.** `tools/qa/coverage.json` reports Settings controller walk `CT-01` FAIL (limited traversal; focus loss not yet independently reproduced) and R1 dynamic options `ST-03` NOT TESTED. Recheck each first/last game, transition from a row to tabs, popups and quick menu. Do not call a layout broken just because the explorer did not visit its controls.
4. **Category-switch stability and per-category overrides.** Existing rule in `CLAUDE.md`: switching categories inside the same layout must not reload its shell. `CS-03` has untested layouts and some specific category-change title/image situations. Test `L2/R2` both ways, rapid reversal, only 1–3 games, empty and no art, mixed consoles, and correct category-picker visibility.
5. **Physical layouts.** Case stacks / Arcade have measured slow-frame findings (`D61`); Box 1–3 games/empty and Coverflow-details on Lite, touch, and long titles remain unverified (`D96`). Avoid animations or extra layers until performance and geometry are measured on hardware. For N64/PS2/DS/3DS/Switch, use authentic media and don't invent artwork.
6. **Dual-screen layouts.** `TWIN_DESK`, `TWIN_ICONS`, `TWIN_SHOWCASE`, Pocket classic/ribbon and second-screen widgets need main/second screen, post-game and screen-return testing on the actual Thor; `DV-03` is NOT TESTED in shared QA coverage. Don’t disturb existing user prefs.
7. **Background themes.** Run automated captures and sample frame pacing per `ThemeGroup`: still scenes, dynamic scenes, music-reactive, album-art-dependent, game showcase and trophy-driven. Check Android API compatibility, no old artwork flashes, legible text against pale backgrounds, clean fallback if media is missing, Lite/low quality and no screensaver over active music/app.
8. **Overcrowding and truncation.** `D83` notes remaining '…' labels in R1 Now Playing, Settings subtitle, Widget Board editor. Prefer responsive text sizing/reflow without hiding actions. Don't enlarge global typography or disrupt 53 layouts to fix one.

## QA acceptance and workflow

- Test in bounded batches on the authorized AYN Thor (`adb -s 3140972f`) or emulator-5554. Don't access or install on other physical devices, reset data/charge limits, or alter saved layouts. Restore temporary settings.
- Run relevant unit/static/self-tests first, then actual screenshot / short recording comparisons at selected and intermediate animation frames. Check performance in both high and Lite effects.
- Maintain an honest per-layout test table: controller, L2/R2, popup, long names, empty/small categories, no-art, Endless on/off, light/dark, second screen if supported.
- `src_copy/` is prescribed as the local edit source in `CLAUDE.md`, but is not tracked in this GitHub tree. This review branch contains a narrow change to the tracked `app/` mirror. Reconcile that patch with the local `src_copy/` before copying or merging, so it isn't silently overwritten.
- **No release / merge approval** until green build, targeted tests and hardware check. Do not label untested themes or layouts PASS.
