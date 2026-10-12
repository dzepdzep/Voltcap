package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.shelf.launcher.ui.theme.ShelfTheme

/** Asked once per run of the app (Settings › Accounts › RetroAchievements › "Ask who is playing at start-up"). */
internal object WhoPlaying { var asked = false }

/**
 * "Who is playing?": one big card per saved RetroAchievements account (their RetroAchievements picture and name).
 * Picking one makes that account the one whose achievements show; Skip keeps the one that was in use.
 */
@Composable
internal fun WhoIsPlayingScreen(names: List<String?>, active: Int, onPick: (Int) -> Unit, onSkip: () -> Unit) {
    Dialog(onDismissRequest = onSkip, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        ImmersiveDialogWindow()
        Box(Modifier.fillMaxSize().background(Color(0xF2080A0E)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Who is playing?", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Your achievements follow you.", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                Spacer(Modifier.height(26.dp))
                val first = remember { FocusRequester() }
                LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { first.requestFocus() } }
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    names.forEachIndexed { slot, name ->
                        if (name == null) return@forEachIndexed
                        val shape = uiRounded(20.dp)
                        Column(
                            Modifier
                                .then(if (slot == active) Modifier.focusRequester(first) else Modifier)
                                .width(170.dp)
                                .clip(shape)
                                .background(Color.White.copy(alpha = if (slot == active) 0.14f else 0.08f))
                                .shelfClickable(shape) { onPick(slot) }
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AsyncImage(
                                "https://media.retroachievements.org/UserPic/$name.png", name, contentScale = ContentScale.Crop,
                                modifier = Modifier.size(96.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f))
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (slot == active) Text("Last played", color = ShelfTheme.accent, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
                Spacer(Modifier.height(26.dp))
                Chip("Skip", accent = false, onClick = onSkip)
            }
        }
    }
}
