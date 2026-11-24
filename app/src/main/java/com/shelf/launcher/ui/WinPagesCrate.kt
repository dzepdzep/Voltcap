package com.shelf.launcher.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/*
 * P2 batch 1 (681a): Record crate's own pages. The window is the sleeve's back; its pages are the sleeve's other printed matter,
 * pulled out sideways like the paper inner sleeve from beside the record:
 *  - Details   = LINER NOTES: the credits set as a record's small-print credit block (FORMAT · RELEASED · LABEL · STYLE), the
 *                play history as a pressing note, the story as the liner text with a drop cap in the sleeve's print colour.
 *  - Achievements = TRACKLIST: SIDE A the ones you have (A1, A2 …), SIDE B the ones still to get (B1 …), each a track with its
 *                badge as the centre label, a dotted leader to its points as the running time; A opens the track's words (what it
 *                asks, when you got it, how many players have it) like a lyric sheet. The order key is a rubber stamp.
 *  - Options   = PRESSING NOTES: each option a numbered sticker line on the paper; the ones that remove something in the
 *                print red with a warning mark.
 * Shared (NativePageKit): the data, L1 / R1, focus, sounds, touch size, scroll. The look is only this sleeve's.
 */

/** The crate's page names, printed where SIDE A is on the sleeve's back. */
internal fun cratePageName(p: WinPage): String = when (p) {
    WinPage.MAIN -> "SIDE A"
    WinPage.DETAILS -> "LINER NOTES"
    WinPage.TROPHIES -> "TRACKLIST"
    WinPage.OPTIONS -> "PRESSING NOTES"
    WinPage.PERF -> "PERFORMANCE"
}

/**
 * The sleeve's header above the rule (681s, emulator review 44194071430: at x1.3 the full header - a cover a quarter of the
 * sleeve, the name in up to three 19 sp lines, the console line - left the insert room for one or two tracks). On Info the
 * sleeve shows itself whole; on its own pages it steps back to a small sleeve: a thumbnail, the page's name and the game's
 * name on one line under it that slides when long (never cut - rule 11), no console line (Info and LINER NOTES' FORMAT carry it), so
 * the printed insert gets the room. [cover] is the cover's side as a share of the sleeve, sizes in sp, [rule] the rule's
 * margin in dp. Pure.
 */
internal data class CrateHeader(val cover: Float, val name: Int, val line: Int, val lines: Int, val rule: Int, val console: Boolean)

internal fun crateHeader(onPage: Boolean): CrateHeader =
    if (onPage) CrateHeader(cover = 0.08f, name = 15, line = 17, lines = 1, rule = 5, console = false)
    else CrateHeader(cover = 0.26f, name = 19, line = 21, lines = 3, rule = 8, console = true)

/** The page's name printed in the sleeve's header; TRACKLIST carries the count (681s: it left the sheet's own first line). Pure. */
internal fun crateHeaderName(p: WinPage, earned: Int, total: Int): String =
    cratePageName(p) + (if (p == WinPage.TROPHIES && total > 0) "  ·  $earned / $total" else "")

/** The paper inner sleeve slides out sideways the way the D-pad went; the one before slips back behind the record. */
internal fun crateMove(dir: Int): ContentTransform =
    (slideInHorizontally(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) { w -> w * dir / 2 } + fadeIn(tween(140, delayMillis = 70))) togetherWith
        (slideOutHorizontally(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) { w -> -w * dir / 6 } + fadeOut(tween(90)))

/** The inner sleeve's paper - a whiter stock than the sleeve's board, so the insert reads as a separate printed sheet. */
internal val CRATE_INSERT_PAPER = Color(0xFFF8F3E8)

/** The insert's lean while it is being pulled (deg) and once it lies on the sleeve - a sheet in the hand, never squared off. */
internal const val CRATE_INSERT_PULL_TILT = -3.5f
internal const val CRATE_INSERT_REST_TILT = -0.7f

/**
 * One of the crate's pages (not Info - the sleeve draws that). [live] false: the copy on its way out.
 * (681b, owner 17 Nov: "the pages should feel like printed inserts being handled, not generic panels") - each page is printed
 * on an INSERT: a sheet of whiter stock lying on the sleeve's back with its own shadow, leaning a little as if just put down,
 * a dog-eared corner, the press's rule across its head; it leans more while it is pulled in or out.
 */
@Composable
internal fun androidx.compose.animation.AnimatedVisibilityScope.CratePage(ctx: WinSceneCtx, p: WinPage, live: Boolean, paper: Color, ink: Color, red: Color, modifier: Modifier) {
    // (Motion Off: the insert lies at rest at once - the page change itself is immediate then, so must its lean be.)
    val anim = rememberAnimationsEnabled()
    val tilt by transition.animateFloat(transitionSpec = { if (anim) tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing) else androidx.compose.animation.core.snap() }, label = "crateTilt") {
        if (it == androidx.compose.animation.EnterExitState.Visible) CRATE_INSERT_REST_TILT else CRATE_INSERT_PULL_TILT
    }
    Box(modifier.padding(horizontal = 2.dp, vertical = 3.dp).graphicsLayer {
        rotationZ = tilt
        shadowElevation = 5.dp.toPx(); shape = androidx.compose.ui.graphics.RectangleShape; clip = false
    }.background(CRATE_INSERT_PAPER).drawBehind {
        // The press's rule across the head and the paper's faint laid lines.
        drawRect(red, size = androidx.compose.ui.geometry.Size(size.width, 3.dp.toPx()))
        var y = 14.dp.toPx(); val step = 9.dp.toPx()
        while (y < size.height) { drawLine(ink.copy(alpha = 0.035f), Offset(0f, y), Offset(size.width, y), 1f); y += step }
    }.drawWithContent {
        drawContent()
        // The dog-eared corner: the corner folded over, its back a shade darker.
        val f = 14.dp.toPx()
        val fold = androidx.compose.ui.graphics.Path().apply { moveTo(size.width - f, size.height); lineTo(size.width, size.height - f); lineTo(size.width - f, size.height - f); close() }
        drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(size.width - f, size.height); lineTo(size.width, size.height); lineTo(size.width, size.height - f); close() }, paper)
        drawPath(fold, Color(0xFFE2D8C4))
        drawPath(fold, ink.copy(alpha = 0.25f), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
    }.padding(start = 8.dp, end = 8.dp, top = 9.dp, bottom = 6.dp)) {
        when (p) {
            WinPage.DETAILS -> CrateLinerNotes(ctx, live, ink, red, Modifier.fillMaxSize())
            WinPage.TROPHIES -> CrateTracklist(ctx, live, CRATE_INSERT_PAPER, ink, red, Modifier.fillMaxSize()) // (the title's ground is the insert's stock)
            WinPage.OPTIONS -> CratePressingNotes(ctx, live, ink, red, Modifier.fillMaxSize())
            else -> ctx.pageFor(p, Modifier.fillMaxSize(), live) // (Performance: the shared page - not a page of this sleeve yet)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CrateLinerNotes(ctx: WinSceneCtx, live: Boolean, ink: Color, red: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    Column(modifier.nativeScroll(ink)) {
        // The credit block: small caps label, the value in the sleeve's serif - as many to a line as fit, never cut.
        val credits = nativeFacts(ctx, setOf("Played", "Last played", "Trophies"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            credits.forEach { f ->
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(color = red, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)) {
                        append(crateCreditName(f.label)); append(" ")
                    }
                    withStyle(SpanStyle(color = ink, fontSize = 12.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold)) { append(f.value) }
                }, lineHeight = 16.sp)
            }
        }
        // The pressing note: how long and when you last played, as the sleeve's small print.
        // (681a emulator review e679r10: "Played Never played" - an unplayed record says so once.)
        val pressing = listOfNotNull(if (ctx.playtime == null) "Not spun yet" else "Played " + ctx.played, ctx.last?.let { "last spun $it" },
            if (ctx.total > 0) "${ctx.earned} of ${ctx.total} tracks" else null)
        Spacer(Modifier.height(6.dp))
        Text(pressing.joinToString("  ·  "), color = ink.copy(alpha = 0.72f), fontSize = 11.sp, fontStyle = FontStyle.Italic,
            fontFamily = FontFamily.Serif, lineHeight = 15.sp)
        Box(Modifier.padding(vertical = 8.dp).fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.35f)))
        // The liner text: the story with a drop cap, a D-pad stop that scrolls (the first stop of the page).
        val story = ctx.summary ?: "No liner notes for this record yet."
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            Text(buildAnnotatedString {
                withStyle(SpanStyle(color = red, fontSize = 30.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black)) { append(story.take(1)) }
                withStyle(SpanStyle(color = ink, fontSize = 13.sp, fontFamily = FontFamily.Serif)) { append(story.drop(1)) }
            }, lineHeight = 18.sp, modifier = Modifier.fillMaxWidth()
                .then(if (on) Modifier.border(1.dp, red.copy(alpha = 0.6f)).padding(4.dp) else Modifier.padding(4.dp)))
        }
    }
}

/** The sleeve's credit word for a fact. Pure. */
internal fun crateCreditName(label: String): String = when (label) {
    "Console" -> "FORMAT"
    "Year" -> "RELEASED"
    "Made by" -> "LABEL"
    "Genre" -> "STYLE"
    else -> label.uppercase()
}

/** The order stamp's touch height (dp): above the 24 dp minimum (R26), below a list row - it shares the first side's line. */
internal const val CRATE_STAMP_TOUCH = 26

/** "A1", "B3": a track's number on its side. Pure. */
internal fun crateTrackNo(earned: Boolean, i: Int): String = (if (earned) "A" else "B") + (i + 1)

/** 682aq: per row of a tracklist - a side heading above it (and whether it is the first side), and its track number. */
internal data class CrateMark(val heading: Boolean, val first: Boolean, val no: String)
internal fun crateMarks(earned: List<Boolean>): List<CrateMark> {
    var a = 0; var b = 0; var side: Boolean? = null
    return earned.map { e ->
        val heading = side != e; val first = side == null; side = e
        CrateMark(heading, heading && first, crateTrackNo(e, if (e) a++ else b++))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CrateTracklist(ctx: WinSceneCtx, live: Boolean, paper: Color, ink: Color, red: Color, modifier: Modifier) {
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    val big = bigPageText()
    // The order key: a rubber stamp on the sleeve (A steps it); the count is printed beside TRACKLIST in the sleeve's header.
    // (681s, emulator review 44194071430: count and stamp on their own line above the list took a track's room at x1.3 - the
    // stamp is printed on the first side's line now, at the head of the sheet, and scrolls with it (the D-pad's first stop as
    // before); a long order name moves it to the line below, never squeezed.)
    val stamp: @Composable () -> Unit = {
        NativeItem(onClick = t.nextOrder, focus = focus, live = live, minTouch = CRATE_STAMP_TOUCH.dp) { on ->
            Box(Modifier.defaultMinSize(minHeight = CRATE_STAMP_TOUCH.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.border(if (on) 2.dp else 1.dp, red, androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                    .background(if (on) red.copy(alpha = 0.12f) else Color.Transparent).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text("ORDER · " + t.order.label.uppercase(), color = red, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
            }
        }
    }
    Column(modifier) {
        // (682aq: lazy rows - the sides' headings and track numbers worked out once for the whole list, so each row knows its own)
        val marks = androidx.compose.runtime.remember(t.all) { crateMarks(t.all.map { it.isUnlocked }) }
        NativeRows(ink, Modifier.weight(1f).fillMaxWidth()) {
            if (t.all.isEmpty()) item { stamp() }
            nativeItems(t.all, { it.id }) { idx, ach -> androidx.compose.foundation.layout.Column {
                val earned = ach.isUnlocked
                val mk = marks[idx]
                if (mk.heading) {
                    val first = mk.first
                    FlowRow(Modifier.fillMaxWidth().padding(top = if (first) 0.dp else 6.dp, bottom = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (earned) "SIDE A  ·  YOURS" else "SIDE B  ·  STILL TO GET", color = red, fontSize = 9.sp, fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp, modifier = Modifier.align(Alignment.CenterVertically))
                        if (first) stamp()
                    }
                }
                val no = mk.no
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { on ->
                        Column(Modifier.fillMaxWidth().background(if (on) ink.copy(alpha = 0.07f) else Color.Transparent).padding(vertical = 3.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(no, color = red, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(28.dp))
                                // The badge as the record's centre label.
                                Box(Modifier.size(24.dp).clip(CircleShape).background(ink.copy(alpha = 0.12f)).border(1.dp, ink.copy(alpha = 0.4f), CircleShape)) {
                                    NativeBadgeMark(ach.title, earned, ink)
                                    AsyncImage(com.shelf.launcher.data.RaBadges.url(ach.badgeName), ach.title, contentScale = ContentScale.Crop,
                                        colorFilter = if (earned) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
                                        alpha = if (earned) 1f else 0.55f, modifier = Modifier.fillMaxSize())
                                }
                                Spacer(Modifier.width(8.dp))
                                // The dotted leader to the running time (the points), drawn under the title's line: the title has the
                                // whole width and the leader shows where the title ends, as on a printed tracklist.
                                // (681cr, CI AchFirstRowRoboTest CRATE x1.3: a title beside a leader with the same weight had half the line
                                // - "Defeat / the first / colossus" in three lines, and the first track was cut at the page's foot.)
                                val ground = if (on) ink.copy(alpha = 0.07f).compositeOver(paper) else paper
                                Box(Modifier.weight(1f), contentAlignment = Alignment.BottomStart) {
                                    Canvas(Modifier.matchParentSize()) {
                                        val y = size.height - 5.dp.toPx()
                                        drawLine(ink.copy(alpha = 0.35f), Offset(0f, y), Offset(size.width - 4.dp.toPx(), y), 1.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())))
                                    }
                                    Text(ach.title ?: "Untitled", color = ink.copy(alpha = if (earned) 1f else 0.6f), fontSize = 13.sp,
                                        fontFamily = FontFamily.Serif, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, lineHeight = 16.sp,
                                        modifier = Modifier.background(ground).padding(end = 6.dp))
                                }
                                Text(ach.points?.let { "$it pts" } ?: "—", color = ink.copy(alpha = if (earned) 0.85f else 0.5f), fontSize = 11.sp,
                                    fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                            }
                            // The lyric sheet: what it asks, always whole (two lines, then all of it once opened).
                            ach.description?.let { d ->
                                if (opened) Text(d, color = ink.copy(alpha = 0.8f), fontSize = 11.sp, fontStyle = FontStyle.Italic, fontFamily = FontFamily.Serif,
                                    lineHeight = 15.sp, modifier = Modifier.padding(start = 60.dp))
                                // (681cr: with large text a closed track keeps its words to one line - shrinking readably, then sliding;
                                // A shows them all - so the first track is whole when the tracklist opens on a short sleeve.)
                                else WinFullText(d, color = ink.copy(alpha = 0.62f), fontSize = 11.sp, maxLines = if (big) 1 else 2, lineHeight = 14.sp,
                                    modifier = Modifier.padding(start = 60.dp))
                            }
                            if (opened) {
                                val facts = listOfNotNull(
                                    (ach.dateEarnedHardcore ?: ach.dateEarned)?.let { "got it " + it.take(10) },
                                    ach.unlockPercent?.let { "%.1f %% of players".format(it) },
                                    ach.type?.takeIf { it.isNotBlank() }
                                )
                                if (facts.isNotEmpty()) Text(facts.joinToString("  ·  "), color = red, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 60.dp, top = 2.dp))
                            }
                        }
                    }
                }
            } }
            if (t.all.isEmpty()) item { Text("No tracks on this record.", color = ink.copy(alpha = 0.6f), fontSize = 12.sp, fontStyle = FontStyle.Italic,
                fontFamily = FontFamily.Serif) }
        }
    }
}

@Composable
private fun CratePressingNotes(ctx: WinSceneCtx, live: Boolean, ink: Color, red: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(ink), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(), focus = if (i == 0) focus else null, live = live) { on ->
                androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                val armed = confirm.armed == i
                // (682k, owner 18 Nov: the confirmation's last word touched the insert's edge at x1.0 - the row keeps a margin there.)
                Row(Modifier.fillMaxWidth().background(if (on) ink.copy(alpha = 0.07f) else Color.Transparent).padding(top = 4.dp, bottom = 4.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    // The sticker: a printed round with its number (a warning mark for the ones that remove something).
                    Box(Modifier.size(22.dp).clip(CircleShape).background(if (o.destructive || on) red else ink.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                        Text(if (o.destructive) "!" else "${i + 1}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        WinFullText(o.label, color = if (o.destructive) red else ink, fontSize = 14.sp, maxLines = 2, fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif, lineHeight = 18.sp)
                        o.sub?.let { WinFullText(it, color = ink.copy(alpha = 0.65f), fontSize = 11.sp, maxLines = 2, fontFamily = FontFamily.Serif, lineHeight = 14.sp) }
                        // (681b: the confirmation - printed in the sleeve's red under the line, as a warning sticker reads.)
                        // (682k: the sticker spans the line's width inside the row's margin - one line where it fits, wrapping whole words
                        // where it doesn't; it never meets the insert's edge or the list's scroll bar.)
                        // (682v, owner 18 Nov: the confirmation readable and spaced - 12 sp on 16, a 6 dp gap above, room inside its box)
                        if (armed) Text("Press A again to confirm  ·  move away to cancel", color = red, fontSize = 12.sp, fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Serif, lineHeight = 16.sp,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).border(1.dp, red).padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                    if (on) Text("▸", color = red, fontSize = 14.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        if (ctx.options.isEmpty()) Text("No pressing notes.", color = ink.copy(alpha = 0.6f), fontSize = 12.sp, fontStyle = FontStyle.Italic, fontFamily = FontFamily.Serif)
    }
}
