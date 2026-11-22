package com.shelf.launcher.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * P2 batch 4 (682y, owner 22 Nov "All"): the Icon bar flyout's own pages. The flyout is a popover rising from the dock - the
 * picture and name at its top, the round dock keys under it. Its pages speak the dock's language:
 *  - Details      = grouped rounded cards (the facts as label / value lines in one group, the story in another).
 *  - Achievements = the achievements as round dock icons in a grid, each with its name under it; the one you're on grows and
 *                   takes the white ring as a dock key does. A shows its description in the line under the grid; the order
 *                   is the first icon.
 *  - Options      = a grouped rounded list, the row you're on lit; one that removes something is red and asks for A again.
 * Motion: a popover's - the new page grows in from 96 % as the old one fades.
 */

internal fun dockMove(dir: Int): ContentTransform =
    (fadeIn(tween(160)) + scaleIn(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing), initialScale = 0.96f)) togetherWith
        (fadeOut(tween(110)) + scaleOut(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing), targetScale = 1.02f))

private val DOCK_GROUP = Color.White.copy(alpha = 0.06f)
private val DOCK_MUTED = Color.White.copy(alpha = 0.6f)
private val DOCK_RED = Color(0xFFFF453A)

/** A short name under a round icon: whole words, two lines at most (a dock key's name). Pure. */
internal fun dockAchName(title: String?): String = (title ?: "Untitled").trim()

@Composable
internal fun DockPage(ctx: WinSceneCtx, p: WinPage, live: Boolean, modifier: Modifier) {
    when (p) {
        WinPage.DETAILS -> DockInfo(ctx, live, modifier)
        WinPage.TROPHIES -> DockAchievements(ctx, live, modifier)
        WinPage.OPTIONS -> DockOptions(ctx, live, modifier)
        else -> ctx.pageFor(p, modifier, live)
    }
}

@Composable
private fun DockInfo(ctx: WinSceneCtx, live: Boolean, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    Column(modifier.nativeScroll(Color.White), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DOCK_GROUP).padding(horizontal = 12.dp, vertical = 6.dp)) {
            nativeFacts(ctx, setOf("Trophies")).forEachIndexed { i, f ->
                if (i > 0) Box(Modifier.fillMaxWidth().heightIn(min = 1.dp, max = 1.dp).background(Color.White.copy(alpha = 0.08f)))
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                    Text(f.label, color = DOCK_MUTED, fontSize = 12.sp, modifier = Modifier.width(96.dp))
                    WholeWordsText(f.value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                }
            }
        }
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            Text(ctx.summary ?: "No story for this game yet.", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, lineHeight = 18.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (on) Color.White.copy(alpha = 0.12f) else DOCK_GROUP).padding(12.dp))
        }
    }
}

/** One round dock icon with its name under it; grows and takes the ring when it's the one you're on. */
@Composable
private fun DockIcon(on: Boolean, name: String, sub: String?, dim: Boolean, icon: @Composable () -> Unit) {
    Column(Modifier.width(84.dp).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(46.dp).graphicsLayer { val s = if (on) 1.12f else 1f; scaleX = s; scaleY = s }.clip(CircleShape)
            .background(Color.White.copy(alpha = 0.1f)).border(if (on) 2.dp else 0.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) { icon() }
        Text(name, color = if (on) Color.White else if (dim) DOCK_MUTED else Color.White.copy(alpha = 0.8f), fontSize = DOCK_LABEL_SP.sp, lineHeight = 12.sp,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        sub?.let { Text(it, color = DOCK_MUTED, fontSize = 9.sp, lineHeight = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DockAchievements(ctx: WinSceneCtx, live: Boolean, modifier: Modifier) {
    val a = ctx.accent
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    val opened = t.list.firstOrNull { it.id == openId }
    Column(modifier) {
        Column(Modifier.weight(1f).fillMaxWidth().nativeScroll(Color.White)) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                NativeItem(onClick = t.nextOrder, Modifier, focus = focus, live = live) { on ->
                    DockIcon(on, "Order: " + t.order.label, "${t.earned} of ${t.total}", dim = false) {
                        Text("⇅", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
                t.list.forEach { ach ->
                    val earned = ach.isUnlocked
                    androidx.compose.runtime.key(ach.id) {
                        NativeItem(onClick = { openId = if (openId == ach.id) null else ach.id }, Modifier, live = live) { on ->
                            DockIcon(on, dockAchName(ach.title), "${ach.points ?: 0} pts", dim = !earned) {
                                NativeBadge(ach.badgeName, ach.title, earned, a, Modifier.size(46.dp), shape = CircleShape)
                            }
                        }
                    }
                }
            }
            if (t.list.isEmpty()) Text("No achievements for this game.", color = DOCK_MUTED, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        }
        // (the line under the dock, as the dock's own full-name line: what the opened achievement asks)
        opened?.let { o ->
            Text((o.title ?: "") + (o.description?.let { " - $it" } ?: ""), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, lineHeight = 16.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).background(DOCK_GROUP).padding(8.dp))
        }
    }
}

@Composable
private fun DockOptions(ctx: WinSceneCtx, live: Boolean, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(Color.White)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DOCK_GROUP).padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ctx.options.forEachIndexed { i, o ->
                NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(), focus = if (i == 0) focus else null, live = live) { on ->
                    androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                    val armed = confirm.armed == i
                    Column(Modifier.fillMaxWidth().heightIn(min = 36.dp).clip(RoundedCornerShape(9.dp)).background(if (on) Color.White.copy(alpha = 0.14f) else Color.Transparent)
                        .padding(horizontal = 10.dp, vertical = 6.dp)) {
                        WinFullText(o.label, color = if (o.destructive) DOCK_RED else Color.White, fontSize = 13.sp, maxLines = 2, fontWeight = FontWeight.Medium, lineHeight = 16.sp)
                        o.sub?.let { WinFullText(it, color = DOCK_MUTED, fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp) }
                        if (armed) Text("Press A again to confirm  ·  move away to cancel", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).background(DOCK_RED.copy(alpha = 0.85f)).padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
        }
        if (ctx.options.isEmpty()) Text("No options for this game.", color = DOCK_MUTED, fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp, top = 4.dp))
    }
}
