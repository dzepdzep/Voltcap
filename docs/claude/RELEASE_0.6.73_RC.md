# v0.6.73 release candidate - prepared, waiting for the owner's GO

Prepared 17 Nov 2026 (live session 678p). Nothing merged, built-for-release or published - each of those waits for the owner's GO.

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
- Badges on game pictures never overlap or cover a name (also Channel at larger text).
- The Achievements page opens on your new unlocks.

Handhelds
- Hide navigation bar works on Retroid handhelds too.
```

## Verification
| Gate | State | Evidence |
|---|---|---|
| CI Verify 310 (unit tests + debug build) on the RC commit | see CI | - |
| D333 rapid A B A B (HW-5) | see HARDWARE_QA_QUEUE HW-5 | KONKR crate678.sh 10 rounds per layout / scale on qa-0.6.73-678p-ci |
| Full popup gate KONKR x1.0 on the RC build | see HW-1 | - |
| KONKR x1.3 full gate | PASS except D333 + Channel (fixed) on 678n | k678n13, kc678o13 |
| RP Nova / RP Classic | earlier builds | HW-17, HW-7 |
| Signed release build + freeze_rc + secret scan | NOT STARTED | needs main = RC (after the GO), on the PC (keys live there only) |
| Upgrade over the real Voltcap on one handheld + smoke test | NOT STARTED | owner's GO (Level 3) |

## GO steps (after the owner says GO)
1. Merge the PR into main (required checks green).
2. PC main tree: pull main, `assembleRelease` + compat, `freeze_rc.py 0.6.73 rc1`, apk_secret_scan, signer = v0.6.72's.
3. Upgrade test on one handheld (owner picks which): the frozen RC over the real Voltcap, smoke test, data kept.
4. Publish with release.ps1 0.6.73 and the notes above; production pins for 0.6.73.
