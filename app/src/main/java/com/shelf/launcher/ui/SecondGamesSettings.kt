package com.shelf.launcher.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Label
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.FolderArt
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * Update 177: Settings › Screens › Second screen Games page — how the consoles look there (folders, tiles, wide cards,
 * logos), a folder pack (a zip, or the Voltcap/Folders folder, of pictures named after the consoles) and a picture of
 * your own for any console. The naming rule is in FolderArt.kt.
 */
@Composable
internal fun SecondGamesSettings() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val titles = LocalTitleOptions.current
    var status by remember { mutableStateOf<String?>(null) }
    var pickingFor by remember { mutableStateOf<String?>(null) }
    val v = FolderArt.version
    val pack = remember(v) { runCatching { FolderArt.packSummary(context) }.getOrDefault(0 to false) }

    val pickZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { status = withContext(Dispatchers.IO) { FolderArt.importZip(context, uri) } }
    }
    val pickPicture = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val p = pickingFor
        pickingFor = null
        if (uri != null && p != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { FolderArt.setMine(context, p, uri) }
            status = if (ok) "Picture set for ${titles.console(p)}." else "Couldn't use that picture."
        }
    }

    MutedNote("The Games page shows every console; tap one for its games. With the controller: ▼ past the bottom of the main screen moves it onto the second screen (B goes back up).")
    Spacer(Modifier.height(8.dp))
    SectionLabel("Consoles look like")
    ChipFlow {
        Features.FolderLook.values().forEach { l ->
            val on = Features.secondFolderLook.state.value == l
            Chip((if (on) "✓ " else "") + l.label, accent = on) { Features.secondFolderLook.value = l }
        }
    }
    Spacer(Modifier.height(8.dp))
    SwitchRow(
        icon = Icons.Filled.Label,
        title = "Console logo over pictures",
        subtitle = "The console's logo over its picture. Off: the picture alone (many packs have the name in the picture).",
        checked = Features.secondFolderLogo.state.value,
        onChange = { Features.secondFolderLogo.value = it }
    )
    Spacer(Modifier.height(10.dp))
    SectionLabel("Folder pack")
    MutedNote(
        "A zip (or the ${FolderArt.DROP_FOLDER} folder on the handheld) of pictures named after the consoles, the same names " +
            "as ROM folders: snes.png, psx.png, gba.webp, megadrive.jpg… iiSU-style names work too (snes_list.png, " +
            "snes_title.png). _default.png is used for every console without its own. PNG, JPG, WebP or GIF."
    )
    Spacer(Modifier.height(6.dp))
    MutedNote(
        if (pack.first == 0 && !pack.second) "No pack yet."
        else "Pack: pictures for ${pack.first} consoles" + if (pack.second) ", and a default one." else "."
    )
    Spacer(Modifier.height(6.dp))
    ChipFlow {
        Chip("Import a zip") { runCatching { pickZip.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*")) } }
        Chip("Use ${FolderArt.DROP_FOLDER}", accent = false) { scope.launch { status = withContext(Dispatchers.IO) { FolderArt.importFolder(context) } } }
        if (pack.first > 0 || pack.second) Chip("Remove the pack", accent = false) { FolderArt.removePack(context); status = "Pack removed." }
    }
    status?.let {
        Spacer(Modifier.height(6.dp))
        Text(it, color = ShelfTheme.colors.textPrimary, fontSize = 12.sp)
    }
    val platforms = FolderArt.platforms
    if (platforms.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        SectionLabel("A picture for one console")
        MutedNote("Comes before the pack. Pick a picture from the handheld for any console.")
        Spacer(Modifier.height(4.dp))
        platforms.forEach { p ->
            val mine = remember(p, v) { FolderArt.hasMine(context, p) }
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(titles.console(p), color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Chip(if (mine) "Change" else "Pick a picture", accent = false) {
                    pickingFor = p
                    runCatching { pickPicture.launch(arrayOf("image/*")) }
                }
                if (mine) {
                    Spacer(Modifier.width(6.dp))
                    Chip("Reset", accent = false) { FolderArt.clearMine(context, p); status = "${titles.console(p)}: back to the pack." }
                }
            }
        }
    }
}
