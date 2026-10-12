package com.shelf.launcher.ui

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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

    fun list(): List<String> = Features.widgetBoardItems.value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    fun set(items: List<String>) { Features.widgetBoardItems.value = items.distinct().joinToString(",") }
    fun add(item: String) { if (item !in list()) set(list() + item) }
    fun remove(item: String) { set(list() - item) }
    fun move(item: String, by: Int) {
        val l = list().toMutableList()
        val i = l.indexOf(item)
        if (i < 0) return
        val j = (i + by).coerceIn(0, l.size - 1)
        l.add(j, l.removeAt(i))
        set(l)
    }
}

/** One AppWidgetHost for the whole app (Android wants a stable host id). */
object AppWidgets {
    private const val HOST_ID = 0x564F
    private var host: AppWidgetHost? = null

    fun host(context: Context): AppWidgetHost =
        host ?: AppWidgetHost(context.applicationContext, HOST_ID).also { host = it }

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
}

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
    var editing by remember { mutableStateOf(false) }
    // Widgets from other apps only update while the host listens.
    DisposableEffect(Unit) {
        val host = AppWidgets.host(context)
        runCatching { host.startListening() }
        onDispose { runCatching { host.stopListening() } }
    }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Widgets", color = ShelfTheme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Chip(if (editing) "Done" else "Edit", accent = editing) { editing = !editing }
        }
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No widgets yet. Add some in Settings › Screens › Second screen tools.",
                    color = ShelfTheme.textMuted, fontSize = 13.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(260.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Update 112: every widget the same size (the screen mirror used to take a whole wide, tall row).
                itemsIndexed(items, key = { _, id -> id }) { index, id ->
                    val tall = false
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(if (tall) 320.dp else 190.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(ShelfTheme.colors.surface.copy(alpha = 0.78f))
                    ) {
                        when {
                            id == WidgetBoardItems.NOW_PLAYING -> NowPlayingWidget(nowPlaying, onPlayPause, onNext, onPrevious)
                            id == WidgetBoardItems.SYSTEM_STATS -> SystemStatsWidget()
                            id == WidgetBoardItems.SCREEN_MIRROR -> ScreenMirrorWidget(Modifier.fillMaxSize())
                            id.startsWith("app:") -> id.removePrefix("app:").toIntOrNull()?.let { AppWidgetView(it) }
                        }
                        if (editing) Row(
                            Modifier.align(Alignment.TopStart).padding(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (index > 0) Chip("◀", accent = false) { WidgetBoardItems.move(id, -1) }
                            if (index < items.size - 1) Chip("▶", accent = false) { WidgetBoardItems.move(id, 1) }
                            Chip("Remove", accent = false) {
                                if (id.startsWith("app:")) id.removePrefix("app:").toIntOrNull()?.let { AppWidgets.delete(context, it) }
                                WidgetBoardItems.remove(id)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingWidget(nowPlaying: NowPlaying?, onPlayPause: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit) {
    Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        val art = nowPlaying?.art
        Box(Modifier.size(110.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f))) {
            if (art != null) androidx.compose.foundation.Image(
                bitmap = art.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("NOW PLAYING", color = ShelfTheme.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(
                nowPlaying?.title ?: "Nothing playing", color = ShelfTheme.colors.textPrimary, fontSize = 15.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(15.sp), maxFontSize = 15.sp),
                fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            nowPlaying?.artist?.let { Text(it, color = ShelfTheme.textMuted, fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("◀◀", accent = false, onClick = onPrevious)
                Chip(if (nowPlaying?.isPlaying == true) "Pause" else "Play", onClick = onPlayPause)
                Chip("▶▶", accent = false, onClick = onNext)
            }
        }
    }
}

/** Battery, memory, storage and the time, refreshed every couple of seconds while the page is on screen. */
@Composable
private fun SystemStatsWidget() {
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
    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SYSTEM", color = ShelfTheme.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            Text(clock, color = ShelfTheme.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        StatBar("Battery", battery, batteryFraction)
        StatBar("Memory", memory, memoryFraction)
        StatBar("Storage", storage, storageFraction)
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
