# VoltCap — requests checklist (kept current every update; newest status first)

Legend: DONE (verified on hardware unless noted) · FIXED (code + tests, hardware partial) · OPEN · DEFERRED (owner decision / device needed).
Release: v0.6.66 RC frozen and unpublished; all new work in `next/0.6.67/`. No publishing without the owner's approval.

## Update 457 (2 Nov 2026)
| Request | Status | Evidence / note |
|---|---|---|
| D172 Settings rows "spawn in" (RP Classic) | DONE (staging, hardware) | focus scroll keeps the next row in view (A/B recording RP Classic, Thor checked); window flat (GPU 11.0 -> 10.2 ms); panel decoration cost measured, look unchanged |
| D172 Thor regression | DONE | Settings GPU 4 ms, 5 % jank |
| D173 Stadium / Snap packaging | BLOCKED | owner saw it on the KONKR Pocket (not connected); Thor shows real US boxes |
| D174 Pokémon Brown generated cart | REPRODUCED, OPEN | flat grey shell, blank top recess, box cover used as sticker |
| D175 GBA side lines | REPRODUCED, OPEN | ~90°: striped column from the side picture; edge-on-face fix tried, no effect, reverted |
| D137 / AutoTDP / D168-D169 | DEFERRED | unchanged |

## Update 456 (2 Nov 2026)
| Request | Status | Evidence / note |
|---|---|---|
| Background hold under drawer / Settings (option B) | DONE (RP Classic measured; Thor no change) | Settings home window 60/s -> 0; drawer frames halved; same look, no black, clips/scenes resume from their place |
| Settings choppy (RP Classic) | PARTIAL | home redraw behind it fixed (D171); Settings window's own ~27 ms frames open (D172); Thor not re-measured |
| Pocket Quest long category names | DONE (RP Classic + Thor) | D170: real + very long + Unicode names, first/last, R2 through all; PqTabs456Test |
| Frozen RC artefacts | DONE | D176 freeze_rc.py + release_candidates/ + SHA256SUMS.txt; original 0.6.66 file lost |
| Pokémon Stadium / Snap packaging | OPEN | D173: cartridge + label on Ribbon look right; box/case layer not inspected |
| Generated cartridges (Pokémon Brown) | OPEN | D174 not investigated |
| GBA side lines when turned | OPEN | D175 code suspect, not reproduced |
| D168 / D169 artwork mismatch | SKIPPED (owner) | unchanged |
| D137 / AutoTDP | DEFERRED | KONKR on USB / approval |

## Update 455 (2 Nov 2026)
| Request | Status | Evidence / note |
|---|---|---|
| Pocket Ribbon top: bigger piece, title + platform / year / last played, graceful fallbacks | DONE (Thor) | N64, 3DS, PS1, PS3, GBA, Emulators; missing year / never played drop out; RibbonTop455Test |
| Long names / rapid nav / reopening windows / lower screen | DONE (Thor) | long title slides whole; 70 rapid presses, art = caption; window x5 no launch; lower screen unchanged |
| Optional screenshot on the top | NOT ADDED | no room without crowding |
| Background pause under fully opaque full-screen panels | WAITING FOR OWNER | drawer / Settings ~94 % wide, home visible around them — rule excludes them; question sent |
| Restore exact frozen RC APK | NOT POSSIBLE AS A FILE | original signed APK overwritten on the PC; rebuild of the unchanged RC source installed |
| Thor test mode off / QA files removed / default Home | DONE | hooks .paused, /sdcard/pv removed, view state verify OK, HomeAlias |
| D168 / D169 cartridge label suspects | LOGGED | TND hack shows TWINE label; aPS3e shows a C64 cover |

## Update 454 (2 Nov 2026)
| Request | Status | Evidence / note |
|---|---|---|
| P1 performance outside Case stacks | MEASURED | perf.sh before (RC) on both devices; bottlenecks D64 (background redraw → drawer ≈30 fps) and D165 (Settings window ~177 ms + 50 ms) |
| P1 D64 idle GPU | INVESTIGATED, WAITING FOR OWNER | cause = selected background (BLACK preview: 0 frames); proposal in SUMMARY 454, nothing changed |
| P2 icon consistency | DONE (audit, no defect) | colour everywhere; one-colour only via the L1 "themed" / second-dock "mono" switches |
| P3 D83 Now Playing / Widget Board / Settings subtitle | FIXED (Settings seen OK on Thor) | FullText; Now Playing + Widget Board not seen on hardware |
| P3 long titles / missing art in popups | DONE in 453 (16 layouts, 2 games) | not repeated |
| P4 D164 preview reliability | DONE (verified, Thor) | previews keep their layout across categories |
| P4 D102 Cross ◀▶ | CLOSED (not a bug) | Thor re-check with the fixed preview |
| P5 popup modes | DONE for 15 more layouts x 6 styles | RP Classic 60/60, Thor 30/30 except D167 (fixed + verified on the Thor) |
| P6 Thor error report | DONE (D166 = QA artefact) | dropbox ANR, main thread idle; default Home = Voltcap |
| P7 KONKR / D137 / Case stacks | WAITING | KONKR on USB |
| P7 D124 AutoTDP | WAITING | owner approval |
| P7 Odin restore | WAITING | Odin on USB |
| P7 physical controller | PENDING | someone at the device |
| P7 Saturn / Dreamcast | STATIC ONLY | SaturnDreamcast454Test; no games on the devices |

## Update 453 (2 Nov 2026)
| Request | Status | Evidence / note |
|---|---|---|
| P1 More: touch | DONE (2 window types) | RP Classic Classic + Live tiles: tap More → all actions, Less, Back |
| P1 long titles / incomplete metadata | DONE | 2 games x 16 layouts, collapsed + More (RP Classic): wrap / slide, no clipped key |
| P1 LIVE / DASH rapid A B A B left open | DONE (D157) | cause: stale re-open request; RP Classic 108/108, Thor 56/56 closed after the fix |
| P1 bloated windows | DONE (D160) | Classic window fits its content (was always full height) |
| P1 physical controller | PENDING | needs someone at the device |
| P2 L2/R2 vs category screen | DONE (D158) | Thor: 20 stops = 20 tiles both ways; the pill bar listed a removed tile (Other apps) — fixed |
| P2 per-category layouts / stability after popups | PARTIAL | view state verified after every run; per-category layouts not exercised (owner settings) |
| P3 drawer focus / scrolling | DONE (D159) | opens on the first app; ▼ reaches the last row |
| P3 long app names | FIXED (D162) | whole name on focus; no long-name app on the devices |
| P3 PXPlay / Asobi only when opted in, Usage access, profiles | DONE in 450–452 | not repeated; All games tile picture = last used item (PXPlay is really used) — not a bug |
| P3 Continue playing look per layout | OPEN | not changed this update |
| P4 media per console (DS/3DS/PSP/PS1/PS3/N64/Switch) | DONE (no defect) | Thor Case stacks + Game shelf stills, settled and after ▶ |
| P4 Saturn / Dreamcast | OPEN | no Saturn / Dreamcast games on the two USB devices |
| P5 .trashed-* | DONE (408/409, re-checked) | no trash files on either device; folder-level trash (D163) unit tested |
| P5 Switch names with dump tags | DONE (D161) | Thor |
| P6 controller polish | PARTIAL | Pocket ribbon / Tiles / Channel grid one press = one move; drawer first press fixed |
| P6 Cross ◀▶ (D102) | CLOSED in 454 | preview artefact (D164 fixed) |
| P7 performance (Settings, R1, category switch, drawer) | MEASURED in 454 | see above |
| D137 Case stacks performance | DEFERRED | until the KONKR Pocket Advance is on USB |
| D124 AutoTDP real-game test | DEFERRED | owner approval needed |

## Older outstanding requests (COWORK_HANDOFF table; not re-done this update)
| Request | Status |
|---|---|
| Wii Channel preview parity | DONE in 408 / 443 (rated Excellent); rapid A/B on Channel grid PASS (453) |
| Case stacks 13 carousel arrangements | OPEN (visual pass not repeated; performance part deferred) |
| Monochrome / themed icon inconsistencies | DONE in 454 (audit, no defect) |
| Switch-style Tile home alignment | DONE (369/370 Thor); 453 still: title, row and dock aligned |
| Pocket ribbon navigation | DONE (453: one press = one move both ways, no wrap at the first game) |
| Popups x 53 layouts x R1 modes | PARTIAL (modes: Stacks 452 + 15 layouts 454; rapid: 21 layouts 453) |
| D64 (owner decision), D67 logo over dock, D61 render-bound layouts, D165 Settings window | OPEN |
| AutoTDP items D113–D119 | FIXED-UNVERIFIED (need real games) |
