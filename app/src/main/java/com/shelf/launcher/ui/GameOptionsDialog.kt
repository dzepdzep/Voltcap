package com.shelf.launcher.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.SourceType

/**
 * Everything you can do to one game, in one place — opened with Start or the
 * "…" on the card. A compact menu rather than a full dialog, so it reads as
 * a quick action sheet. (Update 125: the whole-library rows — auto-match all, refetch all icons, badge placement — are
 * gone from it; "App settings" is the last row. The game window shows these options on its own page.
 * Update 144: their unused parameters are gone too.)
 */
@Composable
fun GameOptionsDialog(
    game: Game,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEditArtwork: () -> Unit,
    onShowDetails: () -> Unit,
    onConfigureEmulator: () -> Unit,
    onLinkAchievements: () -> Unit,
    onViewAchievements: () -> Unit,
    onHideApp: () -> Unit,
    onRemoveSteamGame: () -> Unit,
    onDismiss: () -> Unit,
    usesIconArt: Boolean = false,
    onToggleIconArt: () -> Unit = {},
    isEmulator: Boolean = false,
    onToggleEmulator: () -> Unit = {},
    showCollections: Boolean = false,
    collectionCount: Int = 0,
    onCollections: () -> Unit = {},
    showStatus: Boolean = false,
    playStatus: com.shelf.launcher.data.PlayStatus? = null,
    onStatus: () -> Unit = {},
    /** Screens row (only with a second screen): the layout it starts with, and why. */
    screensSubtitle: String? = null,
    onScreens: () -> Unit = {},
    onRefetchRaIcon: () -> Unit = {},
    /** "Sort…" row: how the games in this category are ordered. */
    onSort: (() -> Unit)? = null,
    /** "Guide" row (walkthrough); null hides it. */
    onGuide: (() -> Unit)? = null,
    /** "Game settings…" row: per-game overrides with inherited/overridden state. */
    onGameSettings: (() -> Unit)? = null,
    /** "Music…" row: music of its own while you're on this game (restart on return, fades). */
    onGameMusic: (() -> Unit)? = null,
    /** Update 125: the emulator's package for the last row, "App settings" (Android games and apps use their own). */
    appSettingsPackage: String? = null,
    /** Update 195: "Cartridge shell…" (CartShellPicker.kt); null hides it. */
    onCartShell: (() -> Unit)? = null,
    /** Update 244: "Remove from Continue playing" (only for a game in it); null hides it. */
    onRemoveFromContinue: (() -> Unit)? = null
) {
    com.shelf.launcher.Breadcrumbs.mark("GameOptionsDialog")
    ShelfMenuDialog(
        title = gameDisplayName(game.name),
        subtitle = "${game.platform} · options",
        onDismiss = onDismiss,
        icon = Icons.Filled.VideogameAsset
    ) {
        MenuRow(Icons.Filled.PlayArrow, "Play", onClick = onPlay)
        MenuRow(
            if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
            if (isFavorite) "Remove from favorites" else "Add to favorites",
            onClick = onToggleFavorite
        )
        MenuRow(Icons.Filled.Info, "Details", "Score, summary, how long to beat", onClick = onShowDetails)
        if (onRemoveFromContinue != null) {
            // (Update 244: until it's played again; its play time stays.)
            MenuRow(Icons.Filled.History, "Remove from Continue playing", "Until you play it again — play time stays", onClick = onRemoveFromContinue)
        }
        if (onGuide != null) {
            MenuRow(Icons.Filled.Book, "Guide", "Walkthrough that follows where you are", onClick = onGuide)
        }
        MenuRow(Icons.Filled.Image, "Artwork & details", "Art, summary, score — pick the scraper or your own images", onClick = onEditArtwork)
        if (onCartShell != null) {
            // (Update 212: an Android game picks a disc or a cartridge here first, AndroidMedia.kt.)
            if (game.platform == "App" || game.platform == "Android") MenuRow(Icons.Filled.Memory, "Disc or cartridge…", "How this game is drawn: a disc, or a cartridge in any shell — it stays an Android game", onClick = onCartShell)
            else MenuRow(Icons.Filled.Memory, "Cartridge shell…", "Draw it on any console's cartridge or your own — it stays a ${game.platform} game", onClick = onCartShell)
        }
        if (onGameSettings != null) {
            MenuRow(Icons.Filled.Tune, "Game settings…", "Sync, second screen, launch parameters — inherited or set for this ${if (game.sourceType == SourceType.APP) "app" else "game"}", onClick = onGameSettings)
        }
        if (onGameMusic != null) {
            MenuRow(Icons.Filled.MusicNote, "Music…", "Music of its own while you're on this game", onClick = onGameMusic)
        }
        if (onSort != null) {
            MenuRow(Icons.Filled.SwapVert, "Sort…", "Order the games in this category", onClick = onSort)
        }
        if (screensSubtitle != null) {
            MenuRow(
                Icons.AutoMirrored.Filled.ScreenShare,
                "Screens",
                screensSubtitle,
                onClick = onScreens
            )
        }
        if (showStatus && game.sourceType != SourceType.APP) {
            MenuRow(
                Icons.Filled.Flag,
                "Status: " + (playStatus?.let { "${it.glyph} ${it.label}" } ?: "none"),
                "Playing, finished, dropped or on your wishlist",
                onClick = onStatus
            )
        }
        if (showCollections) {
            MenuRow(
                Icons.Filled.CollectionsBookmark,
                "Collections…",
                when (collectionCount) {
                    0 -> "Add it to one of your own lists"
                    1 -> "In 1 collection"
                    else -> "In $collectionCount collections"
                },
                onClick = onCollections
            )
        }

        if (game.sourceType == SourceType.ROM) {
            Spacer(Modifier.height(4.dp))
            MenuRow(
                Icons.Filled.VideogameAsset,
                "Emulator for ${game.platform}",
                "Which app — and core — runs this system",
                onClick = onConfigureEmulator
            )
            if (game.raGameId != null) {
                MenuRow(Icons.Filled.EmojiEvents, "Achievements", "View progress", onClick = onViewAchievements)
                MenuRow(Icons.Filled.Link, "Achievement matching", "Hash, custom hash or game ID", onClick = onLinkAchievements)
            } else {
                MenuRow(
                    Icons.Filled.Link,
                    "Achievement matching",
                    "Not linked yet — see why, or set a hash or ID",
                    onClick = onLinkAchievements
                )
            }
            if (game.raGameId != null) {
                MenuRow(
                    Icons.Filled.Refresh,
                    "Refetch RetroAchievements icon",
                    "Ask RetroAchievements again for this game's icon",
                    onClick = onRefetchRaIcon
                )
            }
        }

        if (game.sourceType == SourceType.APP) {
            Spacer(Modifier.height(4.dp))
            MenuRow(
                Icons.Filled.Apps,
                if (usesIconArt) "Use scraped art again" else "Use app icon as art",
                if (usesIconArt) "Show the art found by the scraper" else "Replace scraped art with the app's own icon",
                onClick = onToggleIconArt
            )
            MenuRow(
                Icons.Filled.VideogameAsset,
                if (isEmulator) "Not an emulator" else "Mark as emulator",
                if (isEmulator) "Remove from the Emulators category" else "Show it in the Emulators category",
                onClick = onToggleEmulator
            )
            MenuRow(Icons.Filled.VisibilityOff, "Hide from library", destructive = true, onClick = onHideApp)
            val appCtx = androidx.compose.ui.platform.LocalContext.current
            game.packageName?.let { pkg ->
                MenuRow(Icons.Filled.Delete, "Uninstall", "Android asks you to confirm", destructive = true) {
                    @Suppress("DEPRECATION")
                    runCatching {
                        appCtx.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_DELETE, android.net.Uri.parse("package:$pkg"))
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                    onDismiss()
                }
            }
        }
        if (game.sourceType == SourceType.STEAM) {
            Spacer(Modifier.height(4.dp))
            MenuRow(Icons.Filled.Delete, "Remove from library", destructive = true, onClick = onRemoveSteamGame)
        }
        // A shortcut another app added (DroidDeck's "Add to home screen"): take it out again.
        if (com.shelf.launcher.data.PinnedShortcuts.isShortcut(game)) {
            Spacer(Modifier.height(4.dp))
            val ctx = androidx.compose.ui.platform.LocalContext.current
            MenuRow(Icons.Filled.Delete, "Remove shortcut", "Takes it out of Voltcap (the game stays in its app)", destructive = true) {
                com.shelf.launcher.data.PinnedShortcuts.remove(ctx, game.stableKey)
                onDismiss()
            }
        }
        // Update 125: last, the app's own Android settings (the app itself, or the emulator that runs the game).
        val settingsPkg = if (game.sourceType == SourceType.APP) game.packageName else appSettingsPackage
        if (settingsPkg != null) {
            Spacer(Modifier.height(4.dp))
            val ctx = androidx.compose.ui.platform.LocalContext.current
            MenuRow(Icons.Filled.Info, "App settings", if (game.sourceType == SourceType.APP) "Android's page for this app" else "Android's page for its emulator") {
                runCatching {
                    ctx.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$settingsPkg"))
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                onDismiss()
            }
        }
    }
}
