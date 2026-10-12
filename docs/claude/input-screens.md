# Controller, touch & second screen

Read when adding or changing interactive UI.

## Controller (primary input)
- Everything must be reachable and usable with the D-pad + A/B/X/Y, L1/R1, L2/R2, Select/Start (and M1/M2 combos, `data/Hotkeys.kt`). Never add a touch-only feature.
- Focusable items: `Modifier.shelfFocusable()` / `shelfClickable()` / `focusRing()` from `ui/DialogChrome.kt` (they also play the UI move sound via `LocalUiSounds`). Scrolling popups: `Modifier.dpadScroll(scrollState)`; text-only pages: `ScrollStop`.
- Home-screen key handling lives in `ui/LauncherScreen.kt` (`onPreviewKeyEvent`/`onKeyEvent` blocks — grep, don't read the file). Dialogs/overlays opened from home are tracked by `ui/LauncherUiFlags.kt`; while one is open the home grid must not take focus.
- Closing a popup must return focus to the item that opened it.
- Button legend/glyphs: `GlyphPill` in `ui/HomeChrome.kt`; layout detection `data/SystemButtonLayout.kt` (hooked in `MainActivity.dispatchKeyEvent`). Show labels for what's printed on the button.

## Touch
- Every control must also work by tap. Touch-only extras on the home screen (status bar icons, player buttons) use `tapOnly` so they never steal D-pad focus from the games.
- `MainActivity.dispatch*` calls `HomeIdle.poke()` on any key/touch/stick input (wakes idle animations) — keep that path cheap.

## Second screen (AYN Thor, TV, cover screens)
- `ui/SecondScreen.kt`: `SecondScreenHost(enabled, onUserActivity, content)` shows content on a `Presentation` on the second display (`findSecondDisplay`), shares the main composition/state, follows displays being added/removed, steps aside via `SecondScreenGate` when an app is launched there. The controller drives everything from the main screen; the second screen takes touch.
- Content: `ui/SecondScreenHome.kt`, `ui/CompanionHub.kt`; while a game runs: `ui/CompanionActivity.kt`. Layout rules: `data/ScreenLayouts.kt`, `ui/ScreenLayoutsUi.kt`.
- Use `secondScreenQuality()` for scenes there; keep compact layouts for small screens.
- New screens/overlays that show game/music info should consider a second-screen variant and must not break single-screen devices.

## Performance targets
Weak handhelds (Retroid Pocket Classic, Konkr, ≤6 GB RAM): respect `device/MemoryBudget.kt`, `DevicePerformance`, Lite effects, `PowerSaver`/`HomeIdle`, and stop animations when covered, idle, off-screen or in a game (`data/GameMode.kt`).
