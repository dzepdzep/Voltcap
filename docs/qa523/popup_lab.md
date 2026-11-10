# Popup design lab (Batch 523m/n) — options for review, nothing switched on

Owner (10 Nov): typography too bold and cramped; Play should be the clear primary, Details secondary, More a small round
"•••" beside Details, Back out of the action group as a subtle controller hint; better spacing and console-badge placement,
especially in Storage boxes; each style keeps its identity. **Explore and compare — no global redesign or font change
before review.**

## How it was built (QA app only)
`ui/PopupLab.kt`, switched on per run with `--es voltcap_test_popuplab "font=current|inter|manrope,weights=on,actions=on,storage=on"`
in the isolated QA app (never saved; `off` ends it). Without the hook every popup is exactly as before (unit tests unchanged
apart from PopupMore451Test following the shared More key).
- **font** — the app's typography family swapped for the run: the device font (Roboto), Inter or Manrope. Inter (Debian
  fonts-inter) and Manrope (google/fonts, variable → static 400/500/600/700) are SIL OFL 1.1, subset to Latin + Latin
  Extended + punctuation, in `app/src/qa/assets/qa_fonts` with their licences — the debug/QA build's assets only, **not in
  the release APK**.
- **weights** — one step lighter everywhere without touching a screen: Bold is drawn SemiBold, SemiBold Medium, Black Bold
  (for Roboto: Regular + Medium faces, weight synthesis off).
- **actions** — the generic scenes' action row: Play the only filled key, Details outlined (secondary), More a round "•••" key
  beside Details (Less: the same key, lit edge), Back leaves the row and becomes a "B Back" hint on a small dark pill in the
  window's corner (still tappable; B on the controller unchanged).
- **storage** — Storage boxes: Play + Summary (+ ••• where the window has More) on one line, more air between rows and in the
  menu, the console tag on the picture's corner instead of under the name.

## Evidence (GT78 / KONKR Pocket Advance, 960×640, QA app, stand-in library, `lab523.sh`)
Five variants × eight popups: Voltcap row (own scene), Tile home, Storage boxes, Hero, Spotlight, and the Row reveal /
Shared / Banner styles on the Voltcap row — `Claude outputs\b523\lab523m.tar`; contact sheets in the Batch 523 evidence page.

| Variant | What it shows |
|---|---|
| base | today |
| current_w | Roboto, one step lighter — titles and keys calmer, same metrics: nothing reflows |
| current_wa | + Play / Details / ••• hierarchy, Back as a hint |
| inter_wa | Inter, one step lighter + hierarchy — the crispest at 12–15 sp; **wider**: the Banner style's tab row lost "Options" to its "+1" overflow on the GT78 |
| manrope_wa | Manrope — rounder and friendlier, more open spacing; small caps labels ("CONSOLE", "PLAYED") less distinct at 9 sp |

Found while comparing (fixed in 523n, also without the lab): Storage boxes' menu broke "ACHIEVEMENTS" inside the word on the
GT78 (three cells a row in a narrow column) — cells are now counted from the menu's own width. The lab's Back hint
vanished on Storage's light panel — it now sits on a dark pill.

## Recommendation
1. **Ship first (low risk):** the action hierarchy (Play / Details / •••, Back as a hint) and one-step-lighter weights on
   the current font. It answers "too bold" and "Play should be primary" with no metric change, no bundled font, and every
   style keeps its own shape, colours and fonts (pixel / monospace styles untouched).
2. **Then, if wanted: Inter** for the generic popups (not the styles with their own type). Better small-size legibility than
   Roboto, but it is wider: it needs its own 318-combination popup gate at ×1.0 and ×1.3 before it is switched on (Banner's tab
   row already overflowed on the GT78), and ~1.4 MB of font in the APK (4 weights, subset).
3. **Manrope: not recommended** for these dense popups (rounder, less distinct small labels); fine for large titles only.
4. **Storage boxes:** take the lab layout (console tag on the picture, Play + Summary on one line, more air).

Nothing of this is on for anyone until you pick an option.
