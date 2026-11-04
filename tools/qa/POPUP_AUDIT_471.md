# Popup audit — update 471 (small stabilization pass), staging v0.6.67

Method as 470 (`device/popclip.sh` + `tools/popclip.py`, six categories, R26; `tools/popmerge.py` = latest run per
combination). USB only: KONKR GT78 BW0306N230000845 (960×640, 240 dpi), Odin 2 Portal b92036fb (1920×1080, 369 dpi).
Launches blocked (qa_nolaunch). Builds A → G; every layout touched after A was re-run on its final code (Box and Row opens on E,
row styles of 7 layouts on F, Cover details on G).

## 85 vs 318 (item 5)
470's "85 combinations" per device was **a subset**: 53 layouts with the Own popup + 8 chosen layouts × the 4 other styles
(Banner, Classic, Shared, Row reveal). Row opens was not swept and 45 layouts never had Banner / Classic / Shared / Row reveal
on the final code. The earlier 53 × 6 = 318 (469) is the whole matrix. **471 re-ran the whole matrix**: 53 layouts × 6 styles
(Own, Banner, Classic, Shared, Row reveal, Row opens) on both devices.

## Results (latest run per combination, final code)
| device | config | combos | PASS | FAIL | NOT TESTED | n/a |
|---|---|---|---|---|---|---|
| KONKR | font 1.0 — 53 layouts × 6 styles | 318 | 296 | 0 | 1 | 21 |
| Odin | same | 318 | 297 | 0 | 0 | 21 |
| KONKR | font 1.3, 15 layouts Own | 15 | 15 | 0 | 0 | 0 |
| Odin | font 1.3, 15 layouts Own | 15 | 15 | 0 | 0 | 0 |
| KONKR | display size larger (280 dpi), 15 layouts Own | 15 | 15 | 0 | 0 | 0 |
| Odin | display size larger (420 dpi), 15 layouts Own | 15 | 15 | 0 | 0 | 0 |
| KONKR | long names: 3 games × 14 layouts Own | 42 | 42 | 0 | 0 | 0 |
| KONKR | missing metadata: Bizarre Pokeventure × 14 (part of the 42) + Cover details on G | 15 | 15 | 0 | 0 | 0 |
n/a = a row style (Shared / Row reveal / Row opens) on a layout that never marks the game you're on (Art library, Bubbles,
Camera, Cover details, Hero, Pocket classic, Terminal: 7 × 3 = 21) — the style is not offered; checked by hand on build G
(Hero, Terminal, Art library: no row even after a D-pad move).
NOT TESTED: KONKR CHAN · Banner, visual (the pixel check has no reference for that banner); open / geometry / focus /
buttons / settle PASS.
**Odin long names: NOT TESTED** — its library has one game (WWE SmackDown vs. Raw 2011); no safe synthetic fixture exists
(adding a fake ROM / renaming a game would touch the owner's library).

Missing metadata is checked apart from long names: Pokémon Bizarre Pokeventure (hack, no year / developer / description) —
no empty label, no "Unknown" placeholder, no gap where a fact would be, on all 14 layouts.

Runs: full k471a / o471a (build A); Box k471b / o471b; Row opens k471r / o471r + k471ro / o471ro; Cover details k471c / o471c,
k471cf / k471cd (280 dpi), o471cf / o471cd; font k471fs / o471fs; density k471dd / o471dd; long names k471L1–L3, k471cl.
Case flow clip / Box D-pad: f471 (B), g471 (D).

**D242 (test path) found while merging:** 7 Row opens combos (KONKR Card binder, Orbit, Rental shop; Odin Arcade, Card binder,
Rental shop, Stacks) were PASS in the full run but "n/a" in the Row opens-only rerun — the screenshots showed the layout's own
popup: the style pref set right after the layout switch had been lost. Every PASS in k471r / o471r was checked on a contact sheet
to really be the Row opens popup. Re-running with Own first (k471ro / o471ro) still left Rental shop (KONKR) and 4 Odin
layouts on their own window, and by hand on the KONKR: Rental shop with Row opens → A opened the rental tag; RIGHT, LEFT,
A → Row opens. **D243 (real bug, fixed):** the layout-change effect cleared the "game you're on" mark after the new layout's
card had already set it, and a card that doesn't move never marks again — so a chosen row style fell back to the layout's
own window until the first D-pad move. Now cleared while the new layout composes. Build F: 7 layouts × Row opens / Row reveal
/ Shared with the row style first (no Own before it) — k471rf / o471rf 21 / 21 PASS on each device.

**D244 (found in the PDF review, fixed):** Cover details · More at 280 dpi on the KONKR: the second info line was cut and the
played / trophies line had no height — the gate graded it PASS (a line cut inside its own text box, and a zero-height line,
are not visible in the dump's boxes; zero-height text is too common in hidden layers to be a gate rule yet). On a short panel
with More open the keys are compact (26 dp, 4 dp apart, touch >= 24 dp). Build G: k471g / k471gf / k471gd / k471gl / k471gm,
o471g / o471gf / o471gd PASS, and checked by eye: both lines whole at 1.0, 1.3, 280 / 420 dpi, long and sparse games.

## Item-by-item
- **Cover details hidden metadata (D232):** the facts list scrolled inside a short panel; labels showed, values sat below
  the fold. Now: the header line already has console · year (not repeated); with More open every fact goes into two plain
  lines under the name (console · year · developer · genres / played · last · trophies), so nothing can sit under the fold
  at 1.0, 1.3 or 280 dpi. Panel fully opaque (a ghost of the title showed through). New gate check `facts` (label shown,
  its value below not shown → FAIL; selftest with the 470 dump as fixture) + unit test Fixtures471Test.
- **Cardboard box (D234, D235):** the story opens with a whole-sentence lead (story first on short sheets), full story in
  Contents; the story box takes focus only when it scrolls; DOWN at its end goes straight to Play. "Tap the case to open it"
  only shows with Inspect. Paper look and controller kept.
- **Box Details (D236):** names step down in size until their longest word fits a line (`WholeWordsText`), never split.
- **Bubbles (D237):** on short windows the name goes under the banner instead of over the art; art sharp, details scroll.
- **Magazine 1.3 (D238):** the arrow is in the same row as the word.
- **Arcade / Memory card:** visuals kept; touch areas stay 24 dp (rows are 25–27 dp apart: more would overlap the next row
  or need more spacing, i.e. a different look). 48 dp not met — documented limit.
- **Stadium 2 / Case flow (D239):** sharp still backdrop by default; "Watch clip" plays the original clip fitted (640×480 in
  852×193, black bars, no stretch); Stop returns to the still. No clip decoding while idle (log: only the still request).
  The owner's "video previews" preference is kept: off = no Watch clip button.
- **Blades (D240), Row opens +N (D241):** facts step down to fit; badge strip fits its count on one line.

## Remaining limitations
- Row opens' story preview ends with "…" (pre-existing; the full text is in Details) — a rule-11 item for the next pass.
- Bubbles: the logo slot leaves a wide gap before the name.
- Box at 280 dpi: small story viewport (the lead + Contents keep it readable).
- Arcade / Memory card touch areas 24 dp, not 48 dp.
- Odin long names NOT TESTED; KONKR CHAN · Banner visual NOT TESTED.
- Row opens on the KONKR: the developer / genre / released facts give way (zero height) to the story and keys; they are in
  Details. Box Details: "Achievements 0 / 96" can wrap between "0 /" and "96".
- Gate gap (D244): text cut inside its own box / squeezed to zero height is not detected automatically.

## Settings
Baseline as 470 (KONKR font 1.0, timeout 60 000, brightness 128 manual, rotation auto on / 0, 240 dpi, animation 1.0 / 1.0 /
unset, Case flow · N64 · Pokémon Snap; Odin font 1.0, timeout 1 800 000, brightness 179 manual, rotation auto off / 1,
369 dpi, animation 1.0 / 1.0 / 1.0, Artwork library). Restored by voltcap_test_snapshot and read back after the runs.
Note: the Voltcap view at the 471 start was KONKR Recently played · Pokémon Silver (470 had left N64 · Pokémon Snap) — the
471 snapshot (07:59) is what was restored; system values above read back equal on both devices.
