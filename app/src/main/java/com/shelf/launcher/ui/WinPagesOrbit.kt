package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideIn
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * P2 (734b, class C): Orbit deck's own pages. The window is the disc flown off the orbit, its chrome keys fanned on an arc and
 * the dark glass console with chrome edges, scan lines and spaced glow labels (DISC ... READY, TIME / YEAR readouts in mono,
 * the achievements as numbered track cells). Its pages are the console's own screens, typed in the same readout language:
 *  - Info         = DISC: the console as before (OdConsole).
 *  - Details      = DATA: each fact a readout - the label in the spaced glow caps, the value in bold mono - then the story as
 *                   a LOG panel with a chamfered chrome-thin edge (a D-pad stop).
 *  - Achievements = TRACKS: "TRACKS  N / M" with the lit-cell strip, the order as a chamfered key; then each achievement a track:
 *                   its numbered cell (lit when earned, like the main console's cells), the badge, the title, a mono status line
 *                   "UNLOCKED <date>" / "LOCKED" and "+N PTS"; A opens the whole text and how rare it is.
 *  - Options      = COMMANDS: each option a command line "▸ LABEL" in mono (lit in the orbit's colour); one that removes
 *                   something is amber-red and asks for a second A (NativeConfirm).
 * Heading: the page's code in the glow caps, a chamfered cell per page (the shown one lit) and the breathing READY lamp - after
 * the old page has gone. Motion: the next screen rolls up like a terminal (from below going forward, from above going back);
 * none with Motion Off. The arc keys and the CMD line stay where they are.
 * Shared: data, focus, B, Play-first, inert outgoing copy (NativePageKit).
 */

/** The console's code for a page. Pure. */
internal fun orbitPageName(p: WinPage): String = when (p) {
    WinPage.MAIN -> "DISC"
    WinPage.DETAILS -> "DATA"
    WinPage.TROPHIES -> "TRACKS"
    WinPage.OPTIONS -> "COMMANDS"
    WinPage.PERF -> "PERFORMANCE"
}

/** A track's number in its cell: "01".."99", then the plain number. Pure. */
internal fun orbitTrackNo(i: Int): String = if (i in 0..98) "%02d".format(i + 1) else "${i + 1}"

/** A track's status line: "UNLOCKED <date>" / "UNLOCKED" when yours, "LOCKED" still to get. Pure. */
internal fun orbitTrackLine(earned: Boolean, date: String?): String =
    if (!earned) "LOCKED" else date?.takeIf { it.isNotBlank() }?.let { "UNLOCKED ${it.trim()}" } ?: "UNLOCKED"

/** The count beside TRACKS: "N / M" (earned never above the total). Pure. */
internal fun orbitTrackCount(earned: Int, total: Int): String = "${earned.coerceIn(0, maxOf(total, 0))} / ${maxOf(total, 0)}"

/** The lit cells of the strip over the tracks: at most [cells], in proportion beyond. Pure. */
internal fun orbitLitCells(earned: Int, total: Int, cells: Int = 24): Int = when {
    total <= 0 -> 0
    total <= cells -> earned.coerceIn(0, total)
    else -> kotlin.math.round(earned.coerceIn(0, total).toFloat() / total * cells).toInt()
}

internal fun orbitMove(dir: Int): ContentTransform =
    (fadeIn(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) +
        slideIn(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) { s -> IntOffset(0, if (dir >= 0) s.height / 8 else -s.height / 8) }) togetherWith
        fadeOut(tween(NATIVE_OUT_MS))

private val OdInk = Color(0xFFEAF2FF)
private val OdMuted = Color(0xFF9FB0C8)
private val OdDim = Color(0xFF6E7E96)
private val OdRed = Color(0xFFFF6B5A)
private val OdCell = Brush.verticalGradient(listOf(Color(0xFF1C2433), Color(0xFF0B0F18)))
private val OdCut = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)

private fun odLabel(glow: Color) = TextStyle(color = glow, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, lineHeight = 13.sp)

/** The heading row: the page's code in the glow caps, a chamfered cell per page (the shown one lit), the READY lamp. */
@Composable
internal fun OrbitPageHeading(tabs: List<WinPage>, shown: WinPage, glow: Color, lamp: () -> Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(orbitPageName(shown), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, maxLines = 1,
            softWrap = false, style = TextStyle(shadow = Shadow(glow.copy(alpha = 0.8f), Offset.Zero, 10f)))
        Spacer(Modifier.width(10.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEach { t ->
                val on = t == shown
                Box(Modifier.size(if (on) 20.dp else 10.dp, 8.dp).background(if (on) Brush.verticalGradient(listOf(Color.White, glow)) else OdCell,
                    CutCornerShape(topStart = 3.dp, bottomEnd = 3.dp))
                    .border(1.dp, if (on) Color.White.copy(alpha = 0.8f) else glow.copy(alpha = 0.3f), CutCornerShape(topStart = 3.dp, bottomEnd = 3.dp)))
            }
        }
        androidx.compose.foundation.Canvas(Modifier.size(8.dp)) { drawCircle(glow.copy(alpha = 0.55f + 0.45f * lamp())) }
        Spacer(Modifier.width(5.dp))
        Text("READY", style = odLabel(glow), maxLines = 1, softWrap = false)
    }
    Box(Modifier.padding(vertical = 6.dp).fillMaxWidth().height(1.5.dp).background(Brush.horizontalGradient(listOf(glow, glow.copy(alpha = 0.05f)))))
}

/** One of the console's screens (DISC is the console itself, drawn by OdConsole). */
@Composable
internal fun AnimatedVisibilityScope.OrbitPage(ctx: WinSceneCtx, p: WinPage, live: Boolean, glow: Color, modifier: Modifier) {
    when (p) {
        WinPage.DETAILS -> OrbitData(ctx, live, glow, modifier)
        WinPage.TROPHIES -> OrbitTracks(ctx, live, glow, modifier)
        WinPage.OPTIONS -> OrbitCommands(ctx, live, glow, modifier)
        WinPage.MAIN -> {}
        else -> ctx.pageFor(p, modifier, live) // (Performance: the shared page - not on the console yet)
    }
}

@Composable
private fun OrbitData(ctx: WinSceneCtx, live: Boolean, glow: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val label = odLabel(glow)
    Column(modifier.nativeScroll(glow), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        nativeFacts(ctx).forEach { f ->
            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.Top) {
                WholeWordsText(f.label.uppercase(), color = glow, fontSize = 10.sp, fontWeight = FontWeight.Bold, lineHeight = 13.sp,
                    modifier = Modifier.width(96.dp).padding(top = 3.dp))
                Spacer(Modifier.width(8.dp))
                WholeWordsText(f.value, color = OdInk, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                    lineHeight = 17.sp, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(4.dp))
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            Column(Modifier.fillMaxWidth().background(if (on) glow.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.03f), OdCut)
                .border(if (on) 2.dp else 1.dp, if (on) glow else glow.copy(alpha = 0.3f), OdCut)
                .padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text("LOG", style = label)
                Text(ctx.summary ?: "No story for this game yet.", color = Color(0xFFC5D0E0), fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
private fun OrbitTracks(ctx: WinSceneCtx, live: Boolean, glow: Color, modifier: Modifier) {
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    val label = odLabel(glow)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("TRACKS", style = label, maxLines = 1, softWrap = false)
            Spacer(Modifier.width(8.dp))
            Text(orbitTrackCount(t.earned, t.total), color = OdInk, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
            // (the order key gets at most its share - 682r)
            NativeItem(onClick = t.nextOrder, Modifier.weight(1f, fill = false), focus = focus, live = live) { on ->
                Text("ORDER ▸ " + t.order.label.uppercase(), color = if (on) Color(0xFF07132A) else OdInk, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold, modifier = Modifier
                        .background(if (on) Brush.verticalGradient(listOf(Color.White, glow)) else OdCell, OdCut)
                        .border(1.dp, if (on) Color.White.copy(alpha = 0.8f) else glow.copy(alpha = 0.35f), OdCut)
                        .padding(horizontal = 10.dp, vertical = 5.dp))
            }
        }
        // The lit-cell strip, as the DISC screen draws the tracks.
        if (t.total > 0) {
            val cells = minOf(t.total, 24)
            val lit = orbitLitCells(t.earned, t.total, cells)
            Row(Modifier.fillMaxWidth().padding(top = 5.dp).height(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (i in 0 until cells) Box(Modifier.weight(1f).height(6.dp)
                    .background(if (i < lit) Brush.verticalGradient(listOf(Color.White, glow)) else OdCell, RoundedCornerShape(1.dp)))
            }
        }
        Spacer(Modifier.height(5.dp))
        NativeRows(glow, Modifier.weight(1f).fillMaxWidth(), spacing = 3.dp) {
            nativeItems(t.all, { it.id }) { i, ach ->
                val earned = ach.isUnlocked
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { on ->
                        Column(Modifier.fillMaxWidth()
                            .background(if (on) glow.copy(alpha = 0.12f) else Color.White.copy(alpha = if (earned) 0.05f else 0.02f), OdCut)
                            .border(if (on) 2.dp else 1.dp, if (on) glow else glow.copy(alpha = 0.18f), OdCut)
                            .padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // The track's numbered cell - lit when earned.
                                Box(Modifier.size(28.dp, 18.dp)
                                    .background(if (earned) Brush.verticalGradient(listOf(Color.White, glow)) else OdCell, RoundedCornerShape(4.dp))
                                    .border(1.dp, if (earned) Color.White.copy(alpha = 0.8f) else glow.copy(alpha = 0.25f), RoundedCornerShape(4.dp)),
                                    contentAlignment = Alignment.Center) {
                                    Text(orbitTrackNo(i), color = if (earned) Color(0xFF07132A) else OdDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                }
                                Spacer(Modifier.width(8.dp))
                                NativeBadge(ach.badgeName, ach.title, earned, glow, Modifier.size(28.dp), shape = CutCornerShape(topStart = 5.dp, bottomEnd = 5.dp))
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    WholeWordsText(ach.title ?: "Untitled", color = if (earned) Color.White else Color.White.copy(alpha = 0.72f),
                                        fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp)
                                    Text(orbitTrackLine(earned, ach.dateEarned), color = if (earned) glow else OdDim, fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, lineHeight = 13.sp)
                                }
                                ach.points?.let { pts ->
                                    Text("+$pts PTS", color = if (earned) OdInk else OdDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold, softWrap = false, modifier = Modifier.padding(start = 6.dp))
                                }
                            }
                            ach.description?.let { d ->
                                if (opened) Text(d, color = Color(0xFFC5D0E0), fontSize = 12.sp, lineHeight = 16.sp,
                                    modifier = Modifier.padding(start = 72.dp, top = 3.dp))
                                else WinFullText(d, color = OdMuted, fontSize = 11.sp, maxLines = 1, lineHeight = 14.sp,
                                    modifier = Modifier.padding(start = 72.dp, top = 1.dp))
                            }
                            if (opened) ach.unlockPercent?.let { pc ->
                                Text("RARITY %.1f %% OF PLAYERS".format(pc), color = glow, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 72.dp, top = 1.dp))
                            }
                        }
                    }
                }
            }
            if (t.all.isEmpty()) item {
                Text("NO TRACKS ON THIS GAME YET.", color = OdMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun OrbitCommands(ctx: WinSceneCtx, live: Boolean, glow: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(glow), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(),
                focus = if (i == 0) focus else null, live = live) { on ->
                androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                val armed = confirm.armed == i
                val tone = if (o.destructive) OdRed else glow
                val ink = when { on -> Color(0xFF07132A); o.destructive -> Color(0xFFFFB3AC); else -> OdInk }
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth()
                        .background(if (on) Brush.horizontalGradient(listOf(Color.White, tone)) else Brush.horizontalGradient(listOf(tone.copy(alpha = 0.08f), Color.Transparent)), OdCut)
                        .border(if (on) 2.dp else 1.dp, if (on) Color.White.copy(alpha = 0.8f) else tone.copy(alpha = 0.35f), OdCut)
                        .padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.Top) {
                        Text("▸", color = if (on) ink else tone, fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            WinFullText(o.label.uppercase(), color = ink, fontSize = 13.sp, maxLines = 2, fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace, lineHeight = 17.sp)
                            o.sub?.let { WinFullText(it, color = ink.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp) }
                        }
                    }
                    if (armed) Text("PRESS A AGAIN TO REMOVE  ·  MOVE AWAY TO CANCEL", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold, lineHeight = 15.sp, modifier = Modifier.fillMaxWidth().padding(top = 3.dp)
                            .background(OdRed.copy(alpha = 0.75f), OdCut).padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
        }
        if (ctx.options.isEmpty()) Text("NO COMMANDS FOR THIS GAME.", color = OdMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

/** The console's page area: the heading, then the page's screen, rolled in by [orbitMove]. */
@Composable
internal fun OrbitPageArea(ctx: WinSceneCtx, glow: Color, lamp: () -> Float, modifier: Modifier) {
    val heading = rememberHeadingPage(ctx.page)
    Column(modifier) {
        OrbitPageHeading(nativeTabs(ctx.pages), if (heading == WinPage.MAIN) ctx.page else heading, glow, lamp)
        NativePages(ctx, Modifier.fillMaxWidth().weight(1f), label = "orbitPage", move = ::orbitMove) { pg, pageLive ->
            OrbitPage(ctx, pg, pageLive, glow, Modifier.fillMaxSize())
        }
    }
}

