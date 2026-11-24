# Hardware QA requests (physical handhelds)

Cloud work never waits for the PC or a handheld. When a change needs a real device, add a request here (and, where the
session can, a GitHub issue labelled `hardware-qa`), then carry on with other work. When the owner says the PC and handhelds
are available, a live session works through the OPEN requests with the PC's remote-device tools: isolated QA app only
(`com.shelf.launcher.qa`), production apps / settings / saves / emulator configurations untouched, device state restored after
every run, results pushed and the requirements matrix updated. A request is DONE only with a real-device PASS; an emulator
result never closes one.

Never assume the PC or devices are connected. Never wake, connect, install to or change a device without the standing
authorization in CLAUDE.md (USB only, QA app only).

**Availability (owner 17 Nov 14:45 Berlin): the AYN Thor and the RP Classic are NOT with the owner - never connect, poll, wake or test them; their requests stay NOT TESTED. KONKR only if already connected (check once before device work). No new full 53-layout sweep without a reason; no long hardware runs during the owner's work session.**

Devices: Flip 2 (Retroid Pocket Flip2, 2037b27d, 1920x1080 @ 360 dpi), KONKR (GT78, BW0306N230000845, 960x640 @ 240 dpi),
RP Classic, AYN Thor (3140972f, two screens), Odin.

## Format
```
### HW-<n> <title> - OPEN | IN PROGRESS | DONE | BLOCKED
- Device(s): ...
- Build: commit <sha> on claude/qa-0672-repair; QA build = assembleQa / assembleDebug of that commit
- Why not emulator: ...
- Tests: exact gates / scripts (e.g. pop529.sh run <tag> <off|font=1.3,density=1.15> "<LAYOUTS>" OWN, then popclip.py)
- Also (686a, H16, dev line 686a or later, ~1 min, KONKR x1.3): Orbit deck (ORBIT) at x1.3 in the narrow window: the key
  labels under the round keys read whole - "ACHIEVEMENTS", "SAVE SYNC", "INSPECT" never broken inside the word (spacing a little
  tighter / a step smaller there if they must); x1.0 looks as before. pop529 ORBIT OWN -> popclip.py. Unit evidence only
  (WindowWordsSurvey682Test NEXT_686, OrbitKeyLabel686Test).
- Also (688b, H16, dev line 688b or later, ~1 min, KONKR x1.3): TV room, open a game with achievements on the set, turn to
  Achievements (R1): "Set order" whole on its own line under the count, achievement names whole (points under the badge), the
  first achievement in view, A on the order row cycles it; x1.0 and a wide page (any other layout) look as before. pop529 TV OWN
  -> popclip.py. Unit evidence only (WindowWordsSurvey682Test[TV x1.3] otherPages; CI).
- Also (689a, rule 11, dev line 689a or later, ~2 min, KONKR x1.3 + x1.0): game popup style Classic (the full-screen details
  window), a game with a long name, a long info line (year / developer / publisher / genres) and achievements: the title wraps to
  two lines, then shrinks, then slides - never ends in an ellipsis; info line, 'Last session', the stat labels / values, the page
  tabs, the milestone line, the trophy strip and the inline achievement names / descriptions whole (slide where they must); a
  summary under ~260 characters shown whole (no More chip), a longer one 4 lines + More; RA comments whole. pop529 <layout> CLASSIC
  -> popclip.py. Unit evidence only (GameDetailsFullText689aTest, static TRUNCATE 171 -> 158).
- Also (691a, rule 11, dev line 691a or later, ~2 min, KONKR x1.3 + x1.0): home layout Twin icons, a game with a long name and a
  long console name: the focused game's name and its console / playtime line shrink a little, then slide - never end in an
  ellipsis; on the category page the category card's name + hint, the small labels under the category icons (cells >= 44 dp) and
  the Open bar's 'Open · <category>' / 'Open · <game>' read whole (slide in the bar); the tray does not move when the focus moves.
  Screenshots. Unit evidence only (TwinIconsFullText691aTest, static TRUNCATE 150 -> 143; first compile in CI).
- Also (692a, rule 11, dev line 692a or later, ~2 min, KONKR x1.3 + x1.0): classic row (GameCard) with a game that has no art and
  a very long name, a focused wide capsule (long title, console name on, 'Played ...' on), a card with a NEW unlock pill and a
  trophy count, and the badge mode 'Recent unlock' with a long achievement title: every text whole (shrinks, then slides) - never
  an ellipsis; the cards / capsule do not change size or jump when the focus moves. Screenshots. Unit evidence only
  (GameCardFullText692aTest, static TRUNCATE 143 -> 136; first compile in CI).
- Also (693a, rule 11, dev line 693a or later, ~2 min, KONKR x1.3 + x1.0): volume mixer (R1 / quick settings -> mixer) with a
  running game that has a long name and an app with a long label: title, status line, stream labels, app names, 'Playing ...'
  notes, volume values and the add-app picker's names read whole (shrink, then slide) - never an ellipsis; rows keep their
  height. Screenshots. Unit evidence only (MixerFullText693aTest, static TRUNCATE 136 -> 129; cloud compile PASS).
- Also (694a, rule 11, dev line 694a or later, ~2 min, KONKR x1.3 + x1.0): a home layout with the row info panel + news card
  on, a game with long genres / developer / publisher and a long summary, and a long news headline: facts line, chips,
  summary, news source and headline read whole (shrink, then slide) - never an ellipsis; the panel keeps its height.
  Screenshots. Unit evidence only (RowInfoPanelFullText694aTest, static TRUNCATE 129 -> 124; not compiled in the cloud).
- Also (695a, rule 11, dev line 695a or later, ~2 min, KONKR x1.3 + x1.0): Twin desk home layout, a long category name in the
  bottom bar, a game with a long console name and long playtime, the calendar on a long month name (a long locale): month title,
  console · playtime line, list console names and the bar label read whole (shrink, then slide) - never an ellipsis; the desk
  keeps its layout; L2 / R2 swaps the bar label in the same frame. Screenshots. Unit evidence only (TwinDeskFullText695aTest,
  static TRUNCATE 124 -> 120; not compiled in the cloud).
- Also (696a, rule 11, dev line 696a or later, ~2 min, KONKR x1.3 + x1.0): home widget board, sound widget (compact + expanded)
  with an app whose name is long playing sound: the app name reads whole (shrink, then slide) beside its volume - never an
  ellipsis; the volume bars stay put. Screenshots. Unit evidence only (WidgetBoardFullText696aTest, static TRUNCATE 120 -> 118;
  not compiled in the cloud).
- Also (701a, rule 11, dev line 701a or later, ~2 min, KONKR x1.3 + x1.0): Settings > Profile with a RetroAchievements
  account - the friends card with a friend whose last award has a long title + console, and the Achievement of the Week card
  with a long title / description / game · console: every text reads whole (shrink, then slide) - never an ellipsis; cards keep
  their spacing; D-pad + touch reach the weekly card. Screenshots. Unit evidence only (RaSocialFullText701aTest, static TRUNCATE
  98 -> 94; NOT cloud compiled - CI first compile).
- Also (705a, rule 11, dev line 705a or later, ~2 min, KONKR x1.3 + x1.0): cross-bar trophy list (CrossTrophies) - a game
  with long achievement titles / descriptions, scroll through the list and open the comments panel: every title, the focused
  description (2 lines), the meta line and the comments title read whole (shrink or slide) - never an ellipsis; rows keep their
  spacing while focus grows a row; D-pad + touch scroll the list and comments. Screenshots. Unit evidence only
  (CrossTrophiesFullText705aTest, static TRUNCATE 90 -> 86; NOT cloud compiled (Maven 429) - CI first compile).
- Also (709a, rule 11, dev line 709a or later, ~1 min, KONKR x1.3 + x1.0, with 707a / 703a): R1 quick panel - change a setting
  with a long name (undo strip "Changed: ..." shrinks, then slides), open the tile editor and pick a tile with a long name (2 lines,
  then shrink, then slide) and look at captions with spaces - never an ellipsis; Undo / swipe-away, D-pad + touch still work.
  Screenshots. Unit evidence only (QuickSettingsFullText709aTest, cloud compiled + PASS; static TRUNCATE 81 -> 78).
- Also (711a, rule 11, dev line 711a or later, ~1 min, KONKR x1.3 + x1.0, with 710a): a game with RetroAchievements progress -
  game popup's Journey and the Hero carousel journey: every step's title, date and detail whole (wrap, shrink, then slide),
  never an ellipsis; nothing overlaps the rail. D-pad + touch still work. Screenshots.
  Unit evidence only (GameJourneyFullText711aTest, cloud compiled + PASS; static TRUNCATE 75 -> 72).
- Also (712a, rule 11, dev line 712a or later, ~1 min, KONKR x1.3 + x1.0, with 711a): RetroAchievements dialog with a
  long-titled recent achievement and a long account name - name, game and account name whole (wrap, shrink, then slide),
  never an ellipsis; "Play as" / "Remove" chips stay whole and reachable. D-pad + touch still work. Screenshots.
  Unit evidence only (RaDialogFullText712aTest; static TRUNCATE 72 -> 69).
- Also (713a, rule 11, dev line 713a or later, ~2 min, KONKR x1.3 + x1.0, with 712a): shared dialog chrome - a text field
  holding a long value (e.g. a long ROM folder path / server address), a menu sheet with a long title (e.g. a long game name's
  menu), and chips in a narrow row (Settings chip rows, RA "Play as") - whole (shrink, then slide), never an ellipsis; chips
  keep their size, focus ring and touch target. D-pad + touch still work. Screenshots.
  Unit evidence only (DialogChromeFullText713aTest; static TRUNCATE 69 -> 66).
- Also (715a, Task 1 Row-position, dev line 715a or later, ~2 min, KONKR x1.0, with 714a): Voltcap row, game header OFF,
  Edit Home + Add "Play / Continue" (leave it at its spot, upper right); R1 Row tile Top: from the games ▲ must reach Play (▼ did
  before); move the part low, Row Bottom: ▼ reaches it. Header ON again: ▲ / ▼ as before (Row Top -> ▼ to the header under the
  row). A on the reached Play launches the game. Unit evidence only (HeaderReachDirection715aTest 3/3).
- **717a (cloud, answers the 22:15 note): 714a is testable WITHOUT the R1 panel.** The Row tile's own handler already has a
  QA hook (Batch 521, `--es voltcap_test_dock TOP|MIDDLE|BOTTOM`, preview only: tileRowPlaceReset + withDockHere); 717a adds
  one `rowtile` log line per change (layout, from->to, buttons' spot, every kept spot). Run (LEVEL 1, KONKR, QA app from the
  dev line 717a or later, ~2 min, no key / tap except B on a first-start picker, snapshot saved and restored):
  `sh tools/qa/device/row714.sh r714` (x1.0) and `sh tools/qa/device/row714.sh r714b font=1.3`, pull /sdcard/pc/<tag>, then
  `python3 tools/qa/tools/row714.py <dir>` (PASS / FAIL / INFRA; screenshots + QaClip dumps beside it show the buttons
  at each step). Covers: placed at Middle -> Top (normal spot) -> Middle (spot back) -> Bottom, placed at Bottom, Top -> Bottom
  (its own spot) -> Middle (Middle's spot) -> Middle again (unchanged). The real R1 tile tap stays a by-hand owner check.
- **718a (cloud): the hook IS now the exact handler.** R1's Row tile and `voltcap_test_dock` call the same step (rowTilePress;
  RowTileExactHandler718aTest guards it); `voltcap_test_dock NEXT` = one tile press. row714.sh (dev line 718a or later) adds
  s8-s10, three NEXT presses Middle -> Bottom -> Top -> Middle, graded on the landing place too. Same run commands as 717a.
- **719a (cloud): 716a's combination now has its QA path** (answers the 22:03 result's "no QA hook sets those options").
  `--es voltcap_test_rowcombo on|off` previews game header + "Game info + news" + category bar of every category + a song in
  the status bar's player (nothing saved; off puts the header and the song back); `--ez voltcap_test_rowcol true` logs one
  `rowcol` line (column top / bottom vs the bar's bottom edge, dp). Run (LEVEL 1, QA app only, dev line 719a or later, ~2 min,
  no key / tap except B on a first-start picker, snapshot saved and restored): `sh tools/qa/device/row716.sh r716` (x1.0) and
  `sh tools/qa/device/row716.sh r716b font=1.3`, pull /sdcard/pc/<tag>, `python3 tools/qa/tools/row716.py <dir>`. Steps:
  baseline Middle / Bottom / Top, then the combination Middle / Bottom / Top / Middle through the R1 tile's own step.
- **720a (cloud): 715a (added Play part + real Edit Home D-pad) now has its QA path.** The QA app writes voltcap_qa_edit
  ("EDIT <part>" while Edit Home is open, else "HOME") and `--ez voltcap_test_reach true` logs one `reach` line (the Play part's
  saved / moving / drawn place, the home's own ▲ / ▼ decision, the zone the D-pad is on). Run (LEVEL 1, QA app only, dev line
  720a or later, ~2 min, snapshot saved and restored, never A / R1 / taps; D-pad keys in Edit Home only on "EDIT CLASSIC:h_play",
  home keys only on "HOME", else STOP with nothing pressed): `sh tools/qa/device/play720.sh p720` (x1.0) and
  `sh tools/qa/device/play720.sh p720b font=1.3`, pull /sdcard/pc/<tag>, `python3 tools/qa/tools/play720.py <dir>`. Steps: header
  off + Play added at y 0.44 -> ▲ reaches it; Edit Home 5 real ▼ -> Done -> ▼ reaches it; ▲ ▲ Y (Undo) -> one step; Discard
  keeps the saved place; restart keeps it; Reset this part; Row Top (tile handler, preview) -> ▲ reaches Play. KONKR + RP Classic.
- **721a (cloud): Tile home Top with the profile pictures AND a music player in a top corner.** Top kept the rows clear of the
  pictures only (60 dp), so a player Home shows top-right (~52 dp down) could sit on the tiles / first pill; now 8 dp under
  whichever reaches lower (TileRows.topPadDp; TileTopMusic721aTest 3/3 cloud). By eye (LEVEL 1, QA app, ~2 min, dev line 721a or
  later): Tile home, Switch-style profiles on, music player top-right with a song playing, R1 Row -> Top, x1.0 and x1.3 - the
  first pill / tiles start under the player, nothing overlaps; player off -> rows back up under the pictures. KONKR.
- **722a (cloud): Edit Home / preset preview vs the real Home at Top.** The little drawn preview put the row at 0.2 with the
  name above it and the clock bar at the top; the real Home at Top (Classic row, Tile home) has the row under the edge, the
  name under it and the clock bar at the bottom (above the legend). Preview follows that now (PreviewDock; Middle / Bottom
  unchanged; PreviewDock722aTest 4/4 cloud). By eye (LEVEL 1, QA app, ~1 min, same session as 721a): Settings > Presets, a
  preset whose row is at Top (Classic and Tile home), preview beside the real Home after applying - same order top to bottom. KONKR.
- **RESULT 23 Nov 22:15 (live session, KONKR, dev line 7bd1dea QA app, Level 1) - 714a: NOT TESTED, by choice.** The R1 panel
  opens with its Quick row first (Wi-Fi, Bluetooth, Do not disturb...): the Row tile is further down (collapsed), and tapping or
  D-padding through that panel by script risks a system toggle - forbidden. The hand-placed buttons spot was set (widget_positions
  "shortcuts=0.3000,0.2000" - drawn there), nothing in the panel was pressed or tapped, restore diff 0. To test without touching
  system tiles: a QA-only hook that runs the Row tile's own handler (tileRowPlaceReset + withDockHere(next)) - e.g.
  `--ez voltcap_test_rowtile true` - then p2review.sh r714 (P2 branch 682ao) reads the result from the snapshot JSON (run-as is
  refused: the QA app is not debuggable). Device note: Wi-Fi went off at 22:18 while the KONKR slept - the vendor home
  (com.ayaneo.home) switches it off in sleep and back on at wake ("Set WiFi enabled" at every wake); not caused by the QA tools.
- **RESULT 23 Nov 22:03 (live session, KONKR GT78 USB, QA app built from dev line 7bd1dea = 716b, Level 1):** 716a PASS for the
  QA app's own settings - Voltcap row and Tile home at Row Top / Middle / Bottom, x1.0 (kr10) and x1.3 (kr13): every text of the
  home whole and on screen (QaClip dumps, nothing cut, nothing under the status bar); at Middle / Bottom the game header starts
  below the clock / battery at x1.3; at Top the status bar sits at the bottom, clear. Header ON + "Game & news" + category bar
  Full + music player: NOT TESTED (no QA hook sets those appearance options - next step: drive Settings or extend the snapshot
  hook). 714a (R1 Row tile + hand-placed round buttons) and 715a (Edit Home Play part reach): NOT TESTED - they need the real R1
  tile and Edit Home moves (the voltcap_test_dock hook bypasses 714a's tile handler). Restore diff 0, 0 launches; the KONKR is
  back on the P2 QA build 682am. Tool: p2review.sh rows (P2 branch).
- Also (716a, Task 1 Row-position, dev line 716a or later, ~3 min, KONKR x1.3 QA font scale, with 714a / 715a): Voltcap row,
  game header ON + Next to the row "Game & news" + category bar Full, status bar on, legend on. R1 Row tile Middle, then Bottom:
  the header's top line must sit fully below the clock / battery (it could start under them before); at x1.0 nothing moves
  compared with 715a (same screenshots). Music player in the status bar (top left) once: same check. Unit evidence only
  (RowColumnUnderBar716aTest 4/4).
- Also (714a, Task 1 Row-position, dev line 714a or later, ~2 min, KONKR x1.0, with 713a): Tile home, round buttons "by the
  tiles" moved in Edit Home at Row Middle; R1 Row tile Middle -> Top -> Bottom -> Middle: at Top / Bottom the buttons sit at their
  normal spot (no overlap with the tiles or the profile row), back at Middle the hand-placed spot returns; the same with Settings
  "Where the tiles sit". Screenshots of each place. Unit evidence only (TileRowPlaceSpots714aTest 4/4, Batch521Test 4/4).
- Also (710a, rule 11, dev line 710a or later, ~1 min, KONKR x1.3 + x1.0, with 709a): open a game window's Performance page
  (a game with a long profile name) - every row's name and value whole (wrap, shrink, then slide), never an ellipsis; the main
  page's 2-line story preview may end in "…" but the Details page shows it whole. D-pad + touch still work. Screenshots.
  Unit evidence only (GameWindowFullText710aTest, cloud compiled + PASS; static TRUNCATE 78 -> 75).
- Also (707a, rule 11, dev line 707a or later, ~1 min, KONKR x1.3 + x1.0, with 703a below): home edit mode toolbar - select a
  widget with a long name (and a hidden one): "Edit Home · <name> (hidden)" and its scope line read whole (shrink, then slide) -
  never an ellipsis; toolbar keeps its size at top and bottom; narrow layout keeps the actions row. Screenshots. Unit evidence
  only (HomeEditLegendFullText707aTest, cloud compiled + PASS; static TRUNCATE 86 -> 84).
- Also (703a, rule 11, dev line 703a or later, ~2 min, KONKR x1.3 + x1.0): home edit mode - open the inspector on a widget
  with a long name / scope line, and the widget list with a hidden + an off-screen widget: every name and status line reads
  whole (shrink, then slide) - never an ellipsis; rows keep their spacing; D-pad + touch reach Hide / Show and Fold / Close.
  Screenshots. Unit evidence only (HomeEditFullText703aTest, static TRUNCATE 94 -> 90; cloud compiled, test run in CI).
- Also (700a, rule 11, dev line 700a or later, ~2 min, KONKR x1.3 + x1.0): game guide screen - a game whose guide has a
  long title and long attribution / source URL, a deep contents list with long section titles, and the guide picker with
  long guide titles / subtitles: every text reads whole (shrink, then slide) - never an ellipsis; rows keep their spacing;
  D-pad + touch reach each row and section. Screenshots. Unit evidence only (GuideScreenFullText700aTest, static TRUNCATE
  103 -> 98; NOT cloud compiled - CI first compile).
- Also (699a, rule 11, dev line 699a or later, ~2 min, KONKR x1.3 + x1.0): achievement hub - a game with a long title in the
  progress rows (with "Mastered" / progress note) and a recent unlock with a long achievement title and long game · console
  line: every text reads whole (shrink, then slide) - never an ellipsis; rows keep their spacing; D-pad + touch reach each row.
  Screenshots. Unit evidence only (HubScreensFullText699aTest, static TRUNCATE 108 -> 103; cloud compiled).
- Also (698a, rule 11, dev line 698a or later, ~2 min, KONKR x1.3 + x1.0): music player pages - Spotify disconnected (connect
  hint), a playlist with a long name (strip + grid) and a song with long synced lyric lines: every text reads whole (shrink,
  then slide) - never an ellipsis; rows keep their spacing; D-pad + touch still reach every playlist. Screenshots.
  Unit evidence only (PlayerPagesFullText698aTest, static TRUNCATE 113 -> 108; cloud compiled).
- Also (697a, rule 11, dev line 697a or later, ~2 min, KONKR x1.3 + x1.0): in-game quick menu over a RetroAchievements game
  with long achievement titles / descriptions (Achievements tab) and a paired Bluetooth device with a long name (Bluetooth tab):
  every text reads whole (shrink, then slide) - never an ellipsis; rows keep their spacing; D-pad + touch still reach every row.
  Screenshots. Unit evidence only (QuickMenuFullText697aTest, static TRUNCATE 118 -> 113; cloud compiled).
- Result: (filled in by the hardware session: run tags, PASS / FAIL counts, evidence path)
```

## Levels (CLAUDE.md "Hardware QA levels", owner 16 Nov)
Every request states its level: L1 QA app (default, autonomous on authorized USB devices), L2 production read-only (declared
observation only, nothing changed, no input beyond what the plan declares), L3 production modification (the owner's explicit
approval of that step, backup + rollback + certificate check first). An accidental switch to the production app: stop, press
nothing, log a defect. Evidence is labelled QA or PRODUCTION. All requests below are L1 unless they say otherwise.
Owner 16 Nov: replacing an incompatible QA app (uninstalling com.shelf.launcher.qa only - its own test data) is always allowed.

## Requests

### HW-28 P2 726a + 727a + 728a + 729a + 730a + 731a + 732a + 733a + 734b + 735a + 736a + 737a Circuit board's datasheet sections + Channel grid's own screens + Timeline's own pages + Hero banner's pages + Memory card's browser screens + Cardboard box's insert sheets + File select's own screens + Case flow's own pages + Orbit deck's console screens + Airy home's panel pages + Bubble home's card pages + Pocket camera's menu pages (v0674-native-pages 5e1b096) - OPEN (batch with the next KONKR + RP Classic P2 session)
- Device(s): KONKR (GT78 BW0306N230000845) + RP Classic, USB. Level 1 (QA app only). Branch `claude/v0674-native-pages` at 5d53277
  or later. ~6 min per handheld, x1.0 + x1.3.
- Why not emulator: the popup gate (popclip, R26) and Play-same-place are hardware gates; emulator sheets are preview only.
- Tests: home layout Circuit, a game with achievements (synthetic 120 + a short set): p2review walk Info -> CHARACTERISTICS ->
  PIN STATES -> JUMPERS (R1 / R1 / R1 / L1 back), heading "SECTION n" changes after the old section is gone; Play same size and
  place on every section (QaClip); PIN STATES lazywalk (140 Downs / Ups, B, reopen); a removing jumper armed and left (never
  confirmed); popclip.sh + popclip.py PASS; 0 launches, restore diff 0; one strip of screenshots per scale for the owner.
- Evidence so far: Robolectric only (NativePagesChip726aTest, AchFirstRow / LazyRows / PlayPlace / NoStrayPlay / WinFocus /
  ShelfText CIRCUIT cases green in the cloud). Not on hardware, not visually approved.
- 727a adds Channel grid (same session, same build 8a73d96 or later): p2review walk Info -> Channel Info -> Stamps -> Settings
  (R1 x3, L1 back) with the pager dots following; Play ("Start" on Info) same place on every screen; B from a screen turned to
  with R1 lands on MORE (never Start), B from Options opened through More lands on Options; L1 / R1 storm then B stays in the
  window; Stamps lazywalk; a removing setting armed and left; popclip.sh + popclip.py PASS; 0 launches; x1.0 + x1.3; Light and
  OLED palettes. Evidence: Robolectric only (NativePagesChan727aTest + CHAN suites, 104 tests 0 failures, cloud).
- 728a adds Timeline (same session, build 864e293 or later): p2review walk Info -> CHRONICLE -> MILESTONES -> BRANCHES (R1 x3,
  L1 back), the page name + page line beside it change after the old page has gone; Play same size and place on every page;
  MILESTONES lazywalk (140 Downs / Ups, B, reopen) - the line stays continuous between stops while scrolling; a dated and an
  undated game ("YEAR UNKNOWN" node); a removing branch armed and left; popclip.sh + popclip.py PASS; 0 launches; x1.0 + x1.3.
  Evidence: Robolectric only (NativePagesTime728aTest + TIMELINE suites, 52 cases 0 failures, cloud).
- 729a adds Hero carousel + Hero tiles (one scene; same session, build 546f5b99 or later): p2review walk Info -> THE FEATURE ->
  HALL OF FAME -> QUICK ACTIONS (R1 x3, L1 back) on both layouts, the tab strip's underline moves after the old page has gone,
  the name steps down without a jump of Play; Play same size and place on every page; HALL OF FAME lazywalk; a game with a
  logo and one without; a removing action armed and left; popclip.sh + popclip.py PASS; 0 launches; x1.0 + x1.3.
  Evidence: Robolectric only (NativePagesHero729aTest + HERO / HERO_TILES suites, 100 cases 0 failures, cloud).
- 730a adds Memory card (same session, build e59f3c6 or later): p2review walk Info -> INFORMATION -> MEMORY BLOCKS -> COMMANDS
  (R1 x3, L1 back), the yellow page name + lit block change after the old page has gone; the glass panel between the title and
  the choices on every page (QaClip: nothing under the choices row); Play same size and place on every page; MEMORY BLOCKS
  lazywalk (synthetic 120: 40 blocks, one row); a game on a long console name (wraps left of the title, x1.3); with and without
  a PS2 save icon; a removing command armed and left; popclip.sh + popclip.py PASS; 0 launches; x1.0 + x1.3.
  Evidence: Robolectric only (NativePagesMemCard730aTest + MEMCARD suites, 581 tests 0 failures incl. the survey, cloud).
- 731a adds Cardboard box (same session, build ee74963 or later): p2review walk Info -> CONTENTS -> CHECKLIST -> SERVICE (R1 x3,
  L1 back), the sheet header + "SHEET n OF m" + filled tab change after the old page has gone; the tilted sheet's stamps (Play)
  same size and place on every sheet (PlayPlace passes in Robolectric only); CHECKLIST lazywalk (synthetic 120: 32 boxes, one
  row); the first page's fact values at the right of their leaders (no longer end-aligned) at x1.0 + x1.3; Options / More
  stickers reach SERVICE; a removing line armed (VOID?) and left; popclip.sh + popclip.py PASS; 0 launches; x1.0 + x1.3.
  Evidence: Robolectric only (NativePagesBox731aTest + BOX in AchFirstRow / ShelfText / PlayPlace suites, 0 failures, cloud).
- 732a adds File select (same session, build 91247df or later): p2review walk Info -> DETAILS -> STARS -> OPTIONS (R1 x3, L1
  back), the top row's page name + round pips change after the old page has gone; the fat buttons (Play first) same size and
  place on every page (PlayPlace passes in Robolectric only); STARS lazywalk (synthetic 120: 24 little stars, one row; at x1.3 on
  the KONKR the star row gives way, the count line stays); fact slots' values whole at x1.0 + x1.3; a removing option armed
  (ERASE?) and left; popclip.sh + popclip.py PASS; 0 launches; x1.0 + x1.3; a light and a dark file colour.
  Evidence: Robolectric only (NativePagesFile732aTest + FILESELECT in AchFirstRow / ShelfText / PlayPlace / WinFocus / NoStrayPlay /
  LazyRows suites, 628 tests 0 failures, cloud).
- 733a adds Case flow (same session, build d3907c7 or later): p2review walk Info -> Details -> Extras -> Setup (R1 x3, L1 back),
  the heading (accent edge, page name, page pills) changes after the old page has gone; on a page the window keeps one size and
  Play (first of two pill slots) the same size and place; Extras lazywalk (120 set); a removing Setup pill armed and left (never
  confirmed); the pickup flip on open / B still turns the window; popclip.sh + popclip.py PASS; 0 launches; x1.0 + x1.3.
  Evidence: Robolectric only (NativePagesCase733aTest + CASEFLOW suites, 2286 tests 0 failures, cloud).
- 734b adds Orbit deck (same session, build 6e8c897 or later): p2review walk Info -> Data -> Tracks -> Commands (R1 x3, L1 back)
  with the D-pad on an arc key AND in the page; the heading (page code, page cells, READY lamp) changes after the old page has
  gone; Play keeps its place on the arc (wide) / its slot in the key row (narrow, x1.3) on every page; Tracks lazywalk (120 set);
  a removing command armed and left (never confirmed); the disc flight on open / B unchanged; the DISC line with a long console
  name (SNES) whole and clear of READY at x1.3; popclip.sh + popclip.py PASS; 0 launches; x1.0 + x1.3.
  Evidence: Robolectric only (NativePagesOrbit734bTest + ORBIT suites, PlayPlace all layouts, NoStrayPlay682j 80/80, cloud).
- 735a adds Airy home (same session, build 2e18249 or later): p2review walk Info -> About -> Stars -> Settings (R1 x3, L1 back),
  the page name + dots change after the old page has gone, the page floats up; Play same size and place on every page (on the
  KONKR the pages take the small round keys - Info keeps the big ones: check by eye that the change reads well); Stars lazywalk
  (120-set) with the bubble meter and the order pill (its words slide on one line); a removing setting armed and left; popclip.sh
  + popclip.py PASS; 0 launches; x1.0 + x1.3. Evidence: Robolectric only (NativePagesAiry735aTest + AIRY suites, PlayPlace /
  AchFirstRow / ShelfText AIRY, NoStrayPlay682j all layouts, cloud).
- 736a adds Bubble home (same session, build 445fd1f or later): p2review walk Info -> Info page -> Awards -> Settings (R1 x3, L1
  back), the page name + bubbles change after the old page has gone, the page swells in; Play (the white Start plate) same size
  and place on every page; the page panel now white like the first page (check it reads well over the game's art); Awards
  lazywalk (120-set) with the slim meter and the order key (its words slide on one line); an award opened (A) rounds into a card
  and shows how rare it is; a removing setting armed and left; popclip.sh + popclip.py PASS; 0 launches; x1.0 + x1.3. Evidence:
  Robolectric only (NativePagesBubble736aTest + BUBBLES suites, PlayPlace / AchFirstRow / ShelfText / NoStrayPlay BUBBLES, cloud).
- 737a adds Pocket camera (same session, build 5e1b096 or later): p2review walk Info -> Data -> Album -> Setup (R1 x3, L1 back);
  the page name + pixel squares change after the old page has gone; the page draws down the screen in steps with a scan line
  (none with Motion Off); Play same size and place on every page (Options too - two keys' room); the pixel font readable at
  x1.0 / x1.3 on both palettes you use; Album lazywalk (120-set) with the cell meter and the order row (words slide); an entry
  opened (A) shows the whole text + rarity; a removing setting armed and left; popclip.sh + popclip.py PASS; 0 launches.
  Evidence: Robolectric only (NativePagesCamera737aTest + CAMERA suites, PlayPlace / AchFirstRow / ShelfText / NoStrayPlay, cloud).

### HW-27 P2 682aq (v0674-native-pages) - lazy rows, Coverflow x1.3, Arcade - DONE 24 Nov night (Level 1, QA app, KONKR BW0306N230000845 + RP Classic MC94516AQF091001112, USB)
- lazywalk (120 achievements, 140 quick Downs / Ups, B, reopen, early close): 682aq10 reaches Back on KONKR (Cinema, Resume, Bar)
  and RP Classic (Cinema, Resume); Record crate stays eager (D389 open for it). 0 crashes, restore diff 0.
- Coverflow Details x1.3 (cover mode, kcw13 / rcw13): facts reachable with one Up, story end above Play with one Down (D392 fixed).
- Arcade perf (fair 30-game category): KONKR home navigation slow frames 24.5 -> 0.6 %; RP Classic 12.2 -> 12.6 % (GPU-bound, open).
- D390: the first cover walk looped ~30 min (shell variable) - QA app only, stopped by hand, over the 15-min limit; fixed.
- NOT TESTED: x1.3 lazywalk; layouts other than Cinema / Crate / Resume / Bar on hardware; D391 (home awake behind a window).

### HW-26 Volt second screen text shown whole (690a, rule 11) - OPEN (Thor; batch with HW-19 when the owner says the Thor is back)
- Device(s): AYN Thor (3140972f), USB, lower screen = second display. Level L1 (QA app only). Thor NOT with the owner since 17 Nov -
  never poll / wake; only when he says it is back. Commit: dev line 690a or later. ~3 min, x1.0 + x1.3.
- Why not emulator: the Volt companion draws on the Presentation of a second physical display; the emulator trial has one display.
- Tests: second screen style Volt, a game with a long name and achievements with long titles / descriptions: the clock beside the
  page tabs whole; on the achievements board the game name (2 lines, then shrink, then slide), the console line, the filter labels
  with their counts, the list's titles / descriptions and the picked achievement's title (3 lines) never end in an ellipsis; the
  game card's console label whole; nothing jumps when the focus moves. Screenshot of the lower screen at both scales.
- Evidence so far: unit only (SecondScreenVoltFullText690aTest, static TRUNCATE 158 -> 150); not compiled in the cloud (Maven 429) -
  CI is the first compile.
- Also (708a, rule 11, dev line 708a or later, ~1 min, x1.0 + x1.3): second screen folder games page (open a console folder on the
  lower screen) in each look - List (name 2 lines, then shrink, then slide), Tiles / Covers / Wide (name under the tile shrinks,
  then slides; grid rows stay even) and a game without a cover (its name centred in the tile, up to 4 lines) - never an
  ellipsis; D-pad + touch still pick a game. Screenshots. Unit evidence only (SecondGamesPageFullText708aTest, cloud compiled +
  PASS; static TRUNCATE 84 -> 81).
- Result: (filled in by the hardware session)

### HW-17 v0.6.73 candidate focused re-check (status bar, profile picture, Storage; merged dev line) - DONE (b76e3ef); 678k NOT TESTED
- Device(s): RP Classic (MC94516AQF091001112) + RP Nova (d3641c41), USB. Level L1 (QA app only).
- Build: the merged dev line (af1867f or later; facet 677l merged 17 Nov, whole words NOT included): push a commit with
  "[qa-apk]" for the signed QA APK, add its ledger entry (tools/qa/builds.json) first. Known pre-existing word breaks (D346,
  whole words deferred to v0.6.74 by the owner) go in a homecut --allow file with that reason - never silently.
- Why not emulator: real fonts / densities and the owner's device sizes are the point.
- Tests: status677.sh <tag> "CLASSIC GRID CUBE HUB STORAGE TILES" "off font=1.3,density=1.15" -> statuscheck.py + homecut.py
  (expect PASS); pop529.sh run <tag> font=1.3,density=1.15 "ARTLIB GREENCORE LIBRARY LIVE TERMINAL TWIN_ICONS ARCADE DISCS" OWN
  -> popclip.py (the 6 readability FAILs of j677x13 must be gone), then `pop529.sh restore <tag>` AFTER popclip ends (pop529
  run starts popclip in the background - a chained restore resets the run; wait on popclip's pid, not on pgrep -f).
- Added 678k (H8): on the RP Classic at Larger text (font=1.3), Console Hub with a long name (e.g. 'Advance Wars (Rev 1) (No
  Glitch)'): the focused Hub tile title and the Continue card name wrap / shrink / slide - no '…'; the Achievements card
  ticker rows whole while sliding (screenshot + homecut on HUB; 678k or later build).
- Result: DONE 17 Nov (live session) - 17 Nov 05:20-05:45 UTC, QA build qa-073rc-b76e3ef-ci (CI 44021029412, Verify 310 GREEN on b76e3ef). RP Classic crc10 (x1.0 + x1.3, Classic / Grid / Cube / Hub / Storage / Tiles x points / notes / trophy / pads): status bar + profile picture 54/54, home text 36/36 PASS; badges NOT TESTED (Classic QA library has 2 apps, no fixture - H6). RP Nova src10 + src10b: status 54/54 + 20/20, home text 36/36 + 10/10 PASS; badges: Cube cover-flow side covers (stacked behind the focused one) + a Tiles tile scrolled half off-screen FAIL the strict checker - the same findings on every run since 677c, before these fixes (not a regression; listed for review). RP Nova prc13 x1.3 popup gate on Art library, Greencore, Library, Live, Terminal, Twin icons, Arcade, Discs, Storage, Hub: 10/10 PASS (the 6 readability FAILs of j677x13 gone), status 20/20, badges 2/2; home text 9/10 - Art library 'PlayStation 2' broken inside the word = known, whole words deferred to v0.6.74 by the owner. Nav bar (navbar676, RP Classic x1.0): Retroid pill hidden in 13/13 states; Android's NavigationBar0 window reports visible in gesture mode but draws nothing in the screenshots - the strict PASS rule says FAIL: owner / rule decision (HW-8). NOT TESTED on hardware: 678k (H8 console-home names slide - newer than this build), x1.0 popup gate, KONKR.

### HW-25 Library rescan: deleted last game / empty console / SD card out (681r) - DONE 18 Nov except the SD card (KONKR kk682d + RP Classic rc682b: 18/18 PASS each; build qa-682b-3ec7e45-ci). SD card physically out: NOT TESTED - owner 18 Nov 12:12 does not want to pull the card now; recorded NOT TESTED, never PASS (when wanted: RP Classic, sh rescan681r.sh run TAG 453E-4658 - the owner pulls / reinserts when the log says)
- Branch `claude/v0674-rescan-fix` (d082e7f or later), its CI QA APK; L1 QA app only, synthetic stand-ins only (never the owner's
  ROMs or saves). Emulator: PASS 15/15 (run 44190749178).
- Tests: `sh rescan681r.sh run <tag>` then `restore <tag>` (tools/qa/device) - every case PASS. Plus, on a device with an SD card:
  copy a stand-in to a ROM folder on the card in the QA app, rescan, take the card out (or unmount it), rescan - the card's
  stand-in stays listed ("Kept N from folders that couldn't be read"); card back, rescan - still there; delete it, rescan - gone.
- Then (owner, LEVEL 2 read-only, optional): the owner's real case on their device as they found it - which ROM folder layout
  the Xbox 360 game had (file / folder, own ROM folder or a console subfolder), to confirm it is the case covered.
- Also (P2 batch 1, same session, ~5 min): p2review.sh on CRATE CLASSIC HUB RESUME TV at x1.0 + x1.3 (screens + recordings).

### HW-25 Achievements progress counts each new unlock as it locks in (681y, H12) - OPEN (same KONKR session as HW-24, ~3 min)
- Device: KONKR (or RP Nova), x1.0; L1 QA app only. Build: the CI QA APK of the 681y commit or later.
- Why not emulator: the trial has no RetroAchievements account (no real set, no new unlocks); the QA hook below needs a real set.
- Tests: a game with an achievement set, `--ei voltcap_test_achnew 2` (678o hook) -> open its Achievements page (Power cells view):
  on open the battery / percentage leave out the 2 new ones; each fills up as its badge locks in on the stage; after the stage the
  percentage equals the set's real progress and the summary line agrees. Again with Settings > Motion = Off: the stage shows both
  badges settled at once, the summary line and the full percentage at once (no waiting). Trophy list view: Earned / Progress / tier
  counts follow the same way. A game with nothing new: no fill on open. Screen recording of the first case. Unit evidence only
  (AchProgressCount681yTest, cloud Robolectric).

### HW-24 Channel progress pill clear of the name plate (680a, D356) - OPEN (next KONKR session)
- Device: KONKR (where k679p13 found it), x1.0 + x1.3; L1 QA app only. Build: the CI QA APK of the 680a commit or later.
- Tests: pop529.sh run <tag> font=1.3,density=1.15 "CHAN" OWN (+ off) -> popclip.py + badgecheck (no "PROGRESS covers text"),
  and the screenshots: the pill above the plate, nothing cut, the plate still one line and sliding (D350).
- Also (680b, D357, no extra device time): run badgecheck.py (680b or later) over the same session's CUBE x1.0 and Tiles / Classic
  dumps - the cover-flow side covers and the half-off carousel tile must no longer FAIL; any remaining FAIL is a real collision.
- Also (681cr, P2 branch `claude/v0674-native-pages` 64e5955 or later, its CI QA APK; same KONKR session, ~2 min): Record crate
  at x1.3, open TRACKLIST (R1 R1): the first track whole at the page's top (title in two lines, the dotted leader after it,
  its words on one sliding line until A) - p2review.sh / popclip on CRATE; a cut first row = FAIL. Unit evidence only so far.
  681s (0c7ad56 or later): the same TRACKLIST shows at least two whole tracks at x1.3 (three at x1.0) under the compact sleeve
  header ("TRACKLIST · n / m", the name on one sliding line), the ORDER stamp on the SIDE line and A on it steps the order;
  Info's header unchanged. Unit evidence only (CrateInsertRoom681sRoboTest).
- Also (681v, P2 branch `claude/v0674-native-pages` 00b9c1d or later, its CI QA APK; same KONKR session, ~2 min): TV room at x1.0
  and x1.3, a game with achievements, the window on the set's screen: Info (P100) / TROPHIES (P200) / OPTIONS (P300) each show a
  one-line masthead (P-number, page name block, the game's name sliding), Fastext keys PICTURE / INFO / TROPHIES / OPTIONS on one
  line, P200 two whole rows at x1.0 and one at x1.3, D-pad Down walks the rows (not straight to the buttons), the 120-set scrolls
  far. p2review.sh run <tag> <scale> "TV" -> popclip.py. Unit evidence only (TvTeletextRoom681vRoboTest).
  681x (049dd75 or later, same check, no extra time): each Fastext key's word stands whole in its key (no sliding) - full
  words where they fit, PIC / INFO / TROPHIES / OPTS at x1.3; a tap on each key opens its page. Unit evidence only
  (TvFastextKeys681xRoboTest). p2review.sh's Play guard now uses fixed strings (toybox): the log must show no 'LAUNCH blocked'.
- Also (681e, v0.6.74 item 6, no extra device time): run statuscheck.py (681e or later) over every dump of this session - the
  new "avatar place" lines (picture on screen, the bar's inside the bar, no text over it; corner as evidence). Any FAIL = a
  real misplaced / covered profile picture (report the layout + corner); confirm the reported corners are the intended ones.
- Also (681p, D354, dev line 681p or later, ~2 min): Pocket Quest (POCKET) at x1.3, open a game's window with the disc pulled out
  beside it: "Play game" and "Status" on ONE line each, two whole keys between the arrows, More reached by D-pad right and by a
  swipe; the key words readable (10-12 sp). pop529 POCKET OWN -> popclip.py. Also look at the home status bar's category pill:
  "PlayStation 2" slides inside a narrow pill (rule 11) - say whether that is fine or the pill should get more room.
- Also (681t, H16, dev line 681t or later, ~1 min): Night (NIGHT) at x1.3, a game with achievements: the stats panel's
  ACHIEVED / RELEASED / PLAY TIME labels whole (never "ACHIEVE / D"), values beside them readable, the achievements bar under
  the values; also x1.0 looks as before. pop529 NIGHT OWN -> popclip.py. Unit evidence only (NightStatLabels681tTest, CI).
- Also (681w, H16, dev line 681w or later, ~1 min): Icon bar (BAR) at x1.3, a game with achievements: the dock keys' names
  under the round icons whole - "Achievements" / "Performance" never broken inside the word, a two-word name only between its
  words; the keys equal width; where they no longer all fit, the row shows whole keys with the arrows and D-pad right / a swipe
  reaches the rest; the focused status key's whole text on its line under the dock. x1.0 as before. pop529 BAR OWN -> popclip.py.
  Unit evidence only (BarDockLabels681wTest).
- Also (682t, H16, dev line 682t or later, ~2 min, KONKR x1.3): Night (NIGHT), a game on PlayStation 2: the SYSTEM value
  "PlayStation 2" whole - under its label across the panel when it does not fit beside it (never "PlayStatio / n 2"); the other
  values still beside their labels; x1.0 as before. Pocket ribbon (POCKET_RIBBON), same game, first page and More: the CONSOLE
  fact "PlayStation 2" whole (smaller if it must, never broken inside the word). pop529 NIGHT OWN / POCKET_RIBBON OWN ->
  popclip.py. Unit evidence only (WindowWordsSurvey682Test x1.3, NightValueStack682tTest).
- Also (684a, H16, dev line 684a or later, ~1 min, KONKR x1.0 + x1.3): Timeline (TIMELINE), a PlayStation 2 game WITHOUT
  cover art: the window's grey plate shows the console initials only (no "PlayStati / on" break), the entry line beside it names
  "PlayStation 2" whole; a game with a short console name (e.g. "Wii") still shows its caption under the initials. pop529
  TIMELINE OWN -> popclip.py. Unit evidence only (WindowWordsSurvey682Test TIMELINE, NoArtCaption684Test).
- Also (685a, H16, dev line 685a or later, ~1 min, KONKR x1.0 + x1.3): Storage boxes (STORAGE) at x1.3, a game played before:
  the fact tab "LAST PLAYED" whole (two lines in its tab if it must, never just "LAST"); the other tabs one line as before.
  Card binder (CARDS): open a game - the card still turns over (front away, back in) as before, no flicker or blank frame at
  the half-turn; B - it turns back. pop529 STORAGE OWN / CARDS OWN -> popclip.py. Unit evidence only (WindowWordsSurvey682Test
  NEXT_685, CardFront685Test).
- Also (723a, owner Task 2 R1 Focus position, dev line 723a or later, ~3 min, KONKR x1.0 + x1.3; RP Classic if connected;
  LEVEL 1 QA app only, settings restored): Voltcap row, carousel off - R1 shows "Focus Left"; A -> "Focus Middle" and the game
  you're on moves to the middle and stays there while ◀ ▶ browse (first / last game, Endless on and off, a category with 1 and
  2 games); A again -> Left. Hold the tile -> the list (Left / Middle, current marked), pick with the D-pad and by touch. Tile
  home: A steps Edges -> Left -> Middle -> Right -> Edges, each held while browsing, category name aligned as Settings does.
  Carousel on (either layout) and Voltcap row "Covers · first one wide": tile greyed, its press explains why. Another layout
  (e.g. Grid, Hero): no Focus tile. R1 order unchanged (the new tile after the saved ones). Unit evidence only
  (FocusPosTile723aTest); matrix docs/claude/R1_FOCUS_POSITION_723.md.
- Also (724a, owner Task 3 Recomps, dev line 724a or later, ~2 min, KONKR x1.0 + x1.3; LEVEL 1 QA app only, marks taken off
  afterwards): no Recomps category before marking. Settings > Apps in your library: "Recomp?" chip under an app's name, reach it
  with the D-pad and by touch -> "Recomp ✓"; back home: L2 / R2 and the category tiles now show "Recomps" with that app; it is
  also in All, not in Other apps; launching it works as before. Take the mark off while in Recomps -> back on All, the category
  gone. Unit evidence only (Recomps724aTest).
- Also (725a, owner Task 4 START > Categories, dev line 725a or later, ~3 min, KONKR x1.0 + x1.3; LEVEL 1 QA app only, choices
  taken off afterwards): START on an installed app / Android game -> "Categories…" row (under Details) naming where it is
  listed. Open it: first focus on "Automatic", every row reachable by D-pad and touch, nothing cut at x1.3 (popclip). Tick
  Recomps + Nintendo 64 (or "More consoles…" -> GameCube) and set "Android games": back home the app is in Recomps, Android games
  and the console (a console with no ROMs appears in L2 / R2 only now), once in All, launching it starts the same app (saves,
  play time, art unchanged). Restart the QA app: kept. Untick the console while it is open -> back on All, the console gone.
  "Collections…" opens the collection picker. Unit evidence only (AppCategories725aTest).
- Result:

### HW-23 KONKR evening session 17 Nov (live, owner present) - DONE
- Device: KONKR GT78 (BW0306N230000845, USB), L1 QA app only; production Voltcap 0.6.73 untouched (home again at the end). Odin 2
  Portal (b92036fb) connected but NOT used - the owner's own app (WWE 13 port) was in front.
- Builds: qa-679c-e4669b9-ci (dev line e4669b9, CI 44153799854), qa-679e (branch claude/cube-dock-fit-679d e7cbfd2, CI 44158900949).
- pop529 k679p13 x1.3 SPOT HUB CHAN STORAGE CUBE CLASSIC: popup gate 6/6 PASS; status 10/10; badges 4/5 (Channel D356); home text
  5/6 -> Cube 'L2 · R2' cut (D355).
- status677 k679s x1.0 + x1.3 (6 layouts x points / notes / trophy / pads, narrowest handheld): status bar + profile picture 72/72
  PASS; home text 33/36 (Cube D355); badges: carousel / cover-flow findings (D357, checker). k679ss density 1.4 HUB + CUBE: status
  12/12, home text 6/6.
- 679e fix verified: k679d CUBE x1.0 + x1.3 status 12/12, home text 6/6 (before / after sheet konkr_cube_dock_679d.png).
- NOT TESTED: Terminal 679d (HW-22, newer than the tested build), touch, x1.0 popup gate.

### HW-22 Terminal home: path, header and preview shown whole (679d, rule 11 / D353) - OPEN (batch with other KONKR requests)
- Device(s): KONKR (GT78, BW0306N230000845), USB, only if already connected. Level L1 (QA app only).
- Build: the 679d commit on claude/qa-0672-repair (or newer); "[qa-apk]" QA build + tools/qa/builds.json ledger entry first.
- Why not emulator: the emulator found it (e679r13) but evidence must come from the handheld's own font metrics / width;
  the cloud test (TerminalFullText679dTest) only proves no "…" path is left in the source.
- Tests: TERMINAL at off and font=1.3,density=1.15 with a long category name (e.g. PlayStation 2): screenshot + homecut.py -
  title-bar path and "Directory of ..." header whole or sliding (no "…"), preview title / SYS / TIME lines whole or sliding,
  C:\ root and SYSTEMS directories: the focused line slides its whole note / name; change category with L2 / R2 - the shell
  stays (CATSHELL rule 14); vqa.sh onepress on the listing (first / last item, both directions).
- Result:

### HW-21 Console hub category title clear of the profile row at density 1.4 (679c, H7 / D347) - OPEN (batch with HW-18)
- Device(s): RP Classic (where c677h / c677js saw it), USB. Level L1 (QA app only).
- Build: the 679c commit on claude/qa-0672-repair (or newer); "[qa-apk]" QA build + tools/qa/builds.json ledger entry first.
- Why not emulator: the overlap is about the Classic's own screen size in dp at the 1.4 stress density; the cloud test only
  proves the placement rule (HubCategoryClear679cTest).
- Tests: status677.sh <tag> "HUB RESUME" "off font=1.3,density=1.15 density=1.4" -> statuscheck.py + homecut.py: at 1.4 the
  "All games" title sits below the profile name / score (no overlap), at x1.0 / x1.3 it is where it was (no move); change
  category with L2 / R2 at 1.4 - the title stays put (CATSHELL rule 14); screenshot each.
- Result:

- KONKR 17 Nov evening (qa-679c-e4669b9-ci, status677 k679ss HUB + CUBE at font=1.3,density=1.4): status 12/12, home text 6/6 PASS - the category title clear of the profile row on the KONKR. RP Classic (the device it was found on) still NOT TESTED.
### HW-18 RP Classic full 53-layout popup gate (after the fixture fix, H6) - OPEN (tooling fixed 678l, needs the Classic)
- Device(s): RP Classic. Level L1.
- Why: j677c10 stopped at setup "no game matches 'Shadow of the Colossus'" / "'ActRaiser'": the fixture library does not load
  there (files/roms empty after setup). Cloud first: find why voltcap_test_fixture_roms / names=real don't take on the Classic.
- 678l (cloud): cause found - pop529 setup never made the stand-in files (they came from an earlier fixroms500 / qasetup521 run;
  the Classic's QA app was freshly installed). Setup now runs tools/qa/device/standins.sh (run-as fallback) and logs
  "standins=N via=..."; first check in the run log that N >= 30, then the game hook matches. Push standins.sh with pop529.sh.
  Commit: the 678l commit on claude/qa-0672-repair (or newer). Why not the emulator: the trial already makes its own library;
  the Classic's own storage / run-as behaviour is what failed.
- Tests: pop529.sh run <tag> off <53 layouts> OWN, then x1.3; popclip.py, badgecheck, homecut, statuscheck.
- Result:

### HW-20 Achievements page opens on the new unlocks (678m, H12) - DONE on the KONKR (x1.0 + x1.3); touch scroll / reduced motion NOT TESTED
- Device(s): KONKR or RP Nova (batch with HW-12..HW-16 in the same session). Level L1 (QA app only).
- Build: the 678m commit on claude/qa-0672-repair (or newer).
- Why not emulator: the trial has no RetroAchievements account, so no real new unlocks; the page's real height at the
  owner's font scale is the point.
- Tests: a game with fresh unlocks (QA app: play one unlock, or a test-hook pending set) -> open its Achievements page at off
  and font=1.3,density=1.15: NEW UNLOCKS stage in view on open with the focus ring on it, ▼ reaches the controls then the
  first achievement one press each, ▲ back; a game with nothing new still opens on its first achievement (whole); touch
  scroll works; reduced motion unchanged. Ask the owner which screen "Recently Unlocked" meant if it is not this one.
- Result: DONE 17 Nov (live session, KONKR GT78 USB, QA build qa-0.6.73-678o-ci) - text678.sh k678t, new QA hook
  `--ei voltcap_test_achnew 2` (678o): nothing new -> opens on its first achievement (whole), ▼ / ▲ one press each; two marked
  new -> opens on NEW UNLOCKS with the focus ring on the stage, ▼ "View ▾", ▼ the first achievement, ▲ back - one press each,
  x1.0 and x1.3; page text whole (homecut). NOT TESTED: touch scroll, reduced motion, a real RetroAchievements unlock.

### HW-19 AYN Thor dual-screen: Android Home on the lower screen after a top-screen launch (H13) - OPEN (cloud reading done 679b)
- Device(s): AYN Thor (3140972f), USB. Thor NOT with the owner since 17 Nov - never poll / wake; only when he says it is back.
- Level: L2 PRODUCTION READ-ONLY. The isolated QA app blocks every launch (TestHooks.interceptLaunch, update 500), so it
  cannot reproduce this; the OWNER starts the game / app in his real Voltcap himself, Claude only reads dumpsys (no input).
- Why not emulator: two physical displays + the Thor's own secondary home; the emulator trial has one display.
- Tests (docs/claude/THOR_DUAL_SCREEN.md "What HW-19 must capture"): (a) ROM game, layout "Game + info"; (b) Android game
  (Dead Cells) from the app drawer / dock; (c) the same from its library entry with Play; (d) during (a) touch the lower screen,
  then Home. After each: dumpsys activity "Display #1" resumed activity + dumpsys window focus. Expected per the reading:
  (a) companion, (b) secondary home, (c) companion, (d) secondary home below while the game keeps running.
- Result:

### HW-16 Category screen text shown whole (678j, rule 11) - IN PROGRESS (KONKR grid look done; Slides / Stage NOT TESTED)
- Device(s): RP Nova or KONKR (batch with HW-15 / HW-14 / HW-13 / HW-12 in the same session). Level L1 (QA app only).
- Build: commit of update 678j on claude/qa-0672-repair; QA build = assembleDebug of that commit.
- Why not emulator: the trial does not open the category screen's Slides / Stage / card looks or the slide editor; long
  system names need a real screen width and font scale.
- Tests: at off and font=1.3,density=1.15 in the QA app, a category with a long name and no logo picture (e.g. a custom
  category) and one with a logo: Slides (logo box name two lines, name line, last-played line on), Stage (centred name in
  the logo box, name line in the editor), card look (name two lines + game count): whole (wrap / shrink / slide), boxes keep
  their sizes, CATSHELL unchanged (L2 / R2 swaps without a reload); touch + controller.
- Result: 17 Nov (KONKR, qa-0.6.73-678o-ci, text678.sh k678t): the category screen's default grid look, x1.0 PASS (long names
  wrap or slide: "Nintendo Entertainment System", "Sega Mega Drive / Genesis"); x1.3 FAIL only on 'PlayStation' / 'PlayStation 2'
  / '3' / 'Portable' broken inside the word - the known D346 word breaks the owner deferred to v0.6.74 (whole words). NOT TESTED:
  Slides / Stage / card looks, the slide editor, a custom category without a logo, touch.

### HW-15 Save-sync prompts text shown whole (678i, rule 11) - OPEN
- Device(s): RP Nova or KONKR (batch with HW-14 / HW-13 / HW-12 in the same session). Level L1 (QA app only).
- Build: commit of update 678i on claude/qa-0672-repair; QA build = assembleDebug of that commit.
- Why not emulator: the trial has no Google Drive sign-in, so the sync toast, Drive card, sync log and conflict list never show.
- Tests: at off and font=1.3,density=1.15 in the QA app: the sync toast (title + detail, two lines each), the Drive card
  status line, a game row with a long name, the sync log (time column + two-line entries, a "›" entry still opens) and the
  conflict list's long game names: whole (wrap / shrink / slide), rows keep their heights; touch + controller.
- Result: NOT TESTED 17 Nov - the QA app on the KONKR has no Google Drive sign-in (the QA OAuth client is an owner action).

### HW-14 Settings text shown whole (678h, rule 11) - IN PROGRESS (KONKR: search + 3 pages PASS; sub-pages NOT TESTED)
- Device(s): RP Nova or KONKR (batch with HW-13 / HW-12 / HW-1 / HW-9 in the same session). Level L1 (QA app only).
- Build: commit of update 678h on claude/qa-0672-repair; QA build = assembleDebug of that commit.
- Why not emulator: the trial does not open Settings pages; long titles / theme names need a real screen width and font scale.
- Tests: at off and font=1.3,density=1.15 in the QA app: Settings search (type "theme") - each result label + section path
  whole; a group page title + a sub-page title whole; Themes: theme captions (two lines, grid rows even) + the strip line
  under the picker whole; Scene picker tab captions whole; Artwork > ScreenScraper look-up lines whole; Music > custom track
  name whole; RetroAchievements cards (game name, console line, latest unlock, stat tiles) whole. Rows keep their heights
  while D-pad focus moves; touch + controller.
- Result: 17 Nov (KONKR, qa-0.6.73-678o-ci, text678.sh k678t, homecut on the page dumps): Settings search "theme" (labels +
  section paths), the Theme page (and two ▼), Achievements and Categories pages - every text whole at x1.0 and x1.3. NOT TESTED:
  theme captions grid, Scene picker tabs, ScreenScraper look-up lines, Music custom track, RetroAchievements cards (no account).

### HW-13 Save Vault text shown whole (678g, rule 11) - IN PROGRESS (KONKR fixture view PASS; "Not connected" NOT TESTED)
- Device(s): RP Nova or KONKR (batch with HW-12 / HW-1 / HW-9 in the same session). Level L1 (QA app only, no real saves).
- Build: commit of update 678g on claude/qa-0672-repair; QA build = assembleDebug of that commit.
- Why not emulator: the trial does not open the Save Vault; a long name / place needs a real screen width and font scale.
- Tests: open Saves (QA app, Drive not connected and with the vault fixture) at off and font=1.3,density=1.15: header name +
  count line whole, "Not connected" + problem text whole, save rows one line each (a long one slides), info bar name /
  state / date whole, Browse saves label whole; rows keep their heights (no jump while moving D-pad focus); touch + controller.
- Result: 17 Nov (KONKR, qa-0.6.73-678o-ci, text678.sh k678t, vault fixture, display only): header + count line, the button
  row, console heading, tiles and the info bar (name / console · saves · place / date) whole at x1.0 and x1.3, two ▼ steps
  (homecut PASS). NOT TESTED: the "Not connected" / problem text, Browse saves, touch.

### HW-12 Banner game window text shown whole (678e, rule 11) - DONE on the KONKR (x1.0 + x1.3)
- Device(s): RP Nova or KONKR (any USB handheld; batch with HW-1 / HW-9 in the same session). Level L1 (QA app only).
- Build: commit of update 678e on claude/qa-0672-repair; QA build = assembleDebug of that commit.
- Why not emulator: R26 needs the popup gate on hardware; the emulator trial runs the layouts' OWN style only, not Banner.
- Tests: popclip.sh <tag> "SHELF COVERDETAILS" "BANNER" "<game with a long name + story>" at off and font=1.3,density=1.15,
  then popclip.py (all six grades PASS); look at the Story page (More pill on a cut story), Options rows, achievement rows
  (opened = whole description) and the saves chip. Popup style restored by the snapshot.
- Result: 17 Nov (KONKR, live session). Before (qa-0.6.73-678n-ci, kb678n10 x1.0): COVERDETAILS geometry FAIL - the saves chip
  ("Save sync is not set up", 18 dp) and the L1 / R1 hints (17 dp) are tap targets under 24 dp; SHELF INFRA (the fresh QA app's
  first-run prompts took the first A). Fixed in 678o (24 dp touch boxes, drawn pills unchanged, BannerTouch678oTest). After
  (qa-0.6.73-678o-ci): kb678o10 x1.0 + kb678o13 x1.3, SHELF + COVERDETAILS: open / geometry / visual / focus / buttons / settle /
  readability / glyphs / rapid PASS; hierarchy FAIL on both - the window's corner X ("Close") reads before Play. Owner / rule
  decision (owner_actions): treat a corner close as chrome (like the Back hint) or move it.
- Owner 17 Nov (D351): keep the X in the corner - a separate corner-close control, not part of the action order; Play stays the
  first action and the first focus. popclip.py (678p) leaves a Back / Close off Play's row out of the first-action order; the
  kb678o10 / kb678o13 runs re-graded: SHELF + COVERDETAILS Banner all categories PASS at both scales.

### HW-10 v0.6.74 "Open on screen" (branch claude/v0674-screen-shortcut) - THOR DONE 16 Nov
- Thor 16 Nov (L1, owner's HARDWARE TESTING AUTHORIZATION; supervised launch window 676n - qa_allow_app_launch, Clock only,
  10 min, plain-app path only): drawer touch long-press -> "Open on screen"; Clock Bottom -> really opened on display 4 (bottom),
  Top -> display 0 (top); picker shows the saved choice; restored to Follow default. Android game (Call of Duty): game picker
  (Top / Bottom / Follow default + Screen layouts...) shown, nothing changed, nothing launched. Clock closed (its notification
  prompt left unanswered), window file removed, QA app stopped. Still to do: single-screen regression on the Nova, the
  second-screen dock menu once built.
- How to open the drawer in the QA app: Cube home (voltcap_test_layout CUBE) ▼ ▼ A on "Apps", or Tile home's bar.


### HW-11 Cube facet status bar (branch claude/cube-statusbar-facet) - APPROVED (owner, option C); Thor DONE, Nova running
- Thor 16 Nov x1.0 + x1.3 (676q, CI 2146018): facet with the controller icon (QA app hides the built-in pad by default -
  "Hide the handheld's own controls"; production shows it), hints whole and on screen, cover flow kept (D338). Nova full gates
  r676x13 / r676x10 on the candidate 676r running.

## Hardware session 16 Nov (running): RP Nova d3641c41 via the owner's work laptop (msi, C:\VoltcapQA bundle, QA build
8bd5e4a cloud debug key 4d133dde; old QA app 0.6.68 removed with the owner's OK). Nova = 4:3, 1280x960 @ 320 dpi (640x480 dp).
Results (build 8bd5e4a, QA app): x1.0 n675c **53/53 PASS**; x1.3 n675d **49/53** - FAIL Record crate (More: facts dropped,
empty band), Pocket ribbon (More: labels CONSOL / TROPHI cut), Storage boxes (Details: a title laid out 1 px high), Cube (first A).
All restored (snapshot diff=0, fatal=0). Pickup recordings pkn675 (x1.0, 14 layouts: Shelf, Case stacks, Storage, Cube, TV, Crate, Card binder, Console stack, Box,
Rental shop, Case flow, Disc station, Cover details, Memory card): window opened every time, fatal=0, frames.py (cloud ffmpeg 6;
the laptop VM's ffmpeg 4.4 gives INFRA) **14/14 PASS - no flash, no black frame**.
Frame pacing perf514 (gfxinfo, display 60 Hz mode): Game shelf steps 90th 10 ms / 99th 42 ms, janky 1.7 %; burst 99th 61 ms, 2.8 %;
window open / close 99th 13 ms, 0.6 %. Disc station 10 / 42 ms 1.4 %; burst 48 ms 3.3 %; window 24 ms 0.7 %. Tile home 13 / 69 ms
2.7 %; burst 16 / 57 ms 5.0 %; window 25 ms 1.2 %. No baseline on this device; single hitches (42-69 ms) while stepping - watch.
Not tested on the Nova: sound effects (no automated check - needs the owner's ear), the Record crate checklist (HW-2), the
category-switch recording (catswitch), one-press logs (focus / rapid / shoulder steps of the popup gate passed). Odin 2 Portal (home PC): QA app 8bd5e4a installed, gate started then stopped at the owner's
request (restored, fatal=0) - can run when the home PC is used again.

## Next hardware session (batched) - 16 Nov (owner: KONKR + other handhelds; waits for "devices ready")
1. Identify every USB handheld (serial, model, Android, screen, dpi; USB not wireless - CLAUDE.md device rule). QA app only.
2. One QA build of the newest dev commit on each; register it in tools/qa/builds.json. Push the newest tools/qa/device scripts
   too (675o: popclip.sh / shelfpages529.sh read dumps in plain ASCII - the emulator's grep missed keys in dumps holding "›").
3. Full popup gates x1.0 + x1.3 on each device (two at a time, ~50 min each): closes HW-1 for those devices, HW-5, HW-6.
4. Record crate checklist (HW-2) and Cube first press (HW-4) on the KONKR; controller / onepress / animation checks where
   the device differs (screen size, controller).
4b. HW-7 (5 min, KONKR): Spotify screen + player (675l) and notifications panel (675m) long names - look only, no account change.
5. Restore every device (snapshot diff=0, fatal=0), push results, update releases.json gates.
Flip 2 (unavailable): its 15 Nov gates stay HISTORICAL evidence for v0.6.73 (owner 16 Nov); its own session (popup gates on
the newest build + the charging reproduction, HW-3) waits until it is back.

### HW-8 Hide navigation bar on other handhelds (676b) - OPEN
- KONKR, RP Classic (Retroid, should match the Nova), AYN Thor / Odin (own hide_nav_bar 0 and 1 - never changed by us), any stock-Android
  phone with 3-button nav. QA app only: `sh /data/local/tmp/navbar676.sh <tag>` (read-only; the swipe step only with SWIPE=1 and
  never where the owner's Voltcap is the home app). PASS = SecondaryHomeHandle0 / NavigationBar0 not visible in every Voltcap state
  with Hide nav bar on; with the test hook off, the bar follows the device's own rules.
- Nova 16 Nov: before (675r) pill in 12/12 Voltcap states; after (676) 0/14. Pending on the next build: re-run the same.
- KONKR 17 Nov (qa-0.6.73-678o2-ci, navbar676 k678nav, gesture navigation): NavigationBar0 / SecondaryHomeHandle0 not visible in
  12 / 12 Voltcap states (startup, popup open / moved / closed, R1, START, L2 / R2, back from another app, setting on again); the
  setting off (test hook) asks for the bar and gesture mode draws none (device rule). Swipe NOT TESTED (the owner's Voltcap is the
  home app). KONKR PASS.
- Thor 16 Nov (676n): 13/13 steps no pill (NavigationBar0 / SecondaryHomeHandle0 hidden); swipe NOT TESTED (owner's Voltcap is
  home). Thor's own hide_nav_bar=1 keeps the bar hidden with the app's setting off too (device rule, not ours). The script's
  guard first aborted on the Thor (D341, DroidDeck's window on another display) - fixed 676o.

### HW-9 Badge lane on small screens (676a) - IN PROGRESS (Nova PASS; KONKR run 17 Nov - one spacing finding; RP Classic open)
- KONKR, RP Classic, Nova at x1.0 + x1.3: pop529 "CUBE GRID TILES CLASSIC SHELF STACKS" then `badgecheck.py <run>/qa_clip` (R27).
- Nova x1.3 676: collisions 25/25 PASS; Grid chip text was clipped inside its pill (pre-existing) - fixed in 676c, text check needs
  the 676c build's dumps (needW / boxW).
- KONKR 17 Nov (qa-0.6.73-678n-ci, all 53 layouts' dumps of k678n10): strict badgecheck 233 / 282 PASS. The 49 FAILs: scaled /
  turned / scrolled tiles the checker reads as cut (Cube side covers, Consoles' turned side card, Hero tiles / Dashboard scaled
  tiles, Panels' tile scrolled off the edge - the kind listed for review on the Nova) and two real ones: Twin icons - console
  and progress badges 4 dp apart on the small tile at x1.0 and 4.7 dp on the big card at x1.3 (kc678o13; rule 6 dp, D352);
  Magazine - the console badge over '2 games' in one transient step. Channel x1.3 (the count under a two-line name) fixed in
  678o (D350) and PASS on the KONKR (kc678o13).

### HW-7 RP Nova x1.3 re-test of the 4 Nova FAILs - DONE (n675r13 + n676x13: all four PASS; x1.0 n675r10 4/4 PASS)
- Device: RP Nova d3641c41 (via the owner's laptop) or any 4:3 / 640x480 dp handheld. After the fixes in HANDOFF "FIRST".
- Tests: pop529.sh run <tag> font=1.3,density=1.15 "CRATE POCKET_RIBBON STORAGE CUBE" OWN, then popclip.py.

### HW-1 Full popup gates on 674n (x1.0 + x1.3, both handhelds) - KONKR DONE 17 Nov (52 / 53 + 53 / 53 after the Channel fix); Flip 2 unavailable
- Device(s): Flip 2, KONKR.
- Build: c7ad53a7 (674n code = cf629e2e) - QA build installed on both 15 Nov 21:24.
- Why not emulator: the 53-layout gate's PASS needs real GPU / panel / controller; emulator evidence does not count.
- Tests: Flip 2 x1.0 `f674o`, KONKR x1.3 `k674n` (running 15 Nov from 21:28); then Flip 2 x1.3 and KONKR x1.0 on the same build
  to confirm the 674n fixes (Side menu Save sync, Bar arrows) and 674m glyphs (Arcade, Pocket ribbon).
- Result so far: Flip 2 x1.3 f674k (674k) 52/53 - Bar ◀; Flip 2 x1.0 f674o (674n) 52/53 - Side status line; KONKR x1.0 k674m (674k)
  51/53 - Arcade, Side; KONKR x1.3 k674n (674n) 51/53 - Library R2, Spot START. All five fixed in code since (674m-675e): a full
  re-run of all four on the newest build closes this request.
- 17 Nov KONKR (USB, live session, QA build qa-0.6.73-678n-ci = 678n): x1.0 k678n10 52 / 53 PASS - TV room rapid A B A B left the
  window open (D333, cause traced, HW-5); x1.3 k678n13 52 / 53 PASS - Channel: the achievement count under the focused name's
  second line (readability) -> 678o fix (D350) passed kc678o13 but made Compose never idle on Channel (CI robo tests) -
  reverted in 678q: Channel x1.3 count under a two-line name is a known v0.6.74 item.
- RC (17 Nov): qa-0.6.73-rc-91b1ac3-ci full x1.0 krc10 53/53 PASS (incl. TV rapid); final RC qa-0.6.73-rc-be8865c-ci krcq10
  CHAN / TWIN_ICONS / CRATE / TV / CARDS / BOX 6/6 PASS.
  Arcade, Side, Library, Spot, Bar, Pocket ribbon PASS at both scales (the k674 FAILs are gone). Owner review sheet:
  "C:\Users\User\Claude outputs\live678n\KONKR_popups_678n_review.pdf" (both scales, every layout). Flip 2: unavailable.

### HW-2 Record crate x1.3 vertical menu - verification checklist - DONE on the KONKR 17 Nov (all items PASS after the D333 fix)
- Device(s): Flip 2 + KONKR (x1.0 and x1.3).
- Build: latest dev commit.
- Why not emulator: owner's checklist is about real D-pad / focus timing on the handhelds.
- Tests: every action incl. MORE reachable by D-pad; MORE once, last, no dots; More open / close restores focus; rapid A/B
  reliable; nothing clipped at x1.0 / x1.3; menu never changes size while navigating. (Visual design APPROVED by the owner 15 Nov.)
- 17 Nov KONKR (qa-0.6.73-678o-ci, new tools/qa/device/crate678.sh + tools/qa/tools/crate678.py, run k678c2): x1.0 (one row) and
  x1.3 (vertical menu): every key one size in every step (PASS), PLAY -> SLEEVE NOTES -> INSPECT -> MORE one press each (PASS),
  MORE opens its page and B comes back with the focus on MORE (PASS), B closes (PASS), nothing clipped (popup gates k678n10 /
  k678n13 CRATE all PASS); rapid A B A B at 250 ms: 9 / 10 closed at x1.0 and at x1.3, no launch - FAIL (D333 / HW-5 cause).
- After the D333 fix (qa-0.6.73-678p-ci, k678p_crate): every item PASS at x1.0 and x1.3, rapid 10 / 10 at each scale.

### HW-3 Flip 2 charging sparkle - reproduce first - BLOCKED - DEVICE UNAVAILABLE
- Device(s): Flip 2. Why not emulator: OEM charging switch (`input_suspend`) behaviour.
- The 674i patch is NOT on the dev line, never installed, never tested (branch claude/charge-loop-674i, docs/claude/patches/).
- Evidence conflict (owner 16 Nov): the owner saw the battery drop to ~78 %, charge back to 80 % and the sparkle only at
  80 % - one cycle, not a loop. The 15 Nov 20:20 log showed production's limit toggling `input_suspend` every 1-2 s at 79-80 %.
- Next, when the Flip 2 is back: read-only reproduction first (production Voltcap as it is, plugged in near 80 %, 15 min:
  logcat + battery state + whether the sparkle shows more than once). Only if it loops: apply the patch to a QA build and one
  supervised test with production's limit off (owner's step). If it does not loop: close the patch as not needed.

### HW-4 Cube first A on KONKR x1.3; L2 / R2 fact 0 px - DONE (popup gate) / onepress NOT TESTED
- Device(s): KONKR. Build: latest. Tests: popclip gate CUBE at x1.3 + onepress.
- 17 Nov k678n13 (qa-0.6.73-678n-ci, x1.3): CUBE all popup-gate categories PASS - the first A opened the window (no "first A
  opened nothing"). NOT TESTED: the L2 / R2 fact width on its own, vqa onepress.

### HW-5 Rapid A/B leaves a window open once (Cards, Crate, TV) - DONE 17 Nov (D333 fixed in 678p, 60/60 rounds closed on the KONKR)
- Device(s): KONKR + Flip 2. 674k gates (f674k, k674m) had no rapid FAIL; keep watching in HW-1 runs before closing.
- 17 Nov KONKR: TV room k678n10 (1 / 1), then crate678.sh 10-round runs: Record crate x1.0 1 / 10, x1.3 1 / 10, TV x1.0 1 / 10
  left the window open (no launch ever). Every failing round's VoltcapQA trace is the same: A opens; B closes (fly-back); the
  second A lands on the closing window ("win reopen on press"); the window finishes closing with reopen=true and a new window is
  opened a frame later; the last B reaches NO window and not the home (no winkey / key / "home B" line) - it falls in the swap
  between the closing dialog and the reopened one. Proposed fix (cloud): reopen in place (keep the same dialog window and replay
  its opening) so no key can fall between two windows; regression = crate678.sh 10 rounds on Crate + TV, x1.0 + x1.3.
- 678p (reopen in place, qa-0.6.73-678p-ci, crate678.sh k678p_*): Record crate x1.0 10/10 + x1.3 10/10, TV room x1.0 10/10 + x1.3
  10/10, Cardboard box x1.0 10/10, Card binder x1.0 10/10 closed - 60 / 60, no launch (before: ~1 in 10 left open).

### HW-6 Spot START glyph re-measure with the key box (675e, D-674o-1) - KONKR PASS 17 Nov; Flip 2 NOT TESTED
- Device(s): KONKR (x1.3, where k674n read dy +3 px) + Flip 2 x1.0. Build: 675e or later (QA app).
- Why not emulator: the reading came from the KONKR's real panel and backdrop at x1.3; emulator evidence never closes it.
- Tests: popclip / navclip on SPOTLIGHT home with QA traces; glyphcheck on the run folder - START / SELECT / A / X / Y must read
  source "InkGlyph+key" and PASS (limits unchanged, max(1.5 px, 5 %)); look at the crops of anything over 4 %.

- Result 17 Nov evening (KONKR, QA build qa-679c-e4669b9-ci, pop529 k679p13 x1.3): SPOT popup gate all columns PASS incl. glyphs (START / SELECT / A / X / Y) - PASS on the KONKR; Flip 2 x1.0 NOT TESTED (device unavailable).
### HW-7 Spotify screen + player: long names shown whole (675l, rule 11) - OPEN
- Device(s): KONKR (960x640, the narrowest), x1.0 + x1.3. Build: 675l or later (QA app). Only if the QA app is already signed
  in to Spotify or shows its lists without an account; never sign in with the owner's account for this.
- Why not emulator: the emulator has no Spotify session; row heights / slide speed are judged on the real panel.
- Tests: a playlist / album / track with a long name and long artist line: the name wraps to 2 lines, shrinks, then slides
  (never "…"); list rows keep their height when the artist line is one line; D-pad focus moves row to row without jumps.
- 675m extension: notifications panel (R1 pull-down) with a long app name, a long notification title and a long two-line text,
  a running background task and the charge-limit pill: wrap / shrink / slide, never "…"; rows keep their height; D-pad ▶ / ◀
  still steps between a notification and its ✕ (D26). Use notifications the QA app can already see; post none from other apps.
- 677a extension (commit: the 677a cloud run's): the post-game session card (return from a game with a long name, a session
  with achievements and a milestone) and the status-bar session note: wrap / shrink / slide, never "…"; the card keeps its
  height for a short name; the note slides inside its 320 dp and stops after 30 s idle. Emulator can't play a real game session.
