# QA and visual design overhaul — audit and proposed test matrix (update 474, staging after v0.6.67)

v0.6.67 is released and frozen (tag v0.6.67 = 0357637, release + compat APKs). Nothing here changes it. Staging work goes
into `next/0.6.68/` on main (the PC tools commit only to main), exactly as `next/0.6.67/` did.

## 1. Audit of the current QA system

### What exists and is trustworthy
| area | tool | coverage today | evidence quality |
|---|---|---|---|
| Popup / game window geometry (R26) | QaClip dump + `popclip.sh` + `popclip.py` (6 categories) | 53 layouts × 6 styles on KONKR + Odin (318 each, 21 n/a); font 1.3 and display-size subsets (15 layouts); long / sparse names on KONKR (14 layouts) | good: dump + screenshot per step, PASS only when all 6 categories pass |
| Flashes / black frames | `vqa.sh` recordings + `frames.py` | 51 / 53 layouts (S4 runs, Oct 2026) | **stale**: recorded on builds 6–8 weeks old |
| Focus / one-press (R23) | `focuscheck.py`, `onepress_all.sh`, static ONEPRESS | per layout, older builds | partly stale |
| Category stability (R24) | `catswitch.sh` + `catswitch.py`, `CategoryStable394Test` | all layouts once | stale |
| Input contract | `contract462.sh`, `InputContract462Test` | 53 layouts × reserved keys (KONKR) | good, unit test current |
| Settings | `settings_inventory.py` (static) | 1 206 keys: 800 written by Settings UI, 79 by R1; 0 duplicate / 0 load-save mismatch / 15 stale copies | static only: **no device test per setting** |
| High-risk combinations | `combos.json` (35 rows) | 19 PASS, 16 NOT TESTED (9 without any runner) | honest, but small |
| Human visual review | `coverage.json` layout_manual | **9 / 53 layouts** | the rest have no recorded human verdict |
| CI | `qa.yml` (QA self-tests, strict static, coverage) on main | green since #128 | does **not** build or unit-test app code; never sees staging |
| CI app build | `verify-update-310.yml` (Discord stub, testDebugUnitTest + assembleDebug) | only on `claude/**` branches / PRs | not run for main or staging patches |

### Gaps found (each becomes a Phase-1 item)
1. **Text clipped inside its own box** is invisible to the gate (D244 found by eye): QaClip exports boxes only, not text layout.
2. **Zero-height / squeezed nodes** are not checked (too many hidden layers have zero-height text to make a blanket rule).
3. **Gaps / unused space, image crop / stretch / low-resolution art** — not measured at all.
4. **Row / title / art jumping while scrolling** — only R21 SLOT-JUMP statically and old recordings; no per-step position check.
5. **Delayed loading, stale images** — only frames.py on old recordings.
6. **Evidence is not bound to a build**: popclip runs don't record versionCode, APK SHA-256, git commit, device settings; a
   PASS can't be traced to an exact APK (the 471 audit relied on run-name bookkeeping).
7. **Row-style n/a can hide a real bug** (D242/D243): SKIP rows are trusted without checking the style was really offered.
8. **Content fixtures are incomplete**: no "missing artwork" fixture, no portrait / square / ultra-wide art variants, Odin has
   one game only (long titles NOT TESTED there).
9. **Settings**: no device check that a setting does something, nor that changing one leaves others untouched.
10. **QA tools differ between main and the cloud copy** (19 device scripts only in the cloud copy, 14 only on main, 5 QA files
    and 2 release files edited differently) — merge them once, in the staging start.
11. **CI never sees staging code** (only tools on main).

## 2. Proposed test matrix

Literal "every combination" is impossible (≈1 206 settings, 53 layouts, 6 styles, 2+ devices, many contents). The matrix
below says exactly what is run; every run row records build, device, configuration and evidence files.

### Axes
| axis | values | source |
|---|---|---|
| Layout | 53 (`enum HomeLayout`) | code |
| Popup style | OWN, BANNER, CLASSIC, SHARED, ROW_REVEAL, ROW_OPENS (row styles only where offered: 297 applicable / device) | `PopupStyle.offered` |
| Device | KONKR GT78 960×640 240 dpi; Odin 2 Portal 1920×1080 369 dpi; Thor / RP Classic only when on USB | CLAUDE.md |
| Text scale | 1.0, 1.3 (Android max on both), 0.85 | system font_scale (restored) |
| Display size | default, larger (KONKR 280, Odin 420), smaller (KONKR 210, Odin 320) | wm density (restored) |
| Content | normal (Pokémon Stadium 2 / WWE), very long name, sparse metadata, missing art (new safe fixture), wide / portrait / square art | game-select hook + new QA-only art override (session only, never saved) |
| Input | controller (evdev), touch taps, rapid A/B | vqa.sh, popclip.sh |
| States | home focused, popup main / More / page / closed, category switch, scrolling game → game | popclip + new step dumps |

### Tiers
| tier | what | size per device | when |
|---|---|---|---|
| T0 static / unit (CI) | QA self-tests, strict static checks, coverage, **app unit tests on the staging patch** | all | every push |
| T1 popup gate | 53 × 6 at default config, normal content | 318 (297 applicable) | every popup-touching change; full at milestones |
| T2 configuration sweep | 53 layouts × OWN × {font 1.3, font 0.85, larger, smaller} + 6 styles on 8 representative layouts | 212 + 4×48 | milestones |
| T3 content sweep | 53 × OWN × {long, sparse, missing art, wide, portrait} (KONKR); Odin when fixtures allow | 265 | milestones |
| T4 pairwise | covering array over layout family × style × font × density × content × device (every pair at least once) | ≈ 120 extra runs | milestones |
| T5 home navigation | 53 layouts: scroll 6 games each way + category switch: jump / flash / stale / load-time checks | 53 | every layout change |
| T6 settings | every user-settable setting: static (reader, UI, backup, search) + unit persistence; device "set → observe → restore" for the ~240 visual ones (Appearance + R1 + layout options), with a whole-prefs diff to prove nothing else changed | 800 static, ~240 device | staged by group |
| T7 other UI | Settings pages, R1 panel, L1 shortcuts, category screen modes, app drawer, profile picker (6 looks), music player, second screen (Thor) | inventory first | milestones |

A combination counts only with a PASS that names build, device, configuration and files. Not run = NOT TESTED; not offered
= N/A (verified against the app's own `offered` list, not assumed).

## 3. New checks (Phase 1)
| check | how (layout measurement + screenshot) | status rule |
|---|---|---|
| Text cut inside its box | QaClip exports per text: line count, `hasVisualOverflow`, ellipsis, laid-out vs needed height (test-hook only) | overflow in a popup → FAIL |
| Zero-height content | node with text / control, width > 0, height 0, inside the popup's bounds and window, not marked hidden | FAIL |
| Overlap / unreachable / off-screen | existing geometry + focus walk reaching every action | FAIL |
| Excessive gap | largest empty vertical / horizontal band inside a panel vs its content | FINDING (design), never FAIL alone |
| Image fit | QaClip exports image nodes: drawn box, source size, scale mode → crop %, stretch ratio, upscale factor | stretch → FAIL; crop > 25 % / upscale > 2× → FINDING |
| Jumping | focused card / title / art boxes per navigation step; same-slot shift > 2 dp between games | FAIL (R21 / R24) |
| Delayed / stale art | art-ready timestamp per focus change + frames.py on a fresh recording | > 300 ms or wrong art → FAIL |
| Touch / focus | ≥ 24 dp touch, first action focused, focus visible | FAIL |
| Pixel checks | existing cut-button pixel check + a crop check that the screenshot matches the dump | supporting only: a PASS never rests on pixels alone |
| Evidence binding | run manifest: versionName / code, APK SHA-256, git commit, device serial, font, density, layout, style, content, time | missing / mismatched → INFRA, never PASS |

## 4. Plan
- **Phase 1:** merge the two QA tool copies; staging `next/0.6.68`; CI job that applies the staging patch and runs app
  unit tests + QA self-tests; evidence manifest; new checks (QaClip text / image export is test-hook-only app code in
  staging) each with selftest fixtures that fail on the old behaviour.
- **Phase 2:** full T1 + T5 on both devices with the new checks; design review per layout / popup; findings classified
  (defect / strong recommendation / owner decision / not tested); objective defects fixed in staging with tests.
- **Phase 3:** PDF volumes (one per ~6 layouts, full-resolution screenshots, numbered annotations, labels, coverage table,
  priority ranking) + original PNGs.
- **Phase 4:** T2–T4, T6–T7 as far as hardware time allows; everything else listed as NOT TESTED.
- **Deferred features** (NEXT_OVERHAUL.md R1–R8) stay untouched; an implementation order is proposed after Phase 2.

## 5. Not testable now
Second screen / dual-screen lifecycle (Thor not on USB), RP Classic small-screen cases (not on USB), real game launches
(QA blocks launches), Odin long titles until a safe fixture exists, physical controller presses (adb / evdev only).
