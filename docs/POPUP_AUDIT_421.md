# Popup audit — Update 421 (1 Nov 2026)

Source of truth: `winSceneFor()` in `ui/WinScenes.kt` (layout → scene) and `PopupStyle` in `ui/QuickSettings.kt`
(which window A opens). 53 `HomeLayout` values. Before 421: 19 layouts had a scene. After 421: 34.

Legend — HW: checked on the AYN Thor 3140972f (screenshot, release build of this update). CODE: compiled + unit tests only.

## Popup style selection (R1 › Popup and Settings › Home › Layout options › Game window)

| Fix | Detail | Test |
|---|---|---|
| Classic can no longer show a layout scene | Every opener asks `PopupStyle.opensClassic()` (A, tap, second screen, reopen after B, Spotlight hint); GameWindow itself redirects to the details window if reached with Classic (explicit Inspect excepted). Cardboard box: A with Classic does not pull a case out first. | `Popup421Test.classicIsClassicWhateverTheLayoutsScene`; HW: Box + Classic → A opened the details window (A again = Play prompt), no case pulled, no insert sheet |
| Row styles only where there is a row | `PopupStyle.offered(layout, hasRow)` hides Opens / Reveal / Shared when no card of the game you're on is drawn on this screen (`GameOrigin`, reset on layout change). A saved row style stays listed (never reset silently). | `Popup421Test.rowStylesHidden…` (CODE) |
| R1 and Settings show the same list | Settings had only own / classic; it now offers Default + the same styles as R1, each with the same one-line description. | CODE |

## Layouts

| Layout | Popup | 421 change | Tested |
|---|---|---|---|
| Cardboard box | Insert sheet (BOX_SHEET) + case pull-out | Box: flap outline stroke, crease / seam / wall shading softened, rim edge lit, selected case without dark keyline | HW (box look, Classic) |
| File select, Storage boxes, Memory card, Case flow, Orbit deck, Airy, Night, Blades, Bubbles, Pocket camera, Panels, Magazine, Rental shop, Terminal, Arcade, Cinema, Arctic | existing scenes | none (kept) | not re-tested |
| Channel grid | Wii Channel preview | palette (see below); wide art crop-fills the preview, portrait cover whole over a soft filled copy | HW (Light Blue, preview) |
| Record crate | **new** Record sleeve back (Side A track list, sleeve slides out) | new | HW |
| Card binder | **new** Card back (gold foil, flips in) | new; note: with "A plays" on (Thor), A starts the game by design | HW not reached (A plays on the Thor) |
| Disc station | **new** Disc player front panel (LCD + transport keys), real disc beside it | new | HW |
| Green Core | **new** glowing system panel | new | HW not reached (A did not open on the first press; NOT TESTED) |
| Cube menu | **new** cube face turning in | new | HW |
| Pocket classic / Pocket ribbon / Pocket Quest | **new** handheld LCD (4-shade) | new | HW (Pocket classic) |
| Game shelf / Case stacks / Console stack / Coverflow details | **new** library index card beside the real case | new; card flight kept for the plain-window path | HW (Shelf, Stacks, Coverflow) |
| Twin desk / icons / showcase | **new** hinge window (art half / controls half) | new | HW (Twin desk, single screen only) |
| Artwork library | **new** gallery wall + placard (art whole, never stretched) | new | HW |
| Voltcap row, Tile home, Hero, Grid, TV room, Dashboard, Live tiles, Side menu, Cross, Spotlight, Icon bar, Console hub, Resume, Circuit, Library panel, Timeline, Hero tiles, Layout Studio | the standard window in the layout's look (`lookFor`) | none — still to design | — |

## Remaining
- 18 standard layouts still use the shared window (in their own colours): TIMELINE, CIRCUIT, CROSS are the next candidates.
- Twin layouts: the hinge window is one window; a true split across the two physical screens is not done.
- Green Core and Card binder scenes not seen on hardware.
- Rapid A/B/A, Lite, touch and compact-screen checks of the new scenes: NOT TESTED.
