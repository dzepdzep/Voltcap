# VoltCap master backlog (v0.6.68 staging) — single source of remaining work

Started update 504 (8 Nov 2026). Newest state wins; one line per request. Statuses:
**DONE-V** implemented + verified on hardware · **DONE-O** implemented, needs owner / device verification ·
**OPEN** not implemented · **DEFER** deferred (risk / scope, not approved for 0.6.68) · **SUPER** superseded by an existing solution.
Staging code lives only in staging_local/0.6.68/staging.patch (see COWORK_HANDOFF RESUME line for the sha). "Verified" means a
device check in Voltcap QA with the evidence named in SUMMARY; a passing unit test alone is never DONE-V.

## Inspect / physical media
| Item | Status | Where / evidence |
|---|---|---|
| Pinch zoom 1–3× + two-finger pan, bounded; Y / long press reset | DONE-O (finger test) | 498; insp500.sh |
| Pause idle motion while handled; settle back ~3 s after the last finger lifts, 360 ms eased, idle resumes without a jump | DONE-V for one finger (Thor QA app: PS1 case, open case + disc, N64 box, cartridge out — log pause → hold=3000 → dur=360 → resume, recording: still frames through the hold, ~0.37 s ease, sway back with no jump); two-finger pinch/pan = owner finger test | 504 InspectMotion.touchDown/touchUp/release, contentPhase clock; q504 recordings |
| Opening a case / taking the piece out by tap left its idle motion paused until the next drag | DONE-V (found + fixed 504: caseStepDone; Thor recording 0.00 motion before → regular cycles after) | 504 Batch504Test.caseStepsResumeTheIdleMotion |
| CD label spin visibly pausing | DONE-O (the fixture disc is plain, so its spin isn't visible in pixels; clock logic tested) | 504 contentPhase |
| No tap / open / close from the end of a pinch | DONE-O | 504 pinchedJustNow |
| Game shelf: case top faces (cardboard vs plastic), pulled-case shadow, contact shadow | DONE-V (owner look approval pending) | 499, 501, 502 |
| Case Stacks spine depth (shelf's Spine depth draw) | DONE-V (owner look approval pending) | 503 |
| Top faces in Case Stacks | SUPER (eye-level view: not visible) | 503 note |
| Generated GB pak (Pokémon Brown path) faces / edges / seams | DONE-V on fixture; real game owner-only | 502 cart502.sh |
| Clear / opaque memory cards at all angles | DONE-V (fixture, production renderer) | 502 QaCardSheet |
| Real Pokémon Brown, light-up cartridge, RP Classic | OPEN — owner device | — |
| Template spine names cut ("…Link To The"), Hero wheel side labels "…" (rule 11) | OPEN | found 503 |
| Authentic front/side/back per console (R25 references) | OPEN per model; no new models without references | ARTWORK_SOURCES |

## Artwork / loading
| Item | Status | Where |
|---|---|---|
| Title blank on every visit for unreadable / square logos | DONE-V (Thor Arctic) | 503 LogoOrName |
| Logo slot shape remembered (no name jump between visits) | DONE-O | 475 LogoAspects + 503 failures |
| Home logo part shows the name when the logo fails | DONE-O | 503 |
| Stale in-between art | DONE-V | 499 |
| Showcase header title stable while art arrives | DONE-V (no change needed) | 503 recording |
| Header backdrop cross-fades from the previous game's art to "none" (~0.4 s) | OPEN — owner to judge (transition vs stale) | 503 observation |
| Scan stretch / crop audit across all layouts | OPEN | — |

## Achievements
| Item | Status | Where |
|---|---|---|
| Badges as one panel + cache-only neighbour warm-up | DONE-V (Thor Blades A/B) | 503 BadgeWarm |
| Overlay ×1.6: ▲▼ in header, tabs whole | DONE-V (GT78) | 502 |
| Pinned / full-list comments consistency | DONE-O | 496–497 |
| Real RA account path (owner data) | OPEN — owner device | — |

## Popups (game window scenes) and layout-aware app windows
| Item | Status | Where |
|---|---|---|
| Every layout has a scene (51/53; TV opens on its TV screen, Custom = standard window by design) | DONE (code audit 504) | WinScenes.winSceneFor |
| Shared families with per-layout variants: Shelf family (Shelf / Stacks / Consoles / Cover details), Pocket (3), Twin (3), Hero (2), Tiles/Grid | DONE | WinScenesStyled |
| Disc Station popup in the layout's look + owner accent (was silver deck, fixed teal) | DONE-V look on Thor (owner approval pending) | 504 q504_disc_circuit_popups.png |
| Circuit popup follows Board colours (was always green board) | DONE-V look on Thor, Theme board (owner approval pending) | 504 same screenshot |
| Remaining fixed-palette scenes to review against their home: RentalTag, BubbleCard, SpotPoster, LibraryPage, GalleryPlacard, RecordSleeve, PocketClassicMenu, CrossOptions | OPEN (batch 505) | 504 matrix |
| Wii Channel preview parity | DONE-O | 425/497 |
| Layout-aware Settings with custom accent | DONE-V (Arctic magenta) | 503 |
| Achievements / comments / Save Vault windows on hardware | DONE-O | 497–499 |
| Own / Banner / Classic / Row styles never overridden | DONE (rule kept) | — |

## Navigation / UI
| Item | Status | Where |
|---|---|---|
| Focus kept after Settings | DONE-V (Arctic, pixel-identical) | 503 |
| Focus after game window / options | DONE-V | 501 |
| Menu sheets on short screens | DONE-V (GT78) | 501 |
| Inspect / popup focus return on all layouts, rapid A/B | OPEN (sample-based) | — |
| Screensaver overlay leakage | OPEN — no repro yet | — |
| Dual-screen popups (Thor second screen) | OPEN | — |

## Settings / R1 (protect, don't redo)
Per-layout spacing, custom palettes, colour-source precedence (Theme.kt order), settings search, icon / order prefs, preview
protection, R0/R1/R2 fixes: **DONE** — never reimplement; regressions only.

## In-game overlays
| Item | Status |
|---|---|
| HUD / notification positions, perf stats, music controls readability | OPEN (no batch yet) |

## Deferred (not approved for 0.6.68)
New system layouts, new 3D templates, additional console models without references, Studio templates: **DEFER**.

## Release blockers for v0.6.68 (feature freeze → final QA)
1. Owner approval of new default looks (case tops, stacks depth, window families, Disc Station / Circuit popups).
2. Owner finger test: Inspect two-finger pinch / pan (one-finger pause / settle / resume verified 504).
3. Owner-only media: real artwork, Pokémon Brown, RP Classic, KONKR.
4. One supervised sync / backup regression on the real install (the owner reports the Thor's Drive settings may already be
   back on; QA never touches them).
5. Owner's go to merge the staging patch, then the full PRE_RELEASE_CHECKLIST on the merged build.
6. Rule-11 leftovers above.
