package com.shelf.launcher.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.HeroMotion
import com.shelf.launcher.ui.theme.ShelfTheme

/*
 * Settings › previews of the animations (update 109): each choice shows what it does, looping, as you pick it. Drawn
 * with simple shapes in the theme's colours, so they cost nothing and never need a game's art.
 */

private val PreviewW = 176.dp
private val PreviewH = 99.dp

/** A 0..1 clock that loops every [periodMs] while the preview is on screen. */
@Composable
private fun loopClock(periodMs: Int): Float {
    val t = rememberInfiniteTransition(label = "animPreview")
    val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart), label = "animPreviewT")
    return v
}

/** The frame round a preview, with its caption. */
@Composable
internal fun PreviewFrame(caption: String? = null, width: Dp = PreviewW, height: Dp = PreviewH, content: @Composable BoxScope.() -> Unit) {
    Column {
        Box(
            Modifier.size(width, height).clip(RoundedCornerShape(10.dp)).background(Color(0xFF0E1117))
                .border(1.dp, ShelfTheme.accent.copy(alpha = 0.45f), RoundedCornerShape(10.dp)).clipToBounds(),
            content = content
        )
        if (caption != null) Text(caption, color = ShelfTheme.textMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

/** One of two little "pictures" (a sky, a sun, hills) in the theme's colours, so motion between them reads clearly. */
private fun DrawScope.picture(which: Int, accent: Color, secondary: Color) {
    val w = size.width; val h = size.height
    val sky = if (which == 0) listOf(accent.copy(alpha = 0.85f), Color(0xFF151A2A)) else listOf(secondary.copy(alpha = 0.85f), Color(0xFF221420))
    drawRect(Brush.verticalGradient(sky))
    drawCircle(Color.White.copy(alpha = 0.85f), radius = h * 0.14f, center = Offset(if (which == 0) w * 0.7f else w * 0.28f, h * 0.36f))
    val hill = Path().apply {
        moveTo(0f, h); lineTo(0f, h * 0.7f)
        if (which == 0) { lineTo(w * 0.3f, h * 0.5f); lineTo(w * 0.55f, h * 0.72f); lineTo(w * 0.8f, h * 0.55f) }
        else { lineTo(w * 0.25f, h * 0.62f); lineTo(w * 0.5f, h * 0.45f); lineTo(w * 0.75f, h * 0.66f) }
        lineTo(w, h * 0.62f); lineTo(w, h); close()
    }
    drawPath(hill, Color.Black.copy(alpha = 0.55f))
}

/** A picture box: [which] picture drawn into the whole frame. */
@Composable
private fun Picture(which: Int, modifier: Modifier) {
    val a = ShelfTheme.accent
    val s = ShelfTheme.colors.secondary
    Canvas(modifier.fillMaxSize()) { picture(which, a, s) }
}

private fun ease(x: Float): Float { val c = x.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }

/** Background art motion (Home › Background › Motion & transitions, and a game's own): the next picture coming in. */
@Composable
internal fun HeroMotionPreview(motion: HeroMotion, caption: String? = motion.label) {
    val t = loopClock(4800)
    // Two changes per loop: picture 0 → 1, then 1 → 0.
    val half = (t * 2f) % 1f
    val to = if (t < 0.5f) 1 else 0
    val from = 1 - to
    val speed = Features.transitionSpeed.state.value.ms / 240f
    val dur = (when (motion) { HeroMotion.QUICK, HeroMotion.QUICK_ZOOM, HeroMotion.KEN_BURNS -> 0.16f; HeroMotion.CROSSFADE -> 0.22f; else -> 0.3f } * speed).coerceIn(0.08f, 0.6f)
    val q = ease(half / dur)
    PreviewFrame(caption) {
        // The one going.
        Box(Modifier.fillMaxSize().graphicsLayer {
            when (motion) {
                HeroMotion.CROSSFADE -> alpha = 1f - q
                HeroMotion.PARALLAX -> translationX = -q * size.width * 0.35f
                HeroMotion.ZOOM_THROUGH -> { scaleX = 1f + 0.25f * q; scaleY = scaleX; alpha = 1f - q }
                HeroMotion.COVER_FLOW -> { rotationY = -90f * q; cameraDistance = 8f * density }
                HeroMotion.MEMORY_CARD -> { translationY = -q * size.height * 0.4f; alpha = 1f - q }
                else -> Unit
            }
            flatTextFor3d() // Update 177: drawn flat, then turned (Android 13 text cache)
        }) { Picture(from, Modifier) }
        // The one coming in.
        Box(Modifier.fillMaxSize().graphicsLayer {
            when (motion) {
                HeroMotion.CROSSFADE, HeroMotion.QUICK -> alpha = q
                HeroMotion.QUICK_ZOOM -> { alpha = q; val z = 1f + 0.08f * half; scaleX = z; scaleY = z }
                HeroMotion.KEN_BURNS -> { alpha = q; val z = 1.04f + 0.1f * half; scaleX = z; scaleY = z; translationX = -half * size.width * 0.06f }
                HeroMotion.SLIDE -> { alpha = q; translationX = (1f - q) * size.width * 0.18f }
                HeroMotion.PARALLAX -> translationX = (1f - q) * size.width
                HeroMotion.ZOOM_THROUGH -> { val z = 1.18f - 0.18f * q; scaleX = z; scaleY = z; alpha = q }
                HeroMotion.COVER_FLOW -> { rotationY = 90f * (1f - q); cameraDistance = 8f * density; alpha = if (q > 0.5f) 1f else 0f }
                HeroMotion.MEMORY_CARD -> translationY = (1f - q) * size.height
                else -> Unit
            }
            flatTextFor3d() // Update 177: drawn flat, then turned (Android 13 text cache)
        }.drawWithContent {
            when (motion) {
                // A growing circle from the middle.
                HeroMotion.IRIS -> {
                    val r = q * kotlin.math.hypot(size.width, size.height) / 2f
                    val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, r)) }
                    clipPath(circle) { this@drawWithContent.drawContent() }
                }
                // Blocks scattered over the frame.
                HeroMotion.PIXEL -> {
                    val n = 10; val m = 6
                    val bw = size.width / n; val bh = size.height / m
                    for (i in 0 until n) for (j in 0 until m) {
                        val seed = ((i * 73 + j * 151) % 97) / 97f
                        if (seed < q) clipRect(i * bw, j * bh, (i + 1) * bw + 1f, (j + 1) * bh + 1f) { this@drawWithContent.drawContent() }
                    }
                }
                // An old TV: a bright line opening into the picture.
                HeroMotion.CRT -> {
                    val open = ease((q - 0.25f) / 0.75f)
                    val bandH = 2.dp.toPx() + open * size.height
                    val top = (size.height - bandH) / 2f
                    clipRect(0f, top, size.width, top + bandH) { this@drawWithContent.drawContent() }
                    if (q < 1f) drawRect(Color.White.copy(alpha = (1f - open) * 0.9f), Offset(0f, top), Size(size.width, bandH))
                }
                else -> drawContent()
            }
        }) { Picture(to, Modifier) }
    }
}

/** Cover → wide capsule: how the game you're on opens wide. */
@Composable
internal fun CapsuleRevealPreview(style: Features.CapsuleReveal, caption: String? = style.label) {
    val t = loopClock(2800)
    // Cover alone, opening, wide, back to the cover.
    val q = when {
        t < 0.25f -> 0f
        t < 0.6f -> ease((t - 0.25f) / 0.35f)
        t < 0.9f -> 1f
        else -> 1f - ease((t - 0.9f) / 0.1f)
    }
    val a = ShelfTheme.accent
    val s = ShelfTheme.colors.secondary
    PreviewFrame(caption) {
        val cardH = 64.dp
        val wideW = cardH * 2.14f
        val coverW = cardH * 2f / 3f
        Box(Modifier.align(Alignment.Center).size(wideW, cardH)) {
            // The cover (left part of the slot).
            val cover: @Composable (Modifier) -> Unit = { m ->
                Box(m.size(coverW, cardH).clip(RoundedCornerShape(5.dp)).background(Brush.verticalGradient(listOf(s, Color(0xFF241A2E)))))
            }
            val wide: @Composable (Modifier) -> Unit = { m ->
                Box(m.size(wideW, cardH).clip(RoundedCornerShape(5.dp))) { Canvas(Modifier.fillMaxSize()) { picture(0, a, s) } }
            }
            when (style) {
                Features.CapsuleReveal.ROLL, Features.CapsuleReveal.WIPE -> {
                    cover(Modifier)
                    wide(Modifier.drawWithContent {
                        val x = q * size.width
                        clipRect(0f, 0f, x, size.height) { this@drawWithContent.drawContent() }
                        if (style == Features.CapsuleReveal.ROLL && q in 0.01f..0.99f) {
                            drawRect(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), x - 14.dp.toPx(), x), Offset(x - 14.dp.toPx(), 0f), Size(14.dp.toPx(), size.height))
                        }
                    })
                }
                // Update 129: off — the wide art is just there.
                Features.CapsuleReveal.NONE -> if (q < 0.5f) cover(Modifier) else wide(Modifier)
                Features.CapsuleReveal.SLIDE -> {
                    cover(Modifier.graphicsLayer { translationX = -q * coverW.toPx(); alpha = 1f - q })
                    wide(Modifier.graphicsLayer { translationX = (1f - q) * size.width; alpha = q })
                }
                Features.CapsuleReveal.FLIP -> {
                    if (q < 0.5f) cover(Modifier.graphicsLayer { rotationY = q * 180f; cameraDistance = 10f * density ; flatTextFor3d() })
                    else wide(Modifier.graphicsLayer { rotationY = (q - 1f) * 180f; cameraDistance = 10f * density ; flatTextFor3d() })
                }
                Features.CapsuleReveal.SWEEP -> {
                    cover(Modifier.graphicsLayer { alpha = 1f - q })
                    wide(Modifier.graphicsLayer { alpha = q }.drawWithContent {
                        drawContent()
                        if (q in 0.01f..0.99f) {
                            val x = q * size.width * 1.3f - size.width * 0.15f
                            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.6f), Color.Transparent), x - 20.dp.toPx(), x + 20.dp.toPx()))
                        }
                    })
                }
            }
        }
    }
}

/** Moving discs, cartridges & cases: each as picked, side by side. */
@Composable
internal fun MediaMotionPreview(disc: Features.MediaMotion, cart: Features.MediaMotion, case: Features.MediaMotion) {
    val a = ShelfTheme.accent
    val s = ShelfTheme.colors.secondary
    PreviewFrame("Disc · cartridge · case", width = 210.dp) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).motionPreview(true, disc)) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Brush.sweepGradient(listOf(Color(0xFFD9DDE6), a, Color(0xFFB8BEC9), s, Color(0xFFD9DDE6))))
                    drawRect(Color.White.copy(alpha = 0.8f), Offset(size.width * 0.48f, size.height * 0.08f), Size(size.width * 0.04f, size.height * 0.3f))
                    drawCircle(Color(0xFF0E1117), radius = size.minDimension * 0.12f)
                }
            }
            Box(Modifier.size(44.dp, 52.dp).motionPreview(false, cart).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 2.dp, bottomEnd = 2.dp)).background(Color(0xFF5B606A))) {
                Box(Modifier.align(Alignment.Center).size(34.dp, 30.dp).clip(RoundedCornerShape(3.dp)).background(Brush.verticalGradient(listOf(a, s))))
            }
            Box(Modifier.size(38.dp, 54.dp).motionPreview(false, case).clip(RoundedCornerShape(2.dp)).background(Brush.verticalGradient(listOf(s, a)))) {
                Box(Modifier.fillMaxWidth().height(8.dp).background(Color.Black.copy(alpha = 0.45f)))
            }
        }
    }
}

/** A carousel view: seven little cards gliding along, laid out as picked. */
@Composable
internal fun CarouselViewPreview(view: CarouselView) {
    val t = loopClock(6000)
    // Glides one card at a time, resting a moment on each.
    val step = (t * 6f)
    val i = kotlin.math.floor(step).toInt()
    val f = ease(((step - i) - 0.5f) / 0.5f)
    val pos = (if (i % 6 < 3) (i % 3) + f else 3f - ((i % 3) + f)).coerceIn(0f, 3f) + 1.5f
    val a = ShelfTheme.accent
    val s = ShelfTheme.colors.secondary
    PreviewFrame(view.label, width = 210.dp) {
        val bigW = 36.dp
        val bigH = 50.dp
        for (n in 0 until 7) {
            val rel = n - pos
            val p = Carousel.place(rel, 2, view)
            if (p.alpha <= 0f) continue
            Box(
                Modifier.align(Alignment.Center).zIndex(p.z)
                    .offset(x = bigW * p.x, y = bigH * p.y)
                    .size(bigW, bigH)
                    .graphicsLayer {
                        // (Update 134: Spine wall's cards turn side-on — kept just short of it, so they show as slim spines.)
                        scaleX = p.scale; scaleY = p.scale; rotationY = p.turnY.coerceIn(-78f, 78f); rotationZ = p.turnZ
                        cameraDistance = 10f * density; alpha = p.alpha
                        flatTextFor3d() // Update 177: drawn flat, then turned (Android 13 text cache)
                    }
                    .clip(RoundedCornerShape(4.dp))
                    .background(Brush.verticalGradient(if (n % 2 == 0) listOf(a, Color(0xFF1A1F2B)) else listOf(s, Color(0xFF221A26))))
            ) { if (p.shade > 0.01f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = p.shade))) }
        }
    }
}

/** Pages and categories changing (update 112): sliding, fading, or just changing. */
@Composable
internal fun PageSlidePreview(kind: Features.PageAnim) {
    val t = loopClock(3200)
    val half = (t * 2f) % 1f
    val page = if (t < 0.5f) 1 else 0
    val q = if (kind != Features.PageAnim.NONE) ease(half / 0.3f) else 1f
    val dir = if (page == 1) 1f else -1f
    val slide = kind == Features.PageAnim.SLIDE
    val a = ShelfTheme.accent
    val s = ShelfTheme.colors.secondary
    @Composable
    fun Page(n: Int, m: Modifier) {
        Row(m.fillMaxSize().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (k in 0 until 3) Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(4.dp)).background(if (n == 0) a.copy(alpha = 0.75f - k * 0.15f) else s.copy(alpha = 0.75f - k * 0.15f)))
        }
    }
    PreviewFrame(when (kind) { Features.PageAnim.SLIDE -> "Pages slide"; Features.PageAnim.FADE -> "Pages fade over"; Features.PageAnim.NONE -> "Pages just change" }) {
        Page(1 - page, Modifier.graphicsLayer { translationX = if (slide) -dir * q * size.width else 0f; alpha = 1f - q })
        Page(page, Modifier.graphicsLayer { translationX = if (slide) dir * (1f - q) * size.width else 0f; alpha = q })
    }
}

/** The next game's info coming in (update 112): rising, fading, or just appearing. */
@Composable
internal fun RiseInPreview(kind: Features.InfoAnim) {
    val t = loopClock(2400)
    val q = if (kind != Features.InfoAnim.NONE) ease(((t % 0.5f) / 0.5f) / 0.35f) else 1f
    val n = if (t < 0.5f) 0 else 1
    val a = ShelfTheme.accent
    val rise = kind == Features.InfoAnim.RISE
    PreviewFrame(when (kind) { Features.InfoAnim.RISE -> "Info rises into place"; Features.InfoAnim.FADE -> "Info fades in"; Features.InfoAnim.NONE -> "Info just appears" }) {
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp).graphicsLayer {
            alpha = if (rise) 0.25f + 0.75f * q else q; translationY = if (rise) (1f - q) * 14f * density else 0f
        }) {
            Box(Modifier.size(if (n == 0) 96.dp else 74.dp, 10.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.9f)))
            Spacer(Modifier.height(5.dp))
            Box(Modifier.size(if (n == 0) 60.dp else 82.dp, 7.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.5f)))
            Spacer(Modifier.height(6.dp))
            Box(Modifier.size(40.dp, 12.dp).clip(RoundedCornerShape(50)).background(a))
        }
    }
}

/** How far the game you're on stands out (update 112): a row of five, the middle one popping out by [pop] (1 = normal). */
@Composable
internal fun StandOutPreview(pop: Float) {
    val t = loopClock(2600)
    val on = (t * 5f).toInt().coerceIn(0, 4)
    val a = ShelfTheme.accent
    val s = ShelfTheme.colors.secondary
    PreviewFrame("The game you're on: ${(pop * 100).toInt()} %", width = 210.dp) {
        Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            for (i in 0 until 5) {
                val k by androidx.compose.animation.core.animateFloatAsState(if (i == on) pop else 0f, tween(140), label = "standOut")
                Box(
                    Modifier.size(30.dp, 42.dp).graphicsLayer { val sc = 1f + 0.08f * k; scaleX = sc; scaleY = sc; translationY = -3f * k * density }
                        .clip(RoundedCornerShape(4.dp)).background(Brush.verticalGradient(if (i % 2 == 0) listOf(a, Color(0xFF1A1F2B)) else listOf(s, Color(0xFF221A26))))
                        .then(if (i == on) Modifier.border(2.dp, Color.White, RoundedCornerShape(4.dp)) else Modifier)
                )
            }
        }
    }
}

/**
 * Update 112: the animations of one layout — each "As everywhere" (the general choice above) or its own: how the game's
 * info comes in, how pages and categories change, how far the game you're on stands out, and whether only it moves.
 */
@Composable
internal fun LayoutAnimChoices(layout: com.shelf.launcher.data.HomeLayout, globalHero: HeroMotion) {
    val F = Features
    MotionPrefs.observe()
    val o = MotionPrefs.overrides(F.layoutAnims.state.value)
    fun stored(what: String) = o["${layout.name}.$what"]
    Text("Animations in ${layout.label}", color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    Text("Only this layout. \"As everywhere\" follows the general choice (Home › Animations; the picture: Background › Motion & transitions).", color = ShelfTheme.textMuted, fontSize = 11.sp)
    Spacer(Modifier.height(6.dp))
    // Update 119: how the game's picture comes in, on its own (the rest of the motion stays as it is).
    Text("The game's picture coming in", color = ShelfTheme.textMuted, fontSize = 12.sp)
    ChipFlow {
        Chip("As everywhere", accent = stored("hero") == null) { MotionPrefs.setOverride(layout, "hero", null) }
        com.shelf.launcher.data.HeroMotion.values().forEach { v -> Chip(v.label, accent = stored("hero") == v.name) { MotionPrefs.setOverride(layout, "hero", v.name) } }
    }
    Spacer(Modifier.height(4.dp))
    HeroMotionPreview(MotionPrefs.hero(globalHero, layout))
    Spacer(Modifier.height(8.dp))
    Text("The game's info", color = ShelfTheme.textMuted, fontSize = 12.sp)
    ChipFlow {
        Chip("As everywhere", accent = stored("info") == "GLOBAL" || (stored("info") == null && MotionPrefs.override(layout, "info") == null)) { MotionPrefs.setOverride(layout, "info", "GLOBAL") }
        com.shelf.launcher.data.Features.InfoAnim.values().forEach { v -> Chip(v.label, accent = MotionPrefs.override(layout, "info") == v.name) { MotionPrefs.setOverride(layout, "info", v.name) } }
    }
    Spacer(Modifier.height(4.dp))
    RiseInPreview(MotionPrefs.info(layout))
    Spacer(Modifier.height(8.dp))
    Text("Pages and categories", color = ShelfTheme.textMuted, fontSize = 12.sp)
    ChipFlow {
        Chip("As everywhere", accent = stored("page") == "GLOBAL" || (stored("page") == null && MotionPrefs.override(layout, "page") == null)) { MotionPrefs.setOverride(layout, "page", "GLOBAL") }
        com.shelf.launcher.data.Features.PageAnim.values().forEach { v -> Chip(v.label, accent = MotionPrefs.override(layout, "page") == v.name) { MotionPrefs.setOverride(layout, "page", v.name) } }
    }
    Spacer(Modifier.height(4.dp))
    PageSlidePreview(MotionPrefs.page(layout))
    Spacer(Modifier.height(8.dp))
    Text("How far the game you're on stands out", color = ShelfTheme.textMuted, fontSize = 12.sp)
    ChipFlow {
        Chip("As everywhere", accent = stored("pop") == null) { MotionPrefs.setOverride(layout, "pop", null) }
        listOf(0, 50, 100, 150, 200, 300).forEach { v -> Chip("$v %", accent = stored("pop") == v.toString()) { MotionPrefs.setOverride(layout, "pop", v.toString()) } }
    }
    Spacer(Modifier.height(4.dp))
    StandOutPreview(MotionPrefs.pop(layout))
    Spacer(Modifier.height(8.dp))
    Text("Spinning / bobbing discs, cartridges and cases", color = ShelfTheme.textMuted, fontSize = 12.sp)
    ChipFlow {
        Chip("As everywhere", accent = stored("motion") == null) { MotionPrefs.setOverride(layout, "motion", null) }
        Chip("Every game", accent = stored("motion") == "0") { MotionPrefs.setOverride(layout, "motion", "0") }
        Chip("Only the game you're on", accent = stored("motion") == "1") { MotionPrefs.setOverride(layout, "motion", "1") }
    }
}

/** Pictures changing: the next one fades in over the last (held), or replaces it at once. */
@Composable
internal fun PicturesPreview(held: Boolean) {
    val t = loopClock(3600)
    val half = (t * 2f) % 1f
    val to = if (t < 0.5f) 1 else 0
    val q = if (held) ease(half / 0.25f) else 1f
    PreviewFrame(if (held) "Next picture fades in over the last" else "Next picture replaces it at once") {
        Picture(1 - to, Modifier)
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = q }) { Picture(to, Modifier) }
    }
}
