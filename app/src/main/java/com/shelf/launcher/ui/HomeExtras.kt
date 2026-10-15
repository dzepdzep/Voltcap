package com.shelf.launcher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.Features
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlin.math.abs

/*
 * Home screen extras (update 27): the games slide in when the category changes, a soft light in the colours
 * of the game you're on behind its card, and the status bar's battery as a little charge gauge.
 */

/** Which way the last category change went (+1 next, -1 previous): the slide comes from that side. */
internal object CategorySlide {
    var dir by mutableIntStateOf(1)
    /** The category on screen (set by the home screen). */
    var key by mutableStateOf("")
    /**
     * Counts L2 / R2 category steps. The slide plays only on a step: it used to follow [key], and anything
     * that rewrote the category (a library refresh while art was saving) replayed it — the row kept
     * jumping right and back.
     */
    var tick by mutableIntStateOf(0)
    fun step(direction: Int) { dir = direction; tick++ }
    /**
     * Update 162: carousels on screen (GameCarousel) that slide their own collection along their path on a category
     * change — the slide round them ([categorySlide]) then stays still, so the two never move at once.
     */
    var pathOwners = 0
}

/**
 * The games slide in from the side the category change came from (Appearance › Game row › Slide between
 * categories). Plays on each L2 / R2 step ([CategorySlide.tick]); only the drawing moves (graphicsLayer), never the layout.
 */
@Composable
internal fun Modifier.categorySlide(): Modifier {
    val tick = CategorySlide.tick
    val anim = remember { Animatable(0f) }
    var first by remember { mutableStateOf(true) }
    // Update 112: Slide / Fade / Just change (Home › Animations, per layout too).
    MotionPrefs.observe()
    val kind = MotionPrefs.page()
    val on = Features.categorySlide.state.value && !LocalLiteEffects.current && kind != Features.PageAnim.NONE
    val slide = kind == Features.PageAnim.SLIDE
    LaunchedEffect(tick) {
        if (first) { first = false; return@LaunchedEffect }
        // (Update 162: not round a carousel that slides its games along its own path, CategorySlide.pathOwners.)
        if (!on || CategorySlide.pathOwners > 0) return@LaunchedEffect
        anim.snapTo(CategorySlide.dir.toFloat())
        // (Update 207: started once the new category has been drawn — two frames on — and a little longer, so the time
        // spent making it doesn't eat the slide: it jumped part of the way, then rushed.)
        androidx.compose.runtime.withFrameNanos { }
        androidx.compose.runtime.withFrameNanos { }
        anim.animateTo(0f, tween(340, easing = FastOutSlowInEasing))
    }
    return this.graphicsLayer {
        translationX = if (slide) anim.value * 64.dp.toPx() else 0f
        alpha = 1f - abs(anim.value) * (if (slide) 0.75f else 1f)
    }
}

/**
 * A soft light in the colours of the game you're on (its cover's palette), centred behind its card and
 * following it. Draw it behind the games. Only the drawing updates as the card moves.
 */
@Composable
internal fun CoverLight(artUrl: Any?) {
    val palette = rememberArtPalette(artUrl, enabled = artUrl != null)
    val c1 by animateColorAsState(palette?.accent ?: ShelfTheme.accent, tween(600), label = "coverLight1")
    val c2 by animateColorAsState(palette?.secondary ?: ShelfTheme.colors.secondary, tween(600), label = "coverLight2")
    var origin by remember { mutableStateOf(Offset.Zero) }
    Canvas(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        val r = FocusedCardRect.rect ?: return@Canvas
        val center = Offset(r.center.x - origin.x, r.center.y - origin.y)
        val radius = maxOf(r.width, r.height) * 1.25f + 60.dp.toPx()
        drawCircle(
            Brush.radialGradient(
                0f to c1.copy(alpha = 0.42f), 0.45f to c2.copy(alpha = 0.16f), 1f to Color.Transparent,
                center = center, radius = radius
            ),
            radius, center
        )
    }
}

/**
 * The battery as a little charge gauge: five cells in the theme's colours (red when low), a bolt over it while
 * charging. Square in the 32-bit console look.
 */
@Composable
internal fun VoltBattery(percent: Int, charging: Boolean, low: Color) {
    val accent = ShelfTheme.accent
    val second = lerpColor(accent, ShelfTheme.colors.secondary, 0.6f)
    val pixel = VoltUi.pixel
    val pulse = if (charging) rememberFocusPulse(animate = true) else 1f
    Canvas(Modifier.size(width = 26.dp, height = 13.dp)) {
        val nub = 2.5.dp.toPx()
        val w = size.width - nub
        val h = size.height
        val stroke = 1.3.dp.toPx()
        val corner = CornerRadius(if (pixel) 0f else 3.dp.toPx())
        drawRoundRect(Color.White.copy(alpha = 0.85f), topLeft = Offset(stroke / 2, stroke / 2), size = Size(w - stroke, h - stroke), cornerRadius = corner, style = Stroke(stroke))
        drawRect(Color.White.copy(alpha = 0.85f), topLeft = Offset(w, h * 0.3f), size = Size(nub, h * 0.4f))
        val isLow = !charging && percent < 15
        val cells = 5
        val filled = ((percent + 19) / 20).coerceIn(0, cells)
        val pad = 2.4.dp.toPx()
        val gap = 1.dp.toPx()
        val cw = (w - pad * 2 - gap * (cells - 1)) / cells
        for (i in 0 until filled) {
            val t = if (cells > 1) i / (cells - 1f) else 0f
            val c = if (isLow) low else lerpColor(accent, second, t)
            drawRect(c, topLeft = Offset(pad + i * (cw + gap), pad), size = Size(cw, h - pad * 2))
        }
        if (charging) {
            val bw = h * 0.55f; val bh = h * 0.95f
            val x0 = (w - bw) / 2; val y0 = (h - bh) / 2
            drawPath(Path().apply {
                moveTo(x0 + bw * 0.62f, y0); lineTo(x0 + bw * 0.05f, y0 + bh * 0.58f); lineTo(x0 + bw * 0.46f, y0 + bh * 0.58f)
                lineTo(x0 + bw * 0.34f, y0 + bh); lineTo(x0 + bw * 0.95f, y0 + bh * 0.38f); lineTo(x0 + bw * 0.54f, y0 + bh * 0.38f); close()
            }, Color.White.copy(alpha = 0.6f + 0.4f * pulse))
        }
    }
}

/**
 * A light blur of the background (and a soft shade) only behind the games, their names and the categories —
 * [band] in root pixels — fading out above and below so there's no hard edge. [strength] 1..100. Android 12+
 * blurs; older versions (and [liteBlur]) get just the soft shade. [backdrop] redraws the background to blur.
 */
@Composable
internal fun SoftRowBlur(band: androidx.compose.ui.geometry.Rect, strength: Int, liteBlur: Boolean, backdrop: @Composable () -> Unit) {
    val k = strength / 100f
    val canBlur = !liteBlur && android.os.Build.VERSION.SDK_INT >= 31
    val feather = with(androidx.compose.ui.platform.LocalDensity.current) { 46.dp.toPx() }
    fun mask(w: Float, h: Float): Brush = softBandBrush(band, w, h, feather, Color.Black)
    if (canBlur) {
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize()
                .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(mask(size.width, size.height), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
                }
                .blur((6f + 18f * k).dp, androidx.compose.ui.draw.BlurredEdgeTreatment.Rectangle)
        ) { backdrop() }
    }
    // The soft shade on top of it (the only part on older Android).
    Canvas(Modifier.fillMaxSize()) {
        val m = mask(size.width, size.height)
        drawRect(m, alpha = (if (canBlur) 0.22f else 0.38f) * (0.4f + 0.6f * k))
    }
}

/**
 * A soft mask for the game-UI band: [color] over [band] (root pixels), fading out over [feather] instead of
 * a hard edge. A band that starts at the left edge (Hero layout) fades out to the right, a row band fades
 * out above and below. Edges that touch the screen edge stay solid to the edge.
 */
internal fun softBandBrush(band: androidx.compose.ui.geometry.Rect, w: Float, h: Float, feather: Float, color: Color): Brush {
    val ww = w.coerceAtLeast(1f)
    val hh = h.coerceAtLeast(1f)
    val clear = color.copy(alpha = 0f)
    val sideBand = band.top <= 0.5f && band.bottom >= hh - 0.5f && band.right < ww - 0.5f
    return if (sideBand) {
        val inR = (band.right / ww).coerceIn(0f, 1f)
        val outR = ((band.right + feather * 2.5f) / ww).coerceIn(inR, 1f)
        Brush.horizontalGradient(0f to color, inR to color, outR to clear, 1f to clear, startX = 0f, endX = ww)
    } else {
        val topIn = (band.top / hh).coerceIn(0f, 1f)
        val botIn = (band.bottom / hh).coerceIn(topIn, 1f)
        val top = ((band.top - feather) / hh).coerceIn(0f, topIn)
        val bot = ((band.bottom + feather) / hh).coerceIn(botIn, 1f)
        val stops = ArrayList<Pair<Float, Color>>()
        // Touching the screen edge: solid right up to it.
        if (band.top <= 0.5f) stops += 0f to color else { stops += 0f to clear; stops += top to clear; stops += topIn to color }
        if (band.bottom >= hh - 0.5f) stops += 1f to color else { stops += botIn to color; stops += bot to clear; stops += 1f to clear }
        Brush.verticalGradient(*stops.toTypedArray(), startY = 0f, endY = hh)
    }
}

/** The background blurred only inside the soft game-UI band (Theme look › Blur area: Game UI only). */
@Composable
internal fun SoftBandBlur(band: androidx.compose.ui.geometry.Rect, radius: androidx.compose.ui.unit.Dp, backdrop: @Composable () -> Unit) {
    val feather = with(androidx.compose.ui.platform.LocalDensity.current) { 46.dp.toPx() }
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxSize()
            .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(softBandBrush(band, size.width, size.height, feather, Color.Black), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
            }
            .blur(radius, androidx.compose.ui.draw.BlurredEdgeTreatment.Rectangle)
    ) { backdrop() }
}

/**
 * Text under a 3D turn (rotationX / rotationY, i.e. perspective) can't be reused by Android's renderer: every frame
 * adds a new cached copy and the cache clean-up gets slower and slower until the app stops answering (seen on the
 * Thor, Android 13). Call last in a graphicsLayer block: a turned layer is then drawn flat into its own buffer and
 * only that picture is turned.
 */
internal fun androidx.compose.ui.graphics.GraphicsLayerScope.flatTextFor3d() {
    if (rotationX != 0f || rotationY != 0f) compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
}
