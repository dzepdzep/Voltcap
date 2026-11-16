# Autorun log (newest first; one short entry per scheduled or live session)

## 2026-11-15 ~22:30 Berlin - live session (cloud migration, 674o)
- Cloud: repo clone + push verified from a cloud session; Android SDK installs (setup_android.sh); unit-test build started in
  the cloud (2 cores: slow, CI is the gate). Emulator trial reworked (674o) and started with "[emulator-qa]".
- Hardware (PC): HW-1 gates graded - Flip 2 x1.0 f674o 52/53, KONKR x1.3 k674n 51/53; both devices restored (snapshot diff=0,
  fatal=0). Fixes for the three FAILs in 674o.
- Cloud verified: compile of app + tests (~7 min) and targeted unit tests (13/13) in the cloud container; pushes from the cloud.
- Emulator 674o matrix: swangle_indirect stays up (gates ran, emulator FAILs = timing to investigate); default now swangle.
- Cloud scheduled task "VoltCap Cloud Autonomous Development" hourly at :17; first run 22:17 UTC (unverified until it logs here).
- PC hourly task: still held off by the PC lock file (Claude outputs\AUTORUN_LOCK.txt) until the cloud task is verified.

## 2026-11-15 22:18-23:55 UTC - cloud scheduled run (first)
- Lock taken 22:18 (was free). Item: HANDOFF Next 0, CI memory (G11). Verify 310 was red on c0760eb (one runner), green on 4d44599.
- Measured: native (non-JVM) memory growth inside EndlessFillLitRoboTest (+~250 MB per Case flow row, not returned by GC).
  Fix 675a: heavy classes in testDebugHeavyRoboUnitTest (JVM per class), 6 shards; LookStyles states made at start.
- Cloud tests: EndlessFillLitShardsTest PASS; all shards + EndlessOnceRoboTest PASS (shard 3 three runs); per-JVM peak <= 3.3 GB.
- Cloud memory cgroup ~5.8 GB: -Xmx5g compile OOM-killed; -Xmx4g works (CLOUD_DEVELOPMENT.md updated).
- CI on the push: see Actions; next run checks it first.

## 2026-11-16 00:18-01:00 UTC - cloud scheduled run
- Lock free (released 23:40); taken 00:21. CI: Verify 310 green on 7dbcab0c (675a), lowest free memory 6.4 GB.
- Item: HANDOFF Next 1, emulator trial. Artifact of run 43762702001: popup open in the "first A opened nothing" shot - the
  QaClip dump came after the fixed sleeps. Fix 675b: scripts wait for the dump (dw), QaClip writes it whole. Selftest 156/156,
  QaClipWriteWhole675bTest 2/2, static checks PASS. Pushed with "[emulator-qa]"; next run reads that trial.
- Results on 750b669c: Verify 310 green (G11: 2 green pushes). Emulator trial 43780985909: e529b SHELF PASS, COVERDETAILS 10/11;
  e529a still FAIL - popup on screen but the dump has only the home window (QaWalk roots after the test scale) -> next run.

## 2026-11-16 01:18-01:55 UTC - cloud scheduled run
- Lock free (released 00:32); taken 01:19. CI: Verify 310 green on 750b669c and 2fa6bc39 (G11: 3 green pushes).
- Item: HANDOFF Next 1. Artifact of trial 43780985909: e529a firstA shots show the popup the probe missed; fontScale 1.0 in the
  dumps is expected (in-app scale). The probe was deleted, so late vs missing root was unknowable. 675c: dumps carry popupAgeMs +
  roots, popclip.sh keeps the failed probe and re-probes once ("opened late" still FAILs open, no second A on Play).
  Selftest 157/157, QaClipDiag675cTest 2/2, static PASS, compile OK. Pushed with "[emulator-qa]".

## 2026-11-16 02:18-02:50 UTC - cloud scheduled run
- Lock free (released 01:31); taken 02:19. CI: Verify 310 green on ddca9b66 (675c). Emulator trial 43786748164 FAIL (EMULATOR).
- Item: HANDOFF Next 1. Artifact read: first A opens every time now; the 2 FAILs are controls present in the dump that the
  device's grep did not see (both dumps hold "Full story on Details ›" on one long line). 675d: popclip.sh reads dumps one node
  per line, dw waits for a whole dump. Selftest 158/158, static PASS. No "[emulator-qa]" (3 h rule) - next run starts it.

## 2026-11-16 03:18-03:50 UTC - cloud scheduled run
- Lock free (released 02:23); taken 03:18. CI: Verify 310 green on e1c1ad88 (675d). Emulator trial not started (3 h rule, from 04:31).
- Item: HANDOFF Next 2 (D-674o-1). 675e: GlyphPill marks its pill (QA traces only), QaClip dumps the glyph's "key" box,
  glyphcheck measures in it (limits unchanged). Selftest 161/161, static PASS. Gradle compile blocked by Maven Central 429
  (2 tries) - CI compiles. HW-6 queued.
