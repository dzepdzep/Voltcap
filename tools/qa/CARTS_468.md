# Update 468 — Game Boy pak sides, clear GBC board, NES front (batch 4), Crystal media check — staging v0.6.67

Owner review of Update 466 (qa466_review.pdf page 4). Rule R25: references first, used for measuring only — **no photo is
bundled or committed**; the comparison sheet with reference thumbnails is private QA output (`Claude outputs/qa468/`).

## References used
| What | Reference (exact URL) | Licence | Used for |
|---|---|---|---|
| GBC Game Pak right / left side, top | https://commons.wikimedia.org/wiki/File:Nintendo_Game_Boy_Color_Game_Pak_Right_Side.jpg , …_Left_Side.jpg , …_Top.jpg (Mitch Mastroni) | CC BY-SA 4.0 | thickness 7.7–7.8 mm (side 80 px / 674 px of 65.5 mm; top 83 / 614 of 57 mm), front half 5.8 / back half 2.0 mm, seam, grip grooves 4–13 mm from the top on the front half, ~0.5 mm edge radius |
| Clear GBC pak, 3/4 | https://commons.wikimedia.org/wiki/File:Nintendo-Game-Boy-Color-Cartridge.jpg (Evan-Amos) | Public domain | pale clear walls with the green board seen through them, gold fingers through the front |
| Pokémon Silver board, chip side | https://commons.wikimedia.org/wiki/File:PokemonSilverBoard.jpg (Simon A. Eugster) | CC BY-SA 3.0 | part places (MBC3 QFP, CR2025 battery 19.8 mm, SRAM, mask ROM, reset IC, 32 kHz crystal, passives), screw hole, 32 gold fingers, notch |
| Pokémon Silver board, solder side | https://commons.wikimedia.org/wiki/File:PokemonSilverBoardBackside.jpg (Simon A. Eugster) | CC BY-SA 3.0 | traces bundle, via rows, test pads TP1–TP4, finger stubs (not gold), no chips |
| Clear GBC back | https://commons.wikimedia.org/wiki/File:GBCartridges_Back.jpg (PiaCarrot) | CC BY-SA 3.0 | board seen through the back (solder side), rear markings (466) |
| NES Game Pak front | https://commons.wikimedia.org/wiki/File:NES-Cartridge.jpg (Evan-Amos) | Public domain | sticker recess 47.8 mm in, 56.8 × 90.7 mm from the top edge; groove column 18.4–44.5 mm, ~24 grooves; arrow; foot 6.9 mm in each side from 109 mm |
| SFC / PAL rear (searched again) | Commons: SF_Memory_Cassette.jpg, SNES_Cartridge_PAL.jpg, Nigel_Mansell's…SNES_cartridge.JPG | — | fronts / connector edge only — **no straight-on rear found: SFC/PAL back stays BLOCKED** |

## What changed (staging)
- **Inspect sides of the Game Boy / black Color / clear Color paks** (`ui/MediaInspectView.kt` SlabSide): measured thickness
  (clear pak 9 → 7.8 mm), rounded edges (0.5 mm), the seam between the halves, the grip grooves on the front half's sides.
  The face is never stretched onto the side (sides are plastic only; the face fades out over the last degrees, 458).
- **Clear pak:** walls see-through as one sheet of clear plastic (overlapping slices no longer add up to an opaque grey wall);
  edge-on the wall fills out so the side never turns into a comb or vanishes. Inside: the board (1 mm, lying on the back
  half) and the parts standing on its label side, at their depth — seen through the side at 60–105°.
- **Board** (`ui/GbBoard.kt`, new): drawn from the Silver photos — chip side on the drawn front (under the label, which
  hides the chips; gold fingers, battery top, traces and vias round it), solder side on the back (traces, vias, test pads,
  finger stubs, screw hole, no chips). Parts follow the game's ROM header cartridge type (0x0147, read with the label facts —
  `data/LabelIds.kt`): no SRAM / battery without save RAM, crystal only with the MBC3 timer, no mapper for ROM-only — so not one
  board for every game. **Verified:** the MBC3 + timer + RAM + battery board (Gold / Silver / Crystal). **APPROXIMATE:**
  other types (their own MBC1 / MBC5 board layouts are not photographed — the same places with parts left out).
- **Real photos unchanged:** a game's own cartridge photo stays its front; the photo-made front (PhotoShells, 462) still
  shows the photo's own board. Only the sides / inside are added.
- **NES Game Pak front** (`ui/CartShells.kt`, batch 4): sticker upright on the right (55 × 90 on the front, 7 mm over the top),
  groove column left, arrow, connector foot. Back not re-verified (unchanged).

## Pokémon Crystal: box in the RC, clear cart in staging — cause
Not a media-selection change. Page 4's Crystal row was taken with the test hook's `media` shell (update 462: "the piece the
cartridge-and-disc look draws"). The RC (0.6.66) has no `media` shell, so its hook fell back to the normal Inspect piece —
the game's case (the real box). Staging drew the real cartridge photo as asked. **Checked on the KONKR (staging):** the normal
Inspect of Pokémon Crystal Version (no forced shell) shows the real box, as in the RC (`q468b/cv_none_*`). Nothing replaces
a real box with a generated cartridge or the other way round; real scans keep precedence (RealScans464Test).

## Verified on hardware (KONKR GT78 960×640, USB)
Rotation 0 / 15 / 30 / 45 / 60 / 75 / 85 / 90 / 105 / 135 / 180 (sweeps q468a, q468b, q468c; `cart468.sh`): clear pak with photo-made
front (Harvest Moon 3) and drawn front (photo shells off), Pokémon Crystal Version's real photo, black and grey paks, GBA
(unchanged), NES. Sheet: `Claude outputs/qa468/gbc_rotation_468.png` (RC row, staging rows, reference thumbnails).

## Open / not done
- D218 GBA side not measured (plain wall). D219 home-screen turned pieces (SolidPiece) keep the old opaque sides — the board
  face is shared, the measured sides are Inspect only.
- The drawn traces are simplified (kind and density, not copied); the real solder side is darker and denser.
- Gold contacts drawn through the back opening (466 data) are kept; the solder side's finger stubs are green.
- SFC / PAL back BLOCKED (no straight-on rear photo). Famicom: Jaleco back only (unchanged).
- Odin 2 Portal not re-swept for this update.
