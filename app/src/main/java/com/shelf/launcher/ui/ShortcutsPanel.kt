package com.shelf.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.Game
import com.shelf.launcher.ui.theme.ShelfTheme

/**
 * Pinned app shortcuts (L1), laid out like a home screen: an icon with its
 * name underneath. "Edit" puts a remove badge on every icon; the picker adds
 * more. Separate from the game library on purpose.
 */
@Composable
fun ShortcutsPanel(
    shortcuts: List<Game>,
    installedApps: List<Game>,
    onLaunch: (Game) -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onReorder: (List<String>) -> Unit = {},
    onDismiss: () -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("ShortcutsPanel")
    var picking by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    // Edit mode works on its own copy of the order, saved on every drop.
    var order by remember { mutableStateOf(shortcuts) }
    var holding by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(shortcuts) { if (holding == null) order = shortcuts }
    fun drop() {
        holding = null
        onReorder(order.mapNotNull { it.packageName })
    }

    ShelfDialog(
        icon = if (picking) Icons.Filled.Add else Icons.Filled.Apps,
        title = if (picking) "Add a shortcut" else "Shortcuts",
        subtitle = when {
            picking -> "Pick an installed app."
            editing && holding != null ->
                "Moving ${order.firstOrNull { it.packageName == holding }?.name ?: "shortcut"} — D-pad to move, A to drop."
            editing -> "A picks a shortcut up (or hold and drag it) · X or the red badge removes it."
            else -> "Apps one button away."
        },
        onDismiss = onDismiss,
        maxContentHeightFraction = 0.7f,
        footer = {
            when {
                picking -> Chip("Back", accent = false, onClick = { picking = false; query = "" })
                editing -> Chip("Done", onClick = { if (holding != null) drop(); editing = false })
                else -> {
                    if (shortcuts.isNotEmpty()) Chip("Edit", accent = false, onClick = { editing = true })
                    Chip("Add", accent = false, onClick = { picking = true })
                    Chip("Close", onClick = onDismiss)
                }
            }
        }
    ) {
        if (picking) {
            ShelfTextField(query, { query = it }, "Search apps")
            Spacer(Modifier.height(12.dp))
            val pinned = shortcuts.mapNotNull { it.packageName }.toSet()
            // Voltcap's own screens first (achievements, stats, Spotify…), then the installed apps.
            val builtins = com.shelf.launcher.data.BuiltinShortcuts.all.mapNotNull { com.shelf.launcher.data.BuiltinShortcuts.game(it.id) }
            val candidates = (builtins + installedApps)
                .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
                .filterNot { it.packageName in pinned }
            if (candidates.isEmpty()) {
                EmptyState("Nothing matches.")
            } else {
                IconGrid(candidates) { app ->
                    app.packageName?.let(onAdd)
                    picking = false
                    query = ""
                }
            }
        } else if (shortcuts.isEmpty()) {
            ShelfCard {
                EmptyState("No shortcuts yet. Add apps you want one button away — a browser, a store, a chat app.")
            }
        } else if (editing) {
            EditGrid(
                apps = order,
                holding = holding,
                onPick = { pkg ->
                    val held = holding
                    when {
                        held == null -> holding = pkg
                        held == pkg -> drop()
                        else -> {
                            // Touch: tap where the held shortcut should go.
                            val from = order.indexOfFirst { it.packageName == held }
                            val to = order.indexOfFirst { it.packageName == pkg }
                            if (from >= 0 && to >= 0) {
                                order = order.toMutableList().apply { add(to, removeAt(from)) }
                            }
                            drop()
                        }
                    }
                    Unit
                },
                onMove = { delta ->
                    val held = holding ?: return@EditGrid
                    val from = order.indexOfFirst { it.packageName == held }
                    val to = (from + delta).coerceIn(0, order.lastIndex)
                    if (from >= 0 && to != from) {
                        order = order.toMutableList().apply { add(to, removeAt(from)) }
                    }
                },
                onDrop = { if (holding != null) drop() },
                onDragStart = { pkg -> holding = pkg },
                onRemove = { pkg ->
                    if (holding == pkg) holding = null
                    order = order.filterNot { it.packageName == pkg }
                    onRemove(pkg)
                    if (order.isEmpty()) editing = false
                }
            )
        } else {
            IconGrid(shortcuts) { app -> onLaunch(app) }
        }
    }
}

/** Apps as a grid of icons with names, as many per row as fit. */
@Composable
private fun IconGrid(apps: List<Game>, removable: Boolean = false, onClick: (Game) -> Unit) {
    com.shelf.launcher.Breadcrumbs.mark("IconGrid")
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val tile = 92.dp
        val perRow = maxOf(1, (maxWidth / tile).toInt())
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            apps.chunked(perRow).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { app ->
                        AppTile(app, removable, Modifier.weight(1f)) { onClick(app) }
                    }
                    // Keep a partial last row aligned to the grid.
                    repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * The Edit-mode grid. A (or a tap) picks a shortcut up; while held, the
 * D-pad moves it through the grid (◀ ▶ one place, ▲ ▼ one row) and A drops
 * it. With touch, press and hold a shortcut and drag it where it should go.
 * X or the red badge removes.
 *
 * Tiles are placed by hand in one box (not rows), so a tile keeps its
 * identity — and a running drag — when it moves to another row.
 */
@Composable
private fun EditGrid(
    apps: List<Game>,
    holding: String?,
    onPick: (String) -> Unit,
    onMove: (Int) -> Unit,
    onDrop: () -> Unit,
    onRemove: (String) -> Unit,
    onDragStart: (String) -> Unit = {}
) {
    com.shelf.launcher.Breadcrumbs.mark("EditGrid")
    val sounds = LocalUiSounds.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Finger offset of the dragged tile from its grid slot, in px.
    var drag by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var dragging by remember { mutableStateOf<String?>(null) }
    val latestApps by androidx.compose.runtime.rememberUpdatedState(apps)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val perRow = maxOf(1, (maxWidth / 92.dp).toInt())
        val tileW = maxWidth / perRow
        // A tile is 90 dp plus two lines of its name: the row grows with a bigger font size, so rows never overlap.
        val rowH = 92.dp + with(density) { 26.sp.toDp() }
        val tileWPx = with(density) { tileW.toPx() }
        val rowHPx = with(density) { rowH.toPx() }
        val rows = (apps.size + perRow - 1) / perRow
        Box(Modifier.fillMaxWidth().height(rowH * rows.coerceAtLeast(1))) {
            apps.forEachIndexed { index, app ->
                val pkg = app.packageName
                if (pkg != null) key(pkg) {
                    val held = holding == pkg
                    val isDragging = dragging == pkg
                    val slot = androidx.compose.ui.unit.IntOffset(
                        ((index % perRow) * tileWPx).toInt(),
                        ((index / perRow) * rowHPx).toInt()
                    )
                    val animated by androidx.compose.animation.core.animateIntOffsetAsState(slot, label = "tileSlot")
                    AppTile(
                        app,
                        removable = holding == null,
                        modifier = Modifier
                            .offset {
                                if (isDragging) slot + androidx.compose.ui.unit.IntOffset(drag.x.toInt(), drag.y.toInt())
                                else animated
                            }
                            .zIndex(if (held) 1f else 0f)
                            .width(tileW)
                            .pointerInput(pkg, perRow, tileWPx, rowHPx) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        dragging = pkg
                                        drag = androidx.compose.ui.geometry.Offset.Zero
                                        onDragStart(pkg)
                                        sounds?.play(UiSound.SELECT)
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        drag += amount
                                        // Crossed into another slot: move there, keep the tile under the finger.
                                        val cols = kotlin.math.round(drag.x / tileWPx).toInt()
                                        val rowsMoved = kotlin.math.round(drag.y / rowHPx).toInt()
                                        val from = latestApps.indexOfFirst { it.packageName == pkg }
                                        val col = from % perRow
                                        val colTarget = (col + cols).coerceIn(0, perRow - 1)
                                        val target = ((from / perRow + rowsMoved) * perRow + colTarget)
                                            .coerceIn(0, latestApps.lastIndex)
                                        if (from >= 0 && target != from) {
                                            onMove(target - from)
                                            val dx = (target % perRow - col) * tileWPx
                                            val dy = (target / perRow - from / perRow) * rowHPx
                                            drag -= androidx.compose.ui.geometry.Offset(dx, dy)
                                            sounds?.play(UiSound.MOVE)
                                        }
                                    },
                                    onDragEnd = { dragging = null; drag = androidx.compose.ui.geometry.Offset.Zero; onDrop() },
                                    onDragCancel = { dragging = null; drag = androidx.compose.ui.geometry.Offset.Zero; onDrop() }
                                )
                            }
                            .onPreviewKeyEvent { event ->
                                val isBack = event.key == Key.ButtonB || event.key == Key.Back
                                // B drops on key-up, and both halves are kept from
                                // the dialog so it doesn't close as well.
                                if (held && isBack) {
                                    if (event.type == KeyEventType.KeyUp) { onDrop(); sounds?.play(UiSound.BACK) }
                                    return@onPreviewKeyEvent true
                                }
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when {
                                    held && event.key == Key.DirectionLeft -> { onMove(-1); sounds?.play(UiSound.MOVE); true }
                                    held && event.key == Key.DirectionRight -> { onMove(1); sounds?.play(UiSound.MOVE); true }
                                    held && event.key == Key.DirectionUp -> { onMove(-perRow); sounds?.play(UiSound.MOVE); true }
                                    held && event.key == Key.DirectionDown -> { onMove(perRow); sounds?.play(UiSound.MOVE); true }
                                    !held && holding == null && event.key == Key.ButtonX -> {
                                        onRemove(pkg); sounds?.play(UiSound.BACK); true
                                    }
                                    else -> false
                                }
                            },
                        lifted = held || isDragging,
                        onRemove = { onRemove(pkg) }
                    ) { onPick(pkg) }
                }
            }
        }
    }
}

@Composable
private fun AppTile(
    app: Game,
    removable: Boolean,
    modifier: Modifier,
    lifted: Boolean = false,
    onRemove: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("AppTile")
    val builtin = com.shelf.launcher.data.BuiltinShortcuts.isBuiltin(app.packageName)
    val icon = if (builtin) null else rememberAppIcon(app.packageName)
    // Styled like Voltcap's own round shortcut buttons: a soft raised card, the icon in an accent-ringed
    // circle, the same focus ring and type as the rest of the app (it used to look like a foreign grid).
    val tileShape = uiRounded(com.shelf.launcher.ui.theme.Ds.rMedium)
    Column(
        modifier
            .padding(3.dp)
            .graphicsLayer {
                val grow = if (lifted) 1.08f else 1f
                scaleX = grow
                scaleY = grow
            }
            .clip(tileShape)
            .background(if (lifted) ShelfTheme.accent.copy(alpha = 0.30f) else ShelfTheme.colors.surfaceRaised.copy(alpha = 0.55f))
            .border(if (lifted) 2.dp else 1.dp, if (lifted) ShelfTheme.accent else Color.White.copy(alpha = 0.06f), tileShape)
            .shelfClickable(tileShape) { onClick() }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        androidx.compose.ui.graphics.Brush.radialGradient(
                            listOf(ShelfTheme.accent.copy(alpha = 0.22f), ShelfTheme.colors.surface)
                        )
                    )
                    .border(1.5.dp, ShelfTheme.accent.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) Image(icon, app.name, colorFilter = themedIconFilter(), modifier = Modifier.size(36.dp).clip(uiRounded(9.dp)))
                else if (builtin) Icon(builtinIcon(app.packageName), app.name, tint = ShelfTheme.accent, modifier = Modifier.size(26.dp))
            }
            if (removable) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-6).dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(com.shelf.launcher.ui.theme.Ds.danger)
                        // QA: (C12) tap only: no second, unringed D-pad stop inside the tile (X on the tile removes).
                        .then(if (onRemove != null) Modifier.tapOnly { onRemove() } else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, "Remove", tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            app.name,
            color = ShelfTheme.colors.textPrimary,
            fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp),
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            lineHeight = androidx.compose.ui.unit.TextUnit(1.182f, androidx.compose.ui.unit.TextUnitType.Em),
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(80.dp)
        )
    }
}


/** The icon of one of Voltcap's own screens in the shortcuts panel. */
internal fun builtinIcon(id: String?): androidx.compose.ui.graphics.vector.ImageVector = when (id) {
    in R1_IDS -> R1Shortcut.icon(id)
    "voltcap:achievements" -> androidx.compose.material.icons.Icons.Filled.EmojiEvents
    "voltcap:stats" -> androidx.compose.material.icons.Icons.Filled.BarChart
    "voltcap:spotify" -> androidx.compose.material.icons.Icons.Filled.PlayCircle
    "voltcap:music" -> androidx.compose.material.icons.Icons.Filled.MusicNote
    "voltcap:saves" -> androidx.compose.material.icons.Icons.Filled.SdCard
    "voltcap:rediscover" -> androidx.compose.material.icons.Icons.Filled.History
    "voltcap:explore" -> androidx.compose.material.icons.Icons.Filled.Explore
    "voltcap:recap" -> androidx.compose.material.icons.Icons.Filled.AutoAwesome
    "voltcap:whattoplay" -> androidx.compose.material.icons.Icons.Filled.Casino
    "voltcap:categories" -> androidx.compose.material.icons.Icons.Filled.GridView
    "voltcap:screensaver" -> androidx.compose.material.icons.Icons.Filled.Nightlight
    else -> androidx.compose.material.icons.Icons.Filled.Settings
}

/** The ids of the R1 tiles that can sit in L1 ("voltcap:r1:<tile>"). */
private val R1_IDS = com.shelf.launcher.data.BuiltinShortcuts.R1_TILES.map { R1Shortcut.PREFIX + it.first }.toSet()
