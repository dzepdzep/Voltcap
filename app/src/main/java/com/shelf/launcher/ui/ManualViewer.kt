package com.shelf.launcher.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.Description
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A game's manual, full screen, drawn by Android's own PDF renderer (nothing leaves the device).
 * ◀ ▶ or L1 / R1 turn pages, ▲ ▼ scroll a zoomed page, A switches fit-screen / fit-width, B closes.
 * Taps on the left / right half turn pages too.
 */
@Composable
fun ManualViewer(file: File, title: String, onDismiss: () -> Unit) {
    val pfd = remember(file) { runCatching { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }.getOrNull() }
    val renderer = remember(pfd) { pfd?.let { runCatching { PdfRenderer(it) }.getOrNull() } }
    // Closed under the page lock, so a page still rendering finishes first; the renderer owns pfd and closes it too (update 91).
    DisposableEffect(renderer) {
        onDispose {
            if (renderer != null) runCatching { synchronized(renderer) { renderer.close() } }
            else runCatching { pfd?.close() }
        }
    }
    var page by remember { mutableStateOf(0) }
    var fitWidth by remember { mutableStateOf(false) }
    val count = renderer?.pageCount ?: 0
    val screen = screenSizeDp()
    val scroll = rememberScrollState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val bitmap by produceState<Bitmap?>(null, renderer, page) {
        val r = renderer ?: return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                synchronized(r) {
                    r.openPage(page).use { p ->
                        // About 2x the screen width: sharp when zoomed to fit-width.
                        val w = (screen.width.value * 2.2f).toInt().coerceIn(600, 2400)
                        val h = (w.toFloat() * p.height / p.width.coerceAtLeast(1)).toInt().coerceIn(1, 8000)
                        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }.getOrNull()
        }
    }
    LaunchedEffect(page) { scroll.scrollTo(0) }
    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ImmersiveDialogWindow()
        // Inside the dialog: its window has to exist before the box can take focus (and the keys).
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.DirectionRight, Key.ButtonR1 -> { if (page < count - 1) page++; true }
                        Key.DirectionLeft, Key.ButtonL1 -> { if (page > 0) page--; true }
                        Key.DirectionDown -> { scope.launch { scroll.animateScrollTo(scroll.value + 300) }; true }
                        Key.DirectionUp -> { scope.launch { scroll.animateScrollTo(scroll.value - 300) }; true }
                        Key.ButtonA, Key.Enter, Key.DirectionCenter -> { fitWidth = !fitWidth; true }
                        Key.ButtonB, Key.Back, Key.Escape -> { onDismiss(); true }
                        else -> false
                    }
                }
                .pointerInput(count) {
                    detectTapGestures { o ->
                        if (o.x > size.width / 2) { if (page < count - 1) page++ } else if (page > 0) page--
                    }
                }
        ) {
            val b = bitmap
            if (renderer == null) {
                Text("This manual can't be opened.", color = Color.White, modifier = Modifier.align(Alignment.Center))
            } else if (b != null) {
                if (fitWidth) {
                    Box(Modifier.fillMaxSize().verticalScroll(scroll)) {
                        Image(b.asImageBitmap(), title, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxSize())
                    }
                } else {
                    Image(b.asImageBitmap(), title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
            }
            Text(
                "$title · page ${page + 1} / $count   ◀ ▶ pages · A zoom · B close",
                color = Color.White, fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomCenter).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

/**
 * No manual for this game yet: what to do about it, instead of a message that vanishes. Pick a PDF
 * you have (kept as this game's manual from then on), search for one online in the in-app reader
 * (archive.org keeps many scanned manuals), and where Voltcap looks by itself.
 */
@Composable
internal fun ManualMissingDialog(
    game: com.shelf.launcher.data.Game,
    screenScraper: Boolean,
    onPicked: (android.net.Uri) -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onPicked(uri) }
    val title = gameDisplayName(game.name)
    val console = LocalTitleOptions.current.console(game.platform)
    ShelfDialog(
        icon = androidx.compose.material.icons.Icons.Filled.Description,
        title = "No manual yet",
        subtitle = title,
        onDismiss = onDismiss
    ) {
        MutedNote(
            "Voltcap looks for <rom name>.pdf next to the ROM and in a \"manuals\" folder beside it" +
                (if (screenScraper) ", then for ScreenScraper's scan - it has none for this game." else ". Connect ScreenScraper (Settings › Accounts) to download scanned manuals automatically.")
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 8.dp))
        ChipFlow {
            Chip("Pick a PDF…") { picker.launch(arrayOf("application/pdf")) }
            Chip("Search online", accent = false) {
                val q = android.net.Uri.encode("$title $console manual")
                GuideWebActivity.open(context, "https://archive.org/search?query=$q", game.stableKey, title)
            }
            Chip("Close", accent = false, onClick = onDismiss)
        }
        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 6.dp))
        MutedNote("A PDF you pick (or download from the search and then pick) becomes this game's manual: ◀ ▶ turn pages, A zooms.")
    }
}
