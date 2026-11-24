package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * P2 (728a, class C): the Timeline window's own pages. The window is an entry hanging from the year line (TimelineHang), so its
 * pages are read along a line of time, each one drawn as its own small vertical timeline in the entry card:
 *  - Info         = ENTRY: the cover, name and facts as before (TimelineHang, unchanged).
 *  - Details      = CHRONICLE: the facts as dated stops down a thin line in the accent (a node per fact, label over value in
 *                   whole words), the story as THE STORY SO FAR at the line's end (a D-pad stop that scrolls).
 *  - Achievements = MILESTONES: each achievement a stop on the line - a filled node and "Reached <date>" when you have it, an
 *                   open ring and "Ahead" when not, its points on the right; "N of M milestones reached" and the order as a
 *                   pill; A opens its whole text and how rare it is.
 *  - Options      = BRANCHES: each option a branch off the line (a short tick to a rounded tag); one that removes something is
 *                   red and asks for a second A (NativeConfirm).
 * Heading: the page's name beside the page line - one tick per page on a short line, the shown one filled - after the old page
 * has gone. Motion: the next page comes along the line of time - in from the right going forward, from the left going back, a
 * fifth of the way, as it fades up; none with Motion Off. Shared: data, focus, B, Play-first, inert outgoing copy (NativePageKit).
 */

/** The Timeline's name for a page. Pure. */
internal fun timePageName(p: WinPage): String = when (p) {
    WinPage.MAIN -> "ENTRY"
    WinPage.DETAILS -> "CHRONICLE"
    WinPage.TROPHIES -> "MILESTONES"
    WinPage.OPTIONS -> "BRANCHES"
    WinPage.PERF -> "PERFORMANCE"
}

/** A milestone's line under its title: "Reached <date>" / "Reached" when yours, "Ahead" when still to get. Pure. */
internal fun timeMilestoneWhen(earned: Boolean, date: String?): String =
    if (!earned) "Ahead" else date?.takeIf { it.isNotBlank() }?.let { "Reached $it" } ?: "Reached"

/** The milestones' count line. Pure. */
internal fun timeMilestoneCount(earned: Int, total: Int): String =
    if (total == 1) "$earned of 1 milestone reached" else "$earned of $total milestones reached"

/** An achievement's points at its stop: "N pts" (1 pt), nothing without points. Pure. */
internal fun timePoints(points: Int?): String? = points?.let { if (it == 1) "1 pt" else "$it pts" }

internal fun timeMove(dir: Int): ContentTransform =
    (fadeIn(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) +
        slideInHorizontally(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) { w -> if (dir >= 0) w / 5 else -w / 5 }) togetherWith
        fadeOut(tween(NATIVE_OUT_MS))

private val TIME_RED = Color(0xFFE5534B)
private val TIME_INK = Color.White
private fun inkOnTime(c: Color) = if (c.luminance() > 0.45f) Color(0xFF15171C) else Color.White

/** The page line beside the heading: one tick per page, the shown one filled and larger. */
@Composable
internal fun TimePageLine(tabs: List<WinPage>, shown: WinPage, a: Color) {
    val n = tabs.size.coerceAtLeast(1)
    val at = tabs.indexOf(shown)
    Canvas(Modifier.width((n * 14).dp).height(10.dp)) {
        val y = size.height / 2
        val step = size.width / n
        drawLine(a.copy(alpha = 0.4f), Offset(step / 2, y), Offset(size.width - step / 2, y), 1.5.dp.toPx())
        for (i in 0 until n) {
            val c = Offset(step * i + step / 2, y)
            if (i == at) drawCircle(a, 4.dp.toPx(), c) else drawCircle(a.copy(alpha = 0.7f), 2.5.dp.toPx(), c, style = Stroke(1.2.dp.toPx()))
        }
    }
}

/** One of the entry's pages (Info is the entry itself, drawn by TimelineHang). */
@Composable
internal fun AnimatedVisibilityScope.TimePage(ctx: WinSceneCtx, p: WinPage, live: Boolean, a: Color, modifier: Modifier) {
    when (p) {
        WinPage.DETAILS -> TimeChronicle(ctx, live, a, modifier)
        WinPage.TROPHIES -> TimeMilestones(ctx, live, a, modifier)
        WinPage.OPTIONS -> TimeBranches(ctx, live, a, modifier)
        WinPage.MAIN -> {}
        else -> ctx.pageFor(p, modifier, live) // (Performance: the shared page - not on this timeline yet)
    }
}

/** A stop on a page's line: the line runs through the node's column, [node] draws the stop, [content] sits right of it. */
@Composable
private fun TimeStop(a: Color, first: Boolean, last: Boolean, node: @Composable () -> Unit, content: @Composable () -> Unit) {
    // (the line is drawn behind the row, so the row needs no intrinsic measuring - its texts lay out once)
    Row(Modifier.fillMaxWidth().drawBehind {
        val x = 11.dp.toPx()
        val top = if (first) 10.dp.toPx() else 0f
        val bottom = if (last) 10.dp.toPx() else size.height
        if (bottom > top) drawLine(a.copy(alpha = 0.45f), Offset(x, top), Offset(x, bottom), 2.dp.toPx())
    }) {
        Box(Modifier.width(22.dp).padding(top = 4.dp), contentAlignment = Alignment.TopCenter) { node() }
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun TimeDot(a: Color, filled: Boolean, size: Int = 12) =
    Canvas(Modifier.size(size.dp)) {
        if (filled) drawCircle(a) else { drawCircle(Color(0xFF1A1D23)); drawCircle(a, style = Stroke(2.dp.toPx())) }
    }

@Composable
private fun TimeChronicle(ctx: WinSceneCtx, live: Boolean, a: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val facts = nativeFacts(ctx, setOf("Trophies"))
    Column(modifier.nativeScroll(a)) {
        facts.forEachIndexed { i, f ->
            TimeStop(a, first = i == 0, last = false, node = { TimeDot(a, filled = true, size = 10) }) {
                Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    Text(f.label.uppercase(), color = a, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, lineHeight = 13.sp)
                    WholeWordsText(f.value, color = TIME_INK, fontSize = 13.sp, lineHeight = 17.sp, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        TimeStop(a, first = facts.isEmpty(), last = true, node = { TimeDot(a, filled = false) }) {
            Column(Modifier.fillMaxWidth()) {
                Text("THE STORY SO FAR", color = a, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, lineHeight = 13.sp)
                Spacer(Modifier.height(3.dp))
                NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
                    Text(ctx.summary ?: "No story on this entry yet.", color = TIME_INK.copy(alpha = 0.88f), fontSize = 12.sp, lineHeight = 17.sp,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(a.copy(alpha = if (on) 0.16f else 0.06f))
                            .border(if (on) 2.dp else 1.dp, if (on) a else a.copy(alpha = 0.3f), RoundedCornerShape(8.dp)).padding(8.dp))
                }
            }
        }
    }
}

@Composable
private fun TimeMilestones(ctx: WinSceneCtx, live: Boolean, a: Color, modifier: Modifier) {
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(timeMilestoneCount(t.earned, t.total), color = TIME_INK.copy(alpha = 0.75f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                lineHeight = 14.sp, modifier = Modifier.weight(1f))
            // (the order pill gets at most its share, as the Classic panel's - 682r: unbounded it took the row)
            NativeItem(onClick = t.nextOrder, Modifier.weight(1f, fill = false), focus = focus, live = live) { on ->
                Text("Order: " + t.order.label, color = if (on) inkOnTime(a) else a, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) a else Color.Transparent)
                        .border(1.dp, a, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        NativeRows(a, Modifier.weight(1f).fillMaxWidth()) {
            nativeItems(t.all, { it.id }) { i, ach ->
                val earned = ach.isUnlocked
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    TimeStop(a, first = i == 0, last = i == t.all.size - 1, node = { TimeDot(a, filled = earned) }) {
                        NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { on ->
                            Column(Modifier.fillMaxWidth().padding(bottom = 4.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (on) a.copy(alpha = 0.16f) else Color.Transparent)
                                .then(if (on) Modifier.border(1.dp, a, RoundedCornerShape(8.dp)) else Modifier).padding(horizontal = 6.dp, vertical = 4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    NativeBadge(ach.badgeName, ach.title, earned, a, Modifier.size(26.dp), shape = RoundedCornerShape(6.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        WholeWordsText(ach.title ?: "Untitled", color = if (earned) TIME_INK else TIME_INK.copy(alpha = 0.6f), fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold, lineHeight = 16.sp)
                                        Text(timeMilestoneWhen(earned, ach.dateEarned), color = if (earned) a else TIME_INK.copy(alpha = 0.5f), fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold, lineHeight = 13.sp)
                                    }
                                    timePoints(ach.points)?.let {
                                        Text(it, color = TIME_INK.copy(alpha = if (earned) 0.85f else 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                            softWrap = false, modifier = Modifier.padding(start = 6.dp))
                                    }
                                }
                                ach.description?.let { d ->
                                    if (opened) Text(d, color = TIME_INK.copy(alpha = 0.85f), fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(start = 34.dp, top = 2.dp))
                                    else WinFullText(d, color = TIME_INK.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1, lineHeight = 14.sp, modifier = Modifier.padding(start = 34.dp, top = 1.dp))
                                }
                                if (opened) ach.unlockPercent?.let { pc ->
                                    Text("%.1f %% of players reached this".format(pc), color = a.copy(alpha = 0.9f), fontSize = 10.sp,
                                        modifier = Modifier.padding(start = 34.dp, top = 1.dp))
                                }
                            }
                        }
                    }
                }
            }
            if (t.all.isEmpty()) item {
                Text("No milestones on this timeline.", color = TIME_INK.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun TimeBranches(ctx: WinSceneCtx, live: Boolean, a: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(a)) {
        ctx.options.forEachIndexed { i, o ->
            val ink = if (o.destructive) TIME_RED else a
            TimeStop(a, first = i == 0, last = i == ctx.options.size - 1, node = {
                // the branch: a short tick from the line toward the option's tag
                Canvas(Modifier.width(22.dp).height(14.dp)) {
                    val y = size.height / 2
                    drawCircle(ink, 3.dp.toPx(), Offset(size.width / 2, y))
                    drawLine(ink, Offset(size.width / 2, y), Offset(size.width + 6.dp.toPx(), y), 2.dp.toPx())
                }
            }) {
                NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    focus = if (i == 0) focus else null, live = live) { on ->
                    androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                    val armed = confirm.armed == i
                    Column(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(if (on) ink.copy(alpha = 0.2f) else TIME_INK.copy(alpha = 0.05f))
                        .border(if (on) 2.dp else 1.dp, if (on) ink else ink.copy(alpha = 0.4f), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        WinFullText(o.label, color = if (o.destructive) TIME_RED else TIME_INK, fontSize = 13.sp, maxLines = 2, fontWeight = FontWeight.Bold, lineHeight = 16.sp)
                        o.sub?.let { WinFullText(it, color = TIME_INK.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp) }
                    }
                    if (armed) Text("Press A again to confirm  ·  move away to cancel", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).background(TIME_RED).padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
        }
        if (ctx.options.isEmpty()) Text("No branches from this entry.", color = TIME_INK.copy(alpha = 0.6f), fontSize = 12.sp)
    }
}
