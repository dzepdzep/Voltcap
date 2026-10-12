package com.shelf.launcher.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.launch

/** What the first-start setup shows as already done. */
data class SetupStatus(
    val gameCount: Int = 0,
    val artConnected: Boolean = false,
    val raConnected: Boolean = false,
    val detailsConnected: Boolean = false,
    val rawgConnected: Boolean = false,
    val screenScraperConnected: Boolean = false
)

/**
 * First-start setup, like other front-ends have: games, artwork, details,
 * achievements, then the extras. Every step opens the same popup the
 * settings use; each can be skipped and done later from Settings.
 */
@Composable
fun FirstRunDialog(
    status: SetupStatus,
    onRomFolders: () -> Unit,
    onSteamGames: () -> Unit,
    onArtwork: () -> Unit,
    onRetroAchievements: () -> Unit,
    onIgdb: () -> Unit,
    onRawg: () -> Unit,
    onScreenScraper: () -> Unit,
    onFinish: () -> Unit,
    /** The button legend along the bottom of the home screen. */
    showLegend: Boolean = false,
    onShowLegendChange: (Boolean) -> Unit = {}
) {
    com.shelf.launcher.Breadcrumbs.mark("FirstRunDialog")
    val context = LocalContext.current
    var step by remember { mutableStateOf(0) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    val importScope = androidx.compose.runtime.rememberCoroutineScope()
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importScope.launch {
            // (update 91) Reading and applying the backup (files, encrypted stores) runs off the main thread.
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { com.shelf.launcher.data.Backup.import(context, uri) }
            }
            result
                .onSuccess {
                    com.shelf.launcher.data.FirstRun.markDone(context)
                    com.shelf.launcher.data.Backup.restart(context)
                }
                .onFailure { importMessage = "Couldn't import: ${it.message}" }
        }
    }
    // Update 144: everything back from Google Drive (the backup Settings › Backup › Export to Google Drive put there, or
    // the settings "Send my settings to Drive" sent), signing in to Drive first.
    var driveBusy by remember { mutableStateOf(false) }
    // Update 147: the receiving half of Share settings (PIN / QR code) shown under the Welcome question.
    var nearby by remember { mutableStateOf(false) }
    val driveConnect = rememberDriveConnect { importMessage = it; driveBusy = false }
    val fromDrive: () -> Unit = {
        driveBusy = true
        importMessage = "Connecting to Google Drive…"
        driveConnect {
            importScope.launch {
                importMessage = "Getting your backup from Google Drive…"
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { com.shelf.launcher.data.Backup.importFromDrive(context) }
                }
                    .onSuccess {
                        com.shelf.launcher.data.FirstRun.markDone(context)
                        com.shelf.launcher.data.Backup.restart(context)
                    }
                    .onFailure { importMessage = "Couldn't get it: ${it.message}"; driveBusy = false }
            }
        }
    }
    // (Update 141: "Extra art" — the art packs question is part of the setup now.)
    val steps = listOf("Welcome", "Permissions", "Your games", "Artwork", "Extra art", "Game details", "Achievements", "Finishing touches")
    val name = steps[step]
    val last = steps.lastIndex

    ShelfDialog(
        icon = when (name) {
            "Welcome" -> Icons.Filled.AutoAwesome
            "Permissions" -> Icons.Filled.Security
            "Your games" -> Icons.Filled.Folder
            "Artwork" -> Icons.Filled.Image
            "Extra art" -> Icons.Filled.CloudDownload
            "Game details" -> Icons.Filled.Info
            "Achievements" -> Icons.Filled.EmojiEvents
            else -> Icons.Filled.Home
        },
        title = if (step == 0) "Welcome to Voltcap" else steps[step],
        subtitle = "Step ${step + 1} of ${steps.size}",
        // Update 144: B / Back (or a tap outside) goes back one step instead of ending the whole
        // setup for good; only the first step closes it. "Skip setup" / "Start playing" still finish.
        onDismiss = { if (step > 0) step-- else onFinish() },
        maxContentHeightFraction = 0.7f,
        footer = {
            if (step > 0) Chip("Back", accent = false) { step-- }
            if (step < last) {
                Chip("Skip setup", accent = false, onClick = onFinish)
                Chip("Next") { step++ }
            } else {
                Chip("Start playing", onClick = onFinish)
            }
        }
    ) {
        StepDots(step, steps.size)
        Spacer(Modifier.height(14.dp))
        // What each step is for, in a line (update 122: the mascot that used to say it is gone).
        MutedNote(
            when (name) {
                "Welcome" -> "Welcome to Voltcap! Let's get your games set up."
                "Permissions" -> "A few permissions first, so Voltcap can find your games and saves."
                "Your games" -> "Organize your games, emulators and apps, all on one shelf."
                "Artwork" -> "Covers and hero art make the shelf shine. Let's fetch some!"
                "Extra art" -> "Cartridges, spines, disc shells and 3D models for the game looks. Download them now or later."
                "Game details" -> "Want summaries, scores and how long each game takes?"
                "Achievements" -> "Track achievements, playtime and more. Every unlock gets its moment!"
                else -> "Customize Voltcap to your liking. Enjoy your games!"
            }
        )
        Spacer(Modifier.height(12.dp))
        when (name) {
            "Welcome" -> {
                // First question of all: moving from another device skips the rest.
                ShelfCard {
                    Text(
                        "Have you used Voltcap on another device?",
                        color = ShelfTheme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    MutedNote(
                        "Take back the backup you made there and everything comes along — settings, accounts, artwork " +
                            "choices, saved details — and setup is done. From Google Drive: Settings › Backup › Export to " +
                            "Google Drive on the other device. From a file: Settings › Backup › Export. From another device: " +
                            "it shows a PIN or QR code (Settings › Backup › Share settings)."
                    )
                    Spacer(Modifier.height(10.dp))
                    ChipFlow {
                        Chip("Yes — from Google Drive", enabled = !driveBusy) { fromDrive() }
                        // Update 147: straight from the other Voltcap on the same Wi-Fi, with its PIN or QR code.
                        Chip(if (nearby) "✓ Yes — from another device (PIN or QR)" else "Yes — from another device (PIN or QR)", accent = nearby) { nearby = !nearby }
                        Chip("Yes — from a backup file…", accent = false) {
                            importFile.launch(arrayOf("application/json", "text/plain", "*/*"))
                        }
                        Chip("No, set up fresh", accent = false) { step++ }
                    }
                    importMessage?.let { Spacer(Modifier.height(6.dp)); MutedNote(it) }
                }
                if (nearby) {
                    Spacer(Modifier.height(12.dp))
                    SettingsShareCard(receiveOnly = true) { doc ->
                        importMessage = "Taking it all back…"
                        importScope.launch {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                runCatching { com.shelf.launcher.data.Backup.applyDocument(context, doc, null, "before import from another device") }
                            }
                                .onSuccess {
                                    com.shelf.launcher.data.FirstRun.markDone(context)
                                    com.shelf.launcher.data.Backup.restart(context)
                                }
                                .onFailure { importMessage = "Couldn't import: ${it.message}" }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                ShelfCard {
                    Text(
                        "Your ROMs, Android games and Steam games on one shelf — with artwork, details, " +
                            "achievements and controller-first menus.",
                        color = ShelfTheme.colors.textPrimary, fontSize = 15.sp, lineHeight = 21.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    MutedNote(
                        "Setup takes a couple of minutes: permissions, your ROM folder, then optional free accounts " +
                            "for artwork, details and achievements. Every step can be skipped and changed later under " +
                            "Select › Settings."
                    )
                }
            }
            "Permissions" -> PermissionsSetup()
            "Your games" -> {
                ShelfCard {
                    MutedNote(
                        "Pick the folder that holds your ROMs — one sub-folder per console works best " +
                            "(e.g. ROMs/SNES, ROMs/PS2). Sub-folders are searched too. Installed Android games " +
                            "and emulators are found by themselves. Steam games (through GameNative / Winlator) can be added here or later."
                    )
                    Spacer(Modifier.height(10.dp))
                    ChipFlow {
                        Chip("Choose ROM folder", onClick = onRomFolders)
                        Chip("Add Steam games", accent = false, onClick = onSteamGames)
                    }
                    if (status.gameCount > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text("✓ ${status.gameCount} games found", color = ShelfTheme.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            "Artwork" -> {
                MutedNote("Both are free and optional — one is enough to get artwork. Using both fills in more games.")
                Spacer(Modifier.height(8.dp))
                SetupAccount(
                    "SteamGridDB",
                    "Covers, wide banners, backgrounds and logos.",
                    status.artConnected, onArtwork,
                    how = "Sign in at steamgriddb.com (with Steam) › Preferences › API › copy the key › paste it here."
                )
                Spacer(Modifier.height(12.dp))
                SetupAccount(
                    "ScreenScraper",
                    "Best for retro ROMs: box art, discs, video previews, details.",
                    status.screenScraperConnected, onScreenScraper,
                    how = "Make a free account at screenscraper.fr › enter the same username and password here."
                )
            }
            "Extra art" -> PacksSetupStep()
            "Game details" -> {
                MutedNote("Optional: summaries, scores and play times in each game's details.")
                Spacer(Modifier.height(8.dp))
                SetupAccount(
                    "IGDB",
                    "Summaries, critic scores and how long each game takes to beat.",
                    status.detailsConnected, onIgdb,
                    how = "dev.twitch.tv/console › Register Your Application (any name, OAuth redirect http://localhost, " +
                        "category Application Integration) › copy the Client ID, make a Client Secret › paste both here."
                )
                Spacer(Modifier.height(12.dp))
                SetupAccount(
                    "RAWG",
                    "Metacritic scores and average playtime.",
                    status.rawgConnected, onRawg,
                    how = "rawg.io/apidocs › Get API key (free account) › paste the key here."
                )
            }
            "Achievements" -> SetupAccount(
                "RetroAchievements",
                "Trophies for retro games: progress on every cover, mastered games marked, your profile under Select.",
                status.raConnected, onRetroAchievements,
                how = "Free account at retroachievements.org › Settings › Keys › copy the Web API Key › enter your " +
                    "username and that key here. (Unlocking itself happens in the emulator, e.g. RetroArch — sign in there too.)"
            )
            else -> {
                // Checked again whenever you come back from Android's home-app picker.
                val resumed = rememberIsResumed()
                val isHome = remember(resumed) { com.shelf.launcher.data.HomeLauncher.isDefault(context) }
                ShelfCard {
                    SectionLabel("Home app")
                    MutedNote("Open Voltcap with the home button and when the device starts, like a console.")
                    Spacer(Modifier.height(8.dp))
                    if (isHome) {
                        Text("✓ Voltcap is your home app", color = ShelfTheme.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Chip("Use Voltcap as home app") {
                            com.shelf.launcher.data.HomeLauncher.setOffered(context, true)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                ShelfCard {
                    SectionLabel("Button legend")
                    MutedNote(
                        "A slim strip along the bottom with the main buttons. Press R3 (click the right stick) " +
                            "any time to see every control on a small card. Turn the strip on or off here or under " +
                            "Settings › Controls."
                    )
                    Spacer(Modifier.height(8.dp))
                    ChipFlow {
                        Chip("Show it", accent = showLegend) { onShowLegendChange(true) }
                        Chip("Hide it", accent = !showLegend) { onShowLegendChange(false) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                ShelfCard {
                    SectionLabel("Quick Settings tiles")
                    QsTilesCard(compact = true)
                }
                Spacer(Modifier.height(12.dp))
                ShelfCard {
                    SectionLabel("Later, if you like")
                    MutedNote(
                        "• Save sync between handhelds: Settings › Sync › Connect Google Drive, then tick your emulators' " +
                            "save folders (in-game saves only — save states are never synced).\n" +
                            "• Two screens: Settings › Screens chooses what goes on each one.\n" +
                            "• Spotify: Settings › Accounts. Spotify only allows this with your own free developer app " +
                            "(needs Premium) — the card there explains the steps. Music controls for any app work without it."
                    )
                }
                Spacer(Modifier.height(12.dp))
                ShelfCard {
                    SectionLabel("Good to know")
                    MutedNote(
                        "A opens a game's details · Select opens the menu · L1 shortcuts · R1 quick menu (sort, sync, quick settings) · " +
                            "L2 / R2 switch category · B shows every category · L3 jumps by letter · R3 shows every control · " +
                            "hold Select + A / ◀ / ▶ to control music."
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupAccount(name: String, blurb: String, connected: Boolean, onOpen: () -> Unit, how: String? = null) {
    ShelfCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, color = ShelfTheme.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
            if (connected) {
                Spacer(Modifier.width(8.dp))
                Text("✓ Connected", color = ShelfTheme.accent, fontSize = 13.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(13.sp), maxFontSize = 13.sp), fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(4.dp))
        MutedNote(blurb)
        if (how != null && !connected) {
            Spacer(Modifier.height(4.dp))
            Text("How: $how", color = ShelfTheme.colors.textPrimary.copy(alpha = 0.85f), fontSize = 12.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.height(10.dp))
        Chip(if (connected) "Change" else "Connect $name", accent = !connected, onClick = onOpen)
    }
}

@Composable
private fun StepDots(step: Int, count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(if (i == step) 10.dp else 7.dp)
                    .clip(CircleShape)
                    .background(if (i <= step) ShelfTheme.accent else Color.White.copy(alpha = 0.2f))
            )
        }
    }
}
