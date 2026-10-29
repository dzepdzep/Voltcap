package com.shelf.launcher.ui

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.NowPlaying
import com.shelf.launcher.ui.theme.ShelfTheme

/**
 * The Widget Board: a page of the second screen with built-in widgets (Now Playing, System Stats, Screen
 * Mirror) and widgets from any other Android app, hosted with Android's own AppWidgetHost. Which ones, and in
 * what order, is Features.widgetBoardItems: "builtin:NOW_PLAYING", "builtin:SYSTEM_STATS", "builtin:SCREEN_MIRROR",
 * "app:<appWidgetId>". Other apps' widgets are added from the main screen (Settings › Appearance › Second screen
 * tools) because Android asks permission there; the board itself is touch.
 */
object WidgetBoardItems {
    const val NOW_PLAYING = "builtin:NOW_PLAYING"
    const val SYSTEM_STATS = "builtin:SYSTEM_STATS"
    const val SCREEN_MIRROR = "builtin:SCREEN_MIRROR"
    /** Update 299: the volume mixer (MixerActivity / AppMixer) as a widget. */
    const val VOLUME_MIXER = "builtin:VOLUME_MIXER"
    /** Update 348: the volume of the app on each screen (SoundPerScreenWidget). */
    const val SOUND_SCREENS = "builtin:SOUND_SCREENS"

    /** Update 299: the built-in widgets, in the order they're offered. */
    val BUILTINS = listOf(NOW_PLAYING to "Now Playing", SYSTEM_STATS to "System Stats", SCREEN_MIRROR to "Screen Mirror", VOLUME_MIXER to "Volume Mixer", SOUND_SCREENS to "Sound per screen")

    fun list(): List<String> = Features.widgetBoardItems.value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    fun set(items: List<String>) { Features.widgetBoardItems.value = items.distinct().joinToString(",") }
    fun add(item: String) { if (item !in list()) set(list() + item) }
    fun remove(item: String) { set(list() - item); setSize(item, null) }

    // ---- Update 299: each widget's size (Features.widgetBoardSizes) ----
    fun parseSizes(raw: String): Map<String, WidgetSize> = raw.split(';').mapNotNull { e ->
        val k = e.substringBefore('=', "").trim(); val v = e.substringAfter('=', "").trim()
        val sz = WidgetSize.entries.firstOrNull { it.name == v }
        if (k.isEmpty() || sz == null) null else k to sz
    }.toMap()
    fun encodeSizes(m: Map<String, WidgetSize>): String =
        m.filter { it.value != WidgetSize.NORMAL }.entries.joinToString(";") { "${it.key}=${it.value.name}" }
    fun sizeOf(item: String): WidgetSize = parseSizes(Features.widgetBoardSizes.value)[item] ?: WidgetSize.NORMAL
    /** [size] null (or NORMAL): back to the default size. */
    fun setSize(item: String, size: WidgetSize?) {
        val m = parseSizes(Features.widgetBoardSizes.value).toMutableMap()
        if (size == null || size == WidgetSize.NORMAL) m.remove(item) else m[item] = size
        val enc = encodeSizes(m)
        if (enc != Features.widgetBoardSizes.value) Features.widgetBoardSizes.value = enc
    }
    /** [item] moved to [to] (an index in the list as it is). */
    fun moveTo(order: List<String>, item: String, to: Int): List<String> {
        val i = order.indexOf(item)
        if (i < 0) return order
        val l = order.toMutableList()
        l.add(to.coerceIn(0, l.size - 1), l.removeAt(i))
        return l
    }
    fun move(item: String, by: Int) {
        val l = list().toMutableList()
        val i = l.indexOf(item)
        if (i < 0) return
        val j = (i + by).coerceIn(0, l.size - 1)
        l.add(j, l.removeAt(i))
        set(l)
    }
}

/** Update 299: a Widget Board widget's size: its height, and EXPANDED takes the whole row. */
enum class WidgetSize(val label: String, val heightDp: Int) {
    COMPACT("Compact", 112), NORMAL("Normal", 190), EXPANDED("Expanded", 300);
    fun next(allowed: List<WidgetSize>): WidgetSize = allowed.getOrNull((allowed.indexOf(this) + 1) % allowed.size.coerceAtLeast(1)) ?: this
}

/** One AppWidgetHost for the whole app (Android wants a stable host id). */
object AppWidgets {
    private const val HOST_ID = 0x564F
    private var host: AppWidgetHost? = null

    fun host(context: Context): AppWidgetHost =
        host ?: AppWidgetHost(context.applicationContext, HOST_ID).also { host = it }

    /**
     * The host is shared by every board (the home second screen and the in-game companion). Each board used to start
     * and stop it on its own, so the home board leaving as a game started switched the companion's widgets off.
     * Listening now lasts while at least one board wants it (main thread only).
     */
    private var listeners = 0

    fun startListening(context: Context) {
        if (listeners++ == 0) runCatching { host(context).startListening() }
    }

    fun stopListening(context: Context) {
        if (listeners > 0 && --listeners == 0) runCatching { host(context).stopListening() }
    }

    fun manager(context: Context): AppWidgetManager = AppWidgetManager.getInstance(context.applicationContext)

    /** Every widget the installed apps offer, by app name. */
    fun providers(context: Context): List<Pair<String, AppWidgetProviderInfo>> {
        val pm = context.packageManager
        return runCatching {
            manager(context).installedProviders.map { info ->
                val app = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString() }.getOrDefault(info.provider.packageName)
                val label = runCatching { info.loadLabel(pm) }.getOrNull().orEmpty().ifBlank { "Widget" }
                "$app · $label" to info
            }.sortedBy { it.first.lowercase() }
        }.getOrDefault(emptyList())
    }

    fun delete(context: Context, id: Int) { runCatching { host(context).deleteAppWidgetId(id) } }

    /**
     * Update 299: the sizes another app's widget [id] allows on the board — its own limits (resize mode, smallest and,
     * from Android 12, largest height). NORMAL (the board's size before 299) is always kept.
     */
    fun allowedSizes(context: Context, id: Int): List<WidgetSize> {
        val info = runCatching { manager(context).getAppWidgetInfo(id) }.getOrNull() ?: return listOf(WidgetSize.NORMAL)
        val d = context.resources.displayMetrics.density
        val vertical = info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0
        if (!vertical) return listOf(WidgetSize.NORMAL)
        val minDp = ((info.minResizeHeight.takeIf { it > 0 } ?: info.minHeight) / d)
        val maxDp = if (android.os.Build.VERSION.SDK_INT >= 31 && info.maxResizeHeight > 0) info.maxResizeHeight / d else Float.MAX_VALUE
        return WidgetSize.entries.filter { it == WidgetSize.NORMAL || (it.heightDp >= minDp && it.heightDp <= maxDp) }
    }
}

/** The tile the controller moves to from tile [i] with ▲ (dir 2) or ▼ (dir 3) on a board of [size] tiles in [cols] columns; null at the edge. */
internal fun boardPadStep(i: Int, size: Int, cols: Int, dir: Int): Int? {
    if (i !in 0 until size) return null
    val c = cols.coerceAtLeast(1)
    return when (dir) {
        3 -> (i + c).takeIf { it < size } ?: (size - 1).takeIf { it / c > i / c }
        2 -> (i - c).takeIf { it >= 0 }
        else -> null
    }
}

/** Update 299: the sizes [item] may take (built-ins: all three). */
internal fun allowedSizesOf(context: Context, item: String): List<WidgetSize> =
    if (item.startsWith("app:")) item.removePrefix("app:").toIntOrNull()?.let { AppWidgets.allowedSizes(context, it) } ?: listOf(WidgetSize.NORMAL)
    else WidgetSize.entries

/** The page. */
@Composable
internal fun WidgetBoardPage(
    nowPlaying: NowPlaying?,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    com.shelf.launcher.Breadcrumbs.mark("WidgetBoardPage")
    val context = LocalContext.current
    val raw = Features.widgetBoardItems.state.value
    val items = remember(raw) { raw.split(',').map { it.trim() }.filter { it.isNotEmpty() } }
    val sizesRaw = Features.widgetBoardSizes.state.value
    val sizes = remember(sizesRaw) { WidgetBoardItems.parseSizes(sizesRaw) }
    var editing by remember { mutableStateOf(false) }
    // Update 299: the editor — the widget picked (touch, or the controller's focus), the one being dragged and the order
    // while dragging (saved when it's let go, so nothing is written on every step).
    var selected by remember { mutableStateOf<String?>(null) }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var order by remember { mutableStateOf(items) }
    LaunchedEffect(items) { if (dragId == null) order = items }
    LaunchedEffect(editing) { if (!editing) { selected = null; dragId = null } }
    val gridState = rememberLazyGridState()
    val padScope = androidx.compose.runtime.rememberCoroutineScope()
    // The controller (SecondPad) steps tile to tile with ▲ ▼, scrolling the board to the next one: a widget below the
    // fold (another app's, which can't take focus itself) used to be unreachable, the dock being the nearest thing below.
    fun cardMove(id: String, dir: Int): Boolean {
        val i = order.indexOf(id)
        val cols = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == i }
            ?.let { cur -> gridState.layoutInfo.visibleItemsInfo.count { it.row == cur.row } } ?: 1
        val to = boardPadStep(i, order.size, cols, dir) ?: return false
        padScope.launch { gridState.animateScrollToItem(to) }
        SecondPad.focus("wb-card-${order[to]}")
        return true
    }
    val inputMode = androidx.compose.ui.platform.LocalInputModeManager.current
    // Widgets from other apps only update while the host listens.
    DisposableEffect(Unit) {
        AppWidgets.startListening(context)
        onDispose { AppWidgets.stopListening(context) }
    }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Widgets", color = ShelfTheme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Chip(if (editing) "Done" else "Edit", accent = editing, modifier = Modifier.padTarget("wb-edit") { editing = !editing }) { editing = !editing }
        }
        // (Update 299: the built-ins not on the board yet, added from the board itself while editing.)
        // (Shown while no widget is picked; picking one shows its tools in this place instead — room is short below.)
        if (editing && selected?.takeIf { it in order } == null) {
            val missing = WidgetBoardItems.BUILTINS.filter { it.first !in items }
            if (missing.isNotEmpty()) Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Add:", color = ShelfTheme.textMuted, fontSize = 12.sp)
                missing.forEach { (id, label) -> Chip("+ $label", accent = false, modifier = Modifier.padTarget("wb-add-$id") { WidgetBoardItems.add(id); selected = id }) { WidgetBoardItems.add(id); selected = id } }
            }
        }
        // (Update 299: the picked widget's tools sit here, above the board — always in view, whatever the widget's size
        // on the small lower screen.)
        val sel = selected?.takeIf { editing && it in order }
        if (sel != null) {
            val index = order.indexOf(sel)
            val name = WidgetBoardItems.BUILTINS.firstOrNull { it.first == sel }?.second
                ?: sel.removePrefix("app:").toIntOrNull()?.let { wid -> runCatching { AppWidgets.manager(context).getAppWidgetInfo(wid)?.loadLabel(context.packageManager) }.getOrNull() }
                ?: "Widget"
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                WidgetEditBar(
                    sel, index, order.size, sizes[sel] ?: WidgetSize.NORMAL, allowedSizesOf(context, sel), Modifier,
                    onMove = { by -> order = WidgetBoardItems.moveTo(order, sel, index + by); WidgetBoardItems.set(order) },
                    onSize = { WidgetBoardItems.setSize(sel, it) },
                    onReset = { WidgetBoardItems.setSize(sel, null) },
                    onRemove = {
                        if (sel.startsWith("app:")) sel.removePrefix("app:").toIntOrNull()?.let { AppWidgets.delete(context, it) }
                        WidgetBoardItems.remove(sel); selected = null
                    }
                )
            }
        } else if (editing && order.isNotEmpty() && WidgetBoardItems.BUILTINS.all { it.first in items }) {
            Text("Tap a widget to size, move or remove it; drag its handle to move it.", color = ShelfTheme.textMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
        }
        if (order.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (editing) "Add a widget above." else "No widgets yet. Tap Edit to add some, or Settings › Screens › Second screen tools.",
                    color = ShelfTheme.textMuted, fontSize = 13.sp
                )
            }
        } else {
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
            // (Expanded never taller than the board's own room: the Thor's lower screen shows ~225 dp of it.)
            val viewH = maxHeight
            LazyVerticalGrid(
                columns = GridCells.Adaptive(260.dp),
                state = gridState,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Update 112: every widget the same size. Update 299: each its own size (compact / normal / expanded,
                // expanded the whole row), set in the editor and kept.
                itemsIndexed(
                    order, key = { _, id -> id },
                    span = { _, id -> GridItemSpan(if ((sizes[id] ?: WidgetSize.NORMAL) == WidgetSize.EXPANDED) maxLineSpan else 1) }
                ) { _, id ->
                    val size = sizes[id] ?: WidgetSize.NORMAL
                    val dragging = dragId == id
                    val isSel = editing && (selected == id || dragging)
                    val shape = RoundedCornerShape(16.dp)
                    Box(
                        Modifier
                            .then(if (dragging) Modifier.zIndex(2f).graphicsLayer { translationX = dragOffset.x; translationY = dragOffset.y; scaleX = 1.03f; scaleY = 1.03f } else Modifier.animateItem())
                            .fillMaxWidth()
                            .height(if (size == WidgetSize.EXPANDED) minOf(size.heightDp.dp, viewH) else size.heightDp.dp)
                            .clip(shape)
                            .then(if (editing) Modifier else Modifier.padTarget("wb-card-$id", onMove = { d -> cardMove(id, d) }) { })
                            .background(ShelfTheme.colors.surface.copy(alpha = 0.78f))
                            .then(
                                if (editing) Modifier.border(if (isSel) 3.dp else 1.5.dp, if (isSel) ShelfTheme.accent else Color.White.copy(alpha = 0.35f), shape)
                                else Modifier
                            )
                    ) {
                        when {
                            id == WidgetBoardItems.NOW_PLAYING -> NowPlayingWidget(nowPlaying, onPlayPause, onNext, onPrevious, size)
                            id == WidgetBoardItems.SYSTEM_STATS -> SystemStatsWidget(size)
                            id == WidgetBoardItems.SCREEN_MIRROR -> ScreenMirrorWidget(Modifier.fillMaxSize())
                            id == WidgetBoardItems.VOLUME_MIXER -> VolumeMixerWidget(size)
                            id == WidgetBoardItems.SOUND_SCREENS -> SoundPerScreenWidget(size)
                            id.startsWith("app:") -> id.removePrefix("app:").toIntOrNull()?.let { AppWidgetView(it) }
                        }
                        if (editing) {
                            // While editing, the widget itself doesn't take taps (no volume moved by accident): a tap — or
                            // the controller's focus — picks it; a second tap lets it go.
                            var focused by remember { mutableStateOf(false) }
                            Box(
                                Modifier.fillMaxSize()
                                    .background(Color.Black.copy(alpha = if (isSel) 0.10f else 0.22f))
                                    .pointerInput(id) { detectTapGestures { selected = if (selected == id) null else id } }
                                    .padTarget("wb-tile-$id") { selected = if (selected == id) null else id }
                                    // (Picked by focus only when the controller moved it there: a widget moved by touch
                                    // could otherwise hand the pick to whichever one took its old place.)
                                    .onFocusChanged { focused = it.isFocused; if (it.isFocused && inputMode.inputMode == androidx.compose.ui.input.InputMode.Keyboard) selected = id }
                                    .focusable()
                                    .focusRing(focused, shape)
                            )
                            // The drag handle (top middle, clear of the widgets' own corner keys): drag it to put the
                            // widget elsewhere.
                            Box(
                                Modifier.align(Alignment.TopCenter).padding(4.dp).size(width = 56.dp, height = 36.dp).clip(RoundedCornerShape(50))
                                    .background(if (dragging) ShelfTheme.accent else Color.Black.copy(alpha = 0.55f))
                                    .pointerInput(id) {
                                        detectDragGestures(
                                            onDragStart = { dragId = id; selected = id; dragOffset = Offset.Zero },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragOffset += amount
                                                val vis = gridState.layoutInfo.visibleItemsInfo
                                                val me = vis.firstOrNull { it.key == id } ?: return@detectDragGestures
                                                val cx = me.offset.x + me.size.width / 2f + dragOffset.x
                                                val cy = me.offset.y + me.size.height / 2f + dragOffset.y
                                                val over = vis.firstOrNull { it.key != id && cx >= it.offset.x && cx <= it.offset.x + it.size.width && cy >= it.offset.y && cy <= it.offset.y + it.size.height }
                                                if (over != null) {
                                                    val to = order.indexOf(over.key as String)
                                                    if (to >= 0) {
                                                        order = WidgetBoardItems.moveTo(order, id, to)
                                                        // (It takes that one's place: kept under the finger.)
                                                        dragOffset += Offset((me.offset.x - over.offset.x).toFloat(), (me.offset.y - over.offset.y).toFloat())
                                                    }
                                                }
                                            },
                                            onDragEnd = { WidgetBoardItems.set(order); dragId = null; dragOffset = Offset.Zero },
                                            onDragCancel = { WidgetBoardItems.set(order); dragId = null; dragOffset = Offset.Zero }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.material3.Icon(
                                    androidx.compose.material.icons.Icons.Filled.DragHandle, contentDescription = "Drag to move",
                                    tint = if (dragging) onAccentColor() else Color.White, modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

/**
 * Update 299: the picked widget's tools, above the board — move it before / after, its size (cycles through the sizes
 * it allows), back to its default size, remove. Round 40 dp keys: easy to hit on the small lower screen, and each a
 * controller stop with the shared focus ring.
 */
@Composable
private fun WidgetEditBar(
    id: String, index: Int, count: Int, size: WidgetSize, allowed: List<WidgetSize>, modifier: Modifier,
    onMove: (Int) -> Unit, onSize: (WidgetSize) -> Unit, onReset: () -> Unit, onRemove: () -> Unit
) {
    Row(
        modifier.padding(6.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.72f)).padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        if (index > 0) EditKey(androidx.compose.material.icons.Icons.Filled.ChevronLeft, "Move earlier", "wb-left") { onMove(-1) }
        if (index < count - 1) EditKey(androidx.compose.material.icons.Icons.Filled.ChevronRight, "Move later", "wb-right") { onMove(1) }
        if (allowed.size > 1) {
            Box(
                Modifier.height(40.dp).clip(RoundedCornerShape(50)).background(ShelfTheme.accent.copy(alpha = 0.85f))
                    .shelfClickable(RoundedCornerShape(50)) { onSize(size.next(allowed)) }
                    .padTarget("wb-size") { onSize(size.next(allowed)) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) { Text(size.label, color = onAccentColor(), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
        }
        if (size != WidgetSize.NORMAL) EditKey(androidx.compose.material.icons.Icons.Filled.Refresh, "Reset size", "wb-reset", onClick = onReset)
        EditKey(androidx.compose.material.icons.Icons.Filled.Close, "Remove", "wb-remove", danger = true, onClick = onRemove)
    }
}

@Composable
private fun EditKey(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, padId: String, danger: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape)
            .background(if (danger) com.shelf.launcher.ui.theme.Ds.danger.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.12f))
            .shelfClickable(CircleShape) { onClick() }
            .padTarget(padId) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Icon(icon, contentDescription = desc, tint = if (danger) com.shelf.launcher.ui.theme.Ds.danger else Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun NowPlayingWidget(nowPlaying: NowPlaying?, onPlayPause: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit, size: WidgetSize = WidgetSize.NORMAL) {
    // (Update 299: its cover follows the widget's size.)
    val artSize = if (size == WidgetSize.COMPACT) 84.dp else 110.dp
    Row(Modifier.fillMaxSize().padding(if (size == WidgetSize.COMPACT) 10.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
        val art = nowPlaying?.art
        Box((if (size == WidgetSize.EXPANDED) Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true) else Modifier.size(artSize)).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f))) {
            if (art != null) androidx.compose.foundation.Image(
                bitmap = art.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("NOW PLAYING", color = ShelfTheme.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            FullText(
                nowPlaying?.title ?: "Nothing playing", color = ShelfTheme.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2
            )
            if (size != WidgetSize.COMPACT) nowPlaying?.artist?.let { FullText(it, color = ShelfTheme.textMuted, fontSize = 12.sp, maxLines = 1) }
            Spacer(Modifier.height(if (size == WidgetSize.COMPACT) 4.dp else 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("◀◀", accent = false, modifier = Modifier.padTarget("wb-np-prev", onPress = onPrevious), onClick = onPrevious)
                Chip(if (nowPlaying?.isPlaying == true) "Pause" else "Play", modifier = Modifier.padTarget("wb-np-play", onPress = onPlayPause), onClick = onPlayPause)
                Chip("▶▶", accent = false, modifier = Modifier.padTarget("wb-np-next", onPress = onNext), onClick = onNext)
            }
        }
    }
}

/** Battery, memory, storage and the time, refreshed every couple of seconds while the page is on screen. */
@Composable
private fun SystemStatsWidget(size: WidgetSize = WidgetSize.NORMAL) {
    val context = LocalContext.current
    var battery by remember { mutableStateOf("—") }
    var batteryFraction by remember { mutableStateOf(0f) }
    var memory by remember { mutableStateOf("—") }
    var memoryFraction by remember { mutableStateOf(0f) }
    var storage by remember { mutableStateOf("—") }
    var storageFraction by remember { mutableStateOf(0f) }
    var clock by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            runCatching {
                val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val temp = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
                if (level >= 0) {
                    batteryFraction = level.toFloat() / scale
                    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                    battery = "${(batteryFraction * 100).toInt()} %" + (if (charging) " · charging" else "") + (if (temp > 0f) String.format(java.util.Locale.US, " · %.0f °C", temp) else "")
                }
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                val mi = android.app.ActivityManager.MemoryInfo()
                am.getMemoryInfo(mi)
                val usedMem = mi.totalMem - mi.availMem
                memoryFraction = usedMem.toFloat() / mi.totalMem
                memory = "%.1f of %.1f GB".format(java.util.Locale.US, usedMem / 1e9, mi.totalMem / 1e9)
                val fs = android.os.StatFs(android.os.Environment.getDataDirectory().path)
                val total = fs.blockCountLong * fs.blockSizeLong
                val free = fs.availableBlocksLong * fs.blockSizeLong
                storageFraction = 1f - free.toFloat() / total
                storage = "%.0f GB free of %.0f".format(java.util.Locale.US, free / 1e9, total / 1e9)
                clock = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            }
            kotlinx.coroutines.delay(2_000)
        }
    }
    Column(Modifier.fillMaxSize().padding(if (size == WidgetSize.COMPACT) 10.dp else 14.dp), verticalArrangement = Arrangement.spacedBy(if (size == WidgetSize.EXPANDED) 16.dp else 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SYSTEM", color = ShelfTheme.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            Text(clock, color = ShelfTheme.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        StatBar("Battery", battery, batteryFraction)
        // (Update 299: compact shows the battery and the time only.)
        if (size != WidgetSize.COMPACT) {
            StatBar("Memory", memory, memoryFraction)
            StatBar("Storage", storage, storageFraction)
        }
    }
}

@Composable
private fun StatBar(label: String, value: String, fraction: Float) {
    Column {
        Row {
            Text(label, color = ShelfTheme.textMuted, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text(value, color = ShelfTheme.colors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(3.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.10f))) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxSize().clip(RoundedCornerShape(50)).background(ShelfTheme.accent))
        }
    }
}

/** Another app's widget, drawn by Android's own host view. */
@Composable
private fun AppWidgetView(id: Int) {
    val context = LocalContext.current
    val info = remember(id) { runCatching { AppWidgets.manager(context).getAppWidgetInfo(id) }.getOrNull() }
    if (info == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("This widget isn't available any more.", color = ShelfTheme.textMuted, fontSize = 12.sp)
        }
    } else {
        // The laid-out size (update 91: the view's own width / height were still 0 when update ran, so the
        // widget was always told its minimum size and never the real one).
        var sizePx by remember(id) { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
        AndroidView(
            factory = { ctx -> AppWidgets.host(ctx).createView(ctx, id, info) },
            update = { view ->
                if (sizePx.width > 0 && sizePx.height > 0) {
                    val dm = view.resources.displayMetrics
                    // Update 144: the widget's minimum is in pixels, so it is turned into dp too before comparing.
                    val w = (sizePx.width / dm.density).toInt().coerceAtLeast((info.minWidth / dm.density).toInt())
                    val h = (sizePx.height / dm.density).toInt().coerceAtLeast((info.minHeight / dm.density).toInt())
                    runCatching { view.updateAppWidgetSize(null, w, h, w, h) }
                }
            },
            modifier = Modifier.fillMaxSize().onSizeChanged { sizePx = it }
        )
    }
}

/**
 * Update 299: the Volume Mixer as a widget — the same mixer as MixerActivity, not a second one: the media volume is
 * Android's own (StreamRow, which follows the volume keys through SystemSync), each app's is AppMixer's (read with
 * AppMixer.apps, set with AppMixer.setVolume, numbered with its tickets), and both re-read when either side changes
 * something (AppMixer.changes). Compact: the media volume; normal: and the app playing; expanded: up to six apps.
 * The ⤢ key opens the full mixer.
 */
@Composable
private fun VolumeMixerWidget(size: WidgetSize) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(Unit) { com.shelf.launcher.device.SystemSync.start(context) }
    val sync by com.shelf.launcher.device.SystemSync.version.collectAsState()
    val changes by com.shelf.launcher.device.audio.AppMixer.changes.collectAsState()
    val perApp = remember { com.shelf.launcher.device.audio.AppMixer.available(context) }
    val showApps = size != WidgetSize.COMPACT && perApp
    var apps by remember { mutableStateOf<List<com.shelf.launcher.device.audio.AppMixer.AudioApp>?>(null) }
    // Volumes just moved here, shown over what a look in the background reads until they're saved (as the full mixer).
    val held = remember { androidx.compose.runtime.mutableStateMapOf<String, Pair<Int, Long>>() }
    val beforeMute = remember { HashMap<String, Int>() }
    val pending = remember { HashMap<String, kotlinx.coroutines.Job>() }
    suspend fun load() {
        if (!showApps) return
        apps = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { com.shelf.launcher.device.audio.AppMixer.apps(context) }.getOrNull()
        } ?: apps
    }
    LaunchedEffect(showApps, changes) { load() }
    // (A slow second look while it's on screen, as the full mixer: a game that starts making sound shows up.)
    LaunchedEffect(showApps) { while (showApps) { kotlinx.coroutines.delay(6_000L); load() } }
    fun volumeOf(a: com.shelf.launcher.device.audio.AppMixer.AudioApp): Int =
        held[a.packageName]?.takeIf { it.second > android.os.SystemClock.uptimeMillis() }?.first ?: a.volume
    fun commit(a: com.shelf.launcher.device.audio.AppMixer.AudioApp, v: Int) {
        held[a.packageName] = v to (android.os.SystemClock.uptimeMillis() + 3_000L)
        val ticket = com.shelf.launcher.device.audio.AppMixer.ticket(a.packageName)
        pending.remove(a.packageName)?.cancel()
        pending[a.packageName] = scope.launch {
            kotlinx.coroutines.delay(280)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { com.shelf.launcher.device.audio.AppMixer.setVolume(context, a.packageName, v, forGame = a.gameOnly, ticket = ticket) }
            }
        }
    }
    fun openMixer() {
        val i = com.shelf.launcher.device.audio.AppMixer.openIntent(context)
        // (On the main screen: an activity started on the second screen's own display opens under the second screen's
        // window and can't be seen — checked on the Thor.)
        val opened = if (android.os.Build.VERSION.SDK_INT >= 26) runCatching {
            context.startActivity(i, android.app.ActivityOptions.makeBasic().setLaunchDisplayId(android.view.Display.DEFAULT_DISPLAY).toBundle())
        }.isSuccess else false
        if (!opened) runCatching { context.startActivity(i) }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = if (size == WidgetSize.COMPACT) 6.dp else 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("VOLUME", color = ShelfTheme.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).shelfClickable(CircleShape) { openMixer() }.padTarget("wb-mix-open") { openMixer() },
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.OpenInFull, contentDescription = "Open the volume mixer", tint = ShelfTheme.colors.textPrimary, modifier = Modifier.size(18.dp))
            }
        }
        StreamRow("Media", android.media.AudioManager.STREAM_MUSIC, sync, padId = "wb-mix-media")
        if (size != WidgetSize.COMPACT) {
            if (!perApp) Text("Volume per app isn't available on this device — open the mixer for why.", color = ShelfTheme.textMuted, fontSize = 11.sp, lineHeight = 14.sp)
            else {
                val list = apps
                // Playing first, then the ones set to their own volume.
                val shown = list.orEmpty().sortedWith(compareByDescending<com.shelf.launcher.device.audio.AppMixer.AudioApp> { it.playing || it.runningGame != null }.thenBy { it.volume == 100 })
                    .take(if (size == WidgetSize.EXPANDED) 6 else 1)
                when {
                    list == null -> Text("Looking for apps playing sound…", color = ShelfTheme.textMuted, fontSize = 11.sp)
                    shown.isEmpty() -> Text("No app is playing sound.", color = ShelfTheme.textMuted, fontSize = 11.sp)
                    else -> Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        shown.forEach { a ->
                            val v = volumeOf(a)
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                                val icon = rememberAppIcon(a.packageName)
                                if (icon != null) androidx.compose.foundation.Image(icon, null, modifier = Modifier.size(20.dp).clip(RoundedCornerShape(5.dp)))
                                Spacer(Modifier.width(6.dp))
                                Text(a.label, color = ShelfTheme.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(if (v == 0) "Muted" else "$v %", color = ShelfTheme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            VolumeBar(
                                percent = v, steps = 20,
                                onChange = { nv -> commit(a, nv) },
                                onToggle = {
                                    if (v == 0) commit(a, beforeMute.remove(a.packageName) ?: 100)
                                    else { beforeMute[a.packageName] = v; commit(a, 0) }
                                },
                                padId = "wb-mix-${a.packageName}"
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Update 348 (Toni: "controlling the sound per screen" on dual-screen handhelds): one volume for the app on each screen —
 * the game on the top screen, the video or music on the bottom one — so either can be turned up or down without the
 * other. The apps are read from the window manager through the handheld's root service (ScreenApps: AYN / Retroid);
 * under Voltcap's own page the app last used on that screen. Each volume is the mixer's own (AppMixer: the same volume
 * the full mixer and the Volume Mixer widget show, "Just for this game" kept for a running game). A look every 3 s while
 * the widget is on screen. Without the root service or per-app volume, it says why.
 */
@Composable
private fun SoundPerScreenWidget(size: WidgetSize) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val changes by com.shelf.launcher.device.audio.AppMixer.changes.collectAsState()
    val perApp = remember { com.shelf.launcher.device.audio.AppMixer.available(context) }
    val canTell = remember { com.shelf.launcher.device.audio.ScreenApps.available }
    // This widget's own screen (the second screen it's drawn on), and the main one.
    val here = remember(context) {
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 30) context.display?.displayId
            else (context.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.displayId
        }.getOrNull() ?: -1
    }
    var screens by remember { mutableStateOf<Map<Int, com.shelf.launcher.device.audio.ScreenApps.OnScreen>?>(null) }
    var volumes by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    val held = remember { androidx.compose.runtime.mutableStateMapOf<String, Pair<Int, Long>>() }
    val beforeMute = remember { HashMap<String, Int>() }
    val pending = remember { HashMap<String, kotlinx.coroutines.Job>() }
    suspend fun look() {
        val own = context.packageName
        val got = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { com.shelf.launcher.device.audio.ScreenApps.read(own) }.getOrNull() to
                runCatching { com.shelf.launcher.device.audio.AppMixer.effective(context) }.getOrDefault(emptyMap())
        }
        got.first?.let { screens = it }
        volumes = got.second
    }
    LaunchedEffect(changes) { if (canTell && perApp) look() }
    LaunchedEffect(Unit) { while (canTell && perApp) { kotlinx.coroutines.delay(3_000L); look() } }
    fun volumeOf(pkg: String): Int = held[pkg]?.takeIf { it.second > android.os.SystemClock.uptimeMillis() }?.first ?: (volumes[pkg] ?: 100)
    fun commit(pkg: String, v: Int) {
        held[pkg] = v to (android.os.SystemClock.uptimeMillis() + 3_000L)
        val ticket = com.shelf.launcher.device.audio.AppMixer.ticket(pkg)
        pending.remove(pkg)?.cancel()
        pending[pkg] = scope.launch {
            kotlinx.coroutines.delay(280)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    // (The running game's own volume where it has one — "Just for this game" — else the app's.)
                    val g = com.shelf.launcher.device.audio.AppMixer.runningGame(context)
                    val forGame = g != null && g.packageName == pkg && com.shelf.launcher.device.audio.AppMixer.gameVolume(context, g.key) != null
                    com.shelf.launcher.device.audio.AppMixer.setVolume(context, pkg, v, forGame = forGame, ticket = ticket)
                }
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = if (size == WidgetSize.COMPACT) 6.dp else 10.dp)) {
        Text("SOUND PER SCREEN", color = ShelfTheme.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(4.dp))
        when {
            !perApp -> Text("Volume per app isn't available on this device — the Volume Mixer says why.", color = ShelfTheme.textMuted, fontSize = 11.sp, lineHeight = 14.sp)
            !canTell -> Text("This handheld doesn't let Voltcap see which app each screen shows (it needs the handheld's own root service, as on AYN and Retroid).", color = ShelfTheme.textMuted, fontSize = 11.sp, lineHeight = 14.sp)
            screens == null -> Text("Looking at the screens…", color = ShelfTheme.textMuted, fontSize = 11.sp)
            else -> Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                val main = android.view.Display.DEFAULT_DISPLAY
                // The main screen first, then this one (and any other).
                val order = listOf(main, here) + screens.orEmpty().keys.filter { it != main && it != here }
                order.distinct().forEach { d ->
                    val name = when (d) { main -> "Main screen"; here -> "This screen"; else -> "Screen $d" }
                    val on = screens.orEmpty()[d]
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Text(name.uppercase(), color = ShelfTheme.textMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                        if (on != null && !on.showing) Text("  · last used", color = ShelfTheme.textMuted, fontSize = 9.sp)
                    }
                    if (on == null) Text("Nothing but Voltcap here.", color = ShelfTheme.textMuted, fontSize = 11.sp)
                    else {
                        val pkg = on.packageName
                        val v = volumeOf(pkg)
                        val label = remember(pkg) {
                            runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                            val icon = rememberOwnAppIcon(pkg)
                            if (icon != null) androidx.compose.foundation.Image(icon, null, modifier = Modifier.size(20.dp).clip(RoundedCornerShape(5.dp)))
                            Spacer(Modifier.width(6.dp))
                            Text(label, color = ShelfTheme.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(if (v == 0) "Muted" else "$v %", color = ShelfTheme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        VolumeBar(
                            percent = v, steps = 20,
                            onChange = { nv -> commit(pkg, nv) },
                            onToggle = {
                                if (v == 0) commit(pkg, beforeMute.remove(pkg) ?: 100)
                                else { beforeMute[pkg] = v; commit(pkg, 0) }
                            },
                            padId = "wb-screen-$d"
                        )
                    }
                }
            }
        }
    }
}
