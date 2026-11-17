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
| Record crate | RECORD_SLEEVE | the sleeve's printed matter pulled out sideways: LINER NOTES (credit block FORMAT / RELEASED / LABEL / STYLE, pressing note, story with a drop cap), TRACKLIST (SIDE A yours A1..., SIDE B to get B1..., badge as centre label, dotted leader to the points, A opens the words like a lyric sheet, ORDER rubber stamp), PRESSING NOTES (numbered stickers, red "!" for removing ones) | 681a, 681b, 681cr | WinFocus CRATE both + ShelfText PASS; AchFirstRow CRATE x1.0 / x1.3 PASS since 681cr (CI RED on 1a6c5c9: the first track cut, 94 of 137 px - title had half the line beside the leader; review docs/claude/review/p2_crate_681cr_tracklist_x13_robo.png) | run 44173674038 x1.0 + x1.3: Info + Liner notes shown (docs/claude/review/p2_crate_681a_emulator.png); Tracklist / Pressing notes NOT captured (the walk stops at Details) | NOT TESTED | pending |
| Classic (Voltcap row) | VOLT_PANEL | the charged panel: DETAILS as a charge readout (facts as labelled meter lines along the bolt stripe, story on the dark glass), ACHIEVEMENTS as battery cells (a cell per achievement filling the stripe, list rows with a bolt for earned, charge % = progress), OPTIONS as power switches (each option a row with a switch-like lit edge in the accent); motion: the panel content charges in from the stripe (left to right wipe) | - | | | | |
| Console hub | HUB_PLATE (HubNameplate, WinSceneFamilies.kt) | the console's nameplate: DETAILS engraved on the plate (facts as stamped labels, story as the plate's back), ACHIEVEMENTS as a row of service medals / screws along the plate with a list below, OPTIONS as the console's switch panel; motion: the plate flips on its hinge | - | | | | |
| Resume & discover | DISCOVER_CARD | the discover feed: DETAILS as "About this game" cards stacked like a feed, ACHIEVEMENTS as a progress feed ("Next up", "Recently earned", "Still to get" shelves of small cards), OPTIONS as a settings sheet in the card's pill style; motion: cards slide up like a feed | - | | | | |
| TV room | (none - the window draws on the TV's screen, GameWindow tvRect) | TELETEXT on the TV: P100 Details, P200 Achievements, P300 Options in teletext blocks (double-height title, coloured rows), Fastext bar (red / green / yellow / blue) for the pages, page number typed in the corner on a change; needs a scene for the TV window first | - | | | | |

## All 53

Native pages **14 / 53 implemented** (13 earlier families + Record crate 681a); hardware-verified: the 13 earlier ones in their
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
