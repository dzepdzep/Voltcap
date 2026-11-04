# Popup audit — update 470 (final targeted stabilization), staging v0.6.67

Method as 467 / 469 (`device/popclip.sh` + `tools/popclip.py`, six categories, R26; `tools/popmerge.py` = latest run per
combination). USB only: KONKR GT78 BW0306N230000845 (960×640, 240 dpi), Odin 2 Portal b92036fb (1920×1080, 369 dpi).
Launches blocked (qa_nolaunch); each Play tap must ask to launch **the game the run is on** (`key=` line, popclip.py FAILs
another game).

## Results (latest run per combination; layouts touched after build D re-run on the final code)
| device | config | combos | PASS | FAIL | NOT TESTED | n/a |
|---|---|---|---|---|---|---|
| KONKR | font 1.0 — 53 layouts Own + 8 × Banner / Classic / Shared / Row reveal | 85 | 81 | 0 | 0 | 4 |
| Odin | same | 85 | 81 | 0 | 0 | 4 |
| KONKR | font 1.3, 15 layouts Own | 15 | 15 | 0 | 0 | 0 |
| Odin | font 1.3, 15 layouts Own | 15 | 15 | 0 | 0 | 0 |
| KONKR | display size larger (280 dpi), 15 layouts Own | 15 | 15 | 0 | 0 | 0 |
| Odin | display size larger (420 dpi), 13 layouts Own | 13 | 13 | 0 | 0 | 0 |
| KONKR | long / missing details: 3 games × 14 layouts Own | 42 | 42 | 0 | 0 | 0 |
n/a = Bubbles / Cover details with Shared / Row reveal (those layouts draw no row; the style is not offered).
469 for comparison: KONKR 287 / 5 FAIL / 2 NT, Odin 288 / 4 / 1.
Runs: KONKR k470f, k470g (build D), k470h (G), k470j (H), k470k / k470kb (J); font k470fs, k470hs, k470js, k470ks;
280 dpi k470d, k470m (K); long names k470L1–L3. Odin o470f, o470g (D), o470F (F), o470h (G), o470k (J), o470b (G);
font o470fs, o470Ffs, o470hs, o470ks; 420 dpi o470d, o470m (J). Build D → K changes touched only Box, Night, Orbit,
Arcade, Memory card, Magazine (and a QA log in VideoPreview) — each re-run on its final code on both devices.

## Long / missing details (item 2)
The 469 run asked for a game name the hook didn't match and silently stayed on Pokémon Stadium 2. 470: the hook matches
exact / contains / ROM file name and logs `game 'q' -> name | NO MATCH`; popclip stops on NO MATCH and writes game.txt.
KONKR: "From TV Animation One Piece – Yume no Luffy Kaizokudan Tanjou!" (very long name, sparse metadata, landscape art),
"Pokémon Bizarre Pokeventure" (hack: no year / developer / description), "Advance Wars 2 – Black Hole Rising" (GBA, wide
cover) × 14 layouts: **42 / 42 PASS, 0 slides**. Found on the way: D225 — long names slid on one line though room was free
(StableFullText measured without the theme's letter spacing) — fixed. **Odin: NOT TESTED** for long names — its library has
one game (WWE SmackDown vs. Raw 2011), which is the run game for every Odin run.

## D212 (item 4) — touch rooms, looks kept
| layout | before (k470f) | now | how |
|---|---|---|---|
| Arcade | menu lines 17 dp to touch | 24 dp | SceneControl(minTouch = 24 dp): touch box taller than the drawn bar; the bar keeps its size, the gap between lines grows 1 → 8 dp |
| Memory card | Saves line 14 dp | 24 dp | same |
| Orbit | round keys' boxes crossed 9×18 px | apart | 22° between keys (was 17°): the fan is a little wider |
| Night | turned keys' boxes crossed 13 px; status lines 19 px | apart | keys are 3 px apart laid out — only the screen-aligned boxes of the 4°-turned keys cross: marked tilted for the gate (as 467's rental tag); status lines 10 dp apart |
| Magazine | 3–6 px | apart | the page is turned 2°: marked tilted; 8 dp gutter between the two callout columns |
Tap test on the KONKR (final build, screenshots `tap470/`): a tap in Arcade's new gap under GAME INFO opened Game info; a tap
in the strip where Night's PLAY box and DETAILS cross opened Details (no launch asked); Orbit Details, Magazine OPTIONS
(at its box's bottom edge) and the Memory card Saves line (above its words) each opened their own page. 5 / 5.
**Limit that stays:** 24 dp is the gate's minimum (WCAG 2.2 target size); Android's 48 dp recommendation is not met by these
rows — at 48 dp the Arcade menu would need twice its height (more than the tube shows) and the look would change.
Compose widens a small target towards 48 dp only where its neighbours leave room.

## Other gate findings fixed this update
D226 Box / Night status lines crossed on the turned sheet (12 / 10 dp) · D227 Night "HAL Laborator|y" (value auto-sizes in
two lines) · D228 Magazine at 1.3 "ACHIEVEMENT|S" (callouts auto-size, gutter) · D230 pixel check read PRESS START's flat
letter bottoms / the tube's scan lines as a cut button (now checks that the fill goes on below; real cuts still FAIL) ·
D231 run tags differing only in case overwrote each other on the case-insensitive /sdcard (test path; recovered) ·
Orbit at 280 dpi "did not open" = the gate read "Play PLAY" as two words (narrow keys: icon description + caps label) —
test path, fixed in geomcheck `_label`, selftest added.

## Hardware, settings (item 6)
Captured before any change (baseline): KONKR font 1.0, timeout 60 000, brightness 128 manual, rotation auto on / 0, 240 dpi,
animation 1.0 / 1.0 / unset, layout Case flow, view Nintendo 64 · Pokémon Snap. Odin font 1.0, timeout 1 800 000, brightness
179 manual, rotation auto off / 1, 369 dpi, animation 1.0 / 1.0 / 1.0, layout Artwork library. After: all equal on both
(read back), layouts restored by the snapshot (now including the "appearance" prefs) — KONKR Case flow / N64 / Pokémon Snap,
Odin Artwork library. **Not claimed:** the KONKR's 60 s timeout and the Odin's Artwork library are the values found at the
start of 470 — 468 / 469 may have changed them before a baseline existed; the owner's own originals are unknown.
Font / density runs: scripts read and wrote back the exact value ("font was 1.0 → restored 1.0", "density override none →
reset"). RC 0.6.66 rebuilt (inputs identical: Gradle took 5 tasks from cache) and reinstalled on both; test files and scripts
removed (kept charge_log.csv, voltcap_qa_screen, Odin `upd`; untouched /data/local/tmp items not made by 469 / 470).
