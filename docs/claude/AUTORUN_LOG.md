# Autorun log (newest first; one short entry per scheduled or live session)

## 2026-11-15 ~22:30 Berlin - live session (cloud migration, 674o)
- Cloud: repo clone + push verified from a cloud session; Android SDK installs (setup_android.sh); unit-test build started in
  the cloud (2 cores: slow, CI is the gate). Emulator trial reworked (674o) and started with "[emulator-qa]".
- Hardware (PC): HW-1 gates graded - Flip 2 x1.0 f674o 52/53, KONKR x1.3 k674n 51/53; both devices restored (snapshot diff=0,
  fatal=0). Fixes for the three FAILs in 674o.
- PC hourly task: still held off by the PC lock file (Claude outputs\AUTORUN_LOCK.txt) until the cloud task is verified.
