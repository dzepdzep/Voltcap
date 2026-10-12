# Shelf — Retro Launcher (Android)

Native Kotlin + Jetpack Compose port of the web mockup: full-bleed background
art that crossfades per selection, a bottom dock where the focused game
unfolds from a portrait cover into a landscape banner, SteamGridDB art
(automatic, manually-linked, or resolved from a Steam AppID), a unified
library combining scanned ROMs (multi-disc PS1 games collapsed into one
entry), installed apps, and manually-added Steam games via GameNative, a
"continue playing" ordering based on what you last launched, and
RetroAchievements progress display for manually-linked games.

## Honest status

I don't have an Android SDK, Gradle, or network access in the environment
that generated this code, so **none of this has actually been compiled**.
Every change goes through a manual pass — package declarations checked
against folder paths, every cross-file call traced to its definition, brace/
paren balance verified — but only a real Gradle sync can confirm it builds.
Treat that first sync as the actual test, not this description.

The GameNative integration specifically is built on a web search, not prior
knowledge — see "Steam games via GameNative" below for exactly what's
verified versus assumed there.

## Building it

1. Open the `ShelfLauncher/` folder in Android Studio and let it sync — it'll
   offer to generate the Gradle wrapper (`gradlew`/`gradlew.bat` + the jar),
   which isn't included since those are binary/generated files I can't
   produce without network access. `gradle/wrapper/gradle-wrapper.properties`
   (pinning Gradle 8.4) is included.
2. Run on a device/emulator (API 24+).
3. Tap **Art: off** to connect SteamGridDB (steamgriddb.com/profile/preferences/api) —
   the key is encrypted at rest (`SgdbKeyStore.kt`, via Jetpack Security) and
   remembered across restarts.
4. Tap **⋯** on a focused ROM to configure its emulator (system chooser or a
   specific app/activity — apps and Steam entries don't need this, they
   launch directly).
5. Tap **Library** to grant storage access and set/scan your ROM folder.
6. Tap the **i** icon on any focused game to manually link a SteamGridDB game
   ID instead of relying on fuzzy name search (Vapor-Launcher-style).
7. Installed-app entries show an eye-off icon instead of the emulator ⋯ — tap
   it to hide that app from the library. Hidden apps can be restored from the
   **Library** dialog.
8. Tap **Steam** to add a Steam game by name + AppID — see below for how it
   launches and where its art comes from.
9. Tap the search icon to filter the dock by name, and use the platform pill
   row (appears once there's more than one platform in the library) to
   filter by NES/SNES/App/Steam/etc. Both combine — search and a platform
   filter can be active at once.
10. Multi-disc PS1 games are collapsed into one entry automatically during a
    ROM scan — see "Multi-disc games" below for exactly how.
11. Tap **Achievements** to connect a RetroAchievements account, then the
    trophy icon on any focused ROM to manually link it to a RetroAchievements
    game ID — see "RetroAchievements" below for what this does and doesn't do.

## What's in the library, and where it comes from

The dock merges three kinds of entries into one sorted list:

- **ROMs** — `RomScanner.kt` walks a folder (default
  `/storage/emulated/0/ROMs`, editable) matching known extensions to
  platforms. Needs `MANAGE_EXTERNAL_STORAGE` ("all files access"), which on
  Android 11+ has to be granted from a Settings screen — the **Library**
  button walks you to the right one either way.
- **Installed apps** — `AppScanner.kt` lists everything with a launcher icon,
  same as an app drawer would. Doesn't try to guess which apps are "games" —
  everything launchable shows up (hide the rest with the eye-off icon).
- **Steam games (via GameNative)** — added manually by name + Steam AppID
  through the **Steam** dialog, not scanned. See the dedicated section below.

Each `Game` has a `stableKey` (ROM path, package name, or `steam:<appId>`)
used to key the art cache and manual SteamGridDB ID overrides, so a rescan
or an edit doesn't lose either.

### Search and platform filtering

`LauncherUiState.visibleGames` is a computed property, not stored state —
it filters the full `games` list by the current search text and platform
selection on the fly. `focusedIndex` always indexes into `visibleGames`, not
the raw list, so `setFocus`/`recombineGames` clamp against the filtered
count. `games` itself stays untouched by any filter, since art fetching,
recently-played sorting, and hidden-app bookkeeping all need to see the
whole library regardless of what's currently searched or filtered.

### Continue playing

`RecentlyPlayedStore.kt` records a timestamp on every successful launch,
keyed by `stableKey`. The merged library is sorted by that timestamp
(most-recent first; anything never played keeps its name-sorted order after
that) — so there's no separate "Continue Playing" shelf, the game you last
played is just whatever's focused/leftmost when the app opens, the same
effect Steam's Big Picture mode has. The focused banner also shows a
relative "Played 2h ago" caption when a game has a recorded timestamp.

### Steam games via GameNative

[GameNative](https://github.com/utkarshdalal/GameNative) runs Windows Steam
(and Epic/GOG/Amazon) games on Android via Wine/Proton — no ROM file, no
streaming. Verified by searching rather than assumed from training data:

- **Verified**: GameNative has a documented, "already-exported" launch
  intent (confirmed by its own project discussion, and by a Cart Edition
  fork's README showing the exact `am start` invocation):
  ```
  am start -n app.gamenative/app.gamenative.MainActivity \
      -a app.gamenative.LAUNCH_GAME -ei app_id <steamAppId>
  ```
  Note the `-ei` — the AppID is an **integer** extra, not a string.
  `GameLauncher.kt` builds this exact intent via an explicit component
  (`setClassName`), which — unlike the app-drawer scan — doesn't need any
  package-visibility `<queries>` entry, since explicit intents bypass that
  restriction entirely.
- **Verified**: SteamGridDB's own API docs confirm
  `GET /api/v2/games/steam/{steamAppId}` returns the matching SteamGridDB
  game object directly from a Steam AppID — exact, no fuzzy name matching.
  `fetchArt()` uses this automatically for Steam entries before falling back
  to name search, so the same AppID you type in for launching also gets you
  the right art without a second manual SteamGridDB ID lookup.
- **Assumed, not confirmed**: that the mainline GameNative app's package name
  is exactly `app.gamenative` (confirmed for the CE fork; the mainline
  project's own PR discussion implies the same, but I didn't find its
  manifest directly). The package name, activity, action string, and extra
  key are all editable in the Steam dialog's "GameNative settings (advanced)"
  section if your install differs.
- **Not attempted**: auto-discovering which Steam games are already
  installed in GameNative. There's an open (unmerged, as of this being
  written) pull request proposing a ContentProvider for exactly that — worth
  revisiting if/when it ships, since it would let this become a real scan
  instead of manual entry.

### Multi-disc games

Multi-disc PS1 (and similar CD-based) games get one library entry instead of
one per disc, using the `.m3u` playlist format RetroArch and most PS1 cores
already understand natively for in-game disc swapping — `RomScanner.kt`
doesn't invent its own disc-switching, it just makes sure a `.m3u` exists and
points the library entry at that.

Three cases, in order:
1. **An `.m3u` already exists** in the folder — its listed discs become one
   entry, and those disc files are excluded from the rest of the scan so
   they don't also show up individually.
2. **No `.m3u`, but a name pattern matches** — files sharing a title that
   only differs by a `(Disc N)` / `(Disk N)` / `(CD N)` style suffix (case-
   insensitive, optionally `"of N"` / `"/N"`) get one generated next to them,
   *only* if that filename isn't already taken by something the scanner
   didn't recognize as covering them (never overwrites).
3. **Everything else** scans as a normal single-file game, same as before.

If a `.m3u` can't be written (permission edge case, read-only volume), the
scan doesn't fail — it falls back to listing those discs individually and
says so in the post-scan status message, along with how many groups *did*
get created.

### RetroAchievements

Achievements are unlocked by watching live emulator memory during play —
only the emulator core itself can actually do that. RetroArch already does
this natively once you log into Retro Achievements in *its own* settings;
this launcher can't unlock anything and doesn't try to. What it does instead:
shows achievement progress (earned / total) for games you've manually linked
to a RetroAchievements game ID, both as a corner badge on the cover and a
count on the focused banner.

- **Full achievement list, with a missable filter**: tap the "🏆 X/Y
  achievements" line on a focused, linked game's banner to see every
  achievement (locked and unlocked), with a "Missable only" toggle. This is
  a single call to `API_GetGameInfoAndUserProgress.php` — confirmed directly
  against the live api-docs.retroachievements.org page while building this,
  which is also how a mistake from the first pass at this feature got
  caught: that first version assumed (from a bug-report discussion, not the
  primary docs) that this endpoint didn't return the achievement `type`
  field, and merged in a second endpoint to compensate. Fetching the actual
  current docs page showed `type` genuinely is there — lowercase, unlike
  every other PascalCase field on the same object, which is why it's
  declared lowercase on purpose in `RaProgressAchievement` rather than
  "corrected" to match the rest. The unnecessary second endpoint is gone.
  Fetched lazily per game (only when you open the list), cached until you
  relink that game's ID.
- **Recently-unlocked feed**: open **Achievements** while connected to see
  what's unlocked in the last 24 hours across your whole account, with a
  refresh button — `API_GetUserRecentAchievements.php`, confirmed against
  the same live docs page (params: `u`, `y`, and `m` for minutes-to-look-
  back, defaulting server-side to 60 if omitted; passed explicitly here as
  1440 so opening this doesn't show an empty feed most of the time).
- **Verified**: the endpoint shape and auth for the progress-count call —
  `GET https://retroachievements.org/API/API_GetUserProgress.php?u=<username>&y=<webApiKey>&i=<comma-separated game IDs>`
  — confirmed against RetroAchievements' own API docs (`u` = username, `y` =
  web API key, both required on every endpoint).
- **Manual linking only, by design**: RetroAchievements identifies games by a
  ROM hash, and the exact hashing rules are console-specific (not a plain
  file MD5) — getting that subtly wrong would silently match the wrong game,
  which is worse than not matching at all. So, same reasoning as the
  SteamGridDB manual ID: find the game on retroachievements.org, copy the ID
  from the URL, paste it into the trophy-icon dialog.
- **Not independently confirmed**: the exact JSON field names for
  `RaGameProgress`, `RaExtendedAchievement`, and `RaProgressAchievement` —
  these follow RetroAchievements' well-established PascalCase API convention
  (confirmed via a real client library's struct definitions for the
  adjacent "recent achievements" endpoints), but no search this pass
  surfaced a literal response body for GetUserProgress, GetGameExtended, or
  GetGameInfoAndUserProgress specifically to check field-by-field. If
  progress or the achievement list comes back empty despite a working
  connection, that's the first thing to check against a real response.
- Credentials are encrypted at rest the same way as the SteamGridDB key (see
  `RaCredentialsStore.kt`), with the same honest fallback if the Keystore is
  ever unavailable.

## Known gaps / natural next steps

- **Libretro core `.so` paths** (for the explicit-intent RetroArch route)
  vary by device architecture and RetroArch version, so that field in the
  emulator-config dialog is intentionally left for you to fill in per device.
- **No auto-scan for GameNative's Steam library** — see above.

## Fixed after real-device testing

A few things only showed up once this was actually running on a physical
handheld, not guessable from code review alone:

- **Card sizes were too large for shorter landscape screens.** The original
  cover/banner dimensions were sized without accounting for handhelds with
  limited landscape height (~380-400dp) — the focused banner alone was tall
  enough that the whole bottom-aligned UI cluster (platform filter row +
  dock + control bar) overflowed upward and visually overlapped the top
  status bar. Shrunk both `CoverSize` and `BannerSize` in `GameCard.kt` to
  fit comfortably within that budget.
- **Battery percentage was a static icon with no real number.** `StatusBar`
  now reads the actual live percentage via a `BroadcastReceiver` on
  `ACTION_BATTERY_CHANGED` (see `rememberBatteryPercent()` in
  `LauncherScreen.kt`), registered/unregistered with the composable's
  lifecycle.
- **Sample/placeholder games no longer show by default.** `sampleLibrary`
  (Ember Trail, Deep Signal, etc.) was previously seeded as the default ROM
  list so there was always *something* on screen before a real scan — the
  library now starts empty and only shows what's actually scanned/added.
  The sample data itself is still defined in `Models.kt` in case it's useful
  for future preview/testing, just no longer wired in as a default.
- **Achievements are now reachable from a controller, not just touch.**
  Pressing the **Y** button (`Key.ButtonY`) on whichever game is currently
  focused opens its achievement list directly — handled at the root `Box`
  in `LauncherScreen.kt`, relying on Compose's key-event bubbling from
  whichever `GameCard` currently holds real focus. Also added a visible
  "Y ACHIEVEMENTS" hint to the control bar, since an undiscoverable shortcut
  isn't much better than no shortcut on a gamepad-first UI.

## Quick start

1. **Point it at your ROMs.** Menu → Library → grant storage access → Browse
   to your ROM folder → Scan. Consoles appear as filter pills once games are
   found (they're derived from what's actually scanned, so an empty library
   shows none).
2. **Say which emulator runs what.** Menu → Emulators → tap a console →
   pick from your installed apps. Known emulators for that system are
   suggested at the top.
3. **Add artwork.** Menu → Scrapers → SteamGridDB → paste an API key from
   steamgriddb.com/profile/preferences/api. Covers, backgrounds and logos
   fill in automatically.

Optional extras, same pattern: Menu → Scrapers → **IGDB** for scores and
how-long-to-beat (press ▼ on a game to see them), and Menu → **Achievements**
for RetroAchievements progress (press Y on a game).

## Game details (IGDB)

Pressing **down** on a focused game opens a panel with its summary, release
year, developer, genres, aggregated critic score, and time-to-beat figures
(rushed / normally / completionist).

This uses IGDB rather than scraping HowLongToBeat. HLTB has no public API, so
pulling from it means scraping their site — fragile, and not something they
permit. IGDB has an official `game_time_to_beats` endpoint carrying the same
three figures, so it's one credentialed source instead of an API plus a
scraper.

IGDB is owned by Twitch, so setup goes through the Twitch developer console:
register an app at dev.twitch.tv/console, then paste the Client ID and a
generated Client Secret into Menu → Scrapers → IGDB. Credentials are
encrypted at rest like the others, and the OAuth token is cached in memory
and refreshed a minute before expiry rather than on every request.

## Themes and UI structure

Five themes ship in `ui/theme/ShelfColors.kt`: Ember (default amber),
Cobalt, Synthwave, Matcha, and Mono — the last being true-black for OLED
handhelds, where the art ends up the only colour on screen. Pick one under
**Menu › Theme**; the choice persists via `ThemeStore`.

Colours are delivered through a `CompositionLocal` (`LocalShelfColors`) and
read as `ShelfTheme.accent` / `.background` / `.secondary`, so no screen
hardcodes a colour and adding a sixth palette means adding one enum entry —
nothing else changes. The ViewModel is hoisted above the theme wrapper in
`MainActivity` so a theme switch repaints the whole tree, dialogs included.

The top bar used to carry Library / Steam / Achievements / Art chips
alongside the status icons, which crowded it badly on a handheld. Those all
moved into **MENU** (bottom-left, or Start on a controller). The status bar
now only shows actual status: search, wifi, battery, and the
RetroAchievements avatar + points.

## Making it a home-screen replacement

The `HOME` / `DEFAULT` intent-filter categories are commented out in
`AndroidManifest.xml`. Uncomment them if you want this launchable as the
device's actual home screen instead of just an app icon — note that holding
the Home-app role also automatically grants full package visibility, so
`AppScanner` would no longer need the `<queries>` block once that's enabled.

## Google Drive sync (Settings › Sync)

Syncs emulator save folders and Voltcap's own per-game data (favourites,
playtime, recently played, artwork picks, manual IDs) through a visible
**Voltcap Sync** folder in your Google Drive. Code: `data/CloudSync.kt`
(sync logic), `data/DriveClient.kt` (Drive REST calls over OkHttp),
`ui/CloudSyncContent.kt` (the settings page).

### One-time Google Cloud setup (~5 minutes)

Google only hands out Drive access to apps it knows by package name and
signing key, so this has to be done once:

1. Go to <https://console.cloud.google.com>, create a project (e.g. "Voltcap").
2. **APIs & Services › Library** → enable **Google Drive API**.
3. **APIs & Services › OAuth consent screen** (Google Auth Platform) →
   External, app name "Voltcap", your email as support/developer contact.
   Under **Data access / Scopes** add `.../auth/drive.file`.
   Under **Audience**, add your Google account as a test user — or press
   **Publish app**: drive.file is a non-sensitive scope, so no Google review
   is needed, and published apps don't make you re-confirm access every 7 days.
4. **Credentials › Create credentials › OAuth client ID** → type **Android**,
   package name `com.shelf.launcher`, and the SHA-1 of your signing key:
   - debug builds: in Android Studio's Terminal run
     `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"` and then
     `.\gradlew signingReport` (the first line points Gradle at Android
     Studio's own Java), or run the Gradle task
     `app › Tasks › android › signingReport` (or
     `keytool -list -v -keystore %USERPROFILE%\.android\debug.keystore -alias androiddebugkey -storepass android`)
   - a release keystore needs its own Android client with its SHA-1.
   No client ID goes into the code — Play Services matches the app by
   package + signature.
5. On each handheld: Settings › Sync › **Connect Google Drive**, pick the
   account, allow. The device needs Google Play services (the Odin 2 has them).

### How it behaves

- **Save folders** sync by *name*: "RetroArch saves" on one device meets
  "RetroArch saves" on the other, whatever the paths. Suggested when they
  exist (upper/lower case doesn't matter): RetroArch saves, PPSSPP
  (`PPSSPP/PSP/SAVEDATA` or `PSP/SAVEDATA`), ARMSX2 / NetherSX2 /
  DuckStation / ePSXe memory cards, DraStic, Flycast, Citra, M64Plus FZ,
  legacy `dolphin-emu`. Anything else via "Add folder…".
- **Only real saves are synced.** Each folder has a save type
  (`SaveKind` in `data/SyncTrees.kt`) that decides which files count:
  RetroArch `.srm/.sav/.rtc/.eep/.fla/.sra/.mpk/.mcr/.mcd/.ps2/.dsv/…`,
  PPSSPP whole `SAVEDATA/<game>/` folders, PS2 `.ps2` cards, PS1
  `.mcd/.mcr`, DraStic `.dsv`, Flycast `vmu_save_*.bin` (not the BIOS),
  Dolphin `.gci/.raw` and Wii `…/data/`, Citra `…/data/`. Logs, configs,
  BIOS, shaders and installed games stay out. Folders you add yourself use
  every known save type. Files an older build uploaded that aren't saves
  are ignored (left in Drive, never downloaded).
- Every folder row has **Change** to point it somewhere else (name and save
  type stay, so it keeps syncing with the same Drive folder).
- **Save states are never synced**, only in-game saves and memory cards:
  state files (.state, .ppst, .dss, .p2s…) and state folders are skipped
  everywhere, hidden in the folder picker, and state folders saved by older
  versions are dropped.
- **Dolphin** keeps saves in `Android/data/org.dolphinemu.dolphinemu/files`,
  which Android 11+ won't let other apps open directly. "Add from another
  app…" (or the Dolphin suggestion) opens Android's picker: ☰ → Dolphin →
  "Use this folder". Voltcap then syncs only `GC` and `Wii/title` through
  that permission (`data/SyncTrees.kt`, DocTree).
- Per file: changed on one side → that side wins, silently. Changed on
  both (or different on the very first sync) → nothing is touched and
  Voltcap asks: *Use Drive's* / *Keep this device's* / *Newest of each*,
  showing when and on which device each version was saved. The version not
  kept goes to `/Voltcap/Sync conflicts/`. "Decide later" leaves both as
  they are. Deletions are never propagated — a deleted save comes back.
- With "Sync before and after playing" on, launching a game first checks
  Drive. The player is only asked something when there's a decision:
  a conflict (above), or Drive couldn't be reached within 15 s
  (*Play anyway* / *Try again* / *Cancel*). Coming back to Voltcap pushes
  saves up. Close the game inside the emulator before returning, otherwise
  the emulator may not have written the save yet.
- Also syncs 5 s after Voltcap starts, and on "Sync now".
- **Launcher data** is merged, not replaced: playtime adds up across
  devices, favourite stars added/removed on either side stick, artwork picks
  from device files (content://) stay local. ROM games match by their path
  *inside* the ROM folder, so keep the same folder structure on both devices.

## Collections, play status, screenshots, BIOS check, weekly recap

Every item here has its own switch; switched off, it disappears completely.

- **Collections** (Settings › Library › Collections & status). All three
  kinds show under the *Collections, franchises & status* entry of the
  category order, which you can move or hide there.
  - Your own lists: add a game with Start › Collections…
  - Franchises with 3 or more games in the library ("Final Fantasy",
    "Pokémon", "The Legend of Zelda"). These are found from game names and
    update as games are added (`data/Collections.kt`, `Franchises`).
  - Play statuses (Start › Status): Playing, Finished, Dropped and Wishlist.
    Each status with at least one game in it becomes a category. The Random
    game hotkey skips Finished and Dropped games.
- **Achievements** (Appearance › Achievements):
  - *Rarity*: "Unlocked by X % of players" (`NumAwarded` ÷
    `NumDistinctPlayers` from `API_GetGameInfoAndUserProgress`), a *Rare*
    tag under 5 %, an *Easiest next* mark, and set order / most unlocked /
    rarest sorting.
  - The new *Almost mastered* category lists started games nearest to 100 %
    first.
  - *Tips*: a Tips button in the details window opens the guide and every
    comment, and the D-pad scrolls through them.
- **Your screenshots** (Library › Your screenshots). The details window
  lists images from the emulators' screenshot folders whose file name
  starts with the ROM name or the title (`data/Screenshots.kt`). Tap one to
  view it full screen and use ◀ ▶ to flip through. You can add more
  folders.
- **BIOS check** (Settings › Emulators). For each console you own that
  needs a BIOS, it shows whether a file with the right name is in
  RetroArch/system, the emulators' BIOS folders or the ROM folder. It only
  checks names, not whether the dump is good (`data/BiosCheck.kt`).
- **Weekly recap** (Settings › Stats). A notification on Sunday evening
  with the week's play time, days played, most-played game and finished
  games (`data/RecapWorker.kt`, WorkManager).

## Screen layouts (Settings › Screens)

What goes on each screen when a game starts — for built-in second screens
(AYN Thor, Ayaneo Flip DS…) and for a TV or monitor on the video out.

- **Layouts** say which screen the game goes on and what the other one gets:
  Voltcap's info (cover, a live session timer, total playtime, achievement
  progress, music controls), another app (Discord, YouTube, a browser guide…),
  a black screen, or nothing new. "Emulator uses both screens" keeps Voltcap
  out of the way for DS/3DS emulators that draw both screens themselves.
  Six are built in; make your own under Settings › Screens › Layouts.
- **Rules** pick the layout, most specific first: this game › a collection
  it's in › its console › its emulator › your default › Voltcap's default
  (game + info; DS/3DS use both screens; on a TV, the game goes on the TV).
  Set them from a game (Start › Screens), where you can also play once with a
  different layout without saving anything. Settings › Screens lists and
  removes them.
- **Two setups, two sets of rules**: "Built-in second screen" and "TV or
  monitor". A connected screen is guessed from its name (HDMI/DP/USB/cast →
  TV) and can be corrected in Settings › Screens; screens are remembered by
  name and resolution, since display numbers change between boots.
- **Launch order**: the other screen is filled first, the game ~350 ms later,
  because Android gives the controller to whatever started last.
- **Coming back**: returning to Voltcap's home screen closes what it put on
  the other screen. An app you put there stays open (Voltcap can't close
  other apps). When the game is on the second screen, B / Back / "Back to
  Voltcap" on the info page returns to the home screen.
- Files: `data/ScreenLayouts.kt` (layouts, rules, store, screen detection),
  `ui/CompanionActivity.kt` (the info/black page on the other screen),
  `ui/ScreenLayoutsUi.kt` (Start › Screens picker and Settings › Screens),
  `LauncherViewModel.launchNow` (the launch sequence), and `GameLauncher`'s
  new `displayId` parameter.
- **Not verified on hardware**: whether every emulator/app accepts being
  opened on a second screen. Android can refuse; Voltcap then starts the game
  anyway and says which part didn't go on the other screen.

## Second screen home, ROM folders, category defaults (later additions)

- **Second screen while you're in Voltcap** (`ui/SecondScreenHome.kt`): tabs
  for Art, Video (the ScreenScraper clip), Details, Achievements (progress +
  this game's recent unlocks) and Music, for the game you're on; and a dock
  with five apps of yours, all apps (a grid on the second screen) and a cog
  that opens Settings › Screens. Dock on/off, its apps, where they open and
  the default tab are under Settings › Screens › While you're in Voltcap.
- **Several ROM folders + deep search** (Settings › Library › ROM folders):
  every folder is scanned (subfolders included, hidden folders and
  Android/data skipped); deep search recognises console folders at any depth
  (ROMs/Nintendo/SNES → SNES). The first folder is the main one.
- **Categories on by default**: All, Recent and the consoles. Favorites,
  Most played, Mastered, In progress, Almost mastered, Emulators and the
  Collections entry start hidden (switch them on under Settings › Library ›
  Categories); franchises and the new genre categories start off. Existing
  installs get this once, unless you had already hidden categories yourself.
- **Looping row** keeps sliding the same way with one or two games.
- **Start › Refetch RetroAchievements icon** (and "Refetch all missing icons").
