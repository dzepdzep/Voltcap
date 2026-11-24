# VoltCap handoff (read first, update last)

**18 Nov ~10:45 Berlin - OWNER DECISIONS 08:11 + NEW RULE 09:44 (newest; before everything below).**
- RESCAN FIX (`claude/v0674-rescan-fix`, 0c49f03, Verify 310 GREEN on 3ec7e45): stays on its branch until HW-25 passes. HW-25
  hardware: KONKR (GT78, Android 12, 36-game QA library) + RP Classic (Android 14) rescan681r.sh 18/18 PASS each (only game of a
  console deleted -> gone, console no category, All selected; twice; restart; one of two; whole game folder; re-added same
  identity; QA-app crashes 0). Your own collections kept empty and refilled on re-add (unit). SD card physically out: NOT TESTED
  (owner "not now") - rescan681r.sh run TAG 453E-4658 on the RP Classic waits for the card. No console-category feature invented.
  Ready for the owner's merge GO apart from that one step.
- P2 (`claude/v0674-native-pages`, Verify 310 GREEN eb3eb91): batch 1 NOT visually approved. RELEASE-BLOCKING Play defect found
  and fixed (682a): an A within ~30-200 ms of an L1/R1 turn onto Details started the game on all 5 layouts - 300 ms page-turn
  guard, NoStrayPlay682aTest 10/10 (fails without the guard). KONKR popup gate (pop529 + popclip, build qa-682c-2ca480f-ci)
  x1.0 PASS 5/5 and x1.3 PASS 5/5, 0 launches. Armed-confirmation capture: review 44264372593's "armed" shots were the Info page
  (script pressed the B Back hint) - p2review 682f fixed (A only on a focused "Hide from library", claimed only from the dump);
  re-review 44272567211 running. TV room's armed state still NOT captured. Next P2 layouts only after the owner sees batch 1.
- 682e-i UPDATE (owner: "whatever looks best" at the list's end -> exact alignment kept; "yes, all three"): Bar (tab on Play's
  column, was 2 dp vs 6), Library (lit tab leads its strip after L2, WholeChipRow leadFocused), Dash (labels + Play on one inner
  column DASH_INNER, KONKR had measured -8 dp) done on `claude/v0674-play-anchor` a4382ac (Verify 310 GREEN). KONKR kc682
  anchorcheck PASS 6/6 (Dash, Bar, Classic x All / Xbox 360); Library adapted (review by eye); Tiles / Hero tiles no home Play.
  P2 682h: an armed / grown native row now brings itself into view (confirmation line was cut; NativeConfirm681Test fails
  without, 8/8 with). Emulator re-review 44280831400 running.
- NEW SYSTEM RULE 682e (owner 09:44): the home's Play button and the selected category chip share one anchor - own branch
  `claude/v0674-play-anchor` (cd5c921, Verify 310 GREEN). HomeAnchor.kt; anchorcheck.py; anchors682.sh before/after from one
  build (voltcap_test_anchor_off, QA only). KONKR all 53 layouts x All / Xbox 360: CLASSIC before -8 dp / +522 dp, after 0.0 /
  0.0 (PASS); every layout that shows the shared header + chip row gets it. Own Play / chips (rule not reached, by eye):
  CINEMA, CUSTOM already one column; BAR, DASH small offsets; LIBRARY selected chip runs to the far right (needs the adapted
  rule); ARTLIB has no chip row (n/a). Trade-off for the owner: the last console selected leaves it alone at Play's edge.

**17 Nov ~23:10 Berlin - OWNER: LIBRARY RESCAN BUG 681r (data integrity - newest, before everything below).** A deleted game
that was the only one of its console stayed listed after Rescan library, and the console stayed a category. Focused v0.6.74 fix
on branch `claude/v0674-rescan-fix` (from this line at c71c8cb): RomScanner.rootState (OK / GONE on readable storage /
UNREADABLE: card out, bare mount point, refused listing) + failedDirs from the walk; data/RomReconcile.kt one verdict per scan
(read-and-missing = gone, unreadable = kept, nothing read = no verdict, automatic scan with no games = no verdict); a console
with no games left drops out of the selection (falls back to All) after a scan's verdict, kept after a restart. Found on the
way: the old code dropped every game of an unplugged SD-card folder whenever another folder had games (now kept). Saves,
history, achievements, art, metadata, favourites, settings untouched (keyed by path; re-adding brings them back).
Evidence: RescanPrune681rTest 8/8 + DeletedRoms523Test 3/3 (cloud); emulator end-to-end rescan681r.sh run 44190749178 VERDICT
PASS - 15/15 cases (only game deleted -> not listed, its console no category, selection back on All; rescan twice; restart;
one of two; whole game folder; re-added = same identity) on synthetic stand-ins in the QA app's own folder. Verify 310: one
runner green (full suite), the other hit Maven Central 429 (infra) - rerun requested. Hardware NOT TESTED (HW queue: the
owner's real case - the exact Xbox 360 game layout on the device is not known; also an SD card taken out). Not merged; owner GO
needed. Next for the hourly task: nothing to do on it unless CI goes red.
P2 batch 1 status: Classic 681c, Console hub 681d, Resume 681e-f, TV room 681g implemented on `claude/v0674-native-pages`
(18/53 implemented; batch 1 hardware 0/5, owner 0/5). Emulator review of all five re-running (run 44194071430); findings from
the first review fixed in 681h (bumpers lost on the Back hint, Classic label wrap; crate x1.3 word breaks fixed by 681cr).

**17 Nov ~22:15 Berlin - OWNER: P2 NATIVE PAGES IS A MAIN PRIORITY (newest; read with the v0.6.74 list below).** The 40 layouts
without their own Details / Achievements / Options pages get them, each in its own design ("not the shared pages in different
borders"), batches of 4-5, screenshots / recordings for review, tracked as Native pages X/53 (implemented / hardware / owner).
Plan, rules, per-layout design briefs and the tracker: `docs/claude/NATIVE_PAGES_P2.md` on branch `claude/v0674-native-pages`
(the kit NativePageKit.kt + Record crate 681a are there; batch 1 next: Classic, Console hub, Resume, TV room).
Run order from now: alternate - one run P2 (next layout of the current batch on `claude/v0674-native-pages`, worktree, push
fast-forward; merge the dev line into that branch first when it moved), the next run the v0.6.74 list below (pixel font / badge
styles / polish / fixes). CI first as always. Never merge the P2 branch into the dev line before the owner approves the batch.

**17 Nov ~20:15 Berlin - OWNER PRIORITIES v0.6.74 (newest; this order wins over every older list below).** v0.6.73 is
published. Each run: verify Verify 310 on the last push first (red -> fix first), then the first unblocked item:
1. D356 Channel progress pill vs sliding name - DONE in code 680a (badge lane OVERLAY band; BadgeOverlay680aTest); HW-24 queued.
2. Pixel font 62.5 % with readable minimums (owner B1 = option B, branch `claude/v0674-pixel-font`): finish it and fix the
   typography of Pocket Classic, Pocket Ribbon, Night, Pocket Quest, Pocket Camera, Bar and Terminal on that branch; emulator
   review sheets for the owner; merge into this line only after the owner's OK on the comparison.
3. Badge styles (owner B1 plan, docs/claude/BADGE_STYLES_674.md if present): Classic + Compact (default), Top Clip, Hanging
   Bottom - selectable, per-layout override, achievement progress visible wherever possible (compact form before hiding);
   Twin Icons badge spacing (D352). On a separate branch `claude/v0674-badge-styles`; comparison images before approval.
4. Visual polish across the 53 game windows, each keeping its own design (matrix H16).
5. badgecheck (D357): skip badges of tiles scrolled partly off-screen and cover-flow covers stacked behind the focused one
   (real collisions still FAIL) + selftest fixtures for both.
6. Profile picture in its intended corner, never twice, never overlapping (statuscheck keeps guarding).
7. Then: Achievements animations (H12), Thor dual-screen (H13, owner options in THOR_DUAL_SCREEN.md), Save Sync 529, the rest.
Rules (owner 17 Nov 20:52): existing hourly task only; bounded cloud work + tests; emulator QA where it fits; hardware queued;
never disturb apps running on connected devices; dashboard / matrix / handoff after meaningful changes; no full 53-layout
hardware reruns without need; experiments on separate v0.6.74 branches; interrupt the owner only for design decisions,
blocking hardware, data-integrity risks or release authorization; no merge to main / publish without GO.


**24 Nov 21:40 UTC, 739a (cloud, Task 5):** Verify 310 on 5d6a422 (dev) + fb72ff4 (P2) GREEN. P2 branch 982dfb1: Rental shop
(SHOP_TAG) has the rental tag's own pages - CATALOGUE CARD / REWARDS CARD (punch card) / COUNTER in its black capitals lit in the
neon (WinPagesShop.kt; class C 14 scenes done, own designs 44 / 53). PlayPlace had silently skipped the shop (did not know RENT &
PLAY / CATALOGUE CARD) - taught both; Play now proven put on all three pages. HW-28 extended. Not approved.
NEXT: check Verify 310 on this push AND on 982dfb1 (P2) FIRST (red -> fix first). Then Task 5: next class C layout of batch 6 on the
P2 branch (Arctic, Green core, Art library, Cross; then Live, Layout Studio; the Pocket family (POCKET_LCD) needs all three faces in
one batch) - or an emulator p2review sheet of batch 5 for the owner, or the Task 1 residual. The owner's session pushes to the P2
branch: fetch + merge before every P2 push. New P2 pages: before pushing run PlayPlace682ab + PlayPlaceRpc682ab + AchFirstRow
(+Rpc) + NoStrayPlay682j + ShelfText for the layout AND every test that greps the scene name (grep -rln <SCENE> app/src/test); check
the PLAYPLACE output line really found Play on the pages (a layout with its own key words is skipped, not failed).

**24 Nov 20:50 UTC, 738a (cloud, Task 5):** Verify 310 on ca697b9 (dev) + 5e1b096 (P2) GREEN. P2 branch fb72ff4: Panel dashboard
(PANEL_DETAIL) has the swung-round panel's own pages - OVERVIEW / ACHIEVEMENTS / MANAGE as flat tiles and rows lit in the
dashboard's colour, sliding in like the next panel (WinPagesPanels.kt; class C 13 scenes done, own designs 43 / 53). Batch 5
(Orbit, Airy, Bubbles, Camera, Panels) is now all implemented - ready for an emulator p2review sheet for the owner. HW-28 extended.
NEXT: check Verify 310 on this push AND on fb72ff4 (P2) FIRST (red -> fix first). Then Task 5: next class C layout on the P2
branch (batch 6: Rental shop, Arctic, Green core, Art library, Cross; then Live, Layout Studio; the Pocket family (POCKET_LCD) needs
all three faces in one batch) - or the Task 1 residual. The owner's session pushes to the P2 branch: fetch + merge before every P2
push. New P2 pages: before pushing run PlayPlace682ab + PlayPlaceRpc682ab + AchFirstRow (+Rpc) + NoStrayPlay682j for the layout
AND every test that greps the scene name (grep -rln <SCENE> app/src/test).

**24 Nov 19:55 UTC, 737a (cloud, Task 5):** Verify 310 on 32e3610 (dev) + 445fd1f (P2) GREEN. P2 branch 5e1b096 (737a f2e65f0 +
merge of the owner session's 682au): Pocket camera (CAMERA_MENU) has the cartridge menu's own pages - DATA / ALBUM / SETUP in its
pixel font and four shades (WinPagesCamera.kt; class C 12 scenes done, own designs 42 / 53). PlayPlace caught Play a key lower on
Options (fixed: UniformKeyColumn slots = PAGE_SLOTS); AchFirstRow Rpc x1.3 caught a 346 dp first award (fixed). HW-28 extended.
NEXT: check Verify 310 on this push AND on 5e1b096 (P2) FIRST (red -> fix first). Then Task 5: next class C layout on the P2
branch (Panels, Shop, Green core, Art library, Live, Layout Studio, Cross; the Pocket family (POCKET_LCD) needs all three faces in
one batch) - or the Task 1 residual. The owner's session pushes to the P2 branch: fetch + merge before every P2 push. New P2
pages: before pushing run PlayPlace682ab + PlayPlaceRpc682ab + AchFirstRow (+Rpc) + NoStrayPlay682j for the layout AND every test
that greps the scene name (grep -rln <SCENE> app/src/test).

**24 Nov 18:45 UTC, 736a (cloud, Task 5):** Verify 310 on 18d8051 (dev) + 2e18249 (P2) GREEN. P2 branch 445fd1f: Bubble home
(BUBBLE_CARD) has the LiveArea card's own pages - INFO / AWARDS / SETTINGS on its white panel (WinPagesBubble.kt; class C 11
scenes done, own designs 41 / 53). The page panel is white now (was a dark see-through box). HW-28 extended. Not approved.
NEXT: check Verify 310 on this push AND on 445fd1f (P2) FIRST (red -> fix first). Then Task 5: next class C layout on the P2
branch (Camera, Panels, Shop, Green core, Art library, Live, Layout Studio, Cross; the Pocket family (POCKET_LCD) needs all three
faces in one batch) - or the Task 1 residual. The owner's session pushes to the P2 branch: fetch + merge before every P2 push.
New P2 pages: before pushing run PlayPlace682ab + PlayPlaceRpc682ab + AchFirstRow (+Rpc) + NoStrayPlay682j for the layout AND
every test that greps the scene name (grep -rln <SCENE> app/src/test).

**24 Nov 17:45 UTC, 735a (cloud, Task 5):** Verify 310 on 1eb9328 (dev) + 6e8c897 (P2) GREEN. P2 branch 2e18249 (735a da3e595 +
merge of the owner session's 682at charging light): Airy home (AIRY_PANEL) has the panel's own pages - ABOUT / STARS / SETTINGS
(WinPagesAiry.kt; class C 10 scenes done, own designs 40 / 53). x1.3 room for the first star found by AchFirstRow and fixed
(one-line stars header; tight panel pages: small keys, smaller heading). HW-28 extended. Not approved.
NEXT: check Verify 310 on this push AND on 2e18249 (P2) FIRST (red -> fix first). Then Task 5: next class C layout on the P2
branch (Bubbles, Camera, Panels, Shop, Green core, Art library, Live, Layout Studio, Cross; the Pocket family (POCKET_LCD) needs
all three faces in one batch) - or the Task 1 residual. The owner's session pushes to the P2 branch: fetch + merge before every
P2 push. New P2 pages: before pushing run PlayPlace682ab + PlayPlaceRpc682ab + AchFirstRow (+Rpc) + NoStrayPlay682j for the layout
AND every test that greps the scene name (grep -rln <SCENE> app/src/test).

**24 Nov 16:50 UTC, 734b (cloud, Task 5):** Verify 310 on 00d7e9b (dev) GREEN; P2 ec5addf RED was the owner session's (Arcade),
fixed by its 6a9e2d4 (GREEN). P2 branch 6e8c897: Orbit deck (ORBIT_TERMINAL) has the console's own screens - DATA / TRACKS / COMMANDS
(WinPagesOrbit.kt; class C 9 scenes done, own designs 39 / 53). Arc keeps PAGE_SLOTS places on pages (Play stayed put only after
that); console name on its own line (ran under READY at x1.3). PlayPlace + NoStrayPlay now name icon-only / labelled keys. HW-28
extended. Not approved. The owner's live session is pushing to the P2 branch (682as) - fetch + merge before every P2 push.
NEXT: check Verify 310 on this push AND on 6e8c897 (P2) FIRST (red -> fix first). Then Task 5: next class C layout on the P2
branch (Airy, Bubbles, Camera, Panels, Shop, Green core, Art library, Live, Layout Studio, Cross; the Pocket family (POCKET_LCD)
needs all three faces in one batch) - or the Task 1 residual. New P2 pages: before pushing run PlayPlace682ab + PlayPlaceRpc682ab +
AchFirstRow + NoStrayPlay682j for the layout AND every test that greps the scene name (grep -rln <SCENE> app/src/test).

**24 Nov 15:45 UTC, 734a (cloud, CI fix):** Verify 310 on 194557a (dev) GREEN; P2 branch RED on d3907c7 (PopupNativeStorageTest
still named Case flow as a shared-page scene after 733a converted it). Fixed test-only on the P2 branch (50cfb3f): the check follows
NATIVE_PAGE_SCENES, so future conversions cannot break it. PopupNativeStorageTest 9/9 (cloud). Not approved; HW-28 unchanged.
NEXT: check Verify 310 on this push AND on 50cfb3f (P2) FIRST (red -> fix first). Then Task 5: next class C layout on the P2
branch (Orbit, Airy, Bubbles, Camera, Panels, Shop, Green core, Art library, Live, Layout Studio, Cross; the Pocket family
(POCKET_LCD) needs all three faces in one batch) - or the Task 1 residual. New P2 pages: before pushing run PlayPlace682ab +
PlayPlaceRpc682ab + AchFirstRow for the layout AND every non-Robolectric test that greps the scene name (grep -rln <SCENE>
app/src/test) - 733a missed PopupNativeStorageTest that way.

**24 Nov 15:20 UTC, 733a (cloud, Task 5):** Verify 310 on 3f6f2f6 (dev) + 91247df (P2) GREEN. P2 branch d3907c7: Case flow
(CASE_MEDIA) has the case's own pages in the picture's glass well - DETAILS / EXTRAS / SETUP (WinPagesCase.kt; class C 8 scenes
done, own designs 38 / 53); on its pages the window keeps one size so Play stays put (PlayPlace caught it); PlayPlace's More
matcher now reads a labelled More key. Robolectric 2286 tests 0 failures. HW-28 extended. Not approved.
NEXT: check Verify 310 on this push AND on d3907c7 (P2) FIRST (red -> fix first). Then Task 5: next class C layout on the P2
branch (Orbit, Airy, Bubbles, Camera, Panels, Shop, Green core, Art library, Live, Layout Studio, Cross; the Pocket family
(POCKET_LCD) needs all three faces in one batch) - or the Task 1 residual (Row Top column vs the bottom clock bar; design choice).
New P2 pages: run PlayPlace682ab + PlayPlaceRpc682ab + AchFirstRow for the layout before pushing.

**24 Nov 14:20 UTC, 732a (cloud, Task 5):** Verify 310 on c157066 (dev) + ee74963 (P2) GREEN. P2 branch 91247df: File select
(FILE_CARD) has the file's own screens on its inset - DETAILS / STARS / OPTIONS (WinPagesFile.kt; class C 7 scenes done, own designs
37 / 53); its page row keeps Play in the first of two slots (PlayPlace caught it before the push). Robolectric 628 tests 0 failures
(FILESELECT cases). HW-28 extended. Not approved. Cloud Gradle: export VOLTCAP_MAVEN_MIRROR (Maven Central 429 this run).
NEXT: check Verify 310 on this push AND on 91247df (P2) FIRST (red -> fix first). Then Task 5: next class C layout on the P2
branch (Case flow, Orbit, Airy, Bubbles, Camera, Panels, Shop, Green core, Art library, Live, Layout Studio, Cross; the Pocket
family (POCKET_LCD) needs all three faces in one batch) - or the Task 1 residual (Row Top column vs the bottom clock bar; design
choice). New P2 pages: run PlayPlace682ab + PlayPlaceRpc682ab + AchFirstRow for the layout before pushing.

**24 Nov 13:20 UTC, 731a (cloud, Task 5 + P2 CI fix):** Verify 310 on b2c90cc (dev) GREEN; P2 branch was RED on 911681e
(PlayPlace682ab MEMCARD - 730a's Memory card put Play alone in the middle on COMMANDS): fixed (two page slots), the owner's session
pushed the same fix (682as, theirs kept). P2 branch ee74963: Cardboard box (BOX_SHEET) has the insert's own sheets - CONTENTS /
CHECKLIST / SERVICE typed on its paper (WinPagesBox.kt; class C 6 scenes done, own designs 36 / 53); first page's end-aligned fact
values no longer laid out past their box. Robolectric suites green (see SUMMARY 731a). HW-28 extended. Not approved.
NEXT: check Verify 310 on this push AND on ee74963 (P2) FIRST (red -> fix first). Then Task 5: next class C layout on the P2
branch (File select, Case flow, Orbit, Airy, Bubbles, Camera, Panels, Shop, Green core, Art library, Live, Layout Studio, Cross;
the Pocket family (POCKET_LCD) needs all three faces in one batch) - or the Task 1 residual (Row Top column vs the bottom clock bar;
design choice). New P2 pages: run PlayPlace682ab + PlayPlaceRpc682ab too (they caught 730a; they derive from AchFirstRow.NATIVE).

**24 Nov 11:55 UTC, 730a (cloud, Task 5):** Verify 310 on d2d508f (dev) + 546f5b99 (P2) GREEN. P2 branch e59f3c6: Memory card
(MEMCARD_INFO) has the save browser's own screens on its glass panel - INFORMATION / MEMORY BLOCKS / COMMANDS (WinPagesMemCard.kt;
class C 5 scenes done, own designs 35 / 53); the panel now spans title-to-choices (measured). Existing defect fixed: a long console
name ran under the title at x1.3 (ShelfText MEMCARD). Robolectric 581 tests 0 failures. HW-28 extended. Not approved. The owner's
live session pushed 682ar3 to the P2 branch meanwhile (merged) - fetch + merge before every P2 push.
NEXT: check Verify 310 on this push AND on e59f3c6 (P2) FIRST (red -> fix first). Then Task 5: next class C layout on the P2
branch (remaining single scenes: Box, File select, Case flow, Orbit, Airy, Bubbles, Camera, Panels, Shop, Green core, Art library,
Live, Layout Studio, Cross; the Pocket family (POCKET_LCD) needs all three faces in one batch) - or the Task 1 residual (Row Top
column vs the bottom clock bar; design choice).

**24 Nov 10:55 UTC, 729a (cloud, Task 5):** Verify 310 on e9092e9 (dev) + 864e293 (P2) GREEN. P2 branch 546f5b99: Hero carousel +
Hero tiles (HERO_BANNER, one scene) have the banner's own pages - THE FEATURE / HALL OF FAME / QUICK ACTIONS (WinPagesHero.kt;
class C 4 scenes done, own designs 34 / 53). Compact heading on pages (x1.3 AchFirstRow found 0 px for the first achievement).
Robolectric 1,884 tests 0 failures (100 Hero cases). HW-28 extended. Not approved. The owner's live session also pushes to the
P2 branch (682ar, 682ar2 merged in) - fetch + merge before every P2 push.
NEXT: check Verify 310 on this push AND on 546f5b99 (P2) FIRST (red -> fix first). Then Task 5: next class C layout on the P2
branch (the Pocket family (POCKET_LCD) needs all three faces in one batch; else the next single-scene class C layout in
NATIVE_PAGES_AUDIT.md) - or the Task 1 residual (Row Top column vs the bottom clock bar; design choice).

**24 Nov 09:50 UTC, 728a (cloud, Task 5):** Verify 310 on 678c10e (dev) + 8a73d96 (P2) GREEN. P2 branch 864e293: Timeline
(TIMELINE_ENTRY) has its own pages along its line - CHRONICLE / MILESTONES / BRANCHES (WinPagesTime.kt; class C 3 of 23, own
designs 32 / 53). Robolectric: NativePagesTime728aTest 4/4 + TIMELINE in AchFirstRow / ShelfText / WinFocus / NoStrayPlay /
LazyRows / PlayPlace - 52 cases green, 0 skipped (cloud). HW-28 extended. Not approved.
NEXT: check Verify 310 on this push AND on 864e293 (P2) FIRST (red -> fix first). Then Task 5: next class C layout on the P2
branch (suggested: Hero / Hero tiles (HERO_BANNER, two layouts one scene); the Pocket family (POCKET_LCD) needs all three faces
in one batch) - or the Task 1 residual (Row Top column vs the bottom clock bar; design choice).

**24 Nov 08:55 UTC, 727a (cloud, Task 5):** Verify 310 on 045fac8 + 5d53277 GREEN. P2 branch 8a73d96: Channel grid (WII_PREVIEW)
has the channel's own screens in its preview well - CHANNEL INFO / STAMPS / SETTINGS (WinPagesChan.kt; class C 2 of 23, own designs
31 / 53). Three focus bugs fixed in the Channel scene on the way (More-page focus effect, one key row, closed More answers for its
keys - NoStrayPlay CHAN storm). Robolectric CHAN suites 104/0 + Channel source tests green (cloud). HW-28 extended. Not approved.
NEXT: check Verify 310 on this push AND on 8a73d96 (P2) FIRST (red -> fix first). Then Task 5: next class C layout on the P2
branch (suggested: Timeline (TIMELINE_ENTRY), then Hero / Hero tiles (HERO_BANNER); the Pocket family (POCKET_LCD) needs all three
faces in one batch) - or the Task 1 residual (Row Top column vs the bottom clock bar; design choice).

**24 Nov 07:55 UTC, 726a (cloud, Task 5):** Verify 310 on 55b7361 + eb4af38 GREEN. P2 branch 5d53277: Circuit board (CHIP_SHEET)
has its own datasheet sections - CHARACTERISTICS / PIN STATES / JUMPERS (WinPagesChip.kt; first class C layout, own designs
30 / 53). Robolectric: NativePagesChip726aTest + CIRCUIT in AchFirstRow / LazyRows / PlayPlace / NoStrayPlay / WinFocus /
ShelfText, all green (cloud). HW-28 queued (KONKR + RP Classic). Not visually approved.
NEXT: check Verify 310 on this push AND on 5d53277 (P2) FIRST (red -> fix first). Then Task 5 continues on the P2 branch with
the next class C layout (suggested: Channel (WII_PREVIEW) or Timeline (TIMELINE_ENTRY) - single-layout scenes; the Pocket
family (POCKET_LCD) needs all three faces in one batch) - or the Task 1 residual (Row Top column vs the bottom clock bar; design
choice).

**24 Nov 06:40 UTC, 725b (cloud, Task 5 prep):** Verify 310 on 35bb129 GREEN. P2 branch `claude/v0674-native-pages`: dev line
merged in (31e2931, test_qa.py conflict = both sides kept); its QA selftest was RED (p2review.sh r714 raw keys around winguard)
-> eb4af38: k_home via wg_key_home, the R1 panel's B via new wg_key_main; selftest 264 OK, merged tree compiles, static PASS.
Unit tests NOT RUN (Maven 429) - CI on eb4af38 is their check.
NEXT: check Verify 310 on the dev push AND on eb4af38 (P2) FIRST (red -> fix first). Then Task 5: the first of the 23 class C
layouts on the P2 branch (worktree; NATIVE_PAGES_P2.md "How a family joins"; suggested next: Pocket family / Channel /
Circuit) - or the Task 1 residual (Row Top: a column taller than the room above the bottom clock bar; needs a design choice,
no fix that cuts text).

**24 Nov 05:45 UTC, 725a (cloud, Task 4):** Verify 310 on ff28c4f GREEN. START > "Categories…" on an installed app / Android
game: Automatic / Android games / Other apps, Recomps, consoles (LibraryAppsSettings.appConsoles, "More consoles…"), Collections…
link; several at once, applied at once, kept by package name; a console with only an app appears / hides; no duplicate in All.
AppCategories725aTest 6/6 + 724a / 450 / 482 / 394 PASS (cloud). HW-24 725a.
NEXT: check Verify 310 on this push FIRST (red -> fix first). Then Task 5 (remaining 24 native-page layouts on
`claude/v0674-native-pages`, worktree, merge the dev line in first) or the open Task 1 residuals (Row Top column taller than
the screen vs the bar at the bottom); Task 3 auto-suggest stays OPEN until a reliable detector / vetted list exists.

**24 Nov 04:40 UTC, 724a (cloud, Task 3):** Verify 310 on c7f4430 GREEN. Recomps category: apps marked by hand in Settings >
Apps in your library ("Recomp?" chip) form "Recomps" (RECOMPS_PLATFORM, LibraryAppsSettings.recomps); in All too, never Other
apps, hidden while empty, same game record. No auto-suggest (no reliable detector). Recomps724aTest 5/5 + 450 / 482 / 394 PASS
(cloud). HW-24 724a.
NEXT: check Verify 310 on this push FIRST (red -> fix first). Then Task 4: START on an installed game / app -> Classification &
categories, MULTIPLE at once (Recomps, Android games, Other apps, consoles, collections); manual beats automatic; reuse
LibraryAppsSettings (recomps / forcedGames / forcedApps) + collections; no duplicate records; launch / saves / metadata unchanged.

**24 Nov 03:40 UTC, 723a (cloud, Task 2):** Verify 310 on 4404495 GREEN. R1 Focus position: 53-layout audit (code) - only the
Voltcap row and Tile home have a horizontal focus anchor; new R1 "Focus" tile reuses their settings (press = next, hold = list),
hidden elsewhere, greyed with carousel / first-one-wide; matrix docs/claude/R1_FOCUS_POSITION_723.md. FocusPosTile723aTest 4/4 +
R1Compat326 / 491 / TileWalk329 PASS (cloud). HW-24 723a.
NEXT: check Verify 310 on this push FIRST (red -> fix first). Then owner 22:43 item 5: Recomps category (Task 3) - separate
category for installed Android recomp APKs + manual shortcuts, auto-suggest only when reliable, hidden when empty; then START ->
Classification & Categories (Task 4).

**24 Nov 02:40 UTC, 722a (cloud, Task 1):** Verify 310 on e77f9fa (721a) GREEN. Edit Home / preset preview vs the real Home:
at Top the preview drew the row at 0.2, its name above and the clock bar at the top; now row under the edge, name under it, clock
bar at the bottom (above the legend) - the real Home's rule (PreviewDock). Middle / Bottom unchanged. PreviewDock722aTest 4/4
PASS (cloud). HW-24 722a (by eye).
NEXT: check Verify 310 on this push FIRST (red -> fix first). Then owner 22:43 item 4: R1 Focus Position audit (53 layouts,
reuse the Voltcap row / Classic and Tile home focus-anchor prefs; only where genuinely supported; A cycles, long-press direct).

**24 Nov 01:40 UTC, 721a (cloud, Task 1):** Verify 310 on a8a3ef6 (720a) GREEN. Tile home Top vs the profile row audited: Top
with the Switch-style pictures ignored a top-corner music player (Middle already kept both floors) - TileRows.topPadDp now keeps
the rows 8 dp under whichever reaches lower; unchanged when only one is shown. TileTopMusic721aTest 3/3 + 521 / TileRows / 714a
tests PASS (cloud). HW-24 721a (by eye).
NEXT: check Verify 310 on this push FIRST (red -> fix first). Then Task 1: Edit Home preview (PresetPreview.kt rowTop fractions)
vs the real Home at Top / Middle / Bottom; then item 4 R1 Focus Position audit (53 layouts, reuse the Classic / Tile home
focus-anchor prefs).

**24 Nov 00:40 UTC, 720a (cloud, Task 1):** Verify 310 on d11266d (719a) GREEN. 715a's real Edit Home D-pad move of the added
Play part now has a QA path: voltcap_qa_edit mark (EDIT <part> / HOME) + `voltcap_test_reach` line (from the home's own
prepareReach); play720.sh + play720.py (selftest 6/6). PlayReach720aTest 3/3 PASS (cloud). HW-24 720a.
NEXT: check Verify 310 on this push FIRST (red -> fix first). Then Task 1 (owner 22:43 item 3): Tile home Top vs the profile row
at x1.3 (Batch 521 / Sprint 527 minimum), Edit Home preview (PresetPreview.kt rowTop fractions) vs the real Home; then item 4
R1 Focus Position audit.

**23 Nov 23:35 UTC, 719a (cloud, Task 1):** Verify 310 on d1c70cd (718a) GREEN. 716a's real combination now has a QA path:
`voltcap_test_rowcombo on|off` (header + Game info / news + every category + a song in the bar's player, preview only) and
`voltcap_test_rowcol` (one `rowcol` line: column vs status bar, dp); row716.sh + row716.py (selftest 6/6). RowCombo719aTest 3/3
PASS (cloud). HW-24 719a.
NEXT: check Verify 310 on this push FIRST (red -> fix first). Then Task 1 (owner 22:43 item 3): a safe QA path for the real Edit
Home D-pad movement of the added Play part (715a; HomeEditState select: + real D-pad keys only while Edit Home is open in the QA
app, no R1); then Tile home Top vs the profile row at x1.3, Edit Home preview vs the real Home.

**23 Nov 22:20 UTC, 718a (cloud, Task 1):** Verify 310 on bbe5c98 GREEN. Owner 22:43 order read (items 1-2 = P2 on-device
profiling / Coverflow x1.3 on the P2 branch - live session with hardware). Task 1 "verify it is the exact handler": R1's Row
tile and `voltcap_test_dock` now share rowTilePress (ui/QuickSettings.kt); `voltcap_test_dock NEXT` = one tile press;
row714.sh s8-s10 press round the cycle. RowTileExactHandler718aTest 3/3 + 714a / 717a / 521 tests PASS (cloud). HW-24 714a.
NEXT: check Verify 310 on this push FIRST (red -> fix first). Then Task 1 (owner 22:43 item 3): a safe QA path for the real Edit
Home D-pad movement of the added Play part (715a; HomeEditState select: + real D-pad keys only while Edit Home is open in the
QA app, no R1), and a QA hook for the 716a combination (game header + Game & news + category bar Full + music player) so it can
be checked at x1.3; then Tile home Top vs the profile row at x1.3, Edit Home preview vs the real Home.

**23 Nov 21:55 UTC, 717a (cloud, QA tooling):** Verify 310 on 7bd1dea (716b) GREEN. 714a's hardware check unblocked without R1
(its Quick row has system tiles): `voltcap_test_dock` (the Row tile's own handler, preview only) now logs one `rowtile` line;
tools/qa/device/row714.sh + tools/qa/tools/row714.py grade it (selftest 5/5). RowTileQaLine717aTest 3/3 PASS (cloud). HW-24 714a.
NEXT: check Verify 310 on this push FIRST (red -> fix first). Then TASK 1 continued (one finding per run with its test): Tile home
Top vs the profile row at x1.3 (Batch 521 / Sprint 527 minimum), corner round shortcuts vs the moved column, Edit Home preview
(PresetPreview.kt rowTop fractions) vs the real Home.

**23 Nov 20:35 UTC, 716b (cloud, CI fix):** Verify 310 on 5d96b3d (716a) RED on both runners: RoboFramesApply529Test caught
RowColumnUnderBar716aTest stepping frames without Snapshot.sendApplyNotifications() (test hygiene, app code fine). Fixed the
test loop; 716a 4/4 + RoboFramesApply529Test 1/1 PASS (cloud). Reminder for new Robolectric tests: every
mainClock.advanceTimeByFrame() line also calls Snapshot.sendApplyNotifications().
NEXT: check Verify 310 on this push FIRST (red -> fix first). Then TASK 1 continued (one finding per run with its test): Tile home
Top vs the profile row at x1.3 (Batch 521 / Sprint 527 minimum), corner round shortcuts vs the moved column, Edit Home preview
(PresetPreview.kt rowTop fractions) vs the real Home.

**23 Nov 19:35 UTC, 716a (cloud):** Verify 310 on 2e865a2 (715a) GREEN. TASK 1 finding 3 fixed: Voltcap row Row Middle /
Bottom placed its column on the whole screen, so a tall column (header + info + chips at x1.3) started under the status bar;
RowColumnFit.keepUnderBar keeps its top below the bar (unchanged whenever it fits; Middle clears the legend too where both fit).
RowColumnUnderBar716aTest 4/4 PASS (cloud). HW-24 716a. Matrix H18.
Residual (noted, not changed): Row Top puts the bar at the bottom; a column taller than the screen there could still reach it.
NEXT: check Verify 310 on this push FIRST (red -> fix first). Then TASK 1 continued (one finding per run with its test): Tile home
Top vs the profile row at x1.3 (Batch 521 / Sprint 527 minimum), corner round shortcuts vs the moved column, Edit Home preview
(PresetPreview.kt rowTop fractions) vs the real Home.

**23 Nov 18:45 UTC, 715a (cloud):** Verify 310 on 37b32a1 (714a) GREEN. Owner 18:23 backlog read (P2-i / P2-j / D386 / D387 are
on-device profiling - live session). TASK 1 finding 2 fixed: Voltcap row with the header off - ▲ / ▼ follow where an added Play /
Continue part is drawn (HomeReach.headerAbove + PartRects.frac), not the row's place. HeaderReachDirection715aTest 3/3 PASS
(cloud). HW-24 715a. Matrix H18.
Audited and consistent (no change): header above / below the row per Row place vs prepareReach; the status-bar music player at Top
(bar moves to the bottom, playerUp follows); ShortcutCorner.resolve vs the row edge.
NEXT: check Verify 310 on this push FIRST (red -> fix first). Then TASK 1 continued (one finding per run with its test): Tile home
Top vs the profile row at x1.3 (Batch 521 / Sprint 527 minimum), Row Middle with header + info + chips taller than the screen at
x1.3 (the centred column can run under the status bar), Edit Home preview vs the real Home.

**23 Nov 17:25 UTC, 714a (cloud):** Verify 310 on 8598c1c (713a) GREEN. P2 finish handed over by the live session (P2-23c) ->
owner TASK 1 (Row-position collisions) started. Finding 1 fixed: Tile home Row place change deleted the round buttons' Edit Home
spot by the tiles (Batch 521) - now kept aside per place ("shortcuts@<PLACE>" in widget_positions) and restored there.
TileRowPlaceSpots714aTest 4/4 + Batch521Test 4/4 PASS (cloud). HW-24 714a. Matrix H18.
NEXT: check Verify 310 on this push FIRST (red -> fix first). Then TASK 1 continued (cloud audit, one finding per run with its
test): Voltcap row (CLASSIC) Top / Middle / Bottom vs the game header (headerUp in LauncherScreen.prepareReach), the small music
player in the status bar at Top, corner round shortcuts (ShortcutCorner.resolve), Tile home Top vs the profile row at x1.3
(Batch 521 / Sprint 527 minimum), Edit Home preview vs the real Home. Hardware checks batched into HW-24.

**23 Nov 16:35 UTC, 713a (cloud):** Verify 310 on 2f17a84 (712a) GREEN; 711a rerun 45195727121 GREEN (NDK failure was infra).
P2 finish still in the live session (claude/v0674-native-pages pushes to 15:23 UTC) - left alone. Filler: P13 rule 11
DialogChrome.kt 3 TRUNCATE -> 0 (ShelfTextField value, ShelfMenuDialog title, Chip label FullText). DialogChromeFullText713aTest
2/2 PASS (cloud). TRUNCATE 69 -> 66. HW-24 713a. Cloud Gradle needs VOLTCAP_MAVEN_MIRROR (Maven 429 without it).
NEXT: check Verify 310 on this push FIRST (red -> fix first). If P2 is finished / handed over: TASK 1 (Row-position collisions
audit). Otherwise P13 next TRUNCATE file (CompanionActivity 3), one per run with its test.

**23 Nov 15:25 UTC, 712a (cloud):** Verify 310 on 38cd4fb (711a) RED on ubuntu-24.04 only: NDK 28 download was a bad zip
before any build (infra); ubuntu-latest passed the full suite; failed jobs rerun requested (run 45195727121). P2 finish still in
the live session (claude/v0674-native-pages pushes to 14:24 UTC) - left alone. Filler: P13 rule 11 RetroAchievementsDialog.kt
3 TRUNCATE -> 0 (achievement name / game / account name FullText). RaDialogFullText712aTest. TRUNCATE 72 -> 69. HW-24 712a.
NEXT: check Verify 310 on this push AND the 45195727121 rerun FIRST (a real failure -> fix first). If P2 is finished / handed
over: TASK 1 (Row-position collisions audit). Otherwise P13 next TRUNCATE file (DialogChrome 3), one per run with its test.

**23 Nov 14:25 UTC, 711a (cloud):** Verify 310 on 838af2e (710a) GREEN. P2 finish still in the live session on
claude/v0674-native-pages (pushes to 13:57 UTC) - left alone. Filler: P13 rule 11 GameJourneyUi.kt 3 TRUNCATE -> 0 (journey step
title / date / detail FullText). GameJourneyFullText711aTest 2/2 PASS (cloud compiled). TRUNCATE 75 -> 72. HW-24 711a (KONKR).
NEXT: check Verify 310 on this push FIRST. If the live session has finished P2 (backlog 0000000 P2 items DONE / handed over):
start TASK 1 (Row-position collisions audit). Otherwise P13 next TRUNCATE file (RetroAchievementsDialog 3, DialogChrome 3), one
per run with its test.

**23 Nov 13:30 UTC, 710a (cloud):** Verify 310 on 8f1ac4f (owner backlog 13:32) GREEN. Owner order 13:32: P2 finish (P2-a..f) is in
the live session on claude/v0674-native-pages (pushes 13:23) - left alone; Tasks 1-4 start after P2. Filler: P13 rule 11
GameWindow.kt 3 TRUNCATE -> 0 (perf rows FullText; story preview qa-ok, Details page whole). GameWindowFullText710aTest 3/3 PASS
(cloud compiled). TRUNCATE 78 -> 75. HW-24 710a (KONKR).
NEXT: check Verify 310 on this push FIRST. If the live session has finished P2 (backlog 0000000 P2 items DONE / handed over):
start TASK 1 (Row-position collisions audit: R1 Row Position x TileRows / TileHome / Edit Home - cloud code audit + a test first).
Otherwise P13 next TRUNCATE file (3 each: GameJourneyUi, RetroAchievementsDialog, DialogChrome), one per run with its test.

**23 Nov 12:20 UTC, 709a (cloud, v0.6.74 turn):** Verify 310 on d0e153e (708a) GREEN. P2 left alone (owner review pending).
P13 rule 11: QuickSettings.kt (R1 panel undo strip, tile editor name, spaced tile caption) 3 TRUNCATE -> 0 via FullText.
QuickSettingsFullText709aTest 2/2 PASS in the cloud (compiled, mirror on). TRUNCATE 81 -> 78. HW-24 709a (KONKR).
NEXT: check Verify 310 on this push FIRST. Then P2 turn if the owner has answered the package / Coverflow reading space;
otherwise v0.6.74: P13 next TRUNCATE file (3 each: GameWindow, GameJourneyUi, RetroAchievementsDialog, DialogChrome) - one
per run with its FullText test; or H12 once the owner picks.

**23 Nov 11:20 UTC, 708a (cloud, v0.6.74 turn):** Verify 310 on 673a1e0 (707a) GREEN. P2 left alone (owner review pending).
P13 rule 11: SecondGamesPage.kt (second screen folder games: list row, missing-cover name, tile label) 3 TRUNCATE -> 0 via
FullText. SecondGamesPageFullText708aTest 2/2 PASS in the cloud (compiled; export VOLTCAP_MAVEN_MIRROR or plugin resolution
fails). TRUNCATE 84 -> 81. HW-26 708a (Thor, with 690a).
NEXT: check Verify 310 on this push FIRST. Then P2 turn if the owner has answered the package / Coverflow reading space;
otherwise v0.6.74: P13 next TRUNCATE file (3 each: QuickSettings, GameWindow, GameJourneyUi, RetroAchievementsDialog,
DialogChrome) - one per run with its FullText test; or H12 once the owner picks.

**23 Nov 10:20 UTC, 707a (cloud, v0.6.74 turn):** Verify 310 on c917a78 (P2-23b, with 706a's mirror) GREEN - mirror confirmed.
P2 left alone (owner review of the 12-layout before/after package pending). P13 rule 11: HomeEditLegend.kt (edit toolbar title
+ scope line) 2 TRUNCATE -> 0 via FullText (reserve = false). HomeEditLegendFullText707aTest 2/2 PASS in the cloud (compiled).
TRUNCATE 86 -> 84. HW-24 707a (KONKR, with 703a).
NEXT: check Verify 310 on this push FIRST. Then P2 turn if the owner has answered the package / Coverflow reading space;
otherwise v0.6.74: P13 next TRUNCATE file (3 each: SecondGamesPage, QuickSettings, GameWindow, GameJourneyUi,
RetroAchievementsDialog, DialogChrome) - one per run with its FullText test; or H12 once the owner picks.

**23 Nov 09:20 UTC, 706a (cloud, CI fix):** Verify 310 on 503d679 (705a) RED on both runners from Maven Central 429 only (3 attempts
each; app + tests compiled). VOLTCAP_MAVEN_MIRROR (Google's Maven Central mirror, CI / cloud only): settings.gradle.kts asks it
before mavenCentral(); Robolectric's android-all fetch too. Cloud with the mirror: compile + 705a test 2/2 + 681y 5/5 PASS.
Cloud runs: export VOLTCAP_MAVEN_MIRROR=https://maven-central.storage-download.googleapis.com/maven2/ (CLOUD_DEVELOPMENT.md).
NEXT: check Verify 310 on this push FIRST (red from the mirror itself -> fix; 429 gone = confirmed). Then the v0.6.74 / P2
alternation (next v0.6.74 turn: rule 11 HomeEditLegend; same mirror env for emulator-qa-trial.yml / qa.yml if they 429).

**23 Nov 08:20 UTC, 705a (cloud, v0.6.74 turn):** Verify 310 on f54a2f9 (704a) GREEN on both runners. P2 left alone (29-layout
package owner review pending). P13 rule 11: CrossTrophies.kt (trophy row titles - slide, no auto-fit since the size grows with
focus; focused description 2 lines + meta line; comments panel title) 4 TRUNCATE -> 0 via FullText (reserve = false).
CrossTrophiesFullText705aTest. TRUNCATE 90 -> 86. Cloud compile BLOCKED twice by Maven 429 - CI is the first compile. HW-24 705a.
NEXT: check Verify 310 on this push FIRST (a real compile error in CrossTrophies - e.g. weight() scope - or the new test -> fix).
Then P2 turn once the owner has reviewed the 29-layout package (P2-23a; TV room D367); v0.6.74: P13 next TRUNCATE file
(HomeEditLegend 2) - one per run with its FullText test; or H12 once the owner picks.

**23 Nov 07:20 UTC, 704a (cloud, CI fix):** Verify 310 on 0288490 (703a): ubuntu-24.04 GREEN (whole suite), ubuntu-latest RED
from 1134 tests that each failed on Robolectric's own 429 fetch of android-all-instrumented ("There were failing tests" -> 702a
still called it code). retry_infra.py now counts failed tests while streaming: infra only when every one is a 429 / 5xx fetch
and the count matches Gradle's totals. Selftest +3 (fail on the old script), test_qa 216/216. No app code.
NEXT: check Verify 310 on this push FIRST (a 429 run must now print "Retrying after a package-server failure"; a real red ->
fix). Then P2 turn once the owner has reviewed the 29-layout package (P2-23a; TV room D367); v0.6.74: P13 next TRUNCATE files
(CrossTrophies 4, HomeEditLegend 2) - one per run with its FullText test; or H12 once the owner picks.

**23 Nov 06:20 UTC, 703a (cloud, v0.6.74 turn):** Verify 310 on 628d827 (702a) GREEN. P2 left alone (29-layout package owner
review pending). P13 rule 11: HomeEditSession.kt (home edit inspector widget name + scope line, widget list names + status
lines) 4 TRUNCATE -> 0 via FullText (reserve = false). HomeEditFullText703aTest. TRUNCATE 94 -> 90. Cloud compile PASSED;
test run blocked by Maven 429 - CI runs it. HW-24 703a (KONKR).
NEXT: check Verify 310 on this push FIRST (red in HomeEditSession / the new test -> fix; a 429 must show the 702a retry). Then
P2 turn once the owner has reviewed the 29-layout package (P2-23a; TV room D367); v0.6.74: P13 next TRUNCATE files
(CrossTrophies 4, HomeEditLegend 2) - one per run with its FullText test; or H12 once the owner picks.

**23 Nov 05:20 UTC, 702a (cloud, CI fix):** Verify 310 on 9073da2 (701a): ubuntu-latest GREEN (whole suite), ubuntu-24.04
Maven 429 only. Root cause of the four red runs: retry_infra.py ruled "> Task :app:testDebugUnitTest FAILED" a code failure
even when its only cause was the 429 download, so no retry ever ran. Fixed: a failed task is infra only when every Gradle
"What went wrong" block is a 429 / 5xx download; selftest +2 (fails on the old script), test_qa 213/213. No app code.
NEXT: check Verify 310 on this push FIRST - if a 429 appears, the log must now show "Retrying after a package-server failure"
(if it does not, look at the tail kept / the block parser). Then P2 turn once the owner has reviewed the 29-layout package
(P2-23a; TV room D367); v0.6.74: P13 next TRUNCATE files (4 each: HomeEditSession, CrossTrophies) - one per run with its
FullText test; or H12 once the owner picks.

**23 Nov 04:30 UTC, 701a (cloud, v0.6.74 turn):** Verify 310 on 0b13a64 (700a) RED on both runners from Maven Central 429 only
(Robolectric nativeruntime jar); compileDebugKotlin + compileDebugUnitTestKotlin PASSED in CI. P2 left alone (29-layout package
owner review pending). P13 rule 11: RaSocialUi.kt (friend award line, Achievement of the Week title / description / game line)
4 TRUNCATE -> 0 via FullText (reserve = false). RaSocialFullText701aTest. TRUNCATE 98 -> 94. Cloud compile BLOCKED twice by
Maven 429 - CI is the first compile. HW-24 701a (KONKR). Note: three Verify runs in a row have lost their test step to Maven 429;
if it keeps happening, a CI-only item is worth it (a dependency cache / retry for the Robolectric runtime jars - never skip tests).
NEXT: check Verify 310 on this push FIRST (a real compile / test failure in RaSocialUi or the new test -> fix; 429 again ->
infra, carry on). Then P2 turn once the owner has reviewed the 29-layout package (P2-23a; TV room D367); v0.6.74: P13 next
TRUNCATE files (4 each: HomeEditSession, CrossTrophies) - one per run with its FullText test; or H12 once the owner picks.

**23 Nov 03:20 UTC, 700a (cloud, v0.6.74 turn):** Verify 310 on a08de4f (699a) RED on both runners from Maven Central 429
only (every failure a Robolectric artifact download; no code failure) - rerun is the lock commit 3d003e4's run. P2 left alone
(29-layout package owner review pending). P13 rule 11: GuideScreen.kt (guide title, source line, contents section titles,
guide picker rows) 5 TRUNCATE -> 0 via FullText (reserve = false). GuideScreenFullText700aTest. TRUNCATE 103 -> 98. Cloud
compile BLOCKED twice by Maven 429 - CI is the first compile. HW-24 700a (KONKR).
NEXT: check Verify 310 on this push FIRST (a real compile / test failure in GuideScreen or the new test -> fix; 429 again ->
infra, carry on). Then P2 turn once the owner has reviewed the 29-layout package (P2-23a; TV room D367); v0.6.74: P13 next
TRUNCATE files (4 each: RaSocialUi, HomeEditSession, CrossTrophies) - one per run with its FullText test; or H12 once the
owner picks.

**23 Nov 02:20 UTC, 699a (cloud, v0.6.74 turn):** Verify 310 on ab8dd64 (698a) GREEN. P2 left alone (29-layout package owner review
pending). P13 rule 11: HubScreens.kt (achievement hub progress rows + recent-unlocks timeline) 5 TRUNCATE -> 0 via FullText
(reserve = false). HubScreensFullText699aTest. TRUNCATE 108 -> 103. Cloud compile PASSED; test run blocked by Maven 429 - CI runs it. HW-24 699a (KONKR).
NEXT: check Verify 310 on this push FIRST (red in HubScreens / the new test -> fix). Then P2 turn once the owner has reviewed
the 29-layout package (P2-23a; TV room D367); v0.6.74: P13 next TRUNCATE files (5: GuideScreen; then 4 each: RaSocialUi,
HomeEditSession, CrossTrophies) - one per run with its FullText test; or H12 once the owner picks.

**23 Nov 01:20 UTC, 698a (cloud, v0.6.74 turn):** Verify 310 on f1050e8 (697a): ubuntu-latest GREEN, ubuntu-24.04 Maven 429 infra. P2 left alone (29-layout package owner review
pending). P13 rule 11: PlayerPages.kt (music player pages: Spotify hint, playlist names, lyric lines) 5 TRUNCATE -> 0 via FullText
(reserve = false). PlayerPagesFullText698aTest. TRUNCATE 113 -> 108. Cloud compile PASSED; test run blocked by Maven 429 - CI runs it. HW-24 698a (KONKR).
NEXT: check Verify 310 on this push FIRST (red in PlayerPages / the new test -> fix). Then P2 turn once the owner has reviewed
the 29-layout package (P2-23a; TV room D367); v0.6.74: P13 next TRUNCATE files (5 each: HubScreens, GuideScreen; then 4 each:
RaSocialUi, HomeEditSession, CrossTrophies) - one per run with its FullText test; or H12 once the owner picks.

**23 Nov 00:20 UTC, 697a (cloud, v0.6.74 turn):** Verify 310 on 47ebe9b (696a) / ff677c5: ubuntu-latest GREEN, ubuntu-24.04
Maven 429 infra. P2 left alone (29-layout package owner review pending). P13 rule 11: QuickMenuActivity.kt (in-game quick menu: achievements
tab + Bluetooth device names) 5 TRUNCATE -> 0 via FullText (reserve = false). QuickMenuFullText697aTest. TRUNCATE 118 -> 113.
Cloud compile PASSED; test run blocked by Maven 429 - CI runs it. HW-24 697a (KONKR).
NEXT: check Verify 310 on this push FIRST (red in QuickMenuActivity / the new test -> fix). Then P2 turn once the owner has reviewed
the 29-layout package (P2-23a; TV room D367); v0.6.74: P13 next TRUNCATE files (5 each: PlayerPages, HubScreens, GuideScreen) - one per run with its FullText test;
or H12 once the owner picks.

**22 Nov 23:20 UTC, 696a (cloud, v0.6.74 turn):** Verify 310 on 492265c (695a): ubuntu-latest GREEN (full suite), ubuntu-24.04
Robolectric jar-transform infra. P2 left alone (batch 1 owner review pending). P13 rule 11: WidgetBoard.kt (sound widget app names)
2 TRUNCATE -> 0 via FullText (reserve = false). WidgetBoardFullText696aTest. TRUNCATE 120 -> 118. NOT compiled in the cloud (Maven 429)
- CI first compile. HW-24 696a (KONKR).
NEXT: check Verify 310 on this push FIRST (red in WidgetBoard / the new test -> fix). Then P2 turn if the owner has reviewed
batch 1; v0.6.74: P13 next TRUNCATE files (5 each: QuickMenuActivity, PlayerPages, HubScreens, GuideScreen) - one per run with its
FullText test; or H12 once the owner picks.

**22 Nov 22:20 UTC, 695a (cloud, v0.6.74 turn):** Verify 310 on 6e8e88b (694a): ubuntu-latest GREEN, ubuntu-24.04 Maven 429 infra
(re-run requested). P2 left alone (batch 1 owner review pending). P13 rule 11: TwinDesk.kt (Twin desk home) 4 TRUNCATE -> 0 via
FullText (reserve = false). TwinDeskFullText695aTest. TRUNCATE 124 -> 120. NOT compiled in the cloud (Maven 429) - CI first compile. HW-24 695a (KONKR).
NEXT: check Verify 310 on this push FIRST (red in TwinDesk / the new test -> fix). Then P2 turn if the owner has reviewed
batch 1; v0.6.74: P13 next TRUNCATE files - WidgetBoard (2), then the next file static_checks lists - one per run with its FullText test; or H12 once the owner picks.

**22 Nov 21:20 UTC, 694a (cloud, v0.6.74 turn):** Verify 310 GREEN on 24d51ab (693a, both runners). P2 left alone (batch 1 owner
review pending). P13 rule 11: RowInfoPanel.kt (home row info panel + news card) 5 TRUNCATE -> 0 via FullText (reserve = false).
RowInfoPanelFullText694aTest. TRUNCATE 129 -> 124. NOT compiled in the cloud (Maven 429) - CI first compile. HW-24 694a (KONKR).
NEXT: check Verify 310 on this push FIRST (red in RowInfoPanel / the new test -> fix). Then P2 turn if the owner has reviewed
batch 1; v0.6.74: P13 next TRUNCATE files - TwinDesk (4), WidgetBoard - one per run with its FullText test; or H12 once the owner picks.

**22 Nov 20:20 UTC, 693a (cloud, v0.6.74 turn):** Verify 310 on 74035e5 (692b): ubuntu-latest GREEN, ubuntu-24.04 infra (Robolectric
jar transform). P2 left alone (batch 1 owner review pending). P13 rule 11: MixerActivity.kt (volume mixer) 7 TRUNCATE -> 0 via
FullText (reserve = false). MixerFullText693aTest. TRUNCATE 136 -> 129. Cloud compile PASSED; test run blocked by Maven 429 - CI runs it.
NEXT: check Verify 310 on this push FIRST (red in MixerActivity / the new test -> fix). Then P2 turn if the owner has reviewed
batch 1; v0.6.74: P13 next TRUNCATE files - RowInfoPanel (5), TwinDesk (4) - one per run with its FullText test; or H12 once the owner picks.

**22 Nov 19:20 UTC, 692b (cloud, v0.6.74 turn):** Verify 310 on 956b1d8 RED on both runners: GameCardFullText692aTest 2 of 3
failed (test harness slip - its `[^)]*` regex stopped at `copy(alpha = ...)` / `if (veryTight)`; GameCard.kt itself is right, the
rest of the suite 2734 passed). Fixed the matcher (bounded to one FullText call, same assertions; Python mirror 1/1/1). Cloud compile
blocked (Maven 429). No app change.
NEXT: check Verify 310 on this push FIRST (still red in GameCardFullText692aTest -> fix the test again). Then P2 turn if the owner
has reviewed batch 1; v0.6.74: P13 next TRUNCATE files - MixerActivity (7), RowInfoPanel (5), TwinDesk (4) - one per run.

**22 Nov 18:30 UTC, 692a (cloud, v0.6.74 turn):** Verify 310 on 673012d (691a): ubuntu-latest GREEN, ubuntu-24.04 Maven 429 infra
(re-run requested). P2 left alone (batch 1 owner review pending). P13 rule 11: GameCard.kt 7 TRUNCATE -> 0 via FullText
(reserve = false). GameCardFullText692aTest. TRUNCATE 143 -> 136. NOT compiled in the cloud (plugin resolution blocked) - CI first
compile. HW-24 692a (KONKR).
NEXT: check Verify 310 on this push FIRST (red in GameCard / the new test -> fix). Then P2 turn if the owner has reviewed batch 1;
v0.6.74: P13 next TRUNCATE files - MixerActivity (7), RowInfoPanel (5), TwinDesk (4) - one file per run with its FullText test;
or H12 once the owner picks.

**22 Nov 17:30 UTC, 691a (cloud, v0.6.74 turn):** Verify 310 on c166ca7 (690a): ubuntu-latest GREEN (690a compiles + passes),
ubuntu-24.04 Maven 429 infra (re-run requested). P2 left alone (batch 1 owner review pending). P13 rule 11: TwinIcons.kt 7 TRUNCATE
-> 0 via FullText (reserve = false). TwinIconsFullText691aTest. TRUNCATE 150 -> 143. NOT compiled in the cloud (Maven 429 x2) - CI
first compile. HW-24 691a (KONKR).
NEXT: check Verify 310 on this push FIRST (red in TwinIcons / the new test -> fix). Then P2 turn if the owner has reviewed batch 1;
v0.6.74: P13 next TRUNCATE files - GameCard (7), MixerActivity (7), RowInfoPanel (5), TwinDesk (4) - one file per run with its
FullText test; or H12 once the owner picks.

**22 Nov 16:40 UTC, 690a (cloud, v0.6.74 turn):** Verify 310 GREEN on 919d362 (689a). P2 left alone (batch 1 owner review still
pending). P13 rule 11: SecondScreenVolt.kt (Volt second screen) 8 TRUNCATE -> 0 via FullText (reserve = false).
SecondScreenVoltFullText690aTest. TRUNCATE 158 -> 150. NOT compiled in the cloud (Maven 429 x2) - CI is the first compile. HW-26 (Thor).
NEXT: check Verify 310 on this push FIRST (red in SecondScreenVolt / the new test -> fix). Then P2 turn if the owner has reviewed
batch 1; v0.6.74: P13 next TRUNCATE files - TwinIcons (7), GameCard (7), MixerActivity (7), RowInfoPanel (5) - one file per run
with its FullText test; or H12 once the owner picks.

**22 Nov 15:50 UTC, 689a (cloud, v0.6.74 turn):** Verify 310 on ce88244 (688b): ubuntu-latest GREEN (TV x1.3 guard passes),
ubuntu-24.04 Maven 429 infra (re-run requested). H16 is now guarded on 53/53; D357 + item 6 done earlier; H12 waits on the owner's
reveal choice -> P13 rule 11: GameDetailsOverlay.kt (Classic details window) 13 TRUNCATE -> 1 marked (FullText; comments wrap);
summary no longer cut without a More chip. GameDetailsFullText689aTest 3/3 (cloud compiled). TRUNCATE 171 -> 158. HW-24 689a.
NEXT: check Verify 310 on this push FIRST (red -> fix). Then P2 turn if the owner has reviewed batch 1 (leave a live session's P2
branch alone); v0.6.74: P13 next TRUNCATE files - SecondScreenVolt (8), TwinIcons (7), GameCard (7), MixerActivity (7), RowInfoPanel
(5) - one file per run with its FullText test; or H12 once the owner picks.

**22 Nov 14:40 UTC, 688a (cloud, v0.6.74 turn):** Verify 310 GREEN on b44e5e0 (687a). H16: TV room added to WindowWordsSurvey682Test
(NEXT_688; the test sets TvScreen.rect to the set's screen at KONKR size) + tvWindowOpensOnTheSet. 53/53 layouts listed. NOT
compiled in the cloud (Maven Central 429 x2) - CI is the first compile.
688b (same run): CI on b257ffe compiled it; TV x1.3 otherPages found a REAL cut (Achievements page on the set: 'Set order' 20 px,
names 80 px) -> WindowTrophies narrow form < 240 dp (order on its own line, points under a 28 dp badge). Uncompiled in the cloud.
NEXT: check Verify 310 on 688b FIRST. Red in WindowWordsSurvey682Test[TV ...] = either a harness slip (fix the test) or a real TV
window cut (fix the window, keep the guard). Then P2 turn if the owner has reviewed batch 1 (a live session pushed P2 batch 3 at
13:34 UTC - leave that branch to it); v0.6.74: item 5 badgecheck (D357) if still open, then H12.

**22 Nov 13:50 UTC, 687a (cloud, v0.6.74 turn):** fcb4998's Verify 310 red = infra (one runner's dependency transform failed,
no test ran; the other runner GREEN); cbf0226 (686a) Verify 310 was still running. H16: Terminal, Arcade, Arctic, Artlib, Custom +
the shared-scene layouts Grid, Console stack, Coverflow details, Twin desk under all four window guards x1.0 + x1.3 (NEXT_687) -
all clean, no app change. 52 of 53 layouts guarded (TV room left: needs tvRect).
NEXT: check Verify 310 on cbf0226 and this push FIRST (red -> fix). Then P2 turn if the owner has reviewed batch 1 (else skip);
v0.6.74: H16 TV room window (a test harness that opens it on a tvRect), then item 5 badgecheck (D357) if still open, then H12.

**22 Nov 12:20 UTC, 686a (cloud, v0.6.74 turn):** Verify 310 GREEN on 5e76521 (685a). P2 left alone (a live session pushed the P2
branch 12:03 UTC; its Verify 310 there is red - that session's to fix). H16: Orbit, Airy, Blades, Camera, Magazine, Shop under all
four window guards x1.0 + x1.3 (NEXT_686) - Orbit x1.3 key label "ACHIEVEMENTS" broke inside the word: fixed (odLabelFit,
OrbitKeyLabel686Test); Blades' turned edge name was a guard false positive - parent-clip guard now transform-aware (turned
fixtures in ParentClipProbe683Test). 42 window scenes guarded. HW-24 686a (~1 min).
NEXT: check Verify 310 on this push FIRST (red -> fix). If the P2 branch is still red and its last push is > 3 h old with no
live session, look at its failure (report only; P2 pushes only per NATIVE_PAGES_P2.md). Then v0.6.74: H16 last windows - Terminal,
Arcade, Arctic, Artlib, Custom, then Grid / TV / Consoles / Twin desk / Cover details if they have their own scene.

**18 Nov 20:55 UTC, 685a (cloud, v0.6.74 turn):** Verify 310 GREEN on 86f15c7 (684a). P2 left alone (batch 1 waits on its
hardware re-check + the owner's look). H16: Cards, Greencore, Box, File select, Storage, Caseflow under all four window guards
x1.0 + x1.3 (NEXT_685). Two real findings fixed: Storage's fact label "LAST PLAYED" lost "PLAYED" at x1.3 (labels WholeWordsText);
Card binder's turned-away front stayed laid out at alpha 0 (no longer composed once away - cardFrontAway derived state;
CardFront685Test). 36 window scenes guarded. HW-24 685a (~1 min).
NEXT: check Verify 310 on this push FIRST (red -> fix). Then P2 turn only if the owner has reviewed batch 1 / named batch 2's next
layout (else skip it); v0.6.74: H16 next batch - Orbit, Airy, Blades, Camera, Magazine, Shop, then Terminal, Arcade, Arctic,
Artlib, Custom, Grid / TV / Consoles / Twin desk / Cover details if they have their own scene.

**18 Nov 19:55 UTC, 684a (cloud, v0.6.74 turn):** Verify 310 on 5f46185: ubuntu-24.04 GREEN, ubuntu-latest Maven 429 (infra;
failed job re-run requested). P2 left alone: batch 1's 682s visual pass waits on its hardware re-check + the owner's look (owner
"2" = batch 1 fixes first); Spotlight is "if wanted". H16: Circuit, Timeline, Shelf, Stacks, Crate, Discs, Cube under all four
window guards x1.0 + x1.3 (NEXT_684) - Timeline's no-art plate broke "PlayStati / on 2": fixed (SceneNoArt drops a caption that
cannot stay whole where the console is named beside it; NoArtCaption684Test). 30 window scenes guarded. HW-24 684a (~1 min).
NEXT: check Verify 310 on this push FIRST (red -> fix). Then P2 turn only if the owner has reviewed batch 1 / named batch 2's next
layout (else skip it); v0.6.74: H16 next batch - Cards, Greencore, Box, File select, Storage, Caseflow, then Orbit, Airy, Blades,
Camera, Magazine, Shop, Terminal, Arcade, Arctic, Artlib, Custom.

**18 Nov 18:40 UTC, 683a (cloud, v0.6.74 turn):** Verify 310 GREEN on 4784060 (682u). Live P2 push 18:16 UTC (< 1 h) -> v0.6.74
turn. H16: Classic, Tiles (Grid shares its Tile card), Dash, Side, Spot, Library added to WindowWordsSurvey682Test (NEXT_683) -
all four guards x1.0 + x1.3 clean (48 cases, cloud compiled), no app change. 24 window scenes guarded.
NEXT: check Verify 310 on this push FIRST (red -> fix). Then P2 turn if the last P2 push (18:16 UTC) is > 1 h old; else H16 next
batch: Circuit (Chip sheet), Timeline, Shelf family (Index card), Crate (Record sleeve), Discs, Cube - then Cards, Greencore, Box,
File select, Storage, Caseflow, Orbit, Airy, Blades, Camera, Magazine, Shop, Terminal, Arcade, Arctic, Artlib, Custom.

**18 Nov 17:25 UTC, 682u (cloud, CI fix):** Verify 310 on 4aa599a (682t) RED on both runners: Batch513Test.
narrowWindowsKeepWholeWords searched Night's old `WholeWordsText(value ?: "—"` text (682t made it `shown`, beside or stacked).
Guard updated to the same intent (val shown + exactly 2 WholeWordsText(shown ...)); app unchanged. Cloud Gradle blocked (Maven
429) - CI runs it. NEXT: check Verify 310 on this push FIRST (red -> fix). Then the turn order from 682t's NEXT below (P2 turn if
the live session's last P2 push - e45fe3e 17:13 UTC - is > 1 h old, else H16 next windows).

**18 Nov 16:50 UTC, 682t (cloud, v0.6.74 turn):** Verify 310 GREEN on 1a66808 (both runners; 682s page guard clean). Live P2
push 15:47 UTC (< 1 h) -> v0.6.74 turn. H16 high-priority set (Pocket Classic, Pocket Ribbon, Pocket Quest, Night, Bar) added to
WindowWordsSurvey682Test (all four guards, x1.0 + x1.3): two real x1.3 word breaks fixed in code - Night SYSTEM value stacks under
its label when it cannot fit beside it; SceneFactsBody values are WholeWordsText (Pocket ribbon CONSOLE fact). 18 windows clean
(cloud compile + 144 cases). HW-24 extended (682t). Handheld NOT TESTED.
NEXT: check Verify 310 on this push FIRST (red -> fix). Then P2 turn if the live session's last P2 push (15:47 UTC) is > 1 h old
(batch 2 review / batch 3 per NATIVE_PAGES_P2.md, merge the dev line into the P2 branch first); else v0.6.74 turn: H16 next
windows not yet under the guards (the remaining 35 of 53: e.g. Classic, Tiles, Grid, Dash, Side, Spot, Library, Circuit,
Timeline, Shelf family, Crate, Discs, Cube...) in batches of ~6, or H12 once the owner picks.

**18 Nov 15:40 UTC, 682s (cloud, v0.6.74 turn):** Verify 310 on fb0cd09: ubuntu-latest GREEN (682m More guard passes, 6 windows
clean), the other runner Maven infra. Live P2 push 15:13 UTC (< 1 h) -> v0.6.74 turn. H16: WindowWordsSurvey682Test.otherPages -
Details / Options / Achievements of the 13 windows (Hero .. Cinema; Cross achievements = home column, skipped) under both guards,
x1.0 + x1.3, real option rows + long achievement titles; opened by the new QA page hook (TestHooks.winPage /
voltcap_test_winpage, through the window's own callbacks). Cloud compile BLOCKED (Maven x2) - CI compiles first.
NEXT: check Verify 310 on this push FIRST: compile error / "page did not open" -> fix the test or hook; words / parent finding
-> a real H16 cut on a page: fix that page (test fails on the old code). Then P2 turn if the live session's last P2 push is
> 1 h old (batch 2 is implemented by the live session - next is its review / batch 3 per NATIVE_PAGES_P2.md); else v0.6.74 turn:
H16 next is the owner's high-priority set (Pocket Classic, Pocket Ribbon, Night, Pocket, Bar) under the same three guards, or H12
once the owner picks.

**18 Nov 14:45 UTC, 682m (cloud, v0.6.74 turn):** Verify 310 GREEN on 4460aa3. Live P2 push 13:59 UTC (< 1 h) -> v0.6.74 turn.
H16: WindowWordsSurvey682Test.morePage - the 6 of the 13 windows with More (Hero, Hero tiles, Live, Hub, Twin icons, Twin
showcase) opened, More opened via TestHooks.winMore, both guards on what it reveals, x1.0 + x1.3 (12 cases; 14 skipped, no
More). Cloud compile BLOCKED (Maven 429 x2) - CI compiles + runs it first. Test only.
NEXT: check Verify 310 on this push FIRST: compile error / "More did not open" -> fix the test; a words / parent finding -> a
real H16 cut on a More section: fix the window (test fails on the old code). Then P2 turn (batch 2, Cube first; merge the dev
line into the P2 branch first) if the live session's last P2 push (13:59 UTC) is > 1 h old. Following v0.6.74 turn: the same
guards for the windows' own Details / Achievements / Options pages, or H12 once the owner picks.

**18 Nov 13:20 UTC, 682l (cloud, v0.6.74 turn):** Verify 310 GREEN on eec7217. Live P2 push 12:40 UTC (< 1 h) -> v0.6.74 turn.
H16: WindowWordsSurvey682Test.parentClip - texts of the 13 windows (Hero .. Cinema) checked for a PARENT cutting them (box vs
visible bounds in the root, beyond the box's empty room; sliding lines / scrolling content excluded). All 26 cases CLEAN (cloud
compile + run), now a guard; ParentClipProbe683Test 2/2 proves it finds a real cut. Test only, no window changed.
NEXT: check Verify 310 on this push (red -> fix first). Next run = P2 turn (batch 2, Cube first; merge the dev line into the P2
branch first) if the live session's last P2 push (12:40 UTC) is > 1 h old - it will be. Following v0.6.74 turn: extend the H16
survey to the More / second page of each window (the k678n13 sheet compares first / More pages), or the H12 reveal once the
owner picks NOW / A / B.

**18 Nov 12:40 UTC, 682k (cloud, v0.6.74 turn):** CI: Verify 310 on 16a1388 (682j) RED on code - Batch489Test: StageSlot's
turned layer lacked flatTextFor3d() (D285). Fixed (AchievementFx.kt); cloud compile OK, Batch489Test 6/6 + RevealSheet682jTest 2/2,
static PASS. Storyboard read: proposals draw correctly -> docs/evidence/h12_reveal_682k/storyboard.png + owner action (NOW / A / B /
mix). H12 reveal waits on the owner; nothing wired.
NEXT: check Verify 310 on this push (red -> fix first). Next run = P2 turn (batch 2, Cube first) if the live session's last P2 push
(11:54 UTC, 682j focus hand-over) is > 1 h old; else v0.6.74 turn: more H16 windows from the k678n13 sheet (parent clipping / More
pages, which the survey does not cover) or item 5 badgecheck (D357) if not done.

**18 Nov 11:50 UTC, 682j (cloud, v0.6.74 turn):** CI: Verify 310 on 67b08fd red = Maven 429 on one runner (infra; the other
ran the full suite GREEN). H12 reveal / trophy redesign comparison: StageSlot now draws a pure pose (RevealPose, voltRevealPose =
the old formulas, unchanged look); RevealSheet682jTest renders NOW vs proposals A "Trophy flip" / B "Glow burst" (test-only) to
app/build/reports/tests/h12-reveal/storyboard.png (CI test-results artifact). Cloud compile blocked (Maven 429 x2) - CI compiles.
NEXT: check Verify 310 on this push (compile / RevealSheet682jTest red -> fix first); if green, download the storyboard, look at it
and fix any drawing problems in the proposals; then it goes to the owner as a design decision (A / B / keep NOW / mix) - an
owner action, not decided by the run. Next run = P2 turn (batch 2, Cube first) if the live session's last P2 push (10:39 UTC)
is > 1 h old.

**18 Nov 11:05 UTC, 682g (cloud, v0.6.74 turn):** H16 survey read again (Verify 310 GREEN on cc229b9): all 187 "cuts" had
"text" = the max width - MultiParagraph.width is the layout width, not the glyphs. Survey now measures the inked width (widest
line); cloud compile + run: all 13 windows (Hero .. Cinema) CLEAN at x1.0 and x1.3 on their first page; the survey is now a guard
(any finding fails). Test only; no window needed a change by this measure (parent clipping / More pages not covered).
NEXT: check Verify 310 on this push. Next run = P2 turn (batch 2, Cube first) if the live session's last P2 push is > 1 h old;
else v0.6.74 turn: H12 reveal / trophy redesign comparison (the 682e anchor for Library / Bar / Dash was done by the live
session, a4382ac). Also read P2 re-review 44280831400 on the next P2 turn.

**18 Nov 09:40 UTC, 682b (cloud, v0.6.74 turn):** read the 682 survey (Verify 310 GREEN on ebe8e55): nearly every label of all 13
windows listed as "cut at its box's edge", identical at x1.0 / x1.3, no broken word - an unreliable signal, so NO window was
changed. WindowWordsSurvey682Test now counts a cut only at >= 1 px past the box or dropped lines (cutAtEdge, pure test) and prints
each finding's numbers. Cloud compile failed (Maven resolution x2) - CI compiles it.
NEXT: check Verify 310 on this push (compile / SurveyRule682Test red -> fix first). Then read the new findings from the
"update-310-test-results-*" artifact (TEST-...WindowWordsSurvey682Test.xml system-out): if findings remain with text width <= box
width they are still artefacts (compare against a node-bounds check before fixing anything); real ones -> fix in list order (Hero,
Hero tiles, Live, Cross ...) with a test that fails on the old code. Next run = P2 turn only if the live session's last P2 push
(09:04 UTC) is > 1 h old.

**18 Nov 08:45 UTC, 682 (cloud, v0.6.74 turn):** H16 next windows. The k678n13 review PDF is not in the repo and the cloud could
not compile (Maven Central 429 x3), so this run added WindowWordsSurvey682Test (test only, app unchanged): Hero, Hero tiles, Live,
Cross, Hub, Resume, Channel, Twin icons, Twin showcase, Memory card, Bubbles, Panels, Cinema at KONKR 640 x 427 dp, x1.0 + x1.3 -
every text broken inside a word or cut at its edge (one-line sliding text excluded) -> app/build/reports/h16-survey/findings.txt in
CI's "update-310-test-results-*" artifact + the test stdout. A live session pushed P2 682a / 682c to `claude/v0674-native-pages`
at ~08:07 UTC - left alone.
NEXT: check Verify 310 on this push (first compile of the survey: a compile / "no text reached" failure -> fix first). Next run =
P2 turn ONLY if the live session's last P2 push is > 1 h old (else another v0.6.74 turn). Next v0.6.74 turn: download the survey
findings (gh api .../actions/runs/<id>/artifacts) and fix the first windows in list order, each with a test that fails on the old
code (pattern: BarDockLabels681wTest / NightStatLabels681tTest); "clean" windows need no change.

**18 Nov 07:45 UTC, 681z (cloud, P2 turn):** dev line merged into `claude/v0674-native-pages`; emulator review 44247782675 READ
(TV Fastext keys whole at both scales - 681x confirmed; Play guard held; App category still empty on the emulator -> destructive
confirmation NOT TESTED there). (c) DONE on the P2 branch (d14710b): grade_trial.py grades review runs on their own walks (P2:
p2r13 / p2r10, font reviews: e679r13 / e679r10) + P2 contact sheets; p2review.sh restore no false diff=1 on an empty keep folder,
App category checked. Selftest 224/224. Dev CI: Verify 310 GREEN on 19bc1e0 (681y); 79bb40d red = Maven 429 (infra, lock commit).
NEXT: check Verify 310 on this push and on d14710b (P2). Next run = v0.6.74 turn: H12 rest (reveal / trophy animation redesign -
comparison for the owner first) or more H16 windows from the k678n13 sheet (Hero, Hero tiles, Live, Cross). Next P2 turn: batch 2
(Cube first) + dispatch "P2:TV CUBE" review (expect NOT TESTED until an app fixture exists for the confirmation).

**18 Nov 06:50 UTC, 681y (cloud, v0.6.74 turn):** item 7 H12 - the Achievements page's progress (Power cells battery, Trophy
header, summary bar) now counts each new unlock as its badge locks in on the stage and fills up to it; nothing new = no fill. The
stage's waits / sound cues follow the motion speed (Off / reduced motion = every badge at once). AchProgressCount681yTest 5/5 +
Ach suites PASS (cloud compile + Robolectric). HW-25 queued (KONKR, with HW-24). Dev CI: Verify 310 on 29f0c87 (docs) was running;
last code push 18a8cbc GREEN. P2 cf40522 Verify 310 GREEN; its emulator review 44247782675 ended "failure" (review runs end NOT
TESTED by design until (c) grade_trial.py review mode is done) - read it next P2 turn.
NEXT: check Verify 310 on this push (red -> fix first). Next run = P2 turn: read review 44247782675 (TV keys), (c) grade_trial.py
review-mode verdict, then batch 2 (Cube first). Following v0.6.74 turn: H12 rest (reveal / trophy animation redesign needs an
owner look - comparison first) or more H16 windows from the k678n13 sheet (Hero, Hero tiles, Live, Cross ...).

**18 Nov 05:20 UTC, 681x (cloud, P2 turn):** dev line merged into `claude/v0674-native-pages` (4a3e313); findings (a) + (b) of
review 44233448467 fixed there: (a) TV Fastext keys each as wide as their own word, short words PIC / OPTS when the four full
words cannot fit (WinPagesTv.kt ttxKeyPlan) - TvFastextKeys681xRoboTest; (b) p2review.sh playfocused() fixed strings only
(toybox) - selftest TestP2ReviewPlayFocused681x 5, 216/216. Cloud compile blocked by Maven 429 (3 tries); CI compiled it: on
049dd75 the new robo test failed (fixture game had no achievements -> no TROPHIES key), fixed in cf40522 - Verify 310 ubuntu-24.04
GREEN, the other runner still running at hand-off. (c) review runs end NOT TESTED (grade_trial.py e529 expectation) - still open.
Emulator review "P2:TV" dispatched on cf40522. Dev-line CI GREEN on 18a8cbc (681w).
NEXT: check Verify 310 on cf40522 (P2) and on this push. Next run = v0.6.74 turn: H12 achievements animations (or more H16
windows from the k678n13 sheet). Next P2 turn: read the P2:TV review; (c) grade_trial.py review-mode verdict; then batch 2 (Cube first).

**18 Nov 04:20 UTC, 681w (cloud, v0.6.74 turn):** H16 Bar window - the dock keys' names were cramped at x1.3 ("Achievements"
broke inside the word in the fixed 78 dp column). Each key now takes the widest word's width + 6 dp (>= 78 dp), the name fills
its key; BarDockLabels681wTest 2/2 (fails on the old code), Readability476 15, WinFocus 448 PASS (cloud). HW-24 extended (Bar x1.3).
Dev-line CI GREEN on 87f4150 (681v). P2 emulator review 44233448467 READ (P2:TV CRATE on 00b9c1d): crate x1.3 tracklist now
two whole tracks (681s confirmed on the emulator); TV pages have a body. Still open on the P2 branch: (a) TV Fastext keys at
equal widths slide - "PICTU / TROPH / OPTIO" at x1.3, "TROPHIE" at x1.0 (WinPagesTv.kt ttxFastext row: weight each key by its
word, e.g. Modifier.weight(ttxKey(pg).length)); (b) p2review.sh playfocused() did not match on the device - the TV
app_options_last dump has Play focused yet A was pressed (1 blocked launch per scale): replace the `[^]]` ERE with a toybox-safe
check (split objects on '}' and grep -F '"label":"Play",' then '"focused":true') + a selftest; (c) review runs end NOT TESTED
(grade_trial.py expects e529 runs - known 679a follow-up). Record (a)-(c) in NATIVE_PAGES_P2.md on that branch.
NEXT: check Verify 310 on this push (red -> fix first). Next run = P2 turn: (a) + (b) above, re-run the review, then batch 2
(Cube first). Following v0.6.74 turn: H12 achievements animations (or more H16 windows from the k678n13 sheet).

**18 Nov 03:55 UTC, 681v (cloud, P2 turn):** batch 1 finding 3 TRACED from the emulator run's own dumps: the TV room "blocked
launch" was the review script - the App category did not take, ten Downs on a 9-row Options page left the list for Play and the
"arming" A pressed it (p2review.sh now presses A only on a focused option row). The dumps also showed a real TV defect, now FIXED
on `claude/v0674-native-pages` (d3d06b0, merged with the dev line = 00b9c1d): on the set's screen the teletext pages had no body
(achievement rows / story / options 0 px, VOLTCAP TXT one letter a line at x1.3, Down fell to the buttons). Under 200 dp: one-line
masthead, one-line Fastext keys, one-line P200 bar row, 10 sp rows. TvTeletextRoom681vRoboTest 4/4 + NativePages681a 11,
NativeConfirm681 8, AchFirstRow 107, WinFocus 448 PASS (cloud). Emulator review "P2:TV CRATE" dispatched on the P2 branch.
Dev-line CI GREEN on 18106e4 (681u code). HW-24 extended (TV room).
NEXT: check Verify 310 on this push and the P2 push; read the emulator review (TV + crate re-capture) and record it in
NATIVE_PAGES_P2.md. Next run = v0.6.74 turn: Bar window labels cramped (H16; not in the pixel-font branch's files), then H12
achievements animations. Next P2 turn: finding 2 (App category in the review script) or batch 2 (Cube first).

**18 Nov 03:15 UTC, 681u (cloud):** Verify 310 was RED on 1888e41 (681t): the new NightStatLabels681tTest showed ACHIEVED still on
2 lines at x1.3 (50 % cap too narrow). Fixed: cap 55 %, gap 4 dp, 3 % spare; compiled + NightStatLabels681tTest 3/3 and related
PASS in the cloud. NEXT: check Verify 310 on this push first; then the P2 turn below.

**18 Nov 02:45 UTC, 681t (cloud, v0.6.74 turn):** Night window stats labels (ACHIEVED / RELEASED) keep whole words at x1.3:
label column = widest label + gap (>= old 96 dp / 40 %, <= half the row), labels step down to 10 sp only if needed; the
achievements bar follows the column (WinScenes.kt; NightStatLabels681tTest new, Batch513Test pin updated). NOT compiled in the
cloud (Maven Central 429 twice) - CI on this push is the first compile: red on code -> fix first. HW-24 extended (Night x1.3).
NEXT: check Verify 310 on this push. Next run = P2 turn: batch 1 finding 3 (TV room blocked launch - trace which press reached
Play) or batch 2 (Cube first). Following v0.6.74 turn: Bar window labels cramped (H16; check it is not in the pixel-font
branch's files: WinScenesStyled / WinScenesArcade / WinScenesConsole are), then H12 achievements animations.

**18 Nov 02:10 UTC, 681s (cloud, P2 turn - the live session's last P2 push was > 1 h old):** dev line merged into
`claude/v0674-native-pages`; batch 1 finding 1 (Record crate x1.3: the insert showed one track and a part) fixed on that branch
(0c7ad56): compact sleeve header on the sleeve's own pages, count beside TRACKLIST, ORDER stamp on the first side's line.
CrateInsertRoom681sRoboTest 4/4 + crate-related suites PASS in the cloud; Info unchanged. Emulator re-capture + owner look pending.
Dev-line CI GREEN on b0bc839.
NEXT: check Verify 310 on this push and on 0c7ad56 (P2) - red on code -> fix first. Next run = v0.6.74 turn: item 7 H12
achievements animations or more H16 windows (Bar labels cramped, Night ACHIEVED / RELEASED) if they don't touch the pixel-font
branch's files. Next P2 turn: finding 3 (TV room blocked launch - trace which press reached Play) or batch 2 (Cube first).

**18 Nov 00:20 UTC, 681q (cloud):** Verify 310 RED on f303833 (681p) = infra: Maven Central 429 on ubuntu-latest, the 24.04 image
passed every test. Fixed in CI: tools/ci/retry_infra.py re-runs Gradle only after a package-server 429 / 5xx with no code failure
in the output (selftest TestCiRetryInfra681q 4, 211/211). App unchanged. 681p's code itself was green on the other runner.
NEXT: check Verify 310 on the 681q push (red on code -> fix first; a retry warning annotation is expected only on 429s). Then the
P2 turn of the alternation IF the live session's last P2 push (e6e16fd 23:31 UTC) is > 1 h old - batch 1 open finding: crate
x1.3 insert (see NATIVE_PAGES_P2.md on that branch); else v0.6.74 item 7 H12 / more H16 windows (Bar labels, Night).

**17 Nov 23:55 UTC, 681p (cloud, v0.6.74 turn - live session was on the P2 branch < 1 h ago):** item 4 window polish (H16) -
D354 Pocket Quest "Play game" wrap fixed in code (one-line keys while two show, key words 12 -> readable 10 sp at large text;
twoLineOnlyWhenAlone opt-in, other layouts unchanged). PqPlayKeyOneLine681pTest 5/5 + related PASS in the cloud. The status
pill part of D354 is the rule-11 slide in a narrow pill - owner look on HW-24. Dev CI GREEN on c342492.
NEXT: check Verify 310 on the 681p push (red -> fix first); next run = P2 turn if the live session left the P2 branch (> 1 h
since its last commit), else v0.6.74 item 7 H12 achievements animations / more H16 windows (Bar labels cramped, Night
ACHIEVED / RELEASED) if they don't touch the pixel-font branch's files.

**17 Nov 22:35 UTC, 681e (cloud, v0.6.74 turn of the alternation):** item 6 (profile picture) - the cloud-testable gap closed in
QA tooling: statuscheck.py now grades each profile picture's place ("avatar place"): wholly on screen, the status bar's picture
inside the status bar, no text over any picture (its own initial excluded); the corner is reported as evidence (layout pictures
live in Edit-Home-movable parts, so their corner is not graded). TestAvatarPlace681e 4 (3 fail on the old checker); selftest
207/207; static PASS. Hardware: rides along HW-24 (statuscheck over that session's dumps). Dev-line CI GREEN on c71c8cb. A live
session was pushing to claude/v0674-native-pages during this run (fd32d24) - the cloud stayed off that branch.
NEXT: check Verify 310 on the 681e push (red -> fix first); next run = P2 turn (Resume / TV room) only if the live session is
not on the P2 branch (check its newest commit time; < 1 h -> take the v0.6.74 list instead: item 4 window polish H16 - D354
Pocket Quest "Play game" wrap / status pill cut if it doesn't touch the pixel-font branch's files; then item 7 H12 animations).

**17 Nov 21:45 UTC, 681cr (cloud):** P2 branch CI was RED on 1a6c5c9 (AchFirstRowRoboTest CRATE x1.3: the tracklist's first
track cut). Fixed on `claude/v0674-native-pages` (64e5955, rebased on the live session's 681b-681d): the title has the whole line,
the leader runs under it; large text keeps a closed track's words to one sliding line. AchFirstRow 87/87 + NativePages681a 8/8
PASS in the cloud. A live session was pushing P2 (681b-d) at the same time without the dev-line lock: the cloud run named its
commit 681cr to keep clear of its numbers. NEXT: check Verify 310 on 64e5955 (P2) and on the dev line; next run = v0.6.74 list
(alternation; P2 batch 1 continues with Resume / TV room unless the live session has them).

**17 Nov 20:35 UTC, 680b (cloud):** priority 5 / D357 done in QA tooling - badgecheck no longer fails badges of a tile a
carousel scrolled partly off-screen (measured at the tile's scale; a badge cut inside a whole tile still FAILs) nor of covers
stacked behind a larger one (NOT TESTED, never PASS); real collisions still FAIL. Selftest TestBadgeCheckCarousel680b (6, 3 fail
on the old checker), full QA selftest 203/203, static checks PASS. Confirmation on real dumps rides along HW-24. CI GREEN on
79c11a5. NEXT: check Verify 310 on the 680b push (red -> fix first); items 2-3 (pixel font / badge styles) stay on their
live-session branches pending the owner's comparison OK; then 6 profile picture corner (statuscheck guard - look for a cloud-
testable gap), 4 window polish (H16), 7 achievements animations (H12).

**17 Nov 20:10 Berlin (679a-c):** owner B1. Pixel font B on `claude/v0674-pixel-font` (PixelType, 9.5 sp minimum) - waits for the owner's OK on pixel_font_final_emulator.pdf before merging into the dev line. Badges: build per docs/claude/BADGE_STYLES_PLAN.md on `claude/v0674-badges` (Classic + compact ladder first, idle test first). Emulator review mode: `gh workflow run emulator-qa-trial.yml --ref <branch> -f review_layouts="..."`.
**17 Nov 19:50 UTC, 679d (cloud):** rule 11 / D353 - Terminal home path, "Directory of" header and preview lines shown whole (FullText, one size; focused root / systems lines slide). TerminalFullText679dTest; NOT compiled in the cloud (Maven 429) - CI first compile. HW-22 queued (KONKR). CI GREEN on e4669b9. NEXT: check Verify 310 on the 679d push (red -> fix first); then D354 Pocket Quest "Play game" wrap / status pill cut (H16, emulator e679r13) if it doesn't touch the pixel-font branch's files, else rule 11 filler (static TRUNCATE 171); pixel font / badges stay on their live-session branches.
**17 Nov 18:55 UTC, 679c (cloud):** H7 / D347 fixed in code - the Console hub category title keeps clear of the Hub's profile row (HubCategoryClear679cTest 4/4 PASS in the cloud; HW-21 on the RP Classic, batched with HW-18). CI GREEN on f3d8362. NEXT: check Verify 310 on the 679c push (red -> fix first); H9 pixel font / H10-H11 badges are owner-decided but live on claude/v0674-pixel-font and claude/v0674-badges (live-session branches - the cloud run never pushes there; continue only on this line if the owner moves them here); then H16 worst popups (Night, Bar) after H9; H12 achievement animations.

**17 Nov 17:45 UTC, 679b (cloud):** H13 Thor dual-screen cloud reading DONE -> docs/claude/THOR_DUAL_SCREEN.md (6 code paths, options A-D for the owner; owner action added). HW-19 rewritten as owner-driven L2 read-only (the QA app blocks every launch). NEXT: check Verify 310 on the 679b push (red -> fix first); owner picks pending (H9 pixel font, H10/H11 badges, H13 fix) - do not implement those before the pick; then H16 worst popups (Night, Bar), H7 Hub density 1.4 (no evidence in the repo: reproduce with a Robolectric density-1.4 HUB layout test first).

**17 Nov 16:35 UTC, 678t (cloud):** dashboard now derives a release equal to releases.json current as PUBLISHED (v0.6.73), NEXT RELEASE = v0.6.74; tools/cloud/test_dashboard.py. NEXT: check Verify 310 on the 678t push (red -> fix first), then v0.6.74: H9 pixel font before / after (branch claude/pixel-font-small), H16 worst popups, H10 / H11 badge mockups, D352 Twin icons badge gap, H12 animations, H13 Thor reading.


**17 Nov 17:20 Berlin: v0.6.73 PUBLISHED** (main a4012212, assets = frozen rc1, KONKR production 0.6.73 upgraded before publication). Dev branch now holds the 0.6.73 pins + RC sums + records -> next PR to main (docs / tooling only). v0.6.74 work may resume on this branch: H9 pixel font before / after, H16 worst popups, H10 / H11 badge mockups, D352, H12 animations, H13 Thor (cloud).

**17 Nov 15:25 (678r, live):** owner handoff merged (matrix H16 53-window polish, H17 Channel; queue: Thor + RP Classic NOT with the owner - never poll / wake). RC is now **eadd00f** (be8865c + Channel D350 one-line sliding name plate; FitText extraLine, default unchanged). CI GREEN push + PR #21; heavy robo 280/280 incl. CHAN x1.3; KONKR Channel x1.3 + x1.0 PASS on qa-0.6.73-678r-ci. WAITING FOR GO. Do not push app changes to this branch while it is the RC head - v0.6.74 work (H9 pixel font before / after, H16 polish, H10 / H11 badge mockups, H12 animations, H13 Thor cloud reading) goes on a v0674 branch.
Updated: 2026-11-17 ~13:40 UTC, updates 678p-q (LIVE session, owner present): D333 fixed (reopen in place) and verified on the KONKR
(60/60 rapid rounds); D351 owner decision (corner X = chrome, popclip changed); v0.6.73 RC prepared - version 0.6.73 / 80, RC commit
be8865c, draft PR #21 to main, CI GREEN, KONKR x1.0 53/53 on the RC build. 678o's Channel fix reverted (Compose never idle on
Channel - CI robo tests); D350 back to v0.6.74. WAITING FOR THE OWNER'S GO - no merge / build-for-release / publish without it
(GO steps: docs/claude/RELEASE_0.6.73_RC.md). NEXT (cloud, while waiting): v0.6.74 work only on a separate branch or after the
release - do NOT push app changes to claude/qa-0672-repair (it is the RC / PR #21 head); docs / QA-tool commits are fine but
keep the RC commit's app tree unchanged. Then D350 (with a Robolectric idle test first), D352, H13 Thor reading.
Updated: 2026-11-17 ~11:55 UTC, update 678o (LIVE session, KONKR by USB, owner present): KONKR full popup gates on the newest build -
x1.0 52/53 (TV rapid A/B, D333), x1.3 52/53 -> Channel fixed (D350) and re-gated PASS; Banner touch boxes fixed (D349, verified);
HW-20 / HW-8 verified, HW-2 all PASS except rapid A/B 9/10, text pages 32/34 (D346 known). D333 cause traced: the last B falls
between the closing and the reopened dialog. NEXT (cloud): 1) check Verify 310 on 94fb3d8 (if red: fix first); 2) D333 fix -
reopen the closing window in place instead of close + new dialog (GameWindow / GamePopup), unit test, then HW-5 re-check with
crate678.sh 10 rounds on CRATE + TV at x1.0 + x1.3 (needs the KONKR); 3) D352 Twin icons badge gap; 4) the earlier priority 5
(H13 Thor dual-screen reading -> docs/claude/THOR_DUAL_SCREEN.md). D351 waits for the owner.
Updated: 2026-11-17 ~08:35 UTC, update 678n (cloud run): Verify 310 was RED on a6cdfc7 (678m) - AchCompact529Test pinned the old
firstFocus line that 678m changed on purpose; the pin now checks both new lines (8/8 + 2/2 PASS in the cloud). NEXT: check Verify
310 on the 678n push (if red: fix first), then priority 5 (H13 Thor dual-screen reading -> docs/claude/THOR_DUAL_SCREEN.md).
Lesson (again): before changing a source line, grep app/src/test for its literal - 678m missed AchCompact529Test.
Updated: 2026-11-17 ~07:50 UTC, update 678m (cloud run): Verify 310 GREEN on 2fa4c92 (678l); priority 4 (H12) first step done -
the Achievements page opened its focus on the first row and scrolled past the new unlocks; it now opens on the new-unlocks stage
(AchNewUnlocksOnOpen678mTest FAILED on old code, PASS now). HW-20 verifies. NEXT: check Verify 310 on the 678m push (if red:
fix first), then priority 5 (H13 Thor dual-screen reading -> docs/claude/THOR_DUAL_SCREEN.md); H12 animations after that.
Before: 2026-11-17 ~06:45 UTC, update 678l (cloud run): Verify 310 GREEN on 9280921 (678k); priority 3 (H6) done in tooling -
pop529 setup makes its own stand-in library (tools/qa/device/standins.sh, run-as fallback, INFRA stop), selftest 190/190;
HW-18 verifies on the Classic. NEXT: check Verify 310 on the 678l push (if red: fix first), then priority 4 (H12 Achievements).
Before: 2026-11-17 ~05:45 UTC, update 678k (cloud run): priority 1 done - Verify 310 GREEN on the merge b76e3ef (gate PASS);
priority 2 (H8) done in code - console homes slide long names instead of "…", ConsoleHubFullText678kTest compiled + PASS;
handheld check added to HW-17. NEXT: check Verify 310 on the 678k push (if red: fix first), then priority 3 (H6 fixture).
Before: Updated: 2026-11-17 ~05:15 UTC, live session: owner decisions - whole words to v0.6.74 (branch claude/whole-words-v0674),
same-row badge direction approved but keep both badges visible (compact variant / adaptive spacing, comparison first). The
v0.6.73 candidate fixes (facet 677l = status bar, one profile picture, Storage facts, Cube facet, line heights, home cuts) are
MERGED into this dev line; CI on the merge decides; focused handheld re-check HW-17.
Before: 2026-11-17 ~04:45 UTC, live session (owner at work, devices disconnected after it): owner's consolidated requests of
17 Nov recorded - matrix section H (H1-H15), backlog section 00, releases.json v0.6.73 / v0.6.74 items + gates, owner actions,
HW-17..HW-19. Candidate fixes for v0.6.73 live on `claude/cube-statusbar-facet` (677d-k; NOT merged into this dev line).
Before: 2026-11-17 ~04:40 UTC, update 678j (cloud run: rule 11 - category screen FullText, PlatformBrowserFullText678jTest;
TRUNCATE 187 -> 180; compiled + PASS in the cloud; HW-16 queued; CI GREEN on 678i; emulator trial STARTED on this commit).
Before: 2026-11-17 ~03:40 UTC, update 678i (cloud run: rule 11 - save-sync prompts FullText, SyncPromptsFullText678iTest;
TRUNCATE 194 -> 187; NOT compiled in the cloud (Maven 429) so CI is its first compile; HW-15 queued; CI GREEN on 678h).
Before: 2026-11-17 ~02:50 UTC, update 678h (cloud run: rule 11 - Settings FullText, SettingsDialogFullText678hTest;
TRUNCATE 211 -> 194; NOT compiled in the cloud (Maven 429) so CI is its first compile; HW-14 queued; trial on 678g READ: PASS).
Before: 2026-11-17 ~01:45 UTC, update 678g (cloud run: rule 11 - Save Vault FullText, SaveExplorerFullText678gTest;
TRUNCATE 224 -> 211; test classpath Maven 429 so CI is the test's first run; HW-13 queued; emulator trial STARTED on this commit).
Before: 2026-11-17 ~00:35 UTC, update 678f (cloud run: CI red on 678e = PopupPolish444Test read the old em line
height of the banner name; test reads the new sp form (same 1.08 rule); compiled + PASS in the cloud; no trial started).
Before: 2026-11-16 ~23:45 UTC, update 678e (cloud run: rule 11 - banner game window FullText, GameWindowBannerFullText678eTest;
TRUNCATE 236 -> 224; popclip gate for the Banner style queued as HW-12; no trial started - next allowed from ~00:45).
Before: 2026-11-16 ~22:40 UTC, update 678d (cloud run: emulator trial 43961010406 on 678c READ - VERDICT PASS, first fully
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
- OWNER 23 Nov 13:32 (newest, wins): docs/MASTER_BACKLOG.md section 0000000 - finish P2 (P2-a..f, live session on
  claude/v0674-native-pages), THEN Tasks 1-4 in order without asking again (Row-position collisions, R1 Focus Position tile, Recomps
  category, START classification), Task 5 (53 native pages) runs on. Hardware steps -> HARDWARE_QA_QUEUE.md. No merge / production
  install / release without the owner's GO.
- OWNER PRIORITIES 17 Nov (backlog section 00 - this order wins over the rule 11 filler below; one item per run, smallest safe
  step first; notify the owner only for hardware, a design decision or a release GO; never a new scheduled task):
  1. DONE 678k (GREEN on b76e3ef). v0.6.73: read "Verify Android update 310" on the merge commit (facet 677l merged into this line, 17 Nov). If red: fix it
     here first. Then record CI in releases.json. The facet branch is done - work on this line only.
  2. DONE 678k in code (HW-17 verifies). H8 (cloud, small, v0.6.73-relevant): on the candidate code, find the Console Hub focused-card title ("... (No Glitc…") and
     the Achievements card ticker ("Class Dismisse"): any TextOverflow.Ellipsis / maxLines clip left -> FullText (rule 11) on
     THIS dev line + a test; note whether the facet already fixed it (676y "Hub ticker rows unbounded").
  3. DONE 678l in tooling (HW-18 verifies). H6 (QA tooling): why pop529 setup finds no fixture game on the RP Classic (files/roms empty after
     voltcap_test_fixture_roms; "no game matches") - read TestHooks fixture code; fix + selftest; unblocks HW-18.
  4. FIRST STEP DONE 678m (HW-20 verifies; animations still open). H12 Achievements page: find what scrolls past "Recently Unlocked" on open (initial scroll / focus request / async load);
     plan + first fix with a Robolectric test; animations after that (reduced motion kept). v0.6.74.
  5. DONE 679b (docs/claude/THOR_DUAL_SCREEN.md; owner picks a fix A-D). H13 Thor dual-screen (cloud reading): CompanionActivity / SecondScreen Presentation lifecycle, launch flags / display
     targeting in GameLauncher, onStop / onPause handling - write findings to docs/claude/THOR_DUAL_SCREEN.md; HW-19 verifies.
  6. H9 pixel font audit (branch `claude/pixel-font-small`): list every pixel-font use + sizes; emulator screenshots may PREVIEW
     only (never evidence). H10 / H11 badge mockups (static images) for the owner's choice.
  - Owner decisions 17 Nov: H4 whole words -> v0.6.74 (work stays on claude/whole-words-v0674; bring it to this line only
    when v0.6.73 is released, then a full gate). H10 badges: direction approved, keep BOTH badges visible - design a compact
    badge variant / adaptive spacing on claude/badge-same-row, make a side-by-side comparison (images) for the owner; no
    implementation approval yet.
  - Owner 17 Nov: the real Voltcap may be used / updated for testing in live sessions (matrix H15; CLAUDE.md L3 safeguards).
- FIRST (678j): BOOTSTRAP OK required. Check "Verify Android update 310" on the 678j push (if red: fix it first). READ the
  emulator trial started on 678j ("[emulator-qa]", ~04:40 UTC; next allowed from ~07:40): e529a / e529b popup gate must stay
  PASS, e529c touch walk as 678d. Then rule 11 filler: StyleTerminal (8) - grep app/src/test for each literal first.
- DONE 678j: category screen text shown whole; CI GREEN on 678i (SUMMARY "Update 678j").
- FIRST (678i): BOOTSTRAP OK required. Check "Verify Android update 310" on the 678i push - FIRST compile of the SyncPrompts
  FullText change + SyncPromptsFullText678iTest (cloud hit Maven 429). If red: fix it first. From ~04:45 UTC START the emulator
  trial (head commit "[emulator-qa]") and read it the run after. Then rule 11 filler: PlatformBrowser (7) or StyleTerminal (8)
  - grep app/src/test for each literal first.
- DONE 678i: save-sync prompts text shown whole; CI GREEN on 678h (SUMMARY "Update 678i").
- FIRST (678h): BOOTSTRAP OK required. Check "Verify Android update 310" on the 678h push - FIRST compile of the SettingsDialog
  FullText change + SettingsDialogFullText678hTest (cloud hit Maven 429). If red: fix it first. Then rule 11 filler: the next
  file in static_checks TRUNCATE (grep app/src/test for each literal first). Emulator trial allowed again from ~04:45 UTC.
- DONE 678h: Settings text shown whole; emulator trial on 678g READ - VERDICT PASS, e529c taps as expected (SUMMARY "Update 678h").
- FIRST (678g): BOOTSTRAP OK required. Check "Verify Android update 310" on the 678g push (SaveExplorerFullText678gTest runs
  there first; if red: first item). READ the emulator trial started on 678g ("[emulator-qa]", ~01:45 UTC; next allowed from
  ~04:45): e529a/e529b must stay PASS; e529c touch log should show "tap '*achievement 1*' at x,y" / "tap 'game settings*'"
  (678d expectations). Then rule 11 filler: SettingsDialog (17) - one run, grep app/src/test for each literal first.
- DONE 678g: Save Vault text shown whole (SUMMARY "Update 678g").
- FIRST (678f): BOOTSTRAP OK required. Check "Verify Android update 310" on the 678f push (678e cc34a59 was RED on
  PopupPolish444Test only - fixed 678f; if still red: first item). START the emulator trial (head commit "[emulator-qa]",
  allowed now - last one 678c ~21:45) and read it the run after (expectations: 678d item below). Then rule 11 filler:
  SaveExplorer (13) or SettingsDialog (17), one per run. Lesson: before changing a line, grep app/src/test for its literal
  text (source tests pin exact strings).
- FIRST (678e): BOOTSTRAP OK required. Check "Verify Android update 310" on the 678e push (if red: first item). From ~00:45 UTC
  START the emulator trial (head commit "[emulator-qa]") and read it the run after (expectations: 678d item below). If the
  trial can run a second popup style within the job time, add BANNER to its popclip STYLES (covers HW-12's geometry on the
  emulator; HW-12 still needs a handheld). Next rule 11 filler: SaveExplorer (13) or SettingsDialog (17), one per run.
- DONE 678e: banner game window text shown whole (SUMMARY "Update 678e").
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
