# v0.6.73 release candidate - prepared, waiting for the owner's GO

Prepared 17 Nov 2026 (live session 678p / 678q). RC commit: be8865c (draft PR #21); candidate update 678r (Channel D350 one-line plate, owner handoff 17 Nov) - RC moves to it once CI is GREEN and, if the KONKR is connected, a short Channel check passes. Nothing merged, built-for-release or published - each of those waits for the owner's GO.

## Candidate
- Branch `claude/qa-0672-repair` at the RC prep commit (version 0.6.73 / versionCode 80). Base: main 5b01ef39 (v0.6.72); the
  dev line fast-forwards main (PR to main, 2 required checks).
- Out (owner): Save Sync 529 (v0.6.74, after real Drive tests), Flip 2 charging fix (device unavailable), whole-word fitting
  (v0.6.74), pixel-font polish at x1.3 - Pocket Ribbon, Pocket Classic, Night (v0.6.74, owner 17 Nov: does not delay v0.6.73).

## Release notes (draft - the GitHub release body is what "What's new" shows after the update)
```
Voltcap 0.6.73

Game windows
- Every game window's buttons are one size and in one row or one column, MORE last; nothing is cut or squeezed.
- Record crate at larger text: a vertical menu.
- Disc station: the disc stays in the tray for info and lifts out for Inspect; its own track pages.
- Pressing A B A B very fast no longer leaves a window open (TV room, Record crate and the other layouts).
- Banner style: the Save sync chip and the L1 / R1 tabs are easier to tap.

Text and screens
- Long names and labels are shown whole (they wrap, shrink a little or slide) instead of ending in "..." - Console homes,
  Settings, Save Vault, sync prompts, category screen, the year recap and more.
- Larger-text fixes on small screens (Airy, Blades, Orbit, Circuit, Library, Dashboard, File select, Terminal, Rental shop, Cinema).
- Status bar: RetroAchievements points are never squashed; less important icons step aside first.
- One profile picture on Console Hub, Resume, Spotlight and Channel.
- Badges on game pictures never overlap or cover a name.
- The Achievements page opens on your new unlocks.

Handhelds
- Hide navigation bar works on Retroid handhelds too.
```

## Verification
| Gate | State | Evidence |
|---|---|---|
| CI Verify 310 (unit tests + debug build) | PASS | GREEN on be8865c (push + PR #21); QA tooling checks PASS on PR #21 |
| D333 rapid A B A B (HW-5) | PASS | KONKR 678p: Record crate 20/20, TV 20/20 (x1.0 + x1.3), Cardboard box 10/10, Card binder 10/10 |
| Record crate checklist (HW-2) | PASS | KONKR x1.0 + x1.3 (k678p_crate) |
| Full popup gate KONKR x1.0, RC build | PASS 53/53 | krc10 on qa-0.6.73-rc-91b1ac3-ci; final RC be8865c: 6 touched layouts 6/6 (krcq10) |
| Full popup gate KONKR x1.3 | 52/53 | k678n13 (678n) - Channel two-line name over the count (D350): 678r keeps the plate one line; cloud robo idle x1.3 PASS (EndlessOnceRoboTest 280/280); KONKR Channel x1.3 recheck pending |
| Banner style | PASS x1.0 + x1.3 | owner decision D351 (corner X is chrome) |
| Owner visual review | PASS | owner 17 Nov; pixel-font x1.3 polish -> v0.6.74 |
| RP Nova / RP Classic | earlier builds | HW-17, HW-7 (no regression expected: the RC changes since are the D333 window close and text fixes) |
| Signed release build + freeze_rc + secret scan | NOT STARTED | after the GO: needs main = RC, on the PC (keys only there) |
| Upgrade over the real Voltcap on one handheld + smoke test | NOT STARTED | owner's GO (Level 3) |

Known and accepted for v0.6.73: Channel x1.3 name over the count (D350), Twin icons badges 4-4.7 dp apart (D352), 'PlayStation' word
break at x1.3 on the category screen (D346, whole words), pixel-font x1.3 polish - all v0.6.74.

## GO steps (after the owner says GO)
1. Merge the PR into main (required checks green).
2. PC main tree: pull main, `assembleRelease` + compat, `freeze_rc.py 0.6.73 rc1`, apk_secret_scan, signer = v0.6.72's.
3. Upgrade test on one handheld (owner picks which): the frozen RC over the real Voltcap, smoke test, data kept.
4. Publish with release.ps1 0.6.73 and the notes above; production pins for 0.6.73.
