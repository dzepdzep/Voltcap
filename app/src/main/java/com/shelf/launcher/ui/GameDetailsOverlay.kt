package com.shelf.launcher.ui

import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Leaderboard
import android.os.Build
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.shelf.launcher.data.shownIn
import com.shelf.launcher.data.DetailsBackdrop
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.ArtOverride
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.GameArt
import com.shelf.launcher.data.GameMetadata
import com.shelf.launcher.data.RaAchievementDetail
import com.shelf.launcher.data.RaBadges
import com.shelf.launcher.data.PlaytimeStore
import com.shelf.launcher.data.RaGameProgress
import com.shelf.launcher.data.SourceType
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlin.math.roundToInt

/** How much of the screen the details window covers. Tweak freely. */
private const val DetailsWidthFraction = 0.94f
private const val DetailsHeightFraction = 0.94f

/** Summary lines shown before "More". */
private const val SummaryCollapsedLines = 4
private const val SummaryLongChars = 260

/** The summary is long enough to need a More / Less button. */
fun detailsHasMore(metadata: GameMetadata?, sections: Set<com.shelf.launcher.data.DetailsSection>): Boolean =
    com.shelf.launcher.data.DetailsSection.SUMMARY in sections && (metadata?.summary?.length ?: 0) > SummaryLongChars

/** The buttons along the top of the details window, in D-pad order. */
enum class DetailsAction(val glyph: String, val label: String) {
    PLAY("▶", "Play"),
    FAVORITE("X", "Favorite"),
    // (Update 231, #81: the classic details window opens this game's own quick performance settings from here.)
    /** This game's quick performance settings (only on a handheld that can be tuned: Tuner.supported). */
    PERFORMANCE("⚡", "Performance"),
    ADD_MISSING("＋", "Add missing"),
    /** The Google Drive save-sync badge under the play time. */
    SYNC("☁", "Save sync"),
    /** The achievement progress bar under the scores. */
    ACHIEVEMENTS("Y", "Achievements"),
    /** "Fix link" in the RA title-match warning (only while that warning is showing). */
    FIX_RA_LINK("🔗", "Fix link"),
    /** Guide + community comments, full screen (controller-friendly). */
    TIPS("💬", "Tips"),
    /** The walkthrough screen (Continue guide once there is a saved place). */
    GUIDE("📖", "Guide"),
    /** The game's manual (PDF next to the ROM, or ScreenScraper's scan). */
    MANUAL("📕", "Manual"),
    /** Steam games, with a Steam account connected (Settings › Accounts). */
    STEAM_ACHIEVEMENTS("🏆", "Steam achievements"),
    /** RetroAchievements leaderboards for linked games. */
    LEADERBOARDS("📊", "Leaderboards"),
    /** The "Artwork" strip: ◀ ▶ walk along it, A opens the full-screen viewer. */
    GALLERY("🖼", "Artwork"),
    /** "More" / "Less" under a long summary. */
    SUMMARY("More", "More"),
    /** "More": opens the secondary actions (guide, tips, manual, leaderboards, add missing…). */
    MORE("⋯", "More"),
    /** The cog in the top-right corner: the game's options. */
    OPTIONS("START", "Options"),
    /** The time-played pill (top right): opens this game's play stats. */
    STATS("⏱", "Play stats"),
    /** B — not a button in the window, just the action. */
    CLOSE("B", "Close")
}

/** Actions available for [game] — Achievements only when it's linked,
 * "Add missing" only when something can be fetched ([hasMissing]). */
fun detailsActionsFor(game: Game, hasMissing: Boolean = false, hasTips: Boolean = false, hasSync: Boolean = false, hasGallery: Boolean = false, hasMore: Boolean = false, hasFixRaLink: Boolean = false, hasStats: Boolean = true): List<DetailsAction> {
    val all = detailsActionsAll(game, hasMissing, hasTips, hasSync, hasGallery, hasMore, hasFixRaLink, hasStats)
    // The secondary actions live under More: shown (and reachable) only while it's open.
    val secondary = all.filter { it in DetailsSecondary }
    return all.filter { it !in DetailsSecondary || DetailsMore.open.value } +
        (if (secondary.isNotEmpty()) listOf(DetailsAction.MORE) else emptyList())
}

/** Under "More" in the game popup (Play, achievements and the recent session stay up front). */
val DetailsSecondary = setOf(
    DetailsAction.ADD_MISSING, DetailsAction.TIPS, DetailsAction.GUIDE, DetailsAction.MANUAL,
    DetailsAction.STEAM_ACHIEVEMENTS, DetailsAction.LEADERBOARDS
)

/** Whether the popup's More row is open (closed again whenever the popup opens or changes game). */
object DetailsMore {
    val open = androidx.compose.runtime.mutableStateOf(false)
}

private fun detailsActionsAll(game: Game, hasMissing: Boolean, hasTips: Boolean, hasSync: Boolean, hasGallery: Boolean, hasMore: Boolean, hasFixRaLink: Boolean, hasStats: Boolean): List<DetailsAction> =
    DetailsAction.values().filter {
        when (it) {
            DetailsAction.ACHIEVEMENTS -> game.raGameId != null
            DetailsAction.TIPS -> hasTips && game.raGameId != null
            DetailsAction.GUIDE -> game.sourceType != com.shelf.launcher.data.SourceType.APP
            DetailsAction.MANUAL -> game.sourceType == com.shelf.launcher.data.SourceType.ROM
            DetailsAction.STEAM_ACHIEVEMENTS -> game.steamAppId != null && SteamAccountState.connected
            DetailsAction.LEADERBOARDS -> game.raGameId != null
            DetailsAction.ADD_MISSING -> hasMissing
            DetailsAction.SYNC -> hasSync
            DetailsAction.GALLERY -> hasGallery
            DetailsAction.SUMMARY -> hasMore
            DetailsAction.FIX_RA_LINK -> hasFixRaLink
            DetailsAction.STATS -> hasStats
            // (Update 231, #81: only where the tuner works — elsewhere there's nothing to set.)
            DetailsAction.PERFORMANCE -> com.shelf.launcher.device.tune.Tuner.supported
            DetailsAction.CLOSE, DetailsAction.MORE -> false
            else -> true
        }
    }

/** True while the details window shows the RA title-match warning (so its "Fix link" chip can be selected). */
fun detailsHasFixRaLink(
    game: Game,
    info: com.shelf.launcher.data.ra.RaMatchInfo?,
    sections: Set<com.shelf.launcher.data.DetailsSection>
): Boolean = game.raGameId != null && info?.source == com.shelf.launcher.data.RaMatchSource.TITLE &&
    com.shelf.launcher.data.DetailsSection.ACHIEVEMENTS in sections

/**
 * Full details window for one game, opened with A. Sits inside the launcher's
 * root Box rather than in a Dialog window, so the root key handler keeps
 * receiving gamepad input while it's open (see LauncherScreen).
 */
@Composable
fun GameDetailsOverlay(
    game: Game,
    /** The last play session's summary (time, unlocks, completion change), when it was this game. */
    lastSummary: SessionSummary? = null,
    art: GameArt?,
    artOverride: ArtOverride?,
    metadata: GameMetadata?,
    metadataLoading: Boolean,
    metadataReady: Boolean,
    raProgress: RaGameProgress?,
    achievements: List<RaAchievementDetail>,
    achievementsLoading: Boolean,
    lastPlayedAt: Long?,
    playtimeMs: Long?,
    isFavorite: Boolean,
    scrollState: ScrollState,
    selectedAction: DetailsAction,
    onAction: (DetailsAction) -> Unit,
    /** Fill the whole screen — used when the window moves to a second display. */
    fillScreen: Boolean = false,
    screenshots: List<String> = emptyList(),
    videoUrl: String? = null,
    /** Box front / back, disc… as (label, address). */
    physicalMedia: List<Pair<String, String>> = emptyList(),
    /** What "Add missing" would fetch (labels); empty hides the button. */
    missing: List<String> = emptyList(),
    fillingMissing: Boolean = false,
    /** "Add missing" already ran; it now offers to try one other source. */
    canRetryMissing: Boolean = false,
    /** Result of the last lookup for this game, shown under the buttons. */
    note: String? = null,
    /** What the window shows (Settings › Appearance › Details window). */
    sections: Set<com.shelf.launcher.data.DetailsSection> = com.shelf.launcher.data.DetailsSection.values().toSet(),
    /** What sits behind the window's contents (Appearance › Details window). */
    backdrop: com.shelf.launcher.data.DetailsBackdrop = com.shelf.launcher.data.DetailsBackdrop.COLORS,
    /** Google Drive: "Saves synced 5 min ago · from AYN Odin 2" (null hides it). */
    saveSync: String? = null,
    /** Drive is connected and this game's saves can sync: shows the [SaveSyncBadge]. */
    syncAvailable: Boolean = false,
    syncStatus: com.shelf.launcher.data.GameSyncStatus? = null,
    /** RetroAchievements Rich Presence from the last session of this game. */
    richPresence: String? = null,
    /** Comments on the game's RetroAchievements page + its guide link. */
    tips: com.shelf.launcher.data.RaGameTips? = null,
    /** "Couldn't refresh — showing saved data from …" for the achievements (null = none). */
    achievementsNote: String? = null,
    /** How the game is linked to RetroAchievements (a title match shows a warning). */
    raMatchInfo: com.shelf.launcher.data.ra.RaMatchInfo? = null,
    /** Opens the RetroAchievements matching dialog. */
    onFixRaLink: () -> Unit = {},
    /** Settings › Appearance › Achievements › Tips. */
    showTips: Boolean = true,
    /** A walkthrough place is saved: the Guide button reads "Continue guide". */
    guideContinue: Boolean = false,
    /** Playing / Finished / … (null = none, or the feature is off). */
    playStatus: com.shelf.launcher.data.PlayStatus? = null,
    /** Your own screenshots of this game (Settings › Library › Your screenshots). */
    showOwnScreenshots: Boolean = false,
    screenshotFolders: Set<String> = emptySet(),
    onOpenScreenshot: (List<java.io.File>, Int) -> Unit = { _, _ -> },
    /** Which picture of the Artwork strip is ringed while [DetailsAction.GALLERY] is selected. */
    galleryIndex: Int = 0,
    /** The strip's pictures changed (so the controller code knows how far ◀ ▶ can go). */
    onGalleryItems: (List<GalleryItem>) -> Unit = {},
    onOpenGallery: (List<GalleryItem>, Int) -> Unit = { _, _ -> },
    /** The long summary is showing in full. */
    summaryExpanded: Boolean = false,
    /** The achievement list is open right under the progress bar (A on it), instead of a separate window. */
    achievementsExpanded: Boolean = false,
    /** Inline list filter: 0 all, 1 still to get, 2 missable, 3 missables to get, 4 unlocked (◀ ▶ while the list is open). */
    achievementsFilter: Int = 0,
    /** Row the D-pad is on in the inline list (-1 = on the progress bar). */
    achievementsSelected: Int = -1,
    /** The inline row that's opened (details + comments), or null. */
    achievementExpandedId: Int? = null,
    onToggleAchievement: (RaAchievementDetail) -> Unit = {},
    achievementComments: Map<Int, List<com.shelf.launcher.data.RaComment>> = emptyMap(),
    achievementCommentsLoading: Set<Int> = emptySet(),
    onAchievementsFilter: (Int) -> Unit = {},
    /** Tapping a row: the full list (with details and comments). */
    onOpenAchievementList: () -> Unit = {},
    /** RetroAchievements progress / award for the game (Game Journey, the milestone line). */
    raCompletion: com.shelf.launcher.data.RaCompletionEntry? = null
) {
    // Developer, genre, date, publisher, description: only the ones switched on for this window.
    @Suppress("NAME_SHADOWING")
    val metadata = metadata.shownIn(com.shelf.launcher.data.Features.factsDetails.state.value)
    com.shelf.launcher.Breadcrumbs.mark("GameDetailsOverlay")
    val onDismiss = { onAction(DetailsAction.CLOSE) }
    val tipsReady = showTips && hasTipsContent(tips)
    val actions = detailsActionsFor(game, hasMissing = missing.isNotEmpty() || fillingMissing || canRetryMissing, hasTips = tipsReady, hasSync = syncAvailable)
    val ownShots by androidx.compose.runtime.produceState(emptyList<java.io.File>(), game.stableKey, showOwnScreenshots, screenshotFolders) {
        value = if (!showOwnScreenshots || game.sourceType != SourceType.ROM) emptyList()
        else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { com.shelf.launcher.data.Screenshots.find(game, screenshotFolders) }.getOrDefault(emptyList())
        }
    }
    val show = { section: com.shelf.launcher.data.DetailsSection -> section in sections }

    // One "Artwork" strip for everything that is a picture: art, box & disc,
    // screenshots (Media / Box & disc toggles hide their groups). Pictures that
    // fail to load drop out, so the strip and the controller's count agree.
    var failedArt by androidx.compose.runtime.remember(game.stableKey) {
        androidx.compose.runtime.mutableStateOf(emptySet<Any>())
    }
    val galleryItems = androidx.compose.runtime.remember(
        art, artOverride, physicalMedia, screenshots, ownShots, sections, failedArt
    ) {
        buildGalleryItems(
            art, artOverride, physicalMedia, screenshots, ownShots,
            showMedia = com.shelf.launcher.data.DetailsSection.MEDIA in sections,
            showPhysical = com.shelf.launcher.data.DetailsSection.PHYSICAL in sections
        ).filter { it.model !in failedArt }
    }
    LaunchedEffect(galleryItems) { onGalleryItems(galleryItems) }
    var galleryTop by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
    var achTop by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
    var summaryTop by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
    // Where the sections' column starts inside the scrolling page: the three tops above are measured
    // inside that column, so this is added to them (without it every target was ~0 and ▼ onto the
    // achievements scrolled the page back to the top instead of down to them).
    var sectionsTop by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }

    // Keep the highlighted thing in view: the buttons at the top scroll back up, the parts further
    // down (achievements, artwork, summary) are scrolled to.
    LaunchedEffect(selectedAction) {
        val target = when (selectedAction) {
            DetailsAction.ACHIEVEMENTS -> sectionsTop + achTop - 12
            DetailsAction.GALLERY -> sectionsTop + galleryTop - 12
            DetailsAction.SUMMARY -> sectionsTop + summaryTop - 12
            else -> 0
        }
        scrollState.animateScrollTo(target.coerceAtLeast(0))
    }

    val shape = uiRounded(14.dp)
    // The banner follows the artwork priority (a portrait cover never fills this wide banner, so
    // "Cover first" counts as Hero first here).
    val heroUrl = com.shelf.launcher.data.ArtPick.backdrop(
        game.stableKey, artOverride, art, screenshots,
        priority = com.shelf.launcher.data.GameLooks.artPriority(game.stableKey).let {
            if (it == com.shelf.launcher.data.Features.ArtPriority.COVER) com.shelf.launcher.data.Features.ArtPriority.HERO else it
        }
    )?.takeIf { it != (artOverride?.cover ?: art?.gridUrl) }
    val coverUrl = artOverride?.cover ?: art?.gridUrl
    val logoUrl = artOverride?.logo ?: art?.logoUrl
    val titles = LocalTitleOptions.current
    // The page continues the hero: two of its colours (very dark, restrained) run down from the banner
    // into the near-black page, so the lower part feels like the same picture instead of a panel.
    val base = androidx.compose.ui.graphics.lerp(ShelfTheme.background, Color.Black, 0.45f)
    val artColors = rememberArtPalette(heroUrl ?: coverUrl, enabled = !com.shelf.launcher.data.Features.themeColorsOnly.state.value)
    val tone1 by androidx.compose.animation.animateColorAsState(
        androidx.compose.ui.graphics.lerp(artColors?.accent ?: ShelfTheme.accent, Color.Black, 0.74f), androidx.compose.animation.core.tween(500), label = "page1")
    val tone2 by androidx.compose.animation.animateColorAsState(
        androidx.compose.ui.graphics.lerp(artColors?.secondary ?: ShelfTheme.colors.secondary, Color.Black, 0.84f), androidx.compose.animation.core.tween(500), label = "page2")
    val pageColor = tone1
    val pageBrush = Brush.verticalGradient(0f to tone1, 0.45f to tone2, 1f to base)

    // Start at the top whenever the window opens or switches game (left/right), with More closed.
    LaunchedEffect(game.stableKey) { scrollState.scrollTo(0); DetailsMore.open.value = false }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { DetailsMore.open.value = false } }

    // The last session's length, shown with the other numbers.
    val lastSessionContext = androidx.compose.ui.platform.LocalContext.current
    val lastSession by androidx.compose.runtime.produceState<PlaytimeStore.Session?>(null, game.stableKey, lastPlayedAt) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                PlaytimeStore(lastSessionContext.applicationContext).loadSessions().lastOrNull { it.key == game.stableKey }
            }.getOrNull()
        }
    }

    // Dims the launcher behind; tapping outside the window closes it.
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (fillScreen) 1f else 0.7f))
            // tapToRun: always the latest close callback (pointerInput(Unit) kept the first one).
            .tapToRun(onDismiss),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier
                .fillMaxWidth(if (fillScreen) 1f else DetailsWidthFraction)
                .fillMaxHeight(if (fillScreen) 1f else DetailsHeightFraction)
                .clip(shape)
                .background(pageBrush)
                .border(1.dp, Color.White.copy(alpha = 0.1f), shape)
                // Swallow taps on the window itself so they don't close it.
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            val bannerH = (maxHeight * 0.42f).coerceIn(140.dp, 340.dp)
            val windowW = maxWidth
            // The chosen backdrop (Appearance › Details window), very faint behind the page.
            DetailsBackdropLayer(
                backdrop = backdrop,
                heroUrl = heroUrl,
                coverUrl = coverUrl,
                screenshots = screenshots,
                videoUrl = videoUrl,
                hue = game.hue,
                accent = artColors?.accent ?: ShelfTheme.accent,
                secondary = artColors?.secondary ?: ShelfTheme.colors.secondary,
                // (a picture is faint behind the text; the colour drape, also the fallback when there is no picture, is not)
                modifier = Modifier.matchParentSize()
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // ---- The banner: wide art edge to edge, the logo (or name) on it ----
                Box(Modifier.fillMaxWidth().height(bannerH).background(hueBrush(game.hue))) {
                    when {
                        heroUrl != null -> AsyncImage(
                            model = rememberFullQualityHero(heroUrl), contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().pixelArtwork()
                        )
                        coverUrl != null -> AsyncImage(
                            model = coverUrl, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().blur(24.dp).alpha(0.8f)
                        )
                        game.sourceType == SourceType.APP -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            AppIconArt(game.packageName, wide = false, modifier = Modifier.size(bannerH * 0.5f))
                        }
                    }
                    // Fades into the page at the bottom, so the buttons sit on the art's own colours.
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                0f to Color.Transparent, 0.55f to Color.Transparent, 1f to pageColor
                            )
                        )
                    )
                    LogoAndName(
                        logoUrl = if (titles.showLogos) logoUrl else null,
                        placement = titles.logoPlacement,
                        logoHeight = (bannerH * 0.32f).coerceIn(44.dp, 96.dp),
                        logoMaxWidth = 300.dp,
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 28.dp, end = 20.dp, bottom = 22.dp)
                    ) { m ->
                        Text(
                            titles.display(game.name),
                            color = Color.White, fontSize = 26.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(26.sp), maxFontSize = 26.sp), lineHeight = androidx.compose.ui.unit.TextUnit(1.154f, androidx.compose.ui.unit.TextUnitType.Em), fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = uiFontStyle().merge(androidx.compose.ui.text.TextStyle(
                                shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.8f), androidx.compose.ui.geometry.Offset(0f, 2f), 10f)
                            )),
                            modifier = m.widthIn(max = 520.dp)
                        )
                    }
                    if (playStatus != null) {
                        Text(
                            "${playStatus.glyph}  ${playStatus.label}",
                            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(14.dp)
                                .clip(uiRounded(50))
                                .background(statusColor(playStatus).copy(alpha = 0.6f))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }

                // ---- Play, the numbers, and the square buttons on the right ----
                // Small windows (the lower screen of a dual-screen handheld, 3.5" handhelds): the buttons
                // share the first line with Play and the numbers wrap onto the lines under it, so nothing
                // is cut off; wide ones keep everything on one line (numbers wrap if they still don't fit).
                val compactRow = windowW < 620.dp
                val buttonSize = if (compactRow) 38.dp else 44.dp
                val scoreValue = metadata?.let { m ->
                    (m.metacritic ?: m.criticScore)?.takeIf { show(com.shelf.launcher.data.DetailsSection.SCORES) }
                }
                val beatHours = metadata?.let { m ->
                    (m.normallyHours ?: m.hastilyHours)?.takeIf { show(com.shelf.launcher.data.DetailsSection.TIME_TO_BEAT) }
                }
                val completeHours = metadata?.completelyHours?.takeIf {
                    show(com.shelf.launcher.data.DetailsSection.TIME_TO_BEAT) && it != beatHours
                }
                @Composable
                fun Stats(modifier: Modifier) {
                    androidx.compose.foundation.layout.FlowRow(
                        modifier,
                        horizontalArrangement = Arrangement.spacedBy(if (compactRow) 14.dp else 22.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (show(com.shelf.launcher.data.DetailsSection.PLAYTIME)) {
                            StatColumn("LAST PLAYED", lastPlayedAt?.let { lastPlayedLabel(it) } ?: "Never", compact = compactRow)
                        }
                        // The critic score and time to beat sit right next to "Last played".
                        if (scoreValue != null) {
                            StatColumn(
                                if (metadata?.metacritic != null) "METACRITIC" else "CRITICS", scoreValue.toString(),
                                compact = compactRow, valueColor = scoreColor(scoreValue)
                            )
                        }
                        if (beatHours != null) StatColumn("TIME TO BEAT", formatHours(beatHours), compact = compactRow)
                        if (completeHours != null) StatColumn("100 %", formatHours(completeHours), compact = compactRow)
                        if (show(com.shelf.launcher.data.DetailsSection.PLAYTIME)) {
                            StatColumn(
                                "PLAY TIME",
                                playtimeMs?.takeIf { it > 0L }?.let { PlaytimeStore.format(it) } ?: "New",
                                selected = selectedAction == DetailsAction.STATS,
                                onClick = { onAction(DetailsAction.STATS) },
                                compact = compactRow
                            )
                            lastSession?.let { StatColumn("LAST SESSION", PlaytimeStore.format(it.ms), compact = compactRow) }
                        }
                        if (game.raGameId != null) {
                            val total = if (achievements.isNotEmpty()) achievements.size else raProgress?.NumPossibleAchievements ?: 0
                            val earned = if (achievements.isNotEmpty()) achievements.count { it.isUnlocked } else raProgress?.NumAchieved ?: 0
                            if (total > 0) StatColumn("ACHIEVEMENTS", com.shelf.launcher.data.achievementProgress(earned, total), compact = compactRow)
                        }
                        StatColumn("PLATFORM", titles.console(game.platform), compact = compactRow)
                        if (lastSummary != null && lastSummary.gameKey == game.stableKey) LastSessionLine(lastSummary)
                    }
                }
                @Composable
                fun Buttons() {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SquareButton(
                            icon = if (isFavorite) androidx.compose.material.icons.Icons.Filled.Star else androidx.compose.material.icons.Icons.Filled.StarBorder,
                            label = if (isFavorite) "Favorited" else "Favorite",
                            selected = selectedAction == DetailsAction.FAVORITE,
                            tint = if (isFavorite) com.shelf.launcher.ui.theme.Ds.gold else null,
                            size = buttonSize
                        ) { onAction(DetailsAction.FAVORITE) }
                        // (Update 231, #81: this game's quick performance settings, right beside Favorite.)
                        if (DetailsAction.PERFORMANCE in actions) {
                            SquareButton(
                                icon = androidx.compose.material.icons.Icons.Filled.Speed,
                                label = "Performance",
                                selected = selectedAction == DetailsAction.PERFORMANCE,
                                size = buttonSize
                            ) { onAction(DetailsAction.PERFORMANCE) }
                        }
                        if (syncAvailable) {
                            SyncIconButton(
                                status = syncStatus,
                                selected = selectedAction == DetailsAction.SYNC,
                                onClick = { onAction(DetailsAction.SYNC) },
                                size = buttonSize
                            )
                        }
                        if (DetailsAction.MORE in actions) {
                            SquareButton(
                                icon = androidx.compose.material.icons.Icons.Filled.MoreHoriz,
                                label = "More",
                                selected = selectedAction == DetailsAction.MORE,
                                tint = if (DetailsMore.open.value) ShelfTheme.accent else null,
                                size = buttonSize
                            ) { onAction(DetailsAction.MORE) }
                        }
                        SquareButton(
                            icon = androidx.compose.material.icons.Icons.Filled.Settings,
                            label = "Game options",
                            selected = selectedAction == DetailsAction.OPTIONS,
                            size = buttonSize
                        ) { onAction(DetailsAction.OPTIONS) }
                    }
                }
                if (com.shelf.launcher.data.Features.headerStyle.state.value == com.shelf.launcher.data.Features.HeaderStyle.SHOWCASE) {
                    // Showcase (default): "continue playing" line + stat boxes above, Play pill + round buttons beside.
                    DetailsShowcaseRow(
                        game = game, compact = compactRow, selectedAction = selectedAction, actions = actions, onAction = onAction,
                        isFavorite = isFavorite, syncAvailable = syncAvailable, syncStatus = syncStatus,
                        playtimeMs = playtimeMs, lastPlayedAt = lastPlayedAt, achievements = achievements, raProgress = raProgress,
                        beatHours = beatHours, score = scoreValue,
                        showPlaytime = show(com.shelf.launcher.data.DetailsSection.PLAYTIME),
                        console = titles.console(game.platform)
                    )
                    if (lastSummary != null && lastSummary.gameKey == game.stableKey) {
                        Box(Modifier.padding(start = if (compactRow) 16.dp else 24.dp, top = 6.dp)) { LastSessionLine(lastSummary) }
                    }
                } else if (compactRow) {
                    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            PlayButton(selected = selectedAction == DetailsAction.PLAY, compact = true, label = if (lastPlayedAt != null) "Continue" else "Play") { onAction(DetailsAction.PLAY) }
                            Spacer(Modifier.weight(1f))
                            Buttons()
                        }
                        Spacer(Modifier.height(10.dp))
                        Stats(Modifier.fillMaxWidth())
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayButton(selected = selectedAction == DetailsAction.PLAY, label = if (lastPlayedAt != null) "Continue" else "Play") { onAction(DetailsAction.PLAY) }
                        Spacer(Modifier.width(22.dp))
                        Stats(Modifier.weight(1f))
                        Spacer(Modifier.width(12.dp))
                        Buttons()
                    }
                }

                // ---- One line about the game (like a store page's "from your library" line) ----
                val infoLine = (if (show(com.shelf.launcher.data.DetailsSection.INFO))
                    listOfNotNull(metadata?.releaseYear?.toString(), metadata?.developer, metadata?.publisher) + metadata?.genres.orEmpty().take(3)
                else emptyList()).joinToString("  ·  ")
                if (infoLine.isNotBlank()) {
                    Text(
                        infoLine, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(14.sp), maxFontSize = 14.sp), maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp)
                    )
                }
                // One contextual milestone (Appearance › Details window › Milestone line): only one at a time.
                DetailsMilestoneLine(game, achievements, raProgress, raCompletion, lastPlayedAt)
                if (!richPresence.isNullOrBlank()) {
                    Text(
                        "📍  Last session: $richPresence", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(13.sp), maxFontSize = 13.sp),
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp)
                    )
                }
                if (note != null) {
                    Text(
                        note, color = ShelfTheme.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 6.dp)
                    )
                }

                // ---- The rest of what the game has, as tabs across the middle ----
                val tabs = actions.filter {
                    it == DetailsAction.ADD_MISSING || it == DetailsAction.TIPS || it == DetailsAction.GUIDE ||
                        it == DetailsAction.MANUAL || it == DetailsAction.STEAM_ACHIEVEMENTS || it == DetailsAction.LEADERBOARDS
                }
                if (tabs.isNotEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(top = 14.dp), contentAlignment = Alignment.Center) {
                        // Wraps onto a second line instead of running off the edge (small screens used
                        // to show only up to "Manual").
                        androidx.compose.foundation.layout.FlowRow(
                            Modifier.padding(horizontal = if (compactRow) 12.dp else 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            tabs.forEach { action ->
                                TabPill(
                                    label = when {
                                        action == DetailsAction.ADD_MISSING && fillingMissing -> "ADDING…"
                                        action == DetailsAction.ADD_MISSING && missing.isEmpty() -> "TRY ANOTHER SOURCE"
                                        action == DetailsAction.ADD_MISSING -> "ADD " + missing.take(2).joinToString(", ").uppercase() +
                                            if (missing.size > 2) " +${missing.size - 2}" else ""
                                        action == DetailsAction.GUIDE -> if (guideContinue) "CONTINUE GUIDE" else "GUIDE"
                                        action == DetailsAction.TIPS -> "TIPS" + (tips?.comments?.size?.takeIf { it > 0 }?.let { " ($it)" } ?: "")
                                        action == DetailsAction.STEAM_ACHIEVEMENTS -> "STEAM ACHIEVEMENTS"
                                        else -> action.label.uppercase()
                                    },
                                    selected = action == selectedAction,
                                    icon = tabIcon(action),
                                    onClick = { onAction(action) }
                                )
                            }
                        }
                    }
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { sectionsTop = it.positionInParent().y.toInt() }
                        .padding(horizontal = 24.dp)
                ) {
                    // ---- Achievements ----
                    if (show(com.shelf.launcher.data.DetailsSection.ACHIEVEMENTS) && game.raGameId != null) {
                        Box(Modifier.onGloballyPositioned { achTop = it.positionInParent().y.toInt() })
                        PageHeading("Achievements")
                        ProgressStrip(
                            raProgress = raProgress,
                            achievements = achievements,
                            isLoading = achievementsLoading,
                            selected = selectedAction == DetailsAction.ACHIEVEMENTS,
                            onClick = { onAction(DetailsAction.ACHIEVEMENTS) }
                        )
                        if (!achievementsNote.isNullOrBlank()) Muted(achievementsNote)
                        // Everything the separate list window had, right here and compact: A on the bar opens it,
                        // ◀ ▶ switch the filter, Y still opens the full window (details, comments, sorting).
                        if (achievementsExpanded && achievements.isNotEmpty()) {
                            InlineAchievements(
                                achievements = achievements,
                                filter = achievementsFilter,
                                onFilter = onAchievementsFilter,
                                onOpenList = onOpenAchievementList,
                                selectedIndex = if (selectedAction == DetailsAction.ACHIEVEMENTS) achievementsSelected else -1,
                                expandedId = achievementExpandedId,
                                onToggle = onToggleAchievement,
                                comments = achievementComments,
                                commentsLoading = achievementCommentsLoading
                            )
                        }
                        // Tips moved into More (one compact entry instead of a big block down here).
                    }

                    // ---- Game Journey: first played → sessions → achievements → milestones → mastery ----
                    DetailsJourney(game, achievements, raCompletion, compact = windowW < 620.dp)

                    // ---- Artwork (art, box & disc, screenshots, the clip) ----
                    if (galleryItems.isNotEmpty() || (videoUrl != null && show(com.shelf.launcher.data.DetailsSection.MEDIA))) {
                        Column(Modifier.onGloballyPositioned { galleryTop = it.positionInParent().y.toInt() }) {
                            PageHeading("Artwork · ${galleryItems.size}")
                            ArtworkStrip(
                                items = galleryItems,
                                selected = galleryIndex.coerceIn(0, (galleryItems.size - 1).coerceAtLeast(0)),
                                focused = selectedAction == DetailsAction.GALLERY,
                                videoUrl = videoUrl?.takeIf { show(com.shelf.launcher.data.DetailsSection.MEDIA) },
                                onOpen = { i -> onOpenGallery(galleryItems, i) },
                                onFailed = { failedArt = failedArt + it.model }
                            )
                        }
                    }

                    // ---- About ----
                    if (show(com.shelf.launcher.data.DetailsSection.SUMMARY)) {
                        Box(Modifier.onGloballyPositioned { summaryTop = it.positionInParent().y.toInt() })
                        PageHeading("About")
                        when {
                            metadata == null && metadataLoading -> Muted("Looking up ${game.name}…")
                            metadata == null && !metadataReady -> Muted(
                                "Connect a details source — IGDB or ScreenScraper (Select › Accounts) — to see the summary, score and playtimes."
                            )
                            metadata == null -> Muted("No metadata found for \"${game.name}\".")
                            else -> {
                                val summary = metadata.summary
                                if (summary.isNullOrBlank()) {
                                    Muted("No summary available.")
                                } else {
                                    Text(
                                        summary,
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 14.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(14.sp), maxFontSize = 14.sp),
                                        lineHeight = androidx.compose.ui.unit.TextUnit(1.429f, androidx.compose.ui.unit.TextUnitType.Em),
                                        maxLines = if (summaryExpanded) Int.MAX_VALUE else SummaryCollapsedLines,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (detailsHasMore(metadata, sections)) {
                                        Spacer(Modifier.height(6.dp))
                                        ActionChip(
                                            label = if (summaryExpanded) "Less" else "More",
                                            selected = selectedAction == DetailsAction.SUMMARY,
                                            onClick = { onAction(DetailsAction.SUMMARY) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Scores and time to beat moved up next to "Last played".
                    Spacer(Modifier.height(28.dp))
                }
            }
        }
    }
}

/**
 * The big Play button at the start of the row under the banner, in the chosen style (Appearance ›
 * Game popup › Play button): Block, Pill, Ring, Capsule or Text only. "Continue" once it's been played.
 */
@Composable
private fun PlayButton(selected: Boolean, compact: Boolean = false, label: String = "Play", onClick: () -> Unit) {
    val style = com.shelf.launcher.data.Features.playStyle.state.value
    val h = if (compact) 44.dp else 52.dp
    val accent = ShelfTheme.accent
    when (style) {
        com.shelf.launcher.data.Features.PlayStyle.RING -> Row(
            Modifier.revealWhen(selected).clip(uiRounded(50)).tapToRun(onClick).padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(h).focusRing(selected, androidx.compose.foundation.shape.CircleShape)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (selected) accent else Color.Transparent)
                    .border(2.dp, accent, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Filled.PlayArrow, contentDescription = null,
                    tint = if (selected) onAccentColor() else accent, modifier = Modifier.size(h * 0.55f)
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        com.shelf.launcher.data.Features.PlayStyle.TEXT -> Column(
            Modifier.revealWhen(selected).clip(uiRounded(6.dp)).tapToRun(onClick).padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Filled.PlayArrow, contentDescription = null,
                    tint = if (selected) accent else Color.White, modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(label.uppercase(), color = if (selected) accent else Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
            }
            Box(Modifier.padding(top = 3.dp).height(2.dp).width(if (selected) 90.dp else 0.dp).background(accent))
        }
        else -> {
            val shape = when (style) {
                com.shelf.launcher.data.Features.PlayStyle.PILL, com.shelf.launcher.data.Features.PlayStyle.CAPSULE -> uiRounded(50)
                else -> uiRounded(6.dp)
            }
            val capsule = style == com.shelf.launcher.data.Features.PlayStyle.CAPSULE
            Row(
                Modifier
                    .height(h)
                    .widthIn(min = if (compact) 128.dp else 170.dp)
                    .revealWhen(selected)
                    .focusRing(selected, shape)
                    .clip(shape)
                    .background(
                        if (capsule) Brush.horizontalGradient(listOf(accent, androidx.compose.ui.graphics.lerp(accent, ShelfTheme.colors.secondary, 0.55f)))
                        else androidx.compose.ui.graphics.SolidColor(if (selected) accent else accent.copy(alpha = 0.78f))
                    )
                    .then(if (capsule) Modifier.border(1.5.dp, Color.White.copy(alpha = if (selected) 0.9f else 0.35f), shape) else Modifier)
                    .tapToRun(onClick)
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Icon(
                    if (capsule) androidx.compose.material.icons.Icons.Filled.Bolt else androidx.compose.material.icons.Icons.Filled.PlayArrow,
                    contentDescription = null, tint = onAccentColor(), modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(label, color = onAccentColor(), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Showcase layout of the row under the banner (Appearance › Game popup › Layout): the accent
 * "CONTINUE PLAYING · TODAY" line and glass stat boxes above, the Play pill with round buttons
 * (favourite, Drive, ⋯ More, options) beside it and where the game comes from at the right.
 */
@Composable
private fun DetailsShowcaseRow(
    game: Game,
    compact: Boolean,
    selectedAction: DetailsAction?,
    actions: List<DetailsAction>,
    onAction: (DetailsAction) -> Unit,
    isFavorite: Boolean,
    syncAvailable: Boolean,
    syncStatus: com.shelf.launcher.data.GameSyncStatus?,
    playtimeMs: Long?,
    lastPlayedAt: Long?,
    achievements: List<RaAchievementDetail>,
    raProgress: RaGameProgress?,
    beatHours: Double?,
    score: Int?,
    showPlaytime: Boolean,
    console: String
) {
    val total = if (achievements.isNotEmpty()) achievements.size else raProgress?.NumPossibleAchievements ?: 0
    val earned = if (achievements.isNotEmpty()) achievements.count { it.isUnlocked } else raProgress?.NumAchieved ?: 0
    val stats = showcaseStats(
        playtimeMs = playtimeMs,
        lastPlayedAt = lastPlayedAt,
        earned = earned, total = if (game.raGameId != null) total else 0,
        beatHours = beatHours, score = score,
        statsAction = DetailsAction.STATS.takeIf { showPlaytime && it in actions }
    ).let { list -> if (showPlaytime) list else list.filter { it.label != "PLAYED" && it.label != "LAST PLAYED" } }
    val icons = androidx.compose.material.icons.Icons.Filled
    val syncState = syncStatus?.state
    val buttons = listOfNotNull(
        HeaderButton(
            DetailsAction.FAVORITE, if (isFavorite) icons.Star else icons.StarBorder,
            if (isFavorite) "Favorited" else "Favorite", if (isFavorite) com.shelf.launcher.ui.theme.Ds.gold else null
        ) { onAction(DetailsAction.FAVORITE) },
        // (Update 231, #81: this game's quick performance settings, right beside Favorite.)
        if (DetailsAction.PERFORMANCE in actions) HeaderButton(
            DetailsAction.PERFORMANCE, icons.Speed, "Performance"
        ) { onAction(DetailsAction.PERFORMANCE) } else null,
        if (syncAvailable) HeaderButton(
            DetailsAction.SYNC,
            when (syncState) {
                com.shelf.launcher.data.SyncState.SYNCED -> icons.CloudDone
                com.shelf.launcher.data.SyncState.OFFLINE, com.shelf.launcher.data.SyncState.ERROR,
                com.shelf.launcher.data.SyncState.NEEDS_SIGN_IN -> icons.CloudOff
                else -> icons.CloudSync
            },
            "Save sync",
            when (syncState) {
                com.shelf.launcher.data.SyncState.SYNCED -> com.shelf.launcher.ui.theme.Ds.success
                com.shelf.launcher.data.SyncState.CONFLICT, com.shelf.launcher.data.SyncState.ERROR,
                com.shelf.launcher.data.SyncState.NEEDS_SIGN_IN -> com.shelf.launcher.ui.theme.Ds.warning
                com.shelf.launcher.data.SyncState.UPLOADING, com.shelf.launcher.data.SyncState.DOWNLOADING -> ShelfTheme.accent
                else -> null
            }
        ) { onAction(DetailsAction.SYNC) } else null,
        if (DetailsAction.MORE in actions) HeaderButton(
            DetailsAction.MORE, icons.MoreHoriz, "More", if (DetailsMore.open.value) ShelfTheme.accent else null
        ) { onAction(DetailsAction.MORE) } else null,
        HeaderButton(DetailsAction.OPTIONS, icons.Settings, "Game options") { onAction(DetailsAction.OPTIONS) }
    )
    ShowcaseHeader(
        title = null,
        lastPlayedAt = lastPlayedAt,
        stats = stats,
        buttons = buttons,
        source = when (game.sourceType) {
            com.shelf.launcher.data.SourceType.STEAM -> "Steam"
            com.shelf.launcher.data.SourceType.APP -> "Android"
            else -> console
        },
        selected = selectedAction,
        compact = compact,
        onPlay = { onAction(DetailsAction.PLAY) },
        onStat = onAction,
        modifier = Modifier.fillMaxWidth().padding(start = if (compact) 16.dp else 24.dp, end = if (compact) 16.dp else 24.dp, top = 8.dp)
    )
}

/** "LAST PLAYED / Today": a small caps label over its value; tappable (and selectable) when [onClick] is set. */
@Composable
private fun StatColumn(
    label: String,
    value: String,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    compact: Boolean = false,
    valueColor: Color = Color.White
) {
    val shape = uiRounded(6.dp)
    Column(
        Modifier
            .revealWhen(selected)
            .then(if (onClick != null) Modifier.focusRing(selected, shape).clip(shape).tapToRun(onClick) else Modifier)
            .background(if (selected) Color.White.copy(alpha = 0.12f) else Color.Transparent, shape)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = if (compact) 10.sp else 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(if (compact) 10.sp else 11.sp), maxFontSize = if (compact) 10.sp else 11.sp), fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, softWrap = false)
        Text(value, color = valueColor, fontSize = if (compact) 15.sp else 17.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(if (compact) 15.sp else 17.sp), maxFontSize = if (compact) 15.sp else 17.sp), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, softWrap = false)
    }
}

/** Score colours as review sites use them: green from 75, amber from 50, red below. */
private fun scoreColor(score: Int): Color = when {
    score >= 75 -> com.shelf.launcher.ui.theme.Ds.success
    score >= 50 -> com.shelf.launcher.ui.theme.Ds.warning
    else -> com.shelf.launcher.ui.theme.Ds.danger
}

/** A square icon button like a store page's controller / cog buttons: white when selected. */
@Composable
private fun SquareButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    tint: Color? = null,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    onClick: () -> Unit
) {
    val shape = uiRounded(6.dp)
    Box(
        Modifier
            .size(size)
            .revealWhen(selected)
            .clip(shape)
            .background(if (selected) Color.White else Color.Transparent)
            .border(1.5.dp, Color.White.copy(alpha = if (selected) 1f else 0.7f), shape)
            .tapToRun(onClick),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Icon(
            icon, contentDescription = label,
            tint = if (selected) Color.Black else tint ?: Color.White,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

/** One of the tabs across the middle (Guide, Tips, Manual…): a grey pill while selected. */
@Composable
private fun TabPill(label: String, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .revealWhen(selected)
            .clip(uiRounded(50))
            .background(if (selected) Color.White.copy(alpha = 0.22f) else Color.Transparent)
            .tapToRun(onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            // The icon in the accent (Ds), centred on the text's line.
            androidx.compose.material3.Icon(
                icon, contentDescription = null,
                tint = ShelfTheme.accent,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(7.dp))
        }
        Text(
            label,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.75f),
            fontSize = 13.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(13.sp), maxFontSize = 13.sp), fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, softWrap = false,
            style = uiFontStyle().merge(CenteredLine)
        )
    }
}

/** An icon for each tab across the middle of the details window. */
private fun tabIcon(action: DetailsAction): androidx.compose.ui.graphics.vector.ImageVector? = when (action) {
    DetailsAction.ADD_MISSING -> androidx.compose.material.icons.Icons.Filled.AddPhotoAlternate
    DetailsAction.TIPS -> androidx.compose.material.icons.Icons.Filled.Forum
    DetailsAction.GUIDE -> androidx.compose.material.icons.Icons.Filled.AutoStories
    DetailsAction.MANUAL -> androidx.compose.material.icons.Icons.Filled.Description
    DetailsAction.STEAM_ACHIEVEMENTS -> androidx.compose.material.icons.Icons.Filled.EmojiEvents
    DetailsAction.LEADERBOARDS -> androidx.compose.material.icons.Icons.Filled.Leaderboard
    else -> null
}

/** The popup's single contextual milestone line (kept out of the big window function: JVM method size). */
@Composable
private fun DetailsMilestoneLine(
    game: Game, achievements: List<RaAchievementDetail>, raProgress: RaGameProgress?,
    completion: com.shelf.launcher.data.RaCompletionEntry?, lastPlayedAt: Long?
) {
    val total = if (achievements.isNotEmpty()) achievements.size else raProgress?.NumPossibleAchievements ?: 0
    val earned = if (achievements.isNotEmpty()) achievements.count { it.isUnlocked } else raProgress?.NumAchieved ?: 0
    rememberContextMilestone(game.stableKey, earned, total, completion, lastPlayedAt)?.let { m ->
        Text(
            m, color = ShelfTheme.accent, fontSize = 13.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(13.sp), maxFontSize = 13.sp), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp)
        )
    }
}

/** The popup's Game Journey section (Appearance › Details window › Game Journey). */
@Composable
private fun DetailsJourney(game: Game, achievements: List<RaAchievementDetail>, completion: com.shelf.launcher.data.RaCompletionEntry?, compact: Boolean) {
    if (!com.shelf.launcher.data.Features.gameJourney.state.value) return
    val steps = rememberGameJourney(game, achievements, completion)
    if (steps.isEmpty()) return
    PageHeading("Journey")
    GameJourneyTimeline(steps, compact = compact)
}

/** A section heading on the page ("Achievements", "About"…). */
@Composable
private fun PageHeading(text: String) {
    Text(
        text, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 22.dp, bottom = 10.dp)
    )
}

/** "Today", "Yesterday", "3 days ago", or the date. */
internal fun lastPlayedLabel(at: Long): String {
    val days = ((System.currentTimeMillis() - at) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "Today"
        days == 1 -> "Yesterday"
        days < 7 -> "$days days ago"
        else -> java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(at))
    }
}

/** The details window's controller grid, top to bottom as the window shows it (◀ ▶ move within a row). */
val DetailsRows: List<List<DetailsAction>> get() =
    if (com.shelf.launcher.data.Features.headerStyle.value == com.shelf.launcher.data.Features.HeaderStyle.SHOWCASE) listOf(
        // Showcase: the PLAYED box sits above Play; Play and its round buttons share one row.
        listOf(DetailsAction.STATS),
        // (Update 231, #81: Performance sits right after Favorite.)
        listOf(DetailsAction.PLAY, DetailsAction.FAVORITE, DetailsAction.PERFORMANCE, DetailsAction.SYNC, DetailsAction.MORE, DetailsAction.OPTIONS)
    ) + DetailsRowsBelow
    else listOf(listOf(DetailsAction.PLAY, DetailsAction.STATS, DetailsAction.FAVORITE, DetailsAction.PERFORMANCE, DetailsAction.SYNC, DetailsAction.MORE, DetailsAction.OPTIONS)) +
        DetailsRowsBelow

/** Everything under the Play row, the same in both layouts. */
private val DetailsRowsBelow: List<List<DetailsAction>> = listOf(
    listOf(DetailsAction.ADD_MISSING, DetailsAction.TIPS, DetailsAction.GUIDE, DetailsAction.MANUAL, DetailsAction.STEAM_ACHIEVEMENTS, DetailsAction.LEADERBOARDS),
    listOf(DetailsAction.ACHIEVEMENTS, DetailsAction.FIX_RA_LINK),
    listOf(DetailsAction.GALLERY),
    listOf(DetailsAction.SUMMARY)
)

/**
 * The window's background: blurred or sharp background art, the game's
 * screenshots taking turns, its muted clip, a blurred cover, or plain colour.
 * Falls back to the blurred art whenever the chosen thing isn't there.
 */
@Composable
private fun DetailsBackdropLayer(
    backdrop: com.shelf.launcher.data.DetailsBackdrop,
    heroUrl: String?,
    coverUrl: String?,
    screenshots: List<String>,
    videoUrl: String?,
    hue: Int,
    accent: Color,
    secondary: Color,
    modifier: Modifier
) {
    // Default (and the old "blurred art" value): no picture at all. Two colours from the cover are draped over the
    // panel as soft glows, so the page feels tied to the game but stays in the Voltcap look.
    val faintBase = modifier
    @Suppress("NAME_SHADOWING") val modifier = faintBase.alpha(0.18f)
    when {
        backdrop == DetailsBackdrop.PLAIN -> Box(modifier.background(ShelfTheme.background))
        backdrop == DetailsBackdrop.HERO && heroUrl != null -> AsyncImage(
            model = heroUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.pixelArtwork()
        )
        backdrop == DetailsBackdrop.COVER && coverUrl != null -> AsyncImage(
            model = coverUrl, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = modifier.blur(20.dp)
        )
        backdrop == DetailsBackdrop.VIDEO && videoUrl != null -> Box(modifier) {
            androidx.compose.runtime.key(videoUrl) {
                VideoBackdrop(videoUrl, withSound = false, modifier = Modifier.matchParentSize())
            }
        }
        backdrop == DetailsBackdrop.SCREENSHOTS && screenshots.isNotEmpty() -> {
            var index by androidx.compose.runtime.remember(screenshots) { androidx.compose.runtime.mutableStateOf(0) }
            LaunchedWhileActive(screenshots) {
                while (screenshots.size > 1) {
                    kotlinx.coroutines.delay(6_000)
                    index = (index + 1) % screenshots.size
                }
            }
            androidx.compose.animation.Crossfade(
                targetState = screenshots[index % screenshots.size],
                animationSpec = androidx.compose.animation.core.tween(1200),
                label = "detailsShots",
                modifier = modifier
            ) { url ->
                AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        else -> ColorDrape(accent, secondary, faintBase)
    }
}

internal fun hasTipsContent(tips: com.shelf.launcher.data.RaGameTips?): Boolean =
    tips != null && (tips.guideUrl != null || tips.comments.isNotEmpty())

internal fun statusColor(status: com.shelf.launcher.data.PlayStatus): Color = when (status) {
    com.shelf.launcher.data.PlayStatus.PLAYING -> Color(0xFF3C8DDE)
    com.shelf.launcher.data.PlayStatus.FINISHED -> Color(0xFF3FA66B)
    com.shelf.launcher.data.PlayStatus.DROPPED -> Color(0xFF9A5A5A)
    com.shelf.launcher.data.PlayStatus.WISHLIST -> Color(0xFFD4A63A)
    com.shelf.launcher.data.PlayStatus.BACKLOG -> Color(0xFF7A9CC6)
}

/**
 * "12 / 40 achievements · 30%" with a bar — the whole strip opens the list of
 * what's still missing.
 */
@Composable
private fun ProgressStrip(
    raProgress: RaGameProgress?,
    achievements: List<RaAchievementDetail>,
    isLoading: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    val total = if (achievements.isNotEmpty()) achievements.size else raProgress?.NumPossibleAchievements ?: 0
    val earned = if (achievements.isNotEmpty()) achievements.count { it.isUnlocked } else raProgress?.NumAchieved ?: 0
    val shape = uiRounded(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) ShelfTheme.accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f))
            .border(if (selected) 2.dp else 0.dp, if (selected) ShelfTheme.accent else Color.Transparent, shape)
            .tapToRun(onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        when {
            total <= 0 && isLoading -> Muted("Loading achievements…")
            total <= 0 -> Muted("No achievement data yet.")
            else -> {
                val fraction = (earned.toFloat() / total).coerceIn(0f, 1f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "🏆  " + com.shelf.launcher.data.achievementProgress(earned, total), color = Color.White, fontSize = 14.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(14.sp), maxFontSize = 14.sp), fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (earned >= total) "Mastered ›" else (com.shelf.launcher.data.remainingNote(earned, total) ?: "${total - earned} to go") + " ›",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(uiRounded(3.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                ) {
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(ShelfTheme.accent))
                }
            }
        }
    }
}

/**
 * Tappable button that also shows its gamepad glyph. Uses pointerInput rather
 * than clickable on purpose — same reason as GameCard: clickable registers a
 * focus target that would compete with the root key handler.
 */
@Composable
private fun ActionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    com.shelf.launcher.Breadcrumbs.mark("ActionChip")
    val fg = if (selected) onAccentColor() else Color.White
    Box(
        Modifier
            .clip(uiRounded(50))
            .background(if (selected) ShelfTheme.accent else Color.White.copy(alpha = 0.1f))
            .tapToRun(onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp)
    ) {
        Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Muted(text: String) {
    com.shelf.launcher.Breadcrumbs.mark("Muted")
    Text(text, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, lineHeight = 18.sp)
}

internal fun formatHours(hours: Double): String =
    if (hours >= 10 || hours % 1.0 == 0.0) "${hours.roundToInt()}h" else "${"%.1f".format(hours)}h"

private fun hueBrush(hue: Int): Brush = Brush.radialGradient(
    colors = listOf(
        Color.hsl(hue.toFloat(), 0.55f, 0.22f),
        Color.hsl(hue.toFloat(), 0.45f, 0.09f)
    )
)

/**
 * A tap handler that survives recomposition. pointerInput(onClick) restarted
 * the gesture detector every time the window redrew (a new lambda each
 * time — the music clock, a loading image…), which cancelled taps midway:
 * that's why the cog and buttons didn't react to touch.
 */
@Composable
private fun Modifier.tapToRun(onClick: () -> Unit): Modifier {
    val latest = androidx.compose.runtime.rememberUpdatedState(onClick)
    return this.pointerInput(Unit) { detectTapGestures { latest.value() } }
}

/** Drive status of this game's saves as one small icon (colour = state); A / tap opens the sync window. */
@Composable
private fun SyncIconButton(status: com.shelf.launcher.data.GameSyncStatus?, selected: Boolean, onClick: () -> Unit, size: androidx.compose.ui.unit.Dp = 44.dp) {
    val state = status?.state
    val tint = when (state) {
        com.shelf.launcher.data.SyncState.SYNCED -> com.shelf.launcher.ui.theme.Ds.success
        com.shelf.launcher.data.SyncState.CONFLICT, com.shelf.launcher.data.SyncState.ERROR,
        com.shelf.launcher.data.SyncState.NEEDS_SIGN_IN -> com.shelf.launcher.ui.theme.Ds.warning
        com.shelf.launcher.data.SyncState.UPLOADING, com.shelf.launcher.data.SyncState.DOWNLOADING -> ShelfTheme.accent
        else -> Color.White.copy(alpha = 0.8f)
    }
    val icon = when (state) {
        com.shelf.launcher.data.SyncState.SYNCED -> androidx.compose.material.icons.Icons.Filled.CloudDone
        com.shelf.launcher.data.SyncState.OFFLINE, com.shelf.launcher.data.SyncState.ERROR,
        com.shelf.launcher.data.SyncState.NEEDS_SIGN_IN -> androidx.compose.material.icons.Icons.Filled.CloudOff
        else -> androidx.compose.material.icons.Icons.Filled.CloudSync
    }
    // Square, like the other buttons in the row under the banner; white while selected.
    val shape = uiRounded(6.dp)
    Box(
        Modifier
            .size(size)
            .revealWhen(selected)
            .clip(shape)
            .background(if (selected) Color.White else Color.Transparent)
            .border(1.5.dp, Color.White.copy(alpha = if (selected) 1f else 0.7f), shape)
            .tapToRun(onClick),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Icon(
            icon, contentDescription = syncBadgeLabel(status),
            tint = if (selected) Color.Black else tint, modifier = Modifier.size(size * 0.5f)
        )
    }
}

private val INLINE_FILTERS = listOf("All", "Still to get", "Missable", "Missables to get", "Unlocked")

/** How many inline filters there are (◀ ▶ cycle through them). */
internal const val INLINE_FILTER_COUNT = 5

/** What the compact list shows for [filter], in RetroAchievements' own order (shared with the D-pad code). */
internal fun inlineAchievementsShown(achievements: List<RaAchievementDetail>, filter: Int): List<RaAchievementDetail> {
    val ordered = achievements.sortedWith(com.shelf.launcher.data.RA_DISPLAY_ORDER)
    return when (filter) {
        1 -> ordered.filter { !it.isUnlocked }
        2 -> ordered.filter { it.isMissable }
        // Only the missables you can still get: flagged missable and not unlocked yet.
        3 -> ordered.filter { it.isMissable && !it.isUnlocked }
        4 -> ordered.filter { it.isUnlocked }
        else -> ordered
    }
}

/**
 * The achievement list, compact, under the progress bar: filter pills + one-line rows, in the
 * set's own order (as on retroachievements.org). ▲ ▼ walk the rows ([selectedIndex], kept in view),
 * A (or a tap) opens a row right there: full description, type, rarity, when it was unlocked, and
 * its comments — no extra window.
 */
@Composable
private fun InlineAchievements(
    achievements: List<RaAchievementDetail>,
    filter: Int,
    onFilter: (Int) -> Unit,
    onOpenList: () -> Unit,
    selectedIndex: Int = -1,
    expandedId: Int? = null,
    onToggle: (RaAchievementDetail) -> Unit = {},
    comments: Map<Int, List<com.shelf.launcher.data.RaComment>> = emptyMap(),
    commentsLoading: Set<Int> = emptySet()
) {
    val shown = inlineAchievementsShown(achievements, filter)
    Spacer(Modifier.height(8.dp))
    // Five pills now: scrolls sideways on narrow screens instead of squashing them.
    Row(
        Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        INLINE_FILTERS.forEachIndexed { i, label ->
            val on = i == filter
            Text(
                label,
                color = if (on) onAccentColor() else Color.White.copy(alpha = 0.8f),
                fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(uiRounded(50))
                    .background(if (on) ShelfTheme.accent else Color.White.copy(alpha = 0.08f))
                    .tapToRun { onFilter(i) }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
    Spacer(Modifier.height(4.dp))
    if (shown.isEmpty()) Muted("Nothing here.")
    shown.forEachIndexed { index, a ->
        androidx.compose.runtime.key(a.id) {
            InlineAchievementRow(
                a = a,
                selected = index == selectedIndex,
                expanded = a.id == expandedId,
                onToggle = { onToggle(a) },
                comments = comments[a.id],
                commentsLoading = a.id in commentsLoading
            )
        }
    }
    Spacer(Modifier.height(2.dp))
    Muted("▲ ▼ Move · A Open · ◀ ▶ Filter · Y Full list")
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun InlineAchievementRow(
    a: RaAchievementDetail,
    selected: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    comments: List<com.shelf.launcher.data.RaComment>?,
    commentsLoading: Boolean
) {
    val bring = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    LaunchedEffect(selected, expanded) {
        if (selected) { kotlinx.coroutines.delay(30); runCatching { bring.bringIntoView() } }
    }
    val shape = uiRounded(8.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bring)
            // (Update 119: each achievement on its own little card, a gap between them.)
            .padding(vertical = 2.dp)
            .clip(shape)
            .background(if (selected) ShelfTheme.accent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f))
            .border(if (selected) 1.5.dp else 0.dp, if (selected) ShelfTheme.accent else Color.Transparent, shape)
            .tapToRun(onToggle)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val badgeUrl = RaBadges.url(a.badgeName)
            if (badgeUrl != null) {
                AsyncImage(
                    model = badgeUrl, contentDescription = null,
                    colorFilter = if (a.isUnlocked) null else GreyMatrix,
                    modifier = Modifier.size(26.dp).clip(uiRounded(5.dp)).alpha(if (a.isUnlocked) 1f else 0.5f)
                )
            } else Box(Modifier.size(26.dp).clip(uiRounded(5.dp)).background(Color.White.copy(alpha = 0.1f)))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        a.title ?: "Unknown achievement",
                        color = if (a.isUnlocked) Color.White else Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (a.isMissable) { Spacer(Modifier.width(4.dp)); MissableIcon() }
                }
                if (!expanded) a.description?.let {
                    Text(it, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(6.dp))
            Text(
                listOfNotNull(a.points?.let { "$it" }, a.unlockPercent?.let { "${it.toInt()}%" }).joinToString(" · "),
                color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp
            )
        }
        if (expanded) {
            Column(Modifier.padding(start = 34.dp, top = 4.dp, bottom = 4.dp)) {
                a.description?.let { Text(it, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp) }
                val facts = listOfNotNull(
                    a.points?.let { "$it points" },
                    a.type?.takeIf { it.isNotBlank() }?.replace('_', ' ')?.replaceFirstChar { it.uppercase() },
                    a.unlockPercent?.let { "${"%.1f".format(it)} % of players" },
                    (a.dateEarnedHardcore ?: a.dateEarned)?.let { "Unlocked ${it.take(10)}" + if (a.dateEarnedHardcore != null) " (hardcore)" else "" }
                ).joinToString("  ·  ")
                if (facts.isNotEmpty()) Text(facts, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                when {
                    commentsLoading && comments == null -> Text("Loading comments…", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                    comments.isNullOrEmpty() -> Text("No comments yet.", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                    else -> sortedComments(comments).take(6).forEach { c ->
                        Text(
                            buildString {
                                append(c.User ?: "Someone")
                                c.Submitted?.take(10)?.let { append(" · ").append(it) }
                            },
                            color = ShelfTheme.accent, fontSize = 10.sp, fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            (c.CommentText ?: "").trim(), color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp),
                            maxLines = 4, overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(3.dp))
                    }
                }
            }
        }
    }
}


/**
 * The details window's banner art at full quality: decoded at the picture's own resolution (capped at
 * 3840 px, 2560 on small-memory devices) in full colour, never the smaller / 16-bit copy the home
 * background keeps in memory. It comes from the on-device art store (no download) whenever it was
 * saved there.
 */
@Composable
private fun rememberFullQualityHero(url: String): coil.request.ImageRequest {
    val context = androidx.compose.ui.platform.LocalContext.current
    return androidx.compose.runtime.remember(url) {
        val cap = if (com.shelf.launcher.device.MemoryBudget.small(context)) 2560 else 3840
        coil.request.ImageRequest.Builder(context)
            .data(url)
            .size(coil.size.Size(cap, cap))
            .scale(coil.size.Scale.FIT)
            .precision(coil.size.Precision.INEXACT)
            .allowRgb565(false)
            .bitmapConfig(android.graphics.Bitmap.Config.ARGB_8888)
            .memoryCacheKey("detailsHero|$url")
            // Shows the home background's copy (if it's in memory) until the full one is ready.
            .placeholderMemoryCacheKey(url)
            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
            .crossfade(180)
            .build()
    }
}
