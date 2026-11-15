package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * 529s (owner P3, physical pickups — Storage boxes first): the sprite you picked leaves its slot in the box and hops onto the
 * record's stand; closing, it hops back into the same slot. One sprite all the way: the box's copy is hidden from the first
 * frame of the trip until it has landed back (GameWindow's spriteFlight → MediaInspect, [caseHomeHidden]), and the stand's
 * copy is hidden until the flying one has arrived ([storageSlotHidden]). The record itself does not fly: it switches on
 * round the sprite (fades in, its frame settling vertically), as a PC box's screen does.
 *
 * The flying copy is drawn over the record, outside its layer, so the record's fade never fades the sprite. Its target is
 * the live sheet's sprite place, measured inside the record's layer (so the record's own open motion doesn't move it).
 */
internal class StorageFlightSlot {
    /** The record's content coordinates (inside its layer). */
    var panel: LayoutCoordinates? = null
    /** The live sheet's sprite place, relative to [panel] (layout space, untransformed). */
    var slot by mutableStateOf<Rect?>(null)
    /** Where [panel]'s layout origin sits in the flyer's parent (px, untransformed): the record is at its root's corner, the
     *  Cube menu's face one cube depth down. */
    var panelAt: Offset = Offset.Zero
    /** Extra lift the sheet draws its sprite at when settled (the stand's -6 dp), so the hand-over doesn't jump. */
    var restLiftPx by mutableStateOf(0f)
}

internal val LocalStorageFlight = staticCompositionLocalOf<StorageFlightSlot?> { null }

/** The stand / header copy is hidden while the flying one is on its way (reveal below 1 with a flight). Pure. */
internal fun storageSlotHidden(flying: Boolean, reveal: Float): Boolean = flying && reveal < 0.999f

/**
 * The flying sprite's transform at reveal [k] from the box slot [from] (window) to the stand [to] (window): it rises out
 * of the box, travels on an arc and settles. Returns (dx, dy, scale) relative to [to]. Pure.
 */
internal fun storageSpriteFlight(from: Rect, to: Rect, k: Float): Triple<Float, Float, Float> {
    val t = k.coerceIn(0f, 1f)
    val s0 = (from.width / to.width).coerceIn(0.05f, 3f)
    val s = s0 + (1f - s0) * t
    val arc = kotlin.math.sin(t * Math.PI).toFloat()
    val lift = arc * maxOf(to.height * 0.22f, kotlin.math.abs(from.center.x - to.center.x) * 0.12f)
    return Triple((from.center.x - to.center.x) * (1f - t), (from.center.y - to.center.y) * (1f - t) - lift, s)
}

/** Marks the live sheet's sprite place for the flight (call before the sprite's own graphicsLayer). */
@Composable
internal fun Modifier.storageFlightSlot(live: Boolean, restLift: androidx.compose.ui.unit.Dp = 0.dp): Modifier {
    val holder = LocalStorageFlight.current ?: return this
    if (!live) return this
    val lift = with(LocalDensity.current) { restLift.toPx() }
    return this.onGloballyPositioned { c ->
        val p = holder.panel ?: return@onGloballyPositioned
        if (!p.isAttached || !c.isAttached) return@onGloballyPositioned
        val o = p.localPositionOf(c, Offset.Zero) + holder.panelAt
        val r = Rect(o.x, o.y, o.x + c.size.width, o.y + c.size.height)
        if (holder.slot != r) holder.slot = r
        if (holder.restLiftPx != lift) holder.restLiftPx = lift
    }
}

/**
 * 529s: where the picked object is going in the window and what it looks like there, registered by the scene; the trip
 * itself is drawn by the window ([PickupOverlay]) from its very first frame, at the home's place, so the home's copy can be
 * hidden in that same frame (KONKR frames pk529s11: drawn by the scene it came a frame or two late, after the window's
 * first heavy frame, and the home's copy and the flying one showed together).
 */
internal object PickupStage {
    /** The object's place in the window (window coordinates, as settled), or null until the scene has measured it. */
    var target by mutableStateOf<Rect?>(null)
    var content by mutableStateOf<(@Composable () -> Unit)?>(null)
    var uniform = true
    /** 529t: the object turns over on its way (Record crate's sleeve: its front leaves the crate, its back is the window). */
    var flip = false
    /** The window has laid out the flying copy (at home's place): the home's copy can go now. */
    var drawn by mutableStateOf(false)
    fun clear() { target = null; content = null; uniform = true; flip = false; drawn = false }
}

/** Registers the scene's place for the object (its sheet's slot, from [holder], in window coordinates) and its look there. */
@Composable
internal fun PickupTarget(holder: StorageFlightSlot, rootInWindow: Offset?, uniform: Boolean, content: @Composable () -> Unit) {
    val slot = holder.slot
    val root = rootInWindow
    androidx.compose.runtime.SideEffect {
        PickupStage.uniform = uniform
        if (PickupStage.content !== content) PickupStage.content = content
        val r = if (slot != null && root != null) Rect(root.x + slot.left, root.y + slot.top - holder.restLiftPx, root.x + slot.right, root.y + slot.bottom - holder.restLiftPx) else null
        if (r != null && PickupStage.target != r) PickupStage.target = r
    }
}

/**
 * The picked object on its trip between its home place [origin] and [PickupStage.target] (both window coordinates), at
 * reveal [reveal]: until the scene's place is known it waits at [origin]. It starts as the home's own picture ([snap]) and
 * becomes the window's ([PickupStage.content]) on the way; back, the other way round. Not drawn once it has arrived (the
 * place's own copy shows - [storageSlotHidden]). [rootInWindow]: where this overlay's parent sits in the window.
 */
@Composable
internal fun PickupOverlay(origin: Rect, snap: androidx.compose.ui.graphics.layer.GraphicsLayer?, rootInWindow: () -> Offset, reveal: () -> Float) {
    val dens = LocalDensity.current
    // (529t: an object that turns over keeps its own shape on the way - a binder card is upright, the window it turns into
    // is wide: it arrives as itself, centred on that place, and the window's side turns in from edge-on.)
    val to = PickupStage.target?.let { if (PickupStage.flip) fitShape(it, origin) else it }
    val box = to ?: origin
    val content = PickupStage.content
    Box(
        Modifier.offset { val r = rootInWindow(); IntOffset((box.left - r.x).toInt(), (box.top - r.y).toInt()) }
            .size(with(dens) { box.width.toDp() }, with(dens) { box.height.toDp() })
            .onGloballyPositioned { if (!PickupStage.drawn) PickupStage.drawn = true }
            .graphicsLayer {
                val k = if (to == null) 0f else reveal()
                if (QaTrace.on) QaTrace.event("pickupfly", "k=$k to=${to != null}")
                val flip = PickupStage.flip
                if (if (flip) k >= FLIP_EDGE else !storageSlotHidden(true, k)) { alpha = 0f; return@graphicsLayer }
                if (to == null) return@graphicsLayer
                // (Turning over: it is at its place, edge-on, at FLIP_EDGE - the window's side turns in from there.)
                if (flip) { rotationY = 90f * flipTurn(k); cameraDistance = 14f * density; flatTextFor3d() }
                val (dx, dy, s) = storageSpriteFlight(origin, to, if (flip) (k / FLIP_EDGE).coerceIn(0f, 1f) else k)
                translationX = dx; translationY = dy
                if (PickupStage.uniform) { scaleX = s; scaleY = s }
                else {
                    val t = k.coerceIn(0f, 1f)
                    val sx0 = (origin.width / to.width).coerceIn(0.05f, 3f); val sy0 = (origin.height / to.height).coerceIn(0.05f, 3f)
                    scaleX = sx0 + (1f - sx0) * t; scaleY = sy0 + (1f - sy0) * t
                }
            }
    ) {
        if (snap != null && !snap.isReleased) Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - (if (to == null || PickupStage.flip) 0f else pickupMorph(reveal())) }.drawBehind {
            if (snap.isReleased) return@drawBehind
            val sw = snap.size.width.toFloat(); val sh = snap.size.height.toFloat()
            if (sw > 0f && sh > 0f) scale(size.width / sw, size.height / sh, pivot = Offset.Zero) { drawLayer(snap) }
        })
        if (content != null && to != null && !PickupStage.flip) Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (snap == null || snap.isReleased) 1f else maxOf(pickupMorph(reveal()), PREWARM_ALPHA) }) { content() }
    }
}

/** How far the flying picture has turned from the home's picture into the window's (0..1) at reveal [k]. Pure. */
internal fun pickupMorph(k: Float): Float = ((k - 0.25f) / 0.5f).coerceIn(0f, 1f)

/** The record's own coordinates for [StorageFlightSlot]: the window position of its root and its content. */
internal fun Modifier.storageFlightRoot(onRoot: (Offset) -> Unit): Modifier = this.onGloballyPositioned { onRoot(it.positionInWindow()) }

/** 529s: the longest a trip waits for its flying copy to be drawn before it starts anyway. */
internal const val PICKUP_WAIT_MS = 250L

/**
 * 529s: before a trip starts - its flying copy laid out at home's place (at most [PICKUP_WAIT_MS]), then two more frames, so
 * the window's first heavy frames (KONKR: two of ~80 ms on opening Cube menu's face) are over before anything moves: a trip
 * started under them jumped half its way in one frame. Meanwhile the flying copy and the home's sit on each other.
 */
internal suspend fun pickupSettle() {
    kotlinx.coroutines.withTimeoutOrNull(PICKUP_WAIT_MS) { androidx.compose.runtime.snapshotFlow { PickupStage.drawn }.first { it } }
    // (529t: then until two frames in a row come in time - the window's first drawings are over - at most SETTLE_MAX_FRAMES.)
    // (Measured by the clock, not the frames' vsync times: a frame that stalls on the GPU still carries the next vsync's time -
    // KONKR pk529t5: 100 / 206 / 129 ms frames whose vsync times were 16 ms apart.)
    var last = androidx.compose.runtime.withFrameNanos { System.nanoTime() }
    var calm = 0
    for (i in 0 until SETTLE_MAX_FRAMES) {
        val now = androidx.compose.runtime.withFrameNanos { System.nanoTime() }
        calm = if (settledFrame(now - last)) calm + 1 else 0
        last = now
        if (calm >= 2) break
    }
}

/** 529t: a frame that came in time (under 24 ms after the one before it). Pure. */
internal fun settledFrame(deltaNanos: Long): Boolean = deltaNanos in 1 until 24_000_000L

/** 529t: the most frames a trip waits for the window to settle (~200 ms at 60 Hz) before it starts anyway. */
internal const val SETTLE_MAX_FRAMES = 12

/** 529t: a window drawn but not yet shown, so its first drawing is done before a pickup trip (invisible: under 1 / 255). */
internal const val PREWARM_ALPHA = 0.008f // (above the renderer's "nothing to draw" cut-off, 0.003 was skipped; ~2 / 255)

/** 529s: on each frame of the trip (the animation's own frame, before that frame is drawn) the home's copy is hidden while the
 *  object is away ([reveal] above 0), in the same frame the flying copy moves - never one without the other. */
internal fun pickupFrame(reveal: Float) {
    // (As Inspect's piece does: [MediaInspect.hold] hides the home's copy whole, [MediaInspect.amount] follows the trip, so
    // what a layout does with the others round it - the TV room's carousel steps them back - still glides.)
    MediaInspect.hold.value = caseHomeHidden(reveal) > 0f
    MediaInspect.amount.floatValue = reveal.coerceIn(0f, 1f)
}

/** 529s: the object has landed back at home - its home copy shows again (the flying one is still over it this frame). */
internal fun pickupHandBack() { MediaInspect.pickupReveal = null; MediaInspect.hold.value = false; MediaInspect.amount.floatValue = 0f }

/** 529t: where a turning-over trip has its object at its place and edge-on (the window's side turns in after it). */
internal const val FLIP_EDGE = 0.6f

/** 529t: Record crate's sleeve - out of the crate, forward and over (an even curve), and back. */
internal const val SLEEVE_FLIP_OPEN_MS = 720
internal const val SLEEVE_FLIP_CLOSE_MS = 560

/** 529t: the turn of the object's front away from you (0 = facing you, 1 = edge-on) at reveal [k]: it starts turning once well
 *  on its way and is edge-on as it arrives. Pure. */
internal fun flipTurn(k: Float): Float { val t = ((k - 0.3f) / (FLIP_EDGE - 0.3f)).coerceIn(0f, 1f); return t * t * (3f - 2f * t) }

/** 529t: the window's side turning in after [FLIP_EDGE] (1 = still edge-on, 0 = facing you). Pure. */
internal fun flipBackTurn(k: Float): Float { val t = ((k - FLIP_EDGE) / (1f - FLIP_EDGE)).coerceIn(0f, 1f); return 1f - (1f - (1f - t) * (1f - t) * (1f - t)) }

/** 529t: registers a turning-over trip's place in the window (window coordinates, as laid out - before the place's own turn). */
@Composable
internal fun Modifier.pickupFlipTarget(on: Boolean): Modifier = if (!on) this else this.onGloballyPositioned { c ->
    val r = c.boundsInWindow()
    PickupStage.flip = true
    if (PickupStage.target != r) PickupStage.target = r
}

/** 529t: the largest rect of [shape]'s proportions centred in [place]. Pure. */
internal fun fitShape(place: Rect, shape: Rect): Rect {
    if (shape.width <= 0f || shape.height <= 0f || place.width <= 0f || place.height <= 0f) return place
    val a = shape.width / shape.height
    val w = minOf(place.width, place.height * a); val h = w / a
    return Rect(place.center.x - w / 2f, place.center.y - h / 2f, place.center.x + w / 2f, place.center.y + h / 2f)
}
