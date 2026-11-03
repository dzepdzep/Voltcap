# Update 463 — photo-made cartridges for every console with a real cartridge / card: coverage matrix

How it works (unchanged from 462 / 462b): for a game whose piece is drawn (no real cartridge photo of its own — hacks, homebrew,
games ScreenScraper has no cartridge photo for), the drawn shell's front is replaced by a REAL cartridge photo of the SAME shell
taken from your own library's scans, with the game's own label laid into the photo's sticker recess (the label is exactly what the
drawn shell would print: your pick › its real label scan › your Labels folder › the label made from its own artwork — never the base
ROM's or another game's). Sides and back take the photo's plastic. No photo that fits → the drawn shell, as before.

New in 463 (PhotoShells.kt):
- Every console with a real cartridge / card is wired; a photo is only used for the shell its own game came on
  (CartShells.authenticIdFor: console + region of the photo's game, e.g. a Japanese / PAL SNES photo is the Super Famicom shape,
  a US one the North American pak), and only when its outline has the shell's proportions (±9 %).
- Outside the Game Boy family the photo is never tinted: a colour variant (gold / blue N64, black Famicom) is photo-made only
  from a photo of that same plastic (near in colour AND the same kind — grey with grey, coloured within 35° of hue). A green Army
  Men N64 photo is not used for a black pak; it falls back to drawn.
- Outside the Game Boy family the photo's sticker must actually be found where the shell's is (and be paper, not plastic);
  otherwise the photo is not used. Measured sticker places where the drawn shell's differ: Super Famicom / PAL (the real sticker
  60 % wide from the top edge), DS / 3DS (the whole paper down to the product-code strip is covered, so no other game's NTR- /
  CTR- code shows).
- See-through detection only for shells that came in see-through plastic (NES grip ridges read as "board showing").
- QA: `--ez voltcap_test_photoshells true` now analyses every shell and writes `photoshells/inventory.tsv` (per shell: photos,
  fits, per colour the result or fallback reason; ids only, no addresses).

## Matrix (KONKR GT78 library scans, 3 Nov 2026)

| Console / shell | Authentic model | Real photo in library (of this shell / fit) | Photo-made | Label matching | Front / side / back checked | Hardware | Remaining limitations |
|---|---|---|---|---|---|---|---|
| Game Boy (grey) | yes | 139 / 24 | yes (grey; yellow tinted, 462) | own label first (461 D184) | yes (462b sheet) | KONKR | 90° = slab of shell thickness; back = drawn back in photo plastic |
| GBC dual-mode (black) | yes | 45 / 24 | yes | same | yes (462b) | KONKR | same |
| GBC Color-only (clear) | yes | 104 / 24 (23 clear) | yes | same | yes (462b) | KONKR | all clear paks use the one colourless clear photo |
| GBA | yes | 385 / 24 | yes (dark grey); Ruby / Sapphire / Emerald = drawn (no such photo) | same | yes (463: Circle of the Moon real vs generated) | KONKR | same as GB |
| N64 | yes | 89 / 24 | grey, gold, blue; black / red / clear / smoke = drawn (no photo of that plastic) | same | yes (Pokémon Snap real vs generated identical; Army Men label on grey) | KONKR (real N64 games) | back drawn; 90° slab |
| SNES North America | yes | 151 / 24 | grey; black = drawn | same | yes (Alien 3 photo, own + Astérix label) | KONKR via QA label stand-ins* | back drawn |
| Super Famicom / PAL SNES | yes | 19 / 19 | grey (measured sticker) | same | yes (Astérix photo, own + Alien 3 label) | KONKR via QA* | back is the drawn shell's round-cornered outline (photo's top corners are squarer); drawn label art (96×52) laid into the real 78×37 sticker |
| Famicom | yes (5 colours) | 7 / 4 (all black) | black; grey / red / yellow / blue = drawn | same | yes (DBZ photo, own label) | KONKR via QA* | back plain; few photos |
| NES | model exists, **does not match the photos** | 49 / 15 | **NOT photo-made** | — | drawn fallback checked | KONKR | every NES photo shows the sticker upright down the right half beside the grip ridges; the drawn shell has it across the top. Needs the NES shell's sticker re-measured (owner decision: it changes the drawn shell) |
| DS | yes | 242 / 23 | dark grey; white = drawn | same | yes (FFCC photo, own label) | KONKR via QA* | back drawn (contacts) |
| 3DS | yes | 214 / 23 | yes | same | yes (Cities of Gold photo, own label) | KONKR via QA* | back drawn |
| Switch | yes | 26 / 24 | yes | same | yes front (DKC photo, own label); back drawn | KONKR via QA* | back drawn |
| Switch 2 / Game-Key Card | yes | none (no ScreenScraper id wired) | no → drawn | — | — | — | needs Switch 2 cartridge photos |
| Virtual Boy | yes | 0 | wired, falls back | — | — | — | no photo in library |
| Mega Drive / Genesis, MD Japan, Master System (+Japan), Game Gear, 32X, SG-1000 | yes | 0 | wired, falls back | — | not verifiable | — | no Sega cartridge photos in the scanned library |
| Atari 2600 / 5200 / 7800, Lynx, Jaguar | yes | 0 | wired, falls back | — | — | — | no photos |
| PC Engine HuCard, Neo Geo Pocket, WonderSwan | yes | 0 | wired, falls back | — | — | — | no photos |
| Neo Geo AES / MVS | yes | — | excluded | — | — | — | both shells under one ScreenScraper id and too close in shape to tell apart safely |
| PS Vita | no authentic CartShells model | — | not possible | — | — | — | custom pieces stay |
| Special shells (EA, Sonic & Knuckles, Codemasters, Pinball, Camera, Tilt, …) | yes | — | excluded | — | — | — | drawn special shells stay |
| Systems without a real cartridge / custom models you picked | — | — | untouched | — | — | — | CUSTOM MODEL / pack picks are never replaced (CartShells.frontFor pack path runs first) |

\* The scanned library lists these consoles' games, but none are on the connected devices (KONKR: GBA, GBC, N64; the Odin has no
cartridge library). So the generated pieces were checked on the KONKR's real renderer with a QA-only label stand-in — a label
cut from another real photo of the same console (`lab_*.png`, session only, nothing saved) on the photo-made shell — next to the
real photo. Not checked on a real game of those consoles: the home-screen path is the same code as N64 / GBA (verified with real
games).

## Evidence
`Claude outputs/qa463/qa463_sheet_a.png` (GBA, N64, SNES, Super Famicom), `qa463_sheet_b.png` (Famicom, DS, 3DS, Switch, NES fallback):
left = the real library photo, then the generated piece at front / 45° / 75° / 90° / back, same crops. Templates: `qa463/ps1`,
`ps2`, `ps4` (`.txt` files there carry ScreenScraper addresses — do not share). Inventory: `qa463/ps2/inventory.tsv`.

## Accurate vs approximate
Accurate: the photographed front — real plastic, moulded lettering, grooves, texture, edges, recess and light — for every
photo-made shell; label = the game's own. Approximate (real AND generated alike): the 90° view is a slab of the shell's thickness;
backs are the researched drawn backs in the photo's plastic (no back photos exist); clear GBC paks share one clear photo.
Performance: templates are only made for shells on screen (one 720 px picture each); the QA run that made all 14 at once had the
app at 411 MB PSS on the KONKR (not compared against the RC in the same state).
