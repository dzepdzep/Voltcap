package com.shelf.launcher.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.Features

/*
 * Update 137: R1 tiles that adjust what you're looking at — Art position (was Art drop), and Cartridge colour — close the R1 panel and
 * show their control on the home screen instead: under the game, or above it when the game row sits at the bottom, so
 * the game it changes stays in sight while you change it.
 */
internal object HomeAdjust {
    enum class Kind { ART_DROP, CART_COLOUR }

    /**
     * What's being adjusted: [model] Art drop of the 3D models (else of the Box art looks); the game you were on.
     * Update 202: [look] — Art position for that look (covers, Box art, cartridges, discs; from Edit Home); null = [model]'s.
     */
    class Target(val kind: Kind, val model: Boolean = false, val platform: String? = null, val name: String? = null, val look: ArtPlace.Look? = null)

    /** The control showing on the home screen, or null. LauncherScreen closes the R1 panel when this is set. */
    var open by mutableStateOf<Target?>(null)
}

/** Update 137: where the cover sits in its window: moved ([x], [y], −0.5…1 of [ArtPlace.REACH] × the window) and sized ([zoom]). */
internal class ArtPos(val x: Float, val y: Float, val zoom: Float) {
    val key: String get() = "$x,$y,$zoom"
    companion object { val NONE = ArtPos(0f, 0f, 1f) }
}

/** The Art position settings of a look (the 3D models' window, or the Box art looks' case template), each its own. */
internal object ArtPlace {
    /** How far a full step (100) moves the cover: this much of the window's width / height. */
    const val REACH = 0.3f

    fun drop(model: Boolean) = if (model) Features.artDropModel else Features.artDropCase
    fun shift(model: Boolean) = if (model) Features.artShiftModel else Features.artShiftCase
    fun zoom(model: Boolean) = if (model) Features.artZoomModel else Features.artZoomCase

    /** The look's position now (read in composition: it redraws when it changes). */
    fun of(model: Boolean) = ArtPos(shift(model).state.value / 100f, drop(model).state.value / 100f, zoom(model).state.value / 100f)

    fun moved(model: Boolean) = drop(model).state.value != 0 || shift(model).state.value != 0 || zoom(model).state.value != 100

    fun reset(model: Boolean) { drop(model).value = 0; shift(model).value = 0; zoom(model).value = 100 }

    /**
     * Update 202: the looks Art position works on, each its own: Box art (its three settings above, as before), and the
     * covers, cartridges and discs of the game row (Features.artPosLooks; GameCard draws them moved and sized).
     */
    enum class Look(val label: String) { BOX("Box art"), COVER("Covers"), CART("Cartridges"), DISC("Discs") }

    private fun looks(): Map<String, Triple<Int, Int, Int>> = Features.artPosLooks.state.value.split(';').mapNotNull { p ->
        val i = p.indexOf('=')
        if (i <= 0) return@mapNotNull null
        val v = p.substring(i + 1).split(',').mapNotNull { it.trim().toIntOrNull() }
        if (v.size < 3) null else p.substring(0, i) to Triple(v[0], v[1], v[2])
    }.toMap()

    /** [look]'s left / right, up / down and size (−50…50, −50…100, 50–200; reads state). */
    fun get(look: Look): Triple<Int, Int, Int> =
        if (look == Look.BOX) Triple(shift(false).state.value, drop(false).state.value, zoom(false).state.value)
        else looks()[look.name] ?: Triple(0, 0, 100)

    fun set(look: Look, x: Int, y: Int, z: Int) {
        val cx = x.coerceIn(-50, 50); val cy = y.coerceIn(-50, 100); val cz = z.coerceIn(50, 200)
        if (look == Look.BOX) { shift(false).value = cx; drop(false).value = cy; zoom(false).value = cz; return }
        val m = looks().toMutableMap()
        if (cx == 0 && cy == 0 && cz == 100) m.remove(look.name) else m[look.name] = Triple(cx, cy, cz)
        Features.artPosLooks.value = m.entries.joinToString(";") { "${it.key}=${it.value.first},${it.value.second},${it.value.third}" }
    }

    /** [look]'s position now (read in composition: it redraws when it changes). */
    fun ofLook(look: Look): ArtPos = get(look).let { ArtPos(it.first / 100f, it.second / 100f, it.third / 100f) }

    fun movedLook(look: Look): Boolean = get(look).let { it.first != 0 || it.second != 0 || it.third != 100 }

    fun resetLook(look: Look) = set(look, 0, 0, 100)
}

/**
 * Update 202: [this] drawn at its look's Art position (moved within its place and sized), or as it is when centred. The
 * covers, cartridges and discs of the game row (GameCard); Box art does it in the case's window itself (CoverCases.kt).
 * (Not @Composable: it only reads state, which the composition calling it follows.)
 */
internal fun Modifier.artPosition(look: ArtPlace.Look?): Modifier {
    if (look == null || look == ArtPlace.Look.BOX) return this
    val pos = ArtPlace.ofLook(look)
    if (pos.x == 0f && pos.y == 0f && kotlin.math.abs(pos.zoom - 1f) < 0.005f) return this
    return this.graphicsLayer {
        scaleX = pos.zoom; scaleY = pos.zoom
        translationX = pos.x * size.width * ArtPlace.REACH; translationY = pos.y * size.height * ArtPlace.REACH
    }
}

/** Colours offered for a custom cartridge (besides its console's own, and any you type). */
private val CART_COLOURS = listOf(
    0xFF2B2D33, 0xFF5D6168, 0xFFA3A8AD, 0xFFE9EAEC, 0xFF6951AC, 0xFF8E4FC2, 0xFFE46FA8, 0xFFD23B3B,
    0xFFEC7136, 0xFFF2C230, 0xFF9CCB3B, 0xFF4FA35A, 0xFF2FA7A0, 0xFF4F87B0, 0xFF3C6FD1, 0xFF2B3F7A
).map { it.toInt() }

/** The control of [HomeAdjust.open], at the top of the screen ([atTop]) or the bottom. */
@Composable
internal fun HomeAdjustBar(atTop: Boolean) {
    val t = HomeAdjust.open ?: return
    val close = { HomeAdjust.open = null }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = close,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ImmersiveDialogWindow()
        val view = androidx.compose.ui.platform.LocalView.current
        androidx.compose.runtime.SideEffect {
            (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.setDimAmount(0f)
        }
        val screen = androidx.compose.ui.platform.LocalConfiguration.current
        val dens = androidx.compose.ui.platform.LocalDensity.current.density
        val gestures = if (t.kind == HomeAdjust.Kind.ART_DROP) {
            // Art position: drag anywhere on the screen moves the cover, pinch sizes it (a screen's width ≈ the whole reach).
            Modifier.pointerInput(t.model, t.look) {
                val span = (screen.screenWidthDp * dens / 3f).coerceAtLeast(1f)
                // Update 202: for the look it was opened for (Box art from the R1 tile; any look from Edit Home).
                val look = t.look ?: ArtPlace.Look.BOX
                detectTransformGestures { _, pan, zoom, _ ->
                    val now = ArtPlace.get(look)
                    ArtPlace.set(
                        look,
                        (now.first + pan.x / span * 100f).toInt(),
                        (now.second + pan.y / span * 100f).toInt(),
                        if (zoom != 1f) (now.third * zoom).toInt() else now.third
                    )
                }
            }
        } else Modifier.pointerInput(Unit) { detectTapGestures { close() } }
        Box(
            Modifier.fillMaxSize().then(gestures).padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = if (atTop) Alignment.TopCenter else Alignment.BottomCenter
        ) {
            // (A tap on the card itself doesn't close it.)
            Box(Modifier.widthIn(max = 820.dp).pointerInput(Unit) { detectTapGestures { } }) {
                when (t.kind) {
                    HomeAdjust.Kind.ART_DROP -> ArtDropBar(t.look ?: ArtPlace.Look.BOX, close)
                    HomeAdjust.Kind.CART_COLOUR -> CartColourBar(t, close)
                }
            }
        }
    }
}

@Composable
private fun ArtDropBar(look: ArtPlace.Look, close: () -> Unit) {
    // Update 202: any look (Box art as before — its own three settings — and the covers, cartridges and discs).
    val (x, y, z) = ArtPlace.get(look)
    fun put(nx: Int = ArtPlace.get(look).first, ny: Int = ArtPlace.get(look).second, nz: Int = ArtPlace.get(look).third) = ArtPlace.set(look, nx, ny, nz)
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { first.requestFocus() } }
    // D-pad moves the cover, L1 / R1 (or L2 / R2) size it, A is Done, Y resets; touch: drag and pinch anywhere.
    Box(
        Modifier.focusRequester(first).focusable().onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val now = ArtPlace.get(look)
            when (e.key) {
                Key.DirectionLeft -> { put(nx = now.first - 2); true }
                Key.DirectionRight -> { put(nx = now.first + 2); true }
                Key.DirectionUp -> { put(ny = now.second - 2); true }
                Key.DirectionDown -> { put(ny = now.second + 2); true }
                Key.ButtonL1, Key.ButtonL2, Key.PageUp -> { put(nz = now.third - 5); true }
                Key.ButtonR1, Key.ButtonR2, Key.PageDown -> { put(nz = now.third + 5); true }
                Key.ButtonY -> { ArtPlace.resetLook(look); true }
                // (Update 202: B closes it too — from Edit Home it goes back there.)
                Key.ButtonA, Key.Enter, Key.DirectionCenter, Key.ButtonB, Key.Back -> { close(); true }
                else -> false
            }
        }
    ) {
        ShelfCard {
            // (Update 200: the 3D models' Art position went with the retired pre-rendered 3D models.)
            SectionLabel("Art position · ${look.label}")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    MutedNote("Drag the picture anywhere on the screen to move it, pinch to resize. D-pad: move · L1 / R1: size · A: Done · Y: Reset.")
                    MutedNote("Left / right $x · Up / down $y · Size $z%")
                }
                Spacer(Modifier.width(12.dp))
                Chip("Smaller", accent = false) { put(nz = ArtPlace.get(look).third - 5) }
                Spacer(Modifier.width(8.dp))
                Chip("Bigger", accent = false) { put(nz = ArtPlace.get(look).third + 5) }
                Spacer(Modifier.width(8.dp))
                Chip("Reset", accent = false) { ArtPlace.resetLook(look) }
                Spacer(Modifier.width(8.dp))
                Chip("Done", onClick = close)
            }
            MutedNote(
                when (look) {
                    ArtPlace.Look.BOX -> "Every game in the Box art looks."
                    ArtPlace.Look.COVER -> "Every game's cover in the game row (moved and sized in its place)."
                    ArtPlace.Look.CART -> "Every game's cartridge in the game row (moved and sized in its place)."
                    ArtPlace.Look.DISC -> "Every game's disc in the game row (moved and sized in its place)."
                }
            )
        }
    }
}

@Composable
private fun CartColourBar(t: HomeAdjust.Target, close: () -> Unit) {
    ModelColors.version.intValue
    val platform = t.platform
    val name = t.name
    val forGame = platform != null && name != null
    var gameScope by remember { mutableStateOf(forGame && (ModelColors.game(platform!!, name!!) != null || ModelColors.all() == null)) }
    val sys = Models3D.systemOf(platform)
    val own = Models3D.consoleColor(sys)
    val mine = if (gameScope && forGame) ModelColors.game(platform!!, name!!) else ModelColors.all()
    // What the cartridge wears now (for this scope): yours, else (this game) the colour for all of them, else the console's.
    val now = mine ?: (if (gameScope) ModelColors.all() else null) ?: own ?: 0xFFA3A8AD.toInt()
    val set: (Int?) -> Unit = { c -> if (gameScope && forGame) ModelColors.setGame(platform!!, name!!, c) else ModelColors.setAll(c) }
    ShelfCard {
        SectionLabel("Cartridge colour" + if (gameScope && forGame) " · $name" else " · All custom cartridges")
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { first.requestFocus() } }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                ChipFlow {
                    if (forGame) Box(Modifier.focusRequester(first)) {
                        Chip((if (gameScope) "✓ " else "") + "This game", accent = gameScope) { gameScope = true }
                    }
                    Box(if (forGame) Modifier else Modifier.focusRequester(first)) {
                        Chip((if (!gameScope || !forGame) "✓ " else "") + "All custom cartridges", accent = !gameScope || !forGame) { gameScope = false }
                    }
                }
                Spacer(Modifier.height(8.dp))
                // ⌀ = no colour of yours here: the console's own (this game: the one for all, if you set one).
                ColorChoices(CART_COLOURS, mine) { set(it) }
                Spacer(Modifier.height(8.dp))
                ChipFlow {
                    Chip(if (gameScope && forGame) "Back to the usual colour" else "Console colours", accent = false) { set(null) }
                    Chip("Done", onClick = close)
                }
            }
            Column(Modifier.weight(1f)) {
                val hsv = remember(now) { FloatArray(3).also { android.graphics.Color.colorToHSV(now, it) } }
                PercentSlider("Hue", (hsv[0] / 360f * 100f).toInt(), { v ->
                    // (A grey has no hue: give it some colour so the slider shows what it does.)
                    val s = if (hsv[1] < 0.15f) 0.6f else hsv[1]
                    val b = if (hsv[2] < 0.15f) 0.7f else hsv[2]
                    set(android.graphics.Color.HSVToColor(floatArrayOf(v / 100f * 360f, s, b)))
                }, live = true, valueText = { "${(it * 3.6f).toInt()}°" })
                ColorFineTune("Colour", now) { set(it) }
            }
        }
        MutedNote(
            (if (sys != null) "Without a colour of yours, a custom cartridge wears its console's colour and logo. " else "") +
                "Only the custom and clear cartridges change; the real ones stay as they are."
        )
    }
}
