# Cartridge accuracy audit — Update 300 (21 Oct 2026)

Scope: every cartridge / game card Voltcap draws (`CartShells.kt`, `MediaEditions.kt`, `ShellMaterials.kt`,
`MediaVariants.kt`, `CartBacks.kt`, `CartUnderside.kt`, `PhysicalMedia.kt`, `RomScanner.kt`). Update 297's Switch label
and special-cart heights were kept as they are. Switch 2 was not touched.

Status words (kept apart on purpose):
- **VERIFIED**: a source states it; the quote or link is given. Colour values (hex) are still approximations.
- **ESTIMATED**: drawn from a photo or a reasonable assumption, not measured.
- **CONFLICTING**: sources disagree with each other or with the drawing.
- **UNCONFIRMED**: no usable source found.

Sources that could not be read on 21 Oct 2026: ConsoleMods (offline for maintenance), Bulbapedia (maintenance),
Game Verifying's casing table (loads only partly). Measurements that depend on them were left alone.

## Selection policy (checked in the code, unchanged order)
1. Your pick for the game (Cartridge shell…), 2. your pick for the console / a global style (pack, clear, template
looks), 3. a verified, unambiguous retail edition for that title and region (`MediaEditions.auto`), 4. the console's
authentic shell — **now using the ROM header where it can be read** (Game Boy / Color, DS), 5. the earlier fallback.
Games with several documented editions are not picked by themselves; they are offered in Cartridge shell…
The same `mediaFor` / `authenticIdFor` path serves the home layouts, mixed lists, the game window, the launch
animation, opened cases and the second screen.

## Audit table

| System | Variant | Region | Before | Change (Update 300) | Source | Status |
|---|---|---|---|---|---|---|
| GB / GBC | Automatic shell from the ROM header (0x0143: 80h dual-mode black, C0h Color-only clear, else Game Boy grey) | all | File-name tag "(GB Compatible)" only | **Header first** (checksum-checked, read off the main thread, 0x240 bytes, plain files / content addresses / .zip; .7z unknown), kept with size + date; the tag is the fallback. Editions and your picks still win | [Pan Docs](https://gbdev.io/pandocs/The_Cartridge_Header.html#0143--cgb-flag) | VERIFIED (flag meaning); the plastic per game remains a software-based inference |
| GBC | Black dual-mode Game Pak | all | 57×65.5×9 (the clear cart's size) | The Game Boy case in black: 57×65×7.8 like `gb` | [Game Verifying, GBC](https://gameverifying.com/wiki/cart-based-systems/gameboy/color) ("GBC games with black DMG cases…") | VERIFIED (same case); mm ESTIMATED |
| GBC | Clear Color-only Game Pak | all | No notch on the back, 9 mm | Unchanged (correct) | Wikipedia, Game Boy Game Pak | VERIFIED |
| GB / GBC | Moulded "Nintendo GAME BOY™" / "GAME BOY COLOR" on the grip | all | Not drawn on the front | Not changed (needs new front geometry; noted) | Game Verifying DMG / GBC | VERIFIED, not implemented |
| GB | Pokémon Red / Blue / Yellow, Donkey Kong Land | US/EU/AU | Coloured, automatic; JP grey | Unchanged | [Game Verifying DMG](https://gameverifying.com/wiki/cart-based-systems/gameboy/dmg) | VERIFIED |
| GB | Donkey Kong Land 2 / III yellow | US/EU/AU | Pending | Unchanged | — | UNCONFIRMED |
| GB/GBC | Pokémon Gold / Silver (western) | US/EU/AU | Gold / silver glitter, automatic | Unchanged | Game Verifying DMG: "Gold and Silver should be Gold and Silver respectively with glitters on them." | VERIFIED |
| GB/GBC | **Pocket Monsters Kin / Gin** | JP | Pending, not automatic | **Verified, automatic**: Kin dark-blue front / dark-grey back, Gin the opposite (existing materials) | Game Verifying DMG: "The front half of Gold should be dark blue and the back half should be a dark grey. The Japanese Silver is the exact opposite." | VERIFIED (colour values approximate) |
| GBC | Pokémon Crystal clear cyan glitter; Command Master teal; Kirby Tilt 'n' Tumble pink | — | Present | Unchanged (Kirby's EU/AU regions not trimmed — unconfirmed) | Game Verifying GBC | VERIFIED colours |
| GBC | Pokémon Pinball rumble shell | all | Present (plain outline) | Unchanged; rear photos show a top hump the front lacks | CARTRIDGES.md notes | CONFLICTING (outline) |
| GB | Game Boy Camera | all | Shell exists, no automatic edition | Not changed (colours vary) | Wikipedia | ESTIMATED |
| GB | **Nintendo Power GB Memory** | JP | Dark grey placeholder | **White** | [MarioWiki](https://www.mariowiki.com/GB_Memory): "white GB Memory Cartridges"; Bulbapedia infobox "Colors: White" | VERIFIED (text sources; the page photos were not examined pixel by pixel) |
| SFC | **Nintendo Power SF Memory** | JP | Grey | **White** | MarioWiki: "white flash memory cartridges known as SF Memory Cassettes" | VERIFIED (text) |
| GBA | Ruby / Sapphire / Emerald / FireRed / LeafGreen | listed incl. JP | Present | Unchanged (FireRed's red vs orange already noted as conflicting) | [Game Verifying GBA](https://www.gameverifying.com/wiki/depository/gba) | VERIFIED colours; JP ESTIMATED; FireRed CONFLICTING |
| GBA | WarioWare: Twisted! / Mawaru, Boktai series, Drill Dozer, Classic NES Series, GBA Video, bit Generations, Rockman EXE 4/5 | — | Present (297 heights kept) | Unchanged | Game Verifying GBA | VERIFIED |
| GBA | **Yoshi Topsy-Turvy** | EU/AU | US shell picked automatically in PAL | **Not automatic in EU/AU** ("PAL copies have a different front casing", not drawn) | Game Verifying GBA | VERIFIED (the PAL shell itself not drawn) |
| GBA | Famicom Mini colours, Rockman EXE 4 grey alternates | JP | Missing | Not added (which title gets which colour unconfirmed) | — | UNCONFIRMED |
| DS | **Card thickness** | all | 3.8 mm | **3.64 mm** | [Game Verifying NDS](https://gameverifying.com/wiki/cart-based-systems/nds): "a cart thickness of 3.64mm, ± 0.02mm" | VERIFIED |
| DS | **White DSi-exclusive card (NTR-005 white)** | all | Material existed, nothing used it | **From the DS header**: unit code 03h on a game card (title-ID category 00030000) → white card. DSiWare (category 00030004, e.g. Cave Story, Dragon Quest Wars — never on a card) stays on the standard card | Game Verifying NDS ("used for DSi Exclusive games"); [Wikipedia](https://en.wikipedia.org/wiki/Nintendo_Game_Card) ("white-colored casings"); [DSiBrew header](https://dsibrew.org/wiki/DSi_cartridge_header) | VERIFIED (rule); colour approximate |
| DS | **NTR-031 infrared** (8 titles) | + Korea | Korea missing | **Korea added** | [Game Verifying NTR-031](https://www.gameverifying.com/wiki/cart-based-systems/nds031) | VERIFIED |
| 3DS | Side tab | all | Right side, size estimated | Unchanged | Game Verifying 3DS (side only) | VERIFIED side; size ESTIMATED |
| N64 | 42 coloured North American carts, grey alternates offered | US | Present | Unchanged | [micro-64](https://micro-64.com/database/colouredcarts.shtml) | VERIFIED |
| N64 | Majora's Mask PAL gold, Stadium 2 PAL | EU | Cited to micro-64 (which covers North America) | Not changed; source attribution flagged | nintendo64ever (not re-read by me) | CONFLICTING (source) |
| NES | Zelda / Zelda II gold vs grey | US | Offered, not automatic | Unchanged | thelegendofnes.com | VERIFIED |
| SNES | Doom red, Killer Instinct black, Maximum Carnage red; NA vs SFC/PAL shape | — | Present | Unchanged | doomwiki, SVG | VERIFIED / ESTIMATED (shape) |
| FDS | **Disk System images (.fds)** | JP | Scanner files them under NES → drawn as an NES / Famicom cartridge | **Drawn as the FDS disk** (console, emulator, launch unchanged) | code | VERIFIED (code) |
| Genesis | EA tab, Virtua Racing, Codemasters J-Cart, Sonic & Knuckles | — | Present / offered | Unchanged (J-Cart stays offered only; S&K kept) | CARTRIDGES.md, de.wikipedia J-Cart | CONFLICTING (EA tab place) |
| Sega / other | Genesis, 32X, WonderSwan, Evercade, Microvision proportions | — | Recorded mismatches vs rear photos | Not changed until ConsoleMods can be read | CARTRIDGES.md | CONFLICTING |
| Many | SG-1000, Famicom, FDS, SFC, Mega Drive, game.com, Mega Duck… | — | Reachable only by picking (no extension / folder maps to them) | Not changed (needs scanner rules — out of this update's minimal scope) | code | VERIFIED (code) |
| Switch | Card, red label header (297) | — | Present | Unchanged | 297 | — |

## Checked on the AYN Thor
See SUMMARY 300 for what was looked at on the device and what wasn't.
