package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * P2 (742a, class C): the Cross menu's own pages. The window is the cross menu itself: the game's name in thin white type and
 * a fine rule on the left, over the wave, and the options column sliding in at the right in the home's wash (Dusk / Ocean /
 * Ember or the theme's), its lit row a band. Its pages are read out on the left, in the menu's own thin type:
 *  - Info         = the facts as before.
 *  - Details      = Information: a system-information list - each label thin and grey, right-aligned against a fine
 *                   divider, the value white beside it (stacked on a narrow column) - then the description as one row of the
 *                   menu (a D-pad stop).
 *  - Achievements = Trophies: one line - the cup, "N / M", how far in per cent and a fine progress line (one earned always shows,
 *                   full only when every one is earned) and the order as a band key; each trophy a row of the menu: its icon
 *                   (grown a little on the row you are on, the way the menu's selected item is), the title in thin type,
 *                   "Earned <date>" / "Not earned", "+N"; A shows the whole text and how rare it is.
 *  - Options      = Options: each option a row of the menu; one that removes something is in the warning orange and asks for a
 *                   second A ("Press A again to confirm", NativeConfirm).
 * Heading: the cross bar - one small icon per page along a fine line, the shown one bright and named, after the old page has gone.
 * Motion: the next page slides in along the bar from the side you moved to (fading in); none with Motion Off. On its pages the
 * options column keeps the page slots, Play first, so Play stays put. Shared: data, focus, B, Play-first, inert outgoing copy
 * (NativePageKit).
 */

/** The cross menu's name for a page. Pure. */
internal fun crossPageName(p: WinPage): String = when (p) {
    WinPage.MAIN -> "Game"
    WinPage.DETAILS -> "Information"
    WinPage.TROPHIES -> "Trophies"
    WinPage.OPTIONS -> "Options"
    WinPage.PERF -> "Performance"
}

private fun crossPageIcon(p: WinPage): ImageVector = when (p) {
    WinPage.MAIN -> Icons.Filled.Info
    WinPage.DETAILS -> Icons.Filled.Description
    WinPage.TROPHIES -> Icons.Filled.EmojiEvents
    WinPage.OPTIONS -> Icons.Filled.Tune
    WinPage.PERF -> Icons.Filled.Speed
}

/** The count beside the cup: "N / M" (earned never above the total). Pure. */
internal fun crossAwardCount(earned: Int, total: Int): String = "${earned.coerceIn(0, maxOf(total, 0))} / ${maxOf(total, 0)}"

/** How far in, in whole per cent: 0 only with none earned, 100 only when every one is earned (1 earned of 500 is 1 %, 499 is 99 %). Pure. */
internal fun crossPercent(earned: Int, total: Int): Int {
    if (total <= 0) return 0
    val e = earned.coerceIn(0, total)
    if (e == 0) return 0
    if (e == total) return 100
    return (e * 100 / total).coerceIn(1, 99)
}

/** A trophy's second line: "Earned <date>" / "Earned" when yours, "Not earned" otherwise. Pure. */
internal fun crossAwardLine(earned: Boolean, date: String?): String =
    if (!earned) "Not earned" else date?.takeIf { it.isNotBlank() }?.let { "Earned ${it.trim()}" } ?: "Earned"

/** The next page slides in along the bar from the side you moved to ([dir] > 0: from the right), fading in; the old goes at once. */
internal fun crossMove(dir: Int): ContentTransform {
    val d = if (dir < 0) -1 else 1
    return (fadeIn(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) +
        slideInHorizontally(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) { w -> d * w / 4 }) togetherWith
        fadeOut(tween(NATIVE_OUT_MS))
}

private val CrossWarn = Color(0xFFFF8A3D)
private val CrossBand = RoundedCornerShape(4.dp)

/** The cross menu's ink for its pages: white thin type over the wave, [band] = the options column's lit band. */
internal class CrossInk(val band: Color) {
    val ink = Color.White
    val label = Color.White.copy(alpha = 0.55f)
    val muted = Color.White.copy(alpha = 0.45f)
    val hair = Color.White.copy(alpha = 0.22f)
    val rest = Color.White.copy(alpha = 0.06f)
    val onBand = com.shelf.launcher.ui.theme.textOn(band, Color.White)
}

/** The heading: the cross bar - one small icon per page on a fine line, the shown one bright and named beside it. */
@Composable
internal fun CrossPageHeading(tabs: List<WinPage>, shown: WinPage, k: CrossInk, short: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(bottom = if (short) 3.dp else 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            tabs.forEach { p ->
                val on = p == shown
                Icon(crossPageIcon(p), null, tint = if (on) k.ink else k.muted, modifier = Modifier.size(if (on) (if (short) 18.dp else 22.dp) else 14.dp))
                Spacer(Modifier.width(if (short) 8.dp else 10.dp))
            }
            Text(crossPageName(shown), color = k.ink, fontSize = if (short) 15.sp else 18.sp, fontWeight = FontWeight.Light,
                lineHeight = if (short) 18.sp else 22.sp, maxLines = 2, modifier = Modifier.weight(1f, fill = false))
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(k.hair))
    }
}

/** One of the cross menu's pages (Info is the facts, drawn by CrossOptions). */
@Composable
internal fun AnimatedVisibilityScope.CrossPage(ctx: WinSceneCtx, p: WinPage, live: Boolean, k: CrossInk, modifier: Modifier, narrow: Boolean) {
    when (p) {
        WinPage.DETAILS -> CrossInformation(ctx, live, k, modifier, narrow)
        WinPage.TROPHIES -> CrossTrophies(ctx, live, k, modifier)
        WinPage.OPTIONS -> CrossOptionRows(ctx, live, k, modifier)
        WinPage.MAIN -> {}
        else -> ctx.pageFor(p, modifier, live) // (Performance: the shared page - not on the menu yet)
    }
}

@Composable
private fun CrossInformation(ctx: WinSceneCtx, live: Boolean, k: CrossInk, modifier: Modifier, narrow: Boolean) {
    val focus = nativeListFocus(ctx, live)
    Column(modifier.nativeScroll(k.band)) {
        nativeFacts(ctx).forEach { f ->
            if (narrow) Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                WholeWordsText(f.label, color = k.label, fontSize = 11.sp, lineHeight = 14.sp)
                WholeWordsText(f.value, color = k.ink, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Light)
            } else Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                // The label right-aligned against the divider, the value beside it - the system-information list.
                Box(Modifier.weight(0.38f), contentAlignment = Alignment.TopEnd) {
                    WholeWordsText(f.label, color = k.label, fontSize = 12.sp, lineHeight = 17.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.width(1.dp).height(16.dp).background(k.hair))
                Spacer(Modifier.width(8.dp))
                WholeWordsText(f.value, color = k.ink, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(0.62f))
            }
        }
        Spacer(Modifier.height(8.dp))
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            Column(Modifier.fillMaxWidth().background(if (on) k.band else k.rest, CrossBand).border(1.dp, if (on) k.band else k.hair, CrossBand)
                .padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text("Description", color = if (on) k.onBand else k.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 15.sp)
                Spacer(Modifier.height(2.dp))
                Text(ctx.summary ?: "No description for this game yet.", color = if (on) k.onBand else k.ink, fontSize = 13.sp, lineHeight = 18.sp,
                    fontWeight = FontWeight.Light)
            }
        }
    }
}

@Composable
private fun CrossTrophies(ctx: WinSceneCtx, live: Boolean, k: CrossInk, modifier: Modifier) {
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    Column(modifier) {
        // One line: the cup and the count, the per cent and the fine progress line in the room between, the order key at most its
        // share (its words slide on one line, rule 11) - so the first trophy stays whole at x1.3 (AchFirstRow).
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.EmojiEvents, null, tint = k.ink, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(crossAwardCount(t.earned, t.total), color = k.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp,
                maxLines = 1, softWrap = false)
            val pc = crossPercent(t.earned, t.total)
            Spacer(Modifier.width(6.dp))
            Text("$pc %", color = k.label, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1, softWrap = false)
            Box(Modifier.weight(1f).padding(horizontal = 8.dp).height(3.dp).background(k.hair, RoundedCornerShape(2.dp))) {
                if (pc > 0) Box(Modifier.fillMaxWidth(pc / 100f).height(3.dp).background(k.ink, RoundedCornerShape(2.dp)))
            }
            NativeItem(onClick = t.nextOrder, Modifier.weight(1f, fill = false), focus = focus, live = live) { on ->
                Box(Modifier.background(if (on) k.band else k.rest, CrossBand).border(1.dp, if (on) k.band else k.hair, CrossBand)
                    .padding(horizontal = 10.dp, vertical = 4.dp)) {
                    WinFullText("Order: " + t.order.label, color = if (on) k.onBand else k.ink, fontSize = 11.sp, maxLines = 1, lineHeight = 14.sp)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        NativeRows(k.band, Modifier.weight(1f).fillMaxWidth(), spacing = 3.dp) {
            nativeItems(t.all, { it.id }) { _, ach ->
                val earned = ach.isUnlocked
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { on ->
                        val text = if (on) k.onBand else if (earned) k.ink else k.muted
                        val sub = if (on) k.onBand.copy(alpha = 0.85f) else k.muted
                        Column(Modifier.fillMaxWidth().background(if (on) k.band else k.rest, CrossBand)
                            .border(1.dp, if (on) k.band else Color.Transparent, CrossBand).padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // The icon grows a little on the row you are on - the menu's selected item (drawn larger, not laid out larger).
                                NativeBadge(ach.badgeName, ach.title, earned, k.ink, Modifier.size(32.dp).graphicsLayer {
                                    val s = if (on) 1.12f else 1f; scaleX = s; scaleY = s
                                })
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    WholeWordsText(ach.title ?: "Untitled", color = text, fontSize = 13.sp, fontWeight = if (on) FontWeight.Normal else FontWeight.Light,
                                        lineHeight = 17.sp)
                                    Text(crossAwardLine(earned, ach.dateEarned), color = sub, fontSize = 10.sp, lineHeight = 13.sp)
                                }
                                ach.points?.let { pts ->
                                    Text("+$pts", color = text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, softWrap = false,
                                        modifier = Modifier.padding(start = 6.dp))
                                }
                            }
                            ach.description?.let { d ->
                                if (opened) Text(d, color = text, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(start = 42.dp, top = 3.dp))
                                else WinFullText(d, color = sub, fontSize = 11.sp, maxLines = 1, lineHeight = 14.sp, modifier = Modifier.padding(start = 42.dp, top = 1.dp))
                            }
                            if (opened) ach.unlockPercent?.let { pc ->
                                Text("%.1f %% of players have it".format(pc), color = sub, fontSize = 11.sp,
                                    modifier = Modifier.padding(start = 42.dp, top = 1.dp, bottom = 2.dp))
                            }
                        }
                    }
                }
            }
            if (t.all.isEmpty()) item {
                Text("No trophies for this game yet.", color = k.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun CrossOptionRows(ctx: WinSceneCtx, live: Boolean, k: CrossInk, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(k.band), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(),
                focus = if (i == 0) focus else null, live = live) { on ->
                androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                val armed = confirm.armed == i
                val hi = if (o.destructive) CrossWarn else k.band
                val onHi = if (o.destructive) Color.Black else k.onBand
                Column(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().background(if (on) hi else k.rest, CrossBand)
                        .border(1.dp, if (on) hi else if (o.destructive) CrossWarn.copy(alpha = 0.5f) else k.hair, CrossBand)
                        .padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        WinFullText(o.label, color = if (on) onHi else if (o.destructive) CrossWarn else k.ink, fontSize = 14.sp, maxLines = 2,
                            lineHeight = 18.sp, fontWeight = if (on) FontWeight.Normal else FontWeight.Light)
                        o.sub?.let { WinFullText(it, color = if (on) onHi.copy(alpha = 0.85f) else k.muted, fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp) }
                    }
                    if (armed) Text("Press A again to confirm  ·  move away to cancel", color = Color.Black, fontSize = 12.sp, lineHeight = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp).background(CrossWarn, CrossBand).padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
        }
        if (ctx.options.isEmpty()) Text("No options for this game.", color = k.muted, fontSize = 13.sp)
    }
}

/** The cross menu's page area on the left: the cross bar heading, then the page, slid in by [crossMove]. [short]: a short
 *  window - a smaller heading, so the first trophy stays whole (AchFirstRow); [narrow]: the facts stack. */
@Composable
internal fun CrossPageArea(ctx: WinSceneCtx, k: CrossInk, modifier: Modifier, short: Boolean = false, narrow: Boolean = false) {
    val heading = rememberHeadingPage(ctx.page)
    Column(modifier) {
        CrossPageHeading(nativeTabs(ctx.pages), if (heading == WinPage.MAIN) ctx.page else heading, k, short)
        NativePages(ctx, Modifier.fillMaxWidth().weight(1f), label = "crossPage", move = ::crossMove) { pg, pageLive ->
            CrossPage(ctx, pg, pageLive, k, Modifier.fillMaxSize(), narrow)
        }
    }
}
