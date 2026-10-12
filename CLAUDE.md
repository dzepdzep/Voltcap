# VoltCap — Claude guide

Android game launcher for handhelds (AYN Odin/Thor, Retroid, phones). Kotlin + Jetpack Compose, single module `:app`.
Package `com.shelf.launcher` (project folder `ShelfLauncher`). User-facing name: Voltcap.

## Permanent rules
1. Read this file first. Read a `docs/claude/*.md` file only when the task touches that system.
2. Do not scan the whole project. Open only files needed for the current task.
3. Search for the exact file/class/function first (grep), then read only the relevant range. Never read huge files whole.
4. Do not spawn subagents, background agents or parallel workers unless I explicitly ask.
5. Do not redo work that is already complete. Check the current code (and `SUMMARY.md`) before changing a feature.
6. Reuse existing code/components (see "Shared UI" below) instead of creating duplicates.
7. Preserve controller (D-pad/buttons), touch and second-screen support in every UI change.
8. Keep explanations short; spend tokens on implementation.
9. Run only the build/checks the change needs.
10. Do not modify CLAUDE.md during normal tasks unless I ask or the project structure changes significantly.

## Workflow (always)
- **Edit `src_copy/` only**, never `app/src/main/java/com/shelf/launcher/` directly. I robocopy `src_copy` into the real source myself. `src_copy/` mirrors `app/src/main/java/com/shelf/launcher/` 1:1.
- Files outside the Kotlin sources (Gradle files, manifest, `res/`, keep rules) go in **`project_copy/`** at the same path relative to the project root (I run `robocopy project_copy . /E`).
- Theme scene tools stay in `tools/themes/` (edit in place).
- After every update, add an entry at the **top** of `SUMMARY.md` (newest first): what changed, what to copy, files touched, what was checked / not tested.

## Layout
```
app/build.gradle.kts        deps, SDK, R8, signing     app/compose_stability.conf
app/src/main/AndroidManifest.xml   app/src/main/res/raw (bgm_*.ogg, ui_*_*.ogg)   res/drawable-nodpi (mascot_*.png)
app/src/main/cpp/           Discord native bridge (CMake)      app/libs/discord/  Discord SDK .aar
src_copy/                   ← EDIT HERE (Kotlin sources)
  MainActivity.kt           host; dispatchKeyEvent/Touch/GenericMotion → HomeIdle.poke(), SystemButtonLayout
  LauncherViewModel.kt      app state + logic (~260 KB — grep, never read whole)
  LauncherUiState.kt        UI state data classes
  data/                     stores (SharedPreferences), scanners, APIs, sync, RA/Steam, guides (data/guide), perf (data/perf)
  device/                   charge limit, tuner (device/tune), vendor/root, accessories, MemoryBudget
  spotify/                  Spotify auth/API/repo
  ui/                       all Compose screens; ui/theme/ (ShelfColors, Ds, Theme)
tools/themes/               AGSL scenes + gen.py → ui/ThemeShaders.kt
tools/audio/                Python generators for the .ogg music/sounds (reference only)
tools/release/              PRE_RELEASE_CHECKLIST.md, GITHUB_RELEASE.md
SUMMARY.md, README.md       running change log / feature overview (large: grep, don't read whole)
```

## Big files — grep, then read ranges
`ui/ThemeShaders.kt` (900 KB, GENERATED — never edit or read), `LauncherViewModel.kt` (260 KB), `ui/LauncherScreen.kt` (220 KB),
`ui/SettingsDialog.kt` (190 KB), `ui/GameDetailsOverlay.kt` (78 KB), `ui/HomeChrome.kt` (68 KB), `data/CloudSync.kt` (71 KB),
`data/AppearanceStore.kt`, `ui/SaveExplorer.kt` (56 KB), `ui/theme/ShelfColors.kt` (48 KB).

## Key systems → where
| System | Files |
|---|---|
| Home screen root | `ui/LauncherScreen.kt` (`LauncherScreen()`), dialog flags in `ui/LauncherUiFlags.kt` |
| Home layouts | classic row `ui/GameDock.kt` + `ui/GameCard.kt`; `ui/TileHome.kt`; `ui/HeroCarousel.kt`; status bar/legend `ui/HomeChrome.kt` |
| Game popup | `ui/GameDetailsOverlay.kt` |
| Settings UI | `ui/SettingsDialog.kt`, `ui/SettingsExtras.kt`, R1 panel `ui/QuickSettings.kt`, L1 `ui/ShortcutsPanel.kt` |
| Look settings | `data/AppearanceStore.kt` ("appearance" prefs + enums); newer switches `data/Features.kt` ("voltcap_features") |
| Themes / scenes | `ui/theme/ShelfColors.kt`, `ui/ThemeScene.kt`, `ui/ThemeSceneLegacy.kt`, `ui/ThemeThumbs.kt` → see `docs/claude/themes.md` |
| Music | `ui/MusicPlayer.kt`, `ui/NowPlayingPanel.kt`, `ui/NowPlayingScene.kt`, `ui/MusicTheme.kt`, `data/AudioReactive.kt`, `data/Lyrics.kt`, `spotify/` |
| Background music / UI sounds | `ui/BackgroundMusic.kt`, `ui/UiSounds.kt`, `MusicLoop` in `ShelfColors.kt` |
| Achievements | `data/RetroAchievementsApi.kt`, `data/AchievementSeen.kt`, `ui/AchievementListDialog.kt`, `ui/BadgeWall.kt`, `ui/AchievementFx.kt`, `ui/HubScreens.kt` |
| Saves | `data/CloudSync.kt`, `data/DriveClient.kt`, `data/SaveCapsules.kt`, `data/MemoryCards.kt`, `ui/SaveExplorer.kt` |
| Second screen | `ui/SecondScreen.kt` (Presentation host, `findSecondDisplay`), `ui/SecondScreenHome.kt`, `ui/CompanionActivity.kt`, `ui/CompanionHub.kt` |
| Overlays over games | `ui/PerfOverlayService.kt`, `ui/RaOverlayView.kt`, `ui/MusicOverlayView.kt` |
| Device / power | `device/ChargeLimiter.kt`, `device/tune/*`, `data/GameMode.kt`, `ui/PowerSaver.kt` (`HomeIdle`) |
| Library / launch | `data/RomScanner.kt`, `data/AppScanner.kt`, `data/GameLauncher.kt`, `data/RunningGames.kt`, `data/EmulatorCatalog.kt` |
| Backup | `data/Backup.kt`, `data/SettingsScopes.kt` (new prefs must be added here to be backed up) |

## Shared UI (reuse)
- Tokens: `ui/theme/Ds.kt` (spacing 4/8/12/16/24, radii, icon sizes, text sizes, status colours).
- `ui/DialogChrome.kt`: `ShelfDialog`, `ShelfMenuDialog`, `ShelfCard`, `MenuRow`, `SwitchRow`, `RadioLine`, `Chip`/`ChipFlow`, `SettingsHeading`, `SettingsGrid`, `SectionLabel`, `MutedNote`, `EmptyState`, `IconBadge`, `CloseButton`, `ShelfTextField`, modifiers `shelfClickable`, `shelfFocusable`, `focusRing`, `dpadScroll`.
- `ui/FitText.kt` (auto-fit text), `ui/Reveal.kt`, `ui/SettingsSlider.kt`, `ui/ColorTools.kt`, `ui/RetroFx.kt` (synth sounds).
- Input rules: see `docs/claude/input-screens.md`.

## Build & checks
- Toolchain: AGP 9.3.3 (built-in Kotlin, no kotlin-android plugin), Kotlin/Compose plugin 2.2.10, Gradle 9.5.0, compileSdk/targetSdk 36, minSdk 24, Java 17, NDK 28 (CMake bridge), Compose BOM 2026.02.01, Coil 2.7, Retrofit 2.11, WorkManager 2.10.1. Release = R8 + resource shrinking (`app/src/main/keepRules/rules.keep`; own classes kept for Gson).
- I build in Android Studio on Windows (`gradlew.bat assembleDebug`) from `app/src` after copying. No Android SDK in Claude's cloud workspace; a type-check against the Android/Compose jars in `~/.gradle` caches is the usual substitute. Say clearly what was and wasn't compiled.
- Shader change: `python tools/themes/gen.py` then `python tools/themes/check_uniforms.py` (details in `docs/claude/themes.md`). Nothing else for Kotlin-only changes.
- Keep `MainMenu` and `GameDetailsOverlay` functions under the JVM method-size limit (split into sub-composables).
- Theme names/art must be original (no trademarks, characters, logos).
