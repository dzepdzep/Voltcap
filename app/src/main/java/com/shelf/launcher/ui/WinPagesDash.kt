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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * P2 batch 3 (682w): the Dashboard window's own pages. The window is a dashboard card - the art band, the name, three stat
 * widgets on rounded tiles, the keys as flat rounded buttons - so its pages are the dashboard's other tabs, built of the same
 * widgets:
 *  - Details      = OVERVIEW: every fact as a small stat tile (label over value) in a grid, the story as a wide card under them.
 *  - Achievements = PROGRESS: a completion widget (the percentage, a bar, earned of total) over the achievements as list cards,
 *                   each with its points chip; the order as a filter chip; A opens a card's description.
 *  - Options      = SETTINGS: settings rows on cards with a chevron; one that removes something is red and asks for A again.
 * Motion: a tab switch - the new tab fades up from 97 %, the old one fades.
 */

internal fun dashPageName(p: WinPage): String = when (p) {
    WinPage.MAIN -> "Summary"
    WinPage.DETAILS -> "Overview"
    WinPage.TROPHIES -> "Progress"
    WinPage.OPTIONS -> "Settings"
    WinPage.PERF -> "Performance"
}

/** The completion widget's percentage, whole: 0 with no set. Pure. */
internal fun dashPercent(earned: Int, total: Int): Int = if (total <= 0) 0 else (earned * 100 / total).coerceIn(0, 100)

internal fun dashMove(@Suppress("UNUSED_PARAMETER") dir: Int): ContentTransform =
    (fadeIn(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) + scaleIn(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing), initialScale = 0.97f)) togetherWith
        fadeOut(tween(140))

private val DASH_TILE = Color.White.copy(alpha = 0.05f)
private val DASH_MUTED = Color.White.copy(alpha = 0.55f)
private val DASH_RED = Color(0xFFE5484D)

@Composable
internal fun AnimatedVisibilityScope.DashPage(ctx: WinSceneCtx, p: WinPage, live: Boolean, modifier: Modifier) {
    when (p) {
        WinPage.DETAILS -> DashOverview(ctx, live, modifier)
        WinPage.TROPHIES -> DashProgress(ctx, live, modifier)
        WinPage.OPTIONS -> DashSettings(ctx, live, modifier)
        WinPage.MAIN -> {}
        else -> ctx.pageFor(p, modifier, live)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashOverview(ctx: WinSceneCtx, live: Boolean, modifier: Modifier) {
    val a = ctx.accent
    val focus = nativeListFocus(ctx, live)
    Column(modifier.nativeScroll(Color.White)) {
        val facts = nativeFacts(ctx, setOf("Trophies"))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            facts.forEach { f ->
                Column(Modifier.widthIn(min = 96.dp).clip(RoundedCornerShape(8.dp)).background(DASH_TILE).padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Text(f.label, color = DASH_MUTED, fontSize = 10.sp)
                    WholeWordsText(f.value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(DASH_TILE)
                .then(if (on) Modifier.border(2.dp, a, RoundedCornerShape(8.dp)) else Modifier).padding(10.dp)) {
                Text("Story", color = DASH_MUTED, fontSize = 10.sp)
                Text(ctx.summary ?: "No story for this game yet.", color = Color.White.copy(alpha = 0.88f), fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
    }
}

@Composable
private fun DashProgress(ctx: WinSceneCtx, live: Boolean, modifier: Modifier) {
    val a = ctx.accent
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    Column(modifier) {
        // The completion widget.
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(DASH_TILE).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("${dashPercent(t.earned, t.total)}%", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, softWrap = false)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("${t.earned} of ${t.total} earned", color = DASH_MUTED, fontSize = 11.sp)
                Box(Modifier.padding(top = 3.dp).fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f))) {
                    Box(Modifier.fillMaxWidth(if (t.total > 0) t.earned.toFloat() / t.total else 0f).fillMaxHeight().clip(RoundedCornerShape(50)).background(a))
                }
            }
            Spacer(Modifier.width(10.dp))
            // The filter chip: the list's order, A steps it (682r rule: at most its share of the row).
            NativeItem(onClick = t.nextOrder, Modifier.weight(1f, fill = false), focus = focus, live = live) { on ->
                Text(t.order.label, color = if (on) com.shelf.launcher.ui.theme.textOn(a, Color.Black) else Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) a else Color.White.copy(alpha = 0.08f)).padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        NativeRows(Color.White, Modifier.weight(1f).fillMaxWidth(), 4.dp) {
            nativeItems(t.all, { it.id }) { _, ach ->
                val earned = ach.isUnlocked
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { on ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(DASH_TILE)
                            .then(if (on) Modifier.border(2.dp, a, RoundedCornerShape(8.dp)) else Modifier).padding(horizontal = 8.dp, vertical = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                NativeBadge(ach.badgeName, ach.title, earned, a, Modifier.size(28.dp), shape = RoundedCornerShape(6.dp))
                                Spacer(Modifier.width(8.dp))
                                WholeWordsText(ach.title ?: "Untitled", color = if (earned) Color.White else DASH_MUTED, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                    lineHeight = 16.sp, modifier = Modifier.weight(1f))
                                Text("${ach.points ?: 0} pts", color = if (earned) a else DASH_MUTED, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, softWrap = false,
                                    modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                            ach.description?.let { d ->
                                if (opened) Text(d, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(start = 36.dp, top = 3.dp))
                                else WinFullText(d, color = DASH_MUTED, fontSize = 11.sp, maxLines = 1, lineHeight = 14.sp, modifier = Modifier.padding(start = 36.dp, top = 2.dp))
                            }
                        }
                    }
                }
            }
            if (t.all.isEmpty()) item {
            Text("No achievements for this game.", color = DASH_MUTED, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DashSettings(ctx: WinSceneCtx, live: Boolean, modifier: Modifier) {
    val a = ctx.accent
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(Color.White), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(), focus = if (i == 0) focus else null, live = live) { on ->
                androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                val armed = confirm.armed == i
                val edge = if (o.destructive) DASH_RED else a
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(DASH_TILE)
                    .then(if (on) Modifier.border(2.dp, edge, RoundedCornerShape(8.dp)) else Modifier).padding(horizontal = 10.dp, vertical = 7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            WinFullText(o.label, color = if (o.destructive) DASH_RED else Color.White, fontSize = 13.sp, maxLines = 2, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp)
                            o.sub?.let { WinFullText(it, color = DASH_MUTED, fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp) }
                        }
                        Text("›", color = DASH_MUTED, fontSize = 18.sp)
                    }
                    // (owner 18 Nov: the confirmation readable and spaced)
                    if (armed) Text("Press A again to confirm  ·  move away to cancel", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(6.dp)).background(DASH_RED.copy(alpha = 0.85f)).padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }
        if (ctx.options.isEmpty()) Text("No settings for this game.", color = DASH_MUTED, fontSize = 12.sp)
    }
}
