# Voltcap — handover to a new Claude

Written 12 Oct 2026, after update **(145)** (kept up to date to 165). Read this whole file before you change anything, then read the top 3–5 entries of `SUMMARY.md`.

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
15. **GitHub (since 144):** the project folder is a git repo on `github.com/dzepdzep/Voltcap`, branch `main` (public: the code, the art packs in `packs/v1`, the releases). Every update's PowerShell block ends with the commit and push, after the robocopy lines: `cd "C:\Users\User\AndroidStudioProjects\ShelfLauncher"`, `git add -A`, `git commit -q -m "Update <n>: <short title>"`, `git push origin main`, then `.\gradlew installDebug`. `.gitignore` keeps `app/libs/discord`, `src_copy`, `project_copy`, `Claude outputs`, `screens`, logs, keys and builds off GitHub. One-time set-up: `tools\release\setup_github.ps1`. Never put an interactive command (Read-Host, a sign-in) in the middle of a pasted block — pasted lines after it get read as its answers; run those as a one-line script call on their own.
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
