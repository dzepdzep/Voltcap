# VoltCap persistent outstanding request register — cross-check at Update 403

This is a list of previously reported requirements, NOT evidence that they are already implemented. Before changing status, reconcile with the current code, SUMMARY.md, CURRENT_TASK.md, COWORK_HANDOFF.md and QA results. Preserve historical done entries; add links to test captures and defects for every change.

## Immediate physical media and transitions (Update 403 focus)
- Dark artificial rectangular shadow slabs, dark empty slots, floor/row tint, selected-game theme block still visible in Cardboard Box/mixed cases; remove artifacts while keeping attached 3D depth and light natural shading.
- Detached, black, hollow-looking side extrusions of boxes/spines; connected geometry, correct face-to-face mapping, no oversized fake 3D.
- Item featured at top of shelf must COME from its row position when navigating, not spawn; reverse into row for return/selection changes.
- Continuous pickup -> physical presentation/popup -> return in Cardboard Box, Game Shelf, Case Stacks, Cover Details and comparable spine-based layouts; eliminate preflight dim/idle ~8 frames, clipped blank/double models, snapping or cutscene feel; safe rapid A/B and D86.
- Artwork fit and physical case sizes. N64 crop/slabs/sidebars; PS2 Yu-Gi-Oh doubled platform banner appears in current source art, verify correct real front art selection/manual overrides; check real vs custom and never alter user's scans silently.
- True physical 3D box faces/thickness/perspective front/spine/top/back, original Mighty Final Fight reference, proper selected box artwork. NES/SNES/N64/PS1/2/3/4/5/PSP/DS/3DS/GBC/GBA/Switch mapping and region sizes. Switch cartridge wrong; N64 backs/spines wrong; other systems intermittent backs.
- Auto/1/2/3 rows in Cardboard Box only when packaging fits; 1–3 game/empty categories, DS/3DS/GBA vs GBC box realities and mixed categories; touch/controller/second screen.

## Outstanding behavior and looks (track, don't forget)
- Artwork Library: Endless last-first loop vs duplicate/spawn bugs, rails, touch drag, category sizes 1/2/3, correct artwork and measured performance; preview previously accidentally showed Cover Details; verify correct layout. Category selection L2/R2 sometimes reaches categories absent from picker despite shared CategoryStops source; reproduce runtime with user settings and honor intentionally hidden categories.
- Category-selection designs: System Stage, System Grid, Side Banner, Retro Timeline, Compact List, Collector's Drawers, Pocket Worlds, Archive 1994, Pocket Dashboard; actual unique designs using existing VoltCap theme/system assets, controller-first input, per-console/mixed overrides.
- Pocket Ribbon (upper screen): previous request was one centered cartridge, no left artwork/no blur, no Endless, directional slide synchronized to bottom selection; test long lists and last-game stop. Pocket Quest is distinct: title overlap, thumbnail fade, FPS investigation remain OPEN unless evidence says otherwise.
- Disc Station blank/delayed labels and visual progress; Cinema/Arctic/Dashboard/TV Room/Case Stacks/Game Shelf/Cardboard Box/Cover Details/TWIN_ICONS/TWIN_SHOWCASE/HERO_TILES/dual-screen and other applicable layouts: physical 3D, controller focus, category switch artifact and performance audit.
- Persistent picture flash issues: old backdrop/front/spine/back appearing; background cached with wrong key, 1-frame empty/no-art, wrong or missing custom spine, case size jumps in mixed categories; correct cover behavior and memory budget; finish ~30 layout category-switch QA and github CI.
- Layout-specific popups: Cardboard Box top view/real pullout, physical shelves, Pocket Classic, Pokémon PC, N64/Saturn/Dreamcast/Persona 3, PS2 save-like icons; prefer proper shared interaction systems. R1 options audit for incompatible selections; hide only genuinely impossible combinations after correcting bugs. Smooth controller D-pad focus and scrolling, avoid breaking existing layouts.
- Dual-screen: normal second screen in all dual layouts; synchronized colors in TwinDex, remove flashing hardcoded category bar, widgets continue in games, volume mixer and profile/status presentations as documented. Android discs vs cartridges selectable, video wallpaper selection, UI text/icon scaling, nav-bar consistency, frame drops, GL crash on RP Classic and GBC Balanced stutter, 3D cart carousel crash.
- User profiles/start-up choices, optional avatar, WiiFlow row reveal, layout-specific category visibility/background video loop, subtle status bars, achievements overlay and supported performance/TDP settings — follow project docs and mark staged/deferred appropriately.

## Required accounting in every update
- Append detailed numbered SUMMARY.md entry before finishing, not just a generic line, with every request and all remaining OPEN work; synchronize CURRENT_TASK.md, COWORK_HANDOFF.md, defect and coverage records.
- Maintain exact user device safety: only Thor serial 3140972f or emulator-5554; NEVER Google TV Streamer or unqualified ADB. No data deletion, preserve profiles and installed games, restore test settings.
- Evidence from actual on-device frames/screens plus deterministic regression tests, not build-only claims. Mark unsupported/untested items NOT VERIFIED.
- Do not publish a no-op new release. When a meaningful verified release is truly ready, publish NORMAL public GitHub release with correct signed APK and complete release notes. If not, leave local commit and precise handoff.


---
## Status at the end of Update 403 (29 Oct 2026) — cross-checked against SUMMARY.md 393-402, CURRENT_TASK.md, COWORK_HANDOFF.md, tools/qa/defects.json

DONE = implemented and seen on the Thor; PARTIAL = part done / limits stated; OPEN = not started or not reproduced; NOT VERIFIED = code or claim without hardware evidence. Evidence: `.shots/403/` (local, not committed), defects D92-D95, `Physical403Test`, `CoverDetails403Test`.

### Immediate physical media and transitions
| Request | Status | Note |
|---|---|---|
| Dark rectangular shadow slabs / dark slots / floor-row tint / selected-game block (Cardboard box) | **DONE** (D92, D88, D89) | row-wide sheen + contact bands replaced by per-case ones; selection block removed in 402. Box 2 rows ALL on the Thor; 1 / 3 rows, 1-3 games, empty NOT re-checked |
| Detached black hollow side extrusions | **DONE for the Box** (D92) | sides / ends keep the same attached geometry, now cardboard / plastic toned. Other layouts: stills of Crate, Binder, Console stack, Shelf, Stacks show none |
| Featured item must come from its row position when navigating | **PARTIAL** (D95 suspect) | Game shelf traced + Thor-framed (rises from its slot, old case returns); owner's spawn not reproduced; one continuous curve NOT built |
| Continuous pickup -> popup -> return in Box / Shelf / Stacks / Cover details | **PARTIAL** | 402 flight + 403 veil follows the flight (D93); Box re-captured, Shelf / Stacks / Cover details window code shared but not re-captured |
| Pre-flight dim / idle ~8 frames | **DONE for the dim (Box)**; idle wait for slot + cover (<= 450 ms) **OPEN / NOT MEASURED** | the veil no longer dims ahead of the movement |
| Safe rapid A/B, D86 | **PARTIAL** | rapid A,B (120 / 250 ms) x3 on the Box: one case in every sampled frame (6 fps sampling of a 48 fps capture); the literal `D A B A B A B` was not sent (A on the open popup is Play and would launch a game) |
| Artwork fit, N64 crop / sidebars | **PARTIAL** (D90, D94) | Cover details N64 front is the real ScreenScraper scan (480x352 on a 1.425 face, no fit bars); the grey fields are not drawn by Voltcap, raw file NOT inspected; NES / SNES / DS / GBA / Switch fit rule NOT audited |
| PS2 Yu-Gi-Oh doubled banner | **OPEN** (D91 suspect) | in the source picture (402); needs another scan or a per-game cover override; no heuristic, user art untouched |
| True physical 3D faces front / spine / top / back, reference 07 | **PARTIAL** (D94) | spine + top visible, no clipping, Subtle and Realistic 3D (GBA, N64). Renderer already true 3D; per-console spine / back mapping (N64 backs, Switch cart), the full reference parity and region sizes NOT audited |
| Auto / 1 / 2 / 3 rows only when packaging fits | **PARTIAL** | BoxRows400 unchanged and its tests green; Thor checks of 1 / 3 rows, 1-3 games, empty NOT done this update |

### Outstanding behaviour and looks
| Request | Status |
|---|---|
| Artwork library Endless loop, 1-3 games, rail, touch drag, performance | OPEN (not touched in 403) |
| L2 / R2 reaching categories absent from the picker | OPEN (not reproduced; CategoryStops shared) |
| Category-selection designs (System Stage ... Pocket Dashboard) | OPEN |
| Pocket Ribbon top screen | DONE in 401 (single cartridge, directional slide, no Endless); long lists / last-game stop NOT VERIFIED |
| Pocket Quest title overlap, thumbnail fade, FPS | OPEN |
| Disc Station blank / delayed labels | OPEN |
| ~30 layouts category-switch QA (rule 14) | PARTIAL (394-397 PASS list; ~36 layouts NOT TESTED; BOX + COVERDETAILS only visually re-checked in 403) |
| Picture flashes (stale backdrop / spine / back), case size jumps in mixed categories | OPEN / NOT VERIFIED (398 spawning fixes) |
| Layout-specific popups (Box top view / real pullout, Pocket Classic, Pokemon PC, N64 / Saturn / Dreamcast / Persona 3, PS2 save icons) | OPEN |
| R1 options audit / controller focus | OPEN |
| Dual-screen (TWIN_*, HERO_TILES, normal second screen, TwinDex colours, volume mixer, nav-bar, GL crash RP Classic, GBC stutter, 3D cart crash) | OPEN |
| Profiles / start-up choices, WiiFlow reveal, achievements overlay, TDP | OPEN / staged per project docs |
| New request: row-to-featured animation (this update) | PARTIAL (see above) |
