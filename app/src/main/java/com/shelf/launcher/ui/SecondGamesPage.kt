package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.foundation.gestures.animateScrollBy
import kotlinx.coroutines.launch
import com.shelf.launcher.data.Game
import com.shelf.launcher.ui.theme.ShelfTheme

/*
 * Update 122: the second screen's Games page. Every console as a folder in your theme's colour with its logo on the
 * front (PlatformLogos.kt); a tap opens a popup over the page with that console's games as a grid of covers, and a
 * tap on a game takes the main screen to it (its console, the game selected, its game window open). Touch, like the
 * rest of the second screen; the controller keeps driving the main screen.
 */

/** One console on the Games page. */
internal data class GamesFolder(val platform: String, val name: String, val count: Int)

@Composable
internal fun SecondGamesPage(
    folders: List<GamesFolder>,
    /** The console's games, in the order its row shows them. */
    gamesOf: (String) -> List<Game>,
    coverOf: (Game) -> String?,
    onPickGame: (platform: String, game: Game) -> Unit,
    /** Update 185: a game's wide picture (banner / hero), for the Wide look of the games inside a console. */
    wideOf: (Game) -> String? = { null },
    /**
     * Update 195: the console (and game) lit or opened here, so the main screen follows it — its category, the game
     * selected — without opening anything.
     */
    onHighlight: (platform: String, game: Game?) -> Unit = { _, _ -> },
    /**
     * Update 200: the folder lit (controller), touched or hovered here while no folder is open — the top screen shows that
     * console big; null when there's none (also when this page goes away).
     */
    onHover: (GamesFolder?) -> Unit = {}
) {
    var open by remember { mutableStateOf<GamesFolder?>(null) }
    // Update 200: a folder's long-press menu (Open / Change icon… / Reset icon).
    var menuFor by remember { mutableStateOf<GamesFolder?>(null) }
    // Update 200: Settings › Screens › Second screen Games page › Top screen follows the Games page (on by default).
    val follow = com.shelf.launcher.data.Features.secondTopFollows.state.value
    // Update 195: the main screen follows: a folder opened (tap or A) shows that console there; with the controller here,
    // the game that's lit inside it does too (a moment after it stops moving). (Update 200: a folder only lit no longer
    // switches the top screen's console — it shows the folder big there instead, onHover below.)
    val latestHighlight by androidx.compose.runtime.rememberUpdatedState(onHighlight)
    androidx.compose.runtime.LaunchedEffect(open, follow) { if (follow) open?.let { latestHighlight(it.platform, null) } }
    val padOn = SecondPad.active
    val lit = SecondPad.focused
    // Update 200: the folder under a finger (pressed) or a mouse / stylus (hovered), reported by each folder.
    var touchFolder by remember { mutableStateOf<String?>(null) }
    val litFolder = if (padOn && lit != null && lit.startsWith("folder-")) lit.removePrefix("folder-") else null
    val hoverPlatform = if (!follow || open != null || menuFor != null) null else touchFolder ?: litFolder
    val latestHover by androidx.compose.runtime.rememberUpdatedState(onHover)
    androidx.compose.runtime.LaunchedEffect(hoverPlatform, folders) {
        val f = hoverPlatform?.let { p -> folders.firstOrNull { it.platform == p } }
        // (A short wait first, so a quick tap that opens the folder, or the controller passing over, doesn't flash it up top.)
        if (f != null) kotlinx.coroutines.delay(180)
        latestHover(f)
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { latestHover(null) } }
    androidx.compose.runtime.LaunchedEffect(lit, padOn, open) {
        val id = lit
        if (padOn && id != null && follow) {
            kotlinx.coroutines.delay(260)
            val f = open
            if (f != null && id.startsWith("game-")) {
                val key = id.removePrefix("game-")
                gamesOf(f.platform).firstOrNull { it.stableKey == key }?.let { latestHighlight(f.platform, it) }
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        if (folders.isEmpty()) {
            Text("No consoles yet.", color = ShelfTheme.textMuted, fontSize = 13.sp, modifier = Modifier.align(Alignment.Center))
        } else {
            // Update 177: the look picked in Settings › Screens › Second screen Games page, with the folder pictures.
            val look = com.shelf.launcher.data.Features.secondFolderLook.state.value
            // Update 182: the look switched right here too (Tiles by default), and the controller scrolls the grid.
            val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
            PadScroller(gridState, open == null)
            val (minSize, aspect) = when (look) {
                com.shelf.launcher.data.Features.FolderLook.TILES -> 96.dp to 1f
                com.shelf.launcher.data.Features.FolderLook.CARDS -> 150.dp to 1.78f
                com.shelf.launcher.data.Features.FolderLook.LOGOS -> 120.dp to 1.6f
                else -> 104.dp to 1.18f
            }
            Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                com.shelf.launcher.data.Features.FolderLook.values().forEach { l ->
                    val pick = { com.shelf.launcher.data.Features.secondFolderLook.value = l }
                    Chip(l.label, accent = l == look, modifier = Modifier.padTarget("gameslook-" + l.name) { pick() }) { pick() }
                    Spacer(Modifier.width(6.dp))
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = minSize),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(folders, key = { it.platform }) { f ->
                    // Update 200: a finger on the folder (or a mouse / stylus over it) shows it up top, like the controller;
                    // a long-press opens its menu (Change icon…).
                    val src = remember { MutableInteractionSource() }
                    val pressed by src.collectIsPressedAsState()
                    val hovered by src.collectIsHoveredAsState()
                    val touching = pressed || hovered
                    androidx.compose.runtime.LaunchedEffect(touching) {
                        if (touching) touchFolder = f.platform else if (touchFolder == f.platform) touchFolder = null
                    }
                    androidx.compose.runtime.DisposableEffect(f.platform) { onDispose { if (touchFolder == f.platform) touchFolder = null } }
                    val showMenu = { menuFor = f }
                    if (look == com.shelf.launcher.data.Features.FolderLook.FOLDERS) ConsoleFolder(f, Modifier.fillMaxWidth().aspectRatio(aspect), src, showMenu) { open = f }
                    else ConsoleTile(f, look, Modifier.fillMaxWidth().aspectRatio(aspect), src, showMenu) { open = f }
                }
            }
            }
        }
        // Update 200: a folder's long-press menu, on its own controller layer (over the page, under a folder's popup).
        menuFor?.let { mf ->
            val ctx = androidx.compose.ui.platform.LocalContext.current.applicationContext
            val v = com.shelf.launcher.data.FolderArt.version
            val mine = remember(mf.platform, v) { runCatching { com.shelf.launcher.data.FolderArt.hasMine(ctx, mf.platform) }.getOrDefault(false) }
            androidx.compose.runtime.CompositionLocalProvider(LocalPadLayer provides 2) {
                FolderIconMenu(
                    folder = mf,
                    hasOwnIcon = mine,
                    onOpen = { menuFor = null; open = mf },
                    // The picker opens on the top screen, so the controller goes back up with it.
                    onChange = { menuFor = null; FolderIcons.pick(mf.platform, mf.name); SecondPad.leave() },
                    onReset = { menuFor = null; com.shelf.launcher.data.FolderArt.clearMine(ctx, mf.platform) },
                    onClose = { menuFor = null }
                )
            }
        }
        AnimatedVisibility(
            visible = open != null,
            // Update 200: a set ~220 ms scale + fade (it used the loose default springs), in step with the second screen's
            // page slide and gliding highlight; the highlight follows the games as the popup scales in.
            enter = fadeIn(androidx.compose.animation.core.tween(220)) +
                scaleIn(androidx.compose.animation.core.tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing), initialScale = 0.92f),
            exit = fadeOut(androidx.compose.animation.core.tween(180)) +
                scaleOut(androidx.compose.animation.core.tween(180, easing = androidx.compose.animation.core.FastOutLinearInEasing), targetScale = 0.94f)
        ) {
            // (Kept while it fades out: the folder that was open.)
            val f = remember { mutableStateOf<GamesFolder?>(null) }
            if (open != null) f.value = open
            f.value?.let { folder ->
                // (Update 195: the popup's controls on their own layer — the controller stays inside it.)
                androidx.compose.runtime.CompositionLocalProvider(LocalPadLayer provides 1) {
                    FolderPopup(folder, gamesOf(folder.platform), coverOf, wideOf, onClose = { open = null }) { g -> onPickGame(folder.platform, g) }
                }
            }
        }
    }
}

/** A folder in the theme's colour (a back with its tab, a lighter front) with the console's logo on the front. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ConsoleFolder(f: GamesFolder, modifier: Modifier, source: MutableInteractionSource, onLongClick: () -> Unit, onClick: () -> Unit) {
    val accent = ShelfTheme.accent
    val back = lerp(accent, Color.Black, 0.28f)
    val frontTop = lerp(accent, Color.White, 0.12f)
    val frontBottom = accent
    // (Update 200: a long-press opens the folder's menu; [source] tells the page it's touched or hovered.)
    Box(
        modifier.padTarget("folder-" + f.platform) { onClick() }.clip(RoundedCornerShape(10.dp))
            .hoverable(source)
            .combinedClickable(
                interactionSource = source, indication = androidx.compose.foundation.LocalIndication.current,
                onLongClick = onLongClick, onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val left = w * 0.04f
            val right = w * 0.96f
            val top = h * 0.06f
            val bottom = h * 0.94f
            val r = CornerRadius(w * 0.06f)
            drawRoundRect(Color.Black.copy(alpha = 0.3f), Offset(left + w * 0.015f, top + h * 0.04f), Size(right - left, bottom - top), r)
            val path = Path().apply {
                moveTo(left, top + h * 0.05f)
                lineTo(left, bottom)
                lineTo(right, bottom)
                lineTo(right, top + h * 0.12f)
                lineTo(left + w * 0.42f, top + h * 0.12f)
                lineTo(left + w * 0.35f, top)
                lineTo(left + w * 0.05f, top)
                close()
            }
            drawPath(path, back)
            val frontY = top + h * 0.2f
            drawRoundRect(
                Brush.verticalGradient(listOf(frontTop, frontBottom), frontY, bottom),
                Offset(left, frontY), Size(right - left, bottom - frontY), r
            )
            // A thin light line along the front's top edge, like the edge of a sheet.
            drawLine(Color.White.copy(alpha = 0.35f), Offset(left + r.x, frontY + 1f), Offset(right - r.x, frontY + 1f), strokeWidth = 2f)
        }
        // Update 177: the folder's picture (FolderArt) on its front. (Update 200: animated GIF / WebP ones move.)
        val pic = rememberFolderPicture(f.platform)
        if (pic != null) {
            Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.weight(0.26f))
                FolderIconImage(pic, null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().weight(0.68f).padding(horizontal = 6.dp).clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)))
                Spacer(Modifier.weight(0.06f))
            }
        }
        // The logo on the front (it starts a quarter of the way down), never past it.
        if (pic == null || com.shelf.launcher.data.Features.secondFolderLogo.state.value) Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(0.34f))
            PlatformLogo(f.platform, f.name, modifier = Modifier.fillMaxWidth(0.72f).weight(0.5f), textColor = if (pic != null) Color.White else onAccentColor())
            Spacer(Modifier.weight(0.16f))
        }
        // How many games, small in the corner of the tab.
        Text(
            "${f.count}", color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 6.dp)
        )
    }
}

/** Update 177: the folder picture for [platform] (FolderArt), or null. */
@Composable
private fun rememberFolderPicture(platform: String): java.io.File? {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val v = com.shelf.launcher.data.FolderArt.version
    return remember(platform, v) { runCatching { com.shelf.launcher.data.FolderArt.find(context, platform) }.getOrNull() }
}

/** Update 177: a console as a tile, a wide card or its logo alone: its picture (FolderArt) filling it, else the theme's colours. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ConsoleTile(
    f: GamesFolder, look: com.shelf.launcher.data.Features.FolderLook, modifier: Modifier,
    source: MutableInteractionSource, onLongClick: () -> Unit, onClick: () -> Unit
) {
    val accent = ShelfTheme.accent
    val shape = RoundedCornerShape(if (look == com.shelf.launcher.data.Features.FolderLook.TILES) 14.dp else 12.dp)
    val pic = if (look == com.shelf.launcher.data.Features.FolderLook.LOGOS) null else rememberFolderPicture(f.platform)
    val logoOn = pic == null || com.shelf.launcher.data.Features.secondFolderLogo.state.value
    Box(
        modifier.padTarget("folder-" + f.platform) { onClick() }.clip(shape)
            .background(
                if (look == com.shelf.launcher.data.Features.FolderLook.LOGOS) Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.04f)))
                else Brush.linearGradient(listOf(lerp(accent, Color.White, 0.12f), lerp(accent, Color.Black, 0.3f)))
            )
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
            // (Update 200: a long-press opens the folder's menu; [source] tells the page it's touched or hovered.)
            .hoverable(source)
            .combinedClickable(
                interactionSource = source, indication = androidx.compose.foundation.LocalIndication.current,
                onLongClick = onLongClick, onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (pic != null) {
            // (Update 200: animated GIF / WebP pictures move.)
            FolderIconImage(pic, f.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (logoOn) Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))))
        }
        if (logoOn) {
            val wide = look == com.shelf.launcher.data.Features.FolderLook.CARDS
            Box(Modifier.fillMaxSize().padding(10.dp), contentAlignment = if (pic != null) (if (wide) Alignment.BottomStart else Alignment.BottomCenter) else Alignment.Center) {
                PlatformLogo(
                    f.platform, f.name,
                    modifier = Modifier.fillMaxWidth(if (pic != null) (if (wide) 0.45f else 0.7f) else 0.72f).fillMaxHeight(if (pic != null) 0.3f else 0.5f),
                    textColor = if (pic != null || look == com.shelf.launcher.data.Features.FolderLook.LOGOS) Color.White else onAccentColor()
                )
            }
        }
        Text(
            "${f.count}", color = Color.White.copy(alpha = 0.9f), fontSize = 9.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/** The console's games as a grid of covers, over the Games page. */
@Composable
private fun FolderPopup(
    folder: GamesFolder,
    games: List<Game>,
    coverOf: (Game) -> String?,
    wideOf: (Game) -> String?,
    onClose: () -> Unit,
    onPick: (Game) -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    // A tap beside the panel closes it.
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxSize().padding(6.dp).clip(shape)
                .background(ShelfTheme.colors.surface.copy(alpha = 0.96f))
                .border(1.dp, ShelfTheme.accent.copy(alpha = 0.5f), shape)
                // Taps on the panel itself stay on it.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(12.dp)
        ) {
            Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                PlatformLogo(folder.platform, folder.name, Modifier.width(120.dp).fillMaxHeight(), textColor = ShelfTheme.colors.textPrimary)
                Spacer(Modifier.width(10.dp))
                Text(
                    "${games.size} GAMES", color = ShelfTheme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    Modifier.size(36.dp).padTarget("folder-close") { onClose() }.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, "Close", tint = ShelfTheme.colors.textPrimary, modifier = Modifier.size(20.dp))
                }
            }
            // Update 185: how the games inside look — Tiles (default), Covers, Wide or a List — picked right here.
            val look = com.shelf.launcher.data.Features.secondGamesLook.state.value
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                com.shelf.launcher.data.Features.FolderGamesLook.values().forEach { l ->
                    val pick = { com.shelf.launcher.data.Features.secondGamesLook.value = l }
                    Chip(l.label, accent = l == look, modifier = Modifier.padTarget("gamelook-" + l.name) { pick() }) { pick() }
                    Spacer(Modifier.width(6.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            val gamesGrid = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
            PadScroller(gamesGrid, true)
            val cols = when (look) {
                com.shelf.launcher.data.Features.FolderGamesLook.TILES -> GridCells.Adaptive(minSize = 92.dp)
                com.shelf.launcher.data.Features.FolderGamesLook.WIDE -> GridCells.Adaptive(minSize = 160.dp)
                com.shelf.launcher.data.Features.FolderGamesLook.LIST -> GridCells.Fixed(1)
                else -> GridCells.Adaptive(minSize = 78.dp)
            }
            LazyVerticalGrid(
                columns = cols,
                state = gamesGrid,
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(if (look == com.shelf.launcher.data.Features.FolderGamesLook.LIST) 6.dp else 10.dp)
            ) {
                items(games, key = { it.stableKey }) { g ->
                    val tap = Modifier.padTarget("game-" + g.stableKey) { onPick(g); SecondPad.leave() }.clip(RoundedCornerShape(8.dp)).clickable { onPick(g) }
                    if (look == com.shelf.launcher.data.Features.FolderGamesLook.LIST) {
                        Row(tap.fillMaxWidth().height(54.dp).background(Color.White.copy(alpha = 0.05f)).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            val cover = coverOf(g)
                            Box(Modifier.fillMaxHeight().aspectRatio(0.72f).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.07f))) {
                                // (Update 202: the cover's file's flat strips trimmed — TrimMargins.kt; Crop fills the thumb.)
                                if (cover != null) AsyncImage(rememberStyleArtRequest(cover, trim = true), g.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                gameDisplayName(g.name), color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                            )
                        }
                    } else Column(tap, horizontalAlignment = Alignment.CenterHorizontally) {
                        val wide = look == com.shelf.launcher.data.Features.FolderGamesLook.WIDE
                        val pic = if (wide) wideOf(g) ?: coverOf(g) else coverOf(g)
                        val aspect = when (look) {
                            com.shelf.launcher.data.Features.FolderGamesLook.TILES -> 1f
                            com.shelf.launcher.data.Features.FolderGamesLook.WIDE -> 1.78f
                            else -> 0.72f
                        }
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(aspect).clip(RoundedCornerShape(if (look == com.shelf.launcher.data.Features.FolderGamesLook.TILES) 12.dp else 6.dp))
                                .background(Color.White.copy(alpha = 0.07f)),
                            contentAlignment = Alignment.Center
                        ) {
                            // (Update 202: a cover with its file's flat strips trimmed — TrimMargins.kt; Crop fills the tile.)
                            if (pic != null) AsyncImage(rememberStyleArtRequest(pic, trim = pic == coverOf(g)), g.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else Text(
                                gameDisplayName(g.name), color = ShelfTheme.colors.textPrimary, fontSize = 10.sp, textAlign = TextAlign.Center,
                                maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(6.dp)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            gameDisplayName(g.name), color = ShelfTheme.colors.textPrimary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Update 182: while [on], the controller on the second screen scrolls [state] a row when ▲ ▼ find nothing further on
 * screen (the grid's next rows aren't made until they scroll in), so every console and game can be reached.
 */
@Composable
internal fun PadScroller(state: androidx.compose.foundation.lazy.grid.LazyGridState, on: Boolean) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val here = LocalOnSecondScreen.current
    androidx.compose.runtime.DisposableEffect(state, on, here) {
        val hook: ((Int) -> Boolean)? = if (!on || !here) null else { dir ->
            val can = if (dir == 3) state.canScrollForward else state.canScrollBackward
            if (can) {
                val step = (state.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.height ?: 200).toFloat() + 30f
                scope.launch { state.animateScrollBy(if (dir == 3) step else -step) }
            }
            can
        }
        if (hook != null) SecondPad.scroller = hook
        onDispose { if (hook != null && SecondPad.scroller === hook) SecondPad.scroller = null }
    }
}
