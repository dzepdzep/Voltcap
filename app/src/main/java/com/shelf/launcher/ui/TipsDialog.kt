package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.RaGameTips
import com.shelf.launcher.ui.theme.ShelfTheme
import java.io.File

/**
 * Details › Tips: the set's official guide and everything people wrote on the
 * game's RetroAchievements page — each comment is a stop for the D-pad, so the
 * whole thing reads with a controller.
 */
@Composable
internal fun TipsDialog(game: Game, tips: RaGameTips, onDismiss: () -> Unit, onOpenGuide: (() -> Unit)? = null) {
    val context = androidx.compose.ui.platform.LocalContext.current
    ShelfDialog(
        icon = Icons.Filled.Lightbulb,
        title = "Tips",
        subtitle = gameDisplayName(game.name) + if (tips.comments.isNotEmpty()) " · ${tips.comments.size} comments" else "",
        onDismiss = onDismiss,
        maxContentHeightFraction = 0.7f
    ) {
        ChipFlow {
            tips.guideUrl?.let { url ->
                // A text guide reads inside the app (full-screen guide); a plain link opens the browser.
                if (onOpenGuide != null) Chip("📖  Read the guide here") { onOpenGuide() }
                Chip("📖  Open the official guide here", accent = onOpenGuide == null) {
                    GuideWebActivity.open(context, url, game.stableKey, stripRegionTags(game.name))
                }
            }
        }
        if (tips.commentsError != null || tips.guideError != null) {
            Spacer(Modifier.height(6.dp))
            MutedNote("Some tips couldn't be loaded (${(tips.commentsError ?: tips.guideError)?.message}) — reopen this to retry.")
        }
        if (tips.comments.isEmpty()) {
            EmptyState(
                if (tips.commentsError != null) "Comments couldn't be loaded."
                else "Nobody has written on this game's page yet."
            )
        } else {
            Spacer(Modifier.height(8.dp))
            CommentOrderChips()
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                sortedComments(tips.comments).forEach { c ->
                    val shape = RoundedCornerShape(12.dp)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(ShelfTheme.colors.surfaceRaised)
                            .shelfClickable(shape) { }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            (c.User ?: "Someone") + (c.Submitted?.take(10)?.let { "  ·  $it" } ?: ""),
                            color = ShelfTheme.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            cleanComment(c.CommentText.orEmpty()),
                            color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

/** Your screenshots, full screen: ◀ ▶ (or tap the sides) to flip, B to close. */
@Composable
internal fun ScreenshotViewer(files: List<File>, start: Int, onDismiss: () -> Unit) {
    var index by remember(files) { mutableStateOf(start.coerceIn(0, (files.size - 1).coerceAtLeast(0))) }
    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ImmersiveDialogWindow()
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(focus)
                .focusable()
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (e.key) {
                        Key.DirectionLeft, Key.ButtonL1 -> { if (index > 0) index--; true }
                        Key.DirectionRight, Key.ButtonR1 -> { if (index < files.lastIndex) index++; true }
                        Key.ButtonB, Key.Back, Key.ButtonA, Key.Enter -> { onDismiss(); true }
                        else -> false
                    }
                }
        ) {
            files.getOrNull(index)?.let { f ->
                AsyncImage(model = f, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                // Taps: left third back, right third forward, middle closes.
                Box(
                    Modifier.fillMaxSize().pointerInput(files) {
                        detectTapGestures { p ->
                            val third = size.width / 3f
                            when {
                                p.x < third -> if (index > 0) index--
                                p.x > third * 2 -> if (index < files.lastIndex) index++
                                else -> onDismiss()
                            }
                            Unit
                        }
                    }
                )
                Text(
                    "${index + 1} / ${files.size}  ·  " +
                        java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                            .format(java.util.Date(f.lastModified())) +
                        "   ◀ ▶ flip · B close",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}
