package com.shelf.launcher.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * P2 batch 7 (682ae; audit class B - the Night menu had its slanted page panel but the shared rows inside it). Night menu's
 * pages in the status screen's own words - everything on the menu's slant, white bars for the one you're on:
 *  - Details      = STATUS: each fact a slanted white tag (the field, italic capitals) with its value beside it; the story in a
 *                   slanted dark box.
 *  - Achievements = the list as slanted command bars: the one you're on pulled out in white with dark words, the badge at its
 *                   start, the points as "12 PT" at its end; the order on the first bar. A on a bar shows all it asks.
 *  - Options      = the commands, slanted bars with the pointer; one that removes something is red and asks for A again.
 * The panel's own sweep along the slant stays NightStatus'.
 */

private val NIGHT_RED = Color(0xFFE5484D)
private val NIGHT_INK = Color(0xFF0B1424)

/** A command bar on the slant: white and pulled out (a few dp to the right) when it's the one you're on. */
@Composable
private fun NightBar(on: Boolean, hi: Color, edge: Color = hi, content: @Composable (ink: Color) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(start = if (on) 10.dp else 0.dp).heightIn(min = 36.dp).clip(slantShape(0.18f))
        .background(if (on) Color.White else edge.copy(alpha = 0.16f)).padding(horizontal = 14.dp, vertical = 5.dp),
        contentAlignment = Alignment.CenterStart) { content(if (on) NIGHT_INK else Color.White) }
}

@Composable
internal fun NightPage(ctx: WinSceneCtx, p: WinPage, live: Boolean, hi: Color, modifier: Modifier) {
    when (p) {
        WinPage.DETAILS -> NightFacts(ctx, live, hi, modifier)
        WinPage.TROPHIES -> NightAchievements(ctx, live, hi, modifier)
        WinPage.OPTIONS -> NightCommands(ctx, live, hi, modifier)
        else -> ctx.pageFor(p, modifier, live)
    }
}

@Composable
private fun NightFacts(ctx: WinSceneCtx, live: Boolean, hi: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    Column(modifier.nativeScroll(Color.White), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        nativeFacts(ctx, setOf("Trophies")).forEach { f ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(104.dp).clip(slantShape(0.25f)).background(Color.White).padding(horizontal = 12.dp, vertical = 3.dp)) {
                    Text(f.label.uppercase(), color = NIGHT_INK, fontSize = 11.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                }
                Spacer(Modifier.width(10.dp))
                WholeWordsText(f.value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.heightIn(min = 4.dp))
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            Box(Modifier.fillMaxWidth().clip(slantShape(0.04f)).background(NIGHT_INK.copy(alpha = if (on) 0.75f else 0.55f)).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(ctx.summary ?: "No story for this game yet.", color = Color.White, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun NightAchievements(ctx: WinSceneCtx, live: Boolean, hi: Color, modifier: Modifier) {
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    Column(modifier) {
        NativeItem(onClick = t.nextOrder, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            NightBar(on, hi) { ink ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${t.earned} / ${t.total}", color = ink, fontSize = 15.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                    Text("  EARNED", color = ink.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    Text("ORDER: " + t.order.label.uppercase(), color = ink, fontSize = 11.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                }
            }
        }
        Spacer(Modifier.heightIn(min = 6.dp))
        NativeRows(Color.White, Modifier.weight(1f).fillMaxWidth(), 4.dp) {
            nativeItems(t.all, { it.id }) { _, ach ->
                val earned = ach.isUnlocked
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { on ->
                        NightBar(on, hi) { ink ->
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    NativeBadge(ach.badgeName, ach.title, earned, hi, Modifier.size(26.dp), shape = slantShape(0.15f))
                                    Spacer(Modifier.width(10.dp))
                                    WholeWordsText(ach.title ?: "Untitled", color = if (earned || on) ink else ink.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                        lineHeight = 16.sp, modifier = Modifier.weight(1f))
                                    Text("${ach.points ?: 0} PT", color = ink, fontSize = 12.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, softWrap = false,
                                        modifier = Modifier.padding(start = 8.dp))
                                }
                                ach.description?.let { d ->
                                    if (opened) Text(d, color = ink, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(start = 36.dp, top = 2.dp))
                                    else WinFullText(d, color = ink.copy(alpha = 0.65f), fontSize = 11.sp, maxLines = 1, lineHeight = 14.sp, modifier = Modifier.padding(start = 36.dp, top = 1.dp))
                                }
                            }
                        }
                    }
                }
            }
            if (t.all.isEmpty()) item {
            Text("NO ACHIEVEMENTS FOR THIS GAME.", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(start = 10.dp))
            }
        }
    }
}

@Composable
private fun NightCommands(ctx: WinSceneCtx, live: Boolean, hi: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(Color.White), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(), focus = if (i == 0) focus else null, live = live) { on ->
                androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                val armed = confirm.armed == i
                NightBar(on, hi, if (o.destructive) NIGHT_RED else hi) { ink ->
                    Column {
                        WinFullText(o.label.uppercase(), color = if (o.destructive && !on) NIGHT_RED else if (o.destructive) Color(0xFFB3261E) else ink, fontSize = 13.sp, maxLines = 2,
                            fontWeight = FontWeight.Black, lineHeight = 16.sp)
                        o.sub?.let { WinFullText(it, color = ink.copy(alpha = 0.65f), fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp) }
                        if (armed) Text("Press A again to confirm  ·  move away to cancel", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clip(slantShape(0.1f)).background(NIGHT_RED).padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                }
            }
        }
        if (ctx.options.isEmpty()) Text("NO OPTIONS FOR THIS GAME.", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 10.dp))
    }
}
