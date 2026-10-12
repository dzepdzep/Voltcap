package com.shelf.launcher.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.RaConnectionState
import com.shelf.launcher.data.RaBadges
import com.shelf.launcher.data.RaRecentAchievement
import com.shelf.launcher.ui.theme.ShelfTheme

@Composable
fun RetroAchievementsDialog(
    state: RaConnectionState,
    username: String?,
    statusMessage: String?,
    isKeyEncrypted: Boolean,
    recentAchievements: List<RaRecentAchievement>,
    isRecentLoading: Boolean,
    onConnect: (username: String, apiKey: String) -> Unit,
    onDisconnect: () -> Unit,
    onRefreshRecent: () -> Unit,
    onDismiss: () -> Unit,
    /** Both accounts' user names (null: empty slot), the one playing, and switching / adding / removing. */
    accounts: List<String?> = emptyList(),
    activeSlot: Int = 0,
    onSwitchAccount: (Int) -> Unit = {},
    onAddAccount: (username: String, apiKey: String) -> Unit = { _, _ -> },
    onRemoveAccount: (Int) -> Unit = {}
) {
    com.shelf.launcher.Breadcrumbs.mark("RetroAchievementsDialog")
    var usernameField by remember { mutableStateOf(username ?: "") }
    var apiKeyField by remember { mutableStateOf("") }
    val connected = state == RaConnectionState.ON

    ShelfDialog(
        icon = Icons.Filled.EmojiEvents,
        title = "RetroAchievements",
        subtitle = "Achievement progress for your library.",
        onDismiss = onDismiss,
        footer = {
            Chip("Back", accent = false, onClick = onDismiss)
            if (connected) {
                Chip("Disconnect", accent = false, onClick = onDisconnect)
            } else {
                Chip(
                    if (state == RaConnectionState.CONNECTING) "Connecting…" else "Connect",
                    enabled = usernameField.isNotBlank() && apiKeyField.isNotBlank() &&
                        state != RaConnectionState.CONNECTING,
                    onClick = { onConnect(usernameField.trim(), apiKeyField.trim()) }
                )
            }
        }
    ) {
        if (connected) {
            ConnectedCard(
                title = "Signed in as $username",
                detail = "Login is " + (if (isKeyEncrypted) "encrypted" else "saved (unencrypted fallback)") +
                    ". Achievements still unlock inside the emulator itself (e.g. RetroArch's own " +
                    "RetroAchievements login) — Voltcap only shows your progress."
            )
            Spacer(Modifier.height(12.dp))
            AccountsCard(accounts, activeSlot, onSwitchAccount, onAddAccount, onRemoveAccount)
            Spacer(Modifier.height(12.dp))
            ShelfCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        SectionLabel("Recently unlocked")
                        MutedNote("Last 24 hours, across all games.")
                    }
                    Chip(
                        if (isRecentLoading) "Loading…" else "Refresh",
                        accent = false,
                        enabled = !isRecentLoading,
                        onClick = onRefreshRecent
                    )
                }
                Spacer(Modifier.height(8.dp))
                when {
                    isRecentLoading && recentAchievements.isEmpty() -> EmptyState("Loading…")
                    recentAchievements.isEmpty() -> EmptyState("Nothing unlocked in the last 24 hours.")
                    else -> recentAchievements.forEach { RecentAchievementRow(it) }
                }
            }
        } else {
            if (accounts.any { it != null }) {
                AccountsCard(accounts, activeSlot, onSwitchAccount, onAddAccount, onRemoveAccount)
                Spacer(Modifier.height(12.dp))
            }
            MutedNote(
                "Get your web API key from the \"Keys\" section of your RetroAchievements " +
                    "control panel. Games are matched automatically once you're connected."
            )
            Spacer(Modifier.height(14.dp))
            ShelfTextField(usernameField, { usernameField = it }, "Username")
            Spacer(Modifier.height(8.dp))
            ShelfTextField(apiKeyField, { apiKeyField = it }, "Web API key", password = true)
        }
        statusMessage?.let {
            Spacer(Modifier.height(12.dp))
            MutedNote(it)
        }
    }
}

@Composable
private fun RecentAchievementRow(achievement: RaRecentAchievement) {
    com.shelf.launcher.Breadcrumbs.mark("RecentAchievementRow")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .shelfFocusable()
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val badgeUrl = RaBadges.url(achievement.BadgeName)
        if (badgeUrl != null) {
            AsyncImage(
                model = badgeUrl,
                contentDescription = null,
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))
            )
        } else {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ShelfTheme.accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.EmojiEvents, null, tint = ShelfTheme.accent, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                achievement.Title ?: "Unknown achievement",
                color = ShelfTheme.colors.textPrimary,
                fontSize = 13.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(13.sp), maxFontSize = 13.sp),
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                achievement.GameTitle ?: "",
                color = ShelfTheme.textMuted,
                fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        achievement.Points?.let {
            Spacer(Modifier.width(8.dp))
            Text("$it pts", color = ShelfTheme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** The two RetroAchievements accounts: who is playing, add a second one, remove one, ask at start-up. */
@Composable
private fun AccountsCard(
    accounts: List<String?>,
    activeSlot: Int,
    onSwitch: (Int) -> Unit,
    onAdd: (String, String) -> Unit,
    onRemove: (Int) -> Unit
) {
    var user by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    val names = accounts.filterNotNull()
    ShelfCard {
        SectionLabel("Accounts")
        MutedNote("Two people on one handheld: each has their own RetroAchievements account. Whoever is playing sees their own progress and unlocks.")
        Spacer(Modifier.height(8.dp))
        accounts.forEachIndexed { slot, name ->
            if (name == null) return@forEachIndexed
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name, color = ShelfTheme.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (slot == activeSlot) "Playing now" else "Saved", color = if (slot == activeSlot) ShelfTheme.accent else ShelfTheme.textMuted, fontSize = 12.sp)
                }
                if (slot != activeSlot) Chip("Play as", onClick = { onSwitch(slot) })
                Spacer(Modifier.width(6.dp))
                Chip("Remove", accent = false, onClick = { onRemove(slot) })
            }
        }
        if (names.size >= 2) {
            Spacer(Modifier.height(8.dp))
            val ask = com.shelf.launcher.data.Features.whoPlays.state.value
            Chip(if (ask) "✓ Ask who is playing at start-up" else "Ask who is playing at start-up", accent = ask) {
                com.shelf.launcher.data.Features.whoPlays.value = !ask
            }
        }
        Spacer(Modifier.height(10.dp))
        SectionLabel(if (names.size >= 2) "Replace the second account" else "Add a second account")
        ShelfTextField(user, { user = it }, "Username")
        Spacer(Modifier.height(6.dp))
        ShelfTextField(key, { key = it }, "Web API key", password = true)
        Spacer(Modifier.height(8.dp))
        Chip("Add and play as them", enabled = user.isNotBlank() && key.isNotBlank(), onClick = {
            onAdd(user.trim(), key.trim()); user = ""; key = ""
        })
    }
}
