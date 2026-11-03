# v0.6.67 staging — NOT part of 0.6.66 (nothing here is compiled)

Kept outside `app/` so it can never be built into the frozen 0.6.66 release candidate. Updates 446–454.

## What is in it (one patch for all three: `profile_picker.patch`)
1. **"Who's playing?" looks (update 446, owner decisions in 447).** Six looks: Aurora glass, Console classic, Neon arcade,
   Minimal OLED, Follow Voltcap theme, Midnight blue (the 0.6.66 look). Settings › Home › Animations: thumbnails +
   "Preview the looks" (L1 / R1), in Settings search ("Who"). Saved in `voltcap_features` › `picker_look` (backed up).
   **Default (owner, update 447):** an existing install keeps Midnight blue; a new install starts on Aurora glass. Chosen once
   at start only when nothing is saved (`Features.firstPickerLook(FirstRun.isDone)`), then saved — a saved choice is never
   overwritten. Per-profile colour presets (reuse `hue`), whole-word names (never "…"), 76–128 dp pictures in one scrolling
   row, pick animation, bigger PIN pad, D-pad + touch, Lite mode. Switching / storage unchanged.
2. **D144 — QA picker detection by the app's own mark (update 447).** With the test-hooks file, Voltcap writes
   `<external files>/voltcap_qa_screen` = `PICKER <pid>` while a picker is up, `HOME <pid>` otherwise (and at every start).
   `tools/qa/device/picker.sh` reads it (display awake + Voltcap in front + same pid; HOME also needs a lit screen) — no
   background colours. Old builds (no mark) fall back to the Midnight-blue corner. Device check: `pickdetect.sh`.
3. **Dual-screen return button (update 447, D146).** The way back from the second screen's dashboard to a two-screen layout
   (Twin desk / icons / showcase, Pocket classic / ribbon) is a compact button at the end of the dashboard's header row
   (32 dp visible, 44 dp touch / controller target, layout-styled; "Back to <layout>" for TalkBack) instead of the wide pill
   in the bottom-left corner that covered the dock. The header keeps room for it (`SecondScreenHome(headerEnd = …)`), so it
   never overlaps tabs, dock, apps or page arrows; overlays (drawer, app picker, big player) draw over it. Same controller id
   (`twin-back`), same action (`TwinScreens.dashboard = false`), no setting changed. QA hook `--es voltcap_test_twindash on|off`;
   device check `twinback.sh`.

4. **D147 — controller onto the dashboard (update 448).** With the dashboard open, ▼ on any two-screen layout goes past its hidden
   lower part (HomeReach, then the dashboard); the return button hands the controller back to the layout (`SecondPad.leave()`).
   `StyleHomes.kt` + `TwinScreens.kt`; TwinBack447Test.

5. **Gesture pill (update 449, D149 / D150).** Retroid: the window asks Android to hide the bar whenever the device's own
   hide_nav_bar is 0 (ContentObserver, no polling) — the white pill showed there before; AYN: note in the card that the pill needs
   Force-hide; the Force-hide switch shows the live state. GestureHint, SystemBars, SecondScreen, MainActivity, GestureHintCard; NavBar449Test.

6. **D148 — Retroid fast-charge stop (update 450).** On a Retroid only (the AYN path is unchanged): a short partial wake lock while a
   battery change is handled (the RP Classic slept ~0.45 s after each change, so the stop begun at 80 % landed at 83 %), a current check
   ~12 s after a stop (still charging → written again, then the next switch added), a learned fast-charge lead (0–2 %, only from this
   device's own overshoot, only while 1 % takes < 90 s), the R1 chip shows the real level when it isn't 80, `limit_*` rows in
   charge_log.csv. 80 % / resume < 77 % unchanged. `ChargeLimiter.kt`, `ChargeLog.kt`, `DeviceControls.kt`; ChargeFastStop450Test.

7. **Apps in Continue playing (update 450, D151).** Per-app opt-in "Show in Continue playing" (app drawer › hold / Start on an app, and the
   app's options) for streaming clients such as PXPlay (`psplay.grill.com`) and Asobi (`com.asobi.droid`); empty by default, the app stays
   an app. A launch from Voltcap notes the time only (no session, play time, save sync, AutoTDP / tuning, Discord; the existing task is brought
   forward). With Usage access (optional) use outside Voltcap counts too — only time in front, from the opt-in on, per profile — read once
   when Voltcap comes back. "Clear from Continue playing" forgets an app's history. `data/ContinueApps.kt`, `LibraryAppsStore.kt`
   (`continue_apps`), `RecentlyPlayedStore.forget`, `LauncherUiState` (RECENT), `LauncherViewModel`, `AppDrawer`, `GameOptionsDialog`,
   `LauncherScreen`, `SettingsScopes`; QA hook `--es voltcap_test_appinfo pkg,pkg` (read-only); ContinueApps450Test.

8. **Update 451.** Continue playing: an opted-in app opened from its tile takes the light app path (D152), open streaming apps are never
   "games to quit" (D154), Clear survives the last-played sync (D155), Usage-access-off time is never read later, use settles to the playing
   profile before a profile switch. Charging (Retroid): wake lock only while the limit has work, one stop check at a time. Game windows (D156):
   More in 22 layouts' windows (`GameWindow.kt` `MORE_SCENES`). QA: view-state snapshot / verified restore (`viewstate.sh`), safe
   `voltcap_test_category` (`S:<list>`, never empty). Tests ChargeWake451Test, ContinueApps451*, PopupMore451Test.

9. **Update 452.** VoltPanel (Classic) window: the picture row takes only the room left, so the keys stay visible with More open on
   1240x1080 (`WinScenesHomes2.kt`, PopupMore452Test). QA hook `voltcap_test_winmore on` (GameWindow / TestHooks). Continue playing:
   `RecentlyPlayedStore.visibleOf` (pure, ContinueApps452SyncTest). QA scripts popverify.sh, picker() by app mark (d137, popscan, popfinal).

10. **Update 453.** Rapid A B A B never leaves the window open (`GameWindow.kt` GamePopup tokens, `LauncherScreen.kt` home B), category pills =
   L2 / R2 stops (`HomeChrome.kt`, `LauncherScreen.kt`), app drawer starts on its first app and shows the focused name whole (`AppDrawer.kt`),
   Classic window fits its content (`WinScenesHomes2.kt`), Switch dump tags off names (`GameTitles.kt`), trash / recovery folders skipped
   (`RomScanner.kt`). Tests PopupReopen453Test (+VoltPanelFit453Test), CategoryBar453Test (+DrawerFocus453Test), SwitchTitle453Test,
   TrashFolders453Test; PlayWhileClosing412Test / PopupPolish444Test updated.

11. **Update 454.** QA layout previews survive category switches (`LauncherViewModel.kt`, D164); R1 Now Playing title / artist and Widget
   Board name whole (`NotificationsPanel.kt`, `WidgetBoard.kt`, D83); the A that opens the Classic details window on its press never clicks
   its Play (`LauncherScreen.kt` HomeKeyGuard.swallowAUp, D167). Tests PreviewCategory454Test (+DetailsHeldA454Test), SaturnDreamcast454Test.

12. **Update 455.** Pocket Ribbon upper screen: the game's piece sized by its own media fills the space above its name and one line
   "platform · year · Played …" (`PocketRibbon.kt`). Test RibbonTop455Test.

13. **Update 456.** Background held under the app drawer / Settings incl. the home's own motion (`LauncherScreen.kt` panelHold, `PowerSaver.kt`
   calmHere / EffectClockDriver(hold) / focus pulse, `PhysicalMedia.kt` MediaClock.hold, `FitText.kt`, `FocusEffects.kt`, `VoltUi.kt`,
   `StyleCircuit.kt`, `ShowcaseScene.kt`), Pocket Quest tab names (`StylePocketQuest.kt`), QA hooks panelhold / tabnames (`TestHooks.kt`).
   Tests PanelHold456Test, PqTabs456Test.

14. **Update 457.** Settings rows no longer pop in at the bottom edge — focus scroll keeps a look-ahead (`SettingsDialog.kt` SettingsLookAhead, D172); Settings window flat — elevation 0, no unseen shadow insets (`SettingsDialog.kt` SettingsWindowFlat, D172; GPU 11.0 -> 10.2 ms on the
    RP Classic); QA hooks `voltcap_test_lookahead` / `voltcap_test_settingsflat` / `voltcap_test_surfaceprobe` (`TestHooks.kt`, `VoltUi.kt` voltSurface probe, measurement only;
    default look unchanged). Test: `Render457Test`.

15. **Update 458.** GBA / cartridge sides solid at every turn — copies never edge-on, face fades edge-on, copies cut to the side's own edge, side strip
    filled side-on (`SolidPiece.kt` edgeOnSafeYaw / edgeOnFade / SideClip, D175); Inspect's face fades edge-on, slices up to 72 (`MediaInspectView.kt`);
    drawn Game Boy pak: full sticker, moulded "Nintendo GAME BOY", grain, softer light (`CartShells.kt`, D174). Tests SideEdge458Test, GbPak458Test.

16. **Update 459.** Generated printed labels per platform on the existing shells (`ui/GeneratedLabels.kt` new, `ui/CartShells.kt` front), real
    header codes / FAN-MADE / HOMEBREW (`data/LabelIds.kt` new, indexed from `LauncherScreen.kt`), moulded GAME BOY independent of the backs setting
    (`CartShells.kt` gbTopText); 458's grain / light / sticker crop and the DS productCode removed; Settings category slide (`SettingsDialog.kt`
    SettingsPageSlide). Test Labels459Test (GbPak458Test dropped). Check the patch with `--exclude='tools/*'`.

17. **Update 460.** Generated label designs off by default (`GeneratedLabels.enabled`); Labels folder (`data/LabelFolder.kt` new, `PhysicalOverrides.apply`,
    `LauncherViewModel.refreshFolderLabels`); Inspect hook wears the label scan (`MediaInspectView.kt`); Cube hub lines + FullText reserve (`CubeHub.kt`,
    `FitText.kt`, reserve=false in Carousel / CategoryLooks / GridHome / StyleArctic / StyleCinema / StylePocketQuest / StyleRecordCrate); chip-row crash
    (`WholeChipRow.kt`); case layouts decoded ahead (`RealMedia.kt` WarmCaseNeighbours, `StyleCaseFlow.kt`, `StyleCaseStacks.kt`, `StyleRentalShop.kt`);
    QA hook `voltcap_test_genlabels`. Test Labels460Test.

18. **Update 460b.** D173: scans shared between copies of the same game file (`data/GameCopies.kt` new, `LauncherViewModel.shareScansAcrossCopies`,
    called from `LauncherScreen.kt`); QA hook `voltcap_test_mediadump` (`TestHooks.kt`, `LauncherScreen.kt`). Test GameCopies460Test.

19. **Update 461.** D173 copy matching hardened (`data/GameCopies.kt`, size check in `LauncherViewModel.kt`); Case stacks decoding ahead
    removed + capped warm window elsewhere (`ui/RealMedia.kt` WarmWindow, `ui/StyleCaseStacks.kt`, QA hook `voltcap_test_warm`
    in `ui/TestHooks.kt`); popup WindowShade (`ui/RowRevealWindow.kt`, `ui/GlanceWindow.kt`); printed Game Boy label
    (`ui/GeneratedLabels.kt`, `ui/CartShells.kt`); hacks without the base game's label (`data/HackLabels.kt` new,
    `data/PhysicalOverrides.kt`, `LauncherViewModel.kt`). Tests WarmWindow461Test, PrintedGameBoy461Test, PopupShade461Test,
    HackLabels461Test; PopupReview443Test and Labels460Test updated.

20. **Update 462.** Controller contract (`ui/InputContract.kt` new; gate in `ui/StyleHomes.kt`; Select / Start re-binds removed from
    StyleBlades / StyleMagazine / StylePanels / StylePocketCamera / StyleTerminal / StyleRentalShop / StyleGreenCore / StyleBubbles /
    StylePocketQuest, PocketRibbon launch hold; hint / help texts), themed pickers (`ThemedPicker` in InputContract.kt,
    `ui/CategoryLooks462.kt` new, LauncherScreen openCategoryScreen + QA overlay log, PlatformBrowser, PlatformBrowserSettings,
    CategoryLooks, SettingsDialog, data/Features pbThemed + 3 looks), photo-made paks (`ui/PhotoShells.kt` new, CartShells,
    GeneratedLabels edge-colour borders, LauncherViewModel, Features photoShells, SettingsDialog switch, MediaInspectView / TestHooks QA
    hooks). Tests InputContract462Test, PhotoShells462Test.

21. **Update 462b.** PhotoShells verified + turned sides / back from the photo (`ui/PhotoShells.kt` sideTone / why / MediaTrace / saved
    findings, `ui/SolidPiece.kt`, `ui/MediaInspectView.kt`), QA source trace (`ui/CartShells.kt`, `ui/PhysicalMedia.kt`), Inspect
    hook art / name fields (`ui/MediaInspect.kt`, `ui/LauncherScreen.kt`, `ui/GameWindow.kt`, `ui/StyleCardboardBox.kt`). Test
    PhotoShells462Test updated.

## Placeholders in the 0.6.66 tree
`app/src/main/java/com/shelf/launcher/ui/ProfileLooks.kt`, `app/src/test/java/com/shelf/launcher/ui/ProfilePicker445Test.kt` and
`TwinBack447Test.kt`, `NavBar449Test.kt`, plus `data/ContinueApps.kt`, `data/ContinueApps450Test.kt`, `device/ChargeFastStop450Test.kt`, `device/ChargeWake451Test.kt`, `ui/PopupMore451Test.kt`, `ui/PopupMore452Test.kt`, `ui/PopupReopen453Test.kt`, `ui/CategoryBar453Test.kt`, `ui/SwitchTitle453Test.kt`, `data/TrashFolders453Test.kt`, `ui/PreviewCategory454Test.kt`, `ui/SaturnDreamcast454Test.kt`, `ui/RibbonTop455Test.kt`, `ui/PanelHold456Test.kt`, `ui/PqTabs456Test.kt`, `ui/Render457Test.kt`, `ui/SideEdge458Test.kt`, `ui/GbPak458Test.kt`, `data/LabelIds.kt`, `ui/GeneratedLabels.kt`, `ui/Labels459Test.kt`, `data/LabelFolder.kt`, `ui/Labels460Test.kt`, `data/GameCopies.kt`, `data/GameCopies460Test.kt`, `data/HackLabels.kt`, `data/HackLabels461Test.kt`, `ui/WarmWindow461Test.kt`, `ui/PrintedGameBoy461Test.kt`, `ui/PopupShade461Test.kt` (and their `src_copy/` / `project_copy/` copies) are EMPTY placeholders (only a package line); they are listed in `.gitignore`,
never committed, and add nothing to a 0.6.66 build. (Update 462 adds ui/InputContract.kt, ui/CategoryLooks462.kt, ui/PhotoShells.kt, InputContract462Test, PhotoShells462Test.) Safe to delete by hand.

## Apply when 0.6.67 starts (after 0.6.66 is published)
    cd C:\Users\User\AndroidStudioProjects\ShelfLauncher
    del app\src\main\java\com\shelf\launcher\ui\ProfileLooks.kt, app\src\test\java\com\shelf\launcher\ui\ProfilePicker445Test.kt, app\src\test\java\com\shelf\launcher\ui\TwinBack447Test.kt, app\src\test\java\com\shelf\launcher\ui\NavBar449Test.kt, app\src\main\java\com\shelf\launcher\data\ContinueApps.kt, app\src\test\java\com\shelf\launcher\data\ContinueApps450Test.kt, app\src\test\java\com\shelf\launcher\device\ChargeFastStop450Test.kt, app\src\test\java\com\shelf\launcher\device\ChargeWake451Test.kt, app\src\test\java\com\shelf\launcher\ui\PopupMore451Test.kt, app\src\test\java\com\shelf\launcher\ui\PopupMore452Test.kt, app\src\test\java\com\shelf\launcher\ui\PopupReopen453Test.kt, app\src\test\java\com\shelf\launcher\ui\CategoryBar453Test.kt, app\src\test\java\com\shelf\launcher\ui\SwitchTitle453Test.kt, app\src\test\java\com\shelf\launcher\data\TrashFolders453Test.kt, app\src\test\java\com\shelf\launcher\ui\PreviewCategory454Test.kt, app\src\test\java\com\shelf\launcher\ui\SaturnDreamcast454Test.kt, app\src\test\java\com\shelf\launcher\ui\RibbonTop455Test.kt, app\src\test\java\com\shelf\launcher\ui\PanelHold456Test.kt, app\src\test\java\com\shelf\launcher\ui\PqTabs456Test.kt, app\src\test\java\com\shelf\launcher\ui\Render457Test.kt, app\src\test\java\com\shelf\launcher\ui\SideEdge458Test.kt, app\src\test\java\com\shelf\launcher\ui\GbPak458Test.kt, app\src\main\java\com\shelf\launcher\data\LabelIds.kt, app\src\main\java\com\shelf\launcher\ui\GeneratedLabels.kt, app\src\test\java\com\shelf\launcher\ui\Labels459Test.kt, app\src\main\java\com\shelf\launcher\data\LabelFolder.kt, app\src\test\java\com\shelf\launcher\ui\Labels460Test.kt, app\src\main\java\com\shelf\launcher\data\GameCopies.kt, app\src\test\java\com\shelf\launcher\data\GameCopies460Test.kt, app\src\main\java\com\shelf\launcher\ui\InputContract.kt, app\src\main\java\com\shelf\launcher\ui\CategoryLooks462.kt, app\src\main\java\com\shelf\launcher\ui\PhotoShells.kt, app\src\test\java\com\shelf\launcher\ui\InputContract462Test.kt, app\src\test\java\com\shelf\launcher\ui\PhotoShells462Test.kt
    git apply --ignore-whitespace next/0.6.67/profile_picker.patch
(the patch also removes the forty `.gitignore` placeholder lines; SecondScreen / SecondScreenHome / TwinScreens / GameOptionsDialog / SettingsScopes / GameTitles / FocusEffects / PowerSaver / PhysicalMedia / StyleCircuit / StylePocketQuest / SolidPiece are CRLF in git — `--ignore-whitespace` handles them). Or copy `next/0.6.67/src_copy/*` over `src_copy/` and
`next/0.6.67/project_copy/*` over `project_copy/`, then sync. **Also copy the same files into `project_copy/` (or delete the stale
`project_copy` copies of SUMMARY.md / .gitignore / defects.json): `sync_sources` copies project_copy over the root and put older
docs back once (update 447).** Then: testDebugUnitTest (ProfilePicker445Test, TwinBack447Test), `python3 tools/qa/selftest/test_qa.py`,
assembleRelease, device QA `pickdetect.sh`, `pickerlooks.sh`, `pickerux.sh`, `twinback.sh` (Thor).

22. **Update 463.** PhotoShells for every console with a real cartridge / card (`ui/PhotoShells.kt`: SYSTEMS / CONSOLE / shellOfGame via
    authenticIdFor, tolerance / tinted / sameKind, sticker must be found, STICKER sfc / ds / 3ds + WHOLE, inventory QA);
    `ui/TestHooks.kt` (photoshells hook: analyseAll + inventory); `ui/MediaInspectView.kt` (QA `lab_` art as label scan);
    tests PhotoShells463Test (new), PhotoShells462Test (updated). Matrix: tools/qa/PHOTOSHELLS_463.md.

23. **Update 464.** Real-scan guard test `RealScans464Test` (project_copy test, new). No app code change. Evidence: tools/qa/REAL_SCANS_464.md.

## Notes
- The Odin 2 Portal saved `picker_look = AURORA` during update 446's Settings-preview test, so 0.6.67 would show Aurora there
  (a saved choice is kept). If you want Midnight there, pick it in Settings (or QA: `--es voltcap_test_pref picker_look=MIDNIGHT`).
- `PICKER_LOOKS_446.pdf`: before / after and all six looks on the GT78 and the Odin. Update 447 sheet: `TWIN_BACK_447.pdf`.
