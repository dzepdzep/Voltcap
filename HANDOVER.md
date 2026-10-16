# Voltcap — handover to a new Claude

Written 12 Oct 2026, after update **(145)** (kept up to date to 207). Read this whole file before you change anything, then read the top 3–5 entries of `SUMMARY.md`.

## What this is
**Voltcap** is my Android game launcher, an Android Studio project called "ShelfLauncher" with package `com.shelf.launcher`. It is written in Kotlin with Jetpack Compose.

I run it on three handhelds:
- AYN Thor (two screens, Android 13/14);
- AYN Odin 2 Portal;
- Retroid Pocket Classic.

## Where things are (on my PC)
- **Project root:** `C:\Users\User\AndroidStudioProjects\ShelfLauncher\`
- **`src_copy\`:** the mirror of the Kotlin source (`app/src/main/java/com/shelf/launcher/`). It contains `data/`, `device/`, `spotify/`, `ui/` and the top-level files. **This is the only code you edit.**
- **`project_copy\`:** non-Kotlin files (resources, assets, tools), at the same path they have relative to the project root.
- **`SUMMARY.md`:** the running log of every update, newest first.
- **Other notes:** `CLAUDE.md`, `FEATURES.md` and `CURRENT_TASK.md` (this one is older and may be out of date).
- **Release and Google Drive set-up:** `tools/release/GITHUB_RELEASE.md`.

I copy `src_copy` into `app/src/main/java/com/shelf/launcher/` myself (robocopy), and `project_copy` over the project root, with the PowerShell block Claude gives me (rule 13).

## Rules (always)
1. **Only edit** `src_copy` (and `project_copy` for non-Kotlin files). Never touch `app/src`.
2. **After every update, add an entry at the top of `SUMMARY.md`,** and bring this handover file up to date at the end (so a new Claude can take over at any time). The entry's title contains the next number, e.g. `## Update — <date> (131): <short title>`. The entry lists:
   - **Copy:** new, changed and emptied files;
   - **Files to delete:** if there are any;
   - **What changed:** in plain words;
   - **Check:** what you checked, what you fixed during the check, and what was **not tested**.
3. **Big check before every delivery:**
   - Get a fresh copy of each file from my PC first; never edit from an old copy.
   - When you're done, reread every change line by line against my PC's current copy.
   - Type-check before you ship. Nothing ships that doesn't compile.
   - The type-check is expected to give **0 errors**. (The old "5 known SvgDecoder errors" were only a missing `coil-svg` jar; it's in `tools/compile_check/paths.txt` since 122.)
   - The previous Claude had a type-check set up in its own workspace (kotlinc 2.2.10 plus the project's dependency jars listed in `tools/compile_check/paths.txt`, from my Gradle cache, plus `android.jar` from SDK platform 36). You'll need to set up your own. If you can't, tell me and I'll build in Android Studio.
4. **Reviews and bug checks:** go through every file, don't skip any. **Before every delivery, bug check** everything the update touches and everything it reaches (callers, shared state, other screens using the same code), and say in SUMMARY what was checked and fixed.
5. **Don't operate my PC** to run builds, and don't take over my computer. Work on the files. No subagents unless I ask.
6. **No Shizuku and no dependency on other apps.** Root features use the handheld's own root service (AYN / Retroid "PServer"), through `device/RootShell`.
7. **Controller (D-pad), touch and second-screen support** must keep working everywhere.
8. **Original names and art only:** no copied brands or characters. The exceptions are the Steam case template I supplied myself, my own spine pack, my cartridge pack and my optical-system (disc console) pack (all three downloaded since 131, see below), my 3D shape library with its logos (the "3D models" pack since 136, v6.8 and the ES-DE console logos in `model_logos.zip` since 137) and the console logos in `assets/platform_logos`, `assets/badge_logos` and `assets/badge_icons` (Dan Patrick's v2 Platform Logos, credited in the app and in `badge_logos/CREDITS.txt`). The mascot (Volt the cat) was removed in 122 at my request.
9. **No lower-case text on popups,** buttons or labels.
10. **Ask me** when you don't understand something. Don't guess.
11. **Always give exact locations:** full paths on my PC (e.g. `C:\Users\User\AndroidStudioProjects\ShelfLauncher\src_copy\ui\StyleCrossMenu.kt`) for every file to copy or delete, and the exact Settings path (Settings › … › …) for every option, in SUMMARY and in your replies.
12. **Dexing:** the Kotlin type-check doesn't catch everything Android's dexer rejects. After each compile, search the compiled classes for `NON_LOCAL_RETURN`. Never `return@key` (or any early return) out of an inline composable such as `key { }`. That broke the build in 125 (fixed in 127).
13. **Always give a PowerShell command** in your reply and in each SUMMARY entry: one block to paste that first deletes every file under "Files to delete" (in `project_copy` first, so robocopy can't bring them back, then in the project, with `Remove-Item -LiteralPath … -ErrorAction SilentlyContinue`), then runs
    `robocopy "C:\Users\User\AndroidStudioProjects\ShelfLauncher\src_copy" "C:\Users\User\AndroidStudioProjects\ShelfLauncher\app\src\main\java\com\shelf\launcher" /E /NJH /NP` and
    `robocopy "C:\Users\User\AndroidStudioProjects\ShelfLauncher\project_copy" "C:\Users\User\AndroidStudioProjects\ShelfLauncher" /E /NJH /NP`.
14. **Always give me the easiest, fastest way** to do anything I have to do myself: one PowerShell block to paste rather than many manual steps, Git from the command line rather than clicking through web pages, and so on. If a manual way is the only option, say why. (Example, 140: the packs go up with one `git clone` / `robocopy` / `git push` block, not 100-file web uploads.)
15. **GitHub (since 144):** the project folder is a git repo on `github.com/dzepdzep/Voltcap`, branch `main` (public: the code, the art packs in `packs/v1`, the releases). Every update's PowerShell block ends with the commit and push, after the robocopy lines: `cd "C:\Users\User\AndroidStudioProjects\ShelfLauncher"`, `git add -A`, `git commit -q -m "Update <n>: <short title>"`, `git push origin main`, (since 209) `& "$P\tools\release\release.ps1" <next 0.6.x> "<notes>"` (GitHub release "Voltcap <version>" with Voltcap-<version>.apk — the in-app updater's source), then `.\gradlew installRelease` (since 211; installDebug only when a check needs run-as). `.gitignore` keeps `app/libs/discord`, `src_copy`, `project_copy`, `Claude outputs`, `screens`, logs, keys and builds off GitHub. One-time set-up: `tools\release\setup_github.ps1`. Never put an interactive command (Read-Host, a sign-in) in the middle of a pasted block — pasted lines after it get read as its answers; run those as a one-line script call on their own.
16. **Signing (since 144):** `keystore.properties` (your release key, never in git) signs debug and release builds alike (`app/build.gradle.kts`), so Android Studio installs, GitHub releases and the in-app updater all install over each other. Changing keys needs an uninstall on each handheld (Settings › Backup › Export first).

## How to deliver
- **Before editing:** copy each file you'll touch from my PC and note its modified time.
- **When you write back:** only overwrite a file if I haven't changed it since you copied it.
- **New files:** go next to the others in `src_copy`.
- **Emptying a file:** reduce it to just its `package` line, so the build still works, and list it under "Files to delete".
- **Then** update `SUMMARY.md` and tell me in a few lines what changed and what I should test.

## Things to know about the code
- **Settings:**
  - Prefs live in `data/Features.kt` (`BoolPref`, `IntPref`, `StringPref`, `SetPref`, `EnumPref`). Read them in Compose with `.state.value` and outside it with `.value`.
  - Inside Compose, reach a nested enum by its full name (`com.shelf.launcher.data.Features.X`).
  - Bigger appearance settings are in `data/AppearanceStore.kt`.
- **Compose pitfalls that caused bugs before:**
  - Don't read a CompositionLocal's `.current` inside `runCatching`; read it into a `val` first.
  - Use `rememberUpdatedState` for lambdas used inside `pointerInput(Unit)`.
  - Don't key `pointerInput` on a size: it restarts the gesture on every resize.
  - A child placed outside its parent's bounds gets no touches.
- **Main screen:** `ui/LauncherScreen.kt` is very big. The layouts are the `Style*.kt` files, plus `HomeChrome.kt`, `HomeParts.kt` and `HomeEdit.kt` (Edit Home).
- **Animated backgrounds:**
  - `ui/ThemeScene.kt` holds the shader scenes, quality and resolution, and `idleFrameMs`.
  - `ui/PowerSaver.kt` holds `HomeIdle`, `SecondIdle`, `LocalSceneCovered` and `LocalSceneMinFrameMs`.
  - `ui/ThemeShaders.kt` is generated by `tools/themes/gen.py`. Never edit it by hand.
  - Blurred pictures are made once in `ui/BakedBlur.kt` and `ui/SmoothBlur.kt`.
- **In-game overlays:** `ui/PerfOverlayService.kt` runs them (drag to move, placed with the screen's real size). Each overlay sits inside its own `ui/OverlayShell.kt` window frame (fold arrow in the overlay's outer corner, folded tab, resize by dragging a corner or pinching). The views are `MusicOverlayView` and `RaOverlayView`.
- **Second screen:** `ui/SecondScreen*.kt` and `ui/CompanionActivity.kt`. Its pages are `SecondPage` in `AppearanceStore.kt`; the Games page (console folders, popup of games) is `ui/SecondGamesPage.kt`, its logos `ui/PlatformLogos.kt`. The Handheld menu layout and "Games below / Swap screens" were removed in 122; `ui/DualScreenGrid.kt` only holds `DualGridArt` now.
- **Endless rows (125):** `ui/EndlessRow.kt` (`rememberLoopFocus`, `LoopRow`, `FollowFocus`, `loopsOnItsOwn`). The view model's `loopTick` / `loopStep` say which way a step went; `AnchoredStrip(loop = …)` and the lazy rows use a virtual index.
- **Game window (125):** `ui/GameWindow.kt`. Its pages are `WinPage` (main, details, options, trophies), what shows where per layout is `WinPlan` (Features.gameWindowPlan), and the options list is `gameWindowOptions` in `LauncherScreen.kt`.
- **Per-layout plans (125):** `NamePlan` and `HeroFitPlan` in `AppearanceStore.kt` (Features.namesByLayout, heroFitByLayout).
- **Edit Home (125):** `PartsLayer.z` (floating parts over the games, under popups), `HomeEditState.snapToOthers`, `barFolded`; part styles hand `LocalPartLook` to `PartBackingBox`.
- **Your own presets (125):** `LayoutPresetStore` saves the whole "appearance" prefs (typed) and `Features.snapshot()`, and puts them back on apply (`restoreAll`).
- **Where the focused game card is:** `ui/FocusedCard.kt` (`Modifier.focusedCard()` on each layout's focused card). The cover light and the game window's grow animation use it.
- **Volume mixer** (`device/audio/`):
  - **Automatic = sound effect (since 122):** `MixerEffects` (DynamicsProcessing on each app's audio session, held by Voltcap). The root helper is still used to find who's playing.
  - **Player volume (picked by hand):** `AppMixer` starts `MixerHelper` as root through `app_process`. It reads Android's active players and sets each app's player volume, or a constant "system duck" volume shaper (the second way). Some firmware (the Thor) silently ignores both.
  - **Announced sessions:** `AudioSessions` listens for the audio sessions apps announce.
  - **Spotify:** the Spotify Web API is the fallback for Spotify.
  - Mixer check is in `ui/MixerActivity.kt`.
- **Cartridges look (128):** `ui/CustomCarts.kt` — `CartLook` (the pack's 14 shapes, each with its size and window in the pack's own units from its `manifest.json`), `CartSource` (Original / Custom), `CustomCarts` (Voltcap console key → the pack's ES-DE id, asset path), `customCartFor(platform)` and the `CustomCart` composable. `mediaFor` hands out `MediaPick(…, CUSTOM_CART + platform, aspect)`; `PhysicalMedia` draws it first thing. Prefs: `Features.cartsOnly` (the look), `cartSource`, `cartLook`, `cartClear`. Pictures: downloaded since 131 (see Extra art packs), `files/packs/carts/<shape>/<o|t>/<id>.webp`, 1600 px wide, lossless, window transparent.
- **Look styles (129):** `ui/LookStyles.kt` — one console's disc / case / spine for every game (`Features.discStyle`, `caseStyle`, `spineStyle`, "" = each game's own), R1 › Disc / Case / Spine tiles (`styleTile` in `QuickSettings.kt`). `LookStyles.casePlatform` / `spinePlatform` map a game's console to the picked one; `mediaFor(asDisc)` uses `LookStyles.disc()`.
- **Real scans standing free (129):** `realScanOf(type, typed)` and `mediaRound(platform)` in `PhysicalMedia.kt`, drawn with `RealMediaPicture` in every row (no card behind them); `isFreeMedia(CARTRIDGE_DISC)` is true for every console with a shell.
- **Achievement placement (129):** `Features.achPlace` (`AchPlace`: CORNER, ABOVE, NAME, UNDER, OFF), R1 › Achievements tile (id `progressbar`) and Settings › Achievements › Achievements on cards & pop-ups › On the cards › Trophy count › Where on the game. Covers: `trophyAlign` in `GameCard.kt`; other layouts: `achChipAlign()` in `StyleHomes.kt` (Grid, Tiles, style homes). The tile also sets the wide capsule's `progressOnBanner` / `progressAbove` / `badgeNextToTitle` / `trophyOnBanner`.
- **Pulled-out case (129):** `PulledOutCase` in `ShelfHome.kt`, shared by the Game shelf and the TV room shelf; `Features.ShelfPulled` FRONT (real case, else Voltcap's; default), SIDE, OURS, COVER.
- **Size of the game you're on (129):** `cardSizes()` in `GameCard.kt` grows it by 15 % × `MotionPrefs.pop()` (0..1); `GameDock` takes `grow` from `sizes`. Carousels: `Features.carouselSize` (60–200, default 130), `carouselEdge` default on.
- **Google Drive sync:** `data/CloudSync.kt` and `data/DriveClient.kt` use the `drive.file` scope.

## State after update 122 (not tested on a device yet)
- **Second screen Games page:** folders, the popup of games, a tap on a game opening it on the main screen; the arrows by the tabs.
- **Console logos** (SVG through Coil) on the Games folders and the Cross menu folders.
- **Overlays:** drag to every edge, corner drag and pinch resize, the fold arrow in the outer corner.
- **Volume mixer:** Automatic now uses the sound effect. **Next step:** run Mixer check while an app plays; each app line says whether the effect is on its sessions.
- **Save Vault:** GameCube saves should be back.
- **Game window:** opens on Play / Continue; disc games show the case with the disc.
- **Real spines and discs** need ScreenScraper scans; I have no ScreenScraper access right now.
- **Spines (125):** a real spine scan, else your spine pack (`vc_spine_*`, `CustomSpines.yours` in `CoverCases.kt`, title area and ink from your manifest), else Steam's spine, else the plain one; the name is fitted into the title area (`spineWithTitle` for the 3D boxes). The old `cov_spine_*` (except Steam's and Android's) and `tpl_spine_*` are to be deleted (see 125).
- **Spines (126):** Android games and apps use Voltcap's redrawn Android spine (`cov_spine_android`, laid out like the pack; in `CustomSpines.builtIn`), not the pack's two Android spines. Each `SpineTpl` carries its picture's `aspect`; the Game shelf and the TV room shelf size the slot from it, and `SpinePicture` never crops. `SPINE_MAX_ASPECT` is 0.125.
- **Spines (123/124, older):** Strips cut from box scans only with Game look › Spines cut from box scans (default off). Steam has its own custom spine. Since 124 a shelf spine is at most a tenth of its height wide (`SPINE_MAX_ASPECT` in `CoverCases.kt`), and the name auto-sizes over up to two lines. No other scraper Voltcap uses has spines; LaunchBox's Games Database ("Box - Spine", sparse, terms unclear) and EmuMovies (members) do.
- **Shuffle (124):** `ui/MusicShuffle.kt`, on the big player, the second screen's music page and the in-game music panel while Spotify plays. It uses the Web API when connected, else Spotify's media-session shuffle action.

## State after update 125 (not tested on a device yet)
- Everything in the 125 entry. Above all: endless rows on each layout, Spotlight's Row & menu look, Resume's fly-in, your spines, the mixer with AetherSX2 / ARMSX2, Sleep mode, the old popup's ◀ ▶, and presets carrying everything.
- My optical-disc pack (`Downloads\VoltCap_Optical_Disc_Systems_SystemLogoWhite_v3_30_with_Android_Steam`) is used since 131 (the disc-console shells, downloaded).

## State after update 126 (not tested on a device yet)
- **TV room grid** follows R1 › Look; only the Spines look shows the 3D cases turned to their spines (`spinesOn` in `TvRoomHome`'s `CaseGrid` call). `TvRoom.Games.SPINES` is now labelled "Grid · as the Look".
- **Frame rate:** Automatic quality picks SMOOTH (60) on every handheld that isn't low-end or short on memory, whatever the screen's Hz; FrameWatch still steps it down. `sceneFrameMs()` in `ThemeScene.kt` is the one rate for `SceneClock` and the music visualizers' `MusicClock`. Calm themes have no cap of their own any more. Packs: `PackRules.MIN_FRAME_MS` 14 (~60), `HEAVY_FRAME_MS` 28 (~30).

## State after update 127 (not tested on a device yet)
- **Cross menu achievements:** `ui/CrossTrophies.kt` (`CrossTrophies`, `crossTrophiesOn`, `CrossTrophyView.full`). It is shown from the `viewingAchievementsForGame` block in `LauncherScreen.kt` when the layout is the Cross menu and `Features.crossTrophies` is on; A opens `AchievementListDialog`, whose B comes back. The game window's Achievements goes there too on the Cross menu (`GameWindow.kt`, `WinExtras.onAchievements`).

## State after update 128 (not tested on a device yet)
- **Cartridges look:** Original / Custom, Opaque / Clear and the 14 shapes, from R1 › Cartridge (only while the look is on; tap = next shape, hold = everything) and Settings › Game look › Games are drawn as › Cartridges. Labels are always the game's cover. The second screen doesn't use the pack.

## State after update 129 (not tested on a device yet)
- Everything in the 129 entry: symbol badges (`assets/badge_icons`, `BadgeLogos.ICON` in `ConsoleBadge.kt`), slimmer spines (`SPINE_ASPECT` 0.13), the PS1 case without its hinge, real scans standing free, Disc / Case / Spine / Achievements tiles, carousel Game size, the first game's capsule animation (`Features.capsuleRevealFirst`, off), `HeroFitPlan.of(layout, global, look)` filling the screen by default, the game popup (Play lit first, Achievements for that game, no cut words).

## State after update 130 (not tested on a device yet)
- **Hide navigation bar:** a watchdog in `MainActivity` (`SystemBars.navStuck`) re-hides a bar that is really up every 1.5 s (15 s after four tries); every Compose `Dialog` calls `ImmersiveDialogWindow()`; the other activities re-apply `SystemBars.apply` on focus.
- **Circuit board:** its bar (Play, Settings, Details) has its own arrows in `styleHomeKey` (Play above, the two under it).
- **Blur:** `BakedBlur.sideFor` / `radiusAt` size the baked copy by the blur and the screen.
- **Game shelf:** scroll and pull-out are read in the offset / graphics layer lambdas, not while composing (`ShelfHome.kt`).
- **First game → menu (Side menu, Resume, Dashboard):** `StyleNav.movedAt`; ◀ goes into the menu only on a fresh press ~280 ms after landing.

## State after update 131 (not tested on a device yet)
- **Extra art packs:** `data/Packs.kt` (what's downloadable, what's on the device, the download on one background thread with OkHttp, `version` bumped whenever pieces arrive or go) and `ui/PacksUi.kt` (`PacksFirstAsk`, shown once from `LauncherScreen.kt` after the first-time setup; `PacksCard` in Settings › Game look › Extra art packs).
- **Where they're hosted:** the public repo `dzepdzep/Voltcap`, folder `packs/v1` (`Packs.RELEASE` = `https://raw.githubusercontent.com/dzepdzep/Voltcap/main/packs/v1/`). I upload the files myself on github.com (Add file › Upload files, ≤100 files and ≤25 MB each per upload); the whole project can't be pushed because `app\libs\discord\lib\debug` has files over GitHub's 100 MB limit (140). All 234 files live in `project_copy\packs\v1\`: rebuilt in 140 from my originals in Downloads (`cartridges`, `VoltCap_Custom_Console_Spines_All`, `VoltCap_Optical_Disc_Systems_SystemLogoWhite_v3_30_with_Android_Steam`) plus the 3D models (137). For a new version of a pack, add `packs/v2` and change `RELEASE`.
- **Files:**
  - `carts_<id>.zip` (64): `<shape>/<o|t>/<id>.webp`, unpacked to `files/packs/carts/`, counted once `<id>.ok` is written;
  - `spine_<name>.webp` (60): `files/packs/spines/`;
  - `disc_<name>.webp` (27): `files/packs/discs/`.
- **Spines without resources:** `CustomSpines.meta` (title box, ink, aspect per console), `yours` only lists spines on the device; `PackSpines` gives each file a stand-in id below zero, and `spineImage(res)` loads either a resource or a downloaded file (`rememberSpine`, `Box3D`).
- **Disc consoles:** `DiscShells` in `CustomCarts.kt` (shell aspect, window measured from my pictures), `discShellFor(platform)` (Cartridges look, disc-like console, not overridden by Custom's cartridge), drawn by `DiscShellArt`.
- **R1 › Cartridge** shows only with `Packs.anyCarts()`.

## State after update 132 (not tested on a device yet)
- **Spotlight's Home menu** (`SpotLook.HOME_MENU`, the default since 132): `SpotHomeMenu` / `SpotMenuTop` / `SpotMenuTiles` / `SpotHints` in `StyleSpotlight.kt`. The status bar steps aside there (`spotOwnTop` in `AppearanceStore.kt`, unless `Features.spotStatusBar`), and the legend too while its hints are on (`legendOn`, which every legend gap now reads). The hints are `StyleNav.bar`; X uses the new `StyleActions.favorite`.
- **Cross menu Trophies category:** `SpecialFilter.TROPHIES`, only in `categoryOrder` on the Cross menu with `Features.crossTrophyCat`; A there opens `viewingAchievementsForGame` (`openGame` in `LauncherScreen.kt`); leaving the Cross menu while in it goes back to All.
- **Game window achievements page:** `WindowTrophies` in `GameWindow.kt` has an order row (`Features.winTrophyOrder`, `trophyOrder()`) and opens an achievement's comments under it (`TrophyMore`, `WinExtras.onLoadComments` → `viewModel.loadAchievementComments`). The two readings I asked about are in the 132 entry.

## State after update 133 (not tested on a device yet)
- **Only for this layout:** `data/LayoutOnly.kt` (each layout's own values `own`, the shared values of the layout on screen `shared`, typed like the presets; noted while Settings › Home is open — `LayoutOnlyCard` in `SettingsDialog.kt` — and swapped in `LauncherViewModel.setAppearance` via `leaving` / `entered`).
- **Edit Home stretch:** `Features.widgetStretch`, `HomeWidgets.stretch()`, `HomeEditState.liveStretch` / `stretchBy`, `Modifier.stretched()` and `StretchHandle` in `HomeEdit.kt`; layouts' own parts stretch through their graphics layer in `editablePart`.
- **Backings:** `Features.partBackingSolid` and `backingUnder()` in `HomeParts.kt` (a dark layer under each backing).

## State after update 134 (not tested on a device yet)
- **3D cases:** `ui/Case3D.kt` (`Case3D` composable; `CaseDraw` does the perspective maths — yaw/pitch, back-face culling by projected area, faces warped with `setPolyToPoly`, mirror image under the floor in a faded layer). `CarouselView.ARC / SPINE_WALL / TURNED` (with `cases = true`), `Carousel.cases3d`, `Carousel.reach` (Spine wall: ×3 each side) and `Features.carousel3d` in `Carousel.kt` / `Features.kt`. `GameCarousel` hands the turn (`Place.turnY`) to the case instead of its graphics layer; the flat drawing is now `CarouselArt`, the name `CarouselName` (above for Turned row).
- **Spine cache:** `SpineCache` in `CoverCases.kt`. `rememberSpine` (now returns `SpineCache.Ready?`, picture + ink) and `rememberTitledSpine` (`SpineCache.Titled`) make the pictures on IO, once each. `SpinePicture`, `Box3D` and `Case3D` use them. `spineImage` stays, but nothing in the shelves calls it any more.

## State after update 135 (not tested on a device yet)
- **Carousel spines:** with spines aside (Spines look, or Record flip in COVER / CASE / CASE_REAL) in Tucked / Record flip / Flat row, `GameCarousel` places them itself: `Carousel.SPINE_SIZE` (0.66 of the middle game), `Carousel.spineScale(d)`, packed from just beside the cover; "N each side" is ×3 for them (`spinesAside`).
- **Endless carousel:** `GameCarousel(loop, loopTick, loopStep)` uses `rememberLoopFocus`; items are keyed by their endless place, the game is place mod count. Callers: Grid (`LauncherScreen.kt`), Side menu (`loopRowOn(c)`), TV room (`TvRoomHome(loop, loopTick, loopStep)`). `loopsOnItsOwn` is true for GRID with Carousel look and TV with its carousel.
- **Seeing the handheld:** Claude can use the `voltcap-adb` MCP server (`tools\adb_mcp\server.js`, in Claude's desktop config): screenshot (saved in `screens\`, also inline), press (A, B, X, Y, L1, R1, D-pad…), tap, swipe, logcat, shell. Debug builds can be read with `run-as com.shelf.launcher` (e.g. `shared_prefs/voltcap_features.xml`). mobile-mcp doesn't work from the cloud session (its `device` argument is stripped).
- **3D shape library (v6, `Downloads\VoltCap_3D_Shape_Library_v6_3_Complete\VoltCap_v6_Refined\v6`):** 81 GLB models, all pass the Khronos glTF validator. Used since 136 as pre-rendered pictures (see below).

## State after update 136 (not tested on a device yet)
- **3D models look:** `ui/Models3D.kt` — `ModelFinish` (OFF / REAL / CUSTOM / CLEAR), `ModelSpare`, `ModelAngle`, `Models3D` (console key → model id tables `REAL` / `CUSTOM`, `idFor`, `available`, `on`, `meta`, `compose`, two LRU caches) and the `Model3DArt` composable. Prefs `Features.modelFinish / modelSpare / modelAngle`; UI `ModelOptions` in `GameLookPicker.kt` (Settings › Game look › Covers, cases & discs › 3D models). `mediaFor` hands out `MediaPick(…, MODEL_3D + id, aspect)` first thing; `PhysicalMedia` draws it; the carousel's `cases3d` path draws `Model3DArt(yaw = p.turnY)` when the console has a model.
- **The pack:** `Packs.Pack.MODELS`, `model_<id>.zip` (81, 11 MB) → `files/packs/models/<id>/` with `<yaw>.webp` (model, window dark), `<yaw>_g.webp` (window light, alpha = where the window shows) for yaw −60…60 step 20 (since 166: −160…180, see State after 166), and `meta.json` (w, h, `art` = window aspect, `angles` = the window's 4 corners per yaw, normalised, TL TR BR BL). App side: cover cut to `art`, `setPolyToPoly` to the corners on a black layer, × gain (PorterDuff MULTIPLY), + model (ADD). Made by `tools/render3d` (WebGL2 renderer in headless Chromium, Playwright + sharp). The zips are in `project_copy/packs/v1/`; I upload them to the repo's `packs/v1` on github.com (see Extra art packs).
- **Mixer:** `AppMixer.fxGlobal` (meta "fx_global"): when the firmware moves the effect to session 0 (AYN: "createEffect sessionId 0" in logcat), `checkRemap` notices (no "effects for session X" in `dumpsys media.audio_flinger`), Automatic uses the helper instead. The helper is started on the single-thread `starter` with `setsid … &`, so `apply()` never waits on it (it hung forever before).
- **Gesture hint:** `HintKey("global","hide_nav_bar")` (AYN) is the first candidate; `hintKey()` re-detects once (`keys_v` = 2).
- **Art drop:** `Features.artDropCase` (used in `CoverFace`, `CoverCases.kt`) and `artDropModel` (`Models3D.labelPicture`), R1 tile `artdrop` + `ArtDropPopup` in `QuickSettings.kt` (bottom slider, no dim). **Name strip:** `Features.modelName`, `PhysicalMedia(title)`.
- **Save Vault 3D card:** `SaveVaultStyle.CARD_3D`, `Features.saveVaultCard` (`ModelSpare`) / `saveVaultClear`, `Models3D.saveCardId()`, drawn in `StyledSaveCard` (`SaveVaultStyles.kt`). The cards aren't used for games.
- **Even row carousel:** `CarouselView.POP` in `Carousel.kt`.
- **Wide capsule fixed:** `fixedSlot` in `GameDock.kt` / `GameCard.kt` (focused wide on the left, not the carousel / pinned first): drawn on its own, no reveal, nothing on its left.
- **Text:** `fitMin(size)` in `FitText.kt` is the floor for every `StepBased` auto-size (≥ 10 sp, or the size itself when smaller); auto-sized line heights are in em.
- **Shades:** status bar edge (`StatusBar` in `HomeChrome.kt`), Dashboard menu column and Now playing bottom, Side menu buttons fit (`SidePanel`), Spotlight name drawn with `drawText`, `stylePlaytime(ms, played)`, Game shelf `makeRoom`, Console hub ticker fade and `ConsoleStatus` only without the status bar.

## State after update 137 (not tested on a device yet)
- **Library v6.8** (`Downloads\VoltCap_3D_Shape_Library_v6_8_Complete\VoltCap_v6_Refined\v6`): `branding/card_slots.json` (tint materials `molded_polymer` / `recess_polymer` / `translucent_tinted_polycarbonate`, nodes `console_logo` / `console_name`, `default_system`), `branding/systems.json` (203 ES-DE ids: shell / badge sRGB), `branding/logos/<id>.png` (64).
- **Pack v6.8:** marker `<id>.v68` (`Packs.marker`), so 136 pieces are fetched again. Paintable pieces (`meta.tint`: `sys`, `clear`, `trans`, `levels`, `logo` quad + `ls` light per yaw, `logoAspect`, `own` for the Save Vault cards) also have `<yaw>_t<level>.webp`, `<yaw>_b.webp` (badge weight), `<yaw>_gw.webp` (clear). `model_logos.zip` → `models/logos/<esde>.webp` + `systems.json` (`{id: [shell, badge]}`), item `Models3D.LOGOS` (serves every console).
- **Painting:** `Models3D.tintFor(id, platform, shownName)` → `Tint(shell, badge, logo)`; `paint()` (solid: `Tone.inv` / `Tone.out` around ACES + sRGB, exact; clear: piecewise in sRGB level, premultiplied, alpha by mean level) then the logo via `setPolyToPoly` × `ls`; LRU `tinted`. `compose(…, pos, tint)`; clear art gain × srgb(trans·c)/srgb(trans). `MediaPick.platform` for a model is now `~m3d:<id>|<console>`. `GENERIC = "cart_snes"` for consoles without a custom piece (needs the logos piece).
- **Colours:** `ModelColors` (prefs `model_colors`: `all`, `g:<console>|<shown name lower-case>`), init from `Packs.init`. R1 tile `cartcolour` → `HomeAdjust.open = Target(CART_COLOUR, …)`.
- **Home-screen controls:** `ui/HomeAdjust.kt` — `HomeAdjust.open`, `HomeAdjustBar(atTop = dock BOTTOM)` hosted in `LauncherScreen` (a `LaunchedEffect` closes the R1 panel). `ArtPos` / `ArtPlace` (prefs `artDrop*` −50…100, `artShift*` −50…50, `artZoom*` 50…200; `REACH` 0.3 of the window), used by `CoverFace` and `Models3D.labelPicture`. `ArtDropPopup` is gone.
- **Wide place:** `fixedSlot` in `GameDock.kt` is an `AnimatedContent` on `focus` (`Features.capsuleSwap`).
- **Carousels:** `Carousel.placeFlat` / `flatView` for the Voltcap row (`GameDock`) and Tiles (`TileHome`); 138: `Carousel.flatReach(bigW, half)` sets how many games each side they draw (enough to pass the screen edges with To the edge).
- **Artwork:** `Features.artSourceCover / Wide / Hero / Logo` (ArtSource names, "" = main) in `fetchArtFromSelectedSource` (per-field order). `data/PhysicalOverrides.kt` (prefs `physical_overrides`, "<key>\t<label>"): applied wherever `physicalMedia` is set in the view model (`physicalRaw` keeps the scans); UI in `ArtworkPickerDialog` (`PhysicalOverrides.SLOTS`).
- **Settings:** new top-level "Screensaver" category (`SettingsDialog.kt` CATEGORIES + `SettingsIndex`).
- **Gesture hint:** `GestureHint.forceOn` defaults to (immersive && `RootShell.available`) while "force" was never set; `onForeground` grants WRITE_SECURE_SETTINGS through the root service first. On the Odin 2 Portal the pill is the `SecondaryHomeHandle` window, gone with `global hide_nav_bar 1`.
- **Renderer:** `tools/render3d/batch2.js` (see its README).

## State after update 141 (not tested on a device yet)
- **Updates:** `data/AppUpdates.kt` (GitHub releases of `dzepdzep/Voltcap`, latest release's tag vs versionName, APK downloaded to the external cache and handed to Android's installer through the FileProvider; `REQUEST_INSTALL_PACKAGES` in the manifest), `ui/UpdatesUi.kt` (Settings › Updates & downloads: version, checker, PacksCard). Auto-check at start once a day (prefs `app_updates`). Release: `tools/release/release.ps1 <version> "<notes>"` (gh CLI).
- **Art packs in setup:** `FirstRunDialog` step "Extra art" (`PacksSetupStep` in `PacksUi.kt`); steps are matched by name now, not index.
- **Row glide:** `Features.rowGlide` (Quick 1100 / Smooth 380 / Slow 170 stiffness; carousel 160 / 300 / 460 ms) used by `dockMotion()` and `GameCarousel`.
- **Voltcap row carousel:** `GameDock` draws `GameCarousel` (Carousel.kt) when CLASSIC is in `carouselLayouts`; its box is `bigCover.height / (0.84 × carousel size)` tall. In `GameCarousel`, `cases3d` = 3D cases switch, or a case view (Arc / Spine wall / Turned row) with a case look; `modelLook` draws Model3DArt for the cartridges & discs look in any view.
- **First one wide:** `pinnedFirstOn(appearance)` (LauncherScreen.kt) = FIRST_ONLY + Voltcap row, not carousel / square tiles. GameDock pins game 1 (ignores Endless and centre focus); `moveFocus` wraps without Endless.
- **Borrowed case / disc:** `LocalCaseOwner` (CoverCases.kt, set by `CaseArt`) → `PlainFrame`: the template's frame outside the window tinted to its average colour (`FrameColors`), the game's console logo (`PlatformLogo`) in the biggest band. Borrowed disc → `genericDiscKey(own console)`; UMD → `"plain:<console>"`.
- **3D models look:** `LookStep(models = true)`, `setModelsLook()` and `Features.modelFinishLast` (GameLookPicker.kt).
- **Save Vault 3D:** `SwayingModel` (SaveVaultStyles.kt) — ±60° sway on the 7 pictures, the last few degrees by graphicsLayer. Open / crack animations removed from SaveExplorer (SaveOpenAnimation remains, unused).
- **Clear 3D:** `Models3D.compose` — clear art gain ×1.2 uncoloured (`CLEAR_ART_GAIN`), shell thinned over the window (`clearThinPaint`). Clear pack carts at 82 % alpha.

## State after update 142
- **3D cases in the case looks:** `Models3D.CASES` / `caseModel(platform)` (3D models on, the case downloaded) and `standYaw(focused)`; `CaseArt` (RealMedia.kt) draws `Model3DArt` first when there's one (not for a borrowed case). PhysicalMedia uses `standYaw` too.
- **Release:** `powershell -ExecutionPolicy Bypass -File "…\tools\release\release.ps1" <version> "<notes>"`; Java comes from Android Studio's `jbr` (JAVA_HOME set for the user in 141's reply).

## State after update 143
- **Normal row motion:** `fixedSlot` (GameDock.kt) now also needs `!bannerFree` — the fixed wide place only while the game you're on opens wide; cases / carts / discs slide like a cover row. Measured on the Odin with screencap frames (row line y=900, best horizontal shift per frame).
- **PS1 band:** `PS1_BAND` (0.0956, CoverCases.kt) cut off the left of PS1 case scans (`ScanCase(cutLeft)`); `CaseArtFace` never turns PS1 to its spine; `DiscInCase` uses the flat `CoverCase` for PS1 when there's no scan.
- **Carousel shade:** `CarouselArt` darkens only the drawn game (offscreen layer + SrcAtop), not a box over the place.
- **Art position:** `CoverFace` and `Models3D.labelPicture` fit the whole cover in the window, then Art size / position apply.

## State after update 144
- **Row glide:** `GameDock` no longer animates each card's x with its own spring (that never showed on the handhelds — the row jumped in one frame, measured with `screenrecord`). One `Animatable` `rowPos` (virtual index) glides; each card's x is `lerp(xAt(floor), xAt(floor+1))` of its distance from `rowPos`, read in `offset {}`. `glideBanner` / `glideDiscRoom` animate the focused card's room.
- **Case sizes:** `DiscInCase` and the "Case with its cartridge / disc" path size the case like a lone case (`h = min(maxHeight, maxWidth / aspect)`) and let the disc / cartridge overflow right (`wrapContentSize(unbounded)` + offset). `casePeek()` (PhysicalMedia.kt) gives the row the room after the focused game (`discRoom`). `discFrac()` / `DISC_OUT` shared.
- **Settings header:** one slim bar, window 94 % tall, no floating Close row.
- **Bug check 144:** 72 fixes (list in SUMMARY 144). Saved art cache now in `Art/cache` / `saved_art/cache` (ArtStorage.cacheDir), ArtFiles keeps an in-memory index (`savedFile`). SGDB `checkKey()` OK / REJECTED / UNREACHABLE with a retry job. `OtherDevices.deviceId` carries an ANDROID_ID mark; `retiredIds` cleaned off Drive. `other_devices`, `romm_downloads`, `sleep_radios_state` are INTERNAL prefs.
- **Measuring motion on a handheld:** `screenrecord --size 640x360 --bit-rate 600000 --time-limit 2`, base64 it out in 75k-character `cut -c` chunks, decode and `ffmpeg` it here, then match the row line frame by frame (screencap alone is ~95 ms a frame, too slow).

## State after update 145
- **Release builds (R8):** `rules.keep` keeps Room / WorkManager / startup classes made by reflection (the release APK crashed at start without them). To test a release build on the Odin without touching the installed Voltcap: rename its package with apktool (`renameManifestPackage` + authorities / custom permission renamed), sign with uber-apk-signer, install, read `logcat -b crash`, uninstall.
- **Drive:** `Backup.exportToDrive` / `readFromDrive` / `importFromDrive` (My Drive › Voltcap › voltcap-backup.json, falls back to voltcap-settings.json); `rememberDriveConnect` (DriveConnect.kt) signs in from anywhere; first-start Welcome uses it.
- **GameCard:** `pillTopRoom` keeps the top of a free-standing case / cartridge / disc clear for a top trophy count.
- **Case pictures:** `FillUnlessMuchCut` (CoverCases.kt) fills a window unless >15 % would be cut; Models3D labels use it too.
- **Shimmer:** `EffectClock.shimmer` runs until the deep idle (3 min), slower while idle.

## State after update 146
- **New layouts:** `HomeLayout` LIBRARY / STACKS / TIMELINE / ARCADE / CONSOLES (all `styleHome`), each in its own file `ui/Style<Library|CaseStacks|Timeline|Arcade|ConsoleStack>.kt` with `Style…()`, `…Key(StyleKeyCtx)` and `…Settings()`. `StyleKeyCtx` (StyleHomes.kt) carries key, first, focus, count, setFocus, sound, category, enterZone, reach, take. Prefs in Features (Update 146 block).
- **Banner page:** `Features.winBanner` → `GameWindow` hands over to `BannerGameWindow` (GameWindowBanner.kt) for every layout.
- **Carousel:** `CarouselView.RECEDE` (left-anchored; `anchorX` in GameCarousel, `mid` in GameDock's carousel `xAt`). Cover flow and "Arc / Spine wall for every look" were built and then removed: they already exist (Record flip, Arc, Spine wall).
- **Wide first game (pinnedFirst):** no longer pinned. Each card's x is `lerp(pinX(index, floor(rowPos)), pinX(index, floor+1))`; game 0 is wide, the rest covers, the focused cover bigger.
- **Live tiles / Channel grid background:** `liveArt` / `chanArt` (art behind via `styleArtBehind`) with `liveLight` / `chanLight`; one Background choice in their options.
- **Google sign-in screen:** `PRIVACY.md` at the repo root is the privacy policy link.

## State after update 147
- **Pictures as bitmaps:** `drawableBitmap()` (ui/DrawableBitmap.kt) turns any drawable into a bitmap; image requests that read pixels set `AnimatedArt.PARAM_STILL` (animated SteamGridDB covers came back as AnimatedImageDrawable → grey 3D pieces). `gamesCount(n)` there too.
- **Disc station:** `HomeLayout.DISCS`, ui/StyleDiscStation.kt (`StyleDiscStation`, `discStationKey`, `DiscStationSettings`), prefs `discSpin`, `discAbout`; StyleHomes asks for disc scans for it whatever the look.
- **Spines:** `SpineSlot` (ShelfHome.kt) keeps a spine's own shape; `Features.spinesOwn` (Matching spines) skips real scans.
- **Apps:** GameCard passes the app icon as the label / cover of the drawn media and case; AppIconArt only when neither is drawn.
- **First start:** `SettingsShareCard(receiveOnly = true)` under Welcome; the document goes straight to `Backup.applyDocument`, then restart.
- **Carousel:** `Features.carouselStage` (Dark stage) drawn behind GameCarousel; the case views (`view.cases`) switch Reflection and Dark stage on when picked. SPINE_WALL 72°, TURNED faces the middle.

## State after update 148
- **Settings jump:** `SettingsJump.pending` (ui/SettingsJump.kt) = a search hit's label; `jumpAnchor(label)` (on SwitchRow, RowChoice, StyleChoice, PercentSlider, SectionLabel, FoldGroup) scrolls it into view and flashes it. While a jump is pending every FoldGroup renders open; the folds around the hit (`LocalFoldKeys`) stay open; a jump that finds nothing gives up after 2 s.
- **Layout options:** FoldGroups `lo_window`, `lo_bg_names`, `lo_carousel`.
- **Animated covers:** `animatedArt` defaults to true; `Features.animatedFocusOnly` → GameCard asks `rememberCardArtRequest(…, still = true)` (PARAM_STILL, "|still" in the memory key) for unfocused animated covers.
- **Clips:** `ClipStore` (filesDir/clips, SHA-1 of the game key, limit `clipsMaxMb`); `Features.clipsOffline`; the view model queues saves (`saveClipLater`, one at a time, paused while playing) and `loadVideoUrl` prefers the saved file ("file://…").

## State after update 149
- **Auto backup:** `AutoBackupWorker` (12-hourly WorkManager check, runs `Backup.exportToDrive` when `Features.autoBackup` DAILY / WEEKLY has passed since `autoBackupAt`; needs `CloudSync` settings `connected`). Scheduled from the view model's init and when the choice changes (Settings › Backup).
- **What's new:** `WhatsNew.check` (ui/WhatsNew.kt) compares the installed versionName with prefs "whats_new"; on a change fetches GitHub `releases/tags/v<version>` and shows `WhatsNewDialog` (LauncherScreen, not during first run).
- **Share report:** `CrashReports.latestReportText` / `recentLog` appended to Diagnostics' report.
- **Save sync (already there, unchanged):** before a game — resting on it / opening its window fetches its saves from Drive (at most every 3 min, Play never waits); during — SaveWatchWorker uploads changed saves 10 → 20 → 40 → 60 min in (upload only); after — on coming back, that game's saves both ways, then a full sync (PostGameSyncWorker retries offline).

## State after update 150
- **Device test method:** force-stop, edit `shared_prefs/appearance.xml` (`home_layout`) / `voltcap_features.xml` with `run-as com.shelf.launcher sed -i …` (single-quoted, no `sh -c`), start HOME; back the two files up to `files/test_backup/` first and copy them back after.
- **Disc station:** `Features.discBlur` (game art behind the disc, BakedBlur); info column on a soft gradient.
- **CaseArt with a 3D case model:** sized to `Models3D.aspect` so `caseModifier` (focus frame) hugs it.
- **Carousel cases3d:** `Models3D.caseModel` only (no cartridge models); else the drawn case3d.

## State after update 214
- **Optical-system pack shells** (PhysicalMedia › CUSTOM_CART branch): always `solidPiece(cartSolid(null, platform, packShell?.aspect ?: cartLook.aspect, custom = true)).mediaMotion(false, …)` — the cartridge rock (rememberCartSpin, ±24° / 5.2 s with Moving cartridges = Spin / Both). 203 had `mediaMotion(packDisc, …)` = disc rotationZ 360° on a cartridge-shaped shell (the "clock hands" bug).
- **N64 shell** (CartShells "n64"): arched top (parabola, 6.5 mm rise, 9 points r 8), grooves at 22.7 / 93.3 (ridges with across = false, n = 1), side step at 56.5 → 57.5 (1.6 mm), guide-slot ledge + edge ridges, label 29.5 / 7 / 57 × 63.5, `labelRadius = 3f` (new CartShell param, default 0.8 — `front()` uses it). Measured off the user's straight-on PAL Super Mario 64 photo.
- **CategoryTileOrder** (data/CategoryTileOrder.kt, prefs "category_tile_order"): free order of tile keys; `sort(tiles) { key }` applied at the end of `buildCategoryTiles` and in `cycleCategory`'s stops; unknown tiles go after their default predecessor. `moveCategoryTile` = PlatformOrder.move (two consoles) + CategoryTileOrder.move; `reorderPlatforms` also moves it when an order is saved. Settings › Categories move / reset clear it. `CategoryReorder.held` setter snapshots CategoryTileOrder + PlatformOrder (new `snapshot` / `restore`) on pick-up; B (key and system back) → `cancelCategoryMove()` restores them.
- **Open from the 214 prompt:** disc centre-layer glitch (not found; needs the MP4), Switch card outline (size matches; needs a photo), category-switch timing (needs the device; PerfDiag), media loading consistency. **Not device-tested.**

## State after update 213
- **Game popup status** (ui/GamePopupStatus.kt): `rememberGamePerf(g, pkg)` → `GamePerfState` (own / inherited from pkg: › plat: › global, `put` = TuneStore.setForKey(game, change(own ?: inherited)) + Tuner.profileChanged, `inherit` = remove; running = Tuner.status.source == g.name), keyed on `TuneStore.edits` (new counter bumped by setGlobal / setForKey / setForKeys). `perfRows` / `perfNote` shared by both window styles. `rememberGameSyncView(g, syncOn)` → `GameSyncView` (SyncStatusStore per game + CloudSync settings + canWatchSaves on IO; never syncs).
- **GameWindow:** `WinPage.PERF`, `LocalWinStatus` (WinStatus: perf, sync, hasFan, onSync, onSyncSetUp, onPerf, onPerfAdvanced, perfFocus), Performance button after Play (`PERF_LABEL`) or a Speed WinIconButton in fill rows / TV strip, `WinStatusLine` under the buttons on every page, `WindowPerformance`. Back from PERF focuses Performance (`fromPerf`). STACK btnRoom 100 dp.
- **Banner:** BannerTab.PERFORMANCE (only with a tuner), Speed BannerRound, `BannerSyncChip` in the status line (replaces console · year there).
- **LauncherScreen:** onSync keeps the popup open; `perfPackage` (app package or emulatorFor), `onPerfAdvanced` = profileForGame, `onSyncSetUp`, `syncOn` (game / console override only). SaveSyncDialog's button reads "Sync this game".
- **Not device-tested** (no handheld connected during 212 / 213).

## State after update 212
- **Edit Home session** (ui/HomeEditSession.kt): `HomeEditSession.begin()` (from `HomeEditState.open()`, which returns if already active) snapshots the live maps + the at-once prefs (hiddenParts, overlayParts, partStyles, partBackingSolid, artPosLooks, art*Case) as `EditSnap`; `push(kind, force)` before every change (same kind within 900 ms coalesced), `undo()`, `dirty()`, `save()` (= `HomeEditState.done()`), `discard()` (snapshot back + `HomeEditState.close()`), `back()`. Unfinished session's prefs kept in "session" prefs `home_edit_open_212`, put back by `recover(context)` at start (LauncherScreen).
- **Keys** (HomeEdit.kt › onKey): A panel (`styling = selected`), B back, Start Done, Y Undo, X `resetSelected`, Select `toggleShown`, L3 Elements, R3 Art position, D-pad `nudge`. Preview: any key / tap ends it.
- **Chrome:** handles only on the selected part (MovableWidget, editablePart — corner hit tests need `wasSelected`); `HomeEditState.bounds` / `chrome` (root px) feed `Modifier.homeEditShield()` on LauncherScreen's root (blocks touches off the parts). Toolbar = HomeEditLegend.kt `HomeEditToolbar` (top when the selected part is low). Panel = `HomeInspector` (Dialog, no dim; side ≥ 840 dp else sheet) with `PartSizeControls` / `PartLookControls` (HomePartStyle.kt; `PartStyleSheet` removed). `HomeElementsSheet`, `HomeLeavePrompt`. Scopes: `scopeOf` (layout ids / MUSIC+SHORTCUTS every layout / the rest shared by the row layouts, `!styleHome`); `resetLayout` uses the same rule.
- **Category reorder:** CategoryScreen grid-level `awaitEachGesture` (hold = pick up + drag, `DragState` key / pos / edge scroll, `DragHandleBadge`); tiles have no long press any more; `onOptions` → `pbState.options` (PlatformOptionsDialog: `onReorder`, `onDesignSlide` null on TILES, Change / Reset picture). Y on the classic screen = options, X does nothing now. PlatformBrowser GridView: same gesture on the grid (consoles only; non-console hold = options), Start picks up without `pbReorder` (pref kept, unused, switch removed from settings).
- **Android media** (ui/AndroidMedia.kt): `Features.androidMedia` (FOLLOW / DISC / CARTRIDGE) + `androidMediaGames` ("key=MODE;…"). `mediaFor` asks `AndroidMedia.disc(platform, gameKey, lookDisc)` first for App / Android (lookDisc = asDisc ‖ mixed All CDs ‖ DiscLook.on); `artForGame(…, gameKey)` (all callers pass it) turns a picked disc into CARTRIDGE_DISC in the Discs-only look; hidden-kind check uses it; `mediaBandFor` takes gameKey. UI: `AndroidMediaChoice` (GameLookPicker, Cases and Carts looks), `AndroidMediaGameChoice` (top of CartShellPicker; menu row "Disc or cartridge…" for Android).
- **Category title** (ui/CategoryTitle.kt): layouts' own labels call `CategoryTitle.Native(partId?)` (20 sites + CategoriesBar when Category name isn't Off and there are games); `fallbackOn(layout)` = setting not Off, no native, `LAYOUT:category` not in hiddenParts. LauncherScreen adds CATEGORY to the overlay parts 600 ms after a layout change when wanted (not on row layouts with Category name Off); shows without a focused game; `categoryTitleFade` (SUBTLE: 1 → 0.6 after 3.2 s). `HomeWidgets.remove` of a CATEGORY overlay also records it in hiddenParts. Setting `Features.categoryTitle` (Settings › Categories › On the home screen).
- **Not device-tested** (no handheld connected during 212). Next: update 213 — game popup Performance panel + per-game sync status (spec in the 16 Oct attachment; GamePerformanceProfile in TunerContent.kt, TuneStore per-game / plat: / global, SyncStatusStore per game).

## State after update 211
- **Daily build = release:** blocks end with `.\gradlew installRelease` (debug and release share the key). Manifest has `<profileable android:shell="true"/>` so gfxinfo / simpleperf work on release; run-as (prefs, files) only on a debug install — ask the user for `.\gradlew installDebug` when a check needs it. Debug on the RP Classic: 34 % janky scrolling vs 0.4 % release.
- **RP Classic:** serial MC94516AQF091001112, 1240 × 1080 landscape (1080 × 1240 panel), density 320 (2×), Android 14, layout Spotlight, card art HERO, category screen ON_B. Not the default home (Quickstep).
- **Spotlight names:** Text placed by a zero-size `layout {}` under the tile (fixed width, 2 lines).
- **GameCard trophy pill:** widthIn(max = card − 12 dp); percentage only at width ≥ 132 dp.
- **SyncCornerNotice** top padding 60 dp.
- **cycleCategory** skips stops whose visibleGames is empty.
- **SwayingModel back:** compose(card, 180) or else compose(card, 0) without cover, mirrored.
- **ArtFiles.name:** LruCache(8192), hex by table.

## State after update 210
- **ShellMaterials** (ui/ShellMaterials.kt): material registry (id, kind OPAQUE / METALLIC / TRANSLUCENT / EDITION, base, highlight, roughness, metallic, transmission, glitter, back, edge, note). Choice colour key "m:<id>" → `CartShells.colourOf` via `ShellMaterials.colourFor`. ShellColour gained highlight / metallic / glitter / backArgb / matte; CartShells.front / back draw them in `finish()` (sheen gradient, seeded glitter dots), back uses backArgb.
- **MediaEditions** (ui/MediaEditions.kt): 105 `Edition` records generated from a Python list (see docs/physical_media/media_editions.json — regenerate both together). `forGame(platform, gameKey)` matches the ROM file name's normalised title (`titleOf`: brackets and "the" dropped, punctuation removed; "x*" = starts with, "#tag" = "(tag)" in the name). `auto()` = the single VERIFIED + auto edition whose regions contain every region the file name states (`regionsOf`); cached per game. Order: CartShellStore choice (game, then console) → `MediaEditions.autoRaw` (in mediaFor's authentic branch and drawn3dPick) → authentic shell. `ownShellOf` / `ownShellColour` (SolidPiece.kt) use the edition's shell / plastic for photo backs.
- **Features.realEditions** (pref real_editions, default true): Settings › Game look › Covers, cases & discs › More look options › Real special editions.
- **CartShellPicker:** "Real editions of this game" cards (EditionCard), plastics by MaterialKind; the old 11 presets still parse (saved choices) but aren't listed.
- **SolidSpec.clear** also for library shells whose colour opacity < 0.85 (clear cartridges); back.alpha = 0.5 + 0.5 × clear.
- **Colours:** ds #45464B, 3ds #CFD0CC, switch2 / switch2_key #C53B3D, drill_dozer brown, warioware_twisted transparent grey + JP translucent white, yoshi_topsy green (opaque).

## State after update 209
- **SolidSpec.clear** (SolidPiece.kt): see-through plastic — edge slices each at alpha 1-(1-clear)^(1/(n+1)), a `hole` layer (the face outline, BlendMode.DstOut) clears them behind the face, back.alpha = clear. SwayingModel passes 0.55 for cards whose meta tint is clear.
- **FlyingSaveIcon:** no shadowElevation with the 3D card style.
- **Releases:** every update's block runs `tools\release\release.ps1 <version> "<notes>"` after the push (version = 0.6.x, one step per update; 209 = 0.6.2). The script bumps versionCode / versionName in app and project_copy build.gradle.kts, builds assembleRelease, commits "Voltcap <version>" and pushes, then `gh release create v<version> --target main --title "Voltcap <version>"` with `Voltcap-<version>.apk`. APK outputs are named by `base.archivesName` = "Voltcap-<versionName>".

## State after update 208
- **FlatArt (RealMedia.kt):** decoded pictures that are one flat colour (7 × 7 grid within 6 levels) are noted by URL and treated as missing: rememberSoftBitmap / rememberSoftLoad skip them, `withoutFlat()` drops them from scan lists (Case3D, case3dModels, MediaInspectView). `FlatArt.version` bumps when one is found.
- **ScreenScraper.physicalMedia:** region order = ManualLinks.regionOrder(game); every kind prefers the box-2D's region (setRank).
- **Case3D:** a scanned automatic back is asked at once unless MemoryBudget.small; case3dModels returns (front, spine, back) and warmCase3d warms backs at 480. CaseDraw face 2 on a matte flap box (cardboard) is the spine bitmap with fold lines; the plastic sides are `plainSide`.
- **DerivedArt (Case3D.kt):** LruCache of pictures made from a source bitmap (fill / jewel / back / spine / avg keys, weak ref to the source) or named ones (hue fronts, plain spines). CaseLook.averageOf samples a grid.
- **HomeScans (PhysicalMedia.kt):** `map` still a state for old readers; `of(key)` / `knownHere(key)` are per-game states — Case3D and CarouselItem use them. CarouselCtx.physicalMedia is a var set each composition (not a remember key). CarouselItem takes `atFocus`, not the focus index.
- **CartShells:** ds / 3ds outline with the cut corner, lab(2.9, 1.6, 27, 29), no ridges; front() band always 0; `labelArt` crops box-shaped art to the MediaCovers window (COVER_OF); cache key f3.
- **realCaseMm:** CaseCatalog (US) for KEEP / CARDCASE shells first; PS3 / PS5 / XboxOne / XboxSeries 171.5.
- **Case only:** AppearanceStore.caseOnly208 (pref case_only_208) set discCase / caseWithMedia off and caseMedia=INSIDE once. LookCaseMedia labels Case only / Case + media / Case + disc half out.
- **Reorder:** CategoryReorder (CategoryScreen.kt) on / held; LauncherScreen.moveCategoryTile (platforms → reorderPlatforms, else viewModel.moveCategory of the group); Start toggles, A holds, D-pad moves while held, B / back puts down / finishes; drag via detectDragGestures. cycleCategory, CategoryWarmup and styleStops sort consoles with PlatformOrder.
- **saveAllArt:** a call while running sets artSaveAgain; the job runs once more (silent) after it ends if not full.
- **PerfDiag (ui/PerfDiag.kt):** debug builds only (FLAG_DEBUGGABLE); FrameMetrics listener on its own thread; tag VoltcapPerf; `mark()` from cycleCategory; softHits / softMisses, DerivedArt.hits / misses.
- **Sync:** CloudSync.autoAddFolders (pref auto_folders_208, only when connected, saves on and folders empty; presetFolders with files) runs at the start of sync(). Report.noFolders changes the summary. DriveAuth.request(context) uses the chosen account (pref account_email); driveAccount (pref drive_email) from DriveClient.accountEmail() (about?fields=user(emailAddress)), shown as CloudSyncState.account. CloudSync.switchAccount clears the old token, folder ids, changes_token, files/cloud_sync (except playtime-v2.json) and drive_only_saves_v1.json, then connect().
- **Git:** update 207 is on GitHub (main = 09fe47d); no release since v0.6.1 (versionName still 0.6.1).

## State after update 207
- **Save all (saveAllArt):** two worker sets on the main thread — ScreenScraper URLs (ArtFiles.isScreenScraper) with screenScraperRepository.allowedThreads workers, the rest with SAVE_LANES (4); ArtFiles.download(url, refused, busy) reports 429/430/431 as busy (not refused); 430 stops ScreenScraper's queue for the run. artSaveDone / the notification update at most every 600 ms.
- **RomScanner:** Switch extras skipped (isSwitchExtra / inSwitchExtraFolder: update, dlc, mods, patch, firmware, keys, atmosphere, contents, romfs, exefs folders; title IDs not ending 000; [UPD]/[DLC]/vNNNNN). New: cci / cxi / 3dsx, folders n3ds / new3ds / nintendo3ds, scd.
- **CartShells n64:** label lab(32.6, 7.5, 50.8, 60.3).
- **CaseCatalog:** NDS / 3DS 135 wide × 122 tall.
- **Photo backs:** SolidSpec(fitBackToFace) — rememberPhotoSolid's back in the photo tone (ShellColour "photo"), drawn over the piece rect masked by the face silhouette (SrcIn). InspectSlab does the same for p.realScan; its slab aspect is CoverAspects.rawOf(photo).
- **mediaRound(p)** is false for UMD consoles (PSP): the UMD photo is a slab everywhere. Disc photos load with TrimToOpaque (TrimMargins.kt) and ContentScale.Crop.
- **Save Vault 3D card (SwayingModel):** ProvidePieceTurn + solidPiece(spec, withCartSpin = false) over Model3DArt at 0°; back = Models3D.compose(card, 180) mirrored; depth SAVE_CARD_DEPTH 0.1.
- **Second dock:** SecondDockShortcuts entries voltcap:saves / whattoplay / stats / search / vsettings / screensaver, handled in LauncherScreen's dockShortcut (open on the main screen).
- **Category switch:** case3dModels / warmCase3d (RealMedia.kt) used by the carousel warm-up and CategoryWarmup; CarouselSwap's k and categorySlide wait two frames before animating (carouselMs + 260 / 340 ms).
- **SolidPiece:** slices every 2 px, max 32.

## State after update 206
- **Save all:** saveAllArt says "Already saving art" when a job runs (not silent); setArtStorage restarts saving when auto-save is on and the size grows (or turns unlimited); saveAllRealMedia cancels a running save and starts again (its URL list now includes every game's scans).
- **mediaFor:** the authentic CartShells shell is the default for every cartridge console (the `Features.authenticCarts` gate is gone). Order: CartShellStore choice → drawn3dPick → customCartFor (Cartridges only / custom source) → authentic shell → MediaShell.
- **Labels:** CartShells.front draws the art with `drawBitmap(art, null, artRect)` (stretched; key `f2|`); RealMedia TemplateMedia, CustomCarts PackCart and PhysicalMedia's label use ContentScale.FillBounds.
- **UMD:** MediaShell.UMD aspect 65f / 64f (Sony 2004 spec).
- **Audit data:** `docs/physical_media/physical_media_verification.json` is generated from CartShells / CaseCatalog plus sources (statuses verified / approximate / disputed / unconfirmed). Disputed sizes were deliberately not changed. When a size is measured, change it in CartShells.kt / CaseCatalog.kt and update its JSON entry.
- **OOM watch:** /data/local/tmp/vcwatch.sh on the Thor logs the Java heap to vcheap.log and dumps vc.hprof above 260 MB (runs about 3 hours from 10:00, 15 Oct).

## State after update 205
- **One piece rule:** typedArtFor(CARTRIDGE_DISC, …, platform, gameKey) returns null when CartShellStore has a choice (the picked shell wins), else the photo (own photo on) / label (shells on). drawnMediaWanted / realScanOf unchanged; MediaInspectView.mediaPiece now uses realScanOf only (the 202 mismatch). Drawn pieces get `drawnLabelScan(physical, platform)` ("Disc label") as label with labelIsScan; CartShellArt / CartShells.front(scan = true) draw it without the console band.
- **Mixed lists:** MediaCovers.used ignores `mixed`; mediaFor uses the authentic shell in mixed lists; case3dAspect and the TV room's pulled-out case too. MixedArt choices still apply.
- **GBC:** CartShells.authenticIdFor(platform, region, gameKey) — "(GB Compatible)" → gbc_dual. Used by mediaFor, drawn3dPick, ownShellOf, the shell picker.
- **Scans at start:** LauncherViewModel.preloadPhysicalMedia() reads the whole "physical_media" prefs into state.physicalMedia (no network). artUrlsToSave only saves scans for `scansSeen` (looked up or shown: CategoryWarmup → HomeScans.seen) and only drawnScans() kinds; ScanReport.saveAll saves every game's.
- **No stand-ins:** HomeScans.known(key); GameSpine draws a neutral strip for a ROM whose scans aren't known; Case3D uses rememberSoftLoad (RealMedia.kt) and SpineCache.WAITING while the real spine loads.
- **Backs:** Case3D makes `defaultCaseBack` (back scan → full-scan cut → PrintedBack with the cover) when no back is passed, the first time its yaw passes 70°; boxPiece prefers "Back of the box"; fitBack(jewel) crops insert flaps (k 1.05–1.3).
- **BoxTexture:** 204's faceWidth cap removed (it cut PS1 backs); findSpine accepts ratio up to 3.4 (N64 / SNES); cache key v4.
- **Sides:** SolidPiece / InspectSlab edge layer = outline (solid layer) filled with the tone (SrcIn) — PhotoTones / photoShellTone read a photo's plastic; slices up to 64. Carousel.alpha is 1 for shown games.
- **CustomCarts:** clear only with ModelFinish.CLEAR (or OFF + cartClear).
- **ScanReport** (data/ArtFiles.kt): per-kind found / none, unknown, failed + reasons from ScreenScraperRepository.lookupOutcome (OUTCOME_* in ScreenScraper.kt); library count (countRealMedia). UI: SettingsDialog RealMediaReport().
- **Arcade cabinets:** HomeLayout.pickable excludes ARCADE; AppearanceStore load maps ARCADE → CLASSIC; presets arc_std / arc_clean removed.

## State after update 204
- **Update check (AppUpdates):** `autoCheckIfDue(c, atStart, resumed)` — start / resume at most every 10 min (`START_MIN_GAP_MS`), on screen every hour (`AUTO_EVERY_MS`), 15 min after a failure (`lastFailed`). Conditional request with the last ETag (`lastRelease` / `etag`, this run only; a 304 reuses the release). `later()` holds that tag 12 h (`laterTag` / `laterAt`). Automatic checks don't set `State.Checking`. UpdatePromptHost passes `resumed = true` on ON_RESUME.
- **Pocket Quest:** `Features.pqBumpers` is key `pq_bumpers2`, default false (R1 / L1 as everywhere). Top bar has 5 tools (`PQ_TOOLS`): Systems, Search, Sort, Quick settings (`actions.quickMenu`), Settings; `pqTopPress` index `tabs + 3` = quick settings.
- **Media for every game:** `mediaFor` never returns null — a console without its own medium gets `GENERIC_CART` (or `genericDiscKey(platform)` as a disc). `isFreeMedia(CARTRIDGE_DISC)` is always true. `typedArtFor(CARTRIDGE_DISC)` ignores scans for `noOwnMedia(platform)` (App, Android, Steam, no MediaShell) — GameCard.kt. Discs for all (`discsForAll`) covers consoles without a shell too; mixed All CDs / All cartridges covers every game.
- **Show as (GameLookPicker):** `LookShowAs` MIX / CARTS / DISCS over `LookDiscGames` (MIX = DISC or CASE, remembered in LookMemory `mixDiscs`; CARTS = CART; DISCS = ALL). `showAsNow`, `pickShowAs`. `stepGameLook` goes Covers → Cases → Mix → Cartridges → Discs → Art; `currentLookName` shows the Show as for Cartridges & discs.
- **Applicable only:** GameLookPicker returns early with a note when `layoutOwnLook` or Pocket Quest plain covers; R1TileRules hides (not greys) the look tiles there, `progressbar` on PQ covers, `herofit` where the layout shows its own art, and Background where the layout draws its own.
- **Photo backs:** `RealMediaPicture(model, round, modifier, platform, gameKey)` uses `rememberPhotoSolid` (SolidPiece.kt: `ownShellOf`, `ownShellBack`) — the console's authentic shell back / depth / colour. InspectSlab uses `ownShellOf` for a real scan (`p.pick == null && p.realScan != null`).
- **Box crop:** BoxCrop cuts back and front to `min(l, w - r)` minus 0.5 % from the fold (cache key `…,2`).
- **Manuals:** `ManualLinks.runAll(download)` / `stopAll` / `bulk` (Bulk: downloading, done, total, found) / `foundCount()`; `LauncherViewModel.findAllManuals(download)` (lookups paced 400 ms, downloads via `ArtFiles.downloadManual`). UI: `ManualsBulkRow()` in SettingsDialog's Scraping card.
- **Category name part:** `CATEGORY_PART_LAYOUTS` (AppearanceStore.kt) get `HomeWidgets.CATEGORY` added once (`categoryPart204`, flag `category_part_204` in "appearance"). The overlay draws `CategoryNamePart` (HomeParts.kt) unless Category name is All categories (then `CategoriesBar()`); start spot `categoryPartSpot(layout)`.
- **First one wide:** `focusedWide` for FIRST_ONLY only when `pinnedFirstOn`; `pickFocus(FIRST_ONLY)` removes CLASSIC from `carouselLayouts` and sets `focusPosition = LEFT`.
- **Overlay restore:** `restoreOverlaysForRunningGame` checks `RunningGames.keys` (was `confirmed`, filled late by `refresh`) and starts with `newSession = false`.
- **Type-check setup (this session):** `/tmp/claude-0/cc` (jars from paths.txt, android.jar, themeshaders.jar from the debug classes.jar, discord_partner_sdk.jar out of the aar), kotlinc 2.2.10 in `/tmp/claude-0/kotlinc`; tools/compile_check/check.sh with `K=/tmp/claude-0/kotlinc`.
- **Mixer order:** `AppMixer.ticket(pkg)` numbers a change when it's asked; `setVolume(..., ticket)` runs one at a time and skips a stale ticket. `MixerNotification.tap` (main thread, in `onReceive`) works out the step, draws it, keeps it in `wanted` until `apply` has set it; `post()` overlays `wanted`. The mixer card's `commit` passes a ticket too.
- **Box scan cuts:** `BoxTexture.faceWidth` caps the back / front cut at the console's real front width (`realCaseMm`) at the scan's height; cache key version 3.

## State after update 203
- **Solid cartridges (SolidPiece.kt):** a view that turns its games hands the turn down with `ProvidePieceTurn(PieceTurn(yawOf, camera))` and doesn't turn the slot itself when the game is a cartridge look.
- **Drawing:** `Modifier.solidPiece(SolidSpec)` (in PhysicalMedia's cartridge branches and RealMediaPicture) draws the piece turned, using GraphicsLayer rotationY:
  - the face is recorded, then drawn as slices through its depth, then the lit face (or the back);
  - all of it goes into one Offscreen buffer with a margin.
- **Flat parts:** discs and ConsoleBadge use `followTurn()` (a flat rotationY).
- **Cartridge spin:** "Moving cartridges" no longer turns in `motionLayer` (`turnCart = false` from mediaMotion); `rememberCartSpin()` adds the spin inside solidPiece.
- **Shared values:** `slabDepth`, `shellTone`, `PLAIN_SHELL` / `CUSTOM_SHELL` / `ANDROID_SHELL` in MediaInspectView are internal now.
- **Not changed:** InspectSlab (the game window) keeps its own drawing.
- **Views wired:** Carousel (CarouselArt slot + CaseWithMedia3D MediaPiece), GameDock, TileHome, GridHome (Lift), HeroCarousel (Flip), StyleGreenCore games carousel.

## State after update 202
- **Looks:**
  - `GameLookPicker.kt` has 4 looks; their choices set the old prefs underneath.
  - `Features.lookMemory` (`look_memory`) remembers each look's choices.
  - Migration flag `look_rework_202` (`migrateGameLook202`, called from AppearanceStore.load).
- **Layouts:**
  - `HomeLayout.GREENCORE`: StyleGreenCore.kt, GreenCoreSphere/Sounds/Settings, prefs `gc_*`.
  - `HomeLayout.POCKET`: StylePocketQuest.kt, PocketQuestScenery/Settings, prefs `pq_*`.
  - Both are dispatched in StyleHomes.kt and have a WinLook in GameWindow.kt.
- **Manuals:** `ManualLinks` keeps the URLs (prefs `manual_urls`, login added on download only); ManualReader uses PdfRenderer.
  - The booklet comes from `CaseInside.bookletRect` / `lid(... booklet)`.
  - Taps go through `openBookletPlace` / `tapBooklet` in MediaInspectView.
- **Updates:** AppUpdates `autoCheckIfDue` / `prompt` / `skip`, prefs file `app_updates`; `UpdatePromptHost` runs in LauncherScreen.
- **Covers:**
  - Plain centred crop everywhere: Case3D `fillCrop`, CoverCases CoverFace `ContentScale.Crop`.
  - The `TrimUniformMargins` Coil transformation (TrimMargins.kt) runs on cover requests.
- **Android cases:** `androidOwnCase()` in CoverCases.
- **Badges and spines:** ConsoleBadge `onCase` hides the badge on banded cases; `BoxTexture.realSpine()`.
- **R1 tiles:** `r1TileFit(id, ctx)` in R1TileRules.kt decides every tile.
- **Edit Home:** `editablePart` items for every home piece; `art_pos_looks` holds the art position per look.
- **Background music:**
  - `MusicBlackout` and `reevaluate()` in BackgroundMusic.kt decide when it may play.
  - ScreenSaver has `onBlackout`; LidPause calls `holdForLid`.
  - Setting `keepMusicScreenOff`.
- **Achievements overlay:** per-part toggles in PerfConfig (`ra_name`, `ra_description`, `ra_totals`…), migration `ra_clean_default_202`; OverlayShell `fitScreen`.
- **Play time:** format 2 in CloudSync (`playtimeV2`: legacy + per-device counters, merged by max); PlaytimeStore `own_json`; a one-time repair per device.
- **Navigation bar:** GestureHint holds AYN's `global hide_nav_bar` while a game from Voltcap runs or Voltcap is the home app.
  - A root-service uninstall guard puts the value back if Voltcap is removed.
  - The second display reads the value only when it starts.

## State after update 201
- **AYN pservice (RootShell):**
  - Commands over 255 characters are cut, with a stray "1" appended; only the first output line comes back.
  - Use `RootShell.execAll(list)` for writes (packed to at most `MAX_COMMAND` 250) and `execJoined(cmd)` for multi-line reads.
  - pservice rewrites top speeds, its performance-mode floors and GPU levels every ~10 s while the screen is on; Tuner re-applies once a second while that happens.
  - `persist.vendor.debug.mode=3` would stop that loop; not set, it needs the user's OK.
- **Tuner:** all three clusters, plus msm_performance `cpu_max_freq` / `cpu_min_freq` per CPU (the old values are saved and put back), and the GPU cap read back. The fan curve and heat guard read the chip's thermal zone.
- **InspectSlab:**
  - The back is drawn in its own offscreen layer, so its SrcIn / SrcAtop blends stay on the back.
  - Backs without a drawn shell back are an opaque silhouette.
  - `CartShells.back()` paints an opaque inside under clear plastic.
- **InspectStick:** catches the stick clicks and the right stick at the game window's view, before focus.
- **LyricsStage:** `statsOn = false` (the panel and its settings are gone); the `onSettings` gear calls SecondScreenHome's `onOpenMusicSettings`, a LauncherScreen jump to "Music page layout".

## State after update 200
- **InspectMotion steps:** `openLid` / `takeOut` / `putBack` / `closeCase`.
  - `tapCase(scope, hasContent, onItem)` and `stickClick(scope, hasContent)` drive them; `toggleCase` stays for opening all at once.
  - `OpenItemPlace.hit` tells a tap on the item from one on the case, using Case3D's open book: the lid is left of the middle, the tray right of it, each scaled by 1/1.92.
  - InspectContent takes taps only while `out` is up.
- **`MediaInspect.stickClick`:** the GameWindow sets it while a piece is out; LauncherScreen's root `onKeyEvent` calls it for THUMBL / THUMBR.
- **Second screen:**
  - `SecondPadRing()` (SecondPad.kt) is the one highlight; targets register `plain` for `padTargetPlain`.
  - `SecondPageSlide` slides the pages.
- **LyricsStage.kt:** `LyricsStage(nowPlaying?, …)` and `LyricsStageSettings()`.
  - Prefs `Features.stage*` (keys `stage_…`).
  - `SecondMusicLayout.STAGE` is the default; `applyStage200()` moves existing users once (flag `stage_default_200`).
- **Retired 3D models:**
  - `drawn3dOn()` / `drawn3dPick()` in PhysicalMedia route ModelFinish REAL / CUSTOM / CLEAR to drawn shells.
  - `InspectKind.MODEL`, `InspectModel` and `CarouselModel` are removed; `Models3D.on()` is false and `caseModel()` returns null (the Save Vault still uses Models3D).
  - "m3d:" choices fall back to the authentic shell (`CartShells.retired`).
- **CartBacks.kt:** generated from the research JSON (`/tmp/claude-0/backs/backs.json`; generator `/tmp/claude-0/gen/genbacks.py`). `CartShells.back(shell, colour, px, neutral)` draws it, mirrored.
  - `Features.cartBackStyle` is AUTO / AUTHENTIC / NEUTRAL; `CartShells.belongsTo(shellId, console)` decides AUTO.
- **CartShellStore:** a ConcurrentHashMap plus a `version` int state, made in `Features.init` (`warmUp`). Never make a snapshot state lazily during drawing.
- **AnimatedArt.StillFirst** wraps the ImageLoader:
  - The decoder keeps each animated picture's first frame at `stillKey(src)`.
  - Requests for .gif / .webp without a placeholder get it as their placeholder.
- **Folder icons:**
  - FolderIcons.kt: `FolderIconImage`, `FolderIconMenu`, `FolderIconPickerHost` (the top screen opens the picker) and `TopScreenFolderPreview`.
  - `SecondGamesPage.onHover`; `Features.secondTopFollows`.
- **RunningGames.foregroundMs:** a game is in front while any of its screens is open; `closedPerUsage` waits for every screen to be destroyed. `launchApp` sends every Android game through the game path.
- **Lights:**
  - VendorAdapter writes `enable` 0/1 on both SN3112 chips.
  - SystemLights holds list values ("1,1" → "0,0").
  - The LauncherScreen timer holds the system light setting off while dark.
- **Tuner:** while a limit is on, the CPU / GPU minimums go to the hardware minimum (AYN's floors are no longer kept); it reads back the caps.
- **Case3D `stretchOrCrop(src, aspect, 0.13)`:** within 13 % the cover is stretched whole, further off it's cropped (`framed`, `fitFace`, no-spec faces).

## State after update 199
- **`InspectMotion.spinOnce(scope)`:** a tap on a non-BOX piece in MediaInspectPiece runs the showcase turn once, from
  facing you. GameWindow's R3 does the same for a non-BOX piece. Only after lift reaches 1; a nudge cancels it.
- **MediaInspectPiece hint room:** a BOX with content keeps 40 dp under it (`roomH`).
- **`jewelFront`:** art with aspect / window outside 0.8–1.25 is centre-cropped to the booklet window. Otherwise it is
  fitted whole as before.
- **GameWindow:**
  - Media(showName): the name's width is limited to `0.97 − 0.46·mediaAspect/1.78` when WinGameMedia is on the picture.
  - Arrange.LIST: the column is 240 dp and scrolls.
  - SHELF details label: "Turn over".
- **StyleDiscStation:** the summary sits in a BoxWithConstraints with weight 1; its line count comes from the height.
- **Carousel `carouselSlotH`:** non-mixed aspect ≥ 1.6 uses a 1.7 multiplier (was 1.25). The unit spacing follows.
- **StyleConsoleStack:** the stop label is a FitText (11→7 sp, 2 lines).

## State after update 198
- **`InspectStick`:** when `out > 0.5`, it uses `nudgeContent` / `releaseContent`.
- **Side by side:** InspectContent cw = min(0.56w, 0.84h·aspect), centred at 0.72w. The case aside uses
  `CASE_ASIDE_SHIFT` 0.25 and `CASE_ASIDE_SHRINK` 0.48.
- **`secondCasePiece(g, state)`:** boxPiece plus the printed back and content. `OpenableCase(piece, modifier)` has its
  own InspectMotion (started, lift 1): tap toggles, drag turns, InspectContent.
- **`SecondHomeGame.inspect: Any?`:** an InspectPiece, built in LauncherScreen when `Features.secondOpenCase`.
  CaseDetailsPage uses OpenableCase when it's set.
- **`Features.inspectOpenAtStart`:** MediaInspectPiece toggles the case once lift reaches 1; OpenableCase does after
  250 ms.

## State after update 197
- **`isWholeCaseScan(f, caseAspect)`:** within 8 % of the case's aspect. A JEWEL face then uses f as it is, and
  CaseLook uses `withoutStrip()`.
- **Choice "m3d:<id>":** `pickOf` gives a MODEL_3D pick (when it's downloaded). `libraryPieces()` lists the cart_ /
  card_ / *_cart_real / *_card_real ids on the device. CartShellArt draws Model3DArt for it.
- **VB shell:** h 73.74, with a FLAP extra as the dust cap.

## State after update 196
- **R1 tile "herofit":** QuickSettings, after the background tile, inside the same block. It uses
  `HeroFitPlan.of` / `put` for `a.homeLayout`.
- **Disabled (toast with the reason) when:** `backgroundStyle != ART`, or CLASSIC with `dockPosition != BOTTOM`.
- **Hold:** the choiceSheet with the HeroFit values.
- **Also added in:** BuiltinShortcuts.R1_TILES and the R1Shortcut icon (AspectRatio).

## State after update 195
- **ui/CartShells.kt:**
  - `CartShell` (mm outline x,y,r triples, label, ridges, screws, colours, extras, contacts, check, note), 67 entries
    from the research;
  - `presets` colours; `authenticId(platform, region)`;
  - a choice is "<shell>~<colour>" or "pack:<CartLook key>~clear"; `parse` / `pickOf` / `split`;
  - MediaPick.platform is "~sh:<choice>|<console>" (`SHELL_PREFIX`); `isCustomPiece()` = CUSTOM_CART or SHELL;
  - `front()` / `back()` bitmaps (LRU 24), the back mirrored;
  - `CartShellArt` composable (a pack choice goes to `PackCart(look, clear)`).
- **CartShellStore:** `Features.cartShellChoices` ("key\tchoice" lines; keys are the game's stableKey or
  console:<platform>), a SnapshotStateMap made in Snapshot.global.
- **mediaFor(platform, mixed, asDisc, gameKey):** a per-game / per-console choice first; then
  `Features.authenticCarts` (non-disc consoles; region from `CaseCatalog.regionOf(gameKey)`). Call sites with a game
  pass `gameKey = g.stableKey`.
- **PhysicalMedia:** SHELL branch. **MediaInspectView:** SLAB depth = shell.depth; InspectSlab draws the shell's back
  bitmap when turned.
- **CartShellPicker** (ShelfDialog): search; Authentic (groups) / Your custom shells; colours; "For every <console>
  game". Opened from GameOptionsDialog `onCartShell` (LauncherUiFlags.cartShellForGame).
- **SecondPad layers:** `LocalPadLayer`; `live()` keeps only the top layer. FolderPopup, the drawer, the picker and
  the dock menu are layer 1.
- **SecondGamesPage `onHighlight(platform, game?)`:** the folder opened, or `SecondPad.focused` "folder-…" / "game-…"
  after 260 ms. LauncherScreen sets the platformFilter and focus.
- **Showcase edge arrows:** "sc-edge-prev" / "sc-edge-next", 30×64 dp, CenterStart / CenterEnd. The top-bar ‹ › is
  no longer passed.
- **Case3D JEWEL:** always `jewelFront(f)`.
- **Not tested on a device yet.**

## State after update 194
- **`ShowcaseTopBar(onStep)`:** a pill with ‹ › (padTarget sc-prev / sc-next). SecondScreenHome passes `latestStep`
  when there is more than one page or the game window is open.
- **Case3D JEWEL face:** an art aspect of 0.86–1.16 goes to `jewelFront(f)` directly; other aspects use the
  template-framed `made`. The PS1 template (0.894) used to crop square scans.

## State after update 193
- **ui/SecondDockShortcuts.kt:**
  - `entries` (id → name), `page(id)` (ACHIEVEMENTS / MUSIC / LIBRARY / MAGNIFIER), `game(id)`, `all()`;
  - `Icon(id, modifier)`: a builtin's icon on an accent plate, else AppIconArt.
- **SecondScreenHome:**
  - `onShortcut: ((String) -> String?)?` (null in CompanionHub, which shows no shortcuts);
  - `launchFromDock` for both docks (a page if visible, else the note, else `onShortcut`);
  - `dockNote` pill at the top for 2.4 s;
  - the picker adds `SecondDockShortcuts.all()`, sorted first under ★.
- **LauncherScreen:**
  - dockApps maps unknown packages to `SecondDockShortcuts.game`;
  - `dockShortcut` handles each id (the second screen's LocalContext as `dockCtx`);
  - `onSecond(intent)` starts on `ScreenPlacement.secondScreen` with `optionsFor(displayId)`.
- **CaseLook:** `seam` (front share of the depth), `edgeStyle` EDGE_KEEP / CARD / JEWEL / CLAM, `catches`.
  - Keep cases: 2 mm / t. Card cases: 0.46 (0.5 when ≥ 18 mm). Switch: 2 / t. Jewel: 2.4 / t. Longbox: 3 / t.
  - Clamshell and cassette: 0.5. Book box: 2.5 / t. Legacy styles have defaults.
- **CaseDraw:**
  - `openingEdge` on the EDGE face: lid band, seam shadow ±gap, lit lip, catch bumps;
  - `topEdge`: the lid band and seam line, or a box's fold crease and tuck slit;
  - the grip recess starts at seam + 0.08.
- **CaseOpenDraw:**
  - `lidFrac` / `lidEdge`: the tray walls run to zTop = hd − lidT;
  - the lid's inside sits at zTop and its outside is offset by lidT along (−sinθ, cosθ);
  - the rim quad is drawn from both sides (`twoSided`).

## State after update 192
- **InspectSlab:** `toImageBitmap()` gives a HARDWARE bitmap, and `getPixel` threw IllegalStateException on the Thor and
  the Classic (the crash buffer showed it at 12:34–12:37). It is now copied to ARGB_8888 first, and the whole
  `sampleShell` call is wrapped in runCatching.

## State after update 191
- **ui/CaseCatalog.kt:**
  - `CaseSpec(platform, region?, shell, w/h/t mm, plastic, opacity, tray, media CaseHeld, mediaWMm, mediaLeft,
    mediaU/V, memCard, gbaSlot, grip, check, note)`;
  - `CaseShell` KEEP / CARDCASE / JEWEL / LONGBOX / CLAMSHELL / CARDBOARD / BOOKBOX / BIGBOX / CASSETTE;
  - `CaseHeld` (named so to avoid Carousel's private CaseMedia);
  - `CaseCatalog.forGame(g)` (LRU 600 keyed by platform, region pref and file) and `regionOf(file)` (the first tag
    naming a region; several → the default);
  - `noRetailCase` returns null and the legacy path is used;
  - C64 `.tap` / `.t64` → cassette.
- **`Features.caseRegion`** (`case_region`, `CaseRegionChoice` AUTO_US default / AUTO_EU / AUTO_JP / US / EU / JP;
  `code`, `fixed`). Settings › Game look › Covers, cases & discs › Case region (StyleChoice after Printed case backs).
- **Case3D:**
  - `spec` remembered on stableKey, region pref and casePlatform;
  - `shape` = `spec.aspect` when known;
  - face = `jewelFront` (booklet window 0.075–0.925 × 0.018–0.982, letterboxed) for JEWEL, else framed or `fitFace`
    (≤ 6 % kept, else cropped);
  - the cover template is dropped when its aspect is off by more than 15 % (not for JEWEL);
  - depth = `spec.depth`;
  - `case3dAspect` uses the spec (clamped 0.55–1.85).
- **`CaseLook.of(spec, style, face)`:** edge / top / back / grip / strip / matte / inner / flap.
  - Keep cases: edge = mix(dark, plastic, 0.5 + 0.4·opacity).
  - Jewel: the tray seen through the plastic; strip = tray colour with alpha F2 (dark) or 88 (light).
  - Cardboard: the front's average colours at its top and right edge.
- **CaseDraw** takes `look` and `flap`:
  - the top becomes a dark opening;
  - dust flaps 0–100°, tuck flap 0–118° with its tongue;
  - the inner tray rises h·0.2 after flap 0.7, with its empty well.
- **CaseInside:**
  - `kindFor(spec, platform, itemRound)`: what lies in the case decides HUB / JEWEL vs SLOT (this fixes the flip);
  - `plastic(spec, platform)`: keep cases mix paper white toward the plastic by 0.25 + 0.75·opacity;
  - `itemRect(..., spec)`: real mm and mediaU/V;
  - `tray` / `lid(..., spec, kind)`; `mediaBed` handles the hub (DISC80 hub ×1.5), the GBA slot, the Switch tabs and
    the legacy leaflet;
  - mediaLeft: hub on the lid, clips on the tray;
  - memCard: recess under a shorter manual on the lid.
- **CaseOpenDraw:** `itemOnLid` quad on the lid's inside (offset 0.18·d along its normal).
- **Callers:**
  - `CaseItem.round` (from `caseItemOf`).
  - `toggleCase(hinged = true)` everywhere (boxes animate `lid` as the flap).
  - InspectContent uses `kindFor` / `itemRect` with the spec; on the lid it starts at hinge − w2. Box start y is
    top − 0.1h.
  - `scanShape` is skipped when a spec exists.
- **Not tested on a device yet.** The Thor was in a game during the work.

## State after update 190
- **ArtDownloadService** (data/; manifest: dataSync FGS + `FOREGROUND_SERVICE_DATA_SYNC`, in project_copy):
  - ref-counted `start(ctx, total)` / `progress` / `stop`;
  - started by scrapeMissingArt (try/finally round the whole job) and by saveAllArt;
  - `Features.artInBackground` (`art_in_background`, true);
  - `GameMode.awaitForArt()` waits only when that's off (art loop, saveAllArt, spines, real covers).
  - `saveAllArt(afterScrape = true)` from the scrape job; it used to return at once.
- **Case3D:** the decorative lines are removed; the PS1 strip stays. KEEP edge face (f == 2) gets a grip recess at
  v 0.36–0.64.
- **CaseOpenDraw:** no outlines.
- **CaseInside.tray:** HUB has 3 arcs (200°/38, 330°/60, 122°/38); HUB and SLOT get a grip strip on the right edge.
  The keep lid's tabs are on the hinge side.
- **InspectSlab:** GraphicsLayer extrusion:
  - `face` recorded, `edge` (SlabLight 0.42–0.64) drawn at n slices far to near;
  - `lit` front, or the `back` silhouette in `shell` (sampled with `toImageBitmap`, `sampleShell`) or `p.tone`.
- **InspectDisc:** the far oval and edge are clipped out of the hole (clipPath Difference).
- **drawDiscUnderside:** sweep lobes (m1, m0, m1, m2 ×2) at view × 0.9 and a doubled rainbow at view × 0.9 + 28.
- **Cases out:** translationX −0.2w, scale 1 − 0.4·out.
- **To check on the Thor:**
  - the notification and background art with Voltcap behind a game;
  - the custom-cartridge spin;
  - the case grip and no lines.
- Cases built from a 3D case model (R1 › 3D in a case look) are pre-rendered pictures and don't open; the drawn cases do.

## State after update 189
- **Opening cases:**
  - `InspectPiece.content` (BOX only) = `contentPieceFor` (mediaPiece CARTRIDGE_DISC, else a generic disc for disc
    consoles).
  - `InspectMotion`: `lid` / `out` / `opened` / `toggleCase(scope, hinged, hasContent)`; `contentTurn`
    (`nudgeContent` / `releaseContent`). `close()` resets the case first.
  - Case3D draws `CaseOpenDraw` (ui/CaseOpen.kt) while `openOf() > 0`:
    - a book fitted by k = 1/(1 + 0.92·open), centre shifted w2/2·open, pitch + 8·open;
    - tray / item / walls / spine / back / lid outside / lid inside, sorted by depth;
    - lid angle 178°·open around (x = −hw, z = +hd).
  - `CaseInside`: Kind HUB / JEWEL / SLOT / BOX, `plastic(platform)`, and `tray` / `lid` / `item` bitmaps (LRU 12).
  - `InspectContent` flies from `itemRect` in the open book (k = 1/1.92) to the slot centre + 0.1w. The case moves
    −0.34w and scales 0.58 with `out`.
  - Tap on the piece, or R3 (GameWindow `onPreviewKeyEvent`), toggles it; `OpenHint` explains.
- **Disc undersides:**
  - `DiscFormat` / `DiscFormats.of(game)` (file read for PS2 / DVD consoles: ext, ISO size, CHD unitBytes /
    logicalBytes, CSO size); `drawDiscUnderside(c, r, format, view)`.
  - InspectDisc uses it with yaw; mini disc k = 80/120.
- **Second screen look:**
  - `Features.secondLook` (SHOWCASE default / CLASSIC).
  - SecondScreenHome: `ShowcaseBackdrop`, `pageBody` lambda shared by both looks, `ShowcaseTopBar` / `ShowcaseDots` /
    `ShowcaseDock` (ui/SecondShowcase.kt); `SHOWCASE_TOP` 52 dp / `SHOWCASE_BOTTOM` 78 dp.
  - `SecondHomeGame.logoUrl`; `avatarUrl` / `raPoints` params.
- **Dim:**
  - `secondDimLevel` is now `second_dim_level2` (30 / 45 / 60, default 45) and the backlight 0.12.
  - `SecondDim.artGrey` (ValueAnimator) is read by `Modifier.secondDimArt()`: a RenderEffect colour matrix on
    API 31+, darkening only below. Used on the showcase art and logo.
- **Stick lights:** LauncherScreen registers SCREEN_OFF / ON while composed. Off: `apply(on = false)`; on: the
  latest colour and level (`lightsNow`), on a thread.
- Not yet tried on a device.

## State after update 188
- **SecondDim** (ui/SecondDim.kt): plain Views, because Compose is paused while a game is in front and the
  Presentation stays up.
  - SecondScreen's Presentation content is a FrameLayout (ComposeView + a black `dimLayer`).
  - `SecondDim.attach(window, layer)` / `detach`. `poke()` comes from MainActivity key/touch/generic motion, onResume,
    and the Presentation's `dispatchTouchEvent`. While dimmed, the whole first gesture is swallowed and wakes it.
  - After `secondDimAfter` s: the layer fades to `secondDimLevel` %; with `secondDimBacklight`,
    `window.screenBrightness` = 0.02 (per-display override, to check on the Thor).
- **AnimatedArt.gates()** walks the weak set with hasNext/next (toList → `first()` crashed with
  NoSuchElementException after a GC).
- **BatterySaver.away** = `saverAway && notInFront && gameRunning`. `gameRunning` is set by GameMode
  enter/exit/userIsHere. On the Thor, display 4's launcher3 SecondaryDisplayLauncher becomes the global top-resumed
  activity after any app there closes.
- **Case3D:**
  - `shape` = the front bitmap's own aspect (clamped 0.8–1.25× of `aspect`) unless it's framed in a template.
  - `fitBack` stretches a back within ±20 %, else crops.
  - `fill` param (inspect passes 0.97).
  - `CASE_JEWEL_BLACK` (PS1): a black hinge strip at u 0–0.062 and dark edges.
- **Inspect size:** `min(slot.w × 0.98, slot.h × 0.95 × aspect)`.
- **RomLibraryPrefetch:** started in MainActivity.onCreate (once per process); `loadRomCache` = `take { romCacheFile }`.
  Logs "Voltcap library": N games read in X ms, and shown X ms after start.
- **Settings:**
  - `jumpAnchor`'s flash fades in a `rememberCoroutineScope` (it used to be cancelled when `pending` cleared).
  - Intro and popup fit `screenSizeDp().height × 0.92` with a weighted scroll area.
  - FoldGroups `second_dim` (Screens) and `dev_saver` (ShelfCard).
- Thor check of 187: the intro was too tall (fixed); "away" dropped home frames from 411 to 12 per 5 s with an app on
  the second display.

## State after update 187
- **BatterySaver** (data/BatterySaver.kt): `low` (mode AUTO: not plugged and level ≤ `saverLowAt`, or `systemSaver` with
  `saverFollowSystem`; ALWAYS; OFF), `away` (`saverAway` && `notInFront`), `resting` = either.
  - `notInFront`: `MainActivity.onTopResumedActivityChanged(false)` → `onFront(false)` → set after 3 s; true → cleared at
    once. `poke()` (MainActivity key/touch, the Presentation's pointer watcher) clears it and, while not top, re-arms 20 s.
    `onStop` resets it.
  - Level/plug come from `BatterySaverDriver()` (ui/BatterySaverUi.kt, in LauncherScreen's AmbientEffects; its own
    ACTION_BATTERY_CHANGED receiver while resumed); `ensure()` registers ACTION_POWER_SAVE_MODE_CHANGED once.
  - `sync()` → `AnimatedArt.setResting` (a separate `resting` flag in `Gate.may()`).
- **Where it bites:**
  - `rememberIsActive()` is false while `away`, only when `LocalLifecycleOwner` is MainActivity (home + its
    Presentation; the companion / mixer activities are unaffected).
  - `low`: `LocalSceneCovered` (LauncherScreen's scene provider and SecondScreen) → still frame (music visualizer keeps
    running while music plays); `HomeIdle.still` (= deep || low) and `HomeIdle.calm` (= idle || low) replace the old
    `!HomeIdle.deep` / `!HomeIdle.idle` checks in the effect files; video previews' LaunchedEffect keyed on `resting`.
- **Intro:** prefs "battery_saver" `intro_seen`; `BatterySaverIntroDialog` 7 s after start when no other dialog,
  What's new or permissions prompt is up. "Settings" jumps to Device › `dev_saver`.
- **Prefs:** `saver_mode`, `saver_low_at` (20), `saver_follow_system`, `saver_away`, `carousel_stage_game_color`,
  `box_flow_tilt`, `ribbon_glow`, `classic_top_art` (all default on / 20).
- **Carousel:** the stage accent = `rememberArtPalette(focused cover).accent`, animated 700 ms. Box flow sway:
  `Carousel.speedOf` (= glide velocity, set by SideEffect) → `flowSway(near)` added to the middle box's turnY (±10°).
- **Pocket:** ribbon glow canvas under the icons (`rememberFocusPulse`); classic's `PocketTopBrowse` replaces the
  clock/calendar while `PocketNav.page != HOME`.
- Not yet tried on a device.

## State after update 186
- **ScanCase:** shape is `CoverAspects.rawOf(scan)` (noted in its `onSuccess`, minus the PS1 band), clamped to
  0.75–1.33× the template's.
- **Inspect BOX:** aspect is the front scan's (not PS1). Size is `min(slot.w × 0.92, slot.h × 0.88 × aspect)`; no more
  `realCaseScale`.
- **Case3D:**
  - `caseStyleOf(platform)` gives `CASE_JEWEL` / `CASE_CARD` / `CASE_KEEP` → `CaseDraw.draw(style)`.
  - Front details are drawn with `lineOn(u, v…)` on the projected quad; the edge colours follow the style.
- **R1:**
  - `background` tile cycles ART + videos → ART → ANIMATED → BLACK; hold lists all six.
  - Undo can be swiped away or closed with ✕, shows 6 s, and its label is the tile's first line.
- **Details:** `details_asked` prefs back off silent runs for 14 days; nothing is marked while offline
  (`deviceOnline()`).

## State after update 185
- **Carousel:** `Features.carouselSpread` (default true) gates `Carousel.spread`. R1 Carousel hold has gap choices.
- **ViewModel:**
  - `categoryFocus` (category key → stableKey). `setPlatformFilter` / `setSpecialFilter` remember the game and restore
    it.
  - Continue playing's entry is dropped at `GameMode.enter`.
- **R1Undo (R1Undo.kt):**
  - `r1Undoable(label, appearance, scope, run)` takes `Features.snapshot()` and the appearance before and 350 ms
    after, then offers the difference.
  - The bar is at the top of QuickSettingsCard (8 s).
  - Wraps tile taps, hold choices and `LayoutListPopup`.
- **BackgroundWorkDialog (BackgroundWork.kt):** opened by `StatusBar(onProgressClick)` from LauncherScreen's
  `showWork`. Stops call `cancelScrape`, `cancelMetadataScrape` and `cancelArtSave`.
- **Second screen Games page:**
  - `secondFolderLook` is back on its old key (FOLDERS default).
  - `Features.secondGamesLook` (TILES / COVERS / WIDE / LIST) is used in `FolderPopup`; `wideOf` comes from
    LauncherScreen.
- **After install:** the PowerShell block runs `adb shell am start -a MAIN -c HOME` on each device.

## State after update 184
- **Pocket:** `pocketUsesLook(c.type)` (not Cover / wide / screenshot) and `PocketLookArt` → `ShelfGameArt` in the frames
  (classic `PocketGameArt` with Game picture = Cover, ribbon icons). `layoutOwnLook` no longer lists the Pocket layouts.
- **Carousel:** `placeInto` uses `g0 = gap / spread` near the middle and `g` (with the spread) only for the far steps
  (TUCKED, Spine wall, Box flow). The floor pool is a radial glow scaled flat.
- **Pocket classic top bar:** name and clock use `screenX`.

## State after update 183
- **Art vs details:** `scrapeMissingMetadata` lanes wait (`silent` only) while `scrapeJob` is active. Both use
  ScreenScraper's single slot; the art waited behind details ("Getting art 1/215", seen on the Thor).
- **Carousel:**
  - `Carousel.spread` multiplies `gap`. It's set from GameCarousel: packed views `16 / n` (1–2.2), others `8 / n`
    (1–1.5).
  - `bigW` keeps room only for the lifted top (`/(1 + lift)`); the first term is `0.9`.
  - Stage: tinted (accent → black), spotlight, floor pool, perspective lines, horizon glow and edge vignette.
- **Endless:** `loopRow` pref key is `loop_row3` (default true, on again once).
- **3D cases:** `caseModels3d` has a new key (`case_models_3d2`); R1 look3d is disabled in case looks.
- **R1:** `LOOK_SUB_TILES` are disabled where `layoutOwnLook != null`; `LayoutListPopup` height follows the screen.
- **Pocket:**
  - `statusOn` is false for the Pocket layouts.
  - Calendar cell 17, digits 6.5; Achievements 5.3 on one line.
  - Responsive width up to `POCKET_H × 2.25`.
- **Thor test method (release build):** layout via R1 › hold Layout. `screencap -d 4630946482288158084` for the second
  screen, then pull and stage.

## State after update 182
- **LaunchThrow (cart / disc):**
  - Phases: lift (0–150), glide upright (150–500), push into the slot (560–inEnd), click.
  - `rotationZ` is only a small lean. A cartridge stops half way in; a disc goes all the way in.
  - Case scene: the disc is carried down, no spin.
- **CrossTrophies:**
  - `TrophyFilter.MISSABLE` uses `RaAchievementDetail.isMissable`.
  - `commentsFor` with `CrossComments` (`state.raComments`); `onLoadComments` is `viewModel.loadAchievementComments`.
  - `SpecialFilter.TROPHIES` label is "Achievements".
- **QuickSettings:**
  - `carouselApplies(layout)` greys out the Carousel tile; Carousel off is first in its hold list.
  - `R1ChoicePopup` scrolls (`heightIn` 86 % of the screen).
  - Endless tile is greyed out where `!endlessApplies` (Hero tiles); `loopRowOn` respects it.
- **Carousel:**
  - Edge `side` = `ceil(12 × screenWidthDp / 600)` (× 3 for spines aside).
  - `reach` is × 3 for Spine wall and Box flow always.
  - Box flow `sc` is constant in the stacks.
- **Pocket:**
  - `PocketScope` has `fullH` / `cy`; the design is offset by `cy` in `PocketScreen`, and `atFull` reaches the whole
    screen.
  - `PocketScreen(designH)` adds `POCKET_STRIP_H` (28) when `Features.pocketStrip`.
  - `pocketPixel` has a new key (`pocket_pixel2`, default false); `ribbonIcon` too (`ribbon_icon2`, 44, 24..56).
  - `PocketSpot` holds the ids (tools 4–8, dock 10+, music 20–22).
  - New helpers: `pocketTools`, `pocketToolX`, `PocketToolButton`, `PocketHomeStrip`, `pocketStripSpots`,
    `pocketShared`, `pocketRowStep`.
  - Classic keys use rows; the ribbon uses `PocketNav.ribbonSpot` (−1 = the row).
  - Icons are 16 × 16 grids (`tools/…`: generated in the session from `icons182/gen.py`).
- **SecondPad:**
  - Targets can have `move` (`padTarget(id, onMove)`).
  - `SecondPad.scroller` (`PadScroller` for LazyGrids) scrolls a row when ▲ ▼ find nothing.
  - `padTargetPlain` draws no ring.
- **Second screen:** the Wall page's chips and `wall-view` are pad targets; `secondFolderLook` has a new key
  (`second_folder_look2`, TILES); the look chips are on the Games page.
- **TwinBackPill(name, layout):** styled per layout.
- **GameMode:** `enteredAt`, and `userIsHere()` is called from `MainActivity.dispatchKeyEvent` / `dispatchTouchEvent`
  and on focus after 4 s.
- **HTTP:** `callTimeout` on the SGDB (60), ScreenScraper (90), ArtFiles (120) and IGDB / RAWG / TGDB (60) clients.
- **CloudSync launcher data:**
  - playtime / recent go through `normalized()` (`fileKey`: `romf:<console>/<file>`, console from
    `RomScanner.platformForFolderName` up the path; old path keys are summed / maxed, and a new key beats old ones);
  - `localized()` maps them back to this device's keys (`CloudSync.libraryKeys` from the view model).
- **Open:** Real cases on the Thor (scans not showing; 3D case models crop the cover; the user wants real case shapes).
  Diagnose on the Thor when it's free.

## State after update 181
- **Pocket layouts:** `HomeLayout.POCKET_CLASSIC` / `POCKET_RIBBON` (`isTwin`, new `isPocket`).
  - `PocketShared.kt`: `PocketScope` (256 × 192 dots, `u` dp per dot, `cx` = centring offset; `at` is
    design-relative, `atScreen` is screen-relative), `PocketScreen`, pixel icons as string grids (`PocketIcons`),
    `pocketBrackets` / `pocketOutline` / `pocketPinstripes`, `PocketAccent`, `PocketBackdrop`.
  - `PocketClassic.kt`: `PocketNav` (page / spot / cursor / ribbon state; `signature()` goes into the ▼ snapshot),
    `PocketClassicTop` / `PocketClassicBottom`, `pocketClassicKey`. Panel content uses `at(x - cx, …)`.
  - `PocketRibbon.kt`: `PocketRibbonTop` (`PocketPhoto` file), `PocketRibbonBottom`, `ribbonMove` / `ribbonOpen` /
    `ribbonStart`, `pocketRibbonKey`. Gesture lambdas read `rememberUpdatedState(c)` (`live`), never a stale ctx.
  - `PocketSettings.kt`: the Layout options of both.
  - TwinHost takes `topH` / `bottomH` / `minW` / `stacked`. `twinOnSecond` is false for Pocket layouts when
    `pocketArrange != AUTO`. `styleHomeKey` gives them the keys first.
  - Sounds: `SoundStyle.POCKET` (synth `pocket()` / `square()`), used while `isPocket && pocketSounds`.
  - Not tested on a device yet: check the geometry on the Retroid (one screen) and the Thor (two screens).
- **Animated art:**
  - `AnimatedArt.SourceTag` (interceptor, after `SharedArtInterceptor`, before `SavedArtInterceptor`) writes
    `PARAM_SRC`.
  - The decoder wraps every AnimatedImageDrawable in `AnimatedArt.Gate` (DrawableWrapper + Animatable). It plays only
    when started, not moving, not sleeping, and (`focusOnly`) its src is in `setFocus(urls)`.
  - LauncherScreen feeds the focused game's art and override URLs. `Features.animatedFocusOnly` has a new key,
    `animated_focus_only2`, default true.

## State after update 180
- **TwinScreens:**
  - `fullTop` is set by TwinHost (`twinOnSecond && Features.twinFullTop`, default true). With it, StyleBackLayer draws
    `TwinDeskBackdrop` / `TwinIconsBackdrop` full screen, `styleOwnsBackground` is true, LauncherScreen's
    `sceneCovered` is true, and the tops skip their own rounded sheet.
  - `dashboard`: SecondScreenContent skips the twin bottom while it's true. `TwinBackPill` is on the second screen
    (padTarget "twin-back"). It's reset on layout change.
- **TwinDesk top:** `twinDeskClock` / `twinCalendar` / `twinDeskArt` / `twinDeskGame` (count ≤ 1 → centred, bigger).
  `DeskPicture` (`c.wide` in a white photo frame). The bar has a 4th round "Second screen" only while `twinOnSecond`.
- **TwinIcons:** `twinTools` adds a "Second screen" HomeShortcut while `twinOnSecond`.
- Next (asked 13 Oct): two new layouts in the style of the 2004 two-screen handheld menu (clock + calendar top;
  card / two buttons / pak / toolbar bottom) and of the 2008 one (photo top; ribbon of square icons, speech-bubble
  title, START tab, scroll bar). Original names and art only.

## State after update 179
- **HomeEdit:**
  - `editablePart` (layout parts, `liveShift` dp): the size handle compensates `liveShift` so the top right stays put;
    the stretch handles compensate so the left / top stays put (centre-origin `graphicsLayer` scale kept, so saved
    layouts don't jump).
  - `MovableWidget`: `keepCorner(rx, ry, keepRight)` rewrites the `live` fraction via `SafeZone.place` /
    `fractionOf`. `StretchHandle` has a new `onStretch` callback.
- **PerfOverlayService / OverlayShell:** `onResizeStart(right, bottom)` → `ResizeAnchor` (window rect at the start).
  `applyCorner` places the window with the opposite corner fixed while anchored. On release, `saveFreePlace` stores the
  fraction. Only for free-placed overlays.
- **LauncherScreen:** at the top of `onKeyEvent`, a fresh ▼ snapshots focus / StyleNav / HomeReach / tileBarFocus /
  miniFocus / quickJump / showDetails / category / GamePopup. If nothing changed 160 ms later, `SecondPad.enter()`.
- **Features:** `twinGap` (0–32, default 8) and `twinDeskGap` (0–24, default 7). `TWIN_GAPS` / `gapLabel` live in
  TwinIcons.kt. R1 tile `twingap`.
- **Layout Maker v3:** side handles, sizes capped at the area, and text size follows the box height (Words follow the box).

## State after update 178
- **CustomLayouts:**
  - `PRESETS` (id → layout.json text, ids `preset-…`) are loaded and cached like the example.
  - `list()` = example + presets + imported; `builtIn(id)`.
  - `writeLayout(context, id, out)` zips the layout.json plus the imported layout's pictures. `writeExample` calls it.
  - Settings chip "Save this layout" saves the picked layout.
- **Layout Maker artifact** https://claude.ai/artifact/WT7meYarQghnr352DcxSZF:
  - Source `scratchpad/maker/layout-maker.html` (this session); capability `downloads`; JSZip 3.10.1 from cdnjs.
  - It mirrors the parse defaults and the motion / games maths of `StyleCustom.kt` — keep them in step if the format
    changes.
  - Its presets match `CustomLayouts.PRESETS` + `EXAMPLE_JSON`.
- Open from the user (13 Oct): "not only size but to the side as well but not too huge" — meaning unclear, asked.

## State after update 177
- **ANR (Thor, 13 Oct):**
  - Seen as: RenderThread in `GrTextBlobRedrawCoordinator` / `GrTextBlob::Key::operator==`, main thread blocked in
    `TextureView.destroyHardwareLayer`.
  - Fix: every `graphicsLayer` with rotationX/Y now ends with `flatTextFor3d()` (Offscreen).
  - Keep doing this for any new 3D turn: grep `rotationY\s*=` without `flatTextFor3d`. The Carousel's text-free band
    Canvas is left as it was.
- **SecondPad (ui/SecondPad.kt):**
  - `enter()` / `leave()` / `key()`; spatial nav over targets registered by `Modifier.padTarget(id) { … }`, which only
    registers inside `LocalOnSecondScreen` (provided in SecondScreenHost).
  - Entered from `reach(false)` in LauncherScreen when HomeReach has nothing below, and from `HomeReach.key` past the
    last zone.
  - Keys go through `SecondPad.key` at the top of the home `onKeyEvent`.
  - `SecondPadHint` is drawn over the second screen.
- **RomScanner:**
  - Dedupe by canonical lowercase path.
  - Same-folder same-name formats are reduced by `formatRank`.
  - Disc groups are keyed by folder (the grandparent when the parent has a disc marker) + title, not by extension.
  - Without `Features.autoM3u` (default off), a set is one game (first disc) and goes into `ScanResult.missingM3u`.
  - `ScanResult.duplicates`; `writeM3u(set)`.
  - `LibraryCheck` state is set in `scanAllFolders`; `LauncherViewModel.makeMissingM3u()`; `LibraryCheckCard` in
    LibraryDialog.
- **Looks:** `layoutOwnLook(layout)` (GameLookPicker.kt). The R1 `gamelook` tile gets `disabled` (new R1Tile field →
  dimmed + toast). `GameLookPicker` chips use `enabled = lookOk`.
- **FolderArt (data/FolderArt.kt):**
  - Storage: `files/folder_art/pack` and `files/folder_art/mine`.
  - `find` order: mine → pack index (names through `RomScanner.platformForFolderName`, iiSU suffixes ranked) →
    `_default`.
  - `importZip` / `importFolder` (`Voltcap/Folders`), `setMine` / `clearMine`; `version` state; `platforms` is set by
    a LauncherScreen SideEffect.
  - Features: `secondFolderLook` (FOLDERS / TILES / CARDS / LOGOS), `secondFolderLogo`.
  - SecondGamesPage: `ConsoleTile` and the folder front picture. Settings: `SecondGamesSettings` (FoldGroup
    "Second screen Games page").
- Next (asked for): drag-and-drop layout maker (web) + more built-in presets; "not only size but to the side as
  well, not too huge" — unclear, asked.

## State after update 176
- **Custom layout:** `HomeLayout.CUSTOM` (style home, last in `pickable`). `Features.customLayout` (StringPref, "" =
  built-in example).
- **data/CustomLayouts.kt:**
  - Model: `CustomScene` / `SceneBackground` / `ScenePart` / `SceneLoop` / `SceneEnter`.
  - `parse` uses org.json and throws readable messages; `format` 1.
  - Storage: `files/layouts/<slug>/`.
  - Import: `import(uri)` (zip or bare json), `importFolder()` (`Voltcap/Layouts`, mkdirs) and `importDir`. Zips are
    flattened to base names, only `layout.json` and png/jpg/jpeg/webp/gif, 80 files / 48 MB.
  - `writeExample` and `EXAMPLE_JSON` (Neon drift); `cache` per id.
- **ui/StyleCustom.kt:**
  - `StyleCustom(c)`: parts placed in a BoxWithConstraints by share; `CustomBackLayer` from `StyleBackLayer`;
    `customOwnsBackground` in `styleOwnsBackground`.
  - `customLayoutKey` (column: ▲ ▼ step, ▶ bar; row / arc: ▼ bar, ▲ reach).
  - `CustomSceneNow` (direction, buttons, version, scene).
  - Motion: `sceneLoop` (infinite transition), `sceneEnter` (Animatable keyed on the game for `on: move`).
  - `SceneGames`: spring on focus; anchored focused card grows `focusSize / cardSize`; arc offset and tilt;
    fadeEdges; only ±visible cards composed.
  - Colours: `sceneColor` takes `#hex`, accent, text, white, black, transparent.
- **StyleHomes.kt zone keys:** ◀ from the first button leaves the BAR zone in a column custom layout.
- **ui/CustomLayoutSettings.kt:** list / pick / delete, import file / folder, save the example, the in-app guide.
  Wired in `StyleHomeSettings`.
- Previews: `PresetPreview.custom()`, `Pictogram.HERO`, `layoutBlurb`.
- Docs: `docs\CUSTOM_LAYOUTS.md` and `docs\layouts\` (neon-drift, side-list, with .vclayout zips).
- Not yet checked on a device (no build here). Possible next steps: an Edit Home drag editor that writes
  layout.json, and per-layout fonts from the zip.

## State after update 175
- **R1 Carousel tile (QuickSettings.kt):** tap = on if off, else `Features.carouselView` next ordinal (wraps); a `cases` view
  also sets `carouselReflect` + `carouselStage`. Hold list ends with `R1Choice("Carousel off")`.
- **Folder games (RomScanner.kt):** `isFolderGame(dir)` (`PS3_GAME` dir or `PS3_DISC.SFB`) in `onEnter` → added to
  `folderGames`, not walked; Game with `romPath` = folder, platform `folderGamePlatform` (PS3), name `folderGameTitle`
  (PARAM.SFO via `SaveCapsules.sfoTitle`, else folder name minus .iso and `[A-Z]{4}\d{5}` serial). `notGames` extension set
  skipped in the walk (no "md": Genesis).
- **Launch (GameLauncher.kt):** `FOLDER_GAME_EXTRAS` (`aenu.aps3e` → `game_dir`); a directory `romPath` puts the plain
  path in that extra instead of `applyRom`.
- **Scraping (LauncherViewModel.kt):** lanes via `Semaphore` + `coroutineScope { launch }` on the main dispatcher (state
  maps touched only between suspensions): `ART_LANES` 4 (scrapeMissingArt), `DETAIL_LANES` 3 + `DETAIL_LANE_PAUSE_MS` 250
  (scrapeMissingMetadata), `MEDIA_LANES` 3 (scrapeEverything scans/shots/clips; no per-game delay). scrapeEverything runs
  art + details together, progress = sum.
- **ScreenScraper.kt:** `throttled` = `slots` Semaphore (permits = `threads`) + `gate` Mutex spacing starts by
  `MIN_GAP_MS / threads` (≥ 400 ms); one thread still counts the gap from the end of the answer. `learnThreads` reads
  `response.ssuser.maxthreads` from each gameInfo answer (capped at 4).
- Not yet checked on a device: Thor PS3 list + aPS3e `game_dir` launch; scraping speed on a real library.
- Open: custom animated layouts (user upload + tutorial) — waiting on the format choice.

## State after update 174
- **Endless, each game once:** `drawnOnce(off, n, before)` (EndlessRow.kt) in GameCarousel's draw loop, CrossList,
  StyleCircuit, AnchoredStrip; lazy rows `rememberLoopRow` and TV room lists loop only with `> LAZY_LOOP_MIN` (8) games.
- **Tile look:** GridHome `tiles` also `COVER && carouselTiles()`; Cross list square art (`w × 0.58`); TV room grid
  square cells (`cellAspect`) drawing the cover; GameCarousel `caseLook` false with tiles (no 3D cases).
- **Case stacks bar focus:** `ArchRound` white 3 dp ring + 1.12 grow when on.
- **Case + media in carousels:** slot `+ room` (right only), `CaseWithMedia3D` shifted left by half the room; unit adds
  `room`.
- **D-pad pass (173 on the Classic):** see SUMMARY 174. Layout picker: hold R1 › Layout; tapping the tile cycles. R1 tile
  positions move with the look (Tile look: Layout at (852, 718) px, Player at (1012, 715)).
- The user's music player had a free spot (Edit Home) — reset by a stray tap during the pass; told them.

## State after update 173
- **Media matched to the case:** `CaseWithMedia3D` (Carousel.kt): `caseReal` (originalCases, not case-for-all, a front
  scan) → `BoxTexture.media(scans)` as `RealMediaPicture`, else `PhysicalMedia` with the "Disc label" scan / cover / app
  icon. `CaseArtBody` (RealMedia.kt) uses `BoxTexture.mediaOfFront(scan)` (new, ignores `mediaShells`) when the case is real.
- **App icons:** `coverOrAppIcon(g, cover)` (AppIcons.kt) used in GridHome, HeroCarousel, TileHome, StyleCaseStacks,
  ShelfHome (CoverCase), StyleArt, CarouselArt, CaseWithMedia3D; `Case3D` `plainFront(title, hue, icon)` framed in the
  template when there's no cover. `CategoryTile.appIcon` → `AppIconArt` on coverless tiles.
- **Carousel height:** `bigW` capped by `0.97 / (1 + 2 × lift)` (lift: reflection 0.08, Box flow 0.04).
- **Endless:** Timeline needs ≥ 2 rows (`rows.size >= 2`); GridHome `drawLoop = looping && games.size > cols` for the
  drawing (key wrap unchanged).
- `PrintedBack.title` printed when there's no description and no screenshots.
- Layout picker: hold R1 › Layout for the list (tapping the tile cycles).

## State after update 172
- `CaseWithMedia3D` (Carousel.kt): `realScanOf(CARTRIDGE_DISC, label)` → `RealMediaPicture` (drawn shells off), else
  `PhysicalMedia` — as the flat cards do.

## State after update 171
- **Box-scan covers:** `isBoxFront(cov, artAspect)` (CoverCases.kt): within 4.5 % of `frontAspect`, not within 2 % of 2:3,
  never own cases → no frame (`CoverFace` window = whole front, `caseFitFor` null; `Case3D` face `cropTo`). Shape from
  `CoverAspects.rawOf(url)` (unclamped, noted with `note`).
- **Tile look:** `styleAspect(COVER)` = 1 when `carouselTiles()` (all style layouts' grids and panels).
- **Carousel case media:** `mediaRoom` animated, full only for `i == focusIdx`; `CaseWithMedia3D(show)`; the carousel
  unit adds `2 × room` for the focused game.
- **Own 3D cases:** `OWN_CASE_DEPTH` 0.05 (Case3D) when no spine scan.

## State after update 170
- **Printed backs:** `tools/media_templates/backs_build.py` (pack → `tpl_back_<id>.webp` + `backs.json`: the
  see-through screenshot windows not touching the edge, overlapping ones dropped; the biggest plain band of rows, also
  with a side strip left out; over art or printed, ink) and `gen_backs_kt.py` → `ui/MediaBacks.kt` (`BackTpl`,
  `MediaBacks.of`). `backs_plan.json`: which pack file each console uses (paths under the pack's root).
- `PrintedBacks.kt`: `PrintedBack` spec (platform, art, shots, text, aspect), `PrintedBacks.compose` (480 wide in the
  case's shape: art small→big darkened, template stretched, windows filled, band: 2 shots + StaticLayout text, dark
  panel over art; LruCache 6), `rememberPrintedBack`. `inspectPieceFor` sets `InspectPiece.back` (now `var`) to
  `printedBackFor(g, state, aspect)` for BOX pieces without a back scan when `Features.printedBacks` (true). `Case3D`
  takes a `PrintedBack` or a picture as `back`.
- Not yet: the 3D-model cases (shape library) keep their own backs; the flat looks have no back.

## State after update 169
- **Cartridge templates:** `tpl_cart_{gb,gbc,gbc_black,gba,gamegear,neogeopocket,pokemonmini,psp,psvita,wonderswan}`
  re-made by `tools/media_templates/tplfix.py` (cv2 Telea inpaint of the marks, ES-DE logo composited). `RealMedia
  .TemplateMedia` `useLabelFrame = false` (the `tpl_cartl_*` files stay but are unused there); `MediaInspectView
  .slabAspect` uses `tpl.cart ?: tpl.label`.
- **Case fit:** `CaseFit` / `CoverTpl.fitTo(coverAspect)` / `caseFitFor(cov, art)` / `frontAspectFor(art)` /
  `FittedFrame` / `fittedFrameBitmap` (CoverCases.kt): the frame cut in three along one axis, only the window's slice
  stretched (a window reaching both sides changes height, one reaching top and bottom width). Shape from
  `CoverAspects` (noted in `CoverFace` onSuccess too); unknown → 2:3 for own cases, none for consoles. Prefs
  `Features.ownCaseFit` (true), `allCaseFit` (false). `CoverTpl.isOwn()` in MediaCovers.kt. Used in `CoverCase`,
  `shelfArtAspect`, `CaseArtBody` (case + media), `Case3D` + `case3dAspect` (own cases at 2:3).
- **3D case fronts:** `Case3D` `caseTpl` = own case, or `MediaCovers.of(platform)` with `coverTemplates` (not with
  spine/case-for-all, not over a scanned front) → `framed(front, band, win)`.
- **Pack progress:** `Packs.Progress` has `bytes` / `size` / `fraction`; `fetch(onBytes)` counts while copying (stop
  mid-piece deletes the part); `Packs.upToDate`. `PacksProgressPill` (PacksUi.kt) at the bottom right of
  `LauncherScreen`'s root Box; `PacksBar`, `packsProgressText`.
- **Template pack (C:\Users\User\Downloads\Templates (1)\Templates, same as `Templates`):** used: cover/ front + spine
  (cov_*), cartridge + with label, disc, 3dbox `3D box.png` shapes (tpl_box_*), cover texture spine + front (tpl_spine_*,
  tpl_front_*). Not used yet: `cover - back.png` (most consoles) and the back third of the cover textures, the
  finished `<console> 3dbox.png`, `label/` frames, cartridge colour variants, `_Assets` logos, `_examples` (reference
  renders only).
- **Next / asked:** printed backs for the 3D cases from the back templates (user to confirm), the facing / angled
  examples as a toggle (we have Settings › Box turned to show its spine). Still open from before: Grid default size, TV
  room grid ignores the look, Tile look row off the bottom, Game shelf front cover late, Thor second screen, Asobi
  shortcuts. Mixer one-shot way still untested on the Classic.

## State after update 168
- **Mixer one-shot way:** the Classic's PServer (`/system/bin/pservice`, popen) runs the helper fine but the `daemon`
  never stays. `AppMixer.keeperDied` (≥ 2 → `oneShot`, persisted as meta `keeper_dies`): `apply()` HELPER runs
  `holdOnce()` (helper mode `hold uid:amp …` = apply + list in one process) instead of starting the daemon; the
  EffectLoop (AudioPlaybackCallback pokes it) runs `holdOnce` per change, waits up to 30 s otherwise, and uses its list
  for the effect on handle-less players. Mixer check's Volume keeper line says so. The adb shell user can call the
  helper directly (`CLASSPATH=$(pm path …) app_process /system/bin … MixerHelper list|apply|hold`); shell can't reach
  PServerBinder (`service call` → "does not exist").
- **Cross menu lists:** `Features.crossCats` (category keys); `LauncherUiState.categoryOrder` keeps them on the Cross
  even when hidden; chips in `StyleHomeSettings.CrossSettings`.
- **Carousel case media:** `caseMediaFor()` / `CaseMedia` / `CaseWithMedia3D` (Carousel.kt) for CASE / CASE_REAL with
  `discCase` or `caseWithMedia` when `cases3d`; slot widened 2 × `room` (case centred); `PhysicalMedia(noCase = true)`.
- **Next:** 169 the cartridge cover templates (`tpl_cart_*`, `tpl_cartl_*`, 51) without native branding, the console logo
  instead. Still open from before: custom case templates in the SteamGridDB cover's shape, Grid default size, TV room
  grid ignores the look, Tile look row off the bottom, Game shelf front cover late, Thor second screen, Asobi shortcuts.

## State after update 167
- **Case shapes / sizes:** `case3dAspect` ignores `mixed` (real shape always); `realCaseScale` = (longest/190)^0.6,
  0.8…1; Carousel `caseRefMm` (Box flow or CASE_REAL: the biggest case's longest side among the games) → `realK`
  (^0.6, 0.74…1) on the 3D case's slot height, `floorDrop` keeps it on the floor.
- **MediaCovers.own / used:** Steam / App / Android cases always (`OWN`); `used(p, mixed)` replaces every
  `coverTemplates ? MediaCovers.of(p)`. Case3D: `framed()` puts the art in the own case's window under its band; spine
  template used for them too.
- **Carousel:** `carouselTiles()` (Tile look → aspect 1); `carouselWideOn()` + `Features.carouselWide` (set by the look
  step "Covers · wide capsule", chip in Game look) — the focused item's aspect animates to CAPSULE_ASPECT and it draws as
  CAPSULE; the middle-width unit follows.
- **Keys:** Grid / TV ▲ ▼ with `loopRow` go through `viewModel.moveFocus(±cols)`; Timeline ▼ wraps with Endless; Cross
  ▼ from the last game → 0.
- **Cross menu:** `slideNow` (the pending CategorySlide.dir in the frame the category changes); CrossList hides rel ≤ −0.7.
- **Warm-up:** `warmSoft()` (RealMedia.kt) decodes the 480 / 640 px soft bitmaps ahead for 3D cases / pieces; Case3D
  `hueFront` while loading.
- **Packs.refreshOld():** at init, MODELS items with an old marker (`.v68`) and no new one are fetched again.
- **Next:** 168 the volume mixer without Shizuku (built-in Wireless debugging pairing → helper as shell); 169 the
  cartridge cover templates without native branding.

## State after update 166.1
- **Fluent turns:** `InspectModel` draws the two nearest pictures as `InspectFrame`s, each in its own layer turned
  `d` / `d − 20` degrees (d = past the lower picture), crossfaded over d 7…13. `Model3DLive` turns its layer by
  `wrap(yaw − a0)` (±12°) when it isn't on its own slow turn.

## State after update 166
- **3D pack v6.11, all the way round:** `tools/render3d/batch3.js` (18 angles −160…180, from `glbplan.json`: the
  blank-branding GLB where there is one, else v6.11), then `backs.py --write` (adds `backs` / `backAspect` — the back
  label's corners where the back faces you — and fixes a logo plate without geometry, 3ds_card_real), zipped as
  `model_<id>.zip` into `packs/v1`. Marker `<id>.v611` (data/Packs.kt). `model_logos.zip` is unchanged (same systems.json).
  The Save Vault's clear cards have only −60…60 (`SPARE_ANGLES`; SwayingModel never goes past 60).
- **Models3D:** `ANGLES` −160…180, `wrap()`, `nearest()` round the circle; `Meta.backs/backAspect`; `compose(…, backLogo)`
  prints the game's logo on the back label (`composeFront` is the old compose); keys carry the logo only at back angles.
  `idFor()` never returns a case (the 3D looks are cartridge looks; disc consoles → their custom cartridge, solid with
  REAL); "Disc consoles as" (`modelDiscAsCart`) has no chip any more. Real pieces carry `tint.own` (own colours, console
  logo on the blank badge).
- **MediaInspectView:** `InspectModel` crossfades the two nearest real angles (no squeeze / mirror); `InspectPiece.logo`
  (set in `inspectPieceFor`); BOX pieces sized by `realCaseScale` (Case3D.kt) — `case3dAspect` uses `realCaseMm`'s shape
  also with cover templates off, `realCaseDepth` the thickness when there's no spine picture.
- **Carousel:** `MediaBandLayer` no longer called (the grey / dark panel behind turned games).
- **Features.migrate166** (`m166_face_done`): "The one you're on" back to Faces you once.
- **Baked logos:** `assets/platform_logos_png` (44) and `assets/system_logos_png` (65), from the SVGs by
  `tools/render3d/rasterlogos.js`; `PlatformLogos.baked()` / `bakedForUrl()` (kept for the app's life), used by
  `PlatformLogo` and `CrossIconTile`. `res/drawable-nodpi/media_umd.png`: "PSP" lettering removed.
- **Open for 167:** the cartridge cover templates (`tpl_cart_*`, `tpl_cartl_*`) still show native branding; volume mixer
  without Shizuku (the user found github.com/yume-chan/VolumeManager, which needs Shizuku).

## State after update 165
- Carousel `MediaBandLayer`: shift capped at tan ≤ 1.5, drawn only for turns 3–60°, lighter shade.

## State after update 164
- GameLookPicker `LOOK_STEPS`: Cartridges (ORIGINAL), Custom cartridges (CUSTOM), Clear cartridges (CUSTOM + clear),
  3D cartridges (REAL), 3D custom (CUSTOM), 3D clear (CLEAR); `applyCartStep`, `cartStepMatches`, `pickCartStep`. The
  "Cartridges" chip row in Settings lists them; CartridgeOptions keeps only the shape.
- GameWindow `motion.open(..., spin = fullInspect)`.

## State after update 163
- `Features.motionOnLite` (default on): GameWindow `fullInspect` and Carousel `pathSlide` ignore Lite with it.
- MediaInspectView `InspectModel`: `inspectNorm` / `inspectEff`; past ±60° squeeze, past 90° the unlabelled model mirrored
  (back); `motion.limited` is always false now.
- R1 "look3d" tile: cartridge looks set `modelFinish` REAL, or CUSTOM / CLEAR when `cartsOnly` + `CartSource.CUSTOM`.

## State after update 162
- **Carousel.kt:** one moving position `CarouselGlide` (spring, rowGlide stiffness), items placed in graphicsLayer via
  `Carousel.placeInto` (Place fields are var), `CarouselCtx` remembered; `cases3d = caseLook && (carousel3d || view.cases)`;
  `MediaBandLayer` / `mediaBandFor` (edge thickness by MediaShell — flip the sign there if the edge shows on the wrong
  side); `carouselSlotH` (mixed lists); `stackEase` / `stackZ` (Box flow, Spine wall); `CarouselSwap` (category slide
  along the path, `CategorySlide.pathOwners` turns `categorySlide()` off while a carousel is composed).
- **Case3D:** `back`, `yawOf`, `shadeOf`, `realFront` parameters; face 4 = back.
- **Inspect:** MediaInspect.kt (`amount`, `key`, `drawnAsCase` set by the carousel), MediaInspectView.kt
  (`inspectPieceFor`, `InspectMotion`, `InspectStick` on AXIS_Z), GameWindow `InspectLayout`, `LocalWinInspect`.
  Not in the banner window style or the TV room's set; other layouts (grids, rows) don't make room.
- **Presets:** `LayoutPreset.look` (`PresetLook`), `features` map, `carousel`; `applyPresetExtras()` in PresetsCard.kt.
  "(modified)" still checks only the look settings.

## State after update 161
- **CarouselView.ARCH / BOX_FLOW** (Carousel.place; BOX_FLOW `cases = true`, `reach` ×3 like the Spine wall, flatView →
  FLIP). The arch stage is gone from Case stacks (`Features.stacksArch` removed); StacksArch.kt keeps the tool strip and
  the bottom panel.
- **Keep art in memory:** `Features.keepArt` (default on) → `MemoryBudget.keepArt` (read once): `imageCacheShare` 0.3,
  `KeepingCache` wraps Coil's memory cache and ignores trims below TRIM_MEMORY_COMPLETE / RUNNING_CRITICAL; `release()`
  returns early likewise. Manifest `android:largeHeap="true"` (RP Classic: heap 256 → 512 MB).

## State after update 160
- **Case stacks:** StacksArch.kt (`StacksToolbar` = `c.shortcuts` in StyleNav.top, `StacksArchStage`, `StacksPanel`
  with StyleNav.bar Options / Favourite / Details / Play); `Features.stacksArch` (default on), `stacksToolbar`;
  `StacksFront` / `stacksCaseLook` internal. Old `StacksBar` and `StacksRoom` gone.
- **DiscLook.on** (PhysicalMedia.kt, set from LauncherScreen): `typedArtFor(CARTRIDGE_DISC)` gives null for cartridge
  games in Discs only.
- **R1 "look3d" tile:** case looks → `Features.caseModels3d` (CaseArt's `models3d` default, `Models3D.caseModel`, carousel
  3D cases); cartridge looks → `setModelsLook`.
- **Model3DArt:** a case model (`Models3D.isCase`) takes `BoxTexture.front` via `HomeScans.keyOfCover` (cover → key).
- **CustomCarts:** shells through `rememberShellRequest` (560 px, key `shell|path|mtime`), labels through `mediaArtModel`.
- **Still open:** Grid default size, ▲ ▼ round on Grid / TV room / Cross menu, Timeline one-group repeat, TV room grid
  ignores the look, Tile look row off the bottom, Game shelf front late, Cross menu icons, custom case templates in the
  SteamGridDB cover's shape, Thor second screen.

## State after update 159
- **Carousel.kt cover**, PhysicalMedia / DiscCase3D real disc scan, RealMedia box front: their `contentScale` /
  `modifier` had been swallowed by trailing comments in 156 / 157 (restored).
- **Disc station:** only `mediaKind(platform) == "disc"` games use their CARTRIDGE_DISC scan (StyleDiscStation,
  StyleHomes warm-up, LaunchThrow); cartridge games print the cover.
- **Case stacks:** `StacksRoom` removed; flat-cover middle sized by `CoverAspects`; `GameCarousel` in the stage when
  STACKS is in `Features.carouselLayouts` (Settings shows the Carousel fold for STACKS too).
- **`styleOwnsBackground(layout)`** (StyleHomes): no full-screen video preview over those backdrops.
- **Still open (now 160):** Grid default size, ▲ ▼ round on Grid / TV room / Cross menu, Timeline one-group repeat, TV
  room grid ignores the look, Tile look row off the bottom, Game shelf front cover late, Cross menu icons, custom case
  templates take the SteamGridDB cover's shape, Case stacks side spines in other looks, Thor second screen.

## State after update 158
- **New layouts** (enum order after DISCS: HERO_TILES, TWIN_DESK, TWIN_ICONS, CRATE, CARDS; all `styleHome`):
  StyleHeroTiles.kt (`heroTilesKey`, ContinuousGrid strip, dock from `heroTilesDockApps`), TwinScreens.kt / TwinDesk.kt /
  TwinIcons.kt (`TwinHost`: with Voltcap's second screen up, `SecondScreenContent` draws the bottom half; else stacked),
  StyleRecordCrate.kt (`recordCrateKey`, part `part:CRATE:info`), StyleCardBinder.kt (`cardBinderButton` for Y / A / B
  before the D-pad code in `styleHomeKey`, `cardBinderKey`). No presets / pictures of their own yet.
- **Lobby** (`AppTheme.LOBBY`, `ThemeMotif.LOBBY`): drawn by LobbyScene.kt on every Android; not recolorable;
  `SceneSource` returns "" for it; Theme.kt uses the light Material scheme when the theme background is light (not
  with game / album colours). `gen.py` `NO_SCENE` writes `LOBBY -> ""` / `emptySet()` into ThemeShaders.kt.
- **PickedFacing.kt:** `Features.pickedFaces` (default on); `modelTurn` default off + `migrate158` (`m158_face_done`).
- **Not tested on a device.** Open: Lobby with the many hard-coded white texts; Hero tiles status bar on the pale backdrop;
  Card binder hold-A timing; Twin layouts on the Thor's second screen.
- **Found on the RP Classic (157.1), for 159:** Grid default size (user's is Large), ▲ ▼ don't go round on Grid / TV room /
  Cross menu, Timeline repeats one group, TV room grid ignores the look, Tile look on the row runs off the bottom, Game
  shelf front cover late, Case stacks crop + flat neighbours, Cross menu icons blurry, custom case templates should take
  the SteamGridDB cover's shape, carousel frame taller than the art.

## State after update 157
- **SharedArtInterceptor** (StyleArtRequests.kt, first in the image loader's components): String requests without
  their own key / transformations / parameters, resolved size ≤ `styleArtPx` (and ≥ 160 px on one side) → size px×px,
  FIT, key `url|style|px` — the same as `styleArtRequest` / `warmStyleArt`.
- **mediaArtModel(model)** for disc labels, real media, 3D box fronts. StyleHome warm-up: focus / 2, 150 ms; DISCS warms
  label scans. GameDock warms the style shape for media looks.

## State after update 156
- **Endless:** `loopsOnItsOwn()` is true for every layout (no `EdgeBump.wrap` any more). New helpers: LoopPlaces.kt
  (`rememberLoopPlace`, `LoopTap`), StyleLoopSteps.kt (`StyleLoop`, `rememberWalkedPlace` — Library / Timeline / Channel),
  LiveLoop.kt (Live tiles pages). HomeGameGrid / TileHomeRow get `loopTick` / `loopStep` from LauncherScreen.
- **Art requests:** StyleArtRequests.kt (`styleArtRequest` fixed square px = half the short side, 360–720, key
  `url|style|px`, thumb placeholder) used by `StyleArt`, GameCarousel covers / wide art and their warm-ups.
- **CategoryWarmup.kt:** neighbouring categories' `visibleGames` + thumbs + first card covers, 1.2 s after landing.
- **Open:** Grid ▲ ▼ don't go round; swipes at the ends on Tiles / Stacks / Console stack.

## State after update 155
- **GestureHint.systemOwnsNav** (Retroid + root service): `SystemBars.apply` doesn't hide the navigation bar
  (`windowHidesNav`); the force layer (global `hide_nav_bar`) does. On the RP Classic both together showed the pill.
- **AppMixer:** `heldUids()` (from `pgrep -fl`), `stopHelperAndRelease()` — in `ensure()` when a helper runs that this
  process didn't start (`daemonSig` empty), and in the EFFECT branch of `apply()`. Automatic → HELPER on Retroid.
- Found: a `voltcap_mixd` from 09:33 (old APK path) holding Spotify (uid 10134) at 0.09.

## State after update 154
- **Disc station:** `Features.discPlayer` (PLAYER default / ART), `Features.discCalm` (true). DiscPlayer.kt:
  `discPlayerBody`, `discWell`, `DiscPlayerFront`. `discCalm` in LauncherScreen → LocalSceneCovered, no VideoBackdrop,
  ambience / backdrop touches covered. Disc box has `gameOrigin` → LaunchThrow.planFor makes a DISC throw for DISCS.
- **Covers:** `CoverAspects` (Carousel.kt) notes each plain cover's shape; `shelfArtAspect(..., cover)`. `FillUnlessMuchCut.MAX_CUT`
  = 0.06, window background = `FrameColors` when the art is there.
- **AutoBackupWorker:** `includeDeviceSetup = true`.
- **Charge limit / AutoTDP:** off in this handheld's prefs since the reinstall (not a code fault: PServer calls work,
  `/sys/class/qcom-battery/restrict_chg` is written at start). The user switches them on again.
- **Not tested on the device yet** (release build: no run-as).

## State after update 153
- **Release build installed** (22:13): `run-as` no longer works, so prefs can't be edited from adb; layouts are switched
  through Settings › Home › Layout & presets (pick the layout chip, then "Use <layout> as it is").
- **Disc station:** `DiscPanel` takes the cover when there's no backdrop; a Screen-blended radial wash of the glow colour
  (`discIsDark` → stronger).
- **GameDock:** `rowAtBottom` → the name line above the row wraps (no fixed two-line / logo height).
- **Open:** "some art cut off on all sides" — asked the user which layout / look (screenshot).

## State after update 152
- **Performance (measured on the RP Classic, carousel in Grid):** debug build: 50th-percentile frame 50–77 ms, about 43 % of
  the main thread in the ART interpreter (debuggable). Use `installRelease` for daily use (debug and release share the
  release key, same package, so they install over each other). `run-as` only works on the debug build: install debug for
  test sessions that edit prefs.
- **AnimatedArt:** decoded AnimatedImageDrawables are registered (weak); `onMove()` (from MainActivity key / touch) stops the
  running ones and restarts them 450 ms after the last move; `setSleeping` from HomeIdle deep (3 min).
- **Open:** Side menu "random folder icons" still not reproduced (All apps shows empty icon squares for a moment while the
  icons load — maybe that). Start-game throw not tried on the device (it would have started a game).

## State after update 151
- **3D:** `Models3D.idFor` gives disc consoles their CASES model (`modelDiscAsCart` keeps the custom cartridge); `CaseArt(models3d = false)` → case looks flat. `Model3DArt(life = true)` → `Model3DLive` (Models3DLife.kt: shadow, sheen, ±20° turn between pre-rendered angles, `modelTurn`, `modelReflect`). Composite cache keyed by the cover's address (`Models3D.noteSource` from `rememberSoftBitmap`, `cachedFor`), and no coverless composite while the cover loads.
- **Throw:** `LaunchThrow.kt` overlay from `startGame` in LauncherScreen (`Features.launchThrow`); `DiscCase3D.kt` (`discCase3d`).
- **Achievements:** `AchSpots.kt` (corner placements), comments auto-open (`achCommentsAuto`).
- **Video:** `videoPreviews` on by default + one-time migration.
- **Save Vault:** `SaveMatch.kt` (name / ID matching), `DriveOnlySaves.kt` (Drive listing, 20-min cache), "Other saves" group; not connected → connect prompt.
- **Layouts:** ThemeTint.kt (theme accents into layouts), HomeEditLegend.kt, StyleTimelineHistory.kt (new Timeline), DiscStationFx.kt.
- **Open:** throw ignores "Launch animation" off (own setting); Side menu folder-icon flash not reproduced (needs a recording); Timeline focused name pill can cover neighbours' dates; grid scroll lag still to measure on the device.

## Planned next
- Nothing left from my long list of 10 Oct (130–134 are done). Ask me what's next.

## Open questions for me
- **`settings.gradle.kts`:** the previous Claude changed it directly to `rootProject.name = "Voltcap"` (outside project_copy). I still have to say keep or revert.

## Housekeeping still open (I do these, but remind me)
Delete the old spine pictures listed in update 125 (`cov_spine_*` except `cov_spine_steam` and `cov_spine_android`, and every `tpl_spine_*`), since 126 `vc_spine_androidgames.webp` and `vc_spine_androidapps.webp`, and since 131 every `vc_spine_*.webp` and the folder `assets/carts` (all in the 131 PowerShell block). Create the public repo `dzepdzep/Voltcap` and upload `project_copy\packs\v1\` (all 234 files) to `packs/v1` on `main` (steps in SUMMARY 140). Before that: nothing left to delete (checked 9 Oct, after 123). The mascot files and pictures, `tools/mascot`, `ThemePacks.kt`, `LoopThemes.kt`, `ThemePacksCard.kt` and `make_theme_packs.py` are all gone.

The `loops` asset folders are already deleted.

Start by reading the top of `SUMMARY.md`, then tell me you're ready.
