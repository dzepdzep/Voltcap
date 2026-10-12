package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.ConsoleLogos
import com.shelf.launcher.data.ConsolePhotos
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.PlatformInfo
import com.shelf.launcher.ui.theme.ShelfTheme

/*
 * The platform browser: the category screen as Slides (one platform at a time, like a console's shelf
 * card), Wall (slanted slices of art with the logo in the middle) or Grid (a horizontal grid of cards,
 * optionally tall 2:3, with a reorder mode). Every part is switchable (Settings › Appearance ›
 * Platform browser). Works with the controller (◀ ▶ ▲ ▼, A, B, Y options, X random game, Start to pick a
 * platform up in the grid), touch (tap, swipe, long press / drag) and on the second screen (Wall).
 */

// ---------------------------------------------------------------------------------------------
// Slide layout (the slide editor)
// ---------------------------------------------------------------------------------------------

/** Where something sits on the slide, as fractions of its width and height. */
data class FRect(val x: Float, val y: Float, val w: Float, val h: Float)

/** The parts of a platform slide you can place and size freely. */
enum class SlideEl(val label: String) {
    COVERS("Cover fan"), SHADOW("Shadow"), LOGO("Logo"), ICON("Icon"), NAME("Platform name"),
    COUNT("Game count"), MAKER("Maker and year"), LASTPLAYED("Last played card")
}

object SlideLayout {
    val slideDefaults: Map<String, FRect> = mapOf(
        SlideEl.COVERS.name to FRect(0.07f, 0.10f, 0.30f, 0.78f),
        SlideEl.SHADOW.name to FRect(0.05f, 0.80f, 0.34f, 0.10f),
        SlideEl.LOGO.name to FRect(0.44f, 0.20f, 0.46f, 0.26f),
        SlideEl.ICON.name to FRect(0.44f, 0.06f, 0.07f, 0.11f),
        SlideEl.NAME.name to FRect(0.44f, 0.50f, 0.50f, 0.10f),
        SlideEl.COUNT.name to FRect(0.44f, 0.62f, 0.50f, 0.07f),
        SlideEl.MAKER.name to FRect(0.44f, 0.70f, 0.30f, 0.06f),
        SlideEl.LASTPLAYED.name to FRect(0.76f, 0.60f, 0.18f, 0.32f)
    )

    /** The Wall view on the second screen: its own layout, one for platforms and one for games. */
    val wallDefaults: Map<String, FRect> = mapOf(
        "SLICES" to FRect(0f, 0f, 1f, 1f),
        "LOGO" to FRect(0.30f, 0.30f, 0.40f, 0.30f),
        "NAME" to FRect(0.30f, 0.62f, 0.40f, 0.08f),
        "COUNT" to FRect(0.30f, 0.71f, 0.40f, 0.06f)
    )

    /** "KEY=x,y,w,h;…" laid over [defaults]; anything missing or malformed keeps the default. */
    fun parse(raw: String, defaults: Map<String, FRect>): Map<String, FRect> {
        val out = defaults.toMutableMap()
        raw.split(';').forEach { part ->
            val pieces = part.split('=')
            if (pieces.size == 2 && pieces[0] in defaults) {
                val n = pieces[1].split(',').mapNotNull { it.toFloatOrNull() }
                if (n.size == 4) out[pieces[0]] = FRect(n[0].coerceIn(-0.3f, 1.2f), n[1].coerceIn(-0.3f, 1.2f), n[2].coerceIn(0.02f, 1.5f), n[3].coerceIn(0.02f, 1.5f))
            }
        }
        return out
    }

    fun encode(map: Map<String, FRect>): String =
        map.entries.joinToString(";") { "${it.key}=${it.value.x},${it.value.y},${it.value.w},${it.value.h}" }

    /** Which element's switch in Settings is on. */
    fun shown(el: String): Boolean = when (el) {
        SlideEl.COVERS.name -> Features.pbShowCovers.value
        SlideEl.SHADOW.name -> Features.pbShowShadow.value
        SlideEl.LOGO.name -> Features.pbShowLogo.value
        SlideEl.ICON.name -> Features.pbShowIcon.value
        SlideEl.NAME.name -> Features.pbShowName.value
        SlideEl.COUNT.name -> Features.pbShowCount.value
        SlideEl.MAKER.name -> Features.pbShowMaker.value
        SlideEl.LASTPLAYED.name -> Features.pbShowLastPlayed.value
        else -> true
    }

    fun toggle(el: String) {
        when (el) {
            SlideEl.COVERS.name -> Features.pbShowCovers.toggle()
            SlideEl.SHADOW.name -> Features.pbShowShadow.toggle()
            SlideEl.LOGO.name -> Features.pbShowLogo.toggle()
            SlideEl.ICON.name -> Features.pbShowIcon.toggle()
            SlideEl.NAME.name -> Features.pbShowName.toggle()
            SlideEl.COUNT.name -> Features.pbShowCount.toggle()
            SlideEl.MAKER.name -> Features.pbShowMaker.toggle()
            SlideEl.LASTPLAYED.name -> Features.pbShowLastPlayed.toggle()
        }
    }
}

// ---------------------------------------------------------------------------------------------
// State and controller keys
// ---------------------------------------------------------------------------------------------

/** The platform highlighted in the browser right now (null when it is closed): its own music plays meanwhile. */
object PlatformBrowserFocus {
    val platform = mutableStateOf<String?>(null)
}

/** What the music overrides look at on the home screen: the game you are on and its platform. */
internal data class MusicContext(val gameKey: String?, val platform: String?)

/** What the platform browser is doing right now (kept by the launcher screen). */
class PlatformBrowserState {
    /** The tile picked up for moving in the grid (-1 = none). */
    var grabbed by mutableIntStateOf(-1)
    /** +1 when the last move went right / forward, -1 left. The animations follow it. */
    var direction by mutableIntStateOf(1)
    /** The slide editor is open. */
    var editing by mutableStateOf(false)
    var editEl by mutableStateOf(SlideEl.LOGO.name)
    /** The Platform Options menu is open for this tile. */
    var options by mutableStateOf<CategoryTile?>(null)
    /** The slide layout being edited (saved to Features as it changes). */
    var layout by mutableStateOf<Map<String, FRect>>(SlideLayout.slideDefaults)

    fun loadLayout() { layout = SlideLayout.parse(Features.pbSlideLayout.value, SlideLayout.slideDefaults) }

    fun saveLayout() { Features.pbSlideLayout.value = SlideLayout.encode(layout) }

    fun move(dx: Float, dy: Float) {
        val r = layout[editEl] ?: return
        layout = layout + (editEl to r.copy(x = (r.x + dx).coerceIn(-0.3f, 1.1f), y = (r.y + dy).coerceIn(-0.3f, 1.1f)))
    }

    /** Grows or shrinks the element around its centre. */
    fun resize(factor: Float) {
        val r = layout[editEl] ?: return
        val nw = (r.w * factor).coerceIn(0.03f, 1.2f)
        val nh = (r.h * factor).coerceIn(0.03f, 1.2f)
        layout = layout + (editEl to FRect(r.x + (r.w - nw) / 2f, r.y + (r.h - nh) / 2f, nw, nh))
    }

    fun cycleElement(step: Int) {
        val all = SlideEl.values().filter { it != SlideEl.ICON }.toTypedArray()
        val i = all.indexOfFirst { it.name == editEl }.coerceAtLeast(0)
        editEl = all[(i + step + all.size) % all.size].name
    }

    fun resetLayout() { layout = SlideLayout.slideDefaults; saveLayout() }
}

/**
 * The controller on the platform browser. Returns true when the key was used (the caller returns true too).
 * [index] is the highlighted tile; [setIndex] moves it.
 */
internal fun platformBrowserKey(
    event: KeyEvent,
    st: PlatformBrowserState,
    view: Features.PlatformView,
    tiles: List<CategoryTile>,
    index: Int,
    setIndex: (Int) -> Unit,
    onPick: () -> Unit,
    onClose: () -> Unit,
    onRandom: () -> Unit,
    onReorder: (from: Int, to: Int) -> Unit,
    onMoveSound: () -> Unit,
    onBackSound: () -> Unit
): Boolean {
    val down = event.type == KeyEventType.KeyDown
    val up = event.type == KeyEventType.KeyUp
    if (!down && !up) return true
    if (st.editing) {
        if (!down) return true
        when (event.key) {
            Key.DirectionLeft -> st.move(-0.01f, 0f)
            Key.DirectionRight -> st.move(0.01f, 0f)
            Key.DirectionUp -> st.move(0f, -0.01f)
            Key.DirectionDown -> st.move(0f, 0.01f)
            Key.ButtonL1 -> { st.cycleElement(-1); onMoveSound() }
            Key.ButtonR1 -> { st.cycleElement(1); onMoveSound() }
            Key.ButtonX -> st.resize(1.04f)
            Key.ButtonY -> st.resize(0.96f)
            Key.ButtonA, Key.Enter, Key.DirectionCenter -> SlideLayout.toggle(st.editEl)
            Key.ButtonSelect -> st.resetLayout()
            Key.ButtonB, Key.Back, Key.ButtonStart -> { st.saveLayout(); st.editing = false; onBackSound() }
            else -> Unit
        }
        return true
    }
    val count = tiles.size
    // Wide cards in the grid sit in two rows: ▲ ▼ move between them, ◀ ▶ jump a whole column.
    val rows = if (view == Features.PlatformView.GRID && !Features.pbTallCards.value) 2 else 1
    if (down) {
        var step = 0
        when (event.key) {
            Key.DirectionLeft -> step = -rows
            Key.DirectionRight -> step = rows
            Key.DirectionUp -> step = if (rows > 1) -1 else 0
            Key.DirectionDown -> step = if (rows > 1) 1 else 0
            else -> Unit
        }
        if (step != 0 && count > 0) {
            // Wraps round at both ends (the last platform goes on to the first); picked up for moving, it stops at the ends.
            val next = if (st.grabbed >= 0) (index + step).coerceIn(0, count - 1) else (index + step).mod(count)
            // A platform picked up only swaps with other platforms (not "All games", Favorites…).
            val blocked = st.grabbed >= 0 && tiles.getOrNull(next)?.platform == null
            if (next != index && !blocked) {
                st.direction = if (step > 0) 1 else -1
                if (st.grabbed >= 0) {
                    onReorder(index, next)
                    st.grabbed = next
                }
                setIndex(next)
                onMoveSound()
            }
        }
        if (event.key == Key.ButtonL1 && count > 0 && st.grabbed < 0) { st.direction = -1; setIndex((index - 3).coerceAtLeast(0)); onMoveSound() }
        if (event.key == Key.ButtonR1 && count > 0 && st.grabbed < 0) { st.direction = 1; setIndex((index + 3).coerceAtMost(count - 1)); onMoveSound() }
        return true
    }
    when (event.key) {
        Key.ButtonA, Key.Enter, Key.DirectionCenter -> {
            if (st.grabbed >= 0) { st.grabbed = -1; onMoveSound() } else onPick()
        }
        Key.ButtonB, Key.Back -> {
            if (st.grabbed >= 0) { st.grabbed = -1; onBackSound() } else onClose()
        }
        Key.ButtonY -> tiles.getOrNull(index)?.let { st.options = it }
        Key.ButtonX -> onRandom()
        // Start: pick a platform up to move it (grid with the reorder mode switched on).
        Key.ButtonStart -> if (view == Features.PlatformView.GRID && Features.pbReorder.value && tiles.getOrNull(index)?.platform != null) {
            st.grabbed = if (st.grabbed >= 0) -1 else index
            onMoveSound()
        }
        else -> Unit
    }
    return true
}

// ---------------------------------------------------------------------------------------------
// The browser
// ---------------------------------------------------------------------------------------------

/** A platform's scraped pictures (Platform Options › Scrape platform media): its logo, photo, fan art.
 *  The card art itself comes from [CategoryArt] (Settings › Categories › "Cards show"); no RetroAchievements icons. */
private class TileArt(val logo: String?, val photo: String?, val fanart: String?)

@Composable
private fun rememberTileArt(tile: CategoryTile): TileArt {
    val context = LocalContext.current
    remember(context) { PlatformInfo.init(context); true }
    val version = PlatformInfo.version.value
    val media = remember(tile.platform, version) { tile.platform?.let { PlatformInfo.media(it) } }
    return TileArt(media?.logo, tile.customImage ?: media?.photo, media?.fanart)
}

/** "113 Games (9 Favorites)". */
private fun countText(tile: CategoryTile): String {
    val games = if (tile.count == 1) "1 Game" else "${tile.count} Games"
    return if (tile.favorites > 0) "$games (${tile.favorites} ${if (tile.favorites == 1) "Favorite" else "Favorites"})" else games
}

@Composable
fun PlatformBrowser(
    tiles: List<CategoryTile>,
    selectedIndex: Int,
    state: PlatformBrowserState,
    view: Features.PlatformView,
    onFocus: (Int) -> Unit,
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
    onRandom: () -> Unit,
    onReorder: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    /** Room left free at the top for the status bar (the action bar used to sit under it). */
    topInset: androidx.compose.ui.unit.Dp = 0.dp
) {
    com.shelf.launcher.Breadcrumbs.mark("PlatformBrowser")
    val tile = tiles.getOrNull(selectedIndex)
    val art = if (tile != null) rememberTileArt(tile) else null
    // Its own music (if the platform has one) plays while it is highlighted.
    LaunchedEffect(tile?.platform) { PlatformBrowserFocus.platform.value = tile?.platform }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { PlatformBrowserFocus.platform.value = null } }
    Box(modifier.fillMaxSize().background(ShelfTheme.background)) {
        // The platform's card art (its games fading, the last played one…, CategoryArt.kt), dimmed, behind everything.
        if (tile != null && view != Features.PlatformView.WALL) {
            CategoryArt(tile, CardShape.WIDE, Modifier.fillMaxSize().graphicsLayer { alpha = 0.22f })
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.55f)))))
        Box(Modifier.fillMaxSize().padding(top = topInset)) {
        when (view) {
            Features.PlatformView.SLIDES -> SlidesView(tiles, selectedIndex, state, onFocus, onPick)
            Features.PlatformView.WALL -> WallView(tiles, selectedIndex, state.direction, Features.pbAnimate.value, onFocus, onPick, Modifier.fillMaxSize())
            Features.PlatformView.GRID -> GridView(tiles, selectedIndex, state, onFocus, onPick, onReorder)
            else -> Unit
        }
        // Action bar: the platform's name, the game count and the random-game button; it animates between views.
        AnimatedContent(
            targetState = view,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically(tween(220)) { -it / 2 }) togetherWith (fadeOut(tween(140)) + slideOutVertically(tween(140)) { -it / 2 })
            },
            label = "pbActionBar",
            modifier = Modifier.align(Alignment.TopCenter)
        ) { v ->
            PlatformActionBar(tile, selectedIndex, tiles.size, v, onRandom, onClose)
        }
        if (Features.pbShowLegend.value && !state.editing) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                LegendItem("A", "Choose")
                LegendItem("Y", "Options")
                if (Features.pbBarRandom.value) LegendItem("X", "Random game")
                if (view == Features.PlatformView.GRID && Features.pbReorder.value) LegendItem("Start", "Move platform")
                LegendItem("B", "Back")
            }
        }
        if (state.editing) SlideEditorBar(state, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun LegendItem(button: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            button, color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.85f)).padding(horizontal = 7.dp, vertical = 2.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PlatformActionBar(tile: CategoryTile?, index: Int, total: Int, view: Features.PlatformView, onRandom: () -> Unit, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            when (view) { Features.PlatformView.SLIDES -> "Platforms"; Features.PlatformView.WALL -> "Wall"; else -> "All platforms" },
            color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(12.dp))
        if (tile != null) Text("${index + 1} / $total", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        if (tile != null && Features.pbBarCount.value) {
            Text(
                countText(tile), color = ShelfTheme.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.10f)).padding(horizontal = 12.dp, vertical = 6.dp)
            )
            Spacer(Modifier.width(10.dp))
        }
        if (Features.pbBarRandom.value) {
            Chip("Random game", accent = true, onClick = onRandom)
            Spacer(Modifier.width(10.dp))
        }
        CloseButton(onClose)
    }
}

// ---------------------------------------------------------------------------------------------
// Slides
// ---------------------------------------------------------------------------------------------

@Composable
private fun SlidesView(
    tiles: List<CategoryTile>, selected: Int, state: PlatformBrowserState,
    onFocus: (Int) -> Unit, onPick: (Int) -> Unit
) {
    val animate = Features.pbAnimate.value
    val latestIndex by rememberUpdatedState(selected)
    val latestCount by rememberUpdatedState(tiles.size)
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(state.editing) {
                if (!state.editing) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            if (total < -80f && latestIndex < latestCount - 1) { state.direction = 1; onFocus(latestIndex + 1) }
                            else if (total > 80f && latestIndex > 0) { state.direction = -1; onFocus(latestIndex - 1) }
                        }
                    ) { _, amount -> total += amount }
                }
            }
    ) {
        AnimatedContent(
            targetState = selected,
            transitionSpec = {
                val dir = if (animate) state.direction else 0
                if (dir == 0) fadeIn(tween(1)) togetherWith fadeOut(tween(1))
                else (fadeIn(tween(260)) + slideInHorizontally(tween(260)) { it / 10 * dir }) togetherWith
                    (fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { -it / 10 * dir })
            },
            label = "pbSlides",
            modifier = Modifier.fillMaxSize()
        ) { idx ->
            val tile = tiles.getOrNull(idx)
            if (tile != null) PlatformSlide(tile, state, Modifier.fillMaxSize(), onTap = { if (!state.editing) onPick(idx) })
        }
    }
}

/** One platform's slide: grey skewed bands, then whichever parts are switched on, placed by the layout. */
@Composable
internal fun PlatformSlide(
    tile: CategoryTile,
    state: PlatformBrowserState,
    modifier: Modifier = Modifier,
    layout: Map<String, FRect> = state.layout,
    onTap: () -> Unit = {}
) {
    val art = rememberTileArt(tile)
    val editing = state.editing
    val density = LocalDensity.current
    BoxWithConstraints(modifier.pointerInput(Unit) { detectTapGestures { onTap() } }) {
        val w = maxWidth
        val h = maxHeight
        val wPx = with(density) { w.toPx() }
        val hPx = with(density) { h.toPx() }
        val covers = layout[SlideEl.COVERS.name] ?: SlideLayout.slideDefaults.getValue(SlideEl.COVERS.name)
        if (Features.pbBands.value) {
            val band = Color.hsl(tile.hue.toFloat(), 0.12f, 0.45f)
            Canvas(Modifier.fillMaxSize()) {
                val skew = size.height * 0.12f
                for (k in 0 until 3) {
                    val left = size.width * (covers.x - 0.05f + k * 0.055f)
                    val bw = size.width * 0.04f
                    val p = Path().apply {
                        moveTo(left + skew, 0f); lineTo(left + skew + bw, 0f); lineTo(left + bw, size.height); lineTo(left, size.height); close()
                    }
                    drawPath(p, band.copy(alpha = 0.10f + 0.05f * k))
                }
            }
        }
        SlideEl.values().filter { it != SlideEl.ICON }.forEach { el ->
            val r = layout[el.name]
            val on = SlideLayout.shown(el.name)
            // In the editor a switched-off part stays visible, faint, so it can still be found and switched on.
            if (r != null && (on || editing)) {
                val selectedEl = editing && state.editEl == el.name
                Box(
                    Modifier
                        .offset(w * r.x, h * r.y)
                        .size(w * r.w, h * r.h)
                        .graphicsLayer { alpha = if (on) 1f else 0.3f }
                        .then(
                            if (editing) Modifier
                                .border(if (selectedEl) 2.dp else 1.dp, if (selectedEl) ShelfTheme.accent else Color.White.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                .pointerInput(el.name) {
                                    detectDragGestures(onDragStart = { state.editEl = el.name }) { change, drag ->
                                        change.consume()
                                        state.move(drag.x / wPx, drag.y / hPx)
                                    }
                                }
                            else Modifier
                        )
                ) {
                    SlidePart(el, tile, art)
                }
            }
        }
    }
}

@Composable
private fun SlidePart(el: SlideEl, tile: CategoryTile, art: TileArt) {
    when (el) {
        SlideEl.COVERS -> {
            val covers = tile.covers.take(3)
            if (covers.isEmpty()) {
                CategoryArt(tile, CardShape.WIDE, Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)))
            } else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // Back to front: the left and right covers fanned out behind the middle one.
                val order = covers.withIndex().toList().reversed()
                order.forEach { (i, url) ->
                    val angle = when (i) { 0 -> 0f; 1 -> -9f; else -> 9f }
                    val shift = when (i) { 0 -> 0.dp; 1 -> (-28).dp; else -> 28.dp }
                    AsyncImage(
                        model = url, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxHeight(if (i == 0) 1f else 0.9f)
                            .aspectRatio(2f / 3f)
                            .offset(x = shift)
                            .rotate(angle)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
            }
        }
        SlideEl.SHADOW -> Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)),
                RoundedCornerShape(50)
            )
        )
        SlideEl.LOGO -> {
            val logo = art.logo
            if (logo != null) AsyncImage(model = logo, contentDescription = tile.label, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart, modifier = Modifier.fillMaxSize())
            else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                Text(tile.label, color = Color.White, fontSize = 34.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(34.sp), maxFontSize = 34.sp), fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = androidx.compose.ui.unit.TextUnit(1.059f, androidx.compose.ui.unit.TextUnitType.Em))
            }
        }
        // The RetroAchievements system icon is gone (update 81); the part stays so saved slide designs still load.
        SlideEl.ICON -> Unit
        SlideEl.NAME -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            Text(tile.label, color = Color.White, fontSize = 22.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(22.sp), maxFontSize = 22.sp), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        SlideEl.COUNT -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            Text(countText(tile), color = ShelfTheme.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        SlideEl.MAKER -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            val line = tile.platform?.let { PlatformInfo.line(it) }
            if (line != null) Text(line, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1)
        }
        SlideEl.LASTPLAYED -> {
            val cover = tile.lastPlayedCover
            if (cover != null) Column(
                Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.40f)).padding(6.dp)
            ) {
                Text("LAST PLAYED", color = Color.White.copy(alpha = 0.7f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(4.dp))
                AsyncImage(model = cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(6.dp)))
                tile.lastPlayedTitle?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = Color.White, fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** The slide editor's bar: choose a part, make it smaller or bigger, switch it on or off, reset, done. */
@Composable
private fun SlideEditorBar(state: PlatformBrowserState, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.72f)).padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(
            "Drag a part, or ◀ ▶ ▲ ▼ to move it · L1 / R1 choose a part · X bigger · Y smaller · A show / hide · Select reset · B done",
            color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp
        )
        Spacer(Modifier.height(6.dp))
        ChipFlow {
            SlideEl.values().filter { it != SlideEl.ICON }.forEach { el ->
                Chip((if (SlideLayout.shown(el.name)) "" else "(off) ") + el.label, accent = state.editEl == el.name) { state.editEl = el.name }
            }
        }
        Spacer(Modifier.height(6.dp))
        ChipFlow {
            Chip("Smaller", accent = false) { state.resize(0.94f) }
            Chip("Bigger", accent = false) { state.resize(1.06f) }
            Chip(if (SlideLayout.shown(state.editEl)) "Hide it" else "Show it", accent = false) { SlideLayout.toggle(state.editEl) }
            Chip("Reset", accent = false) { state.resetLayout() }
            Chip("Done") { state.saveLayout(); state.editing = false }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Wall
// ---------------------------------------------------------------------------------------------

/** A slanted slice: the top edge shifted right by [skewPx]; [gapPx] keeps a thin line between neighbours. */
private class SliceShape(private val skewPx: Float, private val gapPx: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val s = skewPx.coerceAtMost(size.width * 0.45f)
        val p = Path().apply {
            moveTo(s + gapPx, 0f)
            lineTo(size.width - gapPx, 0f)
            lineTo(size.width - s - gapPx, size.height)
            lineTo(gapPx, size.height)
            close()
        }
        return Outline.Generic(p)
    }
}

/**
 * The Wall: a row of slanted slices of each platform's art, the one you are on wider, its logo in the middle.
 * Switching platform slides the slices in the direction you are browsing. Also used on the second screen
 * ([layout] places the logo / name / count there).
 */
@Composable
internal fun WallView(
    tiles: List<CategoryTile>,
    selected: Int,
    direction: Int,
    animate: Boolean,
    onFocus: (Int) -> Unit,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    layout: Map<String, FRect> = SlideLayout.wallDefaults,
    /** The layout editor (second screen): the three parts get an outline and can be dragged. */
    editing: Boolean = false,
    editKey: String = "LOGO",
    onEditKey: (String) -> Unit = {},
    onLayoutChange: (Map<String, FRect>) -> Unit = {}
) {
    val density = LocalDensity.current
    val latestLayout by rememberUpdatedState(layout)
    val visible = 9
    val start = (selected - visible / 2).coerceIn(0, (tiles.size - visible).coerceAtLeast(0))
    val window = tiles.drop(start).take(visible)
    val shift = remember { Animatable(0f) }
    LaunchedEffect(selected) {
        if (animate) {
            shift.snapTo(direction * 140f)
            shift.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow))
        }
    }
    val skew = with(density) { 54.dp.toPx() }
    val gap = with(density) { 2.dp.toPx() }
    BoxWithConstraints(modifier.background(Color.Black)) {
        // The slice's own box size, read here: nested scopes below can't use the implicit maxWidth / maxHeight.
        val boxW = maxWidth
        val boxH = maxHeight
        val accentColor = ShelfTheme.accent
        Row(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = shift.value * density.density },
            horizontalArrangement = Arrangement.spacedBy((-54).dp)
        ) {
            window.forEachIndexed { i, tile ->
                val index = start + i
                val sel = index == selected
                val weight by animateFloatAsState(if (sel) 2.8f else 1f, tween(260), label = "wallWeight")
                WallSlice(tile, sel, skew, gap, Modifier.weight(weight).fillMaxHeight()) { if (sel) onPick(index) else onFocus(index) }
            }
        }
        val tile = tiles.getOrNull(selected)
        val r = layout["LOGO"] ?: SlideLayout.wallDefaults.getValue("LOGO")
        val name = layout["NAME"] ?: SlideLayout.wallDefaults.getValue("NAME")
        val countRect = layout["COUNT"] ?: SlideLayout.wallDefaults.getValue("COUNT")
        if (tile != null) {
            AnimatedContent(
                targetState = tile,
                transitionSpec = {
                    val dir = if (animate) direction else 0
                    (fadeIn(tween(260)) + slideInHorizontally(tween(260)) { it / 6 * dir }) togetherWith
                        (fadeOut(tween(160)) + slideOutHorizontally(tween(160)) { -it / 6 * dir })
                },
                label = "wallLogo",
                modifier = Modifier.fillMaxSize()
            ) { t ->
                val art = rememberTileArt(t)
                val wPx = with(density) { boxW.toPx() }
                val hPx = with(density) { boxH.toPx() }
                // In the editor each part is outlined (the chosen one in the accent colour) and can be dragged.
                fun edit(key: String): Modifier =
                    if (!editing) Modifier
                    else Modifier
                        .border(if (editKey == key) 2.dp else 1.dp, if (editKey == key) accentColor else Color.White.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .pointerInput(key) {
                            detectDragGestures(onDragStart = { onEditKey(key) }) { change, drag ->
                                change.consume()
                                val cur = latestLayout[key] ?: return@detectDragGestures
                                onLayoutChange(latestLayout + (key to cur.copy(x = (cur.x + drag.x / wPx).coerceIn(-0.3f, 1.1f), y = (cur.y + drag.y / hPx).coerceIn(-0.3f, 1.1f))))
                            }
                        }
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.offset(boxW * r.x, boxH * r.y).size(boxW * r.w, boxH * r.h)
                            .clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.35f)).then(edit("LOGO")),
                        contentAlignment = Alignment.Center
                    ) {
                        val logo = art.logo
                        if (logo != null) AsyncImage(model = logo, contentDescription = t.label, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(14.dp))
                        else Text(t.label, color = Color.White, fontSize = 30.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(30.sp), maxFontSize = 30.sp), fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(14.dp))
                    }
                    Box(Modifier.offset(boxW * name.x, boxH * name.y).size(boxW * name.w, boxH * name.h).then(edit("NAME")), contentAlignment = Alignment.Center) {
                        Text(t.label, color = Color.White, fontSize = 18.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(18.sp), maxFontSize = 18.sp), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box(Modifier.offset(boxW * countRect.x, boxH * countRect.y).size(boxW * countRect.w, boxH * countRect.h).then(edit("COUNT")), contentAlignment = Alignment.Center) {
                        Text(countText(t), color = accentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun WallSlice(tile: CategoryTile, selected: Boolean, skewPx: Float, gapPx: Float, modifier: Modifier, onClick: () -> Unit) {
    val latest by rememberUpdatedState(onClick)
    Box(
        modifier
            .clip(SliceShape(skewPx, gapPx))
            .background(Brush.linearGradient(listOf(Color.hsl(tile.hue.toFloat(), 0.45f, 0.30f), Color.hsl(((tile.hue + 40) % 360).toFloat(), 0.4f, 0.10f))))
            .pointerInput(Unit) { detectTapGestures { latest() } }
    ) {
        CategoryArt(tile, CardShape.TALL, Modifier.fillMaxSize())
        // The ones you are not on are dimmed so the logo and the selected slice stand out.
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (selected) 0.18f else 0.52f)))
    }
}

// ---------------------------------------------------------------------------------------------
// Grid
// ---------------------------------------------------------------------------------------------

@Composable
private fun GridView(
    tiles: List<CategoryTile>, selected: Int, state: PlatformBrowserState,
    onFocus: (Int) -> Unit, onPick: (Int) -> Unit, onReorder: (Int, Int) -> Unit
) {
    val tall = Features.pbTallCards.value
    val reorder = Features.pbReorder.value
    val rows = if (tall) 1 else 2
    val gridState = rememberLazyGridState()
    val columnFirst = selected / rows
    LaunchedEffect(selected, rows) {
        val info = gridState.layoutInfo
        val visible = info.visibleItemsInfo
        val first = visible.firstOrNull()?.index ?: 0
        val last = visible.lastOrNull()?.index ?: 0
        if (visible.isEmpty() || selected <= first + rows - 1 || selected >= last - rows + 1) {
            gridState.animateScrollToItem(((columnFirst - 1).coerceAtLeast(0)) * rows)
        }
    }
    val density = LocalDensity.current
    val latestTiles by rememberUpdatedState(tiles)
    val latestReorder by rememberUpdatedState(onReorder)
    LazyHorizontalGrid(
        rows = GridCells.Fixed(rows),
        state = gridState,
        modifier = Modifier.fillMaxSize().padding(top = 70.dp, bottom = 44.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 32.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        itemsIndexed(tiles, key = { _, t -> t.key }) { index, tile ->
            val isSel = index == selected
            val grabbed = state.grabbed == index
            val latestSel by rememberUpdatedState(isSel)
            val latestIndex by rememberUpdatedState(index)
            var accX by remember { mutableFloatStateOf(0f) }
            var accY by remember { mutableFloatStateOf(0f) }
            GridCard(
                tile, tall, isSel, grabbed,
                Modifier
                    .pointerInput(reorder, tile.key) {
                        detectTapGestures(
                            onTap = { if (state.grabbed >= 0) state.grabbed = -1 else if (latestSel) onPick(latestIndex) else onFocus(latestIndex) },
                            onLongPress = { if (!reorder) state.options = tile }
                        )
                    }
                    .then(
                        if (reorder && tile.platform != null) Modifier.pointerInput(tile.key) {
                            // Long press picks it up; dragging moves it a slot each time you pass half a card.
                            detectDragGesturesAfterLongPress(
                                onDragStart = { state.grabbed = latestTiles.indexOfFirst { it.key == tile.key }; accX = 0f; accY = 0f },
                                onDragEnd = { state.grabbed = -1 },
                                onDragCancel = { state.grabbed = -1 }
                            ) { change, drag ->
                                change.consume()
                                accX += drag.x; accY += drag.y
                                val cardW = with(density) { (if (tall) 120.dp else 200.dp).toPx() }
                                val cur = latestTiles.indexOfFirst { it.key == tile.key }
                                var target = cur
                                if (accX > cardW * 0.6f) { target = cur + rows; accX = 0f }
                                else if (accX < -cardW * 0.6f) { target = cur - rows; accX = 0f }
                                else if (rows > 1 && accY > cardW * 0.4f) { target = cur + 1; accY = 0f }
                                else if (rows > 1 && accY < -cardW * 0.4f) { target = cur - 1; accY = 0f }
                                if (target != cur && target in latestTiles.indices && latestTiles[target].platform != null) {
                                    latestReorder(cur, target)
                                    state.grabbed = target
                                    onFocus(target)
                                }
                            }
                        } else Modifier
                    )
            )
        }
    }
}

@Composable
private fun GridCard(tile: CategoryTile, tall: Boolean, selected: Boolean, grabbed: Boolean, modifier: Modifier) {
    val art = rememberTileArt(tile)
    val scale by animateFloatAsState(if (grabbed) 1.08f else if (selected) 1.04f else 1f, tween(160), label = "gridCard")
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .fillMaxHeight()
            .aspectRatio(if (tall) 2f / 3f else 16f / 10f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color.hsl(tile.hue.toFloat(), 0.45f, 0.28f), Color.hsl(((tile.hue + 40) % 360).toFloat(), 0.4f, 0.10f))))
            .border(if (selected || grabbed) 3.dp else 0.dp, if (grabbed) Color.White else if (selected) ShelfTheme.accent else Color.Transparent, shape)
    ) {
        CategoryArt(tile, if (tall) CardShape.TALL else CardShape.WIDE, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)))))
        val logo = art.logo
        Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            if (logo != null && Features.pbShowLogo.value) AsyncImage(model = logo, contentDescription = null, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart, modifier = Modifier.height(26.dp).fillMaxWidth(0.8f))
            if (Features.pbShowName.value) Text(tile.label, color = Color.White, fontSize = 14.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(14.sp), maxFontSize = 14.sp), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = androidx.compose.ui.unit.TextUnit(1.143f, androidx.compose.ui.unit.TextUnitType.Em))
            if (Features.pbShowCount.value) Text(countText(tile), color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (grabbed) Text(
            "◀ ▶", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
