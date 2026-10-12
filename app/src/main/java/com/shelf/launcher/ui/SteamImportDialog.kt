package com.shelf.launcher.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.ImportCandidate
import com.shelf.launcher.data.SteamGameEntry
import com.shelf.launcher.data.SteamImportStore
import com.shelf.launcher.data.SteamShortcutImport
import com.shelf.launcher.ui.theme.ShelfTheme

/** Pick a folder, see the Steam games found in it, and import the ticked ones in one go. */
@Composable
fun SteamImportDialog(
    existingAppIds: Set<Int>,
    onImport: (List<SteamGameEntry>) -> Int,
    onDismiss: () -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("SteamImportDialog")
    val context = LocalContext.current
    val store = remember { SteamImportStore(context.applicationContext) }
    var folder by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf(false) }
    var candidates by remember { mutableStateOf<List<ImportCandidate>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var message by remember { mutableStateOf<String?>(null) }
    val lastFolder = remember { store.lastFolder() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            store.setLastFolder(uri.toString())
            message = null
            folder = uri.toString()
        }
    }

    // Scan whenever a folder is chosen; the scan runs off the main thread and is cancelled if we leave.
    val scanGeneration = remember { intArrayOf(0) }
    LaunchedEffect(folder) {
        val f = folder ?: return@LaunchedEffect
        val generation = ++scanGeneration[0]
        scanning = true
        scanned = false
        val result = try {
            SteamShortcutImport.scan(context.applicationContext, Uri.parse(f), existingAppIds)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // a superseded / left scan must stay cancelled, not report "no games"
        } catch (e: Exception) {
            emptyList()
        }
        if (generation != scanGeneration[0]) return@LaunchedEffect // a newer scan owns the state now
        candidates = result
        selected = result.filter { it.importable && !it.alreadyInLibrary }.map { it.appId }.toSet()
        scanning = false
        scanned = true
    }

    fun inLibrary(c: ImportCandidate) = c.alreadyInLibrary || c.appId in existingAppIds
    val pickable = candidates.filter { it.importable && !inLibrary(it) }
    val count = pickable.count { it.appId in selected }

    ShelfDialog(
        icon = Icons.Filled.Folder,
        title = "Import Steam games",
        subtitle = "Looks for Steam shortcuts and installed games in a folder.",
        onDismiss = onDismiss,
        footer = {
            Chip("Close", accent = false, onClick = onDismiss)
            Spacer(Modifier.width(8.dp))
            Chip("Import $count game${if (count == 1) "" else "s"}", enabled = count > 0 && !scanning, onClick = {
                val entries = pickable.filter { it.appId in selected }.map { SteamGameEntry(it.name, it.appId) }
                val added = onImport(entries)
                val already = candidates.count { inLibrary(it) } + (entries.size - added)
                message = "Imported $added Steam game${if (added == 1) "" else "s"} ($already already in your library)"
                selected = emptySet()
            })
        }
    ) {
        ShelfCard {
            MutedNote(
                "Choose a folder holding .url or .desktop shortcuts, a Steam library's steamapps folder " +
                    "(appmanifest files), a userdata config folder (shortcuts.vdf), or exported shortcut files. " +
                    "Subfolders are searched up to 3 levels deep."
            )
            Spacer(Modifier.height(12.dp))
            ChipFlow {
                Chip("Choose folder…", onClick = { picker.launch(null) })
                if (lastFolder != null && folder == null) {
                    Chip("Rescan last folder", accent = false, onClick = { message = null; folder = lastFolder })
                }
            }
            message?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }

        if (scanning) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                MutedNote("Scanning folder…")
            }
        } else if (scanned && candidates.isEmpty()) {
            Spacer(Modifier.height(12.dp))
            MutedNote("No Steam games found in that folder.")
        } else if (candidates.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            ShelfCard {
                SectionLabel("Found (${candidates.size})")
                ChipFlow {
                    Chip("Select all", accent = false, enabled = pickable.isNotEmpty(),
                        onClick = { selected = pickable.map { it.appId }.toSet() })
                    Chip("Select none", accent = false, enabled = selected.isNotEmpty(), onClick = { selected = emptySet() })
                }
                Spacer(Modifier.height(6.dp))
                candidates.forEach { c ->
                    val lib = inLibrary(c)
                    val enabled = c.importable && !lib
                    val checked = lib || (enabled && c.appId in selected)
                    CandidateRow(c, checked, enabled, lib) {
                        selected = if (c.appId in selected) selected - c.appId else selected + c.appId
                    }
                }
            }
        }
    }
}

@Composable
private fun CandidateRow(c: ImportCandidate, checked: Boolean, enabled: Boolean, inLibrary: Boolean, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val colors = ShelfTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .shelfClickable(shape, enabled = enabled) { onToggle() }
            .padding(horizontal = 6.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (checked) colors.textPrimary.copy(alpha = if (enabled) 1f else 0.4f) else colors.surfaceRaised)
                .border(1.dp, ShelfTheme.textMuted, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (checked) Icon(Icons.Filled.Check, null, tint = colors.surfaceRaised, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                c.name, color = if (c.importable) colors.textPrimary else ShelfTheme.textMuted,
                fontSize = 13.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(13.sp), maxFontSize = 13.sp), fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            val detail = when {
                inLibrary -> "In library"
                !c.importable -> c.reason ?: "Not importable"
                else -> "AppID ${c.appId}"
            }
            Text(
                "$detail · ${c.source}", color = ShelfTheme.textMuted, fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp),
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
    }
}
