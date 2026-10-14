package com.shelf.launcher.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.AnimatedArt
import com.shelf.launcher.data.FolderArt
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * Update 200: custom folder icons for the second screen's Games page, made easy, and the top screen following that page.
 * - Long-press a folder on the Games page → Change icon… opens Android's picture picker (on the top screen: the second
 *   screen's window can't host it) and the picture is copied into Voltcap (FolderArt.setMine), so it stays even if the
 *   original is moved. Reset icon goes back to the pack / the drawn folder.
 * - Any picture works: PNG, JPG, WebP, and animated GIF / WebP (also APNG) play — through the image loader's own animated
 *   decoder (AnimatedArt, Android 9+; older Android shows the first frame), forced on for folder icons whatever the
 *   Animated artwork setting says, because you picked that picture on purpose.
 * - While a folder is lit (controller), touched or hovered there, the top screen shows that console big (TopScreenFolderPreview);
 *   once it's opened, the top screen switches to that console (LauncherScreen, SecondGamesPage's onHighlight).
 */

/** Update 200: the bridge between the second screen and the main screen's picture picker. */
internal object FolderIcons {
    /** A console whose icon should be picked (platform to its name), handled by [FolderIconPickerHost] on the main screen. */
    var request by mutableStateOf<Pair<String, String>?>(null)

    /** Opens the picture picker for [platform]'s folder icon. */
    fun pick(platform: String, name: String) { request = platform to name }
}

/**
 * Update 200: a folder's own picture, moving if it's an animated GIF / WebP. Static pictures load as before (the animated
 * decoder steps aside for them). [px]: the size it's decoded at, so the icon stays sharp without decoding a huge picture
 * at full size (and big enough that the loader doesn't treat it as a tiny thumbnail, which always stays still).
 */
@Composable
internal fun FolderIconImage(
    file: java.io.File,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    px: Int = 512
) {
    val context = LocalContext.current
    val v = FolderArt.version
    val request = remember(file.absolutePath, v, px) {
        coil.request.ImageRequest.Builder(context).data(file)
            .setParameter(AnimatedArt.PARAM_ANIMATE, true, memoryCacheKey = "anim")
            .size(px)
            .build()
    }
    AsyncImage(request, contentDescription, contentScale = contentScale, modifier = modifier)
}

/** Update 200: [platform]'s folder picture (your own first, then the pack's), or null. */
@Composable
internal fun rememberFolderIconFile(platform: String): java.io.File? {
    val context = LocalContext.current.applicationContext
    val v = FolderArt.version
    return remember(platform, v) { runCatching { FolderArt.find(context, platform) }.getOrNull() }
}

/**
 * Update 200: the picture picker for [FolderIcons.pick], placed once in the main screen's composition (the activity): the
 * second screen's window has no activity of its own to start the picker from. The picture is copied into Voltcap.
 */
@Composable
internal fun FolderIconPickerHost() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    // (Saveable: Android may recreate Voltcap while its picker is open.)
    var pickingFor by rememberSaveable { mutableStateOf<String?>(null) }
    var pickingName by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val p = pickingFor
        val name = pickingName ?: p
        pickingFor = null
        pickingName = null
        if (uri != null && p != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { FolderArt.setMine(context, p, uri) }
            android.widget.Toast.makeText(
                context, if (ok) "New icon for $name." else "Couldn't use that picture.", android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
    val request = FolderIcons.request
    LaunchedEffect(request) {
        if (request != null) {
            FolderIcons.request = null
            pickingFor = request.first
            pickingName = request.second
            runCatching { launcher.launch(arrayOf("image/*")) }.onFailure { pickingFor = null; pickingName = null }
        }
    }
}

/**
 * Update 200: the long-press menu of a folder on the Games page: open it, change its icon, reset it. Drawn over the page
 * (tap beside it to close); reachable with the controller too (its own layer, like a folder's popup).
 */
@Composable
internal fun FolderIconMenu(
    folder: GamesFolder,
    hasOwnIcon: Boolean,
    onOpen: () -> Unit,
    onChange: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.width(260.dp).clip(shape)
                .background(ShelfTheme.colors.surface)
                .border(1.dp, ShelfTheme.accent.copy(alpha = 0.5f), shape)
                // Taps on the menu itself stay on it.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(vertical = 6.dp)
        ) {
            Text(
                folder.name, color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            FolderMenuItem("Open", "foldermenu-open", onOpen)
            FolderMenuItem("Change icon…", "foldermenu-change", onChange)
            if (hasOwnIcon) FolderMenuItem("Reset icon", "foldermenu-reset", onReset)
            FolderMenuItem("Close", "foldermenu-close", onClose)
            Text(
                "Any picture works: PNG, JPG, WebP, and animated GIF / WebP play. The picker opens on the top screen.",
                color = ShelfTheme.textMuted, fontSize = 11.sp, lineHeight = 14.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun FolderMenuItem(text: String, id: String, onClick: () -> Unit) {
    Text(
        text, color = ShelfTheme.colors.textPrimary, fontSize = 14.sp,
        modifier = Modifier.fillMaxWidth().padTarget(id) { onClick() }.clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp)
    )
}

/**
 * Update 200: the main (top) screen's view of the console lit on the second screen's Games page: its icon big (moving if
 * it's a GIF / WebP; without one, its logo), its name and how many games, over the dimmed home. Fades in and out; it
 * takes no touches or focus, so the home underneath and the second screen keep working. [folder] null: hidden.
 */
@Composable
internal fun TopScreenFolderPreview(folder: GamesFolder?, modifier: Modifier = Modifier) {
    // (Kept while it fades out: the folder that was shown.)
    val shown = remember { mutableStateOf<GamesFolder?>(null) }
    if (folder != null) shown.value = folder
    AnimatedVisibility(
        visible = folder != null,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(200)),
        modifier = modifier
    ) {
        shown.value?.let { f ->
            BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.TopCenter) {
                val iconSize = minOf(maxHeight * 0.42f, maxWidth * 0.3f, 260.dp)
                val pic = rememberFolderIconFile(f.platform)
                val shape = RoundedCornerShape(28.dp)
                Column(
                    Modifier.padding(top = maxHeight * 0.1f).widthIn(max = 560.dp).clip(shape)
                        .background(ShelfTheme.colors.surface.copy(alpha = 0.9f))
                        .border(1.dp, ShelfTheme.accent.copy(alpha = 0.55f), shape)
                        .padding(horizontal = 36.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (pic != null) {
                        FolderIconImage(
                            pic, f.name, contentScale = ContentScale.Fit, px = 720,
                            modifier = Modifier.size(iconSize).clip(RoundedCornerShape(20.dp))
                        )
                    } else {
                        PlatformLogo(
                            f.platform, f.name,
                            modifier = Modifier.width(iconSize * 1.5f).height(iconSize * 0.6f),
                            textColor = ShelfTheme.colors.textPrimary, textSize = 28.sp
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        f.name, color = ShelfTheme.colors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (f.count == 1) "1 game" else "${f.count} games",
                        color = ShelfTheme.textMuted, fontSize = 15.sp, textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
