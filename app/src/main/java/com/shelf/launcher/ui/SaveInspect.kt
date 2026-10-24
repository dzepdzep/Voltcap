package com.shelf.launcher.ui

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Features.SaveVaultStyle
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.Ps2BrowserStyle
import com.shelf.launcher.data.Ps2Icons
import com.shelf.launcher.data.SaveCapsules
import com.shelf.launcher.data.SyncState
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * (Update 241, #49–#52: Save Vault's own Free Inspect viewer. Save Vault › a save › Inspect — only there: no other screen,
 * look, carousel or media preview opens it, and the game window's own inspect view is left as it was. It shows the very
 * object the vault draws that save as — the drawn memory card, SD card, floppy or capsule, the 3D card from the 3D models
 * pack, or a PS2 save's own 3D icon with the PS2 browser style — and lets you look at it from every side:
 *  - touch / mouse: drag turns it (any way: round, over the top, under the bottom), two fingers move it and pinch zooms
 *    it, the mouse wheel zooms;
 *  - controller: the right stick turns it, L1 + left stick moves it, L2 / R2 zoom, Y puts it back as it was, X turns
 *    auto-turn on or off (off unless you turn it on; remembered), B closes; the D-pad moves through the buttons.
 * The drawn objects are 2.5D like the game window's cartridges (InspectSlab's way, done here for any yaw and pitch):
 * their face recorded once and drawn again, darker, slice after slice through their thickness in their own plastic — so
 * every side has its own outline — then the lit face in front, or from behind their own back (SaveObjectBack, the 3D
 * card's saveCardBack) with the SaveVault mark moulded into it. The PS2 icon is a real mesh, turned in 3D.
 * A strip of the colours the object takes (Original, the Voltcap memory cards' colours, your own) previews live; only
 * Apply keeps it (per save or per console, in SaveLookStore as Appearance does), Revert goes back to what's kept, Reset
 * to original clears it, closing without Apply keeps nothing. Looking around never opens a game, restores a save or
 * touches any save data. Rotation, zoom and pan are read only while drawing (no recomposition per frame), the pictures
 * and marks are cached, the controller's frame loop sleeps while nothing is held.)
 */

/** (Update 241, #49: what the viewer shows; [depth]: its thickness against its longest side.) */
internal enum class InspectObject(val label: String, val depth: Float) {
    MEMORY_CARD("Memory card", 0.10f),
    SD_CARD("SD card", 0.066f),
    FLOPPY("Floppy disk", 0.035f),
    CAPSULE("Capsule", 0.34f),
    CARD_3D("3D card", SAVE_CARD_DEPTH),
    PS2_ICON("PS2 save icon", 0f)
}

private fun InspectObject.style(): SaveVaultStyle = when (this) {
    InspectObject.MEMORY_CARD -> SaveVaultStyle.MEMORY_CARD
    InspectObject.SD_CARD -> SaveVaultStyle.SD_CARD
    InspectObject.FLOPPY -> SaveVaultStyle.FLOPPY
    InspectObject.CAPSULE -> SaveVaultStyle.CAPSULE
    InspectObject.CARD_3D, InspectObject.PS2_ICON -> SaveVaultStyle.CARD_3D
}

/**
 * (Update 241, #48/#49: the object a save is drawn as with the vault's [style] — the drawn memory card while the 3D card
 * isn't on the device, as StyledSaveCard does — or null for the Classic squares: flat icons stay flat.)
 */
internal fun inspectObjectFor(style: SaveVaultStyle): InspectObject? = when (style) {
    SaveVaultStyle.MEMORY_CARD -> InspectObject.MEMORY_CARD
    SaveVaultStyle.SD_CARD -> InspectObject.SD_CARD
    SaveVaultStyle.FLOPPY -> InspectObject.FLOPPY
    SaveVaultStyle.CAPSULE -> InspectObject.CAPSULE
    SaveVaultStyle.CARD_3D -> if (Models3D.saveCardId() != null) InspectObject.CARD_3D else InspectObject.MEMORY_CARD
    SaveVaultStyle.CLASSIC, SaveVaultStyle.ICONS -> null
}

/** (Update 241, #48: a PS2 save's memory-card saves when the vault draws it with its own icon — the PS2 browser style.) */
private fun ps2InspectRefs(game: Game, capsule: SaveCapsules.Capsule?): List<Ps2SaveRef> =
    if ((Features.saveVaultStyle.state.value == SaveVaultStyle.ICONS || Ps2BrowserStyle.on.value) && Ps2BrowserStyle.isPs2(game.platform)) ps2RefsOf(capsule) else emptyList()

/** (Update 241, #50: whether a save's details in Save Vault offer Inspect — a 3D object to look at.) */
internal fun saveInspectable(game: Game, capsule: SaveCapsules.Capsule?): Boolean =
    inspectObjectFor(Features.saveVaultStyle.state.value) != null || ps2InspectRefs(game, capsule).isNotEmpty()

// ---- The pose: turn, tilt, zoom, move — read while drawing ----

private const val DEAD = 0.22f
private const val ZOOM_MIN = 0.5f
private const val ZOOM_MAX = 3f

/** The object is laid out this much bigger than it first shows (and drawn smaller), so zooming in stays sharp. */
private const val RES = 1.4f

private class InspectPose {
    val yaw = mutableFloatStateOf(0f)
    val pitch = mutableFloatStateOf(0f)
    val zoom = mutableFloatStateOf(1f)
    val panX = mutableFloatStateOf(0f)
    val panY = mutableFloatStateOf(0f)
    // The controller, as it is now.
    val rx = mutableFloatStateOf(0f)
    val ry = mutableFloatStateOf(0f)
    val lx = mutableFloatStateOf(0f)
    val ly = mutableFloatStateOf(0f)
    val lt = mutableFloatStateOf(0f)
    val rt = mutableFloatStateOf(0f)
    val panMod = mutableStateOf(false)
    val auto = mutableStateOf(Features.saveInspectAutoRotate.value)
    /** How far it may be moved each way (px, at zoom 1), and the stick's speed (px a second) — from the stage's size. */
    var limitX = 0f
    var limitY = 0f
    var panSpeed = 600f
    private var resetJob: Job? = null
    /** Update 274 (Toni): when it was last touched / turned / zoomed (uptime ms), for the turn back after the hold. */
    @Volatile var touchedAt = android.os.SystemClock.uptimeMillis()
    /** Update 274: a finger is on it. */
    @Volatile var touching = false
    fun touch() { touchedAt = android.os.SystemClock.uptimeMillis() }

    /** How much its front faces you (−1: its back, full on). */
    fun facing(): Float =
        (cos(Math.toRadians(yaw.floatValue.toDouble())) * cos(Math.toRadians(pitch.floatValue.toDouble()))).toFloat()

    /** Upside down (tilted past upright), a turn to the right still turns what you see to the right. */
    private fun yawSign(): Float = if (cos(Math.toRadians(pitch.floatValue.toDouble())) < 0.0) -1f else 1f

    /** 0.6.18: the coast after a flick (°/s), slowing to a stop in [step]; 0 at rest. */
    val vYaw = mutableFloatStateOf(0f)
    val vPitch = mutableFloatStateOf(0f)

    /** 0.6.18 (Toni): let go of a drag with speed and it keeps turning, slower and slower, until it stops (gently). */
    fun fling(vy: Float, vp: Float) {
        val y = vy.coerceIn(-FLING_MAX, FLING_MAX); val p = vp.coerceIn(-FLING_MAX * 0.6f, FLING_MAX * 0.6f)
        if (abs(y) < FLING_MIN && abs(p) < FLING_MIN) return
        resetJob?.cancel()
        vYaw.floatValue = y; vPitch.floatValue = p
    }

    fun stopCoast() { vYaw.floatValue = 0f; vPitch.floatValue = 0f }

    private fun coasting(): Boolean = abs(vYaw.floatValue) >= FLING_STOP || abs(vPitch.floatValue) >= FLING_STOP
    fun coastingNow(): Boolean = coasting()

    fun turn(dYaw: Float, dPitch: Float, user: Boolean = true) {
        if (user) { resetJob?.cancel(); touch() }
        yaw.floatValue = Models3D.wrap(yaw.floatValue + dYaw * yawSign())
        pitch.floatValue = Models3D.wrap(pitch.floatValue + dPitch)
    }

    fun zoomBy(f: Float) {
        resetJob?.cancel(); touch()
        zoom.floatValue = (zoom.floatValue * f).coerceIn(ZOOM_MIN, ZOOM_MAX)
        clampPan()
    }

    fun panBy(dx: Float, dy: Float) {
        resetJob?.cancel(); touch()
        panX.floatValue += dx
        panY.floatValue += dy
        clampPan()
    }

    private fun clampPan() {
        val z = zoom.floatValue
        panX.floatValue = panX.floatValue.coerceIn(-limitX * z, limitX * z)
        panY.floatValue = panY.floatValue.coerceIn(-limitY * z, limitY * z)
    }

    fun held(): Boolean =
        abs(rx.floatValue) > DEAD || abs(ry.floatValue) > DEAD || lt.floatValue > DEAD || rt.floatValue > DEAD ||
            (panMod.value && (abs(lx.floatValue) > DEAD || abs(ly.floatValue) > DEAD)) || auto.value || coasting()

    /** One frame of the sticks, triggers and auto-turn, [dt] seconds. */
    fun step(dt: Float) {
        val r = rx.floatValue; val u = ry.floatValue
        val dy = if (abs(r) > DEAD) r * 170f * dt else 0f
        val dp = if (abs(u) > DEAD) u * 140f * dt else 0f
        if (dy != 0f || dp != 0f) { stopCoast(); turn(dy, dp) }
        else if (coasting()) {
            // (0.6.18: a flick coasting to a stop.)
            turn(vYaw.floatValue * dt, vPitch.floatValue * dt, user = false)
            val k = exp(-FLING_FRICTION * dt)
            vYaw.floatValue *= k; vPitch.floatValue *= k
            if (!coasting()) stopCoast()
        }
        else if (auto.value && resetJob?.isActive != true) turn(28f * dt, 0f, user = false)
        val zin = rt.floatValue - lt.floatValue
        if (abs(zin) > DEAD) zoomBy(exp(zin * 1.4f * dt))
        if (panMod.value) {
            val x = lx.floatValue; val y = ly.floatValue
            if (abs(x) > DEAD || abs(y) > DEAD) panBy(if (abs(x) > DEAD) x * panSpeed * dt else 0f, if (abs(y) > DEAD) y * panSpeed * dt else 0f)
        }
    }

    /** Y: back as it first stood — facing you, upright, its own size, in the middle — smoothly. */
    fun reset(scope: CoroutineScope) {
        resetJob?.cancel()
        stopCoast(); touch()
        resetJob = scope.launch {
            val y0 = Models3D.wrap(yaw.floatValue); val p0 = Models3D.wrap(pitch.floatValue)
            val z0 = zoom.floatValue; val x0 = panX.floatValue; val q0 = panY.floatValue
            animate(0f, 1f, animationSpec = tween(420, easing = FastOutSlowInEasing)) { v, _ ->
                val k = 1f - v
                yaw.floatValue = y0 * k; pitch.floatValue = p0 * k
                zoom.floatValue = 1f + (z0 - 1f) * k
                panX.floatValue = x0 * k; panY.floatValue = q0 * k
            }
        }
    }
}

// ---- The window ----

/**
 * (Update 241, #49–#52: the Free Inspect viewer for [game]'s save object — opened only from Save Vault's save details.
 * [capsule]: what the save holds (a PS2 save's own icon with the PS2 browser style). Closing returns to those details.)
 */
@Composable
internal fun SaveInspectDialog(game: Game, icon: String?, state: SyncState, capsule: SaveCapsules.Capsule?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val style = Features.saveVaultStyle.state.value
    val styleObj = inspectObjectFor(style)
    val refs = remember(capsule, Features.saveVaultStyle.state.value) { ps2InspectRefs(game, capsule) }
    val ps2 by produceState<Ps2Load>(Ps2Load(refs.isEmpty(), null), refs) {
        if (refs.isEmpty()) return@produceState
        val ic = withContext(Dispatchers.IO) {
            refs.take(8).firstNotNullOfOrNull { r -> runCatching { Ps2Icons.load(context, r.id) }.getOrNull()?.icon }
        }
        value = Ps2Load(true, ic)
    }
    val mesh = remember(ps2.icon) { ps2.icon?.let { runCatching { Ps2IconMesh(it) }.getOrNull() }?.takeIf { it.ok } }
    val obj: InspectObject? = if (mesh != null) InspectObject.PS2_ICON else if (!ps2.done) null else styleObj
    val name = gameDisplayName(game.name)
    val brand = remember(game.platform) { com.shelf.launcher.data.ConsoleNames.display(game.platform) }

    val pose = remember { InspectPose() }
    val scope = rememberCoroutineScope()
    val toggleAuto = {
        pose.auto.value = !pose.auto.value
        Features.saveInspectAutoRotate.value = pose.auto.value
    }

    // (#51: the colour being looked at — kept only with Apply, per save or per console like Appearance.)
    var forConsole by remember { mutableStateOf(SaveLookStore.forGame(game.stableKey) == null && SaveLookStore.forConsole(game.platform) != null) }
    fun saved(): SaveLook? = if (forConsole) SaveLookStore.forConsole(game.platform) else SaveLookStore.forGame(game.stableKey)
    var preview by remember { mutableStateOf(saved()) }
    var custom by remember { mutableStateOf(false) }
    val savedNow = saved()
    val dirty = preview != savedNow
    // A save without a colour of its own shows its console's.
    val shown = preview ?: if (!forConsole) SaveLookStore.forConsole(game.platform) else null
    val card = if (obj == InspectObject.CARD_3D) Models3D.saveCardIdFor(shown) else null
    val paintable = obj != null && obj != InspectObject.PS2_ICON && (card == null || Models3D.meta(card)?.tint?.own != null)
    val hasTrim = obj == InspectObject.MEMORY_CARD || obj == InspectObject.SD_CARD || obj == InspectObject.FLOPPY || obj == InspectObject.CARD_3D
    val presets = remember(com.shelf.launcher.data.Packs.version.intValue) { saveLookPresets() }
    fun store(l: SaveLook?) {
        val v = l?.takeUnless { it.isEmpty }
        if (forConsole) SaveLookStore.setConsole(game.platform, v) else SaveLookStore.setGame(game.stableKey, v)
    }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(paintable) { kotlinx.coroutines.delay(120); runCatching { firstFocus.requestFocus() } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ImmersiveDialogWindow()
        InspectControllerInput(pose, onReset = { pose.reset(scope) }, onToggleAuto = toggleAuto, onClose = onDismiss)
        BoxWithConstraints(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF121520), Color(0xFF05060B))))
        ) {
            val wide = maxWidth > maxHeight * 1.15f
            val stage: @Composable (Modifier) -> Unit = { m ->
                Box(m) {
                    if (obj != null) InspectStage(obj, pose, game, icon, state, shown, mesh, Modifier.fillMaxSize())
                    else Text(
                        if (!ps2.done) "Reading the save's icon…" else "The Classic squares are flat save icons: there's no 3D object to inspect.",
                        color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.align(Alignment.Center).padding(24.dp)
                    )
                    Text(
                        "Drag: turn · two fingers: move, pinch: zoom · right stick: turn · L1 + left stick: move · L2 / R2: zoom · Y: reset · X: auto-turn · B: close",
                        color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, lineHeight = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
            val panel: @Composable (Modifier) -> Unit = { m ->
                Column(m.background(Color.Black.copy(alpha = 0.35f)).verticalScroll(rememberScrollState()).padding(14.dp)) {
                    Text("Inspect", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(
                        (obj?.label?.let { "$it · " } ?: "") + name, color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(10.dp))
                    if (obj != null && paintable) {
                        Text("Colour", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        ChipFlow {
                            Chip("This save", accent = !forConsole) { forConsole = false; preview = saved(); custom = false }
                            Chip("Every $brand save", accent = forConsole) { forConsole = true; preview = saved(); custom = false }
                        }
                        Spacer(Modifier.height(8.dp))
                        // The strip: Original, the Voltcap memory cards' colours, your own.
                        // (Update 294: whole swatches only, "+N ›" for the rest; it follows the D-pad's focus — WholeChipRow.)
                        WholeChipRow(presets.size + 2, -1, Modifier.fillMaxWidth(), spacing = 8.dp) { si ->
                            when {
                                si == 0 -> Box(Modifier.focusRequester(firstFocus)) {
                                    SwatchChip("Original", originalShell(obj.style()), selected = preview?.shell == null && !custom) { custom = false; preview = null }
                                }
                                si <= presets.size -> {
                                    val p = presets[si - 1]
                                    SwatchChip(
                                        p.label.substringAfter(" · "), p.shell,
                                        selected = !custom && preview?.shell == p.shell && (preview?.preset == null || preview?.preset == p.id)
                                    ) { custom = false; preview = SaveLook(p.shell, if (hasTrim) p.badge else null, p.id, preview?.clear) }
                                }
                                else -> SwatchChip("Custom…", preview?.shell?.takeIf { preview?.preset == null }, selected = custom) { custom = !custom }
                            }
                        }
                        if (custom) {
                            Spacer(Modifier.height(8.dp))
                            val c = preview?.shell ?: originalShell(obj.style())
                            HueSlider(c) { h -> preview = SaveLook(h, preview?.trim, null, preview?.clear) }
                            Spacer(Modifier.height(6.dp))
                            ColorFineTune("Shell", c) { v -> preview = SaveLook(v, preview?.trim, null, preview?.clear) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (dirty) "Previewing — not kept until you Apply." else "This is the colour that's kept.",
                            color = if (dirty) ShelfTheme.accent else Color.White.copy(alpha = 0.55f), fontSize = 11.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        ChipFlow {
                            Chip("Apply", accent = true, enabled = dirty) { store(preview); custom = false }
                            Chip("Revert", accent = false, enabled = dirty) { preview = savedNow; custom = false }
                            Chip("Reset to original", accent = false, enabled = preview != null) { preview = null; custom = false }
                        }
                        if (!forConsole && preview == null && SaveLookStore.forConsole(game.platform) != null) {
                            Spacer(Modifier.height(4.dp))
                            MutedNote("Without a colour of its own this save shows its console's.")
                        }
                        Spacer(Modifier.height(4.dp))
                        MutedNote("Only how it's drawn: the save itself is never touched.")
                    } else if (obj != null) {
                        MutedNote(
                            if (obj == InspectObject.PS2_ICON) "This is the save's own icon from its memory card: its colours are the game's."
                            else "This 3D card can't be recoloured. Pick one of the Voltcap memory cards (Filters & look › Style › 3D card) or a drawn style."
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("View", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    ChipFlow {
                        Box(if (paintable) Modifier else Modifier.focusRequester(firstFocus)) {
                            Chip("Reset view (Y)", accent = false) { pose.reset(scope) }
                        }
                        Chip(if (pose.auto.value) "✓ Auto-turn (X)" else "Auto-turn (X)", accent = pose.auto.value) { toggleAuto() }
                        Chip("Close (B)", accent = false, onClick = onDismiss)
                    }
                }
            }
            val boxW = maxWidth; val boxH = maxHeight
            if (wide) Row(Modifier.fillMaxSize()) {
                stage(Modifier.weight(1f).fillMaxHeight())
                panel(Modifier.width((boxW * 0.36f).coerceIn(260.dp, 400.dp)).fillMaxHeight())
            } else Column(Modifier.fillMaxSize()) {
                stage(Modifier.weight(1f).fillMaxWidth())
                panel(Modifier.fillMaxWidth().heightIn(max = boxH * 0.45f))
            }
        }
    }
}

/** (Update 241, #48: a PS2 save's icon being read: [done], and the icon when it had one.) */
private class Ps2Load(val done: Boolean, val icon: Ps2Icons.Icon?)

/**
 * (Update 241, #52: the controller in the viewer — taken where the viewer's own window gets it, before any of its parts,
 * as the game window's inspect view does (InspectStick): the right stick, L1 + left stick, the triggers, Y, X, B. The
 * left stick alone and the D-pad still move through the buttons. The frame loop runs only while something is held.)
 */
@Composable
private fun InspectControllerInput(pose: InspectPose, onReset: () -> Unit, onToggleAuto: () -> Unit, onClose: () -> Unit) {
    val view = LocalView.current
    val reset by rememberUpdatedState(onReset)
    val toggle by rememberUpdatedState(onToggleAuto)
    val close by rememberUpdatedState(onClose)
    DisposableEffect(view, pose) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val original = window?.callback
        if (window == null || original == null) return@DisposableEffect onDispose { }
        val wrapped = object : android.view.Window.Callback by original {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                val down = event.action == KeyEvent.ACTION_DOWN
                val first = down && event.repeatCount == 0
                when (event.keyCode) {
                    KeyEvent.KEYCODE_BUTTON_Y -> { if (first) reset(); return true }
                    KeyEvent.KEYCODE_BUTTON_X -> { if (first) toggle(); return true }
                    KeyEvent.KEYCODE_BUTTON_L1 -> { pose.panMod.value = down; return true }
                    KeyEvent.KEYCODE_BUTTON_L2 -> { pose.lt.floatValue = if (down) 1f else 0f; return true }
                    KeyEvent.KEYCODE_BUTTON_R2 -> { pose.rt.floatValue = if (down) 1f else 0f; return true }
                    KeyEvent.KEYCODE_BUTTON_B -> { if (event.action == KeyEvent.ACTION_UP) close(); return true }
                }
                return original.dispatchKeyEvent(event)
            }

            override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
                if (event.isFromSource(InputDevice.SOURCE_JOYSTICK) && event.action == MotionEvent.ACTION_MOVE) {
                    pose.rx.floatValue = event.getAxisValue(MotionEvent.AXIS_Z)
                    pose.ry.floatValue = event.getAxisValue(MotionEvent.AXIS_RZ)
                    pose.lx.floatValue = event.getAxisValue(MotionEvent.AXIS_X)
                    pose.ly.floatValue = event.getAxisValue(MotionEvent.AXIS_Y)
                    pose.lt.floatValue = maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE))
                    pose.rt.floatValue = maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS))
                    // With L1 held the left stick moves the object, not the focus.
                    if (pose.panMod.value) return true
                }
                return original.dispatchGenericMotionEvent(event)
            }
        }
        window.callback = wrapped
        onDispose { if (window.callback === wrapped) window.callback = original }
    }
    LaunchedEffect(pose) {
        while (true) {
            snapshotFlow { pose.held() }.first { it }
            var last = -1L
            while (pose.held()) {
                val now = withFrameNanos { it }
                if (last > 0L) pose.step(((now - last) / 1_000_000_000f).coerceAtMost(0.05f))
                last = now
            }
        }
    }
    // Update 274 (Toni): as in the game window — left alone for the hold (Settings › Game look: 2 s by default) it turns
    // back to face you slowly (its own size, in the middle), then sways gently left and right until it's touched again.
    LaunchedEffect(pose) { pose.idleLoop() }
}

/**
 * Update 274: the turn back after the hold and the sway after it (Features.inspectAutoReturn / inspectHold /
 * inspectReturnSpeed / inspectSway — the game window's settings). Sleeps while there's nothing to do.
 */
private suspend fun InspectPose.idleLoop() {
    var swayT = 0f
    var amp = 0f
    var back: FloatArray? = null   // yaw, pitch, zoom, panX, panY as the turn back started
    var backT = 0f
    var swayBase = 0f
    var last = -1L
    while (true) {
        val busy = touching || held()
        val idle = android.os.SystemClock.uptimeMillis() - touchedAt
        val hold = Features.inspectHold.state.value.ms
        val returnOn = Features.inspectAutoReturn.state.value
        val swayOn = Features.inspectSway.state.value
        if (busy) {
            if (busy && (touching || coastingNow() || abs(rx.floatValue) > DEAD || abs(ry.floatValue) > DEAD)) touch()
            back = null; amp = 0f; swayT = 0f; last = -1L
            kotlinx.coroutines.delay(120)
            continue
        }
        if (idle < hold) { back = null; amp = 0f; swayT = 0f; last = -1L; kotlinx.coroutines.delay(100); continue }
        val atRest = abs(Models3D.wrap(yaw.floatValue)) < 0.5f && abs(Models3D.wrap(pitch.floatValue)) < 0.5f &&
            abs(zoom.floatValue - 1f) < 0.01f && abs(panX.floatValue) < 0.5f && abs(panY.floatValue) < 0.5f
        val wantBack = returnOn && back == null && amp == 0f && swayT == 0f && !atRest
        if (!wantBack && back == null && !swayOn) { kotlinx.coroutines.delay(200); continue }
        val now = withFrameNanos { it }
        val dt = if (last > 0L) ((now - last) / 1_000_000_000f).coerceAtMost(0.05f) else 0f
        last = now
        if (wantBack) { back = floatArrayOf(Models3D.wrap(yaw.floatValue), Models3D.wrap(pitch.floatValue), zoom.floatValue, panX.floatValue, panY.floatValue); backT = 0f }
        val b = back
        if (b != null) {
            backT += dt * 1000f / Features.inspectReturnSpeed.state.value.ms
            val v = FastOutSlowInEasing.transform(backT.coerceIn(0f, 1f))
            val k = 1f - v
            yaw.floatValue = b[0] * k; pitch.floatValue = b[1] * k
            zoom.floatValue = 1f + (b[2] - 1f) * k
            panX.floatValue = b[3] * k; panY.floatValue = b[4] * k
            if (backT >= 1f) { back = null; swayBase = 0f; swayT = 0.0001f }
            continue
        }
        if (!swayOn) { kotlinx.coroutines.delay(200); continue }
        // The sway (around where it stands — facing you after the turn back, else where you left it).
        if (swayT == 0f) swayBase = yaw.floatValue
        amp += (1f - amp) * (dt * 1.6f).coerceAtMost(1f)
        swayT += dt
        yaw.floatValue = swayBase + 24f * amp * sin(swayT * 1000f / CART_SPIN_MS * 2f * PI.toFloat())
    }
}

private val NO_BOB: () -> Float = { 0f }
private val NO_PLACE: (androidx.compose.ui.geometry.Rect) -> Unit = { }

/** (Update 241, #49/#52: the object on its stage — touch, mouse and pinch, the pose read while drawing.) */
@Composable
private fun InspectStage(
    obj: InspectObject, pose: InspectPose, game: Game, icon: String?, state: SyncState, look: SaveLook?, mesh: Ps2IconMesh?, modifier: Modifier
) {
    BoxWithConstraints(
        modifier
            .clipToBounds()
            .pointerInput(pose) {
                awaitEachGesture {
                    val down0 = awaitFirstDown(requireUnconsumed = false)
                    // (0.6.18: a touch stops a coasting turn; a one-finger flick starts one as you let go.)
                    pose.stopCoast()
                    pose.touching = true; pose.touch() // (Update 274: not turned back while a finger is on it)
                    val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                    tracker.addPosition(down0.uptimeMillis, down0.position)
                    var pinched = false
                    val k0 = 200f / size.width.coerceAtLeast(1)
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        event.changes.firstOrNull()?.let { tracker.addPosition(it.uptimeMillis, it.position) }
                        if (pressed >= 2) pinched = true
                        if (pressed >= 2) {
                            val z = event.calculateZoom()
                            val p = event.calculatePan()
                            if (z != 1f) pose.zoomBy(z)
                            if (p != Offset.Zero) pose.panBy(p.x, p.y)
                        } else if (pressed == 1) {
                            val ch = event.changes.firstOrNull { it.pressed }
                            if (ch != null) {
                                val d = ch.positionChange()
                                val k = 200f / size.width.coerceAtLeast(1)
                                if (d != Offset.Zero) pose.turn(d.x * k, d.y * k)
                            }
                        }
                        event.changes.forEach { if (it.positionChange() != Offset.Zero) it.consume() }
                    } while (event.changes.any { it.pressed })
                    pose.touching = false; pose.touch()
                    if (!pinched) tracker.calculateVelocity().let { v -> pose.fling(v.x * k0, v.y * k0) }
                }
            }
            .pointerInput(pose) {
                // The mouse wheel zooms.
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        if (e.type == PointerEventType.Scroll) {
                            val dy = e.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                            if (dy != 0f) { pose.zoomBy(if (dy > 0f) 0.9f else 1.1f); e.changes.forEach { it.consume() } }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        SideEffect {
            pose.limitX = wPx / 2f; pose.limitY = hPx / 2f
            pose.panSpeed = wPx * 0.6f
        }
        // A soft pool of light behind it.
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.08f), Color.Transparent))))
        if (obj == InspectObject.PS2_ICON) {
            if (mesh != null) {
                val buffer = remember { SoftBuffer() }
                Canvas(Modifier.fillMaxSize()) {
                    val z = pose.zoom.floatValue
                    withTransform({
                        translate(pose.panX.floatValue, pose.panY.floatValue)
                        scale(z, z, center)
                    }) { drawPs2Mesh(mesh, buffer, pose.yaw.floatValue, pose.pitch.floatValue) }
                }
            }
            return@BoxWithConstraints
        }
        val aspect = obj.style().aspect()
        /** How much of its node's width the object itself fills (the drawn pieces stand at 86 %, the capsule at 82 %). */
        val frac = when (obj) { InspectObject.CAPSULE -> 0.82f; InspectObject.CARD_3D -> 1f; else -> 0.86f }
        // Fitted with room to turn and tilt.
        val objW = minOf(maxWidth * 0.68f, maxHeight * 0.68f * aspect)
        val nodeW = objW / frac
        val nodeH = nodeW / aspect
        val back by remember(pose) { derivedStateOf { pose.facing() < 0f } }
        val card = if (obj == InspectObject.CARD_3D) Models3D.saveCardIdFor(look) else null
        val meta = remember(card, com.shelf.launcher.data.Packs.version.intValue) { card?.let { Models3D.meta(it) } }
        // The sides' plastic (and how see-through it is).
        val tone = when (obj) {
            InspectObject.MEMORY_CARD -> memCardTones(look).body.last()
            InspectObject.SD_CARD -> sdBody(look).first()
            InspectObject.FLOPPY -> floppyBody(look).first()
            InspectObject.CAPSULE -> capsuleGlass(look)
            else -> Color(look?.shell ?: meta?.tint?.own?.first ?: 0xFF9EA2A8.toInt())
        }
        val clear = when (obj) {
            InspectObject.CAPSULE -> 0.35f
            InspectObject.CARD_3D -> if (meta?.tint?.clear == true) 0.55f else null
            else -> null
        }
        Box(
            Modifier
                .requiredSize(nodeW * RES, nodeH * RES)
                .graphicsLayer {
                    val s = pose.zoom.floatValue / RES
                    scaleX = s; scaleY = s
                    translationX = pose.panX.floatValue
                    translationY = pose.panY.floatValue
                }
                .inspectSolid(pose, if (card != null) saveCardDepth(card) else obj.depth, frac, aspect, tone, clear,
                    slot = obj == InspectObject.MEMORY_CARD || (obj == InspectObject.CARD_3D && card?.startsWith("memory_card") == true),
                    pill = if (obj == InspectObject.CAPSULE) 0.62f else null),
            contentAlignment = Alignment.Center
        ) {
            InspectFace(obj, back, game, icon, state, look)
        }
    }
}

/** (Update 241, #49: the face toward you — the object's front as the vault draws it, or its back.) */
@Composable
private fun InspectFace(obj: InspectObject, back: Boolean, game: Game, icon: String?, state: SyncState, look: SaveLook?) {
    val name = gameDisplayName(game.name)
    when (obj) {
        InspectObject.CARD_3D -> {
            val card = Models3D.saveCardIdFor(look) ?: return
            // Its back made while its front shows, so turning it round never shows an empty back first.
            val version = com.shelf.launcher.data.Packs.version.intValue
            val tint = Models3D.tintFor(card, game.platform, name, look?.shell, look?.trim)
            val engrave = Features.saveEngraving.state.value
            val bmp by produceState<android.graphics.Bitmap?>(null, card, version, tint?.key, engrave, game.platform) {
                value = withContext(Dispatchers.Default) { saveCardBack(card, tint, engrave, vaultConsole(game.platform)) }
            }
            if (!back) {
                Model3DArt(card, icon, 0f, Modifier.fillMaxSize(), name = name, alwaysName = true, platform = game.platform, shellOverride = look?.shell, badgeOverride = look?.trim)
            } else {
                val img = remember(bmp) { bmp?.asImageBitmap() }
                if (img != null) Image(img, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        }
        InspectObject.CAPSULE -> {
            if (!back) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CapsuleSaveCard(icon, name, state, false, 0f, NO_BOB, false, NO_PLACE, look)
            } else SaveObjectBack(SaveVaultStyle.CAPSULE, look, icon, state, Modifier.fillMaxSize(), platform = vaultConsole(game.platform))
        }
        InspectObject.PS2_ICON -> Unit
        else -> {
            if (!back) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                StyledSaveCard(obj.style(), icon, name, state, false, 0f, NO_BOB, false, NO_PLACE, platform = game.platform, look = look)
            } else SaveObjectBack(obj.style(), look, icon, state, Modifier.fillMaxSize(), platform = vaultConsole(game.platform))
        }
    }
}

// ---- (Update 241, #49/#52: the solid piece, turned and tilted) ----

/** The face's outline made solid (its alpha ×4, the faintest edge pixels dropped), for its sides. */
private val SOLIDIFY = ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
    1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 4f, -24f
)))

/** A brightness filter in 20 steps (made once each). */
private object InspectLight {
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
 * A face (laid out round [px], [py]) turned [cy]/[sy] round its upright, then tilted [cp]/[sp] round the screen's level,
 * at depth [z] — seen straight on, as the game window's cartridges are (its PiecePose): a point (x, y) lands at
 * (x·cos yaw + z·sin yaw, y·cos pitch − x·sin yaw·sin pitch + z·cos yaw·sin pitch). Never quite 0 wide / tall, so a face
 * seen edge-on keeps its slices.
 */
private fun setPose(m: Matrix, cy: Float, sy: Float, cp: Float, sp: Float, px: Float, py: Float, z: Float) {
    val a = if (abs(cy) < 0.01f) (if (cy < 0f) -0.01f else 0.01f) else cy
    val dd = if (abs(cp) < 0.01f) (if (cp < 0f) -0.01f else 0.01f) else cp
    val c = -sy * sp
    m.reset()
    val v = m.values
    v[0] = a; v[1] = c; v[4] = 0f; v[5] = dd
    v[12] = px - a * px + z * sy
    v[13] = py - c * px - dd * py + z * cy * sp
}

/**
 * This node's content as a solid piece [depth] thick (against its longest side; the piece fills [frac] of the node's
 * width at [aspect]), turned and tilted by [pose]: its face recorded once, its sides that face's outline in [tone]
 * slice after slice, darker as they turn from the light, then the lit face — or, seen from behind, the content (its
 * back, drawn mirrored) at the far side. [clear]: a see-through plastic (each slice faint, none of them behind its face).
 * The layers and the drawing blocks are made once per size; each frame only re-records them.
 */
private fun Modifier.inspectSolid(
    pose: InspectPose, depth: Float, frac: Float, aspect: Float, tone: Color, clear: Float?,
    /** Update 285 (Toni: "a memory card's opening at the bottom"): its connector slot cut along the bottom edge. */
    slot: Boolean = false,
    /**
     * Update 325 (Toni: "inspecting the capsule from the side shows weird graphics"): the piece's own outline, a pill this
     * wide against its height, centred — for a face that is mostly clear glass (the capsule), whose sides made from its
     * opaque parts were only its icon and badge pushed out. Null: the sides follow the face's outline.
     */
    pill: Float? = null
): Modifier =
    this.drawWithCache {
        val face = obtainGraphicsLayer()
        val solid = obtainGraphicsLayer()
        solid.colorFilter = SOLIDIFY
        val edge = obtainGraphicsLayer()
        edge.compositingStrategy = CompositingStrategy.Offscreen
        val lit = obtainGraphicsLayer()
        val hole = obtainGraphicsLayer()
        hole.blendMode = BlendMode.DstOut
        // (Only a see-through piece needs one buffer of its own: its hole must clear only its own sides.)
        val whole = if (clear != null) obtainGraphicsLayer().also { it.compositingStrategy = CompositingStrategy.Offscreen } else null
        val sheen = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.Transparent, Color.Black.copy(alpha = 0.18f)))
        val side = tone.copy(alpha = 1f)
        val mx = Matrix()
        val ow = size.width * frac
        val depthPx = depth * max(ow, ow / aspect.coerceAtLeast(0.05f))
        val px = size.width / 2f
        val py = size.height / 2f
        val margin = (0.2f * max(size.width, size.height) + depthPx).roundToInt()
        val wholeSize = IntSize(size.width.roundToInt() + 2 * margin, size.height.roundToInt() + 2 * margin)
        // This frame's pose (set before each drawing).
        var content: ContentDrawScope? = null
        var cyv = 1f; var syv = 0f; var cpv = 1f; var spv = 0f; var facing = 1f; var slices = 0
        val recordFace: DrawScope.() -> Unit = { content?.drawContent() }
        val recordFromFace: DrawScope.() -> Unit = { drawLayer(face) }
        val recordHole: DrawScope.() -> Unit = { drawLayer(solid) }
        val pillW = size.width * frac
        val pillH = if (pill != null) pillW / pill.coerceAtLeast(0.05f) else 0f
        val recordPill: DrawScope.() -> Unit = {
            drawRoundRect(Color.White, Offset((size.width - pillW) / 2f, (size.height - pillH) / 2f), Size(pillW, pillH), CornerRadius(pillW / 2f))
        }
        val recordEdge: DrawScope.() -> Unit = {
            drawLayer(solid)
            drawRect(side, blendMode = BlendMode.SrcIn)
            drawRect(sheen, blendMode = BlendMode.SrcAtop)
        }
        // (Update 285: the inner slices carry the connector slot — a dark opening with its contacts' glint — along the
        // middle of the bottom edge, so tilted to show its underside the card has its opening there; the outer slices,
        // its front and back walls, stay whole.)
        val slotted = if (slot) obtainGraphicsLayer().also { it.compositingStrategy = CompositingStrategy.Offscreen } else null
        // The slot follows the card's own outline: its lowest [slotH] px (the outline less the outline moved up by that
        // much), across the middle 60 % — wherever the picture's card ends, whatever margin the picture has under it.
        val oh = ow / aspect.coerceAtLeast(0.05f)
        val slotH = (oh * 0.045f).coerceAtLeast(2f)
        val shiftOut = if (slot) obtainGraphicsLayer().also { it.blendMode = BlendMode.DstOut } else null
        val keepIn = if (slot) obtainGraphicsLayer().also { it.blendMode = BlendMode.DstIn } else null
        val strip = if (slot) obtainGraphicsLayer().also { it.compositingStrategy = CompositingStrategy.Offscreen } else null
        val recordShift: DrawScope.() -> Unit = { translate(0f, -slotH) { drawLayer(solid) } }
        val recordKeep: DrawScope.() -> Unit = { drawLayer(solid) }
        val recordStrip: DrawScope.() -> Unit = {
            clipRect(size.width / 2f - ow * 0.30f, 0f, size.width / 2f + ow * 0.30f, size.height) {
                drawRect(Color(0xFF0E0F12))
            }
            shiftOut?.let { drawLayer(it) }
            keepIn?.let { drawLayer(it) }
        }
        val recordSlotted: DrawScope.() -> Unit = {
            drawLayer(edge)
            strip?.let { drawLayer(it) }
        }
        val drawPiece: DrawScope.() -> Unit = {
            // Far to near: the slices, then the face (or, from behind, the back at the far side).
            val n = slices
            if (n > 0) for (k in 0..n) {
                val f = k.toFloat() / n
                val z = if (facing >= 0f) -depthPx / 2f + depthPx * f else depthPx / 2f - depthPx * f
                setPose(mx, cyv, syv, cpv, spv, px, py, z)
                val inner = slotted != null && n >= 6 && k >= n / 5 && k <= n - n / 5
                withTransform({ transform(mx) }) { drawLayer(if (inner) slotted!! else edge) }
            }
            val zf = if (facing >= 0f) depthPx / 2f else -depthPx / 2f
            setPose(mx, cyv, syv, cpv, spv, px, py, zf)
            withTransform({ transform(mx) }) {
                if (clear != null) drawLayer(hole)
                drawLayer(lit)
            }
        }
        val drawWhole: DrawScope.() -> Unit = { translate(margin.toFloat(), margin.toFloat()) { drawPiece() } }
        onDrawWithContent {
            content = this
            val ty = Math.toRadians(pose.yaw.floatValue.toDouble())
            val tp = Math.toRadians(pose.pitch.floatValue.toDouble())
            cyv = cos(ty).toFloat(); syv = sin(ty).toFloat(); cpv = cos(tp).toFloat(); spv = sin(tp).toFloat()
            facing = cyv * cpv
            val spread = sqrt((1f - facing * facing).coerceAtLeast(0f))
            slices = if (depthPx < 0.5f) 0 else ceil(depthPx * spread / 1.5f).toInt().coerceIn(0, 40)
            face.record(block = recordFace)
            if (pill != null) { solid.colorFilter = null; solid.record(block = recordPill) } else solid.record(block = recordFromFace)
            edge.colorFilter = InspectLight.of(0.42f + 0.22f * spread)
            edge.alpha = if (clear == null || slices <= 0) 1f
                else (1.0 - Math.pow(1.0 - clear.coerceIn(0.05f, 0.95f).toDouble(), 1.0 / (slices + 1))).toFloat()
            edge.record(block = recordEdge)
            if (slotted != null) {
                shiftOut?.record(block = recordShift)
                keepIn?.record(block = recordKeep)
                strip?.record(block = recordStrip)
                slotted.record(block = recordSlotted)
            }
            lit.colorFilter = InspectLight.of(1f - 0.35f * spread)
            lit.record(block = recordFromFace)
            if (whole != null) {
                hole.record(block = recordHole)
                whole.record(size = wholeSize, block = drawWhole)
                translate(-margin.toFloat(), -margin.toFloat()) { drawLayer(whole) }
            } else drawPiece()
            content = null
        }
    }


/** 0.6.18: the console whose logo a save's back carries — none for "Other saves". */
// (Update 285, Toni: "remove the console label from the back, please": no save's back carries its console's logo any
// more — the vault's mark (and the engraving, if on) only.)
@Suppress("UNUSED_PARAMETER")
internal fun vaultConsole(platform: String?): String? = null
