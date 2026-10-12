package com.shelf.launcher.ui

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
    val coverSlides: List<String> = emptyList()
)

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
    /** Hold a tile (touch): pick your own picture for it. */
    onPickImage: (Int) -> Unit = {}
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
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(if (style == com.shelf.launcher.data.CategoryTileStyle.LARGE) 14.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text("Choose a category", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "◀ ▶ ▲ ▼ Choose · A Open · Y (or hold) Your own picture · X Remove it · B Back",
                        color = ShelfTheme.textMuted, fontSize = 12.sp
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
            itemsIndexed(tiles, key = { _, t -> t.key }) { index, tile ->
                if (style == com.shelf.launcher.data.CategoryTileStyle.TILES) {
                    TileLookTile(tile, art, selected = index == selectedIndex, onClick = { onPick(index) }, onLongPress = { onPickImage(index) })
                } else if (style == com.shelf.launcher.data.CategoryTileStyle.COMPACT) {
                    CompactTile(tile, art, selected = index == selectedIndex, onClick = { onPick(index) }, onLongPress = { onPickImage(index) })
                } else {
                    Tile(tile, art, selected = index == selectedIndex, onClick = { onPick(index) }, modifier = Modifier, onLongPress = { onPickImage(index) })
                }
            }
        }
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
    val latestLong by rememberUpdatedState(onLongPress)
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
                .pointerInput(Unit) { detectTapGestures(onLongPress = { latestLong() }) { latest() } }
        ) {
            CategoryArt(tile, CardShape.SQUARE, Modifier.matchParentSize(), art)
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
    val latestLong by rememberUpdatedState(onLongPress)
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
                .pointerInput(Unit) { detectTapGestures(onLongPress = { latestLong() }) { latest() } }
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
    return buildList {
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
                            lastPlayedArt = wideOf(lastOf(inIt) ?: inIt.firstOrNull()), coverSlides = coverSlidesOf(inIt)
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
    }
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
    val latestLong by rememberUpdatedState(onLongPress)
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
                .pointerInput(Unit) { detectTapGestures(onLongPress = { latestLong() }) { latest() } },
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
