# Integration recommendation: toward 0.6.71 (12 Nov 2026, local; nothing merged into main)

## Where things stand
| Line | Tip | What it is | Ready for main? |
|---|---|---|---|
| `origin/main` | 65f7cca | Published v0.6.70 / 77 (tag v0.6.70 = cc646b4) + release record | (the target) |
| `feature/popup-native` | 15af3d5 | 61 commits on the local v0.6.70 base 6c6c98c: native popups (Storage, Night, Tiles, Blades), popup infrastructure (one presentation decision), popup-first defaults for Card binder / Pocket Quest, focus fixes, the Game shelf flight / hand-over fixes, text-fit fixes (rule 11), Spine-flicker trace, settings inventory, tests (1,698+); 3D Shelf code present but **off** (`Shelf3D.enabled = false`) | Almost, see blockers |
| `exp/shelf-3d-flip` | 4fcc2eb | popup-native + 3D Shelf **on** + `-qa-s3d` build name | **No**: experimental, under review |
| `feature/connected-ignition` | 38802b1 | Connected Ignition (owner-approved design, PC-tested) | No: hardware QA not run (BLOCKED-HW) |
| `integration/0.6.71` (new, local) | 13b727c | popup-native joined with main's history | The candidate to merge (fast-forward of main) |

**History:** main's app code equals the local v0.6.70 base (`git diff 6c6c98c origin/main -- app/` is empty); the two lines only diverged in commits. A plain `git merge` uses the old base a1f762c and conflicts in 10 files. Merged with 6c6c98c as the base (`git merge-tree --merge-base 6c6c98c`) it is clean. `integration/0.6.71` is that merge, made with `commit-tree` and both parents kept: no rewrite, no force. It brings in main's release record and docs. On fast-forward, main stays a fast-forward.

## Recommended path
1. Push the three development branches (authorised; waiting only on the tool restart, below).
2. Re-run the Blades popup gate and a ×1.3 popup run on hardware (below), then mark the four native layouts HARDWARE VERIFIED in `coverage.json`.
3. Build `integration/0.6.71` (`assembleRelease` check + `testDebugUnitTest` + `lintRelease`: no new findings) and run the release checklist (`tools/release/PRE_RELEASE_CHECKLIST.md`).
4. Fast-forward main to it; bump to 0.6.71 / 78 with the release tool; publish only on your go.
5. Keep `exp/shelf-3d-flip` and `feature/connected-ignition` out of the release. The 3D code ships disabled (off is the stable branch's value; no setting, no hook in release builds). Connected Ignition stays on its branch until its hardware QA.

## What still blocks a safe merge and release
1. **Blades native popup, R26 FAIL on hardware (Flip 2, ×1.0).** The covered blade's title showed in the strip beside the page heading. A fix is in (15af3d5, compiled, unit tests running) but **not re-verified on hardware**. (Its other R26 failure, "CRASH BANDICOOT cut by 262 px", is the standing name drawn turned 90°; the checker measures the unrotated box. That's a checker false positive to fix in `geomcheck`, recorded, not app work.)
2. **×1.3 text on the native popups: NOT TESTED.** The QA app's in-app scale hook didn't change the popup text on the Flip 2, and changing the system font size is not allowed. Needs another way (a QA-app text-scale preference read by the popups) or your manual check.
3. **Ten hardware-pending layouts** (`coverage.json`: TILES, GRID, TV, SHELF, STACKS, CARDS, POCKET, STORAGE, NIGHT, BLADES).
   - Storage, Night and Tiles now pass R26 at ×1.0 on the Flip 2.
   - The others (and the bumper / onepress / catswitch checks) are still pending.
4. **Popup-first default change** (Card binder, Pocket Quest: A opens the game window unless "A plays" was set). This is a user-visible behaviour change and needs your OK in the release notes.
5. **Release provenance:** the QA APKs tested are not registered in `builds.json`, so the evidence ledger grades the runs INFRA. Register the integration build before the gate run.
6. **lintRelease** still exits 1 on 49 errors from before this work (no new ones). Same as v0.6.70, so not a new blocker, but the release checklist expects you to accept it.
7. **Local tag mismatch:** the local `v0.6.70` tag points to 6c6c98c, GitHub's to cc646b4 (same app code). Never push tags from this clone; fix the local tag only with your OK.

## Not blocking the stable release (experimental, stays off)
- **3D Shelf (Flip 2, final build b56e316/daef44e):**
  - Fixed: artwork continuity, crop and shape; hand-over and resting case match the shelf; tilt 14°.
  - **Open:** the 3D case first appears ~40° into its turn (a pop). Not caused by dropped frames: none over 20 ms. A per-frame trace build (bf6ab00) is ready for the next device run.
  - Not approved; your visual review is needed.
- **Custom-spine flicker on the old shelf:** not reproduced on the Flip 2. The trace covers the 3D path only.
