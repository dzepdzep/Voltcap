# VoltCap master backlog — single source of remaining work

Last reconciled **10 Nov 2026 (batch 520)** against main `a1f762c` (v0.6.69, versionCode 76), the batch 505–519 evidence
(project docs POPUP_BATCH_505–516, SECURITY_519_RELEASE), ROADMAP_070.md, REQUESTS_CHECKLIST.md, CARTS_468/469,
E1E2_REFS_487.md and the code. Where an older document still says OPEN, the newer evidence listed here wins. ROADMAP_070.md and
REQUESTS_CHECKLIST.md are history; update only this file.

Statuses:
- **DONE**: implemented and verified on hardware (evidence named).
- **DONE-O**: implemented; needs the owner's eyes or hands.
- **PARTIAL**: part implemented or verified; what is left is named.
- **OPEN**: reproducible and not fixed.
- **BLOCKED-HW / BLOCKED-REF / BLOCKED-OWNER**: needs a device, a photo / measurement, or an owner action or approval.
- **DEFER**: not planned (risk or scope).

## Protected baselines (regression only, never redo)

| Area | Evidence |
|---|---|
| GBC transparent-shell PCB renderer (GbBoard: mapper, ROM / SRAM, battery, crystal, contacts, traces, both PCB sides, depth in clear plastic) | 468 / 469 |
| Real cartridge photo priority + PhotoShells (RealScans464Test) | 463 / 464 |
| GB-family side geometry, PCB in Home + Inspect | 469, 512 cart512 |
| Cardboard Box 1 / 2 / 3 rows; D131 corner shortcut; D142 Twin Desk hinge / popup | 438, 4xx |
| InputContract + category parity (L2 / R2, picker, tabs; D278 / D279) | 462, 482–484 |
| 53-layout game windows; Follow layout / Instant / Motion Off; case flights | 504–508 |
| Game shelf art-flash fix, idle settle (Shelf bob, effects, focus pulse, charging light, picker) | 510, 511, 513 |
| Custom CD colour logos + regional rules | 508–510 |
| R0 / R1 / R2 settings, badge panels, FullText, full-screen profile picker | 491, 503, 514 |
| v0.6.69 ScreenScraper / IGDB through Voltcap's service (signed media, no keys in the APK) | 517–519 |
| Xbox / PlayStation case fixtures (dimensions, spines X360 / PS3 / PS4, back bands, plastics, tray) | 516 |
| Thor Ken Burns smooth drift (511; the 512 / 513 stepped and paced variants are rejected) | 513 |
| Case Stacks recomposition fixes (D137: 493 / 494) and the 13 carousel arrangements | 493–494 |

## 1. Done and verified (newest first; only what changed status recently)

| Item | Evidence |
|---|---|
| Flat case art fits like the 3D faces (CropUnlessMuchCut): a 16:9 picture you picked is shown whole on the Game shelf (Plain cover), plain PS3–PS5 / Xbox cases; covers close to the window still fill | 520, RP Classic q520 (wide fixture, before / after) |
| A picture taken for a whole wrap whose front cut fails (no folds: a 16:9 picture on a PS1 jewel case) now shows the picture, not an empty case | 520, RP Classic q520 (PS1 Crash, Voltcap's case / Real case) |
| Unmeasured wraps / unfolded box scans load hidden until their shape is known (no flash of the whole scan or its spine before the front) | 520, code + unit tests; device: no flash seen in q520 (a one-frame flash can't be proven absent by screenshots) |
| ScreenScraper account check: "Testing…" stays (was cleared after 3.5 s while ScreenScraper can take ~45 s), Connect disabled while checking, slow-answer note; status messages in account windows stay until replaced, elsewhere 3.5–10 s by length | 520, unit tests; device PARTIAL (see 3) |
| Collector backs (PS4 / PS5 / Xbox …): a colour logo under 3 : 1 on the dark header bar (PS4 blue 1.8 : 1) is printed white | 520, unit test; device check in the 520 gate |
| v0.6.69 published; production Worker; real-provider checks; QA app on the Classic | 519 (SECURITY_519_RELEASE) |
| KONKR (= GT78) 53-layout regression, rapid A / B 96 / 96, controller contract, idle 0.5–1.3 % CPU | 516 |
| Xbox / PlayStation cases at 6 poses (dimensions, spines, bands, plastics, tray) | 516 |
| Rapid A / B on KONKR (B while opening / open / closing) — the 513 / 514 intermittent "window left open" not reproduced in 96 | 515–516 |
| E3 case print crop (D280), E4 GBC box spines, E5 Case Flow reflection, E6 controller contract | 484–485 |
| D257 / D263 / D265 / E7 owner-approved popup decisions | 480d, 487 |
| K3 Save Vault card looks; K5 CI | 488 |

## 2. Implemented, needs the owner (DONE-O)

| Item | What to check |
|---|---|
| Inspect two-finger pinch / pan (498; one-finger pause verified) | finger test |
| K2 home chips above screensaver / popups (487 fix) | does it come back on your Thor? |
| K4 custom spine flicker while scrolling (490: 82 → 0 / 90 → 0 measured) | visual check |
| Case tops / Stacks spine depth, Disc Station / Circuit popups | look approval |
| Title / logo slot remembered (475 / 503) | no visit-to-visit jump seen; no dedicated recording |
| Header backdrop cross-fade to "none" (~0.4 s) | keep or remove |
| Flat Game shelf wide pictures (520): the strips round a whole wide picture show the game's colour gradient | keep, or a plain dark plate |

## 3. Genuinely open and reproducible

| Item | Status / next step |
|---|---|
| KONKR native / graphics memory growth under heavy navigation (183 → 433 MB, 383 at rest; Classic flat) | OPEN — suspected MediaTek driver / texture caching, **not** a confirmed leak; needs a KONKR soak (BLOCKED-HW: not connected) |
| Case Stacks fast scroll on KONKR: burst p90 36 ms / 10.4 % janky (Shelf 0 %) | OPEN, render-bound (494); no change without a new measurement plan |
| ScreenScraper per-game scrape progress for 40 s+ answers (the account check is fixed) | PARTIAL — game look-ups show the existing loading state; no device run with a slow answer yet |
| Cover details leaving-case first flight frame (1 of 666 frames, Nova) | OPEN, low; cause (spine bounds written while drawing) not confirmed → not changed |
| K1 game spacing in the remaining compatible layouts | PARTIAL — Voltcap row, Tiles, Game shelf, Cover details, Case stacks + 9 that had it; others not started |
| B5 long rows / D137 render cost | PARTIAL — see Case Stacks above |

## 4. Cosmetic refinements worth doing

| Item | Status |
|---|---|
| PS5 / Xbox One spine logo | BLOCKED-REF — no authentic usable artwork in the pack (documented 508 / 516); nothing invented |
| Front console band only with "Cover templates" on | by design (516) — owner decision if it should change |
| Missing CD logo assets: FDS, SG-1000, Atari 5200, Xbox One / Series, Switch 2, PS5, CD-i | BLOCKED-REF |
| Sega CD category logo | BLOCKED-HW (no Sega CD games in the fixture libraries) |
| Scan stretch / crop audit across layout families | PARTIAL — the shared flat rule fixed in 520; no layout-by-layout audit (not wanted) |

## 5. Blocked (hardware, references, owner)

| Item | Blocker |
|---|---|
| Odin 3 navigation bar hidden automatically inside Voltcap | BLOCKED-HW (Odin 3 not connected); inspect read-only first, no guessing |
| Thor dual-screen lifecycle (D2), app handoff, remaining second-screen interactions, A4 Ribbon two-screen | BLOCKED-HW / OWNER (needs an app opened on the top screen) |
| In-game overlays (HUD, notifications, perf stats, music), D4 FPS / AutoTDP / thermal | BLOCKED-OWNER (approved game session) |
| Real RetroAchievements account path | BLOCKED-OWNER |
| GameNative real launch (D1) | BLOCKED-OWNER (GameNative not signed in) |
| B2 background clip measurement | BLOCKED-HW (no clip library on the connected devices) |
| E1 cartridges: GBA side / top, SFC + PAL rears, Famicom / NES rears, Mega Drive, double labels; E2 case calipers / box sides | BLOCKED-REF — 9 photos listed in tools/qa/E1E2_REFS_487.md |
| GBC PCB refinement for MBC1 / MBC5 layouts | BLOCKED-REF — approximate layouts stay; MBC3 + RTC (Pokémon G / S / C) is reference-based and protected |
| Light-up cartridge, owner's exact Pokémon Brown label | OWNER data |
| KONKR / Thor re-runs of A1 / A3 / A5 / B3 / C1 / C2 | BLOCKED-HW this session (only the RP Classic is connected) |
| Production backups P2–P7, real install B2–B6 | BLOCKED-OWNER (each step approved separately; docs/DATA_BACKUP_513.md, docs/REAL_INSTALL_510.md) |

## 6. Deferred
New system layouts, new 3D templates, consoles without references, Studio templates, popups inside the activity window,
a separate hero surface for Ken Burns (only with a measured net win), screensaver overlay leakage (no repro since 488).
