# Overnight session 12 Nov 2026 — what's ready, what's pending

Nothing was pushed, merged, released or installed. v0.6.70, `main`, FW1 and the production app are untouched.
No input was sent to any handheld: both failed the gates every time they were checked (KONKR not answering status
queries, i.e. asleep; Flip 2 with the real Voltcap in front). Hardware QA stays **BLOCKED — OWNER ACCESS REQUIRED**.

## Android work — branch `feature/popup-native` (from v0.6.70, 11 commits)
Status of everything below: **IMPLEMENTED + PC-TESTED** (Kotlin 2.2.10 + Compose type-check against the app's real jars:
0 errors; all 1074 unit tests run with real JUnit 4.13.2 — no failure that v0.6.70 doesn't have in the same harness (134
fail on both: Android stubs without `isReturnDefaultValues`); static_checks PASS, summary identical to v0.6.70).
**PC Gradle build: PENDING** (the PC tool builds only the `fw1` / `ci` worktrees). **Hardware: PENDING.**

| What | Where |
|---|---|
| Storage boxes keeps its own Details / Achievements / Options (the "Storage → unrelated Details" regression: `SceneBody` swapped every scene's pages for the shared page); record sheets file in from the D-pad side, the tab paper slides | `WinPageFlow.kt` (new), `GameWindow.kt`, `WinScenes.kt` |
| Night menu pages in a big slanted panel; panels sweep along the slant; banner wipe with a dark band (never empty) | `WinScenes.kt` |
| Tiles: the card's writing turns over like a tile per page | `WinSceneFamilies.kt` |
| Blades: page blades slide in over / away to reveal; the covered game blade keeps its whole name in two lines on its edge | `WinScenesConsole.kt` |
| All four: L1 / R1 walk only the pages the window has; covered / leaving controls are disabled; focus enters the new page at once | shared |
| Every layout window: B back focuses the opener directly — Play was lit ~60 ms after B (a quick A started the game) | `GameWindow.kt` |
| Popup-first: Card binder and Pocket Quest open the window on A unless "A plays" was saved (same keys) | `data/Features.kt` |
| Game shelf / Case stacks: no picture flash as the case comes out (window copy used the cover until its scan loaded; home copy hidden a frame early) | `WinSceneFamilies.kt`, `ShelfHome.kt`, `CoverCases.kt`, `GameWindow.kt` |
| 3D case spines also cached by picture address (re-decode no longer shows the dark strip) — owner's live flicker NOT confirmed | `Case3D.kt` |
| Window achievements line keeps its total ("141 /" audit 5a) | `GameWindow.kt` |
| Settings inventory (read-only, 734 prefs, generated) | `docs/SETTINGS_INVENTORY.md`, `tools/settings_audit/` |

## To build it on the PC (your OK needed — tooling change + restart)
1. Copy `server.js.popup_allowlist_PROPOSED.js` over `tools\adb_mcp\server.js` (one added line: `pnat` worktree → `feature/popup-native`), restart the Claude desktop app.
2. `worktree_prepare pnat popup_native_20261112.bundle` → `gradle_start assembleDebug worktree pnat` (QA app identity, debug key).
3. Then hardware QA under the gates, as for Connected Ignition.

## Connected Ignition — branch `feature/connected-ignition`
PC: built on the PC 11 Nov (QA package, debug signer, secrets clean); cloud re-run tonight: type-check 0 errors, 1061 tests
with no new failures, static checks PASS. Hardware QA checklist + gated helper ready (`docs/CONNECTED_IGNITION_HW_QA.md`,
`tools/qa/device/ci_qa.sh`). Finding (not a regression — v0.6.70 has the same gap): an activity recreated mid-intro loses
the start-up picker.

## Popup Motion Review (prototypes) — re-verified
Executed results only; 32/32 at 30 and 60 fps; self-test 27/27; a thrown check fails the run. Notes in `POLISH_MOTION_REVIEW.md`.

## Open / needs you
- Hardware QA for everything above (both devices blocked tonight).
- Q-2 physically convincing 3D flip: plan only (shared-corner hinge, per-face shading by angle, edge highlight) — needs frame-by-frame review on a device.
- Audit 5a title-over-subtitle overlap: not reproduced without a capture.
- Night menu: very long names scroll slowly in the subtitle / selected home bar (compromise, as reviewed).
- Settings cleanup: inventory only; `box_sides_chosen` is written and never read (candidate to retire, your call).
