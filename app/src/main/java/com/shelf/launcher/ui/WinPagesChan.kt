package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * P2 (727a, class C): the Channel grid window's own pages. The window is the channel's rounded preview card (WiiChannelPreview,
 * in the grid's palette - wiiPaletteNow - and its rim colour), so its pages are drawn in the preview's well as the channel's own
 * screens, in soft rounded cards and pills:
 *  - Info         = the channel's banner (WiiChannelPreviewBody, unchanged; More keeps its page of big keys).
 *  - Details      = CHANNEL INFO: the facts on one rounded info card (label over value, whole words), the story as the
 *                   "About this channel" bubble (a D-pad stop that scrolls).
 *  - Achievements = STAMPS: each achievement a rounded pill with a round stamp on the right - inked in the rim colour with a
 *                   star when you have it, a dashed-looking empty ring with its points when not; "N of M stamps" and the
 *                   order as a pill; A opens its whole text and when / how rare.
 *  - Options      = SETTINGS: each option a big rounded pill (the window's own key shape); one that removes something is
 *                   red and asks for a second A (NativeConfirm).
 * A page header in the well names the screen and shows the channel pager's dots (one per page, the shown one filled).
 * Motion: the next screen slides in from the side as a channel page does (from the right going forward, the left going back)
 * while the old one slides a little the other way and fades; none with Motion Off. Shared: data, focus, B, Play-first, inert
 * outgoing copy (NativePageKit).
 */

/** The channel screen's name for a page. Pure. */
internal fun chanPageName(p: WinPage): String = when (p) {
    WinPage.MAIN -> "Channel"
    WinPage.DETAILS -> "Channel Info"
    WinPage.TROPHIES -> "Stamps"
    WinPage.OPTIONS -> "Settings"
    WinPage.PERF -> "Performance"
}

/** "N of M stamps" (one stamp: "1 of 1 stamp"). Pure. */
internal fun chanStampCount(earned: Int, total: Int): String = "$earned of $total stamp" + if (total == 1) "" else "s"

/** A stamp's points: "N pts" / "1 pt", "—" with none. Pure. */
internal fun chanPoints(points: Int?): String = when (points) { null -> "—"; 1 -> "1 pt"; else -> "$points pts" }

/** Which pager dot is filled for [p] among [tabs] (-1 when the page is not one of them). Pure. */
internal fun chanDot(tabs: List<WinPage>, p: WinPage): Int = tabs.indexOf(p)

internal fun chanMove(dir: Int): ContentTransform =
    (fadeIn(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) +
        slideInHorizontally(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) { w -> if (dir >= 0) w / 4 else -w / 4 }) togetherWith
        (fadeOut(tween(NATIVE_OUT_MS)) + slideOutHorizontally(tween(NATIVE_OUT_MS)) { w -> if (dir >= 0) -w / 8 else w / 8 })

private val CHAN_RED = Color(0xFFD9443A)

/** One of the channel's screens (Info is the banner, drawn by WiiChannelPreviewBody). */
@Composable
internal fun AnimatedVisibilityScope.ChanPage(ctx: WinSceneCtx, p: WinPage, live: Boolean, pal: WiiPalette, rim: Color, modifier: Modifier) {
    Column(modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
        ChanHeader(ctx, p, pal, rim)
        Spacer(Modifier.height(6.dp))
        val body = Modifier.weight(1f).fillMaxWidth()
        when (p) {
            WinPage.DETAILS -> ChanInfo(ctx, live, pal, rim, body)
            WinPage.TROPHIES -> ChanStamps(ctx, live, pal, rim, body)
            WinPage.OPTIONS -> ChanSettings(ctx, live, pal, rim, body)
            WinPage.MAIN -> {}
            else -> ctx.pageFor(p, body, live) // (Performance: the shared page - not a channel screen yet)
        }
    }
}

/** The screen's name on the left, the channel pager's dots on the right. */
@Composable
private fun ChanHeader(ctx: WinSceneCtx, p: WinPage, pal: WiiPalette, rim: Color) {
    val tabs = nativeTabs(ctx.pages)
    val on = chanDot(tabs, p)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        WholeWordsText(chanPageName(p), color = pal.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 19.sp, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            tabs.indices.forEach { i ->
                Box(Modifier.size(if (i == on) 9.dp else 7.dp).clip(CircleShape).background(if (i == on) rim else pal.edge))
            }
        }
    }
}

@Composable
private fun ChanInfo(ctx: WinSceneCtx, live: Boolean, pal: WiiPalette, rim: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val card = RoundedCornerShape(14.dp)
    Column(modifier.nativeScroll(rim)) {
        val facts = nativeFacts(ctx, setOf("Trophies"))
        if (facts.isNotEmpty()) Column(Modifier.fillMaxWidth().clip(card).background(pal.surface).border(1.dp, pal.edge, card).padding(horizontal = 12.dp, vertical = 6.dp)) {
            facts.forEachIndexed { i, f ->
                if (i > 0) Spacer(Modifier.fillMaxWidth().height(1.dp).background(pal.edge.copy(alpha = 0.6f)))
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    WholeWordsText(f.label, color = pal.muted, fontSize = 11.sp, lineHeight = 14.sp)
                    WholeWordsText(f.value, color = pal.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 17.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            val s = RoundedCornerShape(16.dp)
            Column(Modifier.fillMaxWidth().clip(s).background(if (on) pal.keyOn else pal.surface).border(if (on) 3.dp else 1.dp, if (on) rim else pal.edge, s)
                .padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text("About this channel", color = rim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(ctx.summary ?: "This channel has no description yet.", color = pal.ink, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun ChanStamps(ctx: WinSceneCtx, live: Boolean, pal: WiiPalette, rim: Color, modifier: Modifier) {
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WholeWordsText(chanStampCount(t.earned, t.total), color = pal.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 15.sp,
                modifier = Modifier.weight(1f))
            // (the order pill gets at most its share, as the other native pages' order keys - 682r)
            NativeItem(onClick = t.nextOrder, Modifier.weight(1f, fill = false), focus = focus, live = live) { on ->
                val s = RoundedCornerShape(50)
                Text("Order: " + t.order.label, color = pal.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(s).background(if (on) pal.keyOn else pal.key).border(if (on) 3.dp else 1.dp, if (on) rim else pal.edge, s)
                        .padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        NativeRows(rim, Modifier.weight(1f).fillMaxWidth(), spacing = 5.dp) {
            nativeItems(t.all, { it.id }) { _, ach ->
                val earned = ach.isUnlocked
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { on ->
                        val s = RoundedCornerShape(18.dp)
                        Column(Modifier.fillMaxWidth().clip(s).background(if (on) pal.keyOn else pal.surface).border(if (on) 3.dp else 1.dp, if (on) rim else pal.edge, s)
                            .padding(horizontal = 8.dp, vertical = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                NativeBadge(ach.badgeName, ach.title, earned, rim, Modifier.size(30.dp), shape = CircleShape)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    WholeWordsText(ach.title ?: "Untitled", color = if (earned) pal.ink else pal.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp)
                                    ach.description?.let { d ->
                                        if (opened) Text(d, color = pal.ink, fontSize = 12.sp, lineHeight = 16.sp)
                                        else WinFullText(d, color = pal.muted, fontSize = 12.sp, maxLines = 1, lineHeight = 15.sp)
                                    }
                                }
                                Spacer(Modifier.width(6.dp))
                                ChanStamp(earned, ach.points, pal, rim)
                            }
                            if (opened) listOfNotNull(ach.dateEarned?.let { "Stamped $it" }, ach.unlockPercent?.let { "%.1f %% of players have it".format(it) }).forEach { l ->
                                Text(l, color = pal.muted, fontSize = 11.sp, modifier = Modifier.padding(start = 38.dp, top = 2.dp))
                            }
                        }
                    }
                }
            }
            if (t.all.isEmpty()) item {
                Text("No stamps for this channel.", color = pal.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

/** The round stamp on a pill: inked with a star when yours, an empty ring with the points when not. */
@Composable
private fun ChanStamp(earned: Boolean, points: Int?, pal: WiiPalette, rim: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(if (earned) rim else Color.Transparent)
            .border(if (earned) 0.dp else 1.5.dp, if (earned) Color.Transparent else pal.edge, CircleShape), contentAlignment = Alignment.Center) {
            if (earned) Text("★", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, softWrap = false)
        }
        Text(chanPoints(points), color = if (earned) pal.ink else pal.muted, fontSize = 10.sp, softWrap = false)
    }
}

@Composable
private fun ChanSettings(ctx: WinSceneCtx, live: Boolean, pal: WiiPalette, rim: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(rim), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(), focus = if (i == 0) focus else null, live = live) { on ->
                androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                val armed = confirm.armed == i
                val edge = if (o.destructive) CHAN_RED else rim
                val s = RoundedCornerShape(22.dp)
                Column(Modifier.fillMaxWidth().clip(s).background(if (on) pal.keyOn else pal.key)
                    .border(if (on) 3.dp else 1.5.dp, if (on) edge else if (o.destructive) CHAN_RED.copy(alpha = 0.55f) else pal.edge, s)
                    .padding(horizontal = 14.dp, vertical = 7.dp)) {
                    WinFullText(o.label, color = if (o.destructive) CHAN_RED else pal.ink, fontSize = 14.sp, maxLines = 2, fontWeight = FontWeight.Bold, lineHeight = 17.sp)
                    o.sub?.let { WinFullText(it, color = pal.muted, fontSize = 12.sp, maxLines = 2, lineHeight = 15.sp) }
                    if (armed) Text("Press A again to confirm  ·  move away to cancel", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(50)).background(CHAN_RED).padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
        }
        if (ctx.options.isEmpty()) Text("No settings for this channel.", color = pal.muted, fontSize = 13.sp)
    }
}
