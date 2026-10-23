package com.shelf.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.shelf.launcher.data.Features
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
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

/**
 * A view's turn for the game in it: degrees round the upright ([yawOf], read while drawing), and its camera distance.
 * Update 314: [opaqueOf] (read while drawing): the view draws the piece fully opaque right now — then it's drawn straight
 * into the view, without the buffer that makes a fading piece fade as one (pixel for pixel the same at full opacity, one
 * big GPU picture less per piece). Null: always through the buffer, as before.
 */
internal class PieceTurn(
    val yawOf: () -> Float, val camera: Float = 12f, val opaqueOf: (() -> Boolean)? = null,
    /**
     * Update 316 (read while composing): false while the view doesn't show the piece at all (a carousel game far off the
     * screen) — it keeps no GPU pictures then: Compose keeps every layer a piece makes alive, on the GPU too, until it's
     * let go, drawn or not (Flip 2: GPU memory full after ~90 s of scrolling). Null: always.
     */
    val activeOf: (() -> Boolean)? = null,
    /**
     * Update 317: how dark the view shades the piece (0–1, the carousel's games aside). The piece takes it on itself, as
     * a darker light on its pictures, once it's drawn from them ([shadeTaken]) — the view then needs no offscreen picture
     * of its own to darken it, which was drawn again every frame for every game aside. Null: the view shades it.
     */
    val shadeOf: (() -> Float)? = null,
    /**
     * Update 317 (Toni, option 2: "fewer copies while moving"): the view is moving (a carousel gliding) — the piece draws its
     * sides with at most [MOVING_COPIES] copies meanwhile, its full sides again the moment it stops. Null: never.
     */
    val movingOf: (() -> Boolean)? = null,
    /**
     * Update 325 (Toni: "with 5 each side it works super well, but I don't want the sides to feel empty"): the piece is far
     * out in the view (a carousel game past the nearest [FULL_NEAR] each side) — its sides are drawn with at most
     * [FAR_COPIES] copies, as while moving. (Its face alone, flat, was tried first — Toni: "I hate that they appear
     * flat".) Null: never.
     */
    val farOf: (() -> Boolean)? = null
) {
    /** Update 317: the piece applies [shadeOf] itself (it's drawn from its pictures); the view leaves it out. */
    val shadeTaken = mutableStateOf(false)
}

/**
 * Update 325 (Toni: "I liked the reflection — could it cost less?"): inside a carousel game's reflection. The piece there is
 * its turned face alone, no copies for its sides and no picture of its own taken — each reflection was a whole second
 * piece, its sides' copies and all; faint and fading, its thickness didn't show.
 */
internal val LocalPieceReflection = androidx.compose.runtime.staticCompositionLocalOf { false }

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
            // (Update 306: a pack shape modelled on a real cartridge — the N64 shape on the N64 Game Pak — turns with that
            // cartridge's thickness and back (its screws, panels, connector), the game's own console named on it;
            // CustomCarts.authenticShell. Pack shapes without one keep the plain slab.)
            sh == null -> choice.pack?.let { CustomCarts.authenticShell(it) }?.let { real ->
                val tone = packTone ?: CUSTOM_SHELL
                // (Update 325: its thickness only — no drawn back, the drawn cartridges are gone.)
                SolidSpec(choice.aspect, real.depth.coerceIn(0.02f, 0.4f), tone)
            } ?: SolidSpec(choice.aspect, 0.12f, packTone ?: CUSTOM_SHELL)
            else -> {
                val colour = CartShells.colourOf(sh, choice.colourKey)
                // (Update 210: a see-through plastic stays see-through turned — SolidSpec.clear.)
                // (Update 306: another console's game on this shell has its own console's name on the back — backFor.)
                SolidSpec(sh.aspect, sh.depth.coerceIn(0.02f, 0.4f), Color(colour.argb).copy(alpha = 1f), clear = if (colour.opacity < 0.85f) 0.5f else null, back = {
                    CartShells.backFor(sh, colour, 480, console, backStyle).asImageBitmap()
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
        // (Update 325, Toni: "delete the drawn cartridges, they don't look anything like our real models": the photo
        // keeps its real shell's thickness and plastic, but no drawn back — turned round it's its own plain plastic.)
        else SolidSpec(aspect ?: sh.aspect, sh.depth.coerceIn(0.02f, 0.4f), tone ?: Color(ownShellColour(sh, platform, gameKey).argb).copy(alpha = 1f))
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
                // (Update 239, #33: the shape it's drawn in — an app's / Android game's Switch-card shape, CustomCarts.lookFor.)
                CustomCarts.idFor(console)?.let { CustomCarts.asset(CustomCarts.lookFor(console), clear, it) }
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

    /** Update 308: the plastic of the pack picture [f] (read at 1/8 size once, then kept); null when none can be read. */
    fun toneOfFile(f: java.io.File): Color? {
        packs[f.path]?.let { c -> return if (c != 0) Color(c) else null }
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
    // (Update 325, Toni: "discs don't spin and cartridges don't wobble", even while going through games: Performance ›
    // Automatic had turned Lite on after slow frames, and Lite stopped every one. The game you're on keeps its motion in
    // Lite — one piece; the others stay still.)
    val on = (!LocalLiteEffects.current || LocalMediaFocused.current) && rememberAnimationsEnabled() && rememberIsActive() && !HomeIdle.calm
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
 * Update 314: a piece whose whole content is one finished picture (a drawn shell's front, CartShellArt), fitted at
 * [aspect] in the node as CartShellArt lays it out (pieceArtRect). See [solidPiece]'s faceArt.
 */
internal class PieceFace(
    val image: ImageBitmap, val aspect: Float,
    /**
     * Update 316: the picture stretched to this part of the [aspect] box (left, top, right, bottom shares) — your pack's
     * shape drawn as its real cartridge (AuthenticPackCart, pieceFrameRect); null: fitted whole (CartShellArt).
     */
    val frame: FloatArray? = null
)

/**
 * Draws this node's content as the solid piece [spec] turned by the view's turn (LocalPieceTurn) plus its own spin.
 * Nothing changes (no layers) when nothing turns it.
 */
@Composable
internal fun Modifier.solidPiece(
    spec: SolidSpec?,
    /** Update 207: false for a piece that isn't a game's cartridge (the Save Vault's 3D card): no cartridge spin added. */
    withCartSpin: Boolean = true,
    /**
     * Update 314 (scrolling the 3D cartridges lagged): the content is exactly this picture ([PieceFace]), so the turned
     * face, its outline and its sides are drawn from the picture itself — made once, not every frame. Each side slice
     * used to draw the face's own layer again (up to ~130 times a piece), and Android re-walks and redraws everything
     * under a layer drawn more than once in a frame: every turned cartridge redrew five or six offscreen pictures each
     * frame. Now only the slices' smear and the whole piece are drawn per frame. Null: as before (any content).
     */
    faceArt: PieceFace? = null,
    /**
     * Update 317: what the content shows (its console, art and shape), so its picture (PieceShot) is kept for a piece of
     * the same content coming back on the screen — taking it again cost the screen's drawing ~10–80 ms. Null: not kept.
     */
    shotKey: String? = null
): Modifier {
    if (spec == null) return this
    // Update 317: the front's own picture for a piece without one (kept while it's off the screen) — see PieceShot.
    val shot = remember { PieceShot() }
    val turn = LocalPieceTurn.current
    val mirror = LocalPieceReflection.current
    // (Update 316: not shown at all — none of its layers kept; see PieceTurn.activeOf.)
    if (turn?.activeOf?.invoke() == false) return this
    val spin = if (withCartSpin) rememberCartSpin() else null
    if (turn == null && spin == null && TestHooks.pose.value == null) return this
    val dens = LocalDensity.current.density
    val cam = (turn?.camera ?: 14f) * dens
    // Update 316 (Perfetto traces, Flip 2: ~1,000 GPU pictures standing, the covers thrown out of the GPU's cache and
    // uploaded again every frame): the sides' picture — the face's outline in the plastic's colour with its sheen — made
    // once from the front, away from the screen's thread. With it the sides and the face are plain picture draws, no
    // offscreen picture of their own (and no smear: each slice's copies drawn at the very same places).
    val shotImage = if (faceArt == null) shot.image.value else null
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // (The same modifier for the piece's whole life: a new one at each recomposition — every step of a carousel — made the
    // content draw again, which counted as new content and took the picture again, until the piece gave up on it.)
    val shotMark = remember(shot) { Modifier.graphicsLayer { }.drawWithContent { shot.changed(); drawContent() } }
    val sideMade = rememberDerived((faceArt?.image ?: shotImage)?.asAndroidBitmap(), "side317|${spec.tone.toArgb()}") { b -> sideArtOf(b, spec.tone.toArgb())?.asImageBitmap() }
    // (Update 317: the last sides' picture kept while a new front's is made — the piece stays drawn from its pictures, so it
    // keeps darkening itself; see shadeTaken below.)
    val lastSide = remember { mutableStateOf<ImageBitmap?>(null) }
    if (sideMade != null && lastSide.value !== sideMade) androidx.compose.runtime.SideEffect { lastSide.value = sideMade }
    val sideArt = sideMade ?: lastSide.value?.takeIf { faceArt?.image != null || shotImage != null }
    // (Update 317: drawn from its pictures — it takes the view's shade on itself; see PieceTurn.shadeOf.)
    val fromPictures = sideArt != null && (faceArt?.image ?: shotImage) != null
    // (Update 317: set to whether it's drawn from its pictures — they're kept while a new front's are made, lastSide, so it
    // no longer flips back for a frame while a piece rebuilds, which left the games aside undarkened: flashing brighter.
    // A passing buffer to darken it then held ~470 GPU pictures on the Flip 2.)
    // (Update 317, Toni: still flashing now and then — a piece coming in before its pictures were ready left the view to
    // darken it a frame late. The piece now always darkens itself: through its light once drawn from its pictures, else
    // through its whole-piece buffer's paint — so the view never does, and there's no hand-over left.)
    if (turn?.shadeOf != null) androidx.compose.runtime.SideEffect { turn.shadeTaken.value = true }
    // Update 316 (Flip 2: ~1,000 GPU pictures, 1.2 GB, climbing while scrolling until it froze): the piece's layers kept for
    // as long as the piece is shown, recorded again when its drawing is rebuilt. They were made new at every rebuild —
    // and a carousel rebuilt its pieces at each step — while the ones let go kept their GPU pictures for a long time.
    val face = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val edge = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val lit = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val back = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val whole = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val solid = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val smear = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val hole = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val edgeLeafLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val litLeafLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    return this.drawWithCache {
        // (Update 294: the face drawn flat into its own picture, which is then turned — the turned layers below used to
        // draw its contents (a badge's or a label's text) under a new angle every frame, and Android 13's text cache
        // grew until the Thor stopped answering on the Hub.)
        face.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
        // (Update 205: the sides are the piece's outline in its plastic's colour, made in a layer of their own.)
        edge.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
        // All of it in one buffer, so a faded piece (a carousel fades its games op by op) fades as one, its slices never
        // showing through its face.
        whole.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
        // The back in a layer of its own, so its tone and shade only touch the back (see InspectSlab, update 201).
        back.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
        // The face's outline made solid, for a back without a drawn one (a clear shell's face is see-through): `solid`.
        // Update 294: one slice's side smeared across the gap to the next (see below), drawn once into its own picture.
        smear.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
        // Update 209: the face's outline that clears a see-through piece's sides from behind its face.
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
        // (Room round it in the buffer: the near side grows past the node as it turns, and the slices stand out.)
        val m = (0.35f * maxOf(size.width, size.height) + depthPx).roundToInt()
        // (Update 314, Flip 2 ANR — the GPU ran out of memory: the smear was made inside the whole piece's buffer and took
        // that buffer's size plus the margin again, about twice the pixels it needs. Both are this size now.)
        val buf = IntSize(size.width.roundToInt() + 2 * m, size.height.roundToInt() + 2 * m)
        // Update 314: the picture's place in the node, as CartShellArt draws it (null: any content, drawn each frame).
        // (Update 317: a piece without its front's picture uses its own shot of it once there is one, made at the node's size.)
        val shotFace = shotImage?.takeIf { it.width == size.width.toInt() && it.height == size.height.toInt() && size.height > 0f }
            ?.let { PieceFace(it, size.width / size.height) }
        val pf = faceArt ?: shotFace
        val art = pf?.image
        val artAt = pf?.let { f ->
            f.frame?.let { fr -> pieceFrameRect(size.width, size.height, f.aspect, fr) }
                ?: pieceArtRect(size.width, size.height, f.aspect, f.image.width, f.image.height)
        }
        val artPos = artAt?.let { IntOffset(it[0], it[1]) }
        val artSize = artAt?.let { IntSize(it[2], it[3]) }
        val artSrc = art?.let { IntSize(it.width, it.height) }
        val solidFilter = solid.colorFilter
        // (Update 316: plain layers — no offscreen picture — for the sides and the face drawn from the pictures.)
        val side = sideArt?.takeIf { art != null }
        val sideSrc = side?.let { IntSize(it.width, it.height) }
        val edgeLeaf = if (side != null) edgeLeafLayer.also { it.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.ModulateAlpha } else null
        val litLeaf = if (side != null) litLeafLayer else null
        var artMade = false
        var solidMade = false
        var madeYaw = Float.NaN
        var madeShade = -1f
        var madeMoving = false
        var madeFar = false
        onDrawWithContent {
            // (Update 294, testing only: TestHooks `voltcap_test_pose` — held at one turn, for before / after pictures.)
            val testPose = TestHooks.pose.value
            val yaw = testPose?.first ?: ((turn?.yawOf?.invoke() ?: 0f) + (spin?.invoke() ?: 0f))
            // (Update 317: the view's shade, on the piece's own light — see PieceTurn.shadeOf.)
            val shadeS = if (turn?.shadeOf != null) turn.shadeOf.invoke().coerceIn(0f, 1f) else 0f
            // (Update 325, Thor recording — Mix look, All games, still 250 ms a frame, 41 ms of it handing the frame to the
            // GPU: a piece without a sides' picture — the cartridge / disc photos — was darkened through its whole-piece
            // buffer, so every game aside drew itself into a buffer of its own in every frame as the row moved. Each of its
            // parts — sides, face, back — now carries the shade in its own light, like the pieces with a sides' picture; the
            // buffer is left for a piece facing you square, drawn as its content, and for one fading in or out.)
            val shadeK = 1f - shadeS
            val flat = abs(yaw) < 0.25f
            // (Facing you square, drawn as its own content: darkened through the whole-piece buffer's paint.)
            val bufShade = flat && art == null && shadeS > 0.01f
            whole.colorFilter = if (bufShade) SolidLight.of(1f - shadeS) else null
            if (flat && (bufShade || shadeS <= 0.01f)) {
                if (bufShade) {
                    whole.record(size = buf) { translate(m.toFloat(), m.toFloat()) { this@onDrawWithContent.drawContent() } }
                    translate(-m.toFloat(), -m.toFloat()) { drawLayer(whole) }
                } else drawContent()
                madeYaw = Float.NaN
                return@onDrawWithContent
            }
            val moving = turn?.movingOf?.invoke() == true
            val far = turn?.farOf?.invoke() == true
            // (Update 314: fully opaque in its view — drawn straight into it, no whole-piece buffer.)
            // (Not a see-through piece: its cut-out behind the face, DstOut, must stay inside its own buffer.)
            val direct = testPose == null && spec.clear == null && turn?.opaqueOf?.invoke() == true && !bufShade
            if (art == null) face.record { this@onDrawWithContent.drawContent() }
            if (moving) PieceShots.hold(500)
            // (Update 317: the content's picture taken again whenever it changes — see PieceShot.)
            if (faceArt == null && testPose == null && !shot.off) {
                val want = "${shot.version.intValue}|${size.width.toInt()}x${size.height.toInt()}"
                val keep = shotKey?.let { "$it|${size.width.toInt()}x${size.height.toInt()}" }
                if (shot.image.value == null && keep != null) PieceShots.get(keep)?.let { shot.image.value = it; shot.asked = want }
                // (Update 325: a reflection uses a picture already taken for the same content, never takes one itself.)
                if (shot.asked != want && !mirror) {
                    // (Its own drawing for the picture isn't new content: drawn again here after a while away from the
                    // screen's drawing, the content draws itself again — counted, that took a picture every frame.)
                    if (art != null) {
                        shot.quiet = true
                        try { face.record { this@onDrawWithContent.drawContent() } } finally { shot.quiet = false }
                    }
                    shot.take(scope, face, want, keep)
                }
            }
            // (Update 314: the same turn as last drawn — the parts only drawn from the picture are still right.)
            if (art != null && artMade && yaw == madeYaw && shadeS == madeShade && moving == madeMoving && far == madeFar && testPose == null && !direct) {
                translate(-m.toFloat(), -m.toFloat()) { drawLayer(whole) }
                return@onDrawWithContent
            }
            val t = Math.toRadians(yaw.toDouble())
            val c = cos(t).toFloat()
            val sn = sin(t).toFloat()
            edge.rotationY = yaw; edge.cameraDistance = cam
            lit.rotationY = yaw; lit.cameraDistance = cam
            // (Update 316: the plain sides and face, their light drawn in — recorded again only as the turn changes.)
            if (edgeLeaf != null && litLeaf != null && side != null && art != null) {
                edgeLeaf.rotationY = yaw; edgeLeaf.cameraDistance = cam
                litLeaf.rotationY = yaw; litLeaf.cameraDistance = cam
                val edgeLight = SolidLight.of((0.42f + 0.22f * abs(sn)) * shadeK)
                // (Update 317: the sides' picture is SIDE_SPREAD px wider each side — drawn that much wider.)
                val padN = (SIDE_SPREAD * artSize!!.width / (side.width - 2 * SIDE_SPREAD).coerceAtLeast(1).toFloat()).roundToInt()
                edgeLeaf.record {
                    drawImage(
                        side, IntOffset.Zero, sideSrc!!, IntOffset(artPos!!.x - padN, artPos.y),
                        IntSize(artSize.width + 2 * padN, artSize.height), colorFilter = edgeLight
                    )
                }
                if (c >= 0f) {
                    val faceLight = SolidLight.of((1f - 0.4f * abs(sn)) * shadeK)
                    litLeaf.record { drawImage(art, IntOffset.Zero, artSrc!!, artPos!!, artSize!!, colorFilter = faceLight) }
                }
            }
            back.rotationY = yaw; back.cameraDistance = cam
            // (Update 209: a see-through piece's back as see-through as its sides.)
            back.alpha = spec.clear?.let { 0.5f + 0.5f * it.coerceIn(0f, 1f) } ?: 1f
            // (Each part recorded at the node's own size, round its own middle; then placed in the buffer below.)
            // The sides: the face's outline, darker, lit a little as they face you. Update 205: filled with the shell's
            // plastic — they were copies of the whole face, so a cartridge seen from the side showed its label over and
            // over in stripes (the "glitches" round the turned cartridges in the Box flow stacks).
            val sideTone = spec.tone.copy(alpha = 1f)
            edge.colorFilter = SolidLight.of((0.42f + 0.22f * abs(sn)) * shadeK)
            if (art == null) {
                solid.record { drawLayer(face) }
                edge.record {
                    drawLayer(solid)
                    drawRect(sideTone, blendMode = BlendMode.SrcIn)
                    drawRect(sheen, blendMode = BlendMode.SrcAtop)
                }
            } else if (!artMade) {
                // (Update 314: each made once from the picture, with no other layer inside — so drawing the sides many
                // times a frame never makes Android redraw them, or the face, again.)
                artMade = true
                // (Update 316: not with the plain sides and face — edgeLeaf / litLeaf: an offscreen layer recorded and never
                // drawn still held its GPU picture.)
                if (edgeLeaf == null) {
                    edge.record {
                        drawImage(art, IntOffset.Zero, artSrc!!, artPos!!, artSize!!, colorFilter = solidFilter)
                        drawRect(sideTone, blendMode = BlendMode.SrcIn)
                        drawRect(sheen, blendMode = BlendMode.SrcAtop)
                    }
                    lit.record { drawImage(art, IntOffset.Zero, artSrc!!, artPos!!, artSize!!) }
                }
            }
            // (Update 316: the outline made solid only once it's drawn — behind a see-through piece's face, or its back.)
            if (art != null && !solidMade && (spec.clear != null || c < 0f)) {
                solidMade = true
                solid.record { drawImage(art, IntOffset.Zero, artSrc!!, artPos!!, artSize!!) }
            }
            madeYaw = yaw
            madeShade = shadeS
            madeMoving = moving
            madeFar = far
            if (c >= 0f) {
                lit.colorFilter = SolidLight.of((1f - 0.4f * abs(sn)) * shadeK)
                if (art == null) lit.record { drawLayer(face) }
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
                    if (shadeK < 1f) drawRect(Color.Black, alpha = 1f - shadeK, blendMode = BlendMode.SrcAtop)
                }
            }
            // (The node's middle: inside the whole-piece buffer the drawing area is bigger than the node.)
            val nodeC = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
            // The piece itself: far to near, the slices, then the face (or the back), in the node's own place.
            val pieceDraw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit = {
                // Far to near: the slices, then the face (or the back, seen from behind).
                // (Update 205: close enough together that no gap shows between them on a thick piece side-on.)
                // (Update 207: a slice every 2 px, at most 32 — 64 slices a piece, every piece in a row, each frame,
                // was part of what made scrolling a big library heavy; 2 px apart still shows no gap.)
                // (Update 324, Thor recording — Mix look, All games: 308 ms a frame, 63 ms of it handing the frame to the GPU:
                // the cartridge / disc photos — this path — drew up to 32 slices and redrew their smear picture for every piece
                // in every frame. At most 16 now, and while the row glides 6 with no smear, as the shells' sides (MOVING_COPIES).)
                val n = if (mirror) 0 else ceil(depthPx * abs(sn) / 2f).toInt().coerceIn(0, if (far) FAR_COPIES else if (moving) MOVING_SLICES else 16)
                val clear = spec.clear
                // Update 294 (Toni, the Thor: stepped light outlines / stacked layers round a turned cartridge's edges —
                // the small cart beside the one you're on in the Hub's cover flow, the stacked pieces in the 9/26 view):
                // the slices are copies of the turned outline moved sideways, and on the Thor's bigger pieces they're
                // further apart than 2 px (at most 32 of them: 4–5 px on a big N64 cartridge seen at 60–80°). Each
                // slice's slanted top / bottom edge then ends in a step before the next one starts, and the steps
                // read as a stack of layers. Each slice is now smeared across the gap to the next one — its side
                // drawn again 1 px apart (at most 4 times, into one picture made once a frame) — so the sides are one
                // solid edge at every angle, with the same 32 slice draws as before (no new render load).
                val dz = if (n > 0) depthPx / n else 0f
                val step = dz * sn * (if (c >= 0f) 1f else -1f)
                val fill = if (n > 0 && testPose?.second != true && !moving && !far) ceil(abs(step)).toInt().coerceIn(1, 4) else 1
                val copies = n * fill + 1
                // (Update 209: a see-through piece's slices each faint enough that all of them together are [clear].)
                // (Update 316: the plain sides when there's a sides' picture — edgeLeaf — else the offscreen ones.)
                val edgeL = edgeLeaf ?: edge
                // (Update 317: drawn from the sides' picture — a copy every [SIDE_SPREAD] px (foreshortened), the near one,
                // under the face, left out; see SIDE_SPREAD.)
                val reach = if (edgeLeaf != null && side != null && artSize != null)
                    (SIDE_SPREAD * artSize.width / (side.width - 2 * SIDE_SPREAD).coerceAtLeast(1).toFloat() * abs(c)).coerceAtLeast(1.5f) else 0f
                val span = depthPx * abs(sn)
                val nLeaf = if (edgeLeaf == null || span < 0.5f || mirror) 0 else if (far) ceil(span / reach).toInt().coerceIn(1, FAR_COPIES) else ceil(span / reach).toInt().coerceIn(1, if (moving) MOVING_COPIES else 24)
                edgeL.alpha = if (edgeLeaf != null) {
                    if (clear == null || nLeaf <= 0) 1f else (1.0 - Math.pow(1.0 - clear.coerceIn(0.05f, 0.95f), 1.0 / nLeaf)).toFloat()
                } else if (clear == null || n <= 0) 1f else (1.0 - Math.pow(1.0 - clear.coerceIn(0.05f, 0.95f), 1.0 / copies)).toFloat()
                // (Update 325, Toni: "a floating layer over the Game Boy cartridges on top and a bit on the left": the copies
                // behind the face were drawn as big as the face — moved sideways only, never further away — so seen large, in
                // Inspect, the far ones stood out past its top edge. Each is drawn as much smaller as it is further from you
                // than the face: the turned layers' own camera, [cam] × 72 px away (Skia's camera works in inches).)
                val camPx = cam * 72f
                val nearV = -depthPx / 2f * abs(c)
                fun depthK(z: Float): Float = ((camPx + nearV) / (camPx - z * c)).coerceIn(0.5f, 1f)
                if (edgeLeaf != null) for (k in 0 until nLeaf) {
                    val f = k.toFloat() / nLeaf
                    val z = if (c >= 0f) -depthPx / 2f + depthPx * f else depthPx / 2f - depthPx * f
                    val dk = depthK(z)
                    scale(dk, dk, pivot = nodeC) { translate(z * sn, 0f) { drawLayer(edgeLeaf) } }
                }
                if (n > 0 && fill > 1 && edgeLeaf == null) {
                    smear.record(size = buf) {
                        translate(m.toFloat(), m.toFloat()) {
                            for (j in 0 until fill) translate(step * j / fill, 0f) { drawLayer(edge) }
                        }
                    }
                }
                if (n > 0 && edgeLeaf == null) for (k in 0..n) {
                    val f = k.toFloat() / n
                    val z = if (c >= 0f) -depthPx / 2f + depthPx * f else depthPx / 2f - depthPx * f
                    // (The nearest slice alone: nothing past it to fill.)
                    val dk = depthK(z)
                    scale(dk, dk, pivot = nodeC) {
                        if (fill > 1 && k < n) translate(z * sn - m, -m.toFloat()) { drawLayer(smear) }
                        else translate(z * sn, 0f) { drawLayer(edge) }
                    }
                }
                val faceX = if (c >= 0f) depthPx / 2f * sn else -depthPx / 2f * sn
                // (Update 209: none of a see-through piece's sides behind its face — they showed through it.)
                if (clear != null) {
                    hole.rotationY = yaw; hole.cameraDistance = cam
                    hole.record { drawLayer(solid) }
                    translate(faceX, 0f) { drawLayer(hole) }
                }
                if (c >= 0f) translate(faceX, 0f) { drawLayer(litLeaf ?: lit) }
                else translate(faceX, 0f) { drawLayer(back) }
            }
            if (direct) {
                // (Update 314: fully opaque in its view — straight in; nothing shows through, so it reads as one piece.)
                pieceDraw()
                madeYaw = Float.NaN
                return@onDrawWithContent
            }
            whole.record(size = buf) { translate(m.toFloat(), m.toFloat()) { pieceDraw() } }
            translate(-m.toFloat(), -m.toFloat()) { drawLayer(whole) }
        }
    }.then(
        // (Update 317: the content in a layer of its own, so its drawing runs only when it changes — PieceShot.version.)
        if (faceArt == null && !shot.off) shotMark else Modifier
    )
}

/**
 * Update 317 (Perfetto trace, Flip 2, scrolling the drawn console cartridges: ~6 frames a second, ~400 offscreen pictures
 * drawn again each frame — face, outline, sides and smear for each of ~75 turned pieces): a piece whose front isn't one
 * picture (the drawn templates, a cartridge photo, a pack picture) is drawn once into a picture of its own, and turned
 * from that like the shells (solidPiece's faceArt). Taken again whenever the content draws something new ([changed], run
 * from the content's own layer only when it does — a label coming in); until then it's drawn as before. Content that
 * keeps changing (an animation in it) gives up on the picture and is drawn as before.
 */
internal class PieceShot {
    val image = mutableStateOf<ImageBitmap?>(null)
    val version = androidx.compose.runtime.mutableIntStateOf(0)
    var asked: String? = null
    var off = false
    /** While the piece draws its content for the picture itself (not a change of the content). */
    var quiet = false
    private var job: kotlinx.coroutines.Job? = null
    private var restarts = 0
    private var fails = 0
    private var taken = 0

    fun changed() { if (!quiet) version.intValue++ }

    fun take(scope: kotlinx.coroutines.CoroutineScope, layer: androidx.compose.ui.graphics.layer.GraphicsLayer, key: String, keep: String? = null) {
        asked = key
        // (Never more than [MAX_SHOTS] for a piece: the last one stays — each new one also makes its sides' picture again.)
        if (taken >= MAX_SHOTS) return
        if (job?.isActive == true && ++restarts > 40) { off = true; image.value = null; job?.cancel(); return }
        job?.cancel()
        job = scope.launch {
            var done = false
            try {
                // (A short wait, so a label still coming in isn't taken half drawn.)
                kotlinx.coroutines.delay(120)
                // (Update 325: not while a carousel glides or a new category comes in — each picture waits on the render
                // thread, which is busiest then; taken once it's been still a moment — PieceShots.hold.)
                while (PieceShots.held()) kotlinx.coroutines.delay(200)
                if (asked != key) return@launch
                // (Update 325, Thor recording scrolling the Mix look: 2.7 s of 15 making these pictures — each one a
                // hardware picture made on the main thread, waiting on the render thread, 20–100 ms — and a row of new
                // games coming in all took theirs in the same frame: 1–2.4 s frames. One at a time now, a frame apart.)
                val b = SHOTS.withLock {
                    if (asked != key) null
                    // (Update 325, the next Thor recording: still 3.2 s of 15 between category steps, one right after another —
                    // a third of a second apart now, so the frames between them stay quick.)
                    else runCatching { layer.toImageBitmap() }.getOrNull().also { kotlinx.coroutines.delay(320) }
                }
                if (asked != key) return@launch
                if (b == null || b.width <= 0 || b.height <= 0) { if (++fails > 5) { off = true; image.value = null } }
                else if (asked == key) { restarts = 0; taken++; image.value = b; done = true; if (keep != null) PieceShots.put(keep, b) }
            } finally {
                // (Not taken — gone off the screen meanwhile, say: asked again the next time it's drawn.)
                if (!done && asked == key) asked = null
            }
        }
    }

    private companion object {
        const val MAX_SHOTS = 10
        /** Update 325: one piece's picture made at a time, across every piece. */
        val SHOTS = kotlinx.coroutines.sync.Mutex()
    }
}

/** Update 317: the pieces' pictures of their content ([PieceShot]) by what they show, for pieces coming back on the screen. */
internal object PieceShots {
    // (About 0.7 MB each at a carousel's size, on the GPU.)
    private val cache = android.util.LruCache<String, ImageBitmap>(96)
    @Volatile private var quietUntil = 0L
    /** Update 325: no pieces' pictures taken for the next [ms] (a carousel moving, a category coming in). */
    fun hold(ms: Long) { quietUntil = maxOf(quietUntil, android.os.SystemClock.uptimeMillis() + ms) }
    fun held(): Boolean = android.os.SystemClock.uptimeMillis() < quietUntil
    fun get(key: String): ImageBitmap? = cache.get(key)
    fun put(key: String, image: ImageBitmap) { cache.put(key, image) }
}

/**
 * Update 316: the sides' picture for a front [src]: its outline (alpha × 4 − 24, as the solid filter makes it) filled with
 * [tone], under the sheen (white 12 % at the top, clear in the middle, black 18 % at the bottom — the offscreen sides drew
 * the same over the node) — what the offscreen sides drew, made once on the CPU. Null when it can't be read.
 */
internal fun sideArtOf(src: android.graphics.Bitmap, tone: Int): android.graphics.Bitmap? = runCatching {
    val soft = if (src.config == android.graphics.Bitmap.Config.HARDWARE) src.copy(android.graphics.Bitmap.Config.ARGB_8888, false) else src
    val w0 = soft.width; val h = soft.height
    if (w0 <= 0 || h <= 0) return@runCatching null
    val src0 = IntArray(w0 * h)
    soft.getPixels(src0, 0, w0, 0, 0, w0, h)
    // (Update 317: [SIDE_SPREAD] px of room each side, and the outline spread that far sideways — see SIDE_SPREAD.)
    val r = SIDE_SPREAD
    val w = w0 + 2 * r
    val px = IntArray(w * h)
    val alpha = IntArray(w)
    val win = IntArray(w)
    for (y in 0 until h) {
        val rgb = sideRowRgb((y + 0.5f) / h, tone)
        java.util.Arrays.fill(alpha, 0)
        for (x in 0 until w0) alpha[x + r] = sideAlpha(src0[y * w0 + x] ushr 24)
        val row = y * w
        // (The widest alpha within r either side — a running maximum over the row, once per pixel.)
        var head = 0; var tail = 0
        for (x in 0 until w + r) {
            if (x < w) {
                while (tail > head && alpha[win[tail - 1]] <= alpha[x]) tail--
                win[tail++] = x
            }
            val c = x - r
            if (c < 0) continue
            while (win[head] < c - r) head++
            px[row + c] = (alpha[win[head]] shl 24) or rgb
        }
    }
    val made = android.graphics.Bitmap.createBitmap(px, w, h, android.graphics.Bitmap.Config.ARGB_8888)
    // (Update 317: kept on the GPU — as a plain picture it was thrown out of the GPU's cache and uploaded again, ~28 a
    // frame in the Flip 2 trace.)
    if (android.os.Build.VERSION.SDK_INT >= 26) made.copy(android.graphics.Bitmap.Config.HARDWARE, false) ?: made else made
}.getOrNull()

/**
 * Update 317 (Toni: "draw them like the cases, as long as they aren't thin like paper from the side"): the sides' picture
 * has this many px of room each side of the front's, and its outline is spread that far sideways. The turned piece's
 * side is copies of it from far to near; spread, each copy reaches the next across a gap of up to this much (foreshortened
 * by the turn), so a few copies make one solid edge — 4–20 a piece instead of 30–130 — the outline's shape (notches,
 * curves) kept, the side a few px thicker at most, never thinner.
 */
internal const val SIDE_SPREAD = 10

/** Update 317: the most copies a turned piece's side is drawn with while the view moves (PieceTurn.movingOf). */
// (Update 325: as many as at rest now — Toni: "the jagged bottoms appear only when scrolling": with 3 / 4 copies the
// steps between them showed while it moved, and the reflection turned out to be what cost. Only the smear stays off.)
internal const val MOVING_COPIES = 24
/** Update 324: a photo piece's slices while the row glides (at rest up to 16 — the same now, Update 325). */
internal const val MOVING_SLICES = 16

/** Update 325: how many games each side of a carousel's middle are drawn with their thickness (PieceTurn.farOf). */
internal const val FULL_NEAR = 5

/** Update 325: the most copies a piece past [FULL_NEAR] draws its sides with. */
internal const val FAR_COPIES = 3

/** A brightness filter in 40 steps (made once each). */
private object SolidLight {
    // (Update 317: 40 steps — the carousel's shade now changes through it as the games slide.)
    private val cache = arrayOfNulls<ColorFilter>(41)
    fun of(l: Float): ColorFilter? {
        val i = (l.coerceIn(0f, 1f) * 40f).roundToInt()
        if (i >= 40) return null
        cache[i]?.let { return it }
        val k = i / 40f
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
