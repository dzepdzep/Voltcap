package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * P2 (726a, class C): the Circuit board window's own pages. The window is the chip's datasheet printed on the board (the
 * board / trace / silkscreen colours of Layout options > Board colours, circuitSheetColors), so its pages are the sheet's
 * sections, in its monospace silkscreen:
 *  - Info         = PINOUT: the facts numbered like the chip's pins (ChipSheet, unchanged).
 *  - Details      = CHARACTERISTICS: a PARAMETER / VALUE table between rules (values in whole words), the story as the
 *                   FUNCTIONAL DESCRIPTION block (a D-pad stop that scrolls).
 *  - Achievements = PIN STATES: each achievement a numbered pin - an LED lit in the trace colour and HIGH when you have it,
 *                   hollow and LOW when not, its points in "PT" on the right; "N / M PINS HIGH" and the order as a key;
 *                   A opens its whole text and when / how rare.
 *  - Options      = JUMPERS: each option a jumper J1, J2 ... - the cap bridges its two pins while it has the focus; one that
 *                   removes something is printed red and asks for a second A (NativeConfirm).
 * Motion: the section's signal runs along the trace - the new section slides in a little from the trace side (from the other
 * side going back) as it fades up; none with Motion Off. Shared: data, focus, B, Play-first, inert outgoing copy (NativePageKit).
 */

/** The datasheet's section name for a page. Pure. */
internal fun chipPageName(p: WinPage): String = when (p) {
    WinPage.MAIN -> "PINOUT"
    WinPage.DETAILS -> "CHARACTERISTICS"
    WinPage.TROPHIES -> "PIN STATES"
    WinPage.OPTIONS -> "JUMPERS"
    WinPage.PERF -> "PERFORMANCE"
}

/** The section's number on the sheet (1 = Info). Pure. */
internal fun chipSectionNo(p: WinPage): Int = when (p) {
    WinPage.MAIN -> 1; WinPage.DETAILS -> 2; WinPage.TROPHIES -> 3; WinPage.OPTIONS -> 4; WinPage.PERF -> 5
}

/** A pin's number for the [i]-th achievement (0-based): P01 .. P99, then P100 .... Pure. */
internal fun chipPin(i: Int): String = "P" + (i + 1).toString().padStart(2, '0')

/** A pin's logic state: HIGH when the achievement is yours, LOW when it is still to get. Pure. */
internal fun chipPinState(earned: Boolean): String = if (earned) "HIGH" else "LOW"

/** An achievement's points on its pin: "N PT", or "— PT" with none. Pure. */
internal fun chipPoints(points: Int?): String = (points?.toString() ?: "—") + " PT"

/** A jumper's name for the [i]-th option (0-based). Pure. */
internal fun chipJumper(i: Int): String = "J${i + 1}"

internal fun chipMove(dir: Int): ContentTransform =
    (fadeIn(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) +
        slideInHorizontally(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) { w -> if (dir >= 0) -w / 10 else w / 10 }) togetherWith
        fadeOut(tween(NATIVE_OUT_MS))

private val CHIP_RED = Color(0xFFE5483B)
private val CHIP_MONO = FontFamily.Monospace

/** One of the datasheet's sections (Info is the sheet's own pinout, drawn by ChipSheet). */
@Composable
internal fun AnimatedVisibilityScope.ChipPage(ctx: WinSceneCtx, p: WinPage, live: Boolean, pcb: Color, trace: Color, silk: Color, modifier: Modifier) {
    when (p) {
        WinPage.DETAILS -> ChipCharacteristics(ctx, live, trace, silk, modifier)
        WinPage.TROPHIES -> ChipPinStates(ctx, live, pcb, trace, silk, modifier)
        WinPage.OPTIONS -> ChipJumpers(ctx, live, trace, silk, modifier)
        WinPage.MAIN -> {}
        else -> ctx.pageFor(p, modifier, live) // (Performance: the shared page - not a section of this sheet yet)
    }
}

@Composable
private fun ChipRule(trace: Color) = Spacer(Modifier.fillMaxWidth().height(1.dp).background(trace.copy(alpha = 0.45f)))

@Composable
private fun ChipCharacteristics(ctx: WinSceneCtx, live: Boolean, trace: Color, silk: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    Column(modifier.nativeScroll(trace)) {
        ChipRule(trace)
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text("PARAMETER", color = trace, fontSize = 10.sp, fontFamily = CHIP_MONO, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.width(118.dp))
            Text("VALUE", color = trace, fontSize = 10.sp, fontFamily = CHIP_MONO, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
        }
        ChipRule(trace)
        nativeFacts(ctx, setOf("Trophies")).forEach { f ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(f.label.uppercase(), color = silk.copy(alpha = 0.65f), fontSize = 10.sp, fontFamily = CHIP_MONO, lineHeight = 14.sp, modifier = Modifier.width(118.dp))
                WholeWordsText(f.value, color = silk, fontSize = 12.sp, fontFamily = CHIP_MONO, lineHeight = 15.sp, modifier = Modifier.weight(1f))
            }
        }
        ChipRule(trace)
        Spacer(Modifier.height(6.dp))
        Text("FUNCTIONAL DESCRIPTION", color = trace, fontSize = 10.sp, fontFamily = CHIP_MONO, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(3.dp))
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            Text(ctx.summary ?: "No description on this datasheet yet.", color = silk.copy(alpha = 0.88f), fontSize = 12.sp, lineHeight = 17.sp, fontFamily = CHIP_MONO,
                modifier = Modifier.fillMaxWidth().border(if (on) 2.dp else 1.dp, if (on) trace else trace.copy(alpha = 0.3f), CutCornerShape(4.dp)).padding(8.dp))
        }
    }
}

@Composable
private fun ChipPinStates(ctx: WinSceneCtx, live: Boolean, pcb: Color, trace: Color, silk: Color, modifier: Modifier) {
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${t.earned} / ${t.total} PINS HIGH", color = silk.copy(alpha = 0.75f), fontSize = 10.sp, fontFamily = CHIP_MONO, fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp, modifier = Modifier.weight(1f))
            // (the order key gets at most its share, as the Classic panel's - 682r: unbounded it took the row)
            NativeItem(onClick = t.nextOrder, Modifier.weight(1f, fill = false), focus = focus, live = live) { on ->
                Text("ORDER " + t.order.label.uppercase(), color = if (on) pcb else trace, fontSize = 10.sp, fontFamily = CHIP_MONO, fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp, modifier = Modifier.clip(CutCornerShape(3.dp)).background(if (on) trace else Color.Transparent)
                        .border(1.dp, trace, CutCornerShape(3.dp)).padding(horizontal = 6.dp, vertical = 3.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        ChipRule(trace)
        NativeRows(trace, Modifier.weight(1f).fillMaxWidth()) {
            nativeItems(t.all, { it.id }) { i, ach ->
                val earned = ach.isUnlocked
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { on ->
                        Column(Modifier.fillMaxWidth().clip(CutCornerShape(3.dp)).background(if (on) trace.copy(alpha = 0.16f) else Color.Transparent)
                            .then(if (on) Modifier.border(1.dp, trace, CutCornerShape(3.dp)) else Modifier).padding(horizontal = 4.dp, vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(chipPin(i), color = trace, fontSize = 10.sp, fontFamily = CHIP_MONO, softWrap = false, modifier = Modifier.width(40.dp))
                                // the pin's LED: lit in the trace colour when HIGH, a hollow ring when LOW
                                Canvas(Modifier.size(10.dp)) {
                                    if (earned) drawCircle(trace) else drawCircle(trace.copy(alpha = 0.6f), style = Stroke(1.5.dp.toPx()))
                                }
                                Spacer(Modifier.width(6.dp))
                                NativeBadge(ach.badgeName, ach.title, earned, trace, Modifier.size(24.dp), shape = CircleShape)
                                Spacer(Modifier.width(8.dp))
                                WholeWordsText(ach.title ?: "Untitled", color = if (earned) silk else silk.copy(alpha = 0.55f), fontSize = 12.sp, fontFamily = CHIP_MONO,
                                    fontWeight = FontWeight.Bold, lineHeight = 15.sp, modifier = Modifier.weight(1f))
                                Column(Modifier.padding(start = 6.dp), horizontalAlignment = Alignment.End) {
                                    Text(chipPinState(earned), color = if (earned) trace else silk.copy(alpha = 0.5f), fontSize = 10.sp, fontFamily = CHIP_MONO,
                                        fontWeight = FontWeight.Bold, softWrap = false)
                                    Text(chipPoints(ach.points), color = silk.copy(alpha = if (earned) 0.85f else 0.5f), fontSize = 10.sp, fontFamily = CHIP_MONO, softWrap = false)
                                }
                            }
                            ach.description?.let { d ->
                                if (opened) Text(d, color = silk.copy(alpha = 0.85f), fontSize = 11.sp, lineHeight = 15.sp, fontFamily = CHIP_MONO,
                                    modifier = Modifier.padding(start = 56.dp, top = 2.dp))
                                else WinFullText(d, color = silk.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1, lineHeight = 14.sp, modifier = Modifier.padding(start = 56.dp, top = 1.dp))
                            }
                            if (opened) listOfNotNull(ach.dateEarned?.let { "LATCHED  $it" }, ach.unlockPercent?.let { "YIELD  %.1f %% of players".format(it) }).forEach { l ->
                                Text(l, color = trace.copy(alpha = 0.85f), fontSize = 10.sp, fontFamily = CHIP_MONO, modifier = Modifier.padding(start = 56.dp, top = 1.dp))
                            }
                        }
                    }
                    if (i < t.all.size - 1) Spacer(Modifier.fillMaxWidth().height(1.dp).background(trace.copy(alpha = 0.14f)))
                }
            }
            if (t.all.isEmpty()) item {
                Text("No pins on this chip.", color = silk.copy(alpha = 0.6f), fontSize = 12.sp, fontFamily = CHIP_MONO, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun ChipJumpers(ctx: WinSceneCtx, live: Boolean, trace: Color, silk: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(trace), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(), focus = if (i == 0) focus else null, live = live) { on ->
                androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                val armed = confirm.armed == i
                val ink = if (o.destructive) CHIP_RED else trace
                Column(Modifier.fillMaxWidth().clip(CutCornerShape(3.dp)).background(if (on) ink.copy(alpha = 0.14f) else Color.Transparent)
                    .then(if (on) Modifier.border(1.dp, ink, CutCornerShape(3.dp)) else Modifier).padding(horizontal = 6.dp, vertical = 5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(chipJumper(i), color = ink, fontSize = 10.sp, fontFamily = CHIP_MONO, fontWeight = FontWeight.Bold, softWrap = false, modifier = Modifier.width(30.dp))
                        // the jumper: two header pins; the cap bridges them while the D-pad is on it
                        Canvas(Modifier.width(22.dp).height(12.dp)) {
                            val r = 2.dp.toPx(); val y = size.height / 2
                            drawCircle(ink, r, Offset(size.width * 0.25f, y)); drawCircle(ink, r, Offset(size.width * 0.75f, y))
                            if (on) drawRect(ink.copy(alpha = 0.85f), Offset(size.width * 0.1f, y - 4.dp.toPx()), Size(size.width * 0.8f, 8.dp.toPx()))
                        }
                        Spacer(Modifier.width(8.dp))
                        WinFullText(o.label, color = if (o.destructive) CHIP_RED else silk, fontSize = 13.sp, maxLines = 2, fontWeight = FontWeight.Bold, lineHeight = 16.sp,
                            modifier = Modifier.weight(1f))
                    }
                    o.sub?.let { WinFullText(it, color = silk.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp, modifier = Modifier.padding(start = 60.dp)) }
                    if (armed) Text("Press A again to confirm  ·  move away to cancel", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clip(CutCornerShape(3.dp)).background(CHIP_RED).padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }
        if (ctx.options.isEmpty()) Text("No jumpers on this board.", color = silk.copy(alpha = 0.6f), fontSize = 12.sp, fontFamily = CHIP_MONO)
    }
}
