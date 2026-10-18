package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.LauncherViewModel
import com.shelf.launcher.data.RaBadges
import com.shelf.launcher.data.RaRecentAchievement
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import java.lang.ref.WeakReference

/**
 * Links the in-game second screen ([CompanionActivity]) to the home screen's view model, which
 * stays alive while the game runs: same pages, same data, and unlock events as they arrive.
 */
object CompanionBridge {
    @Volatile var vm: WeakReference<LauncherViewModel>? = null
    private val _unlocks = MutableSharedFlow<RaRecentAchievement>(extraBufferCapacity = 16)
    val unlocks: SharedFlow<RaRecentAchievement> = _unlocks
    /** The companion is on screen: the unlock check runs more often then. */
    @Volatile var open = false

    fun unlocked(a: RaRecentAchievement) { _unlocks.tryEmit(a) }
}

/**
 * The second screen while a game runs: the game's logo first (from SteamGridDB or your own pick),
 * fading away after a few seconds; then the very same pages as the home second screen — art,
 * details, achievements, music, guide, performance — with the session timer; and a card with all
 * the details whenever an achievement unlocks.
 */
@Composable
internal fun CompanionHub(vm: LauncherViewModel, info: CompanionInfo, onBack: () -> Unit) {
    val state by vm.uiState.collectAsState()
    val guideUi by vm.guideState.collectAsState()
    val profileVersion by vm.profileVersion.collectAsState()
    val game = remember(state.games, info.gameKey) { state.games.firstOrNull { it.stableKey == info.gameKey } }
    val key = game?.stableKey

    // An unlock: refresh the game's list so the Achievements page shows it unlocked.
    LaunchedEffect(key) {
        CompanionBridge.unlocks.collect { if (game != null) vm.retryAchievementDetails(game) }
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedWhileActive {
        while (true) { now = System.currentTimeMillis(); delay(1000) }
    }

    // Update 112: the Widgets and Magnifier pages work here too, while a game runs (they showed "Not available yet."):
    // this screen is real, and Android's capture prompt is asked for from here (Voltcap's home is in the background then,
    // and Android doesn't let a background screen open the prompt).
    val mirrorContext = androidx.compose.ui.platform.LocalContext.current
    val mirrorLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { r ->
        val data = r.data
        if (r.resultCode == android.app.Activity.RESULT_OK && data != null) ScreenMirror.begin(mirrorContext, r.resultCode, data)
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        val before = ScreenMirror.onRequest
        val wasReal = SecondScreenReal.active
        ScreenMirror.onRequest = { runCatching { mirrorLauncher.launch(ScreenMirror.consentIntent(mirrorContext)) } }
        SecondScreenReal.active = true
        onDispose { ScreenMirror.onRequest = before; SecondScreenReal.active = wasReal }
    }
    var pillWidthPx by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize().background(ShelfTheme.background)) {
        if (game != null) {
            val override = remember(profileVersion, key) {
                vm.effectiveProfile(game).takeIf { it.secondScreenPage.source != com.shelf.launcher.data.ProfileSource.GLOBAL }?.secondScreenPage?.value
            }
            val awards = remember(state.raCompletionById, game.raGameId) {
                game.raGameId?.let { com.shelf.launcher.data.RaAward.from(state.raCompletionById[it]?.HighestAwardKind) }
            }
            SecondScreenHome(
                info = SecondHomeGame(
                    game = game,
                    coverUrl = state.artOverrides[key]?.cover ?: state.art[key]?.gridUrl,
                    heroUrl = com.shelf.launcher.data.ArtPick.backdrop(key, state.artOverrides[key], state.art[key], state.screenshots[key])
                        ?.takeIf { it != (state.artOverrides[key]?.cover ?: state.art[key]?.gridUrl) },
                    wideUrl = state.artOverrides[key]?.wide ?: state.art[key]?.wideGridUrl,
                    playtimeMs = (state.playtime[key] ?: 0L) + (now - info.startedAt).coerceAtLeast(0L),
                    award = awards,
                    progress = state.raProgress[key],
                    metadata = state.metadata[key],
                    metadataLoading = key in state.metadataLoading,
                    videoUrl = state.videoUrls[key],
                    recentUnlocks = game.raGameId?.let { id -> state.raRecentAchievements.filter { it.GameID == id } }.orEmpty(),
                    achievements = state.raAchievementDetails[key].orEmpty(),
                    caseUrl = typedArtFor(com.shelf.launcher.data.CardArtType.CASE, state.physicalMedia[key], null, game.platform), // (Update 231, #39)
                    discUrl = state.physicalMedia[key].orEmpty().firstOrNull { it.first == "Disc / cartridge" }?.second,
                    labelUrl = state.physicalMedia[key].orEmpty().firstOrNull { it.first == "Disc label" }?.second,
                    box2dUrl = BoxTexture.front(state.physicalMedia[key], game.platform),
                    shots = state.screenshots[key].orEmpty()
                ),
                page = override ?: state.appearance.secondPage,
                onPage = { p ->
                    if (override != null) vm.updateGameProfile(game) { it.copy(secondScreenPage = p) }
                    else vm.setAppearance(state.appearance.copy(secondPage = p))
                },
                nowPlaying = state.nowPlaying,
                onPlayPause = { vm.mediaPlayPause() },
                onNext = { vm.mediaNext() },
                onPrevious = { vm.mediaPrevious() },
                onSeek = { vm.mediaSeekTo(it) },
                videoSound = false,
                onNeedMetadata = { vm.loadMetadata(it) },
                onNeedVideo = { vm.loadVideoUrl(it, force = true) },
                showDock = state.appearance.secondDock,
                dockApps = state.appearance.secondDockApps.mapNotNull { p -> state.installedApps.firstOrNull { it.packageName == p } },
                allApps = state.installedApps,
                onLaunchApp = { vm.launchDockApp(it) },
                onDockChange = { list -> vm.setAppearance(state.appearance.copy(secondDockApps = list)) },
                onOpenSettings = onBack,
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
                onToggleLyrics = { vm.setAppearance(state.appearance.copy(secondMusicLyrics = !state.appearance.secondMusicLyrics)) },
                onMusicBlurChange = { vm.setAppearance(state.appearance.copy(secondMusicBlur = it)) },
                onNeedAchievements = { vm.loadAchievementDetails(it) },
                onNeedPhysical = { vm.loadPhysicalMedia(it) },
                walkthroughContent = { g -> GuideSecondScreenPage(g, guideUi) { vm.peekGuide(it) } },
                performanceContent = { PerfPage() },
                extraPage = { p ->
                    when (p) {
                        com.shelf.launcher.data.SecondPage.WIDGETS -> WidgetBoardPage(
                            state.nowPlaying,
                            onPlayPause = { vm.mediaPlayPause() },
                            onNext = { vm.mediaNext() },
                            onPrevious = { vm.mediaPrevious() }
                        )
                        com.shelf.launcher.data.SecondPage.MAGNIFIER -> MagnifierPage(Modifier.fillMaxSize())
                        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("The Wall shows while you're in Voltcap.", color = ShelfTheme.textMuted)
                        }
                    }
                },
                // The timer pill sits top right: the page tabs / clock keep clear of it.
                headerEndInset = with(androidx.compose.ui.platform.LocalDensity.current) { pillWidthPx.toDp() }
            )
        } else {
            Text("Playing ${gameDisplayName(info.title)}", color = ShelfTheme.colors.textPrimary, modifier = Modifier.align(Alignment.Center))
        }

        // Session timer + back, small, top right (out of the way of the page tabs).
        Row(
            Modifier
                .align(Alignment.TopEnd)
                // Its width (padding included) is kept free at the end of the page header.
                .onSizeChanged { pillWidthPx = it.width }
                .padding(top = 10.dp, end = 12.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⏱ " + sessionClock(now - info.startedAt), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            Text("Voltcap", color = ShelfTheme.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.pointerInput(Unit) { detectTapGestures { onBack() } })
        }

        // (Update 113: the big logo — SteamGridDB's by default.)
        val logoUrl = game?.let { bigLogo(it, state) } ?: key?.let { state.artOverrides[it]?.logo ?: state.art[it]?.logoUrl }
        LogoIntro(logoUrl, info.title, state.artOverrides[key]?.hero ?: state.art[key]?.heroUrl ?: info.heroUrl)

        UnlockPopups(game?.raGameId, state.raProgress[key])
    }
}

private fun sessionClock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** The game's logo, big, over its blurred background; fades away after a few seconds (tap skips). */
@Composable
private fun LogoIntro(logoUrl: String?, title: String, heroUrl: String?) {
    var shown by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(7000); shown = false }
    AnimatedVisibility(shown, enter = fadeIn(tween(400)), exit = fadeOut(tween(1200))) {
        Box(
            Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures { shown = false } },
            contentAlignment = Alignment.Center
        ) {
            if (heroUrl != null) {
                AsyncImage(heroUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(24.dp).alpha(0.5f))
            }
            var logoFailed by remember(logoUrl) { mutableStateOf(logoUrl == null) }
            if (!logoFailed) {
                AsyncImage(
                    logoUrl, title, contentScale = ContentScale.Fit, onError = { logoFailed = true },
                    modifier = Modifier.fillMaxWidth(0.7f).height(160.dp)
                )
            } else {
                Text(
                    gameDisplayName(title), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp),
                    style = nameTextStyle(com.shelf.launcher.data.NameBackdrop.SHADOW)
                )
            }
        }
    }
}

/** One card per unlock with everything about it; stays ~9 s, tap to close. Queued when several arrive. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun UnlockPopups(raGameId: Int?, progress: com.shelf.launcher.data.RaGameProgress?) {
    val queue = remember { mutableStateOf(listOf<RaRecentAchievement>()) }
    val vm = CompanionBridge.vm?.get()
    LaunchedEffect(Unit) {
        // The same unlock twice in a row would never leave the queue (its key wouldn't change): once is enough.
        CompanionBridge.unlocks.collect { a ->
            if (queue.value.none { it.AchievementID == a.AchievementID }) queue.value = queue.value + a
        }
    }
    val current = queue.value.firstOrNull()
    var visible by remember(current?.AchievementID) { mutableStateOf(current != null) }
    LaunchedEffect(current?.AchievementID) {
        if (current == null) return@LaunchedEffect
        visible = true
        // Update 144: a tap that closes the card ends the wait at once, so the next queued unlock
        // follows straight away instead of after the rest of the 9 s.
        kotlinx.coroutines.withTimeoutOrNull(9000) {
            androidx.compose.runtime.snapshotFlow { visible }.first { !it }
        }
        visible = false
        delay(400)
        queue.value = queue.value.drop(1)
    }
    val details = remember(current?.AchievementID, vm) {
        current?.AchievementID?.let { id ->
            vm?.uiState?.value?.raAchievementDetails?.values?.flatten()?.firstOrNull { it.id == id }
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible && current != null,
            enter = fadeIn(tween(250)) + scaleIn(initialScale = 0.85f) + slideInVertically { it / 4 },
            exit = fadeOut(tween(300)) + slideOutVertically { -it / 4 }
        ) {
            val a = current ?: return@AnimatedVisibility
            Column(
                Modifier
                    .widthIn(max = 520.dp)
                    .padding(24.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.verticalGradient(listOf(ShelfTheme.colors.surfaceRaised, ShelfTheme.colors.surface)))
                    .border(2.dp, ShelfTheme.accent, RoundedCornerShape(22.dp))
                    .pointerInput(a.AchievementID) { detectTapGestures { visible = false } }
                    // A short second screen: the card scrolls instead of losing its bottom.
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("ACHIEVEMENT UNLOCKED", color = ShelfTheme.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Spacer(Modifier.height(12.dp))
                AsyncImage(RaBadges.url(a.BadgeName), a.Title, modifier = Modifier.size(96.dp).clip(RoundedCornerShape(14.dp)))
                Spacer(Modifier.height(12.dp))
                Text(a.Title.orEmpty(), color = ShelfTheme.colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                if (!a.Description.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(a.Description!!, color = ShelfTheme.textMuted, fontSize = 15.sp, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(12.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    fun pill(t: String) = t
                    listOfNotNull(
                        a.Points?.let { "$it points" },
                        if (a.HardcoreMode == 1) "Hardcore" else "Softcore",
                        a.Type?.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() },
                        details?.unlockPercent?.let { "%.1f %% of players".format(it) }
                    ).forEach { t ->
                        Text(
                            pill(t), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f)).padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                val possible = progress?.NumPossibleAchievements ?: 0
                if (possible > 0 && (raGameId == null || a.GameID == raGameId)) {
                    val earned = ((progress?.NumAchieved ?: 0) + 1).coerceAtMost(possible)
                    Spacer(Modifier.height(14.dp))
                    Text("$earned / $possible in ${a.GameTitle ?: "this game"}", color = ShelfTheme.colors.textPrimary, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.15f))) {
                        Box(Modifier.fillMaxWidth(earned.toFloat() / possible).height(6.dp).background(ShelfTheme.accent))
                    }
                }
            }
        }
    }
}
