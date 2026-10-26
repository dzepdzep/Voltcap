package com.shelf.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.HotkeyAction
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Update 344: Bubble home (2011) — Voltcap's own take on the early-2010s handheld's home of glossy bubbles and its
 * game pages (original drawing, names and sounds; nobody's logos or artwork). Two stages:
 *
 * BUBBLES — your games as glossy round bubbles in staggered rows on a soft wallpaper, a page at a time (pages run
 *   downwards, dots on the right). The one you're on swells and bobs, its name in a pill under it; the others float
 *   gently. ◀ ▶ along, ▲ ▼ between the rows and on to the next page. A unfolds the game's LiveArea. With Folders on,
 *   B steps up to your consoles as folder bubbles (A opens one).
 * LIVEAREA — the game's own page: its art in layers, its bubble and name, a big Start button, round buttons for its
 *   achievements, details and options, its screenshots, achievements and facts on the right. The page's top corner is
 *   curled: B (or a tap / a drag on the corner) peels it back to the bubbles. ◀ ▶ (on Start) the next game's page,
 *   ▼ the round buttons. Settings › Home › Layout options (Bubble home): open straight into the LiveArea instead.
 * Keep distinct from Airy home (a row of tiles): here everything is round, glossy and paged.
 */

/** Update 344: where the controller is in Bubble home. */
internal object BubbleNav {
    enum class View { BUBBLES, FOLDERS, LIVE }
    var view by mutableStateOf(View.BUBBLES)
    var folder by mutableIntStateOf(0)
    var fromFolders = false
    /** LiveArea: 0 Start, 1 achievements, 2 details, 3 options. */
    var live by mutableIntStateOf(0)
    /** Bumped on each move between LiveAreas (+1 next / −1 back) so the page slides the right way. */
    var liveDir = 1
    var started = false
    @Volatile var ctx: StyleCtx? = null
    @Volatile var systems: List<Int> = emptyList()
    /** The page as drawn: bubbles across (the middle row one fewer) — read by the keys. */
    @Volatile var cols = 5
    internal val keysDown = HashSet<Int>()
    fun perPage(c: Int = cols): Int = 3 * c - 1
    fun signature(): Any = listOf(view, folder, live)
}

private fun bbSound(s: KitSound, fallback: UiSound?) = LayoutKitSounds.play(LayoutKit.BUBBLES, s, fallback)

/** The slot [s] on a page of [cols] across → (row, column); the middle row has one fewer. */
internal fun bubbleSlot(s: Int, cols: Int): Pair<Int, Int> = when {
    s < cols -> 0 to s
    s < 2 * cols - 1 -> 1 to (s - cols)
    else -> 2 to (s - (2 * cols - 1))
}

internal fun bubbleIndex(row: Int, col: Int, cols: Int): Int = when (row) {
    0 -> col
    1 -> cols + col
    else -> 2 * cols - 1 + col
}

/** Where ▲ / ▼ from bubble [i] go (pages run downwards); null: past the first / last. */
internal fun bubbleVertical(i: Int, down: Boolean, cols: Int, count: Int): Int? {
    val per = 3 * cols - 1
    val page = i / per
    val (row, col) = bubbleSlot(i % per, cols)
    val (nPage, nRow, nCol) = if (down) when (row) {
        0 -> Triple(page, 1, col.coerceAtMost(cols - 2))
        1 -> Triple(page, 2, col)
        else -> Triple(page + 1, 0, col)
    } else when (row) {
        0 -> Triple(page - 1, 2, col)
        1 -> Triple(page, 0, col)
        else -> Triple(page, 1, col.coerceAtMost(cols - 2))
    }
    if (nPage < 0) return null
    val j = nPage * per + bubbleIndex(nRow, nCol, cols)
    return when {
        j < count -> j
        // The next page / row is shorter: its last bubble.
        nPage * per < count && j >= count -> (count - 1).takeIf { it != i && (down || it < i) }
        else -> null
    }
}

private fun bubbleHotkey(a: HotkeyAction): Boolean {
    val h = GreenCoreHooks.hotkey ?: return false
    h(a)
    return true
}

internal fun bubblesOpenLive() {
    BubbleNav.live = 0
    BubbleNav.view = BubbleNav.View.LIVE
    bbSound(KitSound.OPEN, UiSound.OPEN)
}

/** B / the back gesture: LiveArea → bubbles; bubbles opened from a folder → folders. False: nothing to do here. */
internal fun bubblesBack(): Boolean {
    when (BubbleNav.view) {
        BubbleNav.View.LIVE -> BubbleNav.view = BubbleNav.View.BUBBLES
        BubbleNav.View.BUBBLES -> if (Features.bvFolders.value) {
            val c = BubbleNav.ctx
            BubbleNav.folder = if (c != null) BubbleNav.systems.indexOf(c.stop).coerceAtLeast(0) else 0
            BubbleNav.view = BubbleNav.View.FOLDERS
        } else return false
        BubbleNav.View.FOLDERS -> return false
    }
    bbSound(KitSound.BACK, UiSound.BACK)
    return true
}

private fun bubblesPickFolder(c: StyleCtx, pos: Int) {
    val idx = BubbleNav.systems.getOrNull(pos) ?: return
    BubbleNav.folder = pos
    val steps = pocketStepsTo(c, idx)
    if (steps != 0) c.actions.category(steps)
    BubbleNav.fromFolders = true
    BubbleNav.view = BubbleNav.View.BUBBLES
    bbSound(KitSound.SELECT, if (steps == 0) UiSound.SELECT else null)
}

/** A on a LiveArea control (or a tap). */
internal fun bubblesLiveAct(c: StyleCtx, which: Int) {
    val g = c.game ?: return
    BubbleNav.live = which
    when (which) {
        0 -> { bbSound(KitSound.SPECIAL, null); c.actions.play(g) }
        1 -> { bbSound(KitSound.SELECT, UiSound.OPEN); c.actions.achievements(g) }
        2 -> { bbSound(KitSound.SELECT, UiSound.OPEN); c.actions.details() }
        else -> { bbSound(KitSound.SELECT, UiSound.OPEN); c.actions.options(g) }
    }
}

private fun bubbleStep(c: StyleCtx, to: Int, page: Boolean) {
    if (to == c.focus) return
    c.actions.select(to)
    bbSound(if (page) KitSound.PAGE else KitSound.MOVE, UiSound.MOVE)
}

/**
 * Update 344: Bubble home's keys (all of them while it's on). True = handled.
 * BUBBLES: ◀ ▶ along (Endless round), ▲ ▼ between rows and pages, A the LiveArea, B the folders (Folders on; else the
 * home screen's own), X / Y the home screen's own. FOLDERS: the same moves, A opens, B the home screen's own.
 * LIVE: ◀ ▶ the next game's page (on Start) or along the round buttons, ▲ ▼ between Start and them, A, B back, X favourite,
 * Y achievements. Start: the quick menu; Select: sort & filter.
 */
internal fun bubblesKey(event: KeyEvent, reach: (Boolean) -> Boolean): Boolean {
    val c = BubbleNav.ctx ?: return false
    val code = event.nativeKeyEvent.keyCode
    val down = event.type == KeyEventType.KeyDown
    val up = event.type == KeyEventType.KeyUp
    if (up && BubbleNav.keysDown.remove(code)) return true
    val k = event.key
    if (k == Key.ButtonStart || k == Key.Menu) { if (up) c.actions.quickMenu(); return up }
    if (k == Key.ButtonSelect) return up && bubbleHotkey(HotkeyAction.SORT)
    val view = BubbleNav.view
    val isA = k == Key.ButtonA || k == Key.Enter || k == Key.DirectionCenter
    if (isA) {
        if (up && !event.nativeKeyEvent.isCanceled) when (view) {
            BubbleNav.View.BUBBLES -> if (c.games.isNotEmpty()) bubblesOpenLive()
            BubbleNav.View.FOLDERS -> bubblesPickFolder(c, BubbleNav.folder)
            BubbleNav.View.LIVE -> bubblesLiveAct(c, BubbleNav.live)
        }
        return true
    }
    if (!down) return false
    val first = event.nativeKeyEvent.repeatCount == 0
    fun take(): Boolean { BubbleNav.keysDown += code; return true }
    val isB = k == Key.ButtonB || k == Key.Back
    if (isB) return if (first && bubblesBack()) take() else if (view == BubbleNav.View.LIVE) take() else false
    val cols = BubbleNav.cols
    when (view) {
        BubbleNav.View.LIVE -> {
            val g = c.game ?: return take()
            val l = BubbleNav.live
            when (k) {
                Key.DirectionLeft, Key.DirectionRight -> {
                    val d = if (k == Key.DirectionRight) 1 else -1
                    if (l == 0) {
                        val n = c.games.size
                        val next = if (StyleLoop.on && n > 1) (c.focus + d).mod(n) else (c.focus + d).coerceIn(0, n - 1)
                        if (next != c.focus) { BubbleNav.liveDir = d; c.actions.select(next); bbSound(KitSound.PAGE, UiSound.MOVE) } else if (first) EdgeBump.hit(d)
                    } else {
                        val nl = (l + d).coerceIn(1, 3)
                        if (nl != l) { BubbleNav.live = nl; bbSound(KitSound.MOVE, UiSound.MOVE) }
                    }
                }
                Key.DirectionDown -> if (l == 0) { BubbleNav.live = 1; bbSound(KitSound.MOVE, UiSound.MOVE) } else if (first) reach(false)
                Key.DirectionUp -> if (l != 0) { BubbleNav.live = 0; bbSound(KitSound.MOVE, UiSound.MOVE) } else if (first) reach(true)
                Key.ButtonX -> if (first) c.actions.favorite(g)
                Key.ButtonY -> if (first) c.actions.achievements(g)
                else -> return false
            }
            return take()
        }
        BubbleNav.View.FOLDERS -> {
            val n = BubbleNav.systems.size
            val cur = BubbleNav.folder
            val next: Int? = when (k) {
                Key.DirectionLeft -> cur - 1
                Key.DirectionRight -> cur + 1
                Key.DirectionUp -> bubbleVertical(cur, false, cols, n) ?: run { if (first) reach(true); null }
                Key.DirectionDown -> bubbleVertical(cur, true, cols, n) ?: run { if (first) reach(false); null }
                Key.ButtonX, Key.ButtonY -> return take()
                else -> return false
            }
            if (next != null) {
                val cl = next.coerceIn(0, (n - 1).coerceAtLeast(0))
                if (cl != cur) {
                    val per = BubbleNav.perPage()
                    BubbleNav.folder = cl
                    bbSound(if (cl / per != cur / per) KitSound.PAGE else KitSound.MOVE, UiSound.MOVE)
                }
            }
            return take()
        }
        BubbleNav.View.BUBBLES -> {
            val n = c.games.size
            if (n == 0) return if (k == Key.ButtonX || k == Key.ButtonY) take() else false
            val per = BubbleNav.perPage()
            val f = c.focus
            when (k) {
                Key.DirectionLeft, Key.DirectionRight -> {
                    val d = if (k == Key.DirectionRight) 1 else -1
                    val next = if (StyleLoop.on && n > 1) (f + d).mod(n) else (f + d).coerceIn(0, n - 1)
                    if (next != f) bubbleStep(c, next, next / per != f / per) else if (first) EdgeBump.hit(d)
                }
                Key.DirectionUp, Key.DirectionDown -> {
                    val downK = k == Key.DirectionDown
                    val next = bubbleVertical(f, downK, cols, n)
                    if (next != null) bubbleStep(c, next, next / per != f / per) else if (first) reach(!downK)
                }
                else -> return false
            }
            return take()
        }
    }
}

// ---- Wallpaper ----

private class BubbleWall(val top: Color, val mid: Color, val bottom: Color, val glow: Color, val ink: Color)

private fun bubbleWall(w: Features.BvWall): BubbleWall = when (w) {
    Features.BvWall.SKY -> BubbleWall(Color(0xFF1D4E8F), Color(0xFF3A8BD0), Color(0xFF9FD8F2), Color(0xFFDDF5FF), Color(0xFF0D2A4A))
    Features.BvWall.SUNSET -> BubbleWall(Color(0xFF3A1C4E), Color(0xFFC0506A), Color(0xFFFFC07A), Color(0xFFFFE6C4), Color(0xFF3A1A2A))
    Features.BvWall.MINT -> BubbleWall(Color(0xFF0E4A47), Color(0xFF2E9C8A), Color(0xFFBFF0D8), Color(0xFFE6FFF4), Color(0xFF0B302C))
    Features.BvWall.NIGHT -> BubbleWall(Color(0xFF05060F), Color(0xFF151A3A), Color(0xFF2E3570), Color(0xFF9FB2FF), Color(0xFF0A0D22))
    Features.BvWall.GAME -> BubbleWall(Color(0x66000000), Color(0x33000000), Color(0x88000000), Color(0xFFFFFFFF), Color(0xFF101010))
}

/** Update 344: the wallpaper: a soft gradient, ribbons of light and slow bokeh (the game's art: just a soft shade over it). */
@Composable
internal fun BubblesBackdrop(modifier: Modifier) {
    val wall = Features.bvWall.state.value
    val w = bubbleWall(wall)
    val motion = rememberAnimationsEnabled() && !LocalLiteEffects.current && rememberIsActive() && !HomeIdle.calm && Features.bvFloat.state.value
    val clock = rememberGcClock(motion, 1f, 24)
    Canvas(modifier) {
        if (wall == Features.BvWall.GAME) {
            drawRect(Brush.verticalGradient(listOf(w.top, w.mid, w.bottom)))
            return@Canvas
        }
        drawRect(Brush.verticalGradient(listOf(w.top, w.mid, w.bottom)))
        val t = clock.floatValue
        // Two soft ribbons of light across.
        for (r in 0 until 2) {
            val p = Path()
            val y0 = size.height * (0.55f + r * 0.15f)
            p.moveTo(0f, y0)
            var x = 0f
            while (x <= size.width) {
                p.lineTo(x, y0 + sin(x / size.width * 6f + t * 0.25f + r * 1.7f) * size.height * 0.05f)
                x += size.width / 40f
            }
            p.lineTo(size.width, size.height); p.lineTo(0f, size.height); p.close()
            drawPath(p, Brush.verticalGradient(listOf(w.glow.copy(alpha = 0.16f), Color.Transparent), startY = y0 - size.height * 0.05f, endY = y0 + size.height * 0.25f))
        }
        // Bokeh.
        for (i in 0 until 16) {
            val rr = size.minDimension * (0.015f + 0.05f * bvHash(i, 1))
            val x = size.width * bvHash(i, 2) + sin(t * 0.2f + i) * rr
            val span = size.height + rr * 4f
            val y = size.height + rr * 2f - ((bvHash(i, 3) * span + t * (6f + 10f * bvHash(i, 4))) % span)
            drawCircle(w.glow.copy(alpha = 0.05f + 0.07f * bvHash(i, 5)), rr, Offset(x, y))
        }
    }
}

private fun bvHash(i: Int, salt: Int): Float {
    var h = i * 73856093 + salt * 19349663
    h = h xor (h ushr 13); h *= 0x5bd1e995; h = h xor (h ushr 15)
    return ((h ushr 1) % 10000) / 10000f
}

// ---- The layout ----

@Composable
internal fun StyleBubbles(c: StyleCtx, modifier: Modifier = Modifier) {
    val systems = remember(c.stops) { gcSystems(c) }
    SideEffect {
        BubbleNav.ctx = c
        BubbleNav.systems = systems
        StyleNav.side = emptyList(); StyleNav.top = emptyList(); StyleNav.bar = emptyList()
        StyleLoop.on = loopRowOn(c)
        if (!BubbleNav.started) {
            BubbleNav.started = true
            if (Features.bvOpens.value == Features.BvOpens.LIVEAREA && c.games.isNotEmpty()) BubbleNav.view = BubbleNav.View.LIVE
        }
        // Folders switched off while showing them: back to the bubbles.
        if (BubbleNav.view == BubbleNav.View.FOLDERS && !Features.bvFolders.value) BubbleNav.view = BubbleNav.View.BUBBLES
        if (BubbleNav.view == BubbleNav.View.LIVE && c.games.isEmpty()) BubbleNav.view = BubbleNav.View.BUBBLES
    }
    rememberLayoutKit(LayoutKit.BUBBLES, Features.bvSounds.state.value, c.state.appearance.soundsEnabled)
    DisposableEffect(Unit) { onDispose { BubbleNav.keysDown.clear() } }
    val windowOpen = GamePopup.game != null
    val view = BubbleNav.view
    androidx.activity.compose.BackHandler(enabled = !windowOpen && (view == BubbleNav.View.LIVE || (view == BubbleNav.View.BUBBLES && Features.bvFolders.state.value))) { bubblesBack() }
    val caption = CategoryTitle.captionOn(c.state.appearance)
    if (view == BubbleNav.View.BUBBLES && caption) CategoryTitle.Native(null)
    // The LiveArea opening (unfolding out of the bubble) and closing (peeled back).
    val reduced = !rememberAnimationsEnabled()
    val open = remember { Animatable(if (view == BubbleNav.View.LIVE) 1f else 0f) }
    LaunchedEffect(view) {
        val to = if (view == BubbleNav.View.LIVE) 1f else 0f
        if (reduced) open.snapTo(to) else open.animateTo(to, tween(if (to > 0f) 520 else 460, easing = FastOutSlowInEasing))
    }
    // Screenshots / achievements for the LiveArea of the game you're on.
    val g = c.game
    LaunchedEffect(g?.stableKey, view) {
        val gg = g ?: return@LaunchedEffect
        if (view != BubbleNav.View.LIVE) return@LaunchedEffect
        kotlinx.coroutines.delay(300)
        if (!c.state.screenshots.containsKey(gg.stableKey)) c.actions.needMedia(gg)
        if (c.state.raAchievementDetails[gg.stableKey].isNullOrEmpty() && c.progress(gg) != null) c.actions.needAchievements(gg)
        c.actions.needMetadata(gg)
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val cols = if (maxWidth / maxHeight > 1.45f) 5 else 4
        val nameMax = maxWidth * 0.42f
        SideEffect { BubbleNav.cols = cols }
        val o = open.value
        // The bubbles (or folders) recede as the LiveArea comes forward.
        if (o < 0.999f) Box(Modifier.fillMaxSize().graphicsLayer { val s = 1f - 0.08f * o; scaleX = s; scaleY = s; alpha = 1f - o }) {
            if (view == BubbleNav.View.FOLDERS) BubbleFolders(c, cols, systems)
            else BubblePages(c, cols)
            // The category (or Folders) as a small tag top left.
            if (caption || view == BubbleNav.View.FOLDERS) Row(
                Modifier.padding(start = 16.dp, top = 4.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.22f)).padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(if (view == BubbleNav.View.FOLDERS) Icons.Filled.Folder else Icons.Filled.BubbleChart, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (view == BubbleNav.View.FOLDERS) "Folders" else c.categoryLabel + "  ·  " + c.games.size, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            // The name of the bubble you're on, in a white pill at the top in the middle.
            val name = if (view == BubbleNav.View.FOLDERS) c.stops.getOrNull(systems.getOrNull(BubbleNav.folder) ?: -1)?.label
                else g?.let { LocalTitleOptions.current.display(it.name) }
            if (name != null) androidx.compose.animation.Crossfade(targetState = name, label = "bvName", modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp)) { nm ->
                Text(nm, color = Color(0xFF16202C), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = nameMax).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.9f)).padding(horizontal = 16.dp, vertical = 5.dp))
            }
        }
        if (o > 0.001f && g != null) LiveAreaPage(c, g, o, view == BubbleNav.View.LIVE)
    }
}

/** Where bubble [slot] sits on a page [w] × [h] px of [cols] across: its centre and the diameter. */
private fun bubbleSpot(slot: Int, cols: Int, w: Float, h: Float): Triple<Float, Float, Float> {
    val (row, col) = bubbleSlot(slot, cols)
    val padX = w * 0.06f
    val stepX = (w - padX * 2f) / cols
    val top = h * 0.07f
    val stepY = (h * 0.86f) / 3f
    val x = padX + (col + 0.5f + if (row == 1) 0.5f else 0f) * stepX
    val y = top + (row + 0.5f) * stepY
    val d = minOf(stepX * 0.74f, stepY * 0.76f)
    return Triple(x, y, d)
}

@Composable
private fun BubblePages(c: StyleCtx, cols: Int) {
    val per = 3 * cols - 1
    val count = c.games.size
    if (count == 0) { StyleEmpty(); return }
    val focus = c.focus.coerceIn(0, count - 1)
    val pages = (count + per - 1) / per
    val page = focus / per
    val reduced = !rememberAnimationsEnabled()
    val pageAnim = remember { Animatable(page.toFloat()) }
    LaunchedEffect(page) { if (reduced) pageAnim.snapTo(page.toFloat()) else pageAnim.animateTo(page.toFloat(), spring(dampingRatio = 0.86f, stiffness = 260f)) }
    val select by rememberUpdatedState(c.actions.select)
    val latestFocus by rememberUpdatedState(focus)
    val latestCount by rememberUpdatedState(count)
    val density = LocalDensity.current
    val motion = rememberAnimationsEnabled() && !LocalLiteEffects.current && rememberIsActive() && !HomeIdle.calm && Features.bvFloat.state.value
    val clock = rememberGcClock(motion, 1f, 30)
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()
        // Swipe up / down: the next / last page.
        .pointerInput(Unit) {
            var acc = 0f
            detectVerticalDragGestures(onDragStart = { acc = 0f }) { ch, dy ->
                ch.consume(); acc += dy
                val step = 80.dp.toPx()
                val p = latestFocus / per
                if (acc <= -step) { acc = 0f; val np = ((p + 1) * per).coerceAtMost(latestCount - 1); if (np / per != p) { select(np); bbSound(KitSound.PAGE, UiSound.MOVE) } }
                if (acc >= step) { acc = 0f; if (p > 0) { select((p - 1) * per); bbSound(KitSound.PAGE, UiSound.MOVE) } }
            }
        }
    ) {
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val areaW = wPx * 0.94f
        val shiftX = (wPx - areaW) * 0.35f
        // The pages near the one you're on (the one sliding in and out too).
        val pv = pageAnim.value
        for (p in (pv.toInt() - 1).coerceAtLeast(0)..(pv.toInt() + 1).coerceAtMost(pages - 1)) {
            val dy = (p - pv) * hPx
            if (abs(dy) >= hPx) continue
            for (s in 0 until per) {
                val i = p * per + s
                if (i >= count) break
                val gg = c.games[i]
                val (x, y, d) = bubbleSpot(s, cols, areaW, hPx)
                key(i) {
                    val on = i == focus
                    BubbleArt(c, gg, on, d, i, clock, Modifier.offset { IntOffset((x - d / 2f + shiftX).roundToInt(), (y - d / 2f + dy).roundToInt()) }.zIndex(if (on) 2f else 1f)
                        .pointerInput(i) { detectTapGestures { if (latestFocus == i) bubblesOpenLive() else { select(i); bbSound(KitSound.MOVE, UiSound.MOVE) } } })
                }
            }
        }
        // The page dots down the right.
        if (pages > 1) Column(Modifier.align(Alignment.CenterEnd).padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            for (p in 0 until pages.coerceAtMost(14)) Box(
                Modifier.size(if (p == page) 9.dp else 7.dp).clip(CircleShape)
                    .background(if (p == page) Color.White else Color.White.copy(alpha = 0.4f))
                    .pointerInput(p) { detectTapGestures { select((p * per).coerceAtMost(latestCount - 1)); bbSound(KitSound.PAGE, UiSound.MOVE) } }
            )
        }
    }
}

/** One glossy bubble with the game's art, floating; the one you're on swells and bobs, ringed in light. */
@Composable
private fun BubbleArt(c: StyleCtx, g: Game, on: Boolean, dPx: Float, i: Int, clock: androidx.compose.runtime.FloatState, modifier: Modifier) {
    val density = LocalDensity.current
    val d = with(density) { dPx.toDp() }
    val grow by animateFloatAsState(if (on) 1.16f else 1f, spring(dampingRatio = 0.42f, stiffness = 380f), label = "bvGrow")
    Box(modifier.size(d).graphicsLayer {
        val t = clock.floatValue
        translationY = sin(t * 1.3f + i * 0.9f) * (if (on) 4f else 2.5f) * density.density
        scaleX = grow; scaleY = grow
    }) {
        // Its soft shadow.
        Canvas(Modifier.fillMaxSize()) {
            drawOval(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.28f), Color.Transparent), center = Offset(size.width / 2f, size.height * 1.02f), radius = size.width * 0.5f),
                Offset(size.width * 0.12f, size.height * 0.9f), Size(size.width * 0.76f, size.height * 0.2f))
            if (on) drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0f)), radius = size.width * 0.66f), size.width * 0.66f)
        }
        Box(Modifier.fillMaxSize().clip(CircleShape).background(placeholderBrush(g.hue))) {
            val cv = c.cover(g)
            if (g.sourceType == com.shelf.launcher.data.SourceType.APP) AppIconArt(g.packageName, wide = false, modifier = Modifier.fillMaxSize())
            else if (cv != null) AsyncImage(rememberStyleArtRequest(cv, trim = true), g.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text(LocalTitleOptions.current.display(g.name), color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 3, modifier = Modifier.align(Alignment.Center).padding(8.dp))
        }
        // The gloss: a bright cap, a rim, a darker lower edge.
        Canvas(Modifier.fillMaxSize()) {
            val r = size.width / 2f
            drawCircle(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.22f))), r)
            drawOval(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.62f), Color.White.copy(alpha = 0.05f)), startY = size.height * 0.05f, endY = size.height * 0.48f),
                Offset(size.width * 0.17f, size.height * 0.05f), Size(size.width * 0.66f, size.height * 0.42f))
            drawCircle(Color.White.copy(alpha = if (on) 0.95f else 0.7f), r - 1.2f, style = Stroke(if (on) 3.2f else 2f))
        }
        if (g.stableKey in c.state.favorites) Icon(Icons.Filled.Star, null, tint = Color(0xFFFFD34A), modifier = Modifier.align(Alignment.TopEnd).size(16.dp))
    }
}

@Composable
private fun BubbleFolders(c: StyleCtx, cols: Int, systems: List<Int>) {
    val per = 3 * cols - 1
    val n = systems.size
    if (n == 0) { StyleEmpty(); return }
    val focus = BubbleNav.folder.coerceIn(0, n - 1)
    val page = focus / per
    val density = LocalDensity.current
    val context = LocalContext.current
    val counts = remember(c.state.games) { c.state.games.groupingBy { it.platform }.eachCount() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fullW = with(density) { maxWidth.toPx() }
        val wPx = fullW * 0.94f
        val shiftX = (fullW - wPx) * 0.35f
        val hPx = with(density) { maxHeight.toPx() }
        for (s in 0 until per) {
            val i = page * per + s
            if (i >= n) break
            val st = c.stops.getOrNull(systems[i]) ?: continue
            val (x, y, dPx) = bubbleSpot(s, cols, wPx, hPx)
            val d = with(density) { dPx.toDp() }
            val on = i == focus
            key(i) {
                val grow by animateFloatAsState(if (on) 1.14f else 1f, spring(dampingRatio = 0.45f, stiffness = 380f), label = "bvFold")
                val platform = st.platform?.takeIf { st.special == null && !com.shelf.launcher.data.isCollectionKey(it) }
                val photo by produceState<String?>(null, platform) {
                    value = if (platform != null && com.shelf.launcher.data.ConsolePhotos.hasPhoto(platform)) runCatching { com.shelf.launcher.data.ConsolePhotos.url(context, platform) }.getOrNull() else null
                }
                Box(Modifier.offset { IntOffset((x - dPx / 2f + shiftX).roundToInt(), (y - dPx / 2f).roundToInt()) }.size(d).graphicsLayer { scaleX = grow; scaleY = grow }
                    .pointerInput(i) { detectTapGestures { if (BubbleNav.folder == i) bubblesPickFolder(c, i) else { BubbleNav.folder = i; bbSound(KitSound.MOVE, UiSound.MOVE) } } }) {
                    Box(Modifier.fillMaxSize().clip(CircleShape).background(Brush.verticalGradient(listOf(Color(0xFFF4F8FC), Color(0xFFC9D6E4)))), contentAlignment = Alignment.Center) {
                        when {
                            photo != null -> AsyncImage(photo, st.label, Modifier.fillMaxSize(0.78f), contentScale = ContentScale.Fit)
                            platform != null -> PlatformLogo(platform, st.label, Modifier.fillMaxSize(0.66f), textColor = Color(0xFF223040), blackToWhite = false)
                            else -> Icon(st.icon, null, tint = Color(0xFF223040), modifier = Modifier.size(d * 0.4f))
                        }
                    }
                    Canvas(Modifier.fillMaxSize()) {
                        drawOval(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0f)), endY = size.height * 0.45f),
                            Offset(size.width * 0.17f, size.height * 0.05f), Size(size.width * 0.66f, size.height * 0.4f))
                        drawCircle(Color.White.copy(alpha = if (on) 0.95f else 0.7f), size.width / 2f - 1.2f, style = Stroke(if (on) 3.2f else 2f))
                    }
                    // The folder tab and its count.
                    Row(Modifier.align(Alignment.BottomEnd).clip(RoundedCornerShape(50)).background(Color(0xFF2F86E0)).padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Folder, null, tint = Color.White, modifier = Modifier.size(11.dp))
                        st.platform?.let { p -> counts[p]?.let { Text(" $it", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) } }
                    }
                }
            }
        }
    }
}

// ---- LiveArea ----

/**
 * The page with its top-right corner peeled back by [k] (0 = flat, 1 = gone): what's left of the page (the rect cut along
 * the fold line x − y = w − k·L).
 */
private fun peelShape(k: Float) = GenericShape { size, _ ->
    val l = (size.width + size.height) * k
    val c = size.width - l
    // Rect ∩ {x − y ≤ c}, walked round its corners.
    val pts = listOf(Offset(0f, 0f), Offset(size.width, 0f), Offset(size.width, size.height), Offset(0f, size.height))
    val out = ArrayList<Offset>()
    for (j in pts.indices) {
        val a = pts[j]; val b = pts[(j + 1) % pts.size]
        val ia = a.x - a.y <= c; val ib = b.x - b.y <= c
        if (ia) out += a
        if (ia != ib) {
            val t = (c - (a.x - a.y)) / ((b.x - b.y) - (a.x - a.y))
            out += Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
        }
    }
    if (out.size >= 3) { moveTo(out[0].x, out[0].y); for (q in 1 until out.size) lineTo(out[q].x, out[q].y); close() }
}

@Composable
private fun LiveAreaPage(c: StyleCtx, g: Game, open: Float, active: Boolean) {
    val density = LocalDensity.current
    val reduced = !rememberAnimationsEnabled()
    // Idle: a small dog-ear. Closing: the peel runs to the end.
    val ear = 0.045f
    val peel = ear + (1f - open) * (1f - ear)
    // The page slides sideways when you go to the next game's LiveArea.
    val slide = remember { Animatable(0f) }
    LaunchedEffect(g.stableKey) {
        if (reduced || !active) return@LaunchedEffect
        slide.snapTo(BubbleNav.liveDir.toFloat())
        slide.animateTo(0f, tween(360, easing = FastOutSlowInEasing))
    }
    var dragPeel by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp)) {
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val k = (peel + dragPeel).coerceIn(0f, 1f)
        val compactPage = maxHeight < 360.dp
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                // Unfolding: it swings down from the top edge as it opens.
                rotationX = (1f - open) * -38f
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                cameraDistance = 14f * density.density
                translationX = slide.value * size.width * 0.35f
                alpha = (open * 1.6f).coerceIn(0f, 1f) * (1f - abs(slide.value) * 0.6f)
            }
        ) {
            Box(Modifier.fillMaxSize().clip(peelShape(k))) { LiveAreaBody(c, g, compactPage) }
            // The flap: the corner folded back, its underside lit near the fold, shaded at its tip.
            Canvas(Modifier.fillMaxSize()) {
                val l = (size.width + size.height) * k
                val ax = size.width - l
                val a = Offset(ax, 0f); val b = Offset(size.width, l); val cc = Offset(ax, l)
                val flap = Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(cc.x, cc.y); close() }
                // Clip the flap to the page.
                clipRect(0f, 0f, size.width, size.height) {
                    drawPath(flap, Color.Black.copy(alpha = 0.25f), style = Stroke(6f))
                    drawPath(flap, Brush.linearGradient(listOf(Color(0xFFFDFDFD), Color(0xFFD9DEE4), Color(0xFFA9B3BE)), start = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f), end = cc))
                }
            }
            // The corner to peel by hand: tap it, or drag it in (let go past a fifth of the way: it peels off).
            val span by rememberUpdatedState(wPx + hPx)
            Box(Modifier.align(Alignment.TopEnd).size(72.dp)
                .pointerInput(Unit) { detectTapGestures { if (BubbleNav.view == BubbleNav.View.LIVE) bubblesBack() } }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = { if (dragPeel > 0.18f && BubbleNav.view == BubbleNav.View.LIVE) bubblesBack() else dragPeel = 0f },
                        onDragCancel = { dragPeel = 0f }
                    ) { ch, dx -> ch.consume(); dragPeel = (dragPeel - dx / span).coerceIn(0f, 0.9f) }
                })
        }
        LaunchedEffect(active) { if (!active) dragPeel = 0f }
    }
}

@Composable
private fun LiveAreaBody(c: StyleCtx, g: Game, compact: Boolean) {
    val titles = LocalTitleOptions.current
    val l = BubbleNav.live
    val shots = c.state.screenshots[g.stableKey].orEmpty()
    Box(Modifier.fillMaxSize().background(Color(0xFF0D1520))) {
        // Layer 1: the game's big art, then a wash so the controls read.
        (c.backdrop(g) ?: c.wide(g))?.let { AsyncImage(rememberStyleArtRequest(it), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.72f), 0.55f to Color.Black.copy(alpha = 0.25f), 1f to Color.Black.copy(alpha = 0.45f))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))))
        Row(Modifier.fillMaxSize().padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 14.dp)) {
            // Left: its bubble, name, Start and the round buttons.
            Column(Modifier.weight(0.52f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(if (compact) 48.dp else 64.dp).clip(CircleShape).background(placeholderBrush(g.hue)).border(2.dp, Color.White, CircleShape)) {
                        val cv = c.cover(g)
                        if (g.sourceType == com.shelf.launcher.data.SourceType.APP) AppIconArt(g.packageName, wide = false, modifier = Modifier.fillMaxSize())
                        else if (cv != null) AsyncImage(rememberStyleArtRequest(cv, trim = true), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(titles.display(g.name), color = Color.White, fontSize = if (compact) 18.sp else 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = if (compact) 20.sp else 27.sp)
                        Text(if (g.sourceType == com.shelf.launcher.data.SourceType.APP) "Android" else titles.console(g.platform), color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, maxLines = 1)
                    }
                }
                Spacer(Modifier.weight(1f))
                // The big Start: a glossy white plate, lit when it's the one you're on.
                val startOn = l == 0
                val glow by animateFloatAsState(if (startOn) 1f else 0f, tween(180), label = "laStart")
                Box(
                    Modifier.width(if (compact) 170.dp else 220.dp).height(if (compact) 50.dp else 64.dp)
                        .graphicsLayer { val s = 1f + 0.04f * glow; scaleX = s; scaleY = s }
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFE2E8EF))))
                        .border(if (startOn) 3.dp else 1.dp, if (startOn) Color(0xFF39A9FF) else Color.White.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .pointerInput(Unit) { detectTapGestures { bubblesLiveAct(c, 0) } },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Start", color = Color(0xFF1A2633), fontSize = if (compact) 20.sp else 26.sp, fontWeight = FontWeight.Bold)
                    Box(Modifier.fillMaxWidth().fillMaxHeight(0.45f).align(Alignment.TopCenter).background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.7f), Color.Transparent))))
                }
                Spacer(Modifier.height(if (compact) 10.dp else 16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    LiveRound(Icons.Filled.EmojiEvents, "Achievements", l == 1, compact) { bubblesLiveAct(c, 1) }
                    LiveRound(Icons.Filled.Info, "Details", l == 2, compact) { bubblesLiveAct(c, 2) }
                    LiveRound(Icons.Filled.Settings, "Options", l == 3, compact) { bubblesLiveAct(c, 3) }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    when (l) { 1 -> "Achievements"; 2 -> "Details"; 3 -> "Options"; else -> "◀ ▶ other games   ▼ more   B back" },
                    color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1
                )
            }
            Spacer(Modifier.width(18.dp))
            // Right: screenshots, achievements and facts, as framed panels.
            Column(Modifier.weight(0.48f).fillMaxHeight().padding(top = 38.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (shots.isNotEmpty()) Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    shots.take(if (compact) 2 else 3).forEach { s ->
                        // (Shown once its picture is in: no empty frames while it loads, none for a picture that fails.)
                        var loaded by remember(s) { mutableStateOf(false) }
                        val shown by animateFloatAsState(if (loaded) 1f else 0f, tween(220), label = "laShot")
                        Box(Modifier.weight(1f).fillMaxHeight().graphicsLayer { alpha = shown }.clip(RoundedCornerShape(6.dp)).border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(6.dp))) {
                            AsyncImage(rememberStyleArtRequest(s), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, onSuccess = { loaded = true })
                        }
                    }
                }
                LiveInfoPanel(c, g, compact)
            }
        }
    }
}

@Composable
private fun LiveRound(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, on: Boolean, compact: Boolean, onTap: () -> Unit) {
    val s by animateFloatAsState(if (on) 1.12f else 1f, spring(dampingRatio = 0.5f), label = "laRound")
    Box(
        Modifier.size(if (compact) 40.dp else 50.dp).graphicsLayer { scaleX = s; scaleY = s }.clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.95f), Color(0xFFCFD9E4))))
            .border(if (on) 3.dp else 1.dp, if (on) Color(0xFF39A9FF) else Color.White, CircleShape)
            .pointerInput(label) { detectTapGestures { onTap() } },
        contentAlignment = Alignment.Center
    ) { Icon(icon, label, tint = Color(0xFF26364A), modifier = Modifier.size(if (compact) 20.dp else 24.dp)) }
}

@Composable
private fun LiveInfoPanel(c: StyleCtx, g: Game, compact: Boolean) {
    val meta = c.state.metadata[g.stableKey]
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.9f)).padding(12.dp)
    ) {
        c.progress(g)?.let { (e, t) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.EmojiEvents, null, tint = Color(0xFFE0A400), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("$e / $t", color = Color(0xFF1A2633), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFDCE3EA))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth((e.toFloat() / t).coerceIn(0f, 1f)).background(Color(0xFF39A9FF)))
                }
            }
            val list = c.state.raAchievementDetails[g.stableKey].orEmpty()
            if (list.isNotEmpty() && !compact) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (list.filter { it.isUnlocked } + list.filter { !it.isUnlocked }).take(7).forEach { a ->
                        val url = com.shelf.launcher.data.RaBadges.url(a.badgeName)
                        Box(Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFDCE3EA))) {
                            if (url != null) AsyncImage(url, a.title, Modifier.fillMaxSize().graphicsLayer { alpha = if (a.isUnlocked) 1f else 0.35f }, contentScale = ContentScale.Crop)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        @Composable
        fun line(label: String, value: String?) {
            if (value.isNullOrBlank()) return
            Row { Text(label, color = Color(0xFF5B6B7C), fontSize = 12.sp, modifier = Modifier.width(86.dp)); Text(value, color = Color(0xFF1A2633), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        line("Played", stylePlaytime(c.playtime(g), c.lastPlayed(g) != null))
        line("Last played", c.lastPlayed(g)?.let { lastPlayedLabel(it) })
        line("Released", meta?.releaseYear?.toString())
        if (!compact) line("Made by", meta?.developer)
    }
}

// ---- Options ----

/** Update 344: Settings › Home › Layout options (Bubble home). Each its own "bv_…" Features pref. */
@Composable
internal fun BubblesSettings() {
    MutedNote(
        "Your games as glossy bubbles, a page at a time; A unfolds the game's LiveArea with its big Start button, B peels it " +
            "back. ◀ ▶ along, ▲ ▼ between the rows and pages; in the LiveArea ◀ ▶ the next game, ▼ its round buttons. Tap a " +
            "bubble to pick it, again to open it; swipe up / down for the pages; drag or tap the page's corner to peel it."
    )
    Spacer(Modifier.height(10.dp))
    StyleChoice("Opens on", Features.BvOpens.values(), Features.bvOpens.state.value, { it.label }) { Features.bvOpens.value = it }
    StyleChoice("Wallpaper", Features.BvWall.values(), Features.bvWall.state.value, { it.label }) { Features.bvWall.value = it }
    Toggle(Icons.Filled.Folder, "Folders", "B on the bubbles shows your consoles as folder bubbles; A opens one. Off: B is the home screen's own.", Features.bvFolders)
    Toggle(Icons.Filled.Wallpaper, "Floating", "The bubbles bob gently and the wallpaper's lights drift. Off: still.", Features.bvFloat)
    Toggle(Icons.Filled.QueueMusic, "Interface sounds", "Soft pops and blooms of its own. Off: Voltcap's usual sounds.", Features.bvSounds)
    if (Features.bvSounds.state.value) {
        PercentSlider("Sound volume", Features.kitVolume.state.value, { Features.kitVolume.value = it.coerceIn(0, 100) })
        Spacer(Modifier.height(6.dp))
    }
}
