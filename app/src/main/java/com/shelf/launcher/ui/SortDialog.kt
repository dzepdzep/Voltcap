package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.SortMode

/**
 * How the games in the category you're in are ordered. Opened from the sort
 * chip on the category bar, Start › Sort…, or the Sort hotkey. D-pad: up/down
 * through the list, A picks, B closes.
 *
 * @param override this category's own pick, or null when it follows the default.
 * @param globalMode the default (Settings › Library › Sort games by).
 * @param keepsOwnOrder the category (Recent, Most played…) has a built-in order it keeps until you pick one.
 */
@Composable
fun SortDialog(
    categoryName: String,
    override: SortMode?,
    globalMode: SortMode,
    keepsOwnOrder: Boolean,
    onPick: (SortMode) -> Unit,
    onUseDefault: () -> Unit,
    onApplyToAll: (SortMode) -> Unit,
    onDismiss: () -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("SortDialog")
    // What the list is sorted by right now (null: the category's built-in order).
    val active: SortMode? = override ?: if (keepsOwnOrder) null else globalMode
    ShelfMenuDialog(
        title = "Sort games",
        subtitle = "This category: $categoryName",
        onDismiss = onDismiss,
        icon = Icons.Filled.Sort
    ) {
        SectionLabel("This category: $categoryName")
        RadioLine(
            label = "Use default",
            selected = override == null,
            blurb = if (keepsOwnOrder) "Keeps its own order" else globalMode.label
        ) { onUseDefault(); onDismiss() }
        SortMode.values().forEach { mode ->
            RadioLine(label = mode.label, selected = override == mode) { onPick(mode); onDismiss() }
        }
        // Direction only where two modes are each other's reverse.
        if (active == SortMode.NAME || active == SortMode.NAME_DESC) {
            Spacer(Modifier.height(4.dp))
            MenuRow(
                Icons.Filled.SwapVert,
                if (active == SortMode.NAME) "Switch to Z–A" else "Switch to A–Z"
            ) {
                onPick(if (active == SortMode.NAME) SortMode.NAME_DESC else SortMode.NAME)
                onDismiss()
            }
        }
        if (active != null) {
            Spacer(Modifier.height(4.dp))
            MenuRow(
                Icons.Filled.DoneAll,
                "Apply to all categories",
                "Everything sorted by ${active.label}"
            ) { onApplyToAll(active); onDismiss() }
        }
        // 0.6.15: Metacritic scores from a file (MetacriticFile) for games whose details have none.
        if (active == SortMode.METACRITIC || active == SortMode.CRITIC) {
            Spacer(Modifier.height(4.dp))
            MetacriticFileRow()
        }
        Spacer(Modifier.height(4.dp))
        MutedNote("The default is set under Settings › Library › Sort games by.")
    }
}

/** 0.6.15: load / replace / remove the Metacritic scores file (a CSV with name, platform, metascore; or a .zip with one). */
@Composable
internal fun MetacriticFileRow() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val n = com.shelf.launcher.data.MetacriticFile.count.intValue
    var status by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        status = "Reading the file…"
        scope.launch {
            status = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { com.shelf.launcher.data.MetacriticFile.import(context, uri) }
                    .fold({ "Loaded: $it games with a score. The list is sorted with them now." }, { "Couldn't read it: ${it.message}" })
            }
        }
    }
    // 0.6.15: RAWG fills in the missing scores on its own (LauncherViewModel.fillMetacriticScores).
    val rawg = com.shelf.launcher.data.MetacriticFile.rawgConnected.value
    val prog = com.shelf.launcher.data.MetacriticFile.rawgProgress.value
    MutedNote(
        when {
            prog != null -> "Looking up missing scores on RAWG: ${prog.first} / ${prog.second}…"
            rawg -> "Games without a score get it from RAWG on their own (your RAWG key, Settings › Accounts)."
            else -> "Connect a free RAWG key in Settings › Accounts and Voltcap fills in the missing scores on its own."
        }
    )
    MenuRow(
        Icons.Filled.UploadFile,
        if (n > 0) "Load another Metacritic file" else "Load a Metacritic scores file",
        if (n > 0) "$n scores from your file fill in games whose details have none" else
            "A .csv (or .zip) with name, platform and metascore — games whose details have no score get it from there"
    ) { runCatching { pick.launch(arrayOf("text/*", "application/zip", "application/octet-stream", "*/*")) } }
    if (n > 0) MenuRow(Icons.Filled.Delete, "Remove the scores file", "Only scores from game details count again") {
        com.shelf.launcher.data.MetacriticFile.clear(context); status = null
    }
    status?.let { MutedNote(it) }
}
