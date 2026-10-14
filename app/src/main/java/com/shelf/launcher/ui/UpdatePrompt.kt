package com.shelf.launcher.ui

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.AppUpdates
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Update 202: "Voltcap x is out" — the automatic update check and the question it asks on the home screen.
 *
 * Before, a new version only showed in Settings › Updates & downloads (and as a short toast), so it was easy to miss.
 * Now Voltcap looks on its own (a little after start, then — update 204 — every hour while it runs and on coming back to
 * it; only with a connection; Settings › Updates & downloads › "Check for updates automatically") and, when there is a
 * newer version that wasn't skipped, asks — by choice: Update now (download with progress here, then Android's
 * installer), Later (update 204: asks about that version again at the next start, or 12 hours on) or Skip this version.
 */

/**
 * Update 202: runs the automatic check and shows the question. [canShow]: the home screen is showing with nothing over
 * it (no popup, no first-time setup, no game starting) — LauncherScreen decides that. Once the question is up it stays
 * until it's answered (it takes the controller, so nothing else can open under it).
 */
@Composable
internal fun UpdatePromptHost(canShow: Boolean) {
    val context = LocalContext.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    // The start's check a little after start (never in the way of the intro or the first frames), then a look every
    // 15 minutes while Voltcap is on screen — each a no-op until an hour has passed since the last check (update 204).
    LaunchedEffect(Unit) {
        delay(8_000)
        AppUpdates.autoCheckIfDue(context, atStart = true)
        while (true) {
            delay(15 * 60_000L)
            if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) AppUpdates.autoCheckIfDue(context)
        }
    }
    // Coming back to Voltcap (from a game, an app): check then, at most every 10 minutes (update 204; it waited for the
    // 12 hours before). Ignored before the start's check.
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) AppUpdates.autoCheckIfDue(context, resumed = true)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val release = AppUpdates.prompt.value ?: return
    // Shown only after the home screen has been free for a moment (not the instant a popup closes or a game returns).
    var shown by remember(release.tag) { mutableStateOf(false) }
    LaunchedEffect(canShow, release.tag) {
        if (canShow && !shown) { delay(1_500); shown = true }
    }
    if (shown) UpdatePromptDialog(release)
}

/** Update 202: the question itself, in Voltcap's own dialog look. */
@Composable
private fun UpdatePromptDialog(release: AppUpdates.Release) {
    val context = LocalContext.current
    val st = AppUpdates.state.value
    val version = release.tag.removePrefix("v").removePrefix("V")
    val installedName = remember { AppUpdates.installed(context).first }
    // Whether "Update now" was picked here (a failure is only shown for that, not for an unrelated one).
    var started by remember(release.tag) { mutableStateOf(false) }
    // Android 8+ asks once to allow installs from Voltcap; read again on coming back from its screen.
    var allowed by remember { mutableStateOf(AppUpdates.canInstall(context)) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) allowed = AppUpdates.canInstall(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val mine = { r: AppUpdates.Release -> r.tag == release.tag }
    val phase = when {
        st is AppUpdates.State.Downloading && mine(st.release) -> "downloading"
        st is AppUpdates.State.Ready && mine(st.release) -> "ready"
        st is AppUpdates.State.Failed && started -> "failed"
        else -> "ask"
    }
    val later = { AppUpdates.later() }
    // B on the controller = Later (acted on when it's let go, as Back is, so the release doesn't reach the home screen).
    val bIsLater = Modifier.onPreviewKeyEvent { e ->
        if (e.key == Key.ButtonB) { if (e.type == KeyEventType.KeyUp) later(); true } else false
    }
    // The main button of each step gets the controller's focus.
    val first = remember { FocusRequester() }
    LaunchedEffect(phase) {
        repeat(4) {
            delay(80)
            if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

    ShelfDialog(
        icon = Icons.Filled.SystemUpdate,
        title = "Voltcap $version is out",
        subtitle = "You have $installedName" + (if (release.published.isNotBlank()) " · released ${release.published}" else ""),
        onDismiss = later,
        widthFraction = 0.7f,
        footer = {
            when (phase) {
                "downloading" -> Chip("Hide", accent = false, modifier = bIsLater.focusRequester(first)) { later() }
                "ready" -> {
                    Chip("Later", accent = false, modifier = bIsLater) { later() }
                    Chip("Install", modifier = bIsLater.focusRequester(first)) {
                        (st as? AppUpdates.State.Ready)?.let { AppUpdates.install(context, it.file) }
                    }
                }
                else -> {
                    Chip("Skip this version", accent = false, modifier = bIsLater) { AppUpdates.skip(context, release.tag) }
                    Chip("Later", accent = false, modifier = bIsLater) { later() }
                    Chip(if (phase == "failed") "Try again" else "Update now", modifier = bIsLater.focusRequester(first)) {
                        started = true
                        AppUpdates.download(context, release)
                    }
                }
            }
        }
    ) {
        Column(Modifier.fillMaxWidth().then(bIsLater)) {
            // What's new: the release notes, a few lines high, scrolling on their own (▲ ▼ while they have focus).
            val notes = remember(release.notes) {
                release.notes.trim().lines().joinToString("\n") {
                    it.trimStart('#', ' ').replace("**", "").replace(Regex("^[-*] "), "•  ")
                }
            }
            if (notes.isNotBlank()) {
                SectionLabel("What's new")
                NotesBox(notes)
                Spacer(Modifier.height(10.dp))
            }
            val size = if (release.apkBytes > 0) String.format("%.1f MB", release.apkBytes / 1_000_000.0) else "size unknown"
            Text("Download: $size", color = ShelfTheme.textMuted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            when (phase) {
                "downloading" -> {
                    val d = st as AppUpdates.State.Downloading
                    val pct = if (d.total > 0) (d.done * 100 / d.total).toInt().coerceIn(0, 100) else -1
                    Text(
                        "Downloading… " + (if (pct >= 0) "$pct %" else "${d.done / 1_000_000} MB"),
                        color = ShelfTheme.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    PacksBar(if (d.total > 0) d.done.toFloat() / d.total else 0f, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    MutedNote("Android's installer opens when it's done. Hide this if you like: it carries on.")
                }
                "ready" -> Text(
                    "Downloaded. Android's installer asks to update Voltcap (Install opens it again if it was closed).",
                    color = ShelfTheme.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
                "failed" -> MutedNote((st as AppUpdates.State.Failed).why)
                else -> Unit
            }
            if (!allowed && phase != "downloading") {
                Spacer(Modifier.height(8.dp))
                MutedNote("The first time, Android asks you to allow installs from Voltcap.")
                Spacer(Modifier.height(6.dp))
                ChipFlow { Chip("Allow installs from Voltcap", accent = false, modifier = bIsLater) { AppUpdates.allowInstalls(context) } }
            }
        }
    }
}

/** The release notes: a few lines high, reachable with the D-pad, ▲ ▼ scroll them (and move on at either end). */
@Composable
private fun NotesBox(notes: String) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 130.dp)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionDown -> if (scroll.value < scroll.maxValue) { scope.launch { scroll.animateScrollBy(120f) }; true } else false
                    Key.DirectionUp -> if (scroll.value > 0) { scope.launch { scroll.animateScrollBy(-120f) }; true } else false
                    else -> false
                }
            }
            .shelfFocusable()
            .verticalScroll(scroll)
    ) {
        Text(notes, color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, lineHeight = 19.sp)
    }
}
