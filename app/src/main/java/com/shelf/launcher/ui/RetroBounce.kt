package com.shelf.launcher.ui

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.ConsoleLogos
import com.shelf.launcher.data.Features
import com.shelf.launcher.ui.theme.ShelfColors
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** What the Retro bounce theme shows: the console you're on and the consoles in your library (set by the home screen). */
internal object RetroBounceSource {
    var platform by mutableStateOf<String?>(null)
    var platforms by mutableStateOf<List<String>>(emptyList())
}

/**
 * Retro bounce: 32-bit D-pads, A / B buttons and console icons bouncing round the screen over the
 * pixel stage (retro_bounce.agsl). The console icons are RetroAchievements' own system icons (the ones
 * next to the category names; loaded once a RetroAchievements account is linked) — the console you're
 * on, or a mix from your library (Appearance › Theme). Without them, pixel cartridges and discs stand in.
 *
 * Cheap on purpose: a dozen little pictures drawn into one canvas at a stepped ~20 fps (an old
 * console's motion, and a fraction of the work), snapped to the pixel grid; nothing runs while the
 * home screen is covered, idle for long, off screen or with Lite effects.
 */
@Composable
internal fun RetroBounceLayer(colors: ShelfColors, modifier: Modifier) {
    val context = LocalContext.current
    val liteStill = LocalLiteEffects.current && !LocalAnimateOnLite.current
    val active = rememberIsActive() && rememberAnimationsEnabled() && !liteStill && !LocalSceneCovered.current
    val tuning = LocalSceneTuning.current
    val sprites = remember(colors.accent, colors.secondary) { RetroSprites(colors.accent.toArgb(), colors.secondary.toArgb()) }

    val mode = Features.retroIcons.state.value
    val current = RetroBounceSource.platform
    val library = RetroBounceSource.platforms
    val logos = ConsoleLogos.icons.value
    val urls = remember(mode, current, library, logos) {
        val own = current?.let { ConsoleLogos.raIcon(it) }
        if (mode == Features.RetroIcons.CURRENT && own != null) listOf(own)
        else library.mapNotNull { ConsoleLogos.raIcon(it) }.distinct().take(14).ifEmpty { listOfNotNull(own) }
    }
    val icons by produceState(emptyList<ImageBitmap>(), urls) {
        value = urls.mapNotNull { url ->
            runCatching {
                val req = coil.request.ImageRequest.Builder(context).data(url).allowHardware(false).build()
                val d = (coil.Coil.imageLoader(context).execute(req) as? coil.request.SuccessResult)?.drawable
                val bmp = (d as? BitmapDrawable)?.bitmap ?: d?.let {
                    Bitmap.createBitmap(it.intrinsicWidth.coerceAtLeast(1), it.intrinsicHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888).also { b ->
                        val c = android.graphics.Canvas(b); it.setBounds(0, 0, b.width, b.height); it.draw(c)
                    }
                }
                bmp?.asImageBitmap()
            }.getOrNull()
        }
    }

    val world = remember { RetroWorld() }
    val frame = remember { mutableIntStateOf(0) }
    val count = (14f * tuning.particles).roundToInt().coerceIn(6, 22)
    val frameMs = maxOf(50, LocalSceneQuality.current.frameMs, idleFrameMs())
    LaunchedEffect(active, frameMs, tuning.speed) {
        if (!active) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            // Sleep until the next step is due instead of waking on every display frame.
            kotlinx.coroutines.delay((frameMs - 6L).coerceAtLeast(1L))
            withFrameNanos { now ->
                val dt = (now - last) / 1_000_000_000f
                last = now
                world.step(dt.coerceAtMost(0.1f) * tuning.speed)
                frame.intValue++
            }
        }
    }

    Canvas(modifier) {
        @Suppress("UNUSED_VARIABLE") val tick = frame.intValue // redraw on every step
        val u = maxOf(2f, floor(size.height / 180f))
        world.ensure(size.width, size.height, u, count, icons.size)
        for (b in world.items) {
            val img: ImageBitmap = when (b.kind) {
                0 -> sprites.dpad
                1 -> sprites.a
                2 -> sprites.b
                4 -> sprites.extras[b.icon % sprites.extras.size]
                else -> if (icons.isNotEmpty()) icons[b.icon % icons.size] else when (b.icon % 3) {
                    0 -> sprites.cart
                    1 -> sprites.disc
                    else -> sprites.extras[(b.icon / 3) % sprites.extras.size]
                }
            }
            // Sprites are a fixed number of fat pixels; icons are shown 12 pixels across.
            val baseW = if (b.kind == 3 && icons.isNotEmpty()) 12f * u else img.width * u
            val baseH = if (b.kind == 3 && icons.isNotEmpty()) baseW * img.height / img.width.coerceAtLeast(1) else img.height * u
            val sq = b.squash
            val w = snap(baseW * (1f + 0.22f * sq), u)
            val h = snap(baseH * (1f - 0.22f * sq), u)
            val hop = abs(sin(b.phase)) * b.hopAmp * u
            val x = snap(b.x - w / 2f, u)
            val floorY = b.y
            val y = snap(floorY - h - hop, u)
            // A little shadow on the "ground" under it, smaller the higher it hops.
            val sw = snap(w * (0.7f - 0.3f * (hop / (b.hopAmp * u + 1f))), u)
            drawRect(Color.Black.copy(alpha = 0.28f), Offset(snap(b.x - sw / 2f, u), snap(floorY, u)), Size(sw, u))
            drawImage(
                img,
                srcOffset = IntOffset.Zero, srcSize = IntSize(img.width, img.height),
                dstOffset = IntOffset(x.toInt(), y.toInt()), dstSize = IntSize(w.toInt(), h.toInt()),
                filterQuality = FilterQuality.None
            )
        }
    }
}

private fun snap(v: Float, u: Float) = floor(v / u) * u

/** The bouncing things: plain fields, stepped by the clock and read by the draw. */
private class RetroWorld {
    class Bouncer(
        var kind: Int, var icon: Int, var x: Float, var y: Float, var vx: Float, var vy: Float,
        val hopAmp: Float, val hopSpeed: Float, var phase: Float, var squash: Float = 0f
    )
    val items = ArrayList<Bouncer>()
    private var w = 0f
    private var h = 0f
    private var u = 1f
    private var key = ""
    private val rnd = java.util.Random(7)

    fun ensure(width: Float, height: Float, unit: Float, count: Int, icons: Int) {
        val k = "$width/$height/$count/${icons > 0}"
        if (k == key) return
        key = k; w = width; h = height; u = unit
        items.clear()
        for (i in 0 until count) {
            // A mix: D-pads, A, B and consoles (more consoles when there are real icons to show).
            val kind = when (i % 7) { 0 -> 0; 1 -> 1; 2 -> 2; 3, 4 -> 3; else -> 4 }
            val speed = (40f + rnd.nextFloat() * 60f) * unit
            val dir = rnd.nextFloat() * 6.283f
            items += Bouncer(
                kind = kind, icon = i,
                x = rnd.nextFloat() * width, y = height * (0.2f + rnd.nextFloat() * 0.75f),
                vx = speed * kotlin.math.cos(dir), vy = speed * 0.45f * sin(dir),
                hopAmp = 6f + rnd.nextFloat() * 12f, hopSpeed = 2.5f + rnd.nextFloat() * 2.5f,
                phase = rnd.nextFloat() * 3.14f
            )
        }
    }

    fun step(dt: Float) {
        if (w <= 0f) return
        val margin = 8f * u
        for (b in items) {
            b.x += b.vx * dt
            b.y += b.vy * dt
            val before = floor(b.phase / Math.PI.toFloat())
            b.phase += b.hopSpeed * dt
            // Landed from a hop: a quick squash.
            if (floor(b.phase / Math.PI.toFloat()) != before) b.squash = 1f
            if (b.x < margin) { b.x = margin; b.vx = abs(b.vx); b.squash = 1f }
            if (b.x > w - margin) { b.x = w - margin; b.vx = -abs(b.vx); b.squash = 1f }
            // y is the ground line under it: kept below the top (room for the hop) and above the bottom.
            val top = h * 0.12f + 20f * u
            if (b.y < top) { b.y = top; b.vy = abs(b.vy) }
            if (b.y > h - u * 2f) { b.y = h - u * 2f; b.vy = -abs(b.vy) }
            b.squash = (b.squash - dt * 6f).coerceAtLeast(0f)
        }
    }
}

/** The theme's pixel sprites, built from little character maps in the theme's colours. */
private class RetroSprites(accent: Int, secondary: Int) {
    private val k = 0xFF14121C.toInt()

    val dpad = sprite(
        listOf(
            "...KKKKK...",
            "...KOHOK...",
            "...KOOOK...",
            "KKKKOOOKKKK",
            "KHOOOOOOOSK",
            "KOOOODOOOOK",
            "KOOOOOOOOSK",
            "KKKKOOOKKKK",
            "...KOOOK...",
            "...KOSOK...",
            "...KKKKK..."
        ),
        mapOf('K' to k, 'O' to 0xFFCFCBDD.toInt(), 'H' to 0xFFFFFFFF.toInt(), 'S' to 0xFF9E99B3.toInt(), 'D' to 0xFF7E7992.toInt())
    )
    val a = button(accent, listOf(".###.", "#...#", "#####", "#...#", "#...#"))
    val b = button(secondary, listOf("####.", "#...#", "####.", "#...#", "####."))
    val cart = sprite(
        listOf(
            ".KKKKKKKKK.",
            ".KGGGGGGGK.",
            ".KGLLLLLGK.",
            ".KGLAAALGK.",
            ".KGLAAALGK.",
            ".KGLLLLLGK.",
            ".KGGGGGGGK.",
            ".KGGGGGGGK.",
            ".KGKGKGKGK.",
            ".KKKKKKKKK."
        ),
        mapOf('K' to k, 'G' to 0xFF6E6A80.toInt(), 'L' to 0xFFEDEAF5.toInt(), 'A' to accent)
    )
    // More variety: face buttons, a stick, a handheld, a memory card, a coin, a star, a heart, a pad.
    val extras: List<ImageBitmap> = listOf(
        button(0xFF4FA3FF.toInt(), listOf("#...#", ".#.#.", "..#..", ".#.#.", "#...#")),
        button(0xFFFFC94A.toInt(), listOf("#...#", ".#.#.", "..#..", "..#..", "..#..")),
        sprite(
            listOf("..KKKKK..", ".KHOOOOK.", "KHOOOOOOK", "KOOODOOOK", "KOODDDOOK", "KOOODOOOK", "KOOOOOOSK", ".KOOOOSK.", "..KKKKK.."),
            mapOf('K' to k, 'O' to 0xFF8D889E.toInt(), 'H' to 0xFFE6E3F0.toInt(), 'S' to 0xFF5D586E.toInt(), 'D' to 0xFF3A3648.toInt())
        ),
        sprite(
            listOf("KKKKKKKKK", "KGGGGGGGK", "KGLLLLLGK", "KGLAAALGK", "KGLLLLLGK", "KGGGGGGGK", "KGDGGGOGK", "KDDDGGGGK", "KGDGGOGOK", "KGGGGGGGK", "KKKKKKKKK"),
            mapOf('K' to k, 'G' to 0xFFB9B4C8.toInt(), 'L' to 0xFF2A2F2A.toInt(), 'A' to 0xFF8BC34A.toInt(), 'D' to 0xFF3A3648.toInt(), 'O' to accent)
        ),
        sprite(
            listOf("KKKKKKKK.", "KGGGGGGGK", "KGLLLLLGK", "KGLAAALGK", "KGLLLLLGK", "KGGGGGGGK", "KGKGKGKGK", "KKKKKKKKK"),
            mapOf('K' to k, 'G' to 0xFF5B5870.toInt(), 'L' to 0xFFEDEAF5.toInt(), 'A' to secondary)
        ),
        sprite(
            listOf("..KKKK..", ".KYYYYK.", "KYHYYYSK", "KYHYKYSK", "KYHYKYSK", "KYYYYYSK", ".KYSSSK.", "..KKKK.."),
            mapOf('K' to k, 'Y' to 0xFFFFD54A.toInt(), 'H' to 0xFFFFF4C2.toInt(), 'S' to 0xFFC9971E.toInt())
        ),
        sprite(
            listOf("....K....", "...KYK...", "KKKKYKKKK", "KYYYYYYYK", ".KYYHYYK.", "..KYYYK..", ".KYYKYYK.", ".KYK.KYK.", ".KK...KK."),
            mapOf('K' to k, 'Y' to 0xFFFFE066.toInt(), 'H' to 0xFFFFFFFF.toInt())
        ),
        sprite(
            listOf(".KK.KK.", "KRRKRRK", "KRHRRRK", "KRRRRRK", ".KRRRK.", "..KRK..", "...K..."),
            mapOf('K' to k, 'R' to 0xFFFF4D6D.toInt(), 'H' to 0xFFFFC2CC.toInt())
        ),
        sprite(
            listOf("..KKKKKKKKK..", ".KPPPPPPPPPK.", "KPPDPPPPPAPPK", "KPDDDPPPBPAPK", "KPPDPPPPPBPPK", "KPPPPKKKPPPPK", ".KPPK...KPPK.", "..KK.....KK.."),
            mapOf('K' to k, 'P' to 0xFFCFCBDD.toInt(), 'D' to 0xFF3A3648.toInt(), 'A' to accent, 'B' to secondary)
        )
    )

    val disc: ImageBitmap = run {
        val n = 11
        val px = IntArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val dx = x - 5f; val dy = y - 5f
            val d = kotlin.math.sqrt(dx * dx + dy * dy)
            px[y * n + x] = when {
                d > 5.4f -> 0
                d > 4.5f -> k
                d < 1.0f -> 0
                d < 1.8f -> k
                // A rainbow-ish sheen across the silver, in the theme's colours.
                (x - y) in -1..0 -> secondary
                (x - y) in 2..3 -> accent
                else -> 0xFFD9D6E4.toInt()
            }
        }
        Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    private fun sprite(rows: List<String>, pal: Map<Char, Int>): ImageBitmap {
        val w = rows.maxOf { it.length }
        val h = rows.size
        val px = IntArray(w * h)
        rows.forEachIndexed { y, r -> r.forEachIndexed { x, c -> px[y * w + x] = pal[c] ?: 0 } }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    /** A round 11 × 11 face button in [color] with a 5 × 5 letter. */
    private fun button(color: Int, letter: List<String>): ImageBitmap {
        val n = 11
        val px = IntArray(n * n)
        val shade = android.graphics.Color.rgb(
            (android.graphics.Color.red(color) * 0.7f).toInt(), (android.graphics.Color.green(color) * 0.7f).toInt(), (android.graphics.Color.blue(color) * 0.7f).toInt()
        )
        for (y in 0 until n) for (x in 0 until n) {
            val dx = x - 5f; val dy = y - 5f
            val d = kotlin.math.sqrt(dx * dx + dy * dy)
            px[y * n + x] = when {
                d > 5.4f -> 0
                d > 4.5f -> k
                dx + dy > 4f -> shade
                dx + dy < -5f -> 0xFFFFFFFF.toInt()
                else -> color
            }
        }
        letter.forEachIndexed { y, r -> r.forEachIndexed { x, c -> if (c == '#') px[(y + 3) * n + (x + 3)] = 0xFFFFFFFF.toInt() } }
        return Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}

/** Appearance › Theme, while Retro bounce is picked. */
@Composable
internal fun RetroBounceSettings() {
    val F = Features
    Column {
        SectionLabel("Retro bounce")
        MutedNote("Which console icons bounce round with the D-pads and A / B buttons. The icons are RetroAchievements' system icons (shown once your RetroAchievements account is linked); without them, pixel cartridges and discs stand in.")
        Spacer(Modifier.height(8.dp))
        ChipFlow {
            Features.RetroIcons.values().forEach { m ->
                Chip(m.label, accent = F.retroIcons.state.value == m) { F.retroIcons.value = m }
            }
        }
        MutedNote("\"This console\" follows the category you're on (a mix on All games). How many there are follows the scene's particles setting.")
    }
}
