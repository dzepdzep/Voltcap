# 529o–529q QA evidence — touch→controller, single B, compact pages, bars on Twin / Resume, category strip

Real handheld only: KONKR GT78 (960×640, 240 dpi, Android 12), isolated QA app `com.shelf.launcher.qa`, synthetic QA
fixture library (fixture names / covers / metadata, synthetic achievements), launches blocked (`qa_nolaunch`), preview on
(nothing saved). The owner's QA snapshot was put back after every run (`owner snapshot files back: diff=0`). No emulator
evidence in this folder. Every image is the full 960×640 viewport with the status bar masked (top 36 px painted black, the
viewport keeps its size); `_zoom` files are 2× crops of the window.

×1.3 = QA scale `font=1.3,density=1.15`; ×1.0 = no override. Note: runs sp529o0 / sp529o1 / sp529o2 / sp529p13 / sp529p10
passed the scale as `1.3`, which the hook does not parse - they were ×1.0 and are not used here.

| Files | Build (APK sha256 prefix) | Commit | Run |
|---|---|---|---|
| `x13_*`, `x10_cover_*`, `x10_shelf_*` | `52bef443e52a` | `c21a2c48` | sp529r13 (×1.3), sp529r10 (×1.0), 14 Nov 13:03–13:09 |
| `touch_x10_*` | `4116b9c83983` | `760753f8` | tf529d, 14 Nov 12:23 |
| `x13_twin_*`, `x13_resume_*`, `x10_twin_*`, `x10_resume_*` | `4116b9c83983` | `760753f8` | sp529q4 (×1.3), sp529q4b (×1.0) |
| `strip_settings_legacy_frames_x10.jpg`, `strip_settings_529j_glide_frames_x10.jpg` | `4116b9c83983` | `760753f8` | cs529p (same build, A/B hook `voltcap_test_chipglide legacy/new`) |
| `strip_settings_529q_clipped_frames_x10.jpg` | `52bef443e52a` | `c21a2c48` | cs529q |

Strip files: frames of the screen recording (variable frame rate - a frame is written when the screen changes) between
0.70 s and 1.35 s after the recording started, cropped to the Settings tab strip; the left column is the frame's time in ms.
Sampled frames from a recording: they do not prove continuous motion or GPU pacing.

## Results (kept separate)

**Physical device, interaction (tf529d, ×1.0, Coverflow details):**
- Tap on an achievement row, then R1 → the Options page opened at once (`R1 after the tap reached the window`). Before
  (tf529a, 529m build): 0 key events after the tap. PASS.
- A after a tap: `launches=0`, the window stayed open (it lights the first row). Start after a tap: `launches=0`. PASS.
- Options › Game settings (A) → sheet over the window; one B → back on the window's Options page with Game settings lit;
  L1 then reached the window (2 key events). Before 26949935 the sheet closed the window first (owner report). PASS.
- Game settings › Artwork & details… → the Artwork screen opened; the game window closed under it (as before 529o); B → home. PASS.
- D-pad after a tap: the first press is still taken by Android to leave touch mode (it lights a control, no move). Open.

**Physical device, visual (delegated review only - no owner approval):**
- Shelf-family pages: page keys are bars spanning the card in whole-word rows. ×1.0: one row (Play | Full details / Full
  list | Back) on Game shelf and Coverflow Achievements / Options; Coverflow Details: Play | Full details over a whole-width Back.
- ×1.3 Coverflow: Details shows facts, the achievement line and the first story line with a scroll chevron; Options shows
  Add to favorites and Game settings (sub-text whole) with Music cut under a fade + chevron; Achievements shows the count and
  the order row - **the first achievement row is only a sliver above the fold (FAIL against "the achievement touch target must
  actually be visible"; the tap found it, but it is not a usable row).**
- ×1.3 Game shelf: Details 3 lines + chevron; Achievements order row + half of the first row; Options 2 rows + chevron.
- Order row: "Order: Set order" wraps whole (was cut to "Order: Set").
- Twin desk (single-screen KONKR) and Resume & discover: keys as spanning bars; the round ••• keeps its size and is last
  (Twin: alone on its row; Resume: beside Inspect).
- Status bar: the Coverflow panel's top edge sits ~9 px under the status bar area (masked here); not changed in this round.

**Physical device, motion (category strip, Settings):** chipglide.py on the frame log - legacy POP (one-frame rise 1.00),
529j GLIDE (0.17-0.33, 211-332 moving frames, 22-27 frame gaps > 25 ms). In 529j frames 1385-1450 ms the incoming chip's
word was drawn over "+11 ›"; in 529q it is cut at the span's edge (no overlap). Library and Pocket: POP → GLIDE as well
(cs529p). RP Classic (the owner's video device): NOT TESTED.

**Night menu Full / Full-legacy / Lite (nab529p, gfxinfo, interleaved, two rounds each):** not conclusive - the same
variant differed more between its two rounds (Full nav janky 10.7 % vs 0.0 %) than between variants. No claim either way.
