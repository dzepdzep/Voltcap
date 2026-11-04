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
                // (Update 469: the window-wide backdrop at the screen's size — rememberBackdropRequest — not the 360–720 px card decode.)
                if (art != null) AsyncImage(
                    if (art == ctx.cover) rememberStyleArtRequest(art) else rememberBackdropRequest(art), null, Modifier.fillMaxSize().graphicsLayer {
                        val k = ctx.reveal().coerceIn(0f, 1f); val z = 1.14f - 0.14f * k; scaleX = z; scaleY = z
                    }, contentScale = ContentScale.Crop
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.55f to Color(0xFF181818).copy(alpha = 0.35f), 1f to Color(0xFF181818))))
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xFF181818).copy(alpha = 0.7f), 0.5f to Color.Transparent)))
                Column(Modifier.align(Alignment.BottomStart).padding(start = 28.dp, bottom = 10.dp, end = 28.dp).graphicsLayer {
                    val k = ctx.reveal().coerceIn(0f, 1f); translationY = (1f - k) * 30.dp.toPx(); alpha = k
                }) {
                    LogoOrName(ctx.logo, ctx.name, Modifier.height(if (compact) 44.dp else 66.dp).fillMaxWidth(0.55f)) { StableFullText(ctx.name, color = Color.White, fontSize = if (compact) 24.sp else 32.sp, fontWeight = FontWeight.Black, maxLines = 2) }
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
                                        // (v0.6.66 popup polish: 10 sp at 72 % was hard to read on the GT78's small screen.)
                                        Text(a.label, color = Color.White.copy(alpha = if (on) 1f else 0.86f), fontSize = if (compact) 12.sp else 11.sp,
                                            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, softWrap = false,
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
                        // (Update 428, D107: reachable with the D-pad — it only scrolled by touch.)
                        Column(Modifier.weight(1f).sceneReadable(s, ctx.accent).sceneFadeEdges(s).verticalScroll(s)) {
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
    // (Update 469, D213: with larger text (1.3×) on a short screen — the KONKR, 427 dp — the eight action rows didn't fit beside
    // the facts and Back was cut. Then the actions take the drawer's whole width in two columns; the facts stay on Details.)
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    // (Recheck at 1.3× on the KONKR: its drawer is compact (< 400 dp) — the first fix tested !compact and missed it; Back was
    // still cut by the list's scroll. Compact or not, larger text on a short drawer takes the two columns.)
    val big = fontScale > 1.1f && h < 480.dp
    // (Two columns only where each gets ~150 dp: beside the inspected case the drawer is ~230 dp and the words were cut
    // ("ls D", "syn"). There one column with tighter rows — eight rows of 1.3× text fit a 400 dp drawer.)
    val tight = big && (w * 0.66f - 44.dp) >= 300.dp
    // (Also a compact drawer whose rows don't fit at the normal height — the Odin at display size larger, its Performance key
    // in the list: Back cut by 12 px. Rows ~ line + 12 dp padding + 2 dp gap; the logo, title and rule take ~130 dp.)
    val lineDp = with(androidx.compose.ui.platform.LocalDensity.current) { 18.sp.toDp() }
    val rowsNeed = (lineDp + 14.dp) * (ctx.buttons.size + ctx.tools.size)
    val dense = (big && !tight) || (compact && rowsNeed > h - 130.dp)
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
            LogoOrName(ctx.logo, ctx.name, Modifier.height(if (compact) 36.dp else 52.dp).fillMaxWidth()) { StableFullText(ctx.name, color = Color.White, fontSize = if (compact) 20.sp else 26.sp, fontWeight = FontWeight.Bold, maxLines = 2) }
            Text(homeWinPageName(ctx.page).uppercase(), color = accent, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            Box(Modifier.padding(vertical = 8.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))
            if (ctx.page == WinPage.MAIN) {
                Row(Modifier.weight(1f)) {
                    // The actions as a media-centre list.
                    val s = rememberScrollState()
                    val acts = ctx.buttons.filter { !it.back } + ctx.tools + ctx.buttons.filter { it.back }
                    @Composable fun actionRow(a: WinAction, m: Modifier) {
                            SceneControl(a, m) { on ->
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                        .background(if (on) accent else Color.Transparent).padding(horizontal = 12.dp, vertical = if (dense) 3.dp else if (compact || tight || h < 500.dp) 6.dp else 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(a.icon, null, tint = if (on) Color.Black else if (a.urgent) accent else Color.White.copy(alpha = 0.85f), modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(12.dp))
                                    // (Update 467: its own line height — the theme's 24 sp line made every row 36 dp and pushed Back
                                    // half off the drawer on the KONKR.)
                                    FullText(a.label, color = if (on) Color.Black else Color.White, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = if (a.primary || on) FontWeight.Bold else FontWeight.Medium)
                                }
                            }
                    }
                    if (tight) {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            acts.chunked(2).forEach { pair ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    pair.forEach { a -> actionRow(a, Modifier.weight(1f)) }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    } else Column(Modifier.weight(0.9f).fillMaxHeight().sceneFadeEdges(s).verticalScroll(s), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        acts.forEach { a -> actionRow(a, Modifier.fillMaxWidth()) }
                    }
                    if (!compact && !tight) {
                        Spacer(Modifier.width(18.dp))
                        // The info list and the plot.
                        val s2 = rememberScrollState()
                        // (Update 428, D107: the plot reachable with the D-pad — its last rows were cut for a controller.)
                        Column(Modifier.weight(1.1f).fillMaxHeight().sceneReadable(s2, accent).sceneFadeEdges(s2).verticalScroll(s2)) {
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
