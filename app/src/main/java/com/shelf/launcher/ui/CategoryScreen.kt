package com.shelf.launcher.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.ui.theme.ShelfTheme

/** One tile on the category screen. */
data class CategoryTile(
    /** "ALL", a special category's name, or "p:<platform>". */
    val key: String,
    val label: String,
    val subtitle: String?,
    /** A few covers from the category, fanned out on the tile. */
    val covers: List<String>,
    val hue: Int,
    /** Wide art (capsules / backgrounds) for the slideshow. */
    val slides: List<String> = emptyList(),
    /** Your own picture for this tile, if you set one. */
    val customImage: String? = null,
    /** Games in it, and how many of them are favourites (platform browser). */
    val count: Int = 0,
    val favorites: Int = 0,
    /** The console's key ("SNES") for platform tiles; null for All / special / collection tiles. */
    val platform: String? = null,
    /** The cover of the game played most recently in it (the slide's last-played card). */
    val lastPlayedCover: String? = null,
    val lastPlayedTitle: String? = null,
    /** Wide art (capsule / hero) of the game played most recently in it, else of its first game ("Last played game"). */
    val lastPlayedArt: String? = null,
    /** Covers of its games for the slow fade on tall / square cards (up to 12). */
    val coverSlides: List<String> = emptyList(),
    /** Update 173: an app in it whose icon stands in on a tile without covers (Android games: the tile was blank). */
    val appIcon: String? = null
)

/**
 * Update 208: Choose a category › Reorder — move categories and consoles round. Start (or the Reorder button in the
 * corner) turns it on; A picks the tile you're on up and puts it down, the D-pad moves it while it's held, B puts it
 * down / finishes. On the touch screen, drag a tile to its new place while it's on. Consoles keep your order
 * (PlatformOrder, also L2 / R2's); All, Favorites, Recent… and the consoles' and collections' blocks move as groups
 * (Settings › Categories order).
 * Update 212: holding a tile (touch) picks it up straight away — Reorder turns on, and the same hold drags it; near the
 * top or bottom edge the list scrolls on. The tile you're moving is followed by its key, whatever order comes next. Its
 * picture moved to the tile's options (Y, or the Options button): Change picture / Reset picture / Reorder.
 */
internal object CategoryReorder {
    var on by androidx.compose.runtime.mutableStateOf(false)
    private var heldNow by androidx.compose.runtime.mutableStateOf(false)
    /**
     * A tile is picked up (the D-pad moves it). Update 214: picking it up remembers the order as it was, so B puts it
     * all back (cancel); A puts it down where it is (keep).
     */
    var held: Boolean
        get() = heldNow
        set(v) {
            if (v && !heldNow) { savedTiles = com.shelf.launcher.data.CategoryTileOrder.snapshot(); savedPlatforms = com.shelf.launcher.data.PlatformOrder.snapshot() }
            if (!v) { savedTiles = null; savedPlatforms = null }
            heldNow = v
        }
    private var savedTiles: List<String>? = null
    private var savedPlatforms: List<String>? = null
    /** Update 214: B with a tile picked up — the order as it was when it was picked up, and the tile put down. */
    fun cancel() {
        val t = savedTiles; val pl = savedPlatforms
        if (t != null) com.shelf.launcher.data.CategoryTileOrder.restore(t)
        if (pl != null) com.shelf.launcher.data.PlatformOrder.restore(pl)
        held = false
    }
    fun stop() { on = false; held = false }
}

/** Update 212: a tile being dragged on the category screens (by its key), the finger, and the edge scroll (px a frame). */
internal class DragState {
    var key by androidx.compose.runtime.mutableStateOf<String?>(null)
    var pos by androidx.compose.runtime.mutableStateOf<androidx.compose.ui.geometry.Offset?>(null)
    var edge by androidx.compose.runtime.mutableFloatStateOf(0f)
}

/** Update 212: the drag handle on a tile that can be moved (reordering). */
@Composable
internal fun DragHandleBadge(modifier: Modifier) {
    androidx.compose.material3.Icon(
        androidx.compose.material.icons.Icons.Filled.DragIndicator, "Drag to move", tint = Color.White,
        modifier = modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.55f)).padding(3.dp)
            .then(Modifier.width(18.dp).height(18.dp))
    )
}

/** Columns on the category screen — the D-pad moves by this much up / down.
 * One source of truth: the tile style (Appearance › Game row). */
fun categoryColumns(style: com.shelf.launcher.data.CategoryTileStyle): Int = style.columns

/**
 * Every category as a tile, picked with the D-pad and A — like choosing a
 * console on a shelf, instead of stepping through them with L2 / R2. Each
 * console tile shows a few of its own covers and its full name. Tiles are laid
 * out lazily, so only the ones on screen are drawn (and only those run a
 * slideshow).
 */
@Composable
fun CategoryScreen(
    tiles: List<CategoryTile>,
    selectedIndex: Int,
    onPick: (Int) -> Unit,
    art: com.shelf.launcher.data.CategoryTileArt = com.shelf.launcher.data.CategoryTileArt.SLIDESHOW,
    style: com.shelf.launcher.data.CategoryTileStyle = com.shelf.launcher.data.CategoryTileStyle.LARGE,
    /** Update 212: Y / the Options button — the tile's options (its picture, Reorder). */
    onOptions: (Int) -> Unit = {},
    /** Update 208: moves the tile at the first index to the second (true when it moved). */
    onMove: (Int, Int) -> Boolean = { _, _ -> false },
    /** Update 208: a tile tapped while reordering becomes the one you're on. */
    onSelect: (Int) -> Unit = {}
) {
    com.shelf.launcher.Breadcrumbs.mark("CategoryScreen")
    val columns = categoryColumns(style)
    val gridState = rememberLazyGridState()
    // Keep the highlighted tile in view (the header is item 0).
    LaunchedEffect(selectedIndex, columns, tiles.size) {
        val target = selectedIndex + 1
        val info = gridState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == target }
        if (item == null) {
            if (tiles.isNotEmpty()) gridState.animateScrollToItem(if (selectedIndex < columns) 0 else target)
        } else {
            val top = item.offset.y
            val bottom = top + item.size.height
            val viewportEnd = info.viewportSize.height - info.afterContentPadding
            when {
                top < info.beforeContentPadding -> gridState.animateScrollBy((top - info.beforeContentPadding).toFloat())
                bottom > viewportEnd -> gridState.animateScrollBy((bottom - viewportEnd).toFloat())
            }
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(ShelfTheme.background.copy(alpha = 0.94f))
    ) {
        val reorder = CategoryReorder.on
        val latestMove by rememberUpdatedState(onMove)
        val latestSelect by rememberUpdatedState(onSelect)
        val latestTiles by rememberUpdatedState(tiles)
        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
        // Update 212: the tile being dragged (by its key — it keeps it whatever order comes next), where the finger is,
        // and how fast the list scrolls on while the finger is near its top or bottom edge (px a frame).
        val drag = androidx.compose.runtime.remember { DragState() }
        fun tileAt(p: androidx.compose.ui.geometry.Offset): Int? = gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
            info.index > 0 && p.x >= info.offset.x && p.x < info.offset.x + info.size.width &&
                p.y >= info.offset.y && p.y < info.offset.y + info.size.height
        }?.index?.minus(1)
        fun moveUnder(p: androidx.compose.ui.geometry.Offset) {
            val k = drag.key ?: return
            val from = latestTiles.indexOfFirst { it.key == k }
            val to = tileAt(p) ?: return
            if (from >= 0 && to != from && latestMove(from, to)) latestSelect(to)
        }
        LaunchedEffect(drag.edge != 0f) {
            while (drag.edge != 0f && drag.key != null) {
                androidx.compose.runtime.withFrameNanos { }
                gridState.scrollBy(drag.edge)
                drag.pos?.let { moveUnder(it) }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            // (Update 208: while reordering, a drag moves the tile under your finger to where you drag it. Update 212: a
            // hold picks it up first — Reorder turns on — and the same touch drags it.)
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                val slop = viewConfiguration.touchSlop
                val holdMs = viewConfiguration.longPressTimeoutMillis
                val edgePx = 56.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    val start = tileAt(down.position) ?: return@awaitEachGesture
                    if (!CategoryReorder.on) {
                        // Not reordering: a hold (no move) picks it up; a tap or a scroll goes on as usual.
                        val ended = withTimeoutOrNull(holdMs) {
                            var stop = false
                            while (!stop) {
                                val c = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                                if (c == null || !c.pressed || (c.position - down.position).getDistance() > slop) stop = true
                            }
                            true
                        }
                        if (ended == true) return@awaitEachGesture
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        CategoryReorder.on = true
                    } else {
                        // Reordering: it's dragged once it moves (a tap still picks it up / puts it down).
                        var moved = false
                        while (!moved) {
                            val c = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                            if (c == null || !c.pressed) return@awaitEachGesture
                            if ((c.position - down.position).getDistance() > slop) moved = true
                        }
                    }
                    latestSelect(start)
                    CategoryReorder.held = true
                    drag.key = latestTiles.getOrNull(start)?.key
                    while (true) {
                        val c = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                        c.consume()
                        if (!c.pressed) break
                        drag.pos = c.position
                        drag.edge = when {
                            c.position.y < edgePx -> -(edgePx - c.position.y).coerceAtMost(edgePx) / 4f
                            c.position.y > size.height - edgePx -> (c.position.y - (size.height - edgePx)).coerceAtMost(edgePx) / 4f
                            else -> 0f
                        }
                        moveUnder(c.position)
                    }
                    // Put down where it is (Reorder stays on: Done, B or Start finishes).
                    drag.edge = 0f; drag.key = null; drag.pos = null
                    CategoryReorder.held = false
                }
            },
            // (Update 208: no scrolling by touch while reordering — a drag moves a tile then; the D-pad still scrolls to it.)
            userScrollEnabled = !reorder,
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(if (style == com.shelf.launcher.data.CategoryTileStyle.LARGE) 14.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (reorder) "Reorder categories" else "Choose a category", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (reorder) "A Pick up / put down · ◀ ▶ ▲ ▼ Move it · Drag a tile (hold) to move it · B or Start Done"
                            else "◀ ▶ ▲ ▼ Choose · A Open · Y Options (picture) · Start or hold a tile Reorder · B Back",
                            color = ShelfTheme.textMuted, fontSize = 12.sp
                        )
                        // (Update 212: how the order works, said where it's changed.)
                        if (reorder) Text(
                            "Consoles move among the consoles. All, Favorites, Recent… and the consoles' and collections' blocks move as whole groups (Settings › Categories › Which categories).",
                            color = ShelfTheme.textMuted, fontSize = 11.sp
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    // (Update 208: the Reorder button in the corner. Update 212: and the tile's Options.)
                    if (!reorder) {
                        Chip("Options", accent = false) { onOptions(selectedIndex) }
                        Spacer(Modifier.width(8.dp))
                    }
                    Chip(if (reorder) "Done" else "Reorder", accent = reorder) {
                        if (CategoryReorder.on) CategoryReorder.stop() else CategoryReorder.on = true
                    }
                }
            }
            itemsIndexed(tiles, key = { _, t -> t.key }) { index, tile ->
                val heldHere = reorder && CategoryReorder.held && index == selectedIndex
                // (Update 208: tiles glide to their new places while reordering; the one held is lifted.)
                Box(Modifier.animateItem().graphicsLayer {
                    val k = if (heldHere) 1.06f else 1f
                    scaleX = k; scaleY = k
                    alpha = if (reorder && !heldHere && CategoryReorder.held) 0.75f else 1f
                }) {
                val pick: () -> Unit = { if (reorder) { onSelect(index); CategoryReorder.held = !CategoryReorder.held || selectedIndex != index } else onPick(index) }
                if (style == com.shelf.launcher.data.CategoryTileStyle.TILES) {
                    TileLookTile(tile, art, selected = index == selectedIndex, onClick = pick)
                } else if (style == com.shelf.launcher.data.CategoryTileStyle.COMPACT) {
                    CompactTile(tile, art, selected = index == selectedIndex, onClick = pick)
                } else {
                    Tile(tile, art, selected = index == selectedIndex, onClick = pick, modifier = Modifier)
                }
                // (Update 212: while reordering, each tile's drag handle.)
                if (reorder) DragHandleBadge(Modifier.align(Alignment.TopEnd).padding(6.dp))
                }
            }
        }
        // (Update 233, #94: scrolled down, the heading and the tiles ran under the status bar's clock and icons — the
        // top now fades into the background there, as the rest of the screen.)
        val scrolledDown by androidx.compose.runtime.remember {
            androidx.compose.runtime.derivedStateOf { gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 24 }
        }
        if (scrolledDown) Box(
            Modifier.fillMaxWidth().height(44.dp).align(Alignment.TopCenter).background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to ShelfTheme.background.copy(alpha = 0.97f), 0.6f to ShelfTheme.background.copy(alpha = 0.85f), 1f to Color.Transparent
                )
            )
        )
    }
}

/** Tile-style: a small rounded card with the category's cover, its name under it. */
@Composable
private fun CompactTile(
    tile: CategoryTile,
    art: com.shelf.launcher.data.CategoryTileArt,
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {}
) {
    val shape = uiRounded(LocalCardLook.current.cornerDp.dp + 4.dp)
    val latest by rememberUpdatedState(onClick)
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color.hsl(tile.hue.toFloat(), 0.45f, 0.28f),
                            Color.hsl(((tile.hue + 40) % 360).toFloat(), 0.4f, 0.1f)
                        )
                    )
                )
                .border(if (selected) 3.dp else 0.dp, if (selected) ShelfTheme.accent else Color.Transparent, shape)
                .pointerInput(Unit) { detectTapGestures { latest() } }
        ) {
            CategoryArt(tile, CardShape.SQUARE, Modifier.matchParentSize(), art)
            // (Update 173: no covers in it — its app's icon, not a blank tile.)
            if (tile.covers.isEmpty() && tile.customImage == null && tile.appIcon != null) {
                AppIconArt(tile.appIcon, wide = false, modifier = Modifier.matchParentSize())
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            tile.label,
            color = if (selected) ShelfTheme.accent else Color.White,
            fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp),
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = androidx.compose.ui.unit.TextUnit(1.167f, androidx.compose.ui.unit.TextUnitType.Em)
        )
        tile.subtitle?.let {
            Text(it, color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(10.sp), maxFontSize = 10.sp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Tile(
    tile: CategoryTile,
    art: com.shelf.launcher.data.CategoryTileArt,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    onLongPress: () -> Unit = {}
) {
    val shape = uiRounded(LocalCardLook.current.cornerDp.dp + 4.dp)
    val latest by rememberUpdatedState(onClick)
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color.hsl(tile.hue.toFloat(), 0.45f, 0.28f),
                            Color.hsl(((tile.hue + 40) % 360).toFloat(), 0.4f, 0.1f)
                        )
                    )
                )
                .border(if (selected) 3.dp else 0.dp, if (selected) ShelfTheme.accent else Color.Transparent, shape)
                .pointerInput(Unit) { detectTapGestures { latest() } }
        ) {
            // Your own picture, else what Settings › Categories › "Cards show" picks (CategoryArt.kt).
            CategoryArt(tile, CardShape.WIDE, Modifier.matchParentSize(), art)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
            )
            // With the cover fan on the right, the name keeps to the left part so it never runs under the covers.
            val fan = categoryArtIsFan(tile, art)
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .then(if (fan) Modifier.fillMaxWidth(0.58f) else Modifier)
                    .padding(12.dp)
            ) {
                Text(
                    tile.label,
                    color = Color.White,
                    fontSize = 15.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(15.sp), maxFontSize = 15.sp),
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Em)
                )
                tile.subtitle?.let {
                    Text(it, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * Works out every category tile. Meant for a background thread (Dispatchers.Default):
 * it runs each category's filter, which the state memoizes (see LauncherUiState),
 * so repeat calls with unchanged inputs are cheap.
 */
fun buildCategoryTiles(
    state: com.shelf.launcher.LauncherUiState,
    fullNames: Boolean,
    categoryImages: Map<String, String>
): List<CategoryTile> {
    fun coversOf(list: List<com.shelf.launcher.data.Game>) = list.asSequence()
        .mapNotNull { g -> state.artOverrides[g.stableKey]?.cover ?: state.art[g.stableKey]?.gridUrl }
        .take(3).toList()
    // Wide art for the slideshow: capsules first, backgrounds otherwise.
    fun slidesOf(list: List<com.shelf.launcher.data.Game>) = list.asSequence()
        .mapNotNull { g ->
            val o = state.artOverrides[g.stableKey]; val a = state.art[g.stableKey]
            o?.wide ?: a?.wideGridUrl ?: o?.hero ?: a?.heroUrl
        }
        .take(12).toList()
    fun hueOf(key: String) = (key.hashCode() and 0x7fffffff) % 360
    fun favsOf(list: List<com.shelf.launcher.data.Game>) = list.count { it.stableKey in state.favorites }
    fun lastOf(list: List<com.shelf.launcher.data.Game>) =
        list.filter { (state.lastPlayedAt[it.stableKey] ?: 0L) > 0L }.maxByOrNull { state.lastPlayedAt[it.stableKey] ?: 0L }
    fun coverOf(g: com.shelf.launcher.data.Game?) =
        g?.let { state.artOverrides[it.stableKey]?.cover ?: state.art[it.stableKey]?.gridUrl }
    // "Last played game": the most recent one's wide art (the first game when none was played), cover as a fallback.
    fun wideOf(g: com.shelf.launcher.data.Game?) = g?.let {
        val o = state.artOverrides[it.stableKey]; val a = state.art[it.stableKey]
        o?.wide ?: a?.wideGridUrl ?: o?.hero ?: a?.heroUrl ?: o?.cover ?: a?.gridUrl
    }
    fun coverSlidesOf(list: List<com.shelf.launcher.data.Game>) = list.asSequence()
        .mapNotNull { g -> state.artOverrides[g.stableKey]?.cover ?: state.art[g.stableKey]?.gridUrl }
        .take(12).toList()
    // (Update 214: in your own order of the tiles, each one on its own — CategoryTileOrder.)
    return com.shelf.launcher.data.CategoryTileOrder.sort(buildList {
        state.categoryOrder.forEach { key ->
            when (key) {
                com.shelf.launcher.data.CategorySettings.ALL ->
                    add(
                        CategoryTile(
                            "ALL", "All games", gamesCount(state.games.size), coversOf(state.games), 210,
                            slides = slidesOf(state.games.shuffled(java.util.Random(7))), customImage = categoryImages["ALL"],
                            count = state.games.size, favorites = favsOf(state.games),
                            lastPlayedCover = coverOf(lastOf(state.games)), lastPlayedTitle = lastOf(state.games)?.name,
                            lastPlayedArt = wideOf(lastOf(state.games) ?: state.games.firstOrNull()), coverSlides = coverSlidesOf(state.games)
                        )
                    )
                com.shelf.launcher.data.CategorySettings.CONSOLES -> com.shelf.launcher.data.PlatformOrder.sort(state.availablePlatforms).forEach { p ->
                    // Same as the row shows for it (Android games, apps and all).
                    val inIt = state.copy(platformFilter = p, specialFilter = null, searchQuery = "").visibleGames
                    val label = when (p) {
                        com.shelf.launcher.ANDROID_GAMES_PLATFORM -> "Android games"
                        "App" -> "Other apps"
                        else -> if (fullNames) com.shelf.launcher.data.ConsoleNames.display(p) else p
                    }
                    add(
                        CategoryTile(
                            "p:$p", label, gamesCount(inIt.size), coversOf(inIt), hueOf(p),
                            slides = slidesOf(inIt), customImage = categoryImages["p:$p"],
                            count = inIt.size, favorites = favsOf(inIt), platform = p,
                            lastPlayedCover = coverOf(lastOf(inIt)), lastPlayedTitle = lastOf(inIt)?.name,
                            lastPlayedArt = wideOf(lastOf(inIt) ?: inIt.firstOrNull()), coverSlides = coverSlidesOf(inIt),
                            appIcon = (lastOf(inIt) ?: inIt.firstOrNull())?.takeIf { it.sourceType == com.shelf.launcher.data.SourceType.APP }?.packageName
                                ?: inIt.firstOrNull { it.sourceType == com.shelf.launcher.data.SourceType.APP }?.packageName
                        )
                    )
                }
                com.shelf.launcher.data.CategorySettings.COLLECTIONS -> state.collections.forEach { c ->
                    val inIt = state.copy(platformFilter = c.key, specialFilter = null, searchQuery = "").visibleGames
                    if (inIt.isNotEmpty()) add(
                        CategoryTile(
                            "p:${c.key}", c.name, gamesCount(inIt.size), coversOf(inIt), hueOf(c.key),
                            slides = slidesOf(inIt), customImage = categoryImages["p:${c.key}"],
                            count = inIt.size, favorites = favsOf(inIt),
                            lastPlayedCover = coverOf(lastOf(inIt)), lastPlayedTitle = lastOf(inIt)?.name,
                            lastPlayedArt = wideOf(lastOf(inIt) ?: inIt.firstOrNull()), coverSlides = coverSlidesOf(inIt)
                        )
                    )
                }
                else -> runCatching { com.shelf.launcher.SpecialFilter.valueOf(key) }.getOrNull()?.let { f ->
                    // Favorites, Recent, In progress…: their own games, worked out the
                    // same way the row does it.
                    val inIt = state.copy(specialFilter = f, platformFilter = null, searchQuery = "").visibleGames
                    add(
                        CategoryTile(
                            key, f.label, gamesCount(inIt.size), coversOf(inIt), hueOf(key),
                            slides = slidesOf(inIt), customImage = categoryImages[key],
                            count = inIt.size, favorites = favsOf(inIt),
                            lastPlayedCover = coverOf(lastOf(inIt)), lastPlayedTitle = lastOf(inIt)?.name,
                            lastPlayedArt = wideOf(lastOf(inIt) ?: inIt.firstOrNull()), coverSlides = coverSlidesOf(inIt)
                        )
                    )
                }
            }
        }
    }) { it.key }
}

/**
 * The Tiles home's look for a category: a rounded square with its console photo or covers, the
 * focused one a little bigger with a ring in the accent colour and a soft glow, its name centred
 * under it.
 */
@Composable
private fun TileLookTile(
    tile: CategoryTile,
    art: com.shelf.launcher.data.CategoryTileArt,
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {}
) {
    val corner = LocalCardLook.current.cornerDp.dp + 8.dp
    val shape = uiRounded(corner)
    val latest by rememberUpdatedState(onClick)
    val plain = tile.customImage == null && art == com.shelf.launcher.data.CategoryTileArt.PLAIN
    val scale by androidx.compose.animation.core.animateFloatAsState(if (selected) 1.08f else 0.94f, label = "tileScale")
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .then(if (selected) Modifier.shadowGlow(ShelfTheme.accent, corner) else Modifier)
                .clip(shape)
                .background(Brush.linearGradient(listOf(Color.hsl(tile.hue.toFloat(), 0.45f, 0.3f), Color.hsl(((tile.hue + 40) % 360).toFloat(), 0.4f, 0.12f))))
                .border(if (selected) 3.dp else 1.dp, if (selected) ShelfTheme.accent else Color.White.copy(alpha = 0.08f), shape)
                .pointerInput(Unit) { detectTapGestures { latest() } },
            contentAlignment = Alignment.Center
        ) {
            if (plain) Text(tile.label.take(2), color = Color.White.copy(alpha = 0.85f), fontSize = 28.sp, fontWeight = FontWeight.Bold)
            else CategoryArt(tile, CardShape.SQUARE, Modifier.matchParentSize(), art)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            tile.label,
            color = if (selected) ShelfTheme.accent else Color.White,
            fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        tile.subtitle?.let { Text(it, color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(10.sp), maxFontSize = 10.sp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
    }
}

/** A soft glow round a rounded shape (focused tiles). */
private fun Modifier.shadowGlow(color: Color, corner: androidx.compose.ui.unit.Dp): Modifier = this.drawBehind {
    val r = corner.toPx()
    for (i in 3 downTo 1) {
        val g = i * 4.dp.toPx()
        drawRoundRect(
            color.copy(alpha = 0.08f * (4 - i)),
            topLeft = androidx.compose.ui.geometry.Offset(-g, -g),
            size = androidx.compose.ui.geometry.Size(size.width + 2 * g, size.height + 2 * g),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r + g)
        )
    }
}
