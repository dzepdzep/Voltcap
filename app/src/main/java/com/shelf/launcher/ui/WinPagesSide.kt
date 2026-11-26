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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * P2 batch 4 (682y, owner 22 Nov "All"): the Side menu window's own pages. The window is a navigation drawer sliding out from
 * the menu's edge - the picture at its top, the name, then the menu's rows with the theme's accent bar on the one you're on.
 * Its pages are the drawer's submenus, each a list of the same rows under a small section header:
 *  - Details      = ABOUT: the facts as two-line rows (label over value) and the story as a row that scrolls.
 *  - Achievements = ACHIEVEMENTS: rows with the badge as the leading icon, the points as the trailing text; the order as the
 *                   first row; A opens a row's description.
 *  - Options      = OPTIONS: the options as rows; one that removes something is red and asks for A again.
 * Motion: a submenu comes in from the right edge as a drawer's does, going back slides it out to the right.
 * The drawer's own key rows stay under the submenu on every page (Play first, the same height).
 */

internal fun sidePageName(p: WinPage): String = when (p) {
    WinPage.MAIN -> "MENU"
    WinPage.DETAILS -> "ABOUT"
    WinPage.TROPHIES -> "ACHIEVEMENTS"
    WinPage.OPTIONS -> "OPTIONS"
    WinPage.PERF -> "PERFORMANCE"
}

internal fun sideMove(dir: Int): ContentTransform {
    val d = if (dir < 0) -1 else 1
    return (fadeIn(tween(160)) + slideInHorizontally(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) { w -> d * w / 3 }) togetherWith
        (fadeOut(tween(120)) + slideOutHorizontally(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) { w -> -d * w / 6 })
}

private val SIDE_MUTED = Color.White.copy(alpha = 0.6f)
private val SIDE_ROW_ON = Color.White.copy(alpha = 0.08f)
private val SIDE_RED = Color(0xFFE5484D)

/** A drawer row: the accent bar at its start when it's the one you're on, as the drawer's main menu rows. */
@Composable
private fun SideRow(on: Boolean, accent: Color, edge: Color = accent, content: @Composable () -> Unit) {
    // (The bar is drawn behind the row, as tall as the row - a filled child would take a bounded parent's whole height.)
    Box(Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(RoundedCornerShape(4.dp)).background(if (on) SIDE_ROW_ON else Color.Transparent)
        .drawBehind { if (on) drawRect(edge, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)) }
        .padding(start = 13.dp, end = 10.dp, top = 6.dp, bottom = 6.dp), contentAlignment = Alignment.CenterStart) { content() }
}

@Composable
internal fun AnimatedVisibilityScope.SidePage(ctx: WinSceneCtx, p: WinPage, live: Boolean, modifier: Modifier) {
    Column(modifier) {
        Text(sidePageName(p), color = ctx.accent, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, modifier = Modifier.padding(start = 13.dp, bottom = 4.dp))
        val m = Modifier.weight(1f).fillMaxWidth()
        when (p) {
            WinPage.DETAILS -> SideAbout(ctx, live, m)
            WinPage.TROPHIES -> SideAchievements(ctx, live, m)
            WinPage.OPTIONS -> SideOptions(ctx, live, m)
            WinPage.MAIN -> {}
            else -> ctx.pageFor(p, m, live)
        }
    }
}

@Composable
private fun SideAbout(ctx: WinSceneCtx, live: Boolean, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    Column(modifier.nativeScroll(Color.White)) {
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            SideRow(on, ctx.accent) {
                Text(ctx.summary ?: "No story for this game yet.", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
        nativeFacts(ctx, setOf("Trophies")).forEach { f ->
            Column(Modifier.fillMaxWidth().padding(start = 13.dp, top = 6.dp)) {
                Text(f.label, color = SIDE_MUTED, fontSize = 10.sp)
                WholeWordsText(f.value, color = Color.White, fontSize = 13.sp, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
private fun SideAchievements(ctx: WinSceneCtx, live: Boolean, modifier: Modifier) {
    val a = ctx.accent
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    Column(modifier) {
        NativeItem(onClick = t.nextOrder, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            SideRow(on, a) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${t.earned} of ${t.total} earned", color = SIDE_MUTED, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("Order: " + t.order.label, color = if (on) a else Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        NativeRows(Color.White, Modifier.weight(1f).fillMaxWidth(), 2.dp) {
            nativeItems(t.all, { it.id }) { _, ach ->
                val earned = ach.isUnlocked
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { on ->
                        SideRow(on, a) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    NativeBadge(ach.badgeName, ach.title, earned, a, Modifier.size(28.dp), shape = RoundedCornerShape(6.dp))
                                    Spacer(Modifier.width(10.dp))
                                    WholeWordsText(ach.title ?: "Untitled", color = if (earned) Color.White else SIDE_MUTED, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                        lineHeight = 16.sp, modifier = Modifier.weight(1f))
                                    Text("${ach.points ?: 0}", color = if (earned) a else SIDE_MUTED, fontSize = 12.sp, fontWeight = FontWeight.Bold, softWrap = false,
                                        modifier = Modifier.padding(start = 6.dp))
                                }
                                ach.description?.let { d ->
                                    if (opened) Text(d, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(start = 38.dp, top = 2.dp))
                                    else WinFullText(d, color = SIDE_MUTED, fontSize = 11.sp, maxLines = 1, lineHeight = 14.sp, modifier = Modifier.padding(start = 38.dp, top = 1.dp))
                                }
                            }
                        }
                    }
                }
            }
            if (t.all.isEmpty()) item {
            Text("No achievements for this game.", color = SIDE_MUTED, fontSize = 12.sp, modifier = Modifier.padding(start = 13.dp))
            }
        }
    }
}

@Composable
private fun SideOptions(ctx: WinSceneCtx, live: Boolean, modifier: Modifier) {
    val a = ctx.accent
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(Color.White), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(), focus = if (i == 0) focus else null, live = live) { on ->
                androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                val armed = confirm.armed == i
                SideRow(on, a, if (o.destructive) SIDE_RED else a) {
                    Column {
                        WinFullText(o.label, color = if (o.destructive) SIDE_RED else Color.White, fontSize = 13.sp, maxLines = 2, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp)
                        o.sub?.let { WinFullText(it, color = SIDE_MUTED, fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp) }
                        if (armed) Text("Press A again to confirm  ·  move away to cancel", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(4.dp)).background(SIDE_RED.copy(alpha = 0.85f)).padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
        }
        if (ctx.options.isEmpty()) Text("No options for this game.", color = SIDE_MUTED, fontSize = 12.sp, modifier = Modifier.padding(start = 13.dp))
    }
}
