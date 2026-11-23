package com.shelf.launcher.ui

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/*
 * P2 batch 2 (682o): the Cinema window's own pages. The window is a streaming service's title page (the backdrop, the lit
 * premiere edge, the big white Play), so its pages are that service's own screens, in its dark grey and Cinema's colour:
 *  - Details      = MORE INFO: "About <game>" - the credits as the service's grey-label lines (Made by, Released, Genres ...),
 *                   a rating-style plate with the console, the story as the synopsis (a D-pad stop that scrolls).
 *  - Achievements = EPISODES: each achievement an episode row - its number, the badge as the still, the title, its points as the
 *                   running time, a progress bar under the still (full in Cinema's colour = earned, empty = to get), A opens
 *                   the episode's synopsis; the order as the service's season picker ("SORTED: ...").
 *  - Options      = MY LIST & MORE: each option a row with the service's round outlined key; one that removes something is red
 *                   and asks for a second A (NativeConfirm).
 * Motion: the next screen fades up out of a slight zoom, the last one fades out (a streaming app's screen change).
 */

/** The page's name on the title line's lit edge. Pure. */
internal fun cinemaPageName(p: WinPage): String = when (p) {
    WinPage.MAIN -> "OVERVIEW"
    WinPage.DETAILS -> "MORE INFO"
    WinPage.TROPHIES -> "EPISODES"
    WinPage.OPTIONS -> "MY LIST & MORE"
    WinPage.PERF -> "PERFORMANCE"
}

/** An episode's number as the service prints it: "1", "2" ... for the list in the chosen order. Pure. */
internal fun cinemaEpisodeNo(index: Int): String = "${index + 1}"

/** An episode's "running time": its points ("10 pts"), or "-" without. Pure. */
internal fun cinemaRuntime(points: Int?): String = points?.let { "$it pts" } ?: "-"

internal fun cinemaMove(@Suppress("UNUSED_PARAMETER") dir: Int): ContentTransform =
    (fadeIn(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) + scaleIn(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing), initialScale = 0.97f)) togetherWith
        fadeOut(tween(120))

private val CINEMA_LABEL = Color(0xFF8C8C8C)
private val CINEMA_PAGE_RED = Color(0xFFE50914)

/** One of Cinema's own screens (the overview is the title page itself, drawn by CinemaPremiere). */
@Composable
internal fun AnimatedVisibilityScope.CinemaPage(ctx: WinSceneCtx, p: WinPage, live: Boolean, accent: Color, modifier: Modifier) {
    when (p) {
        WinPage.DETAILS -> CinemaMoreInfo(ctx, live, accent, modifier)
        WinPage.TROPHIES -> CinemaEpisodes(ctx, live, accent, modifier)
        WinPage.OPTIONS -> CinemaMyList(ctx, live, accent, modifier)
        WinPage.MAIN -> {}
        else -> ctx.pageFor(p, modifier, live) // (Performance: the shared page - not a screen of the service yet)
    }
}

@Composable
private fun CinemaMoreInfo(ctx: WinSceneCtx, live: Boolean, accent: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    Column(modifier.nativeScroll(Color.White)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("About ", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            WinFullText(ctx.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 2, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        // The rating-style plate: the console in an outlined box, as a maturity rating sits.
        Text(ctx.console.uppercase(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
            modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.6f)).padding(horizontal = 5.dp, vertical = 1.dp))
        Spacer(Modifier.height(6.dp))
        // The credits: grey label, white value - the service's "Cast:" / "Genres:" lines.
        nativeFacts(ctx, setOf("Trophies", "Console")).forEach { f ->
            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                Text(f.label + ":", color = CINEMA_LABEL, fontSize = 12.sp, modifier = Modifier.width(110.dp))
                Text(f.value, color = Color.White, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(8.dp))
        // The synopsis - the page's first D-pad stop.
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            Text(ctx.summary ?: "No synopsis for this title yet.", color = Color.White.copy(alpha = 0.88f), fontSize = 13.sp, lineHeight = 19.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).then(if (on) Modifier.border(2.dp, Color.White, RoundedCornerShape(4.dp)) else Modifier)
                    .padding(8.dp))
        }
    }
}

@Composable
private fun CinemaEpisodes(ctx: WinSceneCtx, live: Boolean, accent: Color, modifier: Modifier) {
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    Column(modifier) {
        // The season picker: the order, as the service's dark drop-down; A steps it.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Episodes", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("${t.earned} of ${t.total} watched", color = CINEMA_LABEL, fontSize = 12.sp, modifier = Modifier.weight(1f))
            // (682r, RP Classic x1.3: unbounded, the order key took the row and the title beside it went to 0 px wide, one letter a
            // line, pushing the list off the page - the key gets at most its share, as the TV's)
            NativeItem(onClick = t.nextOrder, Modifier.weight(1f, fill = false), focus = focus, live = live) { on ->
                Text("Sorted: " + t.order.label + "  ▾", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(Color(0xFF242424))
                        .border(if (on) 2.dp else 1.dp, if (on) Color.White else Color.White.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        NativeRows(Color.White, Modifier.weight(1f).fillMaxWidth(), 2.dp) {
            itemsIndexed(t.all, key = { _, it -> it.id }) { i, ach ->
                val earned = ach.isUnlocked
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    vcTraceC("VC:cinemaRow") { NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { on ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(if (on) Color(0xFF333333) else Color.Transparent)
                            .padding(horizontal = 6.dp, vertical = 5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(cinemaEpisodeNo(i), color = CINEMA_LABEL, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                                // The still with its progress bar under it (full = earned).
                                Column(Modifier.width(52.dp)) {
                                    Box(Modifier.size(52.dp, 32.dp).clip(RoundedCornerShape(2.dp)).background(Color.Black)) {
                                        NativeBadgeMark(ach.title, earned)
                                        vcTraceC("VC:badgeImg") { AsyncImage(com.shelf.launcher.data.RaBadges.url(ach.badgeName), ach.title, contentScale = ContentScale.Crop,
                                            colorFilter = if (earned) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
                                            alpha = if (earned) 1f else 0.6f, modifier = Modifier.fillMaxSize()) }
                                    }
                                    Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0xFF4D4D4D))) {
                                        if (earned) Box(Modifier.fillMaxSize().background(if (accent == Color.Unspecified) CINEMA_PAGE_RED else accent))
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(ach.title ?: "Untitled", color = Color.White, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                                    lineHeight = 16.sp, modifier = Modifier.weight(1f))
                                Text(cinemaRuntime(ach.points), color = CINEMA_LABEL, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
                            }
                            ach.description?.let { d ->
                                if (opened) Text(d, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(start = 90.dp, top = 3.dp))
                                else WinFullText(d, color = CINEMA_LABEL, fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp, modifier = Modifier.padding(start = 90.dp, top = 3.dp))
                            }
                            if (opened) {
                                val facts = listOfNotNull(
                                    (ach.dateEarnedHardcore ?: ach.dateEarned)?.let { "Watched " + it.take(10) },
                                    ach.unlockPercent?.let { "%.1f %% of players".format(it) },
                                    ach.type?.takeIf { it.isNotBlank() }
                                )
                                if (facts.isNotEmpty()) Text(facts.joinToString("  ·  "), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 90.dp, top = 2.dp))
                            }
                        }
                    } }
                }
            }
            if (t.all.isEmpty()) item { Text("No episodes - this title has no achievements.", color = CINEMA_LABEL, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun CinemaMyList(ctx: WinSceneCtx, live: Boolean, accent: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(Color.White), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("My List & more", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp))
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(), focus = if (i == 0) focus else null, live = live) { on ->
                androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                val armed = confirm.armed == i
                val ink = if (o.destructive) CINEMA_PAGE_RED else Color.White
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(if (on) Color(0xFF333333) else Color.Transparent)
                    .padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    // The service's round outlined key (filled white while it has the focus), a plus / a cross mark.
                    Box(Modifier.size(30.dp).clip(CircleShape).background(if (on) Color.White else Color.Transparent)
                        .border(2.dp, if (on) Color.White else Color.White.copy(alpha = 0.55f), CircleShape), contentAlignment = Alignment.Center) {
                        Text(if (o.destructive) "✕" else "+", color = if (on) Color.Black else ink, fontSize = 15.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        WinFullText(o.label, color = ink, fontSize = 14.sp, maxLines = 2, fontWeight = FontWeight.Bold, lineHeight = 18.sp)
                        o.sub?.let { WinFullText(it, color = CINEMA_LABEL, fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp) }
                        // (682v, owner 18 Nov: the confirmation readable and spaced - 12 sp on 16, a 6 dp gap above, room inside its box)
                        if (armed) Text("Press A again to confirm  ·  move away to cancel", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            lineHeight = 16.sp, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(3.dp)).background(CINEMA_PAGE_RED)
                                .padding(horizontal = 6.dp, vertical = 3.dp))
                    }
                }
            }
        }
        if (ctx.options.isEmpty()) Text("Nothing more for this title.", color = CINEMA_LABEL, fontSize = 12.sp)
    }
}


/** 682ag: Cinema's opening scene without a picture - a studio title card: the game's colour, a projector's beam from the top
 *  corner, the console's initials large and faint, its name in spaced capitals. Decorative (no semantics: the title line
 *  under it names the game). */
@Composable
internal fun CinemaNoArtBand(ctx: WinSceneCtx, modifier: Modifier) {
    Box(modifier.background(placeholderBrush(ctx.g.hue)), contentAlignment = Alignment.CenterEnd) {
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            val beam = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * 0.92f, 0f); lineTo(size.width * 0.18f, size.height); lineTo(size.width * 0.62f, size.height); close()
            }
            drawPath(beam, androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.02f))))
            // the film's grain: a few faint scratches
            for (i in 0 until 5) {
                val x = size.width * (0.12f + i * 0.19f)
                drawLine(Color.White.copy(alpha = 0.05f), androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x + 6f, size.height), 1f)
            }
        }
        Column(Modifier.padding(end = 28.dp).clearAndSetSemantics { }, horizontalAlignment = Alignment.End) {
            Text(studioInitials(ctx.console), color = Color.White.copy(alpha = 0.22f), fontSize = 44.sp, fontWeight = FontWeight.Black, maxLines = 1, softWrap = false) // qa-ok: TRUNCATE-CLIP (2-4 initials, a decorative watermark)
            Text(ctx.console.uppercase(), color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
        }
    }
}
