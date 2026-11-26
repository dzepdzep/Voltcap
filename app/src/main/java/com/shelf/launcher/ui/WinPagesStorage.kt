package com.shelf.launcher.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * P2 batch 4 (682y, owner 22 Nov "All"): Storage boxes' sheets in the record's own words. The record already turned its sheets
 * (its tabs, the sheet filing in from the side, WinScenes.StorageRecord); what was drawn ON a sheet was the shared page. Now:
 *  - Details      = the entry's record cells: each fact in a small pixel-framed cell (its name in red capitals over the value),
 *                   the story in a wide cell under them.
 *  - Achievements = the entry's list as a box's: the red pointer on the row you're on, the badge in a pixel frame, the title in
 *                   whole words, the points as "12 PTS"; the order as the first row. A on a row shows all it asks.
 *  - Options      = a menu as the Info sheet's: the pointer, the label in monospace capitals; one that removes something is red
 *                   and asks for A again.
 * The record's navy, white and paper; nothing of another design.
 */

private fun storagePoints(p: Int?): String = "${p ?: 0} PTS"

@Composable
internal fun StoragePage(ctx: WinSceneCtx, p: WinPage, red: Color, live: Boolean, modifier: Modifier) {
    when (p) {
        WinPage.DETAILS -> StorageCells(ctx, red, live, modifier)
        WinPage.TROPHIES -> StorageList(ctx, red, live, modifier)
        WinPage.OPTIONS -> StorageMenu(ctx, red, live, modifier)
        else -> ctx.pageFor(p, modifier, live)
    }
}

/** The sheet's row: the pointer when it's the one you're on, the paper a shade darker. */
@Composable
private fun StorageRowFrame(on: Boolean, red: Color, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 36.dp).background(if (on) Color(0xFFE6EEF8) else Color.Transparent).padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(if (on) "▶" else " ", color = red, fontSize = 13.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(16.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun StorageCells(ctx: WinSceneCtx, red: Color, live: Boolean, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    Column(modifier.nativeScroll(RecInk), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            nativeFacts(ctx, setOf("Trophies")).forEach { f ->
                Column(Modifier.widthIn(min = 96.dp).pixelFrame(Color.White, 5.dp).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(f.label.uppercase(), color = red, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    WholeWordsText(f.value, color = RecInk, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp)
                }
            }
        }
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            Column(Modifier.fillMaxWidth().pixelFrame(if (on) Color(0xFFE6EEF8) else Color.White, 5.dp).padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text("STORY", color = red, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Text(ctx.summary ?: "No story for this entry yet.", color = RecInk, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun StorageList(ctx: WinSceneCtx, red: Color, live: Boolean, modifier: Modifier) {
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    Column(modifier) {
        NativeItem(onClick = t.nextOrder, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            StorageRowFrame(on, red) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${t.earned} / ${t.total} EARNED", color = RecMuted, fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                    Text("ORDER: " + t.order.label.uppercase(), color = if (on) red else RecInk, fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).heightIn(min = 2.dp).background(RecNavy.copy(alpha = 0.25f)))
        NativeRows(RecInk, Modifier.weight(1f).fillMaxWidth()) {
            nativeItems(t.all, { it.id }) { _, ach ->
                val earned = ach.isUnlocked
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { on ->
                        StorageRowFrame(on, red) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(32.dp).pixelFrame(Color.White, 4.dp)) {
                                        NativeBadge(ach.badgeName, ach.title, earned, red, Modifier.size(25.dp), shape = CutCornerShape(2.dp))
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    WholeWordsText(ach.title ?: "Untitled", color = if (earned) RecInk else RecMuted, fontSize = 13.sp, fontWeight = FontWeight.Black,
                                        lineHeight = 16.sp, modifier = Modifier.weight(1f))
                                    Text(storagePoints(ach.points), color = if (earned) red else RecMuted, fontSize = 11.sp, fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace, softWrap = false, modifier = Modifier.padding(start = 6.dp))
                                }
                                ach.description?.let { d ->
                                    if (opened) Text(d, color = RecInk, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(start = 40.dp, top = 2.dp))
                                    else WinFullText(d, color = RecMuted, fontSize = 11.sp, maxLines = 1, lineHeight = 14.sp, modifier = Modifier.padding(start = 40.dp, top = 1.dp))
                                }
                            }
                        }
                    }
                }
            }
            if (t.all.isEmpty()) item {
            Text("NO ACHIEVEMENTS FOR THIS ENTRY.", color = RecMuted, fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 20.dp, top = 4.dp))
            }
        }
    }
}

@Composable
private fun StorageMenu(ctx: WinSceneCtx, red: Color, live: Boolean, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(RecInk)) {
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(), focus = if (i == 0) focus else null, live = live) { on ->
                androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                val armed = confirm.armed == i
                StorageRowFrame(on, red) {
                    Column {
                        WinFullText(o.label.uppercase(), color = if (o.destructive) red else RecInk, fontSize = 13.sp, maxLines = 2, fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace, lineHeight = 16.sp)
                        o.sub?.let { WinFullText(it, color = RecMuted, fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp) }
                        if (armed) Text("PRESS A AGAIN TO CONFIRM  ·  MOVE AWAY TO CANCEL", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace, lineHeight = 16.sp,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).pixelFrame(red, 4.dp).padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
        }
        if (ctx.options.isEmpty()) Text("NO OPTIONS FOR THIS ENTRY.", color = RecMuted, fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 20.dp, top = 4.dp))
    }
}
