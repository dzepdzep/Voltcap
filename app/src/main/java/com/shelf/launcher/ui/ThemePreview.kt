package com.shelf.launcher.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shelf.launcher.ui.theme.AppTheme

/** A full-screen theme preview in progress (Settings › Theme › ▶ Full preview). */
internal object ScenePreview {
    var theme by mutableStateOf<AppTheme?>(null)
    /** True while a preview runs: music scenes beat as if a song played. */
    val active: Boolean get() = theme != null
}

/**
 * The theme full screen for ten seconds (or until any button / a tap): the real scene at the real size,
 * moving — particles, CRT, transitions and, for music themes, a simulated beat on the current cover (or
 * its colours) — so it can be judged properly before using it. Game showcase themes show the game you're on.
 */
@Composable
internal fun FullThemePreview(theme: AppTheme, onDone: () -> Unit) {
    var left by remember(theme) { mutableIntStateOf(10) }
    LaunchedEffect(theme) {
        while (left > 0) { kotlinx.coroutines.delay(1000); left-- }
        onDone()
    }
    val focus = remember { FocusRequester() }
    // Only a press made inside the preview closes it: the release of the A that opened it arrives here too.
    var pressed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { focus.requestFocus() } }
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ImmersiveDialogWindow()
        CompositionLocalProvider(LocalSceneCovered provides false) {
            Box(
                Modifier
                    .fillMaxSize()
                    .focusRequester(focus)
                    .focusable()
                    .onKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown) pressed = true
                        else if (e.type == KeyEventType.KeyUp && pressed) onDone()
                        true
                    }
                    .pointerInput(Unit) { detectTapGestures { onDone() } }
            ) {
                ThemeScene(theme.motif, theme.colors, Modifier.fillMaxSize(), waveY = 0.45f, waveH = 0.16f)
                Text(
                    "${theme.label} · preview · ${left}s · any button closes",
                    color = Color.White, fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                )
            }
        }
    }
}
