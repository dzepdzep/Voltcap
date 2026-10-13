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

    /** [move]: update 182 — a control that moves inside itself (the Wall's slices): ◀ ▶ ▲ ▼ go to it first (true = used). */
    private class Target(val id: String, var rect: Rect, var press: () -> Unit, var order: Long, var move: ((Int) -> Boolean)? = null)

    /**
     * Update 182: a scrolling list on the second screen (the Games page, a console's games) scrolls a step when ▲ ▼
     * find nothing further on screen (its next rows aren't made yet); true = it scrolled.
     */
    var scroller: ((Int) -> Boolean)? = null
    private val main by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }

    private val targets = LinkedHashMap<String, Target>()
    private var counter = 0L
    private val keysDown = HashSet<Int>()

    fun register(id: String, rect: Rect, press: () -> Unit, move: ((Int) -> Boolean)? = null) {
        synchronized(targets) {
            val t = targets[id]
            if (t == null) targets[id] = Target(id, rect, press, counter++, move) else { t.rect = rect; t.press = press; t.move = move }
        }
    }

    fun unregister(id: String) {
        synchronized(targets) { targets.remove(id) }
        if (focused == id) focused = null
    }

    /** What can be reached now: on screen (a control scrolled away or on another page has no area). */
    private fun live(): List<Target> = synchronized(targets) { targets.values.filter { it.rect.width > 1f && it.rect.height > 1f } }

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
                if (next != null) { focused = next; sound(UiSound.MOVE) }
                else if (dir >= 2 && scroller?.let { runCatching { it(dir) }.getOrDefault(false) } == true) {
                    // Update 182: the list scrolled a row; the next one is lit once it's made.
                    main.postDelayed({ step(dir)?.let { focused = it } }, 140L)
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
internal fun Modifier.padTarget(id: String, onMove: ((Int) -> Boolean)? = null, onPress: () -> Unit): Modifier = composed {
    // Only on the second screen (the same piece can be drawn on the main screen, where the D-pad has its own focus).
    // (No early return: the same steps run every time.)
    val here = LocalOnSecondScreen.current
    val press by rememberUpdatedState(onPress)
    val move by rememberUpdatedState(onMove)
    val hasMove = onMove != null
    val lit = here && SecondPad.active && SecondPad.focused == id
    val bring = remember { BringIntoViewRequester() }
    DisposableEffect(id, here) { onDispose { if (here) SecondPad.unregister(id) } }
    LaunchedEffect(lit) { if (lit) runCatching { bring.bringIntoView() } }
    if (!here) this else this
        .bringIntoViewRequester(bring)
        .onGloballyPositioned { SecondPad.register(id, it.boundsInWindow(), { press() }, if (hasMove) { d -> move?.invoke(d) == true } else null) }
        .then(if (lit) Modifier.border(3.dp, ShelfTheme.accent, uiRounded(10.dp)) else Modifier)
}

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
            "Controller here · B or ▲ at the top: back up",
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
    DisposableEffect(id, here) { onDispose { if (here) SecondPad.unregister(id) } }
    if (!here) this else this.onGloballyPositioned { SecondPad.register(id, it.boundsInWindow(), { press() }) }
}
