package com.shelf.launcher.ui

import kotlinx.coroutines.flow.flowOn
import com.shelf.launcher.data.statusOn
import com.shelf.launcher.data.legendOn
import com.shelf.launcher.data.shownIn
import com.shelf.launcher.data.cardArtFor
import com.shelf.launcher.data.kindOf

import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.toArgb
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.border
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import android.view.InputDevice
import android.view.MotionEvent
import kotlin.math.abs
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import com.shelf.launcher.data.actionFor
import com.shelf.launcher.data.hotkeyFor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.shelf.launcher.data.RaBadges
import com.shelf.launcher.ArtConnectionState
import com.shelf.launcher.LauncherViewModel
import com.shelf.launcher.RaConnectionState
import com.shelf.launcher.SpecialFilter
import com.shelf.launcher.forDisplay
import com.shelf.launcher.usesOwnIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.shelf.launcher.ui.theme.ShelfTheme

/**
 * Root of the launcher. Sets up interface sounds for everything below it —
 * including popups — then draws the screen.
 */
@Composable
fun LauncherScreen(viewModel: LauncherViewModel = viewModel()) {
    com.shelf.launcher.Breadcrumbs.mark("LauncherScreen")
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    val sounds = remember { UiSounds(context.applicationContext) }
    DisposableEffect(Unit) { onDispose { sounds.release() } }
    // Only what this level draws from: a D-pad step (which changes other parts
    // of the state) no longer recomposes the root or rebuilds its CompositionLocals.
    val appearance by remember(viewModel) {
        viewModel.uiState.map { it.appearance }.distinctUntilChanged()
    }.collectAsState(viewModel.uiState.value.appearance)
    // (Update 255, #74: look-alike names, worked out off the main thread whenever the library or the name setting changes.)
    val lookAlike by remember(viewModel) {
        viewModel.uiState.map { it.games to it.appearance.hideAllBrackets }.distinctUntilChanged()
            .map { (games, all) -> lookAlikeNames(games, all) }
            .flowOn(kotlinx.coroutines.Dispatchers.Default)
    }.collectAsState(emptyMap())
    val rootTheme by remember(viewModel) {
        viewModel.uiState.map { it.appTheme }.distinctUntilChanged()
    }.collectAsState(viewModel.uiState.value.appTheme)
    // Only needed for "Wide for the first game only" (whether the row's first game is the one you're on).
    val rootFocusIndex by remember(viewModel) {
        viewModel.uiState.map { it.focusedIndex }.distinctUntilChanged()
    }.collectAsState(viewModel.uiState.value.focusedIndex)
    val nowPlayingActive by remember(viewModel) {
        viewModel.uiState.map { it.nowPlaying?.isPlaying == true }.distinctUntilChanged()
    }.collectAsState(viewModel.uiState.value.nowPlaying?.isPlaying == true)
    // Music of its own for the platform / game you are on (platform and game music overrides).
    val musicContext by remember(viewModel) {
        viewModel.uiState.map { st ->
            val g = st.visibleGames.getOrNull(st.focusedIndex)
            MusicContext(g?.stableKey, st.platformFilter ?: g?.platform)
        }.distinctUntilChanged()
    }.collectAsState(MusicContext(null, null))
    val browsingPlatform = PlatformBrowserFocus.platform.value
    val musicOverridesVersion = com.shelf.launcher.data.MusicOverrides.version.value
    val restartKeys = com.shelf.launcher.data.Features.musicRestartOnReturn.state.value
    val ownMusicTrack = remember(browsingPlatform, musicContext, musicOverridesVersion, restartKeys) {
        val key = if (browsingPlatform != null) com.shelf.launcher.data.MusicOverrides.active(context, null, browsingPlatform)
        else com.shelf.launcher.data.MusicOverrides.active(context, musicContext.gameKey, musicContext.platform)
        key?.let { k ->
            com.shelf.launcher.data.MusicOverrides.file(context, k)?.let { f -> MusicOverrideSource(k, f, f.lastModified(), k in restartKeys) }
        }
    }
    SideEffect {
        sounds.enabled = appearance.soundsEnabled
        if (com.shelf.launcher.data.AnimatedArt.enabled != appearance.animatedArt) {
            com.shelf.launcher.data.AnimatedArt.enabled = appearance.animatedArt
            // Pictures already in memory were decoded the other way: drop them so covers reload playing / still.
            runCatching { coil.Coil.imageLoader(context).memoryCache?.clear() }
        }
        com.shelf.launcher.data.AnimatedArt.preferAnimatedScraping = appearance.animatedArt && appearance.preferAnimatedArt
        sounds.volume = appearance.soundVolume / 100f
        // The console-menu, 32-bit and dark-fantasy music bring their own navigation sounds (if switched on).
        sounds.style = appearance.soundStyleFor(rootTheme).let { st ->
            // Update 181: the Pocket layouts bring their own small chirps (Layout options › Pocket sounds).
            if (appearance.homeLayout.isPocket && com.shelf.launcher.data.Features.pocketSounds.value) com.shelf.launcher.data.SoundStyle.POCKET else st
        }
        sounds.haptics = appearance.haptics
        sounds.hapticStrength = appearance.hapticStrength
        sounds.hapticView = view
        // One album-art blur for every music player (also read by the overlay and companion screen).
        AlbumArtBlur.sync(appearance.secondMusicBlurArt, appearance.secondMusicBlur)
    }
    val liteContext = androidx.compose.ui.platform.LocalContext.current
    val liteEffects = when (appearance.performanceMode) {
        com.shelf.launcher.data.PerformanceMode.LITE -> true
        com.shelf.launcher.data.PerformanceMode.FULL -> false
        // Automatic also turns lite on when the frames watched this session stay slow (FrameWatch).
        com.shelf.launcher.data.PerformanceMode.AUTO -> remember { com.shelf.launcher.device.DevicePerformance.isLowEnd(liteContext) } ||
            com.shelf.launcher.device.FrameWatch.steps.intValue >= 2
    }
    // Update 125: where the names go in this layout (its options), else Game names.
    com.shelf.launcher.data.Features.namesByLayout.state.value
    val layoutNames = com.shelf.launcher.data.NamePlan.of(appearance.homeLayout, appearance.titlePlacement, appearance.nameSide)
    // Update 129: the Voltcap row with the carousel look (its games sized by the carousel's Game size).
    val rowCarousel = appearance.homeLayout == com.shelf.launcher.data.HomeLayout.CLASSIC &&
        com.shelf.launcher.data.HomeLayout.CLASSIC.name in com.shelf.launcher.data.Features.carouselLayouts.state.value
    CompositionLocalProvider(
        LocalUiSounds provides sounds,
        LocalLiteEffects provides liteEffects,
        LocalAnimateOnLite provides appearance.animateOnLite,
        LocalSceneQuality provides when (appearance.sceneQuality) {
            // Automatic: Low on slower handhelds, Balanced on small-memory ones, Full elsewhere.
            com.shelf.launcher.data.SceneQuality.AUTO -> remember {
                when {
                    com.shelf.launcher.device.DevicePerformance.isLowEnd(liteContext) -> com.shelf.launcher.data.SceneQuality.LOW
                    com.shelf.launcher.device.MemoryBudget.small(liteContext) -> com.shelf.launcher.data.SceneQuality.BALANCED
                    // Strong handhelds (Odin 2, Thor…): 60 fps, like the rest of the UI. (Update 126: on 60 Hz screens
                    // too — they got 30 before; FrameWatch still steps it down to 30 if the device drops frames.)
                    else -> com.shelf.launcher.data.SceneQuality.SMOOTH
                }
            }.let { q ->
                // Still missing frames on this device: one step lower per FrameWatch step (Automatic only).
                val down = com.shelf.launcher.device.FrameWatch.steps.intValue
                com.shelf.launcher.data.SceneQuality.values()[(q.ordinal + down).coerceAtMost(com.shelf.launcher.data.SceneQuality.LOWEST.ordinal)]
            }
            else -> appearance.sceneQuality
        }.let { q ->
            // Theme intensity Low doubles as performance mode: at most Low quality (fewer pixels, fewer frames).
            if (com.shelf.launcher.data.Features.themeIntensity.state.value == com.shelf.launcher.data.Features.Intensity.LOW &&
                q.ordinal < com.shelf.launcher.data.SceneQuality.LOW.ordinal
            ) com.shelf.launcher.data.SceneQuality.LOW else q
        },
        LocalTitleOptions provides TitleOptions(
            // Row at the top of the screen: names go under the wide capsule
            // by default (Game names › "On the art" + Name sits › Automatic).
            // (Update 125: per layout — the layout's own choice in its options, else Game names.)
            if (layoutNames.first == com.shelf.launcher.data.TitlePlacement.INSIDE &&
                appearance.dockPosition == com.shelf.launcher.data.DockPosition.TOP &&
                layoutNames.second == com.shelf.launcher.data.RowSide.AUTO
            ) com.shelf.launcher.data.TitlePlacement.ABOVE else layoutNames.first,
            appearance.hideRegionTags,
            appearance.coverAchievements,
            appearance.coverRecentUnlocks,
            appearance.masteredStyle,
            appearance.masteredShimmer,
            appearance.showGameLogos,
            appearance.showConsoleName,
            appearance.logoPlacement,
            appearance.fullConsoleNames,
            hideAllBrackets = appearance.hideAllBrackets,
            shimmerStyle = appearance.shimmerStyle,
            lookAlike = lookAlike
        ),
        // (Update 141: whatever Endless and "Game you're on" say — see pinnedFirstOn.)
        LocalPinnedFirstWide provides pinnedFirstOn(appearance),
        // Cartridges / discs this home layout leaves out (Appearance › Game row › Cards).
        LocalHiddenMedia provides appearance.hiddenMedia,
        LocalCardLook provides CardLook(
            // Card size, times the Voltcap row's % size (update 105, R1 Size / Layout options).
            // (Update 129: with the carousel look, times the carousel's Game size, and not sized by "games across".)
            heightFraction = appearance.cardSize.heightFraction *
                (if (appearance.homeLayout == com.shelf.launcher.data.HomeLayout.CLASSIC) GameSize.scale(com.shelf.launcher.data.HomeLayout.CLASSIC) else 1f) *
                (if (rowCarousel) com.shelf.launcher.data.Features.carouselSize.state.value / 100f else 1f),
            rowGames = if (appearance.homeLayout == com.shelf.launcher.data.HomeLayout.CLASSIC && !rowCarousel) com.shelf.launcher.data.Features.rowGamesFor(com.shelf.launcher.data.HomeLayout.CLASSIC) else 0,
            squareTiles = tileLookOn(appearance),
            spines = com.shelf.launcher.data.Features.spineLook.state.value && !tileLookOn(appearance) &&
                appearance.cardArtFor() == com.shelf.launcher.data.CardArtType.COVER,
            capsules = appearance.cardArtFor() == com.shelf.launcher.data.CardArtType.CAPSULE,
            cornerDp = appearance.cardCorners.radiusDp,
            // Tile-style tiles never unfold into a wide capsule.
            focusedWide = (appearance.focusedCardStyle == com.shelf.launcher.data.FocusedCardStyle.WIDE ||
                // "Covers · first one wide" (update 141): the row's first game is a fixed wide capsule at the left
                // end, always, and every other game a cover — the one you're on too (GameDock lets only game 1 be wide).
                // (Update 204: only where that fixed capsule is drawn — pinnedFirstOn. Elsewhere (the row as a carousel)
                // it made every game you landed on unfold into a wide capsule; there they stay covers now.)
                (appearance.focusedCardStyle == com.shelf.launcher.data.FocusedCardStyle.FIRST_ONLY && pinnedFirstOn(appearance))) &&
                appearance.homeLayout != com.shelf.launcher.data.HomeLayout.TILES && !tileLookOn(appearance),
            otherGames = appearance.otherGamesLook,
            focusPosition = appearance.focusPosition,
            playtimeOnHome = appearance.playtimeOnHome,
            trophyCorner = appearance.trophyCorner,
            trophyOnBanner = appearance.trophyOnBanner,
            progressOnBanner = appearance.progressOnBanner,
            focusBorder = appearance.focusBorder,
            loopRow = appearance.loopRow,
            nameBelow = layoutNames.second.below(appearance.dockPosition),
            tiltOthers = appearance.tiltOthers,
            nameCapsule = appearance.nameCapsule,
            nameBackdrop = appearance.nameBackdrop,
            progressAbove = appearance.progressAbove,
            focusEffect = appearance.focusEffect,
            masteredThemeColor = appearance.masteredThemeColor,
            achievementsOnCards = appearance.achievementsOnCards,
            favoriteStar = appearance.favoriteStar,
            bannerButtons = appearance.bannerButtons,
            lastPlayedOnBanner = appearance.lastPlayedOnBanner,
            infoSpacingDp = appearance.infoSpacing.dp
        )
    ) {
        // Played once per start, over everything, when switched on.
        var showIntro by remember { mutableStateOf(appearance.introStyle != com.shelf.launcher.data.IntroStyle.OFF && com.shelf.launcher.LaunchInfo.shouldPlayIntro() && viewModel.takeIntroSlot()) }
        // While the intro plays the launcher isn't drawn at all, so nothing of
        // it — and none of its pop-ups, which open in their own windows above
        // everything — can show through. It appears as the intro fades out.
        var contentVisible by remember { mutableStateOf(!showIntro) }
        // Start-up work held back for the intro (StartupGate) goes ahead once it ends, is skipped, or never plays.
        LaunchedEffect(showIntro) { if (!showIntro) StartupGate.release() }
        val introScope = rememberCoroutineScope()
        // Any controller button skips the intro (a tap already does). The
        // button's release is swallowed afterwards, so skipping never also
        // opens / plays something on the home screen.
        var introEndedAt by remember { mutableStateOf(0L) }
        val introFocus = remember { androidx.compose.ui.focus.FocusRequester() }
        LaunchedEffect(Unit) { if (showIntro) runCatching { introFocus.requestFocus() } }
        Box(
            Modifier
                .fillMaxSize()
                .background(ShelfTheme.background)
                .focusRequester(introFocus)
                .focusable()
                .onPreviewKeyEvent { event ->
                    when {
                        showIntro && !isSystemKey(event.nativeKeyEvent.keyCode) -> {
                            if (event.type == KeyEventType.KeyDown) {
                                introEndedAt = android.os.SystemClock.uptimeMillis()
                                showIntro = false
                                contentVisible = true
                            }
                            true
                        }
                        event.type == KeyEventType.KeyUp && event.nativeKeyEvent.downTime <= introEndedAt -> true
                        else -> false
                    }
                }
        ) {
            if (contentVisible) LauncherScreenContent(viewModel)
            // Quiet theme music (or your own track), if switched on. It waits
            // for the intro so the two never overlap.
            BackgroundMusicHost(
                // The track can be any loop, independent of the look; "" = the theme's own.
                loop = appearance.musicLoopFor(rootTheme),
                mode = if (showIntro || com.shelf.launcher.CrashGuard.safeMode) com.shelf.launcher.data.BackgroundMusicMode.OFF else appearance.musicMode,
                volumePercent = appearance.musicVolume,
                customStamp = appearance.musicCustomStamp,
                suppress = nowPlayingActive,
                ownTrack = if (showIntro || com.shelf.launcher.CrashGuard.safeMode) null else ownMusicTrack
            )
            // Update 169: the extra art packs' download, wherever you are (it showed only in its Settings card).
            if (!showIntro) PacksProgressPill(Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 14.dp))
            if (showIntro) {
                VoltcapIntro(
                    style = appearance.introStyle,
                    onBolt = {
                        val a = viewModel.uiState.value.appearance
                        if (a.lightsEnabled) introScope.launch {
                            com.shelf.launcher.data.ControllerLights.flash(
                                context, 0xFFFFD84A.toInt(), times = 1,
                                restoreColor = com.shelf.launcher.data.LightsTheme.resolve(a.lightsColor), restoreBrightness = a.lightsBrightness,
                                onMs = 260
                            )
                        }
                    },
                    onReveal = { contentVisible = true },
                    onDone = { introEndedAt = android.os.SystemClock.uptimeMillis(); showIntro = false; contentVisible = true }
                )
            }
        }
    }
}

/** Volume / media / power-style keys: never part of a Select combo, always left to the system. */
private fun isSystemKey(code: Int): Boolean = when (code) {
    android.view.KeyEvent.KEYCODE_VOLUME_UP, android.view.KeyEvent.KEYCODE_VOLUME_DOWN,
    android.view.KeyEvent.KEYCODE_VOLUME_MUTE, android.view.KeyEvent.KEYCODE_POWER,
    android.view.KeyEvent.KEYCODE_HEADSETHOOK, android.view.KeyEvent.KEYCODE_CAMERA,
    android.view.KeyEvent.KEYCODE_BRIGHTNESS_UP, android.view.KeyEvent.KEYCODE_BRIGHTNESS_DOWN -> true
    else -> code in 85..90 || code in android.view.KeyEvent.KEYCODE_MEDIA_PLAY..android.view.KeyEvent.KEYCODE_MEDIA_RECORD
}

@Composable
private fun LauncherScreenContent(viewModel: LauncherViewModel) {
    com.shelf.launcher.Breadcrumbs.mark("LauncherScreenContent")
    val sounds = LocalUiSounds.current
    val uiState by viewModel.uiState.collectAsState()
    // What to draw: apps set to use their own icon have their scraped art
    // hidden here, so every screen falls back to the app-icon look.
    val state = remember(uiState) { uiState.forDisplay() }
    // Update 177: the consoles there are, for Settings › Second screen Games page (FolderArt).
    androidx.compose.runtime.SideEffect {
        if (com.shelf.launcher.data.FolderArt.platforms != state.availablePlatforms) com.shelf.launcher.data.FolderArt.platforms = state.availablePlatforms
    }
    val flagsContext = androidx.compose.ui.platform.LocalContext.current
    val flags = remember(viewModel) { LauncherUiFlags(viewModel, flagsContext, state.searchQuery.isNotBlank()) }
    with(flags) {
    val guideUi by viewModel.guideState.collectAsState()
    // All apps (swipe up from the bottom edge, or the "All apps" hotkey).

    // Full details window (A on a focused game). Deliberately NOT part of
    // aDialogIsOpen: it lives inside the root Box, so the root must keep focus
    // and handle its keys itself.
    // Start › Collections / Status, Details › Tips, and a screenshot opened full screen.
    // Details › Artwork strip: what it holds, which picture is ringed, and the full-screen viewer.
    // Details › achievements list shown inline under the progress bar, and its filter.
    // The game being started (launch animation) — launched when it ends.
    // A different game is still open in the background: ask before starting another (quit it, or keep both).
    // The yearly recap (every December and on the Voltcap anniversary; Settings › Stats any time).
    var yearRecap by remember { mutableStateOf<com.shelf.launcher.data.YearRecap.Period?>(null) }
    // Manual: none found for this game (a window with what to do next).
    var manualMissing by remember { mutableStateOf<com.shelf.launcher.data.Game?>(null) }
    val recapContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        // After start-up has settled (intro, first scan), once.
        kotlinx.coroutines.delay(8_000)
        val due = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { com.shelf.launcher.data.YearRecap.due(recapContext) }.getOrNull()
        }
        if (due != null && yearRecap == null) yearRecap = due
    }
    var runningAsk by remember { mutableStateOf<Pair<com.shelf.launcher.data.Game, List<com.shelf.launcher.data.Game>>?>(null) }
    // The platform's intro video (Platform Options): after the launch animation, before the game starts.
    fun launchAfterIntro(game: com.shelf.launcher.data.Game, alreadyOpen: Boolean) {
        val on = com.shelf.launcher.data.Features.platformIntroVideos.value && game.platform !in com.shelf.launcher.data.Features.introVideoOff.value
        val video = if (on && !alreadyOpen) {
            com.shelf.launcher.data.PlatformInfo.introVideo(game.platform) ?: com.shelf.launcher.data.PlatformInfo.media(game.platform)?.video
        } else null
        if (video != null) introVideo = game to video else viewModel.launch(game)
    }
    fun startGame(game: com.shelf.launcher.data.Game, askIfOthersOpen: Boolean = true) {
        // Update 151: a game already being thrown in starts when its animation ends (a second tap would start it twice).
        if (LaunchThrow.plan != null) return
        val alreadyOpen = game.stableKey in com.shelf.launcher.data.RunningGames.confirmed
        if (askIfOthersOpen && !alreadyOpen) {
            val others = viewModel.otherOpenGames(game)
            if (others.isNotEmpty()) { runningAsk = game to others; return }
        }
        sounds?.play(UiSound.LAUNCH)
        // A game that's still open just comes back to the front: no launch animation for that.
        // Update 151: a case, cartridge or disc is thrown in instead (LaunchThrow.kt, Home › Animations › Starting a game).
        // (Update 295: one choice, Home › Animations › Starting a game — Plug in the media by default, falling back to the
        // cover rising where the media can't play (no place on screen, a full-screen card); Cover rises; or Off.)
        val style = com.shelf.launcher.data.Features.launchThrow.value
        val thrown = if (style == com.shelf.launcher.data.Features.LaunchThrow.THROW && !alreadyOpen && LaunchThrow.plan == null && launchingGame == null)
            LaunchThrow.planFor(game, state, state.visibleGames.getOrNull(state.focusedIndex)?.stableKey) else null
        if (thrown != null) LaunchThrow.plan = thrown
        else if (style != com.shelf.launcher.data.Features.LaunchThrow.OFF && !alreadyOpen) launchingGame = game else launchAfterIntro(game, alreadyOpen)
    }
    val detailsScroll = rememberScrollState()
    val detailsScope = rememberCoroutineScope()

    // Every way of opening the window goes through here so it always starts
    // on Play, scrolled to the top.
    val openDetails = {
        detailsAction = DetailsAction.PLAY
        galleryIndex = 0
        summaryExpanded = false
        achInlineOpen = false
        achInlineFilter = 0
        achInlineIndex = -1
        achInlineExpanded = null
        showDetails = true
    }

    val visibleGames = state.visibleGames
    // (Update 258: what an empty shelf says — StyleHomes.kt EmptyShelf.)
    val emptyLine = EmptyShelf.textFor(state.searchQuery, state.games.none { it.sourceType != com.shelf.launcher.data.SourceType.APP })
    SideEffect { if (EmptyShelf.text != emptyLine) EmptyShelf.text = emptyLine }
    // 0.6.17: the Metacritic chip on the wide / row cards reads the details from here (GameCard).
    SideEffect { if (MetacriticCards.metadata !== state.metadata) MetacriticCards.metadata = state.metadata }
    val focusedGame = visibleGames.getOrNull(state.focusedIndex)
    // Update 181: only the game you're on moves (Settings › Artwork › Only the game you're on moves; on by default).
    val animFocusOnly = com.shelf.launcher.data.Features.animatedFocusOnly.state.value
    SideEffect { com.shelf.launcher.data.AnimatedArt.focusOnly = animFocusOnly }
    val focusArtKey = focusedGame?.stableKey
    val focusArt = focusArtKey?.let { state.art[it] }
    val focusOverride = focusArtKey?.let { state.artOverrides[it] }
    LaunchedEffect(focusArtKey, focusArt, focusOverride) {
        com.shelf.launcher.data.AnimatedArt.setFocus(
            listOfNotNull(
                focusArt?.gridUrl, focusArt?.wideGridUrl, focusArt?.heroUrl, focusArt?.logoUrl, focusArt?.animatedHeroUrl,
                focusOverride?.cover, focusOverride?.wide, focusOverride?.hero, focusOverride?.logo
            ).toSet()
        )
    }
    // Update 202: the game you're on, for the small pictures of the game look's choices (Settings › Game look, holding
    // the R1 Look tile).
    SideEffect {
        LookPreviewGame.set(
            focusedGame, focusOverride?.cover ?: focusArt?.gridUrl, focusOverride?.wide ?: focusArt?.wideGridUrl,
            focusOverride?.hero ?: focusArt?.heroUrl, focusArtKey?.let { state.screenshots[it]?.firstOrNull() },
            focusArtKey?.let { state.physicalMedia[it] }
        )
    }
    // Update 156: the next and previous categories got ready in the background (their covers in memory).
    CategoryWarmup(state)
    // A or a tap on the game you're on (update 113): the layout's own game window (GameWindow.kt; its Details button
    // opens the big details window), or with Home › Game window off, straight into the game.
    val openGame = {
        val g = focusedGame
        if (g != null) {
            // (Update 119: a layout set to the classic window opens the big details window instead.)
            // Update 132: in the Cross menu's Trophies category A opens the game's achievements (its column, CrossTrophies.kt).
            if (state.appearance.homeLayout == com.shelf.launcher.data.HomeLayout.CROSS &&
                state.specialFilter == com.shelf.launcher.SpecialFilter.TROPHIES && g.raGameId != null) viewingAchievementsForGame = g
            else if (!com.shelf.launcher.data.Features.gameWindow.value) startGame(g)
            else if (state.appearance.homeLayout.name in com.shelf.launcher.data.Features.classicWindowLayouts.value) openDetails()
            else GamePopup.game = g
        }
    }

    // Game names › Logos from RetroAchievements: the game's RA icon stands in
    // for the SteamGridDB logo (a logo you picked yourself still wins).
    val displayArt = remember(state.art, state.raIcons, state.raCompletionById, state.appearance.raLogos, state.games) {
        if (!state.appearance.raLogos) state.art else {
            val out = state.art.toMutableMap()
            state.games.forEach { g ->
                val id = g.raGameId ?: return@forEach
                val icon = state.raIcons[id]
                    ?: com.shelf.launcher.data.RaBadges.mediaUrl(state.raCompletionById[id]?.ImageIcon)
                    ?: return@forEach
                out[g.stableKey] = (out[g.stableKey] ?: com.shelf.launcher.data.GameArt(gridUrl = null, heroUrl = null)).copy(logoUrl = icon)
            }
            out
        }
    }
    // Artwork priority: a game whose background would come from a screenshot (no hero, or "Screenshot
    // first") gets its screenshots looked up once you rest on it (cached after the first time).
    val artPriorityNow = com.shelf.launcher.data.GameLooks.artPriority(focusedGame?.stableKey)
    LaunchedEffect(focusedGame?.stableKey, artPriorityNow) {
        val game = focusedGame ?: return@LaunchedEffect
        val key = game.stableKey
        if (state.screenshots.containsKey(key)) return@LaunchedEffect
        val hasHero = state.artOverrides[key]?.hero != null || state.art[key]?.heroUrl != null
        val wants = when (artPriorityNow) {
            com.shelf.launcher.data.Features.ArtPriority.SCREENSHOT -> true
            com.shelf.launcher.data.Features.ArtPriority.COVER -> false
            else -> !hasHero
        }
        if (!wants) return@LaunchedEffect
        kotlinx.coroutines.delay(650)
        viewModel.loadMedia(game)
    }
    // Resting on a game for a moment fetches its newest saves from Drive in
    // the background (never delays Play).
    LaunchedEffect(focusedGame?.stableKey) {
        val game = focusedGame ?: return@LaunchedEffect
        kotlinx.coroutines.delay(1_500)
        viewModel.prepareLaunch(game)
    }
    // Update 113: the big logos are SteamGridDB's by default — looked up for the game you rest on when its artwork's
    // logo came from somewhere else (once per game, remembered).
    // (Update 125: also whenever the "Logo (SteamGridDB)" part is on this layout.)
    val sgdbPart = HomeWidgets.LOGO_SGDB in com.shelf.launcher.data.Features.overlayPartsFor(state.appearance.homeLayout)
    LaunchedEffect(focusedGame?.stableKey, com.shelf.launcher.data.Features.bigLogoSgdb.state.value, sgdbPart) {
        val g = focusedGame ?: return@LaunchedEffect
        if (sgdbPart) { kotlinx.coroutines.delay(400); viewModel.loadSgdbLogo(g); return@LaunchedEffect }
        if (!com.shelf.launcher.data.Features.bigLogoSgdb.value || state.artOverrides[g.stableKey]?.logo != null) return@LaunchedEffect
        if (state.art[g.stableKey]?.logoUrl?.contains("steamgriddb", ignoreCase = true) == true) return@LaunchedEffect
        kotlinx.coroutines.delay(400)
        viewModel.loadSgdbLogo(g)
    }
    val raLogoPart = HomeWidgets.LOGO_RA in com.shelf.launcher.data.Features.overlayPartsFor(state.appearance.homeLayout)
    LaunchedEffect(focusedGame?.stableKey, state.appearance.raLogos, raLogoPart) {
        if (!(state.appearance.raLogos || raLogoPart) || focusedGame == null) return@LaunchedEffect
        // (Update 232, #60: once you rest on the game — stepping fast along the shelf no longer reads its saved icon and
        // updates the screen's state at every game passed.)
        kotlinx.coroutines.delay(450)
        if (state.appearance.raLogos || raLogoPart) focusedGame?.let { viewModel.loadRaIcon(it) }
    }

    // Opening the details window (or moving to another game while it's open)
    // pulls in what it shows. Both calls are no-ops when the data is already
    // cached or loading.
    // Left analog stick scrolls the details window smoothly. Only the vertical
    // axis is taken while it's open; everything else is left for Android to
    // turn into D-pad presses as usual (that's what moves the dock and the
    // highlighted button).
    val view = LocalView.current
    val detailsOpenForStick by rememberUpdatedState(showDetails)
    // Right stick left/right: jump through the library letter by letter.
    DisposableEffect(view) {
        view.setOnGenericMotionListener { _, event ->
            if (event.isFromSource(InputDevice.SOURCE_JOYSTICK) && event.action == MotionEvent.ACTION_MOVE) {
                val rx = event.getAxisValue(MotionEvent.AXIS_Z)
                rightStickX = if (abs(rx) > 0.6f) rx else 0f
            }
            // Details window: the RIGHT stick scrolls it. The left stick and
            // the D-pad are left alone — Android turns the left stick into
            // D-pad presses, which move between the window's buttons.
            if (detailsOpenForStick &&
                event.isFromSource(InputDevice.SOURCE_JOYSTICK) &&
                event.action == MotionEvent.ACTION_MOVE
            ) {
                val ry = event.getAxisValue(MotionEvent.AXIS_RZ)
                stickY = if (abs(ry) > 0.2f) ry else 0f
                false
            } else {
                stickY = 0f
                false
            }
        }
        onDispose { view.setOnGenericMotionListener(null) }
    }
    LaunchedWhileActive(showDetails) {
        if (!showDetails) return@LaunchedWhileActive
        while (true) {
            // Asleep while the stick rests (it used to wake every frame for as long as the window was open).
            if (abs(stickY) <= 0.2f) androidx.compose.runtime.snapshotFlow { abs(stickY) > 0.2f }.first { it }
            withFrameNanos { }
            val y = stickY
            if (abs(y) > 0.2f) detailsScroll.dispatchRawDelta(y * 26f)
        }
    }

    // Back from a game that was launched with the details window open: reopen it once that game is focused.
    val pendingDetailsKey = viewModel.pendingDetailsKeyState.value
    LaunchedEffect(pendingDetailsKey, focusedGame?.stableKey) {
        if (pendingDetailsKey != null && focusedGame?.stableKey == pendingDetailsKey) {
            viewModel.pendingDetailsKeyState.value = null
            if (!viewModel.categoryScreenOpenState.value) openDetails()
        }
    }
    LaunchedEffect(showDetails, focusedGame?.stableKey) {
        val game = focusedGame
        if (showDetails && game == null) showDetails = false
        if (showDetails && game != null) {
            if (state.metadataReady) viewModel.loadMetadata(game)
            if (game.raGameId != null) viewModel.loadAchievementDetails(game)
            viewModel.loadMedia(game)
            viewModel.loadPhysicalMedia(game)
            viewModel.loadVideoUrl(game, force = true)
        }
    }

    // Screensaver: any input restarts the idle timer. The button that wakes
    // it is swallowed (down *and* up), so waking never also launches a game.
    // Quick jump: null = closed, otherwise the highlighted letter.
    // Which control of the top-left music player the D-pad is on (null = the dock).
    // Set while a held ◀/▶ is auto-repeating, so its release doesn't add a step.
    // ◀ / ▶ presses already acted on when they went down: their release is swallowed.
    val dockKeysDown = remember { mutableSetOf<Int>() }
    // Tile-style home: which round button under the tiles has the focus (null = the tiles).
    // When the D-pad went down onto those buttons: a release of a press that began before never acts there.
    // Which Settings section to open (the avatar opens straight into Profile).

    // Coming back to the launcher closes the running game's play session and
    // restarts the idle timer, so the screensaver can't greet you on return.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) viewModel.onLauncherPaused()
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.onLauncherResumed()
                lastInputAt = System.currentTimeMillis()
                screensaverOn = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.statusMessage) {
        if (state.statusMessage != null) {
            kotlinx.coroutines.delay(3500)
            viewModel.clearStatusMessage()
        }
    }

    val rootFocusRequester = remember { FocusRequester() }

    // Hoisted so focus management can see it too, not just the key handler.
    val firstRunContext = androidx.compose.ui.platform.LocalContext.current
    // Save sync (Google Drive): the per-game dialog. Opened from the R1 panel's Drive card — the
    // details window no longer carries a sync badge (status bar notification + R1 are enough).
    val syncLastAt = com.shelf.launcher.data.CloudSync.state.collectAsState().value.settings.lastSyncAt
    // Swiping sideways on the corner music player opens the music library to pick a playlist.
    DisposableEffect(Unit) {
        MusicSwipe.handler = { showSpotify = true }
        MusicSwipe.openFull = { showMusicPopup = true }
        val openSaves = { showMenu = false; showSyncStatus = false; showNotifications = false; showSaveExplorer = true }
        SaveExplorerHook.open = openSaves
        // Only clear what's still ours: a rebuilt home screen sets its own first, then the old one disposes.
        onDispose { MusicSwipe.handler = null; MusicSwipe.openFull = null; if (SaveExplorerHook.open === openSaves) SaveExplorerHook.open = null }
    }
    // Games open in the background: checked again every 20 s while the home screen is on screen
    // (only while there are some), so a game closed from the app list stops glowing.
    LaunchedWhileActive(com.shelf.launcher.data.RunningGames.keys.isNotEmpty()) {
        while (com.shelf.launcher.data.RunningGames.keys.isNotEmpty()) {
            kotlinx.coroutines.delay(20_000)
            viewModel.refreshRunning()
        }
    }
    // A Voltcap transfer QR code opened from a camera app: straight to Settings › Backup › QR code,
    // which receives it (the settings show in the Import Preview first).
    val qrPending by com.shelf.launcher.data.QrTransfer.pending.collectAsState()
    LaunchedEffect(qrPending) {
        if (qrPending != null) { openFold("share_qr"); menuStartCategory = "Backup"; showMenu = true }
    }
    // Rediscover asked for from Settings › Library or L1: its own (hand-opened) category.
    val rediscoverRequests = RediscoverHook.requests.intValue
    var rediscoverSeen by remember { mutableStateOf(rediscoverRequests) }
    LaunchedEffect(rediscoverRequests) {
        if (rediscoverRequests != rediscoverSeen) {
            rediscoverSeen = rediscoverRequests
            showMenu = false; showSettings = false
            viewModel.setSpecialFilter(com.shelf.launcher.SpecialFilter.REDISCOVER)
        }
    }
    // Explore saves asked for from the R1 panel, Settings › Sync or the sync window.
    val saveRequests = SaveExplorerHook.requests.intValue
    var saveRequestsSeen by remember { mutableStateOf(saveRequests) }
    LaunchedEffect(saveRequests) {
        if (saveRequests != saveRequestsSeen) {
            saveRequestsSeen = saveRequests
            showMenu = false; showSyncStatus = false; showNotifications = false
            // A frame for the closing panel to let go of the controller, then the browser opens.
            kotlinx.coroutines.delay(50)
            showSaveExplorer = true
        }
    }
    // The details window's small Drive icon: only when Drive is connected and this game's saves can sync.
    val detailsSyncAvailable by produceState(false, if (showDetails) focusedGame?.stableKey else null, syncLastAt) {
        val g = focusedGame?.takeIf { showDetails }
        value = g != null && kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { com.shelf.launcher.data.CloudSync.canWatchSaves(firstRunContext, g) }.getOrDefault(false)
        }
    }
    val detailsSyncStatuses by com.shelf.launcher.data.SyncStatusStore.all.collectAsState()
    // The R1 panel's Drive card shows the focused game's save status when its saves can sync.
    val r1SyncGame by produceState<com.shelf.launcher.data.Game?>(null, if (showNotifications || showSyncStatus) focusedGame?.stableKey else null, syncLastAt) {
        val g = focusedGame?.takeIf { showNotifications || showSyncStatus }
        value = g?.takeIf {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { com.shelf.launcher.data.CloudSync.canWatchSaves(firstRunContext, it) }.getOrDefault(false)
            }
        }
    }
    // Setup popups normally return to the settings menu when closed; during
    // the first-start setup they return to the setup instead.
    LaunchedEffect(showFirstRun, showMenu) { if (showFirstRun && showMenu) showMenu = false }

    // (Update 231, #81: the classic details window's Performance button: this game's quick power settings.)
    var perfForGame by remember { mutableStateOf<com.shelf.launcher.data.Game?>(null) }
    // ONE list of every overlay that takes the controller from the root, so
    // "is a dialog open?" and "B closes them all" can never drift apart.
    // (The details window is deliberately absent: it lives inside the root.)
    val otherDialogOpen = showAppDrawer || showFirstRun || showSettings || showMenu || showEmulatorSettings || showIgdb || showRawg ||
                    showNotifications || showSpotify || showMusicPopup || showSyncStatus || showSaveExplorer || showShortcuts || showAppsLibrary || showTgdb || showScreenScraper ||
                    optionsForGame != null || showLibraryDialog || showAddSteamGameDialog ||
                    editingArtworkForGame != null ||
                    showAchievementsDialog || configuringPlatform != null ||
                    configuringArtForGame != null || configuringRaForGame != null ||
                    viewingAchievementsForGame != null || pickingMissingSourceFor != null ||
                    pbState.options != null || musicOverrideFor != null ||
                    collectionsForGame != null || statusForGame != null || playStatsForGame != null || tipsForGame != null || guideForGame != null || viewingShots != null || artViewer != null ||
                    screensForGame != null || profileForGame != null || showControlsHelp || showSortDialog || showWhatToPlay || manualFor != null || steamAchFor != null || leaderboardsFor != null || uiState.syncOfflinePrompt != null ||
                    saveSyncForGame != null || runningAsk != null || yearRecap != null || manualMissing != null || hubScreen != null ||
                    perfForGame != null
    // Google Drive sync conflicts: always shown while a game waits on them,
    // otherwise only when nothing else is open (and not snoozed).
    val cloudSync by com.shelf.launcher.data.CloudSync.state.collectAsState()
    val syncConflictShowing = cloudSync.conflicts.isNotEmpty() && !cloudSync.running &&
        (uiState.gameWaitingOnConflicts != null || (!cloudSync.conflictsSnoozed && !otherDialogOpen))
    val aDialogIsOpen = otherDialogOpen || syncConflictShowing
    // Edit Home opened from Settings or a menu: everything over the home screen steps aside.
    LaunchedEffect(HomeEditState.active) { if (HomeEditState.active) { showSettings = false; showMenu = false; showNotifications = false } }
    // Battery watcher: the once-a-minute measuring starts with the home screen (it stays quiet when switched off).
    val batteryWatchContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { com.shelf.launcher.data.BatteryWatch.start(batteryWatchContext) }
    // Update 112: the animations follow the layout on screen (its own choices, Home › Animations › per layout).
    SideEffect { MotionPrefs.layout = state.appearance.homeLayout }
    // Update 292: what's on screen, for the freeze snapshot of a crash report (FreezeWatchdog).
    SideEffect {
        com.shelf.launcher.data.crash.FreezeWatchdog.state("homeLayout", state.appearance.homeLayout.name)
        com.shelf.launcher.data.crash.FreezeWatchdog.state("screensaverOn", screensaverOn)
        com.shelf.launcher.data.crash.FreezeWatchdog.state("settingsOpen", showSettings)
        com.shelf.launcher.data.crash.FreezeWatchdog.state("menuOpen", showMenu)
    }
    // Update 159: each game's key by its cover (a 3D case finds the game's scanned case front from it, Model3DArt).
    val coverKeys = remember(state.art, state.artOverrides) {
        val m = HashMap<String, String>(state.art.size * 2)
        for ((k, a) in state.art) a.gridUrl?.let { m[it] = k }
        for ((k, o) in state.artOverrides) o.cover?.let { m[it] = k }
        m
    }
    // Update 287: release years, for a DS game's default case (DsCases) — only the DS games'.
    LaunchedEffect(state.metadata) {
        val ds = state.games.filter { it.platform == "NDS" }.mapNotNull { g -> state.metadata[g.stableKey]?.releaseYear?.let { g.stableKey to it } }.toMap()
        if (ds != DsCases.years) { DsCases.years = ds; DsCases.changed() }
    }
    // Update 113: the games' scans, for the Spines look's own spines (read only where a spine is drawn).
    SideEffect {
        if (HomeScans.map !== state.physicalMedia) HomeScans.map = state.physicalMedia
        HomeScans.need = { g -> viewModel.loadPhysicalMedia(g, needSpine = true) }
        HomeScans.seen = { keys -> viewModel.markScansSeen(keys) }
        HomeScans.cover = { k -> state.artOverrides[k]?.cover ?: state.art[k]?.gridUrl }
        HomeScans.keyOfCover = coverKeys
    }
    // (Update 235, #15/#16: Update 112's redirect stood here — a saved Tile home became the Voltcap row and switched the
    // Square tile look on for everyone. Tile home is a layout again and opens as itself; homes already moved stay as
    // they are, and the Square tile look is only on when you pick it.)
    // "Who is playing?" once per run, after the intro, when two RetroAchievements accounts are saved and it is switched on.
    var whoAsk by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!WhoPlaying.asked && com.shelf.launcher.data.Features.whoPlays.value && viewModel.raAccountNames().count { it != null } >= 2) {
            StartupGate.awaitIntro()
            whoAsk = true
        }
    }
    if (whoAsk) {
        WhoIsPlayingScreen(
            names = state.raAccounts, active = state.raActiveSlot,
            onPick = { WhoPlaying.asked = true; whoAsk = false; viewModel.switchRaAccount(it) },
            onSkip = { WhoPlaying.asked = true; whoAsk = false }
        )
    }
    // Update 131: the extra art packs (data/Packs.kt) — asked once after the first-time setup; with "all" or "only my
    // consoles" the missing pieces (a console added to the library, a download that failed) follow a few seconds after start.
    val packContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { com.shelf.launcher.data.Packs.loadAsked(packContext) }
    // Update 202: Update 141's start check and its toast moved to UpdatePromptHost (further down, with the other
    // questions): it checks a little after start and every 12 hours, and asks "Voltcap x is out" instead of a toast.
    LaunchedEffect(state.availablePlatforms) {
        val consoles = state.availablePlatforms.toSet()
        com.shelf.launcher.data.Packs.libraryConsoles = consoles
        val choice = com.shelf.launcher.data.Packs.choice(packContext)
        if (choice != null && choice != com.shelf.launcher.data.Packs.Choice.NONE && consoles.isNotEmpty()) {
            kotlinx.coroutines.delay(5000)
            com.shelf.launcher.data.Packs.download(packContext, choice, consoles)
        }
    }
    // Update 132: the Trophies category is the Cross menu's own; leaving that layout while in it goes back to All.
    val trophyCatOn = com.shelf.launcher.data.Features.crossTrophyCat.state.value
    LaunchedEffect(state.appearance.homeLayout, state.specialFilter, trophyCatOn) {
        if (state.specialFilter == com.shelf.launcher.SpecialFilter.TROPHIES &&
            (state.appearance.homeLayout != com.shelf.launcher.data.HomeLayout.CROSS || !trophyCatOn)
        ) viewModel.setPlatformFilter(null)
    }
    val packsAskOpen = !com.shelf.launcher.data.Packs.asked.value && !showFirstRun && !whoAsk && state.games.isNotEmpty()
    if (packsAskOpen) {
        PacksFirstAsk(onDone = {})
    }
    // "Replay the Volt guide": the first-time setup with Volt again.
    LaunchedEffect(VoltHub.replay) { if (VoltHub.replay) { VoltHub.replay = false; showSettings = false; showMenu = false; showFirstRun = true } }
    fun closeAllDialogs() {
        viewingAchievementsForGame = null
        CrossTrophyView.full = false
        hubScreen = null
        configuringRaForGame = null
        configuringArtForGame = null
        configuringPlatform = null
        showAchievementsDialog = false
        showAddSteamGameDialog = false
        showLibraryDialog = false
        showSettings = false
        showMenu = false
        showEmulatorSettings = false
        showIgdb = false
        showRawg = false
        showTgdb = false
        showScreenScraper = false
        showNotifications = false
        showSpotify = false
        showMusicPopup = false
        showSyncStatus = false
        showSaveExplorer = false
        showShortcuts = false
        showAppsLibrary = false
        showAppDrawer = false
        showFirstRun = false
        showControlsHelp = false
        showSortDialog = false
        showWhatToPlay = false
        manualFor = null
        steamAchFor = null
        leaderboardsFor = null
        optionsForGame = null
        editingArtworkForGame = null
        pickingMissingSourceFor = null
        collectionsForGame = null
        screensForGame = null
        profileForGame = null
        statusForGame = null
        playStatsForGame = null
        tipsForGame = null
        if (guideForGame != null) viewModel.closeGuide()
        guideForGame = null
        viewingShots = null
        artViewer = null
        saveSyncForGame = null
        perfForGame = null
    }
    // (Update 261: the game a test walk starts from — its console's category, focused on it.)
    // Update 274 (testing): a game's piece out on a given shell, held at an angle (TestHooks.inspect).
    val testInspect = TestHooks.inspect.value
    LaunchedEffect(testInspect, state.games.size) {
        val v = testInspect ?: return@LaunchedEffect
        val parts = v.split('|')
        val g = state.games.firstOrNull { it.name.contains(parts[0].replace('_', ' '), ignoreCase = true) } ?: run { TestHooks.inspect.value = null; return@LaunchedEffect }
        GamePopup.game = null
        kotlinx.coroutines.delay(400)
        MediaInspect.testShell = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
        MediaInspect.testPose = floatArrayOf(parts.getOrNull(2)?.toFloatOrNull() ?: 0f, parts.getOrNull(3)?.toFloatOrNull() ?: 0f)
        MediaInspect.forceSeq++
        MediaInspect.force = g.stableKey
        GamePopup.game = g
        TestHooks.inspect.value = null
    }
    // (Update 305, testing only: TEST packaging facts for a game this session — TestHooks.facts.)
    val testFacts = TestHooks.facts.value
    LaunchedEffect(testFacts, state.games.size) {
        val v = testFacts ?: return@LaunchedEffect
        val parts = v.split('|')
        val g = state.games.firstOrNull { it.name.contains(parts[0].replace('_', ' '), ignoreCase = true) }
        if (g != null) {
            val ratings = parts.drop(1).mapNotNull { r -> com.shelf.launcher.data.PackagingFactsStore.rating(r.substringBefore(' '), r.substringAfter(' ', ""), "test") }
            com.shelf.launcher.data.PackagingFactsStore.put(g.stableKey, com.shelf.launcher.data.PackagingFacts(
                ratings = ratings, publisher = "Test Publisher", features = listOf("Single-player", "Full controller support"), source = "test"))
            android.util.Log.e("VoltcapTest", "facts for ${g.name}: ${ratings.map { it.label }}")
        }
        TestHooks.facts.value = null
    }
    // (Update 305, testing only: a picture added to / removed from a game's real-media slot — TestHooks.pick.)
    val testPick = TestHooks.pick.value
    val testCtx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(testPick, state.games.size) {
        val v = testPick ?: return@LaunchedEffect
        val parts = v.split('|')
        val g = state.games.firstOrNull { it.name.contains(parts[0].replace('_', ' '), ignoreCase = true) }
        if (g != null && parts.size >= 3) {
            val label = parts[1]
            val url = if (parts[2] == "clear") null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.shelf.launcher.data.UserArt.importLocal(testCtx, android.net.Uri.fromFile(java.io.File(parts[2])))
            }
            viewModel.setPhysicalOverride(g, label, url)
            android.util.Log.e("VoltcapTest", "pick ${g.name} / $label -> $url")
        }
        TestHooks.pick.value = null
    }
    // (Update 301, testing only: a game's Artwork & details — TestHooks.artwork.)
    val testArtwork = TestHooks.artwork.value
    LaunchedEffect(testArtwork, state.games.size) {
        val v = testArtwork ?: return@LaunchedEffect
        val g = state.games.firstOrNull { it.name.contains(v, ignoreCase = true) } ?: return@LaunchedEffect
        editingArtworkForGame = g
        TestHooks.artwork.value = null
    }
    val testGame = TestHooks.game.value
    LaunchedEffect(testGame) {
        val q = testGame ?: return@LaunchedEffect
        val g = state.games.firstOrNull { it.name.contains(q, ignoreCase = true) } ?: run { TestHooks.game.value = null; return@LaunchedEffect }
        closeAllDialogs(); categoryScreenOpen = false; showDetails = false; StyleNav.reset()
        viewModel.setSearchQuery("")
        viewModel.setPlatformFilter(g.platform)
        kotlinx.coroutines.delay(400)
        val i = viewModel.uiState.value.visibleGames.indexOfFirst { it.stableKey == g.stableKey }
        if (i >= 0) viewModel.setFocus(i)
        TestHooks.game.value = null
    }
    // (0.6.15: the Sort window for a test run.)
    if (TestHooks.sort.value) LaunchedEffect(Unit) { closeAllDialogs(); showSortDialog = true; TestHooks.sort.value = false }
    // (0.6.15: a category for a test run — "ALL" = All games, else a console's name.)
    val testCategory = TestHooks.category.value
    LaunchedEffect(testCategory) {
        val c = testCategory ?: return@LaunchedEffect
        closeAllDialogs(); categoryScreenOpen = false; showDetails = false; StyleNav.reset()
        viewModel.setSearchQuery("")
        viewModel.setPlatformFilter(if (c.equals("ALL", ignoreCase = true)) null else c)
        kotlinx.coroutines.delay(300)
        viewModel.setFocus(0)
        TestHooks.category.value = null
    }
    // (Update 261: a game look for this session only, for a test walk — nothing saved.)
    val testLook = TestHooks.look.value
    LaunchedEffect(testLook) {
        val name = testLook ?: return@LaunchedEffect
        com.shelf.launcher.data.CardArtType.values().firstOrNull { it.name == name }?.let { t ->
            TestHooks.previewOnly = true
            viewModel.previewAppearance(state.appearance.copy(cardArtType = t))
        }
        TestHooks.look.value = null
    }
    // (Update 261: a Settings page asked for by a test walk.)
    val testSettings = TestHooks.settings.value
    LaunchedEffect(testSettings) {
        val cat = testSettings ?: return@LaunchedEffect
        closeAllDialogs(); categoryScreenOpen = false; showDetails = false
        kotlinx.coroutines.delay(200)
        menuStartCategory = cat; showMenu = true
        // (Cleared last: clearing it first restarted this effect and cancelled it in its delay.)
        TestHooks.settings.value = null
    }
    // (Update 280: Game look › Layout standard — a layout switched to brings its own standard look.)
    val lookFromLayoutOn = com.shelf.launcher.data.Features.lookFromLayout.state.value
    LaunchedEffect(state.appearance.homeLayout, lookFromLayoutOn) {
        if (!lookFromLayoutOn) return@LaunchedEffect
        layoutStandardIfDue(viewModel.uiState.value.appearance)?.let { t ->
            if (TestHooks.previewOnly) viewModel.previewAppearance(t) else viewModel.setAppearance(t)
        }
    }
    // (Update 261: a layout asked for by a test walk over adb — TestHooks.kt, off without its flag file.)
    val testLayout = TestHooks.layout.value
    LaunchedEffect(testLayout) {
        val name = testLayout ?: return@LaunchedEffect
        TestHooks.layout.value = null
        com.shelf.launcher.data.HomeLayout.values().firstOrNull { it.name == name }?.let { l ->
            closeAllDialogs(); categoryScreenOpen = false; showDetails = false; StyleNav.reset()
            if (TestHooks.previewOnly) viewModel.previewAppearance(state.appearance.copy(homeLayout = l))
            else viewModel.setAppearance(state.appearance.copy(homeLayout = l))
        }
    }

    fun runDetailsAction(action: DetailsAction, game: com.shelf.launcher.data.Game) {
        when (action) {
            DetailsAction.PLAY -> { showDetails = false; startGame(game) }
            DetailsAction.FAVORITE -> viewModel.toggleFavorite(game)
            DetailsAction.ACHIEVEMENTS ->
                // Opens / closes the compact list right under the bar (Y opens the full window).
                if (game.raGameId != null) {
                    val list = inlineAchievementsShown(state.raAchievementDetails[game.stableKey].orEmpty(), achInlineFilter)
                    val row = list.getOrNull(achInlineIndex)
                    if (achInlineOpen && row != null) {
                        // A on a row: open / close it right there (details + comments).
                        achInlineExpanded = if (achInlineExpanded == row.id) null else row.id
                        if (achInlineExpanded != null) viewModel.loadAchievementComments(row.id)
                    } else {
                        achInlineOpen = !achInlineOpen
                        achInlineIndex = -1
                        achInlineExpanded = null
                    }
                }
            // First press fetches from every source; after that it lets you
            // pick one source to try, when something is still missing.
            DetailsAction.ADD_MISSING ->
                if (viewModel.missingSourcesFor(game, uiState).isNotEmpty() &&
                    viewModel.missingFor(game, uiState).isEmpty()
                ) pickingMissingSourceFor = game
                else viewModel.fillMissing(game)
            DetailsAction.TIPS -> if (game.raGameId?.let { state.raGameTips[it] } != null) tipsForGame = game
            DetailsAction.GUIDE -> { guideForGame = game; viewModel.openGuide(game) }
            DetailsAction.MANUAL -> viewModel.openManual(game, onReady = { file -> manualFor = game to file }, onMissing = { manualMissing = game })
            DetailsAction.STEAM_ACHIEVEMENTS -> steamAchFor = game
            DetailsAction.LEADERBOARDS -> leaderboardsFor = game
            DetailsAction.FIX_RA_LINK -> configuringRaForGame = game
            DetailsAction.SYNC -> if (detailsSyncAvailable) saveSyncForGame = game
            DetailsAction.GALLERY -> if (galleryItems.isNotEmpty()) {
                artViewer = galleryItems to galleryIndex.coerceIn(0, galleryItems.lastIndex)
            }
            DetailsAction.SUMMARY -> summaryExpanded = !summaryExpanded
            DetailsAction.OPTIONS -> optionsForGame = game
            // More: the secondary actions open as a row under the buttons (▼ goes into it, B closes it).
            DetailsAction.MORE -> DetailsMore.open.value = !DetailsMore.open.value
            DetailsAction.STATS -> playStatsForGame = game
            // (Update 231, #81: this game's own quick performance settings, over the window.)
            DetailsAction.PERFORMANCE -> perfForGame = game
            // (Update 285, Toni: "every popup an option to inspect the media": the game window with its piece out, whatever
            // the look draws — as the menu's Inspect….)
            DetailsAction.INSPECT -> if (game.sourceType != com.shelf.launcher.data.SourceType.APP) {
                showDetails = false
                MediaInspect.testShell = null; MediaInspect.testPose = null; MediaInspect.forceSeq++; MediaInspect.force = game.stableKey
                GamePopup.game = game
            }
            DetailsAction.CLOSE -> showDetails = false
        }
    }

    // A real dialog on top takes the stick's events, so the last value would
    // otherwise keep scrolling the window behind it.
    LaunchedEffect(aDialogIsOpen) { if (aDialogIsOpen) stickY = 0f }

    // When a popup closes on a button's key-down (B in the achievements list,
    // say), that button's key-up still arrives here afterwards — and B here
    // would close the details window too. Key-ups for presses that began
    // before the last popup closed are therefore swallowed.
    LaunchedEffect(aDialogIsOpen) { if (!aDialogIsOpen) dialogClosedAt = android.os.SystemClock.uptimeMillis() }

    // Music buttons (Settings › Appearance › Music controls): Select held
    // plus a button, or a stick press.
    val musicKeysDown = remember { mutableSetOf<Int>() }
    // Forget every held-key / combo bookkeeping. Called whenever key-ups could
    // be lost to another window (dialog, game, lifecycle change, focus loss),
    // so a stale "Select is held" can never turn the next plain press into a combo.
    // When a hotkey opened the controls card (its key-up is ignored there).
    val controlsHelpOpenedAt = remember { longArrayOf(0L) }
    fun resetKeyState() {
        selectHeld = false
        selectComboUsed = false
        musicKeysDown.clear()
        heldRepeat = false
        dockKeysDown.clear()
    }
    LaunchedEffect(aDialogIsOpen) { resetKeyState() }
    val windowFocused = androidx.compose.ui.platform.LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) { if (!windowFocused) resetKeyState() }
    DisposableEffect(lifecycleOwner) {
        val resetObserver = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE ||
                event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) resetKeyState()
        }
        lifecycleOwner.lifecycle.addObserver(resetObserver)
        onDispose { lifecycleOwner.lifecycle.removeObserver(resetObserver) }
    }
    fun runMusic(action: com.shelf.launcher.data.MusicAction) {
        when (action) {
            com.shelf.launcher.data.MusicAction.PLAY_PAUSE -> viewModel.mediaPlayPause()
            com.shelf.launcher.data.MusicAction.NEXT -> viewModel.mediaNext()
            com.shelf.launcher.data.MusicAction.PREVIOUS -> viewModel.mediaPrevious()
            com.shelf.launcher.data.MusicAction.SEEK_BACK, com.shelf.launcher.data.MusicAction.SEEK_FORWARD -> {
                val playing = state.nowPlaying
                // Unknown length (0): clamping to it jumped back to the start (update 91).
                if (playing != null && playing.canSeek && playing.durationMs > 0) {
                    val delta = if (action == com.shelf.launcher.data.MusicAction.SEEK_FORWARD) 10_000L else -10_000L
                    viewModel.mediaSeekTo((playing.currentPosition() + delta).coerceIn(0L, playing.durationMs))
                }
            }
        }
        sounds?.play(UiSound.SELECT)
    }

    // Category screen (every category as a tile): B opens it, or it greets
    // you at start (Appearance › Game row).
    // Held by the ViewModel: survives activity recreation and is restored after a game.
    // Your own pictures for category tiles (Y on the category screen).
    val tileContext = androidx.compose.ui.platform.LocalContext.current
    val categoryImagesStore = remember { com.shelf.launcher.data.CategoryImagesStore(tileContext) }
    val pickTileImage = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        val key = pickingImageFor
        pickingImageFor = null
        if (uri != null && key != null) {
            runCatching {
                tileContext.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            categoryImagesStore.set(key, uri.toString())
            categoryImages = categoryImagesStore.loadAll()
        }
    }
    val fullNames = state.appearance.fullConsoleNames
    // Worked out off the main thread, and only while the category screen is open
    // (or once, so it is ready the first time it opens). Each category's list
    // comes from the state's memoized visibleGames.
    val tilesWanted = categoryScreenOpen || categoryTiles.isEmpty()
    LaunchedEffect(
        tilesWanted, state.games, state.categoryOrder, state.availablePlatforms, state.art, state.artOverrides,
        fullNames, categoryImages, state.favorites, state.lastPlayedAt, state.playtime, state.collections,
        com.shelf.launcher.data.PlatformOrder.version.value, com.shelf.launcher.data.CategoryTileOrder.version.value
    ) {
        if (!tilesWanted) return@LaunchedEffect
        // Art arrives in bursts while scraping: let a burst settle first.
        if (categoryTiles.isNotEmpty()) kotlinx.coroutines.delay(150)
        val snapshot = state
        categoryTiles = withContext(Dispatchers.Default) { buildCategoryTiles(snapshot, fullNames, categoryImages) }
    }
    // The category showing now, as a category tile's key ("ALL", a special filter's name, "p:<platform or collection>").
    val currentCategoryKey = when {
        state.specialFilter != null -> state.specialFilter.name
        state.platformFilter != null -> "p:" + state.platformFilter
        else -> "ALL"
    }
    fun openCategoryScreen() {
        val current = currentCategoryKey
        categoryIndex = categoryTiles.indexOfFirst { it.key == current }.coerceAtLeast(0)
        categoryScreenOpen = true
        sounds?.play(UiSound.OPEN)
    }
    // Shows a category (from the category screen, or the second screen's Wall).
    fun applyCategory(tile: CategoryTile) {
        when {
            tile.key == "ALL" -> { viewModel.setSpecialFilter(null); viewModel.setPlatformFilter(null) }
            tile.key.startsWith("p:") -> viewModel.setPlatformFilter(tile.key.removePrefix("p:"))
            else -> runCatching { SpecialFilter.valueOf(tile.key) }.getOrNull()?.let { viewModel.setSpecialFilter(it) }
        }
    }
    fun pickCategory(index: Int) {
        val tile = categoryTiles.getOrNull(index) ?: return
        applyCategory(tile)
        categoryScreenOpen = false
        sounds?.play(UiSound.SELECT)
    }
    // Platform browser, Grid view: a platform dragged / moved to another place (remembered by PlatformOrder).
    fun reorderPlatforms(from: Int, to: Int) {
        val a = categoryTiles.getOrNull(from)?.platform ?: return
        val b = categoryTiles.getOrNull(to)?.platform ?: return
        val platforms = categoryTiles.mapNotNull { it.platform }
        com.shelf.launcher.data.PlatformOrder.move(platforms, platforms.indexOf(a), platforms.indexOf(b))
        // (Update 214: with the tiles in your own order, that order follows too — else the move wouldn't show.)
        if (com.shelf.launcher.data.CategoryTileOrder.snapshot().isNotEmpty()) com.shelf.launcher.data.CategoryTileOrder.move(categoryTiles.map { it.key }, from, to)
        categoryTiles = categoryTiles.toMutableList().also { it.add(to, it.removeAt(from)) }
    }
    // Update 208: Choose a category › Reorder — the tile at [from] to [to]. True when something moved.
    // (Update 214: every tile on its own — CategoryTileOrder — a console, Android games, Other apps, Continue playing, All
    // games, a collection… It used to move the whole block it belonged to (all the consoles, all the collections) past
    // the next one, and two tiles of the same kind of special filter couldn't pass each other. Two consoles still keep
    // your console order (PlatformOrder) in step for the views that sort the consoles by it.)
    var categoryFollowKey by remember { mutableStateOf<String?>(null) }
    fun moveCategoryTile(from: Int, to: Int): Boolean {
        val a = categoryTiles.getOrNull(from) ?: return false
        val b = categoryTiles.getOrNull(to) ?: return false
        if (from == to) return false
        if (a.platform != null && b.platform != null) {
            val platforms = categoryTiles.mapNotNull { it.platform }
            com.shelf.launcher.data.PlatformOrder.move(platforms, platforms.indexOf(a.platform), platforms.indexOf(b.platform))
        }
        com.shelf.launcher.data.CategoryTileOrder.move(categoryTiles.map { it.key }, from, to)
        categoryTiles = categoryTiles.toMutableList().also { it.add(to, it.removeAt(from)) }
        categoryIndex = to
        // (The tiles are worked out again from the new order; the one you're on is found again by its key.)
        categoryFollowKey = a.key
        return true
    }
    /** Update 214: B with a tile picked up — everything back where it was when it was picked up, still on that tile. */
    fun cancelCategoryMove() {
        categoryFollowKey = categoryTiles.getOrNull(categoryIndex)?.key
        CategoryReorder.cancel()
    }
    LaunchedEffect(categoryTiles) {
        val k = categoryFollowKey ?: return@LaunchedEffect
        val i = categoryTiles.indexOfFirst { it.key == k }
        if (i >= 0) { categoryIndex = i; categoryFollowKey = null }
    }
    LaunchedEffect(categoryScreenOpen) { if (!categoryScreenOpen) CategoryReorder.stop() }

    // The random-game button: go to that category, then pick from it once the games are showing.
    fun randomFromBrowser() {
        val tile = categoryTiles.getOrNull(categoryIndex) ?: return
        pbRandomFor = tile.key
        pickCategory(categoryIndex)
    }

    // Hotkeys (Settings › Controls): a button or Select + button that does something.
    val hotkeyContext = androidx.compose.ui.platform.LocalContext.current
    // Lyrics are opt-in: load the stored choice (off unless the user turned it on).
    // Screen Mirror (second screen's Widget Board): Android's capture prompt shows here, on the main screen.
    val mirrorLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { r ->
        val data = r.data
        if (r.resultCode == android.app.Activity.RESULT_OK && data != null) ScreenMirror.begin(hotkeyContext, r.resultCode, data)
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        ScreenMirror.onRequest = { runCatching { mirrorLauncher.launch(ScreenMirror.consentIntent(hotkeyContext)) } }
        onDispose { ScreenMirror.onRequest = null }
    }
    // Platform browser: your platform order, scraped platform media, and the slide layout you designed.
    LaunchedEffect(Unit) {
        com.shelf.launcher.data.PlatformInfo.init(hotkeyContext)
        com.shelf.launcher.data.PlatformOrder.init(hotkeyContext)
        com.shelf.launcher.data.CategoryTileOrder.init(hotkeyContext)
    }
    LaunchedEffect(com.shelf.launcher.data.Features.pbSlideLayout.state.value) { if (!pbState.editing) pbState.loadLayout() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.shelf.launcher.data.LyricsOptIn.init(hotkeyContext) }
        // "Now playing" theme: its settings, and the last song's colours so it doesn't open in the fallback.
        com.shelf.launcher.data.MusicThemeSettings.init(hotkeyContext)
        MusicTheme.init(hotkeyContext)
    }
    // Console logos for the category bar (saved list; refreshed monthly when RetroAchievements is connected).
    LaunchedEffect(state.raUsername) {
        runCatching { com.shelf.launcher.data.ConsoleLogos.load(hotkeyContext) }
    }
    // Button legend names (Xbox / Nintendo / PlayStation), read by every GlyphPill.
    androidx.compose.runtime.SideEffect {
        ButtonGlyphStyle.current = state.appearance.buttonGlyphs
        ButtonGlyphStyle.layout = state.appearance.buttonLayout
    }
    // "Now playing" theme: hand it the playing track; it takes the colours from the cover.
    // (Also when its scene is picked to play behind another theme.)
    val musicThemeOn = state.appTheme.motif.reactive ||
        com.shelf.launcher.ui.theme.AppTheme.values().any { it.motif.reactive && it.name == state.appearance.sceneTheme }
    // (Also for lyrics "away from the cover's subject", which needs the cover looked at.)
    val coverWanted = musicThemeOn || state.appearance.lyricsPosition == com.shelf.launcher.data.LyricsPosition.AWAY
    LaunchedEffect(coverWanted, state.nowPlaying) {
        if (coverWanted) MusicTheme.update(hotkeyContext, state.nowPlaying)
    }
    fun runHotkey(hotkey: com.shelf.launcher.data.Hotkey) {
        when (hotkey.action) {
            com.shelf.launcher.data.HotkeyAction.OFF -> return
            com.shelf.launcher.data.HotkeyAction.OPEN_APP -> {
                val pkg = hotkey.packageName ?: return
                viewModel.launchAppByPackage(pkg, hotkey.appLabel)
            }
            com.shelf.launcher.data.HotkeyAction.PLAY_SPOTIFY -> viewModel.playPauseSpotify()
            com.shelf.launcher.data.HotkeyAction.OPEN_SPOTIFY -> showSpotify = true
            com.shelf.launcher.data.HotkeyAction.TOGGLE_MUSIC_PLAYER ->
                viewModel.setAppearance(state.appearance.copy(miniPlayer = !state.appearance.miniPlayer))
            com.shelf.launcher.data.HotkeyAction.LAST_GAME -> {
                val last = state.games.maxByOrNull { state.lastPlayedAt[it.stableKey] ?: 0L }
                    ?.takeIf { (state.lastPlayedAt[it.stableKey] ?: 0L) > 0L }
                if (last != null) startGame(last)
                else viewModel.setStatusMessage("Nothing played yet.")
                return
            }
            com.shelf.launcher.data.HotkeyAction.CATEGORIES -> { openCategoryScreen(); return }
            com.shelf.launcher.data.HotkeyAction.SEARCH -> showSearchBar = !showSearchBar
            com.shelf.launcher.data.HotkeyAction.NOTIFICATIONS -> { viewModel.refreshNotifications(); notificationsOnly = true; showNotifications = true }
            com.shelf.launcher.data.HotkeyAction.SHORTCUTS -> showShortcuts = true
            com.shelf.launcher.data.HotkeyAction.SCREENSAVER -> screensaverOn = true
            com.shelf.launcher.data.HotkeyAction.APP_DRAWER -> showAppDrawer = true
            // "Can't decide": something unplayed or not finished from the
            // category you're in — selected, with its details open.
            com.shelf.launcher.data.HotkeyAction.RANDOM_GAME -> {
                val visible = state.visibleGames
                val unfinished = visible.indices.filter { i ->
                    val g = visible[i]
                    val award = g.raGameId?.let { com.shelf.launcher.data.RaAward.from(state.raCompletionById[it]?.HighestAwardKind) }
                    val st = if (state.appearance.playStatus) state.playStatuses[g.stableKey]?.status else null
                    award != com.shelf.launcher.data.RaAward.MASTERED && award != com.shelf.launcher.data.RaAward.COMPLETED &&
                        award != com.shelf.launcher.data.RaAward.BEATEN && award != com.shelf.launcher.data.RaAward.BEATEN_HARDCORE &&
                        st != com.shelf.launcher.data.PlayStatus.FINISHED && st != com.shelf.launcher.data.PlayStatus.DROPPED
                }.filter { it != state.focusedIndex }
                val pick = unfinished.randomOrNull() ?: visible.indices.filter { it != state.focusedIndex }.randomOrNull()
                if (pick == null) {
                    viewModel.setStatusMessage("Nothing else to pick from here.")
                    return
                }
                viewModel.setFocus(pick)
                openDetails()
            }
            com.shelf.launcher.data.HotkeyAction.TOGGLE_LEGEND ->
                viewModel.setAppearance(state.appearance.copy(showLegend = !state.appearance.showLegend))
            com.shelf.launcher.data.HotkeyAction.SORT -> showSortDialog = true
            com.shelf.launcher.data.HotkeyAction.WHAT_TO_PLAY -> showWhatToPlay = true
            // L3 / R3's jobs on another button (devices without stick presses).
            com.shelf.launcher.data.HotkeyAction.QUICK_JUMP -> {
                val letters = state.visibleGames.map { com.shelf.launcher.quickJumpLetter(it.name) }.toSet()
                if (letters.isEmpty()) return
                val here = focusedGame?.let { com.shelf.launcher.quickJumpLetter(it.name) }
                // The strip takes every key from now on (this button's and Select's key-ups too), so the
                // combo bookkeeping is cleared here — or the next press would still count as held.
                resetKeyState()
                quickJump = here?.takeIf { it in letters } ?: QUICK_JUMP_LETTERS.first { it in letters }
                sounds?.play(UiSound.OPEN)
                return
            }
            com.shelf.launcher.data.HotkeyAction.CONTROLS_HELP -> {
                // Opened on key-down: the hotkey's own key-up must not close it again (see the card's key handling).
                controlsHelpOpenedAt[0] = android.os.SystemClock.uptimeMillis()
                showControlsHelp = true; sounds?.play(UiSound.OPEN); return
            }
            com.shelf.launcher.data.HotkeyAction.QUICK_MENU -> { viewModel.refreshNotifications(); notificationsOnly = false; showNotifications = true; sounds?.play(UiSound.OPEN); return }
            com.shelf.launcher.data.HotkeyAction.SETTINGS -> { menuStartCategory = null; showMenu = true; sounds?.play(UiSound.OPEN); return }
            com.shelf.launcher.data.HotkeyAction.VOLUME_MIXER -> {
                runCatching { hotkeyContext.startActivity(com.shelf.launcher.device.audio.AppMixer.openIntent(hotkeyContext)) }
                sounds?.play(UiSound.OPEN); return
            }
            com.shelf.launcher.data.HotkeyAction.PREVIOUS_CATEGORY -> { viewModel.cycleCategory(forward = false); CategorySlide.step(-1) }
            com.shelf.launcher.data.HotkeyAction.NEXT_CATEGORY -> { viewModel.cycleCategory(forward = true); CategorySlide.step(1) }
            com.shelf.launcher.data.HotkeyAction.TOGGLE_FAVORITE -> focusedGame?.let { viewModel.toggleFavorite(it) }
            com.shelf.launcher.data.HotkeyAction.GAME_OPTIONS -> focusedGame?.let { optionsForGame = it }
            com.shelf.launcher.data.HotkeyAction.GAME_DETAILS -> if (focusedGame != null) openDetails()
            com.shelf.launcher.data.HotkeyAction.MUSIC_PLAY_PAUSE -> viewModel.mediaPlayPause()
            com.shelf.launcher.data.HotkeyAction.MUSIC_NEXT -> viewModel.mediaNext()
            com.shelf.launcher.data.HotkeyAction.MUSIC_PREVIOUS -> viewModel.mediaPrevious()
            com.shelf.launcher.data.HotkeyAction.VIRTUAL_SECOND_SCREEN -> {
                if (com.shelf.launcher.data.Features.virtualSecondScreen.value) com.shelf.launcher.data.Features.virtualSecondScreenOpen.toggle()
                else viewModel.setStatusMessage("Switch on the virtual second screen first (Settings › Screens › Second screen tools).")
            }
            com.shelf.launcher.data.HotkeyAction.SECOND_SCREEN_PAGE -> {
                val pages = com.shelf.launcher.data.SecondPage.values().filter {
                    it != com.shelf.launcher.data.SecondPage.WALKTHROUGH &&
                        it != com.shelf.launcher.data.SecondPage.PERFORMANCE && it.available()
                }
                val next = pages[(pages.indexOf(state.appearance.secondPage) + 1).mod(pages.size)]
                viewModel.setAppearance(state.appearance.copy(secondPage = next))
            }
            com.shelf.launcher.data.HotkeyAction.RESTART_APP -> com.shelf.launcher.data.Backup.restart(hotkeyContext)
            com.shelf.launcher.data.HotkeyAction.TOGGLE_PERF_OVERLAY -> {
                val on = !com.shelf.launcher.data.perf.PerfSettings.config(hotkeyContext).showDuringGames
                com.shelf.launcher.data.perf.PerfSettings.update(hotkeyContext) { it.copy(showDuringGames = on) }
                android.widget.Toast.makeText(
                    hotkeyContext, if (on) "Performance overlay on" else "Performance overlay off", android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
        sounds?.play(UiSound.SELECT)
    }

    // Screensaver timer, controller lights, now-playing updates and root focus.
    @Composable
    fun AmbientEffects() {
    // Power saving while nobody touches the handheld (Appearance › Behind the dock › Save power when idle).
    SideEffect { HomeIdle.enabled = state.appearance.idleSaver }
    LaunchedWhileActive { HomeIdle.watch() }
    EffectClockDriver()
    BatterySaverDriver() // (Update 187)
    // Only while on screen: no screensaver during a game or with the display off.
    val screensaverActive = rememberIsActive()
    LaunchedEffect(lastInputAt, aDialogIsOpen, showSearchBar, state.screensaver, screensaverOn, screensaverActive, HomeEditState.active) {
        val settings = state.screensaver
        // (Update 125: never while Edit Home is open.)
        if (!screensaverActive || !settings.enabled || aDialogIsOpen || showSearchBar || screensaverOn || HomeEditState.active) return@LaunchedEffect
        kotlinx.coroutines.delay(settings.idleMinutes * 60_000L)
        screensaverOn = true
    }
    // Update 202: the screensaver dismissed: the background music fades back in at once, not after its 250 ms fade-out.
    LaunchedEffect(screensaverOn) { if (!screensaverOn) MusicBlackout.setSaverBlack(false) }

    LaunchedEffect(showMenu) { if (showMenu) viewModel.refreshArtStorageStats() }

    // Controller / stick RGB lights: Shelf's colour while it's on screen, dark
    // once the screensaver has faded to black. Re-applied on every return,
    // since Android hands the lights back to the system while a game runs.
    val lightsContext = androidx.compose.ui.platform.LocalContext.current
    var lightsDark by remember { mutableStateOf(false) }
    var lightsResumes by remember { mutableStateOf(0) }
    val lightsLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lightsLifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) lightsResumes++
        }
        lightsLifecycle.lifecycle.addObserver(observer)
        onDispose { lightsLifecycle.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(screensaverOn, state.screensaver.blackoutMinutes, state.appearance.lightsOffWithScreensaver) {
        lightsDark = false
        val minutes = state.screensaver.blackoutMinutes
        if (screensaverOn && minutes > 0 && state.appearance.lightsOffWithScreensaver) {
            kotlinx.coroutines.delay(minutes * 60_000L)
            lightsDark = true
        }
    }
    val lightsActive = rememberIsActive()
    // "Theme colour" for the lights: follow the accent, settled (not every animation frame).
    val accentArgb = ShelfTheme.accent.toArgb()
    var lightsAccent by remember { mutableStateOf(com.shelf.launcher.data.LightsTheme.accent) }
    LaunchedEffect(accentArgb) {
        kotlinx.coroutines.delay(400)
        com.shelf.launcher.data.LightsTheme.accent = accentArgb
        lightsAccent = accentArgb
    }
    val lightsHeldOff = com.shelf.launcher.data.Features.lightsHeldOff.state.value
    LaunchedEffect(
        state.appearance.lightsEnabled, state.appearance.lightsColor, state.appearance.lightsBrightness, lightsHeldOff,
        lightsDark, lightsResumes, lightsActive, state.appearance.lightsMusic && state.nowPlaying?.isPlaying == true,
        if (state.appearance.lightsColor == com.shelf.launcher.data.LIGHTS_THEME_COLOR) lightsAccent else 0
    ) {
        val a = state.appearance
        if (com.shelf.launcher.CrashGuard.safeMode) return@LaunchedEffect // safe mode: no controller lights
        // Only while on screen with the display on: no background wake-ups, nothing during a game.
        if (!lightsActive) return@LaunchedEffect
        // Light writes (sysfs / HID) are blocking I/O: off the main thread.
        // Theme colours are bright, saturated accents: on the sticks they glared, so they're dimmed.
        val lightLevel = if (a.lightsColor == com.shelf.launcher.data.LIGHTS_THEME_COLOR) (a.lightsBrightness * 45 / 100).coerceAtLeast(3) else a.lightsBrightness
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            // "Off": the handheld's own light setting goes off too (it kept putting its colour back);
            // anything else puts it back the way it was.
            // Update 200: the lights timer ("off with the screensaver") does the same while it holds them dark — the
            // Thor's firmware otherwise put its colour back on a stick — and puts the setting back when it ends.
            runCatching {
                if (a.lightsEnabled && (lightsHeldOff || lightsDark)) com.shelf.launcher.device.SystemLights.holdOff(lightsContext)
                else com.shelf.launcher.device.SystemLights.release(lightsContext)
            }
            com.shelf.launcher.data.ControllerLights.apply(
                lightsContext, a.lightsEnabled, on = !lightsDark && !lightsHeldOff, color = com.shelf.launcher.data.LightsTheme.resolve(a.lightsColor), brightness = lightLevel
            )
        }
        // The handheld's own firmware switches the stick lights off after a while, and AYN's system puts its
        // own colour back now and then (the Thor does it often), so Voltcap's choice — colour or Off — is
        // quietly re-sent while Voltcap is on screen: every 8 s on AYN (a one-way call, nearly free), 30 s
        // elsewhere. (Not while the lights pulse with the music: that loop owns them then.)
        val resendMs = if (com.shelf.launcher.device.RootShell.available) 8_000L else 30_000L
        // Update 200: also re-sent while the timer holds them dark with music playing (the pulse loop stops then,
        // so nothing kept them off and the firmware's colour came back).
        while (a.lightsEnabled && !(a.lightsMusic && !lightsHeldOff && !lightsDark && state.nowPlaying?.isPlaying == true)) {
            kotlinx.coroutines.delay(resendMs)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                if (lightsHeldOff || lightsDark) runCatching { com.shelf.launcher.device.SystemLights.holdOff(lightsContext) }
                com.shelf.launcher.data.ControllerLights.apply(
                    lightsContext, true, on = !lightsDark && !lightsHeldOff, color = com.shelf.launcher.data.LightsTheme.resolve(a.lightsColor), brightness = lightLevel
                )
            }
        }
    }

    // Update 189: the screen going off (timeout, power button) turns every stick light off — on the AYN Thor the
    // firmware switched only the right stick's off and the left one stayed lit with the screen dark — and the screen
    // coming on puts Voltcap's colour back (also while a game is in front). Only when Voltcap runs the lights.
    val lightsNow by androidx.compose.runtime.rememberUpdatedState(
        Triple(state.appearance.lightsEnabled && !com.shelf.launcher.CrashGuard.safeMode, !lightsDark && !lightsHeldOff,
            com.shelf.launcher.data.LightsTheme.resolve(state.appearance.lightsColor) to
                (if (state.appearance.lightsColor == com.shelf.launcher.data.LIGHTS_THEME_COLOR) (state.appearance.lightsBrightness * 45 / 100).coerceAtLeast(3) else state.appearance.lightsBrightness))
    )
    DisposableEffect(Unit) {
        val app = lightsContext.applicationContext
        // Update 308 (AYN Thor: the stick lights stayed dark after the screen went off and on): whether they were lit when
        // the screen went off is remembered, and on the AYN route they're put back once, a moment after the screen comes on
        // (after the firmware's own wake-up writes) — Voltcap's colour, or with System setting the handheld's own; never
        // switched on when they were off or you switched them off. Other handhelds: as before.
        val wasLit = java.util.concurrent.atomic.AtomicBoolean(true)
        val wakeGen = java.util.concurrent.atomic.AtomicInteger(0)
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context, i: android.content.Intent) {
                val (enabled, on, colorLevel) = lightsNow
                val off = i.action == android.content.Intent.ACTION_SCREEN_OFF
                val gen = wakeGen.incrementAndGet()
                Thread({
                    runCatching {
                        val wakeRoute = com.shelf.launcher.data.ControllerLights.restoresOnWake(app)
                        if (off && wakeRoute) wasLit.set(if (enabled) on else com.shelf.launcher.data.ControllerLights.systemLightsOn(app) == true)
                        if (wakeRoute && !off) {
                            Thread.sleep(1200)
                            // (Screen off again meanwhile: nothing.)
                            if (wakeGen.get() != gen) return@runCatching
                            val (en, onNow, cl) = lightsNow
                            com.shelf.launcher.data.ControllerLights.restoreAfterWake(app, wasLit.get(), en, onNow, cl.first, cl.second)
                        } else if (enabled) {
                            com.shelf.launcher.data.ControllerLights.apply(app, true, on = !off && on, color = colorLevel.first, brightness = colorLevel.second)
                        }
                    }
                }, "voltcap-lights-screen").start()
            }
        }
        runCatching {
            app.registerReceiver(receiver, android.content.IntentFilter().apply {
                addAction(android.content.Intent.ACTION_SCREEN_OFF); addAction(android.content.Intent.ACTION_SCREEN_ON)
            })
        }
        onDispose { runCatching { app.unregisterReceiver(receiver) } }
    }

    // "Lights pulse with the music": while music plays and Voltcap is on screen, the lights beat in the
    // cover's colour — to the real sound when the music scene is listening, else to a steady 112 bpm.
    val musicPulse = state.appearance.lightsEnabled && state.appearance.lightsMusic && !lightsDark && !lightsHeldOff &&
        state.nowPlaying?.isPlaying == true && !com.shelf.launcher.CrashGuard.safeMode
    LaunchedEffect(musicPulse, lightsActive) {
        if (!musicPulse || !lightsActive) return@LaunchedEffect
        val fallback = com.shelf.launcher.data.LightsTheme.resolve(state.appearance.lightsColor)
        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val start = android.os.SystemClock.elapsedRealtime()
                while (true) {
                    val now = android.os.SystemClock.elapsedRealtime()
                    val level = if (com.shelf.launcher.data.AudioReactive.live) com.shelf.launcher.data.AudioReactive.bass.coerceIn(0f, 1f)
                    else kotlin.math.exp(-(((now - start) % 536L) / 1000f) * 6f)
                    val color = MusicTheme.colors?.accent?.toArgb() ?: fallback
                    // Softer than full: the cover's colours on the sticks were too bright.
                    com.shelf.launcher.data.ControllerLights.pulse(lightsContext, color, (10 + 50 * level).toInt())
                    kotlinx.coroutines.delay(100)
                }
            }
        } finally {
            com.shelf.launcher.data.ControllerLights.endPulse()
        }
    }

    // Keep the playing track fresh while something is showing it.
    // Event-driven (MediaController callbacks), registered only while resumed with the screen on.
    NowPlayingUpdates(
        enabled = screensaverOn || showNotifications || state.appearance.miniPlayer ||
            musicThemeOn || (state.appearance.lightsEnabled && state.appearance.lightsMusic) ||
            com.shelf.launcher.data.Features.cornerViz.state.value != com.shelf.launcher.data.Features.CornerViz.OFF ||
            // (Update 229, #03: the Pocket ribbon's album-art top and the Pocket home strip only changed track on resume)
            (state.appearance.homeLayout == com.shelf.launcher.data.HomeLayout.POCKET_RIBBON &&
                com.shelf.launcher.data.Features.ribbonTop.state.value == com.shelf.launcher.data.Features.RibbonTop.ALBUM) ||
            ((state.appearance.homeLayout == com.shelf.launcher.data.HomeLayout.POCKET_RIBBON ||
                state.appearance.homeLayout == com.shelf.launcher.data.HomeLayout.POCKET_CLASSIC) && pocketStripOn())
    ) {
        viewModel.refreshNowPlaying()
    }

    // The root is the app's only focus target now, so it must hold focus
    // whenever no overlay does — Compose throws if a key arrives with nothing
    // focused.
    LaunchedEffect(Unit) {
        runCatching { rootFocusRequester.requestFocus() }
    }
    // Also whenever the window gets focus back (a popup window, Android's settings or a game closed):
    // a request made while the window was unfocused can be lost, and then the first button press
    // (Select for the menu, typically) only put focus back instead of doing anything.
    LaunchedEffect(aDialogIsOpen, showSearchBar, windowFocused) {
        if (!aDialogIsOpen && !showSearchBar && windowFocused) {
            kotlinx.coroutines.delay(16)
            runCatching { rootFocusRequester.requestFocus() }
        }
    }

    }
    AmbientEffects()

    // One definition of the details window, shown on this screen or the second one.
    @Composable
    fun DetailsWindow(game: com.shelf.launcher.data.Game, fillScreen: Boolean) {
        // The saved walkthrough place decides whether the button says "Continue guide" (local read only).
        LaunchedEffect(game.stableKey) { viewModel.peekGuide(game) }
        // Its newest saves start coming down from Google Drive now, quietly —
        // usually done before you press Play; Play never waits for it.
        LaunchedEffect(game.stableKey) { viewModel.prepareLaunch(game) }
        // "Add missing" goes away once it has run — don't leave the highlight
        // on a button that isn't there any more.
        val missingNow = remember(uiState, game.stableKey) { viewModel.missingFor(game, uiState) }
        val retrySources = remember(uiState, game.stableKey) { viewModel.missingSourcesFor(game, uiState) }
        val filling = game.stableKey in state.fillingMissing
        LaunchedEffect(missingNow.isEmpty(), filling, retrySources.isEmpty()) {
            if (detailsAction == DetailsAction.ADD_MISSING && missingNow.isEmpty() && !filling && retrySources.isEmpty()) {
                detailsAction = DetailsAction.PLAY
            }
        }
        GameDetailsOverlay(
            game = game,
            lastSummary = state.sessionSummary,
            art = displayArt[game.stableKey],
            artOverride = state.artOverrides[game.stableKey],
            metadata = state.metadata[game.stableKey],
            physicalMedia = state.physicalMedia[game.stableKey].orEmpty(),
            metadataLoading = game.stableKey in state.metadataLoading,
            metadataReady = state.metadataReady,
            raProgress = state.raProgress[game.stableKey],
            achievements = state.raAchievementDetails[game.stableKey] ?: emptyList(),
            achievementsLoading = game.stableKey in state.raAchievementDetailsLoading,
            lastPlayedAt = state.lastPlayedAt[game.stableKey],
            playtimeMs = state.playtime[game.stableKey],
            isFavorite = game.stableKey in state.favorites,
            scrollState = detailsScroll,
            selectedAction = detailsAction,
            onAction = { action ->
                detailsAction = action
                runDetailsAction(action, game)
            },
            fillScreen = fillScreen,
            screenshots = state.screenshots[game.stableKey].orEmpty(),
            videoUrl = state.videoUrls[game.stableKey],
            missing = missingNow.map { it.label },
            fillingMissing = filling,
            canRetryMissing = retrySources.isNotEmpty(),
            note = state.metadataNotes[game.stableKey]?.takeIf { game.stableKey in state.missingTried },
            sections = state.appearance.detailsSections,
            backdrop = state.appearance.detailsBackdrop,
            richPresence = state.raProfile?.takeIf { it.LastGameID != null && it.LastGameID == game.raGameId }?.RichPresenceMsg,
            tips = game.raGameId?.let { state.raGameTips[it] },
            achievementsNote = raDetailsNote(state, game.stableKey),
            raMatchInfo = com.shelf.launcher.data.ra.raMatchInfo(game.raGameId, state.raMatches[game.stableKey]),
            onFixRaLink = { configuringRaForGame = game },
            showTips = state.appearance.showTips,
            guideContinue = guideUi?.let { it.gameKey == game.stableKey && it.position != null } == true,
            playStatus = if (state.appearance.playStatus) state.playStatuses[game.stableKey]?.status else null,
            showOwnScreenshots = state.appearance.showScreenshots,
            screenshotFolders = state.appearance.screenshotFolders,
            onOpenScreenshot = { files, i -> viewingShots = files to i },
            galleryIndex = galleryIndex,
            onGalleryItems = { items ->
                galleryItems = items
                galleryIndex = galleryIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
                if (items.isEmpty() && detailsAction == DetailsAction.GALLERY) detailsAction = DetailsAction.PLAY
            },
            onOpenGallery = { items, i ->
                detailsAction = DetailsAction.GALLERY
                galleryIndex = i
                artViewer = items to i
            },
            summaryExpanded = summaryExpanded,
            syncAvailable = detailsSyncAvailable,
            syncStatus = detailsSyncStatuses[game.stableKey],
            achievementsExpanded = achInlineOpen,
            achievementsFilter = achInlineFilter,
            onAchievementsFilter = { achInlineFilter = it; achInlineIndex = -1 },
            achievementsSelected = achInlineIndex,
            achievementExpandedId = achInlineExpanded,
            onToggleAchievement = { a ->
                detailsAction = DetailsAction.ACHIEVEMENTS
                val list = inlineAchievementsShown(state.raAchievementDetails[game.stableKey].orEmpty(), achInlineFilter)
                achInlineIndex = list.indexOfFirst { it.id == a.id }
                achInlineExpanded = if (achInlineExpanded == a.id) null else a.id
                if (achInlineExpanded != null) viewModel.loadAchievementComments(a.id)
            },
            achievementComments = state.raComments,
            achievementCommentsLoading = state.raCommentsLoading,
            onOpenAchievementList = { achievementsShowMissing = false; viewingAchievementsForGame = game },
            raCompletion = game.raGameId?.let { state.raCompletionById[it] }
        )
    }

    val availableLetters = remember(state.visibleGames) {
        state.visibleGames.map { com.shelf.launcher.quickJumpLetter(it.name) }.toSet()
    }
    // Right stick: hold left/right to step through letters (with repeat),
    // showing the letter big in the middle for a moment.
    val stickDirection = when {
        rightStickX > 0f -> 1
        rightStickX < 0f -> -1
        else -> 0
    }
    val stickBlocked by rememberUpdatedState(aDialogIsOpen || showDetails || categoryScreenOpen)
    val stickOnMusic by rememberUpdatedState(miniFocus != null)
    val stickNowPlaying by rememberUpdatedState(state.nowPlaying)
    val stickLetters by rememberUpdatedState(availableLetters)
    val stickFocused by rememberUpdatedState(focusedGame)
    LaunchedEffect(stickDirection) {
        if (stickDirection == 0) {
            kotlinx.coroutines.delay(700)
            stickLetter = null
            return@LaunchedEffect
        }
        var seekTarget: Long? = null
        while (true) {
            // On the music player, the right stick moves through the song instead.
            val playing = stickNowPlaying
            if (stickOnMusic && !stickBlocked) {
                if (playing != null && playing.canSeek && playing.durationMs > 0) {
                    // Counted from our own target, since the reported position
                    // only refreshes once a second.
                    val next = ((seekTarget ?: playing.currentPosition()) + stickDirection * 5_000L)
                        .coerceIn(0L, playing.durationMs)
                    seekTarget = next
                    viewModel.mediaSeekTo(next)
                    sounds?.play(UiSound.MOVE)
                }
                kotlinx.coroutines.delay(250)
                continue
            }
            if (!stickBlocked && stickLetters.isNotEmpty()) {
                val here = stickLetter
                    ?: stickFocused?.let { com.shelf.launcher.quickJumpLetter(it.name) }
                    ?: QUICK_JUMP_LETTERS.first()
                val next = nextQuickJumpLetter(here, stickDirection, stickLetters)
                if (next != here || stickLetter == null) {
                    viewModel.jumpToLetter(next)
                    sounds?.play(UiSound.MOVE)
                }
                stickLetter = next
            }
            kotlinx.coroutines.delay(320)
        }
    }

    val awardsByKey = remember(state.games, state.raCompletionById) {
        state.games.mapNotNull { game ->
            game.raGameId
                ?.let { com.shelf.launcher.data.RaAward.from(state.raCompletionById[it]?.HighestAwardKind) }
                ?.let { game.stableKey to it }
        }.toMap()
    }
    // Mastered / completed games, for the shimmer on drawn cartridges / discs / cases in every layout.
    androidx.compose.runtime.LaunchedEffect(awardsByKey) {
        AwardShine.keys = awardsByKey.filterValues {
            it == com.shelf.launcher.data.RaAward.MASTERED || it == com.shelf.launcher.data.RaAward.COMPLETED
        }.keys
    }

    // Game states (Settings › Library › Game states): NEW / PLAYING / ON HOLD / COMPLETED / MASTERED / 100%.
    val statesOn = com.shelf.launcher.data.Features.gameStates.state.value
    val gameLooksRaw = com.shelf.launcher.data.Features.gameLooks.state.value
    val runningKeys = com.shelf.launcher.data.RunningGames.keys
    val gameStates = remember(statesOn, state.games, awardsByKey, state.raProgress, state.playStatuses, state.lastPlayedAt, runningKeys, state.firstSeen, gameLooksRaw) {
        if (!statesOn) emptyMap() else {
            val now = System.currentTimeMillis()
            // NEW: added in the last two weeks (the first scan's seeding doesn't count) and never played.
            val fresh = 14L * 24 * 60 * 60 * 1000
            state.games.mapNotNull { g ->
                val k = g.stableKey
                com.shelf.launcher.data.GameState.derive(
                    manual = com.shelf.launcher.data.GameLooks.of(k).state,
                    award = awardsByKey[k],
                    progress = state.raProgress[k],
                    status = state.playStatuses[k]?.status,
                    lastPlayed = state.lastPlayedAt[k],
                    running = k in runningKeys,
                    isNewArrival = state.firstSeen[k]?.let { now - it < fresh } == true,
                    now = now
                )?.let { k to it }
            }.toMap()
        }
    }
    SideEffect { if (GameStates.byKey != gameStates) GameStates.byKey = gameStates }

    // Second screen (AYN Thor etc.): the details window moves there, and
    // otherwise it shows the focused game and the music player.
    // While the screensaver runs, the second screen follows the "Second screen
    // while it runs" setting: the same screensaver, black, or its normal view.
    val secondSaverMode = state.screensaver.secondScreen
    val secondSaverShowsArt = secondSaverMode != com.shelf.launcher.data.SecondScreenSaver.KEEP &&
        secondSaverMode != com.shelf.launcher.data.SecondScreenSaver.BLACK &&
        secondSaverMode != com.shelf.launcher.data.SecondScreenSaver.CLOCK
    val secondSaverSlides = remember(screensaverOn, secondSaverMode, state.games, state.art, state.artOverrides, state.lastPlayedAt) {
        if (screensaverOn && secondSaverShowsArt) {
            val recentFirst = secondSaverMode == com.shelf.launcher.data.SecondScreenSaver.RECOMMENDED &&
                state.games.any { state.lastPlayedAt.containsKey(it.stableKey) }
            val pool = if (recentFirst) {
                state.games.filter { state.lastPlayedAt.containsKey(it.stableKey) }
                    .sortedByDescending { state.lastPlayedAt[it.stableKey] ?: 0L }.take(40)
            } else state.games
            pool.mapNotNull { g ->
                val override = state.artOverrides[g.stableKey]
                val art = state.art[g.stableKey]
                val image = override?.hero ?: art?.heroUrl ?: override?.wide ?: art?.wideGridUrl
                    ?: return@mapNotNull null
                ScreensaverSlide(g.name, g.platform, image, override?.logo ?: art?.logoUrl)
            }.shuffled().take(80)
        } else {
            emptyList()
        }
    }
    // Update 200: the console folder lit / touched on the second screen's Games page (no folder open there): the top screen
    // shows it big over the home (TopScreenFolderPreview, FolderIcons.kt). Null: none.
    var secondHoverCategory by remember { mutableStateOf<GamesFolder?>(null) }
    // Update 200: "Change icon…" on a Games page folder opens Android's picture picker from here (the main screen's activity).
    FolderIconPickerHost()
    // What the second screen shows.
    @Composable
    fun SecondScreenContent() {
        val game = focusedGame
        if (screensaverOn && secondSaverMode == com.shelf.launcher.data.SecondScreenSaver.BLACK) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures { screensaverOn = false }
                    }
            )
        } else if (screensaverOn && secondSaverMode != com.shelf.launcher.data.SecondScreenSaver.KEEP) {
            ScreenSaver(
                slides = secondSaverSlides,
                nowPlaying = state.nowPlaying,
                showMusic = when (secondSaverMode) {
                    com.shelf.launcher.data.SecondScreenSaver.MUSIC,
                    com.shelf.launcher.data.SecondScreenSaver.RECOMMENDED -> true
                    com.shelf.launcher.data.SecondScreenSaver.ART_SLIDESHOW,
                    com.shelf.launcher.data.SecondScreenSaver.CLOCK -> false
                    else -> state.screensaver.showMusic
                },
                clockOnly = secondSaverMode == com.shelf.launcher.data.SecondScreenSaver.CLOCK,
                blackoutMinutes = state.screensaver.blackoutMinutes,
                onDismiss = { screensaverOn = false },
                showLyrics = state.screensaver.showLyrics,
                sharpen = state.appearance.sharpHeroes,
                albumBlur = state.appearance.secondMusicBlurArt,
                albumBlurPercent = state.appearance.secondMusicBlur
            )
        } else if (TwinScreens.ctx != null && state.appearance.homeLayout.isTwin && SecondScreenShown.value &&
            !TwinScreens.dashboard && // Update 180: the dashboard asked for from the layout
            !(showDetails && state.appearance.secondDetails)) {
            // Update 158: Twin desk / Twin icons put their bottom half here (TwinScreens.kt); the game window still
            // comes here when it's set to.
            TwinScreens.ctx?.let { TwinSecondScreen(it) }
        } else {
            val key = game?.stableKey
            val profileVersion by viewModel.profileVersion.collectAsState()
            // Only an override (game or console) replaces the page; otherwise the normal setting stays in charge.
            val gamePageOverride = remember(profileVersion, key) {
                game?.let { viewModel.effectiveProfile(it) }?.takeIf { it.secondScreenPage.source != com.shelf.launcher.data.ProfileSource.GLOBAL }?.secondScreenPage?.value
            }
            val dockApps = remember(state.appearance.secondDockApps, state.installedApps) {
                state.appearance.secondDockApps.filter { it.isNotBlank() }.distinct().mapNotNull { p ->
                    // (Update 193: Voltcap's own shortcuts sit in the dock too.)
                    state.installedApps.firstOrNull { it.packageName == p } ?: SecondDockShortcuts.game(p)
                }
            }
            // Update 193: a Voltcap shortcut tapped in the second screen's dock (not a page there): done right away, or
            // Android's own panel opened on the second screen — nothing pops up on the main screen. Returns its note.
            val dockCtx = androidx.compose.ui.platform.LocalContext.current
            val dockShortcut: (String) -> String? = { id ->
                val f = com.shelf.launcher.data.Features
                fun onSecond(intent: android.content.Intent): Boolean {
                    val d = com.shelf.launcher.data.ScreenPlacement.secondScreen(dockCtx)?.displayId
                    return runCatching {
                        dockCtx.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK), com.shelf.launcher.data.ScreenPlacement.optionsFor(d))
                        true
                    }.getOrDefault(false)
                }
                when (id) {
                    "voltcap:spotify" -> { MusicApps.playPause(dockCtx, state.nowPlaying?.isPlaying == true); null }
                    "voltcap:explore" -> {
                        f.exploreMode.value = !f.exploreMode.value
                        if (f.exploreMode.value) "Explore on: the whole collection, shown equally" else "Explore off: Living Library highlights are back"
                    }
                    "voltcap:rediscover" -> { viewModel.setSpecialFilter(com.shelf.launcher.SpecialFilter.REDISCOVER); "Rediscover: games you haven't played in a while" }
                    "voltcap:r1:lights" -> {
                        if (!state.appearance.lightsEnabled) {
                            f.lightsHeldOff.value = false
                            viewModel.setAppearance(state.appearance.copy(lightsEnabled = true))
                            "Lights: Voltcap's colour"
                        } else {
                            f.lightsHeldOff.value = !f.lightsHeldOff.value
                            if (f.lightsHeldOff.value) "Lights off" else "Lights on"
                        }
                    }
                    "voltcap:r1:fps" -> {
                        var label = "Off"
                        com.shelf.launcher.data.perf.PerfSettings.update(dockCtx) { c ->
                            val now = when {
                                c.showDuringGames && c.musicDuringGames && c.raDuringGames -> "All"
                                !c.showDuringGames && !c.musicDuringGames && !c.raDuringGames -> "Off"
                                c.showDuringGames && !c.musicDuringGames && !c.raDuringGames -> "Stats"
                                !c.showDuringGames && c.musicDuringGames && !c.raDuringGames -> "Music"
                                !c.showDuringGames && !c.musicDuringGames && c.raDuringGames -> "Trophies"
                                else -> "Mixed"
                            }
                            val next = when (now) {
                                "Off" -> Triple(true, false, false)
                                "Stats" -> Triple(false, true, false)
                                "Music" -> Triple(false, false, true)
                                "Trophies" -> Triple(true, true, true)
                                else -> Triple(false, false, false)
                            }
                            label = when (next) {
                                Triple(true, false, false) -> "Stats"
                                Triple(false, true, false) -> "Music"
                                Triple(false, false, true) -> "Trophies"
                                Triple(true, true, true) -> "All"
                                else -> "Off"
                            }
                            c.copy(showDuringGames = next.first, musicDuringGames = next.second, raDuringGames = next.third)
                        }
                        PerfOverlayService.reconfigure(dockCtx)
                        if (label == "Stats" && com.shelf.launcher.data.perf.PerfAutoSetup.possible) {
                            Thread { runCatching { com.shelf.launcher.data.perf.PerfAutoSetup.run(dockCtx.applicationContext) } }.start()
                        }
                        "Overlay over games: $label"
                    }
                    "voltcap:r1:endless" -> if (!endlessApplies(state.appearance.homeLayout)) "${state.appearance.homeLayout.label} has no Endless row" else {
                        viewModel.setAppearance(state.appearance.copy(loopRow = !state.appearance.loopRow))
                        if (!state.appearance.loopRow) "Endless row on" else "Endless row off"
                    }
                    "voltcap:r1:navbar" -> {
                        val on = !com.shelf.launcher.data.GestureHint.immersiveState.value
                        com.shelf.launcher.data.GestureHint.setImmersive(dockCtx, on)
                        if (on) "Navigation bar hidden" else "Navigation bar shown"
                    }
                    "voltcap:r1:whoplays" -> { f.whoPlays.value = !f.whoPlays.value; if (f.whoPlays.value) "Who is playing: on" else "Who is playing: off" }
                    "voltcap:r1:drawnmedia" -> { f.mediaShells.value = !f.mediaShells.value; if (f.mediaShells.value) "Drawn discs" else "Real discs" }
                    "voltcap:r1:theme" -> {
                        val favs = f.favoriteThemes.state.value
                        val favThemes = com.shelf.launcher.ui.theme.AppTheme.values().filter { it.name in favs }
                        if (favThemes.isEmpty()) "Star some themes first (Settings › Theme), then this steps through them"
                        else {
                            val next = favThemes[(favThemes.indexOf(state.appTheme) + 1).mod(favThemes.size)]
                            viewModel.setTheme(next)
                            "Theme: ${next.label}"
                        }
                    }
                    "voltcap:r1:saver" -> {
                        val modes = com.shelf.launcher.data.BatterySaver.Mode.values()
                        val next = modes[(f.saverMode.value.ordinal + 1) % modes.size]
                        f.saverMode.value = next
                        when (next) {
                            com.shelf.launcher.data.BatterySaver.Mode.AUTO -> "Battery saver: when the battery is low"
                            com.shelf.launcher.data.BatterySaver.Mode.ALWAYS -> "Battery saver: always"
                            com.shelf.launcher.data.BatterySaver.Mode.OFF -> "Battery saver: off"
                        }
                    }
                    "voltcap:r1:rescan" -> if (state.isScanning) "Already scanning" else { viewModel.rescanEverything(); "Rescanning your library" }
                    "voltcap:r1:scrape" -> if (state.scrapeTotal > 0) "Getting art: ${state.scrapeDone} of ${state.scrapeTotal}" else { viewModel.scrapeMissingArt(manual = true); "Getting the missing art" }
                    "voltcap:r1:dnd" -> {
                        val nm = dockCtx.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                        if (!nm.isNotificationPolicyAccessGranted) {
                            onSecond(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                            "Allow Voltcap to change Do not disturb, then tap it again"
                        } else {
                            val wasOn = QuickControls.isDndOn(dockCtx)
                            QuickControls.toggleDnd(dockCtx)
                            if (wasOn) "Do not disturb off" else "Do not disturb on"
                        }
                    }
                    "voltcap:r1:wifi" -> {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            if (!onSecond(android.content.Intent(android.provider.Settings.Panel.ACTION_WIFI))) onSecond(android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
                            null
                        } else { QuickControls.toggleWifi(dockCtx); "Wi-Fi switched" }
                    }
                    "voltcap:r1:bt" -> {
                        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.R) { QuickControls.toggleBluetooth(dockCtx); "Bluetooth switched" }
                        else { onSecond(android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)); null }
                    }
                    "voltcap:r1:settings" -> { onSecond(android.content.Intent(android.provider.Settings.ACTION_SETTINGS)); null }
                    // Update 207: Voltcap's own screens, opened on the main screen from the second screen's dock.
                    "voltcap:saves" -> { showSaveExplorer = true; "Save Vault: on the main screen" }
                    "voltcap:whattoplay" -> { showWhatToPlay = true; "What to play: on the main screen" }
                    "voltcap:stats" -> { hubScreen = HubScreen.STATS; "Stats: on the main screen" }
                    "voltcap:search" -> { showSearchBar = true; "Search: on the main screen" }
                    "voltcap:vsettings" -> { showSettings = true; "Settings: on the main screen" }
                    "voltcap:screensaver" -> { screensaverOn = true; null }
                    else -> "That shortcut only works on the main screen"
                }
            }
            val secondMotif = SecondScene.motif
            val secondColors = SecondScene.colors
            // Its own wallpaper (Settings › Screens › Second screen tools), else the theme's scene.
            val secondWallpaper = com.shelf.launcher.data.Features.secondScreenWallpaper.state.value.takeIf { it.isNotEmpty() }
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            SecondScreenHome(
                sceneBehind = if (secondWallpaper != null) {
                    {
                        coil.compose.AsyncImage(
                            model = secondWallpaper, contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize()
                        )
                    }
                } else if (secondMotif != null && secondColors != null) {
                    { ThemeScene(secondMotif, secondColors, Modifier.fillMaxSize()) }
                } else null,
                info = SecondHomeGame(
                    game = game,
                    coverUrl = key?.let { state.artOverrides[it]?.cover ?: state.art[it]?.gridUrl },
                    // Artwork priority (ArtPick); the cover has its own slot here.
                    heroUrl = key?.let { k ->
                        com.shelf.launcher.data.ArtPick.backdrop(k, state.artOverrides[k], state.art[k], state.screenshots[k])
                            ?.takeIf { it != (state.artOverrides[k]?.cover ?: state.art[k]?.gridUrl) }
                    },
                    wideUrl = key?.let { state.artOverrides[it]?.wide ?: state.art[it]?.wideGridUrl },
                    playtimeMs = key?.let { state.playtime[it] },
                    award = key?.let { awardsByKey[it] },
                    progress = key?.let { state.raProgress[it] },
                    metadata = key?.let { state.metadata[it] },
                    metadataLoading = key != null && key in state.metadataLoading,
                    videoUrl = key?.let { state.videoUrls[it] },
                    recentUnlocks = game?.raGameId?.let { id -> state.raRecentAchievements.filter { it.GameID == id } }.orEmpty(),
                    achievements = key?.let { state.raAchievementDetails[it] }.orEmpty(),
                    caseUrl = key?.let { k -> typedArtFor(com.shelf.launcher.data.CardArtType.CASE, state.physicalMedia[k], null, game?.platform) }, // (Update 231, #39: never ScreenScraper's turned 3D box drawn flat as the case front)
                    discUrl = key?.let { k -> state.physicalMedia[k].orEmpty().firstOrNull { it.first == "Disc / cartridge" }?.second },
                    labelUrl = key?.let { k -> state.physicalMedia[k].orEmpty().firstOrNull { it.first == "Disc label" }?.second },
                    box2dUrl = key?.let { k -> BoxTexture.front(state.physicalMedia[k], game?.platform) },
                    shots = key?.let { k -> state.screenshots[k] }.orEmpty(),
                    logoUrl = key?.let { k -> state.artOverrides[k]?.logo ?: state.art[k]?.logoUrl },
                    // Update 198: the case that opens on the Details page.
                    inspect = if (game != null && com.shelf.launcher.data.Features.secondOpenCase.state.value)
                        remember(game.stableKey, state.physicalMedia[game.stableKey], state.art[game.stableKey], state.artOverrides[game.stableKey],
                            com.shelf.launcher.data.Features.printedBacks.state.value) { runCatching { secondCasePiece(game, state) }.getOrNull() }
                    else null
                ),
                // Update 189: the "Game art" look's top-left pill.
                avatarUrl = profilePictureUrl(com.shelf.launcher.data.RaBadges.mediaUrl(state.raProfile?.UserPic)),
                raPoints = state.raProfile?.TotalPoints,
                onNeedAchievements = { viewModel.loadAchievementDetails(it) },
                onNeedPhysical = { viewModel.loadPhysicalMedia(it) },
                walkthroughContent = { g -> GuideSecondScreenPage(g, guideUi) { viewModel.peekGuide(it) } },
                performanceContent = { PerfPage() },
                extraPage = { p ->
                    when (p) {
                        com.shelf.launcher.data.SecondPage.WIDGETS -> WidgetBoardPage(
                            state.nowPlaying,
                            onPlayPause = { viewModel.mediaPlayPause() },
                            onNext = { viewModel.mediaNext() },
                            onPrevious = { viewModel.mediaPrevious() }
                        )
                        com.shelf.launcher.data.SecondPage.WALL -> {
                            val wallGames = remember(visibleGames, state.art, state.artOverrides) {
                                wallGameTiles(
                                    visibleGames,
                                    cover = { g -> state.artOverrides[g.stableKey]?.cover ?: state.art[g.stableKey]?.gridUrl },
                                    wide = { g -> state.artOverrides[g.stableKey]?.wide ?: state.art[g.stableKey]?.wideGridUrl }
                                )
                            }
                            SecondWallPage(
                                platformTiles = categoryTiles, currentCategoryKey = currentCategoryKey,
                                gameTiles = wallGames, focusedGame = state.focusedIndex,
                                onPickCategory = { applyCategory(it) },
                                onFocusGame = { viewModel.setFocus(it) }
                            )
                        }
                        com.shelf.launcher.data.SecondPage.MAGNIFIER -> MagnifierPage(Modifier.fillMaxSize())
                        else -> Unit
                    }
                },
                // Update 122: the Games page — every console as a folder; a tap on a game in one takes the main screen
                // to it (its console, the game selected, its game window open, as A would).
                gamesPage = {
                    val titles = LocalTitleOptions.current
                    val folders = remember(state.games, state.availablePlatforms, state.libraryApps, state.appearance.hideDuplicates, com.shelf.launcher.data.PlatformOrder.version.value) {
                        com.shelf.launcher.data.PlatformOrder.sort(state.availablePlatforms).mapNotNull { p ->
                            val n = state.copy(platformFilter = p, specialFilter = null, searchQuery = "").visibleGames.size
                            if (n == 0) null else GamesFolder(
                                p,
                                when (p) {
                                    com.shelf.launcher.ANDROID_GAMES_PLATFORM -> "Android games"
                                    "App" -> "Other apps"
                                    else -> titles.console(p)
                                },
                                n
                            )
                        }
                    }
                    SecondGamesPage(
                        folders = folders,
                        gamesOf = { p -> state.copy(platformFilter = p, specialFilter = null, searchQuery = "").visibleGames },
                        coverOf = { g -> state.artOverrides[g.stableKey]?.cover ?: state.art[g.stableKey]?.gridUrl },
                        wideOf = { g -> state.artOverrides[g.stableKey]?.wide ?: state.art[g.stableKey]?.wideGridUrl ?: state.artOverrides[g.stableKey]?.hero ?: state.art[g.stableKey]?.heroUrl },
                        onPickGame = { p, g ->
                            viewModel.setPlatformFilter(p)
                            val index = viewModel.uiState.value.visibleGames.indexOfFirst { it.stableKey == g.stableKey }
                            if (index >= 0) viewModel.setFocus(index)
                            sounds?.play(UiSound.SELECT)
                            if (index >= 0 && com.shelf.launcher.data.Features.gameWindow.value) {
                                if (state.appearance.homeLayout.name in com.shelf.launcher.data.Features.classicWindowLayouts.value) openDetails()
                                else GamePopup.game = g
                            }
                        },
                        // Update 195: the main screen follows what's lit or opened on the Games page (no window opens).
                        onHighlight = { p, g ->
                            if (viewModel.uiState.value.platformFilter != p) viewModel.setPlatformFilter(p)
                            if (g != null) {
                                val index = viewModel.uiState.value.visibleGames.indexOfFirst { it.stableKey == g.stableKey }
                                if (index >= 0) viewModel.setFocus(index)
                            }
                        },
                        // Update 200: a folder lit / touched there shows that console big up here (the overlay over the home).
                        onHover = { secondHoverCategory = it }
                    )
                },
                page = gamePageOverride ?: state.appearance.secondPage,
                // While a game/console override is active, picking a page on the second screen sets it for that game.
                onPage = { p ->
                    if (game != null && gamePageOverride != null) viewModel.updateGameProfile(game) { it.copy(secondScreenPage = p) }
                    else viewModel.setAppearance(state.appearance.copy(secondPage = p))
                },
                nowPlaying = state.nowPlaying,
                onPlayPause = { viewModel.mediaPlayPause() },
                onNext = { viewModel.mediaNext() },
                onPrevious = { viewModel.mediaPrevious() },
                onSeek = { viewModel.mediaSeekTo(it) },
                videoSound = state.appearance.videoSound,
                onNeedMetadata = { viewModel.loadMetadata(it) },
                onNeedVideo = { viewModel.loadVideoUrl(it, force = true) },
                showDock = state.appearance.secondDock,
                dockApps = dockApps,
                allApps = state.installedApps,
                onLaunchApp = { viewModel.launchDockApp(it) },
                onDockChange = { list -> viewModel.setAppearance(state.appearance.copy(secondDockApps = list)) },
                onOpenSettings = { menuStartCategory = "Screens"; showMenu = true },
                // Update 201: the Lyrics stage's gear: straight to Music page layout and its options.
                onOpenMusicSettings = {
                    val e = SettingsIndex.all.firstOrNull { it.label == "Music page layout" }
                    if (e != null) prepareSettingsJump(e.section, e.subpage, e.foldKey, e.label)
                    menuStartCategory = e?.section ?: "Screens"; showMenu = true
                },
                onShortcut = dockShortcut,
                options = SecondScreenOptions(
                    artType = state.appearance.secondArtType,
                    artBlur = state.appearance.secondArtBlur,
                    playerOverArt = state.appearance.secondPlayerOverArt,
                    musicLayout = state.appearance.secondMusicLayout,
                    visualizerMotif = secondVisualizerMotif(state.appTheme, state.appearance.sceneTheme),
                    musicBlurArt = state.appearance.secondMusicBlurArt,
                    musicBlur = state.appearance.secondMusicBlur,
                    musicLyrics = state.appearance.secondMusicLyrics,
                    screenshotFolders = state.appearance.screenshotFolders,
                    pageTitleOnly = state.appearance.secondPageTitleOnly,
                    visualizerArt = state.appearance.secondVisualizerArt,
                    detailsCaseFirst = state.appearance.secondDetailsCase
                ),
                onToggleLyrics = {
                    viewModel.setAppearance(
                        state.appearance.copy(secondMusicLyrics = !state.appearance.secondMusicLyrics)
                    )
                },
                onBrowseSpotify = { showSpotify = true },
                onMusicBlurChange = { viewModel.setAppearance(state.appearance.copy(secondMusicBlur = it)) },
                // A on a game: its details window as the second screen's Game page (Settings › Screens).
                fxOverlay = if (state.appearance.musicFxSecond) {
                    {
                        CornerVisualizers(
                            playing = state.nowPlaying?.isPlaying == true,
                            colors = musicSceneColors(),
                            topInset = 52.dp,
                            bottomInset = if (state.appearance.secondDock) 84.dp else 12.dp
                        )
                    }
                } else null,
                gameWindow = if (showDetails && game != null && state.appearance.secondDetails) {
                    { DetailsWindow(game, fillScreen = true) }
                } else null,
                onCloseGameWindow = { showDetails = false }
            )
            }
        }
    }
    val secondScreenActive = SecondScreenHost(
        enabled = state.appearance.useSecondScreen && !com.shelf.launcher.CrashGuard.safeMode,
        // Touches and keys on the second screen count as using the launcher. Update 113: a touch down there also
        // hands the controller back to the home screen's keys (at most twice a second), so the D-pad keeps working
        // whichever screen you touched last.
        onUserActivity = {
            val now = System.currentTimeMillis()
            if (now - lastInputAt > 500L && !aDialogIsOpen && GamePopup.game == null && !showDetails) runCatching { rootFocusRequester.requestFocus() }
            lastInputAt = now
        }
    ) {
        Box(Modifier.fillMaxSize()) {
            SecondScreenContent()
            // Update 180: the dashboard opened from a two-screen layout: the way back to the layout's bottom part.
            if (state.appearance.homeLayout.isTwin && TwinScreens.dashboard) TwinBackPill(state.appearance.homeLayout.label, state.appearance.homeLayout)
        }
    }
    // The companion shows music, so keep it fresh while a second screen is up.
    // The Presentation shares this activity's lifecycle, so this stops when Voltcap is stopped
    // (a game is running; CompanionActivity has its own updates).
    SideEffect { SecondScreenReal.active = secondScreenActive }
    NowPlayingUpdates(enabled = secondScreenActive) {
        viewModel.refreshNowPlaying()
    }

    val drawerAllowed by rememberUpdatedState(!aDialogIsOpen && !showDetails && !screensaverOn)
    // Update 224 (#12): Android's own swipe-from-the-bottom area (gesture navigation) — a swipe for all apps starts just
    // above it, where Android lets the app have it (from the very edge Android took it, so it rarely worked).
    val gestureBottomPx by rememberUpdatedState(
        androidx.compose.foundation.layout.WindowInsets.systemGestures.getBottom(androidx.compose.ui.platform.LocalDensity.current)
    )
    // Tile-style home: the round buttons under the tiles.
    val tileLayout = state.appearance.homeLayout == com.shelf.launcher.data.HomeLayout.TILES
    val heroLayout = state.appearance.homeLayout == com.shelf.launcher.data.HomeLayout.HERO
    val gridLayout = state.appearance.homeLayout == com.shelf.launcher.data.HomeLayout.GRID
    val tvLayout = state.appearance.homeLayout == com.shelf.launcher.data.HomeLayout.TV
    // The style homes (Dashboard, Live tiles, Side menu, Cross menu, Spotlight, Icon bar — StyleHomes.kt): whole-screen
    // layouts with their own menus, laid out and lit like the grid / TV room.
    val styleLayout = state.appearance.homeLayout.styleHome
    // Update 159: Discs only on → cartridge games print their cover on their CD (typedArtFor).
    val discLookNow = state.appearance.cardArtType == com.shelf.launcher.data.CardArtType.DISC
    androidx.compose.runtime.SideEffect { if (DiscLook.on != discLookNow) DiscLook.on = discLookNow }
    LaunchedEffect(state.appearance.homeLayout) { StyleNav.reset(); HomeReach.leave(); GamePopup.game = null; TwinScreens.dashboard = false; TwinScreens.categoriesExpanded = false }
    // Update 181: the Pocket layouts play their sounds through the home screen's.
    SideEffect { PocketSounds.play = { s -> sounds?.play(s) } }
    // Update 202: Green Core's menu opens the app drawer, the music library and the sort window through the hotkey actions.
    SideEffect { GreenCoreHooks.hotkey = { a -> runHotkey(com.shelf.launcher.data.Hotkey(action = a)) } }
    val tileBarLast = remember { intArrayOf(0) }
    LaunchedEffect(tileLayout) { if (!tileLayout) tileBarFocus = null }
    val tileShortcuts = listOf(
        HomeShortcut("Categories", Icons.Filled.GridView, Color(0xFFFF9F43)) { openCategoryScreen() },
        HomeShortcut("All apps", Icons.Filled.Apps, Color(0xFF4CD964)) { if (drawerAllowed) showAppDrawer = true },
        HomeShortcut("Search", Icons.Filled.Search, Color(0xFF36C5F0)) { showSearchBar = true },
        HomeShortcut("Notifications", Icons.Filled.Notifications, Color(0xFFFF5A5F)) {
            viewModel.refreshNotifications()
            notificationsOnly = true; showNotifications = true
        },
        HomeShortcut("Music", Icons.Filled.MusicNote, Color(0xFFB57BFF)) { showSpotify = true },
        HomeShortcut("Achievements", Icons.Filled.EmojiEvents, Color(0xFFFFC83D)) {
            val game = focusedGame
            if (game?.raGameId != null) viewingAchievementsForGame = game
            else hubScreen = HubScreen.ACHIEVEMENTS
        },
        // Update 274 (Toni): Play stats as a round shortcut too (hours, weeks, streaks — the Stats page).
        HomeShortcut("Stats", Icons.Filled.BarChart, Color(0xFF4FD1A5)) { hubScreen = HubScreen.STATS },
        HomeShortcut("Shortcuts", Icons.Filled.Bolt, Color(0xFF5AC8FA)) { showShortcuts = true },
        // The memory-card save browser (also in the R1 panel and Settings › Sync).
        HomeShortcut(com.shelf.launcher.data.SAVES_NAME, Icons.Filled.SdCard, Color(0xFF7FE0C4)) { showSaveExplorer = true },
        HomeShortcut("What should I play?", Icons.Filled.Casino, Color(0xFFFF7EB6)) { showWhatToPlay = true },
        HomeShortcut("Edit Home", Icons.Filled.OpenWith, Color(0xFFB4A8FF)) { HomeEditState.open() },
        // Help: the setup guide again, and help for the trickier pages.
        HomeShortcut("Help", Icons.Filled.Info, Color(0xFFFFB46B)) { VoltHub.open = true },
        HomeShortcut("Settings", Icons.Filled.Settings, Color(0xFFC7CDD6)) { menuStartCategory = null; showMenu = true }
    ).let { all ->
        // Settings › Round shortcuts › Buttons: the ones switched off stay out (Settings always stays reachable).
        val hidden = com.shelf.launcher.data.Features.shortcutHidden.state.value
        all.filter { it.label !in hidden }.ifEmpty { all.filter { it.label == "Settings" } }
    }
    // Update 101 (HomeReach.kt): the round shortcuts as they are reached with the D-pad when folded into a corner ☰ or
    // floating, in the order they are drawn (a ☰ in a right-hand corner lists them right to left).
    fun reachShortcuts(): List<HomeShortcut> {
        val spot = state.appearance.shortcutSpot
        val rightCorner = spot == com.shelf.launcher.data.ShortcutSpot.TOP_RIGHT || spot == com.shelf.launcher.data.ShortcutSpot.BOTTOM_RIGHT
        return if (rightCorner) tileShortcuts.reversed() else tileShortcuts
    }
    /** What lies above / below the games for the D-pad right now (HomeReach), nearest first. */
    fun prepareReach() {
        val a = state.appearance
        val dock = a.dockPosition
        val classic = !tileLayout && !heroLayout && !gridLayout && !tvLayout && !styleLayout
        // The game header's buttons (classic row), or its Play & buttons part on Hero / Spotlight / Icon bar.
        val actionsAt = HomeWidgets.position(HomeWidgets.H_ACTIONS) ?: HomeWidgets.position(HomeWidgets.HEADER)
        val headerUp = actionsAt?.let { it.second < 0.5f } ?: if (classic) dock != com.shelf.launcher.data.DockPosition.TOP else true
        // The round shortcuts in a corner ☰ or floating (the Tile home's bar by the tiles has its own keys).
        val spot = a.shortcutSpot
        val scOn = a.tileShortcuts && tileShortcuts.isNotEmpty() && spot != com.shelf.launcher.data.ShortcutSpot.ROW &&
            !(styleLayout && styleHasShortcuts(a.homeLayout)) && !(tileLayout && spot == com.shelf.launcher.data.ShortcutSpot.FREE)
        val scUp = if (spot == com.shelf.launcher.data.ShortcutSpot.FREE) {
            HomeWidgets.position(HomeWidgets.SHORTCUTS)?.let { it.second < 0.5f } ?: (dock == com.shelf.launcher.data.DockPosition.BOTTOM)
        } else {
            var top = spot == com.shelf.launcher.data.ShortcutSpot.TOP_LEFT || spot == com.shelf.launcher.data.ShortcutSpot.TOP_RIGHT
            if (top && dock == com.shelf.launcher.data.DockPosition.TOP) top = false
            if (!top && dock == com.shelf.launcher.data.DockPosition.BOTTOM) top = true
            top
        }
        // The small music player (in the status bar or a corner; not while the Now playing panel replaces it).
        val pos = a.musicPlayerPosition
        val moved = HomeWidgets.position(HomeWidgets.MUSIC)
        val inStatusBar = pos == com.shelf.launcher.data.MusicPlayerPosition.TOP_LEFT && a.statusOn && moved == null
        // The music theme's Now playing panel replaces the small player (its buttons, when shown, are reached the same way).
        val panelOn = HomeReach.musicPanel && state.nowPlaying != null && com.shelf.launcher.data.Features.musicShowControls.state.value
        val playerOn = panelOn || (a.miniPlayer && state.nowPlaying != null && !HomeReach.musicPanel &&
            (inStatusBar || moved != null || !styleLayout))
        val playerUp = when {
            panelOn -> HomeReach.musicPanelUp
            moved != null -> moved.second < 0.5f
            inStatusBar -> !(dock == com.shelf.launcher.data.DockPosition.TOP && !gridLayout && !tvLayout && !styleLayout)
            else -> pos == com.shelf.launcher.data.MusicPlayerPosition.TOP_LEFT || pos == com.shelf.launcher.data.MusicPlayerPosition.TOP_RIGHT
        }
        val all = listOf(HomeReach.Zone.HEADER to headerUp, HomeReach.Zone.SHORTCUTS to scUp, HomeReach.Zone.MUSIC to playerUp)
        // Update 109: a part taken off with its ✕ in Edit Home is skipped by the D-pad.
        HomeReach.counts = mapOf(
            HomeReach.Zone.SHORTCUTS to if (scOn && !HomeWidgets.hidden(HomeWidgets.SHORTCUTS)) tileShortcuts.size else 0,
            HomeReach.Zone.MUSIC to if (playerOn && (panelOn || !HomeWidgets.hidden(HomeWidgets.MUSIC))) 3 else 0
        )
        HomeReach.up = all.filter { it.second }.map { it.first }
        HomeReach.down = all.filter { !it.second }.map { it.first }
    }
    /** A on a button reached with the D-pad (HomeReach). */
    fun pressReach(z: HomeReach.Zone, i: Int) {
        when (z) {
            HomeReach.Zone.HEADER -> { HomeReach.header.getOrNull(i)?.invoke(); HomeReach.leave() }
            HomeReach.Zone.SHORTCUTS -> { reachShortcuts().getOrNull(i)?.onClick?.invoke(); HomeReach.leave() }
            HomeReach.Zone.MUSIC -> when (i) {
                MINI_PREVIOUS -> viewModel.mediaPrevious()
                MINI_PLAY -> viewModel.mediaPlayPause()
                MINI_NEXT -> viewModel.mediaNext()
            }
        }
    }
    // Moved in Edit Home: the round shortcuts float on their own instead of sitting by the tiles.
    // "Anywhere" (any home style): the bar always floats where it was put.
    val shortcutsFree = state.appearance.tileShortcuts && state.appearance.shortcutSpot == com.shelf.launcher.data.ShortcutSpot.FREE
    // Round shortcuts that can be picked up in Edit Home: by the tiles, floating, or the corner ☰ (any layout that
    // shows them; not a style home that has them in its own menu). Moving them makes them float ("Anywhere").
    val shortcutsMovable = state.appearance.tileShortcuts && !(styleLayout && styleHasShortcuts(state.appearance.homeLayout)) &&
        (state.appearance.shortcutSpot != com.shelf.launcher.data.ShortcutSpot.ROW || tileLayout)
    val shortcutsFloating = shortcutsFree || (shortcutsMovable && HomeEditState.active) || (tileLayout && state.appearance.tileShortcuts &&
        state.appearance.shortcutSpot == com.shelf.launcher.data.ShortcutSpot.ROW &&
        (HomeWidgets.position(HomeWidgets.SHORTCUTS) != null || HomeEditState.active))
    LaunchedEffect(
        shortcutsMovable, tileLayout, heroLayout, gridLayout, tvLayout, styleLayout, com.shelf.launcher.data.Features.homeHeader.state.value,
        com.shelf.launcher.data.Features.homeHeaderHidden.state.value, state.appearance.categoryBar, state.appearance.rowInfo,
        com.shelf.launcher.data.Features.overlayParts.state.value, state.appearance.homeLayout
    ) {
        HomeWidgets.present = buildSet {
            add(HomeWidgets.MUSIC)
            if (shortcutsMovable) add(HomeWidgets.SHORTCUTS)
            val classic = !tileLayout && !heroLayout && !gridLayout && !tvLayout && !styleLayout
            val headerOn = com.shelf.launcher.data.Features.homeHeader.state.value
            if (headerOn && classic) {
                add(HomeWidgets.HEADER)
                val hidden = com.shelf.launcher.data.Features.homeHeaderHidden.state.value
                if ("title" !in hidden) add(HomeWidgets.H_TITLE)
                if ("continue" !in hidden) add(HomeWidgets.H_LINE)
                if (listOf("PLAYED", "ACHIEVEMENTS", "LAST PLAYED", "TIME TO BEAT", "SCORE").any { it !in hidden }) add(HomeWidgets.H_STATS)
                if (listOf("play", "fav", "details", "options", "sync").any { it !in hidden }) add(HomeWidgets.H_ACTIONS)
                if ("source" !in hidden) add(HomeWidgets.H_BADGE)
            }
            if (classic && state.appearance.categoryBar != com.shelf.launcher.data.CategoryBar.OFF) add(HomeWidgets.CATEGORY)
            state.appearance.homeLayout.takeIf { it in com.shelf.launcher.data.Features.overlayLayouts }?.let { l ->
                com.shelf.launcher.data.Features.overlayPartsFor(l).forEach { add(HomeWidgets.overlayId(l, it)) }
            }
            if (classic) {
                // Next to the row: with the game header on, its game part is left out (the header has it).
                val info = state.appearance.rowInfo
                if ((info == com.shelf.launcher.data.RowInfo.GAME || info == com.shelf.launcher.data.RowInfo.BOTH) && !headerOn) add(HomeWidgets.INFO)
                if (info == com.shelf.launcher.data.RowInfo.NEWS || info == com.shelf.launcher.data.RowInfo.BOTH) add(HomeWidgets.NEWS)
                // Update 202: a card added to the Voltcap row with + Add that its layout shows now anyway is there once.
                val twice = filter { it.startsWith(com.shelf.launcher.data.HomeLayout.CLASSIC.name + ":") && HomeWidgets.baseId(it) in this }
                removeAll(twice.toSet())
            }
        }
    }
    // Edit Home finished with the round shortcuts moved: they float where they were put from now on.
    LaunchedEffect(HomeEditState.active) {
        if (HomeEditState.active) return@LaunchedEffect
        val a = state.appearance
        val moved = HomeWidgets.position(HomeWidgets.SHORTCUTS) != null
        val atTiles = tileLayout && a.shortcutSpot == com.shelf.launcher.data.ShortcutSpot.ROW
        if (moved && shortcutsMovable && a.shortcutSpot != com.shelf.launcher.data.ShortcutSpot.FREE && !atTiles) {
            viewModel.setAppearance(a.copy(shortcutSpot = com.shelf.launcher.data.ShortcutSpot.FREE, tileShortcuts = true))
        }
    }
    // The home screen's layers and every popup live in their own composables, so
    // LauncherScreenContent stays small enough for Android to compile ahead of time
    // (one huge method runs interpreted, slowly).
    /** The game header for the game you're on (Home › Next to the row › Game header): above the row, or floating. */
    @Composable
    fun HomeHeaderBlock(fill: Boolean, only: String? = null) {
        focusedGame?.let { g ->
            val compactHeader = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 700
            HomeShowcaseHeader(
                game = g,
                title = LocalTitleOptions.current.display(g.name),
                lastPlayedAt = state.lastPlayedAt[g.stableKey],
                playtimeMs = state.playtime[g.stableKey],
                metadata = state.metadata[g.stableKey],
                raProgress = state.raProgress[g.stableKey],
                isFavorite = g.stableKey in state.favorites,
                syncStatus = detailsSyncStatuses[g.stableKey],
                console = LocalTitleOptions.current.console(g.platform),
                compact = compactHeader,
                onPlay = { runDetailsAction(DetailsAction.PLAY, g) },
                onFavorite = { viewModel.toggleFavorite(g) },
                onDetails = { openDetails() },
                onOptions = { optionsForGame = g },
                onSync = { saveSyncForGame = g },
                modifier = (if (only != null) Modifier else if (fill) Modifier.fillMaxWidth().padding(horizontal = if (compactHeader) 16.dp else 24.dp)
                    else Modifier.widthIn(max = 520.dp))
                    // The next game's header rises into place (update 108).
                    .riseOnChange(g.stableKey),
                slots = only == null, only = only,
                category = categoryKeyLabel(state.sortCategoryKey)
            )
        }
    }
    /** Next to the row › game info or news card ([mode] GAME or NEWS), for its place in the layout or moved (Edit Home). */
    @Composable
    fun RowInfoBlock(mode: com.shelf.launcher.data.RowInfo, padStart: Boolean = true, padEnd: Boolean = true) {
        RowInfoPanel(
            padStart = padStart, padEnd = padEnd,
            mode = mode,
            game = focusedGame,
            metadata = focusedGame?.let { state.metadata[it.stableKey] },
            raProgress = focusedGame?.let { state.raProgress[it.stableKey] },
            newsSources = state.appearance.newsSources
        )
    }
    // The classic row's game header can be moved and resized in Edit Home.
    val headerMovable = com.shelf.launcher.data.Features.homeHeader.state.value && focusedGame != null &&
        !tileLayout && !heroLayout && !gridLayout && !tvLayout && !styleLayout

    // Update 185: what's running in the background (a tap on the progress text at the top).
    var showWork by remember { mutableStateOf(false) }
    @Composable
    fun HomeStatusBar(modifier: Modifier, nowPlaying: com.shelf.launcher.data.NowPlaying?, atBottom: Boolean) {
        // Live, so the bell's count is right without opening the panel.
        // Observed only while resumed, so the listener can skip its work while nobody looks.
        val notesResumed = rememberIsResumed()
        val noNotes = remember { kotlinx.coroutines.flow.MutableStateFlow<List<com.shelf.launcher.data.ShelfNotification>>(emptyList()) }
        val liveNotifications by (if (notesResumed) com.shelf.launcher.data.ShelfNotificationListener.notifications else noNotes).collectAsState()
        StatusBar(
            scrapeDone = state.scrapeDone,
            scrapeTotal = state.scrapeTotal,
            raMatchDone = state.raMatchDone,
            raMatchTotal = state.raMatchTotal,
            artSaveDone = state.artSaveDone,
            artSaveTotal = state.artSaveTotal,
            detailsDone = state.metadataScrapeDone,
            detailsTotal = state.metadataScrapeTotal,
            raProfile = state.raProfile,
            onProfileClick = { hubScreen = HubScreen.PROFILE },
            onProgressClick = { showWork = true },
            // Achievements earned just before leaving the game: a trophy until they're opened.
            trophyCount = state.returnTrophies?.takeIf { System.currentTimeMillis() - it.at < 6 * 3_600_000L }?.count ?: 0,
            onTrophy = {
                val rt = state.returnTrophies
                val g = rt?.let { r -> state.games.firstOrNull { it.stableKey == r.gameKey } }
                if (g?.raGameId != null) viewingAchievementsForGame = g else hubScreen = HubScreen.ACHIEVEMENTS
            },
            onPlayPause = { viewModel.mediaPlayPause() },
            onNext = { viewModel.mediaNext() },
            onPrevious = { viewModel.mediaPrevious() },
            showLyrics = state.appearance.miniPlayerLyrics &&
                state.appearance.lyricsPosition == com.shelf.launcher.data.LyricsPosition.WITH_PLAYER,
            miniFocus = miniFocus ?: HomeReach.index.takeIf { HomeReach.zone == HomeReach.Zone.MUSIC },
            onSeek = { viewModel.mediaSeekTo(it) },
            onSearchButtonClick = {
                showSearchBar = !showSearchBar
                if (!showSearchBar) viewModel.setSearchQuery("")
            },
            notificationCount = liveNotifications.count { !it.ongoing },
            onNotifications = { viewModel.refreshNotifications(); notificationsOnly = true; showNotifications = true },
            // Google Drive sync lives in the status bar; tap it for what happened last.
            syncSlot = {
                SessionStatusNote(uiState.sessionNote)
                SyncStatusChip(uiState.syncNotice, uiState.syncNoticeKind) { showSyncStatus = true }
            },
            nowPlaying = nowPlaying,
            atBottom = atBottom,
            modifier = modifier
        )
    }
    @Composable
    fun BoxScope.HomeLayer() {

        // Where the row of games starts (from the top), so "Whole picture"
        // can end exactly there.
        var rowTopPx by remember { mutableStateOf(0f) }
        // …and where it ends, so the "Now playing" panel and wave can keep clear of it.
        var rowBottomPx by remember { mutableStateOf(0f) }
        var homeHeightPx by remember { mutableStateOf(0f) }
        var homeWidthPx by remember { mutableStateOf(0f) }
        Spacer(Modifier.fillMaxSize().onSizeChanged { homeHeightPx = it.height.toFloat(); homeWidthPx = it.width.toFloat() })
        // Top of the focused card's art — the background ends right there.
        var artTopPx by remember { mutableStateOf(0f) }
        // The hero carousel has no row: forget the last row's place, so lyrics and the music player
        // aren't kept clear of a row that isn't there any more.
        if (heroLayout || gridLayout || tvLayout || styleLayout) SideEffect { if (rowTopPx != 0f || rowBottomPx != 0f) { rowTopPx = 0f; rowBottomPx = 0f } }
        // This game's own look (Game settings › Look for this game): its theme plays as the animated
        // background while it's the one you're on, whatever "Behind the dock" is set to.
        val focusedLook = com.shelf.launcher.data.GameLooks.of(focusedGame?.stableKey)
        val gameScene = focusedLook.theme?.let { name -> com.shelf.launcher.ui.theme.AppTheme.values().firstOrNull { it.name == name } }
        val backgroundStyle = if (gameScene != null) com.shelf.launcher.data.BackgroundStyle.ANIMATED else state.appearance.backgroundStyle
        val wallpaper = state.appearance.wallpaperUri
        // "Now playing" scene behind the home screen (its own theme, or picked as the animated scene)?
        // A scene picked for this category (Appearance › Behind the dock) wins over the general pick.
        val categoryKey = state.sortCategoryKey
        SideEffect {
            HomeContext.categoryKey = categoryKey
            HomeContext.categoryLabel = categoryKeyLabel(categoryKey)
        }
        val categoryScene = remember(state.appearance.categoryScenes, categoryKey) {
            com.shelf.launcher.data.CategoryScenes.parse(state.appearance.categoryScenes)[categoryKey]
        }
        val pickedScene = gameScene ?: (categoryScene ?: state.appearance.sceneTheme)?.let { name ->
            com.shelf.launcher.ui.theme.AppTheme.values().firstOrNull { it.name == name }
        }
        val musicScene = backgroundStyle == com.shelf.launcher.data.BackgroundStyle.ANIMATED &&
            (pickedScene?.motif ?: state.appTheme.motif).reactive
        // The lyric themes (Lyric cloud, Karaoke, Lyric sheet, Neon lyrics) show the words themselves: no
        // lyric line in the player or on its own then.
        val lyricCloud = musicScene && (pickedScene?.motif ?: state.appTheme.motif).lyrics
        // The free band beside the game row (above it, below it, or the bigger side when it's in the
        // middle): the panel sits in it and the wave runs through it.
        val musicDensity = androidx.compose.ui.platform.LocalDensity.current
        val musicDock = state.appearance.dockPosition
        val musicReserveTop = with(musicDensity) {
            (if (state.appearance.statusOn && musicDock != com.shelf.launcher.data.DockPosition.TOP) 50.dp else 14.dp).toPx()
        }
        val musicReserveBottom = with(musicDensity) {
            ((if (state.appearance.legendOn) 30.dp else 10.dp) +
                (if (state.appearance.statusOn && musicDock == com.shelf.launcher.data.DockPosition.TOP) 36.dp else 0.dp)).toPx()
        }
        val musicSlot = with(musicDensity) {
            MusicSlot.pick(
                screenH = homeHeightPx,
                dock = musicDock,
                rowTop = rowTopPx,
                rowBottom = rowBottomPx,
                reserveTop = musicReserveTop,
                reserveBottom = musicReserveBottom,
                gap = 10.dp.toPx()
            )
        }
        // Wave, player under it, lyrics (Appearance › Theme › Equalizer / Lyrics: where each goes).
        // Appearance › Theme › On the background › Lyrics off hides them in every music theme.
        val musicLyricsStyle = if (!com.shelf.launcher.data.Features.musicShowLyrics.state.value) com.shelf.launcher.data.MusicLyricsStyle.OFF
            else com.shelf.launcher.data.MusicThemeSettings.lyrics
        val musicLyricsSpot = com.shelf.launcher.data.MusicThemeSettings.lyricsSpot
        // With the player switched off, "In the player" lyrics go on the wave instead of vanishing.
        val lyricsInPlayer = musicLyricsSpot == com.shelf.launcher.data.MusicLyricsSpot.IN_PLAYER &&
            musicLyricsStyle != com.shelf.launcher.data.MusicLyricsStyle.OFF &&
            com.shelf.launcher.data.MusicThemeSettings.controls && !lyricCloud
        // Small screens (the lower screen of a dual-screen handheld, 3.5" devices): smaller lyrics and a
        // lower lyric band, so lines wrap and shrink inside the screen instead of running off it.
        val musicCompact = with(musicDensity) { homeHeightPx > 0f && homeHeightPx.toDp() < 430.dp }
        val musicLyricH = when {
            musicLyricsStyle == com.shelf.launcher.data.MusicLyricsStyle.BIG -> if (musicCompact) 58.dp else 72.dp
            else -> if (musicCompact) 34.dp else 40.dp
        } * (if (!com.shelf.launcher.data.MusicThemeSettings.controls) 1.6f else 1f) // the lyric alone: the player's room too
        val musicLayout = with(musicDensity) {
            MusicLayout.compute(
                screenH = homeHeightPx,
                slot = musicSlot,
                spot = com.shelf.launcher.data.MusicThemeSettings.eqSpot,
                lyricsSpot = musicLyricsSpot,
                reserveTop = musicReserveTop,
                reserveBottom = musicReserveBottom,
                // Room for this scene's visualizer (a ring needs more than a wave), so the player under
                // it never covers it.
                waveHalf = (homeHeightPx * visualizerHalf(pickedScene?.motif ?: state.appTheme.motif) *
                    com.shelf.launcher.data.MusicThemeSettings.vizSize / 100f).coerceAtLeast(28.dp.toPx()),
                minWaveHalf = 16.dp.toPx(),
                // Player: song + buttons + timeline (+ the lyric when it's in the player).
                panelH = (if (lyricsInPlayer) 126.dp else 82.dp).toPx(),
                lyricH = musicLyricH.toPx(),
                gap = 6.dp.toPx(),
                rowTop = rowTopPx, rowBottom = rowBottomPx
            )
        }
        // Side by side (Appearance › Music player › Layout): the visualizer on the left with the whole free
        // height, the player on the right. Only for scenes drawn round a centre (they read uWaveX).
        val musicMotif = pickedScene?.motif ?: state.appTheme.motif
        val musicSide = homeHeightPx > 0f && homeWidthPx > 0f && musicMotif.reactive &&
            "uWaveX" in SceneSource.uniforms(musicMotif) &&
            when (com.shelf.launcher.data.MusicThemeSettings.playerLayout) {
                com.shelf.launcher.data.MusicPlayerLayout.SIDE -> true
                com.shelf.launcher.data.MusicPlayerLayout.STACKED -> false
                com.shelf.launcher.data.MusicPlayerLayout.AUTO -> homeWidthPx / homeHeightPx >= 1.45f && !musicCompact
            }
        val sideLayout = if (!musicSide) null else with(musicDensity) {
            MusicSideLayout.compute(
                screenW = homeWidthPx, screenH = homeHeightPx, slot = musicSlot,
                spot = com.shelf.launcher.data.MusicThemeSettings.eqSpot,
                lyricsSpot = musicLyricsSpot,
                reserveTop = musicReserveTop, reserveBottom = musicReserveBottom,
                wantHalf = homeHeightPx * visualizerHalf(musicMotif) * 1.35f * com.shelf.launcher.data.MusicThemeSettings.vizSize / 100f,
                lyricH = musicLyricH.toPx(), pad = 28.dp.toPx(), gap = 8.dp.toPx(),
                rowTop = rowTopPx, rowBottom = rowBottomPx
            )
        }
        val musicWaveY = if (homeHeightPx > 0f) (sideLayout?.waveY ?: musicLayout.waveY) / homeHeightPx else 0.5f
        val musicWaveH = if (homeHeightPx > 0f) (sideLayout?.waveHalf ?: musicLayout.waveHalf) / homeHeightPx else 0.1f
        val musicWaveX = sideLayout?.waveX ?: 0f
        // The panel replaces the small corner player while this background is on (no two players).
        // Update 122: only on the layouts with a game row the band is worked out from (the Voltcap row). On the
        // full-screen layouts (Grid, Hero, TV room and the style homes) the theme's own player and lyric line ran into
        // their panels, so there the scene is only the background (the layout's own music player stays).
        val sceneHasBand = !heroLayout && !gridLayout && !tvLayout && !styleLayout
        val musicPanelFor = state.nowPlaying?.takeIf {
            musicScene && sceneHasBand && com.shelf.launcher.data.MusicThemeSettings.controls && homeHeightPx > 0f
        }
        SideEffect { HomeReach.musicPanel = musicPanelFor != null }
        // Something covers the whole home screen: the animated background holds a still frame meanwhile.
        // Update 121: only when nothing of it can be seen. A popup that leaves it showing round its edges
        // (Settings, the details window, the app drawer…) lets it move on, at a calmer ~15 fps.
        val sceneCovered = screensaverOn || categoryScreenOpen || guideForGame != null ||
            manualFor != null || artViewer != null || viewingShots != null ||
            // Update 180: a two-screen layout filling the top screen: the theme's animated background rests under it.
            (state.appearance.homeLayout.isTwin && TwinScreens.fullTop) ||
            // Update 202: Green Core covers it with its own backdrop and sphere.
            state.appearance.homeLayout == com.shelf.launcher.data.HomeLayout.GREENCORE ||
            // Update 181: the Pocket layouts always draw their own (paper or the handheld's body).
            state.appearance.homeLayout.isPocket ||
            // Update 202: Pocket Quest drawing its theme's scenery (Layout options › Background): the theme's background rests.
            (state.appearance.homeLayout == com.shelf.launcher.data.HomeLayout.POCKET && pqOwnsBackground()) ||
            // (Update 239, #93: the Cube menu's own room covers it while its backdrop is on.)
            (state.appearance.homeLayout == com.shelf.launcher.data.HomeLayout.CUBE && com.shelf.launcher.data.Features.cubeBackdrop.state.value)
        // Update 154: Disc station keeps what's behind it still (Disc station › Calm background, on by default): the
        // animated background holds a frame, game ambience and backdrop touches rest, no video clip plays.
        val discCalm = state.appearance.homeLayout == com.shelf.launcher.data.HomeLayout.DISCS &&
            com.shelf.launcher.data.Features.discCalm.state.value
        val scenePartlyCovered = !sceneCovered && (showMenu || showSettings || showDetails || showSaveExplorer ||
            showAppsLibrary || showAppDrawer || showFirstRun)
        // The second screen only gets the scene while the animated background is on.
        if (backgroundStyle != com.shelf.launcher.data.BackgroundStyle.ANIMATED) SideEffect { SecondScene.set(null, null) }
        // This theme's own look (Settings › Appearance › Theme look): blur and dim of the backdrop, scene motion.
        val look = com.shelf.launcher.data.Features.lookFor(state.appTheme.name)
        val backdrop: @Composable BoxScope.() -> Unit = {
            when {
                backgroundStyle == com.shelf.launcher.data.BackgroundStyle.BLACK -> Unit // plain background colour
                backgroundStyle == com.shelf.launcher.data.BackgroundStyle.ANIMATED -> {
                    val a = state.appearance
                    // A scene picked in Settings plays with its own colours, whatever theme is active.
                    val sceneMotif = pickedScene?.motif ?: state.appTheme.motif
                    val baseColors = pickedScene?.colors ?: com.shelf.launcher.ui.theme.ShelfTheme.colors
                    // Scene style: colours from the game you're on, the time of day, a colour shift. Not for
                    // the music scenes, which take theirs from the song's cover.
                    // Game showcase scenes always take their colours from the game's own art.
                    val fromCover = a.sceneFromCover || sceneMotif.showcase
                    val styled = !sceneMotif.reactive && (fromCover || a.sceneTimeOfDay || look.sceneHue != 0)
                    val coverModel = focusedGame?.let { g ->
                        com.shelf.launcher.data.ArtPick.backdrop(g, state.artOverrides, state.art, state.screenshots)
                    }
                    val coverColors = rememberArtPalette(coverModel, fromCover && !sceneMotif.reactive)
                    if (sceneMotif.showcase || sceneMotif == com.shelf.launcher.ui.theme.ThemeMotif.RETRO_TV) {
                        // The showcase needs the game's screenshots: looked up once you've rested on a game
                        // for a moment (not for every game the D-pad passes over).
                        val showGame = focusedGame
                        LaunchedEffect(showGame?.stableKey) {
                            if (showGame == null) return@LaunchedEffect
                            kotlinx.coroutines.delay(650)
                            viewModel.loadMedia(showGame)
                        }
                        val showKey = showGame?.stableKey
                        val shots = showKey?.let { state.screenshots[it] }.orEmpty()
                        val heroUrl = showGame?.let { g -> state.artOverrides[g.stableKey]?.hero ?: state.art[g.stableKey]?.heroUrl }
                        val coverUrl = showGame?.let { g -> state.artOverrides[g.stableKey]?.cover ?: state.art[g.stableKey]?.gridUrl }
                        val showcaseArt = remember(showKey, heroUrl, coverUrl, shots) {
                            showKey?.let { ShowcaseArt(it, heroUrl, coverUrl, shots) }
                        }
                        SideEffect { Showcase.art = showcaseArt }
                    }
                    // Scenes that show your games (Specimen lab, Console disc, Poster wall, the console-era music scenes):
                    // the game you're on and the covers of the row round it (SceneArt). The real disc / cartridge picture
                    // is looked up once you've rested on a game for a moment.
                    val artScene = sceneMotif in SceneArt.MEDIA_MOTIFS || sceneMotif == com.shelf.launcher.ui.theme.ThemeMotif.POSTER_WALL ||
                        (sceneMotif in SceneArt.ERA_MOTIFS && com.shelf.launcher.data.Features.eraArt.state.value != com.shelf.launcher.data.Features.EraArt.ALBUM)
                    val artGame = focusedGame.takeIf { artScene }
                    if (artGame != null && sceneMotif in SceneArt.MEDIA_MOTIFS) LaunchedEffect(artGame.stableKey) {
                        kotlinx.coroutines.delay(650)
                        viewModel.loadPhysicalMedia(artGame)
                    }
                    val artKey = artGame?.stableKey
                    val artScan = artKey?.let { k -> state.physicalMedia[k].orEmpty().firstOrNull { it.first == "Disc / cartridge" }?.second }
                    val artCover = artKey?.let { k -> state.artOverrides[k]?.cover ?: state.art[k]?.gridUrl }
                    val artHero = artKey?.let { k -> state.artOverrides[k]?.hero ?: state.art[k]?.heroUrl }
                    val sceneGame = remember(artKey, artScan, artCover, artHero) {
                        artGame?.let { g ->
                            SceneGame(g.stableKey, g.platform, artCover, artHero, artScan, MediaShell.of(g.platform)?.disc == true)
                        }
                    }
                    val wantCovers = artScene && sceneMotif == com.shelf.launcher.ui.theme.ThemeMotif.POSTER_WALL
                    val rowCovers = remember(wantCovers, state.focusedIndex, visibleGames, state.art, state.artOverrides) {
                        if (!wantCovers) emptyList() else buildList {
                            val f = state.focusedIndex
                            for (off in intArrayOf(0, 1, -1, 2, -2, 3, -3, 4, -4, 5, 6, 7)) {
                                val g = visibleGames.getOrNull(f + off) ?: continue
                                val url = state.artOverrides[g.stableKey]?.cover ?: state.art[g.stableKey]?.gridUrl ?: continue
                                if (url !in this) add(url)
                                if (size >= SceneArt.ATLAS_CELLS) break
                            }
                        }
                    }
                    SideEffect {
                        if (SceneArt.game != sceneGame) SceneArt.game = sceneGame
                        if (SceneArt.rowCovers != rowCovers) SceneArt.rowCovers = rowCovers
                    }
                    val hour = rememberHourOfDay(a.sceneTimeOfDay)
                    val sceneColors = if (!styled) baseColors else animatedSceneColors(
                        (if (fromCover && coverColors != null) coverColors else baseColors)
                            .let { if (a.sceneTimeOfDay) it.atTimeOfDay(hour) else it }
                            .hueShifted(look.sceneHue.toFloat())
                    )
                    // Animate only on the second screen: it gets the scene (SecondScene), this one a still frame.
                    // Music scenes go to the second screen by default when it's up (Settings › Screens): on the
                    // main one the games' rows / the TV covered them.
                    val sceneOnSecond = (a.sceneSecondOnly && secondScreenActive) ||
                        (a.musicFxSecond && sceneMotif.reactive && SecondScreenShown.value)
                    // Its own scene on the second screen (Settings › Appearance › Animated background): another motif, but
                    // always drawn in this one colour scheme, so nothing clashes between the two screens.
                    val secondPick = a.secondSceneTheme?.let { n -> com.shelf.launcher.ui.theme.AppTheme.values().firstOrNull { it.name == n } }
                    SideEffect {
                        SecondScene.set(
                            when {
                                secondPick != null && secondScreenActive -> secondPick.motif
                                sceneOnSecond -> sceneMotif
                                else -> null
                            },
                            sceneColors
                        )
                    }
                    // Retro bounce: the console you're on, and the ones in your library.
                    if (sceneMotif == com.shelf.launcher.ui.theme.ThemeMotif.RETRO_BOUNCE) SideEffect {
                        RetroBounceSource.platform = state.platformFilter
                        if (RetroBounceSource.platforms != state.availablePlatforms) RetroBounceSource.platforms = state.availablePlatforms
                    }
                    // The lyric themes keep their words off the status bar, the row, the player and the legend
                    // (and the Gaming room puts its monitor in the biggest open space between them).
                    // Scenes with a subject in the middle (Voltcap, Specimen lab, CRT room, Trophy vault...) slide it down,
                    // clear of a game row at the top.
                    val sceneAvoid = if (!sceneMotif.lyrics && sceneMotif != com.shelf.launcher.ui.theme.ThemeMotif.GAMING_ROOM &&
                        !(sceneMotif == com.shelf.launcher.ui.theme.ThemeMotif.SHOWCASE_CRT || SceneArt.usesShift(sceneMotif))) emptyList() else with(musicDensity) {
                        buildList {
                            add(0f..musicReserveTop)
                            if (rowBottomPx > rowTopPx) add((rowTopPx - 8.dp.toPx())..(rowBottomPx + 8.dp.toPx()))
                            if (musicPanelFor != null && sideLayout == null) add(musicLayout.panelTop..musicLayout.panelBottom)
                            if (homeHeightPx > 0f) add((homeHeightPx - musicReserveBottom)..homeHeightPx)
                        }
                    }
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalSceneAvoid provides sceneAvoid,
                        LocalSceneCovered provides (sceneCovered || sceneOnSecond || discCalm || com.shelf.launcher.data.BatterySaver.low), // (Update 187: the battery saver's low-battery rest: a still frame)
                        LocalSceneMinFrameMs provides (if (scenePartlyCovered) 66 else 0),
                        // The theme's own speed / particles, times Theme intensity (Low · Normal · High).
                        LocalSceneTuning provides com.shelf.launcher.data.GameLooks.intensity(focusedGame?.stableKey).let { k ->
                            SceneTuning(look.sceneSpeed / 100f * k.speed, 0f, look.sceneParticles / 100f * k.amount, look.sceneShift / 100f)
                        }
                    ) {
                        if (a.sceneFade) {
                            // A new scene (theme or category changed) dissolves in over the old one.
                            androidx.compose.animation.Crossfade(
                                targetState = sceneMotif, animationSpec = tween(400), label = "sceneFade"
                            ) { motif ->
                                ThemeScene(motif, sceneColors, Modifier.fillMaxSize(), waveY = musicWaveY, waveH = musicWaveH, waveX = musicWaveX)
                            }
                        } else {
                            ThemeScene(sceneMotif, sceneColors, Modifier.fillMaxSize(), waveY = musicWaveY, waveH = musicWaveH, waveX = musicWaveX)
                        }
                    }
                }
                backgroundStyle == com.shelf.launcher.data.BackgroundStyle.WALLPAPER && wallpaper != null -> {
                    // A picture, or a GIF / animated WebP / video that plays while the home screen is in front.
                    UserWallpaper(wallpaper, Modifier.fillMaxSize())
                    DockScrim()
                }
                else -> {
                    // Blurred background: the pictures of the games either side are fetched ahead (just into the disk cache,
                    // decoded tiny), so moving on shows the next one at once instead of waiting for the download.
                    if (backgroundStyle == com.shelf.launcher.data.BackgroundStyle.BLURRED) {
                        val warmCtx = androidx.compose.ui.platform.LocalContext.current
                        LaunchedEffect(state.focusedIndex, visibleGames.size) {
                            kotlinx.coroutines.delay(200)
                            val loader = coil.Coil.imageLoader(warmCtx)
                            for (off in intArrayOf(1, -1, 2, -2, 3)) {
                                val g = visibleGames.getOrNull(state.focusedIndex + off) ?: continue
                                val url = com.shelf.launcher.data.ArtPick.backdrop(g, state.artOverrides, state.art, state.screenshots) ?: continue
                                loader.enqueue(coil.request.ImageRequest.Builder(warmCtx).data(url).size(64, 64).memoryCachePolicy(coil.request.CachePolicy.DISABLED).build())
                            }
                        }
                    }
                    BackdropArt(
                    focusedGame, state,
                    blurred = backgroundStyle == com.shelf.launcher.data.BackgroundStyle.BLURRED,
                    rowTopPx = if (artTopPx > 0f) artTopPx else rowTopPx,
                    // A lone game leaves the row mostly empty — then the picture
                    // fills the whole screen instead of stopping at the capsule.
                    // Tiles at the bottom: the picture ends right above the tiles too.
                    rowFilled = visibleGames.size > 1 &&
                        // (Update 237, #30: the Voltcap row with Other games in the row › None has no cards to end at.)
                        !(state.appearance.homeLayout == com.shelf.launcher.data.HomeLayout.CLASSIC &&
                            com.shelf.launcher.data.Features.dockOtherGames.state.value == com.shelf.launcher.data.Features.DockOtherGames.NONE)
                    )
                }
            }
        }
        val blurOn = look.blur > 0 && android.os.Build.VERSION.SDK_INT >= 31
        val blurRadius = (look.blur * 0.3f).dp
        val fullBlur = blurOn && look.blurArea == com.shelf.launcher.data.Features.BlurArea.FULL
        // Update 121: blurred on a small copy (lowResBlur) — the same look, a fraction of the work on a moving scene.
        val blurPx = with(androidx.compose.ui.platform.LocalDensity.current) { blurRadius.toPx() }
        Box(Modifier.fillMaxSize().then(if (fullBlur) Modifier.lowResBlur(blurPx) else Modifier)) { backdrop() }
        // The style homes' full-screen layer: the game's art (Side menu / Spotlight / Icon bar), Spotlight's shade,
        // Cross menu's wash, Live tiles' pale backdrop (StyleHomes.kt).
        if (styleLayout) {
            val g = focusedGame
            StyleBackLayer(
                layout = state.appearance.homeLayout,
                gameKey = g?.stableKey,
                artUrl = g?.let { com.shelf.launcher.data.ArtPick.backdrop(it, state.artOverrides, state.art, state.screenshots) },
                topInset = if (state.appearance.statusOn) 46.dp else 0.dp,
                bottomInset = if (state.appearance.legendOn) 28.dp else 0.dp
            )
        }
        // The shade over the game's art (Theme › Background › Shade on the game's art): darker at the left, top,
        // bottom and corners like a console home, so the middle of the picture stands out (HeroShade.kt).
        val artBehind = backgroundStyle == com.shelf.launcher.data.BackgroundStyle.ART || backgroundStyle == com.shelf.launcher.data.BackgroundStyle.BLURRED ||
            (styleLayout && styleArtBehind(state.appearance.homeLayout))
        if (artBehind && !(styleLayout && !styleArtBehind(state.appearance.homeLayout))) HeroShade()
        // A soft light in the colours of the game you're on, behind its card (HomeExtras.kt).
        if (com.shelf.launcher.data.Features.coverLight.state.value && !heroLayout && !tvLayout && !styleLayout) CoverLight(
            focusedGame?.let { g -> state.artOverrides[g.stableKey]?.cover ?: state.art[g.stableKey]?.gridUrl }
        )
        val slideKey = "${state.platformFilter}|${state.specialFilter}"
        androidx.compose.runtime.SideEffect { CategorySlide.key = slideKey }
        // Game UI area only: the band behind the games, their names and the categories.
        val bandDensity = androidx.compose.ui.platform.LocalDensity.current
        val uiBand = with(bandDensity) {
            // Tiles: from the art above the tiles down (like the backing band).
            val bandTop = if (tileLayout && artTopPx > 0f && artTopPx < rowTopPx) artTopPx else rowTopPx
            gameUiBand(homeWidthPx, homeHeightPx, bandTop, rowBottomPx, heroLayout, 96.dp.toPx(), 56.dp.toPx())
                // Grid: the games fill the screen, so the game-UI area is all of it.
                ?: if ((gridLayout || styleLayout) && homeWidthPx > 0f && homeHeightPx > 0f) androidx.compose.ui.geometry.Rect(0f, 0f, homeWidthPx, homeHeightPx) else null
        }
        val bandFeather = with(bandDensity) { 46.dp.toPx() }
        val bandArea = look.blurArea == com.shelf.launcher.data.Features.BlurArea.GAME_UI && uiBand != null
        if (bandArea && blurOn && backgroundStyle != com.shelf.launcher.data.BackgroundStyle.BLACK) {
            // Soft edges (no hard line where the blur stops), HomeExtras.kt.
            SoftBandBlur(uiBand!!, blurRadius) { backdrop() }
        }
        // Readability: a light blur and a soft shade behind just the games, their names and the categories,
        // fading out above and below (Appearance › Interface style › Soft blur behind the games).
        val rowBlur = com.shelf.launcher.data.Features.rowBlur.state.value
        if (rowBlur > 0 && uiBand != null && !heroLayout && !gridLayout && !styleLayout && !(bandArea && blurOn) && !fullBlur) {
            SoftRowBlur(uiBand, rowBlur, liteBlur = LocalLiteEffects.current || backgroundStyle == com.shelf.launcher.data.BackgroundStyle.BLACK) { backdrop() }
        }
        // Dim / overlay (and a frosted wash where real blur isn't available, Android 11 and older).
        val frost = if (!blurOn && look.blur > 0) look.blur / 100f * 0.35f else 0f
        if (look.dim > 0 || frost > 0f) {
            val dimA = look.dim / 100f * 0.85f
            val wash = com.shelf.launcher.ui.theme.ShelfTheme.colors.surface
            val onlyBand = look.blurArea == com.shelf.launcher.data.Features.BlurArea.GAME_UI
            if (!onlyBand || uiBand != null) Spacer(Modifier.fillMaxSize().drawBehind {
                val band = if (onlyBand) uiBand else null
                // Game UI only: the shade fades out softly instead of stopping at a hard line.
                if (band != null) {
                    if (frost > 0f) drawRect(softBandBrush(band, size.width, size.height, bandFeather, wash.copy(alpha = frost)))
                    if (dimA > 0f) drawRect(softBandBrush(band, size.width, size.height, bandFeather, Color.Black.copy(alpha = dimA)))
                } else {
                    if (frost > 0f) drawRect(wash.copy(alpha = frost))
                    if (dimA > 0f) drawRect(Color.Black.copy(alpha = dimA))
                }
            })
        }

        // This game's CRT treatment and ambience (Game settings › Look for this game), in the art's colours.
        val wantsCrt = focusedLook.crt == true && backgroundStyle != com.shelf.launcher.data.BackgroundStyle.BLACK
        val ambience = focusedLook.ambience?.takeIf { com.shelf.launcher.data.Features.gameAmbience.state.value }
        if (wantsCrt || ambience != null) {
            val lookArt = com.shelf.launcher.data.ArtPick.backdrop(focusedGame, state.artOverrides, state.art, state.screenshots)
            val lookColors = rememberArtPalette(lookArt, enabled = !com.shelf.launcher.data.Features.themeColorsOnly.state.value)
            val glow = lookColors?.accent ?: com.shelf.launcher.ui.theme.ShelfTheme.accent
            if (wantsCrt) CrtArtOverlay(glow)
            if (ambience != null) GameAmbienceLayer(
                ambience, glow, lookColors?.secondary ?: com.shelf.launcher.ui.theme.ShelfTheme.colors.secondary,
                covered = sceneCovered || discCalm
            )
        }

        // Trophy vault: your latest mastered games and unlocked badges for its pedestals (also its previews).
        val steamRecent = com.shelf.launcher.data.SteamTrophies.recent.value
        val steamMastered = com.shelf.launcher.data.SteamTrophies.mastered.value
        // (Update 119: and the unlocked achievements of your games' own lists — not only the last 30 days' — or only the
        // mastered games, as set.)
        val masteredOnly = com.shelf.launcher.data.Features.trophyMasteredOnly.state.value
        val moreTrophies = remember(state.raAchievementDetails) {
            state.raAchievementDetails.values.flatten().filter { it.isUnlocked }
                .mapNotNull { a -> com.shelf.launcher.data.RaBadges.url(a.badgeName)?.let { it to TrophyShelf.raTime(a.dateEarnedHardcore ?: a.dateEarned) } }
        }
        val trophyPicks = remember(state.raCompletionById, state.raRecentAchievements, steamRecent, steamMastered, state.art, state.artOverrides, moreTrophies, masteredOnly) {
            TrophyShelf.pick(state.raCompletionById.values, state.raRecentAchievements, steamMastered, steamRecent, more = moreTrophies, masteredOnly = masteredOnly) { appId ->
                // A 100 %'d Steam game: its cover from the library, else Steam's own.
                state.games.firstOrNull { it.steamAppId == appId }?.let { g -> state.artOverrides[g.stableKey]?.cover ?: state.art[g.stableKey]?.gridUrl }
                    ?: "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/library_600x900.jpg"
            }
        }
        // Badge bounce: more of them (up to 16), the same mix.
        val bouncePicks = remember(state.raCompletionById, state.raRecentAchievements, steamRecent, steamMastered, state.art, state.artOverrides, moreTrophies, masteredOnly) {
            TrophyShelf.pick(state.raCompletionById.values, state.raRecentAchievements, steamMastered, steamRecent, limit = BadgeBounce.MAX, more = moreTrophies, masteredOnly = masteredOnly) { appId ->
                state.games.firstOrNull { it.steamAppId == appId }?.let { g -> state.artOverrides[g.stableKey]?.cover ?: state.art[g.stableKey]?.gridUrl }
                    ?: "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/library_600x900.jpg"
            }
        }
        SideEffect {
            TrophyShelf.items = trophyPicks
            if (BadgeBounce.items != bouncePicks) BadgeBounce.items = bouncePicks
        }
        val vaultOn = backgroundStyle == com.shelf.launcher.data.BackgroundStyle.ANIMATED &&
            (pickedScene?.motif ?: state.appTheme.motif) in setOf(
                com.shelf.launcher.ui.theme.ThemeMotif.TROPHY_VAULT, com.shelf.launcher.ui.theme.ThemeMotif.BADGE_BOUNCE
            )
        LaunchedEffect(vaultOn, state.raState) {
            if (!vaultOn || state.raState != RaConnectionState.ON) return@LaunchedEffect
            // The last 30 days of unlocks, and which games are mastered (cached; fetched if there's nothing yet).
            if (state.raRecentAchievements.isEmpty()) viewModel.loadRecentAchievements(minutesBack = 43_200)
            if (state.raCompletionById.isEmpty()) viewModel.refreshRaCompletion()
            // (Update 119: and the achievement lists of the games you played last, for their badges.)
            state.games.filter { it.raGameId != null && state.raAchievementDetails[it.stableKey] == null }
                .sortedByDescending { state.lastPlayedAt[it.stableKey] ?: 0L }.take(8)
                .forEach { g -> viewModel.loadAchievementDetails(g); kotlinx.coroutines.delay(400) }
        }
        // Steam: the recently played games' achievements (each re-read at most every 30 min; the game you're
        // playing every 90 s from the unlock watch).
        val trophyContext = androidx.compose.ui.platform.LocalContext.current
        LaunchedEffect(vaultOn) {
            if (!vaultOn) return@LaunchedEffect
            val played = state.games.filter { it.steamAppId != null }
                .sortedByDescending { state.lastPlayedAt[it.stableKey] ?: 0L }
                .mapNotNull { it.steamAppId }.take(8)
            while (true) {
                com.shelf.launcher.data.SteamTrophies.refresh(trophyContext, played)
                kotlinx.coroutines.delay(30 * 60_000L)
            }
        }

        // Achievement touches inside the backdrop (Appearance › Achievements › In the backdrop).
        if (com.shelf.launcher.data.Features.backdropAchievements.state.value.isNotEmpty()) {
            val recentBadges = remember(state.raRecentAchievements) {
                state.raRecentAchievements.mapNotNull { com.shelf.launcher.data.RaBadges.url(it.BadgeName) }.distinct().take(5)
            }
            val prog = focusedGame?.let { state.raProgress[it.stableKey] }?.let { p ->
                val total = p.NumPossibleAchievements ?: 0
                if (total > 0) (p.NumAchieved ?: 0).toFloat() / total else null
            }
            BackdropAchievementsLayer(recentBadges, prog, covered = sceneCovered || discCalm, modifier = Modifier.fillMaxSize())
        }

        // Video preview: rest on a game for two seconds and its clip fades in.
        var videoReadyFor by remember { mutableStateOf<String?>(null) }
        // (Update 187: not while the battery saver rests — low battery, or a game / another app has the controller.)
        val videoRest = com.shelf.launcher.data.BatterySaver.resting
        LaunchedEffect(focusedGame?.stableKey, state.appearance.videoPreviews, videoRest) {
            videoReadyFor = null
            val game = focusedGame ?: return@LaunchedEffect
            if (!state.appearance.videoPreviews || videoRest || com.shelf.launcher.CrashGuard.safeMode) return@LaunchedEffect
            // (Update 231, #59: the clip is looked up only once you've stayed on the game 0.4 s, not on every game passed —
            // the look-up and the state change it makes cost a frame each step. Still fades in 2 s after you stop.)
            kotlinx.coroutines.delay(400)
            viewModel.loadVideoUrl(game)
            kotlinx.coroutines.delay(1600)
            videoReadyFor = game.stableKey
        }
        val videoUrl = focusedGame?.stableKey?.let { key -> state.videoUrls[key]?.takeIf { videoReadyFor == key } }
        // The Gaming room plays the clip on its monitor instead of across the whole screen.
        val roomClip = backgroundStyle == com.shelf.launcher.data.BackgroundStyle.ANIMATED &&
            (pickedScene?.motif ?: state.appTheme.motif) == com.shelf.launcher.ui.theme.ThemeMotif.GAMING_ROOM &&
            android.os.Build.VERSION.SDK_INT >= 33
        // (Update 151: not on the Hero carousel, which plays it over its hero picture: one clip at a time.)
        val roomVideo = if (roomClip && !screensaverOn && !heroLayout) videoUrl else null
        val roomSound = state.appearance.videoSound
        SideEffect { Showcase.video = roomVideo; Showcase.videoSound = roomSound }
        // Update 151: the Hero carousel plays it over its big hero picture (below), not under it where it was hidden.
        // (Update 159: nor over a style layout's own solid background — pale backdrops, the bookcase, the circuit board.)
        // (Update 307: one clip fading into the next and out when it's put away — VideoCrossfade — and looping on while the
        // home screen rests: only a low battery holds its frame now. Update 221 held it after 30 s without input.)
        // Update 313: Video previews are allowed as a fullscreen background only
        // when the user has selected game art or blurred game art. A clip must
        // never replace Your wallpaper or an animated theme (including the
        // per-game theme selected above). The Gaming room's video monitor
        // remains independent and is intentionally not affected.
        val backdropClip = videoUrl?.takeIf {
            allowsFullscreenGameplayVideo(backgroundStyle) &&
                !roomClip && !screensaverOn && !heroLayout && !discCalm &&
                !(styleLayout && styleOwnsBackground(state.appearance.homeLayout))
        }
        VideoCrossfade(backdropClip, state.appearance.videoSound, Modifier.fillMaxSize(), paused = com.shelf.launcher.data.BatterySaver.low)

        // Hero carousel: the focused game's hero fills the screen (under the status bar).
        if (heroLayout) {
            val hg = focusedGame
            // The heroes either side of the focused game are decoded ahead (a few only), so moving
            // through the carousel shows them at once.
            val heroCtx = androidx.compose.ui.platform.LocalContext.current
            val heroScreen = screenSizeDp()
            val heroD = androidx.compose.ui.platform.LocalDensity.current.density
            LaunchedEffect(state.focusedIndex, visibleGames.size) {
                kotlinx.coroutines.delay(150)
                val i0 = state.focusedIndex
                val urls = (-2..2).filter { it != 0 }.mapNotNull { off ->
                    // (Update 156: going round, the first games are next after the last.)
                    visibleGames.getOrNull((i0 + off).let { if (state.appearance.loopRow && visibleGames.size >= 2) it.mod(visibleGames.size) else it })?.let { g ->
                        com.shelf.launcher.data.ArtPick.backdrop(g, state.artOverrides, state.art, state.screenshots)?.let { it to g.stableKey }
                    }
                }
                prefetchHeroes(
                    heroCtx, urls,
                    (heroScreen.width.value * heroD).toInt().coerceIn(320, 1920), (heroScreen.height.value * heroD).toInt().coerceIn(240, 1080)
                )
            }
            HeroCarouselBackdrop(
                heroUrl = com.shelf.launcher.data.ArtPick.backdrop(hg, state.artOverrides, state.art, state.screenshots),
                fallbackUrl = hg?.let { g -> state.artOverrides[g.stableKey]?.cover ?: state.art[g.stableKey]?.gridUrl },
                gameKey = hg?.stableKey
            )
            // Update 151: rest on a game with a scraped clip and it plays over the hero picture (Settings › Music & sound ›
            // Video previews turns it off). It fades in on its first frame, so until then the hero stays.
            // (Not when the info panel plays it already, Detail layers › Preview video: one clip at a time.)
            // (Update 307: fading from clip to clip, looping on while idle — VideoCrossfade.)
            VideoCrossfade(
                videoUrl?.takeIf {
                    allowsFullscreenGameplayVideo(backgroundStyle) &&
                        !screensaverOn && !com.shelf.launcher.data.Features.detailLayerVideo.state.value
                },
                state.appearance.videoSound, Modifier.fillMaxSize(), paused = com.shelf.launcher.data.BatterySaver.low
            )
        }
        // The status bar's real height (taller with the music player in it), so what sits under it
        // (grid, TV room, corner visualizers and shortcuts) starts below it instead of on the player.
        var statusBarHeightPx by remember { androidx.compose.runtime.mutableIntStateOf(0) }
        // (Update 125: not over the screensaver.)
        val barHere = state.appearance.statusOn && !screensaverOn
        val statusBarDp = if (barHere) with(androidx.compose.ui.platform.LocalDensity.current) { statusBarHeightPx.toDp() } else 0.dp
        if (barHere) HomeStatusBar(
            // The player sits in the status bar when it's top left; other
            // corners get their own spot (below).
            nowPlaying = state.nowPlaying?.takeIf {
                state.appearance.miniPlayer && musicPanelFor == null &&
                    state.appearance.musicPlayerPosition == com.shelf.launcher.data.MusicPlayerPosition.TOP_LEFT &&
                    // Moved in Edit Home: it sits where it was put instead of in the status bar.
                    HomeWidgets.position(HomeWidgets.MUSIC) == null && !HomeEditState.active
            },
            // With the games at the top, clock / battery / Wi-Fi go to the bottom.
            atBottom = state.appearance.dockPosition == com.shelf.launcher.data.DockPosition.TOP && !gridLayout && !tvLayout && !styleLayout,
            // Grid and TV room fill the screen: the status bar is fixed at the top there and drawn above the games, never covered.
            modifier = (if (state.appearance.dockPosition == com.shelf.launcher.data.DockPosition.TOP && !gridLayout && !tvLayout && !styleLayout) {
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (state.appearance.legendOn) 32.dp else 0.dp)
            } else {
                Modifier.align(Alignment.TopCenter).zIndex(if ((gridLayout || tvLayout || styleLayout) && !showDetails) 5f else 0f)
            }).onSizeChanged { statusBarHeightPx = it.height }
        )

        // Corner visualizers (Appearance › Music player): small space scenes moving with the music.
        // With the second screen up they play there instead (Settings › Screens › Music effects).
        if (!(state.appearance.musicFxSecond && SecondScreenShown.value)) CornerVisualizers(
            playing = state.nowPlaying?.isPlaying == true,
            colors = musicSceneColors(),
            topInset = if (state.appearance.statusOn && state.appearance.dockPosition != com.shelf.launcher.data.DockPosition.TOP) maxOf(48.dp, statusBarDp + 6.dp) else 10.dp,
            bottomInset = if (state.appearance.legendOn) 34.dp else 10.dp,
            busy = buildSet {
                // The round shortcuts folded into a corner ☰.
                if (state.appearance.tileShortcuts && state.appearance.shortcutSpot != com.shelf.launcher.data.ShortcutSpot.ROW) when (state.appearance.shortcutSpot) {
                    com.shelf.launcher.data.ShortcutSpot.TOP_LEFT -> add(Alignment.TopStart)
                    com.shelf.launcher.data.ShortcutSpot.TOP_RIGHT -> add(Alignment.TopEnd)
                    com.shelf.launcher.data.ShortcutSpot.BOTTOM_LEFT -> add(Alignment.BottomStart)
                    com.shelf.launcher.data.ShortcutSpot.BOTTOM_RIGHT -> add(Alignment.BottomEnd)
                    else -> Unit
                }
                // The corner music player (when it isn't inside the status bar and wasn't moved in Edit Home).
                if (state.appearance.miniPlayer && state.nowPlaying != null && HomeWidgets.position(HomeWidgets.MUSIC) == null) when (state.appearance.musicPlayerPosition) {
                    com.shelf.launcher.data.MusicPlayerPosition.TOP_LEFT -> if (!state.appearance.statusOn) add(Alignment.TopStart)
                    com.shelf.launcher.data.MusicPlayerPosition.TOP_RIGHT -> add(Alignment.TopEnd)
                    com.shelf.launcher.data.MusicPlayerPosition.BOTTOM_LEFT -> add(Alignment.BottomStart)
                    com.shelf.launcher.data.MusicPlayerPosition.BOTTOM_RIGHT -> add(Alignment.BottomEnd)
                }
            }
        )
        // Round shortcuts moved in Edit Home: they float wherever they were put (drag / D-pad there).
        if (shortcutsFloating) {
            MovableWidget(
                HomeWidgets.SHORTCUTS,
                defaultAlign = if (state.appearance.dockPosition == com.shelf.launcher.data.DockPosition.BOTTOM) Alignment.TopCenter else Alignment.BottomCenter,
                defaultPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
            ) {
                HomeShortcutBar(
                    tileShortcuts, tileBarFocus ?: HomeReach.index.takeIf { HomeReach.zone == HomeReach.Zone.SHORTCUTS },
                    themed = state.appearance.tileShortcutTheme,
                    highlight = state.appearance.shortcutHighlight, fillWidth = false
                )
            }
        }
        // Round shortcuts as a small ☰ in a corner (any home style). It steps clear of the status bar,
        // the music player in that corner and the game row (a corner on the row's side flips to the other side).
        val spot = state.appearance.shortcutSpot
        // (Not on a style home that has them in its own menu already: the corner ☰ sat on its title / menu.)
        if (state.appearance.tileShortcuts && spot != com.shelf.launcher.data.ShortcutSpot.ROW && spot != com.shelf.launcher.data.ShortcutSpot.FREE &&
            !HomeEditState.active && !(styleLayout && styleHasShortcuts(state.appearance.homeLayout))) {
            val dock = state.appearance.dockPosition
            var top = spot == com.shelf.launcher.data.ShortcutSpot.TOP_LEFT || spot == com.shelf.launcher.data.ShortcutSpot.TOP_RIGHT
            val start = spot == com.shelf.launcher.data.ShortcutSpot.TOP_LEFT || spot == com.shelf.launcher.data.ShortcutSpot.BOTTOM_LEFT
            if (top && dock == com.shelf.launcher.data.DockPosition.TOP) top = false
            if (!top && dock == com.shelf.launcher.data.DockPosition.BOTTOM) top = true
            val statusTop = state.appearance.statusOn && dock != com.shelf.launcher.data.DockPosition.TOP
            val statusBottom = state.appearance.statusOn && dock == com.shelf.launcher.data.DockPosition.TOP
            val playerCorner = state.appearance.musicPlayerPosition.takeIf { state.appearance.miniPlayer && state.nowPlaying != null }
            val playerHere = playerCorner != null && when (playerCorner) {
                com.shelf.launcher.data.MusicPlayerPosition.TOP_LEFT -> top && start
                com.shelf.launcher.data.MusicPlayerPosition.TOP_RIGHT -> top && !start
                com.shelf.launcher.data.MusicPlayerPosition.BOTTOM_LEFT -> !top && start
                com.shelf.launcher.data.MusicPlayerPosition.BOTTOM_RIGHT -> !top && !start
            }
            val edge = (if (top && statusTop || !top && statusBottom) maxOf(44.dp, statusBarDp + 6.dp) else 10.dp) +
                (if (playerHere) 64.dp else 0.dp) +
                (if (!top && state.appearance.legendOn) 28.dp else 0.dp)
            CornerShortcuts(
                shortcuts = tileShortcuts,
                themed = state.appearance.tileShortcutTheme,
                top = top,
                start = start,
                // Reached with the D-pad (HomeReach): they fold out and the one you're on is ringed.
                focused = HomeReach.index.takeIf { HomeReach.zone == HomeReach.Zone.SHORTCUTS },
                modifier = Modifier
                    // Over the covers, never behind them.
                    .zIndex(5f)
                    .align(
                        when {
                            top && start -> Alignment.TopStart
                            top -> Alignment.TopEnd
                            start -> Alignment.BottomStart
                            else -> Alignment.BottomEnd
                        }
                    )
                    .padding(
                        start = if (start) 12.dp else 0.dp, end = if (start) 0.dp else 12.dp,
                        top = if (top) edge else 0.dp, bottom = if (top) 0.dp else edge
                    )
            )
        }

        // Music player in another corner (Audio › Music player & lyrics).
        // (Also top left when the status bar is hidden — it normally lives there.)
        // (Not in a style home's corner: it sat on the Side menu's row title and the Spotlight / Icon bar text. Moved by
        // hand in Edit Home it still shows where it was put.)
        val playerElsewhere = state.nowPlaying?.takeIf {
            !(styleLayout && HomeWidgets.position(HomeWidgets.MUSIC) == null && !HomeEditState.active) &&
            state.appearance.miniPlayer && musicPanelFor == null &&
                (state.appearance.musicPlayerPosition != com.shelf.launcher.data.MusicPlayerPosition.TOP_LEFT ||
                    !state.appearance.statusOn || HomeWidgets.position(HomeWidgets.MUSIC) != null || HomeEditState.active)
        }
        // Lyrics on their own line at the top or bottom of the screen.
        val lyricsPos = state.appearance.lyricsPosition
        val lyricsFor = state.nowPlaying?.takeIf {
            state.appearance.miniPlayer && state.appearance.miniPlayerLyrics && musicPanelFor == null &&
                lyricsPos != com.shelf.launcher.data.LyricsPosition.WITH_PLAYER &&
                // A music theme showing its own lyrics (or Lyric cloud) already has them: never two at once.
                !(musicScene && (lyricCloud || musicLyricsStyle != com.shelf.launcher.data.MusicLyricsStyle.OFF))
        }
        if (lyricsFor != null) {
            // "Away from the cover's subject": the calmer half of the cover (MusicTheme.findSubject).
            val lyricsTop = when (lyricsPos) {
                com.shelf.launcher.data.LyricsPosition.TOP -> true
                com.shelf.launcher.data.LyricsPosition.AWAY -> MusicTheme.calmTop
                else -> false
            }
            // Under the status bar / above the legend — or beside the game row when it sits right there
            // (it used to be drawn underneath the row, so moving it to that edge seemed to do nothing).
            val edges = with(musicDensity) {
                lyricEdges(homeHeightPx, musicReserveTop, musicReserveBottom, 40.dp.toPx(), 8.dp.toPx(), rowTopPx, rowBottomPx)
            }
            PlaceLyricLine(
                topPx = if (lyricsTop) edges.first else null,
                bottomPx = if (lyricsTop) null else edges.second,
                sidePadding = 16.dp,
                align = com.shelf.launcher.data.Features.lyricsAlign.state.value,
                modifier = Modifier.zIndex(4f)
            ) {
                Box(
                    Modifier
                        // Update 202: moved / resized / taken off in Edit Home (each layout its own).
                        .editablePart(LayoutParts.id(state.appearance.homeLayout, "lyrics"), "Lyrics line")
                        .lyricFloat()
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    MiniLyricLine(lyricsFor, standalone = true)
                }
            }
        }
        if (playerElsewhere != null || HomeEditState.active) {
            val pos = state.appearance.musicPlayerPosition
            val bottom = pos == com.shelf.launcher.data.MusicPlayerPosition.BOTTOM_LEFT ||
                pos == com.shelf.launcher.data.MusicPlayerPosition.BOTTOM_RIGHT
            val right = pos == com.shelf.launcher.data.MusicPlayerPosition.TOP_RIGHT ||
                pos == com.shelf.launcher.data.MusicPlayerPosition.BOTTOM_RIGHT
            // Movable in Edit Home; until moved it sits in the corner picked in Settings.
            MovableWidget(
                HomeWidgets.MUSIC,
                defaultAlign = when {
                    bottom && right -> Alignment.BottomEnd
                    bottom -> Alignment.BottomStart
                    right -> Alignment.TopEnd
                    else -> Alignment.TopStart
                },
                // Clear of the status bar at the top and the legend at the bottom.
                defaultPadding = PaddingValues(
                    start = 14.dp, end = 14.dp,
                    top = if (bottom) 0.dp else 52.dp,
                    bottom = if (bottom) (if (state.appearance.legendOn) 36.dp else 10.dp) else 0.dp
                )
            ) {
                if (playerElsewhere == null) MusicPlayerPlaceholder() else MiniMusicPlayer(
                    playerElsewhere,
                    onPlayPause = { viewModel.mediaPlayPause() },
                    onNext = { viewModel.mediaNext() },
                    onPrevious = { viewModel.mediaPrevious() },
                    showLyrics = state.appearance.miniPlayerLyrics &&
                        state.appearance.lyricsPosition == com.shelf.launcher.data.LyricsPosition.WITH_PLAYER,
                    focusedSlot = miniFocus ?: HomeReach.index.takeIf { HomeReach.zone == HomeReach.Zone.MUSIC },
                    onSeek = { viewModel.mediaSeekTo(it) }
                )
            }
        }

        // "Now playing" background: its small player (song, ⏮ ⏯ ⏭, timeline, lyric) in the free band
        // beside the game row — above it, below it, or on the roomier side when the row is in the middle.
        // Always right under the equalizer wave, centred like it, with the same controls everywhere.
        // Where the panel's buttons are for the D-pad (HomeReach): above or below the game row.
        if (musicPanelFor != null) SideEffect {
            HomeReach.musicPanelUp = (sideLayout?.panelTop ?: musicLayout.panelTop) < rowTopPx
        }
        val panelFocus = HomeReach.index.takeIf { HomeReach.zone == HomeReach.Zone.MUSIC }
        if (musicPanelFor != null && sideLayout != null) {
            // Side by side: the player centred in its column right of the visualizer.
            PlaceInBox(sideLayout.panelLeft, sideLayout.panelTop, sideLayout.panelRight, sideLayout.panelBottom) {
                NowPlayingPanel(
                    playing = musicPanelFor,
                    colors = musicSceneColors(),
                    lyricsStyle = if (lyricsInPlayer) musicLyricsStyle else com.shelf.launcher.data.MusicLyricsStyle.OFF,
                    onPlayPause = { viewModel.mediaPlayPause() },
                    onNext = { viewModel.mediaNext() },
                    onPrevious = { viewModel.mediaPrevious() },
                    onSeek = { viewModel.mediaSeekTo(it) },
                    // Update 202: moved / resized / taken off in Edit Home (each layout its own).
                    modifier = Modifier.editablePart(LayoutParts.id(state.appearance.homeLayout, "nowplaying"), "Now playing card"),
                    focusedSlot = panelFocus
                )
            }
        } else if (musicPanelFor != null) {
            PlaceCentredBelow(musicLayout.panelTop, musicLayout.panelBottom, sidePadding = if (musicCompact) 12.dp else 28.dp) {
                NowPlayingPanel(
                    playing = musicPanelFor,
                    colors = musicSceneColors(),
                    lyricsStyle = if (lyricsInPlayer) musicLyricsStyle else com.shelf.launcher.data.MusicLyricsStyle.OFF,
                    onPlayPause = { viewModel.mediaPlayPause() },
                    onNext = { viewModel.mediaNext() },
                    onPrevious = { viewModel.mediaPrevious() },
                    onSeek = { viewModel.mediaSeekTo(it) },
                    // Update 202: moved / resized / taken off in Edit Home (each layout its own).
                    modifier = Modifier.editablePart(LayoutParts.id(state.appearance.homeLayout, "nowplaying"), "Now playing card"),
                    focusedSlot = panelFocus
                )
            }
        }
        // Lyrics on their own: on the wave, or at the top / bottom of the screen.
        val sceneLyricsFor = state.nowPlaying
        if (musicScene && sceneHasBand && sceneLyricsFor != null && homeHeightPx > 0f && !lyricsInPlayer && !lyricCloud &&
            musicLyricsStyle != com.shelf.launcher.data.MusicLyricsStyle.OFF
        ) {
            PlaceCentredOn(
                sideLayout?.lyricY ?: musicLayout.lyricY, sidePadding = if (musicCompact) 16.dp else 40.dp,
                centreXPx = sideLayout?.lyricX, maxWidthPx = sideLayout?.lyricMaxW,
                align = com.shelf.launcher.data.Features.lyricsAlign.state.value
            ) {
                NowPlayingLyricLine(
                    sceneLyricsFor, musicLyricsStyle,
                    musicSceneColors(),
                    maxHeightPx = with(musicDensity) { musicLyricH.roundToPx() },
                    compact = musicCompact
                )
            }
        }

        if (showSearchBar) {
            SearchBar(
                query = state.searchQuery,
                onQueryChange = { viewModel.setSearchQuery(it) },
                onDone = {
                    showSearchBar = false
                    // (Update 258: nothing here but something in your library — All games with it.)
                    viewModel.searchEverywhereIfEmpty()
                    // (Update 258, RP Classic: the D-pad back on the games — after a tap on a layout's Search button its
                    // top row kept the selection, and A opened the search again instead of starting the game found.)
                    StyleNav.reset()
                    sounds?.play(UiSound.SELECT)
                    runCatching { rootFocusRequester.requestFocus() }
                },
                onCancel = {
                    showSearchBar = false
                    StyleNav.reset()
                    viewModel.setSearchQuery("")
                    sounds?.play(UiSound.BACK)
                    runCatching { rootFocusRequester.requestFocus() }
                },
                // With the games at the top it goes to the bottom (it used to sit on the row), clear of the
                // status bar and legend there; else under the status bar.
                // 0.6.15 (#120, RP Classic Spotlight): over a layout's own top row (status bar off) the layout was drawn
                // over the bar — the typed text ran under its icons. The bar now sits above the layout, solid enough to read.
                solid = styleLayout && !state.appearance.statusOn,
                modifier = if (state.appearance.dockPosition == com.shelf.launcher.data.DockPosition.TOP) Modifier
                    .zIndex(5.5f)
                    .align(Alignment.BottomCenter)
                    .padding(bottom = (if (state.appearance.statusOn) 44.dp else 12.dp) + (if (state.appearance.legendOn) 30.dp else 0.dp))
                else Modifier
                    .zIndex(5.5f)
                    .align(Alignment.TopCenter)
                    .padding(top = if (state.appearance.statusOn) 64.dp else 20.dp)
            )
        }

        // The button legend; R3 or Appearance › Home screen shows / hides it.
        @Composable
        fun LegendBar() {
            ControlBar(
                onMenuClick = { showMenu = true },
                onFavorite = { focusedGame?.let { viewModel.toggleFavorite(it) } },
                onShortcuts = { showShortcuts = true },
                onNotifications = {
                    viewModel.refreshNotifications()
                    notificationsOnly = true; showNotifications = true
                },
                onAchievements = {
                    focusedGame?.let { game ->
                        if (game.raGameId != null) viewingAchievementsForGame = game
                        else viewModel.setStatusMessage(
                            "No RetroAchievements ID linked for ${game.name} yet — use the trophy icon to link one."
                        )
                    }
                },
                onOptions = { focusedGame?.let { optionsForGame = it } },
                onSelect = { if (focusedGame != null) openDetails() },
                onBack = {
                    if (showSearchBar) {
                        showSearchBar = false
                        viewModel.setSearchQuery("")
                    } else if (state.appearance.categoryScreen != com.shelf.launcher.data.CategoryScreenMode.OFF) {
                        openCategoryScreen()
                    }
                },
                onHelp = { showControlsHelp = true },
                onNextCategory = { viewModel.cycleCategory(forward = true); CategorySlide.step(1) },
                onPrevCategory = { viewModel.cycleCategory(forward = false); CategorySlide.step(-1) },
                continueButton = state.appearance.hotkeys.firstOrNull {
                    it.action == com.shelf.launcher.data.HotkeyAction.LAST_GAME
                }?.button,
                onContinue = {
                    state.appearance.hotkeys.firstOrNull { it.action == com.shelf.launcher.data.HotkeyAction.LAST_GAME }
                        ?.let { runHotkey(it) }
                },
                onQuickJump = {
                    if (availableLetters.isNotEmpty()) {
                        val here = focusedGame?.let { com.shelf.launcher.quickJumpLetter(it.name) }
                        quickJump = here?.takeIf { it in availableLetters }
                            ?: QUICK_JUMP_LETTERS.first { it in availableLetters }
                    }
                }
            )
        }

        // The tile home's name position, shared with the category label (see TileNameAnchor).
        val tileNameAnchor = remember { TileNameAnchor() }

        // Categories line up with the row: the game you're on in the middle → centred,
        // held at the right → right-aligned, otherwise from the left end.
        fun categoryAlign(): Alignment.Horizontal = if (tileLayout) when (state.appearance.tileFocus) {
            com.shelf.launcher.data.TileFocus.CENTER -> Alignment.CenterHorizontally
            com.shelf.launcher.data.TileFocus.RIGHT -> Alignment.End
            else -> Alignment.Start
        } else if (state.appearance.focusPosition == com.shelf.launcher.data.FocusPosition.CENTER && !pinnedFirstOn(state.appearance)) Alignment.CenterHorizontally
        else Alignment.Start

        // Category bar above the row, or under it (Appearance › Game row).
        @Composable
        fun CategoriesBar() {
            if (state.games.isNotEmpty()) Box(Modifier) {
                // (Update 212: the category bar is this layout's own category title, CategoryTitle.kt.)
                if (state.appearance.categoryBar != com.shelf.launcher.data.CategoryBar.OFF) CategoryTitle.Native()
                when (state.appearance.categoryBar) {
                    com.shelf.launcher.data.CategoryBar.FULL -> PlatformFilterRow(
                        bottomGap = state.appearance.infoSpacing.dp.dp,
                        platforms = state.availablePlatforms,
                        order = state.categoryOrder,
                        selected = state.platformFilter,
                        special = state.specialFilter,
                        collections = state.collections.map { it.key },
                        onSelect = { viewModel.setPlatformFilter(it) },
                        onSelectSpecial = { viewModel.setSpecialFilter(it) },
                        align = categoryAlign(),
                        edge = if (tileLayout) tileRowMetrics(state.appearance.cardSize).start else null
                        // Sort moved to the R1 panel (quick settings), Start › Sort… and the SORT hotkey.
                    )
                    // Only the category you're in, lined up with the game
                    // you're on (left end or middle). L2 / R2 still switch.
                    com.shelf.launcher.data.CategoryBar.CURRENT -> {
                        // Console / platform categories can keep their name in one fixed place (Name &
                        // category › Console name): centred above the games, at the left, or hidden.
                        // Other categories always behave as before.
                        val isConsole = state.specialFilter == null && state.platformFilter != null
                        val pin = if (isConsole) com.shelf.launcher.data.Features.consoleNamePin.state.value else com.shelf.launcher.data.Features.ConsoleNamePin.FLOW
                        val align = when (pin) {
                            com.shelf.launcher.data.Features.ConsoleNamePin.CENTER -> Alignment.CenterHorizontally
                            com.shelf.launcher.data.Features.ConsoleNamePin.LEFT -> Alignment.Start
                            else -> categoryAlign()
                        }
                        val pinned = pin == com.shelf.launcher.data.Features.ConsoleNamePin.CENTER || pin == com.shelf.launcher.data.Features.ConsoleNamePin.LEFT
                        val centered = align == Alignment.CenterHorizontally
                        // Same left edge — and the same style (Name & category ›
                        // Style) — as the game name next to the row (GameDock).
                        val backdrop = state.appearance.nameBackdrop
                        val screenWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { screenSizeDp().width.toPx() }
                        val zone = safeZone()
                        // The category's own look: a category with its own scene (Behind the dock › per
                        // category) lends the name that theme's accent, so the name never falls back to one
                        // generic style when the category changes.
                        val catKey = state.sortCategoryKey
                        val catColors = remember(state.appearance.categoryScenes, catKey) {
                            com.shelf.launcher.data.CategoryScenes.parse(state.appearance.categoryScenes)[catKey]
                                ?.let { n -> com.shelf.launcher.ui.theme.AppTheme.values().firstOrNull { it.name == n } }?.colors
                        }
                        val baseColors = com.shelf.launcher.ui.theme.ShelfTheme.colors
                        val nameColors = catColors?.let { c -> baseColors.copy(accent = c.accent, accentGlow = c.accentGlow, secondary = c.secondary) } ?: baseColors
                        if (pin != com.shelf.launcher.data.Features.ConsoleNamePin.HIDDEN) Row(
                            Modifier
                                .fillMaxWidth()
                                // (Update 249, #119: it fades as the row turns into the game window — RowReveal.)
                                .then(if (com.shelf.launcher.data.Features.winMotion.state.value != com.shelf.launcher.data.Features.WinMotion.NONE)
                                    Modifier.graphicsLayer { alpha = RowReveal.sharedAlpha() } else Modifier)
                                .then(
                                    // Tile home: exactly on the name's line — it follows the selected tile
                                    // (left edge / centre / right edge, like the name), not the screen edge.
                                    if (tileLayout && !pinned) Modifier
                                        .padding(bottom = state.appearance.infoSpacing.dp.dp)
                                        .onTileNameLine(tileNameAnchor, screenWidthPx, nameBackdropInset(backdrop))
                                    else if (pinned) Modifier.padding(
                                        start = if (centered) zone.left else maxOf(zone.left, if (tileLayout) tileRowMetrics(state.appearance.cardSize).start else dockTextStart()),
                                        end = zone.right,
                                        bottom = state.appearance.infoSpacing.dp.dp
                                    )
                                    else Modifier.padding(
                                        start = if (centered) DockCenteredPadding else dockTextStart(),
                                        end = DockCenteredPadding,
                                        bottom = state.appearance.infoSpacing.dp.dp
                                    )
                                ),
                            horizontalArrangement = when {
                                tileLayout && !pinned -> Arrangement.Start
                                align == Alignment.CenterHorizontally -> Arrangement.Center
                                align == Alignment.End -> Arrangement.End
                                else -> Arrangement.Start
                            }
                        ) {
                            val catLabel = state.specialFilter?.label
                                ?: state.platformFilter?.let { LocalTitleOptions.current.console(it) }
                                ?: "All"
                            val catLogo = if (state.specialFilter == null) com.shelf.launcher.data.ConsoleLogos.iconFor(state.platformFilter) else null
                            // Each name keeps its own logo while the old one animates out (it used to lose it).
                            val logos = remember { HashMap<String, String?>() }
                            logos[catLabel] = catLogo
                            val catSize = if (LocalCompactPills.current) 12.sp * LocalTileScale.current else 15.sp
                            androidx.compose.runtime.CompositionLocalProvider(com.shelf.launcher.ui.theme.LocalShelfColors provides nameColors) {
                                // L2 / R2: the name swaps, fades, slides or drops in (Appearance › Name & category).
                                CategoryNameSwap(catLabel, state.appearance.categoryAnim) { label ->
                                    Row(Modifier.nameBackdrop(backdrop).onGloballyPositioned { c -> GlanceOrigin.category = GlanceOrigin.onScreen(c) }, verticalAlignment = Alignment.CenterVertically) {
                                        val logo = logos[label]
                                        if (logo != null) {
                                            coil.compose.AsyncImage(logo, null, Modifier.size(with(androidx.compose.ui.platform.LocalDensity.current) { catSize.toDp() } * 1.2f))
                                            Spacer(Modifier.width(6.dp))
                                        }
                                        Text(
                                            label,
                                            color = Color.White,
                                            fontSize = catSize, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(catSize), maxFontSize = catSize),
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            style = nameTextStyle(backdrop)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    com.shelf.launcher.data.CategoryBar.OFF -> Unit
                }
            }
        }

        // The row of games: top, middle or bottom (Appearance › Game row). The
        // legend always stays along the bottom edge.
        val dockPosition = state.appearance.dockPosition
        val legendShown = state.appearance.legendOn
        @Composable
        fun BoxScope.GameRows() {
        if (styleLayout) {
            // The style homes (StyleHomes.kt): their own menus, cards and panels over the whole screen.
            StyleHome(
                layout = state.appearance.homeLayout, state = state, art = displayArt, shortcuts = tileShortcuts,
                actions = run {
                    val freshActions = StyleActions(
                        select = { viewModel.setFocus(it); StyleNav.reset() },
                        open = { StyleNav.reset(); sounds?.play(UiSound.SELECT); openGame() },
                        details = { StyleNav.reset(); GamePopup.game = null; openDetails() },
                        needAchievements = { g -> if (g.raGameId != null) viewModel.loadAchievementDetails(g) },
                        play = { g -> StyleNav.reset(); sounds?.play(UiSound.SELECT); startGame(g) },
                        category = { steps ->
                            // (Update 219, #04: straight to the tapped tab, not N single steps that skip empty ones.)
                            viewModel.jumpCategory(steps)
                            CategorySlide.step(if (steps > 0) 1 else -1)
                            sounds?.play(UiSound.MOVE)
                        },
                        achievements = { g ->
                            if (g.raGameId != null) viewingAchievementsForGame = g else hubScreen = HubScreen.ACHIEVEMENTS
                        },
                        // Update 132 (Spotlight's Home menu hints): what X does on a game.
                        favorite = { g ->
                            if (g.stableKey in com.shelf.launcher.data.RunningGames.keys) { viewModel.closeRunning(g); sounds?.play(UiSound.BACK) }
                            else { viewModel.toggleFavorite(g); sounds?.play(UiSound.SELECT) }
                        },
                        needPhysical = { viewModel.loadPhysicalMedia(it) },
                        needMedia = { viewModel.loadMedia(it) },
                        needSpine = { viewModel.loadPhysicalMedia(it, needSpine = true) },
                        mediaPlayPause = { viewModel.mediaPlayPause() },
                        mediaNext = { viewModel.mediaNext() },
                        mediaPrevious = { viewModel.mediaPrevious() },
                        mediaSeek = { viewModel.mediaSeekTo(it) },
                        options = { g -> optionsForGame = g },
                        sync = { g -> saveSyncForGame = g }, // (Update 119: it used to need the details window open, so it did nothing from the home.)
                        profile = { StyleNav.reset(); sounds?.play(UiSound.OPEN); hubScreen = HubScreen.PROFILE },
                        settings = { StyleNav.reset(); sounds?.play(UiSound.OPEN); menuStartCategory = null; showMenu = true },
                        notifications = { StyleNav.reset(); sounds?.play(UiSound.OPEN); viewModel.refreshNotifications(); notificationsOnly = true; showNotifications = true },
                        cloud = { StyleNav.reset(); sounds?.play(UiSound.OPEN); showSyncStatus = true },
                        // Update 158: Twin desk's round buttons (the R1 quick settings panel, the screensaver).
                        quickMenu = { StyleNav.reset(); sounds?.play(UiSound.OPEN); viewModel.refreshNotifications(); notificationsOnly = false; showNotifications = true },
                        screensaver = { StyleNav.reset(); screensaverOn = true },
                        // Update 181: the Pocket layouts' statistics and achievements panels.
                        stats = { StyleNav.reset(); sounds?.play(UiSound.OPEN); hubScreen = HubScreen.STATS },
                        achievementsHub = { StyleNav.reset(); sounds?.play(UiSound.OPEN); hubScreen = HubScreen.ACHIEVEMENTS },
                        // Update 202: Pocket Quest's panel (the game's summary, year, genre), its Sort & filter and Search buttons.
                        needMetadata = { g -> if (state.metadataReady) viewModel.loadMetadata(g) },
                        sort = { StyleNav.reset(); sounds?.play(UiSound.OPEN); showSortDialog = true },
                        search = { StyleNav.reset(); sounds?.play(UiSound.OPEN); showSearchBar = true }
                    )
                    // (Update 232, #60: one StyleActions kept for the home, each of its actions calling this pass's own —
                    // a new one at every pass changed the carousel's onSelect / onNeedPhysical, so Case stacks' carousel
                    // never skipped a recomposition. What each action does is unchanged: always the latest.)
                    val latestActions = rememberUpdatedState(freshActions)
                    remember {
                        fun a() = latestActions.value
                        StyleActions(
                            select = { a().select(it) },
                            open = { a().open() },
                            play = { a().play(it) },
                            category = { a().category(it) },
                            achievements = { a().achievements(it) },
                            favorite = { a().favorite(it) },
                            needPhysical = { a().needPhysical(it) },
                            needMedia = { a().needMedia(it) },
                            needSpine = { a().needSpine(it) },
                            mediaPlayPause = { a().mediaPlayPause() },
                            mediaNext = { a().mediaNext() },
                            mediaPrevious = { a().mediaPrevious() },
                            mediaSeek = { a().mediaSeek(it) },
                            options = { a().options(it) },
                            sync = { a().sync(it) },
                            profile = { a().profile() },
                            settings = { a().settings() },
                            notifications = { a().notifications() },
                            cloud = { a().cloud() },
                            details = { a().details() },
                            needAchievements = { a().needAchievements(it) },
                            quickMenu = { a().quickMenu() },
                            screensaver = { a().screensaver() },
                            stats = { a().stats() },
                            achievementsHub = { a().achievementsHub() },
                            needMetadata = { a().needMetadata(it) },
                            sort = { a().sort() },
                            search = { a().search() }
                        )
                    }
                },
                modifier = Modifier.align(Alignment.TopCenter).fillMaxSize()
                    .padding(
                        top = if (state.appearance.statusOn) maxOf(46.dp, statusBarDp + 4.dp) else 12.dp, bottom = if (legendShown) 28.dp else 8.dp,
                        // The Side menu's panel runs against the left edge.
                        start = if (state.appearance.homeLayout == com.shelf.launcher.data.HomeLayout.SIDE) 0.dp else 12.dp, end = 12.dp
                    )
            )
        } else if (tvLayout) {
            // TV room (TvRoomHome.kt): the game on an old TV, the games beside it, its story under it.
            if (visibleGames.isEmpty()) Text(EmptyShelf.text, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.align(Alignment.Center))
            else TvRoomHome(
                games = visibleGames, focus = state.focusedIndex, art = displayArt, overrides = state.artOverrides,
                screenshots = state.screenshots, videoUrls = state.videoUrls, metadata = state.metadata,
                playtime = state.playtime, lastPlayed = state.lastPlayedAt, nowPlaying = state.nowPlaying,
                videoSound = state.appearance.videoSound,
                onSelect = viewModel::setFocus, onOpen = { openGame() },
                onNeedMedia = { g -> viewModel.loadMedia(g); viewModel.loadVideoUrl(g, force = true); viewModel.loadMetadata(g) },
                modifier = Modifier.align(Alignment.TopCenter).fillMaxSize()
                    .padding(top = if (state.appearance.statusOn) maxOf(46.dp, statusBarDp + 4.dp) else 12.dp, bottom = if (legendShown) 28.dp else 8.dp, start = 14.dp, end = 14.dp),
                cardArtType = state.appearance.cardArtFor(), physicalMedia = state.physicalMedia,
                onNeedPhysical = { viewModel.loadPhysicalMedia(it) },
                // Update 135: Endless goes round in the TV room's carousel.
                loop = state.appearance.loopRow, loopTick = state.loopTick, loopStep = state.loopStep,
                // The room fills the whole screen, past the padding above.
                roomBleed = androidx.compose.foundation.layout.PaddingValues(
                    top = if (state.appearance.statusOn) maxOf(46.dp, statusBarDp + 4.dp) else 12.dp, bottom = if (legendShown) 28.dp else 8.dp, start = 14.dp, end = 14.dp
                ),
                // Recently unlocked badges (update 107), newest first.
                recentBadges = emptyList(),
                // Update 125: the wall shows the achievements of the game you're on (earned first, in colour; the rest in grey).
                gameBadges = { g ->
                    state.raAchievementDetails[g.stableKey].orEmpty()
                        .sortedWith(compareBy<com.shelf.launcher.data.RaAchievementDetail> { !it.isUnlocked }.then(com.shelf.launcher.data.RA_DISPLAY_ORDER))
                        .mapNotNull { a -> com.shelf.launcher.data.RaBadges.url(a.badgeName)?.let { it to a.isUnlocked } }
                }
            )
            // The game you're on: its achievement list for the wall, once it stays a moment.
            val tvFocused = visibleGames.getOrNull(state.focusedIndex)
            LaunchedEffect(tvFocused?.stableKey) {
                val g = tvFocused ?: return@LaunchedEffect
                if (!com.shelf.launcher.data.Features.tvRecentBadges.value || g.raGameId == null) return@LaunchedEffect
                kotlinx.coroutines.delay(500)
                viewModel.loadAchievementDetails(g)
            }
        } else if (gridLayout) {
            // Grid home (GridHome.kt): categories on top, the games filling the rest of the screen.
            Column(
                Modifier.align(Alignment.TopCenter).fillMaxSize()
                    .padding(top = if (state.appearance.statusOn) maxOf(46.dp, statusBarDp + 4.dp) else 12.dp, bottom = if (legendShown) 26.dp else 6.dp, start = 12.dp, end = 12.dp)
            ) {
                // (Update 202: the category bar and the games are moved / resized / taken off in Edit Home.)
                if (state.appearance.categoryBar != com.shelf.launcher.data.CategoryBar.OFF) {
                    Box(Modifier.editablePart(LayoutParts.id(com.shelf.launcher.data.HomeLayout.GRID, "category"), "Category bar")) { CategoriesBar() }
                    Spacer(Modifier.height(4.dp))
                }
                if (visibleGames.isEmpty()) {
                    Text(EmptyShelf.text, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(24.dp))
                } else if (com.shelf.launcher.data.HomeLayout.GRID.name in com.shelf.launcher.data.Features.carouselLayouts.state.value) GameCarousel(
                    // Grid › Carousel look: one game big in the middle, the next ones each side behind it, its name under it.
                    games = visibleGames, focus = state.focusedIndex, art = displayArt, overrides = state.artOverrides,
                    onSelect = viewModel::setFocus, onOpen = { openGame() },
                    cardArtType = state.appearance.cardArtFor(), physicalMedia = state.physicalMedia, screenshots = state.screenshots,
                    onNeedPhysical = { viewModel.loadPhysicalMedia(it) },
                    modifier = Modifier.fillMaxWidth().weight(1f).editablePart(LayoutParts.id(com.shelf.launcher.data.HomeLayout.GRID, "games"), "Games"), showName = true,
                    // Update 135: Endless goes round in the carousel too.
                    loop = state.appearance.loopRow, loopTick = state.loopTick, loopStep = state.loopStep
                ) else HomeGameGrid(
                    games = visibleGames, focus = state.focusedIndex, art = displayArt, overrides = state.artOverrides,
                    physicalMedia = state.physicalMedia, screenshots = state.screenshots, cardArtType = state.appearance.cardArtFor(),
                    onSelect = viewModel::setFocus, onOpen = { openGame() },
                    onNeedPhysical = { viewModel.loadPhysicalMedia(it) }, onNeedScreenshots = { viewModel.loadMedia(it) },
                    sizeScale = when (state.appearance.cardSize) { com.shelf.launcher.data.CardSize.SMALL -> 0.82f; com.shelf.launcher.data.CardSize.MEDIUM -> 1f; com.shelf.launcher.data.CardSize.LARGE -> 1.3f } *
                        GameSize.scale(com.shelf.launcher.data.HomeLayout.GRID),
                    modifier = Modifier.fillMaxWidth().weight(1f).editablePart(LayoutParts.id(com.shelf.launcher.data.HomeLayout.GRID, "games"), "Games"),
                    progressOf = { g ->
                        val list = state.raAchievementDetails[g.stableKey].orEmpty()
                        val p = state.raProgress[g.stableKey]
                        val total = if (list.isNotEmpty()) list.size else p?.NumPossibleAchievements ?: 0
                        if (total <= 0) null else (if (list.isNotEmpty()) list.count { it.isUnlocked } else p?.NumAchieved ?: 0) to total
                    },
                    // Update 156: going round at the end is one step (the grid repeats its games, GridHome.kt).
                    loopTick = state.loopTick, loopStep = state.loopStep
                )
            }
        } else if (tileLayout) androidx.compose.runtime.CompositionLocalProvider(LocalTileScale provides state.appearance.tileScale / 100f) {
            // Tile-style: tiles a little above the middle, round buttons under them.
            // Top / middle / bottom like the classic row (Appearance › Game row › Where the tiles sit).
            Column(
                Modifier
                    .align(
                        when (dockPosition) {
                            com.shelf.launcher.data.DockPosition.TOP -> Alignment.TopCenter
                            com.shelf.launcher.data.DockPosition.MIDDLE -> Alignment.Center
                            com.shelf.launcher.data.DockPosition.BOTTOM -> Alignment.BottomCenter
                        }
                    )
                    .padding(
                        // At the top it sits right under the edge like the classic row (the status bar moves to the bottom).
                        top = if (dockPosition == com.shelf.launcher.data.DockPosition.TOP) 10.dp else 0.dp,
                        bottom = if (legendShown) 26.dp else 8.dp
                    )
                    .onGloballyPositioned { rowTopPx = it.positionInRoot().y; rowBottomPx = rowTopPx + it.size.height }
            ) {
                // Category names on the same side as the classic row puts them (Appearance › Categories).
                // Round buttons: right above the tiles when they sit at the bottom, otherwise right under them.
                // Or in a corner instead (shortcutSpot).
                val shortcutsAbove = dockPosition == com.shelf.launcher.data.DockPosition.BOTTOM
                val shortcutsByRow = state.appearance.tileShortcuts && state.appearance.shortcutSpot == com.shelf.launcher.data.ShortcutSpot.ROW
                // The category names filter the row, so they hug the tiles too — on the side away from the
                // round buttons (never pushed out past them). Without buttons by the row: the usual side setting.
                // Default: right above the game names (between the round buttons and the name when the
                // buttons sit above the tiles). "Under the row" puts them right under the tiles.
                val tileCategoryBelow = state.appearance.categorySide == com.shelf.launcher.data.RowSide.BELOW
                val showCats = state.appearance.categoryBar != com.shelf.launcher.data.CategoryBar.OFF
                state.statusMessage?.let { message ->
                    Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp)) {
                        Text(
                            message,
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.6f))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
                if (shortcutsAbove && shortcutsByRow && !shortcutsFloating) {
                    HomeShortcutBar(tileShortcuts, tileBarFocus, themed = state.appearance.tileShortcutTheme, labelOnTop = true, highlight = state.appearance.shortcutHighlight)
                }
                // A one-line name takes one line's room: the second line's room goes up here (above the
                // category), so the category sits right on the name and the tiles never move.
                if (dockPosition != com.shelf.launcher.data.DockPosition.TOP && tileNameAnchor.nameLines == 1) {
                    Spacer(Modifier.height(22.dp * LocalTileScale.current))
                }
                if (!tileCategoryBelow && showCats) {
                    // Smaller here: on this layout the bar sits near the music player's lyrics.
                    androidx.compose.runtime.CompositionLocalProvider(LocalCompactPills provides true) {
                        CategoriesBar()
                    }
                    Spacer(Modifier.height(2.dp))
                }
                if (visibleGames.isEmpty()) {
                    Text(
                        EmptyShelf.text,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)
                    )
                } else {
                    // (Update 235, #15/#16: the tiles are moved / resized / taken off in Edit Home like the Voltcap row's
                    // Game row (update 202) — Tile home was out of the list when that came.)
                    Box(Modifier.fillMaxWidth().editablePart(LayoutParts.id(com.shelf.launcher.data.HomeLayout.TILES, "row"), "Game row")) {
                    TileHomeRow(
                        lastPlayedAt = state.lastPlayedAt,
                        onTilesTop = { y -> if (kotlin.math.abs(y - artTopPx) > 1f) artTopPx = y },
                        games = visibleGames,
                        art = displayArt,
                        artOverrides = state.artOverrides,
                        focusedIndex = state.focusedIndex,
                        raProgress = state.raProgress,
                        favorites = state.favorites,
                        cardArtType = state.appearance.cardArtFor(),
                        cardSize = state.appearance.cardSize,
                        dimmed = tileBarFocus != null,
                        onFocus = { tileBarFocus = null; viewModel.setFocus(it) },
                        onOpen = { tileBarFocus = null; openGame() },
                        physicalMedia = state.physicalMedia,
                        screenshots = state.screenshots,
                        awards = awardsByKey,
                        // At the top the name goes under the tiles (like the classic row), so the tiles touch the top.
                        nameBelow = dockPosition == com.shelf.launcher.data.DockPosition.TOP,
                        focusAnchor = state.appearance.tileFocus,
                        nameAnchor = tileNameAnchor,
                        onNeedPhysical = { viewModel.loadPhysicalMedia(it) },
                        onNeedScreenshots = { viewModel.loadMedia(it) },
                        // Update 156: going round at the end is one step (TileHome.kt).
                        loopTick = state.loopTick, loopStep = state.loopStep
                    )
                    }
                }
                if (tileCategoryBelow && showCats) {
                    Spacer(Modifier.height(4.dp))
                    androidx.compose.runtime.CompositionLocalProvider(LocalCompactPills provides true) {
                        CategoriesBar()
                    }
                }
                if (!shortcutsAbove && shortcutsByRow && !shortcutsFloating) {
                    Spacer(Modifier.height(if (dockPosition == com.shelf.launcher.data.DockPosition.TOP) 2.dp else 8.dp))
                    HomeShortcutBar(tileShortcuts, tileBarFocus, themed = state.appearance.tileShortcutTheme, highlight = state.appearance.shortcutHighlight)
                }
            }
        } else {
            Column(
                Modifier.align(
                    when (dockPosition) {
                        com.shelf.launcher.data.DockPosition.TOP -> Alignment.TopCenter
                        com.shelf.launcher.data.DockPosition.MIDDLE -> Alignment.Center
                        com.shelf.launcher.data.DockPosition.BOTTOM -> Alignment.BottomCenter
                    }
                ).onGloballyPositioned { rowTopPx = it.positionInRoot().y; rowBottomPx = rowTopPx + it.size.height }.then(
                    // The status bar moves to the bottom then, so the row can sit
                    // right at the top.
                    if (dockPosition == com.shelf.launcher.data.DockPosition.TOP) Modifier.padding(top = 16.dp) else Modifier
                )
            ) {
                val categoryBelow = state.appearance.categorySide.below(dockPosition)
                // Game header (Appearance › Game popup › Game header on the home screen): the showcase block
                // for the game you're on. It replaces the game part of "Next to the row" (news can stay).
                val homeHeaderOn = com.shelf.launcher.data.Features.homeHeader.state.value && focusedGame != null
                val rowInfoMode = state.appearance.rowInfo.let { m ->
                    if (!homeHeaderOn) m else when (m) {
                        com.shelf.launcher.data.RowInfo.GAME -> com.shelf.launcher.data.RowInfo.OFF
                        com.shelf.launcher.data.RowInfo.BOTH -> com.shelf.launcher.data.RowInfo.NEWS
                        else -> m
                    }
                }
                // Edit Home: the header (and each of its parts) can be moved; until then it sits here (FlowSlot).
                val homeHeader: @Composable () -> Unit = { FlowSlot(HomeWidgets.HEADER, Modifier.fillMaxWidth()) { HomeHeaderBlock(true) } }
                val headerAbove = dockPosition != com.shelf.launcher.data.DockPosition.TOP
                val headerHere = HomeEditState.active || HomeWidgets.position(HomeWidgets.HEADER) == null
                if (homeHeaderOn && headerAbove && headerHere) {
                    homeHeader()
                    Spacer(Modifier.height(6.dp))
                }
                // Game info / news (Appearance › Next to the row): above the row
                // when it sits at the bottom, over the background art…
                val rowInfoPanel: @Composable () -> Unit = {
                    // Safe mode: no news (network); the game info stays.
                    val mode = rowInfoMode.let { if (com.shelf.launcher.CrashGuard.safeMode && (it == com.shelf.launcher.data.RowInfo.NEWS || it == com.shelf.launcher.data.RowInfo.BOTH)) com.shelf.launcher.data.RowInfo.GAME else it }
                    val both = mode == com.shelf.launcher.data.RowInfo.BOTH
                    // The game info card and the news card can each be moved in Edit Home (FlowSlot).
                    Row(Modifier.fillMaxWidth()) {
                        if (mode == com.shelf.launcher.data.RowInfo.GAME || both) FlowSlot(HomeWidgets.INFO, Modifier.weight(if (both) 1.3f else 1f)) { RowInfoBlock(com.shelf.launcher.data.RowInfo.GAME, padEnd = !both) }
                        if (mode == com.shelf.launcher.data.RowInfo.NEWS || both) FlowSlot(HomeWidgets.NEWS, Modifier.weight(1f).padding(start = if (both) 10.dp else 0.dp)) { RowInfoBlock(com.shelf.launcher.data.RowInfo.NEWS, padStart = !both) }
                    }
                }
                val infoAbove = dockPosition == com.shelf.launcher.data.DockPosition.BOTTOM
                if (infoAbove && rowInfoMode != com.shelf.launcher.data.RowInfo.OFF) {
                    rowInfoPanel()
                    Spacer(Modifier.height(10.dp))
                }
                if (!categoryBelow) FlowSlot(HomeWidgets.CATEGORY) { CategoriesBar() }
                state.statusMessage?.let { message ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 6.dp)
                    ) {
                        Text(
                            message,
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.6f))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
                if (visibleGames.isEmpty()) {
                    Text(
                        EmptyShelf.text,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)
                    )
                } else {
                    // GameDock lives in GameDock.kt. With the game header on, the header already shows the name
                    // and the play time: the row's own name / play-time line stays out (it said the same twice).
                    val dockTitles = LocalTitleOptions.current.let { if (homeHeaderOn) it.copy(placement = com.shelf.launcher.data.TitlePlacement.HIDDEN) else it }
                    val dockLook = LocalCardLook.current.let { if (homeHeaderOn) it.copy(playtimeOnHome = false) else it }
                    androidx.compose.runtime.CompositionLocalProvider(LocalTitleOptions provides dockTitles, LocalCardLook provides dockLook) {
                    // Update 202: the game row itself is moved / resized / taken off in Edit Home (the game you're on in it
                    // too, on its own — GameDock).
                    Box(Modifier.fillMaxWidth().editablePart(LayoutParts.id(com.shelf.launcher.data.HomeLayout.CLASSIC, "row"), "Game row")) {
                    GameDock(
                        games = visibleGames,
                        art = displayArt,
                        focusedIndex = state.focusedIndex,
                        lastPlayedAt = state.lastPlayedAt,
                        raProgress = state.raProgress,
                        loopTick = state.loopTick,
                        loopStep = state.loopStep,
                        onFocus = viewModel::setFocus,
                        // Tapping the focused card opens its details window,
                        // same as pressing A.
                        onLaunch = { openGame() },
                        onOpenOptions = { game -> optionsForGame = game },
                        onViewAchievements = { game -> viewingAchievementsForGame = game },
                        favorites = state.favorites,
                        onToggleFavorite = { game -> viewModel.toggleFavorite(game) },
                        artOverrides = state.artOverrides,
                        cardArtType = state.appearance.cardArtFor(),
                        categoryBelow = categoryBelow && state.appearance.categoryBar != com.shelf.launcher.data.CategoryBar.OFF,
                        rowAtBottom = dockPosition == com.shelf.launcher.data.DockPosition.BOTTOM, // (Update 153)
                        physicalMedia = state.physicalMedia,
                        screenshots = state.screenshots,
                        onNeedPhysical = { viewModel.loadPhysicalMedia(it) },
                        onNeedScreenshots = { viewModel.loadMedia(it) },
                        badgeMode = state.achievementBadgeMode,
                        badgeNextToTitle = state.achievementBadgeNextToTitle,
                        recentUnlocks = state.raRecentAchievements,
                        awards = awardsByKey,
                        playtime = state.playtime,
                        onArtTop = { y -> if (kotlin.math.abs(y - artTopPx) > 1f) artTopPx = y },
                        onCycleBadgeMode = {
                            viewModel.setAchievementBadgeMode(
                                if (state.achievementBadgeMode == com.shelf.launcher.AchievementBadgeMode.PROGRESS)
                                    com.shelf.launcher.AchievementBadgeMode.RECENT_UNLOCK
                                else com.shelf.launcher.AchievementBadgeMode.PROGRESS
                            )
                        }
                    )
                    }
                    }
                }
                if (categoryBelow) {
                    Spacer(Modifier.height(4.dp))
                    FlowSlot(HomeWidgets.CATEGORY) { CategoriesBar() }
                }
                // …and under it when the games sit at the top or in the middle.
                if (!infoAbove && rowInfoMode != com.shelf.launcher.data.RowInfo.OFF) {
                    Spacer(Modifier.height(10.dp))
                    rowInfoPanel()
                }
                // Row at the top: the game header goes under it.
                if (homeHeaderOn && !headerAbove && headerHere) {
                    Spacer(Modifier.height(6.dp))
                    homeHeader()
                }
                // The legend's space is always kept under the row, so showing or
                // hiding it (R3) never moves the games — it just appears below.
                // Right down at the bottom; the slim legend lifts it just a little.
                if (dockPosition == com.shelf.launcher.data.DockPosition.BOTTOM) {
                    Spacer(Modifier.height(if (legendShown) 22.dp else 2.dp))
                }
            }
        }
        }
        // Dock backing (a colour or your animated picture behind the row).
        if (!heroLayout && !gridLayout && !tvLayout && !styleLayout) DockBacking(if (tileLayout && artTopPx > 0f) artTopPx else rowTopPx, rowBottomPx)
        // Edit Home (update 90): the Voltcap row's parts moved out of their place — the header, each of its pieces, the
        // category name, the game info and news cards — float where they were put. While editing, every one of them is
        // drawn here on top of its own place in the layout (FlowSlot keeps the space), so nothing jumps or overlaps.
        if (!tileLayout && !heroLayout && !gridLayout && !tvLayout && !styleLayout) {
            var floatOrigin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
            Box(Modifier.matchParentSize().zIndex(PartsLayer.z).onGloballyPositioned { floatOrigin = it.positionInRoot() }) {
                val bothCards = HomeWidgets.INFO in HomeWidgets.present && HomeWidgets.NEWS in HomeWidgets.present
                HomeWidgets.flowIds.forEach { id ->
                    val moved = HomeWidgets.position(id) != null
                    if (id in HomeWidgets.present && (moved || (HomeEditState.active && HomeEditState.flowRect[id] != null))) {
                        androidx.compose.runtime.key(id) {
                            val w = HomeWidgets.width(id)
                            MovableWidget(
                                id, defaultAlign = Alignment.CenterStart, defaultPadding = PaddingValues(0.dp),
                                defaultFrac = flowFractions(id, floatOrigin), fixedWidth = w
                            ) {
                                when (id) {
                                    HomeWidgets.HEADER -> HomeHeaderBlock(w != null)
                                    HomeWidgets.CATEGORY -> CategoriesBar()
                                    HomeWidgets.INFO -> RowInfoBlock(com.shelf.launcher.data.RowInfo.GAME, padEnd = !bothCards)
                                    HomeWidgets.NEWS -> RowInfoBlock(com.shelf.launcher.data.RowInfo.NEWS, padStart = !bothCards)
                                    else -> HomeHeaderBlock(false, only = id)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (heroLayout) {
            val hg = focusedGame
            val seenAll by com.shelf.launcher.data.AchievementSeen.all.collectAsState()
            val info = hg?.let { g ->
                val k = g.stableKey
                val list = state.raAchievementDetails[k].orEmpty()
                val prog = state.raProgress[k]
                val meta = state.metadata[k].shownIn(com.shelf.launcher.data.Features.factsList.state.value)
                val total = if (list.isNotEmpty()) list.size else prog?.NumPossibleAchievements ?: 0
                val earned = if (list.isNotEmpty()) list.count { it.isUnlocked } else prog?.NumAchieved ?: 0
                val tips = g.raGameId?.let { state.raGameTips[it] }
                HeroInfo(
                    game = g, title = gameDisplayName(g.name),
                    // (Update 113: the big logo — SteamGridDB's by default.)
                    logo = if (LocalTitleOptions.current.showLogos) bigLogo(g, state) else null,
                    console = com.shelf.launcher.data.ConsoleNames.display(g.platform),
                    playtimeMs = state.playtime[k] ?: 0L, lastPlayed = state.lastPlayedAt[k],
                    summary = meta?.summary,
                    facts = listOfNotNull(meta?.releaseYear?.toString(), meta?.genres?.take(2)?.joinToString(", ")?.takeIf { it.isNotBlank() }, meta?.developer, meta?.publisher),
                    achievements = list, earned = earned, total = total,
                    newIds = g.raGameId?.let { seenAll[com.shelf.launcher.data.AchievementSeen.keyRa(it)]?.glow }.orEmpty(),
                    screenshots = state.screenshots[k].orEmpty(),
                    guideUrl = tips?.guideUrl, tipCount = tips?.comments?.size ?: 0,
                    milestone = heroMilestone(state.playtime[k] ?: 0L, state.lastPlayedAt[k], earned, total),
                    completion = g.raGameId?.let { state.raCompletionById[it] }
                )
            }
            // The list and tips for the game you rest on (cached / no-ops when already there).
            LaunchedEffect(hg?.stableKey) {
                val g = hg ?: return@LaunchedEffect
                kotlinx.coroutines.delay(500)
                if (g.raGameId != null) viewModel.loadAchievementDetails(g)
            }
            HeroCarouselLayer(
                games = visibleGames, focused = state.focusedIndex, page = heroPage,
                coverFor = { g -> state.artOverrides[g.stableKey]?.cover ?: state.art[g.stableKey]?.gridUrl },
                info = info,
                categoryLabel = categoryKeyLabel(state.sortCategoryKey),
                onSelect = { i -> viewModel.setFocus(i); sounds?.play(UiSound.MOVE) },
                onOpen = { sounds?.play(UiSound.SELECT); openGame() },
                onPage = { i -> heroPage = i; sounds?.play(UiSound.MOVE) },
                onOpenAchievements = { g -> if (g.raGameId != null) viewingAchievementsForGame = g },
                onOpenScreenshot = { g, i ->
                    val shots = state.screenshots[g.stableKey].orEmpty()
                    if (shots.isNotEmpty()) artViewer = shots.map { GalleryItem("Screenshot", it, null) } to i
                },
                onOpenGuide = { g -> guideForGame = g },
                onLoadMedia = { g -> viewModel.loadMedia(g) },
                bottomPadding = if (legendShown) 24.dp else 0.dp,
                wideFor = { g -> state.artOverrides[g.stableKey]?.wide ?: state.art[g.stableKey]?.wideGridUrl },
                infoAtTop = state.appearance.dockPosition == com.shelf.launcher.data.DockPosition.TOP,
                cardArtType = state.appearance.cardArtFor(), physicalMedia = state.physicalMedia,
                onNeedPhysical = { viewModel.loadPhysicalMedia(it) },
                videoUrl = hg?.stableKey?.let { state.videoUrls[it] },
                loop = state.appearance.loopRow, loopTick = state.loopTick, loopStep = state.loopStep
            )
        } else Box(Modifier.matchParentSize().edgeBump()) {
            // (Update 249, #119: Row reveal's shade over the art, under the rows — RowRevealWindow.kt.)
            if (com.shelf.launcher.data.Features.winMotion.state.value == com.shelf.launcher.data.Features.WinMotion.ROW_REVEAL)
                RowRevealShade(Modifier.matchParentSize())
            GameRows()
        }
        // Hero carousel / Spotlight / Icon bar (update 93): the game header's pieces, the category name and the game info
        // and news cards picked in that layout's options, each floating on its own (right side, stacked, until moved in
        // Edit Home). The same parts as the Voltcap row's, with their own place per layout.
        val overlayLayout = state.appearance.homeLayout.takeIf { it in com.shelf.launcher.data.Features.overlayLayouts }
        val overlayParts = overlayLayout?.let { com.shelf.launcher.data.Features.overlayPartsFor(it) }.orEmpty()
        SideEffect { HomeEditState.layout = state.appearance.homeLayout }
        // Update 202: the look Edit Home's Art position (R3) works on here — Box art on any layout; the covers, cartridges
        // and discs where the game row draws them (the Voltcap row); none on the others.
        val artLookHere = run {
            val t = state.appearance.cardArtFor()
            when {
                t == com.shelf.launcher.data.CardArtType.CASE || t == com.shelf.launcher.data.CardArtType.CASE_REAL -> ArtPlace.Look.BOX
                state.appearance.homeLayout != com.shelf.launcher.data.HomeLayout.CLASSIC -> null
                t == com.shelf.launcher.data.CardArtType.DISC -> ArtPlace.Look.DISC
                t == com.shelf.launcher.data.CardArtType.CARTRIDGE_DISC ->
                    if (focusedGame?.platform?.let { mediaKind(it) == "disc" } == true) ArtPlace.Look.DISC else ArtPlace.Look.CART
                t == com.shelf.launcher.data.CardArtType.COVER -> ArtPlace.Look.COVER
                else -> null
            }
        }
        SideEffect { HomeEditState.artLook = artLookHere }
        // Update 125: the floating parts over the games — but under anything that opens over the home screen.
        val homeCovered = showDetails || showControlsHelp || categoryScreenOpen || screensaverOn || stickLetter != null
        SideEffect { PartsLayer.z = if (homeCovered) 0f else 6f }
        // The description part needs the game's details (update 104), once you rest on a game.
        val wantsDesc = HomeWidgets.DESC in overlayParts
        // Update 116: the achievements card needs the game's achievement list.
        val wantsAch = HomeWidgets.ACH in overlayParts || HomeWidgets.PROGRESS in overlayParts
        LaunchedEffect(wantsAch, focusedGame?.stableKey) {
            val g = focusedGame ?: return@LaunchedEffect
            if (!wantsAch || g.raGameId == null) return@LaunchedEffect
            kotlinx.coroutines.delay(500)
            viewModel.loadAchievementDetails(g)
        }
        LaunchedEffect(wantsDesc, focusedGame?.stableKey, state.metadataReady) {
            val g = focusedGame ?: return@LaunchedEffect
            if (!wantsDesc || !state.metadataReady) return@LaunchedEffect
            kotlinx.coroutines.delay(450)
            viewModel.loadMetadata(g)
        }
        // Update 212: the category title where the layout shows none of its own right now (CategoryTitle.kt) — the same
        // Category name part, once the layout has settled (its own label registers as it's drawn).
        var catSettled by remember(state.appearance.homeLayout) { mutableStateOf(false) }
        LaunchedEffect(state.appearance.homeLayout) { kotlinx.coroutines.delay(600); catSettled = true }
        // (On the row layouts, Settings › Categories › Category name: Off is your choice of no name there: no fallback.)
        // Update 310: the fallback must follow the CURRENT layout, not the process-wide count of
        // native category titles. The old counter can include the other display (or a departing page).
        // On Green Core that toggled the whole category-pill bar over the MENU (visible in Toni's video).
        val nativeOnThisLayout = when (overlayLayout) {
            com.shelf.launcher.data.HomeLayout.GREENCORE -> GreenCoreNav.page == GreenCoreNav.Page.GAMES
            com.shelf.launcher.data.HomeLayout.TWIN_DESK,
            com.shelf.launcher.data.HomeLayout.TWIN_SHOWCASE -> state.appearance.categoryBar != com.shelf.launcher.data.CategoryBar.OFF
            com.shelf.launcher.data.HomeLayout.POCKET_CLASSIC,
            com.shelf.launcher.data.HomeLayout.POCKET_RIBBON -> true // native menu/category navigation
            else -> CategoryTitle.nativeShown()
        }
        val catFallback = overlayLayout != null && catSettled && HomeWidgets.CATEGORY !in overlayParts &&
            !nativeOnThisLayout &&
            com.shelf.launcher.data.Features.categoryTitle.state.value != com.shelf.launcher.data.Features.CategoryTitleMode.OFF &&
            HomeWidgets.overlayId(overlayLayout, HomeWidgets.CATEGORY) !in com.shelf.launcher.data.Features.hiddenParts.state.value &&
            !(!overlayLayout.styleHome && state.appearance.categoryBar == com.shelf.launcher.data.CategoryBar.OFF)
        // (Update 212: the category title shows with no game too — an empty category; the other parts need the game.)
        val partsHere = (if (catFallback) overlayParts + HomeWidgets.CATEGORY else overlayParts)
            .filter { focusedGame != null || it == HomeWidgets.CATEGORY }
        if (overlayLayout != null && partsHere.isNotEmpty()) {
            // (Update 125: over the games, always.)
            Box(Modifier.matchParentSize().zIndex(PartsLayer.z)) {
                // (Update 202: on the Voltcap row, a card added with + Add that its layout shows now anyway — News turned on
                // in Next to the row after adding it — is shown once: in the layout.)
                partsHere.filter { !(overlayLayout == com.shelf.launcher.data.HomeLayout.CLASSIC && it in HomeWidgets.present) }.forEachIndexed { idx, part ->
                    val id = HomeWidgets.overlayId(overlayLayout, part)
                    androidx.compose.runtime.key(id) {
                        val w = HomeWidgets.width(id) ?: when (part) {
                            HomeWidgets.INFO -> 420.dp
                            HomeWidgets.NEWS -> 380.dp
                            HomeWidgets.DESC -> 380.dp
                            HomeWidgets.LOGO, HomeWidgets.LOGO_SGDB -> 300.dp
                            HomeWidgets.LOGO_RA -> 120.dp
                            HomeWidgets.ACH -> 300.dp
                            HomeWidgets.PROGRESS -> 260.dp
                            HomeWidgets.CATEGORY, HomeWidgets.H_BADGE, HomeWidgets.H_ACTIONS, HomeWidgets.H_PLAY, HomeWidgets.H_BUTTONS, HomeWidgets.AWARD -> null
                            else -> 360.dp
                        }
                        MovableWidget(
                            id, defaultAlign = Alignment.CenterEnd, defaultPadding = PaddingValues(0.dp),
                            // The logo starts top left (over the art), the rest stacked on the right.
                            // (Update 204: the category name at its layout's free spot, categoryPartSpot.)
                            defaultFrac = if (part == HomeWidgets.CATEGORY) categoryPartSpot(overlayLayout)
                                else if (part == HomeWidgets.LOGO || part == HomeWidgets.LOGO_SGDB || part == HomeWidgets.LOGO_RA) logoPartSpot(overlayLayout)
                                // (Update 230, #09: Hero carousel with its covers on the right — the stack starts on the
                                // left, it sat on the cover column.)
                                // (Update 250, #94, RP Classic: TV room too — its games column is down the right, and an
                                // added part such as the achievement bar lay across its top row of games.)
                                else (if ((overlayLayout == com.shelf.launcher.data.HomeLayout.HERO && com.shelf.launcher.data.Features.heroCoversRight.state.value) ||
                                    overlayLayout == com.shelf.launcher.data.HomeLayout.TV) 0f else 1f) to
                                    (0.18f + idx * 0.17f).coerceAtMost(1f), fixedWidth = w,
                            // (Update 258, #120: the category name steps out of the way of a part you put on its spot.)
                            keepClearOf = if (part == HomeWidgets.CATEGORY) partsHere.filter { it != part }.map { HomeWidgets.overlayId(overlayLayout, it) } else emptyList()
                        ) {
                            // Update 204: the category's name on its own (all the categories with "All categories").
                            // (Update 212: the category title — the category or filter you're in, with or without a
                            // game; clear when it changes, then dimmed (Settings › Home › Category title).)
                            if (part == HomeWidgets.CATEGORY && state.appearance.categoryBar != com.shelf.launcher.data.CategoryBar.FULL) {
                                val key = state.sortCategoryKey + "|" + state.searchQuery
                                // Update 220 (#04): a tap on the category's name opens Choose a category (every category,
                                // Continue playing too) — TV room and the other layouts showing just the name had no way to
                                // change category by touch. (Not while Edit Home moves it; no focus, so the D-pad is unchanged.)
                                Box(Modifier.categoryTitleFade(key).then(
                                    if (HomeEditState.active) Modifier
                                    else Modifier.pointerInput(Unit) { detectTapGestures { openCategoryScreen() } }
                                )) {
                                    PartBackingBox(state.appearance.homeLayout) {
                                        CategoryNamePart(CategoryTitle.text(state.sortCategoryKey, state.searchQuery), state.appearance.nameBackdrop)
                                    }
                                }
                            }
                            else if (focusedGame == null) Unit
                            // Update 104: the logo sits straight on the art; the others on the backing picked in
                            // Layout options › Game info on screen (the layout's colours by default).
                            else if (part == HomeWidgets.LOGO) HomeLogoPart(focusedGame.name, bigLogo(focusedGame, state))
                            // Update 125: SteamGridDB's logo only / RetroAchievements' game icon only.
                            else if (part == HomeWidgets.LOGO_SGDB) HomeLogoPart(
                                focusedGame.name,
                                state.sgdbLogos[focusedGame.stableKey]?.takeIf { it.isNotEmpty() }
                                    ?: state.art[focusedGame.stableKey]?.logoUrl?.takeIf { it.contains("steamgriddb", ignoreCase = true) }
                            )
                            else if (part == HomeWidgets.LOGO_RA) HomeRaLogoPart(
                                focusedGame.name,
                                focusedGame.raGameId?.let { id -> state.raIcons[id] ?: com.shelf.launcher.data.RaBadges.mediaUrl(state.raCompletionById[id]?.ImageIcon) }
                            )
                            // Update 116: Play and the round buttons on their own sit right on the screen (no backing).
                            else if (part == HomeWidgets.H_PLAY || part == HomeWidgets.H_BUTTONS) HomeHeaderBlock(false, only = part)
                            else PartBackingBox(state.appearance.homeLayout) {
                                when (part) {
                                    HomeWidgets.CATEGORY -> CategoriesBar()
                                    HomeWidgets.INFO -> RowInfoBlock(com.shelf.launcher.data.RowInfo.GAME, padStart = false, padEnd = false)
                                    HomeWidgets.NEWS -> RowInfoBlock(com.shelf.launcher.data.RowInfo.NEWS, padStart = false, padEnd = false)
                                    HomeWidgets.DESC -> HomeDescriptionPart(focusedGame, state.metadata[focusedGame.stableKey]?.summary)
                                    HomeWidgets.ACH -> HomeAchievementsPart(focusedGame, state.raAchievementDetails[focusedGame.stableKey].orEmpty(), state.raProgress[focusedGame.stableKey])
                                    HomeWidgets.PROGRESS -> HomeProgressPart(focusedGame, state.raAchievementDetails[focusedGame.stableKey].orEmpty(), state.raProgress[focusedGame.stableKey])
                                    HomeWidgets.AWARD -> HomeAwardPart(
                                        focusedGame, awardsByKey[focusedGame.stableKey],
                                        focusedGame.raGameId?.let { id -> state.raIcons[id] ?: com.shelf.launcher.data.RaBadges.mediaUrl(state.raCompletionById[id]?.ImageIcon) }
                                    )
                                    else -> HomeHeaderBlock(false, only = part)
                                }
                            }
                        }
                    }
                }
            }
        }
        // Update 200: the console lit on the second screen's Games page, big over the dimmed home (fades in / out). It has no
        // touch or focus handling of its own, so the home and the second screen keep working; it steps aside for anything
        // opened over the home and for Settings › Top screen follows the Games page being off.
        val hoverShown = secondHoverCategory?.takeIf {
            com.shelf.launcher.data.Features.secondTopFollows.state.value && !homeCovered && !aDialogIsOpen && GamePopup.game == null
        }
        Box(Modifier.matchParentSize().zIndex(6.5f)) { TopScreenFolderPreview(hoverShown, Modifier.fillMaxSize()) }
        if (legendShown) {
            // Update 202: moved, resized or taken off in Edit Home like the layout's other parts (each layout its own).
            Box(Modifier.align(Alignment.BottomCenter).editablePart(LayoutParts.id(state.appearance.homeLayout, "legend"), "Button legend")) { LegendBar() }
        }
        // The details window. Drawn last so it sits above the dock; real
        // dialogs (achievement list, options) are separate windows and still
        // appear on top of it.
        // "Grows out of the game" (Appearance › Home screen): it springs up from the card you're on.
        // Worked out once per opening, without observing the row's scroll (that would recompose the
        // home screen on every frame of a slide).
        val zoomScr = screenSizeDp()
        val zoomDensity = androidx.compose.ui.platform.LocalDensity.current
        val zoomDockStart = dockTextStart()
        val zoomSizes = cardSizes()
        val zoomPinned = LocalPinnedFirstWide.current
        val zoomOrigin: androidx.compose.ui.graphics.TransformOrigin = remember(showDetails) {
            androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation {
                val cx: Float = if (tileLayout) {
                    val m = tileNameAnchor.metrics
                    val nx = tileNameAnchor.nameX?.value
                    val sc = tileNameAnchor.scroll?.value
                    if (m != null && nx != null && sc != null) ((m.start.value + nx - sc + m.tile.value / 2f) / zoomScr.width.value) else 0.2f
                } else if (state.appearance.focusPosition == com.shelf.launcher.data.FocusPosition.CENTER && !zoomPinned) 0.5f
                else if (zoomPinned && state.focusedIndex > 0) {
                    // Past the fixed first capsule: the cover you're on sits right after it.
                    ((zoomDockStart + zoomSizes.focused.width + zoomSizes.cover.width * 0.6f).value / zoomScr.width.value)
                } else ((zoomDockStart + zoomSizes.focused.width / 2).value / zoomScr.width.value)
                val cy = with(zoomDensity) { if (rowBottomPx > 0f) ((rowTopPx + rowBottomPx) / 2f) / zoomScr.height.toPx() else 0.7f }
                androidx.compose.ui.graphics.TransformOrigin(cx.coerceIn(0f, 1f), cy.coerceIn(0f, 1f))
            }
        }
        val popupZoom = state.appearance.popupZoom
        AnimatedVisibility(
            // On the main screen unless Voltcap's second screen is really up and shows it there (an app
            // holding the second display used to hide it on both: A seemed to do nothing).
            visible = showDetails && focusedGame != null && !(SecondScreenShown.value && state.appearance.secondDetails),
            enter = if (popupZoom) fadeIn(tween(140)) + scaleIn(androidx.compose.animation.core.spring(dampingRatio = 0.72f, stiffness = 420f), initialScale = 0.3f, transformOrigin = zoomOrigin)
            else fadeIn(tween(160)) + scaleIn(tween(160), initialScale = 0.96f),
            exit = if (popupZoom) fadeOut(tween(140)) + androidx.compose.animation.scaleOut(tween(160), targetScale = 0.4f, transformOrigin = zoomOrigin)
            else fadeOut(tween(120))
        ) {
            val game = focusedGame
            if (game != null) DetailsWindow(game, fillScreen = false)
        }

        // New games just found by a scan: the arrival show at the top (Appearance › Home screen).
        if (state.newArrivals.isNotEmpty()) {
            if (!state.appearance.arrivalAnim) LaunchedEffect(state.newArrivals) { viewModel.consumeNewArrivals() }
            else if (!aDialogIsOpen && !showDetails && launchingGame == null && !screensaverOn) {
                val byKeyArr = remember(state.games) { state.games.associateBy { it.stableKey } }
                val arrTitles = LocalTitleOptions.current
                NewArrivalShow(
                    keys = state.newArrivals,
                    nameOf = { k -> byKeyArr[k]?.let { arrTitles.display(it.name) } ?: k.substringAfterLast('/') },
                    coverFor = { k -> state.artOverrides[k]?.cover ?: state.art[k]?.gridUrl },
                    consoleOf = { k -> byKeyArr[k]?.takeIf { it.sourceType != com.shelf.launcher.data.SourceType.APP }?.let { arrTitles.console(it.platform) } },
                    onDone = { viewModel.consumeNewArrivals() },
                    // Under the status bar (it used to cover the clock and the player there).
                    modifier = Modifier.align(Alignment.TopCenter).padding(
                        top = if (state.appearance.dockPosition != com.shelf.launcher.data.DockPosition.TOP) maxOf(30.dp, statusBarDp + 6.dp) else 30.dp
                    )
                )
            }
        }

        launchingGame?.let { game ->
            // Update 200: the picture and shape of the card it starts from (was the cover cut to 2:3 whatever the card).
            val launchArt = remember(game) { launchArtFor(game, state, state.visibleGames.getOrNull(state.focusedIndex)?.stableKey) }
            LaunchAnimation(
                coverUrl = launchArt.url,
                aspect = launchArt.aspect,
                crop = launchArt.crop,
                // (Update 202: a cover trimmed as the cards show it.)
                trim = launchArt.trim,
                title = LocalTitleOptions.current.display(game.name),
                onDone = {
                    launchingGame = null
                    launchAfterIntro(game, false)
                },
                heroUrl = state.artOverrides[game.stableKey]?.hero ?: state.art[game.stableKey]?.heroUrl,
                subtitle = if (game.sourceType == com.shelf.launcher.data.SourceType.APP) null
                else LocalTitleOptions.current.console(game.platform)
            )
        }

        if (showWork) BackgroundWorkDialog(
            state,
            onStopArt = { viewModel.cancelScrape() },
            onStopDetails = { viewModel.cancelMetadataScrape() },
            onStopSaving = { viewModel.cancelArtSave() },
            onOpenSettings = { menuStartCategory = "Metadata"; showMenu = true },
            onDismiss = { showWork = false }
        )
        // Update 225 (#102): the status line ("Couldn't launch …", "No emulator set up for …") was drawn only by the
        // Voltcap row and Tile home: on every other layout a game that didn't start just did nothing. There it shows here.
        if (heroLayout || gridLayout || tvLayout || styleLayout) state.statusMessage?.let { message ->
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 64.dp), contentAlignment = Alignment.Center) {
                Text(
                    message, color = Color.White, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
        // Update 151: the case opens / the cartridge or disc is thrown in; the game starts when it ends.
        LaunchThrow.plan?.let { plan ->
            LaunchThrowOverlay(plan) {
                if (LaunchThrow.plan === plan) { LaunchThrow.plan = null; launchAfterIntro(plan.game, false) }
            }
        }

        introVideo?.let { (game, address) ->
            PlatformIntroVideo(address) {
                // Both the end of the clip and a skip land here; only the first one starts the game.
                if (introVideo != null) { introVideo = null; viewModel.launch(game) }
            }
        }

        // R3: every control on one wide, low card along the bottom (three
        // columns, so it fits a landscape screen). Any button closes it.
        AnimatedVisibility(
            visible = showControlsHelp,
            enter = fadeIn(tween(140)) + scaleIn(tween(140), initialScale = 0.97f),
            exit = fadeOut(tween(100)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ControlsHelpCard(
                hotkeys = state.appearance.hotkeys,
                musicButtons = state.appearance.musicButtons,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 30.dp)
            )
        }

        // (The session summary popup after a game is gone: the details window shows "Last session" instead.)

        // Google Drive sync: in the status bar; a small note in a corner only when the status bar is off.
        // (0.6.15, #120 Q45: TV channels and Hero tiles have games right under their top row — there it sits in the
        // middle of the top row instead, where those layouts leave room.)
        val noticeTopMiddle = state.appearance.homeLayout == com.shelf.launcher.data.HomeLayout.CHAN ||
            state.appearance.homeLayout == com.shelf.launcher.data.HomeLayout.HERO_TILES
        if (!state.appearance.statusOn) SyncCornerNotice(
            notice = uiState.syncNotice,
            kind = uiState.syncNoticeKind,
            // (0.6.17: in the gap between the category name on the left and the clock & round buttons on the right —
            // centred on the screen it reached over TV channels' clock; it's narrower there when the gap is small.)
            modifier = if (noticeTopMiddle) Modifier.align(Alignment.TopCenter).padding(top = 8.dp, start = 200.dp, end = 280.dp) else Modifier
                .align(
                    if (state.appearance.dockPosition == com.shelf.launcher.data.DockPosition.TOP) Alignment.BottomEnd
                    else Alignment.TopEnd
                )
                .padding(
                    // (Update 211: below the layouts' own top row — at 44 dp it sat over their clock and icons, e.g.
                    // Spotlight on the RP Classic.)
                    top = 60.dp, end = 16.dp,
                    bottom = if (state.appearance.dockPosition == com.shelf.launcher.data.DockPosition.TOP) 30.dp else 0.dp
                )
        )

        AnimatedVisibility(
            visible = categoryScreenOpen,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(120))
        ) {
            val browserView = com.shelf.launcher.data.Features.platformView.value
            if (browserView == com.shelf.launcher.data.Features.PlatformView.TILES) {
                CategoryScreen(
                    categoryTiles, categoryIndex, onPick = { pickCategory(it) },
                    onMove = { from, to -> moveCategoryTile(from, to).also { if (it) sounds?.play(UiSound.MOVE) } },
                    onSelect = { categoryIndex = it },
                    art = com.shelf.launcher.data.Features.categoryArt.state.value, style = state.appearance.categoryTileStyle,
                    // (Update 212: the tile's options — its picture, Reorder; a hold reorders now.)
                    onOptions = { i -> categoryTiles.getOrNull(i)?.let { tile -> categoryIndex = i; pbState.options = tile } }
                )
            } else {
                PlatformBrowser(
                    categoryTiles, categoryIndex, pbState, browserView,
                    onFocus = { categoryIndex = it },
                    onPick = { pickCategory(it) },
                    onClose = { categoryScreenOpen = false; sounds?.play(UiSound.BACK) },
                    onRandom = { randomFromBrowser() },
                    onReorder = { from, to -> reorderPlatforms(from, to) },
                    topInset = if (state.appearance.statusOn) 40.dp else 0.dp
                )
            }
        }
        // A random game was asked for: once its category is the one showing, pick and open it.
        LaunchedEffect(pbRandomFor, currentCategoryKey) {
            val want = pbRandomFor
            if (want != null && want == currentCategoryKey) {
                kotlinx.coroutines.delay(80)
                pbRandomFor = null
                runHotkey(com.shelf.launcher.data.Hotkey(action = com.shelf.launcher.data.HotkeyAction.RANDOM_GAME))
            }
        }

        quickJump?.let { letter ->
            QuickJumpBar(
                available = availableLetters,
                selected = letter,
                onPick = { picked ->
                    viewModel.jumpToLetter(picked)
                    quickJump = null
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.92f)
            )
        }

        AnimatedVisibility(
            visible = stickLetter != null && quickJump == null,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(250)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                Modifier
                    .size(110.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    (stickLetter ?: ' ').toString(),
                    color = ShelfTheme.accent,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        AnimatedVisibility(
            visible = screensaverOn,
            enter = fadeIn(tween(1200)),
            exit = fadeOut(tween(250))
        ) {
            val slides = remember(state.games, state.art, state.artOverrides) {
                state.games.mapNotNull { game ->
                    val override = state.artOverrides[game.stableKey]
                    val art = state.art[game.stableKey]
                    val image = override?.hero ?: art?.heroUrl ?: override?.wide ?: art?.wideGridUrl
                        ?: return@mapNotNull null
                    ScreensaverSlide(game.name, game.platform, image, override?.logo ?: art?.logoUrl)
                }.shuffled().take(80)
            }
            ScreenSaver(
                slides = slides,
                nowPlaying = state.nowPlaying,
                showMusic = state.screensaver.showMusic,
                blackoutMinutes = state.screensaver.blackoutMinutes,
                onDismiss = { screensaverOn = false },
                showLyrics = state.screensaver.showLyrics,
                sharpen = state.appearance.sharpHeroes,
                albumBlur = state.appearance.secondMusicBlurArt,
                albumBlurPercent = state.appearance.secondMusicBlur,
                animatedTheme = if (state.screensaver.style == com.shelf.launcher.data.SaverStyle.THEME)
                    state.screensaver.theme?.let { n -> runCatching { com.shelf.launcher.ui.theme.AppTheme.valueOf(n) }.getOrNull() } ?: state.appTheme
                    else null,
                musicTheme = if (state.screensaver.musicStyle == com.shelf.launcher.data.SaverMusicStyle.THEME)
                    (state.screensaver.musicTheme?.let { n -> runCatching { com.shelf.launcher.ui.theme.AppTheme.valueOf(n) }.getOrNull() }
                        ?: com.shelf.launcher.ui.theme.AppTheme.NOW_PLAYING)
                    else null,
                stayWithMusic = state.screensaver.stayWithMusic,
                // Update 202: the main screen's screensaver faded to black: Voltcap's background music fades out too.
                // (Only here: the second screen's saver above doesn't pass it.)
                onBlackout = { black -> MusicBlackout.setSaverBlack(black) }
            )
        }
    }

    @Composable
    fun GameDialogs() {
    if (showSettings) {
        SteamGridDbDialog(
            state = state.artState,
            statusMessage = state.statusMessage,
            isKeyEncrypted = state.isArtKeyEncrypted,
            onConnect = { key -> viewModel.connect(key) },
            onDisconnect = { viewModel.disconnectArt() },
            onDismiss = { showSettings = false; showMenu = true }
        )
    }


    configuringArtForGame?.let { game ->
        ArtIdDialog(
            game = game,
            onSave = { rawId ->
                viewModel.setManualArtId(game, rawId)
                configuringArtForGame = null
            },
            onDismiss = { configuringArtForGame = null }
        )
    }

    configuringRaForGame?.let { game ->
        val manualId = remember(game.stableKey, state.games) { viewModel.manualRaId(game) }
        RaIdDialog(
            game = game,
            match = state.raMatches[game.stableKey],
            manualId = manualId,
            customHash = state.raCustomHashes[game.stableKey],
            isMatching = state.isMatchingAchievements,
            onSaveId = { rawId ->
                viewModel.setManualRaId(game, rawId)
                configuringRaForGame = null
            },
            onSaveHash = { rawHash -> viewModel.setCustomRaHash(game, rawHash) },
            onRehash = { viewModel.rehashGame(game) },
            onDismiss = { configuringRaForGame = null }
        )
    }

    optionsForGame?.let { game ->
        val screensPlan = remember(game.stableKey, state.screenLayoutsVersion, state.appearance.useSecondScreen) {
            viewModel.screenPlanFor(game)
        }
        GameOptionsDialog(
            game = game,
            isFavorite = game.stableKey in state.favorites,
            onPlay = { optionsForGame = null; showDetails = false; startGame(game) },
            onToggleFavorite = { viewModel.toggleFavorite(game) },
            onEditArtwork = {
                optionsForGame = null
                editingArtworkForGame = game
                viewModel.loadArtCandidates(game)
            },
            // Update 195: draw it on any shell of the cartridge library.
            onCartShell = if (game.sourceType == com.shelf.launcher.data.SourceType.APP && game.packageName?.startsWith("voltcap:") == true) null
                else { { optionsForGame = null; cartShellForGame = game } },
            // Update 274 (Toni): its piece out in the game window, whatever the look (MediaInspect.force).
            onDsCase = if (game.platform == "NDS") ({ optionsForGame = null; dsCaseForGame = game }) else null,
            onInspect = if (game.sourceType == com.shelf.launcher.data.SourceType.APP) null
                else { { optionsForGame = null; MediaInspect.testShell = null; MediaInspect.testPose = null; MediaInspect.forceSeq++; MediaInspect.force = game.stableKey; GamePopup.game = game } },
            onShowDetails = {
                optionsForGame = null
                if (!showDetails) openDetails()
            },
            onConfigureEmulator = { optionsForGame = null; configuringPlatform = game.platform },
            onLinkAchievements = { optionsForGame = null; configuringRaForGame = game },
            onViewAchievements = { optionsForGame = null; viewingAchievementsForGame = game },
            onHideApp = { optionsForGame = null; viewModel.hideApp(game) },
            onRemoveSteamGame = {
                optionsForGame = null
                game.steamAppId?.let(viewModel::removeSteamGame)
            },
            onDismiss = { optionsForGame = null },
            usesIconArt = uiState.usesOwnIcon(game),
            onToggleIconArt = { game.packageName?.let { viewModel.toggleAppIconArt(it) } },
            isEmulator = game.packageName in uiState.emulatorPackages,
            onToggleEmulator = { game.packageName?.let { viewModel.toggleAppEmulator(it) } },
            showCollections = state.appearance.userCollections,
            collectionCount = state.userCollections.count { game.stableKey in it.value },
            onCollections = { optionsForGame = null; collectionsForGame = game },
            showStatus = state.appearance.playStatus,
            playStatus = state.playStatuses[game.stableKey]?.status,
            onStatus = { optionsForGame = null; statusForGame = game },
            screensSubtitle = screensPlan?.let { screensRowSubtitle(it.resolved) },
            onScreens = { optionsForGame = null; screensForGame = game },
            onRefetchRaIcon = { optionsForGame = null; viewModel.refetchRaIcon(game) },
            onSort = { optionsForGame = null; showSortDialog = true },
            onGuide = if (game.sourceType != com.shelf.launcher.data.SourceType.APP) {
                { optionsForGame = null; guideForGame = game; viewModel.openGuide(game) }
            } else null,
            onGameSettings = { optionsForGame = null; profileForGame = game },
            onGameMusic = {
                optionsForGame = null
                musicOverrideFor = com.shelf.launcher.data.MusicOverrides.gameKey(game.stableKey) to com.shelf.launcher.ui.stripRegionTags(game.name)
            },
            appSettingsPackage = viewModel.emulatorFor(game),
            // (Update 244: a game in Continue playing can be taken off it — until it's played again.)
            onRemoveFromContinue = if (state.lastPlayedAt.containsKey(game.stableKey)) {
                { optionsForGame = null; viewModel.removeFromContinuePlaying(game) }
            } else null
        )
    }

    profileForGame?.let { game ->
        GameProfileDialog(
            game = game,
            viewModel = viewModel,
            displayVersion = state.screenLayoutsVersion,
            hasCustomArt = state.artOverrides[game.stableKey]?.isEmpty() == false,
            onOpenScreens = { profileForGame = null; screensForGame = game },
            onOpenArtwork = {
                profileForGame = null
                editingArtworkForGame = game
                viewModel.loadArtCandidates(game)
            },
            onOpenEmulator = { profileForGame = null; configuringPlatform = game.platform },
            onDismiss = { profileForGame = null }
        )
    }

    steamAchFor?.let { game -> SteamAchievementsDialog(game) { steamAchFor = null } }
    leaderboardsFor?.let { game -> RaLeaderboardsDialog(game) { leaderboardsFor = null } }

    manualMissing?.let { game ->
        ManualMissingDialog(
            game = game,
            screenScraper = viewModel.screenScraperConnected(),
            onPicked = { uri -> manualMissing = null; viewModel.importManual(game, uri) { file -> manualFor = game to file } },
            onDismiss = { manualMissing = null }
        )
    }
    manualFor?.let { (game, file) ->
        ManualViewer(file, gameDisplayName(game.name)) { manualFor = null }
    }
    // Update 202: the manual from anywhere — the game window's Manual button, the booklet in an opened case, the second
    // screen's Details page: found (or downloaded once, its progress in ManualDownloadDialog), then read.
    androidx.compose.runtime.DisposableEffect(viewModel) {
        com.shelf.launcher.data.ManualLinks.open = { g ->
            viewModel.openManual(g, onReady = { file -> manualFor = g to file }, onMissing = { manualMissing = g })
        }
        onDispose { com.shelf.launcher.data.ManualLinks.open = null }
    }
    ManualDownloadDialog()

    // Phone web remote: its game list and "start this game".
    androidx.compose.runtime.DisposableEffect(state.games) {
        val games = state.games
        com.shelf.launcher.data.WebRemote.games = {
            games.filter { it.sourceType != com.shelf.launcher.data.SourceType.APP || it.platform != "App" }
                .map { Triple(it.stableKey, gameDisplayName(it.name), com.shelf.launcher.data.ConsoleNames.display(it.platform)) }
        }
        val launch: (String) -> Unit = { key ->
            games.firstOrNull { it.stableKey == key }?.let { g -> android.os.Handler(android.os.Looper.getMainLooper()).post { startGame(g) } }
        }
        com.shelf.launcher.data.WebRemote.onLaunch = launch
        // A home screen that's gone mustn't keep receiving launches (a rebuilt one sets its own first).
        onDispose { if (com.shelf.launcher.data.WebRemote.onLaunch === launch) com.shelf.launcher.data.WebRemote.onLaunch = null }
    }

    if (showWhatToPlay) {
        WhatToPlayDialog(
            suggest = { exclude ->
                com.shelf.launcher.data.PlaySuggest.pick(
                    state.visibleGames, state.lastPlayedAt, state.playtime, state.favorites,
                    if (state.appearance.playStatus) state.playStatuses else emptyMap(),
                    state.raCompletionById, exclude
                )
            },
            onPick = { game ->
                showWhatToPlay = false
                val i = state.visibleGames.indexOfFirst { it.stableKey == game.stableKey }
                if (i >= 0) { viewModel.setFocus(i); openDetails() }
            },
            onDismiss = { showWhatToPlay = false }
        )
    }

    if (showSortDialog) {
        val special = state.specialFilter
        val name = when {
            special != null -> special.label
            state.platformFilter == null -> "All"
            state.platformFilter == com.shelf.launcher.ANDROID_GAMES_PLATFORM -> "Android games"
            com.shelf.launcher.data.isCollectionKey(state.platformFilter) -> com.shelf.launcher.data.collectionLabel(state.platformFilter)
            else -> state.platformFilter
        }
        val catKey = state.sortCategoryKey
        SortDialog(
            categoryName = name,
            override = state.categorySort[catKey],
            globalMode = state.appearance.sortMode,
            keepsOwnOrder = special == SpecialFilter.RECENT || special == SpecialFilter.MOST_PLAYED ||
                special == SpecialFilter.ALMOST_MASTERED,
            onPick = { viewModel.setCategorySort(catKey, it) },
            onUseDefault = { viewModel.setCategorySort(catKey, null) },
            onApplyToAll = { viewModel.applySortToAllCategories(it) },
            onDismiss = { showSortDialog = false }
        )
    }

    screensForGame?.let { game ->
        val plan = remember(game.stableKey, state.screenLayoutsVersion) { viewModel.screenPlanFor(game) }
        if (plan == null) {
            // The second screen went away (or was switched off) meanwhile.
            LaunchedEffect(Unit) { screensForGame = null }
        } else {
            ScreenLayoutPickerDialog(
                game = game,
                setup = plan.setup,
                current = plan.resolved,
                layouts = remember(state.screenLayoutsVersion) { viewModel.screenLayouts() },
                collections = remember(game.stableKey, state.collections) { viewModel.collectionsOf(game) },
                emulatorPackage = remember(game.stableKey, state.emulatorConfigs) { viewModel.emulatorFor(game) },
                onPlayOnce = { layoutId ->
                    screensForGame = null
                    showDetails = false
                    viewModel.setNextLaunchLayout(layoutId)
                    startGame(game)
                },
                onSave = { scope, value, layoutId ->
                    viewModel.setScreenRule(scope, value, layoutId, plan.setup)
                    screensForGame = null
                },
                onClearGameRule = {
                    viewModel.setScreenRule(com.shelf.launcher.data.RuleScope.GAME, game.stableKey, null, plan.setup)
                    screensForGame = null
                },
                onDismiss = { screensForGame = null }
            )
        }
    }

    collectionsForGame?.let { game ->
        CollectionPickerDialog(
            game = game,
            collections = state.userCollections,
            onToggle = { name -> viewModel.toggleInCollection(name, game) },
            onCreate = { name -> viewModel.createCollection(name.trim(), game) },
            onDismiss = { collectionsForGame = null }
        )
    }
    statusForGame?.let { game ->
        PlayStatusDialog(
            game = game,
            current = state.playStatuses[game.stableKey]?.status,
            onPick = { st -> viewModel.setPlayStatus(game, st); statusForGame = null },
            onDismiss = { statusForGame = null }
        )
    }
    tipsForGame?.let { game ->
        val tips = game.raGameId?.let { state.raGameTips[it] }
        if (tips != null) TipsDialog(
            game, tips, onDismiss = { tipsForGame = null },
            // Only a guide we can read as text opens in the app; anything else stays a browser link.
            onOpenGuide = tips.guideUrl?.takeIf { com.shelf.launcher.data.guide.RetroAchievementsGuideProvider.toRawUrl(it) != null }
                ?.let { { tipsForGame = null; guideForGame = game; viewModel.openGuide(game) } }
        )
        else LaunchedEffect(Unit) { tipsForGame = null }
    }
    guideForGame?.let { game ->
        GuideScreen(
            game = game,
            state = guideUi?.takeIf { it.gameKey == game.stableKey },
            onDismiss = { guideForGame = null; viewModel.closeGuide() },
            onChoose = { viewModel.chooseGuide(game, it) },
            onContinue = { viewModel.continueGuide(game) },
            onLocate = { viewModel.locateNow(game) },
            onCorrect = { viewModel.correctSection(game, it) },
            onSpoiler = { viewModel.setGuideSpoiler(game, it) },
            onMissablesMode = { viewModel.setGuideMissablesMode(game, it) },
            onPickFolder = { viewModel.setLocalGuideFolder(it) }
        )
    }
    }

    @Composable
    fun ViewerDialogs() {
    viewingShots?.let { (files, start) ->
        ScreenshotViewer(files, start, onDismiss = { viewingShots = null })
    }
    artViewer?.let { (items, start) ->
        val game = focusedGame
        ArtworkViewer(
            items = items,
            start = start,
            override = game?.let { state.artOverrides[it.stableKey] },
            onUseAsCardArt = { slot, url -> game?.let { viewModel.setArtOverride(it, slot, url) } },
            onDismiss = { artViewer = null }
        )
    }

    dsCaseForGame?.let { game -> DsCasePicker(game) { dsCaseForGame = null } }
    cartShellForGame?.let { game ->
        CartShellPicker(game, state.artOverrides[game.stableKey]?.cover ?: state.art[game.stableKey]?.gridUrl) { cartShellForGame = null }
    }

    editingArtworkForGame?.let { game ->
        // (Update 137: its box, disc, cartridge and spine scans, for picking those too.)
        androidx.compose.runtime.LaunchedEffect(game.stableKey) { viewModel.loadPhysicalMedia(game) }
        ArtworkPickerDialog(
            game = game,
            candidates = state.artCandidates,
            isLoading = state.isLoadingArtCandidates,
            currentOverride = state.artOverrides[game.stableKey],
            scrapedArt = uiState.art[game.stableKey],
            onSet = { slot, value -> viewModel.setArtOverride(game, slot, value) },
            onSearch = { query -> viewModel.searchArtCandidates(query) },
            onClearAll = { viewModel.clearArtOverrides(game) },
            onDismiss = {
                editingArtworkForGame = null
                viewModel.clearArtCandidates()
            },
            metadata = state.metadata[game.stableKey],
            metadataLoading = game.stableKey in state.metadataLoading,
            metadataNote = state.metadataNotes[game.stableKey],
            metadataSource = state.metadataSource,
            igdbConnected = state.igdbConnected,
            screenScraperConnected = state.screenScraperConnected,
            rawgConnected = state.rawgConnected,
            onScrapeMetadata = { source, name -> viewModel.scrapeMetadataFor(game, source, name) },
            detailCandidates = state.detailCandidates[game.stableKey],
            detailCandidatesLoading = game.stableKey in state.detailCandidatesLoading,
            onCompareSources = { name -> viewModel.compareDetailSources(game, name) },
            onApplyDetailField = { field, from -> viewModel.applyDetailField(game, field, from) },
            onApplyBestOfEach = { viewModel.applyBestOfEachSource(game) },
            physical = state.physicalMedia[game.stableKey].orEmpty(),
            physicalPicks = com.shelf.launcher.data.PhysicalOverrides.of(game.stableKey),
            onSetPhysical = { label, url -> viewModel.setPhysicalOverride(game, label, url) },
            heroUpscale = state.heroUpscale[game.stableKey],
            onHeroUpscale = { viewModel.setHeroUpscale(game, it) }
        )
    }

    }

    @Composable
    fun MediaDialogs() {
    val popupPlaying = state.nowPlaying
    LaunchedEffect(showMusicPopup, popupPlaying == null) { if (showMusicPopup && popupPlaying == null) showMusicPopup = false }
    if (showMusicPopup && popupPlaying != null) {
        // A tap on a small player: the big player in a popup (big cover and buttons; the playlists are one swipe / arrow away).
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showMusicPopup = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            ImmersiveDialogWindow()
            Box(
                Modifier
                    .fillMaxWidth(0.94f)
                    .fillMaxHeight(0.94f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black)
            ) {
                BigMusicPlayer(
                    nowPlaying = popupPlaying,
                    blurArt = state.appearance.secondMusicBlurArt,
                    blurPercent = state.appearance.secondMusicBlur,
                    showLyrics = state.appearance.secondMusicLyrics,
                    onToggleLyrics = { viewModel.setAppearance(state.appearance.copy(secondMusicLyrics = !state.appearance.secondMusicLyrics)) },
                    onBrowseSpotify = { showMusicPopup = false; showSpotify = true },
                    onPlayPause = { viewModel.mediaPlayPause() },
                    onNext = { viewModel.mediaNext() },
                    onPrevious = { viewModel.mediaPrevious() },
                    onSeek = { viewModel.mediaSeekTo(it) },
                    onClose = { showMusicPopup = false }
                )
            }
        }
    }

    playStatsForGame?.let { g ->
        GamePlayStatsDialog(
            game = g,
            totalMs = state.playtime[g.stableKey],
            lastPlayedAt = state.lastPlayedAt[g.stableKey],
            raProgress = state.raProgress[g.stableKey],
            onDismiss = { playStatsForGame = null }
        )
    }

    // (Update 231, #81: the classic details window's quick performance settings for this game — the same rows as the
    // game popup's Performance page; Advanced… opens the game's settings on top, closing them comes back here.)
    perfForGame?.let { g ->
        GamePerfQuickDialog(
            game = g,
            perfPackage = remember(g.stableKey) { if (g.sourceType == com.shelf.launcher.data.SourceType.APP) g.packageName else viewModel.emulatorFor(g) },
            onAdvanced = { profileForGame = g },
            onDismiss = { perfForGame = null }
        )
    }

    // Update 113: the game window (A or a tap on the game you're on), in the layout's own look, over the main screen.
    GamePopup.game?.let { g ->
        GameWindow(
            layout = state.appearance.homeLayout, g = g, state = state, art = displayArt,
            onClose = { GamePopup.game = null },
            onPlay = { GamePopup.game = null; startGame(g) },
            onFullDetails = { GamePopup.game = null; openDetails() },
            onNeedMedia = { gg -> viewModel.loadMedia(gg); viewModel.loadVideoUrl(gg); viewModel.loadMetadata(gg); viewModel.loadPhysicalMedia(gg); viewModel.loadAchievementDetails(gg) },
            // Update 119: the game's options, its achievements and its save sync, from the window.
            onOptions = { GamePopup.game = null; optionsForGame = g },
            // (Update 129: always this game's own — a Steam game its Steam achievements, a game not matched to
            // RetroAchievements yet the matching for it; it used to open the whole achievements hub.)
            onAchievements = {
                GamePopup.game = null
                when {
                    g.raGameId != null -> viewingAchievementsForGame = g
                    g.platform == "Steam" -> steamAchFor = g
                    else -> configuringRaForGame = g
                }
            },
            // (Update 213: the game's sync opens over the window — closing it comes back to the window.)
            onSync = { saveSyncForGame = g },
            // Update 132: the comments under an achievement opened on the window's achievements page.
            onLoadComments = { id -> viewModel.loadAchievementComments(id) },
            // Update 213: Performance — the emulator's / app's own profile counts too (as the tuner resolves it), the
            // whole tuner in the game's settings over the window, Settings › Sync when save sync isn't set up, and the
            // game's own "Sync saves".
            perfPackage = remember(g.stableKey) { if (g.sourceType == com.shelf.launcher.data.SourceType.APP) g.packageName else viewModel.emulatorFor(g) },
            onPerfAdvanced = { profileForGame = g },
            onSyncSetUp = { GamePopup.game = null; menuStartCategory = "Sync"; showMenu = true },
            syncOn = remember(g.stableKey, profileForGame) {
                viewModel.effectiveProfile(g).syncSaves.let { if (it.source == com.shelf.launcher.data.ProfileSource.GLOBAL) null else it.value }
            },
            // Update 125: the game's options on the window's own page, in its style; only what the window doesn't
            // already have (no Play, Details or achievement list) and nothing that's for the whole library.
            options = gameWindowOptions(
                g, state, uiState, viewModel,
                close = { GamePopup.game = null },
                onGameSettings = { profileForGame = g },
                onGameMusic = { musicOverrideFor = com.shelf.launcher.data.MusicOverrides.gameKey(g.stableKey) to com.shelf.launcher.ui.stripRegionTags(g.name) },
                onGuide = { guideForGame = g; viewModel.openGuide(g) },
                onStatus = { statusForGame = g },
                onCollections = { collectionsForGame = g },
                onEmulator = { configuringPlatform = g.platform },
                onMatching = { configuringRaForGame = g }
            )
        )
    }

    if (showSaveExplorer) {
        // Saves whose game isn't in this library: their covers looked up by name (once; kept).
        val vaultArt by viewModel.vaultArt.collectAsState()
        val capsules by com.shelf.launcher.data.SaveCapsules.all.collectAsState()
        LaunchedEffect(capsules) {
            // (0.6.18: not for saves without a game name — there is nothing to look up.)
            val away = capsules.filter { !it.onDevice && it.named }.map { it.game }
            if (away.isNotEmpty()) viewModel.loadVaultArt(away)
        }
        SaveExplorerDialog(
            games = state.games,
            iconFor = { g ->
                g.raGameId?.let { id -> state.raIcons[id] ?: com.shelf.launcher.data.RaBadges.mediaUrl(state.raCompletionById[id]?.ImageIcon) }
                    ?: state.artOverrides[g.stableKey]?.cover ?: state.art[g.stableKey]?.gridUrl ?: vaultArt[g.stableKey]
            },
            onOpenGame = { g -> showSaveExplorer = false; saveSyncForGame = g },
            onDismiss = { showSaveExplorer = false },
            lastPlayed = state.lastPlayedAt,
            // Update 112: the vault always uses the consoles' full names (Game Boy Color, not GBC).
            consoleName = { p -> com.shelf.launcher.data.ConsoleNames.display(p) },
            // (Update 285: Icons only — the RetroAchievements icon, else the SteamGridDB logo, else as above.)
            logoFor = { g ->
                g.raGameId?.let { id -> state.raIcons[id] ?: com.shelf.launcher.data.RaBadges.mediaUrl(state.raCompletionById[id]?.ImageIcon) }
                    ?: state.artOverrides[g.stableKey]?.logo ?: state.art[g.stableKey]?.logoUrl
                    ?: state.artOverrides[g.stableKey]?.cover ?: state.art[g.stableKey]?.gridUrl ?: vaultArt[g.stableKey]
            }
        )
    }

    runningAsk?.let { (game, others) ->
        val names = others.joinToString(", ") { gameDisplayName(it.name) }
        ShelfMenuDialog(
            title = "$names ${if (others.size == 1) "is" else "are"} still open",
            subtitle = "Start ${gameDisplayName(game.name)}:",
            icon = Icons.Filled.SportsEsports,
            onDismiss = { runningAsk = null }
        ) {
            MenuRow(Icons.Filled.Close, "Quit ${if (others.size == 1) "it" else "them"} and play", "Ends the open game first — unsaved progress there is lost") {
                runningAsk = null
                others.forEach { viewModel.closeRunning(it) }
                startGame(game, askIfOthersOpen = false)
            }
            MenuRow(Icons.Filled.PlayArrow, "Keep both open", "Start ${gameDisplayName(game.name)}; the other stays in the background") {
                runningAsk = null
                startGame(game, askIfOthersOpen = false)
            }
            MenuRow(Icons.Filled.Cancel, "Cancel") { runningAsk = null }
        }
    }

    if (showSyncStatus) {
        SyncStatusDialog(
            gameKey = r1SyncGame?.stableKey,
            gameName = r1SyncGame?.let { gameDisplayName(it.name) },
            onOpenGameSync = r1SyncGame?.let { g -> { showSyncStatus = false; saveSyncForGame = g } },
            onOpenSettings = { showSyncStatus = false; menuStartCategory = "Sync"; showMenu = true },
            onDismiss = { showSyncStatus = false },
            games = state.games,
            coverFor = { k -> state.artOverrides[k]?.cover ?: state.art[k]?.gridUrl }
        )
    }

    // RomM (update 93): the library on your RomM server; finished downloads rescan the library.
    if (RommUi.showing) {
        RommBrowser(onDismiss = { RommUi.showing = false }, onAdded = { viewModel.rescanEverything() })
    }
    // Downloads that finished while the browser was closed (or Voltcap wasn't running): moved in and scanned now.
    val rommContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        while (true) {
            com.shelf.launcher.data.GameMode.awaitNotPlaying()
            val added = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { com.shelf.launcher.data.Romm.poll(rommContext) }.getOrDefault(0)
            }
            if (added > 0 && !RommUi.showing) viewModel.rescanEverything()
            kotlinx.coroutines.delay(if (com.shelf.launcher.data.Romm.progress.value.isEmpty()) 120_000 else 5_000)
        }
    }
    if (showSpotify) {
        com.shelf.launcher.ui.spotify.SpotifyScreen(
            onDismiss = { showSpotify = false },
            onOpenAccounts = { showSpotify = false; menuStartCategory = "Accounts"; showMenu = true },
            blurPercent = state.appearance.secondMusicBlur,
            onBlurChange = { viewModel.setAppearance(state.appearance.copy(secondMusicBlur = it)) }
        )
    }

    // Update 137: an R1 tile's control on the home screen (Art drop, Cartridge colour): the R1 panel closes for it, and
    // it sits under the game, or above it when the game row is at the bottom.
    androidx.compose.runtime.LaunchedEffect(HomeAdjust.open) { if (HomeAdjust.open != null) showNotifications = false }
    HomeAdjustBar(atTop = state.appearance.dockPosition == com.shelf.launcher.data.DockPosition.BOTTOM)

    if (showNotifications) {
        // Re-check on every return to the app while open: coming back from the
        // Settings toggle otherwise leaves the panel insisting access is off.
        // (Uses the same lifecycleOwner as the play-session hook above.)
        androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    viewModel.refreshNotifications()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        NotificationsPanel(
            notificationsOnly = notificationsOnly,
            hasAccess = state.hasNotificationAccess,
            errorMessage = state.notificationError,
            notifications = state.notifications,
            nowPlaying = state.nowPlaying,
            onPlayPause = { viewModel.mediaPlayPause() },
            onNext = { viewModel.mediaNext() },
            onPrevious = { viewModel.mediaPrevious() },
            onSeek = { viewModel.mediaSeekTo(it) },
            onDismissNotification = { viewModel.dismissNotification(it) },
            onOpenNotificationApp = { pkg ->
                state.installedApps.firstOrNull { it.packageName == pkg }?.let {
                    showNotifications = false
                    viewModel.launchApp(it)
                }
            },
            onDismiss = { showNotifications = false },
            onBrowseSpotify = { showNotifications = false; showSpotify = true },
            dualScreen = state.appearance.useSecondScreen,
            onToggleDualScreen = {
                viewModel.setAppearance(state.appearance.copy(useSecondScreen = !state.appearance.useSecondScreen))
            },
            lightsEnabled = state.appearance.lightsEnabled,
            lightsColor = state.appearance.lightsColor,
            lightsBrightness = state.appearance.lightsBrightness,
            onLightsChange = { enabled, color, brightness ->
                viewModel.setAppearance(
                    state.appearance.copy(lightsEnabled = enabled, lightsColor = color, lightsBrightness = brightness)
                )
            },
            lightsOffWithScreensaver = state.appearance.lightsOffWithScreensaver,
            lightsFlashOnUnlock = state.appearance.flashOnUnlock,
            onLightsAdvancedChange = { offWithSaver, flashOnUnlock ->
                viewModel.setAppearance(
                    state.appearance.copy(lightsOffWithScreensaver = offWithSaver, flashOnUnlock = flashOnUnlock)
                )
            },
            backgroundTasks = listOf(
                "Scraping artwork" to (state.scrapeDone to state.scrapeTotal),
                "Downloading game details" to (state.metadataScrapeDone to state.metadataScrapeTotal),
                "Saving artwork on the device" to (state.artSaveDone to state.artSaveTotal),
                "Matching achievements" to (state.raMatchDone to state.raMatchTotal)
            ),
            onStopTasks = {
                viewModel.cancelScrape()
                viewModel.cancelMetadataScrape()
                viewModel.cancelArtSave()
            },
            sortLabel = uiState.effectiveSortMode.short,
            onSort = { showNotifications = false; showSortDialog = true },
            // R1 › Game look: steps the layout in use through Covers → Real cases → … (GameLookPicker.kt).
            gameLook = currentLookName(state.appearance),
            onCycleGameLook = { viewModel.setAppearance(stepGameLook(state.appearance)) },
            // R1 › layout, carousel, theme, rescan, missing art, the game's power, and "hold a tile for its settings".
            home = run {
                val g = focusedGame
                // Favourite themes, in the themes' own order: the tile steps to the next one (none: the theme list).
                val favs = com.shelf.launcher.data.Features.favoriteThemes.state.value
                val favThemes = com.shelf.launcher.ui.theme.AppTheme.values().filter { it.name in favs }
                R1Home(
                    appearance = state.appearance,
                    setAppearance = { viewModel.setAppearance(it) },
                    themeName = state.appTheme.label,
                    themeKey = state.appTheme.name,
                    themeAction = if (favThemes.isEmpty()) null else {
                        {
                            val i = favThemes.indexOf(state.appTheme)
                            viewModel.setTheme(favThemes[(i + 1).mod(favThemes.size)])
                        }
                    },
                    scanning = state.isScanning,
                    onRescan = { viewModel.rescanEverything() },
                    scrapeDone = state.scrapeDone, scrapeTotal = state.scrapeTotal,
                    onScrapeMissing = { viewModel.scrapeMissingArt(manual = true) },
                    gameKey = g?.stableKey, gameName = g?.let { gameDisplayName(it.name) }, gamePlatform = g?.platform,
                    gameConsole = g?.platform?.let { com.shelf.launcher.data.ConsoleNames.display(it) },
                    consoleSiblings = { g?.let { me -> state.games.filter { it.platform == me.platform && it.stableKey != me.stableKey }.map { it.stableKey } }.orEmpty() },
                    openSettingsAt = { section, label ->
                        // Straight to the setting: its section, sub-page and group (the settings search's own map).
                        val e = label?.let { l -> SettingsIndex.all.firstOrNull { it.label == l } }
                        val sec = e?.section ?: section
                        if (sec != null) prepareSettingsJump(sec, e?.subpage, e?.foldKey, e?.label)
                        showNotifications = false
                        menuStartCategory = sec
                        showMenu = true
                    },
                    badgeNextToTitle = state.achievementBadgeNextToTitle,
                    onToggleBadgePlacement = { viewModel.toggleAchievementBadgePlacement() }
                )
            },
            syncGameKey = r1SyncGame?.stableKey,
            syncGameName = r1SyncGame?.let { gameDisplayName(it.name) },
            onOpenGameSync = r1SyncGame?.let { g -> { showNotifications = false; saveSyncForGame = g } },
            onOpenSyncSettings = { showNotifications = false; menuStartCategory = "Sync"; showMenu = true },
            onDismissAll = { viewModel.dismissAllNotifications() }
        )
    }

    }

    @Composable
    fun MainMenu() {
    when (hubScreen) {
        HubScreen.ACHIEVEMENTS -> AchievementsHub(
            games = state.games, completion = state.raCompletionById, recent = state.raRecentAchievements,
            connected = state.raState == com.shelf.launcher.RaConnectionState.ON,
            onOpenGame = { g -> hubScreen = null; viewingAchievementsForGame = g },
            onRefresh = { viewModel.refreshRaCompletion(); viewModel.loadRecentAchievements(minutesBack = 43_200) },
            onDismiss = { hubScreen = null }
        )
        HubScreen.PROFILE -> ProfileScreen(
            connected = state.raState == com.shelf.launcher.RaConnectionState.ON,
            profile = state.raProfile, stats = state.raProfileStats, completion = state.raCompletionById.values,
            recent = state.raRecentAchievements,
            onOpen = { viewModel.refreshRaCompletion(); viewModel.loadRecentAchievements(minutesBack = 10_080) },
            onOpenGame = { id ->
                // Games that aren't on this device open too: their achievements come straight from
                // RetroAchievements by its game id (a stand-in game with no ROM).
                val g = state.games.firstOrNull { it.raGameId == id } ?: run {
                    val e = state.raCompletionById[id]
                    com.shelf.launcher.data.Game(
                        name = e?.Title ?: "RetroAchievements game $id",
                        platform = e?.ConsoleName ?: "",
                        hue = 0,
                        sourceType = com.shelf.launcher.data.SourceType.ROM,
                        romPath = "ra:$id",
                        raGameId = id
                    )
                }
                hubScreen = null; achievementsShowMissing = true; viewingAchievementsForGame = g
            },
            onOpenRecap = { yearRecap = it },
            onDismiss = { hubScreen = null }
        )
        HubScreen.STATS -> StatsScreen(
            games = state.games, playtime = state.playtime, completion = state.raCompletionById.values,
            appearance = state.appearance, onAppearanceChange = { viewModel.setAppearance(it) },
            statuses = state.playStatuses, onOpenRecap = { yearRecap = it },
            onDismiss = { hubScreen = null }
        )
        null -> Unit
    }
    if (showMenu) {
        SettingsDialog(
            currentTheme = state.appTheme,
            badgeMode = state.achievementBadgeMode,
            badgeNextToTitle = state.achievementBadgeNextToTitle,
            steamGridDbState = state.artState,
            raState = state.raState,
            raUsername = state.raUsername,
            igdbConnected = state.igdbConnected,
            tgdbConnected = state.tgdbConnected,
            screenScraperConnected = state.screenScraperConnected,
            artSource = state.artSource,
            metadataSource = state.metadataSource,
            scrapeDone = state.scrapeDone,
            scrapeTotal = state.scrapeTotal,
            isMatchingAchievements = state.isMatchingAchievements,
            onSelectTheme = { viewModel.setTheme(it) },
            onSetBadgeMode = { viewModel.setAchievementBadgeMode(it) },
            onToggleBadgePlacement = { viewModel.toggleAchievementBadgePlacement() },
            screensaver = state.screensaver,
            onScreensaverChange = { viewModel.setScreensaver(it) },
            onPreviewScreensaver = { showMenu = false; screensaverOn = true },
            artStorage = state.artStorage,
            artStorageUsedBytes = state.artStorageUsedBytes,
            artStorageMissing = state.artStorageMissing,
            artSaveDone = state.artSaveDone,
            artSaveTotal = state.artSaveTotal,
            onSaveAllArt = { viewModel.saveAllArt() },
            onCancelArtSave = { viewModel.cancelArtSave() },
            onClearSavedArt = { viewModel.clearSavedArt() },
            onArtStorageChange = { viewModel.setArtStorage(it) },
            onClearAchievementData = { viewModel.clearSavedAchievementData() },
            appearance = state.appearance,
            onAppearanceChange = { viewModel.setAppearance(it) },
            libraryApps = uiState.libraryApps,
            onOpenAppsLibrary = { showMenu = false; showAppsLibrary = true },
            onAppsAlwaysUseIcons = { viewModel.setAppsAlwaysUseIcons(it) },
            categories = uiState.categories,
            // (Update 214: a move here (the groups' order) starts again from Settings › Categories' order — the tiles' own
            // order from Choose a category › Reorder would otherwise hide it.)
            onMoveCategory = { key, delta -> com.shelf.launcher.data.CategoryTileOrder.reset(); viewModel.moveCategory(key, delta) },
            onToggleCategoryHidden = { viewModel.toggleCategoryHidden(it) },
            onResetCategories = { com.shelf.launcher.data.CategoryTileOrder.reset(); viewModel.resetCategoryOrder() },
            onSectionChange = { viewModel.rememberSettingsSection(it) },
            hasSecondScreen = secondScreenActive,
            screenLayoutsVersion = state.screenLayoutsVersion,
            onScreenLayoutsChanged = { viewModel.bumpScreenLayouts() },
            raProfile = state.raProfile,
            raProfileStats = state.raProfileStats,
            raCompletion = state.raCompletionById.values,
            raRecent = state.raRecentAchievements,
            onOpenProfile = {
                viewModel.refreshRaCompletion()
                viewModel.loadRecentAchievements(minutesBack = 10_080)
            },
            initialCategory = menuStartCategory ?: uiState.categories.lastSettingsSection,
            onOpenRomLibrary = { showMenu = false; showLibraryDialog = true },
            onOpenSteamGames = { showMenu = false; showAddSteamGameDialog = true },
            onOpenShortcuts = { showMenu = false; showShortcuts = true },
            onOpenSteamGridDb = { showMenu = false; showSettings = true },
            onOpenRetroAchievements = { showMenu = false; showAchievementsDialog = true },
            onOpenIgdb = { showMenu = false; showIgdb = true },
            rawgConnected = state.rawgConnected,
            onOpenRawg = { showMenu = false; showRawg = true },
            games = state.games,
            playtime = state.playtime,
            userCollections = state.userCollections,
            onRenameCollection = { old, new -> viewModel.renameCollection(old, new) },
            onDeleteCollection = { viewModel.deleteCollection(it) },
            playStatuses = state.playStatuses,
            libraryPlatforms = state.availablePlatforms.filter {
                it != "App" && it != "Steam" && it != com.shelf.launcher.ANDROID_GAMES_PLATFORM
            },
            romRoot = state.romRootPath,
            onOpenTgdb = { showMenu = false; showTgdb = true },
            onOpenScreenScraper = { showMenu = false; showScreenScraper = true },
            onSelectMetadataSource = { viewModel.setMetadataSource(it) },
            onSelectArtSource = { viewModel.setArtSource(it) },
            onOpenEmulators = { showMenu = false; showEmulatorSettings = true },
            onScrapeMissing = { viewModel.scrapeMissingArt(manual = true) },
            onRescrapeAll = { viewModel.scrapeMissingArt(force = true, manual = true) },
            onCancelScrape = { viewModel.cancelScrape() },
            onAutoMatchAchievements = { viewModel.autoMatchAchievements() },
            onRehashAchievements = { viewModel.autoMatchAchievements(force = true) },
            metadataScrapeDone = state.metadataScrapeDone,
            metadataScrapeTotal = state.metadataScrapeTotal,
            savedDetailsCount = state.metadata.size,
            onScrapeMetadata = { viewModel.scrapeMissingMetadata() },
            onCancelMetadataScrape = { viewModel.cancelMetadataScrape() },
            onClearMetadata = { viewModel.clearSavedMetadata() },
            onRefreshAllMetadata = { viewModel.scrapeMissingMetadata(refreshAll = true) },
            onRunSetup = { showMenu = false; showFirstRun = true },
            onOpenRaGame = { id ->
                val g = state.games.firstOrNull { it.raGameId == id }
                if (g != null) { showMenu = false; achievementsShowMissing = true; viewingAchievementsForGame = g }
                else viewModel.setStatusMessage("That game isn't in your library on this device.")
            },
            onOpenYearRecap = { yearRecap = it },
            scrapeOptions = state.scrapeOptions,
            onScrapeOptionsChange = { viewModel.setScrapeOptions(it) },
            onDismiss = { showMenu = false }
        )
    }


    }

    @Composable
    fun SettingsDialogs() {
    if (showAppsLibrary) {
        AppsLibraryDialog(
            installedApps = uiState.installedApps,
            settings = uiState.libraryApps,
            gameApps = uiState.gameAppPackages,
            knownEmulators = uiState.knownEmulatorPackages,
            onModeChange = { viewModel.setAppsMode(it) },
            onToggleInLibrary = { viewModel.toggleAppInLibrary(it) },
            onToggleEmulator = { viewModel.toggleAppEmulator(it) },
            onSetAppKind = { pkg, kind -> viewModel.setAppKind(pkg, kind) },
            onDismiss = { showAppsLibrary = false; showMenu = true }
        )
    }

    val shortcutsContext = androidx.compose.ui.platform.LocalContext.current
    if (showShortcuts) {
        ShortcutsPanel(
            shortcuts = state.shortcuts,
            installedApps = state.installedApps,
            onLaunch = { app ->
                showShortcuts = false
                val id = app.packageName
                if (com.shelf.launcher.data.BuiltinShortcuts.isBuiltin(id)) {
                    // Voltcap's own screens pinned to L1.
                    when (id) {
                        "voltcap:achievements" -> {
                            val g = focusedGame
                            if (g?.raGameId != null) viewingAchievementsForGame = g else hubScreen = HubScreen.ACHIEVEMENTS
                        }
                        "voltcap:stats" -> hubScreen = HubScreen.STATS
                        "voltcap:profile" -> hubScreen = HubScreen.PROFILE
                        "voltcap:spotify" -> MusicApps.playPause(shortcutsContext, state.nowPlaying?.isPlaying == true)
                        "voltcap:music" -> showSpotify = true
                        "voltcap:saves" -> showSaveExplorer = true
                        "voltcap:rediscover" -> viewModel.setSpecialFilter(com.shelf.launcher.SpecialFilter.REDISCOVER)
                        "voltcap:explore" -> {
                            com.shelf.launcher.data.Features.exploreMode.value = !com.shelf.launcher.data.Features.exploreMode.value
                            viewModel.setStatusMessage(
                                if (com.shelf.launcher.data.Features.exploreMode.value) "Explore: the whole collection, shown equally (no highlights)"
                                else "Explore off: Living Library highlights are back"
                            )
                        }
                        "voltcap:recap" -> yearRecap = com.shelf.launcher.data.YearRecap.calendarYear()
                        "voltcap:whattoplay" -> showWhatToPlay = true
                        "voltcap:categories" -> openCategoryScreen()
                        "voltcap:screensaver" -> screensaverOn = true
                        else -> if (R1Shortcut.tileOf(id) != null) {
                            // An R1 tile pinned to L1: the R1 panel opens and presses it.
                            R1Shortcut.pending = R1Shortcut.tileOf(id)
                            viewModel.refreshNotifications()
                            notificationsOnly = false; showNotifications = true
                        } else { menuStartCategory = null; showMenu = true }
                    }
                } else viewModel.launchShortcut(app)
            },
            onAdd = { viewModel.addShortcut(it) },
            onRemove = { viewModel.removeShortcut(it) },
            onReorder = { viewModel.reorderShortcuts(it) },
            onDismiss = { showShortcuts = false }
        )
    }

    if (showScreenScraper) {
        ScreenScraperDialog(
            connected = state.screenScraperConnected,
            username = state.screenScraperUsername,
            statusMessage = state.statusMessage,
            onConnect = { devId, devPassword, user, password ->
                viewModel.connectScreenScraper(devId, devPassword, user, password)
            },
            onDisconnect = { viewModel.disconnectScreenScraper() },
            onDismiss = { showScreenScraper = false; showMenu = true }
        )
    }

    if (showTgdb) {
        TgdbDialog(
            connected = state.tgdbConnected,
            statusMessage = state.statusMessage,
            onConnect = { viewModel.connectTgdb(it) },
            onDisconnect = { viewModel.disconnectTgdb() },
            onDismiss = { showTgdb = false; showMenu = true }
        )
    }

    if (showIgdb) {
        IgdbDialog(
            connected = state.igdbConnected,
            statusMessage = state.statusMessage,
            onConnect = { id, secret -> viewModel.connectIgdb(id, secret) },
            onDisconnect = { viewModel.disconnectIgdb() },
            onDismiss = { showIgdb = false; showMenu = true }
        )
    }

    if (showRawg) {
        RawgDialog(
            connected = state.rawgConnected,
            statusMessage = state.statusMessage,
            onConnect = { viewModel.connectRawg(it) },
            onDisconnect = { viewModel.disconnectRawg() },
            onDismiss = { showRawg = false; showMenu = true }
        )
    }

    if (showEmulatorSettings) {
        EmulatorSettingsDialog(
            platforms = state.availablePlatforms.filter {
                it != "App" && it != "Steam" && it != com.shelf.launcher.ANDROID_GAMES_PLATFORM
            },
            configs = state.emulatorConfigs,
            installedApps = state.installedApps,
            onEditPlatform = { configuringPlatform = it },
            onDismiss = { showEmulatorSettings = false; showMenu = true }
        )
    }

    // (Update 256, #102: a game that couldn't start for want of an emulator opens the picker for its console.)
    val needEmulator by viewModel.needEmulatorFor.collectAsState()
    // (Update 258, RP Classic: the game waiting for that emulator — it starts as soon as one is picked; you had to find
    // it and press Play again.)
    var startAfterPick by remember { mutableStateOf<com.shelf.launcher.data.Game?>(null) }
    LaunchedEffect(needEmulator) {
        needEmulator?.let { configuringPlatform = it.platform; startAfterPick = it; viewModel.needEmulatorFor.value = null }
    }
    configuringPlatform?.let { platform ->
        EmulatorPickerDialog(
            platform = platform,
            installedApps = state.installedApps,
            existing = state.emulatorConfigs[platform],
            onSave = { config ->
                viewModel.setEmulatorConfig(config)
                configuringPlatform = null
                val g = startAfterPick?.takeIf { it.platform == config.platform }
                startAfterPick = null
                if (g != null) startGame(g)
            },
            onDismiss = { configuringPlatform = null; startAfterPick = null }
        )
    }

    MainMenu()

    if (showAchievementsDialog) {
        LaunchedEffect(state.raState) {
            if (state.raState == RaConnectionState.ON) viewModel.loadRecentAchievements()
        }
        RetroAchievementsDialog(
            state = state.raState,
            username = state.raUsername,
            statusMessage = state.statusMessage,
            isKeyEncrypted = state.isRaKeyEncrypted,
            recentAchievements = state.raRecentAchievements,
            isRecentLoading = state.isRaRecentLoading,
            onConnect = { username, apiKey -> viewModel.connectRa(username, apiKey) },
            onDisconnect = { viewModel.disconnectRa() },
            onRefreshRecent = { viewModel.loadRecentAchievements() },
            onDismiss = { showAchievementsDialog = false; showMenu = true },
            accounts = state.raAccounts,
            activeSlot = state.raActiveSlot,
            onSwitchAccount = { viewModel.switchRaAccount(it) },
            onAddAccount = { u, k -> viewModel.addRaAccount(u, k) },
            onRemoveAccount = { viewModel.removeRaAccount(it) }
        )
    }

    yearRecap?.let { period ->
        val recapForeignArt by viewModel.vaultArt.collectAsState()
        YearRecapDialog(
            period = period,
            games = state.games,
            coverForLib = { k -> state.artOverrides[k]?.cover ?: state.art[k]?.gridUrl },
            // Update 117: games from your other devices that aren't here, their covers looked up by name.
            foreignCover = { k -> recapForeignArt[k] },
            onNeedForeignCovers = { gs -> viewModel.loadVaultArt(gs) },
            heroFor = { k -> state.artOverrides[k]?.hero ?: state.art[k]?.heroUrl },
            completion = state.raCompletionById,
            onDismiss = { yearRecap = null }
        )
    }
    viewingAchievementsForGame?.let { game ->
        LaunchedEffect(game.stableKey) { viewModel.loadAchievementDetails(game); viewModel.clearReturnTrophies(game.stableKey) }
        // Update 127: on the Cross menu, the menu's own achievement column (CrossTrophies.kt); A there opens this list,
        // and B in the list comes back to the column.
        val crossColumn = crossTrophiesOn(state.appearance.homeLayout)
        if (crossColumn && !CrossTrophyView.full) CrossTrophies(
            game, state, visibleGames, displayArt,
            loading = game.stableKey in state.raAchievementDetailsLoading, startLocked = achievementsShowMissing,
            onFull = { CrossTrophyView.full = true },
            onDismiss = { viewingAchievementsForGame = null; achievementsShowMissing = false },
            onLoadComments = { viewModel.loadAchievementComments(it) }
        ) else AchievementListDialog(
            game = game,
            achievements = state.raAchievementDetails[game.stableKey] ?: emptyList(),
            isLoading = game.stableKey in state.raAchievementDetailsLoading,
            onDismiss = { if (crossColumn && CrossTrophyView.full) CrossTrophyView.full = false else { CrossTrophyView.full = false; viewingAchievementsForGame = null; achievementsShowMissing = false } },
            onOpenHub = { CrossTrophyView.full = false; viewingAchievementsForGame = null; achievementsShowMissing = false; hubScreen = HubScreen.ACHIEVEMENTS },
            gameIcon = game.raGameId?.let { id -> state.raIcons[id] ?: com.shelf.launcher.data.RaBadges.mediaUrl(state.raCompletionById[id]?.ImageIcon) },
            startWithMissing = achievementsShowMissing,
            comments = state.raComments,
            commentsLoading = state.raCommentsLoading,
            onLoadComments = { viewModel.loadAchievementComments(it) },
            refreshNote = raDetailsNote(state, game.stableKey),
            missedMarks = state.raMissedMarks,
            onMarkMissed = { id, missed -> viewModel.setAchievementMissed(id, missed) },
            commentsHasMore = state.raCommentsHasMore,
            onLoadMoreComments = { viewModel.loadMoreAchievementComments(it) },
            commentsFailed = state.raCommentsError.keys,
            richPresence = state.raProfile?.takeIf { it.LastGameID != null && it.LastGameID == game.raGameId }?.RichPresenceMsg,
            showRarity = state.appearance.showRarity,
            matchInfo = com.shelf.launcher.data.ra.raMatchInfo(game.raGameId, state.raMatches[game.stableKey]),
            onFixLink = { configuringRaForGame = game },
            onRetry = { viewModel.retryAchievementDetails(game) }
        )
    }

    pickingMissingSourceFor?.let { game ->
        val sources = viewModel.missingSourcesFor(game, uiState)
        val still = viewModel.missingFor(game, uiState, ignoreTried = true)
        ShelfMenuDialog(
            title = "Try another source",
            subtitle = if (still.isEmpty()) "Nothing is missing any more." else "Still missing: " + still.joinToString(", ") { it.label },
            onDismiss = { pickingMissingSourceFor = null },
            icon = Icons.Filled.Refresh
        ) {
            sources.forEach { source ->
                MenuRow(Icons.Filled.Refresh, source.label, "Look only there") {
                    viewModel.fillMissingFrom(game, source)
                    pickingMissingSourceFor = null
                }
            }
            if (sources.isEmpty()) EmptyState("No other connected source can fill this in.")
        }
    }

    }

    @Composable
    fun SyncAndSetupDialogs() {
    // Google Drive sync: only asks when there's something to decide — which
    // saves to keep, or whether to play without having checked Drive.
    uiState.syncOfflinePrompt?.let { prompt ->
        SyncOfflineDialog(
            gameName = gameDisplayName(prompt.game.name),
            reason = prompt.reason,
            onPlay = { viewModel.playWithoutSync() },
            onRetry = { viewModel.retrySyncAndPlay() },
            onCancel = { viewModel.dismissSyncOfflinePrompt() }
        )
    }
    saveSyncForGame?.let { game ->
        // Device / folder info from the sync records (reads files: off the main thread).
        val detailLine by produceState<String?>(null, game.stableKey, syncLastAt) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { com.shelf.launcher.data.CloudSync.saveStatusFor(firstRunContext, game) }.getOrNull()
            }
        }
        SaveSyncDialog(
            gameKey = game.stableKey,
            gameName = gameDisplayName(game.name),
            detailLine = detailLine,
            onSyncNow = { viewModel.syncGameNow(game) },
            onResolveConflict = {
                com.shelf.launcher.data.CloudSync.unsnoozeConflicts()
                saveSyncForGame = null
            },
            onOpenSettings = { saveSyncForGame = null; GamePopup.game = null; menuStartCategory = "Sync"; showMenu = true },
            onDismiss = { saveSyncForGame = null }
        )
    }
    val gameWaitingOnSaves = uiState.gameWaitingOnConflicts
    if (syncConflictShowing) {
        SyncConflictDialog(
            conflicts = cloudSync.conflicts,
            gameName = gameWaitingOnSaves?.let { gameDisplayName(it.name) },
            onChoice = { viewModel.resolveSyncConflicts(it) },
            onLater = { viewModel.postponeSyncConflicts() }
        )
    }

    if (showAppDrawer) {
        val hiddenPkgs = remember(state.hiddenApps) { state.hiddenApps.mapNotNull { it.packageName }.toSet() }
        AppDrawer(
            apps = remember(state.installedApps, hiddenPkgs) { state.installedApps.filterNot { it.packageName in hiddenPkgs } },
            onLaunch = { app ->
                showAppDrawer = false
                viewModel.launchShortcut(app)
            },
            onDismiss = { showAppDrawer = false },
            onHide = { viewModel.hideApp(it) },
            isGame = { pkg ->
                val lib = state.libraryApps
                when (lib.kindOf(pkg)) {
                    com.shelf.launcher.data.AppKind.GAME -> true
                    com.shelf.launcher.data.AppKind.APP -> false
                    com.shelf.launcher.data.AppKind.AUTO -> pkg in state.gameAppPackages
                }
            },
            onSetKind = { pkg, kind -> viewModel.setAppKind(pkg, kind) }
        )
    }

    // After the first-start setup: once, if something useful isn't allowed yet (e.g. new after an update).
    var showPermissionsPrompt by remember {
        mutableStateOf(runCatching { PermissionsPrompt.shouldShow(firstRunContext) }.getOrDefault(false))
    }
    // A moment after start, so it never pops up over the intro's last frames.
    var promptReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(2_500); promptReady = true }
    // Update 149: "What's new" once after an update (its GitHub release notes).
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(4_000); WhatsNew.check(firstRunContext) }
    WhatsNew.shown.value?.let { (v, notes) ->
        if (!showFirstRun) WhatsNewDialog(v, notes) { WhatsNew.shown.value = null }
    }
    // Update 187: once, a short note about the battery saver (first start after the setup, or after this update), when
    // nothing else is open.
    var saverIntro by remember { mutableStateOf(runCatching { com.shelf.launcher.data.BatterySaver.introDue(firstRunContext) }.getOrDefault(false)) }
    var saverIntroReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(7_000); saverIntroReady = true }
    // (Update 258, first start on a fresh install: it opened on top of "Download extra art?" — one at a time now.)
    if (saverIntro && saverIntroReady && !otherDialogOpen && !packsAskOpen && WhatsNew.shown.value == null && !(showPermissionsPrompt && promptReady)) {
        val done = {
            com.shelf.launcher.data.BatterySaver.introSeen(firstRunContext)
            saverIntro = false
        }
        BatterySaverIntroDialog(
            onSettings = {
                done()
                prepareSettingsJump("Device", null, "dev_saver", "Battery saver")
                menuStartCategory = "Device"
                showMenu = true
            },
            onDismiss = done
        )
    }
    // Update 274 (Toni): once after this update — Inspect or Wobble for the pieces in the game window (InspectChoice.kt),
    // on the bare home screen with nothing else asking.
    var inspectAsk by remember { mutableStateOf(InspectChoice.due()) }
    var inspectAskReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(9_000); inspectAskReady = true }
    // (Not during an adb test walk — TestHooks: it stood over every screenshot.)
    if (inspectAsk && inspectAskReady && !TestHooks.previewOnly && TestHooks.shell.value == null && !showFirstRun && !otherDialogOpen && !packsAskOpen && WhatsNew.shown.value == null &&
        !(saverIntro && saverIntroReady) && !(showPermissionsPrompt && promptReady) && GamePopup.game == null &&
        launchingGame == null && !screensaverOn && !HomeEditState.active && !categoryScreenOpen && !showDetails
    ) {
        InspectChoiceDialog { inspectAsk = false }
    }
    if (showPermissionsPrompt && promptReady && !showFirstRun) {
        PermissionsPromptDialog(onDismiss = {
            PermissionsPrompt.markAsked(firstRunContext)
            showPermissionsPrompt = false
        })
    }
    // Update 202: the automatic update check and its "Voltcap x is out" question (UpdatePrompt.kt). Asked only on the
    // bare home screen: never over another popup or question, in the first-time setup, while a game starts, over the
    // details / category screen, the screensaver or Edit Home — it waits until all of that is gone.
    UpdatePromptHost(
        canShow = !aDialogIsOpen && !showFirstRun && !whoAsk && launchingGame == null && LaunchThrow.plan == null &&
            introVideo == null && cartShellForGame == null && !showDetails && !categoryScreenOpen && !screensaverOn &&
            !showSearchBar && !HomeEditState.active && GamePopup.game == null && WhatsNew.shown.value == null &&
            !(saverIntro && saverIntroReady) && !(showPermissionsPrompt && promptReady) &&
            (com.shelf.launcher.data.Packs.asked.value || state.games.isEmpty())
    )
    if (showFirstRun) {
        FirstRunDialog(
            status = SetupStatus(
                gameCount = state.games.count { it.sourceType != com.shelf.launcher.data.SourceType.APP },
                artConnected = state.artState == com.shelf.launcher.ArtConnectionState.ON,
                raConnected = state.raState == com.shelf.launcher.RaConnectionState.ON,
                detailsConnected = state.igdbConnected,
                rawgConnected = state.rawgConnected,
                screenScraperConnected = state.screenScraperConnected
            ),
            onRomFolders = { showLibraryDialog = true },
            onSteamGames = { showAddSteamGameDialog = true },
            onArtwork = { showSettings = true },
            onRetroAchievements = { showAchievementsDialog = true },
            onIgdb = { showIgdb = true },
            onRawg = { showRawg = true },
            onScreenScraper = { showScreenScraper = true },
            showLegend = state.appearance.showLegend,
            onShowLegendChange = { viewModel.setAppearance(state.appearance.copy(showLegend = it)) },
            onFinish = {
                com.shelf.launcher.data.FirstRun.markDone(firstRunContext)
                showFirstRun = false
            }
        )
    }

    if (showLibraryDialog) {
        LibraryDialog(
            romRootPaths = state.romRootPaths,
            deepSearch = state.deepSearch,
            isScanning = state.isScanning,
            statusMessage = state.statusMessage,
            hasStoragePermission = viewModel::hasStoragePermission,
            hiddenApps = state.hiddenApps,
            onScan = { paths, deep -> viewModel.scanLibraryFolders(paths, deep) },
            onUnhideApp = { packageName -> viewModel.unhideApp(packageName) },
            onMakeM3u = { viewModel.makeMissingM3u() },
            onDismiss = { showLibraryDialog = false; showMenu = true }
        )
    }

    if (showAddSteamGameDialog) {
        AddSteamGameDialog(
            existingSteamGames = state.games.filter { it.sourceType == com.shelf.launcher.data.SourceType.STEAM },
            gameNativeConfig = state.gameNativeConfig,
            onAdd = { name, appId -> viewModel.addSteamGame(name, appId) },
            onRemove = { appId -> viewModel.removeSteamGame(appId) },
            onImport = { list -> viewModel.importSteamGames(list) },
            onSaveGameNativeConfig = { config -> viewModel.setGameNativeConfig(config) },
            onDismiss = { showAddSteamGameDialog = false }
        )
    }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(ShelfTheme.background)
            .focusRequester(rootFocusRequester)
            .focusable()
            // Update 212: while editing, a touch that isn't on a part or Edit Home's bars goes nowhere (HomeEditSession.kt).
            .homeEditShield()
            .onPreviewKeyEvent { event ->
                lastInputAt = System.currentTimeMillis()
                when {
                    // Edit Home: the controller moves widgets until A / B finishes.
                    // Volume / media keys stay with the system while editing (update 91).
                    HomeEditState.active && isSystemKey(event.nativeKeyEvent.keyCode) -> false
                    HomeEditState.active -> HomeEditState.onKey(event) { id ->
                        if (id == HomeWidgets.SHORTCUTS) defaultFractions(
                            if (state.appearance.dockPosition == com.shelf.launcher.data.DockPosition.BOTTOM) Alignment.TopCenter else Alignment.BottomCenter
                        ) else defaultFractions(
                            when (state.appearance.musicPlayerPosition) {
                                com.shelf.launcher.data.MusicPlayerPosition.TOP_LEFT -> Alignment.TopStart
                                com.shelf.launcher.data.MusicPlayerPosition.TOP_RIGHT -> Alignment.TopEnd
                                com.shelf.launcher.data.MusicPlayerPosition.BOTTOM_LEFT -> Alignment.BottomStart
                                com.shelf.launcher.data.MusicPlayerPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
                            }
                        )
                    }
                    screensaverOn -> {
                        if (event.type == KeyEventType.KeyDown) {
                            screensaverOn = false
                            swallowKeyUp = true
                        }
                        true
                    }
                    swallowKeyUp && event.type == KeyEventType.KeyUp -> {
                        swallowKeyUp = false
                        true
                    }
                    else -> false
                }
            }
            .pointerInput(Unit) {
                // Swipe up from the bottom edge: all apps. Only watches — taps
                // and the row's own swipes carry on as before.
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent(PointerEventPass.Initial).changes
                            .firstOrNull { it.pressed && !it.previousPressed } ?: continue
                        // (Update 224, #12: a band just above Android's gesture area — or the bottom 56 dp without one.)
                        val edge = size.height - gestureBottomPx
                        if (down.position.y < edge - (if (gestureBottomPx > 0) 80.dp.toPx() else 56.dp.toPx())) continue
                        val startY = down.position.y
                        val startX = down.position.x
                        while (true) {
                            val change = awaitPointerEvent(PointerEventPass.Initial).changes
                                .firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val up = startY - change.position.y
                            // Mostly upwards (a sideways swipe through the row isn't one).
                            if (up > 90.dp.toPx() && kotlin.math.abs(change.position.x - startX) < up) {
                                if (drawerAllowed) showAppDrawer = true
                                break
                            }
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                // Watch touches without taking them, just to reset the idle timer.
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        lastInputAt = System.currentTimeMillis()
                    }
                }
            }
            .onKeyEvent { event ->
                // Update 177: the controller on the second screen (▼ past the bottom of this one), SecondPad.kt.
                if (SecondPad.active) {
                    if (aDialogIsOpen) SecondPad.leave()
                    else if (SecondPad.key(event) { sounds?.play(it) }) return@onKeyEvent true
                }
                // Update 179: ▼ that changes nothing on this screen — the bottom of any layout, whatever the layout does
                // with ▼ — moves the controller onto the second screen (some layouts never asked for what lies below).
                if (!SecondPad.active && SecondScreenShown.value && !aDialogIsOpen && !HomeEditState.active &&
                    event.key == Key.DirectionDown && event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0
                ) {
                    fun padSnapshot(): List<Any?> {
                        val st = viewModel.uiState.value
                        return listOf(
                            st.focusedIndex, st.platformFilter, st.specialFilter, StyleNav.zone, StyleNav.index,
                            HomeReach.zone, HomeReach.index, tileBarFocus, miniFocus, quickJump, showDetails,
                            categoryScreenOpen, GamePopup.game?.stableKey, PocketNav.signature(), GreenCoreNav.signature(),
                            PqNav.signature(), // (Update 202: Pocket Quest's own places)
                            CubeNav.signature(), // (Update 239, #93: the Cube menu's page and faces)
                            // (Update 260, Thor: Choose a category's tile and the game window's row and scroll were missing
                            // here, so a ▼ that moved down the category grid or scrolled the game window still sent the
                            // controller to the second screen 160 ms later — it skipped what was left to scroll.)
                            categoryIndex, detailsAction, detailsScroll.value, detailsScroll.isScrollInProgress
                        )
                    }
                    val before = padSnapshot()
                    detailsScope.launch {
                        kotlinx.coroutines.delay(160)
                        if (!SecondPad.active && !HomeEditState.active && padSnapshot() == before && SecondPad.enter()) sounds?.play(UiSound.MOVE)
                    }
                }
                // Select is a MODIFIER. Music buttons and hotkeys come first, on
                // the home screen and details window. While Select is held,
                // EVERY other key press (bound or not) belongs to the combo:
                // a bound one runs its action, an unbound one does nothing,
                // and Select's own release then does not open the menu.
                // Update 200: a stick click while a piece is out in the game window is for the piece (open its case a
                // step, or turn it), even when the window's own controls haven't got the focus yet.
                run {
                    val code = event.nativeKeyEvent.keyCode
                    val hook = MediaInspect.stickClick
                    if (hook != null && (code == android.view.KeyEvent.KEYCODE_BUTTON_THUMBL || code == android.view.KeyEvent.KEYCODE_BUTTON_THUMBR)) {
                        if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) hook()
                        return@onKeyEvent true
                    }
                }
                if (!aDialogIsOpen && quickJump == null) {
                    val code = event.nativeKeyEvent.keyCode
                    if (code == android.view.KeyEvent.KEYCODE_BUTTON_SELECT) {
                        if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                            selectHeld = true
                            selectComboUsed = false
                        } else if (event.type == KeyEventType.KeyUp) {
                            selectHeld = false
                            if (selectComboUsed) { selectComboUsed = false; return@onKeyEvent true }
                        }
                    } else if (!isSystemKey(code)) {
                        // The key-up (and any auto-repeat) of a press that
                        // began as a combo / music press is swallowed, even if
                        // Select was let go first — otherwise A's key-up would
                        // still open / play the game.
                        if (event.type == KeyEventType.KeyUp && musicKeysDown.remove(code)) return@onKeyEvent true
                        if (event.type == KeyEventType.KeyDown && code in musicKeysDown) return@onKeyEvent true
                        // (Auto-repeats of a key that went down BEFORE Select are
                        // not combos: repeatCount > 0 falls through as normal.)
                        if (event.type == KeyEventType.KeyDown && selectHeld && event.nativeKeyEvent.repeatCount == 0) {
                            selectComboUsed = true
                            musicKeysDown += code
                            val comboMusic = state.appearance.musicButtons.actionFor(code, true)
                            val comboHotkey = state.appearance.hotkeys.hotkeyFor(code, true)
                            if (comboMusic != null) runMusic(comboMusic)
                            else if (comboHotkey != null) runHotkey(comboHotkey)
                            // else: an unbound combo does nothing.
                            return@onKeyEvent true
                        }
                        val action = state.appearance.musicButtons.actionFor(code, false)
                        if (action != null && event.type == KeyEventType.KeyDown) {
                            if (event.nativeKeyEvent.repeatCount == 0) {
                                musicKeysDown += code
                                runMusic(action)
                            }
                            return@onKeyEvent true
                        }
                        val hotkey = state.appearance.hotkeys.hotkeyFor(code, false)
                        if (hotkey != null && event.type == KeyEventType.KeyDown) {
                            if (event.nativeKeyEvent.repeatCount == 0) {
                                musicKeysDown += code
                                runHotkey(hotkey)
                            }
                            return@onKeyEvent true
                        }
                    }
                }

                // Launch animation running: it'll open the game in a moment.
                if (launchingGame != null) return@onKeyEvent true
                // Update 151: the same while a game is thrown in.
                if (LaunchThrow.plan != null) return@onKeyEvent true
                // Platform intro video: any button skips it (and starts the game).
                introVideo?.let { (game, _) ->
                    if (event.type == KeyEventType.KeyDown && !isSystemKey(event.nativeKeyEvent.keyCode)) {
                        introVideo = null
                        viewModel.launch(game)
                    }
                    return@onKeyEvent !isSystemKey(event.nativeKeyEvent.keyCode)
                }

                // Controls card (R3): any button closes it.
                if (showControlsHelp) {
                    if (event.type == KeyEventType.KeyUp && event.nativeKeyEvent.downTime >= controlsHelpOpenedAt[0]) {
                        showControlsHelp = false
                        sounds?.play(UiSound.BACK)
                    }
                    return@onKeyEvent true
                }

                // Category screen: it owns the controller while it's open.
                if (categoryScreenOpen && !aDialogIsOpen) {
                    // Volume / media / power keys are not ours: the system gets them.
                    if (isSystemKey(event.nativeKeyEvent.keyCode)) return@onKeyEvent false
                    // The platform browser (Slides / Wall / Grid) has its own keys; the classic tiles keep these.
                    val browserView = com.shelf.launcher.data.Features.platformView.value
                    if (browserView != com.shelf.launcher.data.Features.PlatformView.TILES) {
                        return@onKeyEvent platformBrowserKey(
                            event, pbState, browserView, categoryTiles, categoryIndex,
                            setIndex = { categoryIndex = it },
                            onPick = { pickCategory(categoryIndex) },
                            onClose = { categoryScreenOpen = false; sounds?.play(UiSound.BACK) },
                            onRandom = { randomFromBrowser() },
                            onReorder = { from, to -> reorderPlatforms(from, to) },
                            onMoveSound = { sounds?.play(UiSound.MOVE) },
                            onBackSound = { sounds?.play(UiSound.BACK) }
                        )
                    }
                    val count = categoryTiles.size
                    if (event.type == KeyEventType.KeyDown) {
                        val step = when (event.key) {
                            Key.DirectionLeft -> -1
                            Key.DirectionRight -> 1
                            Key.DirectionUp -> -categoryColumns(state.appearance.categoryTileStyle)
                            Key.DirectionDown -> categoryColumns(state.appearance.categoryTileStyle)
                            else -> 0
                        }
                        // Update 208: reordering with a tile picked up — the D-pad moves it.
                        if (step != 0 && count > 0 && CategoryReorder.on && CategoryReorder.held) {
                            val to = (categoryIndex + step).coerceIn(0, count - 1)
                            if (to != categoryIndex && moveCategoryTile(categoryIndex, to)) sounds?.play(UiSound.MOVE)
                            return@onKeyEvent true
                        }
                        if (step != 0 && count > 0) {
                            // ◀ ▶ wrap round at the ends; ▲ ▼ stop at the top / bottom row.
                            val next = if (step == 1 || step == -1) (categoryIndex + step).mod(count) else (categoryIndex + step).coerceIn(0, count - 1)
                            if (next != categoryIndex) { categoryIndex = next; sounds?.play(UiSound.MOVE) }
                        }
                        return@onKeyEvent true
                    }
                    // Update 208: Start — reorder on / done; while it's on, A picks up / puts down and B finishes.
                    if (event.key == Key.ButtonStart) {
                        if (CategoryReorder.on) CategoryReorder.stop() else CategoryReorder.on = true
                        sounds?.play(UiSound.MOVE)
                        return@onKeyEvent true
                    }
                    if (CategoryReorder.on) {
                        when (event.key) {
                            Key.ButtonA, Key.Enter, Key.DirectionCenter -> { CategoryReorder.held = !CategoryReorder.held; sounds?.play(UiSound.SELECT) }
                            // (Update 214: B with a tile picked up cancels the move — the order goes back to how it was.)
                            Key.ButtonB, Key.Back -> { if (CategoryReorder.held) cancelCategoryMove() else CategoryReorder.stop(); sounds?.play(UiSound.BACK) }
                            else -> Unit
                        }
                        return@onKeyEvent true
                    }
                    when (event.key) {
                        Key.ButtonA, Key.Enter, Key.DirectionCenter -> pickCategory(categoryIndex)
                        Key.ButtonB, Key.Back -> { categoryScreenOpen = false; sounds?.play(UiSound.BACK) }
                        // Y: the tile's options — Change picture, Reset picture, Reorder (update 212: the picture used to be
                        // straight on Y and X; the same as the platform browser's Y now).
                        Key.ButtonY -> categoryTiles.getOrNull(categoryIndex)?.let { tile -> pbState.options = tile; sounds?.play(UiSound.OPEN) }
                        else -> Unit
                    }
                    return@onKeyEvent true
                }

                // Update 101: what sits beside the games (the game header's buttons, the round shortcuts in a corner ☰ or
                // floating, the corner music player) is reached past the games' edge, on every layout (HomeReach.kt).
                val reachFree = !aDialogIsOpen && !showDetails && quickJump == null && miniFocus == null &&
                    event.nativeKeyEvent.downTime > dialogClosedAt
                if (HomeReach.zone != null && (!reachFree || HomeEditState.active)) HomeReach.leave()
                if (reachFree && HomeReach.key(event, press = { z, i -> pressReach(z, i) }, sound = { sounds?.play(it) })) return@onKeyEvent true
                // Goes past the games' edge (▲ [goingUp], else ▼): true when something was there.
                fun reach(goingUp: Boolean): Boolean {
                    if (!reachFree || HomeEditState.active) return false
                    prepareReach()
                    if (!HomeReach.enter(goingUp, event.nativeKeyEvent.eventTime)) {
                        // Update 177: past the bottom of the main screen, nothing more there: onto the second screen.
                        if (!goingUp && SecondPad.enter()) {
                            sounds?.play(UiSound.MOVE)
                            return true
                        }
                        return false
                    }
                    tileBarFocus = null
                    sounds?.play(UiSound.MOVE)
                    HomeReach.keysDown += event.nativeKeyEvent.keyCode
                    return true
                }
                val reachDown = event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0
                val reachKey = event.key == Key.DirectionUp || event.key == Key.DirectionDown
                // The classic row: ▲ / ▼ were free; they go to what is above / below the row.
                if (reachFree && reachDown && reachKey && tileBarFocus == null && !tileLayout && !heroLayout && !gridLayout && !tvLayout && !styleLayout &&
                    reach(event.key == Key.DirectionUp)) return@onKeyEvent true
                // Grid / TV room: past the top row / the last row.
                if (reachFree && reachDown && reachKey && (gridLayout || tvLayout) && visibleGames.isNotEmpty()) {
                    val cols = GridHome.columns.coerceAtLeast(1)
                    val edge = if (event.key == Key.DirectionUp) state.focusedIndex < cols else state.focusedIndex + cols > visibleGames.lastIndex
                    if (edge && reach(event.key == Key.DirectionUp)) return@onKeyEvent true
                }
                // Hero carousel: ▲ on the first game, ▼ on the last (not with the endless row: it wraps round there).
                if (reachFree && reachDown && reachKey && heroLayout && tileBarFocus == null && !state.appearance.loopRow) {
                    val edge = if (event.key == Key.DirectionUp) state.focusedIndex <= 0 else state.focusedIndex >= visibleGames.lastIndex
                    if (edge && reach(event.key == Key.DirectionUp)) return@onKeyEvent true
                }
                // (Update 256: a search that found nothing — B gives the whole category back. On the style homes the empty
                // list left the controller in a bar that isn't drawn then, which took B and did nothing.)
                if (!aDialogIsOpen && !showDetails && state.searchQuery.isNotBlank() && visibleGames.isEmpty() &&
                    (event.key == Key.ButtonB || event.key == Key.Back)) {
                    if (event.type == KeyEventType.KeyUp) {
                        StyleNav.reset()
                        viewModel.setSearchQuery("")
                        sounds?.play(UiSound.BACK)
                    }
                    return@onKeyEvent true
                }
                // The style homes: their menus / bars and their own ◀ ▶ ▲ ▼ (StyleHomes.kt).
                if (styleLayout && !aDialogIsOpen && !showDetails && quickJump == null && miniFocus == null &&
                    event.nativeKeyEvent.downTime > dialogClosedAt &&
                    styleHomeKey(
                        event, state.appearance.homeLayout, visibleGames.size, state.focusedIndex,
                        setFocus = { viewModel.setFocus(it) },
                        category = { forward -> viewModel.cycleCategory(forward); CategorySlide.step(if (forward) 1 else -1) },
                        sound = { sounds?.play(it) },
                        reach = { up -> reach(up) }
                    )
                ) return@onKeyEvent true
                // Tile-style home: ▼ from the tiles goes down to the round
                // buttons, ◀ ▶ walk along them, A opens one, ▲ or B goes back up.
                if (tileLayout && !aDialogIsOpen && !showDetails && quickJump == null && miniFocus == null) {
                    val code = event.nativeKeyEvent.keyCode
                    // The buttons sit above the tiles when the tiles are at the bottom: ▲ goes to them there.
                    val buttonsAbove = state.appearance.dockPosition == com.shelf.launcher.data.DockPosition.BOTTOM
                    val tileBarEnterKey = if (buttonsAbove) Key.DirectionUp else Key.DirectionDown
                    val tileBarLeaveKey = if (buttonsAbove) Key.DirectionDown else Key.DirectionUp
                    if (event.type == KeyEventType.KeyUp && dockKeysDown.remove(code)) return@onKeyEvent true
                    val slot = tileBarFocus
                    if (slot == null) {
                        if (event.type == KeyEventType.KeyDown && event.key == tileBarEnterKey && tileShortcuts.isNotEmpty() && state.appearance.tileShortcuts &&
                            (state.appearance.shortcutSpot == com.shelf.launcher.data.ShortcutSpot.ROW || state.appearance.shortcutSpot == com.shelf.launcher.data.ShortcutSpot.FREE)) {
                            tileBarFocus = tileBarLast[0].coerceIn(0, tileShortcuts.lastIndex)
                            tileBarEnteredAt = event.nativeKeyEvent.eventTime
                            sounds?.play(UiSound.MOVE)
                            dockKeysDown += code
                            return@onKeyEvent true
                        }
                        // The other way from the tiles (or the buttons are in a corner): what lies there (HomeReach).
                        if (reachDown && reachKey && reach(event.key == Key.DirectionUp)) return@onKeyEvent true
                    } else {
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.DirectionLeft, Key.DirectionRight -> {
                                    val next = (slot + if (event.key == Key.DirectionRight) 1 else -1)
                                        .coerceIn(0, tileShortcuts.lastIndex)
                                    if (next != slot) { tileBarFocus = next; tileBarLast[0] = next; sounds?.play(UiSound.MOVE) }
                                    dockKeysDown += code
                                    return@onKeyEvent true
                                }
                                tileBarLeaveKey -> {
                                    tileBarFocus = null
                                    sounds?.play(UiSound.MOVE)
                                    dockKeysDown += code
                                    return@onKeyEvent true
                                }
                                // Further out past the buttons: what lies beyond them (HomeReach), if anything.
                                tileBarEnterKey -> {
                                    if (event.nativeKeyEvent.repeatCount == 0 && reach(event.key == Key.DirectionUp)) return@onKeyEvent true
                                    dockKeysDown += code; return@onKeyEvent true
                                }
                                else -> Unit
                            }
                        } else if (event.type == KeyEventType.KeyUp &&
                            event.nativeKeyEvent.downTime > dialogClosedAt &&
                            event.nativeKeyEvent.downTime >= tileBarEnteredAt
                        ) {
                            when (event.key) {
                                Key.ButtonA, Key.Enter, Key.DirectionCenter -> {
                                    sounds?.play(UiSound.OPEN)
                                    tileShortcuts.getOrNull(slot)?.onClick?.invoke()
                                    return@onKeyEvent true
                                }
                                Key.ButtonB, Key.Back -> {
                                    tileBarFocus = null
                                    sounds?.play(UiSound.BACK)
                                    return@onKeyEvent true
                                }
                                else -> Unit
                            }
                        }
                    }
                }

                // Grid home: ▲ ▼ move a row (on key-down, so holding keeps moving).
                if ((gridLayout || tvLayout) && !aDialogIsOpen && !showDetails && quickJump == null && miniFocus == null &&
                    (event.key == Key.DirectionUp || event.key == Key.DirectionDown)
                ) {
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.downTime > dialogClosedAt) {
                        val cols = GridHome.columns.coerceAtLeast(1)
                        val down = event.key == Key.DirectionDown
                        val n = visibleGames.size
                        val next = state.focusedIndex + (if (down) cols else -cols)
                        // Update 166: Endless on: ▲ ▼ go by the view model's step — past the last row ▼ carries on round to
                        // the first (and ▲ from the first row to the last); it used to do nothing there, so only ◀ ▶ moved,
                        // and a plain jump left the endless grid's place behind.
                        if (state.appearance.loopRow && n >= 2) { viewModel.moveFocus(if (down) cols else -cols); sounds?.play(UiSound.MOVE) }
                        else if (next in visibleGames.indices) { viewModel.setFocus(next); sounds?.play(UiSound.MOVE) }
                        // (Update 166: the last row shorter than the others: ▼ from above its end lands on the last game.)
                        else if (down && state.focusedIndex / cols < (n - 1) / cols) { viewModel.setFocus(n - 1); sounds?.play(UiSound.MOVE) }
                    }
                    return@onKeyEvent true
                }
                // Hero carousel: ▲ ▼ change game, ◀ ▶ change the info page (the hero stays).
                if (heroLayout && !aDialogIsOpen && !showDetails && quickJump == null && miniFocus == null && tileBarFocus == null &&
                    event.key in setOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight)
                ) {
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.downTime > dialogClosedAt) {
                        when (event.key) {
                            Key.DirectionUp -> viewModel.moveFocus(-1)
                            Key.DirectionDown -> viewModel.moveFocus(1)
                            Key.DirectionLeft -> heroPage = (heroPage - 1).mod(HeroPage.values().size)
                            else -> heroPage = (heroPage + 1).mod(HeroPage.values().size)
                        }
                        sounds?.play(UiSound.MOVE)
                    }
                    return@onKeyEvent true
                }
                // ◀ ▶ on the dock move the moment the button goes DOWN (they used to
                // wait for its release, which made every step feel late); holding
                // keeps moving with the D-pad's key repeat. The release is swallowed.
                val horizontal = event.key == Key.DirectionLeft || event.key == Key.DirectionRight
                if (event.type == KeyEventType.KeyDown && horizontal &&
                    event.nativeKeyEvent.downTime > dialogClosedAt &&
                    !aDialogIsOpen && !showDetails && quickJump == null && miniFocus == null && tileBarFocus == null
                ) {
                    viewModel.moveFocus(if (event.key == Key.DirectionRight) 1 else -1)
                    sounds?.play(UiSound.MOVE)
                    dockKeysDown += event.nativeKeyEvent.keyCode
                    return@onKeyEvent true
                }
                if (event.type == KeyEventType.KeyUp && horizontal && dockKeysDown.remove(event.nativeKeyEvent.keyCode)) {
                    heldRepeat = false
                    return@onKeyEvent true
                }

                // Details window: ▲ ▼ (D-pad or left stick) move between its
                // buttons like ◀ ▶ do — the right stick scrolls. On key-down,
                // so holding it keeps moving.
                if (showDetails && !aDialogIsOpen && event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionDown || event.key == Key.DirectionUp)
                ) {
                    val game = focusedGame
                    // The inline achievement list takes ▲ ▼ while the selection is on it: row by row,
                    // the window scrolls along; past the last row ▼ moves on, above the first ▲ is the bar.
                    val inlineList = if (game != null && detailsAction == DetailsAction.ACHIEVEMENTS && achInlineOpen)
                        inlineAchievementsShown(state.raAchievementDetails[game.stableKey].orEmpty(), achInlineFilter) else emptyList()
                    val down0 = event.key == Key.DirectionDown
                    if (inlineList.isNotEmpty() && ((down0 && achInlineIndex < inlineList.lastIndex) || (!down0 && achInlineIndex >= 0))) {
                        achInlineIndex += if (down0) 1 else -1
                        sounds?.play(UiSound.MOVE)
                        return@onKeyEvent true
                    }
                    if (game != null) {
                        val actions = detailsActionsFor(
                            game,
                            hasMissing = viewModel.missingFor(game, uiState).isNotEmpty() ||
                                game.stableKey in state.fillingMissing ||
                                viewModel.missingSourcesFor(game, uiState).isNotEmpty(),
                            hasTips = state.appearance.showTips && hasTipsContent(game.raGameId?.let { state.raGameTips[it] }),
                            hasSync = detailsSyncAvailable,
                            hasGallery = galleryItems.isNotEmpty(),
                            hasMore = detailsHasMore(state.metadata[game.stableKey], state.appearance.detailsSections),
                            hasFixRaLink = false,
                            hasStats = com.shelf.launcher.data.DetailsSection.PLAYTIME in state.appearance.detailsSections
                        )
                        // ▲ ▼ follow the window from top to bottom: the cog + Drive icon (top right), the
                        // buttons, achievements, artwork, summary. Past the last one the window just scrolls,
                        // so the score tiles at the very bottom are reachable too (never back up to the cog).
                        // By rows, so ▼ never walks sideways along the buttons (◀ ▶ do that):
                        // top-right icons, the buttons (lands on Play), achievements, artwork, summary.
                        val rows = DetailsRows.map { r -> r.filter { it in actions } }.filter { it.isNotEmpty() }
                        val row = rows.indexOfFirst { detailsAction in it }
                        val down = event.key == Key.DirectionDown
                        val next = when {
                            row < 0 -> DetailsAction.PLAY.takeIf { it in actions } ?: rows.firstOrNull()?.firstOrNull()
                            else -> rows.getOrNull(row + if (down) 1 else -1)?.let { r ->
                                if (DetailsAction.PLAY in r) DetailsAction.PLAY else r.first()
                            }
                        }
                        if (next != null) {
                            detailsAction = next; sounds?.play(UiSound.MOVE)
                        } else {
                            detailsScope.launch { detailsScroll.animateScrollBy(if (down) 260f else -260f) }
                        }
                    }
                    return@onKeyEvent true
                }

                // Update 125: ◀ ▶ in the details window act on the PRESS too (like ▲ ▼). On release they never got there
                // on some handhelds — the unclaimed press moved Android's own focus and the release was lost — so only
                // Play and the play time (▲ ▼) could be reached.
                if (showDetails && !aDialogIsOpen && event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionLeft || event.key == Key.DirectionRight) && event.nativeKeyEvent.downTime > dialogClosedAt
                ) {
                    val game = focusedGame
                    val actions = game?.let {
                        detailsActionsFor(
                            it,
                            hasMissing = viewModel.missingFor(it, uiState).isNotEmpty() || it.stableKey in state.fillingMissing ||
                                viewModel.missingSourcesFor(it, uiState).isNotEmpty(),
                            hasTips = state.appearance.showTips && hasTipsContent(it.raGameId?.let { id -> state.raGameTips[id] }),
                            hasSync = detailsSyncAvailable,
                            hasGallery = galleryItems.isNotEmpty(),
                            hasMore = detailsHasMore(state.metadata[it.stableKey], state.appearance.detailsSections),
                            hasFixRaLink = false,
                            hasStats = com.shelf.launcher.data.DetailsSection.PLAYTIME in state.appearance.detailsSections
                        )
                    } ?: emptyList()
                    val right = event.key == Key.DirectionRight
                    val step = if (right) 1 else -1
                    val sideRow = DetailsRows.filter { DetailsAction.GALLERY !in it && DetailsAction.SUMMARY !in it }
                        .map { r -> r.filter { it in actions } }.firstOrNull { detailsAction in it }
                    val current = actions.indexOf(detailsAction).coerceAtLeast(0)
                    val next = if (sideRow != null) sideRow.getOrNull(sideRow.indexOf(detailsAction) + step) else actions.getOrNull(current + step)
                    if (detailsAction == DetailsAction.ACHIEVEMENTS && achInlineOpen) {
                        achInlineFilter = (achInlineFilter + step + INLINE_FILTER_COUNT) % INLINE_FILTER_COUNT; achInlineIndex = -1; sounds?.play(UiSound.MOVE)
                    } else if (detailsAction == DetailsAction.GALLERY && (if (right) galleryIndex < galleryItems.lastIndex else galleryIndex > 0)) {
                        galleryIndex += step; sounds?.play(UiSound.MOVE)
                    } else if (next != null) {
                        detailsAction = next; sounds?.play(UiSound.MOVE)
                    }
                    return@onKeyEvent true
                }

                // Gamepad buttons act on release, but their PRESS must be claimed too: an unclaimed press
                // makes Android send a stand-in key instead (Select → Menu, B → Back, Start → Enter…),
                // which swallowed the first Select press and could double some others.
                if (event.type != KeyEventType.KeyUp) {
                    return@onKeyEvent android.view.KeyEvent.isGamepadButton(event.nativeKeyEvent.keyCode)
                }
                // A release Android marks as cancelled belongs to no real press.
                if (event.nativeKeyEvent.isCanceled) return@onKeyEvent true
                // The press started while a popup was still open: it was that popup's.
                if (event.nativeKeyEvent.downTime <= dialogClosedAt) return@onKeyEvent true

                // Quick-jump strip open: it takes the controller until closed.
                quickJump?.let { letter ->
                    return@onKeyEvent when (event.key) {
                        Key.DirectionLeft, Key.DirectionRight -> {
                            quickJump = nextQuickJumpLetter(
                                letter, if (event.key == Key.DirectionRight) 1 else -1, availableLetters
                            )
                            sounds?.play(UiSound.MOVE)
                            true
                        }
                        Key.ButtonA, Key.Enter, Key.DirectionCenter -> {
                            viewModel.jumpToLetter(letter)
                            quickJump = null
                            sounds?.play(UiSound.SELECT)
                            true
                        }
                        Key.ButtonB, Key.Back, Key.ButtonThumbLeft, Key.ButtonThumbRight -> {
                            quickJump = null
                            sounds?.play(UiSound.BACK)
                            true
                        }
                        else -> true
                    }
                }

                // Details window open (and no real dialog above it): it owns
                // the controller until it's closed.
                if (showDetails && !aDialogIsOpen) {
                    val game = focusedGame
                    val actions = game?.let {
                        detailsActionsFor(
                            it,
                            hasMissing = viewModel.missingFor(it, uiState).isNotEmpty() || it.stableKey in state.fillingMissing ||
                                viewModel.missingSourcesFor(it, uiState).isNotEmpty(),
                            hasTips = state.appearance.showTips && hasTipsContent(it.raGameId?.let { id -> state.raGameTips[id] }),
                            hasSync = detailsSyncAvailable,
                            hasGallery = galleryItems.isNotEmpty(),
                            hasMore = detailsHasMore(state.metadata[it.stableKey], state.appearance.detailsSections),
                            hasFixRaLink = false,
                            hasStats = com.shelf.launcher.data.DetailsSection.PLAYTIME in state.appearance.detailsSections
                        )
                    } ?: emptyList()
                    val current = actions.indexOf(detailsAction).coerceAtLeast(0)
                    // ◀ ▶ stay within the row the selection is on (as the window shows it).
                    val sideRow = DetailsRows.filter { DetailsAction.GALLERY !in it && DetailsAction.SUMMARY !in it }
                        .map { r -> r.filter { it in actions } }.firstOrNull { detailsAction in it }
                    fun sideways(step: Int): DetailsAction? =
                        if (sideRow != null) sideRow.getOrNull(sideRow.indexOf(detailsAction) + step)
                        else actions.getOrNull(current + step)
                    // Returns before the dock's key handling below, so the
                    // selection behind the window can't move while it's open.
                    return@onKeyEvent when (event.key) {
                        Key.ButtonB, Key.Back -> {
                            // B closes the More row first (back onto More), then the window.
                            if (DetailsMore.open.value) { DetailsMore.open.value = false; detailsAction = DetailsAction.MORE }
                            else showDetails = false
                            sounds?.play(UiSound.BACK); true
                        }
                        Key.ButtonA, Key.Enter, Key.DirectionCenter -> {
                            // Play has its own start-up sound.
                            if (detailsAction != DetailsAction.PLAY) sounds?.play(UiSound.SELECT)
                            game?.let { runDetailsAction(detailsAction, it) }
                            true
                        }
                        // ◀ ▶ ▲ ▼ are handled on key-down above (update 125: ◀ ▶ too).
                        Key.DirectionLeft, Key.DirectionRight, Key.DirectionDown, Key.DirectionUp -> true
                        Key.ButtonX -> { game?.let { runDetailsAction(DetailsAction.FAVORITE, it) }; true }
                        Key.ButtonY -> {
                            game?.takeIf { it.raGameId != null }?.let { achievementsShowMissing = true; viewingAchievementsForGame = it }
                            true
                        }
                        Key.ButtonStart, Key.Menu -> { game?.let { runDetailsAction(DetailsAction.OPTIONS, it) }; true }
                        Key.ButtonSelect -> { menuStartCategory = null; showMenu = true; true }
                        // Not ours (volume etc.) — let the system have it. The
                        // dock can't move anyway: its keys are handled above.
                        else -> false
                    }
                }

                // Update 144: the mini player is touch-only here (the D-pad reaches it through HomeReach). Its old D-pad
                // handling in this spot could never run (miniFocus was cleared just before it) and has been taken out.
                if (miniFocus != null) miniFocus = null

                // Any dialog open? B / Back closes the topmost one first, so the
                // whole UI stays usable from a controller without touching the screen.

                when (event.key) {
                    Key.ButtonB, Key.Back -> {
                        if (aDialogIsOpen) {
                            closeAllDialogs()
                            true
                        } else if (showSearchBar) {
                            showSearchBar = false
                            viewModel.setSearchQuery("")
                            sounds?.play(UiSound.BACK)
                            true
                        } else if (state.searchQuery.isNotBlank() && !showDetails) {
                            // (Update 252: a search you've confirmed ("robobot" in 3DS) — B gives the whole category back
                            // first. It stayed filtered: B went past it to the category screen or did nothing, and the only
                            // way out was opening the search again and deleting the text.)
                            viewModel.setSearchQuery("")
                            sounds?.play(UiSound.BACK)
                            true
                        } else if (state.appearance.categoryScreen != com.shelf.launcher.data.CategoryScreenMode.OFF &&
                            !showDetails
                        ) {
                            // B on the home screen: back out to every category.
                            openCategoryScreen()
                            true
                        } else {
                            false // let the system handle back at the top level
                        }
                    }

                    // The root is the only focus target, so navigation is
                    // handled here outright. Nothing else can move selection,
                    // which removes the double-step and the intermittent
                    // "sometimes moves, sometimes doesn't".
                    Key.DirectionLeft -> {
                        if (!aDialogIsOpen) { viewModel.moveFocus(-1); sounds?.play(UiSound.MOVE); true } else false
                    }

                    Key.DirectionRight -> {
                        if (!aDialogIsOpen) { viewModel.moveFocus(1); sounds?.play(UiSound.MOVE); true } else false
                    }

                    // Right stick click: every control, in a small card.
                    Key.ButtonThumbRight -> {
                        if (!aDialogIsOpen) {
                            showControlsHelp = true
                            sounds?.play(UiSound.OPEN)
                            true
                        } else false
                    }

                    // Left stick click: quick-jump strip.
                    Key.ButtonThumbLeft -> {
                        if (!aDialogIsOpen && availableLetters.isNotEmpty()) {
                            val here = focusedGame?.let { com.shelf.launcher.quickJumpLetter(it.name) }
                            quickJump = here?.takeIf { it in availableLetters }
                                ?: QUICK_JUMP_LETTERS.first { it in availableLetters }
                            sounds?.play(UiSound.OPEN)
                            true
                        } else false
                    }

                    // A opens the details window; A again (inside it) plays.
                    Key.Enter, Key.DirectionCenter, Key.ButtonA -> {
                        if (!aDialogIsOpen) {
                            if (focusedGame != null) {
                                sounds?.play(UiSound.SELECT)
                                openGame()
                            }
                            true
                        } else false
                    }

                    // Triggers cycle category; bumpers open the side panels.
                    Key.ButtonR2 -> {
                        if (!aDialogIsOpen) { viewModel.cycleCategory(forward = true); CategorySlide.step(1); sounds?.play(UiSound.MOVE); true } else false
                    }

                    Key.ButtonL2 -> {
                        if (!aDialogIsOpen) { viewModel.cycleCategory(forward = false); CategorySlide.step(-1); sounds?.play(UiSound.MOVE); true } else false
                    }

                    Key.ButtonR1 -> {
                        if (!aDialogIsOpen) {
                            viewModel.refreshNotifications()
                            notificationsOnly = false; showNotifications = true
                            true
                        } else false
                    }

                    Key.ButtonL1 -> {
                        if (!aDialogIsOpen) { showShortcuts = true; true } else false
                    }

                    Key.ButtonY -> {
                        if (!aDialogIsOpen) {
                            focusedGame?.let { game ->
                                if (game.raGameId != null) {
                                    viewingAchievementsForGame = game
                                } else {
                                    viewModel.setStatusMessage(
                                        "No RetroAchievements ID linked for ${game.name} yet — use the trophy icon to link one."
                                    )
                                }
                            }
                            true
                        } else false
                    }

                    Key.ButtonX -> {
                        if (!aDialogIsOpen) {
                            // X on a game that's still open in the background closes it; otherwise favourite.
                            focusedGame?.let { g ->
                                if (g.stableKey in com.shelf.launcher.data.RunningGames.keys) {
                                    viewModel.closeRunning(g)
                                    sounds?.play(UiSound.BACK)
                                } else {
                                    viewModel.toggleFavorite(g)
                                    sounds?.play(UiSound.SELECT)
                                }
                            }
                            true
                        } else false
                    }

                    // Select opens the main menu (all the settings categories).
                    Key.ButtonSelect -> {
                        if (!aDialogIsOpen) { menuStartCategory = null; showMenu = true; true } else false
                    }

                    // Start opens options for the highlighted game — the
                    // console-style expectation.
                    Key.ButtonStart, Key.Menu -> {
                        if (!aDialogIsOpen) {
                            focusedGame?.let { optionsForGame = it }
                            true
                        } else false
                    }

                    else -> false
                }
            }
    ) {
        HomeLayer()
        HomeEditBar()
        // Update 212: Edit Home — the panels close themselves; the home screen gets the keys back after them, an edit
        // left unfinished when Voltcap closed is put back, and touch Back is B (a step back, then Save / Discard).
        val editContext = androidx.compose.ui.platform.LocalContext.current
        LaunchedEffect(Unit) {
            HomeEditSession.recover(editContext)
            HomeEditState.refocus = { runCatching { rootFocusRequester.requestFocus() } }
        }
        androidx.activity.compose.BackHandler(enabled = HomeEditState.active && !aDialogIsOpen) {
            HomeEditSession.back()
        }
        // Touch back (the back gesture / button with no controller key: Android 13+ doesn't send one) closes
        // what's on top of the home screen, like B. Popups handle their own back.
        // (0.6.15, #120: the open search bar too — touch Back only emptied it (RP Classic) or did nothing with it empty
        // (Thor); the bar stayed up until B.)
        androidx.activity.compose.BackHandler(enabled = !aDialogIsOpen && (showDetails || categoryScreenOpen || showSearchBar || (state.searchQuery.isNotBlank() && !HomeEditState.active))) {
            when {
                showSearchBar && !showDetails && !categoryScreenOpen -> {
                    showSearchBar = false
                    StyleNav.reset()
                    viewModel.setSearchQuery("")
                    runCatching { rootFocusRequester.requestFocus() }
                }
                categoryScreenOpen && pbState.editing -> { pbState.saveLayout(); pbState.editing = false }
                categoryScreenOpen && pbState.grabbed >= 0 -> pbState.grabbed = -1
                showDetails -> if (DetailsMore.open.value) DetailsMore.open.value = false else showDetails = false
                // (Update 208: reordering — back puts the tile down / finishes first.)
                categoryScreenOpen && CategoryReorder.on -> if (CategoryReorder.held) cancelCategoryMove() else CategoryReorder.stop()
                categoryScreenOpen -> categoryScreenOpen = false
                // (Update 252: touch Back gives a confirmed search's whole category back too, as B does.)
                state.searchQuery.isNotBlank() -> viewModel.setSearchQuery("")
            }
            sounds?.play(UiSound.BACK)
        }
        // One-screen devices: the second screen as a panel over the main one (Settings › Screens › Second screen tools).
        VirtualSecondScreenLayer(enabled = com.shelf.launcher.data.Features.virtualSecondScreen.state.value && !secondScreenActive) {
            SecondScreenContent()
        }
        VoltHubDialog()
        // No post-session card any more: a brief line in the status bar (StatusBar's sync slot), and the
        // full summary in the game's popup.
    }

    // Platform Options (Y in the platform browser) and music of its own (platform or game).
    pbState.options?.let { tile ->
        PlatformOptionsDialog(
            tile = tile,
            onDismiss = { pbState.options = null },
            onPickPicture = {
                pickingImageFor = tile.key
                pickTileImage.launch(arrayOf("image/*"))
            },
            onRemovePicture = {
                categoryImagesStore.set(tile.key, null)
                categoryImages = categoryImagesStore.loadAll()
            },
            // (Update 212: the classic category screen has no slides to design; it opens these options too.)
            onDesignSlide = if (com.shelf.launcher.data.Features.platformView.value == com.shelf.launcher.data.Features.PlatformView.TILES) null else { {
                com.shelf.launcher.data.Features.platformView.value = com.shelf.launcher.data.Features.PlatformView.SLIDES
                pbState.loadLayout()
                pbState.editing = true
            } },
            // Update 212: Reorder from the tile's options — the classic screen and the platform browser's Grid (a console
            // only there: the other tiles keep their places in the grid).
            onReorder = run {
                val v = com.shelf.launcher.data.Features.platformView.value
                val i = categoryTiles.indexOfFirst { it.key == tile.key }
                when {
                    i < 0 -> null
                    v == com.shelf.launcher.data.Features.PlatformView.TILES -> { { categoryIndex = i; CategoryReorder.on = true; CategoryReorder.held = true } }
                    v == com.shelf.launcher.data.Features.PlatformView.GRID && tile.platform != null -> { { categoryIndex = i; pbState.grabbed = i } }
                    else -> null
                }
            },
            onMusic = { tile.platform?.let { musicOverrideFor = com.shelf.launcher.data.MusicOverrides.platformKey(it) to tile.label } }
        )
    }
    musicOverrideFor?.let { (key, title) ->
        MusicOverrideDialog(key, title) { musicOverrideFor = null }
    }
    GameDialogs()
    ViewerDialogs()
    MediaDialogs()
    SettingsDialogs()
    SyncAndSetupDialogs()
    } // with(flags)
}

/** "Couldn't refresh — showing saved data from …" for a game's achievement list, if any. */
internal fun raDetailsNote(state: com.shelf.launcher.LauncherUiState, stableKey: String): String? =
    com.shelf.launcher.data.ra.RaStatusText.detailsNote(
        error = state.raDetailsError[stableKey],
        fetchedAt = state.raDetailsFetchedAt[stableKey],
        stale = stableKey in state.raDetailsStale,
        hasData = state.raAchievementDetails[stableKey]?.isNotEmpty() == true
    )


/** The single contextual milestone line for a game (hero carousel overview). */
internal fun heroMilestone(playtimeMs: Long, lastPlayed: Long?, earned: Int, total: Int): String? {
    val days = lastPlayed?.let { ((System.currentTimeMillis() - it) / 86_400_000L).toInt() }
    return when {
        total > 0 && earned >= total -> "Mastered"
        total > 0 && total - earned == 1 -> "1 achievement remaining"
        total > 0 && com.shelf.launcher.data.remainingNote(earned, total) != null -> com.shelf.launcher.data.remainingNote(earned, total)
        days != null && days == 0 -> "Played today"
        days != null && days >= 180 -> "Not played in ${days / 30} months"
        playtimeMs == 0L -> "Not played yet"
        else -> null
    }
}

/** The band behind the games, their names and the categories (Theme look › Blur area: Game UI only), or null before layout. */
internal fun gameUiBand(w: Float, h: Float, rowTop: Float, rowBottom: Float, hero: Boolean, above: Float, below: Float): androidx.compose.ui.geometry.Rect? {
    if (w <= 0f || h <= 0f) return null
    if (hero) return androidx.compose.ui.geometry.Rect(0f, 0f, w * 0.46f, h)
    if (rowBottom <= rowTop) return null
    return androidx.compose.ui.geometry.Rect(0f, (rowTop - above).coerceAtLeast(0f), w, (rowBottom + below).coerceAtMost(h))
}

/** A rounded rectangle at [rect] (screen pixels) as a clip shape. */
internal class BandShape(private val rect: androidx.compose.ui.geometry.Rect, private val corner: Float) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.Outline =
        androidx.compose.ui.graphics.Outline.Rounded(androidx.compose.ui.geometry.RoundRect(rect, androidx.compose.ui.geometry.CornerRadius(corner)))
}

/** The Tile game look is really showing: switched on, Classic layout and the plain cover art (the other looks keep their own shapes). Tiles never unfold into a wide capsule. */
/**
 * Update 141, "Covers · first one wide" in the Voltcap row (not as a carousel or square tiles): game 1 is a fixed wide
 * capsule at the left end that never moves, folds or rolls out; every other game is a cover (the one you're on too),
 * none ever shows left of the capsule, and ◀ on game 1 goes round to the last game. Endless is off in this view.
 */
internal fun pinnedFirstOn(a: com.shelf.launcher.data.AppearanceSettings): Boolean =
    a.focusedCardStyle == com.shelf.launcher.data.FocusedCardStyle.FIRST_ONLY &&
        a.homeLayout == com.shelf.launcher.data.HomeLayout.CLASSIC &&
        com.shelf.launcher.data.HomeLayout.CLASSIC.name !in com.shelf.launcher.data.Features.carouselLayouts.value &&
        !(com.shelf.launcher.data.Features.squareTiles.value && a.cardArtFor() == com.shelf.launcher.data.CardArtType.COVER)

internal fun tileLookOn(a: com.shelf.launcher.data.AppearanceSettings): Boolean =
    com.shelf.launcher.data.Features.squareTiles.state.value &&
        a.homeLayout == com.shelf.launcher.data.HomeLayout.CLASSIC &&
        a.cardArtFor() == com.shelf.launcher.data.CardArtType.COVER


/**
 * Update 125: the game's options for the game window's own page. Cleaned up: nothing the window already has (Play,
 * Details, the achievements), nothing for the whole library (auto-match, refetch all icons, badge placement) and nothing
 * Game settings already holds (screens, artwork). The last one is always the app's own Android settings: the app
 * itself for Android games and apps, the emulator for the others.
 */
@Composable
private fun gameWindowOptions(
    g: com.shelf.launcher.data.Game,
    state: com.shelf.launcher.LauncherUiState,
    uiState: com.shelf.launcher.LauncherUiState,
    viewModel: com.shelf.launcher.LauncherViewModel,
    close: () -> Unit,
    onGameSettings: () -> Unit,
    onGameMusic: () -> Unit,
    onGuide: () -> Unit,
    onStatus: () -> Unit,
    onCollections: () -> Unit,
    onEmulator: () -> Unit,
    onMatching: () -> Unit
): List<WinOption> {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isApp = g.sourceType == com.shelf.launcher.data.SourceType.APP
    val isRom = g.sourceType == com.shelf.launcher.data.SourceType.ROM
    val fav = g.stableKey in state.favorites
    // Update 202: Manual in the options, only for a game that has one (the window stays open under the reader).
    val hasManual = rememberHasManual(g)
    fun go(f: () -> Unit): () -> Unit = { close(); f() }
    return buildList {
        add(WinOption(if (fav) "Remove from favorites" else "Add to favorites") { viewModel.toggleFavorite(g) })
        add(WinOption("Game settings", "Sync, second screen, artwork, look — for this ${if (isApp) "app" else "game"}", onClick = go(onGameSettings)))
        add(WinOption("Music", "Music of its own while you're on it", onClick = go(onGameMusic)))
        if (!isApp) add(WinOption("Guide", "Walkthrough that follows where you are", onClick = go(onGuide)))
        if (hasManual) add(WinOption("Manual", "The game's booklet, page by page (downloaded the first time)") {
            com.shelf.launcher.data.ManualLinks.open?.invoke(g)
        })
        if (state.appearance.playStatus && !isApp) add(WinOption(
            "Status: " + (state.playStatuses[g.stableKey]?.status?.let { "${it.glyph} ${it.label}" } ?: "none"), "Playing, finished, dropped or wishlist", onClick = go(onStatus)
        ))
        if (state.appearance.userCollections) add(WinOption("Collections", "Add it to one of your own lists", onClick = go(onCollections)))
        if (isRom) {
            add(WinOption("Emulator for ${g.platform}", "Which app — and core — runs this console", onClick = go(onEmulator)))
            add(WinOption("Achievement matching", if (g.raGameId != null) "Hash, custom hash or game ID" else "Not linked yet — see why, or set a hash or ID", onClick = go(onMatching)))
            if (g.raGameId != null) add(WinOption("Refetch achievement icon", "Ask RetroAchievements again for its icon", onClick = go { viewModel.refetchRaIcon(g) }))
        }
        if (isApp) {
            val ownIcon = uiState.usesOwnIcon(g)
            add(WinOption(if (ownIcon) "Use scraped art again" else "Use the app's icon as art") { g.packageName?.let { viewModel.toggleAppIconArt(it) } })
            val emu = g.packageName in uiState.emulatorPackages
            add(WinOption(if (emu) "Not an emulator" else "Mark as emulator") { g.packageName?.let { viewModel.toggleAppEmulator(it) } })
            add(WinOption("Hide from library", destructive = true, onClick = go { viewModel.hideApp(g) }))
            g.packageName?.let { pkg ->
                add(WinOption("Uninstall", "Android asks you to confirm", destructive = true, onClick = go {
                    @Suppress("DEPRECATION")
                    runCatching {
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_DELETE, android.net.Uri.parse("package:$pkg")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }))
            }
        }
        if (g.sourceType == com.shelf.launcher.data.SourceType.STEAM) {
            add(WinOption("Remove from library", destructive = true, onClick = go { g.steamAppId?.let(viewModel::removeSteamGame) }))
        }
        if (com.shelf.launcher.data.PinnedShortcuts.isShortcut(g)) {
            add(WinOption("Remove shortcut", "Takes it out of Voltcap (the game stays in its app)", destructive = true, onClick = go { com.shelf.launcher.data.PinnedShortcuts.remove(context, g.stableKey) }))
        }
        // Last: the app's own Android settings (the app itself, or the emulator that runs the game).
        val pkg = if (isApp) g.packageName else viewModel.emulatorFor(g)
        if (pkg != null) add(WinOption("App settings", if (isApp) "Android's page for this app" else "Android's page for its emulator", onClick = go {
            runCatching {
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$pkg"))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }))
    }
}
