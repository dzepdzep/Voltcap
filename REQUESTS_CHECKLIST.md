# VoltCap — requests checklist (kept current every update; newest status first)

Legend: DONE (verified on hardware unless noted) · FIXED (code + tests, hardware partial) · OPEN · DEFERRED (owner decision / device needed).
Release: v0.6.66 RC frozen and unpublished; all new work in `next/0.6.67/`. No publishing without the owner's approval.

## Update 464 (3 Nov 2026)
| Request | Status | Evidence / note |
|---|---|---|
| Real cartridge photos visually unchanged | DONE (KONKR) | Silver / Crystal / Gold, RC vs staging pixel diffs (REAL_SCANS_464.md) |
| Front art, proportions, light, colours preserved | DONE | Inspect front 0.000-0.76 % (edge resampling only); home 0° identical |
| Side and back not regressed | DONE | home 45 / 75 / 180° identical; 90° = the D175 fix (RC vanished edge-on) |
| User-selected real scans take priority | DONE | RealScans464Test: picture choice + PhysicalOverrides never consult PhotoShells |
| PhotoShells only replaces the drawn fallback | DONE | RealScans464Test: PhotoShells only in the drawn-shell path |
| Missing / failed template can't damage real path | DONE | RealScans464Test: photo ?: drawn, failures remembered, setting off |
| Inspect, animation, home layouts show authentic carts | DONE | Inspect (case › cartridge) + Pocket Quest home at 5 angles |
| Extend only after the checks | DONE | checks pass; 463 extension kept (it only touches drawn shells) |

## Update 463 (3 Nov 2026)
| Request | Status | Evidence / note |
|---|---|---|
| Extend PhotoShells to all real cartridge / card systems | FIXED (wired for 28 shells) | PHOTOSHELLS_463.md matrix |
| Authentic model + regional variants per platform | DONE | photo's own game → CartShells.authenticIdFor (console + photo region); shape ±9 % |
| Real photo from the library per platform | DONE where the library has one | GB/GBC/GBA, N64, SNES NA, SFC/PAL, Famicom, NES, DS, 3DS, Switch; none for Sega / Atari / NEC / SNK / Bandai / VB |
| No recolouring, no stretching another regional shell | DONE | no tint outside GB family; same-plastic photo only; region from the photo's game |
| Exact game label; hacks / homebrew own label; never base ROM's | DONE (unchanged pipeline) | label = what the drawn shell prints (461 D184) |
| Rotation, sides / back, performance | DONE (KONKR) | sheets qa463_sheet_a/b; 90° slab + drawn back approximate (same for real pieces) |
| Safe fallback | DONE | per-colour reasons in inventory.tsv; NES excluded (sticker geometry mismatch) |
| Custom models untouched | DONE | pack / custom path runs first |
| N64, SNES/SFC, NES/Famicom, DS/3DS, Sega first | N64 / SNES / SFC / Famicom / DS / 3DS / Switch DONE; NES OPEN (re-measure); Sega DEFERRED (no photos) | |
| Hardware test | KONKR only; non-local consoles via QA label stand-ins | no NES/SNES/DS/3DS/Switch/Sega games on the connected devices |

## Update 462b (3 Nov 2026)
| Request | Status | Evidence / note |
|---|---|---|
| Verify PhotoShells is used in staging (not the RC) | DONE (KONKR) | mediasrc trace + Pocket Quest shots; table MEDIA_SOURCES_462b.md |
| Record real scan / PhotoShells / procedural / custom model + fallback reason | DONE | trace reasons; only fallback = first-start analysis, now remembered |
| Same plastic, lettering, texture, ridges, recess, edges, shading; only sticker changes | DONE for GB / GBC / GBA fronts | Silver real vs PhotoShells identical |
| Rotation mismatch (drawn back / sides) | FIXED where it differed | sides + back now from the photo's plastic; 90° slab + drawn back approximate for real and made alike |
| Sheet: Silver real, Silver PhotoShells, Fool's Gold, Brown, Prism; front/45/75/90/back | DONE | qa462b_cart_sheet.png (Brown via session-only art override, not on devices) |
| Extend to N64 / SNES / NES / Sega / DS / 3DS / Switch | WAITING for your OK | not changed |

## Update 462 (3 Nov 2026)
| Request | Status | Evidence / note |
|---|---|---|
| Pocket Quest: Select opens Categories instead of Settings | FIXED + verified (KONKR, simulated input) | reproduced first (pq462_select.png); InputContract gate; contract462 POCKET Select=OK, B=themed |
| Audit all 53 layouts + document the global contract | DONE | tools/qa/INPUT_CONTRACT.md; 8 more layouts re-bound Select / Start (D188) |
| Select / L2 / R2 / R1 / L1 / A / B / rapid presses on hardware | DONE 53/53 (KONKR, adb input keyevent) | qa462 contract result; physical controller NOT tested |
| Layout consumes input only in its own context; precedence | DONE | Route LAYOUT / HOME / HOLD; owned: Pocket classic pages, PQ bumpers option, launch animations |
| Automated regression tests | DONE | InputContract462Test (every layout × every reserved key) |
| Themed category picker (PQ, Wii Channel, Cinema, Arctic; standard elsewhere) | IMPLEMENTED, partly verified | PQ / Green Core / Pocket classic own pages (hardware B=themed); Channel / Cinema / Arctic looks shown on KONKR, layout padding fixed after, final look NOT re-photographed |
| Picker: one data source, hidden / order, L2/R2 match, focus restore, no per-category layout write | DONE (code) | same tiles / stops; pick = applyCategory / L2-R2 steps |
| Real vs generated cartridges: photo-based shell templates (GB family) | IMPLEMENTED, verified on KONKR | PhotoShells from your library's own cartridge photos; qa462_cart_sheet.png |
| Silver real / Silver new / Fool's Gold / Prism on GBC / front-45-75-90-rear-Inspect | DONE except Brown (not on devices: mock) | differences left: drawn rear, flat 90° side, GBA hack label — NOT finished |
| No grey plastic side strips, full title, no fake codes | FIXED | printed borders take the art's edge colour |
| Other systems (GBA done; N64, NES, SNES, Sega, DS, 3DS, Switch) | OPEN | same mechanism, sticker places needed per shell |
| Performance | MEASURED | KONKR PSS 452-454 MB off vs 465-466 MB on; frames unchanged (first build was +330 MB: fixed) |
| Popup animation audit, D137, RP Classic Settings slide, label verification | OPEN | not worked on in 462 |

## Update 461 (3 Nov 2026)
| Request | Status | Evidence / note |
|---|---|---|
| D173 compile + test + KONKR | DONE | compiled, GameCopies460Test 5/5, d173_caseflow.png; whole path + size must match; picks still win |
| D137 cap preloading, compare RC | MEASURED, decoding ahead REMOVED in Case stacks | A/B same build: +25-35 MB, no gain; RC 598-615 MB vs final staging 607-636 MB (cold start); still render-bound — OPEN |
| Popup audit 53 layouts × 6 styles | DONE on hardware 318/318 (KONKR + Odin) | POPUP_AUDIT_461.md, popup_sheet_*.png; D183 fixed + re-checked; animations / More / Thor / RP NOT covered |
| Brown option D | IMPLEMENTED, WAITING APPROVAL | brown_optionD_mock.png (Brown not on devices), labels461_closeup.png (KONKR, same code) |
| Principle on other missing-label carts | GB / GBC done; GBA needs approval; others unchanged | labels461_konkr.png |
| Hacks never base game's label | FIXED (D184) | Crystal Kaizo own art now; Crystal Clear fan label kept |
| Other label audit | PARTLY | N64 / GBA / GBC real labels on KONKR; SNES / NES / Sega / DS / 3DS / Switch not on devices — NOT verified |
| RP Classic Settings slide | OPEN | RP not connected |

## Update 460b (3 Nov 2026)
| Request | Status | Evidence / note |
|---|---|---|
| Real-label-first for GB / GBC / GBA | APPROVED (owner), in staging | order: your label → matched scan → Voltcap/Labels → fallback |
| D173 Snap / Stadium real packaging | FIXED, HARDWARE KONKR | root cause: duplicate copies (internal + SD); scans saved under the SD copy only; GameCopies shares them |
| Other N64 games | CHECKED KONKR | 6 games had own scans and were fine; 11 games gained scans from their copy |
| Brown label | WAITING OWNER | no scan; options A/B/C (brown_fallback_options.png) |
| Other systems' label audit | PARTLY | N64 real labels OK (2 games); SNES / SFC / NES / DS / 3DS / Switch / Vita / Sega / CustomCarts NOT verified |
| D137 Case stacks | MEASURED, NOT FIXED | staging 39/34/29 % vs RC 51/28/25 % janky; staging +90-110 MB PSS (decode ahead) — to cap |
| Popup audit, all layouts × R1 modes | OPEN | not done; matrix still to build |
| Settings slide RP | OPEN | RP not connected |

## Update 460 (3 Nov 2026)
| Request | Status | Evidence / note |
|---|---|---|
| Real label first, existing custom label second | DONE (staging), HARDWARE KONKR | ScreenScraper label scans = real stickers + fan labels for hacks; 459 designs off; qa460/evidence sheet |
| 459 generated designs removed as default | DONE | `GeneratedLabels.enabled` false (QA hook only); no FAN-MADE print |
| Fan-made label collections (julius1504, Ddaretrogamer, vgmoose) | NOT BUNDLED | no licence / permission found; Labels folder lets you add your own |
| Labels folder (your own / fan labels) | DONE (code + tests) | `Voltcap/Labels`, name-matched; not tried on hardware with a file |
| Dimension references (ConsoleMods, Dinierto CC0) | RESEARCHED, NOT APPLIED | shell geometry unchanged; GB 42×37 vs CartShells 42.6×38.5 etc. to review |
| Brown proposal | WAITING OWNER | no label scan; not on the KONKR |
| Comparisons N64 / SNES / SFC / DS and other consoles | NOT DONE | waits for GB/GBC/GBA approval |
| Cube layout jumps with name length | FIXED (staging), HARDWARE Odin | names keep 2 lines' height; FullText reserves its lines everywhere |
| Blocks before artwork in case layouts (KONKR) | FIXED (staging), KONKR sampled OK | cases decoded ahead; not reproduced on the RC in this session |
| KONKR crash (Cube hub tabs) | FIXED (staging) | crash log IndexOutOfBounds 6/6; RC 0.6.66 has it |
| D173 Snap / Stadium packaging | NARROWED | real scans present, case shows another front + plain spine; needs a log build |
| D175 GBA sides 45-180 | OK on KONKR | Thor / RP not connected |
| Settings slide RP / controller L1-R1 | NOT TESTED | RP not connected |
| D137 Case stacks (KONKR) | NOT DONE | time; KONKR now connected |

## Update 459 (2 Nov 2026)
| Request | Status | Evidence / note |
|---|---|---|
| Reuse the existing shells, only the sticker generated | DONE (staging) | 458 grain / light / crop removed; real scans and user labels never replaced |
| Game Boy (gb / gbc / gbc_dual), Pokémon Brown | DONE (staging), HARDWARE-VERIFIED RP Classic | whole cover, BROWN VERSION readable, grey printed strips, FAN-MADE, moulded lettering on all three shells |
| Moulded branding not tied to Cartridge backs = Neutral | DONE, unit-tested + RP | gbTopText unconditional |
| GBA labels | DONE (staging), HARDWARE Thor + RP | dark printed panel + title + real AGB code; no DS frame; D175 kept |
| N64 label | DONE (staging), HARDWARE Thor | full-width sticker, title + ID, PAL for Europe |
| SNES / SFC | DONE (staging), HARDWARE Thor + RP (SFC) | header band by region; title panel beside portrait art; US SNES not captured on the RP |
| NES / Famicom / VB / Atari / Sega / cards | CODE + UNIT TESTS ONLY | not seen on hardware |
| DS / 3DS / Switch / PS Vita | PARTLY | DS seen (Thor); 3DS / Switch / Vita not captured; SwitchLabel unchanged |
| CustomCarts 14 shapes audit | NOT DONE | |
| Real codes only, FAN-MADE / HOMEBREW | DONE, unit-tested + hardware (DMG-AAXE-USA, CGB-BYTE-USA, AGB codes, FAN-MADE) | invented DS productCode removed; no rating / licence marks drawn |
| Comparison sheets | DONE | qa459/evidence/labels_459.png ("real" column = VoltCap's real box scan; no external photos) |
| RP Classic Settings category slide | FIXED (staging), PARTLY VERIFIED | directional, no blank frame, chips stay (RP touch recording); RP ~3-4 steps per slide (31 % janky); Thor smooth (9 %); controller L1/R1 not recorded on the RP |
| D175 performance | VERIFIED Thor | staging 0.98 / 1.21 % after warm-up vs RC ~1.6 % |
| D172 / D173 / D137 / D124 | KEPT / DEFERRED | unchanged |

## Update 458 (2 Nov 2026)
| Request | Status | Evidence / note |
|---|---|---|
| D175 GBA side lines (front, 45, 75, 90, rear) | FIXED (staging), HARDWARE-VERIFIED Thor | vanish at 90° fixed; saw-tooth cut to the side's own edge; Inspect stripe gone; GB/GBC/N64/3DS checked; ~1 px fine tooth left at ~75°; Switch + RP Classic not tested |
| D175 movement | VERIFIED Thor | turn 0→180 (22 steps) + L/R scroll recordings |
| D175 performance | NOT PROVEN | Thor GBA row staging 4.3 % janky (1 run) vs RC ~1.6 % (3 runs); RP same (14.2 vs 13.9 %) — re-measure staging |
| D174 Pokémon Brown generated pak | IMPROVED (staging), HARDWARE-VERIFIED RP Classic, owner look pending | full sticker, moulded "Nintendo GAME BOY", grain, softer light; box-foot text (BROWN VERSION, rating) cut off by the fill |
| D172 Settings frame drops (RP Classic) | OPEN | 12-14 % janky; one slow UI frame per press + GPU; plain panels don't change it |
| D170 Pocket Quest long names | DONE (456) | verified RP Classic + Thor |
| D173 / D137 (KONKR), D124 (approval) | DEFERRED | unchanged |

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
