package com.shelf.launcher.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.EsdeMedia

/** Update 422: what the ES-DE card asks the view model to do (set by LauncherScreen). */
internal object EsdeActions {
    var rescan: ((Int, Int, Int, List<String>) -> Unit) -> Unit = { }
    var disconnect: () -> Unit = { }
    var apply: () -> Unit = { }
}

/**
 * Update 422: Settings › Library › ES-DE artwork. Reuses ES-DE's downloaded covers, screenshots, fan art, marquees,
 * disc / cartridge photos and clips — read-only: VoltCap never writes, moves, renames or deletes anything in ES-DE's folder.
 */
@Composable
internal fun EsdeMediaCard() {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(EsdeMedia.enabled(ctx)) }
    var tree by remember { mutableStateOf(EsdeMedia.treeUri(ctx)) }
    var prefer by remember { mutableStateOf(EsdeMedia.prefer(ctx)) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var unmatched by remember { mutableStateOf<List<String>>(emptyList()) }
    var showUnmatched by remember { mutableStateOf(false) }
    val auto = remember { EsdeMedia.autoRoots() }
    fun scan() {
        busy = true; result = null
        EsdeActions.rescan { hit, miss, bad, names ->
            busy = false; unmatched = names
            result = "$hit games matched · $miss without ES-DE media" + (if (bad > 0) " · $bad files skipped (not a picture or clip VoltCap shows)" else "") + "."
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) { EsdeMedia.setTree(ctx, uri); tree = uri.toString(); on = true; scan() }
    }
    SettingsHeading("ES-DE artwork")
    ShelfCard {
        MutedNote(
            "Uses the covers, screenshots, fan art, marquees, disc / cartridge photos and clips ES-DE has already downloaded. " +
                "Read only: nothing in ES-DE's folder is changed. Matched by console and exact ROM file name. Your own picks in Artwork & details always come first."
        )
        Spacer(Modifier.height(8.dp))
        MutedNote(when {
            !on -> if (auto.isNotEmpty()) "Found ES-DE media at ${auto.first().absolutePath}." else "No ES-DE media found in the usual places — pick the folder."
            tree != null -> "Folder: picked by you (read access only)."
            else -> "Folder: ${auto.firstOrNull()?.absolutePath ?: "not found (storage removed?)"}"
        })
        if (on) EsdeMedia.index?.let { MutedNote("Last scan: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.scannedAt))}") }
        if (busy) MutedNote("Scanning ES-DE's media…")
        result?.let { MutedNote(it) }
        Spacer(Modifier.height(8.dp))
        ChipFlow {
            if (!on && auto.isNotEmpty()) Chip("Use it", accent = true) { EsdeMedia.useAutoFolder(ctx); on = true; scan() }
            Chip(if (tree == null) "Pick ES-DE's folder" else "Change folder", accent = !on && auto.isEmpty()) { runCatching { picker.launch(null) } }
            if (on) Chip("Rescan") { scan() }
            if (on) Chip(if (prefer) "ES-DE first: on" else "ES-DE first: off") {
                prefer = !prefer; EsdeMedia.setPrefer(ctx, prefer); EsdeActions.apply()
            }
            if (on && unmatched.isNotEmpty()) Chip(if (showUnmatched) "Hide unmatched" else "Show unmatched (${unmatched.size})") { showUnmatched = !showUnmatched }
            if (on) Chip("Disconnect") { EsdeActions.disconnect(); on = false; tree = null; result = "Disconnected. ES-DE's files were not touched."; unmatched = emptyList() }
        }
        if (on) MutedNote(if (prefer) "ES-DE first: its pictures replace the downloaded ones (your own picks still win)." else "ES-DE fills in only where VoltCap has no picture yet.")
        if (showUnmatched) MutedNote(unmatched.take(60).joinToString(", ") + if (unmatched.size > 60) " and ${unmatched.size - 60} more" else "")
    }
}
