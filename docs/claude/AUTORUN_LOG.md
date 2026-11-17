# Autorun log (newest first; one short entry per scheduled or live session)




## 2026-11-16 07:22-07:50 UTC - cloud scheduled run (675l)
- Lock free, taken. Emulator trial not yet allowed (3 h rule, from ~08:45). Rule 11: Spotify screen + player names show whole (FullText), 13 "…" removed; compiled + tests PASS in the cloud.

## 2026-11-16 06:02-06:20 UTC - live session (675j)
- v0.6.73 plan without the Flip 2 (historical evidence + risk); HW-3 reproduce-first; .claude/settings.json effortLevel high (to verify via run logs).

## 2026-11-16 05:33-06:05 UTC - live session (675i)
- Owner: Flip 2 unavailable, charging fix to v0.6.74. Fix kept on claude/charge-loop-674i + patch, removed from the dev line. popclip grade crash (675e regression) fixed. Note: this session's lock push cancelled CI on 675h's commit; CI on 675i covers both.

## 2026-11-16 05:09-05:50 UTC - live session (675g, owner summary + release tracker)
- Lock taken 05:09 (the 05:17 cloud run skips). Owner summary + release tracker generated; task prompt updated; effort: cloud host passes CLAUDE_EFFORT=medium.

## 2026-11-16 04:18-04:45 UTC - live session (675f, owner dashboard)
- Lock taken 04:18 (the 04:17 cloud run skips). STATUS_DASHBOARD.md + dashboard.py + progress.json; HW queue batched; task prompt -Xmx4g. No app code; CI green on deedfa45 before.

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

## 2026-11-16 05:20-05:40 UTC - cloud scheduled run
- Lock free (released 05:17 by 675g live session); taken 05:20. CI: Verify 310 green on c6d2f8a (675f, includes 675e Kotlin);
  ec3e103 (675g) in progress, then superseded by the lock push.
- Item: HANDOFF Next 1. 675h: shelfpages529.sh dumps read one node per line + whole-dump wait (as popclip 675d). Selftest
  162/162 (new test fails on old script), static PASS. No app code. Pushed with "[emulator-qa]".

## 2026-11-16 06:18-06:45 UTC - cloud scheduled run (675k)
- Lock free (released by 675j); taken 06:18. CI: Verify 310 green on 2371ce29 (675i).
- Item: HANDOFF FIRST - read emulator run 43811840328: NOT TESTED, precondition failed (Pixel Launcher ANR dialog + the
  Inspect / Wobble question). Fixed in run_trial.sh + the QA first-run hook; EmulatorHarness529Test 4/4, compile OK, static
  PASS, selftest 163/163. No [emulator-qa] (3 h rule: next from ~08:45).

## 2026-11-16 08:21-08:45 UTC - cloud scheduled run (675m)
- Lock free (released by 675l); taken 08:21. CI: Verify 310 green on 8bd5e4ae (675l).
- Item: rule 11 filler (trial not allowed before 08:41): notifications panel, 6 Ellipsis -> FullText (268 -> 262).
  NotificationsFullText675mTest 2/2, compile OK (one Maven 429 retry), static PASS. Head commit carries "[emulator-qa]".

## 2026-11-16 09:20-09:50 UTC - cloud scheduled run (675o)
- Lock free (released 08:48); taken ~09:35. CI: Verify 310 green on a1945934; one flaky EndlessOnceRoboTest on 0e0b1dc1.
- Item: emulator trial 43833359452 (675m) read - first fully graded trial (x1.0 SHELF PASS 11/11); FAILs = device grep
  missing keys in dumps with "›". Fix 675o in popclip.sh / shelfpages529.sh (ASCII reads, late extra-A closed). Selftest
  164/164, static PASS. Next trial from ~11:42.

## 2026-11-16 14:22-14:55 UTC - cloud scheduled run (677a)
- Lock free (released 14:15 by the live session); taken 14:23. CI: Verify 310 on 1e1400c in progress at start (d712fab cancelled, d1d6d98 green).
- Item: rule 11 filler - SessionSummary (6 TRUNCATE -> FullText) + SessionSummaryFullText677aTest; compiled + tests PASS in the cloud.
  Emulator trial started on this commit (first on the 675o fix). Effort env: medium.

## 2026-11-16 16:20-16:50 UTC - cloud scheduled run (677b)
- Lock free (released 15:29 by the live session); taken 16:21. CI: Verify 310 RED on 0cc6739 + 0e23337 (QaIsolation521Test lookup).
- Item: fixed the test lookup (buildTypes debug block) + QA-key block assertion; not compiled here (Maven 429 x2), CI decides.
  Emulator trial 43887216747 read (x1.0 COVERDETAILS full PASS; SHELF / x1.3 misses remain). Effort env: medium.

## 2026-11-16 17:18-17:40 UTC - cloud scheduled run (677c)
- Lock free (released 16:32 by 677b); taken 17:19. CI: Verify 310 GREEN on 833f308 (677b fix confirmed).
- Item: emulator trial 43887216747 misses traced (probes held Play + popupAgeMs while up() read none) -> popclip.sh up() own
  probe names + app popupAgeMs + read diagnostics; selftest 177/177. Trial not started (3 h window opens ~17:50). Effort env: medium.

## 2026-11-16 ~18:20-18:55 UTC - cloud run 677d
- Lock free (none since 17:25); taken 18:20. CI: Verify 310 GREEN on 0eb3904 (677c). Effort env: medium.
- Workspace started on main (pre-cloned); worked in a separate dev-branch clone. Owner (live) asked for a permanent fix:
  tools/cloud/start_run.sh + scheduled task prompt updated (bootstrap + branch check before lock / commit / push).
- Item: rule 11 play header + Stats page FullText (11 places; test compiled + PASS). Emulator trial started on this commit.

## 2026-11-16 ~19:19-19:50 UTC - cloud run 678a
- BOOTSTRAP OK (eba2de4); lock free (none since 18:32), taken 19:19. CI: Verify 310 GREEN on eba2de4. Effort env: medium.
- Read emulator trial 43929836268 (677d): SHELF PASS both scales, COVERDETAILS buttons FAIL from the device's grep -iE.
- Item: popclip.sh label reads without a regex + selftest (178/178). No emulator trial this run (3 h rule, from ~21:35).

## 2026-11-16 ~20:30 UTC - cloud run 678b
- BOOTSTRAP OK (866a956); lock free (none since 19:24), taken 20:18. CI: Verify 310 GREEN on 866a956 (678a). Effort env: medium.
- Item: shelfpages529.sh touch taps without the device's grep -iE + selftest (178/178). No emulator trial (3 h rule, from ~21:35).

## 2026-11-16 ~21:18-21:50 UTC - cloud run 678c
- BOOTSTRAP OK (67d745f); lock free, taken 21:18. CI: Verify 310 GREEN on 67d745f (678b). Effort env: medium.
- Rule 11: year recap text shown whole (9 "…" removed, YearRecapFullText678cTest PASS, compiled after one Maven 429 retry).
  Emulator trial started on this commit ("[emulator-qa]"). Lock released.

## 2026-11-16 ~22:18-22:45 UTC - cloud run 678d
- BOOTSTRAP OK (88e3457); lock free, taken 22:18. CI: Verify 310 GREEN on 88e3457 (678c). Effort env: medium.
- Read emulator trial 43961010406 (678c): VERDICT PASS (first fully green). e529c touch taps missed because the game had no
  achievement set -> shelfpages529.sh touch walk page-aware + game with a set; selftest 179/179. No trial started (3 h rule). Lock released.

## 2026-11-16 ~23:18-23:50 UTC - cloud run 678e
- BOOTSTRAP OK (a3b685e); lock free (none since 22:22), taken 23:18. CI: Verify 310 GREEN on a3b685e (678d). Effort env: medium.
- Rule 11: banner game window text shown whole (12 "…" -> 1 marked), GameWindowBannerFullText678eTest PASS, compiled after a
  Maven 429 retry; HW-12 queued (popclip, Banner style). No trial started (3 h rule, from ~00:45). Lock released.

## 2026-11-17 ~00:18-00:35 UTC - cloud run 678f
- BOOTSTRAP OK (cc34a59); lock free (none since 23:39), taken 00:18. CI: Verify 310 RED on cc34a59 (678e) - one source test
  (PopupPolish444Test) pinned the banner name's old em line height; test updated to the new sp form (same rule), compiled +
  PASS in the cloud. No trial (3 h rule, from ~00:45). Effort env: medium. Lock released.

## 2026-11-17 ~01:18-01:45 UTC - cloud run 678g
- BOOTSTRAP OK (ad80646); lock free (none since 00:29), taken 01:18. CI: Verify 310 GREEN on ad80646 (678f). Effort env: medium.
- Rule 11: Save Vault text shown whole (13 "…" removed, TRUNCATE 224 -> 211), SaveExplorerFullText678gTest; main Kotlin
  compiled, test classpath Maven 429 twice (test not run here - CI). HW-13 queued. Emulator trial started on this commit. Lock released.

## 2026-11-17 ~02:19-02:50 UTC - cloud run 678h
- BOOTSTRAP OK (a5b76b5); lock free (none since 01:30), taken 02:19. CI: Verify 310 GREEN on a5b76b5; emulator trial on 678g
  VERDICT PASS (e529c taps as expected). Effort env: medium.
- Rule 11: Settings text shown whole (17 "…" removed, TRUNCATE 211 -> 194), SettingsDialogFullText678hTest; Maven 429 on both
  compile tries - not compiled here, CI is the first compile. HW-14 queued. No trial started. Lock released.

## 2026-11-17 ~03:18-03:40 UTC - cloud run 678i
- BOOTSTRAP OK (d2185d5); lock free (none since 02:26), taken 03:18. CI: Verify 310 GREEN on d2185d5 (678h). Effort env: medium.
- Rule 11: save-sync prompts text shown whole (7 "…" removed, TRUNCATE 194 -> 187), SyncPromptsFullText678iTest; Maven 429 on
  both compile tries - not compiled here, CI is the first compile. HW-15 queued. No trial started. Lock released.
