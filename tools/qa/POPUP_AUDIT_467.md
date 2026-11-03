# Popup geometry audit — update 467 (QA rule R26), staging v0.6.67

Owner request (3 Nov 2026): "I don't want to discover clipped controls manually anymore." Clipped Play / Details / Back /
More in the Pokémon Stadium 2 popup (Case flow, KONKR) fixed; every popup now goes through an automatic gate on hardware.

## How it is checked
- **App side (`ui/QaClip.kt`, test hooks only):** every control and text node from Compose semantics — its own box (`full`)
  and the part its ancestors let show (`shown`), focus (Compose focus or the focus ring's QA mark), inside a scroll / paging
  box (`scroll`), tilted scene (`tilt`), the window's focus, screen, density, font scale, `hasRow`. Asked through a file
  (`<files>/qa_clip_req`) — an intent closes the popups that live inside the home screen.
- **Device side (`device/popclip.sh`):** per layout × R1 popup style: home step → **A** (controller) → main → **tap** Details
  (touch) → page → B → **tap** More → B → **tap** Play (launch blocked by `qa_nolaunch`, counted from the log) → **B** closes
  (probe before each B, so B is never pressed once too often). Screenshot + dump at every settled step. `popclip_cfg.sh` runs
  it under another font scale / density and restores it.
- **Gate (`tools/popclip.py` + `tools/geomcheck.py --clip`):** six separate categories — open · geometry · visual · focus ·
  buttons · settle. PASS only when every applicable one passes; missing evidence = INFRA / NOT TESTED; a row style the
  layout doesn't offer = SKIP (n/a). Geometry: nothing cut by its parents (an action partly cut inside a scroll counts), on
  screen, no overlapping controls, touch ≥ 24 dp (under 32 dp noted), focused control whole, no scroll box squeezed below
  one row. Visual: each action button's drawn edge in the screenshot (heuristic — layout bounds can't see every drawing
  clip; this never claims full visual coverage). Settle: settled stills only — frame-by-frame animation is the flash
  checker's job (R1), **not covered here**.

## Results (latest run per combination)
| device | config | combos | PASS | FAIL | NOT TESTED | INFRA | SKIP (n/a) |
|---|---|---|---|---|---|---|---|
| KONKR GT78 960×640 | font 1.0, all 53 × 6 | 318 | 266 | 24 | 4 | 1 | 23 |
| Odin 2 Portal 1920×1080 | font 1.0, all 53 × 6 | 318 | 270 | 21 | 2 | 0 | 25 |
| KONKR | font 1.3, 10 layouts × 3 styles | 30 | 15 | 15 | 0 | 0 | 0 |

(KONKR: full run k467f + reruns k467r / k467t after fixes; Odin: o467f + o467r / o467t / o467z. Font 1.3 run k467fs was
before its fixes — the re-check k467fz is in SUMMARY. Of the remaining FAILs, 15 KONKR / 11 Odin are Card binder and Pocket
layouts where **A plays from the home screen's own key** (owner's "A plays" / Pocket's Play Game key) — the popup is not
opened by A there, so it is effectively NOT TESTED; the gate now reports that case as NOT TESTED when it can see the key.)

## Defects found and fixed (D197–D209, tools/qa/defects.json)
| id | layout · style · resolution | cause | fix | verified |
|---|---|---|---|---|
| D197 | Case flow · Own · KONKR | whole panel one scroll, actions inside it | actions outside the scroll | k467f / o467f PASS, before/after sheet |
| D198 | all · Row styles most · both | face button after a touch reached nothing focused | gamepad button leaves touch mode first | manual + sweep |
| D199 | Classic window · all | buttons bare tap areas | click semantics; focus ring QA mark | k467f PASS |
| D200 | Resume · Own · KONKR | square as tall as the card | square leaves ≥ 290 dp; facts box gives way under one row | k467r PASS |
| D201 | Arctic · Own · both | theme's 24 sp line → 36 dp rows, Back cut | own 18 sp line, 6 dp padding | k467t / o467z PASS |
| D202 | Blades · Own · KONKR | 8 rows past the blade | shorter rows under 440 dp | k467r PASS |
| D203 | Panels · Own · KONKR | Back's last 3 dp | short rows 30 dp | k467r PASS |
| D204 | Storage · Own (beside the case) · KONKR | tabs / menu too wide | narrow tabs, 2 items a row | k467r PASS |
| D205 | Terminal · Own · both | rows 20 dp, status 13 dp | 24 dp | k467t / o467z PASS |
| D206 | Rental shop · Own · KONKR | BACK under the tag's foot | shorter rows under 440 dp | o467z PASS; KONKR 1.3 in k467fz |
| D207 | Twin × 3 · Own | touch on Play taken for the A release | only after a key press | Odin PASS; KONKR still D211 |
| D208 | Bubbles LiveArea | bare tap areas | click semantics | — (LiveArea is the layout's own page) |
| D209 | Box / Cover details · font 1.3 · KONKR | Inspect hint off screen | slides back inside | k467fz |

## Open (not fixed)
- **D210** first A after Voltcap starts does nothing (fresh process) — both devices.
- **D211** Twin windows on the KONKR: a tap on Play does nothing (Odin fine) — cause not found.
- **Storage · Own:** the sweep's tap on the DETAILS tab didn't change the page; a manual tap did — NOT confirmed either way.
- **Font 1.3 (KONKR, re-check k467fz after the fixes):** Resume, Terminal, Box PASS; still open **D213** Arctic Back cut,
  **D214** rental tag BACK cut, **D215** Cover details More overlap; the Banner page's GAME INFO tab flagged there is a gate
  false positive (whole-chip row, "+2 ›" — fixed in the gate after the run, not re-run).
- **D212 owner decision:** Arcade rows 17 dp + PRESS START bar (pixel heuristic), Memory card status 14 dp, Night's
  slanted keys and Orbit's round keys cross boxes, Magazine 3–6 px.

## Not tested
Smaller density (wm density) — NOT TESTED; Odin at font 1.3 — NOT TESTED; content variations — one game per device
(Stadium 2, "WWE SmackDown Vs. Raw 2011" — longer title, 109 / 0 achievements), no long-description / no-metadata games;
frame-by-frame animation; the BANNER page's own secondary tabs beyond Game info.
