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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.PlaytimeStore
import com.shelf.launcher.data.RaAward
import com.shelf.launcher.data.RaCompletionEntry
import com.shelf.launcher.ui.theme.ShelfTheme
import java.util.Calendar

/**
 * Settings › Stats: how much you play — this week, this month, all time, your
 * daily streak, the last 8 weeks as bars, and the consoles and games you
 * spend the most time on. Week/day history starts with the session log
 * (added with this page); totals include everything played before.
 */
@Composable
internal fun StatsContent(
    games: List<Game>,
    playtime: Map<String, Long>,
    raCompletion: Collection<RaCompletionEntry>,
    weeklyRecap: Boolean = false,
    onWeeklyRecap: (Boolean) -> Unit = {},
    /** Play statuses (empty when that feature is off). */
    statuses: Map<String, com.shelf.launcher.data.StatusEntry> = emptyMap()
) {
    val context = LocalContext.current
    // (update 91) The session log is read off the main thread (it was parsed during composition).
    val sessions by androidx.compose.runtime.produceState(emptyList<PlaytimeStore.Session>()) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { PlaytimeStore(context).loadSessions() }.getOrDefault(emptyList())
        }
    }
    val now = System.currentTimeMillis()
    val stats = remember(sessions, playtime, games) { computeStats(sessions, playtime, games, now) }
    val mastered = raCompletion.count {
        val award = RaAward.from(it.HighestAwardKind)
        award == RaAward.MASTERED || award == RaAward.COMPLETED
    }

    // Headline numbers.
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Tile("This week", tileTime(stats.week), Modifier.weight(1f))
        Tile("This month", tileTime(stats.month), Modifier.weight(1f))
        Tile("All time", tileTime(stats.total), Modifier.weight(1f))
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Tile("Daily streak", if (stats.streak == 1) "1 day" else "${stats.streak} days", Modifier.weight(1f))
        Tile("Games played", stats.gamesPlayed.toString(), Modifier.weight(1f))
        Tile("Mastered", mastered.toString(), Modifier.weight(1f))
        if (statuses.isNotEmpty()) {
            val monthStart = Calendar.getInstance().apply { timeInMillis = startOfDay(now); set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
            val finished = statuses.values.count { it.status == com.shelf.launcher.data.PlayStatus.FINISHED && it.at >= monthStart }
            Tile("Finished this month", finished.toString(), Modifier.weight(1f))
        }
    }

    // Last 8 weeks.
    Spacer(Modifier.height(12.dp))
    ShelfCard {
        SectionLabel("Last 8 weeks")
        if (sessions.isEmpty()) {
            MutedNote("Play something — each session from now on shows up here by week.")
        } else {
            WeekBars(stats.weeks)
        }
    }

    // Consoles and games.
    Spacer(Modifier.height(12.dp))
    ShelfCard {
        SectionLabel("Most played consoles")
        if (stats.consoles.isEmpty()) MutedNote("Nothing played yet.")
        stats.consoles.forEach { (name, ms) -> BarLine(name, ms, stats.consoles.first().second) }
    }
    Spacer(Modifier.height(12.dp))
    ShelfCard {
        SectionLabel("Most played games")
        if (stats.topGames.isEmpty()) MutedNote("Nothing played yet.")
        stats.topGames.forEach { (name, ms) -> BarLine(name, ms, stats.topGames.first().second) }
    }
    Spacer(Modifier.height(12.dp))
    SwitchRow(
        icon = androidx.compose.material.icons.Icons.Filled.Notifications,
        title = "Weekly recap",
        subtitle = "Sunday evening: a notification with the week's play time, days played, your most-played game and what you finished.",
        checked = weeklyRecap,
        onChange = onWeeklyRecap
    )
    // Update 117: the recaps with your other devices' play.
    Spacer(Modifier.height(8.dp))
    SwitchRow(
        icon = androidx.compose.material.icons.Icons.Filled.Notifications,
        title = "Recaps include your other devices",
        subtitle = "Games played on your other handhelds (synced with Google Drive) count in the week, month and year recaps, also ones that aren't on this device.",
        checked = com.shelf.launcher.data.Features.recapOtherDevices.state.value,
        onChange = { com.shelf.launcher.data.Features.recapOtherDevices.value = it }
    )
    if (com.shelf.launcher.data.Features.recapOtherDevices.state.value) {
        Spacer(Modifier.height(6.dp))
        SectionLabel("A game that isn't on this device shows as")
        ChipFlow {
            com.shelf.launcher.data.Features.RecapForeignArt.values().forEach { v ->
                Chip(v.label, accent = com.shelf.launcher.data.Features.recapForeignArt.state.value == v) { com.shelf.launcher.data.Features.recapForeignArt.value = v }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    MutedNote("Weeks and streaks count sessions from when this page was added; totals include everything played before.")
}

private data class Stats(
    val week: Long,
    val month: Long,
    val total: Long,
    val streak: Int,
    val gamesPlayed: Int,
    /** Oldest → newest, 8 entries: (label, ms). */
    val weeks: List<Pair<String, Long>>,
    val consoles: List<Pair<String, Long>>,
    val topGames: List<Pair<String, Long>>
)

private fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ms
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun startOfWeek(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = startOfDay(ms)
    firstDayOfWeek = Calendar.MONDAY
    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    if (timeInMillis > ms) add(Calendar.WEEK_OF_YEAR, -1)
}.timeInMillis

private fun computeStats(
    sessions: List<PlaytimeStore.Session>,
    playtime: Map<String, Long>,
    games: List<Game>,
    now: Long
): Stats {
    val weekStart = startOfWeek(now)
    val monthStart = Calendar.getInstance().apply {
        timeInMillis = startOfDay(now); set(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis

    val weeks = (7 downTo 0).map { back ->
        val from = Calendar.getInstance().apply { timeInMillis = weekStart; add(Calendar.WEEK_OF_YEAR, -back) }.timeInMillis
        val to = Calendar.getInstance().apply { timeInMillis = from; add(Calendar.WEEK_OF_YEAR, 1) }.timeInMillis
        val label = if (back == 0) "This" else Calendar.getInstance().apply { timeInMillis = from }.let {
            "${it.get(Calendar.DAY_OF_MONTH)}.${it.get(Calendar.MONTH) + 1}."
        }
        label to sessions.filter { it.start in from until to }.sumOf { it.ms }
    }

    // Streak: consecutive days with play, ending today (or yesterday, if
    // nothing yet today — the streak isn't broken until the day is over).
    val days = sessions.map { startOfDay(it.start) }.toSet()
    var day = startOfDay(now)
    if (day !in days) day = Calendar.getInstance().apply { timeInMillis = day; add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
    var streak = 0
    while (day in days) {
        streak++
        day = Calendar.getInstance().apply { timeInMillis = day; add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
    }

    val byKey = games.associateBy { it.stableKey }
    val consoles = playtime.entries
        .groupBy { byKey[it.key]?.platform?.let { p -> com.shelf.launcher.data.ConsoleNames.display(p) } ?: "Other" }
        .mapValues { (_, list) -> list.sumOf { it.value } }
        .filterValues { it > 0 }
        .entries.sortedByDescending { it.value }.take(5).map { it.key to it.value }
    val topGames = playtime.entries.filter { it.value > 0 }
        .sortedByDescending { it.value }.take(5)
        .map { (key, ms) -> (byKey[key]?.name?.let { gameDisplayName(it) } ?: key.substringAfterLast('/')) to ms }

    return Stats(
        week = sessions.filter { it.start >= weekStart }.sumOf { it.ms },
        month = sessions.filter { it.start >= monthStart }.sumOf { it.ms },
        total = playtime.values.sum(),
        streak = streak,
        gamesPlayed = playtime.count { it.value > 0 },
        weeks = weeks,
        consoles = consoles,
        topGames = topGames
    )
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(ShelfTheme.colors.surfaceRaised)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(label.uppercase(), color = ShelfTheme.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Spacer(Modifier.height(2.dp))
        // Long values ("12.5 h" is fine, anything wordier) get smaller instead
        // of being cut off — "under a minute" used to show as just "under a".
        Text(
            value, color = ShelfTheme.colors.textPrimary,
            fontSize = if (value.length > 7) 16.sp else 20.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(if (value.length > 7) 16.sp else 20.sp), maxFontSize = if (value.length > 7) 16.sp else 20.sp),
            fontWeight = FontWeight.Bold, maxLines = 2, softWrap = true, overflow = TextOverflow.Ellipsis
        )
    }
}

/** Short enough for a tile: "0 min", "<1 min", "45 min", "3.5 h". */
private fun tileTime(ms: Long): String = when {
    ms <= 0L -> "0 min"
    ms < 60_000L -> "<1 min"
    else -> PlaytimeStore.format(ms)
}

/** One accent series; only this week and the best week carry a number. */
@Composable
private fun WeekBars(weeks: List<Pair<String, Long>>) {
    val max = weeks.maxOf { it.second }.coerceAtLeast(1L)
    val best = weeks.indexOfFirst { it.second == max && max > 1L }
    Spacer(Modifier.height(8.dp))
    Row(
        Modifier.fillMaxWidth().height(120.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        weeks.forEachIndexed { i, (_, ms) ->
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                // (Update 224, #02: last week's time shows too, to compare with this week's.)
                if (ms > 0 && (i >= weeks.lastIndex - 1 || i == best)) {
                    Text(PlaytimeStore.format(ms), color = ShelfTheme.colors.textPrimary, fontSize = 10.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(10.sp), maxFontSize = 10.sp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight((ms.toFloat() / max).coerceIn(if (ms > 0) 0.03f else 0.01f, 1f))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        // Update 224 (#02): this week in the full accent, last week a step lighter, earlier weeks quieter —
                        // the current comparison stands out at a glance.
                        .background(
                            if (ms > 0) ShelfTheme.accent.copy(alpha = when (i) { weeks.lastIndex -> 1f; weeks.lastIndex - 1 -> 0.7f; else -> 0.42f })
                            else Color.White.copy(alpha = 0.08f)
                        )
                )
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        weeks.forEachIndexed { i, (label, _) ->
            // (Update 224, #02: "This week" / "Last week" by name, in their bars' colours.)
            val text = when (i) { weeks.lastIndex -> "This week"; weeks.lastIndex - 1 -> "Last week"; else -> label }
            Text(
                text,
                color = when (i) { weeks.lastIndex -> ShelfTheme.accent; weeks.lastIndex - 1 -> ShelfTheme.colors.textPrimary; else -> ShelfTheme.textMuted },
                fontWeight = if (i >= weeks.lastIndex - 1) androidx.compose.ui.text.font.FontWeight.Bold else null,
                fontSize = 10.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(8.sp), maxFontSize = 10.sp),
                maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Name, a bar relative to the top entry, and the time. */
@Composable
private fun BarLine(name: String, ms: Long, max: Long) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(13.sp), maxFontSize = 13.sp), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(170.dp))
        Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.08f))) {
            Box(
                Modifier
                    .fillMaxWidth((ms.toFloat() / max.coerceAtLeast(1L)).coerceIn(0.02f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(ShelfTheme.accent)
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(PlaytimeStore.format(ms), color = ShelfTheme.textMuted, fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}
