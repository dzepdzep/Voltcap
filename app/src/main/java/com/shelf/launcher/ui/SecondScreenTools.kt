package com.shelf.launcher.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.Features
import com.shelf.launcher.ui.theme.ShelfTheme

/** Settings › Appearance › Second screen tools: Widget Board, Screen Mirror, Wall, wallpaper, virtual second screen. */
@Composable
internal fun SecondScreenToolsSettings(hasSecondScreen: Boolean) {
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pair<Int, AppWidgetProviderInfo>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val raw = Features.widgetBoardItems.state.value
    val items = remember(raw) { WidgetBoardItems.list() }

    fun finish(id: Int, ok: Boolean) {
        if (ok) { WidgetBoardItems.add("app:$id"); message = "Added to the Widget Board." }
        else { AppWidgets.delete(context, id); message = "Not added." }
        pending = null
    }
    val configureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        pending?.let { finish(it.first, r.resultCode == Activity.RESULT_OK) }
    }
    fun configureOrFinish(id: Int, info: AppWidgetProviderInfo) {
        val cfg = info.configure
        if (cfg == null) finish(id, true)
        else runCatching {
            configureLauncher.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).setComponent(cfg).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        }.onFailure { finish(id, true) }
    }
    val bindLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val p = pending
        if (p != null) { if (r.resultCode == Activity.RESULT_OK) configureOrFinish(p.first, p.second) else finish(p.first, false) }
    }
    fun addWidget(info: AppWidgetProviderInfo) {
        val id = AppWidgets.host(context).allocateAppWidgetId()
        pending = id to info
        val bound = runCatching { AppWidgets.manager(context).bindAppWidgetIdIfAllowed(id, info.provider) }.getOrDefault(false)
        if (bound) configureOrFinish(id, info)
        else runCatching {
            // Android asks the person before one app may show another's widget.
            bindLauncher.launch(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            )
        }.onFailure { finish(id, false) }
    }
    val pickWallpaper = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            Features.secondScreenWallpaper.value = uri.toString()
        }
    }

    Column {
        Text("Pages on the second screen", color = ShelfTheme.textMuted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        FeatureSwitch(Icons.Filled.Widgets, "Widgets page", "The Widget Board among the second screen's tabs.", Features.secondWidgets)
        FeatureSwitch(Icons.Filled.ViewCarousel, "Wall page", "Slanted slices of your platforms' (or games') art, with its own layout for each.", Features.secondWall)
        FeatureSwitch(Icons.Filled.Cast, "Magnifier page", "The main screen's picture, enlarged, on the second screen: pinch to zoom, drag to move, double-tap for the whole picture, with a small map of where you are. Android asks once for permission to capture the screen.", Features.secondMagnifier)
        Spacer(Modifier.height(6.dp))

        Text("Widget Board", color = ShelfTheme.textMuted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        SwitchRow(Icons.Filled.MusicNote, "Now Playing", "The song, its cover and the buttons.", WidgetBoardItems.NOW_PLAYING in items, { on ->
            if (on) WidgetBoardItems.add(WidgetBoardItems.NOW_PLAYING) else WidgetBoardItems.remove(WidgetBoardItems.NOW_PLAYING)
        })
        Spacer(Modifier.height(8.dp))
        SwitchRow(Icons.Filled.Info, "System Stats", "Battery, memory, storage and the time.", WidgetBoardItems.SYSTEM_STATS in items, { on ->
            if (on) WidgetBoardItems.add(WidgetBoardItems.SYSTEM_STATS) else WidgetBoardItems.remove(WidgetBoardItems.SYSTEM_STATS)
        })
        Spacer(Modifier.height(8.dp))
        SwitchRow(Icons.Filled.Cast, "Screen Mirror", "The main screen's picture on the second screen, with zoom and trackpad modes.", WidgetBoardItems.SCREEN_MIRROR in items, { on ->
            if (on) WidgetBoardItems.add(WidgetBoardItems.SCREEN_MIRROR) else WidgetBoardItems.remove(WidgetBoardItems.SCREEN_MIRROR)
        })
        Spacer(Modifier.height(8.dp))
        FeatureChoice("Screen Mirror mode", Features.MirrorMode.values(), Features.mirrorMode) { it.label }
        MutedNote("Zoom: pinch and drag on the mirrored picture. Trackpad: drag moves a pointer on the main screen, a tap clicks (needs this handheld's system shell). Android asks for permission to capture the screen when you start it.")
        Spacer(Modifier.height(8.dp))
        items.filter { it.startsWith("app:") }.forEach { id ->
            val widgetId = id.removePrefix("app:").toIntOrNull()
            val label = widgetId?.let { runCatching { AppWidgets.manager(context).getAppWidgetInfo(it)?.loadLabel(context.packageManager) }.getOrNull() }
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(label?.toString() ?: "A widget that's gone", color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Chip("Remove", accent = false) { widgetId?.let { AppWidgets.delete(context, it) }; WidgetBoardItems.remove(id) }
            }
        }
        Chip("Add a widget from another app…") { picking = true }
        message?.let { MutedNote(it) }
        Spacer(Modifier.height(14.dp))

        Text("Wallpapers", color = ShelfTheme.textMuted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        MutedNote("The main screen's wallpaper is set under Background (\"Your wallpaper\"). The second screen can have its own:")
        ChipFlow {
            Chip(if (Features.secondScreenWallpaper.state.value.isEmpty()) "Pick a picture…" else "Pick another picture…") { pickWallpaper.launch(arrayOf("image/*")) }
            if (Features.secondScreenWallpaper.state.value.isNotEmpty()) Chip("Same as the main screen", accent = false) { Features.secondScreenWallpaper.value = "" }
        }
        Spacer(Modifier.height(14.dp))

        Text("Virtual second screen", color = ShelfTheme.textMuted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        FeatureSwitch(
            Icons.Filled.Settings, "Virtual second screen",
            if (hasSecondScreen) "For devices with one screen: the second screen opens as a panel over the main one. (This device has a real second screen, which is used instead.)"
            else "The second screen opens as a panel over the main one: a tab at the right edge, or the hotkey. The controller keeps driving the main screen.",
            Features.virtualSecondScreen
        )
    }

    if (picking) {
        val providers = remember { AppWidgets.providers(context) }
        ShelfMenuDialog(title = "Add a widget", subtitle = "From any app on this device", onDismiss = { picking = false }, icon = Icons.Filled.Widgets) {
            if (providers.isEmpty()) MutedNote("No app offers widgets here.")
            providers.forEach { (label, info) ->
                MenuRow(Icons.Filled.Widgets, label) { picking = false; addWidget(info) }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The second screen's Wall page
// ---------------------------------------------------------------------------------------------

private class WallDirection(var last: Int = 0, var dir: Int = 1)

/** The Wall on the second screen: platforms or the category's games, each with its own layout. */
@Composable
internal fun SecondWallPage(
    platformTiles: List<CategoryTile>,
    currentCategoryKey: String,
    gameTiles: List<CategoryTile>,
    focusedGame: Int,
    onPickCategory: (CategoryTile) -> Unit,
    onFocusGame: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    com.shelf.launcher.Breadcrumbs.mark("SecondWallPage")
    val ofPlatforms = Features.wallOfPlatforms.state.value
    val tiles = if (ofPlatforms) platformTiles else gameTiles
    val selected = if (ofPlatforms) platformTiles.indexOfFirst { it.key == currentCategoryKey }.coerceAtLeast(0) else focusedGame.coerceIn(0, (gameTiles.size - 1).coerceAtLeast(0))
    val holder = remember(ofPlatforms) { WallDirection(selected, 1) }
    if (selected != holder.last) { holder.dir = if (selected > holder.last) 1 else -1; holder.last = selected }
    val layoutRaw = if (ofPlatforms) Features.pbWallPlatformsLayout.state.value else Features.pbWallGamesLayout.state.value
    var layout by remember(ofPlatforms, layoutRaw) { mutableStateOf(SlideLayout.parse(layoutRaw, SlideLayout.wallDefaults)) }
    var editing by remember { mutableStateOf(false) }
    var editKey by remember { mutableStateOf("LOGO") }
    fun save() {
        val encoded = SlideLayout.encode(layout)
        if (ofPlatforms) Features.pbWallPlatformsLayout.value = encoded else Features.pbWallGamesLayout.value = encoded
    }
    fun scale(f: Float) {
        val r = layout[editKey] ?: return
        val nw = (r.w * f).coerceIn(0.05f, 1.2f)
        val nh = (r.h * f).coerceIn(0.03f, 1.2f)
        layout = layout + (editKey to FRect(r.x + (r.w - nw) / 2f, r.y + (r.h - nh) / 2f, nw, nh))
    }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            // (Update 182: every chip and the Wall itself reachable with the controller on the second screen.)
            Chip("Platforms", accent = ofPlatforms, modifier = Modifier.padTarget("wall-platforms") { Features.wallOfPlatforms.value = true }) { Features.wallOfPlatforms.value = true }
            Spacer(Modifier.width(6.dp))
            Chip("Games", accent = !ofPlatforms, modifier = Modifier.padTarget("wall-games") { Features.wallOfPlatforms.value = false }) { Features.wallOfPlatforms.value = false }
            Spacer(Modifier.weight(1f))
            if (!editing) Chip("Edit layout", accent = false, modifier = Modifier.padTarget("wall-edit") { editing = true }) { editing = true }
        }
        // Update 144: the edit chips wrap onto their own line(s), so Reset and Done always fit on the narrow second screen.
        if (editing) {
            ChipFlow(Modifier.padding(bottom = 6.dp)) {
                Chip("LOGO", accent = editKey == "LOGO") { editKey = "LOGO" }
                Chip("NAME", accent = editKey == "NAME") { editKey = "NAME" }
                Chip("COUNT", accent = editKey == "COUNT") { editKey = "COUNT" }
                Chip("–", accent = false) { scale(0.94f) }
                Chip("+", accent = false) { scale(1.06f) }
                Chip("Reset", accent = false) { layout = SlideLayout.wallDefaults; save() }
                Chip("Done", modifier = Modifier.padTarget("wall-done") { save(); editing = false }) { save(); editing = false }
            }
        }
        WallView(
            tiles = tiles, selected = selected, direction = holder.dir, animate = Features.pbAnimate.state.value,
            onFocus = { i -> tiles.getOrNull(i)?.let { if (ofPlatforms) onPickCategory(it) else onFocusGame(i) } },
            onPick = { i -> tiles.getOrNull(i)?.let { if (ofPlatforms) onPickCategory(it) else onFocusGame(i) } },
            // Update 182: the controller on the Wall: ◀ ▶ go along its slices (the main screen follows), A picks.
            modifier = Modifier.fillMaxSize().padTarget(
                "wall-view",
                onMove = { d ->
                    val step = when (d) { 0 -> -1; 1 -> 1; else -> 0 }
                    val to = selected + step
                    if (step != 0 && to in tiles.indices) {
                        tiles.getOrNull(to)?.let { if (ofPlatforms) onPickCategory(it) else onFocusGame(to) }
                        true
                    } else step != 0
                }
            ) { tiles.getOrNull(selected)?.let { if (ofPlatforms) onPickCategory(it) else onFocusGame(selected) }; SecondPad.leave() },
            layout = layout, editing = editing, editKey = editKey,
            onEditKey = { editKey = it }, onLayoutChange = { layout = it }
        )
    }
}

/** Game tiles for the Wall: one per game of the category, with its cover and wide art. */
internal fun wallGameTiles(
    games: List<com.shelf.launcher.data.Game>,
    cover: (com.shelf.launcher.data.Game) -> String?,
    wide: (com.shelf.launcher.data.Game) -> String?
): List<CategoryTile> = games.map { g ->
    CategoryTile(
        key = g.stableKey, label = g.name, subtitle = null,
        covers = listOfNotNull(cover(g)), hue = g.hue, slides = listOfNotNull(wide(g) ?: cover(g)),
        count = 1, platform = null
    )
}

/**
 * The virtual second screen: on a device with one screen, the second screen's pages open as a panel from the
 * right edge (a small tab, or the hotkey). Touch only, like the real one; the controller keeps driving the main screen.
 */
@Composable
internal fun androidx.compose.foundation.layout.BoxScope.VirtualSecondScreenLayer(enabled: Boolean, content: @Composable () -> Unit) {
    val open = Features.virtualSecondScreenOpen.state.value
    if (enabled) {
        if (!open) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .align(androidx.compose.ui.Alignment.CenterEnd)
                    .width(26.dp).height(96.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                    .background(ShelfTheme.accent.copy(alpha = 0.85f))
                    .clickable { Features.virtualSecondScreenOpen.value = true },
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) { Text("2", color = onAccentColor(), fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = open,
            enter = androidx.compose.animation.slideInHorizontally { it } + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutHorizontally { it } + androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(androidx.compose.ui.Alignment.CenterEnd)
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.62f)
                    .background(ShelfTheme.background)
                    .border(1.dp, Color.White.copy(alpha = 0.10f))
            ) {
                content()
                CloseButton({ Features.virtualSecondScreenOpen.value = false }, size = 32.dp)
            }
        }
    }
}
