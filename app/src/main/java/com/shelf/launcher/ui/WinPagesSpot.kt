package com.shelf.launcher.ui

import androidx.compose.foundation.lazy.items
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * P2 batch 2 (682t, owner 18 Nov 21:28 "start Spotlight now"): the Spotlight window's own pages. The window is a film poster
 * under a spotlight - the backdrop, the dark vignette, the light pooled on the title, the pill keys - so its pages are the
 * poster's printed parts and the theatre round it, in the poster's black, white and the theme's light:
 *  - Details      = THE PLOT: the poster's billing block - the credits in its condensed, letter-spaced capitals ("MADE BY",
 *                   "RELEASED", "GENRE" ...), centred as on a one-sheet - and the story as the tagline-sized synopsis under it
 *                   (a D-pad stop that scrolls).
 *  - Achievements = THE CREDITS: the end credits - each achievement a centred credit line, its badge as the cast photo, its
 *                   points as the billing; earned names lit, the rest in the dark; the order as "BILLING: ..."; A opens the line.
 *  - Options      = BACKSTAGE: each option a door under a work light - a dark card with the light's edge; one that removes
 *                   something is red and asks for a second A (NativeConfirm).
 * Motion: the spotlight swings - the next page comes in from the side it is in key order, the last one goes out the other
 * way, dimming as it leaves the light; the poster's pool of light moves from the title down onto the page (SpotPoster).
 * The poster's Play pill row stays where it is on every page (owner 18 Nov: Play's size and place the same on every page).
 */

/** The page's name, as the poster's small header line. Pure. */
internal fun spotPageName(p: WinPage): String = when (p) {
    WinPage.MAIN -> "NOW SHOWING"
    WinPage.DETAILS -> "THE PLOT"
    WinPage.TROPHIES -> "THE CREDITS"
    WinPage.OPTIONS -> "BACKSTAGE"
    WinPage.PERF -> "PERFORMANCE"
}

/** A credit's billing: "★ 10" for its points, "★" without. Pure. */
internal fun spotBilling(points: Int?): String = points?.let { "★ $it" } ?: "★"

/** The billing block's label for a fact: the poster's capitals ("Developer" -> "MADE BY"). Pure. */
internal fun spotCreditLabel(label: String): String = when (label.lowercase()) {
    "developer" -> "MADE BY"
    "publisher" -> "PRESENTED BY"
    "year", "released" -> "RELEASED"
    "genre", "genres" -> "GENRE"
    "played" -> "LAST SHOWING"
    else -> label.uppercase()
}

internal fun spotMove(dir: Int): ContentTransform {
    val d = if (dir < 0) -1 else 1
    return (fadeIn(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) +
        slideInHorizontally(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) { w -> d * w / 8 }) togetherWith
        (fadeOut(tween(160)) + slideOutHorizontally(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) { w -> -d * w / 12 })
}

private val SPOT_DIM = Color.White.copy(alpha = 0.55f)
private val SPOT_RED = Color(0xFFE5484D)

/** One of Spotlight's own pages (Info is the poster itself, drawn by SpotPoster). */
@Composable
internal fun AnimatedVisibilityScope.SpotPage(ctx: WinSceneCtx, p: WinPage, live: Boolean, light: Color, modifier: Modifier) {
    when (p) {
        WinPage.DETAILS -> SpotPlot(ctx, live, light, modifier)
        WinPage.TROPHIES -> SpotCredits(ctx, live, light, modifier)
        WinPage.OPTIONS -> SpotBackstage(ctx, live, light, modifier)
        WinPage.MAIN -> {}
        else -> ctx.pageFor(p, modifier, live)
    }
}

@Composable
private fun SpotPlot(ctx: WinSceneCtx, live: Boolean, light: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    Column(modifier.nativeScroll(Color.White), horizontalAlignment = Alignment.CenterHorizontally) {
        // The billing block: label and name run on as one centred paragraph, the labels small, the names bold.
        val facts = nativeFacts(ctx, setOf("Trophies"))
        if (facts.isNotEmpty()) {
            val block = androidx.compose.ui.text.buildAnnotatedString {
                facts.forEachIndexed { i, f ->
                    if (i > 0) append("   ")
                    pushStyle(androidx.compose.ui.text.SpanStyle(color = SPOT_DIM, fontSize = 9.sp, fontWeight = FontWeight.Medium))
                    // (no-break spaces inside a credit: a label never ends a line apart from its name - RP Classic x1.0 "GENRE" /)
                    append(spotCreditLabel(f.label).replace(' ', '\u00A0') + "\u00A0")
                    pop()
                    pushStyle(androidx.compose.ui.text.SpanStyle(color = Color.White, fontWeight = FontWeight.Bold))
                    append(f.value.uppercase())
                    pop()
                }
            }
            Text(block, fontSize = 12.sp, letterSpacing = 1.5.sp, lineHeight = 17.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        Box(Modifier.width(48.dp).height(1.dp).background(light.copy(alpha = 0.8f)))
        Spacer(Modifier.height(8.dp))
        // The synopsis - the page's first D-pad stop (it scrolls on with Down).
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            Text(ctx.summary ?: "No synopsis for this title yet.", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, lineHeight = 19.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                    .then(if (on) Modifier.border(2.dp, light, RoundedCornerShape(6.dp)) else Modifier).padding(8.dp))
        }
    }
}

@Composable
private fun SpotCredits(ctx: WinSceneCtx, live: Boolean, light: Color, modifier: Modifier) {
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${t.earned} OF ${t.total} IN THE LIGHT", color = SPOT_DIM, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            // The billing order, as a pill like the poster's keys; A steps it. (682r rule: the key gets at most its share.)
            NativeItem(onClick = t.nextOrder, Modifier.weight(1f, fill = false), focus = focus, live = live) { on ->
                Text("BILLING: " + t.order.label.uppercase(), color = if (on) Color.Black else Color.White, fontSize = 10.sp, letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) light else Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 4.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        NativeRows(Color.White, Modifier.weight(1f).fillMaxWidth(), 2.dp) {
            items(t.all, key = { it.id }) { ach ->
                val earned = ach.isUnlocked
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { on ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                            .background(if (on) light.copy(alpha = 0.16f) else Color.Transparent)
                            .then(if (on) Modifier.border(1.dp, light.copy(alpha = 0.8f), RoundedCornerShape(6.dp)) else Modifier)
                            .padding(horizontal = 8.dp, vertical = 5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                NativeBadge(ach.badgeName, ach.title, earned, light, Modifier.size(30.dp), shape = CircleShape)
                                Spacer(Modifier.width(10.dp))
                                WholeWordsText((ach.title ?: "Untitled").uppercase(), color = if (earned) Color.White else SPOT_DIM, fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold, lineHeight = 15.sp, modifier = Modifier.weight(1f))
                                Text(spotBilling(ach.points), color = if (earned) light else SPOT_DIM, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    softWrap = false, modifier = Modifier.padding(start = 6.dp))
                            }
                            ach.description?.let { d ->
                                if (opened) Text(d, color = Color.White.copy(alpha = 0.88f), fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(start = 40.dp, top = 3.dp))
                                else WinFullText(d, color = SPOT_DIM, fontSize = 11.sp, maxLines = 1, lineHeight = 14.sp, modifier = Modifier.padding(start = 40.dp, top = 2.dp))
                            }
                            if (opened) {
                                val facts = listOfNotNull(
                                    (ach.dateEarnedHardcore ?: ach.dateEarned)?.let { "IN THE LIGHT SINCE " + it.take(10) },
                                    ach.unlockPercent?.let { "%.1f %% OF PLAYERS".format(it) }
                                )
                                if (facts.isNotEmpty()) Text(facts.joinToString("  ·  "), color = light, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 40.dp, top = 2.dp))
                            }
                        }
                    }
                }
            }
            if (t.all.isEmpty()) item {
            Text("No credits - this title has no achievements.", color = SPOT_DIM, fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SpotBackstage(ctx: WinSceneCtx, live: Boolean, light: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(Color.White), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(), focus = if (i == 0) focus else null, live = live) { on ->
                androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                val armed = confirm.armed == i
                val edge = if (o.destructive) SPOT_RED else light
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(if (on) Color(0xFF26282E) else Color(0xFF16181C).copy(alpha = 0.85f))
                    .border(1.dp, if (on) edge else Color.White.copy(alpha = 0.10f), RoundedCornerShape(6.dp)), verticalAlignment = Alignment.CenterVertically) {
                    // The work light's edge: lit on the door you're at.
                    Box(Modifier.width(4.dp).height(40.dp).background(if (on) edge else edge.copy(alpha = 0.25f)))
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 6.dp)) {
                        WinFullText(o.label.uppercase(), color = if (o.destructive) SPOT_RED else Color.White, fontSize = 12.sp, maxLines = 2,
                            fontWeight = FontWeight.Bold, lineHeight = 16.sp, letterSpacing = 1.sp)
                        o.sub?.let { WinFullText(it, color = SPOT_DIM, fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp) }
                        // (owner 18 Nov: confirmation text readable and spaced - on its own line, the poster's dark under it, a
                        // gap above, not cramped against the row's text)
                        if (armed) Text("Press A again to confirm  ·  move away to cancel", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            lineHeight = 16.sp, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(4.dp))
                                .background(SPOT_RED.copy(alpha = 0.85f)).padding(horizontal = 8.dp, vertical = 5.dp))
                    }
                }
            }
        }
        if (ctx.options.isEmpty()) Text("Nothing backstage for this title.", color = SPOT_DIM, fontSize = 12.sp)
    }
}

/**
 * 682ag (owner review 23 Nov - Spotlight: "an intentional no-art layout instead of a huge black empty screen"): a game without
 * a picture plays on a stage - deep red curtains drawn in at both sides, a warm pool of light on the middle, and on the main
 * page the bill in that light: "NOW SHOWING", the title large in whole words, the console and year under it. The pages keep
 * the curtains and the light; their own words sit on the stage.
 */
@Composable
internal fun SpotNoArtStage(ctx: WinSceneCtx, modifier: Modifier) {
    val lit = sceneLit(com.shelf.launcher.data.HomeLayout.SPOT, com.shelf.launcher.ui.theme.ShelfTheme.accent, Color.Black)
    Box(modifier) {
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF2B0B10), Color(0xFF120407), Color.Black)))
            // the curtains: soft vertical folds, darker toward the middle edge
            val cw = size.width * 0.16f
            for (side in 0..1) {
                val x0 = if (side == 0) 0f else size.width - cw
                val folds = 6
                for (i in 0 until folds) {
                    val fx = x0 + cw * i / folds
                    drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFF5A1018), Color(0xFF2A060B), Color(0xFF5A1018)), startX = fx, endX = fx + cw / folds),
                        topLeft = androidx.compose.ui.geometry.Offset(fx, 0f), size = androidx.compose.ui.geometry.Size(cw / folds, size.height))
                }
                val edge = if (side == 0) x0 + cw else x0
                drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(
                    if (side == 0) listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent) else listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                    startX = if (side == 0) edge else edge - cw * 0.5f, endX = if (side == 0) edge + cw * 0.5f else edge),
                    topLeft = androidx.compose.ui.geometry.Offset(if (side == 0) edge else edge - cw * 0.5f, 0f), size = androidx.compose.ui.geometry.Size(cw * 0.5f, size.height))
            }
            // the pool of light on the stage
            drawRect(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0xFFFFE2A8).copy(alpha = 0.22f), Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.36f), radius = size.minDimension * 0.55f))
        }
        if (ctx.page == WinPage.MAIN) Column(Modifier.align(Alignment.TopCenter).fillMaxWidth(0.62f).padding(top = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text("NOW SHOWING", color = lit, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
            Spacer(Modifier.height(8.dp))
            WholeWordsText(ctx.name.uppercase(), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black, lineHeight = 34.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(listOfNotNull(ctx.console, ctx.year).joinToString("  ·  ").uppercase(), color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, letterSpacing = 3.sp)
            ctx.genres?.let { Text(it.uppercase(), color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp, letterSpacing = 2.sp, modifier = Modifier.padding(top = 4.dp)) }
        }
    }
}
