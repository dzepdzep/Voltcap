package com.shelf.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.Features

/*
 * Next version: the collections Cinema and Arctic hub show as rows (Netflix-like rows / Arctic widgets). Each row is a
 * list of positions in the category's own game list (StyleCtx.games), so picking a game in any row is the same
 * StyleActions.select as everywhere else — the game window, Play, the background and the second screen all follow.
 */

/** One row: its title and the games in it, as positions in StyleCtx.games. */
internal class HomeRow(val key: String, val title: String, val games: List<Int>)

/** Which rows a layout shows (its own switches). */
internal class HomeRowSwitches(val continuePlaying: Boolean, val favourites: Boolean, val mostPlayed: Boolean, val notPlayed: Boolean)

/**
 * The rows for [c]: Continue playing (newest first), the category itself, Your favourites, Most played and Not played
 * yet — each only when it has games. The category's own row is always there (it holds every game, so the game you're on
 * is always in some row).
 */
internal fun homeRows(c: StyleCtx, on: HomeRowSwitches): List<HomeRow> {
    val n = c.games.size
    if (n == 0) return emptyList()
    val rows = ArrayList<HomeRow>()
    val idx = (0 until n)
    if (on.continuePlaying) {
        val played = idx.filter { c.lastPlayed(c.games[it]) != null }.sortedByDescending { c.lastPlayed(c.games[it]) ?: 0L }.take(20)
        if (played.isNotEmpty()) rows += HomeRow("continue", "Continue playing", played)
    }
    rows += HomeRow("all", c.categoryLabel, idx.toList())
    if (on.favourites) {
        val fav = idx.filter { c.games[it].stableKey in c.state.favorites }
        if (fav.isNotEmpty()) rows += HomeRow("fav", "Your favourites", fav)
    }
    if (on.mostPlayed) {
        val most = idx.filter { (c.playtime(c.games[it]) ?: 0L) > 0L }.sortedByDescending { c.playtime(c.games[it]) ?: 0L }.take(15)
        if (most.size >= 2) rows += HomeRow("most", "Most played", most)
    }
    if (on.notPlayed) {
        val fresh = idx.filter { c.lastPlayed(c.games[it]) == null && c.playtime(c.games[it]) == null }.take(30)
        if (fresh.isNotEmpty() && fresh.size < n) rows += HomeRow("new", "Not played yet", fresh)
    }
    return rows
}

/** Where the controller is in a row layout: the row, and the place in each row it was last on. */
internal class RowCursor {
    var row = 0
    val cols = HashMap<String, Int>()

    /** Keeps the cursor on [focus] (the game you're on may have changed elsewhere: a category step, a search). */
    fun follow(rows: List<HomeRow>, focus: Int) {
        if (rows.isEmpty()) { row = 0; return }
        row = row.coerceIn(0, rows.lastIndex)
        val r = rows[row]
        val at = cols[r.key]?.let { r.games.getOrNull(it) }
        if (at == focus) return
        val here = r.games.indexOf(focus)
        if (here >= 0) { cols[r.key] = here; return }
        // Another row has it: the category's row always does.
        val k = rows.indexOfFirst { it.key == "all" }.coerceAtLeast(0)
        row = k; cols[rows[k].key] = rows[k].games.indexOf(focus).coerceAtLeast(0)
    }

    fun col(rows: List<HomeRow>): Int = rows.getOrNull(row)?.let { (cols[it.key] ?: 0).coerceIn(0, (it.games.size - 1).coerceAtLeast(0)) } ?: 0

    /** ◀ ▶: the game [d] along the row (false at its end). */
    fun along(rows: List<HomeRow>, d: Int, select: (Int) -> Unit): Boolean {
        val r = rows.getOrNull(row) ?: return false
        val now = col(rows); val to = now + d
        if (to !in r.games.indices) return false
        cols[r.key] = to; select(r.games[to]); return true
    }

    /** ▲ ▼: the row [d] away, on the game it was last on there (false past the first / last row). */
    fun across(rows: List<HomeRow>, d: Int, select: (Int) -> Unit): Boolean {
        val to = row + d
        if (to !in rows.indices) return false
        row = to
        val r = rows[to]
        val c = (cols[r.key] ?: 0).coerceIn(0, r.games.lastIndex)
        cols[r.key] = c; select(r.games[c]); return true
    }
}

/**
 * A story longer than its box rolls up slowly, holds at the end, and comes back (R18: all of it can be read, nothing cut
 * with "…"). It rests with the home screen (HomeIdle.calm) and starts again from the top for each game.
 */
@Composable
internal fun RollingStory(
    text: String, color: Color, fontSize: TextUnit, lineHeight: TextUnit, height: Dp, modifier: Modifier = Modifier, roll: Boolean = true,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null
) {
    val scroll: ScrollState = rememberScrollState()
    val calm = HomeIdle.calm
    val animate = rememberAnimationsEnabled()
    LaunchedEffect(text, roll, calm, animate) {
        scroll.scrollTo(0)
        if (!roll || calm || !animate) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(3500)
            val max = scroll.maxValue
            if (max <= 0) return@LaunchedEffect
            scroll.animateScrollTo(max, tween((max * 22).coerceIn(1500, 30_000), easing = LinearEasing))
            kotlinx.coroutines.delay(3000)
            scroll.animateScrollTo(0, tween(600))
        }
    }
    Box(modifier.height(height).sceneFadeEdges(scroll, 14.dp).verticalScroll(scroll, enabled = true)) {
        Text(text, color = color, fontSize = fontSize, lineHeight = lineHeight, fontFamily = fontFamily)
    }
}
