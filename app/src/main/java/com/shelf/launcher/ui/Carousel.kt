package com.shelf.launcher.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.shelf.launcher.data.ArtOverride
import com.shelf.launcher.data.ConsoleNames
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.GameArt
import com.shelf.launcher.data.GameMetadata
import com.shelf.launcher.data.NowPlaying
import com.shelf.launcher.ui.theme.ShelfTheme
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput

/**
 * One game's art on the shelf (TV room, carousels), filling [modifier] (no empty bands round it):
 * - cartridges / discs / boxes as the objects themselves, standing free with a light pool under the one you're on;
 * - else the console's own cover template with the game's cover in it ("Cases with spines": the template pack's 3D
 *   box seen a little from the side, so the spine shows) — not when the consoles are [mixed];
 * - else the plain cover, filling the slot.
 * The coloured frame (Cards & frames) hugs the case / cover; the glow follows Game row › Effect.
 */
@Composable
internal fun ShelfGameArt(
    g: Game, on: Boolean, cardArtType: com.shelf.launcher.data.CardArtType,
    art: Map<String, GameArt>, overrides: Map<String, ArtOverride>,
    physicalMedia: Map<String, List<Pair<String, String>>>, screenshots: Map<String, List<String>>,
    spines: Boolean, mixed: Boolean, realRef: RealCaseRef?, pulse: Float, modifier: Modifier
) {
    val look = LocalCardLook.current
    val frameOn = on && look.focusBorder
    val glowOn = on && look.focusEffect != com.shelf.launcher.data.FocusEffect.NONE
    val cover = overrides[g.stableKey]?.cover ?: art[g.stableKey]?.gridUrl
    val gArt = artForGame(cardArtType, g.platform, LocalHiddenMedia.current, mixed)
    val frame = Modifier
        .then(if (glowOn) Modifier.softGlow(ShelfTheme.accent, 2.dp, 0.5f + 0.5f * pulse) else Modifier)
        .then(if (frameOn) Modifier.border(2.5.dp, ShelfTheme.accent, RoundedCornerShape(2.dp)) else Modifier)
    val cov = if (!mixed && Features.coverTemplates.state.value) MediaCovers.of(g.platform) else null
    if (isFreeMedia(gArt, g.platform)) {
        val typed = typedArtFor(gArt, physicalMedia[g.stableKey], screenshots[g.stableKey], g.platform)
        Box(modifier.awardShine(g.stableKey, on)) {
            val pick = if (gArt == com.shelf.launcher.data.CardArtType.CARTRIDGE_DISC) mediaFor(g.platform, mixed, cardArtType == com.shelf.launcher.data.CardArtType.DISC) else null
            // Update 129: "Real discs": the game's own scan (not a drawn shell with the scan as its label).
            val real = realScanOf(gArt, typed)
            if (real != null) MediaFocus(on) { RealMediaPicture(real, mediaRound(g.platform), Modifier.fillMaxSize().padding(4.dp)) }
            else if (pick != null) MediaFocus(on) { PhysicalMedia(pick.shell, typed ?: cover, Modifier.fillMaxSize().padding(4.dp), platform = pick.platform, labelIsScan = typed != null, caseCover = cover, title = g.name) }
            else MediaFocus(on) { CaseArt(
                g.platform, cover, typed, Modifier.fillMaxSize().padding(4.dp), title = gameDisplayName(g.name),
                real = realRef, caseModifier = if (frameOn) Modifier.border(2.5.dp, ShelfTheme.accent, RoundedCornerShape(2.dp)) else Modifier
            ) }
            // Update 141: the console's badge on cartridges, discs, cases and 3D models too.
            ConsoleBadge(g.platform, packageName = g.packageName)
        }
    } else if (gArt == com.shelf.launcher.data.CardArtType.CAPSULE || gArt == com.shelf.launcher.data.CardArtType.HERO || gArt == com.shelf.launcher.data.CardArtType.SCREENSHOT) {
        // Update 115: the wide looks as themselves (they used to show the cover in a case).
        val o = overrides[g.stableKey]
        val a = art[g.stableKey]
        val hero = o?.hero ?: a?.heroUrl
        val wide = o?.wide ?: a?.wideGridUrl
        val url = when (gArt) {
            com.shelf.launcher.data.CardArtType.CAPSULE -> wide ?: hero ?: cover
            com.shelf.launcher.data.CardArtType.HERO -> hero ?: wide ?: cover
            else -> screenshots[g.stableKey]?.firstOrNull() ?: hero ?: wide ?: cover
        }
        val shape = RoundedCornerShape(6.dp)
        Box(
            modifier
                .then(if (glowOn) Modifier.softGlow(ShelfTheme.accent, 6.dp, 0.5f + 0.5f * pulse) else Modifier)
                .then(if (frameOn) Modifier.border(2.5.dp, ShelfTheme.accent, shape) else Modifier)
                .clip(shape)
                .background(placeholderBrush(g.hue))
        ) {
            if (url != null) AsyncImage(rememberStyleArtRequest(url), g.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().pixelArtwork()) // (Update 156)
            else Text(gameDisplayName(g.name), color = Color.White, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.Center).padding(6.dp))
            ConsoleBadge(g.platform, packageName = g.packageName)
        }
    } else if (cov != null) {
        val box3d = if (spines) MediaTemplates.of(g.platform)?.box else null
        Box(modifier, contentAlignment = Alignment.Center) {
            if (box3d != null) {
                val (front, isBox) = boxFront(box3d, cover, null)
                Box(Modifier.fillMaxSize().padding(2.dp).awardShine(g.stableKey, on)) {
                    // (Update 117: with the game's own spine on its side when it has one.)
                    val ownSpine = if (Features.originalCases.state.value) BoxTexture.spine(physicalMedia[g.stableKey], g.platform) else null
                    Box3D(box3d, front, isBox, gameDisplayName(g.name), Modifier.fillMaxSize(), spin = false, realSpine = ownSpine)
                }
            } else CoverCase(cov, cover, placeholderBrush(g.hue), Modifier.fillMaxSize().padding(2.dp).awardShine(g.stableKey, on), caseModifier = frame)
            // Update 141: on the console's case too.
            ConsoleBadge(g.platform, packageName = g.packageName)
        }
    } else {
        val shape = RoundedCornerShape(4.dp)
        Box(
            // Plain cover filling the slot (the same size for every game).
            modifier
                .then(if (glowOn) Modifier.softGlow(ShelfTheme.accent, 4.dp, 0.5f + 0.5f * pulse) else Modifier)
                .then(if (frameOn) Modifier.border(2.5.dp, ShelfTheme.accent, shape) else Modifier)
                .clip(shape)
                .background(placeholderBrush(g.hue))
        ) {
            if (cover != null) AsyncImage(
                rememberStyleArtRequest(cover), g.name, contentScale = ContentScale.Crop, // (Update 156: the warmed shape) modifier = Modifier.fillMaxSize().pixelArtwork(),
                // (Update 154: its shape noted, so the carousel's slot takes it — CoverAspects.)
                onSuccess = { r -> r.painter.intrinsicSize.let { sz -> CoverAspects.note(cover, sz.width, sz.height) } }
            )
            else if (g.sourceType == com.shelf.launcher.data.SourceType.APP) AppIconArt(g.packageName, wide = false, modifier = Modifier.fillMaxSize().padding(10.dp))
            else Text(gameDisplayName(g.name), color = Color.White, fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp), maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.Center).padding(6.dp))
            // Case gloss.
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent, Color.Transparent), start = Offset.Zero, end = Offset(size.width, size.height)))
            }
            // Update 115: the console's badge.
            ConsoleBadge(g.platform, packageName = g.packageName)
        }
    }
}

/** The width / height a game's art takes on the shelf (its case's front, its cartridge / disc, else a cover's shape). */
internal fun shelfArtAspect(g: Game, cardArtType: com.shelf.launcher.data.CardArtType, hidden: Set<String>, mixed: Boolean, cover: String? = null): Float {
    // Update 115: the wide looks at their own shape (they used to be cropped into a cover's).
    if (cardArtType == com.shelf.launcher.data.CardArtType.CAPSULE) return CAPSULE_ASPECT
    if (cardArtType == com.shelf.launcher.data.CardArtType.HERO || cardArtType == com.shelf.launcher.data.CardArtType.SCREENSHOT) return 16f / 9f
    val gArt = artForGame(cardArtType, g.platform, hidden, mixed)
    if (isFreeMedia(gArt, g.platform)) {
        if (gArt == com.shelf.launcher.data.CardArtType.CARTRIDGE_DISC) return mediaFor(g.platform, mixed, cardArtType == com.shelf.launcher.data.CardArtType.DISC)?.aspect ?: 0.7f
        return MediaCovers.of(g.platform)?.frontAspect ?: 0.7f
    }
    if (!mixed && Features.coverTemplates.state.value) MediaCovers.of(g.platform)?.let { return it.frontAspect }
    // Update 154: a plain cover at its own shape once it has loaded (it was cut to 0.7 — tops, bottoms and sides of
    // square GBA boxes, wide PC covers and tall posters were cut off).
    return CoverAspects.of(cover) ?: 0.7f
}

/** Update 154: the shape (width / height) of each plain cover the carousel has loaded, so its slot fits it whole. */
internal object CoverAspects {
    private val map = androidx.compose.runtime.mutableStateMapOf<String, Float>()
    fun of(url: String?): Float? = url?.let { map[it] }
    fun note(url: String, w: Float, h: Float) {
        if (w <= 0f || h <= 0f || w.isInfinite() || h.isInfinite()) return
        val a = (w / h).coerceIn(0.5f, 1.25f)
        val old = map[url]
        if (old == null || kotlin.math.abs(old - a) > 0.01f) map[url] = a
    }
}

/**
 * The carousel look (TV room › Carousel, and "Carousel look" for the row, tiles, grid and Hero carousel): how far out,
 * how small and how turned a game sits for its distance [d] (in games, may be fractional) from the one in the middle.
 * Offsets are in widths of the middle game.
 */
/** Carousel tilt: which way the games aside turn. */
enum class TiltDir(val label: String) { OUT("Outward"), IN("Inward") }

/** Carousel tilt: which games turn. */
enum class TiltSides(val label: String) { BOTH("Both sides"), LEFT("Left only"), RIGHT("Right only") }

/** How the carousel lays the games out (update 104, Settings › Carousel look › View). */
enum class CarouselView(val label: String, /** Update 134: always drawn as 3D cases. */ val cases: Boolean = false) {
    /** The game you're on big, the next ones smaller and tucked behind it (as before). */
    TUCKED("Tucked behind"),
    /** The ones aside packed close and turned steeply towards the middle, like flicking through records. */
    FLIP("Record flip"),
    /** Round an arc, like a dial: the ones aside drop and lean along the curve. */
    WHEEL("Wheel"),
    /** A deck: the next ones stacked just behind the one you're on, the ones before slide off to the left. */
    DECK("Deck"),
    /** Flat, evenly spaced, the one you're on bigger. */
    FLAT("Flat row"),
    /** Update 136: the games aside at their normal size, side by side; only the one you're on pops out a little. */
    POP("Even row"),
    /** Update 134: 3D cases fanned round an arc, turned towards the middle, the far ones smaller and higher. */
    ARC("Arc", true),
    /** Update 134: the one you're on facing you, the others turned to their spines and packed tight, like a shelf. */
    SPINE_WALL("Spine wall", true),
    /** Update 134: a flat row of 3D cases all turned a little, so each one's spine shows, the name above. */
    TURNED("Turned row", true),
    /**
     * Update 146: like a dashboard: the game you're on big near the left edge, the next ones to its right getting
     * smaller and turned a little away, stepping back into the distance; the ones before slide off to the left.
     */
    RECEDE("Receding row")
}

internal object Carousel {
    /** Games each side (1–5, Settings and the R1 Carousel tile); "To the edge" (update 119): as many as reach the screen's edges. */
    val side: Int get() = if (Features.carouselEdge.state.value) EDGE_SIDE else Features.tvCarouselSide.state.value.coerceIn(1, 5)
    /** "To the edge": enough games each side to fill past the screen's edges (the ones off screen aren't seen). */
    const val EDGE_SIDE = 12
    /** The games aside turned away from the middle one. */
    val tilt: Boolean get() = Features.tvCarouselTilt.state.value
    /** Turn of a game one step out (degrees). */
    const val TILT_DEG = 38f

    // (Update 129: "To the edge" keeps the games aside bigger — they looked small.)
    fun sideScale(side: Int): Float = when (side) { 1 -> 0.78f; 2 -> 0.7f; 3 -> 0.62f; 4 -> 0.57f; 5 -> 0.53f; else -> 0.68f }

    /** Size against the middle game. */
    fun scale(d: Float, side: Int): Float {
        val near = d.coerceAtMost(1f); val far = (d - 1f).coerceAtLeast(0f)
        // (To the edge: the far ones shrink more gently, so the row reaches the edges at a size you can still see.)
        return (1f - (1f - sideScale(side)) * near) * Math.pow(if (side >= EDGE_SIDE) 0.95 else 0.86, far.toDouble()).toFloat()
    }

    /** Centre of the game from the middle one's centre, in middle-game widths (always ≥ 0; mirror it for the left). */
    fun offset(d: Float, side: Int): Float {
        val s = sideScale(side)
        val near = d.coerceAtMost(1f); val far = (d - 1f).coerceAtLeast(0f)
        // One step out: tucked about a third of its width behind the middle game; further ones closer still.
        return (0.5f + s * 0.2f) * near + s * (if (side >= EDGE_SIDE) 0.62f else 0.5f) * far
    }

    /** Shown at all: only [side] games each side (the next one fades in / out as it moves). */
    fun alpha(d: Float, side: Int): Float = when {
        d <= side -> 1f - 0.12f * d.coerceAtMost(1f)
        d >= side + 0.75f -> 0f
        else -> (1f - (d - side) / 0.75f) * 0.88f
    }

    /** Darkening over the games aside: they sit in the middle one's shadow. */
    fun shade(d: Float): Float = (0.22f * d.coerceAtMost(1f) + 0.12f * (d - 1f).coerceAtLeast(0f)).coerceAtMost(0.5f)

    /**
     * Turn (rotationY) for a game at signed distance [rel] (negative = left of the middle one): turned away from
     * the middle like pages (outward) or towards it (inward), on both sides or only the left / right one.
     */
    fun turn(rel: Float): Float {
        if (!tilt) return 0f
        when (Features.tvCarouselTiltSides.state.value) {
            TiltSides.LEFT -> if (rel > 0f) return 0f
            TiltSides.RIGHT -> if (rel < 0f) return 0f
            else -> {}
        }
        val dir = if (Features.tvCarouselTiltDir.state.value == TiltDir.IN) 1f else -1f
        return dir * kotlin.math.sign(rel) * TILT_DEG * kotlin.math.abs(rel).coerceAtMost(1f)
    }

    /** Drawn above the farther ones. */
    fun z(d: Float): Float = 100f - d * 10f

    val view: CarouselView get() = Features.carouselView.state.value
    /** Update 134: the games drawn as real 3D cases (the "3D cases" switch, and always in Arc, Spine wall and Turned row). */
    val cases3d: Boolean get() = Features.carousel3d.state.value || view.cases
    /** How many games show each side for the [shown] view: the Spine wall packs three times as many (its spines are slim). */
    fun reach(side: Int, shown: CarouselView = view): Int = if (shown == CarouselView.SPINE_WALL && side < EDGE_SIDE) side * 3 else side
    /** Spacing between the games (Settings, 60–160 %). */
    val gap: Float get() = Features.carouselGap.state.value / 100f

    /** Update 135: how tall a spine aside is against the game you're on (they used to be nearly as tall as it). */
    const val SPINE_SIZE = 0.66f
    /** Size of a spine aside at distance [d]: from the middle game's size down to [SPINE_SIZE], then barely smaller. */
    fun spineScale(d: Float): Float {
        val near = d.coerceAtMost(1f); val far = (d - 1f).coerceAtLeast(0f)
        return (1f - (1f - SPINE_SIZE) * near) * (1f - 0.008f * far).coerceAtLeast(0.85f)
    }

    /**
     * Where a game at signed distance [rel] sits for the [view]: x offset and y offset (in middle-game widths /
     * heights), size, turn round its upright (Y), lean (Z), how much shows and darkening.
     */
    class Place(val x: Float, val y: Float, val scale: Float, val turnY: Float, val turnZ: Float, val alpha: Float, val shade: Float, val z: Float)

    /**
     * Update 137: [place] for the carousels of flat covers (the Voltcap row, Tiles): the 3D-case views, which need real
     * cases, take their nearest flat view there (Arc → Wheel, Spine wall → Record flip, Turned row → Flat row).
     */
    fun placeFlat(rel: Float, side: Int): Place = place(rel, side, flatView)

    /**
     * Update 137: how many games each side a carousel of flat covers (Voltcap row, Tiles) has to draw so they pass the
     * screen's edge — "To the edge" packs them much closer than a plain row, so the row's own count fell short of it.
     * [bigW]: the middle game's width, [half]: half the screen's width (same unit).
     */
    fun flatReach(bigW: Float, half: Float): Int {
        val s = side
        val r = reach(s, flatView)
        for (k in 1..r) {
            val p = placeFlat(k.toFloat(), s)
            if (p.alpha <= 0f) return k
            if (kotlin.math.abs(p.x) * bigW - bigW * p.scale / 2f > half) return k + 1
        }
        return r + 1
    }

    val flatView: CarouselView get() = when (view) {
        CarouselView.ARC -> CarouselView.WHEEL
        CarouselView.SPINE_WALL -> CarouselView.FLIP
        CarouselView.TURNED -> CarouselView.FLAT
        else -> view
    }

    fun place(rel: Float, side: Int, shown: CarouselView = view): Place {
        val d = kotlin.math.abs(rel)
        val sign = if (rel < 0f) -1f else 1f
        val near = d.coerceAtMost(1f)
        val far = (d - 1f).coerceAtLeast(0f)
        val g = gap
        return when (shown) {
            CarouselView.TUCKED -> Place(offset(d, side) * sign * g, 0f, scale(d, side), turn(rel), 0f, alpha(d, side), shade(d), z(d))
            CarouselView.FLIP -> Place(
                sign * ((0.7f * near + 0.22f * far) * g), 0f, 1f - 0.16f * near - 0.04f * far,
                // Turned steeply towards the middle (or away with the tilt set outward).
                (if (Features.tvCarouselTiltDir.state.value == TiltDir.OUT) -1f else 1f) * sign * 62f * near, 0f,
                alpha(d, side + 1), shade(d) * 0.8f, z(d)
            )
            CarouselView.WHEEL -> {
                val a = (rel * 0.45f * g).coerceIn(-1.5f, 1.5f)
                Place(
                    kotlin.math.sin(a) * 1.9f, (1f - kotlin.math.cos(a)) * 0.9f, 1f - 0.12f * d.coerceAtMost(3f),
                    0f, a * 57.3f * 0.55f, alpha(d, side), shade(d) * 0.7f, z(d)
                )
            }
            CarouselView.DECK -> if (rel >= 0f) Place(
                0.09f * rel * g, -0.045f * rel, 1f - 0.07f * rel, 0f, 0f,
                alpha(d, side), shade(d), z(d)
            ) else Place(
                // Past ones slide off to the left and fade.
                -1.15f * d * g, 0f, 1f - 0.05f * d, -18f * near, 0f,
                (1f - d * 1.4f).coerceIn(0f, 1f), 0f, 101f
            )
            CarouselView.FLAT -> Place(
                sign * (near * 0.97f + far * 0.86f) * g, 0f, if (d < 1f) 1f - 0.22f * d else 0.78f,
                0f, 0f, alpha(d, side), shade(d) * 0.6f, z(d)
            )
            CarouselView.POP -> Place(
                // Side by side with a small gap: the middle one a quarter bigger, the rest all the same size, unshaded.
                sign * (0.95f * near + 0.85f * far) * g, 0f, 1f - 0.2f * near,
                0f, 0f, if (d <= side) 1f else alpha(d, side), shade(d) * 0.25f, z(d)
            )
            CarouselView.ARC -> {
                // Round a wide arc: each turned to face its middle (the left ones show their spines).
                val a = (rel * 0.38f * g).coerceIn(-1.4f, 1.4f)
                val back = 1f - kotlin.math.cos(a)
                Place(
                    kotlin.math.sin(a) * 2.4f, -back * 0.25f, (1f - 0.1f * near) / (1f + 1.1f * back),
                    -a * 57.3f * 0.85f, 0f, alpha(d, side), shade(d) * 0.6f, z(d)
                )
            }
            CarouselView.SPINE_WALL -> Place(
                // Turned almost to their spines (Update 147: 72°, a sliver of each front showing, as on a disc loader's
                // case wall), packed close in a stack each side.
                sign * (0.62f * near + 0.125f * far) * g, 0f, if (d < 1f) 1f - 0.1f * d else 0.9f,
                72f * near, 0f, alpha(d, reach(side, shown)), shade(d) * 0.5f, z(d)
            )
            CarouselView.TURNED -> Place(
                // Update 147: the ones aside close to the middle one, a little smaller and turned to face it (the left
                // ones turned right, the right ones left), like a disc loader's cover row; the one you're on straight on.
                sign * (near * 0.8f + far * 0.62f) * g, 0f, if (d < 1f) 1f - 0.2f * d else 0.8f,
                -sign * 26f * near, 0f, alpha(d, side), shade(d) * 0.5f, z(d)
            )
            CarouselView.RECEDE -> if (rel >= 0f) {
                // Update 146: each next game a step further back: smaller by the same share each step, its left edge just
                // under the one before (a little overlap), centred a bit higher so the row runs off to a point above the
                // middle, and turned a little away like a wall seen from its left end.
                val sc = if (d <= 1f) 1f - (1f - RECEDE_NEXT) * d else RECEDE_NEXT * Math.pow(RECEDE_SHRINK.toDouble(), far.toDouble()).toFloat()
                val back = RECEDE_NEXT * 0.82f * (1f - Math.pow(RECEDE_SHRINK.toDouble(), far.toDouble()).toFloat()) / (1f - RECEDE_SHRINK)
                Place(
                    (0.76f * near + back) * g, -(1f - sc) * 0.3f, sc,
                    24f * near, 0f, alpha(d, side), shade(d) * 0.8f, z(d)
                )
            } else Place(
                // The ones before slide off past the left edge and fade (drawn over the one coming in, like the Deck).
                -1.1f * d * g, 0f, 1f - 0.08f * near, 0f, 0f,
                (1f - d * 1.25f).coerceIn(0f, 1f), 0f, z(d) + 0.5f
            )
        }
    }

    /** Update 146, Receding row: the first game after the one you're on, against it. */
    const val RECEDE_NEXT = 0.62f
    /** Update 146, Receding row: each game after that this much smaller than the one before. */
    const val RECEDE_SHRINK = 0.86f
}

/**
 * Like a console dashboard's game carousel: the game you're on big in the middle, lit up, and the next ones each
 * side, smaller and set behind it (Settings › Games each side), optionally turned away like pages ("Tilt the other
 * games"). Used by TV room › Carousel and by the Grid's "Carousel look". ◀ ▶ move, A opens. [showName]: the
 * game's name under the one in the middle.
 */
@Composable
internal fun GameCarousel(
    games: List<Game>, focus: Int, art: Map<String, GameArt>, overrides: Map<String, ArtOverride>,
    onSelect: (Int) -> Unit, onOpen: () -> Unit,
    cardArtType: com.shelf.launcher.data.CardArtType,
    physicalMedia: Map<String, List<Pair<String, String>>>, screenshots: Map<String, List<String>>,
    onNeedPhysical: (Game) -> Unit,
    modifier: Modifier = Modifier,
    showName: Boolean = false,
    /** Update 135: Endless (R1) on — the games go round, so both sides are always filled (even on the first game). */
    loop: Boolean = false,
    /** The view model's D-pad step count and direction, for the endless place ([rememberLoopFocus]). */
    loopTick: Int = 0,
    loopStep: Int = 0,
    /** Update 146: time played per game (stableKey → ms), for the Receding row's line under its big game. */
    playtime: Map<String, Long> = emptyMap()
) {
    // Update 156: the covers a few games either side decoded ahead, in the shape the carousel draws them (the Grid and TV
    // room carousels warmed nothing: each new cover came in empty for a moment).
    val warmCtx = androidx.compose.ui.platform.LocalContext.current.applicationContext
    androidx.compose.runtime.LaunchedEffect(focus, games) {
        kotlinx.coroutines.delay(120)
        val n = games.size
        if (n == 0) return@LaunchedEffect
        val reach = if (com.shelf.launcher.device.MemoryBudget.small(warmCtx)) 2 else 3
        val urls = ((focus - reach)..(focus + reach)).mapNotNull { i ->
            val g = games.getOrNull(if (loop) i.mod(n) else i) ?: return@mapNotNull null
            overrides[g.stableKey]?.cover ?: art[g.stableKey]?.gridUrl
        }.distinct()
        prefetchThumbs(warmCtx, urls)
        warmStyleArt(warmCtx, urls)
    }
    val spines = spinesOn(cardArtType)
    val view = Carousel.view
    // Update 141: every carousel view works with every look. The views made for 3D cases (Arc, Spine wall, Turned row)
    // draw the 3D cases only for the case looks (covers, real cases); with cartridges, discs, wide capsules, pictures or
    // spines they lay out that look itself, turned the same way. The "3D cases" switch still turns every look into cases.
    val caseLook = !spines && (cardArtType == com.shelf.launcher.data.CardArtType.COVER || cardArtType == com.shelf.launcher.data.CardArtType.CASE ||
        cardArtType == com.shelf.launcher.data.CardArtType.CASE_REAL)
    val cases3d = Features.carousel3d.state.value || (view.cases && caseLook)
    // Update 141: with 3D models on, the cartridges & discs look draws each game as its 3D model in every view, at its turn.
    val modelLook = Models3D.on() && cardArtType == com.shelf.launcher.data.CardArtType.CARTRIDGE_DISC
    // Update 135: the games aside drawn as slim spines in a row (Spines look, or Record flip turning covers to their
    // spines), in the views that lay the games out in a row (Wheel and Deck keep their own places).
    val spinesAside = !cases3d && (view == CarouselView.TUCKED || view == CarouselView.FLIP || view == CarouselView.FLAT || view == CarouselView.POP) && (spines || (view == CarouselView.FLIP &&
        (cardArtType == com.shelf.launcher.data.CardArtType.COVER || cardArtType == com.shelf.launcher.data.CardArtType.CASE || cardArtType == com.shelf.launcher.data.CardArtType.CASE_REAL)))
    // Update 146: the Receding row's next games as spines (Spines look) packed one after another to the right.
    val recede = view == CarouselView.RECEDE
    val spinesRight = recede && spines && !cases3d
    // Spines are slim: three times as many each side (like the Spine wall), so they reach the screen's edges.
    val side = Carousel.side.let { if ((spinesAside || spinesRight) && it < Carousel.EDGE_SIDE) it * 3 else it }
    val n = games.size
    val looping = loop && n >= 2
    // The place on the endless row (just [focus] when it doesn't go round).
    val vFocus = rememberLoopFocus(focus, n, looping, loopTick, loopStep, games)
    LaunchedEffect(focus, cardArtType, games.size, cases3d) {
        // (Update 134: the 3D cases want the games' spine scans too.)
        if (!cardArtType.wantsMedia() && !cases3d) return@LaunchedEffect
        kotlinx.coroutines.delay(120)
        for (i in intArrayOf(focus, focus + 1, focus - 1, focus + 2, focus - 2, focus + 3, focus - 3)) {
            val g = (if (looping) games.getOrNull(i.mod(n)) else games.getOrNull(i)) ?: continue
            if (!physicalMedia.containsKey(g.stableKey)) { onNeedPhysical(g); kotlinx.coroutines.delay(90) }
        }
    }
    GridHome.columns = 1
    GridHome.focus = focus
    val select by rememberUpdatedState(onSelect)
    val open by rememberUpdatedState(onOpen)
    val look = LocalCardLook.current
    val pulse = rememberFocusPulse(animate = look.focusEffect != com.shelf.launcher.data.FocusEffect.NONE && !LocalLiteEffects.current)
    val hidden = LocalHiddenMedia.current
    val mixed = androidx.compose.runtime.remember(games) { mixedConsoles(games) }
    val realRef = androidx.compose.runtime.remember(games, cardArtType) { realCaseRef(cardArtType, games) }
    // Where the carousel is (in games): slides smoothly between them.
    // (Update 141: as quick as Settings › Home › Animations › Moving between games says.)
    val glideMs = Features.rowGlide.state.value.carouselMs
    val pos by animateFloatAsState(vFocus.toFloat(), tween(glideMs, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "carousel")
    val dens = androidx.compose.ui.platform.LocalDensity.current.density
    val latestFocus by rememberUpdatedState(focus)
    val latestLoop by rememberUpdatedState(looping)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    // Update 134: Turned row has the name above the games.
    val nameAbove = view == CarouselView.TURNED
    if (showName && nameAbove) games.getOrNull(focus)?.let { g -> CarouselName(g, Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp)) }
    BoxWithConstraints(
        Modifier.fillMaxWidth().weight(1f).pointerInput(games.size) {
            // Swipe: one game per ~70 dp.
            var acc = 0f
            detectHorizontalDragGestures(onDragEnd = { acc = 0f }) { change, dx ->
                change.consume()
                acc += dx
                val step = 70.dp.toPx()
                // (update 91) Counted from a local target: latestFocus only changes after a recomposition, so a
                // fast swipe over several games used to move just one.
                var target = latestFocus
                // (Update 135: with Endless on, a swipe goes round past the first / last game too.)
                while (acc <= -step) { acc += step; target = if (latestLoop) (target + 1).mod(games.size) else (target + 1).coerceAtMost(games.lastIndex); select(target) }
                while (acc >= step) { acc -= step; target = if (latestLoop) (target - 1).mod(games.size) else (target - 1).coerceAtLeast(0); select(target) }
            }
        }
    ) {
        // The one in the middle: as tall as fits, never wider than ~half the width so the others still show.
        // (Update 129: times the carousel's Game size, never taller than the space.)
        val sizeK = Features.carouselSize.state.value / 100f
        val bigW = minOf(maxHeight * 0.84f * 0.72f * sizeK, maxHeight * 0.97f * 0.72f, maxWidth * 0.5f * sizeK)
        val bigH = bigW / 0.72f
        val reach = Carousel.reach(side, view)
        // Update 147: the dark stage (on with the 3D case views): the background fades to black towards the floor the
        // games stand on, a faint gloss line along it, and stays dark below (drawn past the bottom of the carousel).
        if (Features.carouselStage.state.value) {
            val floorK = 0.5f + (bigH / maxHeight) / 2f - (if (Features.carouselReflect.state.value) (bigH * 0.08f) / maxHeight else 0f)
            androidx.compose.foundation.Canvas(Modifier.matchParentSize().zIndex(-1000f)) {
                val floorY = size.height * floorK
                val below = size.height * 1.5f
                drawRect(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0f), 0.35f to Color.Black.copy(alpha = 0.55f), 1f to Color.Black.copy(alpha = 0.9f),
                        startY = -size.height * 0.25f, endY = floorY
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, -size.height * 0.25f),
                    size = androidx.compose.ui.geometry.Size(size.width, floorY + size.height * 0.25f)
                )
                drawRect(
                    Color.Black.copy(alpha = 0.92f), topLeft = androidx.compose.ui.geometry.Offset(0f, floorY),
                    size = androidx.compose.ui.geometry.Size(size.width, below)
                )
                // The glossy floor: a soft sheen just under the line the games stand on.
                drawRect(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.07f), Color.Transparent), startY = floorY, endY = floorY + size.height * 0.22f
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, floorY),
                    size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.22f)
                )
                drawLine(
                    Color.White.copy(alpha = 0.12f), androidx.compose.ui.geometry.Offset(0f, floorY),
                    androidx.compose.ui.geometry.Offset(size.width, floorY), strokeWidth = 1.5f
                )
            }
        }
        // Update 146: the Receding row spaces its games by the big one's real width: a wide capsule or a round disc is
        // wider than a cover, and spaced as covers they piled up. Glides to the next game's width with the carousel.
        val widen = recede
        // (No room yet — a 0-high box while the screen opens: 1, not 0 / 0 = NaN, which crashes the offset.)
        val unitTarget = games.getOrNull(focus)?.takeIf { bigW > 0.dp }?.let { fg ->
            val a = if (cases3d) case3dAspect(fg, mixed) else shelfArtAspect(fg, cardArtType, hidden, mixed)
            val sh = minOf(bigH, if (a > 1f) bigW / a * 1.25f else bigH)
            ((sh * a) / bigW).coerceIn(0.6f, 1.8f)
        } ?: 1f
        val unitAnim by animateFloatAsState(unitTarget, tween(glideMs, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "carouselUnit")
        val unitK = if (widen) unitAnim else 1f
        // Update 146: the Receding row's big game sits near the left edge (its left side ~7 % in), not in the middle.
        val recedeLeft = (maxWidth * 0.07f).coerceAtLeast(12.dp)
        val anchorX = if (recede) -maxWidth / 2 + recedeLeft + bigW * unitK / 2 else 0.dp
        val first = kotlin.math.floor(pos).toInt() - reach - 1
        val last = kotlin.math.ceil(pos).toInt() + reach + 1
        for (i in first..last) {
            // (Update 135: on an endless row item i is a place on the endless row; its game is i mod the count.)
            val real = if (looping) i.mod(n) else i
            val g = games.getOrNull(real) ?: continue
            val rel = i - pos
            val d = kotlin.math.abs(rel)
            val p = Carousel.place(rel, side)
            if (p.alpha <= 0f) continue
            val sign = if (rel < 0f) -1f else 1f
            // Update 113, Spines look: the games aside as their spines (closer together), the middle one its cover.
            // Update 117: a game turned almost side-on (Record flip) shows its real spine, facing you, instead of a squashed cover.
            val sideways = !cases3d && !spines && i != vFocus && kotlin.math.abs(p.turnY) > 50f &&
                (cardArtType == com.shelf.launcher.data.CardArtType.COVER || cardArtType == com.shelf.launcher.data.CardArtType.CASE || cardArtType == com.shelf.launcher.data.CardArtType.CASE_REAL)
            val spineHere = !cases3d && ((spines && i != vFocus) || sideways)
            // Update 136: with 3D models on, the 3D cases are your 3D shape library's pieces, at the angle nearest their turn.
            // (Update 146: worked out first — a 3D model is drawn instead of a spine, so it isn't packed as one.)
            // (Update 149: with 3D cases — Arc, Spine wall, Turned row — only your library's real 3D *cases*; a console
            // without one gets the drawn case with its cover, not its cartridge: cartridges can't turn far enough to their
            // side and stood wide over the middle game.)
            // (Update 151: your library's 3D cases only with the 3D models look; the case looks' 3D cases are all drawn ones.)
            val model = if (cases3d) (if (modelLook) Models3D.caseModel(g.platform) else null)
                else if (modelLook && isFreeMedia(artForGame(cardArtType, g.platform, hidden, mixed), g.platform)) Models3D.available(g.platform) else null
            val near = d.coerceAtMost(1f)
            val far = (d - 1f).coerceAtLeast(0f)
            // Update 146: Receding row, Spines look: the next games as spines one after another to its right, stepping back.
            val recedeSpine = spinesRight && spineHere && model == null && rel > 0f
            // Update 135: spines aside sit lower than the game you're on (they were nearly as tall as it), all the same
            // height like books on a shelf, packed close one after another from just beside its cover.
            val scale = if (recedeSpine || (spineHere && spinesAside)) Carousel.spineScale(d) else p.scale
            val x = if (recedeSpine || (spineHere && spinesAside)) {
                val spineW = bigH * Carousel.SPINE_SIZE * SPINE_ASPECT
                val firstX = bigW * unitK * (0.5f + 0.07f * Carousel.gap) + spineW / 2
                val stepX = spineW + bigW * 0.03f * Carousel.gap
                anchorX + (firstX * near + stepX * far) * sign
            } else anchorX + bigW * unitK * p.x * (if (spines && !cases3d && !recede) 0.62f else 1f)
            // (Update 146: the Receding row's spines step up towards its vanishing point like its covers do.)
            val yK = if (recedeSpine) -(1f - scale) * 0.3f else p.y
            val reflect = Features.carouselReflect.state.value
            val aspect = if (model != null) Models3D.aspect(model) else if (cases3d) case3dAspect(g, mixed) else if (spineHere) SPINE_ASPECT else shelfArtAspect(g, cardArtType, hidden, mixed, overrides[g.stableKey]?.cover ?: art[g.stableKey]?.gridUrl)
            // Slot in the item's own shape (no empty bands round it), as tall as the middle one × its scale.
            val h0 = minOf(bigH, if (aspect > 1f) bigW / aspect * 1.25f else bigH)
            val w0 = h0 * aspect
            val on = i == vFocus
            androidx.compose.runtime.key(if (looping) i else g.stableKey) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        // (Update 135: a spine never covers the game you're on while the carousel slides.)
                        .zIndex(if (spineHere) p.z - 50f else p.z)
                        .offset(x = x, y = bigH * yK - (if (reflect) bigH * 0.08f else 0.dp))
                        .size(w0, h0)
                        .graphicsLayer {
                            scaleX = scale; scaleY = scale
                            // (Update 134: a 3D case turns itself, in its own drawing.)
                            // Update 144: the Spine wall turns the games aside a full 90°, which draws flat art edge-on
                            // (gone). A spine already faces you there; any other flat look turns at most 78°, like the preview.
                            rotationY = if (sideways || cases3d || model != null || (spineHere && view == CarouselView.SPINE_WALL)) 0f
                                else p.turnY.coerceIn(-78f, 78f)
                            rotationZ = p.turnZ
                            cameraDistance = 12f * dens
                            this.alpha = p.alpha
                            // (Its reflection is drawn outside its slot: faded op by op, not through a slot-sized layer.)
                            if (cases3d || model != null) compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
                            if (rotationY != 0f) flatTextFor3d()
                        }
                        .pointerInput(real) { detectTapGestures { if (real == GridHome.focus) open() else select(real) } }
                        .then(if (on) Modifier.focusedCard() else Modifier)
                ) {
                    if (model != null) Model3DArt(
                        model, overrides[g.stableKey]?.cover ?: art[g.stableKey]?.gridUrl, p.turnY, Modifier.fillMaxSize(), shade = p.shade, name = g.name, platform = g.platform,
                        // Update 151: its shadow, sheen and slow turn (Models3DLife.kt).
                        life = true, focused = on
                    ) else if (cases3d) Case3D(
                        g, overrides[g.stableKey]?.cover ?: art[g.stableKey]?.gridUrl, physicalMedia[g.stableKey], p.turnY, aspect,
                        Modifier.fillMaxSize(), shade = p.shade, reflect = reflect,
                        frame = on && look.focusBorder, frameColor = ShelfTheme.accent
                    ) else CarouselArt(g, on, spineHere, reflect, h0, p.shade, cardArtType, art, overrides, physicalMedia, screenshots, mixed, realRef, pulse)
                }
            }
        }
    }
    // Update 146: the Receding row's name (and time played) sits under its big game at the left, lined up with it.
    if (showName && recede) games.getOrNull(focus)?.let { g ->
        RecedeName(g, playtime[g.stableKey]?.takeIf { it > 0L && look.playtimeOnHome }, Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp))
    } else if (showName && !nameAbove) games.getOrNull(focus)?.let { g -> CarouselName(g, Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp)) }
    }
}

/**
 * Update 146, Receding row: the big game's name, left-aligned under it (its left edge ~7 % in, as in [GameCarousel]),
 * and its time played under that when there is some.
 */
@Composable
private fun RecedeName(g: Game, playedMs: Long?, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        Column(Modifier.padding(start = (maxWidth * 0.07f).coerceAtLeast(12.dp), end = 12.dp).fillMaxWidth(0.62f)) {
            ScrollingName(
                gameDisplayName(g.name), scrolling = true, color = ShelfTheme.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                lines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                style = nameTextStyle(com.shelf.launcher.data.NameBackdrop.SHADOW),
                modifier = Modifier.fillMaxWidth()
            )
            if (playedMs != null) Text(
                // (The same short time as the other "Played …" lines: "Played <1 min", not "Played Under a minute".)
                "Played " + com.shelf.launcher.data.PlaytimeStore.format(playedMs), color = ShelfTheme.colors.textPrimary.copy(alpha = 0.75f), fontSize = 12.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, style = nameTextStyle(com.shelf.launcher.data.NameBackdrop.SHADOW)
            )
        }
    }
}

/** The carousel's name line (the game in the middle). */
@Composable
private fun CarouselName(g: Game, modifier: Modifier) {
    ScrollingName(
        gameDisplayName(g.name), scrolling = true, color = ShelfTheme.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold,
        lines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        style = nameTextStyle(com.shelf.launcher.data.NameBackdrop.SHADOW),
        modifier = modifier
    )
}

/** One carousel game drawn flat (its art or spine, its shade and its reflection), in its slot. */
@Composable
private fun CarouselArt(
    g: Game, on: Boolean, spineHere: Boolean, reflect: Boolean, h0: androidx.compose.ui.unit.Dp, shadeK: Float,
    cardArtType: com.shelf.launcher.data.CardArtType, art: Map<String, GameArt>, overrides: Map<String, ArtOverride>,
    physicalMedia: Map<String, List<Pair<String, String>>>, screenshots: Map<String, List<String>>,
    mixed: Boolean, realRef: RealCaseRef?, pulse: Float
) {
    Box(Modifier.fillMaxSize()) {
        // Update 143: the games aside darkened only where they're drawn — the shade used to be a dark box over the whole
        // slot, showing round a case, cartridge or disc smaller than it.
        val shade = shadeK
        Box(
            Modifier.fillMaxSize().then(
                if (shade > 0.01f) Modifier
                    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(Color.Black.copy(alpha = shade), blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop)
                    }
                else Modifier
            )
        ) {
            if (spineHere) GameSpine(g, physicalMedia[g.stableKey], Modifier.fillMaxSize())
            else ShelfGameArt(g, on, cardArtType, art, overrides, physicalMedia, screenshots, spines = false, mixed = mixed, realRef = realRef, pulse = pulse, modifier = Modifier.fillMaxSize())
        }
        // A reflection on a glossy floor under it (update 104, Settings › Carousel look).
        if (reflect) Box(
            Modifier.fillMaxSize().offset(y = h0 + 3.dp)
                .graphicsLayer {
                    scaleY = -1f
                    alpha = 0.32f
                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(0f to Color.Transparent, 0.62f to Color.Transparent, 1f to Color.Black),
                        blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                    )
                }
        ) {
            if (spineHere) GameSpine(g, physicalMedia[g.stableKey], Modifier.fillMaxSize())
            else ShelfGameArt(g, false, cardArtType, art, overrides, physicalMedia, screenshots, spines = false, mixed = mixed, realRef = realRef, pulse = 0f, modifier = Modifier.fillMaxSize())
        }
    }
}
