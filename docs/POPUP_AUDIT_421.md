# Popup audit — Update 421 / 422 (1 Nov 2026)

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
| Cross menu | **422** options column sliding in from the right edge, title + facts on the left | new | HW |
| Live tiles | **422** flat tile panel, lower-case title, turnstile entrance | new | HW |
| Dashboard | **422** widget card with art header and three stat widgets | new | HW |
| Side menu | **422** drawer from the left edge, menu rows with accent bar | new | HW |
| Spotlight | **422** poster under a spotlight, logo + pill keys | new | HW |
| Icon bar | **422** flyout rising from the dock, round icon keys, pointer | new | HW |
| Console hub | **422** brushed-metal nameplate with console badge | new | HW |
| Resume & discover | **422** big square + discover card | new | HW |
| Circuit board | **422** chip datasheet (pin-numbered facts, PCB, traces) | new | HW |
| Library panel | **422** library page: hero banner, big green Play, stats + progress bar | new | HW |
| Timeline | **422** entry pinned to a year bubble on the line | new | HW |
| Hero carousel / Hero tiles | **422** hero banner opening from its strip | new | HW (Hero) |
| Tile home / Grid | **422** clean game card popping out of the tile | new | HW |
| Voltcap row | **422** house panel with the charge stripe | new | HW |
| TV room | opens on the TV's own screen (tvRect) | kept | — |
| Layout Studio | the standard window in the user's chosen look (user-built layout) | kept on purpose | — |

## Remaining
- Every layout except Layout Studio and TV room now has its own window (51 of 53, checked by `Popup421Test`).
- Twin layouts: the hinge window is one window; a true split across the two physical screens is not done.
- Green Core and Card binder scenes not seen on hardware.
- Rapid A/B/A, Lite, touch and compact-screen checks of the new scenes: NOT TESTED.
