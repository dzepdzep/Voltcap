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
    onPickGame: (platform: String, game: Game) -> Unit
) {
    var open by remember { mutableStateOf<GamesFolder?>(null) }
    Box(Modifier.fillMaxSize()) {
        if (folders.isEmpty()) {
            Text("No consoles yet.", color = ShelfTheme.textMuted, fontSize = 13.sp, modifier = Modifier.align(Alignment.Center))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(folders, key = { it.platform }) { f ->
                    ConsoleFolder(f, Modifier.fillMaxWidth().aspectRatio(1.18f)) { open = f }
                }
            }
        }
        AnimatedVisibility(
            visible = open != null,
            enter = fadeIn() + scaleIn(initialScale = 0.94f),
            exit = fadeOut() + scaleOut(targetScale = 0.94f)
        ) {
            // (Kept while it fades out: the folder that was open.)
            val f = remember { mutableStateOf<GamesFolder?>(null) }
            if (open != null) f.value = open
            f.value?.let { folder ->
                FolderPopup(folder, gamesOf(folder.platform), coverOf, onClose = { open = null }) { g -> onPickGame(folder.platform, g) }
            }
        }
    }
}

/** A folder in the theme's colour (a back with its tab, a lighter front) with the console's logo on the front. */
@Composable
private fun ConsoleFolder(f: GamesFolder, modifier: Modifier, onClick: () -> Unit) {
    val accent = ShelfTheme.accent
    val back = lerp(accent, Color.Black, 0.28f)
    val frontTop = lerp(accent, Color.White, 0.12f)
    val frontBottom = accent
    Box(modifier.clip(RoundedCornerShape(10.dp)).clickable { onClick() }, contentAlignment = Alignment.Center) {
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
        // The logo on the front (it starts a quarter of the way down), never past it.
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(0.34f))
            PlatformLogo(f.platform, f.name, modifier = Modifier.fillMaxWidth(0.72f).weight(0.5f), textColor = onAccentColor())
            Spacer(Modifier.weight(0.16f))
        }
        // How many games, small in the corner of the tab.
        Text(
            "${f.count}", color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 6.dp)
        )
    }
}

/** The console's games as a grid of covers, over the Games page. */
@Composable
private fun FolderPopup(
    folder: GamesFolder,
    games: List<Game>,
    coverOf: (Game) -> String?,
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
                    Modifier.size(36.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, "Close", tint = ShelfTheme.colors.textPrimary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 78.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(games, key = { it.stableKey }) { g ->
                    Column(Modifier.clip(RoundedCornerShape(8.dp)).clickable { onPick(g) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        val cover = coverOf(g)
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.07f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (cover != null) AsyncImage(cover, g.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
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
