package com.shelf.launcher.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.animation.Crossfade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.shelf.launcher.data.SecondArtType
import com.shelf.launcher.data.SecondMusicLayout
import com.shelf.launcher.data.Screenshots
import androidx.compose.runtime.getValue
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.GameMetadata
import com.shelf.launcher.data.NowPlaying
import com.shelf.launcher.data.PlaytimeStore
import com.shelf.launcher.data.RaAward
import com.shelf.launcher.data.RaBadges
import com.shelf.launcher.data.RaAchievementDetail
import com.shelf.launcher.data.RaGameProgress
import com.shelf.launcher.data.RaRecentAchievement
import com.shelf.launcher.data.SecondPage
import com.shelf.launcher.ui.theme.ShelfTheme

/** Everything the second screen's home shows for the game you're on. */
class SecondHomeGame(
    val game: Game?,
    val coverUrl: String?,
    val heroUrl: String?,
    /** The wide capsule / background art, when there is one. */
    val wideUrl: String?,
    val playtimeMs: Long?,
    val award: RaAward?,
    val progress: RaGameProgress?,
    val metadata: GameMetadata?,
    val metadataLoading: Boolean,
    val videoUrl: String?,
    /** This game's recent unlocks (last day or so). */
    val recentUnlocks: List<RaRecentAchievement>,
    /** The game's full achievement list (empty until loaded / not linked). */
    val achievements: List<RaAchievementDetail> = emptyList(),
    /** Box / case picture (ScreenScraper or RetroAchievements), when known. */
    val caseUrl: String? = null,
    /** Cartridge / disc picture, when known. */
    val discUrl: String? = null,
    /** Just the disc / cartridge label (for the drawn cartridge / disc), when known. */
    val labelUrl: String? = null,
    /** The flat front box scan (for the 3D box), when known. */
    val box2dUrl: String? = null,
    /** The game's scraped screenshots (the Art page's "Screenshots", with your own). */
    val shots: List<String> = emptyList(),
    /** Update 189: the game's logo (the "Game art" look's middle). */
    val logoUrl: String? = null,
    /** Update 198: the game's case to open on the Details page (Settings › Screens: off → null, the flat case as before). */
    val inspect: Any? = null
)

/** Settings › Appearance › Second screen, as the home screen needs them. */
class SecondScreenOptions(
    artType: SecondArtType = SecondArtType.HERO,
    val artBlur: Int = 50,
    val playerOverArt: Boolean = false,
    val musicLayout: SecondMusicLayout = SecondMusicLayout.FULL,
    /** The music-reactive scene behind the "Visualizer" layout. */
    val visualizerMotif: com.shelf.launcher.ui.theme.ThemeMotif = com.shelf.launcher.ui.theme.ThemeMotif.BLOOM,
    val musicBlurArt: Boolean = true,
    val musicBlur: Int = 60,
    val musicLyrics: Boolean = false,
    val screenshotFolders: Set<String> = emptySet(),
    /** Only the current page's name at the top, with arrows to the others. */
    val pageTitleOnly: Boolean = false,
    /** Visualizer music layout: the album cover card over the visualizer too. */
    val visualizerArt: Boolean = false,
    /** Details page: the case first (tap to open it), else the cartridge / disc first. */
    val detailsCaseFirst: Boolean = true
) {
    /** The Art page shows the hero or your screenshots only (older choices — cover, box, disc… — show the hero). */
    val artType: SecondArtType = when (artType) {
        SecondArtType.SCREENSHOTS, SecondArtType.SLIDESHOW -> SecondArtType.SCREENSHOTS
        else -> SecondArtType.HERO
    }
}

/**
 * The second screen while you're in Voltcap (no game running): a page of your
 * choice for the game you're on — its art, its video clip, its details, its
 * achievements, or the music player — over its blurred background, and a
 * dock along the bottom: five apps of yours, all apps, and a cog that opens
 * Settings › Screens on the main screen. Touch only; the controller keeps
 * driving the main screen.
 */
@Composable
fun SecondScreenHome(
    info: SecondHomeGame,
    page: SecondPage,
    onPage: (SecondPage) -> Unit,
    nowPlaying: NowPlaying?,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    videoSound: Boolean,
    onNeedMetadata: (Game) -> Unit,
    onNeedVideo: (Game) -> Unit,
    showDock: Boolean,
    /** The docked apps, in order (only installed ones). */
    dockApps: List<Game>,
    allApps: List<Game>,
    onLaunchApp: (Game) -> Unit,
    /** The dock changed (add, move, remove): the new list of package names. */
    onDockChange: (List<String>) -> Unit,
    onOpenSettings: () -> Unit,
    options: SecondScreenOptions = SecondScreenOptions(),
    onToggleLyrics: () -> Unit = {},
    /** Spotify library entry / blur setting for the full music page (null = hidden). */
    onBrowseSpotify: (() -> Unit)? = null,
    onMusicBlurChange: ((Int) -> Unit)? = null,
    onNeedAchievements: (Game) -> Unit = {},
    onNeedPhysical: (Game) -> Unit = {},
    /** Walkthrough page content; null hides the "Guide" tab. */
    walkthroughContent: (@Composable (Game?) -> Unit)? = null,
    /** Performance page content; null hides the "Performance" tab. */
    performanceContent: (@Composable () -> Unit)? = null,
    /** The Widgets and Wall pages' content (null: those pages show "Not available yet."). */
    extraPage: (@Composable (SecondPage) -> Unit)? = null,
    /** Update 122: the Games page (console folders, SecondGamesPage.kt); null hides the "Games" tab. */
    gamesPage: (@Composable () -> Unit)? = null,
    /** The home's animated scene, when it plays on this screen (Animate only on the second screen). */
    sceneBehind: (@Composable () -> Unit)? = null,
    /**
     * The game's details window (A on a game, Settings › Screens › Game window on the second screen): shown as a
     * "Game" page in front of the others. Leaving it (arrows, swipe, back) calls [onCloseGameWindow].
     */
    gameWindow: (@Composable () -> Unit)? = null,
    onCloseGameWindow: () -> Unit = {},
    /** Music effects moved here from the main screen (corner visualizers), drawn over the pages. */
    fxOverlay: (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? = null,
    /** Room kept free at the right of the header row for something drawn over it (the in-game timer). */
    headerEndInset: Dp = 0.dp,
    /** Update 189: your picture and points for the "Game art" look's top-left pill (RetroAchievements). */
    avatarUrl: String? = null,
    raPoints: Int? = null,
    /**
     * Update 193: a Voltcap shortcut in the dock that isn't a page here (a switch, an Android panel): does it and returns
     * what to say in the note on this screen (null: nothing to say). Null: no Voltcap shortcuts in this dock.
     */
    onShortcut: ((String) -> String?)? = null
) {
    com.shelf.launcher.Breadcrumbs.mark("SecondScreenHome")
    // Without a Games page (the companion), a saved "Games" choice shows the Art page.
    val page = if (page == SecondPage.LIBRARY && gamesPage == null) SecondPage.ART else page
    var drawerOpen by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Game?>(null) }
    val game = info.game
    val dockPackages = dockApps.mapNotNull { it.packageName }

    // Details and clips are fetched only when their page is showing.
    LaunchedEffect(game?.stableKey, page) {
        if (game != null && page == SecondPage.DETAILS) { onNeedMetadata(game); onNeedPhysical(game) }
        if (game != null && page == SecondPage.VIDEO) onNeedVideo(game)
        if (game != null && page == SecondPage.ACHIEVEMENTS && game.raGameId != null) {
            // Scrolling through the row shouldn't fire a RetroAchievements request per game:
            // only the game you stop on is fetched (the effect restarts on each move).
            kotlinx.coroutines.delay(700)
            onNeedAchievements(game)
        }
    }
    LaunchedEffect(game?.stableKey, page, options.artType) {
        if (game != null && page == SecondPage.ART &&
            (options.artType == SecondArtType.CASE || options.artType == SecondArtType.DISC || options.artType == SecondArtType.SHOWCASE)
        ) onNeedPhysical(game)
        // "Cartridge + details" shows the story too.
        if (game != null && page == SecondPage.ART && options.artType == SecondArtType.SHOWCASE) {
            kotlinx.coroutines.delay(500)
            onNeedMetadata(game)
        }
    }

    // The page only changes when the user picks one (a tab, or Settings).
    // Music never replaces the artwork by itself. Closing the full-screen
    // player goes back to the page the user came from, or to the classic
    // card for this session; the saved page is never rewritten silently.
    var cameFrom by remember { mutableStateOf<SecondPage?>(null) }
    var musicClosed by remember { mutableStateOf(false) }
    // A tap on the player card opens the big music player over the page (cover, big buttons, playlists on a swipe / arrow).
    var bigPlayer by remember { mutableStateOf(false) }
    LaunchedEffect(nowPlaying == null) { if (nowPlaying == null) bigPlayer = false }
    LaunchedEffect(page) { if (page != SecondPage.MUSIC) musicClosed = false }
    val selectPage: (SecondPage) -> Unit = { p ->
        if (p == SecondPage.MUSIC && page != SecondPage.MUSIC) cameFrom = page
        if (p == SecondPage.MUSIC) musicClosed = false
        onPage(p)
    }

    // The Music page can take over the whole second screen (tabs and dock
    // hidden); "Close" goes back to the Art page.
    // Only when it's really playing: a paused or stale session shows the classic card (the Visualizer layout stays up when paused).
    val gameOpen = gameWindow != null
    // Update 200: the Lyrics stage stays up while paused and even with nothing playing at all (it then rests like the
    // dashboard it copies: clock, stats and the idle picture), so it doesn't wait for a playing session like the others.
    val stageMusic = !gameOpen && page == SecondPage.MUSIC && options.musicLayout == SecondMusicLayout.STAGE && !musicClosed
    val fullMusic = stageMusic || !gameOpen && page == SecondPage.MUSIC && options.musicLayout != SecondMusicLayout.CLASSIC &&
        nowPlaying != null && (nowPlaying.isPlaying || options.musicLayout == SecondMusicLayout.VISUALIZER) && !musicClosed
    // The full player is a page like the others now (inside the second screen, under its tabs), so you can
    // keep swiping through the pages — it used to take over the whole screen like a popup.
    run {
    // Swipes flip the pages (left = next, right = previous). The system back swipe on this screen does the
    // same (see SecondScreenGestures): on the AYN Thor it used to close Voltcap's second screen and show the
    // stock launcher's drawer.
    val visiblePages = SecondPage.values().filter {
        (it != SecondPage.LIBRARY || gamesPage != null) &&
            ((it != SecondPage.WALKTHROUGH || walkthroughContent != null) && (it != SecondPage.PERFORMANCE || performanceContent != null) &&
                (extraPage != null || (it != SecondPage.WIDGETS && it != SecondPage.WALL && it != SecondPage.MAGNIFIER)) &&
                (it != SecondPage.MAGNIFIER || SecondScreenReal.active) && it.available() || it == page)
    }
    val latestCloseGame by rememberUpdatedState(onCloseGameWindow)
    // Update 200: which way a step went ([0] = -1 / 1, [1] = the page it went to), so the slide follows the direction of
    // travel even when it wraps around (last → first slides in from the right). Tabs and the dock use the page order.
    val pageTravel = remember { IntArray(2) }
    val stepPage: (Int) -> Unit = { dir ->
        if (gameOpen) {
            // Out of the Game page: forward = the page you were on, back = the one before it.
            latestCloseGame()
            if (dir < 0) {
                val i = visiblePages.indexOf(page).coerceAtLeast(0)
                val prev = visiblePages[(i - 1 + visiblePages.size) % visiblePages.size]
                if (prev != page) selectPage(prev)
            }
        } else {
            val i = visiblePages.indexOf(page).coerceAtLeast(0)
            val next = visiblePages[(i + dir + visiblePages.size) % visiblePages.size]
            if (next != page) { pageTravel[0] = dir; pageTravel[1] = next.ordinal; selectPage(next) }
        }
    }
    val latestStep by rememberUpdatedState(stepPage)
    // Update 193: a Voltcap shortcut tapped in the dock: its page here, or done right away with a short note on this
    // screen (SecondDockShortcuts.kt). Apps open as before.
    var dockNote by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(dockNote) { if (dockNote != null) { kotlinx.coroutines.delay(2400); dockNote = null } }
    val launchFromDock: (Game) -> Unit = { app ->
        val id = app.packageName
        if (id != null && com.shelf.launcher.data.BuiltinShortcuts.isBuiltin(id)) {
            val p = SecondDockShortcuts.page(id)
            if (p != null) {
                if (p in visiblePages) { if (p != page) selectPage(p) }
                else dockNote = "The ${p.label} page is off: Settings › Screens › Second screen tools"
            } else dockNote = onShortcut?.invoke(id)
        } else onLaunchApp(app)
    }
    val latestOverlay by rememberUpdatedState({ drawerOpen || pickerOpen || menuFor != null })
    val latestCloseOverlay by rememberUpdatedState({ drawerOpen = false; pickerOpen = false; menuFor = null })
    androidx.compose.runtime.DisposableEffect(Unit) {
        val back: () -> Unit = {
            when {
                latestOverlay() -> latestCloseOverlay()
                else -> latestStep(-1)
            }
        }
        SecondScreenGestures.onBack = back
        onDispose { if (SecondScreenGestures.onBack === back) SecondScreenGestures.onBack = null }
    }
    val swipeDensity = androidx.compose.ui.platform.LocalDensity.current
    Box(
        Modifier.fillMaxSize().background(ShelfTheme.background).pointerInput(Unit) {
            val threshold = with(swipeDensity) { 72.dp.toPx() }
            var acc = 0f
            detectHorizontalDragGestures(
                onDragStart = { acc = 0f },
                onDragEnd = {
                    if (acc <= -threshold) latestStep(1) else if (acc >= threshold) latestStep(-1)
                    acc = 0f
                }
            ) { change, dx -> acc += dx; change.consume() }
        }
    ) {
        // Update 189: the "Game art" look — the game's art full screen, floating pills and dock (SecondShowcase.kt).
        val showcase = com.shelf.launcher.data.Features.secondLook.state.value == com.shelf.launcher.data.Features.SecondLook.SHOWCASE
        if (showcase) {
            ShowcaseBackdrop(info, sceneBehind)
        } else if (sceneBehind != null) {
            sceneBehind()
        } else if (info.heroUrl != null) {
            // A softer top end than before (64 dp): the art stays recognisable behind the pages.
            // Blurred once (update 121), not on every redraw of the pages over it.
            BakedBlurImage(info.heroUrl, blurRadiusFor(options.artBlur, 36f), Modifier.fillMaxSize(), alpha = 0.35f)
        }
        if (!showcase) Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, ShelfTheme.background.copy(alpha = 0.92f)))
            ).then(if (VoltUi.on) Modifier.voltAmbient(ShelfTheme.accent, ShelfTheme.colors.secondary) else Modifier)
        )

        // The page itself (both looks).
        // Update 200: drawn for a given page (and whether it's the game window), so the page sliding out keeps drawing
        // itself while the next one slides in (SecondPageSlide). The game window and the full music player are kept from
        // the last time they showed: they're worked out for the current page only, and the page going out needs them too.
        val keptGameWindow = remember { arrayOfNulls<(@Composable () -> Unit)>(1) }
        if (gameWindow != null) keptGameWindow[0] = gameWindow
        val keptFullMusic = remember { BooleanArray(1) }
        if (page == SecondPage.MUSIC && !gameOpen) keptFullMusic[0] = fullMusic
        val pageBody: @Composable (SecondPage, Boolean) -> Unit = { page, inGame ->
                val fullMusic = keptFullMusic[0]
                if (inGame) Box(Modifier.fillMaxSize().clip(uiRounded(18.dp))) { keptGameWindow[0]?.invoke() }
                else when (page) {
                    SecondPage.ART -> if (showcase) ShowcaseArtPage(
                        info, info.logoUrl,
                        if (nowPlaying != null && nowPlaying.isPlaying && options.playerOverArt) {
                            { MusicPlayer(nowPlaying, onPlayPause, onNext, onPrevious, onSeek, modifier = Modifier.tapOnly { bigPlayer = true }, artBackdrop = false) }
                        } else null
                    ) else ArtPage(info, nowPlaying, onPlayPause, onNext, onPrevious, onSeek, options) { bigPlayer = true }
                    SecondPage.VIDEO -> VideoPage(info, videoSound)
                    SecondPage.DETAILS -> CaseDetailsPage(info, options.detailsCaseFirst)
                    SecondPage.ACHIEVEMENTS -> AchievementsPage(info)
                    // Update 200: the Lyrics stage (LyricsStage.kt) — playing, paused, or resting with nothing playing.
                    SecondPage.MUSIC -> if (fullMusic && options.musicLayout == SecondMusicLayout.STAGE) {
                        LyricsStage(
                            nowPlaying = nowPlaying,
                            onPlayPause = onPlayPause,
                            onNext = onNext,
                            onPrevious = onPrevious,
                            onSeek = onSeek,
                            modifier = Modifier.fillMaxSize().clip(uiRounded(18.dp))
                        )
                    } else if (fullMusic && nowPlaying != null) {
                    FullMusicScreen(
                            modifier = Modifier.fillMaxSize().clip(uiRounded(18.dp)),
                        nowPlaying = nowPlaying,
                        blurArt = options.musicBlurArt,
                        blurPercent = options.musicBlur,
                        showLyrics = options.musicLyrics,
                        onToggleLyrics = onToggleLyrics,
                        onBrowseSpotify = onBrowseSpotify,
                        onBlurChange = onMusicBlurChange,
                        onPlayPause = onPlayPause,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onSeek = onSeek,
                        visualizer = if (options.musicLayout == SecondMusicLayout.VISUALIZER) options.visualizerMotif else null,
                        visualizerArt = options.visualizerArt,
                        onClose = {
                            val back = cameFrom
                            if (back != null && back != SecondPage.MUSIC) onPage(back) else musicClosed = true
                        }
                    )
                    } else MusicPage(nowPlaying, onPlayPause, onNext, onPrevious, onSeek) { bigPlayer = true }
                    SecondPage.LIBRARY -> if (gamesPage != null) gamesPage()
                        else ArtPage(info, nowPlaying, onPlayPause, onNext, onPrevious, onSeek, options) { bigPlayer = true }
                    SecondPage.WALKTHROUGH ->
                        if (walkthroughContent != null) walkthroughContent(game) else Placeholder("Not available yet.")
                    SecondPage.PERFORMANCE ->
                        if (performanceContent != null) performanceContent() else Placeholder("Not available yet.")
                    SecondPage.WIDGETS, SecondPage.WALL, SecondPage.MAGNIFIER ->
                        if (extraPage != null) extraPage(page) else Placeholder("Not available yet.")
                }
        }

        if (showcase) {
            // Update 189: the floating bars over the art; the page between them.
            val pageIndex = visiblePages.indexOf(page).coerceAtLeast(0)
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxSize().padding(
                        start = 12.dp, end = 12.dp, top = SHOWCASE_TOP,
                        bottom = if (showDock && !gameOpen) SHOWCASE_BOTTOM else 22.dp
                    )
                ) {
                    // Update 200: the pages slide (the panel slides with its page; Art has none).
                    SecondPageSlide(page, gameOpen, visiblePages, pageTravel) { p, inGame ->
                        if (p == SecondPage.ART && !inGame) pageBody(p, false) else ShowcasePanel { Box(Modifier.fillMaxSize().padding(8.dp)) { pageBody(p, inGame) } }
                    }
                }
                ShowcaseTopBar(
                    avatarUrl, raPoints, if (gameOpen) "Game" else page.label, onPageTap = { latestStep(1) }, endInset = headerEndInset,
                    modifier = Modifier.align(Alignment.TopStart)
                )
                // Update 195: ‹ › fixed at the screen's left and right edges, half-way down, to step through the pages
                // (swiping still works); reachable with the controller too.
                if (visiblePages.size > 1 || gameOpen) for (dir in intArrayOf(-1, 1)) {
                    Box(
                        Modifier.align(if (dir < 0) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 2.dp)
                            .size(width = 30.dp, height = 64.dp)
                            .padTarget(if (dir < 0) "sc-edge-prev" else "sc-edge-next") { latestStep(dir) }
                            .clip(RoundedCornerShape(15.dp)).background(Color.Black.copy(alpha = 0.42f))
                            .clickable { latestStep(dir) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (dir < 0) androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowLeft
                            else androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            if (dir < 0) "Previous page" else "Next page", tint = Color.White, modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!gameOpen) ShowcaseDots(visiblePages.size, pageIndex, Modifier.padding(bottom = 7.dp))
                    if (showDock && !gameOpen) ShowcaseDock(
                        apps = dockApps, maxApps = SECOND_DOCK_MAX,
                        onHome = { visiblePages.firstOrNull()?.let { if (it != page) selectPage(it) } },
                        onLaunch = launchFromDock,
                        onAllApps = { drawerOpen = true },
                        onSettings = onOpenSettings,
                        onAdd = { pickerOpen = true },
                        onMenu = { menuFor = it },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        } else Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
            // Volt look: the Voltcap mark, underlined tabs and the clock over a voltage line Volt sits on.
            Box(Modifier.fillMaxWidth().padding(end = headerEndInset)) {
            if (gameOpen) PageSwitcher(page, visiblePages.size + 1, latestStep) {
                Text("Game", color = ShelfTheme.accent, fontSize = 13.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(13.sp), maxFontSize = 13.sp), fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, softWrap = false)
            }
            else if (VoltUi.on) VoltSecondHeader(page, visiblePages, selectPage, nowPlaying, titleOnly = options.pageTitleOnly, onStep = latestStep)
            else if (options.pageTitleOnly) PageSwitcher(page, visiblePages.size, latestStep)
            // Update 122: a subtle arrow at each end of the tabs too, so it's clear the pages go on (swipe or tap).
            else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PageArrow(left = true, visible = visiblePages.size > 1) { latestStep(-1) }
                Box(Modifier.weight(1f)) {
                    PageTabs(
                        page, selectPage,
                        showWalkthrough = walkthroughContent != null,
                        showPerformance = performanceContent != null,
                        showExtra = extraPage != null,
                        showGames = gamesPage != null
                    )
                }
                PageArrow(left = false, visible = visiblePages.size > 1) { latestStep(1) }
            }
            }
            Spacer(Modifier.height(10.dp))
            // Update 200: the pages slide in the direction of travel instead of just appearing.
            Box(Modifier.fillMaxWidth().weight(1f)) { SecondPageSlide(page, gameOpen, visiblePages, pageTravel) { p, inGame -> pageBody(p, inGame) } }
            if (showDock && !gameOpen) {
                Spacer(Modifier.height(10.dp))
                AppDockBar(
                    apps = dockApps,
                    onLaunch = launchFromDock,
                    onAllApps = { drawerOpen = true },
                    onSettings = onOpenSettings,
                    onAdd = { pickerOpen = true },
                    onMenu = { menuFor = it }
                )
            }
        }

        if (fxOverlay != null && !gameOpen) fxOverlay()

        if (bigPlayer && nowPlaying != null && !gameOpen) {
            BigMusicPlayer(
                nowPlaying = nowPlaying,
                blurArt = options.musicBlurArt,
                blurPercent = options.musicBlur,
                showLyrics = options.musicLyrics,
                onToggleLyrics = onToggleLyrics,
                onBrowseSpotify = onBrowseSpotify,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onSeek = onSeek,
                onClose = { bigPlayer = false },
                modifier = Modifier.clip(uiRounded(18.dp))
            )
        }

        // (Update 195: the drawer, the picker and the dock menu on their own controller layer, like a folder's popup.)
        if (drawerOpen) androidx.compose.runtime.CompositionLocalProvider(LocalPadLayer provides 1) {
            SecondScreenDrawer(
                apps = allApps,
                onLaunch = { drawerOpen = false; onLaunchApp(it) },
                onClose = { drawerOpen = false }
            )
        }

        if (pickerOpen) androidx.compose.runtime.CompositionLocalProvider(LocalPadLayer provides 1) {
            SecondScreenAppPicker(
                // (Update 193: Voltcap's own shortcuts too, first.)
                apps = allApps.filter { it.packageName !in dockPackages } +
                    (if (onShortcut != null) SecondDockShortcuts.all().filter { it.packageName !in dockPackages } else emptyList()),
                onPick = { app ->
                    pickerOpen = false
                    app.packageName?.let { onDockChange((dockPackages + it).distinct().take(SECOND_DOCK_MAX)) }
                },
                onClose = { pickerOpen = false }
            )
        }

        // Update 193: what a dock shortcut just did.
        dockNote?.let { note ->
            Box(Modifier.fillMaxSize().padding(top = 58.dp), contentAlignment = Alignment.TopCenter) {
                Text(
                    note, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 520.dp).clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.72f)).padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        menuFor?.let { target ->
            val pkg = target.packageName
            val index = dockPackages.indexOf(pkg)
            if (pkg != null && index >= 0) androidx.compose.runtime.CompositionLocalProvider(LocalPadLayer provides 1) {
                DockAppMenu(
                    title = target.name,
                    canMoveLeft = index > 0,
                    canMoveRight = index < dockPackages.size - 1,
                    onMove = { delta ->
                        val list = dockPackages.toMutableList()
                        list.add(index + delta, list.removeAt(index))
                        onDockChange(list)
                        menuFor = null
                    },
                    onRemove = { onDockChange(dockPackages - pkg); menuFor = null },
                    onClose = { menuFor = null }
                )
            }
        }
    }
    }
}

/**
 * The second screen's back gesture (an edge swipe on the AYN Thor's lower screen). The Presentation hands
 * it here instead of closing; the second-screen home flips to the previous page (or closes its drawer).
 */
internal object SecondScreenGestures {
    @Volatile var onBack: (() -> Unit)? = null
}

/** Most apps the second screen's dock holds. */
const val SECOND_DOCK_MAX = 8

/**
 * "Only the current page" at the top: its name in the middle and a subtle arrow on each side to the
 * previous / next page (they wrap around), so it's clear there's more without listing every tab. Swipes
 * still flip the pages too.
 */
@Composable
internal fun PageSwitcher(page: SecondPage, pageCount: Int, onStep: (Int) -> Unit, modifier: Modifier = Modifier, title: @Composable () -> Unit = {
    Text(page.label, color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(13.sp), maxFontSize = 13.sp), fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, softWrap = false)
}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PageArrow(left = true, visible = pageCount > 1) { onStep(-1) }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { title() }
        PageArrow(left = false, visible = pageCount > 1) { onStep(1) }
    }
}

/** A quiet chevron (no fill, muted): tap it to step a page. */
@Composable
private fun PageArrow(left: Boolean, visible: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).then(if (visible) Modifier.padTarget(if (left) "page-prev" else "page-next") { onClick() } else Modifier)
            .clip(RoundedCornerShape(50)).then(if (visible) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (visible) {
            androidx.compose.material3.Icon(
                if (left) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (left) "Previous page" else "Next page",
                tint = ShelfTheme.colors.textPrimary.copy(alpha = 0.4f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/** The page chips along the top, same pills as everywhere else in Voltcap. */
@Composable
private fun PageTabs(
    page: SecondPage,
    onPage: (SecondPage) -> Unit,
    showWalkthrough: Boolean,
    showPerformance: Boolean,
    showExtra: Boolean = false,
    showGames: Boolean = false
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SecondPage.values().filter {
            (it != SecondPage.LIBRARY || showGames) &&
                ((it != SecondPage.WALKTHROUGH || showWalkthrough) && (it != SecondPage.PERFORMANCE || showPerformance) &&
                    (showExtra || (it != SecondPage.WIDGETS && it != SecondPage.WALL && it != SecondPage.MAGNIFIER)) &&
                    (it != SecondPage.MAGNIFIER || SecondScreenReal.active) && it.available() || it == page)
        }.forEach { p ->
            val selected = p == page
            Box(
                Modifier
                    .padTarget("tab-" + p.name) { onPage(p) }
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) ShelfTheme.accent else Color.White.copy(alpha = 0.08f))
                    .clickable { onPage(p) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    p.label,
                    color = if (selected) onAccentColor() else ShelfTheme.colors.textPrimary,
                    fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp), fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, softWrap = false
                )
            }
        }
    }
}

@Composable
private fun NoGame() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Voltcap", color = ShelfTheme.colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Placeholder(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = ShelfTheme.textMuted, fontSize = 13.sp)
    }
}

/**
 * Big art with the title over it. Which picture (hero, wide background,
 * cover, your screenshots, or a slideshow of all of them) is a setting. The
 * music player only shows here when "Player over the artwork" is on — it
 * otherwise lives on the Music page.
 */
@Composable
private fun ArtPage(
    info: SecondHomeGame,
    nowPlaying: NowPlaying?,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    options: SecondScreenOptions,
    onOpenPlayer: () -> Unit = {}
) {
    com.shelf.launcher.Breadcrumbs.mark("ArtPage")
    val game = info.game
    val wantsShots = options.artType == SecondArtType.SCREENSHOTS || options.artType == SecondArtType.SLIDESHOW
    // Your own emulator screenshots, only looked up when the setting needs them.
    val shots by produceState(emptyList<String>(), game?.stableKey, options.screenshotFolders, wantsShots) {
        value = if (game != null && wantsShots) {
            withContext(Dispatchers.IO) {
                runCatching { Screenshots.find(game, options.screenshotFolders).map { it.absolutePath } }
                    .getOrDefault(emptyList())
            }
        } else emptyList()
    }
    val pool: List<String> = when (options.artType) {
        SecondArtType.HERO -> listOfNotNull(info.heroUrl ?: info.wideUrl ?: info.coverUrl)
        SecondArtType.BACKGROUND -> listOfNotNull(info.wideUrl ?: info.heroUrl ?: info.coverUrl)
        SecondArtType.COVER -> listOfNotNull(info.coverUrl ?: info.heroUrl)
        SecondArtType.CASE -> listOfNotNull(info.caseUrl ?: info.coverUrl ?: info.heroUrl)
        SecondArtType.DISC -> listOfNotNull(info.discUrl ?: info.coverUrl ?: info.heroUrl)
        SecondArtType.SHOWCASE -> listOfNotNull(info.heroUrl ?: info.wideUrl ?: info.coverUrl)
        SecondArtType.SCREENSHOTS ->
            (shots + info.shots).distinct().ifEmpty { listOfNotNull(info.heroUrl ?: info.wideUrl ?: info.coverUrl) }
        SecondArtType.SLIDESHOW ->
            (listOfNotNull(info.heroUrl, info.wideUrl, info.coverUrl) + shots).distinct()
    }
    var slide by remember(game?.stableKey) { mutableStateOf(0) }
    LaunchedEffect(pool.size, options.artType, game?.stableKey) { slide = 0 }
    val slidesActive = rememberIsActive()
    LaunchedEffect(pool.size, options.artType, game?.stableKey, slidesActive) {
        if (!slidesActive) return@LaunchedEffect // paused while MainActivity is stopped / screen off
        if (pool.size > 1 && wantsShots) {
            while (true) {
                delay(8_000)
                slide = (slide + 1) % pool.size
            }
        }
    }
    val picture = pool.getOrNull(slide % maxOf(1, pool.size))

    if (game == null) {
        NoGame()
    } else if (options.artType == SecondArtType.SHOWCASE) {
        ShowcaseArt(info)
    } else {
        val stillOpen = game.stableKey in com.shelf.launcher.data.RunningGames.keys
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    // Still open in the background: the same green pulse as on the main grid.
                    .runningGlow(stillOpen, 18.dp)
                    .clip(uiRounded(18.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, if (VoltUi.on) ShelfTheme.accent.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f), uiRounded(18.dp))
            ) {
                // The game's own transition (Game settings) or a quick fade, a little slower here than on the home screen.
                val transitionStyle = com.shelf.launcher.data.GameLooks.transition(game.stableKey, com.shelf.launcher.data.HeroMotion.QUICK)
                val liteFx = LocalLiteEffects.current
                if (picture != null) {
                    val shell = if (options.artType == SecondArtType.DISC && mediaShellsOn) MediaShell.of(game.platform) else null
                    if (shell != null) PhysicalMedia(shell, info.labelUrl ?: info.coverUrl, Modifier.fillMaxSize().padding(18.dp), platform = game.platform, labelIsScan = info.labelUrl != null)
                    // Box / case: the console's real case (cover template / template pack), standing free — no letterboxed scan.
                    else if (options.artType == SecondArtType.CASE && caseMarker(game.platform) != null)
                        CaseArt(game.platform, info.coverUrl, info.caseUrl, Modifier.fillMaxSize().padding(14.dp), title = gameDisplayName(game.name), detail = 720)
                    else androidx.compose.animation.AnimatedContent(targetState = picture, transitionSpec = { gameTransition(transitionStyle, com.shelf.launcher.data.Features.transitionSpeed.value.ms * 2, liteFx) }, label = "secondArt") { url ->
                        // Screenshots are files on the device, everything else is a URL.
                        val model: Any = if (url.startsWith("/")) java.io.File(url) else url
                        val fit = options.artType == SecondArtType.CASE || options.artType == SecondArtType.DISC
                        AsyncImage(
                            model = model, contentDescription = game.name,
                            contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().pixelArtwork().padding(if (fit) 12.dp else 0.dp)
                        )
                    }
                } else if (game.sourceType == com.shelf.launcher.data.SourceType.APP) {
                    AppIconArt(game.packageName, wide = true, modifier = Modifier.fillMaxSize())
                }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))
                    )
                )
                // The name sits right in the bottom-left corner of the art (no cover thumbnail, no achievements:
                // those have their own page).
                Row(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth(0.8f).padding(start = 12.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            gameDisplayName(game.name), color = Color.White, fontSize = 20.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(20.sp), maxFontSize = 20.sp),
                            fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                        val bits = listOfNotNull(
                            game.platform,
                            info.playtimeMs?.takeIf { it > 0 }?.let { "Played ${PlaytimeStore.format(it)}" }
                        )
                        Text(bits.joinToString(" · "), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (stillOpen) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (game.stableKey in com.shelf.launcher.data.RunningGames.confirmed) "● Still open · A resumes · X closes"
                                else "● Probably still open · X closes",
                                color = RunningGlowColor, fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp),
                                fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.45f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
            if (nowPlaying != null && nowPlaying.isPlaying && options.playerOverArt) {
                Spacer(Modifier.height(8.dp))
                // The page behind is already the blurred art: the card itself stays sharp (blur over the
                // player as well was too much).
                MusicPlayer(nowPlaying, onPlayPause, onNext, onPrevious, onSeek, modifier = Modifier.tapOnly(onOpenPlayer), artBackdrop = false)
            }
        }
    }
}

@Composable
private fun VideoPage(info: SecondHomeGame, withSound: Boolean) {
    val game = info.game
    val url = info.videoUrl
    if (game == null) {
        NoGame()
    } else if (url == null) {
        Placeholder("No video clip for this game (clips come from ScreenScraper).")
    } else {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black)
        ) {
            androidx.compose.runtime.key(url) {
                VideoBackdrop(url, withSound = withSound, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun AchievementsPage(info: SecondHomeGame) {
    val game = info.game
    val possible = info.progress?.NumPossibleAchievements ?: 0
    if (game == null) {
        NoGame()
    } else if (game.raGameId == null) {
        Placeholder("Not linked to RetroAchievements (Start › Achievement matching).")
    } else {
        val earned = info.progress?.NumAchieved ?: 0
        val list = info.achievements
        if (VoltUi.on && list.isNotEmpty()) {
            VoltAchievementBoard(game, earned, possible, info.award?.label, list)
        } else
        Column(Modifier.fillMaxSize().then(if (list.isEmpty()) Modifier.verticalScroll(rememberScrollState()) else Modifier)) {
            Text(gameDisplayName(game.name), color = ShelfTheme.colors.textPrimary, fontSize = 18.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(18.sp), maxFontSize = 18.sp), fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            if (possible > 0) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$earned", color = ShelfTheme.accent, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text(" / $possible achievements", color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(14.sp), maxFontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).padding(bottom = 6.dp))
                    Spacer(Modifier.weight(1f))
                    info.award?.let {
                        Text(it.label.uppercase(), color = ShelfTheme.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    }
                }
                Box(
                    Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                ) {
                    Box(
                        Modifier.fillMaxWidth((earned.toFloat() / possible).coerceIn(0f, 1f))
                            .height(8.dp).background(ShelfTheme.accent)
                    )
                }
            } else {
                MutedNote("Progress shows once RetroAchievements has been checked for this game.")
            }
            Spacer(Modifier.height(14.dp))
            // Plain if / else, never an early `return@Column`: leaving an inline
            // composable early unbalances Compose's groups (the AYN Thor crash).
            if (list.isNotEmpty()) {
                AchievementList(list, Modifier.weight(1f).fillMaxWidth())
            } else {
                SectionLabel("Unlocked lately")
                if (info.recentUnlocks.isEmpty()) {
                    MutedNote("Nothing in the last day. Press Y on the main screen for the full list.")
                } else {
                    info.recentUnlocks.take(8).forEach { a ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = RaBadges.url(a.BadgeName), contentDescription = null,
                                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.Title.orEmpty(), color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(13.sp), maxFontSize = 13.sp), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text(a.Description.orEmpty(), color = ShelfTheme.textMuted, fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            a.Points?.let { Text("$it pts", color = ShelfTheme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
}

private enum class AchFilter(val label: String) { ALL("All"), LOCKED("Locked"), UNLOCKED("Unlocked"), MISSABLE("Missable"), MISSABLE_LEFT("Missables to get") }

/** Filter chips over a touch-scrollable list of the game's achievements (read-only). */
@Composable
private fun AchievementList(list: List<RaAchievementDetail>, modifier: Modifier = Modifier) {
    var filter by remember { mutableStateOf(AchFilter.ALL) }
    val shown = remember(list, filter) {
        when (filter) {
            AchFilter.ALL -> list
            AchFilter.LOCKED -> list.filter { !it.isUnlocked }
            AchFilter.UNLOCKED -> list.filter { it.isUnlocked }
            AchFilter.MISSABLE -> list.filter { it.isMissable }
            AchFilter.MISSABLE_LEFT -> list.filter { it.isMissable && !it.isUnlocked }
        }
    }
    Column(modifier) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AchFilter.values().forEach { f ->
                val selected = f == filter
                Box(
                    Modifier
                        .padTarget("achfilter-" + f.name) { filter = f }
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) ShelfTheme.accent else Color.White.copy(alpha = 0.08f))
                        .clickable { filter = f }
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        f.label, color = if (selected) onAccentColor() else ShelfTheme.colors.textPrimary,
                        fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp), fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, softWrap = false
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (shown.isEmpty()) {
            Placeholder("Nothing in this filter.")
        } else {
            androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.id }) { a ->
                    val unlocked = a.isUnlocked
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp).alpha(if (unlocked) 1f else 0.6f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = a.badgeName?.let { if (unlocked) RaBadges.url(it) else RaBadges.url(it + "_lock") },
                            contentDescription = null,
                            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.title.orEmpty(), color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(13.sp), maxFontSize = 13.sp), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(a.description.orEmpty(), color = ShelfTheme.textMuted, fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (a.isMissable) {
                            Icon(
                                Icons.Filled.Warning, "Missable", tint = ShelfTheme.accent,
                                modifier = Modifier.padding(horizontal = 6.dp).size(16.dp)
                            )
                        }
                        a.points?.let { Text("$it", color = ShelfTheme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicPage(
    nowPlaying: NowPlaying?,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onOpenPlayer: () -> Unit = {}
) {
    if (nowPlaying == null) {
        Placeholder("Nothing playing. Start music in any app and it shows up here.")
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MusicPlayer(nowPlaying, onPlayPause, onNext, onPrevious, onSeek, modifier = Modifier.tapOnly(onOpenPlayer), artBackdrop = false)
        }
    }
}

/**
 * The dock: your apps (scrolls sideways if they don't all fit), a "+" to add
 * one, then all apps and settings on the right — a floating bar in the
 * theme's surface colour, like Voltcap's dialogs. Long-press an app for
 * move / remove.
 */
@Composable
private fun AppDockBar(
    apps: List<Game>,
    onLaunch: (Game) -> Unit,
    onAllApps: () -> Unit,
    onSettings: () -> Unit,
    onAdd: () -> Unit,
    onMenu: (Game) -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .voltSurface(shape, base = ShelfTheme.colors.surface, alpha = 0.88f)
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            apps.forEach { app ->
                DockSlot(app, onClick = { onLaunch(app) }, onLongClick = { onMenu(app) })
            }
            if (apps.size < SECOND_DOCK_MAX) DockSlot(null, onClick = onAdd, onLongClick = {})
        }
        Box(Modifier.width(1.dp).height(36.dp).background(Color.White.copy(alpha = 0.1f)))
        Spacer(Modifier.width(10.dp))
        RoundIcon(Icons.Filled.Apps, "All apps", onAllApps)
        Spacer(Modifier.width(8.dp))
        RoundIcon(Icons.Filled.Settings, "Dock settings", onSettings)
    }
}

private val SLOT: Dp = 48.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockSlot(app: Game?, onClick: () -> Unit, onLongClick: () -> Unit) {
    Box(
        Modifier
            .size(SLOT)
            .padTarget(rememberPadId("dock")) { onClick() }
            .clip(RoundedCornerShape(14.dp))
            .background(if (app == null) Color.White.copy(alpha = 0.05f) else ShelfTheme.colors.surfaceRaised)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        if (app == null) {
            Icon(Icons.Filled.Add, "Add an app to the dock", tint = ShelfTheme.accent, modifier = Modifier.size(22.dp))
        } else {
            SecondDockShortcuts.Icon(app.packageName, Modifier.fillMaxSize().padding(6.dp))
        }
    }
}

/** Long-press menu for a docked app: drawn over the second screen, tap outside to close. */
@Composable
private fun DockAppMenu(
    title: String,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit
) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable(onClick = onClose),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier
                .padding(bottom = 84.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(ShelfTheme.colors.surface)
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                .clickable(enabled = false) {}
                .padding(vertical = 6.dp)
                .width(200.dp)
        ) {
            Text(
                title, color = ShelfTheme.textMuted, fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp), fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            if (canMoveLeft) MenuItem("Move left") { onMove(-1) }
            if (canMoveRight) MenuItem("Move right") { onMove(1) }
            MenuItem("Remove from dock", onClick = onRemove)
        }
    }
}

@Composable
private fun MenuItem(text: String, onClick: () -> Unit) {
    Text(
        text, color = ShelfTheme.colors.textPrimary, fontSize = 14.sp,
        modifier = Modifier.fillMaxWidth().padTarget(rememberPadId("menu")) { onClick() }.clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

/**
 * Pick an app to add to the dock, right on the second screen: a grid of every
 * app not already docked, with an A–Z strip on the right to jump. Tapping (or
 * D-pad + A on) an app adds it and closes — one step.
 */
@Composable
private fun SecondScreenAppPicker(apps: List<Game>, onPick: (Game) -> Unit, onClose: () -> Unit) {
    // (Update 193: Voltcap's own shortcuts first, under ★ in the letters.)
    val sorted = remember(apps) {
        apps.sortedWith(compareBy<Game>({ !com.shelf.launcher.data.BuiltinShortcuts.isBuiltin(it.packageName) }, { it.name.lowercase() }))
    }
    val pickerKeys = remember(sorted) { appKeys(sorted) }
    fun initial(g: Game): Char = if (com.shelf.launcher.data.BuiltinShortcuts.isBuiltin(g.packageName)) '★'
        else g.name.trim().firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' } ?: '#'
    val firstIndex = remember(sorted) {
        val map = LinkedHashMap<Char, Int>()
        sorted.forEachIndexed { i, g -> map.getOrPut(initial(g)) { i } }
        map
    }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    Column(
        Modifier
            .fillMaxSize()
            .background(ShelfTheme.background.copy(alpha = 0.97f))
            .clickable(enabled = false) {}
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Add to dock", color = ShelfTheme.colors.textPrimary, fontSize = 18.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(18.sp), maxFontSize = 18.sp), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            RoundIcon(Icons.Filled.Close, "Close", onClose)
        }
        Spacer(Modifier.height(10.dp))
        if (sorted.isEmpty()) {
            Placeholder("Every app is already in the dock.")
        } else {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(76.dp),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sorted.size, key = { pickerKeys[it] }) { i ->
                        val app = sorted[i]
                        Column(
                            Modifier
                                .padTarget("pick-" + app.stableKey) { onPick(app) }
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPick(app) }
                                .padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            SecondDockShortcuts.Icon(app.packageName, Modifier.size(48.dp).aspectRatio(1f))
                            Spacer(Modifier.height(4.dp))
                            Text(
                                app.name, color = ShelfTheme.colors.textPrimary, fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp),
                                maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Column(
                    Modifier.width(30.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    firstIndex.forEach { (letter, index) ->
                        Text(
                            letter.toString(), color = ShelfTheme.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { scope.launch { gridState.scrollToItem(index) } }
                                .padding(vertical = 6.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(SLOT)
            .padTarget(rememberPadId("round")) { onClick() }
            .clip(CircleShape)
            .background(ShelfTheme.accent.copy(alpha = 0.18f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = ShelfTheme.accent, modifier = Modifier.size(22.dp))
    }
}

/** All apps, as a grid over the second screen (not a popup: it stays on this screen). */
@Composable
private fun SecondScreenDrawer(apps: List<Game>, onLaunch: (Game) -> Unit, onClose: () -> Unit) {
    val sorted = remember(apps) { apps.sortedBy { it.name.lowercase() } }
    val drawerKeys = remember(sorted) { appKeys(sorted) }
    Column(
        Modifier
            .fillMaxSize()
            .background(ShelfTheme.background.copy(alpha = 0.97f))
            // Update 144: taps on the drawer's title, padding or empty message stop here instead of
            // switching the page or hitting the dock underneath (no click target, so D-pad focus is unchanged).
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("All apps", color = ShelfTheme.colors.textPrimary, fontSize = 18.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(18.sp), maxFontSize = 18.sp), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            RoundIcon(Icons.Filled.Close, "Close", onClose)
        }
        Spacer(Modifier.height(10.dp))
        if (sorted.isEmpty()) {
            Placeholder("No apps found yet.")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(76.dp),
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(sorted.size, key = { drawerKeys[it] }) { i ->
                    val app = sorted[i]
                    Column(
                        Modifier
                            .padTarget("app-" + app.stableKey) { SecondPad.leave(); onLaunch(app) }
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onLaunch(app) }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AppIconArt(app.packageName, wide = false, modifier = Modifier.size(48.dp).aspectRatio(1f))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            app.name, color = ShelfTheme.colors.textPrimary, fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp),
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Grid keys for apps: the package, made unique when one package is listed twice (an app with two
 * launcher icons) — two items with one key crash a lazy grid.
 */
private fun appKeys(apps: List<Game>): List<String> {
    val seen = HashSet<String>(apps.size * 2)
    return apps.mapIndexed { i, a -> val k = a.packageName ?: a.name; if (seen.add(k)) k else "$k#$i" }
}

/** The scene behind the second screen's "Visualizer" music layout: the home's music scene, or Bloom. */
internal fun secondVisualizerMotif(theme: com.shelf.launcher.ui.theme.AppTheme, sceneTheme: String?): com.shelf.launcher.ui.theme.ThemeMotif {
    val picked = sceneTheme?.let { n -> com.shelf.launcher.ui.theme.AppTheme.values().firstOrNull { it.name == n } }
    return listOfNotNull(picked?.motif, theme.motif).firstOrNull { it.reactive } ?: com.shelf.launcher.ui.theme.ThemeMotif.BLOOM
}
