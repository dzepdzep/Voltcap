package com.shelf.launcher.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.shelf.launcher.data.GameMetadata
import com.shelf.launcher.data.NewsFeed
import com.shelf.launcher.data.NewsItem
import com.shelf.launcher.data.RaGameProgress
import com.shelf.launcher.data.RowInfo
import com.shelf.launcher.data.shownIn

/**
 * What sits next to the row (Appearance › Next to the row): the game you're
 * on — year, genre, developer, Metacritic, time to beat, achievements, a line
 * of its story — and/or rotating headlines from retro and game news sites.
 * Under the row when the games are at the top or in the middle, above it
 * (over the background art) when they're at the bottom.
 */
@Composable
internal fun RowInfoPanel(
    mode: RowInfo,
    game: Game?,
    metadata: GameMetadata?,
    raProgress: RaGameProgress?,
    newsSources: Set<String>,
    modifier: Modifier = Modifier,
    /** The usual row-text margins on the left / right (off for a card moved in Edit Home, or the inner sides of two). */
    padStart: Boolean = true,
    padEnd: Boolean = true
) {
    // No early return: this project's Compose compiler mishandles them (see AppIcons.kt).
    val showGame = (mode == RowInfo.GAME || mode == RowInfo.BOTH) && game != null
    val showNews = mode == RowInfo.NEWS || mode == RowInfo.BOTH
    if (showGame || showNews) Row(
        modifier
            .fillMaxWidth()
            .padding(start = if (padStart) dockTextStart() else 0.dp, end = if (padEnd) DockCenteredPadding else 0.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (showGame && game != null) {
            GameInfoCard(game, metadata.shownIn(com.shelf.launcher.data.Features.factsRow.state.value), raProgress, Modifier.weight(if (showNews) 1.3f else 1f, fill = !showNews).widthIn(max = 560.dp))
        }
        if (showNews) {
            NewsCard(newsSources, Modifier.weight(1f, fill = true).widthIn(max = 460.dp))
        }
    }
}

private val panelShape = RoundedCornerShape(14.dp)

@Composable
private fun PanelBox(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .clip(panelShape)
            .background(Color.Black.copy(alpha = 0.42f))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) { content() }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun GameInfoCard(game: Game, m: GameMetadata?, progress: RaGameProgress?, modifier: Modifier) {
    PanelBox(modifier) {
        val facts = listOfNotNull(
            m?.releaseYear?.toString(),
            m?.genres?.take(2)?.joinToString(", ")?.takeIf { it.isNotBlank() },
            m?.developer?.takeIf { it.isNotBlank() },
            m?.publisher?.takeIf { it.isNotBlank() },
            LocalTitleOptions.current.console(game.platform)
        )
        Text(
            facts.joinToString("  ·  ").uppercase(),
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 10.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(10.sp), maxFontSize = 10.sp),
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        val tiles = buildList<Pair<String, Color?>> {
            m?.metacritic?.let { add(("MC $it") to scoreColor(it)) }
                ?: m?.criticScore?.let { add(("Critics $it") to scoreColor(it)) }
            m?.normallyHours?.takeIf { it > 0 }?.let { h ->
                add(("⏱ ${if (h < 10) String.format(java.util.Locale.US, "%.1f", h) else h.toInt().toString()} h to beat") to null)
            }
            val possible = progress?.NumPossibleAchievements ?: 0
            if (possible > 0) add(("🏆 " + com.shelf.launcher.data.achievementProgress(progress?.NumAchieved ?: 0, possible)) to null)
        }
        if (tiles.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            // Onto a second line when the card is narrow (they used to be squeezed and cut).
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                tiles.forEach { (text, colour) ->
                    Text(
                        text,
                        color = if (colour != null) Color.Black else Color.White,
                        fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colour ?: Color.White.copy(alpha = 0.12f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        }
        val summary = m?.summary?.takeIf { it.isNotBlank() }
        if (summary != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                summary,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp),
                lineHeight = androidx.compose.ui.unit.TextUnit(1.333f, androidx.compose.ui.unit.TextUnitType.Em),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        } else if (tiles.isEmpty() && m == null) {
            Spacer(Modifier.height(4.dp))
            Text("No details downloaded for this game yet.", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
        }
    }
}

/** Metacritic's own colours: green 75+, yellow 50+, red below. */
private fun scoreColor(score: Int): Color = when {
    score >= 75 -> com.shelf.launcher.ui.theme.Ds.success
    score >= 50 -> com.shelf.launcher.ui.theme.Ds.warning
    else -> com.shelf.launcher.ui.theme.Ds.danger
}

@Composable
private fun NewsCard(sources: Set<String>, modifier: Modifier) {
    val context = LocalContext.current
    // Fetched now and every 30 minutes (NewsFeed keeps its own cache).
    // Only while the panel is on screen and the screen is on; NewsFeed's own 30-minute cache
    // means coming back sooner costs no request.
    val newsActive = rememberIsActive()
    val items by produceState(emptyList<NewsItem>(), sources, newsActive) {
        if (!newsActive) return@produceState
        while (true) {
            value = NewsFeed.load(sources)
            kotlinx.coroutines.delay(30 * 60_000L)
        }
    }
    var index by remember(items) { mutableIntStateOf(0) }
    LaunchedWhileActive(items) {
        while (items.size > 1) {
            kotlinx.coroutines.delay(8_000)
            index = (index + 1) % items.size
        }
    }
    PanelBox(modifier) {
        val item = items.getOrNull(index)
        Crossfade(targetState = item, label = "news") { shown ->
            Column(
                Modifier
                    .fillMaxWidth()
                    // Update 144: a tap that never takes focus (clickable made the card focusable, so the D-pad could get
                    // stuck on it instead of the home row).
                    .tapOnly {
                        shown?.let {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(it.link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                    }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "NEWS",
                        color = com.shelf.launcher.ui.theme.ShelfTheme.accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (shown == null) "Loading…" else shown.source.uppercase() + ago(shown.publishedAt),
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 10.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(10.sp), maxFontSize = 10.sp),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    shown?.title ?: "Headlines from your news sources show here.",
                    color = Color.White,
                    fontSize = 13.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(13.sp), maxFontSize = 13.sp),
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = androidx.compose.ui.unit.TextUnit(1.308f, androidx.compose.ui.unit.TextUnitType.Em),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun ago(at: Long): String {
    if (at <= 0L) return ""
    val minutes = (System.currentTimeMillis() - at) / 60_000
    return when {
        minutes < 60 -> "  ·  ${minutes.coerceAtLeast(1)} MIN AGO"
        minutes < 60 * 24 -> "  ·  ${minutes / 60} H AGO"
        else -> "  ·  ${minutes / (60 * 24)} D AGO"
    }
}
