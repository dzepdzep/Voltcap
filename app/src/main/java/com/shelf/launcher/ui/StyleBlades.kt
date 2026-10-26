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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.shelf.launcher.data.CardArtType
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.HotkeyAction
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Update 344: Blades (2005) — Voltcap's own take on the mid-2000s console dashboard of overlapping blades (original
 * drawing, names and sounds; nobody's logos or artwork).
 *
 * Five tall blades stand overlapped across the screen, each with a softly curved leading edge and its name running up
 * its tab: SPOTLIGHT, GAMES, SYSTEMS, MEDIA, SETTINGS. The blade you're on is open across the middle; the ones before it
 * are stacked as thin edges on the left, the ones after it on the right. ◀ ▶ slide the next blade open (the whole stack
 * glides, the content cross-slides), ▲ ▼ move down the blade's list.
 * - GAMES: the category's games as a list with a lit bar on the one you're on; beside it the game in your game look
 *   (cover, case, cartridge, disc…), its console, play time and achievements with their badges. A opens its window
 *   (the "guide" blade, WinScenesConsole.kt), X favourite, Y achievements, L2 / R2 the category — as everywhere.
 * - SYSTEMS: your consoles with their logos, counts and pictures; A opens one's games on the Games blade (B back).
 * - SPOTLIGHT: the games you played last, your achievements and statistics, search; a gamer card on the right.
 * - MEDIA: music, Save Vault, apps, the screensaver. SETTINGS: Settings, the quick menu, sort & filter, search, profile.
 * Start: the quick menu (R1's panel); Select: sort & filter. Touch: tap a blade's edge to open it, swipe across to slide,
 * tap a row to pick it, again to open it, drag the list. Options: Settings › Home › Layout options (Blades).
 */

/** Update 344: where the controller is in Blades (kept while you play and come back — StyleRestore). */
internal object BladesNav {
    const val SPOTLIGHT = 0
    const val GAMES = 1
    const val SYSTEMS = 2
    const val MEDIA = 3
    const val SETTINGS = 4
    const val COUNT = 5
    val NAMES = arrayOf("Spotlight", "Games", "Systems", "Media", "Settings")

    var blade by mutableIntStateOf(GAMES)
    var spotRow by mutableIntStateOf(0)
    var sysRow by mutableIntStateOf(0)
    var mediaRow by mutableIntStateOf(0)
    var setRow by mutableIntStateOf(0)
    /** The Games blade was opened from Systems (B goes back there). */
    var gamesFromSystems = false
    /** The "Start on" blade has been applied once this session. */
    var started = false
    @Volatile var ctx: StyleCtx? = null
    @Volatile var systems: List<Int> = emptyList()
    /** Each menu blade's rows as last drawn (their actions), read by the keys. */
    @Volatile var spotItems: List<BladeItem> = emptyList()
    @Volatile var mediaItems: List<BladeItem> = emptyList()
    @Volatile var setItems: List<BladeItem> = emptyList()
    internal val keysDown = HashSet<Int>()

    fun signature(): Any = listOf(blade, spotRow, sysRow, mediaRow, setRow)

    fun rowOf(b: Int): Int = when (b) { SPOTLIGHT -> spotRow; SYSTEMS -> sysRow; MEDIA -> mediaRow; SETTINGS -> setRow; else -> 0 }
    fun setRowOf(b: Int, v: Int) { when (b) { SPOTLIGHT -> spotRow = v; SYSTEMS -> sysRow = v; MEDIA -> mediaRow = v; SETTINGS -> setRow = v } }
    fun itemsOf(b: Int): List<BladeItem> = when (b) { SPOTLIGHT -> spotItems; MEDIA -> mediaItems; SETTINGS -> setItems; else -> emptyList() }
}

/** Update 344: one row of a menu blade. */
internal class BladeItem(val label: String, val sub: String, val icon: ImageVector, val game: Game? = null, val act: () -> Unit)

/** Update 344: the blades' colours: [hi] the lit front, [lo] the deep end, [ink] what's written on the lit bar. */
internal class BladePalette(val hi: Color, val lo: Color, val glow: Color, val bg: Color, val ink: Color)

@Composable
internal fun bladePalette(): BladePalette = when (Features.blColor.state.value) {
    Features.BlColor.GREEN -> BladePalette(Color(0xFF6DB821), Color(0xFF143A0B), Color(0xFFB8F05A), Color(0xFF070B06), Color(0xFF14310B))
    Features.BlColor.SILVER -> BladePalette(Color(0xFF9AA4AF), Color(0xFF2A3037), Color(0xFFF2F5F8), Color(0xFF07080A), Color(0xFF1E242B))
    Features.BlColor.BLUE -> BladePalette(Color(0xFF3C8FD8), Color(0xFF0B2552), Color(0xFF9AD6FF), Color(0xFF03060D), Color(0xFF07203F))
    Features.BlColor.ORANGE -> BladePalette(Color(0xFFE07A1C), Color(0xFF552106), Color(0xFFFFCB80), Color(0xFF0B0603), Color(0xFF3A1704))
    Features.BlColor.THEME -> ShelfTheme.accent.let { a ->
        BladePalette(lerp(a, Color.Black, 0.12f), lerp(a, Color.Black, 0.75f), lerp(a, Color.White, 0.45f), lerp(a, Color.Black, 0.94f), lerp(a, Color.Black, 0.78f))
    }
}

/** The colour of blade [i] (0 the front-most): from the lit front to the deep end. */
private fun bladeColor(p: BladePalette, i: Int): Color = lerp(p.hi, p.lo, (i / (BladesNav.COUNT - 1f)) * 0.75f)

// ---- Sounds ----

private fun blSound(s: KitSound, fallback: UiSound?) = LayoutKitSounds.play(LayoutKit.BLADES, s, fallback)

private fun blHotkey(a: HotkeyAction): Boolean {
    val h = GreenCoreHooks.hotkey ?: return false
    h(a)
    return true
}

// ---- What the keys do ----

/** Opens blade [b] (the whole stack glides there). */
internal fun bladesGo(b: Int) {
    val to = b.coerceIn(0, BladesNav.COUNT - 1)
    if (to == BladesNav.blade) return
    if (to != BladesNav.GAMES) BladesNav.gamesFromSystems = false
    BladesNav.blade = to
    blSound(KitSound.PAGE, UiSound.MOVE)
}

/** A on a menu blade's row (or a tap on the row you're on). */
internal fun bladesActivate(c: StyleCtx, b: Int, row: Int) {
    when (b) {
        BladesNav.SYSTEMS -> {
            val idx = BladesNav.systems.getOrNull(row) ?: return
            BladesNav.sysRow = row
            val steps = pocketStepsTo(c, idx)
            if (steps != 0) c.actions.category(steps)
            BladesNav.gamesFromSystems = true
            BladesNav.blade = BladesNav.GAMES
            blSound(KitSound.PAGE, if (steps == 0) UiSound.SELECT else null)
        }
        else -> {
            val item = BladesNav.itemsOf(b).getOrNull(row) ?: return
            blSound(KitSound.SELECT, UiSound.OPEN)
            item.act()
        }
    }
}

/** B / the back gesture: Games opened from Systems goes back there; another blade back to Games. False: nothing to do. */
internal fun bladesBack(): Boolean {
    when {
        BladesNav.blade == BladesNav.GAMES && BladesNav.gamesFromSystems -> { BladesNav.gamesFromSystems = false; BladesNav.blade = BladesNav.SYSTEMS }
        BladesNav.blade != BladesNav.GAMES -> BladesNav.blade = BladesNav.GAMES
        else -> return false
    }
    blSound(KitSound.BACK, UiSound.BACK)
    return true
}

private fun blStepGame(c: StyleCtx, d: Int): Boolean {
    val n = c.games.size
    if (n == 0) return false
    val next = if (StyleLoop.on && n >= 2) (c.focus + d).mod(n) else (c.focus + d).coerceIn(0, n - 1)
    if (next == c.focus) return false
    c.actions.select(next)
    blSound(KitSound.MOVE, UiSound.MOVE)
    return true
}

private fun blRowCount(b: Int): Int = if (b == BladesNav.SYSTEMS) BladesNav.systems.size else BladesNav.itemsOf(b).size

/**
 * Update 344: Blades' keys (all of them while it's on, before the home screen's own). True = handled.
 * ◀ ▶ the blades; ▲ ▼ down the list (Games: the game; Endless goes round); A acts on a menu blade's row (on the Games
 * blade it's the home screen's own: the game window); B back (Games from Systems → Systems, another blade → Games; on
 * Games it's the home screen's own); X / Y the home screen's own on Games. Start: quick menu; Select: sort & filter.
 */
internal fun bladesKey(event: KeyEvent, reach: (Boolean) -> Boolean): Boolean {
    val c = BladesNav.ctx ?: return false
    val code = event.nativeKeyEvent.keyCode
    val down = event.type == KeyEventType.KeyDown
    val up = event.type == KeyEventType.KeyUp
    if (up && BladesNav.keysDown.remove(code)) return true
    val k = event.key
    if (k == Key.ButtonStart || k == Key.Menu) {
        if (up) c.actions.quickMenu()
        return up
    }
    if (k == Key.ButtonSelect) return up && blHotkey(HotkeyAction.SORT)
    val b = BladesNav.blade
    val isA = k == Key.ButtonA || k == Key.Enter || k == Key.DirectionCenter
    if (isA && b != BladesNav.GAMES) {
        if (up && !event.nativeKeyEvent.isCanceled) bladesActivate(c, b, BladesNav.rowOf(b))
        return true
    }
    if (!down) return false
    val first = event.nativeKeyEvent.repeatCount == 0
    fun take(): Boolean { BladesNav.keysDown += code; return true }
    val isB = k == Key.ButtonB || k == Key.Back
    if (isB) return if (first && bladesBack()) take() else if (b == BladesNav.GAMES) false else take()
    when (k) {
        Key.DirectionLeft, Key.DirectionRight -> {
            if (first) {
                val to = b + if (k == Key.DirectionRight) 1 else -1
                if (to in 0 until BladesNav.COUNT) bladesGo(to) else EdgeBump.hit(if (to < 0) -1 else 1)
            }
            return take()
        }
        Key.DirectionUp, Key.DirectionDown -> {
            val d = if (k == Key.DirectionDown) 1 else -1
            if (b == BladesNav.GAMES) {
                if (!blStepGame(c, d) && first) reach(d < 0)
            } else {
                val n = blRowCount(b)
                val cur = BladesNav.rowOf(b)
                val next = (cur + d).coerceIn(0, (n - 1).coerceAtLeast(0))
                if (next != cur) { BladesNav.setRowOf(b, next); blSound(KitSound.MOVE, UiSound.MOVE) } else if (first) reach(d < 0)
            }
            return take()
        }
        Key.ButtonX, Key.ButtonY -> return if (b == BladesNav.GAMES && c.games.isNotEmpty()) false else take()
        else -> return if (isA && c.games.isEmpty()) take() else false
    }
}

// ---- The backdrop ----

/** Update 344: the dark room the blades stand in — a soft glow of their colour, slow light sweeping across a floor line. */
@Composable
internal fun BladesBackdrop(modifier: Modifier) {
    val p = bladePalette()
    val motion = Features.blMotion.state.value && rememberAnimationsEnabled() && !LocalLiteEffects.current && rememberIsActive() && !HomeIdle.calm
    val clock = rememberGcClock(motion, 1f, 30)
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(lerp(p.bg, p.lo, 0.35f), p.bg, Color.Black)))
        // The glow of the open blade, low and to the right.
        drawCircle(Brush.radialGradient(listOf(p.hi.copy(alpha = 0.22f), p.hi.copy(alpha = 0.05f), Color.Transparent), center = Offset(size.width * 0.62f, size.height * 0.62f), radius = size.width * 0.6f),
            size.width * 0.6f, Offset(size.width * 0.62f, size.height * 0.62f))
        // A floor line with its sheen.
        val floor = size.height * 0.93f
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, p.hi.copy(alpha = 0.07f), Color.Transparent), startY = floor - 30f, endY = floor + 30f), Offset(0f, floor - 30f), Size(size.width, 60f))
        // Slow bands of light sweeping across (still when motion is off).
        val t = clock.floatValue
        for (i in 0 until 3) {
            val x = ((t * (14f + i * 6f) + i * size.width * 0.37f) % (size.width * 1.6f)) - size.width * 0.3f
            val bw = size.width * (0.08f + i * 0.03f)
            val path = Path().apply { moveTo(x, 0f); lineTo(x + bw, 0f); lineTo(x + bw - size.width * 0.12f, size.height); lineTo(x - size.width * 0.12f, size.height); close() }
            drawPath(path, Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.035f), Color.Transparent), startX = x - size.width * 0.12f, endX = x + bw))
        }
    }
}

// ---- The layout ----

@Composable
internal fun StyleBlades(c: StyleCtx, modifier: Modifier = Modifier) {
    val systems = remember(c.stops) { gcSystems(c) }
    val spot = bladesSpotItems(c)
    val media = bladesMediaItems(c)
    val sets = bladesSettingsItems(c)
    SideEffect {
        BladesNav.ctx = c
        BladesNav.systems = systems
        BladesNav.spotItems = spot
        BladesNav.mediaItems = media
        BladesNav.setItems = sets
        StyleNav.side = emptyList(); StyleNav.top = emptyList(); StyleNav.bar = emptyList()
        StyleLoop.on = loopRowOn(c)
        if (!BladesNav.started) {
            BladesNav.started = true
            BladesNav.blade = if (Features.blStart.value == Features.BlStart.SPOTLIGHT) BladesNav.SPOTLIGHT else BladesNav.GAMES
        }
    }
    rememberLayoutKit(LayoutKit.BLADES, Features.blSounds.state.value, c.state.appearance.soundsEnabled)
    DisposableEffect(Unit) { onDispose { BladesNav.keysDown.clear() } }
    val windowOpen = GamePopup.game != null
    androidx.activity.compose.BackHandler(enabled = !windowOpen && (BladesNav.blade != BladesNav.GAMES || BladesNav.gamesFromSystems)) { bladesBack() }
    // The Games blade names the category in its header.
    if (BladesNav.blade == BladesNav.GAMES && CategoryTitle.captionOn(c.state.appearance)) CategoryTitle.Native(null)
    // Achievement badges of the game you're on (asked for once you rest on it).
    val g = c.game
    LaunchedEffect(g?.stableKey) {
        val gg = g ?: return@LaunchedEffect
        if (!Features.blBadges.value) return@LaunchedEffect
        kotlinx.coroutines.delay(500)
        if (c.state.raAchievementDetails[gg.stableKey].isNullOrEmpty() && c.progress(gg) != null) c.actions.needAchievements(gg)
    }
    val p = bladePalette()
    val reduced = !rememberAnimationsEnabled()
    val pos = remember { Animatable(BladesNav.blade.toFloat()) }
    val blade = BladesNav.blade
    LaunchedEffect(blade) {
        if (reduced) pos.snapTo(blade.toFloat())
        else pos.animateTo(blade.toFloat(), tween(520, easing = FastOutSlowInEasing))
    }
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val compact = maxHeight < 380.dp
        val tabPx = with(density) { (maxWidth * 0.042f).coerceIn(26.dp, 44.dp).toPx() }
        val leftPx = wPx * 0.025f
        val rightPx = wPx * 0.015f
        val n = BladesNav.COUNT
        fun leftAt(i: Int) = leftPx + i * tabPx * 0.78f
        fun rightAt(i: Int) = wPx - rightPx - (n - i) * tabPx
        fun xOf(i: Int, at: Float): Float {
            val d = (i - at).coerceIn(0f, 1f)
            return leftAt(i) + (rightAt(i) - leftAt(i)) * d
        }
        val topPx = hPx * 0.035f
        val botPx = hPx * 0.965f
        // Swipe across to slide the blades.
        val latestBlade by rememberUpdatedState(blade)
        val hitAt by rememberUpdatedState({ x: Float, y: Float ->
            val at = pos.value
            (n - 1 downTo 0).firstOrNull { x >= xOf(it, at) && y in topPx..botPx }
        })
        Box(
            Modifier.fillMaxSize()
                // A tap on a blade's visible part opens it (the later blades lie over the earlier ones, to the right).
                .pointerInput(Unit) {
                    detectTapGestures { o -> hitAt(o.x, o.y)?.let { if (it != latestBlade) bladesGo(it) } }
                }
                .pointerInput(Unit) {
                var acc = 0f
                detectHorizontalDragGestures(onDragStart = { acc = 0f }) { ch, dx ->
                    acc += dx
                    val step = 90.dp.toPx()
                    if (acc <= -step) { acc = 0f; if (latestBlade < BladesNav.COUNT - 1) bladesGo(latestBlade + 1); ch.consume() }
                    if (acc >= step) { acc = 0f; if (latestBlade > 0) bladesGo(latestBlade - 1); ch.consume() }
                }
            }
        ) {
            for (i in 0 until n) {
                key(i) {
                    BladeShape(
                        i, p, xOf = { xOf(i, pos.value) }, topPx = topPx, botPx = botPx, tabPx = tabPx, depth = { abs(i - pos.value) },
                        lit = i == blade, modifier = Modifier.fillMaxSize().zIndex(i.toFloat())
                    )
                }
            }
            // The open blade's content, between its tab and the next blade's edge; it slides and fades as the stack glides.
            val a = blade
            val cx = leftAt(a) + tabPx * 1.05f
            val cr = (if (a + 1 < n) rightAt(a + 1) else wPx - rightPx) - tabPx * 0.25f
            Box(
                Modifier.zIndex(n + 1f)
                    .offset { androidx.compose.ui.unit.IntOffset(cx.roundToInt(), (topPx + hPx * 0.035f).roundToInt()) }
                    .size(with(density) { (cr - cx).coerceAtLeast(1f).toDp() }, with(density) { (botPx - topPx - hPx * 0.07f).toDp() })
                    .graphicsLayer {
                        val d = (pos.value - a)
                        alpha = (1f - abs(d) * 2.2f).coerceIn(0f, 1f)
                        translationX = -d * 60.dp.toPx()
                    }
            ) {
                when (a) {
                    BladesNav.GAMES -> BladeGames(c, p, compact)
                    BladesNav.SYSTEMS -> BladeSystems(c, p, compact, systems)
                    BladesNav.SPOTLIGHT -> BladeMenu(c, p, compact, a, spot, gamerCard = true)
                    BladesNav.MEDIA -> BladeMenu(c, p, compact, a, media, gamerCard = false)
                    else -> BladeMenu(c, p, compact, a, sets, gamerCard = false)
                }
            }
        }
    }
}

/** One blade: its curved leading edge, its gloss, the name up its tab; a tap on its visible part opens it. */
@Composable
private fun BladeShape(
    i: Int, p: BladePalette, xOf: () -> Float, topPx: Float, botPx: Float, tabPx: Float, depth: () -> Float,
    lit: Boolean, modifier: Modifier
) {
    val base = bladeColor(p, i)
    val litK by animateFloatAsState(if (lit) 1f else 0f, tween(420), label = "blLit")
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val x = xOf()
            val dpth = depth().coerceIn(0f, 3f)
            // (Update 346: more depth — blades further from the open one stand lower and shorter, the leading edge bows out
            // further, a bevel and a lit sheen run along the curve, and the open blade carries a diagonal gloss.)
            val top = topPx + dpth * size.height * 0.02f
            val bot = botPx - dpth * size.height * 0.02f
            val right = size.width
            val bulge = tabPx * 0.62f
            val corner = tabPx * 0.5f
            val mid = (top + bot) / 2f
            val path = Path().apply {
                moveTo(x + bulge + corner, top)
                lineTo(right, top)
                lineTo(right, bot)
                lineTo(x + bulge + corner, bot)
                quadraticTo(x + bulge, bot, x + bulge * 0.8f, bot - corner)
                // The leading edge bows out to the left in the middle.
                quadraticTo(x - bulge * 0.7f, mid, x + bulge * 0.8f, top + corner)
                quadraticTo(x + bulge, top, x + bulge + corner, top)
                close()
            }
            val edge = Path().apply {
                moveTo(x + bulge + corner, bot)
                quadraticTo(x + bulge, bot, x + bulge * 0.8f, bot - corner)
                quadraticTo(x - bulge * 0.7f, mid, x + bulge * 0.8f, top + corner)
                quadraticTo(x + bulge, top, x + bulge + corner, top)
            }
            // A soft shadow cast to the left onto the blade behind, deeper for the open one (it stands proud).
            val lift = 0.6f + 0.4f * litK
            translate(left = -tabPx * 0.34f * lift) { drawPath(path, Color.Black.copy(alpha = 0.2f)) }
            translate(left = -tabPx * 0.2f * lift) { drawPath(path, Color.Black.copy(alpha = 0.28f)) }
            translate(left = -tabPx * 0.08f) { drawPath(path, Color.Black.copy(alpha = 0.3f)) }
            val shade = (0.2f + 0.12f * dpth).coerceAtMost(0.55f) * (1f - litK)
            drawPath(path, Brush.verticalGradient(listOf(lerp(base, Color.White, 0.18f), base, lerp(base, Color.Black, 0.55f)), startY = top, endY = bot))
            // Darker towards its right: the blade turns away from the light.
            drawPath(path, Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.22f)), startX = x + tabPx * 2f, endX = right))
            drawPath(path, Color.Black.copy(alpha = shade))
            clipPath(path) {
                // The bevel: a lit band just inside the curved edge and a dark one after it, so the edge reads as thickness.
                drawPath(edge, Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.34f + 0.16f * litK), Color.Transparent), startX = x - bulge * 0.7f, endX = x + bulge * 1.6f),
                    style = Stroke(tabPx * 0.5f))
                drawPath(edge, Color.Black.copy(alpha = 0.18f), style = Stroke(tabPx * 0.12f), blendMode = androidx.compose.ui.graphics.BlendMode.SrcOver)
                // The gloss: a bright band near the top.
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.05f), Color.Transparent), startY = top, endY = top + (bot - top) * 0.32f))
                // The open blade's diagonal sheen.
                if (litK > 0.01f) drawRect(
                    Brush.linearGradient(
                        0f to Color.Transparent, 0.45f to Color.Transparent, 0.52f to Color.White.copy(alpha = 0.1f * litK), 0.6f to Color.Transparent, 1f to Color.Transparent,
                        start = Offset(x, top), end = Offset(right, bot)
                    )
                )
            }
            // The rim: the curved edge lit, the top edge catching the light.
            drawPath(edge, Color.White.copy(alpha = 0.3f + 0.45f * litK), style = Stroke(2.2f))
            drawLine(Color.White.copy(alpha = 0.18f + 0.2f * litK), Offset(x + bulge + corner, top + 1f), Offset(right, top + 1f), strokeWidth = 1.6f)
        }
        // The name up its tab.
        Text(
            BladesNav.NAMES[i].uppercase(), color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
            maxLines = 1, softWrap = false,
            modifier = Modifier.graphicsLayer {
                // (Update 346: clear of the deeper curve near the bottom.)
                translationX = xOf() + tabPx * 0.3f
                translationY = botPx - 34.dp.toPx()
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                rotationZ = -90f
                alpha = if (lit) 0.75f else 1f
            }.zIndex(1f)
        )
    }
}

/** The open blade's title line: its name big, light, and what it's showing. */
@Composable
private fun BladeTitle(title: String, sub: String?, p: BladePalette, compact: Boolean) {
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
        Text(title, color = Color.White, fontSize = if (compact) 24.sp else 34.sp, fontWeight = FontWeight.Light, maxLines = 1)
        if (sub != null) {
            Spacer(Modifier.width(12.dp))
            Text(sub, color = lerp(p.glow, Color.White, 0.4f), fontSize = if (compact) 13.sp else 16.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(bottom = if (compact) 3.dp else 6.dp))
        }
    }
}

/** A list row: the lit one a bright bar (dark ink), the others white on the blade. */
@Composable
private fun BladeRow(on: Boolean, p: BladePalette, h: Dp, modifier: Modifier, content: @Composable (Boolean) -> Unit) {
    val k by animateFloatAsState(if (on) 1f else 0f, tween(160), label = "blRow")
    Box(
        modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(3.dp))
            .background(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.92f * k), lerp(p.glow, Color.White, 0.5f).copy(alpha = 0.85f * k))))
            .then(if (!on) Modifier.border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(3.dp)) else Modifier),
        contentAlignment = Alignment.CenterStart
    ) { content(on) }
}

/** Keeps the list scrolled so [focus] stays in view, gliding (Endless: the short way round). */
@Composable
private fun rememberBladeScroll(focus: Int, count: Int, visible: Int, loop: Boolean): Animatable<Float, *> {
    val scroll = remember { Animatable(0f) }
    val reduced = !rememberAnimationsEnabled()
    LaunchedEffect(focus, count, visible) {
        val maxTop = (count - visible).coerceAtLeast(0).toFloat()
        val want = (focus - visible / 2f + 0.5f).coerceIn(0f, maxTop)
        if (reduced || abs(want - scroll.value) > visible * 2f) scroll.snapTo(want)
        else scroll.animateTo(want, spring(dampingRatio = 0.9f, stiffness = 420f))
    }
    return scroll
}

// ---- GAMES ----

@Composable
private fun BladeGames(c: StyleCtx, p: BladePalette, compact: Boolean) {
    val caption = CategoryTitle.captionOn(c.state.appearance)
    Column(Modifier.fillMaxSize()) {
        BladeTitle("Games", if (caption) c.categoryLabel + "  ·  " + c.games.size + (if (c.games.size == 1) " game" else " games") else null, p, compact)
        Spacer(Modifier.height(if (compact) 6.dp else 12.dp))
        // (No early return out of the Column, an inline composable: HANDOVER rule 12.)
        if (c.games.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(EmptyShelf.text, color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp) }
        } else Row(Modifier.fillMaxSize()) {
            BladeGameList(c, p, compact, Modifier.weight(0.47f).fillMaxHeight())
            Spacer(Modifier.width(if (compact) 10.dp else 18.dp))
            c.game?.let { g -> BladeGamePreview(c, g, p, compact, Modifier.weight(0.53f).fillMaxHeight()) }
        }
    }
}

@Composable
private fun BladeGameList(c: StyleCtx, p: BladePalette, compact: Boolean, modifier: Modifier) {
    val titles = LocalTitleOptions.current
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    val latestFocus by rememberUpdatedState(c.focus)
    val count = c.games.size
    val latestCount by rememberUpdatedState(count)
    BoxWithConstraints(modifier.clipToBounds()) {
        val rowH = (maxHeight / 7.5f).coerceIn(34.dp, 54.dp)
        val gap = 3.dp
        val visible = ((maxHeight + gap) / (rowH + gap)).toInt().coerceAtLeast(1)
        val scroll = rememberBladeScroll(c.focus, count, visible, StyleLoop.on)
        val density = LocalDensity.current
        val stepPx = with(density) { (rowH + gap).toPx() }
        val first = scroll.value.toInt().coerceAtLeast(0)
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                var acc = 0f
                detectVerticalDragGestures(onDragStart = { acc = 0f }) { ch, dy ->
                    ch.consume(); acc += dy
                    while (acc <= -stepPx) { acc += stepPx; val nx = latestFocus + 1; if (nx < latestCount) select(nx) }
                    while (acc >= stepPx) { acc -= stepPx; val pv = latestFocus - 1; if (pv >= 0) select(pv) }
                }
            }
        ) {
            for (i in first..(first + visible).coerceAtMost(count - 1)) {
                val g = c.games[i]
                key(i) {
                    val on = i == c.focus
                    BladeRow(on, p, rowH, Modifier.graphicsLayer { translationY = (i - scroll.value) * stepPx }
                        .pointerInput(i) { detectTapGestures { if (latestFocus == i) open() else select(i) } }) { lit ->
                        Row(Modifier.fillMaxSize().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.fillMaxHeight(0.8f).width(rowH * 0.8f * 0.75f).clip(RoundedCornerShape(2.dp)).background(placeholderBrush(g.hue))) {
                                val cv = c.cover(g)
                                if (cv != null) AsyncImage(rememberStyleArtRequest(cv, trim = true), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                else if (g.sourceType == com.shelf.launcher.data.SourceType.APP) AppIconArt(g.packageName, wide = false, modifier = Modifier.fillMaxSize())
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(titles.display(g.name), color = if (lit) p.ink else Color.White, fontSize = if (compact) 14.sp else 16.sp,
                                fontWeight = if (lit) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (g.stableKey in c.state.favorites) Icon(Icons.Filled.Star, null, tint = if (lit) p.ink else p.glow, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
        // The scroll position along the right edge.
        if (count > visible) Canvas(Modifier.align(Alignment.CenterEnd).width(3.dp).fillMaxHeight().offset(x = 6.dp)) {
            val f = scroll.value / (count - visible).coerceAtLeast(1)
            val bar = size.height * (visible.toFloat() / count).coerceIn(0.08f, 1f)
            drawRect(Color.White.copy(alpha = 0.12f))
            drawRect(Color.White.copy(alpha = 0.7f), Offset(0f, (size.height - bar) * f.coerceIn(0f, 1f)), Size(size.width, bar))
        }
    }
}

@Composable
private fun BladeGamePreview(c: StyleCtx, g: Game, p: BladePalette, compact: Boolean, modifier: Modifier) {
    val titles = LocalTitleOptions.current
    val open by rememberUpdatedState(c.actions.open)
    BoxWithConstraints(modifier) {
        // (A case look may stand its cartridge / disc beside the case: a wider slot keeps the pair big.)
        val aspect = lookSlotAspect(c.type)
        val infoH = if (compact) 92.dp else 132.dp
        val artH = (maxHeight - infoH).coerceAtLeast(80.dp)
        val artW = (artH * aspect).coerceAtMost(maxWidth)
        val artHFit = artW / aspect
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(artH), contentAlignment = Alignment.Center) {
                androidx.compose.animation.Crossfade(targetState = g, label = "blArt", animationSpec = tween(if (rememberAnimationsEnabled()) 200 else 0)) { gg ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        StyleArt(c, gg, Modifier.size(artW, artHFit).pointerInput(gg.stableKey) { detectTapGestures { open() } }, focused = true, fx = 0f, ring = p.glow)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(titles.display(g.name), color = Color.White, fontSize = if (compact) 16.sp else 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (g.sourceType == com.shelf.launcher.data.SourceType.APP) "Android" else titles.console(g.platform)) + "  ·  " +
                        stylePlaytime(c.playtime(g), c.lastPlayed(g) != null),
                    color = Color.White.copy(alpha = 0.78f), fontSize = if (compact) 12.sp else 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            c.progress(g)?.let { (e, t) ->
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.EmojiEvents, null, tint = p.glow, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("$e / $t", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.16f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth((e.toFloat() / t).coerceIn(0f, 1f)).background(Brush.horizontalGradient(listOf(p.glow, Color.White))))
                    }
                }
                if (Features.blBadges.state.value && !compact) BladeBadges(c, g)
            }
        }
    }
}

/** The game's first achievements as small badges: earned in colour, the others greyed. */
@Composable
private fun BladeBadges(c: StyleCtx, g: Game) {
    val list = c.state.raAchievementDetails[g.stableKey].orEmpty().sortedWith(com.shelf.launcher.data.RA_DISPLAY_ORDER)
    if (list.isEmpty()) return
    val grey = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        // The earned ones first (newest feeling of progress), then what's left.
        val shown = (list.filter { it.isUnlocked } + list.filter { !it.isUnlocked }).take(8)
        shown.forEach { a ->
            val url = com.shelf.launcher.data.RaBadges.url(a.badgeName)
            Box(Modifier.size(26.dp).clip(RoundedCornerShape(3.dp)).background(Color.Black.copy(alpha = 0.3f))) {
                if (url != null) AsyncImage(url, a.title, Modifier.fillMaxSize().graphicsLayer { alpha = if (a.isUnlocked) 1f else 0.45f },
                    colorFilter = if (a.isUnlocked) null else grey, contentScale = ContentScale.Crop)
            }
        }
    }
}

// ---- SYSTEMS ----

@Composable
private fun BladeSystems(c: StyleCtx, p: BladePalette, compact: Boolean, systems: List<Int>) {
    val counts = remember(c.state.games) { c.state.games.groupingBy { it.platform }.eachCount() }
    val row = BladesNav.sysRow.coerceIn(0, (systems.size - 1).coerceAtLeast(0))
    Column(Modifier.fillMaxSize()) {
        BladeTitle("Systems", "${systems.size} in your library", p, compact)
        Spacer(Modifier.height(if (compact) 6.dp else 12.dp))
        if (systems.isNotEmpty()) Row(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.weight(0.47f).fillMaxHeight().clipToBounds()) {
                val rowH = (maxHeight / 7.5f).coerceIn(34.dp, 54.dp)
                val gap = 3.dp
                val visible = ((maxHeight + gap) / (rowH + gap)).toInt().coerceAtLeast(1)
                val scroll = rememberBladeScroll(row, systems.size, visible, false)
                val stepPx = with(LocalDensity.current) { (rowH + gap).toPx() }
                val first = scroll.value.toInt().coerceAtLeast(0)
                for (i in first..(first + visible).coerceAtMost(systems.size - 1)) {
                    val st = c.stops.getOrNull(systems[i]) ?: continue
                    key(i) {
                        BladeRow(i == row, p, rowH, Modifier.graphicsLayer { translationY = (i - scroll.value) * stepPx }
                            .pointerInput(i) { detectTapGestures { if (BladesNav.sysRow == i) bladesActivate(c, BladesNav.SYSTEMS, i) else { BladesNav.sysRow = i; blSound(KitSound.MOVE, UiSound.MOVE) } } }) { lit ->
                            Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                val plat = st.platform
                                if (plat != null) PlatformLogo(plat, st.label, Modifier.width(rowH * 1.4f).fillMaxHeight(0.62f), textColor = if (lit) p.ink else Color.White, textSize = 11.sp, blackToWhite = !lit)
                                else Icon(st.icon, null, tint = if (lit) p.ink else Color.White, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(st.label, color = if (lit) p.ink else Color.White, fontSize = if (compact) 14.sp else 16.sp, fontWeight = if (lit) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                st.platform?.let { pl -> counts[pl]?.let { Text("$it", color = if (lit) p.ink.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.6f), fontSize = 13.sp) } }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.width(if (compact) 10.dp else 18.dp))
            val st = c.stops.getOrNull(systems.getOrNull(row) ?: -1)
            Box(Modifier.weight(0.53f).fillMaxHeight()) { if (st != null) BladeSystemPreview(st, counts[st.platform ?: ""] ?: 0, p, compact) }
        }
    }
}

@Composable
private fun BladeSystemPreview(st: StyleStop, count: Int, p: BladePalette, compact: Boolean) {
    val context = LocalContext.current
    val platform = st.platform?.takeIf { st.special == null && !com.shelf.launcher.data.isCollectionKey(it) }
    val photosOn = Features.consolePhotos.state.value
    val photo by produceState<String?>(null, platform, photosOn) {
        value = if (photosOn && platform != null && com.shelf.launcher.data.ConsolePhotos.hasPhoto(platform)) {
            runCatching { com.shelf.launcher.data.ConsolePhotos.url(context, platform) }.getOrNull()
        } else null
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            androidx.compose.animation.Crossfade(targetState = photo to platform, label = "blSys") { (ph, pl) ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when {
                        ph != null -> AsyncImage(ph, st.label, Modifier.fillMaxSize(0.92f), contentScale = ContentScale.Fit)
                        pl != null -> PlatformLogo(pl, st.label, Modifier.fillMaxWidth(0.7f).fillMaxHeight(0.4f), textSize = 22.sp)
                        else -> Icon(st.icon, null, tint = Color.White, modifier = Modifier.size(72.dp))
                    }
                }
            }
        }
        if (platform != null && photo != null) PlatformLogo(platform, st.label, Modifier.fillMaxWidth(0.5f).height(if (compact) 26.dp else 36.dp), textSize = 15.sp)
        Text(if (count == 1) "1 game" else "$count games", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        Text("A  Open its games", color = lerp(p.glow, Color.White, 0.4f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

// ---- SPOTLIGHT / MEDIA / SETTINGS ----

@Composable
internal fun bladesSpotItems(c: StyleCtx): List<BladeItem> {
    val titles = LocalTitleOptions.current
    val recent = remember(c.state.lastPlayedAt, c.state.games) {
        c.state.games.filter { c.state.lastPlayedAt.containsKey(it.stableKey) }.sortedByDescending { c.state.lastPlayedAt[it.stableKey] ?: 0L }.take(4)
    }
    return remember(recent, titles, c.actions) {
        buildList {
            recent.forEachIndexed { i, g ->
                add(BladeItem(if (i == 0) "Jump back in" else titles.display(g.name), if (i == 0) titles.display(g.name) else titles.console(g.platform), Icons.Filled.PlayArrow, g) { GamePopup.game = g })
            }
            add(BladeItem("Achievements", "Everything you've earned", Icons.Filled.EmojiEvents) { c.actions.achievementsHub() })
            add(BladeItem("Statistics", "Play time and activity", Icons.Filled.BarChart) { c.actions.stats() })
            add(BladeItem("Search", "Find a game", Icons.Filled.Search) { c.actions.search() })
        }
    }
}

@Composable
internal fun bladesMediaItems(c: StyleCtx): List<BladeItem> = remember(c.actions, c.shortcuts) {
    listOf(
        BladeItem("Music", "Your music and player", Icons.Filled.QueueMusic) { if (!blHotkey(HotkeyAction.OPEN_SPOTIFY)) MusicSwipe.handler?.invoke() },
        BladeItem("Save Vault", "Your saves, backed up", Icons.Filled.Save) { SaveExplorerHook.request() },
        BladeItem("Apps", "Everything installed", Icons.Filled.Apps) {
            if (!blHotkey(HotkeyAction.APP_DRAWER)) c.shortcuts.firstOrNull { s -> s.label == "All apps" }?.onClick?.invoke()
        },
        BladeItem("Screensaver", "Rest the screen", Icons.Filled.Wallpaper) { c.actions.screensaver() }
    )
}

@Composable
internal fun bladesSettingsItems(c: StyleCtx): List<BladeItem> = remember(c.actions) {
    listOf(
        BladeItem("Settings", "Everything Voltcap can do", Icons.Filled.Settings) { c.actions.settings() },
        BladeItem("Quick settings", "The R1 panel", Icons.Filled.Tune) { c.actions.quickMenu() },
        BladeItem("Sort & filter", "How the games are listed", Icons.Filled.Sort) { c.actions.sort() },
        BladeItem("Search", "Find a game", Icons.Filled.Search) { c.actions.search() },
        BladeItem("Profile", "Who's playing", Icons.Filled.Person) { c.actions.profile() }
    )
}

@Composable
private fun BladeMenu(c: StyleCtx, p: BladePalette, compact: Boolean, b: Int, items: List<BladeItem>, gamerCard: Boolean) {
    val row = BladesNav.rowOf(b).coerceIn(0, (items.size - 1).coerceAtLeast(0))
    val titles = LocalTitleOptions.current
    Column(Modifier.fillMaxSize()) {
        BladeTitle(BladesNav.NAMES[b], null, p, compact)
        Spacer(Modifier.height(if (compact) 6.dp else 12.dp))
        Row(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.weight(0.47f).fillMaxHeight().clipToBounds()) {
                val rowH = (maxHeight / 7.5f).coerceIn(34.dp, 54.dp)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    // (Update 346: the rows follow the blade's curved leading edge — out to the left in the middle, in at
                    // the ends — so the menu reads as part of the blade, not a plain list.)
                    val mid = (items.size - 1) / 2f
                    items.forEachIndexed { i, it ->
                        val bow = if (mid > 0f) ((i - mid) / mid).let { k -> k * k } else 0f
                        BladeRow(i == row, p, rowH, Modifier.padding(start = (if (compact) 10.dp else 18.dp) * bow).pointerInput(b, i) {
                            detectTapGestures { if (BladesNav.rowOf(b) == i) bladesActivate(c, b, i) else { BladesNav.setRowOf(b, i); blSound(KitSound.MOVE, UiSound.MOVE) } }
                        }) { lit ->
                            Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                val gg = it.game
                                if (gg != null) Box(Modifier.fillMaxHeight(0.8f).width(rowH * 0.6f).clip(RoundedCornerShape(2.dp)).background(placeholderBrush(gg.hue))) {
                                    c.cover(gg)?.let { cv -> AsyncImage(rememberStyleArtRequest(cv, trim = true), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                                } else Icon(it.icon, null, tint = if (lit) p.ink else Color.White, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(it.label, color = if (lit) p.ink else Color.White, fontSize = if (compact) 14.sp else 16.sp, fontWeight = if (lit) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.width(if (compact) 10.dp else 18.dp))
            Column(Modifier.weight(0.53f).fillMaxHeight()) {
                val it = items.getOrNull(row)
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    if (it != null) androidx.compose.animation.Crossfade(targetState = row, label = "blMenu") { r ->
                        val item = items.getOrNull(r) ?: return@Crossfade
                        val gg = item.game
                        if (gg != null) Column(Modifier.fillMaxSize()) {
                            // (Update 346: the game in a pod cut to the blade's own curve, glossed and rimmed in light.)
                            val pod = BladePodShape
                            Box(Modifier.fillMaxWidth().weight(1f).shadow(10.dp, pod).clip(pod).background(placeholderBrush(gg.hue))) {
                                (c.wide(gg))?.let { u -> AsyncImage(rememberStyleArtRequest(u), gg.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))))
                                Box(Modifier.fillMaxWidth().fillMaxHeight(0.3f).background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent))))
                                Box(Modifier.fillMaxSize().border(2.dp, Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0.2f))), pod))
                                Column(Modifier.align(Alignment.BottomStart).padding(start = 26.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
                                    Text(titles.display(gg.name), color = Color.White, fontSize = if (compact) 16.sp else 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(titles.console(gg.platform) + "  ·  " + stylePlaytime(c.playtime(gg), true), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, maxLines = 1)
                                }
                            }
                        } else Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Box(Modifier.size(if (compact) 64.dp else 96.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.08f)))).border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center) {
                                Icon(item.icon, null, tint = Color.White, modifier = Modifier.size(if (compact) 32.dp else 48.dp))
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(item.label, color = Color.White, fontSize = if (compact) 18.sp else 22.sp, fontWeight = FontWeight.Light)
                            Text(item.sub, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                        }
                    }
                }
                if (gamerCard && !compact) { Spacer(Modifier.height(10.dp)); BladeGamerCard(c, p) }
            }
        }
    }
}

/** Update 346: Spotlight's pod — a panel whose left edge bows out like a blade's leading edge. */
private val BladePodShape = androidx.compose.foundation.shape.GenericShape { size, _ ->
    val b = (size.width * 0.05f).coerceAtMost(22f * 2.5f)
    val r = (size.minDimension * 0.04f).coerceAtLeast(6f)
    moveTo(b + r, 0f)
    lineTo(size.width - r, 0f); quadraticTo(size.width, 0f, size.width, r)
    lineTo(size.width, size.height - r); quadraticTo(size.width, size.height, size.width - r, size.height)
    lineTo(b + r, size.height)
    quadraticTo(b, size.height, b * 0.9f, size.height - r)
    quadraticTo(-b * 0.8f, size.height / 2f, b * 0.9f, r)
    quadraticTo(b, 0f, b + r, 0f)
    close()
}

/** The gamer card: your picture, name, points, and how much you've played. */
@Composable
private fun BladeGamerCard(c: StyleCtx, p: BladePalette) {
    val prof = c.state.raProfile
    val stats = c.state.raProfileStats
    val avatar = profilePictureUrl(com.shelf.launcher.data.RaBadges.mediaUrl(prof?.UserPic))
    val total = remember(c.state.playtime) { c.state.playtime.values.sum() }
    // (Update 346: a gamer tag — a glossy plate with rounded ends in the blade's colour, the picture in a lit frame at its
    // left end — instead of a flat dark strip.)
    val tag = RoundedCornerShape(50)
    Row(
        Modifier.fillMaxWidth().height(64.dp).shadow(6.dp, tag).clip(tag)
            .background(Brush.verticalGradient(listOf(lerp(p.hi, Color.White, 0.18f), p.hi, lerp(p.hi, p.lo, 0.7f))))
            .drawBehind { drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.32f), Color.Transparent), endY = size.height * 0.5f)) }
            .border(1.5.dp, Color.White.copy(alpha = 0.55f), tag).padding(start = 8.dp, end = 18.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(50.dp).clip(CircleShape).background(lerp(p.lo, Color.Black, 0.3f)).border(2.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
            if (avatar != null) AsyncImage(avatar, prof?.User, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Filled.Person, null, tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            val ink = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 1.5f), 3f))
            Text(prof?.User ?: c.state.raUsername ?: "Player", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = ink)
            Text(
                listOfNotNull(prof?.TotalPoints?.let { "$it points" }, stats.mastered.takeIf { it > 0 }?.let { "$it mastered" }, stylePlaytime(total.takeIf { it > 0 }, true).takeIf { total > 0 }?.let { "$it played" })
                    .joinToString("  ·  ").ifEmpty { "${c.state.games.size} games" },
                color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = ink
            )
        }
    }
}

// ---- Options ----

/** Update 344: Settings › Home › Layout options (Blades). Each its own "bl_…" Features pref. */
@Composable
internal fun BladesSettings() {
    MutedNote(
        "Five overlapping blades — Spotlight, Games, Systems, Media, Settings. ◀ ▶ slide the next blade open, ▲ ▼ move down " +
            "its list, A opens, B goes back. L2 / R2 change the category, Start the quick menu, Select sort & filter. Tap a " +
            "blade's edge to open it, swipe across to slide."
    )
    Spacer(Modifier.height(10.dp))
    StyleChoice("Colour", Features.BlColor.values(), Features.blColor.state.value, { it.label }) { Features.blColor.value = it }
    StyleChoice("Start on", Features.BlStart.values(), Features.blStart.state.value, { it.label }) { Features.blStart.value = it }
    Toggle(Icons.Filled.EmojiEvents, "Achievement badges", "The game's badges under it on the Games blade (earned in colour).", Features.blBadges)
    Toggle(Icons.Filled.Wallpaper, "Moving light", "Slow bands of light sweeping behind the blades. Off: still.", Features.blMotion)
    Toggle(Icons.Filled.QueueMusic, "Interface sounds", "The blades' own airy ticks, swishes and chimes. Off: Voltcap's usual sounds.", Features.blSounds)
    if (Features.blSounds.state.value) {
        PercentSlider("Sound volume", Features.kitVolume.state.value, { Features.kitVolume.value = it.coerceIn(0, 100) })
        Spacer(Modifier.height(6.dp))
    }
}

/** Update 344: the slot for the game in its look on the console layouts: the look's own shape, wider for the case looks (the
 *  media may stand beside the case) so the pair isn't shrunk into a cover-shaped slot. */
internal fun lookSlotAspect(type: CardArtType): Float =
    if (type == CardArtType.CASE || type == CardArtType.CASE_REAL) maxOf(styleAspect(type), 1.15f) else styleAspect(type)
