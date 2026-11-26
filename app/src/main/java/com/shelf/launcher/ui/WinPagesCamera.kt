package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/*
 * P2 (737a, class C): Pocket camera's own pages. The window is the camera cartridge's menu - the chunky pixel frame in the four
 * shades of the picked palette, the dithered picture on the left, the name over a one-pixel rule and the menu rows with the
 * arrow cursor on the right. Its pages take the room under the name, drawn in the same pixel language on the light shade:
 *  - Info         = GAME: the menu as before.
 *  - Details      = DATA: the facts as LABEL / VALUE lines in the pixel font, dotted pixel rules between, then the story in a
 *                   one-pixel frame headed ABOUT (a D-pad stop; the head turns dark while you are on it).
 *  - Achievements = ALBUM: the cup, "03/12" and a meter of pixel cells, the order as a menu row; each award a numbered album
 *                   entry - "NO.01", its badge in a square frame, the title, "GOT <date>" / "NOT YET", the points; the row
 *                   you are on dark with the arrow cursor, as on the menu; A opens the whole text and how rare it is.
 *  - Options      = SETUP: menu rows with the arrow cursor; one that removes something asks for a second A (NativeConfirm),
 *                   the warning a dark strip under it.
 * Heading: the page's name and a pixel square per page (the shown one filled) over a dotted rule - after the old page has
 * gone. Motion: the page draws itself down the little screen in steps, a dark scan line at its front (cameraStep, as the
 * window switching on); none with Motion Off. The keys stay a column under the page, so Play stays put (674e).
 * Shared: data, focus, B, Play-first, inert outgoing copy (NativePageKit).
 */

/** The camera's name for a page. Pure. */
internal fun cameraPageName(p: WinPage): String = when (p) {
    WinPage.MAIN -> "GAME"
    WinPage.DETAILS -> "DATA"
    WinPage.TROPHIES -> "ALBUM"
    WinPage.OPTIONS -> "SETUP"
    WinPage.PERF -> "PERFORMANCE"
}

/** The count beside the cup, two digits each as on the camera's counters: "03/12" (earned never above the total). Pure. */
internal fun cameraAlbumCount(earned: Int, total: Int): String {
    val t = maxOf(total, 0)
    return "%02d/%02d".format(earned.coerceIn(0, t), t)
}

/** An album entry's number: "NO.01" (from 1). Pure. */
internal fun cameraEntryNo(index: Int): String = "NO.%02d".format(maxOf(index, 0) + 1)

/** An award's line: "GOT <date>" / "GOT" when yours, "NOT YET" otherwise. Pure. */
internal fun cameraAwardLine(earned: Boolean, date: String?): String =
    if (!earned) "NOT YET" else date?.takeIf { it.isNotBlank() }?.let { "GOT ${it.trim()}" } ?: "GOT"

/** How many of the meter's [cells] are filled: none at 0, all only when everything is earned, at least one once one is. Pure. */
internal fun cameraMeterCells(earned: Int, total: Int, cells: Int): Int {
    if (total <= 0 || cells <= 0) return 0
    val e = earned.coerceIn(0, total)
    if (e == 0) return 0
    if (e == total) return cells
    return (e * cells / total).coerceIn(1, cells - 1)
}

internal fun cameraMove(@Suppress("UNUSED_PARAMETER") dir: Int): ContentTransform =
    fadeIn(tween(NATIVE_PAGE_MS / 4)) togetherWith fadeOut(tween(NATIVE_OUT_MS))


/** The four shades (c0 darkest .. c3 lightest), the pixel font and its unit. */
internal class CameraInk(val c0: Color, val c1: Color, val c2: Color, val c3: Color, val font: FontFamily, val u: Dp, val fs: (Float) -> TextUnit)

/** A row of pixel dots across: the camera's dotted rule. */
@Composable
private fun CameraDots(k: CameraInk, color: Color = k.c2) {
    Canvas(Modifier.fillMaxWidth().height(k.u)) {
        val p = k.u.toPx()
        var x = 0f
        while (x + p <= size.width) { drawRect(color, Offset(x, 0f), Size(p, p)); x += p * 2 }
    }
}

/** The heading: the page's name and a pixel square per page (the shown one filled), a dotted rule under it. */
@Composable
internal fun CameraPageHeading(tabs: List<WinPage>, shown: WinPage, k: CameraInk) {
    Column(Modifier.fillMaxWidth().padding(bottom = k.u * 2)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(cameraPageName(shown), color = k.c0, fontFamily = k.font, fontSize = k.fs(5.5f), maxLines = 1, softWrap = false)
            Spacer(Modifier.width(k.u * 4))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(k.u * 2), verticalAlignment = Alignment.CenterVertically) {
                tabs.forEach { t ->
                    val on = t == shown
                    Canvas(Modifier.size(k.u * 4)) {
                        val p = k.u.toPx()
                        if (on) drawRect(k.c0) else {
                            drawRect(k.c1, Offset.Zero, Size(size.width, p)); drawRect(k.c1, Offset(0f, size.height - p), Size(size.width, p))
                            drawRect(k.c1, Offset.Zero, Size(p, size.height)); drawRect(k.c1, Offset(size.width - p, 0f), Size(p, size.height))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(k.u))
        CameraDots(k)
    }
}

/** One of the camera's pages (Game is the menu itself, drawn by CameraMenu). */
@Composable
internal fun AnimatedVisibilityScope.CameraPage(ctx: WinSceneCtx, p: WinPage, live: Boolean, k: CameraInk, modifier: Modifier) {
    val anim = rememberAnimationsEnabled()
    // The page draws itself down the little screen in steps (as the window switching on), a dark scan line at its front.
    val scan by transition.animateFloat(transitionSpec = { if (anim) tween(NATIVE_PAGE_MS) else snap() }, label = "cameraScan") {
        if (it == EnterExitState.Visible) 1f else 0f
    }
    val m = modifier.drawWithContent {
        val q = cameraStep(scan)
        clipRect(bottom = size.height * q) { this@drawWithContent.drawContent() }
        if (q in 0.01f..0.99f) drawRect(k.c0, Offset(0f, size.height * q), Size(size.width, k.u.toPx()))
    }
    when (p) {
        WinPage.DETAILS -> CameraData(ctx, live, k, m)
        WinPage.TROPHIES -> CameraAlbum(ctx, live, k, m)
        WinPage.OPTIONS -> CameraSetup(ctx, live, k, m)
        WinPage.MAIN -> {}
        else -> ctx.pageFor(p, modifier, live) // (Performance: the shared page - not on the camera yet)
    }
}

@Composable
private fun CameraData(ctx: WinSceneCtx, live: Boolean, k: CameraInk, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    Column(modifier.nativeScroll(k.c0)) {
        nativeFacts(ctx).forEachIndexed { i, f ->
            if (i > 0) CameraDots(k)
            Row(Modifier.fillMaxWidth().padding(vertical = k.u * 1.5f), verticalAlignment = Alignment.Top) {
                WholeWordsText(f.label.uppercase(), color = k.c1, fontFamily = k.font, fontSize = k.fs(4.5f), modifier = Modifier.weight(0.42f))
                Spacer(Modifier.width(k.u * 2))
                WholeWordsText(f.value.uppercase(), color = k.c0, fontFamily = k.font, fontSize = k.fs(4.5f), modifier = Modifier.weight(0.58f))
            }
        }
        Spacer(Modifier.height(k.u * 3))
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            Column(Modifier.fillMaxWidth().border(if (on) k.u * 2 else k.u, if (on) k.c0 else k.c1, RectangleShape)) {
                Text("ABOUT", color = if (on) k.c3 else k.c0, fontFamily = k.font, fontSize = k.fs(4.5f),
                    modifier = Modifier.fillMaxWidth().background(if (on) k.c0 else k.c2).padding(horizontal = k.u * 3, vertical = k.u))
                Text(ctx.summary ?: "No description for this game yet.", color = k.c0, fontFamily = k.font, fontSize = k.fs(4.5f),
                    lineHeight = k.fs(6f), modifier = Modifier.padding(horizontal = k.u * 3, vertical = k.u * 2))
            }
        }
    }
}

/** A row in the menu's way: dark with the arrow cursor while it has the focus. */
@Composable
private fun CameraRow(lit: Boolean, k: CameraInk, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 24.dp).background(if (lit) k.c0 else Color.Transparent)
        .padding(horizontal = k.u * 2, vertical = k.u), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(k.u * 7)) { if (lit) pixelIcon(PX_CURSOR, Offset.Zero, k.u.toPx(), k.c3) }
        Spacer(Modifier.width(k.u * 2))
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun CameraAlbum(ctx: WinSceneCtx, live: Boolean, k: CameraInk, modifier: Modifier) {
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    Column(modifier) {
        // One line: the cup and the count, the cell meter in the room between, the order key at most its share (its words
        // slide on one line, rule 11) - so the first entry stays whole at x1.3 (AchFirstRow).
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(k.u * 7)) { pixelIcon(PX_TROPHY, Offset.Zero, k.u.toPx(), k.c0) }
            Spacer(Modifier.width(k.u * 2))
            Text(cameraAlbumCount(t.earned, t.total), color = k.c0, fontFamily = k.font, fontSize = k.fs(4.5f), maxLines = 1, softWrap = false)
            Canvas(Modifier.weight(1f).padding(horizontal = k.u * 3).height(k.u * 3)) {
                val p = k.u.toPx()
                val cells = ((size.width + p) / (p * 3)).toInt().coerceAtLeast(1)
                val lit = cameraMeterCells(t.earned, t.total, cells)
                for (c in 0 until cells) drawRect(if (c < lit) k.c0 else k.c2, Offset(c * p * 3, 0f), Size(p * 2, size.height))
            }
            NativeItem(onClick = t.nextOrder, Modifier.weight(1f, fill = false), focus = focus, live = live) { lit ->
                Box(Modifier.background(if (lit) k.c0 else k.c2).padding(horizontal = k.u * 3, vertical = k.u)) {
                    WinFullText("ORDER " + t.order.label.uppercase(), color = if (lit) k.c3 else k.c0, fontFamily = k.font, fontSize = k.fs(4.5f), maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(k.u * 2))
        NativeRows(k.c0, Modifier.weight(1f).fillMaxWidth(), spacing = k.u) {
            nativeItems(t.all, { it.id }) { i, ach ->
                val earned = ach.isUnlocked
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { lit ->
                        val ink = if (lit) k.c3 else if (earned) k.c0 else k.c1
                        Column(Modifier.fillMaxWidth().background(if (lit) k.c0 else Color.Transparent)
                            .border(k.u, if (lit) k.c0 else k.c2, RectangleShape).padding(horizontal = k.u * 2, vertical = k.u)) {
                            // (AchFirstRow Rpc x1.3: the number, badge and points in one line with the title left the title a
                            // letter wide - the entry stood 346 dp tall. The badge stands on the left, the number and the points
                            // share the entry's first line, the title the whole width under them.)
                            Row(verticalAlignment = Alignment.Top) {
                                NativeBadge(ach.badgeName, ach.title, earned, k.c1, Modifier.padding(top = k.u).size(k.u * 8)
                                    .border(k.u, if (lit) k.c3 else k.c0, RectangleShape), shape = RectangleShape)
                                Spacer(Modifier.width(k.u * 2))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(cameraEntryNo(i), color = if (lit) k.c3 else k.c1, fontFamily = k.font, fontSize = k.fs(4f), maxLines = 1, softWrap = false)
                                        Spacer(Modifier.weight(1f))
                                        ach.points?.let { pts ->
                                            Text("${pts}P", color = ink, fontFamily = k.font, fontSize = k.fs(4f), maxLines = 1, softWrap = false,
                                                modifier = Modifier.padding(start = k.u * 2))
                                        }
                                    }
                                    WholeWordsText((ach.title ?: "Untitled").uppercase(), color = ink, fontFamily = k.font, fontSize = k.fs(4.5f))
                                    Text(cameraAwardLine(earned, ach.dateEarned), color = if (lit) k.c3 else k.c1, fontFamily = k.font, fontSize = k.fs(4f))
                                    ach.description?.let { d ->
                                        if (opened) Text(d, color = ink, fontFamily = k.font, fontSize = k.fs(4.5f), lineHeight = k.fs(6f),
                                            modifier = Modifier.padding(top = k.u))
                                        else WinFullText(d, color = if (lit) k.c3 else k.c1, fontFamily = k.font, fontSize = k.fs(4f), maxLines = 1,
                                            modifier = Modifier.padding(top = k.u * 0.5f))
                                    }
                                    if (opened) ach.unlockPercent?.let { pc ->
                                        Text("%.1f %% OF PLAYERS HAVE IT".format(pc), color = ink, fontFamily = k.font, fontSize = k.fs(4f),
                                            modifier = Modifier.padding(top = k.u * 0.5f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (t.all.isEmpty()) item {
                Text("NO AWARDS FOR THIS GAME YET.", color = k.c1, fontFamily = k.font, fontSize = k.fs(4.5f), modifier = Modifier.padding(top = k.u * 2))
            }
        }
    }
}

@Composable
private fun CameraSetup(ctx: WinSceneCtx, live: Boolean, k: CameraInk, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(k.c0), verticalArrangement = Arrangement.spacedBy(k.u)) {
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(),
                focus = if (i == 0) focus else null, live = live) { lit ->
                androidx.compose.runtime.LaunchedEffect(lit) { if (!lit) confirm.leave(i) }
                val armed = confirm.armed == i
                Column(Modifier.fillMaxWidth()) {
                    CameraRow(lit, k) {
                        Column(verticalArrangement = Arrangement.spacedBy(k.u * 0.5f)) {
                            WinFullText((if (o.destructive) "! " else "") + o.label.uppercase(), color = if (lit) k.c3 else k.c0,
                                fontFamily = k.font, fontSize = k.fs(5f), maxLines = 2)
                            o.sub?.let { WinFullText(it.uppercase(), color = if (lit) k.c2 else k.c1, fontFamily = k.font, fontSize = k.fs(4f), maxLines = 2) }
                        }
                    }
                    if (armed) Text("PRESS A AGAIN TO REMOVE - MOVE AWAY TO CANCEL", color = k.c3, fontFamily = k.font, fontSize = k.fs(4.5f),
                        modifier = Modifier.fillMaxWidth().padding(top = k.u).background(k.c1).padding(horizontal = k.u * 3, vertical = k.u * 1.5f))
                }
            }
        }
        if (ctx.options.isEmpty()) Text("NO SETTINGS FOR THIS GAME.", color = k.c1, fontFamily = k.font, fontSize = k.fs(4.5f))
    }
}

/** The camera's page area under the name: the heading, then the page, drawn down the screen by [CameraPage]'s scan. */
@Composable
internal fun CameraPageArea(ctx: WinSceneCtx, k: CameraInk, modifier: Modifier) {
    val heading = rememberHeadingPage(ctx.page)
    Column(modifier) {
        CameraPageHeading(nativeTabs(ctx.pages), if (heading == WinPage.MAIN) ctx.page else heading, k)
        NativePages(ctx, Modifier.fillMaxWidth().weight(1f), label = "cameraPage", move = ::cameraMove) { pg, pageLive ->
            CameraPage(ctx, pg, pageLive, k, Modifier.fillMaxSize())
        }
    }
}
