# Badge styles v0.6.74 - plan (owner decision 17 Nov 18:37: B1)

Owner decisions:
- **Classic + compact achievement badge = default.**
- **Top Clip** and **Hanging Bottom** are selectable options. All three are available, per home layout where feasible.
- Compact progression **Full → Count → Percent → Mini ring**. The achievement badge stays visible wherever physically reasonable.
- Fix the Twin icons spacing (D352) in this work.
- Accurate final visual comparisons (real app screenshots, not mockups) before the designs count as fully approved.
- Branch: `claude/v0674-badges`, cut from the dev line. The v0.6.73 release and its tags are never touched.

## Today (dev line)
- `BadgeLane.kt` `planBadges`:
  - Corners are kept; a later badge STACKS below an earlier one.
  - If neither fits, the later badge is hidden (`BadgeSpot.shown = false`).
  - The console badge is placed first.
- `GameProgressChip` (StyleHomes.kt) already steps down on its own, from the card width alone:
  - Full "48/120 · 40%" → count → percent → bar-only.
  - It does not know about the console badge.
- `ConsoleBadge.kt`: the console pill in its corner.
- Badges are drawn INSIDE the tile's `clip(shape)` (StyleArt, GameCard, TileHome). That is why Top Clip / Hanging Bottom can't simply offset them.

## Design
1. **Setting** (one source of truth; `data/Features.kt` + `data/SettingsScopes.kt` so it is backed up):
   - `badge_style` = CLASSIC (default) | TOP_CLIP | HANGING_BOTTOM.
   - An optional per-layout override `badge_style_<LAYOUT>`.
   - Existing users see no change.
2. **Compact progression without a layout loop.** D350's lesson: never write lane state from a measure that the measured content depends on.
   - The chip measures all four forms once per text change (TextMeasurer, as `GameProgressChip` already does).
   - It reports a form ladder [full, count, pct, ring] widths to the lane in one `BadgeBox`.
   - `planBadges` picks the largest form that keeps the SAME ROW, at least `BADGE_GAP` clear of the console badge.
   - The chip only READS the chosen form at placement / draw. Its measured size is the chosen form's size, fixed for the plan's inputs, so the plan converges in one pass.
   - Hiding is the last resort: only when even the ring does not fit.
   - The Robolectric idle test (EndlessOnceRoboTest-style) comes first, as for D350.
3. **Top Clip / Hanging Bottom.**
   - Badges move to an unclipped overlay sibling of the tile's clipped art (same `Box`, after the clip). Its position comes from the lane, shifted by half the pill height (above the top edge / below the bottom edge).
   - Scroll containers and neighbouring tiles need that room: each layout gets a padding token, `badgeOverhang(style)`.
   - Where a layout can't give room (dense grids, Twin icons' small slots), it falls back to Classic.
   - This per-layout "feasible" list is recorded in `LAYOUT_MATRIX.md`.
4. **D352 Twin icons.**
   - Reproduce on the emulator first: emulator review mode, TWIN_ICONS, badgecheck on the qa_clip dump.
   - Then compare the drawn pill edges with the reported `BadgeBox` (outer padding / border outside the reported box is the suspect).
   - The fix goes in the reporting, not a Twin-only offset.

## Verification
- Unit tests:
  - `planBadges` ladder: same row kept, ring before hide, no overlap.
  - Setting wiring and backup scope.
  - Idle test on the CHAN / GRID / TILES / TWIN_ICONS rows.
- Emulator review runs (679a mode), all three styles × x1.0 / x1.3, on the layouts that show badges:
  - Cube, Grid, Tiles, Classic, Shelf, Stacks, Twin icons, Channel, Storage.
- badgecheck + popclip.
- Side-by-side sheets of real screenshots for the owner, before calling the design final.
- Hardware later (KONKR if connected; Thor / RP Classic are queued).
