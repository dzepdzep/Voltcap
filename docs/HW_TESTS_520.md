# Exact hardware test cases waiting for devices (batch 520)

All in the isolated **Voltcap QA** app (com.shelf.launcher.qa) with fixture data. Before every run:
- confirm the USB serial;
- launches blocked (`voltcap_test_nolaunch`);
- settings snapshotted and restored after;
- the real Voltcap's pid unchanged (`pidof com.shelf.launcher` before and after).

## KONKR Pocket Advance (= "GT78", BW0306N230000845) — memory growth (516: 183 → 433 MB PSS, 383 at rest)

1. **Baseline:** cold start the QA app; record `dumpsys meminfo com.shelf.launcher.qa` (TOTAL PSS, Native, Graphics, EGL mtrack, GL mtrack, Java) after 30 s idle.
2. **Navigation load:** 5 rounds of 53-layout previews (`voltcap_test_layout`), each with 20 D-pad moves and one game window open / close. Record meminfo after each round and after 60 s idle.
3. **Separating driver cache from a leak:**
   - at the end, `am send-trim-memory com.shelf.launcher.qa RUNNING_CRITICAL`, then meminfo again;
   - then background the app (HOME → QA app off screen 60 s) and meminfo again.

   Graphics / EGL dropping after trim or background means a cache. Java or Native rising round after round, and not dropping, means a leak.
4. **Repeat** rounds 6–10 with `voltcap_test_pref "case_3d=false"` (flat art) to see whether 3D textures carry it.
5. **Pass:**
   - PSS after trim within 20 % of round 1;
   - no monotonic Java / Native growth over 10 rounds;
   - no ANR or kill.

## KONKR — Case Stacks fast scroll (516: burst p90 36 ms, 10.4 % janky)

- `perf514.sh`-style framestats: Case Stacks, GBC fixture, 60 right presses at 80 ms (burst), then at 250 ms (steps).
- Clips off and on.
- Record p50 / p90 / p99 frame time, janky %, missed vsyncs, and RenderThread vs main thread time (`gfxinfo framestats` columns).
- Compare Shelf (0 %) under the same script.
- No code change unless main-thread time (not RenderThread) dominates.

## Thor (3140972f) — second screen

1. Twin layouts × popup open from the top and bottom screen; B from the other screen closes it (507 PASS; re-run).
2. **D2 lifecycle:** needs the owner's OK to open an app on the top screen. Then the lower UI leaves when an app opens on top; "Probably still open" clears; focus returns to the screen used last.
3. Second-screen brightness (485) still applied after a QA app restart.
4. Pocket Ribbon on two screens: no duplicate title on the upper screen (A4 / D270).

## Odin 3 — navigation bar (owner: "should be done inside the app automatically")

Read-only first:
- `dumpsys window | grep -iE "navigationbar|insets|immersive"`;
- `settings get global policy_control`;
- `getprop | grep -i ayn`;
- which navigation mode is set;
- a screenshot of the QA app home.

Then compare with the Thor (where the bar hides). Only after the cause is known: a fix in MainActivity's window insets (no system setting changed). Never guess.
