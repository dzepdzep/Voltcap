package com.shelf.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.AppKind
import com.shelf.launcher.data.Game
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import com.shelf.launcher.ui.theme.ShelfTheme

/**
 * Every installed app, A–Z — opened by swiping up from the bottom edge of
 * the home screen (or the "All apps" hotkey). D-pad / tap to pick, B closes.
 */
@Composable
internal fun AppDrawer(
    apps: List<Game>,
    onLaunch: (Game) -> Unit,
    onDismiss: () -> Unit,
    /** Hidden from the library and from this drawer (Settings › Library brings it back). */
    onHide: (Game) -> Unit = {},
    /** Whether the app counts as a game right now (detected or set by hand). */
    isGame: (String) -> Boolean = { false },
    onSetKind: (String, AppKind) -> Unit = { _, _ -> }
) {
    com.shelf.launcher.Breadcrumbs.mark("AppDrawer")
    val sorted = remember(apps) { apps.sortedBy { it.name.lowercase() } }
    var menuFor by remember { mutableStateOf<Game?>(null) }
    ShelfDialog(
        icon = Icons.Filled.Apps,
        title = "All apps",
        subtitle = "${sorted.size} apps · hold an app or press Start for options",
        onDismiss = onDismiss,
        widthFraction = 0.94f,
        maxContentHeightFraction = 0.8f,
        scrollable = false
    ) {
        if (sorted.isEmpty()) {
            EmptyState("No apps found yet — they appear once Voltcap has looked through the installed apps.")
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                val perRow = maxOf(1, (maxWidth / 96.dp).toInt())
                LazyVerticalGrid(
                    columns = GridCells.Fixed(perRow),
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(sorted) { app ->
                        DrawerTile(app, Modifier.fillMaxWidth(), onMenu = { menuFor = app }) { onLaunch(app) }
                    }
                }
            }
        }
    }
    menuFor?.let { app -> AppOptionsMenu(app, isGame, onSetKind, onHide = { onHide(app) }, onDismiss = { menuFor = null }) }
}

/** Long-press / Start on an app: uninstall, Android's app info, hide, game or app. */
@Composable
private fun AppOptionsMenu(
    app: Game,
    isGame: (String) -> Boolean,
    onSetKind: (String, AppKind) -> Unit,
    onHide: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val pkg = app.packageName ?: run { onDismiss(); return }
    val game = isGame(pkg)
    ShelfDialog(icon = Icons.Filled.Apps, title = app.name, subtitle = pkg, onDismiss = onDismiss, widthFraction = 0.6f) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MenuRow(Icons.Filled.Info, "App info", "Android's page for this app: storage, permissions, force stop") {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                onDismiss()
            }
            MenuRow(
                Icons.Filled.SportsEsports,
                if (game) "Mark as app" else "Mark as game",
                if (game) "Leaves the games row; stays in the drawer" else "Shows it with your games"
            ) { onSetKind(pkg, if (game) AppKind.APP else AppKind.GAME); onDismiss() }
            MenuRow(Icons.Filled.VisibilityOff, "Hide", "From the library and this drawer. Settings › Library brings it back") {
                onHide(); onDismiss()
            }
            MenuRow(Icons.Filled.Delete, "Uninstall", "Android asks you to confirm", destructive = true) {
                runCatching {
                    @Suppress("DEPRECATION")
                    context.startActivity(
                        Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }.onFailure {
                    android.widget.Toast.makeText(context, "Android didn't open its uninstall screen", android.widget.Toast.LENGTH_SHORT).show()
                }
                onDismiss()
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DrawerTile(app: Game, modifier: Modifier, onMenu: () -> Unit, onClick: () -> Unit) {
    val icon = rememberAppIcon(app.packageName)
    val shape = RoundedCornerShape(14.dp)
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val sounds = LocalUiSounds.current
    Column(
        modifier
            .clip(shape)
            .focusRing(focused, shape)
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && (e.key == Key.ButtonStart || e.key == Key.Menu)) { onMenu(); true } else false
            }
            .combinedClickable(
                interactionSource = source,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = { sounds?.play(UiSound.SELECT); onClick() },
                onLongClick = { sounds?.play(UiSound.OPEN); onMenu() }
            )
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (icon != null) {
            Image(icon, app.name, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(13.dp)))
        } else {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(13.dp)).background(ShelfTheme.accent.copy(alpha = 0.25f)))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            app.name,
            color = Color.White,
            fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = androidx.compose.ui.unit.TextUnit(1.182f, androidx.compose.ui.unit.TextUnitType.Em)
        )
    }
}
