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

    /** What's being adjusted: [model] Art drop of the 3D models (else of the Box art looks); the game you were on. */
    class Target(val kind: Kind, val model: Boolean = false, val platform: String? = null, val name: String? = null)

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
            Modifier.pointerInput(t.model) {
                val span = (screen.screenWidthDp * dens / 3f).coerceAtLeast(1f)
                detectTransformGestures { _, pan, zoom, _ ->
                    val m = t.model
                    ArtPlace.shift(m).value = (ArtPlace.shift(m).value + pan.x / span * 100f).toInt().coerceIn(-50, 50)
                    ArtPlace.drop(m).value = (ArtPlace.drop(m).value + pan.y / span * 100f).toInt().coerceIn(-50, 100)
                    if (zoom != 1f) ArtPlace.zoom(m).value = (ArtPlace.zoom(m).value * zoom).toInt().coerceIn(50, 200)
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
                    HomeAdjust.Kind.ART_DROP -> ArtDropBar(t.model, close)
                    HomeAdjust.Kind.CART_COLOUR -> CartColourBar(t, close)
                }
            }
        }
    }
}

@Composable
private fun ArtDropBar(model: Boolean, close: () -> Unit) {
    val x = ArtPlace.shift(model); val y = ArtPlace.drop(model); val z = ArtPlace.zoom(model)
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { first.requestFocus() } }
    // D-pad moves the cover, L1 / R1 (or L2 / R2) size it, A is Done, Y resets; touch: drag and pinch anywhere.
    Box(
        Modifier.focusRequester(first).focusable().onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (e.key) {
                Key.DirectionLeft -> { x.value = (x.state.value - 2).coerceIn(-50, 50); true }
                Key.DirectionRight -> { x.value = (x.state.value + 2).coerceIn(-50, 50); true }
                Key.DirectionUp -> { y.value = (y.state.value - 2).coerceIn(-50, 100); true }
                Key.DirectionDown -> { y.value = (y.state.value + 2).coerceIn(-50, 100); true }
                Key.ButtonL1, Key.ButtonL2, Key.PageUp -> { z.value = (z.state.value - 5).coerceIn(50, 200); true }
                Key.ButtonR1, Key.ButtonR2, Key.PageDown -> { z.value = (z.state.value + 5).coerceIn(50, 200); true }
                Key.ButtonY -> { ArtPlace.reset(model); true }
                Key.ButtonA, Key.Enter, Key.DirectionCenter -> { close(); true }
                else -> false
            }
        }
    ) {
        ShelfCard {
            SectionLabel("Art position · " + if (model) "3D models" else "Box art")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    MutedNote("Drag the picture anywhere on the screen to move it, pinch to resize. D-pad: move · L1 / R1: size · A: Done · Y: Reset.")
                    MutedNote("Left / right ${x.state.value} · Up / down ${y.state.value} · Size ${z.state.value}%")
                }
                Spacer(Modifier.width(12.dp))
                Chip("Smaller", accent = false) { z.value = (z.state.value - 5).coerceIn(50, 200) }
                Spacer(Modifier.width(8.dp))
                Chip("Bigger", accent = false) { z.value = (z.state.value + 5).coerceIn(50, 200) }
                Spacer(Modifier.width(8.dp))
                Chip("Reset", accent = false) { ArtPlace.reset(model) }
                Spacer(Modifier.width(8.dp))
                Chip("Done", onClick = close)
            }
            MutedNote(if (model) "Every game in the 3D models look." else "Every game in the Box art looks.")
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
