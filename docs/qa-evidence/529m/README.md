# 529m QA evidence — shelf family, Coverflow details action panel (owner correction #15)

Real handheld, isolated QA app only (`com.shelf.launcher.qa`), synthetic QA fixture library (names / covers of the QA
fixture set, synthetic achievements), launches blocked, owner QA snapshot restored byte-identical after every run.
Device: KONKR GT78, 960×640, 240 dpi, Android 12. Status bar cropped from every image. This is **real-handheld** evidence,
not emulator evidence.

| Build | APK sha256 (prefix) | Source |
|---|---|---|
| 529l (before) | `91083e40aa30` | `b14d4021` |
| 529m (after) | `ef935a157f70` | `b14d4021` + 529m working tree = the commit that adds this folder |

Text scale ×1.3 = the app's QA scale `font=1.3, density=1.15`; ×1.0 = no override. Layout style OWN (the layout's own window).

## Files

| File | What it shows | Input sequence |
|---|---|---|
| `coverdetails_panel_before_after.jpg` | Coverflow details Info panel, before (529l) / after (529m), ×1.3 More closed / open, and 529m at ×1.0 | A opens; ••• (More) |
| `pages_x13.jpg` | Details / Achievements / Options at ×1.3 — Coverflow details and Game shelf (529m) | A, R1, R1, R1 |
| `touch_then_controller_x13.jpg` | Touch then controller at ×1.3 (529m) | Game shelf: R1 R1, tap achievement row, R1, L1×3 · Coverflow: R1×3, tap "Game settings", B, L1×3 |
| `frames_shelf_info_to_details_BEFORE_529l.jpg` | Info → Details frames before the fix | R1 |
| `frames_shelf_info_to_details_x13.jpg`, `frames_cover_info_to_details_x13.jpg` | Info → Details frames, 529m (ms from just before the press) | R1 |
| `frames_shelf_details_to_achievements_x13.jpg` | Details → Achievements frames, 529m | R1 |
| `frames_shelf_motionoff_x13.jpg` | Motion Off: the same press is immediate | R1 |

## Results (separately: source / geometry / interaction / visual / motion)

- **Geometry (popup gate popclip.py)**: Coverflow details ×1.3 PASS (run k529m1), ×1.0 PASS (k529m2). Before: ×1.3 FAIL
  (k529k3: the two info lines laid out at 0 px with More open).
- **Visual (delegated review only — no owner approval)**: one action per full-width bar, same height, icons in one column;
  save status is a separate full-width line "Save sync is not set up"; ••• last. The home's console · year line no longer
  crosses the case header at ×1.3. With More open at ×1.3 the panel holds only the actions (the name and console · year stay
  visible on the home beside it; the facts are on Details) — the deliberate More mechanism; at ×1.0 the two fact lines stay.
- **Motion (recording, frame review)**: Info → Details now moves in all four shelf layouts (~280–300 ms, 15–18 frames; before:
  one-frame swap). The leaving Info card keeps its own content (before: it showed the Details body with a second cover).
  No geometry jump at the first frame (Back hint keeps its room on pages). Motion Off: ≤ 2 frames.
- **Interaction — FAIL / open**:
  - After a **tap** on an Achievements row, R1 / L1 do nothing until B (Game shelf, also before 529m). The 529m attempt
    (window view focusable in touch mode) did **not** fix it — still open.
  - Coverflow details: tap "Game settings" opens its sheet; one B closes the sheet **and** the game window; the following L1
    then opened the home's Shortcuts panel (cell 8). Needs a decision / fix — open.
  - Coverflow details pages at ×1.3 show only one or two rows of the page (Details / Achievements) and the page keys wrap
    with Back alone — not acceptable yet — open.
- Rapid reopen (A B ×3): 0 launches (all runs). Crashes: 0.
