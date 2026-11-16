# VoltCap requirements matrix (living; started 15 Nov 2026, update 674l)

The single place that says, per requirement, what is **implemented**, **tested**, **visually approved by the owner**, **released**,
**blocked** or **unfinished**. Scheduled runs and live sessions update the rows they touch; nothing is marked DONE because code
exists or a release shipped.

Columns: **Impl** (code on the dev line `claude/qa-0672-repair`) · **Unit** (JVM / Robolectric tests) · **HW** (hardware gate PASS,
device + scale + tag) · **Owner** (written approval by the owner) · **Rel** (in a published release) · **Status**.
Status words: DONE (every applicable column has evidence) · VERIFY (implemented + tested, owner review / hardware still missing) ·
IN PROGRESS · OPEN · BLOCKED-HW / -REF / -OWNER (why) · DEFER (later, by the owner's decision).

Sources reconciled so far: docs/REQUEST_COVERAGE_529.md (13 Nov, sections A–F, carried below as rows A1… F21 - their status is as of
13 Nov unless a newer note says otherwise: **re-verify each when touched**), docs/MASTER_BACKLOG.md, SUMMARY.md updates 672–674,
Project docs UPDATE_673 / UPDATE_674. Not yet reconciled line by line: ROADMAP_070, POPUP_BATCH_505–516, QA_PHASE*, CARTS_468/469,
old SUMMARY entries before 529, GitHub issues (no API access in these sessions).

## P. Programme-level requirements (the whole app)

| # | Requirement | Impl | Unit | HW | Owner | Rel | Status |
|---|---|---|---|---|---|---|---|
| P1 | All 53 Home layouts work, stable on category change (rule 14) | yes | CategoryStable394Test, CATSHELL | catswitch runs (older) | partly | 0.6.72 | VERIFY - re-run catswitch on the dev line |
| P2 | Every Home layout's popup has its own Details / Achievements / Options pages ("literally all popups") | 13 / 53 native | per scene | 13 layouts (673j/k) | no | no | IN PROGRESS (40 layouts on the shared page) |
| P3 | Uniform button & menu system (owner brief 15 Nov) on every popup | all families (673g–674l) | UniformKeys673Test, TwoLineKeys674Test, … | full gates 674f–674k (see G) | Record crate x1.3 only | no | VERIFY (owner review of the rest) |
| P4 | Physical pickup / return / Inspect animations (one real object out and back) | 14 / 14 pickups; in-place Inspect: Disc station, Coverflow details | Inspect673Test, Batch523PickupTest | pickup529 runs (673b) | no | no | VERIFY; D323/D324 in-place Inspect for other Inspect windows OPEN; D322 latency OPEN |
| P5 | 17 category styles | (to reconcile) | | | | | OPEN - list and per-style status to be reconciled from category docs |
| P6 | Artwork accuracy, reference-first (R25); real scans never changed | per model | RealScans464Test | | partly | partly | IN PROGRESS; D7 BLOCKED-REF |
| P7 | Themes / scenes | yes | check_uniforms | | partly | yes | VERIFY (no open request recorded) |
| P8 | Performance (cold start, render-bound layouts, idle) | partly | | measured | | | OPEN (D10, F16) |
| P9 | Controller: D-pad / B / rapid presses / one-press rule (R23) everywhere | yes | WinFocusRoboTest, ONEPRESS | popclip focus / rapid steps | | partly | IN PROGRESS (rapid A/B flakes G6) |
| P10 | Save Vault | yes | | | | partly | VERIFY (D6) |
| P11 | Save Sync 529 (data safety A1–A11) | fix/save-sync line | full | Flip 2 dry runs | "worked" (owner test 15 Nov, build unknown) | Save Sync excluded from 0.6.72 | BLOCKED-OWNER (real Drive E2E) - see A |
| P12 | Accessibility (touch >= 24 dp, contrast, reduced motion, TalkBack labels) | partly | | popclip touch / contrast | | | IN PROGRESS (reduced motion BLOCKED: system setting) |
| P13 | Rule 11: no "…" truncation anywhere | 256 TRUNCATE places left (16 Nov, 677a: post-game session card + status note; 675m: notifications panel; 675l: Spotify screen + player; 281 -> 256) | static TRUNCATE, NowPlayingTruncate79Test, SpotifyFullText675lTest, NotificationsFullText675mTest, SessionSummaryFullText677aTest | | | | IN PROGRESS (F21; Spotify + notifications + session card: owner look pending) |
| P14 | Category +N overflow counters removed | no | | | | | DEFER (owner: later refinement) |
| P15 | Everything stays free, no paid unlocks / mandatory paid APIs | yes | | | standing rule | yes | DONE (standing) |

## G. This session (673 / 674, 15 Nov 2026)

| # | Requirement | Impl | Unit | HW | Owner | Rel | Status |
|---|---|---|---|---|---|---|---|
| G1 | Record crate x1.3: deliberate vertical menu (identical keys, Play first, MORE last, D-pad scroll, no clipped keys / facts) | 674e–g | CrateVertical674Test | f674i Flip 2 x1.3 PASS, k674k / k674l KONKR x1.3 PASS (MORE reached walking down) | **APPROVED 15 Nov** (visual) | no | VERIFY - owner's checklist: MORE reachable (yes, k674l), MORE once and last (warn-uniform), focus after More open/close, rapid A/B, x1.0 and x1.3 clipping, no size change while navigating - to confirm in the 674k full gates |
| G2 | Two-line keys on narrow rows (item 4) | 674c | TwoLineKeys674Test | gates 674c+ | no | no | VERIFY |
| G3 | Cinema: Play first in the row on short windows | 674c/d | Batch478Test | k674d PASS | no | no | VERIFY |
| G4 | D332 x1.3 squeezes (Airy, Blades, Orbit, Circuit, Library, Dashboard, File select, Terminal) | 674e–k | | f674k Flip 2 x1.3 52/53, k674n KONKR x1.3 51/53 - none of the D332 layouts FAIL | no | no | TESTED (hardware) - awaiting owner visual review |
| G5 | Wii channel: one uniform row (Start not stretched, worded MORE, no •••), More page one row, no broken words | 674l | Hierarchy529Test | not yet | no | no | IN PROGRESS (gate pending) |
| G6 | Rapid A/B leaves a window open once (Cards, Crate, TV - D319 / D333 family) | - | | no rapid FAIL in f674k, k674m, f674o, k674n (4 full gates) | | | WATCH (HW-5) |
| G7 | Home glyph centring: Arcade popup ▶, Pocket ribbon START, Spot START, Library R2, Bar ◀ | 674m Arcade + Pocket; 674n Bar; 674o Library | Batch674nTest, Batch674oTest, QaGlyphKey675eTest, glyphcheck (675e: GlyphPill glyphs measured in their dumped key box) | Arcade, Pocket, Bar PASS on 674n (f674o / k674n); Library R2 FAIL on 674n -> 674o not on HW; Spot START = checker (D-674o-1) fixed in 675e, not re-measured on HW (HW-6) | | | IN PROGRESS |
| G8 | Cube: first A opened nothing on KONKR x1.3; L2 · R2 fact 0 px; Cube Classic x1.3 small card | - | | k673k, k674a | small card: owner's design choice | | OPEN / BLOCKED-OWNER (card) |
| G9 | QA checker: untested rows never counted as passes; the pixel compare covers uniform rows | 674l (POPUP_KEY_WORDS); 674o emulator verdict step | selftest 155/155 | full gates 674k / 674n: NOT TESTED = 0; unknown build -> INFRA (k674n before registration) | | | TESTED |
| G10 | Flip 2 charging sparkle replays (charge-limit loop on input_suspend) | 674i - removed from the dev line in 675i (kept: branch claude/charge-loop-674i, docs/claude/patches/charge-loop-674i.patch) | ChargeLoop674Test (in the patch) | **BLOCKED - DEVICE UNAVAILABLE** (owner 16 Nov: Flip 2 away; not failed, never run) | | | DEFERRED to v0.6.74+ (owner 16 Nov) |
| G11 | CI memory (test worker 9.6 GB) | 674e forkEvery 400; 674o forkEvery 150 (no effect: counts classes); 675a heavy Robolectric classes in testDebugHeavyRoboUnitTest (JVM per class), EndlessFillLitRoboTest in 6 shards; LookStyles states made at start | EndlessFillLitShardsTest; heavy task in the cloud (per-JVM peak <= 3.3 GB) | c7ad53a7 + c0760eb stopped at ~1,120 tests inside EndlessFillLitRoboTest (9.4 GB); 675a: CI green on 7dbcab0c (both runners; heavy task ran; lowest free memory 6.4 GB) | | | VERIFY - green on several pushes in a row before DONE (750b669c: 2 so far; 0cc6739 / 0e23337 red on a test lookup, not memory - fixed 677b) |
| G12 | Scheduled autonomous development - CLOUD (owner 15 Nov: replaces the PC hourly task once verified) | docs/claude/CLOUD_DEVELOPMENT.md, AUTORUN_LOCK.md | | clone + push verified from a cloud session 15 Nov; scheduled cloud run: pending verification | | | IN PROGRESS |
| G13 | Cloud development environment (build, tests, static checks, CI, commits from the cloud) | 674o tools/cloud/setup_android.sh, VOLTCAP_CLOUD stub | Batch674o/674n/488 pass in the cloud | SDK install, app + test compile (~7 min), targeted tests and pushes verified from a cloud session 15 Nov | | | DONE for the environment; scheduled runs: G12 |
| G14 | Emulator QA in GitHub Actions: all 53 layouts, native pages, 17 category styles, x1.0 / x1.3, KONKR / Flip 2 / RP Classic-like profiles, bounded jobs, artifacts, verdicts, failures -> backlog | 674o stage 1: x86_64 build, -skin, renderer matrix, verdict | grade_trial verdict | run 43762702001: swangle_indirect stays up, 2 layouts graded (FAIL: open / More step - timing suspected); swiftshader offline, guest precondition; 675b: artifact read - the popup WAS open in the firstA shot, the QaClip dump came after the fixed sleep -> popclip / shelfpages wait for the dump (written whole), selftest + QaClipWriteWhole675bTest | | | IN PROGRESS (stage 2: dump waits 675b, run 43780985909: e529b SHELF PASS, COVERDETAILS 10/11; e529a probe missed a popup the next shot showed -> 675c keeps the failed probe, re-probes once (late = open FAIL, no second A), dumps carry popupAgeMs + roots; run 43786748164: first A opens every time, the 2 FAILs = device grep missed controls present in the dump (the dumps with "Full story on Details ›") -> 675d reads dumps one node per line, dw waits for a whole dump; 675h gives shelfpages529.sh (e529c) the same reads; trial on 675h/675i: grader crash fixed 675i; run 43811840328 precondition failed - a foreign ANR dialog + the Inspect / Wobble question over the picker -> 675k: trial closes ANR dialogs (hide_error_dialogs + precheck), first-run hook answers the question; EmulatorHarness529Test; run 43833359452 (675m): preconditions PASS, graded: e529b SHELF PASS 11/11, COVERDETAILS open FAIL; e529a COVERDETAILS 10/11, SHELF 9/11 - all from the device grep missing controls in dumps holding "›" (probes held them) -> 675o: dump reads in plain ASCII, late extra-A window closed before the rapid presses; run 43887216747 (677a, 4c332f1): e529b COVERDETAILS PASS 12/12 (first full row), e529b SHELF first A opened nothing (probe 1 / home 0), e529a COVERDETAILS late open + More step / Details page missing, SHELF Details page missing; no crash) |
| G15 | Hardware QA request queue (cloud never waits for devices) | docs/claude/HARDWARE_QA_QUEUE.md (HW-1..5) | | | | | IN PROGRESS |

## A–F. Carried from REQUEST_COVERAGE_529 (13 Nov) - status as of then; update when touched

A1–A11 data safety (Save Sync P0, mid-game, active-emulator guard, simultaneous uploads, multi-file, clock, earlier versions,
restart survival, names, Syncthing, production backups) · B1–B7 popup requests of 529b (B1 "••• last" is superseded by the 15 Nov
brief: MORE as a word, last) · C1–C3 release / integration · D1–D20 backlog items (spine flicker, cold artwork, Shared Shelf, 3D Shelf,
blur, Save Vault, artwork refs, Connected Ignition, Thor dual-screen, performance, controller, Hub / Resume achievements, Airy no-art,
KONKR memory, ScreenScraper, K1 spacing, Odin 3 / overlays / AutoTDP / RA / GameNative, owner checks, sprint 528 leftovers, QA
tooling) · E defects.json triage · F1–F21 older requests (RP Classic hot charging, Pokémon Silver save, launch every console,
stability matrix, first sync, iiDB, Odin 2 Portal sweep, PS3 disc pack, real cartridge labels, turned custom cases, Thor ANR, folder
icon pack, Layout Studio, RetroArch GL, design questions, performance, settings clean-up, ideas, NEXT_OVERHAUL V/R, rule 11).
The full rows with their evidence are in docs/REQUEST_COVERAGE_529.md; each run that touches one copies it up into a table here with
its current state.
