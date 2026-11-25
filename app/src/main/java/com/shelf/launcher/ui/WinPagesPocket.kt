package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * P2 (745a, class C, the last scene): the Pocket family's own pages. One scene (POCKET_LCD) with three faces, each its own home's
 * look (WinScenesStyled.PocketPopup picks the face from the layout); each face now draws its own Details / Achievements / Options:
 *  - Pocket classic = the handheld's own system menu: white paper, the blue (your accent) title bar, pixel type. On its pages the
 *    title bar names the page - "‹ DATA ›" and "2/4" - like the handheld's menu header. DATA: the facts as menu lines, each label
 *    in the bar's colour, a rule under each, the description in a framed TEXT box. MEDALS: "3/12" in pixel type and a bar of ten
 *    pixel blocks; each achievement a menu line with the handheld's ▶ cursor on the one you are on, its badge in a square frame,
 *    "GOT <date>" / "NOT YET", "+N". MENU: the options as menu lines, the one you are on filled in the bar's colour, a removing
 *    one in red and asking for a second A. Motion: the LCD steps the next menu in - four hard steps sideways, no easing glide.
 *  - Pocket ribbon = the plain white information sheet: thin grey rules, the cartridge's art big on the left (kept on its pages).
 *    A ribbon of page names along its top in pixel type, the shown one underlined in the accent. Details: a two-column table on
 *    grey rules, the description on the sheet. Records: "3 of 12 earned", the per cent and a hairline bar; each achievement a
 *    row on a grey rule, its badge square, "+N" in grey. Settings: rows with a › at the right; the one you are on gets the accent's
 *    edge mark; a removing one in red. Motion: the sheet's ribbon slides the next page in from the side it is on.
 *  - Pocket Quest = the deep technical panel in its own palette (pqPalette), cut corners, the cartridge's art beside it (kept).
 *    Cut-corner chips name the pages (Overview / Status / Quest log / Commands). Status: stat lines - the label in small spaced
 *    capitals, the value bold, a fading accent divider; the description in a deep cut-corner well. Quest log: QUESTS CLEARED
 *    "3 / 12" and a gauge of ten cut-corner cells; each achievement a deep well, "CLEARED <date>" / "OPEN". Commands: cut-corner
 *    buttons, a removing one in red. Motion: the page rises a little and the panel's scan line runs down over it once.
 * Every face: on its pages the key row keeps the first page's slots and the window grows upward only (livePageHeight, measured
 * with More closed), so Play stays where the first page had it. Shared: data, focus, B, Play-first, inert outgoing copy
 * (NativePageKit). No motion with Motion Off.
 */

/** Which of the Pocket family's homes the window opened in. */
internal enum class PocketFace { CLASSIC, RIBBON, QUEST }

/** The face's name for a page (its own words). Pure. */
internal fun pocketPageName(face: PocketFace, p: WinPage): String = when (face) {
    PocketFace.CLASSIC -> when (p) {
        WinPage.MAIN -> "GAME"; WinPage.DETAILS -> "DATA"; WinPage.TROPHIES -> "MEDALS"; WinPage.OPTIONS -> "MENU"; WinPage.PERF -> "SPEED"
    }
    PocketFace.RIBBON -> when (p) {
        WinPage.MAIN -> "Overview"; WinPage.DETAILS -> "Details"; WinPage.TROPHIES -> "Records"; WinPage.OPTIONS -> "Settings"
        WinPage.PERF -> "Performance"
    }
    PocketFace.QUEST -> when (p) {
        WinPage.MAIN -> "Overview"; WinPage.DETAILS -> "Status"; WinPage.TROPHIES -> "Quest log"; WinPage.OPTIONS -> "Commands"
        WinPage.PERF -> "Performance"
    }
}

/** The blocks of a ten-block bar that are filled: none when none is earned, all only when every one is; one earned always
 *  shows one block. Pure. */
internal fun pocketBlocks(earned: Int, total: Int, n: Int = 10): Int {
    if (total <= 0 || earned <= 0 || n <= 0) return 0
    if (earned >= total) return n
    return Math.round(earned.toFloat() * n / total).coerceIn(1, n - 1)
}

/** "N/M" (Pocket classic) or "N / M" (Pocket Quest) - earned never above the total, nothing below 0. Pure. */
internal fun pocketCount(earned: Int, total: Int, spaced: Boolean): String {
    val t = maxOf(total, 0)
    val e = earned.coerceIn(0, t)
    return if (spaced) "$e / $t" else "$e/$t"
}

/** The Pocket ribbon's record line: "3 of 12 earned". Pure. */
internal fun ribbonRecordLine(earned: Int, total: Int): String {
    val t = maxOf(total, 0)
    return "${earned.coerceIn(0, t)} of $t earned"
}

/** An achievement's second line in the face's words. Pure. */
internal fun pocketAwardLine(face: PocketFace, earned: Boolean, date: String?): String {
    val d = date?.trim()?.takeIf { it.isNotEmpty() }
    return when (face) {
        PocketFace.CLASSIC -> if (!earned) "NOT YET" else d?.let { "GOT $it" } ?: "GOT"
        PocketFace.RIBBON -> if (!earned) "Not earned yet" else d?.let { "Earned $it" } ?: "Earned"
        PocketFace.QUEST -> if (!earned) "OPEN" else d?.let { "CLEARED $it" } ?: "CLEARED"
    }
}

/** The Pocket classic header on a page: "‹ DATA ›" and where it is in the page order ("2/4"). Pure. */
internal fun classicPageHeader(tabs: List<WinPage>, shown: WinPage): Pair<String, String> {
    val i = tabs.indexOf(shown)
    return "‹ ${pocketPageName(PocketFace.CLASSIC, shown)} ›" to (if (i < 0) "" else "${i + 1}/${tabs.size}")
}

/** The LCD's stepped motion: [steps] hard steps instead of a glide. Pure. */
internal fun lcdSteps(steps: Int = 4): Easing = Easing { x -> if (x >= 1f) 1f else (kotlin.math.floor(x * steps) / steps).coerceIn(0f, 1f) }

/** The page change of each face: the classic LCD steps the menu in sideways, the ribbon's sheet slides in from its side, Pocket
 *  Quest's page rises (its scan line runs in PocketPage); the old page goes at once. */
internal fun pocketMove(face: PocketFace, dir: Int): ContentTransform {
    val d = if (dir < 0) -1 else 1
    return when (face) {
        PocketFace.CLASSIC -> (fadeIn(tween(1, delayMillis = NATIVE_OUT_MS)) +
            slideInHorizontally(tween(NATIVE_PAGE_MS, delayMillis = NATIVE_OUT_MS, easing = lcdSteps())) { w -> d * w / 5 }) togetherWith
            fadeOut(tween(NATIVE_OUT_MS, easing = lcdSteps(2)))
        PocketFace.RIBBON -> (fadeIn(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) +
            slideInHorizontally(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) { w -> d * w / 8 }) togetherWith fadeOut(tween(NATIVE_OUT_MS))
        PocketFace.QUEST -> (fadeIn(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) +
            slideInVertically(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) { h -> h / 12 }) togetherWith fadeOut(tween(NATIVE_OUT_MS))
    }
}

private val PocketRed = Color(0xFFD03A3A)

/** The face's ink on its pages. */
internal class PocketInk(
    val face: PocketFace, val paper: Color, val ink: Color, val muted: Color, val accent: Color, val rule: Color, val well: Color,
    val font: FontFamily?, val light: Color = accent
) {
    val onAccent = if (accent.luminance() > 0.45f) Color(0xFF15171C) else Color.White
    val tint = accent.copy(alpha = 0.14f)
}

/** Short window (or large text on a mid one): the pages' headings step down so the first achievement stays whole. */
@Composable
internal fun pocketShort(ctx: WinSceneCtx): Boolean =
    ctx.maxH < 380.dp || (LocalDensity.current.fontScale > 1.1f && ctx.maxH < 480.dp)

/**
 * 745a: where a Pocket window's keys sit. The window's height on its pages: the first page's height (measured with More closed)
 * grown towards the window's room, upward only (livePageHeight, as on Live 743a / Studio 744a), so its bottom stays put. And,
 * where the cartridge's art beside the facts is taller than them (Pocket ribbon, Pocket Quest), the room the first page left
 * under its keys ([keyGap]) - kept under the keys on its pages, so the key row, and Play, stay where the first page had them.
 */
internal class PocketPlace {
    var mainH by mutableStateOf<Dp?>(null)
    var keyGap by mutableStateOf(0.dp)
    internal var panel: androidx.compose.ui.layout.LayoutCoordinates? = null
}

@Composable
internal fun rememberPocketPlace(ctx: WinSceneCtx): PocketPlace = remember(ctx.g.stableKey) { PocketPlace() }

/** The window's size on its pages (see [PocketPlace]); put it before the window's own motion. */
@Composable
internal fun pocketPageBox(ctx: WinSceneCtx, place: PocketPlace): Modifier {
    val dens = LocalDensity.current
    val mainH = place.mainH
    val pageH = mainH?.let { livePageHeight(it, ctx.maxH) }
    val moreShut = sceneKeys(ctx).none { it.label == "Less" }
    return when {
        ctx.page == WinPage.MAIN -> Modifier.heightIn(max = ctx.maxH).onSizeChanged { if (moreShut) place.mainH = with(dens) { it.height.toDp() } }
        pageH != null -> Modifier.height(pageH).offset(y = -(pageH - mainH) / 2)
        else -> Modifier.heightIn(max = ctx.maxH)
    }
}

/** The window's own box (inside its motion): what the key row measures against. */
internal fun Modifier.pocketPanel(place: PocketPlace): Modifier = onGloballyPositioned { place.panel = it }

/** The key row: on the first page (More closed) it measures the room under it. */
@Composable
internal fun Modifier.pocketKeys(ctx: WinSceneCtx, place: PocketPlace): Modifier {
    if (ctx.page != WinPage.MAIN || sceneKeys(ctx).any { it.label == "Less" }) return this
    val dens = LocalDensity.current
    return onGloballyPositioned { c ->
        val p = place.panel ?: return@onGloballyPositioned
        if (!p.isAttached || !c.isAttached) return@onGloballyPositioned
        val bottom = p.localPositionOf(c, Offset.Zero).y + c.size.height
        val gap = with(dens) { (p.size.height - bottom).coerceAtLeast(0f).toDp() }
        if (kotlin.math.abs((gap - place.keyGap).value) > 0.5f) place.keyGap = gap
    }
}

/** On a page: the room the first page left under its keys, less what the page's own layout already leaves ([natural]). */
@Composable
internal fun PocketKeyGap(ctx: WinSceneCtx, place: PocketPlace, natural: Dp) {
    if (ctx.page != WinPage.MAIN) Spacer(Modifier.height(pocketExtraGap(place.keyGap, natural)))
}

/** 745a (pure): the room to add under a page's keys - the first page's room under them less the page's own, never below 0. */
internal fun pocketExtraGap(mainGap: Dp, natural: Dp): Dp = (mainGap - natural).coerceAtLeast(0.dp)

/** The page area: the face's own page names (Pocket classic names its page in the title bar instead), then the page. */
@Composable
internal fun PocketPageArea(ctx: WinSceneCtx, k: PocketInk, modifier: Modifier, short: Boolean = false) {
    val heading = rememberHeadingPage(ctx.page)
    val shown = if (heading == WinPage.MAIN) ctx.page else heading
    Column(modifier) {
        when (k.face) {
            PocketFace.CLASSIC -> {}
            PocketFace.RIBBON -> RibbonTabs(nativeTabs(ctx.pages), shown, k, short)
            PocketFace.QUEST -> QuestTabs(nativeTabs(ctx.pages), shown, k, short)
        }
        NativePages(ctx, Modifier.fillMaxWidth().weight(1f), label = "pocketPage", move = { d -> pocketMove(k.face, d) }) { pg, pageLive ->
            PocketPage(ctx, pg, pageLive, k, Modifier.fillMaxSize(), short)
        }
    }
}

/** Pocket classic's title bar on a page: the page's name between the handheld's arrows, its place in the order on the right. */
@Composable
internal fun ClassicPageBar(ctx: WinSceneCtx, bar: Color, on: Color, font: FontFamily) {
    val heading = rememberHeadingPage(ctx.page)
    val (name, place) = classicPageHeader(nativeTabs(ctx.pages), if (heading == WinPage.MAIN) ctx.page else heading)
    Row(Modifier.fillMaxWidth().background(bar).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = on, fontSize = 13.sp, fontFamily = font, modifier = Modifier.weight(1f), maxLines = 1, softWrap = false)
        Text(place, color = on, fontSize = 13.sp, fontFamily = font, maxLines = 1, softWrap = false)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RibbonTabs(tabs: List<WinPage>, shown: WinPage, k: PocketInk, short: Boolean) {
    Column(Modifier.fillMaxWidth().padding(bottom = if (short) 4.dp else 6.dp)) {
        androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            tabs.forEach { p ->
                val on = p == shown
                val mark = k.accent
                // The shown page underlined in the accent (drawn under the word - no intrinsic measuring).
                Text(pocketPageName(PocketFace.RIBBON, p), color = if (on) k.accent else k.muted, fontSize = 12.sp, lineHeight = 15.sp,
                    fontFamily = k.font, maxLines = 1, softWrap = false, modifier = Modifier.drawBehind {
                        if (on) drawRect(mark, topLeft = Offset(0f, size.height - 2.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width, 2.dp.toPx()))
                    }.padding(top = if (short) 2.dp else 3.dp, bottom = if (short) 4.dp else 5.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(k.rule))
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun QuestTabs(tabs: List<WinPage>, shown: WinPage, k: PocketInk, short: Boolean) {
    androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().padding(bottom = if (short) 4.dp else 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        tabs.forEach { p ->
            val on = p == shown
            val shape = CutCornerShape(5.dp)
            Box(Modifier.background(if (on) k.accent else k.well, shape).border(1.dp, if (on) k.accent else k.rule, shape)
                .padding(horizontal = if (short) 8.dp else 10.dp, vertical = if (short) 3.dp else 4.dp)) {
                Text(pocketPageName(PocketFace.QUEST, p).uppercase(), color = if (on) k.onAccent else k.muted, fontSize = 11.sp, lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** One page of the face (the first page is the window itself). */
@Composable
internal fun AnimatedVisibilityScope.PocketPage(ctx: WinSceneCtx, p: WinPage, live: Boolean, k: PocketInk, modifier: Modifier, short: Boolean) {
    val anim = rememberAnimationsEnabled()
    // Pocket Quest: the panel's scan line runs down over the page once as it comes in.
    val scan by transition.animateFloat(transitionSpec = { if (anim) tween(NATIVE_PAGE_MS + 120, easing = LinearEasing) else snap() }, label = "pocketScan") {
        if (it == EnterExitState.Visible) 1f else 0f
    }
    val line = k.accent
    val m = if (k.face != PocketFace.QUEST) modifier else modifier.drawWithContent {
        drawContent()
        if (scan in 0.001f..0.999f) {
            val y = size.height * scan
            drawRect(Brush.verticalGradient(listOf(line.copy(alpha = 0f), line.copy(alpha = 0.35f)), startY = y - 18.dp.toPx(), endY = y),
                topLeft = Offset(0f, (y - 18.dp.toPx()).coerceAtLeast(0f)), size = androidx.compose.ui.geometry.Size(size.width, minOf(18.dp.toPx(), y)))
            drawLine(line, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx())
        }
    }
    when (p) {
        WinPage.DETAILS -> when (k.face) {
            PocketFace.CLASSIC -> ClassicData(ctx, live, k, m)
            PocketFace.RIBBON -> RibbonDetails(ctx, live, k, m)
            PocketFace.QUEST -> QuestStatus(ctx, live, k, m)
        }
        WinPage.TROPHIES -> PocketAwards(ctx, live, k, m, short)
        WinPage.OPTIONS -> PocketOptions(ctx, live, k, m)
        WinPage.MAIN -> {}
        else -> ctx.pageFor(p, modifier, live) // (Performance: the shared page - not a page of the Pocket family yet)
    }
}

/** The handheld menu's ▶ cursor, drawn in hard pixels (no glyph to cut at large text); its room is kept when it is off. */
@Composable
private fun ClassicCursor(on: Boolean, ink: Color) {
    androidx.compose.foundation.Canvas(Modifier.width(14.dp).height(12.dp)) {
        if (!on) return@Canvas
        val u = size.height / 6f // six pixel rows: a stepped triangle
        for (r in 0 until 6) {
            val w = (if (r < 3) r + 1 else 6 - r) * u
            drawRect(ink, topLeft = Offset(0f, r * u), size = androidx.compose.ui.geometry.Size(w, u))
        }
    }
}

// ───────────── Details ─────────────

@Composable
private fun ClassicData(ctx: WinSceneCtx, live: Boolean, k: PocketInk, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    Column(modifier.nativeScroll(k.accent)) {
        nativeFacts(ctx).forEach { f ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                WholeWordsText(f.label.uppercase(), color = k.accent, fontSize = 12.sp, lineHeight = 15.sp, fontFamily = k.font,
                    modifier = Modifier.weight(0.4f).padding(end = 8.dp))
                WholeWordsText(f.value, color = k.ink, fontSize = 13.sp, lineHeight = 17.sp, fontFamily = k.font, modifier = Modifier.weight(0.6f))
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(k.rule))
        }
        Spacer(Modifier.height(8.dp))
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            Column(Modifier.fillMaxWidth().background(if (on) k.tint else k.paper, RectangleShape).border(2.dp, if (on) k.accent else k.rule, RectangleShape)
                .padding(horizontal = 10.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ClassicCursor(on, k.accent)
                    Text("TEXT", color = k.accent, fontSize = 12.sp, fontFamily = k.font)
                }
                Spacer(Modifier.height(3.dp))
                Text(ctx.summary ?: "No description for this game yet.", color = k.ink, fontSize = 13.sp, lineHeight = 18.sp, fontFamily = k.font)
            }
        }
    }
}

@Composable
private fun RibbonDetails(ctx: WinSceneCtx, live: Boolean, k: PocketInk, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    Column(modifier.nativeScroll(k.accent)) {
        nativeFacts(ctx).forEach { f ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                WholeWordsText(f.label, color = k.muted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.weight(0.36f).padding(end = 8.dp))
                WholeWordsText(f.value, color = k.ink, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.64f))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(k.rule))
        }
        Spacer(Modifier.height(8.dp))
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            Row(Modifier.fillMaxWidth().background(if (on) k.tint else Color.Transparent)) {
                Box(Modifier.width(3.dp).height(if (on) 40.dp else 0.dp).background(k.accent))
                Column(Modifier.padding(start = if (on) 9.dp else 0.dp, top = 6.dp, bottom = 6.dp, end = 6.dp)) {
                    Text("About", color = k.muted, fontSize = 12.sp, lineHeight = 15.sp, fontFamily = k.font)
                    Spacer(Modifier.height(2.dp))
                    Text(ctx.summary ?: "No description for this game yet.", color = k.ink, fontSize = 14.sp, lineHeight = 19.sp)
                }
            }
        }
    }
}

@Composable
private fun QuestStatus(ctx: WinSceneCtx, live: Boolean, k: PocketInk, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val divider = Brush.horizontalGradient(listOf(k.accent.copy(alpha = 0.7f), k.accent.copy(alpha = 0f)))
    Column(modifier.nativeScroll(k.accent)) {
        nativeFacts(ctx).forEach { f ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                WholeWordsText(f.label.uppercase(), color = k.muted, fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold)
                WholeWordsText(f.value, color = k.ink, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(divider))
        }
        Spacer(Modifier.height(8.dp))
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            val shape = CutCornerShape(8.dp)
            Column(Modifier.fillMaxWidth().background(k.well, shape).border(if (on) 2.dp else 1.dp, if (on) k.accent else k.rule, shape)
                .padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text("LORE", color = k.light, fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Spacer(Modifier.height(3.dp))
                Text(ctx.summary ?: "No description for this game yet.", color = k.ink, fontSize = 14.sp, lineHeight = 19.sp)
            }
        }
    }
}

// ───────────── Achievements ─────────────

@Composable
private fun PocketAwards(ctx: WinSceneCtx, live: Boolean, k: PocketInk, modifier: Modifier, short: Boolean) {
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    Column(modifier) {
        // The head: the count, the face's bar, the order.
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (k.face) {
                PocketFace.CLASSIC -> Text("MEDALS " + pocketCount(t.earned, t.total, spaced = false), color = k.ink, fontSize = if (short) 13.sp else 15.sp,
                    fontFamily = k.font, maxLines = 1, softWrap = false)
                PocketFace.RIBBON -> {
                    Text(ribbonRecordLine(t.earned, t.total), color = k.ink, fontSize = if (short) 14.sp else 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    Spacer(Modifier.width(8.dp))
                    Text("${livePercent(t.earned, t.total)}%", color = k.muted, fontSize = 12.sp, maxLines = 1, softWrap = false)
                }
                PocketFace.QUEST -> Column {
                    if (!short) Text("QUESTS CLEARED", color = k.muted, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Text(pocketCount(t.earned, t.total, spaced = true), color = k.ink, fontSize = if (short) 16.sp else 20.sp, lineHeight = if (short) 19.sp else 24.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
            }
            Spacer(Modifier.width(10.dp))
            NativeItem(onClick = t.nextOrder, Modifier.weight(1f, fill = false), focus = focus, live = live) { on ->
                val shape: Shape = when (k.face) { PocketFace.QUEST -> CutCornerShape(5.dp); else -> RectangleShape }
                Box(Modifier.background(if (on) k.accent else if (k.face == PocketFace.QUEST) k.well else Color.Transparent, shape)
                    .border(if (k.face == PocketFace.CLASSIC) 2.dp else 1.dp, if (on) k.accent else k.rule, shape).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    val word = if (k.face == PocketFace.CLASSIC) "ORDER: " + t.order.label.uppercase() else "Order: " + t.order.label
                    WinFullText(word + if (k.face == PocketFace.RIBBON) "  ›" else "", color = if (on) k.onAccent else k.ink, fontSize = 12.sp, maxLines = 1,
                        lineHeight = 15.sp, fontFamily = if (k.face == PocketFace.QUEST) null else k.font)
                }
            }
        }
        val filled = pocketBlocks(t.earned, t.total)
        when (k.face) {
            // Ten pixel blocks.
            PocketFace.CLASSIC -> Row(Modifier.padding(top = 4.dp, bottom = 6.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(10) { i -> Box(Modifier.weight(1f).height(8.dp).background(if (i < filled) k.accent else k.rule)) }
            }
            // A hairline.
            PocketFace.RIBBON -> Box(Modifier.padding(top = if (short) 3.dp else 5.dp, bottom = if (short) 3.dp else 6.dp).fillMaxWidth().height(2.dp).background(k.rule)) {
                val pc = livePercent(t.earned, t.total)
                if (pc > 0) Box(Modifier.fillMaxWidth(pc / 100f).height(2.dp).background(k.accent))
            }
            // Ten cut-corner cells.
            PocketFace.QUEST -> Row(Modifier.padding(top = 5.dp, bottom = 8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(10) { i ->
                    Box(Modifier.weight(1f).height(9.dp).background(if (i < filled) k.accent else k.well, CutCornerShape(3.dp))
                        .border(1.dp, if (i < filled) k.light else k.rule, CutCornerShape(3.dp)))
                }
            }
        }
        NativeRows(k.accent, Modifier.weight(1f).fillMaxWidth(), spacing = if (k.face == PocketFace.RIBBON) 0.dp else 3.dp) {
            nativeItems(t.all, { it.id }) { _, ach ->
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { on ->
                        PocketAwardRow(ach, on, opened, k)
                    }
                }
            }
            if (t.all.isEmpty()) item {
                Text(if (k.face == PocketFace.CLASSIC) "NO MEDALS FOR THIS GAME YET." else "No achievements for this game yet.", color = k.muted,
                    fontSize = 13.sp, fontFamily = if (k.face == PocketFace.CLASSIC) k.font else null, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun PocketAwardRow(ach: com.shelf.launcher.data.RaAchievementDetail, on: Boolean, opened: Boolean, k: PocketInk) {
    val earned = ach.isUnlocked
    val text = if (earned || on) k.ink else k.muted
    val sub = if (earned) k.accent else k.muted
    val pixel = if (k.face == PocketFace.CLASSIC) k.font else null
    val shape: Shape = when (k.face) { PocketFace.QUEST -> CutCornerShape(6.dp); else -> RectangleShape }
    val bg = when (k.face) {
        PocketFace.CLASSIC -> if (on) k.tint else Color.Transparent
        PocketFace.RIBBON -> if (on) k.tint else Color.Transparent
        PocketFace.QUEST -> k.well
    }
    val edge = when (k.face) {
        PocketFace.QUEST -> if (on) k.accent else k.rule
        else -> Color.Transparent
    }
    Column(Modifier.fillMaxWidth().background(bg, shape).border(if (on && k.face == PocketFace.QUEST) 2.dp else 1.dp, edge, shape)) {
        Row(Modifier.padding(start = 4.dp, end = 8.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            when (k.face) {
                PocketFace.CLASSIC -> ClassicCursor(on, k.accent)
                PocketFace.RIBBON -> Box(Modifier.width(3.dp).height(34.dp).background(if (on) k.accent else Color.Transparent))
                PocketFace.QUEST -> {}
            }
            Spacer(Modifier.width(4.dp))
            val badgeShape: Shape = if (k.face == PocketFace.QUEST) CutCornerShape(4.dp) else RectangleShape
            Box(Modifier.size(36.dp).border(if (k.face == PocketFace.CLASSIC) 2.dp else 1.dp, if (earned) k.accent else k.rule, badgeShape),
                contentAlignment = Alignment.Center) {
                NativeBadge(ach.badgeName, ach.title, earned, k.accent, Modifier.size(30.dp), shape = badgeShape)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                WholeWordsText(ach.title ?: "Untitled", color = text, fontSize = if (pixel != null) 13.sp else 14.sp, fontWeight = if (pixel != null) null else FontWeight.Bold,
                    lineHeight = 18.sp, fontFamily = pixel)
                Text(pocketAwardLine(k.face, earned, ach.dateEarned), color = sub, fontSize = 11.sp, lineHeight = 14.sp, fontFamily = pixel,
                    fontWeight = if (k.face == PocketFace.QUEST) FontWeight.Bold else null, letterSpacing = if (k.face == PocketFace.QUEST) 1.sp else androidx.compose.ui.unit.TextUnit.Unspecified)
            }
            ach.points?.let { pts ->
                val chipShape: Shape = if (k.face == PocketFace.QUEST) CutCornerShape(3.dp) else RectangleShape
                Box(Modifier.padding(start = 6.dp).background(if (k.face == PocketFace.RIBBON) Color.Transparent else if (earned) k.accent else k.rule.copy(alpha = 0.5f), chipShape)
                    .padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text("+$pts", color = if (k.face == PocketFace.RIBBON) k.muted else if (earned) k.onAccent else k.ink, fontSize = 12.sp, fontFamily = pixel,
                        fontWeight = if (pixel != null) null else FontWeight.Bold, softWrap = false)
                }
            }
        }
        val indent = if (k.face == PocketFace.QUEST) 54.dp else 68.dp
        ach.description?.let { d ->
            if (opened) Text(d, color = text, fontSize = 12.sp, lineHeight = 16.sp, fontFamily = pixel, modifier = Modifier.padding(start = indent, end = 8.dp, bottom = 3.dp))
            else WinFullText(d, color = k.muted, fontSize = 11.sp, maxLines = 1, lineHeight = 14.sp, fontFamily = pixel, modifier = Modifier.padding(start = indent, end = 8.dp, bottom = 3.dp))
        }
        if (opened) ach.unlockPercent?.let { p ->
            Text("%.1f %% of players have it".format(p), color = sub, fontSize = 11.sp, fontFamily = pixel, modifier = Modifier.padding(start = indent, bottom = 4.dp))
        }
        if (k.face == PocketFace.RIBBON) Box(Modifier.fillMaxWidth().height(1.dp).background(k.rule))
    }
}

// ───────────── Options ─────────────

@Composable
private fun PocketOptions(ctx: WinSceneCtx, live: Boolean, k: PocketInk, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    val pixel = if (k.face == PocketFace.CLASSIC) k.font else null
    Column(modifier.nativeScroll(k.accent), verticalArrangement = Arrangement.spacedBy(if (k.face == PocketFace.RIBBON) 0.dp else 5.dp)) {
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(),
                focus = if (i == 0) focus else null, live = live) { on ->
                androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                val armed = confirm.armed == i
                val hi = if (o.destructive) PocketRed else k.accent
                val onHi = if (o.destructive) Color.White else k.onAccent
                val shape: Shape = if (k.face == PocketFace.QUEST) CutCornerShape(6.dp) else RectangleShape
                Column(Modifier.fillMaxWidth()) {
                    when (k.face) {
                        // A menu line: the one you are on filled in the bar's colour, the ▶ cursor before it.
                        PocketFace.CLASSIC -> Row(Modifier.fillMaxWidth().background(if (on) hi else Color.Transparent)
                            .border(2.dp, if (o.destructive) PocketRed else if (on) hi else k.rule, RectangleShape).padding(horizontal = 8.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            ClassicCursor(on, onHi)
                            Column(Modifier.weight(1f)) {
                                WinFullText(o.label.uppercase(), color = if (on) onHi else if (o.destructive) PocketRed else k.ink, fontSize = 13.sp, maxLines = 2,
                                    lineHeight = 17.sp, fontFamily = pixel)
                                o.sub?.let { WinFullText(it, color = if (on) onHi.copy(alpha = 0.85f) else k.muted, fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp, fontFamily = pixel) }
                            }
                        }
                        // A settings row on a grey rule: the accent's edge mark on the one you are on, a › at its end.
                        PocketFace.RIBBON -> Column(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().background(if (on) (if (o.destructive) PocketRed.copy(alpha = 0.1f) else k.tint) else Color.Transparent),
                                verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.width(3.dp).height(40.dp).background(if (on) hi else Color.Transparent))
                                Column(Modifier.weight(1f).padding(start = 9.dp, top = 6.dp, bottom = 6.dp)) {
                                    WinFullText(o.label, color = if (o.destructive) PocketRed else k.ink, fontSize = 15.sp, maxLines = 2, lineHeight = 19.sp,
                                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                                    o.sub?.let { WinFullText(it, color = k.muted, fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp) }
                                }
                                Text("›", color = if (on) hi else k.muted, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 8.dp))
                            }
                            Box(Modifier.fillMaxWidth().height(1.dp).background(k.rule))
                        }
                        // A cut-corner command button.
                        PocketFace.QUEST -> Column(Modifier.fillMaxWidth().background(if (on) hi else k.well, shape)
                            .border(1.dp, if (o.destructive) PocketRed else if (on) hi else k.rule, shape).padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            WinFullText(o.label, color = if (on) onHi else if (o.destructive) PocketRed else k.ink, fontSize = 15.sp, maxLines = 2,
                                lineHeight = 19.sp, fontWeight = FontWeight.Bold)
                            o.sub?.let { WinFullText(it, color = if (on) onHi.copy(alpha = 0.85f) else k.muted, fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp) }
                        }
                    }
                    if (armed) Text(if (k.face == PocketFace.CLASSIC) "PRESS A AGAIN TO CONFIRM · MOVE AWAY TO CANCEL" else "Press A again to confirm  ·  move away to cancel",
                        color = Color.White, fontSize = 12.sp, lineHeight = 16.sp, fontFamily = pixel, fontWeight = if (pixel != null) null else FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().padding(top = 3.dp).background(PocketRed, shape).padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
        }
        if (ctx.options.isEmpty()) Text(if (k.face == PocketFace.CLASSIC) "NO OPTIONS FOR THIS GAME." else "No options for this game.", color = k.muted,
            fontSize = 13.sp, fontFamily = pixel)
    }
}
