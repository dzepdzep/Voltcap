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
- **682am - D389 (high, fixed):** every native Achievements page composed all its rows at once - a 120-achievement game froze the
  KONKR for one 1031 ms frame in Cinema (194 ms Arcade). rememberNativeTrophies now hands the page its first 10 rows at once and
  3 more each frame once the page has slid in (counts / totals / look-ups from the whole list; a refresh or re-sort never takes
  rows away). kt_big -> kt_big2: Cinema 1031 -> 132 ms, Arcade 194 -> 56 ms; the rest fill in ~40 ms frames for ~1.5 s (a Cinema
  row costs ~13 ms - still D386). Walk kbig13 x1.3: 24 Downs reach row 24, "120 FILE(S) TOTAL", Play same place, restore diff 0.
  CI green on af8ae8c (682al).

### 682aq - P2 performance, owner 23 Nov 22:43 (24 Nov night, autonomous) - QA app only, KONKR + RP Classic over USB
- **D389 - lazy rows (fixed in code, not final):** `NativeRows` (kit) - a LazyColumn with the page's own scroll cue and faded edges -
  on 25 native Achievements pages (Record crate stays eager - see 682aq8; Dock icon lines and Discover shelves worked out per row up front:
  `crateMarks`, `dockLines`, `discoverRuns`). Only on-screen rows are composed; counts, order and look-ups use the whole list.
  Robolectric LazyRows682aqTest (every native layout x 0 / 1 / 10 / 120 x x1.0 / x1.3, 224 cases, fails on 682am). Hardware
  `p2review.sh lazywalk` (140 quick Downs in bursts of ten, 140 Ups, B, reopen, early close): the first lazy build lost presses
  (KONKR stopped on row 30, RP Classic 52; the eager column reached the end). Diagnostics (682aq6) showed every press arrived and
  every move "succeeded" - moves made mid-glide landed on rows already passed. 682aq7: before each queued move the next row is put
  whole in view (at once while presses wait, gliding for one press); reaches Back on KONKR (Cinema, Crate, Resume, Bar) and RP
  Classic (Cinema, Resume); Up back to the top; reopen and early close clean, 0 crashes, restore diff 0.
- **D386 - page change:** Cinema's worst page-change frame 133.7 -> 32 ms (Tetris) and 84 -> 35 ms (120 achievements), KONKR
  atrace. Window text (StableFullText, reserve = false) now fits inside the Text's own measure (`StableFitAutoSize`): same 1 sp
  steps to the readable minimum, same extra line, same slide - StableFit682aqTest compares every line's end, width and bottom
  old vs new (128 cases, identical). Text appearance unchanged.
- **D379 - Arcade:** the cabinet sides used anti-aliased clipPath -> a CPU mask uploaded every glide frame. Now path fills and a
  shader-filled print (same picture). KONKR fair run (30-game category, no recording) 682al -> 682aq2: home navigation slow frames
  24.5 -> 0.6 % (502 frames), RenderThread p50 13.0 -> 7.5 ms. RP Classic: home navigation 12.2 -> 12.6 % (GPU-bound, GPU p50
  15 ms even when still), window walk 23.2 -> 5.3 %. 60 FPS not claimed; RP Classic GPU cost open.
- Builds: QA APKs qa682aq .. qa682aq7 (QA app only). Production Voltcap untouched (versionCode 80 on both devices).
- **682aq8 (CI on 682aq: NoStrayPlay682jTest CRATE failed):** lazy, the Record crate's ORDER stamp - the page's first focus, inside
  the first row - was not focusable after a quick L1 / R1 turn on a scrolled list; the turn's focus fell back to Play and the held A
  started the game. Crate goes back to its eager tracklist (D389 open for Crate). The rows' move queue no longer scrolls the list
  itself (Compose threw "uncancelled requests present" when a scroll cut a bring-into-view short): each move waits for the glide to
  end; while presses wait, rows come into view without a glide. NoStrayPlay 60 / LazyRows 216 / AchFirstRow 220 / WinFocus 448 green.
- **682aq9 / aq10:** D392 (Coverflow / shelf-family Details at x1.3) - the story, one D-pad stop taller than the panel, opened with
  the facts scrolled out and its last lines below, and Up / Down moved nothing; now Up / Down on the story scroll the page while
  there is more. Hardware x1.3 (kcw13 / rcw13): one Up shows the whole facts line, one Down the story's last line above Play.
  D389 move queue: the glide-wait version (aq8 / aq9) landed Downs back on the row above; aq10 keeps aq7's reveal-before-move,
  only when no other scroll runs - Back reached on KONKR (Cinema, Resume, Bar) and RP Classic (Cinema, Resume), Up back to the top,
  0 crashes, restore diff 0. D390: the first cover walk looped ~30 min (shared shell variable) - QA app only, stopped by hand.

### 682ar - owner 24 Nov 09:51 (option 2: Record crate first, then Task 1)
- **D393 (safety, P2 QA builds 682aq-aq10 only):** A after a quick L1 / R1 started the game on Record crate AND Dock (BAR).
  Root cause: both pages print their order key - the page's first focus - inside the lazy list's first row. A lazy list holds
  its place by key on a re-sort and keeps its scroll when the page is turned back to, so that row was not built; the window's
  8-frame request for the page's first focus failed, fell back to Play, and the A kept through the turn (682j hand-over) was
  delivered to Play. Fixes: (1) `NativeRows(head = true, order = ...)` - the list goes back to its head when the page is turned to
  again or re-sorted; the crate's head line with the stamp is the list's own keyed first item (a re-sort no longer rebuilds the
  lit stamp inside another track); (2) safety net - `PageHandoff.release(onPlay)` never hands a confirm press kept during a page
  change to Play. No delays added. Tests: ListHeadTurn682arTest (CRATE / BAR x1.0 / x1.3: fails 4/4 on 7110d79), PageHandoff682arTest,
  NoStrayPlay682jTest 60, LazyRows 224 (Crate lazy again), AchFirstRow 192, WinFocus 448.
- **D390:** winguard deadline on every key / tap (15 min default), watchdog; `p2review.sh storm` mode (bursts L1 R1 A ... in
  one input call inside the confirmed window, launch count after each).
### 726a - P2 class C, first layout: Circuit board (WinPagesChip.kt) - implemented, NOT owner-approved, HW NOT TESTED
- The chip's datasheet on the board (the board / trace / silkscreen colours of Board colours), its sections in the sheet's
  monospace silkscreen, the heading "SECTION n · NAME" under the game's name (after the old section has gone):
  CHARACTERISTICS (a PARAMETER / VALUE table between rules, values in whole words; the story as the FUNCTIONAL DESCRIPTION
  block, a D-pad stop), PIN STATES (each achievement a numbered pin P01 ..., an LED lit in the trace colour + HIGH when yours,
  hollow + LOW when not, "N PT"; "N / M PINS HIGH", the order key; A opens the whole text with LATCHED date / YIELD rarity;
  lazy rows), JUMPERS (J1, J2 ... - the cap bridges the two header pins while focused; removing ones red, second A).
  Motion: the section's signal runs along the trace (slides in a tenth from the trace side, from the other side going back).
  Info (PINOUT) unchanged. Play keeps its slot on every section (sceneKeySlots); L1 / R1 anywhere in the sheet.
- Tests (cloud, Robolectric): NativePagesChip726aTest 4/4 (fails before: no chipPageName / CHIP_SHEET not native); CIRCUIT
  added to AchFirstRow (6/6, + RPC), LazyRows (8/8), PlayPlace (2/2 + RPC 2/2), NoStrayPlay (2/2), WinFocus (8/8),
  ShelfText (24/24); WinPresentation / Visual426 / PopupNativeStorage / NativePages681a / Review682ag / UniformKeys green.
- Audit: class A 30 / 53 (+ Circuit), class B 1 (Disc station), class C 22. Hardware NOT TESTED (HW-27), not visually approved.

### 727a - P2 class C, second layout: Channel grid (WinPagesChan.kt) - implemented, NOT owner-approved, HW NOT TESTED
- The channel's own screens in the preview card's well (the grid's palette, wiiPaletteNow, and its rim colour), soft rounded
  cards and pills, a header naming the screen with the channel pager's dots (one per page, the shown one filled):
  CHANNEL INFO (the facts on one rounded info card, label over value in whole words; the story as the "About this channel"
  bubble, a D-pad stop), STAMPS (each achievement a rounded pill, a round stamp on the right - inked with a star when yours,
  an empty ring when not - with its points; "N of M stamps", the order as a pill; A opens the whole text, "Stamped <date>",
  how rare; lazy rows), SETTINGS (each option a big rounded pill in the window's key shape; removing ones red, second A).
  Motion: the next screen slides in from the side a quarter of the way, as a channel page does (right going forward, left
  going back), the old one slides an eighth the other way and fades. Info (the banner) and its More page of big keys unchanged.
- Found and fixed on the way (the scene now stays composed under its own pages): (1) the More page's focus effect ran on
  every page turn - B from Options landed on More's first key (Details); it now follows More being opened / closed only.
  (2) Two key rows (Info / pages) swapped on every turn; one row now, the pages' keys in the Info row's slots (Play in one
  place on every screen - PlayPlace). (3) A screen turned to with L1 / R1 has its More key as opener, which is not on the card
  while More is closed: the window waited ~8 frames with nothing lit and in an L1 / R1 storm the focus was lost (B then
  closed the window - NoStrayPlay682jTest). The closed More key now answers for the keys behind it: B lands on More at once.
- Tests (cloud, Robolectric): NativePagesChan727aTest 4/4 (fails before: no chanPageName / WII_PREVIEW not native); CHAN added
  to AchFirstRow + ShelfText; CHAN in WinFocus / PlayPlace (+ RPC) / NoStrayPlay / LazyRows (n=0 check accepts the card's
  "Start") green - 104 tests, 0 failures; Hierarchy529 (source shape updated to the one row), Batch494 / 496 / 506 / 507,
  Popup421, Visual426, WindowWordsSurvey682, CategoryStable394 green. Static checks PASS.
- Audit: class A 31 / 53 (+ Channel), class B 1 (Disc station), class C 21. Hardware NOT TESTED (HW-28), not visually approved.

### 728a - P2 class C, third layout: Timeline (WinPagesTime.kt) - implemented, NOT owner-approved, HW NOT TESTED
- The entry hanging from the year line keeps its cover, name and console line; on its pages the facts area becomes the page,
  each drawn as its own small vertical timeline in the accent (a thin line down the left, a node per stop), the page named
  beside a short page line (one tick per page, the shown one filled) after the old page has gone:
  CHRONICLE (each fact a filled stop, label over value in whole words; the story as THE STORY SO FAR at the line's end, an
  open node, a D-pad stop), MILESTONES (each achievement a stop - filled node + "Reached <date>" when yours, open ring +
  "Ahead" when not - badge, title, points "N pts"; "N of M milestones reached", the order as a pill; A opens the whole text
  and how rare; lazy rows), BRANCHES (each option a branch tick off the line to a rounded tag; removing ones red, second A).
  Motion: the next page comes along the line of time - from the right a fifth of the way going forward, from the left going
  back - as it fades up; none with Motion Off. Info (ENTRY) unchanged. The entry takes a steady height on its pages (max 480 dp)
  so the milestones scroll; Play keeps its slot (sceneKeySlots); L1 / R1 anywhere in the window. The line is drawn behind
  each stop (no intrinsic measuring of the rows' texts).
- Tests (cloud, Robolectric): NativePagesTime728aTest 4/4 (fails before: no timePageName / TIMELINE_ENTRY not native);
  TIMELINE added to AchFirstRow + ShelfText; with WinFocus / NoStrayPlay / LazyRows / PlayPlace (+ RPC) / WinPresentation /
  Batch508 / Visual426: 1,850 tests 0 failures (52 Timeline cases, none skipped); Hierarchy529 / CategoryStable394 /
  WindowWordsSurvey682 / NativePages681a / Batch2 / UniformKeys / NativeConfirm / PopupNativeStorage / Popup421 /
  NoArtCaption684: 597 tests 0 failures. Static checks PASS (no finding in the new file).
- Audit: class A 32 / 53 (+ Timeline), class B 1 (Disc station), class C 20. Hardware NOT TESTED (HW-28), not visually approved.

### 729a - P2 class C, fourth scene: Hero banner (Hero carousel + Hero tiles, WinPagesHero.kt) - implemented, NOT owner-approved, HW NOT TESTED
- The banner (key art behind the dark gradient, logo / name bottom left) keeps its look; on its pages the hero text column
  widens a little (62 -> 70 %) and the art darkens a shade, the name steps down to a smaller title (no reserved lines) and the
  banner's tab strip takes the info line's place: every page name in a row (whole names, wraps), the shown one bold with a white
  underline, after the old page has gone. Pages, in the banner's white-on-art type:
  THE FEATURE (the facts as a band of big key figures - value large over a small label, a thin accent rule each, whole words;
  the story as a pull quote with an accent bar, a D-pad stop), HALL OF FAME (a big "N / M" figure + "achievements unlocked",
  a slim progress bar in the accent, each achievement a billboard row - round badge, title, "Unlocked <date>" / "Locked",
  points pill; the order as a white pill; A opens the whole text and how rare; lazy rows), QUICK ACTIONS (big pill buttons in
  the banner's key style, white when focused; removing ones red, second A). Motion: the next page grows up out of the banner's
  lower left corner (where the hero text sits) as it fades in, nudged from the right going forward / the left going back; none
  with Motion Off. Info (SPOTLIGHT) unchanged. Play keeps its slot (sceneKeySlots); L1 / R1 anywhere in the window.
- Found by the tests on the way: at x1.3 the full-size title (3 reserved lines) and the info line left the Hall of Fame 0 px
  for its first achievement (AchFirstRow HERO big=true FAILED 6/6) - fixed by the compact heading on pages.
- Tests (cloud, Robolectric): NativePagesHero729aTest 4/4 (fails before: no heroPageName / HERO_BANNER not native); HERO +
  HERO_TILES added to AchFirstRow + ShelfText; with WinFocus / NoStrayPlay / LazyRows / PlayPlace / CategoryStable394 /
  NativePagesTime728a and others: 1,884 tests 0 failures (100 Hero cases, none skipped). Static checks PASS.
- Audit: class A 34 / 53 (+ Hero carousel, Hero tiles), class B 1 (Disc station), class C 18. Hardware NOT TESTED (HW-28), not
  visually approved.

### 730a - P2 class C, fifth scene: Memory card (MEMCARD_INFO, WinPagesMemCard.kt) - implemented, NOT owner-approved, HW NOT TESTED
- The save browser (grey screen, the save's icon turning big at the left, the title in yellow top right, the choices as glowing
  words along the bottom) keeps its look. Its pages are the browser's own screens on the dark glass panel at the right, in the
  browser's type (yellow titles, white words with the soft drop shadow, the choices' white-to-blue glow on the focused line):
  INFORMATION (each fact a label / value line, labels dim at the left, values wrapping in whole words; the story as the save's
  Notes, a D-pad stop), MEMORY BLOCKS ("N / M blocks saved" over one row of memory blocks - a lit yellow block per achievement
  you have, a hollow one for each still to get, 40 blocks at most where one stands for several, any progress lights one; each
  achievement a save entry: square badge, title yellow when yours, "Saved <date>" / "Empty block", points; A opens the whole
  text and how rare; the order a glowing word; lazy rows), COMMANDS (each option a word line with the choices' glow; removing
  ones red, second A as the browser asks before a delete). Heading: the page's name in yellow and a row of small blocks, one per
  page, the shown one lit, after the old page has gone. Motion: the next screen rises a little into the panel as it fades in,
  drifting in from the right going forward / the left going back; none with Motion Off. Info (SAVE) unchanged.
- The panel was a fixed 62 % box in the middle of the right half; it now runs from under the title to above the choices row
  (measured), and the facts make way on a page (they are on INFORMATION). L1 / R1 anywhere in the window.
- Found by the tests on the way (MEMCARD was in no text suite before): at x1.3 a long console name ("Super Nintendo
  Entertainment System") ran one line under the save's title and was cut there - also on the Info page, so a defect of the
  existing window, not only the new pages (ShelfText MEMCARD x1.3 FAILED 6/24); the card block keeps left of the title's column
  and the name wraps. A wrapping 40-block strip took two rows at x1.3 and left the first achievement 2 px short (AchFirstRow
  MEMCARD big n=40) - one row of blocks sharing the width.
- Tests (cloud, Robolectric): NativePagesMemCard730aTest 5/5 (fails before: no mcPageName / MEMCARD_INFO not native); MEMCARD
  added to AchFirstRow + ShelfText; with WinFocus / NoStrayPlay / PopupMore451 / Layouts330 / WindowWordsSurvey682 /
  CubeFacet676i: 581 tests 0 failures (146 skipped by the survey's own assumptions, none of the MEMCARD page cases). After merging
  682ar3: 730a + WinFocus / NoStrayPlay MEMCARD + PageHandoff682ar + ListHeadTurn682ar green; QA selftest OK; static PASS.
- Audit: class A 35 / 53 (+ Memory card), class B 1 (Disc station), class C 17. Hardware NOT TESTED (HW-28), not visually approved.
- **682ar2 / ar3 hardware (QA app, KONKR + RP Classic, synthetic games, 0 launches throughout):** `p2review.sh storm` on Crate and
  Dock - 7 same-instant bursts + 3 paced sequences each: never a launch; paced L1 R1 A steps the order on the key every time.
  Found on the way and fixed: (a) a burst ending on the page it started from never released the hand-over (it was keyed by the
  page) - the D-pad did nothing until the next turn: now keyed by every turn (HandoffPage.turns); (b) D391's real cause - the
  screensaver's wait counted only the home's own input: a game window's keys never reached it, so after 5 + 10 minutes the home
  behind the window went to its screensaver and black. `p2review.sh awake` (saver idle 40 s, QA only): 120 s of presses in a
  window - no screensaver; 60 s without - it starts. PASS both devices. (c) D394: the D390 watchdog outlived a finished run.
  Crate lazywalk (0 / 1 / 10 / 120 KONKR, 120 RP Classic): Back reached, Up to the stamp, early close clean. D390 on device:
  P2_MAX_S=25 -> ABORT "max run time", nothing pressed. HeadListSource682arTest guards every future page (head lists).
- **Task 1 hardware - Row Position (714a / 715a / 716a), Level 1 QA app qa682ar3, KONKR (BW0306N230000845) + RP Classic
  (MC94516AQF091001112) over USB, x1.0 and x1.3, every run "restore: OK", fatal 0, the owner's Voltcap untouched (pid unchanged / not running):**
  - row714 (manually placed round buttons come back when the row moves, via the QA-only R1 Row Position hook - no system quick
    settings): PASS on both, x1.0 + x1.3.
  - row716 (complex combination: game header + info / news + every category + a song in the bar's player; Top / Middle / Bottom):
    PASS on both, x1.0 + x1.3. The first x1.3 grade was INFRA: the scale change re-created the home and logged one extra rowcol line
    before step 1, so every step read its neighbour's line - grader fixed (682ar4), the same captures re-graded PASS.
  - play720 (715a: the D-pad reaches the added Play part in Edit Home; move, Done, Undo, Discard, restart, Reset this part, Edit Home
    Preview with the dock at TOP): first run FAIL r7 on both / both scales - the script pressed Up Up within 0.9 s, which Edit Home
    merges into ONE Undo step by design (a held D-pad), so Y undid both. Script now spaces the presses; re-run k720c/k720d/r720c/r720d:
    **PASS on both, x1.0 + x1.3.** Same x1.3 stray-line grader fix as row716.
  - Not covered by these runs: touch dragging in Edit Home (manual), Odin 2 Portal (connected, not used in this task).

### 682as - Priority 3: RP Classic Arcade GPU (24 Nov, investigation only - no app change yet)
Level 1, QA app qa682ar3, RP Classic (MC94516AQF091001112, USB), x1.0, synthetic games; every run "restore: OK", diff 0, 0 launches.
- **`p2review.sh arbisect`** (new): the Arcade home with one of its own look options off at a time (Marquee glow, side art,
  gameplay clip, scan lines, motion, all of them, Arcade hall), 6 s still + 12 home moves each, gfxinfo. Result: **no option
  changes the GPU cost** - GPU p50 15-16 ms per frame in every variant, still or moving (frame budget 16.7 ms); moving janky
  15-25 % in every variant (run-to-run noise about +-5 %). Arcade hall: 7.6 %.
- **The still home redraws every vsync** (framestats: 16.7 ms apart, nothing pressed). **`p2review.sh stilldiff`** (new, 5
  screenshots, nothing pressed): the only pixels changing are the attract screens' sliding lines - the big machine's console
  name ("PLAYSTATION 2", FullText 9 sp, one line, a few px too wide) and the small machine's title (FitText slideWhenCut). A
  slide (rule 11) keeps the whole window repainting at 60 fps (GPU 15 ms each) until HomeIdle.calm (30 s without input).
- **Moving:** atrace (tracehome ARCADE) - each L / R costs one 34-44 ms main-thread frame (Recomposer 34-39 ms, Compose:recompose
  ~20 ms of it; VC:arcBig / arcMini ~1 ms each), RenderThread DrawFrames ~18 ms average (88 FillRect + 48 AA convex path ops a
  frame). So the moving jank is one CPU hitch per press plus a GPU that is at the frame budget with nothing on.
- Open (owner to choose): A) let the attract-screen lines fit (shrink to the readable minimum) so a still Arcade home stops
  repainting; B) cut the per-move recomposition; C) find the full-screen overdraw (QA-only part toggles, new build); D) leave it.
### 731a - P2 class C, sixth scene: Cardboard box (BOX_SHEET, WinPagesBox.kt) - implemented, NOT owner-approved, HW NOT TESTED
- The insert sheet (a typed inventory slip on cream paper, taped at its corner, beside the case lifted out of the box; actions
  rubber-stamped boxes) keeps its look. Its pages are the slip's other sheets, typed on the same paper in the same typewriter
  face between the title and the stamps: CONTENTS (the Details stamp's own name; each fact a numbered item on a dotted leader,
  values wrapping in whole words; the story as REMARKS, a D-pad stop - the slip's "THE WHOLE STORY: CONTENTS" now lands on it),
  CHECKLIST ("N OF M CHECKED" over one row of typed boxes - filled for yours, empty still to get, 32 at most where one stands for
  several; each achievement a checklist line: typed [X] / [ ], square badge, title, "CHECKED <date>" / "NOT YET", points; A opens
  the whole text and how rare; the order a small stamped box; lazy rows), SERVICE (each option a typed line in a ruled box,
  stamped in the accent when focused; removing ones in the slip's red ink, second A with a VOID? confirmation). Heading: "SHEET n
  OF m", the page's name and a row of small sheet tabs, the shown one filled, after the old page has gone. Motion: the next sheet
  feeds up from under the one before as it fades in, a little from the right going forward / the left going back; none with
  Motion Off. The slip's own header now says INSERT · INVENTORY on the first page (it said CONTENTS, the name of its Details
  stamp - two pages had the same name). L1 / R1 anywhere in the window.
- Found by the tests on the way (BOX was in no text suite before): the first page's end-aligned fact values ('Never played',
  '140 / 160') were laid out across the whole 308 px room and reported 212 px past their own box (ShelfText BOX FAILED 12/24
  main page); the dotted leader already puts the value at the right, so the value is no longer end-aligned. The text suite could
  not reach the box's Options / More (round stickers named only by their icon's description) - it now matches an icon-only
  control by its description, and "contents" as a Details word.
- Tests (cloud, Robolectric): NativePagesBox731aTest 6/6 (fails before: no boxPageName / BOX_SHEET not native); BOX added to
  AchFirstRow + ShelfText; ShelfText suite 846 tests 0 failures; after merging 682ar4: NativePagesBox731a + HeadListSource682ar +
  AchFirstRow + WinFocus + NoStrayPlay682j + PageHandoff682ar + ListHeadTurn682ar + PopupMore451 + Layouts330 + Popup421 +
  WindowWordsSurvey682: 1,306 tests 0 failures (166 skipped by the suites' own assumptions). Static PASS.
- Audit: class A 36 / 53 (+ Cardboard box), class B 1 (Disc station), class C 16. Hardware NOT TESTED (HW-28), not visually approved.
- **731a CI fix (P2 branch was RED on 911681e, run 45393737656 - PlayPlace682ab / PlayPlaceRpc682ab MEMCARD x1.0 + x1.3, 4 of
  4,139):** 730a's Memory card pages kept Play in the first half on Details but alone in the middle on Options. The page row now
  has two slots (PAGE_SLOTS, Play the first) on every page - Memory card and the Cardboard box sheets. PlayPlace could not reach
  the box's CONTENTS stamp (it then compared the first page's Play) - "contents" added to its Details words. PlayPlace +
  PlayPlaceRpc 151 / 0 (incl. BOX, MEMCARD); AchFirstRowRpc682r + LazyRows682aq 315 / 0; ShelfText + AchFirstRow 1,213 run
  with only those 4 PlayPlace BOX failures before the word fix.

### 732a - P2 class C, seventh scene: File select (FILE_CARD, WinPagesFile.kt) - implemented, NOT owner-approved, HW NOT TESTED
- The turned-over save file keeps its chunky bevelled block, outlined capitals, yellow lit state and dark inset panel; its pages
  are the file's own screens on that inset: DETAILS (each fact a fat rounded slot - yellow label, white value - and the story in
  its own slot, a D-pad stop), STARS (big star + "N / M STARS", a row of little stars yellow / outlined, one standing for several
  past 24; each achievement a rounded row - star, round badge, outlined title, "GOT IT <date>" / "NOT YET", "+N"; A opens the whole
  text + rarity; lazy rows; order as a small round button), OPTIONS (fat round slots like the file's buttons, yellow when lit;
  removing ones red with "ERASE? PRESS A AGAIN" - second A). The top row names the page in outlined capitals with one round pip
  per page (shown one yellow) after the old page has gone; the next screen pops in from 94 % a little from the side; none with
  Motion Off. Before: the shared page body inside the inset and the shared page name in the top row.
- Found by the tests: Play moved between pages (PlayPlace682ab FILESELECT x1.0 / x1.3: wide on Details, short in the middle on
  Options) -> a page's button row keeps two slots, Play the first (as Box / Memory card); at x1.3 on the tight file the star row
  left the first achievement 62 of 73 px (AchFirstRow FILESELECT big=true) -> on a tight file with big text the count line says
  it alone, and the inset's page area has no second padding.
- Tests (cloud): NativePagesFile732aTest 5/5 (new; fails before: no filePageName / FILE_CARD not native); FILESELECT added to
  AchFirstRow + ShelfText; FILESELECT cases of AchFirstRow / AchFirstRowRpc / ShelfText / PlayPlace / PlayPlaceRpc / WinFocus /
  NoStrayPlay / LazyRows + NativeConfirm / PageHandoff / ListHeadTurn / UniformKeys / PopupMore451 / Layouts330 / WindowWordsSurvey
  / CubeFacet 628 tests 0 failures (146 WindowWordsSurvey skips, as before); NativePages* + PopupNativeStorage + DiscTracks green.
  Static PASS. Hardware NOT TESTED (HW-28 extended), emulator review not run, not visually approved.

### 682as - Arcade A (owner 24 Nov 14:54: fix the continuous redrawing first) - implemented, hardware-measured, NOT visually approved
- **Change:** the attract screens' words (title, console line) and the marquee signs' fallback name wrap at whole words and size
  down to the tube's smallest size; only the machine you're on may slide (a name that fits nowhere at all - it rests with the home
  at 30 s, as before); the others stay still. The console line steps 9 -> 7 sp before it wraps ("PLAYSTATION 2" now one line). A
  far tube that cannot hold every word even condensed (never below half width) shows its first whole lines - never half a word;
  its sign right above carries the whole name, and the machine shows it whole once you move onto it. FitText: `wholeWords`,
  `stillWhenCut` (condensed, no ellipsis). QA-only A/B switch `--es voltcap_test_arattract legacy|new` (isolated QA app, session).
  Tests: ArcadeTube682asTest (legacy slides - fails as the old code; new: no slide on tubes you're not on, no split word, the big
  tube whole, the console line one line), ChanPlateOneLine678r updated (FitText keys).
- **Hardware (Level 1, QA app qa682as2 = 77fdd6e, RP Classic MC94516AQF091001112 USB, x1.0, the 30-game category on Shadow of
  the Colossus, no recording; `p2review.sh arab` - legacy / new interleaved twice in the same build; restore OK, diff 0, 0 launches):**

  | phase (after a press) | legacy | new |
  |---|---|---|
  | still 11-19 s (home awake, nothing moving) | 58.8 / 58.5 frames/s, GPU p50 16 ms, p50 32-34 ms | **0 frames** (both runs) |
  | still 1-9 s | 55.9 / 56.2 frames/s, slow 4.5 / 4.7 % | 19.9 frames/s, all slow (p50 42 ms) - the status bar's charging light (USB charging; Update 493/511 light, settles 10 s after input), not Arcade |
  | 12 home moves | 561 / 598 frames, slow 18.2 / 16.2 %, p50 31-32, p95 77-85, p99 105-113 ms, GPU p50 15-16 | 450 / 456 frames, slow 25.8 / 24.6 %, p50 31-32, p95 89-93, p99 105-113 ms, GPU p50 14 |

  Moving is unchanged in absolute terms (slow UI frames 45-55 vs 48-50, slow draws 97-102 vs 112-116); the percentage is higher
  only because the legacy run's denominator included ~60 cheap marquee frames a second. 60 FPS not claimed: a home frame on the RP
  Classic costs GPU 14-16 ms plus ~13 ms issuing (atrace), and each move still has its main-thread hitch (Arcade B, next).
- Screens: rsd4 (awake_1.png) - big tube "SHADOW OF THE COLOSSUS / PLAYSTATION 2" whole, still; small tube "SHADOW OF / THE".
- Found on the way (not changed): the charging light redraws the whole window ~20-30 times a second for 10 s after each press
  while charging - on the RP Classic each such frame is a full 40 ms window redraw.
- **Arcade B - profile (24 Nov, RP Classic, QA app qa682as_trace2 = compose-tracing build + VC: sections, Level 1, restore OK):**
  correction first - the earlier "35-44 ms stall per move" came from a trace started on Tetris, a one-game category (the presses
  did not change game, as in 682al's correction). On the 30-game category a game-to-game move is one ~100 ms main-thread frame
  (plain build agrees: arab moving p95 77-93 ms, p99 105-113 ms). Per move: composition ~54 ms - the screen's content body
  (LauncherScreenContent, which collects the whole UI state) ~17 ms of its own, the Arcade showcase ~15 ms (the big machine's new
  content 7 ms, the info 3 ms, row / category ~1 ms each), applying changes 4-6 ms; layout ~37-46 ms - sub-compositions of
  BoxWithConstraints (the showcase itself, the row, FitText) ~17 ms and ~20 text measurements of 0.7-2 ms; draw recording ~10 ms.
  No single hot spot; next: cut the per-move work of the screen body and the showcase's layout-time composition.
### 733a - P2 class C, eighth scene: Case flow (CASE_MEDIA, WinPagesCase.kt) - implemented, NOT owner-approved, HW NOT TESTED
- The case opened into its media keeps its dark glass stage, black glass well with a shine, accent-edged name band and glossy
  round-ended pills reflected on the floor; its pages take the picture's place in that glass well, like the menu of the disc
  inside: DETAILS (each fact a glass strip with the name band's accent edge - small spaced label, white value - and the story in
  its own glass panel, a D-pad stop), EXTRAS ("N of M unlocked" over a glossy accent bar with its floor reflection; each
  achievement a glass slide - badge with its reflection, title, "Unlocked <date>" / "Locked", a points pill; A opens the whole text
  + rarity; lazy rows; the order a small glossy pill), SETUP (glossy round-ended option pills, accent gloss when lit; removing ones
  red with "Press A again to remove" - second A). Heading: the accent edge, the page's name and a round-ended pill per page (the
  shown one in the accent) after the old page has gone; the next page flows in like the next case (from the side, from 92 %);
  none with Motion Off. Before: the shared page body squeezed into the picture's band (110-300 dp).
- On its pages the window keeps one size (the whole stage), so the pills - two slots, Play the first - stay where they are.
- Test fix found on the way: PlayPlace682ab's More matcher missed a More key that is labelled and described ("more more" - Case
  flow), so it compared Info's Play with Details' and never reached Extras / Setup; it now reads More as AchFirstRow does.
- Tests (cloud): NativePagesCase733aTest 4/4 (new; fails before: no casePageName, CASE_MEDIA not native); CASEFLOW added to
  AchFirstRow (+ PlayPlace / PlayPlaceRpc via NATIVE) + ShelfText; AchFirstRow / ShelfText / PlayPlace* / WinFocus / NoStrayPlay /
  NativeConfirm / PageHandoff / ListHeadTurn / UniformKeys / PopupMore451 / WindowWordsSurvey / Layouts330 / Pickups672 / Batch506 /
  NativePages* 2286 tests 0 failures. Static PASS. Hardware NOT TESTED (HW-28 extended), emulator review not run, not approved.
### 734b - P2 class C, ninth scene: Orbit deck (ORBIT_TERMINAL, WinPagesOrbit.kt) - implemented, NOT owner-approved, HW NOT TESTED
- The disc flown off the orbit, its chrome keys fanned on an arc and the dark glass console (chrome edge, scan lines, spaced glow
  caps, mono readouts, numbered track cells) stay as they are; the pages are the console's own screens in the same readout
  language: DATA (each fact a readout - spaced glow label, bold mono value - then the story as a LOG panel with a chamfered edge,
  a D-pad stop), TRACKS ("TRACKS N / M" + the lit-cell strip; each achievement a track - its numbered cell lit when earned, the
  badge, the title, a mono "UNLOCKED <date>" / "LOCKED" line, "+N PTS"; A opens the whole text + rarity; lazy rows; the order a
  chamfered key), COMMANDS (each option a "▸ LABEL" mono command line lit in the orbit's colour; removing ones amber-red with
  "PRESS A AGAIN TO REMOVE" - second A). Heading: the page's code in glow caps, a chamfered cell per page (the shown one lit) and
  the breathing READY lamp, after the old page has gone; the next screen rolls up like a terminal (from below forward, from above
  back); none with Motion Off. The CMD line stays under the pages. Before: the shared page body inside the console.
- L1 / R1 on the window's root (wide: arc keys; narrow: key row). On a page the arc keeps PAGE_SLOTS places before Back and the
  narrow row two slots, so Play stays put on Options (PlayPlace682ab caught 27 px / 51 px before the fix).
- Existing defect fixed (ShelfText x1.3, narrow console): a long console name (SUPER NINTENDO ENTERTAINMENT SYSTEM) ran under
  READY; the console name now has its own line under DISC ... READY.
- Test fixes found on the way: PlayPlace682ab's Play matcher missed a key that is labelled and described ("play play" - Orbit's
  narrow key), so x1.3 compared nothing; NoStrayPlay682j named the focus by its text only, so on Orbit's icon-only arc keys the
  walk "reached Play" on any key - a key with no text is now named by its merged description.
- Tests (cloud): NativePagesOrbit734bTest 4/4 (new; fails before: no orbitPageName, ORBIT_TERMINAL not native); ORBIT added to
  AchFirstRow (+ PlayPlace / PlayPlaceRpc via NATIVE) + ShelfText; PlayPlace* all layouts, NoStrayPlay682j all 40 layouts, every
  ORBIT / Orbit case (AchFirstRow, ShelfText, WindowWordsSurvey, WinFocus, LazyRows, OrbitSpin, OrbitKeyLabel) + PopupNativeStorage
  + Layouts330: 0 failures. Static PASS. Hardware NOT TESTED (HW-28 extended), emulator review not run, not approved.

### 682at - charging light (owner 24 Nov 17:50, option 2, visual quality kept) - implemented, hardware-measured, NOT visually approved
- **Why presses restarted it:** Update 511 keyed the light (PlainBattery) and the Volt gauge's bolt (the shared focus pulse,
  50 ms steps = 20 updates/s) on `HomeIdle.settled` - 10 s after the last input they rest, and every press wakes them for another
  10 s. **Now:** feedback only - charging starting, the level changing while charging, Voltcap coming back to the front: two
  sweeps / breaths at the full frame rate (the plain light was 30 updates/s before), then on to the still place (light at 0.55,
  bolt at its steady glow) and still until one of those changes. Draw-only in its own layer as before (no recomposition). Display
  only - ChargeLimiter, the charge limit, bypass and battery reporting untouched. A/B switch `voltcap_test_chargelight legacy|new`.
  ChargeLight682atTest (legacy restarts on every press - fails as the old code; new: once per charging start / level change).
- **Hardware (Level 1, QA app qa682at = 45857d6, `p2review.sh chab`, the QA app's battery hook showing 77 % -> 78 % charging on both,
  same build, legacy / new twice, Arcade home on Shadow of the Colossus, x1.0, restore OK, diff 0, 0 launches):**
  RP Classic 1-9 s after a press: legacy 19.9 frames/s (every one a ~42 ms whole-window frame) -> **new 0 frames** (both runs).
  KONKR: 60.4-60.6 frames/s in both - the KONKR QA profile's home keeps drawing for other reasons in that window (not the battery:
  the only pixels changing between the light shots are the bolt's); the light itself no longer restarts there either (same build,
  same code path). Appearance: kch2 / rch2 `CH_*_light_1..6` + `_rest` - the same bolt, same brightness range; new comes to its
  steady glow after two breaths. (The Voltcap-row runs kch / rch are invalid for this: its theme scene draws 60 fps by itself.)
- Open: what draws 60 fps on the KONKR's still Arcade home for ~10 s after a press (decorative effects of its look) - tracked.
### 735a - P2 class C, tenth scene: Airy home (AIRY_PANEL, WinPagesAiry.kt) - implemented, NOT owner-approved, HW NOT TESTED
- The floating white 1999 panel (soft rounded corners, pale blue-grey well, the cover in its rounded frame, the name in the
  light face, the pixel LCD, big round keys with their word under them) stays as it is; its pages take the panel's right column
  in the same soft language: ABOUT (each fact a white round-ended bubble - small accent label, the value in the dark ink - then
  the story in a white speech bubble with a little tail, a D-pad stop), STARS (one line: "★ N of M", a bubble meter filled in the
  accent for what you have - as many bubbles as the room holds, in proportion past a dozen - and the order as a round pill whose
  words slide; each achievement a white rounded card - the badge in a round bubble, the title, "Got it <date>" / "Still to get",
  the points in a small round accent bubble; A opens the whole text + how rare it is; lazy rows), SETTINGS (big round-ended
  pills with a round dot at the start, filled when lit; removing ones soft red with "Press A again to remove" - second A).
  Heading: the page's name in the light face and a dot per page (the shown one a longer accent pill) after the old page has
  gone; the next page floats up like a bubble (from below forward, from above back, from 97 %); none with Motion Off.
- L1 / R1 on the panel's root. On a page the round keys keep two page slots (Play the first), so Play stays put (PlayPlace
  AIRY x1.0 / x1.3 identical on all three pages). A tight panel (640 x 427, the KONKR) takes the small round keys on its pages,
  a smaller heading and closer edges - AchFirstRow caught the first star half a card (16 px, then 46 px of 73) at x1.3 before.
- Tests (cloud): NativePagesAiry735aTest 4/4 (new; fails before: no airyPageName, AIRY_PANEL not native); AIRY added to
  AchFirstRow (+ PlayPlace / PlayPlaceRpc via NATIVE) + ShelfText; AchFirstRow* / ShelfText / PlayPlace* AIRY, Airy477, WinFocus
  AIRY, NoStrayPlay682j all layouts, WindowWordsSurvey682, NativeConfirm, PopupNativeStorage, Layouts330, UniformKeys,
  NativePages*: 0 failures. Static PASS. Hardware NOT TESTED (HW-28 extended), emulator review not run, not approved.
- Audit: class C 11 scenes done (own designs 40 / 53).

### 682au - Arcade B (owner 24 Nov 19:23: reduce the ~100 ms game-change stall) - IN PROGRESS, no frame-level gain yet
- **Profile (RP Classic, compose-tracing QA builds qa682au_cp / cp2 with timestamp marks, 30-game category, 12 moves, Level 1):**
  per game change ~97-101 ms on the main thread: composition ~50 ms (the screen's content body - LauncherScreenContent, which
  collects the whole UI state, so every move re-runs its ~7300 lines - ~15 ms spread evenly in small pieces, no hot spot; the
  Arcade showcase: big machine's new content 6 ms, info 2.6 ms, row ~1.4 ms; applying changes 5 ms), layout ~35 ms (layout-time
  sub-compositions 13 ms, text measurement 11 ms), draw recording ~9 ms. FitText's size search: **10.9 ms per change** (7 texts).
- **Change:** FitText's step-down now gallops then halves (`fitStepSize`, the same step as before - FitSearch682auTest checks every
  threshold). Same-build A/B (`p2review.sh fsab`, qa682au, legacy / new twice, 24 moves each): p95 121 / 105 -> 101 / 101 ms, p99
  150 / 125 -> 125 / 121 ms, slow UI frames 96 / 69 -> 79 / 69, slow 14.8 / 10.4 % -> 12.0 / 10.8 % - **within run-to-run noise:
  no measurable frame-level gain claimed.** The remaining cost is structural (the whole screen body re-composing on every focus
  step); next step proposed: keep the focused index out of LauncherScreenContent's own reads (its children read it), measured the
  same way.

### 682au - Record crate / Dock (BAR) safety verification (owner 24 Nov 19:23 item 2) - HARDWARE PASS
QA app qa682au (28ab750, Level 1), KONKR BW0306N230000845 + RP Classic MC94516AQF091001112 over USB, x1.0 and x1.3
(ksf10 / ksf13 / rsf10 / rsf13, `p2review.sh safety TAG SCALE "CRATE BAR"`), the 120-achievement synthetic game (Crazy Taxi):
Achievements page, A on the order key twice (Crate: Set order -> Still to get first -> Earned first; Dock: Earned first -> Latest
earned / Most points / Rarest first), after each re-sort a deep scroll and quick bursts 'L1 R1 A', 'R1 L1 A', 'L1 R1 A A' (12 per
run) - **0 launches**; rapid B B, the window reopened at once, 'R1 R1 A' - **0 launches**; a fresh window, focus on Play, one
deliberate A - **exactly 1 launch recorded** (the QA launch block) - all 8 layout x handheld x scale cases. Restore OK, diff 0.
The plain storm (fast + paced) passed earlier on both (682ar2 / ar3). Regression tests kept: ListHeadTurn682arTest,
PageHandoff682arTest, HeadListSource682arTest (fail on the old implementation). D393: hardware-verified.
Task 1 Row Position (714a / 715a / 716a) hardware results stay as recorded under 682ar4 (accepted by the owner 24 Nov).
### 736a - P2 class C, eleventh scene: Bubble home (BUBBLE_CARD, WinPagesBubble.kt) - implemented, NOT owner-approved, HW NOT TESTED
- The LiveArea card (the game's art behind, the round bubble and name, the white Start plate and round-ended white keys down the
  left, the white info panel on the right) stays as it is; its pages take the white panel in the same language: INFO (the facts
  as label / value lines with hairlines between, then the description in a pale blue-grey box - a D-pad stop), AWARDS (one line:
  the gold cup, "N / M", a slim round-ended meter in the card's blue, the order as a round-ended white key whose words slide; each
  award a round-ended white key - badge in a round bubble, title, "Earned <date>" / "Not yet", the points in a gold ring; A opens
  the whole text + how rare it is, the key rounding into a card; lazy rows), SETTINGS (round-ended white keys with the card's own
  white-to-grey face and blue ring; removing ones write in the warning orange, "Press A again to remove" - second A).
  Heading: the page's name and a bubble per page (the shown one filled in the blue with a white ring) after the old page has
  gone; the next page swells out like a bubble from the heading's corner (from 94 %); none with Motion Off. The page panel was a
  dark see-through box around the shared pages - now white like the card's first page.
- L1 / R1 on the card's root. The keys stay down the left (Play the first; the tools only on the first page, as before), so Play
  stays put (PlayPlace BUBBLES x1.0 / x1.3 + Rpc PASS). A short card (under 380 dp) takes a smaller heading and closer edges.
- Tests (cloud): NativePagesBubble736aTest 4/4 (new; fails before: no bubblePageName, BUBBLE_CARD not native); BUBBLES added to
  AchFirstRow (+ PlayPlace / PlayPlaceRpc via NATIVE) + ShelfText; AchFirstRow* / PlayPlace* / NoStrayPlay682j / ShelfText BUBBLES,
  WindowWordsSurvey682, PopupMore451, Layouts344, Batch505, Batch489, Visual426, PopupNativeStorage, NativeConfirm, UniformKeys,
  NativePages*: 694 tests, 0 failures. Static PASS. Hardware NOT TESTED (HW-28 extended), emulator review not run, not approved.
- Audit: class C 11 scenes done (own designs 41 / 53).
### 737a - P2 class C, twelfth scene: Pocket camera (CAMERA_MENU, WinPagesCamera.kt) - implemented, NOT owner-approved, HW NOT TESTED
- The cartridge menu (the chunky pixel frame in the picked palette's four shades, the dithered picture and the console / time on
  the left, the name over a one-pixel rule on the right) stays as it is; its pages take the room under the name on the light
  screen, in the same pixel font and shades: DATA (LABEL / VALUE lines, dotted pixel rules between, then the story in a one-pixel
  frame headed ABOUT - a D-pad stop, the head turns dark while you are on it), ALBUM (one line: the pixel cup, "03/12", a meter of
  pixel cells - one earned always shows a cell, full only when all are earned - and the order as a menu row whose words slide;
  each award a numbered album entry "NO.01" with its badge in a square pixel frame, the title, "GOT <date>" / "NOT YET", the
  points; the row you are on dark with the arrow cursor, as on the menu; A opens the whole text + how rare it is; lazy rows),
  SETUP (menu rows with the arrow cursor; removing ones marked "!" and ask "PRESS A AGAIN TO REMOVE" - second A).
  Heading: the page's name and a pixel square per page (the shown one filled) over a dotted rule, after the old page has gone.
  Motion: the page draws itself down the little screen in the window's own 12 switch-on steps (cameraStep), a dark scan line at
  its front; none with Motion Off. Was: the shared pages in a dark box.
- L1 / R1 on the menu's root. The keys stay one uniform column under the page (674e), so Play stays put; on the pages the name
  keeps one sliding line so the page gets the room (it had up to three).
- Reuse: the existing pixel cup / cursor icons (PX_TROPHY now internal), pixelIcon, the menu's pixel unit and font sizes.
- Tests (cloud): NativePagesCamera737aTest (new; fails before: no cameraPageName, CAMERA_MENU not native); CAMERA added to
  AchFirstRow (+ PlayPlace / PlayPlaceRpc via NATIVE) + ShelfText. Results in SUMMARY 737a. Hardware NOT TESTED (HW-28 extended),
  emulator review not run, not approved.
- Audit: class C 12 scenes done (own designs 42 / 53).
### 738a - P2 class C, thirteenth scene: Panel dashboard (PANEL_DETAIL, WinPagesPanels.kt) - implemented, NOT owner-approved, HW NOT TESTED
- The swung-round panel (the wide art strip with the name and the bar in the dashboard's colour, the commands as flat rows down
  the left lit in that colour, the cover and facts on the right) stays as it is; its pages take the right side in the same flat
  panel language and the picked palette (Green / Silver / Graphite / Theme): OVERVIEW (each fact a flat tile with a bar in the
  dashboard's colour down its left, label over value; then the story in a flat panel headed ABOUT under a colour bar - a D-pad
  stop), ACHIEVEMENTS (one line: the cup, "N / M", a flat meter of 10 segments - one earned always lights one, all only when every
  one is earned - and the order as a flat tile whose words slide; each achievement a flat row: square badge, title, "Unlocked
  <date>" / "Locked", the points; the row you are on lit like the commands; A opens the whole text + how rare it is; lazy rows),
  MANAGE (flat rows like the command column; removing ones in the warning orange, "Press A again to remove" - second A).
  Heading: the page's name and a flat segment per page (the shown one long, in the colour) over a hairline, after the old page
  has gone; the art strip now says the console alone (the page name was said twice). Motion: the next page slides along from
  the side it comes from, like the next panel of the row coming round (fading in); none with Motion Off. Was: the shared pages
  in a thin frame.
- L1 / R1 on the panel's root. The commands keep their column and weights on every page (Play first, the tools only on the first
  page), so Play stays put (PlayPlace PANELS x1.0 / x1.3 + Rpc PASS).
- Tests (cloud): NativePagesPanels738aTest 4/4 (new; fails before: no panelPageName, PANEL_DETAIL not native); PANELS added to
  AchFirstRow (+ PlayPlace / PlayPlaceRpc via NATIVE) + ShelfText; with AchFirstRow*, PlayPlace*, NoStrayPlay682j, ShelfText,
  WinFocus, Layouts344, WindowWordsSurvey682, NativeConfirm, PopupNativeStorage, UniformKeys673, NativePages*: 2,575 tests, 0
  failures. Static PASS. Hardware NOT TESTED (HW-28 extended), emulator review not run, not approved.
- Audit: class C 13 scenes done (own designs 43 / 53).
### 739a - P2 class C, fourteenth scene: Rental shop (SHOP_TAG, WinPagesShop.kt) - implemented, NOT owner-approved, HW NOT TESTED
- The yellow rental tag hanging beside the case you took off the shelf (title in heavy black capitals, barcode, tear-off, black
  punched keys lit in the shop's neon) stays as it is; its pages are printed on the tag under the title in the same ink:
  CATALOGUE CARD (the tag's own CATALOGUE CARD key opens it: each fact label over value in black capitals between dashed rules,
  then the story under a black bar headed THE STORY - a D-pad stop), REWARDS CARD (the shop's member punch card: cup, "N / M",
  ten punch holes - one earned always punches one, all only when every one is earned - and the order as a black stamp; each
  achievement a line with its square badge, the title in capitals, "EARNED <date>" / "NOT YET", "+N PTS"; the line you are on in
  the neon; A opens the whole text + how rare it is; lazy rows), COUNTER (the choices as black punched rows; removing ones in the
  tag's orange, "PRESS A AGAIN TO REMOVE" - second A). Heading: the page's name in black capitals (wraps, never cut) and a punch
  hole per page (the shown one punched), after the old page has gone. Motion: the next page drops in from above like the next
  card slipped onto the tag's string (fading in); none with Motion Off. No made-up rental period, due date or price (update 346).
  Was: the shared pages in a black frame with "▸" words under it.
- L1 / R1 anywhere on the tag. On its pages the tag takes its whole height and its keys are one row of punched keys with two
  page slots, Play (RENT & PLAY) first, so Play stays put: PlayPlace SHOP x1.0 / x1.3 found Play at the same place on all three
  pages (it could not find the shop's RENT & PLAY / CATALOGUE CARD keys before - the test now knows both words).
- Tests (cloud): NativePagesShop739aTest 4/4 (new; fails before: no shopPageName, SHOP_TAG not native); SHOP added to AchFirstRow
  (+ Rpc, PlayPlace via NATIVE) + ShelfText (its details matcher learned "catalogue"); with PlayPlace*, NoStrayPlay682j, Layouts344,
  WindowWordsSurvey682, Batch505, PopupShade461, Pickups672, ShopTag673, NativeConfirm, PopupNativeStorage, WinFocus, UniformKeys673,
  NativePages*: 2,639 tests, 0 failures. Static PASS. Hardware NOT TESTED (HW-28 extended), emulator review not run, not approved.
- Audit: class C 14 scenes done (own designs 44 / 53).
