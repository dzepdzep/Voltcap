package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * P2 (730a, class C): the Memory card layout's own pages. The window is the console's save browser showing a save you've
 * picked - the grey screen, the save's icon turning big on the left, the title in yellow top right, the choices as glowing words
 * along the bottom - so its pages are the browser's own screens on the dark glass panel at the right, in the browser's type
 * (yellow titles, white words with a soft drop shadow, the soft white-to-blue glow on the one you're on):
 *  - Info         = SAVE: the browser's save screen as before (MemCardWindow, unchanged).
 *  - Details      = INFORMATION: the save's info list - each fact a label / value line (labels dim at the left, values white,
 *                   wrapping in whole words), then the story as the save's notes (a D-pad stop).
 *  - Achievements = MEMORY BLOCKS: "N / M blocks saved" over a strip of memory blocks (a lit block per achievement you have, a
 *                   hollow one for each still to get; one block stands for several on long lists), then each achievement a save
 *                   entry - its square badge, the title in yellow when yours, "Saved <date>" / "Empty block", its points; A
 *                   opens its whole text and how rare it is. The order is a glowing word like the browser's choices.
 *  - Options      = COMMANDS: each option a word line with the choices' glow when focused; one that removes something is red
 *                   and asks for a second A (NativeConfirm), as the browser asks before it deletes a save.
 * Heading: the page's name in yellow at the top of the panel and a row of small memory blocks, one per page, the shown one lit -
 * after the old page has gone. Motion: the next screen rises a little into the panel as it fades in, drifting in from the
 * right going forward, from the left going back; none with Motion Off.
 * Shared: data, focus, B, Play-first, inert outgoing copy (NativePageKit).
 */

/** The save browser's name for a page. Pure. */
internal fun mcPageName(p: WinPage): String = when (p) {
    WinPage.MAIN -> "SAVE"
    WinPage.DETAILS -> "INFORMATION"
    WinPage.TROPHIES -> "MEMORY BLOCKS"
    WinPage.OPTIONS -> "COMMANDS"
    WinPage.PERF -> "PERFORMANCE"
}

/** An achievement entry's line: "Saved <date>" / "Saved" when yours, "Empty block" when still to get. Pure. */
internal fun mcEntryLine(earned: Boolean, date: String?): String =
    if (!earned) "Empty block" else date?.takeIf { it.isNotBlank() }?.let { "Saved $it" } ?: "Saved"

/** The blocks line: "N / M blocks saved" ("1 / 1 block saved"). Pure. */
internal fun mcBlocksLine(earned: Int, total: Int): String = "$earned / $total " + (if (total == 1) "block" else "blocks") + " saved"

/** The most blocks the strip draws; past it one block stands for several achievements. */
internal const val MC_BLOCKS_MAX = 40

/**
 * The block strip for [earned] of [total]: (blocks drawn, blocks lit). Every achievement has its own block up to [MC_BLOCKS_MAX];
 * past that the strip keeps [MC_BLOCKS_MAX] blocks and lights the earned share rounded up - any progress lights at least one
 * block, and every block is lit only when every achievement is earned. Pure.
 */
internal fun mcBlocks(earned: Int, total: Int, max: Int = MC_BLOCKS_MAX): Pair<Int, Int> {
    if (total <= 0) return 0 to 0
    val e = earned.coerceIn(0, total)
    if (total <= max) return total to e
    if (e == total) return max to max
    val lit = if (e == 0) 0 else ((e.toLong() * max + total - 1) / total).toInt().coerceIn(1, max - 1)
    return max to lit
}

/** An entry's points: "N pts" (1 pt), nothing without points. Pure. */
internal fun mcPoints(points: Int?): String? = points?.let { if (it == 1) "1 pt" else "$it pts" }

internal fun mcMove(dir: Int): ContentTransform =
    (fadeIn(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) +
        slideIn(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) { s -> IntOffset(if (dir >= 0) s.width / 14 else -s.width / 14, s.height / 16) }) togetherWith
        fadeOut(tween(NATIVE_OUT_MS))

private val MC_RED = Color(0xFFFF6B5E)
private val MC_INK = Color.White
private val MC_TYPE = TextStyle(shadow = McShadow)

/** The choices' soft glow (MemCardWindow's choice words), behind a focused line. */
private fun Modifier.mcGlow(on: Boolean, red: Boolean = false): Modifier = drawBehind {
    if (on) drawRoundRect(
        Brush.verticalGradient(if (red) listOf(MC_RED.copy(alpha = 0.7f), MC_RED.copy(alpha = 0.4f))
            else listOf(Color.White.copy(alpha = 0.45f), Color(0xFF9CC2FF).copy(alpha = 0.3f))),
        cornerRadius = CornerRadius(8.dp.toPx())
    )
}

/** The panel's heading: the page's name in the browser's yellow and a row of small memory blocks, the shown page's lit. */
@Composable
internal fun McPageHeading(tabs: List<WinPage>, shown: WinPage) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        WholeWordsText(mcPageName(shown), color = McYellow, fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 17.sp,
            modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            tabs.forEach { t ->
                val on = t == shown
                Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(if (on) MC_INK else Color.Transparent)
                    .border(1.dp, MC_INK.copy(alpha = if (on) 1f else 0.5f), RoundedCornerShape(2.dp)))
            }
        }
    }
}

/** One of the browser's screens on the glass panel (Info is the save screen itself, drawn by MemCardWindow). */
@Composable
internal fun AnimatedVisibilityScope.MemCardPage(ctx: WinSceneCtx, p: WinPage, live: Boolean, modifier: Modifier) {
    when (p) {
        WinPage.DETAILS -> McInformation(ctx, live, modifier)
        WinPage.TROPHIES -> McBlocks(ctx, live, modifier)
        WinPage.OPTIONS -> McCommands(ctx, live, modifier)
        WinPage.MAIN -> {}
        else -> ctx.pageFor(p, modifier, live) // (Performance: the shared page - not in the browser yet)
    }
}

@Composable
private fun McInformation(ctx: WinSceneCtx, live: Boolean, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val facts = nativeFacts(ctx, setOf("Trophies"))
    Column(modifier.nativeScroll(MC_INK), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        facts.forEach { f ->
            Row(Modifier.fillMaxWidth()) {
                WholeWordsText(f.label, color = MC_INK.copy(alpha = 0.62f), fontSize = 12.sp, lineHeight = 15.sp,
                    modifier = Modifier.widthIn(min = 72.dp, max = 112.dp).padding(end = 8.dp))
                WholeWordsText(f.value, color = MC_INK, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp,
                    modifier = Modifier.weight(1f))
            }
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(MC_INK.copy(alpha = 0.22f)))
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            Column(Modifier.fillMaxWidth().mcGlow(on).padding(horizontal = 6.dp, vertical = 4.dp)) {
                Text("Notes", color = McYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 15.sp, style = MC_TYPE)
                Text(ctx.summary ?: "No notes saved for this game.", color = MC_INK.copy(alpha = 0.9f), fontSize = 13.sp, lineHeight = 18.sp, style = MC_TYPE)
            }
        }
    }
}

@Composable
private fun McBlocks(ctx: WinSceneCtx, live: Boolean, modifier: Modifier) {
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WholeWordsText(mcBlocksLine(t.earned, t.total), color = MC_INK, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp,
                modifier = Modifier.weight(1f))
            // (the order word gets at most its share - 682r)
            NativeItem(onClick = t.nextOrder, Modifier.weight(1f, fill = false), focus = focus, live = live) { on ->
                Text("Order: " + t.order.label, color = if (on) MC_INK else MC_INK.copy(alpha = 0.82f), fontSize = 12.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, style = MC_TYPE,
                    modifier = Modifier.mcGlow(on).padding(horizontal = 10.dp, vertical = 3.dp))
            }
        }
        // The card's memory blocks: lit for yours, hollow for those still to get - one row of blocks sharing the panel's width
        // (a wrapping strip took two rows at x1.3 and left the first achievement 2 px short - AchFirstRow MEMCARD n=40).
        val (cells, lit) = mcBlocks(t.earned, t.total)
        if (cells > 0) androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().padding(top = 5.dp).height(8.dp)) {
            val gap = (if (cells > 24) 2.dp else 3.dp).toPx()
            val w = ((size.width - gap * (cells - 1)) / cells).coerceAtMost(8.dp.toPx())
            val line = 1.dp.toPx()
            for (i in 0 until cells) {
                val x = i * (w + gap)
                if (i < lit) drawRect(McYellow, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Size(w, size.height))
                else drawRect(MC_INK.copy(alpha = 0.4f), androidx.compose.ui.geometry.Offset(x + line / 2, line / 2),
                    androidx.compose.ui.geometry.Size(w - line, size.height - line), style = androidx.compose.ui.graphics.drawscope.Stroke(line))
            }
        }
        Spacer(Modifier.height(6.dp))
        NativeRows(MC_INK, Modifier.weight(1f).fillMaxWidth(), spacing = 2.dp) {
            nativeItems(t.all, { it.id }) { _, ach ->
                val earned = ach.isUnlocked
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { on ->
                        Column(Modifier.fillMaxWidth().mcGlow(on).padding(horizontal = 6.dp, vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                NativeBadge(ach.badgeName, ach.title, earned, McYellow, Modifier.size(30.dp), shape = RoundedCornerShape(4.dp))
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    WholeWordsText(ach.title ?: "Untitled", color = if (earned) McYellow else MC_INK.copy(alpha = 0.66f), fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold, lineHeight = 16.sp)
                                    Text(mcEntryLine(earned, ach.dateEarned), color = MC_INK.copy(alpha = if (earned) 0.85f else 0.5f), fontSize = 10.sp,
                                        lineHeight = 13.sp, style = MC_TYPE)
                                }
                                mcPoints(ach.points)?.let {
                                    Text(it, color = MC_INK.copy(alpha = if (earned) 0.95f else 0.55f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                        softWrap = false, style = MC_TYPE, modifier = Modifier.padding(start = 6.dp))
                                }
                            }
                            ach.description?.let { d ->
                                if (opened) Text(d, color = MC_INK.copy(alpha = 0.9f), fontSize = 12.sp, lineHeight = 16.sp, style = MC_TYPE,
                                    modifier = Modifier.padding(start = 38.dp, top = 3.dp))
                                else WinFullText(d, color = MC_INK.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1, lineHeight = 14.sp,
                                    modifier = Modifier.padding(start = 38.dp, top = 1.dp))
                            }
                            if (opened) ach.unlockPercent?.let { pc ->
                                Text("%.1f %% of players saved this".format(pc), color = McYellow, fontSize = 10.sp, style = MC_TYPE,
                                    modifier = Modifier.padding(start = 38.dp, top = 1.dp))
                            }
                        }
                    }
                }
            }
            if (t.all.isEmpty()) item {
                Text("No memory blocks on this card yet.", color = MC_INK.copy(alpha = 0.62f), fontSize = 12.sp, style = MC_TYPE,
                    modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun McCommands(ctx: WinSceneCtx, live: Boolean, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(MC_INK), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(),
                focus = if (i == 0) focus else null, live = live) { on ->
                androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                val armed = confirm.armed == i
                Column(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().mcGlow(on, red = o.destructive).padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        WinFullText(o.label, color = if (o.destructive && !on) MC_RED else MC_INK, fontSize = 15.sp, maxLines = 2,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, lineHeight = 18.sp)
                        o.sub?.let { WinFullText(it, color = MC_INK.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp) }
                    }
                    if (armed) Text("Press A again to confirm  ·  move away to cancel", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        lineHeight = 16.sp, modifier = Modifier.fillMaxWidth().padding(top = 3.dp).clip(RoundedCornerShape(8.dp)).background(MC_RED)
                            .padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
        }
        if (ctx.options.isEmpty()) Text("No commands for this save.", color = MC_INK.copy(alpha = 0.62f), fontSize = 12.sp, style = MC_TYPE)
    }
}
