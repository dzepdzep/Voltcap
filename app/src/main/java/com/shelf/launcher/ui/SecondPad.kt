package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlin.math.abs
// (Update 200: the gliding ring.)
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.positionInWindow

/*
 * Update 177: the controller on the second screen. The second screen used to be touch only (the controller always drove
 * the main screen), so on the AYN Thor the lower screen couldn't be reached with the D-pad. Now ▼ past the bottom of the
 * main screen (past the games and whatever lies under them) moves the controller onto the second screen:
 * - ◀ ▶ ▲ ▼ move between its buttons, tabs, folders, apps and games (the nearest one that way);
 * - A presses the one that's lit, B (or ▲ past its top) goes back to the main screen;
 * - a game picked there takes the main screen to it, and the controller goes back up with it.
 * Each control on the second screen joins in with Modifier.padTarget (it reports where it is; lit with an accent ring).
 */
internal object SecondPad {
    /** The controller is on the second screen. */
    var active by mutableStateOf(false)
        private set
    /** The control that's lit. */
    var focused by mutableStateOf<String?>(null)
        private set

    /**
     * Update 200: while true the gliding ring ([SecondPadRing]) holds still where it is — the list under it is scrolling a
     * row (the scroller step in [key]) and the next control is lit a moment later; it then glides from here into that row.
     */
    var ringHold by mutableStateOf(false)
        private set

    /**
     * [move]: update 182 — a control that moves inside itself (the Wall's slices): ◀ ▶ ▲ ▼ go to it first (true = used).
     * Update 200: [rect] is snapshot state so the gliding ring (read in its draw phase) follows its control while a list
     * scrolls or a popup scales in, and [plain] marks a control that shows it's lit in its own style (no ring over it).
     */
    // QA: (C7) [menu]: what X does on the control (its long-press menu), or null.
    private class Target(val id: String, rect: Rect, var press: () -> Unit, var order: Long, var move: ((Int) -> Boolean)? = null, var layer: Int = 0, var plain: Boolean = false, var menu: (() -> Unit)? = null) {
        var rect by mutableStateOf(rect)
    }

    /**
     * Update 182: a scrolling list on the second screen (the Games page, a console's games) scrolls a step when ▲ ▼
     * find nothing further on screen (its next rows aren't made yet); true = it scrolled.
     */
    var scroller: ((Int) -> Boolean)? = null
    private val main by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }

    private val targets = LinkedHashMap<String, Target>()
    private var counter = 0L
    private val keysDown = HashSet<Int>()

    fun register(id: String, rect: Rect, press: () -> Unit, move: ((Int) -> Boolean)? = null, layer: Int = 0, plain: Boolean = false, menu: (() -> Unit)? = null) {
        synchronized(targets) {
            val t = targets[id]
            if (t == null) targets[id] = Target(id, rect, press, counter++, move, layer, plain, menu) else { t.rect = rect; t.press = press; t.move = move; t.layer = layer; t.plain = plain; t.menu = menu }
        }
        if (layer > topLayer) topLayer = layer // (Update 200)
    }

    /**
     * Update 200: the highest layer with controls on it (a popup open over the page = 1). The ring is drawn over everything,
     * so it hides while the lit control is under a popup (the folder just opened) instead of floating on top of the popup.
     */
    var topLayer by mutableIntStateOf(0)
        private set

    /** Update 200: control [id]'s layer (0 when unknown). */
    internal fun layerOf(id: String?): Int = if (id == null) 0 else synchronized(targets) { targets[id] }?.layer ?: 0

    /** Update 200: where control [id] is now (window coordinates; snapshot state, so a draw phase reading it follows it). */
    internal fun rectOf(id: String?): Rect? {
        if (id == null) return null
        val r = synchronized(targets) { targets[id] }?.rect ?: return null
        return if (r.width > 1f && r.height > 1f) r else null
    }

    /** Update 200: control [id] draws its own lit style (padTargetPlain), so the gliding ring stays off it. */
    internal fun isPlain(id: String?): Boolean = id != null && synchronized(targets) { targets[id] }?.plain == true

    /** QA: (C7) control [id] has a long-press menu (X opens it). */
    internal fun hasMenu(id: String?): Boolean = id != null && synchronized(targets) { targets[id] }?.menu != null

    fun unregister(id: String) {
        val top = synchronized(targets) { targets.remove(id); targets.values.maxOfOrNull { it.layer } ?: 0 }
        if (top != topLayer) topLayer = top // (Update 200: a popup closed)
        if (focused == id) focused = null
    }

    /** What can be reached now: on screen (a control scrolled away or on another page has no area). */
    private fun live(): List<Target> = synchronized(targets) {
        val shown = targets.values.filter { it.rect.width > 1f && it.rect.height > 1f }
        // (Update 195: a popup over the page (a console's games, the app drawer) takes the controller to itself: only the
        // top layer's controls can be reached — moving inside a folder used to wander onto the page behind it.)
        val top = shown.maxOfOrNull { it.layer } ?: 0
        shown.filter { it.layer == top }
    }

    /** Moves the controller onto the second screen (lighting the top-left control). False when there's nothing there. */
    fun enter(): Boolean {
        if (!SecondScreenShown.value) return false
        val all = live()
        if (all.isEmpty()) return false
        val keep = all.firstOrNull { it.id == focused }
        focused = (keep ?: all.minWith(compareBy<Target>({ it.rect.top.toInt() / 24 }, { it.rect.left }))).id
        active = true
        return true
    }

    fun leave() {
        active = false
        ringHold = false // (Update 200)
    }

    /** The keys while the controller is on the second screen (call before the main screen's own). True = handled. */
    fun key(event: KeyEvent, sound: (UiSound) -> Unit): Boolean {
        if (!active) return false
        if (!SecondScreenShown.value) { leave(); return false }
        val code = event.nativeKeyEvent.keyCode
        val down = event.type == KeyEventType.KeyDown
        val up = event.type == KeyEventType.KeyUp
        if (up && keysDown.remove(code)) return true
        val dir = when (event.key) {
            Key.DirectionLeft -> 0; Key.DirectionRight -> 1; Key.DirectionUp -> 2; Key.DirectionDown -> 3
            else -> -1
        }
        if (dir >= 0) {
            if (down) {
                keysDown += code
                // (Update 182: a control that moves inside itself first — the Wall's slices.)
                val own = synchronized(targets) { targets[focused] }?.move
                if (own != null && runCatching { own(dir) }.getOrDefault(false)) { sound(UiSound.MOVE); return true }
                val next = step(dir)
                if (next != null) { focused = next; ringHold = false; sound(UiSound.MOVE) }
                else if (dir >= 2 && scroller?.let { runCatching { it(dir) }.getOrDefault(false) } == true) {
                    // Update 182: the list scrolled a row; the next one is lit once it's made.
                    // Update 200: the ring holds its place while the row scrolls in, then glides into it (it used to ride
                    // up with the old control and pop down to the new one). Released even if nothing is found.
                    ringHold = true
                    main.postDelayed({ step(dir)?.let { focused = it }; ringHold = false }, 140L)
                    sound(UiSound.MOVE)
                }
                else if (dir == 2 && event.nativeKeyEvent.repeatCount == 0) { leave(); sound(UiSound.MOVE) }
            }
            return true
        }
        return when (event.key) {
            Key.ButtonA, Key.Enter, Key.DirectionCenter -> {
                if (down) keysDown += code
                if (up) {
                    val t = synchronized(targets) { targets[focused] }
                    if (t != null) { sound(UiSound.OPEN); runCatching { t.press() } }
                }
                true
            }
            Key.ButtonB, Key.Back -> {
                if (down) keysDown += code
                if (up) { leave(); sound(UiSound.BACK) }
                true
            }
            // QA: (C7) X on a control with a long-press menu (folder, dock app) opens it; elsewhere X goes back up as before.
            Key.ButtonX -> {
                val menu = synchronized(targets) { targets[focused] }?.menu
                if (menu == null) { if (down) leave(); false }
                else {
                    if (down) {
                        keysDown += code // (its key-up is swallowed at the top)
                        if (event.nativeKeyEvent.repeatCount == 0) { sound(UiSound.OPEN); runCatching { menu() } }
                    }
                    true
                }
            }
            else -> {
                // Any other button: back to the main screen, where it does its usual job.
                if (down) leave()
                false
            }
        }
    }

    /** The nearest control from the lit one in direction [dir] (0 ◀, 1 ▶, 2 ▲, 3 ▼), or null. */
    private fun step(dir: Int): String? {
        val all = live()
        val from = all.firstOrNull { it.id == focused } ?: return all.firstOrNull()?.id
        val r = from.rect
        val cx = r.center.x; val cy = r.center.y
        var best: Target? = null
        var bestScore = Float.MAX_VALUE
        for (t in all) {
            if (t.id == from.id) continue
            val o = t.rect
            // Along: how far that way (must be past this one's middle); across: how far off the line.
            val along: Float; val across: Float
            when (dir) {
                0 -> { along = cx - o.center.x; across = overlapGap(r.top, r.bottom, o.top, o.bottom) }
                1 -> { along = o.center.x - cx; across = overlapGap(r.top, r.bottom, o.top, o.bottom) }
                2 -> { along = cy - o.center.y; across = overlapGap(r.left, r.right, o.left, o.right) }
                else -> { along = o.center.y - cy; across = overlapGap(r.left, r.right, o.left, o.right) }
            }
            if (along <= 4f) continue
            val score = along + across * 3f
            if (score < bestScore) { bestScore = score; best = t }
        }
        return best?.id
    }

    /** 0 when the two spans overlap, else the gap between them. */
    private fun overlapGap(a0: Float, a1: Float, b0: Float, b1: Float): Float =
        if (b1 < a0) a0 - b1 else if (b0 > a1) b0 - a1 else 0f
}

/**
 * Update 177: a control on the second screen the controller can reach ([SecondPad]): [id] unique on the screen, [onPress]
 * what A does (the same as a tap). Lit with an accent ring while the controller is on it, and scrolled into view.
 */
internal fun Modifier.padTarget(id: String, onMove: ((Int) -> Boolean)? = null, onMenu: (() -> Unit)? = null, onPress: () -> Unit): Modifier = composed {
    // Only on the second screen (the same piece can be drawn on the main screen, where the D-pad has its own focus).
    // (No early return: the same steps run every time.)
    val here = LocalOnSecondScreen.current
    val press by rememberUpdatedState(onPress)
    val move by rememberUpdatedState(onMove)
    val hasMove = onMove != null
    // QA: (C7) X = the control's long-press menu.
    val menu by rememberUpdatedState(onMenu)
    val hasMenu = onMenu != null
    // Update 200: derived, so a move recomposes only the two controls whose lit state changed (not every target).
    val lit by remember(id, here) { derivedStateOf { here && SecondPad.active && SecondPad.focused == id } }
    val layer = LocalPadLayer.current
    val bring = remember { BringIntoViewRequester() }
    DisposableEffect(id, here) { onDispose { if (here) SecondPad.unregister(id) } }
    // (Scrollable parents animate bringIntoView themselves — a spring, not a jump — and the gliding ring follows the
    // control's live bounds while they do.)
    LaunchedEffect(lit) { if (lit) runCatching { bring.bringIntoView() } }
    // Update 200: no border on the control any more — one ring over the whole second screen ([SecondPadRing]) glides
    // from the control that was lit to this one (the old per-control border jumped from one to the next).
    if (!here) this else this
        .bringIntoViewRequester(bring)
        .onGloballyPositioned { SecondPad.register(id, it.boundsInWindow(), { press() }, if (hasMove) { d -> move?.invoke(d) == true } else null, layer, menu = if (hasMenu) { { menu?.invoke() } } else null) }
}

/** Update 195: which layer a second-screen control is on (a popup over the page is 1): only the top one is reachable. */
internal val LocalPadLayer = androidx.compose.runtime.staticCompositionLocalOf { 0 }

/** Update 177: true inside the second screen's window (SecondScreenHost). */
internal val LocalOnSecondScreen = androidx.compose.runtime.staticCompositionLocalOf { false }

private var padIds = 0

/** A unique id for a [padTarget] that has no natural one. */
@Composable
internal fun rememberPadId(prefix: String): String = remember { prefix + "-" + (++padIds) }

/** A short hint over the second screen while the controller is on it. */
@Composable
internal fun SecondPadHint() {
    if (SecondPad.active) androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        androidx.compose.material3.Text(
            // QA: (C7) "X: menu" while the lit control has one.
            if (SecondPad.hasMenu(SecondPad.focused)) "Controller here · X: menu · B or ▲ at the top: back up"
            else "Controller here · B or ▲ at the top: back up",
            color = Color.White,
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 4.dp)
                .clip(uiRounded(50))
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}

/** Update 182: a [padTarget] that draws no ring of its own (the control shows it's lit in its own style). */
internal fun Modifier.padTargetPlain(id: String, onPress: () -> Unit): Modifier = composed {
    val here = LocalOnSecondScreen.current
    val press by rememberUpdatedState(onPress)
    val layer = LocalPadLayer.current
    DisposableEffect(id, here) { onDispose { if (here) SecondPad.unregister(id) } }
    // (Update 200: registered as plain — the gliding ring fades off while one of these is lit.)
    if (!here) this else this.onGloballyPositioned { SecondPad.register(id, it.boundsInWindow(), { press() }, null, layer, plain = true) }
}

/** Update 200: what the gliding ring remembers between frames (plain fields: written and read in the draw phase only). */
private class RingMemo {
    /** Where the ring was when the lit control changed (it glides from here); null = sit on the control. */
    var from: Rect? = null
    /** Where it was last drawn (window coordinates). */
    var shown: Rect? = null
    /** The overlay's own place in the window (the controls report window coordinates). */
    var origin: Offset = Offset.Zero
}

/**
 * Update 200: the controller's highlight on the second screen — ONE accent ring drawn over everything there (pages and the
 * folder popup alike) that glides, position and size, from the control that was lit to the next one with a soft spring,
 * instead of a border jumping between controls ("going through stuff on the second screen just spawns the next column").
 * It follows its control while a list scrolls (the control's bounds are snapshot state read in the draw phase), fades and
 * shrinks onto the first control when the controller comes down here, fades out when it goes back up, and keeps off
 * controls that show their own lit style (padTargetPlain). All motion is read in the draw phase: no recomposition per frame.
 * Composed once, last, in SecondScreenHost.
 */
@Composable
internal fun SecondPadRing() {
    val accent = ShelfTheme.accent
    val id = SecondPad.focused
    val hold = SecondPad.ringHold
    // (Off a plain control, and off one left under a popup until a move takes it into the popup.)
    val show = SecondPad.active && id != null && !SecondPad.isPlain(id) && SecondPad.layerOf(id) >= SecondPad.topLayer
    val glide = remember { Animatable(1f) }
    val appear = remember { Animatable(0f) }
    val memo = remember { RingMemo() }
    LaunchedEffect(show) {
        if (show) appear.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
        else appear.animateTo(0f, tween(170))
    }
    LaunchedEffect(id, hold) {
        val start = memo.shown
        // Not on screen yet (just came down, or back from a plain control): it appears on the control, no glide.
        if (start == null || appear.value < 0.05f) { memo.from = null; glide.snapTo(1f); return@LaunchedEffect }
        // From wherever it is right now (even mid-glide), so quick presses chain smoothly.
        memo.from = start
        glide.snapTo(0f)
        // ~250 ms to settle, a hair of overshoot.
        if (!hold) glide.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 520f))
    }
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned { memo.origin = it.positionInWindow() }
            .drawBehind {
                val live = SecondPad.rectOf(SecondPad.focused)
                val to = live ?: memo.shown ?: return@drawBehind
                val p = glide.value
                val from = memo.from
                val r = if (from == null || p >= 1f) to else lerp(from, to, p)
                memo.shown = r
                val a = appear.value.coerceIn(0f, 1f)
                if (a <= 0.01f) return@drawBehind
                // Coming in it shrinks onto the control; going it grows a little as it fades.
                val s = 1f + 0.14f * (1f - a)
                val sw = 3.dp.toPx()
                val w = r.width * s
                val h = r.height * s
                val left = r.center.x - w / 2f - memo.origin.x
                val top = r.center.y - h / 2f - memo.origin.y
                val radius = 10.dp.toPx()
                // A soft glow just outside the edge (one wide, faint stroke: cheap)...
                drawRoundRect(
                    accent.copy(alpha = 0.22f * a), Offset(left, top), Size(w, h), CornerRadius(radius), style = Stroke(7.dp.toPx())
                )
                // ...and the 3 dp ring itself, inside the edge like the old border.
                drawRoundRect(
                    accent.copy(alpha = a), Offset(left + sw / 2f, top + sw / 2f), Size((w - sw).coerceAtLeast(0f), (h - sw).coerceAtLeast(0f)),
                    CornerRadius((radius - sw / 2f).coerceAtLeast(0f)), style = Stroke(sw)
                )
            }
    )
}
