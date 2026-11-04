# VoltCap QA — update 477: staging CI, D250 / D252 / D266, T5 triage, T3 content run

Staging only (0.6.68, `next/0.6.68/staging.patch`); v0.6.67 frozen, main's app untouched, nothing published.
Devices (USB only): KONKR GT78 BW0306N230000845 (Android 12), Odin 2 Portal b92036fb (Android 13). Thor / RP Classic NOT TESTED.

## 1. Build identity
| build | APK SHA-256 | patch SHA-256 (on main e03b09f) | tree | what |
|---|---|---|---|---|
| qa-0.6.68-477a | 2b138584… | 8d4f8554… | 14532eba… | 476c + Airy More in the panel's well + D252 key trace |
| qa-0.6.68-477b | 15eb65c9… | 8872ae48… | 1192d738… | + D252 fix (KeyRootComposeView) ; Airy More keys small in the key row |
| qa-0.6.68-477c | f55f314f… | 87c85249… | b8a7d81f… | + Airy More keys as a pill row, name never cut, QaClip `placed` |
| **qa-0.6.68-477d** | **677a58bc…** | **225800de…** | **29ad0ebc…** | + Airy room with More open (KONKR), QaClip marks sliding texts — **what this report's "after" shows** |
All versionCode 73, never published; full hashes in `builds.json`. 477a–c were tried on hardware and failed there (recorded below).

## 2. Staging CI (Priority 1)
`.github/workflows/staging.yml` active (eb80e01, written from the PC via the KONKR — Explorer could not create `.github`).
It applies the staging patch to main, compiles, runs the JVM unit tests and the debug APK, then the QA self-tests and strict static checks.
| commit | Staging build | QA tooling | why |
|---|---|---|---|
| 09ccd99 / eb80e01 | FAIL (self-tests) | FAIL | test_qa imported visualpdf → reportlab, not on CI → split stepreason.py |
| f71c809 | FAIL (self-tests) | PASS | unknown: logs need sign-in → annotations added |
| e03b09f | FAIL (self-tests) | PASS | annotations: `TestRuncheck…empty_is_not_tested` needs ffmpeg; the step's `grep` returned 1 on a clean log and `bash -e` failed it |
| **9c2d393** | **PASS** (run 41907158243, all 12 steps) | **PASS** | ffmpeg installed, that test skips without it, the greps can't fail the step |

## 3. Defect outcomes
| id | outcome | evidence |
|---|---|---|
| **D250** Blades "another game" | **Not an app bug — QA harness assumption.** ▶ on Blades moves to the Systems blade (hint "Open its games"); the view model's focused game stays the run's game; A there opens the system's game list and the next A opened the window for the game then focused. `voltcap_test_game` does not bring Blades back to its Games blade, so later key sequences acted on whatever blade was active (round 2 ended on Media → Spotify page). Grading corrected (f71c809); app unchanged. UX note: the Systems blade highlights its first system, not the current game's (recommendation). | k477b / o477b (per-key focus + popup identity from the app's own dumps) |
| **D252** first D-pad after touch | **Fixed (477b), verified KONKR.** Cause: Android 12's view root consumes the first navigation key to leave touch mode (trace: 1 of 2 keys reached the activity). Fix: the root view leaves touch mode itself before IME dispatch, so the key is delivered (A/B handling from 467 unchanged). Odin (Android 13) already delivered both keys. Limitation: dialog windows (own view root) not covered. | before k477a: tap → RIGHT moved 0/3; after k477d: 2/2, `leftouch` trace, keys-only walk unchanged. **Physical controller: NOT TESTED** (ADB key events only) |
| **D266** Airy facts | **Fixed (477d), both devices.** Round keys (Play, Details, Back, Inspect, More/Less) along the bottom; More opens one row of rounded pills (Options, Achievements, Save sync, Manual); name 2 lines then shrinks/slides (never cut). Nothing removed; pills 32 dp, round keys 50–58 dp. Gate: readcheck `facts` now **FAILs** a text that is zero-size or never placed (QaClip `placed`) — the dropped FlowRow line (477b/c) passed the old checks. | k477l/m/n, o477l/m/n (real, long names, no metadata): facts + geometry PASS main and More; controller walk reaches every key (k477k/o477k). 477a/b/c FAILED on the KONKR (well squeezed / row wrapped / chips dropped). **Larger system text: NOT TESTED** (needs the system font scale — owner to set). Before/after: `VoltCap_before_after_477.pdf` |

Harness lesson: on the KONKR (Android 12) a background script whose output goes to /sdcard makes every `input` call fail (`Failed transaction`); logs now go to /data/local/tmp.

## 4. T5 triage (Priority 4) — re-walked with recordings on 477c (k477t / o477t), Twin desk again on 477d (k477p / o477p)
| layout | finding | class | result |
|---|---|---|---|
| Cinema (Odin) | card "11–17 px off" on return | **incorrect QA measurement** — card border at the same pixel in every screenshot; the dump's box was taken mid-glide (D269) | navclip now checks pixels → PASS |
| Arctic (KONKR) | same game one slot left after ▶ ◀ | **deliberate scroll** (row scrolls only as far as needed; smooth in the recording) | expectation `slot_free`; fixed-slot row = recommendation D267 |
| Cover details | facts / "ACHIEVEMENTS" elsewhere | **deliberate animation** (the panel rolls by itself — D263, deferred) | expectation `pages` → PASS (art NOT TESTED: no card) |
| Pocket Ribbon (Odin) | "Android" moves | **expected text motion** (label after the title) | PASS |
| Camera | "002"/"003" move | **deliberate animation** (frame counter) | PASS (art NOT TESTED) |
| Terminal | "►" moves; Odin preview 6 px lower once | pointer = **expected**; 6 px = **genuine jump** (pixels confirmed, s4 fine) — D268 open | KONKR PASS; Odin FAIL |
| Twin desk | "stuck focus" | **incorrect QA expectation** — ◀ ▶ leave the list for the desk's other panes; the ◀ ▶ prelude put the walk outside the games | walked ▼ ▲ without prelude: PASS both |
| Twin icons | "Open" moves | **expected** (label under the focused icon) | PASS |
Recordings: frames.py found no flash / black frame in any of them (hitches only).

## 5. Coverage after this update
| tier | KONKR | Odin |
|---|---|---|
| T1 OWN (53) | 476d sweep (52 + 1 harness) ; Airy re-run 477d | same |
| T1 other 5 styles | **NOT TESTED** | BANNER + CLASSIC 106/106 (475); others NOT TESTED |
| T2 font / display size | **NOT TESTED** — needs system font scale / display size changed (owner) | NOT TESTED |
| T3 content (477d, 53 layouts) | set A (long names + portrait art + no metadata): **39 PASS / 14 FAIL**; set B (short names + wide art): **50 PASS / 2 FAIL / 1 INFRA** | set A: **47 PASS / 6 FAIL**; set B: **51 PASS / 1 FAIL / 1 NOT TESTED** |
| T4 pairwise (59) | **NOT TESTED** | NOT TESTED |
| T5 (8 triaged layouts) | 7 PASS, 1 NOT TESTED (art) | 6 PASS, 1 FAIL (Terminal D268), NOT TESTED art |
| T6 settings (101 groups) | **NOT TESTED** | NOT TESTED |
A generated plan (t4_pairwise.json, T6_GROUPS.md) is not counted as coverage.

### 5a. T3 findings (new)
- **D271** — a very long name is cut at its line limit (no slide, rule 11) in 13 window scenes on the KONKR (Arcade, Box, Camera, Custom, File select, Magazine, Night, Panels, Shop, Storage, Terminal, TV, + Orbit's own slide read as cut) and 4 on the Odin. Same fix as Airy (FullText). Open — next pass.
- Cinema (Odin) set A: an 18 px-wide control under 24 dp — to check.
- Blades (A and B, both devices): window did not open — Blades keeps the blade it was on (left on Systems / Media by the D250 runs); the test hook doesn't reset it (harness, D250 class).
- Set B: Bar once "first A opened nothing" (timing), Tiles no evidence captured (INFRA) on the KONKR.

## 6. Deferred (recorded only, per owner)
D257 Tiles, D263 Cover details rolling story, D265 Twin paging indicators, D270 Pocket Ribbon title on both screens, and the collected layout / popup / colour-customization ideas.

## 7. Final checks and device state
- Airy OWN re-run on 477d: **all 7 categories PASS on both devices** (k477q / o477q).
- Both devices back on the 0.6.66 RC (APK f79aa2a7…); test hooks removed; the RC's own settings snapshot is byte-identical to the one from before update 476 (KONKR a6b8f5c7…, Odin cd8eff0a…). No game launched, nothing uninstalled, no data cleared, no ROM / artwork / profile / background changed.
- Self-tests 130 OK (1 skip without ffmpeg locally); static checks strict PASS; app unit tests SUCCESS on the 477d tree; staging CI PASS on 9c2d393.
