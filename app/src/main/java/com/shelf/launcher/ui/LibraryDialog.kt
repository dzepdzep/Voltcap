package com.shelf.launcher.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.Game
import com.shelf.launcher.ui.theme.ShelfTheme

@Composable
fun LibraryDialog(
    romRootPaths: List<String>,
    deepSearch: Boolean,
    isScanning: Boolean,
    statusMessage: String?,
    hasStoragePermission: () -> Boolean,
    hiddenApps: List<Game>,
    /** The folders (main one first) and deep search; saves them and scans all. */
    onScan: (List<String>, Boolean) -> Unit,
    onUnhideApp: (String) -> Unit,
    onDismiss: () -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("LibraryDialog")
    val context = LocalContext.current
    var paths by remember { mutableStateOf(romRootPaths) }
    var deep by remember { mutableStateOf(deepSearch) }
    var newPath by remember { mutableStateOf("") }
    var showBrowser by remember { mutableStateOf(false) }
    var granted by remember { mutableStateOf(hasStoragePermission()) }

    // Android 11+: "All files access" is only grantable from a Settings
    // screen, with no meaningful result code — so re-check the real
    // permission state whenever the person comes back from it.
    val allFilesAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        granted = hasStoragePermission()
    }

    // Android 6-10: a normal runtime permission dialog with a real result.
    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        granted = isGranted
    }

    ShelfDialog(
        icon = Icons.Filled.Folder,
        title = "ROM folders",
        subtitle = "Where Voltcap looks for games.",
        onDismiss = onDismiss,
        footer = {
            Chip("Back", accent = false, onClick = onDismiss)
            if (granted) {
                Chip(
                    if (isScanning) "Scanning…" else if (paths.size > 1) "Scan all ${paths.size}" else "Scan",
                    enabled = !isScanning && paths.isNotEmpty(),
                    onClick = { onScan(paths, deep) }
                )
            }
        }
    ) {
        if (!granted) {
            ShelfCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Filled.Lock)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        SectionLabel("Storage access needed")
                        MutedNote("Voltcap needs to read your storage to find ROM files.")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Chip(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "Grant all-files access"
                    else "Grant storage permission",
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            allFilesAccessLauncher.launch(
                                Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        } else {
                            legacyPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                    }
                )
            }
        } else {
            ShelfCard {
                SectionLabel("Folders to scan")
                MutedNote("Every folder is searched, subfolders included. Games are matched by file extension (.nes, .sfc, .gba, .bin, .zip…). The first folder is the main one.")
                Spacer(Modifier.height(8.dp))
                paths.forEachIndexed { i, p ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (i == 0) Icons.Filled.FolderOpen else Icons.Filled.Folder, null,
                            tint = if (i == 0) ShelfTheme.accent else ShelfTheme.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            p, color = ShelfTheme.colors.textPrimary, fontSize = 13.sp,
                            modifier = Modifier.weight(1f), maxLines = 2
                        )
                        if (i > 0) {
                            Spacer(Modifier.width(6.dp))
                            Chip("Make main", accent = false) { paths = listOf(p) + paths.filter { it != p } }
                        }
                        if (paths.size > 1) {
                            Spacer(Modifier.width(6.dp))
                            Chip("Remove", accent = false) { paths = paths.filter { it != p } }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                ShelfTextField(newPath, { newPath = it }, "Add a folder (path)", onSubmit = {
                    val t = newPath.trim().trimEnd('/')
                    if (t.isNotEmpty() && t !in paths) paths = paths + t
                    newPath = ""
                })
                Spacer(Modifier.height(10.dp))
                ChipFlow {
                    Chip("Browse…", accent = false, onClick = { showBrowser = true })
                    if (newPath.isNotBlank()) {
                        Chip("Add") {
                            val t = newPath.trim().trimEnd('/')
                            if (t.isNotEmpty() && t !in paths) paths = paths + t
                            newPath = ""
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            SwitchRow(
                icon = Icons.Filled.FolderOpen,
                title = "Deep search",
                subtitle = "Recognise console folders at any depth — ROMs/Nintendo/SNES/… is SNES. " +
                    "Off: only the folders directly inside each ROM folder name the console. Hidden folders and Android's app data are always skipped.",
                checked = deep,
                onChange = { deep = it }
            )
        }

        statusMessage?.let {
            Spacer(Modifier.height(12.dp))
            MutedNote(it)
        }

        if (hiddenApps.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            ShelfCard {
                SectionLabel("Hidden apps (${hiddenApps.size})")
                MutedNote("Apps you've hidden from the library. Restore one to bring it back.")
                Spacer(Modifier.height(6.dp))
                hiddenApps.forEach { app ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            app.name,
                            color = ShelfTheme.colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Chip("Restore", accent = false, onClick = { app.packageName?.let(onUnhideApp) })
                    }
                }
            }
        }
    }

    if (showBrowser) {
        FolderBrowserDialog(
            startPath = paths.firstOrNull() ?: com.shelf.launcher.data.LibrarySettingsStore.DEFAULT_ROOT_PATH,
            onSelect = { picked ->
                val t = picked.trimEnd('/')
                if (t.isNotEmpty() && t !in paths) paths = paths + t
                showBrowser = false
            },
            onDismiss = { showBrowser = false }
        )
    }
}
