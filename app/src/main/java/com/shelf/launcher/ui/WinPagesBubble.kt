package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * P2 (736a, class C): Bubble home's own pages. The window is the LiveArea card - the game's art behind, its round bubble and
 * name, the white Start plate and the round-ended white keys down the left, a white info panel on the right. Its pages take
 * that white panel, drawn in the same language:
 *  - Info         = GAME: the card as before.
 *  - Details      = INFO: the facts as label / value lines on the white panel with hairlines between, then the story in a pale
 *                   blue-grey rounded box (a D-pad stop).
 *  - Achievements = AWARDS: the gold cup, "N / M", a slim round-ended meter in the card's blue and the order as a round-ended
 *                   key; each award a round-ended white key - its badge in a round bubble, the title, "Earned <date>" /
 *                   "Not yet", the points in a gold ring; A opens the whole text and how rare it is.
 *  - Options      = SETTINGS: round-ended white keys like the card's own (the same white-to-grey face, the blue ring when you are
 *                   on one); one that removes something writes in the warning orange and asks for a second A (NativeConfirm).
 * Heading: the page's name and a bubble per page (the shown one filled in the blue with a white ring) - after the old page has
 * gone. Motion: the next page swells out like a bubble from the heading's left (from 94 %, fading in); none with Motion Off.
 * The keys stay down the left (Play the first, the tools only on the card's first page as before).
 * Shared: data, focus, B, Play-first, inert outgoing copy (NativePageKit).
 */

/** The card's name for a page. Pure. */
internal fun bubblePageName(p: WinPage): String = when (p) {
    WinPage.MAIN -> "Game"
    WinPage.DETAILS -> "Info"
    WinPage.TROPHIES -> "Awards"
    WinPage.OPTIONS -> "Settings"
    WinPage.PERF -> "Performance"
}

/** The count beside the cup: "N / M" (earned never above the total). Pure. */
internal fun bubbleAwardCount(earned: Int, total: Int): String = "${earned.coerceIn(0, maxOf(total, 0))} / ${maxOf(total, 0)}"

/** An award's line: "Earned <date>" / "Earned" when yours, "Not yet" otherwise. Pure. */
internal fun bubbleAwardLine(earned: Boolean, date: String?): String =
    if (!earned) "Not yet" else date?.takeIf { it.isNotBlank() }?.let { "Earned ${it.trim()}" } ?: "Earned"

/** The meter's filled share, 0..1. Pure. */
internal fun bubbleMeter(earned: Int, total: Int): Float = if (total <= 0) 0f else earned.coerceIn(0, total).toFloat() / total

internal fun bubbleMove(@Suppress("UNUSED_PARAMETER") dir: Int): ContentTransform =
    (fadeIn(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) +
        scaleIn(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing), initialScale = 0.94f, transformOrigin = TransformOrigin(0.1f, 0.05f))) togetherWith
        fadeOut(tween(NATIVE_OUT_MS))

private val BhInk = Color(0xFF1A2633)
private val BhMuted = Color(0xFF5B6B7C)
private val BhLine = Color(0xFFD5DEE8)
private val BhWell = Color(0xFFEDF2F7)
private val BhGold = Color(0xFFE0A400)
private val BhWarn = Color(0xFFB0630A)
private val BhKey = RoundedCornerShape(50)
private val BhBox = RoundedCornerShape(6.dp)
private val BhFace = Brush.verticalGradient(listOf(Color.White, Color(0xFFDDE5EE)))

/** The heading: the page's name and a bubble per page (the shown one filled in the blue with a white ring). */
@Composable
internal fun BubblePageHeading(tabs: List<WinPage>, shown: WinPage, blue: Color, compact: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(bottom = if (compact) 4.dp else 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(bubblePageName(shown), color = BhInk, fontSize = if (compact) 15.sp else 17.sp, fontWeight = FontWeight.Bold,
            lineHeight = if (compact) 18.sp else 21.sp, maxLines = 1, softWrap = false)
        Spacer(Modifier.width(10.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEach { t ->
                val on = t == shown
                Box(Modifier.size(if (on) 11.dp else 8.dp).clip(CircleShape).background(if (on) blue else BhLine)
                    .border(if (on) 2.dp else 0.dp, Color.White, CircleShape))
            }
        }
    }
}

/** One of the card's pages (Game is the card itself, drawn by BubbleCard). */
@Composable
internal fun AnimatedVisibilityScope.BubblePage(ctx: WinSceneCtx, p: WinPage, live: Boolean, blue: Color, modifier: Modifier) {
    when (p) {
        WinPage.DETAILS -> BubbleInfo(ctx, live, blue, modifier)
        WinPage.TROPHIES -> BubbleAwards(ctx, live, blue, modifier)
        WinPage.OPTIONS -> BubbleSettings(ctx, live, blue, modifier)
        WinPage.MAIN -> {}
        else -> ctx.pageFor(p, modifier, live) // (Performance: the shared page - not in the card yet)
    }
}

@Composable
private fun BubbleInfo(ctx: WinSceneCtx, live: Boolean, blue: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    Column(modifier.nativeScroll(blue)) {
        nativeFacts(ctx).forEachIndexed { i, f ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(BhLine))
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                WholeWordsText(f.label, color = BhMuted, fontSize = 11.sp, lineHeight = 14.sp, modifier = Modifier.width(84.dp).padding(top = 2.dp))
                Spacer(Modifier.width(8.dp))
                WholeWordsText(f.value, color = BhInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 17.sp, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(8.dp))
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            Column(Modifier.fillMaxWidth().background(BhWell, BhBox).border(if (on) 3.dp else 1.dp, if (on) blue else BhLine, BhBox)
                .padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text("About the game", color = BhMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, lineHeight = 14.sp)
                Text(ctx.summary ?: "No description for this game yet.", color = Color(0xFF34404D), fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
private fun BubbleAwards(ctx: WinSceneCtx, live: Boolean, blue: Color, modifier: Modifier) {
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    Column(modifier) {
        // One line: the cup and the count, the slim meter in the room between, the order key at most its share (682r; its words
        // slide on one line, rule 11) - so the first award stays whole at x1.3 (AchFirstRow).
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.EmojiEvents, null, tint = BhGold, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(bubbleAwardCount(t.earned, t.total), color = BhInk, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp,
                maxLines = 1, softWrap = false)
            Box(Modifier.weight(1f).padding(horizontal = 8.dp).height(6.dp).clip(BhKey).background(BhLine)) {
                val k = bubbleMeter(t.earned, t.total)
                if (k > 0f) Box(Modifier.fillMaxWidth(k).height(6.dp).clip(BhKey).background(blue))
            }
            NativeItem(onClick = t.nextOrder, Modifier.weight(1f, fill = false), focus = focus, live = live) { lit ->
                Box(Modifier.clip(BhKey).background(BhFace).border(if (lit) 3.dp else 1.dp, if (lit) blue else BhLine, BhKey)
                    .padding(horizontal = 12.dp, vertical = 4.dp)) {
                    WinFullText("Order: " + t.order.label, color = BhInk, fontSize = 12.sp, maxLines = 1, lineHeight = 15.sp)
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        NativeRows(blue, Modifier.weight(1f).fillMaxWidth(), spacing = 4.dp) {
            nativeItems(t.all, { it.id }) { _, ach ->
                val earned = ach.isUnlocked
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    val shape = if (opened) RoundedCornerShape(18.dp) else BhKey
                    NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { lit ->
                        Column(Modifier.fillMaxWidth().clip(shape)
                            .background(if (earned) BhFace else Brush.verticalGradient(listOf(BhWell, BhWell)))
                            .border(if (lit) 3.dp else 1.dp, if (lit) blue else BhLine, shape)
                            .padding(start = 6.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                NativeBadge(ach.badgeName, ach.title, earned, blue, Modifier.size(30.dp), shape = CircleShape)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    WholeWordsText(ach.title ?: "Untitled", color = if (earned) BhInk else BhMuted, fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold, lineHeight = 16.sp)
                                    Text(bubbleAwardLine(earned, ach.dateEarned), color = if (earned) legibleOn(blue, Color.White) else BhMuted,
                                        fontSize = 11.sp, lineHeight = 14.sp)
                                }
                                ach.points?.let { pts ->
                                    Box(Modifier.padding(start = 6.dp).border(2.dp, if (earned) BhGold else BhLine, CircleShape)
                                        .padding(horizontal = 7.dp, vertical = 2.dp)) {
                                        Text("$pts", color = if (earned) BhWarn else BhMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, softWrap = false)
                                    }
                                }
                            }
                            ach.description?.let { d ->
                                if (opened) Text(d, color = Color(0xFF34404D), fontSize = 12.sp, lineHeight = 16.sp,
                                    modifier = Modifier.padding(start = 38.dp, top = 3.dp))
                                else WinFullText(d, color = BhMuted, fontSize = 11.sp, maxLines = 1, lineHeight = 14.sp,
                                    modifier = Modifier.padding(start = 38.dp, top = 1.dp))
                            }
                            if (opened) ach.unlockPercent?.let { pc ->
                                Text("%.1f %% of players have it".format(pc), color = BhWarn, fontSize = 11.sp,
                                    modifier = Modifier.padding(start = 38.dp, top = 1.dp, bottom = 2.dp))
                            }
                        }
                    }
                }
            }
            if (t.all.isEmpty()) item {
                Text("No awards for this game yet.", color = BhMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun BubbleSettings(ctx: WinSceneCtx, live: Boolean, blue: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(blue), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(),
                focus = if (i == 0) focus else null, live = live) { lit ->
                androidx.compose.runtime.LaunchedEffect(lit) { if (!lit) confirm.leave(i) }
                val armed = confirm.armed == i
                Column(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().clip(BhKey).background(BhFace)
                        .border(if (lit) 3.dp else 1.dp, if (lit) (if (o.destructive) BhWarn else blue) else BhLine, BhKey)
                        .padding(horizontal = 16.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        WinFullText(o.label, color = if (o.destructive) BhWarn else BhInk, fontSize = 13.sp, maxLines = 2, lineHeight = 17.sp,
                            fontWeight = FontWeight.Bold)
                        o.sub?.let { WinFullText(it, color = BhMuted, fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp) }
                    }
                    if (armed) Text("Press A again to remove  ·  move away to cancel", color = Color.White, fontSize = 12.sp, lineHeight = 16.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).background(BhWarn, BhBox).padding(horizontal = 14.dp, vertical = 6.dp))
                }
            }
        }
        if (ctx.options.isEmpty()) Text("No settings for this game.", color = BhMuted, fontSize = 13.sp)
    }
}

/** The card's page area: the heading, then the page, swelled in by [bubbleMove], on the white panel. [compact]: a short card
 *  (under 380 dp) - a smaller heading and closer edges, so the first award stays whole (AchFirstRow). */
@Composable
internal fun BubblePageArea(ctx: WinSceneCtx, blue: Color, modifier: Modifier, compact: Boolean = false) {
    val heading = rememberHeadingPage(ctx.page)
    Column(modifier.background(Color.White.copy(alpha = 0.94f)).padding(horizontal = if (compact) 10.dp else 12.dp, vertical = if (compact) 6.dp else 10.dp)) {
        BubblePageHeading(nativeTabs(ctx.pages), if (heading == WinPage.MAIN) ctx.page else heading, blue, compact)
        NativePages(ctx, Modifier.fillMaxWidth().weight(1f), label = "bubblePage", move = ::bubbleMove) { pg, pageLive ->
            BubblePage(ctx, pg, pageLive, blue, Modifier.fillMaxSize())
        }
    }
}
