# Update 465 — cardboard-box faces: source trace (batch 1) and plain-colour sides (batch 2)

Device: KONKR GT78 BW0306N230000845 (USB). Staging v0.6.67 build for the tests only; RC v0.6.66 back at the end.
Evidence (PC `Claude outputs/qa465/`): `qa465_box_sides.png` (FILL vs colour v1, 7 games x 3 poses),
`qa465_box_sides_v2.png` + `qa465_box_sides_v2_zoom.png` (printed / colour v1 / colour v2, right side + top).
Trace: `voltcap_test_mediadump` FACES / SIDES lines (sanitised — no URLs).

## Batch 1 — where every visible face comes from (N64 Pokémon boxes)
| Face | Source | Status |
|---|---|---|
| Front | authentic ScreenScraper box scan (US), BoxTexture.front | genuine, unchanged |
| Spine (left) | authentic "Spine" scan | genuine, unchanged |
| Back | authentic back scan (defaultCaseBack) | genuine, unchanged |
| Right side / top / bottom | NO scan exists (ScreenScraper has front / spine / back only) → generated (Box sides = "Fill missing sides", the default) | generated |

- No wrong scan chosen, no region mix-up, no other game's art. ROM copies: internal `/storage/emulated/0/ROMs/n64` only (9 games); no SD-card duplicates on the KONKR.
- The red/white look of the right side and top is the FILL print: its base colour comes from the front's right-edge N64 band and it
  prints the title and logo. That is the "generated fallback", correctly used only because no real side scan exists.
- Genuine N64 box designs (front / spine / back) are not altered by anything in this update.
- Regression cover: BoxSidesLabels465Test (side picture or your pick wins over any colour; real label scans never get a printed plaque / code).

## Batch 2 — new option "Plain colour from the box art" (Settings › Box sides)
- Default stays **Fill missing sides** (printed) — nobody's setting changes. The new option is `BoxSides.COLOUR`.
- Colour: histogram of the console's art window only (12 % inner margin; skips the N64 / GB platform band, white corners and the
  rating block by low weight on near-white / near-black unless they are most of the cover), neighbouring buckets summed, the winning
  bucket averaged, muted 10 % toward grey. One colour for right side, top and bottom.
- Surface: flat colour + fine deterministic grain and row streaks (cardboard), soft darkening at each fold (fold thickness). No
  stripes, logos, codes, gradients or gloss.
- Real side scans and your own side pictures always win (pick() order unchanged).
- v1 → v2 tuning: v1 picked Kirby 64 red (the N64 band) and Silver gold (the emblem); v2 (art-window tolerance 0.15, saturation weight
  0.3) gives Kirby pink/blue-pink and Silver slate blue.

| Game | Printed (current default) | Plain colour v2 | Verdict (visual, owner to confirm) |
|---|---|---|---|
| Pokémon Snap | red print + title | plain red | colour cleaner, matches the front |
| Pokémon Stadium | red print + title | plain red | colour cleaner |
| Pokémon Stadium 2 | red/blue print | plain red | colour cleaner |
| Paper Mario | red print + title | plain red | OK |
| Kirby 64 | red (band) print | pink | v2 matches the box art; v1 was wrong |
| Pokémon Silver (GBC) | print | slate blue | v2 OK; v1 gold was wrong |
| Advance Wars 2 (GBA) | dark print | dark brown | OK |

"Authentic" side faces cannot be shown: no right / top / bottom scans exist for these games (front and spine authentic in all columns).
**Not marked fixed** — the choice of default is the owner's (R25: visual sign-off, not tests).

## Coverage — systems that use the cardboard box faces
N64, SNES / SFC, NES / Famicom, Game Boy / GBC / GBA (cardboard boxes) use rememberBoxSides → all get the option.
Plastic cases (DS / 3DS / Switch / disc systems) don't use generated cardboard sides; unchanged.
Verified on hardware: N64 (5), GBC (1), GBA (1). SNES / NES / Famicom boxes: no games on the connected devices — wired, not verified.
