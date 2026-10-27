package com.shelf.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.HotkeyAction
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * Update 344: Panel dashboard (2008) — Voltcap's own take on the late-2000s console dashboard of angled panels (original
 * drawing, names and sounds; nobody's logos, avatars or artwork). Distinct from Blades: here the menu runs in channels,
 * one above the other, and each channel is a row of panels receding into the distance.
 *
 * Your profile stands on the left (your picture in a lit frame, your name and points). On the right, the channel you're
 * on: its name, then its panels — the one you're on big and square to you, the next ones stepping back to the right,
 * smaller and turned away, overlapping like cards on a table. The channels above and below wait smaller and dimmed.
 * Channels: Spotlight (what you played last, achievements, statistics, search), Games (the category's games in your
 * game look), Systems (your consoles), Media, Settings.
 * ▲ ▼ change the channel (they slide up / down), ◀ ▶ along its panels. A opens (a game: its window — the detail panel
 * swinging in, WinScenesConsole.kt), B back (Games opened from Systems → Systems, another channel → Games), X / Y / L2 /
 * R2 as everywhere. Start: the quick menu, Select: sort & filter. Touch: tap a panel, swipe along, swipe up / down.
 */

/** Update 344: where the controller is in Panel dashboard. */
internal object PanelNav {
    const val SPOTLIGHT = 0
    const val GAMES = 1
    const val SYSTEMS = 2
    const val MEDIA = 3
    const val SETTINGS = 4
    const val COUNT = 5
    val NAMES = arrayOf("Spotlight", "My games", "Systems", "Media", "Settings")

    var channel by mutableIntStateOf(GAMES)
    var spotCol by mutableIntStateOf(0)
    var sysCol by mutableIntStateOf(0)
    var mediaCol by mutableIntStateOf(0)
    var setCol by mutableIntStateOf(0)
    var gamesFromSystems = false
    var started = false
    @Volatile var ctx: StyleCtx? = null
    @Volatile var systems: List<Int> = emptyList()
    @Volatile var spotItems: List<BladeItem> = emptyList()
    @Volatile var mediaItems: List<BladeItem> = emptyList()
    @Volatile var setItems: List<BladeItem> = emptyList()
    internal val keysDown = HashSet<Int>()

    fun signature(): Any = listOf(channel, spotCol, sysCol, mediaCol, setCol)
    fun colOf(ch: Int): Int = when (ch) { SPOTLIGHT -> spotCol; SYSTEMS -> sysCol; MEDIA -> mediaCol; SETTINGS -> setCol; else -> 0 }
    fun setColOf(ch: Int, v: Int) { when (ch) { SPOTLIGHT -> spotCol = v; SYSTEMS -> sysCol = v; MEDIA -> mediaCol = v; SETTINGS -> setCol = v } }
    fun itemsOf(ch: Int): List<BladeItem> = when (ch) { SPOTLIGHT -> spotItems; MEDIA -> mediaItems; SETTINGS -> setItems; else -> emptyList() }
    fun countOf(ch: Int, games: Int): Int = when (ch) { GAMES -> games; SYSTEMS -> systems.size; else -> itemsOf(ch).size }
}

/** Update 344: Panel dashboard's colours. */
internal class PanelPalette(val top: Color, val bottom: Color, val ink: Color, val sub: Color, val accent: Color, val panel: Color, val panelInk: Color)

@Composable
internal fun panelPalette(): PanelPalette = when (Features.pdColor.state.value) {
    Features.PdColor.GREEN -> PanelPalette(Color(0xFF3E8E26), Color(0xFF0C2608), Color.White, Color(0xFFCFEFC0), Color(0xFF9BE34A), Color(0xFFF4F6F2), Color(0xFF1E2A1A))
    Features.PdColor.SILVER -> PanelPalette(Color(0xFFF1F3F5), Color(0xFFB9C1CA), Color(0xFF1F262E), Color(0xFF55606C), Color(0xFF5DBB2A), Color.White, Color(0xFF1F262E))
    Features.PdColor.GRAPHITE -> PanelPalette(Color(0xFF2A2F36), Color(0xFF07090B), Color.White, Color(0xFFAEB7C2), Color(0xFF7FD64A), Color(0xFF3A414A), Color.White)
    Features.PdColor.THEME -> ShelfTheme.accent.let { a ->
        PanelPalette(lerp(a, Color.Black, 0.35f), lerp(a, Color.Black, 0.88f), Color.White, lerp(a, Color.White, 0.7f), lerp(a, Color.White, 0.35f), Color(0xFFF4F6F8), Color(0xFF1C2228))
    }
}

private fun pdSound(s: KitSound, fallback: UiSound?) = LayoutKitSounds.play(LayoutKit.PANELS, s, fallback)

private fun pdHotkey(a: HotkeyAction): Boolean {
    val h = GreenCoreHooks.hotkey ?: return false
    h(a)
    return true
}

internal fun panelsGo(ch: Int) {
    val to = ch.coerceIn(0, PanelNav.COUNT - 1)
    if (to == PanelNav.channel) return
    if (to != PanelNav.GAMES) PanelNav.gamesFromSystems = false
    PanelNav.channel = to
    pdSound(KitSound.PAGE, UiSound.MOVE)
}

internal fun panelsActivate(c: StyleCtx, ch: Int, col: Int) {
    when (ch) {
        PanelNav.GAMES -> c.actions.open()
        PanelNav.SYSTEMS -> {
            val idx = PanelNav.systems.getOrNull(col) ?: return
            PanelNav.sysCol = col
            val steps = pocketStepsTo(c, idx)
            if (steps != 0) c.actions.category(steps)
            PanelNav.gamesFromSystems = true
            PanelNav.channel = PanelNav.GAMES
            pdSound(KitSound.PAGE, if (steps == 0) UiSound.SELECT else null)
        }
        else -> {
            val item = PanelNav.itemsOf(ch).getOrNull(col) ?: return
            pdSound(KitSound.SELECT, UiSound.OPEN)
            item.act()
        }
    }
}

internal fun panelsBack(): Boolean {
    when {
        PanelNav.channel == PanelNav.GAMES && PanelNav.gamesFromSystems -> { PanelNav.gamesFromSystems = false; PanelNav.channel = PanelNav.SYSTEMS }
        PanelNav.channel != PanelNav.GAMES -> PanelNav.channel = PanelNav.GAMES
        else -> return false
    }
    pdSound(KitSound.BACK, UiSound.BACK)
    return true
}

/** Update 344: Panel dashboard's keys (all of them while it's on). True = handled. */
internal fun panelsKey(event: KeyEvent, reach: (Boolean) -> Boolean): Boolean {
    val c = PanelNav.ctx ?: return false
    val code = event.nativeKeyEvent.keyCode
    val down = event.type == KeyEventType.KeyDown
    val up = event.type == KeyEventType.KeyUp
    if (up && PanelNav.keysDown.remove(code)) return true
    val k = event.key
    if (k == Key.ButtonStart || k == Key.Menu) { if (up) c.actions.quickMenu(); return up }
    if (k == Key.ButtonSelect) return up && pdHotkey(HotkeyAction.SORT)
    val ch = PanelNav.channel
    val isA = k == Key.ButtonA || k == Key.Enter || k == Key.DirectionCenter
    if (isA && ch != PanelNav.GAMES) {
        if (up && !event.nativeKeyEvent.isCanceled) panelsActivate(c, ch, PanelNav.colOf(ch))
        return true
    }
    if (!down) return false
    val first = event.nativeKeyEvent.repeatCount == 0
    fun take(): Boolean { PanelNav.keysDown += code; return true }
    val isB = k == Key.ButtonB || k == Key.Back
    if (isB) return if (first && panelsBack()) take() else if (ch == PanelNav.GAMES) false else take()
    when (k) {
        Key.DirectionUp, Key.DirectionDown -> {
            if (first) {
                val to = ch + if (k == Key.DirectionDown) 1 else -1
                if (to in 0 until PanelNav.COUNT) panelsGo(to) else reach(to < 0)
            }
            return take()
        }
        Key.DirectionLeft, Key.DirectionRight -> {
            val d = if (k == Key.DirectionRight) 1 else -1
            if (ch == PanelNav.GAMES) {
                val n = c.games.size
                if (n == 0) return take()
                val next = if (StyleLoop.on && n > 1) (c.focus + d).mod(n) else (c.focus + d).coerceIn(0, n - 1)
                if (next != c.focus) { c.actions.select(next); pdSound(KitSound.MOVE, UiSound.MOVE) } else if (first) EdgeBump.hit(d)
            } else {
                val n = PanelNav.countOf(ch, c.games.size)
                val cur = PanelNav.colOf(ch)
                val next = (cur + d).coerceIn(0, (n - 1).coerceAtLeast(0))
                if (next != cur) { PanelNav.setColOf(ch, next); pdSound(KitSound.MOVE, UiSound.MOVE) } else if (first) EdgeBump.hit(d)
            }
            return take()
        }
        Key.ButtonX, Key.ButtonY -> return if (ch == PanelNav.GAMES && c.games.isNotEmpty()) false else take()
        else -> return if (isA && c.games.isEmpty()) take() else false
    }
}

// ---- The backdrop ----

/** Update 344: a soft gradient with a glossy horizon band and faint curved ribbons. */
@Composable
internal fun PanelsBackdrop(modifier: Modifier) {
    val p = panelPalette()
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(p.top, lerp(p.top, p.bottom, 0.55f), p.bottom)))
        // The glossy horizon behind the panels.
        val hy = size.height * 0.5f
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.13f), Color.Transparent), startY = hy - size.height * 0.2f, endY = hy + size.height * 0.2f),
            Offset(0f, hy - size.height * 0.2f), Size(size.width, size.height * 0.4f))
        for (r in 0 until 3) {
            val path = Path().apply {
                moveTo(0f, size.height * (0.72f + r * 0.06f))
                cubicTo(size.width * 0.3f, size.height * (0.6f + r * 0.05f), size.width * 0.7f, size.height * (0.9f - r * 0.04f), size.width, size.height * (0.7f + r * 0.07f))
            }
            drawPath(path, Color.White.copy(alpha = 0.06f), style = androidx.compose.ui.graphics.drawscope.Stroke(size.height * 0.012f))
        }
    }
}

// ---- The layout ----

@Composable
internal fun StylePanels(c: StyleCtx, modifier: Modifier = Modifier) {
    val systems = remember(c.stops) { gcSystems(c) }
    val spot = bladesSpotItems(c)
    val media = bladesMediaItems(c)
    val sets = bladesSettingsItems(c)
    SideEffect {
        PanelNav.ctx = c
        PanelNav.systems = systems
        PanelNav.spotItems = spot; PanelNav.mediaItems = media; PanelNav.setItems = sets
        StyleNav.side = emptyList(); StyleNav.top = emptyList(); StyleNav.bar = emptyList()
        StyleLoop.on = loopRowOn(c)
        if (!PanelNav.started) {
            PanelNav.started = true
            PanelNav.channel = if (Features.pdStart.value == Features.BlStart.SPOTLIGHT) PanelNav.SPOTLIGHT else PanelNav.GAMES
        }
    }
    rememberLayoutKit(LayoutKit.PANELS, Features.pdSounds.state.value, c.state.appearance.soundsEnabled)
    DisposableEffect(Unit) { onDispose { PanelNav.keysDown.clear() } }
    val windowOpen = GamePopup.game != null
    val ch = PanelNav.channel
    androidx.activity.compose.BackHandler(enabled = !windowOpen && (ch != PanelNav.GAMES || PanelNav.gamesFromSystems)) { panelsBack() }
    if (ch == PanelNav.GAMES && CategoryTitle.captionOn(c.state.appearance)) CategoryTitle.Native(null)
    val g = c.game
    LaunchedEffect(g?.stableKey) {
        val gg = g ?: return@LaunchedEffect
        kotlinx.coroutines.delay(500)
        if (c.state.raAchievementDetails[gg.stableKey].isNullOrEmpty() && c.progress(gg) != null) c.actions.needAchievements(gg)
    }
    val p = panelPalette()
    val reduced = !rememberAnimationsEnabled()
    val chPos = remember { Animatable(ch.toFloat()) }
    LaunchedEffect(ch) { if (reduced) chPos.snapTo(ch.toFloat()) else chPos.animateTo(ch.toFloat(), tween(460, easing = FastOutSlowInEasing)) }
    val density = LocalDensity.current
    val latestCh by rememberUpdatedState(ch)
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()
        .pointerInput(Unit) {
            var acc = 0f
            detectVerticalDragGestures(onDragStart = { acc = 0f }) { chg, dy ->
                acc += dy
                val step = 70.dp.toPx()
                if (acc <= -step) { acc = 0f; chg.consume(); if (latestCh < PanelNav.COUNT - 1) panelsGo(latestCh + 1) }
                if (acc >= step) { acc = 0f; chg.consume(); if (latestCh > 0) panelsGo(latestCh - 1) }
            }
        }
    ) {
        val narrow = maxWidth / maxHeight < 1.45f
        val compact = maxHeight < 380.dp
        val showProfile = Features.pdProfile.state.value && !narrow
        val leftW = if (showProfile) maxWidth * 0.2f else 0.dp
        // The profile on the left.
        if (showProfile) PanelProfile(c, p, Modifier.width(leftW).fillMaxHeight().padding(start = 20.dp, bottom = 16.dp, top = 16.dp))
        // The channels: the one you're on in the middle, the others above / below smaller and dimmed.
        val rowH = maxHeight * (if (compact) 0.6f else 0.56f)
        val stepPx = with(density) { (maxHeight * 0.62f).toPx() }
        val baseX = leftW + 16.dp
        // (Pass 5, D57: the channels on screen as a derived list and their place read in the layer / draw lambdas — read
        // raw here, every channel's panels were re-composed on every frame of an L / R glide.)
        val chShown by remember { androidx.compose.runtime.derivedStateOf { (0 until PanelNav.COUNT).filter { abs(it - chPos.value) <= 1.6f } } }
        for (i in chShown) {
            fun dNow() = i - chPos.value
            key(i) {
                Box(
                    Modifier.offset(x = baseX).width(maxWidth - baseX).height(rowH + 90.dp)
                        .graphicsLayer {
                            val d = dNow()
                            translationY = (this@BoxWithConstraints.maxHeight.toPx() - (rowH + 90.dp).toPx()) * 0.42f + d * stepPx
                            val s = 1f - 0.32f * abs(d).coerceAtMost(1f)
                            scaleX = s; scaleY = s
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            alpha = (1f - abs(d) * 0.62f).coerceIn(0f, 1f)
                            compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                        }
                        // (Update 346: a channel above / below shows its name and the edge of its panels fading out — not the
                        // blank tops of its panels cut off by the screen, which read as grey placeholder bars.)
                        .drawWithContent {
                            drawContent()
                            val d = dNow()
                            val t = abs(d).coerceAtMost(1f)
                            if (t > 0.01f) {
                                val h = size.height
                                val keep = androidx.compose.ui.graphics.Color.Black
                                val gone = androidx.compose.ui.graphics.Color.Black.copy(alpha = 1f - t)
                                val brush = if (d > 0f) Brush.verticalGradient(0f to keep, 0.11f to keep, 0.2f to gone, 1f to gone, startY = 0f, endY = h)
                                else Brush.verticalGradient(0f to gone, 0.5f to gone, 0.8f to keep, 1f to keep, startY = 0f, endY = h)
                                drawRect(brush, blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
                            }
                        }
                ) { PanelChannel(c, p, i, i == ch, rowH, compact) }
            }
        }
    }
}

/** The profile column: your picture in a lit frame, standing on a soft disc of light, your name and points under it. */
@Composable
private fun PanelProfile(c: StyleCtx, p: PanelPalette, modifier: Modifier) {
    val prof = c.state.raProfile
    val avatar = profilePictureUrl(com.shelf.launcher.data.RaBadges.mediaUrl(prof?.UserPic))
    val stats = c.state.raProfileStats
    Column(modifier, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.BottomCenter) {
            Canvas(Modifier.size(150.dp, 40.dp).offset(y = 16.dp)) {
                drawOval(Brush.radialGradient(listOf(p.accent.copy(alpha = 0.55f), Color.Transparent)), Offset.Zero, size)
            }
            Box(
                Modifier.size(118.dp).clip(RoundedCornerShape(8.dp)).background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0.55f))))
                    .border(3.dp, Color.White, RoundedCornerShape(8.dp)).padding(5.dp).clip(RoundedCornerShape(5.dp)).background(p.bottom),
                contentAlignment = Alignment.Center
            ) {
                if (avatar != null) AsyncImage(avatar, prof?.User, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Filled.Person, null, tint = Color.White, modifier = Modifier.size(64.dp))
            }
        }
        Spacer(Modifier.height(22.dp))
        Text(prof?.User ?: c.state.raUsername ?: "Player", color = p.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.EmojiEvents, null, tint = p.accent, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text(prof?.TotalPoints?.toString() ?: "${stats.achievementsEarned}", color = p.sub, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** One channel: its name, then its panels receding to the right; the one you're on big, its words under the row. */
@Composable
private fun PanelChannel(c: StyleCtx, p: PanelPalette, ch: Int, on: Boolean, rowH: Dp, compact: Boolean) {
    val n = PanelNav.countOf(ch, c.games.size)
    val focus = if (ch == PanelNav.GAMES) c.focus else PanelNav.colOf(ch)
    val reduced = !rememberAnimationsEnabled()
    val scroll = remember { Animatable(focus.toFloat()) }
    LaunchedEffect(focus) {
        if (reduced || abs(scroll.value - focus) > 6f) scroll.snapTo(focus.toFloat())
        else scroll.animateTo(focus.toFloat(), spring(dampingRatio = 0.86f, stiffness = 300f))
    }
    val density = LocalDensity.current
    val select by rememberUpdatedState(c.actions.select)
    val latestFocus by rememberUpdatedState(focus)
    val latestN by rememberUpdatedState(n)
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(PanelNav.NAMES[ch], color = p.ink, fontSize = if (compact) 22.sp else 30.sp, fontWeight = FontWeight.Light, maxLines = 1)
            if (ch == PanelNav.GAMES && CategoryTitle.captionOn(c.state.appearance)) {
                Spacer(Modifier.width(12.dp))
                Text(c.categoryLabel + "  ·  " + c.games.size, color = p.sub, fontSize = 14.sp, maxLines = 1, modifier = Modifier.padding(bottom = 5.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        val panelH = rowH * 0.78f
        val panelW = panelH * (if (ch == PanelNav.GAMES) 1.08f else 1.25f)
        val wPx = with(density) { panelW.toPx() }
        Box(Modifier.fillMaxWidth().height(panelH)
            .pointerInput(ch) {
                var acc = 0f
                detectHorizontalDragGestures(onDragStart = { acc = 0f }) { chg, dx ->
                    chg.consume(); acc += dx
                    val step = 60.dp.toPx()
                    fun go(to: Int) {
                        if (to !in 0 until latestN) return
                        if (ch == PanelNav.GAMES) select(to) else PanelNav.setColOf(ch, to)
                        pdSound(KitSound.MOVE, UiSound.MOVE)
                    }
                    if (acc <= -step) { acc = 0f; go(latestFocus + 1) }
                    if (acc >= step) { acc = 0f; go(latestFocus - 1) }
                }
            }
        ) {
            if (n == 0) Text(EmptyShelf.text, color = p.sub, fontSize = 15.sp, modifier = Modifier.align(Alignment.CenterStart))
            // (Pass 5, D57: as above — the panels in view derived, their place read while laid out and drawn.)
            val shownRange by remember(n) { androidx.compose.runtime.derivedStateOf { (scroll.value - 1.5f).toInt().coerceAtLeast(0)..(scroll.value + 6f).toInt().coerceAtMost(n - 1) } }
            for (i in shownRange) {
                fun dNow() = i - scroll.value
                val zOrder by remember(i) { androidx.compose.runtime.derivedStateOf { 100f - (dNow() * 4f).roundToInt() * 2.5f } }
                key(i) {
                    val lit = on && i == focus
                    Box(
                        Modifier.size(panelW, panelH).zIndex(zOrder)
                            .graphicsLayer {
                                val d = dNow()
                                val dd = d.coerceAtLeast(0f)
                                val s = if (d <= 0f) 1f else 0.72f.pow(dd)
                                val x = if (d <= 0f) d * wPx * 1.15f else wPx * 0.78f * (1f - 0.72f.pow(dd)) / (1f - 0.72f)
                                translationX = x
                                scaleX = s; scaleY = s
                                transformOrigin = TransformOrigin(0f, 0.5f)
                                rotationY = -16f * dd.coerceAtMost(1f)
                                cameraDistance = 12f * density.density
                                alpha = when {
                                    d < 0f -> (1f + d * 1.6f).coerceIn(0f, 1f)
                                    d > 4.5f -> (1f - (d - 4.5f)).coerceIn(0f, 1f)
                                    else -> 1f
                                }
                            }
                            .pointerInput(i) {
                                detectTapGestures {
                                    if (PanelNav.channel != ch) panelsGo(ch)
                                    else if (latestFocus == i) panelsActivate(c, ch, i)
                                    else { if (ch == PanelNav.GAMES) select(i) else PanelNav.setColOf(ch, i); pdSound(KitSound.MOVE, UiSound.MOVE) }
                                }
                            }
                    ) { PanelFace(c, p, ch, i, lit) }
                }
            }
        }
        // The words for the panel you're on.
        if (on && n > 0) {
            Spacer(Modifier.height(10.dp))
            PanelCaption(c, p, ch, focus, compact)
        }
    }
}

/** A panel's face: white, square-cornered with a soft rim; a game in your look, a console's picture, a menu's icon. */
@Composable
private fun PanelFace(c: StyleCtx, p: PanelPalette, ch: Int, i: Int, lit: Boolean) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        Modifier.fillMaxSize().clip(shape)
            .background(Brush.verticalGradient(listOf(p.panel, lerp(p.panel, Color.Black, 0.1f))))
            .border(if (lit) 3.dp else 1.dp, if (lit) p.accent else Color.White.copy(alpha = 0.5f), shape)
    ) {
        when (ch) {
            PanelNav.GAMES -> c.games.getOrNull(i)?.let { g ->
                // The game's own art behind, softened, and the game in your look in front of it.
                (c.wide(g) ?: c.backdrop(g))?.let { AsyncImage(rememberStyleArtRequest(it), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(p.panel.copy(alpha = 0.55f), p.panel.copy(alpha = 0.85f)))))
                BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
                    val aspect = lookSlotAspect(c.type)
                    val h = minOf(maxHeight, maxWidth / aspect)
                    StyleArt(c, g, Modifier.size(h * aspect, h), focused = lit, fx = if (lit) 0f else null, ring = p.accent)
                }
            }
            PanelNav.SYSTEMS -> c.stops.getOrNull(PanelNav.systems.getOrNull(i) ?: -1)?.let { st -> PanelSystemFace(st, p) }
            else -> PanelNav.itemsOf(ch).getOrNull(i)?.let { item ->
                val gg = item.game
                if (gg != null) {
                    (c.wide(gg) ?: c.cover(gg))?.let { AsyncImage(rememberStyleArtRequest(it), gg.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))))
                    Text(item.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
                } else Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(p.accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                        Icon(item.icon, null, tint = lerp(p.accent, p.panelInk, 0.35f), modifier = Modifier.size(36.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(item.label, color = p.panelInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
        // The gloss across the top.
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.35f).background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent))))
    }
}

@Composable
private fun PanelSystemFace(st: StyleStop, p: PanelPalette) {
    val context = LocalContext.current
    val platform = st.platform?.takeIf { st.special == null && !com.shelf.launcher.data.isCollectionKey(it) }
    val photo by produceKeyed<String?>(null, platform, platform) {
        value = if (platform != null && com.shelf.launcher.data.ConsolePhotos.hasPhoto(platform)) runCatching { com.shelf.launcher.data.ConsolePhotos.url(context, platform) }.getOrNull() else null
    }
    Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                photo != null -> AsyncImage(photo, st.label, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                platform != null -> PlatformLogo(platform, st.label, Modifier.fillMaxWidth(0.8f).fillMaxHeight(0.5f), textColor = p.panelInk, blackToWhite = false)
                else -> Icon(st.icon, null, tint = p.panelInk, modifier = Modifier.size(48.dp))
            }
        }
        if (platform != null) PlatformLogo(platform, st.label, Modifier.fillMaxWidth(0.7f).height(22.dp), textColor = p.panelInk, textSize = 11.sp, blackToWhite = false)
    }
}

/** Under the row: the panel you're on in words. */
@Composable
private fun PanelCaption(c: StyleCtx, p: PanelPalette, ch: Int, focus: Int, compact: Boolean) {
    val titles = LocalTitleOptions.current
    when (ch) {
        PanelNav.GAMES -> c.games.getOrNull(focus)?.let { g ->
            Column {
                Text(titles.display(g.name), color = p.ink, fontSize = if (compact) 16.sp else 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text((if (g.sourceType == com.shelf.launcher.data.SourceType.APP) "Android" else titles.console(g.platform)) + "  ·  " + stylePlaytime(c.playtime(g), c.lastPlayed(g) != null),
                        color = p.sub, fontSize = 13.sp, maxLines = 1)
                    c.progress(g)?.let { (e, t) ->
                        Spacer(Modifier.width(14.dp))
                        Icon(Icons.Filled.EmojiEvents, null, tint = p.accent, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("$e / $t", color = p.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.width(120.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(p.sub.copy(alpha = 0.3f))) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth((e.toFloat() / t).coerceIn(0f, 1f)).background(p.accent))
                        }
                    }
                }
            }
        }
        PanelNav.SYSTEMS -> c.stops.getOrNull(PanelNav.systems.getOrNull(focus) ?: -1)?.let { st ->
            val count = remember(c.state.games, st.platform) { c.state.games.count { it.platform == st.platform } }
            Column {
                Text(st.label, color = p.ink, fontSize = if (compact) 16.sp else 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(if (count == 1) "1 game" else "$count games", color = p.sub, fontSize = 13.sp)
            }
        }
        else -> PanelNav.itemsOf(ch).getOrNull(focus)?.let { item ->
            Column {
                val gg = item.game
                Text(gg?.let { titles.display(it.name) } ?: item.label, color = p.ink, fontSize = if (compact) 16.sp else 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (gg != null) titles.console(gg.platform) + "  ·  " + stylePlaytime(c.playtime(gg), true) else item.sub, color = p.sub, fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}

// ---- Options ----

/** Update 344: Settings › Home › Layout options (Panel dashboard). Each its own "pd_…" Features pref. */
@Composable
internal fun PanelsSettings() {
    MutedNote(
        "Channels of angled panels — Spotlight, My games, Systems, Media, Settings — your profile on the left. ▲ ▼ change " +
            "the channel, ◀ ▶ along its panels, A opens, B goes back. Swipe up / down for the channels, along for the panels."
    )
    Spacer(Modifier.height(10.dp))
    StyleChoice("Colour", Features.PdColor.values(), Features.pdColor.state.value, { it.label }) { Features.pdColor.value = it }
    StyleChoice("Start on", Features.BlStart.values(), Features.pdStart.state.value, { it.label }) { Features.pdStart.value = it }
    Toggle(Icons.Filled.Person, "Your profile", "Your picture, name and points on the left (wide screens). Off: the panels take the room.", Features.pdProfile)
    Toggle(Icons.Filled.QueueMusic, "Interface sounds", "Muted ticks, a deep panel swing and soft pads. Off: Voltcap's usual sounds.", Features.pdSounds)
    if (Features.pdSounds.state.value) {
        PercentSlider("Sound volume", Features.kitVolume.state.value, { Features.kitVolume.value = it.coerceIn(0, 100) })
        Spacer(Modifier.height(6.dp))
    }
}
