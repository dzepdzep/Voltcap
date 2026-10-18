package com.shelf.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.shelf.launcher.data.Features
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Update 181: Pocket ribbon — Voltcap's own take on the menu of the 2008 two-screen pocket handheld (original art and
 * names only):
 * - top screen: white paper with faint pinstripes, a thin status line (volume, your name; date, time, battery) and a big
 *   framed picture: the game's art, the album playing, your own photo, or a slideshow of the game's screenshots;
 * - bottom screen: a speech bubble with the name of the one you're on, one row of square icons in white frames gliding
 *   along with the one you're on in the middle (bigger, framed in your colour, a START tab under it), and a scroll bar
 *   with ◀ ▶ and where you are in the row along the bottom.
 * The row is your categories; A opens one and its games fill the row; B goes back to the categories. A on a game opens
 * its game window, START (or the START tab) starts it. Touch: swipe the row, tap an icon to pick it, tap it again or the
 * START tab to open it, tap the scroll bar to jump.
 */

private val RPaper = Color(0xFFFFFFFF)
private val RStripe = Color(0xFFF1F2F4)
private val RLower = Color(0xFFE9EBEE)
private val RLowerStripe = Color(0xFFE1E4E8)
private val REdge = Color(0xFFAAB0B8)
private val RInk = Color(0xFF3A3F46)
private val RMuted = Color(0xFF8A9099)
private val RFrame = Color(0xFFFFFFFF)

@Composable
internal fun StylePocketRibbon(c: StyleCtx, modifier: Modifier = Modifier) {
    SideEffect {
        PocketNav.ctx = c
        StyleNav.side = emptyList(); StyleNav.top = emptyList(); StyleNav.bar = emptyList()
        // (Update 226, #98: Endless read whatever the layout before had set — the R1 tile and Settings changed nothing.)
        StyleLoop.on = loopRowOn(c)
    }
    PocketStartCategory(c)
    // Opens on the categories or on the games of the category you're in (Layout options), once per visit.
    LaunchedEffect(Unit) {
        if (Features.ribbonStartOnCategories.value) { PocketNav.ribbonGames = false; PocketNav.ribbonCat = c.stop.coerceAtLeast(0) }
    }
    val split = Features.ribbonSplit.state.value / 100f
    TwinHost(
        c, modifier,
        top = { m, framed -> PocketRibbonTop(c, m, framed) },
        bottom = { m, framed -> PocketRibbonBottom(c, m, framed) },
        topH = (480 * split).dp, bottomH = (480 * (1f - split)).dp, minW = 320.dp,
        stacked = when (Features.pocketArrange.state.value) {
            Features.PocketArrange.STACKED -> true; Features.PocketArrange.SIDE -> false; else -> null
        }
    )
}

private fun ribbonAccentNow(): PocketAccent = runCatching { PocketAccent.valueOf(Features.ribbonAccent.value) }.getOrDefault(PocketAccent.SKY)

// ---- Top screen: the picture ----

@Composable
private fun PocketRibbonTop(c: StyleCtx, modifier: Modifier, framed: Boolean) {
    val accent = pocketAccent(runCatching { PocketAccent.valueOf(Features.ribbonAccent.state.value) }.getOrDefault(PocketAccent.SKY))
    val font = pocketFont()
    val time = rememberTwinTime(seconds = false)
    val tz = remember { java.util.TimeZone.getDefault() }
    val battery = rememberBatteryInfo()
    val user = pocketUser(c.state)
    val g = c.game
    val context = LocalContext.current
    val source = Features.ribbonTop.state.value
    // The picture: the game's art, the album playing, your photo, or the game's screenshots one after another.
    val shots = g?.let { c.state.screenshots[it.stableKey] }.orEmpty()
    val slideIndex by produceState(0, g?.stableKey, source, shots.size) {
        value = 0
        if (source == Features.RibbonTop.SLIDES && shots.size > 1 && Features.ribbonSlides.value) {
            while (true) { kotlinx.coroutines.delay(5000); value = (value + 1) % shots.size }
        }
    }
    // (Update 229, #03: at a track change the first news often had no cover yet — the game's picture flashed before the
    // album) The last cover stays while music is playing or paused; only no music at all goes back to the game.
    val np = c.state.nowPlaying
    val heldArt = remember { arrayOfNulls<android.graphics.Bitmap>(1) }
    if (np == null) heldArt[0] = null else np.art?.let { heldArt[0] = it }
    val model: Any? = when (source) {
        Features.RibbonTop.ALBUM -> (np?.art ?: heldArt[0]?.takeIf { np != null && !it.isRecycled }) ?: g?.let { c.backdrop(it) }
        Features.RibbonTop.PHOTO -> PocketPhoto.file(context)?.takeIf { it.isFile } ?: g?.let { c.backdrop(it) }
        Features.RibbonTop.SLIDES -> shots.getOrNull(slideIndex) ?: g?.let { c.backdrop(it) }
        else -> g?.let { c.backdrop(it) ?: c.wide(it) }
    }
    PocketRibbonFrame(modifier, framed) {
        PocketScreen(Modifier.fillMaxSize(), responsive = !Features.pocketPixel.state.value, outside = RPaper) {
            val uPx = d(1f).value * density
            Canvas(Modifier.atFull(0f, 0f, width, fullH)) { pocketPinstripes(uPx, RPaper, RStripe) }
            val status = Features.ribbonStatus.state.value
            if (status) {
                Canvas(Modifier.atScreen(3f, 1f, 10f, 10f)) { pocketIcon(PocketIcons.VOLUME, RInk, accent) }
                PocketText(this, user, 15f, 1f, 110f, 10f, 7.5f, accent, font, TextAlign.Start, screenX = true)
                val day = pocketDay(time.longValue, tz)
                PocketText(this, "%02d/%02d  %d:%02d".format(day.month, day.dayOfMonth, day.hour, day.minute), width - 22f - 90f, 1f, 90f, 10f, 7.5f, RInk, font, TextAlign.End, screenX = true)
                Canvas(Modifier.atScreen(width - 18f, 2.5f, 13f, 7f)) { pocketBattery(uPx, battery.percent, RInk) }
                Canvas(Modifier.atScreen(0f, 12f, width, 1f)) { drawRect(REdge.copy(alpha = 0.6f)) }
            }
            // The framed picture.
            val top = if (status) 18f else 10f
            val h = 160f - (if (status) 0f else -8f)
            Box(Modifier.at(20f, top, 216f, h.coerceAtMost(POCKET_H - top - 14f))) {
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(Color(0x22000000), Offset(uPx, uPx * 1.5f), size)
                    drawRect(RFrame)
                    pocketOutline(uPx, REdge)
                }
                Box(Modifier.fillMaxSize().padding(d(4f)).background(Color(0xFFDDE1E6))) {
                    if (model != null) HeldImage(model, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, imageModifier = Modifier.pixelArtwork())
                    else if (g != null) Box(Modifier.fillMaxSize().background(placeholderBrush(g.hue)))
                }
            }
            // Small captions under the picture: the category, and where you are in it.
            CategoryTitle.Native() // (Update 212: the layout's own category title.)
            PocketText(this, c.categoryLabel, 22f, POCKET_H - 13f, 140f, 11f, 6.5f, RMuted, font, TextAlign.Start)
            if (c.games.isNotEmpty()) PocketText(this, "${c.focus + 1} / ${c.games.size}", 154f, POCKET_H - 13f, 80f, 11f, 6.5f, RMuted, font, TextAlign.End)
        }
    }
}

/** Update 181: your own photo for Pocket ribbon's top screen (Layout options › Pick a photo). */
internal object PocketPhoto {
    fun file(context: android.content.Context): java.io.File = java.io.File(context.filesDir, "pocket_photo")
    fun set(context: android.content.Context, uri: android.net.Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input -> file(context).outputStream().use { input.copyTo(it) } } != null
    }.getOrDefault(false)
}

@Composable
private fun PocketRibbonFrame(modifier: Modifier, framed: Boolean, content: @Composable () -> Unit) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
    Box(
        if (framed) modifier.clip(shape).background(Color(0xFF2B2F35)).padding(3.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp)).background(RPaper)
        else modifier.background(RPaper)
    ) { content() }
}

// ---- Bottom screen: the ribbon ----

/** One icon in the row: a category or a game. */
private class RibbonItem(val key: String, val title: String, val sub: String, val game: com.shelf.launcher.data.Game?, val stop: StyleStop?)

@Composable
internal fun PocketRibbonBottom(c: StyleCtx, modifier: Modifier, framed: Boolean) {
    val accent = pocketAccent(runCatching { PocketAccent.valueOf(Features.ribbonAccent.state.value) }.getOrDefault(PocketAccent.SKY))
    val font = pocketFont()
    val titles = LocalTitleOptions.current
    val gamesMode = PocketNav.ribbonGames
    val live by androidx.compose.runtime.rememberUpdatedState(c)
    val n = if (gamesMode) c.games.size else c.stops.size
    val cur = (if (gamesMode) c.focus else PocketNav.ribbonCat).coerceIn(0, (n - 1).coerceAtLeast(0))
    fun item(i: Int): RibbonItem = if (gamesMode) {
        val g = c.games[i]
        RibbonItem(g.stableKey, titles.display(g.name), titles.console(g.platform), g, null)
    } else {
        val s = c.stops[i]
        RibbonItem("stop:" + pocketStopKey(s), s.label, if (i == c.stop) "${c.games.size} games" else "Category", null, s)
    }
    val iconSize = Features.ribbonIcon.state.value.toFloat()
    val gap = Features.ribbonGap.state.value.toFloat()
    val pitch = iconSize + 6f + gap
    // The row glides so the one you're on stays in the middle (snappy, settling without wobble).
    val pos by animateFloatAsState(cur.toFloat(), if (pocketMs(160) == 0) tween(0) else spring(dampingRatio = 0.9f, stiffness = 900f), label = "ribbonPos")
    var drag by remember { mutableFloatStateOf(0f) }
    // A small bounce at the ends.
    val bump = remember { Animatable(0f) }
    LaunchedEffect(PocketNav.bumpAt) {
        if (PocketNav.bumpAt == 0L) return@LaunchedEffect
        bump.snapTo(0f)
        bump.animateTo(PocketNav.bump * 6f, tween(pocketMs(70)))
        bump.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 600f))
    }
    // Starting: the others fade, the one you're on grows a little.
    val launch by animateFloatAsState(if (PocketNav.launching) 1f else 0f, tween(pocketMs(180)), label = "ribbonLaunch")
    PocketRibbonFrame(modifier, framed) {
        val strip = pocketStripOn()
        PocketScreen(Modifier.fillMaxSize(), responsive = !Features.pocketPixel.state.value, outside = RLower,
            designH = POCKET_H + if (strip) POCKET_STRIP_H else 0f) {
            val uPx = d(1f).value * density
            Canvas(Modifier.atFull(0f, 0f, width, fullH)) { pocketPinstripes(uPx, RLower, RLowerStripe) }
            // The speech bubble with the name.
            Box(Modifier.at(20f, 6f, 216f, 46f)) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = CornerRadius(uPx * 5f)
                    val tail = Path().apply {
                        moveTo(size.width / 2f - uPx * 6f, size.height - uPx * 6f)
                        lineTo(size.width / 2f, size.height)
                        lineTo(size.width / 2f + uPx * 6f, size.height - uPx * 6f)
                        close()
                    }
                    drawRoundRect(Color(0x1A000000), Offset(0f, uPx), Size(size.width, size.height - uPx * 6f), r)
                    drawRoundRect(RFrame, Offset.Zero, Size(size.width, size.height - uPx * 6f), r)
                    drawRoundRect(REdge, Offset.Zero, Size(size.width, size.height - uPx * 6f), r, style = Stroke(uPx))
                    drawPath(tail, RFrame)
                    drawLine(REdge, Offset(size.width / 2f - uPx * 6f, size.height - uPx * 6f), Offset(size.width / 2f, size.height), uPx)
                    drawLine(REdge, Offset(size.width / 2f + uPx * 6f, size.height - uPx * 6f), Offset(size.width / 2f, size.height), uPx)
                }
            }
            if (n == 0) PocketText(this, "There's nothing here.", 24f, 8f, 208f, 34f, 8f, RMuted, font, TextAlign.Center)
            else {
                val it0 = item(cur)
                if (Features.ribbonNames.state.value) {
                    PocketText(this, it0.title, 24f, 9f, 208f, 18f, 8.5f, RInk, font, TextAlign.Center)
                    PocketText(this, it0.sub, 24f, 26f, 208f, 12f, 6.5f, RMuted, font, TextAlign.Center)
                } else PocketText(this, it0.title, 24f, 9f, 208f, 30f, 8.5f, RInk, font, TextAlign.Center, maxLines = 2)
            }
            // The row of icons (only the ones in view are made).
            val rowY = 62f
            val mid = width / 2f
            Box(
                Modifier.atScreen(0f, rowY - 6f, width, iconSize + 40f).clipToBounds()
                    .pointerInput(gamesMode, n) {
                        detectHorizontalDragGestures(
                            onDragEnd = { drag = 0f }, onDragCancel = { drag = 0f }
                        ) { ch, dx ->
                            ch.consume()
                            drag += dx / uPx
                            val step = pitch * 0.6f
                            if (drag > step) { drag -= pitch; ribbonMove(live, -1) } else if (drag < -step) { drag += pitch; ribbonMove(live, 1) }
                        }
                    }
            ) {
                // Update 187: a soft glow round the one you're on (gently breathing; still while idle or saving battery).
                if (Features.ribbonGlow.state.value && n > 0) {
                    val pulse = rememberFocusPulse(true)
                    val gw = iconSize * 2.4f
                    val gh = iconSize + 34f
                    val gx = mid + (cur - pos) * pitch + drag + bump.value
                    Canvas(Modifier.atScreen(gx - gw / 2f, 8f + (iconSize + 6f) / 2f - gh / 2f, gw, gh)) {
                        val a = (0.2f + 0.2f * pulse) * (1f + 0.6f * launch)
                        val r = size.width / 2f
                        scale(1f, size.height / size.width, pivot = center) {
                            drawCircle(
                                androidx.compose.ui.graphics.Brush.radialGradient(
                                    0f to accent.copy(alpha = a.coerceAtMost(1f)), 0.45f to accent.copy(alpha = (a * 0.4f).coerceAtMost(1f)),
                                    1f to Color.Transparent, center = center, radius = r
                                ),
                                radius = r, center = center
                            )
                        }
                    }
                }
                val shown = ((width / pitch) / 2f).toInt() + 2
                val from = (pos.roundToInt() - shown).coerceAtLeast(0)
                val to = (pos.roundToInt() + shown).coerceAtMost(n - 1)
                for (i in from..to) {
                    androidx.compose.runtime.key(if (gamesMode) "g$i" else "s$i") {
                    val rel = i - pos
                    val x = mid + rel * pitch + drag + bump.value
                    val lit = i == cur
                    val grow by animateFloatAsState(if (lit) 1.15f else 1f, tween(pocketMs(120)), label = "ribbonGrow")
                    val alpha = if (lit) 1f else (1f - launch)
                    val scale = grow * (if (lit) 1f + 0.12f * launch else 1f)
                    val it1 = item(i)
                    Box(
                        Modifier.atScreen(x - (iconSize + 6f) / 2f, 6f + 2f, iconSize + 6f, iconSize + 6f)
                            .zIndex(if (lit) 2f else 1f)
                            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
                            .pointerInput(i, gamesMode) {
                                detectTapGestures { if (i == ribbonCur(live)) ribbonOpen(live) else ribbonSelect(live, i) }
                            }
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawRect(Color(0x26000000), Offset(0f, uPx), size)
                            drawRect(RFrame)
                            if (lit) pocketOutline(uPx, accent, 2f) else pocketOutline(uPx, REdge)
                        }
                        Box(Modifier.fillMaxSize().padding(d(3f)).background(Color(0xFFF2F3F5)), contentAlignment = Alignment.Center) {
                            val g = it1.game
                            val st = it1.stop
                            // (Update 183: in your game look — cartridges, cases, discs — when it's one of those.)
                            if (g != null && pocketUsesLook(c.type)) PocketLookArt(c, g, Modifier.fillMaxSize())
                            else if (g != null) {
                                val url = coverOrAppIcon(g, c.cover(g))
                                if (url != null) AsyncImage(url, g.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().pixelArtwork())
                                else Box(Modifier.fillMaxSize().background(placeholderBrush(g.hue)))
                            } else if (st != null) {
                                val p = st.platform
                                if (p != null && !com.shelf.launcher.data.isCollectionKey(p)) PlatformLogo(p, st.label, Modifier.fillMaxSize(), tint = RInk, textColor = RInk, textSize = sp(5f))
                                else Icon(st.icon, null, tint = RInk, modifier = Modifier.fillMaxSize(0.7f))
                            }
                        }
                    }
                    }
                }
                // The START tab under the one in the middle.
                if (n > 0) Box(
                    Modifier.atScreen(mid - 20f, 6f + iconSize + 6f + 6f, 40f, 12f)
                        .graphicsLayer { alpha = 1f - launch * 0.5f }
                        .pointerInput(Unit) { detectTapGestures { ribbonStart(live) } }
                ) {
                    Canvas(Modifier.fillMaxSize()) { drawRoundRect(accent, cornerRadius = CornerRadius(uPx * 3f)) }
                    PocketText(this@PocketScreen, if (PocketNav.ribbonGames) "START" else "OPEN", -cx, 0f, 40f, 12f, 7f, Color.White, font, TextAlign.Center)
                }
            }
            // The scroll bar: ◀, the track with where you are, ▶.
            val barY = POCKET_H - 22f
            Box(Modifier.atScreen(4f, barY, 14f, 14f).pointerInput(Unit) { detectTapGestures { ribbonMove(live, -1) } }) {
                Canvas(Modifier.fillMaxSize()) { drawRoundRect(accent, cornerRadius = CornerRadius(uPx * 2f)); ribbonTri(Color.White, true, uPx) }
            }
            Box(Modifier.atScreen(width - 18f, barY, 14f, 14f).pointerInput(Unit) { detectTapGestures { ribbonMove(live, 1) } }) {
                Canvas(Modifier.fillMaxSize()) { drawRoundRect(accent, cornerRadius = CornerRadius(uPx * 2f)); ribbonTri(Color.White, false, uPx) }
            }
            val trackW = width - 44f
            Box(Modifier.atScreen(22f, barY, trackW, 14f).pointerInput(n) {
                detectTapGestures { o -> if (n > 0) ribbonSelect(live, ((o.x / size.width) * (n - 1)).roundToInt().coerceIn(0, n - 1)) }
            }) {
                Canvas(Modifier.fillMaxSize()) {
                    drawRoundRect(RFrame, cornerRadius = CornerRadius(uPx * 2f))
                    drawRoundRect(REdge, cornerRadius = CornerRadius(uPx * 2f), style = Stroke(uPx))
                    // Small marks along it, and the marker where you are.
                    val marks = 24
                    for (m in 1 until marks) drawRect(REdge.copy(alpha = 0.6f), Offset(size.width * m / marks, size.height * 0.4f), Size(uPx * 0.7f, size.height * 0.2f))
                    if (n > 0) {
                        val f = if (n == 1) 0f else (pos / (n - 1)).coerceIn(0f, 1f)
                        val mw = (size.width / n).coerceIn(uPx * 4f, uPx * 18f)
                        drawRoundRect(accent, Offset((size.width - mw) * f, uPx * 2f), Size(mw, size.height - uPx * 4f), CornerRadius(uPx * 1.5f))
                    }
                }
            }
            // Update 182: the same small tools as Pocket classic (Settings, Achievements, Statistics, Second screen,
            // Sleep), above the scroll bar, and the home strip under it all.
            val tools = pocketTools(c, ribbon = true)
            val spotNow = PocketNav.ribbonSpot
            tools.forEachIndexed { i, (id, icon) ->
                PocketToolButton(this, pocketToolX(i, tools.size), barY - 21f, icon, RInk, accent, spotNow == id, PocketNav.pressed == id) {
                    PocketNav.ribbonSpot = id; pocketShared(live, id)
                }
            }
            if (strip) PocketHomeStrip(this, c, POCKET_H, accent, font, RInk, RMuted, REdge, Color(0xFFF7F8FA), spotNow.takeIf { it >= 0 })
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.ribbonTri(color: Color, left: Boolean, u: Float) {
    val w = size.width; val h = size.height
    val p = Path().apply {
        if (left) { moveTo(w * 0.62f, h * 0.25f); lineTo(w * 0.32f, h * 0.5f); lineTo(w * 0.62f, h * 0.75f) }
        else { moveTo(w * 0.38f, h * 0.25f); lineTo(w * 0.68f, h * 0.5f); lineTo(w * 0.38f, h * 0.75f) }
        close()
    }
    drawPath(p, color)
}

// ---- What the ribbon does ----

private fun ribbonCount(c: StyleCtx) = if (PocketNav.ribbonGames) c.games.size else c.stops.size
private fun ribbonCur(c: StyleCtx) = if (PocketNav.ribbonGames) c.focus else PocketNav.ribbonCat

private fun ribbonSelect(c: StyleCtx, i: Int) {
    if (i == ribbonCur(c)) return
    PocketNav.ribbonSpot = -1 // (Update 182: back on the row)
    if (PocketNav.ribbonGames) c.actions.select(i) else PocketNav.ribbonCat = i
    PocketSounds.play?.invoke(UiSound.MOVE)
}

/** One step along the row; at an end, a small bounce (or round to the other end with Endless). */
internal fun ribbonMove(c: StyleCtx, d: Int) {
    val n = ribbonCount(c)
    if (n == 0) return
    val cur = ribbonCur(c)
    var next = cur + d
    if (next !in 0 until n) {
        if (PocketNav.ribbonGames && StyleLoop.on && n > 1) next = next.mod(n)
        else { PocketNav.bump = d; PocketNav.bumpAt = System.currentTimeMillis(); return }
    }
    ribbonSelect(c, next)
}

/** A (or a tap on the one you're on): a category opens its games; a game opens its game window. */
private fun ribbonOpen(c: StyleCtx) {
    if (!PocketNav.ribbonGames) {
        val steps = pocketStepsTo(c, PocketNav.ribbonCat)
        if (steps != 0) c.actions.category(steps)
        PocketSounds.play?.invoke(UiSound.OPEN)
        PocketNav.ribbonGames = true
        return
    }
    if (c.game != null) ribbonLaunching { c.actions.open() }
}

/** START (or the START tab): a game starts; a category opens. */
private fun ribbonStart(c: StyleCtx) {
    if (!PocketNav.ribbonGames) { ribbonOpen(c); return }
    val g = c.game ?: return
    ribbonLaunching { c.actions.play(g) }
}

/** The launch moment: the others fade, the one you're on grows a little, then [then] runs and the row comes back. */
private fun ribbonLaunching(then: () -> Unit) {
    val ms = pocketMs(180).toLong()
    if (ms == 0L) { then(); return }
    PocketNav.launching = true
    val h = android.os.Handler(android.os.Looper.getMainLooper())
    h.postDelayed({ then(); h.postDelayed({ PocketNav.launching = false }, 400L) }, ms)
}

private fun ribbonBack(c: StyleCtx): Boolean {
    if (!PocketNav.ribbonGames) return false
    PocketNav.ribbonGames = false
    PocketNav.ribbonCat = c.stop.coerceAtLeast(0)
    PocketSounds.play?.invoke(UiSound.BACK)
    return true
}

/**
 * Update 181: Pocket ribbon's keys: ◀ ▶ along the row, A opens (a category: its games; a game: its game window), START
 * starts the game, B back to the categories. ▲ / ▼: what lies above / below (▼: the second screen). True = handled.
 */
internal fun pocketRibbonKey(event: KeyEvent, reach: (Boolean) -> Boolean): Boolean {
    val c = PocketNav.ctx ?: return false
    val code = event.nativeKeyEvent.keyCode
    val down = event.type == KeyEventType.KeyDown
    val up = event.type == KeyEventType.KeyUp
    if (up && PocketNav.keysDown.remove(code)) return true
    if (!down) return false
    val first = event.nativeKeyEvent.repeatCount == 0
    fun take(): Boolean { PocketNav.keysDown += code; return true }
    // Update 182: below the row, the small tools and the home strip (PocketNav.ribbonSpot; −1 = the row).
    val spot = PocketNav.ribbonSpot
    val tools = pocketTools(c, ribbon = true)
    val rows = listOf(
        tools.mapIndexed { i, (id, _) -> id to pocketToolX(i, tools.size) + 9f },
        if (pocketStripOn()) pocketStripSpots(c) else emptyList()
    ).filter { it.isNotEmpty() }
    fun go(s: Int): Boolean { if (s != spot) { PocketNav.ribbonSpot = s; PocketSounds.play?.invoke(UiSound.MOVE) }; return take() }
    if (spot >= 0) return when (event.key) {
        Key.DirectionLeft -> pocketRowStep(rows, spot, 0)?.let { go(it) } ?: take()
        Key.DirectionRight -> pocketRowStep(rows, spot, 1)?.let { go(it) } ?: take()
        Key.DirectionUp -> pocketRowStep(rows, spot, 2)?.let { go(it) } ?: go(-1)
        Key.DirectionDown -> pocketRowStep(rows, spot, 3)?.let { go(it) } ?: run { if (first) reach(false); take() }
        Key.ButtonA, Key.Enter, Key.DirectionCenter -> { if (first) pocketShared(c, spot); take() }
        Key.ButtonStart -> { if (first) ribbonStart(c); take() }
        Key.ButtonB, Key.Back -> { if (first) { PocketNav.ribbonSpot = -1; PocketSounds.play?.invoke(UiSound.BACK) }; take() }
        else -> false
    }
    return when (event.key) {
        Key.DirectionLeft -> { ribbonMove(c, -1); take() }
        Key.DirectionRight -> { ribbonMove(c, 1); take() }
        Key.DirectionUp -> { if (first) reach(true); take() }
        Key.DirectionDown -> {
            // The tool nearest the middle first; without any, what lies below.
            val first0 = rows.firstOrNull()?.minByOrNull { kotlin.math.abs(it.second - 128f) }?.first
            if (first0 != null) go(first0) else { if (first) reach(false); take() }
        }
        Key.ButtonA, Key.Enter, Key.DirectionCenter -> { if (first) ribbonOpen(c); take() }
        Key.ButtonStart -> { if (first) ribbonStart(c); take() }
        Key.ButtonB, Key.Back -> if (first && ribbonBack(c)) take() else false
        else -> false
    }
}
