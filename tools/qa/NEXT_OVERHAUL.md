# Next major QA / design overhaul — collected requests (not release blockers for v0.6.67)

Recorded at the v0.6.67 release gate (update 472, 4 Nov 2026). Nothing here was implemented in 471 / 472.

## Visual observations from the 471 review (owner)
| # | Where | Observation | Note |
|---|---|---|---|
| V1 | Row opens (popup style) | The Pokémon Stadium 2 artwork is cropped substantially (cover filled into a wide box) | KONKR k471r / k471rf; image fitting, see R2 |
| V2 | Case flow window | Short bars / shadows under the bottom buttons (Play, Details, Back, …) not explained by the design | k471a CASEFLOW_OWN_main |
| V3 | Cardboard box, display size larger | An isolated round control (Manual) on its own row, and a very small summary area | k471dd BOX_OWN_main (280 dpi) |
| V4 | Box Details page | An achievement count can wrap between its numbers ("0 /" · "96") | k471L1 BOX_OWN_page |
| V5 | Bubbles details | Title and thumbnail could be integrated better (wide gap from the logo slot to the name) | k471a BUBBLES_OWN_page |
| V6 | Row opens text (from 471) | Story preview ends with "…" (rule 11); on the KONKR the developer / genre facts give way | full text in Details |
| V7 | Arcade / Memory card | Touch areas 24 dp, not Android's 48 dp (rows 25–27 dp apart) | documented limit |
| V8 | QA gate | Text cut inside its own box / squeezed to zero height is not detected (D244 found by eye); Channel · Banner home/closed steps have no buttons, so its pixel category reads NOT TESTED | tool work |

## Carried-forward requests (owner, earlier updates)
| # | Request |
|---|---|
| R1 | Category-logo fixes |
| R2 | Image fitting (art cropped / zoomed in windows and rows) |
| R3 | Row stability |
| R4 | Dual-screen lifecycle and widgets |
| R5 | Independent brightness (per screen) |
| R6 | Performance-reading accuracy |
| R7 | GameNative `.steam` importing into PC / Steam |
| R8 | Per-layout colour customisation |
