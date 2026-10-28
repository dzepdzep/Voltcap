package com.shelf.launcher.ui

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/*
 * Next version: the windows of Cinema and Arctic hub (see WinScenes.kt for how a layout's own window works).
 * - Cinema Premiere: the billboard grown into the game's own page — its art across the top, rising out of the tile you
 *   picked and settling as the page opens out from it; the logo or name over the art, a line of facts, Play as the big
 *   white key, the other actions as round keys with their names under them, the story and achievements beside them.
 * - Arctic info drawer: a drawer sliding out from the right edge, the poster you picked standing on the left; in the
 *   drawer the clear logo, an info list with icons, the plot, and the actions as a media-centre list (icon, name, the one
 *   you're on a full-width bar).
 * The other pages (details, options, achievements, performance) are GameWindow's own, drawn inside each window.
 */

private fun homeWinPageName(p: WinPage) = when (p) {
    WinPage.MAIN -> "Overview"; WinPage.DETAILS -> "Details"; WinPage.OPTIONS -> "Options"; WinPage.TROPHIES -> "Achievements"; WinPage.PERF -> "Performance"
}

// ---------------------------------------------------------------------------------------------------------------------
// Cinema Premiere
// ---------------------------------------------------------------------------------------------------------------------

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun CinemaPremiere(ctx: WinSceneCtx) {
    val w = minOf(ctx.maxW, 860.dp)
    val h = minOf(ctx.maxH, 560.dp)
    val compact = h < 400.dp
    val accent = cinemaAccent()
    Box(
        Modifier.width(w).height(h).graphicsLayer {
            // Opens out of the tile: up from a little smaller, its corners rounding in as it settles.
            val k = ctx.reveal().coerceIn(0f, 1f)
            val e = 1f - (1f - k) * (1f - k) * (1f - k)
            val s = 0.82f + 0.18f * e
            scaleX = s; scaleY = s
            transformOrigin = TransformOrigin(0.5f, 0.62f)
            alpha = (k * 2.2f).coerceIn(0f, 1f)
            shape = RoundedCornerShape(10.dp); clip = true
            shadowElevation = 24.dp.toPx() * e
        }.background(Color(0xFF181818))
    ) {
        Column(Modifier.fillMaxSize()) {
            // The art: the game's backdrop with a slow drift in, fading into the page.
            Box(Modifier.fillMaxWidth().height(h * (if (ctx.page == WinPage.MAIN) 0.42f else 0.34f)).clipToBounds()) {
                val art = ctx.backdrop ?: ctx.wide ?: ctx.cover
                if (art != null) AsyncImage(
                    rememberStyleArtRequest(art), null, Modifier.fillMaxSize().graphicsLayer {
                        val k = ctx.reveal().coerceIn(0f, 1f); val z = 1.14f - 0.14f * k; scaleX = z; scaleY = z
                    }, contentScale = ContentScale.Crop
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.55f to Color(0xFF181818).copy(alpha = 0.35f), 1f to Color(0xFF181818))))
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xFF181818).copy(alpha = 0.7f), 0.5f to Color.Transparent)))
                Column(Modifier.align(Alignment.BottomStart).padding(start = 28.dp, bottom = 10.dp, end = 28.dp).graphicsLayer {
                    val k = ctx.reveal().coerceIn(0f, 1f); translationY = (1f - k) * 30.dp.toPx(); alpha = k
                }) {
                    if (ctx.logo != null) AsyncImage(rememberStyleArtRequest(ctx.logo, trim = true), ctx.name, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
                        modifier = Modifier.height(if (compact) 44.dp else 66.dp).fillMaxWidth(0.55f))
                    else FullText(ctx.name, color = Color.White, fontSize = if (compact) 24.sp else 32.sp, fontWeight = FontWeight.Black, maxLines = 2)
                }
            }
            Row(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 10.dp)) {
                Column(Modifier.weight(1.25f).fillMaxHeight()) {
                    // The line of facts, the "mastered" green like a match score.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (ctx.total > 0 && ctx.earned >= ctx.total) { Text("MASTERED", color = Color(0xFF46D369), fontSize = 13.sp, fontWeight = FontWeight.Black); Spacer(Modifier.width(10.dp)) }
                        FullText(listOfNotNull(ctx.year, ctx.console, ctx.genres?.substringBefore(",")).joinToString("   "), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    if (ctx.page == WinPage.MAIN) {
                        val play = ctx.buttons.filter { it.primary }
                        val rest = ctx.buttons.filter { !it.primary && !it.back } + ctx.tools
                        val back = ctx.buttons.filter { it.back }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            play.forEach { a ->
                                SceneControl(a, Modifier) { on ->
                                    Row(
                                        Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = if (on) 1f else 0.9f))
                                            .then(if (on) Modifier.border(2.dp, accent, RoundedCornerShape(6.dp)) else Modifier)
                                            .padding(horizontal = 22.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(a.icon, null, tint = Color.Black, modifier = Modifier.size(22.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(a.label, color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        // The other actions as round keys with their names under them (as a streaming app's "My list", "Rate").
                        // One line of keys that scrolls sideways: the key you move to is brought into view, so every action is
                        // reachable however many a game has; each key is as wide as its name, which stays whole on one line.
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (rest + back).forEach { a ->
                                SceneControl(a, Modifier.widthIn(min = if (compact) 58.dp else 66.dp)) { on ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            Modifier.size(if (compact) 38.dp else 44.dp).clip(CircleShape)
                                                .background(if (on) Color.White else Color(0xFF2A2A2A))
                                                .border(2.dp, if (a.urgent) accent else if (on) Color.White else Color.White.copy(alpha = 0.45f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) { Icon(a.icon, null, tint = if (on) Color.Black else Color.White, modifier = Modifier.size(20.dp)) }
                                        Spacer(Modifier.height(3.dp))
                                        Text(a.label, color = Color.White.copy(alpha = if (on) 1f else 0.72f), fontSize = 10.sp, softWrap = false,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    }
                                }
                            }
                        }
                    } else {
                        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.05f))) { ctx.pageBody(Modifier.fillMaxSize()) }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ctx.buttons.forEach { a ->
                                SceneControl(a, Modifier) { on ->
                                    Text(a.label, color = if (on) Color.Black else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(if (on) Color.White else Color(0xFF333333)).padding(horizontal = 14.dp, vertical = 6.dp))
                                }
                            }
                        }
                    }
                }
                if (ctx.page == WinPage.MAIN && !compact) {
                    Spacer(Modifier.width(18.dp))
                    // The story and the achievements, as the side column of the page.
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        val s = rememberScrollState()
                        Column(Modifier.weight(1f).sceneFadeEdges(s).verticalScroll(s)) {
                            ctx.summary?.takeIf { it.isNotBlank() }?.let { Text(it, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, lineHeight = 19.sp) }
                            Spacer(Modifier.height(8.dp))
                            Text(listOfNotNull(ctx.developer?.let { "Developer: $it" }, ctx.genres?.let { "Genres: $it" }).joinToString("\n"),
                                color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, lineHeight = 17.sp)
                        }
                        Text("Played " + ctx.played + (ctx.last?.let { "  ·  last $it" } ?: ""), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                        if (ctx.total > 0) {
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.18f))) {
                                Box(Modifier.fillMaxHeight().fillMaxWidth(ctx.earned.toFloat() / ctx.total).background(accent))
                            }
                            Text("${ctx.earned} of ${ctx.total} achievements", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Arctic info drawer
// ---------------------------------------------------------------------------------------------------------------------

@Composable
internal fun ArcticDrawer(ctx: WinSceneCtx) {
    val w = minOf(ctx.maxW, 900.dp)
    val h = minOf(ctx.maxH, 560.dp)
    val compact = h < 400.dp
    val accent = ctx.accent
    Box(Modifier.width(w).height(h)) {
        // The poster you picked, standing on the left as the drawer comes out past it.
        Box(
            Modifier.align(Alignment.CenterStart).fillMaxHeight(0.78f).aspectRatio(0.68f).graphicsLayer {
                val k = ctx.reveal().coerceIn(0f, 1f)
                translationX = (1f - k) * 40.dp.toPx()
                alpha = k
                shape = RoundedCornerShape(12.dp); clip = true
                shadowElevation = 18.dp.toPx() * k
            }.background(Color(0xFF1B2633))
        ) {
            ctx.cover?.let { AsyncImage(rememberStyleArtRequest(it), ctx.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        // The drawer.
        Column(
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.66f).graphicsLayer {
                val k = ctx.reveal().coerceIn(0f, 1f)
                val e = 1f - (1f - k) * (1f - k)
                translationX = (1f - e) * size.width * 0.9f
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp); clip = true
            }.background(Brush.horizontalGradient(listOf(Color(0xF2101A26), Color(0xFA0B121B))))
                .padding(horizontal = 22.dp, vertical = 16.dp)
        ) {
            if (ctx.logo != null) AsyncImage(rememberStyleArtRequest(ctx.logo, trim = true), ctx.name, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
                modifier = Modifier.height(if (compact) 36.dp else 52.dp).fillMaxWidth())
            else FullText(ctx.name, color = Color.White, fontSize = if (compact) 20.sp else 26.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            Text(homeWinPageName(ctx.page).uppercase(), color = accent, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            Box(Modifier.padding(vertical = 8.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))
            if (ctx.page == WinPage.MAIN) {
                Row(Modifier.weight(1f)) {
                    // The actions as a media-centre list.
                    val s = rememberScrollState()
                    Column(Modifier.weight(0.9f).fillMaxHeight().sceneFadeEdges(s).verticalScroll(s), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        (ctx.buttons.filter { !it.back } + ctx.tools + ctx.buttons.filter { it.back }).forEach { a ->
                            SceneControl(a, Modifier.fillMaxWidth()) { on ->
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                        .background(if (on) accent else Color.Transparent).padding(horizontal = 12.dp, vertical = if (compact) 6.dp else 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(a.icon, null, tint = if (on) Color.Black else if (a.urgent) accent else Color.White.copy(alpha = 0.85f), modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(12.dp))
                                    FullText(a.label, color = if (on) Color.Black else Color.White, fontSize = 14.sp, fontWeight = if (a.primary || on) FontWeight.Bold else FontWeight.Medium)
                                }
                            }
                        }
                    }
                    if (!compact) {
                        Spacer(Modifier.width(18.dp))
                        // The info list and the plot.
                        val s2 = rememberScrollState()
                        Column(Modifier.weight(1.1f).fillMaxHeight().sceneFadeEdges(s2).verticalScroll(s2)) {
                            @Composable fun line(k: String, v: String?) {
                                if (v.isNullOrBlank()) return
                                Row(Modifier.padding(vertical = 2.dp)) {
                                    Text(k, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.width(92.dp))
                                    Text(v, color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                            line("System", ctx.console)
                            line("Released", ctx.year)
                            line("Developer", ctx.developer)
                            line("Genre", ctx.genres)
                            line("Played", ctx.played)
                            line("Last played", ctx.last)
                            if (ctx.total > 0) line("Achievements", "${ctx.earned} / ${ctx.total}")
                            ctx.summary?.takeIf { it.isNotBlank() }?.let {
                                Spacer(Modifier.height(8.dp))
                                Text(it, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, lineHeight = 19.sp)
                            }
                        }
                    }
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth()) { ctx.pageBody(Modifier.fillMaxSize()) }
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ctx.buttons.forEach { a ->
                        SceneControl(a, Modifier) { on ->
                            Text(a.label, color = if (on) Color.Black else Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) accent else Color.White.copy(alpha = 0.08f)).padding(horizontal = 14.dp, vertical = 7.dp))
                        }
                    }
                }
            }
        }
    }
}
