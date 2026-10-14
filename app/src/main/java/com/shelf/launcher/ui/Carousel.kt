package com.shelf.launcher.ui

import androidx.compose.ui.graphics.asAndroidBitmap

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
import androidx.compose.ui.graphics.drawscope.scale
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
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.drawscope.translate
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
    val cov = MediaCovers.used(g.platform, mixed)
    if (isFreeMedia(gArt, g.platform)) {
        val typed = typedArtFor(gArt, physicalMedia[g.stableKey], screenshots[g.stableKey], g.platform)
        Box(modifier.awardShine(g.stableKey, on)) {
            val pick = if (gArt == com.shelf.launcher.data.CardArtType.CARTRIDGE_DISC) mediaFor(g.platform, mixed, cardArtType == com.shelf.launcher.data.CardArtType.DISC, gameKey = g.stableKey) else null
            // Update 129: "Real discs": the game's own scan (not a drawn shell with the scan as its label).
            // (Update 200: the 3D cartridges looks drew their drawn piece whatever the scans. Update 202: "The game's own
            // photo" decides in every cartridge look — realScanOf is null with it off — see drawnMediaWanted.)
            val real = realScanOf(gArt, typed)
            // (Update 173: an app without art: its icon on the label / case front — they stayed blank.)
            val appIcon = if (cover == null && typed == null && g.sourceType == com.shelf.launcher.data.SourceType.APP) {
                rememberAppIcon(g.packageName)?.let { remember(it) { it.asAndroidBitmap() } }
            } else null
            if (real != null) MediaFocus(on) { RealMediaPicture(real, mediaRound(g.platform), Modifier.fillMaxSize().padding(4.dp), platform = g.platform, gameKey = g.stableKey) }
            else if (pick != null) {
                MediaFocus(on) { PhysicalMedia(pick.shell, typed ?: cover ?: appIcon, Modifier.fillMaxSize().padding(4.dp), platform = pick.platform, labelIsScan = typed != null, caseCover = cover ?: appIcon, title = g.name) }
            }
            else MediaFocus(on) { CaseArt(
                g.platform, cover ?: appIcon, typed, Modifier.fillMaxSize().padding(4.dp), title = gameDisplayName(g.name),
                real = realRef, caseModifier = if (frameOn) Modifier.border(2.5.dp, ShelfTheme.accent, RoundedCornerShape(2.dp)) else Modifier
            ) }
            // Update 141: the console's badge on cartridges, discs, cases and 3D models too.
            // (Update 202: not on a case — its printed band shows the console already, it read twice.)
            ConsoleBadge(g.platform, packageName = g.packageName, onCase = real == null && pick == null)
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
            // (Update 202: the cover, when it stands in for missing wide art, with its file's flat strips trimmed.)
            if (url != null) AsyncImage(rememberStyleArtRequest(url, trim = url == cover), g.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().pixelArtwork()) // (Update 156)
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
                    // (Update 202: the same real spine the closed cases and the Spines look show — BoxTexture.realSpine.)
                    val ownSpine = BoxTexture.realSpine(physicalMedia[g.stableKey], g.platform)
                    Box3D(box3d, front, isBox, gameDisplayName(g.name), Modifier.fillMaxSize(), spin = false, realSpine = ownSpine)
                }
            } else CoverCase(cov, cover, placeholderBrush(g.hue), Modifier.fillMaxSize().padding(2.dp).awardShine(g.stableKey, on), caseModifier = frame)
            // Update 141: on the console's case too. (Update 202: not over the case's own printed band — it read twice.)
            ConsoleBadge(g.platform, packageName = g.packageName, onCase = true)
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
                // (Update 156: the warmed shape.) Update 159: the size was inside that comment, so the cover drew at its own
                // small size in the slot's colour (a big red box with the art small).
                // (Update 202: flat strips that are part of the cover's file trimmed off first — TrimMargins.kt — so the
                // shape noted below, and the slot, are the art's own.)
                rememberStyleArtRequest(cover, trim = true), g.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().pixelArtwork(),
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
    // Update 166: the Tile look in the carousels too: every cover a square tile (it was the Voltcap row's only).
    if (cardArtType == com.shelf.launcher.data.CardArtType.COVER && carouselTiles()) return 1f
    if (cardArtType == com.shelf.launcher.data.CardArtType.HERO || cardArtType == com.shelf.launcher.data.CardArtType.SCREENSHOT) return 16f / 9f
    val gArt = artForGame(cardArtType, g.platform, hidden, mixed)
    if (isFreeMedia(gArt, g.platform)) {
        if (gArt == com.shelf.launcher.data.CardArtType.CARTRIDGE_DISC) return mediaFor(g.platform, mixed, cardArtType == com.shelf.launcher.data.CardArtType.DISC, gameKey = g.stableKey)?.aspect ?: 0.7f
        return MediaCovers.of(g.platform)?.frontAspect ?: 0.7f
    }
    // (Update 169: a Steam / Android case reshaped to its cover — CoverCase.)
    MediaCovers.used(g.platform, mixed)?.let { return it.frontAspectFor(cover) }
    // Update 154: a plain cover at its own shape once it has loaded (it was cut to 0.7 — tops, bottoms and sides of
    // square GBA boxes, wide PC covers and tall posters were cut off).
    return CoverAspects.of(cover) ?: 0.7f
}

/** Update 166: the Tile look (square tiles), not under Spines. */
internal fun carouselTiles(): Boolean = Features.squareTiles.state.value && !Features.spineLook.state.value

/** Update 166: the game you're on as its wide capsule in the carousels ("Covers · wide capsule"). */
internal fun carouselWideOn(cardArtType: com.shelf.launcher.data.CardArtType): Boolean =
    Features.carouselWide.state.value && cardArtType == com.shelf.launcher.data.CardArtType.COVER && !carouselTiles() && !Features.spineLook.state.value

/** Update 154: the shape (width / height) of each plain cover the carousel has loaded, so its slot fits it whole. */
internal object CoverAspects {
    private val map = androidx.compose.runtime.mutableStateMapOf<String, Float>()
    fun of(url: String?): Float? = url?.let { map[it] }
    /** Update 171: the picture's own shape, not kept to the slot's range (a wide SNES box scan is 1.37). */
    private val raw = androidx.compose.runtime.mutableStateMapOf<String, Float>()
    fun rawOf(url: String?): Float? = url?.let { raw[it] }
    fun note(url: String, w: Float, h: Float) {
        if (w <= 0f || h <= 0f || w.isInfinite() || h.isInfinite()) return
        val r = w / h
        if (raw[url]?.let { kotlin.math.abs(it - r) > 0.005f } != false) raw[url] = r
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
    RECEDE("Receding row"),
    /**
     * Update 159: an arch fanned across the screen (from the loader picture): the one you're on raised and bigger in
     * front, each step out a little lower, smaller and leaning outward. Any game look.
     */
    ARCH("Arch"),
    /**
     * Update 159: box flow, like a disc loader's 3D box browser: the one you're on facing you a little apart in the
     * middle, the others as 3D boxes packed tight either side and turned to their spines, the stacks growing slightly
     * toward the screen's edges; moving, the next box slides out of its stack and turns to face you.
     */
    BOX_FLOW("Box flow", true)
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
    fun reach(side: Int, shown: CarouselView = view): Int =
        // (Update 182: with To the edge too — slim spines and boxes need three times as many to reach the edges.)
        if (shown == CarouselView.SPINE_WALL || shown == CarouselView.BOX_FLOW) side * 3 else side
    /** Spacing between the games (Settings, 60–160 %), times [spread]. */
    val gap: Float get() = Features.carouselGap.state.value / 100f * spread
    /**
     * Update 182: with only a few games the carousel spreads them out (set by GameCarousel from how many there are), so
     * the sides aren't a small huddle in the middle with empty stage round it.
     */
    @Volatile var spread = 1f

    /**
     * Update 187: how fast the carousel is moving now (games a second; read while placing, every moving frame), for Box
     * flow's sway ([Features.boxFlowTilt]). Set by the carousel on screen.
     */
    @Volatile var speedOf: () -> Float = { 0f }

    /** Update 187: Box flow — the box you're on leans into the move a little (degrees, ±10 at most). */
    private fun flowSway(near: Float): Float =
        if (!Features.boxFlowTilt.state.value || near >= 1f) 0f
        else (-speedOf() * 1.4f).coerceIn(-10f, 10f) * (1f - near) * (1f - near)

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
    class Place(
        var x: Float, var y: Float, var scale: Float, var turnY: Float, var turnZ: Float, var alpha: Float, var shade: Float, var z: Float
    ) {
        /** Update 162: filled in place (the carousel reuses one per game while it moves: nothing made per frame). */
        fun set(x: Float, y: Float, scale: Float, turnY: Float, turnZ: Float, alpha: Float, shade: Float, z: Float): Place {
            this.x = x; this.y = y; this.scale = scale; this.turnY = turnY; this.turnZ = turnZ
            this.alpha = alpha; this.shade = shade; this.z = z
            return this
        }
    }

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
        CarouselView.BOX_FLOW -> CarouselView.FLIP
        else -> view
    }

    fun place(rel: Float, side: Int, shown: CarouselView = view): Place =
        placeInto(Place(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f), rel, side, shown)

    /**
     * Update 162: 0…1 across the step out of the middle, eased so a game leaving the middle slows into its stack's own
     * step ([endSlope]: the stack's step against the whole way out) — no jolt where it joins the stack, and the same
     * speed through the middle from either side.
     */
    private fun stackEase(t: Float, endSlope: Float): Float {
        val t2 = t * t
        val t3 = t2 * t
        return (t3 - 2f * t2 + t) * 1.2f + (3f * t2 - 2f * t3) + (t3 - t2) * endSlope
    }

    /**
     * Update 162: drawing order of the packed stacks (Box flow, Spine wall), whose games all show their spine (left
     * side): the middle one on top, then the ones crossing; in the right stack each one further out over the one inside
     * it (else its front would cover the next one's spine), in the left stack the inner one on top.
     */
    private fun stackZ(rel: Float): Float {
        val d = kotlin.math.abs(rel)
        if (d < 1f) return 100f - 30f * d
        val far = (d - 1f).coerceAtMost(19f)
        return if (rel > 0f) 50f + far else 50f - far
    }

    /**
     * Update 162: [place] into [out] (no new object). [midW]: the middle game's width against a cover's, so the packed
     * views leave the same gap beside a wide cartridge as beside a case.
     */
    fun placeInto(out: Place, rel: Float, side: Int, shown: CarouselView = view, midW: Float = 1f): Place {
        val d = kotlin.math.abs(rel)
        val sign = if (rel < 0f) -1f else 1f
        val near = d.coerceAtMost(1f)
        val far = (d - 1f).coerceAtLeast(0f)
        val g = gap
        // (Update 183: the spread for few games only widens the steps between the ones aside, not the gap round the one
        // you're on — it left a wide empty space each side of the middle game.)
        val g0 = g / spread.coerceAtLeast(0.01f)
        val farK = (g - g0)
        return when (shown) {
            CarouselView.TUCKED -> out.set((offset(d, side) * g0 + far * sideScale(side) * 0.5f * farK) * sign, 0f, scale(d, side), turn(rel), 0f, alpha(d, side), shade(d), z(d))
            CarouselView.FLIP -> out.set(
                sign * ((0.7f * near + 0.22f * far) * g), 0f, 1f - 0.16f * near - 0.04f * far,
                // Turned steeply towards the middle (or away with the tilt set outward).
                (if (Features.tvCarouselTiltDir.state.value == TiltDir.OUT) -1f else 1f) * sign * 62f * near, 0f,
                alpha(d, side + 1), shade(d) * 0.8f, z(d)
            )
            CarouselView.WHEEL -> {
                val a = (rel * 0.45f * g).coerceIn(-1.5f, 1.5f)
                out.set(
                    kotlin.math.sin(a) * 1.9f, (1f - kotlin.math.cos(a)) * 0.9f, 1f - 0.12f * d.coerceAtMost(3f),
                    0f, a * 57.3f * 0.55f, alpha(d, side), shade(d) * 0.7f, z(d)
                )
            }
            CarouselView.DECK -> if (rel >= 0f) out.set(
                0.09f * rel * g, -0.045f * rel, 1f - 0.07f * rel, 0f, 0f,
                alpha(d, side), shade(d), z(d)
            ) else out.set(
                // Past ones slide off to the left and fade.
                -1.15f * d * g, 0f, 1f - 0.05f * d, -18f * near, 0f,
                (1f - d * 1.4f).coerceIn(0f, 1f), 0f, 101f
            )
            CarouselView.FLAT -> out.set(
                sign * (near * 0.97f + far * 0.86f) * g, 0f, if (d < 1f) 1f - 0.22f * d else 0.78f,
                0f, 0f, alpha(d, side), shade(d) * 0.6f, z(d)
            )
            CarouselView.POP -> out.set(
                // Side by side with a small gap: the middle one a quarter bigger, the rest all the same size, unshaded.
                sign * (0.95f * near + 0.85f * far) * g, 0f, 1f - 0.2f * near,
                0f, 0f, if (d <= side) 1f else alpha(d, side), shade(d) * 0.25f, z(d)
            )
            CarouselView.ARC -> {
                // Round a wide arc: each turned to face its middle (the left ones show their spines).
                val a = (rel * 0.38f * g).coerceIn(-1.4f, 1.4f)
                val back = 1f - kotlin.math.cos(a)
                out.set(
                    kotlin.math.sin(a) * 2.4f, -back * 0.25f, (1f - 0.1f * near) / (1f + 1.1f * back),
                    -a * 57.3f * 0.85f, 0f, alpha(d, side), shade(d) * 0.6f, z(d)
                )
            }
            CarouselView.SPINE_WALL -> {
                // Turned almost to their spines (Update 147: 72°, a sliver of each front showing, as on a disc loader's
                // case wall), packed close in a stack each side.
                // Update 162: out of the middle eased into the stack's step, a little more to the spine further out,
                // standing on the same floor as the one you're on, the stacks drawn so every spine shows.
                val a = 0.5f * midW + 0.12f
                val e = stackEase(near, 0.125f / a)
                val sc = 1f - 0.1f * e
                out.set(
                    sign * (a * e * g0 + 0.125f * far * g), (1f - sc) * 0.5f,
                    sc, 72f * e + (far * 1.2f).coerceAtMost(8f), 0f, alpha(d, reach(side, shown)), shade(d) * 0.5f, stackZ(rel)
                )
            }
            CarouselView.TURNED -> {
                // Update 147: the ones aside close to the middle one, a little smaller and turned to face it (the left
                // ones turned right, the right ones left), like a disc loader's cover row; the one you're on straight on.
                // (Update 162: the gap beside the middle one follows its width; all standing on one floor.)
                val sc = if (d < 1f) 1f - 0.2f * d else 0.8f
                out.set(
                    sign * (near * (0.5f * midW + 0.3f) + far * 0.62f) * g, (1f - sc) * 0.5f, sc,
                    -sign * 26f * near, 0f, alpha(d, side), shade(d) * 0.5f, z(d)
                )
            }
            CarouselView.RECEDE -> if (rel >= 0f) {
                // Update 146: each next game a step further back: smaller by the same share each step, its left edge just
                // under the one before (a little overlap), centred a bit higher so the row runs off to a point above the
                // middle, and turned a little away like a wall seen from its left end.
                val sc = if (d <= 1f) 1f - (1f - RECEDE_NEXT) * d else RECEDE_NEXT * Math.pow(RECEDE_SHRINK.toDouble(), far.toDouble()).toFloat()
                val back = RECEDE_NEXT * 0.82f * (1f - Math.pow(RECEDE_SHRINK.toDouble(), far.toDouble()).toFloat()) / (1f - RECEDE_SHRINK)
                out.set(
                    (0.76f * near + back) * g, -(1f - sc) * 0.3f, sc,
                    24f * near, 0f, alpha(d, side), shade(d) * 0.8f, z(d)
                )
            } else out.set(
                // The ones before slide off past the left edge and fade (drawn over the one coming in, like the Deck).
                -1.1f * d * g, 0f, 1f - 0.08f * near, 0f, 0f,
                (1f - d * 1.25f).coerceIn(0f, 1f), 0f, z(d) + 0.5f
            )
            // Update 159: the arch — overlapping a quarter, each step a little lower, leaning outward, the middle lifted.
            CarouselView.ARCH -> out.set(
                rel * 0.74f * g, (d * d * 0.035f).coerceAtMost(0.5f) - (1f - near) * 0.05f,
                (1f - 0.12f * near - 0.05f * far).coerceAtLeast(0.6f),
                0f, (rel * 4.5f).coerceIn(-20f, 20f), alpha(d, side), shade(d) * 0.35f, z(d)
            )
            // Update 159: box flow — a gap round the middle box, then the boxes packed tight and turned to their spines.
            // Update 162, tuned to the box browser videos: the one you're on facing you, lifted a touch, a clear gap (it
            // follows the middle piece's width), then the others packed tight on a gentle curve a step behind it (0.84),
            // coming slowly forward toward the screen's edges and turning further to their spines there; feet on one
            // floor; eased out of the middle so the one leaving slides into its stack without a jolt.
            CarouselView.BOX_FLOW -> {
                val a = 0.5f * midW + 0.32f
                val e = stackEase(near, 0.15f / a)
                // (Update 182: all the same size in the stacks — they grew toward the edges, which read as the boxes
                // growing as you went along them.)
                val sc = 1f - 0.16f * e
                out.set(
                    sign * (a * e * g0 + 0.15f * far * g), (1f - sc) * 0.5f * e - 0.04f * (1f - e),
                    sc, 66f * e + (far * 2.2f).coerceAtMost(16f) + flowSway(near), 0f,
                    alpha(d, reach(side, shown)), shade(d) * 0.45f, stackZ(rel)
                )
            }
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
        // (Update 202: covers, drawn trimmed — CarouselArt.)
        warmStyleArt(warmCtx, urls) { true }
        // Update 166: the 3D cases' and 3D pieces' covers too (they read their own plain bitmaps: Case3D 480 px, the 3D
        // pieces 640 px), a little further out — Box flow shows many at once.
        val f = Features
        val box3d = f.carousel3d.value || Carousel.view.cases
        // (Update 200: the 3D cartridges looks' drawn shells read the cover at 480 px, as the 3D cases do — the
        // pre-rendered 3D models' 640 px is gone with them.)
        val models = drawn3dOn() && cardArtType == com.shelf.launcher.data.CardArtType.CARTRIDGE_DISC
        if (box3d || models) {
            val far = reach + 3
            val more = ((focus - far)..(focus + far)).mapNotNull { i ->
                val g = games.getOrNull(if (loop) i.mod(n) else i) ?: return@mapNotNull null
                overrides[g.stableKey]?.cover ?: art[g.stableKey]?.gridUrl
            }.distinct()
            // (Update 202: the 3D cases read their cover trimmed — Case3D; the drawn cartridge shells as it is.)
            if (box3d) warmSoft(warmCtx, more, 480, trim = true)
            if (models) warmSoft(warmCtx, more, 480)
        }
    }
    val spines = spinesOn(cardArtType)
    val view = Carousel.view
    // Update 141: every carousel view works with every look. The views made for 3D cases (Arc, Spine wall, Turned row)
    // draw the 3D cases only for the case looks (covers, real cases); with cartridges, discs, wide capsules, pictures or
    // spines they lay out that look itself, turned the same way.
    // (Update 174: not with the Tile look — the case views drew 3D cases over it; now square tiles there too.)
    val caseLook = !spines && !(cardArtType == com.shelf.launcher.data.CardArtType.COVER && carouselTiles()) &&
        (cardArtType == com.shelf.launcher.data.CardArtType.COVER || cardArtType == com.shelf.launcher.data.CardArtType.CASE ||
        cardArtType == com.shelf.launcher.data.CardArtType.CASE_REAL)
    // Update 162: the "3D cases" switch too turns only the case looks into 3D cases (it boxed cartridges, cards and discs
    // into cases); every other look shows each game as its own piece in every view, a turned flat piece with its edge.
    val cases3d = caseLook && (Features.carousel3d.state.value || view.cases)
    // Update 141: with 3D models on, the cartridges & discs look drew each game as its 3D model in every view, at its turn.
    // (Update 200: retired — the 3D cartridges looks are drawn shells (mediaFor), each a turned flat piece like the other
    // looks; kept false so the views' code stays as it is.)
    val modelLook = false
    // Update 135: the games aside drawn as slim spines in a row (Spines look, or Record flip turning covers to their
    // spines), in the views that lay the games out in a row (Wheel and Deck keep their own places).
    val spinesAside = !cases3d && (view == CarouselView.TUCKED || view == CarouselView.FLIP || view == CarouselView.FLAT || view == CarouselView.POP) && (spines || (view == CarouselView.FLIP &&
        (cardArtType == com.shelf.launcher.data.CardArtType.COVER || cardArtType == com.shelf.launcher.data.CardArtType.CASE || cardArtType == com.shelf.launcher.data.CardArtType.CASE_REAL)))
    // Update 146: the Receding row's next games as spines (Spines look) packed one after another to the right.
    val recede = view == CarouselView.RECEDE
    val spinesRight = recede && spines && !cases3d
    // Spines are slim: three times as many each side (like the Spine wall), so they reach the screen's edges.
    // Update 182: "To the edge" counts the games each side from the screen's width (a fixed 12 stopped short of the
    // edges on big screens like the AYN Thor's, and spines never got their three times as many there).
    val screenWide = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp / 600f).coerceIn(1f, 3f)
    val side = Carousel.side.let {
        val edge = it >= Carousel.EDGE_SIDE
        val base = if (edge) kotlin.math.ceil(Carousel.EDGE_SIDE * screenWide).toInt() else it
        if (spinesAside || spinesRight) base * 3 else base
    }
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
    val tap: (Int) -> Unit = remember { { r: Int -> if (r == GridHome.focus) open() else select(r) } }
    val look = LocalCardLook.current
    // Update 162: the focus pulse is read by the game you're on only (CarouselArt) — read here, it redrew the whole
    // carousel twenty times a second.
    val pulseOn = look.focusEffect != com.shelf.launcher.data.FocusEffect.NONE && !LocalLiteEffects.current
    val hidden = LocalHiddenMedia.current
    val mixed = androidx.compose.runtime.remember(games) { mixedConsoles(games) }
    val realRef = androidx.compose.runtime.remember(games, cardArtType) { realCaseRef(cardArtType, games) }
    // Update 166: Box flow's cases (and Cases · real sizes) at their real sizes against the biggest one here.
    val caseRefMm = androidx.compose.runtime.remember(games, cardArtType, Carousel.view) {
        if (Carousel.view != CarouselView.BOX_FLOW && cardArtType != com.shelf.launcher.data.CardArtType.CASE_REAL) 0f
        else games.asSequence().map { it.platform }.distinct().mapNotNull { realCaseMm(it) }.maxOfOrNull { maxOf(it.first, it.second) } ?: 0f
    }
    val dens = androidx.compose.ui.platform.LocalDensity.current.density
    val latestFocus by rememberUpdatedState(focus)
    val latestLoop by rememberUpdatedState(looping)
    // Update 162: one moving place for the whole carousel (in games), read only while placing and drawing (graphicsLayer,
    // the draw lambdas), so moving doesn't rebuild the carousel each frame. It glides on a critically damped spring as
    // quick as Settings › Home › Animations › Moving between games: a new step carries on at the speed it already has
    // (holding the D-pad flows instead of stopping and starting at each game), and it never overshoots.
    val reachNow = Carousel.reach(side, view)
    val animOn = rememberAnimationsEnabled()
    val glideSpeed = Features.rowGlide.state.value
    val glide = remember { CarouselGlide(vFocus.toFloat()) }
    // (Update 187: Box flow's sway reads how fast it's moving.)
    androidx.compose.runtime.SideEffect { Carousel.speedOf = { glide.pos.velocity } }
    val swap = remember { CarouselSwap() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Update 162: a category change slides the collection along the carousel's own path (the old one out, the new one
    // in, CarouselSwap) instead of the whole box sliding (categorySlide round it stays still while a carousel is here).
    MotionPrefs.observe()
    val pageKind = MotionPrefs.page()
    val pathSlide = Features.categorySlide.state.value && (!LocalLiteEffects.current || Features.motionOnLite.state.value) && pageKind != Features.PageAnim.NONE && animOn // (Update 162: Full animations on Lite)
    swap.check(games, looping, vFocus, CategorySlide.tick, CategorySlide.dir, pathSlide, glide)
    androidx.compose.runtime.DisposableEffect(Unit) {
        CategorySlide.pathOwners++
        onDispose {
            CategorySlide.pathOwners--
            // (Update 162 (review): no carousel, no game drawn as a 3D case.)
            MediaInspect.drawnAsCase = null
        }
    }
    // Update 162 (review): tells the game window's inspect (MediaInspect.drawnAsCase) when the game you're on is drawn as a
    // 3D case although its look is a plain cover, so opening it lifts out the box the carousel shows.
    val caseKey = if (cases3d) games.getOrNull(focus)?.stableKey else null
    androidx.compose.runtime.SideEffect { MediaInspect.drawnAsCase = caseKey }
    LaunchedEffect(vFocus, swap.token) {
        val t = vFocus.toFloat()
        val key = if (n > 0) games.getOrNull(if (looping) vFocus.mod(n) else vFocus)?.stableKey else null
        val from = glide.target
        glide.target = vFocus
        val same = key != null && key == glide.key
        glide.key = key
        if (swap.snapPending || !animOn) {
            // A new category (its collection slides in along the path) or animations off: straight to the place.
            swap.snapPending = false
            glide.pos.snapTo(t)
        } else {
            // The same game at a new number (Endless renumbered, the list re-sorted or grown): it stays where it is.
            if (vFocus != from && same) glide.pos.snapTo(glide.pos.value + (vFocus - from))
            // A long jump (a letter, a tap far off): only the last step glides, not dozens of games racing past.
            else if (kotlin.math.abs(vFocus - from) > reachNow + 2) glide.pos.snapTo(t - kotlin.math.sign((vFocus - from).toFloat()))
            if (glide.pos.value != t || glide.pos.targetValue != t) scope.launch {
                glide.pos.animateTo(t, androidx.compose.animation.core.spring(1f, glideSpeed.stiffness, 0.0005f))
            }
        }
    }
    LaunchedEffect(swap.token) {
        if (swap.token == 0) return@LaunchedEffect
        val tk = swap.token
        swap.k.snapTo(0f)
        swap.kPending = false
        swap.k.animateTo(1f, tween(glideSpeed.carouselMs + 200, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        swap.doneToken.intValue = tk
    }
    // (Update 162 (review): the moving values always read first — while a slide was pending they weren't read at all, so
    // the games' layers and posFloor never subscribed to them and stayed frozen through the slide and after it.)
    val curPos: () -> Float = remember { { val v = glide.pos.value; if (swap.snapPending) swap.snapTo else v } }
    val kNow: () -> Float = remember { { val v = swap.k.value; if (swap.kPending) 0f else v } }
    // Which games are near enough to draw changes only once per game passed (not every frame).
    val posFloor by remember { androidx.compose.runtime.derivedStateOf { kotlin.math.floor(curPos()).toInt() } }
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
        // (Update 173: the reflection and Box flow lift the one you're on; with a big Game size its top went over what's
        // above the carousel — Case stacks' tool strip. Its height is kept so the lifted top still fits.)
        val lift = (if (Features.carouselReflect.state.value) 0.08f else 0f) + (if (view == CarouselView.BOX_FLOW) 0.04f else 0f)
        // (Update 182: room kept only for the lifted top — the reflection may run on under what's below the carousel —
        // so the games are bigger; they were small on the Thor's wide screen.)
        val bigW = minOf(maxHeight * 0.9f * 0.72f * sizeK, maxHeight * 0.97f * 0.72f / (1f + lift), maxWidth * 0.5f * sizeK)
        // Update 182: few games, spread out (the packed views most, their boxes are slim).
        val packed = view == CarouselView.BOX_FLOW || view == CarouselView.SPINE_WALL
        val spreadNow = if (!Features.carouselSpread.state.value) 1f else if (packed) (16f / games.size.coerceAtLeast(2)).coerceIn(1f, 2.2f) else (8f / games.size.coerceAtLeast(2)).coerceIn(1f, 1.5f)
        androidx.compose.runtime.SideEffect { Carousel.spread = spreadNow }
        val bigH = bigW / 0.72f
        // Update 147: the dark stage (on with the 3D case views): the background fades to black towards the floor the
        // games stand on, a faint gloss line along it, and stays dark below (drawn past the bottom of the carousel).
        if (Features.carouselStage.state.value) {
            val floorK = 0.5f + (bigH / maxHeight) / 2f - (if (Features.carouselReflect.state.value) (bigH * 0.08f) / maxHeight else 0f)
            // Update 182: the stage with more life — tinted by your colour, a soft spotlight behind the game you're on,
            // a pool of light on the glossy floor under it, a horizon line that glows in the middle and fades to the
            // sides, faint lines running into the distance, and the edges dimmed (it was plain black).
            // Update 187: in the colours of the game you're on (its cover's), gliding from one game's to the next.
            val stageGame = games.getOrNull(focus)
            val stageCover = if (Features.carouselStageGameColor.state.value) stageGame?.let { overrides[it.stableKey]?.cover ?: art[it.stableKey]?.gridUrl } else null
            val stagePalette = rememberArtPalette(stageCover, stageCover != null)
            val stageTarget = (if (stageCover != null) stagePalette?.accent else null) ?: com.shelf.launcher.ui.theme.ShelfTheme.accent
            val accent by androidx.compose.animation.animateColorAsState(stageTarget, tween(700), label = "stageTint")
            val deep = androidx.compose.ui.graphics.lerp(accent, Color.Black, 0.86f)
            val bigHpx = with(androidx.compose.ui.platform.LocalDensity.current) { bigH.toPx() }
            androidx.compose.foundation.Canvas(Modifier.matchParentSize().zIndex(-1000f)) {
                val floorY = size.height * floorK
                val below = size.height * 1.5f
                val cx = size.width / 2f
                drawRect(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0f), 0.35f to deep.copy(alpha = 0.6f), 1f to deep.copy(alpha = 0.93f),
                        startY = -size.height * 0.25f, endY = floorY
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, -size.height * 0.25f),
                    size = androidx.compose.ui.geometry.Size(size.width, floorY + size.height * 0.25f)
                )
                // The spotlight behind the game you're on.
                val spotC = androidx.compose.ui.geometry.Offset(cx, floorY - bigHpx * 0.5f)
                drawCircle(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0.06f), Color.Transparent), center = spotC, radius = bigHpx * 1.25f
                    ),
                    radius = bigHpx * 1.25f, center = spotC
                )
                // The floor.
                drawRect(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(androidx.compose.ui.graphics.lerp(deep, Color.Black, 0.2f).copy(alpha = 0.95f), Color.Black.copy(alpha = 0.97f)),
                        startY = floorY, endY = floorY + size.height * 0.5f
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, floorY),
                    size = androidx.compose.ui.geometry.Size(size.width, below)
                )
                // Lines running into the distance (towards a point far behind the game you're on).
                val vanish = androidx.compose.ui.geometry.Offset(cx, floorY - size.height * 0.6f)
                val bottom = floorY + size.height * 0.6f
                for (k in -7..7) {
                    if (k == 0) continue
                    val xb = cx + k * size.width * 0.16f
                    val t = (floorY - vanish.y) / (bottom - vanish.y)
                    val xf = vanish.x + (xb - vanish.x) * t
                    drawLine(
                        Color.White.copy(alpha = 0.035f), androidx.compose.ui.geometry.Offset(xf, floorY),
                        androidx.compose.ui.geometry.Offset(xb, bottom), strokeWidth = 1.2f
                    )
                }
                // A pool of light on the floor under the game you're on.
                // (Update 183: a round glow squashed flat, so it fades out on every side — drawn as an oval its top and
                // bottom edges showed.)
                val poolC = androidx.compose.ui.geometry.Offset(cx, floorY + bigHpx * 0.08f)
                val poolR = size.width * 0.3f
                scale(1f, (bigHpx * 0.22f / poolR).coerceIn(0.05f, 1f), pivot = poolC) {
                    drawCircle(
                        androidx.compose.ui.graphics.Brush.radialGradient(
                            0f to Color.White.copy(alpha = 0.10f), 0.45f to accent.copy(alpha = 0.05f), 1f to Color.Transparent,
                            center = poolC, radius = poolR
                        ),
                        radius = poolR, center = poolC
                    )
                }
                // The glossy floor's sheen just under the line the games stand on.
                drawRect(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.06f), Color.Transparent), startY = floorY, endY = floorY + size.height * 0.22f
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, floorY),
                    size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.22f)
                )
                // The horizon: brightest in the middle, in your colour, fading to the sides.
                drawRect(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        0f to Color.Transparent, 0.3f to accent.copy(alpha = 0.25f), 0.5f to Color.White.copy(alpha = 0.32f),
                        0.7f to accent.copy(alpha = 0.25f), 1f to Color.Transparent
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, floorY - 0.75f),
                    size = androidx.compose.ui.geometry.Size(size.width, 1.5f)
                )
                // The edges dimmed.
                drawRect(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.45f), 0.18f to Color.Transparent, 0.82f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.45f)
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, -size.height * 0.25f),
                    size = androidx.compose.ui.geometry.Size(size.width, size.height * 1.75f + below)
                )
            }
        }
        // Update 146: the Receding row spaces its games by the big one's real width: a wide capsule or a round disc is
        // wider than a cover, and spaced as covers they piled up. Glides to the next game's width with the carousel.
        // (Update 162: the packed views — Box flow, Spine wall, Turned row — keep their gap beside it by its width too.)
        // (No room yet — a 0-high box while the screen opens: 1, not 0 / 0 = NaN, which crashes the offset.)
        val unitTarget = games.getOrNull(focus)?.takeIf { bigW > 0.dp }?.let { fg ->
            // (Update 171: with the room for its cartridge / disc beside it, so the neighbours stand clear of it.)
            val media = if (cases3d) caseMediaFor(fg, cardArtType, mixed)?.room(realCaseMm(fg.platform)) ?: 0f else 0f
            val a = if (cases3d) case3dAspect(fg, mixed) else if (carouselWideOn(cardArtType)) CAPSULE_ASPECT else shelfArtAspect(fg, cardArtType, hidden, mixed)
            val sh = carouselSlotH(a, bigW, bigH, mixed)
            ((sh * (a + media)) / bigW).coerceIn(0.6f, 2.4f)
        } ?: 1f
        // (Update 162: on the same spring as the carousel, and read only while placing.)
        val unitState = animateFloatAsState(unitTarget, androidx.compose.animation.core.spring(1f, glideSpeed.stiffness, 0.001f), label = "carouselUnit")
        // Update 146: the Receding row's big game sits near the left edge (its left side ~7 % in), not in the middle.
        val recedeLeft = (maxWidth * 0.07f).coerceAtLeast(12.dp)
        // (Update 162 (review): kept while nothing in it changes, so the games skip recomposing when the carousel passes
        // a game — a new one each time made every game in it rebuild.)
        val reflectOn = Features.carouselReflect.state.value
        val accentNow = ShelfTheme.accent
        val ctx = remember(
            view, side, bigW, bigH, recede, recedeLeft, maxWidth, unitState, cases3d, modelLook, spines, spinesAside, spinesRight,
            reflectOn, dens, cardArtType, art, overrides, physicalMedia, screenshots, mixed, realRef, hidden, look.focusBorder, accentNow, pulseOn
        ) {
            CarouselCtx(
                view, side, bigW, bigH, if (recede) -maxWidth / 2 + recedeLeft else null, unitState, recede, cases3d, modelLook,
                spines, spinesAside, spinesRight, recede, reflectOn, dens, cardArtType, art, overrides,
                physicalMedia, screenshots, mixed, realRef, hidden, look.focusBorder, accentNow, pulseOn
            )
        }
        ctx.caseRefMm = caseRefMm
        // Update 162: while a category change slides, the collection that was here goes on along the path (layer 1)
        // and the new one comes in after it from the side the change came from (layer 0), each fading as it goes.
        val showOld = swap.token != 0 && swap.doneToken.intValue != swap.token && swap.oldGames.isNotEmpty()
        val shiftK = if (pageKind == Features.PageAnim.SLIDE) CarouselSwap.SHIFT else 0f
        val shiftN = shiftK.toInt()
        val dirS = swap.dir
        // (Update 162 (review): posFloor read every time, so the carousel stays subscribed to it.)
        val pf = posFloor
        val base = if (swap.snapPending) vFocus else pf
        val oldPos = swap.oldPos
        for (layer in 0..(if (showOld) 1 else 0)) {
            val old = layer == 1
            val list = if (old) swap.oldGames else games
            val ln = list.size
            val lLoop = if (old) swap.oldLoop else looping
            val lBase = if (old) kotlin.math.floor(oldPos).toInt() else base
            // (The leaving collection only as far as 8 each side: a long stack's far end just goes, rather than
            // building dozens of games for a moment.)
            val lReach = if (old) minOf(reachNow, 8) else reachNow
            val lo = lBase - lReach - 1 - (if (showOld && ((!old && dirS > 0) || (old && dirS < 0))) shiftN else 0)
            val hi = lBase + lReach + 2 + (if (showOld && ((!old && dirS < 0) || (old && dirS > 0))) shiftN else 0)
            val focusIdx = if (old) kotlin.math.round(oldPos).toInt() else vFocus
            for (i in lo..hi) {
                // (Update 135: on an endless row item i is a place on the endless row; its game is i mod the count.)
                // (Update 174: each game once when the row is shorter than the view.)
                if (lLoop && !drawnOnce(i - focusIdx, ln)) continue
                val real = if (lLoop) i.mod(ln) else i
                val g = list.getOrNull(real) ?: continue
                val relOf: () -> Float = if (old) ({ i - oldPos - dirS * shiftK * kNow() })
                    else if (showOld) ({ i - curPos() + dirS * shiftK * (1f - kNow()) })
                    else ({ i - curPos() })
                val fadeOf: () -> Float = if (old) ({ 1f - kNow() }) else if (showOld) kNow else CarouselSwap.FULL
                androidx.compose.runtime.key(old, if (lLoop) i else g.stableKey) {
                    CarouselItem(
                        ctx, g, real, i, focusIdx, on = !old && i == vFocus, interactive = !old,
                        relOf = relOf, fadeOf = fadeOf, onTap = tap, modifier = Modifier.align(Alignment.Center)
                    )
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

/** Update 162: the carousel's one moving place (in games), and the place and game it last went to. */
private class CarouselGlide(start: Float) {
    val pos = androidx.compose.animation.core.Animatable(start, 0.0005f)
    var target = kotlin.math.round(start).toInt()
    var key: String? = null
}

/**
 * Update 162: a category change in the carousel: the collection that was on screen slides on along the carousel's path
 * and fades while the new one comes in along it ([k] 0 → 1). Started when the list changes with an L2 / R2 step
 * ([CategorySlide.tick]) — the step and the new list arrive together or one just after the other; any other new list
 * (art loaded, a re-sort) changes in place.
 */
private class CarouselSwap {
    private var seenTick = Int.MIN_VALUE
    private var armedAt = 0L
    private var lastGames: List<Game>? = null
    private var lastLoop = false
    private var changedAt = 0L
    private var beforeChange: List<Game>? = null
    private var beforeLoop = false
    /** +1: the new collection comes in from the right (next category), -1 from the left. */
    var dir = 1
        private set
    var oldGames: List<Game> = emptyList()
        private set
    var oldPos = 0f
        private set
    var oldLoop = false
        private set
    /** Counts the slides started; [doneToken] is the last one finished (written by its coroutine, never while composing). */
    var token = 0
        private set
    val doneToken = androidx.compose.runtime.mutableIntStateOf(0)
    val k = androidx.compose.animation.core.Animatable(1f)
    /** Until the glide has jumped to the new list's place / the slide has started, they're drawn as if they had. */
    var snapPending = false
    var snapTo = 0f
    var kPending = false

    fun check(games: List<Game>, looping: Boolean, vFocus: Int, tick: Int, tickDir: Int, on: Boolean, glide: CarouselGlide) {
        val now = android.os.SystemClock.uptimeMillis()
        if (seenTick == Int.MIN_VALUE) seenTick = tick
        val prev = lastGames
        if (tick != seenTick) { seenTick = tick; dir = if (tickDir < 0) -1 else 1; armedAt = now }
        if (prev != null && prev !== games && !sameShape(prev, games)) { beforeChange = prev; beforeLoop = lastLoop; changedAt = now }
        lastGames = games
        lastLoop = looping
        val b = beforeChange
        // The new list with the step or within a moment after it (or a frame before it).
        if (b != null && armedAt != 0L && changedAt >= armedAt - 120L && changedAt - armedAt < 1200L) {
            if (on && b.isNotEmpty() && games.isNotEmpty()) {
                oldGames = b
                oldLoop = beforeLoop && b.size >= 2
                oldPos = androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation { glide.pos.value }
                token++
                snapPending = true
                snapTo = vFocus.toFloat()
                kPending = true
            }
            beforeChange = null
            armedAt = 0L
        }
        if (armedAt != 0L && now - armedAt > 1200L) armedAt = 0L
        if (beforeChange != null && now - changedAt > 1200L) beforeChange = null
    }

    /** The same list made again (a caller filtering anew): same size and the same games at its ends and middle. */
    private fun sameShape(a: List<Game>, b: List<Game>): Boolean {
        if (a.size != b.size) return false
        if (a.isEmpty()) return true
        val m = a.size / 2
        return a[0].stableKey == b[0].stableKey && a[m].stableKey == b[m].stableKey && a[a.size - 1].stableKey == b[b.size - 1].stableKey
    }

    companion object {
        /** How many places along the path the collections slide (Slide; Fade only fades). */
        const val SHIFT = 3f
        val FULL: () -> Float = { 1f }
    }
}

/** Update 162: what every game in the carousel shares (made once per composition, read while placing and drawing). */
private class CarouselCtx(
    val view: CarouselView, val side: Int, val bigW: androidx.compose.ui.unit.Dp, val bigH: androidx.compose.ui.unit.Dp,
    /** Receding row: the big game's left edge from the middle (its centre follows its width); null in the other views. */
    val recedeLeft: androidx.compose.ui.unit.Dp?,
    val unit: androidx.compose.runtime.State<Float>, val widen: Boolean, val cases3d: Boolean, val modelLook: Boolean,
    val spines: Boolean, val spinesAside: Boolean, val spinesRight: Boolean, val recede: Boolean, val reflect: Boolean,
    val dens: Float, val cardArtType: com.shelf.launcher.data.CardArtType, val art: Map<String, GameArt>,
    val overrides: Map<String, ArtOverride>, val physicalMedia: Map<String, List<Pair<String, String>>>,
    val screenshots: Map<String, List<String>>, val mixed: Boolean, val realRef: RealCaseRef?, val hidden: Set<String>,
    val focusBorder: Boolean, val accent: Color, val pulseOn: Boolean
) {
    /**
     * Update 166: the biggest case among the games (its longest side, mm) when the 3D cases stand at their real sizes
     * (Box flow, and the Cases · real sizes look in any view); 0: every case fills its slot. Set each composition.
     */
    var caseRefMm = 0f

    /** [Carousel.placeInto] for this carousel's view. */
    fun place(p: Carousel.Place, rel: Float): Carousel.Place = Carousel.placeInto(p, rel, side, view, unit.value)

    /** Where the game at [rel] is drawn: [p] gets its offset from the middle in pixels (x, y) and its final size. */
    fun locate(p: Carousel.Place, rel: Float, spineHere: Boolean, noModel: Boolean, px: androidx.compose.ui.unit.Density) {
        val u = unit.value
        place(p, rel)
        val d = kotlin.math.abs(rel)
        val sign = if (rel < 0f) -1f else 1f
        val near = d.coerceAtMost(1f)
        val far = (d - 1f).coerceAtLeast(0f)
        // Update 146: Receding row, Spines look: the next games as spines one after another to its right, stepping back.
        val recedeSpine = spinesRight && spineHere && noModel && rel > 0f
        // Update 135: spines aside sit lower than the game you're on, all the same height like books on a shelf, packed
        // close one after another from just beside its cover.
        val packed = recedeSpine || (spineHere && spinesAside)
        val scale = if (packed) Carousel.spineScale(d) else p.scale
        val unitK = if (widen) u else 1f
        val bw = with(px) { bigW.toPx() }
        val bh = with(px) { bigH.toPx() }
        val anchor = if (recedeLeft != null) with(px) { recedeLeft.toPx() } + bw * unitK / 2f else 0f
        val x = if (packed) {
            val spineW = bh * Carousel.SPINE_SIZE * SPINE_ASPECT
            val firstX = bw * unitK * (0.5f + 0.07f * Carousel.gap) + spineW / 2
            val stepX = spineW + bw * 0.03f * Carousel.gap
            anchor + (firstX * near + stepX * far) * sign
        } else anchor + bw * unitK * p.x * (if (spines && !cases3d && !recede && view != CarouselView.SPINE_WALL && view != CarouselView.BOX_FLOW) 0.62f else 1f)
        // (Update 146: the Receding row's spines step up towards its vanishing point like its covers do.)
        val yK = if (recedeSpine) -(1f - scale) * 0.3f else p.y
        p.x = x
        p.y = bh * yK - (if (reflect) bh * 0.08f else 0f)
        p.scale = scale
    }
}

/**
 * Update 162: a game's slot height for its shape ([aspect]: width / height) against the middle game's box. In a list
 * mixing consoles every piece gets about a cover's area (a wide cartridge no smaller, a tall card or a round disc no
 * bigger than its neighbours — they jumped in size from one console to the next); otherwise as tall as fits.
 */
private fun carouselSlotH(
    aspect: Float, bigW: androidx.compose.ui.unit.Dp, bigH: androidx.compose.ui.unit.Dp, mixed: Boolean
): androidx.compose.ui.unit.Dp =
    if (mixed) minOf(bigH * kotlin.math.sqrt(0.72f / aspect.coerceAtLeast(0.05f)), bigH, bigW * 1.5f / aspect.coerceAtLeast(0.05f))
    // (Update 199: a wide capsule, hero art or screenshot a good deal wider than a case, so it isn't a thin strip — the
    // row spaces its neighbours by the one you're on's width.)
    else minOf(bigH, if (aspect > 1f) bigW / aspect * (if (aspect >= 1.6f) 1.7f else 1.25f) else bigH)

/** Update 162: one carousel game's own scratch places (filled while placing / drawing on the main thread, never made per frame). */
private class CarouselScratch {
    val p = Carousel.Place(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f)
    val z = Carousel.Place(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f)
    val r = Carousel.Place(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f)
    val s = Carousel.Place(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f)
    val q = Carousel.Place(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f)
}

/**
 * Update 162: one game in the carousel at its place [relOf] (games from the middle, read while placing and drawing only:
 * moving never rebuilds it). Its look decides what it is: a 3D case (case looks in the 3D views), a 3D model, a spine,
 * or its own flat piece (cartridge, card, disc, case, cover) — turned like the others, a piece with its edge showing
 * (MediaBandLayer). While a game is inspected (MediaInspect) the others step back and its own place stays empty.
 */
@Composable
private fun CarouselItem(
    c: CarouselCtx, g: Game, real: Int, i: Int, focusIdx: Int, on: Boolean, interactive: Boolean,
    relOf: () -> Float, fadeOf: () -> Float, onTap: (Int) -> Unit, modifier: Modifier
) {
    val relNow = rememberUpdatedState(relOf)
    val fadeNow = rememberUpdatedState(fadeOf)
    val tapNow = rememberUpdatedState(onTap)
    val ctxNow = rememberUpdatedState(c)
    val ps = remember { CarouselScratch() }
    val cover = c.overrides[g.stableKey]?.cover ?: c.art[g.stableKey]?.gridUrl
    val caseType = c.cardArtType == com.shelf.launcher.data.CardArtType.COVER || c.cardArtType == com.shelf.launcher.data.CardArtType.CASE ||
        c.cardArtType == com.shelf.launcher.data.CardArtType.CASE_REAL
    // Update 117: a game turned almost side-on (Record flip) shows its real spine, facing you, instead of a squashed cover.
    // (Update 162: worked out from the moving place, so only this game redraws when it flips to its spine.)
    val sidewaysCan = !c.cases3d && !c.spines && i != focusIdx && caseType
    val turnedFar = remember(sidewaysCan) {
        androidx.compose.runtime.derivedStateOf { sidewaysCan && kotlin.math.abs(ctxNow.value.place(ps.q, relNow.value()).turnY) > 50f }
    }
    val sideways = turnedFar.value
    // Update 113, Spines look: the games aside as their spines (closer together), the middle one its cover.
    val spineHere = !c.cases3d && ((c.spines && i != focusIdx) || sideways)
    val gArt = artForGame(c.cardArtType, g.platform, c.hidden, c.mixed)
    val freeArt = isFreeMedia(gArt, g.platform)
    // Update 136: with 3D models on, the 3D cases were your 3D shape library's pieces, at the angle nearest their turn.
    // (Update 200: retired — the 3D cases are the drawn Case3D, the 3D cartridges looks drawn shells; never a model.)
    val model: String? = null
    // Slot in the item's own shape (no empty bands round it), as tall as the middle one × its scale.
    // Update 166: "Covers · wide capsule": the one you're on unfolds into its wide capsule (and folds back as you move on).
    val wideHere = on && !spineHere && model == null && !c.cases3d && carouselWideOn(c.cardArtType)
    val aspect0 = if (c.cases3d) case3dAspect(g, c.mixed) else if (spineHere) SPINE_ASPECT
        else if (wideHere) CAPSULE_ASPECT else shelfArtAspect(g, c.cardArtType, c.hidden, c.mixed, cover)
    val aspect by androidx.compose.animation.core.animateFloatAsState(aspect0, androidx.compose.animation.core.tween(220), label = "carouselWide")
    val hFull = if (model != null || spineHere) minOf(c.bigH, if (aspect > 1f) c.bigW / aspect * 1.25f else c.bigH)
        else carouselSlotH(aspect, c.bigW, c.bigH, c.mixed)
    // Update 166: a 3D case at its real size against the biggest case here (Box flow, Cases · real sizes) — a Game Boy box
    // shorter than a DVD case — standing on the same floor (moved down by what it lost). Softened so the small ones
    // stay easy to see.
    val realK = if (c.cases3d && c.caseRefMm > 0f) realCaseMm(g.platform)?.let { (w, h) ->
        Math.pow((maxOf(w, h) / c.caseRefMm).toDouble(), 0.6).toFloat().coerceIn(0.74f, 1f)
    } ?: 1f else 1f
    val h0 = hFull * realK
    val floorDrop = (hFull - h0) / 2f
    // Update 168: the case looks with their media (Case + disc, 3D case + disc, Case + its media) in the 3D-case views
    // too: the disc half out of the case behind it, or the cartridge / disc leaning against it — the views drew the case
    // alone. The slot widens the same both sides, so the case stays in the middle.
    val withMedia = if (c.cases3d && model == null) caseMediaFor(g, c.cardArtType, c.mixed) else null
    // Update 171: only the game you're on shows its media (and gets the room for it): on every game of a packed view (Spine
    // wall, Turned row) the cartridges lay over the neighbours' spines. It comes in and goes as you move.
    val mediaFull = withMedia?.room(realCaseMm(g.platform)) ?: 0f
    val mediaRoom by androidx.compose.animation.core.animateFloatAsState(
        if (i == focusIdx) mediaFull else 0f, androidx.compose.animation.core.tween(240), label = "caseMediaRoom"
    )
    val mediaShow = if (mediaFull > 0f) (mediaRoom / mediaFull).coerceIn(0f, 1f) else 0f
    // (Update 174: room on its right only — the case and its media together sit in the middle; the gap on the left went.)
    val w0 = h0 * aspect + h0 * mediaRoom
    // A 3D case or model turns in its own drawing, a spine faces you: only the other flat art turns its slot.
    // (Update 144: at most 78°, never edge-on.)
    val turns = !(sideways || c.cases3d || model != null || (spineHere && (c.view == CarouselView.SPINE_WALL || c.view == CarouselView.BOX_FLOW)))
    val piece = turns && !spineHere && freeArt
    val band = if (piece) remember(gArt, g.platform, c.mixed, c.cardArtType) { mediaBandFor(gArt, g.platform, c.mixed, c.cardArtType) } else null
    val turnOf: () -> Float = {
        if (!turns) 0f else {
            val rel = relNow.value()
            // Update 158: a piece you're on turned like the others with "Turned like the others" (PickedFacing).
            (c.place(ps.r, rel).turnY + (if (piece) PickedFacing.carouselTurn(kotlin.math.abs(rel)) else 0f)).coerceIn(-78f, 78f)
        }
    }
    // Update 158: the one you're on faces you in every view (its turn there is 0); with "Turned like the others"
    // (PickedFacing.kt) it gets a turn of its own, fading in as it reaches the middle.
    val yawOf: () -> Float = { val rel = relNow.value(); c.place(ps.r, rel).turnY + PickedFacing.carouselTurn(kotlin.math.abs(rel)) }
    val shadeOf: () -> Float = { c.place(ps.s, relNow.value()).shade }
    Box(
        modifier
            // The drawing order, read while placing (zIndex is otherwise fixed at composition).
            .layout { m, cons ->
                val pl = m.measure(cons)
                layout(pl.width, pl.height) {
                    val z = c.place(ps.z, relNow.value()).z
                    // (Update 135: a spine never covers the game you're on while the carousel slides.)
                    pl.place(0, 0, zIndex = if (spineHere) z - 50f else z)
                }
            }
            .size(w0, h0)
            .graphicsLayer {
                val rel = relNow.value()
                c.locate(ps.p, rel, spineHere, model == null, this)
                var a = ps.p.alpha * fadeNow.value()
                var s = ps.p.scale
                var tx = ps.p.x
                // Update 162: a game opened to inspect (MediaInspect): the others step back, fade and part a little; its
                // own place stays empty while the game window draws it flying out.
                val ins = MediaInspect.amount.floatValue
                if (ins > 0f) {
                    // (Update 162 (review): only the one you're on — on a short endless row the same game shows more than once.)
                    if (on && g.stableKey == MediaInspect.key) a = 0f
                    else {
                        s *= 1f - 0.1f * ins
                        a *= 1f - 0.65f * ins
                        tx += (if (rel < 0f) -1f else 1f) * ins * 0.16f * c.bigW.toPx()
                    }
                }
                translationX = tx
                translationY = ps.p.y + floorDrop.toPx() * s
                scaleX = s; scaleY = s
                rotationZ = ps.p.turnZ
                alpha = a
                // (Faded op by op: a 3D case's reflection is drawn outside its slot, and no slot-sized buffer per game.)
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
            }
            .then(if (interactive) Modifier.pointerInput(real) { detectTapGestures { tapNow.value(real) } } else Modifier)
            .then(if (on) Modifier.focusedCard() else Modifier)
    ) {
        // (Update 166: the side band is off — not even its empty layer per piece. See MediaBandLayer.)
        // (Was: MediaBandLayer(band, c.dens, turnOf, shadeOf).)
        // Update 203: a cartridge turns as a solid piece (its thickness showing) — the slot hands it the turn instead of
        // turning the flat picture (SolidPiece.kt).
        val solidHere = turns && !spineHere && !wideHere && gArt == com.shelf.launcher.data.CardArtType.CARTRIDGE_DISC
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                rotationY = if (solidHere) 0f else turnOf()
                cameraDistance = 12f * c.dens
                if (rotationY != 0f) flatTextFor3d()
            }
        ) { ProvidePieceTurn(if (solidHere) PieceTurn(turnOf, 12f) else null) {
            // (Update 200: CarouselModel — a game as its pre-rendered 3D model — is gone.)
            if (c.cases3d && withMedia != null) CaseWithMedia3D(
                g, cover, c.physicalMedia[g.stableKey], aspect, h0, withMedia, c, on, yawOf, shadeOf, mediaShow
            )
            else if (c.cases3d) Case3D(
                g, cover, c.physicalMedia[g.stableKey], 0f, aspect, Modifier.fillMaxSize(), reflect = c.reflect,
                frame = on && c.focusBorder, frameColor = c.accent, yawOf = yawOf, shadeOf = shadeOf,
                realFront = c.cardArtType == com.shelf.launcher.data.CardArtType.CASE || c.cardArtType == com.shelf.launcher.data.CardArtType.CASE_REAL
            ) else CarouselArt(g, on, spineHere, c.reflect, h0, shadeOf, if (wideHere) com.shelf.launcher.data.CardArtType.CAPSULE else c.cardArtType, c.art, c.overrides, c.physicalMedia, c.screenshots, c.mixed, c.realRef, c.pulseOn)
        } }
    }
}

/** Update 168: how a 3D case in a carousel shows its media: the disc half out of it, or the piece leaning against it. */
private class CaseMedia(val pick: MediaPick, val discOut: Boolean) {
    private val round get() = pick.shell.disc || pick.shell == MediaShell.UMD
    /** The media's height against the case's. */
    fun size(caseMm: Pair<Float, Float>?): Float =
        if (discOut) ((caseMm?.let { 120f / it.second } ?: 0.63f) * 0.92f).coerceIn(0.45f, 0.95f) else if (round) 0.6f else 0.5f
    /** Its width against its height. */
    val aspect: Float get() = if (round) 1f else pick.aspect
    /** How far it reaches past the case's right edge, against the case's height. */
    fun room(caseMm: Pair<Float, Float>?): Float = size(caseMm) * aspect * (if (discOut) 0.5f else 0.65f)
}

/** Update 168: [g]'s media beside its 3D case in the look [type] (Case + disc / 3D case + disc / Case + its media), or null. */
private fun caseMediaFor(g: Game, type: com.shelf.launcher.data.CardArtType, mixed: Boolean): CaseMedia? {
    if (type != com.shelf.launcher.data.CardArtType.CASE && type != com.shelf.launcher.data.CardArtType.CASE_REAL) return null
    val disc = Features.discCase.state.value
    val lean = Features.caseWithMedia.state.value
    if (!disc && !lean) return null
    val pick = mediaFor(g.platform, mixed, gameKey = g.stableKey) ?: return null
    val round = pick.shell.disc || pick.shell == MediaShell.UMD
    return when {
        lean -> CaseMedia(pick, discOut = false)
        disc && round -> CaseMedia(pick, discOut = true)
        else -> null
    }
}

/**
 * Update 168: the 3D case with its media, turning together: the disc behind it, half out at its right (spinning), or the
 * cartridge / disc leaning against its lower right in front.
 */
@Composable
private fun CaseWithMedia3D(
    g: Game, cover: String?, physical: List<Pair<String, String>>?, caseAspect: Float, h: androidx.compose.ui.unit.Dp,
    m: CaseMedia, c: CarouselCtx, on: Boolean, yawOf: () -> Float, shadeOf: () -> Float,
    /** Update 171: how far the media is in (0: not shown — only the game you're on has it). */
    show: Float = 1f
) {
    val mm = realCaseMm(g.platform)
    val mh = h * m.size(mm)
    val mw = mh * m.aspect
    val caseW = h * caseAspect
    // Update 173: the media matched to the case: the real cartridge / disc beside the game's real (scanned) case, Voltcap's
    // own drawn one (with the game's label scan, else its cover) beside a template case — whatever the drawn-media setting.
    val scans = physical ?: HomeScans.map[g.stableKey]
    val caseReal = Features.originalCases.state.value && !LookStyles.caseForAll() && BoxTexture.front(scans, g.platform) != null
    val realMedia = if (caseReal) BoxTexture.media(scans) else null
    val label = scans?.firstOrNull { it.first == "Disc label" }?.second
    // (Update 173: an app without art wears its icon on its cartridge's label, as the cards do — it was blank.)
    val appIcon = if (cover == null && g.sourceType == com.shelf.launcher.data.SourceType.APP) {
        rememberAppIcon(g.packageName)?.let { remember(it) { it.asAndroidBitmap() } }
    } else null
    val dens = c.dens
    @Composable
    fun MediaPiece(mod: Modifier) {
        if (show <= 0.01f) return
        // (Update 203: turned with the case as a solid piece — a cartridge its thickness, a disc flat; SolidPiece.kt.)
        Box(mod.graphicsLayer {
            val sh = shadeOf()
            alpha = (1f - 0.35f * sh) * show
        }) { ProvidePieceTurn(PieceTurn(yawOf, 12f)) {
            // (Update 171: the real photo is the piece itself — it was once put on the drawn cartridge's label, a small
            // cartridge inside the cartridge. Update 173: beside a real case only.)
            if (realMedia != null) RealMediaPicture(realMedia, m.pick.shell.disc || m.pick.shell == MediaShell.UMD, Modifier.fillMaxSize(), platform = g.platform, gameKey = g.stableKey)
            else PhysicalMedia(m.pick.shell, label ?: cover ?: appIcon, Modifier.fillMaxSize(), platform = m.pick.platform, labelIsScan = label != null, caseCover = cover ?: appIcon, title = g.name, noCase = true)
        } }
    }
    // (Update 174: the case moved left by half the media's room, so the pair is centred in its slot.)
    val pairShift = h * m.room(mm) * show / 2f
    Box(Modifier.fillMaxSize().offset(x = -pairShift), contentAlignment = Alignment.Center) {
        // The disc behind the case, its middle at the case's right edge.
        if (m.discOut) MediaPiece(Modifier.size(mw, mh).offset(x = caseW / 2f))
        Box(Modifier.size(caseW, h)) {
            Case3D(
                g, cover, physical, 0f, caseAspect, Modifier.fillMaxSize(), reflect = c.reflect,
                frame = on && c.focusBorder, frameColor = c.accent, yawOf = yawOf, shadeOf = shadeOf, realFront = true
            )
        }
        // The cartridge / disc leaning against the case's lower right, in front.
        if (!m.discOut) MediaPiece(Modifier.size(mw, mh).offset(x = caseW / 2f + mw * 0.15f, y = (h - mh) / 2f).graphicsLayer { rotationZ = 8f })
    }
}

/** Update 162: a flat piece's edge: how thick it is (a share of its width), its colour, and whether it's round (a disc). */
private class MediaBand(val thick: Float, val color: Color, val round: Boolean)

/** Update 162: the edge of [platform]'s piece in the [gArt] look (real proportions, a disc a little thicker to read); null: none. */
private fun mediaBandFor(
    gArt: com.shelf.launcher.data.CardArtType, platform: String, mixed: Boolean, type: com.shelf.launcher.data.CardArtType
): MediaBand? {
    // A game case's plastic (box art drawn flat).
    if (gArt == com.shelf.launcher.data.CardArtType.CASE) return MediaBand(0.1f, Color(0xFF23252C), false)
    if (gArt != com.shelf.launcher.data.CardArtType.CARTRIDGE_DISC) return null
    val pick = mediaFor(platform, mixed, type == com.shelf.launcher.data.CardArtType.DISC) ?: return null
    if (isCustomPiece(pick.platform)) return MediaBand(0.13f, Color(0xFF46484F), false)
    val grey = Color(0xFF7C7E85)
    val dark = Color(0xFF34363C)
    return when (pick.shell) {
        MediaShell.DISC, MediaShell.DISC_DARK, MediaShell.DISC_WII, MediaShell.DISC_GD, MediaShell.MINI_DISC -> MediaBand(0.022f, Color(0xFFB4B8C0), true)
        MediaShell.UMD -> MediaBand(0.07f, dark, false)
        MediaShell.N64 -> MediaBand(0.16f, dark, false)
        MediaShell.SNES -> MediaBand(0.17f, grey, false)
        MediaShell.NES -> MediaBand(0.14f, grey, false)
        MediaShell.GB, MediaShell.GBC, MediaShell.VIRTUAL_BOY -> MediaBand(0.14f, grey, false)
        MediaShell.GBA -> MediaBand(0.14f, dark, false)
        MediaShell.DS, MediaShell.DS3, MediaShell.VITA, MediaShell.CARD -> MediaBand(0.11f, dark, false)
        MediaShell.SWITCH -> MediaBand(0.15f, dark, false)
        MediaShell.HUCARD -> MediaBand(0.02f, Color(0xFFD4D4D8), false)
        MediaShell.BLACK_CART -> MediaBand(0.15f, Color(0xFF1E1F23), false)
        MediaShell.GAME_GEAR -> MediaBand(0.12f, dark, false)
        MediaShell.ATARI -> MediaBand(0.22f, Color(0xFF26272B), false)
        else -> MediaBand(0.13f, dark, false)
    }
}

/**
 * Update 162: the side of a flat piece turned in the carousel, so a turned cartridge, card or disc has its thickness
 * and never thins to a line: its outline drawn again behind it, moved out on the side turned toward you by its
 * thickness (as far as that side shows at this turn), and lit by the turn — only the strip past the piece shows. Drawn
 * in its own turned layer (the piece's own layer is cut to its slot), read while drawing only, nothing made per frame.
 */
@Composable
private fun MediaBandLayer(band: MediaBand, dens: Float, turnOf: () -> Float, shadeOf: () -> Float) {
    val turnNow = rememberUpdatedState(turnOf)
    val shadeNow = rememberUpdatedState(shadeOf)
    // (Update 162 (review): made again for a new band — a disc's circle stayed a rectangle's outline, or the other way.)
    val path = remember(band) { androidx.compose.ui.graphics.Path() }
    val made = remember(band) { FloatArray(2) }
    val rim = remember(dens) { Stroke(width = 1.2f * dens) }
    Canvas(Modifier.fillMaxSize().graphicsLayer { rotationY = turnNow.value(); cameraDistance = 12f * dens }) {
        val t = turnNow.value()
        val a = kotlin.math.abs(t)
        // (Update 165: only while the front still shows well — past 60° the piece is mostly its side and the band only
        // added a dark block.)
        // (Update 166: off — the side was a rectangle shifted behind the piece, which never matched a cartridge's or a
        // card's own outline: a grey block peeked out beside the games aside. The real thickness comes from the 3D looks.)
        if (false && a >= 3f && a <= 60f) {
            // (ShelfGameArt draws a piece 4 dp in from its slot.)
            val inset = 4f * dens
            val w = (size.width - inset * 2f).coerceAtLeast(1f)
            val h = (size.height - inset * 2f).coerceAtLeast(1f)
            if (made[0] != size.width || made[1] != size.height) {
                made[0] = size.width; made[1] = size.height
                path.reset()
                if (band.round) path.addOval(androidx.compose.ui.geometry.Rect(center, minOf(w, h) / 2f))
                else path.addRoundRect(androidx.compose.ui.geometry.RoundRect(inset, inset, inset + w, inset + h, androidx.compose.ui.geometry.CornerRadius(w * 0.04f)))
            }
            val rad = Math.toRadians(a.toDouble())
            val across = if (band.round) minOf(w, h) else w
            // Seen at turn a, a side t deep shows t·sin a wide; in the turned layer that's t·tan a.
            // (Update 165: capped — near side-on tan grows without end, and turned 70–80° in Box flow the side stood out as a
            // wide dark slab next to the games aside. Now at most about one and a half thicknesses.)
            val shift = band.thick * across * kotlin.math.tan(rad).toFloat().coerceAtMost(1.5f) * (if (t > 0f) -1f else 1f)
            // (Update 165: lighter, so the side reads as the shell's plastic, not a black strip.)
            val l = ((0.7f + 0.3f * kotlin.math.sin(rad).toFloat()) * (1f - 0.6f * shadeNow.value())).coerceIn(0f, 1f)
            val col = Color(band.color.red * l, band.color.green * l, band.color.blue * l)
            val edge = Color((band.color.red * l + 0.12f).coerceAtMost(1f), (band.color.green * l + 0.12f).coerceAtMost(1f), (band.color.blue * l + 0.12f).coerceAtMost(1f))
            clipPath(path, androidx.compose.ui.graphics.ClipOp.Difference) {
                translate(left = shift, top = 0f) {
                    drawPath(path, col)
                    drawPath(path, edge, style = rim)
                }
            }
        }
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
    g: Game, on: Boolean, spineHere: Boolean, reflect: Boolean, h0: androidx.compose.ui.unit.Dp, shadeOf: () -> Float,
    cardArtType: com.shelf.launcher.data.CardArtType, art: Map<String, GameArt>, overrides: Map<String, ArtOverride>,
    physicalMedia: Map<String, List<Pair<String, String>>>, screenshots: Map<String, List<String>>,
    mixed: Boolean, realRef: RealCaseRef?, pulseOn: Boolean
) {
    // Update 162: the focus pulse only for the game you're on (it used to recompose the whole carousel).
    val pulse = if (on) rememberFocusPulse(animate = pulseOn) else 0f
    // Update 162: the shade follows the moving place while drawing; only whether there is one is decided here.
    val shadeNow = rememberUpdatedState(shadeOf)
    val shaded by remember { androidx.compose.runtime.derivedStateOf { shadeNow.value() > 0.01f } }
    Box(Modifier.fillMaxSize()) {
        // Update 143: the games aside darkened only where they're drawn — the shade used to be a dark box over the whole
        // slot, showing round a case, cartridge or disc smaller than it.
        Box(
            Modifier.fillMaxSize().then(
                if (shaded) Modifier
                    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val shade = shadeNow.value()
                        if (shade > 0.01f) drawRect(Color.Black.copy(alpha = shade.coerceAtMost(1f)), blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop)
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
