package com.shelf.launcher.ui

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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
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
 * P2 batch 1 (681g): the TV room window's own pages. The window is the picture on the room's old television, so its pages are
 * that set's TELETEXT service - black screen, block colours, a monospaced character grid:
 *  - Details      = P100: the double-height title, the facts as coloured rows (cyan labels, white values), the story as the
 *                   page's text (the first D-pad stop).
 *  - Achievements = P200: a mosaic progress bar, the order as a magenta block, each achievement a row - a green block for one
 *                   you have, a red outline for one still to get, its points in cyan; A shows the whole text.
 *  - Options      = P300: an index page, numbered entries; one that removes something is set on red and asks twice.
 * The FASTEXT bar at the foot names the pages in red / green / yellow / blue (a tap opens one; L1 / R1 walk them), and the page
 * number in the header is typed in a digit at a time on a change (all at once with Motion Off). Shared: data, focus, B,
 * Play-first, inert outgoing copy (NativePageKit).
 */

internal val TTX_BLACK = Color(0xFF000000)
internal val TTX_RED = Color(0xFFFF2B2B)
internal val TTX_GREEN = Color(0xFF2BFF2B)
internal val TTX_YELLOW = Color(0xFFFFFF2B)
internal val TTX_BLUE = Color(0xFF2B2BFF)
internal val TTX_MAGENTA = Color(0xFFFF2BFF)
internal val TTX_CYAN = Color(0xFF2BFFFF)
internal val TTX_WHITE = Color(0xFFFFFFFF)
private val TTX = androidx.compose.ui.text.font.FontFamily.Monospace

/** The page number for a page. Pure. */
internal fun ttxPageNo(p: WinPage): Int = when (p) {
    WinPage.MAIN -> 100
    WinPage.DETAILS -> 100
    WinPage.TROPHIES -> 200
    WinPage.OPTIONS -> 300
    WinPage.PERF -> 400
    else -> 888
}

/** The page's title on the teletext service. Pure. */
internal fun ttxPageName(p: WinPage): String = when (p) {
    WinPage.DETAILS -> "GAME INFO"
    WinPage.TROPHIES -> "ACHIEVEMENTS"
    WinPage.OPTIONS -> "GAME OPTIONS"
    WinPage.PERF -> "PERFORMANCE"
    else -> "INDEX"
}

/** The Fastext keys: colour and page, in the set's order (red, green, yellow, blue) for the window's [pages]. Pure. */
internal fun ttxFastext(pages: Set<WinPage>): List<Pair<Color, WinPage>> =
    listOf(TTX_RED to WinPage.MAIN, TTX_GREEN to WinPage.DETAILS, TTX_YELLOW to WinPage.TROPHIES, TTX_BLUE to WinPage.OPTIONS).filter { it.second in pages }

/** A mosaic progress bar of [cells] block characters. Pure. */
internal fun ttxBar(earned: Int, total: Int, cells: Int = 20): String {
    if (total <= 0) return "░".repeat(cells)
    val lit = if (earned >= total) cells else (earned * cells / total).coerceIn(if (earned > 0) 1 else 0, cells - 1)
    return "█".repeat(lit) + "░".repeat(cells - lit)
}

internal fun ttxMove(@Suppress("UNUSED_PARAMETER") dir: Int): ContentTransform =
    fadeIn(tween(1, delayMillis = NATIVE_PAGE_MS / 3)) togetherWith fadeOut(tween(1, delayMillis = NATIVE_PAGE_MS / 3))

/** Double-height teletext text: drawn twice as tall, laid out at its doubled height. */
private fun Modifier.doubleHeight(): Modifier = this.layout { m, c ->
    val pl = m.measure(c.copy(minHeight = 0, maxHeight = if (c.hasBoundedHeight) c.maxHeight / 2 else c.maxHeight))
    layout(pl.width, pl.height * 2) { pl.placeWithLayer(0, 0) { scaleY = 2f; transformOrigin = TransformOrigin(0f, 0f) } }
}

@Composable
internal fun AnimatedVisibilityScope.TvTeletext(ctx: WinSceneCtx, p: WinPage, live: Boolean, modifier: Modifier, footRoom: androidx.compose.ui.unit.Dp) {
    val anim = rememberAnimationsEnabled()
    // The page number typed in, a digit at a time, as the set finds the page.
    val typed by transition.animateFloat(transitionSpec = { if (anim) tween(NATIVE_PAGE_MS, easing = androidx.compose.animation.core.LinearEasing) else snap() }, label = "ttxNo") {
        if (it == EnterExitState.Visible) 3f else 0f
    }
    val no = ttxPageNo(p).toString()
    Column(modifier.background(TTX_BLACK).padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = footRoom)) {
        // The header: page number (typed), the service, the console.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("P" + no.take(typed.toInt().coerceIn(1, 3)).padEnd(3, ' '), color = TTX_WHITE, fontSize = 12.sp, fontFamily = TTX, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("VOLTCAP TXT", color = TTX_YELLOW, fontSize = 12.sp, fontFamily = TTX, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(ctx.console.uppercase(), color = TTX_CYAN, fontSize = 11.sp, fontFamily = TTX)
        }
        Text(ttxPageName(p), color = TTX_WHITE, fontSize = 11.sp, fontFamily = TTX, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 3.dp).fillMaxWidth().background(TTX_BLUE).padding(horizontal = 4.dp, vertical = 1.dp))
        Text(ctx.name.uppercase(), color = TTX_YELLOW, fontSize = 11.sp, fontFamily = TTX, fontWeight = FontWeight.Bold, lineHeight = 13.sp,
            modifier = Modifier.padding(top = 3.dp).doubleHeight())
        Spacer(Modifier.height(4.dp))
        val body = Modifier.weight(1f).fillMaxWidth()
        when (p) {
            WinPage.DETAILS -> TtxInfo(ctx, live, body)
            WinPage.TROPHIES -> TtxAchievements(ctx, live, body)
            WinPage.OPTIONS -> TtxOptions(ctx, live, body)
            else -> ctx.pageFor(p, body, live) // (Performance: the shared page - not a teletext page yet)
        }
        // FASTEXT: the coloured page keys (a tap opens; the controller walks them with L1 / R1).
        Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ttxFastext(ctx.pages).forEach { (c, pg) ->
                Text(if (pg == WinPage.MAIN) "PICTURE" else ttxPageName(pg), color = c, fontSize = 10.sp, fontFamily = TTX, fontWeight = FontWeight.Bold,
                    lineHeight = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f).then(if (pg == p) Modifier.border(1.dp, c) else Modifier)
                        .pointerInput(pg, live) { detectTapGestures { if (live) { if (pg == WinPage.MAIN) ctx.setPage(WinPage.MAIN) else ctx.open(pg) } } }
                        .padding(vertical = 1.dp))
            }
        }
    }
}

@Composable
private fun TtxInfo(ctx: WinSceneCtx, live: Boolean, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    Column(modifier.nativeScroll(TTX_WHITE)) {
        val facts = nativeFacts(ctx, setOf("Console", "Trophies"))
        facts.forEach { f ->
            Row(Modifier.fillMaxWidth()) {
                Text(f.label.uppercase().padEnd(12, ' '), color = TTX_CYAN, fontSize = 11.sp, fontFamily = TTX, lineHeight = 14.sp)
                Text(f.value, color = TTX_WHITE, fontSize = 11.sp, fontFamily = TTX, lineHeight = 14.sp, modifier = Modifier.weight(1f))
            }
        }
        if (ctx.total > 0) Text("ACHIEVEMENTS  ${ctx.earned}/${ctx.total}", color = TTX_GREEN, fontSize = 11.sp, fontFamily = TTX, lineHeight = 14.sp)
        Spacer(Modifier.height(5.dp))
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            Text(ctx.summary ?: "NO STORY ON THIS PAGE YET.", color = TTX_WHITE, fontSize = 11.sp, fontFamily = TTX, lineHeight = 14.sp,
                modifier = Modifier.fillMaxWidth().border(1.dp, if (on) TTX_YELLOW else Color.Transparent).padding(3.dp))
        }
    }
}

@Composable
private fun TtxAchievements(ctx: WinSceneCtx, live: Boolean, modifier: Modifier) {
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(ttxBar(t.earned, t.total, 16), color = TTX_GREEN, fontSize = 11.sp, fontFamily = TTX)
            Spacer(Modifier.width(6.dp))
            Text("${t.earned}/${t.total}", color = TTX_WHITE, fontSize = 11.sp, fontFamily = TTX, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            NativeItem(onClick = t.nextOrder, Modifier, focus = focus, live = live) { on ->
                Text("ORDER " + t.order.label.uppercase(), color = if (on) TTX_BLACK else TTX_MAGENTA, fontSize = 10.sp, fontFamily = TTX, fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(if (on) TTX_MAGENTA else Color.Transparent).border(1.dp, TTX_MAGENTA).padding(horizontal = 3.dp))
            }
        }
        Spacer(Modifier.height(3.dp))
        Column(Modifier.weight(1f).fillMaxWidth().nativeScroll(TTX_WHITE)) {
            t.list.forEach { ach ->
                val earned = ach.isUnlocked
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { on ->
                        Column(Modifier.fillMaxWidth().background(if (on) TTX_BLUE else Color.Transparent).padding(horizontal = 2.dp, vertical = 2.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Box(Modifier.padding(top = 2.dp).size(10.dp).background(if (earned) TTX_GREEN else Color.Transparent).border(1.dp, if (earned) TTX_GREEN else TTX_RED))
                                Spacer(Modifier.width(5.dp))
                                Text((ach.title ?: "UNTITLED").uppercase(), color = if (earned) TTX_YELLOW else TTX_WHITE, fontSize = 11.sp, fontFamily = TTX,
                                    fontWeight = FontWeight.Bold, lineHeight = 14.sp, modifier = Modifier.weight(1f))
                                Text(ach.points?.let { "${it}P" } ?: "", color = TTX_CYAN, fontSize = 11.sp, fontFamily = TTX, modifier = Modifier.padding(start = 4.dp))
                            }
                            ach.description?.let { d ->
                                if (opened) Text(d, color = TTX_WHITE, fontSize = 10.sp, fontFamily = TTX, lineHeight = 13.sp, modifier = Modifier.padding(start = 15.dp))
                                else WinFullText(d, color = TTX_WHITE.copy(alpha = 0.75f), fontSize = 10.sp, maxLines = 2, lineHeight = 13.sp, fontFamily = TTX,
                                    modifier = Modifier.padding(start = 15.dp))
                            }
                            if (opened) {
                                val facts = listOfNotNull(
                                    (ach.dateEarnedHardcore ?: ach.dateEarned)?.let { "GOT " + it.take(10) },
                                    ach.unlockPercent?.let { "%.1f%% OF PLAYERS".format(it) },
                                    ach.type?.takeIf { it.isNotBlank() }?.uppercase()
                                )
                                if (facts.isNotEmpty()) Text(facts.joinToString("  "), color = TTX_GREEN, fontSize = 10.sp, fontFamily = TTX, lineHeight = 13.sp,
                                    modifier = Modifier.padding(start = 15.dp))
                            }
                        }
                    }
                }
            }
            if (t.list.isEmpty()) Text("NO ACHIEVEMENTS ON THIS SERVICE.", color = TTX_WHITE, fontSize = 11.sp, fontFamily = TTX)
        }
    }
}

@Composable
private fun TtxOptions(ctx: WinSceneCtx, live: Boolean, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(TTX_WHITE)) {
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(), focus = if (i == 0) focus else null, live = live) { on ->
                androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                val armed = confirm.armed == i
                Column(Modifier.fillMaxWidth().background(when { o.destructive && on -> TTX_RED; on -> TTX_BLUE; else -> Color.Transparent }).padding(horizontal = 2.dp, vertical = 2.dp)) {
                    Row {
                        Text("%3d ".format(301 + i), color = if (o.destructive) (if (on) TTX_WHITE else TTX_RED) else TTX_CYAN, fontSize = 11.sp, fontFamily = TTX, fontWeight = FontWeight.Bold)
                        Text(o.label.uppercase(), color = if (o.destructive && !on) TTX_RED else if (on) TTX_YELLOW else TTX_WHITE, fontSize = 11.sp, fontFamily = TTX,
                            fontWeight = FontWeight.Bold, lineHeight = 14.sp, modifier = Modifier.weight(1f))
                    }
                    o.sub?.let { Text(it, color = TTX_WHITE.copy(alpha = 0.8f), fontSize = 10.sp, fontFamily = TTX, lineHeight = 13.sp, modifier = Modifier.padding(start = 30.dp)) }
                    if (armed) Text("PRESS A AGAIN TO CONFIRM - MOVE AWAY TO CANCEL", color = TTX_RED, fontSize = 10.sp, fontFamily = TTX, fontWeight = FontWeight.Bold,
                        lineHeight = 13.sp, modifier = Modifier.padding(start = 30.dp, top = 2.dp).background(TTX_WHITE).padding(horizontal = 3.dp))
                }
            }
        }
        if (ctx.options.isEmpty()) Text("NO OPTIONS ON THIS SERVICE.", color = TTX_WHITE, fontSize = 11.sp, fontFamily = TTX)
    }
}
