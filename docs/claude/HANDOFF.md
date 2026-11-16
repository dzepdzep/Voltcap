# VoltCap handoff (read first, update last)

Updated: 2026-11-16 ~22:40 UTC, update 678d (cloud run: emulator trial 43961010406 on 678c READ - VERDICT PASS, first fully
green trial; e529c touch walk made page-aware + game with an achievement set (selftest 179/179); no trial started).
Before: 2026-11-16 ~21:45 UTC, update 678c (cloud run: rule 11 - year recap FullText, YearRecapFullText678cTest compiled + PASS; emulator trial STARTED on this commit).
Before: 2026-11-16 ~20:30 UTC, update 678b (cloud run: QA tool - shelfpages529.sh touch taps read labels without the device's grep -iE, TestPopclipScript.test_shelfpages_taps_read_labels_without_the_devices_regex; selftest 178/178; CI green on 866a956).
Before: 2026-11-16 ~19:50 UTC, update 678a (cloud run: QA tool - popclip.sh reads labels without the device's grep -iE (emulator run 43929836268 on 677d: SHELF PASS x1.0 + x1.3, COVERDETAILS only buttons FAIL = grep -iE missed Play / Details / More that fixed-string reads found); selftest 178/178; CI green on eba2de4).
Before: update 677d (cloud run, owner live: rule 11 - play header + Stats page FullText
(PlayHeaderStatsFullText677dTest, compiled + PASS in the cloud); run bootstrap tools/cloud/start_run.sh - the pre-cloned
workspace starts on main; the task prompt now switches to the dev branch safely; emulator trial started on this commit).
Before: update 677c (cloud run: QA tool - popclip.sh up() reads a probe of its own name each
time and also trusts the app's popupAgeMs; Details / More misses log plain-vs-pattern reads; selftest 177/177. CI green on 677b.)
Before: update 677b (cloud run: CI red on 0cc6739 / 0e23337 fixed - QaIsolation521Test
looked at the new QA-key signingConfigs debug block instead of the debug build type; NOT compiled here (Maven 429), CI decides).
Before: update 677a (cloud run: rule 11 - post-game session card + status note
shown whole (FullText, SessionSummaryFullText677aTest; compiled in the cloud); emulator trial started on this commit).
Before: update 675o (cloud run: read emulator run 43833359452 on 675m - first fully graded
trial: x1.0 SHELF PASS 11/11; every FAIL traced to the device grep missing keys in dumps holding "›" -> popclip.sh /
shelfpages529.sh read dumps in plain ASCII, late extra-A window closed before the rapid presses; selftest 164/164).
Previous: 675m (notifications panel FullText).
Dev branch `claude/qa-0672-repair`; production `main` = v0.6.72 (5b01ef39). Nothing released after v0.6.72; no production app
touched; KONKR production not upgraded. How to work in the cloud: `docs/claude/CLOUD_DEVELOPMENT.md`.

## State
- Cloud migration (owner request 15 Nov): repo attached to cloud sessions (clone + push verified); Android SDK setup script
  `tools/cloud/setup_android.sh`; `VOLTCAP_CLOUD=1` uses the CI Discord stub. Cloud scheduled task replaces the PC's hourly task
  once a cloud run is verified (see AUTORUN_LOG). Hardware-only work: `docs/claude/HARDWARE_QA_QUEUE.md`.
- Emulator QA (`emulator-qa-trial.yml`): every trial since 14 Nov crashed the emulator ~90 s in. 674o: x86_64 QA build, AVD at
  the profile's own size, renderer matrix (swiftshader_indirect / swangle_indirect / guest), verdict step that fails unless PASS.
  First 674o run: see AUTORUN_LOG / Actions.
- Handheld gates (674n build, c2cdfc81): Flip 2 x1.3 f674k 52/53 (674k build), Flip 2 x1.0 f674o 52/53 (Side status line
  51 px < 54 -> fixed 674o), KONKR x1.0 k674m 51/53 (674k build; both fixed since), KONKR x1.3 k674n 51/53 (Library R2 -> fixed
  674o; Spot START = checker reads a backdrop shape as the pill edge, ink measured centred by eye -> D-674o-1 below).
- CI: verify-update-310 green on 7358016a, c30d6f5a, cf629e2e, 7dbcab0c (675a), 750b669c (675b), 2fa6bc39, ddca9b66 (675c),
  e1c1ad88 (675d). 675e (Kotlin: QaClip glyph key + QaGlyphKey675eTest) was NOT compiled in the cloud (Maven 429) - CI is its
  first compile: if red, fixing it is item 0.

## Next (in order; skip what needs hardware - queue it instead)
- FIRST (678d): BOOTSTRAP OK required. Check "Verify Android update 310" on the 678d push. From ~00:45 UTC START the emulator
  trial (head commit "[emulator-qa]") and read it the run after: e529a/e529b must stay PASS; e529c log should show "tap
  '*achievement 1*' at x,y" + "tap 'game settings*' at x,y" (or "using '<game>' (a set of 10)" in setup), no "no shown
  control". Then widen the trial (more layouts in run_trial.sh's popclip LAYS, bounded by the job time) - item for G14.
  Meanwhile: rule 11 GameWindowBanner (12) - popup file: FullText + test, then a popclip gate (emulator + HW queue).
- DONE 678d: emulator trial 43961010406 (678c) READ - VERDICT PASS (see SUMMARY "Update 678d").
- FIRST (678c): BOOTSTRAP OK required. Check "Verify Android update 310" on the 678c push. READ the emulator trial started on
  678c ("[emulator-qa]", ~21:45 UTC; next allowed from ~00:45): expect "Play keys 1" in the probe lines, COVERDETAILS Details
  page + More step at both scales, and e529c's touch log "tap '*achievement 1*' at x,y" / "tap 'game settings*' at x,y".
  Meanwhile: rule 11 GameWindowBanner (12) - a popup file: FullText + test, then a popclip gate (emulator trial covers
  COVERDETAILS / SHELF; add the handheld check to HW queue). YearRecapDialog DONE 678c.
- FIRST (678b): BOOTSTRAP OK required. Check "Verify Android update 310" on the 678b push. From ~21:35 UTC START the emulator
  trial (head commit "[emulator-qa]") and read it the run after: popclip expectations as 678a below, and e529c's touch log should
  now show "tap '*achievement 1*' at x,y" / "tap 'game settings*' at x,y" instead of "tap: no shown control". Meanwhile: rule 11
  screens GameWindowBanner (12), YearRecapDialog (9) (Kotlin: compile + a FullText test, as 677d).
- DONE 678b: shelfpages529.sh taps regex-free (see SUMMARY "Update 678b").
- (678a item, kept for its expectations) BOOTSTRAP OK required. Check "Verify Android update 310" on the 678a push. From ~21:35 UTC START the emulator
  trial (head commit "[emulator-qa]") and read it the run after: expect "Play keys 1" in the probe lines (no more "up by the
  app's own popupAgeMs (Play key not read"), COVERDETAILS Details page + More step present at both scales. Meanwhile: give
  shelfpages529.sh (e529c touch walk) the same regex-free reads (its ctl takes real regexes like '[^"]*achievement 1[^"]*' -
  turn them into prefix / contains word compares; this trial logged "tap: no shown control" for both taps); then rule 11
  screens GameWindowBanner (12), YearRecapDialog (9).
- DONE 678a: emulator trial 43929836268 (677d) READ - see SUMMARY "Update 678a".
- FIRST (677d): the run must print "BOOTSTRAP OK claude/qa-0672-repair <sha>" (tools/cloud/start_run.sh); if BLOCKED, report
  the reason and stop. Check "Verify Android update 310" on the 677d push. READ the emulator trial started on 677d
  ("[emulator-qa]", ~18:55 UTC; next allowed from ~21:55) per the 677c item below. Then rule 11 next screens: GameWindowBanner (12),
  YearRecapDialog (9).
- FIRST (677c): check "Verify Android update 310" on the 677c push (677b 833f308 GREEN). Then START the emulator trial on the
  next commit (head message "[emulator-qa]", allowed from ~17:50 UTC) and read it next run: expect no "first A opened nothing /
  late" where the probe holds popupAgeMs; read the new "read: probe N ..." / "Details read: plain X, pattern Y" / "plain More
  keys" lines. If plain > 0 and pattern = 0, the device's grep -iE pattern is the culprit: replace the ERE label matches
  (pc / ctl / DETW / MOREW greps) with label extraction + per-word fixed-string compares.
- Emulator trial 43887216747 (677a) READ: x1.0 COVERDETAILS 12/12 PASS; x1.0 SHELF "first A opened nothing" (read: Play keys
  probe 1, home 0 - the probe saw Play, yet graded as not open: look at the open grading in popclip.py vs the probe line);
  x1.3 COVERDETAILS late open (1.5 s) + "More: none read in the main dump (7 controls read)" + no Details action; x1.3 SHELF no
  Details action (More reached). Next: compare raw/e529a/COVERDETAILS_OWN dumps for the More / Details keys (QaClip names) vs
  what popclip.sh greps; next trial allowed from ~17:35 UTC.
- DONE 16 Nov live (675r / 676 / 676c, SUMMARY "Update 676"): the four Nova x1.3 FAILs fixed and PASS on the Nova; Hide nav
  bar root cause fixed (Retroid handle quirk was gated on a lazy root check); global badge lane (ui/BadgeLane.kt + badgecheck R27).
- DONE 676d-f: D334 guard (app + scripts, proven on the Nova), QA levels in CLAUDE.md, pinned QA key, badge lane final (name plate
  as lane content; readcheck skips unplaced texts). Nova 676f: x1.3 CUBE/TILES/GRID + x1.0 CUBE all PASS.
- v0.6.73 (owner: nav bar + badge lane IN, if CI + hardware regression pass): remaining gates - full 53-layout x1.3 re-run on the
  newest QA build (Nova, live session), KONKR when connected, CI green on the release commit; then the release checklist and the
  owner's final GO. Owner waits on: the Cube status-bar option (A capsule / B floating / C facet - shown 16 Nov), the GitHub
  secret + cloud env var for the QA key (docs/claude/QA_SIGNING_KEY.md).
- NEXT (v0.6.74): per-item "Open on screen" shortcut - docs/claude/SCREEN_SHORTCUT_PLAN.md (audit done; implement step 1 first:
  one shared ScreenTargetRow used by AppDrawer AppOptionsMenu, SecondScreenHome DockAppMenu, the game options; tests; no device).
- NEXT (v0.6.74, owner 16 Nov): Save Sync 529 / 529c onto the dev line. The branch fix/save-sync exists only as a bundle
  (project doc claude/backups/fix_save_sync_bundle_b64.txt; PC Claude outputs\backups\fix-save-sync.bundle; base 558cb1f7 =
  v0.6.71). Rebase / port onto claude/qa-0672-repair on a NEW branch (claude/save-sync-0674), never into the v0.6.73 line;
  compile + full unit suite + SaveSync529Test / SaveSyncSim529Test / SyncGuards529Test. Real Drive test waits for the owner
  (OAuth client for com.shelf.launcher.qa + spare-account sign-in; D337: the QA key must be stable first).
- DONE 677b (see above): read the emulator trial STARTED by the 677a cloud run (head commit "[emulator-qa]", ~14:50 UTC, first trial on the
  675o fix; next one allowed from ~17:50) - artifacts emulator-qa-<gpu>-<sha>. Expect e529a/e529b COVERDETAILS + SHELF to grade without "opened
  nothing" / "More step missing" / rapid launches. If a miss remains, the new log lines say what the device read ("read: Play
  keys probe X, home Y", "More: none read in the main dump (N controls read)") - set them beside the copied probes. If misses
  remain even so, the next step is QaClip writing non-ASCII as \uXXXX escapes (check every consumer of the raw dump text first).
- CI watch: EndlessOnceRoboTest [BOX n=12 x1] failed once on 0e0b1dc1 (ubuntu-latest, "performMeasureAndLayout called
  during measure layout"), green on the next push. If it recurs, make it the first item (heavy Robolectric class).
- v0.6.73 (owner 16 Nov): proceed with the KONKR + today's other handhelds; the Flip 2's 15 Nov gates are HISTORICAL evidence
  (risk listed, not a blocker). A live session runs the hardware batch when the owner says "devices ready".
- The Flip 2 is unavailable (owner 16 Nov): don't wait for it; its checks are batched as hardware session B. The charging fix
  lives on branch claude/charge-loop-674i / docs/claude/patches/charge-loop-674i.patch - do NOT re-apply it until the owner
  says the Flip 2 is back (target v0.6.74).
Every run also: append ONE line to docs/claude/run_reports.jsonl (plain English for the owner, see CLOUD_DEVELOPMENT.md), keep
releases.json / owner_actions.json / progress.json true to the evidence, then `python3 tools/cloud/dashboard.py`.
When a progress.json count changes, change its source text with it.
0. CI memory (G11, 675a): green on 7dbcab0c (both runners, heavy task ran, lowest free memory 6.4 GB) - 1 green push. Keep
   checking "Verify Android update 310" on each push; G11 DONE only after several green pushes in a row. If red, first item
   again (add the growing class to heavyRoboTests in app/build.gradle.kts).
   Side finding: lazily made global Compose states crash a fresh JVM's first frame - look for other objects with lazily made
   states read by Case3D / warm-ahead threads (LookStyles fixed via Features.warmUiStores).
1. Emulator trial: STARTED on the 675h commit ("[emulator-qa]", ~05:35 UTC; next one allowed from ~08:35). Read it
   (EMULATOR evidence): artifacts emulator-qa-<gpu>-<sha>. 675h gave shelfpages529.sh (e529c) jl + whole-dump waits too. Run 43786748164 on ddca9b66 (675c): first A opened the window every time (no probeA/B).
   FAILs left: e529a SHELF / e529b COVERDETAILS "no Details action" + More step missing (+ e529a "extra: A opened nothing"),
   with the controls IN the pulled dump - the device's grep missed them on the two dumps that also held "Full story on Details
   ›" (10-13 KB single line). 675d: popclip.sh reads every dump one node per line (jl) and dw waits for a whole dump ("dump
   unreadable" logged). If the trial still shows "no Details action" with the control in the dump: add a probe that copies
   `jl <dump> | grep -c control` into the log to see what the device reads. Also open: e529b COVERDETAILS "rapid A B A B left
   a window open" and e529c's precondition (qa_windows=2 after e529b) - is a window really left (rapid shot / after dump) or
   is it the same read problem? (shelfpages529.sh: done 675h.)
2. D-674o-1 glyphcheck: DONE in code 675e (GlyphPill glyphs measured in their dumped key box); HW-6 re-measures on the
   handhelds. Other pills drawn by hand round an InkGlyph (Library, Pocket, Memory card, Cube...) can get the same
   QaClip.GlyphKey mark if a run shows the same walk symptom there.
3. Native popup pages for the remaining layouts; physical pickup / return animations (Disc Station etc.); D323 / D324 in-place
   Inspect; D322 pickup latency; promote warn-uniform to FAIL; Cube first-A / L2-R2 fact (HW-4).
3b. Rule 11 (P13 / F21): 262 TRUNCATE left (static_checks). Filler item between trials: one screen per run, FullText(...,
   reserve = false) where the old Text had no reserved height + a source test (pattern: SpotifyFullText675lTest). Done:
   Now Playing (D79), Spotify screen + player (675l), notifications panel (675m), session card + status note (677a, 256 left).
   Next small ones: PlayHeader 6, StatsContent 5. Biggest left: SettingsDialog 17, SaveExplorer 13, GameDetailsOverlay 13,
   GameWindowBanner 12, YearRecapDialog 9. Popup files (GameDetailsOverlay, GameWindowBanner) need a popclip gate after (R26).
4. Requirements matrix reconciliation (docs/REQUIREMENTS_MATRIX.md) and the full backlog (docs/MASTER_BACKLOG.md).
5. Later: +N category counters.

## Needs the owner
- (done 675f: the task prompt now says -Xmx4g / -Xmx1500m and reads + regenerates docs/claude/STATUS_DASHBOARD.md every run.)
- Retire the PC hourly task "VoltCap hourly backlog run" once the cloud task's first run is verified (Claude does it then).
- Flip 2 charge-loop fix: postponed to v0.6.74 (owner 16 Nov, Flip 2 unavailable); HW-3 BLOCKED - DEVICE UNAVAILABLE.
- Local-only branches `review/ai-setup-0672` (2 commits) and `feature/dual-welcome` (7 commits) are on the PC only (bundle in
  `Claude outputs\cloudmig\local_branches.bundle`); they change CI workflows, so they were not pushed. Push / keep: owner's call.
- v0.6.73, KONKR production upgrade, merges to main: separate authorization.
