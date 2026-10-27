package com.shelf.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.QueueMusic
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.HotkeyAction
import kotlin.math.floor

/*
 * Update 344: Pocket camera (1998) — Voltcap's own take on the late-90s handheld camera cartridge (original drawing, names
 * and sounds; nobody's logos or artwork). Everything in four shades of one colour, in chunky pixels:
 * - the middle: a viewfinder panel with a thick pixel frame — the "POCKET CAMERA" bar and the photo's number, the game
 *   you're on as a dithered photo (its cover, a screenshot or its art: ▲ ▼ pick which), its name, year and console,
 *   pixel icons for its achievements and time played;
 * - left and right: the album — the four games before and the four after as small dithered photos (tap one to pick it).
 * Moving on snaps the shutter (the photo blinks shut and opens on the next) or develops it line by line (Layout options).
 * ◀ ▶ the games (Endless: round), ▲ ▼ the picture, A the game's window — a camera menu with a ► cursor — X / Y / L2 / R2
 * as everywhere. Palettes: the classic green, the pocket grey and four colour ones.
 */

/** Update 344: where the controller is in Pocket camera. */
internal object CameraNav {
    /** 0 cover, 1 screenshot, 2 art. */
    var source by mutableIntStateOf(0)
    @Volatile var ctx: StyleCtx? = null
    internal val keysDown = HashSet<Int>()
    fun signature(): Any = source
}

/** The four shades (darkest first) of the palette picked. */
internal fun cameraPalette(p: Features.PcPalette): IntArray = p.shades.map { it.toInt() }.toIntArray()

private fun pcSound(s: KitSound, fallback: UiSound?) = LayoutKitSounds.play(LayoutKit.CAMERA, s, fallback)

private fun pcHotkey(a: HotkeyAction): Boolean {
    val h = GreenCoreHooks.hotkey ?: return false
    h(a)
    return true
}

/**
 * Update 344: Pocket camera's keys. ◀ ▶ the games (Endless round), ▲ ▼ which picture the photo shows (cover, screenshot,
 * art), A / B / X / Y / L / R the home screen's own. Start: quick menu; Select: sort & filter.
 */
internal fun pocketCameraKey(event: KeyEvent, reach: (Boolean) -> Boolean): Boolean {
    val c = CameraNav.ctx ?: return false
    val code = event.nativeKeyEvent.keyCode
    val down = event.type == KeyEventType.KeyDown
    val up = event.type == KeyEventType.KeyUp
    if (up && CameraNav.keysDown.remove(code)) return true
    val k = event.key
    if (k == Key.ButtonStart || k == Key.Menu) { if (up) c.actions.quickMenu(); return up }
    if (k == Key.ButtonSelect) return up && pcHotkey(HotkeyAction.SORT)
    if (!down) return false
    val first = event.nativeKeyEvent.repeatCount == 0
    fun take(): Boolean { CameraNav.keysDown += code; return true }
    val n = c.games.size
    when (k) {
        Key.DirectionLeft, Key.DirectionRight -> {
            if (n == 0) return take()
            val d = if (k == Key.DirectionRight) 1 else -1
            val next = if (StyleLoop.on && n > 1) (c.focus + d).mod(n) else (c.focus + d).coerceIn(0, n - 1)
            if (next != c.focus) { c.actions.select(next); pcSound(KitSound.MOVE, UiSound.MOVE) } else if (first) EdgeBump.hit(d)
            return take()
        }
        Key.DirectionUp, Key.DirectionDown -> {
            if (!first) return take()
            // From the cover ▲ goes on past it (the status bar, the player above); otherwise ▲ ▼ turn the picture.
            if (k == Key.DirectionUp && CameraNav.source == 0) { reach(true); return take() }
            val d = if (k == Key.DirectionDown) 1 else -1
            CameraNav.source = (CameraNav.source + d).mod(3)
            pcSound(KitSound.SPECIAL, UiSound.MOVE)
            return take()
        }
        else -> return if (n == 0 && (k == Key.ButtonA || k == Key.ButtonX || k == Key.ButtonY)) take() else false
    }
}

// ---- Pixel icons (7 × 7, "#" lit) ----

private val PX_CAMERA = arrayOf("  ###  ", "#######", "#     #", "#  #  #", "# ### #", "#  #  #", "#######")
private val PX_TROPHY = arrayOf("#######", "# ### #", " ##### ", "  ###  ", "   #   ", "  ###  ", " ##### ")
private val PX_CLOCK = arrayOf(" ##### ", "#  #  #", "#  #  #", "#  ## #", "#     #", "#     #", " ##### ")
private val PX_STAR = arrayOf("   #   ", "   #   ", "#######", " ##### ", "  ###  ", " ## ## ", "##   ##")
internal val PX_CURSOR = arrayOf("#      ", "##     ", "###    ", "####   ", "###    ", "##     ", "#      ")

internal fun DrawScope.pixelIcon(icon: Array<String>, at: Offset, u: Float, color: Color) {
    for (y in icon.indices) for (x in icon[y].indices) if (icon[y][x] == '#') drawRect(color, Offset(at.x + x * u, at.y + y * u), Size(u, u))
}

@Composable
private fun PixelIcon(icon: Array<String>, u: Dp, color: Color) {
    Canvas(Modifier.size(u * 7)) { pixelIcon(icon, Offset.Zero, u.toPx(), color) }
}

/** A chunky pixel frame: [thick] dark edge with notched corners round a light face. */
internal fun DrawScope.pixelFrame(u: Float, face: Color, edge: Color, thick: Int = 2) {
    val t = u * thick
    drawRect(face, Offset(t, t), Size(size.width - 2 * t, size.height - 2 * t))
    // The edge, its corners notched by one pixel.
    drawRect(edge, Offset(u, 0f), Size(size.width - 2 * u, t))
    drawRect(edge, Offset(u, size.height - t), Size(size.width - 2 * u, t))
    drawRect(edge, Offset(0f, u), Size(t, size.height - 2 * u))
    drawRect(edge, Offset(size.width - t, u), Size(t, size.height - 2 * u))
}

// ---- The backdrop ----

/** Update 344: the darkest shade with a faint checker of pixels. */
@Composable
internal fun PocketCameraBackdrop(modifier: Modifier) {
    val pal = cameraPalette(Features.pcPalette.state.value)
    val grid = Features.pcGrid.state.value
    Canvas(modifier) {
        drawRect(Color(pal[0]))
        if (!grid) return@Canvas
        val u = floor(size.minDimension / 120f).coerceAtLeast(3f)
        var y = 0f; var row = 0
        val dot = Color(pal[1]).copy(alpha = 0.35f)
        while (y < size.height) {
            var x = if (row % 2 == 0) 0f else u * 2f
            while (x < size.width) { drawRect(dot, Offset(x, y), Size(u * 0.5f, u * 0.5f)); x += u * 4f }
            y += u * 2f; row++
        }
    }
}

// ---- The layout ----

@Composable
internal fun StylePocketCamera(c: StyleCtx, modifier: Modifier = Modifier) {
    SideEffect {
        CameraNav.ctx = c
        StyleNav.side = emptyList(); StyleNav.top = emptyList(); StyleNav.bar = emptyList()
        StyleLoop.on = loopRowOn(c)
    }
    rememberLayoutKit(LayoutKit.CAMERA, Features.pcSounds.state.value, c.state.appearance.soundsEnabled)
    DisposableEffect(Unit) { onDispose { CameraNav.keysDown.clear() } }
    val caption = CategoryTitle.captionOn(c.state.appearance)
    if (caption) CategoryTitle.Native(null)
    val pal = cameraPalette(Features.pcPalette.state.value)
    val c0 = Color(pal[0]); val c1 = Color(pal[1]); val c2 = Color(pal[2]); val c3 = Color(pal[3])
    val g = c.game
    // The screenshot / art for the photo, asked for when that's the picture picked.
    val source = CameraNav.source
    LaunchedEffect(g?.stableKey, source) {
        val gg = g ?: return@LaunchedEffect
        kotlinx.coroutines.delay(250)
        if (source == 1 && !c.state.screenshots.containsKey(gg.stableKey)) c.actions.needMedia(gg)
    }
    val font = pocketFont()
    val density = LocalDensity.current
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    val latestFocus by rememberUpdatedState(c.focus)
    val latestCount by rememberUpdatedState(c.games.size)
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()
        // Swipe across: the next / last game.
        .pointerInput(Unit) {
            var acc = 0f
            detectHorizontalDragGestures(onDragStart = { acc = 0f }) { ch, dx ->
                ch.consume(); acc += dx
                val step = 70.dp.toPx()
                if (acc <= -step) { acc = 0f; if (latestFocus + 1 < latestCount) { select(latestFocus + 1); pcSound(KitSound.MOVE, UiSound.MOVE) } }
                if (acc >= step) { acc = 0f; if (latestFocus > 0) { select(latestFocus - 1); pcSound(KitSound.MOVE, UiSound.MOVE) } }
            }
        }
    ) {
        if (g == null) {
            Text(EmptyShelf.text.uppercase(), color = c3, fontFamily = font, fontSize = 14.sp, modifier = Modifier.align(Alignment.Center))
            return@BoxWithConstraints
        }
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        // One "pixel" of the camera's screen, and the photo's whole-number scale. (Update 346: on a wide screen the facts
        // stand beside the photo, as on the camera's own info screen, so the photo can be drawn bigger — on the Thor
        // 6× instead of 4×; the photo stays at whole pixels, never smoothed.)
        val wide = maxWidth > maxHeight * 1.5f
        val scale = if (wide) {
            val s = floor(hPx * 0.86f / 131f).coerceAtLeast(1f)
            val fit = floor(wPx * 0.72f / 212f).coerceAtLeast(1f)
            minOf(s, fit)
        } else floor((hPx * 0.58f) / 112f).coerceAtLeast(1f).let { s -> if (128f * s > wPx * 0.6f) floor(wPx * 0.6f / 128f).coerceAtLeast(1f) else s }
        val u = with(density) { (scale.coerceAtLeast(2f)).toDp() }
        val photoW = with(density) { (128f * scale).toDp() }
        val photoH = with(density) { (112f * scale).toDp() }
        val infoW = u * 72
        val panelW = if (wide) photoW + infoW + u * 12 else photoW + u * 8
        val sideRoom = (maxWidth - panelW) / 2
        val thumb = (sideRoom * 0.62f).coerceAtMost(maxHeight / 5.2f)
        val showSides = thumb >= 40.dp
        // (Update 346: never smaller than ~9 sp for the small print, however small the camera's pixel gets on a short screen.)
        val fs: (Float) -> TextUnit = { k -> with(density) { maxOf((u * k).toSp().value, k * 2.1f).sp } }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (showSides) CameraAlbum(c, (1..4).map { c.focus - it }.reversed(), thumb, pal, u, font, fs, select, Alignment.End, Modifier.width(sideRoom).padding(end = u * 6))
            // The viewfinder.
            Box(Modifier.width(panelW).padding(vertical = u * 2)) {
                Canvas(Modifier.matchParentSize()) { pixelFrame(u.toPx(), c3, c0, 3) }
                Column(Modifier.padding(u * 4)) {
                    // The bar: the cartridge's name and the photo's number.
                    Row(Modifier.fillMaxWidth().background(c0).padding(horizontal = u * 2, vertical = u), verticalAlignment = Alignment.CenterVertically) {
                        PixelIcon(PX_CAMERA, u * 0.9f, c3)
                        Spacer(Modifier.width(u * 2))
                        Text("POCKET CAMERA", color = c3, fontFamily = font, fontSize = fs(5.5f), maxLines = 1, modifier = Modifier.weight(1f))
                        Text("No." + (c.focus + 1).toString().padStart(3, '0'), color = c2, fontFamily = font, fontSize = fs(5.5f), maxLines = 1)
                    }
                    Spacer(Modifier.height(u * 2))
                    val photo = @Composable { CameraPhoto(c, g, source, photoW, photoH, pal, Modifier.pointerInput(g.stableKey) { detectTapGestures { open() } }) }
                    if (wide) Row {
                        photo()
                        Spacer(Modifier.width(u * 4))
                        Column(Modifier.width(infoW).height(photoH)) { CameraFacts(c, g, pal, u, font, fs, stacked = true) }
                    } else {
                        photo()
                        Spacer(Modifier.height(u * 2))
                        CameraFacts(c, g, pal, u, font, fs, stacked = false)
                    }
                }
            }
            if (showSides) CameraAlbum(c, (1..4).map { c.focus + it }, thumb, pal, u, font, fs, select, Alignment.Start, Modifier.width(sideRoom).padding(start = u * 6))
        }
        // The album's name top left, the hints along the bottom.
        if (caption) Text(("ALBUM: " + c.categoryLabel).uppercase(), color = c2, fontFamily = font, fontSize = fs(5f), maxLines = 1,
            modifier = Modifier.align(Alignment.TopStart).padding(start = u * 4, top = u))
        Text(
            "< > SELECT   ^ v " + when (source) { 1 -> "SCREENSHOT"; 2 -> "ARTWORK"; else -> "COVER" } + "   A VIEW",
            color = c2, fontFamily = font, fontSize = fs(4.5f), maxLines = 1,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = u)
        )
    }
}

/** The photo: the picked picture dithered into the palette; moving on snaps the shutter or develops it line by line. */
@Composable
private fun CameraPhoto(c: StyleCtx, g: Game, source: Int, w: Dp, h: Dp, pal: IntArray, modifier: Modifier) {
    val url = when (source) {
        1 -> c.state.screenshots[g.stableKey]?.firstOrNull() ?: c.cover(g)
        2 -> c.backdrop(g) ?: c.wide(g)
        else -> c.cover(g)
    }
    val bmp = rememberDithered(url, 128, 112, pal)
    val shift = Features.pcShift.state.value
    val reduced = !rememberAnimationsEnabled()
    val shut = remember { Animatable(0f) }
    LaunchedEffect(g.stableKey, source) {
        if (reduced || shift == Features.PcShift.INSTANT) { shut.snapTo(0f); return@LaunchedEffect }
        shut.snapTo(1f)
        shut.animateTo(0f, tween(if (shift == Features.PcShift.DEVELOP) 520 else 240, easing = LinearEasing))
    }
    val c0 = Color(pal[0]); val c3 = Color(pal[3])
    Box(modifier.size(w, h).border(1.dp, c0)) {
        if (bmp != null) Image(bmp, g.name, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.None)
        else Box(Modifier.fillMaxSize().background(Color(pal[2])))
        // The shutter (two blinds meeting in the middle) or the develop (the photo appearing line by line from the top).
        Canvas(Modifier.fillMaxSize()) {
            val k = shut.value
            if (k <= 0f) return@Canvas
            val step = size.height / 112f
            if (shift == Features.PcShift.DEVELOP) {
                val shown = floor((1f - k) * 112f) * step
                drawRect(c3, Offset(0f, shown), Size(size.width, size.height - shown))
                drawRect(c0, Offset(0f, shown), Size(size.width, step * 2f))
            } else {
                val half = size.height / 2f * k
                drawRect(c0, Offset.Zero, Size(size.width, floor(half / step) * step))
                drawRect(c0, Offset(0f, size.height - floor(half / step) * step), Size(size.width, floor(half / step) * step))
            }
        }
    }
}

/**
 * The photo's facts: its name, year and console, achievements, time played (and a star when it's a favourite). [stacked]:
 * one under the other beside the photo (wide screens), else under the photo as before.
 */
@Composable
private fun CameraFacts(c: StyleCtx, g: Game, pal: IntArray, u: Dp, font: FontFamily, fs: (Float) -> TextUnit, stacked: Boolean) {
    val c0 = Color(pal[0]); val c1 = Color(pal[1])
    val titles = LocalTitleOptions.current
    val meta = c.state.metadata[g.stableKey]
    val year = meta?.releaseYear?.toString()
    val console = if (g.sourceType == com.shelf.launcher.data.SourceType.APP) "ANDROID" else titles.console(g.platform).uppercase()
    val time = stylePlaytime(c.playtime(g), c.lastPlayed(g) != null).uppercase()
    val fav = g.stableKey in c.state.favorites
    if (stacked) {
        FullText(titles.display(g.name).uppercase(), color = c0, fontFamily = font, fontSize = fs(6f), lineHeight = fs(7.5f), maxLines = 3)
        Spacer(Modifier.height(u * 2))
        Box(Modifier.fillMaxWidth().height(u).background(c0))
        Spacer(Modifier.height(u * 3))
        // (Shrinks rather than breaking a long console name in the middle of a word.)
        FitText(console, c1, maxSize = fs(4.5f), minSize = 7.sp, maxLines = 1, style = androidx.compose.ui.text.TextStyle(fontFamily = font))
        year?.let { Text(it, color = c1, fontFamily = font, fontSize = fs(4.5f), maxLines = 1) }
        Spacer(Modifier.height(u * 4))
        c.progress(g)?.let { (e, t) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelIcon(PX_TROPHY, u * 0.9f, c0); Spacer(Modifier.width(u * 2))
                Text("$e/$t", color = c0, fontFamily = font, fontSize = fs(5f), maxLines = 1)
            }
            Spacer(Modifier.height(u * 2))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelIcon(PX_CLOCK, u * 0.9f, c0); Spacer(Modifier.width(u * 2))
            FullText(time, color = c0, fontFamily = font, fontSize = fs(5f), lineHeight = fs(6.5f), maxLines = 2)
        }
        if (fav) {
            Spacer(Modifier.height(u * 2))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelIcon(PX_STAR, u * 0.9f, c0); Spacer(Modifier.width(u * 2))
                Text("FAVOURITE", color = c0, fontFamily = font, fontSize = fs(5f), maxLines = 1)
            }
        }
        return
    }
    FullText(titles.display(g.name).uppercase(), color = c0, fontFamily = font, fontSize = fs(7f), maxLines = 1)
    FullText(listOfNotNull(year, console).joinToString("  "), color = c1, fontFamily = font, fontSize = fs(5f), maxLines = 1)
    Spacer(Modifier.height(u * 2))
    Row(verticalAlignment = Alignment.CenterVertically) {
        c.progress(g)?.let { (e, t) ->
            PixelIcon(PX_TROPHY, u * 0.9f, c0); Spacer(Modifier.width(u * 2))
            Text("$e/$t", color = c0, fontFamily = font, fontSize = fs(5f)); Spacer(Modifier.width(u * 5))
        }
        PixelIcon(PX_CLOCK, u * 0.9f, c0); Spacer(Modifier.width(u * 2))
        FullText(time, color = c0, fontFamily = font, fontSize = fs(5f), maxLines = 1, modifier = Modifier.weight(1f, fill = false))
        if (fav) { Spacer(Modifier.width(u * 5)); PixelIcon(PX_STAR, u * 0.9f, c0) }
    }
}

/** A column of the album: small dithered photos of the games at [indexes] (those past the ends left out), kept to the viewfinder's side. */
@Composable
private fun CameraAlbum(
    c: StyleCtx, indexes: List<Int>, thumb: Dp, pal: IntArray, u: Dp, font: FontFamily, fs: (Float) -> TextUnit, select: (Int) -> Unit,
    align: Alignment.Horizontal, modifier: Modifier
) {
    val n = c.games.size
    val c0 = Color(pal[0]); val c2 = Color(pal[2]); val c3 = Color(pal[3])
    Column(modifier, horizontalAlignment = align, verticalArrangement = Arrangement.spacedBy(u * 2, Alignment.CenterVertically)) {
        indexes.forEach { raw ->
            val i = if (StyleLoop.on && n > 1) raw.mod(n) else raw
            if (i !in 0 until n || i == c.focus) { Spacer(Modifier.size(thumb)); return@forEach }
            val g = c.games[i]
            key(raw) {
                val bmp = rememberDithered(c.cover(g), 48, 48, pal)
                Box(Modifier.size(thumb).pointerInput(i) { detectTapGestures { select(i); pcSound(KitSound.MOVE, UiSound.MOVE) } }) {
                    Canvas(Modifier.fillMaxSize()) { pixelFrame(u.toPx(), c3, c2, 2) }
                    Box(Modifier.fillMaxSize().padding(u * 3)) {
                        if (bmp != null) Image(bmp, g.name, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.None)
                    }
                    Text((i + 1).toString().padStart(3, '0'), color = c3, fontFamily = font, fontSize = fs(4f),
                        modifier = Modifier.align(Alignment.BottomEnd).background(c0).padding(horizontal = u))
                }
            }
        }
    }
}

// ---- Options ----

/** Update 344: Settings › Home › Layout options (Pocket camera). Each its own "pc_…" Features pref. */
@Composable
internal fun PocketCameraSettings() {
    MutedNote(
        "Your games as dithered photos in four shades: the one you're on big in the viewfinder, the album of the games " +
            "before and after either side. ◀ ▶ the games, ▲ ▼ the photo (cover, screenshot, artwork), A the game's camera " +
            "menu. Tap an album photo to pick it, swipe across for the next one."
    )
    Spacer(Modifier.height(10.dp))
    StyleChoice("Palette", Features.PcPalette.values(), Features.pcPalette.state.value, { it.label }) { Features.pcPalette.value = it }
    StyleChoice("Moving on", Features.PcShift.values(), Features.pcShift.state.value, { it.label }) { Features.pcShift.value = it }
    Toggle(Icons.Filled.GridOn, "Screen dots", "A faint checker of pixels behind everything. Off: plain.", Features.pcGrid)
    Toggle(Icons.Filled.QueueMusic, "Interface sounds", "Square-wave blips and a shutter snap. Off: Voltcap's usual sounds.", Features.pcSounds)
    if (Features.pcSounds.state.value) {
        PercentSlider("Sound volume", Features.kitVolume.state.value, { Features.kitVolume.value = it.coerceIn(0, 100) })
        Spacer(Modifier.height(6.dp))
    }
}
