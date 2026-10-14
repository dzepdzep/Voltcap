package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
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
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Update 202: Green Core (2001) — Voltcap's own homage to the feel of the 2001 console dashboard (original names and
 * drawings only; nobody's logos, artwork or sounds):
 * - a near-black screen shaded dark green; a large translucent sphere on the left — a 3D grid turning slowly, a pulsing
 *   energy core, tilted rings going round, a gauge ring and faint circuit traces (GreenCoreSphere.kt);
 * - MENU: angled, see-through green panels stacked on the right, curving round the sphere, each with a small round
 *   light and letter-spaced caps: GAMES, SYSTEMS, APPS, SAVE VAULT, MUSIC, SETTINGS. The one you're on slides out and
 *   lights up; the core flashes.
 * - GAMES: the category's games as a list (or a curved carousel, Layout options) beside the sphere, the game you're on
 *   in your game look (cartridge, case, disc… — StyleArt) floating in front of the core, a technical info panel with
 *   its console, play time, last played and achievements. A opens the game window (or starts it) like everywhere,
 *   X favourite, Y achievements (Voltcap's own buttons), B back to the menu.
 * - SYSTEMS: your consoles as panels with their logos and game counts; A opens one's games.
 * The sphere slides and shrinks between the pages, the panels slide in. Start: the quick menu (R1's panel); Select: sort
 * & filter. L2 / R2 change the category, L1 / R1 as everywhere. Touch: tap / drag everything; the back gesture goes back.
 * Options: Settings › Home › Layout options (GreenCoreSettings.kt).
 */

/** Update 202: where the controller is in Green Core (kept while you play a game and come back). */
internal object GreenCoreNav {
    enum class Page { MENU, GAMES, SYSTEMS }
    var page by mutableStateOf(Page.MENU)
    /** The menu panel you're on (GcItem order). */
    var menu by mutableIntStateOf(0)
    /** The place in the systems list. */
    var system by mutableIntStateOf(0)
    /** The games page was opened from SYSTEMS (B goes back there). */
    var gamesFromSystems = false
    /** Bumped on a move in the menus / a page change: the core flashes. */
    var beat by mutableIntStateOf(0)
    @Volatile var ctx: StyleCtx? = null
    /** The systems list (indexes into the category cycle), as last drawn. */
    @Volatile var systems: List<Int> = emptyList()
    internal val keysDown = HashSet<Int>()
    /** Everything the controller moves here, for the "▼ changed nothing" check (LauncherScreen). */
    fun signature(): Any = listOf(page, menu, system)
}

/** Update 202: the home screen's hotkey actions (app drawer, music library, sort, search), set by LauncherScreen. */
internal object GreenCoreHooks {
    @Volatile var hotkey: ((HotkeyAction) -> Unit)? = null
}

private enum class GcItem(val label: String) {
    GAMES("GAMES"), SYSTEMS("SYSTEMS"), APPS("APPS"), SAVES("SAVE VAULT"), MUSIC("MUSIC"), SETTINGS("SETTINGS")
}

/** Where the sphere sits on each page: centre x / y (shares of the area), size, brightness. */
private const val GC_GAMES_X = 0.17f
private const val GC_GAMES_Y = 0.55f
private const val GC_SYS_X = 0.25f
private const val GC_SYS_Y = 0.53f

private fun gcPlace(page: GreenCoreNav.Page, carousel: Boolean): FloatArray = when (page) {
    GreenCoreNav.Page.MENU -> floatArrayOf(0.29f, 0.53f, 1f, 1f)
    GreenCoreNav.Page.GAMES -> if (carousel) floatArrayOf(0.5f, 0.34f, 0.78f, 0.8f) else floatArrayOf(GC_GAMES_X, GC_GAMES_Y, 0.8f, 0.92f)
    GreenCoreNav.Page.SYSTEMS -> floatArrayOf(GC_SYS_X, GC_SYS_Y, 0.9f, 1f)
}

// ---- Sounds ----

/** The layout's own synthesised sound when they're on, else Voltcap's usual one ([fallback], or none). */
private fun gcSound(s: GcSound, fallback: UiSound?) {
    if (GreenCoreSounds.on) GreenCoreSounds.play(s) else if (fallback != null) PocketSounds.play?.invoke(fallback)
}

private fun gcHotkey(a: HotkeyAction): Boolean {
    val h = GreenCoreHooks.hotkey ?: return false
    h(a)
    return true
}

// ---- What the panels do ----

/** The consoles in the category cycle (indexes into its stops); without any, every stop. */
internal fun gcSystems(c: StyleCtx): List<Int> {
    val consoles = c.stops.indices.filter { i ->
        val st = c.stops[i]
        val p = st.platform
        st.special == null && p != null && !com.shelf.launcher.data.isCollectionKey(p)
    }
    return consoles.ifEmpty { c.stops.indices.toList() }
}

private fun gcGo(p: GreenCoreNav.Page) {
    if (GreenCoreNav.page == p) return
    GreenCoreNav.page = p
    GreenCoreNav.beat++
    gcSound(GcSound.WHOOSH, UiSound.OPEN)
}

private fun gcMenuTo(i: Int) {
    if (GreenCoreNav.menu == i) return
    GreenCoreNav.menu = i
    GreenCoreNav.beat++
    gcSound(GcSound.CLICK, UiSound.MOVE)
}

internal fun gcActivate(c: StyleCtx, item: Int) {
    val what = GcItem.values().getOrNull(item) ?: return
    GreenCoreNav.menu = item
    when (what) {
        GcItem.GAMES -> { GreenCoreNav.gamesFromSystems = false; gcGo(GreenCoreNav.Page.GAMES) }
        GcItem.SYSTEMS -> {
            GreenCoreNav.system = GreenCoreNav.systems.indexOf(c.stop).coerceAtLeast(0)
            gcGo(GreenCoreNav.Page.SYSTEMS)
        }
        // The app drawer (as the All apps shortcut / hotkey).
        GcItem.APPS -> {
            gcSound(GcSound.CONFIRM, UiSound.OPEN)
            if (!gcHotkey(HotkeyAction.APP_DRAWER)) c.shortcuts.firstOrNull { s -> s.label == "All apps" }?.onClick?.invoke()
        }
        // The memory-card save browser (as the R1 tile does).
        GcItem.SAVES -> { gcSound(GcSound.CONFIRM, UiSound.OPEN); SaveExplorerHook.request() }
        // The music library and player (as the Music shortcut).
        GcItem.MUSIC -> {
            gcSound(GcSound.CONFIRM, UiSound.OPEN)
            if (!gcHotkey(HotkeyAction.OPEN_SPOTIFY)) MusicSwipe.handler?.invoke()
        }
        // Voltcap's settings menu (it plays its own sound).
        GcItem.SETTINGS -> c.actions.settings()
    }
}

/** Opens the games of the system at [pos] in the systems list (as many L2 / R2 steps as it is away). */
private fun gcPickSystem(c: StyleCtx, pos: Int) {
    val idx = GreenCoreNav.systems.getOrNull(pos) ?: return
    GreenCoreNav.system = pos
    val steps = pocketStepsTo(c, idx)
    if (steps != 0) c.actions.category(steps)
    GreenCoreNav.gamesFromSystems = true
    GreenCoreNav.page = GreenCoreNav.Page.GAMES
    GreenCoreNav.beat++
    gcSound(GcSound.CONFIRM, if (steps == 0) UiSound.SELECT else null)
}

/** B / the back gesture: games → systems (when opened from there) or the menu; systems → the menu. */
internal fun gcBack(c: StyleCtx?) {
    when (GreenCoreNav.page) {
        GreenCoreNav.Page.GAMES -> if (GreenCoreNav.gamesFromSystems) {
            GreenCoreNav.page = GreenCoreNav.Page.SYSTEMS
            val at = if (c != null) GreenCoreNav.systems.indexOf(c.stop) else -1
            if (at >= 0) GreenCoreNav.system = at
        } else GreenCoreNav.page = GreenCoreNav.Page.MENU
        GreenCoreNav.Page.SYSTEMS -> GreenCoreNav.page = GreenCoreNav.Page.MENU
        GreenCoreNav.Page.MENU -> return
    }
    GreenCoreNav.gamesFromSystems = false
    GreenCoreNav.beat++
    gcSound(GcSound.BACK, UiSound.BACK)
}

/** One game on ([d] ±1, Endless goes round) or a jump ([jump]: stops at the ends). True when it moved. */
private fun gcStepGame(c: StyleCtx, d: Int, jump: Boolean = false): Boolean {
    val n = c.games.size
    if (n == 0) return false
    val next = if (!jump && StyleLoop.on && n >= 2) (c.focus + d).mod(n) else (c.focus + d).coerceIn(0, n - 1)
    if (next == c.focus) return false
    c.actions.select(next)
    gcSound(GcSound.CLICK, UiSound.MOVE)
    return true
}

/**
 * Update 202: Green Core's keys (all of them while it's on, before the home screen's own). True = handled.
 * MENU: ▲ ▼ between the panels, A (or ▶ on GAMES / SYSTEMS) opens; B is the home screen's own.
 * GAMES: ▲ ▼ (◀ ▶ on the carousel) the game, ◀ ▶ (▲ ▼) six at a time; A / X / Y / L / R are the home screen's own (the
 * game window, favourite, achievements, categories…); B back.
 * SYSTEMS: ▲ ▼ the system, ◀ ▶ five at a time, A its games, B back.
 * Start (on release): the quick menu; Select (on release, not as a combo): sort & filter.
 */
internal fun greenCoreKey(event: KeyEvent, reach: (Boolean) -> Boolean): Boolean {
    val c = GreenCoreNav.ctx ?: return false
    val code = event.nativeKeyEvent.keyCode
    val down = event.type == KeyEventType.KeyDown
    val up = event.type == KeyEventType.KeyUp
    if (up && GreenCoreNav.keysDown.remove(code)) return true
    val k = event.key
    // Start: the R1 quick menu. Select: sort & filter (the Sort hotkey). Both on release; their presses are claimed by
    // the home screen (Select is also the combo key: a combo never reaches here).
    if (k == Key.ButtonStart || k == Key.Menu) {
        if (up) c.actions.quickMenu()
        return up
    }
    if (k == Key.ButtonSelect) return up && gcHotkey(HotkeyAction.SORT)
    val page = GreenCoreNav.page
    val isA = k == Key.ButtonA || k == Key.Enter || k == Key.DirectionCenter
    // A on the menu / systems acts on release (what it opens never gets the release).
    if (isA && page != GreenCoreNav.Page.GAMES) {
        if (up && !event.nativeKeyEvent.isCanceled) {
            if (page == GreenCoreNav.Page.MENU) gcActivate(c, GreenCoreNav.menu.coerceIn(0, GcItem.values().size - 1))
            else gcPickSystem(c, GreenCoreNav.system)
        }
        return true
    }
    if (!down) return false
    val first = event.nativeKeyEvent.repeatCount == 0
    fun take(): Boolean { GreenCoreNav.keysDown += code; return true }
    val isB = k == Key.ButtonB || k == Key.Back
    return when (page) {
        GreenCoreNav.Page.MENU -> {
            val last = GcItem.values().size - 1
            val m = GreenCoreNav.menu.coerceIn(0, last)
            when (k) {
                Key.DirectionUp -> { if (m > 0) gcMenuTo(m - 1) else if (first) reach(true); take() }
                Key.DirectionDown -> { if (m < last) gcMenuTo(m + 1) else if (first) reach(false); take() }
                Key.DirectionRight -> { if (first && m <= 1) gcActivate(c, m); take() }
                Key.DirectionLeft -> take()
                // No game is shown on the menu: X / Y wait for the games page.
                Key.ButtonX, Key.ButtonY -> take()
                // B (the category screen), L1 / R1 / L2 / R2: the home screen's own.
                else -> false
            }
        }
        GreenCoreNav.Page.GAMES -> {
            val carousel = Features.gcBrowse.value == Features.GcBrowse.CAROUSEL
            val isDir = k == Key.DirectionUp || k == Key.DirectionDown || k == Key.DirectionLeft || k == Key.DirectionRight
            val vertical = k == Key.DirectionUp || k == Key.DirectionDown
            val fwd = k == Key.DirectionDown || k == Key.DirectionRight
            when {
                isB -> { if (first) gcBack(c); take() }
                // A (the game window), X, Y, L / R: the home screen's own — nothing to act on without games.
                !isDir -> if (c.games.isEmpty() && (k == Key.ButtonX || k == Key.ButtonY || isA)) take() else false
                (if (carousel) !vertical else vertical) -> {
                    if (!gcStepGame(c, if (fwd) 1 else -1) && first && vertical) reach(!fwd)
                    take()
                }
                else -> { gcStepGame(c, if (fwd) 6 else -6, jump = true); take() }
            }
        }
        GreenCoreNav.Page.SYSTEMS -> {
            val n = GreenCoreNav.systems.size
            val cur = GreenCoreNav.system
            val next: Int? = when (k) {
                Key.DirectionUp -> cur - 1
                Key.DirectionDown -> cur + 1
                Key.DirectionLeft -> cur - 5
                Key.DirectionRight -> cur + 5
                else -> null
            }
            when {
                isB -> { if (first) gcBack(c); take() }
                k == Key.ButtonX || k == Key.ButtonY -> take()
                next == null -> false
                else -> {
                    val clamped = next.coerceIn(0, (n - 1).coerceAtLeast(0))
                    if (clamped != cur) {
                        GreenCoreNav.system = clamped
                        GreenCoreNav.beat++
                        gcSound(GcSound.CLICK, UiSound.MOVE)
                    } else if (first && (k == Key.DirectionUp || k == Key.DirectionDown)) reach(k == Key.DirectionUp)
                    take()
                }
            }
        }
    }
}

// ---- The layout ----

@Composable
internal fun StyleGreenCore(c: StyleCtx, modifier: Modifier = Modifier) {
    val systems = remember(c.stops) { gcSystems(c) }
    SideEffect {
        GreenCoreNav.ctx = c
        GreenCoreNav.systems = systems
        StyleNav.side = emptyList(); StyleNav.top = emptyList(); StyleNav.bar = emptyList()
        StyleLoop.on = loopRowOn(c)
    }
    // Sounds: the samples made / loaded once (off the main thread); the hum while the layout is in use.
    val context = LocalContext.current
    val master = c.state.appearance.soundsEnabled
    val soundsOn = Features.gcSounds.state.value
    val humOn = Features.gcHum.state.value
    val volume = Features.gcVolume.state.value
    val active = rememberIsActive()
    val windowOpen = GamePopup.game != null
    SideEffect { GreenCoreSounds.master = master }
    LaunchedEffect(soundsOn) {
        if (soundsOn) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { GreenCoreSounds.prepare(context) }
    }
    val humPlay = humOn && master && active && !windowOpen
    LaunchedEffect(humPlay, volume) { GreenCoreSounds.hum(humPlay, volume / 100f) }
    DisposableEffect(Unit) {
        onDispose {
            GreenCoreSounds.stopHum()
            GreenCoreNav.keysDown.clear()
        }
    }
    val page = GreenCoreNav.page
    // The back gesture (touch) goes back a page, like B.
    androidx.activity.compose.BackHandler(enabled = page != GreenCoreNav.Page.MENU && !windowOpen) { gcBack(c) }

    // Motion: Layout options › Reduced motion (or the system's animations off) keeps the sphere still and the pages
    // change without sliding; it rests while idle / hidden / a game window is open.
    val reduced = Features.gcReducedMotion.state.value || !rememberAnimationsEnabled()
    val lite = LocalLiteEffects.current
    val speed = Features.gcSphereSpeed.state.value / 100f
    val running = active && !reduced && speed > 0f && !HomeIdle.still && !windowOpen
    val fps = when {
        HomeIdle.calm -> 15
        lite -> 30
        else -> 60
    }
    val clock = rememberGcClock(running, speed, fps)
    val carousel = Features.gcBrowse.state.value == Features.GcBrowse.CAROUSEL
    val place = gcPlace(page, carousel)
    val spec: AnimationSpec<Float> = if (reduced) snap() else tween(560, easing = FastOutSlowInEasing)
    val sx = animateFloatAsState(place[0], spec, label = "gcX")
    val sy = animateFloatAsState(place[1], spec, label = "gcY")
    val ss = animateFloatAsState(place[2], spec, label = "gcS")
    val sd = animateFloatAsState(place[3], spec, label = "gcD")
    val flash = remember { Animatable(0f) }
    val beat = GreenCoreNav.beat
    LaunchedEffect(beat) {
        if (beat == 0 || reduced) return@LaunchedEffect
        flash.snapTo(1f)
        flash.animateTo(0f, tween(520))
    }
    BoxWithConstraints(modifier) {
        val compact = maxHeight < 380.dp
        val boxW = maxWidth
        GreenCoreSphere(
            Modifier.fillMaxSize(), clock, sx, sy, ss, sd, { flash.value }, lite,
            bgMotion = Features.gcBgMotion.state.value && !reduced
        )
        // The page's panels slide in from the right as it changes (mechanical: a short ease, no bounce).
        val enter = remember { Animatable(0f) }
        LaunchedEffect(page) {
            if (reduced) enter.snapTo(1f)
            else {
                enter.snapTo(0f)
                enter.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
            }
        }
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val e = enter.value
                alpha = e
                translationX = (1f - e) * 56.dp.toPx()
            }
        ) {
            when (page) {
                GreenCoreNav.Page.MENU -> GcMenu(c, systems.size, compact, reduced, boxW)
                GreenCoreNav.Page.GAMES -> if (carousel) GcCarousel(c, compact, reduced) else GcGameList(c, compact, reduced)
                GreenCoreNav.Page.SYSTEMS -> GcSystems(c, systems, compact, reduced)
            }
        }
    }
}

// ---- MENU ----

@Composable
private fun GcMenu(c: StyleCtx, systemCount: Int, compact: Boolean, reduced: Boolean, width: Dp) {
    val accent = greenCoreAccent()
    val live by rememberUpdatedState(c)
    val panelH = if (compact) 40.dp else 50.dp
    val gap = if (compact) 6.dp else 9.dp
    val colW = (width * 0.44f).coerceIn(220.dp, 430.dp)
    val song = c.state.nowPlaying?.title?.takeIf { it.isNotBlank() }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 2.dp)) {
            Text("GREEN CORE", color = Color(0xFFDDF7E1), fontSize = if (compact) 12.sp else 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp, maxLines = 1, softWrap = false)
            Text(
                "${c.state.games.size} TITLES ONLINE · ${systemCount} SYSTEMS", color = accent.copy(alpha = 0.7f), fontSize = 9.sp,
                fontFamily = FontFamily.Monospace, letterSpacing = 1.5.sp, maxLines = 1, softWrap = false
            )
        }
        Column(
            Modifier.align(Alignment.CenterEnd).width(colW).padding(end = 6.dp),
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            GcItem.values().forEachIndexed { i, item ->
                val sub = when (item) {
                    GcItem.GAMES -> "${c.categoryLabel.uppercase()} · ${c.games.size} TITLES"
                    GcItem.SYSTEMS -> "$systemCount IN YOUR LIBRARY"
                    GcItem.APPS -> "ANDROID APPLICATIONS"
                    GcItem.SAVES -> "MEMORY · CLOUD SAVES"
                    GcItem.MUSIC -> song?.let { "PLAYING · ${it.uppercase()}" } ?: "LIBRARY & PLAYER"
                    GcItem.SETTINGS -> "SYSTEM CONFIGURATION"
                }
                GcMenuPanel(i, item.label, sub, GreenCoreNav.menu == i, panelH, accent, reduced) {
                    gcActivate(live, i)
                }
            }
        }
        Text(
            "Ⓐ SELECT   START QUICK MENU   SELECT SORT", color = accent.copy(alpha = 0.55f), fontSize = 9.sp,
            fontFamily = FontFamily.Monospace, letterSpacing = 1.sp, maxLines = 1, softWrap = false,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 4.dp, bottom = 2.dp)
        )
    }
}

/** One angled menu panel: the round light, the name in letter-spaced caps, a line under it; lit and slid out when on. */
@Composable
private fun GcMenuPanel(i: Int, label: String, sub: String, on: Boolean, height: Dp, accent: Color, reduced: Boolean, onTap: () -> Unit) {
    val tap by rememberUpdatedState(onTap)
    val lit = animateFloatAsState(if (on) 1f else 0f, if (reduced) snap() else tween(200, easing = FastOutSlowInEasing), label = "gcPanel")
    val shape = remember(height) { GcPanelShape(height * 0.3f, height * 0.32f) }
    val glow = Features.gcGlow.state.value / 100f
    val bend = abs(i - 2.5f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            // The panels' inner edges curve round the sphere (the middle ones reach furthest in).
            .padding(start = (bend * bend * 7f).dp)
            .graphicsLayer { translationX = -16.dp.toPx() * lit.value }
            .drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                val base = Brush.horizontalGradient(listOf(Color(0xCC08160C), Color(0x8C051008)))
                val fill = Brush.horizontalGradient(listOf(accent.copy(alpha = 0.46f), accent.copy(alpha = 0.10f)))
                val edge = Stroke(1.dp.toPx())
                val halo = Stroke(7.dp.toPx())
                onDrawBehind {
                    val l = lit.value
                    drawOutline(outline, base)
                    if (l > 0f) {
                        drawOutline(outline, fill, alpha = l)
                        if (glow > 0f) drawOutline(outline, accent.copy(alpha = 0.2f * glow * l), style = halo)
                    }
                    drawOutline(outline, accent.copy(alpha = 0.28f + 0.67f * l), style = edge)
                }
            }
            .pointerInput(i) { detectTapGestures { tap() } }
            .padding(start = height * 0.46f, end = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GcLight(accent, lit, Modifier.size(height * 0.28f))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    label, color = if (on) Color(0xFFF2FFF4) else Color(0xFFB4D6BB), fontSize = if (height < 46.dp) 15.sp else 18.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 3.sp, maxLines = 1, softWrap = false
                )
                Text(
                    sub, color = accent.copy(alpha = if (on) 0.95f else 0.5f), fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** The small round light on a panel: a ring, a dot that grows and glows when it's on. */
@Composable
private fun GcLight(accent: Color, lit: State<Float>, modifier: Modifier) {
    Spacer(
        modifier.drawWithCache {
            val ring = Stroke(1.4.dp.toPx())
            onDrawBehind {
                val r = size.minDimension / 2f
                val l = lit.value
                if (l > 0f) drawCircle(accent.copy(alpha = 0.22f * l), r * 1.7f)
                drawCircle(accent.copy(alpha = 0.5f + 0.5f * l), r, style = ring)
                drawCircle(accent.copy(alpha = 0.18f + 0.82f * l), r * (0.42f + 0.16f * l))
            }
        }
    )
}

// ---- GAMES: the list ----

@Composable
private fun GcGameList(c: StyleCtx, compact: Boolean, reduced: Boolean) {
    val accent = greenCoreAccent()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        val g = c.game
        // The game you're on, in your game look, floating in front of the sphere's core.
        if (g != null) GcPreview(c, g, w * GC_GAMES_X, h * GC_GAMES_Y, w * 0.3f, h * 0.6f, accent)
        Column(Modifier.align(Alignment.TopEnd).width(w * 0.64f).fillMaxHeight()) {
            GcHeader(c, "GAMES · ${c.categoryLabel.uppercase()}", if (c.games.isEmpty()) null else "${c.focus + 1} / ${c.games.size}", accent, compact, tools = true)
            Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
            if (c.games.isEmpty()) {
                GcNothing("NO TITLES IN THIS CATEGORY · L2 / R2 FOR ANOTHER", accent, Modifier.weight(1f).fillMaxWidth())
            } else {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    GcList(c, compact, reduced, accent, Modifier.weight(0.56f).fillMaxHeight())
                    Spacer(Modifier.width(10.dp))
                    GcInfoPanel(c, g, compact, accent, Modifier.weight(0.44f).fillMaxHeight().padding(bottom = 4.dp))
                }
            }
        }
    }
}

/** The game you're on as your game look (StyleArt: cartridge, case, disc, cover…) at the sphere's core; a tap opens it. */
@Composable
private fun GcPreview(c: StyleCtx, g: Game, cx: Dp, cy: Dp, maxW: Dp, maxH: Dp, accent: Color) {
    val aspect = if (spinesOn(c.type)) 0.5f else styleAspect(c.type).coerceIn(0.5f, 1.9f)
    val h = minOf(maxH, maxW / aspect)
    val w = h * aspect
    val open by rememberUpdatedState(c.actions.open)
    val pulse = rememberFocusPulse(animate = !LocalLiteEffects.current)
    Box(
        Modifier.offset(x = cx - w / 2, y = cy - h / 2).size(w, h)
            .pointerInput(Unit) { detectTapGestures { open() } }
    ) {
        AnimatedContent(targetState = g, label = "gcPreview", transitionSpec = { gameSwap() }) { shown ->
            StyleArt(c, shown, Modifier.size(w, h), focused = true, fx = pulse, ring = accent)
        }
    }
}

/**
 * The category's games down a column, the one you're on held a little above the middle, the list gliding to it. Only
 * the rows near it are composed (big libraries stay light); their places are read while laying out (no recomposition
 * while it glides). A drag moves through them, a tap picks one, a tap on the one you're on opens it.
 */
@Composable
private fun GcList(c: StyleCtx, compact: Boolean, reduced: Boolean, accent: Color, modifier: Modifier) {
    val n = c.games.size
    val rowH = if (compact) 38.dp else 46.dp
    val pos = remember { Animatable(c.focus.toFloat()) }
    LaunchedEffect(c.focus, n) {
        val target = c.focus.toFloat()
        if (reduced || abs(pos.value - target) > 8f) pos.snapTo(target)
        else pos.animateTo(target, tween(170, easing = FastOutSlowInEasing))
    }
    val live by rememberUpdatedState(c)
    val density = LocalDensity.current
    val rowPx = with(density) { rowH.toPx() }
    BoxWithConstraints(
        modifier.clipToBounds().pointerInput(n) {
            var acc = 0f
            var target = 0
            detectVerticalDragGestures(
                onDragStart = { acc = 0f; target = live.focus },
                onDragEnd = { acc = 0f },
                onDragCancel = { acc = 0f }
            ) { change, dy ->
                change.consume()
                acc += dy
                while (acc <= -rowPx) { acc += rowPx; if (target < n - 1) { target++; live.actions.select(target); gcSound(GcSound.CLICK, UiSound.MOVE) } }
                while (acc >= rowPx) { acc -= rowPx; if (target > 0) { target--; live.actions.select(target); gcSound(GcSound.CLICK, UiSound.MOVE) } }
            }
        }
    ) {
        val topPx = with(density) { (maxHeight * 0.42f).toPx() } - rowPx / 2f
        val half = (maxHeight / rowH).toInt() / 2 + 2
        val from = (c.focus - half).coerceAtLeast(0)
        val to = (c.focus + half).coerceAtMost(n - 1)
        for (i in from..to) {
            key(i) {
                GcGameRow(
                    c, i, i == c.focus, rowH, compact, accent,
                    Modifier
                        .offset { IntOffset(0, (topPx + (i - pos.value) * rowPx).roundToInt()) }
                        .graphicsLayer { alpha = (1f - abs(i - pos.value) / (half + 1f)).coerceIn(0.12f, 1f) }
                        .fillMaxWidth()
                        .height(rowH - 5.dp)
                )
            }
        }
    }
}

@Composable
private fun GcGameRow(c: StyleCtx, i: Int, on: Boolean, rowH: Dp, compact: Boolean, accent: Color, modifier: Modifier) {
    val g = c.games[i]
    val titles = LocalTitleOptions.current
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    val shape = remember(rowH) { GcPanelShape(rowH * 0.24f, rowH * 0.2f) }
    val fav = g.stableKey in c.state.favorites
    Row(
        modifier
            .clip(shape)
            .background(
                if (on) Brush.horizontalGradient(listOf(accent.copy(alpha = 0.40f), accent.copy(alpha = 0.10f)))
                else Brush.horizontalGradient(listOf(Color(0xB308160C), Color(0x7A051008)))
            )
            .border(1.dp, accent.copy(alpha = if (on) 0.9f else 0.22f), shape)
            .pointerInput(i) {
                detectTapGestures {
                    if (i == GridHome.focus) open() else { select(i); gcSound(GcSound.CLICK, UiSound.MOVE) }
                }
            }
            .padding(start = rowH * 0.26f, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The glowing selection mark.
        Box(
            Modifier.width(3.dp).height(rowH * 0.5f).clip(RoundedCornerShape(2.dp))
                .background(if (on) accent else accent.copy(alpha = 0.18f))
        )
        Spacer(Modifier.width(7.dp))
        Box(Modifier.size(rowH - 14.dp).clip(RoundedCornerShape(3.dp)).background(placeholderBrush(g.hue))) { GcThumb(c, g) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                titles.display(g.name), color = if (on) Color.White else Color(0xFFC2E2C8), fontSize = if (compact) 12.sp else 14.sp,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                titles.console(g.platform).uppercase(), color = accent.copy(alpha = if (on) 0.95f else 0.5f), fontSize = 9.sp,
                fontFamily = FontFamily.Monospace, letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (fav) Icon(Icons.Filled.Star, "Favourite", tint = accent, modifier = Modifier.size(14.dp))
    }
}

/** A row's small picture: your game look when it's a real thing (cartridge, case, disc), else the cover. */
@Composable
private fun GcThumb(c: StyleCtx, g: Game) {
    if (pocketUsesLook(c.type)) PocketLookArt(c, g, Modifier.fillMaxSize())
    else {
        val model = coverOrAppIcon(g, c.cover(g))
        // (Update 202: the cover with its file's flat strips trimmed — TrimMargins.kt — in the cards' shared shape; an
        // app's icon as it is. Crop fills the thumb.)
        if (model != null) AsyncImage(if (model is String) rememberStyleArtRequest(model, trim = true) else model, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().pixelArtwork())
    }
}

/** The technical info panel: the title, its console, play time, last played, achievements; Play and Details. */
@Composable
private fun GcInfoPanel(c: StyleCtx, g: Game?, compact: Boolean, accent: Color, modifier: Modifier) {
    val shape = remember { GcPanelShape(14.dp, 0.dp) }
    Box(
        modifier.clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xD9081710), Color(0xCC040C07))))
            .border(1.dp, accent.copy(alpha = 0.45f), shape)
    ) {
        if (g != null) {
            AnimatedContent(targetState = g, label = "gcInfo", transitionSpec = { gameSwap() }, modifier = Modifier.fillMaxSize()) { shown ->
                GcInfo(c, shown, compact, accent)
            }
        }
    }
}

@Composable
private fun GcInfo(c: StyleCtx, g: Game, compact: Boolean, accent: Color) {
    val titles = LocalTitleOptions.current
    val play by rememberUpdatedState(c.actions.play)
    val details by rememberUpdatedState(c.actions.details)
    val progress = c.progress(g)
    val last = c.lastPlayed(g)
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = if (compact) 8.dp else 12.dp)) {
        GcLabel("SELECTED TITLE", accent)
        Spacer(Modifier.height(3.dp))
        FitText(titles.display(g.name), color = Color.White, maxSize = if (compact) 15.sp else 20.sp, minSize = 11.sp, maxLines = 2, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.height(if (compact) 14.dp else 18.dp).width(54.dp), contentAlignment = Alignment.CenterStart) {
                PlatformLogo(g.platform, titles.console(g.platform), Modifier.fillMaxSize(), textSize = 9.sp)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                titles.console(g.platform).uppercase(), color = accent, fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(if (compact) 5.dp else 8.dp))
        GcRule(accent)
        Spacer(Modifier.height(if (compact) 3.dp else 6.dp))
        GcStat("PLAY TIME", stylePlaytime(c.playtime(g), last != null).uppercase(), accent)
        GcStat("LAST PLAYED", last?.let { lastPlayedLabel(it).uppercase() } ?: "NEVER", accent)
        GcStat("ACHIEVEMENTS", progress?.let { "${it.first} / ${it.second}" } ?: "NONE", accent)
        if (progress != null) GcBar(progress, accent)
        if (g.stableKey in c.state.favorites) GcStat("STATUS", "★ FAVOURITE", accent)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GcButton("PLAY", primary = true, accent = accent, modifier = Modifier.weight(1f)) { play(g) }
            GcButton("DETAILS", primary = false, accent = accent, modifier = Modifier.weight(1f)) { details() }
        }
    }
}

// ---- GAMES: the curved carousel ----

/**
 * The games on a curve in front of the sphere: the one you're on big in the middle, the others turning away and rising
 * round the curve, smaller and fainter. Each drawn in your game look. ◀ ▶ (or a drag) move, a tap picks / opens.
 */
@Composable
private fun GcCarousel(c: StyleCtx, compact: Boolean, reduced: Boolean) {
    val accent = greenCoreAccent()
    val n = c.games.size
    val pos = remember { Animatable(c.focus.toFloat()) }
    LaunchedEffect(c.focus, n) {
        val target = c.focus.toFloat()
        if (reduced || abs(pos.value - target) > 6f) pos.snapTo(target)
        else pos.animateTo(target, tween(260, easing = FastOutSlowInEasing))
    }
    val live by rememberUpdatedState(c)
    val pulse = rememberFocusPulse(animate = !LocalLiteEffects.current)
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        if (n == 0) {
            GcNothing("NO TITLES IN THIS CATEGORY · L2 / R2 FOR ANOTHER", accent, Modifier.fillMaxSize())
        } else {
            val aspect = if (spinesOn(c.type)) 0.5f else styleAspect(c.type).coerceIn(0.5f, 1.9f)
            val stripH = if (compact) 62.dp else 76.dp
            val itemH = minOf((h - stripH - 40.dp) * 0.82f, (w * 0.26f) / aspect).coerceAtLeast(60.dp)
            val itemW = itemH * aspect
            val wPx = with(density) { itemW.toPx() }
            val cxPx = with(density) { (w / 2).toPx() }
            val baseY = with(density) { (h - stripH - 10.dp - itemH).toPx() }
            val rx = with(density) { (w * 0.42f).toPx() }
            val lift = with(density) { (h * 0.2f).toPx() }
            val stepPx = with(density) { 56.dp.toPx() }
            Box(
                Modifier.fillMaxSize().pointerInput(n) {
                    var acc = 0f
                    var target = 0
                    detectHorizontalDragGestures(
                        onDragStart = { acc = 0f; target = live.focus },
                        onDragEnd = { acc = 0f },
                        onDragCancel = { acc = 0f }
                    ) { change, dx ->
                        change.consume()
                        acc += dx
                        while (acc <= -stepPx) { acc += stepPx; if (target < n - 1) { target++; live.actions.select(target); gcSound(GcSound.CLICK, UiSound.MOVE) } }
                        while (acc >= stepPx) { acc -= stepPx; if (target > 0) { target--; live.actions.select(target); gcSound(GcSound.CLICK, UiSound.MOVE) } }
                    }
                }
            ) {
                val from = (c.focus - 4).coerceAtLeast(0)
                val to = (c.focus + 4).coerceAtMost(n - 1)
                for (i in from..to) {
                    key(i) {
                        val on = i == c.focus
                        val select by rememberUpdatedState(c.actions.select)
                        val open by rememberUpdatedState(c.actions.open)
                        Box(
                            Modifier
                                .zIndex(-abs(i - c.focus).toFloat())
                                .offset {
                                    val a = ((i - pos.value) * 0.36f).coerceIn(-1.5f, 1.5f)
                                    IntOffset((cxPx + sin(a) * rx - wPx / 2f).roundToInt(), (baseY - (1f - cos(a)) * lift).roundToInt())
                                }
                                .size(itemW, itemH)
                                .graphicsLayer {
                                    val a = ((i - pos.value) * 0.36f).coerceIn(-1.5f, 1.5f)
                                    val k = 0.5f + 0.5f * cos(a)
                                    scaleX = k; scaleY = k
                                    alpha = (cos(a) * 1.3f - 0.15f).coerceIn(0f, 1f)
                                    rotationY = -a * 30f
                                    cameraDistance = 12f * this.density
                                }
                                .pointerInput(i) {
                                    detectTapGestures {
                                        if (i == GridHome.focus) open() else { select(i); gcSound(GcSound.CLICK, UiSound.MOVE) }
                                    }
                                }
                        ) {
                            StyleArt(c, c.games[i], Modifier.fillMaxSize(), focused = on, fx = if (on) pulse else null, ring = accent)
                        }
                    }
                }
            }
            val g = c.game
            if (g != null) GcInfoStrip(c, g, compact, accent, Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.94f).height(stripH))
        }
        // (Drawn last: over the drag area, so its chips take taps.)
        GcHeader(
            c, "GAMES · ${c.categoryLabel.uppercase()}", if (n == 0) null else "${c.focus + 1} / $n", accent, compact, tools = true,
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth()
        )
    }
}

/** The carousel's info along the bottom: name and console on the left, the readouts, Play and Details on the right. */
@Composable
private fun GcInfoStrip(c: StyleCtx, g: Game, compact: Boolean, accent: Color, modifier: Modifier) {
    val titles = LocalTitleOptions.current
    val play by rememberUpdatedState(c.actions.play)
    val details by rememberUpdatedState(c.actions.details)
    val shape = remember { GcPanelShape(12.dp, 10.dp) }
    val progress = c.progress(g)
    val last = c.lastPlayed(g)
    Row(
        modifier.clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xD9081710), Color(0xCC040C07))))
            .border(1.dp, accent.copy(alpha = 0.45f), shape)
            .padding(start = 18.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            FitText(titles.display(g.name), color = Color.White, maxSize = if (compact) 14.sp else 17.sp, minSize = 10.sp, maxLines = 1, fontWeight = FontWeight.Bold)
            Text(
                titles.console(g.platform).uppercase() + " · " + stylePlaytime(c.playtime(g), last != null).uppercase() + " · " +
                    (last?.let { lastPlayedLabel(it).uppercase() } ?: "NEVER PLAYED") +
                    (progress?.let { " · ${it.first}/${it.second} ACHIEVEMENTS" } ?: ""),
                color = accent.copy(alpha = 0.85f), fontSize = 9.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(10.dp))
        GcButton("PLAY", primary = true, accent = accent, modifier = Modifier.width(if (compact) 72.dp else 88.dp)) { play(g) }
        Spacer(Modifier.width(8.dp))
        GcButton("DETAILS", primary = false, accent = accent, modifier = Modifier.width(if (compact) 80.dp else 96.dp)) { details() }
    }
}

// ---- SYSTEMS ----

/**
 * Your consoles as panels down the right, shifting round the sphere as you move (the ones further from yours lean out);
 * the one you're on lit, its logo big over the sphere's core with its game count. A opens its games.
 */
@Composable
private fun GcSystems(c: StyleCtx, systems: List<Int>, compact: Boolean, reduced: Boolean) {
    val accent = greenCoreAccent()
    val titles = LocalTitleOptions.current
    val counts = remember(c.state.games) { c.state.games.groupingBy { it.platform }.eachCount() }
    val n = systems.size
    val cur = GreenCoreNav.system.coerceIn(0, (n - 1).coerceAtLeast(0))
    val pos = remember { Animatable(cur.toFloat()) }
    LaunchedEffect(cur) {
        if (reduced || abs(pos.value - cur) > 8f) pos.snapTo(cur.toFloat())
        else pos.animateTo(cur.toFloat(), tween(200, easing = FastOutSlowInEasing))
    }
    val live by rememberUpdatedState(c)
    val density = LocalDensity.current
    val rowH = if (compact) 40.dp else 50.dp
    val rowPx = with(density) { rowH.toPx() }
    val bendPx = with(density) { 3.dp.toPx() }
    fun countOf(at: Int): Int? {
        val idx = systems.getOrNull(at) ?: return null
        if (idx == c.stop) return c.games.size
        val p = c.stops.getOrNull(idx)?.platform ?: return null
        return if (com.shelf.launcher.data.isCollectionKey(p)) null else counts[p] ?: 0
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        // The system you're on, big over the core.
        val st = systems.getOrNull(cur)?.let { c.stops.getOrNull(it) }
        if (st != null) {
            val lw = (w * 0.24f).coerceAtMost(220.dp)
            val lh = lw * 0.5f
            Column(
                Modifier.offset(x = w * GC_SYS_X - lw / 2, y = h * GC_SYS_Y - lh / 2).width(lw),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedContent(targetState = st, label = "gcSystem", transitionSpec = { gameSwap() }) { shown ->
                    Box(Modifier.width(lw).height(lh), contentAlignment = Alignment.Center) { GcStopLogo(shown, accent, Modifier.fillMaxSize(), big = true) }
                }
                Spacer(Modifier.height(6.dp))
                val count = countOf(cur)
                Text(
                    if (count != null) "$count TITLES" else st.label.uppercase(), color = Color(0xFFE6FBEA), fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, letterSpacing = 2.sp, maxLines = 1, softWrap = false
                )
            }
        }
        Column(Modifier.align(Alignment.TopEnd).width(w * 0.52f).fillMaxHeight()) {
            GcHeader(c, "SYSTEMS", if (n == 0) null else "${cur + 1} / $n", accent, compact, tools = false)
            Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
            if (n == 0) GcNothing("NO SYSTEMS FOUND", accent, Modifier.weight(1f).fillMaxWidth())
            else BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().clipToBounds().pointerInput(n) {
                    var acc = 0f
                    detectVerticalDragGestures(onDragStart = { acc = 0f }, onDragEnd = { acc = 0f }, onDragCancel = { acc = 0f }) { change, dy ->
                        change.consume()
                        acc += dy
                        while (acc <= -rowPx) { acc += rowPx; if (GreenCoreNav.system < n - 1) { GreenCoreNav.system++; gcSound(GcSound.CLICK, UiSound.MOVE) } }
                        while (acc >= rowPx) { acc -= rowPx; if (GreenCoreNav.system > 0) { GreenCoreNav.system--; gcSound(GcSound.CLICK, UiSound.MOVE) } }
                    }
                }
            ) {
                val topPx = with(density) { (maxHeight * 0.42f).toPx() } - rowPx / 2f
                val half = (maxHeight / rowH).toInt() / 2 + 2
                val from = (cur - half).coerceAtLeast(0)
                val to = (cur + half).coerceAtMost(n - 1)
                for (i in from..to) {
                    key(i) {
                        val stop = c.stops.getOrNull(systems[i])
                        if (stop != null) GcSystemRow(
                            stop, countOf(i), i == cur, rowH, accent,
                            Modifier
                                .offset {
                                    val d = i - pos.value
                                    IntOffset((d * d * bendPx).coerceAtMost(bendPx * 12f).roundToInt(), (topPx + d * rowPx).roundToInt())
                                }
                                .graphicsLayer { alpha = (1f - abs(i - pos.value) / (half + 1f)).coerceIn(0.12f, 1f) }
                                .fillMaxWidth()
                                .height(rowH - 6.dp)
                        ) {
                            if (GreenCoreNav.system == i) gcPickSystem(live, i)
                            else { GreenCoreNav.system = i; GreenCoreNav.beat++; gcSound(GcSound.CLICK, UiSound.MOVE) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GcSystemRow(st: StyleStop, count: Int?, on: Boolean, rowH: Dp, accent: Color, modifier: Modifier, onTap: () -> Unit) {
    val tap by rememberUpdatedState(onTap)
    val shape = remember(rowH) { GcPanelShape(rowH * 0.28f, rowH * 0.3f) }
    val slide by animateFloatAsState(if (on) 1f else 0f, tween(180), label = "gcSys")
    Row(
        modifier
            .graphicsLayer { translationX = -14.dp.toPx() * slide }
            .clip(shape)
            .background(
                if (on) Brush.horizontalGradient(listOf(accent.copy(alpha = 0.42f), accent.copy(alpha = 0.10f)))
                else Brush.horizontalGradient(listOf(Color(0xB308160C), Color(0x7A051008)))
            )
            .border(1.dp, accent.copy(alpha = if (on) 0.92f else 0.24f), shape)
            .pointerInput(Unit) { detectTapGestures { tap() } }
            .padding(start = rowH * 0.42f, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(rowH * 1.1f).height(rowH * 0.5f), contentAlignment = Alignment.CenterStart) { GcStopLogo(st, accent, Modifier.fillMaxSize(), big = false) }
        Spacer(Modifier.width(10.dp))
        Text(
            st.label.uppercase(), color = if (on) Color.White else Color(0xFFC2E2C8), fontSize = if (rowH < 46.dp) 12.sp else 14.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        if (count != null) Text(
            "$count", color = accent.copy(alpha = if (on) 1f else 0.6f), fontSize = 12.sp, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false
        )
    }
}

/** A category's logo: the console's own (from the logos shipped with Voltcap), else its symbol. */
@Composable
private fun GcStopLogo(st: StyleStop, accent: Color, modifier: Modifier, big: Boolean) {
    val p = st.platform
    Box(modifier, contentAlignment = if (big) Alignment.Center else Alignment.CenterStart) {
        if (p != null && !com.shelf.launcher.data.isCollectionKey(p)) {
            PlatformLogo(p, st.label, Modifier.fillMaxSize(), textColor = Color(0xFFE6FBEA), textSize = if (big) 16.sp else 10.sp)
        } else {
            Icon(st.icon, null, tint = accent, modifier = Modifier.size(if (big) 56.dp else 20.dp))
        }
    }
}

// ---- Small parts ----

/** The page's header: ◀ (back), where you are, the count; L2 / R2, Sort and Search for touch. */
@Composable
private fun GcHeader(c: StyleCtx, path: String, right: String?, accent: Color, compact: Boolean, tools: Boolean, modifier: Modifier = Modifier) {
    val live by rememberUpdatedState(c)
    Row(modifier.fillMaxWidth().height(if (compact) 26.dp else 30.dp), verticalAlignment = Alignment.CenterVertically) {
        GcChip("◀", accent) { gcBack(live) }
        Spacer(Modifier.width(8.dp))
        Text(
            path, color = Color(0xFFD9F6DE), fontSize = if (compact) 11.sp else 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
        )
        if (right != null) {
            Spacer(Modifier.width(8.dp))
            Text(right, color = accent, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
        }
        Spacer(Modifier.weight(1f))
        if (tools) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                GcChip("L2", accent) { live.actions.category(-1) }
                GcChip("R2", accent) { live.actions.category(1) }
                if (!compact) GcChip("SORT", accent) { gcHotkey(HotkeyAction.SORT) }
                if (!compact) GcChip("SEARCH", accent) { gcHotkey(HotkeyAction.SEARCH) }
            }
        }
    }
}

@Composable
private fun GcChip(text: String, accent: Color, onTap: () -> Unit) {
    val tap by rememberUpdatedState(onTap)
    val shape = remember { GcPanelShape(5.dp, 0.dp) }
    Box(
        Modifier.clip(shape).background(Color(0x99061009)).border(1.dp, accent.copy(alpha = 0.45f), shape)
            .pointerInput(Unit) { detectTapGestures { tap() } }
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = accent, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun GcButton(text: String, primary: Boolean, accent: Color, modifier: Modifier, onTap: () -> Unit) {
    val tap by rememberUpdatedState(onTap)
    val shape = remember { GcPanelShape(7.dp, 6.dp) }
    Box(
        modifier.height(30.dp).clip(shape)
            .background(if (primary) accent.copy(alpha = 0.88f) else Color(0x99061009))
            .border(1.dp, accent.copy(alpha = if (primary) 1f else 0.5f), shape)
            .pointerInput(Unit) { detectTapGestures { tap() } },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, color = if (primary) Color(0xFF03130A) else accent, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp, maxLines = 1, softWrap = false
        )
    }
}

@Composable
private fun GcLabel(text: String, accent: Color) {
    Text(text, color = accent.copy(alpha = 0.7f), fontSize = 9.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp, maxLines = 1, softWrap = false)
}

@Composable
private fun GcRule(accent: Color) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.7f), Color.Transparent))))
}

@Composable
private fun GcStat(label: String, value: String, accent: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = accent.copy(alpha = 0.7f), fontSize = 9.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp, maxLines = 1, softWrap = false)
        Spacer(Modifier.width(8.dp))
        Text(
            value, color = Color(0xFFE4FBE8), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.weight(1f)
        )
    }
}

/** Achievements as a row of small cells, lit up to the share earned (gold once mastered). */
@Composable
private fun GcBar(progress: Pair<Int, Int>, accent: Color) {
    val (earned, total) = progress
    val share = if (total > 0) (earned.toFloat() / total).coerceIn(0f, 1f) else 0f
    val done = earned >= total && total > 0
    val gold = com.shelf.launcher.ui.theme.Ds.gold
    Spacer(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).height(5.dp).drawBehind {
            val cells = 20
            val gap = 2.dp.toPx()
            val cw = (size.width - gap * (cells - 1)) / cells
            val litCells = (share * cells).roundToInt()
            for (k in 0 until cells) {
                val on = k < litCells
                drawRect(
                    if (done) gold else if (on) accent else accent.copy(alpha = 0.16f),
                    topLeft = Offset(k * (cw + gap), 0f), size = Size(cw, size.height)
                )
            }
        }
    )
}

@Composable
private fun GcNothing(text: String, accent: Color, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(text, color = accent.copy(alpha = 0.75f), fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.5.sp, textAlign = TextAlign.Center)
    }
}
