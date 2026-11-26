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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * P2 (732a, class C): the File select layout's own pages. The window is the save file block you opened, turned over and grown
 * big - chunky, bevelled, in the block's own colour, its words in outlined capitals, yellow for what is lit, a dark inset panel
 * in its middle. Its pages are the file's own screens on that inset, in the same chunky look:
 *  - Info         = FILE: the file's back as before (FileCardBack).
 *  - Details      = DETAILS (its button's name): each fact a fat rounded slot - the label in yellow capitals, the value in
 *                   white - then the story in its own slot (a D-pad stop).
 *  - Achievements = STARS: the big star "× N / M" the file's top already shows, a row of little stars (yellow for each one you
 *                   have, outlined for each still to get; one stands for several on long lists), then each achievement a row - its
 *                   star, a round badge, the title in outlined capitals, "GOT IT <date>" / "NOT YET", "+N" points; A opens its
 *                   whole text and how rare it is. The order is a small round button like the file's tools.
 *  - Options      = OPTIONS: each option a fat round slot like the file's buttons (yellow when lit); one that removes something
 *                   is red and asks for a second A (NativeConfirm) - "ERASE?" as a save file asks.
 * Heading: the page's name in outlined capitals at the top (where the file said the shared page's name) and a round pip per
 * page, the shown one yellow - after the old page has gone. Motion: the next screen pops in on the inset - a little from the
 * right going forward, from the left going back, growing from 94 %; none with Motion Off.
 * Shared: data, focus, B, Play-first, inert outgoing copy (NativePageKit).
 */

/** The file's name for a page. Pure. */
internal fun filePageName(p: WinPage): String = when (p) {
    WinPage.MAIN -> "FILE"
    WinPage.DETAILS -> "DETAILS"
    WinPage.TROPHIES -> "STARS"
    WinPage.OPTIONS -> "OPTIONS"
    WinPage.PERF -> "PERFORMANCE"
}

/** The stars line on the STARS page: "N / M STARS" ("1 / 1 STAR"). Pure. */
internal fun fileStarsLine(earned: Int, total: Int): String =
    "${earned.coerceIn(0, maxOf(total, 0))} / $total " + if (total == 1) "STAR" else "STARS"

/** A star row's note: "GOT IT <date>" / "GOT IT" when yours, "NOT YET" still to get. Pure. */
internal fun fileEntryLine(earned: Boolean, date: String?): String =
    if (!earned) "NOT YET" else date?.takeIf { it.isNotBlank() }?.let { "GOT IT $it" } ?: "GOT IT"

/** A row's points: "+N", nothing without points. Pure. */
internal fun filePoints(points: Int?): String? = points?.let { "+$it" }

/** The most little stars the tally draws; past it one stands for several achievements (the Memory card's rule, [mcBlocks]). */
internal const val FILE_STARS_MAX = 24

internal fun fileMove(dir: Int): ContentTransform =
    (fadeIn(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) +
        scaleIn(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing), initialScale = 0.94f) +
        slideIn(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) { s -> IntOffset(if (dir >= 0) s.width / 14 else -s.width / 14, 0) }) togetherWith
        fadeOut(tween(NATIVE_OUT_MS))

/** The two colours mixed [t] of the way (FileCardBack's mix). */
private fun fileMix(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)

private val FileYellow = Color(0xFFFFE04A)
private val FileOnYellow = Color(0xFF2A2200)
private val FileRed = Color(0xFFE5483E)
private val FileOutline = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(2f, 2.5f), 2f))

/** A five-pointed star filling the draw area - filled for one you have, outlined for one still to get. */
private fun DrawScope.fileStar(color: Color, filled: Boolean, line: Float,
                               box: androidx.compose.ui.geometry.Size = size, at: Offset = Offset.Zero) {
    val c = Offset(at.x + box.width / 2, at.y + box.height / 2); val r = box.minDimension / 2 - if (filled) 0f else line / 2
    val p = Path()
    for (i in 0 until 10) {
        val a = Math.PI / 5 * i - Math.PI / 2
        val rr = if (i % 2 == 0) r else r * 0.45f
        val x = c.x + (rr * kotlin.math.cos(a)).toFloat(); val y = c.y + (rr * kotlin.math.sin(a)).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    if (filled) drawPath(p, color) else drawPath(p, color, style = Stroke(line))
}

/** The page's name in outlined capitals and a round pip per page (the shown one yellow) - the file's top row. */
@Composable
internal fun FilePageHeading(tabs: List<WinPage>, shown: WinPage, ink: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(filePageName(shown), color = ink, fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 1, softWrap = false,
            style = if (ink == Color.White) FileOutline else TextStyle())
        Spacer(Modifier.width(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEach { t ->
                val on = t == shown
                androidx.compose.foundation.layout.Box(Modifier.size(if (on) 10.dp else 8.dp).background(if (on) FileYellow else Color.Black.copy(alpha = 0.35f), CircleShape)
                    .border(2.dp, if (on) Color.White else Color.White.copy(alpha = 0.5f), CircleShape))
            }
        }
    }
}

/** One of the file's screens on its inset (FILE is the file's back itself, drawn by FileCardBack). */
@Composable
internal fun AnimatedVisibilityScope.FilePage(ctx: WinSceneCtx, p: WinPage, live: Boolean, base: Color, tight: Boolean, modifier: Modifier) {
    when (p) {
        WinPage.DETAILS -> FileDetails(ctx, live, base, modifier)
        WinPage.TROPHIES -> FileStars(ctx, live, base, tight, modifier)
        WinPage.OPTIONS -> FileOptions(ctx, live, base, modifier)
        WinPage.MAIN -> {}
        else -> ctx.pageFor(p, modifier, live) // (Performance: the shared page - not on the file yet)
    }
}

/** A fat rounded slot on the inset: the block's colour, darker; yellow with a white rim when lit (red for a removing one). */
private fun Modifier.fileSlot(on: Boolean, base: Color, red: Boolean = false): Modifier {
    val shape = RoundedCornerShape(16.dp)
    val fill = when { on && red -> FileRed; on -> FileYellow; else -> fileMix(base, Color.Black, 0.25f) }
    val rim = when { on -> Color.White; red -> FileRed.copy(alpha = 0.85f); else -> fileMix(base, Color.White, 0.35f) }
    return this.background(fill, shape).border(if (on) 3.dp else 2.dp, rim, shape)
}

@Composable
private fun FileDetails(ctx: WinSceneCtx, live: Boolean, base: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val facts = nativeFacts(ctx)
    Column(modifier.nativeScroll(Color.White), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        facts.forEach { f ->
            Row(Modifier.fillMaxWidth().fileSlot(false, base).padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                WholeWordsText(f.label.uppercase(), color = FileYellow, fontSize = 12.sp, fontWeight = FontWeight.Black, lineHeight = 15.sp,
                    modifier = Modifier.widthIn(max = 150.dp))
                Spacer(Modifier.weight(1f).widthIn(min = 10.dp))
                WholeWordsText(f.value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 17.sp,
                    modifier = Modifier.widthIn(max = 260.dp))
            }
        }
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            Column(Modifier.fillMaxWidth().background(fileMix(base, Color.Black, 0.25f), RoundedCornerShape(16.dp))
                .border(if (on) 3.dp else 2.dp, if (on) FileYellow else fileMix(base, Color.White, 0.35f), RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 7.dp)) {
                Text("STORY", color = FileYellow, fontSize = 12.sp, fontWeight = FontWeight.Black, lineHeight = 15.sp)
                Text(ctx.summary ?: "No story on this file yet.", color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, lineHeight = 17.sp)
            }
        }
    }
}

@Composable
private fun FileStars(ctx: WinSceneCtx, live: Boolean, base: Color, tight: Boolean, modifier: Modifier) {
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(24.dp)) { fileStar(FileYellow, true, 0f) }
            Spacer(Modifier.width(6.dp))
            WholeWordsText(fileStarsLine(t.earned, t.total), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black, lineHeight = 19.sp,
                modifier = Modifier.weight(1f))
            // (the order button gets at most its share - 682r)
            NativeItem(onClick = t.nextOrder, Modifier.weight(1f, fill = false), focus = focus, live = live) { on ->
                Text("ORDER: " + t.order.label.uppercase(), color = if (on) FileOnYellow else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black,
                    style = if (on) TextStyle() else FileOutline,
                    modifier = Modifier.fileSlot(on, base).padding(horizontal = 12.dp, vertical = 4.dp))
            }
        }
        // The little stars: one row sharing the inset's width (yellow for yours, outlined still to get).
        // (732a, AchFirstRow FILESELECT x1.3: on a tight file with big text the row left the first achievement 62 of 73 px - there
        // the "N / M STARS" line says it alone.)
        val (cells, lit) = mcBlocks(t.earned, t.total, FILE_STARS_MAX)
        val bigText = bigPageText()
        if (cells > 0 && !(tight && bigText)) Canvas(Modifier.fillMaxWidth().padding(top = 5.dp).height(12.dp)) {
            val gap = 3.dp.toPx()
            val w = ((size.width - gap * (cells - 1)) / cells).coerceAtMost(12.dp.toPx())
            for (i in 0 until cells) {
                val x = i * (w + gap)
                val box = androidx.compose.ui.geometry.Size(w, size.height)
                if (i < lit) fileStar(FileYellow, true, 0f, box, Offset(x, 0f))
                else fileStar(Color.White.copy(alpha = 0.55f), false, 1.2.dp.toPx(), box, Offset(x, 0f))
            }
        }
        Spacer(Modifier.height(if (tight) 4.dp else 6.dp))
        NativeRows(Color.White, Modifier.weight(1f).fillMaxWidth(), spacing = 4.dp) {
            nativeItems(t.all, { it.id }) { _, ach ->
                val earned = ach.isUnlocked
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { on ->
                        Column(Modifier.fillMaxWidth().background(fileMix(base, Color.Black, if (earned) 0.18f else 0.32f), RoundedCornerShape(16.dp))
                            .border(if (on) 3.dp else 2.dp, if (on) FileYellow else fileMix(base, Color.White, 0.3f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Canvas(Modifier.size(18.dp)) { if (earned) fileStar(FileYellow, true, 0f) else fileStar(Color.White.copy(alpha = 0.5f), false, 1.5.dp.toPx()) }
                                Spacer(Modifier.width(6.dp))
                                NativeBadge(ach.badgeName, ach.title, earned, FileYellow, Modifier.size(30.dp), shape = CircleShape)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    WholeWordsText((ach.title ?: "Untitled").uppercase(), color = if (earned) Color.White else Color.White.copy(alpha = 0.7f),
                                        fontSize = 13.sp, fontWeight = FontWeight.Black, lineHeight = 16.sp)
                                    Text(fileEntryLine(earned, ach.dateEarned), color = if (earned) FileYellow else Color.White.copy(alpha = 0.6f),
                                        fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 14.sp)
                                }
                                filePoints(ach.points)?.let {
                                    Text(it, color = if (earned) FileYellow else Color.White.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.Black,
                                        softWrap = false, style = FileOutline, modifier = Modifier.padding(start = 6.dp))
                                }
                            }
                            ach.description?.let { d ->
                                if (opened) Text(d, color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp, lineHeight = 16.sp,
                                    modifier = Modifier.padding(start = 62.dp, top = 3.dp))
                                else WinFullText(d, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1, lineHeight = 14.sp,
                                    modifier = Modifier.padding(start = 62.dp, top = 1.dp))
                            }
                            if (opened) ach.unlockPercent?.let { pc ->
                                Text("%.1f %% of players got this star".format(pc), color = FileYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 62.dp, top = 1.dp))
                            }
                        }
                    }
                }
            }
            if (t.all.isEmpty()) item {
                Text("NO STARS ON THIS FILE YET.", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun FileOptions(ctx: WinSceneCtx, live: Boolean, base: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(Color.White), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(),
                focus = if (i == 0) focus else null, live = live) { on ->
                androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                val armed = confirm.armed == i
                val ink = when { on && o.destructive -> Color.White; on -> FileOnYellow; o.destructive -> Color(0xFFFFB3AC); else -> Color.White }
                Column(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().fileSlot(on, base, red = o.destructive).padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        WinFullText(o.label.uppercase(), color = ink, fontSize = 14.sp, maxLines = 2, fontWeight = FontWeight.Black, lineHeight = 17.sp,
                            style = if (on && !o.destructive) TextStyle() else FileOutline)
                        o.sub?.let { WinFullText(it, color = ink.copy(alpha = 0.85f), fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp) }
                    }
                    if (armed) Text("ERASE? PRESS A AGAIN TO CONFIRM  ·  MOVE AWAY TO CANCEL", color = Color.White, fontSize = 12.sp,
                        fontWeight = FontWeight.Black, lineHeight = 15.sp, style = FileOutline,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).background(FileRed.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                            .border(2.dp, Color.White, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
        }
        if (ctx.options.isEmpty()) Text("NO OPTIONS FOR THIS FILE.", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
