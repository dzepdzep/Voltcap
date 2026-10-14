package com.shelf.launcher

import com.shelf.launcher.data.isCollectionKey
import android.app.Application
import android.os.Build
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shelf.launcher.data.AppScanner
import com.shelf.launcher.data.EmulatorConfig
import com.shelf.launcher.data.DefaultEmulators
import com.shelf.launcher.data.EmulatorSettingsStore
import com.shelf.launcher.data.PreferencesStore
import com.shelf.launcher.data.ArtCacheStore
import com.shelf.launcher.data.ArtCandidates
import com.shelf.launcher.data.ArtOverride
import com.shelf.launcher.data.ArtOverridesStore
import com.shelf.launcher.data.ArtSlot
import com.shelf.launcher.data.FavoritesStore
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.GameArt
import com.shelf.launcher.data.GameLauncher
import com.shelf.launcher.data.GameNativeConfig
import com.shelf.launcher.data.GameNativeSettingsStore
import com.shelf.launcher.data.GameMetadata
import com.shelf.launcher.data.GameOverridesStore
import com.shelf.launcher.data.IgdbCredentials
import com.shelf.launcher.data.IgdbCredentialsStore
import com.shelf.launcher.data.IgdbRepository
import com.shelf.launcher.data.createIgdbApi
import com.shelf.launcher.data.HiddenAppsStore
import com.shelf.launcher.data.LaunchOutcome
import com.shelf.launcher.data.LibrarySettingsStore
import com.shelf.launcher.data.RaAchievementDetail
import com.shelf.launcher.data.RaCredentials
import com.shelf.launcher.data.RaCredentialsStore
import com.shelf.launcher.data.RaGameProgress
import com.shelf.launcher.data.MetadataSource
import com.shelf.launcher.data.MetadataCacheStore
import com.shelf.launcher.data.MetadataSourceStore
import com.shelf.launcher.data.RaGameListCache
import com.shelf.launcher.data.RaHasher
import com.shelf.launcher.data.RaListedGame
import com.shelf.launcher.data.RaMatchEntry
import com.shelf.launcher.data.RaMatchSource
import com.shelf.launcher.data.RaMatchStore
import com.shelf.launcher.data.RaTitleMatcher
import com.shelf.launcher.data.RaUserProfile
import com.shelf.launcher.data.RaRecentAchievement
import com.shelf.launcher.data.PlaytimeStore
import com.shelf.launcher.data.ArtStorage
import com.shelf.launcher.data.AppsMode
import com.shelf.launcher.data.CategorySettings
import com.shelf.launcher.data.CategoryStore
import com.shelf.launcher.data.LibraryAppsSettings
import com.shelf.launcher.data.LibraryAppsStore
import com.shelf.launcher.data.EmulatorRegistry
import com.shelf.launcher.data.CoreRegistry
import com.shelf.launcher.data.AppearanceSettings
import com.shelf.launcher.data.AppearanceStore
import com.shelf.launcher.data.RaAward
import com.shelf.launcher.data.RaCompletionEntry
import com.shelf.launcher.data.RaDataCache
import com.shelf.launcher.data.RaProfileStats
import com.shelf.launcher.data.VideoUrlStore
import com.shelf.launcher.data.ArtStorageSettings
import com.shelf.launcher.data.ArtStorageStore
import coil.Coil
import coil.annotation.ExperimentalCoilApi
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.shelf.launcher.data.ScreensaverSettings
import com.shelf.launcher.data.ScreensaverStore
import com.shelf.launcher.data.RecentlyPlayedStore
import com.shelf.launcher.data.RetroAchievementsRepository
import com.shelf.launcher.data.RomScanner
import com.shelf.launcher.data.SgdbAuth
import com.shelf.launcher.data.NowPlaying
import com.shelf.launcher.data.SgdbKeyStore
import com.shelf.launcher.data.ShelfNotification
import com.shelf.launcher.data.ShelfNotificationListener
import com.shelf.launcher.data.ShortcutsStore
import com.shelf.launcher.data.mergeFields
import com.shelf.launcher.data.mergeArt
import com.shelf.launcher.data.asCandidates
import com.shelf.launcher.data.lacksAny
import com.shelf.launcher.data.SourceType
import com.shelf.launcher.data.SteamGameEntry
import com.shelf.launcher.data.SteamGamesStore
import com.shelf.launcher.data.SteamGridDbRepository
import com.shelf.launcher.data.ArtSource
import com.shelf.launcher.data.TgdbKeyStore
import com.shelf.launcher.data.ScreenScraperCredentials
import com.shelf.launcher.data.ScreenScraperRepository
import com.shelf.launcher.data.ScreenScraperStore
import com.shelf.launcher.data.createScreenScraperApi
import com.shelf.launcher.data.TheGamesDbRepository
import com.shelf.launcher.data.ThemeStore
import com.shelf.launcher.data.createTheGamesDbApi
import com.shelf.launcher.ui.theme.AppTheme
import com.shelf.launcher.data.createRetroAchievementsApi
import com.shelf.launcher.data.createSteamGridDbApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Screen state and the small helpers that go with it — split out of
// LauncherViewModel.kt so that file is only the ViewModel itself.

enum class ArtConnectionState { OFF, CONNECTING, ON, FAILED }
enum class RaConnectionState { OFF, CONNECTING, ON, FAILED }

/** Non-platform library categories that sit alongside the platform pills. */
enum class SpecialFilter(val label: String) {
    FAVORITES("★ Favorites"),
    RECENT("Continue playing"),
    MOST_PLAYED("Most played"),
    MASTERED("Mastered"),
    IN_PROGRESS("In progress"),
    /** Started, not mastered — closest to 100 % first. */
    ALMOST_MASTERED("Almost mastered"),
    /** Smart: in the library but never started from Voltcap. */
    NEVER_PLAYED("Never played"),
    /** Smart: played before, not in the last 60 days — oldest first. */
    FORGOTTEN("Not played lately"),
    EMULATORS("Emulators"),
    /** Smart Continue (optional, Settings › Library): what you're on, near completion, a few achievements
     * left, and what you were into before — best picks first. */
    SMART_CONTINUE("Smart Continue"),
    /** Update 132: the Cross menu's Trophies category — every game with achievements, last played first. Only in the
     *  Cross menu's category cycle (Settings › Home › Layout options › Cross menu options › Trophies category). */
    TROPHIES("Achievements"),
    /** Rediscover: games not played in 6+ months. Only opened by hand (L1 › Rediscover); never in the
     * category cycle and never mixed into the normal order. */
    REDISCOVER("Rediscover")
}

/** Platform key of the "Android games" category: installed apps Android
 * marks as games, plus apps added to the library by hand (emulators left out). */
const val ANDROID_GAMES_PLATFORM = "Android"

/** Sort names are cleaned once per title, not on every comparison. */
private val sortNameCache = java.util.concurrent.ConcurrentHashMap<String, String>()
internal fun sortName(name: String): String =
    sortNameCache.getOrPut(name) { com.shelf.launcher.ui.stripRegionTags(name).lowercase() }


enum class AchievementBadgeMode { PROGRESS, RECENT_UNLOCK }

/** Sources "Add missing" can be retried with, one at a time. */
enum class MissingSource(val label: String) {
    IGDB("IGDB"),
    RAWG("RAWG"),
    SCREENSCRAPER("ScreenScraper"),
    STEAMGRIDDB("SteamGridDB"),
    THEGAMESDB("TheGamesDB")
}

/** Things a game's details window can offer to fetch when they're missing. */
enum class MissingInfo(val label: String) {
    SUMMARY("summary"),
    SCORE("score"),
    METACRITIC("Metacritic"),
    PLAYTIME("time to beat"),
    VIDEO("video"),
    COVER("cover"),
    BACKGROUND("background"),
    LOGO("logo")
}

/** "Couldn't reach Google Drive — play anyway?" for [game]. */
data class SyncOfflinePrompt(val game: Game, val reason: String)

data class LauncherUiState(
    val games: List<Game> = emptyList(),
    val usingSampleLibrary: Boolean = false,
    val romRootPath: String = LibrarySettingsStore.DEFAULT_ROOT_PATH,
    /** Every ROM folder (Settings › Library › ROM folders); the first is [romRootPath]. */
    val romRootPaths: List<String> = listOf(LibrarySettingsStore.DEFAULT_ROOT_PATH),
    /** Console folders recognised at any depth. */
    val deepSearch: Boolean = true,
    val isScanning: Boolean = false,
    val art: Map<String, GameArt> = emptyMap(), // keyed by Game.stableKey
    val focusedIndex: Int = 0,
    val artState: ArtConnectionState = ArtConnectionState.OFF,
    val statusMessage: String? = null,
    /** Starting a game couldn't check Google Drive for newer saves first. */
    val syncOfflinePrompt: SyncOfflinePrompt? = null,
    /** A game that starts once the player picks which saves to keep. */
    val gameWaitingOnConflicts: Game? = null,
    /** Small corner note for Google Drive sync ("Checking for newer saves…"). */
    val syncNotice: String? = null,
    /** What kind of news [syncNotice] is (look + sound); null for progress / plain notes. */
    val syncNoticeKind: com.shelf.launcher.data.SyncNoticeKind? = null,
    val emulatorConfigs: Map<String, EmulatorConfig> = emptyMap(),
    val hiddenApps: List<Game> = emptyList(),
    val isArtKeyEncrypted: Boolean = true,
    val gameNativeConfig: GameNativeConfig = GameNativeConfig(),
    val lastPlayedAt: Map<String, Long> = emptyMap(), // keyed by Game.stableKey
    val playtime: Map<String, Long> = emptyMap(), // Game.stableKey → total ms played
    val screensaver: ScreensaverSettings = ScreensaverSettings(),
    val artStorage: ArtStorageSettings = ArtStorageSettings(),
    val artStorageUsedBytes: Long = 0L,
    val artStorageMissing: Int = -1, // -1 = not counted yet
    val artSaveDone: Int = 0,
    val artSaveTotal: Int = 0,
    val appearance: AppearanceSettings = AppearanceSettings(),
    /** Bumped whenever a screen layout or rule changes (Settings › Screens,
     * a game's Screens option), so what shows them re-reads. */
    val screenLayoutsVersion: Int = 0,
    /** Counts left/right steps in a looping row, with the last step's
     * direction — so the row keeps sliding the same way even with one or two
     * games, where the focused index alone can't tell which way you went. */
    val loopTick: Int = 0,
    val loopStep: Int = 0,
    val libraryApps: LibraryAppsSettings = LibraryAppsSettings(),
    val categories: CategorySettings = CategorySettings(),
    val gameAppPackages: Set<String> = emptySet(),      // Android games, recognised automatically
    val knownEmulatorPackages: Set<String> = emptySet(), // emulators Shelf recognises itself
    val raCompletionById: Map<Int, RaCompletionEntry> = emptyMap(), // RetroAchievements game ID
    val raProfileStats: RaProfileStats = RaProfileStats(),
    val videoUrls: Map<String, String> = emptyMap(), // Game.stableKey → clip address
    val screenshots: Map<String, List<String>> = emptyMap(), // Game.stableKey → screenshot addresses
    /** Box front / back, disc… for the details window: Game.stableKey → (label, address). */
    val physicalMedia: Map<String, List<Pair<String, String>>> = emptyMap(),
    /** RetroAchievements game icons (full addresses), by RA game ID. */
    val raIcons: Map<Int, String> = emptyMap(),
    /** Update 113: each game's SteamGridDB logo for the big logos (stableKey → url; "" = it has none). */
    val sgdbLogos: Map<String, String> = emptyMap(),
    val metadataNotes: Map<String, String> = emptyMap(), // Game.stableKey → result of a manual details lookup
    val searchQuery: String = "",
    val platformFilter: String? = null, // null = "All"
    val specialFilter: SpecialFilter? = null, // Favorites / Recently played
    val favorites: Set<String> = emptySet(), // Game.stableKey
    val raProfile: RaUserProfile? = null,
    /** Community comments per achievement ID, loaded when you open one. */
    val raComments: Map<Int, List<com.shelf.launcher.data.RaComment>> = emptyMap(),
    val raCommentsLoading: Set<Int> = emptySet(),
    /** Collections, franchises and play statuses shown as categories. */
    val collections: List<com.shelf.launcher.data.ShelfCollection> = emptyList(),
    /** Your own collections (name → game keys), for the pickers. */
    val userCollections: Map<String, Set<String>> = emptyMap(),
    /** Playing / Finished / … per game key. */
    val playStatuses: Map<String, com.shelf.launcher.data.StatusEntry> = emptyMap(),
    /** Comments + guide link per RetroAchievements game ID (details window). */
    val raGameTips: Map<Int, com.shelf.launcher.data.RaGameTips> = emptyMap(),
    val artOverrides: Map<String, ArtOverride> = emptyMap(), // Game.stableKey
    /** Per-game background upscale factor (1 = off); missing = automatic. */
    val heroUpscale: Map<String, Int> = emptyMap(), // Game.stableKey
    val artCandidates: ArtCandidates? = null,
    val isLoadingArtCandidates: Boolean = false,
    val appTheme: AppTheme = AppTheme.EMBER,
    val installedApps: List<Game> = emptyList(),
    val igdbConnected: Boolean = false,
    val rawgConnected: Boolean = false,
    val metadataReady: Boolean = false,
    val artSource: ArtSource = ArtSource.STEAMGRIDDB,
    val tgdbConnected: Boolean = false,
    val screenScraperConnected: Boolean = false,
    val screenScraperUsername: String? = null,
    val metadataSource: MetadataSource = MetadataSource.IGDB,
    val metadata: Map<String, GameMetadata> = emptyMap(), // Game.stableKey
    val metadataLoading: Set<String> = emptySet(),
    /** Artwork & details › Compare sources: each source's result, per game. */
    val detailCandidates: Map<String, Map<MetadataSource, GameMetadata>> = emptyMap(),
    val detailCandidatesLoading: Set<String> = emptySet(),
    /** Games an "Add missing" fetch is running for. */
    val fillingMissing: Set<String> = emptySet(),
    /** Games "Add missing" already ran for this session — what's still
     * missing then isn't out there, so it isn't offered again. */
    val missingTried: Set<String> = emptySet(),
    val metadataScrapeDone: Int = 0,
    val metadataScrapeTotal: Int = 0,
    val scrapeDone: Int = 0,
    val scrapeTotal: Int = 0,
    /** What art / detail scrapes download. */
    val scrapeOptions: com.shelf.launcher.data.ScrapeOptions = com.shelf.launcher.data.ScrapeOptions(),
    val shortcuts: List<Game> = emptyList(),
    val notifications: List<ShelfNotification> = emptyList(),
    val hasNotificationAccess: Boolean = false,
    val nowPlaying: NowPlaying? = null,
    val notificationError: String? = null,
    val isMatchingAchievements: Boolean = false,
    val raMatchDone: Int = 0,
    val raMatchTotal: Int = 0,
    val raMatches: Map<String, RaMatchEntry> = emptyMap(), // Game.stableKey
    val raCustomHashes: Map<String, String> = emptyMap(), // Game.stableKey
    val achievementBadgeMode: AchievementBadgeMode = AchievementBadgeMode.PROGRESS,
    val achievementBadgeNextToTitle: Boolean = false,
    val raState: RaConnectionState = RaConnectionState.OFF,
    /** The two saved RetroAchievements accounts' user names (null: empty slot) and the one playing: kept here so the screens follow changes. */
    val raAccounts: List<String?> = emptyList(),
    val raActiveSlot: Int = 0,
    val raUsername: String? = null,
    /** Games that just appeared in the library (keys), for the arrival animation; cleared once shown. */
    val newArrivals: List<String> = emptyList(),
    val isRaKeyEncrypted: Boolean = true,
    val raProgress: Map<String, RaGameProgress> = emptyMap(), // keyed by Game.stableKey
    val raAchievementDetails: Map<String, List<RaAchievementDetail>> = emptyMap(), // keyed by Game.stableKey
    val raAchievementDetailsLoading: Set<String> = emptySet(), // stableKeys currently fetching
    val raRecentAchievements: List<RaRecentAchievement> = emptyList(),
    val isRaRecentLoading: Boolean = false,
    // ---- RetroAchievements data health (appended: keep new fields at the end) ----
    /** Last failed refresh of a game's achievement list, by Game.stableKey (absent = last refresh worked). */
    val raDetailsError: Map<String, com.shelf.launcher.data.ra.RaError> = emptyMap(),
    /** When the shown achievement list was fetched, by Game.stableKey. */
    val raDetailsFetchedAt: Map<String, Long> = emptyMap(),
    /** Games whose shown list is old (past the refresh window) or failed to refresh. */
    val raDetailsStale: Set<String> = emptySet(),
    /** Achievement IDs the person marked "I missed this" (user-confirmed only). */
    val raMissedMarks: Set<Int> = emptySet(),
    val raGameTipsError: Map<Int, com.shelf.launcher.data.ra.RaError> = emptyMap(),
    val raCommentsError: Map<Int, com.shelf.launcher.data.ra.RaError> = emptyMap(),
    /** Achievements with older comments still to load. */
    val raCommentsHasMore: Set<Int> = emptySet(),
    val raCommentsLoadingMore: Set<Int> = emptySet(),
    /** The small card after a game ends (time played, unlocks, save sync); null = none. */
    val sessionSummary: com.shelf.launcher.ui.SessionSummary? = null,
    /** The brief post-game line in the status bar ("Played 42 min · +3 achievements"); null = none. */
    val sessionNote: String? = null,
    /** Achievements earned in the game just left: a trophy in the status bar until they're looked at. */
    val returnTrophies: com.shelf.launcher.data.ReturnTrophies? = null,
    /** When each game first appeared (epoch ms; 0 = already there before we tracked it), for "Newly added". */
    val firstSeen: Map<String, Long> = emptyMap(),
    /** Per-category sort choices (key: see [sortCategoryKey]); a category without one uses the global sort. */
    val categorySort: Map<String, com.shelf.launcher.data.SortMode> = emptyMap()
) {
    /** What the dock actually shows — `games` stays the full, unfiltered
     * library so art fetching, recently-played sorting, etc. keep operating
     * on everything regardless of what's currently searched/filtered. Search
     * and platform filter combine (both must match). `focusedIndex` is
     * always an index into THIS list, not into `games`. */
    /** Which category the sort choice belongs to: the special filter, the platform / collection, or "all". */
    val sortCategoryKey: String
        get() = when {
            specialFilter != null -> "special:${specialFilter.name}"
            platformFilter != null -> "cat:$platformFilter"
            else -> "all"
        }

    /** The sort in force here: this category's own pick, else the global one. */
    val effectiveSortMode: com.shelf.launcher.data.SortMode
        get() = categorySort[sortCategoryKey] ?: appearance.sortMode

    /** Categories in your order, hidden ones left out (never empty). */
    val categoryOrder: List<String>
        get() {
            val known = CategorySettings.known(SpecialFilter.values().map { it.name })
            // (Update 168: on the Cross menu, the lists you picked for it show too — Cross menu options › Lists.)
            val crossExtra = if (appearance.homeLayout == com.shelf.launcher.data.HomeLayout.CROSS)
                com.shelf.launcher.data.Features.crossCats.state.value else emptySet()
            val shown = categories.normalized(known).filter { it !in categories.hidden || it in crossExtra }
                // Rediscover only opens by hand; Smart Continue only while it's switched on.
                .filter { it != SpecialFilter.REDISCOVER.name }
                .filter { it != SpecialFilter.SMART_CONTINUE.name || com.shelf.launcher.data.Features.smartContinue.state.value }
                // Update 132: Trophies only on the Cross menu, while its switch is on.
                .filter {
                    it != SpecialFilter.TROPHIES.name || (appearance.homeLayout == com.shelf.launcher.data.HomeLayout.CROSS &&
                        com.shelf.launcher.data.Features.crossTrophyCat.state.value)
                }
            return shown.ifEmpty { listOf(CategorySettings.ALL) }
        }

    /** Every emulator: recognised ones, ones marked by hand, and any app set
     * up to run a console. */
    val emulatorPackages: Set<String> by lazy {
        // Built once per state: it's read for every app when filtering.
        knownEmulatorPackages + libraryApps.emulators + emulatorConfigs.values.mapNotNull { it.packageName }
    }

    /** Whether an app belongs in the library's normal categories. */
    fun isAppInLibrary(game: Game): Boolean {
        val pkg = game.packageName ?: return true
        if (pkg in libraryApps.added || game.stableKey in favorites) return true
        return when (libraryApps.mode) {
            AppsMode.ALL -> true
            AppsMode.GAMES_AND_ADDED -> isAndroidGame(game)
            AppsMode.ONLY_ADDED -> false
        }
    }

    /** Whether an installed app counts as an Android game. */
    fun isAndroidGame(game: Game): Boolean {
        if (game.sourceType != SourceType.APP) return false
        val pkg = game.packageName ?: return false
        if (pkg in libraryApps.forcedApps) return false
        if (pkg in libraryApps.forcedGames) return true
        if (pkg in emulatorPackages) return false
        return pkg in gameAppPackages
    }

    /**
     * Worked out once per state (the state is immutable, so it can't go stale)
     * instead of on every read — with hundreds of games the filtering and
     * sorting on each access was what made big categories stutter.
     */
    val visibleGames: List<Game> by lazy {
        // Shared across copies: a copy that only moves the focus (or changes
        // art, dialogs, …) has the same inputs, so it reuses the earlier list.
        val key = MemoKey(
            games, platformFilter, specialFilter, searchQuery, favorites, lastPlayedAt, playtime,
            raCompletionById, metadata, appearance.sortMode, libraryApps, collections,
            gameAppPackages, knownEmulatorPackages, emulatorConfigs, firstSeen, categorySort,
            appearance.hideDuplicates, appearance.regionOrder,
            // (update 91) "Not played lately", Rediscover and Smart Continue depend on the clock: an hour
            // bucket (compared as a string) keeps a cached list from going stale for days.
            (System.currentTimeMillis() / 3_600_000L).toString()
        )
        visibleMemo.getOrCompute(key) { computeVisibleGames() }
    }

    private fun computeVisibleGames(): List<Game> {
        if (platformFilter == ANDROID_GAMES_PLATFORM) {
            return games.filter { isAndroidGame(it) }
                .filter { searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) }
                .let { sortGames(it) }
        }
        if (specialFilter == SpecialFilter.EMULATORS) {
            val emulators = emulatorPackages
            return games.filter { it.sourceType == SourceType.APP && it.packageName in emulators }
                .filter { searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) }
                .let { sortGames(it) }
        }
        val library = games.filter { it.sourceType != SourceType.APP || isAppInLibrary(it) }.let { list ->
            if (!appearance.hideDuplicates) list
            else com.shelf.launcher.data.RegionDedupe.apply(list, appearance.regionOrder.split(',').map { it.trim() }.filter { it.isNotEmpty() }) { g ->
                g.stableKey in favorites || lastPlayedAt.containsKey(g.stableKey)
            }
        }
        val byCategory = when {
            specialFilter == SpecialFilter.FAVORITES -> library.filter { it.stableKey in favorites }
            // "Recently played" means actually played — anything with a
            // recorded launch timestamp, most recent first.
            // Update 200: games only — every Android game (also with "Only apps you add", where they aren't in the
            // library's normal categories but are tracked), never an ordinary app (Settings, Spotify, an emulator
            // opened on its own…) that got a launch time from being started from the dock.
            specialFilter == SpecialFilter.RECENT ->
                (library + games.filter { isAndroidGame(it) && !isAppInLibrary(it) })
                    .filter { (it.sourceType != SourceType.APP || isAndroidGame(it)) && lastPlayedAt.containsKey(it.stableKey) }
                    .sortedByDescending { lastPlayedAt[it.stableKey] ?: 0L }
            specialFilter == SpecialFilter.MOST_PLAYED ->
                library.filter { (playtime[it.stableKey] ?: 0L) > 0L }
                    .sortedByDescending { playtime[it.stableKey] ?: 0L }
            specialFilter == SpecialFilter.MASTERED -> library.filter { game ->
                val award = game.raGameId?.let { RaAward.from(raCompletionById[it]?.HighestAwardKind) }
                award == RaAward.MASTERED || award == RaAward.COMPLETED
            }
            // Started (at least one unlock) but not mastered yet.
            specialFilter == SpecialFilter.IN_PROGRESS -> library.filter { game ->
                val entry = game.raGameId?.let { raCompletionById[it] } ?: return@filter false
                val award = RaAward.from(entry.HighestAwardKind)
                val earned = maxOf(entry.NumAwarded ?: 0, entry.NumAwardedHardcore ?: 0)
                earned > 0 && award != RaAward.MASTERED && award != RaAward.COMPLETED
            }
            specialFilter == SpecialFilter.ALMOST_MASTERED -> library.mapNotNull { game ->
                val entry = game.raGameId?.let { raCompletionById[it] } ?: return@mapNotNull null
                val award = RaAward.from(entry.HighestAwardKind)
                val max = entry.MaxPossible ?: 0
                val earned = maxOf(entry.NumAwarded ?: 0, entry.NumAwardedHardcore ?: 0)
                if (max <= 0 || earned <= 0 || award == RaAward.MASTERED || award == RaAward.COMPLETED) null
                else game to earned.toFloat() / max
            }.sortedByDescending { it.second }.map { it.first }
            specialFilter == SpecialFilter.NEVER_PLAYED -> library.filter { g ->
                g.sourceType != SourceType.APP && !lastPlayedAt.containsKey(g.stableKey) && (playtime[g.stableKey] ?: 0L) == 0L
            }
            specialFilter == SpecialFilter.FORGOTTEN -> {
                val cutoff = System.currentTimeMillis() - 60L * 24 * 60 * 60 * 1000
                library.filter { g -> (lastPlayedAt[g.stableKey] ?: Long.MAX_VALUE) < cutoff }
                    .sortedBy { lastPlayedAt[it.stableKey] ?: 0L }
            }
            specialFilter == SpecialFilter.REDISCOVER -> {
                val cutoff = System.currentTimeMillis() - 182L * 24 * 60 * 60 * 1000
                library.filter { g -> (lastPlayedAt[g.stableKey] ?: Long.MAX_VALUE) < cutoff }
                    .sortedBy { lastPlayedAt[it.stableKey] ?: 0L }
            }
            // (Update 200: games only here too, like Continue playing.)
            specialFilter == SpecialFilter.SMART_CONTINUE ->
                smartContinue(library.filter { it.sourceType != SourceType.APP || isAndroidGame(it) })
            // Update 132: every game with achievements — last played first, then the rest by name.
            specialFilter == SpecialFilter.TROPHIES -> library.filter { it.raGameId != null }
                .sortedWith(compareByDescending<Game> { lastPlayedAt[it.stableKey] ?: 0L }.thenBy { sortName(it.name) })
            // Your collections, franchises and play statuses.
            isCollectionKey(platformFilter) -> {
                val keys = collections.firstOrNull { it.key == platformFilter }?.keys.orEmpty()
                library.filter { it.stableKey in keys }
            }
            // "Apps" = your other apps (Spotify, YouTube…); game apps live under "Android games" only.
            platformFilter == "App" -> library.filter { it.platform == "App" && !isAndroidGame(it) }
            platformFilter != null -> library.filter { it.platform == platformFilter }
            else -> library
        }
        val searched = byCategory.filter {
            searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true)
        }
        // Recently played / Most played keep their own order, unless
        // you picked a sort for that category yourself.
        return if ((specialFilter == SpecialFilter.RECENT || specialFilter == SpecialFilter.MOST_PLAYED ||
                specialFilter == SpecialFilter.ALMOST_MASTERED || specialFilter == SpecialFilter.SMART_CONTINUE ||
                specialFilter == SpecialFilter.REDISCOVER || specialFilter == SpecialFilter.TROPHIES) && !categorySort.containsKey(sortCategoryKey)
        ) searched
        else sortGames(searched)
    }

    /**
     * Smart Continue: a short list, best pick first — what you're playing now (last 3 days), games close to
     * completion (75 %+ or 5 or fewer left), then ones with a few achievements remaining, then games you
     * were really into before (2 h+, not in the last month). Each game once.
     */
    private fun smartContinue(library: List<Game>): List<Game> {
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        fun progress(g: Game): Pair<Int, Int>? {
            val e = g.raGameId?.let { raCompletionById[it] } ?: return null
            val max = e.MaxPossible ?: 0
            if (max <= 0) return null
            val award = RaAward.from(e.HighestAwardKind)
            if (award == RaAward.MASTERED || award == RaAward.COMPLETED) return null
            return maxOf(e.NumAwarded ?: 0, e.NumAwardedHardcore ?: 0) to max
        }
        val current = library.filter { now - (lastPlayedAt[it.stableKey] ?: 0L) < 3 * day }
            .sortedByDescending { lastPlayedAt[it.stableKey] ?: 0L }
        val near = library.mapNotNull { g -> progress(g)?.let { (e, m) -> if (e > 0 && (e * 100 / m >= 75 || m - e <= 5)) g to e.toFloat() / m else null } }
            .sortedByDescending { it.second }.map { it.first }
        val few = library.mapNotNull { g -> progress(g)?.let { (e, m) -> if (e > 0 && m - e in 6..12) g to m - e else null } }
            .sortedBy { it.second }.map { it.first }
        val before = library.filter { g ->
            val last = lastPlayedAt[g.stableKey] ?: return@filter false
            now - last > 30 * day && (playtime[g.stableKey] ?: 0L) >= 2 * 3_600_000L
        }.sortedByDescending { playtime[it.stableKey] ?: 0L }
        return (current + near + few + before).distinctBy { it.stableKey }.take(40)
    }

    /** Games in the order picked under Settings › Library › Sort games by. */
    fun sortGames(list: List<Game>, mode: com.shelf.launcher.data.SortMode = effectiveSortMode): List<Game> {
        val byName = compareBy<Game> { sortName(it.name) }
        fun progress(g: Game): Float {
            val e = g.raGameId?.let { raCompletionById[it] } ?: return -1f
            val max = e.MaxPossible ?: return -1f
            if (max <= 0) return -1f
            return maxOf(e.NumAwarded ?: 0, e.NumAwardedHardcore ?: 0).toFloat() / max
        }
        fun awardRank(g: Game): Int = when (g.raGameId?.let { RaAward.from(raCompletionById[it]?.HighestAwardKind) }) {
            RaAward.MASTERED -> 0
            RaAward.COMPLETED -> 1
            RaAward.BEATEN_HARDCORE -> 2
            RaAward.BEATEN -> 3
            null -> 4
        }
        fun meta(g: Game) = metadata[g.stableKey]
        // Games without the value go last, in name order.
        return when (mode) {
            com.shelf.launcher.data.SortMode.NAME -> list.sortedWith(byName)
            com.shelf.launcher.data.SortMode.NAME_DESC -> list.sortedWith(byName.reversed())
            com.shelf.launcher.data.SortMode.NEWLY_ADDED ->
                list.sortedWith(compareByDescending<Game> { firstSeen[it.stableKey] ?: 0L }.then(byName))
            com.shelf.launcher.data.SortMode.FAVORITES_FIRST ->
                list.sortedWith(compareBy<Game> { it.stableKey !in favorites }.then(byName))
            com.shelf.launcher.data.SortMode.RECENT ->
                list.sortedWith(compareByDescending<Game> { lastPlayedAt[it.stableKey] ?: 0L }.then(byName))
            com.shelf.launcher.data.SortMode.PLAYTIME ->
                list.sortedWith(compareByDescending<Game> { playtime[it.stableKey] ?: 0L }.then(byName))
            com.shelf.launcher.data.SortMode.PROGRESS ->
                list.sortedWith(compareByDescending<Game> { progress(it) }.then(byName))
            com.shelf.launcher.data.SortMode.MASTERED ->
                list.sortedWith(compareBy<Game> { awardRank(it) }.thenByDescending { progress(it) }.then(byName))
            com.shelf.launcher.data.SortMode.METACRITIC ->
                list.sortedWith(compareByDescending<Game> { meta(it)?.metacritic ?: -1 }.then(byName))
            com.shelf.launcher.data.SortMode.CRITIC ->
                list.sortedWith(compareByDescending<Game> { meta(it)?.let { m -> m.metacritic ?: m.criticScore } ?: -1 }.then(byName))
            com.shelf.launcher.data.SortMode.NEWEST ->
                list.sortedWith(compareByDescending<Game> { meta(it)?.releaseYear ?: Int.MIN_VALUE }.then(byName))
            com.shelf.launcher.data.SortMode.OLDEST ->
                list.sortedWith(compareBy<Game> { meta(it)?.releaseYear ?: Int.MAX_VALUE }.then(byName))
            com.shelf.launcher.data.SortMode.SHORTEST ->
                list.sortedWith(compareBy<Game> { meta(it)?.let { m -> m.normallyHours ?: m.hastilyHours } ?: Double.MAX_VALUE }.then(byName))
            com.shelf.launcher.data.SortMode.PLATFORM -> list.sortedWith(compareBy<Game> { it.platform }.then(byName))
        }
    }

    /** Distinct platforms across the full library, for the filter chips —
     * computed from `games` (not `visibleGames`) so switching filters
     * doesn't make other options disappear. */
    val availablePlatforms: List<String> by lazy {
        val key = MemoKey(games, libraryApps, gameAppPackages, knownEmulatorPackages, emulatorConfigs)
        platformsMemo.getOrCompute(key) {
            val platforms = games.map { it.platform }.distinct()
                // "Other apps" only when there's an app that isn't a game.
                .filter { p -> p != "App" || games.any { it.platform == "App" && !isAndroidGame(it) } }
            val android = if (games.any { isAndroidGame(it) }) listOf(ANDROID_GAMES_PLATFORM) else emptyList()
            (platforms + android).distinct().sorted()
        }
    }

    private companion object {
        val visibleMemo = IdentityMemo<List<Game>>(48)
        val platformsMemo = IdentityMemo<List<String>>(4)
    }
}

/** Inputs compared by reference (strings by value), so a state copy that
 * leaves them alone matches the earlier one without walking any list. */
internal class MemoKey(private vararg val refs: Any?) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MemoKey || other.refs.size != refs.size) return false
        for (i in refs.indices) {
            val a = refs[i]; val b = other.refs[i]
            if (a === b) continue
            if (a is String && b is String && a == b) continue
            return false
        }
        return true
    }

    override fun hashCode(): Int {
        var h = 1
        for (r in refs) h = h * 31 + (if (r is String) r.hashCode() else System.identityHashCode(r))
        return h
    }
}

/** A small thread-safe LRU of results by [MemoKey]. Several keys are kept
 * because the category screen asks for every category in turn. */
internal class IdentityMemo<V : Any>(private val max: Int) {
    private val map = object : LinkedHashMap<MemoKey, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<MemoKey, V>?) = size > max
    }

    fun getOrCompute(key: MemoKey, compute: () -> V): V {
        synchronized(map) { map[key] }?.let { return it }
        val value = compute()
        synchronized(map) { map[key] = value }
        return value
    }
}

/** The letter a game files under for quick jumps: A-Z, or '#' for the rest.
 * Leading "The " is ignored, so "The Legend of Zelda" is under L. */
fun quickJumpLetter(name: String): Char {
    val trimmed = name.trim().removePrefix("The ").removePrefix("the ")
    val first = trimmed.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: return '#'
    return if (first in 'A'..'Z') first else '#'
}

/** Whether an app shows its own icon instead of scraped art. */
fun LauncherUiState.usesOwnIcon(game: Game): Boolean =
    game.sourceType == SourceType.APP &&
        (libraryApps.alwaysUseIcons || game.packageName in libraryApps.iconArt)

/**

 * The state as the screen should draw it: apps set to use their own icon have
 * their scraped art hidden, so every screen (dock, background, details,
 * screensaver) falls back to the app-icon look. The art itself isn't deleted —
 * switching back brings it straight back.
 */
/** Semaphore.withPermit, spelled out (keeps the import list unchanged). */
fun LauncherUiState.forDisplay(): LauncherUiState {
    if (!libraryApps.alwaysUseIcons && libraryApps.iconArt.isEmpty()) return this
    // The trimmed art map is reused while games / art / app settings are the same
    // objects, so a focus step doesn't rebuild it.
    val shown = displayArtMemo.getOrCompute(MemoKey(games, art, libraryApps)) {
        val hidden = games.filter { usesOwnIcon(it) }.map { it.stableKey }.toSet()
        // Only scraped art is hidden: artwork you picked yourself for an app
        // (Start › Artwork) always shows, even with "use app icon" on.
        if (hidden.isEmpty()) art else art - hidden
    }
    return if (shown === art) this else copy(art = shown)
}

private val displayArtMemo = IdentityMemo<Map<String, GameArt>>(4)
