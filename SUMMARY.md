# Update 717a (23 Nov 2026, cloud run) - Task 1: the Tile home Row-place check (714a) can run on a handheld without R1
- CI first: Verify 310 on 7bd1dea (716b) GREEN; the later docs-only pushes (HW notes, locks) were cancelled by newer pushes.
- Answers the live session's 22:15 note (714a NOT TESTED: R1 opens on its Quick row of system tiles, so no script may open it).
  The Row tile's own handler already had a QA hook (Batch 521, `--es voltcap_test_dock`, preview only); 717a makes it gradable:
  one QA log line per change, `rowtile TILES MIDDLE->TOP spot=ROW shortcuts=none kept=MIDDLE:0.3000,0.2000`
  (rowTileQaLine in ui/QuickSettings.kt, logged in LauncherScreen's testDock effect; QA builds with the hooks file only -
  normal use unchanged).
- New tools/qa/device/row714.sh (LEVEL 1, QA app only, no key / tap except B on a first-start picker while the QA app is in
  front, snapshot saved / restored, the owner's Voltcap process checked unchanged) and tools/qa/tools/row714.py (PASS / FAIL /
  INFRA; an old build without the line = INFRA, never PASS). HW-24 714a now names the exact run.
- Tests: RowTileQaLine717aTest 3/3, TileRowPlaceSpots714aTest 4/4, Batch521Test 4/4 PASS (cloud, compiled); QA selftest 221/221
  incl. TestRow714Grader717a 5/5; static checks PASS.
- Files to copy: app/src/main/java/com/shelf/launcher/ui/QuickSettings.kt, ui/LauncherScreen.kt,
  app/src/test/java/com/shelf/launcher/ui/RowTileQaLine717aTest.kt (new), tools/qa/device/row714.sh (new),
  tools/qa/tools/row714.py (new), tools/qa/selftest/test_qa.py.
- Hardware NOT TESTED: HW-24 714a (row714.sh x1.0 + x1.3 on the KONKR).

# Update 716b (23 Nov 2026, cloud run) - CI fix: 716a's layout test stepped frames without the apply guard
- CI first: Verify 310 on 5d96b3d (716a) RED on both runners, same single failure (not infra): RoboFramesApply529Test - the
  716a test RowColumnUnderBar716aTest stepped Robolectric frames without Snapshot.sendApplyNotifications() (the 529 rule that
  keeps manual frames from reading stale state). App code unaffected; 2786 tests ran, 1 failed.
- Fix: the frame loop in RowColumnUnderBar716aTest sends apply notifications before each frame (same pattern as the other
  Robolectric tests). RowColumnUnderBar716aTest 4/4 + RoboFramesApply529Test 1/1 PASS (cloud, Maven mirror). Static checks PASS.
- Files to copy: app/src/test/java/com/shelf/launcher/ui/RowColumnUnderBar716aTest.kt.
- Hardware: none needed (test-only).

# Update 716a (23 Nov 2026, cloud run) - Task 1 Row-position: the Voltcap row never starts under the status bar
- CI first: Verify 310 on 2e865a2 (715a) GREEN.
- Audit finding (Voltcap row, status bar x Row place): Row Middle centred the row's column (game header, info / news, category
  bar, games) on the whole screen and Row Bottom stood it on the bottom edge, ignoring the status bar along the top. A column
  taller than the room the bar leaves (header + info + chips at x1.3 text, the music player in the bar) started under the clock /
  battery / player. New ui/RowColumnFit.kt keepUnderBar (applied between the Row-place alignment and rowTopPx): when the column
  fits, it stays exactly where it was (shift 0); otherwise its top stays below the bar (+4 dp), Middle also clears the legend
  band where both fit, and only a column taller than the whole free room spills at the bottom (top readable). Row Top unchanged
  (the bar is at the bottom there). Tile home and the other layouts unchanged.
- Test: RowColumnUnderBar716aTest 4/4 PASS (Robolectric layout: the old centring put a 380 dp column at 10 dp under a 40 dp
  bar, keepUnderBar at 40 dp; pure placement cases; source wiring); HeaderReachDirection715aTest 3/3 PASS. App + tests compiled
  in the cloud (Maven mirror). Static checks PASS.
- Files to copy: app/src/main/java/com/shelf/launcher/ui/RowColumnFit.kt (new), ui/LauncherScreen.kt,
  app/src/test/java/com/shelf/launcher/ui/RowColumnUnderBar716aTest.kt.
- Hardware NOT TESTED: HW-24 716a (KONKR x1.3).

# Update 715a (23 Nov 2026, cloud run) - Task 1 Row-position: the D-pad reaches an added Play part where it is drawn
- CI first: Verify 310 on 37b32a1 (714a) GREEN; e23c26c (owner 18:23 backlog, docs) running.
- Audit finding (Voltcap row, Play controls x Row place): with the game header off, an added "Play / Continue" (or "Play &
  buttons") part is the only thing that publishes Play to the D-pad, but ▲ / ▼ were chosen from the row's place (Row Top -> ▼)
  or from a stored spot of the switched-off header - ▼ went to a Play drawn above the games (it sits upper right by default).
  Now HomeReach.headerAbove decides: header off + added Play part -> where that part is drawn (MovableWidget records its drawn
  fractions in PartRects.frac, a plain map read on key presses only); everything else as before. Other layouts unchanged.
- Test: HeaderReachDirection715aTest 3/3 PASS (fails on 714a: no headerAbove / frac); TileRowPlaceSpots714aTest 4/4 PASS.
  App + tests compiled in the cloud (Maven mirror). Static checks PASS.
- Files to copy: app/src/main/java/com/shelf/launcher/ui/HomeReach.kt, ui/HomeEdit.kt, ui/LauncherScreen.kt,
  app/src/test/java/com/shelf/launcher/ui/HeaderReachDirection715aTest.kt.
- Hardware NOT TESTED: HW-24 715a (KONKR, with 714a).

# Update 714a (23 Nov 2026, cloud run) - Task 1 Row-position: Tile home keeps the round buttons' hand-placed spot per Row place
- CI first: Verify 310 on 8598c1c (713a code) GREEN; 3113e24 (P2-23c docs) running. P2 finish handed over by the live session
  (P2-23c) -> owner Task 1 (Row-position collisions) starts.
- Audit finding: changing Tile home's Row place (R1 Row tile, Settings "Where the tiles sit", the QA dock hook) DELETED the round
  buttons' spot saved "by the tiles" in Edit Home (Batch 521) - a silent erase of a saved customization (owner: never). Now the
  spot is kept aside for the place it was made at (widget_positions key "shortcuts@<PLACE>") and comes back when the tiles return
  there; at another place the buttons sit at their normal spot, so the Batch 521 collision fix stays. Other widgets untouched;
  Reset Layout clears everything as before. The row's old up / down move (no effect since Sprint 527) is still dropped.
- Test: TileRowPlaceSpots714aTest 4/4 PASS (fails on 713a: the spot was reset); Batch521Test 4/4 PASS (source strings updated).
  App + tests compiled in the cloud (Maven mirror). Static checks PASS.
- Files to copy: app/src/main/java/com/shelf/launcher/ui/QuickSettings.kt, ui/SettingsDialog.kt, ui/LauncherScreen.kt,
  app/src/test/java/com/shelf/launcher/ui/TileRowPlaceSpots714aTest.kt, ui/Batch521Test.kt.
- Hardware NOT TESTED: HW-24 714a (KONKR).

# Update 713a (23 Nov 2026, cloud run) - rule 11: shared dialog chrome shows field values, menu titles and chip labels whole
- CI first: Verify 310 on 2f17a84 (712a) GREEN; the 711a rerun (run 45195727121, attempt 2) GREEN too - the NDK failure was infra.
- P2 finish still in the live session on claude/v0674-native-pages (pushes to 15:23 UTC) - left alone; Tasks 1-4 wait for P2.
- DialogChrome.kt (shared UI): 3 TRUNCATE places -> 0. ShelfTextField's shown value (1 line), the ShelfMenuDialog sheet title
  (2 lines) and every Chip label (1 line) go through FullText (shrink readably, then slide; reserve = false so sizes stay).
  Chips size to their label as before; they only slide where their row leaves too little room.
- Test: DialogChromeFullText713aTest 2/2 PASS in the cloud (fails on 712a: 3 Ellipsis); compiled with the Maven mirror on
  (Maven Central 429 without it). Static checks PASS, TRUNCATE 69 -> 66.
- Files to copy: app/src/main/java/com/shelf/launcher/ui/DialogChrome.kt, app/src/test/java/com/shelf/launcher/ui/DialogChromeFullText713aTest.kt.
- Hardware NOT TESTED: HW-24 713a (KONKR, with 712a).

# Update 712a (23 Nov 2026, cloud run) - rule 11: RetroAchievements dialog shows its names whole
- CI first: Verify 310 on 38cd4fb (711a) FAILED on one runner only - infra: the ubuntu-24.04 runner's NDK 28 download was a bad
  zip ("Error on ZipFile unknown archive") before any build; the ubuntu-latest runner passed the full suite. Failed jobs rerun
  requested (run 45195727121).
- P2 finish still in the live session on claude/v0674-native-pages (pushes to 14:24 UTC) - left alone; Tasks 1-4 wait for P2.
- RetroAchievementsDialog.kt: 3 TRUNCATE places -> 0. Recent achievement's name + its game (2 lines each) and a saved account's
  name go through FullText (wrap, shrink readably, then slide; reserve = false).
- Test: RaDialogFullText712aTest (fails on 711a: 3 Ellipsis). App + tests compiled in the cloud; the Gradle test run here was blocked
  by Maven Central 429 (twice) - its assertions checked by an equivalent script (new code pass, 711a code fails); CI runs it. Static checks PASS, TRUNCATE 72 -> 69.
- Files to copy: app/src/main/java/com/shelf/launcher/ui/RetroAchievementsDialog.kt, app/src/test/java/com/shelf/launcher/ui/RaDialogFullText712aTest.kt.
- Hardware NOT TESTED: HW-24 712a (KONKR, with 710a / 711a).

# Update 711a (23 Nov 2026, cloud run) - rule 11: Game Journey timeline shows its text whole
- CI first: Verify 310 on 838af2e (710a) GREEN.
- P2 finish still in the live session on claude/v0674-native-pages (pushes 13:23-13:57 UTC) - left alone; Tasks 1-4 wait for P2.
- GameJourneyUi.kt (GameJourneyTimeline: game popup journey + Hero carousel journey): 3 TRUNCATE places -> 0. Step title
  (2 lines), date (1 line) and detail (3 lines, was 2) go through FullText (wrap, shrink readably, then slide; reserve = false).
- Test: GameJourneyFullText711aTest 2/2 PASS in the cloud (fails on 710a: 3 Ellipsis); compiled with the mirror on.
  Static checks PASS, TRUNCATE 75 -> 72.
- Files to copy: app/src/main/java/com/shelf/launcher/ui/GameJourneyUi.kt, app/src/test/java/com/shelf/launcher/ui/GameJourneyFullText711aTest.kt.
- Hardware NOT TESTED: HW-24 711a (KONKR, with 710a).

# Update 710a (23 Nov 2026, cloud run) - rule 11: game window performance rows show their text whole
- CI first: Verify 310 on 8f1ac4f (owner backlog 23 Nov 13:32) GREEN.
- P2 finish items (owner 13:32) left to the live session on claude/v0674-native-pages (pushes running there); Tasks 1-4 wait for P2.
- GameWindow.kt: 3 TRUNCATE places -> 0. Performance page rows: name (1 line) and value (2 lines) go through FullText (wrap,
  shrink readably, then slide; reserve = false). Main page story: kept as a short preview by design, marked qa-ok: the Details
  page (its button / a tap on the window) shows the whole story (WindowDetails, no line limit) - guarded by the test.
- Test: GameWindowFullText710aTest 3/3 PASS in the cloud (fails on 709a: 3 unmarked Ellipsis); compiled with the mirror on.
  Static checks PASS, TRUNCATE 78 -> 75.
- Files to copy: app/src/main/java/com/shelf/launcher/ui/GameWindow.kt, app/src/test/java/com/shelf/launcher/ui/GameWindowFullText710aTest.kt.
- Hardware NOT TESTED: HW-24 710a (KONKR, with 709a).

# Update 709a (23 Nov 2026, cloud run, v0.6.74 turn) - rule 11: the R1 quick panel shows its text whole
- CI first: Verify 310 on d0e153e (708a) GREEN.
- P2 left alone (owner review of the before/after package pending).
- QuickSettings.kt (R1 quick panel): 3 TRUNCATE places -> 0. The undo strip's "Changed: <setting>" (1 line), the picked tile's
  name in the tile editor (2 lines) and a tile caption with spaces (2 lines in its fixed caption box) go through FullText
  (wrap, shrink readably, then slide; reserve = false). One-word and line-break captions keep their existing clip-and-shrink path.
- Test: QuickSettingsFullText709aTest 2/2 PASS in the cloud (fails on 708a: 3 Ellipsis); compiled with the mirror on.
  Static checks PASS, TRUNCATE 81 -> 78.
- Files to copy: app/src/main/java/com/shelf/launcher/ui/QuickSettings.kt, app/src/test/java/com/shelf/launcher/ui/QuickSettingsFullText709aTest.kt.
- Hardware NOT TESTED: HW-24 709a (KONKR, with 707a / 703a).

# Update 708a (23 Nov 2026, cloud run, v0.6.74 turn) - rule 11: the second screen's folder games page shows game names whole
- CI first: Verify 310 on 673a1e0 (707a) GREEN.
- P2 left alone (owner review of the before/after package pending).
- SecondGamesPage.kt (second screen, a console folder's games): 3 TRUNCATE places -> 0. The List row name (2 lines), the
  missing-cover placeholder name (4 lines, centred, reserve = false) and the name under a tile (1 line, reserved so grid rows
  stay even) go through FullText (wrap, shrink readably, then slide). Unused TextOverflow import removed.
- Test: SecondGamesPageFullText708aTest 2/2 PASS in the cloud (fails on 707a: 3 Ellipsis); :app:compileDebugUnitTestKotlin
  BUILD SUCCESSFUL (mirror on). Static checks PASS, TRUNCATE 84 -> 81.
- Files to copy: app/src/main/java/com/shelf/launcher/ui/SecondGamesPage.kt, app/src/test/java/com/shelf/launcher/ui/SecondGamesPageFullText708aTest.kt.
- Hardware NOT TESTED: HW-26 708a (Thor lower screen, with 690a).

# Update 707a (23 Nov 2026, cloud run, v0.6.74 turn) - rule 11: the home editor's toolbar shows its title and scope line whole
- CI first: Verify 310 on c917a78 (P2-23b, includes 706a's Maven mirror) GREEN - the mirror fix is confirmed.
- P2 left alone (owner review of the before/after package pending).
- HomeEditLegend.kt (home edit mode toolbar): 2 TRUNCATE places -> 0. "Edit Home · <widget>" (+ " (hidden)") and the
  widget's scope line go through FullText (shrink, then slide), reserve = false so the toolbar keeps its size. Unused
  TextOverflow import removed.
- Test: HomeEditLegendFullText707aTest 2/2 (fails on 706a: 2 Ellipsis) + HomeEditFullText703aTest 2/2 PASS in the cloud;
  :app:compileDebugUnitTestKotlin BUILD SUCCESSFUL (mirror on). Static checks PASS, TRUNCATE 86 -> 84.
- Files to copy: app/src/main/java/com/shelf/launcher/ui/HomeEditLegend.kt, app/src/test/java/com/shelf/launcher/ui/HomeEditLegendFullText707aTest.kt.
- Hardware NOT TESTED: HW-24 707a (KONKR, ~1 min, with 703a's home edit check).

# Update 706a (23 Nov 2026, cloud run, CI fix) - CI and cloud runs fetch Maven Central artifacts from Google's mirror
- CI first: Verify 310 on 503d679 (705a) RED on both runners - Maven Central answered 429 to all three attempts (60 s / 120 s
  retries) for Robolectric's nativeruntime-dist-compat jar. App + tests COMPILED on both (compileDebugUnitTestKotlin ran); no
  test ran. Not a code failure. Maven Central refused this cloud container too (429 on the same .pom); Google's Maven Central
  mirror served it (200).
- Fix (CI / cloud only, off on the owner's PC): env VOLTCAP_MAVEN_MIRROR. settings.gradle.kts asks that mirror before
  mavenCentral() (plugins and dependencies; a 404 there still falls back to Maven Central); app/build.gradle.kts passes it to
  Robolectric's own android-all fetch (robolectric.dependency.repo.url). verify-update-310.yml sets it to
  https://maven-central.storage-download.googleapis.com/maven2/. Unset = the old repositories exactly.
- Evidence (cloud, mirror on): :app:compileDebugUnitTestKotlin BUILD SUCCESSFUL; CrossTrophiesFullText705aTest 2/2 (705a's
  test, its first run anywhere) + AchProgressCount681yTest 5/5 (Robolectric) PASS. Static checks PASS (TRUNCATE 86).
- Files to copy: settings.gradle.kts, app/build.gradle.kts, .github/workflows/verify-update-310.yml (project_copy paths).
- Hardware: none needed.

# Update 705a (23 Nov 2026, cloud run, v0.6.74 turn) - rule 11: the cross-bar trophy list shows achievement text whole
- CI first: Verify 310 on f54a2f9 (704a) GREEN on both runners.
- CrossTrophies.kt: 4 TRUNCATE places -> 0. Each row's achievement title (slides when long; no auto-fit, its size grows with
  focus), the focused row's description (2 lines) and meta line, and the comments panel title go through FullText,
  reserve = false so rows keep their spacing. Unused TextOverflow import removed.
- Test: CrossTrophiesFullText705aTest (fails on 704a: 4 Ellipsis). Static checks PASS, TRUNCATE 90 -> 86.
  Cloud :app:compileDebugUnitTestKotlin BLOCKED twice by Maven Central 429 - CI is the first compile.
- Files to copy: app/src/main/java/com/shelf/launcher/ui/CrossTrophies.kt, app/src/test/java/com/shelf/launcher/ui/CrossTrophiesFullText705aTest.kt.
- Hardware NOT TESTED: HW-24 705a (KONKR, ~2 min).

# Update 704a (23 Nov 2026, cloud run, CI fix) - Verify 310 also retries when Robolectric itself is refused by Maven Central
- CI first: Verify 310 on 0288490 (703a): ubuntu-24.04 GREEN (whole suite, incl. HomeEditFullText703aTest); ubuntu-latest RED
  with 1134 failing tests - every one "Failed to fetch maven artifact org.robolectric:android-all-instrumented ... Server
  returned HTTP response code: 429". Robolectric downloads that jar itself when a test class starts, so the 429 shows up as
  failing tests ("There were failing tests"), which 702a still ruled a code failure - no retry.
- tools/ci/retry_infra.py: counts every failed test while the output streams (TestFailures; the 4000-line tail is far shorter
  than 1134 stack traces). Failing tests are infra only when EVERY printed failure is a 429 / 5xx fetch AND their number equals
  the "N tests completed, M failed" totals; one real failure, a hidden failure, a 404, a compile error -> code, no retry.
  The real 0288490 log classifies as infra.
- Selftest TestCiRetryInfra681q +3 (2 fail / error on the old script); test_qa 216/216. Static checks PASS. No app code.
- Files to copy: tools/ci/retry_infra.py, tools/qa/selftest/test_qa.py.
- Hardware: none needed.

# Update 703a (23 Nov 2026, cloud run, v0.6.74 turn) - rule 11: the home editor shows widget names and status lines whole
- CI first: Verify 310 on 628d827 (702a) GREEN.
- HomeEditSession.kt (home edit mode): 4 TRUNCATE places -> 0. The inspector's widget name and scope line, and the widget
  list's names and Hidden / Off the screen / scope lines go through FullText (shrink to a readable size, then slide),
  reserve = false so the panel keeps its spacing. Unused TextOverflow import removed.
- Test: HomeEditFullText703aTest (fails on 702a: 4 Ellipsis). Static checks PASS, TRUNCATE 94 -> 90.
  Cloud :app:compileDebugUnitTestKotlin PASSED; the test run was blocked by Maven Central 429 - CI runs it.
- Files to copy: app/src/main/java/com/shelf/launcher/ui/HomeEditSession.kt, app/src/test/java/com/shelf/launcher/ui/HomeEditFullText703aTest.kt.
- Hardware NOT TESTED: HW-24 703a (KONKR, ~2 min).

# Update 702a (23 Nov 2026, cloud run, CI fix) - Verify 310 now retries when Maven Central refuses a test dependency
- CI first: Verify 310 on 9073da2 (701a): ubuntu-latest GREEN (whole suite, incl. RaSocialFullText701aTest); ubuntu-24.04 RED
  from Maven Central 429 only (Robolectric nativeruntime-dist-compat jar). Fourth Verify run in a row lost a runner this way.
- Cause found: tools/ci/retry_infra.py (681q) never retried these runs. When the 429 hits while :app:testDebugUnitTest
  resolves its classpath, Gradle prints "> Task :app:testDebugUnitTest FAILED" - and that line was a "code failure" sign, so
  the 429 was ruled code and the run stayed red after one attempt (the log shows no "Retrying" line). No test had run.
- Fix: a failed compile / test / lint task now counts as infra only when EVERY Gradle "What went wrong" block is a dependency
  download refused with 429 / 5xx. Failing tests, Kotlin / javac errors, the watchdog stop, a 404, a crashed test JVM or a
  failed task without any Gradle explanation all stay code - a real failure is still never retried into green.
- Checked: the real 9073da2 ubuntu-24.04 step output - old classifier "code", new "infra". Selftest TestCiRetryInfra681q
  +2 tests (the 23 Nov log shape, plain and with GitHub timestamps; five "never hide a real failure" cases); the new infra
  test FAILS on the old script. test_qa.py 213/213 OK. Static checks PASS (TRUNCATE 94, unchanged). No app code changed.
- Files: tools/ci/retry_infra.py, tools/qa/selftest/test_qa.py (CI tooling only - nothing to copy into app/).
- Hardware: none needed.

# Update 701a (23 Nov 2026, cloud run, v0.6.74 turn) - rule 11: the RetroAchievements social cards show their text whole
- CI first: Verify 310 on 0b13a64 (700a): RED on both runners from Maven Central 429 only (Robolectric nativeruntime jar download);
  :app:compileDebugKotlin and :app:compileDebugUnitTestKotlin PASSED in CI, so 700a's GuideScreen change compiles. No code failure.
- RaSocialUi.kt (Settings > Profile RetroAchievements cards): 4 TRUNCATE places -> 0. A friend's last-award line, and the
  Achievement of the Week's title, description and game · console line go through FullText (shrink to a readable size, then
  slide), reserve = false so the cards keep their spacing. Unused TextOverflow import removed.
- Test: RaSocialFullText701aTest (fails on 700a: 4 Ellipsis). Static checks PASS, TRUNCATE 98 -> 94.
  NOT compiled in the cloud: :app:compileDebugUnitTestKotlin failed twice on Maven Central 429 - CI is the first compile.
  Python mirror of the test's regexes: 1/1/1/1 on the new source.
- Files to copy: app/src/main/java/com/shelf/launcher/ui/RaSocialUi.kt, app/src/test/java/com/shelf/launcher/ui/RaSocialFullText701aTest.kt.
- Hardware NOT TESTED: HW-24 701a (KONKR, ~2 min).

# Update 700a (23 Nov 2026, cloud run, v0.6.74 turn) - rule 11: the game guide screen shows its text whole
- CI first: Verify 310 on a08de4f (699a): RED on both runners from Maven Central 429 only (1134/1134 failures are Robolectric artifact downloads; no code failure); rerun on the lock commit 3d003e4 running.
- GuideScreen.kt (game guides): 5 TRUNCATE places -> 0. The guide's title and its attribution / source line (header), the
  section titles in the contents list, and a guide's title and subtitle in the guide picker (2 lines each) go through FullText
  (shrink to a readable size, then slide), reserve = false so rows keep their spacing. Unused TextOverflow import removed.
- Test: GuideScreenFullText700aTest (fails on 699a: 5 Ellipsis). Static checks PASS, TRUNCATE 103 -> 98.
  NOT compiled in the cloud: :app:compileDebugUnitTestKotlin failed twice on Maven Central 429 (dependency download) - CI is the
  first compile. Python mirror of the test's regexes: 1/1/1/1/1 on the new source, 0/0/0/0/0 on 699a (fails as intended).
- Files to copy: app/src/main/java/com/shelf/launcher/ui/GuideScreen.kt, app/src/test/java/com/shelf/launcher/ui/GuideScreenFullText700aTest.kt.
- Hardware NOT TESTED: HW-24 700a (KONKR, ~2 min).

# Update 699a (23 Nov 2026, cloud run, v0.6.74 turn) - rule 11: the achievement hub screens show their text whole
- CI first: Verify 310 on ab8dd64 (698a): GREEN.
- HubScreens.kt (achievement hub): 5 TRUNCATE places -> 0. Progress rows (game title, 2 lines; progress line and its note such
  as "Mastered", 1 line each) and the recent-unlocks timeline (achievement title and its game · console line, 2 lines each) go
  through FullText (shrink to a readable size, then slide), reserve = false so the rows keep their spacing. Unused
  TextOverflow import removed.
- Test: HubScreensFullText699aTest (fails on 698a: 5 Ellipsis). Static checks PASS, TRUNCATE 108 -> 103.
  Cloud compile PASSED (:app:compileDebugUnitTestKotlin); test run blocked by Maven Central 429 - CI runs it (Python mirror of
  its regexes 1/1/1/1/1, and of the Ellipsis check on the 698a source: fails as intended).
- Files to copy: app/src/main/java/com/shelf/launcher/ui/HubScreens.kt, app/src/test/java/com/shelf/launcher/ui/HubScreensFullText699aTest.kt.
- Hardware NOT TESTED: HW-24 699a (KONKR, ~2 min).

# Update 698a (23 Nov 2026, cloud run, v0.6.74 turn) - rule 11: the music player pages show their text whole
- CI first: Verify 310 on f1050e8 (697a): ubuntu-latest GREEN (697a compiles, full suite incl. QuickMenuFullText697aTest
  passes), ubuntu-24.04 infra only (Maven Central 429 on a Robolectric jar).
- PlayerPages.kt (music player pages): 5 TRUNCATE places -> 0. Spotify connect hint (2 lines), playlist names in the strip (2)
  and the grid (2, centred) and the current / next synced lyric line (1 each) go through FullText (shrink to a readable size,
  then slide), reserve = false so the rows keep their spacing. Player designs unchanged. Unused TextOverflow import removed.
- Test: PlayerPagesFullText698aTest (fails on 697a: 5 Ellipsis). Static checks PASS, TRUNCATE 113 -> 108.
  Cloud compile PASSED (:app:compileDebugUnitTestKotlin, after one Maven 429 retry); test run blocked by Maven Central 429 -
  CI runs it (Python mirror of its regexes 1/2/1/1).
- Files to copy: app/src/main/java/com/shelf/launcher/ui/PlayerPages.kt, app/src/test/java/com/shelf/launcher/ui/PlayerPagesFullText698aTest.kt.
- Hardware NOT TESTED: HW-24 698a (KONKR, ~2 min).

# Update 697a (23 Nov 2026, cloud run, v0.6.74 turn) - rule 11: the in-game quick menu shows its text whole
- CI first: Verify 310 on 47ebe9b (696a) and ff677c5: ubuntu-latest GREEN (696a compiles, full suite passes), ubuntu-24.04
  infra only (Maven Central 429 on Robolectric jars).
- QuickMenuActivity.kt (quick menu over a running game): 5 TRUNCATE places -> 0. Achievements tab - game title (2 lines),
  missable lines (2), each achievement's title (1) and description (2) - and the Bluetooth tab's paired device names (1) go
  through FullText (shrink to a readable size, then slide), reserve = false so the rows keep their spacing. Unused TextOverflow
  import removed.
- Test: QuickMenuFullText697aTest (fails on 696a: 5 Ellipsis). Static checks PASS, TRUNCATE 118 -> 113.
  Cloud compile PASSED (:app:compileDebugUnitTestKotlin); test run blocked by Maven Central 429 - CI runs it (Python mirror of its
  regexes 5/5).
- Files to copy: app/src/main/java/com/shelf/launcher/ui/QuickMenuActivity.kt, app/src/test/java/com/shelf/launcher/ui/QuickMenuFullText697aTest.kt.
- Hardware NOT TESTED: HW-24 697a (KONKR, ~2 min).

# Update 696a (22 Nov 2026, cloud run, v0.6.74 turn) - rule 11: the home widget board shows app names whole
- CI first: Verify 310 on 492265c (695a): ubuntu-latest GREEN (695a compiles, full suite incl. TwinDeskFullText695aTest passes),
  ubuntu-24.04 infra only (Robolectric nativeruntime jar transform, no test ran).
- WidgetBoard.kt (home widget board, sound widget): 2 TRUNCATE places -> 0. The per-app rows and the single-app volume row show
  the app's name through FullText (shrink to a readable size, then slide), one line, reserve = false so the volume bars under them
  stay put. "Looking for apps playing sound…" is original text (kept). Unused TextOverflow import removed.
- Test: WidgetBoardFullText696aTest (fails on 695a: 2 Ellipsis). Static checks PASS, TRUNCATE 120 -> 118.
  Cloud compile blocked by Maven Central 429 (compose compiler plugin) - CI is the first compile.
- Files to copy: app/src/main/java/com/shelf/launcher/ui/WidgetBoard.kt, app/src/test/java/com/shelf/launcher/ui/WidgetBoardFullText696aTest.kt.
- Hardware NOT TESTED: HW-24 696a (KONKR, ~2 min).

# Update 695a (22 Nov 2026, cloud run, v0.6.74 turn) - rule 11: the Twin desk layout shows every text whole
- CI first: Verify 310 on 6e8e88b (694a): ubuntu-latest GREEN (694a compiles, RowInfoPanelFullText694aTest passes), ubuntu-24.04
  infra only (Maven Central 429 on the Robolectric jar) - re-run requested.
- TwinDesk.kt (Twin desk home layout): 4 TRUNCATE places -> 0. Calendar month title, the focused game's console · playtime line,
  a list row's console name and the bottom bar's category / zone name go through FullText (shrink to a readable size, then
  slide), one line each, reserve = false so the desk keeps its layout; the bar label keeps its centred weight(1f) slot and
  swaps in the same frame on a category change (rule 14). Unused TextOverflow import removed.
- Test: TwinDeskFullText695aTest (fails on 694a: 4 Ellipsis, 0 FullText). Static checks PASS, TRUNCATE 124 -> 120.
  Cloud compile blocked by Maven Central 429 (compose compiler plugin) - Python mirror of its regexes 1/1/1/1; CI is the first compile.
- Files to copy: app/src/main/java/com/shelf/launcher/ui/TwinDesk.kt, app/src/test/java/com/shelf/launcher/ui/TwinDeskFullText695aTest.kt.
- Hardware NOT TESTED: HW-24 695a (KONKR, ~2 min).

# Update 694a (22 Nov 2026, cloud run, v0.6.74 turn) - rule 11: the home row's info panel shows every text whole
- CI first: Verify 310 on 24d51ab (693a): GREEN on both runners (ubuntu-latest + ubuntu-24.04; MixerFullText693aTest passes).
- RowInfoPanel.kt (info panel beside the home row: game facts + news card): 5 TRUNCATE places -> 0. Facts line (year, genres,
  developer, console; 2 lines), score / time-to-beat / trophy chips, summary (2 lines), news source line and news headline
  (2 lines) go through FullText (shrink to a readable size, then slide), same line counts, reserve = false so the panel keeps
  its layout. Unused TextOverflow import removed.
- Test: RowInfoPanelFullText694aTest (fails on 693a: 5 Ellipsis, 0 FullText). Static checks PASS, TRUNCATE 129 -> 124.
  Cloud compile blocked by Maven Central 429 (plugin resolution) - Python mirror of its regexes 1/1/1/1; CI is the first compile.
- Files to copy: app/src/main/java/com/shelf/launcher/ui/RowInfoPanel.kt, app/src/test/java/com/shelf/launcher/ui/RowInfoPanelFullText694aTest.kt.
- Hardware NOT TESTED: HW-24 694a (KONKR, ~2 min).

# Update 693a (22 Nov 2026, cloud run, v0.6.74 turn) - rule 11: the volume mixer shows every text whole
- CI first: Verify 310 on 74035e5 (692b): ubuntu-latest GREEN (full suite, 692b's matcher fix holds), ubuntu-24.04 infra only
  (Robolectric nativeruntime jar transform failed; no test ran there).
- MixerActivity.kt (volume mixer): 7 TRUNCATE places -> 0. Title, status line (2 lines), stream labels, app names, app notes
  ("Playing ..."), volume values and the app picker's names go through FullText (shrink to a readable size, then slide), same
  line counts, reserve = false so the rows keep their layout. Unused TextOverflow import removed.
- Test: MixerFullText693aTest (fails on 692b: 7 Ellipsis, 0 FullText). Static checks PASS, TRUNCATE 136 -> 129.
  Cloud: :app:compileDebugUnitTestKotlin PASSED (first cloud compile in several runs); running the test blocked by Maven Central
  429 (test runtime classpath) - Python mirror of its regexes 0/7/7, 1, 2; CI runs it.
- Files to copy: app/src/main/java/com/shelf/launcher/ui/MixerActivity.kt, app/src/test/java/com/shelf/launcher/ui/MixerFullText693aTest.kt.
- Hardware NOT TESTED: HW-24 693a (KONKR, ~2 min).

# Update 692b (22 Nov 2026, cloud run) - CI fix: GameCardFullText692aTest matcher
- CI first: Verify 310 on 956b1d8 RED on both runners - 2 of 2736 tests failed, both in 692a's own GameCardFullText692aTest
  (blankCardNameKeepsItsLineCount, recentUnlockSlidesInsideItsRow): their `[^)]*` regex stopped at the `)` of
  `Color.White.copy(alpha = ...)` / `if (veryTight)`. GameCard.kt is correct (no app change).
- Fix: the matcher is bounded to one FullText call (`(?:(?!FullText\()[\s\S])*?`), same assertions. Python mirror of the
  regexes on GameCard.kt: 1 / 1 / 1. Static checks PASS. Cloud Gradle run blocked (Maven Central 429) - CI is the check.
- Files to copy: app/src/test/java/com/shelf/launcher/ui/GameCardFullText692aTest.kt (test only).

# Update 692a (22 Nov 2026, cloud run, v0.6.74 turn) - rule 11: the shared game card shows every text whole
- CI first: Verify 310 on 673012d (691a): ubuntu-latest GREEN (full suite - first compile of 691a passes), ubuntu-24.04 Maven
  Central 429 infra only (Robolectric jar download; no test ran there) - failed jobs re-run requested.
- GameCard.kt (the game card of the classic row and every layout that draws it): 7 TRUNCATE places -> 0. The NEW unlock pill, the
  trophy count, the blank (no-art) card's name (3 lines), the wide capsule's title (1 / 2 lines), console line and 'Played ...'
  line, and the 'Recent unlock' achievement title now go through FullText (same line counts; shrink to a readable size, then
  slide; reserve = false so the cards keep their size). The recent-unlock title is bounded (weight(1f, fill = false)) so it slides
  beside its trophy icon.
- Static TRUNCATE 143 -> 136. Test: GameCardFullText692aTest (fails on the 691a sources). Static checks PASS.
  NOT compiled in the cloud (Gradle plugin resolution blocked) - CI is the first compile. Handheld NOT TESTED -> HW-24 692a (KONKR).
- Files to copy: src_copy/ui/GameCard.kt (app/src/main/java/com/shelf/launcher/ui/GameCard.kt); test
  app/src/test/java/com/shelf/launcher/ui/GameCardFullText692aTest.kt.

# Update 691a (22 Nov 2026, cloud run, v0.6.74 turn) - rule 11: the Twin icons home shows every text whole
- CI first: Verify 310 on c166ca7 (690a): ubuntu-latest GREEN (full suite - first compile of 690a passes), ubuntu-24.04 Maven
  Central 429 infra only (no real test failure; failed jobs re-run requested).
- TwinIcons.kt (home layout Twin icons): 7 TRUNCATE places -> 0. The focused game's name and console / playtime line, the category
  card's name and hint, the small labels under the category icons and the Open bar's category / game name now go through FullText
  (one line as before: shrink to a readable size, then slide; reserve = false so the tray keeps its layout). The 'Open · <category>'
  text is now bounded (weight(1f, fill = false)) so it slides inside the bar. Unused TextOverflow import dropped.
- Static TRUNCATE 150 -> 143. Test: TwinIconsFullText691aTest (fails on the 690a sources). Static checks PASS.
  NOT compiled in the cloud (Maven Central 429 twice) - CI is the first compile. Handheld NOT TESTED -> HW-24 691a (KONKR).
- Files to copy: src_copy/ui/TwinIcons.kt (app/src/main/java/com/shelf/launcher/ui/TwinIcons.kt); test
  app/src/test/java/com/shelf/launcher/ui/TwinIconsFullText691aTest.kt.

# Update 690a (22 Nov 2026, cloud run, v0.6.74 turn) - rule 11: the Volt second screen shows every text whole
- CI first: Verify 310 on 919d362 (689a) GREEN.
- SecondScreenVolt.kt (second screen style Volt - header, achievements board, game card): 8 TRUNCATE places -> 0. The clock beside
  the page tabs, the game name (2 lines) + console line, the filter labels, the list's achievement titles + descriptions (2 lines
  each), the picked achievement's title (3 lines) and the game card's console label now go through FullText (wrap to the old line
  count, shrink to a readable size, then slide; reserve = false so the panels keep their layout). Unused TextOverflow import dropped.
- Static TRUNCATE 158 -> 150. Test: SecondScreenVoltFullText690aTest (fails on the 689a sources). Static checks PASS.
  NOT compiled in the cloud (Maven Central 429 twice) - CI is the first compile. Handheld NOT TESTED -> HW-26 (Thor, not with the
  owner).
- Files to copy: src_copy/ui/SecondScreenVolt.kt (app/src/main/java/com/shelf/launcher/ui/SecondScreenVolt.kt); test
  app/src/test/java/com/shelf/launcher/ui/SecondScreenVoltFullText690aTest.kt.

# Update 689a (22 Nov 2026, cloud run, v0.6.74 turn) - rule 11: the classic game details window shows every text whole
- CI first: Verify 310 on ce88244 (688b) - ubuntu-latest GREEN (full suite, the TV room x1.3 guard passes with 688b's narrow
  achievements form); ubuntu-24.04 red = infra (Maven Central 429 on the Robolectric android-all jar, every Robolectric class) -
  failed job re-run requested.
- GameDetailsOverlay.kt (game popup style Classic, the full-screen details window): 13 TRUNCATE places -> 1 marked. Banner title,
  info line, 'Last session', stat labels + values, page tabs, milestone line, trophy strip count + 'to go', inline achievement
  titles + descriptions now go through FullText (wrap to the old line count, shrink to a readable size, then slide; reserve =
  false so the window's layout does not move). RetroAchievements comments wrap whole (the page scrolls) instead of 4 lines + "…".
- Bug found on the way: the summary was cut to 4 lines with "…" even when no More chip was shown (the chip only appears above
  ~260 characters; a shorter summary ran past 4 lines on a narrow window / x1.3 with no way to read the rest). Now collapsed
  only where More can open it (qa-ok: TRUNCATE marked on that one line).
- Static TRUNCATE 171 -> 158. Test: GameDetailsFullText689aTest 3/3 (fails on the 688b sources) + Batch497Test + Tuner373Test
  (cloud compiled, Robolectric). Static checks PASS. Handheld NOT TESTED -> HW-24 689a.
- Files to copy: src_copy/ui/GameDetailsOverlay.kt (app/src/main/java/com/shelf/launcher/ui/GameDetailsOverlay.kt); test
  app/src/test/java/com/shelf/launcher/ui/GameDetailsFullText689aTest.kt.

# Update 688b (22 Nov 2026, cloud run) - TV room achievements page at x1.3: names and "Set order" stay whole
- The new TV guard (688a) ran in CI on b257ffe: ubuntu-latest 2726 tests, 1 failed - WindowWordsSurvey682Test[TV x1.3].otherPages,
  a REAL finding: on the set's screen (~190 dp wide at KONKR x1.3) the Achievements page gave "Set order" 20 px on the count's line
  and the achievement names 80 px beside a 36 dp badge and the points - 'Set order', 'Unbreakable' and 'The Colossus of the
  Forbidden Lands' broke inside words. (ubuntu-24.04 red = infra: Robolectric android-all-instrumented fetch failed.)
- Fix (GameWindow.kt WindowTrophies): below TROPHIES_NARROW_BELOW (240 dp, not on native pages) the order takes its own line under
  the count (progress stays as a thin line beside the count) and the points go under a 28 dp badge, so the name gets the row's
  width. Wider pages are unchanged.
- Test: the TV x1.3 otherPages case is the regression test (fails on the old code, CI b257ffe). NOT compiled in the cloud (Maven
  Central 429) - CI Verify 310 on this push is the check. Static checks PASS. Handheld NOT TESTED -> HW-24 688b.
- Files to copy: src_copy/ui/GameWindow.kt (app/src/main/java/com/shelf/launcher/ui/GameWindow.kt).

# Update 688a (22 Nov 2026, cloud run, v0.6.74 turn) - H16: the TV room window joins the window guards (53 of 53 layouts listed)
- CI first: Verify 310 GREEN on b44e5e0 (687a).
- WindowWordsSurvey682Test.NEXT_688 = TV room. Its game window opens on the set's own screen, which GameWindow reads from
  TvScreen.rect (set by the room itself); the test now places that rect where TvRoomArt.place puts the set on a full KONKR-size home
  (640 x 427 dp, no bleed) and clears it after each case. All four guards (first page words, parent clip, More, Details / Options /
  Achievements) run on it x1.0 + x1.3. New tvWindowOpensOnTheSet proves the TV path was reached: the name sits in the strip under
  the set's screen, across the set's width (TvControls, update 125) - not the ordinary window.
- NOT compiled in the cloud this run: Maven Central answered 429 twice (documented one retry); a mirror init script was refused by
  the session's permission check. CI Verify 310 on this push compiles and runs it - if a guard finds a real TV cut, fixing it is
  the next run's first item.
- Static checks PASS. Handheld NOT TESTED (test-only change).
- Files: app/src/test/java/com/shelf/launcher/ui/WindowWordsSurvey682Test.kt (test only; nothing to copy into the app sources).

# Update 687a (22 Nov 2026, cloud run, v0.6.74 turn) - H16: the last game windows under the window guards (52 of 53 layouts)
- CI first: Verify 310 on cbf0226 (686a) still running at start; the lock commit fcb4998's red was infra (ubuntu-24.04 runner
  could not transform robolectric nativeruntime-dist-compat - dependency resolution, no test ran), ubuntu-latest GREEN.
- WindowWordsSurvey682Test.NEXT_687: Terminal (run screen), Arcade (cabinet menu), Arctic (drawer), Artwork library (placard),
  Layout Studio (studio panel), plus the layouts that share an already-guarded scene (Grid, Console stack, Coverflow details,
  Twin desk) joined all four guards (first page words, parent clip, More, Details / Options / Achievements) at KONKR size x1.0 + x1.3.
- Result: all 9 clean - no window changed. 52 of 53 layouts are now under the guards; only the TV room is not (its window opens
  on the TV's own screen and needs the home's tvRect, not reachable from GameWindow alone).
- Checks (cloud compiled): 66 survey cases pass + 6 skipped (no More on Terminal / Arcade / Arctic), ParentClipProbe683Test 5/5;
  static checks PASS. Cloud infra: Robolectric's android-all jar 429 from Maven Central again - fetched from the Google Maven
  Central mirror into ~/.m2. Handheld NOT TESTED (test-only change, nothing new to check on hardware).
- Files: app/src/test/java/com/shelf/launcher/ui/WindowWordsSurvey682Test.kt (test only; nothing to copy into the app sources).

