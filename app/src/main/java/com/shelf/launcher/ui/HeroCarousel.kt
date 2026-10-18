package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.PlaytimeStore
import com.shelf.launcher.data.RaAchievementDetail
import com.shelf.launcher.data.RaBadges
import com.shelf.launcher.data.achievementProgress
import com.shelf.launcher.ui.theme.Ds
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlin.math.abs

/**
 * Hero carousel home (Appearance › Home layout › Hero carousel): the focused game's hero fills the
 * screen; a curved column of covers runs down the left (the selected one larger, the rest falling back
 * along an arc with their names beside them); one info region sits bottom right.
 *
 * ▲ ▼ change game. ◀ ▶ page through Overview → Achievements → Play history → Screenshots → Stats →
 * Guide for the same game — the hero and the cover column stay put, only the info region changes.
 * Touch: tap a cover to pick it (tap again to open), drag the column, tap the page names.
 */
internal enum class HeroPage(val label: String) {
    OVERVIEW("Overview"), ACHIEVEMENTS("Achievements"), HISTORY("Play history"),
    SCREENSHOTS("Screenshots"), STATS("Stats"), GUIDE("Guide")
}

/** What the info region needs about the focused game (built by the home screen from its state). */
internal data class HeroInfo(
    val game: Game,
    val title: String,
    val logo: String?,
    val console: String,
    val playtimeMs: Long,
    val lastPlayed: Long?,
    val summary: String?,
    val facts: List<String>,
    val achievements: List<RaAchievementDetail>,
    val earned: Int,
    val total: Int,
    val newIds: Set<Int>,
    val screenshots: List<String>,
    val guideUrl: String?,
    val tipCount: Int,
    val milestone: String?,
    val completion: com.shelf.launcher.data.RaCompletionEntry? = null
)

// ---------------------------------------------------------------------------------------------------
// The hero behind everything (drawn under the status bar)
// ---------------------------------------------------------------------------------------------------

/**
 * One request shape for every hero picture (the backdrop and its neighbours' prefetch): same data and
 * size → the same memory-cache entry, so a prefetched or already-seen hero shows on the first frame.
 */
internal fun heroRequest(context: android.content.Context, url: String, w: Int, h: Int, gameKey: String? = null): coil.request.ImageRequest {
    // A game's own Animated artwork choice (Game settings) plays / stills this picture whatever the global switch.
    val anim = com.shelf.launcher.data.GameLooks.of(gameKey).animateArt
    return with(com.shelf.launcher.data.ArtPick) { coil.request.ImageRequest.Builder(context).animationFor(gameKey) }
        .data(url).size(w, h).memoryCacheKey("hero:$url:$w:$anim").placeholderMemoryCacheKey("hero:$url:$w:$anim").build()
}

/** Decodes the heroes next to the focused game ahead of time (only those; never the whole library). */
internal fun prefetchHeroes(context: android.content.Context, urls: List<Pair<String, String?>>, w: Int, h: Int) {
    val loader = coil.Coil.imageLoader(context)
    urls.forEach { (url, key) -> loader.enqueue(heroRequest(context, url, w, h, key)) }
}

@Composable
internal fun HeroCarouselBackdrop(heroUrl: String?, fallbackUrl: String?, modifier: Modifier = Modifier, gameKey: String? = null) {
    val blur = Features.heroBlur.state.value
    val dim = Features.heroDim.state.value
    val model = heroUrl ?: fallbackUrl
    val context = androidx.compose.ui.platform.LocalContext.current
    val screen = screenSizeDp()
    val d = LocalDensity.current.density
    // Decoded at the screen's size (capped), once; the same size everywhere so the cache hits.
    val w = (screen.width.value * d).toInt().coerceIn(320, 1920)
    val hgt = (screen.height.value * d).toInt().coerceIn(240, 1080)
    // What is on screen: it only changes once the next picture is decoded, so moving between games never
    // shows a black frame (the current picture stays until the next one is ready).
    // Coming back to this screen: a hero that is still in memory shows on the first frame (no black flash).
    // Update 144: the picture, the game it belongs to and "only a cover" are kept together. Before, the old
    // game's hero was paired with the new game straight away, so it turned into a heavily blurred layer while
    // the next one loaded (and stayed so if that failed). A hero that fails now falls back to the cover.
    var shown by remember {
        mutableStateOf<Triple<String, String?, Boolean>?>(model?.takeIf { m ->
            coil.Coil.imageLoader(context).memoryCache?.get(coil.memory.MemoryCache.Key(heroRequest(context, m, w, hgt, gameKey).memoryCacheKey?.key ?: "")) != null
        }?.let { Triple(it, gameKey, it != heroUrl) })
    }
    LaunchedEffect(heroUrl, fallbackUrl, w, gameKey) {
        val loader = coil.Coil.imageLoader(context)
        shown = when {
            heroUrl != null && loader.execute(heroRequest(context, heroUrl, w, hgt, gameKey)) is coil.request.SuccessResult ->
                Triple(heroUrl, gameKey, false)
            fallbackUrl != null && loader.execute(heroRequest(context, fallbackUrl, w, hgt, gameKey)) is coil.request.SuccessResult ->
                Triple(fallbackUrl, gameKey, true)
            else -> null
        }
    }
    Box(modifier.fillMaxSize()) {
        // Held layers (update 104): the hero on screen stays until the next one has loaded, then it fades in over it.
        // (Each layer keeps the game it was made for: its picture request never changes under it.)
        HeldLayers(shown, Features.transitionSpeed.state.value.ms.coerceIn(160, 520), Modifier.fillMaxSize()) { (url, layerKey, fallback), ready, failed ->
            // A slow settle in when a new hero arrives (from a touch of zoom).
            val settle = remember(url) { Animatable(1.045f) }
            LaunchedEffect(url) { settle.animateTo(1f, tween(1400, easing = FastOutSlowInEasing)) }
            run {
                AsyncImage(
                    model = heroRequest(context, url, w, hgt, layerKey), contentDescription = null, contentScale = ContentScale.Crop,
                    onSuccess = { ready() }, onError = { failed() },
                    modifier = Modifier
                        .fillMaxSize()
                        .pixelArtwork()
                        .graphicsLayer { scaleX = settle.value; scaleY = settle.value }
                        // Only a cover to show: always softened, it would look rough stretched.
                        .then(if (blur || fallback) Modifier.blur(if (fallback) 28.dp else 10.dp) else Modifier)
                )
            }
        }
        // Readability: the left (covers) and bottom-right (info) fall to dark; the middle stays clear.
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Color.Black.copy(alpha = if (dim) 0.62f else 0.45f), 0.38f to Color.Transparent)
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = if (dim) 0.78f else 0.55f))
            )
        )
    }
}

// ---------------------------------------------------------------------------------------------------
// Covers and the info region
// ---------------------------------------------------------------------------------------------------

@Composable
internal fun BoxScope.HeroCarouselLayer(
    games: List<Game>,
    focused: Int,
    page: Int,
    coverFor: (Game) -> String?,
    info: HeroInfo?,
    categoryLabel: String,
    onSelect: (Int) -> Unit,
    onOpen: (Game) -> Unit,
    onPage: (Int) -> Unit,
    onOpenAchievements: (Game) -> Unit,
    onOpenScreenshot: (Game, Int) -> Unit,
    onOpenGuide: (Game) -> Unit,
    onLoadMedia: (Game) -> Unit,
    bottomPadding: Dp = 0.dp,
    /** Wide capsule art (Card style › Wide capsules); falls back to the cover. */
    wideFor: (Game) -> String? = coverFor,
    /** The info sits at the top instead of the bottom (follows "Where the row sits" › Top). */
    infoAtTop: Boolean = false,
    /** Card artwork for this layout (Appearance › Game row › Cards): box art / cartridges / discs show as the real objects. */
    cardArtType: com.shelf.launcher.data.CardArtType = com.shelf.launcher.data.CardArtType.COVER,
    physicalMedia: Map<String, List<Pair<String, String>>> = emptyMap(),
    onNeedPhysical: (Game) -> Unit = {},
    /** The focused game's preview clip, for the detail pane's video layer. */
    videoUrl: String? = null,
    /** Update 125: Endless — the column goes round (the view model's step, for the way it went). */
    loop: Boolean = false,
    loopTick: Int = 0,
    loopStep: Int = 0
) {
    // Idle focus mode: after a while without input everything but the hero fades; any input brings it back.
    val hidden = Features.idleFocusMode.state.value && HomeIdle.focusHide
    // (update 91) Only while the home screen is in front: it used to wake every second during games too.
    val idleActive = rememberIsActive()
    LaunchedEffect(Features.idleFocusMode.state.value, idleActive) {
        if (!Features.idleFocusMode.value) { HomeIdle.focusHide = false; return@LaunchedEffect }
        if (!idleActive) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(1000)
            if (android.os.SystemClock.elapsedRealtime() - HomeIdle.lastInputAt > 15_000L) HomeIdle.focusHide = true
        }
    }
    val uiAlpha by animateFloatAsState(if (hidden) 0f else 1f, tween(if (hidden) 900 else 200), label = "idle")
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = uiAlpha }) {
        // Hero settings › Covers on the left / right; the info goes to the other side, top or bottom.
        val right = Features.heroCoversRight.state.value
        CoverWheel(
            games, focused, coverFor, onSelect, onOpen,
            // (Update 112: a new category slides / fades in, as everywhere.)
            Modifier.fillMaxHeight().fillMaxWidth(0.42f).align(if (right) Alignment.CenterEnd else Alignment.CenterStart).categorySlide()
                // Update 202: moved / resized / taken off in Edit Home.
                .editablePart(LayoutParts.id(com.shelf.launcher.data.HomeLayout.HERO, "covers"), "Cover column"),
            wideFor = wideFor, mirrored = right,
            cardArtType = cardArtType, physicalMedia = physicalMedia, onNeedPhysical = onNeedPhysical,
            loopFocus = rememberLoopFocus(focused, games.size, loop && games.size >= 2, loopTick, loopStep, games),
            looping = loop && games.size >= 2
        )
        if (info != null) {
            val zone = safeZone()
            val align = when {
                infoAtTop && right -> Alignment.TopStart
                infoAtTop -> Alignment.TopEnd
                right -> Alignment.BottomStart
                else -> Alignment.BottomEnd
            }
            InfoRegion(
                info, page, categoryLabel, onPage, onOpenAchievements, onOpenScreenshot, onOpenGuide, onLoadMedia,
                coverFor(info.game), videoUrl,
                Modifier.align(align)
                    .padding(
                        start = if (right) maxOf(28.dp, zone.left) else 0.dp, end = if (right) 0.dp else maxOf(28.dp, zone.right),
                        top = if (infoAtTop) maxOf(46.dp, zone.top) else 0.dp,
                        bottom = if (infoAtTop) 0.dp else maxOf(18.dp, zone.bottom) + bottomPadding
                    )
                    .fillMaxWidth(0.56f)
                    // Update 202: moved / resized / taken off in Edit Home.
                    .editablePart(LayoutParts.id(com.shelf.launcher.data.HomeLayout.HERO, "info"), "Info pages")
            )
        }
    }
}

/**
 * The curved column of covers. Positions follow a smoothly animated index, so moving glides the whole
 * arc; the selected cover is larger, framed and lifted.
 */
@Composable
private fun CoverWheel(
    games: List<Game>,
    focused: Int,
    coverFor: (Game) -> String?,
    onSelect: (Int) -> Unit,
    onOpen: (Game) -> Unit,
    modifier: Modifier,
    wideFor: (Game) -> String? = coverFor,
    /** Covers on the right: the arc bends the other way and names sit on the right. */
    mirrored: Boolean = false,
    cardArtType: com.shelf.launcher.data.CardArtType = com.shelf.launcher.data.CardArtType.COVER,
    physicalMedia: Map<String, List<Pair<String, String>>> = emptyMap(),
    onNeedPhysical: (Game) -> Unit = {},
    /** Update 125: the place on the endless column (EndlessRow.kt); equals [focused] when it doesn't go round. */
    loopFocus: Int = focused,
    looping: Boolean = false
) {
    val hiddenMedia = LocalHiddenMedia.current
    // Coloured frame (Settings › Cards & frames) round the selected cover.
    val frameOn = LocalCardLook.current.focusBorder
    val heroCarousel = com.shelf.launcher.data.HomeLayout.HERO.name in Features.carouselLayouts.state.value
    // Games from several consoles: box art is the plain cover, the same size for every game.
    val mixed = remember(games) { mixedConsoles(games) }
    // "Box art · real sizes": every case against the biggest one in the column.
    val realRef = remember(games, cardArtType) { realCaseRef(cardArtType, games) }
    // Cartridge / disc / box art: the label and box scans of the games around the one you're on.
    LaunchedEffect(focused, cardArtType, games.size) {
        if (!cardArtType.wantsMedia()) return@LaunchedEffect
        kotlinx.coroutines.delay(120)
        for (i in ((focused - 3)..(focused + 5)).map { if (looping) it.mod(games.size) else it }.distinct()) {
            // Update 156: going round, the games past the ends are the ones from the other end.
            val g = games.getOrNull(i) ?: continue
            if (!physicalMedia.containsKey(g.stableKey)) { onNeedPhysical(g); kotlinx.coroutines.delay(90) }
        }
    }
    val compact = Features.density.state.value == Features.Density.COMPACT
    val speed = Features.transitionSpeed.state.value.ms
    val style = Features.heroCardStyle.state.value
    val motion = Features.heroCarouselMotion.state.value
    // How the column moves between games (Hero settings › Transition); Glide is the original.
    val spec: androidx.compose.animation.core.AnimationSpec<Float> = when (motion) {
        Features.HeroCarouselMotion.GLIDE, Features.HeroCarouselMotion.FLIP -> tween((speed * 0.9f).toInt().coerceIn(140, 420), easing = FastOutSlowInEasing)
        Features.HeroCarouselMotion.SLIDE -> tween((speed * 0.6f).toInt().coerceIn(100, 300), easing = androidx.compose.animation.core.LinearOutSlowInEasing)
        Features.HeroCarouselMotion.FADE -> androidx.compose.animation.core.snap()
        Features.HeroCarouselMotion.ZOOM -> androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 320f)
    }
    val pos by animateFloatAsState(loopFocus.toFloat(), spec, label = "wheel")
    val change = remember { Animatable(1f) }
    LaunchedEffect(focused) {
        if (motion == Features.HeroCarouselMotion.FADE || motion == Features.HeroCarouselMotion.FLIP || motion == Features.HeroCarouselMotion.ZOOM) {
            change.snapTo(0f); change.animateTo(1f, tween(speed.coerceIn(160, 520), easing = FastOutSlowInEasing))
        }
    }
    val flat = style == Features.HeroCardStyle.FLAT
    val wide = style == Features.HeroCardStyle.WIDE
    val latestFocused by rememberUpdatedState(focused)
    // The handlers below outlive a recomposition (pointerInput): always call the latest callbacks.
    val latestSelect by rememberUpdatedState(onSelect)
    val latestOpen by rememberUpdatedState(onOpen)
    // Update 144: Endless switched on / off in Settings counts at once for the drag too (it kept the old way).
    val loopingNow by rememberUpdatedState(looping)
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier.pointerInput(games.size) {
            // Drag the column: one game per ~56 dp.
            var acc = 0f
            detectVerticalDragGestures(onDragEnd = { acc = 0f }) { _, dy ->
                acc += dy
                val step = with(density) { 56.dp.toPx() }
                // (update 91) Counted from a local target: latestFocused only changes after a recomposition, so a
                // fast drag over several covers used to move just one.
                var target = latestFocused
                while (acc <= -step) { acc += step; target = if (loopingNow) (target + 1).mod(games.size) else (target + 1).coerceAtMost(games.lastIndex); latestSelect(target) }
                while (acc >= step) { acc -= step; target = if (loopingNow) (target - 1).mod(games.size) else (target - 1).coerceAtLeast(0); latestSelect(target) }
            }
        }
    ) {
        val h = maxHeight
        // Card style: covers (rounded or square), wide capsules (their own 2.14 : 1 art), or a flat list.
        // The R1 Size tile (update 104).
        val sizeK = GameSize.scale(com.shelf.launcher.data.HomeLayout.HERO)
        val baseW = when {
            wide -> if (compact) 96.dp else 112.dp
            flat -> if (compact) 44.dp else 50.dp
            else -> if (compact) 50.dp else 56.dp
        } * sizeK
        val baseH = if (wide) baseW / 2.14f else baseW * 1.38f
        val spacing = when {
            wide -> if (compact) 34.dp else 40.dp
            flat -> baseH * 1.12f / sizeK
            else -> if (compact) 42.dp else 48.dp
        } * sizeK
        val centerY = h * 0.5f
        val centerX = if (mirrored) maxWidth * 0.38f else maxWidth * 0.62f
        val range = 7
        val first = if (looping) loopFocus - range else (focused - range).coerceAtLeast(0)
        val last = if (looping) loopFocus + range else (focused + range).coerceAtMost(games.lastIndex)
        // Far ones first so the selected cover is drawn on top.
        // (Update 218, #105: going round a short list, each game once — no copies of the same game in the arc.)
        val order = (first..last).filter { !looping || drawnOnce(it - loopFocus, games.size) }.sortedByDescending { abs(it - pos) }
        // One decode size for every cover in the arc (the selected one's), so a cover keeps its bitmap while it
        // grows / shrinks and a cover already decoded shows at once when this screen comes back.
        val coverPx = with(density) { androidx.compose.ui.unit.IntSize((baseW * 1.6f).roundToPx(), (baseH * 1.6f).roundToPx()) }
        val wheelContext = androidx.compose.ui.platform.LocalContext.current
        // Thumbnails for a wider window: holding the D-pad flies past covers that aren't decoded yet.
        LaunchedEffect(focused / 5, games.size, wide) {
            // Update 156: going round, the covers coming in past the ends (the other end's games) are warmed too.
            val urls = ((focused - 14)..(focused + 14)).mapNotNull { games.getOrNull(if (looping) it.mod(games.size) else it) }
                .mapNotNull { g -> if (wide) wideFor(g) ?: coverFor(g) else coverFor(g) }
            val handles = prefetchThumbs(wheelContext, urls)
            try { kotlinx.coroutines.awaitCancellation() } finally {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { kotlinx.coroutines.delay(1500) }
                handles.forEach { it.dispose() }
            }
        }
        // Update 112: Hero settings › Covers beside the hero — all, only the one you're on, or none.
        val sideCovers = Features.heroSideCovers.state.value
        val shownOrder = when (sideCovers) {
            Features.HeroSideCovers.ALL -> order
            Features.HeroSideCovers.FOCUSED -> order.filter { it == (if (looping) loopFocus else focused) }
            Features.HeroSideCovers.NONE -> emptyList()
        }
        for (v in shownOrder) key(if (looping) "${games[v.mod(games.size)].stableKey}#${Math.floorDiv(v, games.size)}" else games[v].stableKey) {
            // [v]: the place on the (endless) column; [i]: the game's own index.
            val i = if (looping) v.mod(games.size) else v
            val d = v - pos
            val ad = abs(d)
            // The arc: further from the selection = further left and further apart, smaller and dimmer.
            val sel = (1f - ad).coerceIn(0f, 1f)
            val pop = if (motion == Features.HeroCarouselMotion.ZOOM) 0.5f + 0.12f * (1f - change.value) else 0.5f
            val scale = if (flat) 1f + 0.25f * sel else 1f + pop * sel - 0.05f * ad.coerceAtMost(6f)
            val y = centerY + spacing * d + if (flat) 0.dp else spacing * ((if (d > 0) 1f else -1f) * 0.55f * ad.coerceAtMost(1f))
            val bend = if (flat) 0.dp else 14.dp * ad * ad.coerceAtMost(3f) * 0.6f
            val x = if (mirrored) centerX + bend else centerX - bend
            val fade = if (motion == Features.HeroCarouselMotion.FADE) 0.35f + 0.65f * change.value else 1f
            val alpha = (1f - 0.13f * ad).coerceIn(0f, 1f) * fade
            val g = games[i]
            val w = baseW * scale
            val hh = baseH * scale
            // Its name, to the left of the cover (right when mirrored) — only as wide as the room between the cover
            // and the column's edge, so it wraps onto two lines / slides instead of running off the screen (small
            // screens like the Retroid Pocket Classic cut the start off). No room at all: no name.
            val nameRoom = if (mirrored) maxWidth - (x + w / 2 + 12.dp) - 8.dp else x - w / 2 - 12.dp - 8.dp
            if (nameRoom >= 36.dp) ScrollingName(
                gameDisplayName(g.name), scrolling = sel > 0.5f,
                color = Color.White.copy(alpha = alpha * (if (sel > 0.5f) 1f else 0.72f)),
                fontSize = (10 + 3 * sel).sp, fontWeight = if (sel > 0.5f) FontWeight.Bold else FontWeight.Medium,
                textAlign = if (mirrored) TextAlign.Start else TextAlign.End,
                modifier = Modifier
                    .offset(x = if (mirrored) x + w / 2 + 12.dp else 8.dp, y = y - 9.dp)
                    .width(nameRoom)
                    .graphicsLayer { this.alpha = if (ad > 6f) 0f else 1f }
            )
            val shape = when (style) {
                Features.HeroCardStyle.ROUNDED -> RoundedCornerShape(6.dp + 3.dp * sel)
                Features.HeroCardStyle.WIDE -> RoundedCornerShape(8.dp)
                else -> RoundedCornerShape(2.dp)
            }
            val flip = if (motion == Features.HeroCarouselMotion.FLIP && sel > 0.5f) 90f * (1f - change.value) else 0f
            // Box art / cartridge / disc art as the real object, free-standing (no card behind it, no frame).
            val gArt = if (wide) com.shelf.launcher.data.CardArtType.COVER else artForGame(cardArtType, g.platform, hiddenMedia, mixed, gameKey = g.stableKey)
            val free = isFreeMedia(gArt, g.platform)
            // Update 203: a cartridge flips as a solid piece (its thickness showing), not as a flat picture (SolidPiece.kt).
            val solidCart = free && motion == Features.HeroCarouselMotion.FLIP && gArt == com.shelf.launcher.data.CardArtType.CARTRIDGE_DISC
            Box(
                Modifier
                    .offset(x = x - w / 2, y = y - hh / 2)
                    .size(w, hh)
                    .graphicsLayer {
                        this.alpha = alpha; shadowElevation = if (free) 0f else 18f * sel; this.shape = shape; clip = !free
                        rotationY = if (solidCart) 0f else flip; cameraDistance = 10f * this.density
                        // "Carousel look": the covers above and below turned away from the selected one, like pages.
                        if (heroCarousel) rotationX = kotlin.math.sign(d) * Carousel.TILT_DEG * abs(d).coerceAtMost(1f)
                        flatTextFor3d()
                    }
                    .then(if (free) Modifier else Modifier.background(placeholderBrush(g.hue)))
                    .then(if (sel > 0.6f && !free && frameOn) Modifier.border(2.dp, ShelfTheme.accent.copy(alpha = 0.85f * sel), shape) else Modifier)
                    .pointerInput(i) {
                        detectTapGestures { if (i == latestFocused) latestOpen(g) else latestSelect(i) }
                    }
            ) {
                val cover = if (wide) wideFor(g) ?: coverFor(g) else coverFor(g)
                if (free) {
                    val typed = typedArtFor(gArt, physicalMedia[g.stableKey], null, g.platform, g.stableKey)
                    // (Update 205: the drawn piece wears its real label scan when it has one.)
                    val labelScan = if (typed == null) drawnLabelScan(physicalMedia[g.stableKey], g.platform) else null
                    val pick = if (gArt == com.shelf.launcher.data.CardArtType.CARTRIDGE_DISC) mediaFor(g.platform, mixed, cardArtType == com.shelf.launcher.data.CardArtType.DISC, gameKey = g.stableKey) else null
                    Box(Modifier.fillMaxSize().awardShine(g.stableKey, sel > 0.6f)) { ProvidePieceTurn(if (solidCart) PieceTurn({ flip }, 10f) else null) {
                        // Update 129: "Real discs": the game's own scan (not a drawn shell with the scan as its label).
                        // (Update 200: the 3D cartridges looks drew their drawn piece whatever the scans. Update 202: "The
                        // game's own photo" decides in every cartridge look, see drawnMediaWanted.)
                        val real = realScanOf(gArt, typed)
                        // (Update 173: an app without art: its icon on the label / in the case.)
                        val art = coverOrAppIcon(g, cover)
                        if (real != null) MediaFocus(v == loopFocus) { RealMediaPicture(real, mediaRound(g.platform), Modifier.fillMaxSize().padding(2.dp), platform = g.platform, gameKey = g.stableKey) }
                        else if (pick != null) MediaFocus(v == loopFocus) { PhysicalMedia(pick.shell, typed ?: labelScan ?: art, Modifier.fillMaxSize().padding(2.dp), platform = pick.platform, labelIsScan = typed != null || labelScan != null, caseCover = art, title = g.name) }
                        else MediaFocus(v == loopFocus) { CaseArt(
                            g.platform, art, typed, Modifier.fillMaxSize().padding(2.dp), title = gameDisplayName(g.name), detail = 240, real = realRef,
                            caseModifier = if (sel > 0.6f && frameOn) Modifier.border(2.dp, ShelfTheme.accent.copy(alpha = 0.85f * sel), RoundedCornerShape(2.dp)) else Modifier
                        ) }
                    } }
                } else if (cover != null) {
                    // (Update 202: the cover with its file's flat strips trimmed — TrimMargins.kt — in the wide slot too.)
                    val req = remember(cover, coverPx, wide) { artRequest(wheelContext, cover, if (wide) ArtKind.WIDE else ArtKind.COVER, coverPx, trim = true) }
                    AsyncImage(model = req, contentDescription = g.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().pixelArtwork())
                }
                else Box(Modifier.fillMaxSize().background(placeholderBrush(g.hue)))
                // Game state mark on the selected cover only (the others are too small for it).
                if (sel > 0.5f) GameStates.byKey[g.stableKey]?.let { gs ->
                    GameStateTag(gs, Modifier.align(Alignment.BottomCenter).padding(3.dp), compact = true)
                }
            }
        }
    }
}

@Composable
private fun InfoRegion(
    info: HeroInfo,
    page: Int,
    categoryLabel: String,
    onPage: (Int) -> Unit,
    onOpenAchievements: (Game) -> Unit,
    onOpenScreenshot: (Game, Int) -> Unit,
    onOpenGuide: (Game) -> Unit,
    onLoadMedia: (Game) -> Unit,
    cover: String?,
    videoUrl: String?,
    modifier: Modifier
) {
    val p = HeroPage.values()[page.mod(HeroPage.values().size)]
    LaunchedEffect(info.game.stableKey, p) {
        if (p == HeroPage.SCREENSHOTS || p == HeroPage.OVERVIEW) { kotlinx.coroutines.delay(400); onLoadMedia(info.game) }
    }
    // The detail pane's layers (cover, screenshots, clip behind the text; the logo replaces the title text).
    DetailLayers(info.game.stableKey, cover, info.screenshots, videoUrl, null, modifier) {
    val layered = Features.detailLayerCover.state.value && cover != null ||
        Features.detailLayerScreenshots.state.value && info.screenshots.isNotEmpty() ||
        Features.detailLayerVideo.state.value && videoUrl != null
    Column(Modifier.fillMaxWidth().padding(if (layered) 14.dp else 0.dp), horizontalAlignment = Alignment.End) {
        // The page: only this part changes when ◀ ▶ are pressed.
        AnimatedContent(
            targetState = p,
            transitionSpec = {
                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (fadeIn(tween(200)) + slideInHorizontally(tween(220)) { it / 10 * dir }) togetherWith
                    (fadeOut(tween(140)) + slideOutHorizontally(tween(160)) { -it / 12 * dir })
            },
            label = "heroPage"
        ) { pg ->
            Box(Modifier.fillMaxWidth().widthIn(max = 620.dp), contentAlignment = Alignment.BottomEnd) {
                when (pg) {
                    HeroPage.OVERVIEW -> OverviewPage(info)
                    HeroPage.ACHIEVEMENTS -> AchievementsPage(info) { onOpenAchievements(info.game) }
                    HeroPage.HISTORY -> HistoryPage(info)
                    HeroPage.SCREENSHOTS -> ScreenshotsPage(info) { i -> onOpenScreenshot(info.game, i) }
                    HeroPage.STATS -> StatsPage(info)
                    HeroPage.GUIDE -> GuidePage(info) { onOpenGuide(info.game) }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        // The small caps line + achievements line, like a console's own home.
        val line = buildList {
            add(info.console.uppercase())
            if (info.playtimeMs >= 60_000L) add("PLAYTIME " + PlaytimeStore.format(info.playtimeMs).uppercase())
        }.joinToString(" · ")
        Text(line, color = ShelfTheme.accent, fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp), fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        if (info.total > 0) {
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "ACHIEVEMENTS " + achievementProgress(info.earned, info.total), color = Color.White.copy(alpha = 0.85f),
                    fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp
                )
                // Update 151: a small upright tick between the count and its bar.
                com.shelf.launcher.ui.ProgressTick(height = 9.dp, gap = 6.dp)
                Box(Modifier.width(64.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.2f))) {
                    Box(Modifier.fillMaxWidth((info.earned.toFloat() / info.total).coerceIn(0f, 1f)).height(3.dp).background(ShelfTheme.accent))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        // The title (its logo when there is one), right-aligned like the reference.
        if (info.logo != null && Features.detailLayerLogo.state.value) {
            FadeInImage(
                model = info.logo, contentDescription = info.title, contentScale = ContentScale.Fit,
                alignment = Alignment.CenterEnd,
                modifier = Modifier.height(54.dp).widthIn(max = 320.dp)
            )
        } else {
            Text(
                info.title, color = Color.White, fontSize = 26.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(26.sp), maxFontSize = 26.sp), fontWeight = FontWeight.Bold, maxLines = 2,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, lineHeight = androidx.compose.ui.unit.TextUnit(1.154f, androidx.compose.ui.unit.TextUnitType.Em)
            )
        }
        if (categoryLabel.isNotBlank()) {
            Text(categoryLabel, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(10.dp))
        // Page names: the current one bright with a short underline. ◀ ▶ / tap.
        val pageTap by rememberUpdatedState(onPage)
        // All six names need ~430 dp: on a narrower info region only the page you're on shows between the
        // arrows (the names used to run off the edge and get cut).
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        if (maxWidth < 440.dp) Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val count = HeroPage.values().size
            Text("◀", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp,
                modifier = Modifier.pointerInput(p) { detectTapGestures { pageTap((p.ordinal - 1).mod(count)) } })
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${p.label}  ${p.ordinal + 1}/$count", color = Color.White, fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp), fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Box(Modifier.width(18.dp).height(2.dp).background(ShelfTheme.accent))
            }
            Text("▶", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp,
                modifier = Modifier.pointerInput(p) { detectTapGestures { pageTap((p.ordinal + 1).mod(count)) } })
        } else
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("◀", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
            HeroPage.values().forEachIndexed { i, hp ->
                val on = hp == p
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.pointerInput(i) { detectTapGestures { pageTap(i) } }
                ) {
                    Text(
                        hp.label, color = if (on) Color.White else Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp), fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Box(Modifier.width(if (on) 18.dp else 0.dp).height(2.dp).background(ShelfTheme.accent))
                }
            }
            Text("▶", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
        }
        }
    }
    }
}

// ---- Pages (one clean region each: text and thin rules, no stacks of cards) ----

@Composable
private fun PageText(text: String, muted: Boolean = false, size: Int = 13, maxLines: Int = 4) =
    Text(
        text, color = if (muted) Color.White.copy(alpha = 0.62f) else Color.White.copy(alpha = 0.92f),
        // (Update 136: a paragraph is never shrunk to fit — it ends in "…" — and its line height follows its font:
        // the shrunk text kept the full size's spacing, with the "…" alone on a line of its own.)
        fontSize = size.sp, autoSize = if (maxLines <= 2) androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = (size.sp) * 0.75f, maxFontSize = size.sp) else null,
        lineHeight = androidx.compose.ui.unit.TextUnit(1.38f, androidx.compose.ui.unit.TextUnitType.Em), maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End
    )

@Composable
private fun OverviewPage(info: HeroInfo) {
    Column(horizontalAlignment = Alignment.End) {
        // The one contextual milestone (journey-aware), else the simple one.
        val m = rememberContextMilestone(info.game.stableKey, info.earned, info.total, info.completion, info.lastPlayed) ?: info.milestone
        m?.let {
            Text(it, color = ShelfTheme.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
        }
        if (info.facts.isNotEmpty()) PageText(info.facts.joinToString(" · "), muted = true, size = 12, maxLines = 1)
        info.summary?.takeIf { it.isNotBlank() }?.let { Spacer(Modifier.height(4.dp)); PageText(it, maxLines = 3) }
        info.lastPlayed?.let {
            Spacer(Modifier.height(4.dp))
            PageText("Last played " + relativeDays(it), muted = true, size = 12, maxLines = 1)
        }
    }
}

private fun relativeDays(t: Long): String {
    val days = ((System.currentTimeMillis() - t) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "today"
        days == 1 -> "yesterday"
        days < 30 -> "$days days ago"
        days < 365 -> "${days / 30} months ago"
        else -> "${days / 365} years ago"
    }
}

private val greyscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

@Composable
private fun AchievementsPage(info: HeroInfo, onOpen: () -> Unit) {
    // The page stays composed while the game changes: a tap must open the game on screen now.
    val open by rememberUpdatedState(onOpen)
    Column(horizontalAlignment = Alignment.End, modifier = Modifier.pointerInput(Unit) { detectTapGestures { open() } }) {
        if (info.achievements.isEmpty()) {
            PageText(if (info.total > 0) achievementProgress(info.earned, info.total) else "No achievement set linked.", muted = true)
        } else {
        val left = info.total - info.earned
        PageText(
            achievementProgress(info.earned, info.total) + when {
                left == 0 -> " · Mastered"
                else -> com.shelf.launcher.data.remainingNote(info.earned, info.total)?.let { " · $it" } ?: ""
            }, size = 14
        )
        Spacer(Modifier.height(8.dp))
        // Newest unlocks first, then the locked ones greyed after them.
        val unlocked = info.achievements.filter { it.isUnlocked }.sortedByDescending { it.dateEarnedHardcore ?: it.dateEarned ?: "" }
        val locked = info.achievements.filter { !it.isUnlocked }.sortedWith(com.shelf.launcher.data.RA_DISPLAY_ORDER)
        // As many badges as fit the info region (twelve needed ~520 dp; the rest were cut off).
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        val fits = ((maxWidth.value + 6f) / 44f).toInt().coerceIn(1, 12)
        val strip = (unlocked + locked).take(fits)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            strip.forEach { a ->
                val new = a.id in info.newIds
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .then(if (new) Modifier.border(2.dp, Ds.gold, RoundedCornerShape(7.dp)) else Modifier)
                        .background(Color.White.copy(alpha = 0.06f))
                ) {
                    AsyncImage(
                        model = RaBadges.url(a.badgeName), contentDescription = a.title, contentScale = ContentScale.Crop,
                        colorFilter = if (a.isUnlocked) null else greyscale, alpha = if (a.isUnlocked) 1f else 0.4f,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        }
        unlocked.firstOrNull()?.let {
            Spacer(Modifier.height(6.dp))
            PageText("Latest: ${it.title ?: "Achievement"}", muted = true, size = 12, maxLines = 1)
        }
        }
    }
}

@Composable
private fun HistoryPage(info: HeroInfo) {
    val context = LocalContext.current
    val sessions by produceState<List<PlaytimeStore.Session>>(emptyList(), info.game.stableKey, info.playtimeMs) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { PlaytimeStore(context).loadSessions().filter { it.key == info.game.stableKey }.sortedByDescending { it.start } }.getOrDefault(emptyList())
        }
    }
    val journey = if (Features.gameJourney.state.value) rememberGameJourney(info.game, info.achievements, info.completion) else emptyList()
    Column(horizontalAlignment = Alignment.End) {
        if (sessions.isEmpty() && journey.isEmpty()) {
            PageText("No sessions recorded yet.", muted = true)
        } else {
        val fmt = remember { java.text.SimpleDateFormat("EEE d MMM · HH:mm", java.util.Locale.getDefault()) }
        if (sessions.isNotEmpty()) {
            PageText("${sessions.size} session${if (sessions.size == 1) "" else "s"} · ${PlaytimeStore.format(info.playtimeMs)} in total", size = 14)
            Spacer(Modifier.height(6.dp))
            sessions.take(if (journey.isEmpty()) 5 else 3).forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(fmt.format(java.util.Date(s.start)), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    Spacer(Modifier.width(14.dp))
                    Text(PlaytimeStore.format(s.ms), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(72.dp))
                }
            }
        }
        // Game Journey: the story so far, its newest steps (the page stays one calm region).
        if (journey.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            GameJourneyTimeline(journey.takeLast(5), Modifier.widthIn(max = 320.dp).fillMaxWidth(), compact = true)
        }
        }
    }
}

@Composable
private fun ScreenshotsPage(info: HeroInfo, onOpen: (Int) -> Unit) {
    if (info.screenshots.isEmpty()) {
        PageText("No screenshots yet.", muted = true)
        return
    }
    val open by rememberUpdatedState(onOpen)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        items(info.screenshots.size) { i ->
            AsyncImage(
                model = info.screenshots[i], contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 176.dp, height = 99.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .pointerInput(i) { detectTapGestures { open(i) } }
            )
        }
    }
}

@Composable
private fun StatsPage(info: HeroInfo) {
    val context = LocalContext.current
    val sessions by produceState<List<PlaytimeStore.Session>>(emptyList(), info.game.stableKey, info.playtimeMs) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { PlaytimeStore(context).loadSessions().filter { it.key == info.game.stableKey } }.getOrDefault(emptyList())
        }
    }
    // Unlocks per month (last six), from the unlock dates.
    val months = remember(info.achievements) {
        val cal = java.util.Calendar.getInstance()
        val keys = (5 downTo 0).map { back ->
            val c = cal.clone() as java.util.Calendar
            c.add(java.util.Calendar.MONTH, -back)
            String.format(java.util.Locale.US, "%04d-%02d", c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1)
        }
        val counts = info.achievements.mapNotNull { (it.dateEarnedHardcore ?: it.dateEarned)?.take(7) }.groupingBy { it }.eachCount()
        keys.map { it to (counts[it] ?: 0) }
    }
    Column(horizontalAlignment = Alignment.End) {
        val avg = if (sessions.isNotEmpty()) sessions.sumOf { it.ms } / sessions.size else 0L
        val longest = sessions.maxOfOrNull { it.ms } ?: 0L
        PageText(
            listOfNotNull(
                "${sessions.size} sessions",
                avg.takeIf { it > 0 }?.let { "avg " + PlaytimeStore.format(it) },
                longest.takeIf { it > 0 }?.let { "longest " + PlaytimeStore.format(it) }
            ).joinToString(" · "), size = 13, maxLines = 1
        )
        if (info.total > 0) {
            Spacer(Modifier.height(3.dp))
            PageText("Completion " + achievementProgress(info.earned, info.total), muted = true, size = 12, maxLines = 1)
        }
        Spacer(Modifier.height(8.dp))
        // Achievement trend: a small bar per month.
        val max = (months.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
        val accent = ShelfTheme.accent
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            months.forEach { (m, n) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(Modifier.size(width = 18.dp, height = 42.dp)) {
                        val hh = size.height * n / max
                        drawRect(Color.White.copy(alpha = 0.10f))
                        drawRect(accent, topLeft = Offset(0f, size.height - hh), size = androidx.compose.ui.geometry.Size(size.width, hh))
                    }
                    Text(monthShort(m), color = Color.White.copy(alpha = 0.55f), fontSize = 9.sp)
                }
            }
        }
        Text("Unlocks per month", color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp)
    }
}

private fun monthShort(key: String): String = runCatching {
    val d = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).parse(key) ?: return key
    java.text.SimpleDateFormat("MMM", java.util.Locale.getDefault()).format(d)
}.getOrDefault(key)

@Composable
private fun GuidePage(info: HeroInfo, onOpen: () -> Unit) {
    val open by rememberUpdatedState(onOpen)
    Column(horizontalAlignment = Alignment.End, modifier = Modifier.pointerInput(Unit) { detectTapGestures { open() } }) {
        val missables = info.achievements.count { it.isMissable && !it.isUnlocked }
        PageText(
            when {
                info.guideUrl != null -> "RetroAchievements guide available"
                else -> "Walkthrough"
            }, size = 14
        )
        Spacer(Modifier.height(4.dp))
        if (missables > 0) PageText("⚠ $missables missable achievement${if (missables == 1) "" else "s"} still open", muted = true, size = 12)
        if (info.tipCount > 0) PageText("${info.tipCount} tips from players", muted = true, size = 12)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.clip(RoundedCornerShape(50)).background(ShelfTheme.accent).padding(horizontal = 14.dp, vertical = 6.dp)
        ) { Text("Open the guide", color = onAccentColor(), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
    }
}

/** Appearance › Game row, with Hero carousel picked. */
@Composable
internal fun HeroCarouselSettings() {
    val F = Features
    MutedNote(
        "The game's hero fills the screen, covers curve down the left. ▲ ▼ change game, ◀ ▶ page through Overview, " +
            "Achievements, Play history, Screenshots, Stats and Guide. L2 / R2 change category, A opens the game."
    )
    Spacer(Modifier.height(6.dp))
    ChipFlow {
        Chip("Dim edges", accent = F.heroDim.state.value) { F.heroDim.toggle() }
        Chip("Blur hero", accent = F.heroBlur.state.value) { F.heroBlur.toggle() }
        Chip("Idle focus mode", accent = F.idleFocusMode.state.value) { F.idleFocusMode.toggle() }
    }
    Spacer(Modifier.height(6.dp))
    SectionLabel("Covers")
    ChipFlow {
        Chip("On the left", accent = !F.heroCoversRight.state.value) { F.heroCoversRight.value = false }
        Chip("On the right", accent = F.heroCoversRight.state.value) { F.heroCoversRight.value = true }
    }
    MutedNote("The game's info sits on the other side, at the bottom (or at the top when \"Where the row sits\" is Top).")
    Spacer(Modifier.height(6.dp))
    SectionLabel("Covers beside the hero")
    ChipFlow {
        Features.HeroSideCovers.values().forEach { v -> Chip(v.label, accent = F.heroSideCovers.state.value == v) { F.heroSideCovers.value = v } }
    }
    Spacer(Modifier.height(6.dp))
    SectionLabel("Card style")
    ChipFlow {
        Features.HeroCardStyle.values().forEach { st -> Chip(st.label, accent = F.heroCardStyle.state.value == st) { F.heroCardStyle.value = st } }
    }
    Spacer(Modifier.height(6.dp))
    SectionLabel("Transition between games")
    ChipFlow {
        Features.HeroCarouselMotion.values().forEach { m -> Chip(m.label, accent = F.heroCarouselMotion.state.value == m) { F.heroCarouselMotion.value = m } }
    }
    MutedNote("Idle focus mode: after 15 s without input only the hero stays; any press brings the rest back.")
    Spacer(Modifier.height(6.dp))
    SectionLabel("Density")
    ChipFlow {
        Features.Density.values().forEach { d -> Chip(d.label, accent = F.density.state.value == d) { F.density.value = d } }
    }
    Spacer(Modifier.height(8.dp))
    OverlayPartsChoice(com.shelf.launcher.data.HomeLayout.HERO)
}
