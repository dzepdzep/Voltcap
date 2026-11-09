# VoltCap master backlog (v0.6.68 staging) — single source of remaining work

Started in update 504 (8 Nov 2026); last updated in **514 (9 Nov 2026, feature freeze)**. The newest state wins, with one line per request.

Statuses:
- **DONE-V**: implemented and verified on hardware.
- **DONE-O**: implemented; needs owner or device verification.
- **OPEN**: not implemented.
- **DEFER**: deferred (risk or scope; not approved for 0.6.68).
- **SUPER**: superseded by an existing solution.
- **PENDING-HW**: waiting for a device that wasn't available.

Staging code lives only in staging_local/0.6.68/staging.patch (the COWORK_HANDOFF RESUME line has the sha). "Verified" means a device check in Voltcap QA, with the evidence named in SUMMARY. A passing unit test alone is never DONE-V.

## Popups (game-window scenes) — owner-approved direction (504–508)
| Item | Status | Where / evidence |
|---|---|---|
| Every layout has a scene (51/53; TV opens on its TV screen; Custom = standard window by design) | DONE | 504 code audit |
| P0: Layout Studio window, Disc station beside its player, Case Flow name band, Channel grid / Cinema colours, window-owned dim, Motion Off immediate | DONE-V, owner-approved | 506 PDF |
| Popup windows setting (Follow layout / Instant), independent of "The game's info"; Off / Reduce motion win | DONE-V, owner-approved | 507 |
| P1 families: Game shelf, Case stacks, Console stack, Cover details, Tile / Grid, Twin ×3, Card binder, Cube, Hub, Timeline | DONE-V, owner-approved (508) | 507 / 508 PDFs |
| Case trip on Game shelf / Case stacks (470 / 380 ms, comes forward) | DONE-V, owner-approved — keep as is | 508 frame strips |
| Tile type on small screens, printed card front, cube light, undated Timeline node | DONE-V, owner-approved | 508 |
| Popup first frame on the GT78 (A → first frame ~45–80 ms) | INVESTIGATED (509): no bounded pre-warm helps; see POPUP_BATCH_509.md | 509 |
| Case Flow window under Motion Off | DONE-V (506 window-owned motion; Off = 1 frame) | 506 / 507 recordings |
| Remaining families' motion (8 measured on GT78: single-phase, no double transitions) | DONE-V (audit) | 508 P4 |
| GT78 "first A" | DONE-V: not a bug — the profile picker takes it after a cold start | 508 |
| KONKR / compact-screen regression of the 507–508 popups | PENDING-HW (KONKR not connected in 508 / 509) | — |

## Custom CDs
| Item | Status | Where |
|---|---|---|
| Real colour logos (≥ 3 % colour) + baked outline; DS / PS3 / PSP keep the authentic white print | DONE-V, owner-approved | 508 |
| Regional keys: Mega Drive, TG-16, TG-CD, NGPC, WonderSwan Color have their own logo / disc; NGP / WS use their own model's logo (also badges and cross-menu logo look-ups) | DONE-V offline + unit tests (no NGP / WS fixture games to show on device) | 508 / 509 |
| SFC / Famicom: the shipped assets are the Nintendo Classic Mini's (print "ニンテンドークラシックミニ") → not used; generic CD, as before 508 | DONE — owner decision (510): keep the generic CD, no NES / SNES substitute, until authentic regional assets exist | 509 / 510 |
| Missing logo assets: FDS, SG-1000, Atari 5200, Xbox One, Xbox Series, Switch 2, PS5, CD-i (generic CD, nothing invented) | DOCUMENTED | 508 coverage table |

## Inspect / physical media
| Item | Status | Where / evidence |
|---|---|---|
| Pinch zoom 1–3× + two-finger pan, bounded; Y / long press reset | DONE-O (owner finger test) | 498; insp500.sh |
| Pause idle motion while handled; settle ~3 s; resume without a jump | DONE-V one finger; two-finger = owner test | 504 |
| Tap-opened case left idle motion paused | DONE-V | 504 |
| CD label spin pausing / drag on the disc | DONE-V | 505 |
| Game shelf case tops / shadows; Case Stacks spine depth | DONE-V (owner look approval pending) | 499–503 |
| Generated GB pak, memory cards (fixtures) | DONE-V | 502 |
| Pokémon Brown = generated / custom cartridge renderer (not a physical cartridge): fixture at 16 poses incl. edge-on 89/90/91° and back; black side, seam and green/yellow edge fixes kept; translucent memory cards kept | DONE-V (512, Thor) | 512 cart512 |
| Owner's exact Pokémon Brown artwork; light-up cartridge; RP Classic | OPTIONAL — owner data (RP Classic may stay pending) | — |
| Authentic front/side/back per console (R25 references) | OPEN per model; no new models without references | ARTWORK_SOURCES |

## Artwork / loading / titles
| Item | Status | Where |
|---|---|---|
| **Achievement badge loading**: badges as one panel + cache-only neighbour warm-up | DONE-V (Thor Blades A/B); real RA account still owner-only | 503 BadgeWarm |
| **Title / logo shifts**: blank title for unreadable / square logos | DONE-V (Thor Arctic) | 503 |
| Title / logo shifts: logo slot shape remembered; home logo part falls back to the name | DONE-O (no visit-to-visit jump seen in 503–508 captures; no dedicated recording) | 475 / 503 |
| Header backdrop cross-fade to "none" (~0.4 s) | OPEN — owner to judge | 503 |
| Stale in-between art | DONE-V | 499 |
| Scan stretch / crop audit across all layouts | OPEN | — |

## Navigation / UI / dual screen
| Item | Status | Where |
|---|---|---|
| Focus after Settings / game window / options; rapid A B A B | DONE-V (GT78 508 / 509: one A opens, B closes, reopen=false, no launch) | 501 / 508 / 509 |
| Menu sheets on short screens | DONE-V (GT78) | 501 |
| **Screensaver overlay leakage** | OPEN — no repro yet | — |
| **Dual-screen**: Twin popups dim / close from the other screen | DONE-V (Thor) | 507 |
| **Dual-screen**: other second-screen issues (Thor) | OPEN | — |
| Game shelf idle bob kept drawing at rest (GT78 41 % CPU / 382 frames per 6 s; Thor 79 % / 762) | DONE-V (510): bob settles after 10 s without input (0.9 s ease), resumes from its rest pose on any input (0.6 s), no extra timer; at rest GT78 0.3–0.6 %, 0 frames; Thor 1.6–2.0 %, 0 frames | 510 |
| After-input redraws until the 30 s step: charging light (the GT78's whole ~20 fps), focus pulse, effect clock | DONE-V (511): settle with HomeIdle.settled at 10 s, resume without a jump; GT78 0.6–1.3 % / 0 frames at 15 s on Shelf / Stacks / Tiles | 511 |
| Thor Tiles Ken Burns drift cost (~80 % CPU for 22 s) | REVERTED (513): 512 steps and a paced 60 fps variant both judder (presentation even 16–58 % vs 100 %); the smooth 511 drift ships. Cheaper only with a separate hero surface (C) | 513 kb513 / pace513 |

## Achievements / overlays
| Item | Status | Where |
|---|---|---|
| Overlay ×1.6: ▲▼ in header, tabs whole | DONE-V (GT78) | 502 |
| Pinned / full-list comments consistency | DONE-O | 496–497 |
| **In-game overlays**: HUD / notification positions, perf stats, music controls readability | OPEN (no batch yet; needs an approved game run) | — |
| Real RA account path | OPEN — owner device | — |

## Settings / R1 (protect, don't redo)
Per-layout spacing, custom palettes, colour-source precedence (Theme.kt order), settings search, icon / order prefs, preview
protection, R0/R1/R2 fixes: **DONE** — never reimplement; regressions only.

## Deferred (not approved for 0.6.68)
New system layouts, new 3D templates, additional console models without references, Studio templates, popups rendered inside
the activity window instead of a Dialog (the only change that would remove the window-creation part of the first frame): **DEFER**.

## Release categories (512)
### A. Genuine blockers (before v0.6.68 is published)
1. **Production-data backups P2–P7** (docs/DATA_BACKUP_513.md), each approved separately, before any Part B step.
2. **Part B on the real install**, each step approved separately (docs/REAL_INSTALL_510.md). B2 counts as separate from B3 only after P7 (launch-time sync off).
3. **Owner's go to merge** the staging patch (75e70fc1…), then the PRE_RELEASE_CHECKLIST on the merged build, then the GitHub release v0.6.68.
4. **Device coverage the owner wants before release:** Retroid Pocket Classic + Nova DONE (513, QA app); KONKR not run; Thor / GT78 not re-run after the 513 fixes. Retroid results do not stand in for KONKR.

### B. Owner-only visual / hands-on checks (not blockers unless the owner says so)
- Inspect two-finger pinch / pan (finger test; only one-finger pause/settle verified, GT78 512).
- Look approvals: case tops / stacks depth, window families, Disc Station / Circuit popups.
- Exact owner artwork (Pokémon Brown label etc.), real RA account path.

### C. Optional post-release work (keep)
- Achievement image loading (beyond 503 BadgeWarm), in-game overlays (HUD / notifications / perf stats / music), physical media (light-up cartridge, RP Classic, authentic per-console faces), screensaver overlay leakage, dual-screen issues on the Thor, Settings polish.
- Ken Burns: a separate surface (SurfaceView) for the hero so the drift doesn't redraw the window; or a static hero under Save power.
- Cover details: leaving case's first flight frame (1 in 666 frames, Nova) — low; flight reads spine bounds written while drawing (514).
- Rapid A B A B on the Nova: an occasional window left open (2 of 12 rounds once, 0 of 12 on the rerun; no duplicate) — intermittent (514, GT78 513).
- Classic Game shelf: even 60 fps, GPU ~17 ms/frame, latency ~49–65 ms — measured, no change (514).
- GT78 rapid A/B: once in 15 rounds a B during the opening was lost (window stayed open, no duplicate); not reproduced in the rerun.
- Thor inline-Inspect pause check (Thor shows an inline case preview; the 512 drag did not log a pause).
- Scan stretch audit, header backdrop fade, missing CD logo assets.

### D. Completed and verified (512–514)
- 514: "Who's playing?" fills the RP Classic (window fitted the nav-bar inset; now edge to edge, 36/36 cases full screen); wide covers on 3D cases visible and whole (stand-in check, wrap bound) on Shelf / Stacks / Cover details / Case flow / Inspect; Case Flow title no longer ghosts; Nova Side drawer and art-pack sheet fit 4:3.
- 513: Ken Burns kept smooth (511) with measured evidence; Game shelf incoming-art flash fixed (Nova 8→0, Classic 7→0); Camera / Night / Panels whole words on 4:3; "Who's playing?" settles when idle (Nova 765 → 0 frames); Retroid Classic + Nova smoke all pass; memory stable; production-data backup plan.
- Release identity 0.6.68 / vc 75, same package and signer; release APK not debuggable (514: b6c72313…).
- Release builds ignore `voltcap_test_hooks` (QaTrace, launch block, Drive QA pause, test hooks): only the isolated QA app or a debuggable build use it.
- Thor's stale hooks file removed after backup (0 bytes, 8 Nov 19:14:45).
- Corrected recovery plan: roll forward (vc 76), never downgrade / uninstall (docs/RELEASE_PLAN_511.md).
- Popup / mode / window regression (stab512) Thor + GT78: all pass, 0 launches, 0 fatal, 0 AYN.
- Generated GB cartridge + memory cards at 16 poses (Thor).
- Earlier: Shelf bob settle (510), idle effects settle (511), popups 505–508, Custom CDs 508–510.
