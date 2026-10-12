package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.ui.theme.ShelfTheme
import java.io.File

/**
 * Directory browser for picking the ROM folder. Uses plain java.io.File on
 * purpose: the app already holds all-files access, and the Storage Access
 * Framework would hand back content:// URIs that the scanner and launch path
 * can't use as real file paths. Folders only — you're picking a directory.
 */
@Composable
fun FolderBrowserDialog(
    startPath: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Pick ROM folder",
    /** Folders to leave out of the list. */
    showFolder: (File) -> Boolean = { true }
) {
    com.shelf.launcher.Breadcrumbs.mark("FolderBrowserDialog")
    val startDir = remember(startPath) {
        val requested = File(startPath)
        when {
            requested.isDirectory -> requested
            // Fall back to the storage root if the remembered path is gone
            // (SD card removed, folder deleted, first run, etc.)
            else -> File("/storage/emulated/0").takeIf { it.isDirectory } ?: File("/")
        }
    }
    var currentDir by remember { mutableStateOf(startDir) }

    val subDirs = remember(currentDir) {
        runCatching {
            currentDir.listFiles()
                ?.filter { it.isDirectory && !it.isHidden && showFolder(it) }
                ?.sortedBy { it.name.lowercase() }
                ?: emptyList()
        }.getOrDefault(emptyList())
    }

    ShelfDialog(
        icon = Icons.Filled.FolderOpen,
        title = title,
        subtitle = currentDir.absolutePath,
        onDismiss = onDismiss,
        maxContentHeightFraction = 0.55f,
        footer = {
            Chip("Cancel", accent = false, onClick = onDismiss)
            Chip("Use this folder", onClick = { onSelect(currentDir.absolutePath) })
        }
    ) {
        ShelfCard {
            currentDir.parentFile?.let { parent ->
                MenuRow(Icons.Filled.ArrowUpward, "..", "Up to ${parent.name.ifEmpty { "/" }}") {
                    currentDir = parent
                }
            }
            if (subDirs.isEmpty()) {
                EmptyState("No sub-folders here. You can still use this folder, or go back up.")
            } else {
                subDirs.forEach { dir ->
                    MenuRow(Icons.Filled.Folder, dir.name) { currentDir = dir }
                }
            }
        }
    }
}
