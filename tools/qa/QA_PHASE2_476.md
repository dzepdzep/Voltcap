# VoltCap QA overhaul — Phase 2, update 476: review findings, volumes 3–9, ranked defects, fixes with before / after

Staging only (0.6.68, `next/0.6.68/staging.patch`); v0.6.67, its assets, the tag and main's app untouched; nothing published.
Builds (versionCode 73, never published; `builds.json`): **qa-0.6.68-476a** b95eceab…, **476b** 3a609b6f…, **476c** 19690613…
(476c = what this report's "after" shows; base main 59b45fc + patch 90007cf8…, tree 146d5d79…).
Devices (USB only): KONKR GT78 BW0306N230000845, Odin 2 Portal b92036fb. Thor / RP Classic NOT TESTED. Both back on the 0.6.66
RC (f79aa2a7); the owner's settings snapshot, saved by the RC, is byte-identical before / after on both (4 files, md5). No game
launched, nothing uninstalled, no data cleared, no ROM / artwork / background changed.

## 1. Ranked defect report (objective defects first, then recommendations)
Kind: **defect** = objective, confirmed on the screenshot or the layout dump; **recommendation** = subjective, owner decides.
| rank | id | sev | where | finding | kind | status |
|---|---|---|---|---|---|---|
| 1 | D253 | high | Cross Details (KONKR) + the same page in Stacks / Box / Discs / Pocket / TV cards | text column 61 dp, words split, 114 lines | defect | **fixed 476a**, verified k476d / o476d: text keeps ≥ 200 dp, the cover shrinks or goes above; nothing removed |
| 2 | D254 | high | Hero home (KONKR) | info text straight on the hero picture (screenshot HUD text, cover small print) | defect (readability) | **fixed 476b** (476a drew the plate only over layered panes — hardware showed it missing): soft plate behind the text; carousel unchanged. Cover-column names measured: no overlap with any cover in k475r1 / k476d |
| 3 | D250 | high | Blades | window opened another game (475a, both devices) | defect | open — **not reproduced** in 476a / 476c full sweeps (PASS both devices); intermittent, cause not found |
| 4 | D266 | medium | Airy window (KONKR) | the chips' 2nd line (Played, ★ 0 / 109) not drawn: two facts missing | defect | open — needs room: the round keys wrap to 2 rows (D247); **owner decision** (key gap / chip size) |
| 5 | D256 | medium | Spotlight / Dash / Side / banner / console scenes | window-wide picture decoded at the cards' size (Spot: 360 px KONKR, 512 px Odin) and drawn 2.7–3.8× upscaled | defect (decode size) | **fixed 476a** (7 places use the screen-size request). Hero's Odin background is source-limited (file 555×312): not fixable in code |
| 6 | D258 | medium | Bar dock | status keys wrapped to 3–4 lines | defect | **fixed 476a**: first part under the icon, whole text on one reserved line while focused |
| 7 | D255 | medium | Dash window | story cut silently after 3 lines; name over the art's own logo | defect (continuity) | **fixed 476a**: "Full story on Details ›", name under the art strip |
| 8 | D262 | medium | Storage Details page | header half on the blue band, "Details" 1.1 : 1 | defect (contrast) | **fixed 476b** (476a lost the flag in the game-colour tint — hardware still 1.1 : 1): plain paper, readcheck clean |
| 9 | D259 | low | TV Details page (and every window button row) | Play / Full details / Back in three sizes | defect | **fixed 476c** (476a/b read auto-size's asked size back — hardware still three sizes): each button measures, the row shares the smallest |
| 10 | D260 | low | Classic + every scene using the facts box | story fades with no cue | defect (continuity) | **fixed 476a**: cue at the box edge while it can scroll |
| 11 | D261 | low | Spotlight | no name when a logo address fails to load (no-logo case already had the name) | defect | **fixed 476a** — NOT TESTED on hardware (a failing logo can't be made without touching artwork) |
| 12 | D264 | low | Arcade window | wrapped status lines touch | defect (overlap) | **fixed 476a**, verified both devices |
| 13 | D252 | medium | all (touch then D-pad) | first D-pad press after a touch is lost | defect | open — owner decision (475) |
| 14 | D257 | — | Tiles | Play contrast, More set apart | recommendation | not changed (light design) — **owner decision** |
| 15 | D265 | — | Twin desk / icons / showcase | story box pages by itself (D221); a last page of one line | recommendation | page counter would show more follows — owner decision |
| 16 | D263 | — | Cover details home (Odin) | rolling story shows cut lines mid-roll | recommendation | deliberate roll; line-step rolling — owner decision |
Further review notes per layout (contrast, busy backgrounds, cramped columns) are in the volumes, marked recommendation / check.

## 2. Volumes 3–9 (41 layouts) and 1–2 re-issued
`VoltCap_visual_QA_476_vol1…9.pdf`: all 53 layouts × 2 devices, full-resolution screenshots (build 475a), graded results
incl. the new readability category, numbered annotations, review notes with the ids above. Empty cells now say why:
"not offered" (the layout's window has no such key — read from its dump), "NOT CAPTURED" (key exists, evidence missing / tool
couldn't reach it) or "NOT TESTED" (no run). Before / after: `VoltCap_before_after_476.pdf` (18 pairs, both builds' evidence lines).

## 3. Hardware runs (popup gate, OWN style)
| run | build | layouts | KONKR | Odin |
|---|---|---|---|---|
| k476a / o476a | 476a | 53 | 52 PASS, CLASSIC open FAIL = harness (profile picker after install, D249); PASS in k476b | same; PASS in o476b |
| k476c / o476c | 476b | 16 (affected) | 15 PASS + CLASSIC (same harness note) | same |
| k476d / o476d | 476c | 53 | 52 PASS, TV open FAIL once (first A not seen by the probe; the window did open — harness timing); PASS in k476e | **53 / 53 PASS** |
Readability (new, WARN-level heuristics): 0 FAIL on all runs. lowres WARNs on the Odin rose 5 → 11 because pictures are now
*asked* for at screen size — the small sources (e.g. 555×312, 920×430) that were always drawn upscaled now show in the record.

## 4. QA improvements (asked 1–6)
1. **Readability / continuity apart from geometry** — `tools/readcheck.py`, popclip category `readability`: text over text
   (FAIL when the dump proves it; sliding / auto-sized text = WARN), contrast on the screenshot (WARN under 3 : 1, 4.5 : 1 on
   a busy picture), only the window's own texts (home texts under the veil and the status bar excluded); LCD ghost digits,
   watermark words and turned (fanned) texts are not graded. Found D266 (Airy's missing chips) the geometry gate missed.
2. **Low-res pictures** — QaArt want / got with the drawing scale (FIT tighter side, FILL looser): WARN above 2× upscale.
   Severe overlaps and busy-background contrast as in 1.
3. **NOT CAPTURED** — visualpdf `missing_reason`: not offered / NOT CAPTURED (why) / NOT TESTED.
4. **Subjective vs confirmed** — finding kinds in the notes and defects.json (`kind`); heuristics only WARN.
5. **Few games** — sweepcov: a T5 PASS on a list of fewer than 3 games is printed "PASS · N game(s): no scroll claim".
6. **Recordings** — existing `device/vqa.sh rec` + `tools/frames.py` (transient / flash / hitch frames) is the movement
   evidence; it was **not run** in this update (all changes here are static layout / text); T5 stays screenshot-based.
Self-tests: 127 (6 new). Static checks strict PASS. App unit tests (`testDebugUnitTest`) SUCCESS on the 476c tree, incl.
`Readability476Test` (15 tests; the ones added after the hardware runs — Storage paper, hero plate, measured button size, every button row — fail on the code those runs tested).

## 5. Not done / limitations (honest)
- Staging CI still not active (`next/0.6.68/staging-ci.yml` must be copied to `.github/workflows/staging.yml` by the owner).
- T1 other styles (KONKR), T2, T3, T4, T6: NOT TESTED. T5 not re-run on 476 (no navigation code changed).
- Volumes' screenshots are build 475a; only the 18 before / after pairs show 476c.
- D261 not exercised on hardware; D250 not explained; D266 / D257 / D265 / D263 / D252 wait for the owner.
- Readability contrast is a pixel heuristic: most layouts carry a WARN (status-bar text on busy pictures) — a review aid, not a gate.
