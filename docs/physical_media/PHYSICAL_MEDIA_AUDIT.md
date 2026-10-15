# Voltcap — physical media audit (update 206)

Source-first pass over every cartridge, card, UMD, disc and case Voltcap draws. The full per-model data, with a source
for every size, is in `physical_media_verification.json` next to this file (158 entries). The to-scale sheet
`qa_cartridges_to_scale.png` shows every canonical cartridge / card with its label window as a checkerboard.

**Accuracy over completeness:** of 158 entries, only 4 are backed by a manufacturer or standards body (verified).
73 agree with a secondary source (approximate), 16 have sources that disagree (disputed) and 65 have nothing
measured behind them (`UNCONFIRMED — NEEDS MANUAL MEASUREMENT`). Nothing below is called fact-checked without its
source.

## Status values
- **verified** — a manufacturer or standards-body figure (Sony's UMD spec, ECMA-130 / ECMA-267, Nintendo's 8 cm disc page).
- **approximate** — a secondary source (ConsoleMods' community caliper table, Wikipedia's keep-case table) that agrees with the drawing within 0.6 mm.
- **disputed** — sources disagree with each other or with the drawing. The drawing was **not** changed to either value.
- **unconfirmed** — no measurement found: `UNCONFIRMED — NEEDS MANUAL MEASUREMENT`.

## Sources used
- Sony press release, 11 May 2004 (PSP / UMD specifications): https://sony.mediaroom.com/2004-05-11-Sony-Computer-Entertainment-Inc.-Announces-Product-Specifications-of-Handheld-Video-Game-System-PlayStation-Portable-PSP
- ECMA-130 (120 mm CD-ROM): https://ecma-international.org/publications-and-standards/standards/ecma-130
- ECMA-267 (120 mm DVD): https://ecma-international.org/publications-and-standards/standards/ecma-267/
- Nintendo Japan, GameCube 8 cm disc: https://www.nintendo.co.jp/ngc/disk/index.html
- ConsoleMods, Dimensions for Game Cartridges (community): https://consolemods.org/wiki/Dimensions_for_Game_Cartridges
- Wikipedia: Keep case (size table), Jewel case, Universal Media Disc, Nintendo Game Card, MultiMediaCard, Blu-ray.
- Voltcap's own earlier research notes: `CARTRIDGES.md` (update 195) and `CASES.md` — their original URLs weren't recorded, so anything only from there is approximate or unconfirmed.

## Canonical models (one per real media type)
- **Cartridges and cards:** the drawn shells in `src_copy\ui\CartShells.kt` (67 ids, `CartShells:<id>` in the JSON). Since 206 every cartridge console uses its own authentic shell by default (by region; "(GB Compatible)" Game Boy Color games on the black dual-mode shell).
- **UMD:** `MediaShell.UMD` (PhysicalMedia.kt), now 65 : 64 (Sony) instead of square.
- **Discs:** `MediaShell.DISC*` — 120 mm for CD / DVD / Blu-ray / GD-ROM, 80 mm GameCube.
- **Cases:** `CaseCatalog` (`src_copy\ui\CaseCatalog.kt`), one entry per console and region (`CaseCatalog:<console>/<region>`).

## Deprecated duplicates (still in the code, no longer chosen by default)
| Old | Now | When the old one still shows |
|---|---|---|
| 3D pack GLBs `model_*_cart_real` (n64, gb, gbc, gba, snes_us, snes_pal_sfc) | the drawn shell (`CartShells`) | never for a game; the 3D models pack keeps them on disk |
| MediaTemplates cartridge pictures (`tpl_cart_*`) | the drawn shell | consoles with no drawn shell |
| Custom pack shapes (`cart_n64`, `cart_genesis`, `cart_psp_custom`…) | the drawn shell / UMD | only with Cartridges only, or the custom cartridge source, picked |
| 3D pack discs (`disc_*_120`, `disc_gamecube_80`, `umd_psp`) | `MediaShell` discs / UMD | never for a game |

**N64:** the "two N64 cartridges" were the template picture (default for anyone who never picked Real shell) and the drawn shell. Since 206 the drawn shell (116 × 76.6 × 18.5, ConsoleMods) is the only default one; the custom pack N64 shows only when the custom pack is picked.

## Before / after (sizes changed in 206)
| Model | Before | After | Why |
|---|---|---|---|
| UMD (`MediaShell.UMD`) | square (312 × 312 px picture) | 65 : 64 | Sony: approx. 65 W × 64 D × 4.2 mm, 60 mm disc |
| Switch 2 case | 104 × 170 × 10, estimated | same size, sourced | Wikipedia's keep-case table lists 170 × 104 × 10 |

Every other size is unchanged. Where a source disagrees, the entry is **disputed** and the drawing stays as it was until it's measured.

## Labels
- The art is now **stretched to the label window** (never cropped) in every drawn shell, template cartridge and custom pack cartridge: the label keeps the shell's real size and place, the picture adapts to it (`frontArtworkFit = stretch`). A ScreenScraper label scan is the printed sticker, so it fits exactly.
- Label sizes with a measurement behind them (CARTRIDGES.md): NES 55 × 97 (7 mm over the top), Famicom 90 × 45.5, Game Boy / Color 42 × 37, Virtual Boy ~52 × 42, Mega Drive 74 × 67 (7 mm over the top), 32X 75 × 43, Game Gear ~55 × 40. Every other label window is estimated (`labelSource` says so in the JSON).

## Regional variants drawn
- Cartridges: SNES (North America) vs Super Famicom / PAL; Mega Drive vs Mega Drive Japan; Master System vs Mark III; Atari 2600 vs Activision.
- Cases: PS1 (US / JP jewel 10.4 mm, PAL 17.4 mm), PS2 (black, PAL dark blue), GameCube (DVD-size West, compact clear Japan), NDS (black 15 mm NA / JP, clear 20 mm EU), Genesis / Sega CD / Saturn / Dreamcast per region, Neo Geo Pocket.
- **Not drawn:** New Nintendo 3DS black 14 mm cases (Wikipedia), Platinum / Greatest Hits colours, PS1 longboxes, Famicom box sizes.

## Scraper rules (ScreenScraper)
| ScreenScraper media | Voltcap label | Used for |
|---|---|---|
| box-2D | Box | case front |
| box-2D-back | Back of the box | case back (first choice) |
| box-2D-side | Spine | case spine |
| box-texture (max 1600 wide) | Box (full scan) | back / front / spine cut when the parts are missing |
| box-3D | Box (3D) | 3D box looks |
| support-2D | Disc / cartridge | the piece's own photo (Use its own photo on) |
| support-texture | Disc label | the label on the drawn piece; the disc face (round mask) |

## Fallback rules (one rule everywhere)
1. A shell picked for the game or its console (game menu › Cartridge shell…).
2. Its own ScreenScraper photo, when "Use its own photo when one is found" is on and it has one.
3. The drawn piece for its console and region, wearing the **label scan**; the cover only when there's no label scan.
4. A console with no medium of its own: Voltcap's plain cartridge (or the plain CD with its console's name as a disc).
- Case back: Back of the box scan → cut from the full box scan → printed back with the cover.
- Discs: the label scan masked to a ring with the centre hole; else the console's printed disc with the cover.

## Cache changes
- Drawn shell fronts: key `f2|…` (206, stretched labels) — old cut-crop fronts aren't reused.
- Box scan cuts: `BoxCrop` key version 4 (205).

## QA
- `qa_cartridges_to_scale.png`: every canonical cartridge / card at 1 mm = 1.36 px with a checker label, outline coloured by status. It checks sizes and label places; the true outlines are drawn in code.
- **On-device renders (front, back, ¾ front, ¾ back, side) are still to do** after you build 206 on the Thor.

## Unresolved (needs a real measurement or photo)
- **Disputed sizes:** Super Famicom width (127 vs 130), Game Boy Advance thickness (8 vs 9), 32X orientation (111 wide or 111 tall), Master System (within ~1 mm), PSP case (177 × 104 vs 168 × 99), PS1 PAL depth (17.4 vs 18), PS2 / Xbox / Xbox 360 depth (14 vs 15), Blu-ray game cases (12 vs 13).
- **Placeholders (unconfirmed):** Pokémon mini, 64DD, Neo Geo MVS, Neo Geo Pocket, WonderSwan (back research says ~65 × 41, not 52 × 45), Channel F, GX4000, SCV, PV-1000, Loopy, Supervision, Mega Duck, Microvision, Evercade, Game Boy Camera, Pokémon Pinball, Kirby Tilt 'n' Tumble (back research says ~57 × 65, not 78), WarioWare: Twisted!, Boktai, Drill Dozer, Yoshi Topsy-Turvy, e-Reader, Nintendo Power GB Memory; most cardboard boxes and clamshells.
- **Labels:** every label window except the seven measured above.
- **Backs:** Famicom, Super Famicom, Virtual Boy, Pokémon mini and 64DD backs have no rear photo (seam and connector only).
- **Disc thickness:** GameCube's 1.2 mm is assumed (Nintendo's page gives only the diameter).

## How to use
- **A game's piece:** game menu › Cartridge shell… picks the shell for one game or its whole console; otherwise the rule above applies.
- **Own photos vs drawn shells:** Settings › Game look › Use its own photo when one is found (in the look picker; off: every game on its real shell with its label).
- **Case region:** R1 › Case › region (or by the file name's region tag).
- **Real media:** Settings › Metadata › Scraping › Real media — Count, and Save them all on the device.
- **Saving art:** Settings › Metadata › Saved on this device — picking a bigger size now starts saving again straight away; tapping Save all now while it's already saving says so ("Already saving art: n / total").

## Status tables (approximate entries are in the JSON)

### verified (4)

| Model | System | Region | W × H × T (mm) | Status |
|---|---|---|---|---|
| MediaShell.UMD | PSP | all | 65 × 64 × 4.2 | verified |
| MediaShell.DISC (per-console printed disc) | CD-based consoles (PS1, Saturn, Dreamcast GD-ROM, Sega CD, PC Engine CD, 3DO, CD-i, Neo Geo CD) | all | 120 × 120 × 1.2 | verified |
| MediaShell.DISC | DVD-based consoles (PS2, Xbox, Xbox 360, Wii) | all | 120 × 120 × 1.2 | verified |
| MediaShell.DISC (GameCube) | GameCube | all | 80 × 80 × 1.2 | verified |

### disputed (16)

| Model | System | Region | W × H × T (mm) | Status |
|---|---|---|---|---|
| CartShells:sfc | Super Famicom / SNES PAL cassette | all | 130 × 86 × 20 | disputed |
| CartShells:gba | Game Boy Advance Game Pak | all | 60 × 35 × 8 | disputed |
| CartShells:sms | Master System cartridge | all | 109.1 × 69.3 × 16.9 | disputed |
| CartShells:32x | Sega 32X cartridge | all | 111 × 72 × 16 | disputed |
| CaseCatalog:PS1/EU | PS1 | EU | 142 × 125 × 17.4 | disputed |
| CaseCatalog:PS2/US | PS2 | US | 135 × 190 × 14 | disputed |
| CaseCatalog:PS2/EU | PS2 | EU | 135 × 190 × 14 | disputed |
| CaseCatalog:PS2/JP | PS2 | JP | 135 × 190 × 14 | disputed |
| CaseCatalog:PS3/ALL | PS3 | ALL | 135 × 171.5 × 12 | disputed |
| CaseCatalog:PS4/ALL | PS4 | ALL | 135 × 171.5 × 12 | disputed |
| CaseCatalog:PS5/ALL | PS5 | ALL | 135 × 171.5 × 12 | disputed |
| CaseCatalog:PSP/ALL | PSP | ALL | 104 × 177 × 14 | disputed |
| CaseCatalog:Xbox/ALL | Xbox | ALL | 135 × 190 × 14 | disputed |
| CaseCatalog:Xbox360/ALL | Xbox360 | ALL | 135 × 190 × 14 | disputed |
| CaseCatalog:XboxOne/ALL | XboxOne | ALL | 135 × 171.5 × 12 | disputed |
| CaseCatalog:XboxSeries/ALL | XboxSeries | ALL | 135 × 171.5 × 12 | disputed |

### unconfirmed (65)

| Model | System | Region | W × H × T (mm) | Status |
|---|---|---|---|---|
| CartShells:pokemini | Pokémon mini Game Pak | all | 26 × 25 × 6 | unconfirmed |
| CartShells:64dd | 64DD disk | all | 101 × 103 × 10 | unconfirmed |
| CartShells:neogeo_mvs | Neo Geo MVS cartridge | all | 185 × 135 × 26 | unconfirmed |
| CartShells:ngp | Neo Geo Pocket cartridge | all | 47 × 40 × 6.5 | unconfirmed |
| CartShells:wonderswan | WonderSwan cartridge | all | 52 × 45 × 7 | unconfirmed |
| CartShells:channelf | Channel F Videocart | all | 102 × 133 × 22 | unconfirmed |
| CartShells:gx4000 | Amstrad GX4000 cartridge | all | 100 × 72 × 14 | unconfirmed |
| CartShells:scv | Super Cassette Vision cartridge | all | 100 × 80 × 16 | unconfirmed |
| CartShells:pv1000 | Casio PV-1000 cartridge | all | 90 × 80 × 16 | unconfirmed |
| CartShells:loopy | Casio Loopy cartridge | all | 110 × 75 × 18 | unconfirmed |
| CartShells:supervision | Watara Supervision cartridge | all | 52 × 60 × 8 | unconfirmed |
| CartShells:megaduck | Mega Duck / Cougar Boy cartridge | all | 62 × 56 × 8 | unconfirmed |
| CartShells:microvision | Microvision game module | all | 95 × 140 × 12 | unconfirmed |
| CartShells:evercade | Evercade cartridge | all | 60 × 88 × 11 | unconfirmed |
| CartShells:gb_camera | Game Boy Camera | all | 57 × 110 × 38 | unconfirmed |
| CartShells:pokemon_pinball | Pokémon Pinball (rumble) | all | 57 × 84 × 18 | unconfirmed |
| CartShells:kirby_tilt | Kirby Tilt 'n' Tumble | all | 57 × 78 × 14 | unconfirmed |
| CartShells:warioware_twisted | WarioWare: Twisted! | all | 57.5 × 48 × 12 | unconfirmed |
| CartShells:boktai | Boktai (solar sensor) | all | 57.5 × 50 × 9 | unconfirmed |
| CartShells:drill_dozer | Drill Dozer (rumble) | all | 57.5 × 48 × 11 | unconfirmed |
| CartShells:yoshi_topsy | Yoshi Topsy-Turvy (tilt) | all | 57.5 × 48 × 11 | unconfirmed |
| CartShells:ereader | Nintendo e-Reader | all | 120 × 72 × 26 | unconfirmed |
| CartShells:np_gb | Nintendo Power GB Memory | all | 57 × 65.5 × 9 | unconfirmed |
| CaseCatalog:NES/US | NES | US | 127 × 180 × 24 | unconfirmed |
| CaseCatalog:NES/EU | NES | EU | 127 × 180 × 24 | unconfirmed |
| CaseCatalog:NES/JP | NES | JP | 127 × 180 × 24 | unconfirmed |
| CaseCatalog:SNES/US | SNES | US | 181 × 127 × 32 | unconfirmed |
| CaseCatalog:SNES/EU | SNES | EU | 181 × 127 × 32 | unconfirmed |
| CaseCatalog:SNES/JP | SNES | JP | 193 × 107 × 33 | unconfirmed |
| CaseCatalog:N64/US | N64 | US | 181 × 127 × 32 | unconfirmed |
| CaseCatalog:N64/EU | N64 | EU | 181 × 127 × 32 | unconfirmed |
| CaseCatalog:N64/JP | N64 | JP | 181 × 127 × 32 | unconfirmed |
| CaseCatalog:GB/US | GB | US | 125 × 125 × 23 | unconfirmed |
| CaseCatalog:GB/EU | GB | EU | 125 × 125 × 23 | unconfirmed |
| CaseCatalog:GB/JP | GB | JP | 90 × 105 × 20 | unconfirmed |
| CaseCatalog:GBC/US | GBC | US | 125 × 125 × 23 | unconfirmed |
| CaseCatalog:GBC/EU | GBC | EU | 125 × 125 × 23 | unconfirmed |
| CaseCatalog:GBC/JP | GBC | JP | 98 × 124 × 22 | unconfirmed |
| CaseCatalog:GBA/US | GBA | US | 125 × 125 × 23 | unconfirmed |
| CaseCatalog:GBA/EU | GBA | EU | 125 × 125 × 23 | unconfirmed |
| CaseCatalog:GBA/JP | GBA | JP | 125 × 125 × 23 | unconfirmed |
| CaseCatalog:VirtualBoy/ALL | VirtualBoy | ALL | 125 × 125 × 23 | unconfirmed |
| CaseCatalog:PokemonMini/ALL | PokemonMini | ALL | 90 × 90 × 20 | unconfirmed |
| CaseCatalog:Genesis/US | Genesis | US | 145 × 210 × 23 | unconfirmed |
| CaseCatalog:Genesis/EU | Genesis | EU | 145 × 210 × 23 | unconfirmed |
| CaseCatalog:Genesis/JP | Genesis | JP | 145 × 210 × 23 | unconfirmed |
| CaseCatalog:MasterSystem/ALL | MasterSystem | ALL | 145 × 210 × 23 | unconfirmed |
| CaseCatalog:32X/ALL | 32X | ALL | 145 × 210 × 23 | unconfirmed |
| CaseCatalog:SegaCD/US | SegaCD | US | 146 × 208 × 23 | unconfirmed |
| CaseCatalog:Saturn/EU | Saturn | EU | 133 × 205 × 16 | unconfirmed |
| CaseCatalog:Saturn/US | Saturn | US | 146 × 208 × 23 | unconfirmed |
| CaseCatalog:GameGear/ALL | GameGear | ALL | 111 × 152 × 24 | unconfirmed |
| CaseCatalog:NeoGeoPocket/US | NeoGeoPocket | US | 128 × 128 × 26 | unconfirmed |
| CaseCatalog:NeoGeoPocket/JP | NeoGeoPocket | JP | 90 × 120 × 25 | unconfirmed |
| CaseCatalog:PCEngine/US | PCEngine | US | 135 × 185 × 18 | unconfirmed |
| CaseCatalog:PCEngine/EU | PCEngine | EU | 135 × 185 × 18 | unconfirmed |
| CaseCatalog:WonderSwan/ALL | WonderSwan | ALL | 90 × 120 × 25 | unconfirmed |
| CaseCatalog:3DO/US | 3DO | US | 152 × 305 × 30 | unconfirmed |
| CaseCatalog:Atari2600/ALL | Atari2600 | ALL | 140 × 192 × 38 | unconfirmed |
| CaseCatalog:Atari7800/ALL | Atari7800 | ALL | 140 × 192 × 38 | unconfirmed |
| CaseCatalog:Lynx/ALL | Lynx | ALL | 140 × 192 × 38 | unconfirmed |
| CaseCatalog:Jaguar/ALL | Jaguar | ALL | 135 × 190 × 45 | unconfirmed |
| CaseCatalog:ColecoVision/ALL | ColecoVision | ALL | 140 × 215 × 25 | unconfirmed |
| CaseCatalog:Intellivision/ALL | Intellivision | ALL | 130 × 190 × 30 | unconfirmed |
| CaseCatalog:Vectrex/ALL | Vectrex | ALL | 140 × 190 × 40 | unconfirmed |