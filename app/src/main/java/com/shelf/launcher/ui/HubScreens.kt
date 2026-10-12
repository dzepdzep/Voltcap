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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.AchievementSeen
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.RaBadges
import com.shelf.launcher.data.RaCompletionEntry
import com.shelf.launcher.data.RaRecentAchievement
import com.shelf.launcher.data.achievementProgress
import com.shelf.launcher.data.remainingNote
import com.shelf.launcher.ui.theme.Ds
import com.shelf.launcher.ui.theme.ShelfTheme

/**
 * Achievements, Stats and Profile are three separate places now (none of them lives in Settings):
 *  - Achievements: every game with achievements and its progress (count + %), near-completion, and a
 *    timeline of unlocks across games. A game opens its own achievement list.
 *  - Stats: play time, streaks, weeks, consoles and the recaps.
 *  - Profile: the RetroAchievements profile, awards, friends and Achievement of the Week.
 */
internal enum class HubScreen { ACHIEVEMENTS, STATS, PROFILE }

private enum class AchTab(val label: String) { GAMES("Games"), NEAR("Near completion"), TIMELINE("Timeline") }

@Composable
internal fun AchievementsHub(
    games: List<Game>,
    completion: Map<Int, RaCompletionEntry>,
    recent: List<RaRecentAchievement>,
    connected: Boolean,
    onOpenGame: (Game) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    LaunchedEffect(Unit) { onRefresh() }
    var tab by remember { mutableStateOf(AchTab.GAMES) }
    // Manual replay of recent unlock animations (never marks anything; separate from automatic NEW).
    var replay by remember { mutableStateOf<List<StageBadge>?>(null) }
    val seen by AchievementSeen.all.collectAsState()
    val linked = remember(games, completion) {
        games.filter { it.raGameId != null }.distinctBy { it.raGameId }.mapNotNull { g ->
            val c = completion[g.raGameId] ?: return@mapNotNull null
            val total = c.MaxPossible ?: 0
            if (total <= 0) null else Triple(g, c, total)
        }
    }
    ShelfDialog(
        icon = Icons.Filled.EmojiEvents,
        title = "Achievements",
        subtitle = if (linked.isEmpty()) null else {
            val done = linked.sumOf { it.second.NumAwarded ?: 0 }
            val all = linked.sumOf { it.third }
            "${linked.size} games · " + achievementProgress(done, all)
        },
        onDismiss = onDismiss,
        widthFraction = 0.92f,
        maxContentHeightFraction = 0.74f
    ) {
        if (!connected) {
            EmptyState("Connect RetroAchievements (Settings › Accounts) to see your achievements here.")
            return@ShelfDialog
        }
        ChipFlow {
            AchTab.values().forEach { t -> Chip(t.label, accent = t == tab) { tab = t } }
        }
        Spacer(Modifier.height(12.dp))
        when (tab) {
            AchTab.GAMES, AchTab.NEAR -> {
                val list = linked
                    .filter { (_, c, total) -> tab == AchTab.GAMES || ((c.NumAwarded ?: 0) < total && remainingNote(c.NumAwarded ?: 0, total) != null) }
                    .sortedWith(
                        if (tab == AchTab.NEAR) compareBy { (_, c, total) -> total - (c.NumAwarded ?: 0) }
                        else compareByDescending { (_, c, _) -> c.MostRecentAwardedDate ?: "" }
                    )
                if (list.isEmpty()) EmptyState(if (tab == AchTab.NEAR) "No game is close to complete yet." else "No achievement progress yet.")
                // Power cells (default), Scorecards or the plain list (Settings › Achievements; these chips too).
                val hubView = com.shelf.launcher.data.Features.achHubView.state.value
                ChipFlow {
                    com.shelf.launcher.data.Features.AchHubView.values().forEach { v ->
                        Chip(v.label, accent = v == hubView) { com.shelf.launcher.data.Features.achHubView.value = v }
                    }
                }
                Spacer(Modifier.height(10.dp))
                val cards = list.map { (g, c, total) ->
                    val earned = c.NumAwarded ?: 0
                    HubGame(
                        title = gameDisplayName(g.name), icon = RaBadges.mediaUrl(c.ImageIcon), console = c.ConsoleName,
                        earned = earned, total = total, hardcore = c.NumAwardedHardcore ?: 0, mastered = earned >= total,
                        isNew = seen[AchievementSeen.keyRa(g.raGameId!!)]?.glow?.isNotEmpty() == true,
                        onClick = { onOpenGame(g) }
                    )
                }
                when (hubView) {
                    com.shelf.launcher.data.Features.AchHubView.SCORECARD -> {
                        val month = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date())
                        val thisMonth = recent.filter { it.Date?.startsWith(month) == true }
                        ScorecardSummary(
                            mastered = linked.count { (it.second.NumAwarded ?: 0) >= it.third },
                            unlocked = linked.sumOf { it.second.NumAwarded ?: 0 },
                            monthCount = thisMonth.size, monthPoints = thisMonth.sumOf { it.Points ?: 0 }
                        )
                        Spacer(Modifier.height(10.dp))
                        TwoColumns(cards) { g, m -> ScorecardTile(g, m) }
                    }
                    com.shelf.launcher.data.Features.AchHubView.POWER -> TwoColumns(cards, minColumn = 280.dp) { g, m -> PowerGameCell(g, m) }
                    com.shelf.launcher.data.Features.AchHubView.LIST -> cards.forEach { g ->
                        HubGameRow(
                            title = g.title, icon = g.icon, line = achievementProgress(g.earned, g.total),
                            note = if (g.mastered) "Mastered" else remainingNote(g.earned, g.total),
                            fraction = g.earned.toFloat() / g.total, isNew = g.isNew, onClick = g.onClick
                        )
                    }
                }
            }
            AchTab.TIMELINE -> {
                if (recent.isEmpty()) EmptyState("No unlocks in the last weeks.")
                else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MutedNote("Newest first, across all your games.")
                        Spacer(Modifier.weight(1f))
                        Chip("Replay latest", accent = false) {
                            replay = recent.take(6).mapNotNull { a ->
                                a.AchievementID?.let { StageBadge(it, RaBadges.url(a.BadgeName), a.Title ?: "Achievement", a.Points) }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    replay?.let { r ->
                        androidx.compose.runtime.key(r) {
                            NewUnlockStage(r, unlocked = 0, total = 0) {}
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    var lastDay: String? = null
                    recent.sortedByDescending { it.Date ?: "" }.forEach { a ->
                        val day = a.Date?.take(10)
                        if (day != lastDay) {
                            lastDay = day
                            Text(
                                day?.let { friendlyDay(it) } ?: "", color = ShelfTheme.accent, fontSize = 11.sp,
                                fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                            )
                        }
                        val g = games.firstOrNull { it.raGameId == a.GameID }
                        TimelineRow(a, onClick = g?.let { { onOpenGame(it) } })
                    }
                }
            }
        }
    }
}

private fun friendlyDay(iso: String): String = runCatching {
    val d = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(iso) ?: return iso
    java.text.SimpleDateFormat("EEE d MMM yyyy", java.util.Locale.getDefault()).format(d).uppercase()
}.getOrDefault(iso)

@Composable
private fun HubGameRow(title: String, icon: String?, line: String, note: String?, fraction: Float, isNew: Boolean, onClick: () -> Unit) {
    val shape = uiRounded(Ds.rMedium)
    Row(
        Modifier.fillMaxWidth().clip(shape).shelfClickable(shape) { onClick() }.padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).clip(uiRounded(8.dp)).background(Color.White.copy(alpha = 0.06f))) {
            if (icon != null) AsyncImage(model = icon, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(14.sp), maxFontSize = 14.sp), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (isNew) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.clip(uiRounded(50)).background(Ds.gold).padding(horizontal = 6.dp, vertical = 1.dp)) {
                        Text("NEW", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(uiRounded(2.dp)).background(Color.White.copy(alpha = 0.12f))) {
                Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(if (fraction >= 1f) Ds.gold else ShelfTheme.accent))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(line, color = ShelfTheme.colors.textPrimary, fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp), fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (note != null) Text(note, color = if (note == "Mastered") Ds.gold else ShelfTheme.accent, fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TimelineRow(a: RaRecentAchievement, onClick: (() -> Unit)?) {
    val shape = uiRounded(Ds.rMedium)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .then(if (onClick != null) Modifier.shelfClickable(shape) { onClick() } else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(model = RaBadges.url(a.BadgeName), contentDescription = null, modifier = Modifier.size(36.dp).clip(uiRounded(6.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(a.Title ?: "Achievement", color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(13.sp), maxFontSize = 13.sp), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(a.GameTitle, a.ConsoleName).joinToString(" · "), color = ShelfTheme.textMuted, fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(a.Date?.drop(11)?.take(5) ?: "", color = ShelfTheme.textMuted, fontSize = 11.sp)
        a.Points?.let { Spacer(Modifier.width(10.dp)); Text("$it pts", color = ShelfTheme.textMuted, fontSize = 11.sp) }
    }
}

/** The RetroAchievements profile on its own screen (opened from the avatar). */
@Composable
internal fun ProfileScreen(
    connected: Boolean,
    profile: com.shelf.launcher.data.RaUserProfile?,
    stats: com.shelf.launcher.data.RaProfileStats,
    completion: Collection<RaCompletionEntry>,
    recent: List<RaRecentAchievement>,
    onOpen: () -> Unit,
    onOpenGame: (Int) -> Unit,
    onOpenRecap: (com.shelf.launcher.data.YearRecap.Period) -> Unit,
    onDismiss: () -> Unit
) {
    LaunchedEffect(Unit) { onOpen() }
    ShelfDialog(
        icon = Icons.Filled.Person, title = "Profile", subtitle = profile?.User,
        onDismiss = onDismiss, widthFraction = 0.92f, maxContentHeightFraction = 0.74f
    ) {
        ProfileContent(connected, profile, stats, completion, recent, onOpenGame = onOpenGame)
        if (connected) RaWeeklyCard(onOpenGame)
        if (connected) RaFriendsCard()
        Spacer(Modifier.height(12.dp))
        RecapsCard(onOpenRecap)
    }
}

/** Play statistics and recaps on their own screen. */
@Composable
internal fun StatsScreen(
    games: List<Game>,
    playtime: Map<String, Long>,
    completion: Collection<RaCompletionEntry>,
    appearance: com.shelf.launcher.data.AppearanceSettings,
    onAppearanceChange: (com.shelf.launcher.data.AppearanceSettings) -> Unit,
    statuses: Map<String, com.shelf.launcher.data.StatusEntry>,
    onOpenRecap: (com.shelf.launcher.data.YearRecap.Period) -> Unit,
    onDismiss: () -> Unit
) {
    ShelfDialog(
        icon = Icons.Filled.BarChart, title = "Stats", subtitle = "How much you play",
        onDismiss = onDismiss, widthFraction = 0.92f, maxContentHeightFraction = 0.74f
    ) {
        RecapsCard(onOpenRecap)
        Spacer(Modifier.height(12.dp))
        StatsContent(
            games, playtime, completion,
            weeklyRecap = appearance.weeklyRecap,
            onWeeklyRecap = { onAppearanceChange(appearance.copy(weeklyRecap = it)) },
            statuses = if (appearance.playStatus) statuses else emptyMap()
        )
    }
}

/** Appearance › Achievements, the newer options, grouped: badge view, motion, unlocks, the backdrop. */
@Composable
internal fun AchievementLookSettings() {
    val F = com.shelf.launcher.data.Features
    CollapsibleSection("Achievement look & unlocks") {
        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(Modifier.weight(1f)) { MutedNote("Achievements follow your app theme's colours everywhere they appear.") }
            VoltHelpButton(VoltTopic.RETROACHIEVEMENTS)
        }
        FoldGroup("Badge view", summary = "Text under the badges", startOpen = true) {
            ShelfCard {
                MutedNote("What shows under each badge. Hidden text makes the badges bigger. Only the badge wall — not the full details or the post-session card.")
                Spacer(Modifier.height(8.dp))
                ChipFlow {
                    Chip("Name", accent = F.badgeName.state.value) { F.badgeName.toggle() }
                    Chip("Percentage", accent = F.badgePercent.state.value) { F.badgePercent.toggle() }
                    Chip("Count / points", accent = F.badgeCount.state.value) { F.badgeCount.toggle() }
                }
            }
        }
        FoldGroup("Motion", summary = F.achievementIntensity.state.value.label) {
            ShelfCard {
                SectionLabel("Intensity")
                MutedNote("How much the badges glow and move. Low is also the battery-friendly setting.")
                ChipFlow {
                    com.shelf.launcher.data.Features.Intensity.values().forEach { i ->
                        Chip(i.label, accent = F.achievementIntensity.state.value == i) { F.achievementIntensity.value = i }
                    }
                }
            }
        }
        FoldGroup("Unlock animations", summary = if (F.unlockAnimation.state.value) "On" else "Off") {
            SettingsGrid {
                item { m ->
                    SwitchRow(
                        icon = Icons.Filled.EmojiEvents, title = "Unlock animation",
                        subtitle = "New unlocks charge their capsule, pop out, hover and lock back in — once each, the first time you open the game's achievements after earning them. Old unlocks never replay.",
                        checked = F.unlockAnimation.state.value, onChange = { F.unlockAnimation.value = it }, modifier = m
                    )
                }
                item { m ->
                    SwitchRow(
                        icon = Icons.Filled.EmojiEvents, title = "Animate only new unlocks",
                        subtitle = "Skips the fly-in of the whole wall when a list opens; only new unlocks move.",
                        checked = F.animateOnlyNew.state.value, onChange = { F.animateOnlyNew.value = it }, modifier = m
                    )
                }
                item { m ->
                    SwitchRow(
                        icon = Icons.Filled.EmojiEvents, title = "100% celebration",
                        subtitle = "When a game reaches 100%, its badges assemble into a wall and it's stamped Mastered.",
                        checked = F.completionCelebration.state.value, onChange = { F.completionCelebration.value = it }, modifier = m
                    )
                }
                item { m -> SessionSummaryToggle(m) }
            }
        }
        FoldGroup("In the backdrop", summary = F.backdropAchievements.state.value.size.let { if (it == 0) "Off" else "$it on" }) {
            ShelfCard {
                MutedNote("Subtle achievement touches inside your normal home background (any theme): your latest badges, the game's progress, a few trophy sparkles.")
                Spacer(Modifier.height(8.dp))
                ChipFlow {
                    com.shelf.launcher.data.Features.BackdropAchievement.values().forEach { b ->
                        Chip(b.label, accent = b.name in F.backdropAchievements.state.value) { F.backdropAchievements.toggle(b.name) }
                    }
                }
            }
        }
    }
}
