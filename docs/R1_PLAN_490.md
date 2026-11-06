# R1 Quick menu — plan for Layout options and Game spacing (update 490, READ-ONLY, nothing implemented)

Constraints from the owner: keep every existing tap / long-press action and the owner's tile order (`r1_tiles`: `order`,
`hidden`, `more_open`); new optional tiles land in **More** and never move existing tiles; no large redesign.

## What the code does today (QuickSettings.kt)
- Tiles are built as `R1Tile(id, icon, label, …, longAction)`; ~58 ids on two pages (Quick / Device).
- Order: the saved `order` first; a tile **not yet in the saved order is inserted right after the tile that precedes it in
  the built list** (`ordered`, line ~1105). So today a brand-new tile *would* appear in the middle of the owner's row —
  this must change before any tile is added (see R0).
- The first five shown tiles form the top row; the rest sit under **More** (`more_open` remembers whether it is open).
- Already there and related: **Layout** tile (tap: next layout, hold: every layout); **Size** tile (tap: next size, hold:
  the size slider; Case stacks / Grid in carousel mode use the carousel size); **Twin gap** (two-screen layouts only).
- Per-layout game spacing exists as separate prefs: `rowSpacing` (Voltcap row), `tilesSpacing` (Tile home), `shelfSpacing`
  (Game shelf), `cdSpacing` (Cover details), `stacksSpacing` (Case stacks, also reaches its carousel since 489c).

## Proposal (each step separately approvable)
- **R0 — new tiles never disturb the arrangement (prerequisite, tiny):** a tile id missing from a non-empty saved `order`
  is appended at the end of its page (so it lands in More), instead of after its predecessor. Saved `order` / `hidden`
  untouched; with no saved order (fresh install) the built order stays as now. Unit test: an existing saved order is a
  prefix of the result.
- **R1 — Layout options, contextual, no duplicate control:**
  - Add "Options for <layout>…" as the first line of the existing **Layout** tile's hold sheet → opens Settings › Home ›
    Layout options for the layout on screen (the same SettingsJump path search uses). Tap stays "next layout".
  - Optional tile `layoutopts` ("Layout options"), appended in More, same jump. It holds no setting itself, so nothing is
    duplicated.
- **R2 — Game spacing, independent tile:** optional tile `spacing`, appended in More, shown only on layouts that have a
  spacing pref (list above). Tap: next step 80 / 100 / 120 / 150 % of that layout's own pref; hold: the existing
  PercentSlider for it (same pref, same 50–250 % range as Settings). It never touches Size, so Size keeps its tap / hold.
  Layouts without spacing: the tile is not built (no dead tile).
- **Not proposed:** merging Size and Spacing, changing any existing tile's tap / hold, reordering, or default-visible new tiles.

## Verification when implemented
- Device: snapshot `r1_tiles` before / after; only the new id may be added (at the end). Owner's long-press actions
  re-checked tile by tile on the Thor and the KONKR (controller + touch), Edit mode drag unaffected.
- Spacing tile changes only the current layout's spacing pref (snapshot diff shows exactly that one key).
