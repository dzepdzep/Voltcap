package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Texture
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import kotlin.math.sin

/*
 * Update 330: File select (1996) — your games as chunky 3D save-file blocks, in Voltcap's own drawing.
 *
 * A page of four (2 × 2; or six, or eight) bevelled blocks on a deep blue screen with a slowly drifting diamond pattern,
 * "SELECT FILE" over them and the category as the page's name. Each block is a file: its console's name, the game's cover in a
 * little window, its name, a star with the achievements earned, and time played. The block you're on bounces and rocks
 * gently, lit; A opens the game's window, which grows out of the block. Past the page's edge the next page slides in.
 * Colours: bright (a different one per file), the game's own (from its cover), or soft. Optional console-era blips.
 * D-pad: ◀ ▶ ▲ ▼ through the page (past its sides the page before / after), L2 / R2 the next category.
 * Touch: tap a block to pick it, again to open it; swipe to turn the page.
 */

/** Update 330: the page's grid as drawn (read by its key handler). */
internal object FileSelectNav {
    @Volatile var cols: Int = 2
    @Volatile var rows: Int = 2
    /** The colour of the file you're on (its window — WinScenes' FileCardBack — is its back, in the same colour). */
    @Volatile var openColor: Long = 0xFF2F7BE0
    /** Counts the moves (the optional blip plays on each). */
    val moves = androidx.compose.runtime.mutableIntStateOf(0)
}

/** Update 330: File select's D-pad: through the page, past its sides to the page before / after (same row). */
internal fun fileSelectKey(ctx: StyleKeyCtx): Boolean {
    val cols = FileSelectNav.cols.coerceAtLeast(1)
    val rows = FileSelectNav.rows.coerceAtLeast(1)
    val per = cols * rows
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
            r < rows - 1 && f + cols < n -> f + cols
            r < rows - 1 && page * per + (r + 1) * cols < n -> n - 1
            else -> return ctx.reach(false)
        }
        else -> return false
    }
    if (target != null && target != f) {
        ctx.setFocus(target); ctx.sound(UiSound.MOVE)
        FileSelectNav.moves.intValue++
    } else if (ctx.first) EdgeBump.hit(if (ctx.key == Key.DirectionLeft) -1 else 1)
    return ctx.take()
}

private val FsBright = listOf(Color(0xFFE5483C), Color(0xFF2F7BE0), Color(0xFF3DB24E), Color(0xFFF0B429), Color(0xFF9B59D0), Color(0xFF1FB3B8), Color(0xFFE7739C), Color(0xFFEE7F2D))
private val FsSoft = listOf(Color(0xFFD99A93), Color(0xFF8FB2D9), Color(0xFF94C79C), Color(0xFFE3CC8C), Color(0xFFB9A2D3), Color(0xFF8CCBCC), Color(0xFFE2A9BC), Color(0xFFE0B08A))
private val FsShadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(2f, 2f), 2f)

/** Update 330: the deep blue screen with its slowly drifting diamond pattern (Layout options › Pattern). */
@Composable
internal fun FileSelectBackdrop(modifier: Modifier) {
    val pattern = Features.fsPattern.state.value
    val animate = rememberAnimationsEnabled() && pattern && !LocalLiteEffects.current && rememberIsActive() && !HomeIdle.calm
    val clock = rememberGcClock(animate, 1f, 30)
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF1B2A7A), Color(0xFF101A52), Color(0xFF070B2A))))
        if (!pattern) return@Canvas
        val u = 46.dp.toPx()
        val shift = (clock.floatValue * 14f) % u
        var y = -u
        var row = 0
        while (y < size.height + u) {
            var x = -u + (if (row % 2 == 0) 0f else u / 2) + shift
            while (x < size.width + u) {
                val p = Path().apply { moveTo(x, y - u * 0.22f); lineTo(x + u * 0.22f, y); lineTo(x, y + u * 0.22f); lineTo(x - u * 0.22f, y); close() }
                drawPath(p, Color.White.copy(alpha = 0.05f))
                x += u
            }
            y += u / 2; row++
        }
    }
}

@Composable
internal fun StyleFileSelect(c: StyleCtx, modifier: Modifier = Modifier) {
    val loop = loopRowOn(c)
    SideEffect {
        StyleNav.side = emptyList()
        StyleNav.top = emptyList()
        StyleNav.bar = emptyList()
        StyleLoop.on = loop
    }
    // Console-era blips on each move (off by default).
    val context = LocalContext.current
    val sounds = LocalUiSounds.current
    val blips = Features.fsSounds.state.value
    val tick = FileSelectNav.moves.intValue
    LaunchedEffect(tick) { if (blips && tick > 0) RetroFx.play(context, sounds, RetroSound.LOCK, 1.4f) }
    if (c.games.isEmpty()) StyleEmpty(modifier) else FileSelectScreen(c, modifier)
}

@Composable
private fun FileSelectScreen(c: StyleCtx, modifier: Modifier) {
    val page0 = Features.fsPage.state.value
    SideEffect { FileSelectNav.cols = page0.cols; FileSelectNav.rows = page0.rows }
    val per = page0.cols * page0.rows
    val count = c.games.size
    val focus = c.focus.coerceIn(0, count - 1)
    val page = focus / per
    val pages = (count + per - 1) / per
    val animate = rememberAnimationsEnabled()
    val select by rememberUpdatedState(c.actions.select)
    val latestFocus by rememberUpdatedState(c.focus)
    val latestCount by rememberUpdatedState(count)
    val caption = CategoryTitle.captionOn(c.state.appearance)
    if (caption) CategoryTitle.Native(null)
    val outline = TextStyle(shadow = FsShadow)
    Column(modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text("SELECT FILE", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, style = outline)
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                if (caption) Text(c.categoryLabel.uppercase(), color = Color(0xFFFFD84A), fontSize = 15.sp, fontWeight = FontWeight.Bold, style = outline, maxLines = 1)
                if (pages > 1) Text("PAGE ${page + 1} / $pages", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, style = outline)
            }
        }
        Spacer(Modifier.height(8.dp))
        AnimatedContent(
            targetState = c.categoryLabel to page, label = "fsPage",
            transitionSpec = {
                if (!animate) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                else if (targetState.first != initialState.first) fadeIn(tween(260)) togetherWith fadeOut(tween(200))
                else {
                    val dir = if (targetState.second >= initialState.second) 1 else -1
                    (slideInHorizontally(tween(340)) { it * dir } + fadeIn(tween(220))) togetherWith (slideOutHorizontally(tween(340)) { -it * dir } + fadeOut(tween(220)))
                }
            },
            modifier = Modifier.fillMaxWidth().weight(1f).pointerInput(per, count) {
                var acc = 0f
                detectHorizontalDragGestures(onDragStart = { acc = 0f }) { ch, dx ->
                    ch.consume(); acc += dx
                    if (acc <= -120f) { acc = 0f; val p = latestFocus / per + 1; if (p * per < latestCount) select(p * per) }
                    if (acc >= 120f) { acc = 0f; val p = latestFocus / per - 1; if (p >= 0) select(p * per) }
                }
            }
        ) { (_, pg) -> FilePage(c, pg, page0, focus, animate) }
    }
}

@Composable
private fun FilePage(c: StyleCtx, page: Int, layout: Features.FsPage, focus: Int, animate: Boolean) {
    val per = layout.cols * layout.rows
    val spacing = Features.fsSpacing.state.value / 100f
    val gap = (14f * spacing).dp
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
        for (r in 0 until layout.rows) {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap * 1.4f)) {
                for (col in 0 until layout.cols) {
                    val slot = r * layout.cols + col
                    val i = page * per + slot
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        if (i < c.games.size) key(c.games[i].stableKey) { FileBlock(c, c.games[i], i, slot, i == focus, animate) }
                    }
                }
            }
        }
    }
}

/** One file: a chunky bevelled block with its console's name, the cover in a window, the name, stars and time played. */
@Composable
private fun FileBlock(c: StyleCtx, g: Game, index: Int, slot: Int, on: Boolean, animate: Boolean) {
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    val latestFocus by rememberUpdatedState(c.focus)
    val titles = LocalTitleOptions.current
    val colors = Features.fsColors.state.value
    val bounceK = Features.fsBounce.state.value / 100f
    val cover = c.cover(g)
    val coverBmp = rememberSoftBitmap(if (colors == Features.FsColors.GAME) cover else null, 48)
    val gameTint = rememberArtColor(coverBmp, FsBright[slot % FsBright.size])
    val base = when (colors) {
        Features.FsColors.BRIGHT -> FsBright[slot % FsBright.size]
        Features.FsColors.SOFT -> FsSoft[slot % FsSoft.size]
        Features.FsColors.GAME -> gameTint
    }
    val ink = if (base.luminance() > 0.62f) Color(0xFF1B1B1B) else Color.White
    val lit by animateFloatAsState(if (on) 1f else 0f, if (animate) spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium) else tween(0), label = "fsLit")
    if (on) SideEffect { FileSelectNav.openColor = base.toArgb().toLong() and 0xFFFFFFFFL }
    // (Its window opened: the block turns over — its back grows out into the window, WinScenes' FileCardBack — and turns
    // back once the window has gone back into it.)
    val opened = on && Features.winScenes.state.value && GamePopup.game?.stableKey == g.stableKey
    val turnAway by animateFloatAsState(if (opened) 1f else 0f, if (animate) tween(if (opened) 150 else 220) else tween(0), label = "fsTurn")
    val run = on && animate && bounceK > 0f && rememberIsActive() && !HomeIdle.calm && GamePopup.game == null
    val clock = rememberGcClock(run, 1f, 45)
    // (Toni: "replace File A / B / C / D with the console the game belongs to": the block's label is its console's name.)
    val consoleLabel = if (g.sourceType == com.shelf.launcher.data.SourceType.APP) "ANDROID" else titles.console(g.platform).uppercase()
    Box(
        Modifier.fillMaxSize()
            .zIndex(if (on) 2f else 0f)
            .graphicsLayer {
                val t = clock.floatValue
                val bob = if (run) sin(t * 5.5f) * 4.dp.toPx() * bounceK else 0f
                translationY = -lit * 6.dp.toPx() * bounceK + bob * lit
                rotationZ = if (run) sin(t * 2.6f) * 2.2f * bounceK * lit else 0f
                rotationY = (if (run) sin(t * 1.8f) * 7f * bounceK * lit else 0f) + 90f * turnAway
                cameraDistance = 16f * density
                val s = 1f + 0.05f * lit * bounceK.coerceAtMost(1f)
                scaleX = s; scaleY = s
                alpha = if (turnAway > 0.97f) 0f else 0.8f + 0.2f * lit
                // (Its name is text: a turned layer is drawn flat first — Android 13's text cache hung the Thor without it.)
                flatTextFor3d()
            }
            .pointerInput(index) { detectTapGestures { if (latestFocus == index) open() else select(index) } }
    ) {
        val shape = RoundedCornerShape(18.dp)
        // The block's thickness under it.
        Box(Modifier.fillMaxSize().offset(0.dp, 7.dp).clip(shape).background(mixC(base, Color.Black, 0.5f)))
        Box(
            Modifier.fillMaxSize().clip(shape)
                .background(Brush.verticalGradient(listOf(mixC(base, Color.White, 0.28f), base, mixC(base, Color.Black, 0.18f))))
                .border(3.dp, if (on) Color(0xFFFFE65C) else mixC(base, Color.White, 0.45f), shape)
                .gameOrigin(g.stableKey, on)
                .padding(10.dp)
        ) {
            // The shine along the top bevel.
            Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                drawRoundRect(Color.White.copy(alpha = 0.28f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
            }
            Row(Modifier.fillMaxSize().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.fillMaxHeight(0.86f).aspectRatio(0.75f).clip(RoundedCornerShape(8.dp))
                        .background(mixC(base, Color.Black, 0.45f)).border(3.dp, mixC(base, Color.White, 0.6f), RoundedCornerShape(8.dp)).padding(3.dp)
                        .clip(RoundedCornerShape(5.dp))
                ) {
                    if (cover != null) AsyncImage(rememberStyleArtRequest(cover, trim = true), g.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else if (g.sourceType == com.shelf.launcher.data.SourceType.APP) AppIconArt(g.packageName, wide = false, modifier = Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    FullText(consoleLabel, color = ink.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    FullText(titles.display(g.name), color = ink, fontSize = 17.sp, lineHeight = 19.sp, fontWeight = FontWeight.Black, maxLines = 2, style = if (ink == Color.White) TextStyle(shadow = FsShadow) else TextStyle())
                    Spacer(Modifier.height(4.dp))
                    val p = c.progress(g)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Canvas(Modifier.width(16.dp).height(16.dp)) { fsStar(Color(0xFFFFE04A)) }
                        Text(
                            " × ${p?.first ?: 0}" + (p?.let { "  / ${it.second}" } ?: ""),
                            color = ink, fontSize = 13.sp, fontWeight = FontWeight.Bold
                        )
                    }
                    Text(stylePlaytime(c.playtime(g), c.lastPlayed(g) != null), color = ink.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.fsStar(color: Color) {
    val c = Offset(size.width / 2, size.height / 2); val r = size.minDimension / 2
    val p = Path()
    for (i in 0 until 10) {
        val a = Math.PI / 5 * i - Math.PI / 2
        val rr = if (i % 2 == 0) r else r * 0.45f
        val x = c.x + (rr * kotlin.math.cos(a)).toFloat(); val y = c.y + (rr * kotlin.math.sin(a)).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    drawPath(p, color)
    drawPath(p, Color(0xFF7A5A00), style = androidx.compose.ui.graphics.drawscope.Stroke(1.2f))
}

private fun mixC(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)

/** Update 330: Settings › Home › Layout options (File select). Every one its own "fs_…" Features pref. */
@Composable
internal fun FileSelectSettings() {
    MutedNote(
        "Your games as chunky save-file blocks, a page at a time: each labelled with its console, with its cover, name, a star with the achievements " +
            "earned and time played. The one you're on bounces and rocks gently. ◀ ▶ ▲ ▼ through the page — past its sides the next " +
            "page slides in — A opens the game, L2 / R2 the next category. Tap a block to pick it, again to open it; swipe to turn the page."
    )
    Spacer(Modifier.height(10.dp))
    StyleChoice("Files on a page", Features.FsPage.values(), Features.fsPage.state.value, { it.label }) { Features.fsPage.value = it }
    PercentSlider("Spacing", Features.fsSpacing.state.value, { Features.fsSpacing.value = it.coerceIn(60, 150) }, min = 60, max = 150, valueText = { "$it%" })
    PercentSlider("Bounce", Features.fsBounce.state.value, { Features.fsBounce.value = it.coerceIn(0, 160) }, min = 0, max = 160,
        valueText = { if (it == 0) "Still" else "$it%" })
    StyleChoice("Block colours", Features.FsColors.values(), Features.fsColors.state.value, { it.label }) { Features.fsColors.value = it }
    Toggle(Icons.Filled.Texture, "Moving pattern", "A slowly drifting diamond pattern on the blue behind the blocks. Off: plain blue.", Features.fsPattern)
    Toggle(Icons.Filled.MusicNote, "Console-era blips", "A short synth blip as you move between files.", Features.fsSounds)
}
