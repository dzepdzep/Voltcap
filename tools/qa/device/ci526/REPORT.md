# Sprint 526 - Connected Ignition visual QA (Flip 2 + KONKR)

Status: **NOT visually approved** - waiting for the owner's review. RC4 (`c827ff9`) untouched; nothing merged, tagged, pushed to main or published.

## Builds under test (isolated QA app `com.shelf.launcher.qa` only)
| commit | what | QA APK sha256 (installed base.apk) |
|---|---|---|
| `422057e5` | CI + `voltcap_test_startprofiles` hook (main evidence, batches a/b) | `d2c809f5...a6df6ed` |
| `87daefe8` | + shrink-crash fix (batch c, KONKR) | `2ed71d13...ec98b45` |
| `6a6a9913` | + input ignored while leaving, cursor back to active, Batch512Test 0.6.70/77 (batch d) | `5429394b...c76c4866` |
Unit tests on `6a6a9913`: PASS. Flip 2 left on RC4 QA (`c3a45817...59c57`), owner snapshot files identical, real Voltcap 77 / 09:40:21 unchanged.

## Covered (Flip 2, 1920x1080 @120 Hz)
FULL x 7 looks, SHORT, PIXEL, OFF; walk; pick -> Home; B keep; skip; rapid input; text x1.0 / x1.3; long names; 3/4/5/6/8 sample profiles
(memory-only hook); in-app picker (R1 / Settings path, via its intent) - no intro replay; Home intent to the app - no replay.
Not covered: return from a real game (the QA app never launches games), physical R1 / Settings navigation (intent path used).

## Continuity (frame-by-frame, 13 runs)
- No black / blank frame between capsule and picker (0 near-black frames in every hand-off window); no background swap (look backdrop dissolves in
  over ~0.5 s); no duplicated portraits; exactly one selected ring in every sampled frame; only large single-frame change = the designed bolt flash.
- Capsule lines extend from both capsule ends to the screen edges at the row's height, then the energy runs along the row and portraits appear as it
  passes; the selected ring gets a bright pulse when it arrives.
- Frame timing (gfxinfo, whole run): FULL p50 13-17 ms, p90 15-20 ms, janky 0.7-1.9 %; max visible frame gap in the hand-off 10-49 ms.

## Defects / refinement candidates
1. FIXED+verified: crash (IndexOutOfBounds) when the profile row shrinks under the settled stage.
2. FIXED (unit tests pass; hardware recording of the re-check stalled - visual re-check pending): A, A, Right moved the ring to Add while the pick was leaving.
3. Selected glow clipped to a rectangle: ~150 ms square box while the ring arrives (all looks); persistent hard-edged box when the row scrolls (6+ profiles).
4. Wire ends curl into "( )" hooks at both screen edges and linger ~0.5 s detached from the row before fading.
5. Reveal runs right -> left, so the selected (usually leftmost) portrait appears last; amber wire -> look-colour ring changes in ~200 ms.
6. Confirm -> Home: ~0.45 s dim stage, then a 67 ms hitch as the Home starts drawing.
7. Skip is a one-frame cut to the settled picker (no crossfade).
8. Text x1.3: "Keep <long name>" hint wraps onto two lines, misaligned with A / Y.
9. GPU: Aurora / Neon / Midnight backdrops keep drawing every frame while settled (40 s+ seen, ~14 ms/frame vs 8.3 ms budget); Console / OLED / Theme / Ignition draw nothing.
Legacy (by design, not CI): PIXEL / OFF show the Home briefly before the picker; OFF cold start janky 52 %.

## KONKR (GT78, 960x640) - PARTIAL, small-screen pass pending
Settled picker with long names x1.0 fits. Recording stalled; x1.3 runs invalid (am start "Failed transaction"); the device then slept on its own
60 s screen timeout, the gate stopped all input (never woken). KONKR QA app is now `87daefe8` with intro/look changed by the hooks -> restore
(`sh /data/local/tmp/ci526.sh restore`) pending until it is awake and unlocked.

## Recorder
Flip 2 screenrecord stalled in 4 of 24 runs (~2-3 s in, app kept drawing); recheck.py misses stalls inside a file - internal gaps were found by
frame-timestamp analysis; stalled runs re-recorded (c02/c08/c09) except c08v.
