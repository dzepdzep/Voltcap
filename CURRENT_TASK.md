**DONE (update 405, NOT released):** Cardboard box side faces cut to a thin attached rim (D97), Cover details flight is one continuous lift-out-of-slot curve with the slot as the same object (D98); Thor frames in qa_reference_update405. **Next:** same one-curve flight + side audit for Game shelf / Case stacks, rapid reversal / Lite / touch on hardware, then the 404 list; see SUMMARY 405.
**DONE (update 404, v0.6.58):** Cover details: the new big case flies out of its own shelf slot and the old one back into its slot (D95, `CoverFlight`, Thor frames incl. rapid reversals on the final APK); SUMMARY reconciled (status header, 403 = 81c2da9, 398 / 399 order); the ten category looks of 350-352 are IMPLEMENTED in code, System Grid / Side Banner / Retro Timeline / Compact List are ABSENT (`qa_reference_update403/Update404_StatusMatrix.md`); Box Rows 1 / Auto / 3 checked on the Thor. **Next:** D91 PS2 Yu-Gi-Oh (needs a real scan or the user's override), N64 raw-scan check + NES..Switch fit audit + Switch cartridge, Cover details reference parity (back / perspective), Box 1-3 games / empty, Lite / touch for the Cover details flight, idle wait before the pickup flight, D96 title check, then the backlog table in `qa_reference_update403/Update403_Backlog.md`; see SUMMARY 404.
**DONE (update 403, NOT released):** Cardboard box slabs removed (row-wide sheen / contact bands and near-black sides replaced by per-case, material-toned ones: D92), window veil follows the flight (D93), Cover details spine + top visible without clipping (D94), tests `Physical403Test` / `CoverDetails403Test`. **Next:** one continuous row-to-featured curve in Shelf / Stacks / Cover details (D95), Box 1 / 3 rows + 1-3 games + empty on the Thor, N64 raw-scan check + NES..Switch fit audit, PS2 Yu-Gi-Oh override path (D91), idle wait before the flight; backlog table in `qa_reference_update403/Update403_Backlog.md`; see SUMMARY 403.
**DONE (update 402, NOT released):** Cardboard box pickup is one continuous flight (no 900 ms in-box rise, no vanish frame, card returns only as the piece lands; Thor-framed), dark slot / white outline / flat row tint removed from the Box (D88, D89), portrait covers on landscape faces (N64) shown whole instead of a 47 % slice (D90, Thor N64 Cover details only), PS2 Yu-Gi-Oh doubled band traced to the source picture (343x480, no template drawn: D91 suspect, NOT fixed), tests `Case402Test`. **Next:** true 3D Cover details (reference image; not done, a yaw/pitch increase was reverted), frame-check Cover details / Box closing / 1-2-3 rows, D90 on NES SNES DS GBA Switch mixed, D86 with the original sequence; see SUMMARY 402.
**DONE (update 401, NOT released):** Cardboard box shading + selection cleanup, Pocket ribbon top screen = one centred cartridge (no blur / panel) with a directional slide and no Endless, D86 fix (`MediaInspect.hold`, fixed-unverified), tests Aspect401 / Ribbon401 / Hold401. **Next:** stretched artwork root cause (PS2 Yu-Gi-Oh; template frame vs `case3dAspect`, repro on the Thor first), D86 with the original `D A B A B A B` sequence, Cover details true 3D (reference), N64 / Switch mapping, visual regression tests for Box shadow / pickup frames, GBC / 3DS / N64 Box pickup check; see SUMMARY 401.
**DONE (update 400, v0.6.57):** shared pickup transitions (owner bug: the picked case vanished and the popup appeared at once; cause: flight tied to Info animation = Just appear): fixed + Thor-framed in Cardboard box / Case stacks / Game shelf, Cardboard box Rows Auto / 1 / 2 / 3 (BoxRows.kt, Thor-checked DS / 3DS / GBA / NES / All), Cover details perspective follows Spine depth. **Next:** D86 (rapid re-open shows two cases), Artwork library Endless / rail / touch, 3D geometry vs the reference (spine / back mapping N64 / Switch, per-console sizes), 3-row edge cases on hardware (1-3 games, empty, portrait, second screen); see SUMMARY 400.
# CURRENT_TASK - 29 Oct 2026
**DONE (update 404, v0.6.58):** Cover details: the new big case flies out of its own shelf slot and the old one back into its slot (D95, `CoverFlight`, Thor frames incl. rapid reversals on the final APK); SUMMARY reconciled (status header, 403 = 81c2da9, 398 / 399 order); the ten category looks of 350-352 are IMPLEMENTED in code, System Grid / Side Banner / Retro Timeline / Compact List are ABSENT (`qa_reference_update403/Update404_StatusMatrix.md`); Box Rows 1 / Auto / 3 checked on the Thor. **Next:** D91 PS2 Yu-Gi-Oh (needs a real scan or the user's override), N64 raw-scan check + NES..Switch fit audit + Switch cartridge, Cover details reference parity (back / perspective), Box 1-3 games / empty, Lite / touch for the Cover details flight, idle wait before the pickup flight, D96 title check, then the backlog table in `qa_reference_update403/Update403_Backlog.md`; see SUMMARY 404.
**DONE (update 399):** release v0.6.56 published from updates 393-398 (build + unit tests green, Thor smoke PASS). **NEXT (all still open, see SUMMARY 399 NOT DONE list):** real 3D box geometry vs the owner's reference image (provide `reference-cover-details.png`), Artwork library Endless / 1-3 games / touch / rail on hardware, Pocket Quest FPS + Disc Station labels frame check, TWIN_* / HERO_TILES, N64 / Switch media mapping audit, the remaining ~30 layouts catswitch.
**DONE (update 398):** no artwork spawning (shared `HeldImage` + `warmHeld`, `quietSwap`), Cover details spine top faces + bigger case / shelf (Thor-checked); **Next:** verify Pocket Quest / Disc Station frames, Artwork library + Box hardware list, size audit of other physical layouts; see SUMMARY 398.
**DONE (update 397):** `setFocus` ends the category window on any real game move (touch, jump, pick); File Select flagged frame = benign swap frame; catswitch PASS POCKET_RIBBON GREENCORE TWIN_DESK POCKET_CLASSIC DISCS (PRE=A) POCKET (open D-pocket: 1-frame old/new info overlap, pre-existing). **Next:** Artwork library Endless ON / 1-3 games / rail closing, Box Neat / Messy, dual-screen TWIN_ICONS TWIN_SHOWCASE HERO_TILES, the remaining ~30 layouts; see SUMMARY 397.
**DONE (update 396):** the category window ends at the first game move (`CategorySlide.endChange`), Artwork library rail labels no longer wrap / jump while opening (fixed-width label + fade), CRLF fixed (`pushqa.sh`, self-test), category-switch PASS HERO DASH ARCADE CRATE CARDS STORAGE FILESELECT (STORAGE / FILESELECT need `catswitch.sh RUN LIST A`). **Next:** `catswitch.sh` on the remaining ~36 layouts (list in coverage CS-03), immediate game navigation after L2 / R2 on hardware, Artwork library Endless ON / small categories, Box Neat / Messy on hardware, rail closing + L2 / R2 with the rail open.
**DONE (update 395):** Artwork library list starts at the top with Endless off (`artLibRowTop`, no empty space at either end), better text contrast; Cardboard box case side faces (`BoxCaseSides`); category-switch run C3 Thor PASS: ARTLIB, ARCTIC, SHELF, SIDE, STACKS, BOX. **Next:** run `catswitch.sh` on HERO DASH ARCADE CRATE CARDS STORAGE FILESELECT POCKET DISCS TWIN_*; check the 600 ms justChanged guard + rapid reversal sync; Artwork library Endless ON / few games on hardware.
**DONE (update 394):** a category change never reloads a layout (CLAUDE.md rule 14): shell slides / category-keyed fades removed, every game-info swap immediate on a category change (`CategorySlide.justChanged`), Thor prefs set to Just appear / Just change / Just swaps / Games slide in OFF, `catswitch.sh` + CATSHELL + `CategoryStable394Test`. Thor PASS: Cinema, Cover details, Cardboard box, Voltcap row, Tile home; **48 layouts NOT TESTED on hardware (run `catswitch.sh` in batches of 5-8, 3-4 min each)**, empty / mixed / missing-art / per-category layouts / dual screen NOT TESTED.
**DONE (update 393):** spine / case thickness from one source (`ui/CaseThickness.kt`: shelf, Cardboard box, Cover details, as Case3D), entrance animations default to Just appear / Just change (saved prefs kept; the Thor still has FADE / SLIDE saved). Open: TV room / Case stacks thickness, depth Off / Realistic side by side.
**DONE (update 392):** top space cut in Cinema, Arctic hub and Cover details (per layout via `LocalStyleTopInset`, not global), Artwork library 391 change verified; Thor before / after in SUMMARY 392.

**DONE (update 381): Endless navigation.** Dashboard's unreachable last-game card = ◀ on the first game entered the menu instead of wrapping (fixed, Thor-verified, `EndlessNav381Test`).
**Update 382 (29 Oct):** one-press QA rule R23 + tools; Dashboard 'second press' report not reproduced on the Thor (D77, ask the owner for category / game count / options). Uncommitted second-screen work still preserved.
**Update 383 (29 Oct):** D77 fixed: `FollowFocus` (`ui/EndlessRow.kt`) left an in-view card where it was, so the row showed two games left of the selection and moved a press late; now one fixed place (`followTarget`), `FollowFocus383Test`, Thor-checked on the Dashboard. CLAUDE.md rule 13 (start-of-task reading) added.
**DONE (update 385): D79** Now Playing title / artist wrap-shrink-slide, no "…", Thor-verified.
**DONE (update 386):** third-party widgets in the Hub verified on the Thor (home, in-game, after returning home); Hub controller routing found (keys on display 0) and two controller defects fixed (D82 A / B dead, D81 Widget Board unreachable); D80 reproduced and fixed (Wall "0 Games").
**Update 384 (29 Oct):** second-screen in-game fixes (379) verified on the Thor for Now Playing / Music and committed.
**DONE (update 387): D84** profile-selector screensaver timer fixed, Thor-verified.
**DONE (update 388): D77 again** highlight stayed in slot 3 while the row glided; fixed in FollowFocus (highlight follows the slot), Dashboard + Side menu Thor-verified frame by frame.
**DONE (update 389): D77 third time** the highlight is a crossfade by the row position (388 handed it over half-way and the frame swung a slot, found by a per-frame geometry trace); Dashboard + Side menu settled slot verified Endless on / off; other layouts see SUMMARY 389 (untested list).
**DONE (update 391):** spine depth Off / Subtle / Realistic 3D (`Features.spineDepth`, `ui/SpineDepth.kt`, default Subtle), custom spine names never slide (`SpineName` measured shrink + wrap, `SpineTitleFit` for bitmap spines, no "…"), Artwork library top space cut, "Game you're on" labels unified; Thor-checked (SUMMARY 391). Open: Realistic on Case stacks / Cardboard box / TV room, Artwork library selection audit, Middle / Right on other row layouts.
**DONE (update 390):** Dashboard + Side menu row: "Game you're on" Left / Middle / Right (default As designed = second card), Thor-verified; row audit now has a selection check for every layout (13 layouts PASS, Channel grid / Live tiles by design). **Open: Thor sessions S2-S4 (37 layouts untested, list in SUMMARY 390 / COVERAGE RA-03), Hero carousel needs a trace.**
**NEXT PRIORITY: (1) Side menu FollowFocus on hardware, Endless on / off; (2) D83 more "…"; then priority 3 (quick page switcher and controller navigation).**
**Afterwards:** second-screen work continues (in-game widget reliability first), then the rest of the priority list in `COWORK_HANDOFF.md` ("UNFINISHED PRIORITIES"). Everything below is history.

# (history) status 28 Oct 2026, update 369 (device-free, 0.6.54 release still pending)

**Update 378 (28 Oct 2026, Thor ~12.5 min):** D76 not reproduced in 4 recordings (before and after), hardened in `GameCard.kt` (wide art shows whole while the cover under the Roll-out isn't drawn; `RollReveal76Test`). Only-first-wide PASS on the Thor (set through Settings › Home › Layout options, restored to Wide capsule): normal, rapid, reversals, last-to-first and first-to-last wrap, art / title / focus aligned. R1 picker: choosing a layout (A) now moves the focus to Apply, D-pad Apply PASS on the Thor (2 A presses). v0.6.55 release: see SUMMARY.md top.

**Update 377 (28 Oct 2026, Thor ~10.5 min):** final QA, NO RELEASE (v0.6.54 latest, v0.6.55 unused), no code changed. R1 hold picker (preview / choose / cancel / apply / tap-cycle) PASS, per-category assignment + Follow default survive an app restart PASS, Wide Capsules Slides in clean except one 3-frame black bar in the capsule (defect D76, suspect, unfixed); Only-first-wide NOT VERIFIED. Thor left clean. Details: SUMMARY.md top.

**Update 376 (28 Oct 2026, Thor ~15 min):** 374 / 375 checked on the real Thor (Coverflow details, Artwork library, status bars, per-category manager Apply / Cancel / Follow default PASS). NOT checked: Wide Capsules, R1 hold, restart persistence, light / warm themes, dual screen -> **no release; v0.6.54 stays latest** (next tag v0.6.55). Three small fixes (ArtLib mixed row height, one-line slim-spine name, own preset mocks) unverified on hardware. Cinema black band = shared status-bar space: SCREENSHOT NEEDED. Details: SUMMARY.md top.

**Update 373 (28 Oct 2026, Thor ~4 min):** Thor crash fixed at the source: `TuneStore.edits` is a StateFlow (no snapshot state), `Tuner.supportedState` + `probeSupported()` keep the blocking root probe off composition (mechanism proven by unit test; exact app path not reproduced). L1 icons: extracted symbol / initials fallback, never full colour; `shortcut_themed_icons` default on and switched on on the Thor (12 L1 icons PASS; ARMSX2 not installed). Wide capsule: the fixed slot now only for Fades in / Just appears, default Slides in = continuous row; portrait-only art shown whole. Wide motion NOT TESTED on hardware. 336 unit tests, debug build, strict static checks OK. See SUMMARY top. 0.6.54 still unpublished.

**Update 370 (28 Oct 2026, Thor session, see SUMMARY.md top):** 369 verified on the Thor for Tile home (PASS: centred at x=960, even, no drift) and one-down-stays on the category screen; NOT TESTED on hardware: second-down hand-over, custom spines, the 13 carousel modes. New: `ui/QaGeom.kt` geometry trace (`--ez voltcap_test_geom true`, `geomcheck.py LOG --logcat`), PerfPage sub line two lines (second-screen 'No game r…' was cut). 0.6.54 still unpublished; no release prepared until B / C / A are verified on the Thor (physical QA budget ~15 min per run). Thor left clean: hooks paused, snapshot restored, no recorder.

**Read COWORK_HANDOFF.md first (section 2d).** Update 369 (SUMMARY.md): category-screen second-screen gate, own-spine flicker, Tile home drift, Case Stacks audit, geomcheck.py. No device touched; hardware verification NOT TESTED and needs the user's separate permission.

# CURRENT_TASK — status 28 Oct 2026, update 368 (0.6.54, release pending)

**Read COWORK_HANDOFF.md first.** Update 368 (SUMMARY.md): an empty collection was a stop / tab but no tile -> fixed (`CategoryStopsTest`, 12 tests); combination matrix (`tools/qa/combos.json`, `COMBOS.md`, `device/combos.sh`, `combocheck.py`); 12 Thor runs PASS (Voltcap row, Tile home, Cinema, Arctic); version 0.6.54. Next: see "Recommended for Sunday 1 Nov" in COWORK_HANDOFF.md.

# CURRENT_TASK — status 28 Oct 2026, update 367 (0.6.53)

**Read COWORK_HANDOFF.md first.** Update 367 (SUMMARY.md): one category list (`data/CategoryStops.kt`) for L2 / R2, tabs, category screen and warm-up; 4 confirmed inconsistencies fixed, `CategoryStopsTest` (9 tests); exact user symptom not reproduced on the Thor; released 0.6.53 (tag on f7d9a13). Next: see "Recommended for Sunday 1 Nov" in COWORK_HANDOFF.md.

# CURRENT_TASK — status 28 Oct 2026, update 366 (0.6.52)

**Read COWORK_HANDOFF.md first.** Update 366 (SUMMARY.md): picker.sh / pop.sh false pass fixed, 46 self-tests, first real Thor sweep: 51/51 layouts
recorded and validated, stress on Cinema / Arctic PASS, version 0.6.52. Next for Cowork (from 1 Nov): the 9 Settings categories, R1 quick-menu
options, second screen / portrait / other aspect ratios, missing-artwork fixtures, Cinema / Arctic controller walk, per-frame title check.

# CURRENT_TASK — status 28 Oct 2026 (after 0.6.51 + the QA overhaul)

**Read COWORK_HANDOFF.md first** (the current state, what is done, what is open). The text below is the older history and may be out of date.

- 0.6.51 is released. After it: the QA overhaul (tools only, update 365, SUMMARY.md): QA tools can no longer pass on missing evidence,
  frames.py repaired, self-tests + GitHub Actions (qa.yml), coverage matrix `tools/qa/COVERAGE.md`.
- Next (priority): (1) run a real sweep on the Thor with the new manifest (`layouts.sh` then `runcheck.py`); (2) record the 5 never-recorded
  layouts; (3) stress + controller walk on Cinema / Arctic; (4) second screen and portrait; (5) decide on the open perf findings (D64, STACKS / ARCADE / SHELF janky %).

# CURRENT_TASK — updated 6 Oct 2026 (after update 57)

## Done
- Updates 20, 21 (themes) and 22 (the user's list of 5 Oct evening + pixel UI reach, Volt): see SUMMARY.md.
- Update 22 answers from the user: "Sync recently played" = games (same as last-played sync); Save Vault "spawn
  infinitely" = endless scrolling grid; Thor 2nd-screen swipe = flip Voltcap pages.

- Update 23: in-game overlay panel (tile long-press / notification tap), free overlay dragging, visible blurred cover
  on the music overlay, home header / corner visualizer clean-up.

- Update 24: Volt design system (VoltUi.kt: glass panels, accent focus glow, voltage line, pill switches, edge
  cards, underline tabs), applied to all shared components, settings, R1 sheet, notifications; second screen header /
  achievements board / "Cartridge + details" art / Games page; library view grid / list (GameBrowser.kt); Volt
  companion (perch on focused card, roam the background); picture tiles for visual choices + group icons.
  Constraint from the user: don't break the game rows (classic / tiles / hero) — new things layer on top.

- Update 25: Volt pieces in the theme palette (accent → secondary), Grid home layout (GridHome.kt), drawn
  cartridges / discs with label (PhysicalMedia.kt, ScreenScraper support-texture), soft glow / electric redone.

- Update 26: VoltTour.kt (spotlight tour of the real home screen after setup / Mascot › Show me around; targets
  via Modifier.tourTarget(id)), cleaned 10 mascot PNGs (project_copy res/drawable-nodpi).

- Update 27: UiStyle Glass / Energy capsule / 32-bit console / Classic (VoltUi capsuleSurface, pixelSurface), pixel
  artwork in dp + dither, category slide / cover light / charge gauge (HomeExtras.kt).

- Update 28: Pixel Volt set (res volt_*.png, generator tools/mascot), Features.mascotArt; soft blur behind the
  games (Features.rowBlur, SoftRowBlur in HomeExtras.kt).

- Update 29: original Volt default again (code-drawn sets not good enough; user prefers their art), UiStyle +
  Memory card / CRT / Neon sign / Cartridge, HomeLayout.TV (TvRoomHome.kt: TV with clip/screenshots/music OSD,
  console sticker, cases with spines / covers / shelf of spines, info bar).

- Update 30: per-console MediaShell (N64, Switch, Vita, HuCard, GBC, Atari, Game Gear, disc variants PS1/PS2/
  Saturn/GD/Wii, GC mini at real size + Features.bigGcDiscs).

- Update 31: physical media as rendered templates (res media_*.png from tools/media, label window behind).

- Update 32: new Volt sheet → mascot_*.png (27 poses incl. stretch/loaf/prowl/idea/gaming + 9 faces), blink frames
  (MascotPose.blinkRes), per-pose animations in MascotSprite (pop on pose change, music sway, hops, prowl...);
  "Waterfall cliffs" animated theme (ThemeMotif.PIXEL_FALLS, FramesScene.kt, scene_falls.png 8×560×272 strip);
  UMD redone as the real pick-shaped shell with a round window (label clipped by CircleShape).
- Update 33: gesture pill — GestureHint.immersiveState ("Hide navigation bar", default on) + SystemBars.kt
  (window, every Dialog via ImmersiveDialogWindow(), re-hide after IME); "Force-hide gesture hint" switch
  (Shizuku / WRITE_SECURE_SETTINGS, keys navigation_bar_gesture_hint / hide_gesture_line, restore on onStop).

- Update 34: bug check — unique lazy keys (gameKeys), BackHandler for library / tour / details / category screen,
  library Close button, blink stuck fix, showcase shadow, second-screen focus outline. FEATURES.md written.
- Update 35: second bug check — launcher-data merge no longer wipes on unreadable/missing Drive file, last-played
  skip on unreadable file, scan keeps selection, web remote lockout, nearby transfer PIN limit, overlay tile restarts
  the overlay service mid-game (GameMode.active), video Surface release.
- Update 36: full bug check of all 180 files, nothing skipped (user: "dont skip anything in the future"). 88 files
  fixed: Drive download swap, SAF partial file cleanup, duplicate lazy keys (category row, 2nd-screen apps),
  clock-change crash, safe JSON in RA social / Steam, borrowed emulator package names, manuals for apps,
  CUE/GDI tracks, stale touch callbacks (rememberUpdatedState), per-frame wakeups / recompositions, main-thread I/O.
  Google Drive for everyone = publish the OAuth consent screen (drive.file) + release-key Android client.
- Update 37: Shizuku removed (user doesn't want to run it): GestureHint uses RootShell (PServer) + self-grant of
  WRITE_SECURE_SETTINGS, else the adb command. Underclock (TuneProfile.underclock / underclockKhz / underclockGpuMhz,
  Tuner.underclockKhz/GpuLevel/cycleUnderclock) with R1 tile (DeviceControl.UNDERCLOCK), QS tile
  (UnderclockTileService), in-game panel chip. Tiles mid-game now apply to games on the device-wide profile.
- Update 38: per-game "For this game" quick card (underclock / fan / buttons; first pick creates the own profile),
  GameExtras restore persisted (prefs game_extras, INTERNAL) so buttons go back after Voltcap was killed mid-game.
- Update 39: console profiles ("plat:<platform>" in TuneStore per_game; Tuner.resolve own → pkg → plat → global;
  onGameLaunch gets platform + console name), heat guard (Tuner.heatTick, heatCeiling, TuneStore.heatGuard/heatGuardC),
  copy to all console games (TuneStore.setForKeys), measurements (TuneStore.stats/addStat, ProfileStat, Tuner
  measureStart/Sample/End, TuneService.batteryPower). Toggles: consoleProfiles, heatGuard, measure.
  Next ideas offered to the user (not started): see the chat for update 39.
- Update 40: SystemSync (ContentObservers on shade settings, display listener, Wi-Fi/BT/DND/volume broadcasts,
  prefs listeners on tuner/perf_settings/charge_limit/mixer → version StateFlow; screens re-read). Tuner adopts
  outside fan_mode (onSystemFanMode, lastFanWritten); GameExtras.onSystemChange drops restore of Hz / buttons
  changed in the shade. Volume mixer: MixerHelper (app_process via PServer: IAudioService
  getActivePlaybackConfigurations → getClientUid / getPlayerProxy().setVolume; modes list / daemon),
  AppMixer (prefs "mixer", pkill/pgrep 'voltcap_mix[d]'), MixerActivity card, MixerTileService, R1 tile.
- Update 41: AppearanceSettings.secondPageTitleOnly → SecondScreenOptions.pageTitleOnly; PageSwitcher (SecondScreenHome)
  with quiet chevrons, VoltSecondHeader(titleOnly, onStep); switch in Screens › While you're in Voltcap.

- Update 42 (user's 26-point list, part 1): CategorySlide.tick (slide only on L2/R2), GameDock loop tracker keeps its
  place on list refresh, SecondScreenShown + FLAG_NOT_FOCUSABLE Presentation (keys forwarded), details as the second
  screen's "Game" page (gameWindow / onCloseGameWindow, AppearanceSettings.secondDetails), achievements inline detail
  (narrow), MusicPlayer(artBackdrop), FitText uses LocalTextStyle, MascotSprite(sameScale) + BODY_POSES, Volt off
  (Features.migrate42), mixer one-line list + dumpsys fallback, SystemLights (AYN RGB keys held off), all multi-intensity
  LEDs, theme-colour lights at 45 %, music fx on the second screen (musicFxSecond, fxOverlay), visualizer cover toggle
  (secondVisualizerArt, COVER_MOTIFS), second-screen saver BLACK (key second_screen_v42), SaverStyle.VOLT_SLEEP,
  removed: GameBrowser / LIBRARY_VIEW / library prefs / Game clients card / SecondPage.LIBRARY (enum kept for old
  values) / NIGHT_DRIVE; Retro TV = MusicCrtRoom (CRT room shader with the cover).

- Update 43: template pack → res tpl_*.webp + ui/MediaTemplates.kt (generated, tools/media_templates), TemplateMedia
  (mask DstIn in the window) + Box3D (setPolyToPoly front/spine, glass overlay, rotationY sway) in ui/RealMedia.kt;
  PhysicalMedia(platform, labelIsScan) uses them; typedArtFor(platform) keeps only the 2D box when box3d; Features.box3d;
  CaseDetailsPage (ui/SecondScreenDetails.kt, flip open, secondDetailsCase); Features.uiFont (NORMAL / PIXEL),
  headingFont(), uiFontStyle() (font-only merge), Density fontScale 0.84 for pixel; m43 migration of pixelUi; PixelStyleCard
  in Interface style; cardArtByLayout / hiddenMediaByLayout + cardArtFor() + LocalHiddenMedia/artForGame; PinnedShortcuts
  + PinShortcutActivity (CONFIRM_PIN_SHORTCUT, LauncherApps.startShortcut). User rule: every update lists files to delete with full paths.

- Update 44: shaders get playing clocks uPlayT / uBassT / uMidT / uHighT (_reactive.agsl, NowPlayingScene MusicShader integrates
  them; replaced every t * (a + b * level) spin), HOLO_COVER perspective card + uLabel name strip (songLabel(clean = true),
  640 x 170, bilinear), RECORD_SHELF rebuilt (wallSleeve / crate helpers), cube silhouette AA; ThemeMotif.lyrics +
  ThemeGroup.LYRICS, LYRIC_KARAOKE / LYRIC_SHEET (lyric_cloud scene) / LYRIC_NEON in ui/LyricThemes.kt (LyricStageLayer,
  largestFreeBand, Features.lyricNextLine / karaokeBall); TvRoomHome PadGlyph confetti per console + cabinet TV (Canvas,
  rotationY -5, channel param). gen.py: NIGHT_DRIVE out, ALSO map (PIXEL_FALLS); check_uniforms.py handles shared cases;
  tools/themes/shoot_frame.py renders a still. FitText.kt (uiFontStyle) had been left out of update 43's copy: delivered in 44.

- Update 45: mixer rework — MixerHelper one marked line (VOLTCAP_MIX), launch VARIANTS remembered, players carry sessions;
  MixerEffects.kt (DynamicsProcessing per session, AudioReport: DUMP self-grant via root / adb, dumpAsync of "audio",
  root dumpsys joined with tr); AppMixer Method AUTO / HELPER / EFFECT, Status, per-game volume (onGameLaunch /
  onGameEnded from the VM), pinned apps, amplitude = p²; MixerActivity redesign + Mixer check report; Hotkey VOLUME_MIXER.
  QsTiles.kt + QsTilesCard.kt (Settings › Device fold "dev_tiles", FirstRun finishing touches; requestAddTileService
  API 33+, root sysui_qs_tiles add-all, tiles mark onTileAdded/Removed; res ic_tile_*). Visual sweep of every ui/ file:
  ChipFlow rows, ShelfDialog FlowRow footer, PercentSlider valueText, LegendThree in ControlBar, statusBarDp insets,
  SecondScreenHome headerEndInset (CompanionHub timer pill), 76 Ellipsis adds (/tmp ellip.py). SettingsScopes: mixer_games /
  mixer_meta DEVICE, qs_tiles INTERNAL. Not compiled (PC run stopped after generateDebugRFile; jar type-check not run). verify-src-copy.bat in the project root
  sets JAVA_HOME to Android Studio's jbr and runs the verify-src-copy.gradle build.
  User: work on the app files, not by driving the PC (no computer use).

- Update 46: VoltageLine shared clock (VOLT_SPARK_CYCLE); TvRoomBackdrop (room, stand, lamp, screen glow) + CRT flicker /
  roll / phosphor; CaseGrid centred row (padV contentPadding) + free media; isFreeMedia / mediaFloorGlow (PhysicalMedia.kt),
  FrontBox + rememberBoxFace (RealMedia.kt) in GameCard / GridHome / TileHome / TV, GameDock bannerFree slot width;
  MediaVariants (GBC Black / Grey / Clear, Features.gbcCart, res tpl_cart(l)_gbc_black); FocusedLine vertical hug +
  GameDock categoryBelow; SecondScreenOptions.artType limited (HERO / SCREENSHOTS), SecondHomeGame.shots; DockBacking
  header switch + rowBlur / nameBlur sliders (nameTextStyle scales shadow); CloudSync.perGameSaveFilesAll + SaveCapsules
  disk cache; FloatingSaveField, MemoryCardSky own clock, CapsuleCrack before open, MemCardShape; music never paused while
  playing (NowPlayingScene / CodeScene musicOn / Lyric* / CornerVisualizers), scene scale ≥ 0.9, secondScreenQuality = main;
  ScreensaverLyrics fit ladder; Underclock.AUTO + effectiveUnderclock (migration flag uc_with_power); LidPause
  (Features.pauseOnClose); hero retry + HeroLayers refresh on resume. Not compiled.
- Update 47: settings search (SettingsSearchResults + generated SettingsIndex.kt, tools/settings_index/gen.py; jumps via
  subpageMemory / openFold / category); second-screen FullMusicScreen inside SecondPage.MUSIC (no takeover); TV music OSD
  off, Features.tvRoomNames; FrontBox draws the template overlay warped to the front quad (setPolyToPoly), case media no
  longer gated by Features.box3d; FocusedLine wrap under the row; AudioReport session regex ("session ID:") + diagnose
  checks; softBandBrush / SoftBandBlur (HomeExtras.kt) for Game-UI blur + dim + SoftRowBlur (hero fades right, grid full,
  tiles from artTop). Not compiled.
- Update 48: 47 build fix (TvRoomHome OSD block removed); ANR on Thor = hwui text-blob cache under perspective
  (GrTextBlobRedrawCoordinator) → flatTextFor3d() (Offscreen compositing when rotationX/Y != 0) on every 3D layer;
  real compile check in the cloud (tools/compile_check, jars from the user's Gradle cache, granted read access). 0 errors.

## Open / next
- Update 45 needs a build + device test (mixer effect method, tile prompts, Thor lower screen).
- 26-point list done. Maybe: 3D boxes in the TV room's cases.
- See FEATURES.md › "Not done yet" for the full list.
- Possible next: more pictograms (focus effect, other-games look, positions); Volt reacting to game launch / unlock
  toasts.
- Update 56: all brief themes done (+ 18 pixel types, Trophy vault, Badge constellations); Gaming room = the user's
  painting (res/drawable-nodpi/gaming_room.webp, a showcase: uRoom + uShot, monitor at uFocusY = middle of the open
  space from LocalSceneAvoid); ⊘/◐ own-colour marks (color_check.py). Compiled, 0 errors. Needs a device test.
- Update 57: Trophy vault reads RA (TrophyShelf: mastered games + recent badges → uTrophies atlas, spinning frames);
  Gaming room poster = cover (uPoster), clip on the monitor (VideoBackdrop over roomMonitorRect, Showcase.video);
  Steam achievements without a key (community ?xml=1 pages). "Make the hero be…" dropped by the user.
- DroidDeck / PXPlay: needs the installed apps' exported intents (dumpsys package) to go further than update 20.
- Device checks pending (see SUMMARY update 22 "not tested").

## How it's checked
- Before shipping: tools/compile_check/check.sh in the cloud (see its README) — must be 0 errors.
Cloud session without a shell on the PC: stage src_copy (7 calls of 50 files) + jars from ~/.gradle/caches
(compose ui/foundation/animation/runtime 1.10.4, material3 1.4.0, icons 1.7.8, lifecycle 2.9.4, activity 1.10.1,
core 1.16.0, coil 2.7.0, gson 2.11, okhttp 4.12, retrofit 2.11, okio 3.9) and android-36 android.jar; Kotlin 2.2.10
from GitHub (Maven is blocked) with its compose-compiler-plugin.jar. Baseline: 544 errors (was 389 earlier) on untouched sources
(missing WorkManager, Play services, R...). Compare error lists without line numbers.
Commit back with expectedMtimeMs from the staging result.
