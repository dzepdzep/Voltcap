package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.shelf.launcher.data.Romm
import com.shelf.launcher.data.RommCredentialsStore
import com.shelf.launcher.data.RommPlatform
import com.shelf.launcher.data.RommRom
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Opens the RomM browser from anywhere (Settings › Accounts › RomM, the R1 menu). The home screen shows it. */
object RommUi {
    var showing by mutableStateOf(false)
}

/**
 * RomM (update 93): your RomM server's library — consoles across the top, their games as covers, a search. A tap on a
 * game downloads it into its console's ROM folder ([Romm.download]); games already on the handheld say so. Downloads
 * keep going after this closes; when they finish the library is scanned again ([onAdded]).
 */
@Composable
fun RommBrowser(onDismiss: () -> Unit, onAdded: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val creds = remember { RommCredentialsStore(context).load() }
    var platforms by remember { mutableStateOf<List<RommPlatform>?>(null) }
    var picked by remember { mutableStateOf<RommPlatform?>(null) }
    var search by remember { mutableStateOf("") }
    val games = remember { mutableStateListOf<RommRom>() }
    var total by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var askFor by remember { mutableStateOf<RommRom?>(null) }
    var rev by remember { mutableStateOf(0) }
    val progress by Romm.progress.collectAsState()
    val message by Romm.message.collectAsState()

    LaunchedEffect(creds) {
        if (creds == null) return@LaunchedEffect
        runCatching { Romm.platforms(creds) }
            .onSuccess { platforms = it; if (picked == null) picked = it.firstOrNull() }
            .onFailure { error = it.message ?: "Couldn't reach RomM." }
    }
    // Update 144: which load is the current one. A new console or search always starts a fresh load (it used to be
    // dropped while an older page was still loading, so the old console's games stayed); older results are thrown away.
    var gen by remember { mutableStateOf(0) }
    // A page of the console's games (again when the search changes, after a short pause).
    suspend fun loadMore(reset: Boolean) {
        val c = creds ?: return
        val p = picked ?: return
        if (!reset && loading) return
        val my = if (reset) ++gen else gen
        loading = true
        try {
            val page = Romm.roms(c, p.id, search, if (reset) 0 else games.size)
            if (my == gen) {
                if (reset) games.clear()
                games.addAll(page.items)
                total = page.total
                error = null
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (my == gen) error = e.message ?: "Couldn't load the games."
        } finally { if (my == gen) loading = false }
    }
    LaunchedEffect(picked, search) {
        delay(if (search.isBlank()) 0 else 450)
        loadMore(reset = true)
    }
    // Downloads: progress, and moving finished ones into the ROM folder.
    LaunchedEffect(Unit) {
        while (true) {
            val added = withContext(Dispatchers.IO) { runCatching { Romm.poll(context) }.getOrDefault(0) }
            if (added > 0) { onAdded(); rev++ }
            delay(1500)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ImmersiveDialogWindow()
        Box(Modifier.fillMaxSize().background(ShelfTheme.background)) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("RomM", color = ShelfTheme.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    picked?.let { Text("${it.name} · $total games", color = ShelfTheme.textMuted, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f)) }
                        ?: Spacer(Modifier.weight(1f))
                    Chip("Close", accent = false) { onDismiss() }
                }
                Spacer(Modifier.height(8.dp))
                // Update 144: if / else instead of an early return out of the Column (that crashes this Compose compiler).
                if (creds == null) {
                    MutedNote("RomM isn't set up yet: Settings › Accounts › RomM (your server's address and a Client API token).")
                } else {
                    (message ?: error)?.let {
                        Text(it, color = ShelfTheme.colors.textPrimary, fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0x33E0A030)).padding(10.dp))
                        Spacer(Modifier.height(6.dp))
                    }
                    platforms?.let { list ->
                        Box(Modifier.fillMaxWidth()) {
                            ChipFlow { list.forEach { p -> Chip("${p.name} (${p.romCount})", accent = p == picked) {
                                // Update 144: empty the list at once, so paging can't fetch the new console's games at the old list's offset.
                                // Marked as loading too, so "No games here." doesn't flash before the new console's first page
                                // (the fresh load that picked starts always runs and clears it).
                                if (p != picked) { picked = p; games.clear(); total = 0; loading = true }
                            } } }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    ShelfTextField(search, { search = it }, "Search this console", modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    val state = rememberLazyGridState()
                    LaunchedEffect(state, picked) {
                        snapshotFlow { (state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) to state.layoutInfo.totalItemsCount }
                            .collect { (last, count) -> if (count > 0 && last >= count - 8 && games.size < total) loadMore(reset = false) }
                    }
                    LazyVerticalGrid(
                        GridCells.Adaptive(118.dp), state = state, modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(games, key = { it.id }) { rom ->
                            val p = picked ?: return@items
                            val onDevice = remember(rom.id, rev) { Romm.isOnDevice(context, p, rom) }
                            val pct = progress[rom.id]
                            RommGameCard(rom, Romm.coverUrl(creds, rom.coverPath), creds.authHeader, onDevice, pct) {
                                when {
                                    pct != null -> askFor = rom
                                    onDevice -> error = "${rom.name} is already on the handheld."
                                    else -> askFor = rom
                                }
                            }
                        }
                    }
                    if (games.isEmpty() && !loading && error == null && platforms != null) MutedNote("No games here.")
                }
            }
            askFor?.let { rom ->
                val p = picked
                val downloading = progress[rom.id] != null
                RommConfirm(
                    title = rom.name,
                    message = if (downloading) "It's downloading. Stop it?"
                        else "Download ${formatBytes(rom.sizeBytes)} into ${p?.let { Romm.targetDir(context, it).absolutePath } ?: "your ROM folder"}?",
                    confirm = if (downloading) "Stop" else "Download",
                    onConfirm = {
                        askFor = null
                        if (p == null || creds == null) return@RommConfirm
                        if (downloading) Romm.cancel(context, rom.id)
                        else Romm.download(context, creds, p, rom)?.let { error = it }
                    },
                    onDismiss = { askFor = null }
                )
            }
        }
    }
}

@Composable
private fun RommGameCard(rom: RommRom, cover: String?, auth: String, onDevice: Boolean, progress: Float?, onClick: () -> Unit) {
    val context = LocalContext.current
    val request = remember(cover) {
        cover?.let { coil.request.ImageRequest.Builder(context).data(it).addHeader("Authorization", auth).crossfade(true).build() }
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).shelfClickable(RoundedCornerShape(12.dp)) { onClick() }.padding(6.dp)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.08f))) {
            if (request != null) AsyncImage(request, rom.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (onDevice) Text(
                "✓ On device", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopStart).padding(4.dp).clip(RoundedCornerShape(6.dp))
                    .background(Color(0xCC2E9E5B)).padding(horizontal = 5.dp, vertical = 2.dp)
            )
            if (progress != null) LinearProgressIndicator(
                progress = { progress }, color = ShelfTheme.accent,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(5.dp)
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(rom.name, color = ShelfTheme.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            if (progress != null) "Downloading ${(progress * 100).toInt()} %" else formatBytes(rom.sizeBytes),
            color = ShelfTheme.textMuted, fontSize = 10.sp, maxLines = 1
        )
    }
}

/** A small question card over the browser (scrim behind; the first button takes the focus). */
@Composable
private fun RommConfirm(title: String, message: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    androidx.activity.compose.BackHandler { onDismiss() }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.width(420.dp).clip(RoundedCornerShape(16.dp)).background(ShelfTheme.colors.surfaceRaised).padding(16.dp)
        ) {
            Text(title, color = ShelfTheme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Text(message, color = ShelfTheme.textMuted, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.focusRequester(focus)) { Chip(confirm) { onConfirm() } }
                Chip("Cancel", accent = false) { onDismiss() }
            }
        }
    }
}

private fun formatBytes(b: Long): String = when {
    b <= 0 -> ""
    b >= 1L shl 30 -> "%.1f GB".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.0f MB".format(b / (1L shl 20).toDouble())
    else -> "%.0f KB".format(b / 1024.0)
}
