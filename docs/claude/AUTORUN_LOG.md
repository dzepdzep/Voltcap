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
