package com.shelf.launcher

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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock

/** Raichu-yellow blink for achievement unlocks. */
private const val UNLOCK_YELLOW = 0xFFFFD84A.toInt()

/** Update 175: games looked up at once while scraping artwork, details, and cases / screenshots / clips. */
private const val ART_LANES = 4
private const val DETAIL_LANES = 3
private const val DETAIL_LANE_PAUSE_MS = 250L
private const val MEDIA_LANES = 3
/** Update 207: pictures saved at once by Save all (ScreenScraper's own stay within its account limit). */
private const val SAVE_LANES = 4

/** Package-name prefixes of studios that only publish games. */
private val GAME_PUBLISHER_PREFIXES = listOf(
    "com.supercell.", "com.mojang.", "com.miHoYo.", "com.HoYoverse.", "com.YoStar", "com.nintendo.",
    "com.rockstargames.", "com.ea.game.", "com.ea.gp.", "com.gameloft.", "com.netease.", "com.tencent.ig",
    "com.tencent.tmgp", "com.activision.", "com.epicgames.", "com.ubisoft.", "com.square_enix.", "com.square-enix.",
    "com.bandainamcoent.", "com.sega.", "com.king.", "com.roblox.", "com.innersloth.", "com.devolver.",
    "com.playdigious.", "com.nekki.", "com.chucklefish.", "com.noodlecake.", "com.kongregate.", "com.hypergryph.",
    "com.levelinfinite.", "com.proximabeta.", "com.pubg.", "com.garena.game", "com.dts.freefire", "com.riotgames.",
    "com.kabam.", "com.zynga.", "com.playrix.", "com.miniclip.", "com.halfbrick.", "com.rovio.", "com.ketchapp.",
    "com.voodoo.", "com.fingersoft.", "com.bethsoft.", "com.feralinteractive.", "com.aspyr.", "com.snkplaymore.",
    "com.capcom.", "jp.co.capcom.", "jp.konami.", "com.konami.", "com.koeitecmo.", "com.aniplex.", "com.bilibili.",
    "com.kurogame.", "com.nexon.", "com.ncsoft.", "com.netmarble.", "com.com2us.", "com.gravity.", "com.lilithgames.",
    "com.igg.", "com.scopely.", "com.pixelfederation.", "com.twodboy.", "com.ninjakiwi.", "com.mobirix.",
    "com.hottapkgs.", "com.seasun.", "com.bluepoch.", "com.shiftup.", "com.kakaogames.", "com.klab.", "com.cygames.",
    "jp.co.cygames.", "com.aksys.", "com.xd.", "com.tapblaze.", "com.crescentmoongames.", "com.frogmind.",
    "com.yodo1.", "com.devsisters.", "com.StudioAtlas", "com.dotemu.", "com.limbic.", "com.playdead.", "com.thatgamecompany."
)

/** Native libraries only game engines ship. */
private val GAME_ENGINE_LIBS = listOf(
    "libunity.so", "libil2cpp.so", "libUE4.so", "libUnreal.so", "libgodot_android.so", "libyoyo.so",
    "libcocos2dcpp.so", "libcocos2djs.so", "libcocos.so", "libgdx.so", "libgdx-box2d.so", "libRenPy.so",
    "libmain.so", "libfmod.so", "libfmodstudio.so", "libgame.so", "libdefold.so", "libsolar2d.so", "libcorona.so",
    "libxenko.so", "libstride.so", "libsfml-graphics.so", "libSDL2.so", "libsdl2.so"
)

/** What the achievement readout on a card shows. */
class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SteamGridDbRepository(createSteamGridDbApi())
    private val raRepository = RetroAchievementsRepository(createRetroAchievementsApi())
    private val emulatorStore = EmulatorSettingsStore(application)
    private val favoritesStore = FavoritesStore(application)
    private val firstSeenStore = com.shelf.launcher.data.FirstSeenStore(application)
    private val categorySortStore = com.shelf.launcher.data.CategorySortStore(application)
    private val artOverridesStore = ArtOverridesStore(application)
    private val gameProfileStore = com.shelf.launcher.data.GameProfileStore(application)
    /** Bumped on every profile change so Compose readers of [effectiveProfile] recompute. */
    private val _profileVersion = MutableStateFlow(0)
    val profileVersion: StateFlow<Int> = _profileVersion
    private val heroUpscaleStore = com.shelf.launcher.data.HeroUpscaleStore(application)
    private val artCacheStore = ArtCacheStore(application)
    private val themeStore = ThemeStore(application)
    private val shortcutsStore = ShortcutsStore(application)
    private val raMatchStore = RaMatchStore(application)
    private val raGameListCache = RaGameListCache(application)
    // A match request that arrives while one is running (e.g. the startup
    // run racing the first ROM scan) is queued rather than dropped.
    private var raMatchRerun = false
    private var raMatchRerunForce = false
    private val preferencesStore = PreferencesStore(application)
    private val tgdbKeyStore = TgdbKeyStore(application)
    private val metadataSourceStore = MetadataSourceStore(application)
    private val metadataCacheStore = MetadataCacheStore(application)
    private val scrapeOptionsStore = com.shelf.launcher.data.ScrapeOptionsStore(application)
    private val tgdbRepository = TheGamesDbRepository(createTheGamesDbApi())
    private val screenScraperStore = ScreenScraperStore(application)
    // (Update 202: the manual addresses it notes are loaded first — ManualLinks.)
    private val screenScraperRepository = ScreenScraperRepository(createScreenScraperApi()).also { com.shelf.launcher.data.ManualLinks.init(application) }
    private val igdbCredentialsStore = IgdbCredentialsStore(application)
    private val igdbRepository = IgdbRepository(createIgdbApi())
    private val rawgKeyStore = com.shelf.launcher.data.RawgKeyStore(application)
    private val rawgRepository = com.shelf.launcher.data.RawgRepository(com.shelf.launcher.data.createRawgApi())
    private val rawgTried = mutableSetOf<String>()
    private val sgdbKeyStore = SgdbKeyStore(application)
    private val libraryStore = LibrarySettingsStore(application)
    private val overridesStore = GameOverridesStore(application)
    private val hiddenAppsStore = HiddenAppsStore(application)
    private val appDisplayStore = com.shelf.launcher.data.AppDisplayStore(application)
    private val steamGamesStore = SteamGamesStore(application)
    private val gameNativeStore = GameNativeSettingsStore(application)
    private val recentlyPlayedStore = RecentlyPlayedStore(application)
    private val playtimeStore = PlaytimeStore(application)
    private val screensaverStore = ScreensaverStore(application)
    private val artStorageStore = ArtStorageStore(application)
    private val appearanceStore = AppearanceStore(application)
    private val screenLayoutsStore = com.shelf.launcher.data.ScreenLayoutsStore(application)
    // Declared before the UI state: the start category reads it.
    /** Set when Voltcap goes to the background, to tell a quick return
     * (home button, Android closing it for memory) from a real new start. */
    private val sessionPrefs = application.getSharedPreferences("session", android.content.Context.MODE_PRIVATE)

    // ---- Coming back after a game or app (see LaunchContextStore) ----
    private val launchContextStore = com.shelf.launcher.data.LaunchContextStore(application)
    /** Set only when this process was (re)started after Voltcap itself launched something, within 12 h. */
    private val restoreCtx: com.shelf.launcher.data.LaunchContext? =
        (if (com.shelf.launcher.CrashGuard.safeMode) null else runCatching { launchContextStore.loadPendingReturn() }.getOrNull())
            .also { if (it != null) launchContextStore.clearPending() }
    /** True from a restore until the saved game/category has been found (or given up on). */
    private var restoreActive = restoreCtx != null
    /** The category screen and details window live here, not in Compose, so they survive
     * activity recreation (display change, docking) and can be restored after process death. */
    val categoryScreenOpenState = androidx.compose.runtime.mutableStateOf(
        restoreCtx?.categoryScreenOpen
            ?: (appearanceStore.load().categoryScreen == com.shelf.launcher.data.CategoryScreenMode.AT_START)
    )
    val detailsOpenState = androidx.compose.runtime.mutableStateOf(false)
    /** Game whose details window was open at launch; the screen reopens it once that game is focused. */
    val pendingDetailsKeyState = androidx.compose.runtime.mutableStateOf<String?>(
        restoreCtx?.takeIf { it.detailsOpen }?.detailsKey
    )
    private var introTaken = false
    private var ownLaunchPending = false
    private var lastLaunchAt = 0L

    /** The intro plays once per process and never on the way back from a game. */
    fun takeIntroSlot(): Boolean {
        val first = !introTaken
        introTaken = true
        return first && restoreCtx == null
    }

    private val libraryAppsStore = LibraryAppsStore(application)
    private val categoryStore = CategoryStore(application)
    // The game that was selected last time, restored once the library has loaded.
    private var pendingFocusKey: String? = null
    private val raDataCache = RaDataCache(application)
    private val videoUrlStore = VideoUrlStore(application)
    private val raDetailsInFlight = mutableSetOf<String>()
    // When each saved achievement list was fetched and for which RA game (main thread only).
    private val raDetailsMeta = mutableMapOf<String, RaDataCache.DetailsMeta>()
    private val raMissedMarksStore = com.shelf.launcher.data.ra.MissedMarksStore(application)
    private var unlockWatchGameId: Int? = null
    private var artSaveJob: Job? = null
    private val raCredentialsStore = RaCredentialsStore(application)

    // Tracked separately so rescanning/editing one source doesn't wipe the
    // others out — the displayed `games` list is always the merge of all three.
    private var currentRomGames: List<Game> = emptyList()
    private var currentAppGames: List<Game> = emptyList() // hidden apps already excluded
    private var allScannedApps: List<Game> = emptyList()  // unfiltered, needed to unhide later
    private var currentSteamGames: List<Game> = emptyList()
    private var scrapeJob: Job? = null
    /** Update 144: the saved SteamGridDB key checked again after a start without a connection ([fetchAllArt]). */
    private var sgdbRetryJob: Job? = null
    private var metadataScrapeJob: Job? = null
    private var manualScrapeJob: Job? = null
    /** Games whose manual was already looked for (so the automatic pass asks ScreenScraper once per game). */
    private val manualPrefs by lazy { getApplication<Application>().getSharedPreferences("manual_scrape", android.content.Context.MODE_PRIVATE) }

    // ---- Achievement unlocks while playing ----
    private var unlockWatchJob: Job? = null
    private val seenUnlockIds = mutableSetOf<Int>()
    private val unlocksThisSession = mutableListOf<com.shelf.launcher.data.RaRecentAchievement>()

    /**
     * While a game runs, asks RetroAchievements every 45 s for new unlocks
     * and blinks the stick lights for each (AYN lights work in the
     * background). Stops when Shelf comes back.
     */
    private fun startUnlockWatch(raGameId: Int? = null) {
        val creds = raCredentialsStore.load() ?: return
        unlockWatchJob?.cancel()
        unlocksThisSession.clear()
        unlockWatchGameId = raGameId
        unlockWatchJob = viewModelScope.launch {
            // Everything already unlocked counts as seen. (update 91) A failed look-up (offline as the game
            // starts) is tried again before the first check, so older unlocks never pop up as new ones.
            val watchFrom = System.currentTimeMillis()
            var seeded = seedSeenUnlocks(creds, watchFrom)
            var lastStatus: String? = null
            // Battery: the watch lives at most 6 h, and does no network work while the screen is
            // off (after 10 min of that it only re-checks the screen once a minute until it is on again).
            val startedAt = android.os.SystemClock.elapsedRealtime()
            var screenOffSince = 0L
            while (isActive) {
                val offMinutes = if (screenOffSince == 0L) 0L else (android.os.SystemClock.elapsedRealtime() - screenOffSince) / 60_000L
                // Quicker while the second screen shows the game (its unlock cards should feel live).
                kotlinx.coroutines.delay(
                    when {
                        offMinutes >= 10 -> 60_000L
                        com.shelf.launcher.ui.CompanionBridge.open -> 15_000L
                        // While a game runs, a little less often (GameMode): one small request a minute.
                        com.shelf.launcher.data.GameMode.active -> 60_000L
                        else -> 45_000L
                    }
                )
                if (android.os.SystemClock.elapsedRealtime() - startedAt > 6 * 60 * 60_000L) break
                if (!com.shelf.launcher.ui.ScreenState.isInteractive(getApplication())) {
                    if (screenOffSince == 0L) screenOffSince = android.os.SystemClock.elapsedRealtime()
                    // Locked / asleep: no timer at all — wait for the screen to come back on (or the 6 h cap).
                    val left = 6 * 60 * 60_000L - (android.os.SystemClock.elapsedRealtime() - startedAt)
                    if (left <= 0L) break
                    kotlinx.coroutines.withTimeoutOrNull(left) {
                        androidx.compose.runtime.snapshotFlow { com.shelf.launcher.ui.ScreenState.interactive.value }
                            .first { it }
                    }
                    continue
                }
                screenOffSince = 0L
                if (!seeded) seeded = seedSeenUnlocks(creds, watchFrom)
                if (seeded) checkNewUnlocks(celebrate = true)
                // The game's own status line (Rich Presence) onto Discord.
                val a = _uiState.value.appearance
                if (raGameId != null && a.discordPresence && a.discordRichPresence) {
                    val profile = withContext(Dispatchers.IO) { raRepository.fetchProfile(creds.username, creds.apiKey) }
                    val status = profile?.takeIf { it.LastGameID == raGameId }?.RichPresenceMsg?.takeIf { it.isNotBlank() }
                    if (status != null && status != lastStatus) {
                        lastStatus = status
                        com.shelf.launcher.data.DiscordPresence.showStatus(status)
                    }
                    if (profile != null) _uiState.update { it.copy(raProfile = profile) }
                }
                // Follow the walkthrough only while the second screen shows it (at most once a minute).
                val gg = guideWatchGame
                val ap = _uiState.value.appearance
                if (gg != null && ap.useSecondScreen && effectiveProfile(gg).secondScreenPage.value == com.shelf.launcher.data.SecondPage.WALKTHROUGH &&
                    System.currentTimeMillis() - lastGuideLocateAt >= 60_000
                ) relocateGuideQuietly(gg)
            }
        }
    }

    /** (update 91) Marks the unlocks of the 4 h before [untilMs] as seen; false when RetroAchievements couldn't be reached. */
    private suspend fun seedSeenUnlocks(creds: RaCredentials, untilMs: Long): Boolean {
        val before = raRepository.fetchEarnedBetween(creds.username, creds.apiKey, untilMs - 240 * 60_000L, untilMs) ?: return false
        seenUnlockIds += before.mapNotNull { it.AchievementID }
        return true
    }

    // ---- RetroAchievements overlay (Settings › Overlay) ----
    private var raOverlayJob: kotlinx.coroutines.Job? = null
    private var raOverlayKey: String? = null
    private var raOverlayLastUnlock: String? = null

    /** Keeps the overlay's data in step with [game]'s achievement list (saved list first, then a fresh one). */
    private fun followRaOverlay(game: Game) {
        raOverlayJob?.cancel()
        raOverlayKey = game.stableKey
        raOverlayLastUnlock = null
        val title = com.shelf.launcher.ui.gameDisplayName(game.name)
        refreshAchievementDetails(game, force = false)
        raOverlayJob = viewModelScope.launch {
            _uiState.map { it.raAchievementDetails[game.stableKey] to it.raMissedMarks }
                .distinctUntilChanged()
                .collect { (list, missed) ->
                    com.shelf.launcher.data.RaOverlayData.publish(title, list, missed.toSet(), raOverlayLastUnlock, game.raGameId)
                }
        }
    }

    /** New unlocks for the game the overlay follows: marked unlocked in its list (the overlay updates). */
    private fun markOverlayUnlocks(fresh: List<com.shelf.launcher.data.RaRecentAchievement>) {
        val key = raOverlayKey ?: return
        val list = _uiState.value.raAchievementDetails[key] ?: return
        val ids = fresh.mapNotNull { it.AchievementID }.toSet()
        if (list.none { it.id in ids }) return
        raOverlayLastUnlock = fresh.lastOrNull { it.AchievementID in list.map { a -> a.id } }?.Title
        // (update 91) In UTC, like RetroAchievements' own dates (GameJourney.parseRa reads them as UTC).
        val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())
        val updated = list.map { if (it.id in ids && !it.isUnlocked) it.copy(dateEarned = now) else it }
        _uiState.update { it.copy(raAchievementDetails = it.raAchievementDetails + (key to updated)) }
    }

    private suspend fun checkNewUnlocks(celebrate: Boolean): List<com.shelf.launcher.data.RaRecentAchievement> {
        val creds = raCredentialsStore.load() ?: return emptyList()
        val fresh = raRepository.fetchRecentAchievements(creds.username, creds.apiKey, 60)
            .filter { a -> a.AchievementID.let { id -> id != null && id !in seenUnlockIds } }
        if (fresh.isEmpty()) return fresh
        seenUnlockIds += fresh.mapNotNull { it.AchievementID }
        unlocksThisSession += fresh
        // NEW once: these play their unlock animation the next time the game's achievements are opened.
        fresh.groupBy { it.GameID }.forEach { (gid, list) ->
            if (gid != null) com.shelf.launcher.data.AchievementSeen.addFresh(
                getApplication(), com.shelf.launcher.data.AchievementSeen.keyRa(gid), list.mapNotNull { it.AchievementID }
            )
        }
        markOverlayUnlocks(fresh)
        // Into the "recently unlocked" list at once (the Trophy vault and the backdrop badges show them now,
        // not at the next refresh).
        _uiState.update { st ->
            st.copy(raRecentAchievements = (fresh + st.raRecentAchievements).distinctBy { it.AchievementID ?: it.hashCode() })
        }
        // A game these could have just finished: ask RetroAchievements for the award (mastered / completed).
        val completion = _uiState.value.raCompletionById
        val mayFinish = fresh.groupBy { it.GameID }.any { (gid, list) ->
            val e = gid?.let { completion[it] } ?: return@any false
            val max = e.MaxPossible ?: return@any false
            max > 0 && (e.NumAwarded ?: 0) + list.size >= max && e.HighestAwardKind != "mastered"
        }
        if (mayFinish) refreshRaCompletion()
        // The in-game second screen shows a card for each.
        fresh.forEach { com.shelf.launcher.ui.CompanionBridge.unlocked(it) }
        if (celebrate) celebrateUnlock()
        // A pop-up per unlock, over the emulator.
        if (celebrate && _uiState.value.appearance.unlockPopups) {
            fresh.forEach { com.shelf.launcher.data.UnlockNotifier.show(getApplication(), it) }
        }
        return fresh
    }

    // ---- Steam unlocks while playing (for the Trophy vault) ----
    private var steamWatchJob: Job? = null
    private var steamWatchApp: Int? = null

    /**
     * While a Steam game runs (and a Steam account is connected), re-reads its achievements every 90 s with
     * the screen on, so new unlocks reach the Trophy vault (and blink the lights) without waiting.
     */
    private fun startSteamWatch(appId: Int) {
        val app = getApplication<Application>()
        if (com.shelf.launcher.data.SteamAchievements.account(app) == null) return
        steamWatchJob?.cancel()
        steamWatchApp = appId
        steamWatchJob = viewModelScope.launch {
            // What's unlocked already (never counted as new).
            com.shelf.launcher.data.SteamTrophies.refreshGame(app, appId)
            val startedAt = android.os.SystemClock.elapsedRealtime()
            while (isActive && android.os.SystemClock.elapsedRealtime() - startedAt < 6 * 60 * 60_000L) {
                kotlinx.coroutines.delay(90_000L)
                if (!com.shelf.launcher.ui.ScreenState.isInteractive(app)) continue
                val fresh = com.shelf.launcher.data.SteamTrophies.refreshGame(app, appId)
                if (fresh.isNotEmpty()) celebrateUnlock()
            }
        }
    }

    /** Back in Voltcap: one last look at the Steam game just played. */
    private fun finishSteamWatch() {
        val appId = steamWatchApp ?: return
        steamWatchJob?.cancel()
        steamWatchJob = null
        steamWatchApp = null
        viewModelScope.launch { com.shelf.launcher.data.SteamTrophies.refreshGame(getApplication(), appId) }
    }

    /** Yellow blinks on the controller lights, then back to the chosen colour. */
    fun celebrateUnlock() {
        val a = _uiState.value.appearance
        if (!a.flashOnUnlock || !a.lightsEnabled) return
        viewModelScope.launch(Dispatchers.Default) {
            com.shelf.launcher.data.ControllerLights.flash(
                getApplication(), UNLOCK_YELLOW, times = 3,
                restoreColor = com.shelf.launcher.data.LightsTheme.resolve(a.lightsColor), restoreBrightness = a.lightsBrightness
            )
        }
    }

    /** Back in Shelf after playing: last check, then say what was unlocked. */
    private fun finishUnlockWatch() {
        val job = unlockWatchJob ?: return
        refreshRaProfile() // the game's latest Rich Presence line
        // One last look at where the guide stands now that the session is over.
        guideWatchGame?.let { relocateGuideQuietly(it) }
        guideWatchGame = null
        unlockWatchJob = null
        val playedRaGame = unlockWatchGameId
        unlockWatchGameId = null
        job.cancel()
        viewModelScope.launch {
            checkNewUnlocks(celebrate = false)
            // Unlocks change the played game's progress and which missables are still open.
            playedRaGame?.let { refreshAfterSession(it) }
            val unlocked = unlocksThisSession.toList()
            unlocksThisSession.clear()
            if (unlocked.isEmpty()) return@launch
            val titles = unlocked.mapNotNull { it.Title }.take(3)
            val names = titles.joinToString(", ")
            // A trophy in the status bar until the game's achievements are opened.
            val playedKey = _uiState.value.sessionSummary?.gameKey
                ?: playedRaGame?.let { id -> _uiState.value.games.firstOrNull { it.raGameId == id }?.stableKey }
            if (playedKey != null) {
                _uiState.update { it.copy(returnTrophies = com.shelf.launcher.data.ReturnTrophies(playedKey, playedRaGame, unlocked.size)) }
            }
            val rec = playedRaGame?.let { com.shelf.launcher.data.AchievementSeen.get(getApplication(), com.shelf.launcher.data.AchievementSeen.keyRa(it)) }
            val total = playedRaGame?.let { id -> _uiState.value.raCompletionById[id]?.MaxPossible?.takeIf { it > 0 } } ?: rec?.total ?: 0
            val before = rec?.seen?.size ?: (playedRaGame?.let { id -> _uiState.value.raCompletionById[id]?.NumAwarded } ?: 0)
            val gained = unlocked.count { a -> rec == null || a.AchievementID !in rec.seen }
            val after = (before + gained).coerceAtMost(if (total > 0) total else Int.MAX_VALUE)
            // Into the journal: Recap, Stats and Game Journey read it.
            if (playedKey != null) {
                val gameName = _uiState.value.games.firstOrNull { it.stableKey == playedKey }?.name?.let { com.shelf.launcher.ui.stripRegionTags(it) }
                val badges = unlocked.mapNotNull { com.shelf.launcher.data.RaBadges.url(it.BadgeName) }
                withContext(Dispatchers.IO) {
                    com.shelf.launcher.data.PlayJournal.unlocks(
                        getApplication(), playedKey, gameName, unlocked.size, badges,
                        after, total, if (total > 0) before * 100 / total else -1, if (total > 0) after * 100 / total else -1
                    )
                }
            }
            // A session card is on screen: the unlocks go on it instead of a second message.
            if (_uiState.value.sessionSummary != null) {
                _uiState.update { st ->
                    st.copy(sessionSummary = st.sessionSummary?.let { ss ->
                        ss.copy(
                            achievementCount = unlocked.size, achievementTitles = titles,
                            achievementBadges = unlocked.mapNotNull { com.shelf.launcher.data.RaBadges.url(it.BadgeName) }.take(6),
                            unlockedAfter = after, achievementTotal = total,
                            percentBefore = if (total > 0) before * 100 / total else -1,
                            percentAfter = if (total > 0) after * 100 / total else -1
                        ).withMilestone()
                    })
                }
                _uiState.value.sessionSummary?.let { ss ->
                    if (com.shelf.launcher.ui.SessionSummaryPrefs.enabled(getApplication())) showSessionNote(ss)
                    ss.milestone?.let { m -> withContext(Dispatchers.IO) { com.shelf.launcher.data.PlayJournal.milestone(getApplication(), ss.gameKey, ss.gameName, m) } }
                }
                celebrateUnlock()
                refreshRaCompletion()
                return@launch
            }
            _uiState.update {
                it.copy(
                    statusMessage = if (unlocked.size == 1) "🏆 Unlocked: $names"
                    else "🏆 ${unlocked.size} achievements unlocked: $names" + if (unlocked.size > 3) "…" else ""
                )
            }
            celebrateUnlock()
            refreshRaCompletion()
        }
    }

    private val _uiState = MutableStateFlow(
        LauncherUiState(
            games = applyOverrides(currentRomGames),
            emulatorConfigs = emulatorStore.loadAll(),
            romRootPath = libraryStore.loadRootPath(),
            romRootPaths = libraryStore.loadRootPaths(),
            deepSearch = libraryStore.loadDeepSearch(),
            isArtKeyEncrypted = sgdbKeyStore.isEncrypted(),
            gameNativeConfig = gameNativeStore.load(),
            lastPlayedAt = recentlyPlayedStore.loadVisible(), // (Update 244: without games taken off Continue playing)
            playtime = playtimeStore.loadTotals(),
            screensaver = screensaverStore.load(),
            artStorage = artStorageStore.load(),
            appearance = appearanceStore.load(),
            libraryApps = libraryAppsStore.load(),
            categories = categoryStore.load(),
            favorites = favoritesStore.loadAll(),
            categorySort = categorySortStore.loadAll(),
            artOverrides = artOverridesStore.loadAll(),
            heroUpscale = heroUpscaleStore.loadAll(),
            art = artCacheStore.loadAll(),
            appTheme = themeStore.load(),
            achievementBadgeMode = if (preferencesStore.badgeModeIsRecent())
                AchievementBadgeMode.RECENT_UNLOCK else AchievementBadgeMode.PROGRESS,
            achievementBadgeNextToTitle = preferencesStore.badgeNextToTitle(),
            // Where it opens: Settings › Library › "Start in" (Recent by default).
            platformFilter = startPlatformFilter(),
            specialFilter = startSpecialFilter(),
            searchQuery = restoreCtx?.searchQuery ?: "",
            focusedIndex = restoreCtx?.focusedIndex?.coerceAtLeast(0) ?: 0,
            igdbConnected = igdbCredentialsStore.load() != null,
            rawgConnected = rawgKeyStore.load() != null,
            artSource = tgdbKeyStore.loadSource(),
            tgdbConnected = tgdbKeyStore.loadKey() != null,
            screenScraperConnected = screenScraperStore.load() != null,
            screenScraperUsername = screenScraperStore.load()?.username,
            metadataSource = metadataSourceStore.loadSource(),
            // Loaded in the background right after start (see init) — reading
            // hundreds of saved entries here held up the first frame.
            metadata = emptyMap(),
            scrapeOptions = scrapeOptionsStore.load(),
            metadataReady = when (metadataSourceStore.loadSource()) {
                MetadataSource.IGDB -> igdbCredentialsStore.load() != null
                MetadataSource.SCREENSCRAPER -> screenScraperStore.load() != null
                MetadataSource.RAWG -> rawgKeyStore.load() != null
                MetadataSource.COMBINED -> igdbCredentialsStore.load() != null || rawgKeyStore.load() != null ||
                    screenScraperStore.load() != null
                MetadataSource.NONE -> false
            },
            isRaKeyEncrypted = raCredentialsStore.isEncrypted(),
            raMatches = raMatchStore.loadEntries(),
            raCustomHashes = raMatchStore.loadCustomHashes(),
            raMissedMarks = raMissedMarksStore.load()
        )
    )
    val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

    /** Shows what was saved from RetroAchievements last time straight away,
     * before (or without) a network round-trip. */
    /** The saved achievement data being read (see [restoreSavedRaData]); RA refreshes wait for it. */
    private var raRestoreJob: kotlinx.coroutines.Job? = null

    /**
     * The achievement data saved last time. Read and parsed off the main thread: with many games it's a
     * large file, and right after a reboot reading it on the main thread held up the first frames.
     */
    private fun restoreSavedRaData() {
        raRestoreJob = viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { raDataCache.load() }
            applySavedRaData(saved)
        }
    }

    private fun applySavedRaData(saved: com.shelf.launcher.data.RaDataCache.Saved) {
        val completion = saved.completion.orEmpty()
        // Lists without fetch info (or from an older cache layout, dropped by the
        // cache itself) can't be trusted to carry missable info — don't show them.
        val meta = saved.detailsMeta.orEmpty()
        val details = saved.details.orEmpty().filterKeys { meta[it]?.gameId != null }
        raDetailsMeta.clear()
        raDetailsMeta.putAll(meta.filterKeys { it in details })
        val now = System.currentTimeMillis()
        _uiState.update {
            it.copy(
                raProfile = saved.profile ?: it.raProfile,
                raProgress = saved.progress ?: it.raProgress,
                raAchievementDetails = if (details.isEmpty()) it.raAchievementDetails else details,
                raDetailsFetchedAt = raDetailsMeta.mapNotNull { (k, m) -> m.fetchedAt?.let { t -> k to t } }.toMap(),
                raDetailsStale = raDetailsMeta.filter { (_, m) -> now - (m.fetchedAt ?: 0L) > RaDataCache.DETAILS_TTL_MS }.keys,
                raCompletionById = completion.mapNotNull { e -> e.GameID?.let { id -> id to e } }.toMap(),
                raProfileStats = RaProfileStats.from(completion)
            )
        }
    }

    /** Refreshes the completion list (awards per game) and saves it. */
    fun refreshRaCompletion() {
        val creds = raCredentialsStore.load() ?: return
        val gen = raGen
        viewModelScope.launch {
            val entries = raRepository.fetchCompletionProgress(creds.username, creds.apiKey) ?: return@launch
            if (gen != raGen) return@launch // the account changed meanwhile: these belong to the old one
            withContext(Dispatchers.IO) { raDataCache.update { it.copy(completion = entries) } }
            _uiState.update {
                it.copy(
                    raCompletionById = entries.mapNotNull { e -> e.GameID?.let { id -> id to e } }.toMap(),
                    raProfileStats = RaProfileStats.from(entries)
                )
            }
        }
    }

    /** Forgets everything saved from RetroAchievements (it's re-fetched while connected). */
    fun clearSavedAchievementData() {
        raDataCache.clear()
        raDetailsMeta.clear()
        _uiState.update {
            it.copy(
                raAchievementDetails = emptyMap(),
                raDetailsError = emptyMap(),
                raDetailsFetchedAt = emptyMap(),
                raDetailsStale = emptySet(),
                raGameTips = emptyMap(),
                raGameTipsError = emptyMap(),
                raComments = emptyMap(),
                raCommentsError = emptyMap(),
                raCommentsHasMore = emptySet(),
                raCompletionById = emptyMap(),
                raProfileStats = RaProfileStats(),
                statusMessage = "Saved achievement data cleared."
            )
        }
        raCredentialsStore.load()?.let { creds ->
            viewModelScope.launch { fetchAllRaProgress(creds, persistOnSuccess = false, savedCredsRestore = true) }
        }
    }

    fun setAppearance(settingsIn: AppearanceSettings) {
        val old = _uiState.value.appearance
        // Update 133: a layout's own settings ("Only for this layout", data/LayoutOnly.kt) are kept before it's left…
        val switching = settingsIn.homeLayout != old.homeLayout
        if (switching) com.shelf.launcher.data.LayoutOnly.leaving(getApplication(), old.homeLayout)
        // Each home layout keeps its own look: park the one you leave, bring in the one you enter.
        val swapped0 = com.shelf.launcher.data.swapLayoutLook(old, settingsIn)
        // (Update 317ah, Toni: "the round shortcuts should be there by default — it should look like the Switch home": the
        // first time Tile home is picked, its round buttons come on under the tiles, whatever another layout left them as.
        // Changed after that in Settings › Home › Round shortcuts, it stays as you set it.)
        val swapped = if (switching && swapped0.homeLayout == com.shelf.launcher.data.HomeLayout.TILES && !com.shelf.launcher.data.Features.tileHomeButtonsSet.value) {
            com.shelf.launcher.data.Features.tileHomeButtonsSet.value = true
            swapped0.copy(tileShortcuts = true, shortcutSpot = com.shelf.launcher.data.ShortcutSpot.ROW)
        } else swapped0
        appearanceStore.save(swapped)
        // …and after the switch the shared settings come back and the next layout's own ones come in.
        val settings = if (switching && com.shelf.launcher.data.LayoutOnly.entered(getApplication(), old.homeLayout, swapped.homeLayout))
            appearanceStore.load() else swapped
        val raLogosTurnedOn = settings.raLogos && !old.raLogos
        // A new global sort re-orders the dock: stay on the same game.
        if (settings.sortMode != old.sortMode) updateKeepingFocus { it.copy(appearance = settings) }
        else _uiState.update { it.copy(appearance = settings) }
        // 0.6.15: sorting by score: the missing Metacritic scores come in from RAWG on their own.
        if (settings.sortMode != old.sortMode && (settings.sortMode == com.shelf.launcher.data.SortMode.METACRITIC ||
                settings.sortMode == com.shelf.launcher.data.SortMode.CRITIC)) fillMetacriticScores()
        if (raLogosTurnedOn) viewModelScope.launch { fetchMissingRaIcons() }
        // Collections switched on/off: rebuild the categories.
        if (settings.userCollections != old.userCollections ||
            settings.franchiseCollections != old.franchiseCollections ||
            settings.genreCollections != old.genreCollections ||
            settings.playStatus != old.playStatus
        ) refreshCollections()
        if (settings.weeklyRecap != old.weeklyRecap) {
            runCatching { com.shelf.launcher.data.RecapWorker.schedule(getApplication(), settings.weeklyRecap) }
        }
    }

    /**
     * Update 261 (#120 / #95 test walks, TestHooks.kt): shows [a] for this session only — nothing is saved, so a restart
     * of Voltcap brings back your own settings exactly.
     */
    fun previewAppearance(a: AppearanceSettings) {
        _uiState.update { it.copy(appearance = a) }
    }

    /** Applies [transform] and keeps the focused game selected if it is still in the (re-sorted) list. */
    private fun updateKeepingFocus(transform: (LauncherUiState) -> LauncherUiState) {
        _uiState.update { s ->
            val key = s.visibleGames.getOrNull(s.focusedIndex)?.stableKey
            val n = transform(s)
            val idx = key?.let { k -> n.visibleGames.indexOfFirst { it.stableKey == k } } ?: -1
            n.copy(focusedIndex = if (idx >= 0) idx else n.focusedIndex.coerceIn(0, (n.visibleGames.size - 1).coerceAtLeast(0)))
        }
    }

    /** Sort for one category ([LauncherUiState.sortCategoryKey]); null = follow the global sort. */
    fun setCategorySort(key: String, mode: com.shelf.launcher.data.SortMode?) {
        updateKeepingFocus { it.copy(categorySort = if (mode == null) it.categorySort - key else it.categorySort + (key to mode)) }
        viewModelScope.launch(Dispatchers.IO) { categorySortStore.set(key, mode) }
        if (mode == com.shelf.launcher.data.SortMode.METACRITIC || mode == com.shelf.launcher.data.SortMode.CRITIC) fillMetacriticScores()
    }

    // ---- 0.6.15: Metacritic scores filled in from RAWG on their own ----
    // Sorting by Metacritic / Critic score with a RAWG key connected (Settings › Accounts): games whose details have no
    // Metacritic score are looked up on RAWG in the background, one at a time, only that score is added. A game RAWG had
    // nothing for isn't asked again for 30 days. RAWG's free key allows 20,000 requests a month; a library of a few
    // hundred games uses a few hundred to a thousand once.
    private val scorePrefs by lazy { getApplication<Application>().getSharedPreferences("metacritic_fill", android.content.Context.MODE_PRIVATE) }
    private var scoreFillJob: kotlinx.coroutines.Job? = null
    /** Progress of the fill-in for the Sort window: (done, total), or null when nothing runs. */

    /** Looks up the missing Metacritic scores on RAWG (when connected). Safe to call again: one run at a time. */
    fun fillMetacriticScores() {
        if (scoreFillJob?.isActive == true) return
        val key = rawgKeyStore.load() ?: return
        scoreFillJob = viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val s0 = _uiState.value
            val todo = s0.games.filter { g ->
                (g.sourceType == SourceType.ROM || s0.isAndroidGame(g)) &&
                    s0.metadata[g.stableKey]?.metacritic == null &&
                    now - scorePrefs.getLong(g.stableKey, 0L) > 30L * 24 * 3600_000L
            }
            if (todo.isEmpty()) return@launch
            var done = 0
            com.shelf.launcher.data.MetacriticFile.rawgProgress.value = 0 to todo.size
            try {
                for (g in todo) {
                    val meta = runCatching { rawgRepository.fetchMetadata(key, g.name, g.platform) }.getOrNull()
                    val score = meta?.metacritic
                    if (score != null) {
                        val before = _uiState.value.metadata[g.stableKey]
                        val merged = (before ?: GameMetadata(
                            title = g.name, summary = null, releaseYear = null, criticScore = null, criticCount = null,
                            genres = emptyList(), developer = null, hastilyHours = null, normallyHours = null, completelyHours = null
                        )).copy(
                            metacritic = score,
                            criticScore = before?.criticScore ?: score
                        )
                        metadataCacheStore.put(g.stableKey, merged)
                        _uiState.update { it.copy(metadata = it.metadata + (g.stableKey to merged)) }
                    } else scorePrefs.edit().putLong(g.stableKey, System.currentTimeMillis()).apply()
                    done++
                    com.shelf.launcher.data.MetacriticFile.rawgProgress.value = done to todo.size
                    // Gentle on RAWG: about one game a second.
                    kotlinx.coroutines.delay(900)
                }
            } finally {
                com.shelf.launcher.data.MetacriticFile.rawgProgress.value = null
            }
        }
    }

    /** One sort for every category: becomes the global sort and clears the per-category picks. */
    fun applySortToAllCategories(mode: com.shelf.launcher.data.SortMode) {
        val settings = _uiState.value.appearance.copy(sortMode = mode)
        appearanceStore.save(settings)
        updateKeepingFocus { it.copy(appearance = settings, categorySort = emptyMap()) }
        viewModelScope.launch(Dispatchers.IO) { categorySortStore.clearAll() }
        if (mode == com.shelf.launcher.data.SortMode.METACRITIC || mode == com.shelf.launcher.data.SortMode.CRITIC) fillMetacriticScores()
    }

    private val firstSeenMutex = kotlinx.coroutines.sync.Mutex()
    private var firstSeenCache: Map<String, Long>? = null
    private var firstSeenSeeding: Boolean? = null
    private val createdAt = android.os.SystemClock.elapsedRealtime()

    /**
     * Notes when each new game first showed up (for "Newly added"). Everything
     * already here the first time this runs is "unknown" (0) — apps use their
     * real install time — so only genuinely new games sort first. All disk and
     * package-manager work is off the main thread.
     */
    /**
     * New games: their art is fetched first (a few seconds at most, the first four — the show names no more),
     * and the cover is loaded into the image cache, so the arrival card shows the real picture instead of an
     * empty grey tile. Then the arrival show starts.
     */
    private suspend fun announceArrivals(games: List<Game>, keys: Collection<String>) {
        runCatching {
            val fields = _uiState.value.scrapeOptions.art
            val anySource = !SgdbAuth.apiKey.isNullOrBlank() || tgdbKeyStore.loadKey() != null || screenScraperStore.load() != null
            if (anySource && fields.isNotEmpty()) {
                val byKey = games.associateBy { it.stableKey }
                val app = getApplication<Application>()
                val deadline = android.os.SystemClock.elapsedRealtime() + 15_000L
                for (key in keys.take(4)) {
                    val game = byKey[key] ?: continue
                    if (_uiState.value.usesOwnIcon(game)) continue
                    if (com.shelf.launcher.data.TitleMatch.skipScraping(game) && game.sgdbGameId == null) continue
                    var art = _uiState.value.art[key]
                    if (art?.gridUrl == null) {
                        val left = deadline - android.os.SystemClock.elapsedRealtime()
                        if (left <= 0L) break
                        val fresh = kotlinx.coroutines.withTimeoutOrNull(left) { fetchArtFromSelectedSource(game) } ?: continue
                        art = _uiState.value.art[key].mergeArt(fresh, fields)
                        artCacheStore.put(key, art)
                        val merged = art
                        _uiState.update { it.copy(art = it.art + (key to merged)) }
                    }
                    // Into the image cache too, so the card doesn't wait for the download.
                    art?.gridUrl?.let { url ->
                        val left = deadline - android.os.SystemClock.elapsedRealtime()
                        if (left > 0L) kotlinx.coroutines.withTimeoutOrNull(left) {
                            coil.Coil.imageLoader(app).execute(coil.request.ImageRequest.Builder(app).data(url).build())
                        }
                    }
                }
            }
        }
        _uiState.update { it.copy(newArrivals = (it.newArrivals + keys).distinct().takeLast(2000)) }
    }

    /** The arrival animation has shown these. */
    fun consumeNewArrivals() { _uiState.update { it.copy(newArrivals = emptyList()) } }

    private fun recordFirstSeen(games: List<Game>) {
        if (games.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            firstSeenMutex.withLock {
                val known = firstSeenCache ?: firstSeenStore.loadAll().also { firstSeenCache = it }
                val seeding = firstSeenSeeding ?: (!firstSeenStore.isSeeded()).also { firstSeenSeeding = it }
                val now = System.currentTimeMillis()
                val pm = getApplication<Application>().packageManager
                val add = HashMap<String, Long>()
                for (g in games) {
                    val key = g.stableKey
                    if (key in known) continue
                    val installed = if (g.sourceType == SourceType.APP) g.packageName?.let { pkg ->
                        runCatching { pm.getPackageInfo(pkg, 0).firstInstallTime }.getOrNull()?.takeIf { it > 0L }
                    } else null
                    add[key] = installed ?: if (seeding) 0L else now
                }
                val merged = if (add.isEmpty()) known else known + add
                if (add.isNotEmpty()) firstSeenStore.putAll(add)
                firstSeenCache = merged
                // Seeding ends once ROMs are in (or after a minute, for people without any).
                if (seeding && (games.any { it.sourceType == SourceType.ROM } ||
                        android.os.SystemClock.elapsedRealtime() - createdAt > 60_000L)) {
                    firstSeenStore.markSeeded()
                    // (update 91) Seeding is over for this process too: later games get their real first-seen time.
                    firstSeenSeeding = false
                }
                if (_uiState.value.firstSeen !== merged) _uiState.update { it.copy(firstSeen = merged) }
                // Genuinely new (not the first-run seeding, not an app installed long ago): the arrival show.
                if (!seeding) {
                    val fresh = add.filter { (_, at) -> at >= now - 10 * 60_000L }.keys
                    if (fresh.isNotEmpty()) viewModelScope.launch(Dispatchers.IO) { announceArrivals(games, fresh) }
                }
            }
        }
    }

    // v2: older saves came from looser matching and were often the wrong game.
    private val screenshotPrefs = application.getSharedPreferences("screenshots_v2", android.content.Context.MODE_PRIVATE)
        .also {
            val flags = application.getSharedPreferences("migrations", android.content.Context.MODE_PRIVATE)
            if (!flags.getBoolean("media_v2", false)) {
                VideoUrlStore(application).clear()
                application.getSharedPreferences("screenshots", android.content.Context.MODE_PRIVATE).edit().clear().apply()
                flags.edit().putBoolean("media_v2", true).apply()
            }
        }
    private val screenshotsLoading = mutableSetOf<String>()
    private val physicalPrefs = application.getSharedPreferences("physical_media", android.content.Context.MODE_PRIVATE)
    private val physicalLoading = mutableSetOf<String>()
    private val physicalRefreshed = java.util.Collections.synchronizedSet(HashSet<String>())
    @Volatile private var physicalRecheckSeen = 0L
    private val raIconPrefs = application.getSharedPreferences("ra_icons", android.content.Context.MODE_PRIVATE)
    /** Update 113: SteamGridDB logos found for the big logos (stableKey → url, "" = none), kept for good. */
    private val sgdbLogoPrefs = application.getSharedPreferences("sgdb_logos", android.content.Context.MODE_PRIVATE)
    private val sgdbLogoLoading = java.util.Collections.synchronizedSet(HashSet<String>())
    private val raIconLoading = mutableSetOf<Int>()

    /**
     * The physical game for the details window — box front and back, 3D box
     * and disc / cartridge from ScreenScraper, plus RetroAchievements' box art.
     * Looked up once per game, then remembered.
     */
    fun loadPhysicalMedia(game: Game, needSpine: Boolean = false, force: Boolean = false) {
        val key = game.stableKey
        scansSeen += key
        if (key in physicalLoading) return
        // Lists saved before the full box scan was scraped (update 101; before that the spine, update 92) are fetched
        // once more, for the game's own spine and case front (BoxTexture.kt). Only with ScreenScraper connected.
        @Suppress("UNUSED_VARIABLE") val shelf = needSpine
        // (Once per run: a look that failed isn't repeated at every step along the shelf.)
        // Update 108: a game still without its own full box scan is looked up again every two weeks (ScreenScraper
        // keeps adding scans), and at once after Game shelf options › "Look for spines again".
        val recheckAt = com.shelf.launcher.data.Features.spineRecheckAt.value.toLongOrNull() ?: 0L
        if (recheckAt > physicalRecheckSeen) { physicalRecheckSeen = recheckAt; physicalRefreshed.clear() }
        val lastLook = physicalPrefs.getLong("texture_at:$key", 0L)
        val due = lastLook < recheckAt || System.currentTimeMillis() - lastLook > 14L * 24 * 60 * 60 * 1000
        // (Update 141: [force] — "Only the missing real covers" — asks again whatever was saved.)
        val refresh = game.sourceType == SourceType.ROM && (force || (key !in physicalRefreshed && due))
        val known = _uiState.value.physicalMedia[key]
        if (known != null && (!refresh || (!force && known.any { it.first == com.shelf.launcher.ui.BoxTexture.LABEL }) || screenScraperStore.load() == null)) return
        var old: List<Pair<String, String>>? = physicalRaw[key] ?: known
        physicalPrefs.getString(key, null)?.let { saved ->
            val list = saved.split('\n').mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size == 2 && parts[1].isNotBlank()) parts[0] to parts[1] else null
            }
            if (!refresh || (!force && list.any { it.first == com.shelf.launcher.ui.BoxTexture.LABEL }) || screenScraperStore.load() == null) {
                physicalRaw[key] = list
                // (Update 232, #60: the state left as it is when the game's list is the one it already has — no new map.)
                val shown = com.shelf.launcher.data.PhysicalOverrides.apply(key, list)
                _uiState.update { if (it.physicalMedia[key] == shown) it else it.copy(physicalMedia = it.physicalMedia + (key to shown)) }
                return
            }
            if (old == null) old = list
        }
        val before = old
        physicalLoading += key
        if (refresh) physicalRefreshed += key
        viewModelScope.launch {
            val ssCreds = screenScraperStore.load()?.takeIf { game.sourceType == SourceType.ROM }
            val raCreds = raCredentialsStore.load()
            var ssFound = false
            val list = withContext(Dispatchers.IO) {
                val fromSs = ssCreds?.let { c ->
                    runCatching { screenScraperRepository.physicalMedia(c, game) }.getOrDefault(emptyList())
                }.orEmpty()
                ssFound = fromSs.isNotEmpty()
                val fromRa = if (raCreds != null && game.raGameId != null) {
                    raRepository.fetchGameInfo(raCreds.apiKey, game.raGameId)?.let { info ->
                        info.ImageIcon?.let { rememberRaIcon(game.raGameId, it) }
                        listOfNotNull(
                            com.shelf.launcher.data.RaBadges.mediaUrl(info.ImageBoxArt)?.let { "Box (RetroAchievements)" to it },
                            com.shelf.launcher.data.RaBadges.mediaUrl(info.ImageTitle)?.let { "Title screen" to it }
                        )
                    }.orEmpty()
                } else emptyList()
                // A look again that found nothing from ScreenScraper (network trouble) keeps what was saved before.
                if (fromSs.isEmpty() && before != null) (before + fromRa).distinctBy { it.first } else fromSs + fromRa
            }
            physicalLoading -= key
            if (ssCreds != null || raCreds != null) {
                val ed = physicalPrefs.edit().putString(key, list.joinToString("\n") { "${it.first}\t${it.second}" })
                // Asked again later when it came back empty although ScreenScraper knew the game before.
                val beforeHadSs = before?.any { it.first != "Box (RetroAchievements)" && it.first != "Title screen" } == true
                if (ssCreds != null && (ssFound || !beforeHadSs)) ed.putBoolean("spine_checked:$key", true).putLong("texture_at:$key", System.currentTimeMillis())
                ed.apply()
            }
            physicalRaw[key] = list
            // (Update 232, #60: the state left as it is when the look again found the list it already has — no new map.)
            val shown = com.shelf.launcher.data.PhysicalOverrides.apply(key, list)
            _uiState.update { if (it.physicalMedia[key] == shown) it else it.copy(physicalMedia = it.physicalMedia + (key to shown)) }
        }
    }

    /** Update 137: what ScreenScraper found for each game, before your picks (PhysicalOverrides). */
    private val physicalRaw = java.util.concurrent.ConcurrentHashMap<String, List<Pair<String, String>>>()

    /**
     * Update 205: the games whose scans were asked for or shown this run — the ones whose pictures "Save art on the
     * device" keeps, as before 205 (every game's saved scans are in the state from the start now, see
     * [preloadPhysicalMedia]; saving all of them would download every scan of every game at once).
     */
    private val scansSeen: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    /** Update 205: [keys] were shown (a category's games): their scans are saved with the rest of the art. */
    fun markScansSeen(keys: Collection<String>) { scansSeen += keys }

    /**
     * Update 205: every game's scans saved before (box, back, spine, disc / cartridge, label…) put into the state at
     * start, off the main thread. They were read only when a game's card first asked for them — and the cards, cases
     * and spines waited 0.4 s on screen before asking — so every category showed Voltcap's own spines, plain backs and
     * drawn cartridges first and the real ones popped in after, or only on going back to it; and a game's case had no
     * back until its own card had asked. Nothing is looked up on the network here; [loadPhysicalMedia] still asks
     * ScreenScraper again when a game's scans are due for a fresh look.
     */
    private fun preloadPhysicalMedia() {
        viewModelScope.launch(Dispatchers.Default) {
            // (Your own picks for a game's pictures go on top, as when a game is looked up; read here in case the
            // packs, which read them at start, haven't yet.)
            runCatching { com.shelf.launcher.data.PhysicalOverrides.init(getApplication()) }
            // (Update 301: your full case scan is cut in its console's proportions.)
            com.shelf.launcher.data.PhysicalOverrides.platformOf = { k -> _uiState.value.games.firstOrNull { it.stableKey == k }?.platform }
            // (Update 302: a case's back from TheGamesDB, with your own key, where the game has no back scan — TgdbBacks.)
            com.shelf.launcher.data.TgdbBacks.init(getApplication())
            com.shelf.launcher.data.TgdbBacks.fetcher = { g -> tgdbKeyStore.loadKey()?.let { k -> tgdbRepository.fetchBack(k, g) } }
            com.shelf.launcher.data.TgdbBacks.onFound = { g ->
                val key = g.stableKey
                val raw = physicalRaw[key] ?: _uiState.value.physicalMedia[key].orEmpty()
                _uiState.update { it.copy(physicalMedia = it.physicalMedia + (key to com.shelf.launcher.data.PhysicalOverrides.apply(key, raw))) }
            }
            // (Update 305: verified details for generated case backs — ratings, publisher, year… — asked for when a back
            // is wanted: Steam games from Steam's store record, ROMs from their ScreenScraper record. PackagingFacts.)
            // (Update 305: what the cases' printed backs are made of — the hero art, screenshots, description — read from
            // the screen's state where any case is drawn, not only in Inspect: ui/PrintedBacks.kt › BackSources.)
            viewModelScope.launch(Dispatchers.Main) { _uiState.collect { com.shelf.launcher.ui.BackSources.state.value = it } }
            com.shelf.launcher.data.PackagingFactsStore.init(getApplication())
            com.shelf.launcher.data.ArtProvenance.init(getApplication())
            com.shelf.launcher.data.PackagingFactsStore.screenScraper = { g ->
                screenScraperStore.load()?.let { c -> screenScraperRepository.packagingFacts(c, g) }
            }
            val all = runCatching { physicalPrefs.all }.getOrNull().orEmpty()
            val loaded = HashMap<String, List<Pair<String, String>>>(all.size)
            for ((k, v) in all) {
                val saved = v as? String ?: continue
                val list = saved.split('\n').mapNotNull { line ->
                    val parts = line.split('\t')
                    if (parts.size == 2 && parts[1].isNotBlank()) parts[0] to parts[1] else null
                }
                if (list.isEmpty()) continue
                physicalRaw.putIfAbsent(k, list)
                loaded[k] = runCatching { com.shelf.launcher.data.PhysicalOverrides.apply(k, list) }.getOrDefault(list)
            }
            // (Update 305: a game with your own pictures but nothing scraped — no saved list — gets them too; they showed
            // only once its scans had been looked up again.)
            for (k in com.shelf.launcher.data.PhysicalOverrides.keys()) {
                if (k !in loaded) loaded[k] = runCatching { com.shelf.launcher.data.PhysicalOverrides.apply(k, emptyList()) }.getOrDefault(emptyList())
            }
            // (What was looked up meanwhile wins over the saved list.)
            if (loaded.isNotEmpty()) _uiState.update { st -> st.copy(physicalMedia = loaded + st.physicalMedia) }
        }
    }

    /** Update 137: your pick for one of [game]'s real-media pictures ([label] as in the scraped list; null: back to the scan). */
    fun setPhysicalOverride(game: Game, label: String, url: String?) {
        val key = game.stableKey
        com.shelf.launcher.data.PhysicalOverrides.set(key, label, url)
        val raw = physicalRaw[key] ?: _uiState.value.physicalMedia[key].orEmpty()
        _uiState.update { it.copy(physicalMedia = it.physicalMedia + (key to com.shelf.launcher.data.PhysicalOverrides.apply(key, raw))) }
    }

    private fun rememberRaIcon(gameId: Int, relative: String) {
        val url = com.shelf.launcher.data.RaBadges.mediaUrl(relative) ?: return
        raIconPrefs.edit().putString(gameId.toString(), url).apply()
        _uiState.update { it.copy(raIcons = it.raIcons + (gameId to url)) }
    }

    /**
     * With "Logos from RetroAchievements" on: looks up the icon of every
     * linked game that doesn't have one saved yet, quietly and one at a time,
     * so they're all there before you scroll to them. Saved for good (and
     * downloaded with the rest of the artwork).
     */
    private suspend fun fetchMissingRaIcons() {
        if (!_uiState.value.appearance.raLogos) return
        val creds = raCredentialsStore.load() ?: return
        val ids = _uiState.value.games.mapNotNull { it.raGameId }.distinct()
            .filter { it !in _uiState.value.raIcons && it !in raIconLoading }
        for (id in ids.take(400)) {
            com.shelf.launcher.data.GameMode.awaitNotPlaying()
            // Played games already came with their icon (no "continue" inside a
            // lambda: this project's Kotlin version doesn't allow it).
            val known = _uiState.value.raCompletionById[id]?.ImageIcon
            if (known != null) {
                rememberRaIcon(id, known)
                continue
            }
            raIconLoading += id
            val info = withContext(Dispatchers.IO) { runCatching { raRepository.fetchGameInfo(creds.apiKey, id) }.getOrNull() }
            info?.ImageIcon?.let { rememberRaIcon(id, it) }
            if (info != null) raIconLoading -= id
            kotlinx.coroutines.delay(400) // gentle on RetroAchievements
        }
        if (_uiState.value.artStorage.autoSave) saveAllArt(silent = true)
    }

    /**
     * Start › Refetch RetroAchievements icon: forgets the saved icon for this
     * game and asks RetroAchievements again — for games whose icon never
     * showed up (a failed lookup isn't retried on its own until restart).
     */
    fun refetchRaIcon(game: Game) {
        val id = game.raGameId
        if (id == null) {
            _uiState.update {
                it.copy(statusMessage = "${game.name} isn't linked to RetroAchievements yet — use Achievement matching first.")
            }
            return
        }
        val creds = raCredentialsStore.load()
        if (creds == null) {
            _uiState.update { it.copy(statusMessage = "Connect RetroAchievements first (Settings › Accounts).") }
            return
        }
        raIconPrefs.edit().remove(id.toString()).apply()
        raIconLoading -= id
        _uiState.update { it.copy(raIcons = it.raIcons - id, statusMessage = "Asking RetroAchievements for the icon…") }
        raIconLoading += id
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) { runCatching { raRepository.fetchGameInfo(creds.apiKey, id) }.getOrNull() }
            raIconLoading -= id
            val icon = info?.ImageIcon
            if (icon != null) rememberRaIcon(id, icon)
            _uiState.update {
                it.copy(
                    statusMessage = when {
                        icon != null && !it.appearance.raLogos ->
                            "Icon found. Turn on Game names › Logos from RetroAchievements to show it."
                        icon != null -> "RetroAchievements icon updated for ${game.name}."
                        info == null -> "Couldn't reach RetroAchievements — try again in a moment."
                        else -> "RetroAchievements has no icon for this game."
                    }
                )
            }
            if (icon != null && _uiState.value.artStorage.autoSave) saveAllArt(silent = true)
        }
    }

    /** Settings: look up every linked game's icon again, including ones that
     * failed before. Saved icons are kept; only missing ones are asked for. */
    fun refetchMissingRaIcons() {
        raIconLoading.clear()
        _uiState.update { it.copy(statusMessage = "Looking up missing RetroAchievements icons…") }
        viewModelScope.launch {
            if (!_uiState.value.appearance.raLogos) {
                _uiState.update { it.copy(statusMessage = "Turn on Logos from RetroAchievements first.") }
                return@launch
            }
            val before = _uiState.value.raIcons.size
            fetchMissingRaIcons()
            val found = _uiState.value.raIcons.size - before
            _uiState.update { it.copy(statusMessage = if (found > 0) "Found $found more RetroAchievements icons." else "No new icons found.") }
        }
    }

    /**
     * Update 113: the game's SteamGridDB logo, for the big logos (Edit Home's logo, Hero carousel, Spotlight…) when
     * its artwork's logo came from elsewhere. Asked once per game and remembered (also that it has none).
     */
    fun loadSgdbLogo(game: Game) {
        val key = game.stableKey
        if (_uiState.value.sgdbLogos.containsKey(key) || key in sgdbLogoLoading) return
        sgdbLogoPrefs.getString(key, null)?.let { url ->
            _uiState.update { it.copy(sgdbLogos = it.sgdbLogos + (key to url)) }
            return
        }
        if (com.shelf.launcher.data.SgdbAuth.apiKey.isNullOrBlank()) return
        sgdbLogoLoading += key
        viewModelScope.launch {
            val url = withContext(Dispatchers.IO) { runCatching { repository.fetchLogo(game) }.getOrNull() }
            // Only a definite answer is kept ("" = SteamGridDB has no logo for it); a failed look-up is tried next run.
            // (A failed one stays in sgdbLogoLoading, so it isn't asked again until the next start.)
            if (url != null) {
                sgdbLogoPrefs.edit().putString(key, url).apply()
                _uiState.update { it.copy(sgdbLogos = it.sgdbLogos + (key to url)) }
                sgdbLogoLoading -= key
            }
        }
    }

    /** The RetroAchievements icon for a game (Game names › Logos from RetroAchievements). */
    fun loadRaIcon(game: Game) {
        val id = game.raGameId ?: return
        if (_uiState.value.raIcons.containsKey(id) || id in raIconLoading) return
        raIconPrefs.getString(id.toString(), null)?.let { url ->
            _uiState.update { it.copy(raIcons = it.raIcons + (id to url)) }
            return
        }
        // Played games already came with their icon.
        _uiState.value.raCompletionById[id]?.ImageIcon?.let { rememberRaIcon(id, it); return }
        val creds = raCredentialsStore.load() ?: return
        raIconLoading += id
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) { raRepository.fetchGameInfo(creds.apiKey, id) }
            info?.ImageIcon?.let { rememberRaIcon(id, it) }
            if (info != null) raIconLoading -= id // failed lookups aren't retried this session
        }
    }

    /** Screenshots for the details window's media row — ScreenScraper's for
     * ROMs when connected, plus IGDB's. Found once per game, then remembered. */
    fun loadMedia(game: Game) {
        val key = game.stableKey
        if (_uiState.value.screenshots.containsKey(key) || key in screenshotsLoading) return
        screenshotPrefs.getString(key, null)?.let { saved ->
            val list = saved.split('\n').filter { it.isNotBlank() }
            _uiState.update { it.copy(screenshots = it.screenshots + (key to list)) }
            return
        }
        if (game.sourceType == SourceType.APP && !isKnownGameApp(game)) return
        screenshotsLoading += key
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                val fromSs = screenScraperStore.load()?.takeIf { game.sourceType == SourceType.ROM }?.let { creds ->
                    runCatching { screenScraperRepository.screenshots(creds, game) }.getOrDefault(emptyList())
                }.orEmpty()
                val fromIgdb = igdbCredentialsStore.load()?.let { c ->
                    runCatching { igdbRepository.fetchScreenshots(c.clientId, c.clientSecret, game.name, game.platform) }
                        .getOrDefault(emptyList())
                }.orEmpty()
                val fromRawg = rawgKeyStore.load()?.let { k ->
                    runCatching { rawgRepository.fetchScreenshots(k, game.name, game.platform) }.getOrDefault(emptyList())
                }.orEmpty()
                (fromSs + fromIgdb + fromRawg).distinct().take(12)
            }
            screenshotsLoading -= key
            // Nothing found is remembered too, unless no source was connected.
            if (list.isNotEmpty() || screenScraperStore.load() != null || igdbCredentialsStore.load() != null ||
                rawgKeyStore.load() != null
            ) {
                screenshotPrefs.edit().putString(key, list.joinToString("\n")).apply()
            }
            _uiState.update { it.copy(screenshots = it.screenshots + (key to list)) }
        }
    }

    /**
     * Looks a game's details up again from the chosen source, with an
     * editable search name — from the Artwork & details window. Replaces what
     * was saved, and refreshes the screenshots too.
     */
    fun scrapeMetadataFor(game: Game, source: MetadataSource, searchName: String) {
        val key = game.stableKey
        if (key in _uiState.value.metadataLoading) return
        _uiState.update {
            it.copy(metadataLoading = it.metadataLoading + key, metadataNotes = it.metadataNotes - key)
        }
        viewModelScope.launch {
            val name = searchName.ifBlank { game.name }
            val meta: GameMetadata? = runCatching {
                when (source) {
                    MetadataSource.IGDB -> igdbCredentialsStore.load()?.let {
                        igdbRepository.fetchMetadata(it.clientId, it.clientSecret, name, game.platform)
                    }
                    MetadataSource.SCREENSCRAPER -> screenScraperStore.load()?.let { creds ->
                        if (game.sourceType == SourceType.ROM) {
                            screenScraperRepository.fetchMetadata(creds, game)
                        } else null
                    }
                    MetadataSource.RAWG -> rawgKeyStore.load()?.let {
                        rawgRepository.fetchMetadata(it, name, game.platform)
                    }
                    MetadataSource.COMBINED -> fetchCombined(game, name)
                    MetadataSource.NONE -> null
                }
            }.getOrNull()
            val fields = _uiState.value.scrapeOptions.details.ifEmpty { com.shelf.launcher.data.DetailField.values().toSet() }
            val merged = withRawg(game, meta, name)?.let { fresh ->
                _uiState.value.metadata[key].mergeFields(fresh, fields)
            }
            if (merged != null) metadataCacheStore.put(key, merged)
            // The same source (and search name) also looks for artwork, so a
            // re-match fixes the pictures too — not just the text.
            val foundArt = runCatching {
                val named = game.copy(name = name)
                when (source) {
                    MetadataSource.IGDB -> igdbCredentialsStore.load()?.let {
                        igdbRepository.fetchArt(it.clientId, it.clientSecret, name, game.platform)
                    }
                    MetadataSource.RAWG -> rawgKeyStore.load()?.let { rawgRepository.fetchArt(it, name, game.platform) }
                    MetadataSource.SCREENSCRAPER -> screenScraperStore.load()?.takeIf { game.sourceType == SourceType.ROM }
                        ?.let { screenScraperRepository.fetchArt(it, game) }
                    MetadataSource.COMBINED -> withContext(Dispatchers.IO) { fetchArtFromSelectedSource(named) }
                    MetadataSource.NONE -> null
                }
            }.getOrNull()
            if (foundArt != null) {
                val artFields = _uiState.value.scrapeOptions.art.ifEmpty { com.shelf.launcher.data.ArtField.values().toSet() }
                val newArt = _uiState.value.art[key].mergeArt(foundArt, artFields)
                artCacheStore.put(key, newArt)
                _uiState.update { it.copy(art = it.art + (key to newArt)) }
            }
            val note = when {
                meta != null -> "Details" + (if (foundArt != null) " and artwork" else "") + " updated from ${source.label}." +
                    if (merged?.metacritic != null) " Metacritic via RAWG." else ""
                source == MetadataSource.SCREENSCRAPER && game.sourceType != SourceType.ROM ->
                    "ScreenScraper only knows ROMs — try IGDB for this one."
                else -> "${source.label} found nothing for \"$name\". Try another name or the other source."
            }
            // Screenshots follow the new match.
            screenshotPrefs.edit().remove(key).apply()
            _uiState.update {
                it.copy(
                    metadata = if (merged != null) it.metadata + (key to merged) else it.metadata,
                    metadataLoading = it.metadataLoading - key,
                    metadataNotes = it.metadataNotes + (key to note),
                    screenshots = it.screenshots - key
                )
            }
            loadMedia(game)
        }
    }

    private fun isKnownGameApp(game: Game): Boolean = _uiState.value.isAndroidGame(game)

    /** The category Voltcap opens on (Settings › Library › Start in). Recent
     * with nothing played yet opens on All instead of an empty row. */
    /** Back within half an hour: carry on exactly where you were, whatever
     * "Start in" says — the home button must never throw you into Recent. */
    private fun resumingRecently(): Boolean =
        System.currentTimeMillis() - sessionPrefs.getLong("left_at", 0L) < 30 * 60_000L

    /** The game the overlays belong to, while its session lasts (to bring them back after a visit to Voltcap). */
    private var overlayGame: Game? = null
    private var overlayDisplay: Int? = null

    /**
     * Back to a game that is still running (after opening Voltcap mid-game, e.g. to change the overlays in
     * the R1 panel): the overlays come back with the settings as they are now, instead of staying off.
     */
    private fun restoreOverlaysForRunningGame() {
        val game = overlayGame ?: return
        if (ownLaunchPending) return
        // Update 204: still open = in the open list ([RunningGames.keys]). It asked for [RunningGames.confirmed], which is
        // only filled by the background check a moment after Voltcap comes back — going back into the game before that
        // ran (or with no usage access / root to confirm it) forgot the game for good, so the overlays never came back
        // (after a trip to Voltcap, or to the overlay panel from the notification). A game that's gone drops off the
        // list on its own (RunningGames.refresh).
        if (game.stableKey !in com.shelf.launcher.data.RunningGames.keys) { overlayGame = null; return }
        val app = getApplication<Application>()
        val cfg = com.shelf.launcher.data.perf.PerfSettings.config(app)
        val perf = effectiveProfile(game).perfOverlay.value
        val music = cfg.musicDuringGames
        val ra = cfg.raDuringGames && game.raGameId != null
        if (ra) followRaOverlay(game)
        if (perf || music || ra) {
            // (Update 204: the same play session — the overlay's session clock keeps counting instead of starting over.)
            com.shelf.launcher.ui.PerfOverlayService.start(app, overlayDisplay, perf = perf, music = music, ra = ra, newSession = false)
        }
    }

    fun onLauncherPaused() {
        launcherResumed = false
        restoreOverlaysForRunningGame()
        // The visible Voltcap folder (if chosen) gets a fresh copy of every setting.
        viewModelScope.launch(Dispatchers.IO) { runCatching { com.shelf.launcher.data.AppStorage.mirrorSettings(getApplication()) } }
        // A game (or app) is starting: stop listening for Drive changes.
        com.shelf.launcher.data.CloudSync.stopWatching()
        // Discord's callback pump parks itself unless a game session is showing.
        com.shelf.launcher.data.DiscordPresence.setForeground(false)
        sessionPrefs.edit().putLong("left_at", System.currentTimeMillis()).apply()
        // After our own launch the snapshot taken at launch time stays (the list re-sorts afterwards).
        if (!ownLaunchPending && !restoreActive) saveLaunchContext(null, false)
        dismissSessionSummary()
        // Don't lose the last selection to the save delay.
        focusSaveJob?.cancel()
        if (unsavedFocusKey != null) viewModelScope.launch { flushFocusSave() }
    }

    /** Remembers where the launcher was; [pendingReturn] only when we are the reason it leaves. */
    private fun saveLaunchContext(launchedKey: String?, pendingReturn: Boolean) {
        val s = _uiState.value
        val focused = s.visibleGames.getOrNull(s.focusedIndex)?.stableKey
        val detailsOpen = detailsOpenState.value && focused != null
        runCatching {
            launchContextStore.save(
                com.shelf.launcher.data.LaunchContext(
                    platformFilter = s.platformFilter,
                    specialFilter = s.specialFilter?.name,
                    searchQuery = s.searchQuery.takeIf { it.isNotBlank() } ?: "",
                    focusedKey = focused,
                    focusedIndex = s.focusedIndex,
                    categoryScreenOpen = categoryScreenOpenState.value,
                    detailsOpen = detailsOpen,
                    detailsKey = if (detailsOpen) focused else null,
                    launchedKey = launchedKey,
                    savedAt = System.currentTimeMillis(),
                    savedElapsed = android.os.SystemClock.elapsedRealtime(),
                    pendingReturn = pendingReturn
                )
            )
        }
    }

    /** Called right after something was started from Voltcap, before the list re-sorts. */
    private fun noteOwnLaunch(launchedKey: String?) {
        if (restoreActive) return // library not even loaded yet: keep what we restored from
        ownLaunchPending = true
        lastLaunchAt = System.currentTimeMillis()
        saveLaunchContext(launchedKey, true)
    }

    /** Gives up waiting for the saved game: try the launched one, else the nearest index. */
    private fun finishRestore() {
        val ctx = restoreCtx ?: return
        if (!restoreActive) return
        restoreActive = false
        if (_uiState.value.games.isEmpty()) return // still loading: the plain pending key keeps waiting
        refreshCollections() // drops a collection that no longer exists
        if (_uiState.value.visibleGames.isEmpty() && (_uiState.value.platformFilter != null || _uiState.value.specialFilter != null)) {
            // The saved category is gone (console removed, collection deleted): the start category instead.
            val start = appearanceStore.load().startCategory
            val special = runCatching { SpecialFilter.valueOf(start) }.getOrNull()
                ?.takeUnless { it == SpecialFilter.RECENT && recentlyPlayedStore.loadVisible().isEmpty() }
            setSpecialFilter(special)
        }
        _uiState.update { s ->
            val visible = s.visibleGames
            val byKey = ctx.launchedKey?.let { k -> visible.indexOfFirst { it.stableKey == k }.takeIf { it >= 0 } }
            s.copy(focusedIndex = (byKey ?: ctx.focusedIndex).coerceIn(0, (visible.size - 1).coerceAtLeast(0)))
        }
        pendingFocusKey = null
        val s = _uiState.value
        if (pendingDetailsKeyState.value != s.visibleGames.getOrNull(s.focusedIndex)?.stableKey) {
            pendingDetailsKeyState.value = null
        }
    }

    private fun startSpecialFilter(): SpecialFilter? {
        restoreCtx?.let { c -> return c.specialFilter?.let { runCatching { SpecialFilter.valueOf(it) }.getOrNull() } }
        val start = if (resumingRecently()) "LAST" else appearanceStore.load().startCategory
        if (start == "LAST") {
            return preferencesStore.lastSpecialFilter()?.let { runCatching { SpecialFilter.valueOf(it) }.getOrNull() }
        }
        val filter = runCatching { SpecialFilter.valueOf(start) }.getOrNull() ?: return null
        if (filter == SpecialFilter.RECENT && recentlyPlayedStore.loadVisible().isEmpty()) return null
        // (Update 219, #04: a start category you hid (Choose a category › Options) isn't in the L2 / R2 list or the
        // tabs, so the looks couldn't step out of it properly: start in All instead.)
        if (runCatching { filter.name in categoryStore.load().hidden }.getOrDefault(false)) return null
        return filter
    }

    private fun startPlatformFilter(): String? =
        if (restoreCtx != null) restoreCtx.platformFilter else if (resumingRecently() || appearanceStore.load().startCategory == "LAST") preferencesStore.lastPlatformFilter()
        // Update 125: a console to start in ("P:" and its key, e.g. the Android games).
        else appearanceStore.load().startCategory.takeIf { it.startsWith("P:") }?.removePrefix("P:")

    /**
     * What the details window could fetch for [game] that it doesn't have yet
     * — only things a connected source can actually provide. Empty while the
     * details are still loading, after "Add missing" already ran for the game
     * this session, or when the option is switched off.
     */
    fun missingFor(game: Game, s: LauncherUiState = _uiState.value, ignoreTried: Boolean = false): List<MissingInfo> {
        val key = game.stableKey
        if (!s.appearance.offerMissing || key in s.fillingMissing) return emptyList()
        if (key in s.missingTried && !ignoreTried) return emptyList()
        if (key in s.metadataLoading || !s.metadataReady) return emptyList()
        if (game.sourceType == SourceType.APP && !s.isAndroidGame(game)) return emptyList()
        val isRom = game.sourceType == SourceType.ROM
        val ss = s.screenScraperConnected && isRom
        val anyDetails = s.metadataSource != MetadataSource.NONE && (s.igdbConnected || s.rawgConnected || ss)
        val meta = s.metadata[key]
        val art = s.art[key]
        val override = s.artOverrides[key]
        val anyArt = !SgdbAuth.apiKey.isNullOrBlank() || s.tgdbConnected || ss || s.igdbConnected || s.rawgConnected
        return buildList {
            if (anyDetails) {
                if (meta?.summary.isNullOrBlank()) add(MissingInfo.SUMMARY)
                if (meta?.criticScore == null && meta?.metacritic == null) add(MissingInfo.SCORE)
                else if (meta.metacritic == null && s.rawgConnected) add(MissingInfo.METACRITIC)
                if ((s.igdbConnected || s.rawgConnected) && meta?.normallyHours == null && meta?.hastilyHours == null &&
                    meta?.completelyHours == null
                ) add(MissingInfo.PLAYTIME)
            }
            if (ss && !s.videoUrls.containsKey(key)) add(MissingInfo.VIDEO)
            if (anyArt) {
                if ((override?.cover ?: art?.gridUrl) == null) add(MissingInfo.COVER)
                if ((override?.hero ?: art?.heroUrl) == null) add(MissingInfo.BACKGROUND)
                if ((override?.logo ?: art?.logoUrl) == null) add(MissingInfo.LOGO)
            }
        }
    }

    /**
     * Sources "Add missing" can try one at a time after a first try left
     * something out — only connected ones that could fill what's still missing.
     */
    fun missingSourcesFor(game: Game, s: LauncherUiState = _uiState.value): List<MissingSource> {
        if (game.stableKey !in s.missingTried) return emptyList()
        val missing = missingFor(game, s, ignoreTried = true)
        if (missing.isEmpty()) return emptyList()
        val isRom = game.sourceType == SourceType.ROM
        val wantsDetails = missing.any {
            it == MissingInfo.SUMMARY || it == MissingInfo.SCORE || it == MissingInfo.METACRITIC || it == MissingInfo.PLAYTIME
        }
        val wantsArt = missing.any { it == MissingInfo.COVER || it == MissingInfo.BACKGROUND || it == MissingInfo.LOGO }
        return MissingSource.values().filter { src ->
            when (src) {
                MissingSource.IGDB -> s.igdbConnected && (wantsDetails || wantsArt)
                MissingSource.RAWG -> s.rawgConnected && (wantsDetails || wantsArt)
                MissingSource.SCREENSCRAPER -> s.screenScraperConnected && isRom
                MissingSource.STEAMGRIDDB -> !SgdbAuth.apiKey.isNullOrBlank() && wantsArt
                MissingSource.THEGAMESDB -> s.tgdbConnected && wantsArt
            }
        }
    }

    /** "Add missing" again, from one chosen source only. */
    fun fillMissingFrom(game: Game, source: MissingSource) {
        val key = game.stableKey
        val missing = missingFor(game, ignoreTried = true)
        if (missing.isEmpty()) return
        _uiState.update { it.copy(fillingMissing = it.fillingMissing + key) }
        viewModelScope.launch {
            val found = mutableListOf<MissingInfo>()
            try {
                val detailFields = buildSet {
                    if (MissingInfo.SUMMARY in missing) add(com.shelf.launcher.data.DetailField.SUMMARY)
                    if (MissingInfo.SCORE in missing) {
                        add(com.shelf.launcher.data.DetailField.SCORE); add(com.shelf.launcher.data.DetailField.METACRITIC)
                    }
                    if (MissingInfo.METACRITIC in missing) add(com.shelf.launcher.data.DetailField.METACRITIC)
                    if (MissingInfo.PLAYTIME in missing) add(com.shelf.launcher.data.DetailField.PLAYTIME)
                }
                val meta: GameMetadata? = if (detailFields.isEmpty()) null else runCatching {
                    when (source) {
                        MissingSource.IGDB -> igdbCredentialsStore.load()?.let {
                            igdbRepository.fetchMetadata(it.clientId, it.clientSecret, game.name, game.platform)
                        }
                        MissingSource.RAWG -> rawgKeyStore.load()?.let { rawgRepository.fetchMetadata(it, game.name, game.platform) }
                        MissingSource.SCREENSCRAPER -> screenScraperStore.load()?.takeIf { game.sourceType == SourceType.ROM }
                            ?.let { screenScraperRepository.fetchMetadata(it, game) }
                        else -> null
                    }
                }.getOrNull()
                if (meta != null) {
                    val before = _uiState.value.metadata[key]
                    val merged = before.mergeFields(meta, detailFields)
                    if (merged != before) {
                        metadataCacheStore.put(key, merged)
                        _uiState.update { it.copy(metadata = it.metadata + (key to merged)) }
                    }
                    if (MissingInfo.SUMMARY in missing && !merged.summary.isNullOrBlank()) found += MissingInfo.SUMMARY
                    if (MissingInfo.SCORE in missing && (merged.criticScore != null || merged.metacritic != null)) found += MissingInfo.SCORE
                    if (MissingInfo.METACRITIC in missing && merged.metacritic != null) found += MissingInfo.METACRITIC
                    // (update 91) Any of the three times counts (as in missingFor).
                    if (MissingInfo.PLAYTIME in missing && (merged.normallyHours != null || merged.hastilyHours != null ||
                            merged.completelyHours != null)) found += MissingInfo.PLAYTIME
                }
                if (MissingInfo.VIDEO in missing && source == MissingSource.SCREENSCRAPER) {
                    val creds = screenScraperStore.load()
                    val url = creds?.let { runCatching { screenScraperRepository.videoUrl(it, game) }.getOrNull() }
                    if (creds != null) videoUrlStore.put(key, url)
                    if (url != null) {
                        found += MissingInfo.VIDEO
                        _uiState.update { it.copy(videoUrls = it.videoUrls + (key to url)) }
                    }
                }
                val artFields = buildSet {
                    if (MissingInfo.COVER in missing) add(com.shelf.launcher.data.ArtField.COVER)
                    if (MissingInfo.BACKGROUND in missing) add(com.shelf.launcher.data.ArtField.HERO)
                    if (MissingInfo.LOGO in missing) add(com.shelf.launcher.data.ArtField.LOGO)
                }
                if (artFields.isNotEmpty()) {
                    val art: GameArt? = runCatching {
                        withContext(Dispatchers.IO) {
                            when (source) {
                                MissingSource.STEAMGRIDDB -> repository.fetchArt(game)
                                MissingSource.THEGAMESDB -> tgdbKeyStore.loadKey()?.let { tgdbRepository.fetchArt(it, game) }
                                MissingSource.IGDB -> igdbCredentialsStore.load()?.let {
                                    igdbRepository.fetchArt(it.clientId, it.clientSecret, game.name, game.platform)
                                }
                                MissingSource.RAWG -> rawgKeyStore.load()?.let { rawgRepository.fetchArt(it, game.name, game.platform) }
                                MissingSource.SCREENSCRAPER -> screenScraperStore.load()?.takeIf { game.sourceType == SourceType.ROM }
                                    ?.let { screenScraperRepository.fetchArt(it, game) }
                            }
                        }
                    }.getOrNull()
                    if (art != null) {
                        val newArt = _uiState.value.art[key].mergeArt(art, artFields)
                        artCacheStore.put(key, newArt)
                        _uiState.update { it.copy(art = it.art + (key to newArt)) }
                        if (MissingInfo.COVER in missing && newArt.gridUrl != null) found += MissingInfo.COVER
                        if (MissingInfo.BACKGROUND in missing && newArt.heroUrl != null) found += MissingInfo.BACKGROUND
                        if (MissingInfo.LOGO in missing && newArt.logoUrl != null) found += MissingInfo.LOGO
                    }
                }
            } finally {
                val note = if (found.isEmpty()) "${source.label} had nothing more for this game."
                else "Added " + found.joinToString(", ") { it.label } + " from ${source.label}."
                _uiState.update {
                    it.copy(
                        fillingMissing = it.fillingMissing - key,
                        metadataNotes = it.metadataNotes + (key to note),
                        statusMessage = note
                    )
                }
            }
        }
    }

    /**
     * "Add missing" from the details window: asks every connected source for
     * just the things [game] lacks, and keeps everything it already has
     * (including your own picks) untouched.
     */
    fun fillMissing(game: Game) {
        val key = game.stableKey
        val missing = missingFor(game)
        if (missing.isEmpty()) return
        _uiState.update { it.copy(fillingMissing = it.fillingMissing + key, metadataNotes = it.metadataNotes - key) }
        viewModelScope.launch {
            val found = mutableListOf<MissingInfo>()
            try {
                // Details: every source together, each filling the others' gaps.
                val detailFields = buildSet {
                    if (MissingInfo.SUMMARY in missing) add(com.shelf.launcher.data.DetailField.SUMMARY)
                    if (MissingInfo.SCORE in missing) {
                        add(com.shelf.launcher.data.DetailField.SCORE); add(com.shelf.launcher.data.DetailField.METACRITIC)
                    }
                    if (MissingInfo.METACRITIC in missing) add(com.shelf.launcher.data.DetailField.METACRITIC)
                    if (MissingInfo.PLAYTIME in missing) add(com.shelf.launcher.data.DetailField.PLAYTIME)
                }
                if (detailFields.isNotEmpty()) {
                    val fresh = runCatching { fetchCombined(game, game.name) }.getOrNull()
                    if (fresh != null) {
                        val before = _uiState.value.metadata[key]
                        val merged = before.mergeFields(fresh, detailFields)
                        if (merged != before) {
                            metadataCacheStore.put(key, merged)
                            _uiState.update { it.copy(metadata = it.metadata + (key to merged)) }
                        }
                        if (MissingInfo.SUMMARY in missing && !merged.summary.isNullOrBlank()) found += MissingInfo.SUMMARY
                        if (MissingInfo.SCORE in missing && (merged.criticScore != null || merged.metacritic != null)) found += MissingInfo.SCORE
                        if (MissingInfo.METACRITIC in missing && merged.metacritic != null) found += MissingInfo.METACRITIC
                        // (update 91) Any of the three times counts (as in missingFor).
                        if (MissingInfo.PLAYTIME in missing && (merged.normallyHours != null || merged.hastilyHours != null ||
                                merged.completelyHours != null)) found += MissingInfo.PLAYTIME
                    }
                }
                // Video: looked up again even if an earlier try found nothing.
                if (MissingInfo.VIDEO in missing) {
                    val creds = screenScraperStore.load()
                    val url = creds?.let { runCatching { screenScraperRepository.videoUrl(it, game) }.getOrNull() }
                    if (creds != null) videoUrlStore.put(key, url)
                    if (url != null) {
                        found += MissingInfo.VIDEO
                        _uiState.update { it.copy(videoUrls = it.videoUrls + (key to url)) }
                    }
                }
                // Artwork: only the empty slots; overrides stay as they are.
                val artFields = buildSet {
                    if (MissingInfo.COVER in missing) add(com.shelf.launcher.data.ArtField.COVER)
                    if (MissingInfo.BACKGROUND in missing) add(com.shelf.launcher.data.ArtField.HERO)
                    if (MissingInfo.LOGO in missing) add(com.shelf.launcher.data.ArtField.LOGO)
                }
                if (artFields.isNotEmpty()) {
                    val fresh = runCatching { withContext(Dispatchers.IO) { fetchArtFromSelectedSource(game) } }.getOrNull()
                    if (fresh != null) {
                        val newArt = _uiState.value.art[key].mergeArt(fresh, artFields)
                        artCacheStore.put(key, newArt)
                        _uiState.update { it.copy(art = it.art + (key to newArt)) }
                        if (MissingInfo.COVER in missing && newArt.gridUrl != null) found += MissingInfo.COVER
                        if (MissingInfo.BACKGROUND in missing && newArt.heroUrl != null) found += MissingInfo.BACKGROUND
                        if (MissingInfo.LOGO in missing && newArt.logoUrl != null) found += MissingInfo.LOGO
                    }
                }
            } finally {
                val notFound = missing - found.toSet()
                val note = when {
                    found.isEmpty() -> "Nothing more found for this game. Try another name under Options › Artwork & details."
                    notFound.isEmpty() -> "Added " + found.joinToString(", ") { it.label } + "."
                    else -> "Added " + found.joinToString(", ") { it.label } + " — no " +
                        notFound.joinToString(", ") { it.label } + " found."
                }
                _uiState.update {
                    it.copy(
                        fillingMissing = it.fillingMissing - key,
                        missingTried = it.missingTried + key,
                        metadataNotes = it.metadataNotes + (key to note),
                        statusMessage = note
                    )
                }
            }
        }
    }

    /** Finds (once per game, then remembered) the ScreenScraper clip for the
     * video preview. Only for ROMs, and only with ScreenScraper connected. */
    fun loadVideoUrl(game: Game, force: Boolean = false) {
        if (!force && !_uiState.value.appearance.videoPreviews) return
        if (_uiState.value.videoUrls.containsKey(game.stableKey)) return
        // (Update 231, #59: the look-ups below — the saved clip's file (a SHA-1 of the name and a file check), the saved
        // address and the encrypted ScreenScraper login — now run off the main thread, so stepping onto a game no longer
        // stalls the UI for them. The answer reaches the screen exactly as before.)
        if (!videoLookups.add(game.stableKey)) return
        viewModelScope.launch {
            try {
                val app = getApplication<Application>()
                // Update 148: a clip saved on the device plays from there (no Wi-Fi needed, starts at once).
                val local = withContext(Dispatchers.IO) {
                    com.shelf.launcher.data.ClipStore.init(app)
                    com.shelf.launcher.data.ClipStore.local(game.stableKey)
                }
                if (local != null) {
                    _uiState.update { it.copy(videoUrls = it.videoUrls + (game.stableKey to local)) }
                    return@launch
                }
                val saved = withContext(Dispatchers.IO) { videoUrlStore.get(game.stableKey) }
                if (saved != null) {
                    if (saved.isNotEmpty()) {
                        _uiState.update { it.copy(videoUrls = it.videoUrls + (game.stableKey to saved)) }
                        if (com.shelf.launcher.data.Features.clipsOffline.value) saveClipLater(game.stableKey, saved)
                    }
                    return@launch
                }
                if (game.sourceType != SourceType.ROM) return@launch
                val creds = withContext(Dispatchers.IO) { screenScraperStore.load() } ?: return@launch
                val url = runCatching { screenScraperRepository.videoUrl(creds, game) }.getOrNull()
                videoUrlStore.put(game.stableKey, url)
                if (url != null) _uiState.update { it.copy(videoUrls = it.videoUrls + (game.stableKey to url)) }
                if (url != null && com.shelf.launcher.data.Features.clipsOffline.value) saveClipLater(game.stableKey, url)
            } finally {
                videoLookups -= game.stableKey
            }
        }
    }

    /** (Update 231, #59) Games whose clip is being looked up right now (main thread only), so a look-up isn't started twice. */
    private val videoLookups = mutableSetOf<String>()

    /** Update 148: clips waiting to be saved on the device, one at a time in the background. */
    private val clipQueue = kotlinx.coroutines.channels.Channel<Pair<String, String>>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    private val clipQueued = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val clipSaver = viewModelScope.launch(Dispatchers.IO) {
        for ((key, url) in clipQueue) {
            com.shelf.launcher.data.GameMode.awaitNotPlaying()
            val local = com.shelf.launcher.data.ClipStore.save(key, url)
            clipQueued -= key
            // (Not swapped in while it may be playing from the address: the saved one is used the next time.)
            if (local == null) continue
        }
    }

    private fun saveClipLater(key: String, url: String) {
        com.shelf.launcher.data.ClipStore.init(getApplication())
        if (url.startsWith("file://") || com.shelf.launcher.data.ClipStore.local(key) != null) return
        if (clipQueued.add(key)) clipQueue.trySend(key to url)
    }

    /** Update 148: every clip already looked up, saved on the device (Settings › Metadata › Save clips on the device). */
    fun saveAllClips() {
        com.shelf.launcher.data.ClipStore.init(getApplication())
        _uiState.value.games.forEach { g ->
            val u = videoUrlStore.get(g.stableKey)
            if (!u.isNullOrEmpty()) saveClipLater(g.stableKey, u)
        }
    }

    /** Completes once the saved game details are loaded. */
    private val savedMetadataLoaded = kotlinx.coroutines.CompletableDeferred<Unit>()


    /**
     * Called whenever the launcher comes back to the foreground. If a game was
     * launched from Shelf, that session ends here and is added to its total.
     */
    fun onLauncherResumed() {
        launcherResumed = true
        // Really back (not the screens settling right after a launch): nothing left to restore.
        if (ownLaunchPending && System.currentTimeMillis() - lastLaunchAt > 3_000L) {
            ownLaunchPending = false
            launchContextStore.clearPending()
        }
        // A resume in the first seconds after starting a game is just the screens settling
        // (companion screen, display switch), not the game ending. The game is still starting, so
        // do nothing at all here: keep the second-screen gate and companion/overlay as they are,
        // keep the session and its save watcher, and above all don't start the Drive watcher (it
        // would pull saves while the emulator loads them) or flip the foreground flags.
        val playPrefs = getApplication<Application>().getSharedPreferences("playtime", android.content.Context.MODE_PRIVATE)
        val openKey = playPrefs.getString("open_game", null)
        if (openKey != null && System.currentTimeMillis() - playPrefs.getLong("open_since", 0L) < 3_000L) return
        // Back in Voltcap: the second-screen home may show again, and apps
        // installed/removed while away are picked up.
        com.shelf.launcher.ui.SecondScreenGate.release()
        refreshAppsIfChanged()
        // Back on the home screen: the game is over, so whatever Voltcap put on
        // the other screen for it goes (the home screen's own second-screen
        // view takes over again). Not in the moment right after a launch,
        // where a resume is just the screens settling.
        if (System.currentTimeMillis() - lastPlacementAt > 3_000L) com.shelf.launcher.ui.CompanionActivity.closeAll()
        // Game session over: the performance overlay goes (not in the moment right after a launch, where a resume is just the screens settling).
        if (System.currentTimeMillis() - lastLaunchAt > 3_000L) {
            com.shelf.launcher.ui.PerfOverlayService.stop(getApplication<Application>())
            com.shelf.launcher.data.perf.PerfSampler.gamePackage = null
            com.shelf.launcher.data.GameMode.exit()
            // (update 91) The RetroAchievements overlay stops following the game (it's back on the next launch / overlay restore).
            raOverlayJob?.cancel()
            raOverlayJob = null
            com.shelf.launcher.data.RaOverlayData.clear()
            val app = getApplication<Application>()
            Thread {
                runCatching { com.shelf.launcher.device.tune.Tuner.onGameEnded(app) }
                // A game's own volume for its emulator ends with it.
                runCatching { com.shelf.launcher.device.audio.AppMixer.onGameEnded(app) }
            }.start()
            // Share the game just played (last-played on every device).
            syncLastPlayed(afterGame = true)
        } else syncLastPlayed(afterGame = false)
        // Still no ROMs (e.g. storage wasn't ready at boot): look again.
        // (update 91) Not while a silent scan is still retrying: each resume used to start another chain.
        if (currentRomGames.isEmpty() && !_uiState.value.isScanning && romRetryJob?.isActive != true && hasStoragePermission()) {
            scanLibrary(_uiState.value.romRootPath, silent = true)
        }
        finishUnlockWatch()
        finishSteamWatch()
        com.shelf.launcher.data.DiscordPresence.clear()
        com.shelf.launcher.data.DiscordPresence.setForeground(true)
        com.shelf.launcher.data.SyncNotifier.appInForeground = true
        // Which game was open (endSession forgets it, and drops sessions under 20 s from the playtime) is read above.
        // Play time counts only the minutes the game was really in front (with usage access).
        val endNow = System.currentTimeMillis()
        val activeMs = openKey?.let { k ->
            com.shelf.launcher.data.RunningGames.foregroundMs(
                getApplication(), com.shelf.launcher.data.RunningGames.packageFor(getApplication(), k),
                playPrefs.getLong("open_since", endNow), endNow
            )
        }
        val ended = playtimeStore.endSession(endNow, activeMs)
        // Update 274 (Toni: "Silver only says 3 hours", RP Classic): Voltcap is the home screen, so Home pressed mid-game
        // ended the session here, and going back into the game from Recents was never counted. Now every game still open
        // from an earlier launch (RunningGames) gets the time it was really in front while Voltcap was away.
        runCatching {
            val away = playtimeStore.awaySince()
            playtimeStore.clearAway()
            val app0 = getApplication<Application>()
            if (away in 1 until endNow && com.shelf.launcher.data.RunningGames.hasUsageAccess(app0)) {
                for (e in com.shelf.launcher.data.RunningGames.others(app0, openKey ?: "")) {
                    val ms = com.shelf.launcher.data.RunningGames.foregroundMs(app0, e.pkg, away, endNow) ?: continue
                    if (playtimeStore.addPlayed(e.key, endNow - ms, ms)) android.util.Log.i("Playtime", "away play: ${e.key} ${ms / 1000}s")
                }
            }
        }
        // The game is in the background now (still open until it's closed); check what's still running.
        com.shelf.launcher.data.RunningGames.noteLeft(getApplication())
        refreshRunning()
        com.shelf.launcher.data.EmulatorGuard.noteSessionEnded(getApplication())
        val syncTracked = openKey != null && afterGameSync(openKey)
        // Back on screen and no game open: listen for saves the other handheld uploads.
        if (!com.shelf.launcher.CrashGuard.safeMode) com.shelf.launcher.data.CloudSync.startWatching(getApplication())
        val key = ended?.first ?: openKey ?: return
        val duration = ended?.second ?: 0L
        val name = _uiState.value.games.firstOrNull { it.stableKey == key }?.name
        val totals = playtimeStore.loadTotals()
        // The post-session card (Appearance › Home screen › "Show a summary after playing"): time played,
        // total, and — once the last unlock check is back — achievements, count + % and the completion
        // change. Only for a real session (one that counted), never just for having looked at a game.
        val app = getApplication<Application>()
        // The summary is kept for the game's popup; only a brief line shows in the status bar (no card).
        val built = if (ended != null && name != null) {
            val sessions = playtimeStore.loadSessions().filter { it.key == key }
            com.shelf.launcher.ui.SessionSummary(
                gameKey = key, gameName = com.shelf.launcher.ui.gameDisplayName(name), sessionMs = duration,
                totalMs = totals[key] ?: duration, syncTracked = syncTracked,
                sessionCount = sessions.size,
                longestBefore = sessions.dropLast(1).maxOfOrNull { it.ms } ?: 0L
            ).withMilestone()
        } else null
        // The session's milestone goes into the journal (Recap, Game Journey) whether or not the card shows.
        built?.milestone?.let { m -> viewModelScope.launch(Dispatchers.IO) { com.shelf.launcher.data.PlayJournal.milestone(app, key, built.gameName, m) } }
        val summary = built
        _uiState.update { it.copy(playtime = totals, sessionSummary = summary) }
        if (summary != null && com.shelf.launcher.ui.SessionSummaryPrefs.enabled(app)) showSessionNote(summary)
    }

    private var sessionNoteJob: Job? = null

    /** "Played 42 min · +3 achievements" in the status bar for a few seconds. */
    private fun showSessionNote(s: com.shelf.launcher.ui.SessionSummary) {
        val parts = buildList {
            add("Played " + com.shelf.launcher.data.PlaytimeStore.format(s.sessionMs))
            if (s.achievementCount > 0) add("+${s.achievementCount} achievement${if (s.achievementCount == 1) "" else "s"}")
            s.milestone?.let { add(it) }
        }
        sessionNoteJob?.cancel()
        _uiState.update { it.copy(sessionNote = parts.joinToString(" · ")) }
        sessionNoteJob = viewModelScope.launch {
            kotlinx.coroutines.delay(7_000)
            _uiState.update { it.copy(sessionNote = null) }
        }
    }

    /** Checks which games are still open in the background (the glow on their cards). */
    fun refreshRunning() {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) { runCatching { com.shelf.launcher.data.RunningGames.refresh(app) } }
    }

    /** Other games open in the background (a different app than [game]'s): ask before starting [game]. */
    fun otherOpenGames(game: Game): List<Game> {
        val app = getApplication<Application>()
        val pkg = if (game.sourceType == SourceType.APP) game.packageName else emulatorPackageFor(game)
        // Only games known to be open: asking about one that may already be closed would just nag.
        val sure = com.shelf.launcher.data.RunningGames.confirmed
        val others = com.shelf.launcher.data.RunningGames.others(app, game.stableKey)
            .filter { it.pkg != pkg && it.key in sure }.map { it.key }.toSet()
        return _uiState.value.games.filter { it.stableKey in others }
    }

    /** X on a game that's still open: close it. */
    fun closeRunning(game: Game) {
        val app = getApplication<Application>()
        val name = com.shelf.launcher.ui.gameDisplayName(game.name)
        viewModelScope.launch {
            val pkg = com.shelf.launcher.data.RunningGames.packageFor(app, game.stableKey)
            val gone = withContext(Dispatchers.IO) { runCatching { com.shelf.launcher.data.RunningGames.close(app, game.stableKey) }.getOrDefault(false) }
            when {
                gone -> setStatusMessage("Closed $name.")
                !com.shelf.launcher.data.RunningGames.enhanced && pkg != null -> {
                    setStatusMessage("Android doesn't let Voltcap end $name here — tap Force stop on the page that opens.")
                    com.shelf.launcher.data.RunningGames.openAppInfo(app, pkg)
                }
                else -> setStatusMessage("Closed $name.")
            }
        }
    }

    /** The status-bar trophy goes once that game's achievements have been opened. */
    fun clearReturnTrophies(gameKey: String? = null) {
        val rt = _uiState.value.returnTrophies ?: return
        if (gameKey == null || rt.gameKey == gameKey) _uiState.update { it.copy(returnTrophies = null) }
    }

    fun dismissSessionSummary() {
        if (_uiState.value.sessionSummary != null) _uiState.update { it.copy(sessionSummary = null) }
    }

    /** Save sync dialog › Sync now: this game's saves, both ways. Progress shows in the dialog's live status. */
    fun syncGameNow(game: com.shelf.launcher.data.Game) {
        val app = getApplication<Application>()
        if (com.shelf.launcher.CrashGuard.safeMode || com.shelf.launcher.data.CloudSync.state.value.running) return
        viewModelScope.launch {
            val scoped = withContext(Dispatchers.IO) {
                game.takeIf { com.shelf.launcher.data.CloudSync.canWatchSaves(app, it) }
                    ?.let { com.shelf.launcher.data.CloudSync.ScopedGame.of(it) }
            } ?: return@launch
            val outcome = com.shelf.launcher.data.CloudSync.syncScoped(app, scoped, uploadOnly = false)
            com.shelf.launcher.data.SyncNotifier.messageFor(scoped.name, outcome)?.let {
                showSyncNotice(it, kind = com.shelf.launcher.data.SyncNotifier.kindFor(outcome))
            }
        }
    }

    /**
     * A game session just ended (whatever its length): stop the background save watcher, then send the
     * game's saves up — scoped first, then the normal full sync. The expedited work request repeats this
     * in WorkManager so it still finishes (or retries when offline) if Voltcap goes to the background
     * straight away or the process is killed.
     */
    private fun afterGameSync(openKey: String): Boolean {
        val app = getApplication<Application>()
        com.shelf.launcher.data.SaveWatchWorker.endSession(app)
        // (Update 222: the file watcher stays a while longer — emulators often write once more as they close.)
        com.shelf.launcher.data.SaveObserver.gameEnded()
        if (com.shelf.launcher.CrashGuard.safeMode) return false
        val game = _uiState.value.games.firstOrNull { it.stableKey == openKey }
        // Per-game opt-out skips only this game's scoped sync; the normal full sync below still covers every folder.
        val scoped = game?.takeIf { effectiveProfile(it).syncSaves.value && com.shelf.launcher.data.CloudSync.canWatchSaves(app, it) }
            ?.let { com.shelf.launcher.data.CloudSync.ScopedGame.of(it) }
        if (!com.shelf.launcher.data.CloudSync.isActive(app)) return false
        com.shelf.launcher.data.PostGameSyncWorker.enqueue(app, scoped)
        viewModelScope.launch {
            if (scoped != null) {
                val outcome = com.shelf.launcher.data.CloudSync.syncScoped(app, scoped, uploadOnly = false)
                com.shelf.launcher.data.SyncNotifier.messageFor(scoped.name, outcome)?.let {
                showSyncNotice(it, kind = com.shelf.launcher.data.SyncNotifier.kindFor(outcome))
            }
            }
            com.shelf.launcher.data.CloudSync.syncInBackground(app)
        }
        // True when this game's own saves are being synced (the summary card follows that).
        return scoped != null
    }

    /** Move relative to the current selection (holding the D-pad). Reads the
     * latest state, so fast key repeats never skip or repeat a step. */
    fun moveFocus(delta: Int) {
        val s = _uiState.value
        val size = s.visibleGames.size
        // A looping row wraps around from the last game to the first — and
        // keeps sliding even with one or two games (the step tells the row
        // which way to go, see GameDock).
        // Update 141, "Covers · first one wide": no endless row; ◀ on game 1 goes round to the last game (and ▶ on the
        // last one back to game 1), the fixed capsule staying where it is.
        if (com.shelf.launcher.ui.pinnedFirstOn(s.appearance) && size > 1) {
            setFocus((s.focusedIndex + delta).mod(size))
            return
        }
        // (Update 326, audit: not on a layout without Endless — Hero tiles: the R1 tile is hidden there, but a saved Endless
        // still sent the focus round from the last tile to the first.)
        if (s.appearance.loopRow && size >= 1 && com.shelf.launcher.ui.endlessApplies(s.appearance.homeLayout)) {
            val raw = s.focusedIndex + delta
            // The Voltcap row and (update 125) the other rows slide round on their own (EndlessRow.kt); the rest get a short
            // "comes back in from the other side".
            if ((raw < 0 || raw >= size) && size > 1 &&
                !com.shelf.launcher.ui.loopsOnItsOwn(s.appearance.homeLayout)) {
                com.shelf.launcher.ui.EdgeBump.wrap(if (delta > 0) 1 else -1)
            }
            setFocus(raw.mod(size), loopStep = delta)
        } else {
            // Endless is off: pushing past the first / last game bumps against the edge.
            if (size > 0 && (s.focusedIndex + delta < 0 || s.focusedIndex + delta >= size)) com.shelf.launcher.ui.EdgeBump.hit(if (delta > 0) 1 else -1)
            setFocus(s.focusedIndex + delta)
        }
    }

    /** The game key waiting to be saved as "last focused" (see [saveFocusSoon]). */
    private var unsavedFocusKey: String? = null
    private var focusSaveJob: Job? = null

    /** One state update per step; [loopStep] non-null also bumps the loop tick. */
    fun setFocus(index: Int, loopStep: Int? = null) {
        val visible = _uiState.value.visibleGames
        val clamped = index.coerceIn(0, (visible.size - 1).coerceAtLeast(0))
        _uiState.update {
            if (loopStep != null) it.copy(focusedIndex = clamped, loopTick = it.loopTick + 1, loopStep = loopStep)
            else it.copy(focusedIndex = clamped)
        }
        // Moving yourself cancels restoring last session's selection.
        pendingFocusKey = null
        restoreActive = false
        pendingDetailsKeyState.value = null
        // Saved (debounced) so the launcher reopens on the same game.
        visible.getOrNull(clamped)?.stableKey?.let { saveFocusSoon(it) }
    }

    private fun saveFocusSoon(key: String) {
        unsavedFocusKey = key
        focusSaveJob?.cancel()
        focusSaveJob = viewModelScope.launch {
            kotlinx.coroutines.delay(400)
            flushFocusSave()
        }
    }

    /** Writes the pending last-focused key to disk (off the main thread). */
    private suspend fun flushFocusSave() {
        val key = unsavedFocusKey ?: return
        unsavedFocusKey = null
        if (key == _uiState.value.categories.lastFocusedKey) return
        _uiState.update { it.copy(categories = it.categories.copy(lastFocusedKey = key)) }
        val toSave = _uiState.value.categories
        withContext(Dispatchers.IO) { categoryStore.save(toSave) }
    }

    fun setSearchQuery(query: String) {
        // 0.6.15 (#120, Thor): closing the search without typing jumped back to the category's first game.
        if (query == _uiState.value.searchQuery && searchFrom == null) return
        // (Update 258: a search that had to leave its category for All games goes back there once it's cleared.)
        val back = if (query.isBlank()) searchFrom.also { searchFrom = null } else null
        // 0.6.15 (#120): the game you were on when the search started is the one you're back on when it's cleared
        // (it went to the category's first game).
        val before = _uiState.value
        if (before.searchQuery.isBlank() && query.isNotBlank()) searchStartKey = before.visibleGames.getOrNull(before.focusedIndex)?.stableKey
        val returnTo = if (query.isBlank()) searchStartKey.also { searchStartKey = null } else null
        _uiState.update { it.copy(searchQuery = query, focusedIndex = 0) }
        if (back != null) {
            if (back.second != null) setSpecialFilter(back.second) else setPlatformFilter(back.first)
        }
        if (returnTo != null) {
            val i = _uiState.value.visibleGames.indexOfFirst { it.stableKey == returnTo }
            if (i > 0) _uiState.update { it.copy(focusedIndex = i) }
        }
    }

    /** 0.6.15: the game focused when the current search started (refocused when it's cleared). */
    private var searchStartKey: String? = null

    /** Update 258: the category a confirmed search left for All games (platform filter, special filter). */
    private var searchFrom: Pair<String?, SpecialFilter?>? = null

    /**
     * Update 258 (#120, RP Classic): a confirmed search that finds nothing in the category you're in but something in
     * your library shows All games with it (it said "No games match." in Continue playing for a game you have), and says
     * so; clearing the search puts you back in that category.
     */
    fun searchEverywhereIfEmpty() {
        val s = _uiState.value
        val q = s.searchQuery
        if (q.isBlank() || s.visibleGames.isNotEmpty() || (s.platformFilter == null && s.specialFilter == null)) return
        if (s.specialFilter == SpecialFilter.EMULATORS || s.platformFilter == ANDROID_GAMES_PLATFORM || s.platformFilter == "App") return
        val all = s.copy(platformFilter = null, specialFilter = null)
        val found = all.visibleGames.size
        if (found == 0) return
        val from = s.platformFilter to s.specialFilter
        // (Not remembered as your category: that stays the one you were in.)
        restoreActive = false
        rememberCategoryFocus()
        _uiState.update { it.copy(platformFilter = null, specialFilter = null, focusedIndex = 0) }
        searchFrom = from
        _uiState.update {
            it.copy(statusMessage = "Not in this category — " + (if (found == 1) "1 game" else "$found games") +
                " with “$q” in All games. B goes back.")
        }
    }

    fun setPlatformFilter(platform: String?) {
        restoreActive = false
        preferencesStore.setLastPlatformFilter(platform)
        preferencesStore.setLastSpecialFilter(null)
        rememberCategoryFocus()
        _uiState.update { it.copy(platformFilter = platform, specialFilter = null, focusedIndex = 0).let { n -> n.copy(focusedIndex = rememberedFocus(n)) } }
    }

    fun setSpecialFilter(filter: SpecialFilter?) {
        restoreActive = false
        preferencesStore.setLastSpecialFilter(filter?.name)
        preferencesStore.setLastPlatformFilter(null)
        rememberCategoryFocus()
        _uiState.update { it.copy(specialFilter = filter, platformFilter = null, focusedIndex = 0).let { n -> n.copy(focusedIndex = rememberedFocus(n)) } }
    }

    /** Update 186: a validated internet connection right now. */
    private fun deviceOnline(): Boolean = runCatching {
        val cm = getApplication<Application>().getSystemService(android.net.ConnectivityManager::class.java)
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }.getOrDefault(true)

    // ---- Update 185: each category remembers the game you were on ----
    /** Category key → the stable key of the game you were on there (this session). */
    private val categoryFocus = HashMap<String, String>()

    private fun categoryKeyOf(s: LauncherUiState): String =
        s.specialFilter?.let { "S:" + it.name } ?: s.platformFilter?.let { "P:$it" } ?: "ALL"

    /** Notes the game you're on in the category you're leaving. */
    private fun rememberCategoryFocus() {
        val s = _uiState.value
        if (s.searchQuery.isNotBlank()) return
        s.visibleGames.getOrNull(s.focusedIndex)?.let { categoryFocus[categoryKeyOf(s)] = it.stableKey }
    }

    /** Where to land in [s]'s category: the game you were on there last time (found by its key, wherever it is now), else the first. */
    private fun rememberedFocus(s: LauncherUiState): Int {
        val key = categoryFocus[categoryKeyOf(s)] ?: return 0
        return s.visibleGames.indexOfFirst { it.stableKey == key }.coerceAtLeast(0)
    }

    /** Loads every artwork alternative SteamGridDB has for a game, for the
     * picker. Cleared when the picker closes so it doesn't linger in state. */
    fun loadArtCandidates(game: Game) {
        artCandidatesPlatform = game.platform
        artCandidatesGame = game
        _uiState.update { it.copy(isLoadingArtCandidates = true, artCandidates = null) }
        viewModelScope.launch {
            val merged = collectArtCandidates(game, game.name, byName = false)
            _uiState.update { it.copy(artCandidates = merged, isLoadingArtCandidates = false) }
        }
    }

    /** Console of the game whose artwork picker is open, to search IGDB on it. */
    var artCandidatesPlatform: String? = null
    private var artCandidatesGame: Game? = null

    /** Re-runs the artwork search against a title the person typed instead of
     * the filename-derived one. */
    fun searchArtCandidates(query: String) {
        if (query.isBlank()) return
        _uiState.update { it.copy(isLoadingArtCandidates = true, artCandidates = null) }
        val game = artCandidatesGame
        viewModelScope.launch {
            val merged = collectArtCandidates(game, query.trim(), byName = true)
            _uiState.update { it.copy(artCandidates = merged, isLoadingArtCandidates = false) }
        }
    }

    /**
     * Every connected artwork source at once, in parallel, kept apart by
     * source so the picker can show just one of them. SteamGridDB first,
     * then IGDB, RAWG, ScreenScraper (ROMs) and TheGamesDB.
     */
    private suspend fun collectArtCandidates(game: Game?, name: String, byName: Boolean): ArtCandidates? =
        kotlinx.coroutines.coroutineScope {
            val platform = game?.platform ?: artCandidatesPlatform
            val sgdb = async {
                runCatching {
                    if (byName || game == null) repository.fetchArtCandidatesByName(name)
                    else repository.resolveGameId(game)?.let { repository.fetchArtCandidates(it) }
                }.getOrNull()
            }
            val igdb = async {
                igdbCredentialsStore.load()?.let {
                    runCatching { igdbRepository.fetchArtCandidates(it.clientId, it.clientSecret, name, platform) }.getOrNull()
                }
            }
            val rawg = async {
                rawgKeyStore.load()?.let { runCatching { rawgRepository.fetchArtCandidates(it, name, platform) }.getOrNull() }
            }
            val named = game?.copy(name = name)
            val ss = async {
                val creds = screenScraperStore.load()
                if (creds == null || named == null || named.sourceType != SourceType.ROM) null
                else runCatching { screenScraperRepository.fetchArt(creds, named) }.getOrNull()?.asCandidates()
            }
            val tgdb = async {
                val key = tgdbKeyStore.loadKey()
                if (key == null || named == null) null
                else runCatching { tgdbRepository.fetchArt(key, named) }.getOrNull()?.asCandidates()
            }
            val bySource = linkedMapOf<String, ArtCandidates>()
            listOf(
                "SteamGridDB" to sgdb.await(), "IGDB" to igdb.await(), "RAWG" to rawg.await(),
                "ScreenScraper" to ss.await(), "TheGamesDB" to tgdb.await()
            ).forEach { (label, c) ->
                if (c != null && (c.grids + c.wideGrids + c.heroes + c.logos).isNotEmpty()) bySource[label] = c
            }
            if (bySource.isEmpty()) return@coroutineScope null
            val all = bySource.values
            ArtCandidates(
                grids = all.flatMap { it.grids }.distinct(),
                wideGrids = all.flatMap { it.wideGrids }.distinct(),
                heroes = all.flatMap { it.heroes }.distinct(),
                logos = all.flatMap { it.logos }.distinct(),
                sources = bySource,
                animated = all.flatMapTo(HashSet()) { it.animated }
            )
        }

    /**
     * Artwork & details › "Compare sources": looks [game] up in IGDB, RAWG and
     * (for ROMs) ScreenScraper separately, so you can pick each detail from
     * whichever source has it best — say the time to beat from IGDB and the
     * Metacritic score from RAWG.
     */
    fun compareDetailSources(game: Game, searchName: String) {
        val key = game.stableKey
        if (key in _uiState.value.detailCandidatesLoading) return
        _uiState.update { it.copy(detailCandidatesLoading = it.detailCandidatesLoading + key) }
        viewModelScope.launch {
            val name = searchName.ifBlank { game.name }
            val results = kotlinx.coroutines.coroutineScope {
                val igdb = async {
                    igdbCredentialsStore.load()?.let {
                        runCatching { igdbRepository.fetchMetadata(it.clientId, it.clientSecret, name, game.platform) }.getOrNull()
                    }
                }
                val rawg = async {
                    rawgKeyStore.load()?.let { runCatching { rawgRepository.fetchMetadata(it, name, game.platform) }.getOrNull() }
                }
                val ss = async {
                    screenScraperStore.load()?.takeIf { game.sourceType == SourceType.ROM }?.let {
                        runCatching { screenScraperRepository.fetchMetadata(it, game) }.getOrNull()
                    }
                }
                listOf(
                    MetadataSource.IGDB to igdb.await(),
                    MetadataSource.RAWG to rawg.await(),
                    MetadataSource.SCREENSCRAPER to ss.await()
                ).mapNotNull { (src, meta) -> meta?.let { src to it } }.toMap()
            }
            _uiState.update {
                it.copy(
                    detailCandidates = it.detailCandidates + (key to results),
                    detailCandidatesLoading = it.detailCandidatesLoading - key,
                    metadataNotes = if (results.isEmpty()) {
                        it.metadataNotes + (key to "Nothing found for \"$name\". Try another name.")
                    } else it.metadataNotes - key
                )
            }
        }
    }

    /** Takes one detail ([field]) for [game] from [from] — a result of
     * [compareDetailSources] — and keeps everything else as it is. */
    fun applyDetailField(game: Game, field: com.shelf.launcher.data.DetailField, from: GameMetadata) {
        val key = game.stableKey
        val merged = _uiState.value.metadata[key].mergeFields(from, setOf(field))
        metadataCacheStore.put(key, merged)
        _uiState.update { it.copy(metadata = it.metadata + (key to merged)) }
    }

    /** Every detail from whichever compared source has it (IGDB first for
     * scores and playtimes, the longest summary, RAWG for Metacritic). */
    fun applyBestOfEachSource(game: Game) {
        val key = game.stableKey
        val found = _uiState.value.detailCandidates[key] ?: return
        val igdb = found[MetadataSource.IGDB]
        val rawg = found[MetadataSource.RAWG]
        val ss = found[MetadataSource.SCREENSCRAPER]
        val all = listOfNotNull(ss, igdb, rawg)
        if (all.isEmpty()) return
        val critic = igdb?.criticScore?.let { it to igdb.criticCount }
            ?: rawg?.criticScore?.let { it to rawg.criticCount }
            ?: ss?.criticScore?.let { it to ss.criticCount }
        val best = GameMetadata(
            title = all.firstNotNullOfOrNull { it.title },
            summary = all.mapNotNull { it.summary?.takeIf { s -> s.isNotBlank() } }.maxByOrNull { it.length },
            releaseYear = igdb?.releaseYear ?: ss?.releaseYear ?: rawg?.releaseYear,
            criticScore = critic?.first,
            criticCount = critic?.second,
            genres = all.flatMap { it.genres }.distinctBy { it.lowercase() }.take(4),
            developer = ss?.developer ?: igdb?.developer ?: rawg?.developer,
            hastilyHours = igdb?.hastilyHours ?: rawg?.hastilyHours,
            normallyHours = igdb?.normallyHours ?: rawg?.normallyHours ?: ss?.normallyHours,
            completelyHours = igdb?.completelyHours ?: rawg?.completelyHours,
            metacritic = rawg?.metacritic ?: igdb?.metacritic,
            publisher = ss?.publisher
        )
        val merged = _uiState.value.metadata[key].mergeFields(best, com.shelf.launcher.data.DetailField.values().toSet())
        metadataCacheStore.put(key, merged)
        _uiState.update {
            it.copy(
                metadata = it.metadata + (key to merged),
                metadataNotes = it.metadataNotes + (key to "Combined the best of " +
                    found.keys.joinToString(", ") { s -> s.label } + ".")
            )
        }
    }

    fun clearArtCandidates() {
        _uiState.update { it.copy(artCandidates = null, isLoadingArtCandidates = false) }
    }

    /** Sets one artwork slot for one game. [value] is either a scraper URL or
     * a content:// URI for a custom image from the device; null clears it. */
    fun setArtOverride(game: Game, slot: ArtSlot, value: String?) {
        artOverridesStore.set(game.stableKey, slot, value)
        _uiState.update { it.copy(artOverrides = artOverridesStore.loadAll()) }
    }

    /** Background upscaling for one game: null = automatic, 1 = off, 2/4/6/8 = times. */
    fun setHeroUpscale(game: Game, factor: Int?) {
        heroUpscaleStore.set(game.stableKey, factor)
        _uiState.update { it.copy(heroUpscale = heroUpscaleStore.loadAll()) }
    }

    fun clearArtOverrides(game: Game) {
        artOverridesStore.clear(game.stableKey)
        _uiState.update { it.copy(artOverrides = artOverridesStore.loadAll()) }
    }

    fun connectIgdb(clientId: String, clientSecret: String) {
        if (clientId.isBlank() || clientSecret.isBlank()) return
        viewModelScope.launch {
            val ok = igdbRepository.testConnection(clientId.trim(), clientSecret.trim())
            if (ok) {
                igdbCredentialsStore.save(IgdbCredentials(clientId.trim(), clientSecret.trim()))
                _uiState.update {
                    it.copy(
                        igdbConnected = true,
                        metadataReady = it.metadataSource == MetadataSource.IGDB || it.metadataReady,
                        statusMessage = "Connected to IGDB."
                    )
                }
                autoSaveDetails()
            } else {
                _uiState.update {
                    it.copy(
                        igdbConnected = false,
                        statusMessage = "Couldn't authenticate with IGDB — check the Client ID and Secret."
                    )
                }
            }
        }
    }

    fun connectRawg(key: String) {
        if (key.isBlank()) return
        viewModelScope.launch {
            val ok = rawgRepository.testConnection(key.trim())
            if (ok) {
                rawgKeyStore.save(key.trim())
                com.shelf.launcher.data.MetacriticFile.rawgConnected.value = true
                rawgTried.clear()
            }
            _uiState.update {
                it.copy(
                    rawgConnected = ok || it.rawgConnected,
                    metadataReady = it.metadataReady || (ok && it.metadataSource == MetadataSource.RAWG),
                    statusMessage = if (ok) "Connected to RAWG — Metacritic scores will show in game details."
                    else "Couldn't reach RAWG with that key."
                )
            }
        }
    }

    fun disconnectRawg() {
        rawgKeyStore.clear()
        com.shelf.launcher.data.MetacriticFile.rawgConnected.value = false
        _uiState.update {
            it.copy(
                rawgConnected = false,
                metadataReady = if (it.metadataSource == MetadataSource.RAWG) false else it.metadataReady
            )
        }
    }

    /** Adds RAWG's Metacritic score (and its average playtime when nothing
     * else has one) to details from another source. */
    private suspend fun withRawg(game: Game, meta: GameMetadata?, searchName: String = game.name): GameMetadata? {
        val key = rawgKeyStore.load() ?: return meta
        rawgTried += game.stableKey
        val rawg = runCatching { rawgRepository.fetchMetadata(key, searchName, game.platform) }.getOrNull()
            ?: return meta
        if (meta == null) return rawg
        return meta.copy(
            metacritic = rawg.metacritic ?: meta.metacritic,
            normallyHours = meta.normallyHours ?: rawg.normallyHours,
            summary = meta.summary ?: rawg.summary,
            releaseYear = meta.releaseYear ?: rawg.releaseYear,
            developer = meta.developer ?: rawg.developer,
            genres = meta.genres.ifEmpty { rawg.genres }
        )
    }

    fun disconnectIgdb() {
        igdbCredentialsStore.clear()
        _uiState.update {
            it.copy(
                igdbConnected = false,
                metadataReady = if (it.metadataSource == MetadataSource.IGDB) false else it.metadataReady
            )
        }
    }

    /**
     * Details for one game from the chosen source. With ScreenScraper chosen,
     * ROMs get ScreenScraper's details and IGDB (when connected) fills in what
     * ScreenScraper doesn't have — playtimes, and apps/Steam games entirely.
     */
    private suspend fun fetchMetadataFor(game: Game): GameMetadata? {
        // Known non-game apps (Moonlight, Steam Link, emulators…) never get a game's details by name
        // (Moonlight used to show Moonlighter's summary and scores).
        if (com.shelf.launcher.data.TitleMatch.skipScraping(game)) return null
        val igdb = igdbCredentialsStore.load()
        suspend fun fromIgdb(): GameMetadata? =
            igdb?.let { igdbRepository.fetchMetadata(it.clientId, it.clientSecret, game.name, game.platform) }

        val found = when (_uiState.value.metadataSource) {
            MetadataSource.IGDB -> fromIgdb()
            // RAWG first; IGDB (when connected) fills in the rushed /
            // completionist times RAWG doesn't have.
            MetadataSource.RAWG -> {
                val rawg = rawgKeyStore.load()?.let {
                    runCatching { rawgRepository.fetchMetadata(it, game.name, game.platform) }.getOrNull()
                }
                val igdbMeta = if (igdb != null) runCatching { fromIgdb() }.getOrNull() else null
                when {
                    rawg == null -> igdbMeta
                    igdbMeta == null -> rawg
                    else -> rawg.copy(
                        summary = rawg.summary ?: igdbMeta.summary,
                        hastilyHours = igdbMeta.hastilyHours,
                        normallyHours = igdbMeta.normallyHours ?: rawg.normallyHours,
                        completelyHours = igdbMeta.completelyHours,
                        criticCount = igdbMeta.criticCount.takeIf { rawg.criticScore == null },
                        criticScore = rawg.criticScore ?: igdbMeta.criticScore
                    )
                }
            }
            MetadataSource.SCREENSCRAPER -> {
                val creds = screenScraperStore.load()
                val primary = if (creds != null && game.sourceType == SourceType.ROM) {
                    screenScraperRepository.fetchMetadata(creds, game)
                } else null
                val extra = if (primary == null || primary.normallyHours == null) fromIgdb() else null
                when {
                    primary == null -> extra
                    extra == null -> primary
                    else -> primary.copy(
                        summary = primary.summary ?: extra.summary,
                        releaseYear = primary.releaseYear ?: extra.releaseYear,
                        criticScore = primary.criticScore ?: extra.criticScore,
                        criticCount = if (primary.criticScore != null) primary.criticCount else extra.criticCount,
                        genres = primary.genres.ifEmpty { extra.genres },
                        developer = primary.developer ?: extra.developer,
                        hastilyHours = extra.hastilyHours,
                        normallyHours = extra.normallyHours,
                        completelyHours = extra.completelyHours
                    )
                }
            }
            MetadataSource.COMBINED -> return fetchCombined(game, game.name)
            MetadataSource.NONE -> null
        }
        return if (_uiState.value.metadataSource == MetadataSource.NONE) null else withRawg(game, found)
    }

    /**
     * Every connected details source at once, merged field by field: the
     * longest summary, IGDB's critic score and playtimes, RAWG's Metacritic,
     * everyone's genres — so one source's gap is filled by another.
     */
    private suspend fun fetchCombined(game: Game, name: String): GameMetadata? = kotlinx.coroutines.coroutineScope {
        val igdbCreds = igdbCredentialsStore.load()
        val rawgKey = rawgKeyStore.load()
        val ssCreds = screenScraperStore.load()?.takeIf { game.sourceType == SourceType.ROM }
        val igdbJob = async {
            igdbCreds?.let { runCatching { igdbRepository.fetchMetadata(it.clientId, it.clientSecret, name, game.platform) }.getOrNull() }
        }
        val rawgJob = async { rawgKey?.let { runCatching { rawgRepository.fetchMetadata(it, name, game.platform) }.getOrNull() } }
        val ssJob = async { ssCreds?.let { runCatching { screenScraperRepository.fetchMetadata(it, game) }.getOrNull() } }
        val igdb = igdbJob.await()
        val rawg = rawgJob.await()
        val ss = ssJob.await()
        val all = listOfNotNull(ss, igdb, rawg)
        if (all.isEmpty()) return@coroutineScope null
        val critic = igdb?.criticScore?.let { it to igdb.criticCount }
            ?: rawg?.criticScore?.let { it to rawg.criticCount }
            ?: ss?.criticScore?.let { it to ss.criticCount }
        GameMetadata(
            title = all.firstNotNullOfOrNull { it.title },
            summary = all.mapNotNull { it.summary?.takeIf { s -> s.isNotBlank() } }.maxByOrNull { it.length },
            releaseYear = igdb?.releaseYear ?: ss?.releaseYear ?: rawg?.releaseYear,
            criticScore = critic?.first,
            criticCount = critic?.second,
            genres = all.flatMap { it.genres }.distinctBy { it.lowercase() }.take(4),
            developer = ss?.developer ?: igdb?.developer ?: rawg?.developer,
            hastilyHours = igdb?.hastilyHours ?: rawg?.hastilyHours,
            normallyHours = igdb?.normallyHours ?: rawg?.normallyHours ?: ss?.normallyHours,
            completelyHours = igdb?.completelyHours ?: rawg?.completelyHours,
            metacritic = rawg?.metacritic ?: igdb?.metacritic,
            publisher = ss?.publisher
        )
    }

    /** Fetches metadata for one game for the details window. No-op when it's
     * already cached or in flight. */
    fun loadMetadata(game: Game) {
        if (!savedMetadataLoaded.isCompleted) {
            viewModelScope.launch { savedMetadataLoaded.await(); loadMetadata(game) }
            return
        }
        if (com.shelf.launcher.data.TitleMatch.skipScraping(game)) {
            if (_uiState.value.metadata.containsKey(game.stableKey)) {
                _uiState.update { it.copy(metadata = it.metadata - game.stableKey) }
                metadataCacheStore.remove(listOf(game.stableKey))
            }
            return
        }
        val cached = _uiState.value.metadata[game.stableKey]
        if (cached != null) {
            // Saved before RAWG was connected: add the Metacritic score once.
            if (cached.metacritic == null && rawgKeyStore.load() != null && game.stableKey !in rawgTried) {
                viewModelScope.launch {
                    val merged = withRawg(game, cached) ?: return@launch
                    if (merged != cached) {
                        metadataCacheStore.put(game.stableKey, merged)
                        _uiState.update { it.copy(metadata = it.metadata + (game.stableKey to merged)) }
                    }
                }
            }
            return
        }
        if (game.stableKey in _uiState.value.metadataLoading) return

        _uiState.update { it.copy(metadataLoading = it.metadataLoading + game.stableKey) }
        viewModelScope.launch {
            val meta = runCatching { fetchMetadataFor(game) }.getOrNull()
            if (meta != null) metadataCacheStore.put(game.stableKey, meta)
            _uiState.update {
                it.copy(
                    metadata = if (meta != null) it.metadata + (game.stableKey to meta) else it.metadata,
                    metadataLoading = it.metadataLoading - game.stableKey
                )
            }
        }
    }

    /**
     * Downloads details for every game that doesn't have them yet and saves
     * them on the device, so the details window is instant (and works
     * offline) afterwards. Paced to stay well inside IGDB's and
     * ScreenScraper's rate limits.
     */
    fun setScrapeOptions(options: com.shelf.launcher.data.ScrapeOptions) {
        scrapeOptionsStore.save(options)
        _uiState.update { it.copy(scrapeOptions = options) }
    }

    /**
     * Downloads details in the background and saves them on the device, so
     * the details window is instant (and works offline) afterwards. Only the
     * kinds of detail ticked in [LauncherUiState.scrapeOptions] are fetched
     * and changed.
     *
     * [refreshAll] visits every game; otherwise only games missing one of
     * the ticked kinds (e.g. no Metacritic score yet) are visited. [silent]
     * is the automatic background run: games with no details at all only.
     * Paced to stay well inside IGDB's and ScreenScraper's rate limits.
     */
    fun scrapeMissingMetadata(silent: Boolean = false, refreshAll: Boolean = false) {
        if (metadataScrapeJob?.isActive == true) return
        val fields = _uiState.value.scrapeOptions.details
        val rawgOn = rawgKeyStore.load() != null
        // Metacritic alone only needs RAWG, whatever the main source is.
        val rawgOnly = fields.isNotEmpty() && fields.all { it == com.shelf.launcher.data.DetailField.METACRITIC }
        when {
            fields.isEmpty() -> {
                if (!silent) setStatusMessage("Tick at least one kind of detail to download.")
                return
            }
            rawgOnly && !rawgOn -> {
                if (!silent) setStatusMessage("Metacritic scores come from RAWG — connect it first (Settings › Accounts).")
                return
            }
            !rawgOnly && _uiState.value.metadataSource == MetadataSource.NONE -> {
                if (!silent) setStatusMessage("Pick a details source first (Settings › Metadata).")
                return
            }
            !rawgOnly && !_uiState.value.metadataReady -> {
                if (!silent) setStatusMessage("Connect ${_uiState.value.metadataSource.label} first (Settings › Accounts).")
                return
            }
        }

        metadataScrapeJob = viewModelScope.launch {
            savedMetadataLoaded.await()
            // Update 186: games the sources had nothing for are asked again automatically only every two weeks — the 182
            // without details were looked up again at every start ("Details 0/182" each time on the Thor).
            val askedPrefs = getApplication<Application>().getSharedPreferences("details_asked", android.content.Context.MODE_PRIVATE)
            val nowMs = System.currentTimeMillis()
            val askAgainAfter = 14L * 24 * 60 * 60 * 1000
            // Plain apps aren't games IGDB would know; Android games are looked up.
            val pending = _uiState.value.games.filter { game ->
                if (game.sourceType == SourceType.APP && !_uiState.value.isAndroidGame(game)) return@filter false
                if (com.shelf.launcher.data.TitleMatch.skipScraping(game)) return@filter false
                val saved = _uiState.value.metadata[game.stableKey]
                when {
                    silent -> saved == null && nowMs - askedPrefs.getLong(game.stableKey, 0L) > askAgainAfter
                    refreshAll -> true
                    else -> saved.lacksAny(fields, rawgOn)
                }
            }
            if (pending.isEmpty()) {
                if (!silent) setStatusMessage("Nothing missing — every game already has what's ticked.")
                return@launch
            }

            _uiState.update { it.copy(metadataScrapeDone = 0, metadataScrapeTotal = pending.size) }
            var found = 0
            // Update 175: three games at once, each lane pausing a little between its games (was one game and
            // 0.6 s at a time). IGDB allows four requests a second; ScreenScraper keeps its own limit.
            val lanes = kotlinx.coroutines.sync.Semaphore(DETAIL_LANES)
            var done = 0
            kotlinx.coroutines.coroutineScope { for (game in pending) {
                lanes.acquire()
                if (!isActive) { lanes.release(); break }
                launch { try {
                com.shelf.launcher.data.GameMode.awaitNotPlaying()
                // Update 182: the automatic details give way to the artwork while it's being looked up. Both ask
                // ScreenScraper, which answers one request at a time, so "Getting art" stood at 1 until every game's
                // details were done ("Getting art 1/215 · Details 134/182" on the Thor).
                if (silent) while (scrapeJob?.isActive == true) kotlinx.coroutines.delay(1_500L)
                val fresh = runCatching {
                    if (rawgOnly) {
                        rawgKeyStore.load()?.let { rawgRepository.fetchMetadata(it, game.name, game.platform) }
                    } else {
                        fetchMetadataFor(game)
                    }
                }.getOrNull()
                // (Only while online: a lookup that failed for want of a connection is tried again next time.)
                if (deviceOnline()) askedPrefs.edit().putLong(game.stableKey, System.currentTimeMillis()).apply()
                if (fresh != null) {
                    val saved = _uiState.value.metadata[game.stableKey]
                    val merged = saved.mergeFields(fresh, fields)
                    if (merged != saved) found++
                    metadataCacheStore.put(game.stableKey, merged)
                    _uiState.update { it.copy(metadata = it.metadata + (game.stableKey to merged)) }
                }
                done++
                _uiState.update { it.copy(metadataScrapeDone = done) }
                kotlinx.coroutines.delay(DETAIL_LANE_PAUSE_MS)
                } finally { lanes.release() } }
            } }
            _uiState.update {
                it.copy(
                    metadataScrapeDone = 0,
                    metadataScrapeTotal = 0,
                    statusMessage = if (silent) it.statusMessage else "Updated details for $found of ${pending.size} games."
                )
            }
            if (_uiState.value.appearance.genreCollections) refreshCollections()
        }
    }

    fun cancelMetadataScrape() {
        metadataScrapeJob?.cancel()
        _uiState.update { it.copy(metadataScrapeDone = 0, metadataScrapeTotal = 0) }
    }

    /** Forgets all saved details, e.g. to redo wrong matches. */
    fun clearSavedMetadata() {
        metadataScrapeJob?.cancel()
        metadataCacheStore.clear()
        _uiState.update {
            it.copy(metadata = emptyMap(), metadataScrapeDone = 0, metadataScrapeTotal = 0,
                statusMessage = "Saved game details cleared.")
        }
    }

    fun setMetadataSource(source: MetadataSource) {
        metadataSourceStore.saveSource(source)
        _uiState.update {
            it.copy(
                metadataSource = source,
                metadataReady = when (source) {
                    MetadataSource.IGDB -> igdbCredentialsStore.load() != null
                    MetadataSource.SCREENSCRAPER -> screenScraperStore.load() != null
                    MetadataSource.RAWG -> rawgKeyStore.load() != null
                    MetadataSource.COMBINED -> igdbCredentialsStore.load() != null || rawgKeyStore.load() != null ||
                        screenScraperStore.load() != null
                    MetadataSource.NONE -> false
                }
            )
        }
    }

    fun setArtSource(source: ArtSource) {
        tgdbKeyStore.saveSource(source)
        _uiState.update { it.copy(artSource = source) }
    }

    fun connectTgdb(apiKey: String) {
        if (apiKey.isBlank()) return
        viewModelScope.launch {
            val ok = tgdbRepository.testConnection(apiKey.trim())
            if (ok) {
                tgdbKeyStore.saveKey(apiKey.trim())
                _uiState.update {
                    it.copy(tgdbConnected = true, statusMessage = "Connected to TheGamesDB.")
                }
            } else {
                _uiState.update {
                    it.copy(
                        tgdbConnected = false,
                        statusMessage = "Couldn't reach TheGamesDB — check the API key."
                    )
                }
            }
        }
    }

    fun disconnectTgdb() {
        tgdbKeyStore.clearKey()
        _uiState.update { it.copy(tgdbConnected = false) }
    }

    /** Checks the ScreenScraper credentials against the API before saving —
     * a wrong developer key would otherwise fail silently on every game. */
    fun connectScreenScraper(devId: String, devPassword: String, username: String, password: String) {
        val creds = ScreenScraperCredentials(
            devId = devId.trim(),
            devPassword = devPassword.trim(),
            username = username.trim().ifBlank { null },
            password = password.ifBlank { null }
        )
        if (creds.devId.isBlank() || creds.devPassword.isBlank()) {
            setStatusMessage("ScreenScraper needs a developer ID and password.")
            return
        }
        _uiState.update { it.copy(statusMessage = "Testing ScreenScraper…") }
        viewModelScope.launch {
            val error = screenScraperRepository.testConnection(creds)
            if (error == null) {
                screenScraperStore.save(creds)
                _uiState.update {
                    it.copy(
                        screenScraperConnected = true,
                        screenScraperUsername = creds.username,
                        metadataReady = it.metadataReady || it.metadataSource == MetadataSource.SCREENSCRAPER,
                        statusMessage = "Connected to ScreenScraper."
                    )
                }
                autoSaveDetails()
            } else {
                _uiState.update { it.copy(screenScraperConnected = false, statusMessage = error) }
            }
        }
    }

    fun disconnectScreenScraper() {
        screenScraperStore.clear()
        _uiState.update {
            it.copy(
                screenScraperConnected = false,
                screenScraperUsername = null,
                metadataReady = if (it.metadataSource == MetadataSource.SCREENSCRAPER) false else it.metadataReady
            )
        }
    }

    /** Fetches art from whichever source is selected, falling back to the
     * other if the preferred one has nothing — better a cover from the second
     * source than a blank card. */
    // ---- Save Vault: art for saves whose game isn't in this library (found on a memory card / synced) ----
    private val _vaultArt = MutableStateFlow<Map<String, String>>(emptyMap())
    /** Game key → cover for Save Vault games that aren't on this device (looked up by name, kept in prefs). */
    val vaultArt: StateFlow<Map<String, String>> = _vaultArt.asStateFlow()
    @Volatile private var vaultArtRunning = false
    /** Update 317as: games asked for while a look-up was running (they were dropped), and the rest past a pass's 40. */
    private val vaultArtPending = java.util.concurrent.ConcurrentLinkedQueue<Game>()

    /**
     * Looks up a cover for each of [games] (Save Vault entries without a ROM here) by its name, from the art sources
     * you have (SteamGridDB first). Names from memory cards are often run together ("WWESmackDown!vs.RAW"), so they're
     * spaced out first. Found covers are kept; a game with none is tried again after a week.
     */
    /**
     * Update 318 (Toni: "Stats don't show pictures of games that aren't on the device — scrape them before"): the covers of
     * every game in the play history that isn't in the library — played on another device, or here and since removed —
     * looked up ahead (most played first) and kept, so the recaps have them when opened. Removed games are searched by the
     * name in their file's address, the console guessed from its folder (YearRecapDialog asks the same way).
     */
    @Volatile private var stillBackfillRunning = false

    /**
     * Update 319 (Toni: "I don't see the still covers on discs and cartridges"): a cover scraped animated before update
     * 317 has no still one kept beside it, so its cartridges and discs had nothing else to wear. Those games' first still
     * covers are looked up now (40 a pass, each game tried once a week), kept with their art and shown at once.
     */
    fun backfillStillCovers() {
        if (stillBackfillRunning) return
        stillBackfillRunning = true
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prefs = app.getSharedPreferences("still_cover_backfill", android.content.Context.MODE_PRIVATE)
                val now = System.currentTimeMillis()
                val st = _uiState.value
                // (Update 321: animated covers you picked yourself (Artwork & details) get one too, kept by their address.)
                prefs.all.forEach { (k, v) -> if (k.startsWith("map:") && v is String) com.shelf.launcher.data.StillCovers.put(k.removePrefix("map:"), v) }
                val picked = st.games.mapNotNull { g ->
                    val c = st.artOverrides[g.stableKey]?.cover
                    if (c != null && com.shelf.launcher.data.AnimatedArt.looksAnimated(c) && !prefs.contains("map:$c") &&
                        now - prefs.getLong("o:" + g.stableKey, 0L) > 7L * 86_400_000L) g to c else null
                }
                for ((g, c) in picked.take(20)) {
                    val still = repository.fetchStillGrid(g)
                    prefs.edit().putLong("o:" + g.stableKey, now).apply { if (still != null) putString("map:$c", still) }.apply()
                    // (Worn from the next time its cartridge or disc is drawn.)
                    if (still != null) com.shelf.launcher.data.StillCovers.put(c, still)
                }
                val todo = st.games.filter { g ->
                    val a = st.art[g.stableKey]
                    a != null && a.gridUrl != null && a.stillGridUrl == null && com.shelf.launcher.data.AnimatedArt.looksAnimated(a.gridUrl) &&
                        now - prefs.getLong(g.stableKey, 0L) > 7L * 86_400_000L
                }
                for (g in todo.take(40)) {
                    val still = repository.fetchStillGrid(g)
                    prefs.edit().putLong(g.stableKey, now).apply()
                    if (still != null) {
                        val cur = _uiState.value.art[g.stableKey] ?: continue
                        val newArt = cur.copy(stillGridUrl = still)
                        artCacheStore.put(g.stableKey, newArt)
                        com.shelf.launcher.data.StillCovers.note(newArt)
                        _uiState.update { it.copy(art = it.art + (g.stableKey to newArt)) }
                    }
                }
            } finally {
                stillBackfillRunning = false
            }
        }
    }

    fun prefetchStatsArt() {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val lib = _uiState.value.games.mapTo(HashSet()) { it.stableKey }
            val here = runCatching { PlaytimeStore(app).loadSessions() }.getOrDefault(emptyList())
            val there = runCatching { com.shelf.launcher.data.OtherDevices.sessions(app) }.getOrDefault(emptyList())
            val info = runCatching { com.shelf.launcher.data.OtherDevices.info(app) }.getOrDefault(emptyMap())
            val wanted = (here + there).groupBy { it.key }.mapValues { (_, s) -> s.sumOf { it.ms } }
                .entries.sortedByDescending { it.value }.map { it.key }
                .filter { it !in lib }
                .mapNotNull { k ->
                    info[k]?.let { i -> Game(i.name.ifBlank { k.substringAfterLast('/') }, i.platform, 0, SourceType.ROM, romPath = k) }
                        ?: if (k.contains('/')) com.shelf.launcher.data.goneGame(k) else null
                }
            if (wanted.isNotEmpty()) withContext(Dispatchers.Main) { loadVaultArt(wanted) }
        }
    }

    fun loadVaultArt(games: List<Game>) {
        val app = getApplication<android.app.Application>()
        val prefs = app.getSharedPreferences("save_vault_art", android.content.Context.MODE_PRIVATE)
        if (_vaultArt.value.isEmpty()) {
            _vaultArt.value = prefs.all.mapNotNull { (k, v) -> if (k.startsWith("art:") && v is String) k.removePrefix("art:") to v else null }.toMap()
        }
        if (vaultArtRunning) { vaultArtPending.addAll(games); return }
        val todo = games.distinctBy { it.stableKey }.filter { g ->
            g.stableKey !in _vaultArt.value &&
                System.currentTimeMillis() - prefs.getLong("tried:${g.stableKey}", 0L) > 7L * 86_400_000L
        }
        if (todo.isEmpty()) return
        vaultArtRunning = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                vaultArtPending.addAll(todo.drop(40))
                for (g in todo.take(40)) {
                    val search = g.copy(name = vaultSearchName(g.name), romPath = null)
                    val art = runCatching { fetchArtFromSelectedSource(search) }.getOrNull()
                    val url = art?.gridUrl ?: art?.wideGridUrl ?: art?.heroUrl
                    val e = prefs.edit().putLong("tried:${g.stableKey}", System.currentTimeMillis())
                    if (url != null) {
                        e.putString("art:${g.stableKey}", url)
                        _vaultArt.update { it + (g.stableKey to url) }
                    }
                    e.apply()
                }
            } finally {
                vaultArtRunning = false
                // (Update 317as) Then the ones that waited.
                val next = generateSequence { vaultArtPending.poll() }.toList()
                if (next.isNotEmpty()) launch(Dispatchers.Main) { loadVaultArt(next) }
            }
        }
    }

    /** "WWESmackDown!vs.RAW" → "WWE Smack Down vs RAW": a memory card's run-together title, spaced for a search. */
    private fun vaultSearchName(raw: String): String = raw
        .replace(Regex("[!._:\\-]+"), " ")
        .replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
        .replace(Regex("(?<=[A-Z])(?=[A-Z][a-z])"), " ")
        .replace(Regex("(?<=[A-Za-z])(?=[0-9])|(?<=[0-9])(?=[A-Za-z])"), " ")
        .replace(Regex("\\s+"), " ").trim()

    private suspend fun fetchArtFromSelectedSource(original: Game): GameArt? {
        // Known non-game apps (Moonlight, Steam Link, emulators, tools…) keep their own icon: they are
        // never matched to a game by name. A SteamGridDB id set by hand still works.
        if (com.shelf.launcher.data.TitleMatch.skipScraping(original) && original.sgdbGameId == null) return null
        val tgdbKey = tgdbKeyStore.loadKey()
        val screenScraper = screenScraperStore.load()
        // App names often carry ™ / ® that no database has in its titles.
        val game = if (original.sourceType == SourceType.APP) {
            original.copy(name = original.name.replace(Regex("[™®©]"), "").trim())
        } else original

        // The chosen source first, then the rest — and instead of stopping at
        // the first source with a cover, the others fill in whatever is still
        // missing. Android games especially often have a cover on
        // SteamGridDB but no wide capsule or background; IGDB / RAWG artwork
        // fills those. ScreenScraper only knows ROMs.
        val main = _uiState.value.artSource
        // Update 137: each kind of picture can come from its own source first (Settings › Metadata › Artwork source
        // per look); the rest follow the main source, then the others.
        val F = com.shelf.launcher.data.Features
        fun pick(p: com.shelf.launcher.data.Features.StringPref): ArtSource =
            p.value.takeIf { it.isNotBlank() }?.let { n -> runCatching { ArtSource.valueOf(n) }.getOrNull() } ?: main
        val firstFor = mapOf("grid" to pick(F.artSourceCover), "wide" to pick(F.artSourceWide), "hero" to pick(F.artSourceHero), "logo" to pick(F.artSourceLogo))
        val order = (firstFor.values.toList() + main + ArtSource.values()).distinct()
        fun orderFor(field: String): List<ArtSource> = (listOf(firstFor.getValue(field), main) + ArtSource.values()).distinct()

        val found = LinkedHashMap<ArtSource, GameArt>()
        var merged: GameArt? = null
        for (source in order) {
            val art = runCatching {
                when (source) {
                    ArtSource.STEAMGRIDDB -> repository.fetchArt(game)
                    ArtSource.THEGAMESDB -> tgdbKey?.let { tgdbRepository.fetchArt(it, game) }
                    ArtSource.IGDB -> igdbCredentialsStore.load()?.let {
                        igdbRepository.fetchArt(it.clientId, it.clientSecret, game.name, game.platform)
                    }
                    ArtSource.RAWG -> rawgKeyStore.load()?.let {
                        rawgRepository.fetchArt(it, game.name, game.platform)
                    }
                    ArtSource.SCREENSCRAPER ->
                        if (screenScraper != null && game.sourceType == SourceType.ROM && !com.shelf.launcher.data.PinnedShortcuts.isShortcut(game)) {
                            screenScraperRepository.fetchArt(screenScraper, game)
                        } else null
                }
            }.getOrNull() ?: continue
            found[source] = art
            // Each picture from the first source (in its own order) that has one.
            fun <T> first(field: String, get: (GameArt) -> T?): T? = orderFor(field).firstNotNullOfOrNull { s -> found[s]?.let(get) }
            merged = art.copy(
                gridUrl = first("grid") { it.gridUrl },
                wideGridUrl = first("wide") { it.wideGridUrl },
                heroUrl = first("hero") { it.heroUrl },
                logoUrl = first("logo") { it.logoUrl },
                animatedHeroUrl = found.values.firstNotNullOfOrNull { it.animatedHeroUrl }
            )
            val m = merged
            // Done once the sources picked first have all been asked and nothing is missing.
            val askedPicked = order.indexOf(source) >= firstFor.values.maxOf { order.indexOf(it) }
            if (askedPicked && m.gridUrl != null && m.heroUrl != null && (m.wideGridUrl != null || original.sourceType == SourceType.ROM)) break
        }
        return merged
    }

    fun setTheme(theme: AppTheme) {
        themeStore.save(theme)
        _uiState.update { it.copy(appTheme = theme) }
    }

    /**
     * Cycles the category filter: All → Favorites → Recent → each platform →
     * back to All. Bound to the triggers (L2/R2).
     */
    fun cycleCategory(forward: Boolean) {
        val state = _uiState.value
        // (Update 208: debug builds — how long the switch takes to settle, logged as "VoltcapPerf".)
        com.shelf.launcher.ui.PerfDiag.mark(if (forward) "Category switch (R2)" else "Category switch (L2)")
        val stops = categoryStops(state)
        if (stops.isEmpty()) return
        val current = stops.indexOfFirst { (special, platform) ->
            special == state.specialFilter && platform == state.platformFilter
        }.coerceAtLeast(0)
        // (Update 211: past categories with nothing in them — on the RP Classic, R2 stopped on "Other apps" with
        // "No games match." Only when every other one is empty does it go there.)
        val step = if (forward) 1 else -1
        var next = ((current + step) + stops.size) % stops.size
        for (tries in 1 until stops.size) {
            val (s, p) = stops[next]
            val empty = runCatching { state.copy(specialFilter = s, platformFilter = p).visibleGames.isEmpty() }.getOrDefault(false)
            if (!empty) break
            next = ((next + step) + stops.size) % stops.size
        }
        val (special, platform) = stops[next]
        when {
            special != null -> setSpecialFilter(special)
            else -> setPlatformFilter(platform)
        }
    }

    /**
     * Update 219 (#04): goes straight to the category [steps] places from the one you're in, in the same list the
     * style homes draw as tabs (styleStops). Tapping a tab used to repeat [cycleCategory] [steps] times, and that
     * skips empty categories, so a tap two or more tabs away (e.g. on "Continue playing") could open a different
     * category. One step (L2 / R2 or the tab next door) still goes through [cycleCategory].
     */
    fun jumpCategory(steps: Int) {
        if (steps == 0) return
        val state = _uiState.value
        val stops = categoryStops(state)
        if (stops.isEmpty()) return
        // (Outside the list, e.g. a hidden start category: -1, as styleStops counts it too.)
        val current = stops.indexOfFirst { (special, platform) -> special == state.specialFilter && platform == state.platformFilter }
        if (kotlin.math.abs(steps) == 1 && current >= 0) { cycleCategory(steps > 0); return }
        com.shelf.launcher.ui.PerfDiag.mark("Category switch (tab)")
        val (special, platform) = stops[(current + steps).mod(stops.size)]
        when {
            special != null -> setSpecialFilter(special)
            else -> setPlatformFilter(platform)
        }
    }

    /** The category stops L2 / R2 and the style homes' tabs go through: ALL, each special, each console, each list. */
    private fun categoryStops(state: LauncherUiState): List<Pair<SpecialFilter?, String?>> =
        // Flatten the order into stops: ALL, each special, each console.
        // (Update 214: in your own order of the tiles too — CategoryTileOrder — as the category screen shows them.)
        com.shelf.launcher.data.CategoryTileOrder.sort(state.categoryOrder.flatMap { key ->
            when (key) {
                CategorySettings.ALL -> listOf<Pair<SpecialFilter?, String?>>(null to null)
                // (Update 208: in your own console order — Choose a category › Reorder — as the category screen shows them.)
                CategorySettings.CONSOLES -> com.shelf.launcher.data.PlatformOrder.sort(state.availablePlatforms).map { null to it }
                CategorySettings.COLLECTIONS -> state.collections.map { null to it.key }
                else -> runCatching { SpecialFilter.valueOf(key) }.getOrNull()?.let { listOf(it to null) }.orEmpty()
            }
        }) { (special, platform) -> special?.name ?: platform?.let { "p:$it" } ?: "ALL" }
            // (Update 325: not the categories taken off the category screen — CategoryTileOrder.isHidden.)
            .filterNot { (special, platform) -> com.shelf.launcher.data.CategoryTileOrder.isHidden(special?.name ?: platform?.let { "p:$it" } ?: "ALL") }

    // ---- Category order, and where you left off ----

    private fun updateCategories(transform: (CategorySettings) -> CategorySettings) {
        val updated = transform(_uiState.value.categories)
        categoryStore.save(updated)
        _uiState.update { it.copy(categories = updated) }
    }

    /** Moves a category one place earlier (-1) or later (+1). */
    fun moveCategory(key: String, delta: Int) = updateCategories { current ->
        val known = CategorySettings.known(SpecialFilter.values().map { it.name })
        val order = current.normalized(known).toMutableList()
        val from = order.indexOf(key)
        val to = (from + delta).coerceIn(0, order.lastIndex)
        if (from < 0 || from == to) return@updateCategories current
        order.add(to, order.removeAt(from))
        current.copy(order = order)
    }

    fun toggleCategoryHidden(key: String) = updateCategories {
        it.copy(hidden = if (key in it.hidden) it.hidden - key else it.hidden + key)
    }

    fun resetCategoryOrder() = updateCategories { it.copy(order = CategorySettings.DEFAULT_ORDER, hidden = emptySet()) }

    fun rememberSettingsSection(section: String) {
        if (_uiState.value.categories.lastSettingsSection == section) return
        updateCategories { it.copy(lastSettingsSection = section) }
    }

    /** Quick jump: focus the first visible game whose name starts with [letter]
     * ('#' = anything that doesn't start with a letter). */
    fun jumpToLetter(letter: Char) {
        val index = _uiState.value.visibleGames.indexOfFirst { quickJumpLetter(it.name) == letter }
        if (index >= 0) setFocus(index)
    }

    // ---- L1: app shortcuts ----

    fun addShortcut(packageName: String) {
        shortcutsStore.add(packageName)
        refreshShortcuts()
    }

    /** New shortcut order, as package names (Edit mode in the L1 panel). */
    fun reorderShortcuts(order: List<String>) {
        shortcutsStore.reorder(order)
        refreshShortcuts()
    }

    fun removeShortcut(packageName: String) {
        shortcutsStore.remove(packageName)
        refreshShortcuts()
    }

    /** Resolves stored package names against the installed-app list, so an
     * uninstalled app simply disappears from the panel instead of leaving a
     * shortcut that fails on tap. */
    private fun refreshShortcuts() {
        val wanted = shortcutsStore.load()
        val apps = allScannedApps.associateBy { it.packageName }
        _uiState.update { it.copy(shortcuts = wanted.mapNotNull { pkg -> apps[pkg] ?: com.shelf.launcher.data.BuiltinShortcuts.game(pkg) }) }
    }

    // ---- R1: notifications + media ----

    /** Pulls the current notification list and media session. Called when the
     * panel opens rather than observed continuously — there's no point holding
     * a subscription open while someone is browsing games. */
    fun refreshNotifications() {
        val context = getApplication<Application>()
        try {
            val hasAccess = ShelfNotificationListener.hasAccess(context)
            val playing = runCatching { ShelfNotificationListener.nowPlaying(context)?.first }.getOrNull()
            ShelfNotificationListener.flushIfDirty()
            // Just-cleared ones stay hidden while Android is still removing them (no flicker back).
            val now = System.currentTimeMillis()
            synchronized(recentlyDismissed) { recentlyDismissed.values.removeAll { now - it > 5_000 } }
            val hidden = synchronized(recentlyDismissed) { recentlyDismissed.keys.toSet() }
            val notes = runCatching { ShelfNotificationListener.notifications.value }
                .getOrDefault(emptyList()).filterNot { it.key in hidden }
            _uiState.update {
                it.copy(
                    hasNotificationAccess = hasAccess,
                    notifications = notes,
                    nowPlaying = playing,
                    notificationError = null
                )
            }
        } catch (t: Throwable) {
            // Catching Throwable on purpose: this panel has crashed twice and
            // a launcher going down on a button press is worse than a panel
            // that explains itself.
            _uiState.update {
                it.copy(
                    hasNotificationAccess = false,
                    notifications = emptyList(),
                    nowPlaying = null,
                    notificationError = "${t::class.java.simpleName}: ${t.message ?: "no message"}"
                )
            }
        }
    }

    private val recentlyDismissed = mutableMapOf<String, Long>()

    fun dismissNotification(key: String) {
        synchronized(recentlyDismissed) { recentlyDismissed[key] = System.currentTimeMillis() }
        // Gone from the list at once; Android catches up in the background.
        _uiState.update { st -> st.copy(notifications = st.notifications.filterNot { it.key == key }) }
        viewModelScope.launch(Dispatchers.Default) {
            ShelfNotificationListener.dismiss(key)
            kotlinx.coroutines.delay(400)
            withContext(Dispatchers.Main) { refreshNotifications() }
        }
    }

    /** "Clear all": the list empties instantly, then one batched cancel and one refresh. */
    fun dismissAllNotifications() {
        val keys = _uiState.value.notifications.map { it.key }
        if (keys.isEmpty()) return
        val now = System.currentTimeMillis()
        synchronized(recentlyDismissed) { keys.forEach { recentlyDismissed[it] = now } }
        _uiState.update { it.copy(notifications = emptyList()) }
        viewModelScope.launch(Dispatchers.Default) {
            ShelfNotificationListener.dismissAll(keys)
            kotlinx.coroutines.delay(500)
            withContext(Dispatchers.Main) { refreshNotifications() }
        }
    }

    fun mediaPlayPause() = withMediaController { controller ->
        val playing = controller.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
        if (playing) controller.transportControls.pause()
        else {
            com.shelf.launcher.ui.BackgroundMusicGate.otherMusicStarting()
            controller.transportControls.play()
        }
    }

    fun mediaNext() = withMediaController {
        com.shelf.launcher.ui.BackgroundMusicGate.songChanging()
        it.transportControls.skipToNext()
    }

    fun mediaSeekTo(positionMs: Long) = withMediaController { it.transportControls.seekTo(positionMs.coerceAtLeast(0L)) }

    /** Just the playing track, without re-reading notifications — cheap enough
     * to call every second while the player or screensaver is on screen. */
    fun refreshNowPlaying() {
        val playing = runCatching { ShelfNotificationListener.nowPlaying(getApplication())?.first }.getOrNull()
        if (playing != _uiState.value.nowPlaying) _uiState.update { it.copy(nowPlaying = playing) }
    }

    fun setScreensaver(settings: ScreensaverSettings) {
        screensaverStore.save(settings)
        _uiState.update { it.copy(screensaver = settings) }
    }

    fun mediaPrevious() = withMediaController {
        com.shelf.launcher.ui.BackgroundMusicGate.songChanging()
        it.transportControls.skipToPrevious()
    }

    private fun withMediaController(action: (android.media.session.MediaController) -> Unit) {
        runCatching {
            val pair = ShelfNotificationListener.nowPlaying(getApplication()) ?: return@runCatching
            action(pair.second)
        }
        refreshNotifications()
        // (Update 229, #03: after ◀ / ▶ the old title stayed — the player hadn't switched yet when we read it)
        nowPlayingFollowJob?.cancel()
        nowPlayingFollowJob = viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            refreshNowPlaying()
            kotlinx.coroutines.delay(700)
            refreshNowPlaying()
        }
    }
    private var nowPlayingFollowJob: Job? = null

    /**
     * Plays / pauses Spotify without opening it (hotkey). Uses Spotify's own
     * media session when it has one — even when something else is playing —
     * otherwise sends it the "play" media button, which starts the last
     * track in the background.
     */
    fun playPauseSpotify() {
        val app = getApplication<Application>()
        val spotify = "com.spotify.music"
        runCatching { app.packageManager.getPackageInfo(spotify, 0) }.getOrNull() ?: run {
            setStatusMessage("Spotify isn't installed.")
            return
        }
        val controller = runCatching {
            val manager = app.getSystemService(android.content.Context.MEDIA_SESSION_SERVICE)
                as android.media.session.MediaSessionManager
            manager.getActiveSessions(android.content.ComponentName(app, ShelfNotificationListener::class.java))
                .firstOrNull { it.packageName == spotify }
        }.getOrNull()
        if (controller != null) {
            val playing = controller.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
            if (playing) controller.transportControls.pause() else controller.transportControls.play()
        } else {
            // No active Spotify session (paused a while ago, or not running):
            // press "play" the way headphones do. Android hands that to the
            // app that played last — Spotify — which resumes in the background.
            val audio = app.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
            listOf(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.ACTION_UP).forEach { action ->
                runCatching {
                    audio.dispatchMediaKeyEvent(android.view.KeyEvent(action, android.view.KeyEvent.KEYCODE_MEDIA_PLAY))
                }
            }
            // And Spotify directly too, in case another app played last.
            listOf(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.ACTION_UP).forEach { action ->
                val intent = android.content.Intent(android.content.Intent.ACTION_MEDIA_BUTTON)
                    .setPackage(spotify)
                    .putExtra(android.content.Intent.EXTRA_KEY_EVENT, android.view.KeyEvent(action, android.view.KeyEvent.KEYCODE_MEDIA_PLAY))
                runCatching { app.sendBroadcast(intent) }
            }
        }
        refreshNotifications()
    }

    /** An app from the second screen's dock or its all-apps grid. */
    fun launchDockApp(app: Game) = launchApp(app, com.shelf.launcher.data.AppLaunchOrigin.SECOND)

    /** Shortcuts, the app drawer and the like: apps open straight away, no save sync first. */
    fun launchShortcut(game: Game) = launchApp(game, com.shelf.launcher.data.AppLaunchOrigin.MAIN)

    // ---- Opening apps (no game bookkeeping) ----

    /** Where each app was last opened by Voltcap, to spot "already open on the other screen". */
    private val lastAppDisplay = HashMap<String, Int>()

    /** Category key used for the per-category app screen default, or null. */
    private fun appDisplayCategory(game: Game): String? {
        val pkg = game.packageName ?: return null
        return if (pkg in _uiState.value.emulatorPackages) com.shelf.launcher.data.AppDisplayCategories.EMULATOR else null
    }

    /** Which screen [game] (an app) opens on, and why: its own choice ›
     * its category's › the global default. "Where launched" is resolved at launch time. */
    fun effectiveAppDisplay(game: Game): Pair<com.shelf.launcher.data.AppDisplayTarget, com.shelf.launcher.data.AppDisplaySource> {
        val pkg = game.packageName ?: return appDisplayStore.globalTarget() to com.shelf.launcher.data.AppDisplaySource.GLOBAL
        return appDisplayStore.resolve(pkg, appDisplayCategory(game))
    }

    /** [target] null clears this app's own choice. */
    fun setAppDisplay(packageName: String, target: com.shelf.launcher.data.AppDisplayTarget?) {
        appDisplayStore.setAppTarget(packageName, target)
        bumpScreenLayouts()
    }

    fun resetAppDisplay(packageName: String) = setAppDisplay(packageName, null)

    /** [target] null clears the category's default. See [com.shelf.launcher.data.AppDisplayCategories]. */
    fun setCategoryAppDisplay(category: String, target: com.shelf.launcher.data.AppDisplayTarget?) {
        appDisplayStore.setCategoryTarget(category, target)
        bumpScreenLayouts()
    }

    fun categoryAppDisplay(category: String): com.shelf.launcher.data.AppDisplayTarget? = appDisplayStore.categoryTarget(category)

    fun setGlobalAppDisplay(target: com.shelf.launcher.data.AppDisplayTarget) {
        appDisplayStore.setGlobalTarget(target)
        bumpScreenLayouts()
    }

    fun globalAppDisplay(): com.shelf.launcher.data.AppDisplayTarget = appDisplayStore.globalTarget()

    /** Opens an app by package name (hotkeys) — same rules as [launchApp]. */
    fun launchAppByPackage(
        packageName: String,
        label: String? = null,
        origin: com.shelf.launcher.data.AppLaunchOrigin = com.shelf.launcher.data.AppLaunchOrigin.MAIN
    ) {
        val known = allScannedApps.firstOrNull { it.packageName == packageName }
            ?: Game(name = label ?: packageName, platform = "App", hue = 0, sourceType = SourceType.APP, packageName = packageName)
        launchApp(known, origin)
    }

    /**
     * Opens an app. An Android game in the library still goes through the full
     * game path (screen layouts, play time…); anything else is just started —
     * on the screen chosen by [effectiveAppDisplay] — with no session,
     * Discord, unlock watch or library re-sort.
     */
    fun launchApp(app: Game, origin: com.shelf.launcher.data.AppLaunchOrigin = com.shelf.launcher.data.AppLaunchOrigin.MAIN) {
        val pkg = app.packageName
        if (pkg == null) {
            setStatusMessage("${app.name} has nothing to open.")
            return
        }
        val state = _uiState.value
        // Update 200: every Android game (Voltcap's "Android games" category) takes the game path, wherever it's
        // opened from (second-screen dock, app drawer, L1 shortcuts, hotkeys, notifications). It also required the app
        // to be in the library's normal categories, so with Settings › Library › "Only apps you add" an Android game
        // opened from these places started like a plain app: no play time, no last played, nothing to sync.
        if (state.isAndroidGame(app)) {
            launchNow(app)
            return
        }
        val context = getApplication<Application>()
        val second = com.shelf.launcher.data.ScreenPlacement.secondScreen(context)
        val (target, source) = effectiveAppDisplay(app)
        var wantSecond = when (target) {
            com.shelf.launcher.data.AppDisplayTarget.MAIN -> false
            com.shelf.launcher.data.AppDisplayTarget.SECOND -> true
            com.shelf.launcher.data.AppDisplayTarget.WHERE_LAUNCHED ->
                // The dock's own "open on" setting still applies to the dock.
                origin == com.shelf.launcher.data.AppLaunchOrigin.SECOND && state.appearance.secondDockOnSecond
        }
        var note: String? = null
        if (wantSecond && second == null) {
            if (source == com.shelf.launcher.data.AppDisplaySource.APP) {
                setStatusMessage("${app.name} is set to open on the second screen, but no second screen is connected.")
                return
            }
            // A default shouldn't make apps unusable when undocked: open on the
            // main screen and say so.
            wantSecond = false
            note = "No second screen connected — ${app.name} opened on the main screen."
        }
        val displayId = when {
            second == null -> null
            wantSecond -> second.displayId
            else -> android.view.Display.DEFAULT_DISPLAY
        }
        // Already open on the other screen? Android would just bring that
        // task forward there. A second instance is only safe for ordinary
        // apps — never emulators, whose two copies would fight over saves.
        val previous = lastAppDisplay[pkg]
        val newInstance = displayId != null && previous != null && previous != displayId &&
            pkg !in state.emulatorPackages
        val result = GameLauncher.launchAppOn(context, pkg, displayId, app.name, newInstance)
        when (result) {
            is com.shelf.launcher.data.AppLaunchResult.Launched -> {
                if (displayId != null) lastAppDisplay[pkg] = displayId
                noteOwnLaunch(null)
                // The second-screen home is a window above every app on its
                // display; step aside so the app can be seen.
                if (second != null && displayId == second.displayId) com.shelf.launcher.ui.SecondScreenGate.suppress()
                if (note != null) setStatusMessage(note)
            }
            is com.shelf.launcher.data.AppLaunchResult.Failed -> setStatusMessage(result.message)
        }
    }

    // ---- Live app list ----

    private val scanMutex = kotlinx.coroutines.sync.Mutex()
    private var scanDebounce: Job? = null
    private var packageSeq: Int? = null
    private var packageReceiver: android.content.BroadcastReceiver? = null
    /** True between onLauncherResumed and onLauncherPaused: package events only rescan while we're on screen. */
    @Volatile private var launcherResumed = false
    /** A package event arrived while away: the next resume rescans (see [refreshAppsIfChanged]). */
    @Volatile private var appsDirty = false

    /** Installs, removals and updates show up without restarting Voltcap. */
    private fun registerPackageWatch() {
        val app = getApplication<Application>()
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
                val pkg = intent.data?.schemeSpecificPart
                if (pkg != null) com.shelf.launcher.ui.AppIconCache.invalidate(pkg)
                // Away (e.g. in a game): no rescan now, the next resume does it.
                if (launcherResumed) scheduleAppRescan() else appsDirty = true
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_PACKAGE_ADDED)
            addAction(android.content.Intent.ACTION_PACKAGE_REMOVED)
            addAction(android.content.Intent.ACTION_PACKAGE_CHANGED)
            addAction(android.content.Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        runCatching { app.registerReceiver(receiver, filter) }.onSuccess { packageReceiver = receiver }
        viewModelScope.launch(Dispatchers.IO) { packageSeq = com.shelf.launcher.data.AppScanner.changeCheck(app, null).first }
    }

    /** One rescan after a burst of package events (an update sends several). */
    private fun scheduleAppRescan() {
        scanDebounce?.cancel()
        scanDebounce = viewModelScope.launch {
            kotlinx.coroutines.delay(800)
            scanApps(silent = true)
        }
    }

    /** Cheap check when Voltcap comes back to the front. */
    private fun refreshAppsIfChanged() {
        val dirty = appsDirty
        appsDirty = false
        viewModelScope.launch(Dispatchers.IO) {
            val (seq, changed) = com.shelf.launcher.data.AppScanner.changeCheck(getApplication(), packageSeq)
            packageSeq = seq
            if (changed || dirty) scanApps(silent = true)
        }
    }

    // RetroAchievements matching hashes several small files a millisecond: the progress counter reaches
    // the screen at most four times a second (every update redraws the whole home screen).
    @Volatile private var raProgressAt = 0L
    private fun publishRaProgress(checked: Int) {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - raProgressAt < 250L) return
        raProgressAt = now
        _uiState.update { it.copy(raMatchDone = checked) }
    }

    override fun onCleared() {
        packageReceiver?.let { runCatching { getApplication<Application>().unregisterReceiver(it) } }
        packageReceiver = null
        super.onCleared()
    }

    fun setAchievementBadgeMode(mode: AchievementBadgeMode) {
        preferencesStore.setBadgeModeIsRecent(mode == AchievementBadgeMode.RECENT_UNLOCK)
        _uiState.update { it.copy(achievementBadgeMode = mode) }
    }

    fun toggleAchievementBadgePlacement() {
        val next = !_uiState.value.achievementBadgeNextToTitle
        preferencesStore.setBadgeNextToTitle(next)
        _uiState.update { it.copy(achievementBadgeNextToTitle = next) }
    }

    /**
     * Links library games to RetroAchievements automatically, the same way
     * the emulator does: by hashing each ROM/disc and looking the hash up in
     * the console's game list. Files that can't be hashed (CHD etc.) or whose
     * hash isn't known fall back to title matching.
     *
     * Runs by itself at startup and after every ROM scan. Results are saved on
     * the device, so only new or changed files are hashed after the first run.
     * [force] re-hashes everything and refreshes the cached game lists.
     * Manual IDs always win and are never touched.
     */
    fun autoMatchAchievements(silent: Boolean = false, force: Boolean = false) {
        val creds = raCredentialsStore.load() ?: run {
            if (!silent) setStatusMessage("Connect RetroAchievements first (Settings › Accounts).")
            return
        }
        if (_uiState.value.isMatchingAchievements) {
            raMatchRerun = true
            raMatchRerunForce = raMatchRerunForce || force
            return
        }
        val roms = _uiState.value.games.filter { it.sourceType == SourceType.ROM }
        runRaMatching(creds, roms, force = force, silent = silent)
    }

    /** Re-hashes and re-matches one game in the background. */
    fun rehashGame(game: Game) {
        val creds = raCredentialsStore.load() ?: run {
            setStatusMessage("Connect RetroAchievements first (Settings › Accounts).")
            return
        }
        if (overridesStore.loadAll()[game.stableKey]?.raGameId != null) {
            setStatusMessage("${game.name} has a manual RetroAchievements ID — clear it to use hash matching.")
            return
        }
        if (_uiState.value.isMatchingAchievements) {
            setStatusMessage("Achievement matching is already running — try again in a moment.")
            return
        }
        runRaMatching(creds, listOf(game), force = true, silent = false, rehashFiles = true)
    }

    /** Sets (or clears, if blank) a hash to use instead of the computed one —
     * for dumps Shelf can't hash itself, like CHD. Then re-matches the game. */
    fun setCustomRaHash(game: Game, rawHash: String) {
        val hash = rawHash.trim().lowercase().takeIf { it.isNotEmpty() }
        if (hash != null && !Regex("^[0-9a-f]{32}$").matches(hash)) {
            setStatusMessage("A RetroAchievements hash is 32 characters long, using only 0-9 and a-f.")
            return
        }
        raMatchStore.setCustomHash(game.stableKey, hash)
        _uiState.update { it.copy(raCustomHashes = raMatchStore.loadCustomHashes()) }
        rehashGame(game)
    }

    /** Whether this game's RetroAchievements ID was set by hand. */
    fun manualRaId(game: Game): Int? = overridesStore.loadAll()[game.stableKey]?.raGameId

    /** RetroAchievements' console list, once per app start — it almost never changes. */
    private var raConsolesCache: List<com.shelf.launcher.data.RaConsole>? = null

    /**
     * [force] re-checks every game against freshly downloaded game lists, but
     * a file whose size and date haven't changed keeps its saved hash —
     * reading a whole disc image again gives the same answer. Only
     * [rehashFiles] (re-hash one game, from its options) really re-reads it.
     */
    private fun runRaMatching(
        creds: RaCredentials,
        candidates: List<Game>,
        force: Boolean,
        silent: Boolean,
        rehashFiles: Boolean = false
    ) {
        _uiState.update {
            it.copy(
                isMatchingAchievements = true,
                raMatchDone = 0,
                raMatchTotal = 0,
                statusMessage = if (silent) it.statusMessage else "Matching games to RetroAchievements…"
            )
        }

        viewModelScope.launch {
            var linked = 0
            var checked = 0
            try {
                val manual = overridesStore.loadAll()
                val saved = withContext(Dispatchers.IO) { raMatchStore.loadEntries() }
                // (Update 144: read off the main thread, like the entries above.)
                val custom = withContext(Dispatchers.IO) { raMatchStore.loadCustomHashes() }
                val now = System.currentTimeMillis()

                // Only what needs work: new files, changed files, forced
                // re-checks, and week-old misses (RetroAchievements may have
                // added that dump since). Worked out on the device first, so a
                // start with nothing new never waits for the network.
                val todo = withContext(Dispatchers.IO) {
                    candidates.filter { game ->
                        if (manual[game.stableKey]?.raGameId != null) return@filter false
                        val path = game.romPath ?: return@filter false
                        if (RaMatchStore.PLATFORM_TO_RA_CONSOLE[game.platform] == null) return@filter false
                        if (force) return@filter true
                        val entry = saved[game.stableKey] ?: return@filter true
                        val file = java.io.File(path)
                        entry.fileSize != file.length() ||
                            entry.fileModified != file.lastModified() ||
                            (custom[game.stableKey] != null && custom[game.stableKey] != entry.hash) ||
                            (!entry.isHashVerified && now - entry.checkedAt > RaGameListCache.WEEK_MS) ||
                            (!entry.isHashVerified && entry.hasherVersion < RaHasher.VERSION)
                    }
                }
                if (todo.isEmpty()) return@launch
                _uiState.update { it.copy(raMatchTotal = todo.size) }

                val consoles = raConsolesCache?.takeIf { !force && it.isNotEmpty() }
                    ?: raRepository.consoles(creds.apiKey).also { if (it.isNotEmpty()) raConsolesCache = it }
                if (consoles.isEmpty()) {
                    if (!silent) setStatusMessage("Couldn't reach RetroAchievements.")
                    return@launch
                }

                val results = LinkedHashMap<String, RaMatchEntry>()
                // A few files at a time: MD5 of big images is CPU-bound, so
                // this is noticeably quicker without starving the UI.
                val hashSlots = kotlinx.coroutines.sync.Semaphore(3)
                todo.groupBy { it.platform }.forEach { (platform, games) ->
                    val consoleName = RaMatchStore.PLATFORM_TO_RA_CONSOLE[platform]
                    val consoleId = consoles.firstOrNull { it.Name.equals(consoleName, ignoreCase = true) }?.ID
                    val list = consoleId?.let { loadRaGameList(creds.apiKey, it, refresh = force) }
                    if (list == null) {
                        checked += games.size
                        publishRaProgress(checked)
                        return@forEach
                    }

                    // Update 144: a console's list can have thousands of games and hashes — indexed off the main
                    // thread (it froze the home screen while matching ran, e.g. the weekly re-check after start).
                    val (byHash, titles) = withContext(Dispatchers.Default) {
                        val hashes = HashMap<String, RaListedGame>()
                        list.forEach { entry -> entry.Hashes?.forEach { h -> hashes[h.lowercase()] = entry } }
                        hashes to RaTitleMatcher.buildIndex(list)
                    }

                    val jobs = games.map { game ->
                        async {
                            // Hashing big disc images is heavy: never while a game runs.
                            com.shelf.launcher.data.GameMode.awaitNotPlaying()
                            hashSlots.withPermitCompat {
                                withContext(Dispatchers.IO) {
                                    val file = java.io.File(game.romPath ?: "")
                                    val size = file.length()
                                    val modified = file.lastModified()
                                    val customHash = custom[game.stableKey]
                                    val previous = saved[game.stableKey]
                                    // Same file as last time: its hash can't have changed.
                                    val reusable = previous?.hash?.takeIf {
                                        !rehashFiles && customHash == null &&
                                            previous.fileSize == size && previous.fileModified == modified &&
                                            previous.hasherVersion >= RaHasher.VERSION
                                    }
                                    val result = when {
                                        customHash != null -> RaHasher.Result.Hashed(customHash)
                                        reusable != null -> RaHasher.Result.Hashed(reusable)
                                        else -> RaHasher.hash(file.path, platform)
                                    }
                                    Triple(game, result, size to modified)
                                }
                            }
                        }
                    }
                    jobs.forEach { job ->
                        val (game, result, stamp) = job.await()
                        val customHash = custom[game.stableKey]
                        val hash = (result as? RaHasher.Result.Hashed)?.md5
                        val reason = when (result) {
                            is RaHasher.Result.Unsupported -> result.reason
                            is RaHasher.Result.Failed -> "Couldn't hash: ${result.reason}"
                            else -> null
                        }

                        val hashMatch = hash?.let { byHash[it] }
                        val entry = if (hashMatch != null) {
                            RaMatchEntry(
                                gameId = hashMatch.ID, raTitle = hashMatch.Title,
                                source = if (customHash != null) RaMatchSource.CUSTOM_HASH else RaMatchSource.HASH,
                                hash = hash, note = null,
                                fileSize = stamp.first, fileModified = stamp.second, checkedAt = now,
                                hasherVersion = RaHasher.VERSION, raConsole = consoleName
                            )
                        } else {
                            val byTitle = RaTitleMatcher.match(game.name, titles)
                            RaMatchEntry(
                                gameId = byTitle?.ID, raTitle = byTitle?.Title,
                                source = if (byTitle != null) RaMatchSource.TITLE else RaMatchSource.NONE,
                                hash = hash,
                                note = reason ?: "RetroAchievements doesn't recognise this dump's hash",
                                fileSize = stamp.first, fileModified = stamp.second, checkedAt = now,
                                hasherVersion = RaHasher.VERSION, raConsole = if (byTitle != null) consoleName else null
                            )
                        }
                        results[game.stableKey] = entry
                        if (entry.gameId != null) linked++
                        checked++
                        publishRaProgress(checked)
                        // Save as it goes, so closing the app mid-way keeps progress.
                        if (results.size % 25 == 0) {
                            val snapshot = LinkedHashMap(results)
                            withContext(Dispatchers.IO) { raMatchStore.putEntries(snapshot) }
                        }
                    }
                }

                // Games whose link changed (or vanished): their saved progress belongs to the old game.
                val relinked = results.filter { (k, e) -> saved[k]?.gameId != e.gameId }.keys
                // (Update 144: written and read back off the main thread.)
                val allMatches = withContext(Dispatchers.IO) {
                    raMatchStore.putEntries(results)
                    raMatchStore.loadEntries()
                }
                recombineGames()
                // Cached achievement lists may belong to the previously matched game
                // (the saved copy is also checked against the RA game ID when opened).
                _uiState.update {
                    it.copy(
                        raMatches = allMatches,
                        raProgress = it.raProgress - relinked,
                        raAchievementDetails = it.raAchievementDetails - results.keys,
                        statusMessage = when {
                            silent -> it.statusMessage
                            checked == 1 -> if (linked == 1) "Linked to RetroAchievements." else "No RetroAchievements match found."
                            else -> "Checked $checked games — $linked linked to RetroAchievements."
                        }
                    )
                }
                fetchAllRaProgress(creds, persistOnSuccess = false, savedCredsRestore = true)
            } finally {
                _uiState.update { it.copy(isMatchingAchievements = false, raMatchDone = 0, raMatchTotal = 0) }
                if (raMatchRerun) {
                    val rerunForce = raMatchRerunForce
                    raMatchRerun = false
                    raMatchRerunForce = false
                    autoMatchAchievements(silent = true, force = rerunForce)
                }
            }
        }
    }

    /** A console's game list from the device cache when it's fresh, otherwise
     * from RetroAchievements — falling back to a stale cached copy if the
     * network request fails. */
    private suspend fun loadRaGameList(apiKey: String, consoleId: Int, refresh: Boolean): List<RaListedGame>? {
        // Update 144: the cached list (a file read and parsed whole) is read off the main thread.
        if (!refresh) withContext(Dispatchers.IO) { raGameListCache.get(consoleId, RaGameListCache.WEEK_MS) }?.let { return it }
        raRepository.fetchGameList(apiKey, consoleId)?.let { fresh ->
            withContext(Dispatchers.IO) { raGameListCache.put(consoleId, fresh) }
            return fresh
        }
        return withContext(Dispatchers.IO) { raGameListCache.get(consoleId, Long.MAX_VALUE) }
    }

    private var manualOpenJob: Job? = null

    /**
     * Finds (or downloads) [game]'s manual and hands it to [onReady]; a status message when there's none.
     * Update 202: a PDF beside the ROM / picked / downloaded before opens at once. Else the manual's address noted while
     * scraping (ManualLinks; asked of ScreenScraper now when it isn't known yet) is downloaded — once, into the saved-art
     * store, with a progress window (ManualDownloadDialog) — and then opened.
     */
    fun openManual(game: Game, onReady: (java.io.File) -> Unit, onMissing: () -> Unit = {}) {
        if (manualOpenJob?.isActive == true) return
        manualOpenJob = viewModelScope.launch {
            val app = getApplication<Application>()
            val L = com.shelf.launcher.data.ManualLinks
            val local = withContext(Dispatchers.IO) { runCatching { L.fileOnDevice(app, game) }.getOrNull() }
            if (local != null) { onReady(local); return@launch }
            val creds = screenScraperStore.load()
            var url = L.url(game.stableKey)
            if (url == null && creds != null && game.sourceType == SourceType.ROM) {
                setStatusMessage("Looking for the manual…")
                url = withContext(Dispatchers.IO) { runCatching { screenScraperRepository.manualUrl(creds, game) }.getOrNull() }
                clearStatusMessage()
            }
            // None: a window with what to do (pick a PDF, search online) instead of a passing message.
            val link = url
            if (link == null) { onMissing(); return@launch }
            // (Update 320: kept only with Manuals › Download all ahead; otherwise a preview, deleted once closed.)
            val keep = com.shelf.launcher.data.Features.manualKeep.value == com.shelf.launcher.data.Features.ManualKeep.AHEAD
            val file = runCatching { L.download(app, game, link, screenScraperRepository.withLogin(link, creds), keep) }.getOrNull()
            when {
                file != null -> onReady(file)
                L.stoppedLast -> Unit
                creds == null -> setStatusMessage("The manual couldn't be downloaded — connect ScreenScraper (Settings › Accounts) and try again.")
                else -> setStatusMessage("The manual couldn't be downloaded (ScreenScraper may be busy, or today's quota is used up). Try again later.")
            }
        }
    }

    /** True when ScreenScraper is connected (it's one of the places manuals come from). */
    fun screenScraperConnected(): Boolean = screenScraperStore.load() != null

    /** A PDF the person picked becomes [game]'s manual (kept in Voltcap's manuals folder). */
    fun importManual(game: Game, uri: android.net.Uri, onReady: (java.io.File) -> Unit) {
        viewModelScope.launch {
            val file = com.shelf.launcher.data.Manuals.import(getApplication(), game, uri)
            // (Update 202: the case's booklet shows its first page from now on.)
            if (file != null) com.shelf.launcher.data.ManualLinks.version++
            if (file != null) onReady(file) else setStatusMessage("That file isn't a PDF manual.")
        }
    }

    /**
     * Update 244: takes [game] off Continue playing (and the other "recently played" lists) on this device until it's
     * played again. Its play time and its last-played time for sync are kept.
     */
    fun removeFromContinuePlaying(game: Game) {
        recentlyPlayedStore.hide(game.stableKey)
        recombineGames()
    }

    fun toggleFavorite(game: Game) {
        val updated = favoritesStore.toggle(game.stableKey)
        _uiState.update { it.copy(favorites = updated) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun setStatusMessage(message: String) {
        _uiState.update { it.copy(statusMessage = message) }
    }

    /**
     * Fills in emulator assignments for platforms that don't have one, using
     * whichever of the expected apps is actually installed. Runs once — a
     * later manual change must never be reverted by this.
     */
    private fun applyDefaultEmulators() {
        if (preferencesStore.defaultsApplied()) return
        val installed = allScannedApps.mapNotNull { it.packageName }.toSet()
        if (installed.isEmpty()) return // apps not scanned yet; try again next pass

        val existing = _uiState.value.emulatorConfigs
        val platforms = (_uiState.value.availablePlatforms.filter { it != ANDROID_GAMES_PLATFORM } +
            listOf("PS1", "PS2", "GBA", "GB", "GBC", "N64", "GameCube", "Wii", "PSP")).distinct()

        var added = existing
        platforms.forEach { platform ->
            if (existing.containsKey(platform)) return@forEach
            DefaultEmulators.configFor(platform, installed)?.let { config ->
                emulatorStore.save(platform, config)
                added = added + (platform to config)
            }
        }

        if (added !== existing) {
            _uiState.update { it.copy(emulatorConfigs = added) }
        }
        preferencesStore.setDefaultsApplied()
    }

    fun setEmulatorConfig(config: EmulatorConfig) {
        emulatorStore.save(config.platform, config)
        _uiState.update { it.copy(emulatorConfigs = it.emulatorConfigs + (config.platform to config)) }
    }

    fun setGameNativeConfig(config: GameNativeConfig) {
        gameNativeStore.save(config)
        _uiState.update { it.copy(gameNativeConfig = config) }
    }

    /** Adds (or updates, if the AppID already exists) a manually-entered
     * Steam game handed off to GameNative. The AppID doubles as the
     * SteamGridDB art lookup key, so no separate art linking step is needed
     * unless you want to override it. */
    fun addSteamGame(name: String, steamAppId: Int) {
        val entry = SteamGameEntry(name, steamAppId)
        steamGamesStore.add(entry)
        currentSteamGames = steamGamesStore.loadAll().map { it.toGame() }
        recombineGames()

        if (_uiState.value.artState == ArtConnectionState.ON) {
            val game = entry.toGame()
            viewModelScope.launch {
                val art = repository.fetchArt(game)
                if (art != null) {
                    artCacheStore.put(game.stableKey, art) // (update 91) saved, not only shown
                    _uiState.update { it.copy(art = it.art + (game.stableKey to art)) }
                }
            }
        }
    }

    /** Adds many Steam games at once (folder import): one store write, one recombine, then art for the new ones.
     * Games already in the library are left alone. Returns how many were actually added. */
    fun importSteamGames(entries: List<SteamGameEntry>): Int {
        val before = steamGamesStore.loadAll().map { it.steamAppId }.toHashSet()
        val fresh = entries.distinctBy { it.steamAppId }.filter { it.steamAppId !in before }
        if (fresh.isEmpty()) return 0
        steamGamesStore.addAll(fresh)
        currentSteamGames = steamGamesStore.loadAll().map { it.toGame() }
        recombineGames()
        if (_uiState.value.artState == ArtConnectionState.ON) {
            viewModelScope.launch {
                for (entry in fresh) {
                    val game = entry.toGame()
                    val art = runCatching { repository.fetchArt(game) }.getOrNull() ?: continue
                    artCacheStore.put(game.stableKey, art) // (update 91) saved, not only shown
                    _uiState.update { it.copy(art = it.art + (game.stableKey to art)) }
                }
            }
        }
        return fresh.size
    }

    fun removeSteamGame(steamAppId: Int) {
        steamGamesStore.remove(steamAppId)
        currentSteamGames = steamGamesStore.loadAll().map { it.toGame() }
        recombineGames()
    }

    private fun SteamGameEntry.toGame(): Game = Game(
        name = name,
        platform = "Steam",
        hue = (("steam$steamAppId").hashCode() and 0x7fffffff) % 360,
        sourceType = SourceType.STEAM,
        steamAppId = steamAppId
    )

    /** Sets (or clears, if null/blank) a manual SteamGridDB game ID for one
     * entry, then re-fetches art for just that entry if art fetching is on. */
    fun setManualArtId(game: Game, rawId: String) {
        val id = rawId.trim().toIntOrNull()
        overridesStore.setArtId(game.stableKey, id)
        recombineGames()

        if (_uiState.value.artState == ArtConnectionState.ON) {
            val updatedGame = _uiState.value.games.firstOrNull { it.stableKey == game.stableKey } ?: return
            viewModelScope.launch {
                val art = repository.fetchArt(updatedGame)
                if (art != null) {
                    // (update 91) Saved too, so the manual ID's art survives a restart.
                    artCacheStore.put(updatedGame.stableKey, art)
                    _uiState.update { it.copy(art = it.art + (updatedGame.stableKey to art)) }
                }
            }
        }
    }

    /** Sets (or clears) a manual RetroAchievements game ID for one entry,
     * then re-fetches its progress if RA is connected. There's no automatic
     * matching here — RA identifies games by ROM hash, which is
     * console-specific and non-trivial to replicate correctly, so this is
     * a deliberate manual-link-only feature (same reasoning as the
     * SteamGridDB manual ID). */
    fun setManualRaId(game: Game, rawId: String) {
        val id = rawId.trim().toIntOrNull()
        overridesStore.setRaId(game.stableKey, id)
        recombineGames()
        // The cached achievement list (if any) is for whatever ID was linked
        // before — drop it so the next time the list is opened, it refetches
        // for the new ID instead of silently showing the old game's data.
        _uiState.update {
            it.copy(
                raAchievementDetails = it.raAchievementDetails - game.stableKey,
                raProgress = it.raProgress - game.stableKey
            )
        }

        // Clearing a manual ID hands the game back to automatic (hash) matching.
        if (id == null) rehashGame(game)

        val creds = raCredentialsStore.load()
        if (_uiState.value.raState == RaConnectionState.ON && creds != null && id != null) {
            val updatedGame = _uiState.value.games.firstOrNull { it.stableKey == game.stableKey } ?: return
            viewModelScope.launch {
                val progress = raRepository.fetchProgress(creds.username, creds.apiKey, listOf(id)).okValue?.progress?.get(id)
                if (progress != null) {
                    _uiState.update { it.copy(raProgress = it.raProgress + (updatedGame.stableKey to progress)) }
                }
            }
        }
    }

    /** Lazily fetches the full achievement list (locked + unlocked, with
     * type/missable info) for one game, only when its list is actually
     * opened — fetching this for every linked game eagerly would mean two
     * extra API calls per game on every connect, most of which the person
     * would never look at. Cached in raAchievementDetails afterward, so
     * reopening the same game's list doesn't refetch. */
    /** Tips for a game: comments on its RetroAchievements page and the set's
     * guide link — loaded when its details window opens. A failed or partial
     * load is never remembered as "empty": the next open tries again. */
    fun loadGameTips(game: Game) {
        val id = game.raGameId ?: return
        val existing = _uiState.value.raGameTips[id]
        if ((existing != null && existing.isComplete) || id in raTipsLoading) return
        val creds = raCredentialsStore.load() ?: return
        raTipsLoading += id
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { raRepository.fetchGameTips(creds.apiKey, id) }
            raTipsLoading -= id
            _uiState.update {
                when (result) {
                    is com.shelf.launcher.data.ra.RaResult.Ok -> {
                        val fresh = result.value
                        val old = it.raGameTips[id]
                        // A part that failed this time keeps what an earlier load got.
                        val merged = if (old == null) fresh else fresh.copy(
                            comments = if (fresh.commentsError != null) old.comments else fresh.comments,
                            nextOffset = if (fresh.commentsError != null) old.nextOffset else fresh.nextOffset,
                            hasMore = if (fresh.commentsError != null) old.hasMore else fresh.hasMore,
                            guideUrl = if (fresh.guideError != null) old.guideUrl else fresh.guideUrl
                        )
                        it.copy(raGameTips = it.raGameTips + (id to merged), raGameTipsError = it.raGameTipsError - id)
                    }
                    is com.shelf.launcher.data.ra.RaResult.Error ->
                        it.copy(raGameTipsError = it.raGameTipsError + (id to result.error))
                }
            }
        }
    }
    private val raTipsLoading = mutableSetOf<Int>()

    /** Older comments on the game's page, appended to what's shown. */
    fun loadMoreGameComments(game: Game) {
        val id = game.raGameId ?: return
        val tips = _uiState.value.raGameTips[id] ?: return
        if (!tips.hasMore || id in raTipsLoading) return
        val creds = raCredentialsStore.load() ?: return
        raTipsLoading += id
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { raRepository.fetchGameComments(creds.apiKey, id, tips.nextOffset) }
            raTipsLoading -= id
            _uiState.update {
                val current = it.raGameTips[id] ?: return@update it
                when (result) {
                    is com.shelf.launcher.data.ra.RaResult.Ok -> it.copy(
                        raGameTips = it.raGameTips + (id to current.copy(
                            comments = mergeComments(current.comments, result.value.comments),
                            nextOffset = result.value.nextOffset,
                            hasMore = result.value.hasMore,
                            commentsError = null
                        ))
                    )
                    is com.shelf.launcher.data.ra.RaResult.Error ->
                        it.copy(raGameTips = it.raGameTips + (id to current.copy(commentsError = result.error)))
                }
            }
        }
    }

    private fun mergeComments(old: List<com.shelf.launcher.data.RaComment>, more: List<com.shelf.launcher.data.RaComment>) =
        (old + more).distinctBy { Triple(it.User, it.Submitted, it.CommentText) }

    // Next raw offset per achievement, for "load more" (see RetroAchievementsRepository.fetchAchievementComments).
    private val raCommentOffsets = mutableMapOf<Int, Int>()

    /** Comments people left on an achievement's page (newest first) — fetched when you open it. */
    fun loadAchievementComments(achievementId: Int) {
        val s = _uiState.value
        if (achievementId in s.raComments || achievementId in s.raCommentsLoading) return
        val creds = raCredentialsStore.load() ?: return
        _uiState.update { it.copy(raCommentsLoading = it.raCommentsLoading + achievementId) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { raRepository.fetchAchievementComments(creds.apiKey, achievementId, 0) }
            _uiState.update {
                when (result) {
                    is com.shelf.launcher.data.ra.RaResult.Ok -> {
                        raCommentOffsets[achievementId] = result.value.nextOffset
                        it.copy(
                            raComments = it.raComments + (achievementId to result.value.comments),
                            raCommentsError = it.raCommentsError - achievementId,
                            raCommentsHasMore = if (result.value.hasMore) it.raCommentsHasMore + achievementId else it.raCommentsHasMore - achievementId,
                            raCommentsLoading = it.raCommentsLoading - achievementId
                        )
                    }
                    // Not stored in raComments, so opening it again retries.
                    is com.shelf.launcher.data.ra.RaResult.Error -> it.copy(
                        raCommentsError = it.raCommentsError + (achievementId to result.error),
                        raCommentsLoading = it.raCommentsLoading - achievementId
                    )
                }
            }
        }
    }

    /** Older comments on one achievement. */
    fun loadMoreAchievementComments(achievementId: Int) {
        val s = _uiState.value
        val offset = raCommentOffsets[achievementId] ?: return
        if (achievementId !in s.raCommentsHasMore || achievementId in s.raCommentsLoadingMore) return
        val creds = raCredentialsStore.load() ?: return
        _uiState.update { it.copy(raCommentsLoadingMore = it.raCommentsLoadingMore + achievementId) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { raRepository.fetchAchievementComments(creds.apiKey, achievementId, offset) }
            _uiState.update {
                when (result) {
                    is com.shelf.launcher.data.ra.RaResult.Ok -> {
                        raCommentOffsets[achievementId] = result.value.nextOffset
                        it.copy(
                            raComments = it.raComments + (achievementId to mergeComments(it.raComments[achievementId].orEmpty(), result.value.comments)),
                            raCommentsError = it.raCommentsError - achievementId,
                            raCommentsHasMore = if (result.value.hasMore) it.raCommentsHasMore + achievementId else it.raCommentsHasMore - achievementId,
                            raCommentsLoadingMore = it.raCommentsLoadingMore - achievementId
                        )
                    }
                    is com.shelf.launcher.data.ra.RaResult.Error -> it.copy(
                        raCommentsError = it.raCommentsError + (achievementId to result.error),
                        raCommentsLoadingMore = it.raCommentsLoadingMore - achievementId
                    )
                }
            }
        }
    }

    /** The profile again — mostly for the latest Rich Presence ("50 Pokémon
     * caught"), which changes every time you play. */
    private var raProfileFetchedAt = 0L

    fun refreshRaProfile() {
        val creds = raCredentialsStore.load() ?: return
        // Opening several lists in a row doesn't refetch the profile each time (once a minute at most).
        val now = System.currentTimeMillis()
        if (_uiState.value.raProfile != null && now - raProfileFetchedAt < 60_000L) return
        raProfileFetchedAt = now
        viewModelScope.launch {
            val profile = withContext(Dispatchers.IO) { raRepository.fetchProfile(creds.username, creds.apiKey) }
            if (profile != null) _uiState.update { it.copy(raProfile = profile) }
        }
    }

    // ---- Walkthrough (guide) ----
    private val guideRepo by lazy { com.shelf.launcher.data.guide.GuideRepository(getApplication()) }
    private val _guideState = MutableStateFlow<com.shelf.launcher.ui.GuideUiState?>(null)
    /** The guide for the game last opened (full screen or second screen). Null until one is opened. */
    val guideState: StateFlow<com.shelf.launcher.ui.GuideUiState?> = _guideState.asStateFlow()
    private var guideGame: Game? = null
    private var guideJob: Job? = null
    // The running game whose guide follows the play session (set with the unlock watch).
    private var guideWatchGame: Game? = null
    private var lastGuideLocateAt = 0L

    private fun guideRef(game: Game) = com.shelf.launcher.data.guide.GuideGameRef(
        game.stableKey, com.shelf.launcher.ui.stripRegionTags(game.name), game.raGameId,
        game.raGameId?.let { _uiState.value.raGameTips[it]?.guideUrl },
        platform = game.platform
    )

    /** Opens the walkthrough for [game]: finds guides, restores the saved place, then locates from Rich Presence. */
    fun openGuide(game: Game) = loadGuide(game, full = true)

    /** Cheap load for the second screen: only the saved place, no network for candidates. */
    fun peekGuide(game: Game) {
        if (_guideState.value?.gameKey == game.stableKey && guideGame?.stableKey == game.stableKey) return
        loadGuide(game, full = false)
    }

    private fun loadGuide(game: Game, full: Boolean) {
        guideJob?.cancel()
        guideGame = game
        val key = game.stableKey
        if (_guideState.value?.gameKey != key) _guideState.value = com.shelf.launcher.ui.GuideUiState(key, loading = true)
        else _guideState.update { it?.copy(loading = true, error = null) }
        guideJob = viewModelScope.launch {
            try {
                val raId = game.raGameId
                if (full && raId != null && raCredentialsStore.load() != null) {
                    loadGameTips(game)
                    loadAchievementDetails(game)
                    // The guide link comes with the tips; don't wait forever if they fail.
                    kotlinx.coroutines.withTimeoutOrNull(10_000) {
                        _uiState.first { raId in it.raGameTips || raId in it.raGameTipsError }
                    }
                }
                val candidates = if (full) runCatching { guideRepo.guidesFor(guideRef(game)) }.getOrDefault(emptyList())
                else _guideState.value?.candidates.orEmpty()
                var progress = guideRepo.progress(key)
                val texts = candidates.filter { it.kind == com.shelf.launcher.data.guide.GuideCandidate.Kind.TEXT }
                if (full && progress.chosenGuideId == null && texts.size == 1) {
                    guideRepo.selectGuide(key, texts[0].id)
                    progress = guideRepo.progress(key)
                }
                val position = if (progress.chosenGuideId != null) guideRepo.continueGuide(key) else null
                _guideState.value = com.shelf.launcher.ui.GuideUiState(
                    gameKey = key, candidates = candidates, chosenId = progress.chosenGuideId, position = position,
                    spoiler = progress.spoilerLevel, missablesMode = progress.missablesMode,
                    missableReport = guideMissablesFor(game, position),
                    loading = false,
                    error = if (progress.chosenGuideId != null && position == null) "The chosen guide couldn't be loaded. Check your connection or pick another." else null,
                    localFolder = guideRepo.localFolder()
                )
                if (full && position != null) applyLocated(game, guideLocate(game))
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Closed mid-load: don't leave the spinner on (a newer load replacing this one sets its own).
                if (guideJob === kotlin.coroutines.coroutineContext[Job]) _guideState.update { it?.copy(loading = false) }
                throw e
            } catch (e: Exception) {
                _guideState.update { it?.copy(loading = false, error = "Couldn't load the guide: ${e.message}") }
            }
        }
    }

    private suspend fun guideMissablesFor(game: Game, pos: com.shelf.launcher.data.guide.GuidePosition?): com.shelf.launcher.data.guide.MissableReport? {
        if (pos == null || game.raGameId == null) return null
        val ach = _uiState.value.raAchievementDetails[game.stableKey].orEmpty()
        if (ach.isEmpty()) return null
        return runCatching { guideRepo.missables(game.stableKey, ach) }.getOrNull()
    }

    /** Rich Presence (only when it belongs to this game) and this session's unlocks -> a position. */
    private suspend fun guideLocate(game: Game): com.shelf.launcher.data.guide.GuidePosition? {
        val key = game.stableKey
        val raId = game.raGameId
        val creds = raCredentialsStore.load()
        var rp: String? = null
        if (creds != null && raId != null) {
            val r = withContext(Dispatchers.IO) { raRepository.fetchRichPresence(creds.username, creds.apiKey) }
            rp = r.okValue?.takeIf { it.gameId == raId }?.text
        }
        val recent = if (raId == null) emptyList() else
            (_uiState.value.raRecentAchievements.filter { it.GameID == raId } + unlocksThisSession)
                .map { com.shelf.launcher.data.guide.RecentUnlock(it.AchievementID, it.Title) }
        lastGuideLocateAt = System.currentTimeMillis()
        return if (rp == null && recent.isEmpty()) guideRepo.continueGuide(key) else guideRepo.locate(key, rp, recent)
    }

    private suspend fun applyLocated(game: Game, pos: com.shelf.launcher.data.guide.GuidePosition?) {
        if (pos == null) return
        val report = guideMissablesFor(game, pos)
        _guideState.update { st -> if (st?.gameKey == game.stableKey) st.copy(position = pos, missableReport = report, error = null) else st }
    }

    /** Locate now (Rich Presence needs a RetroAchievements connection; without it the saved place is kept). */
    fun locateNow(game: Game) {
        viewModelScope.launch {
            _guideState.update { st -> if (st?.gameKey == game.stableKey) st.copy(locating = true) else st }
            try {
                if (guideRepo.progress(game.stableKey).chosenGuideId != null) applyLocated(game, guideLocate(game))
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { }
            _guideState.update { st -> if (st?.gameKey == game.stableKey) st.copy(locating = false) else st }
        }
    }

    /** Background relocate while playing / after a session; silent, and only when a guide is chosen. */
    private fun relocateGuideQuietly(game: Game) {
        viewModelScope.launch {
            try {
                if (!effectiveProfile(game).guideAutoLocate.value) return@launch // per-game/console opt-out
                if (guideRepo.progress(game.stableKey).chosenGuideId == null) return@launch
                applyLocated(game, guideLocate(game))
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { }
        }
    }

    fun chooseGuide(game: Game, guideId: String?) {
        viewModelScope.launch {
            guideRepo.selectGuide(game.stableKey, guideId)
            loadGuide(game, full = false)
        }
    }

    /** Jumps to the saved place. */
    fun continueGuide(game: Game) {
        viewModelScope.launch {
            val pos = guideRepo.continueGuide(game.stableKey) ?: return@launch
            applyLocated(game, pos)
        }
    }

    /** "This isn't where I am": remembers Rich Presence -> section (or just moves the place when there is no Rich Presence). */
    fun correctSection(game: Game, sectionId: String) {
        viewModelScope.launch {
            val rp = guideRepo.progress(game.stableKey).lastRichPresence
            if (rp.isNullOrBlank()) guideRepo.setPosition(game.stableKey, sectionId)
            else guideRepo.correct(game.stableKey, rp, sectionId)
            applyLocated(game, guideRepo.continueGuide(game.stableKey))
        }
    }

    fun setGuideSpoiler(game: Game, level: com.shelf.launcher.data.guide.SpoilerLevel) {
        _guideState.update { st -> if (st?.gameKey == game.stableKey) st.copy(spoiler = level) else st }
        viewModelScope.launch { guideRepo.setSpoilerLevel(game.stableKey, level) }
    }

    fun setGuideMissablesMode(game: Game, on: Boolean) {
        _guideState.update { st -> if (st?.gameKey == game.stableKey) st.copy(missablesMode = on) else st }
        viewModelScope.launch { guideRepo.setMissablesMode(game.stableKey, on) }
    }

    /** The user's folder of .md/.txt guides (Storage Access Framework tree). */
    fun setLocalGuideFolder(uri: android.net.Uri) {
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        viewModelScope.launch {
            guideRepo.setLocalFolder(uri.toString())
            guideGame?.let { loadGuide(it, full = true) }
        }
    }

    fun closeGuide() {
        guideJob?.cancel()
        _guideState.update { st -> if (st?.loading == true) st.copy(loading = false) else st }
    }

    /** "I missed this" / undo. Only ever set by the person: RetroAchievements has no
     * reliable signal for it, so it is never inferred (see ra/MissableState). */
    fun setAchievementMissed(achievementId: Int, missed: Boolean) {
        val updated = raMissedMarksStore.set(achievementId, missed)
        _uiState.update { it.copy(raMissedMarks = updated) }
    }

    /** Opening a game's achievements: refresh its list (and profile / tips) if online. */
    fun loadAchievementDetails(game: Game) {
        refreshRaProfile()
        loadGameTips(game)
        refreshAchievementDetails(game, force = false)
    }

    /**
     * Shows the saved list straight away and replaces it with a fresh one.
     * Rules: a failed or empty answer never replaces good saved data (it only
     * marks it stale with the reason); a saved list that belongs to a different
     * RA game than the one linked now is discarded; a list fetched within the
     * last minute isn't fetched again unless [force] (e.g. after a play session).
     */
    /** "Retry" after a failed refresh: fetch again now, ignoring the one-minute throttle. */
    fun retryAchievementDetails(game: Game) = refreshAchievementDetails(game, force = true)

    private fun refreshAchievementDetails(game: Game, force: Boolean) {
        val gameId = game.raGameId ?: return
        val creds = raCredentialsStore.load() ?: return
        val key = game.stableKey
        val meta = raDetailsMeta[key]
        var shown = _uiState.value.raAchievementDetails[key]
        if (shown != null && meta?.gameId != gameId) {
            raDetailsMeta.remove(key)
            _uiState.update {
                it.copy(
                    raAchievementDetails = it.raAchievementDetails - key,
                    raDetailsFetchedAt = it.raDetailsFetchedAt - key,
                    raDetailsStale = it.raDetailsStale - key,
                    raDetailsError = it.raDetailsError - key
                )
            }
            shown = null
        }
        // Saved lists are shown at once; a fresh copy is fetched at most every 10 minutes (a play session forces one).
        val justFetched = meta?.fetchedAt?.let { System.currentTimeMillis() - it < 10 * 60_000L } == true
        if (!force && shown != null && justFetched && key !in _uiState.value.raDetailsError) return
        if (!raDetailsInFlight.add(key)) return // already fetching
        if (shown == null) {
            _uiState.update { it.copy(raAchievementDetailsLoading = it.raAchievementDetailsLoading + key) }
        }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { raRepository.fetchAchievementDetails(creds.username, creds.apiKey, gameId) }
                val had = _uiState.value.raAchievementDetails[key]
                when {
                    result is com.shelf.launcher.data.ra.RaResult.Ok && (result.value.isNotEmpty() || had.isNullOrEmpty()) -> {
                        val newMeta = RaDataCache.DetailsMeta(gameId, result.fetchedAt)
                        raDetailsMeta[key] = newMeta
                        // What's new since the previous check (old unlocks are never "new" again).
                        if (result.value.isNotEmpty()) {
                            com.shelf.launcher.data.AchievementSeen.observe(
                                getApplication(), com.shelf.launcher.data.AchievementSeen.keyRa(gameId),
                                result.value.filter { it.isUnlocked }.map { it.id }.toSet(), result.value.size
                            )
                        }
                        _uiState.update {
                            it.copy(
                                raAchievementDetails = it.raAchievementDetails + (key to result.value),
                                raDetailsFetchedAt = it.raDetailsFetchedAt + (key to result.fetchedAt),
                                raDetailsStale = it.raDetailsStale - key,
                                raDetailsError = it.raDetailsError - key
                            )
                        }
                        // An empty answer isn't worth keeping on disk.
                        if (result.value.isNotEmpty()) withContext(Dispatchers.IO) {
                            raDataCache.update { saved ->
                                saved.copy(
                                    details = saved.details.orEmpty() + (key to result.value),
                                    detailsMeta = saved.detailsMeta.orEmpty() + (key to newMeta)
                                )
                            }
                        }
                    }
                    else -> {
                        // Failed, or "no achievements" while we hold a real list: keep the saved one.
                        val error = (result as? com.shelf.launcher.data.ra.RaResult.Error)?.error
                            ?: com.shelf.launcher.data.ra.RaError(
                                com.shelf.launcher.data.ra.RaErrorKind.UNKNOWN,
                                "RetroAchievements returned no achievements for this game."
                            )
                        _uiState.update {
                            it.copy(
                                raDetailsError = it.raDetailsError + (key to error),
                                raDetailsStale = if (it.raAchievementDetails[key] != null) it.raDetailsStale + key else it.raDetailsStale
                            )
                        }
                    }
                }
            } finally {
                raDetailsInFlight.remove(key)
                _uiState.update { it.copy(raAchievementDetailsLoading = it.raAchievementDetailsLoading - key) }
            }
        }
    }

    /** After a play session: the played game's progress and achievement list
     * (unlocks change which missables are still open). */
    private fun refreshAfterSession(raGameId: Int) {
        val creds = raCredentialsStore.load() ?: return
        val games = _uiState.value.games.filter { it.raGameId == raGameId }
        if (games.isEmpty()) return
        viewModelScope.launch {
            val progress = withContext(Dispatchers.IO) {
                raRepository.fetchProgress(creds.username, creds.apiKey, listOf(raGameId))
            }.okValue?.progress?.get(raGameId)
            if (progress != null) {
                val byKey = games.associate { it.stableKey to progress }
                _uiState.update { it.copy(raProgress = it.raProgress + byKey) }
                withContext(Dispatchers.IO) {
                    raDataCache.update { saved -> saved.copy(progress = saved.progress.orEmpty() + byKey) }
                }
            }
            games.forEach { refreshAchievementDetails(it, force = true) }
        }
    }

    /** Fetches the "recently unlocked" feed — everything unlocked in the
     * last [minutesBack] minutes across all games, not scoped to a single
     * linked game. Defaults to a day-wide window since the API's own
     * default (60 minutes) would be empty most of the time someone actually
     * opens this. */
    fun loadRecentAchievements(minutesBack: Int = 1440) {
        val creds = raCredentialsStore.load() ?: return
        _uiState.update { it.copy(isRaRecentLoading = true) }
        val gen = raGen
        viewModelScope.launch {
            val recent = raRepository.fetchRecentAchievements(creds.username, creds.apiKey, minutesBack)
            if (gen != raGen) return@launch
            _uiState.update { it.copy(raRecentAchievements = recent, isRaRecentLoading = false) }
        }
    }

    /** Forgets the saved SteamGridDB key and turns art fetching back off. */
    fun disconnectArt() {
        sgdbKeyStore.clearKey()
        SgdbAuth.apiKey = null
        scrapeJob?.cancel()
        sgdbRetryJob?.cancel()
        artCacheStore.clear()
        _uiState.update { it.copy(artState = ArtConnectionState.OFF, art = emptyMap()) }
    }

    fun connectRa(username: String, apiKey: String) {
        if (username.isBlank() || apiKey.isBlank()) return
        _uiState.update {
            it.copy(raState = RaConnectionState.CONNECTING, raUsername = username, statusMessage = "Testing connection…")
        }
        viewModelScope.launch {
            fetchAllRaProgress(RaCredentials(username, apiKey), persistOnSuccess = true, savedCredsRestore = false)
        }
    }

    /** Forgets the active RetroAchievements account and clears progress data (a second account, if saved, takes over). */
    fun disconnectRa() {
        raCredentialsStore.clear()
        clearRaState()
        if (raCredentialsStore.load() != null) reconnectActiveRa()
    }

    /** What the screens show of the account in use is emptied (the next account's data is fetched afterwards). */
    @Volatile private var raGen = 0

    /** Puts the saved accounts' names / the playing slot into the state (the screens read them from there). */
    private fun syncRaAccounts() {
        _uiState.update { it.copy(raAccounts = raCredentialsStore.usernames(), raActiveSlot = raCredentialsStore.activeSlot()) }
    }

    private fun clearRaState() {
        // Fetches already under way belong to the account that was in use: they drop their answers (raGen).
        raGen++
        raDataCache.clear()
        raDetailsMeta.clear()
        _uiState.update {
            it.copy(
                raState = RaConnectionState.OFF,
                raUsername = null,
                raProgress = emptyMap(),
                isRaRecentLoading = false,
                raRecentAchievements = emptyList(),
                raProfile = null,
                raDetailsError = emptyMap(),
                raDetailsFetchedAt = emptyMap(),
                raDetailsStale = emptySet(),
                raGameTipsError = emptyMap(),
                raAchievementDetails = emptyMap(),
                raCompletionById = emptyMap(),
                raProfileStats = RaProfileStats(),
                raAccounts = raCredentialsStore.usernames(),
                raActiveSlot = raCredentialsStore.activeSlot()
            )
        }
    }

    /** Fetches the active account's progress afresh (after a switch). */
    private fun reconnectActiveRa() {
        val creds = raCredentialsStore.load() ?: return
        _uiState.update { it.copy(raState = RaConnectionState.CONNECTING, raUsername = creds.username) }
        val gen = raGen
        viewModelScope.launch {
            fetchAllRaProgress(creds, persistOnSuccess = false, savedCredsRestore = true)
            if (gen != raGen) return@launch
            autoMatchAchievements(silent = true)
        }
    }

    /** Both RetroAchievements accounts' user names (null: empty), and which one is playing. */
    fun raAccountNames(): List<String?> = raCredentialsStore.usernames()
    fun raActiveSlot(): Int = raCredentialsStore.activeSlot()

    /** "Who is playing": the account in [slot] takes over; its achievements replace the other's. */
    fun switchRaAccount(slot: Int) {
        if (raCredentialsStore.loadSlot(slot) == null) return
        if (raCredentialsStore.activeSlot() == slot && _uiState.value.raState != RaConnectionState.OFF) return
        raCredentialsStore.setActive(slot)
        clearRaState()
        reconnectActiveRa()
    }

    /** Adds (or replaces) the second account after a test login; it becomes the one playing. */
    fun addRaAccount(username: String, apiKey: String) {
        if (username.isBlank() || apiKey.isBlank()) return
        val slot = if (raCredentialsStore.loadSlot(0) == null) 0 else 1
        _uiState.update { it.copy(statusMessage = "Testing connection…") }
        viewModelScope.launch {
            val r = raRepository.fetchProfileResult(username, apiKey)
            if (r is com.shelf.launcher.data.ra.RaResult.Error) {
                _uiState.update { it.copy(statusMessage = "Couldn't connect that account — check the username and web API key.") }
                return@launch
            }
            raCredentialsStore.saveSlot(slot, RaCredentials(username, apiKey))
            raCredentialsStore.setActive(slot)
            clearRaState()
            _uiState.update { it.copy(statusMessage = "$username added.") }
            reconnectActiveRa()
        }
    }

    /** Forgets one of the two accounts. */
    fun removeRaAccount(slot: Int) {
        val wasActive = raCredentialsStore.activeSlot() == slot
        raCredentialsStore.remove(slot)
        syncRaAccounts() // the other account moves up a slot: the list on screen follows at once
        if (wasActive || raCredentialsStore.load() == null) {
            clearRaState()
            if (raCredentialsStore.load() != null) reconnectActiveRa()
        }
    }

    /**
     * Tests the connection, then (on success) fetches progress for every
     * game that has a manually-linked raGameId, batched into one API call.
     * `savedCredsRestore` only changes messaging, same pattern as the
     * SteamGridDB art fetch.
     */
    private suspend fun fetchAllRaProgress(
        creds: RaCredentials,
        persistOnSuccess: Boolean,
        savedCredsRestore: Boolean
    ) {
        val gen = raGen
        val profileResult = raRepository.fetchProfileResult(creds.username, creds.apiKey)
        if (gen != raGen) return // another account took over meanwhile
        if (profileResult is com.shelf.launcher.data.ra.RaResult.Error) {
            val authProblem = profileResult.error.kind == com.shelf.launcher.data.ra.RaErrorKind.AUTH
            _uiState.update {
                it.copy(
                    raState = RaConnectionState.FAILED,
                    statusMessage = when {
                        // Only a rejected login says the login is bad; being offline doesn't.
                        !authProblem && savedCredsRestore -> "Couldn't reach RetroAchievements — showing saved data."
                        !authProblem -> "Couldn't connect to RetroAchievements. ${profileResult.error.message}"
                        savedCredsRestore -> "Saved RetroAchievements login stopped working — reconnect from the Achievements button."
                        else -> "Couldn't connect to RetroAchievements — check the username and web API key."
                    }
                )
            }
            return
        }
        val profile = profileResult.okValue

        if (persistOnSuccess) {
            raCredentialsStore.save(creds)
            syncRaAccounts()
        }
        _uiState.update { it.copy(raProfile = profile ?: it.raProfile) }

        val linkedGames = _uiState.value.games.filter { it.raGameId != null }
        val progressResult = raRepository.fetchProgress(creds.username, creds.apiKey, linkedGames.mapNotNull { it.raGameId })
        if (gen != raGen) return
        // Merge onto what we have: a failed request (or chunk) must not wipe good progress
        // (nor an empty library at startup, before games have loaded).
        val merged: Map<String, RaGameProgress>? = (progressResult as? com.shelf.launcher.data.ra.RaResult.Ok)?.let { ok ->
            val fresh = linkedGames.mapNotNull { game ->
                game.raGameId?.let { id -> ok.value.progress[id]?.let { game.stableKey to it } }
            }.toMap()
            _uiState.value.raProgress + fresh
        }
        val progressError = progressResult.errorOrNull ?: (progressResult as? com.shelf.launcher.data.ra.RaResult.Ok)?.value?.firstError

        _uiState.update {
            it.copy(
                raState = RaConnectionState.ON,
                statusMessage = when {
                    savedCredsRestore -> null
                    progressError != null -> "Connected to RetroAchievements, but progress couldn't be fully refreshed. ${progressError.message}"
                    else -> "Connected to RetroAchievements."
                },
                raProgress = merged ?: it.raProgress
            )
        }
        withContext(Dispatchers.IO) {
            raDataCache.update { saved ->
                saved.copy(profile = profile ?: saved.profile, progress = merged ?: saved.progress)
            }
        }
        refreshRaCompletion()
    }

    fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // pre-scoped-storage devices; a real app should still check
                 // READ_EXTERNAL_STORAGE here for API 23-29.
        }
    }

    fun scanApps(silent: Boolean = false) {
        viewModelScope.launch {
            // One scan at a time: a burst of package events must not overlap.
            scanMutex.withLock {
                val scanned = withContext(Dispatchers.IO) {
                    runCatching { AppScanner.scanOrNull(getApplication()) }.getOrNull()
                }
                // A failed scan keeps what we had rather than emptying the drawer.
                if (scanned == null && allScannedApps.isNotEmpty()) return@withLock
                val apps = scanned.orEmpty()
                allScannedApps = apps
                com.shelf.launcher.data.InstalledAppsHub.apps.value = apps
                val (gameApps, emulatorApps) = withContext(Dispatchers.IO) { classifyApps(apps) }
                _uiState.update {
                    it.copy(installedApps = apps, gameAppPackages = gameApps, knownEmulatorPackages = emulatorApps)
                }
                applyAppVisibility()
                refreshShortcuts()
                applyDefaultEmulators()
                pruneSecondDock(apps)
                if (!silent) {
                    _uiState.update { it.copy(statusMessage = "Found ${currentAppGames.size} apps.") }
                }
            }
        }
    }

    /** Uninstalled apps leave the second-screen dock (and empty slots close up). */
    private fun pruneSecondDock(apps: List<Game>) {
        if (apps.isEmpty()) return
        val installed = apps.mapNotNull { it.packageName }.toSet()
        val current = _uiState.value.appearance.secondDockApps
        val pruned = current.filter { it.isNotBlank() && it in installed }.distinct()
        if (pruned != current) setAppearance(_uiState.value.appearance.copy(secondDockApps = pruned))
    }

    /** Which installed apps are games (Android's own "game" category) and which
     * are emulators Shelf knows. */
    private fun classifyApps(apps: List<Game>): Pair<Set<String>, Set<String>> {
        val pm = getApplication<Application>().packageManager
        val packages = apps.mapNotNull { it.packageName }
        val games = packages.filter { pkg ->
            runCatching { looksLikeGame(pm, pkg) }.getOrDefault(false)
        }.toSet()
        val emulators = packages.filter { pkg ->
            (EmulatorRegistry.displayNameFor(pkg) != null || CoreRegistry.isRetroArch(pkg) ||
                com.shelf.launcher.data.LaunchProfiles.find(pkg) != null) &&
                com.shelf.launcher.data.EmulatorCatalog.isRealEmulator(pm, pkg)
        }.toSet()
        return games to emulators
    }

    /**
     * Whether an app looks like a game. Android's own "game" category is
     * used by far fewer games than it should be, so it also checks:
     *  - the store's game publishers (package-name prefixes of big studios),
     *  - a game engine inside the app (Unity, Unreal, Godot, GameMaker,
     *    cocos2d, libGDX … — their native libraries have fixed names),
     *  - apps that declare gamepad support.
     */
    private fun looksLikeGame(pm: android.content.pm.PackageManager, pkg: String): Boolean {
        val info = pm.getApplicationInfo(pkg, 0)
        if (isGameApp(info)) return true
        if (GAME_PUBLISHER_PREFIXES.any { pkg.startsWith(it) }) return true
        val libs = runCatching { java.io.File(info.nativeLibraryDir ?: "").list()?.toList() }.getOrNull().orEmpty()
        if (libs.any { lib -> GAME_ENGINE_LIBS.any { lib.equals(it, ignoreCase = true) } }) return true
        val features = runCatching {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, android.content.pm.PackageManager.GET_CONFIGURATIONS).reqFeatures
        }.getOrNull().orEmpty()
        return features.any { it.name == "android.hardware.gamepad" }
    }

    @Suppress("DEPRECATION")
    private fun isGameApp(info: android.content.pm.ApplicationInfo): Boolean =
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && info.category == android.content.pm.ApplicationInfo.CATEGORY_GAME) ||
            (info.flags and android.content.pm.ApplicationInfo.FLAG_IS_GAME) != 0

    // ---- Which apps are in the library, and how they look ----

    private fun updateLibraryApps(transform: (LibraryAppsSettings) -> LibraryAppsSettings) {
        val updated = transform(_uiState.value.libraryApps)
        libraryAppsStore.save(updated)
        _uiState.update { state ->
            val next = state.copy(libraryApps = updated)
            next.copy(focusedIndex = next.focusedIndex.coerceIn(0, (next.visibleGames.size - 1).coerceAtLeast(0)))
        }
    }

    fun setAppsMode(mode: AppsMode) = updateLibraryApps { it.copy(mode = mode) }

    /** Marks an installed app as an Android game, a plain app, or back to automatic. */
    fun setAppKind(packageName: String, kind: com.shelf.launcher.data.AppKind) = updateLibraryApps {
        it.copy(
            forcedGames = if (kind == com.shelf.launcher.data.AppKind.GAME) it.forcedGames + packageName else it.forcedGames - packageName,
            forcedApps = if (kind == com.shelf.launcher.data.AppKind.APP) it.forcedApps + packageName else it.forcedApps - packageName
        )
    }

    fun toggleAppInLibrary(packageName: String) = updateLibraryApps {
        it.copy(added = if (packageName in it.added) it.added - packageName else it.added + packageName)
    }

    fun toggleAppEmulator(packageName: String) = updateLibraryApps {
        it.copy(emulators = if (packageName in it.emulators) it.emulators - packageName else it.emulators + packageName)
    }

    /** Show this app's own icon (the blurred-icon card) instead of scraped art. */
    fun toggleAppIconArt(packageName: String) = updateLibraryApps {
        it.copy(iconArt = if (packageName in it.iconArt) it.iconArt - packageName else it.iconArt + packageName)
    }

    fun setAppsAlwaysUseIcons(enabled: Boolean) = updateLibraryApps { it.copy(alwaysUseIcons = enabled) }

    /** Hides an installed app from the library — Settings, Files, and
     * anything else that isn't actually a game/emulator clutters up an
     * unfiltered app-drawer dump, so this is the escape hatch for that. */
    fun hideApp(game: Game) {
        val packageName = game.packageName ?: return
        hiddenAppsStore.hide(packageName)
        applyAppVisibility()
    }

    fun unhideApp(packageName: String) {
        hiddenAppsStore.unhide(packageName)
        applyAppVisibility()
    }

    /** Splits [allScannedApps] into the visible list (feeds the dock) and the
     * hidden list (feeds the "manage hidden apps" section of the Library
     * dialog), then recombines the displayed library. */
    private fun applyAppVisibility() {
        val hidden = hiddenAppsStore.loadAll()
        currentAppGames = allScannedApps.filterNot { it.packageName in hidden }
        val hiddenEntries = allScannedApps.filter { it.packageName in hidden }
        recombineGames()
        _uiState.update { it.copy(hiddenApps = hiddenEntries) }
    }

    /**
     * Scans [rootPath] for ROMs and replaces the ROM half of the library if
     * it finds any. `silent` suppresses the "scanning…"/result status
     * messages, used for the automatic startup scan so it doesn't flash text
     * on every launch.
     */
    /** Where the last successful ROM scan is kept, to show it at the next start. */
    // Getters, not stored values: init (further up) already uses them, and
    // Kotlin sets stored properties in file order.
    private val romCacheFile: java.io.File
        get() = com.shelf.launcher.data.AppStorage.file(
            getApplication(), "Data", "rom_library.json", java.io.File(getApplication<Application>().filesDir, "rom_library.json")
        )
    private val romCacheType: java.lang.reflect.Type
        get() = object : com.google.gson.reflect.TypeToken<List<Game>>() {}.type

    @Suppress("SENSELESS_COMPARISON")
    /** A real scan has set [currentRomGames]: the saved list (read in the background) must not replace it. */
    @Volatile private var romScanApplied = false

    // (Update 188: read from the moment Voltcap starts — RomLibraryPrefetch.)
    private fun loadRomCache(): List<Game> = com.shelf.launcher.data.RomLibraryPrefetch.take { romCacheFile }

    private fun saveRomCache(games: List<Game>) {
        runCatching {
            val tmp = java.io.File(romCacheFile.parentFile, romCacheFile.name + ".tmp")
            tmp.writeText(com.google.gson.Gson().toJson(games))
            if (!tmp.renameTo(romCacheFile)) {
                romCacheFile.delete()
                tmp.renameTo(romCacheFile)
            }
        }
    }

    /** Waits between automatic retries when storage isn't readable yet (s). */
    private val scanRetryDelays: List<Int> get() = listOf(2, 4, 8, 15, 20, 30)

    /** (update 91) The silent scan (and its retries) in progress: a resume doesn't start a second chain. */
    private var romRetryJob: Job? = null

    /** Scans every ROM folder. [rootPath] becomes the main folder if it
     * isn't one of them yet (first-run setup passes the one picked there). */
    fun scanLibrary(rootPath: String, silent: Boolean = false) {
        if (rootPath !in _uiState.value.romRootPaths) {
            libraryStore.saveRootPath(rootPath)
            _uiState.update { it.copy(romRootPath = rootPath, romRootPaths = libraryStore.loadRootPaths()) }
        }
        scanAllFolders(silent, attempt = 0)
    }

    /** R1 › Rescan: every ROM folder and the installed apps, now (nothing while a scan already runs). */
    fun rescanEverything() {
        if (_uiState.value.isScanning) return
        if (_uiState.value.romRootPaths.isNotEmpty()) scanAllFolders(silent = false, attempt = 0)
        scanApps(silent = true)
    }

    /**
     * Update 177: Library check › Make the .m3u files: a playlist for every multi-disc game without one (in the folder
     * with its discs, named after the game), then the folders are scanned again.
     */
    fun makeMissingM3u(only: List<com.shelf.launcher.data.RomScanner.MultiDiscSet>? = null) {
        val sets = only ?: com.shelf.launcher.data.LibraryCheck.missingM3u
        if (sets.isEmpty()) return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { sets.count { com.shelf.launcher.data.RomScanner.writeM3u(it) } }
            val failed = sets.size - ok
            setStatusMessage(
                (if (ok == 1) "1 playlist made." else "$ok playlists made.") +
                    if (failed > 0) " $failed couldn't be written — the folder may be read-only (an SD card without write access)." else ""
            )
            scanAllFolders(silent = true, attempt = 99)
        }
    }

    /** Settings › Library › ROM folders: saves the folders and deep search, then scans them all. */
    fun scanLibraryFolders(paths: List<String>, deep: Boolean) {
        libraryStore.saveRootPaths(paths)
        libraryStore.saveDeepSearch(deep)
        val saved = libraryStore.loadRootPaths()
        _uiState.update { it.copy(romRootPaths = saved, romRootPath = saved.first(), deepSearch = deep) }
        scanAllFolders(silent = false, attempt = 0)
    }

    private fun scanAllFolders(silent: Boolean, attempt: Int) {
        val roots = _uiState.value.romRootPaths
        val deep = _uiState.value.deepSearch
        val rootPath = if (roots.size == 1) roots.first() else "${roots.size} folders"
        _uiState.update {
            it.copy(
                isScanning = true,
                statusMessage = if (silent) it.statusMessage else "Scanning $rootPath…"
            )
        }

        val job = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { RomScanner.scanAll(roots, deep) }.getOrNull()
            }

            // Nothing readable on an automatic scan: most likely the device
            // has only just started and storage isn't mounted yet. Keep the
            // saved list and try again a few times over the next minute and a half.
            if (silent && (result == null || result.games.isEmpty()) && attempt < scanRetryDelays.size) {
                _uiState.update { it.copy(isScanning = false) }
                kotlinx.coroutines.delay(scanRetryDelays[attempt] * 1000L)
                scanAllFolders(silent = true, attempt = attempt + 1)
                return@launch
            }

            when {
                result == null -> _uiState.update {
                    it.copy(
                        isScanning = false,
                        statusMessage = if (silent) it.statusMessage else
                            "Couldn't read $rootPath — check the path and storage permission."
                    )
                }
                result.games.isEmpty() -> _uiState.update {
                    it.copy(
                        isScanning = false,
                        statusMessage = if (silent) it.statusMessage else
                            "No ROMs found in $rootPath for the known file types."
                    )
                }
                else -> {
                    // The game you're on stays selected (the startup scan finishes after the saved list is
                    // already showing and the last game re-selected: it used to jump back to the first game).
                    val keyBefore = _uiState.value.let { it.visibleGames.getOrNull(it.focusedIndex)?.stableKey }
                    currentRomGames = result.games
                    romScanApplied = true
                    // Update 177: multi-disc games without a playlist and doubles, for Library check.
                    com.shelf.launcher.data.LibraryCheck.missingM3u = result.missingM3u
                    com.shelf.launcher.data.LibraryCheck.duplicates = result.duplicates
                    com.shelf.launcher.data.LibraryCheck.scanned = true
                    withContext(Dispatchers.IO) { saveRomCache(result.games) }
                    recombineGames()
                    val discNote = when {
                        result.multiDiscGroupsCreated > 0 && result.multiDiscWriteFailures > 0 ->
                            " (${result.multiDiscGroupsCreated} multi-disc games grouped, " +
                                "${result.multiDiscWriteFailures} couldn't be — check write access)"
                        result.multiDiscGroupsCreated > 0 ->
                            " (${result.multiDiscGroupsCreated} multi-disc games grouped into .m3u)"
                        result.multiDiscWriteFailures > 0 ->
                            " (${result.multiDiscWriteFailures} multi-disc sets found but couldn't write .m3u)"
                        else -> ""
                    }
                    _uiState.update {
                        val kept = keyBefore?.let { k -> it.visibleGames.indexOfFirst { g -> g.stableKey == k } } ?: -1
                        it.copy(
                            usingSampleLibrary = false,
                            focusedIndex = if (kept >= 0) kept else it.focusedIndex.coerceIn(0, (it.visibleGames.size - 1).coerceAtLeast(0)),
                            isScanning = false,
                            statusMessage = if (silent) null else "Found ${result.games.size} ROMs.$discNote" +
                                (if (result.unreadable.isNotEmpty()) " Couldn't read: ${result.unreadable.joinToString(", ")}." else "")
                        )
                    }
                    if (_uiState.value.artState == ArtConnectionState.ON) {
                        scrapeMissingArt()
                    }
                    if (_uiState.value.raState == RaConnectionState.ON) {
                        autoMatchAchievements(silent = true)
                    }
                }
            }
        }
        if (silent) romRetryJob = job
    }

    /** Merges the ROM, app, and Steam lists, reapplies manual SteamGridDB/
     * RetroAchievements ID overrides, then sorts so recently-played games
     * surface first (most recent first) — that's the entire "continue
     * playing" behavior: no separate shelf needed, the first/focused slot on
     * open is just whatever you played most recently. Anything never played
     * keeps its existing name-sorted order after that. */
    // ---- Collections, franchises and play status ----

    // A getter, not a stored value: init (earlier in the file) already uses it.
    private val collectionsStore: com.shelf.launcher.data.CollectionsStore
        get() = com.shelf.launcher.data.CollectionsStore(getApplication())

    /** Rebuilds the collection categories (yours, franchises, statuses). */
    private fun refreshCollections() {
        val s = _uiState.value
        val user = collectionsStore.loadUser()
        val statuses = collectionsStore.loadStatuses()
        val built = com.shelf.launcher.data.buildCollections(
            s.games, user, statuses,
            userOn = s.appearance.userCollections,
            franchisesOn = s.appearance.franchiseCollections,
            statusOn = s.appearance.playStatus,
            genresOn = s.appearance.genreCollections,
            metadata = s.metadata
        )
        _uiState.update {
            // A collection that's gone (switched off, emptied) can't stay selected.
            // (While restoring after a game the library may not be complete yet: finishRestore checks later.)
            val stillThere = restoreActive || !com.shelf.launcher.data.isCollectionKey(it.platformFilter) ||
                built.any { c -> c.key == it.platformFilter }
            it.copy(
                collections = built, userCollections = user, playStatuses = statuses,
                platformFilter = if (stillThere) it.platformFilter else null
            )
        }
        // The saved game may live in a collection that only exists now.
        if (pendingFocusKey != null) _uiState.update { st ->
            val key = pendingFocusKey
            val idx = st.visibleGames.indexOfFirst { g -> g.stableKey == key }
            if (idx < 0) st else { pendingFocusKey = null; restoreActive = false; st.copy(focusedIndex = idx) }
        }
    }

    /** Puts [game] in your collection [name], or takes it out. */
    fun toggleInCollection(name: String, game: Game) {
        val user = collectionsStore.loadUser().toMutableMap()
        val keys = user[name].orEmpty()
        user[name] = if (game.stableKey in keys) keys - game.stableKey else keys + game.stableKey
        collectionsStore.saveUser(user)
        refreshCollections()
    }

    /** A new collection, with [game] in it when given. */
    fun createCollection(name: String, game: Game?) {
        val clean = name.trim().replace(",", " ").take(40)
        if (clean.isBlank()) return
        val user = collectionsStore.loadUser().toMutableMap()
        user[clean] = user[clean].orEmpty() + listOfNotNull(game?.stableKey)
        collectionsStore.saveUser(user)
        refreshCollections()
    }

    fun renameCollection(old: String, new: String) {
        val clean = new.trim().replace(",", " ").take(40)
        if (clean.isBlank() || clean == old) return
        val user = collectionsStore.loadUser().toMutableMap()
        val keys = user.remove(old) ?: return
        user[clean] = user[clean].orEmpty() + keys
        collectionsStore.saveUser(user)
        refreshCollections()
    }

    fun deleteCollection(name: String) {
        val user = collectionsStore.loadUser().toMutableMap()
        if (user.remove(name) != null) {
            collectionsStore.saveUser(user)
            refreshCollections()
        }
    }

    /** Playing / Finished / Dropped / Wishlist — or null to clear it. */
    fun setPlayStatus(game: Game, status: com.shelf.launcher.data.PlayStatus?) {
        val map = collectionsStore.loadStatuses().toMutableMap()
        if (status == null) map.remove(game.stableKey)
        // Picking the same status again keeps its date ("finished this month").
        else if (map[game.stableKey]?.status != status) {
            map[game.stableKey] = com.shelf.launcher.data.StatusEntry(status, System.currentTimeMillis())
        }
        collectionsStore.saveStatuses(map)
        refreshCollections()
    }

    private fun recombineGames() {
        val lastPlayed = recentlyPlayedStore.loadVisible() // (Update 244: without games taken off Continue playing)
        val merged = (applyOverrides(currentRomGames) + applyOverrides(currentAppGames) + applyOverrides(currentSteamGames) +
            applyOverrides(com.shelf.launcher.data.PinnedShortcuts.games(getApplication())))
            .sortedByDescending { lastPlayed[it.stableKey] ?: -1L }
        _uiState.update { current ->
            val updated = current.copy(games = merged, lastPlayedAt = lastPlayed)
            // First time the game from last session is in the list: select it again.
            val restored = pendingFocusKey?.let { key ->
                updated.visibleGames.indexOfFirst { it.stableKey == key }.takeIf { it >= 0 }
            }
            if (restored != null) { pendingFocusKey = null; restoreActive = false }
            updated.copy(
                focusedIndex = (restored ?: updated.focusedIndex)
                    .coerceIn(0, (updated.visibleGames.size - 1).coerceAtLeast(0))
            )
        }
        refreshCollections()
        recordFirstSeen(merged)
    }


    private fun applyOverrides(games: List<Game>): List<Game> {
        if (games.isEmpty()) return games
        val overrides = overridesStore.loadAll()
        val autoMatches = raMatchStore.loadAll()
        return games.map { game ->
            val override = overrides[game.stableKey]
            game.copy(
                sgdbGameId = override?.sgdbGameId,
                // A hand-set ID always beats an auto-match.
                raGameId = override?.raGameId ?: autoMatches[game.stableKey]
            )
        }
    }

    /** A game waiting for its saves to come down from Google Drive. */
    private var launchAfterSync: Job? = null

    /** This game's saves, fetched from Drive in the background while its
     * details window is open — so they're here before Play, without waiting. */
    private var preparedCheck: Pair<String, kotlinx.coroutines.Deferred<com.shelf.launcher.data.SyncOutcome>>? = null

    /** Called when a game's details window opens (Play never waits for it). */
    /** When each game's saves were last fetched early, so resting on a game
     * again and again doesn't ask Drive every time. */
    private val lastPrefetch = mutableMapOf<String, Long>()

    fun prepareLaunch(game: Game) {
        // A game is open (the emulator owns its saves), or this game's saves are switched off: never pull from Drive.
        if (com.shelf.launcher.data.PlaySession.isOpen(getApplication())) return
        if (!effectiveProfile(game).syncSaves.value) return
        val now = System.currentTimeMillis()
        if (now - (lastPrefetch[game.stableKey] ?: 0L) < 3 * 60_000L) return
        lastPrefetch[game.stableKey] = now
        if (preparedCheck?.first == game.stableKey && preparedCheck?.second?.isActive == true) return
        val job = com.shelf.launcher.data.CloudSync.syncForGameInBackground(getApplication(), game) ?: return
        preparedCheck = game.stableKey to job
    }

    /**
     * Starts [game] straight away — it never waits for Google Drive. Saves
     * come down earlier (when the game's details window opens, see
     * [prepareLaunch]) and go up when you come back to Voltcap. A check
     * still running at this moment stops before touching another file, so
     * nothing changes under the emulator.
     */
    fun launch(game: Game) {
        com.shelf.launcher.data.ThemeRotator.onLaunch(getApplication(), _uiState.value.appTheme)?.let { setTheme(it) }
        val check = preparedCheck?.second
        if (check != null && check.isActive) com.shelf.launcher.data.CloudSync.stop()
        com.shelf.launcher.data.CloudSync.stopWatching()
        preparedCheck = null
        showSyncNotice(null)
        launchNow(game)
    }

    private var syncNoticeJob: Job? = null

    /** The small sync note in the corner; gone after [forMs] (null: stays). */
    private fun showSyncNotice(text: String?, forMs: Long? = 5_000, kind: com.shelf.launcher.data.SyncNoticeKind? = null) {
        syncNoticeJob?.cancel()
        _uiState.update { it.copy(syncNotice = text, syncNoticeKind = if (text != null) kind else null) }
        if (text != null && forMs != null) {
            syncNoticeJob = viewModelScope.launch {
                kotlinx.coroutines.delay(forMs)
                _uiState.update { it.copy(syncNotice = null, syncNoticeKind = null) }
            }
        }
    }

    /** Offline prompt: start with the saves this device has. */
    fun playWithoutSync() {
        val game = _uiState.value.syncOfflinePrompt?.game ?: return
        _uiState.update { it.copy(syncOfflinePrompt = null) }
        launchNow(game)
    }

    /** Offline prompt: check Drive again. */
    fun retrySyncAndPlay() {
        val game = _uiState.value.syncOfflinePrompt?.game ?: return
        _uiState.update { it.copy(syncOfflinePrompt = null) }
        launch(game)
    }

    fun dismissSyncOfflinePrompt() {
        _uiState.update { it.copy(syncOfflinePrompt = null) }
    }

    /** The player picked which saves to keep; a waiting game starts after. */
    fun resolveSyncConflicts(choice: com.shelf.launcher.data.ConflictChoice) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            showSyncNotice("Sorting out saves…", forMs = null)
            val outcome = com.shelf.launcher.data.CloudSync.resolveConflicts(app, choice)
            val game = _uiState.value.gameWaitingOnConflicts
            _uiState.update { it.copy(gameWaitingOnConflicts = null) }
            showSyncNotice(outcome.summary)
            if (game == null) return@launch
            if (outcome.ok) launchNow(game)
            else _uiState.update { it.copy(syncOfflinePrompt = SyncOfflinePrompt(game, outcome.summary)) }
        }
    }

    /** "Decide later" — or backing out of the game that was waiting. The
     * saves in question stay exactly as they are on both sides. */
    fun postponeSyncConflicts() {
        com.shelf.launcher.data.CloudSync.snoozeConflicts()
        _uiState.update { it.copy(gameWaitingOnConflicts = null) }
    }

    /** Favourites, playtime, recently played, artwork picks and manual IDs
     * were just merged in from Google Drive. */
    private fun reloadSyncedData() {
        _uiState.update {
            it.copy(
                favorites = favoritesStore.loadAll(),
                playtime = playtimeStore.loadTotals(),
                artOverrides = artOverridesStore.loadAll()
            )
        }
        recombineGames() // re-applies manual IDs and the recently-played order
    }

    // ---- Screen layouts (second screen / TV) ----

    /** Set from a game's Screens option: the layout for the next launch only. */
    private var nextLaunchLayoutId: String? = null
    /** When the other screen was last filled — see [onLauncherResumed]. */
    private var lastPlacementAt = 0L

    /** What a layout decision needs to know about the connected screen. */
    data class ScreenPlan(
        val screen: com.shelf.launcher.data.SecondScreenInfo,
        val setup: com.shelf.launcher.data.ScreenSetup,
        val resolved: com.shelf.launcher.data.ResolvedLayout
    )

    /** The second screen right now, and which setup it counts as. Null when
     * there's none, or "Use the second screen" is off. */
    fun currentScreen(): Pair<com.shelf.launcher.data.SecondScreenInfo, com.shelf.launcher.data.ScreenSetup>? {
        if (!_uiState.value.appearance.useSecondScreen) return null
        val screen = com.shelf.launcher.data.ScreenPlacement.secondScreen(getApplication()) ?: return null
        return screen to screenLayoutsStore.setupFor(screen)
    }

    /** The emulator (or GameNative) behind a game, for emulator rules. */
    private fun emulatorPackageFor(game: Game): String? = when (game.sourceType) {
        SourceType.ROM -> if (game.platform == "DroidDeck") com.shelf.launcher.data.GameClients.DROIDDECK
            else gameEmulatorOverride(game) ?: _uiState.value.emulatorConfigs[game.platform]?.packageName
        SourceType.STEAM -> _uiState.value.gameNativeConfig.packageName
        SourceType.APP -> null
    }

    private fun collectionKeysFor(game: Game): List<String> =
        _uiState.value.collections.filter { game.stableKey in it.keys }.map { it.key }

    /** Which layout [game] would start with, and why. Null without a second screen. */
    fun screenPlanFor(game: Game): ScreenPlan? {
        val (screen, setup) = currentScreen() ?: return null
        val resolved = screenLayoutsStore.resolve(setup, game, collectionKeysFor(game), emulatorPackageFor(game))
        return ScreenPlan(screen, setup, resolved)
    }

    /** Layouts to pick from (built in, then yours). */
    fun screenLayouts(): List<com.shelf.launcher.data.ScreenLayout> = screenLayoutsStore.layouts()

    /** Collections [game] is in, as (key, name), for the "set for a collection" choice. */
    fun collectionsOf(game: Game): List<Pair<String, String>> =
        _uiState.value.collections.filter { game.stableKey in it.keys }.map { it.key to it.name }

    fun emulatorFor(game: Game): String? = emulatorPackageFor(game)

    /**
     * Update 326: the game's own emulator (Game settings › Emulator), when it is still installed. An uninstalled one is
     * kept in the game's settings (it comes back when the emulator does) but the console's emulator is used meanwhile.
     */
    fun gameEmulatorOverride(game: Game): String? {
        if (game.sourceType != SourceType.ROM) return null
        val pkg = gameProfileStore.game(game.stableKey).emulatorPackage ?: return null
        val pm = getApplication<Application>().packageManager
        return pkg.takeIf { runCatching { pm.getPackageInfo(it, 0) }.isSuccess }
    }

    /** Update 326: sets (or with null clears) [game]'s own emulator. */
    fun setGameEmulator(game: Game, packageName: String?) {
        updateGameProfile(game) { it.copy(emulatorPackage = packageName) }
    }

    // ---- Game settings (per-game / per-console overrides) ----

    fun effectiveProfile(game: Game): com.shelf.launcher.data.ResolvedProfile =
        gameProfileStore.resolve(
            game,
            globalSaves = com.shelf.launcher.data.CloudSync.state.value.settings.syncSaves,
            globalPage = _uiState.value.appearance.secondPage,
            globalPerf = com.shelf.launcher.data.perf.PerfSettings.config(getApplication<Application>()).showDuringGames
        )

    fun gameProfile(game: Game) = gameProfileStore.game(game.stableKey)
    fun categoryProfile(game: Game) = gameProfileStore.category(game.platform)

    fun updateGameProfile(game: Game, change: (com.shelf.launcher.data.GameProfile) -> com.shelf.launcher.data.GameProfile) {
        gameProfileStore.updateGame(game.stableKey, change)
        _profileVersion.value++
    }

    fun updateCategoryProfile(game: Game, change: (com.shelf.launcher.data.GameProfile) -> com.shelf.launcher.data.GameProfile) {
        gameProfileStore.updateCategory(game.platform, change)
        _profileVersion.value++
    }

    fun resetGameProfile(game: Game) {
        gameProfileStore.clearGame(game.stableKey)
        _profileVersion.value++
    }

    /** Spoiler level for the game settings dialog (suspend read off the main thread). */
    suspend fun guideSpoilerOf(game: Game) = guideRepo.spoilerLevel(game.stableKey)
    fun setGuideSpoilerOnly(game: Game, level: com.shelf.launcher.data.guide.SpoilerLevel) {
        viewModelScope.launch { guideRepo.setSpoilerLevel(game.stableKey, level) }
        _guideState.update { st -> if (st?.gameKey == game.stableKey) st.copy(spoiler = level) else st }
    }

    /** Saves a rule for the connected screen's setup (the built-in one when
     * nothing is connected). [layoutId] null removes it. */
    fun setScreenRule(scope: com.shelf.launcher.data.RuleScope, value: String, layoutId: String?, setup: com.shelf.launcher.data.ScreenSetup? = null) {
        val target = setup ?: currentScreen()?.second ?: com.shelf.launcher.data.ScreenSetup.DUAL
        screenLayoutsStore.setRule(target, scope, value, layoutId)
        bumpScreenLayouts()
    }

    /** The next launch uses [layoutId], without saving it anywhere. */
    fun setNextLaunchLayout(layoutId: String?) {
        nextLaunchLayoutId = layoutId
    }

    fun bumpScreenLayouts() {
        _uiState.update { it.copy(screenLayoutsVersion = it.screenLayoutsVersion + 1) }
    }

    /**
     * Fills the screen the game isn't on, then starts the game — in that
     * order, because the controller goes to whatever started last.
     */
    private fun launchNow(game: Game) {
        val override = nextLaunchLayoutId
        nextLaunchLayoutId = null
        val plan = screenPlanFor(game)
        if (plan == null) {
            startGameNow(game, displayId = null, placementNote = null)
            return
        }
        val layout = override?.let { screenLayoutsStore.layout(it) } ?: plan.resolved.layout
        val app = getApplication<Application>()
        val main = android.view.Display.DEFAULT_DISPLAY
        if (layout.emulatorUsesBoth) {
            startGameNow(game, displayId = main, placementNote = null)
            return
        }
        val gameSide = layout.gameOn
        val otherSide = if (gameSide == com.shelf.launcher.data.ScreenSide.MAIN) {
            com.shelf.launcher.data.ScreenSide.SECOND
        } else {
            com.shelf.launcher.data.ScreenSide.MAIN
        }
        val gameDisplay = if (gameSide == com.shelf.launcher.data.ScreenSide.MAIN) main else plan.screen.displayId
        val otherDisplay = if (otherSide == com.shelf.launcher.data.ScreenSide.MAIN) main else plan.screen.displayId
        val otherName = plan.setup.sideName(otherSide)

        lastPlacementAt = System.currentTimeMillis()
        // Safe mode: no second-screen composition at all, so no Voltcap info page there either.
        val otherMode = if (com.shelf.launcher.CrashGuard.safeMode && layout.other == com.shelf.launcher.data.OtherScreen.INFO)
            com.shelf.launcher.data.OtherScreen.KEEP else layout.other
        val placed = when (otherMode) {
            com.shelf.launcher.data.OtherScreen.INFO -> com.shelf.launcher.ui.CompanionActivity.start(
                app, otherDisplay,
                // The game's second-screen page is Performance: show the live readouts there instead.
                if (effectiveProfile(game).secondScreenPage.value == com.shelf.launcher.data.SecondPage.PERFORMANCE)
                    com.shelf.launcher.ui.CompanionActivity.MODE_PERFORMANCE
                else com.shelf.launcher.ui.CompanionActivity.MODE_INFO,
                companionInfoFor(game, plan.setup.sideName(gameSide))
            )
            com.shelf.launcher.data.OtherScreen.BLACK -> com.shelf.launcher.ui.CompanionActivity.start(
                app, otherDisplay, com.shelf.launcher.ui.CompanionActivity.MODE_BLACK, null
            )
            com.shelf.launcher.data.OtherScreen.APP -> layout.appPackage?.let {
                com.shelf.launcher.data.ScreenPlacement.startApp(app, it, otherDisplay)
            } ?: false
            com.shelf.launcher.data.OtherScreen.KEEP -> true
        }
        val note = if (placed) null else {
            val what = if (layout.other == com.shelf.launcher.data.OtherScreen.APP) (layout.appLabel ?: "the app") else "Voltcap's info"
            "Couldn't put $what on the $otherName — Android refused it there."
        }
        if (otherMode == com.shelf.launcher.data.OtherScreen.KEEP || !placed) {
            startGameNow(game, gameDisplay, note)
        } else {
            // A moment for the other screen to come up, so the game is the
            // last thing started and gets the controller.
            viewModelScope.launch {
                kotlinx.coroutines.delay(350)
                lastPlacementAt = System.currentTimeMillis()
                startGameNow(game, gameDisplay, note)
            }
        }
    }

    private fun companionInfoFor(game: Game, gameScreen: String): com.shelf.launcher.ui.CompanionInfo {
        val state = _uiState.value
        val key = game.stableKey
        val progress = state.raProgress[key]
        return com.shelf.launcher.ui.CompanionInfo(
            title = game.name,
            platform = game.platform,
            coverUrl = state.artOverrides[key]?.cover ?: state.art[key]?.gridUrl,
            heroUrl = state.artOverrides[key]?.hero ?: state.art[key]?.heroUrl,
            playtimeMs = state.playtime[key] ?: 0L,
            raEarned = progress?.NumAchieved ?: 0,
            raPossible = progress?.NumPossibleAchievements ?: 0,
            startedAt = System.currentTimeMillis(),
            gameScreen = gameScreen,
            gameKey = key
        )
    }

    private fun startGameNow(game: Game, displayId: Int?, placementNote: String?) {
        val config = if (game.sourceType == SourceType.ROM) {
            val console = _uiState.value.emulatorConfigs[game.platform]
            // Update 326: the game's own emulator (Game settings › Emulator) wins over its console's; launched with that
            // emulator's own launch profile, like a console set to it.
            val own = gameEmulatorOverride(game)
            if (own != null && own != console?.packageName) {
                com.shelf.launcher.data.EmulatorConfig(platform = game.platform, mode = com.shelf.launcher.data.LaunchMode.EXPLICIT_INTENT, packageName = own)
            } else console
        } else {
            null
        }
        val (extras, skippedExtras) = com.shelf.launcher.data.LaunchExtras.validate(gameProfileStore.game(game.stableKey).launchExtras)
        // Performance overlay: started while Voltcap is still in front (Android refuses it from the background).
        // The package lets the sampler find the game's frames for FPS (only if the DUMP permission was granted).
        com.shelf.launcher.data.perf.PerfSampler.gamePackage =
            if (game.sourceType == SourceType.APP) game.packageName else emulatorPackageFor(game)
        // Low-power mode for Voltcap itself while the game runs (GameMode).
        com.shelf.launcher.data.GameMode.enter(getApplication())
        // (Update 185: Continue playing starts again at its newest game next time.)
        categoryFocus.remove("S:" + SpecialFilter.RECENT.name)
        // Performance tuner: the game's own profile (or the device-wide one) before the game starts.
        run {
            val tunePkg = com.shelf.launcher.data.perf.PerfSampler.gamePackage
            val app = getApplication<Application>()
            // ROM games: their console's profile applies when they have none of their own.
            val tunePlatform = game.platform.takeIf { game.sourceType == SourceType.ROM }
            val tuneConsole = tunePlatform?.let { com.shelf.launcher.data.ConsoleNames.display(it) }
            Thread {
                // 0.6.15: first the screen rate Voltcap raised for itself goes back, so the game (and its profile's
                // "put back afterwards") start from your own rate.
                runCatching { com.shelf.launcher.device.VoltcapRate.leave(app) }
                runCatching { com.shelf.launcher.device.tune.Tuner.onGameLaunch(app, game.stableKey, game.name, tunePkg, tunePlatform, tuneConsole) }
                // Volume per app: the saved volumes again, and this game's own one for its emulator / app.
                runCatching { com.shelf.launcher.device.audio.AppMixer.onGameLaunch(app, game.stableKey, game.name, tunePkg) }
            }.start()
        }
        // The resolver already falls back to the global "show during games" default, so a per-game/console Off wins.
        val wantsPerfOverlay = effectiveProfile(game).perfOverlay.value
        // The music player over the game rides in the same overlay service.
        val wantsMusicOverlay = com.shelf.launcher.data.perf.PerfSettings.config(getApplication<Application>()).musicDuringGames
        // RetroAchievements over the game: only for games with a set.
        val wantsRaOverlay = com.shelf.launcher.data.perf.PerfSettings.config(getApplication<Application>()).raDuringGames && game.raGameId != null
        if (wantsRaOverlay) followRaOverlay(game) else { raOverlayJob?.cancel(); com.shelf.launcher.data.RaOverlayData.clear() }
        overlayGame = game
        overlayDisplay = displayId
        if (wantsPerfOverlay || wantsMusicOverlay || wantsRaOverlay) {
            com.shelf.launcher.ui.PerfOverlayService.start(
                getApplication<Application>(), displayId, perf = wantsPerfOverlay, music = wantsMusicOverlay, ra = wantsRaOverlay
            )
        }
        val resumePkg = if (game.sourceType == SourceType.APP) game.packageName else emulatorPackageFor(game)
        val app0 = getApplication<Application>()
        // Running games: this very game still open in the background → back to the front, no restart.
        // Only when it's known to be open: a guess could bring back the emulator's own menu instead.
        val stillOpen = resumePkg != null && game.stableKey in com.shelf.launcher.data.RunningGames.confirmed &&
            com.shelf.launcher.data.RunningGames.packageFor(app0, game.stableKey) == resumePkg
        // Another game open in the same emulator: closed first, so the emulator starts the new one
        // cleanly (some ignore a new game while one is loaded).
        if (!stillOpen && resumePkg != null && game.sourceType == SourceType.ROM &&
            com.shelf.launcher.data.RunningGames.others(app0, game.stableKey).any { it.pkg == resumePkg }
        ) {
            runCatching { com.shelf.launcher.data.RunningGames.closePackage(app0, resumePkg) }
        }
        // (Update 328: on the game's own screen — see QuickResume.resume.)
        val resumed = (stillOpen && com.shelf.launcher.data.RunningGames.resume(app0, game.stableKey, displayId)) ||
            (_uiState.value.appearance.quickResume && game.sourceType == SourceType.ROM && resumePkg != null &&
                com.shelf.launcher.data.QuickResume.canResume(getApplication(), game.stableKey, resumePkg) &&
                com.shelf.launcher.data.QuickResume.resume(getApplication(), resumePkg, displayId))
        val outcome = if (resumed) LaunchOutcome.Launched else GameLauncher.launch(
            getApplication<Application>(),
            game,
            config,
            _uiState.value.gameNativeConfig,
            romRoot = game.romPath?.let { p -> _uiState.value.romRootPaths.firstOrNull { p.startsWith(it.trimEnd('/') + "/") } }
                ?: _uiState.value.romRootPath,
            displayId = displayId,
            extras = if (game.sourceType == SourceType.ROM) extras else emptyList()
        )
        // The game didn't start: take down whatever went on the other screen for it.
        if (outcome !is LaunchOutcome.Launched && displayId != null) {
            com.shelf.launcher.ui.CompanionActivity.closeAll()
            // (Update 256, #102: with the info page gone Android starts the other screen's own launcher there, and that
            // took the controller — B and the D-pad went to the other screen until a touch here. Found on the Thor with a
            // console that had no emulator yet. Voltcap is brought back to the front on its own screen once it has settled.)
            viewModelScope.launch {
                kotlinx.coroutines.delay(700)
                runCatching {
                    val app = getApplication<Application>()
                    // (Voltcap's own activity, singleTask: the running one comes forward, no second copy. Not the home
                    // intent — that counts as a Home press and opened Choose a category over the emulator picker.)
                    val i = android.content.Intent(app, com.shelf.launcher.MainActivity::class.java)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    val o = android.app.ActivityOptions.makeBasic().setLaunchDisplayId(android.view.Display.DEFAULT_DISPLAY)
                    app.startActivity(i, o.toBundle())
                }
            }
        }
        if (outcome !is LaunchOutcome.Launched) {
            com.shelf.launcher.ui.PerfOverlayService.stop(getApplication<Application>())
            com.shelf.launcher.data.perf.PerfSampler.gamePackage = null
            // (update 91) Nothing started: leave game mode (background work resumes) and undo the game's tuning / volume.
            com.shelf.launcher.data.GameMode.exit()
            overlayGame = null
            raOverlayJob?.cancel()
            raOverlayJob = null
            com.shelf.launcher.data.RaOverlayData.clear()
            val failedApp = getApplication<Application>()
            Thread {
                runCatching { com.shelf.launcher.device.tune.Tuner.onGameEnded(failedApp) }
                runCatching { com.shelf.launcher.device.audio.AppMixer.onGameEnded(failedApp) }
            }.start()
        }

        if (outcome is LaunchOutcome.Launched) {
            val now = System.currentTimeMillis()
            noteOwnLaunch(game.stableKey)
            com.shelf.launcher.data.QuickResume.noteLaunched(getApplication(), game.stableKey, resumePkg)
            com.shelf.launcher.data.RunningGames.noteLaunched(getApplication(), game.stableKey, resumePkg)
            recentlyPlayedStore.recordPlayed(game.stableKey, now)
            playtimeStore.startSession(game.stableKey, now)
            // Drive won't replace saves under this emulator while it may still be running (EmulatorGuard).
            com.shelf.launcher.data.EmulatorGuard.noteLaunched(
                getApplication(), if (game.sourceType == SourceType.APP) game.packageName else emulatorPackageFor(game)
            )
            // Voltcap is behind the game now; a WorkManager chain uploads its saves while it runs.
            com.shelf.launcher.data.SyncNotifier.appInForeground = false
            if (effectiveProfile(game).syncSaves.value) {
                com.shelf.launcher.data.SaveWatchWorker.startSession(getApplication(), game, now)
                // Update 222 (#103): saves go up seconds after the emulator writes them (SaveObserver).
                com.shelf.launcher.data.SaveObserver.start(getApplication(), game)
            } else com.shelf.launcher.data.SaveObserver.stop()
            if (_uiState.value.appearance.discordPresence) {
                val state = _uiState.value
                val cover = state.artOverrides[game.stableKey]?.cover ?: state.art[game.stableKey]?.gridUrl
                com.shelf.launcher.data.DiscordPresence.showPlaying(
                    com.shelf.launcher.ui.gameDisplayName(game.name),
                    when (game.platform) {
                        "App" -> "Android"
                        else -> game.platform
                    },
                    cover
                )
            }
            guideWatchGame = game
            if (game.raGameId != null) startUnlockWatch(game.raGameId)
            game.steamAppId?.let { startSteamWatch(it) }
            val keyBefore = _uiState.value.let { it.visibleGames.getOrNull(it.focusedIndex)?.stableKey }
            recombineGames() // re-sorts so this game moves to the front next time
            // The re-sort mustn't move the selection to a different game.
            if (keyBefore != null) _uiState.update { s ->
                val idx = s.visibleGames.indexOfFirst { it.stableKey == keyBefore }
                if (idx >= 0) s.copy(focusedIndex = idx) else s
            }
        }

        val message = when (outcome) {
            is LaunchOutcome.Launched -> listOfNotNull(
                placementNote,
                skippedExtras.takeIf { it.isNotEmpty() }?.let { "Skipped launch parameters: " + it.joinToString(", ") + "." }
            ).joinToString(" ").ifEmpty { null }
            is LaunchOutcome.NotConfigured ->
                "No emulator set up for ${game.platform} yet — pick one (Start › Emulator changes it later)."
            is LaunchOutcome.EmulatorNotFound ->
                "${outcome.packageName ?: "The emulator"} didn't accept the launch. Check the emulator set for " +
                    "${game.platform} (Start › Emulator), or try a different game path format there."
            is LaunchOutcome.Error ->
                "Couldn't launch ${game.name}: ${outcome.message}"
        }
        // (Update 225: a launch that didn't start is in the log too — for crash / launch reports.)
        if (outcome !is LaunchOutcome.Launched) android.util.Log.w("VoltcapLaunch", "${game.platform} · ${game.name}: $message")
        // (Update 256, #102: no emulator for the console yet — the emulator picker opens straight away, its installed
        // suggestions on top. It used to say "press Start on the game", and Start opened the game's options with the
        // emulator entry eight rows down.)
        if (outcome is LaunchOutcome.NotConfigured) needEmulatorFor.value = game
        _uiState.update { it.copy(statusMessage = message) }
    }

    /**
     * Update 256: a game that couldn't start for want of an emulator; the home screen opens the picker for its console,
     * then clears it. (Update 258: the game itself, so it starts once you've picked one.)
     */
    val needEmulatorFor = kotlinx.coroutines.flow.MutableStateFlow<Game?>(null)

    fun connect(apiKey: String) {
        if (apiKey.isBlank()) return
        SgdbAuth.apiKey = apiKey
        // (Update 144: a check of the saved key still waiting for a connection makes way.)
        sgdbRetryJob?.cancel()
        _uiState.update { it.copy(artState = ArtConnectionState.CONNECTING, statusMessage = "Testing connection…") }

        viewModelScope.launch {
            fetchAllArt(persistOnSuccess = true, savedKeyRestore = false, apiKeyToPersist = apiKey)
        }
    }

    /**
     * Shared by `connect()`, the silent SGDB-key restore path, and a
     * post-rescan art refresh. `savedKeyRestore` just changes the messaging
     * (no "Testing connection…" noise when it's not a fresh, explicit connect).
     */
    private suspend fun fetchAllArt(
        persistOnSuccess: Boolean,
        savedKeyRestore: Boolean,
        apiKeyToPersist: String? = null,
        attempt: Int = 0
    ) {
        // Verify the key on its own terms. This used to be inferred from
        // "did the first game return art", which meant an empty library
        // (i.e. before any ROM scan) made a perfectly valid key look broken
        // and silently prevented it from ever being saved.
        val check = repository.checkKey()
        // Update 144: the saved key, but no connection (the launcher often starts before Wi-Fi is up): the key is
        // kept as working — nothing says it isn't — and checked again a little later (1, 2, 4… up to 30 minutes
        // apart), which then fetches the missing art. It used to say the key "stopped working" and turn the
        // automatic art off until the next restart.
        if (savedKeyRestore && check == SteamGridDbRepository.KeyCheck.UNREACHABLE) {
            _uiState.update { it.copy(artState = ArtConnectionState.ON) }
            val wait = minOf(60_000L shl minOf(attempt, 5), 30L * 60_000L)
            sgdbRetryJob = viewModelScope.launch {
                kotlinx.coroutines.delay(wait)
                if (_uiState.value.artState == ArtConnectionState.ON && !SgdbAuth.apiKey.isNullOrBlank()) {
                    fetchAllArt(persistOnSuccess = false, savedKeyRestore = true, attempt = attempt + 1)
                }
            }
            return
        }
        if (check != SteamGridDbRepository.KeyCheck.OK) {
            _uiState.update {
                it.copy(
                    artState = ArtConnectionState.FAILED,
                    statusMessage = when {
                        savedKeyRestore -> "Saved SteamGridDB key stopped working — reconnect from Menu › Scrapers."
                        check == SteamGridDbRepository.KeyCheck.REJECTED ->
                            "SteamGridDB didn't accept the key — check it and try again."
                        else -> "Couldn't reach SteamGridDB — check the key and your connection."
                    }
                )
            }
            return
        }

        if (persistOnSuccess && apiKeyToPersist != null) {
            sgdbKeyStore.saveKey(apiKeyToPersist)
        }

        val games = _uiState.value.games
        _uiState.update {
            it.copy(
                artState = ArtConnectionState.ON,
                statusMessage = when {
                    // (Update 144: a later re-check after a start without a connection leaves what's shown alone.)
                    savedKeyRestore && attempt > 0 -> it.statusMessage
                    savedKeyRestore -> null
                    games.isEmpty() -> "Connected to SteamGridDB. Scan a ROM folder to start pulling art."
                    else -> null
                }
            )
        }

        scrapeMissingArt()
    }

    /**
     * Scrapes art for any game that doesn't have it yet, in the background.
     *
     * Runs as its own job rather than inline so it doesn't block connecting or
     * scanning, only touches games with nothing cached (so it's cheap to call
     * repeatedly), and writes each result to disk as it arrives — closing the
     * app mid-scrape keeps whatever finished instead of starting over.
     */
    fun scrapeMissingArt(force: Boolean = false, manual: Boolean = false) {
        val anySourceReady = !SgdbAuth.apiKey.isNullOrBlank() || tgdbKeyStore.loadKey() != null ||
            screenScraperStore.load() != null
        if (!anySourceReady) {
            if (manual) setStatusMessage("Connect an artwork source first (Settings › Accounts).")
            return
        }
        if (scrapeJob?.isActive == true) return // already running
        val fields = _uiState.value.scrapeOptions.art
        if (fields.isEmpty()) {
            if (_uiState.value.scrapeOptions.manuals) scrapeManuals(retry = manual || force)
            else if (manual) setStatusMessage("Tick at least one kind of artwork to download.")
            return
        }

        scrapeJob = viewModelScope.launch {
            // Once: art an older version matched to known non-game apps by name (Moonlight got
            // Moonlighter's) is dropped, so those apps show their own icon again.
            val cleanupPrefs = getApplication<Application>().getSharedPreferences("art_matching", android.content.Context.MODE_PRIVATE)
            if (!cleanupPrefs.getBoolean("app_cleanup_v1", false) && _uiState.value.games.isNotEmpty()) {
                val wrong = _uiState.value.games.filter { g ->
                    com.shelf.launcher.data.TitleMatch.skipScraping(g) && g.sgdbGameId == null && _uiState.value.art.containsKey(g.stableKey)
                }.map { it.stableKey }
                if (wrong.isNotEmpty()) {
                    withContext(Dispatchers.IO) { artCacheStore.remove(wrong) }
                    _uiState.update { it.copy(art = it.art - wrong.toSet()) }
                }
                cleanupPrefs.edit().putBoolean("app_cleanup_v1", true).apply()
            }
            // Update 119: when each game was last looked up automatically. A game the sources have nothing (or no wide
            // art and no logo) for used to be looked up again at every start, so the art "scraped again after a restart".
            val askedPrefs = getApplication<Application>().getSharedPreferences("art_asked", android.content.Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val askAgainAfter = 14L * 24 * 60 * 60 * 1000
            val pending = _uiState.value.games.filter { game ->
                if (_uiState.value.usesOwnIcon(game)) return@filter false
                if (com.shelf.launcher.data.TitleMatch.skipScraping(game) && game.sgdbGameId == null) return@filter false
                val cached = _uiState.value.art[game.stableKey]
                when {
                    // "Re-scrape all": every game, but only the ticked kinds change.
                    force -> true
                    // Pressed by hand: anything missing one of the ticked kinds.
                    manual -> cached.lacksAny(fields)
                    // Automatic runs stay cheap: missing entirely, or cached before wide capsules/logos were fetched —
                    // and not looked up in the last two weeks.
                    else -> (cached == null || (cached.wideGridUrl == null && cached.logoUrl == null)) &&
                        now - askedPrefs.getLong(game.stableKey, 0L) > askAgainAfter
                }
            }
            if (pending.isEmpty()) {
                _uiState.update { it.copy(scrapeDone = 0, scrapeTotal = 0) }
                if (manual) setStatusMessage("Nothing missing — every game already has the ticked artwork.")
                // Manuals still get their (first) pass.
                scrapeManuals(retry = manual || force)
                return@launch
            }

            // (Update 119: one job — each game's pictures are saved on the device right after they're found, so
            // scraping and saving run together with one progress; a "Save all" running already makes way.)
            artSaveJob?.cancel()
            _uiState.update { it.copy(scrapeDone = 0, scrapeTotal = pending.size, artSaveDone = 0, artSaveTotal = 0) }
            val saveAsWeGo = _uiState.value.artStorage.autoSave
            // Update 190: keeps going while Voltcap isn't on screen (ArtDownloadService keeps the process awake).
            val artApp = getApplication<Application>()
            val holdAwake = com.shelf.launcher.data.Features.artInBackground.value
            if (holdAwake) com.shelf.launcher.data.ArtDownloadService.start(artApp, pending.size)
            try {

            // New art reaches the screen in small batches (about twice a second) instead of one full
            // home-screen update per game: big libraries used to redraw everything thousands of times.
            val freshArt = LinkedHashMap<String, com.shelf.launcher.data.GameArt>()
            val asked = HashMap<String, Long>()
            var lastFlush = android.os.SystemClock.uptimeMillis()
            fun flushArt(done: Int) {
                val batch = HashMap(freshArt)
                freshArt.clear()
                lastFlush = android.os.SystemClock.uptimeMillis()
                _uiState.update { it.copy(art = if (batch.isEmpty()) it.art else it.art + batch, scrapeDone = done) }
                if (asked.isNotEmpty()) {
                    val e = askedPrefs.edit()
                    asked.forEach { (k, t) -> e.putLong(k, t) }
                    e.apply()
                    asked.clear()
                }
            }
            try {
                // Update 175: four games looked up at once instead of one after another (SteamGridDB, IGDB and RAWG
                // answer in parallel; ScreenScraper keeps its own limit inside its repository). Everything below runs
                // on the main thread between the lookups, so the batch maps need no locks.
                val lanes = kotlinx.coroutines.sync.Semaphore(ART_LANES)
                var done = 0
                kotlinx.coroutines.coroutineScope {
                    for (game in pending) {
                        lanes.acquire()
                        if (!isActive) { lanes.release(); break }
                        launch {
                            try {
                                if (com.shelf.launcher.data.GameMode.active && freshArt.isNotEmpty()) flushArt(done)
                                // (Update 190: only waits for the game when "Keep getting artwork in the background" is off.)
                                com.shelf.launcher.data.GameMode.awaitForArt()
                                val art = withContext(Dispatchers.IO) { fetchArtFromSelectedSource(game) }
                                asked[game.stableKey] = System.currentTimeMillis()
                                if (art != null) {
                                    val merged = (freshArt[game.stableKey] ?: _uiState.value.art[game.stableKey]).mergeArt(art, fields)
                                    artCacheStore.put(game.stableKey, merged)
                                    freshArt[game.stableKey] = merged
                                    if (saveAsWeGo) withContext(Dispatchers.IO) { saveGameArtNow(merged) }
                                }
                                done++
                                com.shelf.launcher.data.ArtDownloadService.progress(artApp, done, pending.size)
                                if (android.os.SystemClock.uptimeMillis() - lastFlush >= 450L || done == pending.size) flushArt(done)
                            } finally {
                                lanes.release()
                            }
                        }
                    }
                }
            } finally {
                if (freshArt.isNotEmpty() || asked.isNotEmpty()) flushArt(pending.size)
                _uiState.update { it.copy(scrapeDone = 0, scrapeTotal = 0) }
            }

            // What's left to save (backgrounds already looked up, scans, screenshots…) right after, with its own count.
            // (Update 190: it never ran — saveAllArt saw this scrape still running and stopped at once.)
            if (saveAsWeGo) saveAllArt(silent = true, afterScrape = true)
            autoSaveDetails()
            scrapeManuals(retry = manual || force)
            } finally {
                if (holdAwake) com.shelf.launcher.data.ArtDownloadService.stop(artApp)
            }
        }
    }

    /**
     * Finds which ROMs have a scanned manual on ScreenScraper, when Scraping › Manuals is on (the default). The automatic
     * pass asks once per game; pressing Scrape missing / Re-scrape all ([retry]) looks again for every game still without one.
     * Runs after the artwork so the row fills first; paced for ScreenScraper's limits.
     * Update 202: only the manual's address is noted (ManualLinks) — the PDF downloads the first time it's opened. Games
     * already looked up for their art, scans or clips had theirs noted then, so they cost no request here.
     */
    fun scrapeManuals(retry: Boolean = false) {
        if (!_uiState.value.scrapeOptions.manuals) return
        val creds = screenScraperStore.load() ?: return
        if (manualScrapeJob?.isActive == true) return
        val app = getApplication<Application>()
        val L = com.shelf.launcher.data.ManualLinks
        manualScrapeJob = viewModelScope.launch(Dispatchers.IO) {
            val tried = manualPrefs.getStringSet("tried", emptySet()).orEmpty().toMutableSet()
            val pending = _uiState.value.games.filter { g ->
                val k = g.stableKey
                g.sourceType == SourceType.ROM && L.url(k) == null && (retry || (!L.known(k) && k !in tried)) &&
                    com.shelf.launcher.data.Manuals.local(app, g) == null
            }
            if (pending.isEmpty()) {
                if (retry) setStatusMessage("Every game with a manual on ScreenScraper already has it listed.")
                return@launch
            }
            var got = 0
            pending.forEachIndexed { i, game ->
                if (!isActive || !_uiState.value.scrapeOptions.manuals) return@launch
                com.shelf.launcher.data.GameMode.awaitNotPlaying()
                val url = runCatching { screenScraperRepository.manualUrl(creds, game) }.getOrNull()
                if (url != null) got++
                tried += game.stableKey
                // Saved now and then, so closing Voltcap mid-way doesn't ask for those games again.
                if (i % 10 == 9 || i == pending.lastIndex) manualPrefs.edit().putStringSet("tried", HashSet(tried)).apply()
                kotlinx.coroutines.delay(400)
            }
            if (retry || got > 0) setStatusMessage(
                "Found $got manual" + (if (got == 1) "" else "s") + " on ScreenScraper — each downloads the first time you open it."
            )
        }
    }

    private var manualBulkJob: Job? = null

    /**
     * Update 204: Settings › Artwork › Scraping › Manuals — "Find manuals" ([download] false) asks ScreenScraper about
     * every ROM whose manual isn't known yet (whether Scraping › Manuals is ticked or not: you asked for it), paced for
     * ScreenScraper's limits; "Download all manuals" ([download] true) does that and then downloads every manual found
     * that isn't on the device yet (beside the ROM, picked by you or downloaded before), into the saved-art store as
     * opening one does. Progress in ManualLinks.bulk; ManualLinks.stopAll stops it. Waits while a game is running.
     */
    /**
     * Update 320: with Manuals › Download all ahead, every manual found that isn't on the device yet is downloaded in the
     * background (a while after start, and when the setting is switched on) — quietly, as [findAllManuals] with download.
     */
    fun downloadManualsAhead() {
        if (com.shelf.launcher.data.Features.manualKeep.value != com.shelf.launcher.data.Features.ManualKeep.AHEAD) return
        if (screenScraperStore.load() == null) return
        findAllManuals(download = true, quiet = true)
    }

    fun findAllManuals(download: Boolean, quiet: Boolean = false) {
        if (manualBulkJob?.isActive == true) return
        val creds = screenScraperStore.load()
        if (creds == null) {
            setStatusMessage("Manuals come from ScreenScraper: connect it in Settings › Accounts › ScreenScraper first.")
            return
        }
        val app = getApplication<Application>()
        val L = com.shelf.launcher.data.ManualLinks
        // (The automatic pass waits for this one: both ask ScreenScraper about the same games.)
        manualScrapeJob?.cancel()
        manualBulkJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val roms = _uiState.value.games.filter { it.sourceType == SourceType.ROM }
                val ask = roms.filter { g -> L.url(g.stableKey) == null && com.shelf.launcher.data.Manuals.local(app, g) == null }
                var found = 0
                ask.forEachIndexed { i, game ->
                    if (!isActive) return@launch
                    L.bulk = com.shelf.launcher.data.ManualLinks.Bulk(false, i, ask.size, found)
                    com.shelf.launcher.data.GameMode.awaitNotPlaying()
                    val url = runCatching { screenScraperRepository.manualUrl(creds, game) }.getOrNull()
                    if (url != null) found++
                    kotlinx.coroutines.delay(400)
                }
                val tried = manualPrefs.getStringSet("tried", emptySet()).orEmpty().toMutableSet()
                tried += ask.map { it.stableKey }
                manualPrefs.edit().putStringSet("tried", HashSet(tried)).apply()
                if (!download) {
                    setStatusMessage(
                        if (ask.isEmpty()) "Every game's manual was looked for already: ${L.foundCount()} found on ScreenScraper."
                        else "Found $found more manual" + (if (found == 1) "" else "s") + " on ScreenScraper (${L.foundCount()} in all) — each downloads the first time you open it."
                    )
                    return@launch
                }
                val get = roms.filter { g -> L.url(g.stableKey) != null && runCatching { L.fileOnDevice(app, g) }.getOrNull() == null }
                var saved = 0
                var failed = 0
                get.forEachIndexed { i, game ->
                    if (!isActive) return@launch
                    L.bulk = com.shelf.launcher.data.ManualLinks.Bulk(true, i, get.size, saved)
                    com.shelf.launcher.data.GameMode.awaitNotPlaying()
                    val raw = L.url(game.stableKey) ?: return@forEachIndexed
                    com.shelf.launcher.data.ArtFiles.init(app)
                    val file = com.shelf.launcher.data.ArtFiles.downloadManual(
                        raw, screenScraperRepository.withLogin(raw, creds), onProgress = { _, _ -> }, cancelled = { !isActive }
                    )
                    if (file != null) saved++ else failed++
                    kotlinx.coroutines.delay(250)
                }
                if (saved > 0) L.version++
                if (!quiet) setStatusMessage(
                    when {
                        get.isEmpty() -> "Every manual found (${L.foundCount()}) is on this device already."
                        failed == 0 -> "Downloaded $saved manual" + (if (saved == 1) "" else "s") + "."
                        else -> "Downloaded $saved manual" + (if (saved == 1) "" else "s") + "; $failed couldn't be (ScreenScraper may be busy, or today's quota is used up) — try again later."
                    }
                )
            } finally {
                L.bulk = null
            }
        }
    }

    /** Fetches and saves every game's details in the background, when that's
     * switched on and a details source is ready. Quiet: no status messages. */
    private fun autoSaveDetails() {
        if (_uiState.value.artStorage.autoSaveDetails && _uiState.value.metadataReady) {
            scrapeMissingMetadata(silent = true)
        }
    }

    // ---- Artwork saved on the device ----

    /** Every artwork address the library uses, most visible first: covers
     * (so the dock is complete soonest), then wide cards, backgrounds, logos;
     * update 114: then the box / disc / spine scans and screenshots already looked up, and the SteamGridDB logos. */
    private fun artUrlsToSave(): List<String> {
        val state = _uiState.value
        val pairs = state.games.map { state.artOverrides[it.stableKey] to state.art[it.stableKey] }
        val covers = pairs.mapNotNull { (o, a) -> o?.cover ?: a?.gridUrl }
        val wides = pairs.mapNotNull { (o, a) -> o?.wide ?: a?.wideGridUrl }
        val heroes = pairs.mapNotNull { (o, a) -> o?.hero ?: a?.heroUrl }
        val logos = pairs.mapNotNull { (o, a) -> o?.logo ?: a?.logoUrl } +
            // RetroAchievements icons used as logos are saved too.
            (if (state.appearance.raLogos) state.games.mapNotNull { g -> g.raGameId?.let { state.raIcons[it] } } else emptyList()) +
            state.sgdbLogos.values.filter { it.isNotEmpty() }
        // (Update 205: the scans of the games asked for or shown this run, as before — see scansSeen.)
        val scans = state.physicalMedia.entries.filter { it.key in scansSeen }.flatMap { (_, list) -> drawnScans(list) }
        val shots = state.screenshots.values.flatten()
        // Your own images (content://) are already on the device.
        return (covers + wides + heroes + logos + scans + shots).filter { it.startsWith("http") }.distinct()
    }

    /**
     * Update 119: one game's newly found pictures saved on the device straight away (the scrape saves as it goes).
     * Pictures already saved, or with the store full, are skipped. Call off the main thread.
     */
    private fun saveGameArtNow(art: com.shelf.launcher.data.GameArt) {
        val files = com.shelf.launcher.data.ArtFiles
        val urls = listOfNotNull(art.gridUrl, art.wideGridUrl, art.heroUrl, art.logoUrl).filter { it.startsWith("http") }
        if (urls.isEmpty()) return
        files.wanted = files.wanted + urls
        urls.forEach { url -> if (!files.full() && !files.has(url)) files.download(url) }
    }

    /**
     * Downloads every image that isn't saved yet, one at a time, in the background. Update 89 (it ran at every start):
     * images the server refuses are left alone for a few days instead of being tried at every start, a run stops
     * when the device is offline, and it stops when the store is nearly full. "Save all now" ([silent] = false)
     * tries everything again.
     * Update 114: into the permanent store of plain files (ArtFiles), copying first whatever the old image cache
     * still holds (no download for those) — once a picture is saved it stays saved, so this no longer starts over
     * after an update or a restart.
     */
    @OptIn(ExperimentalCoilApi::class)
    fun saveAllArt(silent: Boolean = false, afterScrape: Boolean = false) {
        // (Update 206: said when it's already saving — Save all now seemed to do nothing.)
        if (artSaveJob?.isActive == true) {
            if (!silent) setStatusMessage("Already saving art: ${_uiState.value.artSaveDone} / ${_uiState.value.artSaveTotal}.")
            // (Update 208: asked again while saving — a scrape that just finished found new pictures the running save's
            // list doesn't have: it goes round once more when it's done, so they're saved too without asking.)
            artSaveAgain = true
            return
        }
        artSaveAgain = false
        // (Update 119: while the art is being scraped, it saves as it goes and calls this when it's done.)
        if (scrapeJob?.isActive == true && !afterScrape) {
            if (!silent) setStatusMessage("Saving together with the scrape running now.")
            return
        }
        val app = getApplication<Application>()
        val disk = Coil.imageLoader(app).diskCache
        if (!silent) ArtStorage.forgetFailed(app)

        // Update 190: keeps going while Voltcap isn't on screen (released when this is done).
        val holdAwake = com.shelf.launcher.data.Features.artInBackground.value
        if (holdAwake) com.shelf.launcher.data.ArtDownloadService.start(app, 0)
        artSaveJob = viewModelScope.launch {
          try {
            val urls = artUrlsToSave()
            com.shelf.launcher.data.ArtFiles.wanted = urls.toHashSet()
            val missing = withContext(Dispatchers.IO) {
                val failed = ArtStorage.recentlyFailed(app)
                urls.filter { url -> url !in failed && !com.shelf.launcher.data.ArtFiles.has(url) }
            }
            if (missing.isEmpty()) {
                if (!silent) setStatusMessage("All artwork is already saved on the device.")
                refreshArtStorageStats()
                return@launch
            }
            if (com.shelf.launcher.data.ArtFiles.full()) {
                if (!silent) setStatusMessage("The art store is full: raise its size in Settings › Metadata to save the rest.")
                refreshArtStorageStats()
                return@launch
            }

            _uiState.update { it.copy(artSaveDone = 0, artSaveTotal = missing.size) }
            var saved = 0
            var done = 0
            var offlineInARow = 0
            var stopped: String? = null
            var ssQuotaUsed = false
            val refused = ArrayList<String>()
            // Update 207: several pictures at once (it was one after another — 7,790 pictures took about two hours).
            // Two sets of workers, each taking the next address from its own queue: ScreenScraper's with as many workers
            // as the account may make requests at once (one for a free account, so its limit is never tripped), every
            // other site's with SAVE_LANES — so the other sites' pictures never wait behind ScreenScraper's. Everything
            // here runs on the main thread (viewModelScope) between the downloads, so the counts and queues need no locks.
            var shownAt = 0L
            val ssQueue = ArrayDeque<String>()
            val otherQueue = ArrayDeque<String>()
            missing.forEach { if (com.shelf.launcher.data.ArtFiles.isScreenScraper(it)) ssQueue.addLast(it) else otherQueue.addLast(it) }
            suspend fun saveOne(url: String) {
                try {
                    com.shelf.launcher.data.GameMode.awaitForArt()
                    if (com.shelf.launcher.data.ArtFiles.full()) {
                        stopped = "The art store is full: raise its size in Settings › Metadata to save the rest."
                        return
                    }
                    var wasRefused = false
                    var busy = 0
                    val ok = run {
                            withContext(Dispatchers.IO) {
                                // Already in the old image cache: copied over, no download.
                                val copied = runCatching {
                                    disk?.openSnapshot(url)?.use { snap -> com.shelf.launcher.data.ArtFiles.keep(url, snap.data.toFile()) }
                                }.getOrNull() == true
                                // (The cache's copy isn't needed any more: no picture is kept twice.)
                                if (copied) runCatching { disk?.remove(url) }
                                if (copied) return@withContext true
                                var got = com.shelf.launcher.data.ArtFiles.download(url, { wasRefused = true }, { code -> busy = code })
                                // Too many requests just now: a short pause, then once more.
                                if (!got && busy == 429) { kotlinx.coroutines.delay(4_000); busy = 0; got = com.shelf.launcher.data.ArtFiles.download(url, { wasRefused = true }, { code -> busy = code }) }
                                got
                            }
                    }
                    when {
                        ok -> { saved++; offlineInARow = 0 }
                        busy == 430 -> ssQuotaUsed = true // today's ScreenScraper quota is used up: the rest of its pictures wait
                        wasRefused -> { refused += url; offlineInARow = 0 } // gone, forbidden…: not again for a few days
                        busy != 0 -> offlineInARow = 0
                        ++offlineInARow >= 3 * SAVE_LANES -> stopped = "Saving art stopped: no connection."
                    }
                } finally {
                    done++
                    // (Update 207: the count shown at most about twice a second. Every saved picture made a new
                    // screen state — the whole home redrawn each time, thousands of times while saving — and
                    // scrolling through a big library went sluggish for as long as it ran.)
                    val now = android.os.SystemClock.uptimeMillis()
                    if (now - shownAt >= 600L || done >= missing.size) {
                        shownAt = now
                        _uiState.update { it.copy(artSaveDone = done) }
                        com.shelf.launcher.data.ArtDownloadService.progress(app, done, missing.size)
                    }
                }
            }
            kotlinx.coroutines.coroutineScope {
                val ssWorkers = screenScraperRepository.allowedThreads.coerceAtLeast(1)
                repeat(ssWorkers + SAVE_LANES) { n ->
                    val fromSs = n < ssWorkers
                    val queue = if (fromSs) ssQueue else otherQueue
                    launch {
                        while (isActive && stopped == null) {
                            if (fromSs && ssQuotaUsed) {
                                // Today's ScreenScraper quota is used up: the rest of its pictures wait for another day.
                                done += queue.size; queue.clear()
                                _uiState.update { it.copy(artSaveDone = done) }
                                break
                            }
                            val url = queue.removeFirstOrNull() ?: break
                            saveOne(url)
                        }
                    }
                }
            }
            if (ssQuotaUsed && stopped == null) stopped = "Saved $saved images. ScreenScraper's daily quota is used up: its pictures are saved tomorrow."
            ArtStorage.markFailed(app, refused)
            _uiState.update {
                it.copy(
                    artSaveDone = 0,
                    artSaveTotal = 0,
                    statusMessage = if (silent) it.statusMessage else stopped ?: "Saved $saved images on the device." +
                        (if (refused.isNotEmpty()) " ${refused.size} couldn't be downloaded." else "")
                )
            }
            refreshArtStorageStats()
          } finally {
            if (holdAwake) com.shelf.launcher.data.ArtDownloadService.stop(app)
          }
          // (Update 208: once more for what was found while this ran, when there's still room.)
          if (artSaveAgain && isActive && !com.shelf.launcher.data.ArtFiles.full()) {
              artSaveAgain = false
              val finishing = artSaveJob
              viewModelScope.launch { finishing?.join(); kotlinx.coroutines.delay(2_000); saveAllArt(silent = true) }
          }
        }
    }

    /** Update 208: saveAllArt was asked for while it ran: it goes round once more at the end. */
    @Volatile private var artSaveAgain = false

    private var spineJob: kotlinx.coroutines.Job? = null

    /**
     * Update 114 (Settings › Metadata › Scrape spines): every ROM's box scans — its spine, full box and disc — from
     * ScreenScraper, one game at a time in the background (waits while a game is running). [again]: also the games
     * asked recently that still have no spine of their own (ScreenScraper keeps adding scans).
     */
    fun scrapeAllSpines(again: Boolean) {
        if (spineJob?.isActive == true) return
        if (screenScraperStore.load() == null) {
            if (again) setStatusMessage("Spines come from ScreenScraper: connect it in Settings › Metadata first.")
            return
        }
        if (again) com.shelf.launcher.data.Features.spineRecheckAt.value = System.currentTimeMillis().toString()
        val label = com.shelf.launcher.ui.BoxTexture.LABEL
        spineJob = viewModelScope.launch {
            val pending = _uiState.value.games.filter { g ->
                g.sourceType == SourceType.ROM &&
                    _uiState.value.physicalMedia[g.stableKey]?.any { it.first == label } != true &&
                    physicalPrefs.getString(g.stableKey, null)?.contains(label) != true &&
                    (again || !physicalPrefs.getBoolean("spine_checked:${g.stableKey}", false))
            }
            if (pending.isEmpty()) {
                if (again) setStatusMessage("Every game already has its own spine, or ScreenScraper has none for it yet.")
                return@launch
            }
            com.shelf.launcher.data.SpineScrape.progress = 0 to pending.size
            com.shelf.launcher.data.ScanReport.start(pending.size)
            var found = 0
            try {
                for ((i, g) in pending.withIndex()) {
                    com.shelf.launcher.data.GameMode.awaitForArt()
                    // Its look-up runs on its own: wait for it (at most half a minute), then a short pause for the server.
                    scanOne(g, force = false)
                    if (_uiState.value.physicalMedia[g.stableKey]?.any { it.first == label } == true) found++
                    com.shelf.launcher.data.SpineScrape.progress = (i + 1) to pending.size
                    kotlinx.coroutines.delay(700)
                }
            } finally {
                com.shelf.launcher.data.SpineScrape.progress = null
                com.shelf.launcher.data.ScanReport.finish()
            }
            if (again || found > 0) setStatusMessage("Found $found spine" + (if (found == 1) "" else "s") + " on ScreenScraper.")
            // The new scans are saved on the device with the rest of the art.
            if (found > 0 && _uiState.value.artStorage.autoSave) saveAllArt(silent = true)
        }
    }

    /**
     * Update 141 (Settings › Metadata, and Game look for the real-media looks): every ROM still without any real-media
     * picture — its box scan, its disc / cartridge, its full box — is looked up on ScreenScraper again, one at a time in
     * the background; games that have them are left alone.
     */
    fun scrapeMissingRealArt() {
        if (spineJob?.isActive == true) return
        if (screenScraperStore.load() == null) {
            setStatusMessage("Real covers come from ScreenScraper: connect it in Settings › Accounts first.")
            return
        }
        val real = setOf("Box", "Disc / cartridge", com.shelf.launcher.ui.BoxTexture.LABEL)
        fun hasReal(key: String): Boolean =
            _uiState.value.physicalMedia[key]?.any { it.first in real } == true ||
                physicalPrefs.getString(key, null)?.split('\n')?.any { it.substringBefore('\t') in real } == true
        spineJob = viewModelScope.launch {
            val pending = _uiState.value.games.filter { g -> g.sourceType == SourceType.ROM && !hasReal(g.stableKey) }
            if (pending.isEmpty()) {
                setStatusMessage("Every game already has its real cover pictures.")
                return@launch
            }
            com.shelf.launcher.data.SpineScrape.progress = 0 to pending.size
            com.shelf.launcher.data.ScanReport.start(pending.size)
            var found = 0
            try {
                for ((i, g) in pending.withIndex()) {
                    com.shelf.launcher.data.GameMode.awaitForArt()
                    scanOne(g, force = true)
                    if (hasReal(g.stableKey)) found++
                    com.shelf.launcher.data.SpineScrape.progress = (i + 1) to pending.size
                    kotlinx.coroutines.delay(700)
                }
            } finally {
                com.shelf.launcher.data.SpineScrape.progress = null
                com.shelf.launcher.data.ScanReport.finish()
            }
            setStatusMessage("Found real covers for $found of ${pending.size} game" + (if (pending.size == 1) "" else "s") + " on ScreenScraper.")
            if (found > 0 && _uiState.value.artStorage.autoSave) saveAllArt(silent = true)
        }
    }

    fun stopSpineScrape() {
        spineJob?.cancel()
        com.shelf.launcher.data.SpineScrape.progress = null
        if (com.shelf.launcher.data.ScanReport.running) com.shelf.launcher.data.ScanReport.finish()
    }

    /**
     * Update 205: one game's real media looked up (and waited for, at most half a minute) and counted kind by kind in
     * Settings › Scraping › Real media, with how the look-up went.
     */
    private suspend fun scanOne(g: Game, force: Boolean) {
        val R = com.shelf.launcher.data.ScanReport
        R.game = gameDisplayNameForReport(g)
        R.console = com.shelf.launcher.data.ConsoleNames.display(g.platform)
        screenScraperRepository.lookupOutcome.remove(g.stableKey)
        loadPhysicalMedia(g, needSpine = true, force = force)
        var waited = 0
        while (g.stableKey in physicalLoading && waited < 30_000) { kotlinx.coroutines.delay(200); waited += 200 }
        R.tally(_uiState.value.physicalMedia[g.stableKey], screenScraperRepository.lookupOutcome[g.stableKey])
    }

    private fun gameDisplayNameForReport(g: Game): String = runCatching { com.shelf.launcher.ui.gameDisplayName(g.name) }.getOrDefault(g.name)

    /** The real-media kinds the looks draw (the full box scan and the 3D box only where a cut needs them). */
    private fun drawnScans(list: List<Pair<String, String>>): List<String> {
        val have = list.map { it.first }.toSet()
        return list.filter { (label, _) ->
            when (label) {
                com.shelf.launcher.ui.BoxTexture.LABEL -> !("Spine" in have && "Back of the box" in have && "Box" in have)
                "Box (3D)" -> !("Spine" in have && "Box" in have)
                else -> true
            }
        }.map { it.second }
    }

    /**
     * Update 205 (Settings › Scraping › Real media › Count): per kind, how many games have it and how many of those
     * pictures are saved on the device — a picture not saved loads over the network when it's first shown.
     */
    fun countRealMedia() {
        viewModelScope.launch {
            val st = _uiState.value
            val kinds = com.shelf.launcher.data.ScanReport.KINDS.map { it.first }.toSet()
            val lib = withContext(Dispatchers.IO) {
                val roms = st.games.filter { it.sourceType == SourceType.ROM }
                val have = HashMap<String, Int>(); val saved = HashMap<String, Int>()
                var scanned = 0
                for (g in roms) {
                    val list = st.physicalMedia[g.stableKey] ?: continue
                    if (list.any { it.first in kinds }) scanned++
                    for ((label, url) in list) if (label in kinds) {
                        have[label] = (have[label] ?: 0) + 1
                        if (runCatching { com.shelf.launcher.data.ArtFiles.has(url) }.getOrDefault(false)) saved[label] = (saved[label] ?: 0) + 1
                    }
                }
                com.shelf.launcher.data.ScanReport.Library(roms.size, scanned, have, saved)
            }
            com.shelf.launcher.data.ScanReport.library = lib
        }
    }

    /** Update 205 (Settings › Scraping › Real media › Save them all on the device): every game's drawn real media saved now. */
    fun saveAllRealMedia() {
        scansSeen += _uiState.value.games.filter { it.sourceType == SourceType.ROM }.map { it.stableKey }
        // (A save already running has its list made before: it starts again with every game's real media in it.)
        if (artSaveJob?.isActive == true) { artSaveJob?.cancel(); artSaveJob = null }
        saveAllArt(silent = false)
    }

    /**
     * Update 147: everything a game can have, for every game missing it — artwork (the ticked kinds), details (the
     * ticked ones), then one pass over the games for their real cases / discs / spines, screenshots and video clips.
     * One step after the other (never two look-ups at once on the same server), waiting while a game is being played.
     */
    private var everythingJob: Job? = null

    fun scrapeEverything(parts: Set<com.shelf.launcher.data.EverythingScrape.Part>) {
        if (everythingJob?.isActive == true) return
        if (parts.isEmpty()) { setStatusMessage("Tick at least one thing to fetch."); return }
        val E = com.shelf.launcher.data.EverythingScrape
        everythingJob = viewModelScope.launch {
            try {
                // Update 175: artwork and details are fetched together (they were one after the other), one count for both.
                val art = com.shelf.launcher.data.EverythingScrape.Part.ART in parts
                val details = com.shelf.launcher.data.EverythingScrape.Part.DETAILS in parts
                if (art || details) {
                    E.stage = when { art && details -> "Artwork & details"; art -> "Artwork"; else -> "Details" }
                    E.progress = null
                    if (art) scrapeMissingArt(manual = true)
                    if (details) scrapeMissingMetadata(silent = true)
                    // (Update 149: the count shown here as it goes.)
                    while (scrapeJob?.isActive == true || metadataScrapeJob?.isActive == true) {
                        val st = _uiState.value
                        val done = st.scrapeDone + st.metadataScrapeDone
                        val total = st.scrapeTotal + st.metadataScrapeTotal
                        E.progress = if (total > 0) done to total else null
                        kotlinx.coroutines.delay(500)
                    }
                }
                val creds = screenScraperStore.load()
                val scans = com.shelf.launcher.data.EverythingScrape.Part.SCANS in parts && creds != null
                val shots = com.shelf.launcher.data.EverythingScrape.Part.SHOTS in parts
                val clips = com.shelf.launcher.data.EverythingScrape.Part.VIDEOS in parts && creds != null
                val real = setOf("Box", "Disc / cartridge", com.shelf.launcher.ui.BoxTexture.LABEL)
                fun hasReal(key: String): Boolean =
                    _uiState.value.physicalMedia[key]?.any { it.first in real } == true ||
                        physicalPrefs.getString(key, null)?.split('\n')?.any { it.substringBefore('\t') in real } == true
                fun needsScans(g: Game) = scans && g.sourceType == SourceType.ROM && !hasReal(g.stableKey)
                fun needsShots(g: Game) = shots && !_uiState.value.screenshots.containsKey(g.stableKey) && screenshotPrefs.getString(g.stableKey, null) == null
                fun needsClip(g: Game) = clips && g.sourceType == SourceType.ROM && videoUrlStore.get(g.stableKey) == null
                val pending = _uiState.value.games.filter { needsScans(it) || needsShots(it) || needsClip(it) }
                if (pending.isNotEmpty()) {
                    E.stage = "Cases, screenshots & clips"
                    E.progress = 0 to pending.size
                    // (Update 205: the real media counted kind by kind as they come in — Settings › Scraping › Real media.)
                    val scanCount = pending.count { needsScans(it) }
                    if (scanCount > 0) com.shelf.launcher.data.ScanReport.start(scanCount)
                    // Update 175: three games at once (was one, with half a second between games). ScreenScraper
                    // keeps its own limit; screenshots from the other sources come in alongside.
                    val lanes = kotlinx.coroutines.sync.Semaphore(MEDIA_LANES)
                    var done = 0
                    kotlinx.coroutines.coroutineScope { for (g in pending) {
                        lanes.acquire()
                        if (!isActive) { lanes.release(); break }
                        launch { try {
                        com.shelf.launcher.data.GameMode.awaitNotPlaying()
                        if (needsScans(g)) scanOne(g, force = true)
                        if (needsShots(g)) {
                            loadMedia(g)
                            var waited = 0
                            while (g.stableKey in screenshotsLoading && waited < 30_000) { kotlinx.coroutines.delay(200); waited += 200 }
                        }
                        if (needsClip(g) && creds != null) {
                            val url = withContext(Dispatchers.IO) { runCatching { screenScraperRepository.videoUrl(creds, g) }.getOrNull() }
                            videoUrlStore.put(g.stableKey, url)
                            if (url != null) _uiState.update { it.copy(videoUrls = it.videoUrls + (g.stableKey to url)) }
                            if (url != null && com.shelf.launcher.data.Features.clipsOffline.value) saveClipLater(g.stableKey, url)
                        }
                        done++
                        E.progress = done to pending.size
                        } finally { lanes.release() } }
                    } }
                    if (com.shelf.launcher.data.ScanReport.running) com.shelf.launcher.data.ScanReport.finish()
                    if (_uiState.value.artStorage.autoSave) saveAllArt(silent = true)
                }
                val noSs = creds == null && (com.shelf.launcher.data.EverythingScrape.Part.SCANS in parts || com.shelf.launcher.data.EverythingScrape.Part.VIDEOS in parts)
                setStatusMessage(
                    "Everything fetched that was missing." +
                        if (noSs) " Real cases and video clips need ScreenScraper (Settings › Accounts)." else ""
                )
            } finally {
                E.stage = null
                E.progress = null
            }
        }
    }

    fun stopEverything() {
        everythingJob?.cancel()
        scrapeJob?.cancel()
        metadataScrapeJob?.cancel()
        com.shelf.launcher.data.EverythingScrape.stage = null
        com.shelf.launcher.data.EverythingScrape.progress = null
    }

    fun cancelArtSave() {
        artSaveJob?.cancel()
        _uiState.update { it.copy(artSaveDone = 0, artSaveTotal = 0) }
        refreshArtStorageStats()
    }

    init {
        // (Update 318: the RetroAchievements login decrypted now, off the main thread — its first read took over 5 s on the
        // Flip 2 and froze the game window that asked for it.)
        viewModelScope.launch(Dispatchers.IO) { raCredentialsStore.warm() }
        // (Update 321: each game's own spine fit.)
        com.shelf.launcher.data.SpineFits.init(application)
        com.shelf.launcher.data.IconStyles.init(application)
        preloadPhysicalMedia()
        com.shelf.launcher.data.ScanReport.count = { countRealMedia() }
        com.shelf.launcher.data.ScanReport.saveAll = { saveAllRealMedia() }
        com.shelf.launcher.data.SpineScrape.run = { again -> scrapeAllSpines(again) }
        com.shelf.launcher.data.SpineScrape.runMissingReal = { scrapeMissingRealArt() }
        // Update 204: Settings › Scraping › Manuals: Find manuals / Download all manuals.
        com.shelf.launcher.data.ManualLinks.runAll = { download -> findAllManuals(download) }
        com.shelf.launcher.data.ManualLinks.stopAll = { manualBulkJob?.cancel() }
        // Update 117: the names and consoles that go up with this device's play sessions (for the other devices' recaps).
        var infoCache: Pair<List<Game>, Map<String, Game>>? = null
        // Update 182: the games here, so playtime synced from another handheld lands on the right one.
        com.shelf.launcher.data.CloudSync.libraryKeys = { _uiState.value.games.map { it.stableKey } }
        com.shelf.launcher.data.CloudSync.gameInfo = { key ->
            val gs = _uiState.value.games
            val c = infoCache?.takeIf { it.first === gs } ?: (gs to gs.associateBy { it.stableKey }).also { infoCache = it }
            c.second[key]?.let { it.name to it.platform }
        }
        com.shelf.launcher.data.SpineScrape.stop = { stopSpineScrape() }
        com.shelf.launcher.data.EverythingScrape.run = { parts -> scrapeEverything(parts) }
        com.shelf.launcher.data.EverythingScrape.stop = { stopEverything() }
        com.shelf.launcher.data.EverythingScrape.saveClips = { saveAllClips() }
    }

    /** How much space saved art uses, and how many images are still missing. */
    @OptIn(ExperimentalCoilApi::class)
    fun refreshArtStorageStats() {
        viewModelScope.launch {
            val (used, missing) = withContext(Dispatchers.IO) {
                val failed = ArtStorage.recentlyFailed(getApplication())
                // (Update 114: the permanent store of files — what really stays on the device.)
                com.shelf.launcher.data.ArtFiles.usedBytes() to artUrlsToSave().count { url -> url !in failed && !com.shelf.launcher.data.ArtFiles.has(url) }
            }
            _uiState.update { it.copy(artStorageUsedBytes = used, artStorageMissing = missing) }
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    fun clearSavedArt() {
        artSaveJob?.cancel()
        videoUrlStore.clear()
        screenshotPrefs.edit().clear().apply()
        _uiState.update { it.copy(screenshots = emptyMap()) }
        val loader = Coil.imageLoader(getApplication())
        ArtStorage.forgetFailed(getApplication())
        viewModelScope.launch(Dispatchers.IO) {
            loader.diskCache?.clear()
            loader.memoryCache?.clear()
            com.shelf.launcher.data.ArtFiles.clear()
            _uiState.update {
                it.copy(artSaveDone = 0, artSaveTotal = 0, statusMessage = "Saved artwork cleared.")
            }
            refreshArtStorageStats()
        }
    }

    fun setArtStorage(settings: ArtStorageSettings) {
        val previous = _uiState.value.artStorage
        val sizeChanged = settings.maxMegabytes != previous.maxMegabytes
        artStorageStore.save(settings)
        // (Update 114: the saved art's own limit applies at once.)
        com.shelf.launcher.data.ArtFiles.maxBytes = settings.maxBytes
        _uiState.update {
            it.copy(
                artStorage = settings,
                statusMessage = if (sizeChanged) "New storage size set." else it.statusMessage
            )
        }
        // Turning automatic saving on starts it straight away. (Update 206: and so does more room — saving had stopped at
        // the old size's 90 %, and nothing started again when a bigger size was picked.)
        if (settings.autoSave && (!previous.autoSave || settings.maxMegabytes > previous.maxMegabytes || settings.unlimited && !previous.unlimited)) saveAllArt(silent = true)
    }

    fun cancelScrape() {
        scrapeJob?.cancel()
        _uiState.update { it.copy(scrapeDone = 0, scrapeTotal = 0) }
    }

    /** Maps the files a full sync moved (or failed on) to [com.shelf.launcher.data.SyncStatusStore] entries. Bounded: skipped above 2000 games. */
    private suspend fun applyFullSyncStatus(r: com.shelf.launcher.data.CloudSync.FullSyncResult) {
        val games = _uiState.value.games
        if (games.isEmpty() || games.size > 2000) return
        val app = getApplication<Application>()
        withContext(Dispatchers.IO) {
            runCatching {
                val store = com.shelf.launcher.data.SyncStatusStore
                val now = System.currentTimeMillis()
                val conflicted = com.shelf.launcher.data.CloudSync.gamesTouchedBy(r.conflicts, games)
                conflicted.forEach { k ->
                    store.update(app, k) { it.copy(state = com.shelf.launcher.data.SyncState.CONFLICT, message = "Conflict — open Voltcap to choose", updatedAt = now) }
                }
                val counts = com.shelf.launcher.data.CloudSync.fileCountsTouchedBy(r.changes, games)
                val moved = counts.keys - conflicted
                val downloaded = com.shelf.launcher.data.CloudSync.gamesTouchedBy(r.downloads, games)
                moved.forEach { k ->
                    val n = counts[k] ?: 0
                    store.update(app, k) {
                        it.copy(
                            state = com.shelf.launcher.data.SyncState.SYNCED, message = null, updatedAt = now, lastSuccessAt = now,
                            lastDirection = if (k in downloaded) com.shelf.launcher.data.SyncDirection.DOWNLOAD else com.shelf.launcher.data.SyncDirection.UPLOAD,
                            fileCount = if (n > 0) n else it.fileCount
                        )
                    }
                }
                if (r.offline || r.needsSignIn || !r.ok) {
                    val all = store.all.value
                    games.forEach { g ->
                        if (all[g.stableKey]?.state == com.shelf.launcher.data.SyncState.PENDING) {
                            store.update(app, g.stableKey) {
                                if (r.needsSignIn) it.copy(state = com.shelf.launcher.data.SyncState.NEEDS_SIGN_IN, message = "Sign in to Google Drive again", updatedAt = now)
                                else if (r.offline) it.copy(state = com.shelf.launcher.data.SyncState.OFFLINE, message = "Offline — will retry automatically", updatedAt = now)
                                else it.copy(state = com.shelf.launcher.data.SyncState.ERROR, message = "Sync failed — try again", updatedAt = now)
                            }
                        }
                    }
                }
            }
        }
    }

    // The init block runs LAST, after every property of this class exists.
    // Kotlin runs property initializers and init blocks in declaration order, so
    // an init placed in the middle ran before later properties (e.g. scanMutex,
    // guide state, receivers) were initialised: startup could hit a null Mutex
    // (crash on the Retroid Pocket Classic) or later initializers could reset
    // values init had already set. Keep this block at the end of the class.
    /**
     * Last played on every device (Drive, see LastPlayedSync): rarely, off the main thread; games played on
     * another device move to the front here when they come back newer.
     */
    private fun syncLastPlayed(afterGame: Boolean) {
        if (!com.shelf.launcher.data.LastPlayedSync.due(afterGame)) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.delay(if (afterGame) 6_000L else 10_000L)
            val games = currentRomGames + currentAppGames + currentSteamGames
            if (games.isEmpty()) return@launch
            val pulled = runCatching {
                com.shelf.launcher.data.LastPlayedSync.sync(getApplication(), games, recentlyPlayedStore, afterGame)
            }.getOrDefault(0)
            if (pulled > 0) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { recombineGames() }
        }
    }

    init {
        // 0.6.15: sorting by score at start: the missing Metacritic scores come in from RAWG (a little later, once settled).
        viewModelScope.launch {
            kotlinx.coroutines.delay(20_000)
            val st = _uiState.value
            val m = com.shelf.launcher.data.SortMode.METACRITIC; val c = com.shelf.launcher.data.SortMode.CRITIC
            if (st.appearance.sortMode == m || st.appearance.sortMode == c || st.categorySort.values.any { it == m || it == c } ||
                com.shelf.launcher.data.Features.metacriticChip.value) fillMetacriticScores()
        }
        // 0.6.17: the Metacritic badge turned on: the missing scores come in from RAWG too.
        viewModelScope.launch {
            androidx.compose.runtime.snapshotFlow { com.shelf.launcher.data.Features.metacriticChip.state.value }
                .drop(1)
                .collect { on -> if (on) fillMetacriticScores() }
        }
        // 0.6.15: Metacritic scores from your file (Sort › Metacritic score): read once, and the lists sorted again when they change.
        com.shelf.launcher.data.MetacriticFile.onChange = { _uiState.update { it.copy(scoresVersion = it.scoresVersion + 1) } }
        com.shelf.launcher.data.MetacriticFile.rawgConnected.value = rawgKeyStore.load() != null
        run {
            val app = getApplication<Application>()
            Thread { runCatching { com.shelf.launcher.data.MetacriticFile.load(app) } }.start()
        }
        // Last played from other devices, once a little after start.
        syncLastPlayed(afterGame = false)
        // Favourite-theme rotation: on start, and a check every minute for "after a set time".
        viewModelScope.launch {
            kotlinx.coroutines.delay(800)
            com.shelf.launcher.data.ThemeRotator.onAppStart(getApplication(), _uiState.value.appTheme)?.let { setTheme(it) }
            while (true) {
                kotlinx.coroutines.delay(60_000)
                com.shelf.launcher.data.ThemeRotator.onTick(getApplication(), _uiState.value.appTheme)?.let { setTheme(it) }
            }
        }
        // Keeps the 80 % charge limit honest (AYN): pauses charging at 80 %, resumes below 77 %.
        com.shelf.launcher.device.ChargeLimiter.start(getApplication())
        com.shelf.launcher.device.tune.Tuner.start(getApplication())
        com.shelf.launcher.device.LidPause.install(getApplication())
        // Voltcap's screens follow changes made in Android's shade and in Voltcap's own tiles.
        com.shelf.launcher.device.SystemSync.start(getApplication())
        // A shortcut added from another app (DroidDeck's "Add to home screen"): into the library at once.
        viewModelScope.launch {
            var first = true
            com.shelf.launcher.data.PinnedShortcuts.version.collect { if (first) first = false else recombineGames() }
        }
        // Volume per app: hold the saved volumes again (the helper is gone after a restart).
        run {
            val app = getApplication<Application>()
            Thread { runCatching { com.shelf.launcher.device.audio.AppMixer.ensure(app) } }.start()
        }
        com.shelf.launcher.ui.CompanionBridge.vm = java.lang.ref.WeakReference(this)
        com.shelf.launcher.ui.SteamAccountState.init(getApplication())
        // The Steam profile picture (Settings › Accounts › Steam › Profile picture): saved one now, a fresh one soon.
        com.shelf.launcher.data.SteamAchievements.loadAvatar(getApplication())
        viewModelScope.launch {
            kotlinx.coroutines.delay(20_000L)
            if (com.shelf.launcher.data.Features.avatarSource.value == com.shelf.launcher.data.Features.Avatar.STEAM) {
                com.shelf.launcher.data.SteamAchievements.refreshAvatar(getApplication())
            }
        }
        // Games left open in the background (they glow on the grid until they're closed).
        com.shelf.launcher.data.RunningGames.init(getApplication())
        refreshRunning()
        // Import from ES-DE / Pegasus / Daijisho (Settings › Library): pick up what it added.
        viewModelScope.launch {
            com.shelf.launcher.data.FrontendImport.imported.collect {
                _uiState.update { st -> st.copy(favorites = favoritesStore.loadAll()) }
                refreshCollections()
                recombineGames()
            }
        }
        // Google Drive sync (Settings › Sync): once shortly after start, and
        // whatever it merged in (favourites, playtime…) shows up right away.
        com.shelf.launcher.data.CloudSync.load(application)
        // Per-game sync status for the badge (cheap prefs read, off the main thread).
        viewModelScope.launch(Dispatchers.IO) { runCatching { com.shelf.launcher.data.SyncStatusStore.load(application) } }
        // Background sync every few hours (Settings › Sync), set up again at each start.
        if (!com.shelf.launcher.CrashGuard.safeMode) runCatching { com.shelf.launcher.data.CloudSyncWorker.schedule(application) }
        // Update 149: the automatic Drive backup's schedule.
        if (!com.shelf.launcher.CrashGuard.safeMode) runCatching { com.shelf.launcher.data.AutoBackupWorker.schedule(application) }
        runCatching { com.shelf.launcher.data.RecapWorker.schedule(application, appearanceStore.load().weeklyRecap) }
        // Every RetroAchievements icon found before, straight away — loading
        // them one by one on focus flashed the SteamGridDB logo first.
        _uiState.update { st ->
            st.copy(raIcons = raIconPrefs.all.mapNotNull { (k, v) ->
                val id = k.toIntOrNull() ?: return@mapNotNull null
                (v as? String)?.let { id to it }
            }.toMap() + st.raIcons,
                // (Update 113: the SteamGridDB logos found for the big logos, likewise.)
                sgdbLogos = sgdbLogoPrefs.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap() + st.sgdbLogos)
        }
        viewModelScope.launch {
            kotlinx.coroutines.delay(20_000)
            fetchMissingRaIcons()
        }
        viewModelScope.launch {
            com.shelf.launcher.data.CloudSync.launcherDataChanged.collect { reloadSyncedData() }
        }
        // Full/periodic syncs: turn the changed files into per-game status (scoped syncs keep their own).
        viewModelScope.launch {
            var seen = com.shelf.launcher.data.CloudSync.lastFullSync.value.serial
            com.shelf.launcher.data.CloudSync.lastFullSync.collect { r ->
                if (r.serial == seen) return@collect
                seen = r.serial
                applyFullSyncStatus(r)
            }
        }
        viewModelScope.launch {
            kotlinx.coroutines.delay(5_000)
            if (com.shelf.launcher.CrashGuard.safeMode) return@launch // safe mode: no background sync
            com.shelf.launcher.data.CloudSync.syncInBackground(application)
        }
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { runCatching { metadataCacheStore.loadAll() }.getOrDefault(emptyMap()) }
            // Anything fetched meanwhile is newer than what was saved.
            _uiState.update { it.copy(metadata = saved + it.metadata) }
            // Genre categories are built from these details.
            if (_uiState.value.appearance.genreCollections) refreshCollections()
            savedMetadataLoaded.complete(Unit)
            // Details an older version matched to known non-game apps by name (Moonlight got Moonlighter's)
            // are dropped, once the library is known.
            runCatching {
                _uiState.first { it.games.isNotEmpty() }
                val wrong = _uiState.value.games.filter { g ->
                    com.shelf.launcher.data.TitleMatch.skipScraping(g) && _uiState.value.metadata.containsKey(g.stableKey)
                }.map { it.stableKey }
                if (wrong.isNotEmpty()) {
                    withContext(Dispatchers.IO) { metadataCacheStore.remove(wrong) }
                    _uiState.update { it.copy(metadata = it.metadata - wrong.toSet()) }
                }
            }
        }
        restoreSavedRaData()
        pendingFocusKey = restoreCtx?.focusedKey ?: categoryStore.load().lastFocusedKey
        if (restoreCtx != null) viewModelScope.launch {
            // Slow storage: wait a while for the saved game to appear before falling back.
            kotlinx.coroutines.delay(15_000)
            finishRestore()
        }
        // Before any image loads: every image goes to the permanent on-device
        // store from now on (see ArtStorage).
        ArtStorage.install(application, artStorageStore.load().maxBytes)
        // Update 114: which pictures are your games' (saved for good the first time they load), kept up to date as
        // the library, its art and the looked-up scans and screenshots change.
        viewModelScope.launch {
            _uiState.map { listOf(it.games, it.art, it.artOverrides, it.physicalMedia, it.screenshots, it.raIcons, it.sgdbLogos, it.appearance.raLogos) }
                .distinctUntilChanged { a, b -> a.indices.all { i -> a[i] === b[i] } }
                .collectLatest {
                    kotlinx.coroutines.delay(1_000)
                    com.shelf.launcher.data.ArtFiles.wanted = withContext(Dispatchers.Default) { artUrlsToSave().toHashSet() }
                }
        }
        // Once the library has settled, quietly download any art not saved yet.
        viewModelScope.launch {
            kotlinx.coroutines.delay(15_000)
            if (_uiState.value.artStorage.autoSave) saveAllArt(silent = true)
            if (_uiState.value.artStorage.autoSaveDetails && _uiState.value.metadataReady) {
                scrapeMissingMetadata(silent = true)
            }
            // Update 114: and every game's spine, when that's switched on.
            if (com.shelf.launcher.data.Features.spineScrape.value) {
                kotlinx.coroutines.delay(30_000)
                scrapeAllSpines(again = false)
            }
        }

        sgdbKeyStore.loadKey()?.let { savedKey ->
            SgdbAuth.apiKey = savedKey
            _uiState.update { it.copy(artState = ArtConnectionState.CONNECTING) }
            viewModelScope.launch {
                // Network + many list updates: wait until the start-up animation has handed over.
                com.shelf.launcher.ui.StartupGate.awaitIntro()
                fetchAllArt(persistOnSuccess = false, savedKeyRestore = true)
            }
        }

        syncRaAccounts()
        raCredentialsStore.load()?.let { creds ->
            _uiState.update { it.copy(raState = RaConnectionState.CONNECTING, raUsername = creds.username) }
            val gen = raGen
            viewModelScope.launch {
                com.shelf.launcher.ui.StartupGate.awaitIntro()
                if (gen != raGen) return@launch // another account took over while the intro played
                raRestoreJob?.join() // saved data first, so it can't overwrite what's fetched
                fetchAllRaProgress(creds, persistOnSuccess = false, savedCredsRestore = true)
                // Pick up anything not yet linked, quietly.
                autoMatchAchievements(silent = true)
            }
        }

        // Installed apps need no runtime permission beyond the manifest
        // <queries> block, so this is safe to always run at startup.
        scanApps(silent = true)
        registerPackageWatch()

        // Last scan's games straight away: right after the device boots,
        // Voltcap (as home screen) starts before internal storage can be
        // read, and the library used to come up empty until a manual rescan.
        // Read on a background thread (a big library's list takes a moment to parse) while the intro
        // plays, instead of holding up the first frame. A scan that finishes first wins.
        val romCacheLoad = viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) { loadRomCache() }
            if (!romScanApplied && currentRomGames.isEmpty() && cached.isNotEmpty()) {
                currentRomGames = cached
                recombineGames()
                runCatching { android.util.Log.i("Voltcap library", "shown ${android.os.SystemClock.elapsedRealtime() - android.os.Process.getStartElapsedRealtime()} ms after start") }
            }
        }

        // ROM folder access does need a permission grant — only auto-scan if
        // we already have it from a previous session.
        if (hasStoragePermission()) {
            scanLibrary(_uiState.value.romRootPath, silent = true)
        }

        // Steam games are manually added (see AddSteamGameDialog), so just
        // load whatever's already been saved.
        currentSteamGames = steamGamesStore.loadAll().map { it.toGame() }
        // Still reading the saved library: it shows everything at once when it's in (no half row first).
        if (romCacheLoad.isCompleted) recombineGames()
    }
}


private suspend inline fun <T> kotlinx.coroutines.sync.Semaphore.withPermitCompat(block: () -> T): T {
    acquire()
    try {
        return block()
    } finally {
        release()
    }
}
