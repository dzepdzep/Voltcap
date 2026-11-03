# Update 462b — what each Game Boy family piece is drawn from (KONKR, v0.6.67 staging, Pocket Quest, cartridge look)

Read from the new QA trace (`VoltcapQA mediasrc <game> | <source>`, test hooks only), `tools/qa/device/src462.sh`.

| Game | Source | Note |
|---|---|---|
| Pokémon Silver (official) | REAL cartridge photo | its own ScreenScraper photo |
| Pokémon Crystal (official) | REAL cartridge photo | |
| Pokémon Gold, Monsters Inc., Harry Potter, … (official) | REAL cartridge photo | |
| Pokémon Fool's Gold (hack, Color-only header) | PHOTOSHELL, clear Color-only pak | photo of an official clear pak in the library (jeu 36892), own printed label |
| Pokémon Prism (hack) | PHOTOSHELL, clear Color-only pak | same photo, own label |
| Crystal Kaizo, Gold 97 Reforged (hacks) | PHOTOSHELL, clear Color-only pak | |
| Crystal Clear, Black and White 3 Genesis (hacks) | REAL cartridge photo | their own ScreenScraper entries have a cartridge photo (not the base game's) |
| Pokémon Silver on its drawn shell (QA) | PHOTOSHELL, black dual-mode shape in Silver plastic | Silver's own photo (edition photos first) |
| Pokémon Brown (not on the devices; QA art override on another game) | PHOTOSHELL, grey Game Boy pak | an official grey pak photo, own printed label |
| any, first seconds after the very first start | PROCEDURAL fallback | "photos still being looked at" — now remembered across starts (prefs `photo_shells`) |
| custom models | none on the KONKR's Game Boy games | a picked pack shape logs `CUSTOM MODEL …` and is never replaced |

Fallback reasons the trace reports: setting off · no photo shells for this shell · photos still being looked at · no official
photo of this shell in the library · no solid / see-through photo · nearest photo plastic too far from the colour · photo
loading · photo unusable.

## Accurate vs approximate (turned)
- Front: the real photo's plastic, moulded lettering, ridges, light and edges; only the sticker is replaced. Accurate.
- Sides (45° / 75°): drawn from the front's own outline in the photo's edge plastic — the same method the real photo uses
  (462b: they were the drawn shell's grey). Same as the real piece.
- 90°: a slab of the shell's thickness in that plastic — the real photo is the same slab (no side photos). Approximate for both.
- Back: the researched drawn back in the photo's plastic — the real photo shows the same drawn back (no back photos in the
  library). Approximate for both, identical method.
- Clear paks: every clear pak uses the one colourless clear photo found; a real clear game keeps its own tint (Crystal's blue).
