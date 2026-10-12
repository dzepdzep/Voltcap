package com.shelf.launcher.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.CloudSync
import com.shelf.launcher.data.PlaytimeStore
import com.shelf.launcher.data.SyncState
import com.shelf.launcher.data.SyncStatusStore
import com.shelf.launcher.ui.theme.ShelfTheme

/**
 * What the card after a game shows. Plain values only (it lives in LauncherUiState).
 * [syncTracked]: a scoped save sync was started for this game, so the card follows its live status.
 */
data class SessionSummary(
    val gameKey: String,
    val gameName: String,
    /** This session's length; under a minute is shown as a short session. */
    val sessionMs: Long,
    val totalMs: Long,
    val achievementCount: Int = 0,
    /** Up to three titles. */
    val achievementTitles: List<String> = emptyList(),
    val syncTracked: Boolean = false,
    /** When the session ended; a sync status older than this isn't this session's. */
    val createdAt: Long = System.currentTimeMillis(),
    /** Badge pictures of this session's unlocks (up to six). */
    val achievementBadges: List<String> = emptyList(),
    /** Unlocked / total after the session (0 total = no achievement set). */
    val unlockedAfter: Int = 0,
    val achievementTotal: Int = 0,
    /** Completion before → after (-1 = unknown). */
    val percentBefore: Int = -1,
    val percentAfter: Int = -1,
    /** Sessions of this game so far (this one included) and the longest one before this. */
    val sessionCount: Int = 0,
    val longestBefore: Long = 0L,
    /** The one milestone worth saying ("Mastered", "1 achievement remaining", "Longest session yet"…). */
    val milestone: String? = null
) {
    /** Picks the single most meaningful milestone for this session. */
    fun withMilestone(): SessionSummary {
        val m = when {
            achievementTotal > 0 && unlockedAfter >= achievementTotal && achievementCount > 0 -> "Mastered — 100% complete"
            achievementTotal > 0 && achievementTotal - unlockedAfter == 1 -> "1 achievement remaining"
            percentBefore >= 0 && percentAfter - percentBefore >= 5 -> "Completion +${percentAfter - percentBefore}%"
            sessionCount == 1 -> "First session"
            longestBefore > 0 && sessionMs > longestBefore && sessionMs >= 20 * 60_000L -> "Longest session yet"
            achievementTotal > 0 && com.shelf.launcher.data.remainingNote(unlockedAfter, achievementTotal) != null ->
                com.shelf.launcher.data.remainingNote(unlockedAfter, achievementTotal)
            else -> null
        }
        return copy(milestone = m)
    }
}

/** "Show a summary after playing" (default on). Tiny device-local prefs, like the other feature stores. */
object SessionSummaryPrefs {
    private const val PREFS = "session_summary"
    private const val KEY = "enabled"
    fun enabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)
    fun setEnabled(context: Context, on: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
    }
}

/** Settings switch for [SessionSummaryPrefs] (Appearance › Home screen). */
@Composable
fun SessionSummaryToggle(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var on by remember { mutableStateOf(runCatching { SessionSummaryPrefs.enabled(context) }.getOrDefault(true)) }
    SwitchRow(
        icon = Icons.Filled.CloudSync,
        title = "Brief update after playing",
        subtitle = "For a few seconds in the status bar when you're back: time played and new achievements. The full summary is in the game's popup.",
        checked = on,
        onChange = { on = it; SessionSummaryPrefs.setEnabled(context, it) },
        modifier = modifier
    )
}

/**
 * The post-session card, bottom corner, after a game: the game's artwork running into the card, time
 * played (and the total), the achievements earned with their badges, count + percentage, the
 * completion change (41% → 48%) and one milestone. It is not focusable and has no key handling, so it
 * can't take the controller or swallow the first key press; a tap closes it, and it hides itself after
 * ~7 s (longer with unlocks, and while the save upload is still running).
 */
@Composable
fun SessionSummaryCard(summary: SessionSummary?, onDismiss: () -> Unit, modifier: Modifier = Modifier, art: String? = null) {
    // Keeps the last card while it fades out.
    // Plain holder (not state): updated before it's read, so no write during composition.
    val shownRef = remember { arrayOfNulls<SessionSummary>(1) }
    if (summary != null) shownRef[0] = summary
    val shown = shownRef[0]
    LaunchedEffect(summary?.createdAt, summary?.gameKey, summary?.achievementCount) {
        if (summary == null) return@LaunchedEffect
        kotlinx.coroutines.delay(if (summary.achievementCount > 0) 10_000 else 7_000)
        if (summary.syncTracked && CloudSync.state.value.running) kotlinx.coroutines.delay(5_000)
        onDismiss()
    }
    AnimatedVisibility(
        visible = summary != null,
        enter = fadeIn(androidx.compose.animation.core.tween(220)) + slideInVertically(androidx.compose.animation.core.tween(240)) { it / 4 },
        exit = fadeOut(androidx.compose.animation.core.tween(180)) + slideOutVertically(androidx.compose.animation.core.tween(200)) { it / 4 },
        modifier = modifier
    ) {
        val s = shown ?: return@AnimatedVisibility
        val shape = RoundedCornerShape(com.shelf.launcher.ui.theme.Ds.rLarge)
        androidx.compose.foundation.layout.Box(
            Modifier
                .widthIn(min = 280.dp, max = 400.dp)
                .clip(shape)
                .background(Color(0xFF0B0C10).copy(alpha = 0.94f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
        ) {
            // The artwork, faded into the card from the right.
            if (art != null) {
                coil.compose.AsyncImage(
                    model = art, contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    alpha = 0.55f,
                    modifier = Modifier.matchParentSize()
                )
                androidx.compose.foundation.layout.Box(
                    Modifier.matchParentSize().background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            0f to Color(0xFF0B0C10), 0.55f to Color(0xFF0B0C10).copy(alpha = 0.86f), 1f to Color(0xFF0B0C10).copy(alpha = 0.35f)
                        )
                    )
                )
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    "SESSION", color = ShelfTheme.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp
                )
                Text(
                    s.gameName, color = Color.White, fontSize = 16.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(16.sp), maxFontSize = 16.sp), fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                val played = if (s.sessionMs >= 60_000L) PlaytimeStore.format(s.sessionMs) else "Short session"
                val total = if (s.totalMs > s.sessionMs) " · ${PlaytimeStore.format(s.totalMs)} total" else ""
                Text(played + total, color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (s.achievementCount > 0) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        s.achievementBadges.take(5).forEachIndexed { i, url ->
                            coil.compose.AsyncImage(
                                model = url, contentDescription = null,
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(5.dp))
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${s.achievementCount} achievement${if (s.achievementCount == 1) "" else "s"} earned",
                            color = com.shelf.launcher.ui.theme.Ds.gold, fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
                if (s.achievementTotal > 0) {
                    Spacer(Modifier.height(6.dp))
                    val change = if (s.percentBefore >= 0 && s.percentAfter > s.percentBefore) "   ${s.percentBefore}% → ${s.percentAfter}%" else ""
                    Text(
                        com.shelf.launcher.data.achievementProgress(s.unlockedAfter, s.achievementTotal) + change,
                        color = Color.White, fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp), fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    // Progress line: the part gained this session in gold.
                    val after = s.unlockedAfter.toFloat() / s.achievementTotal
                    val before = if (s.percentBefore >= 0) s.percentBefore / 100f else after
                    androidx.compose.foundation.layout.Box(
                        Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.14f))
                    ) {
                        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth(after.coerceIn(0f, 1f)).height(3.dp).background(com.shelf.launcher.ui.theme.Ds.gold))
                        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth(before.coerceAtMost(after).coerceIn(0f, 1f)).height(3.dp).background(ShelfTheme.accent))
                    }
                }
                s.milestone?.let { m ->
                    Spacer(Modifier.height(6.dp))
                    Text(m, color = ShelfTheme.accent, fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** The post-game line in the status bar: fades in, stays a few seconds, fades out. */
@Composable
internal fun SessionStatusNote(text: String?) {
    var last by remember { mutableStateOf("") }
    if (text != null) last = text
    androidx.compose.animation.AnimatedVisibility(
        visible = text != null,
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandHorizontally(),
        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkHorizontally()
    ) {
        androidx.compose.material3.Text(
            "🎮 $last", color = com.shelf.launcher.ui.theme.ShelfTheme.colors.textPrimary,
            fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 8.dp).widthIn(max = 320.dp)
        )
    }
}

/** The last session in the game's popup: time, unlocks, completion change and the milestone. */
@Composable
internal fun LastSessionLine(s: SessionSummary) {
    val parts = buildList {
        add("Last session " + com.shelf.launcher.data.PlaytimeStore.format(s.sessionMs))
        if (s.achievementCount > 0) add("+${s.achievementCount} achievement${if (s.achievementCount == 1) "" else "s"}" +
            if (s.achievementTitles.isNotEmpty()) " (${s.achievementTitles.joinToString(", ")})" else "")
        if (s.percentBefore >= 0 && s.percentAfter >= 0 && s.percentAfter != s.percentBefore) add("${s.percentBefore}% → ${s.percentAfter}%")
        s.milestone?.let { add(it) }
    }
    androidx.compose.material3.Text(
        "🎮  " + parts.joinToString("  ·  "), color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f),
        fontSize = 13.sp, lineHeight = 17.sp
    )
}
