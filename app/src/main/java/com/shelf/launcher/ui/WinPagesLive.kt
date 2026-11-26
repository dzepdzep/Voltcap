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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * P2 (743a, class C): the Live tiles window's own pages. The window is a flat dark panel turning in like a turnstile from its
 * left edge: the console in lower case in your accent, the name in large thin lower case, the art as a square tile in the
 * accent, the facts beside it and the keys as small flat tiles (Play in the accent). Its pages keep that flat tile language:
 *  - Info         = the art tile and the facts, as before.
 *  - Details      = about: each fact as a small lower-case label in the accent over its value in thin white type, stacked like
 *                   a flat list; then the description on a flat tile (a D-pad stop, filled in the accent when you are on it).
 *  - Achievements = achievements: the big thin per cent, "N of M" in lower case and a flat bar in the accent (one earned always
 *                   shows, full only when every one is earned), the order as a flat tile; each achievement a flat row - its
 *                   badge on a square tile (the accent when yours), the title in thin type, "earned <date>" / "not yet", "+N";
 *                   the row you are on filled in the accent; A shows the whole text and how rare it is.
 *  - Options      = options: each option a flat tile; one that removes something is a red tile and asks for a second A
 *                   ("press A again to confirm", NativeConfirm).
 * Heading: a pivot - the page names in large thin lower case, the shown one white and first, the others after it in turn, dim -
 * after the old page has gone. Motion: the next page turns in like the panel itself, a turnstile from its left edge (rotated
 * 60 degrees -> flat, fading in); none with Motion Off. On its pages the key row keeps the first page's slots, Play first, so
 * Play stays put. Shared: data, focus, B, Play-first, inert outgoing copy (NativePageKit).
 */

/** The Live tiles' name for a page (lower case, as the panel writes). Pure. */
internal fun livePageName(p: WinPage): String = when (p) {
    WinPage.MAIN -> "game"
    WinPage.DETAILS -> "about"
    WinPage.TROPHIES -> "achievements"
    WinPage.OPTIONS -> "options"
    WinPage.PERF -> "performance"
}

/** The pivot's order: the shown page first, then the pages after it in turn, wrapping round ([shown] not in [tabs]: as given). Pure. */
internal fun livePivot(tabs: List<WinPage>, shown: WinPage): List<WinPage> {
    val i = tabs.indexOf(shown)
    if (i <= 0) return tabs
    return tabs.drop(i) + tabs.take(i)
}

/** "N of M" (earned never above the total, nothing below 0). Pure. */
internal fun liveAwardCount(earned: Int, total: Int): String = "${earned.coerceIn(0, maxOf(total, 0))} of ${maxOf(total, 0)}"

/** How far in, whole per cent: 0 only with none earned, 100 only when every one is earned (1 of 500 is 1 %, 499 is 99 %). Pure. */
internal fun livePercent(earned: Int, total: Int): Int {
    if (total <= 0) return 0
    val e = earned.coerceIn(0, total)
    if (e == 0) return 0
    if (e == total) return 100
    return (e * 100 / total).coerceIn(1, 99)
}

/** An achievement's second line in lower case: "earned <date>" / "earned" when yours, "not yet" otherwise. Pure. */
internal fun liveAwardLine(earned: Boolean, date: String?): String =
    if (!earned) "not yet" else date?.takeIf { it.isNotBlank() }?.let { "earned ${it.trim()}" } ?: "earned"

/** The page change: the new page fades in while it turns (LivePage's turnstile); the old one goes at once. */
internal fun liveMove(@Suppress("UNUSED_PARAMETER") dir: Int): ContentTransform =
    fadeIn(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) togetherWith fadeOut(tween(NATIVE_OUT_MS))

private val LiveTile = Color(0xFF2A2A2A)
private val LiveRed = Color(0xFFE51400)

/** The panel's ink for its pages: [accent] = your accent (the art tile, Play, the lit row). */
internal class LiveInk(val accent: Color) {
    val ink = Color.White
    val muted = Color.White.copy(alpha = 0.5f)
    val dim = Color.White.copy(alpha = 0.3f)
    val tile = LiveTile
    val onAccent = com.shelf.launcher.ui.theme.textOn(accent, Color.White)
}

/** The pivot heading: the shown page's name white and first, the others after it dim, in large thin lower case; wraps, never cut. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun LivePivot(tabs: List<WinPage>, shown: WinPage, k: LiveInk, short: Boolean = false) {
    androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().padding(bottom = if (short) 4.dp else 8.dp),
        horizontalArrangement = Arrangement.spacedBy(if (short) 12.dp else 16.dp)) {
        livePivot(tabs, shown).forEach { p ->
            val on = p == shown
            Text(livePageName(p), color = if (on) k.ink else k.dim, fontSize = if (short) 18.sp else 24.sp, lineHeight = if (short) 22.sp else 28.sp,
                fontWeight = FontWeight.Light, maxLines = 1, softWrap = false)
        }
    }
}

/** One of the panel's pages (Info is the art tile and facts, drawn by LiveTilesPanel), turned in like a turnstile. */
@Composable
internal fun AnimatedVisibilityScope.LivePage(ctx: WinSceneCtx, p: WinPage, live: Boolean, k: LiveInk, modifier: Modifier) {
    val anim = rememberAnimationsEnabled()
    val turn by transition.animateFloat(transitionSpec = { if (anim) tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing) else snap() }, label = "liveTurn") {
        if (it == EnterExitState.Visible) 1f else 0f
    }
    val m = modifier.graphicsLayer {
        // The turnstile: hinged on the left edge, from 60 degrees to flat (the old page has already gone - NATIVE_OUT_MS).
        rotationY = (1f - turn) * 60f
        transformOrigin = TransformOrigin(0f, 0.5f)
        cameraDistance = 10f * density
        // (Update 489, D285: turned text drawn flat - see flatTextFor3d.)
        flatTextFor3d()
    }
    when (p) {
        WinPage.DETAILS -> LiveAbout(ctx, live, k, m)
        WinPage.TROPHIES -> LiveAchievements(ctx, live, k, m)
        WinPage.OPTIONS -> LiveOptions(ctx, live, k, m)
        WinPage.MAIN -> {}
        else -> ctx.pageFor(p, modifier, live) // (Performance: the shared page - not a page of the panel yet)
    }
}

@Composable
private fun LiveAbout(ctx: WinSceneCtx, live: Boolean, k: LiveInk, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    Column(modifier.nativeScroll(k.accent), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        nativeFacts(ctx).forEach { f ->
            Column(Modifier.fillMaxWidth()) {
                WholeWordsText(f.label.lowercase(), color = k.accent, fontSize = 12.sp, lineHeight = 15.sp)
                WholeWordsText(f.value, color = k.ink, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Light)
            }
        }
        Spacer(Modifier.height(4.dp))
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            Column(Modifier.fillMaxWidth().background(if (on) k.accent else k.tile, RectangleShape).padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text("description", color = if (on) k.onAccent else k.accent, fontSize = 12.sp, lineHeight = 15.sp)
                Spacer(Modifier.height(2.dp))
                Text(ctx.summary ?: "No description for this game yet.", color = if (on) k.onAccent else k.ink, fontSize = 14.sp, lineHeight = 19.sp,
                    fontWeight = FontWeight.Light)
            }
        }
    }
}

@Composable
private fun LiveAchievements(ctx: WinSceneCtx, live: Boolean, k: LiveInk, modifier: Modifier) {
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    val pc = livePercent(t.earned, t.total)
    Column(modifier) {
        // One line: the big thin per cent, "N of M", the order as a flat tile at most its share (its words slide, rule 11) - so the
        // first achievement stays whole at x1.3 (AchFirstRow); the flat bar under it.
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$pc%", color = k.ink, fontSize = 28.sp, lineHeight = 30.sp, fontWeight = FontWeight.Light, maxLines = 1, softWrap = false)
            Spacer(Modifier.width(8.dp))
            Text(liveAwardCount(t.earned, t.total), color = k.muted, fontSize = 13.sp, lineHeight = 16.sp, maxLines = 1, softWrap = false,
                modifier = Modifier.padding(bottom = 4.dp))
            Spacer(Modifier.width(12.dp))
            NativeItem(onClick = t.nextOrder, Modifier.weight(1f, fill = false), focus = focus, live = live) { on ->
                Box(Modifier.background(if (on) k.accent else k.tile, RectangleShape).padding(horizontal = 10.dp, vertical = 5.dp)) {
                    WinFullText("order: " + t.order.label.lowercase(), color = if (on) k.onAccent else k.ink, fontSize = 12.sp, maxLines = 1, lineHeight = 15.sp)
                }
            }
        }
        Box(Modifier.padding(top = 4.dp, bottom = 6.dp).fillMaxWidth().height(4.dp).background(k.tile)) {
            if (pc > 0) Box(Modifier.fillMaxWidth(pc / 100f).height(4.dp).background(k.accent))
        }
        NativeRows(k.accent, Modifier.weight(1f).fillMaxWidth(), spacing = 2.dp) {
            nativeItems(t.all, { it.id }) { _, ach ->
                val earned = ach.isUnlocked
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { on ->
                        val text = if (on) k.onAccent else if (earned) k.ink else k.muted
                        val sub = if (on) k.onAccent.copy(alpha = 0.85f) else if (earned) k.accent else k.dim
                        Column(Modifier.fillMaxWidth().background(if (on) k.accent else Color.Transparent, RectangleShape)
                            .padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // The badge on a square tile: the accent when yours, the flat grey when not.
                                Box(Modifier.size(36.dp).background(if (earned) (if (on) k.tile else k.accent) else k.tile), contentAlignment = Alignment.Center) {
                                    NativeBadge(ach.badgeName, ach.title, earned, k.ink, Modifier.size(30.dp), shape = RectangleShape)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    WholeWordsText(ach.title ?: "Untitled", color = text, fontSize = 14.sp, fontWeight = FontWeight.Light, lineHeight = 18.sp)
                                    Text(liveAwardLine(earned, ach.dateEarned), color = sub, fontSize = 11.sp, lineHeight = 14.sp)
                                }
                                ach.points?.let { pts ->
                                    Text("+$pts", color = text, fontSize = 13.sp, fontWeight = FontWeight.Light, softWrap = false,
                                        modifier = Modifier.padding(start = 6.dp))
                                }
                            }
                            ach.description?.let { d ->
                                if (opened) Text(d, color = text, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(start = 46.dp, top = 3.dp))
                                else WinFullText(d, color = sub, fontSize = 11.sp, maxLines = 1, lineHeight = 14.sp, modifier = Modifier.padding(start = 46.dp, top = 1.dp))
                            }
                            if (opened) ach.unlockPercent?.let { p ->
                                Text("%.1f %% of players have it".format(p), color = sub, fontSize = 11.sp,
                                    modifier = Modifier.padding(start = 46.dp, top = 1.dp, bottom = 2.dp))
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
private fun LiveOptions(ctx: WinSceneCtx, live: Boolean, k: LiveInk, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(k.accent), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(),
                focus = if (i == 0) focus else null, live = live) { on ->
                androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                val armed = confirm.armed == i
                val hi = if (o.destructive) LiveRed else k.accent
                val onHi = if (o.destructive) Color.White else k.onAccent
                Column(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().background(if (on) hi else k.tile, RectangleShape)
                        .then(if (!on && o.destructive) Modifier.border(1.dp, LiveRed, RectangleShape) else Modifier)
                        .padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        WinFullText(o.label, color = if (on) onHi else k.ink, fontSize = 15.sp, maxLines = 2,
                            lineHeight = 19.sp, fontWeight = FontWeight.Light)
                        o.sub?.let { WinFullText(it, color = if (on) onHi.copy(alpha = 0.85f) else k.muted, fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp) }
                    }
                    if (armed) Text("press A again to confirm  ·  move away to cancel", color = Color.White, fontSize = 12.sp, lineHeight = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp).background(LiveRed, RectangleShape).padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
        }
        if (ctx.options.isEmpty()) Text("No options for this game.", color = k.muted, fontSize = 13.sp)
    }
}

/** The panel's page area: the pivot heading, then the page, turned in like a turnstile. [short]: a short window - a smaller
 *  pivot, so the first achievement stays whole (AchFirstRow). */
@Composable
internal fun LivePageArea(ctx: WinSceneCtx, k: LiveInk, modifier: Modifier, short: Boolean = false) {
    val heading = rememberHeadingPage(ctx.page)
    Column(modifier) {
        LivePivot(nativeTabs(ctx.pages), if (heading == WinPage.MAIN) ctx.page else heading, k, short)
        NativePages(ctx, Modifier.fillMaxWidth().weight(1f), label = "livePage", move = ::liveMove) { pg, pageLive ->
            LivePage(ctx, pg, pageLive, k, Modifier.fillMaxSize())
        }
    }
}
