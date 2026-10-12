package com.shelf.launcher.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.RaAchievementDetail
import com.shelf.launcher.data.RaBadges
import com.shelf.launcher.data.RaComment
import com.shelf.launcher.data.RaMatchSource
import com.shelf.launcher.data.ra.RaMatchInfo
import com.shelf.launcher.ui.theme.ShelfTheme

private val MissableAmber = Color(0xFFE0A030)

/** Small icon-only "missable" marker (amber warning) or, once the person marked it, a muted "marked missed" one. */
@Composable
internal fun MissableIcon(markedMissed: Boolean = false, modifier: Modifier = Modifier) {
    if (markedMissed) {
        Icon(
            Icons.Filled.Block, "Marked as missed",
            tint = ShelfTheme.textMuted, modifier = modifier.size(15.dp)
        )
    } else {
        Icon(
            Icons.Filled.Warning, "Missable",
            tint = MissableAmber, modifier = modifier.size(15.dp)
        )
    }
}

/** Legend for the two icons above (only shown when the list has a missable). Update 144: labels start with a capital. */
@Composable
internal fun MissableLegend() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Warning, null, tint = MissableAmber, modifier = Modifier.size(13.dp))
        Text(" Missable  ·  ", color = ShelfTheme.textMuted, fontSize = 12.sp)
        Icon(Icons.Filled.Block, null, tint = ShelfTheme.textMuted, modifier = Modifier.size(13.dp))
        Text(" Marked as missed", color = ShelfTheme.textMuted, fontSize = 12.sp)
    }
}

/** Shown only for a title/fallback match: the game file itself wasn't matched. */
@Composable
internal fun RaMatchWarning(info: RaMatchInfo, onFixLink: () -> Unit, selected: Boolean = false) {
    if (info.source != RaMatchSource.TITLE) return
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MissableAmber.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Warning, null, tint = MissableAmber, modifier = Modifier.padding(top = 2.dp).size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "Linked by title, not by the game file — achievements may belong to a different version (e.g. Gold vs Silver).",
                color = ShelfTheme.colors.textPrimary, fontSize = 12.sp, lineHeight = 16.sp
            )
        }
        val matched = listOfNotNull(info.raTitle, info.raConsole).joinToString(" · ")
        if (matched.isNotBlank()) {
            Text("Matched: $matched", color = ShelfTheme.textMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Spacer(Modifier.height(6.dp))
        Row { Chip("Fix link", accent = selected) { onFixLink() } }
    }
}

/** Unlock times come from RA as "yyyy-MM-dd HH:mm:ss" (UTC); show them in the system locale/zone. */
private fun formatRaDate(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        val parser = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        parser.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val date = parser.parse(raw.trim()) ?: return raw
        java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(date)
    }.getOrDefault(raw)
}

private fun typeLabel(type: String?): String? = when (type?.trim()?.lowercase()) {
    "missable" -> "Missable"
    "progression" -> "Progression"
    "win_condition" -> "Win condition"
    else -> null // standard / unset: nothing to say
}

/** One achievement in full: description, unlock info, rarity, "I missed this" and community comments. */
@Composable
fun AchievementDetailDialog(
    achievement: RaAchievementDetail,
    markedMissed: Boolean,
    onMarkMissed: (Boolean) -> Unit,
    comments: List<RaComment>?,
    commentsLoading: Boolean,
    commentsFailed: Boolean,
    commentsHasMore: Boolean,
    onLoadComments: () -> Unit,
    onLoadMoreComments: () -> Unit,
    matchInfo: RaMatchInfo?,
    showRarity: Boolean,
    onDismiss: () -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("AchievementDetailDialog")
    ShelfDialog(
        icon = Icons.Filled.EmojiEvents,
        title = achievement.title ?: "Unknown achievement",
        subtitle = achievement.points?.let { "$it points" },
        onDismiss = onDismiss,
        maxContentHeightFraction = 0.75f
    ) {
        ShelfCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val badgeUrl = RaBadges.url(achievement.badgeName)
                if (badgeUrl != null) {
                    AsyncImage(
                        model = badgeUrl,
                        contentDescription = null,
                        colorFilter = if (achievement.isUnlocked) null else GreyMatrix,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .alpha(if (achievement.isUnlocked) 1f else 0.4f)
                    )
                } else {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.08f)))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        achievement.description ?: "No description.",
                        color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, lineHeight = 19.sp
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            val soft = formatRaDate(achievement.dateEarned)
            val hard = formatRaDate(achievement.dateEarnedHardcore)
            if (!achievement.isUnlocked) {
                DetailLine("Status", "Locked")
            } else {
                DetailLine("Status", "Unlocked")
                if (soft != null) DetailLine("Softcore", soft)
                if (hard != null) DetailLine("Hardcore", hard)
            }
            achievement.points?.let { DetailLine("Points", "$it") }
            val pct = achievement.unlockPercent
            if (showRarity && pct != null) DetailLine("Rarity", "Unlocked by ${formatRarity(pct)} of players")
            typeLabel(achievement.type)?.let { DetailLine("Type", it) }
            if (matchInfo != null && matchInfo.isVerified) {
                DetailLine("Set", "Verified by file hash")
            }
        }

        if (achievement.isMissable) {
            Spacer(Modifier.height(10.dp))
            ShelfCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MissableIcon(markedMissed)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            achievement.isUnlocked -> "Missable — you already have this one."
                            markedMissed -> "Missable — you marked this as missed."
                            else -> "Missable — the set author flagged it as easy to lose for good."
                        },
                        color = ShelfTheme.colors.textPrimary, fontSize = 12.sp, lineHeight = 16.sp
                    )
                }
                // RetroAchievements can't tell whether it's still obtainable, so this is only ever your own mark.
                if (!achievement.isUnlocked) {
                    Spacer(Modifier.height(8.dp))
                    Row { Chip(if (markedMissed) "Undo" else "I missed this", accent = !markedMissed) { onMarkMissed(!markedMissed) } }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionLabel("Community comments")
        Spacer(Modifier.height(6.dp))
        LaunchedEffect(achievement.id) { onLoadComments() }
        when {
            comments == null && commentsLoading -> MutedNote("Loading comments…")
            comments == null && commentsFailed -> {
                MutedNote("Couldn't load comments.")
                Spacer(Modifier.height(6.dp))
                Row { Chip("Retry", accent = false) { onLoadComments() } }
            }
            comments == null -> MutedNote("Loading comments…")
            comments.isEmpty() -> MutedNote("No comments on this one yet.")
            else -> {
                // Newest first, whatever has been loaded so far.
                comments.sortedByDescending { it.Submitted ?: "" }.forEach { c ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Text(
                            (c.User ?: "Someone") + (c.Submitted?.take(10)?.let { "  ·  $it" } ?: ""),
                            color = ShelfTheme.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(cleanComment(c.CommentText.orEmpty()), color = ShelfTheme.colors.textPrimary, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
                if (commentsHasMore) {
                    Spacer(Modifier.height(6.dp))
                    Row {
                        if (commentsLoading) MutedNote("Loading older comments…")
                        else Chip("Load older", accent = false) { onLoadMoreComments() }
                    }
                }
                if (commentsFailed) {
                    Spacer(Modifier.height(4.dp))
                    MutedNote("Couldn't load more — try \"Load older\" again.")
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, color = ShelfTheme.textMuted, fontSize = 12.sp, modifier = Modifier.width(76.dp))
        Text(value, color = ShelfTheme.colors.textPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
    }
}

private fun formatRarity(p: Float): String = when {
    p <= 0f -> "0 %"
    p < 1f -> "<1 %"
    p < 10f -> String.format(java.util.Locale.getDefault(), "%.1f %%", p)
    else -> "${p.toInt()} %"
}

/**
 * One achievement's details shown right under its row (instead of a popup): unlock dates, rarity,
 * type, the missable mark and the community comments — compact, and every comment reachable with
 * the D-pad so the list scrolls along. Comments run oldest → newest by default (switchable).
 */
@Composable
internal fun AchievementInlineDetail(
    achievement: RaAchievementDetail,
    markedMissed: Boolean,
    onMarkMissed: (Boolean) -> Unit,
    comments: List<RaComment>?,
    commentsLoading: Boolean,
    commentsFailed: Boolean,
    commentsHasMore: Boolean,
    onLoadComments: () -> Unit,
    onLoadMoreComments: () -> Unit,
    showRarity: Boolean,
    onClose: () -> Unit,
    /** RetroAchievements game id: offers "Pin to the in-game overlay" for a locked achievement. */
    raGameId: Int? = null
) {
    LaunchedEffect(achievement.id) { if (comments == null) onLoadComments() }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 50.dp, end = 4.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        // Facts on one or two short lines.
        val facts = buildList {
            if (!achievement.isUnlocked) add("Locked")
            else {
                formatRaDate(achievement.dateEarnedHardcore)?.let { add("Hardcore $it") }
                    ?: formatRaDate(achievement.dateEarned)?.let { add("Unlocked $it") }
                    ?: add("Unlocked")
            }
            val pct = achievement.unlockPercent
            if (showRarity && pct != null) add("${formatRarity(pct)} of players")
            typeLabel(achievement.type)?.let { add(it) }
        }
        Text(facts.joinToString("  ·  "), color = ShelfTheme.textMuted, fontSize = 11.sp, lineHeight = 15.sp)
        if (raGameId != null && !achievement.isUnlocked) {
            // The one you're working on stays visible in the in-game RetroAchievements overlay.
            val pinned = com.shelf.launcher.data.RaPin.isPinned(raGameId, achievement.id)
            Spacer(Modifier.height(6.dp))
            Chip(if (pinned) "📌 Pinned to the in-game overlay · unpin" else "📌 Pin to the in-game overlay", accent = pinned) {
                com.shelf.launcher.data.RaPin.toggle(raGameId, achievement.id)
            }
        }
        if (achievement.isMissable && !achievement.isUnlocked) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MissableIcon(markedMissed)
                Spacer(Modifier.width(6.dp))
                Chip(if (markedMissed) "Undo \"missed\"" else "I missed this", accent = false) { onMarkMissed(!markedMissed) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("COMMENTS", color = ShelfTheme.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
            Chip(if (commentsNewestFirst.value) "Newest first" else "Oldest first", accent = false) {
                commentsNewestFirst.value = !commentsNewestFirst.value
            }
            Spacer(Modifier.width(6.dp))
            Chip("Close", accent = false, onClick = onClose)
        }
        Spacer(Modifier.height(4.dp))
        val loadOlder: @Composable () -> Unit = {
            if (commentsHasMore) {
                Spacer(Modifier.height(4.dp))
                Row {
                    if (commentsLoading) MutedNote("Loading older comments…")
                    else Chip("Load older", accent = false) { onLoadMoreComments() }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
        when {
            comments == null && commentsFailed -> Row { Chip("Couldn't load — retry", accent = false) { onLoadComments() } }
            comments == null -> MutedNote("Loading comments…")
            comments.isEmpty() -> MutedNote("No comments on this one yet.")
            else -> {
                // Older pages sit at the top when reading oldest → newest.
                if (!commentsNewestFirst.value) loadOlder()
                sortedComments(comments).forEach { c ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            // Focusable so ▲ ▼ walk through the comments (and the list scrolls with them).
                            .shelfClickable(RoundedCornerShape(8.dp)) { }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Text(
                            (c.User ?: "Someone") + (c.Submitted?.take(10)?.let { "  ·  $it" } ?: ""),
                            color = ShelfTheme.accent, fontSize = 10.sp, fontWeight = FontWeight.SemiBold
                        )
                        Text(cleanComment(c.CommentText.orEmpty()), color = ShelfTheme.colors.textPrimary, fontSize = 12.sp, lineHeight = 15.sp)
                    }
                }
                if (commentsNewestFirst.value) loadOlder()
                if (commentsFailed) MutedNote("Couldn't load more — try \"Load older\" again.")
            }
        }
    }
}
