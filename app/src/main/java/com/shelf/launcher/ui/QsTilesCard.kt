package com.shelf.launcher.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.device.QsTiles
import com.shelf.launcher.device.RootShell
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Voltcap's Quick Settings tiles (Settings › Device, and the last setup step): each tile, whether it's in
 * Android's pull-down menu, an Add button (Android 13+ asks with its own prompt; with the root service
 * "Add all" puts them straight in), and the steps to add them by hand.
 */
@Composable
internal fun QsTilesCard(compact: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var states by remember { mutableStateOf<Map<String, Boolean?>>(emptyMap()) }
    var note by remember { mutableStateOf<String?>(null) }
    // Re-read when coming back (the tiles may have been added by hand in the shade meanwhile).
    val resumed = rememberIsResumed()
    LaunchedEffect(refresh, resumed) {
        states = withContext(Dispatchers.IO) { runCatching { QsTiles.states(context) }.getOrDefault(emptyMap()) }
    }
    MutedNote(
        "Android's pull-down menu can hold Voltcap's tiles, so the mixer, the overlays and the power settings are one swipe away — " +
            "in a game too. Hold a Voltcap tile there for the in-game panel."
    )
    Spacer(Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        QsTiles.all.forEach { t ->
            val added = states[t.id]
            val shape = uiRounded(12.dp)
            Row(
                Modifier.fillMaxWidth().voltSurface(shape).padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(32.dp).clip(uiRounded(9.dp)).background(ShelfTheme.accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(
                        when (t.id) {
                            "mixer" -> Icons.Filled.VolumeUp
                            "overlay" -> Icons.Filled.Layers
                            "power" -> Icons.Filled.Bolt
                            else -> Icons.Filled.Speed
                        },
                        null, tint = ShelfTheme.accent, modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.label, color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(13.sp), maxFontSize = 13.sp), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!compact) Text(t.what, color = ShelfTheme.textMuted, fontSize = 11.sp, lineHeight = 14.sp)
                }
                Spacer(Modifier.width(8.dp))
                if (added == true) {
                    Text("✓ In the menu", color = ShelfTheme.accent, fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp), fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                } else if (QsTiles.canAsk) {
                    Chip("Add", accent = true) {
                        val activity = context.findActivity()
                        if (activity == null) { note = "Open this from Voltcap's own settings to add it."; return@Chip }
                        QsTiles.request(activity, t) { r ->
                            note = when (r) {
                                true -> null
                                false -> "Android says the ${t.label} tile wasn't added. If it doesn't ask any more, add it by hand (below)."
                                null -> "Android couldn't show its prompt here: add it by hand (below)."
                            }
                            refresh++
                        }
                    }
                }
            }
        }
    }
    note?.let { Spacer(Modifier.height(6.dp)); MutedNote(it) }
    if (RootShell.available && states.values.any { it != true }) {
        Spacer(Modifier.height(8.dp))
        ChipFlow {
            Chip("Add all to the menu") {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { runCatching { QsTiles.addAllWithRoot(context) }.getOrDefault(false) }
                    note = if (ok) "Done: swipe down from the top twice, the Voltcap tiles are at the end." else "The menu didn't take them: add them by hand (below)."
                    refresh++
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    FoldGroup("Add them by hand", summary = "Four steps in Android's menu", key = "qs_tiles_how", startOpen = !QsTiles.canAsk && !RootShell.available) {
        val steps = listOf(
            "Swipe down from the top of the screen twice, so the whole Quick Settings menu shows.",
            "Tap the pencil (Edit). On AYN and Retroid it's at the bottom of the menu; on some phones it's under the ⋮ menu.",
            "Scroll down to the tiles you can add. Voltcap's have a lightning icon: Volume mixer, Voltcap overlay, Voltcap power / TDP, Voltcap underclock.",
            "Hold one and drag it up among your tiles, then tap Done (or Back). The first few spots also show in the small menu."
        )
        steps.forEachIndexed { i, s ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(20.dp).clip(uiRounded(50)).background(ShelfTheme.accent), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", color = onAccentColor(), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Text(s, color = ShelfTheme.colors.textPrimary, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}
