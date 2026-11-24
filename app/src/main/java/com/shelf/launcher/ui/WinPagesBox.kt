package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideIn
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * P2 (731a, class C): the Cardboard box layout's own pages. The window is the insert sheet tucked into the box with the case - a
 * typed inventory slip on cream paper, taped at its corner, its actions rubber-stamped boxes - so its pages are the slip's other
 * sheets, typed on the same paper in the same typewriter face:
 *  - Info         = INVENTORY: the slip as before (BoxInsertSheet; its heading said CONTENTS, the name of its Details
 *                   button - now INVENTORY, so the two are not both CONTENTS).
 *  - Details      = CONTENTS (its button's name; the slip's "THE WHOLE STORY: CONTENTS" points here): each fact a numbered
 *                   item on a dotted leader ("01 RELEASED ...... 1998"; values wrap in whole
 *                   words), then the story as the slip's REMARKS (a D-pad stop).
 *  - Achievements = CHECKLIST: "N OF M CHECKED" with a typed tally of boxes (a filled box per achievement you have, an empty one
 *                   for each still to get; one box stands for several on long lists), then each achievement a checklist line - a
 *                   typed [X] / [ ], its square badge, the title, "CHECKED <date>" / "NOT YET", its points; A opens its whole
 *                   text and how rare it is. The order is a small stamped box like the slip's actions.
 *  - Options      = SERVICE: each option a typed line with the stamp's ink when focused; one that removes something is in the
 *                   slip's red ink and asks for a second A (NativeConfirm), stamped VOID when armed.
 * Heading: "SHEET n OF m" and the page's name typed under the slip's rule, with a row of small sheet tabs, the shown one filled -
 * after the old page has gone. Motion: the next sheet feeds up from under the one before as it fades in, a little from the right
 * going forward, from the left going back; none with Motion Off.
 * Shared: data, focus, B, Play-first, inert outgoing copy (NativePageKit).
 */

/** The insert's name for a page. Pure. */
internal fun boxPageName(p: WinPage): String = when (p) {
    WinPage.MAIN -> "INVENTORY"
    WinPage.DETAILS -> "CONTENTS"
    WinPage.TROPHIES -> "CHECKLIST"
    WinPage.OPTIONS -> "SERVICE"
    WinPage.PERF -> "PERFORMANCE"
}

/** "SHEET n OF m" for [shown] among [tabs]; nothing for a page that is not a sheet of the insert. Pure. */
internal fun boxSheetLine(tabs: List<WinPage>, shown: WinPage): String? {
    val i = tabs.indexOf(shown)
    return if (i < 0) null else "SHEET ${i + 1} OF ${tabs.size}"
}

/** A packing slip item's number: "01", "02", ... ("10" past nine). Pure. */
internal fun boxItemNo(i: Int): String = (i + 1).toString().padStart(2, '0')

/** The checklist's typed box: "[X]" when yours, "[ ]" still to get. Pure. */
internal fun boxMark(earned: Boolean): String = if (earned) "[X]" else "[ ]"

/** A checklist line's note: "CHECKED <date>" / "CHECKED" when yours, "NOT YET" when still to get. Pure. */
internal fun boxEntryLine(earned: Boolean, date: String?): String =
    if (!earned) "NOT YET" else date?.takeIf { it.isNotBlank() }?.let { "CHECKED $it" } ?: "CHECKED"

/** The tally line: "N OF M CHECKED". Pure. */
internal fun boxCheckedLine(earned: Int, total: Int): String = "${earned.coerceIn(0, maxOf(total, 0))} OF $total CHECKED"

/** A line's points: "N PTS" (1 PT), nothing without points. Pure. */
internal fun boxPoints(points: Int?): String? = points?.let { if (it == 1) "1 PT" else "$it PTS" }

/** The most boxes the tally types; past it one box stands for several achievements (the Memory card's rule, [mcBlocks]). */
internal const val BOX_TALLY_MAX = 32

internal fun boxMove(dir: Int): ContentTransform =
    (fadeIn(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) +
        slideIn(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) { s -> IntOffset(if (dir >= 0) s.width / 18 else -s.width / 18, s.height / 8) }) togetherWith
        fadeOut(tween(NATIVE_OUT_MS))

private val BoxPaper = Color(0xFFF5EEDC)
private val BoxInk = Color(0xFF2B2117)
private val BoxMuted = Color(0xFF6E5C43)
private val BoxRule = Color(0xFFB9A784)
private val BoxRed = Color(0xFFB8322A)
private val BoxMono = FontFamily.Monospace

/** The stamp's ink behind a focused line - the slip's actions' look (filled in the accent, red for a removing one). */
private fun Modifier.boxStamp(on: Boolean, accent: Color, red: Boolean = false): Modifier =
    if (!on) this else this.background(if (red) BoxRed else accent)

/** The sheet's heading: "SHEET n OF m", the page's name, and the small sheet tabs (the shown one filled). */
@Composable
internal fun BoxPageHeading(tabs: List<WinPage>, shown: WinPage) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            boxSheetLine(tabs, shown)?.let { Text(it, color = BoxMuted, fontSize = 10.sp, fontFamily = BoxMono, lineHeight = 13.sp) }
            WholeWordsText(boxPageName(shown), color = BoxInk, fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = BoxMono, lineHeight = 17.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            tabs.forEach { t ->
                val on = t == shown
                Box(Modifier.size(10.dp, 8.dp).background(if (on) BoxInk else Color.Transparent).border(1.dp, BoxInk.copy(alpha = if (on) 1f else 0.5f)))
            }
        }
    }
}

/** One of the insert's sheets (INVENTORY is the slip itself, drawn by BoxInsertSheet). */
@Composable
internal fun AnimatedVisibilityScope.BoxPage(ctx: WinSceneCtx, p: WinPage, live: Boolean, accent: Color, modifier: Modifier) {
    when (p) {
        WinPage.DETAILS -> BoxPackingSlip(ctx, live, accent, modifier)
        WinPage.TROPHIES -> BoxChecklist(ctx, live, accent, modifier)
        WinPage.OPTIONS -> BoxService(ctx, live, accent, modifier)
        WinPage.MAIN -> {}
        else -> ctx.pageFor(p, modifier, live) // (Performance: the shared page - not on the insert yet)
    }
}

@Composable
private fun BoxLeader(modifier: Modifier) {
    Canvas(modifier.height(10.dp).padding(horizontal = 4.dp)) {
        var x = 0f
        while (x < size.width) { drawCircle(BoxRule, 1.1f, Offset(x, size.height - 2f)); x += 5f }
    }
}

@Composable
private fun BoxPackingSlip(ctx: WinSceneCtx, live: Boolean, accent: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val facts = nativeFacts(ctx)
    Column(modifier.nativeScroll(BoxInk), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        facts.forEachIndexed { i, f ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(boxItemNo(i), color = BoxMuted, fontSize = 11.sp, fontFamily = BoxMono, softWrap = false, modifier = Modifier.padding(end = 6.dp))
                WholeWordsText(f.label.uppercase(), color = BoxMuted, fontSize = 12.sp, fontFamily = BoxMono, lineHeight = 15.sp,
                    modifier = Modifier.widthIn(max = 120.dp))
                BoxLeader(Modifier.weight(1f).widthIn(min = 12.dp))
                WholeWordsText(f.value, color = BoxInk, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = BoxMono, lineHeight = 16.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.widthIn(max = 220.dp))
            }
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(BoxRule))
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            Column(Modifier.fillMaxWidth().then(if (on) Modifier.border(2.dp, accent) else Modifier).padding(horizontal = 6.dp, vertical = 4.dp)) {
                Text("REMARKS", color = BoxMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = BoxMono, lineHeight = 14.sp)
                Text(ctx.summary ?: "No remarks on this slip.", color = BoxInk.copy(alpha = 0.88f), fontSize = 12.sp, lineHeight = 16.sp, fontFamily = BoxMono)
            }
        }
    }
}

@Composable
private fun BoxChecklist(ctx: WinSceneCtx, live: Boolean, accent: Color, modifier: Modifier) {
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WholeWordsText(boxCheckedLine(t.earned, t.total), color = BoxInk, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = BoxMono,
                lineHeight = 16.sp, modifier = Modifier.weight(1f))
            // (the order stamp gets at most its share - 682r)
            NativeItem(onClick = t.nextOrder, Modifier.weight(1f, fill = false), focus = focus, live = live) { on ->
                Text("ORDER: " + t.order.label.uppercase(), color = if (on) BoxPaper else BoxInk, fontSize = 11.sp, fontFamily = BoxMono,
                    fontWeight = FontWeight.Black, modifier = Modifier.boxStamp(on, accent).border(2.dp, if (on) accent else BoxInk)
                        .padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
        // The typed tally: one row of boxes sharing the sheet's width (a filled box for yours, an empty one still to get).
        val (cells, lit) = mcBlocks(t.earned, t.total, BOX_TALLY_MAX)
        if (cells > 0) Canvas(Modifier.fillMaxWidth().padding(top = 5.dp).height(8.dp)) {
            val gap = (if (cells > 20) 2.dp else 3.dp).toPx()
            val w = ((size.width - gap * (cells - 1)) / cells).coerceAtMost(9.dp.toPx())
            val line = 1.dp.toPx()
            for (i in 0 until cells) {
                val x = i * (w + gap)
                if (i < lit) drawRect(BoxInk, Offset(x, 0f), androidx.compose.ui.geometry.Size(w, size.height))
                else drawRect(BoxMuted, Offset(x + line / 2, line / 2), androidx.compose.ui.geometry.Size(w - line, size.height - line),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(line))
            }
        }
        Spacer(Modifier.height(6.dp))
        NativeRows(BoxInk, Modifier.weight(1f).fillMaxWidth(), spacing = 2.dp) {
            nativeItems(t.all, { it.id }) { _, ach ->
                val earned = ach.isUnlocked
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { on ->
                        Column(Modifier.fillMaxWidth().then(if (on) Modifier.border(2.dp, accent) else Modifier).padding(horizontal = 6.dp, vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(boxMark(earned), color = if (earned) BoxInk else BoxMuted, fontSize = 13.sp, fontFamily = BoxMono,
                                    fontWeight = FontWeight.Bold, softWrap = false)
                                Spacer(Modifier.width(6.dp))
                                NativeBadge(ach.badgeName, ach.title, earned, accent, Modifier.size(28.dp), shape = RoundedCornerShape(2.dp))
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    WholeWordsText((ach.title ?: "Untitled").uppercase(), color = if (earned) BoxInk else BoxInk.copy(alpha = 0.62f),
                                        fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = BoxMono, lineHeight = 15.sp)
                                    Text(boxEntryLine(earned, ach.dateEarned), color = if (earned) BoxMuted else BoxMuted.copy(alpha = 0.75f),
                                        fontSize = 10.sp, fontFamily = BoxMono, lineHeight = 13.sp)
                                }
                                boxPoints(ach.points)?.let {
                                    Text(it, color = if (earned) BoxInk else BoxMuted, fontSize = 11.sp, fontFamily = BoxMono, fontWeight = FontWeight.Bold,
                                        softWrap = false, modifier = Modifier.padding(start = 6.dp))
                                }
                            }
                            ach.description?.let { d ->
                                if (opened) Text(d, color = BoxInk.copy(alpha = 0.88f), fontSize = 12.sp, lineHeight = 16.sp, fontFamily = BoxMono,
                                    modifier = Modifier.padding(start = 64.dp, top = 3.dp))
                                else WinFullText(d, color = BoxMuted, fontSize = 11.sp, maxLines = 1, lineHeight = 14.sp, fontFamily = BoxMono,
                                    modifier = Modifier.padding(start = 64.dp, top = 1.dp))
                            }
                            if (opened) ach.unlockPercent?.let { pc ->
                                Text("%.1f %% OF PLAYERS CHECKED THIS".format(pc), color = BoxRed, fontSize = 10.sp, fontFamily = BoxMono,
                                    modifier = Modifier.padding(start = 64.dp, top = 1.dp))
                            }
                        }
                    }
                }
            }
            if (t.all.isEmpty()) item {
                Text("NOTHING ON THIS CHECKLIST YET.", color = BoxMuted, fontSize = 12.sp, fontFamily = BoxMono, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun BoxService(ctx: WinSceneCtx, live: Boolean, accent: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(BoxInk), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(),
                focus = if (i == 0) focus else null, live = live) { on ->
                androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                val armed = confirm.armed == i
                val ink = if (on) BoxPaper else if (o.destructive) BoxRed else BoxInk
                Column(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().boxStamp(on, accent, red = o.destructive)
                        .border(if (on) 2.dp else 1.dp, if (on) (if (o.destructive) BoxRed else accent) else BoxRule)
                        .padding(horizontal = 10.dp, vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        WinFullText(o.label.uppercase(), color = ink, fontSize = 13.sp, maxLines = 2, fontFamily = BoxMono,
                            fontWeight = FontWeight.Black, lineHeight = 16.sp)
                        o.sub?.let { WinFullText(it, color = if (on) BoxPaper.copy(alpha = 0.85f) else BoxMuted, fontSize = 11.sp, maxLines = 2,
                            fontFamily = BoxMono, lineHeight = 14.sp) }
                    }
                    if (armed) Text("VOID?  PRESS A AGAIN TO CONFIRM  ·  MOVE AWAY TO CANCEL", color = BoxRed, fontSize = 11.sp, fontFamily = BoxMono,
                        fontWeight = FontWeight.Black, lineHeight = 15.sp, modifier = Modifier.fillMaxWidth().padding(top = 3.dp)
                            .border(2.dp, BoxRed).padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }
        if (ctx.options.isEmpty()) Text("NO SERVICE FOR THIS ITEM.", color = BoxMuted, fontSize = 12.sp, fontFamily = BoxMono)
    }
}
