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
 * P2 batch 6 (682ad; audit NATIVE_PAGES_AUDIT.md class B - Tiles and Grid had their own lifted card but the shared rows inside).
 * Tile home and Grid: the card's other side is drawn in the home's own tile language - square-cornered tiles in the accent's
 * tints, big light numbers, the label small under them:
 *  - Details      = the facts as tiles (the value big, the field small under it), the story on a wide tile.
 *  - Achievements = wide tiles: the badge on a square tile of its own at the start, the title, the points as a big light
 *                   number at the end; the order on the first tile. A on a tile shows all it asks.
 *  - Options      = wide flat tiles with the accent's edge on the one you're on; one that removes something is red and asks for
 *                   A again.
 * The card's own turn (rotating about its upright edge) stays TileLiftCard's.
 */

private val TILE_RED = Color(0xFFE5484D)

/** A tile: square-ish corners, the accent's tint, a white edge when it's the one you're on. */
@Composable
private fun TileFace(on: Boolean, a: Color, ink: Color, modifier: Modifier = Modifier.fillMaxWidth(), tint: Float = 0.12f, content: @Composable () -> Unit) {
    Box(modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(4.dp)).background(a.copy(alpha = if (on) tint + 0.14f else tint))
        .border(if (on) 2.dp else 0.dp, if (on) ink else Color.Transparent, RoundedCornerShape(4.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart) { content() }
}

@Composable
internal fun TilePage(ctx: WinSceneCtx, p: WinPage, live: Boolean, ink: Color, muted: Color, a: Color, modifier: Modifier) {
    when (p) {
        WinPage.DETAILS -> TileFacts(ctx, live, ink, muted, a, modifier)
        WinPage.TROPHIES -> TileAchievements(ctx, live, ink, muted, a, modifier)
        WinPage.OPTIONS -> TileOptions(ctx, live, ink, muted, a, modifier)
        else -> ctx.pageFor(p, modifier, live)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun TileFacts(ctx: WinSceneCtx, live: Boolean, ink: Color, muted: Color, a: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    Column(modifier.nativeScroll(ink), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            nativeFacts(ctx, setOf("Trophies")).forEach { f ->
                Column(Modifier.widthIn(min = 92.dp).clip(RoundedCornerShape(4.dp)).background(a.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    WholeWordsText(f.value, color = ink, fontSize = 15.sp, fontWeight = FontWeight.Light, lineHeight = 19.sp)
                    Text(f.label.lowercase(), color = muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            TileFace(on, a, ink, tint = 0.07f) {
                Text(ctx.summary ?: "No story for this game yet.", color = ink, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun TileAchievements(ctx: WinSceneCtx, live: Boolean, ink: Color, muted: Color, a: Color, modifier: Modifier) {
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    Column(modifier) {
        NativeItem(onClick = t.nextOrder, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            TileFace(on, a, ink) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${t.earned}", color = ink, fontSize = 22.sp, fontWeight = FontWeight.Light)
                    Text("  of ${t.total} earned", color = muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("Order: " + t.order.label, color = ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.heightIn(min = 6.dp))
        NativeRows(ink, Modifier.weight(1f).fillMaxWidth(), 6.dp) {
            nativeItems(t.all, { it.id }) { _, ach ->
                val earned = ach.isUnlocked
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { on ->
                        TileFace(on, a, ink, tint = if (earned) 0.12f else 0.05f) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(3.dp)).background(a.copy(alpha = if (earned) 0.5f else 0.15f)), contentAlignment = Alignment.Center) {
                                        NativeBadge(ach.badgeName, ach.title, earned, a, Modifier.size(30.dp), shape = RoundedCornerShape(2.dp))
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    WholeWordsText(ach.title ?: "Untitled", color = if (earned) ink else muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                        lineHeight = 16.sp, modifier = Modifier.weight(1f))
                                    Text("${ach.points ?: 0}", color = if (earned) ink else muted, fontSize = 20.sp, fontWeight = FontWeight.Light, softWrap = false,
                                        modifier = Modifier.padding(start = 8.dp))
                                }
                                ach.description?.let { d ->
                                    if (opened) Text(d, color = ink, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(start = 44.dp, top = 2.dp))
                                    else WinFullText(d, color = muted, fontSize = 11.sp, maxLines = 1, lineHeight = 14.sp, modifier = Modifier.padding(start = 44.dp, top = 1.dp))
                                }
                            }
                        }
                    }
                }
            }
            if (t.all.isEmpty()) item {
            Text("No achievements for this game.", color = muted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

@Composable
private fun TileOptions(ctx: WinSceneCtx, live: Boolean, ink: Color, muted: Color, a: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(ink), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(), focus = if (i == 0) focus else null, live = live) { on ->
                androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                val armed = confirm.armed == i
                TileFace(on, if (o.destructive) TILE_RED else a, ink, tint = 0.08f) {
                    Column {
                        WinFullText(o.label, color = if (o.destructive) TILE_RED else ink, fontSize = 14.sp, maxLines = 2, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp)
                        o.sub?.let { WinFullText(it, color = muted, fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp) }
                        if (armed) Text("Press A again to confirm  ·  move away to cancel", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(3.dp)).background(TILE_RED).padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
        }
        if (ctx.options.isEmpty()) Text("No options for this game.", color = muted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
    }
}
