# VoltCap QA overhaul — Phase 1 completion report (update 474)

Scope: QA infrastructure only. App changes are QA-only code in **staging 0.6.68** (`next/0.6.68/staging.patch`), inert without
the QA hooks file. Main / v0.6.67 release code, release assets, the GitHub tag and the owner's settings untouched. Nothing
published. Devices back on the 0.6.66 RC (f79aa2a7…, versionCode 73) with the owner's settings restored and verified.

## 1. What was built
| area | tool | what it does |
|---|---|---|
| Merge | `tools/qa/**` (c2fab85) | main's and the cloud copy's QA tools merged: 18 cloud-only device scripts kept, newer cloud docs / scripts taken, main's freeze checklist and fixture kept |
| CI | `next/0.6.68/staging-ci.yml` → to be copied to `.github/workflows/staging.yml` (Claude's remote tools can't write `.github`) | on main: applies `next/0.6.68/staging.patch` to a clean checkout, compiles (Discord stub), runs the JVM unit tests, QA self-tests, strict static checks; prints main's commit, the patch SHA-256 and the tested tree hash |
| Traceability | `device/evidence.sh`, `tools/evidence.py`, `tools/srchash.py`, `builds.json` | every run's manifest: APK SHA-256 + stamped revision, versionName / Code, serial, model, display / density (+ overrides), font scale, animator scale, layouts / styles / game / content / category, script SHA-256, start / finish. APK → build record (base commit, patch SHA-256, tree hash). Missing / interrupted / unknown APK / wrong versionCode / stale file → **INFRA**; `--legacy` old runs: PASS → NOT TESTED |
| Popup gate | `geomcheck.py`, `popclip.py` | new: **squeezed** (text / control with width but no height), **text in its own box** (QaClip `TextLayoutResult`: overflow / maxLines stop / ellipsis), **window shows another game**, run on another game = INFRA, D242 "n/a" needs the app's style confirmation |
| Navigation T5 | `device/navclip.sh`, `tools/navclip.py`, `nav_expect.json` | per layout RIGHT RIGHT LEFT LEFT (or DOWN / UP): **move**, **jump** (same game same places), **steady** (fixed texts), **art** (same game same picture; neighbour ≠ last picture), **late** (early raw shot, Coil load times, > 2x upscale: WARN only) |
| Art fit | `tools/artfit.py` | QA fixture art (frame + circle): stretched > 8 % = FAIL; crop reported, never failed (artistic crop) |
| Fixtures | app `QaFixture.kt`, `QaArt.kt`, `QaClip.kt` (staging) | `--es voltcap_test_fixture "names=long|short, art=none|wide|square|portrait|ultrawide|lowres, meta=none"`: display-only, never saved; names keep the alphabetical order; art in the app cache; QaArt logs hashed tokens only (never addresses) |
| T4 | `tools/pairwise.py`, `pairwise_model.json` | 59 runs cover all 335 coverable pairs (family × style × font × density × content × device); 0 uncovered, 0 excluded; check fails on a missing pair or a style a layout doesn't offer |
| T6 | `tools/t6plan.py`, `tools/snapdiff.py` | 792 settable settings in 101 groups; 414 never set by QA (no UI / account / device-power); before / after snapshot diff (key names only, values never printed) |
| Self-tests | `selftest/test_qa.py` | **117** (98 before): every new check has a broken fixture that must FAIL / INFRA and a good one that must PASS, incl. the real k471cd / k471gd Cover details dumps and two real Odin fixture screenshots |

## 2. Old vs new — the same screens
| screen / run | old gate | new gate | truth (screenshot) |
|---|---|---|---|
| Cover details · More, KONKR 280 dpi (k471cd, D244) | PASS | **FAIL** squeezed: "Never played · 0 / 109 trophies" 327 × 0 px | line missing — defect (fixed in 0.6.67) |
| same, fixed (k471gd) | PASS | PASS squeezed; text NOT TESTED (0.6.66-era dump) | whole |
| update 471 T1 sweep, KONKR 318 combos | 0 squeezed failures | **19 FAIL**: Row opens / Reveal on 17 layouts (D245), Storage (D246), Airy (D247) | facts / story missing |
| update 471 T1 sweep, Odin 318 combos | 0 | **7 FAIL** (D245, D246) | same |
| k474a / o474b (staging build, 5 layouts × 2 styles) | — | D245 / D246 / D247 reproduced on hardware; Cover details, Classic, Dash OWN PASS | as graded |
| o474a: run asked for "WWE", home was on the RetroArch app | buttons FAIL ("launched another game") | **INFRA** (this content was not tested) | wrong content, not an app defect |
| k474f: long-name fixture (build a) | — | **FAIL** open: window showed Paper Mario while home was on Pokémon Stadium 2 | fixture names re-sorted the list → fixed (build b keeps the order); k474g same runs PASS |
| k474n: navclip sent KEYCODE_RIGHT (no such key) | — | first: 6 FAIL "stuck"; after the run-level rule: **INFRA** keys not delivered | the focus never moved; only a clip played |
| k474p / o474q (fixture build b) | — | art **FAIL** "next game kept the same picture" | neighbours really shared a fixture picture → fixed (build c); o474r PASS |
| k474r Voltcap row (real art) | — | jump **FAIL**: title at x 204 on arrival, 63 after the walk (D248) | title really elsewhere (intermittent) |
| artfit: 7 layouts × 2 devices, wide / square / portrait fixture art | — | PASS (none stretched; crops listed); real Odin shots squeezed 25 % → FAIL | as graded |

False positives found and removed during the phase (each now has a self-test): auto-sized text reports its unshrunk
layout (Compose `GetTextLayoutResult`) → untrusted overflow is a WARN unless the box is under half the line; a long story
shortened beside its own Details / More → deliberate summary (WARN); a chip on every card ("PLAYING") → not a fixed text;
a swaying lit case (Shelf) → best match within ±4 % and relative to the neighbour; the first-layout key loss → INFRA only
when no layout moved.

## 3. Hardware runs (USB only; KONKR BW0306N230000845, Odin 2 Portal b92036fb; Thor and RP Classic NOT TESTED)
Builds (versionCode 73, never published): qa-0.6.68-474a ccf46242…, 474b 23e5a951…, 474c 5cbd63bc… (`builds.json`).
Popup: k474a, k474f, k474g, o474a, o474b. Navigation: k474m, k474n, k474p, k474r, k474w, o474p, o474q, o474r.
Restoration: the app's snapshot (settings, appearance, categories, view state) saved before, restored, saved again: all
4 files byte-identical (md5) on both devices; font 1.0, density, size, brightness, timeout unchanged (never set); hooks /
no-launch files removed; RC f79aa2a7 reinstalled (`adb install -r`, data kept), SHA-256 checked on both. No game launched,
nothing uninstalled, no data cleared, no ROM / artwork touched.

## 4. Defects recorded (`defects.json`)
D245 Row opens / reveal facts + achievements squeezed, story ellipsis (medium, open) · D246 Storage facts squeezed (triage) ·
D247 Airy story squeezed on the KONKR (triage: deliberate give-way needs the GivesWay mark) · D248 Voltcap row title jump on
arrival (intermittent) · D249 first D-pad key of a walk sometimes ignored (triage: app or hook).

## 5. Limitations (honest)
- CI: the staging workflow is written but **not active**: `.github` is a protected path for the remote tools, so it sits in
  `next/0.6.68/staging-ci.yml` until it is copied to `.github/workflows/`. No CI run of the staging source exists yet — "green" is not claimed. The app unit tests ran on the PC (`testDebugUnitTest` SUCCESS) on the
  same staging tree.
- Text check: Compose re-lays a plain String Text with its original style for semantics, so auto-sized text overflow can't be
  confirmed from the dump (WARN). No text width is exported yet; sideways cuts are always WARN.
- Early-art timing: `screencap` itself takes ~0.2–0.7 s; the "early" shot is ~0.35 s after the key, so sub-300 ms delays are
  not measurable. Timings are WARN only, never a universal threshold. QaArt recorded no pictures before build c (requests copied by interceptors were never matched).
- Game-count fixtures are **not implemented** (navigation follows the view model's own list; needs a session-only VM list).
- Odin navigation only in All with a fixture (its consoles hold 1–2 games); no Odin scrolling claim beyond those 3 layouts.
- Representative coverage only: popup 5 layouts, navigation 6 (KONKR) / 3 (Odin); T1 / T2 / T3 full sweeps, the 59 T4 runs and
  every T6 group are NOT TESTED. Overlap / unreachable / focus checks are the existing ones (unchanged).
- artfit measures fixture art only (real artwork has no known shape); a layout drawing art under heavy overlays can make it
  NOT TESTED.
- "families" in the T4 model and the `norow` list are QA assumptions (from the 471 sweeps), to be confirmed by the app's
  `PopupStyle.offered` in phase 2.
