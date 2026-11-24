package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * P2 (729a, class C): the Hero banner's own pages (Hero carousel and Hero tiles share the scene). The window is the hero banner
 * opened up - the game's key art behind a dark gradient, the logo / name bottom left - so its pages are the banner's own
 * billboard sections, set in the banner's white-on-art type over the same gradient:
 *  - Info         = SPOTLIGHT: the banner as before (logo, line, story; HeroBanner, unchanged).
 *  - Details      = THE FEATURE: the facts as a band of big key figures (value large and bold over a small label, each with a
 *                   thin accent rule, wrapping in whole words), the story as a pull quote with an accent bar (a D-pad stop).
 *  - Achievements = HALL OF FAME: a big "N / M" figure with a slim white progress bar, then each achievement a billboard row -
 *                   a round badge, title, "Unlocked <date>" in the accent when yours, "Locked" dim when not, points in a pill;
 *                   the order as a white pill; A opens its whole text and how rare it is.
 *  - Options      = QUICK ACTIONS: each option a big pill button in the banner's key style (white when focused); one that
 *                   removes something is red and asks for a second A (NativeConfirm).
 * Heading: the banner's tab strip - every page name in a row (wrapping, whole names), the shown one bold with a white underline,
 * after the old page has gone. Motion: the next page grows up out of the banner's lower left corner (where the hero text sits)
 * as it fades in, nudged from the right going forward, from the left going back; none with Motion Off.
 * Shared: data, focus, B, Play-first, inert outgoing copy (NativePageKit).
 */

/** The Hero banner's name for a page. Pure. */
internal fun heroPageName(p: WinPage): String = when (p) {
    WinPage.MAIN -> "SPOTLIGHT"
    WinPage.DETAILS -> "THE FEATURE"
    WinPage.TROPHIES -> "HALL OF FAME"
    WinPage.OPTIONS -> "QUICK ACTIONS"
    WinPage.PERF -> "PERFORMANCE"
}

/** An achievement's line under its title: "Unlocked <date>" / "Unlocked" when yours, "Locked" when still to get. Pure. */
internal fun heroUnlockLine(earned: Boolean, date: String?): String =
    if (!earned) "Locked" else date?.takeIf { it.isNotBlank() }?.let { "Unlocked $it" } ?: "Unlocked"

/** The Hall of Fame's big figure: "N / M"; "0 / 0" never shown (no list). Pure. */
internal fun heroFigure(earned: Int, total: Int): String = "$earned / $total"

/** The Hall of Fame's words beside the figure. Pure. */
internal fun heroFigureWords(total: Int): String = if (total == 1) "achievement unlocked" else "achievements unlocked"

/** The progress bar's fill (0..1). Pure. */
internal fun heroProgress(earned: Int, total: Int): Float = if (total <= 0) 0f else (earned.toFloat() / total).coerceIn(0f, 1f)

/** An achievement's points pill: "N pts" (1 pt), nothing without points. Pure. */
internal fun heroPoints(points: Int?): String? = points?.let { if (it == 1) "1 pt" else "$it pts" }

internal fun heroMove(dir: Int): ContentTransform =
    (fadeIn(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) +
        scaleIn(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing), initialScale = 0.93f, transformOrigin = TransformOrigin(0f, 1f)) +
        slideInHorizontally(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) { w -> if (dir >= 0) w / 12 else -w / 12 }) togetherWith
        fadeOut(tween(NATIVE_OUT_MS))

private val HERO_RED = Color(0xFFFF5A52)
private val HERO_INK = Color.White

/** The banner's tab strip: every page's name, the shown one bold and underlined in white. Whole names; it wraps when narrow. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun HeroTabStrip(tabs: List<WinPage>, shown: WinPage) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        tabs.forEach { t ->
            val on = t == shown
            // (the underline is drawn under the name - no intrinsic measuring)
            Text(heroPageName(t), color = if (on) HERO_INK else HERO_INK.copy(alpha = 0.55f), fontSize = 11.sp,
                fontWeight = if (on) FontWeight.Black else FontWeight.SemiBold, letterSpacing = 1.2.sp, softWrap = false, lineHeight = 14.sp,
                modifier = Modifier.drawBehind {
                    if (on) drawLine(HERO_INK, Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()), 2.dp.toPx())
                }.padding(bottom = 4.dp))
        }
    }
}

/** One of the banner's pages (Info is the banner itself, drawn by HeroBanner). */
@Composable
internal fun AnimatedVisibilityScope.HeroPage(ctx: WinSceneCtx, p: WinPage, live: Boolean, a: Color, modifier: Modifier) {
    when (p) {
        WinPage.DETAILS -> HeroFeature(ctx, live, a, modifier)
        WinPage.TROPHIES -> HeroHallOfFame(ctx, live, a, modifier)
        WinPage.OPTIONS -> HeroActions(ctx, live, a, modifier)
        WinPage.MAIN -> {}
        else -> ctx.pageFor(p, modifier, live) // (Performance: the shared page - not on the banner yet)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun HeroFeature(ctx: WinSceneCtx, live: Boolean, a: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val facts = nativeFacts(ctx, setOf("Trophies"))
    Column(modifier.nativeScroll(a)) {
        // The key figures: value big over a small label, a thin accent rule at the left of each.
        androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            facts.forEach { f ->
                Column(Modifier.widthIn(max = 220.dp).drawBehind {
                    drawLine(a, Offset(0f, 2.dp.toPx()), Offset(0f, size.height - 2.dp.toPx()), 2.dp.toPx())
                }.padding(start = 8.dp)) {
                    WholeWordsText(f.value, color = HERO_INK, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 19.sp)
                    Text(f.label.uppercase(), color = HERO_INK.copy(alpha = 0.6f), fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp, lineHeight = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        // The story as a pull quote.
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = if (on) 0.5f else 0.3f))
                .border(if (on) 2.dp else 0.dp, if (on) HERO_INK else Color.Transparent, RoundedCornerShape(10.dp)).padding(10.dp)) {
                Box(Modifier.width(3.dp).height(36.dp).clip(RoundedCornerShape(2.dp)).background(a))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("“", color = a, fontSize = 26.sp, fontWeight = FontWeight.Black, lineHeight = 22.sp)
                    Text(ctx.summary ?: "No story for this feature yet.", color = HERO_INK.copy(alpha = 0.9f), fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun HeroHallOfFame(ctx: WinSceneCtx, live: Boolean, a: Color, modifier: Modifier) {
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(heroFigure(t.earned, t.total), color = HERO_INK, fontSize = 24.sp, fontWeight = FontWeight.Black, softWrap = false, lineHeight = 26.sp)
            Spacer(Modifier.width(8.dp))
            Text(heroFigureWords(t.total), color = HERO_INK.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                lineHeight = 14.sp, modifier = Modifier.weight(1f))
            // (the order pill gets at most its share - 682r)
            NativeItem(onClick = t.nextOrder, Modifier.weight(1f, fill = false), focus = focus, live = live) { on ->
                Text("Order: " + t.order.label, color = if (on) Color.Black else HERO_INK, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) HERO_INK else HERO_INK.copy(alpha = 0.16f))
                        .padding(horizontal = 12.dp, vertical = 4.dp))
            }
        }
        // The slim progress bar, as a hero's watch bar.
        val fill = heroProgress(t.earned, t.total)
        Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(HERO_INK.copy(alpha = 0.22f))) {
            Box(Modifier.fillMaxWidth(fill).height(3.dp).background(a))
        }
        Spacer(Modifier.height(8.dp))
        NativeRows(a, Modifier.weight(1f).fillMaxWidth(), spacing = 4.dp) {
            nativeItems(t.all, { it.id }) { _, ach ->
                val earned = ach.isUnlocked
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { on ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = if (on) 0.55f else 0.28f))
                            .border(if (on) 2.dp else 0.dp, if (on) HERO_INK else Color.Transparent, RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                NativeBadge(ach.badgeName, ach.title, earned, a, Modifier.size(32.dp), shape = CircleShape)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    WholeWordsText(ach.title ?: "Untitled", color = if (earned) HERO_INK else HERO_INK.copy(alpha = 0.6f), fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold, lineHeight = 16.sp)
                                    Text(heroUnlockLine(earned, ach.dateEarned), color = if (earned) a else HERO_INK.copy(alpha = 0.5f), fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold, lineHeight = 13.sp)
                                }
                                heroPoints(ach.points)?.let {
                                    Text(it, color = HERO_INK.copy(alpha = if (earned) 0.95f else 0.55f), fontSize = 10.sp, fontWeight = FontWeight.Bold, softWrap = false,
                                        modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(50)).background(HERO_INK.copy(alpha = 0.14f))
                                            .padding(horizontal = 8.dp, vertical = 2.dp))
                                }
                            }
                            ach.description?.let { d ->
                                if (opened) Text(d, color = HERO_INK.copy(alpha = 0.88f), fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(start = 42.dp, top = 3.dp))
                                else WinFullText(d, color = HERO_INK.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1, lineHeight = 14.sp, modifier = Modifier.padding(start = 42.dp, top = 1.dp))
                            }
                            if (opened) ach.unlockPercent?.let { pc ->
                                Text("%.1f %% of players unlocked this".format(pc), color = a, fontSize = 10.sp, modifier = Modifier.padding(start = 42.dp, top = 1.dp))
                            }
                        }
                    }
                }
            }
            if (t.all.isEmpty()) item {
                Text("No achievements in this hall yet.", color = HERO_INK.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun HeroActions(ctx: WinSceneCtx, live: Boolean, a: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(a), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(),
                focus = if (i == 0) focus else null, live = live) { on ->
                androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                val armed = confirm.armed == i
                val bg = when { on && o.destructive -> HERO_RED; on -> HERO_INK; else -> HERO_INK.copy(alpha = 0.14f) }
                val ink = when { on && o.destructive -> Color.White; on -> Color.Black; o.destructive -> HERO_RED; else -> HERO_INK }
                Column(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(bg)
                        .border(1.dp, if (o.destructive) HERO_RED.copy(alpha = if (on) 1f else 0.6f) else Color.Transparent, RoundedCornerShape(50))
                        .padding(horizontal = 16.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        WinFullText(o.label, color = ink, fontSize = 13.sp, maxLines = 2, fontWeight = FontWeight.Bold, lineHeight = 16.sp)
                        o.sub?.let { WinFullText(it, color = ink.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp) }
                    }
                    if (armed) Text("Press A again to confirm  ·  move away to cancel", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(10.dp)).background(HERO_RED).padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
        }
        if (ctx.options.isEmpty()) Text("No quick actions for this game.", color = HERO_INK.copy(alpha = 0.6f), fontSize = 12.sp)
    }
}
