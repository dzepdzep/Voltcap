# Update 466 — cartridge backs from photo references (batch 3) + Pokémon Snap front check

Rule R25: references first, used only for measuring and comparing — **no third-party photo is bundled in the app or committed
to the repo**. Comparison sheets with reference thumbnails are kept in `Claude outputs/qa466/` (private QA output, not in git).

## References used
| Model | Reference (exact URL) | Licence | Views it covers | Used for |
|---|---|---|---|---|
| Clear GBC Game Pak (CGB-only) back | https://commons.wikimedia.org/wiki/File:GBCartridges_Back.jpg (PiaCarrot) | CC BY-SA 3.0 | back, straight on | Nintendo oval, PAT.PEND. / MADE IN JAPAN, dimple, screw, board extent |
| Clear GBC Game Pak front | https://commons.wikimedia.org/wiki/File:Nintendo-Game-Boy-Color-Cartridge.jpg (Evan-Amos) | Public domain | front, 3/4 | board visible round the label, contacts through the plastic |
| Black dual-mode GBC Game Pak (check only) | https://commons.wikimedia.org/wiki/File:Nintendo_Game_Boy_Color_Game_Pak_Back.jpg (Mitch Mastroni, GAMECIP) + Top / Bottom / Left / Right Side files of the same series | CC BY-SA 4.0 | back, top, bottom, both sides | confirmed the existing gbc_dual back (text box, two ribs, dimple, screw, notch) — unchanged |
| Pokémon Silver board (check only) | https://commons.wikimedia.org/wiki/File:PokemonSilverBoard.jpg , ...BoardBackside.jpg (Simon A. Eugster) | CC BY-SA 3.0 | PCB both sides | chips on the board's label side, traces only behind |
| Super Famicom / PAL (low angle) | https://commons.wikimedia.org/wiki/File:SNES_Cartridge_Comparison_bottom.jpg (Bill Bertram, Anomie) | CC BY-SA 2.5 | PAL and NA cassettes from the connector edge | which face it shows is unclear — not used |
| Super Famicom front | https://commons.wikimedia.org/wiki/File:Super-Famicom-Angelique-1994-Cartridge.jpg | Public domain | front, straight on | screws are on the front (bottom corners) |
| Super Famicom shell | https://commons.wikimedia.org/wiki/File:SNES-SFAM-Cartridges.jpg (Evan-Amos) | Public domain | SFC from behind, 3/4 | rounded ends, open connector, screw near the connector end |
| Famicom (Nintendo HVC) front | https://commons.wikimedia.org/wiki/File:Famicom_%27Devil_World%27_cartridge_(5158740395).jpg (Bryan Ochalla) | CC BY-SA 2.0 | front, straight on | connector tab 7.1 mm in each side, 9 mm tall; body 62 mm; no grip lines across the foot |
| Famicom (Jaleco) back | https://www.flickr.com/photos/bochalla/24084609544/ (Bryan Ochalla) | CC BY-SA 2.0 | back, slight angle | how-to-play sticker (≈4.5 mm in, 3 mm from top, 52 mm tall), "MADE IN JAPAN" under it, no screws, tab |
| Pokémon Snap US box | https://gamefaqs.gamespot.com/n64/198311-pokemon-snap/boxes/42710 (owner's link) | site's own (viewing only) | front, back, side, top scans | front fit comparison; real side/top colours |

## What changed (staging)
- **Clear GBC back** (`CartBacks` gbc, `CartShells.back/front`): the black pak's "MADE IN JAPAN / PAT.PEND." box at the top
  replaced by the clear pak's own markings (raised Nintendo oval, the two text lines lower down); the board now fills the shell
  as on the photo (2.6 mm in from the sides, 3 mm from the top, down to the contacts) on back *and* front, and the chip block
  drawn on the back is gone (the chips sit on the board's label side). Dimple, screw, contacts unchanged (already matched).
  Not drawn: the board's traces (approximate: plain board), the unreadable "MODEL NO." line.
- **Super Famicom / PAL back: NOT changed (BLOCKED).** A first version added two screws and a sticker to the back from the
  low-angle PAL photo; a straight-on public-domain front photo (https://commons.wikimedia.org/wiki/File:Super-Famicom-Angelique-1994-Cartridge.jpg,
  Nintendo/Koei, public domain) shows the two screws on the FRONT's bottom corners, so the low-angle photo may show the front.
  Reverted before commit; needs a straight-on rear photo. The staging SFC front (photo-made, 463) already shows the screws and
  lower panel like that photo.
- **Famicom** (`CartShells` famicom + `CartBacks` famicom): outline now has the real connector tab (front, sides and back);
  the five grip lines across the foot removed (no photo shows them); back = sticker area + "MADE IN JAPAN", no screws.
  PARTIAL: one maker (Jaleco) photographed from behind; Konami / Namco / Hudson / Bandai shells and backs NOT verified —
  manufacturer variants not modelled (BLOCKED until references are found).
- **Not changed:** DS design; Silver / Gold real photos; Crystal's real front photo (its drawn back is the clear GBC back, so
  it gets the new markings and board — shown before/after in the sheet); custom models; NES (batch 4).

## Pokémon Snap front (owner's Case flow screenshot)
- Genuine US front (GameFAQs): red background, logo top-left, red N64 band + Nintendo 64 logo on the right — **no beige**.
- RC v0.6.66 Case flow: Voltcap's N64 cover template (red band, seal, ESRB) around a *portrait* artwork, letterboxed with beige
  on both sides, i.e. the generated cover — the real scan was not picked (D173, the RC's duplicate-copy bug).
- Staging (D173 fixed in 460b): the real ScreenScraper US scan is the front; matches the GameFAQs reference. Nothing else to fix
  in the fit (see the sheet). The beige letterbox only appears on generated covers when the art is portrait (design choice, open).

## Box side colours (owner: Snap / Stadium / Stadium 2 / Paper Mario all red?)
Analysis of the exact front pictures the 3D box gets (QA dump `qa_boxcolour/`), candidate colours per cover (share of weighted samples):
| Box | 1st | 2nd | 3rd | Verdict |
|---|---|---|---|---|
| Pokémon Snap | red 0.29 (background) | yellow | black | red is the cover's own background — kept |
| Pokémon Stadium | red 0.18 (arena + logo) | orange | maroon | red genuine — kept |
| Pokémon Stadium 2 | red 0.13 (mostly the STADIUM logo, Ho-oh) | near-black indigo 0.10 (background) | yellow | red partly logo; the real alternative is near-black indigo — owner choice, not forced |
| Paper Mario | near-black 0.085 (outlines!) | red 0.049 | yellow 0.047 | the 465 pick took the drawn outlines — fixed: dark thin strokes no longer count; now red (Mario / title) |
Fix (BoxSides.boxColour): a dark sample (max < 64) counts only as much as its 3×3 neighbours share its colour. Checked on all 22
dumped covers: only Paper Mario changes (near-black → red). Tried and rejected (made other boxes worse — Gold → red/olive,
Harvest Moon → green, Crystal → logo yellow): flat-region weighting, edge-ring weighting, darker-pixel discounts.
The real Snap box's own side scans (GameFAQs) are tan/red (side) and purple (top) — designed panels, not one colour; your own
side picture (Artwork & details › Box right side / top / bottom) still wins over the colour.
