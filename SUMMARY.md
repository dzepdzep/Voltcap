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

