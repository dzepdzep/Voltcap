package com.shelf.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/*
 * Update 344: the game windows of the console-inspired layouts (Blades, …), drawn by the layouts themselves (see
 * WinScenes.kt for how a scene gets the game and its actions from GameWindow).
 */

/** Update 347: the pocket camera's screen moves in whole steps (12 a switch-on), as its little LCD would. */
internal fun cameraStep(k: Float): Float = (kotlin.math.floor(k.coerceIn(0f, 1f) * 12f) / 12f)

/** Update 347: the rental tag's swing as it opens — from 1 (swung away) past 0 (hanging) to a little the other way and back. */
internal fun tagSwing(k: Float): Float {
    val t = k.coerceIn(0f, 1f)
    // A damped swing: cos with the amplitude dying away, landing on 0 at t = 1.
    return ((1f - t) * (1f - t) * kotlin.math.cos(t * Math.PI.toFloat() * 2.5f)).let { if (t >= 1f) 0f else it }
}

/** Update 347: how far the terminal box has drawn itself at [k]: its frame (top, sides, bottom), the ground, the text. */
internal class TermFrame(val top: Float, val sides: Float, val bottom: Float, val fill: Float, val text: Float)

internal fun termFrame(k: Float): TermFrame {
    val t = k.coerceIn(0f, 1f)
    fun seg(a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
    return TermFrame(top = seg(0f, 0.18f), sides = seg(0.12f, 0.38f), bottom = seg(0.32f, 0.45f), fill = seg(0f, 0.3f), text = seg(0.45f, 1f))
}

/** Update 346: Play in the magazine's burst — one "!" (the look's own word may already shout: "CONTINUE!"). */
internal fun magBurstLabel(label: String): String = label.uppercase().let { if (it.endsWith("!")) it else "$it!" }

/** Update 346: a rental tag's key — Play reads "RENT & PLAY", never "RENT & RENT & PLAY" when the look says it already. */
internal fun rentalKeyLabel(label: String, primary: Boolean): String = label.uppercase().let { if (primary && !it.startsWith("RENT")) "RENT & $it" else it }

/** Update 346: a scrolling column whose edges fade where there's more (see sceneFadeEdges). */
@Composable
private fun fadeScroll(): Modifier { val s = rememberScrollState(); return Modifier.sceneFadeEdges(s).verticalScroll(s) }

private fun winPageName(p: WinPage) = when (p) {
    WinPage.MAIN -> "Info"; WinPage.DETAILS -> "Details"; WinPage.OPTIONS -> "Options"; WinPage.TROPHIES -> "Achievements"; WinPage.PERF -> "Performance"
}

// ---------------------------------------------------------------------------------------------------------------------
// Blades: the guide blade
// ---------------------------------------------------------------------------------------------------------------------

/**
 * Blades' window: one more blade slides in from the left over the dimmed stack — its curved edge and gloss, the game's
 * name big and light, its commands as a list with the lit bar (Play first), and on the right its cover with the facts
 * and the achievements under it. The other pages (details, options, achievements, performance) fill the right side.
 */
@Composable
internal fun BladeGuide(ctx: WinSceneCtx) {
    val p = bladePalette()
    val w = minOf(ctx.maxW, 820.dp)
    val h = minOf(ctx.maxH, 520.dp)
    val compact = h < 380.dp
    Box(
        Modifier.size(w, h).graphicsLayer {
            // (Update 347: the blade slides in edge first, turned a little away, and squares up as it lands; closing runs
            // it back out the same way.)
            val k = ctx.reveal().coerceIn(0f, 1f)
            val e = 1f - (1f - k) * (1f - k) * (1f - k)
            translationX = -(1f - e) * size.width * 0.6f
            rotationY = (1f - e) * 14f
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            cameraDistance = 14f * density
            alpha = (e * 1.6f).coerceIn(0f, 1f)
        }
    ) {
        // The blade itself: a curved leading edge, its colour, a gloss and a lit rim.
        Canvas(Modifier.fillMaxSize()) {
            val tab = size.width * 0.05f
            val corner = tab * 0.6f
            val path = Path().apply {
                moveTo(tab + corner, 0f)
                lineTo(size.width - 6f, 0f)
                quadraticTo(size.width, 0f, size.width, 6f)
                lineTo(size.width, size.height - 6f)
                quadraticTo(size.width, size.height, size.width - 6f, size.height)
                lineTo(tab + corner, size.height)
                quadraticTo(tab, size.height, tab * 0.8f, size.height - corner)
                quadraticTo(-tab * 0.5f, size.height / 2f, tab * 0.8f, corner)
                quadraticTo(tab, 0f, tab + corner, 0f)
                close()
            }
            // (Update 346: the same blade as the home's — a deep shadow on what it slides over, a bevel and a lit sheen
            // along its curved edge, the gloss band and a diagonal shine — so it reads as one more blade, not a card.)
            val edge = Path().apply {
                moveTo(tab + corner, size.height)
                quadraticTo(tab, size.height, tab * 0.8f, size.height - corner)
                quadraticTo(-tab * 0.5f, size.height / 2f, tab * 0.8f, corner)
                quadraticTo(tab, 0f, tab + corner, 0f)
            }
            translate(left = -tab * 0.5f, top = 6f) { drawPath(path, Color.Black.copy(alpha = 0.25f)) }
            translate(left = -tab * 0.22f, top = 10f) { drawPath(path, Color.Black.copy(alpha = 0.35f)) }
            drawPath(path, Brush.linearGradient(listOf(lerp(p.hi, Color.White, 0.08f), lerp(p.hi, p.lo, 0.55f), p.lo), start = Offset.Zero, end = Offset(size.width, size.height)))
            clipPath(path) {
                drawPath(edge, Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Transparent), startX = -tab * 0.5f, endX = tab * 2.2f), style = Stroke(tab * 0.9f))
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.04f), Color.Transparent), endY = size.height * 0.32f))
                drawRect(Brush.linearGradient(0f to Color.Transparent, 0.42f to Color.Transparent, 0.5f to Color.White.copy(alpha = 0.09f), 0.58f to Color.Transparent,
                    1f to Color.Transparent, start = Offset.Zero, end = Offset(size.width, size.height)))
            }
            drawPath(edge, Color.White.copy(alpha = 0.8f), style = Stroke(2.4f))
            drawPath(path, Color.White.copy(alpha = 0.3f), style = Stroke(1.5f))
        }
        Column(Modifier.fillMaxSize().padding(start = w * 0.08f, end = 18.dp, top = 14.dp, bottom = 12.dp)) {
            Text(ctx.name, color = Color.White, fontSize = if (compact) 22.sp else 30.sp, fontWeight = FontWeight.Light, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                ctx.console + (if (ctx.page != WinPage.MAIN) "  ·  " + winPageName(ctx.page) else ""),
                color = lerp(p.glow, Color.White, 0.45f), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1
            )
            Spacer(Modifier.height(if (compact) 6.dp else 12.dp))
            Row(Modifier.fillMaxSize()) {
                // The commands.
                Column(Modifier.weight(0.46f).fillMaxHeight().then(fadeScroll()), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    val rows = ctx.buttons.filter { !it.back } + (if (ctx.page == WinPage.MAIN) ctx.tools else emptyList()) + ctx.buttons.filter { it.back }
                    rows.forEach { a ->
                        SceneControl(a, Modifier.fillMaxWidth()) { on ->
                            val k by animateFloatAsState(if (on) 1f else 0f, tween(150), label = "bgRow")
                            Row(
                                Modifier.fillMaxWidth().height(if (a.primary) 44.dp else 36.dp).clip(RoundedCornerShape(3.dp))
                                    .background(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.94f * k), lerp(p.glow, Color.White, 0.5f).copy(alpha = 0.86f * k))))
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(a.icon, null, tint = if (on) p.ink else Color.White, modifier = Modifier.size(if (a.primary) 22.dp else 18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(a.label, color = if (on) p.ink else if (a.urgent) Color(0xFFFFC46B) else Color.White,
                                    fontSize = if (a.primary) 18.sp else 15.sp, fontWeight = if (on || a.primary) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (ctx.page == WinPage.MAIN && ctx.chips.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        ctx.chips.forEach { a ->
                            SceneControl(a, Modifier.fillMaxWidth()) { on ->
                                Text(a.label, color = if (on) Color.White else if (a.urgent) Color(0xFFFFC46B) else Color.White.copy(alpha = 0.72f), fontSize = 12.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    textDecoration = if (on) androidx.compose.ui.text.style.TextDecoration.Underline else null,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
                // The game: its cover and facts, or the page.
                Column(Modifier.weight(0.54f).fillMaxHeight()) {
                    if (ctx.page == WinPage.MAIN) {
                        Row(Modifier.fillMaxWidth().weight(1f)) {
                            Box(Modifier.fillMaxHeight().aspectRatio(0.72f, matchHeightConstraintsFirst = true).clip(RoundedCornerShape(3.dp)).background(Color.Black.copy(alpha = 0.3f))
                                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(3.dp))) {
                                if (ctx.cover != null) AsyncImage(rememberCoverRequest(ctx.cover), ctx.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f).then(fadeScroll())) {
                                @Composable
                                fun fact(label: String, value: String?) {
                                    if (value.isNullOrBlank()) return
                                    Text(label.uppercase(), color = lerp(p.glow, Color.White, 0.4f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                    Text(value, color = Color.White, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(6.dp))
                                }
                                fact("Played", ctx.played)
                                fact("Last played", ctx.last)
                                fact("Released", ctx.year)
                                fact("Made by", ctx.developer)
                                fact("Kind", ctx.genres)
                            }
                        }
                        if (ctx.total > 0) {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(androidx.compose.material.icons.Icons.Filled.EmojiEvents, null, tint = p.glow, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("${ctx.earned} / ${ctx.total}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(10.dp))
                                Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.18f))) {
                                    Box(Modifier.fillMaxHeight().fillMaxWidth((ctx.earned.toFloat() / ctx.total).coerceIn(0f, 1f)).background(Brush.horizontalGradient(listOf(p.glow, Color.White))))
                                }
                            }
                        }
                        if (!compact) ctx.summary?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, color = Color.White.copy(alpha = 0.82f), fontSize = 12.sp, lineHeight = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    } else ctx.pageBody(Modifier.fillMaxSize().clip(RoundedCornerShape(3.dp)).background(Color.Black.copy(alpha = 0.22f)))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Bubble home: a LiveArea card
// ---------------------------------------------------------------------------------------------------------------------

/**
 * Bubble home's window (when it opens from elsewhere — the second screen, a search — rather than as the layout's own
 * LiveArea): the same page in small — the game's art across the top, its bubble and name, the white Start plate, its
 * commands as round-ended white keys; the facts on a white panel, or the page.
 */
@Composable
internal fun BubbleCard(ctx: WinSceneCtx) {
    val w = minOf(ctx.maxW, 760.dp)
    val h = minOf(ctx.maxH, 500.dp)
    val compact = h < 380.dp
    val blue = Color(0xFF39A9FF)
    Box(
        Modifier.size(w, h).graphicsLayer {
            // (Update 347: it opens as the bubble swelling into the page — a round window growing from its bubble's
            // place, the page swinging down as it unfolds — and leaves as the LiveArea does, peeled off from the corner.)
            val k = ctx.reveal().coerceIn(0f, 1f)
            if (!ctx.closing()) {
                val r = (k * 1.25f).coerceIn(0f, 1f)
                shape = androidx.compose.foundation.shape.GenericShape { sz, _ ->
                    val c = Offset(sz.width * 0.12f, sz.height * 0.14f)
                    val far = kotlin.math.hypot(sz.width, sz.height)
                    val rad = 26f * density + (far - 26f * density) * r * r
                    addOval(androidx.compose.ui.geometry.Rect(c, rad))
                }
                clip = true
                rotationX = (1f - k) * -22f
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                cameraDistance = 14f * density
                alpha = (k * 2.5f).coerceIn(0f, 1f)
            }
        }.drawWithContent {
            if (!ctx.closing()) { drawContent(); return@drawWithContent }
            val pk = (1f - ctx.reveal()).coerceIn(0f, 1f)
            val o = peelShape(pk).createOutline(size, layoutDirection, this)
            if (o is androidx.compose.ui.graphics.Outline.Generic) clipPath(o.path) { this@drawWithContent.drawContent() } else drawContent()
            drawPeelFlap(pk)
        }.clip(RoundedCornerShape(10.dp)).background(Color(0xFF0D1520))
    ) {
        (ctx.backdrop ?: ctx.wide)?.let { AsyncImage(rememberStyleArtRequest(it), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.75f), 0.6f to Color.Black.copy(alpha = 0.3f), 1f to Color.Black.copy(alpha = 0.5f))))
        Row(Modifier.fillMaxSize().padding(18.dp)) {
            Column(Modifier.weight(0.5f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(if (compact) 44.dp else 56.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.DarkGray)
                        .border(2.dp, Color.White, androidx.compose.foundation.shape.CircleShape)) {
                        if (ctx.cover != null) AsyncImage(rememberCoverRequest(ctx.cover), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(ctx.name, color = Color.White, fontSize = if (compact) 17.sp else 21.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(ctx.console, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, maxLines = 1)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Column(Modifier.weight(1f).then(fadeScroll()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val rows = ctx.buttons.filter { !it.back } + (if (ctx.page == WinPage.MAIN) ctx.tools else emptyList()) + ctx.buttons.filter { it.back }
                    rows.forEach { a ->
                        SceneControl(a, Modifier) { on ->
                            Row(
                                Modifier.height(if (a.primary) 50.dp else 34.dp).width(if (a.primary) 200.dp else 180.dp)
                                    .clip(RoundedCornerShape(if (a.primary) 10.dp else 50.dp))
                                    .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFDDE5EE))))
                                    .border(if (on) 3.dp else 1.dp, if (on) blue else Color.White.copy(alpha = 0.6f), RoundedCornerShape(if (a.primary) 10.dp else 50.dp))
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = if (a.primary) Arrangement.Center else Arrangement.Start
                            ) {
                                if (!a.primary) { Icon(a.icon, null, tint = Color(0xFF26364A), modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)) }
                                Text(if (a.primary) "Start" else a.label, color = if (a.urgent) Color(0xFFB0630A) else Color(0xFF1A2633),
                                    fontSize = if (a.primary) 20.sp else 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Box(Modifier.weight(0.5f).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(if (ctx.page == WinPage.MAIN) Color.White.copy(alpha = 0.92f) else Color.Black.copy(alpha = 0.55f))) {
                if (ctx.page == WinPage.MAIN) Column(Modifier.fillMaxSize().padding(12.dp).then(fadeScroll())) {
                    if (ctx.total > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.EmojiEvents, null, tint = Color(0xFFE0A400), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("${ctx.earned} / ${ctx.total}", color = Color(0xFF1A2633), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    @Composable
                    fun line(label: String, value: String?) {
                        if (value.isNullOrBlank()) return
                        Text(label, color = Color(0xFF5B6B7C), fontSize = 11.sp)
                        Text(value, color = Color(0xFF1A2633), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(5.dp))
                    }
                    line("Played", ctx.played); line("Last played", ctx.last); line("Released", ctx.year); line("Made by", ctx.developer); line("Kind", ctx.genres)
                    ctx.chips.forEach { a ->
                        SceneControl(a, Modifier) { on ->
                            Text(a.label, color = if (on) blue else if (a.urgent) Color(0xFFB0630A) else Color(0xFF5B6B7C), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                textDecoration = if (on) androidx.compose.ui.text.style.TextDecoration.Underline else null)
                        }
                    }
                } else ctx.pageBody(Modifier.fillMaxSize())
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Pocket camera: the camera menu
// ---------------------------------------------------------------------------------------------------------------------

/**
 * Pocket camera's window: the camera cartridge's own menu screen in its four shades — a thick pixel frame, the game's
 * photo big on the left (dithered), its name and facts under it, and the commands as a pixel-font list on the right with
 * a ► cursor; the one you're on printed in reverse. Opens with the shutter.
 */
@Composable
internal fun CameraMenu(ctx: WinSceneCtx) {
    val pal = cameraPalette(com.shelf.launcher.data.Features.pcPalette.state.value)
    val c0 = Color(pal[0]); val c1 = Color(pal[1]); val c2 = Color(pal[2]); val c3 = Color(pal[3])
    val font = pocketFont()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val w = minOf(ctx.maxW, 780.dp)
    val h = minOf(ctx.maxH, 480.dp)
    val u = with(density) { kotlin.math.floor((h.toPx() / 120f)).coerceAtLeast(2f).toDp() }
    val fs: (Float) -> androidx.compose.ui.unit.TextUnit = { k -> with(density) { (u * k).toSp() } }
    // (Update 346: the same picture the layout shows — its cover, a screenshot or the art, as picked with ▲ ▼ there; a
    // screenshot / the art is a landscape photo.)
    val src = CameraNav.source
    val url = when (src) {
        1 -> ctx.state.screenshots[ctx.g.stableKey]?.firstOrNull() ?: ctx.cover
        2 -> ctx.backdrop ?: ctx.wide ?: ctx.cover
        else -> ctx.cover
    }
    val land = src != 0 && url != ctx.cover
    val bmp = if (land) rememberDithered(url, 128, 112, pal) else rememberDithered(url, 112, 128, pal)
    Box(
        Modifier.size(w, h).graphicsLayer {
            // (Update 347: the little screen switching on in steps, as the cartridge's would — a dot, a line across, then
            // the picture opening out of the line; switching off runs it back down to the dot.)
            val q = cameraStep(ctx.reveal())
            val line = (q / 0.3f).coerceIn(0f, 1f)
            val open = ((q - 0.3f) / 0.7f).coerceIn(0f, 1f)
            scaleX = 0.02f + 0.98f * line
            scaleY = 0.015f + 0.985f * open
            alpha = if (q <= 0f) 0f else 1f
        }
    ) {
        Canvas(Modifier.fillMaxSize()) { pixelFrame(u.toPx(), c3, c0, 3) }
        Row(Modifier.fillMaxSize().padding(u * 6)) {
            Column(if (land) Modifier.fillMaxHeight().fillMaxWidth(0.46f) else Modifier.fillMaxHeight()) {
                val pic = if (land) Modifier.fillMaxWidth().aspectRatio(128f / 112f) else Modifier.weight(1f).aspectRatio(112f / 128f, matchHeightConstraintsFirst = true)
                Box(pic.border(1.dp, c0).background(c2)) {
                    if (bmp != null) androidx.compose.foundation.Image(bmp, ctx.name, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds,
                        filterQuality = androidx.compose.ui.graphics.FilterQuality.None)
                }
                if (land) Spacer(Modifier.weight(1f))
                Spacer(Modifier.height(u * 2))
                Text(ctx.console.uppercase() + (ctx.year?.let { "  $it" } ?: ""), color = c1, fontFamily = font, fontSize = fs(4.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                // (Update 346: the time on its own line — beside the achievements it was cut to "TROPHIES 0/…".)
                Text("TIME " + ctx.played.uppercase(), color = c1, fontFamily = font, fontSize = fs(4.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(u * 6))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text(ctx.name.uppercase(), color = c0, fontFamily = font, fontSize = fs(6.5f), maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = fs(8f))
                Spacer(Modifier.height(u))
                Box(Modifier.fillMaxWidth().height(u).background(c0))
                Spacer(Modifier.height(u * 3))
                if (ctx.page == WinPage.MAIN) {
                    Column(Modifier.weight(1f).then(fadeScroll()), verticalArrangement = Arrangement.spacedBy(u)) {
                        val rows = ctx.buttons.filter { !it.back } + ctx.tools + ctx.buttons.filter { it.back }
                        rows.forEach { a ->
                            SceneControl(a, Modifier.fillMaxWidth()) { on ->
                                Row(Modifier.fillMaxWidth().background(if (on) c0 else Color.Transparent).padding(horizontal = u * 2, vertical = u * 0.5f),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Canvas(Modifier.size(u * 7)) { if (on) pixelIcon(PX_CURSOR, Offset.Zero, u.toPx(), c3) }
                                    Spacer(Modifier.width(u * 2))
                                    Text(a.label.uppercase(), color = if (on) c3 else if (a.urgent) c1 else c0, fontFamily = font,
                                        fontSize = fs(if (a.primary) 6f else 5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(u * 2))
                    if (ctx.total > 0) Text("TROPHIES ${ctx.earned}/${ctx.total}", color = c1, fontFamily = font, fontSize = fs(4.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Box(Modifier.weight(1f).fillMaxWidth().background(c0)) { ctx.pageBody(Modifier.fillMaxSize().padding(u * 2)) }
                    Spacer(Modifier.height(u * 2))
                    ctx.buttons.forEach { a ->
                        SceneControl(a, Modifier) { on ->
                            Text((if (on) "> " else "  ") + a.label.uppercase(), color = if (on) c0 else c1, fontFamily = font, fontSize = fs(5f), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Panel dashboard: the detail panel
// ---------------------------------------------------------------------------------------------------------------------

/**
 * Panel dashboard's window: one big panel swinging round to face you (it turns in from the angle the row's panels stand
 * at) — the game's wide art across its top with the name over it, its commands as a column of flat rows on the left (the
 * one you're on lit in the dashboard's colour), its cover, facts and achievements on the right.
 */
@Composable
internal fun PanelDetail(ctx: WinSceneCtx) {
    val p = panelPalette()
    val w = minOf(ctx.maxW, 800.dp)
    val h = minOf(ctx.maxH, 500.dp)
    val compact = h < 380.dp
    // (Update 346: on a 16:9 handheld the window is ~440 dp high — the art strip and the rows are sized to fit the list.)
    val short = h < 470.dp
    val ink = p.panelInk
    Box(
        Modifier.size(w, h).graphicsLayer {
            // (Update 347: the panel comes forward out of its row — standing at the row's angle, it turns to face you as it
            // grows, and swings back into the row when it closes.)
            val k = ctx.reveal().coerceIn(0f, 1f)
            val e = 1f - (1f - k) * (1f - k)
            rotationY = (1f - e) * -34f
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            cameraDistance = 12f * density
            translationX = -(1f - e) * size.width * 0.06f
            alpha = (k * 1.6f).coerceIn(0f, 1f)
        }.clip(RoundedCornerShape(6.dp)).background(p.panel).border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
    ) {
        Column(Modifier.fillMaxSize()) {
            // The art strip with the name.
            Box(Modifier.fillMaxWidth().height(if (compact) 78.dp else if (short) 100.dp else 132.dp).background(p.bottom)) {
                (ctx.backdrop ?: ctx.wide)?.let { AsyncImage(rememberStyleArtRequest(it), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.3f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))))
                Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 18.dp, vertical = 10.dp)) {
                    Text(ctx.name, color = Color.White, fontSize = if (compact) 20.sp else 26.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(ctx.console + (if (ctx.page != WinPage.MAIN) "  ·  " + winPageName(ctx.page) else ""), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, maxLines = 1)
                }
                Box(Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter).background(p.accent))
            }
            Row(Modifier.fillMaxSize().padding(14.dp)) {
                Column(Modifier.weight(0.45f).fillMaxHeight().then(fadeScroll()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val rows = ctx.buttons.filter { !it.back } + (if (ctx.page == WinPage.MAIN) ctx.tools else emptyList()) + ctx.buttons.filter { it.back }
                    rows.forEach { a ->
                        SceneControl(a, Modifier.fillMaxWidth()) { on ->
                            Row(
                                Modifier.fillMaxWidth().height(if (a.primary) (if (short) 40.dp else 44.dp) else if (short) 31.dp else 36.dp)
                                    .background(if (on) lerp(p.accent, Color.Black, 0.28f) else Color.Transparent).padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(a.icon, null, tint = if (on) Color.White else ink.copy(alpha = 0.75f), modifier = Modifier.size(if (a.primary) 22.dp else 18.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(a.label, color = if (on) Color.White else if (a.urgent) Color(0xFFC0700A) else ink, fontSize = if (a.primary) 18.sp else if (short) 14.sp else 15.sp,
                                    fontWeight = if (a.primary || on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                Spacer(Modifier.width(14.dp))
                Box(Modifier.weight(0.55f).fillMaxHeight()) {
                    if (ctx.page == WinPage.MAIN) Row(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxHeight().aspectRatio(0.72f, matchHeightConstraintsFirst = true).background(Color.Black.copy(alpha = 0.1f))) {
                            if (ctx.cover != null) AsyncImage(rememberCoverRequest(ctx.cover), ctx.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f).then(fadeScroll())) {
                            @Composable
                            fun fact(label: String, value: String?) {
                                if (value.isNullOrBlank()) return
                                Text(label, color = ink.copy(alpha = 0.6f), fontSize = 11.sp)
                                Text(value, color = ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(6.dp))
                            }
                            if (ctx.total > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.EmojiEvents, null, tint = p.accent, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("${ctx.earned} / ${ctx.total}", color = ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                Box(Modifier.padding(vertical = 5.dp).fillMaxWidth().height(5.dp).background(ink.copy(alpha = 0.12f))) {
                                    Box(Modifier.fillMaxHeight().fillMaxWidth((ctx.earned.toFloat() / ctx.total).coerceIn(0f, 1f)).background(p.accent))
                                }
                                Spacer(Modifier.height(4.dp))
                            }
                            fact("Played", ctx.played); fact("Last played", ctx.last); fact("Released", ctx.year); fact("Made by", ctx.developer); fact("Kind", ctx.genres)
                            ctx.chips.forEach { a ->
                                SceneControl(a, Modifier) { on ->
                                    Text(a.label, color = if (on) p.accent else if (a.urgent) Color(0xFFC0700A) else ink.copy(alpha = 0.65f), fontSize = 12.sp, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis, textDecoration = if (on) androidx.compose.ui.text.style.TextDecoration.Underline else null)
                                }
                            }
                        }
                    } else Box(Modifier.fillMaxSize().border(1.dp, ink.copy(alpha = 0.18f))) { ctx.pageBody(Modifier.fillMaxSize()) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Game magazine: the torn-out page
// ---------------------------------------------------------------------------------------------------------------------

/**
 * Game magazine's window: the feature torn out of the issue and pinned up — a page with a torn left edge, a little crooked,
 * the cover taped to it, the headline in the issue's type, and its commands as the magazine's callouts: PLAY in a burst,
 * the others as "▸ SEE PAGE" lines; the one you're on circled in marker.
 */
@Composable
internal fun MagazinePage(ctx: WinSceneCtx) {
    val st = magStyle(com.shelf.launcher.data.Features.mgStyle.state.value)
    val w = minOf(ctx.maxW, 720.dp)
    val h = minOf(ctx.maxH, 500.dp)
    val compact = h < 380.dp
    Box(
        Modifier.size(w, h).graphicsLayer {
            // (Update 347: the torn page is lifted off the issue — it comes up tilted back from its bottom edge, turning,
            // and settles crooked on top; closing lays it back down.)
            val k = ctx.reveal().coerceIn(0f, 1f)
            val e = 1f - (1f - k) * (1f - k)
            rotationX = (1f - e) * 42f
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
            cameraDistance = 16f * density
            rotationZ = -2f + (1f - e) * -9f
            translationY = (1f - e) * size.height * 0.12f
            alpha = (k * 1.8f).coerceIn(0f, 1f)
        }
    ) {
        // The paper with its torn edge.
        Canvas(Modifier.fillMaxSize()) {
            val tear = Path().apply {
                moveTo(18f, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height); lineTo(18f, size.height)
                var y = size.height; var i = 0
                while (y > 0f) { y -= 14f; lineTo(if (i % 2 == 0) 4f else 18f + (i % 3) * 4f, y.coerceAtLeast(0f)); i++ }
                close()
            }
            translate(left = 8f, top = 10f) { drawPath(tear, Color.Black.copy(alpha = 0.35f)) }
            drawPath(tear, st.paper)
        }
        Row(Modifier.fillMaxSize().padding(start = 36.dp, end = 18.dp, top = 16.dp, bottom = 14.dp)) {
            // The cover, taped on.
            Box(Modifier.fillMaxHeight(0.78f).aspectRatio(0.72f, matchHeightConstraintsFirst = true).graphicsLayer { rotationZ = 3f }) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f))) {
                    if (ctx.cover != null) AsyncImage(rememberCoverRequest(ctx.cover), ctx.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Box(Modifier.align(Alignment.TopCenter).offset(y = (-8).dp).size(60.dp, 18.dp).graphicsLayer { rotationZ = -6f }.background(Color(0x99F3E9C6)))
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text(st.masthead + "  ·  " + (if (ctx.page == WinPage.MAIN) "FEATURE" else winPageName(ctx.page).uppercase()), color = st.accent, fontFamily = st.head,
                    fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp)
                Text(ctx.name.uppercase(), color = st.ink, fontFamily = st.head, fontWeight = st.headWeight, fontSize = if (compact) 20.sp else 26.sp, lineHeight = if (compact) 22.sp else 28.sp,
                    fontStyle = if (st.italicHead) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(ctx.console, ctx.year, ctx.developer).joinToString("  ·  "), color = st.muted, fontFamily = st.body, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.padding(vertical = 6.dp).fillMaxWidth().height(2.dp).background(st.ink))
                if (ctx.page == WinPage.MAIN) {
                    Column(Modifier.weight(1f).then(fadeScroll()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ctx.buttons.filter { it.primary }.forEach { a ->
                            SceneControl(a, Modifier) { on ->
                                Box(Modifier.size(if (compact) 88.dp else 112.dp, if (compact) 42.dp else 60.dp), contentAlignment = Alignment.Center) {
                                    Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = if (on) -4f else 0f; val s = if (on) 1.08f else 1f; scaleX = s; scaleY = s }) {
                                        val p = Path(); val n = 16
                                        for (i in 0 until n * 2) {
                                            val rx = if (i % 2 == 0) size.width / 2f else size.width / 2.5f
                                            val ry = if (i % 2 == 0) size.height / 2f else size.height / 2.5f
                                            val a2 = Math.PI * i / n
                                            val x = size.width / 2f + (rx * kotlin.math.cos(a2)).toFloat(); val y = size.height / 2f + (ry * kotlin.math.sin(a2)).toFloat()
                                            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                                        }
                                        p.close(); drawPath(p, if (on) st.accent else st.accent2)
                                    }
                                    Text(magBurstLabel(a.label), color = if (on) Color.White else st.ink, fontFamily = st.head, fontWeight = FontWeight.Black, fontSize = if (compact) 13.sp else 16.sp)
                                }
                            }
                        }
                        // (Update 346: the "see page" callouts run in two columns, down then across, as a contents box would —
                        // in one column the last ones ran off the bottom of the page on a 16:9 handheld.)
                        val callouts = ctx.buttons.filter { !it.primary && !it.back } + ctx.tools + ctx.buttons.filter { it.back }
                        val half = (callouts.size + 1) / 2
                        Row(Modifier.fillMaxWidth()) {
                            for (col in listOf(callouts.take(half), callouts.drop(half))) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    col.forEach { a ->
                                        SceneControl(a, Modifier) { on ->
                                            Text("▸ " + a.label.uppercase(), color = if (a.urgent) st.accent else st.ink, fontFamily = st.head, fontWeight = FontWeight.Bold,
                                                fontSize = if (compact) 12.sp else 14.sp, letterSpacing = 0.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.then(if (on) Modifier.border(2.dp, st.accent, androidx.compose.foundation.shape.RoundedCornerShape(50)) else Modifier)
                                                    .padding(horizontal = 10.dp, vertical = if (compact) 1.dp else 3.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Text(listOfNotNull(if (ctx.playtime == null && ctx.lastPlayed == null) "NOT PLAYED YET" else "PLAYED " + ctx.played.uppercase(), if (ctx.total > 0) "ACHIEVEMENTS ${ctx.earned}/${ctx.total}" else null).joinToString("   "),
                        color = st.muted, fontFamily = st.head, fontSize = 10.sp, letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Box(Modifier.weight(1f).fillMaxWidth().border(1.dp, st.ink.copy(alpha = 0.35f))) { ctx.pageBody(Modifier.fillMaxSize()) }
                    ctx.buttons.forEach { a ->
                        SceneControl(a, Modifier) { on ->
                            Text("▸ " + a.label.uppercase(), color = if (on) st.accent else st.ink, fontFamily = st.head, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Rental shop: the rental tag
// ---------------------------------------------------------------------------------------------------------------------

/**
 * Rental shop's window, beside the case you picked up (GameWindow brings the case off the shelf to be turned): the shop's
 * bright yellow rental tag — its name in heavy black capitals, the console, its genre and maker (update 346: no made-up
 * rental period or due date — only the game's own facts; "BE KIND, PLAY IT AGAIN" is the shop's printed slogan), time
 * played and achievements, a barcode, a perforated tear-off; the commands as black punched keys, the one you're on
 * lit in the shop's neon.
 */
@Composable
internal fun RentalTag(ctx: WinSceneCtx) {
    val yellow = Color(0xFFFFE14A); val ink = Color(0xFF1C1C1C); val neon = Color(0xFFFF4FD8)
    val w = minOf(ctx.maxW, 460.dp)
    val h = minOf(ctx.maxH, 520.dp)
    // (Update 346: a short screen gets a short tag — one line of facts, the title on one line, a slimmer tear-off.)
    val short = h < 380.dp
    val tear = if (short) 0.1f else 0.15f
    Box(
        Modifier.size(w, h).graphicsLayer {
            // (Update 347: the tag swings in on the string through its hole as the case comes off the shelf — past
            // straight and back, once — and swings away as the case goes back.)
            val k = ctx.reveal().coerceIn(0f, 1f)
            rotationZ = 2f + tagSwing(k) * 26f
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.04f)
            translationX = (1f - k) * (1f - k) * size.width * 0.35f
            alpha = (k * 2f).coerceIn(0f, 1f)
        }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            translate(left = 8f, top = 10f) { drawRect(Color.Black.copy(alpha = 0.35f)) }
            drawRect(yellow)
            // The hole it hung from and the perforation above the tear-off.
            drawCircle(ink, 9f, Offset(size.width / 2f, 22f))
            var x = 10f
            while (x < size.width - 10f) { drawCircle(ink.copy(alpha = 0.6f), 2.5f, Offset(x, size.height * (1f - tear + 0.02f))); x += 12f }
            // The barcode.
            var bx = 18f; var i = 0
            while (bx < size.width * 0.6f) { val bw = if (i % 3 == 0) 6f else 3f; drawRect(ink, Offset(bx, size.height * (1f - tear + 0.045f)), Size(bw, size.height * (tear - 0.07f))); bx += bw + 3f + (i % 2) * 2f; i++ }
        }
        Column(Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = if (short) 26.dp else 34.dp, bottom = h * tear)) {
            if (!short) Text("GAME RENTALS", color = ink, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            Text(ctx.name.uppercase(), color = ink, fontSize = if (short) 18.sp else 22.sp, lineHeight = if (short) 20.sp else 24.sp, fontWeight = FontWeight.Black,
                maxLines = if (short) 1 else 2, overflow = TextOverflow.Ellipsis)
            Text(ctx.console.uppercase() + (ctx.year?.let { "  ·  $it" } ?: ""), color = ink.copy(alpha = 0.75f), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Box(Modifier.padding(vertical = if (short) 4.dp else 6.dp).fillMaxWidth().height(3.dp).background(ink))
            if (ctx.page == WinPage.MAIN) {
                // (Update 346: the facts two by two, label over value, so the keys below fit on the tag.)
                val facts = listOfNotNull(ctx.genres?.let { "GENRE" to it }, ctx.developer?.let { "MADE BY" to it }, "PLAYED" to ctx.played,
                    if (ctx.total > 0) "TROPHIES" to "${ctx.earned} / ${ctx.total}" else null)
                // (Update 347: on a short tag the facts are one line, so every key fits on the tag without scrolling.)
                if (short) Text(facts.takeLast(2).joinToString("   ·   ") { (l, v) -> "$l ${v.uppercase()}" }, color = ink, fontSize = 10.sp, fontWeight = FontWeight.Black,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                else facts.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 3.dp)) {
                        pair.forEach { (l, v) ->
                            Column(Modifier.weight(1f).padding(end = 6.dp)) {
                                Text(l, color = ink.copy(alpha = 0.65f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1)
                                Text(v.uppercase(), color = ink, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(if (short) 4.dp else 8.dp))
                Column(Modifier.weight(1f).then(fadeScroll()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    @Composable
                    fun key(a: WinAction, m: Modifier) {
                        SceneControl(a, m) { on ->
                            Row(Modifier.fillMaxWidth().height(if (a.primary) (if (short) 32.dp else 38.dp) else if (short) 26.dp else 30.dp).background(if (on) neon else ink).padding(horizontal = if (a.primary) 10.dp else 6.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                // (Narrow keys on a narrow tag: the words, no icon, so they read in full.)
                                val tight = !a.primary && w < 340.dp
                                if (!tight) {
                                    Icon(a.icon, null, tint = if (on) ink else yellow, modifier = Modifier.size(if (a.primary) 16.dp else 12.dp))
                                    Spacer(Modifier.width(if (a.primary) 8.dp else 4.dp))
                                }
                                // (Update 346: the window's look already says "RENT & PLAY" — it read "RENT & RENT & PLAY".)
                                val label = rentalKeyLabel(a.label, a.primary)
                                Text(label, color = if (on) ink else if (a.urgent) Color(0xFFFF8A3D) else yellow,
                                    fontSize = if (a.primary) 15.sp else if (tight) 9.sp else 10.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    // Play across the tag; the others as a block of punched keys, two by two, so they all fit on the tag.
                    ctx.buttons.filter { it.primary }.forEach { key(it, Modifier.fillMaxWidth()) }
                    val rest = ctx.buttons.filter { !it.primary && !it.back } + ctx.tools + ctx.buttons.filter { it.back }
                    val across = if (w >= 340.dp || short) 3 else 2
                    rest.chunked(across).forEach { group ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            group.forEach { key(it, Modifier.weight(1f)) }
                            repeat(across - group.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    ctx.chips.forEach { a ->
                        SceneControl(a, Modifier) { on ->
                            Text(a.label.uppercase(), color = if (on) neon else ink.copy(alpha = 0.75f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth().border(2.dp, ink)) { ctx.pageBody(Modifier.fillMaxSize()) }
                Spacer(Modifier.height(4.dp))
                ctx.buttons.forEach { a ->
                    SceneControl(a, Modifier) { on ->
                        Text("▸ " + a.label.uppercase(), color = if (on) Color(0xFFC0161A) else ink, fontSize = 13.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Terminal: the RUN dialog
// ---------------------------------------------------------------------------------------------------------------------

/**
 * Terminal's window: a DOS-style dialog drawn in the monitor's own colours — a double-line frame with its title in the
 * top border, the game's file (TYPE …NFO: its facts and a few lines of its story) on the left, the commands as a menu on
 * the right with the one you're on in reverse video and a ► cursor. Pops up line by line.
 */
@Composable
internal fun TerminalDialog(ctx: WinSceneCtx) {
    val tc = termColors(com.shelf.launcher.data.Features.tmTheme.state.value)
    val font: androidx.compose.ui.text.font.FontFamily = if (com.shelf.launcher.data.Features.tmPixel.state.value) pocketFont() else androidx.compose.ui.text.font.FontFamily.Monospace
    val w = minOf(ctx.maxW, 760.dp)
    val h = minOf(ctx.maxH, 380.dp)
    val compact = h < 340.dp
    val fs = if (compact) 12.sp else 14.sp
    val glow = com.shelf.launcher.data.Features.tmCrt.state.value != com.shelf.launcher.data.Features.TmCrt.OFF
    val style = androidx.compose.ui.text.TextStyle(fontFamily = font, fontSize = fs, color = tc.fg,
        shadow = if (glow) androidx.compose.ui.graphics.Shadow(tc.fg.copy(alpha = 0.7f), Offset.Zero, 8f) else null)
    Box(
        Modifier.size(w, h).graphicsLayer {
            // (Update 347: the box draws itself — the double frame runs out from the top centre, down the sides and along
            // the bottom; then the text comes up line by line; closing takes it away in reverse.)
            val k = ctx.reveal().coerceIn(0f, 1f)
            alpha = if (k < 0.02f) 0f else 1f
        }
    ) {
        val k = ctx.reveal().coerceIn(0f, 1f)
        Canvas(Modifier.fillMaxSize()) {
            val f = termFrame(k)
            // The box's ground fills in behind the frame as it goes round.
            drawRect(tc.bg, alpha = (f.fill).coerceIn(0f, 1f))
            for ((inset, sw) in listOf(6f to 2f, 11f to 2f)) {
                val l = inset; val t = inset; val r = size.width - inset; val b = size.height - inset
                val cx = size.width / 2f
                // top, from the middle out
                drawLine(tc.fg, Offset(cx - (cx - l) * f.top, t), Offset(cx + (r - cx) * f.top, t), sw)
                // sides, down
                if (f.sides > 0f) { drawLine(tc.fg, Offset(l, t), Offset(l, t + (b - t) * f.sides), sw); drawLine(tc.fg, Offset(r, t), Offset(r, t + (b - t) * f.sides), sw) }
                // bottom, in to the middle
                if (f.bottom > 0f) { drawLine(tc.fg, Offset(l, b), Offset(l + (cx - l) * f.bottom, b), sw); drawLine(tc.fg, Offset(r, b), Offset(r - (r - cx) * f.bottom, b), sw) }
            }
        }
        Text(" RUN " + TermNav.alias(ctx.g) + " ", style = style.copy(color = tc.hiInk, shadow = null), modifier = Modifier.align(Alignment.TopCenter)
            .graphicsLayer { alpha = if (termFrame(ctx.reveal()).top >= 0.6f) 1f else 0f }.background(tc.hi))
        Row(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 22.dp).drawWithContent {
            // The lines come up one at a time from the top (a whole text line at a step).
            val shown = termFrame(k).text
            if (shown <= 0f) return@drawWithContent
            val lineH = fs.toPx() * 1.35f
            val lines = kotlin.math.ceil(size.height / lineH)
            val cut = kotlin.math.floor(shown * lines) * lineH
            clipRect(0f, 0f, size.width, cut) { this@drawWithContent.drawContent() }
        }) {
            Column(Modifier.weight(0.55f).fillMaxHeight().then(fadeScroll())) {
                Text("C:\\> TYPE " + TermNav.alias(ctx.g) + ".NFO", style = style.copy(color = tc.dim))
                Spacer(Modifier.height(4.dp))
                Text(ctx.name.uppercase(), style = style.copy(color = tc.accent), maxLines = 2, overflow = TextOverflow.Ellipsis)
                @Composable
                fun line(l: String, v: String?) { if (!v.isNullOrBlank()) Text(l.padEnd(9) + v.uppercase(), style = style, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                line("SYSTEM", ctx.console); line("YEAR", ctx.year); line("MAKER", ctx.developer); line("PLAYED", ctx.played); line("LAST", ctx.last)
                if (ctx.total > 0) {
                    val filled = ((ctx.earned.toFloat() / ctx.total) * 12).toInt().coerceIn(0, 12)
                    Text("TROPHY".padEnd(9) + "[" + "#".repeat(filled) + ".".repeat(12 - filled) + "] ${ctx.earned}/${ctx.total}", style = style, maxLines = 1)
                }
                if (ctx.page == WinPage.MAIN && !compact) ctx.summary?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it.uppercase(), style = style.copy(color = tc.dim, fontSize = fs * 0.85f), maxLines = 6, overflow = TextOverflow.Ellipsis)
                }
                if (ctx.page != WinPage.MAIN) Box(Modifier.fillMaxWidth().height(h * 0.5f).padding(top = 6.dp).border(1.dp, tc.dim)) { ctx.pageBody(Modifier.fillMaxSize()) }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(0.45f).fillMaxHeight().then(fadeScroll())) {
                val rows = ctx.buttons.filter { !it.back } + (if (ctx.page == WinPage.MAIN) ctx.tools else emptyList()) + ctx.buttons.filter { it.back }
                rows.forEach { a ->
                    SceneControl(a, Modifier.fillMaxWidth()) { on ->
                        Text((if (on) "► " else "  ") + a.label.uppercase(), style = if (on) style.copy(color = tc.hiInk, shadow = null) else style.copy(color = if (a.urgent) tc.accent else tc.fg),
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().background(if (on) tc.hi else Color.Transparent).padding(horizontal = 4.dp, vertical = 2.dp))
                    }
                }
                if (ctx.page == WinPage.MAIN) ctx.chips.forEach { a ->
                    SceneControl(a, Modifier.fillMaxWidth()) { on ->
                        Text("  " + a.label.uppercase(), style = style.copy(color = if (on) tc.accent else tc.dim, fontSize = fs * 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}
