package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/*
 * P2 batch 3 (682w, owner 22 Nov "1 and then 2": the next layouts after the refinements): Terminal's own pages. The window is a
 * DOS dialog in the monitor's colours - the double frame, the RUN title, the file on the left, the command menu on the right
 * with the reverse-video row and the ► cursor. The pages are the commands you would type at its prompt, their output on the
 * left; the menu on the right stays where it is on every page (Play its first row - the same size and place everywhere):
 *  - Details      = C:\> TYPE <GAME>.TXT - the story typed out in full, in the monitor's font (a D-pad stop that scrolls).
 *  - Achievements = C:\> DIR ACHIEVE\ /O:<order> - a directory listing: each achievement a file line, [X] for earned, its
 *                   points as the size column, the count of files at its foot; A on a line prints its description under it.
 *  - Options      = C:\> SETUP - a numbered menu; a line that removes something asks "ARE YOU SURE (Y/N)?" (NativeConfirm).
 * Motion: a page types in - the new output comes down line by line from the prompt (as the dialog's own text does on open),
 * the old screen clears at once (a CLS).
 */

/** The command a page runs at the prompt. Pure. */
internal fun termCommand(p: WinPage, alias: String, order: String = "SET"): String = when (p) {
    WinPage.MAIN -> "TYPE $alias.NFO"
    WinPage.DETAILS -> "TYPE $alias.TXT"
    WinPage.TROPHIES -> "DIR ACHIEVE\\ /O:" + order.uppercase().replace(' ', '_')
    WinPage.OPTIONS -> "SETUP"
    WinPage.PERF -> "MEM /C"
}

/** 682ag (pure): a typed line kept whole at its path / switch marks - a word joiner (U+2060, drawn as nothing) after "/", "\\"
 *  and ":" inside a token, so "/O:SET_ORDER" and "C:\\ACHIEVE" never break after their slash or colon (the line breaker
 *  treats those as break points; RP Classic x1.3 showed "DIR ACHIEVE\\ /" over "O:SET"). */
internal fun termKeepTokens(s: String): String = buildString {
    s.forEachIndexed { i, c -> append(c); if ((c == '/' || c == '\\' || c == ':') && i + 1 < s.length && s[i + 1] != ' ') append('\u2060') }
}

/** A directory listing line for an achievement: "[X] TITLE", the points as the size column. Pure. */
internal fun termDirLine(earned: Boolean, title: String?): String = (if (earned) "[X] " else "[ ] ") + (title ?: "UNTITLED").uppercase()

/** The listing's size column: " 10 PTS" right-aligned to 3 digits. Pure. */
internal fun termPoints(points: Int?): String = (points?.toString() ?: "-").padStart(3) + " PTS"

internal fun termMove(@Suppress("UNUSED_PARAMETER") dir: Int): ContentTransform =
    (fadeIn(tween(60)) + expandVertically(tween(NATIVE_PAGE_MS, easing = LinearEasing), expandFrom = Alignment.Top, clip = true)) togetherWith
        fadeOut(tween(40))

/** One of Terminal's own pages (the main page's file is the dialog's own column). */
@Composable
internal fun AnimatedVisibilityScope.TermPage(ctx: WinSceneCtx, p: WinPage, live: Boolean, style: TextStyle, fg: Color, dim: Color, accent: Color,
                                               hi: Color, hiInk: Color, modifier: Modifier) {
    when (p) {
        WinPage.DETAILS -> TermType(ctx, live, style, fg, dim, hi, modifier)
        WinPage.TROPHIES -> TermDir(ctx, live, style, fg, dim, accent, hi, hiInk, modifier)
        WinPage.OPTIONS -> TermSetup(ctx, live, style, fg, dim, accent, hi, hiInk, modifier)
        WinPage.MAIN -> {}
        else -> ctx.pageFor(p, modifier, live)
    }
}

@Composable
private fun TermType(ctx: WinSceneCtx, live: Boolean, style: TextStyle, fg: Color, dim: Color, hi: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    Column(modifier.nativeScroll(fg)) {
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            Text((ctx.summary ?: "FILE NOT FOUND - NO STORY FOR THIS GAME YET.").uppercase(), style = style.copy(color = fg),
                modifier = Modifier.fillMaxWidth().then(if (on) Modifier.background(hi.copy(alpha = 0.18f)) else Modifier).padding(2.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text("C:\\> _", style = style.copy(color = dim))
    }
}

@Composable
private fun TermDir(ctx: WinSceneCtx, live: Boolean, style: TextStyle, fg: Color, dim: Color, accent: Color, hi: Color, hiInk: Color, modifier: Modifier) {
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    Column(modifier) {
        WholeWordsText(termKeepTokens(" DIRECTORY OF C:\\ACHIEVE"), color = dim, fontSize = style.fontSize, fontFamily = style.fontFamily, lineHeight = style.fontSize * 1.3f)
        // The sort switch, as the command's own /O: - A steps it.
        NativeItem(onClick = t.nextOrder, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            Text((if (on) "► " else "  ") + "/O:" + t.order.label.uppercase(), style = if (on) style.copy(color = hiInk, shadow = null) else style.copy(color = accent),
                modifier = Modifier.fillMaxWidth().background(if (on) hi else Color.Transparent).padding(horizontal = 2.dp, vertical = 2.dp))
        }
        NativeRows(fg, Modifier.weight(1f).fillMaxWidth()) {
            nativeItems(t.all, { it.id }) { _, ach ->
                val earned = ach.isUnlocked
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { on ->
                        val ink = if (on) hiInk else if (earned) fg else dim
                        Column(Modifier.fillMaxWidth().background(if (on) hi else Color.Transparent).padding(horizontal = 2.dp, vertical = 2.dp)) {
                            Row {
                                WholeWordsText(termDirLine(earned, ach.title), color = ink, fontSize = style.fontSize, fontFamily = style.fontFamily,
                                    lineHeight = style.fontSize * 1.3f, modifier = Modifier.weight(1f))
                                Text(termPoints(ach.points), style = style.copy(color = ink, shadow = null), softWrap = false)
                            }
                            ach.description?.let { d ->
                                if (opened) Text("    " + d.uppercase(), style = style.copy(color = if (on) hiInk else fg, fontSize = style.fontSize * 0.9f))
                                else WinFullText("    " + d.uppercase(), color = if (on) hiInk else dim, fontSize = style.fontSize * 0.9f, maxLines = 1, style = style)
                            }
                        }
                    }
                }
            }
            if (t.all.isEmpty()) item {
            Text("FILE NOT FOUND", style = style.copy(color = dim))
            }
        }
        Text("    ${t.earned} FILE(S) EARNED   ${t.total} FILE(S) TOTAL", style = style.copy(color = dim))
    }
}

/** Room kept under an armed row when it scrolls into view - clear of the screen's dark bottom edge (682ag kh13). */
internal val TERM_CONFIRM_ROOM = 16.dp

@Composable
private fun TermSetup(ctx: WinSceneCtx, live: Boolean, style: TextStyle, fg: Color, dim: Color, accent: Color, hi: Color, hiInk: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(fg)) {
        Text(" SETUP - CHOOSE AN OPTION", style = style.copy(color = dim))
        Spacer(Modifier.height(2.dp))
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(), focus = if (i == 0) focus else null, live = live) { on ->
                androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                val armed = confirm.armed == i
                val ink = if (on) hiInk else if (o.destructive) accent else fg
                // (682ag, owner 23 Nov 13:32 - "Terminal's destructive confirmation fully readable": the number and pointer in a column of
                // their own so a wrapped name keeps its indent; the description and the confirmation indented by layout, not spaces;
                // the confirmation as three short lines (no "(Y/ / N)" break) and the armed row scrolled whole into view.)
                val bring = androidx.compose.runtime.remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
                // (682ag kh13, KONKR x1.3: scrolled just into view, the last line "MOVE AWAY = NO" sat in the screen's dark bottom edge -
                // the row comes up with [TERM_CONFIRM_ROOM] under it.)
                val rowSize = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
                val roomPx = with(androidx.compose.ui.platform.LocalDensity.current) { TERM_CONFIRM_ROOM.toPx() }
                androidx.compose.runtime.LaunchedEffect(armed) { if (armed) { kotlinx.coroutines.delay(32)
                    bring.bringIntoView(androidx.compose.ui.geometry.Rect(0f, 0f, rowSize.value.width.toFloat(), rowSize.value.height + roomPx)) } }
                Column(Modifier.fillMaxWidth().onSizeChanged { rowSize.value = it }.bringIntoViewRequester(bring).background(if (on) hi else Color.Transparent).padding(horizontal = 2.dp, vertical = 3.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text((if (on) "► " else "  ") + "${i + 1}. ", style = style.copy(color = ink, shadow = if (on) null else style.shadow), softWrap = false,
                            lineHeight = style.fontSize * 1.3f)
                        WholeWordsText(o.label.uppercase(), color = ink, fontSize = style.fontSize, fontFamily = style.fontFamily,
                            lineHeight = style.fontSize * 1.3f, modifier = Modifier.weight(1f))
                    }
                    o.sub?.let { WholeWordsText(it.uppercase(), color = if (on) hiInk else dim, fontSize = style.fontSize * 0.85f, fontFamily = style.fontFamily,
                        lineHeight = style.fontSize * 0.85f * 1.3f, modifier = Modifier.padding(start = TERM_INDENT)) }
                    if (armed) Column(Modifier.padding(start = TERM_INDENT, top = 6.dp)) {
                        for (l in termConfirmLines()) WholeWordsText(termKeepTokens(l), color = if (on) hiInk else accent, fontSize = style.fontSize,
                            fontFamily = style.fontFamily, lineHeight = style.fontSize * 1.3f)
                    }
                }
            }
        }
        if (ctx.options.isEmpty()) Text("NOTHING TO SET UP.", style = style.copy(color = dim))
        else if (confirm.armed != null) Spacer(Modifier.height(TERM_CONFIRM_ROOM)) // (the last row too can come up off the edge)
    }
}


/** 682ag: the Setup page's indent for an option's description and confirmation (where "► 1. " ends). */
internal val TERM_INDENT = 28.dp

/** 682ag (pure): the destructive confirmation, one short line each - never a break inside "(Y/N)". */
internal fun termConfirmLines(): List<String> = listOf("ARE YOU SURE? (Y/N)", "A AGAIN = YES", "MOVE AWAY = NO")
