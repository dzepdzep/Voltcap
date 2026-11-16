# VoltCap status dashboard

> Phone view of the whole project. Updated after every cloud run.
> Rules: counts come only from evidence (matrix rows, gate tags, CI runs); UNKNOWN means not counted yet, never a guess.
> "Implemented" is code on the dev branch, not finished work. Emulator results never count as handheld verification.
> Sections marked "generated" are rewritten by `python3 tools/cloud/dashboard.py`; the rest is edited by hand each run.

**Links:** [this dashboard](https://github.com/dzepdzep/voltcap/blob/claude/qa-0672-repair/docs/claude/STATUS_DASHBOARD.md) ·
[CI runs](https://github.com/dzepdzep/voltcap/actions?query=branch%3Aclaude%2Fqa-0672-repair) ·
[dev branch](https://github.com/dzepdzep/voltcap/tree/claude/qa-0672-repair) ·
[hardware QA queue](https://github.com/dzepdzep/voltcap/blob/claude/qa-0672-repair/docs/claude/HARDWARE_QA_QUEUE.md) ·
[handoff](https://github.com/dzepdzep/voltcap/blob/claude/qa-0672-repair/docs/claude/HANDOFF.md) ·
[requirements matrix](https://github.com/dzepdzep/voltcap/blob/claude/qa-0672-repair/docs/REQUIREMENTS_MATRIX.md)

## At a glance (hand-written)
| | |
|---|---|
| Stable production version | **v0.6.72** (main 5b01ef39). Nothing released since. |
| Latest development commit | see the top of the [dev branch](https://github.com/dzepdzep/voltcap/commits/claude/qa-0672-repair); last code change: 675e deedfa45 (16 Nov 03:25 UTC) |
| Latest CI ("Verify Android update 310") | **green** on deedfa45 (675e); green on 4 code pushes in a row since the 675a memory fix |
| Latest emulator trial | 43786748164 on ddca9b66 (675c): emulator stays up; 2 layouts graded, FAIL (QA-script dump reading, fixed in 675d, not re-run yet) |
| Last successful cloud run | 16 Nov 03:17-03:45 UTC (675e) |
| Cloud runs so far | 6 (675a-675e + this dashboard), hourly at :17 |
| Waiting for hardware | 6 open hardware requests, batched into one session (Owner actions #1); 25 older requests (13 Nov) also need a device - folded into later sessions after re-checking |
| Waiting for your approval / decision | see Owner actions |

## Overall requirement counts (generated)
<!-- GEN:overall -->
Tracked requirement rows: **92** = 30 in the requirements matrix (programme + recent) + 62 older requests (A-F, status as of 13 Nov, re-verified only when touched).

| Bucket | Matrix rows | Older requests (13 Nov) | Total |
|---|---|---|---|
| done | 2 | 4 | 6 (6 %) |
| implemented, verification pending | 12 | 0 | 12 (13 %) |
| in progress | 9 | 4 | 13 (14 %) |
| not started / open | 4 | 7 | 11 (11 %) |
| waiting for hardware | 0 | 25 | 25 (27 %) |
| waiting for owner | 2 | 19 | 21 (22 %) |
| blocked (references) | 0 | 1 | 1 (1 %) |
| blocked (other) | 0 | 1 | 1 (1 %) |
| deferred by owner | 1 | 1 | 2 (2 %) |

Percentages are shares of the tracked rows, not of the whole project: the full number of requirements is UNKNOWN (not every old request has been reconciled into a row yet).
- waiting for hardware: A2, A4, A6, A7, B1, B3, B4, B6, C2, D2, D5, D6, D9, D10, D11, D12, D13, D14, D17, D19, F7, F9, F16, F19, F21
- waiting for owner: P11, G10, A1, A10, A11, B7, D1, D3, D4, D8, D18, F1, F2, F4, F8, F10, F12, F14, F15, F18, F20
<!-- /GEN:overall -->

## Next development priorities (hand-written)
1. Emulator trial on the 675d+ QA scripts (dump reading); then widen it: all 53 layouts in bounded jobs, x1.0 + x1.3,
   Flip 2-like and RP Classic-like screens, the 17 category looks.
2. CI memory: keep watching (needs several green pushes in a row before G11 is DONE).
3. Native popup pages for the 40 layouts still on the shared page (P2).
4. Physical pickup / return: in-place Inspect for the other Inspect windows (D323 / D324), pickup latency (D322).
5. Reconcile the older requests (A-F) and the master backlog into the matrix, so the total stops being UNKNOWN.

## OWNER ACTIONS REQUIRED (hand-written; only what genuinely needs you)
| # | What I need from you | Why | Device / screenshot | Urgent? | Cloud work continues without it? |
|---|---|---|---|---|---|
| 1 | **One hardware session**: PC on, Flip 2 + KONKR on USB, say "devices ready" | 6 queued checks (gates on the newest build, Record crate checklist, Cube first A, glyph re-measure) can only be closed on real handhelds; batched so you turn the PC on once | Flip 2, KONKR; ~2 h, unattended | No - this week | Yes |
| 2 | **Pause or retire the old PC task** "VoltCap hourly backlog run" | its PC worktree is behind the cloud branch; a run would fail to push (refused, harmless but wasted) | Claude app → scheduled tasks | Low | Yes |
| 3 | Flip 2 charging: either switch production Voltcap's 80 % limit off for one supervised test, or wait for a production update | the fix (674i) can't be tested while the real app's limit fights the QA app | Flip 2 | No | Yes |
| 4 | Visual review of the uniform button system and the x1.3 squeezes (only the Record crate menu is approved) | a design is not done without your approval | screenshots from the next hardware session | No | Yes |
| 5 | Save Sync real-Drive test: register / sign in the QA app's OAuth client (SAVE_SYNC_529 §8) | the last data-safety step needs a real Google Drive and two handhelds | Flip 2 + KONKR | No | Yes |
| 6 | Two PC-only branches (review/ai-setup-0672, feature/dual-welcome): keep on the PC or push? | they change CI workflows, so they were not pushed automatically | none | No | Yes |
| 7 | Nothing yet: 19 older requests (13 Nov list, e.g. F12 style pick, D3 / D8 visual approvals) are marked as waiting for you | Claude re-checks each against today's code first and asks only for the ones still open, grouped | - | No | Yes |
| - | v0.6.73, KONKR production upgrade, merges to main | need your separate authorization; nothing is done without it | - | - | Yes |

## Hardware QA queue (generated)
<!-- GEN:hw -->
| Request | Status |
|---|---|
| HW-1 Full popup gates on 674n (x1.0 + x1.3, both handhelds) | IN PROGRESS |
| HW-2 Record crate x1.3 vertical menu - verification checklist | OPEN |
| HW-3 Flip 2 charge-loop fix (674i) | BLOCKED (owner) |
| HW-4 Cube first A on KONKR x1.3; L2 / R2 fact 0 px | OPEN |
| HW-5 Rapid A/B leaves a window open once (Cards, Crate, TV) | OPEN (watch) |
| HW-6 Spot START glyph re-measure with the key box (675e, D-674o-1) | OPEN |

Open hardware requests: 6 of 6.
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
- Real-device verified: 17 (100 %)
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
