package com.shelf.launcher.ui

import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Close
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.layout
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.ArtOverride
import com.shelf.launcher.data.CardArtType
import com.shelf.launcher.data.CardSize
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.GameArt
import com.shelf.launcher.data.RaGameProgress
import com.shelf.launcher.data.SourceType
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlin.math.abs
import kotlin.math.floor

/*
 * The Tile-style home screen (Appearance › Home layout): one row of big
 * square tiles, the name of the game you're on written above its tile in the
 * accent colour, a thick glowing ring around it, and a pill of round shortcut
 * buttons under the row. The selection moves across the visible tiles and the
 * row only scrolls when it reaches an edge, like a console home menu.
 * Wide capsules never appear here.
 */

/** Tile side as a share of the screen height, per Card size. */
private fun tileFraction(size: CardSize): Float = when (size) {
    CardSize.SMALL -> 0.29f
    CardSize.MEDIUM -> 0.34f
    CardSize.LARGE -> 0.40f
}

/** Tile home size (Appearance › Game row › Tile size), 1 = the Card size default. Name, round buttons and pills follow it. */
internal val LocalTileScale = androidx.compose.runtime.staticCompositionLocalOf { 1f }

/** Geometry of the row, shared by the tiles, the name above them and the touch handling. */
internal class TileRowMetrics(
    val tile: Dp,
    val spacing: Dp,
    val start: Dp,
    val visible: Int
) {
    val stride: Dp get() = tile + spacing
}

@Composable
internal fun tileRowMetrics(size: CardSize): TileRowMetrics {
    val screen = screenSizeDp()
    // Never so big that fewer than four tiles fit across.
    val scale = LocalTileScale.current
    val start = maxOf(24.dp, screen.width * 0.065f)
    val byHeight = screen.height * tileFraction(size) * scale
    val spacingFor = { t: Dp -> maxOf(10.dp, t * 0.075f) }
    // At the default size at least ~4 tiles fit across; bigger sizes may show fewer (never under ~1.6),
    // and never taller than about two thirds of the screen.
    val across = (4.2f / scale).coerceIn(1.6f, 4.2f)
    val maxByWidth = minOf((screen.width - start * 2 - spacingFor(byHeight) * 3) / across, screen.height * 0.62f)
    // Layout options › Games across the screen: exactly that many tiles across (capped at two thirds of the height).
    val n = com.shelf.launcher.data.Features.rowGamesFor(com.shelf.launcher.data.HomeLayout.TILES)
    val tile = if (n > 0) minOf((screen.width - start * 2) / (n + 0.075f * (n - 1)), screen.height * 0.62f)
        else minOf(byHeight, maxByWidth)
    val spacing = spacingFor(tile)
    val visible = floor(((screen.width - start * 2 + spacing) / (tile + spacing)).toDouble()).toInt().coerceAtLeast(1)
    return TileRowMetrics(tile, spacing, start, visible)
}

/**
 * Where the focused game's name sits on the tile home, shared with the category label so the two
 * always line up — the name follows the selected tile (it moves across the row with the default
 * "Edges" setting, and the row isn't always exactly centred on screen), and a label pinned to the
 * screen edge or centre drifted away from it.
 */
@androidx.compose.runtime.Stable
internal class TileNameAnchor {
    internal var nameX by mutableStateOf<Animatable<Float, androidx.compose.animation.core.AnimationVector1D>?>(null)
    internal var scroll by mutableStateOf<Animatable<Float, androidx.compose.animation.core.AnimationVector1D>?>(null)
    internal var metrics by mutableStateOf<TileRowMetrics?>(null)
    /** -1 left edge, 0 centred on the tile, 1 right edge. */
    internal var align by mutableStateOf(-1)
    /** How many lines the focused game's name takes (1 or 2): with one, the category moves down to it. */
    internal var nameLines by mutableStateOf(1)

    /**
     * The x (px) at which something [widthPx] wide starts when lined up like the game name, kept
     * 8 dp inside the screen; null until the row has been laid out. Read it in a layout block: it
     * follows the row's slide frame by frame.
     */
    fun startPx(density: androidx.compose.ui.unit.Density, widthPx: Int, screenWidthPx: Float, inset: Dp = 0.dp): Float? = with(density) {
        val m = metrics ?: return null
        val nx = nameX?.value ?: return null
        val sc = scroll?.value ?: return null
        val tileX = (m.start.value + nx - sc).dp.toPx()
        val tileW = m.tile.toPx()
        val x = when (align) {
            0 -> tileX + tileW / 2f - widthPx / 2f
            1 -> tileX + tileW - widthPx
            else -> tileX + 2.dp.toPx() - inset.toPx()
        }.let { if (align == 1) it + inset.toPx() else it }
        x.coerceIn(8.dp.toPx() - inset.toPx(), maxOf(8.dp.toPx(), screenWidthPx - widthPx - 8.dp.toPx()))
    }
}

/**
 * Places this (full-width) item's content on the [TileNameAnchor] line; plain start until it's known.
 * [inset]: how far the content's own backdrop pushes its text in, so the text (not the pill) lines up with the name.
 */
internal fun Modifier.onTileNameLine(anchor: TileNameAnchor, screenWidthPx: Float, inset: Dp = 0.dp): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0))
    val w = if (constraints.hasBoundedWidth) constraints.maxWidth else p.width
    val density: androidx.compose.ui.unit.Density = this
    layout(w, p.height) {
        val x = anchor.startPx(density, p.width, screenWidthPx, inset) ?: 0f
        p.place(x.toInt(), 0)
    }
}

/**
 * The row of square tiles with the focused game's name above it. [focusedIndex]
 * is the game you're on; taps pick a tile (tapping the picked one opens it),
 * horizontal swipes step through the row.
 */
@Composable
internal fun TileHomeRow(
    /** When each game was last played (Living Library). */
    lastPlayedAt: Map<String, Long> = emptyMap(),
    games: List<Game>,
    art: Map<String, GameArt>,
    artOverrides: Map<String, ArtOverride>,
    focusedIndex: Int,
    raProgress: Map<String, RaGameProgress>,
    favorites: Set<String>,
    cardArtType: CardArtType,
    cardSize: CardSize,
    /** Selection is on the shortcut buttons: the ring dims, the name stays. */
    dimmed: Boolean,
    onFocus: (Int) -> Unit,
    onOpen: (Game) -> Unit,
    physicalMedia: Map<String, List<Pair<String, String>>> = emptyMap(),
    screenshots: Map<String, List<String>> = emptyMap(),
    /** Mastered / completed games (they shimmer like on the classic row). */
    awards: Map<String, com.shelf.launcher.data.RaAward> = emptyMap(),
    /** The game's name under the tiles instead of above (tiles at the top of the screen). */
    nameBelow: Boolean = false,
    /** Where the selected tile sits. */
    focusAnchor: com.shelf.launcher.data.TileFocus = com.shelf.launcher.data.TileFocus.EDGES,
    /** Shared with the category label so it sits exactly where the name does. */
    nameAnchor: TileNameAnchor? = null,
    /** Where the tiles start (root px), for "Whole picture" (the hero ends right above them). */
    onTilesTop: ((Float) -> Unit)? = null,
    /** Asks for a game's box / label scans (only for the few games around the focus). */
    onNeedPhysical: (Game) -> Unit = {},
    /** Asks for a game's screenshots (Screenshot card art). */
    onNeedScreenshots: (Game) -> Unit = {}
) {
    com.shelf.launcher.Breadcrumbs.mark("TileHomeRow")
    // Box / label scans (and screenshots) for the tiles around the one you're on, after the selection rests a
    // moment, one at a time — as the other layouts do (the lookups are remembered by the view model).
    androidx.compose.runtime.LaunchedEffect(focusedIndex, cardArtType, games.size) {
        val shots = cardArtType == CardArtType.SCREENSHOT
        if (!shots && !cardArtType.wantsMedia()) return@LaunchedEffect
        kotlinx.coroutines.delay(150)
        for (i in intArrayOf(focusedIndex, focusedIndex + 1, focusedIndex - 1, focusedIndex + 2, focusedIndex - 2, focusedIndex + 3)) {
            val g = games.getOrNull(i) ?: continue
            if (shots) { if (!screenshots.containsKey(g.stableKey)) { onNeedScreenshots(g); kotlinx.coroutines.delay(100) } }
            else if (!physicalMedia.containsKey(g.stableKey)) { onNeedPhysical(g); kotlinx.coroutines.delay(100) }
        }
    }
    val seenAll by com.shelf.launcher.data.AchievementSeen.all.collectAsState()
    val hiddenMedia = LocalHiddenMedia.current
    // Games from several consoles: box art is the plain cover, the same size on every tile.
    val mixedRow = remember(games) { mixedConsoles(games) }
    // "Box art · real sizes": every case against the biggest one in the row.
    val realRef = remember(games, cardArtType) { realCaseRef(cardArtType, games) }
    // "Carousel look" (Appearance › Game row): the tile you're on in the middle, the next ones each side smaller,
    // tucked behind it and optionally turned away (Carousel); the name sits centred over it.
    val carousel = com.shelf.launcher.data.HomeLayout.TILES.name in com.shelf.launcher.data.Features.carouselLayouts.state.value
    @Suppress("NAME_SHADOWING") val focusAnchor = if (carousel) com.shelf.launcher.data.TileFocus.CENTER else focusAnchor
    val m = tileRowMetrics(cardSize)
    val strideDpValue = m.stride.value
    if (games.isNotEmpty()) {
        val n = games.size
        val focus = focusedIndex.coerceIn(0, n - 1)
        // First fully visible tile: moves only when the selection reaches an edge.
        val window = remember(n, m.visible) { intArrayOf(0) }
        var first = window[0]
        if (focus < first) first = focus
        if (focus > first + m.visible - 1) first = focus - m.visible + 1
        first = first.coerceIn(0, maxOf(0, n - m.visible))
        window[0] = first
        var rowWidth by remember { mutableStateOf(0.dp) }
        val rowDensity = LocalDensity.current
        // Where the game you're on sits (Appearance › Game row): moving only at the edges (default),
        // or held at the left, the middle or the right with the row sliding under it.
        val anchorFirst: Float = when (focusAnchor) {
            com.shelf.launcher.data.TileFocus.EDGES -> first.toFloat()
            com.shelf.launcher.data.TileFocus.LEFT -> focus.toFloat()
            // Exactly in the middle of the screen, so the name, the category and the round buttons
            // (all centred) line up with it.
            // Measured on the row itself (not the display's size, which can leave out the navigation
            // bar's room and put the "middle" left of the real middle).
            com.shelf.launcher.data.TileFocus.CENTER -> focus - (((if (rowWidth > 0.dp) rowWidth else screenSizeDp().width) / 2 - m.tile / 2 - m.start) / m.stride)
            com.shelf.launcher.data.TileFocus.RIGHT -> (focus - m.visible + 1).toFloat()
        }
        if (focusAnchor != com.shelf.launcher.data.TileFocus.EDGES) first = anchorFirst.toInt().coerceAtLeast(0)

        // Scroll position in dp. A long jump (wrapping from the last game to the
        // first) snaps instead of racing past the whole library.
        val scroll = remember { Animatable(anchorFirst * strideDpValue) }
        LaunchedEffect(anchorFirst, strideDpValue) {
            val target = anchorFirst * strideDpValue
            if (abs(target - scroll.value) > strideDpValue * (m.visible + 1)) scroll.snapTo(target)
            else scroll.animateTo(target, dockMotion())
        }
        // The name follows the ring with the same motion as the row.
        val nameX = remember { Animatable(focus * strideDpValue) }
        LaunchedEffect(focus, strideDpValue) {
            val target = focus * strideDpValue
            if (abs(target - nameX.value) > strideDpValue * (m.visible + 1)) nameX.snapTo(target)
            else nameX.animateTo(target, dockMotion())
        }

        if (nameAnchor != null) {
            nameAnchor.nameX = nameX
            nameAnchor.scroll = scroll
            nameAnchor.metrics = m
            nameAnchor.align = when (focusAnchor) {
                com.shelf.launcher.data.TileFocus.CENTER -> 0
                com.shelf.launcher.data.TileFocus.RIGHT -> 1
                else -> -1
            }
        }

        // Thumbnails around the focus (see ArtRequests.thumbRequest): fast scrolling shows these instead
        // of empty tiles while the sharp art decodes.
        val thumbContext = LocalContext.current
        LaunchedEffect(focus / 6, n, cardArtType) {
            val reach = if (com.shelf.launcher.device.MemoryBudget.small(thumbContext)) 10 else 16
            val urls = ((focus - reach)..(focus + reach)).mapNotNull { i ->
                val game = games.getOrNull(i) ?: return@mapNotNull null
                val o = artOverrides[game.stableKey]
                val a = art[game.stableKey]
                val gArt = artForGame(cardArtType, game.platform, hiddenMedia, mixedRow)
                cardArtUrl(gArt, false, o?.cover ?: a?.gridUrl, o?.wide ?: a?.wideGridUrl, o?.hero ?: a?.heroUrl,
                    typedArtFor(gArt, physicalMedia[game.stableKey], screenshots[game.stableKey], game.platform))
            }
            val handles = prefetchThumbs(thumbContext, urls)
            try { kotlinx.coroutines.awaitCancellation() } finally {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { kotlinx.coroutines.delay(1500) }
                handles.forEach { it.dispose() }
            }
        }

        val currentFocus by rememberUpdatedState(focus)
        val currentOnFocus by rememberUpdatedState(onFocus)
        val currentCount by rememberUpdatedState(n)
        val focusedGame = games[focus]
        val titles = LocalTitleOptions.current

        val screenWidthPx = with(LocalDensity.current) { (if (rowWidth > 0.dp) rowWidth else screenSizeDp().width).toPx() }
        Column(Modifier.fillMaxWidth().categorySlide().onSizeChanged { rowWidth = with(rowDensity) { it.width.toDp() } }) {
            val nameLine: @Composable () -> Unit = {
            // The game's name, right above its tile, in the accent colour.
            val ts = LocalTileScale.current
            // Two lines for long names (never "…" after a few words); lined up with the categories:
            // centred on the tile when the selection sits in the middle, its right edge when it sits
            // at the right, its left edge otherwise.
            val align = when (focusAnchor) {
                com.shelf.launcher.data.TileFocus.CENTER -> 0
                com.shelf.launcher.data.TileFocus.RIGHT -> 1
                else -> -1
            }
            // One-line names take one line's room (the category above moves down to them — no gap);
            // the room for the second line goes above the category instead (see LauncherScreen), so the
            // row itself never jumps.
            // (Under the tiles it keeps both lines' room: the round buttons below must not move.)
            // Lines counted with no height limit (the box's own height must not decide it).
            val shownName = titles.display(focusedGame.name)
            val measurer = androidx.compose.ui.text.rememberTextMeasurer()
            val textStyle = uiFontStyle().merge(
                androidx.compose.ui.text.TextStyle(fontSize = 15.sp * ts, lineHeight = 18.sp * ts, fontWeight = FontWeight.SemiBold)
            )
            val maxNamePx = with(LocalDensity.current) { maxOf(m.tile * 2.4f, 220.dp).roundToPx() }
            val measuredLines = remember(shownName, textStyle, maxNamePx) {
                measurer.measure(
                    shownName,
                    textStyle,
                    maxLines = 2,
                    constraints = androidx.compose.ui.unit.Constraints(maxWidth = maxNamePx)
                ).lineCount.coerceIn(1, 2)
            }
            if (nameAnchor != null) androidx.compose.runtime.SideEffect { nameAnchor.nameLines = measuredLines }
            val lines = if (nameBelow) 2 else measuredLines
            // Tall enough for the lines at the system's font size too (a bigger font pushed the name up
            // over the category label).
            val lineDp = with(LocalDensity.current) { (18.sp * ts).toDp() }
            val boxH = if (lines == 1) maxOf(24.dp * ts, lineDp + 6.dp) else maxOf(46.dp * ts, lineDp * 2 + 10.dp)
            Box(Modifier.fillMaxWidth().height(boxH)) {
                Text(
                    shownName,
                    color = ShelfTheme.accent,
                    fontSize = 15.sp * ts, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(15.sp * ts), maxFontSize = 15.sp * ts),
                    lineHeight = androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Em),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = when (align) { 0 -> androidx.compose.ui.text.style.TextAlign.Center; 1 -> androidx.compose.ui.text.style.TextAlign.End; else -> androidx.compose.ui.text.style.TextAlign.Start },
                    style = nameTextStyle(com.shelf.launcher.data.NameBackdrop.SHADOW),
                    modifier = Modifier
                        .align(if (nameBelow) Alignment.TopStart else Alignment.BottomStart)
                        // The next game's name rises into place (update 108).
                        .riseOnChange(focusedGame.stableKey)
                        .padding(bottom = if (nameBelow) 0.dp else 6.dp, top = if (nameBelow) 4.dp else 0.dp)
                        .widthIn(max = maxOf(m.tile * 2.4f, 220.dp))
                        .layout { measurable, constraints ->
                            val p = measurable.measure(constraints.copy(minWidth = 0))
                            layout(p.width, p.height) {
                                val tileX = (m.start.value + nameX.value - scroll.value).dp.toPx()
                                val tileW = m.tile.toPx()
                                val x = when (align) {
                                    0 -> tileX + tileW / 2f - p.width / 2f
                                    1 -> tileX + tileW - p.width
                                    else -> tileX + 2.dp.toPx()
                                }
                                val screenW = screenWidthPx
                                p.place(x.coerceIn(8.dp.toPx(), maxOf(8.dp.toPx(), screenW - p.width - 8.dp.toPx())).toInt(), 0)
                            }
                        }
                )
            }
            }
            if (!nameBelow) nameLine()
            val ringPx = with(LocalDensity.current) { 8.dp.toPx() }
            Box(
                Modifier
                    .fillMaxWidth()
                    // Room for the ring outside the tiles.
                    .height(m.tile + 16.dp)
                    // Held at the left or the right: the game you're on is the last thing seen on that side — the tiles
                    // that went past it slide away behind that edge instead of showing beside it.
                    .then(
                        if (focusAnchor == com.shelf.launcher.data.TileFocus.LEFT || focusAnchor == com.shelf.launcher.data.TileFocus.RIGHT) {
                            val leftHeld = focusAnchor == com.shelf.launcher.data.TileFocus.LEFT
                            Modifier.drawWithContent {
                                val ring = 8.dp.toPx()
                                val edge = if (leftHeld) m.start.toPx() - ring
                                else m.start.toPx() + (m.visible - 1) * m.stride.toPx() + m.tile.toPx() + ring
                                clipRect(left = if (leftHeld) edge else 0f, right = if (leftHeld) size.width else edge) {
                                    this@drawWithContent.drawContent()
                                }
                            }
                        } else Modifier
                    )
                    .then(
                        if (onTilesTop != null) Modifier.onGloballyPositioned {
                            // The tiles themselves start 8 dp in (the ring's room).
                            onTilesTop(it.positionInRoot().y + ringPx)
                        } else Modifier
                    )
                    .pointerInput(strideDpValue) {
                        val stepPx = m.stride.toPx()
                        var accumulated = 0f
                        var target = 0
                        detectHorizontalDragGestures(
                            onDragStart = { accumulated = 0f; target = currentFocus },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                accumulated += dragAmount
                                while (accumulated <= -stepPx) {
                                    accumulated += stepPx
                                    target = (target + 1).coerceAtMost(currentCount - 1)
                                    currentOnFocus(target)
                                }
                                while (accumulated >= stepPx) {
                                    accumulated -= stepPx
                                    target = (target - 1).coerceAtLeast(0)
                                    currentOnFocus(target)
                                }
                            }
                        )
                    }
            ) {
                // Only the tiles that can be on screen (plus one page either side,
                // so scrolling in from the neighbours never shows a gap).
                // (Update 137: a carousel packs them closer — as many as reach past the screen's edges.)
                val reach = if (carousel) Carousel.flatReach(m.tile.value, screenSizeDp().width.value / 2f) else 0
                val from = minOf(first - m.visible, focus - reach).coerceAtLeast(0)
                val to = maxOf(first + m.visible * 2, focus + reach).coerceAtMost(n - 1)
                for (i in from..to) {
                    val game = games[i]
                    key(game.stableKey) {
                        HomeTile(
                            game = game,
                            art = art[game.stableKey],
                            override = artOverrides[game.stableKey],
                            focused = i == focus,
                            dimmed = dimmed,
                            tile = m.tile,
                            progress = raProgress[game.stableKey],
                            favorite = game.stableKey in favorites,
                            cardArtType = artForGame(cardArtType, game.platform, hiddenMedia, mixedRow),
                            typedArtUrl = typedArtFor(artForGame(cardArtType, game.platform, hiddenMedia, mixedRow), physicalMedia[game.stableKey], screenshots[game.stableKey], game.platform),
                            award = awards[game.stableKey],
                            realCase = realRef,
                            mixed = mixedRow,
                            asDisc = cardArtType == com.shelf.launcher.data.CardArtType.DISC,
                            living = livingLook(
                                lastPlayedAt[game.stableKey], raProgress[game.stableKey],
                                freshUnlock = game.raGameId?.let { id -> seenAll[com.shelf.launcher.data.AchievementSeen.keyRa(id)]?.glow?.isNotEmpty() } == true
                            ),
                            onTap = { if (i == focus) onOpen(game) else onFocus(i) },
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .zIndex(if (carousel) Carousel.placeFlat((i - focus).toFloat(), Carousel.side).z else if (i == focus) 1f else 0f)
                                .offset {
                                    val x = (m.start.value + i * strideDpValue - scroll.value).dp
                                    IntOffset(x.roundToPx(), 0)
                                }
                                .then(
                                    if (carousel) Modifier.graphicsLayer {
                                        // Distance from the game you're on, gliding with the row.
                                        val rel = i - nameX.value / strideDpValue
                                        val d = abs(rel)
                                        val side = Carousel.side
                                        val sign = if (rel < 0f) -1f else 1f
                                        // From its place in the row to its place in the carousel.
                                        // (Update 137: in the carousel view picked — Wheel, Deck, Flat row… — not only Tucked behind.)
                                        val pl = Carousel.placeFlat(rel, side)
                                        translationX = (m.tile.toPx() * pl.x) - rel * m.stride.toPx()
                                        translationY = pl.y * size.height
                                        val sc = pl.scale
                                        scaleX = sc; scaleY = sc
                                        alpha = pl.alpha
                                        rotationY = pl.turnY
                                        rotationZ = pl.turnZ
                                        cameraDistance = 12f * density
                                        if (rotationY != 0f) flatTextFor3d()
                                    } else Modifier
                                )
                                .then(if (i == focus) Modifier.focusedCard() else Modifier)
                        )
                    }
                }
            }
            if (nameBelow) nameLine()
        }
    }
}

@Composable
private fun HomeTile(
    game: Game,
    art: GameArt?,
    override: ArtOverride?,
    focused: Boolean,
    dimmed: Boolean,
    tile: Dp,
    progress: RaGameProgress?,
    favorite: Boolean,
    cardArtType: CardArtType,
    typedArtUrl: String?,
    award: com.shelf.launcher.data.RaAward? = null,
    living: LivingLook = LivingLook(),
    /** "Box art · real sizes": the biggest case in the row, so this one is drawn at its real size next to it. */
    realCase: RealCaseRef? = null,
    /** The row mixes consoles (Mixed categories: "All CDs" / "All cartridges" use the generic one where a game has none). */
    mixed: Boolean = false,
    /** The layout's card artwork is "Discs only": a cartridge game is drawn as the generic CD. */
    asDisc: Boolean = false,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val look = LocalCardLook.current
    val titles = LocalTitleOptions.current
    val accent = ShelfTheme.accent
    // Softer corners than the classic cards: squares read best a little rounded.
    val corner = (look.cornerDp.coerceAtLeast(6) * 1.1f).dp
    val shape = RoundedCornerShape(corner)
    // The ring pops in fast and fades out a touch slower.
    val ring by animateFloatAsState(
        targetValue = if (focused) (if (dimmed) 0.45f else 1f) else 0f,
        animationSpec = tween(if (focused) 110 else 160),
        label = "tileRing"
    )
    val coverUrl = override?.cover ?: art?.gridUrl
    val wideUrl = override?.wide ?: art?.wideGridUrl
    val heroUrl = override?.hero ?: art?.heroUrl
    val url = cardArtUrl(cardArtType, false, coverUrl, wideUrl, heroUrl, typedArtUrl)
    val context = LocalContext.current
    val px = with(LocalDensity.current) { tile.roundToPx() }.coerceAtLeast(1)
    val kind = when (url) {
        null -> ArtKind.COVER
        typedArtUrl -> cardArtKind(cardArtType)
        coverUrl -> ArtKind.COVER
        wideUrl -> ArtKind.WIDE
        else -> ArtKind.HERO
    }
    val request = remember(url, kind, px) { url?.let { artRequest(context, it, kind, IntSize(px, px)) } }
    val ringGap = 3.dp
    val ringWidth = 3.5.dp
    val tap by rememberUpdatedState(onTap)
    // Game row › Effect, as on the classic row: electric bolts around the ring, or a breathing glow.
    // Lite effects: a still glow instead of bolts.
    val lite = LocalLiteEffects.current
    val effect = when {
        !focused || dimmed -> com.shelf.launcher.data.FocusEffect.NONE
        lite && look.focusEffect == com.shelf.launcher.data.FocusEffect.ELECTRIC -> com.shelf.launcher.data.FocusEffect.GLOW
        else -> look.focusEffect
    }
    // ~20 updates a second (not every display frame), and still while the handheld sits idle.
    val pulse = rememberFocusPulse(animate = effect != com.shelf.launcher.data.FocusEffect.NONE && !lite)
    val glowBoost = if (effect == com.shelf.launcher.data.FocusEffect.GLOW) 0.6f + 0.8f * pulse else 1f
    // Cartridge / disc / box drawn as the real thing: it stands free in the tile's place (no square behind it, no
    // ring round the square — no empty bands next to the object); a light pool under the one you're on.
    val free = isFreeMedia(cardArtType, game.platform)
    Box(
        modifier
            .size(tile)
            .livingLibrary(living, focused, accent, (look.cornerDp.coerceAtLeast(6) * 1.1f))
            // Soft glow: a real blurred halo outside the ring.
            .then(if (free) Modifier.mediaFloorGlow(focused && !dimmed, accent, pulse) else Modifier)
            .then(
                if (!free && (effect == com.shelf.launcher.data.FocusEffect.GLOW || effect == com.shelf.launcher.data.FocusEffect.ELECTRIC))
                    Modifier.softGlow(
                        accent, corner + ringGap + ringWidth / 2,
                        (if (effect == com.shelf.launcher.data.FocusEffect.GLOW) 0.5f + 0.6f * pulse else 0.3f + 0.2f * pulse) * ring,
                        outset = ringGap + ringWidth / 2
                    )
                else Modifier
            )
            .graphicsLayer {
                // A tiny lift on the tile you're on.
                val s = 1f + 0.02f * ring
                scaleX = s; scaleY = s
            }
            .drawBehind {
                // The accent ring is the coloured frame (Settings › Cards & frames): off = no ring.
                if (ring > 0f && look.focusBorder && !free) {
                    val gap = ringGap.toPx()
                    val w = ringWidth.toPx()
                    val out = gap + w / 2f
                    val r = corner.toPx() + out
                    // Soft glow first, then the ring itself: accent, lighter at the top.
                    drawRoundRect(
                        color = accent.copy(alpha = (0.28f * ring * glowBoost).coerceAtMost(1f)),
                        topLeft = Offset(-out - w, -out - w),
                        size = Size(size.width + 2 * (out + w), size.height + 2 * (out + w)),
                        cornerRadius = CornerRadius(r + w),
                        style = Stroke(w * 2.2f)
                    )
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(lerp(accent, Color.White, 0.45f), accent, lerp(accent, Color.Black, 0.1f)),
                            startY = -out, endY = size.height + out
                        ),
                        topLeft = Offset(-out, -out),
                        size = Size(size.width + 2 * out, size.height + 2 * out),
                        cornerRadius = CornerRadius(r),
                        alpha = ring,
                        style = Stroke(w)
                    )
                }
            }
            .then(
                if (effect == com.shelf.launcher.data.FocusEffect.ELECTRIC && !free) {
                    // Around the ring (gap + half its width out), on top of the art, never clipped.
                    Modifier.electricFrame(accent, corner + ringGap + ringWidth / 2, outset = ringGap + ringWidth / 2, pulse = pulse)
                } else Modifier
            )
            .runningGlow(game.stableKey in com.shelf.launcher.data.RunningGames.keys, corner)
            .then(
                if (free) Modifier
                else Modifier.shadow(if (focused) 10.dp else 3.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                    .clip(shape)
                    .background(placeholderBrush(game.hue))
            )
            .pointerInput(game.stableKey) { detectTapGestures { tap() } }
    ) {
        val pick = if (drawnMediaWanted(cardArtType, typedArtUrl)) mediaFor(game.platform, mixed, asDisc) else null
        val box3d = if (cardArtType == CardArtType.CASE) caseMarker(game.platform) else null
        val shineOn = titles.masteredShimmer && (award == com.shelf.launcher.data.RaAward.MASTERED || award == com.shelf.launcher.data.RaAward.COMPLETED)
        if (pick != null) {
            // Cartridge / disc art, drawn (PhysicalMedia), the label being the scraped label or the cover.
            Box(Modifier.fillMaxSize().then(if (shineOn) Modifier.masteredShineOverShape(if (focused) 0.55f else 0.32f) else Modifier)) {
                MediaFocus(focused) { PhysicalMedia(pick.shell, url, Modifier.fillMaxSize().padding(8.dp), platform = pick.platform, labelIsScan = typedArtUrl != null && url == typedArtUrl, caseCover = coverUrl, title = game.name) }
            }
        } else if (box3d != null) {
            // Box art as the real box (template pack), straight from the front.
            Box(Modifier.fillMaxSize().then(if (shineOn) Modifier.masteredShineOverShape(if (focused) 0.55f else 0.32f) else Modifier)) {
                MediaFocus(focused) { CaseArt(
                    game.platform, coverUrl, typedArtUrl, Modifier.fillMaxSize().padding(6.dp), title = gameDisplayName(game.name), real = realCase,
                    // The coloured frame hugs the case itself.
                    caseModifier = if (focused && !dimmed && look.focusBorder) Modifier.border(2.dp, accent, RoundedCornerShape(2.dp)) else Modifier
                ) }
            }
        } else if (realScanOf(cardArtType, typedArtUrl) != null) {
            // Update 129: "Real discs": the game's own scan, standing free.
            Box(Modifier.fillMaxSize().then(if (shineOn) Modifier.masteredShineOverShape(if (focused) 0.55f else 0.32f) else Modifier)) {
                MediaFocus(focused) { RealMediaPicture(typedArtUrl!!, mediaRound(game.platform), Modifier.fillMaxSize().padding(8.dp)) }
            }
        } else if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = game.name,
                contentScale = ContentScale.Crop,
                // Covers are portrait: keep their upper part, where the title and faces usually are.
                alignment = if (kind == ArtKind.COVER) BiasAlignment(0f, -0.35f) else Alignment.Center,
                modifier = Modifier.fillMaxSize().pixelArtwork()
            )
        } else if (game.sourceType == SourceType.APP) {
            AppIconArt(game.packageName, wide = false, modifier = Modifier.fillMaxSize())
        } else {
            // No art yet: the name, so the tile still says which game it is.
            Text(
                titles.display(game.name),
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp),
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
            )
        }
        if (!focused && look.otherGames != com.shelf.launcher.data.OtherGamesLook.NORMAL) {
            Box(
                Modifier.fillMaxSize().background(
                    Color.Black.copy(alpha = if (look.otherGames == com.shelf.launcher.data.OtherGamesLook.DIMMED) 0.45f else 0.2f)
                )
            )
        }
        // Mastered / completed: the same light sweep as the classic cards (Achievements › Shimmer).
        // Cartridges / discs / boxes carry it over their own shape (above), not a square.
        if (shineOn && !free) {
            MasteredShine(Modifier.fillMaxSize(), if (focused) 0.55f else 0.32f)
        }
        if (favorite && look.favoriteStar) {
            Icon(
                Icons.Filled.Star, contentDescription = "Favorite", tint = accent,
                modifier = Modifier.align(Alignment.TopStart).padding(7.dp).size(14.dp)
            )
        }
        val possible = progress?.NumPossibleAchievements
        // (Update 129: placed by the R1 Achievements tile; the corner one keeps the top right.)
        // Update 151: the corner picked (Settings / the R1 Achievements tile), not always the top right.
        val pillAt = achChipAlign(look.trophyCorner)
        if (possible != null && possible > 0 && look.achievementsOnCards && titles.coverAchievements && pillAt != null) {
            val earned = progress.NumAchieved ?: 0
            Row(
                Modifier
                    .align(pillAt)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.62f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.EmojiEvents, null, tint = accent, modifier = Modifier.size(10.dp))
                // Update 151: a small upright tick between the trophy and "0/69".
                ProgressTick(height = 8.dp, gap = 3.dp)
                Text(
                    "$earned/$possible",
                    color = Color.White, fontSize = 10.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(10.sp), maxFontSize = 10.sp), fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, softWrap = false
                )
            }
        }
        // Game state mark (Settings › Library › Game states), bottom-left unless the no-art name sits there.
        if (request != null || game.sourceType == SourceType.APP) {
            GameStates.byKey[game.stableKey]?.let { gs ->
                GameStateTag(gs, Modifier.align(Alignment.BottomStart).padding(6.dp), compact = !focused)
            }
        }
    }
}

/** The round buttons' names, in their order (Settings › Round shortcuts › Buttons). */
internal val HomeShortcutNames = listOf(
    "Categories", "All apps", "Search", "Notifications", "Music", "Achievements", "Shortcuts",
    com.shelf.launcher.data.SAVES_NAME, "What should I play?", "Edit Home", "Volt", "Settings"
)

/** One round button under the tiles. */
internal class HomeShortcut(
    val label: String,
    val icon: ImageVector,
    /** Each button has its own colour, like a console's home menu. */
    val tint: Color,
    val onClick: () -> Unit
)

/**
 * The pill of round shortcut buttons under the tiles. [focused] is the button
 * the D-pad is on (null: the selection is on the tiles). Its name shows under
 * the pill; that line's space is always kept, so nothing moves.
 */
@Composable
internal fun HomeShortcutBar(
    shortcuts: List<HomeShortcut>,
    focused: Int?,
    /** Theme colours (accent) instead of each button's own colour. */
    themed: Boolean = false,
    /** The focused button's name above the pill (the pill sits above the tiles). */
    labelOnTop: Boolean = false,
    highlight: com.shelf.launcher.data.ShortcutHighlight = com.shelf.launcher.data.ShortcutHighlight.RING,
    /** False when it floats (moved in Edit Home): only as wide as its buttons. */
    fillWidth: Boolean = true
) {
    com.shelf.launcher.Breadcrumbs.mark("HomeShortcutBar")
    val accent = ShelfTheme.accent
    // Smaller on small / narrow screens (Retroid Pocket Classic etc.), so the pill never takes over the screen.
    val screen = screenSizeDp()
    val wanted = (minOf(screen.width / 900.dp, screen.height / 500.dp)).coerceIn(0.62f, 1f) * LocalTileScale.current.coerceIn(0.85f, 1.45f) *
        // Settings › Round shortcuts › Size.
        (com.shelf.launcher.data.Features.shortcutScale.state.value / 100f)
    // Never wider than the screen: with many buttons, a big size or a narrow (second) screen the pill used
    // to run off both edges. Buttons 42, gaps 9, ends 12 (dp, times k).
    val n = shortcuts.size.coerceAtLeast(1)
    val fit = (screen.width.value - 24f) / (42f * n + 9f * (n - 1) + 24f)
    val k = minOf(wanted, fit).coerceAtLeast(0.4f)
    val labelLine: @Composable () -> Unit = {
        Box(Modifier.height(22.dp * k), contentAlignment = Alignment.Center) {
            val label = focused?.let { shortcuts.getOrNull(it)?.label }
            if (label != null) {
                Text(label, color = Color.White, fontSize = 12.sp * k.coerceAtLeast(1f), autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp * k.coerceAtLeast(1f)), maxFontSize = 12.sp * k.coerceAtLeast(1f)), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Clip, softWrap = false,
                    style = nameTextStyle(com.shelf.launcher.data.NameBackdrop.SHADOW))
            }
        }
    }
    Column((if (fillWidth) Modifier.fillMaxWidth() else Modifier), horizontalAlignment = Alignment.CenterHorizontally) {
        if (labelOnTop) labelLine()
        // The pill is drawn behind the buttons rather than clipping them: the highlight round the button
        // you're on (it grows a little) used to be cut off by the pill's rounded ends and its top / bottom.
        val pillColor = ShelfTheme.colors.surface.copy(alpha = 0.72f)
        // "Lift & dot": the pill makes room for the lifted button above and the dot under it, so they stay inside.
        val liftStyle = highlight == com.shelf.launcher.data.ShortcutHighlight.LIFT
        Row(
            Modifier
                .drawBehind {
                    drawRoundRect(pillColor, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f))
                }
                .padding(
                    start = (if (liftStyle) 14.dp else 12.dp) * k, end = (if (liftStyle) 14.dp else 12.dp) * k,
                    top = (if (liftStyle) 10.dp else 8.dp) * k, bottom = (if (liftStyle) 16.dp else 8.dp) * k
                ),
            horizontalArrangement = Arrangement.spacedBy(9.dp * k),
            verticalAlignment = Alignment.CenterVertically
        ) {
            shortcuts.forEachIndexed { i, s ->
                val on = focused == i
                // Button colours: the theme's accent, or each button's own colour (Settings › Round shortcuts).
                // ("Their own colours" used to become the theme's palette with the Volt look on, so the two looked the same.)
                val tint = if (themed) accent else s.tint
                val click by rememberUpdatedState(s.onClick)
                val lift by animateFloatAsState(if (on) 1f else 0f, tween(if (on) 110 else 160), label = "tileButton")
                val filled = highlight == com.shelf.launcher.data.ShortcutHighlight.FILL
                val lifted = highlight == com.shelf.launcher.data.ShortcutHighlight.LIFT
                Box(
                    Modifier
                        .size(42.dp * k)
                        .graphicsLayer {
                            val sc = 1f + (if (lifted) 0.14f else 0.08f) * lift
                            scaleX = sc; scaleY = sc
                            if (lifted) translationY = -4.dp.toPx() * lift
                        }
                        .drawBehind {
                            if (lift > 0f) {
                                val r = size.minDimension / 2f
                                when (highlight) {
                                    com.shelf.launcher.data.ShortcutHighlight.RING -> {
                                        val w = 2.dp.toPx()
                                        drawCircle(accent.copy(alpha = lift), radius = r + w * 1.5f, style = Stroke(w))
                                    }
                                    com.shelf.launcher.data.ShortcutHighlight.FILL -> drawCircle(
                                        androidx.compose.ui.graphics.Brush.radialGradient(
                                            listOf(tint.copy(alpha = 0.55f * lift), Color.Transparent), radius = r * 1.6f
                                        ), radius = r * 1.6f
                                    )
                                    com.shelf.launcher.data.ShortcutHighlight.LIFT -> {
                                        // A soft glow under the button and the dot, both inside the pill's room. The layer
                                        // is lifted, so undo that for the dot (it stays put on the "floor").
                                        val lifted = 4.dp.toPx() * lift
                                        val sc = 1f + 0.14f * lift
                                        drawCircle(
                                            androidx.compose.ui.graphics.Brush.radialGradient(
                                                listOf(tint.copy(alpha = 0.4f * lift), Color.Transparent),
                                                center = center.copy(y = center.y + r * 0.55f), radius = r * 0.95f
                                            ), radius = r * 0.95f, center = center.copy(y = center.y + r * 0.55f)
                                        )
                                        val dotY = center.y + (size.height - center.y + 5.dp.toPx() * k + lifted) / sc
                                        drawCircle(tint.copy(alpha = lift), radius = 2.5.dp.toPx() * k / sc, center = center.copy(y = dotY))
                                    }
                                }
                            }
                        }
                        .clip(CircleShape)
                        .background(
                            if (filled) lerp(lerp(ShelfTheme.colors.surfaceRaised, tint, 0.16f), tint, 0.9f * lift)
                            else lerp(ShelfTheme.colors.surfaceRaised, tint, 0.16f + 0.14f * lift)
                        )
                        .pointerInput(i) { detectTapGestures { click() } },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        s.icon, contentDescription = s.label,
                        tint = if (filled) lerp(tint, Color.White, lift) else tint,
                        modifier = Modifier.size(22.dp * k)
                    )
                }
            }
        }
        if (!labelOnTop) labelLine()
    }
}

/**
 * The round shortcuts folded into one small ☰ button in a corner: tap it and they drop out as a column
 * (upwards in a bottom corner), tap one or ☰ again to fold them away.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun CornerShortcuts(
    shortcuts: List<HomeShortcut>,
    themed: Boolean,
    top: Boolean,
    start: Boolean,
    modifier: Modifier = Modifier,
    /** The button the D-pad is on, in the order they're drawn (HomeReach); they fold out while it's set. */
    focused: Int? = null
) {
    var openTouch by remember { mutableStateOf(false) }
    val always = com.shelf.launcher.data.Features.shortcutsOpen.state.value
    val open = openTouch || focused != null || always
    val accent = ShelfTheme.accent
    // One straight line along the edge, away from the corner: ☰ first, the buttons after it — onto a
    // second line when the screen is too narrow for all of them (they used to run off the far edge).
    Row(
        modifier
            .clip(if (open) RoundedCornerShape(22.dp) else RoundedCornerShape(50))
            .background(if (open) ShelfTheme.colors.surface.copy(alpha = 0.85f) else Color.Transparent)
            .padding(if (open) 4.dp else 0.dp),
        verticalAlignment = if (top) Alignment.Top else Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val toggle: @Composable () -> Unit = {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(ShelfTheme.colors.surface.copy(alpha = 0.85f))
                    .tapOnly { openTouch = !open },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (open) Icons.Filled.Close else Icons.Filled.Menu,
                    contentDescription = if (open) "Close shortcuts" else "Shortcuts",
                    tint = if (themed) accent else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        // Update 144: with 'In a corner: always open' the shortcuts can't fold, so the ✕ (which did nothing) is left out.
        if (start && !always) toggle()
        if (open) {
            androidx.compose.foundation.layout.FlowRow(
                Modifier.weight(1f, fill = false),
                horizontalArrangement = Arrangement.spacedBy(6.dp, if (start) Alignment.Start else Alignment.End),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                (if (start) shortcuts else shortcuts.reversed()).forEachIndexed { i, s ->
                    val tint = if (themed) accent else s.tint
                    val on = focused == i
                    Box(
                        Modifier
                            .size(34.dp)
                            .graphicsLayer { val k = if (on) 1.12f else 1f; scaleX = k; scaleY = k }
                            .clip(CircleShape)
                            .background(lerp(ShelfTheme.colors.surfaceRaised, tint, if (on) 0.45f else 0.2f))
                            .border(if (on) 2.dp else 0.dp, if (on) accent else Color.Transparent, CircleShape)
                            .tapOnly { openTouch = false; s.onClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(s.icon, contentDescription = s.label, tint = tint, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        if (!start && !always) toggle()
    }
}

