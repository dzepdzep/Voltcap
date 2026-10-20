package com.shelf.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.HotkeyAction
import com.shelf.launcher.data.NameBackdrop
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * (Update 239, #93: Cube menu — an OPTIONAL home layout of its own (HomeLayout.CUBE). Nothing of the other layouts
 * changes; every setting is its own "cube_…" Features pref.)
 *
 * Inspired by the feel of the 2001 cube console's menu — a floating translucent cube in a calm dark indigo room — with
 * Voltcap's own drawing only (no one's logos, art or sounds). The cube is a HOME / CATEGORY NAVIGATOR, never the games:
 *
 * - HOME (the centerpiece): the cube big on the left (on top in portrait), floating a little above its shadow; its four
 *   sides are your categories (the one you're in faces you), its top face Apps and its bottom face Settings. ◀ ▶ turn it
 *   to the next category, ▲ ▼ tip it to Apps / Settings, A opens the face turned to you. The face's name, its game count
 *   and the buttons are in a caption beside the cube (never on it alone). B: the home screen's own (the category screen).
 * - LIBRARY: A on a category face shrinks the cube into a reserved dock — a column on the left, a band on top, or hidden
 *   behind a category chip when the screen is too small — while the games slide into the rest of the screen: a spacious
 *   row of the category's games in your game look (StyleArt), the game's name, an info line and Play / Details / Cube.
 *   The cube is drawn UNDER the games and only inside its dock, so it never covers a game, a title or a button.
 *   ◀ ▶ the games (the home screen's own: Endless works), ▼ the buttons, ▲ the docked cube (◀ ▶ / ▲ ▼ there turn it to
 *   the next category, A / B / the way back return to the games), L2 / R2 change the category (the cube turns with it),
 *   B (or the back gesture) brings the big cube back.
 * - Touch: tap a cube face (the one facing you opens it, a side one turns to it), drag the cube to turn / tip it; tap a
 *   game to pick it, tap it again to open it; tap the docked cube (or the chip) for the big cube again.
 * Motion: the turn is a short spring that never queues (a new press re-aims it); the gentle idle float rests when the
 * home screen goes calm (HomeIdle.calm), on Reduced motion and with Android's animations off. The cube is one small
 * Canvas read in the draw phase only (no recomposition per frame); the dock move is a layout-phase offset.
 * Options: Settings › Home › Layout options (Cube menu), StyleHomeSettings.kt › CubeSettings.
 */

/** (Update 239, #93: where the controller is on the Cube menu — kept while you play a game and come back.) */
internal object CubeNav {
    enum class Page { HOME, LIBRARY }
    var page by mutableStateOf(Page.HOME)
    /** The ring face the cube is turned to: a virtual index that counts on past the ends (the category is it mod the
     *  number of categories), so every turn goes the short way and never spins back round. */
    var ring by mutableIntStateOf(0)
    /** -1: the top face (Apps) turned to you, 1: the bottom face (Settings), 0: a category face. */
    var tip by mutableIntStateOf(0)
    @Volatile var ctx: StyleCtx? = null
    /** The dock as last laid out (after fitting it to the screen), for the keys. */
    @Volatile var dock: Features.CubeDock = Features.CubeDock.LEFT
    /** Cube home screen on (Layout options): B on the games goes back to the big cube. */
    @Volatile var homeOn = true
    internal val keysDown = HashSet<Int>()
    /** Everything the controller moves here, for the "▼ changed nothing" check (LauncherScreen, second screen). */
    fun signature(): Any = listOf(page, ring, tip, CubeHub.section, CubeHub.zone, CubeHub.sysFocus)
}

internal fun cubeSound(s: UiSound) { PocketSounds.play?.invoke(s) }

/** ◀ ▶ on the big cube: one face along the ring (the category itself changes on A). */
private fun cubeTurn(c: StyleCtx, d: Int, sound: (UiSound) -> Unit) {
    if (CubeNav.tip != 0) { CubeNav.tip = 0; sound(UiSound.MOVE); return }
    if (c.stops.size <= 1) return
    CubeNav.ring += d
    sound(UiSound.MOVE)
}

/** ▲ (-1) / ▼ (1) on the big cube: tips it toward its top / bottom face. False: nothing more that way. */
private fun cubeTip(d: Int, sound: (UiSound) -> Unit): Boolean {
    val next = CubeNav.tip + d
    if (next < -1 || next > 1) return false
    if (next != 0 && !Features.cubeExtraFaces.value) return false
    CubeNav.tip = next
    sound(UiSound.MOVE)
    return true
}

internal fun cubeApps(c: StyleCtx) {
    val h = GreenCoreHooks.hotkey
    if (h != null) h(HotkeyAction.APP_DRAWER) else c.shortcuts.firstOrNull { it.label == "All apps" }?.onClick?.invoke()
}

/** A on the big cube (or a tap on the face turned to you): Apps, Settings, or that category's games. */
internal fun cubeOpenFace(c: StyleCtx, sound: (UiSound) -> Unit) {
    when (CubeNav.tip) {
        -1 -> { sound(UiSound.OPEN); cubeApps(c) }
        1 -> c.actions.settings()
        else -> {
            val n = c.stops.size
            if (n > 0) {
                val steps = pocketStepsTo(c, CubeNav.ring.mod(n))
                if (steps != 0) c.actions.category(steps)
            }
            StyleNav.reset()
            CubeNav.page = CubeNav.Page.LIBRARY
            sound(UiSound.OPEN)
        }
    }
}

/** B on the games / the back gesture / the Cube button: the big cube again (when the cube home is on). */
internal fun cubeGoHome(sound: (UiSound) -> Unit) {
    if (!CubeNav.homeOn || CubeNav.page == CubeNav.Page.HOME) return
    StyleNav.reset()
    CubeNav.tip = 0
    CubeNav.page = CubeNav.Page.HOME
    sound(UiSound.BACK)
}

/**
 * (Update 239, #93: the Cube menu's keys — all of them while it's on, before the home screen's own. True = handled.)
 * HOME: ◀ ▶ turn, ▲ ▼ tip (then past the cube: HomeReach), A opens the face, X / Y wait for the games; B, L / R, Start,
 * Select are the home screen's own (L2 / R2 change the category and the cube turns with it).
 * LIBRARY: see the comment at the top of the file.
 */
internal fun cubeKey(event: KeyEvent, reach: (Boolean) -> Boolean, sound: (UiSound) -> Unit): Boolean {
    // 0.6.17: the Hub look has its own keys (CubeHub.kt).
    if (CubeHub.active) return cubeHubKey(event, reach, sound)
    val c = CubeNav.ctx ?: return false
    val code = event.nativeKeyEvent.keyCode
    val down = event.type == KeyEventType.KeyDown
    val up = event.type == KeyEventType.KeyUp
    if (up && CubeNav.keysDown.remove(code)) return true
    val k = event.key
    val first = event.nativeKeyEvent.repeatCount == 0
    val isA = k == Key.ButtonA || k == Key.Enter || k == Key.DirectionCenter
    val isB = k == Key.ButtonB || k == Key.Back
    val isDir = k == Key.DirectionLeft || k == Key.DirectionRight || k == Key.DirectionUp || k == Key.DirectionDown
    fun take(): Boolean { CubeNav.keysDown += code; return true }
    val home = CubeNav.homeOn && CubeNav.page == CubeNav.Page.HOME
    if (home) {
        // A acts on release (what it opens never gets the release); its press is the cube's too (no game window).
        if (isA) {
            if (up && !event.nativeKeyEvent.isCanceled) cubeOpenFace(c, sound)
            return true
        }
        if (!down) return false
        return when (k) {
            Key.DirectionLeft -> { cubeTurn(c, -1, sound); take() }
            Key.DirectionRight -> { cubeTurn(c, 1, sound); take() }
            Key.DirectionUp -> { if (!cubeTip(-1, sound) && first) reach(true); take() }
            Key.DirectionDown -> { if (!cubeTip(1, sound) && first) reach(false); take() }
            // No game is shown on the big cube: X (favourite) / Y (achievements) wait for the games.
            Key.ButtonX, Key.ButtonY -> take()
            else -> false
        }
    }
    val zone = StyleNav.zone
    if (zone == StyleNav.Zone.TOP && StyleNav.top.isNotEmpty()) {
        // The docked cube (or the category chip): turn it to the next category; A / B / the way back: the games.
        if (isA || isB) {
            if (up) { StyleNav.leave(); sound(if (isA) UiSound.SELECT else UiSound.BACK) }
            return true
        }
        if (!isDir) return false
        if (!down) return true
        val left = CubeNav.dock == Features.CubeDock.LEFT
        val prev = if (left) Key.DirectionUp else Key.DirectionLeft
        val next = if (left) Key.DirectionDown else Key.DirectionRight
        val back = if (left) Key.DirectionRight else Key.DirectionDown
        when (k) {
            prev, next -> if (first && c.stops.size > 1) { c.actions.category(if (k == next) 1 else -1); sound(UiSound.MOVE) }
            back -> if (first) { StyleNav.leave(); sound(UiSound.MOVE) }
            Key.DirectionUp -> if (first) reach(true)
            else -> Unit
        }
        return take()
    }
    if (zone == StyleNav.Zone.BAR && StyleNav.bar.isNotEmpty()) {
        val items = StyleNav.bar
        if (isA) {
            if (up && event.nativeKeyEvent.downTime >= StyleNav.enteredAt) { sound(UiSound.OPEN); items.getOrNull(StyleNav.index)?.invoke() }
            return true
        }
        if (isB) {
            if (up) { StyleNav.leave(); sound(UiSound.BACK) }
            return true
        }
        if (!isDir) return false
        if (!down) return true
        when (k) {
            Key.DirectionLeft, Key.DirectionRight -> {
                val nextI = (StyleNav.index + if (k == Key.DirectionRight) 1 else -1).coerceIn(0, items.lastIndex)
                if (nextI != StyleNav.index) { StyleNav.index = nextI; sound(UiSound.MOVE) }
            }
            Key.DirectionUp -> if (first) { StyleNav.leave(); sound(UiSound.MOVE) }
            Key.DirectionDown -> if (first) reach(false)
            else -> Unit
        }
        return take()
    }
    if (zone != StyleNav.Zone.GAMES) StyleNav.reset()
    // On the games.
    if (isB) {
        if (!CubeNav.homeOn) return false
        if (down && first) cubeGoHome(sound)
        return take()
    }
    if (!isDir || !down) return false
    val at = event.nativeKeyEvent.eventTime
    return when (k) {
        Key.DirectionUp -> {
            if (first && !StyleNav.enter(StyleNav.Zone.TOP, at).also { if (it) sound(UiSound.MOVE) }) reach(true)
            take()
        }
        Key.DirectionDown -> {
            if (first && !StyleNav.enter(StyleNav.Zone.BAR, at).also { if (it) sound(UiSound.MOVE) }) reach(false)
            take()
        }
        // ◀ ▶: the home screen's own (one game; Endless goes round).
        else -> false
    }
}

// ---- The layout ----

/** (Update 239, #93; 0.6.17: the Hub look on wide screens — CubeHub.kt.) */
@Composable
internal fun StyleCube(c: StyleCtx, modifier: Modifier = Modifier) {
    val hub = Features.cubeLook.state.value == Features.CubeLook.HUB
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (hub && maxWidth >= 600.dp && maxWidth >= maxHeight * 1.25f) CubeHub(c, Modifier.fillMaxSize())
        else StyleCubeClassic(c, Modifier.fillMaxSize())
    }
}

/** (Update 239, #93: the Classic look — the cube of categories.) */
@Composable
private fun StyleCubeClassic(c: StyleCtx, modifier: Modifier = Modifier) {
    val homeOn = Features.cubeHome.state.value
    val dockPref = Features.cubeDock.state.value
    val extra = Features.cubeExtraFaces.state.value
    val nameOnly = Features.cubeFace.state.value == Features.CubeFace.NAME
    val tintPref = Features.cubeTint.state.value
    val sizeK = Features.cubeSize.state.value / 100f
    val reduced = Features.cubeReducedMotion.state.value || !rememberAnimationsEnabled()
    val lite = LocalLiteEffects.current
    val active = rememberIsActive()
    val windowOpen = GamePopup.game != null
    val n = c.stops.size
    val page = if (homeOn) CubeNav.page else CubeNav.Page.LIBRARY
    val accent = ShelfTheme.accent
    val tint = if (tintPref == Features.CubeTint.THEME) accent else Color(tintPref.argb)
    val context = LocalContext.current
    val density = LocalDensity.current
    val live by rememberUpdatedState(c)
    // The cube (or its caption / dock label / chip) always shows the category: the layout's own title.
    CategoryTitle.Native()

    LaunchedEffect(homeOn) { if (!homeOn) CubeNav.page = CubeNav.Page.LIBRARY }
    LaunchedEffect(extra) { if (!extra) CubeNav.tip = 0 }
    // The cube follows the category however it changed (L2 / R2, the category screen…): the short way round.
    LaunchedEffect(c.stop, n) {
        if (c.stop >= 0 && n > 0) {
            var d = (c.stop - CubeNav.ring).mod(n)
            if (d > n / 2) d -= n
            if (d != 0) CubeNav.ring += d
        }
    }
    androidx.activity.compose.BackHandler(enabled = homeOn && page == CubeNav.Page.LIBRARY && !windowOpen) { cubeGoHome(::cubeSound) }
    DisposableEffect(Unit) { onDispose { CubeNav.keysDown.clear(); CubeNav.ctx = null } }

    // Motion: one target each, re-aimed by every press (never queued); a far jump snaps next to it first.
    val ringAnim = remember { Animatable(CubeNav.ring.toFloat()) }
    val ringTarget = CubeNav.ring
    LaunchedEffect(ringTarget, reduced) {
        val t = ringTarget.toFloat()
        if (reduced) { ringAnim.snapTo(t); return@LaunchedEffect }
        if (abs(ringAnim.value - t) > 1.5f) ringAnim.snapTo(t + if (ringAnim.value < t) -1f else 1f)
        ringAnim.animateTo(t, spring(dampingRatio = 0.86f, stiffness = 240f))
    }
    val tipAnim = remember { Animatable(CubeNav.tip.toFloat()) }
    val tipTarget = CubeNav.tip
    LaunchedEffect(tipTarget, reduced) {
        if (reduced) tipAnim.snapTo(tipTarget.toFloat())
        else tipAnim.animateTo(tipTarget.toFloat(), spring(dampingRatio = 0.86f, stiffness = 240f))
    }
    val pageAnim = remember { Animatable(if (page == CubeNav.Page.LIBRARY) 1f else 0f) }
    LaunchedEffect(page, reduced) {
        val t = if (page == CubeNav.Page.LIBRARY) 1f else 0f
        if (reduced) pageAnim.snapTo(t) else pageAnim.animateTo(t, tween(440, easing = FastOutSlowInEasing))
    }
    // Composed only while they can be seen (flips once per page change, not every frame of the move).
    val libShown by remember { derivedStateOf { pageAnim.value > 0.001f } }
    val homeShown by remember { derivedStateOf { pageAnim.value < 0.999f } }
    // The gentle idle float: on the big cube only, resting when the home screen is calm / hidden / a window is open.
    val idleOn = Features.cubeIdle.state.value && !reduced && active && !windowOpen && !HomeIdle.calm && page == CubeNav.Page.HOME
    val clock = rememberGcClock(idleOn, 1f, if (lite) 30 else 60)
    val idleAmp = animateFloatAsState(if (idleOn) 1f else 0f, tween(if (idleOn) 1200 else 700), label = "cubeIdle")

    // The faces' pictures: the console's logo baked in the app (PlatformLogos.baked), else the category's symbol.
    val icons = remember(c.stops) { (c.stops.map { it.icon } + listOf(Icons.Filled.Apps, Icons.Filled.Settings)).distinct() }
    val painters = HashMap<ImageVector, Painter>(icons.size * 2)
    for (iv in icons) key(iv) { painters[iv] = rememberVectorPainter(iv) }
    val logos = remember { HashMap<String, ImageBitmap?>() }
    val stops = c.stops
    val fallbackLabel = c.categoryLabel
    val artFor: (Int) -> CubeFaceArt = { k ->
        if (stops.isEmpty()) CubeFaceArt(fallbackLabel, null, painters[Icons.Filled.Apps])
        else {
            val st = stops[k.mod(stops.size)]
            val p = st.platform
            val logo = if (!nameOnly && p != null && st.special == null && !com.shelf.launcher.data.isCollectionKey(p)) {
                if (!logos.containsKey(p)) logos[p] = runCatching { PlatformLogos.baked(context, p) }.getOrNull()
                logos[p]
            } else null
            CubeFaceArt(st.label, logo, painters[st.icon])
        }
    }
    val topArt = CubeFaceArt("Apps", null, painters[Icons.Filled.Apps])
    val bottomArt = CubeFaceArt("Settings", null, painters[Icons.Filled.Settings])
    val tools = remember { CubeTools() }
    val hits = remember { CubeHits() }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        val portrait = w < h * 1.05f
        // HOME: the big cube and its caption, side by side (stacked in portrait), never overlapping.
        val homeS: Dp
        val homeCx: Dp
        val homeCy: Dp
        if (portrait) {
            homeS = minOf(w * 0.72f, h * 0.5f) * sizeK
            homeCx = w / 2
            homeCy = h * 0.34f
        } else {
            homeS = minOf(h * 0.8f * sizeK, w * 0.56f)
            homeCx = w * 0.31f
            homeCy = h * 0.5f
        }
        // LIBRARY: the dock the cube shrinks into, fitted to the screen (a column needs width, a band height).
        val cubeOff = LayoutParts.off(HomeLayout.CUBE, "cube")
        val dock = when {
            cubeOff -> Features.CubeDock.HIDDEN
            dockPref == Features.CubeDock.LEFT -> if (w >= 560.dp) Features.CubeDock.LEFT else if (h >= 300.dp) Features.CubeDock.TOP else Features.CubeDock.HIDDEN
            dockPref == Features.CubeDock.TOP -> if (h >= 300.dp) Features.CubeDock.TOP else if (w >= 560.dp) Features.CubeDock.LEFT else Features.CubeDock.HIDDEN
            else -> Features.CubeDock.HIDDEN
        }
        val dockW = (w * 0.22f).coerceIn(124.dp, 210.dp)
        val dockH = (h * 0.26f).coerceIn(84.dp, 132.dp)
        val dockS: Dp = when (dock) {
            Features.CubeDock.LEFT -> minOf(dockW - 20.dp, h * 0.42f)
            Features.CubeDock.TOP -> dockH - 10.dp
            Features.CubeDock.HIDDEN -> 0.dp
        }
        val dockCx: Dp = when (dock) {
            Features.CubeDock.LEFT -> dockW / 2
            Features.CubeDock.TOP -> 6.dp + dockS / 2
            Features.CubeDock.HIDDEN -> 30.dp
        }
        val dockCy: Dp = when (dock) {
            Features.CubeDock.LEFT -> 4.dp + dockS / 2
            Features.CubeDock.TOP -> dockH / 2
            Features.CubeDock.HIDDEN -> 16.dp
        }
        val homePx = with(density) { floatArrayOf(homeCx.toPx(), homeCy.toPx(), homeS.toPx()) }
        val dockPx = with(density) { floatArrayOf(dockCx.toPx(), dockCy.toPx(), dockS.toPx()) }
        val hideDocked = dock == Features.CubeDock.HIDDEN
        fun rectAt(i: Int): Float {
            val t = if (homeOn) pageAnim.value else 1f
            return homePx[i] + (dockPx[i] - homePx[i]) * t
        }
        val g = c.game
        val infoOff = LayoutParts.off(HomeLayout.CUBE, "info")
        val inDock = page == CubeNav.Page.LIBRARY && StyleNav.zone == StyleNav.Zone.TOP
        SideEffect {
            CubeNav.ctx = c
            CubeNav.homeOn = homeOn
            CubeNav.dock = dock
            StyleNav.side = emptyList()
            StyleLoop.on = loopRowOn(c)
            if (page == CubeNav.Page.LIBRARY) {
                // ▲ from the games: the docked cube (or the category chip when it's hidden).
                StyleNav.top = listOf({ StyleNav.leave() })
                // ▼ from the games: Play, Details and (with the cube home) the big cube.
                StyleNav.bar = if (g == null || infoOff) emptyList() else {
                    val gg = g
                    listOfNotNull<() -> Unit>({ live.actions.play(gg) }, { live.actions.open() }, if (homeOn) ({ cubeGoHome(::cubeSound) }) else null)
                }
            } else {
                StyleNav.top = emptyList()
                StyleNav.bar = emptyList()
            }
        }

        // The cube — first, so everything else draws over it (it never covers the games, even mid-move).
        Box(
            Modifier
                .offset { IntOffset((rectAt(0) - rectAt(2) / 2f).roundToInt(), (rectAt(1) - rectAt(2) / 2f).roundToInt()) }
                .layout { m, _ ->
                    val s = rectAt(2).roundToInt().coerceAtLeast(1)
                    val placeable = m.measure(Constraints.fixed(s, s))
                    layout(s, s) { placeable.place(0, 0) }
                }
                .editablePart(LayoutParts.id(HomeLayout.CUBE, "cube"), "Cube")
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        val cc = live
                        val i = hits.find(pos.x, pos.y)
                        if (i < 0) return@detectTapGestures
                        val kind = hits.kinds[i]
                        val kk = hits.ks[i]
                        if (CubeNav.homeOn && CubeNav.page == CubeNav.Page.HOME) {
                            when {
                                kind == 1 && Features.cubeExtraFaces.value -> if (CubeNav.tip == -1) cubeOpenFace(cc, ::cubeSound) else { CubeNav.tip = -1; cubeSound(UiSound.MOVE) }
                                kind == 2 && Features.cubeExtraFaces.value -> if (CubeNav.tip == 1) cubeOpenFace(cc, ::cubeSound) else { CubeNav.tip = 1; cubeSound(UiSound.MOVE) }
                                CubeNav.tip != 0 -> { CubeNav.tip = 0; cubeSound(UiSound.MOVE) }
                                kind == 0 && kk != CubeNav.ring -> { CubeNav.ring = kk; cubeSound(UiSound.MOVE) }
                                else -> cubeOpenFace(cc, ::cubeSound)
                            }
                        } else if (kind == 0 && kk != CubeNav.ring && cc.stops.size > 1) {
                            cc.actions.category(kk - CubeNav.ring)
                            cubeSound(UiSound.MOVE)
                        } else if (CubeNav.homeOn) cubeGoHome(::cubeSound)
                    }
                }
                .pointerInput(Unit) {
                    var ax = 0f
                    var ay = 0f
                    detectDragGestures(onDragStart = { ax = 0f; ay = 0f }) { change, d ->
                        change.consume()
                        val cc = live
                        val step = 44.dp.toPx()
                        val big = CubeNav.homeOn && CubeNav.page == CubeNav.Page.HOME
                        ax += d.x
                        ay += d.y
                        // Dragged left: the face on the right comes round to you (the next category).
                        while (ax <= -step) { ax += step; if (big) cubeTurn(cc, 1, ::cubeSound) else if (cc.stops.size > 1) { cc.actions.category(1); cubeSound(UiSound.MOVE) } }
                        while (ax >= step) { ax -= step; if (big) cubeTurn(cc, -1, ::cubeSound) else if (cc.stops.size > 1) { cc.actions.category(-1); cubeSound(UiSound.MOVE) } }
                        // Dragged up: the bottom face (Settings) comes up to you; down: the top face (Apps).
                        if (big) {
                            while (ay <= -step) { ay += step; cubeTip(1, ::cubeSound) }
                            while (ay >= step) { ay -= step; cubeTip(-1, ::cubeSound) }
                        }
                    }
                }
                .drawBehind {
                    val t = if (homeOn) pageAnim.value else 1f
                    val a = if (hideDocked) 1f - t else 1f
                    if (a <= 0.01f || size.minDimension < 4f) { hits.count = 0; return@drawBehind }
                    val s = size.minDimension
                    val amp = idleAmp.value
                    val tt = clock.floatValue
                    val bob = if (amp > 0f) sin(tt * TWO_PI / 6f) * 0.03f * s * amp else 0f
                    val sway = if (amp > 0f) sin(tt * TWO_PI / 9f) * 4f * amp else 0f
                    val nod = if (amp > 0f) sin(tt * TWO_PI / 7.2f) * 2.2f * amp else 0f
                    val half = s * 0.25f
                    val cx = s / 2f
                    val restY = s * 0.45f
                    cubeShadow(cx, restY + half * 1.75f, half, a * (1f - 0.25f * (bob / (0.03f * s)).coerceIn(-1f, 1f)))
                    val p = ringAnim.value
                    val yaw = Math.toRadians((CUBE_REST_YAW - p * 90f + sway).toDouble()).toFloat()
                    val pitch = Math.toRadians((CUBE_REST_PITCH - tipAnim.value * 90f + nod).toDouble()).toFloat()
                    val front = page == CubeNav.Page.HOME || inDock
                    drawCube(
                        tools, hits, cx, restY - bob, half, yaw, pitch,
                        ringBase = kotlin.math.round(p).toInt(), ringFront = CubeNav.ring, tipFront = CubeNav.tip,
                        art = artFor, top = topArt, bottom = bottomArt, extra = extra,
                        tint = tint, lit = if (front) 1f else 0.35f, alpha = a, nameOnly = nameOnly
                    )
                }
        )

        if (homeOn && homeShown) {
            val capMod = if (portrait) Modifier.offset(x = 16.dp, y = homeCy + homeS / 2 + 6.dp).width(w - 32.dp)
            else Modifier.offset(x = w * 0.6f, y = 0.dp).width(w * 0.4f - 12.dp).height(h)
            Box(capMod.graphicsLayer { alpha = 1f - pageAnim.value }, contentAlignment = if (portrait) Alignment.TopCenter else Alignment.CenterStart) {
                CubeCaption(c, extra, tint, portrait, Modifier.editablePart(LayoutParts.id(HomeLayout.CUBE, "caption"), "Cube caption"))
            }
        }

        if (libShown) {
            val libX = if (dock == Features.CubeDock.LEFT) dockW + 6.dp else 0.dp
            val libY = if (dock == Features.CubeDock.TOP) dockH + 2.dp else 0.dp
            // The dock's label: the category you're in, beside (band) or under (column) the docked cube.
            if (dock != Features.CubeDock.HIDDEN) {
                val labelMod = if (dock == Features.CubeDock.LEFT) Modifier.offset(x = 6.dp, y = 4.dp + dockS + 8.dp).width(dockW - 12.dp)
                else Modifier.offset(x = 6.dp + dockS + 12.dp, y = 0.dp).width((w - dockS - 30.dp).coerceAtLeast(80.dp)).height(dockH)
                Box(
                    labelMod.graphicsLayer { alpha = pageAnim.value },
                    contentAlignment = if (dock == Features.CubeDock.LEFT) Alignment.TopCenter else Alignment.CenterStart
                ) { CubeDockLabel(c, dock == Features.CubeDock.LEFT, inDock, tint) }
            }
            Box(
                Modifier.offset(x = libX, y = libY).width(w - libX).height(h - libY)
                    // (Update 242, #93 device check: the game before the one you're on slid out over the docked cube and its
                    // caption on the Thor — the row is cut at its own left / right edges; above and below stay free for the
                    // focused game's lift.)
                    .drawWithContent { clipRect(0f, -size.height, size.width, size.height * 2f) { this@drawWithContent.drawContent() } }
                    .graphicsLayer {
                        val e = pageAnim.value
                        alpha = e
                        translationX = (1f - e) * 48.dp.toPx()
                    }
            ) {
                CubeBrowser(
                    c, chip = dock == Features.CubeDock.HIDDEN, chipOn = inDock, homeOn = homeOn, tint = tint,
                    modifier = Modifier.fillMaxSize().padding(start = 6.dp, end = 10.dp, top = 2.dp, bottom = 4.dp)
                )
            }
        }
    }
}

/** (Update 239, #93: the big cube's caption — the face turned to you, what A does, the keys.) */
@Composable
private fun CubeCaption(c: StyleCtx, extra: Boolean, tint: Color, portrait: Boolean, modifier: Modifier) {
    val n = c.stops.size
    val tip = CubeNav.tip
    val idx = if (n > 0) CubeNav.ring.mod(n) else -1
    val st = c.stops.getOrNull(idx)
    val here = idx == c.stop || n == 0
    val title = when (tip) { -1 -> "Apps"; 1 -> "Settings"; else -> st?.label ?: c.categoryLabel }
    val gamesNow = c.state.games
    // A console face: its own game count (counted once per face and library, not per frame).
    val plat: String? = st?.platform?.takeIf { st?.special == null && !com.shelf.launcher.data.isCollectionKey(it) }
    val consoleCount = remember(plat, gamesNow) { if (plat == null) -1 else gamesNow.count { it.platform == plat } }
    val sub = when {
        tip == -1 -> "All your Android apps"
        tip == 1 -> "Voltcap's settings"
        here -> "${c.games.size} " + if (c.games.size == 1) "game · you're here" else "games · you're here"
        consoleCount >= 0 -> "$consoleCount " + if (consoleCount == 1) "game" else "games"
        else -> "A category of your games"
    }
    val live by rememberUpdatedState(c)
    Column(modifier, horizontalAlignment = if (portrait) Alignment.CenterHorizontally else Alignment.Start) {
        Text(
            title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = nameTextStyle(NameBackdrop.SHADOW)
        )
        Spacer(Modifier.height(4.dp))
        Text(sub, color = Color.White.copy(alpha = 0.72f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(14.dp))
        CubePill(if (tip == 0) "Open" else "Go", if (tip == 0) Icons.Filled.ViewInAr else if (tip < 0) Icons.Filled.Apps else Icons.Filled.Settings, true, tint) {
            cubeOpenFace(live, ::cubeSound)
        }
        Spacer(Modifier.height(14.dp))
        val hint = buildString {
            append("Ⓐ Open   ◀ ▶ Turn")
            if (extra) append("   ▲ Apps   ▼ Settings")
            append("   L2 / R2 Category")
        }
        Text(hint, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** (Update 239, #93: beside / under the docked cube — the category you're in, its count, how to turn it.) */
@Composable
private fun CubeDockLabel(c: StyleCtx, column: Boolean, on: Boolean, tint: Color) {
    val live by rememberUpdatedState(c)
    val multi = c.stops.size > 1
    Column(horizontalAlignment = if (column) Alignment.CenterHorizontally else Alignment.Start) {
        Text(
            c.categoryLabel, color = if (on) Color.White else Color.White.copy(alpha = 0.9f), fontSize = if (column) 15.sp else 18.sp,
            fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, style = nameTextStyle(NameBackdrop.SHADOW)
        )
        Text(
            "${c.games.size} " + if (c.games.size == 1) "game" else "games", color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp, maxLines = 1
        )
        if (multi) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CubePill(if (column) "▲" else "◀", null, false, tint, small = true) { live.actions.category(-1); cubeSound(UiSound.MOVE) }
                Spacer(Modifier.width(6.dp))
                Text(if (on) "Turn" else "L2 · R2", color = if (on) tint.copy(alpha = 1f) else Color.White.copy(alpha = 0.5f), fontSize = 11.sp, maxLines = 1)
                Spacer(Modifier.width(6.dp))
                CubePill(if (column) "▼" else "▶", null, false, tint, small = true) { live.actions.category(1); cubeSound(UiSound.MOVE) }
            }
        }
    }
}

/**
 * (Update 239, #93: the games — a spacious row of the category's games in your game look, the one you're on bigger,
 * its name, an info line and Play / Details / Cube. [chip]: the cube is hidden — a small category chip stands for it.)
 */
@Composable
private fun CubeBrowser(c: StyleCtx, chip: Boolean, chipOn: Boolean, homeOn: Boolean, tint: Color, modifier: Modifier) {
    val live by rememberUpdatedState(c)
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    val titles = LocalTitleOptions.current
    BoxWithConstraints(modifier) {
        val bw = maxWidth
        val bh = maxHeight
        val compact = bh < 300.dp
        val headerH = 30.dp
        val infoH = if (compact) 84.dp else 112.dp
        val aspect = styleAspect(c.type).coerceIn(0.5f, 1.8f)
        val sizeK = GameSize.scale(HomeLayout.CUBE)
        // As big as the room under the header and above the title allows (the size % never pushes the buttons off).
        val room = (bh - headerH - infoH - 24.dp).coerceAtLeast(56.dp)
        val big = minOf((room * sizeK).coerceIn(56.dp, 280.dp), room, bw * 0.42f / aspect)
        val small = big * 0.7f
        Column(Modifier.fillMaxSize()) {
            // Header: the chip (cube hidden), the place in the category, the category keys.
            Row(Modifier.fillMaxWidth().height(headerH), verticalAlignment = Alignment.CenterVertically) {
                if (chip) {
                    CubePill("◆ ${c.categoryLabel}", null, chipOn, tint, small = true) {
                        if (homeOn) cubeGoHome(::cubeSound) else { live.actions.category(1); cubeSound(UiSound.MOVE) }
                    }
                    Spacer(Modifier.width(10.dp))
                }
                if (c.games.isNotEmpty()) Text("${c.focus + 1} / ${c.games.size}", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1)
                Spacer(Modifier.weight(1f))
                Text(if (homeOn) "Ⓑ Cube   L2 / R2 Category" else "L2 / R2 Category", color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp, maxLines = 1)
            }
            Spacer(Modifier.weight(0.4f))
            if (c.games.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(big + 12.dp)) { StyleEmpty(Modifier.fillMaxSize()) }
            } else {
                val keys = remember(c.games) { gameKeys(c.games) }
                // A steady focus look (no pulse): the row recomposes only when you move.
                val pulse = rememberFocusPulse(animate = false)
                AnchoredStrip(
                    count = c.games.size, focus = rememberLoopFocus(c), keyOf = { keys[it] }, small = small, big = big,
                    widthFor = { it * aspect }, gap = 12.dp, startPad = 10.dp, anchorSlots = 1, alignTop = false,
                    loop = loopRowOn(c),
                    modifier = Modifier.fillMaxWidth().height(big + 12.dp).padding(vertical = 6.dp).categorySlide()
                        .editablePart(LayoutParts.id(HomeLayout.CUBE, "games"), "Games row")
                ) { i, _, gw, gh, here ->
                    val gg = c.games[i]
                    StyleArt(
                        c, gg,
                        Modifier.width(gw).height(gh)
                            .then(if (here) Modifier.focusedCard() else Modifier)
                            .pointerInput(i) { detectTapGestures { if (i == GridHome.focus) open() else select(i) } },
                        focused = here && StyleNav.zone == StyleNav.Zone.GAMES, fx = pulse, ring = Color.White, outline = 3.dp
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            val g = c.game
            if (g != null) {
                Column(
                    Modifier.fillMaxWidth().height(infoH).padding(start = 10.dp)
                        .editablePart(LayoutParts.id(HomeLayout.CUBE, "info"), "Title & buttons")
                ) {
                    Text(
                        titles.display(g.name), color = Color.White, fontSize = if (compact) 16.sp else 20.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, style = nameTextStyle(NameBackdrop.SHADOW)
                    )
                    val last = c.lastPlayed(g)
                    val line = buildString {
                        append(titles.console(g.platform))
                        append(" · ")
                        append(stylePlaytime(c.playtime(g), last != null))
                        if (last != null) {
                            val lp = lastPlayedLabel(last)
                            append(" · Last played ")
                            append(if (lp == "Today" || lp == "Yesterday") lp.lowercase() else lp)
                        }
                    }
                    // (The achievement chip is on the card itself — StyleArt — as in the other style homes.)
                    Text(line, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CubePill("Play", Icons.Filled.PlayArrow, StyleNav.on(StyleNav.Zone.BAR, 0), tint, small = compact) { live.game?.let { live.actions.play(it) } }
                        CubePill("Details", Icons.Filled.Info, StyleNav.on(StyleNav.Zone.BAR, 1), tint, small = compact) { live.actions.open() }
                        if (homeOn) CubePill("Cube", Icons.Filled.ViewInAr, StyleNav.on(StyleNav.Zone.BAR, 2), tint, small = compact) { cubeGoHome(::cubeSound) }
                    }
                }
            }
            Spacer(Modifier.weight(0.6f))
        }
    }
}

/** (Update 239, #93: a round button — lit when the D-pad is on it.) */
@Composable
private fun CubePill(label: String, icon: ImageVector?, on: Boolean, tint: Color, small: Boolean = false, onTap: () -> Unit) {
    val tap by rememberUpdatedState(onTap)
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.clip(shape)
            .background(if (on) tint.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.10f))
            .border(1.dp, if (on) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.22f), shape)
            .pointerInput(Unit) { detectTapGestures { tap() } }
            .padding(horizontal = if (small) 10.dp else 16.dp, vertical = if (small) 4.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(if (small) 13.dp else 16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, color = Color.White, fontSize = if (small) 12.sp else 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** (Update 239, #93: the calm room behind the cube — dark indigo light and a faint floor grid; its option.) */
@Composable
internal fun CubeBackdrop(modifier: Modifier) {
    Spacer(
        modifier.drawWithCache {
            val bg = Brush.radialGradient(
                listOf(Color(0xFF2C1C62), Color(0xFF140C30), Color(0xFF06040F)),
                center = Offset(size.width * 0.36f, size.height * 0.45f), radius = size.maxDimension * 0.78f
            )
            val line = Color(0xFF8C7CFF).copy(alpha = 0.07f)
            val stroke = 1.dp.toPx()
            onDrawBehind {
                drawRect(bg)
                val horizon = size.height * 0.64f
                val vx = size.width * 0.5f
                for (i in -10..10) drawLine(line, Offset(vx + i * size.width * 0.02f, horizon), Offset(vx + i * size.width * 0.14f, size.height), stroke)
                var y = horizon
                var step = 5.dp.toPx()
                while (y < size.height) {
                    drawLine(line, Offset(0f, y), Offset(size.width, y), stroke)
                    y += step
                    step *= 1.38f
                }
            }
        }
    )
}

// ---- Drawing the cube (one small Canvas; read in the draw phase only) ----

internal const val TWO_PI = (2.0 * Math.PI).toFloat()
/** Resting a little turned and seen a little from above (the next category's face shows at the right). */
private const val CUBE_REST_YAW = -16f
private const val CUBE_REST_PITCH = 12f
/** The camera's distance in half-edges (perspective). */
internal const val CUBE_F = 6.5f
/** A face's own drawing space (units across). */
internal const val FACE = 360f
internal val RING_NX = floatArrayOf(0f, 1f, 0f, -1f)
internal val RING_NZ = floatArrayOf(1f, 0f, -1f, 0f)
internal const val LX = -0.42f
internal const val LY = 0.68f
internal const val LZ = 0.60f

private class CubeFaceArt(val label: String, val logo: ImageBitmap?, val icon: Painter?)

/** Where the visible faces were drawn last (for taps): kind 0 = a category face ([ks]: its ring index), 1 top, 2 bottom. */
internal class CubeHits {
    val quads = FloatArray(48)
    val kinds = IntArray(6)
    val ks = IntArray(6)
    @Volatile var count = 0
    fun find(x: Float, y: Float): Int {
        for (i in 0 until count) if (inside(i, x, y)) return i
        return -1
    }
    private fun inside(i: Int, x: Float, y: Float): Boolean {
        var pos = 0
        var neg = 0
        for (e in 0 until 4) {
            val ax = quads[i * 8 + e * 2]
            val ay = quads[i * 8 + e * 2 + 1]
            val bx = quads[i * 8 + ((e + 1) % 4) * 2]
            val by = quads[i * 8 + ((e + 1) % 4) * 2 + 1]
            val cr = (bx - ax) * (y - ay) - (by - ay) * (x - ax)
            if (cr > 0f) pos++ else if (cr < 0f) neg++
        }
        return pos == 0 || neg == 0
    }
}

internal class CubeTools {
    val matrix = android.graphics.Matrix()
    val src = floatArrayOf(0f, 0f, FACE, 0f, FACE, FACE, 0f, FACE)
    val dst = FloatArray(8)
    val v = FloatArray(3)
    val path = Path()
    val text = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textAlign = android.graphics.Paint.Align.CENTER
        color = android.graphics.Color.WHITE
    }
    /** Labels already fitted to a face: text size and lines. */
    val lines = HashMap<String, Pair<Float, List<String>>>()
}

internal fun DrawScope.cubeShadow(cx: Float, y: Float, half: Float, alpha: Float) {
    val sw = half * 2.6f
    val sh = half * 0.42f
    drawOval(
        Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.5f * alpha.coerceIn(0f, 1f)), Color.Transparent), center = Offset(cx, y), radius = sw / 2f),
        topLeft = Offset(cx - sw / 2f, y - sh / 2f), size = Size(sw, sh)
    )
}

/**
 * The cube at ([cx], [cy]), [half] its half-edge in px, turned by [yaw] then tipped by [pitch] (radians). The four side
 * faces show the categories round [ringBase] (each face the ring index ≡ its number mod 4 nearest the turn), the top
 * Apps, the bottom Settings. The faces turned away are drawn first as faint glass (their edges show through); the ones
 * facing you get their glass, picture and name mapped flat onto them (so nothing is ever mirrored).
 */
private fun DrawScope.drawCube(
    tools: CubeTools, hits: CubeHits,
    cx: Float, cy: Float, half: Float, yaw: Float, pitch: Float,
    ringBase: Int, ringFront: Int, tipFront: Int,
    art: (Int) -> CubeFaceArt, top: CubeFaceArt, bottom: CubeFaceArt, extra: Boolean,
    tint: Color, lit: Float, alpha: Float, nameOnly: Boolean
) {
    val cyw = cos(yaw)
    val syw = sin(yaw)
    val cp = cos(pitch)
    val sp = sin(pitch)
    val v = tools.v
    fun rot(x: Float, y: Float, z: Float) {
        val x1 = x * cyw + z * syw
        val z1 = -x * syw + z * cyw
        v[0] = x1
        v[1] = y * cp - z1 * sp
        v[2] = y * sp + z1 * cp
    }
    // The top / bottom faces read upright from the category face turned to you.
    val jf = ringFront.mod(4)
    val nfx = RING_NX[jf]
    val nfz = RING_NZ[jf]
    var hitN = 0
    for (pass in 0..1) for (f in 0 until 6) {
        val nx: Float
        val ny: Float
        val nz: Float
        val ux: Float
        val uy: Float
        val uz: Float
        when (f) {
            0, 1, 2, 3 -> { nx = RING_NX[f]; ny = 0f; nz = RING_NZ[f]; ux = 0f; uy = 1f; uz = 0f }
            4 -> { nx = 0f; ny = 1f; nz = 0f; ux = -nfx; uy = 0f; uz = -nfz }
            else -> { nx = 0f; ny = -1f; nz = 0f; ux = nfx; uy = 0f; uz = nfz }
        }
        // Right = up × normal.
        val rx = uy * nz - uz * ny
        val ry = uz * nx - ux * nz
        val rz = ux * ny - uy * nx
        rot(nx, ny, nz)
        val nxr = v[0]
        val nyr = v[1]
        val nzr = v[2]
        val visible = nzr * CUBE_F > 1f
        if ((pass == 1) != visible) continue
        // Corners: top-left, top-right, bottom-right, bottom-left.
        for (ci in 0 until 4) {
            val sr = if (ci == 1 || ci == 2) 1f else -1f
            val su = if (ci < 2) 1f else -1f
            rot(nx + sr * rx + su * ux, ny + sr * ry + su * uy, nz + sr * rz + su * uz)
            val k = CUBE_F / (CUBE_F - v[2])
            tools.dst[ci * 2] = cx + v[0] * k * half
            tools.dst[ci * 2 + 1] = cy - v[1] * k * half
        }
        val path = tools.path
        path.reset()
        path.moveTo(tools.dst[0], tools.dst[1])
        path.lineTo(tools.dst[2], tools.dst[3])
        path.lineTo(tools.dst[4], tools.dst[5])
        path.lineTo(tools.dst[6], tools.dst[7])
        path.close()
        if (!visible) {
            drawPath(path, tint.copy(alpha = 0.10f * alpha))
            drawPath(path, Color.White.copy(alpha = 0.16f * alpha), style = Stroke(1.2.dp.toPx()))
            continue
        }
        val kind = if (f < 4) 0 else if (f == 4) 1 else 2
        var kk = 0
        val faceArt: CubeFaceArt? = when (kind) {
            0 -> {
                var d = (f - ringBase).mod(4)
                if (d == 3) d = -1
                kk = ringBase + d
                art(kk)
            }
            1 -> if (extra) top else null
            else -> if (extra) bottom else null
        }
        val front = when (kind) {
            0 -> tipFront == 0 && kk == ringFront
            1 -> tipFront == -1
            else -> tipFront == 1
        }
        if (hitN < 6) {
            System.arraycopy(tools.dst, 0, hits.quads, hitN * 8, 8)
            hits.kinds[hitN] = kind
            hits.ks[hitN] = kk
            hitN++
        }
        val light = 0.5f + 0.5f * max(0f, nxr * LX + nyr * LY + nzr * LZ)
        drawFace(tools, faceArt, light, tint, if (front) lit else 0f, alpha, nameOnly)
    }
    hits.count = hitN
}

/** One face facing you: its glass, rim and content drawn in the face's own space, mapped onto its corners. */
private fun DrawScope.drawFace(tools: CubeTools, art: CubeFaceArt?, light: Float, tint: Color, lit: Float, alpha: Float, nameOnly: Boolean) {
    tools.matrix.reset()
    if (!tools.matrix.setPolyToPoly(tools.src, 0, tools.dst, 0, 4)) return
    val dark = Color(0xFF0A0916)
    val hi = lerp(dark, tint, (0.62f * light + 0.12f).coerceIn(0f, 1f))
    val lo = lerp(dark, tint, (0.32f * light).coerceIn(0f, 1f))
    drawIntoCanvas { cv ->
        val nc = cv.nativeCanvas
        nc.save()
        nc.concat(tools.matrix)
        drawRect(Brush.verticalGradient(listOf(hi.copy(alpha = 0.80f * alpha), lo.copy(alpha = 0.88f * alpha)), 0f, FACE), Offset.Zero, Size(FACE, FACE))
        drawRect(
            Brush.linearGradient(listOf(Color.White.copy(alpha = 0.14f * alpha), Color.Transparent), Offset.Zero, Offset(FACE * 0.6f, FACE * 0.6f)),
            Offset.Zero, Size(FACE, FACE)
        )
        drawRect(Color.White.copy(alpha = (0.10f + 0.5f * lit) * alpha), Offset(14f, 14f), Size(FACE - 28f, FACE - 28f), style = Stroke(3f + 3f * lit))
        drawRect(lerp(tint, Color.White, 0.55f).copy(alpha = ((0.55f + 0.4f * lit) * alpha).coerceIn(0f, 1f)), Offset.Zero, Size(FACE, FACE), style = Stroke(6f))
        if (art == null) {
            // A plain face: a small diamond emblem.
            val e = Path().apply {
                moveTo(FACE / 2f, FACE * 0.38f); lineTo(FACE * 0.62f, FACE / 2f); lineTo(FACE / 2f, FACE * 0.62f); lineTo(FACE * 0.38f, FACE / 2f); close()
            }
            drawPath(e, Color.White.copy(alpha = 0.28f * alpha), style = Stroke(5f))
        } else faceContent(tools, nc, art, alpha, nameOnly)
        nc.restore()
    }
}

private fun DrawScope.faceContent(tools: CubeTools, nc: android.graphics.Canvas, art: CubeFaceArt, alpha: Float, nameOnly: Boolean) {
    val paint = tools.text
    paint.color = android.graphics.Color.argb((242 * alpha).roundToInt().coerceIn(0, 255), 255, 255, 255)
    paint.setShadowLayer(5f, 0f, 2f, android.graphics.Color.argb((140 * alpha).roundToInt().coerceIn(0, 255), 0, 0, 0))
    if (nameOnly) {
        val (ts, lines) = fitLabel(tools, art.label, 58f, FACE * 0.84f, 3)
        paint.textSize = ts
        val lh = ts * 1.15f
        var y = FACE / 2f - lh * (lines.size - 1) / 2f + ts * 0.35f
        for (l in lines) { nc.drawText(l, FACE / 2f, y, paint); y += lh }
        return
    }
    val boxW = FACE * 0.56f
    val boxH = FACE * 0.34f
    val bcx = FACE / 2f
    val bcy = FACE * 0.40f
    val logo = art.logo
    if (logo != null && logo.width > 0 && logo.height > 0) {
        val sc = min(boxW / logo.width, boxH / logo.height)
        val lw = (logo.width * sc).roundToInt().coerceAtLeast(1)
        val lh = (logo.height * sc).roundToInt().coerceAtLeast(1)
        drawImage(
            logo, srcOffset = IntOffset.Zero, srcSize = IntSize(logo.width, logo.height),
            dstOffset = IntOffset((bcx - lw / 2f).roundToInt(), (bcy - lh / 2f).roundToInt()), dstSize = IntSize(lw, lh),
            alpha = 0.95f * alpha, colorFilter = ColorFilter.tint(Color.White)
        )
    } else art.icon?.let { pnt ->
        val sz = FACE * 0.30f
        translate(bcx - sz / 2f, bcy - sz / 2f) { with(pnt) { draw(Size(sz, sz), alpha = 0.95f * alpha, colorFilter = ColorFilter.tint(Color.White)) } }
    }
    val (ts, lines) = fitLabel(tools, art.label, 40f, FACE * 0.84f, 2)
    paint.textSize = ts
    var y = FACE * 0.72f
    for (l in lines) { nc.drawText(l, FACE / 2f, y, paint); y += ts * 1.15f }
}

/** [label] in at most [maxLines] lines [maxW] wide: the biggest of a few sizes that fits, else the smallest with "…". */
internal fun fitLabel(tools: CubeTools, label: String, base: Float, maxW: Float, maxLines: Int): Pair<Float, List<String>> {
    val cacheKey = "$label|$base|$maxLines"
    tools.lines[cacheKey]?.let { return it }
    if (tools.lines.size > 240) tools.lines.clear()
    val paint = tools.text
    var chosen: Pair<Float, List<String>>? = null
    for (k in floatArrayOf(1f, 0.86f, 0.74f, 0.64f)) {
        paint.textSize = base * k
        val ls = wrapLabel(label, paint, maxW)
        if (ls.size <= maxLines) { chosen = base * k to ls; break }
    }
    if (chosen == null) {
        val ts = base * 0.64f
        paint.textSize = ts
        val keep = wrapLabel(label, paint, maxW).take(maxLines).toMutableList()
        if (keep.isNotEmpty()) {
            var last = keep[keep.lastIndex]
            while (last.isNotEmpty() && paint.measureText("$last…") > maxW) last = last.dropLast(1)
            keep[keep.lastIndex] = "$last…"
        }
        chosen = ts to keep
    }
    tools.lines[cacheKey] = chosen
    return chosen
}

internal fun wrapLabel(text: String, paint: android.graphics.Paint, maxW: Float): List<String> {
    val out = ArrayList<String>()
    var line = ""
    for (word in text.split(' ').filter { it.isNotEmpty() }) {
        val trial = if (line.isEmpty()) word else "$line $word"
        if (paint.measureText(trial) <= maxW) { line = trial; continue }
        if (line.isNotEmpty()) out += line
        // A single word wider than the face: cut where it fits.
        var rest = word
        while (rest.isNotEmpty() && paint.measureText(rest) > maxW) {
            val cut = paint.breakText(rest, true, maxW, null).coerceAtLeast(1)
            out += rest.substring(0, cut)
            rest = rest.substring(cut)
        }
        line = rest
    }
    if (line.isNotEmpty()) out += line
    return out
}
