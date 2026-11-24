package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * P2 (733a, class C): Case flow's own pages. The window is the case opened into its media on the layout's dark glass stage -
 * the picture in a black glass well with a shine, the name band with an accent edge on the case's side, glossy round-ended
 * pills reflected on the stage floor. Its pages take the picture's place, in the same glass, like the menu of the disc inside:
 *  - Info         = CASE: the media view as before (CaseFlowMedia).
 *  - Details      = DETAILS (its button's name): each fact a glass strip with the name band's accent edge - the label small
 *                   and spaced, the value white - then the story in its own glass panel (a D-pad stop).
 *  - Achievements = EXTRAS (the disc's bonus menu): "N of M unlocked" over a glossy bar with its reflection on the floor, then
 *                   each achievement a glass slide - its badge with a reflection, the title, "Unlocked <date>" / "Locked", a
 *                   points pill; A opens the whole text and how rare it is. The order is a small glossy pill.
 *  - Options      = SETUP (the disc menu's setup): each option a glossy round-ended pill like the window's own (accent gloss
 *                   when lit); one that removes something is red and asks for a second A (NativeConfirm).
 * Heading: the name band's accent edge, the page's name and a round-ended pill per page (the shown one in the accent) - after
 * the old page has gone. Motion: the next page flows in like the next case - from the right going forward, from the left
 * going back, growing from 92 %; none with Motion Off.
 * Shared: data, focus, B, Play-first, inert outgoing copy (NativePageKit).
 */

/** The case's name for a page. Pure. */
internal fun casePageName(p: WinPage): String = when (p) {
    WinPage.MAIN -> "Case"
    WinPage.DETAILS -> "Details"
    WinPage.TROPHIES -> "Extras"
    WinPage.OPTIONS -> "Setup"
    WinPage.PERF -> "Performance"
}

/** The count over the EXTRAS bar: "N of M unlocked". Pure. */
internal fun caseExtrasLine(earned: Int, total: Int): String = "${earned.coerceIn(0, maxOf(total, 0))} of $total unlocked"

/** An extra's note: "Unlocked <date>" / "Unlocked" when yours, "Locked" still to get. Pure. */
internal fun caseEntryLine(earned: Boolean, date: String?): String =
    if (!earned) "Locked" else date?.takeIf { it.isNotBlank() }?.let { "Unlocked $it" } ?: "Unlocked"

/** The bar's filled share, 0..1 (nothing with no achievements). Pure. */
internal fun caseExtrasShare(earned: Int, total: Int): Float = if (total <= 0) 0f else (earned.toFloat() / total).coerceIn(0f, 1f)

internal fun caseMove(dir: Int): ContentTransform =
    (fadeIn(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) +
        scaleIn(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing), initialScale = 0.92f) +
        slideIn(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) { s -> IntOffset(if (dir >= 0) s.width / 6 else -s.width / 6, 0) }) togetherWith
        fadeOut(tween(NATIVE_OUT_MS))

private val CaseGlass = Color.White.copy(alpha = 0.07f)
private val CaseRim = Color.White.copy(alpha = 0.14f)
private val CaseMuted = Color(0xFFB9BEC9)
private val CaseRed = Color(0xFFD9443A)

/** The two colours mixed [t] of the way (CaseFlowMedia's mix). */
private fun caseMix(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)

private fun caseOn(accent: Color) = if (accent.luminance() > 0.5f) Color(0xFF111111) else Color.White

/** The heading row: the name band's accent edge, the page's name, a round-ended pill per page (the shown one in the accent). */
@Composable
internal fun CasePageHeading(tabs: List<WinPage>, shown: WinPage, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(24.dp).background(accent, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(10.dp))
        Text(casePageName(shown), color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        Spacer(Modifier.width(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEach { t ->
                val on = t == shown
                Box(Modifier.width(if (on) 18.dp else 8.dp).height(6.dp)
                    .background(if (on) accent else Color.White.copy(alpha = 0.25f), RoundedCornerShape(50)))
            }
        }
    }
}

/** One of the case's pages in the picture's glass well (CASE is the media view itself, drawn by CaseFlowMedia). */
@Composable
internal fun AnimatedVisibilityScope.CasePage(ctx: WinSceneCtx, p: WinPage, live: Boolean, accent: Color, modifier: Modifier) {
    when (p) {
        WinPage.DETAILS -> CaseDetails(ctx, live, accent, modifier)
        WinPage.TROPHIES -> CaseExtras(ctx, live, accent, modifier)
        WinPage.OPTIONS -> CaseSetup(ctx, live, accent, modifier)
        WinPage.MAIN -> {}
        else -> ctx.pageFor(p, modifier, live) // (Performance: the shared page - not on the case yet)
    }
}

@Composable
private fun CaseDetails(ctx: WinSceneCtx, live: Boolean, accent: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val facts = nativeFacts(ctx)
    Column(modifier.nativeScroll(Color.White), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        facts.forEach { f ->
            Row(Modifier.fillMaxWidth().background(CaseGlass, RoundedCornerShape(8.dp)).border(1.dp, CaseRim, RoundedCornerShape(8.dp))
                .padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(3.dp).height(26.dp).background(accent.copy(alpha = 0.85f), RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(10.dp))
                WholeWordsText(f.label.uppercase(), color = CaseMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, lineHeight = 14.sp,
                    modifier = Modifier.widthIn(max = 150.dp).padding(vertical = 5.dp))
                Spacer(Modifier.weight(1f).widthIn(min = 10.dp))
                WholeWordsText(f.value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 17.sp,
                    modifier = Modifier.widthIn(max = 280.dp).padding(vertical = 5.dp))
            }
        }
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            Column(Modifier.fillMaxWidth().background(CaseGlass, RoundedCornerShape(10.dp))
                .border(if (on) 2.dp else 1.dp, if (on) accent else CaseRim, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text("STORY", color = CaseMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp, lineHeight = 14.sp)
                Text(ctx.summary ?: "No story for this game yet.", color = Color(0xFFD5D9E2), fontSize = 13.sp, lineHeight = 17.sp)
            }
        }
    }
}

@Composable
private fun CaseExtras(ctx: WinSceneCtx, live: Boolean, accent: Color, modifier: Modifier) {
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    val onAccent = caseOn(accent)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WholeWordsText(caseExtrasLine(t.earned, t.total), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp,
                modifier = Modifier.weight(1f))
            // (the order pill gets at most its share - 682r)
            NativeItem(onClick = t.nextOrder, Modifier.weight(1f, fill = false), focus = focus, live = live) { on ->
                Text("Order: " + t.order.label, color = if (on) onAccent else Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.background(if (on) Brush.verticalGradient(listOf(caseMix(accent, Color.White, 0.3f), accent))
                        else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.05f))), RoundedCornerShape(50))
                        .border(1.dp, if (on) Color.White.copy(alpha = 0.6f) else CaseRim, RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 5.dp))
            }
        }
        // The glossy bar and its reflection on the floor.
        if (t.total > 0) {
            val share = caseExtrasShare(t.earned, t.total)
            Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(8.dp).background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))) {
                if (share > 0f) Box(Modifier.fillMaxHeight().fillMaxWidth(share)
                    .background(Brush.verticalGradient(listOf(caseMix(accent, Color.White, 0.35f), accent)), RoundedCornerShape(50)))
            }
            Box(Modifier.fillMaxWidth(share.coerceAtLeast(0.001f)).height(4.dp)
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.28f), Color.Transparent)), RoundedCornerShape(50)))
        }
        Spacer(Modifier.height(4.dp))
        NativeRows(Color.White, Modifier.weight(1f).fillMaxWidth(), spacing = 4.dp) {
            nativeItems(t.all, { it.id }) { _, ach ->
                val earned = ach.isUnlocked
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { on ->
                        Column(Modifier.fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (earned) 0.11f else 0.06f), Color.White.copy(alpha = 0.03f))), RoundedCornerShape(10.dp))
                            .border(if (on) 2.dp else 1.dp, if (on) accent else CaseRim, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // The badge with its reflection, as the cases stand on the stage.
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    NativeBadge(ach.badgeName, ach.title, earned, accent, Modifier.size(32.dp), shape = RoundedCornerShape(5.dp))
                                    Box(Modifier.width(28.dp).height(3.dp).background(Brush.verticalGradient(listOf(
                                        (if (earned) accent else Color.White).copy(alpha = 0.25f), Color.Transparent)), RoundedCornerShape(50)))
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    WholeWordsText(ach.title ?: "Untitled", color = if (earned) Color.White else Color.White.copy(alpha = 0.72f),
                                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp)
                                    Text(caseEntryLine(earned, ach.dateEarned), color = if (earned) caseMix(accent, Color.White, 0.45f) else CaseMuted.copy(alpha = 0.8f),
                                        fontSize = 11.sp, lineHeight = 14.sp)
                                }
                                ach.points?.let { pts ->
                                    Text("$pts", color = if (earned) onAccent else Color.White.copy(alpha = 0.75f), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                        softWrap = false, modifier = Modifier.padding(start = 6.dp)
                                            .background(if (earned) accent else Color.White.copy(alpha = 0.1f), RoundedCornerShape(50))
                                            .padding(horizontal = 8.dp, vertical = 2.dp))
                                }
                            }
                            ach.description?.let { d ->
                                if (opened) Text(d, color = Color(0xFFD5D9E2), fontSize = 12.sp, lineHeight = 16.sp,
                                    modifier = Modifier.padding(start = 42.dp, top = 3.dp))
                                else WinFullText(d, color = CaseMuted, fontSize = 11.sp, maxLines = 1, lineHeight = 14.sp,
                                    modifier = Modifier.padding(start = 42.dp, top = 1.dp))
                            }
                            if (opened) ach.unlockPercent?.let { pc ->
                                Text("%.1f %% of players unlocked this".format(pc), color = caseMix(accent, Color.White, 0.45f), fontSize = 11.sp,
                                    modifier = Modifier.padding(start = 42.dp, top = 1.dp))
                            }
                        }
                    }
                }
            }
            if (t.all.isEmpty()) item {
                Text("No extras on this game yet.", color = CaseMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun CaseSetup(ctx: WinSceneCtx, live: Boolean, accent: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    val onAccent = caseOn(accent)
    Column(modifier.nativeScroll(Color.White), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(),
                focus = if (i == 0) focus else null, live = live) { on ->
                androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                val armed = confirm.armed == i
                val fill = when {
                    on && o.destructive -> Brush.verticalGradient(listOf(caseMix(CaseRed, Color.White, 0.25f), CaseRed))
                    on -> Brush.verticalGradient(listOf(caseMix(accent, Color.White, 0.3f), accent))
                    else -> Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.04f)))
                }
                val ink = when { on && o.destructive -> Color.White; on -> onAccent; o.destructive -> Color(0xFFFFB3AC); else -> Color.White }
                Column(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().background(fill, RoundedCornerShape(22.dp))
                        .border(if (on) 2.dp else 1.dp, when { on -> Color.White.copy(alpha = 0.7f); o.destructive -> CaseRed.copy(alpha = 0.7f); else -> CaseRim }, RoundedCornerShape(22.dp))
                        .padding(horizontal = 18.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        WinFullText(o.label, color = ink, fontSize = 14.sp, maxLines = 2, fontWeight = FontWeight.SemiBold, lineHeight = 17.sp)
                        o.sub?.let { WinFullText(it, color = ink.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp) }
                    }
                    if (armed) Text("Press A again to remove  ·  move away to cancel", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        lineHeight = 15.sp, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            .background(CaseRed.copy(alpha = 0.8f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 5.dp))
                }
            }
        }
        if (ctx.options.isEmpty()) Text("No options for this game.", color = CaseMuted, fontSize = 13.sp)
    }
}

/** The page area on the glass stage: heading, then the page in the picture's black glass well with its shine. */
@Composable
internal fun CaseFlowPageArea(ctx: WinSceneCtx, accent: Color, modifier: Modifier) {
    val heading = rememberHeadingPage(ctx.page)
    Column(modifier) {
        CasePageHeading(nativeTabs(ctx.pages), if (heading == WinPage.MAIN) ctx.page else heading, accent)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().weight(1f, fill = false).height(ctx.maxH)
            .background(Brush.verticalGradient(listOf(Color(0xFF15171C), Color(0xFF0B0C0F))), RoundedCornerShape(10.dp))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp)).padding(8.dp)) {
            NativePages(ctx, Modifier.fillMaxSize(), label = "casePage", move = ::caseMove) { pg, pageLive ->
                CasePage(ctx, pg, pageLive, accent, Modifier.fillMaxSize())
            }
            // The glass's shine.
            Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.06f), Color.Transparent, Color.Transparent)),
                RoundedCornerShape(10.dp)))
        }
    }
}
