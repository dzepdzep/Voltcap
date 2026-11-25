# v0.7.0 release readiness (25 Nov 2026, evening)

(Same as the claude.ai project doc claude/RELEASE_0.7.0_READINESS.md.)

Status: **NOT ready. No merge, production install or release without the owner's GO.**
- Branch `claude/v0674-native-pages`, commit `d08182f` (682bb fixes `6f6fd73` + the hourly run's 755a). QA app `qa682bb`.
- CI on `d08182f`: Verify running at the time of writing. Last finished Verify GREEN on `8dfd4f6` (755a).
- Arcade B: on `claude/exp-arcadeb-focus` (`1257ec6`, Verify GREEN). Not merged.
- Versioning is unchanged (0.6.73 / QA identity). It changes only when release preparation is authorised.

## 1. Implementation: 53 / 53
Every layout draws its own Details, Achievements and Options pages. Disc station (682az) was the last one.
This is implemented only: it is not hardware-approved and not visually approved.

## 2. Hardware QA coverage (QA app only, synthetic games, 0 launches throughout)
| Area | KONKR | RP Classic | Notes |
|---|---|---|---|
| Visual capture: all 53 layouts, Home + 4 pages, x1.0 / x1.3 | done | done | v0.7.0 gallery (qa682az / az2, before the 682bb fixes) |
| Play same place across pages, 53 layouts x 4 | 2 defects | 2 defects | Graded from the gallery dumps (playplace_hw.py). Found: Cardboard box x1.3, Pocket camera RP Classic. Both fixed in 682bb; hardware re-check pending |
| 723a / 724a / 725a features | PASS | PASS | APK-update persistence NOT TESTED on hardware (unit covered) |
| Record crate / Dock safety (D393) | PASS | PASS | |
| Spotlight, Terminal, Dashboard, Library, Card binder pages | PASS | PASS | P2 review on b06044b (older build) |
| HW-28: 23 class C layouts, functional | NOT TESTED | NOT TESTED | Runs prepared (HW-29, qachain.sh); stopped because the PC left the bridge |
| Batches 6-7 (Tile home, Grid, Night menu, Blades) | NOT TESTED | NOT TESTED | Same runs |
| Disc station popup geometry gate | NOT TESTED | NOT TESTED | Same runs; PlayPlace / AchFirstRow pass in tests |
| 682bb fixes on hardware | NOT TESTED | NOT TESTED | Emulated page sheets only (Disc station, Cardboard box, Pocket camera) |

## 3. Visual defects
Fixed in code (682bb); hardware check and owner preview still pending:
- Green core home x1.3: PLAY TIME value slid ("AYED N"). Now the label and value share a line only when both fit whole;
  otherwise the value sits under its label.
- Disc station PROGRAM: the MODE key was too narrow. The deck now uses its own short words (IN ORDER, TO GET FIRST ...); the key
  sits on the PROG line when it fits, otherwise on its own line; the music calendar joins the track line.
- Categories / Collections and every shared row description: wrapped lines at x1.3 had a ~31 sp gap; now 16 sp. One-line rows
  are unchanged.
- Cardboard box x1.3: Play was out of view on its pages.
- Pocket camera on the RP Classic: Play moved on Details, and the picture covered the name.

Open (minor; candidates to defer): D336 (Classic capsule progress line ends in an ellipsis at x1.3, rule 11); Pocket camera
ALBUM order key narrow (its words slide); a banner-shaped "cover" dithers to a flat picture (RP Classic QA data); static
rule-11 count on this branch TRUNCATE 66 (the dev line is at 51; integration brings that work in).

## 4. Performance
- Arcade B: about 100 ms stall per game change on the RP Classic Arcade (p95 95-110 ms). The experimental focus-save change
  removes the second whole-home rebuild (40-50 % fewer rebuilds, fewer slow frames) but does not change the stall itself. Not a
  correctness issue: include/defer decided separately.
- Logged, not blocking: KONKR still home drawing ~60 fps; D322; D64; D24; D379 / D386 (partly fixed).

## 5. Safety / data integrity
- No accidental launches in any QA run (the gallery's 212 layout runs and the safety runs all logged 0).
- Library rescan bug 681r: fix on `claude/v0674-rescan-fix`, unmerged; HW-25 PASS except the SD-card-out case; merge conflicts
  in LauncherScreen.kt / TestHooks.kt. Recommended in v0.7.0 (data integrity).
- Play / chip anchor rule 682e on `claude/v0674-play-anchor`, unmerged (conflicts in TestHooks.kt and QA tooling) - owner decision.
- Production VoltCap untouched throughout.

## 6. Integration (needed before any release candidate)
Dry merge into the native-pages branch: `main` (74 commits) clean; dev line `claude/qa-0672-repair` (22 commits, rule-11 work
748a-750a) docs-only conflicts; rescan fix and play anchor conflict in code. A v0.7.0 integration branch is needed, with Verify
GREEN on that exact commit, then the gates in tools/release/PRE_RELEASE_CHECKLIST.md.

## Blocks v0.7.0
1. Hardware check of the 682bb fixes and the owner's targeted preview of the corrected screens.
2. HW-28 functional QA (class C, batches 6-7, Disc station popclip) on both handhelds.
3. Owner visual approval.
4. Owner decisions: rescan fix (recommended in), play anchor rule, Arcade B focus-save (in or defer).
5. Integration branch + green CI on its exact commit; version bump to 0.7.0 only when authorised; signed build, upgrade test
   and publish per the checklist.

## Can be deferred
Arcade B stall; the performance backlog; D336, Pocket camera ALBUM order key, banner-art dither; D332 (Flip 2), D342 (RP Nova);
APK-update persistence on hardware.
