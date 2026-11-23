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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * P2 batch 4 (682y, owner 22 Nov "All"): the Icon bar flyout's own pages. The flyout is a popover rising from the dock - the
 * picture and name at its top, the round dock keys under it. Its pages speak the dock's language:
 *  - Details      = grouped rounded cards (the facts as label / value lines in one group, the story in another).
 *  - Achievements = the achievements as round dock icons in a grid, each with its points under it (682ag: its name, points and
 *                   description in full in the plate under the dock); the one you're on grows and
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

/** One round dock icon with its points under it; grows and takes the ring when it's the one you're on. Its name is its
 *  semantics (read out, found by QA) - shown in full in the detail plate under the grid (682ag). */
@Composable
private fun DockIcon(on: Boolean, name: String, sub: String?, dim: Boolean, icon: @Composable () -> Unit) {
    Column(Modifier.width(DOCK_ICON_CELL.dp).padding(vertical = 4.dp).semantics { contentDescription = name }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(46.dp).graphicsLayer { val s = if (on) 1.12f else 1f; scaleX = s; scaleY = s }.clip(CircleShape)
            .background(Color.White.copy(alpha = 0.1f)).border(if (on) 2.dp else 0.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) { icon() }
        sub?.let { Text(it, color = if (on) Color.White else if (dim) DOCK_MUTED else Color.White.copy(alpha = 0.85f), fontSize = 12.sp, lineHeight = 15.sp,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, textAlign = TextAlign.Center, softWrap = false, modifier = Modifier.padding(top = 5.dp)) }
    }
}

/** (682ag) The icon grid's cell width - room for "999 pts" at 12 sp. */
internal const val DOCK_ICON_CELL = 70

/** What the detail plate under the dock says for the key you're on: its name, the line under it, its description. Pure. */
internal fun dockDetail(title: String?, points: Int?, earned: Boolean, description: String?): Triple<String, String, String?> =
    Triple(dockAchName(title), "${points ?: 0} points · " + if (earned) "Earned" else "Not earned yet", description?.trim()?.takeIf { it.isNotEmpty() })

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DockAchievements(ctx: WinSceneCtx, live: Boolean, modifier: Modifier) {
    val a = ctx.accent
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    // (682ag, owner review 23 Nov - Icon bar: "labels too tiny; show the focused achievement's name, points and description in a
    // proper readable area". The icons keep their points only; the key you're on - or the one A opened - is named in full in the
    // plate under the dock, its points and whether it's earned, its description whole. The plate keeps at least two lines of room,
    // so moving along the dock doesn't make the grid jump.)
    var shownId by androidx.compose.runtime.remember(ctx.g.stableKey) { androidx.compose.runtime.mutableStateOf<Int?>(null) }
    val shown = t.list.firstOrNull { it.id == shownId } ?: t.list.firstOrNull { it.id == openId }
    // (682ag: where the flyout is short - KONKR x1.3 - the plate stands beside the dock instead of under it, so the first row
    // of icons keeps its whole height.)
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        val side = maxHeight < 250.dp && maxWidth >= 340.dp
        val plateSideW = minOf(maxWidth * 0.4f, 220.dp)
        @Composable fun grid(gridMod: Modifier) {
        Column(gridMod.nativeScroll(Color.White)) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                NativeItem(onClick = t.nextOrder, Modifier, focus = focus, live = live) { on ->
                    androidx.compose.runtime.LaunchedEffect(on) { if (on) shownId = null }
                    DockIcon(on, "Order: " + t.order.label, "${t.earned}/${t.total}", dim = false) {
                        Text("⇅", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
                t.list.forEach { ach ->
                    val earned = ach.isUnlocked
                    androidx.compose.runtime.key(ach.id) {
                        NativeItem(onClick = { openId = if (openId == ach.id) null else ach.id; shownId = ach.id }, Modifier, live = live) { on ->
                            androidx.compose.runtime.LaunchedEffect(on) { if (on) shownId = ach.id }
                            DockIcon(on, dockAchName(ach.title), "${ach.points ?: 0} pts", dim = !earned) {
                                NativeBadge(ach.badgeName, ach.title, earned, a, Modifier.size(46.dp), shape = CircleShape)
                            }
                        }
                    }
                }
            }
            if (t.list.isEmpty()) Text("No achievements for this game.", color = DOCK_MUTED, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        }
        }
        @Composable fun plate(plateMod: Modifier) {
        if (t.list.isNotEmpty()) Column(
            plateMod.clip(RoundedCornerShape(12.dp)).background(DOCK_GROUP)
                .verticalScroll(androidx.compose.foundation.rememberScrollState()) // (a long description beside a short dock scrolls, never cut)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clearAndSetSemantics { }, // (what it says is already each icon's name; read once, not twice)
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (shown == null) {
                WholeWordsText("Order: " + t.order.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp)
                Text("${t.earned} of ${t.total} earned · A changes the order", color = DOCK_MUTED, fontSize = 12.sp, lineHeight = 16.sp)
            } else {
                val (name, line, desc) = dockDetail(shown.title, shown.points, shown.isUnlocked, shown.description)
                WholeWordsText(name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp)
                Text(line, color = if (shown.isUnlocked) a else DOCK_MUTED, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
                desc?.let { WholeWordsText(it, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, lineHeight = 16.sp) }
                if (openId == shown.id) {
                    val facts = listOfNotNull((shown.dateEarnedHardcore ?: shown.dateEarned)?.let { "Earned " + it.take(10) },
                        shown.unlockPercent?.let { "%.1f%% of players".format(it) })
                    if (facts.isNotEmpty()) Text(facts.joinToString(" · "), color = DOCK_MUTED, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
        }
        }
        if (side) Row(Modifier.fillMaxWidth()) {
            grid(Modifier.weight(1f).fillMaxHeight())
            plate(Modifier.width(plateSideW).padding(start = 8.dp).heightIn(min = 52.dp))
        } else Column(Modifier.fillMaxWidth()) {
            grid(Modifier.weight(1f).fillMaxWidth())
            plate(Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 52.dp))
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
