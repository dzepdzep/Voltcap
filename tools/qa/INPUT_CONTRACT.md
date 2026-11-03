# VoltCap controller contract (update 462)

One set of standard buttons on every home layout (all 53, including the dual-screen layouts and every R1 mode).
Layouts may change how things look and move, not what these buttons do.

| Button | Home screen action | Where it lives |
|---|---|---|
| Select (short press) | Settings (main menu) | `LauncherScreen` key `when` → `showMenu` |
| Select + button | the user's hotkey combo (remappable, Settings › Controls) | `LauncherScreen` selectHeld / selectComboUsed |
| Start / Menu | the focused game's options | `optionsForGame` |
| R1 | quick menu (quick settings + notifications; Sort & filter is in it) | `showNotifications`, notificationsOnly = false |
| L1 | shortcuts | `showShortcuts` |
| L2 / R2 | previous / next category (visible categories, saved order) | `cycleCategory` |
| A | activate the focused item | layout / home |
| B | back; on the home: the category picker (if Settings › Category screen isn't Off) | `openCategoryScreen` → `ThemedPicker` |
| Y / X | achievements / favourite | home |
| L3 / R3 | quick jump / all controls | home |

Single-button hotkeys the user bound (Settings › Controls) keep priority over all of the above (`hotkeyFor(code, false)`).

## Precedence (highest first)
1. System and game overlays (Android, the in-game panels).
2. A focused popup / modal (`aDialogIsOpen`, the game window, the category screen, quick jump): it gets every key, B closes it.
3. The global buttons above (`InputContract.RESERVED`: Select, Start, Menu, L1, R1).
4. The layout's own navigation (`styleHomeKey` → the layout handlers).

## When a layout may keep a reserved button (`InputContract.route`)
- **Pocket classic**, on its Games / Categories page: L1 / R1 jump 15 (an owned page).
- **Pocket Quest** with "L1 / R1 change the console" switched on by the user (`pq_bumpers2`): L1 / R1.
- **Pocket Quest / Pocket classic / Pocket ribbon** while their launch animation plays (~¼ s): every reserved button is held,
  so a rapid press opens nothing over the game that is starting.
- Nothing else. Select is Settings everywhere.

## What changed in 462
- Pocket Quest: Select opened Select a System (held: sort). Now Settings; Select a System opens with B, the Category screen
  hotkey and the top bar button; L2 / R2 still switch.
- Blades, Magazine, Panels, Pocket camera, Terminal, Rental shop, Green Core, Bubble home: Select sorted and Start opened the
  quick menu. Now Settings / game options; Sort & filter stays in the R1 quick menu and in game options.
- Pocket ribbon: Start played / opened. Now game options (A still plays / opens); a second press during the launch moment no
  longer starts the game twice.

## Themed category pickers (`ThemedPicker`, `themedLookFor`; Settings › Category screen › Themed pickers, on by default)
One data source (the category tiles / `StyleCtx.stops`: visible categories, hidden ones left out, saved order, the same as
L2 / R2), one selection, one pick (`applyCategory` / step like L2 / R2 — a category's own HomeLayout is never written).
- Pocket Quest → its Select a System page (retro). Green Core → its Systems page. Pocket classic → its Categories page.
- Channel grid → Channel Menu look. Cinema → Cinema look. Arctic hub → Arctic Panes look (`ui/CategoryLooks462.kt`).
- Every other layout → the standard category screen (the look picked in Settings, or Follow home layout).
- D-pad, A (pick), B (close, focus back on the same game), touch (tap focuses, tap again picks); long names wrap / slide
  (FullText); an empty category opens with its empty state.

## Tests
- `InputContract462Test` (unit + static: every layout × every reserved key routes HOME by default; the owned contexts;
  the gate precedes every layout handler; no layout re-binds Select / Start).
- Hardware: `tools/qa/device/contract462.sh` (simulated input over USB adb — `input keyevent`; a physical controller was not
  used unless stated in the SUMMARY entry).
