# Hardware QA requests (physical handhelds)

Cloud work never waits for the PC or a handheld. When a change needs a real device, add a request here (and, where the
session can, a GitHub issue labelled `hardware-qa`), then carry on with other work. When the owner says the PC and handhelds
are available, a live session works through the OPEN requests with the PC's remote-device tools: isolated QA app only
(`com.shelf.launcher.qa`), production apps / settings / saves / emulator configurations untouched, device state restored after
every run, results pushed and the requirements matrix updated. A request is DONE only with a real-device PASS; an emulator
result never closes one.

Never assume the PC or devices are connected. Never wake, connect, install to or change a device without the standing
authorization in CLAUDE.md (USB only, QA app only).

Devices: Flip 2 (Retroid Pocket Flip2, 2037b27d, 1920x1080 @ 360 dpi), KONKR (GT78, BW0306N230000845, 960x640 @ 240 dpi),
RP Classic, AYN Thor (3140972f, two screens), Odin.

## Format
```
### HW-<n> <title> - OPEN | IN PROGRESS | DONE | BLOCKED
- Device(s): ...
- Build: commit <sha> on claude/qa-0672-repair; QA build = assembleQa / assembleDebug of that commit
- Why not emulator: ...
- Tests: exact gates / scripts (e.g. pop529.sh run <tag> <off|font=1.3,density=1.15> "<LAYOUTS>" OWN, then popclip.py)
- Result: (filled in by the hardware session: run tags, PASS / FAIL counts, evidence path)
```

## Levels (CLAUDE.md "Hardware QA levels", owner 16 Nov)
Every request states its level: L1 QA app (default, autonomous on authorized USB devices), L2 production read-only (declared
observation only, nothing changed, no input beyond what the plan declares), L3 production modification (the owner's explicit
approval of that step, backup + rollback + certificate check first). An accidental switch to the production app: stop, press
nothing, log a defect. Evidence is labelled QA or PRODUCTION. All requests below are L1 unless they say otherwise.
Owner 16 Nov: replacing an incompatible QA app (uninstalling com.shelf.launcher.qa only - its own test data) is always allowed.

## Requests

### HW-15 Save-sync prompts text shown whole (678i, rule 11) - OPEN
- Device(s): RP Nova or KONKR (batch with HW-14 / HW-13 / HW-12 in the same session). Level L1 (QA app only).
- Build: commit of update 678i on claude/qa-0672-repair; QA build = assembleDebug of that commit.
- Why not emulator: the trial has no Google Drive sign-in, so the sync toast, Drive card, sync log and conflict list never show.
- Tests: at off and font=1.3,density=1.15 in the QA app: the sync toast (title + detail, two lines each), the Drive card
  status line, a game row with a long name, the sync log (time column + two-line entries, a "›" entry still opens) and the
  conflict list's long game names: whole (wrap / shrink / slide), rows keep their heights; touch + controller.

### HW-14 Settings text shown whole (678h, rule 11) - OPEN
- Device(s): RP Nova or KONKR (batch with HW-13 / HW-12 / HW-1 / HW-9 in the same session). Level L1 (QA app only).
- Build: commit of update 678h on claude/qa-0672-repair; QA build = assembleDebug of that commit.
- Why not emulator: the trial does not open Settings pages; long titles / theme names need a real screen width and font scale.
- Tests: at off and font=1.3,density=1.15 in the QA app: Settings search (type "theme") - each result label + section path
  whole; a group page title + a sub-page title whole; Themes: theme captions (two lines, grid rows even) + the strip line
  under the picker whole; Scene picker tab captions whole; Artwork > ScreenScraper look-up lines whole; Music > custom track
  name whole; RetroAchievements cards (game name, console line, latest unlock, stat tiles) whole. Rows keep their heights
  while D-pad focus moves; touch + controller.
- Result:

### HW-13 Save Vault text shown whole (678g, rule 11) - OPEN
- Device(s): RP Nova or KONKR (batch with HW-12 / HW-1 / HW-9 in the same session). Level L1 (QA app only, no real saves).
- Build: commit of update 678g on claude/qa-0672-repair; QA build = assembleDebug of that commit.
- Why not emulator: the trial does not open the Save Vault; a long name / place needs a real screen width and font scale.
- Tests: open Saves (QA app, Drive not connected and with the vault fixture) at off and font=1.3,density=1.15: header name +
  count line whole, "Not connected" + problem text whole, save rows one line each (a long one slides), info bar name /
  state / date whole, Browse saves label whole; rows keep their heights (no jump while moving D-pad focus); touch + controller.
- Result:

### HW-12 Banner game window text shown whole (678e, rule 11) - OPEN
- Device(s): RP Nova or KONKR (any USB handheld; batch with HW-1 / HW-9 in the same session). Level L1 (QA app only).
- Build: commit of update 678e on claude/qa-0672-repair; QA build = assembleDebug of that commit.
- Why not emulator: R26 needs the popup gate on hardware; the emulator trial runs the layouts' OWN style only, not Banner.
- Tests: popclip.sh <tag> "SHELF COVERDETAILS" "BANNER" "<game with a long name + story>" at off and font=1.3,density=1.15,
  then popclip.py (all six grades PASS); look at the Story page (More pill on a cut story), Options rows, achievement rows
  (opened = whole description) and the saves chip. Popup style restored by the snapshot.
- Result:

### HW-10 v0.6.74 "Open on screen" (branch claude/v0674-screen-shortcut) - IN PROGRESS
- Thor 16 Nov (L1): drawer START menu row + picker (Top / Bottom names), Bottom saved and used by the launch path (APP SCREEN
  display=4 = Screen-2), restored. Still to do: an Android game (game path: rule "Set for this game", "Screen layouts..."),
  long-press on the touchscreen, single-screen regression on the Nova, then the second-screen dock menu once built.
- How to open the drawer in the QA app without the bottom swipe (a Home-gesture risk where the owner's Voltcap is home): Tile
  home preview (voltcap_test_layout TILES), the second icon of its bar.

### HW-11 Cube facet status bar (branch claude/cube-statusbar-facet) - waiting for the owner's approval
- Thor top screen x1.0 + x1.3 shown 16 Nov (shots\cube_facet_thor.jpg). Small screen (Nova 640x480 dp) at x1.0 + x1.3 still to do.


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

### HW-9 Badge lane on small screens (676a) - IN PROGRESS (Nova PASS on 676f x1.0 + x1.3; KONKR / RP Classic open)
- KONKR, RP Classic, Nova at x1.0 + x1.3: pop529 "CUBE GRID TILES CLASSIC SHELF STACKS" then `badgecheck.py <run>/qa_clip` (R27).
- Nova x1.3 676: collisions 25/25 PASS; Grid chip text was clipped inside its pill (pre-existing) - fixed in 676c, text check needs
  the 676c build's dumps (needW / boxW).

### HW-7 RP Nova x1.3 re-test of the 4 Nova FAILs - DONE (n675r13 + n676x13: all four PASS; x1.0 n675r10 4/4 PASS)
- Device: RP Nova d3641c41 (via the owner's laptop) or any 4:3 / 640x480 dp handheld. After the fixes in HANDOFF "FIRST".
- Tests: pop529.sh run <tag> font=1.3,density=1.15 "CRATE POCKET_RIBBON STORAGE CUBE" OWN, then popclip.py.

### HW-1 Full popup gates on 674n (x1.0 + x1.3, both handhelds) - IN PROGRESS
- Device(s): Flip 2, KONKR.
- Build: c7ad53a7 (674n code = cf629e2e) - QA build installed on both 15 Nov 21:24.
- Why not emulator: the 53-layout gate's PASS needs real GPU / panel / controller; emulator evidence does not count.
- Tests: Flip 2 x1.0 `f674o`, KONKR x1.3 `k674n` (running 15 Nov from 21:28); then Flip 2 x1.3 and KONKR x1.0 on the same build
  to confirm the 674n fixes (Side menu Save sync, Bar arrows) and 674m glyphs (Arcade, Pocket ribbon).
- Result so far: Flip 2 x1.3 f674k (674k) 52/53 - Bar ◀; Flip 2 x1.0 f674o (674n) 52/53 - Side status line; KONKR x1.0 k674m (674k)
  51/53 - Arcade, Side; KONKR x1.3 k674n (674n) 51/53 - Library R2, Spot START. All five fixed in code since (674m-675e): a full
  re-run of all four on the newest build closes this request.

### HW-2 Record crate x1.3 vertical menu - verification checklist - OPEN
- Device(s): Flip 2 + KONKR (x1.0 and x1.3).
- Build: latest dev commit.
- Why not emulator: owner's checklist is about real D-pad / focus timing on the handhelds.
- Tests: every action incl. MORE reachable by D-pad; MORE once, last, no dots; More open / close restores focus; rapid A/B
  reliable; nothing clipped at x1.0 / x1.3; menu never changes size while navigating. (Visual design APPROVED by the owner 15 Nov.)

### HW-3 Flip 2 charging sparkle - reproduce first - BLOCKED - DEVICE UNAVAILABLE
- Device(s): Flip 2. Why not emulator: OEM charging switch (`input_suspend`) behaviour.
- The 674i patch is NOT on the dev line, never installed, never tested (branch claude/charge-loop-674i, docs/claude/patches/).
- Evidence conflict (owner 16 Nov): the owner saw the battery drop to ~78 %, charge back to 80 % and the sparkle only at
  80 % - one cycle, not a loop. The 15 Nov 20:20 log showed production's limit toggling `input_suspend` every 1-2 s at 79-80 %.
- Next, when the Flip 2 is back: read-only reproduction first (production Voltcap as it is, plugged in near 80 %, 15 min:
  logcat + battery state + whether the sparkle shows more than once). Only if it loops: apply the patch to a QA build and one
  supervised test with production's limit off (owner's step). If it does not loop: close the patch as not needed.

### HW-4 Cube first A on KONKR x1.3; L2 / R2 fact 0 px - OPEN
- Device(s): KONKR. Build: latest. Tests: popclip gate CUBE at x1.3 + onepress.

### HW-5 Rapid A/B leaves a window open once (Cards, Crate, TV) - OPEN (watch)
- Device(s): KONKR + Flip 2. 674k gates (f674k, k674m) had no rapid FAIL; keep watching in HW-1 runs before closing.

### HW-6 Spot START glyph re-measure with the key box (675e, D-674o-1) - OPEN
- Device(s): KONKR (x1.3, where k674n read dy +3 px) + Flip 2 x1.0. Build: 675e or later (QA app).
- Why not emulator: the reading came from the KONKR's real panel and backdrop at x1.3; emulator evidence never closes it.
- Tests: popclip / navclip on SPOTLIGHT home with QA traces; glyphcheck on the run folder - START / SELECT / A / X / Y must read
  source "InkGlyph+key" and PASS (limits unchanged, max(1.5 px, 5 %)); look at the crops of anything over 4 %.

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
