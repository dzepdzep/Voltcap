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

### Status board (owner 18 Nov: implementation, hardware verification and visual approval kept apart)
| Layout | Implemented | HW KONKR | HW RP Classic | Owner visual approval |
|---|---|---|---|---|
| Record crate, Classic, Console hub, Resume & discover | yes (682s fixes) | x1.3 pre-682s only | x1.3 pre-682s only | NO - refinements queued |
| TV room | yes + R27 figure (682u) | x1.3 pre-682u only | x1.0 + x1.3 PASS (0ac1f95) | NO |
| Game magazine, Cinema, Arcade | yes (682r) | x1.3 (Arcade 682r2) | x1.3 (Magazine pre-682r2) | NO |
| Cube | yes (682r) | x1.3 first walk | x1.3 first walk | NO |
| Spotlight | yes (682t) | NOT TESTED | x1.0 + x1.3 PASS (0ac1f95) | NO |

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
