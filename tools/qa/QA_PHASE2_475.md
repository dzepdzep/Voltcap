# VoltCap QA overhaul — Phase 2, first report (update 475)

Staging only (0.6.68, `next/0.6.68/staging.patch`); v0.6.67, its assets and the tag untouched; nothing published.
Build tested: **qa-0.6.68-475a** — APK 7c4e696d…, main ebc96ea + patch 8d4f8554…, tree 82f9f2b8…, versionCode 73.
Devices (USB): KONKR GT78 BW0306N230000845, Odin 2 Portal b92036fb. Thor / RP Classic NOT TESTED. Both back on the
0.6.66 RC (f79aa2a7), the owner's settings snapshot byte-identical before / after (4 files, md5) on both.

## 1. Staging CI
`next/0.6.68/staging-ci.yml` (now also on `tools/qa/**`) must be copied to `.github/workflows/staging.yml` — the remote
tools may not write `.github`. **No staging CI run exists yet; green is not claimed.** QA tooling checks on main: green
(#130–#132). The staging app's unit tests ran on the PC (`testDebugUnitTest` SUCCESS) on the exact tree built.

## 2. D245–D249 triage (screenshots + layout measurements)
| id | verdict | what / fix | proof |
|---|---|---|---|
| D245 Row opens / Reveal facts | **defect, fixed** | the story claimed the room first; facts and achievements got no height. Facts (achievements count as a fact) first, story in whole lines in the room left | k475v / o475v: 9 + 11 row-style combinations PASS (were FAIL); `RevealFacts475Test` |
| D246 Storage facts | **defect, fixed** | rows after RELEASED had no height, PLAYED 10 of 36 px. Whole rows only (`WholeRows`), all facts stay on Details | k475v / o475v STORAGE_OWN geometry PASS; `WholeRows475Test` |
| D247 Airy story (KONKR) | **deliberate give-way, made explicit** | 8 round keys wrap to 2 rows on the KONKR; no room is left. Story in whole lines or none (`StoryLines`, marked). Recommendation (owner): 10 dp key gap keeps one row | k475v / o475v AIRY_OWN PASS |
| D248 Voltcap row title | **defect, fixed (first-visit case still to re-run)** | first logo of a game after a start got a full-width slot (title x 204), later visits the real width (x 63). Logo shapes kept on disk (hashed address) | k475n2 / k475n3 PASS; `LogoAspects475Test` |
| D249 first press ignored | **QA harness, not the app** | on a fresh start "Who's playing?" is up; the walk's keys went to the picker (app mark PICKER). navclip now closes it per layout and grades a walk begun on it INFRA | d249 trials k475e (moved 0 with PICKER, 12 / 12 after) |

New from the sweeps: **D250** Blades opens the window for another game than the focused one (both devices, two runs);
**D251** popup sweep measured another game after Blades (QA harness, fixed: game re-selected per combination);
**D252** after a touch the first D-pad press is lost (Android touch mode: 0 / 5 moved after a tap, 5 / 5 without) —
owner decision on the fix; **D253** Cross Details page on the KONKR: a 61 dp text column, words broken mid-word
(new warning `warn-narrow-text`).

## 3. Coverage (this report; `tools/sweepcov.py`)
| tier | KONKR | Odin |
|---|---|---|
| T1 popup, OWN style, 53 layouts | **52 PASS, 1 FAIL** (Blades, D250) — k475r1 | **52 PASS, 1 FAIL** (Blades) — o475r1 |
| T1 other 5 styles | NOT TESTED (sweep stopped to restore the device) | BANNER + CLASSIC: **106 / 106 PASS** (o475q2); ROW_OPENS / ROW_REVEAL / SHARED NOT TESTED in the full sweep (22 row-style combinations on 8 layouts PASS in o475v) |
| T5 navigation, 53 layouts | 38 PASS, 11 FAIL, 4 NOT TESTED (k475x1–x4, controller focus settled first) | 38 PASS, 9 FAIL, 6 NOT TESTED (o475w1–w8, All category, before the prelude fix) |
| T2 font / size, T3 content | NOT TESTED (only the update 474 fixture runs on 4–6 layouts) | NOT TESTED |
| T4 pairwise (59 runs) | NOT TESTED | NOT TESTED |
| T6 settings (101 groups) | NOT TESTED | NOT TESTED |

T5 FAILs are triage items, not confirmed defects: most are layouts whose design moves things the walk compares — a row
that doesn't scroll back (Arctic: same game, card at another slot after ▶ ◀), labels that follow the title (Odin's
"Android" / "Other apps" beside names of different length), a CRT-style shift (Terminal 6 px), Camera's frame counter,
Twin desk / Twin icons (dual-screen layouts on single-screen devices). Each needs a per-layout expectation
(`nav_expect.json`) or a confirmed defect — phase 2 continues there. Three KONKR T1 combinations failed once
(Dash / Arctic pixel check, TV Play tap) and passed on the re-run: the pixel check and tap timing are flagged unstable.

## 4. Changes to the QA tools in this update
popclip: game re-selected before every combination (D251); n/a rows left out of the headline verdict. navclip: walk ▶ ◀ ◀ ▶
(row ends handled), first-press-ignored detection, scroll / paging groups, the games' own area, pulse / bounce, vertical
layouts, the profile-picker check, a ◀ ▶ prelude (D252), saved per-layout grades. geomcheck: narrow text column
warning. New: visualpdf.py (the PDF volumes), sweepcov.py (coverage), touchkey.sh, firstvisit.sh, d249.sh. 121 self-tests.

## 5. Visual PDFs
Volumes 1 and 2 of 9 (12 layouts × 2 devices, full-resolution screenshots, evidence, graded results, numbered
annotations, recommendations, coverage of all 53 layouts): `VoltCap_visual_QA_475_vol1.pdf`, `…_vol2.pdf`, original
PNGs in `VoltCap_visual_QA_475_png.zip`. Volumes 3–9 (41 layouts) are next: their T1 / T5 evidence exists, the
per-layout review notes do not yet.

## 6. Limitations
- Staging CI not active (see 1). Odin T5 ran before the controller-focus prelude; its row-end / touch effects are not
  separated. KONKR T5 starts from settled controller focus — first-press behaviour is covered by touchkey / firstvisit.
- T5 compares a game's own texts and its card; labels shared by every game and moving with the title can't be told from
  chrome automatically (they need per-layout expectations).
- Review notes are the reviewer's reading of the screenshots; recommendations are not defects until the owner agrees.
