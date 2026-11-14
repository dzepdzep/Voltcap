# 529s QA evidence — physical pickup / return: Storage boxes, Cube menu, TV room

Real handheld only: KONKR GT78 (960×640, 240 dpi, Android 12), isolated QA app `com.shelf.launcher.qa`, synthetic fixture
library (fixture names / covers / metadata, synthetic achievements), launches blocked, preview on (nothing saved). The
owner's QA snapshot was put back after every run (`owner snapshot files back: diff=0`). No emulator evidence here.

×1.3 = QA scale `font=1.3,density=1.15`; ×1.0 = no override. At ×1.3 the KONKR is 556 dp wide, so Cube menu shows its
Classic look (the Hub look needs 600 dp) — the ×1.0 Cube files are the Hub look.

| Files | Run | Build |
|---|---|---|
| `storage_x10_*`, `tv_x10_*` | pk529s15_10 | QA APK of the commit this folder is in, minus the 529s QA trace lines (behaviour identical) |
| `storage_x13_*`, `tv_x13_*` | pk529s15_13 | same |
| `cube_x10_*`, `cube_x13_*` | pk529s16_10 / pk529s16_13 | the commit this folder is in |

Each file: every frame the screen recording wrote between the times in its left labels (ms from the recording's start;
A was pressed ~0.9 s in, B ~3 s after A), full viewport, status bar masked. Screen recordings write a frame only when the
screen changes. **Sampled frames: they do not prove continuous motion or GPU pacing.**

## Results (kept separate)

**Physical device, motion (delegated review only — owner visual approval: not yet):**
- Storage boxes: the sprite leaves its slot (the slot and its shadow are empty while it is away), travels on an arc onto
  the record's stand while the record switches on round it, and goes back into the same slot on B. One sprite in every
  frame examined, ×1.0 and ×1.3. An earlier build (pk529s10) lost the sprite for one frame at the very end of the return
  — fixed (hand-back frame), not seen since.
- Cube menu: the card leaves the row as it was drawn (frame and achievement badge), turns into the cover on the way into
  the face's picture as the face turns in, and comes back the same way on B. Earlier builds showed either both copies
  for one frame (pk529s14/15) or an empty slot for a few frames (pk529s12/13) — fixed by hiding the home copy on the
  trip's own frames and waiting for the flying copy; pk529s16 shows neither. The face's first frames still stall on the
  GT78 (two of ~60–80 ms); the trip now starts after them.
- TV room: the case leaves the games, travels to the set, flattens into the switch-on line and the picture opens out
  of it; B shrinks the picture back into the line, the case comes out and goes home. ×1.0 and ×1.3.
- `slow` frame counts per layout are in the run logs (KONKR misses frames on these homes even before A; not measured
  against a baseline here — NOT TESTED as a performance claim).

**Physical device, visual (delegated):**
- ×1.3 Cube menu Classic: the Play / Details / Cube pills were cut by the screen's bottom edge (pk529s14_13) — the info
  area now grows with the text size (pk529s16_13: whole). The game card in that row is small at ×1.3 (its badge is cut
  to its first characters) — OPEN.

**Not tested:** Flip 2 / RP Classic for these three; owner approval of any of them.

## Record crate (529t) — Retroid Pocket Flip 2 (1920×1080, 120 Hz), runs pkf529t (before) / pkf529v (after)

`crate_flip2_*`: every 2nd frame written, status bar masked. Before (`_before_`): the window grew out of the sleeve while the
sleeve faded (two things on screen). After: the sleeve itself rises out of the crate as drawn, comes forward, turns edge-on
at its place in the window and its back (the window) turns in; B runs it back and it drops into the crate. One object in
the frames examined, ×1.0 and ×1.3. A first after-build (pkf529u) put the trip and the turn in the first ~130 ms
(LinearOutSlowIn) and stalled ~90 ms at the turn (the back's first drawing) — now an even 720 / 560 ms curve, the back is
drawn edge-on from the start. Owner visual: not yet. KONKR: NOT TESTED (cooldown).
