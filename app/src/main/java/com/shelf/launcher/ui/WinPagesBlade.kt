package com.shelf.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * P2 batch 7 (682ae; audit class B - Blades had its page blade but the shared rows in its well). The blade's pages in the
 * dashboard's own words - thin light type, rows that light up in the blade's white-to-glow gradient (as its keys do):
 *  - Details      = the game's details as light label / white value lines, the story under them.
 *  - Achievements = the dashboard's achievement list: the badge on a square tile, the title, the points as "10 G"; the lit row
 *                   the one you're on; the order on the first row. A on a row shows all it asks.
 *  - Options      = the blade's rows; one that removes something is red and asks for A again.
 * The blade's own slide between pages stays BladeGuide's.
 */

private val BLADE_RED = Color(0xFFE5484D)

/** A blade row: lit in the blade's gradient when it's the one you're on (the words in the blade's dark ink). */
@Composable
private fun BladeRow(on: Boolean, glow: Color, edge: Color = Color.White, content: @Composable (ink: Color) -> Unit, darkInk: Color) {
    val k by animateFloatAsState(if (on) 1f else 0f, tween(150), label = "bladeRow")
    Box(Modifier.fillMaxWidth().heightIn(min = 34.dp).clip(RoundedCornerShape(3.dp))
        .background(Brush.horizontalGradient(listOf(edge.copy(alpha = 0.94f * k), lerp(glow, Color.White, 0.5f).copy(alpha = 0.86f * k))))
        .border(1.dp, Color.White.copy(alpha = 0.18f * (1f - k)), RoundedCornerShape(3.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.CenterStart) { content(if (on) darkInk else Color.White) }
}

@Composable
internal fun BladePage(ctx: WinSceneCtx, p: WinPage, live: Boolean, glow: Color, ink: Color, modifier: Modifier) {
    when (p) {
        WinPage.DETAILS -> BladeFacts(ctx, live, glow, ink, modifier)
        WinPage.TROPHIES -> BladeAchievements(ctx, live, glow, ink, modifier)
        WinPage.OPTIONS -> BladeOptions(ctx, live, glow, ink, modifier)
        else -> ctx.pageFor(p, modifier, live)
    }
}

@Composable
private fun BladeFacts(ctx: WinSceneCtx, live: Boolean, glow: Color, ink: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    Column(modifier.nativeScroll(Color.White).padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        nativeFacts(ctx, setOf("Trophies")).forEach { f ->
            Row(verticalAlignment = Alignment.Top) {
                Text(f.label, color = lerp(glow, Color.White, 0.55f), fontSize = 13.sp, fontWeight = FontWeight.Light, modifier = Modifier.width(100.dp))
                WholeWordsText(f.value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 17.sp, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.heightIn(min = 6.dp))
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            BladeRow(on, glow, content = { c -> Text(ctx.summary ?: "No story for this game yet.", color = c, fontSize = 13.sp, fontWeight = FontWeight.Light, lineHeight = 19.sp) }, darkInk = ink)
        }
    }
}

@Composable
private fun BladeAchievements(ctx: WinSceneCtx, live: Boolean, glow: Color, ink: Color, modifier: Modifier) {
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    Column(modifier.padding(6.dp)) {
        NativeItem(onClick = t.nextOrder, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            BladeRow(on, glow, content = { c ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${t.earned} of ${t.total} unlocked", color = c, fontSize = 13.sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f))
                    Text("Order: " + t.order.label, color = c, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }, darkInk = ink)
        }
        Spacer(Modifier.heightIn(min = 4.dp))
        Column(Modifier.weight(1f).fillMaxWidth().nativeScroll(Color.White), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            t.list.forEach { ach ->
                val earned = ach.isUnlocked
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { on ->
                        BladeRow(on, glow, content = { c ->
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    NativeBadge(ach.badgeName, ach.title, earned, glow, Modifier.size(30.dp), shape = RoundedCornerShape(2.dp))
                                    Spacer(Modifier.width(10.dp))
                                    WholeWordsText(ach.title ?: "Untitled", color = if (earned || on) c else c.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.Normal,
                                        lineHeight = 17.sp, modifier = Modifier.weight(1f))
                                    Text("${ach.points ?: 0} G", color = c, fontSize = 13.sp, fontWeight = FontWeight.Medium, softWrap = false, modifier = Modifier.padding(start = 8.dp))
                                }
                                ach.description?.let { d ->
                                    if (opened) Text(d, color = c, fontSize = 12.sp, fontWeight = FontWeight.Light, lineHeight = 16.sp, modifier = Modifier.padding(start = 40.dp, top = 2.dp))
                                    else WinFullText(d, color = c.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1, lineHeight = 14.sp, modifier = Modifier.padding(start = 40.dp, top = 1.dp))
                                }
                            }
                        }, darkInk = ink)
                    }
                }
            }
            if (t.list.isEmpty()) Text("No achievements for this game.", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun BladeOptions(ctx: WinSceneCtx, live: Boolean, glow: Color, ink: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(Color.White).padding(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(), focus = if (i == 0) focus else null, live = live) { on ->
                androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                val armed = confirm.armed == i
                BladeRow(on, glow, edge = if (o.destructive) Color(0xFFFFD9D9) else Color.White, content = { c ->
                    Column {
                        WinFullText(o.label, color = if (o.destructive) BLADE_RED else c, fontSize = 14.sp, maxLines = 2, fontWeight = FontWeight.Normal, lineHeight = 18.sp)
                        o.sub?.let { WinFullText(it, color = c.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp) }
                        if (armed) Text("Press A again to confirm  ·  move away to cancel", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(3.dp)).background(BLADE_RED).padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }, darkInk = ink)
            }
        }
        if (ctx.options.isEmpty()) Text("No options for this game.", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(start = 6.dp))
    }
}
