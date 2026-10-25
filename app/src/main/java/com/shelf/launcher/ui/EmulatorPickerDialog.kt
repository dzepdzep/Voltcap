package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideogameAsset
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.CoreRegistry
import com.shelf.launcher.data.EmulatorConfig
import com.shelf.launcher.data.EmulatorRegistry
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.LaunchMode
import com.shelf.launcher.data.RomFormat
import com.shelf.launcher.ui.theme.ShelfTheme

/**
 * Assigns an emulator to a console. Lists apps that are actually installed,
 * with known emulators for this platform floated to the top. Picking a
 * RetroArch build shows its core list instead of closing, since RetroArch
 * can't boot anything without knowing the core. Manual entry stays available
 * at the bottom for emulators that need a specific activity or extras.
 */
@Composable
fun EmulatorPickerDialog(
    platform: String,
    installedApps: List<Game>,
    existing: EmulatorConfig?,
    onSave: (EmulatorConfig) -> Unit,
    onDismiss: () -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("EmulatorPickerDialog")
    var showManual by remember { mutableStateOf(false) }
    var choosingFolder by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var pendingRetroArchPackage by remember {
        mutableStateOf(existing?.packageName?.takeIf { CoreRegistry.isRetroArch(it) })
    }

    val suggestedPackages = remember(platform) {
        EmulatorRegistry.suggestionsFor(platform).map { it.packageName }.toSet()
    }
    val pm = androidx.compose.ui.platform.LocalContext.current.packageManager
    val (suggested, others) = remember(installedApps, suggestedPackages, query) {
        installedApps
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
            // Update 144: a game or app that only shares a catalog emulator's package name (Genshin Impact) isn't suggested.
            .partition { app ->
                val pkg = app.packageName
                pkg != null && pkg in suggestedPackages && com.shelf.launcher.data.EmulatorCatalog.isRealEmulator(pm, pkg)
            }
    }

    fun pick(app: Game) {
        if (CoreRegistry.isRetroArch(app.packageName)) {
            pendingRetroArchPackage = app.packageName
        } else {
            onSave(EmulatorConfig(platform = platform, mode = LaunchMode.EXPLICIT_INTENT, packageName = app.packageName))
        }
    }

    // Update 144: an installed app shows its own name; the known emulators' names are for ones not installed.
    val currentLabel = existing?.packageName?.let { pkg ->
        installedApps.firstOrNull { it.packageName == pkg }?.name?.takeIf { it.isNotBlank() } ?: EmulatorRegistry.displayNameFor(pkg) ?: pkg
    }

    ShelfDialog(
        icon = Icons.Filled.VideogameAsset,
        title = "Emulator for ${com.shelf.launcher.data.ConsoleNames.label(platform)}",
        subtitle = currentLabel?.let { "Currently: $it" } ?: "Not set yet.",
        onDismiss = onDismiss,
        maxContentHeightFraction = 0.65f,
        footer = {
            Chip("Ask every time", accent = false, onClick = {
                onSave(EmulatorConfig(platform = platform, mode = LaunchMode.SYSTEM_CHOOSER))
            })
            Chip("Back", onClick = onDismiss)
        }
    ) {
        // RetroArch core step comes first when a RetroArch build is chosen.
        pendingRetroArchPackage?.let { raPackage ->
            ShelfCard {
                SectionLabel("RetroArch core for ${com.shelf.launcher.data.ConsoleNames.label(platform)}")
                MutedNote("RetroArch needs to know which core runs this system, otherwise it opens its own menu instead of the game.")
                Spacer(Modifier.height(8.dp))
                val cores = CoreRegistry.coresFor(platform)
                if (cores.isEmpty()) {
                    EmptyState("No known cores for ${com.shelf.launcher.data.ConsoleNames.label(platform)} — use advanced setup below to enter a core path by hand.")
                } else {
                    cores.forEach { core ->
                        val corePath = CoreRegistry.corePath(raPackage, core.id)
                        AppRow(
                            name = core.displayName,
                            subtitle = core.id,
                            highlighted = false,
                            isCurrent = existing?.coreExtraValue == corePath
                        ) {
                            onSave(
                                EmulatorConfig(
                                    platform = platform,
                                    mode = LaunchMode.EXPLICIT_INTENT,
                                    packageName = raPackage,
                                    activityClassName = CoreRegistry.RETROARCH_ACTIVITY,
                                    romExtraKey = CoreRegistry.ROM_EXTRA_KEY,
                                    coreExtraKey = CoreRegistry.CORE_EXTRA_KEY,
                                    coreExtraValue = corePath
                                )
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        ShelfTextField(query, { query = it }, "Search installed apps", leadingIcon = Icons.Filled.Search)
        Spacer(Modifier.height(12.dp))

        if (suggested.isNotEmpty()) {
            ShelfCard {
                SectionLabel("Suggested for ${com.shelf.launcher.data.ConsoleNames.label(platform)}")
                suggested.forEach { app ->
                    AppRow(
                        name = app.name.ifBlank { EmulatorRegistry.displayNameFor(app.packageName) ?: app.packageName.orEmpty() },
                        subtitle = app.packageName.orEmpty(),
                        highlighted = true,
                        isCurrent = app.packageName == existing?.packageName
                    ) { pick(app) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        ShelfCard {
            SectionLabel("All installed apps")
            if (others.isEmpty()) {
                EmptyState("Nothing matches.")
            } else {
                others.forEach { app ->
                    AppRow(
                        name = app.name,
                        subtitle = app.packageName.orEmpty(),
                        highlighted = false,
                        isCurrent = app.packageName == existing?.packageName
                    ) { pick(app) }
                }
            }
        }

        if (existing != null && existing.mode == LaunchMode.EXPLICIT_INTENT &&
            existing.packageName != null && !CoreRegistry.isRetroArch(existing.packageName)
        ) {
            Spacer(Modifier.height(12.dp))
            ShelfCard {
                SectionLabel("Game path format")
                MutedNote(
                    "How the game is handed to the emulator. Automatic uses what this emulator expects. " +
                        "If it opens but can't find or read the game, try another: \"File path\" needs the " +
                        "emulator to have storage access, \"Storage link\" needs your ROM folder added " +
                        "inside the emulator."
                )
                Spacer(Modifier.height(8.dp))
                ChipFlow {
                    RomFormat.values().forEach { format ->
                        val current = existing.romFormat ?: RomFormat.AUTO
                        Chip(format.label, accent = current == format) {
                            onSave(existing.copy(romFormat = format.takeIf { it != RomFormat.AUTO }))
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                SectionLabel("Folder given to the emulator")
                MutedNote(
                    "For storage links: the exact folder you added inside the emulator's own settings " +
                        "(e.g. DuckStation › Game List › Add Search Directory). Android only lets the emulator " +
                        "open the game when this matches. It must contain the game."
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    existing.accessFolder ?: "Voltcap's ROM folder (default)",
                    color = ShelfTheme.colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(8.dp))
                ChipFlow {
                    Chip("Choose folder…", accent = false) { choosingFolder = true }
                    if (existing.accessFolder != null) {
                        Chip("Use Voltcap's ROM folder", accent = false) { onSave(existing.copy(accessFolder = null)) }
                    }
                }
            }
            if (choosingFolder) {
                FolderBrowserDialog(
                    startPath = existing.accessFolder ?: "/storage/emulated/0",
                    onSelect = { folder ->
                        choosingFolder = false
                        onSave(existing.copy(accessFolder = folder))
                    },
                    onDismiss = { choosingFolder = false }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        ActionCard(
            Icons.Filled.Settings,
            if (showManual) "Hide advanced setup" else "Advanced setup",
            "Activity, core path and intent extras",
            onClick = { showManual = !showManual }
        )
        if (showManual) {
            Spacer(Modifier.height(12.dp))
            ManualEmulatorFields(platform, existing, onSave)
        }
    }
}

@Composable
private fun AppRow(
    name: String,
    subtitle: String,
    highlighted: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("AppRow")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .shelfClickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isCurrent -> ShelfTheme.accent
                        highlighted -> ShelfTheme.secondary
                        else -> Color.White.copy(alpha = 0.2f)
                    }
                )
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = ShelfTheme.textMuted, fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (isCurrent) {
            Text("In use", color = ShelfTheme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ManualEmulatorFields(
    platform: String,
    existing: EmulatorConfig?,
    onSave: (EmulatorConfig) -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("ManualEmulatorFields")
    var packageName by remember { mutableStateOf(existing?.packageName ?: "") }
    var activityName by remember { mutableStateOf(existing?.activityClassName ?: "") }
    var romExtraKey by remember { mutableStateOf(existing?.romExtraKey ?: "") }
    var coreExtraKey by remember { mutableStateOf(existing?.coreExtraKey ?: "") }
    var coreExtraValue by remember { mutableStateOf(existing?.coreExtraValue ?: "") }

    ShelfCard {
        MutedNote(
            "For emulators that need more than just the package — e.g. RetroArch, which wants " +
                "a specific activity plus the libretro core path."
        )
        Spacer(Modifier.height(10.dp))
        ShelfTextField(packageName, { packageName = it }, "Package name")
        Spacer(Modifier.height(8.dp))
        ShelfTextField(activityName, { activityName = it }, "Activity class (optional)")
        Spacer(Modifier.height(8.dp))
        ShelfTextField(romExtraKey, { romExtraKey = it }, "ROM extra key (optional)")
        Spacer(Modifier.height(8.dp))
        ShelfTextField(coreExtraKey, { coreExtraKey = it }, "Core extra key (optional)")
        Spacer(Modifier.height(8.dp))
        ShelfTextField(coreExtraValue, { coreExtraValue = it }, "Core .so path (optional)")
        Spacer(Modifier.height(12.dp))
        // A launch by package needs the package; pasted names often carry a stray space.
        Chip("Save advanced config", enabled = packageName.isNotBlank(), onClick = {
            onSave(
                EmulatorConfig(
                    platform = platform,
                    mode = LaunchMode.EXPLICIT_INTENT,
                    packageName = packageName.trim().ifBlank { null },
                    activityClassName = activityName.trim().ifBlank { null },
                    romExtraKey = romExtraKey.trim().ifBlank { null },
                    coreExtraKey = coreExtraKey.trim().ifBlank { null },
                    coreExtraValue = coreExtraValue.trim().ifBlank { null }
                )
            )
        })
    }
}
