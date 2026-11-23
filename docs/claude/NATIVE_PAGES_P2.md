# P2 - every home layout's own Details / Achievements / Options pages

Owner, 17 Nov 2026 21:31: "START THE REMAINING 40 NATIVE POPUP PAGES NOW" - one of the main ongoing priorities (with the
pixel font and the badge styles), batches of 4-5 layouts, real screenshots / short recordings for review, progress tracked as
**Native pages X/53** with implementation, hardware verification and owner approval kept apart.

**Not allowed:** the shared page bodies (WindowDetails / WindowTrophies / WindowOptions) in a different border. Each layout's
pages are drawn the way its home is drawn - its materials, its typography, its page names, its motion.

**Shared (NativePageKit.kt, never the look):** the data (nativeFacts, ctx.summary, rememberNativeTrophies in the window's chosen
order with nextOrder, ctx.options), the behaviour (NativePages: page change with the leaving copy pressing nothing,
LocalShownPage; nativeBumpers: L1 / R1 from anywhere in the window - put it on the scene's root; nativeListFocus: the first row
of a page; NativeItem: focus + move / select sounds + 32 dp touch box; Modifier.nativeScroll: D-pad scroll with the scroll cue;
rememberOpenTrophy). Play-first, B onto the opener, rapid B / A safety and focus stability come from the window
(GameWindow / WinPageFlow) and are checked per layout by WinFocusRoboTest (Robolectric) - every new family must pass it, both
`plain=false` and `plain=true`, and ShelfTextRoboTest (no cut text, no overlap on native pages).

**Branch:** `claude/v0674-native-pages` (from the dev line; never into the dev line before the owner approves the batch's
comparison). Hourly runs may push fast-forward to it (HANDOFF names it). Rebase / merge the dev line into it when the dev line
moves (merge, never force-push).

## How a family joins
1. A `WinPages<Name>.kt` file: `<name>PageName(p)`, `<name>Move(dir)` (its own motion), `<Name>Page(ctx, p, live, …)` with its own
   Details / Achievements / Options; Performance may fall back to `ctx.pageFor` until it gets its own.
2. The scene: `.nativeBumpers(ctx)` on its root; its info area becomes `NativePages(ctx, …, move = ::<name>Move) { pg, live -> … }`
   (Info drawn as before for `WinPage.MAIN`); no `ctx.pageBody(` left; its scene added to `NATIVE_PAGE_SCENES`.
3. Tests: a `NativePages<id>Test` (pure names + "no ctx.pageBody / pageFor only for Performance"), WinFocusRoboTest +
   ShelfTextRoboTest for the layout (`--tests '*WinFocusRoboTest*<LAYOUT>*' --tests '*ShelfTextRoboTest*'`), static checks.
4. Review: the emulator trial in review mode on the branch
   (`gh workflow run emulator-qa-trial.yml --ref claude/v0674-native-pages -f review_layouts="<LAYOUTS>"`) for screenshots
   (EMULATOR evidence - preview only); hardware: popclip on a handheld (HW queue), and a short screen recording of the page walk
   (`screenrecord --time-limit 12` while R1 / R1 / R1 / B) for the owner.

## Batch 1 (owner's order): Classic, Console hub, Resume, TV room, Record crate

| Layout | Scene | Page idea (its own identity) | Impl | Robo | Emulator | HW | Owner |
|---|---|---|---|---|---|---|---|
| Record crate | RECORD_SLEEVE | the sleeve's printed matter pulled out sideways: LINER NOTES (credit block FORMAT / RELEASED / LABEL / STYLE, pressing note, story with a drop cap), TRACKLIST (SIDE A yours A1..., SIDE B to get B1..., badge as centre label, dotted leader to the points, A opens the words like a lyric sheet, ORDER rubber stamp), PRESSING NOTES (numbered stickers, red "!" for removing ones) | 681a, 681b (printed inserts: whiter stock, shadow, lean -0.7 deg settled / -3.5 deg while pulled, dog-ear, press rule; destructive rows ask twice), 681cr, 681f | WinFocus CRATE + ShelfText PASS; AchFirstRow CRATE 6/6 (x1.0 / x1.3, n 1 / 12 / 40) PASS | run 44182839168 (e79a407, before 681cr) x1.3 + x1.0: all four pages captured; FINDINGS: x1.3 tracklist titles broke mid-word ("QA achieve ment 1", 681cr fix not yet re-captured); destructive confirmation NOT captured (the app game window did not open in the script) | NOT TESTED | pending |
| Classic (Voltcap row) | VOLT_PANEL | the charged panel: DETAILS as a charge readout (facts as labelled meter lines along the bolt stripe, story on the dark glass), ACHIEVEMENTS as battery cells (a cell per achievement filling the stripe, list rows with a bolt for earned, charge % = progress), OPTIONS as power switches (each option a row with a switch-like lit edge in the accent); motion: the panel content charges in from the stripe (left to right wipe) | 681c (CHARGE READOUT meter lines + dark-glass story, CELLS battery + bolt cells, SWITCHES rockers / guarded red switch, charge wipe) | WinFocus CLASSIC + ShelfText PASS; AchFirstRow CLASSIC 6/6 PASS | run 44182839168: Info + Charge readout captured; FINDINGS: the scripted R1 walk did not reach CELLS / SWITCHES (open); "CHARGE TIME" label wraps at x1.3 | NOT TESTED | pending |
| Console hub | HUB_PLATE (HubNameplate, WinSceneFamilies.kt) | the console's nameplate: DETAILS engraved on the plate (facts as stamped labels, story as the plate's back), ACHIEVEMENTS as a row of service medals / screws along the plate with a list below, OPTIONS as the console's switch panel; motion: the plate flips on its hinge | 681d (ENGRAVED tags + plate back, SERVICE MEDALS row + riveted record, SWITCH PANEL toggles with red flip covers, hinge flip) | WinFocus HUB + ShelfText PASS; AchFirstRow HUB 6/6 PASS | run 44182839168: Info + Engraved captured; FINDING: the scripted walk did not reach SERVICE MEDALS / SWITCH PANEL (open) | NOT TESTED | pending |
| Resume & discover | DISCOVER_CARD | the discover feed: DETAILS as "About this game" cards stacked like a feed, ACHIEVEMENTS as a progress feed ("Next up", "Recently earned", "Still to get" shelves of small cards), OPTIONS as a settings sheet in the card's pill style; motion: cards slide up like a feed | 681e, 681f (feed cards rise in; PROGRESS: each achievement once under EARNED / NEXT UP / STILL TO GET - the first version repeated them and failed AchFirstRow x1.3) | WinFocus RESUME PASS; AchFirstRow RESUME 6/6 PASS (after 681f) | NOT RUN | NOT TESTED | pending |
| TV room | (none - the window draws on the TV's screen, GameWindow tvRect) | TELETEXT on the TV: P100 Details, P200 Achievements, P300 Options in teletext blocks (double-height title, coloured rows), Fastext bar (red / green / yellow / blue) for the pages, page number typed in the corner on a change; needs a scene for the TV window first | 681g (TELETEXT P100 / P200 / P300 on the set, Fastext bar, typed page number, L1 / R1 on the set; tvSceneCtx gives the TV window the native-page context) | WinFocus TV PASS (bumper walk n/a: no scene); AchFirstRow TV PASS | NOT RUN | NOT TESTED | pending |

## All 53

Native pages **18 / 53 implemented** at batch 1 (22 with batch 2, below) (13 earlier families + batch 1: Record crate 681a-b, Classic 681c, Console hub 681d, Resume 681e-f, TV room 681g) - implemented is NOT hardware-tested and NOT owner-approved; batch 1: 0 / 5 hardware-tested, 0 / 5 approved; hardware-verified: the 13 earlier ones in their
own gates (673j / k, KONKR / Flip 2 / Nova), Record crate NOT TESTED; owner-approved: none recorded for the new pages yet.

Earlier families (13 layouts, 7 scenes): Storage boxes (STORAGE_RECORD), Night menu (NIGHT_STATUS), Tile home + Grid
(TILE_CARD), Blades (BLADE_GUIDE), Game shelf + Case stacks + Console stack + Coverflow details (INDEX_CARD), Twin desk + Twin
icons + Twin showcase (TWIN_HINGE), Disc station (DISC_PANEL) - these draw the shared page bodies inside their own card; after
the 40 they may get their own bodies too (owner to decide).

Remaining after batch 1 (35): Box, File select, Memory card, Coverflow (CASE_MEDIA), Orbit, Airy, Bubbles, Camera, Panels,
Magazine, Rental shop, Terminal, Arcade, Cinema, Arctic, Channel, Card binder, Green core, Cube, Pocket classic + Pocket ribbon
+ Pocket (POCKET_LCD), Art library, Cross, Live, Dashboard, Side menu, Spotlight, Icon bar, Circuit, Library, Timeline, Hero +
Hero tiles, Layout Studio. Suggested batches: 2 = Cube, Spotlight, Cinema, Magazine, Arcade; 3 = Pocket family, Channel,
Terminal, Circuit; 4 = Box, File select, Memory card, Coverflow, Card binder; 5 = Orbit, Airy, Bubbles, Camera, Panels;
6 = Rental shop, Arctic, Green core, Art library, Cross; 7 = Live, Dashboard, Side menu, Icon bar, Library; 8 = Timeline, Hero,
Layout Studio.

### Emulator review run 44194071430 (f97abbf, 18 Nov 00:30) - EMULATOR only, not hardware, not approved
All five batch-1 layouts walked at x1.0 and x1.3 (Info, Details, Achievements opened / scrolled / 120-set far down, Options),
recordings of the R1 walk and the Motion-Off walk. The 681h bumper fix works: Classic and Hub now reach every page.
Open findings: (1) Record crate at x1.3 - the insert under the sleeve's header shows one to two tracks only: give the insert more
room on its pages (smaller header). -> FIXED IN CODE 681s (cloud, 18 Nov): on the sleeve's own pages the header steps back (thumbnail, page
name, the name on one sliding line, no console line); the count sits beside TRACKLIST, the ORDER stamp on the first side's line
(wraps below on a long order name). CrateInsertRoom681sRoboTest: 3 whole tracks at x1.0, 2 at x1.3 (was 1 + part); Info unchanged.
Needs an emulator re-capture + the owner's look. (2) The armed destructive confirmation is still NOT captured on screen - the App category
did not take in the script, so the window opened on a ROM game; behaviour is covered by NativeConfirm681Test 8/8 only.
(3) TV room: one blocked launch per scale during the walk (QA app blocked it, nothing started) - trace which press reached Play. -> TRACED 681v (cloud, 18 Nov, from the run's own layout dumps): the App category did not take (finding 2), so the window was on
a ROM game whose Options page has 9 rows; the script's ten Downs left the list for the button row (TV_app_options_last: Play focused)
and the "arming" A pressed Play. Not an app defect: p2review.sh now presses A only when the dump shows an option row focused (else
logs "armed confirmation NOT TESTED"). The same dumps showed a real TV defect - (4) the teletext pages had NO body on the set's
screen: header "VOLTCAP TXT" wrapped (one letter a line at x1.3), the double-height name + two-line Fastext keys left 0 px for
the list - P200 showed no achievement row (all rows 0 px at x1.0 and x1.3), P100 no story, and D-pad Down fell straight to the
buttons (TV_trophies_focus: Back lit; the 120-set never scrolled). -> FIXED IN CODE 681v: under 200 dp of page height the masthead
is one line (P-number, page name on its blue block, the game's name sliding), the console joins the Info facts, Fastext keys are
one line of short key words (PICTURE / INFO / TROPHIES / OPTIONS), the P200 bar row stays one line (10-cell bar, ORDER key slides,
24 dp touch), rows at the readable 10 sp with one sliding description line (A opens the whole text). Roomy windows unchanged.
TvTeletextRoom681vRoboTest 4/4 (2 whole rows at x1.0, 1 at x1.3, masthead one line); NativePages681aTest 11, NativeConfirm681Test 8,
AchFirstRowRoboTest 107, WinFocusRoboTest 448 (39 skipped) PASS in the cloud. Needs an emulator re-capture + the owner's look.

### Emulator review run 44233448467 (00b9c1d, "P2:TV CRATE") - EMULATOR only, read by 681w
Crate x1.3: the insert now shows two whole tracks (681s confirmed). TV: the pages have a body. Open findings:
(5) TV Fastext keys at equal widths slid ("PICTU / TROPH / OPTIO" at x1.3, "TROPHIE" at x1.0). -> FIXED IN CODE 681x (cloud,
18 Nov): each tight key is as wide as its own word (measured); where the four full words cannot stand side by side the set's
short words PIC / INFO / TROPHIES / OPTS do, each in a key of its own width (ttxKeyPlan). TvFastextKeys681xRoboTest (x1.0 /
x1.3: every key's word laid out no wider than its key + the plan's pure cases). NOT compiled in the cloud (Maven Central 429
twice) - CI on the push is the first compile.
(6) p2review.sh playfocused() did not match on the device (toybox grep, the ERE's [^]] bracket): the TV app_options_last dump
had Play focused yet A was pressed (one blocked launch per scale). -> FIXED 681x: fixed-string matches on one node per line;
selftest TestP2ReviewPlayFocused681x 5 (the fixed-string test fails on the old function), QA selftest 216/216.
(7) Review runs end NOT TESTED: grade_trial.py expects e529 runs (known 679a follow-up) - still open.
Needs: an emulator re-run "P2:TV" on the 681x push, then the owner's look.

### Emulator review run 44247782675 (cf40522, "P2:TV") - EMULATOR only, read by 681z (cloud, 18 Nov)
(5) CONFIRMED on the emulator: the Fastext keys read whole at both scales (PICTURE / INFO / TROPHIES / OPTIONS, x1.0 and x1.3).
The ORDER key and the game name in the masthead slide on their one line at x1.3 (681v design, rule 11 - full text by sliding).
(6) CONFIRMED: the guard held - Play was focused, A was NOT pressed, launches 0 at both scales. But the App category still did not
take (the window opened on Mario Kart 64, a ROM): the QA library has no Android app on the emulator. -> 681z: p2review.sh now
checks the launcher's "category App is empty here" line and logs "no Android app in the QA library - destructive confirmation
NOT TESTED" instead of walking a ROM game's options (no logcat -c mid-layout: the blocked-launch count reads that buffer).
Destructive confirmation: still NOT TESTED on the emulator - it needs an app fixture in the QA library, or the hardware run.
(7) DONE 681z: grade_trial.py review mode - a "P2:<layouts>" run is graded on its own walks p2r13 / p2r10 (grade_p2_run: walk
finished, every layout walked, its page shots there, no crash, no launch reached, nothing the script marked NOT TESTED, the QA
app's settings and snapshot files put back), a font review on e679r13 / e679r10 (popclip); P2 contact sheet sheet_p2_<LAY>.jpg
(both scales). PASS covers the walk's mechanics only - the look stays the owner's review. Selftest TestGradeTrialReview681z 6 +
TestP2ReviewRestore681z 2, 224/224. Graded on this run's artifact the new grader says FAIL (x1.3 "snapshot files diff=1") +
NOT TESTED (armed confirmation): the diff=1 was the restore's own false alarm - on a fresh emulator the keep folder was empty and
its literal glob failed cp / cmp (reproduced with the old line) -> fixed 681z ("none existed before the run").
Needs: the next "P2:..." review dispatch shows a real verdict (expected NOT TESTED until an app fixture exists for the confirmation).

### 18 Nov (interactive 682) - EMULATOR + KONKR, not owner-approved
- 682a RELEASE-BLOCKING fix: an A ~30-200 ms after an L1/R1 turn onto Details started the game (all 5 layouts) - 300 ms
  page-turn guard; NoStrayPlay682aTest 10/10 (fails without the guard).
- KONKR (GT78) popup gate pop529 + popclip, build qa-682c-2ca480f-ci: x1.0 PASS 5/5, x1.3 PASS 5/5, 0 launches.
- 682h: a grown focused row (armed / opened) brings itself into view - the confirmation line was cut (NativeConfirm681Test fails
  without, 8/8 with).
- Emulator review 44280831400 (c68e7b6): armed "Hide from library" confirmation ON SCREEN in all five layouts at x1.0 and x1.3
  (sheet p2_armed_confirmations_emulator.png); 0 launches. OPEN: TV room's teletext confirmation is cramped and partly cut
  (x1.0 worst); Crate x1.0 "cancel" touches the insert's edge. QA-tool gaps: p2review logged a false "no confirmation" for the
  x1.0 pass (dump read too early / by name), and grade_trial's review mode passed a log with FAIL lines - both to fix.


## 682j - A after L1 / R1: focus ownership instead of the 300 ms guard (owner 18 Nov 12:12)
- Root cause (diagnosed in Robolectric, focused node per frame): between a page turn and the new page lighting its control the
  focus stayed on the old one - Play on the TV set's keys for 4 frames, the B Back hint under the window on the shelf / tile /
  twin families (A then closed the window) - and Details itself landed on Play (pageFocusTarget DETAILS -> PLAY), so a quick A
  after R1 started the game. The TV ctx also never registered its opener (made outside the openers' provider): L1 back lit Play.
- Fix: no cooldown. `PageHandoff` (WinPageFlow) - the page change takes the focus off the old control in the same event; an
  invisible holder keeps every press made meanwhile (A, D-pad, L1 / R1) and delivers them in order to what the new page lights;
  B is never held (it drops the kept presses). Details on a layout's own pages lights its own story (`storyStop`, the older
  families' WindowDetails too), never Play. A return to the picture lands on the page's key, else the Details key, else Play.
  `NativePageTurn` / `PAGE_TURN_PLAY_GUARD_MS` removed.
- Tests: NoStrayPlay682jTest - all 17 layouts with their own pages + TV, x1.0 / x1.3 (36 cases): storms, A 0 / 1 / 2 frames
  after every turn, D-pad walls, B; intentional presses at once: L1 + A runs Options' first row once, B + A never plays,
  D-pad to Play + A plays once. Without the hand-over (DETAILS -> LIST only) 36 / 36 failed (TV: A 0 frames after R1 played;
  tile / shelf / twin: A on the Back hint closed the window). Regression: WinFocusRoboTest 448, NativeConfirm681 8,
  PlayWhileClosing412, RapidPlayD135 5, NativePages681a 11, PopupNativeStorage 9, TV 681v / 681x, Crate 681s, DiscTracks673 - PASS.
- Hardware: NOT TESTED yet (next: KONKR, quick L1 / R1 + A walk in Classic, Tiles, Crate, TV).


## 682k - confirmation room: TV teletext and Record Crate (owner 18 Nov 12:12)
- TV room: on the set's screen the options' list has about three teletext lines; the confirmation squeezed under the armed
  row was cramped and ran out of the list (x1.0: 0 px of it on screen). It is now a teletext strip (white box, "PRESS A AGAIN
  TO CONFIRM" / "MOVE AWAY TO CANCEL") at the top of the room's panel under the set (Play, Back); where there is no panel it
  replaces the set's masthead line. The armed row stays lit red on the set. TV option rows tightened (1 dp padding, 13 / 12 sp
  lines) so the removing row (2-line name, 3-line note) is whole at x1.3 (95 of 96 px of list).
- Record Crate: the row keeps a 6 dp margin at its end (the scroll cue no longer meets the sticker) and the sticker spans the
  line's width with 6 dp inside - "cancel" never touches the edge; one line where it fits, whole words where it wraps.
- A tapped row that grows brings itself into view too (NativeItem, was focus only).
- Tests: ConfirmRoom682kRoboTest (native text metrics, CRATE + TV, x1.0 / x1.3): both halves whole and >= 3 px from the row /
  panel edges, the armed row's name and note whole, Fastext keys whole, A A removes once. NativeConfirm681Test now includes
  TV and the same edge checks (D-pad walk, legacy metrics). Hardware: NOT TESTED yet.

- 682j / 682k HARDWARE (18 Nov, QA app only, build qa-682k-6a81717-ci): nav682j.sh on KONKR (CLASSIC TILES CRATE TV) and
  RP Classic (CLASSIC; TILES CRATE TV): R1 + A at once / 60 / 150 ms, R1 R1 + A, an L1 / R1 storm + A, R1 B A - 0 LAUNCH lines
  in every layout; one deliberate A on Play straight after the window opens - exactly 1 (PASS 8 / 8 layout runs). Screens: the
  turned page lights its own item (TV P100 story, crate liner notes, Tiles' Details story stop). First attempt: D359 (the
  script's R1 + A reached the QA app's quick-settings panel on the home - Android's Wi-Fi panel opened, nothing changed;
  fixed, re-run). NOT TESTED on hardware: the 682k confirmation strip / crate margin by eye (needs a p2review run).


## Batch 2 (owner GO 18 Nov 14:54): Game magazine, Cinema, Arcade cabinets, Cube menu - IMPLEMENTED, not hardware-reviewed, not approved
Batch 1 still needs its remaining visual fixes and is NOT visually approved.

| Layout | Scene | Its own pages | Impl | Robo | HW | Owner |
|---|---|---|---|---|---|---|
| Game magazine | MAG_PAGE | THE REVIEW (FACT FILE box + drop-cap article), CHECKLIST (SCORE box, tick boxes, SORTED BY tab), READER SERVICES (dashed coupons with scissors, WARNING coupon asks twice); page-turn slide; headline steps down on pages; the leaving copy's keys inert | 682n | WinFocus MAGAZINE 8/8, ShelfText 12/12 (x1.0 / x1.3, long name, 0 / 160 / 9999 achievements), NoStray x1.0 / x1.3 PASS | NOT TESTED | pending |
| Cinema | CINEMA_PREMIERE | MORE INFO ("About", rating-style plate, grey-label credits, synopsis), EPISODES (numbered rows, badge still with a progress bar, points as running time, "Sorted:" picker), MY LIST & MORE (round outlined keys, red one asks twice); fade-up screen change | 682o | WinFocus CINEMA 8/8, ShelfText 12/12, NoStray PASS | NOT TESTED | pending |
| Arcade cabinets | ARCADE_MENU | INSTRUCTIONS (NAME .... VALUE card, attract text), HIGH SCORES (1ST / 2ND ranks, 000050 scores, "-----" for to get), SERVICE MODE (▶ cursor rows, pink one asks twice); tube roll with a scan line; page name on its own line | 682p | WinFocus ARCADE 8/8, ShelfText 12/12 (x1.3 long-name overlap fixed), NoStray PASS | NOT TESTED | pending |
| Cube menu | CUBE_FACE | FACE 2 · SPECS (square fact tiles + glass slab story), FACE 3 · CUBES (a wall of little lit / hollow cubes + rows led by their cube), FACE 4 · CONTROLS (square keys, red-edged one asks twice); a quarter turn on the face's edge; small picture mark on the other faces | 682q | WinFocus CUBE 8/8, ShelfText 12/12, NoStray PASS | NOT TESTED | pending |

Native pages **22 / 53 implemented** (batch 1: 18; batch 2: +4). Next for batch 2 if wanted: Spotlight (SPOT_POSTER).

### 682r - QA-tool gaps closed (cloud, 18 Nov)
- grade_trial.py review mode: a line the script marked FAIL now fails the run even when the walk finished (it passed such a log
  before); a winguard ABORT grades INFRA. Selftest test_fail_lines_and_abort_682r fails on the old grader.
- p2review.sh: the armed confirmation is read twice (a second dump read, never a second key) - the x1.0 false "no confirmation";
  the window is closed by a checked closewin (B only while the game window holds the focus, until QaClip says none is open),
  never two blind B presses. Every key goes through winguard (682j-c).
- Batch 1 (Record crate, Classic, Console hub, Resume & discover, TV room): still NOT owner-approved; its open visual findings stay open
  until the owner's review of the next sheets.

### 682r - batch 2 room at x1.3 (KONKR, 18 Nov) - NOT owner-approved
- KONKR p2review p2k13 (build 73d6766, x1.3): Game magazine's CHECKLIST had no achievement row - the facts line wrapped, the
  points broke a word per line beside the order key, the page keys stacked. Two Downs reached the window's Full list key; the A
  opened the achievement dialog and winguard ABORTED the run safely (nothing more pressed, QA app put back). D360.
- CI Verify 44334080749 caught the same on AchFirstRow (MAGAZINE 0 px, CINEMA 51 / 95 px, CUBE 83 / 116 px at x1.3).
- Fixed in code: magazine pages drop the facts line (THE REVIEW's FACT FILE has them), keys in one row, points inside the SCORE
  box; Cinema's own screens keep a strip of the backdrop and a smaller title line; Cube: "8 / 12 LIT", smaller cubes for a big
  set, one sliding description line while closed (A opens the whole text). AchFirstRowRoboTest now runs the four layouts at every
  size and both scales (24 / 24 PASS; MAGAZINE fails on the old code). ShelfText 96 / 96, NoStray 8 / 8, Batch2 4 / 4.
- p2review: A on an achievement row only - a window key focused is a logged FAIL, A not pressed; rec.sh no longer required (D361,
  the RP Classic run ended silently before its first line).
- Hardware (QA app, build qa-682r-50f5004-ci, x1.3 only - x1.0 NOT TESTED on hardware):
  - KONKR p2k13b: Magazine, Cinema, Arcade walked - every page, the 120-set scrolled far, destructive row armed (confirmation
    shown) and left, never confirmed, 0 launches, restore OK / diff 0. Magazine's CHECKLIST now shows its rows; Cinema fine.
    FINDING: Arcade at x1.3 broke words ("HIG / H SCO / RES", "ACHIEVEME / NT"). Cube NOT TESTED: its home opens a console
    first - one A left no game window and winguard ABORTED with nothing more pressed (script fixed: TWO_STEP layouts).
  - RP Classic p2r13b (1240 x 1032, 620 x 516 dp): Magazine, Cinema walked, 0 launches, restore OK / diff 0. FINDINGS: Arcade's
    HIGH SCORES showed no row (the order key took the row, the title 0 px wide, 360 px tall) - two Downs reached Full list, the A
    opened the dialog, winguard ABORTED safely; Magazine's narrow column broke "ACHIEVEMEN / T" (cover column too wide).
- 682r-2 fixes (code): every native order key gets at most its share of its row (Arcade, Cinema, Cube, Discover, Hub, Volt,
  Magazine - as the TV's); Arcade: the picture steps back on its screens, whole-word head / titles / INSERT COIN / no-art name,
  the rank as wide as it is, the score under the title, one sliding description line; Magazine: smaller cover on its pages,
  whole-word titles. New AchFirstRowRpc682rRoboTest (the RP Classic's 620 x 516 dp, every native layout, x1.3; ARCADE fails on
  the old code). Tests: AchFirstRow (KONKR size, every native layout, all sizes, both scales) + Rpc - all PASS; ShelfText 192 / 192
  (ARCADE needed the no-art name and INSERT COIN left-aligned: centred, the legacy-graphics measure called them cut); NoStray,
  NativeConfirm, ConfirmRoom, Batch2, WinFocus PASS (1012-test run; RESUME's order key left unbounded after it cost 4 px).

### 682r - hardware re-check of the fixes (QA app, build qa-682r2-ef2694a-ci, x1.3; 18 Nov 18:06-18:12) - NOT owner-approved
- KONKR p2k13c ARCADE: every page walked, HIGH SCORES rows on screen with whole words, destructive row armed and left, 0
  launches. CUBE: first walk complete (CUBES face rows whole; "ORDER · SET / ORDER" key wraps in its half - minor, open);
  the 120-set's second open ABORTED by winguard (the cube stayed on the console's face, one A already opened the game - the
  script expected the home; nothing more pressed). Restore OK / diff 0.
- RP Classic p2r13c ARCADE: HIGH SCORES rows on screen (the 0 px title fixed), whole words, 0 launches; CUBE first walk complete,
  same second-open ABORT. MAGAZINE not reached (stopped at the task's 15-minute hardware limit). Restore OK / diff 0.
- p2review: TWO_STEP now checks after the first A whether the game window is already open (fixed after the run - NOT re-run).
- Still NOT TESTED on hardware: x1.0 for all four; RP Classic Magazine after 682r-2; Cube's 120-set and Options confirmation.

### 682s - batch 1 visual pass (owner 18 Nov 18:20: "2" = batch 1 visual fixes first) - NOT owner-approved
Hardware review walk (QA app, build qa-682r2-ef2694a-ci, x1.3, the hardened p2review; KONKR b1k13 + RP Classic b1r13, 18:21-18:36):
Record crate, Classic, Console hub, Resume & discover, TV room - every page reached on both, destructive row armed (confirmation
shown) and left, never confirmed, 0 launches, fatal 0, restore OK / diff 0. Findings and fixes:
- Classic SWITCHES: an ON / OFF word beside every option showed the row's focus, not the option's state ("Add to favorites OFF",
  "Game settings ON") and touched the armed row's confirmation box -> removed; the lit rocker alone shows the focus.
- Classic page head with no picture: the 34 dp chip got the console plate, "SNES" one letter a line (ShelfText, now covering the
  batch-1 layouts) -> the chip is the game's colour, the name beside it.
- Console hub ENGRAVED (KONKR x1.3): the focus landed on the story, taller than the page, and the stamped tags were scrolled away
  (GENRE / PLAYED 0 px shown, unreachable) -> the tags are the page's first stop; Down goes on to the story. Robolectric did not
  reproduce the scroll (a test written for it passed on the old code and was dropped) - hardware re-check needed.
- ShelfTextRoboTest now includes CRATE, CLASSIC, HUB, RESUME (TV keeps its own TvTeletext / TvFastext tests): 48 + 48 PASS after
  the fixes (the Classic chip failed before; Crate's "Sleeve notes" key is now matched as the details control).
- Looked at, no change: Crate (whole words, confirmation whole), Resume (rows whole), TV (one-line sliding parts as 681v).
  Hub's medal discs show no badge art on the RP Classic (art loading there, not the page).

### 682t / 682u - Spotlight, TV figure rule, Play slots (owner 18 Nov 21:28) - NOT owner-approved
- Spotlight (SPOT_POSTER) own pages: THE PLOT (the poster's billing block + synopsis), THE CREDITS (end credits, badges as cast
  photos, "BILLING:" order), BACKSTAGE (options as doors under a work light, red confirm strip on its own line). The pool of
  light moves from the title onto the page. Batch 2 = 23 / 53 layouts implemented.
- Play the same size in the same place on a layout's pages: KeyRow / UniformKeyRow `slots` - the main page's key count is kept
  (rememberKeySlots, More closed) and a page row with fewer keys keeps the main page's slot width from the same start; a page
  key wider than a slot takes the rest of the row, Play keeps its slot. Applied: Spotlight. Other layouts: queued (their rows).
- NativeBadge: an achievement badge with a fallback mark (first letter on the layout's colour) under the picture - never an
  empty black disc where badges don't load (RP Classic). Used by Spotlight; Hub / others: queued.
- TV room figure (permanent rule R27): drawn in front where it stands, the window's text / keys kept out of its box, the panel
  under the set starts past it. Teletext facts: label column as wide as the longest label, values whole words, stacked under
  their labels on a set too narrow.
- Hardware (QA app, build 0ac1f95, RP Classic only - the KONKR was not connected; 22 Nov 13:24-13:39): TV and Spotlight at x1.3
  (t3r13) and x1.0 (t3r10) - every page, destructive row armed and left, 0 launches, fatal 0, restore OK / diff 0. TV: the
  figure stands in front, no page text or key under it, at both scales. Spotlight: Play the same size and place on Info /
  Details / Credits / Backstage; badge fallback "Q" shown (no badge pictures on the RP Classic). Found: a credit label could end
  a line apart from its name ("GENRE" /) - fixed after the run with no-break spaces (not re-run).
- KONKR: NOT TESTED for all of 682t / 682u.

### Owner review decision, 22 Nov 18:45 (recorded verbatim in substance)
- FUNCTIONAL ACCEPTANCE of batches 1-3 (14 layouts): the recorded functional QA - L1/R1 focus handling, Play positioning, TV
  foreground keep-out, safe confirmations - is accepted. NOT visual approval, NOT merge authorization.
- All 18 P2 layouts stay NOT VISUALLY APPROVED. A review package (real-device Info / Details / Achievements / Options, x1.0 and
  x1.3, short motion clips, final builds only) is being prepared.
- Batch 4: focused x1.0 checks (Side menu, Icon bar, Arctic, Storage boxes) on KONKR + RP Classic; D364 fixed (682z2) and to
  be re-checked. Then batch 5 autonomously.
- The "31 / 53" count is retired: see NATIVE_PAGES_AUDIT.md - 18 own designs, 12 native frames with shared bodies (not done),
  23 shared page.

### Status board (owner 18 Nov: implementation, hardware verification and visual approval kept apart)
| Layout | Implemented | HW KONKR | HW RP Classic | Owner visual approval |
|---|---|---|---|---|
| Record crate, Classic, Console hub, Resume & discover | yes (682s fixes) | x1.3 pre-682s only | x1.3 pre-682s only | NO - refinements queued |
| TV room | yes + R27 figure (682u) | x1.3 pre-682u only | x1.0 + x1.3 PASS (0ac1f95) | NO |
| Game magazine, Cinema, Arcade | yes (682r) | x1.3 (Arcade 682r2) | x1.3 (Magazine pre-682r2) | NO |
| Cube | yes (682r) | x1.3 first walk | x1.3 first walk | NO |
| Spotlight | yes (682t) | x1.0 + x1.3 PASS (b06044b) | x1.0 + x1.3 PASS | NO |
| Terminal, Dashboard, Library, Card binder | yes (682w) | x1.0 + x1.3 PASS (b06044b) | x1.0 + x1.3 PASS (b06044b) | NO |

### Queued for the next handheld session (KONKR + RP Classic) - owner 18 Nov list
1. TV room R27 on the KONKR (x1.0 / x1.3): open, close, switch pages with the figure in view.
2. Spotlight on the KONKR; credit label no-break fix on either.
3. Batch-1 refinements once built: Play slot consistency (Crate, Classic, Hub, Resume, Magazine, Cinema, Arcade, Cube rows),
   Console hub horizontal room for achievements / options, Resume & discover no-art fallback (no large black area), badge
   fallback in Hub medals / all pages (RP Classic), confirmation-text spacing.
4. x1.0 for batch 1 + batch 2 (all nine layouts), Cube's 120-set second open and Options, Magazine on the RP Classic (682r2).

### 682v - batch-1 refinements (owner 18 Nov list; owner 22 Nov "1 and then 2") - NOT owner-approved, HW NOT TESTED
- Play the same size and place on a layout's pages (KeyRow `slots`, the main page's key count): Classic, Console hub, Cube,
  Spotlight, Dashboard, Library, Card binder; Record crate keeps the main page's arrangement (x1.3 vertical menu stays vertical
  on its pages, one key in view, the stack scrolls; x1.0 row). Where a page key is wider than a slot the usual row is used (a
  variant that kept Play's slot cut Play where the main row had scrolled - ShelfText CUBE / CRATE x1.3 - and was dropped).
  Not changed: Game magazine (vertical key menu on its cover, a row on its pages - 682r), Cinema (the big Play button on its main
  page by design), Resume & discover (its main row scrolls), Arcade (a vertical menu, Play first everywhere already).
- Console hub: on MEDALS and the SWITCH PANEL the seated case slims to 11 % (animated), the gap 10 dp - more width for the rows.
- Resume & discover: no picture (or one that never loads) = the game's discover tile (its colour, console and name), never a
  big black square.
- Badges: the fallback mark (the achievement's first letter) under every page's badge picture - Crate, Classic, Hub (medals in
  the hub's colour), Resume, Magazine, Cinema, Cube, Spotlight, Dashboard, Library, Card binder.
- Confirmation text: 12 sp on a 16 sp line, 6 dp above it, 8 x 4 dp inside its box, on every native page.
- Tests: TvFrontKeepOut682u, NativePagesBatch2 (slots, badges, batch 3), CrateInsertRoom681s still 2 whole tracks at x1.3.

### 682w - P2 batch 3 (owner 22 Nov) - implemented, NOT owner-approved, HW NOT TESTED (27 / 53 layouts)
| Layout | Details | Achievements | Options | Motion |
|---|---|---|---|---|
| Terminal | C:\> TYPE <GAME>.TXT - the story typed out | C:\> DIR ACHIEVE\ /O:<order> - a directory listing, [X] earned, points as size | C:\> SETUP - numbered menu, "ARE YOU SURE (Y/N)?" | types in line by line from the prompt; the command menu on the right never moves |
| Dashboard | OVERVIEW - stat tiles grid + story card | PROGRESS - completion widget + list cards with points chips | SETTINGS - cards with chevrons | tab switch: fade up from 97 % |
| Library | ABOUT THIS GAME - description panel + facts column | ACHIEVEMENTS - icon rows with the unlock-rate bar | MANAGE - manage menu | tab strip underline; slides in from the tab's side; banner steps back to a strip |
| Card binder | CARD TEXT - stat line + italic flavour text box | MOVES - badge as the cost, points as the move's number | RULES - the rule box | the text box is dealt (rises in) |

### 682x - full hardware pass, all 14 native-page layouts (owner 22 Nov "do it on the RP Classic all", KONKR connected too)
QA app build b06044b (682v/w), the hardened p2review (winguard), synthetic library, 14:43-16:14 (longer than the usual 15-minute
limit - the owner asked for all of them). Layouts: Crate, Classic, Hub, Resume, TV, Magazine, Cinema, Arcade, Cube, Spotlight,
Terminal, Dashboard, Library, Card binder.
| Run | Device | Scale | Result |
|---|---|---|---|
| all13 | RP Classic | x1.3 | 14 / 14 walked, every page; 0 launches; fatal 0; restore OK / diff 0 |
| allk13 | KONKR | x1.3 | 14 / 14 (first start stopped "asleep" before any key, re-run); 0 launches; restore OK |
| all10 | RP Classic | x1.0 | 14 / 14; 0 launches; restore OK |
| allk10 | KONKR | x1.0 | 14 / 14; 0 launches; restore OK |
- The only FAIL lines: Terminal "A on Hide from library showed no confirmation" on all four runs - a CHECKER miss: Terminal's
  confirmation reads "ARE YOU SURE (Y/N)? A AGAIN = YES · MOVE AWAY = NO" (present in every dump and screenshot); p2review now
  matches it (the device copies were the old script - a push raced the start). Confirmation = PASS by evidence.
- Verified on both: TV figure in front, no page text / key under it (x1.0 + x1.3); Cube's two-step home (both opens); the
  Resume no-art tile, Hub slim seat and medal letters, readable confirmations, Crate vertical keys at x1.3 on the KONKR.
- Found: Dashboard / Library / Card binder at x1.3 - Play half-width on pages where a page key is wider than a slot (main Play
  smaller) -> 682x: Play keeps its slot where the main row showed every key whole (not on hardware yet).
- Not owner-approved: all 14. Device side: the RP Classic shows an Android install-confirmation prompt that was there before
  the run (not ours) - left untouched.

### 682x re-check on hardware (QA app qa-682x-69aafaf-ci, x1.3, 22 Nov 16:25-16:52) - NOT owner-approved
| Run | Device | Layouts | Result |
|---|---|---|---|
| pk13 | KONKR | Dashboard, Library, Card binder, Terminal | 4 / 4 walked; 0 launches; fatal 0; restore OK / diff 0 |
| pr13 | RP Classic | same | 4 / 4; 0 launches; fatal 0; restore OK / diff 0 |
- Terminal's confirmation now detected by the checker (armed + left, never confirmed) on both.
- Play on the pages (QaClip dumps): Dashboard and Library - the same size and place on Details, Achievements and Options
  (PASS). Terminal - RUN the same on every page (PASS). Card binder - Details / Achievements: Play the left half; Options
  (Play alone): a short key in the middle - FAIL -> fixed in 682y (pageAloneGeom), not on hardware yet.
- Review page of all 14 layouts (four run strips each) published for the owner's approval: artifact "P2 native pages - 14
  layouts for approval".

### 682y - P2 batch 4 (owner 22 Nov "All") - implemented, NOT owner-approved, HW NOT TESTED (31 / 53 layouts)
- Side menu (SIDE_DRAWER, WinPagesSide.kt): the drawer's submenus - section header in the accent, rows with the accent bar
  (drawn behind the row: a filled bar child took the whole page's height and left the list 0 px - AchFirstRow caught it);
  submenu slides in from the right. Picture steps back to a strip on pages, a 14 dp margin on a short screen (RPC x1.3: the
  first achievement was cut in half).
- Icon bar (DOCK_FLYOUT, WinPagesDock.kt): grouped rounded cards (Details), the achievements as round dock icons with their
  names (the one you're on grows with the ring; A shows its description in the line under them), grouped list (Options);
  popover motion. Play keeps the first half on every page (KeyRow slots). No-art cover: the console plate.
- Arctic (ARCTIC_DRAWER, WinPagesArctic.kt): the info list (92 dp field column) + plot; media-centre lists with the accent
  band; vertical motion. No-art poster: the console's initials and name (ArcticNoArt).
- Storage boxes (STORAGE_RECORD, WinPagesStorage.kt): the sheet's contents are the record's own - pixel-framed fact cells,
  the list with the red pointer and "N PTS", the menu in monospace capitals with a pixel-framed red confirmation.
- Fixes: Card binder Options Play (above); the no-art plate's console name only where its longest word fits
  ("PlayStatio / n 2" on the Card binder's slim picture, noArtNameFits).
- Checked (cloud, Robolectric): AchFirstRow (NATIVE + RPC), ShelfText native pages incl. batch 4, NativePagesBatch2Test,
  WinPresentationTest, UniformKeys673, PopupMore452, CrateVertical674, CategoryStable394, TvFrontKeepOut682u; static checks
  PASS; PageSheet review of the four at x1.0 / x1.3.

### 682y on hardware (QA app d294cf2, x1.3, 22 Nov 17:49-18:05) - NOT owner-approved
| Run | Device | Layouts | Result |
|---|---|---|---|
| pk14 | KONKR | Side menu, Icon bar, Arctic, Storage boxes, Card binder | 5 / 5 walked; 0 launches; fatal 0; confirmations armed + left; restore OK / diff 0 |
| pr14 | RP Classic | same | 5 / 5; 0 launches; fatal 0; restore OK / diff 0 |
- Play on the pages (QaClip): Card binder now the same on Details / Achievements / Options (fixed, PASS); Icon bar and Arctic
  the same on every page (PASS). Side menu - Options (Play alone) a row lower: FAIL -> 682z (two rows' room on pages).
  Storage boxes - keys on the name line on some pages and under the sheet on others (Play jumped): FAIL -> 682z (every page
  decides as for Play + "Full details"; two slots).
- 682z on hardware (QA app 3d6d46f, x1.3, 18:28-18:36): pk15 KONKR + pr15 RP Classic, Side menu + Storage boxes - walked,
  0 launches, fatal 0, restore diff 0; Play the same size and place on Details / Achievements / Options on both (PASS).
- Found, pre-existing (not a native page): D364 Storage Info sheet "ACHIEVEMENT / S" at x1.3 on the RP Classic - queued.
- Batch 4 x1.0 on hardware: NOT TESTED. Review page updated to 18 layouts (batch 4 strips x1.3). Approval: none yet.

### 682aa - P2 batch 5 (owner 22 Nov "proceed with Batch 5 autonomously"; audit class B first) - implemented, NOT owner-approved, HW NOT TESTED
- Shelf family (Game shelf, Case stacks, Console stack, Coverflow details - WinPagesShelf.kt): the next index card's catalogue
  (facts typed on ruled lines, field in small monospace capitals in the card's label colour), the card's checklist (badge as a
  ringed stamp, title, "N PTS" as the catalogue number), entries with the arrow; each window's own wood / slate / metal.
- Twin family (Twin desk, Twin icons, Twin showcase - WinPagesTwin.kt): pages drawn for the touch screen - fact tiles, a touch
  list of tall rounded tiles with a round points counter, big touch buttons; desk paper or dark glass.
- Both families: Play keeps the first half of the page's row on every page (PageBars(page = true) - two slots).
- Audit: class A 18 -> 25 of 53; class B 12 -> 5 (Night status, Blades, Tiles, Grid, Disc station).
- Checked (cloud): NativePagesBatch2Test (shelfFamilyCardsAreTheirOwn), ShelfText, AchFirstRow (+ RP Classic), PageSheet x1.0 /
  x1.3 of the seven, static checks PASS.

### 682ab - Play the same on every page, x1.0 too (KONKR + RP Classic fk10 / fr10 on 2763331, 22 Nov 19:25-20:17)
- Hardware x1.0, all 18 P2 layouts, both handhelds: every page walked, 0 launches, fatal 0, restore diff 0. Batch 4 x1.0 (Side
  menu, Icon bar, Arctic, Storage boxes): Play the same on every page (PASS); Icon bar's grid tripped the checker (D368, tooling).
- Found (D366): at x1.0 Play changed between pages on Card binder, Console hub, Cube (KONKR), Cinema, Record crate, Resume -
  the x1.3 fixes (682x-682z) did not hold at x1.0. Fixed 682ab: a page's keys always take two slots (Play the first half),
  measured with a "Full details" reference key that is drawn outside the row (no focus, touch or semantics); Console hub's
  narrow seat on every page; Record crate's stack keeps its view height. New regression test PlayPlace682abRoboTest (25 layouts,
  KONKR + RP Classic screens, x1.0 + x1.3). TV room strip (D367) left open for a decision.
- Production observation (Level 2, read-only, RP Classic): com.shelf.launcher updated 0.6.73 / 79 -> 0.6.73 / 80 at 18:36:43,
  installer and initiator com.shelf.launcher (Voltcap's own updater; its Android install prompt had been waiting on screen
  since before 16:52). No QA input was sent after 18:34:53 (run pr15 done) and the restore sends no keys. Recorded for the owner
  to confirm it was their own confirmation.

### 682ad - P2 batch 6: Tile home + Grid (WinPagesTile.kt) - implemented, NOT owner-approved, HW NOT TESTED
- The lifted card's other side in the home's own tile language: fact tiles (value big and light, field small), wide
  achievement tiles (badge on its own square tile, the points as a big light number), flat option tiles with the accent edge;
  Play keeps the first half on every page (PAGE_SLOTS). The card's turn stays its own.
- Audit: class A 27 / 53, class B 3 (Night status, Blades, Disc station), class C 23.
- Also 682ac: Storage boxes' coverless sprite card in the game's placeholder colour (D369).

### 682ae - P2 batch 7: Night menu + Blades - implemented, NOT owner-approved, HW NOT TESTED
- Night menu (WinPagesNight.kt): STATUS tags on the slant, achievement / option rows as slanted command bars (white and pulled
  out when it's yours, "N PT"); the commands column now turns about its top edge, so Play stays put on every page.
- Blades (WinPagesBlade.kt): the dashboard's thin type, rows lit in the blade's white-to-glow gradient, "N G" points; the page
  blade's keys in two page slots.
- Audit: class A 29 / 53, class B 1 (Disc station - its LCD track pages, owner review pending), class C 23.

### Review package + hardware, 22-23 Nov (owner decision 22 Nov 18:45) - NOT VISUALLY APPROVED
QA app only, synthetic library, KONKR (GT78, USB) + RP Classic (USB). Runs: gk10 / gr10 (25 layouts x1.0, build 1ada6c8),
gk13 (29 x1.3, b316b8f), gr13 (29 x1.3, 9192a89), hk10 / hr10 (Magazine, Arcade, Storage, Tiles, Grid, Night, Blades x1.0,
9192a89), hk13 (Magazine, Arcade x1.3, 9192a89). Every run: every page walked, 0 launches, fatal 0, confirmations armed and left
(never confirmed), owner snapshot restored diff 0. (~5 h of handheld time - the owner asked for the full package.)
- Play the same size and place on Details / Achievements / Options (QaClip dumps): 28 / 29 layouts on both handhelds at both
  scales. TV room NOT (D367, open - needs a decision).
- Batch 4 x1.0 (owner's focused check): Side menu, Icon bar, Arctic, Storage boxes - PASS on both. D364 VERIFIED (RP Classic x1.3).
- Review package (artifact "P2 native pages - review package (29 layouts, final builds)"): per layout x run (KONKR / RP Classic
  x1.0 / x1.3) a strip Info · Details · Achievements · Options · confirm armed + the R1 walk clip; each strip names its build
  (9192a89, or 1ada6c8 / b316b8f where the layout's code is unchanged in 9192a89).
- Status: implemented 29 / 53 (own designs); hardware-walked 29 (both handhelds, both scales); functionally accepted 14
  (batches 1-3, owner 22 Nov); visually approved 0; released 0. Remaining: Disc station (class B) + 23 shared-page layouts.

### 682ag - owner visual review of all 29 (23 Nov 07:28): critical + polish fixes - IMPLEMENTED, hardware-walked x1.3, NOT VISUALLY APPROVED
The 16 layouts the owner called close to approval are untouched (except the shared WholeWordsText fix below). Changed:
- **TV room (option 2):** the native-page strips no longer draw a Back key (B still goes back - the window's own handling);
  the page row keeps two page slots, Play first. Teletext grid: where fewer than 24 characters fit a line (RP Classic x1.3 ~17)
  the grid's text steps down (ttxGridScale, never under 78 % of its size). Sonic keep-out unchanged. D367 -> fixed in code.
- **Spotlight:** pages on a rounded dark plate (72 % black; 35 % over the no-art stage); no art: a stage of its own (curtains,
  the pool of light, NOW SHOWING, the name centred in whole words, console · year, genres).
- **Arctic:** the menu as wide as its longest word needs (45-60 % of the drawer), the info list the rest; where that is under
  ~212 dp the field goes over its value (no 92 dp column). The RP Classic sheet is now 620 x 540 dp, the hardware's real size -
  at 516 dp the drawer was "compact" and the bug never showed.
- **Arcade:** High Scores' head, count and order key on lines of their own, whole words, the pixel font's line height; the
  description whole on the entry you're on (or opened); Instructions stack NAME .... over its value where the card is under
  300 dp; Service mode lines in whole words with a gap; the duplicate page name under the title dropped (the panel heads it);
  no-art cabinet name in whole words.
- **Icon bar:** icons keep their points (12 sp); a detail plate names the key you're on - name, points + earned, description
  whole (A adds date / rarity); beside the dock where the flyout is short (KONKR x1.3), under it otherwise.
- **Magazine:** callouts in one column where two can't hold the longest word ("ACHIEVEM / ENTS"); FACT FILE values in whole
  words (field over value under 240 dp); no art: the issue's printed cover (masthead band, console plate, colour strip).
- **Cinema:** no art: a title card (projector beam over the game's colour, console initials + name). **Cube:** the order key
  is "⇅ SET ORDER" in whole words; the pages' small mark shows the console initials, not an empty square.
- **Terminal:** the menu as wide as its longest command (30-45 %, one width on every page so Play never moves); paths and
  switches never break after "/", "\" or ":" (word joiners); dim lines brighter (green 4.5 -> 7.4 : 1, amber 5.6, white 7.8).
- **Library / Side menu:** no-art banners (game colour + initials). Side drawer width responsive (sideDrawerShare: +8 % on a
  squarer screen, + up to 9 % with large text, 62 % max).
- **Night menu:** no-art cover plate; the command column as wide as its longest command (26-34 %), the stats panel giving up the
  same; a stat whose value would get under 96 dp goes under its label (whole words).
- **Shared:** WholeWordsText with a textAlign takes the line's width (a centred line measured past its own edge).
- **Not changed yet:** Coverflow Details reading space (needs a decision: widening the panel on the pages covers the home's
  title, the only place the name shows there); the page-transition frame audit (Arctic, Magazine, Library, Cube) - not started.
- Tests: Review682agTest (pure helpers - fails on 682af: they did not exist), PlayPlace (KONKR + RPC), AchFirstRow (both),
  ShelfText, NightStatLabels, Terminal, Arcade, Storage words, UniformKeys, PageBars, NativePagesBatch2, BarDockLabels -
  1093 tests, 0 failed (local, this commit's sources). Static checks PASS, no new findings.
- PRODUCTION (Level 2, read-only, 23 Nov): RP Classic `com.shelf.launcher` is 0.6.73 (versionCode 80), signing digest 1903d14a
  (= the KONKR's production v80), lastUpdate 2026-11-22 18:36, firstInstall 2026-10-12 kept (in-place update, data kept).
  dumpsys only; the app was not started, touched or changed. Who confirmed the installer prompt is not known from the device.
- Hardware (QA app, Level 1, 23 Nov): KONKR (GT78, USB) + RP Classic (USB), x1.3, 12 changed layouts - runs kag13 / rag13
  (build d29e05c) and kbg13 / rbg13 (TV, Night, Icon bar on f17dfb8). Every page walked, 0 launches, fatal 0, confirmations armed
  and left, owner snapshot restored diff 0 (all four runs). Play the same size and place on Details / Achievements / Options:
  12 / 12 on both handhelds - TV now included (the strip's scroll was kept from Info; each page's strip starts fresh, f17dfb8).
  x1.0 not re-run on hardware (emulator sheets + Robolectric only). ~30 min of handheld time, two handhelds in parallel.
- TV still: the strip under the small TV shows one key at a time at x1.3 (Full details one press right of Play); the teletext text
  is small because the set is small in the room art; its header's game name slides. Owner's call.
- Page-change frame audit: the review sheet's frame mode (VOLTCAP_SHEET_FRAMES) showed the change settled by the 2nd frame -
  the window runs with animations off under Robolectric, so it is not transition evidence. Needs an on-device frame capture
  (not the RP Classic's screenrecord). NOT DONE.
- Before / after package (artifact "P2 visual corrections - before / after (12 layouts)"): per layout, KONKR and RP Classic x1.3,
  the 22-23 Nov strip over the 23 Nov strip + the after walk clip.
- Merged the dev line (503d679: 684a no-art caption rule, 704a CI 429 retry); SceneNoArt keeps both rules (caption only where the
  window doesn't name the console or the word fits, and never where even the readable minimum can't hold it); Night's stat
  stacks when its value's word doesn't fit (682t) or the row leaves under 96 dp (682ag).
- Local full suite on d29e05c: 2952 tests, 1 failed (Readability476: the TV row's measuring key was a second WinButton call -
  fixed in f17dfb8, re-run green with the TV / focus / PlayPlace / Night / ShelfText groups).
- Owner 23 Nov 11:21 (RP Classic x1.3 shots of the package): Night's command keys ran off the right edge (the column turns 4°
  about its top, its lower keys drift right) and ACHIEVEMENTS ran under a key's slanted end; Arcade's attract menu broke
  "ACHIEVEMEN / TS". Fixed in fc0590a / 1d37976: an end margin for the turn (nightCmdDrift, one value on every page), the label
  keeps clear of the slant, the stats panel shows that it scrolls (fading edge + scroll cue, a D-pad stop while it scrolls);
  Arcade's menu lines in whole words, the menu 60 % of the attract screen. Re-walked on both handhelds x1.3 (rdg13 / kdg13
  fc0590a, reg13 / keg13 1d37976): 0 launches, fatal 0, restore diff 0, Play same place on every page. Package updated.
  (A first fc0590a copy was a stale APK - its runs rcg13 / kcg13 are void.)

### 682ah - owner 23 Nov 13:32 P2 items: finish + verify - IMPLEMENTED, hardware-checked, NOT VISUALLY APPROVED
Owner accepted the Night menu and Arcade x1.3 corrections as improved; no blanket approval, no merge, no release GO.
- **Spotlight (81a1c9e):** the title block over the art sits on a rounded 68 % black plate with a thin light line
  (spotTitlePlate); the no-art stage unchanged. KONKR x1.3 kh13: title readable over the busiest cover.
- **Terminal confirmation (81a1c9e, 3785f33):** number + pointer in their own column, description and confirmation indented by
  layout, the confirmation as three short lines (termConfirmLines), the armed row scrolled into view with 16 dp under it
  (TERM_CONFIRM_ROOM) - kh13 showed "MOVE AWAY = NO" in the dark bottom edge (D383); km13 (3785f33): all three lines clear.
- **TV (owner decision kept):** one key at a time, Play first, others to the right, B = Back, Sonic keep-out, Play the same on
  every page. Teletext: no continuous shrink - one step to 85 % only where under 24 characters fit a line (ttxGridScale).
- **Coverflow details:** proposal only, OFF (CoverReadingProposal): the panel keeps its width; the facts as one wrapped line with
  each value whole. Comparison images in the package; waits for the owner.
- **Page-change audit (31f9593, 3785f33)** - real-device screen recordings, every frame (trans.py), KONKR x1.3 kh13 / km13:
  Arctic, Library, Cube, Magazine never show two pages at once; headings / masthead / cube face label change when the old page
  has gone (rememberHeadingPage, NATIVE_OUT_MS 90); no scroll jump while a page opens (nativeOpeningScroll, 600 ms hold).
  Magazine's cover moved while the new sheet faded in (531 ms, text re-wrapped, keys under the edge - D382): it now moves while
  the old sheet leaves (magCoverMs) - 331 ms; its key row can still settle ~50 ms after the sheet appears.
  The window's key strip changes in the first frame (D384, deliberate: the D-pad's return focus needs the new key at once).
- **Frame times (gfxdelta, read-only before/after dumps, KONKR x1.3):** Arctic 10 % janky (p50 18 ms), Magazine 11.4 -> 6.4 %,
  Library 11 %, Cube 4 %, Spotlight 4 %, Terminal 2-4 %, TV 3.5 %. Arcade (D379, 48 % at x1.0) still open - not touched.
- **x1.0 hardware:** KONKR kf10 (81a1c9e) and RP Classic rg10 (31f9593): Play the same place on every page, 12 / 12 on both.
- Tests: Review682agTest (+ magCoverMs), PlayPlace KONKR + RPC, WinFocus, Readability476 - 0 failed. Static checks PASS.
  QA tooling: D380 (gfxinfo reset crashed the QA app) fixed in 9ceab5c; selftests 241 OK.
- **682ai (248e766):** RP Classic x1.3 rh13 showed the new Magazine sheet's key row rising into view over ~250 ms (AnimatedContent's
  default size animation clipping the page area while the masthead stepped down - D385). Native page changes no longer animate
  the area's size. Re-walked ki13 (KONKR: Magazine, Arctic) / ri13 (RP Classic: Magazine, Library): keys in place from the first
  frame, Feature -> Review 282 ms on the KONKR (was 531), fatal 0, 0 launches, restore diff 0, Play same place on every page.
- RP Classic x1.3 rh13 (3785f33, all 7 changed): fatal 0, 0 launches, restore diff 0, Play same place 7 / 7; Terminal
  confirmation clear; frame times p50 25-31 ms on every layout (the RP Classic records its own screen while walking) -
  janky Arctic 28 %, Magazine 12 %, Library 6 %, Cube 8 %, Spotlight 8 %, Terminal 4 %, TV 10 %. RP Classic x1.0 Night rh10:
  Play same place, 0 launches, restore diff 0.
- Package (artifact "P2 visual corrections - before / after", version 1795451646-b145): page-change sheets at the top, x1.0
  strips for both handhelds, the Coverflow proposal at the bottom (not applied).

### Owner 23 Nov 18:23 - decisions and queue (recorded verbatim in substance; dev-line backlog copy pending its lock)
- **Coverflow details: option A** - enable the compact facts line on the isolated QA branch; the reading panel keeps its width, the
  visible title stays (long titles too); facts readable without broken words; the story gains usable space; nothing overlaps cover,
  title or navigation; controller scrolling and focus correct. Test x1.0 + x1.3, show the actual before / after. QA trial only -
  NOT final visual approval, NOT merge.
- **TV room:** keep the one-button strip (Play first, Full details / Full list reachable, B works, Sonic's safe zone). No redesign
  unless testing finds a genuine defect.
- **Arcade stutter (priority):** profile KONKR x1.0 48 % slow frames - recording on vs off, stable screen vs page change, idle vs
  navigation, GPU / CPU frame times, recomposition and overdraw where measurable, current vs earlier builds. Separate app stutter
  from recording overhead; fix confirmed rendering problems without removing Arcade's look or animations; no "60 FPS" claims from
  the video's encode rate. **RP Classic general slowdown:** investigate separately; recording overhead is not a launcher regression.
- **Evidence ZIP:** the downloadable ZIP must physically contain the newest frame-by-frame sheets, x1.0 strips and the Coverflow
  comparison (the last one received was the old x1.3-only package).
- Functional and motion QA acknowledged; NO blanket visual approval of the 12 layouts or the 29-layout batch.
- **Then, in order:** Task 1 row-position collisions (Tile Home, VoltCap Row, Edit Home positions, moved widgets, category bars,
  game info, Play, shortcuts; neighbours rearrange; custom placements kept; Edit Home preview = Home; x1.0 / x1.3, screen shapes,
  large tiles, resized widgets, Undo / Reset / restart) - Task 2 R1 Focus Position tile (horizontal; audit all 53; Classic +
  Tile Home candidates; only on compatible layouts; A cycles, long-press direct; keep R1 order, prefs, smooth scrolling, selected
  game, category-label alignment; compatibility matrix) - Task 3 Recomps category (reliable auto-suggest + manual; APK launching
  unchanged; Windows-only builds never assumed to run) - Task 4 START -> classification, multiple categories (Recomps + Android
  games + N64 example; other apps / consoles / collections; manual beats automatic; persists across updates / rescans / restarts;
  no duplicate records; saves, achievements, art, favourites, settings, playtime untouched) - Task 5 remaining 24 native-page
  layouts incl. Disc Station's shared bodies; a shared page in a new frame does not count.
- Rules: v0.6.74 isolated branches, QA app, synthetic data, targeted hardware tests (no needless full sweeps); implementation /
  hardware / animation-performance / owner approval kept separate; no production install, merge or publication without GO;
  continue autonomously, report only real blockers or design decisions.

### 682aj - owner 18:23 items: Coverflow A, Arcade smoothness, RP Classic, evidence ZIP - IMPLEMENTED, hardware-measured, NOT VISUALLY APPROVED
- **Profiling tool:** `p2review.sh perf TAG SCALE "LAYOUTS"` - per layout, read-only gfxinfo dumps around each phase (home still,
  home still while recording, home navigation, window still, page walk, page walk while recording, window navigation) + the last
  frames' framestats; `gfxdelta.py --detail` adds slow-UI-thread / slow-issue-draw / missed-vsync counts and GPU p50-p99
  (selftests +2). Level 1 only (QA app), launches blocked, owner snapshot restored (diff 0 on every run).
- **Arcade (D379, fixed):** KONKR x1.0, no recording, 682ai kp10 -> 682aj kpn10: home navigation 48.7 % -> 4.0 % slow frames,
  GPU p50 12 -> 4 ms, RenderThread issue 7.9 -> 4.2 ms; game window still 88 % -> 0 %. Cause: the home repaints every frame
  (the scene behind keeps moving) and the room, every row machine and the big machine's body were drawn from scratch each time;
  the window's INSERT COIN blink requested every frame. Fix: offscreen layers for the static parts (arcadeCached: room, light,
  row machines you're not on, big machine's body / side; never while the camera zooms into the monitor, never in the hall where
  machines turn and scale), the blink as two state changes a second (ARCADE_BLINK_MS). Look and animations unchanged.
  ArcadePerf682ajTest (fails on 682ai). (Earlier build 679c on the KONKR: RenderThread 14 ms per Arcade home frame - heavy
  before P2 too; its slow-frame % is not comparable, other start game - see D387.)
- **Recording overhead:** on the KONKR a recording costs a moving home ~40-50 % slow frames on its own (Cinema 49 %, Arcade 41 %
  after the fix) - the earlier "48 %" came from a walk recorded on a screen that never stops moving. Page walks: about the same
  with and without. RP Classic: recording changes little.
- **D386 (open, next):** every native page change on the KONKR has one main-thread frame of 50-115 ms (composing the new page) -
  one hitch per R1 / L1, Cinema as much as Arcade.
- **RP Classic (D387, closed - not a regression):** frame latency ~26-30 ms on every build (GPU-bound device), 17 Nov build
  included. The "moves no longer glide" seen in rp10 / rpn10 was the test's start game: since 681b the scripts start on the
  "achmix" game (Tetris), older builds on the fallback. Same start game (PERF_GAME, perf mode): 682aj glides like 679c (17 Nov)
  and the dev line 19bc1e0 - 466 / 473 / 474 frames, 7.9 / 7.0 / 7.6 % slow. Bisect builds: 679c, a4382ac, 19bc1e0 (built here),
  2ca480f, 73d6766, f816912, 0ac1f95, 0fe071a (built here). D388 (open, low): starting on Tetris the RP Classic's row gives one
  40-70 ms frame per move with no glide - cause not known yet. The KONKR old-build Arcade numbers (kpold10) had the same start-game
  difference and are not a fair comparison; 682ai -> 682aj (same start game) is.
- **Coverflow details (owner option A):** compact facts ON in the QA branch (CoverReadingProposal default true). Hardware before
  (682ai kcb / rcb) / after (682aj kca / rca), x1.0 + x1.3, both handhelds: panel width unchanged, title beside it visible (home with
  Crazy Taxi shown), facts whole words, KONKR x1.0 now shows all facts and the whole story without scrolling; at x1.3 the page opens
  on the story as before; Play same place 4 / 4; 0 launches, restore diff 0. Longest title on hardware: "Crazy Taxi (USA)" - a much
  longer one NOT TESTED.
- **TV room:** unchanged (owner: no redesign without a genuine defect).
- **Evidence ZIP:** voltcap_p2_review_682aj.zip - 115 files incl. 9 page-change sheets, 24 x1.0 strips, the Coverflow hardware
  before / after and the smoothness table; artifact version 1795457328-5953.

### 682al - smoothness follow-up (23 Nov evening, autonomous) - CORRECTION + partial fixes
- **Correction (Arcade numbers):** the 682aj before / after (kp10 -> kpn10, "48.7 -> 4.0 % slow while moving through games") started
  on Tetris, the only game of its category - the presses never moved between games. Fair runs (`PERF_GAME="Shadow of the
  Colossus"`, a 30-game category, KONKR x1.0, no recording), 682ai -> 682al: home navigation frame p50 28 -> 20 ms, p99 85 ->
  42 ms, GPU 12 -> 5 ms; slow-frame share 19 -> 24 % (the RenderThread still issues ~8-9 ms per glide frame - not yet isolated;
  a scan-line batching try, 682am, changed nothing measurable and was reverted). Game window page walk 17.2 -> 2.0 % slow (p99 129 ->
  38 ms). Window still: depends on what is behind it; with the 30-game category 1 -> 6 % (noise-level).
- **D386 (page-change hitch), partly fixed:** atrace (`p2review.sh trace`) shows the page change's long frame is composition +
  layout of the new page. WholeWordsText no longer runs a BoxWithConstraints + its own text measurer per text: the same step-down
  runs inside the text's own layout (Compose TextAutoSize, `WholeWordsAutoSize`, the last layout always the whole text). Arcade's
  worst page-change frame 78 -> 46 ms; Cinema unchanged (130-140 ms - cost elsewhere, StableFullText suspected). ShelfTextRoboTest
  672 / AchFirstRow 220 / PlayPlace 112 green; PageChange682alTest (fails on 682ak). Hardware kam10: Arcade walked, Play same place,
  0 launches, restore diff 0.
- **D388 closed:** Tetris is alone in its category - nothing to move to. CI: b6a66bb's first Verify run failed on two unrelated
  heavy Robolectric tests (ConcurrentModificationException / Endless BLADES count, different on each runner); the rerun of the
  same commit passed - flaky.
