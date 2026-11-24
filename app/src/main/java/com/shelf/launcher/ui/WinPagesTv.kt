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

/**
 * 681v (emulator review 44194071430, TV room on the set's own screen): the page's lines for the room it has. On the room's
 * television (about 150 dp of picture under the controls on a 640 x 427 dp screen) the full masthead - a header that wrapped
 * "VOLTCAP TXT" over three lines (one letter a line at x1.3), the page name, the double-height name and two-line Fastext keys -
 * left the body no height: no achievement row, no story and no option on screen, and D-pad Down fell through to the buttons.
 * Under [TTX_TIGHT_DP] of page height the masthead is one line (page number, the page name on its blue block, the game's name
 * sliding beside it; the console joins the Info facts) and the Fastext keys are one line of short key words. Pure.
 */
internal const val TTX_TIGHT_DP = 200f

/** 682ag: the teletext grid's narrowest - characters a line at least (as far as [TTX_MIN_SCALE] allows). */
internal const val TTX_MIN_COLS = 24
/** 682ag: the teletext's one narrow-set size - a single step down, never a size that keeps shrinking with the set (owner 23 Nov
 *  13:32: "readability without continuously shrinking fonts"); x1.3 stays bigger than x1.0. */
internal const val TTX_MIN_SCALE = 0.85f

/** 682ag (pure): the teletext text's scale for a line [rowPx] wide where an "M" is [cellPx] - 1 while TTX_MIN_COLS fit, else the
 *  one narrow-set step ([TTX_MIN_SCALE]); two sizes only. */
internal fun ttxGridScale(rowPx: Float, cellPx: Float): Float {
    if (cellPx <= 0f || rowPx <= 0f) return 1f
    return if (rowPx / cellPx >= TTX_MIN_COLS) 1f else TTX_MIN_SCALE
}
internal fun ttxTight(pageHeightDp: Float): Boolean = pageHeightDp < TTX_TIGHT_DP

/** The Fastext key word for a page: the set's short key names, one line each. Pure. */
internal fun ttxKey(p: WinPage): String = when (p) {
    WinPage.MAIN -> "PICTURE"
    WinPage.DETAILS -> "INFO"
    WinPage.TROPHIES -> "TROPHIES"
    WinPage.OPTIONS -> "OPTIONS"
    else -> ttxPageName(p)
}

/** 681x: the set's shorter key words, used only when the full words cannot stand whole beside each other on the one line. Pure. */
internal fun ttxKeyShort(p: WinPage): String = when (p) {
    WinPage.MAIN -> "PIC"
    WinPage.OPTIONS -> "OPTS"
    else -> ttxKey(p)
}

/**
 * 681x (P2 emulator review 44233448467, TV room): the tight Fastext keys had equal widths, so a long word slid inside its key
 * ("TROPHIE" at x1.0, "PICTU / TROPH / OPTIO" at x1.3) while INFO had room to spare. Each key now takes its own word's width
 * (the keys' monospace words, measured by [width]): the full words when they all fit [avail] with the [gap]s between them, else
 * the short words (PIC / OPTS), each still in a key of its own width. Returns the words and the keys' weights. Pure.
 */
internal fun ttxKeyPlan(full: List<String>, short: List<String>, width: (String) -> Float, avail: Float, gap: Float): Pair<List<String>, List<Float>> {
    val gaps = gap * (full.size - 1).coerceAtLeast(0)
    val fw = full.map { width(it).coerceAtLeast(1f) }
    if (fw.sum() + gaps <= avail) return full to fw
    return short to short.map { width(it).coerceAtLeast(1f) }
}

@Composable
internal fun AnimatedVisibilityScope.TvTeletext(ctx: WinSceneCtx, p: WinPage, live: Boolean, modifier: Modifier, footRoom: androidx.compose.ui.unit.Dp) {
    val anim = rememberAnimationsEnabled()
    // The page number typed in, a digit at a time, as the set finds the page.
    val typed by transition.animateFloat(transitionSpec = { if (anim) tween(NATIVE_PAGE_MS, easing = androidx.compose.animation.core.LinearEasing) else snap() }, label = "ttxNo") {
        if (it == EnterExitState.Visible) 3f else 0f
    }
    val no = ttxPageNo(p).toString()
    // (682k: the Options page's confirmation - the room's, when it shows it under the set: LocalTvConfirm.)
    val room = LocalTvConfirm.current
    val confirm = room?.confirm ?: rememberNativeConfirm(ctx)
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.background(TTX_BLACK)) {
    // (682ag, owner review 23 Nov - "teletext must not be squeezed into extremely narrow columns": a teletext set is a grid of
    // characters; where the set is too narrow for TTX_MIN_COLS of them at the chosen text size (RP Classic x1.3: ~17), the grid's
    // text steps down - never under TTX_MIN_SCALE of the size asked - so lines hold whole words and titles.)
    val ttxDens = androidx.compose.ui.platform.LocalDensity.current
    val ttxCell = androidx.compose.ui.text.rememberTextMeasurer(cacheSize = 2).measure("M", androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = TTX)).size.width
    val ttxScale = ttxGridScale(with(ttxDens) { (maxWidth - 16.dp).toPx() }, ttxCell.toFloat())
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides
        androidx.compose.ui.unit.Density(ttxDens.density, ttxDens.fontScale * ttxScale)) {
    val tight = ttxTight((maxHeight - footRoom).value)
    Column(Modifier.fillMaxSize().padding(start = 8.dp, end = 8.dp, top = if (tight) 4.dp else 6.dp, bottom = footRoom)) {
        val pageNo = "P" + no.take(typed.toInt().coerceIn(1, 3)).padEnd(3, ' ')
        if (p == WinPage.OPTIONS && confirm.armed != null && room?.underSet != true) {
            // (682k: where the room has no panel under the set, the confirmation is the page's status line, in the masthead's place
            // as a teletext set shows one; with the panel it is there - TvConfirmStrip.)
            Column(Modifier.fillMaxWidth().background(TTX_WHITE).padding(horizontal = 4.dp, vertical = 2.dp)) {
                Text("PRESS A AGAIN TO CONFIRM", color = TTX_RED, fontSize = 10.sp, fontFamily = TTX, fontWeight = FontWeight.Bold, lineHeight = 13.sp)
                Text("MOVE AWAY TO CANCEL", color = TTX_BLUE, fontSize = 10.sp, fontFamily = TTX, fontWeight = FontWeight.Bold, lineHeight = 13.sp)
            }
            Spacer(Modifier.height(3.dp))
        } else if (tight) {
            // One masthead line: page number, the page name on its blue block, the game's name sliding beside it (rule 11).
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(pageNo, color = TTX_WHITE, fontSize = 11.sp, fontFamily = TTX, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                Spacer(Modifier.width(4.dp))
                Text(ttxPageName(p), color = TTX_WHITE, fontSize = 11.sp, fontFamily = TTX, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
                    modifier = Modifier.background(TTX_BLUE).padding(horizontal = 4.dp, vertical = 1.dp))
                Spacer(Modifier.width(6.dp))
                WinFullText(ctx.name.uppercase(), color = TTX_YELLOW, fontSize = 11.sp, fontFamily = TTX, fontWeight = FontWeight.Bold, maxLines = 1,
                    modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(3.dp))
        } else {
            // The header: page number (typed), the service, the console (one line - the console slides when it is long).
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(pageNo, color = TTX_WHITE, fontSize = 12.sp, fontFamily = TTX, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                Spacer(Modifier.width(8.dp))
                Text("VOLTCAP TXT", color = TTX_YELLOW, fontSize = 12.sp, fontFamily = TTX, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                Spacer(Modifier.width(8.dp))
                WinFullText(ctx.console.uppercase(), color = TTX_CYAN, fontSize = 11.sp, fontFamily = TTX, maxLines = 1,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(1f))
            }
            Text(ttxPageName(p), color = TTX_WHITE, fontSize = 11.sp, fontFamily = TTX, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 3.dp).fillMaxWidth().background(TTX_BLUE).padding(horizontal = 4.dp, vertical = 1.dp))
            Text(ctx.name.uppercase(), color = TTX_YELLOW, fontSize = 11.sp, fontFamily = TTX, fontWeight = FontWeight.Bold, lineHeight = 13.sp,
                modifier = Modifier.padding(top = 3.dp).doubleHeight())
            Spacer(Modifier.height(4.dp))
        }
        val body = Modifier.weight(1f).fillMaxWidth()
        when (p) {
            WinPage.DETAILS -> TtxInfo(ctx, live, body, withConsole = tight)
            WinPage.TROPHIES -> TtxAchievements(ctx, live, body, tight)
            WinPage.OPTIONS -> TtxOptions(ctx, live, body, confirm)
            else -> ctx.pageFor(p, body, live) // (Performance: the shared page - not a teletext page yet)
        }
        // FASTEXT: the coloured page keys (a tap opens; the controller walks them with L1 / R1). Tight: one line of key words.
        // (681x: tight keys are as wide as their words - see ttxKeyPlan.)
        val fast = ttxFastext(ctx.pages)
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 3.dp)) {
        val measurer = androidx.compose.ui.text.rememberTextMeasurer(cacheSize = 8)
        val keyStyle = androidx.compose.material3.LocalTextStyle.current.merge(androidx.compose.ui.text.TextStyle(fontSize = 10.sp, fontFamily = TTX, fontWeight = FontWeight.Bold))
        val gapPx = with(androidx.compose.ui.platform.LocalDensity.current) { 4.dp.toPx() }
        val avail = constraints.maxWidth.toFloat()
        val plan = androidx.compose.runtime.remember(fast, avail, gapPx, measurer, keyStyle) {
            ttxKeyPlan(fast.map { ttxKey(it.second) }, fast.map { ttxKeyShort(it.second) },
                { w -> measurer.measure(w, keyStyle, softWrap = false, maxLines = 1).size.width.toFloat() }, avail, gapPx)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            fast.forEachIndexed { i, (c, pg) ->
                val key = Modifier.weight(if (tight) plan.second[i] else 1f).then(if (pg == p) Modifier.border(1.dp, c) else Modifier)
                    .pointerInput(pg, live) { detectTapGestures { if (live) { if (pg == WinPage.MAIN) ctx.setPage(WinPage.MAIN) else ctx.open(pg) } } }
                    .padding(vertical = 1.dp)
                if (tight) WinFullText(plan.first[i], color = c, fontSize = 10.sp, fontFamily = TTX, fontWeight = FontWeight.Bold, maxLines = 1,
                    lineHeight = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = key)
                else Text(if (pg == WinPage.MAIN) "PICTURE" else ttxPageName(pg), color = c, fontSize = 10.sp, fontFamily = TTX, fontWeight = FontWeight.Bold,
                    lineHeight = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = key)
            }
        }
        }
    }
        }
}
}

@Composable
private fun TtxInfo(ctx: WinSceneCtx, live: Boolean, modifier: Modifier, withConsole: Boolean = false) {
    val focus = nativeListFocus(ctx, live)
    Column(modifier.nativeScroll(TTX_WHITE)) {
        // (681v: the tight masthead has no console - it is the first fact then.)
        val facts = nativeFacts(ctx, if (withConsole) setOf("Trophies") else setOf("Console", "Trophies"))
        // (682u: the label column as wide as the longest label + 1 (was 12 characters) and whole words in the values - at x1.3 on
        // a small set "PlaySt / ation" broke inside the word, worse with the room's figure kept out of the screen's corner.)
        val pad = (facts.maxOfOrNull { it.label.length } ?: 0) + 1
        // (a set too narrow for label and value side by side - the label's column would leave the value under 14 characters - puts
        // the value on the line under its label, as a teletext page's indented line)
        val measurer = androidx.compose.ui.text.rememberTextMeasurer(cacheSize = 4)
        val cell = measurer.measure("M", androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = TTX)).size.width
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stacked = constraints.maxWidth < cell * (pad + 14)
            Column {
                facts.forEach { f ->
                    if (stacked) {
                        Text(f.label.uppercase(), color = TTX_CYAN, fontSize = 11.sp, fontFamily = TTX, lineHeight = 14.sp, softWrap = false)
                        WholeWordsText(f.value, color = TTX_WHITE, fontSize = 11.sp, fontFamily = TTX, lineHeight = 14.sp, modifier = Modifier.padding(start = 8.dp))
                    } else Row(Modifier.fillMaxWidth()) {
                        Text(f.label.uppercase().padEnd(pad, ' '), color = TTX_CYAN, fontSize = 11.sp, fontFamily = TTX, lineHeight = 14.sp, softWrap = false)
                        WholeWordsText(f.value, color = TTX_WHITE, fontSize = 11.sp, fontFamily = TTX, lineHeight = 14.sp, modifier = Modifier.weight(1f))
                    }
                }
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
private fun TtxAchievements(ctx: WinSceneCtx, live: Boolean, modifier: Modifier, tight: Boolean = false) {
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    Column(modifier) {
        // (681v: one line - on the set's screen the ORDER key stood one word a line (59 dp at x1.0, 96 at x1.3) beside the
        // 16-cell bar; tight: a 10-cell bar, and the key slides on its one line where even that is too narrow.)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(ttxBar(t.earned, t.total, if (tight) 10 else 16), color = TTX_GREEN, fontSize = 11.sp, fontFamily = TTX, maxLines = 1, softWrap = false)
            Spacer(Modifier.width(6.dp))
            Text("${t.earned}/${t.total}", color = TTX_WHITE, fontSize = 11.sp, fontFamily = TTX, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            Spacer(Modifier.weight(1f).width(6.dp))
            NativeItem(onClick = t.nextOrder, Modifier.weight(1f, fill = false), focus = focus, live = live, minTouch = if (tight) 24.dp else NATIVE_ROW_MIN.dp) { on ->
                WinFullText("ORDER " + t.order.label.uppercase(), color = if (on) TTX_BLACK else TTX_MAGENTA, fontSize = 10.sp, fontFamily = TTX, fontWeight = FontWeight.Bold,
                    maxLines = 1, modifier = Modifier.background(if (on) TTX_MAGENTA else Color.Transparent).border(1.dp, TTX_MAGENTA).padding(horizontal = 3.dp))
            }
        }
        Spacer(Modifier.height(3.dp))
        NativeRows(TTX_WHITE, Modifier.weight(1f).fillMaxWidth()) {
            nativeItems(t.all, { it.id }) { _, ach ->
                val earned = ach.isUnlocked
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { on ->
                        // (681v tight: the set's screen rows at the readable 10 sp, closer together - two whole rows at x1.0, one at x1.3.)
                        val ts = if (tight) 10.sp else 11.sp
                        Column(Modifier.fillMaxWidth().background(if (on) TTX_BLUE else Color.Transparent).padding(horizontal = 2.dp, vertical = if (tight) 1.dp else 2.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Box(Modifier.padding(top = 2.dp).size(10.dp).background(if (earned) TTX_GREEN else Color.Transparent).border(1.dp, if (earned) TTX_GREEN else TTX_RED))
                                Spacer(Modifier.width(5.dp))
                                Text((ach.title ?: "UNTITLED").uppercase(), color = if (earned) TTX_YELLOW else TTX_WHITE, fontSize = ts, fontFamily = TTX,
                                    fontWeight = FontWeight.Bold, lineHeight = if (tight) 13.sp else 14.sp, modifier = Modifier.weight(1f))
                                Text(ach.points?.let { "${it}P" } ?: "", color = TTX_CYAN, fontSize = ts, fontFamily = TTX, modifier = Modifier.padding(start = 4.dp))
                            }
                            ach.description?.let { d ->
                                if (opened) Text(d, color = TTX_WHITE, fontSize = 10.sp, fontFamily = TTX, lineHeight = 13.sp, modifier = Modifier.padding(start = 15.dp))
                                // (tight: one line that slides - A still opens the whole text)
                                else WinFullText(d, color = TTX_WHITE.copy(alpha = 0.75f), fontSize = 10.sp, maxLines = if (tight) 1 else 2, lineHeight = 13.sp, fontFamily = TTX,
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
            if (t.all.isEmpty()) item {
            Text("NO ACHIEVEMENTS ON THIS SERVICE.", color = TTX_WHITE, fontSize = 11.sp, fontFamily = TTX)
            }
        }
    }
}

@Composable
private fun TtxOptions(ctx: WinSceneCtx, live: Boolean, modifier: Modifier, confirm: NativeConfirm) {
    val focus = nativeListFocus(ctx, live)
    Column(modifier.nativeScroll(TTX_WHITE)) {
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(), focus = if (i == 0) focus else null, live = live) { on ->
                androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                // (682k: the set's lines as tight as teletext's - at x1.3 the removing row (a two-line name and a three-line note) was
                // taller than the list's whole room, so its last line could not be seen.)
                Column(Modifier.fillMaxWidth().background(when { o.destructive && on -> TTX_RED; on -> TTX_BLUE; else -> Color.Transparent }).padding(horizontal = 2.dp, vertical = 1.dp)) {
                    Row {
                        Text("%3d ".format(301 + i), color = if (o.destructive) (if (on) TTX_WHITE else TTX_RED) else TTX_CYAN, fontSize = 11.sp, fontFamily = TTX, fontWeight = FontWeight.Bold, lineHeight = 13.sp)
                        Text(o.label.uppercase(), color = if (o.destructive && !on) TTX_RED else if (on) TTX_YELLOW else TTX_WHITE, fontSize = 11.sp, fontFamily = TTX,
                            fontWeight = FontWeight.Bold, lineHeight = 13.sp, modifier = Modifier.weight(1f))
                    }
                    o.sub?.let { Text(it, color = TTX_WHITE.copy(alpha = 0.8f), fontSize = 10.sp, fontFamily = TTX, lineHeight = 12.sp, modifier = Modifier.padding(start = 30.dp)) }
                    // (682k: an armed row's confirmation is the page's status line, in the masthead - TvTeletext.)
                }
            }
        }
        if (ctx.options.isEmpty()) Text("NO OPTIONS ON THIS SERVICE.", color = TTX_WHITE, fontSize = 11.sp, fontFamily = TTX)
    }
}


/** 682k: the TV room's confirmation for an armed removing row, shared by the set's Options page and the panel under the set. */
internal class TvConfirmRoom(val confirm: NativeConfirm, val underSet: Boolean)
internal val LocalTvConfirm = androidx.compose.runtime.compositionLocalOf<TvConfirmRoom?> { null }

/**
 * 682k (owner 18 Nov: "fix the cramped TV Room teletext confirmation ... completely visible at x1.0 and x1.3"): the set's
 * screen has about three teletext lines for the options' list, so the confirmation squeezed in under the armed row was cramped
 * and ran out of the list. Where the room has its panel under the set (Play, Back), the confirmation is a teletext strip at
 * the top of that panel - white box, both halves on lines of their own, the panel's whole width - while the row it asks about
 * stays lit red on the set, whole. Moving off the row disarms it and the strip goes.
 */
@Composable
internal fun TvConfirmStrip(room: TvConfirmRoom?, page: WinPage) {
    if (room == null || page != WinPage.OPTIONS || room.confirm.armed == null) return
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp).background(TTX_WHITE).padding(horizontal = 6.dp, vertical = 3.dp)) {
        Text("PRESS A AGAIN TO CONFIRM", color = TTX_RED, fontSize = 11.sp, fontFamily = TTX, fontWeight = FontWeight.Bold, lineHeight = 14.sp)
        Text("MOVE AWAY TO CANCEL", color = TTX_BLUE, fontSize = 11.sp, fontFamily = TTX, fontWeight = FontWeight.Bold, lineHeight = 14.sp)
    }
}
