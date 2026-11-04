# VoltCap roadmap to v0.7.0 (master request, 4 Nov 2026) — persistent status

Source: the owner's MASTER DEVELOPMENT REQUEST (v0.6.68 and beyond) + ADDITIONAL REQUEST (3D case prints, spines, reflections)
+ VERSIONING PLAN. Every item stays here until DONE (verified) or explicitly dropped by the owner. Update the status after
every milestone; newest session notes go at the top of SUMMARY.md.
Status: DONE (verified) · PARTIAL · BLOCKED · NOT STARTED. Priority: P1 (Batch A) … P5 (Batch E).

## Rules (from the request)
- v0.6.67 published = frozen rollback. All work in v0.6.68 staging (`next/0.6.68/staging.patch`), later released as **v0.7.0**
  (versionCode > 74, same signing, in-place upgrade from 0.6.67 verified, normal + compat APKs) — only with explicit approval.
- Protect ROMs, artwork, labels, categories + order, profiles, layouts, brightness / volume, AutoTDP, charging, status-bar
  prefs, all settings. USB devices only (KONKR BW0306N230000845, Odin b92036fb; Thor 3140972f when on USB). Snapshot +
  verified restore around every hardware run. Never publish / tag without approval.
- Checkpoint SUMMARY.md + CURRENT_TASK.md + COWORK_HANDOFF.md after each milestone; commit through the PC workflow.
- Partial work is never reported DONE; planned test combinations are not coverage.

## Batch A — objective defects (P1)
| id | item | status | notes |
|---|---|---|---|
| A1 | D271 long titles cut in window scenes (shared FullText; re-run failed T3 fixtures) | PARTIAL | 478d: Odin PASS (17 scenes, real + long); KONKR NOT TESTED |
| A2 | D268 Terminal 6 px preview jump (Odin) | DONE (Odin) | cursor glyph changed the line height; 478a o478T/U PASS; KONKR NOT TESTED |
| A3 | Other T3 failures: Blades harness state, Cinema 18 px control, first-A timing, missing evidence (Tiles), wrong test game | PARTIAL | Blades hook, Cinema keys / empty stop (D272), chips 24 dp, picker-first-A harness fixed; Tiles evidence + KONKR re-run left |
| A4 | Pocket Ribbon dual-screen: no duplicate title on the upper screen (D270); single-screen keeps its title | PARTIAL | code done; two-screen check needs the Thor |
| A5 | Category logos: GBA / Sega CD as black lines, sizes; long category names off screen; stale headings, counts, carousel jumps, picker vs L2/R2 | NOT STARTED | |
| A6 | Proven artwork / scrolling regressions; no other game's art flashing | NOT STARTED | |

## Batch B — layout and performance foundations (P2)
| id | item | status |
|---|---|---|
| B1 | Full-screen / unused space audit of 53 layouts (Netflix-like Cinema edges, padding, status-bar space) via shared sizing helpers | NOT STARTED |
| B2 | Background video suspended only when fully covered by opaque UI (not drawer / Settings dim edges); measured CPU / GPU / frames / memory / resume | NOT STARTED |
| B3 | Adaptive status bar: Follow layout (default for new installs) / Always / Minimal / Hidden; per-layout integration; existing prefs kept | NOT STARTED |
| B4 | Shared animation system (Off / Subtle / Normal / Expressive, reduced motion) for popups, Settings, R1, categories, drawer, profile picker, Details, More/Less, dialogs; input safe during animations | NOT STARTED |
| B5 | Scrolling / layout stability audit (rows, titles, covers, late labels, stale art, placeholders) incl. D137 Case stacks; keep 13 arrangements | NOT STARTED |

## Batch C — personalisation (P3)
| id | item | status |
|---|---|---|
| C1 | Colour audit + semantic roles (background, surface, text 1/2, accent, focus, buttons, borders, highlights) | NOT STARTED |
| C2 | Presets (Original, Plain light, OLED black, light blue / yellow / pink, mint, lavender, peach…), hue picker, S/L, HEX, reset, per-layout overrides, global inheritance, readability guard; never recolour covers / labels | NOT STARTED |

## Batch D — functional integrations (P4)
| id | item | status |
|---|---|---|
| D1 | GameNative `.steam` files → PC / Steam category, App ID validation, titles / art, no duplicates, real launch verified | NOT STARTED |
| D2 | Dual-screen lifecycle (lower UI exits when an app opens up top; "Probably still open" stale; focus to wrong screen) | NOT STARTED (Thor needed) |
| D3 | Independent brightness per screen where Android allows | NOT STARTED (Thor needed) |
| D4 | FPS / frame-time / jank / CPU / GPU / battery / thermal / AutoTDP accuracy audit (no risky power tests without approval) | NOT STARTED |

## Batch E — hardware art and refinement (P5)
| id | item | status |
|---|---|---|
| E1 | Cartridges: GBA side geometry, clear GBC PCB, NES geometry / rear, SFC/PAL rear, Famicom variants, Sega references, backs, double labels / product codes, ROM-hack labels (reference-first R25; keep real scans) | NOT STARTED |
| E2 | Boxes / cases: custom box sides, case / spine geometry, artwork fitting | NOT STARTED |
| E3 | 3D case prints cropped in Case Flow vs Inspect (Case Flow, Case Stacks, Game Shelf, Inspect: same texture, no crop / stretch) | NOT STARTED |
| E4 | Spine widths per case type vs real measurements (thickness / UV / texture scale), consistent in Inspect and layouts | NOT STARTED |
| E5 | Case Flow reflection: bright strip / hard edge at the fade end | NOT STARTED |
| E6 | Controller contract re-check (Select = Settings, R1, L2/R2, B closes, Wii preview popup), touch parity | NOT STARTED |
| E7 | Owner-decision visuals: D257 Tiles, D263 Cover details story, D265 Twin paging, popup hierarchy, tiny controls, secondary buttons | NOT STARTED (owner review) |

## QA (with every batch)
T1 other styles, T2 font / display size (needs the owner to change system font scale), T3 remaining content, T4 pairwise
(59 runs), T5 navigation, T6 settings groups (101), T7 Settings / R1 / L1 / picker / drawer / profile / music views — all
NOT TESTED beyond update 477's report. CI must stay green on the real staging source.

## Release (after approval only)
| id | item | status |
|---|---|---|
| R1 | Regression vs v0.6.67: 53 layouts, popup modes, controller, carts / cases, artwork, settings, profiles, libraries, dual screen | NOT STARTED |
| R2 | In-place upgrade 0.6.67 → 0.7.0 without data loss; versionCode > 74; signing | NOT STARTED |
| R3 | GitHub release v0.7.0 (normal + compat APKs, notes, checksums, CI) | BLOCKED (owner approval) |
