package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.LaunchedEffect
import com.shelf.launcher.data.ra.RaMatchInfo
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.RaAchievementDetail
import com.shelf.launcher.data.RaBadges
import com.shelf.launcher.data.ra.MissableState
import com.shelf.launcher.data.ra.missableState
import com.shelf.launcher.ui.theme.ShelfTheme

@Composable
fun AchievementListDialog(
    game: Game,
    achievements: List<RaAchievementDetail>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    /** Open on the locked ones — what's still missing. */
    startWithMissing: Boolean = false,
    /** Community comments per achievement ID (loaded when one is opened). */
    comments: Map<Int, List<com.shelf.launcher.data.RaComment>> = emptyMap(),
    commentsLoading: Set<Int> = emptySet(),
    onLoadComments: (Int) -> Unit = {},
    /** What the game reported last time you played it (Rich Presence), if this game. */
    richPresence: String? = null,
    /** "Unlocked by 12 % of players", Rare, Easiest next (Appearance › Achievements). */
    showRarity: Boolean = true,
    /** "Couldn't refresh — showing saved data from …" (null = nothing to say). */
    refreshNote: String? = null,
    /** Achievement IDs the person marked "I missed this" (user-confirmed only). */
    missedMarks: Set<Int> = emptySet(),
    onMarkMissed: (Int, Boolean) -> Unit = { _, _ -> },
    /** Achievements with older comments to load, and the loader. */
    commentsHasMore: Set<Int> = emptySet(),
    onLoadMoreComments: (Int) -> Unit = {},
    /** Achievements whose comments failed to load (shown with a retry hint). */
    commentsFailed: Set<Int> = emptySet(),
    /** How the game is linked to RetroAchievements (null = not linked / unknown). */
    matchInfo: RaMatchInfo? = null,
    /** Opens the RetroAchievements matching dialog for this game. */
    onFixLink: () -> Unit = {},
    /** Fetch the achievements again (shown next to the refresh note). */
    onRetry: () -> Unit = {},
    /** Opens the Achievements screen with every game (null = no button). */
    onOpenHub: (() -> Unit)? = null,
    /** The game's icon (RetroAchievements), for the trophy list's header. */
    gameIcon: String? = null
) {
    com.shelf.launcher.Breadcrumbs.mark("AchievementListDialog")
    var filter by remember {
        mutableStateOf(
            if (startWithMissing) AchievementFilter.LOCKED
            else AchievementFilter.values().firstOrNull { it.name == lastAchievementFilter } ?: AchievementFilter.ALL
        )
    }
    // The achievement whose detail window is open, and the row to give focus
    // back to when it closes.
    var detailId by remember { mutableStateOf<Int?>(null) }
    var returnFocusId by remember { mutableStateOf<Int?>(null) }
    val rowFocus = remember { mutableMapOf<Int, FocusRequester>() }
    LaunchedEffect(returnFocusId) {
        val id = returnFocusId ?: return@LaunchedEffect
        kotlinx.coroutines.delay(60)
        runCatching { rowFocus[id]?.requestFocus() }
        returnFocusId = null
    }
    val hasRarity = showRarity && achievements.any { it.unlockPercent != null }
    // Order: unlocked first then the locked ones (greyed), the set's own, unlock order (timeline),
    // or most-unlocked first (easiest) / least (rarest).
    // (Update 119: the set's own order by default.)
    var sort by remember { mutableStateOf(RaritySort.SET) }
    // What's new since the last look (AchievementSeen): animate once, glow until viewed.
    val context = androidx.compose.ui.platform.LocalContext.current
    val seenKey = game.raGameId?.let { com.shelf.launcher.data.AchievementSeen.keyRa(it) }
    val seenAll by com.shelf.launcher.data.AchievementSeen.all.collectAsState()
    val seenRec = seenKey?.let { seenAll[it] }
    // Captured once when opened, so the glow stays while the list is open (it's cleared on close).
    val glowIds = remember(seenKey, achievements.isNotEmpty()) { seenRec?.glow.orEmpty() }
    val stageIds = remember(seenKey, achievements.isNotEmpty()) {
        if (com.shelf.launcher.data.Features.unlockAnimation.value) seenRec?.pending.orEmpty() else emptySet()
    }
    var stageDone by remember { mutableStateOf(false) }
    var celebrate by remember { mutableStateOf(false) }
    androidx.compose.runtime.DisposableEffect(seenKey) {
        onDispose { if (seenKey != null) com.shelf.launcher.data.AchievementSeen.markViewed(context, seenKey) }
    }
    LaunchedEffect(seenKey, achievements.isNotEmpty()) {
        // Without the animation switch, pending unlocks are simply consumed (they never replay later).
        if (seenKey != null && achievements.isNotEmpty() && stageIds.isEmpty()) {
            seenRec?.pending?.takeIf { it.isNotEmpty() }?.let { com.shelf.launcher.data.AchievementSeen.markAnimated(context, seenKey, it) }
            if (com.shelf.launcher.data.Features.completionCelebration.value &&
                com.shelf.launcher.data.AchievementSeen.takeCompletion(context, seenKey)) celebrate = true
        }
    }
    // Power cells, trophy list, badge wall or list (Settings › Achievements; the chips here change it too).
    val view = com.shelf.launcher.data.Features.achView.state.value
    val wall = view == com.shelf.launcher.data.Features.AchView.BADGES
    val visible = achievements.filter(filter.test)
        .filter { filter != AchievementFilter.MISSABLE_LEFT || it.missableState(missedMarks) == MissableState.OPEN }
        .let { list ->
        when {
            sort == RaritySort.UNLOCKED_FIRST -> list.sortedWith(
                compareByDescending<RaAchievementDetail> { it.isUnlocked }.then(com.shelf.launcher.data.RA_DISPLAY_ORDER)
            )
            sort == RaritySort.TIMELINE -> list.sortedWith(
                compareByDescending<RaAchievementDetail> { it.isUnlocked }
                    .thenByDescending { it.dateEarnedHardcore ?: it.dateEarned ?: "" }
                    .then(com.shelf.launcher.data.RA_DISPLAY_ORDER)
            )
            !hasRarity || sort == RaritySort.SET -> list.sortedWith(com.shelf.launcher.data.RA_DISPLAY_ORDER)
            sort == RaritySort.EASIEST -> list.sortedByDescending { it.unlockPercent ?: -1f }
            else -> list.sortedBy { it.unlockPercent ?: 101f }
        }
    }
    // The locked achievement most players have: a good one to go for next.
    val easiestNext = if (hasRarity) achievements.filter { !it.isUnlocked && it.unlockPercent != null }
        .maxByOrNull { it.unlockPercent ?: 0f }?.id else null
    val unlockedCount = achievements.count { it.isUnlocked }

    ShelfDialog(
        icon = Icons.Filled.EmojiEvents,
        title = "Achievements",
        subtitle = game.name,
        onDismiss = onDismiss,
        maxContentHeightFraction = 0.7f,
        footer = {
            if (onOpenHub != null) Chip("All games", accent = false) { onOpenHub() }
            Chip("Close", onClick = onDismiss)
        }
    ) {
        if (!refreshNote.isNullOrBlank()) {
            MutedNote(refreshNote)
            Spacer(Modifier.height(6.dp))
            Row { Chip("Retry", accent = false) { onRetry() } }
            Spacer(Modifier.height(8.dp))
        }
        // (The old "Linked by title" warning is gone — the link can still be changed under the game's options.)
        when {
            isLoading -> EmptyState("Loading…")
            achievements.isEmpty() -> EmptyState(
                if (refreshNote.isNullOrBlank()) "No achievement data came back for this game — double-check the linked RetroAchievements ID."
                else "Nothing saved yet for this game. Open it again when you're online."
            )
            else -> {
                // New unlocks since the last look: their own stage, one after another, then the summary.
                if (stageIds.isNotEmpty() && seenKey != null) {
                    val stage = achievements.filter { it.id in stageIds && it.isUnlocked }.sortedWith(com.shelf.launcher.data.RA_DISPLAY_ORDER)
                        .map { StageBadge(it.id, RaBadges.url(it.badgeName), it.title ?: "Achievement", it.points) }
                    if (stage.isNotEmpty() && !stageDone) {
                        NewUnlockStage(stage, unlockedCount, achievements.size) {
                            com.shelf.launcher.data.AchievementSeen.markAnimated(context, seenKey, stage.map { it.id })
                            if (com.shelf.launcher.data.Features.completionCelebration.value &&
                                com.shelf.launcher.data.AchievementSeen.takeCompletion(context, seenKey)) celebrate = true
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
                if (celebrate) {
                    CompletionCelebration(
                        images = achievements.sortedWith(com.shelf.launcher.data.RA_DISPLAY_ORDER).map { RaBadges.url(it.badgeName) },
                        title = gameDisplayName(game.name), total = achievements.size,
                        onDone = { celebrate = false; stageDone = true }
                    )
                }
                when (view) {
                    com.shelf.launcher.data.Features.AchView.POWER -> PowerHeader(achievements)
                    com.shelf.launcher.data.Features.AchView.TROPHY -> TrophyHeader(
                        icon = gameIcon, title = gameDisplayName(game.name), list = achievements
                    )
                    else -> ShelfCard {
                    val fraction = unlockedCount.toFloat() / achievements.size
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            com.shelf.launcher.data.achievementProgress(unlockedCount, achievements.size),
                            color = ShelfTheme.colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.weight(1f))
                        val note = if (unlockedCount >= achievements.size) "Mastered"
                            else com.shelf.launcher.data.remainingNote(unlockedCount, achievements.size)
                        if (note != null) Text(note, color = ShelfTheme.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxHeight()
                                .background(ShelfTheme.accent)
                        )
                    }
                    }
                }
                // The game's own status line from your last session — where
                // progress like "50/151 Pokémon caught" shows, when the game
                // reports it (RetroAchievements keeps no per-achievement progress).
                if (!richPresence.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    ShelfCard {
                        Text("LAST SESSION", color = ShelfTheme.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(3.dp))
                        Text(richPresence, color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, lineHeight = 17.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                // Filter pills: the selected one is filled with the accent.
                ChipFlow {
                    AchievementFilter.values().forEach { option ->
                        // Same rule as the list below ("Missables to get" leaves out the ones marked missed).
                        val count = achievements.count {
                            option.test(it) && (option != AchievementFilter.MISSABLE_LEFT || it.missableState(missedMarks) == MissableState.OPEN)
                        }
                        Chip(
                            "${option.label} ($count)",
                            accent = option == filter,
                            onClick = { filter = option; lastAchievementFilter = option.name }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (visible.isEmpty()) {
                    EmptyState(filter.emptyText)
                } else {
                    MutedNote("A (or tap) on an achievement opens its details and comments right under ${if (wall) "its row" else "it"}; A again closes them.")
                    if (achievements.any { it.isMissable }) {
                        Spacer(Modifier.height(4.dp))
                        MissableLegend()
                    }
                    Spacer(Modifier.height(6.dp))
                    ChipFlow {
                        RaritySort.values().filter { hasRarity || !it.needsRarity }.forEach { opt ->
                            Chip(opt.label, accent = opt == sort) { sort = opt }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    ChipFlow {
                        com.shelf.launcher.data.Features.AchView.values().forEach { v ->
                            Chip(v.label, accent = view == v) { com.shelf.launcher.data.Features.achView.value = v }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    // The badges get their own area (the achievement theme's backdrop), with room for the
                    // unlock and pick animations to rise out of their slots.
                    if (wall) AchievementArea {
                        BadgeWall(
                            items = visible.map { a ->
                                BadgeItem(
                                    key = a.id, image = RaBadges.url(a.badgeName), title = a.title ?: "Achievement",
                                    percent = if (hasRarity) a.unlockPercent else null, unlocked = a.isUnlocked,
                                    points = a.points, missable = a.isMissable,
                                    isNew = a.id in glowIds && a.isUnlocked
                                )
                            },
                            animKey = game.stableKey + filter.name + sort.name,
                            selected = detailId,
                            onClick = { item ->
                                val id = item.key as Int
                                if (detailId == id) detailId = null else { detailId = id; onLoadComments(id) }
                            },
                            modifierFor = { item -> Modifier.focusRequester(rowFocus.getOrPut(item.key as Int) { FocusRequester() }) },
                            below = { item ->
                                val a = visible.first { it.id == item.key }
                                AchievementInlineDetail(
                                    raGameId = game.raGameId,
                                    achievement = a,
                                    markedMissed = a.missableState(missedMarks) == MissableState.MARKED_MISSED,
                                    onMarkMissed = { missed -> onMarkMissed(a.id, missed) },
                                    comments = comments[a.id],
                                    commentsLoading = a.id in commentsLoading,
                                    commentsFailed = a.id in commentsFailed,
                                    commentsHasMore = a.id in commentsHasMore,
                                    onLoadComments = { onLoadComments(a.id) },
                                    onLoadMoreComments = { onLoadMoreComments(a.id) },
                                    showRarity = showRarity,
                                    onClose = { detailId = null; returnFocusId = a.id }
                                )
                            }
                        )
                    } else if (view == com.shelf.launcher.data.Features.AchView.POWER || view == com.shelf.launcher.data.Features.AchView.TROPHY) {
                        Column(verticalArrangement = Arrangement.spacedBy(if (view == com.shelf.launcher.data.Features.AchView.POWER) 8.dp else 10.dp)) {
                            visible.forEach { a ->
                                val requester = rowFocus.getOrPut(a.id) { FocusRequester() }
                                val toggle = { if (detailId == a.id) detailId = null else { detailId = a.id; onLoadComments(a.id) } }
                                if (view == com.shelf.launcher.data.Features.AchView.POWER) PowerCell(
                                    a, showRarity = hasRarity, missable = a.isMissable,
                                    markedMissed = a.missableState(missedMarks) == MissableState.MARKED_MISSED, expanded = detailId == a.id,
                                    modifier = Modifier.focusRequester(requester), onClick = toggle
                                ) else TrophyRow(
                                    a, showRarity = hasRarity, missable = a.isMissable,
                                    markedMissed = a.missableState(missedMarks) == MissableState.MARKED_MISSED, expanded = detailId == a.id,
                                    modifier = Modifier.focusRequester(requester), onClick = toggle
                                )
                                if (detailId == a.id) AchievementInlineDetail(
                                    raGameId = game.raGameId,
                                    achievement = a,
                                    markedMissed = a.missableState(missedMarks) == MissableState.MARKED_MISSED,
                                    onMarkMissed = { missed -> onMarkMissed(a.id, missed) },
                                    comments = comments[a.id],
                                    commentsLoading = a.id in commentsLoading,
                                    commentsFailed = a.id in commentsFailed,
                                    commentsHasMore = a.id in commentsHasMore,
                                    onLoadComments = { onLoadComments(a.id) },
                                    onLoadMoreComments = { onLoadMoreComments(a.id) },
                                    showRarity = showRarity,
                                    onClose = { detailId = null; returnFocusId = a.id }
                                )
                            }
                        }
                    } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // (Update 119: each achievement in its own card — they used to share one.)
                        visible.forEach { a -> ShelfCard {
                            val requester = rowFocus.getOrPut(a.id) { FocusRequester() }
                            AchievementRow(
                                a, showRarity = hasRarity, easiestNext = a.id == easiestNext,
                                markedMissed = a.missableState(missedMarks) == MissableState.MARKED_MISSED,
                                modifier = Modifier.focusRequester(requester),
                                expanded = detailId == a.id
                            ) {
                                // A (or tap) opens its details right under it; again closes them.
                                if (detailId == a.id) detailId = null else { detailId = a.id; onLoadComments(a.id) }
                            }
                            if (detailId == a.id) {
                                AchievementInlineDetail(
                                    raGameId = game.raGameId,
                                    achievement = a,
                                    markedMissed = a.missableState(missedMarks) == MissableState.MARKED_MISSED,
                                    onMarkMissed = { missed -> onMarkMissed(a.id, missed) },
                                    comments = comments[a.id],
                                    commentsLoading = a.id in commentsLoading,
                                    commentsFailed = a.id in commentsFailed,
                                    commentsHasMore = a.id in commentsHasMore,
                                    onLoadComments = { onLoadComments(a.id) },
                                    onLoadMoreComments = { onLoadMoreComments(a.id) },
                                    showRarity = showRarity,
                                    onClose = { detailId = null; returnFocusId = a.id }
                                )
                            }
                        } }
                    }
                }
            }
        }
    }

}

@Composable
private fun AchievementRow(
    achievement: RaAchievementDetail,
    modifier: Modifier = Modifier,
    showRarity: Boolean = false,
    easiestNext: Boolean = false,
    markedMissed: Boolean = false,
    expanded: Boolean = false,
    onClick: () -> Unit = {}
) {
    com.shelf.launcher.Breadcrumbs.mark("AchievementRow")
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .shelfClickable(RoundedCornerShape(12.dp)) { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val badgeUrl = RaBadges.url(achievement.badgeName)
        if (badgeUrl != null) {
            AsyncImage(
                model = badgeUrl,
                contentDescription = null,
                colorFilter = if (achievement.isUnlocked) null else GreyMatrix,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    // Locked badges are grey rather than hidden, so you can
                    // still see what you're working toward.
                    .alpha(if (achievement.isUnlocked) 1f else 0.5f)
            )
        } else {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.08f))
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    achievement.title ?: "Unknown achievement",
                    color = if (achievement.isUnlocked) ShelfTheme.colors.textPrimary else ShelfTheme.textMuted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (achievement.isMissable) {
                    Spacer(Modifier.width(6.dp))
                    MissableIcon(markedMissed)
                }
                val pct = achievement.unlockPercent
                if (showRarity && pct != null && pct < 5f) RarityPill("Rare", Color(0xFFB08A2E))
                if (easiestNext) RarityPill("Easiest next", ShelfTheme.accent)
            }
            achievement.description?.let {
                Text(it, color = ShelfTheme.textMuted, fontSize = 12.sp, lineHeight = 16.sp)
            }
            val pct = achievement.unlockPercent
            if (showRarity && pct != null) {
                Text(
                    "Unlocked by ${formatPercent(pct)} of players",
                    color = ShelfTheme.textMuted.copy(alpha = 0.8f),
                    fontSize = 11.sp
                )
            }
        }
        achievement.points?.let {
            Spacer(Modifier.width(8.dp))
            Text("$it pts", color = ShelfTheme.textMuted, fontSize = 11.sp)
        }
        Spacer(Modifier.width(6.dp))
        Text(if (expanded) "▲" else "▼", color = ShelfTheme.textMuted, fontSize = 10.sp)
    }
}

private enum class RaritySort(val label: String, val needsRarity: Boolean = false) {
    UNLOCKED_FIRST("Unlocked first"), TIMELINE("Timeline"), SET("Set order"),
    EASIEST("Most unlocked first", true), RAREST("Rarest first", true)
}

/** The badge wall's own space: the achievement theme's backdrop, and padding so badges can rise. */
@Composable
private fun AchievementArea(content: @Composable () -> Unit) {
    val style = achievementStyle()
    // (No endless clock here: the backdrop draws nothing now, and reading a running clock while
    // composing redrew the whole badge wall on every frame for as long as the list was open.)
    val phase = 0f
    // No clip: badges that jump, pop out or hover may rise above their slots (shared safe-zone rule:
    // room is reserved instead of cutting the motion off). The panel is only painted, not clipped.
    val zone = safeZone()
    val room = if (zone.compact) 22.dp else 30.dp
    Box(
        Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(com.shelf.launcher.ui.theme.Ds.rLarge))
            .achievementBackdrop(style, if (style.animatedBackdrop) phase else 0f)
            .padding(start = 10.dp, end = 10.dp, top = room, bottom = 14.dp)
    ) { content() }
}

private fun formatPercent(p: Float): String = when {
    p <= 0f -> "0 %"
    p < 1f -> "<1 %"
    p < 10f -> String.format(java.util.Locale.US, "%.1f %%", p)
    else -> "${p.toInt()} %"
}

@Composable
private fun RarityPill(text: String, color: Color) {
    Spacer(Modifier.width(6.dp))
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.35f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(text, color = ShelfTheme.colors.textPrimary, fontSize = 10.sp)
    }
}

/** Comment order everywhere (achievement list, game tips): oldest first by
 * default (reads like a conversation); it used to be newest first (that is also the order they're fetched and paged in, so what's on
 * screen is the latest page); "Oldest first" re-orders what has been loaded.
 * Kept while Voltcap runs. */
internal val commentsNewestFirst = androidx.compose.runtime.mutableStateOf(false)

internal fun sortedComments(list: List<com.shelf.launcher.data.RaComment>): List<com.shelf.launcher.data.RaComment> =
    if (commentsNewestFirst.value) list.sortedByDescending { it.Submitted ?: "" } else list.sortedBy { it.Submitted ?: "" }

/** "Oldest first" / "Newest first". */
@Composable
internal fun CommentOrderChips() {
    ChipFlow {
        Chip("Oldest first", accent = !commentsNewestFirst.value) { commentsNewestFirst.value = false }
        Chip("Newest first", accent = commentsNewestFirst.value) { commentsNewestFirst.value = true }
    }
}

/** Comments come with HTML entities and the odd [url]/[b] tag from the site. */
internal fun cleanComment(text: String): String = text
    .replace(Regex("\\[/?(b|i|u|s|url|img|spoiler|quote|code)(=[^\\]]*)?]", RegexOption.IGNORE_CASE), "")
    .replace("&amp;", "&").replace("&quot;", "\"").replace("&#039;", "'").replace("&#39;", "'")
    .replace("&lt;", "<").replace("&gt;", ">")
    .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
    .trim()

/** Last filter picked, kept while Voltcap runs. */
private var lastAchievementFilter: String? = null

private enum class AchievementFilter(
    val label: String,
    val emptyText: String,
    val test: (RaAchievementDetail) -> Boolean
) {
    ALL("All", "No achievements.", { true }),
    LOCKED("Locked", "Everything's unlocked — nice.", { !it.isUnlocked }),
    UNLOCKED("Unlocked", "Nothing unlocked yet.", { it.isUnlocked }),
    MISSABLE("Missable", "No missable achievements for this game.", { it.isMissable }),
    /** Only the missables still to get (not unlocked, and not marked "I missed this"). */
    MISSABLE_LEFT("Missables to get", "No missables left to get — none can slip away now.", { it.isMissable && !it.isUnlocked })
}
