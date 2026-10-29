# VoltCap status matrix — update 404 (29 Oct 2026)

DONE VERIFIED = seen on the Thor (3140972f) with evidence; DONE UNVERIFIED = code / tests only; PARTIAL; OPEN; DEFERRED. Evidence is local (`.shots/404/`, not committed) unless a file is named.

## A. Category-selection looks (facts from the source audit, update 404)
Wired in `ui/PlatformBrowser.kt` lines ~410-419 (`Features.PlatformView`), drawn in `ui/CategoryLooks.kt`. Updates 350-352 built and polished them; later "missing" notes were wrong.
| Look | Code | Status |
|---|---|---|
| System Stage (`StageLook`) | CategoryLooks.kt `// 1.` | IMPLEMENTED (not re-seen on hardware in 404) |
| System Blades (`BladesLook`) | `// 2.` | IMPLEMENTED |
| Collector's Drawers (`DrawersLook`) | `// 3.` | IMPLEMENTED |
| Pocket Worlds (`WorldsLook`) | `// 4.` | IMPLEMENTED |
| Archive 1994 (`ArchiveLook`) | `// 5.` | IMPLEMENTED |
| Channel Surf (`ChannelLook`) | `// 6.` | IMPLEMENTED |
| Generations (`GenerationsLook`) | `// 7.` | IMPLEMENTED |
| Constellation (`ConstellationLook`) | `// 8.` | IMPLEMENTED |
| System Vault (`VaultLook`) | `// 9.` | IMPLEMENTED |
| Pocket Dashboard (`DashboardLook`) | `// 10.` | IMPLEMENTED |
| System Grid, Side Banner, Retro Timeline, Compact List | no class / enum / string in `src_copy` | ABSENT under these names. Generations is a timeline by release year and the category screen has a grid, but they were never built or verified as these designs: OPEN (only if the owner still wants them; do not duplicate the ten above) |

Hardware status of the ten: no QA row in coverage.json for them (`tools/qa/device/looks.sh`, `catlook.sh` exist). All ten are "IMPLEMENTED, NOT VERIFIED in 404".

## B. Physical media / transitions
| Item | Status | Evidence / files |
|---|---|---|
| D95 Cover details: case comes out of its slot, old case returns | DONE VERIFIED (Thor, release 0.6.58) | `ui/StyleCoverDetails.kt` `CoverFlight` / `CoverBigCases`; `.shots/404/sheet3.jpg`, `sheet4.jpg`; `CoverFlight404Test` |
| D95 Game shelf | DONE (existing three-phase pull-out from the slot, updates 102 / 228), re-traced in code, not re-captured | `ShelfHome.kt` `pull` / `ShelfBack` |
| D95 Case stacks | N/A: no separate featured case (the selection is the carousel) | `StyleCaseStacks.kt` |
| Box / Crate / Binder / Console stack A-pickup flight | DONE (402 / 400), not re-captured in 404 | `GameWindow.kt`, `MediaInspect` |
| Idle wait (<=450 ms) before the pickup flight | OPEN / NOT MEASURED | |
| D86 rapid reversal | PARTIAL: Cover details focus changes R / L / R at 120 ms on the final APK = one case per frame; the literal `D A B A B A B` (A on the popup = Play) was not sent | sheet4.jpg |
| Lite / animations off / touch drag on the Cover details flight | NOT VERIFIED (code gates it: `rememberAnimationsEnabled`, `LocalLiteEffects`) | |
| Cardboard box Rows Auto / 1 / 3 | DONE VERIFIED for ALL (106 / 53 boxes) and GBC (8 games): 1 row ok, 3 rows ok on GBC, honest note + 2 rows on ALL | `.shots/404/boxgrid.jpg` |
| Box 1-3 games, empty, DS / 3DS / NES / N64 specific | NOT VERIFIED | |
| Box slabs / hollow sides (D92, D88, D89) | DONE VERIFIED (403); unchanged | |
| Cover details vs reference 07 (true back, stronger perspective) | PARTIAL (D94) | |

## C. Artwork / media mapping
| Item | Status |
|---|---|
| PS2 Yu-Gi-Oh doubled band (D91) | OPEN. The source picture 343x480 carries it (402); no legitimate replacement scan established; the per-game cover override path exists. No art was altered. |
| N64 grey sidebars (D90) | PARTIAL. On the Thor the 007 box front shows a grey left panel and a red right strip with the ESRB "E" and N64 logo: that matches the real US box design, so it reads as authentic artwork, not a renderer fit bar. The raw scan file was NOT extracted (saved_art is hash-named and the app holds ~19 GB; a tar pull timed out: INFRA). NOT VERIFIED against the raw file. |
| NES / SNES / PS1-3 / PSP / DS / 3DS / GBA / GBC / Switch fit audit, Switch cartridge mapping, N64 back / spine | NOT DONE |

## D. Backlog kept (unchanged, see Update403_Backlog.md)
Artwork library Endless / 1-3 games / touch / rail; Pocket Quest overlap / fades / FPS; Disc Station labels; Cinema / Arctic; Dashboard / TV Room; TWIN_ICONS / TWIN_SHOWCASE / HERO_TILES / dual screen; L2 / R2 vs category picker (not reproduced; hidden categories stay hidden); R1 menu / D-pad audit; ~36 layouts category-switch QA; Pocket Ribbon long lists; D96 (new, suspect): a long Cover details title looked cut at the right edge in rapid-change frames, settled state not inspected.
