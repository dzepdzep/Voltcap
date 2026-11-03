# Popup audit — update 461 (3 Nov 2026), v0.6.67 staging

All **53 layouts × 6 R1 popup styles = 318 combinations**, run on hardware with `tools/qa/device/popaudit461.sh`
(preview only, launches blocked, owner's snapshot saved and restored): KONKR Pocket (960×640, 35 layouts) and
Odin 2 Portal (1920×1080, 18 layouts). Per combination: A opens the popup (screenshot after it settles), B closes it
(window back to home checked), launch attempts and crashes counted, Voltcap in front checked.
Screenshot sheets: `Claude outputs/qa461/evidence/popup_sheet_*.png` (one row per layout, one column per style).

**Legend:** HW ok = opened, readable, B closed, no launch, no crash on hardware · HW FIXED D183 = defect found and fixed,
re-checked on the KONKR after the fix (`popfix461_after.png`) · HW (shows Own) = that style falls back to the layout's
own window (no row to move from) · n/a = the layout ignores the R1 style (its own inline details); `A=Play*` = A plays
the game directly there, by design (the hint bar says so), the launch was blocked by the test hook.

**Not covered by this run (open):** the opening / closing animations (stills only — a recording per style is still
to do), the More button inside each popup, focus order inside the popups, the Thor and RP Classic (not connected),
and code-only review of the 4 n/a layouts' own detail panels.

Custom popups kept unchanged: Wii Channel (CHAN), Cinema, Arctic, Terminal, Camera, Magazine, Shop, File select…
(their OWN column).

| Layout | Device | OWN | BANNER | CLASSIC | ROW_OPENS | ROW_REVEAL | SHARED |
|---|---|---|---|---|---|---|---|
| CLASSIC | KONKR | HW ok | HW ok | HW ok | HW ok | HW ok | HW ok |
| TILES | KONKR | HW ok | HW ok | HW ok | HW ok | HW ok | HW ok |
| HERO | KONKR | HW ok | HW ok | HW ok | HW ok | HW (shows Own) | HW FIXED D183 |
| GRID | KONKR | HW ok | HW ok | HW ok | HW ok | HW FIXED D183 | HW FIXED D183 |
| TV | KONKR | HW ok | HW ok | HW ok | HW ok | HW FIXED D183 | HW FIXED D183 |
| DASH | KONKR | HW ok | HW ok | HW ok | HW ok | HW ok | HW ok |
| LIVE | KONKR | HW ok | HW ok | HW ok | HW ok | HW ok | HW ok |
| SIDE | KONKR | HW ok | HW ok | HW ok | HW ok | HW ok | HW ok |
| CROSS | KONKR | HW ok | HW ok | HW ok | HW ok | HW ok | HW ok |
| SPOT | KONKR | HW ok | HW ok | HW ok | HW ok | HW ok | HW ok |
| BAR | KONKR | HW ok | HW ok | HW ok | HW ok | HW ok | HW ok |
| SHELF | KONKR | HW ok | HW ok | HW ok | HW ok | HW ok | HW ok |
| HUB | KONKR | HW ok | HW ok | HW ok | HW ok | HW ok | HW ok |
| RESUME | KONKR | HW ok | HW ok | HW ok | HW ok | HW ok | HW ok |
| CHAN | KONKR | HW ok | HW ok | HW ok | HW ok | HW ok | HW ok |
| CIRCUIT | KONKR | HW ok | HW ok | HW ok | HW ok | HW ok | HW ok |
| LIBRARY | KONKR | HW ok | HW ok | HW ok | HW ok | HW ok | HW ok |
| STACKS | KONKR | HW ok | HW ok | HW ok | HW ok | HW ok | HW ok |
| TIMELINE | KONKR | HW ok | HW ok | HW ok | HW ok | HW ok | HW ok |
| ARCADE | KONKR | HW ok | HW ok | HW ok | HW ok | HW ok | HW ok |
| CONSOLES | KONKR | HW ok | HW ok | HW ok | HW ok | HW ok | HW ok |
| DISCS | KONKR | HW ok | HW ok | HW ok | HW ok | HW ok | HW ok |
| HERO_TILES | KONKR | HW ok | HW ok | HW ok | HW ok | HW ok | HW ok |
| TWIN_DESK | KONKR | HW ok | HW ok | HW ok | HW ok | HW ok | HW ok |
| TWIN_ICONS | KONKR | HW ok | HW ok | HW ok | HW ok | HW ok | HW ok |
| TWIN_SHOWCASE | KONKR | HW ok | HW ok | HW ok | HW ok | HW ok | HW ok |
| CRATE | KONKR | HW ok | HW ok | HW ok | HW ok | HW ok | HW ok |
| CARDS | KONKR | n/a (own inline) A=Play* | n/a (own inline) | n/a (own inline) | n/a (own inline) | n/a (own inline) | n/a (own inline) |
| CUSTOM | KONKR | HW ok | HW ok | HW ok | HW ok | HW ok | HW ok |
| POCKET_CLASSIC | KONKR | HW ok | HW ok | HW ok | HW (shows Own) | HW (shows Own) | HW ok |
| POCKET_RIBBON | KONKR | HW ok | HW ok | HW ok | HW ok | HW ok | HW ok |
| GREENCORE | KONKR | n/a (own inline) | n/a (own inline) | n/a (own inline) | n/a (own inline) | n/a (own inline) | n/a (own inline) |
| POCKET | KONKR | n/a (own inline) A=Play* | n/a (own inline) | n/a (own inline) | n/a (own inline) | n/a (own inline) | n/a (own inline) |
| CUBE | KONKR | HW ok | HW ok | HW ok | HW ok | HW FIXED D183 | HW FIXED D183 |
| MEMCARD | KONKR | HW ok | HW ok | HW ok | HW ok | HW ok | HW ok |
| CASEFLOW | Odin | HW ok | HW ok | HW ok | HW ok | HW ok | HW ok |
| BOX | Odin | HW ok | HW ok | HW ok | HW ok | HW ok | HW ok |
| STORAGE | Odin | HW ok | HW ok | HW ok | HW ok | HW ok | HW ok |
| FILESELECT | Odin | HW ok | HW ok | HW ok | HW ok | HW FIXED D183 | HW FIXED D183 |
| ORBIT | Odin | HW ok | HW ok | HW ok | HW ok | HW ok | HW ok |
| AIRY | Odin | HW ok | HW ok | HW ok | HW ok | HW ok | HW ok |
| NIGHT | Odin | HW ok | HW ok | HW ok | HW ok | HW ok | HW ok |
| BLADES | Odin | HW ok | HW ok | HW ok | HW ok | HW ok | HW ok |
| BUBBLES | Odin | n/a (own inline) | n/a (own inline) | n/a (own inline) | n/a (own inline) | n/a (own inline) | n/a (own inline) |
| CAMERA | Odin | HW ok | HW ok | HW ok | HW (shows Own) | HW (shows Own) | HW ok |
| PANELS | Odin | HW ok | HW ok | HW ok | HW ok | HW ok | HW ok |
| MAGAZINE | Odin | HW ok | HW ok | HW ok | HW ok | HW ok | HW ok |
| SHOP | Odin | HW ok | HW ok | HW ok | HW ok | HW FIXED D183 | HW FIXED D183 |
| TERMINAL | Odin | HW ok | HW ok | HW ok | HW (shows Own) | HW (shows Own) | HW ok |
| CINEMA | Odin | HW ok | HW ok | HW ok | HW ok | HW ok | HW ok |
| ARCTIC | Odin | HW ok | HW ok | HW ok | HW ok | HW ok | HW ok |
| COVERDETAILS | Odin | HW ok | HW ok | HW ok | HW (shows Own) | HW (shows Own) | HW ok |
| ARTLIB | Odin | HW ok | HW ok | HW ok | HW (shows Own) | HW (shows Own) | HW ok |
