package com.shelf.launcher.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.ui.unit.sp
import com.shelf.launcher.ui.theme.ShelfTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import com.shelf.launcher.data.CategoryTileArt
import com.shelf.launcher.data.Features

/*
 * What a category / platform card shows, the same in all four looks of the category screen (Tiles, Slides,
 * Wall, Grid) and on the second screen's Wall: Settings › Categories › Category screen › "Cards show".
 * Your own picture for a card (Y on it) always wins. No RetroAchievements system icons any more.
 */

/** The card's shape, so the art fits it: wide art for wide cards, covers for tall / square ones. */
internal enum class CardShape { WIDE, TALL, SQUARE }

/**
 * The picture for [tile] in [mode], filling [modifier]:
 * - Slow fade: its games' art one after the other (each card starts at its own moment, only cards on screen run,
 *   it waits while Voltcap isn't in front, and it holds still with Lite effects);
 * - Last played game: the art of the game played most recently in it (its first game when none was played);
 * - A few covers: fanned out (wide cards) / the first cover (tall or square cards);
 * - Photo of the console: its photo (consoles), else the slow fade;
 * - Just the name: nothing (the card's own colour and its name).
 */
@Composable
internal fun CategoryArt(
    tile: CategoryTile,
    shape: CardShape,
    modifier: Modifier = Modifier,
    mode: CategoryTileArt = Features.categoryArt.state.value
) {
    val custom = tile.customImage
    if (custom != null) {
        AsyncImage(custom, null, contentScale = ContentScale.Crop, modifier = modifier)
        return
    }
    val photo = rememberConsolePhoto(tile, mode)
    val wide = shape == CardShape.WIDE
    when {
        // (Update 330: Voltcap's own console pictures — drawn whole, never cropped or stretched, with room round them; on a
        // wide card to the right and up, clear of the name at the bottom left.)
        com.shelf.launcher.data.ConsoleArt.isOurs(photo) -> Box(modifier) {
            AsyncImage(
                photo, null, contentScale = ContentScale.Fit,
                modifier = when (shape) {
                    CardShape.WIDE -> Modifier.align(Alignment.TopEnd).fillMaxWidth(0.64f).fillMaxHeight(0.8f).padding(horizontal = 8.dp, vertical = 6.dp)
                    // (Tall cards and the Wall's slices carry their name in the middle or at the bottom: the console sits above it.)
                    CardShape.TALL -> Modifier.align(Alignment.TopCenter).fillMaxWidth().fillMaxHeight(0.46f).padding(horizontal = 10.dp, vertical = 14.dp)
                    CardShape.SQUARE -> Modifier.align(Alignment.Center).fillMaxSize(0.86f)
                }
            )
        }
        photo != null -> AsyncImage(photo, null, contentScale = if (wide) ContentScale.Crop else ContentScale.Fit, modifier = modifier)
        mode == CategoryTileArt.PLAIN -> Unit
        mode == CategoryTileArt.LAST_PLAYED -> {
            val url = if (wide) tile.lastPlayedArt ?: tile.lastPlayedCover ?: tile.covers.firstOrNull()
                else tile.lastPlayedCover ?: tile.coverSlides.firstOrNull() ?: tile.lastPlayedArt
            if (url != null) AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = modifier)
        }
        mode == CategoryTileArt.COVERS -> {
            if (wide) Box(modifier) { CoverFanRow(tile.covers, Modifier.align(Alignment.CenterEnd).padding(end = 10.dp)) }
            else tile.covers.firstOrNull()?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = modifier) }
        }
        else -> {
            // Slow fade (also "Photo of the console" for a card that has no photo).
            val list = if (wide) tile.slides.ifEmpty { tile.coverSlides } else tile.coverSlides.ifEmpty { tile.slides }
            SlowFade(list, tile.hue, modifier)
        }
    }
}

/** True when [CategoryArt] draws the cover fan on the right of a wide card (the name keeps to the left part then). */
internal fun categoryArtIsFan(tile: CategoryTile, mode: CategoryTileArt = Features.categoryArt.value): Boolean =
    tile.customImage == null && (mode == CategoryTileArt.COVERS && tile.covers.isNotEmpty() ||
        // (Update 330: and with Voltcap's own console picture on the right.)
        mode == CategoryTileArt.CONSOLE && tile.platform?.let { com.shelf.launcher.data.ConsoleArt.model(it) } != null)

/** [urls] one after the other, slowly cross-fading. Still (the first one) with Lite effects. */
@Composable
internal fun SlowFade(urls: List<String>, hue: Int, modifier: Modifier) {
    if (urls.isEmpty()) return
    var index by remember(urls) { mutableIntStateOf(0) }
    val lite = LocalLiteEffects.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    if (!lite && urls.size > 1) LaunchedEffect(urls, lifecycle) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            // Cards start at different moments so they don't all change at once.
            kotlinx.coroutines.delay((hue % 7) * 900L + 2_500L)
            while (true) {
                kotlinx.coroutines.delay(6_500)
                index = (index + 1) % urls.size
            }
        }
    }
    Crossfade(targetState = urls[index % urls.size], animationSpec = tween(1_400), label = "categoryFade", modifier = modifier) { url ->
        AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

/** Up to three covers side by side, overlapping a little (wide cards, "A few covers"). */
@Composable
private fun CoverFanRow(covers: List<String>, modifier: Modifier) {
    Row(modifier.fillMaxHeight(0.8f), horizontalArrangement = Arrangement.spacedBy((-18).dp), verticalAlignment = Alignment.CenterVertically) {
        covers.take(3).forEach { url ->
            AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxHeight().aspectRatio(2f / 3f).clip(uiRounded(6.dp)))
        }
    }
}

/** The console's photo for a console card with "Photo of the console" (null otherwise / while it loads). */
@Composable
internal fun rememberConsolePhoto(tile: CategoryTile, mode: CategoryTileArt): String? {
    val platform = tile.platform?.takeIf {
        mode == CategoryTileArt.CONSOLE && com.shelf.launcher.data.ConsolePhotos.hasPhoto(it)
    } ?: return null
    // (Update 330: Voltcap's own picture at once — no wait, no network; your pick of model follows at once too.)
    Features.consoleHwPick.state.value
    com.shelf.launcher.data.ConsoleArt.model(platform)?.let { return it }
    val context = androidx.compose.ui.platform.LocalContext.current
    val url by produceKeyed<String?>(null, platform, platform) { value = com.shelf.launcher.data.ConsolePhotos.url(context, platform) }
    return url
}

/**
 * Update 330: Settings › Categories › Console pictures — which model shows for the consoles that have more than one
 * (Voltcap's own pictures, ConsoleArt): the closed DS / 3DS or the open ones, PlayStation 2 or its Slim, and so on.
 */
@Composable
internal fun ConsolePicturesPicker() {
    var open by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val titles = LocalTitleOptions.current
    val picks = Features.consoleHwPick.state.value
    val multi = com.shelf.launcher.data.ConsoleArt.VARIANTS.filter { it.value.size > 1 }
    MenuRow(
        androidx.compose.material.icons.Icons.Filled.VideogameAsset,
        title = "Console pictures",
        subtitle = if (open) "Which model each console shows" else "${multi.size} consoles come in more than one model — pick which shows",
        onClick = { open = !open }
    )
    if (open) Column(Modifier.padding(start = 8.dp, top = 4.dp)) {
        multi.forEach { (key, list) ->
            val now = com.shelf.launcher.data.ConsoleArt.nameFor(key)
            androidx.compose.foundation.layout.Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(now?.let { com.shelf.launcher.data.ConsoleArt.asset(it) }, null, contentScale = ContentScale.Fit,
                    modifier = Modifier.size(60.dp, 40.dp))
                androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(titles.console(key).ifBlank { key }, color = ShelfTheme.textMuted, fontSize = 12.sp)
                    ChipFlow {
                        list.forEach { n ->
                            Chip(com.shelf.launcher.data.ConsoleArt.NAMES[n] ?: n, accent = now == n) { com.shelf.launcher.data.ConsoleArt.pick(key, n) }
                        }
                    }
                }
            }
        }
    }
    // (Read so the row follows a pick at once.)
    picks.size
}
