package com.shelf.launcher.ui

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * P2 batch 3 (682w): the Card binder window's own pages. The window is the game's collectible card, turned over out of the
 * binder - the coloured frame, the cream face, the title bar with its count, the picture window, the pill keys. Its pages
 * are the printed parts of such a card's text box (an original card design, no game's card rules):
 *  - Details      = CARD TEXT: the facts as the card's stat line (bold label, value), the story as the italic flavour text
 *                   in its own framed box (a D-pad stop that scrolls).
 *  - Achievements = MOVES: each achievement a move - its badge as the cost symbol, its name bold, its points as the move's
 *                   number on the right; earned moves in ink, the rest faded; the order as a "SET ORDER" line; A opens its text.
 *  - Options      = RULES: the card's rule box - each option a rule line; one that removes something is red and asks for A again.
 * Motion: the text box is dealt - the new section rises a little into the box, the old one fades.
 */

internal fun cardSection(p: WinPage): String = when (p) {
    WinPage.MAIN -> "CARD"
    WinPage.DETAILS -> "CARD TEXT"
    WinPage.TROPHIES -> "MOVES"
    WinPage.OPTIONS -> "RULES"
    WinPage.PERF -> "PERFORMANCE"
}

/** A move's number on the right: its points, or "—". Pure. */
internal fun cardMoveValue(points: Int?): String = points?.toString() ?: "—"

internal fun cardMove(@Suppress("UNUSED_PARAMETER") dir: Int): ContentTransform =
    (fadeIn(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) + slideInVertically(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) { h -> h / 12 }) togetherWith
        fadeOut(tween(120))

private val CARD_RED = Color(0xFFB0302A)

@Composable
internal fun AnimatedVisibilityScope.CardPage(ctx: WinSceneCtx, p: WinPage, live: Boolean, ink: Color, frame: Color, star: Color, modifier: Modifier) {
    when (p) {
        WinPage.DETAILS -> CardText(ctx, live, ink, frame, modifier)
        WinPage.TROPHIES -> CardMoves(ctx, live, ink, frame, star, modifier)
        WinPage.OPTIONS -> CardRules(ctx, live, ink, frame, modifier)
        WinPage.MAIN -> {}
        else -> ctx.pageFor(p, modifier, live)
    }
}

@Composable
private fun CardText(ctx: WinSceneCtx, live: Boolean, ink: Color, frame: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    Column(modifier.nativeScroll(ink)) {
        nativeFacts(ctx, setOf("Trophies")).forEach { f ->
            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                Text(f.label.uppercase(), color = ink, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.width(84.dp))
                WholeWordsText(f.value, color = ink, fontSize = 12.sp, lineHeight = 15.sp, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(6.dp))
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            Text(ctx.summary ?: "No flavour text for this card yet.", color = ink.copy(alpha = 0.85f), fontSize = 12.sp, lineHeight = 16.sp, fontStyle = FontStyle.Italic,
                modifier = Modifier.fillMaxWidth().border(if (on) 2.dp else 1.dp, if (on) ink else frame, RoundedCornerShape(4.dp)).padding(8.dp))
        }
    }
}

@Composable
private fun CardMoves(ctx: WinSceneCtx, live: Boolean, ink: Color, frame: Color, star: Color, modifier: Modifier) {
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${t.earned} / ${t.total} MOVES LEARNED", color = ink.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp,
                modifier = Modifier.weight(1f))
            NativeItem(onClick = t.nextOrder, Modifier.weight(1f, fill = false), focus = focus, live = live) { on ->
                Text(t.order.label.uppercase(), color = if (on) star else ink, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Color(0xFF2A2620) else Color.Transparent)
                        .border(1.dp, ink.copy(alpha = 0.5f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        NativeRows(ink, Modifier.weight(1f).fillMaxWidth()) {
            itemsIndexed(t.all, key = { _, it -> it.id }) { i, ach ->
                val earned = ach.isUnlocked
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { on ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(if (on) frame.copy(alpha = 0.25f) else Color.Transparent)
                            .then(if (on) Modifier.border(1.dp, ink, RoundedCornerShape(4.dp)) else Modifier).padding(horizontal = 4.dp, vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                NativeBadge(ach.badgeName, ach.title, earned, frame, Modifier.size(24.dp), shape = CircleShape)
                                Spacer(Modifier.width(8.dp))
                                WholeWordsText(ach.title ?: "Untitled", color = if (earned) ink else ink.copy(alpha = 0.5f), fontSize = 13.sp, fontWeight = FontWeight.Black,
                                    lineHeight = 16.sp, modifier = Modifier.weight(1f))
                                Text(cardMoveValue(ach.points), color = if (earned) ink else ink.copy(alpha = 0.5f), fontSize = 16.sp, fontWeight = FontWeight.Black,
                                    softWrap = false, modifier = Modifier.padding(start = 6.dp))
                            }
                            ach.description?.let { d ->
                                if (opened) Text(d, color = ink.copy(alpha = 0.85f), fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(start = 32.dp, top = 2.dp))
                                else WinFullText(d, color = ink.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1, lineHeight = 14.sp, modifier = Modifier.padding(start = 32.dp, top = 1.dp))
                            }
                        }
                    }
                    if (i < t.all.size - 1) Spacer(Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.12f)))
                }
            }
            if (t.all.isEmpty()) item {
            Text("This card has no moves.", color = ink.copy(alpha = 0.6f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun CardRules(ctx: WinSceneCtx, live: Boolean, ink: Color, frame: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(ink).border(1.dp, frame, RoundedCornerShape(4.dp)).padding(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(), focus = if (i == 0) focus else null, live = live) { on ->
                androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                val armed = confirm.armed == i
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(if (on) frame.copy(alpha = 0.25f) else Color.Transparent)
                    .then(if (on) Modifier.border(1.dp, if (o.destructive) CARD_RED else ink, RoundedCornerShape(4.dp)) else Modifier).padding(horizontal = 6.dp, vertical = 5.dp)) {
                    WinFullText("◆ " + o.label, color = if (o.destructive) CARD_RED else ink, fontSize = 13.sp, maxLines = 2, fontWeight = FontWeight.Bold, lineHeight = 16.sp)
                    o.sub?.let { WinFullText(it, color = ink.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp, modifier = Modifier.padding(start = 14.dp)) }
                    if (armed) Text("Press A again to confirm  ·  move away to cancel", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(4.dp)).background(CARD_RED).padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }
        if (ctx.options.isEmpty()) Text("No rules on this card.", color = ink.copy(alpha = 0.6f), fontSize = 12.sp)
    }
}
