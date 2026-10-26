package com.shelf.launcher.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/*
 * Update 347: Arcade cabinets' own game window — the machine's screen in its Insert Coin / service menu: grows out of the
 * monitor it came from (GameWindow's grow from the game's place) and switches on like a tube — a bright line opening into
 * the picture — and off again to a dot when it closes. On it: the game's picture with INSERT COIN blinking under it, the
 * game's name in the arcade's lettering, PRESS START (or CONTINUE) big at the top of a menu with a ▶ cursor, the rest of
 * the window's commands under it, its play time and achievements along the bottom like a high-score line. The other pages
 * (details, options, achievements, performance) on the same screen.
 */

private val ArcInk = Color(0xFFF4F1FF)
private val ArcYellow = Color(0xFFFFE23D)
private val ArcCyan = Color(0xFF3DE7FF)
private val ArcPink = Color(0xFFFF3DB4)
private val ArcDim = Color(0xFF8E89A8)

/** How far the tube is on at [k]: the line across ([line] 0…1) then the picture opening out of it ([open] 0…1). */
internal fun arcadeTube(k: Float): Pair<Float, Float> {
    val t = k.coerceIn(0f, 1f)
    return (t / 0.25f).coerceIn(0f, 1f) to ((t - 0.25f) / 0.75f).coerceIn(0f, 1f)
}

@Composable
internal fun ArcadeMenu(ctx: WinSceneCtx) {
    val font = pocketFont()
    val w = minOf(ctx.maxW, 800.dp)
    val h = minOf(ctx.maxH, 500.dp)
    val compact = h < 380.dp
    val big = if (compact) 15.sp else 19.sp
    val small = if (compact) 9.sp else 11.sp
    val line = if (compact) 11.sp else 13.sp
    val glow = TextStyle(fontFamily = font, shadow = Shadow(ArcCyan.copy(alpha = 0.55f), Offset.Zero, 10f))
    val reduced = !rememberAnimationsEnabled() || LocalLiteEffects.current
    // The coin dropping in (Layout options › Arcade sounds; nothing when off).
    androidx.compose.runtime.LaunchedEffect(Unit) { LayoutKitSounds.play(LayoutKit.ARCADE, KitSound.OPEN, null) }
    // INSERT COIN blinks (about once a second; still on Lite / with animations off).
    val blink = if (reduced) null else rememberInfiniteTransition(label = "coin").animateFloat(
        1f, 0f, infiniteRepeatable(tween(560, easing = LinearEasing), RepeatMode.Reverse), label = "coinBlink"
    )
    Box(
        Modifier.size(w, h).graphicsLayer {
            // The tube switching on: a line across, then the picture opening out of it; off: back to the line, then a dot.
            val (ln, op) = arcadeTube(ctx.reveal())
            scaleX = 0.03f + 0.97f * ln
            scaleY = 0.012f + 0.988f * op
            alpha = if (ln <= 0f) 0f else 1f
        }
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF2A2A30), Color(0xFF0E0E12))))
            .padding(if (compact) 8.dp else 12.dp)
    ) {
        // The tube's face.
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(26.dp)).background(Brush.radialGradient(listOf(Color(0xFF15123A), Color(0xFF07061A), Color(0xFF020208))))) {
            Column(Modifier.fillMaxSize().padding(horizontal = if (compact) 14.dp else 22.dp, vertical = if (compact) 8.dp else 14.dp)) {
                // The top line: the game, like a high-score table's header.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ctx.name.uppercase(), color = ArcYellow, fontSize = big, maxLines = 1, overflow = TextOverflow.Ellipsis, style = glow.copy(shadow = Shadow(ArcPink, Offset(2f, 2f), 0f)), modifier = Modifier.weight(1f))
                    if (ctx.page != WinPage.MAIN) Text(" " + arcadePageName(ctx.page), color = ArcCyan, fontSize = small, style = glow, maxLines = 1)
                }
                Text(listOfNotNull(ctx.console, ctx.year, ctx.developer).joinToString("  ·  ").uppercase(), color = ArcCyan, fontSize = small, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, style = glow)
                Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    // The picture, INSERT COIN under it.
                    Column(Modifier.weight(0.46f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.weight(1f).fillMaxWidth().border(2.dp, ArcPink.copy(alpha = 0.7f), RoundedCornerShape(4.dp)).padding(3.dp).clip(RoundedCornerShape(3.dp)).background(Color.Black)) {
                            val shot = ctx.state.screenshots[ctx.g.stableKey]?.firstOrNull()
                            val pic = shot ?: ctx.backdrop ?: ctx.wide ?: ctx.cover
                            if (pic != null) AsyncImage(rememberStyleArtRequest(pic), ctx.name, Modifier.fillMaxSize().pixelArtwork(),
                                contentScale = if (shot != null) ContentScale.Fit else ContentScale.Crop)
                            else Text(ctx.name.uppercase(), color = ArcYellow, fontSize = line, style = glow, modifier = Modifier.align(Alignment.Center).padding(8.dp))
                            Canvas(Modifier.fillMaxSize()) {
                                var y = 0f
                                while (y < size.height) { drawRect(Color.Black.copy(alpha = 0.2f), Offset(0f, y), Size(size.width, 1.5f)); y += 3.5f }
                            }
                        }
                        Spacer(Modifier.height(if (compact) 3.dp else 6.dp))
                        Text("INSERT COIN", color = ArcYellow, fontSize = line, style = glow, maxLines = 1,
                            modifier = Modifier.graphicsLayer { alpha = blink?.value?.let { if (it > 0.5f) 1f else 0.15f } ?: 1f })
                    }
                    Spacer(Modifier.width(if (compact) 10.dp else 18.dp))
                    Column(Modifier.weight(0.54f).fillMaxHeight()) {
                        if (ctx.page == WinPage.MAIN) {
                            val s = rememberScrollState()
                            Column(Modifier.weight(1f).sceneFadeEdges(s).verticalScroll(s), verticalArrangement = Arrangement.spacedBy(if (compact) 1.dp else 3.dp)) {
                                val rows = ctx.buttons.filter { !it.back } + ctx.tools + ctx.buttons.filter { it.back }
                                rows.forEach { a -> ArcadeMenuRow(a, font, if (a.primary) big else line, compact) }
                                if (ctx.chips.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    ctx.chips.forEach { a ->
                                        SceneControl(a, Modifier.fillMaxWidth()) { on ->
                                            Text("  " + a.label.uppercase(), color = if (on) ArcYellow else if (a.urgent) Color(0xFFFF8A3D) else ArcDim, fontSize = small, maxLines = 1,
                                                overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = font), modifier = Modifier.fillMaxWidth())
                                        }
                                    }
                                }
                            }
                        } else {
                            Box(Modifier.weight(1f).fillMaxWidth().border(2.dp, ArcCyan.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).clip(RoundedCornerShape(4.dp))) {
                                ctx.pageBody(Modifier.fillMaxSize())
                            }
                            Spacer(Modifier.height(4.dp))
                            ctx.buttons.forEach { a -> ArcadeMenuRow(a, font, line, compact) }
                        }
                    }
                }
                Spacer(Modifier.height(if (compact) 3.dp else 6.dp))
                // The bottom line: what's known about you and the game, like the high-score line.
                Text(
                    listOfNotNull("PLAYED " + ctx.played.uppercase(), if (ctx.total > 0) "ACHIEVEMENTS ${ctx.earned}/${ctx.total}" else null, ctx.last?.let { "LAST " + it.uppercase() })
                        .joinToString("   "),
                    color = ArcInk, fontSize = small, maxLines = 1, overflow = TextOverflow.Ellipsis, style = glow
                )
            }
            // The tube: scan lines, its curve, the glass.
            Canvas(Modifier.fillMaxSize()) {
                var y = 0f
                while (y < size.height) { drawRect(Color.Black.copy(alpha = 0.16f), Offset(0f, y), Size(size.width, 1.5f)); y += 3.5f }
                drawRect(Brush.radialGradient(0f to Color.Transparent, 0.7f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f), center = center, radius = size.maxDimension * 0.62f))
                drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.08f), Color.Transparent), start = Offset.Zero, end = Offset(size.width * 0.5f, size.height * 0.5f)))
                // The line it switches on from: bright while the picture is still opening.
                val (_, op) = arcadeTube(ctx.reveal())
                if (op < 1f) drawRect(Color.White.copy(alpha = (1f - op) * 0.85f), Offset(0f, size.height / 2f - size.height * 0.5f * op), Size(size.width, size.height * op.coerceAtLeast(0.02f)))
            }
        }
    }
}

/** One line of the menu: ▶ on the one you're on, it lit in yellow; Play big at the top. */
@Composable
private fun ArcadeMenuRow(a: WinAction, font: androidx.compose.ui.text.font.FontFamily, size: androidx.compose.ui.unit.TextUnit, compact: Boolean) {
    SceneControl(a, Modifier.fillMaxWidth()) { on ->
        Row(
            Modifier.fillMaxWidth().background(if (on) ArcYellow else Color.Transparent, RoundedCornerShape(3.dp))
                .padding(horizontal = 6.dp, vertical = if (a.primary) (if (compact) 3.dp else 6.dp) else (if (compact) 1.dp else 2.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (on) "▶ " else "  ", color = if (on) Color(0xFF1A1300) else ArcPink, fontSize = size, style = TextStyle(fontFamily = font))
            Text(a.label.uppercase(), color = if (on) Color(0xFF1A1300) else if (a.primary) ArcYellow else if (a.urgent) Color(0xFFFF8A3D) else ArcInk,
                fontSize = size, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = font, shadow = if (on) null else Shadow(ArcCyan.copy(alpha = 0.45f), Offset.Zero, 8f)))
        }
    }
}

private fun arcadePageName(p: WinPage) = when (p) {
    WinPage.MAIN -> ""; WinPage.DETAILS -> "GAME INFO"; WinPage.OPTIONS -> "OPTIONS"; WinPage.TROPHIES -> "ACHIEVEMENTS"; WinPage.PERF -> "PERFORMANCE"
}
