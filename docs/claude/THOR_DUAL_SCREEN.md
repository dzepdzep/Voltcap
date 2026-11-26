# AYN Thor dual-screen: why Android Home can appear on the lower screen (H13, cloud reading 679b)

Cloud code reading only (17 Nov 2026, update 679b). Nothing here was run on a Thor; every cause below is a CODE PATH that
produces the reported symptom, not a confirmed reproduction. HW-19 (docs/claude/HARDWARE_QA_QUEUE.md) confirms which one the
owner saw. The owner's Dead Cells video was not available to the cloud run.

Thor model assumed by the code: the top screen is Android's default display (id 0, Voltcap's MainActivity, `singleTask`,
home via the disabled-by-default `HomeAlias`); the lower screen is the first non-private non-default display
(`findSecondDisplay` / `ScreenPlacement.secondScreen`, presentation category preferred).

## What Voltcap puts on the lower screen, and when it disappears

| Voltcap state | Lower screen shows | Code |
|---|---|---|
| Voltcap in front on the top screen | the second-screen home: a `Presentation` window owned by MainActivity's composition | `ui/SecondScreen.kt` (`DisposableEffect(display, activity, suppressed, revive)`) |
| MainActivity STOPPED (a game / app covers the top screen) | the Presentation is HIDDEN (update 326: a stopped composition showed a frozen picture over the in-game page) | `SecondScreen.kt` lifecycle observer: `ON_STOP -> p.hide()`, `ON_START -> p.show()` |
| A game started through `launchNow` with a screen plan, layout INFO / Performance / BLACK | `CompanionActivity` (own task, `taskAffinity=...companion`, `excludeFromRecents`, NEW_TASK + MULTIPLE_TASK, `setLaunchDisplayId`), started 350 ms before the game | `LauncherViewModel.launchNow`, `ui/CompanionActivity.kt` `start()` |
| Layout "Game, leave the other screen" (KEEP) | nothing new is started, and the Presentation hides on ON_STOP | `launchNow`: `OtherScreen.KEEP -> true` |
| Layout "Emulator uses both screens" (NATIVE, NDS / 3DS / Eden Duo) | whatever the emulator draws there | `launchNow`: `emulatorUsesBoth` |
| App opened on the top screen (app drawer, dock, shortcut, Continue playing app) | nothing new is started, and the Presentation hides on ON_STOP | `LauncherViewModel.launchApp` -> `GameLauncher.launchAppOn` (no companion path at all) |
| "Use the second screen" off | nothing from Voltcap (`currentScreen()` is null, no plan) | `currentScreen()` |
| Voltcap back in front > 3 s after a launch | companion finished (`closeAll`), Presentation shown again | `onLauncherResumed` (`lastPlacementAt` guard) |

Whenever the table says "nothing", Android needs some window on that display, and on Android 10+ it starts the display's
**secondary home**. Voltcap declares no `android.intent.category.SECONDARY_HOME` activity (AndroidManifest: `HomeAlias` has
HOME + DEFAULT only), so Android uses the system's own secondary launcher there (AOSP: the default home package's
SECONDARY_HOME activity if it has one, else `config_secondaryHomePackage`) - that is the "Android Home" on the lower screen.

## Code paths that give "game on top, Android Home below"

1. **App launches never place anything below (most likely for an Android game such as Dead Cells started from the app
   drawer, dock, a shortcut or Continue playing).** `launchApp` targets the top screen (`Display.DEFAULT_DISPLAY`) and only
   handles the case where the app goes to the SECOND screen (`SecondScreenGate.suppress()`). The game path's
   CompanionActivity / BLACK / KEEP choice (screen layouts) is not consulted for apps. MainActivity stops, the Presentation hides
   (update 326) and the secondary home appears. A library entry of type APP started with Play goes through `launchNow` and DOES
   get the companion - so the same game can behave differently depending on where it was started. Verdict: **VoltCap gap**
   (inconsistent with the game path), not an Android limitation.
2. **Layout KEEP ("Game, leave the other screen" / blurb "Nothing new is opened there").** Since update 326 the Presentation
   hides when Voltcap stops, so "leave as is" in practice means "Android Home". Verdict: **VoltCap bug in meaning** (the
   label promises Voltcap's screen stays; the 326 fix removed it because the paused composition froze).
3. **Home pressed / home gesture while the LOWER screen has focus** (after a touch on the companion or the game's own lower
   UI). Android sends Home to the focused display: it starts the secondary home on the lower screen; the game keeps running on
   top and Voltcap does not come back on either screen. Because Voltcap has no SECONDARY_HOME, this is the stock launcher.
   Verdict: **Android behaviour that VoltCap could own** (by declaring SECONDARY_HOME), probably the "odd two-screen behaviour
   when switching" in the video - to confirm on HW-19.
4. **Companion closed early.** `closeAll()` runs on every MainActivity resume more than 3 s after the launch. If something
   resumes Voltcap briefly while the game keeps running (Recents on the top screen, a notification shade action, the
   in-game Quick menu bringing Voltcap forward and the user going back to the game through Recents), the companion is finished and
   is not started again when the game returns -> Presentation hidden again on stop -> secondary home. Verdict: **VoltCap gap**
   (the companion follows "Voltcap resumed", not "the game ended").
5. **Companion start refused silently.** `CompanionActivity.start` counts any `startActivity` without an exception as
   placed. A display that refuses activities, or a background-start block, gives no exception -> the game starts with nothing
   below. Low likelihood on the Thor (Voltcap is in front at that moment).
6. **Second screen off** in Settings: intended (the owner chose no Voltcap there).

Not a cause: the 350 ms order (companion first, game last so the game takes the controller) and the failure path (game did
not start -> companion closed, Voltcap brought forward on display 0 after 700 ms; update 256) both behave as designed.

## Lifecycle notes (for the fix)

- Android 10+ multi-resume: the game (top) and the companion (bottom) are both RESUMED; only one is top-resumed (has the
  keys). MainActivity uses `onTopResumedActivityChanged` for `voltcapOnTop` / BatterySaver - correct.
- The Presentation belongs to MainActivity: it cannot live while MainActivity is stopped without freezing (update 326). Anything
  that must stay on the lower screen during a game has to be an ACTIVITY on that display (CompanionActivity) or a
  SECONDARY_HOME activity.
- CompanionActivity is `excludeFromRecents` in its own affinity: it never shows in Recents, so Recents switching cannot bring
  it back once finished.
- `CompanionActivity.backToVoltcap()` starts MainActivity with `setLaunchDisplayId(0)` + REORDER_TO_FRONT, then finishes:
  the lower screen goes back to the Presentation once MainActivity starts - correct.

## Options (owner decision - nothing implemented)

| Option | Fixes | Cost / risk |
|---|---|---|
| A. Apps on the top screen follow the screen layout like games (companion INFO / BLACK below, KEEP as chosen) | path 1 | small, contained in `launchApp`; unit-testable (Robolectric); on the Thor only owner-driven Level 2 (the QA app blocks launches) |
| B. KEEP keeps a LIVE Voltcap screen: start CompanionActivity in a "second-screen home" mode instead of nothing | path 2 | medium (the companion would host the second-screen home content); changes what KEEP means - owner's pick |
| C. Declare a SECONDARY_HOME activity (a light CompanionActivity mode) on `HomeAlias`'s package | paths 3-5 (any time Android needs a home below) | Android only uses it while Voltcap is the default home, i.e. the PRODUCTION app on the owner's Thor: verifying it is Level 3 (owner approval) - the QA app is not the home app |
| D. Companion follows the game, not Voltcap's resume: close it when the game's task ends (RunningGames) instead of on every resume > 3 s | path 4 | medium; needs a game-ended signal that is reliable on the Thor |

Recommendation for the owner: A first (consistency, cheap, unit-testable), then C if HW-19 shows path 3.

## What HW-19 must capture (adds to the queue entry)

The isolated QA app can NOT reproduce this: `TestHooks.interceptLaunch` blocks every game and app start in it (update 500,
"the isolated QA app never starts a game - no file or switch can allow it"), so no companion / launch path runs there. The
evidence therefore has to be LEVEL 2 (production read-only): the OWNER starts things in his real Voltcap on the Thor with his
own hands; Claude only reads, with no input into the production app:
`adb -s 3140972f shell dumpsys activity activities | grep -E "Display #|ResumedActivity|topResumed"` and
`adb -s 3140972f shell dumpsys window displays | grep -E "mCurrentFocus|mFocusedApp"` after each step (ask the owner which screen layout the game
uses - it lives in Voltcap's prefs, not read). Steps for the owner: (a) a ROM game with the default layout "Game + info", (b) an Android app / game (Dead Cells)
from the app drawer or dock, (c) the same Android game from its library entry with Play, (d) during (a), touch the lower screen
then press Home. Expected per this reading: (a) CompanionActivity on display 1, (b) the system's secondary home on display 1,
(c) CompanionActivity, (d) the secondary home on display 1 while the game keeps running on display 0.
Options A / D get Robolectric tests in the cloud; their device check is again owner-driven Level 2 on a published build.
