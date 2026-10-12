package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Flag
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.GameJourney
import com.shelf.launcher.data.RaAchievementDetail
import com.shelf.launcher.data.RaCompletionEntry
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Appearance › Details window: the game popup's Play button style, Game Journey and the milestone line. */
@Composable
internal fun GamePopupSettings() {
    val F = Features
    Column {
        SectionLabel("Layout")
        MutedNote("Showcase: a \"Continue playing\" line and stat boxes above, a Play pill with round buttons beside it. Classic: the earlier row of numbers.")
        Spacer(Modifier.height(8.dp))
        ChipFlow {
            Features.HeaderStyle.values().forEach { st ->
                Chip(st.label, accent = F.headerStyle.state.value == st) { F.headerStyle.value = st }
            }
        }
        Spacer(Modifier.height(6.dp))
        SwitchRow(
            androidx.compose.material.icons.Icons.Filled.PlayArrow, "Game header on the home screen",
            "The same title, stat boxes and Play pill with round buttons for the game you're on, above the row (classic row layout).",
            F.homeHeader.state.value, onChange = { F.homeHeader.value = it }
        )
        if (F.headerStyle.state.value == Features.HeaderStyle.CLASSIC) {
            Spacer(Modifier.height(10.dp))
            SectionLabel("Play button")
            MutedNote("How Play (Continue once you've played the game) looks in the classic layout.")
            Spacer(Modifier.height(8.dp))
            ChipFlow {
                Features.PlayStyle.values().forEach { st ->
                    Chip(st.label, accent = F.playStyle.state.value == st) { F.playStyle.value = st }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        SwitchRow(
            androidx.compose.material.icons.Icons.Filled.Timeline, "Game Journey",
            "First played → sessions → achievements → milestones → mastery, as a quiet timeline in the game popup and the hero carousel's History page.",
            F.gameJourney.state.value, onChange = { F.gameJourney.value = it }
        )
        SwitchRow(
            androidx.compose.material.icons.Icons.Filled.Flag, "Milestone line",
            "One line at a time next to the game: \"Last session: 1h 42m\", \"3 achievements unlocked\", \"Completion +8%\", \"1 achievement remaining\", \"Mastered yesterday\".",
            F.contextMilestone.state.value, onChange = { F.contextMilestone.value = it }
        )
        MutedNote("The guide, tips, manual, leaderboards and \"Add missing\" are under ⋯ More in the popup (B closes it again).")
    }
}

/** The game's journey steps, worked out off the main thread (empty until ready). */
@Composable
internal fun rememberGameJourney(game: Game, achievements: List<RaAchievementDetail>, completion: RaCompletionEntry?): List<GameJourney.Step> {
    val context = LocalContext.current
    val steps by produceState(emptyList<GameJourney.Step>(), game.stableKey, achievements.size, achievements.count { it.isUnlocked }, completion?.HighestAwardKind) {
        value = withContext(Dispatchers.IO) { runCatching { GameJourney.build(context, game, achievements, completion) }.getOrDefault(emptyList()) }
    }
    return steps
}

/** The one contextual milestone line for a game (null when off or nothing to say). */
@Composable
internal fun rememberContextMilestone(key: String, earned: Int, total: Int, completion: RaCompletionEntry?, lastPlayed: Long?): String? {
    val context = LocalContext.current
    val on = Features.contextMilestone.state.value
    val line by produceState<String?>(null, key, earned, total, completion?.HighestAwardKind, lastPlayed, on) {
        value = if (!on) null else withContext(Dispatchers.IO) {
            runCatching { GameJourney.contextLine(context, key, earned, total, completion, lastPlayed) }.getOrNull()
        }
    }
    return line
}

/**
 * Game Journey: a quiet vertical timeline — a dot and a thin line per step, the date on the side, the
 * step's line and a few badges. The step still ahead (mastery) is drawn hollow.
 */
@Composable
internal fun GameJourneyTimeline(steps: List<GameJourney.Step>, modifier: Modifier = Modifier, compact: Boolean = false) {
    val fmt = remember { java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM) }
    Column(modifier) {
        steps.forEachIndexed { i, s ->
            val last = i == steps.lastIndex
            Row(Modifier.height(IntrinsicSize.Min)) {
                // The rail: dot and line.
                Box(Modifier.width(18.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    if (!last) Box(Modifier.padding(top = 12.dp).width(2.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.14f)))
                    val c = when (s.kind) {
                        GameJourney.Kind.COMPLETION -> com.shelf.launcher.ui.theme.Ds.gold
                        GameJourney.Kind.ACHIEVEMENT -> ShelfTheme.accent
                        else -> Color.White.copy(alpha = 0.75f)
                    }
                    Box(
                        Modifier.padding(top = 4.dp).size(if (s.kind == GameJourney.Kind.COMPLETION) 12.dp else 9.dp).clip(CircleShape)
                            .background(if (s.done) c else Color.Transparent)
                            .border(1.5.dp, c, CircleShape)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f).padding(bottom = if (compact) 8.dp else 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            s.title, color = if (s.done) Color.White else Color.White.copy(alpha = 0.6f),
                            fontSize = if (compact) 13.sp else 14.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(if (compact) 13.sp else 14.sp), maxFontSize = if (compact) 13.sp else 14.sp), fontWeight = FontWeight.SemiBold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                        )
                        s.at?.let {
                            Spacer(Modifier.width(8.dp))
                            Text(fmt.format(java.util.Date(it)), color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                    s.detail?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (s.badges.isNotEmpty() && !compact) {
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            s.badges.take(6).forEach { b ->
                                AsyncImage(model = b, contentDescription = null, contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(22.dp).clip(RoundedCornerShape(4.dp)))
                            }
                        }
                    }
                }
            }
        }
    }
}
