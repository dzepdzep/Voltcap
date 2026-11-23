package com.shelf.launcher.ui

import androidx.compose.foundation.lazy.items
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/*
 * P2 batch 1 (681c): the Classic (Voltcap row) window's own pages. The window is a charged panel - dark glass with the bolt
 * stripe down its left edge - so its pages are that panel's instruments:
 *  - Details      = CHARGE READOUT: each fact a meter line run out from the stripe (a tick on the stripe, the label in the
 *                   charge colour, the value on the glass), the story on a pane of darker glass (a D-pad stop that scrolls).
 *  - Achievements = CELLS: a battery of cells, one lit per achievement you have (a set over 40 shows each cell as its share),
 *                   the charge as a percentage at the battery's cap; the list as cells - a lit bolt for one you have, a hollow
 *                   cell for one still to get - opened with A for the whole text and when / how rare.
 *  - Options      = SWITCHES: each option a power switch, its lever lit in the charge colour while it has the focus;
 *                   one that removes something is a red guarded switch and asks for a second A (NativeConfirm).
 * Motion: a page charges in from the stripe - a left-to-right wipe driven by the page change's own transition (none with
 * Motion Off). Shared: data, focus, B, Play-first, inert outgoing copy (NativePageKit).
 */

/** The panel's page name, printed in the charge colour beside the game's name. Pure. */
internal fun voltPageName(p: WinPage): String = when (p) {
    WinPage.MAIN -> "INFO"
    WinPage.DETAILS -> "CHARGE READOUT"
    WinPage.TROPHIES -> "CELLS"
    WinPage.OPTIONS -> "SWITCHES"
    WinPage.PERF -> "PERFORMANCE"
    else -> p.name
}

/** The battery's cells: one per achievement up to [max]; above it each cell is a share, lit by the earned fraction. Pure. */
internal fun voltCells(earned: Int, total: Int, max: Int = 40): Pair<Int, Int> {
    if (total <= 0) return 0 to 0
    if (total <= max) return earned.coerceIn(0, total) to total
    val lit = Math.round(earned.toDouble() / total * max).toInt()
    // (one earned is never shown as an empty battery, one missing never as a full one)
    return (if (earned > 0) lit.coerceAtLeast(1) else 0).coerceAtMost(if (earned < total) max - 1 else max) to max
}

/** The charge percentage, whole numbers, 100 only when everything is earned. Pure. */
internal fun voltCharge(earned: Int, total: Int): Int =
    if (total <= 0) 0 else if (earned >= total) 100 else (earned * 100 / total).coerceAtMost(99)

internal fun voltMove(@Suppress("UNUSED_PARAMETER") dir: Int): ContentTransform =
    fadeIn(tween(NATIVE_PAGE_MS / 2)) togetherWith fadeOut(tween(NATIVE_PAGE_MS / 3))

/** One of the panel's pages (Info is the panel's own face, drawn by VoltPanel). */
@Composable
internal fun AnimatedVisibilityScope.VoltPage(ctx: WinSceneCtx, p: WinPage, live: Boolean, modifier: Modifier) {
    val a = ctx.accent
    val anim = rememberAnimationsEnabled()
    // The charge wipe: the page is revealed from the stripe's side as it comes in, and drains back towards it as it leaves.
    val wipe by transition.animateFloat(transitionSpec = { if (anim) tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing) else snap() }, label = "voltWipe") {
        if (it == EnterExitState.Visible) 1f else 0f
    }
    Column(modifier.drawWithContent {
        clipRect(right = size.width * wipe) { this@drawWithContent.drawContent() }
        // the charge front: a thin bright edge riding the wipe
        if (wipe in 0.01f..0.99f) drawRect(a, Offset(size.width * wipe - 2.dp.toPx(), 0f), Size(2.dp.toPx(), size.height))
    }) {
        // The panel's head on its own pages: the cover as a small chip, the name whole, the page name in the charge colour.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(5.dp)).background(Color.Black)) {
                // (682s, ShelfText: with no picture the 34 dp chip got the console plate - "SNES" one letter a line; the chip is the
                // game's own colour then, as the Cube's small mark (682q) - the name stands beside it)
                SmartArt(ctx.cover, ctx.name, Modifier.fillMaxSize(), noArt = { m -> Box(m.background(placeholderBrush(ctx.g.hue))) })
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(voltPageName(p), color = a, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                WinFullText(ctx.name, color = Color.White, fontSize = 15.sp, maxLines = 2, fontWeight = FontWeight.Bold, lineHeight = 18.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        val body = Modifier.weight(1f).fillMaxWidth()
        when (p) {
            WinPage.DETAILS -> VoltReadout(ctx, live, body)
            WinPage.TROPHIES -> VoltCells(ctx, live, body)
            WinPage.OPTIONS -> VoltSwitches(ctx, live, body)
            else -> ctx.pageFor(p, body, live) // (Performance: the shared page - not a page of this panel yet)
        }
    }
}

@Composable
private fun VoltReadout(ctx: WinSceneCtx, live: Boolean, modifier: Modifier) {
    val a = ctx.accent
    val focus = nativeListFocus(ctx, live)
    Column(modifier.nativeScroll(a)) {
        // The meter lines: a tick on the stripe's side, the label in the charge colour, the value on the glass.
        val facts = nativeFacts(ctx, setOf("Trophies"))
        val play = listOfNotNull(if (ctx.playtime == null) NativeFact("Charge time", "Not played yet") else NativeFact("Charge time", ctx.played),
            ctx.last?.let { NativeFact("Last charge", it) })
        (facts.filterNot { it.label == "Played" || it.label == "Last played" } + play).forEach { f ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(10.dp).height(2.dp).background(a))
                Spacer(Modifier.width(6.dp))
                Text(f.label.uppercase(), color = a, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.width(124.dp)) // (681h, e x1.3: "CHARGE TIME" wrapped at 96 dp)
                Text(f.value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(8.dp))
        // The story on darker glass - the page's first D-pad stop.
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            Text(ctx.summary ?: "No story for this game yet.", color = Color.White.copy(alpha = 0.86f), fontSize = 13.sp, lineHeight = 18.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.25f))))
                    .border(if (on) 2.dp else 1.dp, if (on) a else Color.White.copy(alpha = 0.10f), RoundedCornerShape(8.dp)).padding(10.dp))
        }
    }
}

@Composable
private fun VoltCells(ctx: WinSceneCtx, live: Boolean, modifier: Modifier) {
    val a = ctx.accent
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    Column(modifier) {
        // The battery: its cells, the cap, the charge.
        val (lit, cells) = voltCells(t.earned, t.total)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.weight(1f).height(22.dp)) {
                val cap = 4.dp.toPx(); val pad = 2.dp.toPx(); val bodyW = size.width - cap - 1.dp.toPx()
                drawRoundRect(Color.White.copy(alpha = 0.5f), size = Size(bodyW, size.height), cornerRadius = CornerRadius(4.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                drawRect(Color.White.copy(alpha = 0.5f), Offset(bodyW + 1.dp.toPx(), size.height * 0.3f), Size(cap, size.height * 0.4f))
                if (cells > 0) {
                    val gap = if (cells > 24) 1.dp.toPx() else 2.dp.toPx()
                    val cw = (bodyW - pad * 2 - gap * (cells - 1)) / cells
                    for (i in 0 until cells) drawRect(if (i < lit) a else Color.White.copy(alpha = 0.08f),
                        Offset(pad + i * (cw + gap), pad + 1.dp.toPx()), Size(cw, size.height - pad * 2 - 2.dp.toPx()))
                }
            }
            Spacer(Modifier.width(8.dp))
            Text("${voltCharge(t.earned, t.total)} %", color = a, fontSize = 15.sp, fontWeight = FontWeight.Black)
        }
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${t.earned} of ${t.total} cells charged", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.weight(1f))
            // The order key: a small labelled switch (A steps it).
            // (682r, RP Classic x1.3: unbounded, the order key took the row and the title beside it went to 0 px wide, one letter a
            // line, pushing the list off the page - the key gets at most its share, as the TV's)
            NativeItem(onClick = t.nextOrder, Modifier.weight(1f, fill = false), focus = focus, live = live) { on ->
                Text("ORDER  " + t.order.label.uppercase(), color = if (on) onColor(a) else a, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(if (on) a else Color.Transparent)
                        .border(1.dp, a, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 3.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        NativeRows(a, Modifier.weight(1f).fillMaxWidth(), 3.dp) {
            items(t.all, key = { it.id }) { ach ->
                val earned = ach.isUnlocked
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { on ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                            .background(if (on) a.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.03f))
                            .border(1.dp, if (on) a else Color.Transparent, RoundedCornerShape(6.dp)).padding(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // The cell: lit with a bolt when earned, hollow when still to get.
                                Canvas(Modifier.size(width = 14.dp, height = 22.dp)) {
                                    val r = CornerRadius(2.dp.toPx())
                                    drawRect(if (earned) a else Color.White.copy(alpha = 0.35f), Offset(size.width * 0.3f, 0f), Size(size.width * 0.4f, 2.dp.toPx()))
                                    if (earned) drawRoundRect(a, Offset(0f, 2.dp.toPx()), Size(size.width, size.height - 2.dp.toPx()), r)
                                    else drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(0f, 2.dp.toPx()), Size(size.width, size.height - 2.dp.toPx()), r, style = Stroke(1.5.dp.toPx()))
                                    if (earned) {
                                        val bolt = androidx.compose.ui.graphics.Path().apply {
                                            moveTo(size.width * 0.6f, size.height * 0.22f); lineTo(size.width * 0.25f, size.height * 0.62f)
                                            lineTo(size.width * 0.5f, size.height * 0.62f); lineTo(size.width * 0.4f, size.height * 0.92f)
                                            lineTo(size.width * 0.78f, size.height * 0.48f); lineTo(size.width * 0.52f, size.height * 0.48f); close()
                                        }
                                        drawPath(bolt, onColor(a))
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Box(Modifier.size(28.dp).clip(RoundedCornerShape(5.dp)).background(Color.Black)) {
                                    NativeBadgeMark(ach.title, earned)
                                    AsyncImage(com.shelf.launcher.data.RaBadges.url(ach.badgeName), ach.title, contentScale = ContentScale.Crop,
                                        colorFilter = if (earned) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
                                        alpha = if (earned) 1f else 0.5f, modifier = Modifier.fillMaxSize())
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(ach.title ?: "Untitled", color = Color.White.copy(alpha = if (earned) 1f else 0.7f), fontSize = 13.sp,
                                    fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, lineHeight = 16.sp, modifier = Modifier.weight(1f))
                                Text(ach.points?.let { "$it" } ?: "-", color = if (earned) a else Color.White.copy(alpha = 0.5f), fontSize = 12.sp,
                                    fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 6.dp))
                            }
                            ach.description?.let { d ->
                                if (opened) Text(d, color = Color.White.copy(alpha = 0.82f), fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(start = 58.dp, top = 2.dp))
                                else WinFullText(d, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp,
                                    modifier = Modifier.padding(start = 58.dp, top = 2.dp))
                            }
                            if (opened) {
                                val facts = listOfNotNull(
                                    (ach.dateEarnedHardcore ?: ach.dateEarned)?.let { "charged " + it.take(10) },
                                    ach.unlockPercent?.let { "%.1f %% of players".format(it) },
                                    ach.type?.takeIf { it.isNotBlank() }
                                )
                                if (facts.isNotEmpty()) Text(facts.joinToString("  ·  "), color = a, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 58.dp, top = 2.dp))
                            }
                        }
                    }
                }
            }
            if (t.all.isEmpty()) item {
            Text("No cells on this game - it has no achievements.", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }
        }
    }
}

private fun onColor(c: Color) = if (c.luminance() > 0.5f) Color(0xFF111111) else Color.White

/** A switch's danger colour (a guarded switch). */
private val VOLT_GUARD = Color(0xFFE5484D)

@Composable
private fun VoltSwitches(ctx: WinSceneCtx, live: Boolean, modifier: Modifier) {
    val a = ctx.accent
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(a), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(), focus = if (i == 0) focus else null, live = live) { on ->
                androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                val armed = confirm.armed == i
                val c = if (o.destructive) VOLT_GUARD else a
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = if (on) 0.07f else 0.03f))
                    .border(1.dp, if (on) c else Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    // The switch: a rocker in its housing - lever up and lit while focused (armed: lit and held).
                    Canvas(Modifier.size(width = 16.dp, height = 28.dp)) {
                        val r = CornerRadius(3.dp.toPx())
                        drawRoundRect(Color.Black.copy(alpha = 0.6f), cornerRadius = r)
                        drawRoundRect(if (on || armed) c else Color.White.copy(alpha = 0.25f), cornerRadius = r, style = Stroke(1.dp.toPx()))
                        val up = on || armed
                        drawRoundRect(if (up) c else Color.White.copy(alpha = 0.3f), Offset(2.dp.toPx(), if (up) 2.dp.toPx() else size.height / 2),
                            Size(size.width - 4.dp.toPx(), size.height / 2 - 2.dp.toPx()), r)
                        if (o.destructive) { // the guard: two hatch lines over the housing
                            drawLine(c, Offset(0f, size.height * 0.15f), Offset(size.width, size.height * 0.35f), 1.dp.toPx())
                            drawLine(c, Offset(0f, size.height * 0.65f), Offset(size.width, size.height * 0.85f), 1.dp.toPx())
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        WinFullText(o.label, color = if (o.destructive) VOLT_GUARD else Color.White, fontSize = 14.sp, maxLines = 2, fontWeight = FontWeight.Bold, lineHeight = 18.sp)
                        o.sub?.let { WinFullText(it, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp) }
                        // (682v, owner 18 Nov: the confirmation readable and spaced - 12 sp on 16, a 6 dp gap above, room inside its box)
                        if (armed) Text("Guarded switch - press A again to confirm, move away to cancel", color = VOLT_GUARD, fontSize = 12.sp,
                            fontWeight = FontWeight.Black, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp)
                                .border(1.dp, VOLT_GUARD, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                    // (682s, KONKR / RP Classic x1.3 review: the ON / OFF word beside each switch said the row's focus, not the
                    // option's state - "Add to favorites OFF", "Game settings ON" read as settings - and sat on the armed row's
                    // confirmation box. The rocker alone shows the lit switch.)
                }
            }
        }
        if (ctx.options.isEmpty()) Text("No switches for this game.", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
    }
}
