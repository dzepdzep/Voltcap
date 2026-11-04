# Update 469 — home-screen Game Boy pak sides = Inspect's; GBA side BLOCKED — staging v0.6.67

Builds on Update 468 (tools/qa/CARTS_468.md: references, measurements, the clear pak's board). R25: references used for
measuring only, nothing bundled.

## What changed (staging)
- **Home screen's turned cartridges** (`ui/SolidPiece.kt`: SolidSpec.gb / GbSide, same constants as Inspect's SlabSide):
  - Game Boy, black Color and clear Color paks (drawn shell, photo-made shell, the game's own photo of that shell) get the
    seam between the halves (5.8 mm from the front) as one darker copy of the side.
  - The clear pak's sides are clear plastic on the home screen too — the photo-made clear pak had opaque sides in the photo's
    edge colour — with the board (1 mm, on the back half) seen through the side at its depth.
  - Edge-on (past ~81°) the clear side is filled as see-through plastic (it vanished to the board's green line).
  - From behind, the clear pak's back stays opaque (its drawn back is the board under the plastic; drawing it 75 % let
    the background show through the board).
- **Not ported to the home screen:** grip grooves and the 0.5 mm edge rounding (at home sizes, 100–200 px tall, they are
  1–2 px — not visible; they would cost extra draws per piece on the performance-tuned path). Inspect keeps them.
- **Unchanged:** real cartridge photos (their face), your picks and custom models / pack shapes (GbSide only for the drawn
  Game Boy family shells, never a pack shape), every other console's pieces, Stadium 2 box sides, saved box-side choices.

## GBA pak side (owner: "investigate … do not invent")
Searched Wikimedia Commons: Nintendo-Game-Boy-Advance-Cartridge.jpg (Evan-Amos, PD — front 3/4), GBA_Cartridge_Types.jpg
(Thomy pc, CC0 — opened boards), Simcity2000_advance_cartridge_by_zeartul.jpg (PD — front), Game Boy Advance game
cartridges – rear view (CC BY-SA 4.0 — rear). **No side or top view.** Wikipedia's Game Boy Game Pak infobox gives the
Advance pak "6–9 mm thick" without a citation (and the Color pak 9 mm, also uncited — 468 measured the black Color pak at
7.7–7.8 mm from photos; the clear pak is taken as the same mould, not measured itself). **GBA side: BLOCKED** — the drawn
shell keeps its 8 mm plain side; no seam / grips added.

## Verified on hardware
KONKR GT78 (USB), final staging build, Classic row + Cartridge / disc look, `home469.sh` held poses 0 / 30 / 45 / 60 / 75 /
85 / 90 / 105 / 135 / 180° (`Claude outputs/qa469/q469h4`): clear Harvest Moon 3 (photo-made), clear Looney Tunes Alert!,
pink Kirby Tilt 'n' Tumble, black Looney Tunes Twouble! in the same row.
- **Front (0–45°):** labels on the front only; the side's plastic shows from ~30°.
- **75°:** the side as a strip in the shell's plastic, the seam a darker line along it; clear paks paler than the black one.
- **85–90°:** the clear pak a thin translucent wall with the board's line inside it; the black pak a solid black wall —
  no vanishing, no comb.
- **105–180°:** the back: the clear pak's back opaque (board under the plastic), black / pink backs plain.
- A first capture (q469h3) found the board and seam drawn **apart** from the pak at 75–90° (D224, recorded in the piece
  buffer at the wrong size) — fixed and recaptured (q469h4).
- **Shortcomings:** at home sizes (≈100–200 px tall) the 1 mm board is a 1–2 px line — the traces are not readable there
  (they are in Inspect); grip grooves / edge rounding are Inspect-only.
- **Performance (KONKR, gfxinfo, 24 D-pad steps through the Game Boy Color row):** 546 frames, 1.65 % janky, p50 14 ms,
  p90 19 ms, p95 22 ms (under the 16.7 ms budget at p50; same order as 468's row). An RC comparison was attempted but the
  RC has no look / game test hooks, so it measured another screen — **not a like-for-like RC number**.
RC at the same turns: NOT captured — the RC has no hold-a-turn test hook (its home sides are the old opaque strips, 468).
Odin 2 Portal: no Game Boy games in its library — home pieces NOT TESTED there.
Unchanged and checked by test: real scans keep precedence (RealScans464Test), Stadium 2 box sides (BoxSidesLabels465Test).
