# Popup design audit (update 409)

Source inspection = read from code (`winSceneFor`, `GameWindow` dispatcher, `PopupStyle`). "Thor" = real screenshot / key presses on 3140972f this update.
53 home layouts: 19 with an own popup scene, 34 on the shared GameWindow.

## Dispatcher (D105 fixed)
`PopupStyle.current(layout)` = this layout's own entry (`Features.popupStyleByLayout`, "LAYOUT=STYLE") else the old per-layout Classic switch else the
global default (the old "Its style / Its motion (default for all layouts)" settings). `GameWindow` and the row fades read `PopupStyle.motion(layout)` /
`banner(layout)`, so a layout set to Own never opens Banner because another one is set to it. Existing prefs migrate by doing nothing: no entry = old behaviour.

## Findings from the Thor batch (10 layouts)
- CHAN (Wii preview): matches the preview (white or charcoal, big banner well, name + facts under it, chip row, Wii Menu / Start). Chip row scrolls with focus (first chip clips while scrolling, by design). Inspect pulls the case out left of the window; B closes both; second B = category screen. No change needed.
- Shared window (COVERDETAILS, TILES, ARTLIB, GRID, STACKS, TWIN_SHOWCASE): story ends "..." after 2 lines; the full text is in Details (kept: a one-line marquee for a paragraph reads worse). Olive / gold look repeated on 4 layouts: suggested per-layout palettes.
- CINEMA: story column is a fade-scroll area with no control focusing it (cut mid-line on long stories); suggestion: scroll it with the right stick / focus it.
- ARCTIC: drawer menu list clips the last row (Manual) and the story ends "..."; both scrollable, suggestion: scroll hint.
- SHELF / STACKS: the first A pulls the case out (deliberate physical step), the second A opens the window.
- Missing art (Game Boy test title): window shows the console banner + case, nothing broken.

## Matrix
| Layout | Popup type (default) | Override by R1 / default | Verified |
|---|---|---|---|
| CLASSIC (Voltcap row) | Shared GameWindow in the layout look (`lookFor`) | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| TILES (Tile home) | Shared GameWindow in the layout look (`lookFor`) | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | Thor screenshot (shared window) |
| HERO (Hero carousel) | Shared GameWindow in the layout look (`lookFor`) | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| GRID (Grid) | Shared GameWindow in the layout look (`lookFor`) | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | Thor screenshot (shared window) |
| TV (TV room) | Shared GameWindow in the layout look (`lookFor`) | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| DASH (Dashboard) | Shared GameWindow in the layout look (`lookFor`) | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| LIVE (Live tiles) | Shared GameWindow in the layout look (`lookFor`) | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| SIDE (Side menu) | Shared GameWindow in the layout look (`lookFor`) | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| CROSS (Cross menu) | Shared GameWindow in the layout look (`lookFor`) | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| SPOT (Spotlight) | Shared GameWindow in the layout look (`lookFor`) | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| BAR (Icon bar) | Shared GameWindow in the layout look (`lookFor`) | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| SHELF (Game shelf) | Shared GameWindow in the layout look (`lookFor`) | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | Thor: A pulls case out, 2nd A = window (Turn over / Put back) |
| HUB (Console hub) | Shared GameWindow in the layout look (`lookFor`) | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| RESUME (Resume & discover) | Shared GameWindow in the layout look (`lookFor`) | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| CHAN (Channel grid) | Own scene `WII_PREVIEW` | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | Thor: light + dark, D-pad / A / B, Inspect, per-layout Banner + restart |
| CIRCUIT (Circuit board) | Shared GameWindow in the layout look (`lookFor`) | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| LIBRARY (Library panel) | Shared GameWindow in the layout look (`lookFor`) | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| STACKS (Case stacks) | Shared GameWindow in the layout look (`lookFor`) | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | Thor: case + window beside it |
| TIMELINE (Timeline) | Shared GameWindow in the layout look (`lookFor`) | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| ARCADE (Arcade cabinets) | Own scene `ARCADE_MENU` | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| CONSOLES (Console stack) | Shared GameWindow in the layout look (`lookFor`) | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| DISCS (Disc station) | Shared GameWindow in the layout look (`lookFor`) | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| HERO_TILES (Hero tiles) | Shared GameWindow in the layout look (`lookFor`) | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| TWIN_DESK (Twin desk) | Shared GameWindow in the layout look (`lookFor`) | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| TWIN_ICONS (Twin icons) | Shared GameWindow in the layout look (`lookFor`) | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| TWIN_SHOWCASE (Twin showcase) | Shared GameWindow in the layout look (`lookFor`) | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | Thor screenshot (shared window, purple look) |
| CRATE (Record crate) | Shared GameWindow in the layout look (`lookFor`) | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| CARDS (Card binder) | Shared GameWindow in the layout look (`lookFor`) | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| CUSTOM (Layout Studio) | Shared GameWindow in the layout look (`lookFor`) | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| POCKET_CLASSIC (Pocket classic) | Shared GameWindow in the layout look (`lookFor`) | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| POCKET_RIBBON (Pocket ribbon) | Shared GameWindow in the layout look (`lookFor`) | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| GREENCORE (Green Core (2001)) | Shared GameWindow in the layout look (`lookFor`) | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| POCKET (Pocket Quest) | Shared GameWindow in the layout look (`lookFor`) | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| CUBE (Cube menu) | Shared GameWindow in the layout look (`lookFor`) | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| MEMCARD (Memory card (2000)) | Own scene `MEMCARD_INFO` | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| CASEFLOW (Case flow) | Own scene `CASE_MEDIA` | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| BOX (Cardboard box) | Own scene `BOX_SHEET` | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | Thor: home + pull; own insert sheet NOT re-shot this task |
| STORAGE (Storage boxes) | Own scene `STORAGE_RECORD` | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| FILESELECT (File select (1996)) | Own scene `FILE_CARD` | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| ORBIT (Orbit deck (1995)) | Own scene `ORBIT_TERMINAL` | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| AIRY (Airy home (1999)) | Own scene `AIRY_PANEL` | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| NIGHT (Night menu (2006)) | Own scene `NIGHT_STATUS` | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| BLADES (Blades (2005)) | Own scene `BLADE_GUIDE` | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| BUBBLES (Bubble home (2011)) | Own scene `BUBBLE_CARD` | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| CAMERA (Pocket camera (1998)) | Own scene `CAMERA_MENU` | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| PANELS (Panel dashboard (2008)) | Own scene `PANEL_DETAIL` | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| MAGAZINE (Game magazine) | Own scene `MAG_PAGE` | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| SHOP (Rental shop) | Own scene `SHOP_TAG` | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| TERMINAL (Terminal) | Own scene `TERM_RUN` | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | source inspection only |
| CINEMA (Cinema) | Own scene `CINEMA_PREMIERE` | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | Thor screenshot (own premiere page) |
| ARCTIC (Arctic hub) | Own scene `ARCTIC_DRAWER` | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | Thor: own drawer opens while CHAN=Banner (independence) |
| COVERDETAILS (Coverflow details) | Shared GameWindow in the layout look (`lookFor`) | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | Thor screenshot (shared window) |
| ARTLIB (Artwork library) | Shared GameWindow in the layout look (`lookFor`) | all six styles (Row opens / Reveal only where the layout has a row; else Own / Banner) | Thor screenshot (shared window) |
