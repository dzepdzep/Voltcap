package com.shelf.launcher.ui

import kotlinx.coroutines.launch
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * P2 batch 5 (682aa, owner 22 Nov: "proceed with Batch 5 autonomously"; audit NATIVE_PAGES_AUDIT.md - the shelf family had its own
 * index cards but the shared rows inside them). Game shelf, Case stacks, Console stack and Coverflow details: the pages are the
 * next index cards of the shelf's catalogue, and now what is written ON them is the catalogue's own:
 *  - Details      = the catalogue card: each fact typed on its ruled line (the field in small capitals in the label's colour, the
 *                   value in the card's ink), the story typed under them.
 *  - Achievements = the card's checklist: a ruled line each, the badge as the stamp at its start (its letter where there is no
 *                   picture), the title, the points as the card's catalogue number at its end; the order on the first line.
 *                   A on a line shows all it asks.
 *  - Options      = the card's entries, a ruled line each with an arrow on the one you're on; one that removes something is
 *                   written in red and asks for A again under it.
 * Each window's own wood / slate / metal, label colour and ink (ShelfFamilyPages' [label] / [ink]) - nothing of another design.
 * The card change (the next card drawn up out of the slot) stays ShelfFamilyPages' own.
 */

private val SHELF_RED = Color(0xFFC0392B)

/** A ruled line under a row, in the card's ink. */
private fun Modifier.ruled(ink: Color): Modifier = drawBehind {
    val y = size.height - 0.5.dp.toPx()
    drawLine(ink.copy(alpha = 0.16f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
}

/** The catalogue's points label - "12 PTS" with no break between them. Pure. */
internal fun shelfPoints(p: Int?): String = "${p ?: 0} PTS"

@Composable
internal fun ShelfCardPage(ctx: WinSceneCtx, p: WinPage, live: Boolean, ink: Color, label: Color, modifier: Modifier) {
    when (p) {
        WinPage.DETAILS -> ShelfCatalogue(ctx, live, ink, label, modifier)
        WinPage.TROPHIES -> ShelfChecklist(ctx, live, ink, label, modifier)
        WinPage.OPTIONS -> ShelfEntries(ctx, live, ink, label, modifier)
        else -> androidx.compose.runtime.CompositionLocalProvider(LocalNativePageHost provides NativePageHost(Color.Transparent, dark = true, showsCover = true, showsName = true)) {
            ctx.pageFor(p, modifier, live)
        }
    }
}

/** A row of the card: the arrow when it's the one you're on, a ruled line under it. */
@Composable
private fun ShelfLine(on: Boolean, ink: Color, label: Color, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 34.dp).ruled(ink).background(if (on) label.copy(alpha = 0.14f) else Color.Transparent)
        .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (on) "▸" else " ", color = label, fontSize = 14.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(14.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun ShelfCatalogue(ctx: WinSceneCtx, live: Boolean, ink: Color, label: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    // (682aq8, D392 - owner 22:43, Coverflow x1.3 on hardware: the story, one D-pad stop taller than the panel, opened with the
    // facts scrolled out above it and its last lines below - ▲ / ▼ moved nothing. On the story ▲ / ▼ now scroll the page while
    // there is more that way, then the press goes on as before.)
    val sc = androidx.compose.foundation.rememberScrollState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Column(modifier.pageScrollCue(sc, ink).verticalScroll(sc)) {
        // (682ag PROPOSAL, owner 23 Nov 13:32 - Coverflow details' reading space, shown to the owner as a comparison first, OFF by
        // default: the facts as one line of values, so the story starts near the top of the same panel - the panel
        // keeps its width, the home's title stays in view. Coverflow only - LocalCoverReading; the shelf family keeps its rules.)
        if (LocalCoverReading.current) {
            // the facts as one line of values (the field names drop: each value says what it is), the story right under it
            WholeWordsText(coverFactsLine(nativeFacts(ctx, setOf("Trophies")).map { it.value }), color = ink.copy(alpha = 0.78f), fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold, lineHeight = 17.sp, modifier = Modifier.fillMaxWidth().ruled(ink).padding(start = 14.dp, top = 4.dp, bottom = 8.dp))
        } else nativeFacts(ctx, setOf("Trophies")).forEach { f ->
            Row(Modifier.fillMaxWidth().ruled(ink).padding(start = 14.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.Top) {
                Text(f.label.uppercase(), color = label, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(96.dp).padding(top = 2.dp))
                WholeWordsText(f.value, color = ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 17.sp, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.heightIn(min = 6.dp))
        NativeItem(onClick = {}, Modifier.fillMaxWidth().onPreviewKeyEvent { e ->
            if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val step = sc.viewportSize * 0.6f
            when (e.key) {
                androidx.compose.ui.input.key.Key.DirectionUp -> if (sc.value > 0) { scope.launch { sc.animateScrollBy(-step) }; true } else false
                androidx.compose.ui.input.key.Key.DirectionDown -> if (sc.value < sc.maxValue) { scope.launch { sc.animateScrollBy(step) }; true } else false
                else -> false
            }
        }, focus = focus, live = live) { on ->
            Column(Modifier.fillMaxWidth().background(if (on) label.copy(alpha = 0.10f) else Color.Transparent).padding(start = 14.dp, top = 4.dp, bottom = 4.dp)) {
                Text("STORY", color = label, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)
                Text(ctx.summary ?: "No story for this game yet.", color = ink.copy(alpha = 0.9f), fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun ShelfChecklist(ctx: WinSceneCtx, live: Boolean, ink: Color, label: Color, modifier: Modifier) {
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    Column(modifier) {
        NativeItem(onClick = t.nextOrder, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            ShelfLine(on, ink, label) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${t.earned} / ${t.total} EARNED", color = ink.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f))
                    Text("ORDER: " + t.order.label.uppercase(), color = if (on) label else ink, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                }
            }
        }
        NativeRows(ink, Modifier.weight(1f).fillMaxWidth()) {
            nativeItems(t.all, { it.id }) { _, ach ->
                val earned = ach.isUnlocked
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { on ->
                        ShelfLine(on, ink, label) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // (the stamp: the badge in a ring of the label's colour; faded until earned)
                                    Box(Modifier.size(30.dp).border(1.5.dp, if (earned) label else ink.copy(alpha = 0.3f), RoundedCornerShape(50)).padding(2.dp)) {
                                        NativeBadge(ach.badgeName, ach.title, earned, label, Modifier.size(26.dp), shape = RoundedCornerShape(50))
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    WholeWordsText(ach.title ?: "Untitled", color = if (earned) ink else ink.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                        lineHeight = 16.sp, modifier = Modifier.weight(1f))
                                    Text(shelfPoints(ach.points), color = if (earned) label else ink.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace, softWrap = false, modifier = Modifier.padding(start = 6.dp))
                                }
                                ach.description?.let { d ->
                                    if (opened) Text(d, color = ink.copy(alpha = 0.9f), fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(start = 38.dp, top = 2.dp))
                                    else WinFullText(d, color = ink.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1, lineHeight = 14.sp, modifier = Modifier.padding(start = 38.dp, top = 1.dp))
                                }
                            }
                        }
                    }
                }
            }
            if (t.all.isEmpty()) item {
            Text("No achievements for this game.", color = ink.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.padding(start = 14.dp, top = 4.dp))
            }
        }
    }
}

@Composable
private fun ShelfEntries(ctx: WinSceneCtx, live: Boolean, ink: Color, label: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(ink)) {
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(), focus = if (i == 0) focus else null, live = live) { on ->
                androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                val armed = confirm.armed == i
                ShelfLine(on, ink, label) {
                    Column {
                        WinFullText(o.label, color = if (o.destructive) SHELF_RED else ink, fontSize = 13.sp, maxLines = 2, fontWeight = FontWeight.Bold, lineHeight = 16.sp)
                        o.sub?.let { WinFullText(it, color = ink.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp) }
                        if (armed) Text("Press A again to confirm  ·  move away to cancel", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(3.dp)).background(SHELF_RED).padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
        }
        if (ctx.options.isEmpty()) Text("No options for this game.", color = ink.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.padding(start = 14.dp, top = 4.dp))
    }
}


/** 682ag: Coverflow details' reading proposal (the facts as one line of values) - provided only by its panel, and only while
 *  [CoverReadingProposal] is on - owner 23 Nov 18:23: option A, ON in the QA branch as a trial (not visual approval). */
internal val LocalCoverReading = androidx.compose.runtime.compositionLocalOf { false }

/** 682ag: the switch for the comparison (review sheets set it; no user setting until the owner approves). */
internal object CoverReadingProposal { val on = androidx.compose.runtime.mutableStateOf(true) }

/** 682ag (pure): the proposal's facts line - each value kept together (no break inside "Team Ico"), the dot held to the value before it (a line never starts with it). */
internal fun coverFactsLine(values: List<String>): String = values.filter { it.isNotBlank() }.joinToString("\u00A0\u00A0·  ") { it.trim().replace(' ', '\u00A0') }
