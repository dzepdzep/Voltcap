# VoltCap — requests checklist (kept current every update; newest status first)

Legend: DONE (verified on hardware unless noted) · FIXED (code + tests, hardware partial) · OPEN · DEFERRED (owner decision / device needed).
Release: v0.6.66 RC frozen and unpublished; all new work in `next/0.6.67/`. No publishing without the owner's approval.

## Update 453 (2 Nov 2026)
| Request | Status | Evidence / note |
|---|---|---|
| P1 More: touch | DONE (2 window types) | RP Classic Classic + Live tiles: tap More → all actions, Less, Back |
| P1 long titles / incomplete metadata | DONE | 2 games x 16 layouts, collapsed + More (RP Classic): wrap / slide, no clipped key |
| P1 LIVE / DASH rapid A B A B left open | DONE (D157) | cause: stale re-open request; RP Classic 108/108, Thor 56/56 closed after the fix |
| P1 bloated windows | DONE (D160) | Classic window fits its content (was always full height) |
| P1 physical controller | OPEN | needs someone at the device |
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
| P6 Cross ◀▶ (D102) | OPEN (likely QA artefact) | preview lost on category switch (D164) |
| P7 performance (Settings, R1, category switch, drawer) | OPEN | not measured this update |
| D137 Case stacks performance | DEFERRED | until the KONKR Pocket Advance is on USB |
| D124 AutoTDP real-game test | DEFERRED | owner approval needed |

## Older outstanding requests (COWORK_HANDOFF table; not re-done this update)
| Request | Status |
|---|---|
| Wii Channel preview parity | DONE in 408 / 443 (rated Excellent); rapid A/B on Channel grid PASS (453) |
| Case stacks 13 carousel arrangements | OPEN (visual pass not repeated; performance part deferred) |
| Monochrome / themed icon inconsistencies | OPEN (legacy / monochrome icons beyond 371's list not tested) |
| Switch-style Tile home alignment | DONE (369/370 Thor); 453 still: title, row and dock aligned |
| Pocket ribbon navigation | DONE (453: one press = one move both ways, no wrap at the first game) |
| Popups x 53 layouts x R1 modes | PARTIAL (451/452 modes on Stacks; 453 rapid on 21 layouts) |
| D64 RP Classic idle GPU, D67 logo over dock, D83 truncations, D61 render-bound layouts | OPEN |
| AutoTDP items D113–D119 | FIXED-UNVERIFIED (need real games) |
