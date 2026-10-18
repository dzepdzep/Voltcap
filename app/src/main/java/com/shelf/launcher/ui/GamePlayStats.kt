package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.PlaytimeStore
import com.shelf.launcher.data.RaGameProgress
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One game's play stats, opened from the time-played pill in its details window. */
@Composable
internal fun GamePlayStatsDialog(
    game: Game,
    totalMs: Long?,
    lastPlayedAt: Long?,
    raProgress: RaGameProgress?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sessions by produceState(emptyList<PlaytimeStore.Session>(), game.stableKey) {
        value = withContext(Dispatchers.IO) {
            runCatching { PlaytimeStore(context.applicationContext).loadSessions().filter { it.key == game.stableKey } }
                .getOrDefault(emptyList())
        }
    }
    // Update 220 (#01): where the total comes from — this handheld, each other one, and an older total from before play
    // time was counted per device — so a total you don't recognise can be traced.
    val sources by produceState<Pair<Long, List<Pair<String, Long>>>?>(null, game.stableKey) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val own = PlaytimeStore(context.applicationContext).loadOwn()[game.stableKey] ?: 0L
                own to com.shelf.launcher.data.CloudSync.playSources(context.applicationContext, game.stableKey)
            }.getOrNull()
        }
    }
    val now = System.currentTimeMillis()
    val day = 24L * 60 * 60 * 1000
    val total = totalMs ?: sessions.sumOf { it.ms }
    val week = sessions.filter { it.start >= now - 7 * day }.sumOf { it.ms }
    val month = sessions.filter { it.start >= now - 30 * day }.sumOf { it.ms }
    val longest = sessions.maxOfOrNull { it.ms } ?: 0L
    val average = if (sessions.isNotEmpty()) sessions.sumOf { it.ms } / sessions.size else 0L
    val first = sessions.minOfOrNull { it.start }
    val dateFmt = android.text.format.DateFormat.getMediumDateFormat(context)

    ShelfMenuDialog(
        title = gameDisplayName(game.name),
        subtitle = "Play stats",
        icon = Icons.Filled.Schedule,
        onDismiss = onDismiss
    ) {
        ShelfCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox("Total", if (total > 0) PlaytimeStore.format(total) else "—", Modifier.weight(1f))
                StatBox("Last 7 days", if (week > 0) PlaytimeStore.format(week) else "—", Modifier.weight(1f))
                StatBox("Last 30 days", if (month > 0) PlaytimeStore.format(month) else "—", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox("Sessions", sessions.size.toString(), Modifier.weight(1f))
                StatBox("Average", if (average > 0) PlaytimeStore.format(average) else "—", Modifier.weight(1f))
                StatBox("Longest", if (longest > 0) PlaytimeStore.format(longest) else "—", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            first?.let { MutedNote("First played ${dateFmt.format(java.util.Date(it))}") }
            lastPlayedAt?.takeIf { it > 0 }?.let { MutedNote("Last played ${dateFmt.format(java.util.Date(it))} (${syncAgo(it)})") }
            val possible = raProgress?.NumPossibleAchievements
            if (possible != null && possible > 0) {
                val got = raProgress?.NumAchieved ?: 0
                MutedNote("Achievements $got / $possible (${got * 100 / possible}%)")
            }
            // (Plain if, never an early return inside a composable — see AppIcons.kt.)
            val src = sources
            if (src != null && total > 0L) {
                val (own, others) = src
                val parts = buildList {
                    if (own > 0L) add("this device ${PlaytimeStore.format(own)}")
                    others.forEach { (where, ms) -> add((if (where == "Older total") "older total (before per-device counting)" else where) + " " + PlaytimeStore.format(ms)) }
                    // Without the shared copy (sync off), the rest of the total just isn't from this device.
                    val rest = total - own - others.sumOf { it.second }
                    if (others.isEmpty() && rest >= 60_000L) add("from before or other devices ${PlaytimeStore.format(rest)}")
                }
                if (parts.isNotEmpty()) MutedNote("Time from: " + parts.joinToString(" · "))
            }
        }
        if (sessions.isNotEmpty()) {
            ShelfCard {
                SectionLabel("Recent sessions")
                val recent = sessions.sortedByDescending { it.start }.take(12)
                val maxMs = recent.maxOf { it.ms }.coerceAtLeast(1L)
                recent.forEach { sess ->
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(dateFmt.format(java.util.Date(sess.start)), color = ShelfTheme.textMuted, fontSize = 12.sp, modifier = Modifier.width(110.dp))
                        Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(ShelfTheme.colors.surfaceRaised)) {
                            Box(Modifier.fillMaxWidth(sess.ms.toFloat() / maxMs).fillMaxHeight().background(ShelfTheme.accent))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(PlaytimeStore.format(sess.ms), color = ShelfTheme.colors.textPrimary, fontSize = 12.sp, maxLines = 1, modifier = Modifier.width(64.dp))
                    }
                }
            }
        } else {
            MutedNote("Sessions are counted from now on when you start this game from Voltcap.")
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ShelfTheme.colors.surfaceRaised)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(value, color = ShelfTheme.colors.textPrimary, fontSize = 16.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(16.sp), maxFontSize = 16.sp), fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Text(label, color = ShelfTheme.textMuted, fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}
