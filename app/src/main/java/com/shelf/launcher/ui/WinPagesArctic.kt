package com.shelf.launcher.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * P2 batch 4 (682y, owner 22 Nov "All"): Arctic's drawer pages in the media centre's own words. The drawer keeps its poster,
 * its logo / name and the page's name in the accent over the rule (as on its main page); under the rule:
 *  - Info (Details) = the info list as on the main page - the field name in a 92 dp column, its value beside it - and the plot.
 *  - Achievements   = a media-centre list: the row you're on is a band of the accent with black words, the badge as the row's
 *                     icon, the points at its end; the order as the first row. A on a row shows all it asks.
 *  - Options        = the same list; a row that removes something takes a red band and asks for A again.
 * Motion: the list scrolls up into place as a media centre's does (going back, down).
 */

internal fun arcticMove(dir: Int): ContentTransform {
    val d = if (dir < 0) -1 else 1
    // (682ag: the old page is gone before the new one shows - no two pages' text at once)
    return (fadeIn(tween(160, delayMillis = NATIVE_OUT_MS)) + slideInVertically(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) { h -> d * h / 5 }) togetherWith
        (fadeOut(tween(NATIVE_OUT_MS)) + slideOutVertically(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) { h -> -d * h / 10 })
}

private val ARC_RED = Color(0xFFE5484D)
private val ARC_MUTED = Color.White.copy(alpha = 0.5f)

/** A media-centre row: the band when it's the one you're on, the words black on it. */
@Composable
private fun ArcticBand(on: Boolean, band: Color, content: @Composable (ink: Color) -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 34.dp).clip(RoundedCornerShape(8.dp)).background(if (on) band else Color.Transparent)
        .padding(horizontal = 12.dp, vertical = 5.dp), contentAlignment = Alignment.CenterStart) { content(if (on) Color.Black else Color.White) }
}

@Composable
internal fun ArcticPage(ctx: WinSceneCtx, p: WinPage, live: Boolean, modifier: Modifier) {
    when (p) {
        WinPage.DETAILS -> ArcticInfo(ctx, live, modifier)
        WinPage.TROPHIES -> ArcticAchievements(ctx, live, modifier)
        WinPage.OPTIONS -> ArcticOptions(ctx, live, modifier)
        else -> ctx.pageFor(p, modifier, live)
    }
}

@Composable
private fun ArcticInfo(ctx: WinSceneCtx, live: Boolean, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    Column(modifier.nativeScroll(Color.White)) {
        nativeFacts(ctx, emptySet()).forEach { f ->
            Row(Modifier.padding(vertical = 2.dp)) {
                Text(f.label, color = ARC_MUTED, fontSize = 12.sp, modifier = Modifier.width(92.dp))
                WholeWordsText(f.value, color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(8.dp))
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            Text(ctx.summary ?: "No plot for this game yet.", color = Color.White.copy(alpha = if (on) 0.95f else 0.8f), fontSize = 13.sp, lineHeight = 19.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (on) Color.White.copy(alpha = 0.06f) else Color.Transparent).padding(4.dp))
        }
    }
}

@Composable
private fun ArcticAchievements(ctx: WinSceneCtx, live: Boolean, modifier: Modifier) {
    val a = ctx.accent
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    Column(modifier) {
        NativeItem(onClick = t.nextOrder, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            ArcticBand(on, a) { ink ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${t.earned} of ${t.total} earned", color = if (on) ink else ARC_MUTED, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("Order: " + t.order.label, color = ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        NativeRows(Color.White, Modifier.weight(1f).fillMaxWidth(), 2.dp) {
            nativeItems(t.all, { it.id }) { _, ach ->
                val earned = ach.isUnlocked
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { on ->
                        ArcticBand(on, a) { ink ->
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    NativeBadge(ach.badgeName, ach.title, earned, a, Modifier.size(24.dp), shape = RoundedCornerShape(5.dp))
                                    Spacer(Modifier.width(12.dp))
                                    WholeWordsText(ach.title ?: "Untitled", color = if (earned || on) ink else ARC_MUTED, fontSize = 14.sp, lineHeight = 18.sp,
                                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.weight(1f))
                                    Text("${ach.points ?: 0}", color = if (on) ink else if (earned) a else ARC_MUTED, fontSize = 13.sp, fontWeight = FontWeight.Bold, softWrap = false,
                                        modifier = Modifier.padding(start = 8.dp))
                                }
                                ach.description?.let { d ->
                                    val c = if (on) ink.copy(alpha = 0.75f) else ARC_MUTED
                                    if (opened) Text(d, color = if (on) ink else Color.White.copy(alpha = 0.85f), fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(start = 36.dp, top = 2.dp))
                                    else WinFullText(d, color = c, fontSize = 11.sp, maxLines = 1, lineHeight = 14.sp, modifier = Modifier.padding(start = 36.dp, top = 1.dp))
                                }
                            }
                        }
                    }
                }
            }
            if (t.all.isEmpty()) item {
            Text("No achievements for this game.", color = ARC_MUTED, fontSize = 13.sp, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}

@Composable
private fun ArcticOptions(ctx: WinSceneCtx, live: Boolean, modifier: Modifier) {
    val a = ctx.accent
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(Color.White), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(), focus = if (i == 0) focus else null, live = live) { on ->
                androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                val armed = confirm.armed == i
                ArcticBand(on, if (o.destructive) ARC_RED else a) { ink ->
                    Column {
                        WinFullText(o.label, color = if (on) ink else if (o.destructive) ARC_RED else Color.White, fontSize = 14.sp, maxLines = 2, lineHeight = 18.sp,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
                        o.sub?.let { WinFullText(it, color = if (on) ink.copy(alpha = 0.75f) else ARC_MUTED, fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp) }
                        if (armed) Text("Press A again to confirm  ·  move away to cancel", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
        }
        if (ctx.options.isEmpty()) Text("No options for this game.", color = ARC_MUTED, fontSize = 13.sp, modifier = Modifier.padding(start = 12.dp))
    }
}

/** 682y: Arctic's poster without a picture - the console's initials big, its name under them in the drawer's own type. */
@Composable
internal fun ArcticNoArt(ctx: WinSceneCtx, modifier: Modifier) {
    Box(modifier.background(placeholderBrush(ctx.g.hue)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(10.dp)) {
            Text(studioInitials(ctx.console), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text(ctx.console, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}
