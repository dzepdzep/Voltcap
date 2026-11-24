package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideIn
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * P2 (735a, class C): Airy home's own pages. The window is the floating white panel of 1999 - soft rounded corners, a pale
 * blue-grey well, the cover in a rounded frame, the name in a light face, the little pixel LCD and big round keys with their
 * word under them. Its pages take the panel's right column, drawn in the same soft language:
 *  - Info         = GAME: the panel as before.
 *  - Details      = ABOUT: each fact a soft rounded bubble (small accent label, the value in the dark ink), then the story in a
 *                   white speech bubble with its little tail (a D-pad stop).
 *  - Achievements = STARS: "★ N of M" over a row of bubbles filled in the accent for what you have, the order a round pill;
 *                   each achievement a white rounded card - its badge in a round bubble, the title, "Got it <date>" /
 *                   "Still to get", the points in a small round accent bubble; A opens the whole text and how rare it is.
 *  - Options      = SETTINGS: big round-ended pills with a round dot at the start (filled in the accent when lit); one that
 *                   removes something is soft red and asks for a second A (NativeConfirm).
 * Heading: the page's name in the light face and a dot per page (the shown one a longer accent pill) - after the old page has
 * gone. Motion: the next page floats up like a bubble (from below going forward, from above going back, from 97 %); none with
 * Motion Off. The round keys stay along the bottom (two page slots, Play the first).
 * Shared: data, focus, B, Play-first, inert outgoing copy (NativePageKit).
 */

/** The panel's name for a page. Pure. */
internal fun airyPageName(p: WinPage): String = when (p) {
    WinPage.MAIN -> "Game"
    WinPage.DETAILS -> "About"
    WinPage.TROPHIES -> "Stars"
    WinPage.OPTIONS -> "Settings"
    WinPage.PERF -> "Performance"
}

/** The count over the stars: "★ N of M" (earned never above the total). Pure. */
internal fun airyStarCount(earned: Int, total: Int): String = "★ ${earned.coerceIn(0, maxOf(total, 0))} of ${maxOf(total, 0)}"

/** A star's line: "Got it <date>" / "Got it" when yours, "Still to get" otherwise. Pure. */
internal fun airyStarLine(earned: Boolean, date: String?): String =
    if (!earned) "Still to get" else date?.takeIf { it.isNotBlank() }?.let { "Got it ${it.trim()}" } ?: "Got it"

/** The filled bubbles of the meter: at most [bubbles], in proportion beyond. Pure. */
internal fun airyFilledBubbles(earned: Int, total: Int, bubbles: Int = 12): Int = when {
    total <= 0 -> 0
    total <= bubbles -> earned.coerceIn(0, total)
    else -> kotlin.math.round(earned.coerceIn(0, total).toFloat() / total * bubbles).toInt()
}

internal fun airyMove(dir: Int): ContentTransform =
    (fadeIn(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) + scaleIn(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing), initialScale = 0.97f) +
        slideIn(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) { s -> IntOffset(0, if (dir >= 0) s.height / 10 else -s.height / 10) }) togetherWith
        fadeOut(tween(NATIVE_OUT_MS))

private val AhInk = Color(0xFF2D3440)
private val AhMuted = Color(0xFF55606F)
private val AhSoft = Color(0xFF7A8493)
private val AhLine = Color(0xFFDDE5EF)
private val AhWell = Color(0xFFE9EFF6)
private val AhRed = Color(0xFFE0525E)
private val AhCard = RoundedCornerShape(16.dp)
private val AhPill = RoundedCornerShape(50)

private fun ahOn(accent: Color) = if (accent.luminance() > 0.55f) AhInk else Color.White

/** The heading: the page's name in the light face, a dot per page (the shown one a longer accent pill). */
@Composable
internal fun AiryPageHeading(tabs: List<WinPage>, shown: WinPage, accent: Color, tight: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(bottom = if (tight) 2.dp else 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(airyPageName(shown), color = AhInk, fontSize = if (tight) 15.sp else 18.sp, fontWeight = FontWeight.Light, lineHeight = if (tight) 18.sp else 22.sp, maxLines = 1, softWrap = false)
        Spacer(Modifier.width(10.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEach { t ->
                val on = t == shown
                Box(Modifier.size(if (on) 18.dp else 7.dp, 7.dp).background(if (on) accent else AhLine, AhPill))
            }
        }
    }
}

/** One of the panel's pages (Game is the panel itself, drawn by AiryPanel). */
@Composable
internal fun AnimatedVisibilityScope.AiryPage(ctx: WinSceneCtx, p: WinPage, live: Boolean, accent: Color, modifier: Modifier) {
    when (p) {
        WinPage.DETAILS -> AiryAbout(ctx, live, accent, modifier)
        WinPage.TROPHIES -> AiryStars(ctx, live, accent, modifier)
        WinPage.OPTIONS -> AirySettings(ctx, live, accent, modifier)
        WinPage.MAIN -> {}
        else -> ctx.pageFor(p, modifier, live) // (Performance: the shared page - not in the panel yet)
    }
}

@Composable
private fun AiryAbout(ctx: WinSceneCtx, live: Boolean, accent: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    Column(modifier.nativeScroll(accent), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        nativeFacts(ctx).forEach { f ->
            Row(Modifier.fillMaxWidth().background(Color.White, AhPill).border(1.dp, AhLine, AhPill)
                .padding(horizontal = 14.dp, vertical = 5.dp), verticalAlignment = Alignment.Top) {
                WholeWordsText(f.label, color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, lineHeight = 14.sp,
                    modifier = Modifier.width(88.dp).padding(top = 2.dp))
                Spacer(Modifier.width(8.dp))
                WholeWordsText(f.value, color = AhInk, fontSize = 13.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(4.dp))
        // The story in a speech bubble, its tail at the top left (as if the cover were telling it).
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            Column(Modifier.fillMaxWidth().padding(top = 6.dp)
                .drawBehind {
                    val x = 26.dp.toPx(); val t = 7.dp.toPx()
                    val tail = androidx.compose.ui.graphics.Path().apply { moveTo(x - t, t + 1f); lineTo(x, 0f); lineTo(x + t, t + 1f); close() }
                    drawPath(tail, if (on) accent else Color.White)
                }
                .padding(top = 6.dp)
                .background(Color.White, AhCard).border(if (on) 2.dp else 1.dp, if (on) accent else AhLine, AhCard)
                .padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text("The story", color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, lineHeight = 14.sp)
                Text(ctx.summary ?: "No story for this game yet.", color = Color(0xFF66707E), fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun AiryStars(ctx: WinSceneCtx, live: Boolean, accent: Color, modifier: Modifier) {
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    val on = ahOn(accent)
    Column(modifier) {
        // (735a, x1.3: one line - the count, the bubble meter in the room between, the order pill at most its share; a two-line
        // header left the first star half a card on the RP Classic and the 640 x 427 window.)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(airyStarCount(t.earned, t.total), color = AhInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp,
                maxLines = 1, softWrap = false)
            // The bubble meter - a bubble per star (in proportion past a dozen), filled in the accent for the ones you have; as
            // many as the room between holds, never less than one per star shown in proportion.
            androidx.compose.foundation.Canvas(Modifier.weight(1f).height(10.dp).padding(horizontal = 8.dp)) {
                if (t.total <= 0) return@Canvas
                val d = 10.dp.toPx(); val gap = 3.dp.toPx()
                val n = minOf(t.total, 12, ((size.width + gap) / (d + gap)).toInt()).coerceAtLeast(1)
                val filled = airyFilledBubbles(t.earned, t.total, n)
                for (i in 0 until n) {
                    val c = Offset(i * (d + gap) + d / 2f, size.height / 2f)
                    if (i < filled) drawCircle(Brush.radialGradient(listOf(Color.White, accent), c - Offset(d * 0.2f, d * 0.2f), d * 0.75f), d / 2f, c)
                    else drawCircle(Brush.verticalGradient(listOf(Color.White, AhWell)), d / 2f, c)
                    drawCircle(if (i < filled) accent else AhLine, d / 2f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                }
            }
            // (the order pill gets at most its share - 682r; its words slide on one line, rule 11)
            NativeItem(onClick = t.nextOrder, Modifier.weight(1f, fill = false), focus = focus, live = live) { lit ->
                Box(Modifier.background(if (lit) accent else Color.White, AhPill)
                    .border(if (lit) 2.dp else 1.dp, if (lit) accent else AhLine, AhPill)
                    .padding(horizontal = 12.dp, vertical = 4.dp)) {
                    WinFullText("Order: " + t.order.label, color = if (lit) on else AhMuted, fontSize = 12.sp, maxLines = 1, lineHeight = 15.sp)
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        NativeRows(accent, Modifier.weight(1f).fillMaxWidth(), spacing = 4.dp) {
            nativeItems(t.all, { it.id }) { _, ach ->
                val earned = ach.isUnlocked
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { lit ->
                        Column(Modifier.fillMaxWidth()
                            .background(if (earned) Color.White else Color.White.copy(alpha = 0.6f), AhCard)
                            .border(if (lit) 2.dp else 1.dp, if (lit) accent else AhLine, AhCard)
                            .padding(horizontal = 10.dp, vertical = 5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                NativeBadge(ach.badgeName, ach.title, earned, accent, Modifier.size(30.dp), shape = CircleShape)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    WholeWordsText(ach.title ?: "Untitled", color = if (earned) AhInk else AhMuted, fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold, lineHeight = 16.sp)
                                    Text(airyStarLine(earned, ach.dateEarned), color = if (earned) accent else AhSoft, fontSize = 11.sp, lineHeight = 14.sp)
                                }
                                ach.points?.let { pts ->
                                    Box(Modifier.padding(start = 6.dp).background(if (earned) accent else AhWell, AhPill)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)) {
                                        Text("+$pts", color = if (earned) on else AhSoft, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, softWrap = false)
                                    }
                                }
                            }
                            ach.description?.let { d ->
                                if (opened) Text(d, color = Color(0xFF66707E), fontSize = 12.sp, lineHeight = 16.sp,
                                    modifier = Modifier.padding(start = 40.dp, top = 3.dp))
                                else WinFullText(d, color = AhSoft, fontSize = 11.sp, maxLines = 1, lineHeight = 14.sp,
                                    modifier = Modifier.padding(start = 40.dp, top = 1.dp))
                            }
                            if (opened) ach.unlockPercent?.let { pc ->
                                Text("%.1f %% of players have it".format(pc), color = accent, fontSize = 11.sp,
                                    modifier = Modifier.padding(start = 40.dp, top = 1.dp))
                            }
                        }
                    }
                }
            }
            if (t.all.isEmpty()) item {
                Text("No stars for this game yet.", color = AhMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun AirySettings(ctx: WinSceneCtx, live: Boolean, accent: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(accent), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(),
                focus = if (i == 0) focus else null, live = live) { lit ->
                androidx.compose.runtime.LaunchedEffect(lit) { if (!lit) confirm.leave(i) }
                val armed = confirm.armed == i
                val tone = if (o.destructive) AhRed else accent
                val ink = when { lit -> ahOn(tone); o.destructive -> AhRed; else -> AhInk }
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().background(if (lit) tone else Color.White, AhPill)
                        .border(if (lit) 2.dp else 1.dp, if (lit) tone else AhLine, AhPill)
                        .padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(if (lit) ink else tone.copy(alpha = 0.25f))
                            .border(1.dp, if (lit) ink else tone, CircleShape))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            WinFullText(o.label, color = ink, fontSize = 14.sp, maxLines = 2, lineHeight = 18.sp)
                            o.sub?.let { WinFullText(it, color = ink.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp) }
                        }
                    }
                    if (armed) Text("Press A again to remove  ·  move away to cancel", color = Color.White, fontSize = 12.sp, lineHeight = 16.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).background(AhRed, AhCard).padding(horizontal = 14.dp, vertical = 6.dp))
                }
            }
        }
        if (ctx.options.isEmpty()) Text("No settings for this game.", color = AhMuted, fontSize = 13.sp)
    }
}

/** The panel's page area: the heading, then the page, floated in by [airyMove], in the pale well. [tight]: a short panel (the
 *  KONKR, 640 x 427 at x1.3) - a smaller heading and closer edges, so the first star stays whole (AchFirstRow). */
@Composable
internal fun AiryPageArea(ctx: WinSceneCtx, accent: Color, modifier: Modifier, tight: Boolean = false) {
    val heading = rememberHeadingPage(ctx.page)
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(AhWell).padding(horizontal = 12.dp, vertical = if (tight) 6.dp else 8.dp)) {
        AiryPageHeading(nativeTabs(ctx.pages), if (heading == WinPage.MAIN) ctx.page else heading, accent, tight)
        NativePages(ctx, Modifier.fillMaxWidth().weight(1f), label = "airyPage", move = ::airyMove) { pg, pageLive ->
            AiryPage(ctx, pg, pageLive, accent, Modifier.fillMaxSize())
        }
    }
}
