package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * P2 (740a, class C): Green core's own pages. The window is a glowing system console - a dark panel with corners cut at 45°,
 * framed by a line in the layout's glow colour (Layout options › accent), the "// SYSTEM › TITLE" path over the name, a fading
 * glow rule, the cover and facts readout, the keys as cut-corner monospace keys. Its pages are read out on the same console
 * under the rule, in its monospace capitals and its glow:
 *  - Info         = TITLE: the console as before.
 *  - Details      = SPEC: each fact a readout line - the label in the dim glow, the value in the console's ink, a glow tick
 *                   down its left - then the story in a cut-corner well headed "// DESCRIPTION" (a D-pad stop).
 *  - Achievements = UNLOCKS: one line - the cup, "N / M", a core charge bar of 12 cells (one earned always charges one, all
 *                   only when every one is earned) and the order as a cut-corner key; each achievement a cut-corner line: its
 *                   cut-corner badge, the title in capitals, "UNLOCKED <date>" / "LOCKED", "+N"; the line you are on lit in the
 *                   glow; A opens the whole text and how rare it is.
 *  - Options      = CONTROL: "> " command lines; one that removes something is written in the alarm red and asks for a second A
 *                   ("PRESS A AGAIN TO CONFIRM", NativeConfirm).
 * Heading: "> " and the page's name in the glow, and a cut-corner cell per page (the shown one lit), after the old page has gone.
 * Motion: the next page opens from one glowing line across its middle, the console's own boot (fading in); none with Motion
 * Off. The keys stay one row of cut-corner keys under the readout with the page slots, Play first, so Play stays put.
 * Shared: data, focus, B, Play-first, inert outgoing copy (NativePageKit).
 */

/** The console's name for a page. Pure. */
internal fun corePageName(p: WinPage): String = when (p) {
    WinPage.MAIN -> "Title"
    WinPage.DETAILS -> "Spec"
    WinPage.TROPHIES -> "Unlocks"
    WinPage.OPTIONS -> "Control"
    WinPage.PERF -> "Performance"
}

/** The count beside the cup: "N / M" (earned never above the total). Pure. */
internal fun coreAwardCount(earned: Int, total: Int): String = "${earned.coerceIn(0, maxOf(total, 0))} / ${maxOf(total, 0)}"

/** An achievement's status: "UNLOCKED <date>" / "UNLOCKED" when yours, "LOCKED" otherwise. Pure. */
internal fun coreAwardLine(earned: Boolean, date: String?): String =
    if (!earned) "LOCKED" else date?.takeIf { it.isNotBlank() }?.let { "UNLOCKED ${it.trim()}" } ?: "UNLOCKED"

/** How many of the charge bar's [cells] are lit: one earned always charges one, all only when every one is earned. Pure. */
internal fun coreCharge(earned: Int, total: Int, cells: Int): Int {
    if (total <= 0 || cells <= 0) return 0
    val e = earned.coerceIn(0, total)
    if (e == 0) return 0
    if (e == total) return cells
    return (e * cells / total).coerceIn(1, cells - 1)
}

internal const val CORE_CELLS = 12

/** The next page opens from one glowing line across its middle (the console's boot), fading in; the old one goes at once. */
internal fun coreMove(@Suppress("UNUSED_PARAMETER") dir: Int): ContentTransform =
    (fadeIn(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) +
        expandVertically(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing), expandFrom = Alignment.CenterVertically, clip = true)) togetherWith
        fadeOut(tween(NATIVE_OUT_MS))

private val CoreAlarm = Color(0xFFFF5A4E)
private val CoreCut = CutCornerShape(6.dp)
private val CoreCutSmall = CutCornerShape(4.dp)

/** The console's ink set for its pages (from the layout's glow colour). */
internal class CoreInk(val glow: Color, val ink: Color, val dim: Color) {
    val label = glow.copy(alpha = 0.7f)
    val muted = lerp(ink, Color.Black, 0.35f)
    val tile = glow.copy(alpha = 0.07f)
    val line = glow.copy(alpha = 0.3f)
    /** The lit line / key: the glow itself, written on in the console's darkest shade. */
    val lit = glow
    val onLit = lerp(glow, Color.Black, 0.9f)
}

/** The heading: "> " and the page's name in the glow, a cut-corner cell per page (the shown one lit). */
@Composable
internal fun CorePageHeading(tabs: List<WinPage>, shown: WinPage, k: CoreInk, short: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(bottom = if (short) 3.dp else 5.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("> " + corePageName(shown).uppercase(), color = k.glow, fontSize = if (short) 13.sp else 15.sp, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold, letterSpacing = 2.sp, lineHeight = if (short) 16.sp else 19.sp, maxLines = 2,
                modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                tabs.forEach { t ->
                    val on = t == shown
                    Box(Modifier.size(width = 12.dp, height = 8.dp).background(if (on) k.glow else Color.Transparent, CutCornerShape(2.dp))
                        .border(1.dp, if (on) k.glow else k.line, CutCornerShape(2.dp)))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(k.line, k.glow.copy(alpha = 0f)))))
    }
}

/** One of the console's pages (Title is the console itself, drawn by CorePanel). */
@Composable
internal fun AnimatedVisibilityScope.CorePage(ctx: WinSceneCtx, p: WinPage, live: Boolean, k: CoreInk, modifier: Modifier) {
    when (p) {
        WinPage.DETAILS -> CoreSpec(ctx, live, k, modifier)
        WinPage.TROPHIES -> CoreUnlocks(ctx, live, k, modifier)
        WinPage.OPTIONS -> CoreControl(ctx, live, k, modifier)
        WinPage.MAIN -> {}
        else -> ctx.pageFor(p, modifier, live) // (Performance: the shared page - not on the console yet)
    }
}

@Composable
private fun CoreSpec(ctx: WinSceneCtx, live: Boolean, k: CoreInk, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    Column(modifier.nativeScroll(k.glow), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        nativeFacts(ctx).forEach { f ->
            Row(Modifier.fillMaxWidth().background(k.tile, CoreCutSmall)) {
                Box(Modifier.width(2.dp).height(34.dp).background(k.glow))
                Column(Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    WholeWordsText(f.label.uppercase(), color = k.label, fontSize = 10.sp, lineHeight = 13.sp, fontFamily = FontFamily.Monospace)
                    WholeWordsText(f.value, color = k.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 17.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            Column(Modifier.fillMaxWidth().background(k.tile, CoreCut).border(if (on) 2.dp else 1.dp, if (on) k.glow else k.line, CoreCut)
                .padding(horizontal = 10.dp, vertical = 7.dp)) {
                Text("// DESCRIPTION", color = if (on) k.glow else k.label, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp, lineHeight = 13.sp)
                Spacer(Modifier.height(2.dp))
                Text(ctx.summary ?: "No description for this game yet.", color = k.ink, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
private fun CoreUnlocks(ctx: WinSceneCtx, live: Boolean, k: CoreInk, modifier: Modifier) {
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    Column(modifier) {
        // One line: the cup and the count, the charge bar in the room between, the order key at most its share (its words slide on
        // one line, rule 11) - so the first achievement stays whole at x1.3 (AchFirstRow).
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.EmojiEvents, null, tint = k.glow, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(coreAwardCount(t.earned, t.total), color = k.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp,
                fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
            val lit = coreCharge(t.earned, t.total, CORE_CELLS)
            Row(Modifier.weight(1f).padding(horizontal = 8.dp).height(7.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(CORE_CELLS) { i -> Box(Modifier.weight(1f).fillMaxHeight().background(if (i < lit) k.glow else k.tile).border(0.5.dp, k.line)) }
            }
            NativeItem(onClick = t.nextOrder, Modifier.weight(1f, fill = false), focus = focus, live = live) { on ->
                Box(Modifier.background(if (on) k.lit else Color.Transparent, CoreCutSmall).border(1.dp, if (on) k.lit else k.line, CoreCutSmall)
                    .padding(horizontal = 10.dp, vertical = 4.dp)) {
                    WinFullText("ORDER: " + t.order.label.uppercase(), color = if (on) k.onLit else k.ink, fontSize = 11.sp, maxLines = 1, lineHeight = 14.sp,
                        fontFamily = FontFamily.Monospace)
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        NativeRows(k.glow, Modifier.weight(1f).fillMaxWidth(), spacing = 2.dp) {
            nativeItems(t.all, { it.id }) { _, ach ->
                val earned = ach.isUnlocked
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { on ->
                        val text = if (on) k.onLit else if (earned) k.ink else k.muted
                        val sub = if (on) k.onLit.copy(alpha = 0.85f) else k.muted
                        Column(Modifier.fillMaxWidth().background(if (on) k.lit else k.tile, CoreCutSmall)
                            .padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                NativeBadge(ach.badgeName, ach.title, earned, k.glow, Modifier.size(30.dp), shape = CoreCutSmall)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    WholeWordsText((ach.title ?: "Untitled").uppercase(), color = text, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                        lineHeight = 16.sp, fontFamily = FontFamily.Monospace)
                                    Text(coreAwardLine(earned, ach.dateEarned), color = if (on) sub else if (earned) k.glow else k.muted,
                                        fontSize = 10.sp, lineHeight = 13.sp, fontFamily = FontFamily.Monospace)
                                }
                                ach.points?.let { pts ->
                                    Text("+$pts", color = if (on) k.onLit else if (earned) k.ink else k.muted, fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, softWrap = false, modifier = Modifier.padding(start = 6.dp))
                                }
                            }
                            ach.description?.let { d ->
                                if (opened) Text(d, color = text, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(start = 38.dp, top = 3.dp))
                                else WinFullText(d, color = sub, fontSize = 11.sp, maxLines = 1, lineHeight = 14.sp, modifier = Modifier.padding(start = 38.dp, top = 1.dp))
                            }
                            if (opened) ach.unlockPercent?.let { pc ->
                                Text("%.1f %% of players have it".format(pc), color = if (on) k.onLit else k.glow, fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace, modifier = Modifier.padding(start = 38.dp, top = 1.dp, bottom = 2.dp))
                            }
                        }
                    }
                }
            }
            if (t.all.isEmpty()) item {
                Text("No achievements for this game yet.", color = k.muted, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun CoreControl(ctx: WinSceneCtx, live: Boolean, k: CoreInk, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(k.glow), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(),
                focus = if (i == 0) focus else null, live = live) { on ->
                androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                val armed = confirm.armed == i
                val hi = if (o.destructive) CoreAlarm else k.lit
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().background(if (on) hi else k.tile, CoreCutSmall)
                        .border(1.dp, if (on) hi else if (o.destructive) CoreAlarm.copy(alpha = 0.5f) else k.line, CoreCutSmall)
                        .padding(horizontal = 10.dp, vertical = 7.dp)) {
                        Text("> ", color = if (on) k.onLit else if (o.destructive) CoreAlarm else k.glow, fontSize = 14.sp, lineHeight = 18.sp,
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            WinFullText(o.label.uppercase(), color = if (on) k.onLit else if (o.destructive) CoreAlarm else k.ink, fontSize = 13.sp, maxLines = 2,
                                lineHeight = 18.sp, fontFamily = FontFamily.Monospace, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                            o.sub?.let { WinFullText(it, color = if (on) k.onLit.copy(alpha = 0.85f) else k.muted, fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp) }
                        }
                    }
                    if (armed) Text("PRESS A AGAIN TO CONFIRM  ·  MOVE AWAY TO CANCEL", color = Color.Black, fontSize = 11.sp, lineHeight = 15.sp,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp).background(CoreAlarm, CoreCutSmall).padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
        }
        if (ctx.options.isEmpty()) Text("Nothing to control for this game.", color = k.muted, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
}

/** The console's page area: the heading, then the page, booted open by [coreMove]. [short]: a short console - a smaller heading,
 *  so the first achievement stays whole (AchFirstRow). */
@Composable
internal fun CorePageArea(ctx: WinSceneCtx, k: CoreInk, modifier: Modifier, short: Boolean = false) {
    val heading = rememberHeadingPage(ctx.page)
    Column(modifier) {
        CorePageHeading(nativeTabs(ctx.pages), if (heading == WinPage.MAIN) ctx.page else heading, k, short)
        NativePages(ctx, Modifier.fillMaxWidth().weight(1f), label = "corePage", move = ::coreMove) { pg, pageLive ->
            CorePage(ctx, pg, pageLive, k, Modifier.fillMaxSize())
        }
    }
}
