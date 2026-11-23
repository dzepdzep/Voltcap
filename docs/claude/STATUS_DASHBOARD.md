# VoltCap status dashboard

## OWNER SUMMARY (generated after every run - read this first)
<!-- GEN:summary -->
**1. LATEST UPDATE** - 710a · 2026-11-23 13:30 UTC · commit `this update (being pushed)` · CI: Verify 310 on 8f1ac4f: green · effort: medium

**2. WHAT CLAUDE JUST FIXED** - Game window performance page: each setting's name and value now show in full (wrapping, shrinking a little, then sliding) instead of ending in dots; the short story on the main page stays a preview, with the whole story on the Details page.

**3. LAST 24 HOURS** (most important first)
- **p2** (P2-23b): Owner's 23 Nov visual review: the 5 critical layouts (TV room option 2, Spotlight, Arctic, Arcade, Icon bar) and 7 polish layouts fixed; re-checked on both handhelds at x1.3 (QA app, 0 launches, settings restored); Play now stays put on every page of all 29 layouts. Before/after package published. Still open: Coverflow details reading space, page-change frame audit, TV strip one key at a time.
- **CI / build** (706a): The automatic checks kept failing because Maven Central refused downloads (too many requests), not because of a code problem. CI and cloud runs now download from Google's copy of Maven Central first; the owner's own builds are unchanged. Last run's change compiled and its test passed.
- **CI / build** (702a): The automatic check on GitHub kept turning red because Maven Central (where build libraries are downloaded) refused downloads, and its built-in retry never kicked in for the unit-test step. It now retries in that case too - real test or compile failures still turn it red at once.
- **App** (P2-23a): Native pages (P2 feature branch): 29 layouts now draw their own Details, Achievements and Options pages, and all 29 were walked on both handhelds at normal and larger text. Play now keeps the same size and place on every page in 28 of them (the TV room still needs your decision). A review package with real screenshots and short motion clips of every layout is ready; none is visually approved yet.
- **CI / build** (692b): Fixed a mistake in the last run's new check for the game card (its search pattern was too strict and failed in CI); the game card itself was already right.

**4. PROJECT PROGRESS** (implemented / verified on handhelds / approved by you / released)

| Area | Implemented | Verified on handhelds | Approved by you | Released |
|---|---|---|---|---|
| Home layouts | `██████████` 53/53 | `█████████░` 48/53 | `░░░░░░░░░░` 0/53 | `??????????` 53 (older state, v0.6.72) |
| Native pages (own designs) | `█████░░░░░` 29/53 · P2 branch only | `█████░░░░░` 29/53 | `░░░░░░░░░░` 0/53 · functional: 14 accepted functionally 22 Nov (batches 1-3), not visual approval | `░░░░░░░░░░` 0/53 |
| Pickup animations | `██████████` 14/14 | `??????????` 14 opened + no flash / black frame on the RP Nova x1.0 (pkn675, 16 Nov, build 8bd5e4a); look approval: owner | `░░░░░░░░░░` 0/14 | `??????????` UNKNOWN |
| Category styles | `██████████` 17/17 | `??????????` UNKNOWN on the dev line (all 17 checked 12 Nov on an older build) | `░░░░░░░░░░` 0/17 | `??????????` 17 (older state) |
| Save Vault + Sync | `████████░░` 10/12 | `??????????` 2 Flip 2 dry runs (A2, A8); real Drive: 0 | `??????????` UNKNOWN | `░░░░░░░░░░` 0/12 |
| Emulator QA (layouts) | `░░░░░░░░░░` 2/53 | emulator never counts as handheld | - | - |

**5. WORKING ON NEXT** - now: Verify 310 green on this push; then: P2 finish (live session): hardware checks, page-transition audit, Spotlight / Terminal / TV room / Coverflow decisions; Then Task 1: Row-position collisions; meanwhile rule 11 next screen (GameJourneyUi / RetroAchievementsDialog / DialogChrome)

**6. WHAT YOU NEED TO DO** - **NONE urgent.** When convenient: 9 items (see Owner actions below); cloud work continues without them.

**7. NEXT RELEASE** - current v0.6.73 · **v0.6.74: DEVELOPMENT** · 0 of 9 app fixes fully verified · 6 gates left (blocking: Reproduce the sparkle loop on the Flip 2 with production's own limit (read-only: logcat + battery state over 15 min)) · your approval needed: yes - when it is ready · window: UNKNOWN
<!-- /GEN:summary -->

**Links:** [this dashboard](https://github.com/dzepdzep/voltcap/blob/claude/qa-0672-repair/docs/claude/STATUS_DASHBOARD.md) ·
[CI runs](https://github.com/dzepdzep/voltcap/actions?query=branch%3Aclaude%2Fqa-0672-repair) ·
[dev branch](https://github.com/dzepdzep/voltcap/tree/claude/qa-0672-repair) ·
[hardware QA queue](https://github.com/dzepdzep/voltcap/blob/claude/qa-0672-repair/docs/claude/HARDWARE_QA_QUEUE.md) ·
[handoff](https://github.com/dzepdzep/voltcap/blob/claude/qa-0672-repair/docs/claude/HANDOFF.md) ·
[requirements matrix](https://github.com/dzepdzep/voltcap/blob/claude/qa-0672-repair/docs/REQUIREMENTS_MATRIX.md)

---
_How this page works: everything marked "generated" is rebuilt by `python3 tools/cloud/dashboard.py` from the evidence files
(docs/claude/run_reports.jsonl, progress.json, releases.json, owner_actions.json, the requirements matrix and the hardware
queue). Counts come only from evidence; UNKNOWN means not counted yet. "Implemented" is code on the dev branch, not finished
work. Emulator results never count as handheld verification. Release status is derived from the gates, never typed._

## RELEASE TRACKER (generated)
<!-- GEN:release -->
**Current stable:** v0.6.73 (a4012212, 2026-11-17) - Latest stable release (main, PR #21). Assets = frozen rc1 (release 72a94b58, compat 2b6b9e3c). Save Sync 529 not included.

### v0.6.73 - **PUBLISHED**
- Base: main 5b01ef39 (v0.6.72); dev branch claude/qa-0672-repair can fast-forward main. Scope: the stable batch on the dev line since v0.6.72 (updates 673a-675i); Save Sync 529 and the Flip 2 charging fix stay out; plus the 17 Nov status-bar / profile-picture / Storage fixes (H1-H3) - merged into the dev line 17 Nov (677l); whole words moved to v0.6.74 by the owner.
- Release window: PUBLISHED 17 Nov 2026 16:15 UTC (owner GO 15:22 Berlin, option 2: KONKR upgrade before publication)
- App fixes and features: 13 (13 implemented, 0 fully verified, 6 implemented but not yet tested on a handheld, 4 need your look or decision)

| Fix / feature | Handheld evidence | Your approval |
|---|---|---|
| Same-size game-menu buttons on every popup (one row or one column of identical keys, MORE last, nothing cut or squeezed) | popup gate on 53 layouts: 48 pass everywhere, 5 fixed since and need a re-run | review needed (only the Record crate menu is approved) |
| Record crate at x1.3: a vertical menu (WWE SvR 2011 style) | PASS Flip 2 + KONKR at x1.3 (15 Nov); owner's checklist (focus, rapid A/B, no size change) not run yet - KONKR session A | APPROVED 15 Nov |
| Disc station: the disc stays in the tray for normal info, the real disc lifts out for Inspect; its own track pages for Details / Achievements / Options | PASS KONKR x1.3, Flip 2 x1.0 / x1.3 (673d-f) | review needed |
| x1.3 screen-fit fixes (Airy, Blades, Orbit, Circuit, Library, Dashboard, File select, Terminal, Rental shop, Cinema) | PASS in the latest full gates | review needed |
| Wii channel buttons: one row of equal keys, worded MORE, More page without broken words | PASS Flip 2 x1.0, KONKR x1.3 (674n build); x1.3 Flip 2 / x1.0 KONKR not run on that build | review needed |
| Centred symbols on home hints (Arcade pointer, Pocket START, Bar arrows, Library L2 / R2, Spot START) | Arcade, Pocket, Bar PASS; Library and Spot fixed after the last run - re-test needed | not needed |
| Side menu: every key and the Save sync line fit the drawer without scrolling | fixed after the last run - re-test needed | not needed |
| Test and CI reliability (test-runner memory, emulator trial, QA checkers) | - | not needed |
| Hide navigation bar: Retroid pill fixed at its root (676b); polling removed | RP Nova: pill 12/12 states before, 0/14 after (676, 676c, 676d); Thor / KONKR / RP Classic NOT TESTED (HW-8) | APPROVED for v0.6.73 16 Nov (if CI + hardware regression pass) |
| Global badge lane (676a/c/e/f): console / achievement / NEW / score never overlap, never cut, never over a coverless name | RP Nova 676f: x1.3 CUBE/TILES/GRID popup 3/3 + badges 17/17, x1.0 CUBE 1/1 + 10/10; 676d x1.0 GRID/CUBE/TILES 3/3 + 26/26; KONKR / RP Classic NOT TESTED (HW-9) | APPROVED for v0.6.73 16 Nov (if CI + hardware regression pass) |
| QA safety: harness can't reach the owner's Voltcap (D334), QA levels, pinned QA key | D334 guard proven on the Nova (5 root Backs kept) | requested 16 Nov |
| Status bar: RetroAchievements points never squashed or split; when space runs out, less important icons step aside first | PASS RP Classic + Nova x1.0 / x1.3 (c677j, s677i); full Nova x1.3: 2 checker readings of the LCD clock decoration, fixed 677k - re-check pending (HW-17) | requested 17 Nov - keep in v0.6.73 |
| Profile picture shown once on Console Hub, Resume, Spotlight and Channel (the status bar hides its copy) | PASS RP Classic + Nova x1.0 / x1.3 | requested 17 Nov - keep in v0.6.73 |
| Storage Boxes facts kept whole ('6 / 10', 'Never played'), console name never cut | PASS RP Classic + Nova x1.0 / x1.3 | requested 17 Nov - keep in v0.6.73 |
| Console homes (Hub, Resume, Channel): long game names and the category label slide instead of ending in '...' (Hub '(No Glitc...' at Larger text) | NOT TESTED - HW-17 (RP Classic Larger text) | requested 17 Nov (H8) |

| Gate | State | Evidence |
|---|---|---|
| CI unit tests + debug build (Verify Android update 310) | PASS | GREEN on be8865c (RC: version 0.6.73 / 80) - push run + PR #21 run, 17 Nov; QA tooling checks PASS on PR #21 |
| Emulator QA trial (KONKR-like, x1.0 + x1.3) | PASS | run 44017399771 (678j, 9d7605f): success (verdict step passes only on PASS; EMULATOR evidence only, pre-merge tree). run 43961010406 (678c, 88e3457): VERDICT PASS - e529a (x1.3) and e529b (x1.0) popup gate PASS 2/2, COVERDETAILS + SHELF every graded check PASS, FATAL 0, owner snapshot restored diff=0 (EMULATOR evidence only, 2 layouts). Same run: the e529c touch walk tapped nothing (the game had no achievement set, so the pages were one off) - fixed 678d, next trial re-checks; not part of this gate. Before: run 43929836268 (677d, c45d0ab): FAIL, 1 check. SHELF PASS at x1.0 and x1.3; COVERDETAILS only buttons=FAIL (x1.3 More step, x1.0 Details page missing) - the device grep -iE missed keys its fixed-string read found in the same dump; 678a removes the regex from label reads (selftest), next trial decides. Before: run 43887216747 (677a, 4c332f1, first trial on the 675o dump fix): FAIL. x1.0: COVERDETAILS PASS on every check (first full PASS row); SHELF did not open on the first A ('read: Play keys probe 1, home 0'). x1.3: COVERDETAILS opened 1.5 s late + More step / Details page missing; SHELF Details page missing. No crash (FATAL 0). Earlier: 43833359452 (675m). Emulator evidence supports, never replaces, the handheld gates |
| Focused handheld re-check of the merged candidate on RP Classic + Nova (statuscheck + homecut on the status layouts; popup gate on Art library, Greencore, Library, Live, Terminal, Twin icons, Arcade, Discs) | PASS | PR #21 merged (main a4012212); GitHub release v0.6.73 published, assets byte-identical to the tested rc1 |
| Candidate fixes (branch claude/cube-statusbar-facet) merged into the dev line | PASS | PR #21 merged (main a4012212); GitHub release v0.6.73 published, assets byte-identical to the tested rc1 |
| Full popup gates on the KONKR + the other handhelds connected today, newest build (x1.0 + x1.3 where supported) | PASS | KONKR GT78 17 Nov: RC QA build qa-0.6.73-rc-91b1ac3-ci (678p + version) full x1.0 gate krc10 53/53 PASS (TV rapid fixed, D333); final RC be8865c (Channel revert) krcq10 Channel / Twin icons / Record crate / TV / Card binder / Cardboard box 6/6 PASS; rapid A B A B 60/60 (678p). x1.3 full gate on 678n k678n13 52/53 - Channel two-line name over the count (D350) - fixed in 678r, KONKR 17 Nov 15:07: QA build qa-0.6.73-678r-ci (eadd00f, CI Verify 310 GREEN on push) Channel x1.3 kc678r13 popup gate PASS 1/1 (all 11 grades); name plate one line + sliding, 48/120 fully readable; restored (font 1.0, density 240, owner snapshot diff=0). Review sheet: Claude outputs\live678n\KONKR_popups_678n_review.pdf |
| Flip 2 (unavailable): 15 Nov popup gates as historical evidence | HISTORICAL | f674k x1.3 52/53 (674k build), f674o x1.0 52/53 (674n build) - NOT the newest build. Owner 16 Nov: don't block v0.6.73 on the Flip 2; the gap is listed under risks |
| Record crate checklist (focus after More, rapid A/B, no size change) | PASS | KONKR 17 Nov after the D333 fix (qa-0.6.73-678p-ci, crate678.sh k678p_crate): x1.0 + x1.3 one key size while the focus walks, one press each PLAY -> SLEEVE NOTES -> INSPECT -> MORE, MORE open + B back on MORE, B closes, rapid A B A B 10/10 at each scale, no launch; nothing clipped (popup gates). Before the fix: rapid 9/10 (k678c2). |
| Owner visual review of the new menus and x1.3 fixes | PASS | owner 17 Nov: reviewed the KONKR sheet - designs good; pixel-font x1.3 polish (Pocket Ribbon, Pocket Classic, Night) to v0.6.74, not a v0.6.73 blocker |
| Signed release build, frozen RC, secret scan (main tree on the PC) | PASS | PC main a4012212: release + compat 0.6.73 (80) frozen rc1 (72a94b58 / 2b6b9e3c), freeze --verify OK, secret scan clean, signer 88ce4b67 = v0.6.72, debuggable=false, manifest / sync / charging / scopes unchanged |
| Upgrade install over the real Voltcap on one handheld + smoke test | PASS | KONKR real Voltcap 79 -> 80 from the frozen rc1, data kept (firstInstallTime unchanged), home + library + achievements shown, crash buffer 0 |
| Owner GO to merge to main and publish | PASS | PR #21 merged (main a4012212); GitHub release v0.6.73 published, assets byte-identical to the tested rc1 |
- **Risk / known gap:** Flip 2 (1920x1080, 360 dpi, x1.0 / x1.3): checked only on builds 674k / 674n (15 Nov). Fixes since then that touch layouts it shows (Side menu status line, Library / Spot hints) are not seen on a Flip 2. Mitigation: the same fixes pass on the KONKR / other handhelds today; the Flip 2's 15 Nov results had 1 FAIL each (Bar arrows - fixed 674n; Side status line - fixed 674o).
- **Risk / known gap:** Charging fix left out: the Flip 2 keeps v0.6.72's charge-limit behaviour (no change, no new risk).
- **Risk / known gap:** Small 4:3 screens at x1.3 (RP Nova 640x480 dp) showed 4 layout-specific FAILs on 8bd5e4a; the same layouts PASS at x1.0 there and on larger screens.
- **Left out on purpose:** Flip 2 charging sparkle fix (charge-limit pause read as unplugging) - owner 16 Nov: Flip 2 unavailable - postponed to v0.6.74 or later; never tested on the device. Kept: branch claude/charge-loop-674i + docs/claude/patches/charge-loop-674i.patch (re-applies cleanly); removed from the dev line in 675i, ChargeLimiter.kt identical to v0.6.72.

Published: this is the current stable release (a4012212, 2026-11-17). Nothing to approve.

### v0.6.74 - **DEVELOPMENT**
- Base: after v0.6.73. Scope: Flip 2 charging fix (674i) + Save Sync 529 - each only after its own end-to-end test and the owner's approval.
- Release window: UNKNOWN - the Flip 2 is unavailable
- App fixes and features: 9 (2 implemented, 0 fully verified, 2 implemented but not yet tested on a handheld, 0 need your look or decision)

| Fix / feature | Handheld evidence | Your approval |
|---|---|---|
| Flip 2 charging sparkle no longer replays while plugged in (charge-limit pause no longer reads as unplugging) | BLOCKED - DEVICE UNAVAILABLE (Flip 2); not failed - never run. Reassess first: is the loop still reproducible? | test when the Flip 2 is back (production limit off for one supervised test) |
| Save Sync 529 / 529c (verified backups, per-game queues, lineage conflicts, multi-file transactions, mid-game uploads, pre-launch check, restore) | Flip 2 dry run only (no Drive) | owner 16 Nov: v0.6.74 only after real end-to-end Drive tests + approval |
| Per-item 'Open on screen' shortcut in every context menu (long-press / START): Top / Bottom / Follow default; games also 'Screen layouts…' (docs/claude/SCREEN_SHORTCUT_PLAN.md) | AYN Thor (QA app, branch claude/v0674-screen-shortcut 8dab0b2): All apps START menu shows the row; picker Top/Bottom/Where I open it/Follow default; chose Bottom -> launch path chose display 4 (Screen-2) target=SECOND source=APP; restored. Android-game picker, second-screen dock menu, single-screen Nova: NOT TESTED | requested 16 Nov |
| Smaller pixel font (about 60-65 %, pixel font only), before / after for approval | not run | design approval needed |
| Badges stay on one row and scale together; optional Top Clip / Hanging Bottom styles (Classic stays default) | same-row branch PASS on Nova | not approved yet |
| Achievements page opens on Recently Unlocked; better reveal / trophy animations; old unlocks don't replay | not run | requested 17 Nov |
| AYN Thor: Android Home no longer appears on the lower screen when a game opens on the top screen (cause first) | not run | requested 17 Nov |
| Words never broken in the middle where a slightly smaller readable size keeps them whole (app-wide text fitting) | full Nova x1.3 j677x13 (677j) - see matrix H4; x1.0 + RP Classic full gates NOT TESTED | owner 17 Nov: moved to v0.6.74 (affects too many layouts without complete regression verification); kept on branch claude/whole-words-v0674 |
| Visual polish pass of all 53 game windows (KONKR_popups_678n_review.pdf): x1.0 vs x1.3, first page vs More page; type, spacing, art, buttons, hints, empty space, collisions; each layout keeps its own look. First: Pocket Classic (pixel font + blocks too big at x1.3), Pocket Ribbon (console / Never played / achievements wrap), Night (ACHIEVED / RELEASED broken), Pocket (Play game wraps), Bar (Achievements / labels cramped). Then: Hero, Hero tiles, Live, Cross, Hub, Resume, Channel, Twin icons, Twin showcase, Memory card, Bubbles, Panels, Cinema. A functional PASS is not visual approval | KONKR x1.3 sheet (k678n13) shows the need; Pocket Play game wrap fixed in code 681p (unit test); Night ACHIEVED / RELEASED whole in code 681t-681u (unit test, cloud); Bar dock key names whole in code 681w (unit test, cloud); 682 survey test of the next 13 windows (findings from CI); handheld HW-24 NOT TESTED | owner handoff 17 Nov (sections 3-4): v0.6.74, does not delay v0.6.73; pixel font ~60-65 % first (matrix H9), before / after for approval |

| Gate | State | Evidence |
|---|---|---|
| Reproduce the sparkle loop on the Flip 2 with production's own limit (read-only: logcat + battery state over 15 min) | BLOCKED | DEVICE UNAVAILABLE (owner 16 Nov) |
| Charging fix re-applied on the dev line | NOT STARTED | waits for the Flip 2 |
| Flip 2 charging test (QA app, production limit off for one supervised test) | BLOCKED | DEVICE UNAVAILABLE (owner 16 Nov) - not a failure |
| Save Sync rebased onto the v0.6.73 dev line, compiled, full unit suite green | NOT STARTED | branch fix/save-sync only as a bundle (PC / project doc) |
| QA app registered with Google (Android OAuth client, package com.shelf.launcher.qa, cloud debug key SHA-1) + spare-account sign-in | BLOCKED | owner action (only the owner can) |
| Real Drive end-to-end on 2 handhelds, synthetic saves only: hand-over, simultaneous upload, half-sent multi-file save, restore, restart mid-game; real emulator saves never overwritten (read-only checksums before / after) | NOT STARTED | - |

Why it cannot ship yet: Reproduce the sparkle loop on the Flip 2 with production's own limit (read-only: logcat + battery state over 15 min) (BLOCKED); Flip 2 charging test (QA app, production limit off for one supervised test) (BLOCKED); QA app registered with Google (Android OAuth client, package com.shelf.launcher.qa, cloud debug key SHA-1) + spare-account sign-in (BLOCKED); Charging fix re-applied on the dev line (NOT STARTED); Save Sync rebased onto the v0.6.73 dev line, compiled, full unit suite green (NOT STARTED); Real Drive end-to-end on 2 handhelds, synthetic saves only: hand-over, simultaneous upload, half-sent multi-file save, restore, restart mid-game; real emulator saves never overwritten (read-only checksums before / after) (NOT STARTED).
Merging, the production install and publishing always wait for your explicit GO.
<!-- /GEN:release -->

## OWNER ACTIONS REQUIRED (generated; only what genuinely needs you)
<!-- GEN:owner -->
| # | What I need from you | Why | Device | Urgent? | Blocks |
|---|---|---|---|---|---|
| 1 | Achievement reveal animation: pick NOW (Volt capsule), A Trophy flip, B Glow burst, or a mix - see docs/evidence/h12_reveal_682k/storyboard.png (one row per option, left to right over time) | H12 redesign of the reveal / trophy moment; a new look never becomes the default without your OK on the comparison. Nothing changes in the app until you pick | none (image on GitHub) | no | H12 reveal redesign only |
| 2 | Same-row badges: look at the final comparison (compact badge / adaptive spacing - both badges kept visible) when it is ready | you approved the direction on 17 Nov but not hiding the achievement badge below 72 %; implementation waits for your approval of the comparison | screenshots (when Claude posts them) | no | v0.6.74 badge redesign only |
| 3 | Status bar: which layout were you looking at, and do you prefer one single block or each layout drawing it its own way? | your request of 16 Nov - a design change needs your pick before it becomes the default | a screenshot helps | no | that design change only |
| 4 | Next hardware session (any day): RP Classic and the Thor by USB, PC on, then say "devices ready" | left for v0.6.73 / v0.6.74 after the KONKR session of 17 Nov: RP Classic full 53-layout gate (HW-18), Thor dual-screen (HW-19), Spot START glyph re-measure (HW-6) | RP Classic + Thor (USB); KONKR done 17 Nov; Flip 2 not needed for v0.6.73 | no | nothing for v0.6.73 (KONKR covered it) |
| 5 | Pause or retire the old PC task "VoltCap hourly backlog run" | its PC copy is behind the cloud branch; its runs can't push (harmless, wasted) | Claude app > scheduled tasks | no | nothing |
| 6 | Save Sync real-Drive test: sign the QA app in to a spare Google account | last data-safety step before Save Sync 529 can ship (not in v0.6.73) | Flip 2 + KONKR | no | a later release |
| 7 | Two PC-only branches (review/ai-setup-0672, feature/dual-welcome): keep on the PC or push | they change CI workflows, so they were not pushed automatically | none | no | nothing |
| 8 | Thor lower screen (H13): pick a fix - A apps follow the screen layout like games / B 'Leave as is' keeps a live Voltcap screen / C Voltcap as the lower screen's home / D companion stays until the game ends (A first recommended) | cloud reading 17 Nov found why Android Home shows on the lower screen (docs/claude/THOR_DUAL_SCREEN.md); not reproduced on the Thor yet | none (HW-19 later: you start the game, Claude only reads) | no | v0.6.74 Thor dual-screen fix (H13) |
| 9 | Approve the final pixel-font comparison (62.5 % + 9.5 sp readable minimum) or ask for a different minimum | pixel_font_final_emulator.pdf (emulator, 4 pixel layouts x1.0 / x1.3); branch claude/v0674-pixel-font | none | no | merging the pixel font into the v0.6.74 line |

Cloud development continues without any of these. v0.6.73 / KONKR production upgrade / merges to main need your separate GO.
<!-- /GEN:owner -->

## Technical status (hand-written by each run)
| | |
|---|---|
| Stable production version | **v0.6.72** (main 5b01ef39). Nothing released since. |
| Latest development commit | see the top of the [dev branch](https://github.com/dzepdzep/voltcap/commits/claude/qa-0672-repair); last code change: 678e (16 Nov 23:45 UTC, banner game window texts shown whole) |
| Latest CI ("Verify Android update 310") | **green** on a3b685e (678d); 678e pending; green on every finished push since the 675a memory fix |
| Latest emulator trial | 43961010406 (678c): **PASS** - popup gate on 2 of 53 layouts (SHELF, COVERDETAILS) at x1.0 + x1.3, EMULATOR only; next trial from ~00:45 UTC |
| Last successful cloud run | 16 Nov 23:18-23:50 UTC (678e) |
| Cloud runs so far | hourly scheduled runs since 15 Nov (675a-678e; one entry each in AUTORUN_LOG) + live sessions |
| Waiting for hardware | open hardware requests batched into one session (see the queue; HW-12 banner window added 678e); 25 older requests (13 Nov) also need a device |
| Waiting for your approval / decision | see Owner actions |

## Next development priorities (hand-written)
1. Start / read the next emulator trial (from ~00:45 UTC); then widen it: all 53 layouts in bounded jobs, x1.0 + x1.3, the Banner style,
   Flip 2-like and RP Classic-like screens, the 17 category looks.
2. CI memory: keep watching (needs several green pushes in a row before G11 is DONE).
3. Native popup pages for the 40 layouts still on the shared page (P2).
4. Physical pickup / return: in-place Inspect for the other Inspect windows (D323 / D324), pickup latency (D322).
5. Reconcile the older requests (A-F) and the master backlog into the matrix, so the total stops being UNKNOWN.

## Overall requirement counts (generated)
<!-- GEN:overall -->
Tracked requirement rows: **92** = 30 in the requirements matrix (programme + recent) + 62 older requests (A-F, status as of 13 Nov, re-verified only when touched).

| Bucket | Matrix rows | Older requests (13 Nov) | Total |
|---|---|---|---|
| done | 2 | 4 | 6 (6 %) |
| implemented, verification pending | 12 | 0 | 12 (13 %) |
| in progress | 10 | 4 | 14 (15 %) |
| not started / open | 3 | 7 | 10 (10 %) |
| waiting for hardware | 0 | 25 | 25 (27 %) |
| waiting for owner | 1 | 19 | 20 (21 %) |
| blocked (references) | 0 | 1 | 1 (1 %) |
| blocked (other) | 0 | 1 | 1 (1 %) |
| deferred by owner | 2 | 1 | 3 (3 %) |

Percentages are shares of the tracked rows, not of the whole project: the full number of requirements is UNKNOWN (not every old request has been reconciled into a row yet).
- waiting for hardware: A2, A4, A6, A7, B1, B3, B4, B6, C2, D2, D5, D6, D9, D10, D11, D12, D13, D14, D17, D19, F7, F9, F16, F19, F21
- waiting for owner: P11, A1, A10, A11, B7, D1, D3, D4, D8, D18, F1, F2, F4, F8, F10, F12, F14, F15, F18, F20
<!-- /GEN:overall -->

## Hardware QA queue (generated)
<!-- GEN:hw -->
| Request | Status |
|---|---|
| HW-26 Volt second screen text shown whole (690a, rule 11) | OPEN (Thor; batch with HW-19 when the owner says the Thor is back) |
| HW-17 v0.6.73 candidate focused re-check (status bar, profile picture, Storage; merged dev line) | DONE (b76e3ef); 678k NOT TESTED |
| HW-25 Library rescan: deleted last game / empty console / SD card out (681r) | DONE 18 Nov except the SD card (KONKR kk682d + RP Classic rc682b: 18/18 PASS each; build qa-682b-3ec7e45-ci). SD card physically out: NOT TESTED - owner 18 Nov 12:12 does not want to pull the card now; recorded NOT TESTED, never PASS (when wanted: RP Classic, sh rescan681r.sh run TAG 453E-4658 - the owner pulls / reinserts when the log says) |
| HW-25 Achievements progress counts each new unlock as it locks in (681y, H12) | OPEN (same KONKR session as HW-24, ~3 min) |
| HW-24 Channel progress pill clear of the name plate (680a, D356) | OPEN (next KONKR session) |
| HW-23 KONKR evening session 17 Nov (live, owner present) | DONE |
| HW-22 Terminal home: path, header and preview shown whole (679d, rule 11 / D353) | OPEN (batch with other KONKR requests) |
| HW-21 Console hub category title clear of the profile row at density 1.4 (679c, H7 / D347) | OPEN (batch with HW-18) |
| HW-18 RP Classic full 53-layout popup gate (after the fixture fix, H6) | OPEN (tooling fixed 678l, needs the Classic) |
| HW-20 Achievements page opens on the new unlocks (678m, H12) | DONE on the KONKR (x1.0 + x1.3); touch scroll / reduced motion NOT TESTED |
| HW-19 AYN Thor dual-screen: Android Home on the lower screen after a top-screen launch (H13) | OPEN (cloud reading done 679b) |
| HW-16 Category screen text shown whole (678j, rule 11) | IN PROGRESS (KONKR grid look done; Slides / Stage NOT TESTED) |
| HW-15 Save-sync prompts text shown whole (678i, rule 11) | OPEN |
| HW-14 Settings text shown whole (678h, rule 11) | IN PROGRESS (KONKR: search + 3 pages PASS; sub-pages NOT TESTED) |
| HW-13 Save Vault text shown whole (678g, rule 11) | IN PROGRESS (KONKR fixture view PASS; "Not connected" NOT TESTED) |
| HW-12 Banner game window text shown whole (678e, rule 11) | DONE on the KONKR (x1.0 + x1.3) |
| HW-8 Hide navigation bar on other handhelds (676b) | OPEN |
| HW-9 Badge lane on small screens (676a) | IN PROGRESS (Nova PASS; KONKR run 17 Nov - one spacing finding; RP Classic open) |
| HW-7 RP Nova x1.3 re-test of the 4 Nova FAILs | DONE (n675r13 + n676x13: all four PASS; x1.0 n675r10 4/4 PASS) |
| HW-2 Record crate x1.3 vertical menu - verification checklist | DONE on the KONKR 17 Nov (all items PASS after the D333 fix) |
| HW-3 Flip 2 charging sparkle - reproduce first | BLOCKED - DEVICE UNAVAILABLE |
| HW-4 Cube first A on KONKR x1.3; L2 / R2 fact 0 px | DONE (popup gate) / onepress NOT TESTED |
| HW-5 Rapid A/B leaves a window open once (Cards, Crate, TV) | DONE 17 Nov (D333 fixed in 678p, 60/60 rounds closed on the KONKR) |
| HW-7 Spotify screen + player: long names shown whole (675l, rule 11) | OPEN |

Open hardware requests: 15 of 24.
<!-- /GEN:hw -->

## Progress by area (generated)
<!-- GEN:areas -->
_Evidence file: docs/claude/progress.json (2026-11-23 (P2 review package, batches 4-7 on hardware)). Real-device and emulator columns are separate; emulator evidence never counts as device evidence._

**Home layouts (popup gate)**
- Total: 53
- Implemented: 53 (100 %)
- Unit-tested: UNKNOWN
- Emulator-tested: 2 (3 %)
- Real-device verified: 48 (90 %)
- Owner approved: 0 (0 %)
- Released: 53 (older state, v0.6.72)
- Remaining / blocked: owner visual polish pass of all 53 (H16, v0.6.74; KONKR sheet 17 Nov); Flip 2 / Thor / RP Classic unavailable
- Source: device = popup gate PASS on both handhelds at both scales: f674k + f674o (Flip 2), k674m + k674n (KONKR), builds 674k / 674n. emulator = COVERDETAILS + SHELF (KONKR-like; every graded check PASS at x1.0 + x1.3 in run 43961010406, 678c). unit: per-layout test coverage not counted yet.; newest build 8bd5e4a: RP Nova x1.0 53/53 (n675c), x1.3 49/53 (n675d); 17 Nov KONKR newest build: x1.0 k678n10 52/53 (TV rapid), x1.3 k678n13 52/53 (Channel - fixed 678o, kc678o13 PASS) - device count kept until the TV fix is re-gated; RC build qa-0.6.73-rc-91b1ac3 x1.0 krc10 53/53 (TV rapid fixed, D333); Channel x1.3 fixed 678r (kc678r13 + kc678r10 PASS) - device count kept at the last full two-scale gate

**Native popup pages**
- Total: 53
- Implemented: 29 (54 %)
- Unit-tested: 29 (54 %)
- Emulator-tested: 0 (0 %)
- Real-device verified: 29 (54 %)
- Owner approved: 0 (0 %)
- Released: 0 (0 %)
- Remaining / blocked: 29 own designs, 0 visually approved. Owner review 23 Nov 07:28: 16 close to approval (unchanged), 12 changed in 682ag (TV option 2, Spotlight, Arctic, Arcade, Icon bar + 7 polish) - before/after package published 23 Nov; Coverflow details reading space and the page-change frame audit still open. Disc station still shows shared page bodies in its own frame; 23 layouts still on the shared page.
- Source: P2 branch docs/claude/NATIVE_PAGES_P2.md + NATIVE_PAGES_AUDIT.md (23 Nov). device = every page walked on KONKR + RP Classic at x1.0 AND x1.3 (p2review, QA app; 22-23 Nov runs on 1ada6c8 / b316b8f / 9192a89); the 12 layouts changed in 682ag re-walked at x1.3 on both handhelds (kag13 / rag13 d29e05c, kbg13 / rbg13 f17dfb8; 0 launches, 0 crashes, restore diff 0) - x1.0 of the changed 12 not re-run on hardware. Play same size and place on every page: 29 of 29 (TV fixed f17dfb8).

**Physical pickup / return animations**
- Total: 14
- Implemented: 14 (100 %)
- Unit-tested: UNKNOWN
- Emulator-tested: 0 (0 %)
- Real-device verified: 14 opened + no flash / black frame on the RP Nova x1.0 (pkn675, 16 Nov, build 8bd5e4a); look approval: owner
- Owner approved: 0 (0 %)
- Released: UNKNOWN
- Remaining / blocked: in-place Inspect for other Inspect windows (D323 / D324), pickup latency (D322); per-layout hardware count not recorded (pickup529 runs 673b)
- Source: REQUIREMENTS_MATRIX P4; RP Nova pkn675 16 Nov

**Category styles (looks)**
- Total: 17
- Implemented: 17 (100 %)
- Unit-tested: UNKNOWN
- Emulator-tested: 0 (0 %)
- Real-device verified: UNKNOWN on the dev line (all 17 checked 12 Nov on an older build)
- Owner approved: 0 (0 %)
- Released: 17 (older state)
- Remaining / blocked: third-priority polish not started: Stage, Generations, Wall, Slides, Vault, Grid / Dashboard / Constellation; owner visual review
- Source: Project doc SPRINT527: KONKR all 17 looks at x1.0 / x1.3 (12 Nov, feature/popup-native); not re-run on the dev line since

**Uniform buttons / menus**
- Total: UNKNOWN
- Implemented: all popup families
- Unit-tested: yes (UniformKeys673Test, TwoLineKeys674Test ...)
- Emulator-tested: 2 layouts
- Real-device verified: via the 53-layout popup gate
- Owner approved: 1 (Record crate x1.3 menu)
- Released: 0
- Remaining / blocked: owner visual review of the rest; Wii channel gate (G5); promote warn-uniform to FAIL
- Source: REQUIREMENTS_MATRIX P3, G1-G7

**Controller and touch**
- Total: UNKNOWN
- Implemented: partly
- Unit-tested: WinFocusRoboTest, ONEPRESS
- Emulator-tested: first-A probe (675c)
- Real-device verified: popclip focus / rapid steps in every gate
- Owner approved: 0
- Released: partly
- Remaining / blocked: rapid A/B watch (G6), Cube first-A (G8), touch >= 24 dp everywhere (P12)
- Source: REQUIREMENTS_MATRIX P9, P12, G6, G8

**Artwork, themes, profiles**
- Total: UNKNOWN
- Implemented: partly
- Unit-tested: RealScans464Test, check_uniforms
- Emulator-tested: 0
- Real-device verified: UNKNOWN
- Owner approved: partly
- Released: partly
- Remaining / blocked: reference-first models (D7 BLOCKED-REF), real cover-art verification
- Source: REQUIREMENTS_MATRIX P6, P7

**Performance / compatibility**
- Total: UNKNOWN
- Implemented: partly
- Unit-tested: UNKNOWN
- Emulator-tested: 0
- Real-device verified: measured (older)
- Owner approved: 0
- Released: partly
- Remaining / blocked: D10, F16 (frames on Flip 2), KONKR memory
- Source: REQUIREMENTS_MATRIX P8; REQUEST_COVERAGE_529 D10, D14, F16

**Save Vault + Save Sync 529**
- Total: 12
- Implemented: 10 (83 %)
- Unit-tested: 9 (75 %)
- Emulator-tested: 0 (0 %)
- Real-device verified: 2 Flip 2 dry runs (A2, A8); real Drive: 0
- Owner approved: UNKNOWN
- Released: 0 (0 %)
- Remaining / blocked: real-Drive end-to-end on two handhelds (A1 owner step), KONKR checks (A2, A10), A11 partial; Save Sync lives on the fix/save-sync line, excluded from v0.6.72
- Source: REQUEST_COVERAGE_529 A1-A11 (13 Nov: A1-A9 done / implemented with JVM tests, A10 finding only, A11 partial) + REQUIREMENTS_MATRIX P10 (Save Vault: no unit test listed) / P11

**Cloud emulator QA coverage**
- Total: 53 layouts x 2 scales x 3 profiles + 17 looks
- Implemented: 1 profile (KONKR-like), 2 layouts, 2 scales
- Unit-tested: -
- Emulator-tested: 2 / 53 layouts
- Real-device verified: -
- Owner approved: -
- Released: -
- Remaining / blocked: stable since 674o (swangle_indirect); widen to all layouts in bounded jobs, Flip 2-like + RP Classic-like profiles, 17 looks
- Source: REQUIREMENTS_MATRIX G14; Actions 'Emulator QA trial'

**Real handheld QA coverage**
- Total: UNKNOWN
- Implemented: -
- Unit-tested: -
- Emulator-tested: -
- Real-device verified: Flip 2 + KONKR: 53 layouts x 2 scales (15 Nov); Thor / Odin / RP Classic: none since 12-13 Nov
- Owner approved: -
- Released: -
- Remaining / blocked: HW queue (see below)
- Source: HARDWARE_QA_QUEUE.md; gates f674k f674o k674m k674n
<!-- /GEN:areas -->

## Latest phone report (hand-written; replaced after a meaningful milestone)
**16 Nov 2026, morning**
- DONE TODAY: cloud development running hourly without the PC (5 runs overnight); CI memory crash fixed (4 green pushes in a
  row); emulator no longer crashes; glyph checker fix (D-674o-1); this dashboard.
- WHAT FAILED: emulator trial still FAILs 2 layouts (QA-script dump reading - fixed in 675d, re-run pending). No app regression.
- WHAT REMAINS: native pages for 40 layouts, wider emulator coverage, 6 hardware checks, your visual reviews, Save Sync real Drive.
- WHAT YOU NEED FROM ME: one batched hardware session this week (row 1 above); pause the old PC task.
- WHAT YOU WILL DO NEXT: emulator re-run on 675d+, then widen emulator coverage; native popup pages.
