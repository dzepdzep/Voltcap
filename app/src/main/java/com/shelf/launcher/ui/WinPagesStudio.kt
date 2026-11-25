package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.ui.theme.ShelfTheme

/*
 * P2 (744a, class C): Layout Studio's own pages. The window is the studio's panel in the layout's (theme / your) colours: a
 * faint layout grid, the art in a framed well with crop marks, the console in small spaced capitals, the name in bold, the
 * keys as rounded buttons (Play in the accent). Its pages keep that editor's language:
 *  - Info         = Canvas: the art well and the facts, as before.
 *  - Details      = Properties: an inspector - each fact a row, its label in small spaced capitals on the left, its value on
 *                   the right, a dashed guide line under each row; then the description in a text frame (a D-pad stop, outlined
 *                   in the accent and tinted when you are on it).
 *  - Achievements = Milestones: "N / M" in bold, the per cent, and a ruler - a rounded track filled in the accent with a tick
 *                   every tenth (one earned always shows, full only when every one is earned); the order as a rounded chip;
 *                   each achievement a layer row - its badge in a small framed well (outlined in the accent when yours), the
 *                   title in bold, "Earned <date>" / "Not yet", "+N" in a small accent chip; the row you are on tinted and
 *                   outlined in the accent; A shows the whole text and how rare it is.
 *  - Options      = Actions: each option a rounded button like the keys; one that removes something is outlined in red and
 *                   asks for a second A ("Press A again to confirm", NativeConfirm).
 * Heading: the editor's tab strip - the page names in a rounded segmented bar, the shown one filled in the accent, in the
 * page order (wraps, never cut). Motion: the next page snaps in from one grid step below, fading in - as an element placed on
 * the grid; none with Motion Off. On its pages the key row keeps the first page's slots and the panel grows upward only, so
 * Play stays where the first page had it. Shared: data, focus, B, Play-first, inert outgoing copy (NativePageKit).
 */

/** Layout Studio's name for a page (the editor's words). Pure. */
internal fun studioPageName(p: WinPage): String = when (p) {
    WinPage.MAIN -> "Canvas"
    WinPage.DETAILS -> "Properties"
    WinPage.TROPHIES -> "Milestones"
    WinPage.OPTIONS -> "Actions"
    WinPage.PERF -> "Performance"
}

/** "N / M" (earned never above the total, nothing below 0). Pure. */
internal fun studioAwardCount(earned: Int, total: Int): String {
    val t = maxOf(total, 0)
    return "${earned.coerceIn(0, t)} / $t"
}

/** An achievement's second line: "Earned <date>" / "Earned" when yours, "Not yet" otherwise. Pure. */
internal fun studioAwardLine(earned: Boolean, date: String?): String =
    if (!earned) "Not yet" else date?.takeIf { it.isNotBlank() }?.let { "Earned ${it.trim()}" } ?: "Earned"

/** The ruler's ticks: every tenth of the track, the ends left out (0.1 .. 0.9). Pure. */
internal fun studioRulerTicks(): List<Float> = (1..9).map { it / 10f }

/** The page change: the new page fades in while it snaps up onto the grid (StudioPage); the old one goes at once. */
internal fun studioMove(@Suppress("UNUSED_PARAMETER") dir: Int): ContentTransform =
    fadeIn(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) togetherWith fadeOut(tween(NATIVE_OUT_MS))

private val StudioRed = Color(0xFFE5484D)

/** The studio's ink for its pages, in the layout's colours: [accent] = the layout's (your) accent. */
internal class StudioInk(val accent: Color, val ink: Color, val muted: Color) {
    val line = ink.copy(alpha = 0.16f)
    val fill = ink.copy(alpha = 0.07f)
    val tint = accent.copy(alpha = 0.16f)
    val onAccent = if (accent.luminance() > 0.5f) Color(0xFF111111) else Color.White
}

@Composable
internal fun rememberStudioInk(accent: Color): StudioInk {
    val t = ShelfTheme.colors
    val label = com.shelf.launcher.ui.theme.legibleArtAccent(accent, t.surface, t.surface)
    return StudioInk(label, t.textPrimary, ShelfTheme.textMuted)
}

/** The editor's tab strip: the page names in a rounded segmented bar, the shown one filled in the accent; wraps, never cut. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun StudioTabs(tabs: List<WinPage>, shown: WinPage, k: StudioInk, short: Boolean = false) {
    val shape = RoundedCornerShape(10.dp)
    androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().padding(bottom = if (short) 4.dp else 8.dp)
        .background(k.fill, shape).border(1.dp, k.line, shape).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        tabs.forEach { p ->
            val on = p == shown
            Box(Modifier.background(if (on) k.accent else Color.Transparent, RoundedCornerShape(8.dp))
                .padding(horizontal = if (short) 8.dp else 10.dp, vertical = if (short) 3.dp else 5.dp)) {
                Text(studioPageName(p), color = if (on) k.onAccent else k.muted, fontSize = if (short) 11.sp else 12.sp,
                    lineHeight = if (short) 14.sp else 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** One of the panel's pages (Canvas is the art well and facts, drawn by StudioPanel), snapped up onto the grid. */
@Composable
internal fun AnimatedVisibilityScope.StudioPage(ctx: WinSceneCtx, p: WinPage, live: Boolean, k: StudioInk, modifier: Modifier, short: Boolean = false) {
    val anim = rememberAnimationsEnabled()
    val snapIn by transition.animateFloat(transitionSpec = { if (anim) tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing) else snap() }, label = "studioSnap") {
        if (it == EnterExitState.Visible) 1f else 0f
    }
    // One grid step (the panel's 18 dp grid) below -> in place (the old page has already gone - NATIVE_OUT_MS).
    val m = modifier.graphicsLayer { translationY = (1f - snapIn) * 18.dp.toPx() }
    when (p) {
        WinPage.DETAILS -> StudioProperties(ctx, live, k, m)
        WinPage.TROPHIES -> StudioMilestones(ctx, live, k, m, short)
        WinPage.OPTIONS -> StudioActions(ctx, live, k, m)
        WinPage.MAIN -> {}
        else -> ctx.pageFor(p, modifier, live) // (Performance: the shared page - not a page of the studio yet)
    }
}

@Composable
private fun StudioProperties(ctx: WinSceneCtx, live: Boolean, k: StudioInk, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val guide = k.line
    Column(modifier.nativeScroll(k.accent), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        nativeFacts(ctx).forEach { f ->
            Row(Modifier.fillMaxWidth().drawBehind {
                // The inspector's dashed guide line under each row.
                val y = size.height - 0.5f
                drawLine(guide, Offset(0f, y), Offset(size.width, y), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)))
            }.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                WholeWordsText(f.label.uppercase(), color = k.accent, fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(0.38f).padding(top = 2.dp, end = 8.dp))
                WholeWordsText(f.value, color = k.ink, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(0.62f))
            }
        }
        Spacer(Modifier.height(6.dp))
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            val shape = RoundedCornerShape(10.dp)
            Column(Modifier.fillMaxWidth().background(if (on) k.tint else k.fill, shape).border(if (on) 2.dp else 1.dp, if (on) k.accent else k.line, shape)
                .padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text("DESCRIPTION", color = k.accent, fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Spacer(Modifier.height(3.dp))
                Text(ctx.summary ?: "No description for this game yet.", color = k.ink, fontSize = 14.sp, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun StudioMilestones(ctx: WinSceneCtx, live: Boolean, k: StudioInk, modifier: Modifier, short: Boolean) {
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    val pc = livePercent(t.earned, t.total) // (one earned always shows; full only when every one is earned)
    Column(modifier) {
        // One line: "N / M", the per cent, the order as a rounded chip at most its share (its words slide, rule 11) - so the
        // first achievement stays whole at x1.3 (AchFirstRow); the ruler under it.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(studioAwardCount(t.earned, t.total), color = k.ink, fontSize = if (short) 17.sp else 22.sp, lineHeight = if (short) 20.sp else 26.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, softWrap = false)
            Spacer(Modifier.width(8.dp))
            Text("$pc%", color = k.accent, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            Spacer(Modifier.width(12.dp))
            NativeItem(onClick = t.nextOrder, Modifier.weight(1f, fill = false), focus = focus, live = live) { on ->
                val shape = RoundedCornerShape(10.dp)
                Box(Modifier.background(if (on) k.accent else k.fill, shape).border(1.dp, if (on) k.accent else k.line, shape)
                    .padding(horizontal = 10.dp, vertical = 5.dp)) {
                    WinFullText("Order: " + t.order.label, color = if (on) k.onAccent else k.ink, fontSize = 12.sp, maxLines = 1, lineHeight = 15.sp,
                        fontWeight = FontWeight.Medium)
                }
            }
        }
        // The ruler: a rounded track, filled in the accent, a tick every tenth.
        val tick = k.line
        Box(Modifier.padding(top = if (short) 4.dp else 6.dp, bottom = if (short) 6.dp else 8.dp).fillMaxWidth().height(6.dp).background(k.fill, RoundedCornerShape(3.dp))
            .drawBehind {
                studioRulerTicks().forEach { f ->
                    val x = size.width * f
                    drawLine(tick, Offset(x, size.height), Offset(x, size.height + 4.dp.toPx()), 1.dp.toPx())
                }
            }) {
            if (pc > 0) Box(Modifier.fillMaxWidth(pc / 100f).height(6.dp).background(k.accent, RoundedCornerShape(3.dp)))
        }
        NativeRows(k.accent, Modifier.weight(1f).fillMaxWidth(), spacing = 3.dp) {
            nativeItems(t.all, { it.id }) { _, ach ->
                val earned = ach.isUnlocked
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { on ->
                        val shape = RoundedCornerShape(10.dp)
                        val text = if (earned || on) k.ink else k.muted
                        val sub = if (earned) k.accent else k.muted
                        Column(Modifier.fillMaxWidth().background(if (on) k.tint else Color.Transparent, shape)
                            .border(1.dp, if (on) k.accent else Color.Transparent, shape)
                            .padding(start = 6.dp, end = 10.dp, top = 5.dp, bottom = 5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // The badge in a small framed well: outlined in the accent when yours.
                                Box(Modifier.size(38.dp).background(k.fill, RoundedCornerShape(6.dp))
                                    .border(if (earned) 1.5.dp else 1.dp, if (earned) k.accent else k.line, RoundedCornerShape(6.dp)),
                                    contentAlignment = Alignment.Center) {
                                    NativeBadge(ach.badgeName, ach.title, earned, k.accent, Modifier.size(30.dp), shape = RoundedCornerShape(4.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    WholeWordsText(ach.title ?: "Untitled", color = text, fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp)
                                    Text(studioAwardLine(earned, ach.dateEarned), color = sub, fontSize = 11.sp, lineHeight = 14.sp)
                                }
                                ach.points?.let { pts ->
                                    Box(Modifier.padding(start = 6.dp).background(if (earned) k.accent else k.fill, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)) {
                                        Text("+$pts", color = if (earned) k.onAccent else k.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, softWrap = false)
                                    }
                                }
                            }
                            ach.description?.let { d ->
                                if (opened) Text(d, color = text, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(start = 48.dp, top = 3.dp))
                                else WinFullText(d, color = k.muted, fontSize = 11.sp, maxLines = 1, lineHeight = 14.sp, modifier = Modifier.padding(start = 48.dp, top = 1.dp))
                            }
                            if (opened) ach.unlockPercent?.let { p ->
                                Text("%.1f %% of players have it".format(p), color = sub, fontSize = 11.sp,
                                    modifier = Modifier.padding(start = 48.dp, top = 1.dp, bottom = 2.dp))
                            }
                        }
                    }
                }
            }
            if (t.all.isEmpty()) item {
                Text("No achievements for this game yet.", color = k.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun StudioActions(ctx: WinSceneCtx, live: Boolean, k: StudioInk, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(k.accent), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(),
                focus = if (i == 0) focus else null, live = live) { on ->
                androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                val armed = confirm.armed == i
                val hi = if (o.destructive) StudioRed else k.accent
                val onHi = if (o.destructive) Color.White else k.onAccent
                val shape = RoundedCornerShape(10.dp)
                Column(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().background(if (on) hi else k.fill, shape)
                        .border(1.dp, if (o.destructive) StudioRed else if (on) hi else k.line, shape)
                        .padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        WinFullText(o.label, color = if (on) onHi else if (o.destructive) StudioRed else k.ink, fontSize = 15.sp, maxLines = 2,
                            lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
                        o.sub?.let { WinFullText(it, color = if (on) onHi.copy(alpha = 0.85f) else k.muted, fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp) }
                    }
                    if (armed) Text("Press A again to confirm  ·  move away to cancel", color = Color.White, fontSize = 12.sp, lineHeight = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().padding(top = 3.dp).background(StudioRed, shape).padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
        }
        if (ctx.options.isEmpty()) Text("No options for this game.", color = k.muted, fontSize = 13.sp)
    }
}

/** The panel's page area: the editor's tab strip, then the page, snapped up onto the grid. [short]: a short window - a smaller
 *  strip, so the first achievement stays whole (AchFirstRow). */
@Composable
internal fun StudioPageArea(ctx: WinSceneCtx, k: StudioInk, modifier: Modifier, short: Boolean = false) {
    val heading = rememberHeadingPage(ctx.page)
    Column(modifier) {
        StudioTabs(nativeTabs(ctx.pages), if (heading == WinPage.MAIN) ctx.page else heading, k, short)
        NativePages(ctx, Modifier.fillMaxWidth().weight(1f), label = "studioPage", move = ::studioMove) { pg, pageLive ->
            StudioPage(ctx, pg, pageLive, k, Modifier.fillMaxSize(), short)
        }
    }
}
