package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * P2 (739a, class C): Rental shop's own pages. The window is the shop's bright yellow rental tag hanging on its string beside the
 * case you took off the shelf - heavy black capitals, a barcode and a perforated tear-off, the keys punched in black and the one
 * you are on lit in the shop's neon. Its pages are printed on the same tag, under the title, in the same ink:
 *  - Info         = TAG: the tag as before.
 *  - Details      = CATALOGUE CARD: the game's facts as label-over-value capitals between dashed rules (as the back of a rental case
 *                   lists them), then the story under a black bar headed THE STORY (a D-pad stop).
 *  - Achievements = REWARDS CARD: the shop's member punch card - the cup, "N / M", a row of ten punch holes (one earned always
 *                   punches one, all only when every one is earned) and the order as a black stamp; each achievement a line on
 *                   the card: its square badge, the title, "EARNED <date>" / "NOT YET", "+N PTS"; the line you are on lit in the
 *                   neon; A opens the whole text and how rare it is.
 *  - Options      = COUNTER: the shop counter's choices as black punched rows (the one you are on in the neon); one that removes
 *                   something is printed in the tag's orange and asks for a second A (NativeConfirm).
 * Heading: the page's name in black capitals and a punched hole per page (the shown one filled), after the old page has gone.
 * Motion: the next page drops in from above like the next card slipped onto the tag's string (fading in); none with Motion Off.
 * The tag keeps one size on its pages and its keys keep one row (Play first, two page slots), so Play stays put (PlayPlace).
 * Shared: data, focus, B, Play-first, inert outgoing copy (NativePageKit).
 * No made-up rental period, due date or price - only the game's own facts (update 346).
 */

/** The tag's name for a page. Pure. */
internal fun shopPageName(p: WinPage): String = when (p) {
    WinPage.MAIN -> "Rental tag"
    WinPage.DETAILS -> "Catalogue card" // (the tag's own CATALOGUE CARD key opens it)
    WinPage.TROPHIES -> "Rewards card"
    WinPage.OPTIONS -> "Counter"
    WinPage.PERF -> "Performance"
}

/** The count beside the cup: "N / M" (earned never above the total). Pure. */
internal fun shopAwardCount(earned: Int, total: Int): String = "${earned.coerceIn(0, maxOf(total, 0))} / ${maxOf(total, 0)}"

/** An achievement's line on the card: "EARNED <date>" / "EARNED" when yours, "NOT YET" otherwise. Pure. */
internal fun shopAwardLine(earned: Boolean, date: String?): String =
    if (!earned) "NOT YET" else date?.takeIf { it.isNotBlank() }?.let { "EARNED ${it.trim()}" } ?: "EARNED"

/** How many of the card's [holes] are punched: one earned always punches one, all only when every one is earned. Pure. */
internal fun shopPunched(earned: Int, total: Int, holes: Int): Int {
    if (total <= 0 || holes <= 0) return 0
    val e = earned.coerceIn(0, total)
    if (e == 0) return 0
    if (e == total) return holes
    return (e * holes / total).coerceIn(1, holes - 1)
}

internal const val SHOP_HOLES = 10

/** The next page drops in from above like the next card slipped onto the string (from below going back). */
internal fun shopMove(dir: Int): ContentTransform =
    (fadeIn(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) +
        slideInVertically(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) { h -> (if (dir < 0) h else -h) / 8 }) togetherWith
        fadeOut(tween(NATIVE_OUT_MS))

private val ShopOrange = Color(0xFFFF8A3D)

/** The tag's ink set: the yellow paper, the black ink and the shop's neon (RentalTagCard). */
internal class ShopInk(val paper: Color, val ink: Color, val neon: Color, val onNeon: Color, val stamp: Color) {
    val muted = ink.copy(alpha = 0.68f)
    val rule = ink.copy(alpha = 0.45f)
}

/** A dashed rule across the tag, like the printed ones on a case's back. */
@Composable
private fun ShopDash(k: ShopInk) {
    Canvas(Modifier.fillMaxWidth().height(3.dp)) {
        var x = 0f
        val y = size.height / 2f
        while (x < size.width) { drawLine(k.rule, Offset(x, y), Offset(minOf(x + 7f, size.width), y), strokeWidth = 2f); x += 12f }
    }
}

/** A punch hole: filled (punched through) or an empty ring. */
@Composable
private fun ShopHole(punched: Boolean, k: ShopInk, d: Dp) {
    Canvas(Modifier.size(d)) {
        val r = size.minDimension / 2f
        if (punched) drawCircle(k.ink, r) else drawCircle(k.ink, r - 1.2f, style = Stroke(width = 2.2f))
    }
}

/** The heading: the page's name in black capitals and a hole per page (the shown one punched). */
@Composable
internal fun ShopPageHeading(tabs: List<WinPage>, shown: WinPage, k: ShopInk, short: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(bottom = if (short) 3.dp else 5.dp), verticalAlignment = Alignment.CenterVertically) {
        // (Whole words: a narrow tag at x1.3 wraps the name onto a second line rather than cutting it - rule 11.)
        Text(shopPageName(shown).uppercase(), color = k.ink, fontSize = if (short) 13.sp else 15.sp, fontWeight = FontWeight.Black,
            letterSpacing = 1.sp, lineHeight = if (short) 16.sp else 18.sp, maxLines = 2, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEach { t -> ShopHole(t == shown, k, 8.dp) }
        }
    }
}

/** One of the tag's pages (the tag itself is RentalTagCard's first page). */
@Composable
internal fun AnimatedVisibilityScope.ShopPage(ctx: WinSceneCtx, p: WinPage, live: Boolean, k: ShopInk, modifier: Modifier) {
    when (p) {
        WinPage.DETAILS -> ShopBoxBack(ctx, live, k, modifier)
        WinPage.TROPHIES -> ShopRewards(ctx, live, k, modifier)
        WinPage.OPTIONS -> ShopCounter(ctx, live, k, modifier)
        WinPage.MAIN -> {}
        else -> ctx.pageFor(p, modifier, live) // (Performance: the shared page - not on the tag yet)
    }
}

@Composable
private fun ShopBoxBack(ctx: WinSceneCtx, live: Boolean, k: ShopInk, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    Column(modifier.nativeScroll(k.ink), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        nativeFacts(ctx).forEach { f ->
            Column(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                WholeWordsText(f.label.uppercase(), color = k.muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, lineHeight = 12.sp)
                WholeWordsText(f.value.uppercase(), color = k.ink, fontSize = 12.sp, fontWeight = FontWeight.Black, lineHeight = 15.sp)
            }
            ShopDash(k)
        }
        Spacer(Modifier.height(4.dp))
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            Column(Modifier.fillMaxWidth().border(2.dp, if (on) k.neon else k.ink)) {
                Box(Modifier.fillMaxWidth().background(if (on) k.neon else k.ink).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text("THE STORY", color = if (on) k.onNeon else k.paper, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp,
                        lineHeight = 13.sp)
                }
                Text(ctx.summary ?: "No description for this game yet.", color = k.ink, fontSize = 12.sp, lineHeight = 16.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
            }
        }
    }
}

@Composable
private fun ShopRewards(ctx: WinSceneCtx, live: Boolean, k: ShopInk, modifier: Modifier) {
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    Column(modifier) {
        // One line: the cup and the count, the punch holes in the room between, the order stamp at most its share (682r; its words
        // slide on one line, rule 11) - so the first achievement stays whole at x1.3 (AchFirstRow).
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.EmojiEvents, null, tint = k.ink, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(3.dp))
            Text(shopAwardCount(t.earned, t.total), color = k.ink, fontSize = 13.sp, fontWeight = FontWeight.Black, lineHeight = 16.sp,
                maxLines = 1, softWrap = false)
            val punched = shopPunched(t.earned, t.total, SHOP_HOLES)
            Row(Modifier.weight(1f).padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically) {
                repeat(SHOP_HOLES) { i -> ShopHole(i < punched, k, 7.dp) }
            }
            NativeItem(onClick = t.nextOrder, Modifier.weight(1f, fill = false), focus = focus, live = live) { on ->
                Box(Modifier.background(if (on) k.neon else k.ink).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    WinFullText("ORDER: " + t.order.label.uppercase(), color = if (on) k.onNeon else k.paper, fontSize = 10.sp,
                        fontWeight = FontWeight.Black, maxLines = 1, lineHeight = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(k.ink))
        Spacer(Modifier.height(3.dp))
        NativeRows(k.ink, Modifier.weight(1f).fillMaxWidth(), spacing = 2.dp) {
            nativeItems(t.all, { it.id }) { _, ach ->
                val earned = ach.isUnlocked
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { on ->
                        val text = if (on) k.onNeon else if (earned) k.ink else k.muted
                        Column(Modifier.fillMaxWidth().background(if (on) k.neon else Color.Transparent).padding(start = 2.dp, end = 6.dp, top = 3.dp, bottom = 3.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                NativeBadge(ach.badgeName, ach.title, earned, k.ink, Modifier.size(28.dp), shape = RectangleShape)
                                Spacer(Modifier.width(7.dp))
                                Column(Modifier.weight(1f)) {
                                    WholeWordsText((ach.title ?: "Untitled").uppercase(), color = text, fontSize = 12.sp, fontWeight = FontWeight.Black, lineHeight = 15.sp)
                                    Text(shopAwardLine(earned, ach.dateEarned), color = if (on) k.onNeon else k.muted, fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp, lineHeight = 12.sp)
                                }
                                ach.points?.let { pts ->
                                    Text("+$pts PTS", color = text, fontSize = 10.sp, fontWeight = FontWeight.Black, softWrap = false,
                                        modifier = Modifier.padding(start = 5.dp))
                                }
                            }
                            ach.description?.let { d ->
                                if (opened) Text(d, color = text, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(start = 35.dp, top = 3.dp))
                                else WinFullText(d, color = if (on) k.onNeon else k.muted, fontSize = 10.sp, maxLines = 1, lineHeight = 13.sp,
                                    modifier = Modifier.padding(start = 35.dp, top = 1.dp))
                            }
                            if (opened) ach.unlockPercent?.let { pc ->
                                Text("%.1f %% OF PLAYERS HAVE IT".format(pc), color = text, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 35.dp, top = 1.dp, bottom = 2.dp))
                            }
                        }
                    }
                }
            }
            if (t.all.isEmpty()) item {
                Text("NO ACHIEVEMENTS FOR THIS GAME YET.", color = k.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun ShopCounter(ctx: WinSceneCtx, live: Boolean, k: ShopInk, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(k.ink), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(),
                focus = if (i == 0) focus else null, live = live) { on ->
                androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                val armed = confirm.armed == i
                Column(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().background(if (on) k.neon else k.ink).padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        WinFullText(o.label.uppercase(), color = if (on) k.onNeon else if (o.destructive) ShopOrange else k.paper, fontSize = 13.sp,
                            maxLines = 2, lineHeight = 16.sp, fontWeight = FontWeight.Black)
                        o.sub?.let { WinFullText(it, color = if (on) k.onNeon else k.paper.copy(alpha = 0.8f), fontSize = 10.sp, maxLines = 2, lineHeight = 13.sp) }
                    }
                    if (armed) Text("PRESS A AGAIN TO REMOVE  ·  MOVE AWAY TO CANCEL", color = ShopOrange, fontSize = 11.sp, fontWeight = FontWeight.Black,
                        lineHeight = 14.sp, modifier = Modifier.fillMaxWidth().padding(top = 2.dp).background(k.ink).padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
        }
        if (ctx.options.isEmpty()) Text("NOTHING TO DO AT THE COUNTER FOR THIS GAME.", color = k.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** A punched key on the tag's pages - the same black key as on the tag's first page, lit in the neon. */
@Composable
internal fun ShopPageKey(a: WinAction, k: ShopInk, kh: Dp) {
    SceneControl(a, Modifier) { on ->
        Row(Modifier.uniformFill().heightIn(min = kh).background(if (on) k.neon else k.ink).padding(horizontal = if (a.primary) 10.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(a.icon, null, tint = if (on) k.onNeon else k.paper, modifier = Modifier.size(if (a.primary) 15.dp else 12.dp))
            Spacer(Modifier.width(if (a.primary) 7.dp else 4.dp))
            Text(rentalKeyLabel(a.label, a.primary), color = if (on) k.onNeon else if (a.urgent) ShopOrange else k.paper,
                fontSize = if (a.primary) 14.sp else 11.sp, fontWeight = FontWeight.Black, maxLines = 2)
        }
    }
}

/** The tag's page area: the heading, then the page, dropped in by [shopMove]. [short]: a short tag - a smaller heading, so the
 *  first achievement stays whole (AchFirstRow). */
@Composable
internal fun ShopPageArea(ctx: WinSceneCtx, k: ShopInk, modifier: Modifier, short: Boolean = false) {
    val heading = rememberHeadingPage(ctx.page)
    Column(modifier) {
        ShopPageHeading(nativeTabs(ctx.pages), if (heading == WinPage.MAIN) ctx.page else heading, k, short)
        NativePages(ctx, Modifier.fillMaxWidth().weight(1f), label = "shopPage", move = ::shopMove) { pg, pageLive ->
            ShopPage(ctx, pg, pageLive, k, Modifier.fillMaxSize())
        }
    }
}
