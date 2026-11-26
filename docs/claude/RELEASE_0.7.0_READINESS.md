# v0.7.0 release readiness (25 Nov 2026, 23:40)

# Update 26 Nov 2026, ~06:25
- x1.0 HW-28 Play placement (playplace_hw.py): KONKR 28 / 28 PASS, RP Classic 28 / 28 PASS. x1.0 walk complete on both.
- Upgrade v0.6.73 QA -> RC: RP Classic PASS 0 changes (KONKR earlier PASS 0).
- 681r on the RC build, RP Classic: c0-c7 PASS + sd1 (card game listed) - 16 PASS, 0 FAIL. Card out / back: NOT TESTED (the
  card was not taken out within the 3-minute window); the script restored the QA files, the card's stand-in removed.
- Tuner targeted preview (simulated tuner, QA app): KONKR x1.0 / x1.3 and RP Classic x1.0 / x1.3 captured and sent. Note: the
  QA scale hook (voltcap_test_scale) reaches the Heat guard sheet but not the Settings window or the R1 panel - those show x1.0
  in the x1.3 sets. At x1.3 the sheet's "Now: -N %" line sits below the fold (it scrolls; Close stays outside the scroll).
- D402 (QA script, fixed): an unguarded tap in the R1 panel set the KONKR's media volume 2 -> 7; the owner set it back down
  himself; the script now taps only with a before / after volume check and never during the panel's slide-in.

# Update 26 Nov 2026, ~01:20 - release-candidate branch `claude/v070-rc`
- RC = main + dev line (`claude/qa-0672-repair`) + P2 (`claude/v0674-native-pages`) + 681r (`claude/v0674-rescan-fix`, reviewed
  against P2: kept P2's libApp hook and 681r's extraRoot / libdump hooks; run_trial push lists combined). A merge slip in
  TestHooks.kt broke the first RC commit (08b08da, Verify FAIL) - fixed in 7eaccbd. **Verify GREEN on 04baf3f** (code identical
  to 7eaccbd; later commits are QA scripts / builds.json only). RC QA build `qa-070rc-7eaccbd`.
- HW-28 walk **x1.0: PASS on both** (P2 build): KONKR and RP Classic each 28 / 28 layouts, 0 launches, 28 destructive rows armed
  and cancelled, fatal 0, restore diff 0 in all 7 batches. (Play-placement grading of the screenshots: NOT run - PC link lost.)
- On the RC build: upgrade v0.6.73 QA -> RC **PASS 0 changes** (KONKR, 3 snapshots); RP Classic snapshots taken, diff NOT run
  (PC link lost). 681r rescan walk (KONKR): **15 / 15 PASS** (only game deleted -> gone + category gone + All; twice; restart;
  one of two; folder game; re-added keeps its identity); restored. SD card out / back: **NOT TESTED** - needs a person to pull
  the RP Classic's card (the script waits for it; nothing is unmounted by software). Unit: RescanPrune681rTest PASS on RC.
- Tuner targeted preview (QA app, simulated tuner): KONKR x1.0 captured (tools/qa/device/tunerprev.sh); KONKR x1.3 started,
  RP Classic x1.0 / x1.3 NOT captured (PC link lost). Not approval.
- 682e: examples sent to the owner; no behaviour change. Native runtimes: separate branch `claude/native-runtimes` (502bb65).


(Same as the claude.ai project doc claude/RELEASE_0.7.0_READINESS.md. Full audit: docs/claude/AUDIT_0.7.0.md.)

Status: **NOT ready. No merge, production install or release without the owner's GO.**
- Branch `claude/v0674-native-pages`; last code commit `f094d6c` (QA build qa-682bh). CI: Verify GREEN on `b4e37b2`; the
  Verify run on the newest commits was still running when this was written - a release candidate needs green on its exact
  commit anyway.
- Arcade B: on `claude/exp-arcadeb-focus` (`1257ec6`, Verify GREEN). Not merged.
- Versioning unchanged (0.6.73 / QA identity) until release preparation is authorised.

## 1. Implementation: 53 / 53 native layouts
Every layout draws its own Details, Achievements and Options pages. Implemented; not owner-approved.

## 2. Hardware QA (QA app only, synthetic games, 0 launches throughout)
| Area | KONKR | RP Classic |
|---|---|---|
| Visual capture, 53 layouts x 4 pages, x1.0 / x1.3 (gallery) | done | done |
| 682bb review fixes (Green core, Disc station MODE, row line gap, Cardboard box, Pocket camera) | PASS | PASS |
| Stress section (long names, no art, 120 achievements, x1.3) | captured | captured |
| HW-28 functional walk, 28 class-C layouts, x1.3 | PASS (28, 0 launches, 28 confirmations cancelled, Play in place) | PASS (same) |
| HW-28 walk at x1.0 | NOT TESTED | NOT TESTED |
| Popup geometry gate, 28 layouts, x1.0 + x1.3 | PASS 56 / 56 | PASS 56 / 56 |
| Upgrade v0.6.73 -> v0.7.0, settings kept | PASS (0 changes) | PASS (0 changes) |
| Restart / rescan / layout previews / Edit Home discard | PASS (0 changes) | PASS (0 changes) |
| Tuner on simulated hardware: profile source, explicit Off, Heat Guard, Recents, precedence, R1 tile | PASS | PASS |
| Real tuner clocks (AYN / Retroid vendor service) | NOT TESTED (QA app never uses root) | NOT TESTED |

## 3. Visual defects
Fixed and hardware-checked: Green core PLAY TIME, Disc station MODE, Categories / Collections line gap, Cardboard box Play,
Pocket camera, the key row that stopped between keys after More (D397). Open, minor (deferrable): D336, D399, D400.
New screens for the owner's look approval: the tuner's notification wording, the R1 Heat guard tile and its sheet, the
tuner page's Heat guard rows.

## 4. Performance
Arcade B stall (~100 ms per game change, RP Classic) - isolated on its branch, include / defer is the owner's call. No new
performance work in this audit.

## 5. Safety / data integrity
- Upgrade, backup / restore, snapshot undo, cross-device import, repeated migrations: no data lost or changed (audit summary).
- Explicit per-game Underclock Off is kept, named and never moved by AutoTDP; the Heat Guard's cut is temporary and shown
  apart; the firmware's thermal control is never written.
- D401 fixed: the stock-clock record no longer travels with backups.
- **Library rescan with the SD card out (681r): fix on `claude/v0674-rescan-fix`, not on this branch - BLOCKER until the owner
  decides (recommended in).**
- Production VoltCap untouched and never read.

## Blocks v0.7.0
1. 681r rescan fix - owner decision (recommended in), then its hardware check on the integration build.
2. Owner visual approval (targeted preview + the new tuner screens).
3. Owner decisions: play-anchor rule (682e), Arcade B focus-save.
4. Integration branch (main + dev line + this branch), Verify GREEN on that exact commit, versioning when authorised, signed
   build, upgrade test, publish per tools/release/PRE_RELEASE_CHECKLIST.md.

## Can be deferred
HW-28 walk at x1.0; D336 / D399 / D400; the scope review of 31 unlisted settings files; a real-hardware tuner check on an
AYN / Retroid handheld (production, owner-approved steps); the performance backlog; APK-update persistence on the real app.
