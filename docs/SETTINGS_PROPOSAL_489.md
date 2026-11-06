# Settings simplification — READ-ONLY proposal (update 489, for the owner's review)

Nothing here is implemented. No preference key is renamed, moved, reset or migrated by this proposal; every current value,
per-layout override (LayoutOnly "Only this layout", `*_by_layout` keys, layout colours) and R1 tile arrangement
(`r1_tiles`: `order`, `hidden`, `more_open`) stays exactly as it is. Each step below is a separate, approvable change.

## 1. What exists today (measured in the staging sources)

| Area | Size |
|---|---|
| Settings › top-level categories | 21 (Home, Game look, Theme, Screensaver, Categories, Achievements, Music & sound, Controls, Library, Accounts, Emulators, Screens, Metadata, Performance, Overlay, Sync, Permissions, Device, Diagnostics, Backup, Updates & downloads) |
| Switch rows in `SettingsDialog.kt` | 117 (+ 5 in SettingsExtras, sliders / chips not counted) |
| Typed prefs in `Features.kt` | ~710 (`BoolPref` / `IntPref` / enum / string) |
| `AppearanceStore` keys | ~140 |
| Per-layout mechanisms | 3 different ones: LayoutOnly (any Home setting), dedicated `*_by_layout` maps (look, popup style, card art, hidden media, names, hero fit, category bar), `layout_colors` |
| R1 Quick menu | one panel (QuickSettings.kt, 2 158 lines), user order + hidden set + "More" fold |

Pain points seen during QA (not owner-reported unless marked):
- The same setting is reachable from 2–3 places (Settings, R1 tile, Edit Home / layout settings) with no hint which one wins
  when "Only this layout" is involved.
- Three per-layout systems: a value can be "own" through LayoutOnly *and* through a `*_by_layout` map; Settings shows the
  LayoutOnly count only.
- Colour has five sources (below) and the UI shows no "why is my accent this colour".

## 2. Colour precedence as the code resolves it today (Theme.kt, update 480 C2)

Highest first, for the home screen's background / panels / accent:

1. **Music colours** — while music plays and the art source allows it (Auto / Album): album palette. Layout colours and
   Custom are ignored while music colours the theme.
2. **Layout's own colours** (`layout_colors`, Features.layoutColorsFor) — background and/or accent of the layout on screen;
   only for recolourable themes.
3. **Custom look** (Theme › Custom background / accent / saturation / brightness).
4. **"Keep my accent"** — with art colours on: background follows the art, accent stays the Custom or theme accent.
5. **Game art colours** ("Colours from the game you're on", art source Auto / Game) — unless "Theme colours only".
6. **Theme** (appTheme.colors) — the fallback; non-recolourable themes stop at the theme.

Plus two scoped overrides drawn *inside* the screen: a category scene's accent on the category name only
(ProvideNameColors), and readability guards (accent ≥ 3:1, text ≥ 4.5:1) that move lightness only.

Proposal (display only, no behaviour change in step A):
- **A.** Theme page gets a one-line "Colours now come from: <source>" readout using the same order, and the layout colour
  editor says "overrides the theme for <layout>; music colours still win".
- **B.** (Needs approval) make music-vs-layout an explicit choice instead of the fixed rule 1 > 2.

## 3. Settings simplification — staged, reversible

- **S1 (labels only).** Group the 21 categories under 4 headers without moving anything: *Look* (Home, Game look, Theme,
  Screensaver, Categories), *Play* (Controls, Emulators, Screens, Performance, Overlay, Achievements), *Library & data*
  (Library, Metadata, Accounts, Sync, Backup), *System* (Music & sound, Permissions, Device, Diagnostics, Updates).
- **S2 (search).** A filter box over the existing row titles / blurbs (they are already plain strings) — the fastest way
  to find one of 117 switches; no prefs touched.
- **S3 (per-layout visibility).** Every row that has a value of the layout's own shows a small "this layout" badge and a
  "use shared value" action; both LayoutOnly and the `*_by_layout` maps feed the same badge. Storage unchanged.
- **S4 (later, needs explicit approval + backup first).** Merge the `*_by_layout` maps into LayoutOnly. This is the only
  step that would touch stored data; it would need a one-way, tested migration with a Backup export before it runs,
  and is NOT proposed for 0.6.68.

## 4. R1 Quick menu

- Keep the owner's `r1_tiles` order / hidden set as the source of truth; new tiles append to "More", never reorder.
- **Q1.** Show which tiles act on "this layout only" (same badge as S3).
- **Q2.** Any "reset to default order" is a separate, confirmed action — never automatic, never part of an update.
- **Q3.** Long-press a tile → "Open in Settings" (jumps to the full row), so R1 stays short without losing reach.

## 5. Risks / what to verify before any step

- Backup.kt / SettingsScopes.kt must list any new key (rule: new prefs go into SettingsScopes).
- Unit tests that pin pref keys (ResetAndCompat DEVICE_LOOKS, LayoutOnly.skip) must still pass unchanged.
- Device check per step: snapshot save → change → snapshot compare must show only the intended key.
