package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
 * P2 batch 5 (682aa; audit NATIVE_PAGES_AUDIT.md - the Twin family had its own touch screen but the shared rows on it). Twin desk,
 * Twin icons and Twin showcase: the pages are drawn for the TOUCH screen they open on - big rounded touch tiles a finger (or a
 * stylus) hits, in the desk's pale paper (Twin desk) or the dark glass (icons, showcase):
 *  - Details      = the facts as touch tiles (the field small over the value) in a flowing grid, the story in a wide tile.
 *  - Achievements = a touch list: each achievement a tall rounded tile, the badge at its start, the points in a round counter
 *                   at its end; the order as a pill at the top. A / a tap shows all it asks.
 *  - Options      = big touch buttons, one under the other; one that removes something is red and asks for A again.
 * The touch screen's sideways swap between pages stays TwinTouchPages' own.
 */

private class TwinInk(val tile: Color, val tileOn: Color, val ink: Color, val muted: Color, val edge: Color)

private fun twinInk(desk: Boolean, accent: Color) = if (desk)
    TwinInk(Color.White, accent.copy(alpha = 0.16f), DeskInk, DeskMuted, DeskEdge)
else TwinInk(Color.White.copy(alpha = 0.07f), accent.copy(alpha = 0.28f), Color.White, Color.White.copy(alpha = 0.62f), Color.White.copy(alpha = 0.12f))

private val TWIN_RED = Color(0xFFE5484D)

@Composable
internal fun TwinPage(ctx: WinSceneCtx, p: WinPage, live: Boolean, desk: Boolean, modifier: Modifier) {
    val k = twinInk(desk, ctx.accent)
    when (p) {
        WinPage.DETAILS -> TwinFacts(ctx, live, k, modifier)
        WinPage.TROPHIES -> TwinAchievements(ctx, live, k, modifier)
        WinPage.OPTIONS -> TwinButtons(ctx, live, k, modifier)
        else -> androidx.compose.runtime.CompositionLocalProvider(LocalNativePageHost provides NativePageHost(Color.Transparent, dark = !desk, showsCover = true, showsName = true)) {
            ctx.pageFor(p, modifier, live)
        }
    }
}

/** A touch tile: rounded, its edge lit in the accent when it's the one you're on. */
@Composable
private fun TwinTile(on: Boolean, k: TwinInk, accent: Color, modifier: Modifier = Modifier.fillMaxWidth(), edge: Color = accent, content: @Composable () -> Unit) {
    Box(modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).background(if (on) k.tileOn else k.tile)
        .border(if (on) 2.dp else 1.dp, if (on) edge else k.edge, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.CenterStart) { content() }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun TwinFacts(ctx: WinSceneCtx, live: Boolean, k: TwinInk, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    Column(modifier.nativeScroll(k.ink), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            nativeFacts(ctx, setOf("Trophies")).forEach { f ->
                Column(Modifier.widthIn(min = 104.dp).clip(RoundedCornerShape(12.dp)).background(k.tile).border(1.dp, k.edge, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(f.label, color = k.muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    WholeWordsText(f.value, color = k.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp)
                }
            }
        }
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            TwinTile(on, k, ctx.accent) {
                Text(ctx.summary ?: "No story for this game yet.", color = k.ink, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun TwinAchievements(ctx: WinSceneCtx, live: Boolean, k: TwinInk, modifier: Modifier) {
    val a = ctx.accent
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${t.earned} of ${t.total} earned", color = k.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            NativeItem(onClick = t.nextOrder, Modifier, focus = focus, live = live) { on ->
                Text("Order: " + t.order.label, color = if (on) k.ink else k.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.heightIn(min = 32.dp).clip(RoundedCornerShape(50)).background(if (on) k.tileOn else k.tile)
                        .border(if (on) 2.dp else 1.dp, if (on) a else k.edge, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 7.dp))
            }
        }
        NativeRows(k.ink, Modifier.weight(1f).fillMaxWidth(), 6.dp) {
            nativeItems(t.all, { it.id }) { _, ach ->
                val earned = ach.isUnlocked
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { on ->
                        TwinTile(on, k, a) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    NativeBadge(ach.badgeName, ach.title, earned, a, Modifier.size(32.dp), shape = RoundedCornerShape(9.dp))
                                    Spacer(Modifier.width(10.dp))
                                    WholeWordsText(ach.title ?: "Untitled", color = if (earned) k.ink else k.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                        lineHeight = 18.sp, modifier = Modifier.weight(1f))
                                    Box(Modifier.padding(start = 8.dp).size(30.dp).clip(RoundedCornerShape(50)).background(if (earned) a else k.edge), contentAlignment = Alignment.Center) {
                                        Text("${ach.points ?: 0}", color = if (earned) com.shelf.launcher.ui.theme.textOn(a, Color.Black) else k.ink, fontSize = 11.sp,
                                            fontWeight = FontWeight.Black, softWrap = false)
                                    }
                                }
                                ach.description?.let { d ->
                                    if (opened) Text(d, color = k.ink, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(start = 42.dp, top = 3.dp))
                                    else WinFullText(d, color = k.muted, fontSize = 11.sp, maxLines = 1, lineHeight = 14.sp, modifier = Modifier.padding(start = 42.dp, top = 2.dp))
                                }
                            }
                        }
                    }
                }
            }
            if (t.all.isEmpty()) item {
            Text("No achievements for this game.", color = k.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun TwinButtons(ctx: WinSceneCtx, live: Boolean, k: TwinInk, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(k.ink), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(), focus = if (i == 0) focus else null, live = live) { on ->
                androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                val armed = confirm.armed == i
                TwinTile(on, k, ctx.accent, edge = if (o.destructive) TWIN_RED else ctx.accent) {
                    Column {
                        WinFullText(o.label, color = if (o.destructive) TWIN_RED else k.ink, fontSize = 14.sp, maxLines = 2, fontWeight = FontWeight.Bold, lineHeight = 18.sp)
                        o.sub?.let { WinFullText(it, color = k.muted, fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp) }
                        if (armed) Text("Press A again to confirm  ·  move away to cancel", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).background(TWIN_RED).padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
        }
        if (ctx.options.isEmpty()) Text("No options for this game.", color = k.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
    }
}
