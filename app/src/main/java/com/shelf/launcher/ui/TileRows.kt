package com.shelf.launcher.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/*
 * Sprint 527 (owner: "Tile Home's row positioning ... moving a row should produce the intended actual position; neighbouring
 * rows shift to make space; the Edit Home preview must match the final Home"): Tile home's stacked rows - the round buttons
 * by the tiles, the category chips, the tiles (with their name) and the game facts - are one ordered stack instead of
 * free offsets. Moving a row in Edit Home (▲ / ▼, or a drag) changes its place in that order; the others move up or down to
 * make room, so nothing overlaps, nothing is left with a gap and what Edit Home shows is what the home shows.
 *
 * One source of truth: Features.tileRowOrder (ids, comma separated). Empty = the order your settings have always given
 * (Where the tiles sit, the category bar's side, round buttons by the row), so an untouched home looks exactly as before.
 * Rows that aren't shown (a setting off, the buttons floating elsewhere) keep their place in the order and come back there.
 * Pure (unit-tested): TileRowsTest.
 */
internal object TileRows {
    /** Sprint 527: how far a moved order's first row stays below the screen's top (the status bar and clock), in dp. */
    const val TOP_CLEAR_DP = 40

    const val BUTTONS = "buttons"
    const val CATS = "cats"
    const val TILES = "tiles"
    const val INFO = "info"
    val ALL = listOf(BUTTONS, CATS, TILES, INFO)

    /** The order the settings give (Sprint 526 and before): buttons above only at the bottom, the chips above unless "under the row". */
    fun defaultOrder(buttonsAbove: Boolean, catsBelow: Boolean): List<String> = buildList {
        if (buttonsAbove) add(BUTTONS)
        if (!catsBelow) add(CATS)
        add(TILES)
        add(INFO)
        if (catsBelow) add(CATS)
        if (!buttonsAbove) add(BUTTONS)
    }

    /** [saved] (the stored text) as a whole order: unknown ids dropped, repeats once, a missing row at its default place. */
    fun resolve(saved: String, default: List<String>): List<String> {
        val got = saved.split(',').map { it.trim() }.filter { it in ALL }.distinct().toMutableList()
        if (got.isEmpty()) return default
        for (id in default) if (id !in got) {
            // After the row it follows by default (or first): a new or missing row lands where it always sat.
            val before = default.subList(0, default.indexOf(id)).lastOrNull { it in got }
            got.add(if (before == null) 0 else got.indexOf(before) + 1, id)
        }
        return got
    }

    fun format(order: List<String>): String = order.joinToString(",")

    /**
     * [id] moved one place [dir] (-1 up, +1 down) among the rows shown now ([shown]): it swaps with the next shown row that
     * way, the hidden ones between keep their places relative to the others. Unchanged at the top / bottom.
     */
    fun move(order: List<String>, id: String, dir: Int, shown: Set<String>): List<String> {
        val i = order.indexOf(id)
        if (i < 0 || dir == 0) return order
        var j = i + dir
        while (j in order.indices && order[j] !in shown) j += dir
        if (j !in order.indices) return order
        val out = order.toMutableList()
        out.removeAt(i)
        out.add(if (dir > 0) out.indexOf(order[j]) + 1 else out.indexOf(order[j]), id)
        return out
    }

    /** [id] put in front of the shown row [beforeId] (null: after the last shown row) - a drop at an insertion line. */
    fun insertBefore(order: List<String>, id: String, beforeId: String?): List<String> {
        if (id == beforeId || id !in order) return order
        val out = order.toMutableList(); out.remove(id)
        val at = if (beforeId == null || beforeId !in out) out.size else out.indexOf(beforeId)
        out.add(at, id)
        return out
    }

    /**
     * The insertion line a drop at [y] (px) lands on: the shown rows' tops / bottoms ([spans], in order) give one gap per
     * place; the id of the row it would go in front of (null: after the last). The dragged row itself is skipped.
     */
    fun dropBefore(spans: List<Triple<String, Float, Float>>, dragged: String, y: Float): String? {
        val others = spans.filter { it.first != dragged }
        for (s in others) if (y < (s.second + s.third) / 2f) return s.first
        return null
    }

    /** The round buttons sit above the tiles in the order (▲ from the tiles reaches them, not ▼). */
    fun buttonsAbove(saved: String, dockBottom: Boolean, catsBelow: Boolean): Boolean {
        val o = resolve(saved, defaultOrder(dockBottom, catsBelow))
        return o.indexOf(BUTTONS) < o.indexOf(TILES)
    }

    /** The rows shown above the tiles and under them (the tiles' centring keeps the ones under them in the group). */
    fun above(order: List<String>, shown: Set<String>): List<String> = order.takeWhile { it != TILES }.filter { it in shown }
    fun below(order: List<String>, shown: Set<String>): List<String> = order.dropWhile { it != TILES }.drop(1).filter { it in shown }

    /**
     * The space before row [id] in the stack, matching the old fixed layout for the default order: the chips sit 2 dp over
     * the tiles, the facts 18 dp under them (x the tile scale), the chips under the row 4 dp, the buttons under it 8 dp
     * (2 dp at the top), nothing before the first row.
     */
    fun gapBefore(prev: String?, id: String, top: Boolean, tileScale: Float): Float = when {
        prev == null -> 0f
        id == TILES && prev == CATS -> 2f
        id == TILES && prev == BUTTONS -> 0f
        id == TILES -> 8f
        id == INFO && prev == TILES -> 18f * tileScale
        id == INFO -> 8f
        id == CATS && prev == BUTTONS -> 0f
        id == CATS -> 4f
        id == BUTTONS && (prev == TILES || prev == INFO || prev == CATS) -> if (top) 2f else 8f
        else -> 6f
    }
}

/**
 * Sprint 527: Edit Home's side of Tile home's rows (not pure: reads and writes Features.tileRowOrder). The layout tells it
 * which rows are shown and the default order ([update], every composition); ▲ / ▼ on a selected row ([move]) or a drop
 * between rows ([insert]) changes the order - one step of Undo, put back by Discard (HomeEditSession keeps it in its snapshot).
 */
internal object TileRowEdit {
    const val CATS_PART = "cats"
    const val INFO_PART = "info"
    @Volatile private var shown: Set<String> = emptySet()
    @Volatile private var default: List<String> = TileRows.defaultOrder(buttonsAbove = false, catsBelow = false)
    /** Each row's place on the screen now (root px: top, bottom), for drops and the insertion line. */
    val spans = java.util.concurrent.ConcurrentHashMap<String, Pair<Float, Float>>()
    /** The middle (root px) of the room the round buttons keep in the stack while they float in Edit Home, or null. Compose state. */
    var buttonsCentre by androidx.compose.runtime.mutableStateOf<androidx.compose.ui.geometry.Offset?>(null)
    /** The row part a finger is dragging now (its shift follows the finger until the drop), or null. Compose state. */
    var dragging by androidx.compose.runtime.mutableStateOf<String?>(null)
    /** The insertion line while a row is dragged (root px), or null. Compose state. */
    var rowDropLine by androidx.compose.runtime.mutableStateOf<Float?>(null)
    /**
     * A dragged row let go: it goes into the order where its middle is (between the rows there), its up / down shift is
     * taken out and its left / right kept - the same place the insertion line showed. The drag's Undo step was taken at its start.
     */
    fun dropRow(partId: String) {
        val row = rowOf(partId)
        val b = HomeEditState.bounds[partId]
        if (row != null && b != null) insert(row, b.center.y)
        HomeEditState.liveShift[partId]?.let { s -> HomeEditState.liveShift[partId] = s.first to 0f }
        dragging = null; rowDropLine = null
    }
    /**
     * The round buttons let go in Edit Home: near Tile home's rows (and the home keeps a room for them in the stack) they
     * leave their free place and go into the order at the insertion line; anywhere else they stay where they were put.
     */
    fun dropButtons() {
        if (buttonsCentre == null) return
        val b = HomeEditState.bounds[HomeWidgets.SHORTCUTS] ?: return
        val slack = 120f * android.content.res.Resources.getSystem().displayMetrics.density
        if (!nearRows(b.top, b.bottom, slack)) return
        HomeEditState.live.remove(HomeWidgets.SHORTCUTS)
        insert(TileRows.BUTTONS, b.center.y)
    }
    /** The insertion line while the round buttons are dragged over the rows (root px), or null. Compose state. */
    var dropLine by androidx.compose.runtime.mutableStateOf<Float?>(null)

    fun update(shownNow: Set<String>, defaultNow: List<String>) { shown = shownNow; default = defaultNow; spans.keys.retainAll(shownNow) }

    fun order(): List<String> = TileRows.resolve(com.shelf.launcher.data.Features.tileRowOrder.value, default)

    /** The row an Edit Home part is (Tile home's own parts), or null. */
    fun rowOf(partId: String): String? = when (partId) {
        LayoutParts.id(com.shelf.launcher.data.HomeLayout.TILES, "row") -> TileRows.TILES
        LayoutParts.id(com.shelf.launcher.data.HomeLayout.TILES, CATS_PART) -> TileRows.CATS
        LayoutParts.id(com.shelf.launcher.data.HomeLayout.TILES, INFO_PART) -> TileRows.INFO
        else -> null
    }

    /**
     * The row ▲ / ▼ moves for the selected part [id]: Tile home's own rows, and (owner, Sprint 527: "simple with a controller")
     * the round buttons while they sit in the stack - not put somewhere of their own (saved or in this session's moves);
     * once moved freely (◀ ▶, or a drag away from the rows) they float as before.
     */
    fun movableRow(id: String): String? = rowOf(id) ?: if (
        id == HomeWidgets.SHORTCUTS && TileRows.BUTTONS in shown && HomeEditState.layout == com.shelf.launcher.data.HomeLayout.TILES &&
        HomeWidgets.position(HomeWidgets.SHORTCUTS) == null && HomeEditState.live[id] == null
    ) TileRows.BUTTONS else null

    /** [row] one place up / down among the shown rows. False at the top / bottom (nothing changes). */
    fun move(row: String, dir: Int): Boolean {
        val now = order()
        val next = TileRows.move(now, row, dir, shown)
        if (next == now) return false
        HomeEditSession.push("rows:$row", force = true)
        com.shelf.launcher.data.Features.tileRowOrder.value = TileRows.format(next)
        // (A move up / down of the old kind on this part is taken out: the order places it now, nothing is added on top.)
        partOf(row)?.let { id -> HomeEditState.liveShift[id]?.let { s -> HomeEditState.liveShift[id] = s.first to 0f } }
        return true
    }

    /** [row] put where a drop at [y] (root px) lands among the shown rows. */
    fun insert(row: String, y: Float) {
        val list = order().filter { it in shown || it == row }.mapNotNull { id -> spans[id]?.let { Triple(id, it.first, it.second) } }
        val before = TileRows.dropBefore(list, row, y)
        val next = TileRows.insertBefore(order(), row, before)
        if (next != order()) com.shelf.launcher.data.Features.tileRowOrder.value = TileRows.format(next)
    }

    /** The insertion line's y (root px) for a drop at [y]: the middle of the gap it lands in. */
    fun lineFor(row: String, y: Float): Float? {
        val list = order().filter { it in shown && it != row }.mapNotNull { id -> spans[id]?.let { Triple(id, it.first, it.second) } }
        if (list.isEmpty()) return null
        val before = TileRows.dropBefore(list, row, y)
        val i = list.indexOfFirst { it.first == before }
        return when {
            i < 0 -> list.last().third + 6f
            i == 0 -> list.first().second - 6f
            else -> (list[i - 1].third + list[i].second) / 2f
        }
    }

    /** Within reach of the rows for a drop (their span, a little room above and below). */
    fun nearRows(top: Float, bottom: Float, slack: Float): Boolean {
        val all = spans.values; if (all.isEmpty()) return false
        return bottom > all.minOf { it.first } - slack && top < all.maxOf { it.second } + slack
    }

    fun partOf(row: String): String? = when (row) {
        TileRows.TILES -> LayoutParts.id(com.shelf.launcher.data.HomeLayout.TILES, "row")
        TileRows.CATS -> LayoutParts.id(com.shelf.launcher.data.HomeLayout.TILES, CATS_PART)
        TileRows.INFO -> LayoutParts.id(com.shelf.launcher.data.HomeLayout.TILES, INFO_PART)
        TileRows.BUTTONS -> HomeWidgets.SHORTCUTS
        else -> null
    }

    /** "Reset this part" on a row: it goes back to its default place in the order (the others keep theirs). */
    fun resetRow(partId: String) {
        // (Flip 2 t527g: "Reset this part" on the round buttons put their free place back but left them where the order had
        // them - on Tile home it puts them back in their default place in the order too.)
        val row = rowOf(partId) ?: (if (partId == HomeWidgets.SHORTCUTS && HomeEditState.layout == com.shelf.launcher.data.HomeLayout.TILES) TileRows.BUTTONS else null) ?: return
        val saved = com.shelf.launcher.data.Features.tileRowOrder.value
        if (saved.isBlank()) return
        val without = TileRows.resolve(saved, default).filter { it != row }
        com.shelf.launcher.data.Features.tileRowOrder.value = TileRows.format(TileRows.resolve(TileRows.format(without), default))
    }
}

/**
 * Sprint 527: one of Tile home's rows in the stack - its place recorded (TileRowEdit.spans); while Edit Home is open it
 * glides to a new place when the order changes (the others make room), and the selected row shows where ▲ / ▼ would put it.
 * Outside Edit Home nothing animates (a category change never moves the shell - rule 14).
 */
@androidx.compose.runtime.Composable
internal fun androidx.compose.ui.Modifier.tileRowPlacement(row: String): androidx.compose.ui.Modifier {
    val editing = HomeEditState.active
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val anim = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    val last = androidx.compose.runtime.remember { floatArrayOf(Float.NaN) }
    val accent = com.shelf.launcher.ui.theme.ShelfTheme.accent
    val selectedHere = editing && TileRowEdit.partOf(row)?.let { it == HomeEditState.selected } == true
    return this
        .onGloballyPositioned { c ->
            val y = c.positionInRoot().y
            TileRowEdit.spans[row] = y to (y + c.size.height)
            val was = last[0]; last[0] = y
            if (editing && !was.isNaN() && kotlin.math.abs(was - y) > 1f) {
                // (it was drawn at [was]: start there and glide to the new place)
                scope.launch {
                    anim.snapTo(anim.value + (was - y))
                    anim.animateTo(0f, androidx.compose.animation.core.tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                }
            }
        }
        .graphicsLayer { translationY = if (editing) anim.value else 0f }
        .drawWithContent {
            drawContent()
            if (!selectedHere || HomeEditState.preview) return@drawWithContent
            // Where ▲ / ▼ would put it: a faint line in the gap above and below, with a small arrow.
            val w = size.width * 0.34f; val x0 = (size.width - w) / 2f
            val c = accent.copy(alpha = 0.55f)
            for ((y, up) in listOf(-5.dp.toPx() to true, size.height + 5.dp.toPx() to false)) {
                drawLine(c, androidx.compose.ui.geometry.Offset(x0, y), androidx.compose.ui.geometry.Offset(x0 + w, y), 1.5.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 7f)))
                val a = 5.dp.toPx(); val mx = size.width / 2f; val dy = if (up) -a else a
                val p = androidx.compose.ui.graphics.Path().apply { moveTo(mx - a, y); lineTo(mx + a, y); lineTo(mx, y + dy); close() }
                drawPath(p, c)
            }
        }
}
