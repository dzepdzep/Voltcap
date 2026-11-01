# Layout visual audit — Update 423 (1 Nov 2026)

All 53 `HomeLayout` values captured on the AYN Thor 3140972f (main screen 1920×1080, release build of this update),
home screen + the window A opens, with the **demo library** on (`--ez voltcap_test_demo true`, QA hooks only): the real
2157-game library with made-up names, Voltcap-drawn covers / wide pictures (`DemoArt`, no words, no real art), no
RetroAchievements / SteamGridDB / Steam ids, no scans, account name "Player". Same games, consoles, order and focus as
the real library. Captures stay outside Git (`.shots/`, ignored); only the 640×360 previews are committed.

Status words: **verified** = seen correct on the Thor in the final capture of this update; **improved** = changed in 423
and verified; **pending** = a known weakness left for later (listed below). Compiling never counts as verified.

## Fixes made from the captures

| Problem seen | Layouts | Fix |
|---|---|---|
| Window keys ran off the window's right edge ("Achievem", "Opti", a half key) | every 421/422 scene window: Grid, Tiles, Hero, Hero tiles, Hub, Live, Library, Dash, Timeline, Twin ×3, Shelf, Stacks, Consoles, Cover details, Crate, Circuit, Discs, Green Core, Cube, Pocket ×3, Spotlight, Art library | `KeyRow` wraps onto more lines (FlowRow, D-pad ▼ reaches them) |
| Wii preview's small keys cut ("S" of Saves) | Channel grid | small keys wrap, centred |
| Status lines cut with "…" (rule 11) | Arcade, Panels-style chip lists | chip text wraps (2–3 lines); Arcade's chip label no longer starts with an unprintable symbol |
| Facts squeezed to their labels (values gone) after the keys wrapped | Twin desk / icons / showcase | art half 0.36, controls half 0.64, keys 30 dp |
| Cover grew over the title | Green Core | cover sized by the room left (height first), never past the row |
| Real covers / RetroAchievements icons in "demo" pictures (Memory card, Storage boxes, Bar, Custom, Discs) | demo only | demo drops RA / SGDB / Steam ids and their icon maps |
| Box empty, shelf with a dark slab (cases waiting forever for a lookup the demo hides; the dressed list was rebuilt on every state change) | Cardboard box, Game shelf (demo only) | dressed list memoised; demo games count as "looked up, no scans" → console spines drawn |
| Cinema / Arctic drawn previews borrowed Hero / Dashboard | picker drawings (PresetPreview) | own drawings |

## Per layout

All 53: home captured, window captured, preview regenerated from the home capture (`lp_<name>.webp`, 640×360, 16:9, no
stretch). "Window" = the window A opens (Classic = the details window by design).

| Layout | Home | Window | Status |
|---|---|---|---|
| Classic (Voltcap row) | row + hero art | details window | verified |
| Tiles | tile row + dock | tile card | improved (keys wrap) |
| Hero carousel | strip + hero | hero banner | improved |
| Grid | grid | tile card | improved |
| Bar | bar + big art | round-key panel | verified |
| Live tiles | tiles | tile panel | improved |
| Cross menu | icons + list | options column | verified |
| Dashboard | widgets | dash card | improved |
| Side menu | drawer + row | drawer window | verified |
| Spotlight | row + hero | poster | improved |
| Console hub | row + tabs | nameplate | improved |
| Resume & discover | resume + discover | discover card | improved |
| Circuit board | PCB rows | datasheet | improved (pending: empty lower pins area) |
| Library panel | tabs + grid | library page | improved |
| Timeline | year line | entry card | improved |
| Console stack | consoles + cases | index card | improved |
| Game shelf | shelves | index card | improved (demo fix) |
| Case stacks | case row | index card + case | improved |
| Disc station | player + disc | player panel | improved |
| Channel grid | channels | Wii preview | improved (keys wrap) |
| Arcade | cabinets | arcade screen | improved (no "…") |
| TV room | TV + shelf | TV screen window | verified (see note) |
| Hero tiles | hero + tiles | hero banner | improved |
| Twin desk / icons / showcase | clock / icons / hero | hinge window | improved (facts back) |
| Record crate | crate | sleeve back | improved |
| Card binder | fan of cards | card back | verified |
| Layout Studio (custom) | user layout | standard window | verified |
| Pocket classic / ribbon / Pocket Quest | handheld screens | LCD window | improved |
| Green Core | globe + menu | core panel | improved (cover over title fixed) |
| Cube menu | cube + row | cube face | improved |
| Memory card | icons | memory card | verified |
| Case flow | case flow | banner window | verified |
| Cardboard box | box of cases | insert sheet | improved (demo fix) |
| Storage boxes | box grid | stored entry | verified |
| File select | save files | file card | verified |
| Orbit deck | discs on orbit | disc + panel | verified |
| Airy | bubbles row | light window | verified |
| Night | slanted list | status window | verified |
| Blades | blades | blade window | verified |
| Bubbles | bubbles | bubble window | verified (pending: large empty right panel) |
| Pocket camera | album | camera screen | verified |
| Panels | panel row | panel window | verified |
| Magazine | cover story | feature page | verified |
| Rental shop | shelves | rental card | verified |
| Terminal | DOS list | terminal window | verified |
| Cinema | billboard + rows | cinema window | verified (preview drawing fixed) |
| Arctic | slate room | overview window | verified (preview drawing fixed) |
| Coverflow details | box + shelf | index card | improved |
| Artwork library | list + art | gallery placard | improved |

## Picker

- `layoutPreviewRes()` maps every `HomeLayout` to its own `lp_<name>.webp` (no `else`, so a new layout does not compile
  without one); the picker draws it at 16:9 (`LayoutPreview`).
- `LayoutPreviews423Test`: a file for every layout, no two layouts sharing bytes, distinct resource ids, every picture
  16:9 (WebP header). `DemoLibrary423Test`: order kept, list stable, nothing real leaks.

## Not done / pending

- Compact screens (Lite, 1280×720 and smaller) and the second screen were NOT captured in this pass (emulator not
  connected; Thor's second screen not captured per layout).
- Bubbles window: big empty right panel; Circuit datasheet: empty lower half with pin dots — visual polish, not broken.
- TV room: the room picture (`tv_room.webp`, your own picture from update 378) shows a plush figure, a red cap and a ball
  that look like known game characters / items; its preview shows the same picture. Worth replacing with an original
  picture before the repository is public.
- The demo's covers repeat (160 designs over 2157 games); fine for previews.

## Update 424 follow-up
- Cardboard box: cases drawn as solid blocks through the box camera (no overlaps, no dim / warm wash) — real library, Thor: verified.
- Bubbles (art + description in the info panel), Circuit (sheet fits its content), index card (Shelf / Stacks / Cover details grow with
  their keys), Box insert sheet (no "…"), Card binder / Twin / Magazine / Cube at 1280×720 — verified on the Thor with real data.
- Still pending: Pocket Quest info line and Cube button hints at 1280×720; per-layout second-screen captures; Lite.
