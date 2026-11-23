package com.shelf.launcher.ui

import androidx.compose.foundation.lazy.itemsIndexed
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
 * P2 batch 1 (681e): the Resume & discover window's own pages. The window is a big square beside a discover card, so its pages
 * are the discover feed itself - rounded cards and pills, the house style of a store's "for you" feed:
 *  - Details      = ABOUT THIS GAME: a feed of cards - the story (the first D-pad stop), the facts as pills, your play.
 *  - Achievements = PROGRESS FEED: the progress at the top, then the feed's cards in the chosen order, each once, under
 *                   shelf headings - EARNED, NEXT UP (the first three still to get), STILL TO GET; A opens a card in place.
 *  - Options      = a settings sheet of full-width pills; one that removes something is a red-outlined pill and asks twice.
 * Motion: the cards slide up into the feed one after another (none with Motion Off). Shared: data, focus, B, Play-first,
 * inert outgoing copy (NativePageKit).
 */

/** The feed's page name, the pill above it. Pure. */
internal fun discoverPageName(p: WinPage): String = when (p) {
    WinPage.MAIN -> "FOR YOU"
    WinPage.DETAILS -> "ABOUT THIS GAME"
    WinPage.TROPHIES -> "PROGRESS"
    WinPage.OPTIONS -> "SETTINGS"
    WinPage.PERF -> "PERFORMANCE"
    else -> p.name
}

/**
 * The feed's shelf heading before the [i]-th card of a run of earned / still-to-get achievements (the list keeps the chosen
 * order and shows each achievement once): EARNED over a run you have, NEXT UP over the first three still to get, STILL TO GET
 * after them. Null: no heading here. Pure.
 */
internal fun discoverShelf(earned: Boolean, i: Int): String? = when {
    earned -> if (i == 0) "EARNED" else null
    i == 0 -> "NEXT UP"
    i == DISCOVER_NEXT_UP -> "STILL TO GET"
    else -> null
}
internal const val DISCOVER_NEXT_UP = 3

internal fun discoverMove(@Suppress("UNUSED_PARAMETER") dir: Int): ContentTransform =
    fadeIn(tween(NATIVE_PAGE_MS / 2)) togetherWith fadeOut(tween(NATIVE_PAGE_MS / 3))

private val DISCOVER_CARD_BG = Color(0xFF262A32)
private val DISCOVER_RED = Color(0xFFE5484D)
private fun inkOnDiscover(c: Color) = if (c.luminance() > 0.5f) Color(0xFF111111) else Color.White

/** A card's slide up into the feed: card [i] arrives a little after the one above it. */
/** 682aq: each row's place in its run of earned / still-to-get rows (0 starts a shelf). */
internal fun discoverRuns(earned: List<Boolean>): List<Int> { var run = 0; var was: Boolean? = null
    return earned.map { e -> if (was != e) { was = e; run = 0 }; run++ } }

private fun Modifier.feedIn(p: () -> Float, i: Int): Modifier = this.graphicsLayer {
    val k = ((p() * (1f + 0.15f * i)) - 0.15f * i).coerceIn(0f, 1f)
    translationY = (1f - k) * (28.dp.toPx() + i * 10.dp.toPx()); alpha = k
}

@Composable
internal fun AnimatedVisibilityScope.DiscoverPage(ctx: WinSceneCtx, p: WinPage, live: Boolean, modifier: Modifier) {
    val anim = rememberAnimationsEnabled()
    val rise by transition.animateFloat(transitionSpec = { if (anim) tween(NATIVE_PAGE_MS + 80, easing = FastOutSlowInEasing) else snap() }, label = "feedRise") {
        if (it == EnterExitState.Visible) 1f else 0f
    }
    val a = ctx.accent
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(discoverPageName(p), color = inkOnDiscover(a), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(a).padding(horizontal = 8.dp, vertical = 3.dp))
            Spacer(Modifier.width(8.dp))
            WinFullText(ctx.name, color = Color.White, fontSize = 14.sp, maxLines = 2, fontWeight = FontWeight.Bold, lineHeight = 17.sp, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        val body = Modifier.weight(1f).fillMaxWidth()
        val pr = { rise }
        when (p) {
            WinPage.DETAILS -> DiscoverAbout(ctx, live, pr, body)
            WinPage.TROPHIES -> DiscoverProgress(ctx, live, pr, body)
            WinPage.OPTIONS -> DiscoverSettings(ctx, live, pr, body)
            else -> ctx.pageFor(p, body, live) // (Performance: the shared page - not a page of this feed yet)
        }
    }
}

@Composable
private fun FeedCard(title: String, a: Color, modifier: Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DISCOVER_CARD_BG).padding(10.dp)) {
        Text(title, color = a, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiscoverAbout(ctx: WinSceneCtx, live: Boolean, rise: () -> Float, modifier: Modifier) {
    val a = ctx.accent
    val focus = nativeListFocus(ctx, live)
    Column(modifier.nativeScroll(a), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NativeItem(onClick = {}, Modifier.fillMaxWidth().feedIn(rise, 0), focus = focus, live = live) { on ->
            FeedCard("THE STORY", a, if (on) Modifier.border(2.dp, a, RoundedCornerShape(12.dp)) else Modifier) {
                Text(ctx.summary ?: "No story for this game yet.", color = Color.White.copy(alpha = 0.88f), fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
        val facts = nativeFacts(ctx, setOf("Trophies", "Played", "Last played"))
        if (facts.isNotEmpty()) FeedCard("GOOD TO KNOW", a, Modifier.feedIn(rise, 1)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                facts.forEach { f ->
                    Text(f.label + "  " + f.value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 15.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
        }
        FeedCard("YOUR PLAY", a, Modifier.feedIn(rise, 2)) {
            val lines = listOfNotNull(if (ctx.playtime == null) "Not played yet" else "Played " + ctx.played, ctx.last?.let { "Last played $it" },
                if (ctx.total > 0) "${ctx.earned} of ${ctx.total} achievements" else null)
            lines.forEach { Text(it, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, lineHeight = 17.sp) }
        }
    }
}

@Composable
private fun FeedTrophy(ach: com.shelf.launcher.data.RaAchievementDetail, a: Color, live: Boolean, opened: Boolean, onOpen: () -> Unit, modifier: Modifier) {
    val earned = ach.isUnlocked
    NativeItem(onClick = onOpen, modifier.fillMaxWidth(), live = live) { on ->
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DISCOVER_CARD_BG)
            .border(if (on) 2.dp else 0.dp, if (on) a else Color.Transparent, RoundedCornerShape(12.dp)).padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(Color.Black)) {
                    NativeBadgeMark(ach.title, earned)
                    AsyncImage(com.shelf.launcher.data.RaBadges.url(ach.badgeName), ach.title, contentScale = ContentScale.Crop,
                        colorFilter = if (earned) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
                        alpha = if (earned) 1f else 0.55f, modifier = Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(ach.title ?: "Untitled", color = Color.White.copy(alpha = if (earned) 1f else 0.75f), fontSize = 13.sp,
                        fontWeight = FontWeight.Bold, lineHeight = 16.sp)
                    ach.description?.let { d ->
                        if (opened) Text(d, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, lineHeight = 15.sp)
                        else WinFullText(d, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp)
                    }
                }
                Text(if (earned) "✓" else (ach.points?.let { "$it" } ?: ""), color = if (earned) a else Color.White.copy(alpha = 0.6f), fontSize = 12.sp,
                    fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(50))
                        .background(if (earned) a.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.07f)).padding(horizontal = 7.dp, vertical = 2.dp))
            }
            if (opened) {
                val facts = listOfNotNull(
                    (ach.dateEarnedHardcore ?: ach.dateEarned)?.let { "Earned " + it.take(10) },
                    ach.points?.let { "$it points" },
                    ach.unlockPercent?.let { "%.1f %% of players".format(it) },
                    ach.type?.takeIf { it.isNotBlank() }
                )
                if (facts.isNotEmpty()) Text(facts.joinToString("  ·  "), color = a, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 42.dp, top = 3.dp))
            }
        }
    }
}

@Composable
private fun DiscoverProgress(ctx: WinSceneCtx, live: Boolean, rise: () -> Float, modifier: Modifier) {
    val a = ctx.accent
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    Column(modifier) {
        Row(Modifier.feedIn(rise, 0), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${t.earned} of ${t.total} earned", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f))) {
                    Box(Modifier.fillMaxWidth(if (t.total > 0) t.earned.toFloat() / t.total else 0f).height(6.dp).clip(RoundedCornerShape(50)).background(a))
                }
            }
            Spacer(Modifier.width(8.dp))
            // (682r: kept unbounded here - a short pill beside a filling progress column; capped to half it squeezed "27 of 40
            // earned" onto two lines and the first achievement lost 4 px at x1.3, AchFirstRow RESUME n=40)
            NativeItem(onClick = t.nextOrder, Modifier, focus = focus, live = live) { on ->
                Text(t.order.label, color = if (on) inkOnDiscover(a) else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) a else Color.White.copy(alpha = 0.08f)).padding(horizontal = 10.dp, vertical = 5.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        // (682aq, D389: lazy rows - each row's place in its run worked out once, so a row built later knows its shelf heading)
        val runs = androidx.compose.runtime.remember(t.all) { discoverRuns(t.all.map { it.isUnlocked }) }
        NativeRows(a, Modifier.weight(1f).fillMaxWidth(), 6.dp) {
            // (681f, AchFirstRowRoboTest RESUME: shelves that repeated achievements - next up, recent, then everything - put the
            // list's first one below the fold at x1.3. Each achievement once, in the chosen order; the shelves are the runs.)
            itemsIndexed(t.all, key = { _, it -> it.id }) { n, ach -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val earned = ach.isUnlocked
                val run = runs[n]
                discoverShelf(earned, run)?.let { Text(it, color = a, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(top = 4.dp).feedIn(rise, minOf(n + 1, 6))) }
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    FeedTrophy(ach, a, live, opened, { openId = if (opened) null else ach.id }, Modifier.feedIn(rise, minOf(n + 1, 6)))
                }
            } }
            if (t.all.isEmpty()) item { FeedCard("NOTHING TO TRACK", a, Modifier.feedIn(rise, 1)) {
                Text("This game has no achievements.", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            } }
        }
    }
}

@Composable
private fun DiscoverSettings(ctx: WinSceneCtx, live: Boolean, rise: () -> Float, modifier: Modifier) {
    val a = ctx.accent
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(a), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth().feedIn(rise, minOf(i, 6)),
                focus = if (i == 0) focus else null, live = live) { on ->
                androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                val armed = confirm.armed == i
                val c = if (o.destructive) DISCOVER_RED else a
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(if (on) c.copy(alpha = 0.18f) else DISCOVER_CARD_BG)
                    .border(if (on || o.destructive) 1.5.dp else 0.dp, if (on || o.destructive) c else Color.Transparent, RoundedCornerShape(22.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            WinFullText(o.label, color = if (o.destructive) DISCOVER_RED else Color.White, fontSize = 14.sp, maxLines = 2, fontWeight = FontWeight.Bold, lineHeight = 18.sp)
                            o.sub?.let { WinFullText(it, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp) }
                        }
                        Text("›", color = if (on) c else Color.White.copy(alpha = 0.4f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    // (682v, owner 18 Nov: the confirmation readable and spaced - 12 sp on 16, a 6 dp gap above, room inside its box)
                    if (armed) Text("Press A again to confirm · move away to cancel", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50)).background(DISCOVER_RED).padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }
        if (ctx.options.isEmpty()) Text("No settings for this game.", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
    }
}
