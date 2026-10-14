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
 * Update 202: the reader is ManualReader (ManualReader.kt) — pages rendered at the screen's size in the background with
 * the ones around kept ready, a slide as pages turn, swipe / pinch, ◀ ▶ L1 R1 pages, A zoom, B close.
 */
@Composable
fun ManualViewer(file: File, title: String, onDismiss: () -> Unit) {
    ManualReader(file, title, onDismiss)
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
