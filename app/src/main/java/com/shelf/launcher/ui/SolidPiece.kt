package com.shelf.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.shelf.launcher.data.Features
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Update 203: a cartridge / card is a solid piece wherever it's seen turned — a carousel's tilt, a flip, its own slow
 * turn on its stand. A view that turns its games used to turn the flat picture (graphicsLayer rotationY), so a
 * cartridge seen from the side was a sheet of paper, nothing at all at 90°. Now such a view hands its turn down
 * (ProvidePieceTurn) instead of turning the game itself, and the cartridge draws itself turned with its thickness:
 * its face recorded once and drawn again, darker, slice after slice through its depth — its sides follow its own
 * outline (a notch, the N64's round top) in its own colours — then the lit face in front; from behind, its back
 * (the shell's researched back, else its outline in the shell's colour). The game window's inspect view does the
 * same (InspectSlab). Flat parts that ride along with it (a console badge, a disc) turn flat (followTurn).
 */

/** A view's turn for the game in it: degrees round the upright ([yawOf], read while drawing), and its camera distance. */
internal class PieceTurn(val yawOf: () -> Float, val camera: Float = 12f)

/** Set by a view that leaves the turning to the piece (solidPiece / followTurn) inside it; null: nothing turns it. */
internal val LocalPieceTurn = compositionLocalOf<PieceTurn?> { null }

/**
 * [content] with [turn] handed down to the piece in it (null: nothing turns it). Always the same provider, so a turn
 * coming and going (a focus spin) never rebuilds the piece (its pictures would load again).
 */
@Composable
internal fun ProvidePieceTurn(turn: PieceTurn?, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPieceTurn provides turn, content = content)
}

/** A flat part inside a view that handed its turn down (a disc, a badge): turned flat, as the view used to turn it. */
@Composable
internal fun Modifier.followTurn(): Modifier {
    val t = LocalPieceTurn.current ?: return this
    return this.graphicsLayer {
        rotationY = t.yawOf()
        cameraDistance = t.camera * density
        flatTextFor3d()
    }
}

/**
 * A cartridge's / card's solid build: [aspect] its width / height (fitted in the middle of the node it's drawn in;
 * null: the whole node), [depth] its thickness against its longest side, [tone] its plastic, [back] its back (made
 * the first time it's seen from behind).
 */
internal class SolidSpec(
    val aspect: Float?, val depth: Float, val tone: Color, back: (() -> ImageBitmap?)? = null,
    /**
     * Update 207: the back stretched over the whole piece and cut to the face's own outline — for a cartridge photo,
     * whose outline and size are the photo's, not the drawn shell's (from behind it was another, smaller cartridge).
     */
    val fitBackToFace: Boolean = false,
    /**
     * Update 209: a see-through plastic (a translucent memory card): its sides as see-through as this (0…1, how much of
     * what's behind they hide), nothing of them behind its face, and its back as see-through. Null: solid plastic. The
     * sides were solid slices standing behind the face, so a translucent card turned into an opaque grey block.
     */
    val clear: Float? = null
) {
    val back: ImageBitmap? by lazy { runCatching { back?.invoke() }.getOrNull() }
}

/** [spec] for a game on a shell from the cartridge library ([pickPlatform] "shell:…"): its own size, colour and back. */
@Composable
internal fun rememberShellSolid(pickPlatform: String): SolidSpec? {
    val backStyle = Features.cartBackStyle.state.value
    // (Update 228, #36: a pack shape picked in the shell picker turned with the pack picture's own plastic on its sides
    // and back — it was grey, whatever its colour.)
    val packTone = rememberCustomPieceTone(pickPlatform)
    return remember(pickPlatform, backStyle, packTone) {
        val (choice, console) = CartShells.split(pickPlatform)
        val sh = choice?.shell
        when {
            choice == null -> null
            sh == null -> SolidSpec(choice.aspect, 0.12f, packTone ?: CUSTOM_SHELL)
            else -> {
                val colour = CartShells.colourOf(sh, choice.colourKey)
                // (Update 210: a see-through plastic stays see-through turned — SolidSpec.clear.)
                SolidSpec(sh.aspect, sh.depth.coerceIn(0.02f, 0.4f), Color(colour.argb).copy(alpha = 1f), clear = if (colour.opacity < 0.85f) 0.5f else null, back = {
                    val neutral = when (backStyle) {
                        Features.CartBackStyle.AUTHENTIC -> false
                        Features.CartBackStyle.NEUTRAL -> true
                        Features.CartBackStyle.AUTO -> console == null || !CartShells.belongsTo(sh.id, console)
                    }
                    CartShells.back(sh, colour, 480, neutral).asImageBitmap()
                })
            }
        }
    }
}

/**
 * Update 204: the shell a game's own cartridge photo stands for — its console's real shell, by the game's region
 * ([gameKey]); null for a console without one (discs, apps, PC).
 */
internal fun ownShellOf(platform: String?, gameKey: String?): CartShell? =
    // (Update 210: its real edition's shell when it had its own — Boktai's solar cartridge, Kirby Tilt 'n' Tumble…)
    platform?.let { p -> MediaEditions.auto(p, gameKey)?.shell ?: CartShells.authenticIdFor(p, CaseCatalog.regionOf(gameKey ?: ""), gameKey) }
        ?.let { CartShells.of(it) }

/** Update 210: [sh]'s plastic for this game — its real edition's material when it has one, else the shell's own. */
internal fun ownShellColour(sh: CartShell, platform: String?, gameKey: String?): ShellColour =
    platform?.let { MediaEditions.auto(it, gameKey)?.material }?.let { ShellMaterials.of(it)?.colour() } ?: CartShells.colourOf(sh, null)

/**
 * Update 204: the researched back for [sh] in its own colour (Settings › Game look › Cartridge backs: Authentic or Auto
 * show the maker's markings — it is the console's own shell — Neutral leaves them out), [px] on its longest side.
 */
internal fun ownShellBack(sh: CartShell, backStyle: Features.CartBackStyle, px: Int): ImageBitmap? = runCatching {
    CartShells.back(sh, CartShells.colourOf(sh, null), px, backStyle == Features.CartBackStyle.NEUTRAL).asImageBitmap()
}.getOrNull()

/**
 * Update 204: [spec] for the game's own cartridge photo ([photo]): its console's real shell's thickness, colour and
 * researched back (it showed a plain silhouette from behind, so the new backs never appeared on the many games with a
 * photo). [aspect]: the photo's own shape once it has loaded.
 */
@Composable
internal fun rememberPhotoSolid(platform: String?, gameKey: String?, aspect: Float?, tone: Color? = null): SolidSpec {
    val backStyle = Features.cartBackStyle.state.value
    return remember(platform, gameKey, backStyle, aspect, tone) {
        val sh = ownShellOf(platform, gameKey)
        // (Update 205: [tone] — the photo's own plastic, read off its edge — for its sides; a silver Pokémon cartridge
        // isn't the black of the usual Game Boy Color shell.)
        // (Update 207: a UMD photo at the UMD's own thickness.)
        if (sh == null) SolidSpec(aspect, if (platform != null && MediaShell.of(platform) == MediaShell.UMD) slabDepth(MediaShell.UMD) else 0.12f, tone ?: PLAIN_SHELL)
        else SolidSpec(aspect ?: sh.aspect, sh.depth.coerceIn(0.02f, 0.4f), tone ?: Color(ownShellColour(sh, platform, gameKey).argb).copy(alpha = 1f), {
            // (Update 207: in the photo's own plastic colour — a black or gold cartridge photo had the grey shell's back —
            // and cut to the photo's outline when drawn: fitBackToFace.)
            val own = ownShellColour(sh, platform, gameKey)
            // (Update 228, #36: keyed by the colour — every photo's back was cached under one "photo" key, so a
            // cartridge's back could come out in another photo's plastic.)
            val colour = tone?.let { t -> t.copy(alpha = 1f).toArgb().let { c -> ShellColour("photo:$c", own.name, c, own.opacity) } } ?: own
            runCatching { CartShells.back(sh, colour, 480, backStyle == Features.CartBackStyle.NEUTRAL).asImageBitmap() }.getOrNull()
        }, fitBackToFace = true)
    }
}

/**
 * [spec] for a drawn / template / pack cartridge of [shell] on [platform], at [aspect]. [tone]: a [custom] piece's own
 * plastic (rememberCustomPieceTone), else the plain custom grey.
 */
internal fun cartSolid(shell: MediaShell?, platform: String?, aspect: Float?, custom: Boolean = false, tone: Color? = null): SolidSpec =
    SolidSpec(
        aspect, slabDepth(if (custom) null else shell),
        if (custom) tone ?: CUSTOM_SHELL else if (platform == ANDROID_CART) ANDROID_SHELL else shellTone(shell)
    )

/**
 * Update 228 (#36): a custom piece's plastic — your pack's cartridge ("~cc:<console>") or a shell picked from the
 * cartridge library ("~sh:<choice>|<console>", the pack's shapes too) — from what was picked: a library shell's colour
 * as set, else the pack picture's own plastic, read once off a small copy of it. Every custom cartridge turned (and in
 * the game window's all-sides view) had the same grey sides and back, whatever colour it was.
 */
internal object CustomPieceTones {
    /** A pack picture's path → its plastic (0: none could be read off it). */
    private val packs = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /** The pack picture [pickPlatform] is drawn with (as CustomCart / PackCart pick it), or null. */
    fun packFile(pickPlatform: String): java.io.File? = when {
        pickPlatform.startsWith(SHELL_PREFIX) -> {
            val (choice, console) = CartShells.split(pickPlatform)
            val look = choice?.pack
            if (choice == null || look == null) null
            else CustomCarts.idFor(console ?: "")?.let { CustomCarts.asset(look, choice.clear, it) }
        }
        pickPlatform.startsWith(CUSTOM_CART) -> {
            val console = pickPlatform.removePrefix(CUSTOM_CART)
            val disc = discShellFor(console)
            if (disc != null) com.shelf.launcher.data.Packs.discFile(disc.file)
            else {
                val finish = Features.modelFinish.state.value
                val clear = Features.cartClear.state.value && (finish == ModelFinish.CLEAR || finish == ModelFinish.OFF)
                CustomCarts.idFor(console)?.let { CustomCarts.asset(Features.cartLook.state.value, clear, it) }
            }
        }
        else -> null
    }

    /** [pickPlatform]'s plastic when it's known without reading a picture (or its pack picture was read); else null. */
    fun of(pickPlatform: String): Color? {
        if (pickPlatform.startsWith(SHELL_PREFIX)) {
            val choice = CartShells.split(pickPlatform).first
            val sh = choice?.shell
            if (choice != null && sh != null) return Color(CartShells.colourOf(sh, choice.colourKey).argb).copy(alpha = 1f)
        }
        val f = packFile(pickPlatform) ?: return null
        return packs[f.path]?.takeIf { it != 0 }?.let { Color(it) }
    }

    /** [of], reading the pack picture (at 1/8 size, once per picture) when it hasn't been read. Off the main thread. */
    fun load(pickPlatform: String): Color? {
        of(pickPlatform)?.let { return it }
        val f = packFile(pickPlatform) ?: return null
        synchronized(this) {
            packs[f.path]?.let { c -> return if (c != 0) Color(c) else null }
            val b = runCatching {
                android.graphics.BitmapFactory.decodeFile(f.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 })
            }.getOrNull() ?: return null
            val t = runCatching { pictureTone(b) }.getOrNull()
            b.recycle()
            if (packs.size < 512) packs[f.path] = t?.toArgb() ?: 0
            return t
        }
    }

    /**
     * The plastic in a pack picture [b]: the first solid pixel in from its left edge (a little further in, past the
     * outline) on three rows round the middle, averaged; a clear shell's fainter pixels when none is solid.
     */
    private fun pictureTone(b: android.graphics.Bitmap): Color? {
        var r = 0; var g = 0; var bl = 0; var n = 0
        for (minAlpha in intArrayOf(200, 64)) {
            for (row in floatArrayOf(0.4f, 0.5f, 0.6f)) {
                val y = (b.height * row).toInt().coerceIn(0, b.height - 1)
                for (x in 0 until b.width / 3) {
                    if ((b.getPixel(x, y) ushr 24) <= minAlpha) continue
                    val px = b.getPixel((x + maxOf(1, b.width / 40)).coerceAtMost(b.width - 1), y)
                    if ((px ushr 24) > minAlpha) { r += (px shr 16) and 0xFF; g += (px shr 8) and 0xFF; bl += px and 0xFF; n++ }
                    break
                }
            }
            if (n > 0) break
        }
        return if (n == 0) null else Color(r / n, g / n, bl / n)
    }
}

/**
 * Update 228 (#36): [CustomPieceTones] for [pickPlatform] in composition — a pack picture read off the main thread the
 * first time it's needed; null until it's known (or for a piece that isn't a custom one).
 */
@Composable
internal fun rememberCustomPieceTone(pickPlatform: String): Color? {
    val path = CustomPieceTones.packFile(pickPlatform)?.path
    val tone = remember(pickPlatform, path) { mutableStateOf(CustomPieceTones.of(pickPlatform)) }
    LaunchedEffect(pickPlatform, path) {
        if (tone.value != null || path == null) return@LaunchedEffect
        withContext(Dispatchers.IO) { CustomPieceTones.load(pickPlatform) }?.let { tone.value = it }
    }
    return tone.value
}

/** The cartridge's own slow turn on its stand (Settings › Game look › Moving cartridges), or null when it stands still. */
@Composable
internal fun rememberCartSpin(): (() -> Float)? {
    val motion = Features.cartMotion.state.value
    val on = !LocalLiteEffects.current && rememberAnimationsEnabled() && rememberIsActive() && !HomeIdle.calm
    if (!on || !motion.spins) return null
    MotionPrefs.observe()
    if (MotionPrefs.focusedOnlyMotion() && !LocalMediaFocused.current) return null
    if (LocalStillPart.current) return null
    val now = MediaClock.rememberFrameTime()
    return remember(now) {
        {
            val ms = now.longValue / 1_000_000L
            // (As it always turned: about ±24°, slowly left and right, all of them in step.)
            24f * sin((ms % CART_SPIN_MS) / CART_SPIN_MS.toFloat() * 2f * Math.PI.toFloat())
        }
    }
}

internal const val CART_SPIN_MS = 5200L

/**
 * Draws this node's content as the solid piece [spec] turned by the view's turn (LocalPieceTurn) plus its own spin.
 * Nothing changes (no layers) when nothing turns it.
 */
@Composable
internal fun Modifier.solidPiece(
    spec: SolidSpec?,
    /** Update 207: false for a piece that isn't a game's cartridge (the Save Vault's 3D card): no cartridge spin added. */
    withCartSpin: Boolean = true
): Modifier {
    if (spec == null) return this
    val turn = LocalPieceTurn.current
    val spin = if (withCartSpin) rememberCartSpin() else null
    if (turn == null && spin == null) return this
    val dens = LocalDensity.current.density
    val cam = (turn?.camera ?: 14f) * dens
    return this.drawWithCache {
        val face = obtainGraphicsLayer()
        val edge = obtainGraphicsLayer()
        // (Update 205: the sides are the piece's outline in its plastic's colour, made in a layer of their own.)
        edge.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
        val lit = obtainGraphicsLayer()
        val back = obtainGraphicsLayer()
        // All of it in one buffer, so a faded piece (a carousel fades its games op by op) fades as one, its slices never
        // showing through its face.
        val whole = obtainGraphicsLayer()
        whole.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
        // The back in a layer of its own, so its tone and shade only touch the back (see InspectSlab, update 201).
        back.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
        // The face's outline made solid, for a back without a drawn one (a clear shell's face is see-through).
        val solid = obtainGraphicsLayer()
        // Update 209: the face's outline that clears a see-through piece's sides from behind its face.
        val hole = obtainGraphicsLayer()
        hole.blendMode = BlendMode.DstOut
        solid.colorFilter = ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 4f, -24f
        )))
        val sheen = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.Transparent, Color.Black.copy(alpha = 0.18f)))
        // The piece's own size in the node (its longest side sets its thickness).
        val a = spec.aspect
        val pw = if (a == null) size.width else minOf(size.width, size.height * a)
        val ph = if (a == null) size.height else pw / a
        val depthPx = spec.depth * maxOf(pw, ph)
        onDrawWithContent {
            val yaw = (turn?.yawOf?.invoke() ?: 0f) + (spin?.invoke() ?: 0f)
            if (abs(yaw) < 0.25f) { drawContent(); return@onDrawWithContent }
            face.record { this@onDrawWithContent.drawContent() }
            val t = Math.toRadians(yaw.toDouble())
            val c = cos(t).toFloat()
            val sn = sin(t).toFloat()
            for (l in arrayOf(edge, lit, back)) { l.rotationY = yaw; l.cameraDistance = cam }
            // (Update 209: a see-through piece's back as see-through as its sides.)
            back.alpha = spec.clear?.let { 0.5f + 0.5f * it.coerceIn(0f, 1f) } ?: 1f
            // (Each part recorded at the node's own size, round its own middle; then placed in the buffer below.)
            // The sides: the face's outline, darker, lit a little as they face you. Update 205: filled with the shell's
            // plastic — they were copies of the whole face, so a cartridge seen from the side showed its label over and
            // over in stripes (the "glitches" round the turned cartridges in the Box flow stacks).
            solid.record { drawLayer(face) }
            val sideTone = spec.tone.copy(alpha = 1f)
            edge.colorFilter = SolidLight.of(0.42f + 0.22f * abs(sn))
            edge.record {
                drawLayer(solid)
                drawRect(sideTone, blendMode = BlendMode.SrcIn)
                drawRect(sheen, blendMode = BlendMode.SrcAtop)
            }
            if (c >= 0f) {
                lit.colorFilter = SolidLight.of(1f - 0.4f * abs(sn))
                lit.record { drawLayer(face) }
            } else {
                val sb = spec.back
                back.record {
                    if (sb != null && spec.fitBackToFace) {
                        // (Update 207: over the whole piece, cut to the face's outline — the photo's own shape.)
                        drawLayer(solid)
                        drawImage(
                            sb, dstOffset = IntOffset(((size.width - pw) / 2f).roundToInt(), ((size.height - ph) / 2f).roundToInt()),
                            dstSize = IntSize(pw.roundToInt(), ph.roundToInt()), blendMode = BlendMode.SrcIn
                        )
                    } else if (sb != null) {
                        // The shell's drawn back (made mirrored: seen from behind it reads the right way round).
                        val ba = sb.width.toFloat() / sb.height.coerceAtLeast(1)
                        val bw = minOf(size.width, size.height * ba); val bh = bw / ba
                        drawImage(sb, dstOffset = IntOffset(((size.width - bw) / 2f).roundToInt(), ((size.height - bh) / 2f).roundToInt()),
                            dstSize = IntSize(bw.roundToInt(), bh.roundToInt()))
                    } else {
                        drawLayer(solid)
                        drawRect(spec.tone, blendMode = BlendMode.SrcIn)
                    }
                    drawRect(sheen, blendMode = BlendMode.SrcAtop)
                    drawRect(Color.Black, alpha = 0.4f * abs(sn), blendMode = BlendMode.SrcAtop)
                }
            }
            // (Room round it in the buffer: the near side grows past the node as it turns, and the slices stand out.)
            val m = (0.35f * maxOf(size.width, size.height) + depthPx).roundToInt()
            whole.record(size = IntSize(size.width.roundToInt() + 2 * m, size.height.roundToInt() + 2 * m)) {
                translate(m.toFloat(), m.toFloat()) {
                    // Far to near: the slices, then the face (or the back, seen from behind).
                    // (Update 205: close enough together that no gap shows between them on a thick piece side-on.)
                    // (Update 207: a slice every 2 px, at most 32 — 64 slices a piece, every piece in a row, each frame,
                    // was part of what made scrolling a big library heavy; 2 px apart still shows no gap.)
                    val n = ceil(depthPx * abs(sn) / 2f).toInt().coerceIn(0, 32)
                    val clear = spec.clear
                    // (Update 209: a see-through piece's slices each faint enough that all of them together are [clear].)
                    edge.alpha = if (clear == null || n <= 0) 1f else (1.0 - Math.pow(1.0 - clear.coerceIn(0.05f, 0.95f), 1.0 / (n + 1))).toFloat()
                    if (n > 0) for (k in 0..n) {
                        val f = k.toFloat() / n
                        val z = if (c >= 0f) -depthPx / 2f + depthPx * f else depthPx / 2f - depthPx * f
                        translate(z * sn, 0f) { drawLayer(edge) }
                    }
                    val faceX = if (c >= 0f) depthPx / 2f * sn else -depthPx / 2f * sn
                    // (Update 209: none of a see-through piece's sides behind its face — they showed through it.)
                    if (clear != null) {
                        hole.rotationY = yaw; hole.cameraDistance = cam
                        hole.record { drawLayer(solid) }
                        translate(faceX, 0f) { drawLayer(hole) }
                    }
                    if (c >= 0f) translate(faceX, 0f) { drawLayer(lit) }
                    else translate(faceX, 0f) { drawLayer(back) }
                }
            }
            translate(-m.toFloat(), -m.toFloat()) { drawLayer(whole) }
        }
    }
}

/** A brightness filter in 20 steps (made once each). */
private object SolidLight {
    private val cache = arrayOfNulls<ColorFilter>(21)
    fun of(l: Float): ColorFilter? {
        val i = (l.coerceIn(0f, 1f) * 20f).roundToInt()
        if (i >= 20) return null
        cache[i]?.let { return it }
        val k = i / 20f
        return ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToScale(k, k, k, 1f) }).also { cache[i] = it }
    }
}

/**
 * Update 205: the plastic's colour in a cartridge photo [b] — the first solid pixel in from its left edge half-way down,
 * a little further in (past the outline). Null for a picture without a solid edge there. Reads a software copy (a
 * hardware bitmap's pixels can't be read, update 192).
 */
internal fun photoShellTone(b: android.graphics.Bitmap): Color? = runCatching {
    val soft = if (b.config == android.graphics.Bitmap.Config.HARDWARE) b.copy(android.graphics.Bitmap.Config.ARGB_8888, false) else b
    val y = soft.height / 2
    var found: Color? = null
    for (x in 0 until soft.width / 3) {
        val px = soft.getPixel(x, y)
        if ((px ushr 24) > 200) {
            val inX = (x + soft.width / 40).coerceAtMost(soft.width - 1)
            found = Color(soft.getPixel(inX, y)).copy(alpha = 1f)
            break
        }
    }
    found
}.getOrNull()
