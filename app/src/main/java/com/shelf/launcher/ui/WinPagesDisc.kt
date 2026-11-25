package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * P2 (682az, the 53rd layout): Disc station's own pages. The window is the deck's front panel beside the player: smoked metal, the
 * NOW LOADED tray line, the LCD and the transport keys. Its pages are the disc's tracks (673f): L1 / R1 skip a track, the LCD
 * scrolls the old track off and the next one up (forward up, back down) and grows to it. Until now each track played the
 * shared page bodies on the LCD; now each is the deck's own readout, in the LCD's segment-monospace and its one lit colour:
 *  - Info         = track 01, the deck as before.
 *  - Details      = DISC INFO: the disc's CD-text - each fact a readout line (the label in a small lit-edge segment box, the value
 *                   beside it), then the story as LINER NOTES in a framed well with a ▶ mark (a D-pad stop).
 *  - Achievements = PROGRAM: the deck's program list - a line with "PROG", "N / M", a level meter of 16 bars (one earned always
 *                   lights one, all only when every one is earned) and the order as the deck's MODE key in its own short words
 *                   (on its own line under them when the line is too narrow - 682bb); each achievement a
 *                   track: its number "01", ▶ when yours / "--" when not, the title, "PLAYED <date>" / "NOT PLAYED", the points
 *                   as "+N"; the track you are on shown inverted on the LCD; A opens the whole text and how rare it is.
 *  - Options      = SETUP: the deck's menu - each option a line led by its key mark (a square), one that removes something led
 *                   by the eject mark (a triangle over a bar, drawn - not a font glyph) in the warning red and asking for a second A ("PRESS A AGAIN TO CONFIRM", NativeConfirm).
 * Heading: the LCD's track line stays (DISC 1 · TRACK 02 and the page's name); under it the deck's music calendar - a numbered
 * cell per track, the one playing lit. Motion: the deck's own track change (DiscPlayerPanel). The keys stay under the LCD with
 * the Info page's slots and on the tracks the panel grows upward only (livePageHeight, as Live tiles), so Play stays put.
 * Shared: data, focus, B, Play-first, inert outgoing copy (NativePageKit).
 */

/** The deck's name for a page. Pure. */
internal fun discPageName(p: WinPage): String = when (p) {
    WinPage.MAIN -> "Now loaded"
    WinPage.DETAILS -> "Disc info"
    WinPage.TROPHIES -> "Program"
    WinPage.OPTIONS -> "Setup"
    WinPage.PERF -> "Performance"
}

/** The count beside PROG: "N / M" (earned never above the total). Pure. */
internal fun discAwardCount(earned: Int, total: Int): String = "${earned.coerceIn(0, maxOf(total, 0))} / ${maxOf(total, 0)}"

/** A track's number on the program list: "01" ... "99", "100" past that. Pure. */
internal fun discTrackNo(i: Int): String = (i + 1).coerceAtLeast(1).toString().padStart(2, '0')

/** An achievement's status on the program list: "PLAYED <date>" / "PLAYED" when yours, "NOT PLAYED" otherwise. Pure. */
internal fun discAwardLine(earned: Boolean, date: String?): String =
    if (!earned) "NOT PLAYED" else date?.takeIf { it.isNotBlank() }?.let { "PLAYED ${discDay(it)}" } ?: "PLAYED"

/** The day of an earned date ("2026-11-01 10:00:00" -> "2026-11-01"); any other text whole, as given. Pure. */
internal fun discDay(date: String): String = date.trim().let { d -> Regex("""^(\d{4}-\d{2}-\d{2})[ T].*""").find(d)?.groupValues?.get(1) ?: d }

/** How many of the level meter's [bars] are lit: one earned always lights one, all only when every one is earned. Pure. */
internal fun discLevel(earned: Int, total: Int, bars: Int): Int {
    if (total <= 0 || bars <= 0) return 0
    val e = earned.coerceIn(0, total)
    if (e == 0) return 0
    if (e == total) return bars
    return (e * bars / total).coerceIn(1, bars - 1)
}

internal const val DISC_LEVEL_BARS = 16
/** 682bb: the level meter's least / most width on the PROG line. */
internal val DISC_METER_MIN = 40.dp
internal val DISC_METER_MAX = 96.dp

/** 682bb (pure): the MODE key shares the PROG line only when it fits whole beside the count and a meter of [minMeter] (px). */
internal fun discModeOneLine(countW: Int, keyW: Int, maxW: Int, gap: Int, minMeter: Int): Boolean = countW + gap + minMeter + gap + keyW <= maxW

/** 682bb (pure): the deck's own word for the list's order - short, so the MODE key reads at once. */
internal fun discModeWord(o: com.shelf.launcher.data.Features.TrophyOrder): String = when (o) {
    com.shelf.launcher.data.Features.TrophyOrder.SET -> "IN ORDER"
    com.shelf.launcher.data.Features.TrophyOrder.TO_GET -> "TO GET FIRST"
    com.shelf.launcher.data.Features.TrophyOrder.EARNED -> "EARNED FIRST"
    com.shelf.launcher.data.Features.TrophyOrder.LATEST -> "LATEST FIRST"
    com.shelf.launcher.data.Features.TrophyOrder.POINTS -> "TOP POINTS"
    com.shelf.launcher.data.Features.TrophyOrder.RARE -> "RAREST FIRST"
}

private val DiscWarn = Color(0xFFFF6A4D)
private val DiscCell = RoundedCornerShape(2.dp)
private val DiscWell = RoundedCornerShape(4.dp)

/** The LCD's ink set for its pages (from the deck's lit colour on the dark glass). */
internal class DiscInk(val lcd: Color, val glass: Color) {
    val label = lcd.copy(alpha = 0.6f)
    val muted = lcd.copy(alpha = 0.48f)
    val tile = lcd.copy(alpha = 0.06f)
    val line = lcd.copy(alpha = 0.26f)
    /** The track the D-pad is on: the LCD inverted - its lit colour, written on in the glass. */
    val lit = lcd
    val onLit = glass
}

/** The deck's music calendar: a numbered cell per track ([tracks]), the one playing ([shown]) lit. */
@Composable
internal fun DiscCalendar(tracks: List<WinPage>, shown: WinPage, k: DiscInk) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        tracks.forEachIndexed { i, t ->
            val on = t == shown
            Box(Modifier.widthIn(min = 16.dp).background(if (on) k.lit else Color.Transparent, DiscCell).border(1.dp, if (on) k.lit else k.line, DiscCell)
                .padding(horizontal = 3.dp), contentAlignment = Alignment.Center) {
                Text((i + 1).toString(), color = if (on) k.onLit else k.label, fontSize = 9.sp, lineHeight = 12.sp, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold, softWrap = false, maxLines = 1)
            }
        }
    }
}

/**
 * 682bb: the LCD's track line on the pages - "DISC 1 · TRACK 03", the music calendar and the page's name on ONE line where they
 * fit; else "DISC 1", the calendar and the name (the calendar says the track); only where even that does not fit, the calendar on
 * a line of its own under them. (A calendar row of its own cost the PROGRAM page the room its first track needed at x1.3 once the
 * MODE key took a line of its own - AchFirstRow DISCS.)
 */
@Composable
internal fun DiscTrackLine(track: String, page: String, tracks: List<WinPage>, shown: WinPage, k: DiscInk) {
    val style = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, fontFamily = FontFamily.Monospace, lineHeight = 13.sp)
    androidx.compose.ui.layout.Layout(content = {
        // (682bc, HW-29 kpop13_4: the two labels are one fact said long or short - the one left unplaced gives way, it is not
        // a fact the deck dropped. QaClip.GivesWay.)
        Text(track, color = k.lcd.copy(alpha = 0.7f), style = style, maxLines = 1, softWrap = false, modifier = Modifier.semantics { this[QaClip.GivesWay] = true })
        Text(track.substringBefore(" ·"), color = k.lcd.copy(alpha = 0.7f), style = style, maxLines = 1, softWrap = false, modifier = Modifier.semantics { this[QaClip.GivesWay] = true })
        DiscCalendar(tracks, shown, k)
        Text(page, color = k.lcd, style = style, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }) { ms, c ->
        val w = c.maxWidth; val gap = 6.dp.roundToPx()
        val loose = androidx.compose.ui.unit.Constraints(maxWidth = w)
        val full = ms[0].measure(loose); val short = ms[1].measure(loose); val cal = ms[2].measure(loose); val name = ms[3].measure(loose)
        val fit = discTrackLineFit(full.width, short.width, cal.width, name.width, w, gap)
        when (fit) {
            0, 1 -> {
                val lab = if (fit == 0) full else short
                val h = maxOf(lab.height, cal.height, name.height)
                layout(w, h) {
                    lab.place(0, (h - lab.height) / 2); cal.place(lab.width + gap, (h - cal.height) / 2); name.place(w - name.width, (h - name.height) / 2)
                }
            }
            else -> {
                val h1 = maxOf(full.height, name.height); val top = 4.dp.roundToPx()
                layout(w, h1 + top + cal.height) {
                    full.place(0, (h1 - full.height) / 2); name.place(w - name.width, (h1 - name.height) / 2); cal.place(0, h1 + top)
                }
            }
        }
    }
}

/** 682bb (pure): 0 = the full track label, the calendar and the name on one line; 1 = "DISC 1", the calendar and the name; 2 = the
 *  calendar on its own line under the track label and the name. */
internal fun discTrackLineFit(fullW: Int, shortW: Int, calW: Int, nameW: Int, maxW: Int, gap: Int): Int = when {
    fullW + gap + calW + gap + nameW <= maxW -> 0
    shortW + gap + calW + gap + nameW <= maxW -> 1
    else -> 2
}

/** One track of the deck (Info is the deck itself, drawn by DiscPlayerPanel). */
@Composable
internal fun DiscPage(ctx: WinSceneCtx, p: WinPage, live: Boolean, k: DiscInk, modifier: Modifier) {
    when (p) {
        WinPage.DETAILS -> DiscInfo(ctx, live, k, modifier)
        WinPage.TROPHIES -> DiscProgram(ctx, live, k, modifier)
        WinPage.OPTIONS -> DiscSetup(ctx, live, k, modifier)
        WinPage.MAIN -> {}
        else -> androidx.compose.runtime.CompositionLocalProvider(
            LocalNativePageHost provides NativePageHost(Color.Transparent, dark = true, showsCover = true, showsName = false)
        ) { ctx.pageFor(p, modifier, live) } // (Performance: the shared page - not on the deck yet)
    }
}

@Composable
private fun DiscInfo(ctx: WinSceneCtx, live: Boolean, k: DiscInk, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    // (682az, hardware x1.3: with the liner notes as the only stop, the page opened scrolled to them and the CD-text facts were
    // off the top of the LCD. The CD-text block is the first stop now - the page opens on the facts, the notes one Down below.)
    Column(modifier.nativeScroll(k.lcd), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            Column(Modifier.fillMaxWidth().border(if (on) 2.dp else 1.dp, if (on) k.lcd else Color.Transparent, DiscWell).padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("CD-TEXT", color = if (on) k.lcd else k.label, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp, lineHeight = 13.sp)
                nativeFacts(ctx).forEach { f ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Box(Modifier.width(92.dp).border(1.dp, k.line, DiscCell).background(k.tile, DiscCell).padding(horizontal = 5.dp, vertical = 2.dp)) {
                            WholeWordsText(f.label.uppercase(), color = k.label, fontSize = 9.sp, lineHeight = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                        Spacer(Modifier.width(8.dp))
                        WholeWordsText(f.value.uppercase(), color = k.lcd, fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp,
                            fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), live = live) { on ->
            Column(Modifier.fillMaxWidth().background(if (on) k.lcd.copy(alpha = 0.12f) else k.tile, DiscWell)
                .border(if (on) 2.dp else 1.dp, if (on) k.lcd else k.line, DiscWell).padding(horizontal = 10.dp, vertical = 7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("▶", color = k.lcd, fontSize = 10.sp, lineHeight = 13.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("LINER NOTES", color = if (on) k.lcd else k.label, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp, lineHeight = 13.sp)
                }
                Spacer(Modifier.height(3.dp))
                Text(ctx.summary ?: "No liner notes for this game yet.", color = k.lcd.copy(alpha = 0.9f), fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
private fun DiscProgram(ctx: WinSceneCtx, live: Boolean, k: DiscInk, modifier: Modifier) {
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    Column(modifier) {
        // (682bb, owner review of the v0.7.0 preview: the MODE key slid its words in a sliver - "MODE: S" even on the RP Classic
        // at x1.3. The deck says its mode in its own short words (discModeWord) and the line is responsive: PROG, the count, the
        // level meter and the MODE key on one line when the key fits whole beside a meter of at least DISC_METER_MIN; otherwise
        // the key goes whole onto its own line under them and the meter takes the room. Never sliding, never shrunk.)
        val lit = discLevel(t.earned, t.total, DISC_LEVEL_BARS)
        androidx.compose.ui.layout.Layout(content = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("PROG", color = k.label, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, lineHeight = 13.sp,
                    letterSpacing = 1.sp, softWrap = false, maxLines = 1)
                Spacer(Modifier.width(6.dp))
                Text(discAwardCount(t.earned, t.total), color = k.lcd, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp,
                    fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
            }
            Row(Modifier.height(12.dp), horizontalArrangement = Arrangement.spacedBy(1.dp), verticalAlignment = Alignment.Bottom) {
                repeat(DISC_LEVEL_BARS) { i ->
                    // The meter's bars rise left to right, like the deck's level display.
                    val h = 0.35f + 0.65f * (i + 1) / DISC_LEVEL_BARS
                    Box(Modifier.weight(1f).fillMaxHeight(h).background(if (i < lit) k.lcd else k.tile).border(0.5.dp, k.line))
                }
            }
            NativeItem(onClick = t.nextOrder, focus = focus, live = live) { on ->
                Box(Modifier.background(if (on) k.lit else Color.Transparent, DiscCell).border(1.dp, if (on) k.lit else k.line, DiscCell)
                    .padding(horizontal = 9.dp, vertical = 4.dp)) {
                    WinFullText("MODE: " + discModeWord(t.order), color = if (on) k.onLit else k.lcd, fontSize = 11.sp, maxLines = 1, lineHeight = 14.sp,
                        fontFamily = FontFamily.Monospace)
                }
            }
        }) { ms, c ->
            val w = c.maxWidth
            val gap = 8.dp.roundToPx(); val minMeter = DISC_METER_MIN.roundToPx(); val maxMeter = DISC_METER_MAX.roundToPx()
            val count = ms[0].measure(androidx.compose.ui.unit.Constraints(maxWidth = w))
            val key = ms[2].measure(androidx.compose.ui.unit.Constraints(maxWidth = w))
            if (discModeOneLine(count.width, key.width, w, gap, minMeter)) {
                val mw = (w - count.width - key.width - gap * 2).coerceIn(minMeter, maxMeter)
                val meter = ms[1].measure(androidx.compose.ui.unit.Constraints.fixed(mw, 12.dp.roundToPx()))
                val h = maxOf(count.height, key.height, meter.height)
                layout(w, h) {
                    count.place(0, (h - count.height) / 2)
                    meter.place(count.width + gap, (h - meter.height) / 2)
                    key.place(w - key.width, (h - key.height) / 2)
                }
            } else {
                val mw = (w - count.width - gap).coerceIn(0, maxMeter * 2)
                val meter = ms[1].measure(androidx.compose.ui.unit.Constraints.fixed(mw, 12.dp.roundToPx()))
                val h1 = maxOf(count.height, meter.height); val top = 3.dp.roundToPx()
                layout(w, h1 + top + key.height) {
                    count.place(0, (h1 - count.height) / 2)
                    meter.place(count.width + gap, (h1 - meter.height) / 2)
                    key.place(0, h1 + top)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        NativeRows(k.lcd, Modifier.weight(1f).fillMaxWidth(), spacing = 2.dp) {
            nativeItems(t.all, { it.id }) { i, ach ->
                val earned = ach.isUnlocked
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { on ->
                        val text = if (on) k.onLit else if (earned) k.lcd else k.muted
                        val sub = if (on) k.onLit.copy(alpha = 0.8f) else k.muted
                        Column(Modifier.fillMaxWidth().background(if (on) k.lit else k.tile, DiscCell).padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(discTrackNo(i), color = text, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp,
                                    fontFamily = FontFamily.Monospace, softWrap = false, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 22.dp))
                                Spacer(Modifier.width(3.dp))
                                Text(if (earned) "▶" else "--", color = if (on) k.onLit else if (earned) k.lcd else k.muted, fontSize = 11.sp, lineHeight = 14.sp,
                                    fontFamily = FontFamily.Monospace, softWrap = false, maxLines = 1, modifier = Modifier.widthIn(min = 13.dp))
                                Spacer(Modifier.width(3.dp))
                                NativeBadge(ach.badgeName, ach.title, earned, k.lcd, Modifier.size(28.dp), shape = DiscCell)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    WholeWordsText((ach.title ?: "Untitled").uppercase(), color = text, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                        lineHeight = 16.sp, fontFamily = FontFamily.Monospace)
                                    Text(discAwardLine(earned, ach.dateEarned), color = sub, fontSize = 10.sp, lineHeight = 13.sp, fontFamily = FontFamily.Monospace)
                                }
                                ach.points?.let { pts ->
                                    Text("+$pts", color = text, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                                        softWrap = false, modifier = Modifier.padding(start = 6.dp))
                                }
                            }
                            ach.description?.let { d ->
                                if (opened) Text(d, color = text, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(start = 41.dp, top = 3.dp))
                                else WinFullText(d, color = sub, fontSize = 11.sp, maxLines = 1, lineHeight = 14.sp, modifier = Modifier.padding(start = 41.dp, top = 1.dp))
                            }
                            if (opened) ach.unlockPercent?.let { pc ->
                                Text("%.1f %% of players have it".format(pc), color = if (on) k.onLit else k.lcd, fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace, modifier = Modifier.padding(start = 41.dp, top = 1.dp, bottom = 2.dp))
                            }
                        }
                    }
                }
            }
            if (t.all.isEmpty()) item {
                Text("NO TRACKS - no achievements for this game yet.", color = k.muted, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun DiscSetup(ctx: WinSceneCtx, live: Boolean, k: DiscInk, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(k.lcd), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(),
                focus = if (i == 0) focus else null, live = live) { on ->
                androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                val armed = confirm.armed == i
                val hi = if (o.destructive) DiscWarn else k.lit
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().background(if (on) hi else k.tile, DiscCell)
                        .border(1.dp, if (on) hi else if (o.destructive) DiscWarn.copy(alpha = 0.5f) else k.line, DiscCell)
                        .padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        DiscKeyMark(o.destructive, if (on) k.onLit else if (o.destructive) DiscWarn else k.lcd)
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            WinFullText(o.label.uppercase(), color = if (on) k.onLit else if (o.destructive) DiscWarn else k.lcd, fontSize = 13.sp, maxLines = 2,
                                lineHeight = 18.sp, fontFamily = FontFamily.Monospace, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                            o.sub?.let { WinFullText(it, color = if (on) k.onLit.copy(alpha = 0.85f) else k.muted, fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp) }
                        }
                    }
                    if (armed) Text("PRESS A AGAIN TO CONFIRM  ·  MOVE AWAY TO CANCEL", color = Color.Black, fontSize = 11.sp, lineHeight = 15.sp,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp).background(DiscWarn, DiscCell).padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
        }
        if (ctx.options.isEmpty()) Text("Nothing to set up for this game.", color = k.muted, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
}

/** The deck key's mark before a Setup line: a small square, or the eject mark (a triangle over a bar) for one that removes. Drawn,
 *  so it never depends on a font having the symbol. */
@Composable
internal fun DiscKeyMark(eject: Boolean, color: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(14.dp)) {
        val w = size.width; val h = size.height
        if (!eject) { drawRect(color, topLeft = androidx.compose.ui.geometry.Offset(w * 0.2f, h * 0.2f), size = androidx.compose.ui.geometry.Size(w * 0.6f, h * 0.6f)); return@Canvas }
        val tri = androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.5f, h * 0.1f); lineTo(w * 0.92f, h * 0.6f); lineTo(w * 0.08f, h * 0.6f); close() }
        drawPath(tri, color)
        drawRect(color, topLeft = androidx.compose.ui.geometry.Offset(w * 0.08f, h * 0.72f), size = androidx.compose.ui.geometry.Size(w * 0.84f, h * 0.16f))
    }
}
