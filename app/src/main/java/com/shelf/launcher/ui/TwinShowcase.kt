package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/*
 * Update 307: Twin showcase — a two-screen layout after the launcher in the video Toni described (TwinScreens.kt shares
 * the screens out, as for Twin icons, which stays as it was):
 * - top (main) screen: the game you're on as its big picture, edge to edge, its logo or name low on it. Picking another
 *   game moves the picture out and the new one in, in the direction the icons move (◀ ▶: sideways, ▲ ▼: up / down),
 *   sliding, scaling and fading together — never a plain swap;
 * - bottom (second) screen: square icons in 1, 2 or 3 rows (Layout options › Rows), the games running on to the right in
 *   columns — never more rows. The grid slides as one so the column you're on stays in the middle, and the frame of the
 *   one you're on glides to it, on the same timing as the top picture.
 * ◀ ▶ move a column, ▲ ▼ within the column (past the top / bottom row: the status bar / player, as elsewhere); A, or a tap
 * on the one you're on, opens it; a tap on another picks it. Both screens, or both halves stacked on one screen.
 */

/** One move's length and easing, the same on both screens so they move as one. */
private const val SHOW_MS = 380
private val ShowEase = CubicBezierEasing(0.22f, 0.9f, 0.24f, 1f)

private val ShowTop = Color(0xFF0D1118)
private val ShowTray = listOf(Color(0xFFF4F6F9), Color(0xFFE2E7ED))
private val ShowInk = Color(0xFF243040)
private val ShowMuted = Color(0xFF6B7684)
private val ShowEdge = Color(0xFFCBD3DC)

/** The grid's rows as last drawn (for the keys). */
internal object TwinShowNav {
    @Volatile var rows = 2
}

@Composable
internal fun StyleTwinShowcase(c: StyleCtx, modifier: Modifier = Modifier) {
    SideEffect {
        StyleLoop.on = false
        StyleNav.side = emptyList()
        StyleNav.top = emptyList()
        StyleNav.topStart = -1
        StyleNav.bar = emptyList()
    }
    TwinHost(
        c, modifier,
        top = { m, framed -> TwinShowcaseTop(c, m, framed) },
        bottom = { m, framed -> TwinShowcaseBottom(c, m, framed) },
        topH = 260.dp, bottomH = 280.dp
    )
}

/** The grid's keys: games run down each column, then on to the next column to the right. */
internal fun twinShowcaseKey(ctx: StyleKeyCtx): Boolean {
    val n = ctx.count
    if (n <= 0) {
        return when (ctx.key) {
            Key.DirectionUp -> ctx.reach(true)
            Key.DirectionDown -> ctx.reach(false)
            else -> ctx.take()
        }
    }
    val rows = TwinShowNav.rows.coerceIn(1, 3)
    val f = ctx.focus.coerceIn(0, n - 1)
    val row = f % rows
    fun go(target: Int): Boolean {
        if (target != f && target in 0 until n) {
            ctx.setFocus(target)
            ctx.sound(UiSound.MOVE)
        }
        return ctx.take()
    }
    return when (ctx.key) {
        Key.DirectionUp -> if (row > 0) go(f - 1) else ctx.reach(true)
        Key.DirectionDown -> if (row < rows - 1 && f + 1 < n) go(f + 1) else ctx.reach(false)
        Key.DirectionLeft -> if (f - rows >= 0) go(f - rows) else ctx.take()
        // (The last column may be shorter: its last game.)
        Key.DirectionRight -> when {
            f + rows < n -> go(f + rows)
            (f / rows + 1) * rows < n -> go(n - 1)
            else -> ctx.take()
        }
        else -> false
    }
}

@Composable
internal fun TwinShowcaseSettings() {
    MutedNote("Made for handhelds with two screens. The top screen shows the game you're on as its big picture; the bottom screen is a grid of square icons. More games carry on to the right — the rows never grow. Moving slides the grid and the picture together. With one screen, both halves show together.")
    Spacer(Modifier.height(10.dp))
    StyleChoice("Rows", arrayOf(1, 2, 3), Features.twinShowRows.state.value, { "$it" }) { Features.twinShowRows.value = it }
    StyleChoice("Gap between icons", TWIN_GAPS, Features.twinGap.state.value, { gapLabel(it) }) { Features.twinGap.value = it }
    TwinFullTopSwitch()
}

/** Twin showcase's dark backdrop across the whole top screen (behind the status bar too, filling the top screen). */
@Composable
internal fun TwinShowcaseBackdrop(modifier: Modifier) {
    Box(modifier.background(themeDark(ShowTop)))
}

// ---- Top screen: the game you're on ----

/** The picture to show and which way it came: [dx] columns, [dy] rows (−1, 0, 1). */
private class ShowPick(val game: Game?, val dx: Int, val dy: Int)

@Composable
private fun TwinShowcaseTop(c: StyleCtx, modifier: Modifier, framed: Boolean) {
    val shape = RoundedCornerShape(if (framed) 14.dp else 0.dp)
    val context = LocalContext.current
    val density = LocalDensity.current
    val rows = Features.twinShowRows.state.value.coerceIn(1, 3)
    val g = c.game
    // Which way the move went (the column and row it came from), for the picture's direction.
    val last = remember { intArrayOf(c.focus, -1) }
    val pick = remember(g?.stableKey, c.games.size) {
        val old = last[0]; val f = c.focus
        val sameList = last[1] == c.games.size
        last[0] = f; last[1] = c.games.size
        val dx = if (sameList) Integer.signum(f / rows - old / rows) else 0
        val dy = if (sameList && dx == 0) Integer.signum(f % rows - old % rows) else 0
        ShowPick(g, dx, dy)
    }
    BoxWithConstraints(
        modifier.clip(shape).background(themeDark(ShowTop))
            .then(if (framed) Modifier.border(1.dp, Color.White.copy(alpha = 0.12f), shape) else Modifier)
    ) {
        val wPx = with(density) { maxWidth.toPx() }.toInt().coerceIn(320, 1920)
        val hPx = with(density) { maxHeight.toPx() }.toInt().coerceIn(240, 1080)
        // The pictures round the one you're on are decoded ahead, so a move shows the next one at once.
        LaunchedEffect(c.focus, c.games.size, rows, wPx) {
            kotlinx.coroutines.delay(120)
            val near = listOf(-rows, rows, -1, 1).mapNotNull { d -> c.games.getOrNull(c.focus + d) }
            prefetchHeroes(context, near.mapNotNull { ng -> showPicture(c, ng)?.let { it to ng.stableKey } }, wPx, hPx)
        }
        // The move starts once the new picture is ready (at once when it was decoded ahead; at most a moment's wait),
        // so it never slides in empty.
        var shown by remember { mutableStateOf(pick) }
        LaunchedEffect(pick) {
            val pg = pick.game
            val url = pg?.let { showPicture(c, it) }
            if (pg != null && url != null) kotlinx.coroutines.withTimeoutOrNull(220) {
                coil.Coil.imageLoader(context).execute(heroRequest(context, url, wPx, hPx, pg.stableKey))
            }
            shown = pick
        }
        AnimatedContent(
            targetState = shown,
            contentKey = { it.game?.stableKey },
            transitionSpec = {
                val p = targetState
                val sideways = p.dx != 0 || p.dy == 0
                val s = if (sideways) (if (p.dx < 0) -1 else 1) else (if (p.dy < 0) -1 else 1)
                val move = tween<IntOffset>(SHOW_MS, easing = ShowEase)
                val enter = (if (sideways) slideInHorizontally(move) { w -> s * w / 3 } else slideInVertically(move) { h -> s * h / 3 }) +
                    scaleIn(tween(SHOW_MS, easing = ShowEase), initialScale = 1.08f) +
                    fadeIn(tween(SHOW_MS * 3 / 4, easing = ShowEase))
                val exit = (if (sideways) slideOutHorizontally(move) { w -> -s * w / 3 } else slideOutVertically(move) { h -> -s * h / 3 }) +
                    scaleOut(tween(SHOW_MS, easing = ShowEase), targetScale = 0.92f) +
                    fadeOut(tween(SHOW_MS * 2 / 3, easing = ShowEase))
                (enter togetherWith exit).using(SizeTransform(clip = true))
            },
            label = "twinShowcase",
            modifier = Modifier.fillMaxSize()
        ) { p ->
            ShowPicture(c, p.game, wPx, hPx, framed)
        }
    }
}

/** The big picture for [g]: its hero / background art, else its wide art, else its cover. */
private fun showPicture(c: StyleCtx, g: Game): String? = c.backdrop(g) ?: c.wide(g) ?: c.cover(g)

@Composable
private fun ShowPicture(c: StyleCtx, g: Game?, wPx: Int, hPx: Int, framed: Boolean) {
    val context = LocalContext.current
    val titles = LocalTitleOptions.current
    val open by rememberUpdatedState(c.actions.open)
    Box(Modifier.fillMaxSize().tapOnly { if (c.game != null) open() }) {
        if (g == null) {
            Text(EmptyShelf.text, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.align(Alignment.Center))
            return@Box
        }
        val url = showPicture(c, g)
        if (url != null) {
            val req = remember(url, wPx, hPx) { heroRequest(context, url, wPx, hPx, g.stableKey) }
            AsyncImage(req, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().pixelArtwork())
        } else {
            StyleArt(c, g, Modifier.fillMaxSize(), wide = true, corner = 0.dp)
        }
        // A soft shade low down for the name.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Transparent, 0.58f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.72f))
            )
        )
        Column(
            Modifier.align(Alignment.BottomStart).padding(horizontal = if (framed) 12.dp else 22.dp, vertical = if (framed) 10.dp else 18.dp)
                .fillMaxWidth(0.7f)
        ) {
            Text(
                titles.display(g.name), color = Color.White, fontSize = if (framed) 16.sp else 24.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(ShelfTheme.accent))
                Spacer(Modifier.width(7.dp))
                Text(
                    titles.console(g.platform), color = Color.White.copy(alpha = 0.85f), fontSize = if (framed) 11.sp else 13.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1
                )
            }
        }
    }
}

// ---- Bottom screen: the icons ----

@Composable
internal fun TwinShowcaseBottom(c: StyleCtx, modifier: Modifier, framed: Boolean) {
    val shape = RoundedCornerShape(if (framed) 14.dp else 0.dp)
    val n = c.games.size
    val rows = Features.twinShowRows.state.value.coerceIn(1, 3)
    SideEffect { TwinShowNav.rows = rows }
    val keys = remember(c.games) { gameKeys(c.games) }
    val titles = LocalTitleOptions.current
    BoxWithConstraints(
        modifier.clip(shape).background(Brush.verticalGradient(ShowTray))
            .then(if (framed) Modifier.border(1.dp, ShowEdge, shape) else Modifier)
    ) {
        val compact = maxHeight < 260.dp
        val headH = if (compact) 22.dp else 30.dp
        Column(Modifier.fillMaxSize()) {
            // The category and where you are in it.
            Row(
                Modifier.fillMaxWidth().height(headH).padding(horizontal = if (compact) 10.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    c.categoryLabel, color = ShowInk, fontSize = if (compact) 11.sp else 13.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                if (n > 0) Text("${c.focus.coerceIn(0, n - 1) + 1} / $n", color = ShowMuted, fontSize = if (compact) 10.sp else 12.sp, maxLines = 1)
            }
            if (n == 0) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(EmptyShelf.text, color = ShowMuted, fontSize = 13.sp)
                }
            } else {
                ShowGrid(c, keys, rows, Modifier.fillMaxWidth().weight(1f))
            }
            // The game you're on, under the grid.
            val g = c.game
            Box(Modifier.fillMaxWidth().height(headH), contentAlignment = Alignment.Center) {
                if (g != null) androidx.compose.animation.Crossfade(g.stableKey, animationSpec = tween(SHOW_MS / 2), label = "showName") { _ ->
                    Text(
                        titles.display(g.name), color = ShowInk, fontSize = if (compact) 11.sp else 13.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}

/**
 * The icons in [rows] rows, running on to the right; only the columns in view (and one more each side) are drawn. The
 * whole grid slides (one animated place, read where the icons are placed — nothing is composed again while it moves)
 * and the frame glides on the same timing.
 */
@Composable
private fun ShowGrid(c: StyleCtx, keys: List<String>, rows: Int, modifier: Modifier) {
    val n = c.games.size
    val density = LocalDensity.current
    val accent = ShelfTheme.accent
    BoxWithConstraints(modifier.clipToBounds()) {
        val gap = Features.twinGap.state.value.dp.coerceAtLeast(4.dp)
        val padH = 16.dp
        val padV = 8.dp
        val availH = maxHeight - padV * 2
        val availW = maxWidth - padH * 2
        // Square icons as tall as the rows allow, but never so big that fewer than about two and a half columns show.
        val cell = minOf((availH - gap * (rows - 1)) / rows, (availW - gap * 2) / 2.5f).coerceAtLeast(24.dp)
        val pitch = cell + gap
        val visible = (availW + gap) / pitch
        val cols = (n + rows - 1) / rows
        val focus = c.focus.coerceIn(0, (n - 1).coerceAtLeast(0))
        val fCol = focus / rows
        val fRow = focus % rows
        // The column you're on in the middle; at the ends the grid stops at its edge; fewer games than fit: centred.
        val target = if (cols <= visible) -(visible - cols) / 2f
            else (fCol - (visible - 1f) / 2f).coerceIn(0f, cols - visible)
        val scroll = remember { Animatable(target) }
        val frame = remember { Animatable(Offset(fCol.toFloat(), fRow.toFloat()), Offset.VectorConverter) }
        // (A new category or another number of rows: straight there, no slide across the whole list.)
        val seen = remember { intArrayOf(n, rows) }
        LaunchedEffect(target, fCol, fRow, n, rows) {
            val fresh = seen[0] != n || seen[1] != rows
            seen[0] = n; seen[1] = rows
            if (fresh) {
                scroll.snapTo(target); frame.snapTo(Offset(fCol.toFloat(), fRow.toFloat()))
            } else {
                kotlinx.coroutines.coroutineScope {
                    launch { scroll.animateTo(target, tween(SHOW_MS, easing = ShowEase)) }
                    launch { frame.animateTo(Offset(fCol.toFloat(), fRow.toFloat()), tween(SHOW_MS, easing = ShowEase)) }
                }
            }
        }
        val pitchPx = with(density) { pitch.toPx() }
        val cellPx = with(density) { cell.toPx() }
        val x0Px = with(density) { padH.toPx() }
        val gridH = cell * rows + gap * (rows - 1)
        val y0Px = with(density) { (padV + (availH - gridH) / 2).toPx() }
        // Which columns are in view, worked out from where the slide is heading and where it is (both in view).
        val firstCol = (minOf(scroll.targetValue, scroll.value) - 1f).toInt().coerceAtLeast(0)
        val lastCol = (maxOf(scroll.targetValue, scroll.value) + visible + 1f).toInt().coerceAtMost(cols - 1)
        for (col in firstCol..lastCol) {
            for (row in 0 until rows) {
                val i = col * rows + row
                if (i >= n) continue
                key(keys[i]) {
                    ShowIcon(
                        c, i, on = i == focus,
                        modifier = Modifier.size(cell).offset {
                            IntOffset((x0Px + (col - scroll.value) * pitchPx).roundToInt(), (y0Px + row * pitchPx).roundToInt())
                        }
                    ) {
                        if (i == focus) c.actions.open() else c.actions.select(i)
                    }
                }
            }
        }
        // The frame round the one you're on, gliding to it (drawn over the icons, read at draw time only).
        Box(
            Modifier.fillMaxSize().drawWithContent {
                drawContent()
                if (n == 0) return@drawWithContent
                val o = 4.dp.toPx()
                val w = 3.dp.toPx()
                val x = x0Px + (frame.value.x - scroll.value) * pitchPx - o
                val y = y0Px + frame.value.y * pitchPx - o
                val sz = Size(cellPx + o * 2, cellPx + o * 2)
                val r = CornerRadius(cellPx * 0.2f)
                drawRoundRect(accent.copy(alpha = 0.22f), Offset(x - w, y - w), Size(sz.width + w * 2, sz.height + w * 2), CornerRadius(r.x + w), style = Stroke(w * 2))
                drawRoundRect(accent, Offset(x, y), sz, r, style = Stroke(w))
                drawRoundRect(Color.White, Offset(x + w, y + w), Size(sz.width - w * 2, sz.height - w * 2), CornerRadius(r.x - w), style = Stroke(1.dp.toPx()))
            }
        )
    }
}

/** One square icon; the one you're on a touch bigger (eased in and out). */
@Composable
private fun ShowIcon(c: StyleCtx, i: Int, on: Boolean, modifier: Modifier, onTap: () -> Unit) {
    val g = c.games[i]
    val lift by animateFloatAsState(if (on) 1f else 0f, tween(SHOW_MS, easing = ShowEase), label = "showLift")
    val shape = RoundedCornerShape(18)
    Box(
        modifier
            .graphicsLayer { val k = 1f + 0.05f * lift; scaleX = k; scaleY = k; alpha = 0.86f + 0.14f * lift }
            .clip(shape)
            .background(Color.White)
            .border(1.dp, ShowEdge, shape)
            .tapOnly(onTap)
            .padding(2.dp)
    ) { StyleArt(c, g, Modifier.fillMaxSize(), square = true, corner = 7.dp) }
}
