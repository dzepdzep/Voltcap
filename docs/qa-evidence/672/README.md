# 672 / 673a QA evidence — Case flow and Rental shop pickups, x1.3 popup fixes

Real handhelds only: Retroid Pocket Flip 2 (1080×1920 panel used landscape, 360 dpi, 120 Hz) and KONKR GT78 (960×640,
240 dpi, 60 Hz), isolated QA app `com.shelf.launcher.qa`, synthetic fixture library (fixture names / covers / metadata,
synthetic achievements), launches blocked, preview on (nothing saved). The QA snapshot was put back after every run
(`owner snapshot files back: diff=0`). No emulator evidence here.

×1.3 = QA scale `font=1.3,density=1.15`; ×1.0 = no override. At ×1.3 the KONKR is 556 × 371 dp, the Flip 2 742 × 417 dp.

| Files | Run | Build (tools/qa/builds.json) |
|---|---|---|
| `caseflow_flip2_x13_*`, `shop_flip2_x13_*` | pkf673b_13 | qa-0.6.73-672b |
| `caseflow_konkr_x13_*` | pkk673d_13 | qa-0.6.73-672d |
| `shop_konkr_x13_*_frames` | pkk673e_13 | qa-0.6.73-673a |
| `shop_konkr_x13_keys_BEFORE_673a` | gate k673c (main screen) | qa-0.6.73-672c |
| `shop_konkr_x13_keys_after_673a`, `shop_flip2_x13_keys_after_673a` | gates k673e, f673e | qa-0.6.73-673a |
| `cube_classic_konkr_x13_chip_672c` | pkk673d_13 (home before A) | qa-0.6.73-672d |

Frame sheets: every frame the screen recording wrote between the times in each label (seconds from the recording's start;
A ~0.9 s in, B ~3.1 s after A). Screen recordings write a frame only when the screen changes. **Sampled frames: they do not
prove continuous motion or GPU pacing.** Frame gaps measured on the same recordings (ffprobe timestamps, the open and close
windows): KONKR at most 28–29 ms, Flip 2 Rental shop at most 21 ms; Flip 2 Case flow x1.0 one 159 ms gap at the very start of
the open (pkf673b_10, the window's first frame) — the KONKR recordings of the same build line show none.

## Results (kept separate)

**Physical device, motion (delegated review only — owner visual approval: not yet):**
- Case flow (owner design 14 Nov: "lift the selected case forward out of the flow, rotate it into the popup, return it to its
  exact position; no duplicate"): the focused case leaves the flow, turns edge-on at the window's place and the window turns in
  from its back; B turns it back and lands it in the same slot. One case in every frame examined, Flip 2 and KONKR, ×1.0 and ×1.3.
- Rental shop ("pull the actual case from its shelf; shelf labels stay; the empty slot shows; the case returns naturally"):
  the shelf's case comes off its slot (the slot shows empty, the shelf label stays) and is held beside the tag; the tag swings
  in on its string; B swings the tag away and puts the case back in the same slot. One case in every frame examined.
- Both: a measured start latency shared by every pickup window (D322, open): on the KONKR about 0.10–0.14 s from A to the
  first moving frame.

**Physical device, geometry (popup gate, popclip with the evidence ledger):**
- Rental shop x1.3: before 673a the punched keys cut CATALOGUE CARD / ACHIEVEMENTS (KONKR) and CATALOGUE CARD (Flip 2) — the
  gate passed them as an unconfirmed warning (D321). After 673a: every word whole; KONKR three key rows (the last under the
  key column's scroll fade, the "not set up" line below it scrolls in), Flip 2 two rows. k673e and f673e PASS with the stricter
  checker.
- Cube menu Classic x1.3 (KONKR): the achievement chip reads "80%" whole on the small card (D318). The card itself stays small
  at x1.3 — a design decision for the owner.

**Not covered here:** Odin 2 Portal, RP Classic, Thor (not used: two handhelds at a time); the emulator; the owner's eyes.

## 673b — Disc station and Coverflow details: Inspect lifts the real object (owner design 14 Nov)

| Files | Run | Build |
|---|---|---|
| `discs_flip2_x10_inspect_*`, `coverdetails_flip2_x10_inspect_*` | pkf673k_10 (Flip 2 x1.0) | qa-0.6.73-673b iteration before the KONKR fix |
| `discs_konkr_x13_inspect_*`, `coverdetails_konkr_x13_inspect_*` | pkk673m_13 (KONKR x1.3) | qa-0.6.73-673b |

Each 4-picture sheet: the home screen; the window after A (the object stays where it is); 4.4 s after Inspect (pressed with
`--es voltcap_test_wininspect on`, as its key does); after B. Frame sheets: Inspect's lift and B's return.

**Physical device, motion (delegated review only — owner visual approval: not yet):**
- Disc station: A opens the deck's panel with the disc still turning in its tray. Inspect lifts that disc out of the tray — from
  the angle the tray had turned it to, upright as it rises, a little toward you — and B puts it back into the tray. No frame with
  neither the tray's disc nor the lifted one (brightness of the tray per frame, both devices). Its colours change as it leaves and
  lands: the tray's disc has the station's light sheen, the lifted disc didn't (D326) - fixed in 673c (pkf673o_10: the same sheen
  before, during and after the lift and the landing).
- Coverflow details: A opens the side panel with the case standing where it was. Inspect pulls that case forward from the angle,
  tilt and size it stood at and turns it; B puts it back as it stood.
- With Inspect pressed, the Inspect key leaves the panel, which re-centres by half a row.

**673e, Disc station's LCD at x1.3 (D328):**
- `discs_flip2_x13_lcd_BEFORE_673e.jpg` (f673g, Flip 2, Less | More): an empty third title line where the facts belong; with More
  the story cue was laid out 0 px high (not drawn).
- `discs_x13_lcd_after_673e_flip2_konkr.jpg` (f673i Flip 2 | k673h KONKR, each Less | More): CONSOLE / YEAR / MADE BY / GENRE on
  the LCD, the cue at its end; with More the cue where it fits (Flip 2), left out where it doesn't (KONKR).
