package com.shelf.launcher.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.MusicOverrides
import com.shelf.launcher.data.PlatformInfo
import com.shelf.launcher.data.ScreenScraperRepository
import com.shelf.launcher.data.ScreenScraperStore
import com.shelf.launcher.data.createScreenScraperApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Platform Options menu (Y on a platform in the platform browser): scrape its artwork, your own
 * picture, design its slide, its intro video and its music.
 */
@Composable
internal fun PlatformOptionsDialog(
    tile: CategoryTile,
    onDismiss: () -> Unit,
    onPickPicture: () -> Unit,
    onRemovePicture: () -> Unit,
    onDesignSlide: () -> Unit,
    onMusic: () -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("PlatformOptionsDialog")
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val platform = tile.platform
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    remember(context) { PlatformInfo.init(context); true }
    val version = PlatformInfo.version.value
    val ownIntro = remember(platform, version) { platform?.let { PlatformInfo.introVideo(it) } }
    val scraped = remember(platform, version) { platform?.let { PlatformInfo.media(it) } }
    val pickIntro = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && platform != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            PlatformInfo.setIntroVideo(platform, uri.toString())
            status = "Intro video set."
        }
    }
    ShelfMenuDialog(title = tile.label, subtitle = "Platform options", onDismiss = onDismiss, icon = Icons.Filled.Settings) {
        status?.let { MutedNote(it) }
        if (platform != null && Features.platformMediaScrape.state.value) {
            MenuRow(
                Icons.Filled.Refresh,
                if (busy) "Scraping…" else "Scrape platform media",
                if (scraped?.logo != null || scraped?.photo != null) "Done before — scrape again for newer art"
                else "Logo, photo, fan art, maker and year from ScreenScraper"
            ) {
                if (!busy) {
                    busy = true
                    status = null
                    scope.launch { status = scrapePlatformMedia(context, platform); busy = false }
                }
            }
        }
        MenuRow(Icons.Filled.Image, "Your own picture…", "Pick a picture for this platform's tile") { onDismiss(); onPickPicture() }
        if (tile.customImage != null) {
            MenuRow(Icons.Filled.Delete, "Back to the default picture", destructive = true) { onRemovePicture(); onDismiss() }
        }
        MenuRow(Icons.Filled.ViewCarousel, "Design this slide", "Place the logo, name, covers and the rest anywhere, at any size") { onDismiss(); onDesignSlide() }
        if (platform != null) {
            MenuRow(
                Icons.Filled.Videocam, "Intro video…",
                when {
                    ownIntro != null -> "Your own file is set"
                    scraped?.video != null -> "ScreenScraper's clip for this platform"
                    else -> "Pick a video to play before its games start"
                }
            ) { pickIntro.launch(arrayOf("video/*")) }
            if (ownIntro != null) {
                MenuRow(Icons.Filled.Delete, "Remove my intro video", destructive = true) { PlatformInfo.setIntroVideo(platform, null); status = "Removed." }
            }
            SwitchRow(
                Icons.Filled.Videocam, "Play its intro video",
                "After the launch animation, before one of its games starts. Skip it with any key or a tap.",
                platform !in Features.introVideoOff.state.value, { Features.introVideoOff.toggle(platform) }
            )
        }
        if (platform != null) {
            MenuRow(Icons.Filled.MusicNote, "Music…", "Music of its own while you're on this platform") { onDismiss(); onMusic() }
        }
    }
}

private suspend fun scrapePlatformMedia(context: Context, platform: String): String {
    val creds = withContext(Dispatchers.IO) { ScreenScraperStore(context).load() }
        ?: return "Set up ScreenScraper first (Settings › Metadata)."
    val media = withContext(Dispatchers.IO) {
        runCatching { ScreenScraperRepository(createScreenScraperApi()).platformMedia(creds, platform) }.getOrNull()
    } ?: return "ScreenScraper has nothing for this platform right now."
    PlatformInfo.save(platform, media)
    val got = listOfNotNull(
        media.logo?.let { "logo" }, media.photo?.let { "photo" }, media.fanart?.let { "fan art" },
        media.video?.let { "video" }, media.maker?.let { "maker" }
    )
    return if (got.isEmpty()) "ScreenScraper has no artwork for this platform." else "Found: ${got.joinToString(", ")}."
}

/** Music of its own for a platform ("p:SNES") or a game ("g:<key>"): pick, remove, restart on return. */
@Composable
internal fun MusicOverrideDialog(key: String, title: String, onDismiss: () -> Unit) {
    com.shelf.launcher.Breadcrumbs.mark("MusicOverrideDialog")
    val context = LocalContext.current
    val version = MusicOverrides.version.value
    var message by remember { mutableStateOf<String?>(null) }
    val current = remember(key, version) { MusicOverrides.name(context, key) }
    val scope = rememberCoroutineScope()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        // Copying up to 30 MB (maybe from a cloud file) happens off the main thread (update 91).
        if (uri != null) scope.launch {
            message = withContext(Dispatchers.IO) { MusicOverrides.import(context, key, uri) } ?: "Saved."
        }
    }
    ShelfMenuDialog(title = title, subtitle = "Music", onDismiss = onDismiss, icon = Icons.Filled.MusicNote) {
        MutedNote(
            if (current != null) "Plays while you're here: $current. Leaving fades it out as the next one fades in."
            else "No music of its own yet."
        )
        message?.let { MutedNote(it) }
        MenuRow(Icons.Filled.MusicNote, if (current == null) "Pick a track…" else "Pick another track…", "An audio file, up to 30 MB") { pick.launch(arrayOf("audio/*")) }
        if (current != null) {
            MenuRow(Icons.Filled.Delete, "Remove its music", destructive = true) { MusicOverrides.clear(context, key); message = "Removed." }
            SwitchRow(
                Icons.Filled.Refresh, "Restart on return",
                "Start it from the beginning each time you come back. Off: it carries on from where you left it.",
                key in Features.musicRestartOnReturn.state.value, { Features.musicRestartOnReturn.toggle(key) }
            )
        }
    }
}
