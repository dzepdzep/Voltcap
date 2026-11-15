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
| P13 | Rule 11: no "…" truncation anywhere | 259 places left (13 Nov) | static TRUNCATE | | | | OPEN (F21) |
| P14 | Category +N overflow counters removed | no | | | | | DEFER (owner: later refinement) |
| P15 | Everything stays free, no paid unlocks / mandatory paid APIs | yes | | | standing rule | yes | DONE (standing) |

## G. This session (673 / 674, 15 Nov 2026)

| # | Requirement | Impl | Unit | HW | Owner | Rel | Status |
|---|---|---|---|---|---|---|---|
| G1 | Record crate x1.3: deliberate vertical menu (identical keys, Play first, MORE last, D-pad scroll, no clipped keys / facts) | 674e–g | CrateVertical674Test | f674i Flip 2 x1.3 PASS, k674k / k674l KONKR x1.3 PASS (MORE reached walking down) | **APPROVED 15 Nov** (visual) | no | VERIFY - owner's checklist: MORE reachable (yes, k674l), MORE once and last (warn-uniform), focus after More open/close, rapid A/B, x1.0 and x1.3 clipping, no size change while navigating - to confirm in the 674k full gates |
| G2 | Two-line keys on narrow rows (item 4) | 674c | TwoLineKeys674Test | gates 674c+ | no | no | VERIFY |
| G3 | Cinema: Play first in the row on short windows | 674c/d | Batch478Test | k674d PASS | no | no | VERIFY |
| G4 | D332 x1.3 squeezes (Airy, Blades, Orbit, Circuit, Library, Dashboard, File select, Terminal) | 674e–k | | f674i 6/6; k674k 3/4 (File select More -> 674k) | no | no | VERIFY in the 674k gates |
| G5 | Wii channel: one uniform row (Start not stretched, worded MORE, no •••), More page one row, no broken words | 674l | Hierarchy529Test | not yet | no | no | IN PROGRESS (gate pending) |
| G6 | Rapid A/B leaves a window open once (Cards, Crate, TV - D319 / D333 family) | - | | seen k674j, k674g, k673i | | | OPEN (investigate) |
| G7 | Home glyph centring: Arcade popup ▶, Pocket ribbon START, Spot START, Library R2, Bar ◀ | - | glyphcheck | FAIL in f674j / k674j | | | OPEN |
| G8 | Cube: first A opened nothing on KONKR x1.3; L2 · R2 fact 0 px; Cube Classic x1.3 small card | - | | k673k, k674a | small card: owner's design choice | | OPEN / BLOCKED-OWNER (card) |
| G9 | QA checker: untested rows never counted as passes; the pixel compare covers uniform rows | 674l (POPUP_KEY_WORDS) | selftest 151/155 (4 = VM ffmpeg) | re-grade f674j 4->0, k674j 14->0 NOT TESTED | | | VERIFY |
| G10 | Flip 2 charging sparkle replays (charge-limit loop on input_suspend) | 674i | ChargeLoop674Test | **BLOCKED**: production Voltcap's own limit is on and would fight the QA app over input_suspend; a clean test needs production's limit off (owner keeps production settings) | owner authorized a QA-app test | no | BLOCKED-OWNER |
| G11 | CI memory (test worker 9.6 GB) | 674e forkEvery 400 | | CI 9c11c496 green | | | DONE |
| G12 | Hourly scheduled development run | scheduled task "VoltCap hourly backlog run" (15 Nov) | | | requested by the owner | | IN PROGRESS (first runs to be checked in claude/AUTORUN_LOG.md) |

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
