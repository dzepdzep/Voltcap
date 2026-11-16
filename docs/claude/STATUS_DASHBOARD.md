# VoltCap status dashboard

## OWNER SUMMARY (generated after every run - read this first)
<!-- GEN:summary -->
**1. LATEST UPDATE** - 675m · 2026-11-16 08:45 UTC · commit `this update (being pushed)` · CI: Verify Android update 310 green on 8bd5e4ae (675l) · effort: medium

**2. WHAT CLAUDE JUST FIXED** - Notifications panel: long app names, notification titles and texts, background-task names, the date and the charge-limit label are no longer cut off with '...'; they wrap, shrink a little, or slide so all of it can be read. Rows keep their height. The emulator test is started again on this update.

**3. LAST 24 HOURS** (most important first)
- **App** (675i): The Flip 2 charging fix was taken out of the next release because the Flip 2 is away; it is saved separately for v0.6.74, so v0.6.73 can go ahead without it. Also fixed a checker crash that stopped every popup check from being graded.
- **CI / build** (675a): Fixed the automatic test run running out of memory (it was stopped by the watchdog on some pushes); the heaviest test groups now run in their own fresh process. CI has been green since.
- **CI / build** (674o): Development moved to the cloud: it now builds, tests and pushes without your PC. The emulator test, which crashed every time, now stays up. Library's L2 / R2 hints are centred and the Side menu's sync line is full touch size again.
- **QA tools** (675k): Found why the last emulator test graded nothing: another app's 'not responding' box covered VoltCap, and a one-time 'Inspect or Wobble?' question kept popping up 9 seconds after each start. The test now closes such boxes and answers that question in the test app only, so the next emulator run can actually check the popups.
- **Docs** (675j): v0.6.73 no longer waits for the Flip 2: today's KONKR and other handhelds will be tested, and the Flip 2's results from 15 Nov count only as older evidence with the gap listed as a risk. The charging fix will only be used if the problem can still be reproduced.

**4. PROJECT PROGRESS** (implemented / verified on handhelds / approved by you)

| Area | Implemented | Verified on handhelds | Approved by you |
|---|---|---|---|
| Home layouts | `██████████` 53/53 | `█████████░` 48/53 | `░░░░░░░░░░` 0/53 |
| Native pages | `██░░░░░░░░` 13/53 | `██░░░░░░░░` 13/53 | `░░░░░░░░░░` 0/53 |
| Pickup animations | `██████████` 14/14 | `??????????` UNKNOWN | `░░░░░░░░░░` 0/14 |
| Category styles | `██████████` 17/17 | `??????????` UNKNOWN on the dev line (all 17 checked 12 Nov on an older build) | `░░░░░░░░░░` 0/17 |
| Save Vault + Sync | `████████░░` 10/12 | `??????????` 2 Flip 2 dry runs (A2, A8); real Drive: 0 | `??????????` UNKNOWN |
| Emulator QA (layouts) | `░░░░░░░░░░` 2/53 | emulator never counts as handheld | - |

**5. WORKING ON NEXT** - now: read the emulator test started on this update (the first one with the start-up dialog fixes); then: hardware checks on today's handhelds when you say "devices ready"; remove the remaining '...' cut-offs screen by screen (262 left)

**6. WHAT YOU NEED TO DO** - **NONE urgent.** When convenient: 5 items (see Owner actions below); cloud work continues without them.

**7. NEXT RELEASE** - current v0.6.72 · **v0.6.73: TESTING** · 0 of 7 app fixes fully verified · 7 gates left (next: Emulator QA trial (KONKR-like, x1.0 + x1.3)) · your approval needed: yes - when it is ready · window: UNKNOWN
- later: **v0.6.74: DEVELOPMENT** - postponed items; contents grow as later fixes finish (UNKNOWN)
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
**Current stable:** v0.6.72 (5b01ef39, 2026-11-14) - Latest stable release (main). Save Sync 529 not included.

### v0.6.73 - **TESTING**
- Base: main 5b01ef39 (v0.6.72); dev branch claude/qa-0672-repair can fast-forward main. Scope: the stable batch on the dev line since v0.6.72 (updates 673a-675i); Save Sync 529 and the Flip 2 charging fix stay out.
- Release window: UNKNOWN - after today's hardware session (KONKR + other handhelds); then about 1.5 h of release steps after the owner's GO
- App fixes and features: 7 (7 implemented, 0 fully verified, 3 implemented but not yet tested on a handheld, 4 need your look or decision)

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

| Gate | State | Evidence |
|---|---|---|
| CI unit tests + debug build (Verify Android update 310) | PASS | green on 2371ce29 (675i), deedfa45 and the code pushes before it; re-check on the release commit |
| Emulator QA trial (KONKR-like, x1.0 + x1.3) | PENDING | run 43811840328 (675i): NOT TESTED - grader fixed, but the precondition failed: Pixel Launcher's ANR dialog stood over the QA app and the Inspect / Wobble first-start question came up 9 s after each start (675k: both handled). Next trial from ~08:45 UTC. Emulator evidence supports, never replaces, the handheld gates |
| Full popup gates on the KONKR + the other handhelds connected today, newest build (x1.0 + x1.3 where supported) | PENDING | owner 16 Nov: KONKR + other handhelds available today ("devices ready"). Last KONKR: 15 Nov k674m 51/53, k674n 51/53; all failures fixed since |
| Flip 2 (unavailable): 15 Nov popup gates as historical evidence | HISTORICAL | f674k x1.3 52/53 (674k build), f674o x1.0 52/53 (674n build) - NOT the newest build. Owner 16 Nov: don't block v0.6.73 on the Flip 2; the gap is listed under risks |
| Record crate checklist (focus after More, rapid A/B, no size change) | PENDING | hardware request HW-2 |
| Owner visual review of the new menus and x1.3 fixes | PENDING | screenshots come from the hardware session |
| Signed release build, frozen RC, secret scan (main tree on the PC) | NOT STARTED | after the gates above; signing keys live only on the PC |
| Upgrade install over the real Voltcap on one handheld + smoke test | NOT STARTED | needs the owner's GO (production app) |
| Owner GO to merge to main and publish | NOT STARTED | never automatic |
- **Risk / known gap:** Flip 2 (1920x1080, 360 dpi, x1.0 / x1.3): checked only on builds 674k / 674n (15 Nov). Fixes since then that touch layouts it shows (Side menu status line, Library / Spot hints) are not seen on a Flip 2. Mitigation: the same fixes pass on the KONKR / other handhelds today; the Flip 2's 15 Nov results had 1 FAIL each (Bar arrows - fixed 674n; Side status line - fixed 674o).
- **Risk / known gap:** Charging fix left out: the Flip 2 keeps v0.6.72's charge-limit behaviour (no change, no new risk).
- **Left out on purpose:** Flip 2 charging sparkle fix (charge-limit pause read as unplugging) - owner 16 Nov: Flip 2 unavailable - postponed to v0.6.74 or later; never tested on the device. Kept: branch claude/charge-loop-674i + docs/claude/patches/charge-loop-674i.patch (re-applies cleanly); removed from the dev line in 675i, ChargeLimiter.kt identical to v0.6.72.

Why it cannot ship yet: Emulator QA trial (KONKR-like, x1.0 + x1.3) (PENDING); Full popup gates on the KONKR + the other handhelds connected today, newest build (x1.0 + x1.3 where supported) (PENDING); Record crate checklist (focus after More, rapid A/B, no size change) (PENDING); Owner visual review of the new menus and x1.3 fixes (PENDING); Signed release build, frozen RC, secret scan (main tree on the PC) (NOT STARTED); Upgrade install over the real Voltcap on one handheld + smoke test (NOT STARTED); Owner GO to merge to main and publish (NOT STARTED).
Merging, the production install and publishing always wait for your explicit GO.

### v0.6.74 - **DEVELOPMENT**
- Base: after v0.6.73. Scope: postponed items; contents grow as later fixes finish.
- Release window: UNKNOWN - the Flip 2 is unavailable
- App fixes and features: 1 (0 implemented, 0 fully verified, 0 implemented but not yet tested on a handheld, 0 need your look or decision)

| Fix / feature | Handheld evidence | Your approval |
|---|---|---|
| Flip 2 charging sparkle no longer replays while plugged in (charge-limit pause no longer reads as unplugging) | BLOCKED - DEVICE UNAVAILABLE (Flip 2); not failed - never run. Reassess first: is the loop still reproducible? | test when the Flip 2 is back (production limit off for one supervised test) |

| Gate | State | Evidence |
|---|---|---|
| Reproduce the sparkle loop on the Flip 2 with production's own limit (read-only: logcat + battery state over 15 min) | BLOCKED | DEVICE UNAVAILABLE (owner 16 Nov) |
| Charging fix re-applied on the dev line | NOT STARTED | waits for the Flip 2 |
| Flip 2 charging test (QA app, production limit off for one supervised test) | BLOCKED | DEVICE UNAVAILABLE (owner 16 Nov) - not a failure |

Why it cannot ship yet: Reproduce the sparkle loop on the Flip 2 with production's own limit (read-only: logcat + battery state over 15 min) (BLOCKED); Flip 2 charging test (QA app, production limit off for one supervised test) (BLOCKED); Charging fix re-applied on the dev line (NOT STARTED).
Merging, the production install and publishing always wait for your explicit GO.
<!-- /GEN:release -->

## OWNER ACTIONS REQUIRED (generated; only what genuinely needs you)
<!-- GEN:owner -->
| # | What I need from you | Why | Device | Urgent? | Blocks |
|---|---|---|---|---|---|
| 1 | Today: connect the KONKR and the other handhelds by USB, PC on, then say "devices ready" | the remaining handheld checks for v0.6.73 (popup gates on the newest build at x1.0 / x1.3, Record crate checklist, Cube first press, glyph re-measure, controller / animation checks) | KONKR + other handhelds (USB); Flip 2 not needed for v0.6.73 | no | v0.6.73 |
| 2 | Pause or retire the old PC task "VoltCap hourly backlog run" | its PC copy is behind the cloud branch; its runs can't push (harmless, wasted) | Claude app > scheduled tasks | no | nothing |
| 3 | Look at the new menus and x1.3 fixes (screenshots after the hardware session) | a design is not done without your approval; only the Record crate menu is approved | screenshots | no | v0.6.73 approval |
| 4 | Save Sync real-Drive test: sign the QA app in to a spare Google account | last data-safety step before Save Sync 529 can ship (not in v0.6.73) | Flip 2 + KONKR | no | a later release |
| 5 | Two PC-only branches (review/ai-setup-0672, feature/dual-welcome): keep on the PC or push | they change CI workflows, so they were not pushed automatically | none | no | nothing |

Cloud development continues without any of these. v0.6.73 / KONKR production upgrade / merges to main need your separate GO.
<!-- /GEN:owner -->

## Technical status (hand-written by each run)
| | |
|---|---|
| Stable production version | **v0.6.72** (main 5b01ef39). Nothing released since. |
| Latest development commit | see the top of the [dev branch](https://github.com/dzepdzep/voltcap/commits/claude/qa-0672-repair); last code change: 675m (16 Nov 08:45 UTC, notifications panel texts shown whole) |
| Latest CI ("Verify Android update 310") | **green** on 8bd5e4ae (675l); 675m pending; green on every finished push since the 675a memory fix |
| Latest emulator trial | 43811840328 (675i): NOT TESTED (precondition: ANR dialog + Inspect question, handled 675k); new trial started on the 675m commit - result next run |
| Last successful cloud run | 16 Nov 08:21-08:45 UTC (675m) |
| Cloud runs so far | 10 scheduled (675a-675e, 675h, 675k-675m) + live sessions, hourly at :17 |
| Waiting for hardware | 7 open hardware requests, batched into one session (Owner actions #1); 25 older requests (13 Nov) also need a device - folded into later sessions after re-checking |
| Waiting for your approval / decision | see Owner actions |

## Next development priorities (hand-written)
1. Read the emulator trial started on the 675m commit (675k preconditions); then widen it: all 53 layouts in bounded jobs, x1.0 + x1.3,
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
| HW-1 Full popup gates on 674n (x1.0 + x1.3, both handhelds) | IN PROGRESS |
| HW-2 Record crate x1.3 vertical menu - verification checklist | OPEN |
| HW-3 Flip 2 charging sparkle - reproduce first | BLOCKED - DEVICE UNAVAILABLE |
| HW-4 Cube first A on KONKR x1.3; L2 / R2 fact 0 px | OPEN |
| HW-5 Rapid A/B leaves a window open once (Cards, Crate, TV) | OPEN (watch) |
| HW-6 Spot START glyph re-measure with the key box (675e, D-674o-1) | OPEN |
| HW-7 Spotify screen + player: long names shown whole (675l, rule 11) | OPEN |

Open hardware requests: 7 of 7.
<!-- /GEN:hw -->

## Progress by area (generated)
<!-- GEN:areas -->
_Evidence file: docs/claude/progress.json (2026-11-16 (live session, from repo + Project evidence)). Real-device and emulator columns are separate; emulator evidence never counts as device evidence._

**Home layouts (popup gate)**
- Total: 53
- Implemented: 53 (100 %)
- Unit-tested: UNKNOWN
- Emulator-tested: 2 (3 %)
- Real-device verified: 48 (90 %)
- Owner approved: 0 (0 %)
- Released: 53 (older state, v0.6.72)
- Remaining / blocked: 5 layouts fixed since their last FAIL (Bar, Side, Arcade, Library, Spot) - re-test HW-1 / HW-6; owner visual review of all 53
- Source: device = popup gate PASS on both handhelds at both scales: f674k + f674o (Flip 2), k674m + k674n (KONKR), builds 674k / 674n. emulator = COVERDETAILS + SHELF (KONKR-like, run 43786748164+). unit: per-layout test coverage not counted yet.

**Native popup pages**
- Total: 53
- Implemented: 13 (24 %)
- Unit-tested: 13 (24 %)
- Emulator-tested: 0 (0 %)
- Real-device verified: 13 (24 %)
- Owner approved: 0 (0 %)
- Released: 0 (0 %)
- Remaining / blocked: 40 layouts still on the shared page (P2)
- Source: REQUIREMENTS_MATRIX P2 (673j/k gates)

**Physical pickup / return animations**
- Total: 14
- Implemented: 14 (100 %)
- Unit-tested: UNKNOWN
- Emulator-tested: 0 (0 %)
- Real-device verified: UNKNOWN
- Owner approved: 0 (0 %)
- Released: UNKNOWN
- Remaining / blocked: in-place Inspect for other Inspect windows (D323 / D324), pickup latency (D322); per-layout hardware count not recorded (pickup529 runs 673b)
- Source: REQUIREMENTS_MATRIX P4

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
