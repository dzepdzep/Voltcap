package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import kotlin.math.sin

/*
 * Update 330: Storage boxes — each category a storage box of small game icons, in Voltcap's own drawing.
 *
 * A box holds 30 icons (6 × 5; Roomy: 20, 5 × 4) on its own wallpaper (dots, stripes, waves, stars… — one per box, or the
 * same for all, or plain); a category with more games fills more boxes ("PlayStation 2 · Box 3"). The game you're on hops
 * a little under a bobbing pointer and shows big in the preview panel beside the box: its cover, name, console, time
 * played and achievements. A lifts it toward the pointer and opens the game's window; the box slides to the next one
 * when you go past its edge.
 * Icons: small pixel sprites (StorageSprite) — the game's achievement icon where it has one, else its cover's middle,
 * brought down to 32 pixels with an outline — each standing in its slot of the box; the one you're on hops in two steps.
 * A picks it up (it rises to the pointer) and its record opens out of it (WinScenes' StorageRecord).
 * D-pad: ◀ ▶ ▲ ▼ through the box; past its left / right edge the box before / after (same row); L2 / R2 the next category.
 * Touch: tap an icon to pick it, again to open it; swipe across the box to change box.
 */

/** Update 330: the box's grid as drawn (read by its key handler). */
internal object StorageNav {
    @Volatile var cols: Int = 6
    @Volatile var rows: Int = 5
}

/** Update 330: Storage boxes' D-pad: through the box, past its sides to the next / previous box. */
internal fun storageBoxesKey(ctx: StyleKeyCtx): Boolean {
    val cols = StorageNav.cols.coerceAtLeast(1)
    val per = (cols * StorageNav.rows).coerceAtLeast(1)
    val n = ctx.count
    if (n == 0) return false
    val f = ctx.focus
    val page = f / per
    val pos = f % per
    val r = pos / cols
    val col = pos % cols
    val pages = (n + per - 1) / per
    val loop = StyleLoop.on && pages > 1
    val target: Int? = when (ctx.key) {
        Key.DirectionLeft -> when {
            col > 0 -> f - 1
            page > 0 -> ((page - 1) * per + r * cols + cols - 1).coerceAtMost(n - 1)
            loop -> ((pages - 1) * per + r * cols + cols - 1).coerceAtMost(n - 1)
            else -> null
        }
        Key.DirectionRight -> when {
            col < cols - 1 && f + 1 < n -> f + 1
            page < pages - 1 -> ((page + 1) * per + r * cols).coerceAtMost(n - 1)
            loop -> (r * cols).coerceAtMost(n - 1)
            else -> null
        }
        Key.DirectionUp -> if (r > 0) f - cols else return ctx.reach(true)
        Key.DirectionDown -> when {
            r < StorageNav.rows - 1 && f + cols < n -> f + cols
            r < StorageNav.rows - 1 && page * per + (r + 1) * cols < n -> n - 1
            else -> return ctx.reach(false)
        }
        else -> return false
    }
    if (target != null && target != f) { ctx.setFocus(target); ctx.sound(UiSound.MOVE) }
    else if (ctx.first) EdgeBump.hit(if (ctx.key == Key.DirectionLeft) -1 else 1)
    return ctx.take()
}

/** Update 330: the room behind the boxes. */
@Composable
internal fun StorageBoxesBackdrop(modifier: Modifier) {
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF2E5E8C), Color(0xFF1C3A5C), Color(0xFF10233A))))
        // A soft grid of light, like a terminal's screen.
        val step = 28.dp.toPx()
        var y = 0f
        while (y < size.height) { drawLine(Color.White.copy(alpha = 0.035f), Offset(0f, y), Offset(size.width, y), 1f); y += step }
        var x = 0f
        while (x < size.width) { drawLine(Color.White.copy(alpha = 0.035f), Offset(x, 0f), Offset(x, size.height), 1f); x += step }
    }
}

/** Update 333: the box on screen's wallpaper colour (the status bar wears it). */
internal object StorageWallTint {
    var color by androidx.compose.runtime.mutableStateOf<Color?>(null)
}

/** The wallpapers: original patterns, each with its own colours. */
private val StWalls = listOf(
    Color(0xFF7BC67B) to Color(0xFF5FA85F), // meadow dots
    Color(0xFF6FB6E8) to Color(0xFF4F95C8), // waves
    Color(0xFFF2B866) to Color(0xFFDD9A45), // stripes
    Color(0xFFB79CE6) to Color(0xFF977BC8), // stars
    Color(0xFFE88B8B) to Color(0xFFC86A6A), // checks
    Color(0xFF8FD8C6) to Color(0xFF6AB9A6), // diamonds
    Color(0xFFE6D57A) to Color(0xFFC7B55A), // zigzag
    Color(0xFF9AA6B8) to Color(0xFF7A8698)  // plain-ish grid
)

private fun DrawScope.stWallpaper(kind: Int, plain: Boolean) {
    val (a, b) = StWalls[kind.mod(StWalls.size)]
    if (plain) { drawRect(Color(0xFFD9DEE6)); return }
    drawRect(Brush.verticalGradient(listOf(a, b)))
    val u = 22.dp.toPx()
    val ink = Color.White.copy(alpha = 0.22f)
    when (kind.mod(StWalls.size)) {
        0 -> { var y = u / 2; var row = 0; while (y < size.height) { var x = if (row % 2 == 0) u / 2 else u; while (x < size.width) { drawCircle(ink, u * 0.12f, Offset(x, y)); x += u }; y += u * 0.8f; row++ } }
        1 -> { var y = 0f; while (y < size.height + u) { val p = Path().apply { moveTo(0f, y); var x = 0f; while (x < size.width) { quadraticBezierTo(x + u / 2, y - u * 0.3f, x + u, y); quadraticBezierTo(x + u * 1.5f, y + u * 0.3f, x + u * 2, y); x += u * 2 } }; drawPath(p, ink, style = Stroke(u * 0.12f)); y += u } }
        2 -> { var x = -size.height; while (x < size.width) { drawLine(ink, Offset(x, size.height), Offset(x + size.height, 0f), u * 0.3f); x += u * 1.2f } }
        3 -> { var y = u / 2; var i = 0; while (y < size.height) { var x = u / 2 + (i % 2) * u; while (x < size.width) { star(Offset(x, y), u * 0.22f, ink); x += u * 2 }; y += u; i++ } }
        4 -> { var y = 0f; var r = 0; while (y < size.height) { var x = (r % 2) * u; while (x < size.width) { drawRect(ink, Offset(x, y), Size(u, u)); x += u * 2 }; y += u; r++ } }
        5 -> { var y = 0f; while (y < size.height + u) { var x = 0f; while (x < size.width + u) { val p = Path().apply { moveTo(x, y - u * 0.3f); lineTo(x + u * 0.3f, y); lineTo(x, y + u * 0.3f); lineTo(x - u * 0.3f, y); close() }; drawPath(p, ink); x += u }; y += u } }
        6 -> { var y = u / 2; while (y < size.height) { val p = Path().apply { moveTo(0f, y); var x = 0f; var up = true; while (x < size.width) { x += u / 2; lineTo(x, if (up) y - u * 0.25f else y + u * 0.25f); up = !up } }; drawPath(p, ink, style = Stroke(u * 0.1f)); y += u } }
        else -> { var y = 0f; while (y < size.height) { drawLine(ink, Offset(0f, y), Offset(size.width, y), 1.5f); y += u }; var x = 0f; while (x < size.width) { drawLine(ink, Offset(x, 0f), Offset(x, size.height), 1.5f); x += u } }
    }
}

private fun DrawScope.star(c: Offset, r: Float, color: Color) {
    val p = Path()
    for (i in 0 until 10) {
        val a = Math.PI / 5 * i - Math.PI / 2
        val rr = if (i % 2 == 0) r else r * 0.45f
        val x = c.x + (rr * kotlin.math.cos(a)).toFloat(); val y = c.y + (rr * kotlin.math.sin(a)).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    drawPath(p, color)
}

@Composable
internal fun StyleStorageBoxes(c: StyleCtx, modifier: Modifier = Modifier) {
    val loop = loopRowOn(c)
    SideEffect {
        StyleNav.side = emptyList()
        StyleNav.top = emptyList()
        StyleNav.bar = emptyList()
        StyleLoop.on = loop
    }
    if (c.games.isEmpty()) StyleEmpty(modifier) else StorageScreen(c, modifier)
}

@Composable
private fun StorageScreen(c: StyleCtx, modifier: Modifier) {
    val box = Features.stSize.state.value
    SideEffect { StorageNav.cols = box.cols; StorageNav.rows = box.rows }
    val per = box.cols * box.rows
    val count = c.games.size
    val focus = c.focus.coerceIn(0, count - 1)
    val page = focus / per
    val pages = (count + per - 1) / per
    val previewLeft = Features.stPreview.state.value == Features.StPreview.LEFT
    val motion = Features.stMotion.state.value
    val animate = rememberAnimationsEnabled() && motion != Features.StMotion.OFF
    val select by rememberUpdatedState(c.actions.select)
    val latestFocus by rememberUpdatedState(c.focus)
    val latestCount by rememberUpdatedState(count)
    val caption = CategoryTitle.captionOn(c.state.appearance)
    if (caption) CategoryTitle.Native(null)
    Row(modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (previewLeft) StoragePreview(c, Modifier.weight(0.3f).fillMaxHeight())
        Column(Modifier.weight(0.7f).fillMaxHeight()) {
            // The box's name between its arrows.
            Row(
                Modifier.fillMaxWidth().height(44.dp).pixelFrame(Color(0xFFF2F5FA), 8.dp).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("◀", color = Color(0xFF4C6A8E), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    (if (caption) c.categoryLabel else "Box") + (if (pages > 1) "  ·  Box ${page + 1} / $pages" else ""),
                    color = Color(0xFF233248), fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
                )
                Text("▶", color = Color(0xFF4C6A8E), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            // The box: its wallpaper and its slots; the next box slides in from the side you went.
            val wallKind = when (Features.stWallpaper.state.value) {
                Features.StWallpaper.PER_BOX -> (c.stop.coerceAtLeast(0) * 3 + page)
                else -> 1
            }
            val plain = Features.stWallpaper.state.value == Features.StWallpaper.PLAIN
            // (Update 333: the status bar takes the box's wallpaper colours — StatusLooks.)
            val wallTint = if (plain) Color(0xFF9AA6B8) else StWalls[wallKind.mod(StWalls.size)].second
            androidx.compose.runtime.SideEffect { StorageWallTint.color = wallTint }
            AnimatedContent(
                targetState = Triple(c.categoryLabel, page, wallKind), label = "stBox",
                transitionSpec = {
                    if (!animate) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    else {
                        val dir = if (targetState.first != initialState.first) 0 else if (targetState.second >= initialState.second) 1 else -1
                        if (dir == 0) fadeIn(tween(260)) togetherWith fadeOut(tween(200))
                        else (slideInHorizontally(tween(320)) { it * dir } + fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(tween(320)) { -it * dir } + fadeOut(tween(200)))
                    }
                },
                modifier = Modifier.fillMaxWidth().weight(1f)
                    .pointerInput(per, count) {
                        var acc = 0f
                        detectHorizontalDragGestures(onDragStart = { acc = 0f }) { ch, dx ->
                            ch.consume(); acc += dx
                            val s = 120f
                            if (acc <= -s) { acc = 0f; val p = latestFocus / per + 1; if (p * per < latestCount) select(p * per) }
                            if (acc >= s) { acc = 0f; val p = latestFocus / per - 1; if (p >= 0) select(p * per) }
                        }
                    }
            ) { (_, pg, wk) ->
                Box(Modifier.fillMaxSize().pixelFrame(Color(0xFF9AA6B8), 10.dp)) {
                    Canvas(Modifier.fillMaxSize()) { stWallpaper(wk, plain) }
                    StorageGrid(c, pg, box, focus, Modifier.fillMaxSize().padding(10.dp), animate)
                }
            }
        }
        if (!previewLeft) StoragePreview(c, Modifier.weight(0.3f).fillMaxHeight())
    }
}

@Composable
private fun StorageGrid(c: StyleCtx, page: Int, box: Features.StSize, focus: Int, modifier: Modifier, animate: Boolean) {
    val per = box.cols * box.rows
    val count = c.games.size
    val spacing = Features.stSpacing.state.value / 100f
    val cursorOn = Features.stCursor.state.value
    val reduced = Features.stMotion.state.value == Features.StMotion.REDUCED
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    val latestFocus by rememberUpdatedState(c.focus)
    val windowOn = GamePopup.game != null
    val hopClock = if (animate) rememberGcClock(rememberIsActive() && !HomeIdle.calm, 1f, 12) else null
    val bob = if (animate && !reduced && cursorOn) rememberInfiniteTransition(label = "stBob").animateFloat(
        0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "stBobA"
    ) else null
    BoxWithConstraints(modifier) {
        val cellW = maxWidth / box.cols
        val cellH = maxHeight / box.rows
        val icon = (minOf(cellW, cellH) * (0.78f / spacing.coerceIn(0.6f, 1.5f)).coerceIn(0.45f, 0.92f))
        for (r in 0 until box.rows) for (col in 0 until box.cols) {
            val i = page * per + r * box.cols + col
            if (i >= count) continue
            val g = c.games[i]
            val on = i == focus
            key(g.stableKey) {
            val lift by animateFloatAsState(if (on) 1f else 0f, if (animate) spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow) else tween(0), label = "stLift")
            val pick by animateFloatAsState(if (on && windowOn) 1f else 0f, if (animate) spring(dampingRatio = 0.7f) else tween(0), label = "stPick")
            Box(
                Modifier.size(cellW, cellH).graphicsLayer {
                    translationX = col * cellW.toPx(); translationY = r * cellH.toPx()
                }.zIndex(if (on) 2f else 0f)
                    .pointerInput(i) { detectTapGestures { if (latestFocus == i) open() else select(i) } },
                contentAlignment = Alignment.Center
            ) {
                // Its slot in the box: a soft square sunk into the wallpaper.
                Box(Modifier.size(icon * 1.08f).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = if (on) 0.34f else 0.18f))
                    .border(1.5.dp, Color.White.copy(alpha = 0.32f), RoundedCornerShape(10.dp)))
                // The sprite's shadow on the floor of its slot.
                Box(Modifier.size(icon * 0.62f, icon * 0.13f).graphicsLayer { translationY = icon.toPx() * 0.42f; alpha = 0.32f - 0.14f * lift - 0.1f * pick; val k = 1f - 0.25f * pick; scaleX = k }
                    .clip(RoundedCornerShape(50)).background(Color.Black))
                Box(
                    Modifier.size(icon * 0.86f).graphicsLayer {
                        // The one you're on hops in two steps, as sprites do; picked up, it rises toward the hand.
                        val hop = if (on && hopClock != null && !reduced) (((hopClock.floatValue * 2.4f).toInt() % 2) * 3.dp.toPx()) else 0f
                        translationY = -(lift * 3.dp.toPx() + hop + pick * 16.dp.toPx())
                        val s = 1f + 0.14f * lift + 0.12f * pick
                        scaleX = s; scaleY = s
                    }.gameOrigin(g.stableKey, on)
                ) { StorageSprite(g, c.state, c.cover(g), Modifier.fillMaxSize()) }
            }
            }
        }
        // The pointer, bobbing over the icon you're on (and sliding to the next).
        val pos = focus - page * per
        if (cursorOn && pos in 0 until per && focus < count) {
            val tx by animateFloatAsState((pos % box.cols).toFloat(), if (animate) spring(stiffness = 600f) else tween(0), label = "stCurX")
            val ty by animateFloatAsState((pos / box.cols).toFloat(), if (animate) spring(stiffness = 600f) else tween(0), label = "stCurY")
            val grab by animateFloatAsState(if (windowOn) 1f else 0f, if (animate) spring(dampingRatio = 0.6f) else tween(0), label = "stGrab")
            Canvas(
                Modifier.size(22.dp, 22.dp).zIndex(5f).graphicsLayer {
                    translationX = (tx + 0.5f) * cellW.toPx() - 11.dp.toPx()
                    // (Picked up: the pointer comes down onto it, as the hand takes it.)
                    translationY = ty * cellH.toPx() + (cellH.toPx() - icon.toPx()) / 2f - 20.dp.toPx() - (bob?.value ?: 0f) * 5.dp.toPx() * (1f - grab) - grab * 6.dp.toPx()
                }
            ) {
                val p = Path().apply { moveTo(size.width * 0.15f, size.height * 0.05f); lineTo(size.width * 0.85f, size.height * 0.05f); lineTo(size.width * 0.5f, size.height * 0.95f); close() }
                drawPath(p, Color(0xFFFF5A4E))
                drawPath(p, Color.White, style = Stroke(2.dp.toPx()))
            }
        }
    }
}

/**
 * Update 330 (Toni: "sprite-like icons rather than tiny cover cards"): a game as a small pixel sprite — its achievement
 * icon (a square picture made for this size), else its cover's middle, brought down to 32 pixels, its colours stepped,
 * its corners cut in steps and a dark outline round it, drawn big with hard pixel edges. An app: its own icon.
 */
@Composable
internal fun StorageSprite(g: Game, state: com.shelf.launcher.LauncherUiState, cover: String?, modifier: Modifier) {
    val ra = g.raGameId?.let { state.raIcons[it] }
    val model: Any? = ra ?: cover
    val src = rememberSoftBitmap(model, 96, trim = ra == null)
    val sprite = remember(src) { src?.let { runCatching { spriteOf(it, 32) }.getOrNull() } }
    Box(modifier, contentAlignment = Alignment.Center) {
        when {
            sprite != null -> androidx.compose.foundation.Image(
                sprite, g.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                filterQuality = androidx.compose.ui.graphics.FilterQuality.None
            )
            // (Update 342, Toni: "never the coloured icons, their real logos": the app's own icon in its own colours, whatever
            // the icon style — the one-colour symbol on a grey card looked like a stand-in.)
            g.sourceType == com.shelf.launcher.data.SourceType.APP -> rememberOwnAppIcon(g.packageName)?.let { icon ->
                androidx.compose.foundation.Image(icon, g.name, Modifier.fillMaxSize(0.82f).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Fit)
            } ?: Box(Modifier.fillMaxSize(0.7f).clip(RoundedCornerShape(10.dp)).background(placeholderBrush(g.hue)))
            model == null -> Box(Modifier.fillMaxSize(0.8f).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.85f))) { StyleNamePlate(LocalTitleOptions.current.display(g.name)) }
            else -> Box(Modifier.fillMaxSize(0.7f).clip(RoundedCornerShape(4.dp)).background(placeholderBrush(g.hue)))
        }
    }
}

/** [src] made into a [px] × [px] sprite (plus its one-pixel outline): see [StorageSprite]. */
private fun spriteOf(src: android.graphics.Bitmap, px: Int): androidx.compose.ui.graphics.ImageBitmap {
    val side = minOf(src.width, src.height)
    val sq = android.graphics.Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
    val small = android.graphics.Bitmap.createScaledBitmap(sq, px, px, true)
    val inPx = IntArray(px * px)
    small.getPixels(inPx, 0, px, 0, 0, px, px)
    val n = px + 2
    val out = IntArray(n * n)
    fun step(v: Int): Int = ((v * 7 + 127) / 255) * 255 / 7 // eight levels a channel
    for (y in 0 until px) for (x in 0 until px) {
        // The corners cut in steps of a pixel.
        val cx = minOf(x, px - 1 - x); val cy = minOf(y, px - 1 - y)
        if (cx + cy < 3) continue
        val c = inPx[y * px + x]
        val r = step((c shr 16) and 0xFF); val gg = step((c shr 8) and 0xFF); val b = step(c and 0xFF)
        out[(y + 1) * n + (x + 1)] = (0xFF shl 24) or (r shl 16) or (gg shl 8) or b
    }
    // The outline: every empty pixel touching the picture.
    val edge = 0xFF1A2230.toInt()
    val copy = out.copyOf()
    for (y in 0 until n) for (x in 0 until n) {
        if (copy[y * n + x] != 0) continue
        val touch = (x > 0 && copy[y * n + x - 1] != 0) || (x < n - 1 && copy[y * n + x + 1] != 0) ||
            (y > 0 && copy[(y - 1) * n + x] != 0) || (y < n - 1 && copy[(y + 1) * n + x] != 0)
        if (touch) out[y * n + x] = edge
    }
    val bmp = android.graphics.Bitmap.createBitmap(n, n, android.graphics.Bitmap.Config.ARGB_8888)
    bmp.setPixels(out, 0, n, 0, 0, n, n)
    return bmp.asImageBitmap()
}

/** The preview beside the box: the game you're on, big, with its details. */
@Composable
private fun StoragePreview(c: StyleCtx, modifier: Modifier) {
    val g = c.game ?: return
    val titles = LocalTitleOptions.current
    Column(
        modifier.pixelFrame(Color(0xFFF2F5FA), 10.dp).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.fillMaxWidth().weight(1f).pixelFrame(Color(0xFF8FB8DE), 6.dp)
                .background(Brush.verticalGradient(listOf(Color(0xFFCFE6FA), Color(0xFF8FB8DE)))),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(targetState = g, label = "stPreview", transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }) { shown ->
                val cover = c.cover(shown)
                val appIcon = if (cover == null && shown.sourceType == com.shelf.launcher.data.SourceType.APP) rememberOwnAppIcon(shown.packageName) else null
                if (cover != null) AsyncImage(rememberStyleArtRequest(cover, trim = true), shown.name, Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
                // (Update 342: an app without a cover: its own icon, big, in its own colours.)
                else if (appIcon != null) androidx.compose.foundation.Image(appIcon, shown.name, Modifier.fillMaxSize(0.55f).clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Fit)
                else StyleNamePlate(titles.display(shown.name))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(titles.display(g.name), color = Color(0xFF1E2A3A), fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 18.sp)
        Text(titles.console(g.platform), color = Color(0xFF4C5E76), fontSize = 12.sp, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Played", color = Color(0xFF4C5E76), fontSize = 12.sp)
            Text(stylePlaytime(c.playtime(g), c.lastPlayed(g) != null), color = Color(0xFF1E2A3A), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        val p = c.progress(g)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Achievements", color = Color(0xFF4C5E76), fontSize = 12.sp)
            Text(p?.let { (e, t) -> "$e / $t" } ?: "—", color = Color(0xFF1E2A3A), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        if (p != null) {
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFB9C6D6))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth((p.first.toFloat() / p.second).coerceIn(0f, 1f)).background(Color(0xFF3FAE5A)))
            }
        }
    }
}

/** Update 330: Settings › Home › Layout options (Storage boxes). Every one its own "st_…" Features pref. */
@Composable
internal fun StorageBoxesSettings() {
    MutedNote(
        "Each category a storage box of small game icons on its own wallpaper, the game you're on big in the preview beside it. " +
            "◀ ▶ ▲ ▼ through the box — past its sides to the box before / after — A opens the game, L2 / R2 the next category. " +
            "Tap an icon to pick it, again to open it; swipe across the box to change box."
    )
    Spacer(Modifier.height(10.dp))
    StyleChoice("Box size", Features.StSize.values(), Features.stSize.state.value, { it.label }) { Features.stSize.value = it }
    PercentSlider("Room round the icons", Features.stSpacing.state.value, { Features.stSpacing.value = it.coerceIn(60, 150) }, min = 60, max = 150, valueText = { "$it%" })
    StyleChoice("Wallpapers", Features.StWallpaper.values(), Features.stWallpaper.state.value, { it.label }) { Features.stWallpaper.value = it }
    StyleChoice("Preview", Features.StPreview.values(), Features.stPreview.state.value, { it.label }) { Features.stPreview.value = it }
    StyleChoice("Motion", Features.StMotion.values(), Features.stMotion.state.value, { it.label }) { Features.stMotion.value = it }
    Toggle(Icons.Filled.TouchApp, "Pointer", "A pointer bobbing over the icon you're on. Off: only its red frame.", Features.stCursor)
}
