# Popup audit — update 469 (release stabilization), staging v0.6.67

Builds on POPUP_AUDIT_467.md (method unchanged: `device/popclip.sh` + `tools/popclip.py`, six categories, R26). New this
update: `tools/popmerge.py` (latest run per combination wins), popclip answers the profile picker, enters Green Core's games
from its menu, taps "Details" itself before the other page words, stops if Voltcap is not in front after the first A.

## Results (latest run per combination, final staging build)
| device | config | combos | PASS | FAIL | NOT TESTED | SKIP (n/a) |
|---|---|---|---|---|---|---|
| KONKR GT78 960×640 | font 1.0, 53 × 6 | 318 | 287 | 5 | 2 | 24 |
| Odin 2 Portal 1920×1080 | font 1.0, 53 × 6 | 318 | 288 | 4 | 1 | 25 |
| KONKR | font 1.3, 15 layouts Own | 15 | 14 | 0 | 1 | 0 |
| Odin | font 1.3, 15 layouts Own | 15 | 14 | 0 | 1 | 0 |
| KONKR | display size smaller (200 dpi), 10 layouts Own | 10 | 9 | 0 | 1 | 0 |
| KONKR | display size larger (280 dpi), 10 layouts Own | 10 | 9 | 0 | 1 | 0 |
| Odin | display size larger (420 dpi), 10 layouts Own | 10 | 9 | 0 | 1 | 0 |
467 for comparison: KONKR 266 / 24 / 4 NT / 1 INFRA / 23; Odin 270 / 21 / 2 / 0 / 25; KONKR 1.3: 15 / 15.
Runs: KONKR k469g (full) → k469h / k469j / k469m / k469n; Odin o469g (full) → o469h / o469i / o469k / o469m / o469n;
font k469fs…fs6, o469fs2…fs5; density k469d1…d5, o469d1…d3. Re-runs on the build that has each fix.

**Remaining FAILs = D212 (owner decision, unchanged):** Arcade (rows 17 dp, PRESS START bar), Memory card (status 14 dp),
Night (slanted keys' boxes cross), Magazine (3–6 px), Orbit (KONKR only, round keys' boxes).
**NOT TESTED:** Box · Own "Play tap not recorded" (D222: its actions sit in the scrolling note card; a direct Play tap
blocked the launch 1/1 by hand); Channels / Classic Banner page "no window action buttons to compare" (pixel check n/a).
**Long / missing details: NOT TESTED** — the run (k469L) asked for "One Piece - Yume no Luffy" but the test hook matched
no game by that spelling and stayed on Pokémon Stadium 2; it is a font-1.0 repeat, not the long-name check.

## 467 popup bugs
| id | finding | status |
|---|---|---|
| D210 | first A after start ignored | **test path, not the app:** the cold start's "Who's playing?" took the A; popclip answers it. Manual: A picks, next A opens |
| D211 | Twin Play tap does nothing (KONKR) | **fixed (D221):** the facts box paged every 2.8 s and changed height; the keys moved under the finger, the tap hit the veil and closed the window |
| D213 | Arctic Back cut at 1.3 | **fixed:** two columns where wide enough, else a denser single column (also compact drawers, Odin 420 dpi) |
| D214 | Rental shop BACK cut at 1.3 | **fixed:** short layout at 1.3 up to 480 dp (Odin 445 dp still cut it) |
| D215 | Cover details overlap / ghost title | **fixed:** panel opaque; name gives way to the keys at display size larger |
| Storage Details tab | inconsistent | **test path:** "Info" was in the Details word list and matched the INFO tab (already open). Tab taps work 7/7 by hand (KONKR 4, Odin 3) |
| Card binder / Pocket | NOT TESTED (A plays) | **test path fixed:** their A-plays options off for the sweep (restored by the snapshot) |

## Found and fixed in this update
D220 Storage window crashed on A (469's own FitText under FlowRow intrinsics — staging only) · D221 Twin facts box height ·
D223 Pocket Quest keys cut, Blades Back, Cover details keys, Discover status line, Arctic labels in the narrow drawer
(font 1.3 / display size) · Storage ACHIEVEMENTS broke mid-word (one line, auto-size) · Bubbles page "overlap" was the
LiveArea under its full-screen details sheet (gate false positive, fixed in popclip.py) · D224 home GB pak layers (CARTS_469).

## Hardware, settings
USB only (KONKR BW0306N230000845, Odin b92036fb). Font / density changes made by `popclip_cfg.sh` and put back (logged
"font was 1.0 / restored 1.0", "density restored"). Screen-off timeout: KONKR 1 800 000 for the runs, back to **60 000 — this
is the value Update 468 left, not a recorded original** (468 changed it without reading it first; the owner's own value is
unknown). Odin 1 800 000 unchanged. `voltcap_test_snapshot` restored on both; layout / view put back by hand on the KONKR
(Case flow, Nintendo 64, Pokémon Snap) because a font run's app restart dropped the preview flag and the next layout was
saved (FILESELECT). RC 0.6.66 reinstalled on both; test files removed (charge_log.csv, voltcap_qa_screen, Odin `upd` kept).
Performance, KONKR Game Boy Color row (24 D-pad steps): staging 1.65 % janky / p95 22 ms (no like-for-like RC number:
the RC lacks the look / game hooks).
