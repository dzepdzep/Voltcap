# Update 767a (26 Nov 2026, cloud run) - P12: TalkBack names for icon-only tap controls
- CI first: Verify 310 on b08dfd9 (766a) GREEN.
- Matrix P12 (TalkBack labels). Icon-only controls that react only to raw taps (pointerInput / detectTapGestures) had no
  name, no role and no click action - TalkBack read nothing and could not press them: Icon bar's three round buttons next to
  Play (Options, Save sync, Achievements) and the classic card's favourite star + More dots (GameCard MiniIcon). The other
  tap-only icon controls found by a scan already carry a name or sit next to their own text.
- Fix: shared modifier tapButtonSemantics(label, action) in DialogChrome.kt (contentDescription, Role.Button, onClick action
  named like the control); BarRound / MiniIcon take a label ("Options", "Save sync", "Achievements", "Add to favourites" /
  "Remove from favourites", "More options"). Touch, controller focus (StyleNav), look and second screen unchanged.
- Test: TapButtonLabels767aTest 3/3 (Robolectric: name, Button role, the semantics click presses it once; source checks of
  both controls - fails on 766a: tapButtonSemantics missing). Compiled: :app:compileDebugUnitTestKotlin (cloud). Static checks
  [PASS]. Full suite: CI. TalkBack on a handheld NOT TESTED (HW-24 767a).
- Files: app/src/main/java/com/shelf/launcher/ui/DialogChrome.kt, ui/StyleIconBar.kt, ui/GameCard.kt,
  app/src/test/java/com/shelf/launcher/ui/TapButtonLabels767aTest.kt; docs (matrix P12, HANDOFF, HW queue, dashboard).

# Update 766a (26 Nov 2026, cloud run) - P5 polish: Constellation's info column ends above the legend; the console in the light opens on a tap
- CI first: Verify 310 on dd3f057 (765a) GREEN.
- P5 third-priority polish, Constellation. KONKR 640 x 427 dp: the info column under the console in the light starts at
  0.64 h (273 dp) and grew down unbounded; above the legend (54 dp, as Generations keeps) it had ~100 dp. A two-line 24 sp
  name + facts + favourites needed ~103 dp at x1.0 and ~155 dp at x1.3 with the facts wrapped - ~55 dp behind the legend.
- Fix (CategoryLooks.kt): the column bounded (constellationInfoRoom = 0.36 h - 54 dp, min 44 dp; CONST_INFO_TOP 0.64 kept,
  under the object's foot at 0.60), the name weighted (gives way first: shrinks to 14.4 sp, then slides; reserve off), the
  facts (up to two lines) and favourites through FullText; "1 favourite" singular. Touch: the console in the light and its
  info take lookTap (a tap opens it; before only the dimmed ring node did). Ring, keys, second screen unchanged; rule 14
  unaffected (no new transition).
- Test: ConstellationInfoRoom766aTest 3/3 (fails on 765a: helpers missing; old overflow case asserted > 50 dp),
  CategoryLooksInventory759aTest 4/4, CategoryStable394Test 6/6, GenerationsInfoRoom761aTest 3/3. Compiled:
  :app:compileDebugUnitTestKotlin (cloud; one Maven Central 429, retried). Static checks [PASS]. Full suite: CI. Hardware NOT
  TESTED (HW-24 766a).
- Files: app/src/main/java/com/shelf/launcher/ui/CategoryLooks.kt,
  app/src/test/java/com/shelf/launcher/ui/ConstellationInfoRoom766aTest.kt; docs (CATEGORY_STYLES_P5, matrix P5, HANDOFF, HW queue,
  dashboard).

# Update 765a (26 Nov 2026, cloud run) - P5 polish: Pocket Dashboard's preview text stays inside the panel
- CI first: Verify 310 on b995c00 (764a) GREEN.
- P5 third-priority polish, Pocket Dashboard. KONKR 640 x 427 dp: the preview is 46 % of the height (~196 dp); inside its
  26 dp padding the text column had 144 dp. Facts + a two-line 30 sp name + games / favourites + "Last played" needed ~170 dp
  at x1.0 and more at x1.3 (any name) - the vertically centred column overflowed both ends of the rounded, clipped panel: the
  facts line and the last-played line were cut.
- Fix (CategoryLooks.kt): top / bottom padding 18 dp (sides 26 as before, DASH_PAD_H / DASH_PAD_V); the column bounded
  (dashboardTextRoom = preview less padding); the name weighted (gives way first), line height 1.133 em (= the old 34 sp at
  30 sp, now follows the shrink) and its largest size from the room (dashboardNameSp: 30 sp at x1.0 KONKR, ~24 sp at x1.3,
  30 sp on the Thor's top screen; never below 18), so even a name that slides is whole in height; facts through FullText
  (one line, slide if long). Icons row, glide, keys, second screen unchanged; rule 14 unaffected (no new transition).
- Test: DashboardTextRoom765aTest 3/3 (fails on 764a: helpers missing; old 144 dp overflow cases asserted),
  CategoryLooksInventory759aTest 4/4, CategoryStable394Test 6/6. Compiled: :app:compileDebugUnitTestKotlin (cloud). Static
  checks [PASS]. Full suite: CI. Hardware NOT TESTED (HW-24 765a).
- Files: app/src/main/java/com/shelf/launcher/ui/CategoryLooks.kt,
  app/src/test/java/com/shelf/launcher/ui/DashboardTextRoom765aTest.kt; docs (CATEGORY_STYLES_P5, matrix P5, HANDOFF, HW queue,
  dashboard).

# Update 764a (26 Nov 2026, cloud run) - P5 polish: Grid's ▲ ▼ stay in the column, ◀ ▶ in the row
- CI first: Verify 310 on 46666fc (763a) GREEN (the D358 Case flow flake did not recur).
- P5 third-priority polish, Grid (wide cards in two rows, filled column by column). ▲ ▼ stepped through the list (index ± 1,
  wrapping): ▲ on a top card jumped to the previous column's bottom, ▼ on a bottom card to the next column's top, ▼ on the
  last card of an odd count wrapped to the very first; ◀ from the first card landed in the middle column, ▶ from a bottom
  card skipped the shorter last column.
- Fix: gridStep (CategoryLooks.kt, pure): ▲ ▼ stay inside the column and stop at its top / bottom / an empty slot; ◀ ▶ keep
  the row and wrap round the columns (a shorter last column gives its card); picked up (Start / hold) it stops at the ends.
  Used by platformBrowserKey only for Grid's two-row case (other looks unchanged). A picked-up card's hint reads "◀ ▶ ▲ ▼"
  with wide cards (tall cards: one row, "◀ ▶"). Text room checked: KONKR x1.3 card ~141 dp high, logo + name + count ~107 dp.
- Test: GridSteps764aTest 5/5 (fails on 763a: gridStep missing; old index ± 1 values noted per case), CategoryLooksInventory759aTest
  4/4, CategoryStable394Test 6/6, PlatformBrowserFullText678jTest 1/1. Compiled: :app:compileDebugUnitTestKotlin (cloud).
  Static checks [PASS]. Full suite: CI. Hardware NOT TESTED (HW-24 764a).
- Files: app/src/main/java/com/shelf/launcher/ui/CategoryLooks.kt, app/src/main/java/com/shelf/launcher/ui/PlatformBrowser.kt,
  app/src/test/java/com/shelf/launcher/ui/GridSteps764aTest.kt; docs (CATEGORY_STYLES_P5, matrix P5, HANDOFF, HW queue, dashboard).

# Update 763a (26 Nov 2026, cloud run) - CI flake triage + P5 polish: System Vault's reader screen wraps, Wall's count whole
- CI first: Verify 310 on 7c5bf06 (762a) RED on one of two runners: EndlessFillLitRoboTestShard3 [CASEFLOW n=5 fill=true]
  "Unsupported concurrent change during composition" (Compose Recomposer.applyAndCheck; a state written by composition and by
  another thread in the same frame). The twin runner (ubuntu-latest) ran the same suite on the same commit GREEN; 762a touched
  only Slides' text (not Case flow). Recorded as flaky-test defect D358 (stack from the CI test report; the writer is not
  identified - candidates noted); this push re-runs the suite. No assertion weakened.
- P5 polish, System Vault: the reader's screen is ~202 dp of text width on the KONKR (640 x 427 dp); in monospace at x1.3 its
  "READING  ·  <SYSTEM>" header and its count / favourites / maker / year line need ~280 dp and only slid on one line. Both now
  wrap onto a second line first (VAULT_SCREEN_LINES = 2, reserved - the covers between them keep one height from system to
  system: ~90 dp KONKR x1.3, ~36 dp on a 360 dp-high phone). Rack modules, reader, motion and keys (▲ ▼) unchanged.
- Wall: the game count was a plain one-line Text in an editable box (cut when the box was made smaller in the wall editor) -
  now FullText (shrink, then slide; no reserve; box unchanged).
- Test: VaultWallText763aTest 3/3 (source guards fail on 762a), PlatformBrowserFullText678jTest 1/1 (FullText count 9 -> 10),
  SlidesFactsFullText762aTest 2/2, CategoryLooksInventory759aTest 4/4, CategoryStable394Test. Compiled:
  :app:compileDebugUnitTestKotlin (cloud). Static checks [PASS]. Full suite: CI. Hardware NOT TESTED (HW-24 763a).
- Files: app/src/main/java/com/shelf/launcher/ui/CategoryLooks.kt, app/src/main/java/com/shelf/launcher/ui/PlatformBrowser.kt,
  app/src/test/java/com/shelf/launcher/ui/VaultWallText763aTest.kt,
  app/src/test/java/com/shelf/launcher/ui/PlatformBrowserFullText678jTest.kt; tools/qa/defects.json (D358); docs.

# Update 762a (25 Nov 2026, cloud run) - matrix P5 polish: Slides' game count and maker / year line are never cut
- CI first: Verify 310 on 776a3c1 (761a) GREEN.
- P5 third-priority polish, third look (Slides). The slide's parts sit in boxes of a fixed share of the slide; the name and
  logo lines went through FullText (678j), but the game count and the maker / year line were plain one-line Texts that clip.
  On the KONKR (640 x 427 dp) the maker box is 192 dp wide: the built-in "MICROSOFT 2005" at x1.3 already needs ~179 dp, a
  scraped maker ("Sony Computer Entertainment 1994") or a box made smaller in the slide editor was cut off; "123 Games
  (45 Favorites)" at x1.3 needs ~290 of the count box's 320 dp.
- Fix (PlatformBrowser.kt SlidePart COUNT / MAKER): both through FullText (shrink to the readable minimum, then slide), no
  reserved height - the boxes and the slide's layout (and saved slide designs) are unchanged. Keys / swipe unchanged
  (◀ ▶, AnimatedContent keyed by the platform you're on, not a home category change).
- Test: SlidesFactsFullText762aTest 2/2 (source guards fail on 761a; maker box is tight at x1.3), PlatformBrowserFullText678jTest
  1/1 (FullText count 7 -> 9, still 0 ellipses), CategoryLooks527Test 7/7. Compiled: :app:compileDebugUnitTestKotlin (cloud).
  Static checks [PASS]. Full suite: CI. Hardware NOT TESTED (HW-24 762a, ~1 min KONKR).
- Files: app/src/main/java/com/shelf/launcher/ui/PlatformBrowser.kt,
  app/src/test/java/com/shelf/launcher/ui/SlidesFactsFullText762aTest.kt,
  app/src/test/java/com/shelf/launcher/ui/PlatformBrowserFullText678jTest.kt; docs (CATEGORY_STYLES_P5, matrix P5, HANDOFF,
  HW queue, dashboard).

# Update 761a (25 Nov 2026, cloud run) - matrix P5 polish: Generations' info block never rises into the handheld lane
- CI first: Verify 310 on 3beb14f (760a) GREEN.
- P5 third-priority polish, second look (Generations). Arithmetic on the KONKR (640 x 427 dp): the line sits at 46 % of the
  height, the focused handheld (scale 1.15) and its year end ~291 dp down, the info block's bottom is 54 dp above the screen's
  foot, so the block has ~82 dp. At x1.3 a two-line name needs ~125 dp; the block (unbounded, growing upwards) covered the
  focused handheld and its year.
- Fix (CategoryLooks.kt GenerationsLook): the block's height is capped at the handheld lane's foot (generationsInfoRoom;
  GEN_AXIS / GEN_YEAR_W / GEN_INFO_BOTTOM shared with the drawing; 56 dp minimum for short phone screens). The name has
  weight(fill = false), so the generation line and the facts (two lines) are measured first and the name gives way: FullText
  shrinks it to 10 sp, then slides it - never "...". The generation line and the facts now go through FullText too (they slide
  instead of being cut). At x1.0 a one-line name is unchanged.
- Test: GenerationsInfoRoom761aTest 3/3 (room arithmetic: x1.0 fits, x1.3 old need > room, the name keeps a readable line,
  phone minimum; source guards fail on 760a); StagePlateRoom760aTest 2/2, CategoryStable394Test 6/6. Compiled:
  :app:compileDebugUnitTestKotlin (cloud; Maven Central 429 twice, retried). Static checks [PASS]. Full suite: CI. Hardware
  NOT TESTED (HW-24 761a, ~1 min KONKR).
- Files: app/src/main/java/com/shelf/launcher/ui/CategoryLooks.kt,
  app/src/test/java/com/shelf/launcher/ui/GenerationsInfoRoom761aTest.kt; docs (CATEGORY_STYLES_P5, matrix P5, HANDOFF, HW queue,
  dashboard).

# Update 760a (25 Nov 2026, cloud run) - matrix P5 polish: System Stage's museum plate never rises over the plinth and the object
- CI first: Verify 310 on 359aa12 (759a) GREEN.
- P5 third-priority polish, first look (System Stage). Arithmetic on the KONKR (640 x 427 dp, plinth 26 % of the height): the
  plate's room on the plinth front is ~83 dp; at x1.3 a two-line name over two lines of facts needs ~100 dp, and the plate
  (unbounded) rose over the plinth's lit top and the foot of the console standing on it.
- Fix (CategoryLooks.kt StageLook): the plate's height is capped at the object's foot (stagePlateRoom; STAGE_FOOT_SINK /
  STAGE_PLATE_BOTTOM name the two factors both the plate and the object use). The name has weight(fill = false), so the facts
  (maker / year / count, two lines) are measured first and the name gives way: FullText shrinks it to 10 sp, then slides it -
  never "...". At x1.0 a one-line name over two lines of facts is unchanged.
- Test: StagePlateRoom760aTest 2/2 (room arithmetic: x1.0 fits, x1.3 old need > room, the name keeps a readable line; source
  guards fail on 759a); CategoryLooks527Test 7/7, CategoryLooksInventory759aTest 4/4. Compiled: :app:compileDebugUnitTestKotlin
  (cloud). Static checks [PASS]. Full suite: CI. Hardware NOT TESTED (HW-24 760a, ~1 min KONKR).
- Files: app/src/main/java/com/shelf/launcher/ui/CategoryLooks.kt,
  app/src/test/java/com/shelf/launcher/ui/StagePlateRoom760aTest.kt; docs (CATEGORY_STYLES_P5, matrix P5, HANDOFF, HW queue,
  dashboard).

# Update 759a (25 Nov 2026, cloud run) - matrix P5: the 17 category-screen looks reconciled; Blades + Pocket Dashboard key hints fixed
- CI first: Verify 310 on 7a73a1b (758a) GREEN.
- Next-item check: the HANDOFF "NEXT" lines of 756a-758a still named badgecheck D357 and the profile-picture corner - both were
  done in 680b / 681e (HANDOFF 17 Nov, 22 Nov 689a); H16 is guarded 53/53; owner Tasks 2-4 done in code (723a-725a); Save Sync
  port blocked in the cloud (the fix/save-sync branch exists only as a ~100 KB base64 project doc, not a git ref). Picked
  matrix P5 (OPEN: "list and per-style status to be reconciled").
- docs/claude/CATEGORY_STYLES_P5.md: the 17 looks (Features.PlatformView) with what draws each, its keys, the home layouts it
  follows, unit and hardware evidence (last: KONKR x1.0 / x1.3 12 Nov on an older branch), owner 0 / 17, remaining polish.
- Found on the way: Settings > Categories > Category screen told users to press ▲ ▼ for System Blades and Pocket Dashboard,
  but both step with ◀ ▶ only (lookNavOf ROW; ▲ ▼ do nothing). Lines now say ◀ ▶; Pocket Dashboard's no longer calls its row
  of round icons "a compact list". No behaviour change.
- Test: CategoryLooksInventory759aTest 4/4 (exactly 17 looks; each drawn by PlatformBrowser; each Settings line starts with the
  picker name; the arrows in a line = the keys that move it - fails on the 758a text); CategoryLooks527Test 7/7,
  PlatformBrowserFullText678jTest 1/1. Compiled: :app:compileDebugUnitTestKotlin (cloud). Static checks [PASS]. Full suite: CI.
  Hardware NOT TESTED (HW-24 759a, ~5 min KONKR).
- Files: app/src/main/java/com/shelf/launcher/ui/PlatformBrowserSettings.kt,
  app/src/test/java/com/shelf/launcher/ui/CategoryLooksInventory759aTest.kt; docs/claude/CATEGORY_STYLES_P5.md; docs (matrix P5,
  progress.json, HANDOFF, HW queue, dashboard).

# Update 758a (25 Nov 2026, cloud run) - rule 11: the home's category name and the Tiles game name no longer end in "..."
- CI first: Verify 310 on d8a7b15 (757b) GREEN.
- Rule 11 (no "..." truncation), the last two places in the app sources: the home's category name (LauncherScreen) goes
  through FullText - the same readable shrink as before, then it slides along its line; one line's full-size height is kept so
  nothing moves when a long name shrinks. It stays inside CategoryNameSwap, so a category change still swaps the name in place
  (rule 14). The Tiles layout's game name (TileHome) goes through FullText with its own one-go fitting kept (autoFit off - no
  pop-in, update 362) and its fixed name box kept (reserve off - the tile row never moves); a name too long for two lines at
  11 sp slides along one line. Unused TextOverflow import removed from TileHome.
- Test: CategoryTileFullText758aTest 3/3 (fails on the 757b sources); CategoryStable394Test 6/6. Static TRUNCATE 2 -> 0, [PASS].
  Compiled: :app:compileDebugUnitTestKotlin (cloud). Full suite: CI. Hardware NOT TESTED (HW-24 758a).
- Files: app/src/main/java/com/shelf/launcher/ui/LauncherScreen.kt, ui/TileHome.kt,
  app/src/test/java/com/shelf/launcher/ui/CategoryTileFullText758aTest.kt; docs (matrix P13, HANDOFF, HW queue, dashboard).

# Update 757b (25 Nov 2026, cloud run) - rule 11: printed case backs, custom layout texts and the Magazine story no longer end in "..."
- CI first: Verify 310 on dce5a85 (757a) GREEN.
- Rule 11 (no "..." truncation): the printed case back (CollectorBacks) draws its detail lines (publisher, release, players)
  and its foot note whole - a smaller size (to 70 %), then narrower letters - instead of TextUtils.ellipsize (new pure helper
  wholeLineFit). A custom layout's text pieces (StyleCustom) keep the piece's own line count but shrink to a readable size and
  then slide instead of "..." (FullText, no reserved height - the piece's box is the user's). The Magazine home's story keeps
  filling its column and, when it is longer, now ends on the "Full story on Details ›" cue line like every other cut summary
  (owner-approved D255). Kept on purpose with qa-ok: ScrollingName's unfocused name in a fixed-height row (the focused one
  slides whole), StoryLines (summary in the room left; whole on Details), the case back's title / paragraph StaticLayouts
  (only past their readable minimum).
- Test: CollectorFit757bTest 4/4 (fails on the 757a sources); DockBadgeFullText757aTest, LookTabsFullText756aTest,
  InputContract462Test, Batch503Test, Batch520Test PASS. Static TRUNCATE 8 -> 2 (LauncherScreen category name, TileHome game
  name - both need the rule 14 category-switch care). Cloud compiled (compileDebugUnitTestKotlin).
- Files: app/src/main/java/com/shelf/launcher/ui/{CollectorBacks,StyleCustom,StyleMagazine,ScrollingName,WholeRows}.kt,
  app/src/test/java/com/shelf/launcher/ui/CollectorFit757bTest.kt (cloud: edited in app/ directly).
- Not tested: hardware (HW-24 757b KONKR).

# Update 757a (25 Nov 2026, cloud run) - rule 11: classic row game name, badge wall line and Pocket Quest Play key show their text whole
- CI first: Verify 310 on afe1c5e (756a) GREEN.
- Rule 11 (no "..." truncation): GameDock's home game name (two lines, then a slow slide; same size as before - autoFit off -
  and no reserved second line, so nothing around it moves while the selection moves), BadgeWall's percentage / points line and
  Pocket Quest's Play / Continue key (readable size, then slide) move to FullText. HomeChrome's status-bar progress counters
  keep their ellipsis on purpose (qa-ok: live counters that change every second in the bar's leftover room - with the player
  there that box is nearly empty; the whole readouts are in the Background work dialog: tap them, or Notifications). FitText's
  doc comment marked qa-ok.
- Test: DockBadgeFullText757aTest 2/2 (fails on the 756a sources); 15 source-reading suites over the touched files PASS
  (PqTabs456Test, Batch478-521 set, CategoryBar453Test, ClipLightTest, ...). Static TRUNCATE 13 -> 8. Cloud compiled
  (compileDebugUnitTestKotlin).
- Files: app/src/main/java/com/shelf/launcher/ui/{GameDock,BadgeWall,StylePocketQuest,HomeChrome,FitText}.kt,
  app/src/test/java/com/shelf/launcher/ui/DockBadgeFullText757aTest.kt (cloud: edited in app/ directly).
- Not tested: hardware (HW-24 757a KONKR).

# Update 756a (25 Nov 2026, cloud run) - rule 11: state badges, Cube hub labels, Cross progress line, Volt tabs and second-screen names show their text whole
- CI first: Verify 310 on e0604ce (755a) GREEN. P2 branch moved (owner's 682bb) - untouched by this run.
- Rule 11 (no "..." truncation): six texts move from `maxLines + TextOverflow.Ellipsis` to FullText (wrap, readable size,
  then a slow slide): GameLookUi state badge (MASTERED / PERFECT ...), CubeHub console label (+ its "N games" line, which was
  clipped silently), StyleCrossMenu's "e / t achievements · N %" line (Trophies category; "loading…" is the text's own
  ellipsis), VoltUi tab names, SecondShowcase game name (3 lines, no reserved height - centred alone) and SecondScreen details
  name (2 lines, no reserved height, so the console line under it sits where it did).
- Test: LookTabsFullText756aTest 2/2 (fails on the 755a sources); PickerGuideFullText754aTest 2/2, CategoryStable394Test 6/6.
  Static TRUNCATE 19 -> 13, TRUNCATE-CLIP 269 (unchanged). Cloud compiled (compileDebugUnitTestKotlin).
- Files: app/src/main/java/com/shelf/launcher/ui/{GameLookUi,CubeHub,StyleCrossMenu,VoltUi,SecondShowcase,SecondScreen}.kt,
  app/src/test/java/com/shelf/launcher/ui/LookTabsFullText756aTest.kt (cloud: edited in app/ directly).
- Not tested: hardware (HW-24 756a KONKR; second screen with HW-26 Thor).

# Update 755a (25 Nov 2026, cloud run) - P2 emulator review: Cross's default Achievements list is now checked instead of left untested
- CI first: Verify 310 on 9e7a8de (754a) GREEN. P2 emulator review 45633462166 (P2:CROSS on 777afa8) VERDICT NOT TESTED at
  x1.3 + x1.0: the window walk itself was clean (all pages shot, destructive row armed and left, 0 launches, 0 FATAL, restore
  OK), but the walk logged Cross's default list (cross_trophies ON, update 127) as NOT TESTED, which the grader counts.
- P2 branch 8dfd4f6 (fast-forward on 777afa8): p2review's new menu_list() runs after the Cross walk with the setting back on
  (only when it was on before): opens the game's achievements the way the window's Achievements key does (QA hook
  voltcap_test_achlist = the same viewingAchievementsForGame state; no key that could start a game), requires the menu's own
  list as a QA-app dialog with the focus (not the home, no game window) and more "earned / total  ·  N %" lines than the home,
  then one B (wg_key_dlg, only while that list has the focus) must give the home back. Not opened / not closed = FAIL; a
  missing dump = NOT TESTED. Shots CROSS_mlist_home / _open / _closed. No assertion weakened.
- Selftest 282/282 (new behavioural test with stubbed device calls: opened + closed, not opened = FAIL with no key, B leaving it
  open = FAIL; fails on the old script). Review dispatched: run 45644606025 (P2:CROSS on 8dfd4f6).
- Copy: nothing for app/ (QA tools on the P2 branch only). Dev line: hand-off docs only.
- Result: review 45644606025 VERDICT PASS (x1.3 + x1.0; the menu list opened, count lines 0 -> 1, and B closed it to the home
  at both scales; 0 launches). Verify 310 on 8dfd4f6 GREEN. Every P2 review set now has an emulator PASS: one owner action
  (owner_actions.json) lists the runs holding the sheets and asks which Achievements view Cross should show by default.
- Not tested: hardware (none needed for a QA-script change). EMULATOR evidence only.

# Update 754a (25 Nov 2026, cloud run) - P2 emulator review: Cross's walk fixed (its achievements open the menu's own list by default); rule 11: emulator picker, first-run accounts, who-is-playing, presets, manual reader, second-screen guide / details and game-look options show their text whole
- CI first: Verify 310 on b56caf8 (753a) GREEN. P2 emulator review 45623176605 (GREENCORE ARTLIB CROSS LIVE on 3077309)
  VERDICT FAIL on CROSS only (x1.3 + x1.0); GREENCORE (two-step fix of 753a works), ARTLIB and LIVE PASS at both scales, 0
  launches, 0 FATAL, restore OK. Cause (QA script, no app defect): Cross's "Achievements in the menu's style" (cross_trophies,
  update 127, ON by default) opens the menu's own full-screen list from the window's Achievements key, so the window has only
  Game / Information / Options pages - six R1 presses came back to the first page and Down Down from Start game landed on
  Options. Nothing was pressed on a key (the guard held).
- P2 branch 777afa8 (fast-forward on the owner's live 4dfb336): p2review turns cross_trophies off for the Cross walk only (QA
  app; old value read from the app's own log and put back, also when the window does not open; the snapshot restores every
  setting anyway), so the window's own Achievements page (742a) is reviewed; the default list is logged NOT TESTED by this walk.
  Selftest 281/281 (1 new, fails on the old script). Review dispatched: run 45633462166 (P2:CROSS).
- Design note for the owner (not decided here): with the default setting a player never sees Cross's 742a Achievements page -
  the update-127 menu list takes its place. Which one Cross should show by default is the owner's call.
- Dev line, rule 11 (matrix P13): EmulatorPickerDialog.kt (subtitle, two lines), FirstRunDialog.kt ("Connected" mark),
  WhoIsPlaying.kt (player name), PresetsCard.kt (preset name), ManualReader.kt (title + page), GuideSecondScreen.kt (guide title,
  two lines), GameLookPicker.kt (option label, two lines), SecondScreenDetails.kt (console label) go through FullText (wrap,
  readable size, then slide). 8 TRUNCATE places -> 0.
- Test: PickerGuideFullText754aTest 2/2 PASS in the cloud (fails on 753a: 8 Ellipsis); FolderAppsFullText753aTest 2/2 PASS.
  App + tests compiled. Static checks PASS, TRUNCATE 27 -> 19.
- Files to copy: app/src/main/java/com/shelf/launcher/ui/EmulatorPickerDialog.kt, ui/FirstRunDialog.kt, ui/WhoIsPlaying.kt,
  ui/PresetsCard.kt, ui/ManualReader.kt, ui/GuideSecondScreen.kt, ui/GameLookPicker.kt, ui/SecondScreenDetails.kt,
  app/src/test/java/com/shelf/launcher/ui/PickerGuideFullText754aTest.kt.
- Not tested: hardware (HW-24 754a KONKR x1.3 + x1.0; the second-screen pieces on the Thor with HW-26).

# Update 753a (25 Nov 2026, cloud run) - P2 emulator review: Green Core's game window opened as a two-step home; rule 11: folders, artwork gallery, apps library, settings groups / sliders, L1 shortcuts and the Tiles achievement count show their text whole
- CI first: Verify 310 on b8d00ea (752a) GREEN. P2 emulator review 45612718815 (CAMERA PANELS SHOP GREENCORE on 12f3b28)
  VERDICT INFRA: x1.0 PASS (all four), x1.3 CAMERA / PANELS / SHOP PASS, GREENCORE aborted safely ("game window expected",
  nothing pressed). Cause (QA script, no app defect): Green Core's home opens on its MENU page - its first A opens GAMES, the
  window is one A further (StyleGreenCore greenCoreKey); x1.0 only passed because GAMES was still open from the x1.3 walk.
- P2 branch 3077309 (fast-forward on the owner's live c4dbd6e): p2review TWO_STEP="CUBE GREENCORE" (the existing "already
  open" check covers a GAMES page left open). Selftest 280/280 (1 new, fails on the old script). Review dispatched: run
  45623176605 (P2:GREENCORE ARTLIB CROSS LIVE).
- Dev line, rule 11 (matrix P13): FolderIcons.kt (folder menu name, top-screen preview name), ArtworkGallery.kt (label line),
  AppsLibraryDialog.kt (app name), FoldGroup.kt (summary), SettingsSlider.kt (value), ShortcutsPanel.kt (app name, two lines)
  and TileHome.kt (achievement count pill; the home's game name is a later batch) go through FullText (wrap, readable size,
  then slide). 8 TRUNCATE places -> 0.
- Test: FolderAppsFullText753aTest 2/2 PASS in the cloud (fails on 752a: 8 Ellipsis); ShowcaseFullText752aTest 2/2,
  CategoryStable394Test 6/6 PASS. App + tests compiled (Maven Central 429 -> VOLTCAP_MAVEN_MIRROR). Static checks PASS,
  TRUNCATE 35 -> 27.
- Files to copy: app/src/main/java/com/shelf/launcher/ui/FolderIcons.kt, ui/ArtworkGallery.kt, ui/AppsLibraryDialog.kt,
  ui/FoldGroup.kt, ui/SettingsSlider.kt, ui/ShortcutsPanel.kt, ui/TileHome.kt,
  app/src/test/java/com/shelf/launcher/ui/FolderAppsFullText753aTest.kt.
- Not tested: hardware (HW-24 753a, KONKR x1.3 + x1.0, with 749a-752a).

# Update 752a (25 Nov 2026, cloud run) - P2 review of Case flow / Orbit / Airy / Bubble home PASS; rule 11: quick-settings tiles, Arch stacks, Twin showcase and cartridge shell picker show their text whole
- CI first: Verify 310 on 4f14f72 (751a) GREEN; on the P2 branch 12f3b28 (owner's live 682az) GREEN. P2 emulator review
  45603942465 (P2:CASEFLOW ORBIT AIRY BUBBLES on 1dbd311) VERDICT PASS at x1.3 and x1.0, 0 launches. Next set dispatched:
  run 45612718815 (P2:CAMERA PANELS SHOP GREENCORE, on 12f3b28).
- Dev line, rule 11 (matrix P13): QsTilesCard.kt (tile name, "In the menu" mark), StacksArch.kt (game name - StableFullText so
  it never pops in late inside its gameSwap - and the storage / games line), TwinShowcase.kt (category name, game name under the
  grid, both StableFullText) and CartShellPicker.kt (shell name, edition name - two lines kept so the cards line up) go through
  FullText (wrap, readable size, then slide). 8 TRUNCATE places -> 0.
- Test: ShowcaseFullText752aTest 2/2 PASS in the cloud (fails on 751a: 8 Ellipsis); CategoryStable394Test 6/6 PASS.
  App + tests compiled. Static checks PASS, TRUNCATE 43 -> 35.
- Files to copy: app/src/main/java/com/shelf/launcher/ui/QsTilesCard.kt, ui/StacksArch.kt, ui/TwinShowcase.kt,
  ui/CartShellPicker.kt, app/src/test/java/com/shelf/launcher/ui/ShowcaseFullText752aTest.kt.
- Not tested: hardware (HW-24 752a, KONKR x1.3 + x1.0, with 749a-751a).

# Update 751a (25 Nov 2026, cloud run) - P2 emulator review fixed for "remove" confirmations and Bubble home; rule 11: save inspector, save sync, Steam import and RomM browser show their text whole
- CI first: Verify 310 on aaf96c1 (750a) GREEN. P2 emulator review 45593862011 (CASEFLOW ORBIT AIRY BUBBLES, on a524ded)
  VERDICT INFRA: (1) CASEFLOW / ORBIT / AIRY "A on 'Hide from library' showed no confirmation" at x1.3 and x1.0 - the screenshots
  and dumps show "Press A again to remove · move away to cancel" on screen; p2review's confirmtext only knew "again to confirm"
  (a QA false FAIL, not an app defect). (2) BUBBLES aborted: Bubble home's A unfolds the LiveArea inside the home (no game
  window), and the LiveArea's A is Start = Play - winguard stopped correctly, nothing more pressed.
- P2 branch 1dbd311 (fast-forward on the owner's 79d19e3): confirmtext also accepts "again to remove"; HOOK_OPEN="BUBBLES" opens
  that layout's game window with the QA hook voltcap_test_window (never starts a game) instead of an A on the home. Selftest
  279/279 (2 new; both fail on the old script). Review re-dispatched: run 45603942465 (P2:CASEFLOW ORBIT AIRY BUBBLES on 1dbd311).
- Dev line, rule 11 (matrix P13): SaveInspect.kt (controls hint, object name), SaveSyncUi.kt (sync badge label, status line),
  SteamImportDialog.kt (row name, detail / source line) and RommBrowser.kt (ROM tile name - keeps two lines of room - and the
  dialog title) go through FullText (wrap, readable size, then slide). 8 TRUNCATE places -> 0.
- Test: SaveImportFullText751aTest 2/2 PASS in the cloud (fails on 750a: 8 Ellipsis); ArrivalLaunchFullText750aTest 2/2 PASS.
  App + tests compiled. Static checks PASS, TRUNCATE 51 -> 43.
- Files to copy: app/src/main/java/com/shelf/launcher/ui/SaveInspect.kt, ui/SaveSyncUi.kt, ui/SteamImportDialog.kt,
  ui/RommBrowser.kt, app/src/test/java/com/shelf/launcher/ui/SaveImportFullText751aTest.kt.
- Not tested: hardware (HW-24 751a, KONKR x1.3 + x1.0, with 749a / 750a).

# Update 750a (25 Nov 2026, cloud run) - rule 11: new-arrival card, glance window, launch animation and companion screen show their text whole
- CI first: Verify 310 on e510120 (749a) GREEN. P2 emulator review 45584303713 (HERO_TILES MEMCARD BOX FILESELECT, x1.3 + x1.0,
  on a524ded) VERDICT PASS, 0 launches, every destructive row armed and cancelled, crash log empty - sheets in its artifact. Next
  set dispatched: run 45593862011 (P2:CASEFLOW ORBIT AIRY BUBBLES) on a524ded (read-only dispatch; the P2 branch is untouched).
- Dev line, rule 11 (matrix P13): NewArrival.kt (name while it types in - keeps two lines of room so the card never grows - and the
  console / "+N more" tail), GlanceWindow.kt (title, no reserve because its size places the art; facts line 1 / 4 lines),
  LaunchAnimation.kt (centred title + console line) and CompanionActivity.kt (game title + console, Performance page title) go
  through FullText (wrap, readable size, then slide). 9 TRUNCATE places -> 0.
- Test: ArrivalLaunchFullText750aTest 2/2 PASS in the cloud (fails on 749a: 9 Ellipsis); Batch523cTest, PlayWhileClosing412Test,
  PopupReview443Test, PopupShade461Test PASS (read these files). App + tests compiled. Static checks PASS, TRUNCATE 60 -> 51.
- Files to copy: app/src/main/java/com/shelf/launcher/ui/NewArrival.kt, ui/GlanceWindow.kt, ui/LaunchAnimation.kt,
  ui/CompanionActivity.kt, app/src/test/java/com/shelf/launcher/ui/ArrivalLaunchFullText750aTest.kt.
- Not tested: hardware (HW-24 750a, KONKR x1.3 + x1.0, with 749a).

# Update 749a (25 Nov 2026, cloud run) - rule 11: play stats, performance tiles and game popup status rows show their text whole
- CI first: Verify 310 on 435538a (748a) GREEN. P2 emulator review 45576064795 (CIRCUIT CHAN TIMELINE HERO, x1.3 + x1.0) VERDICT
  PASS, 0 launches, every destructive row armed and cancelled - sheets in its artifact. Next set dispatched: run 45584303713
  (P2:HERO_TILES MEMCARD BOX FILESELECT) on a524ded.
- The owner's live session is pushing to claude/v0674-native-pages (682av, 09:10-10:08 Berlin, KONKR hardware results) - this
  run left that branch alone apart from the read-only review dispatch.
- Dev line, rule 11 (matrix P13): GamePlayStats.kt (stat value + label), PerfPage.kt (tile value + sub line, the sub keeps its two
  lines so all six tiles stay one size) and GamePopupStatus.kt (status row label + value) go through FullText (wrap, readable size,
  then slide; reserve = false where the row height must stay). 6 TRUNCATE places -> 0.
- Test: StatsFullText749aTest 2/2 PASS in the cloud (fails on 748a: 6 Ellipsis); Batch482Test 7/7 + Tuner373Test 3/3 PASS (touch
  GamePopupStatus). App + tests compiled. Static checks PASS, TRUNCATE 66 -> 60.
- Files to copy: app/src/main/java/com/shelf/launcher/ui/GamePlayStats.kt, ui/PerfPage.kt, ui/GamePopupStatus.kt,
  app/src/test/java/com/shelf/launcher/ui/StatsFullText749aTest.kt.
- Hardware NOT TESTED: HW-24 749a (KONKR, with 713a).

# Update 748a (25 Nov 2026, cloud run) - P2 emulator review works again: the review walk reads the app's screen dumps without the device's grep (QA tooling, native-pages branch)
- CI first: Verify 310 on 4d6b6b9 (dev lock) GREEN; 9be5fab (P2) superseded by this push; ae4ad42 (P2) GREEN.
- Item (handoff NEXT): review 45566681915 on 9be5fab still INFRA at both scales, but 747c's kept files settled it: both kept
  dumps ended in '}' and held "popupGame" (null / the Okami path), yet the device's grep -o found no popupGame in the same file
  ("no popupGame read"). Both carried non-ASCII page text ('·' / '›') in the dump's one long line. The same review's
  "Pocket classic: A on 'Hide from library' showed no confirmation" was the same fault: the kept dump says "PRESS A AGAIN TO
  CONFIRM · MOVE AWAY TO CANCEL" and the device's grep -qiE missed it. GNU grep and Debian toybox 0.8.9 find both (the device
  uses Android's own regex), so the exact grep defect is not reproduced off-device; the reads no longer depend on it.
- 748a (P2 ae4ad42): winguard.sh wg_jval - the popupGame field read by the shell itself (no grep / regex / other program), used
  by wg_popup and by the abort line; p2review.sh confirmtext - the armed / disarmed confirmation read like onhide (short lines
  split on '{', fixed strings). Checked in dash, bash and mksh on the two real kept dumps.
- Tests: two new selftests with a grep stand-in that finds nothing in a >4 KB line with a non-ASCII byte (a model of the device,
  labelled as such): wg_popup / the abort line read popupGame, confirmtext sees "AGAIN TO CONFIRM" - both FAIL on 9be5fab's
  scripts, PASS now. Selftest 277/277. Static checks PASS (no app code changed).
- EMULATOR result: review 45570243363 on ae4ad42 (P2:POCKET_CLASSIC POCKET_RIBBON POCKET CUSTOM) VERDICT PASS - x1.3 and x1.0
  walks complete for Pocket classic, Pocket ribbon, Pocket Quest and Layout Studio; each destructive row armed (confirmation
  shown) and left, never confirmed; 0 launches; restore OK. First complete P2 emulator review since 18 Nov. The look stays
  the owner's review (sheets in the run's artifact). Emulator evidence only - not handheld.
- Dispatched: review 45576064795 (P2:CIRCUIT CHAN TIMELINE HERO).
- Copy: nothing for the owner's PC (QA tools on branch claude/v0674-native-pages: tools/qa/device/winguard.sh,
  tools/qa/device/p2review.sh, tools/qa/selftest/test_qa.py). Hardware: not needed for this change (HW-28 unchanged).

# Update 747a-c (25 Nov 2026, cloud run) - P2 emulator review restarted: the review walk's "no dump" stops diagnosed and narrowed (QA tooling, native-pages branch)
- CI first: Verify 310 on b44d63f (dev line) and 86344b2 (P2) GREEN.
- Item: the emulator p2review sheet of the newest native pages (handoff NEXT). Every P2 emulator review since 18 Nov stopped as
  INFRA "game window expected / home expected, QaClip: 'no dump'"; reproduced on 86344b2 (run 45560074265, Pocket classic /
  ribbon / Pocket Quest / Layout Studio): Pocket classic walked at x1.3 and x1.0 (sheet captured), then the guard stopped.
- 747a (P2 dc5f873): winguard's abort says why a dump did not come; waits up to 15 s more while a dump is being written.
- 747b (P2 e13bbd1): wg_popup reads the dump ONCE (the old code looked three times; a dump being replaced in between read as
  none). Selftest churn test fails on the old code (ok=7/8), passes on the new.
- Rerun 45563249574 on e13bbd1: still INFRA, but the new line shows the dump WAS written ("request taken, the dump is there")
  while the read gave up within ~1 s - so not a slow app. 747c (P2 9be5fab): such an abort keeps that dump beside the log
  (<tag>_wg_late.json) with its size / last byte / popupGame as the reader sees it. Review re-dispatched: run 45566681915.
- Also seen (rerun, x1.3): Pocket classic "A on 'Hide from library' showed no confirmation" - the first run armed it fine on
  the same code; likely the same dump-read problem (the check reads a dump), not counted as an app defect yet.
- Checked: tools/qa/selftest/test_qa.py 275/275 (P2 worktree). No app code changed. Hardware: NOT TESTED (QA tooling;
  emulator evidence only). Files (P2 branch): tools/qa/device/winguard.sh, tools/qa/selftest/test_qa.py.

# Update 746a (25 Nov 2026, cloud run) - P2 branch CI fix: the native-pages test follows the finished set
- CI first: Verify 310 on 2ed1c9f (dev line) GREEN; on df0f0c3 (P2, Pocket family) RED - 4963 tests, 1 failure:
  PopupNativeStorageTest.storageKeepsItsOwnPages asserted "some scenes still use the shared pages". 745a converted the last one,
  so every WinScene is now in NATIVE_PAGE_SCENES and the assumption no longer holds.
- Fix (test only, `claude/v0674-native-pages` 86344b2): the test now requires that no scene is left on the shared pages and that
  nativePages() is true for every scene - a scene falling back to the shared pages fails. The per-scene check stays.
- Checked: PopupNativeStorageTest 9/9 (cloud, P2 worktree); static checks PASS. No app code changed; nothing to copy for the dev
  line. Hardware: NOT TESTED (not needed - test change only). Files: app/src/test/java/com/shelf/launcher/ui/PopupNativeStorageTest.kt (P2 branch).

# Update 745a (25 Nov 2026, cloud run) - P2 class C: the Pocket family gets its own pages, all three faces (native-pages branch)
- CI first: Verify 310 on 0337ee9 (dev line) and 89ab1d6 (P2, Layout Studio) GREEN. Dev line merged into the P2 branch first
  (docs only; the shallow clone needed --deepen to find the merge base).
- Task 5 on `claude/v0674-native-pages` (worktree), 89ab1d6 -> df0f0c3: the last class C scene. POCKET_LCD is one scene with three
  faces; each now draws its own pages (WinPagesPocket.kt): Pocket classic's handheld menu (title bar names the page "‹ DATA ›"
  "2/4"; DATA / MEDALS / MENU in pixel type, ten pixel blocks, a drawn ▶ cursor, the LCD stepping the menu in four hard steps),
  Pocket ribbon's white sheet (pixel ribbon of page names underlined in the accent; Details table on grey rules, Records with a
  hairline bar, Settings rows with ›; slides in from its side), Pocket Quest's cut-corner panel (chips Overview / Status / Quest
  log / Commands; stat lines, a ten-cell gauge, deep wells, cut-corner buttons; a scan line runs once). Own designs 52 / 53.
- Play stays put: the window grows upward only on its pages and, beside the tall art (ribbon, Quest), keeps the room the first
  page left under its keys (PocketPlace.keyGap). Without it the ribbon's Play sat 80 px (RP Classic 161 px) lower on its pages.
  PlayPlace (+ Rpc) + a temporary first-page probe: Play's bottom edge equal on all pages in every face, x1.0 / x1.3.
- Found by the tests on the way: ribbon x1.3 first record cut (bar now carries the name on a short window); classic cursor's blank
  glyph cut at x1.3 (drawn now); PlayPlace never found Pocket Quest's Play ("Play game") and ShelfText not its Details ("Status") -
  both taught.
- Files (P2 branch only): ui/WinPagesPocket.kt (new), ui/WinScenesStyled.kt, ui/WinPageFlow.kt; tests NativePagesPocket745aTest
  (new), AchFirstRowRoboTest, ShelfTextRoboTest, PlayPlace682abRoboTest; docs/claude/NATIVE_PAGES_P2.md. Dev line: docs only.
- Checked (cloud): compile; 3,050 targeted tests (PlayPlace*, AchFirstRow*, NoStrayPlay682j, WinFocus, ShelfText, WindowWordsSurvey,
  NativeConfirm681, NativePages*, UniformKeys673, Pocket source tests) - 3 ribbon x1.3 failures fixed, touched suites rerun green;
  static PASS. NOT TESTED: hardware (HW-28 extended), emulator review; not owner-approved.

# Update 744a (25 Nov 2026, cloud run) - P2 class C: Layout Studio gets its own pages (native-pages branch)
- CI first: Verify 310 on 5fb6ff6 (dev line) and 63f0b52 (P2, Live tiles) GREEN (50e4a3f was cancelled by the next push).
- Task 5 on `claude/v0674-native-pages` (worktree), 63f0b52 -> 89ab1d6: the nineteenth class C scene (batch 8). Layout Studio's
  panel (layout grid, framed art well with crop marks, the layout's colours) keeps its first page (Canvas); its pages use the
  editor's language under a rounded tab strip (Canvas / Properties / Milestones / Actions, the shown one filled in the accent):
  Properties (inspector rows - spaced-capital labels left, values right, dashed guide lines; the description in a text frame),
  Milestones ("N / M", per cent, a ruler with a tick every tenth - one earned always shows, full only when all are earned - the
  order chip; layer rows with the badge in a framed well, "Earned <date>" / "Not yet", "+N" chip; A opens text + rarity),
  Actions (rounded buttons like the keys; removing ones red-outlined, second A). Pages snap in from one grid step below; none
  with Motion Off. Own designs 49 / 53.
- Play stays put: key row keeps the first page's slots, the panel grows upward only on its pages (livePageHeight, as Live 743a).
  PlayPlace CUSTOM (+ Rpc) x1.0 / x1.3 + a temporary first-page probe: Play's top-left within 1 px on all four pages.
- Found on the way: AchFirstRow CUSTOM x1.3 showed the first milestone half (37 of 75 px) - on a short window the pages now drop
  the console line (it is in Properties), the name goes to 16 sp / two lines and the milestone header steps down; PASS after.
- Checked (cloud): NativePagesStudio744aTest 4/4 (new; fails on the old code: no studioPageName, STUDIO_PANEL not native); CUSTOM
  added to AchFirstRow (+ Rpc, PlayPlace via NATIVE) + ShelfText; with PlayPlace*, NoStrayPlay682j, WindowWordsSurvey682,
  Popup421, Batch506, Layouts330, R1Compat326, WinFocus*, UniformKeys673, NativeConfirm681, NativePages* - 2,903 tests,
  0 failures. Static PASS. (Maven Central answered 429; the run used the settings' VOLTCAP_MAVEN_MIRROR switch with Google's
  Maven Central mirror.)
- Not tested: hardware (HW-28 extended), emulator review; not owner-approved.
- Files (P2 branch): ui/WinPagesStudio.kt (new), ui/WinSceneStudio.kt (StudioPanel, StudioTitle), ui/WinPageFlow.kt; tests
  NativePagesStudio744aTest (new), AchFirstRowRoboTest, ShelfTextRoboTest; docs NATIVE_PAGES_P2.md. Dev line: hand-off docs only.

# Update 743a (25 Nov 2026, cloud run) - P2 class C: the Live tiles panel gets its own pages (native-pages branch)
- CI first: Verify 310 on 73c1053 (dev line) and 7477b77 (P2, Cross menu) GREEN.
- Task 5 on `claude/v0674-native-pages` (worktree), 7477b77 -> 63f0b52: the eighteenth class C scene (batch 7). The Live tiles panel
  (flat, dark, turning in like a turnstile) keeps its flat tile language on its pages under a pivot heading - the page names in
  large thin lower case, the shown one white and first, the others after it in turn, dim: about (lower-case accent labels over thin
  white values, the description on a flat tile), achievements (big thin per cent, "N of M", a flat accent bar - one earned always
  shows, full only when all are earned - the order as a flat tile; each achievement a flat row with its badge on a square tile,
  the row you are on filled in the accent; A shows the whole text and rarity), options (flat tiles; removing ones red, second A).
  The next page turns in like the panel itself (turnstile from the left edge, text drawn flat first - D285); none with Motion Off.
  Own designs 48 / 53.
- Found and fixed on the way (a PlayPlace probe of the first page, not only the pages): grown to the window's height, the centred
  panel put Play 8 dp lower on its pages (RP Classic: 60 dp). On its pages the panel now grows upward only (livePageHeight: half
  the free room, measured with More closed), so Play's top-left is identical on the first page and all three pages (x1.0 / x1.3,
  both screen sizes). Play is wider on the pages, as on every layout whose first-page row scrolls.
- Checked (cloud): NativePagesLive743aTest 5/5 (new; fails on the old code: no livePageName, LIVE_PANEL not native); LIVE added to
  AchFirstRow (+ Rpc, PlayPlace via NATIVE) + ShelfText; with PlayPlace*, NoStrayPlay682j, InputContract462, WindowWordsSurvey682,
  Visual426, Popup421, WinFocus*, Layouts344, Readability476, Batch505, PopupMore451, WinPresentation*, NativeConfirm681,
  NativePages*, UniformKeys673, PopupNativeStorage* - 2,909 tests, 0 failures. Static PASS.
- Not tested: hardware (HW-28 extended), emulator review; not owner-approved.
- Files (P2 branch): ui/WinPagesLive.kt (new), ui/WinScenesHomes2.kt (LiveTilesPanel, livePageHeight), ui/WinPageFlow.kt; tests
  NativePagesLive743aTest (new), AchFirstRowRoboTest, ShelfTextRoboTest; docs NATIVE_PAGES_P2.md. Dev line: hand-off docs only.

# Update 742a (25 Nov 2026, cloud run) - P2 class C: the Cross menu reads out its own pages (native-pages branch)
- CI first: Verify 310 on 993d193 (dev line) and 9831e03 (P2, Art library) GREEN.
- Task 5 on `claude/v0674-native-pages` (worktree), 9831e03 -> 7477b77: the seventeenth class C scene (batch 6; the last class C
  scene that still showed the shared bodies through sharedPageFor's own Cross exception). The Cross menu reads its pages out on the
  left in its thin white type under a "cross bar" (one small icon per page on a fine line, the shown one bright and named):
  Information (system-information list - grey labels right-aligned against a fine divider, white values beside them, stacked on a
  narrow column; the description as one row of the menu), Trophies (cup, "N / M", per cent + a fine progress line - one earned
  always shows, full only when all are earned - the order as a band key; each trophy a row of the menu, its icon drawn larger on
  the row you are on; A shows the whole text and how rare it is), Options (rows of the menu; removing ones in warning orange,
  second A). The next page slides in along the bar from the side you moved to; none with Motion Off. Own designs 47 / 53.
- Found and fixed on the way (PlayPlace CROSS, newly reachable): the centred options column put Play 26 dp (x1.0) / 54 dp (x1.3)
  lower on the pages than on the first page. The first page stays centred as before; the pages' keys keep the page slots and start
  where the first page's keys did; the column's scroll starts at its top on every page. PlayPlace had skipped Cross (its Play is
  "Start game", its Details "Information") - both words taught to PlayPlace / ShelfText.
- Checked (cloud): NativePagesCross742aTest 4/4 (new; fails on the old code: no crossPageName, CROSS_OPTIONS not native); CROSS added
  to AchFirstRow (+ Rpc, PlayPlace via NATIVE) + ShelfText; PlayPlace / PlayPlaceRpc CROSS x1.0 / x1.3 Play at the same place on the
  first page and all three pages; with NoStrayPlay682j, InputContract462, WindowWordsSurvey682, Popup421, WinFocus*, Layouts344,
  Readability476, Batch505, Visual426, PopupMore451, WinPresentation, NativeConfirm681, NativePages*, UniformKeys673,
  PopupNativeStorage - 2,499 tests, 0 failures. Static PASS.
- Not tested: hardware (HW-28 extended), emulator review; not owner-approved.
- Files (P2 branch): ui/WinPagesCross.kt (new), ui/WinScenesHomes2.kt (CrossOptions, KeyColumn slots / scrollKey), ui/WinPageFlow.kt;
  tests NativePagesCross742aTest (new), AchFirstRowRoboTest, ShelfTextRoboTest, PlayPlace682abRoboTest, UniformKeys673Test; docs
  NATIVE_PAGES_P2.md. Dev line: hand-off docs only.

# Update 741a (24 Nov 2026, cloud run) - P2 class C: the Art library's gallery wall gets its own pages (native-pages branch)
- CI first: Verify 310 on c466cf8 (dev line) and 945bb17 (P2, Green core) GREEN.
- Task 5 on `claude/v0674-native-pages` (worktree), 945bb17 -> 9831e03: the sixteenth class C scene (batch 6). The Art library's
  gallery wall hangs its own pages on the pale mat where the work hangs, in the placard's serif and ink, lit in the layout's
  colour: Catalogue (each fact a catalogue line - label in small grey capitals over the value, hairlines between - then the
  story on a white card headed "Notes"), Collection (cup, "N of M acquired", a hang of 10 small frames - one acquired always fills
  one, all only when every one is acquired - the order as a slim key; each achievement a framed badge beside its placard: title,
  "Acquired <date>" / "Not yet acquired", "+N"; A shows the whole text and how rare it is), Curator (each option a small placard;
  removing ones in deep red, second A). Heading = the page's name in serif italic + "ROOM N OF M", after the old page has gone;
  the next page comes up like the gallery lights as you step up to the wall (fade + slight scale); none with Motion Off. On its
  pages the wall keeps its size and its keys keep the page slots (Play first), so Play stays put; on a short wall's pages the
  placard keeps the name alone. Was the shared page bodies on the mat. Own designs 46 / 53.
- Checked (cloud): NativePagesGallery741aTest 4/4 (new; fails on the old code: no galleryPageName, GALLERY_PLACARD not native);
  ARTLIB added to AchFirstRow (+ Rpc, PlayPlace via NATIVE) + ShelfText; PlayPlace / PlayPlaceRpc ARTLIB x1.0 / x1.3 found Play at
  the same place on all three pages; with NoStrayPlay682j, InputContract462, WindowWordsSurvey682, Popup421, WinFocus*,
  Layouts344 / 374, Batch505, NativeConfirm681, NativePages*, UniformKeys673, PopupNativeStorage - 2,803 tests, 0 failures.
  Static PASS.
- Not tested: hardware (HW-28 extended), emulator review; not owner-approved.
- Files (P2 branch): ui/WinPagesGallery.kt (new), ui/WinScenesMore.kt (GalleryPlacard), ui/WinPageFlow.kt; tests
  NativePagesGallery741aTest (new), AchFirstRowRoboTest, ShelfTextRoboTest; docs NATIVE_PAGES_P2.md. Dev line: hand-off docs only.

# Update 740a (24 Nov 2026, cloud run) - P2 class C: Green core's console gets its own pages (native-pages branch)
- CI first: Verify 310 on aa0e172 (dev line) and 982dfb1 (P2, Rental shop) GREEN.
- Task 5 on `claude/v0674-native-pages` (worktree), 982dfb1 -> 945bb17: the fifteenth class C scene (batch 6; Arctic already had
  its own pages since 682y). Green core's glowing system console reads out its own pages under its glow rule, in its monospace
  capitals and the picked glow colour: SPEC (each fact a readout line - label in the dim glow over the value, a glow tick down its
  left - then the story in a cut-corner well headed "// DESCRIPTION"), UNLOCKS (cup, "N / M", a 12-cell core charge bar - one
  earned always charges one, all only when every one is earned - the order as a cut-corner key; each achievement a cut-corner
  line: badge, title, "UNLOCKED <date>" / "LOCKED", "+N"; A shows the whole text and how rare it is), CONTROL ("> " command lines;
  removing ones in alarm red, second A). Heading = "> " + the page's name + a cut-corner cell per page, after the old page has gone;
  the next page opens from one glowing line across its middle like the console's boot; none with Motion Off. On its pages the
  console keeps one height and its keys keep the page slots (Play first), so Play stays put. Was the cover with the shared page
  bodies in the facts' place. Own designs 45 / 53.
- Checked (cloud): NativePagesCore740aTest 4/4 (new; fails on the old code: no corePageName, CORE_PANEL not native); GREENCORE
  added to AchFirstRow (+ Rpc, PlayPlace via NATIVE) + ShelfText; PlayPlace / PlayPlaceRpc GREENCORE x1.0 / x1.3 found Play at the
  same place on all three pages; with NoStrayPlay682j, InputContract462, WindowWordsSurvey682, Popup421, WinFocus*, Layouts344,
  NativeConfirm681, NativePages*, UniformKeys673, PopupNativeStorage - 2,570 + 88 tests, 0 failures. Static PASS.
- Not tested: hardware (HW-28 extended), emulator review; not owner-approved.
- Files (P2 branch): ui/WinPagesCore.kt (new), ui/WinScenesMore.kt (CorePanel), ui/WinPageFlow.kt; tests NativePagesCore740aTest
  (new), AchFirstRowRoboTest, ShelfTextRoboTest; docs NATIVE_PAGES_P2.md. Dev line: hand-off docs only.

# Update 739a (24 Nov 2026, cloud run) - P2 class C: Rental shop's tag gets its own pages (native-pages branch)
- CI first: Verify 310 on 5d6a422 (dev line) and fb72ff4 (P2, Panel dashboard) GREEN.
- Task 5 on `claude/v0674-native-pages` (worktree), fb72ff4 -> 982dfb1: the fourteenth class C scene (first of batch 6). Rental
  shop's yellow rental tag prints its own pages under the title in its black capitals, lit in the shop's neon: CATALOGUE CARD
  (opened by the tag's own CATALOGUE CARD key: facts label over value between dashed rules, the story under a black bar headed
  THE STORY), REWARDS CARD (a member punch card: cup, "N / M", ten punch holes - one earned always punches one, all only when every
  one is earned - the order as a black stamp; each achievement a line with its square badge, title, "EARNED <date>" / "NOT YET",
  "+N PTS"; A shows the whole text and how rare it is), COUNTER (black punched rows; removing ones orange, second A). Heading = the
  page's name (wraps, never cut) + a punch hole per page, after the old page has gone; the next page drops in from above like the
  next card on the tag's string; none with Motion Off. On its pages the tag keeps one size and one row of punched keys (two page
  slots, RENT & PLAY first), so Play stays put. No made-up rental period / due date / price. Was the shared pages in a black frame
  with "▸" words under it. Own designs 44 / 53.
- Found on the way (tests): PlayPlace never checked the Rental shop - it knew neither RENT & PLAY nor CATALOGUE CARD, so it skipped
  the layout; ShelfText could not open its Details. Both now know the shop's words (stronger, not weaker), and PlayPlace SHOP finds
  Play at the same place on all three pages at x1.0 / x1.3.
- Checked (cloud): NativePagesShop739aTest 4/4 (new; fails on the old code: no shopPageName, SHOP_TAG not native); SHOP added to
  AchFirstRow (+ Rpc, PlayPlace via NATIVE) + ShelfText; with PlayPlace*, NoStrayPlay682j, Layouts344, WindowWordsSurvey682,
  Batch505, PopupShade461, Pickups672, ShopTag673, NativeConfirm, PopupNativeStorage, WinFocus, UniformKeys673, NativePages* -
  2,639 tests, 0 failures. Static PASS.
- Not tested: hardware (HW-28 extended), emulator review; not owner-approved.
- Files (P2 branch): ui/WinPagesShop.kt (new), ui/WinScenesConsole.kt (RentalTagCard), ui/WinPageFlow.kt; tests
  NativePagesShop739aTest (new), AchFirstRowRoboTest, PlayPlace682abRoboTest, ShelfTextRoboTest; docs NATIVE_PAGES_P2.md. Dev line:
  hand-off docs only.

# Update 738a (24 Nov 2026, cloud run) - P2 class C: Panel dashboard gets the panel's own pages (native-pages branch)
- CI first: Verify 310 on ca697b9 (dev line) and 5e1b096 (P2, Pocket camera) GREEN.
- Task 5 on `claude/v0674-native-pages` (worktree), 5e1b096 -> fb72ff4: the thirteenth class C scene. Panel dashboard's swung-round
  panel shows its own pages on its right side, flat like the dashboard, in the picked palette: OVERVIEW (each fact a flat tile with
  a colour bar down its left, label over value; the story in a flat panel headed ABOUT under a colour bar), ACHIEVEMENTS (cup,
  "N / M", a 10-segment flat meter - one earned always lights one, all only when every one is earned - the order as a flat tile;
  each achievement a flat row with its square badge, title, "Unlocked <date>" / "Locked" and points, lit like the commands; A shows
  the whole text and how rare it is), MANAGE (flat rows like the command column; removing ones in the warning orange, second A).
  Heading = the page's name + a flat segment per page, after the old page has gone; the next page slides in from its side like the
  next panel coming round; none with Motion Off. The art strip now names the console alone (the page name was said twice). Was
  the shared pages in a thin frame. Own designs 43 / 53.
- Checked (cloud): NativePagesPanels738aTest 4/4 (new; fails on the old code: no panelPageName, PANEL_DETAIL not native); PANELS
  added to AchFirstRow (+ PlayPlace / PlayPlaceRpc via NATIVE) + ShelfText; AchFirstRow*, PlayPlace*, NoStrayPlay682j, ShelfText,
  WinFocus, Layouts344, WindowWordsSurvey682, NativeConfirm, PopupNativeStorage, UniformKeys673, NativePages* - 2,575 tests, 0
  failures. Static PASS.
- Not tested: hardware (HW-28 extended), emulator review; not owner-approved.
- Files (P2 branch): ui/WinPagesPanels.kt (new), ui/WinScenesConsole.kt (PanelDetail), ui/WinPageFlow.kt; tests
  NativePagesPanels738aTest (new), AchFirstRowRoboTest, ShelfTextRoboTest; docs NATIVE_PAGES_P2.md. Dev line: hand-off docs only.

# Update 737a (24 Nov 2026, cloud run) - P2 class C: Pocket camera gets the cartridge menu's own pages (native-pages branch)
- CI first: Verify 310 on 32e3610 (dev line) and 445fd1f (P2, Bubble home) GREEN.
- Task 5 on `claude/v0674-native-pages` (worktree), 445fd1f -> f2e65f0, merged with the owner session's 682au (Arcade FitText,
  p2review safety mode) -> 5e1b096: the twelfth class C scene. Pocket camera's cartridge menu shows its own pages under the name
  on its light screen, in the pixel font and the palette's four shades: DATA (LABEL / VALUE lines with dotted pixel rules + the
  story in a one-pixel frame headed ABOUT), ALBUM (pixel cup, "03/12", a meter of pixel cells - one earned always shows a cell,
  full only when all are earned - and the order as a menu row; each award a numbered album entry "NO.01" with its badge in a
  square pixel frame, the title, "GOT <date>" / "NOT YET" and the points; the row you are on dark, as on the menu; A shows the
  whole text and how rare it is), SETUP (menu rows with the arrow cursor; removing ones marked "!" and ask for a second A).
  Heading = the page's name + a pixel square per page, after the old page has gone; pages draw down the little screen in the
  window's own switch-on steps with a scan line; none with Motion Off. Was the shared pages in a dark box. Own designs 42 / 53.
- Found by tests and fixed before the push: Play sat a key lower on Options (Play alone there) - the camera's key column keeps
  two keys' room on every page (UniformKeyColumn slots, default unchanged for the other callers); at x1.3 on the RP Classic
  size the first award stood 346 dp tall (number + badge + points beside the title left it a letter wide) - the number and
  points now share the entry's first line.
- Checked (cloud): NativePagesCamera737aTest 4/4 (new; fails on the old code); AchFirstRow + Rpc, ShelfText, WinFocus,
  NoStrayPlay682j CAMERA; PlayPlace + PlayPlaceRpc all layouts; UniformKeys673, WindowWordsSurvey682, Layouts344,
  PopupNativeStorage, NativeConfirm, NativePages* - 848 tests, 0 failures. Post-merge (5e1b096): NativePagesCamera737aTest, AchFirstRowRpc + PlayPlaceRpc CAMERA, FitSearch682au 8/8. Static PASS.
- Not tested: hardware (HW-28 extended), emulator review; not owner-approved.
- Files (P2 branch): ui/WinPagesCamera.kt (new), ui/WinScenesConsole.kt (CameraMenu), ui/WinPageFlow.kt, ui/WinScenesMore.kt
  (UniformKeyColumn slots), ui/StylePocketCamera.kt (PX_TROPHY internal); tests NativePagesCamera737aTest (new),
  AchFirstRowRoboTest, ShelfTextRoboTest; docs NATIVE_PAGES_P2.md. Dev line: hand-off docs only.

# Update 736a (24 Nov 2026, cloud run) - P2 class C: Bubble home gets the card's own pages (native-pages branch)
- CI first: Verify 310 on 18d8051 (dev line) and 2e18249 (P2, Airy home + owner's 682at) GREEN.
- Task 5 on `claude/v0674-native-pages` (worktree), 2e18249 -> 445fd1f: the eleventh class C scene. Bubble home's LiveArea card
  shows its own pages on its white info panel, in the card's language: INFO (label / value lines with hairlines + the
  description in a pale box), AWARDS (one line: gold cup, "N / M", a slim round-ended meter in the card's blue, the order as a
  round-ended white key; each award a round-ended white key with a round badge, "Earned <date>" / "Not yet" and the points in a
  gold ring; A rounds it into a card with the whole text and how rare it is), SETTINGS (round-ended white keys with the card's
  white-to-grey face and blue ring; removing ones orange, second A). Heading = the page's name + a bubble per page, after the
  old page has gone; pages swell in like a bubble; none with Motion Off. The page panel was a dark see-through box around the
  shared pages - now white like the first page. Own designs 41 / 53 (class C 11 left).
- Play (the Start plate) stays the first key down the left on every page - PlayPlace BUBBLES x1.0 / x1.3 PASS.
- Checked (cloud): NativePagesBubble736aTest 4/4 (new; fails on the old code); AchFirstRow + Rpc, PlayPlace + Rpc, NoStrayPlay682j,
  ShelfText BUBBLES; WindowWordsSurvey682, PopupMore451, Layouts344, Batch505, Batch489, Visual426, PopupNativeStorage,
  NativeConfirm, UniformKeys, NativePages* - 694 tests, 0 failures. Static PASS. Full suite: CI.
- Not tested: hardware (HW-28 extended), emulator review; not owner-approved.
- Files (P2 branch): ui/WinPagesBubble.kt (new), ui/WinScenesConsole.kt (BubbleCard), ui/WinPageFlow.kt; tests
  NativePagesBubble736aTest (new), AchFirstRowRoboTest, ShelfTextRoboTest; docs NATIVE_PAGES_P2.md. Dev line: hand-off docs only.

# Update 735a (24 Nov 2026, cloud run) - P2 class C: Airy home gets the panel's own pages (native-pages branch)
- CI first: Verify 310 on 1eb9328 (dev line) and 6e8c897 (P2, Orbit deck) GREEN.
- Task 5 on `claude/v0674-native-pages` (worktree), 6e8c897 -> da3e595, merged with the owner session's 682at (charging light)
  -> 2e18249: the tenth class C scene. Airy home's floating white panel shows its own pages in its right column, in the same soft
  1999 language: ABOUT (each fact a white round-ended bubble + the story in a speech bubble), STARS (one line: "★ N of M", a
  bubble meter filled in the accent, the order as a round pill; each achievement a white rounded card with a round badge,
  "Got it <date>" / "Still to get" and a round points bubble), SETTINGS (round-ended pills with a dot; removing ones soft red,
  second A). Heading = the page's name in the light face + page dots, after the old page has gone; pages float up like a bubble;
  none with Motion Off. Own designs 40 / 53 (class C 12 left).
- Found by tests and fixed before the push: at x1.3 on the 640 x 427 window and the RP Classic the first star showed half a card
  (16 px, then 46 px of 73) - the stars header is one line now (the order pill's words slide, rule 11) and a tight panel's pages
  take the small round keys, a smaller heading and closer edges. Play keeps two page slots - same place on all three pages.
- Checked (cloud): NativePagesAiry735aTest 4/4 (new); AchFirstRow + AchFirstRowRpc + ShelfText + PlayPlace + PlayPlaceRpc AIRY,
  Airy477, WinFocus AIRY, NoStrayPlay682j all layouts, WindowWordsSurvey682, NativeConfirm, PopupNativeStorage, Layouts330,
  UniformKeys, NativePages* - 0 failures; after the merge NativePagesAiry + ChargeLight682at green. Static PASS. Full suite: CI.
- Not tested: hardware (HW-28 extended), emulator review; not owner-approved.
- Files (P2 branch): ui/WinPagesAiry.kt (new), ui/WinScenes.kt (AiryPanel), ui/WinPageFlow.kt; tests NativePagesAiry735aTest
  (new), AchFirstRowRoboTest, ShelfTextRoboTest; docs NATIVE_PAGES_P2.md. Dev line: hand-off docs only.

# Update 734b (24 Nov 2026, cloud run) - P2 class C: Orbit deck gets the console's own screens (native-pages branch)
- CI first: Verify 310 on 00d7e9b (dev line) GREEN; P2 branch: ec5addf (owner session, Arcade) RED, fixed by the owner session's
  6a9e2d4 (GREEN, includes 734a's test fix) - nothing left for this run.
- Task 5 on `claude/v0674-native-pages` (worktree), 6a9e2d4 -> 6e8c897: the ninth class C scene. Orbit deck's console (chrome
  edge, scan lines, glow caps, mono readouts) shows its own screens: DATA (fact readouts + the story as a LOG panel), TRACKS (the
  count + lit-cell strip, each achievement a numbered track cell + badge + mono UNLOCKED / LOCKED line + "+N PTS"), COMMANDS
  ("▸ LABEL" command lines; removing ones amber-red, second A). Heading = page code + page cells + READY lamp, after the old page
  has gone; pages roll up like a terminal; none with Motion Off. Own designs 39 / 53 (class C 13 left).
- Found by tests and fixed before the push: Play moved on Options (the arc fan had one key fewer; it now keeps PAGE_SLOTS places
  on pages, the narrow row two slots); an existing defect - at x1.3 a long console name ran under READY (own line now).
  Test fixes: PlayPlace682ab's Play matcher missed Orbit's labelled + described narrow key ("play play"); NoStrayPlay682j named
  the focus by its text only, so on the icon-only arc it "reached Play" anywhere - a key with no text is named by its description.
- Checked (cloud): NativePagesOrbit734bTest 4/4 (new); PlayPlace + PlayPlaceRpc all native layouts; NoStrayPlay682j 80/80; every
  ORBIT / Orbit case (AchFirstRow, ShelfText 24, WindowWordsSurvey, WinFocus, LazyRows, OrbitSpin, OrbitKeyLabel); PopupNativeStorage;
  Layouts330 - 0 failures. Static PASS. Full suite: GitHub CI on the push.
- Not tested: hardware (HW-28 extended), emulator review; not owner-approved.
- Files (P2 branch): ui/WinPagesOrbit.kt (new), ui/WinScenes.kt, ui/WinPageFlow.kt; tests NativePagesOrbit734bTest (new),
  AchFirstRowRoboTest, ShelfTextRoboTest, PlayPlace682abRoboTest, NoStrayPlay682jTest; docs NATIVE_PAGES_P2.md, NATIVE_PAGES_AUDIT.md.
  Dev line: hand-off docs only.

# Update 734a (24 Nov 2026, cloud run) - CI fix on the native-pages branch (Case flow test expectation)
- CI first: Verify 310 on 194557a (dev line) GREEN; on d3907c7 (733a, P2 branch) RED - both runners: 4286 tests, 1 failed,
  PopupNativeStorageTest.storageKeepsItsOwnPages. The test named Case flow (CASE_MEDIA) as an example of a scene still on the
  shared pages; 733a gave Case flow its own pages, so the app was right and the test's example was out of date.
- Fix on `claude/v0674-native-pages` (worktree), d3907c7 -> 50cfb3f, test only: the check now walks every scene not in
  NATIVE_PAGE_SCENES (each must answer false, and at least one must remain), and Case flow is asserted native. No assertion
  weakened: the old single-scene check is a subset of the new one; the next P2 conversion can no longer turn it red.
- Checked (cloud): PopupNativeStorageTest 9/9 (failed on d3907c7 in CI). Static PASS. Full suite: GitHub CI on this push.
- Not tested: nothing new for hardware (test-only change; HW-28 unchanged).
- Files (P2 branch): app/src/test/java/com/shelf/launcher/ui/PopupNativeStorageTest.kt. Dev line: hand-off docs only.

# Update 733a (24 Nov 2026, cloud run) - P2 class C: Case flow gets the case's own pages (native-pages branch)
- CI first: Verify 310 on 3f6f2f6 (dev line) GREEN; on 91247df (732a, P2 branch) GREEN.
- Task 5 on `claude/v0674-native-pages` (worktree), 91247df -> d3907c7: the eighth class C scene. The case opened into its media
  keeps its dark glass stage, black glass well with a shine, accent-edged name band and glossy pills reflected on the floor; its
  pages take the picture's place in the glass well, like the menu of the disc inside: DETAILS (facts as glass strips with the
  name band's accent edge, the story in its own glass panel), EXTRAS ("N of M unlocked" over a glossy bar with its reflection;
  each achievement a glass slide - badge with reflection, title, "Unlocked <date>" / "Locked", points pill; A opens the whole
  text + rarity; lazy rows), SETUP (glossy option pills; removing ones red, second A). Heading: accent edge + page name + a pill
  per page, after the old page has gone; pages flow in like the next case; none with Motion Off. Before: the shared page body
  squeezed into the 110-300 dp picture band.
- Found by the tests: Play moved between pages (window sized by its content) -> on its pages the case keeps one size (the
  stage), pills two slots, Play the first. PlayPlace682ab's More matcher missed a labelled + described More key ("more more"), so
  for Case flow it compared Info with Details and never reached the other pages - it now reads More as AchFirstRow does (not a
  weaker check: it reaches more pages; all layouts still pass).
- Checked (cloud): compile OK; NativePagesCase733aTest 4/4 (new; fails on the old code: no casePageName, CASE_MEDIA not native);
  CASEFLOW added to AchFirstRow (+ PlayPlace / PlayPlaceRpc) + ShelfText; AchFirstRow / ShelfText / PlayPlace* / WinFocus /
  NoStrayPlay / NativeConfirm / PageHandoff / ListHeadTurn / UniformKeys / PopupMore451 / WindowWordsSurvey / Layouts330 /
  Pickups672 / Batch506 / NativePages*: 2286 tests 0 failures. Static PASS. Gradle with VOLTCAP_MAVEN_MIRROR.
- Not tested: hardware (HW-28 extended), emulator review; not visually approved. Native pages: 38 / 53 own designs on the P2 branch.
- Files (P2 branch): ui/WinPagesCase.kt (new), ui/WinScenes.kt (CaseFlowMedia), ui/WinPageFlow.kt, test NativePagesCase733aTest.kt
  (new), AchFirstRowRoboTest.kt, ShelfTextRoboTest.kt, PlayPlace682abRoboTest.kt, docs/claude/NATIVE_PAGES_P2.md + _AUDIT.md.
  Dev line: hand-off docs only.

# Update 732a (24 Nov 2026, cloud run) - P2 class C: File select gets the file's own screens (native-pages branch)
- CI first: Verify 310 on c157066 (dev line) GREEN; on ee74963 (731a, P2 branch) GREEN (run 45402171675).
- Task 5 on `claude/v0674-native-pages` (worktree), ee74963 -> 91247df: the seventh class C scene. The turned-over save file keeps
  its chunky bevelled block, outlined capitals, yellow lit state and dark inset; its pages are the file's own screens on that
  inset: DETAILS (each fact a fat rounded slot - yellow label, white value - and the story in its own slot), STARS (big star +
  "N / M STARS", a row of little stars, yellow for yours, outlined still to get, one standing for several past 24; each
  achievement a rounded row - star, round badge, outlined title, "GOT IT <date>" / "NOT YET", "+N"; A opens the whole text +
  rarity; lazy rows), OPTIONS (fat round slots like the file's buttons; removing ones red with "ERASE? PRESS A AGAIN"). The top row
  names the page with a round pip per page after the old page has gone (it showed the shared page name at once); the next screen
  pops in from 94 %; none with Motion Off.
- Found by the tests: Play moved between pages (PlayPlace682ab FILESELECT x1.0 + x1.3) -> two page slots, Play the first (as
  Box / Memory card); x1.3 tight file left the first achievement 62 of 73 px (AchFirstRow) -> the star row gives way there, the
  count line stays; no double padding in the inset.
- Checked (cloud): compile OK; NativePagesFile732aTest 5/5 (new; fails on the old code: no filePageName, FILE_CARD not native);
  FILESELECT added to AchFirstRow + ShelfText; FILESELECT cases of 8 Robolectric suites + NativeConfirm / PageHandoff / ListHeadTurn
  / UniformKeys / PopupMore451 / Layouts330 / WindowWordsSurvey / CubeFacet: 628 tests 0 failures; NativePages* + PopupNativeStorage
  + DiscTracks green. Static PASS. Gradle needed VOLTCAP_MAVEN_MIRROR (Maven Central 429).
- Not tested: hardware (HW-28 extended), emulator review; not visually approved. Native pages: 37 / 53 own designs on the P2 branch.
- Files (P2 branch): ui/WinPagesFile.kt (new), ui/WinScenes.kt (FileCardBack), ui/WinPageFlow.kt, test NativePagesFile732aTest.kt
  (new), AchFirstRowRoboTest.kt, ShelfTextRoboTest.kt, docs/claude/NATIVE_PAGES_P2.md + _AUDIT.md. Dev line: hand-off docs only.

# Update 731a (24 Nov 2026, cloud run) - P2 class C: Cardboard box gets the insert's own sheets + P2 branch CI fix (native-pages branch)
- CI first: Verify 310 on b2c90cc (dev line) GREEN. P2 branch RED on 911681e (run 45393737656): PlayPlace682ab / PlayPlaceRpc682ab
  MEMCARD x1.0 + x1.3 (4 of 4,139) - 730a's Memory card pages put Play alone in the middle on COMMANDS. Fixed here (two page slots,
  Play the first); the owner's live session pushed the same fix meanwhile (682as) - theirs kept in the merge (ee74963).
- Task 5 on `claude/v0674-native-pages` (worktree), 31f0a09 -> ee74963: the sixth class C scene. The Cardboard box's insert sheet
  keeps its cream paper, tape, typewriter face and rubber stamps; its pages are the slip's other sheets on the same paper:
  CONTENTS (the Details stamp's own name - numbered facts on dotted leaders, the story as REMARKS), CHECKLIST ("N OF M CHECKED"
  over one row of typed boxes; each achievement a [X] / [ ] line with badge, "CHECKED <date>" / "NOT YET", points; A opens the
  whole text + rarity; lazy rows), SERVICE (typed option lines, stamped in the accent when focused; removing ones red with a VOID?
  second A). Heading "SHEET n OF m" + page name + small sheet tabs after the old page has gone; the next sheet feeds up from
  under; none with Motion Off. The first page's header now says INSERT · INVENTORY (it said CONTENTS, the Details stamp's name).
- Found by the tests (BOX was in no text suite before): the first page's end-aligned fact values ('Never played', '140 / 160')
  were laid out across the whole room, reported 212 px past their own box (ShelfText BOX 12/24 FAIL) -> no longer end-aligned (the
  dotted leader puts them at the right). ShelfText / PlayPlace could not reach the box's controls (CONTENTS stamp; Options / More
  as icon-only stickers) -> matched by description / "contents" word (reaching, not loosening).
- Checked (cloud): compile OK; NativePagesBox731aTest 6/6 (new; fails before: no boxPageName / BOX_SHEET not native); BOX added to
  AchFirstRow + ShelfText (846 / 0); after merging the owner's 682ar4: 11 native-page suites 1,306 / 0; PlayPlace + PlayPlaceRpc
  151 / 0; AchFirstRowRpc682r + LazyRows682aq 315 / 0; after the 682as merge PlayPlace + 730a + 731a green. Static PASS.
- Not tested: hardware (HW-28 extended), emulator review; not visually approved. Native pages: 36 / 53 own designs on the P2 branch.
- Files (P2 branch): ui/WinPagesBox.kt (new), ui/WinScenes.kt (BoxInsertSheet), ui/WinPageFlow.kt, test NativePagesBox731aTest.kt
  (new), AchFirstRowRoboTest.kt, ShelfTextRoboTest.kt, PlayPlace682abRoboTest.kt, docs/claude/NATIVE_PAGES_P2.md + _AUDIT.md.
  Dev line: hand-off docs only.

# Update 730a (24 Nov 2026, cloud run) - P2 class C: Memory card gets the save browser's own screens (native-pages branch)
- CI first: Verify 310 on d2d508f (dev line) GREEN; on 546f5b99 (729a, P2 branch) GREEN.
- Task 5 on `claude/v0674-native-pages` (worktree), 57ddc94 (merged with the owner session's 682ar3 push -> 579d131, doc e59f3c6):
  the fifth class C scene. The save browser keeps its grey screen, turning icon, yellow title and glowing choice words; its pages
  are the browser's own screens on the dark glass panel at the right: INFORMATION (label / value lines + the story as Notes),
  MEMORY BLOCKS ("N / M blocks saved" over one row of memory blocks, lit yellow for yours; each achievement a save entry - square
  badge, "Saved <date>" / "Empty block", points; A opens the whole text + rarity; lazy rows), COMMANDS (word lines with the
  choices' glow; removing ones red, second A). Heading: page name in yellow + one small block per page, the shown one lit, after
  the old page has gone. The panel now runs from under the title to above the choices (measured), the facts make way on a page.
- Found by the tests (Memory card was in no text suite before): at x1.3 a long console name ran one line under the save's title
  and was cut - also on the Info page (existing defect, ShelfText MEMCARD x1.3 6/24 FAIL) -> the card block keeps left of the
  title column and the name wraps; a wrapping 40-block strip left the first achievement 2 px short at x1.3 (AchFirstRow n=40) ->
  one row of blocks.
- Checked (cloud): compile OK; NativePagesMemCard730aTest 5/5 (new; fails on the old code); MEMCARD added to AchFirstRow +
  ShelfText; with WinFocus / NoStrayPlay / PopupMore451 / Layouts330 / WindowWordsSurvey682 / CubeFacet676i 581 tests 0 failures;
  after the merge 730a + WinFocus / NoStrayPlay MEMCARD + PageHandoff682ar + ListHeadTurn682ar green; QA selftest OK; static PASS.
- Not tested: hardware (HW-28 extended), emulator review; not visually approved. Native pages: 35 / 53 own designs on the P2 branch.
- Files (P2 branch): ui/WinPagesMemCard.kt (new), ui/StyleMemoryCard.kt, ui/WinPageFlow.kt, test NativePagesMemCard730aTest.kt (new),
  AchFirstRowRoboTest.kt, ShelfTextRoboTest.kt, docs/claude/NATIVE_PAGES_P2.md. Dev line: hand-off docs only.

# Update 729a (24 Nov 2026, cloud run) - P2 class C: Hero carousel / Hero tiles get the banner's own pages (native-pages branch)
- CI first: Verify 310 on e9092e9 (728a, dev line) GREEN; on 864e293 (P2 branch) GREEN.
- Task 5 on `claude/v0674-native-pages` (worktree), b70895f0 (merged with the owner session's 682ar / 682ar2 pushes -> 546f5b99):
  the fourth class C scene, shared by two layouts. The Hero banner keeps its key art, gradient and logo; on its pages the name
  steps down to a smaller title, the banner's tab strip (all page names, the shown one underlined in white) takes the info line's
  place, the art darkens a shade and the text column widens a little. THE FEATURE (facts as big key figures with an accent rule;
  the story as a pull quote), HALL OF FAME (big "N / M" figure, slim progress bar, each achievement a billboard row - round
  badge, "Unlocked <date>" / "Locked", points pill; A opens the whole text + rarity; lazy rows), QUICK ACTIONS (big pill buttons;
  removing ones red, second A). The next page grows up out of the banner's lower left corner; none with Motion Off.
- Found by the tests: at x1.3 the full-size title (3 reserved lines) + info line left the Hall of Fame 0 px for its first
  achievement (AchFirstRow HERO / HERO_TILES big=true 6/6 FAIL) -> the compact heading on pages fixed it.
- Checked (cloud): compile OK; NativePagesHero729aTest 4/4 (new; fails on the old code); HERO + HERO_TILES added to AchFirstRow
  + ShelfText; with WinFocus / NoStrayPlay / LazyRows / PlayPlace / CategoryStable394 / NativeConfirm / UniformKeys /
  NativePagesTime728a: 1,884 tests 0 failures (100 Hero cases, none skipped); after merging 682ar: Hero cases + LazyRows /
  ListHeadTurn682ar / PageHandoff682ar 357 tests 0 failures. Static PASS, QA selftest OK. Hardware NOT TESTED (HW-28), not
  visually approved. Not on the dev line.
  Files (P2 branch): ui/WinPagesHero.kt (new), ui/WinScenesHomes2.kt (HeroBanner), ui/WinPageFlow.kt, tests
  NativePagesHero729aTest (new), AchFirstRowRoboTest, ShelfTextRoboTest, docs/claude/NATIVE_PAGES_P2.md. Dev line: hand-off docs.
- Note: the owner's live session pushed 682ar / 682ar2 to the P2 branch while this run held the lock; merged (no conflicts), nothing overwritten.

# Update 728a (24 Nov 2026, cloud run) - P2 class C: Timeline gets its own Details / Achievements / Options (native-pages branch)
- CI first: Verify 310 on 678c10e (727a lock commit, dev line; 974c7bb itself was cancelled by the newer push) GREEN; on 8a73d96
  (P2 branch) GREEN.
- Task 5 on `claude/v0674-native-pages` (worktree), 864e293: the third class C layout. Timeline's entry (hanging from the year
  line) keeps its cover, name and console line; on its pages the facts area becomes the page, each its own small vertical
  timeline in the accent: CHRONICLE (facts as stops down the line, whole words; THE STORY SO FAR at its end, a D-pad stop),
  MILESTONES (each achievement a stop - filled node + "Reached <date>" when yours, open ring + "Ahead" when not - badge, title,
  "N pts"; "N of M milestones reached", order pill, A opens whole text + rarity; lazy rows), BRANCHES (each option a branch tick
  to a rounded tag; removing ones red, second A). Page name + a short page line (one tick per page) beside it after the old page
  has gone; pages come along the line of time (from the right forward, from the left back). Steady entry height on pages
  (max 480 dp); Play keeps its slot; L1 / R1 anywhere. The line is drawn behind each stop (no intrinsic measuring).
- Checked (cloud): compile OK; NativePagesTime728aTest 4/4 (new; fails on the old code - no timePageName, TIMELINE_ENTRY not
  native); TIMELINE added to AchFirstRow + ShelfText; with WinFocus / NoStrayPlay / LazyRows / PlayPlace (+ RPC) /
  WinPresentation / Batch508 / Visual426: 1,850 tests 0 failures (52 Timeline cases, none skipped); Hierarchy529 /
  CategoryStable394 / WindowWordsSurvey682 / NativePages681a / Batch2 / UniformKeys / NativeConfirm / PopupNativeStorage /
  Popup421 / NoArtCaption684: 597 tests 0 failures. Static PASS. Hardware NOT TESTED (HW-28), not visually approved. Not on
  the dev line.
  Files (P2 branch): ui/WinPagesTime.kt (new), ui/WinSceneFamilies.kt (TimelineHang), ui/WinPageFlow.kt, tests
  NativePagesTime728aTest (new), AchFirstRowRoboTest, ShelfTextRoboTest, docs/claude/NATIVE_PAGES_P2.md. Dev line: hand-off docs.

# Update 727a (24 Nov 2026, cloud run) - P2 class C: Channel grid gets its own Details / Achievements / Options (native-pages branch)
- CI first: Verify 310 on 045fac8 (726a, dev line) GREEN; on 5d53277 (P2 branch) GREEN.
- Task 5 on `claude/v0674-native-pages` (worktree), 8a73d96: the second class C layout. Channel grid's window (the rounded
  preview card in the grid's palette) now draws the channel's own screens in its preview well instead of the shared page body:
  CHANNEL INFO (facts on one rounded info card, the story as the "About this channel" bubble), STAMPS (rounded pills with a
  round stamp - inked star when yours, empty ring when not - and points; "N of M stamps", order pill, A opens date / rarity;
  lazy rows), SETTINGS (big rounded pills; removing ones red and ask for a second A). Header with the screen name and pager
  dots; screens slide in from the side like channel pages. Info (banner) and its More page unchanged.
- Bugs found on the way (only reachable once the scene stays composed under its pages) and fixed: B from Options landed on
  More's first key; two key rows swapped per turn (now one row, Play in the same slot on every screen); an L1 / R1 storm left
  nothing lit because the opener (Details) sits behind the closed More key - the closed More key now answers for those keys.
- Checked (cloud): compile OK; NativePagesChan727aTest 4/4 (new; fails on the old code); CHAN in AchFirstRow / ShelfText /
  WinFocus / PlayPlace (+ RPC) / NoStrayPlay / LazyRows + NativeConfirm / WinPresentation / Batch2 / UniformKeys / Chip726a /
  PopupNativeStorage: 104 tests 0 failures; Hierarchy529 (source-shape string updated to the single row), Batch494/496/506/507,
  Popup421, Visual426, WindowWordsSurvey682, CategoryStable394: green. LazyRows n=0 now also accepts the card's "Start" key.
  Static PASS. Hardware NOT TESTED (HW-28), not visually approved. Not on the dev line.
  Files (P2 branch): ui/WinPagesChan.kt (new), ui/WinScenesWii.kt, ui/WinPageFlow.kt, tests NativePagesChan727aTest (new),
  AchFirstRowRoboTest, ShelfTextRoboTest, LazyRows682aqTest, Hierarchy529Test, docs/claude/NATIVE_PAGES_P2.md. Dev line: hand-off docs.

# Update 726a (24 Nov 2026, cloud run) - P2 class C: Circuit board gets its own Details / Achievements / Options (native-pages branch)
- CI first: Verify 310 on 55b7361 (725b, dev line) GREEN; on eb4af38 (P2 branch) GREEN.
- Task 5 on `claude/v0674-native-pages` (worktree), 5d53277: the first class C layout. Circuit board's window (the chip's
  datasheet on the board, Board colours) now draws its own sections in the sheet's monospace silkscreen instead of the shared
  page body: CHARACTERISTICS (PARAMETER / VALUE table between rules, the story as FUNCTIONAL DESCRIPTION), PIN STATES (each
  achievement a numbered pin with an LED, HIGH / LOW, "N PT", order key, A opens date / rarity; lazy rows), JUMPERS (J1 ...,
  the cap bridges the pins while focused; removing ones red and ask for a second A). Heading "SECTION n · NAME" under the name
  after the old section has gone; the signal slides in from the trace side; Play keeps its slot; L1 / R1 anywhere. Info
  (PINOUT) unchanged. New WinPagesChip.kt; ChipSheet (WinScenesHomes2.kt) uses NativePages; CHIP_SHEET in NATIVE_PAGE_SCENES.
- Checked (cloud): compile OK; NativePagesChip726aTest 4/4 (new; fails on the old code - no chipPageName, CHIP_SHEET not native);
  CIRCUIT added to AchFirstRow 6/6 (+ RPC), LazyRows 8/8, PlayPlace 2/2 (+ RPC 2/2), NoStrayPlay 2/2, WinFocus 8/8, ShelfText
  24/24; WinPresentation / Visual426 / PopupNativeStorage / NativePages681a / Review682ag / UniformKeys green. Static PASS (no
  finding in the new file). Hardware NOT TESTED (HW-28), not visually approved. Not on the dev line (P2 branch until the owner's
  comparison). Files (P2 branch): ui/WinPagesChip.kt (new), ui/WinScenesHomes2.kt, ui/WinPageFlow.kt, tests
  NativePagesChip726aTest (new), AchFirstRowRoboTest, ShelfTextRoboTest, docs/claude/NATIVE_PAGES_P2.md. Dev line: hand-off docs.

# Update 725b (24 Nov 2026, cloud run) - P2 branch: dev line merged in, QA selftest red fixed (QA tooling)
- CI first: Verify 310 on 35bb129 (725a) GREEN.
- Task 5 prep on `claude/v0674-native-pages` (worktree): the dev line (721a-725a, 31 commits) merged in (31e2931; one
  conflict, tools/qa/selftest/test_qa.py - both sides' new classes kept). The P2 branch's QA selftest was RED on 7110d79
  (TestWinGuard682jc: p2review.sh r714's k_home pressed raw `input keyevent KEYCODE_BUTTON_` around the window guard).
  Fixed in eb4af38: k_home -> wg_key_home (R1 only while the confirmed home holds the focus and says HOME); the B that closes
  the R1 panel -> new winguard wg_key_main (only while the QA app's confirmed main window holds the focus; anything else in
  front aborts with nothing pressed). +2 selftests (fail on the old code). The lock file on the P2 branch keeps its own state.
- Checked (cloud): merged P2 tree compiles (:app:compileDebugUnitTestKotlin OK); QA selftest 264 OK; static checks PASS.
  Targeted unit tests NOT RUN here (Maven Central 429 on the unit-test classpath) - CI on eb4af38 runs them.
- No app code changed on either line; no hardware. Files: P2 branch tools/qa/device/winguard.sh, tools/qa/device/p2review.sh,
  tools/qa/selftest/test_qa.py; dev line hand-off docs only.

# Update 725a (24 Nov 2026, cloud run) - Task 4: START > Categories (an installed app / Android game in several categories at once)
- CI first: Verify 310 on ff28c4f (724a) GREEN.
- Owner Task 4: START on an installed app or Android game now has "Categories…" (under Details; its subtitle names where it is
  listed). The dialog (AppCategoriesDialog): Automatic / Android games / Other apps (reuses forcedGames / forcedApps), Recomps
  (724a mark), Consoles (the library's consoles first, then the ones it is in; "More consoles…" lists every console Voltcap
  names; stores / Android / Other apps / aliases never offered) and a Collections… link to the existing picker. Every press
  applies at once. New LibraryAppsSettings.appConsoles (prefs library_apps "app_consoles", keyed by package: kept across
  rescans, restarts and APK updates, backed up with library_apps). A console lists those apps with its games (same record, once
  in All); a console with only an app appears in L2 / R2 and hides again when the last one is taken off (open -> back to All).
  An app listed anywhere by hand is in the library. Manual beats automatic: a recomp you set as an app is in Other apps too
  (automatic recomps still never are). Launch, saves, play time, art, achievements and metadata are keyed as before.
- Tests: AppCategories725aTest 6/6 (new; fails on the old code - no field / no console listing), Recomps724aTest 5/5,
  ContinueApps450Test 10/10, Batch482Test 7/7, CategoryStable394Test 6/6 PASS (cloud, compiled with the Maven mirror); static
  checks PASS.
- Files to copy: app/src/main/java/com/shelf/launcher/data/AppCategories.kt (new), data/LibraryAppsStore.kt, data/ConsoleNames.kt,
  LauncherUiState.kt, LauncherViewModel.kt, ui/AppCategoriesDialog.kt (new), ui/GameOptionsDialog.kt, ui/LauncherScreen.kt,
  ui/LauncherUiFlags.kt; test app/src/test/java/com/shelf/launcher/data/AppCategories725aTest.kt (new).
- NOT TESTED: handheld (HW-24 725a: D-pad / touch, x1.3 popclip, restart). ROMs (a ROM in a second console) not offered - Task 4
  names installed games / apps.

# Update 724a (24 Nov 2026, cloud run) - Task 3: Recomps category (installed recomp / native-port apps, marked by hand)
- CI first: Verify 310 on c7f4430 (723a) GREEN.
- Owner 22:43 item 5 / Task 3: new platform key RECOMPS_PLATFORM ("Recomps"); LibraryAppsSettings.recomps (prefs library_apps
  "recomps", already backed up by SettingsScopes). Settings > Apps in your library: a "Recomp?" / "Recomp ✓" chip under each
  app's name (D-pad / touch). A marked app: in Recomps and in All (whatever the apps mode), never in Other apps; an Android game
  stays in Android games too (detector untouched); same Game record (launch, stable key, saves, play time, art, metadata
  unchanged; no duplicate in All). The category appears in L2 / R2 / tiles only while it has a game; taking the last mark off
  while Recomps is open goes back to All. Left out of the ROM-platform lists (collections dialog etc.). No auto-suggest: no
  reliable detector exists (no package-name guessing, Windows builds never assumed) - stays OPEN for a vetted list.
- Tests: Recomps724aTest 5/5 (new; fails on the old code - no key / no field), ContinueApps450Test 10/10, Batch482Test 7/7,
  CategoryStable394Test 6/6 PASS (cloud); static checks PASS.
- Files to copy: app/src/main/java/com/shelf/launcher/LauncherUiState.kt, LauncherViewModel.kt, data/LibraryAppsStore.kt,
  ui/AppsLibraryDialog.kt, ui/LauncherScreen.kt; test app/src/test/java/com/shelf/launcher/data/Recomps724aTest.kt.
- NOT TESTED: handheld (HW-24 724a). START -> multiple categories (Task 4) is the next item.

# Update 723a (24 Nov 2026, cloud run) - Task 2: new R1 Focus position tile (Voltcap row, Tile home) + 53-layout matrix
- CI first: Verify 310 on 4404495 GREEN (b57bf66's run cancelled by the newer push).
- Owner 22:43 item 4 / Task 2: audit of the 53 layouts (code): only the Voltcap row (FocusPosition, GameDock) and Tile home
  (TileFocus, TileHome) have a horizontal focus anchor; matrix docs/claude/R1_FOCUS_POSITION_723.md (SUPPORTED 2, NOT OFFERED 51,
  Hero's cover side is not a focus position). New R1 tile "Focus" (FocusPosTile723.kt): reuses those two settings (no new pref,
  nothing migrated); press = next choice (Left / Middle; Edges / Left / Middle / Right), hold = the list with the current one
  marked; hidden on the other 51 layouts; greyed with the reason while the carousel look (always middle) or the Voltcap row's
  "Covers · first one wide" keeps the game in one place. Goes after the saved R1 tiles (R1_APPENDED): saved order unchanged;
  pinnable to L1 ("Focus position"); in the R1 tile walk (TileCheck).
- Tests: FocusPosTile723aTest 4/4 (new; old code had no tile / rule), R1Compat326Test 6/6, Batch491Test 6/6, TileWalk329Test 7/7,
  RowTileExactHandler718aTest 3/3 PASS (cloud, compiled with the Maven mirror); static checks PASS.
- Files to copy: app/src/main/java/com/shelf/launcher/ui/FocusPosTile723.kt (new), ui/QuickSettings.kt, ui/R1TileRules.kt,
  ui/R1Extras.kt, ui/R1Shortcuts.kt, ui/TileCheck.kt, data/BuiltinShortcuts.kt,
  app/src/test/java/com/shelf/launcher/ui/FocusPosTile723aTest.kt (new); docs/claude/R1_FOCUS_POSITION_723.md (new).
- Hardware NOT TESTED: HW-24 723a (KONKR x1.0 / x1.3: focus movement, Endless, category alignment, hold list by D-pad + touch).

# Update 722a (24 Nov 2026, cloud run) - Task 1: Edit Home / preset preview matches the real Home at Top
- CI first: Verify 310 on e77f9fa (721a) GREEN.
- Owner 22:43 item 3 ("Edit Home previews kept", preview = real Home): PresetPreview at Top drew the row at 0.2 with the game's
  name ABOVE it and the clock / battery bar at the TOP. The real Home at Top (Classic row, Tile home; not Grid, TV room or a style
  layout) puts the row right under the edge (16 dp / 60 dp), the name UNDER it (nameBelow) and the status bar at the BOTTOM,
  above the legend. New pure PreviewDock (same rule as LauncherScreen atBottom / barTop); Middle and Bottom drawn exactly as before.
- Tests: PreviewDock722aTest 4/4 PASS (new; fails on the old fixed 0.2 top) (cloud, compiled with the Maven mirror); static checks PASS.
- Files to copy: app/src/main/java/com/shelf/launcher/ui/PresetPreview.kt,
  app/src/test/java/com/shelf/launcher/ui/PreviewDock722aTest.kt (new).
- Hardware NOT TESTED: HW-24 722a (by eye, KONKR, with 721a).

# Update 721a (24 Nov 2026, cloud run) - Task 1: Tile home Top keeps clear of the profile pictures AND a top-corner music player
- CI first: Verify 310 on a8a3ef6 (720a) GREEN.
- Owner 22:43 item 3 (Tile home Top vs the profile row, Batch 521 / Sprint 527 minimum): audited. Middle already keeps both
  floors (badgeFloor, musicFloor). Top did not: with Switch-style profile pictures shown it used maxOf(60 dp, pictures + 8 dp) and
  ignored the music player Home shows in a top corner (Sprint 528's floor applied only without the pictures), so a player at the
  top right (~112 dp bottom) sat on the tiles / first pill. New pure TileRows.topPadDp: 8 dp under whichever reaches lower; with
  only one of them (or neither) the result is exactly as before (60 / measured pictures / 10 dp / music + 8).
- Tests: TileTopMusic721aTest 3/3 (new; the old rule gives 60 dp for the 120 dp case), Batch521Test 4/4 + TileRowsTest 12/12
  (source checks moved to the helper), TileRowPlaceSpots714aTest 4/4 PASS (cloud, compiled); static checks PASS.
- Files to copy: app/src/main/java/com/shelf/launcher/ui/LauncherScreen.kt, ui/TileRows.kt,
  app/src/test/java/com/shelf/launcher/ui/TileTopMusic721aTest.kt (new), Batch521Test.kt, TileRowsTest.kt.
- Hardware NOT TESTED: HW-24 721a (by eye, KONKR x1.0 / x1.3).

# Update 720a (24 Nov 2026, cloud run) - Task 1: QA path for the real Edit Home D-pad move of the added Play part (715a)
- CI first: Verify 310 on d11266d (719a) GREEN.
- Owner 22:43 item 3: "a safe path for the real Edit Home D-pad movement and the added Play part". New (QA app / hooks file only,
  normal use unchanged): voltcap_qa_edit ("EDIT <part>" while Edit Home is open, "HOME" otherwise) so a script presses D-pad
  keys in Edit Home only while it really is open on the part, and `--ez voltcap_test_reach true` logs one `reach` line built
  from the home's own prepareReach (the decision a real ▲ / ▼ uses): the Play part's saved / moving / drawn y, above / below,
  the zones up / down, the zone the D-pad is on, Edit Home on / off (PlayReach720.kt).
- tools/qa/device/play720.sh (LEVEL 1, QA app only, snapshot saved / restored; never A, R1 or taps; STOP with nothing pressed
  when a mark does not match): header off + "Play / Continue" added on the Voltcap row at y 0.44 -> ▲ reaches it; Edit Home,
  five real ▼ -> Done -> ▼ reaches it; ▲ ▲ + Y (Undo) = one step; Discard keeps the saved place; restart keeps it; Reset this
  part; Row Top through the R1 tile's handler (preview) -> ▲ reaches Play. tools/qa/tools/play720.py grades it (wrong
  direction, unsaved / wrong move, Undo / Discard / Reset mistakes, a missed key = FAIL; setup not applied, old build, STOP = INFRA).
- Tests: PlayReach720aTest 3/3 (new), HeaderReachDirection715aTest 3/3, RowCombo719aTest 3/3 PASS (cloud, compiled with the
  Maven mirror); QA selftest 235/235 incl. TestPlay720Grader720a 6/6; static checks PASS.
- Files to copy: app/src/main/java/com/shelf/launcher/ui/PlayReach720.kt (new), ui/LauncherScreen.kt, ui/TestHooks.kt,
  app/src/test/java/com/shelf/launcher/ui/PlayReach720aTest.kt (new), tools/qa/device/play720.sh (new),
  tools/qa/tools/play720.py (new), tools/qa/selftest/test_qa.py.
- Hardware NOT TESTED: HW-24 720a (play720.sh x1.0 + x1.3 on the KONKR; RP Classic too if connected).

# Update 719a (23 Nov 2026, cloud run) - Task 1: QA path for 716a's real combination (header + news + every category + bar player)
- CI first: Verify 310 on d1c70cd (718a) GREEN.
- Owner 22:43 item 3: "716a passed with defaults - still the combination game header + game / news + full category bar + music /
  status widgets". The 22:03 live result said no QA hook could set those options. New (QA builds with the hooks file only,
  normal use unchanged): `--es voltcap_test_rowcombo on|off` previews that combination on the layout in use (RowCombo719.appearance:
  Game info + news, category bar of every category incl. the layout's own choice; Features.homeHeader on, held in memory
  during the preview; a fake song so the status bar's player shows; off puts the header and the song back) and
  `--ez voltcap_test_rowcol true` logs one `rowcol` line: where the Voltcap row's column landed (top / bottom) vs the status
  bar's bottom edge and the screen, in dp, plus which parts were really on.
- tools/qa/device/row716.sh (LEVEL 1, QA app only, snapshot saved / restored, no key / tap except B on a first-start picker):
  Classic, defaults at Middle / Bottom / Top, then the combination at Middle / Bottom / Top / Middle through the R1 tile's own
  step (voltcap_test_dock = rowTilePress). tools/qa/tools/row716.py: FAIL when the column starts under the bar or runs off the
  bottom although it fits; a taller-than-the-room column spilling at the bottom is noted (716a's design); combination not on,
  wrong layout or an old build = INFRA.
- Tests: RowCombo719aTest 3/3 (new), RowColumnUnderBar716aTest 4/4, RowTileQaLine717aTest 3/3, RowTileExactHandler718aTest 3/3 PASS
  (cloud, compiled); QA selftest 229/229 incl. TestRow716Grader719a 6/6; static checks PASS.
- Files to copy: app/src/main/java/com/shelf/launcher/ui/RowCombo719.kt (new), ui/LauncherScreen.kt, ui/TestHooks.kt,
  app/src/test/java/com/shelf/launcher/ui/RowCombo719aTest.kt (new), tools/qa/device/row716.sh (new), tools/qa/tools/row716.py (new),
  tools/qa/selftest/test_qa.py.
- Hardware NOT TESTED: HW-24 719a (row716.sh x1.0 + x1.3 on the KONKR; RP Classic too if connected).

# Update 718a (23 Nov 2026, cloud run) - Task 1: the Row tile QA hook now runs R1's exact Row tile handler
- CI first: Verify 310 on bbe5c98 (owner backlog 22:43, contains 717a) GREEN; 0d7b630 / 886835d cancelled by the newer push.
- Owner 22:43 Task 1: "verify it is the exact handler". It was not quite: the R1 Row tile and the hook `voltcap_test_dock`
  each had their own copy of the change (Tile home reset + new place). Now both call one step, rowTilePress(a, to)
  (ui/QuickSettings.kt), and rowTileNext(a) gives the tile's cycle (Top -> Middle -> Bottom -> Top). The tile saves the result
  (unchanged behaviour), the hook previews it. `--es voltcap_test_dock NEXT` = one press of the tile (its own cycle).
- row714.sh: three NEXT presses added (Middle -> Bottom -> Top -> Middle), each graded on the place it lands on as well as the
  buttons' spots (row714.py: optional 4th expect field; older logs still grade).
- Tests: RowTileExactHandler718aTest 3/3 (new: the tile and the hook both call rowTilePress, no private copy, NEXT follows the
  cycle), RowTileQaLine717aTest 3/3, TileRowPlaceSpots714aTest 4/4, Batch521Test 4/4 PASS (cloud, compiled; two source-shape
  assertions moved to the shared step, same check); QA selftest 223/223 incl. TestRow714NextPress718a 2/2; static checks PASS.
- Files to copy: app/src/main/java/com/shelf/launcher/ui/QuickSettings.kt, ui/LauncherScreen.kt, ui/TestHooks.kt,
  app/src/test/java/com/shelf/launcher/ui/RowTileExactHandler718aTest.kt (new), RowTileQaLine717aTest.kt, TileRowPlaceSpots714aTest.kt,
  Batch521Test.kt, tools/qa/device/row714.sh, tools/qa/tools/row714.py, tools/qa/selftest/test_qa.py.
- Hardware NOT TESTED: HW-24 714a (row714.sh x1.0 + x1.3 on the KONKR, now incl. the three tile presses).

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

