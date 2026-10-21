package com.shelf.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.NameBackdrop
import com.shelf.launcher.data.statusOn
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch

/*
 * 0.6.17: the Cube menu's Hub look (Settings › Home › Layout options › Cube menu › Look; the default on wide screens).
 *
 * One screen, three parts, as Toni's picture of it:
 * - a menu on the left — Voltcap, then Games, Systems, Apps and Settings;
 * - a big glass cube in the middle, seen from a corner: its top face Games, its left face Systems, its right face Apps,
 *   and a small cube in front of it Settings. It turns to show the part you're on, floats and sways, and pulses when
 *   you open one; glowing edges, light in its core, a lit ring on the floor, small cubes and specks drifting round it,
 *   soft rays of light from above;
 * - the library on the right: the category tabs, the games as a cover flow (the one you're on big in the middle, the
 *   others turned aside, all mirrored on the floor), the game's name and facts and the buttons.
 *   Systems shows your consoles there instead (A: its games); Apps and Settings a card that opens them.
 * Keys: on the games ◀ ▶ (the home screen's own: Endless works), A / X / Y as everywhere, ▲ the tabs (◀ ▶ change the
 * category, ▼ / A back to the games), L2 / R2 the category, B the menu. In the menu ▲ ▼ choose, A or ▶ open, B the
 * home screen's own. Touch: tap anything.
 * Motion is read in the draw phase only (one clock for the room, the cube's pose and the cover flow's place are
 * Animatables read in graphicsLayer / draw blocks), so nothing recomposes per frame. Lite effects / reduced motion: still.
 */

internal object CubeHub {
    /** 0 Games, 1 Systems, 2 Apps, 3 Settings. */
    var section by mutableIntStateOf(0)
    /** Where the controller is: [MENU] the menu / cube, [TABS] the category tabs, [GAMES] the cover flow, [SYSTEMS] the consoles. */
    var zone by mutableIntStateOf(GAMES)
    var sysFocus by mutableIntStateOf(0)
    /** Bumped when a part is opened: the cube pulses. */
    var pulse by mutableIntStateOf(0)
    @Volatile var active = false
    @Volatile var ctx: StyleCtx? = null
    internal val keysDown = HashSet<Int>()

    const val MENU = 0
    const val TABS = 1
    const val GAMES = 2
    const val SYSTEMS = 3

    val labels = listOf("Games", "Systems", "Apps", "Settings")
}

/** The consoles among the categories (their place in the category cycle, the stop). */
private fun hubSystems(c: StyleCtx): List<Pair<Int, StyleStop>> =
    c.stops.withIndex().filter { (_, st) -> st.platform != null && st.special == null && !com.shelf.launcher.data.isCollectionKey(st.platform) }
        .map { it.index to it.value }

/** A on a part of the menu (or a tap on its face). */
private fun hubOpen(c: StyleCtx, section: Int, sound: (UiSound) -> Unit) {
    CubeHub.section = section
    CubeHub.pulse++
    when (section) {
        0 -> { CubeHub.zone = CubeHub.GAMES; sound(UiSound.OPEN) }
        1 -> {
            val sys = hubSystems(c)
            val here = sys.indexOfFirst { it.first == c.stop }
            if (here >= 0) CubeHub.sysFocus = here
            CubeHub.zone = if (sys.isEmpty()) CubeHub.MENU else CubeHub.SYSTEMS
            sound(UiSound.OPEN)
        }
        2 -> { sound(UiSound.OPEN); cubeApps(c) }
        else -> c.actions.settings()
    }
}

/** A on a console in Systems: its games. */
private fun hubOpenSystem(c: StyleCtx, sound: (UiSound) -> Unit) {
    val sys = hubSystems(c)
    val pick = sys.getOrNull(CubeHub.sysFocus.mod(sys.size.coerceAtLeast(1))) ?: return
    val steps = pocketStepsTo(c, pick.first)
    if (steps != 0) c.actions.category(steps)
    CubeHub.section = 0
    CubeHub.zone = CubeHub.GAMES
    CubeHub.pulse++
    sound(UiSound.OPEN)
}

/** The Hub's keys (from cubeKey). True = handled. */
internal fun cubeHubKey(event: KeyEvent, reach: (Boolean) -> Boolean, sound: (UiSound) -> Unit): Boolean {
    val c = CubeHub.ctx ?: return false
    val code = event.nativeKeyEvent.keyCode
    val down = event.type == KeyEventType.KeyDown
    val up = event.type == KeyEventType.KeyUp
    if (up && CubeHub.keysDown.remove(code)) return true
    val k = event.key
    val first = event.nativeKeyEvent.repeatCount == 0
    val isA = k == Key.ButtonA || k == Key.Enter || k == Key.DirectionCenter
    val isB = k == Key.ButtonB || k == Key.Back
    fun take(): Boolean { CubeHub.keysDown += code; return true }
    when (CubeHub.zone) {
        CubeHub.GAMES -> {
            if (c.games.isEmpty() && isA) return take()
            if (!down) return false
            return when (k) {
                Key.DirectionUp -> { if (first) { CubeHub.zone = CubeHub.TABS; sound(UiSound.MOVE) }; take() }
                Key.ButtonB, Key.Back -> { if (first) { CubeHub.zone = CubeHub.MENU; sound(UiSound.BACK) }; take() }
                Key.DirectionDown -> { if (first) reach(false); take() }
                // ◀ ▶, A, X, Y, L2 / R2: the home screen's own.
                else -> false
            }
        }
        CubeHub.TABS -> {
            if (isA) { if (up) { CubeHub.zone = CubeHub.GAMES; sound(UiSound.SELECT) }; return true }
            if (isB) { if (down && first) { CubeHub.zone = CubeHub.MENU; sound(UiSound.BACK) }; return take() }
            if (!down) return false
            return when (k) {
                Key.DirectionLeft, Key.DirectionRight -> {
                    if (c.stops.size > 1) { c.actions.category(if (k == Key.DirectionRight) 1 else -1); sound(UiSound.MOVE) }
                    take()
                }
                Key.DirectionDown -> { if (first) { CubeHub.zone = CubeHub.GAMES; sound(UiSound.MOVE) }; take() }
                Key.DirectionUp -> { if (first) reach(true); take() }
                else -> false
            }
        }
        CubeHub.SYSTEMS -> {
            val n = hubSystems(c).size
            if (isA) { if (up && !event.nativeKeyEvent.isCanceled) hubOpenSystem(c, sound); return true }
            if (isB) { if (down && first) { CubeHub.zone = CubeHub.MENU; sound(UiSound.BACK) }; return take() }
            if (!down) return false
            return when (k) {
                Key.DirectionLeft, Key.DirectionRight -> {
                    if (n > 1) { CubeHub.sysFocus = (CubeHub.sysFocus + if (k == Key.DirectionRight) 1 else -1).mod(n); sound(UiSound.MOVE) }
                    take()
                }
                Key.DirectionUp -> { if (first) reach(true); take() }
                Key.DirectionDown -> { if (first) reach(false); take() }
                else -> take()
            }
        }
        else -> {
            // The menu (and the cube beside it).
            if (isA) { if (up && !event.nativeKeyEvent.isCanceled) hubOpen(c, CubeHub.section, sound); return true }
            if (!down) return false
            return when (k) {
                Key.DirectionUp -> {
                    if (CubeHub.section > 0) { CubeHub.section--; sound(UiSound.MOVE) } else if (first) reach(true)
                    take()
                }
                Key.DirectionDown -> {
                    if (CubeHub.section < 3) { CubeHub.section++; sound(UiSound.MOVE) } else if (first) reach(false)
                    take()
                }
                Key.DirectionRight -> {
                    if (first) when (CubeHub.section) {
                        0 -> { CubeHub.zone = CubeHub.GAMES; sound(UiSound.MOVE) }
                        1 -> hubOpen(c, 1, sound)
                        else -> Unit
                    }
                    take()
                }
                Key.DirectionLeft, Key.ButtonX, Key.ButtonY -> take()
                // B, L2 / R2, Start, Select: the home screen's own.
                else -> false
            }
        }
    }
}

// ---- The layout ----

@Composable
internal fun CubeHub(c: StyleCtx, modifier: Modifier = Modifier) {
    val live by rememberUpdatedState(c)
    val accent = ShelfTheme.accent
    val tintPref = Features.cubeTint.state.value
    val tint = if (tintPref == Features.CubeTint.THEME) accent else lerp(Color(tintPref.argb), Color(0xFFB07CFF), 0.35f)
    val reduced = Features.cubeReducedMotion.state.value || !rememberAnimationsEnabled()
    val lite = LocalLiteEffects.current
    val active = rememberIsActive()
    val windowOpen = GamePopup.game != null
    val section = CubeHub.section
    val zone = CubeHub.zone
    val density = LocalDensity.current
    CategoryTitle.Native()

    DisposableEffect(Unit) {
        CubeHub.active = true
        onDispose { CubeHub.active = false; CubeHub.ctx = null; CubeHub.keysDown.clear() }
    }
    SideEffect {
        CubeHub.ctx = c
        StyleNav.top = emptyList()
        StyleNav.bar = emptyList()
        StyleNav.side = emptyList()
        StyleLoop.on = loopRowOn(c)
    }
    androidx.activity.compose.BackHandler(enabled = zone != CubeHub.MENU && !windowOpen) {
        CubeHub.zone = CubeHub.MENU
        cubeSound(UiSound.BACK)
    }

    // The room's clock (specks, small cubes, rays, the cube's sway): ~30 a second, still on Lite / reduced / idle.
    val moving = !reduced && !lite && active && !windowOpen && !HomeIdle.calm && Features.cubeIdle.state.value
    val clock = rememberGcClock(moving, 1f, 30)

    // The cube's pose for the part you're on: yaw / pitch in degrees, eased.
    val (yawT, pitchT) = when (section) {
        0 -> -45f to 33f
        1 -> -30f to 24f
        2 -> -60f to 24f
        else -> -45f to 22f
    }
    val yawA = remember { Animatable(yawT) }
    val pitchA = remember { Animatable(pitchT) }
    LaunchedEffect(yawT, pitchT, reduced) {
        if (reduced) { yawA.snapTo(yawT); pitchA.snapTo(pitchT); return@LaunchedEffect }
        kotlinx.coroutines.coroutineScope {
            launch { yawA.animateTo(yawT, spring(dampingRatio = 0.72f, stiffness = 160f)) }
            launch { pitchA.animateTo(pitchT, spring(dampingRatio = 0.72f, stiffness = 160f)) }
        }
    }
    // A part opened: the cube leans in (zooms) a moment.
    val zoom = remember { Animatable(1f) }
    val pulseTick = CubeHub.pulse
    LaunchedEffect(pulseTick) {
        if (pulseTick == 0 || reduced) return@LaunchedEffect
        zoom.animateTo(1.1f, tween(140, easing = FastOutSlowInEasing))
        zoom.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 320f))
    }

    val gamesP = rememberVectorPainter(Icons.Filled.SportsEsports)
    val sysP = rememberVectorPainter(Icons.Filled.Storage)
    val appsP = rememberVectorPainter(Icons.Filled.Apps)
    val setP = rememberVectorPainter(Icons.Filled.Settings)
    val tools = remember { CubeTools() }
    val hits = remember { CubeHits() }
    val miniHits = remember { CubeHits() }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        val topPad = if (c.state.appearance.statusOn) 40.dp else 14.dp
        val menuW = (w * 0.19f).coerceIn(158.dp, 210.dp)
        val cubeW = w * 0.32f
        val libX = menuW + cubeW
        val libW = w - libX - 12.dp
        val cubeCxDp = menuW + cubeW / 2
        val cubeCyDp = h * 0.53f
        val halfDp = minOf(cubeW * 0.25f, h * 0.2f) * (Features.cubeSize.state.value / 100f)

        // The room and the cube: one canvas under everything.
        Spacer(
            Modifier.fillMaxSize()
                .drawWithCache {
                    val cx = cubeCxDp.toPx()
                    val cy = cubeCyDp.toPx()
                    val nebula1 = Brush.radialGradient(listOf(tint.copy(alpha = 0.30f), Color.Transparent), Offset(cx, cy), size.minDimension * 0.75f)
                    val nebula2 = Brush.radialGradient(listOf(Color(0xFF3A6BFF).copy(alpha = 0.14f), Color.Transparent), Offset(size.width * 0.82f, size.height * 0.2f), size.minDimension * 0.7f)
                    val nebula3 = Brush.radialGradient(listOf(Color(0xFFE04BFF).copy(alpha = 0.12f), Color.Transparent), Offset(size.width * 0.12f, size.height * 0.85f), size.minDimension * 0.6f)
                    // (Past the layout's own edges too — it sits a little in from the screen's, and the glow stopped in a frame.)
                    val bleed = Offset(-80.dp.toPx(), -80.dp.toPx())
                    val big = Size(size.width + 160.dp.toPx(), size.height + 160.dp.toPx())
                    onDrawBehind {
                        drawRect(nebula1, bleed, big)
                        drawRect(nebula2, bleed, big)
                        drawRect(nebula3, bleed, big)
                        val t = clock.floatValue
                        hubRays(cx, t, tint)
                        hubSpecks(t, tint, libX.toPx())
                    }
                }
        )
        Spacer(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        val cc = live
                        val mi = miniHits.find(pos.x, pos.y)
                        if (mi >= 0) { if (CubeHub.section == 3) hubOpen(cc, 3, ::cubeSound) else { CubeHub.section = 3; CubeHub.zone = CubeHub.MENU; cubeSound(UiSound.MOVE) }; return@detectTapGestures }
                        val i = hits.find(pos.x, pos.y)
                        if (i < 0) return@detectTapGestures
                        val sec = when (hits.kinds[i]) { 4 -> 0; 0 -> 1; 1 -> 2; else -> -1 }
                        if (sec < 0) return@detectTapGestures
                        if (CubeHub.section == sec) hubOpen(cc, sec, ::cubeSound) else { CubeHub.section = sec; CubeHub.zone = CubeHub.MENU; cubeSound(UiSound.MOVE) }
                    }
                }
                .drawBehind {
                    val cx = cubeCxDp.toPx()
                    val t = clock.floatValue
                    val half = halfDp.toPx() * zoom.value
                    val bob = sin(t * TWO_PI / 6f) * half * 0.05f
                    val cy = cubeCyDp.toPx() - bob
                    val sway = sin(t * TWO_PI / 11f) * 7f
                    val nod = sin(t * TWO_PI / 8f) * 2.5f
                    // Floor: a lit ring under the cube.
                    hubFloor(cx, cubeCyDp.toPx() + halfDp.toPx() * 1.75f, halfDp.toPx(), tint, 1f - 0.3f * (bob / (half * 0.05f + 0.001f)).coerceIn(-1f, 1f))
                    // Small glass cubes floating round it.
                    hubMiniCubes(tools, cx, cy, half, t, tint)
                    // Light in the cube's core.
                    drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.55f), tint.copy(alpha = 0.12f), Color.Transparent), Offset(cx, cy), half * 1.9f), half * 1.9f, Offset(cx, cy))
                    val sel = when (CubeHub.section) { 0 -> 4; 1 -> 0; 2 -> 1; else -> -1 }
                    drawHubCube(
                        tools, hits, cx, cy, half,
                        Math.toRadians((yawA.value + sway).toDouble()).toFloat(), Math.toRadians((pitchA.value + nod).toDouble()).toFloat(),
                        HubFaceArt("Systems", sysP), HubFaceArt("Apps", appsP), HubFaceArt("Games", gamesP),
                        sel, tint, lit = if (CubeHub.zone == CubeHub.MENU) 1f else 0.7f
                    )
                    // Settings: the small cube in front, a little turned.
                    val mHalf = half * 0.33f
                    val mcx = cx + half * 0.18f
                    val mcy = cubeCyDp.toPx() + half * 1.42f + sin(t * TWO_PI / 5f + 1f) * mHalf * 0.12f
                    drawHubCube(
                        tools, miniHits, mcx, mcy, mHalf * (if (CubeHub.section == 3) 1.12f else 1f),
                        Math.toRadians(-24.0 + sin(t * TWO_PI / 9f) * 10.0).toFloat(), Math.toRadians(16.0).toFloat(),
                        HubFaceArt("Settings", setP), null, null,
                        if (CubeHub.section == 3) 0 else -2, tint, lit = if (CubeHub.zone == CubeHub.MENU) 1f else 0.7f, small = true
                    )
                }
        )

        // The menu.
        HubMenu(Modifier.offset(x = 14.dp, y = topPad - 8.dp).width(menuW - 22.dp), tint, section, zone == CubeHub.MENU) { s ->
            if (CubeHub.section == s) hubOpen(live, s, ::cubeSound) else { CubeHub.section = s; CubeHub.zone = CubeHub.MENU; cubeSound(UiSound.MOVE) }
        }

        // The panel on the right.
        Box(Modifier.offset(x = libX, y = topPad).width(libW).height(h - topPad - 6.dp).clipToBounds()) {
            when (section) {
                1 -> HubSystems(c, tint, zone == CubeHub.SYSTEMS, Modifier.fillMaxSize())
                2 -> HubCard(Icons.Filled.Apps, "Apps", "All your Android apps, in Voltcap's app drawer.", tint, Modifier.fillMaxSize()) { hubOpen(live, 2, ::cubeSound) }
                3 -> HubCard(Icons.Filled.Settings, "Settings", "Everything about Voltcap: layouts, looks, sounds, accounts, sync.", tint, Modifier.fillMaxSize()) { hubOpen(live, 3, ::cubeSound) }
                else -> HubLibrary(c, tint, zone, Modifier.fillMaxSize())
            }
        }
    }
}

// ---- Menu ----

@Composable
private fun HubMenu(modifier: Modifier, tint: Color, section: Int, on: Boolean, pick: (Int) -> Unit) {
    val icons = listOf(Icons.Filled.SportsEsports, Icons.Filled.Storage, Icons.Filled.Apps, Icons.Filled.Settings)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 18.dp)) {
            Icon(Icons.Filled.ViewInAr, null, tint = lerp(tint, Color.White, 0.35f), modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(8.dp))
            Text("Voltcap", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1, style = nameTextStyle(NameBackdrop.SHADOW))
        }
        CubeHub.labels.forEachIndexed { i, label ->
            val here = i == section
            val shape = RoundedCornerShape(12.dp)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).height(42.dp).clip(shape)
                    .then(
                        if (here) Modifier.background(Brush.horizontalGradient(listOf(tint.copy(alpha = if (on) 0.85f else 0.55f), tint.copy(alpha = 0.18f))))
                            .border(if (on) 1.5.dp else 1.dp, if (on) Color.White.copy(alpha = 0.9f) else tint.copy(alpha = 0.7f), shape)
                        else Modifier
                    )
                    .pointerInput(i) { detectTapGestures { pick(i) } }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icons[i], null, tint = Color.White.copy(alpha = if (here) 1f else 0.78f), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(label, color = Color.White.copy(alpha = if (here) 1f else 0.82f), fontSize = 15.sp, fontWeight = if (here) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            if (on) "▲ ▼ Choose   Ⓐ Open" else "Ⓑ Menu",
            color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(start = 6.dp)
        )
    }
}

// ---- Library ----

@Composable
private fun HubLibrary(c: StyleCtx, tint: Color, zone: Int, modifier: Modifier) {
    val live by rememberUpdatedState(c)
    val titles = LocalTitleOptions.current
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Game Library", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, style = nameTextStyle(NameBackdrop.SHADOW))
            Spacer(Modifier.weight(1f))
            if (c.games.isNotEmpty()) Text("${c.focus + 1} / ${c.games.size}", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 1)
        }
        Spacer(Modifier.height(8.dp))
        HubTabs(c, tint, zone == CubeHub.TABS)
        Spacer(Modifier.height(4.dp))
        if (c.games.isEmpty()) {
            StyleEmpty(Modifier.fillMaxWidth().weight(1f))
        } else {
            HubCoverFlow(c, tint, zone == CubeHub.GAMES, Modifier.fillMaxWidth().weight(1f))
            val g = c.game
            if (g != null) {
                Text(
                    titles.display(g.name), color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, style = nameTextStyle(NameBackdrop.SHADOW),
                    modifier = Modifier.fillMaxWidth().riseOnChange(g.stableKey)
                )
                val meta = c.state.metadata[g.stableKey]
                val line = buildList {
                    add(titles.console(g.platform))
                    meta?.releaseYear?.let { add(it.toString()) }
                    meta?.genres?.firstOrNull()?.let { add(it) }
                    add(stylePlaytime(c.playtime(g), c.lastPlayed(g) != null))
                }.joinToString("  ·  ")
                Text(line, color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    HubHint("A", "Open") { live.actions.open() }
                    HubHint("X", "Favourite") { live.game?.let { live.actions.favorite(it) } }
                    HubHint("Y", "Achievements") { live.game?.let { live.actions.achievements(it) } }
                }
            }
        }
    }
}

@Composable
private fun HubHint(button: String, label: String, onTap: () -> Unit) {
    val tap by rememberUpdatedState(onTap)
    Row(
        Modifier.padding(start = 12.dp).pointerInput(Unit) { detectTapGestures { tap() } },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(19.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.92f)), contentAlignment = Alignment.Center) {
            Text(button, color = Color(0xFF1A1530), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Spacer(Modifier.width(5.dp))
        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1)
    }
}

/** The categories as tabs; the one you're in filled. ▲ from the games puts the D-pad here (◀ ▶ change the category). */
@Composable
private fun HubTabs(c: StyleCtx, tint: Color, on: Boolean) {
    val live by rememberUpdatedState(c)
    val stop = c.stop
    // (Update 294, Toni: the last tab ("App") was cut off at the right edge: whole tabs only, "+N ›" for the rest —
    // WholeChipRow.)
    WholeChipRow(count = c.stops.size, focused = stop, modifier = Modifier.fillMaxWidth().height(30.dp), spacing = 6.dp) { i ->
        val st = c.stops[i]
        val here = i == stop
        val shape = RoundedCornerShape(8.dp)
        Box(
            Modifier.height(28.dp).clip(shape)
                .background(if (here) tint.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.08f))
                .border(if (here && on) 1.5.dp else 1.dp, if (here && on) Color.White else Color.White.copy(alpha = if (here) 0.4f else 0.14f), shape)
                .pointerInput(i) { detectTapGestures { val d = pocketStepsTo(live, i); if (d != 0) live.actions.category(d); cubeSound(UiSound.MOVE) } }
                .padding(horizontal = 11.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(st.label, color = Color.White.copy(alpha = if (here) 1f else 0.78f), fontSize = 12.sp, fontWeight = if (here) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
        }
    }
}

/**
 * The games as a cover flow: the one you're on big and facing you, the others smaller, turned aside and further back,
 * each mirrored on a glossy floor. The place glides (an Animatable read in the layers), so a move redraws, never
 * recomposes; only the games around the one you're on are composed.
 */
@Composable
private fun HubCoverFlow(c: StyleCtx, tint: Color, on: Boolean, modifier: Modifier) {
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    val lite = LocalLiteEffects.current
    val n = c.games.size
    val loop = loopRowOn(c)
    val vf = rememberLoopFocus(c)
    val place = remember { Animatable(vf.toFloat()) }
    LaunchedEffect(vf) {
        val t = vf.toFloat()
        if (abs(place.value - t) > 5f) place.snapTo(t + if (place.value < t) -1f else 1f)
        place.animateTo(t, spring(dampingRatio = 0.86f, stiffness = 300f))
    }
    val aspect = styleAspect(c.type).coerceIn(0.55f, 1.8f)
    val pulse = rememberFocusPulse(animate = false)
    BoxWithConstraints(modifier) {
        val ch = maxHeight
        val cw = maxWidth
        // The one you're on: as tall as the room allows (its mirror takes the rest), never wider than ~45 % of the panel.
        val bigH = minOf(ch * 0.74f, cw * 0.46f / aspect)
        val bigW = bigH * aspect
        val baseY = ch * 0.08f + bigH
        val density = LocalDensity.current
        val bigWpx = with(density) { bigW.toPx() }
        val keys = remember(c.games) { gameKeys(c.games) }
        for (v in (vf - 4)..(vf + 4)) {
            val idx = if (loop) v.mod(n) else v
            if (idx < 0 || idx >= n) continue
            val gg = c.games[idx]
            key("${keys[idx]}#${if (loop) Math.floorDiv(v, n) else 0}") {
                val dist = abs(v - vf)
                Box(
                    Modifier.zIndex(10f - dist)
                        .offset(x = (cw - bigW) / 2, y = baseY - bigH)
                        .size(bigW, bigH)
                        .graphicsLayer {
                            val d = v - place.value
                            val ad = abs(d)
                            val near = min(ad, 1f)
                            val far = max(ad - 1f, 0f)
                            translationX = (if (d < 0) -1f else 1f) * (near * bigWpx * 0.74f + far * bigWpx * 0.42f)
                            val s = 1f - 0.24f * near - 0.07f * min(far, 2f)
                            scaleX = s; scaleY = s
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                            rotationY = -d.coerceIn(-1f, 1f) * 38f
                            cameraDistance = 14f * density.density
                            alpha = (1f - (ad - 2.6f).coerceIn(0f, 1f)) * (1f - 0.18f * near)
                            flatTextFor3d() // (Update 294: drawn flat, then turned — the Thor's frozen Hub, Android 13's text cache)
                        }
                ) {
                    val here = v == vf
                    StyleArt(
                        c, gg,
                        Modifier.fillMaxSize()
                            .pointerInput(idx) { detectTapGestures { if (idx == GridHome.focus) open() else select(idx) } },
                        focused = here && on, fx = if (here) pulse else null, ring = Color.White, outline = 3.dp
                    )
                    // The mirror on the floor (not on Lite): the same picture upside down, fading out.
                    if (!lite && dist <= 2) {
                        Box(
                            Modifier.fillMaxSize()
                                .graphicsLayer {
                                    translationY = size.height + 4.dp.toPx()
                                    scaleY = -1f
                                    alpha = 0.42f
                                    compositingStrategy = CompositingStrategy.Offscreen
                                }
                                .drawWithContent {
                                    drawContent()
                                    // In the flipped layer the floor line is at the bottom: keep the part near it.
                                    drawRect(
                                        Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black), 0f, size.height),
                                        blendMode = BlendMode.DstIn
                                    )
                                }
                        ) { androidx.compose.runtime.CompositionLocalProvider(LocalArtMirror provides true) { StyleArt(c, gg, Modifier.fillMaxSize()) } }
                    }
                }
            }
        }
        // The glossy floor line under the games.
        Box(
            Modifier.offset(y = baseY + 2.dp).fillMaxWidth().height(2.dp)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, tint.copy(alpha = 0.55f), Color.Transparent)))
        )
    }
}

// ---- Systems / Apps / Settings ----

@Composable
private fun HubSystems(c: StyleCtx, tint: Color, on: Boolean, modifier: Modifier) {
    val live by rememberUpdatedState(c)
    val context = LocalContext.current
    val sys = remember(c.stops) { hubSystems(c) }
    val counts = remember(c.state.games, sys) {
        sys.associate { (_, st) ->
            val p = st.platform!!
            // (The Android category holds your Android games, not a console's.)
            p to if (p.equals("Android", ignoreCase = true)) c.state.games.count { c.state.isAndroidGame(it) } else c.state.games.count { it.platform == p }
        }
    }
    val logos = remember { HashMap<String, androidx.compose.ui.graphics.ImageBitmap?>() }
    Column(modifier) {
        Text("Systems", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, style = nameTextStyle(NameBackdrop.SHADOW), modifier = Modifier.padding(start = 4.dp))
        Text("${sys.size} consoles in your library · Ⓐ their games", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
        Spacer(Modifier.height(10.dp))
        if (sys.isEmpty()) {
            StyleEmpty(Modifier.fillMaxWidth().weight(1f))
        } else {
        val f = CubeHub.sysFocus.mod(sys.size)
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val tileW = (maxWidth * 0.3f).coerceIn(96.dp, 150.dp)
            val tileH = tileW * 0.8f
            val gap = tileW * 0.86f
            for (d in -3..3) {
                if (sys.size < 7 && abs(d) > sys.size / 2) continue
                val i = (f + d).mod(sys.size)
                val (_, st) = sys[i]
                val p = st.platform!!
                val here = d == 0
                key(p) {
                    val shape = RoundedCornerShape(14.dp)
                    Column(
                        Modifier.zIndex(10f - abs(d))
                            .offset(x = (maxWidth - tileW) / 2 + gap * d, y = (maxHeight - tileH) / 2 - 20.dp)
                            .width(tileW).height(tileH)
                            .graphicsLayer {
                                val s = if (here) 1.12f else 1f - 0.08f * abs(d)
                                scaleX = s; scaleY = s
                                alpha = 1f - 0.22f * abs(d)
                            }
                            .clip(shape)
                            .background(Brush.verticalGradient(listOf(tint.copy(alpha = if (here) 0.55f else 0.28f), Color(0xFF120E28).copy(alpha = 0.85f))))
                            .border(if (here) 2.dp else 1.dp, if (here && on) Color.White else tint.copy(alpha = if (here) 0.9f else 0.4f), shape)
                            .pointerInput(i) { detectTapGestures { if (CubeHub.sysFocus.mod(sys.size) == i) hubOpenSystem(live, ::cubeSound) else { CubeHub.sysFocus = i; CubeHub.zone = CubeHub.SYSTEMS; cubeSound(UiSound.MOVE) } } }
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (!logos.containsKey(p)) logos[p] = runCatching { PlatformLogos.baked(context, p) }.getOrNull()
                        val logo = logos[p]
                        if (logo != null) Image(logo, st.label, colorFilter = ColorFilter.tint(Color.White), modifier = Modifier.fillMaxWidth().weight(1f))
                        else Icon(Icons.Filled.SportsEsports, null, tint = Color.White, modifier = Modifier.size(34.dp).weight(1f))
                        Spacer(Modifier.height(4.dp))
                        Text(st.label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${counts[p] ?: 0} games", color = Color.White.copy(alpha = 0.65f), fontSize = 10.sp, maxLines = 1)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            HubHint("A", "Games") { hubOpenSystem(live, ::cubeSound) }
            HubHint("B", "Menu") { CubeHub.zone = CubeHub.MENU }
        }
        }
    }
}

@Composable
private fun HubCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, tint: Color, modifier: Modifier, onOpen: () -> Unit) {
    val open by rememberUpdatedState(onOpen)
    Box(modifier, contentAlignment = Alignment.Center) {
        val shape = RoundedCornerShape(20.dp)
        Column(
            Modifier.width(280.dp).clip(shape)
                .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.45f), Color(0xFF120E28).copy(alpha = 0.88f))))
                .border(1.5.dp, Color.White.copy(alpha = 0.5f), shape)
                .pointerInput(Unit) { detectTapGestures { open() } }
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(52.dp))
            Spacer(Modifier.height(10.dp))
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(sub, color = Color.White.copy(alpha = 0.72f), fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            HubHint("A", "Open") { open() }
        }
    }
}

// ---- Drawing ----

internal class HubFaceArt(val label: String, val icon: Painter)

/** Soft rays of light falling from above onto the cube. */
private fun DrawScope.hubRays(cx: Float, t: Float, tint: Color) {
    val ray = Path()
    for (i in 0 until 3) {
        val a = 0.05f + 0.025f * sin(t * 0.5f + i * 2.1f)
        val x0 = cx - size.width * 0.12f + i * size.width * 0.09f
        ray.reset()
        ray.moveTo(x0, 0f)
        ray.lineTo(x0 + size.width * 0.05f, 0f)
        ray.lineTo(x0 + size.width * (0.16f + i * 0.04f), size.height * 0.9f)
        ray.lineTo(x0 + size.width * (0.04f + i * 0.04f), size.height * 0.9f)
        ray.close()
        drawPath(ray, Brush.verticalGradient(listOf(lerp(tint, Color.White, 0.5f).copy(alpha = a.coerceAtLeast(0f)), Color.Transparent), 0f, size.height * 0.9f))
    }
}

/** Specks of light drifting up and twinkling (fewer over the library). */
private fun DrawScope.hubSpecks(t: Float, tint: Color, libX: Float) {
    val col = lerp(tint, Color.White, 0.55f)
    for (i in 0 until 46) {
        val rx = ((i * 7919) % 1000) / 1000f
        val ry = ((i * 104729) % 1000) / 1000f
        val sp = 0.008f + ((i * 31) % 10) / 1000f
        val x = rx * size.width + sin(t * 0.3f + i) * 6f
        val y = ((ry - t * sp) % 1f + 1f) % 1f * size.height
        if (x > libX && i % 3 != 0) continue
        val tw = 0.25f + 0.35f * (0.5f + 0.5f * sin(t * (1.1f + (i % 5) * 0.3f) + i * 1.7f))
        val r = (1f + (i % 4) * 0.5f) * density
        drawCircle(col.copy(alpha = tw), r, Offset(x, y))
    }
}

/** The lit ring on the floor under the cube. */
private fun DrawScope.hubFloor(cx: Float, y: Float, half: Float, tint: Color, k: Float) {
    val w = half * 3.4f
    val h = half * 0.62f
    drawOval(Brush.radialGradient(listOf(tint.copy(alpha = 0.45f * k), Color.Transparent), Offset(cx, y), w / 2f), Offset(cx - w / 2f, y - h / 2f), Size(w, h))
    for (r in 0 until 3) {
        val rw = w * (0.55f + r * 0.2f)
        val rh = h * (0.55f + r * 0.2f)
        drawOval(lerp(tint, Color.White, 0.3f).copy(alpha = (0.55f - r * 0.16f) * k), Offset(cx - rw / 2f, y - rh / 2f), Size(rw, rh), style = Stroke((1.6f - r * 0.4f) * density))
    }
}

/** Small glass cubes floating and turning round the big one. */
private fun DrawScope.hubMiniCubes(tools: CubeTools, cx: Float, cy: Float, half: Float, t: Float, tint: Color) {
    val spots = floatArrayOf(-1.9f, 0.9f, 0.16f, 1.75f, -1.2f, 0.12f, -1.6f, -1.25f, 0.1f, 2.05f, 1.15f, 0.13f, 1.2f, 1.9f, 0.09f, -0.6f, 1.95f, 0.11f)
    for (i in 0 until spots.size / 3) {
        val sx = cx + spots[i * 3] * half + sin(t * 0.4f + i) * half * 0.06f
        val sy = cy + spots[i * 3 + 1] * half + cos(t * 0.35f + i * 1.3f) * half * 0.08f
        val s = half * spots[i * 3 + 2]
        drawGlassCube(tools, sx, sy, s, Math.toRadians((t * 18f + i * 47f).toDouble()).toFloat(), Math.toRadians((22f + i * 9f).toDouble()).toFloat(), tint)
    }
}

/** A small see-through cube: its faces as faint glass, its edges lit. */
private fun DrawScope.drawGlassCube(tools: CubeTools, cx: Float, cy: Float, half: Float, yaw: Float, pitch: Float, tint: Color) {
    val cyw = cos(yaw); val syw = sin(yaw); val cp = cos(pitch); val sp = sin(pitch)
    val path = tools.path
    val edge = lerp(tint, Color.White, 0.45f)
    for (f in 0 until 6) {
        val nx: Float; val ny: Float; val nz: Float; val ux: Float; val uy: Float; val uz: Float
        when (f) {
            0, 1, 2, 3 -> { nx = RING_NX[f]; ny = 0f; nz = RING_NZ[f]; ux = 0f; uy = 1f; uz = 0f }
            4 -> { nx = 0f; ny = 1f; nz = 0f; ux = 0f; uy = 0f; uz = -1f }
            else -> { nx = 0f; ny = -1f; nz = 0f; ux = 0f; uy = 0f; uz = 1f }
        }
        val rx = uy * nz - uz * ny; val ry = uz * nx - ux * nz; val rz = ux * ny - uy * nx
        val nz1 = -nx * syw + nz * cyw
        val nzr = ny * sp + nz1 * cp
        if (nzr <= 0f) continue
        path.reset()
        for (ci in 0 until 4) {
            val sr = if (ci == 1 || ci == 2) 1f else -1f
            val su = if (ci < 2) 1f else -1f
            val x = nx + sr * rx + su * ux; val y = ny + sr * ry + su * uy; val z = nz + sr * rz + su * uz
            val x1 = x * cyw + z * syw
            val z1 = -x * syw + z * cyw
            val y2 = y * cp - z1 * sp
            val z2 = y * sp + z1 * cp
            val k = CUBE_F / (CUBE_F - z2)
            val px = cx + x1 * k * half
            val py = cy - y2 * k * half
            if (ci == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        drawPath(path, tint.copy(alpha = 0.16f + 0.1f * nzr))
        drawPath(path, edge.copy(alpha = 0.55f), style = Stroke(1.2f * density))
    }
}

/**
 * The hub's cube at ([cx], [cy]), seen from a corner: f0 (+z) [left] on the left, f1 (+x) [right] on the right, the top
 * [top]; the faces turned away drawn first as faint glass so their edges show through. [sel]: the face lit for the part
 * you're on (the f index; -2 none). [small]: the Settings cube — only its front face (f0) carries a picture.
 */
private fun DrawScope.drawHubCube(
    tools: CubeTools, hits: CubeHits, cx: Float, cy: Float, half: Float, yaw: Float, pitch: Float,
    left: HubFaceArt?, right: HubFaceArt?, top: HubFaceArt?, sel: Int, tint: Color, lit: Float, small: Boolean = false
) {
    val cyw = cos(yaw); val syw = sin(yaw); val cp = cos(pitch); val sp = sin(pitch)
    val v = tools.v
    fun rot(x: Float, y: Float, z: Float) {
        val x1 = x * cyw + z * syw
        val z1 = -x * syw + z * cyw
        v[0] = x1; v[1] = y * cp - z1 * sp; v[2] = y * sp + z1 * cp
    }
    var hitN = 0
    val glowW = (if (small) 5f else 9f) * density
    for (pass in 0..1) for (f in 0 until 6) {
        val nx: Float; val ny: Float; val nz: Float; val ux: Float; val uy: Float; val uz: Float
        when (f) {
            0, 1, 2, 3 -> { nx = RING_NX[f]; ny = 0f; nz = RING_NZ[f]; ux = 0f; uy = 1f; uz = 0f }
            4 -> { nx = 0f; ny = 1f; nz = 0f; ux = 0f; uy = 0f; uz = -1f }
            else -> { nx = 0f; ny = -1f; nz = 0f; ux = 0f; uy = 0f; uz = 1f }
        }
        val rx = uy * nz - uz * ny; val ry = uz * nx - ux * nz; val rz = ux * ny - uy * nx
        rot(nx, ny, nz)
        val nxr = v[0]; val nyr = v[1]; val nzr = v[2]
        val visible = nzr * CUBE_F > 1f
        if ((pass == 1) != visible) continue
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
        path.moveTo(tools.dst[0], tools.dst[1]); path.lineTo(tools.dst[2], tools.dst[3])
        path.lineTo(tools.dst[4], tools.dst[5]); path.lineTo(tools.dst[6], tools.dst[7]); path.close()
        if (!visible) {
            drawPath(path, tint.copy(alpha = 0.08f))
            drawPath(path, lerp(tint, Color.White, 0.5f).copy(alpha = 0.22f), style = Stroke(1.2f * density))
            continue
        }
        if (hitN < 6) {
            System.arraycopy(tools.dst, 0, hits.quads, hitN * 8, 8)
            hits.kinds[hitN] = f
            hits.ks[hitN] = 0
            hitN++
        }
        val here = f == sel
        val light = 0.5f + 0.5f * max(0f, nxr * LX + nyr * LY + nzr * LZ)
        // Glow round the face's edges (under), then the glass.
        val edge = lerp(tint, Color.White, if (here) 0.6f else 0.35f)
        drawPath(path, tint.copy(alpha = (if (here) 0.32f else 0.16f) * lit), style = Stroke(glowW * 1.8f))
        drawPath(path, tint.copy(alpha = (if (here) 0.5f else 0.28f) * lit), style = Stroke(glowW))
        val art = when (f) { 0 -> left; 1 -> right; 4 -> top; else -> null }
        hubFace(tools, art, light, tint, here, lit, rotate = if (f == 4) -45f else 0f, small = small)
        drawPath(path, edge.copy(alpha = (if (here) 1f else 0.8f) * (0.6f + 0.4f * lit)), style = Stroke((if (here) 2.6f else 1.8f) * density))
    }
    hits.count = hitN
}

/** One face of the hub's cube, in its own space mapped onto its corners: glass, sheen, rim, picture and name. */
private fun DrawScope.hubFace(tools: CubeTools, art: HubFaceArt?, light: Float, tint: Color, here: Boolean, lit: Float, rotate: Float, small: Boolean) {
    tools.matrix.reset()
    if (!tools.matrix.setPolyToPoly(tools.src, 0, tools.dst, 0, 4)) return
    val top = lerp(tint, Color.White, 0.22f + 0.2f * light)
    val bottom = lerp(Color(0xFF0C0920), tint, 0.55f * light)
    drawIntoCanvas { cv ->
        val nc = cv.nativeCanvas
        nc.save()
        nc.concat(tools.matrix)
        drawRect(Brush.verticalGradient(listOf(top.copy(alpha = if (here) 0.62f else 0.42f), bottom.copy(alpha = if (here) 0.66f else 0.5f)), 0f, FACE), Offset.Zero, Size(FACE, FACE))
        drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), Offset.Zero, Offset(FACE * 0.55f, FACE * 0.55f)), Offset.Zero, Size(FACE, FACE))
        drawRect(Color.White.copy(alpha = (if (here) 0.55f else 0.2f) * lit), Offset(16f, 16f), Size(FACE - 32f, FACE - 32f), style = Stroke(if (here) 5f else 3f))
        if (art != null) {
            if (rotate != 0f) { nc.rotate(rotate, FACE / 2f, FACE / 2f); nc.scale(0.8f, 0.8f, FACE / 2f, FACE / 2f) }
            val sz = FACE * (if (small) 0.42f else 0.34f)
            val iy = FACE * (if (small) 0.36f else 0.34f)
            // A glow behind the picture, then the picture.
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = if (here) 0.45f else 0.22f), Color.Transparent), Offset(FACE / 2f, iy), sz * 0.9f), sz * 0.9f, Offset(FACE / 2f, iy))
            translate(FACE / 2f - sz / 2f, iy - sz / 2f) {
                with(art.icon) { draw(Size(sz, sz), alpha = if (here) 1f else 0.85f, colorFilter = ColorFilter.tint(Color.White)) }
            }
            val paint = tools.text
            paint.color = android.graphics.Color.WHITE
            paint.alpha = if (here) 255 else 225
            paint.setShadowLayer(8f, 0f, 2f, android.graphics.Color.argb(170, 0, 0, 0))
            val (ts, lines) = fitLabel(tools, art.label, if (small) 62f else 50f, FACE * 0.86f, 1)
            paint.textSize = ts
            var y = FACE * (if (small) 0.80f else 0.66f)
            // (Update 294: drawn flat once and put on the face as a picture — StyleCube's flatLabel.)
            for (l in lines) { flatLabel(tools, nc, l, FACE / 2f, y, ts, if (here) 255 else 225, 8f, 2f, 170, 1f); y += ts * 1.15f }
        }
        nc.restore()
    }
}
