package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/*
 * P2 batch 2 (682n): the Game magazine window's own pages. The window is a feature torn out of the issue and pinned up, so its
 * pages are the magazine's own print furniture, in the issue's style (Bold / Glossy / Computer: MagStyle's paper, ink, accents
 * and type):
 *  - Details      = THE REVIEW: a FACT FILE box (the facts as the box's ruled lines, like a review's spec panel) and the story set
 *                   as the article - a drop cap in the issue's accent, the text a D-pad stop that scrolls.
 *  - Achievements = CHECKLIST: the walkthrough's printed checklist - a SCORE box (earned of all, the points), each achievement a
 *                   tick box (ticked in the accent for one you have, an empty box for one to get), A opens its tip text.
 *  - Options      = READER SERVICES: each option a coupon with a dashed cut-out edge; one that removes something is a WARNING
 *                   coupon in the accent that asks for a second A (NativeConfirm).
 * Motion: the page turns - the next sheet slides in from the side you went, the last one slides a little and fades.
 * Shared: data, focus, B, Play-first, inert outgoing copy (NativePageKit); the hand-over keeps a quick A (682j).
 */

/** The page's name in the masthead line. Pure. */
internal fun magPageName(p: WinPage): String = when (p) {
    WinPage.MAIN -> "FEATURE"
    WinPage.DETAILS -> "THE REVIEW"
    WinPage.TROPHIES -> "CHECKLIST"
    WinPage.OPTIONS -> "READER SERVICES"
    WinPage.PERF -> "PERFORMANCE"
}

/** The checklist's score box: "earned / all" and the points earned of all. Pure. */
internal fun magScore(earned: Int, total: Int, pts: Int, ptsAll: Int): Pair<String, String> =
    "$earned / $total" to (if (ptsAll > 0) "$pts OF $ptsAll PTS" else "NO POINTS")

/** The taped cover's step back / forward: done before the next sheet is past a fifth of its fade-in (682ag kh13). */
internal fun magCoverMs(): Int = NATIVE_OUT_MS + 30

internal fun magMove(dir: Int): ContentTransform =
    // (682ag: the next sheet shows once the last one has gone - no two pages' print at once)
    (slideInHorizontally(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) { w -> w * dir / 3 } + fadeIn(tween(160, delayMillis = NATIVE_OUT_MS))) togetherWith
        (slideOutHorizontally(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) { w -> -w * dir / 8 } + fadeOut(tween(NATIVE_OUT_MS)))

/** One of the magazine's own pages (Info is the feature itself, drawn by MagazinePage). */
@Composable
internal fun AnimatedVisibilityScope.MagPage(ctx: WinSceneCtx, st: MagStyle, p: WinPage, live: Boolean, modifier: Modifier) {
    when (p) {
        WinPage.DETAILS -> MagReview(ctx, st, live, modifier)
        WinPage.TROPHIES -> MagChecklist(ctx, st, live, modifier)
        WinPage.OPTIONS -> MagServices(ctx, st, live, modifier)
        else -> ctx.pageFor(p, modifier, live) // (Performance: the shared page - not a page of the magazine yet)
    }
}

@Composable
private fun MagReview(ctx: WinSceneCtx, st: MagStyle, live: Boolean, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    Column(modifier.nativeScroll(st.ink)) {
        // FACT FILE: the review's spec box - a heavy rule, the box's name in reverse, the facts as ruled lines.
        val facts = nativeFacts(ctx, setOf("Trophies"))
        if (facts.isNotEmpty()) Column(Modifier.fillMaxWidth().border(2.dp, st.ink)) {
            Text("FACT FILE", color = st.paper, fontFamily = st.head, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.5.sp,
                modifier = Modifier.fillMaxWidth().background(st.ink).padding(horizontal = 6.dp, vertical = 2.dp))
            // (682ag, owner review 23 Nov: whole words - a narrow box puts the field over its value instead of a word a line beside a
            // fixed 104 dp field column)
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stack = maxWidth < 240.dp
            Column {
            facts.forEachIndexed { i, f ->
                if (stack) Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text(f.label.uppercase(), color = st.accent, fontFamily = st.head, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 0.5.sp)
                    WholeWordsText(f.value, color = st.ink, fontFamily = st.body, fontSize = 12.sp, lineHeight = 15.sp)
                } else Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.Top) {
                    Text(f.label.uppercase(), color = st.accent, fontFamily = st.head, fontWeight = FontWeight.Black, fontSize = 10.sp,
                        letterSpacing = 0.5.sp, modifier = Modifier.width(104.dp).padding(top = 1.dp))
                    WholeWordsText(f.value, color = st.ink, fontFamily = st.body, fontSize = 12.sp, lineHeight = 15.sp, modifier = Modifier.weight(1f))
                }
                if (i < facts.size - 1) Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp).height(1.dp).background(st.ink.copy(alpha = 0.18f)))
            }
            }
            }
        }
        Spacer(Modifier.height(8.dp))
        // The article: the story with a drop cap in the issue's accent - the page's first D-pad stop.
        val story = ctx.summary ?: "No review of this game in this issue yet."
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            Text(buildAnnotatedString {
                withStyle(SpanStyle(color = st.accent, fontSize = 30.sp, fontFamily = st.head, fontWeight = FontWeight.Black)) { append(story.take(1)) }
                append(story.drop(1))
            }, color = st.ink, fontFamily = st.body, fontSize = 13.sp, lineHeight = 18.sp,
                modifier = Modifier.fillMaxWidth().then(if (on) Modifier.border(2.dp, st.accent) else Modifier).padding(6.dp))
        }
    }
}

@Composable
private fun MagChecklist(ctx: WinSceneCtx, st: MagStyle, live: Boolean, modifier: Modifier) {
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    Column(modifier) {
        // The SCORE box beside the order key (a printed "SORTED BY" tab; A steps it).
        Row(verticalAlignment = Alignment.CenterVertically) {
            val pts = t.list.filter { it.isUnlocked }.sumOf { it.points ?: 0 }
            val (score, points) = magScore(t.earned, t.total, pts, t.list.sumOf { it.points ?: 0 })
            // (682r, KONKR x1.3: the points sat between the box and the order key and broke a word per line - "26 / OF / 95 /
            // PT" - pushing the list off the page; they are the box's own last line now.)
            Column(Modifier.border(2.dp, st.ink).padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text("SCORE", color = st.accent, fontFamily = st.head, fontWeight = FontWeight.Black, fontSize = 9.sp, letterSpacing = 1.5.sp)
                Text(score, color = st.ink, fontFamily = st.head, fontWeight = FontWeight.Black, fontSize = 18.sp, lineHeight = 20.sp)
                Text(points, color = st.muted, fontFamily = st.head, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 0.5.sp, lineHeight = 11.sp)
            }
            Spacer(Modifier.weight(1f).widthIn(min = 8.dp))
            // (682r, RP Classic x1.3: unbounded, the order key took the row and the title beside it went to 0 px wide, one letter a
            // line, pushing the list off the page - the key gets at most its share, as the TV's)
            NativeItem(onClick = t.nextOrder, Modifier.weight(1f, fill = false), focus = focus, live = live) { on ->
                Text("SORTED BY  " + t.order.label.uppercase(), color = if (on) st.paper else st.ink, fontFamily = st.head, fontWeight = FontWeight.Black,
                    fontSize = 10.sp, letterSpacing = 1.sp,
                    modifier = Modifier.background(if (on) st.accent else Color.Transparent).border(1.dp, st.ink).padding(horizontal = 6.dp, vertical = 3.dp))
            }
        }
        Box(Modifier.padding(vertical = 5.dp).fillMaxWidth().height(2.dp).background(st.ink))
        Column(Modifier.weight(1f).fillMaxWidth().nativeScroll(st.ink), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            t.list.forEach { ach ->
                val earned = ach.isUnlocked
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { on ->
                        Column(Modifier.fillMaxWidth().background(if (on) st.accent2.copy(alpha = 0.22f) else Color.Transparent)
                            .then(if (on) Modifier.border(2.dp, st.accent) else Modifier).padding(horizontal = 4.dp, vertical = 3.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // The tick box: ticked in the accent for one you have, empty for one to get.
                                Canvas(Modifier.size(16.dp)) {
                                    drawRect(st.ink, style = Stroke(1.5.dp.toPx()))
                                    if (earned) {
                                        val p = androidx.compose.ui.graphics.Path().apply {
                                            moveTo(size.width * 0.18f, size.height * 0.52f); lineTo(size.width * 0.42f, size.height * 0.78f)
                                            lineTo(size.width * 0.86f, size.height * 0.18f)
                                        }
                                        drawPath(p, st.accent, style = Stroke(2.5.dp.toPx()))
                                    }
                                }
                                Spacer(Modifier.width(6.dp))
                                Box(Modifier.size(24.dp).background(st.ink.copy(alpha = 0.1f))) {
                                    NativeBadgeMark(ach.title, earned, st.ink)
                                    AsyncImage(com.shelf.launcher.data.RaBadges.url(ach.badgeName), ach.title, contentScale = ContentScale.Crop,
                                        colorFilter = if (earned) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
                                        alpha = if (earned) 1f else 0.55f, modifier = Modifier.size(24.dp))
                                }
                                Spacer(Modifier.width(6.dp))
                                WholeWordsText((ach.title ?: "Untitled").uppercase(), color = st.ink, fontFamily = st.head, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                                    lineHeight = 15.sp, modifier = Modifier.weight(1f))
                                Text(ach.points?.let { "$it PTS" } ?: "-", color = if (earned) st.accent else st.muted, fontFamily = st.head,
                                    fontWeight = FontWeight.Black, fontSize = 10.sp, modifier = Modifier.padding(start = 6.dp))
                            }
                            ach.description?.let { d ->
                                if (opened) Text(d, color = st.ink, fontFamily = st.body, fontSize = 11.sp, lineHeight = 15.sp, fontStyle = FontStyle.Italic,
                                    modifier = Modifier.padding(start = 52.dp, top = 1.dp))
                                else WinFullText(d, color = st.muted, fontFamily = st.body, fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp,
                                    modifier = Modifier.padding(start = 52.dp, top = 1.dp))
                            }
                            if (opened) {
                                val facts = listOfNotNull(
                                    (ach.dateEarnedHardcore ?: ach.dateEarned)?.let { "TICKED " + it.take(10) },
                                    ach.unlockPercent?.let { "%.1f %% OF PLAYERS".format(it) },
                                    ach.type?.takeIf { it.isNotBlank() }?.uppercase()
                                )
                                if (facts.isNotEmpty()) Text(facts.joinToString("  ·  "), color = st.accent, fontFamily = st.head, fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp, modifier = Modifier.padding(start = 52.dp, top = 1.dp))
                            }
                        }
                    }
                }
            }
            if (t.list.isEmpty()) Text("No checklist for this game - it has no achievements.", color = st.muted, fontFamily = st.body, fontSize = 12.sp)
        }
    }
}

@Composable
private fun MagServices(ctx: WinSceneCtx, st: MagStyle, live: Boolean, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(st.ink), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(), focus = if (i == 0) focus else null, live = live) { on ->
                androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                val armed = confirm.armed == i
                val edge = if (o.destructive) st.accent else st.ink
                // The coupon: a dashed cut-out edge (solid while it has the focus), a scissors mark at its corner.
                Column(Modifier.fillMaxWidth().background(if (on) st.accent2.copy(alpha = 0.18f) else Color.Transparent)
                    .drawCoupon(edge, solid = on).padding(start = 22.dp, end = 10.dp, top = 6.dp, bottom = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (o.destructive) Text("WARNING", color = st.paper, fontFamily = st.head, fontWeight = FontWeight.Black, fontSize = 9.sp,
                            letterSpacing = 1.sp, modifier = Modifier.background(st.accent).padding(horizontal = 4.dp, vertical = 1.dp))
                        if (o.destructive) Spacer(Modifier.width(6.dp))
                        WinFullText(o.label.uppercase(), color = if (o.destructive) st.accent else st.ink, fontFamily = st.head, fontWeight = FontWeight.Black,
                            fontSize = 13.sp, maxLines = 2, lineHeight = 16.sp, modifier = Modifier.weight(1f))
                    }
                    o.sub?.let { WinFullText(it, color = st.muted, fontFamily = st.body, fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp) }
                    // (682v, owner 18 Nov: the confirmation readable and spaced - 12 sp on 16, a 6 dp gap above, room inside its box)
                    if (armed) Text("Press A again to confirm  ·  move away to cancel", color = st.accent, fontFamily = st.head, fontWeight = FontWeight.Black,
                        fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).border(1.5.dp, st.accent).padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }
        if (ctx.options.isEmpty()) Text("No reader services for this game.", color = st.muted, fontFamily = st.body, fontSize = 12.sp)
    }
}

/** A coupon's cut-out edge: dashed (solid when lit), with a little scissors mark at its top-left corner. */
private fun Modifier.drawCoupon(c: Color, solid: Boolean): Modifier = this.drawBehind {
    val w = 1.5.dp.toPx()
    drawRect(c, topLeft = Offset(w / 2, w / 2), size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
        style = Stroke(w, pathEffect = if (solid) null else PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
    // the scissors: two small rings and their blades
    val cx = 10.dp.toPx(); val cy = 10.dp.toPx(); val r = 2.5.dp.toPx()
    drawCircle(c, r, Offset(cx - 3.dp.toPx(), cy + 2.dp.toPx()), style = Stroke(1.2.dp.toPx()))
    drawCircle(c, r, Offset(cx + 3.dp.toPx(), cy + 2.dp.toPx()), style = Stroke(1.2.dp.toPx()))
    drawLine(c, Offset(cx - 1.dp.toPx(), cy), Offset(cx + 5.dp.toPx(), cy - 7.dp.toPx()), 1.2.dp.toPx())
    drawLine(c, Offset(cx + 1.dp.toPx(), cy), Offset(cx - 5.dp.toPx(), cy - 7.dp.toPx()), 1.2.dp.toPx())
}


/** 682ag (pure): the feature's "see page" callouts in two columns only where each column holds the longest word, its arrow and
 *  the callout's padding ([padPx], both sides) beside a [gapPx] gutter. */
internal fun magTwoColumns(widthPx: Float, longestWordPx: Float, arrowPx: Float, gapPx: Float, padPx: Float): Boolean =
    (widthPx - gapPx) / 2f - padPx - arrowPx >= longestWordPx

/** 682ag (owner review 23 Nov): no cover picture - the issue's own printed cover: the masthead in a band of the issue's colour,
 *  the console's plate in the game's colour (initials, name), a strip of the issue's second colour. Never an empty card. */
@Composable
internal fun MagNoArtCover(ctx: WinSceneCtx, st: MagStyle, modifier: Modifier) {
    Column(modifier.background(st.paper).border(1.dp, st.ink.copy(alpha = 0.25f))) {
        // (the masthead shrinks, then slides, in the pages' small cover - never cut)
        WinFullText(st.masthead, color = st.paper, fontFamily = st.head, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp, maxLines = 1,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().background(st.accent).padding(vertical = 3.dp))
        Box(Modifier.weight(1f).fillMaxWidth().background(placeholderBrush(ctx.g.hue)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(6.dp)) {
                Text(studioInitials(ctx.console), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, maxLines = 1, softWrap = false) // qa-ok: TRUNCATE-CLIP (2-4 initials)
                // (the console's name on the feature's big cover; the pages' small one keeps the initials alone)
                if (ctx.page == WinPage.MAIN) WholeWordsText(ctx.console, color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, lineHeight = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        // (the cover line: the issue's own strip - the game's name is the headline beside the cover, whole; on the pages' small
        // cover a long name had no room)
        Box(Modifier.fillMaxWidth().height(10.dp).background(st.accent2))
    }
}
