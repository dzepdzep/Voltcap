# Artwork & generated packaging (update 305)

How Voltcap fits imported artwork onto its physical models, and what it prints on the packaging it generates.
Code: `data/PackagingFacts.kt`, `data/ArtProvenance.kt`, `ui/CollectorBacks.kt`, `ui/OwnSpines.kt`, `ui/BoxSides.kt`,
`ui/ArtFit.kt`, plus the existing `PrintedBacks`, `BoxTexture`, `FullScanCuts`, `PhysicalOverrides`, `ArtworkPickerDialog`.

## Precedence (per surface)
1. **Your picture for that surface** (Artwork & details › a real-media slot: Box, Back, Spine, Disc label, Box right side,
   Box top, Box bottom…).
2. **Your full wrap** (Full case scan) for the front, back and spine you didn't pick separately.
3. **Scans and downloaded artwork** (ScreenScraper, TheGamesDB back, existing artwork matching) — unchanged.
4. **Generated packaging** where nothing above exists and it's switched on (printed backs; box sides "Fill missing").
5. The existing fallbacks (plain plastic / card, the console spine).

A label of yours makes the drawn disc / cartridge wear it: the scraped photo of the real one gives way to it everywhere
(unless you also pick a photo). Clearing a slot goes back to the next item in the list; nothing else is touched.

## Formats handled
| Source | Goes on | How it's fitted |
|---|---|---|
| Front cover / box front | the case's front | cut in the middle to the real case's shape (never stretched); your own front never gets a template band over it |
| Full wrap (back + spine + front) | front, spine, back | spine found automatically or set by hand (Adjust the cuts…); **bleed / border trimmed off the outer edges** (new); each part cut to its face |
| Back | the back | as back scans: eased on up to ~20 % off, cut beyond |
| Spine | the spine | cut to the side's shape (up to 10 % left as is, as spine scans) |
| Cartridge label | the drawn shell's label | fills the label as label scans do; **more than 15 % off its shape it's cut, not squeezed** (new) |
| Disc label | the drawn disc | cut to the round label, the centre hole and the disc's surface kept |
| Box right side / top / bottom | cardboard boxes' faces | a picture lying the other way is turned; within 15 % cut, beyond fitted whole on its edge colour |
| Transparent PNGs | any | drawn over the face's own base (plastic, card, label stock) |

The picture's pixel size never decides its platform or format. When you add one, Voltcap compares its shape with the
surface's real shape (CaseCatalog, the cartridge shells) and says when it was probably made for another case — e.g. a
Universal Game Case wrap on an N64 box — and what it will do. The model never changes to fit the picture.

## Provenance
Adding a picture asks (optionally) where it's from (The Cover Project, my own scan, another site, or typed), whether it's
a scan of real packaging or a custom / fan design, who made it, and what it was made for. Kept on this device
(`art_provenance`, in backups with the other picks). "Box sides › Authentic only" shows only pictures marked as scans.
Pictures are copied into Voltcap's storage (`user_art`) and never uploaded; they don't cloud-sync (only `art_overrides`
web addresses do, as before).

## The Cover Project
No API, and nothing on the site grants automated download (homepage and a list page checked; robots.txt / terms
couldn't be fetched). Voltcap doesn't download from it: Artwork & details has an "Open The Cover Project" button; you
download normally in the browser and add the file with Upload….

## Generated backs
- **Data:** the case back on every 3D case (shelves, carousels, stacks, the game window, Inspect, the second screen)
  now gets what Inspect already had — hero art, up to 3 screenshots, the description — plus verified details.
- **Consoles with a back template** (35, MediaBacks) keep that template's print; nothing (logo, rating) is added on top.
- **Others** (Steam, Android, PS3–PS5, the Xboxes, …) get the collector layout: the platform band (Voltcap's own Steam /
  Android band; for consoles the redrawn logo set Voltcap already ships, else the name), the hero art with the title,
  a screenshot row, the description, a footer with only known details, and "VOLTCAP COLLECTOR'S CASE · NOT AN OFFICIAL
  RETAIL RELEASE" (consoles: "VOLTCAP PRINTED BACK · NOT AN ORIGINAL PACKAGING SCAN").
- **Fallbacks:** no screenshots → bigger hero and text; no description → bigger screenshots; no art → the title large on
  the card. Nothing is invented.

## Ratings
- **Sources:** Steam's store record for the game's app id (`appdetails` → `ratings`: usk / pegi / esrb…, public, no
  key), ScreenScraper's `classifications` for a ROM matched by checksum / name (only when the match is sure).
  Android: no public source → none. IGDB's age ratings weren't added: its current schema couldn't be confirmed.
- **Only valid values** of a known body (USK 0/6/12/16/18, PEGI 3/7/12/16/18, ESRB letters…); Steam's own
  "steam_germany" and generated entries aren't a body's rating and are skipped (an IARC-derived one is kept, marked).
- **Setting:** Game look › Age rating on generated backs: USK (default) / PEGI / ESRB / None, and whether another body's
  is shown when the chosen one has none (it's always labelled with its body).
- **Shown as text** ("USK 16") on a neutral plate — not the bodies' symbols, which are licensed to publishers.
- Facts are asked once, only when a generated back is actually looked at; kept on the device (`packaging_facts`);
  nothing found → asked again after 2 weeks; failures → paused 1 h.

## Steam / Android spines
Generated per game at the template spines' exact shape: Steam navy into charcoal with your template's Steam block, Android
graphite with the robot (CC BY 3.0) and a green line; the title large; a thin accent stripe and foot bar in the cover's
colour. Used by the 3D cases, the Game shelf and the TV room. A real spine scan, your spine pack, your Spine pick and
R1 › Spine still come first.

## Cardboard box sides
Settings › Game look › Box sides: **Fill missing sides** (default: your picture, else a print from the game's title, its
console's logo and its box's colours, with pinstripes), **Authentic only** (only pictures marked as real scans),
**Plain cardboard**. The top's print swings up with the tuck flap. Box sizes, folds and the opening are unchanged.

## Known limits
- No per-surface position / rotation / scale editor and no live 3D preview inside the picker yet (Adjust the cuts… is
  the only manual control); checks happen on the case itself.
- The Switch case's thin edges and the PS / Xbox Blu-ray cases have no top / bottom print (plastic).
- Some box sizes are estimates (CASES.md) — a wrap made for the real box may be cut a little where the estimate is off.
- Front templates in the flat cover looks (not the 3D case) may still frame a mismatched imported front.
- Steam ratings are fetched with `cc=de`; Steam returns the same set for any region as far as checked.
- Rating badges, generated backs and spines are drawn locally; nothing is uploaded.
