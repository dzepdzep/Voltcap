package com.shelf.launcher.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * P2 (738a, class C): Panel dashboard's own pages. The window is one big flat panel swung round out of its row - the wide art
 * strip with the name and a bar in the dashboard's colour, the commands as flat rows down the left (the one you're on lit in
 * that colour), the cover and facts on the right. Its pages take the right side, in the same flat panel language:
 *  - Info         = GAME: the panel as before.
 *  - Details      = OVERVIEW: each fact a flat tile with a bar in the dashboard's colour down its left (label over value),
 *                   then the story in a flat panel headed ABOUT under a bar in that colour (a D-pad stop).
 *  - Achievements = ACHIEVEMENTS: the cup, "N / M", a flat segmented meter (one earned always lights a segment, all lit only when
 *                   all are earned) and the order as a flat tile; each achievement a flat row - its square badge, the title,
 *                   "Unlocked <date>" / "Locked", the points; the row you are on lit like the commands; A opens the whole
 *                   text and how rare it is.
 *  - Options      = MANAGE: flat rows like the command column (lit in the dashboard's colour); one that removes something is
 *                   written in the warning orange and asks for a second A (NativeConfirm).
 * Heading: the page's name and a flat segment per page (the shown one long, in the dashboard's colour) - after the old page has
 * gone. Motion: the next page slides along from the right like the next panel of the row coming round (fading in); none with
 * Motion Off. The commands stay down the left (Play the first, the tools only on the panel's first page as before).
 * Shared: data, focus, B, Play-first, inert outgoing copy (NativePageKit).
 */

/** The dashboard's name for a page. Pure. */
internal fun panelPageName(p: WinPage): String = when (p) {
    WinPage.MAIN -> "Game"
    WinPage.DETAILS -> "Overview"
    WinPage.TROPHIES -> "Achievements"
    WinPage.OPTIONS -> "Manage"
    WinPage.PERF -> "Performance"
}

/** The count beside the cup: "N / M" (earned never above the total). Pure. */
internal fun panelAwardCount(earned: Int, total: Int): String = "${earned.coerceIn(0, maxOf(total, 0))} / ${maxOf(total, 0)}"

/** An achievement's line: "Unlocked <date>" / "Unlocked" when yours, "Locked" otherwise. Pure. */
internal fun panelAwardLine(earned: Boolean, date: String?): String =
    if (!earned) "Locked" else date?.takeIf { it.isNotBlank() }?.let { "Unlocked ${it.trim()}" } ?: "Unlocked"

/** How many of the meter's [segments] are lit: one earned always lights one, all only when every one is earned. Pure. */
internal fun panelMeterLit(earned: Int, total: Int, segments: Int): Int {
    if (total <= 0 || segments <= 0) return 0
    val e = earned.coerceIn(0, total)
    if (e == 0) return 0
    if (e == total) return segments
    return (e * segments / total).coerceIn(1, segments - 1)
}

internal const val PANEL_SEGMENTS = 10

internal fun panelMove(dir: Int): ContentTransform =
    (fadeIn(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) +
        slideInHorizontally(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) { w -> (if (dir < 0) -w else w) / 6 }) togetherWith
        fadeOut(tween(NATIVE_OUT_MS))

private val PdWarn = Color(0xFFC0700A)

/** The panel's ink set for its pages (from panelPalette - light or graphite). */
internal class PanelInk(val ink: Color, val accent: Color, val panel: Color) {
    val muted = ink.copy(alpha = 0.62f)
    val tile = ink.copy(alpha = 0.06f)
    val line = ink.copy(alpha = 0.16f)
    val lit = lerp(accent, Color.Black, 0.28f)
}

/** The heading: the page's name and a flat segment per page (the shown one long, in the dashboard's colour). */
@Composable
internal fun PanelPageHeading(tabs: List<WinPage>, shown: WinPage, k: PanelInk, compact: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(bottom = if (compact) 4.dp else 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(panelPageName(shown), color = k.ink, fontSize = if (compact) 15.sp else 17.sp, fontWeight = FontWeight.SemiBold,
                lineHeight = if (compact) 18.sp else 21.sp, maxLines = 1, softWrap = false)
            Spacer(Modifier.width(10.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                tabs.forEach { t ->
                    val on = t == shown
                    Box(Modifier.width(if (on) 20.dp else 9.dp).height(4.dp).background(if (on) k.accent else k.line))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(k.line))
    }
}

/** One of the panel's pages (Game is the panel itself, drawn by PanelDetail). */
@Composable
internal fun AnimatedVisibilityScope.PanelPage(ctx: WinSceneCtx, p: WinPage, live: Boolean, k: PanelInk, modifier: Modifier) {
    when (p) {
        WinPage.DETAILS -> PanelOverview(ctx, live, k, modifier)
        WinPage.TROPHIES -> PanelAchievements(ctx, live, k, modifier)
        WinPage.OPTIONS -> PanelManage(ctx, live, k, modifier)
        WinPage.MAIN -> {}
        else -> ctx.pageFor(p, modifier, live) // (Performance: the shared page - not in the panel yet)
    }
}

@Composable
private fun PanelOverview(ctx: WinSceneCtx, live: Boolean, k: PanelInk, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    Column(modifier.nativeScroll(k.accent), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        nativeFacts(ctx).forEach { f ->
            Row(Modifier.fillMaxWidth().background(k.tile)) {
                Box(Modifier.width(3.dp).height(36.dp).background(k.accent))
                Column(Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    WholeWordsText(f.label, color = k.muted, fontSize = 11.sp, lineHeight = 14.sp)
                    WholeWordsText(f.value, color = k.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 17.sp)
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            Column(Modifier.fillMaxWidth().background(k.tile).border(if (on) 2.dp else 0.dp, if (on) k.accent else Color.Transparent)) {
                Box(Modifier.fillMaxWidth().height(3.dp).background(k.accent))
                Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text("ABOUT", color = if (on) k.accent else k.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 14.sp)
                    Text(ctx.summary ?: "No description for this game yet.", color = k.ink, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun PanelAchievements(ctx: WinSceneCtx, live: Boolean, k: PanelInk, modifier: Modifier) {
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    Column(modifier) {
        // One line: the cup and the count, the segmented meter in the room between, the order tile at most its share (682r; its
        // words slide on one line, rule 11) - so the first achievement stays whole at x1.3 (AchFirstRow).
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.EmojiEvents, null, tint = k.accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(panelAwardCount(t.earned, t.total), color = k.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp,
                maxLines = 1, softWrap = false)
            val lit = panelMeterLit(t.earned, t.total, PANEL_SEGMENTS)
            Row(Modifier.weight(1f).padding(horizontal = 8.dp).height(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(PANEL_SEGMENTS) { i -> Box(Modifier.weight(1f).fillMaxHeight().background(if (i < lit) k.accent else k.line)) }
            }
            NativeItem(onClick = t.nextOrder, Modifier.weight(1f, fill = false), focus = focus, live = live) { on ->
                Box(Modifier.background(if (on) k.lit else k.tile).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    WinFullText("Order: " + t.order.label, color = if (on) Color.White else k.ink, fontSize = 12.sp, maxLines = 1, lineHeight = 15.sp)
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        NativeRows(k.accent, Modifier.weight(1f).fillMaxWidth(), spacing = 2.dp) {
            nativeItems(t.all, { it.id }) { _, ach ->
                val earned = ach.isUnlocked
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { on ->
                        val text = if (on) Color.White else if (earned) k.ink else k.muted
                        val sub = if (on) Color.White.copy(alpha = 0.85f) else k.muted
                        Column(Modifier.fillMaxWidth().background(if (on) k.lit else k.tile).padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                NativeBadge(ach.badgeName, ach.title, earned, k.accent, Modifier.size(30.dp), shape = androidx.compose.ui.graphics.RectangleShape)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    WholeWordsText(ach.title ?: "Untitled", color = text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp)
                                    Text(panelAwardLine(earned, ach.dateEarned), color = if (on) sub else if (earned) legibleOn(k.accent, k.panel) else k.muted,
                                        fontSize = 11.sp, lineHeight = 14.sp)
                                }
                                ach.points?.let { pts ->
                                    Text("$pts", color = if (on) Color.White else if (earned) k.ink else k.muted, fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold, softWrap = false, modifier = Modifier.padding(start = 6.dp))
                                }
                            }
                            ach.description?.let { d ->
                                if (opened) Text(d, color = text, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(start = 38.dp, top = 3.dp))
                                else WinFullText(d, color = sub, fontSize = 11.sp, maxLines = 1, lineHeight = 14.sp, modifier = Modifier.padding(start = 38.dp, top = 1.dp))
                            }
                            if (opened) ach.unlockPercent?.let { pc ->
                                Text("%.1f %% of players have it".format(pc), color = if (on) Color.White else PdWarn, fontSize = 11.sp,
                                    modifier = Modifier.padding(start = 38.dp, top = 1.dp, bottom = 2.dp))
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
private fun PanelManage(ctx: WinSceneCtx, live: Boolean, k: PanelInk, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(k.accent), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(),
                focus = if (i == 0) focus else null, live = live) { on ->
                androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                val armed = confirm.armed == i
                Column(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().background(if (on) (if (o.destructive) PdWarn else k.lit) else k.tile)
                        .padding(horizontal = 12.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        WinFullText(o.label, color = if (on) Color.White else if (o.destructive) PdWarn else k.ink, fontSize = 14.sp, maxLines = 2,
                            lineHeight = 18.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                        o.sub?.let { WinFullText(it, color = if (on) Color.White.copy(alpha = 0.85f) else k.muted, fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp) }
                    }
                    if (armed) Text("Press A again to remove  ·  move away to cancel", color = Color.White, fontSize = 12.sp, lineHeight = 16.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp).background(PdWarn).padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
        }
        if (ctx.options.isEmpty()) Text("Nothing to manage for this game.", color = k.muted, fontSize = 13.sp)
    }
}

/** The panel's page area: the heading, then the page, slid along by [panelMove]. [compact]: a short panel (under 380 dp) - a
 *  smaller heading, so the first achievement stays whole (AchFirstRow). */
@Composable
internal fun PanelPageArea(ctx: WinSceneCtx, k: PanelInk, modifier: Modifier, compact: Boolean = false) {
    val heading = rememberHeadingPage(ctx.page)
    Column(modifier) {
        PanelPageHeading(nativeTabs(ctx.pages), if (heading == WinPage.MAIN) ctx.page else heading, k, compact)
        NativePages(ctx, Modifier.fillMaxWidth().weight(1f), label = "panelPage", move = ::panelMove) { pg, pageLive ->
            PanelPage(ctx, pg, pageLive, k, Modifier.fillMaxSize())
        }
    }
}
