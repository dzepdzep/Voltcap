# VoltCap handoff (read first, update last)

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
