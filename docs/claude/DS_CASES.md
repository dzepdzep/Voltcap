# Voltcap: Nintendo DS cases and the GBA cartridge holder (update 296)

Code: `src\ui\DsCases.kt` (which case a DS game is drawn in), `src\ui\CaseOpen.kt` (draws the inside).

## The problem and its cause
Toni: "a GBA cartridge holder appears in every DS case". The holder is a moulded recess under the DS card holder. Some
real retail cases had it and some didn't. The cause was the **defaults**: every game without evidence was drawn in its
region's case with the holder:
- Europe and Japan always;
- North America for any game released before 2011, or with no known release year.

So almost every DS game got a holder whether or not its real case had one.

## The rule now
1. **Your choice** (the game's menu › DS case…) always wins. It is shown as "Your choice".
2. Otherwise, the **evidence for that game in that region**:
   - **PRESENT**: the holder is drawn. North America and Japan use the black case. Europe uses the clear case, or the
     black Asian-made PAL case for the games listed in it.
   - **ABSENT**: the region's case without the holder. In North America this is the eco case (recycling cut-outs) for
     games released from 2011 or reported in it; otherwise it is the plain case with only the card holder.
3. Otherwise **UNKNOWN**. This covers no evidence for that region, and games with genuine copies of both kinds. No
   holder is invented. The region's plain case is drawn with only the DS card holder (the documented fallback):
   - North America: plain black, or the eco case from 2011;
   - Europe: the clear case;
   - Japan: the black case.

   The game's menu › DS case… says "Unknown", and any case can still be picked there.
- Evidence names its region and is **never copied to another regional edition**. Example: the EU Pokémon Diamond
  report doesn't give the USA Diamond a holder.
- Variants: the same game in two printings (an early one with the holder, a reprint without) stays UNKNOWN until you
  pick the one you own.
- No leftover holder geometry. A case without the holder draws only the card holder: no recess, clips or mounting.

## Background (general, not per game)
- Wikipedia, *Keep case*: "most" black NA/JP and clear EU DS cases include a GBA slot. This means most, not all, so it
  is not used as a per-game fact. <https://en.wikipedia.org/wiki/Keep_case>
- Nintendo of America's DS games moved to the eco case (no GBA slot) from November 2010. Mario vs. Donkey Kong:
  Mini-Land Mayhem! was reported as the first.
  <https://www.nintendolife.com/forums/ds/ds_gets_its_eco_friendly_box>
  <https://www.cheapassgamer.com/threads/eco-friendly-ds-cases-no-more-gba-slot.246662/>
- PAL clear case: GBA compartment 6 × 2.5 cm. Some PAL games came in a black Asian-made case with a GBA slot, listed by
  name. <https://paderetro.com/2014/03/24/tout-savoir-sur-les-jeux-nintendo-ds-euro/>

## Evidence per game

| Game | Region | Class | How sure | Source | Note |
|---|---|---|---|---|---|
| Mario vs. Donkey Kong: Mini-Land Mayhem! | North America | ABSENT (eco) | Verified (contemporary report) | [Nintendo Life](https://www.nintendolife.com/forums/ds/ds_gets_its_eco_friendly_box) | First DS game in the eco case, Nov 2010. |
| Golden Sun: Dark Dawn | North America | ABSENT (eco) | Reported | [CAG](https://www.cheapassgamer.com/threads/eco-friendly-ds-cases-no-more-gba-slot.246662/) | Buyer, Dec 2010: "came that way". |
| Pokémon Platinum | North America (assumed) | PRESENT | Reported | [Reddit](https://www.reddit.com/r/NintendoDS/comments/1p4aclb/whats_supposed_to_go_here/) | Owner photo of the holder. |
| Animal Crossing: Wild World | North America (assumed) | PRESENT | Reported | [Reddit](https://www.reddit.com/r/NintendoDS/comments/1dk2hqa/) | Owner photo. |
| Shin Megami Tensei: Devil Survivor | North America (assumed) | ABSENT | Reported | [Reddit](https://www.reddit.com/r/nds/comments/vfg30c/) | Collectors. |
| Shin Megami Tensei: Devil Survivor 2 | North America (assumed) | ABSENT | Reported | [Reddit](https://www.reddit.com/r/nds/comments/vfg30c/) | Collectors. |
| Kirby Mass Attack | North America | ABSENT (eco) | Reported | [Reddit](https://www.reddit.com/r/gamecollecting/comments/wyu0o9/) | Later case. |
| Pokémon Black Version 2 | North America | ABSENT (eco) | Reported | [Reddit](https://www.reddit.com/r/nds/comments/17edbew/) | Recycling cut-out. |
| Super Mario 64 DS | North America | UNKNOWN (both kinds) | Reported | [CAG](https://www.cheapassgamer.com/threads/eco-friendly-ds-cases-no-more-gba-slot.246662/) | 2004 printing with the holder; Jan 2011 reprint in the eco case. |
| Mario Kart DS | North America | UNKNOWN (both kinds) | Reported | [Reddit](https://www.reddit.com/r/gamecollecting/comments/vnkr67/) | Early black case with the holder; later reprint without. |
| Kingdom Hearts 358/2 Days | North America (assumed) | UNKNOWN (conflict) | Reported, low | [Reddit](https://www.reddit.com/r/NintendoDS/comments/1ryj5ux/nintendo_ds_game_case/) | One original with the holder, one without (maybe a replacement case). |
| Chrono Trigger (DS) | North America (assumed) | UNKNOWN (conflict) | Reported, low | [Reddit](https://www.reddit.com/r/NintendoDS/comments/1ryj5ux/nintendo_ds_game_case/) | A slotless copy; whether its case is original is uncertain. |
| Pokémon Diamond | Europe (UK) | PRESENT | Reported, low | Toni's lead (no link) | Not confirmed original. |
| Geometry Wars: Galaxy | Europe | PRESENT (black PAL case) | Reported | [Paderetro](https://paderetro.com/2014/03/24/tout-savoir-sur-les-jeux-nintendo-ds-euro/) | Listed (EUR). |
| 42 Jeux Indémodables | Europe (FRA) | PRESENT (black PAL case) | Reported | [Paderetro](https://paderetro.com/2014/03/24/tout-savoir-sur-les-jeux-nintendo-ds-euro/) | Listed. |
| English Training | Europe (FRA) | PRESENT (black PAL case) | Reported | [Paderetro](https://paderetro.com/2014/03/24/tout-savoir-sur-les-jeux-nintendo-ds-euro/) | Listed. |
| Programme d'entraînement cérébral du Dr Kawashima | Europe (FRA) | PRESENT (black PAL case) | Reported | [Paderetro](https://paderetro.com/2014/03/24/tout-savoir-sur-les-jeux-nintendo-ds-euro/) | Listed. |
| Pokémon Mundo Misterioso: Equipo de rescate Azul | Europe (ESP) | PRESENT (black PAL case) | Reported | [Paderetro](https://paderetro.com/2014/03/24/tout-savoir-sur-les-jeux-nintendo-ds-euro/) | Listed. |

**Counts:**
- PRESENT: 8 game/region entries (2 NA, 6 EU).
- ABSENT: 6 games (all NA).
- UNKNOWN with evidence: 4 (2 both-kinds, 2 conflicts).
- Every other DS game in every region is UNKNOWN, so no holder is drawn.

## Limits
- Reddit and Stack Exchange could not be opened from the research session (blocked). The Reddit entries are Toni's
  leads, recorded as "Reported", and their region is assumed North America unless stated. The Stack Exchange lead gave
  no game-level fact that could be checked, so it isn't used.
- Nothing here says the whole DS catalogue is verified. Most games stay UNKNOWN until someone adds evidence.
- To add a game: one `DsCaseEvidence` line in `DsCases.kt` (match text, region, true/false/null, status, URLs, note),
  plus a row here.

## Checked on the AYN Thor (update 296, case look, game window, case opened)
- Pokémon Platinum (USA), PRESENT: black case with the GBA holder.
- Golden Sun: Dark Dawn (USA), ABSENT: eco case with cut-outs, no holder.
- Mario Kart DS (USA), UNKNOWN: plain black case, card holder only, no recess.
- Zoo Tycoon DS (Europe), UNKNOWN: clear case, card holder only.
- Pokémon Diamond (USA), regional check: the EU evidence is not copied, so no holder.

Not checked on a device:
- a Japanese game (none in the library);
- the black PAL case games (not in the library);
- other layouts' closed cases (the inside only shows when a case is opened).
