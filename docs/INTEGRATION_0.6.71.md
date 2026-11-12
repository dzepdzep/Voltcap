# v0.6.71 release candidate — release-readiness (12 Nov 2026, Sprint 525; not merged, not tagged, not published)

## Candidate
- **`integration/0.6.71` @ `c827ff9` (RC4)** — local branch and PC worktree `int` only. Parents: RC3 `b018aca` (popup-native
  8cb1ae8 + `main` 65f7cca) and `feature/popup-native` 07520c9. Tree = popup-native 07520c9; `main` (65f7cca) can fast-forward.
- The RC2 commit `ce48a1d` from the previous report existed only in a discarded cloud workspace (not on the PC, not on GitHub);
  RC3 / RC4 were made the same way and are what was built and tested.
- Experimental code ships off: 3D Shelf (`Shelf3D.enabled = false`, hooks only in the QA app / debuggable builds). Connected
  Ignition and FW1 are not in it.

## Build checks (RC4 c827ff9, worktree int)
assembleDebug (QA app; isolation + secrets) SUCCESS, sha256 `c3a45817…`; testDebugUnitTest executed in `int`, BUILD SUCCESSFUL;
lintRelease 49 E / 966 W / 109 H = baseline, no new findings; QA selftests 139/139. Ledger `qa-0.6.70-int-c827ff9`.

## Status per area (implemented / PC-tested / hardware functional / owner visual approval / release-ready)
| Area | Impl. | PC | HW functional | Owner visual | Release-ready |
|---|---|---|---|---|---|
| Native popups Storage, Night, Tiles, Blades | ✓ | ✓ | ✓ (R26 8/8 ×1.0 / ×1.3, popvid 0 launches) | **pending** | after your look |
| Night ×1.3 settled panel (Saves line) | ✓ | ✓ | ✓ RC3 | **pending** | after your look |
| Popup-first Card binder / Pocket Quest (saved "A plays" kept) | ✓ | ✓ | ✓ RC3 (×1.0, ×1.3, one-game) | — | ✓ |
| Endless once by default, one highlight (Fill on / off) | ✓ | ✓ (EndlessFillLitRoboTest 460, EndlessOnceRoboTest 279) | ✓ RC3, 8 layouts × 1/2/3/25 games | **pending** | ✓ functionally |
| Fill under a versioned key (no reset, old values ignored) | ✓ | ✓ (EndlessFillVersionTest) | ✓ RC3 upgrade from v0.6.70-era QA data | — | ✓ |
| Tiles edge slivers (each game once at the edges, Fill off) | ✓ | ✓ (TilesEdgeOnceTest) | not seen (no fitting category) | **pending** | ✓ functionally |
| Grid, TV room, Game shelf, Case stacks (popups) | ✓ | ✓ | ✓ RC3 ×1.0 / ×1.3 | **pending** | ✓ functionally |
| Old Shelf: slot glide, spine shape stability | ✓ | ✓ (ShelfSlotMotionTest, SpineShapeStable525Test) | ✓ continuous, 0 flashes | **pending** | after your look |
| Upgrade compatibility v0.6.70 → RC | — | ✓ | ✓ 733/734 Features keys identical (only Fill's key), appearance / categories / R1 identical, explicit A-plays kept, restart OK | — | ✓ |

## Release blockers
**None found.** No crash (crash buffer 0 through all runs), no unintended launch (every popvid run: 0 launches; the only
launches were the explicit saved "A plays" — blocked in the QA app as designed), no data loss (upgrade compared key by key),
no broken navigation in the tested layouts.

## Visual issues (your call, not blockers)
1. TV room: the theme's faint "IRON TIDE" word behind the game name on the name plate.
2. Old Shelf: while one case goes back and the next comes out, the two fronts overlap briefly mid-step (both moving).
3. Night ×1.3 panel now taller when needed; Tiles edge behaviour changed (one copy only, Fill off).
4. Shared popups (TV, Shelf, Stacks, Cards, Pocket): L1 / R1 don't walk pages (by design today).
5. Real-artwork review of the four native popups — the QA library has fixture art only.

## Experimental (not enabled in v0.6.71)
3D Shelf (first pull 29 ms, warm-up proposed, not built); Connected Ignition (hardware functional on the Flip 2, branch pushed).

## Deferred
Settings simplification proposal; small-screen (KONKR) pass; cold-start artwork recording at full length (recorder); owner's own
custom-spine flicker scenario.

## Recommendation
When you have looked at the visual items: fast-forward `main` to `c827ff9` (or to a later integration commit if you ask for
changes), then on your explicit go bump to 0.6.71 / 78 with the release tool and publish. Nothing touches main, tags or
releases until then.
