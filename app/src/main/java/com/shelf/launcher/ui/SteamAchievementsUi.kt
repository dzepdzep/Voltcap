package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.SteamAchievements
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.launch

/**
 * Your picture (status bar, Profile): RetroAchievements' [raUrl] or, when chosen under Accounts › Steam, your
 * Steam profile picture (RetroAchievements' when Steam has none saved).
 */
@Composable
internal fun profilePictureUrl(raUrl: String?): String? {
    val steam = com.shelf.launcher.data.SteamAchievements.avatarUrl.value
    // (Update 332: the playing profile's picture — its own, Steam's, its RetroAchievements one, or none: its initial.)
    val context = LocalContext.current
    com.shelf.launcher.data.Profiles.ensure(context)
    val p = com.shelf.launcher.data.Profiles.active()
    val stamp = com.shelf.launcher.data.Profiles.pictureStamp.value
    when (p?.picture) {
        com.shelf.launcher.data.Profiles.Picture.OWN ->
            com.shelf.launcher.data.Profiles.pictureFile(context, p.id).takeIf { it.isFile }?.let { return "file://${it.absolutePath}#$stamp" }
        com.shelf.launcher.data.Profiles.Picture.STEAM -> return steam ?: raUrl
        com.shelf.launcher.data.Profiles.Picture.INITIAL -> return null
        else -> Unit
    }
    val useSteam = com.shelf.launcher.data.Features.avatarSource.state.value == com.shelf.launcher.data.Features.Avatar.STEAM
    return if (useSteam && steam != null) steam else raUrl
}

/** Why a Steam Web API key helps (shown where it can be added). */
private const val STEAM_KEY_WHY =
    "Optional. With a key, new unlocks show up faster (Steam's public pages can be a few minutes behind) and " +
        "your recently played games are read more reliably. Get one free at steamcommunity.com/dev/apikey — any domain name works."

/** Settings › Accounts: Steam achievements — just your public profile, or with a Web API key as well. */
@Composable
internal fun SteamAccountCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var account by remember { mutableStateOf(SteamAchievements.account(context)) }
    SteamAccountState.connected = account != null
    var key by remember { mutableStateOf("") }
    var profile by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var withKey by remember { mutableStateOf(false) }
    fun connect(k: String, p: String) {
        busy = true
        scope.launch {
            val err = SteamAchievements.connect(context, k, p)
            busy = false
            if (err == null) { account = SteamAchievements.account(context); note = null; key = ""; withKey = false } else note = err
        }
    }
    SettingsHeading("Steam achievements")
    ShelfCard {
        val a = account
        if (a != null) {
            MutedNote(
                "Connected (SteamID ${a.steamId}" + (if (a.keyless) ", through your public profile" else ", with your Web API key") +
                    "). Steam games show their achievements in the details window and the Trophy vault."
            )
            Spacer(Modifier.height(8.dp))
            if (a.keyless && withKey) {
                MutedNote(STEAM_KEY_WHY)
                Spacer(Modifier.height(6.dp))
                ShelfTextField(key, { key = it.trim() }, "Web API key", password = true)
                Spacer(Modifier.height(8.dp))
            }
            // Which picture is yours in the status bar and on the Profile page.
            val pic = com.shelf.launcher.data.Features.avatarSource.state.value
            MutedNote("Profile picture (status bar and Profile):")
            Spacer(Modifier.height(4.dp))
            ChipFlow {
                com.shelf.launcher.data.Features.Avatar.values().forEach { opt ->
                    Chip(opt.label, accent = pic == opt) {
                        com.shelf.launcher.data.Features.avatarSource.value = opt
                        if (opt == com.shelf.launcher.data.Features.Avatar.STEAM) scope.launch { SteamAchievements.refreshAvatar(context) }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            note?.let { MutedNote(it); Spacer(Modifier.height(6.dp)) }
            ChipFlow {
                if (a.keyless) {
                    if (!withKey) Chip("Add a Web API key (optional)", accent = false) { withKey = true; note = null }
                    else Chip(if (busy) "Checking…" else "Save key", enabled = !busy && key.isNotBlank()) { connect(key, a.steamId) }
                } else {
                    Chip(if (busy) "Checking…" else "Remove the key", accent = false, enabled = !busy) { connect("", a.steamId) }
                }
                Chip("Disconnect", accent = false) { SteamAchievements.clear(context); account = null; note = null }
            }
        } else {
            MutedNote(
                "No key needed: in Steam › Edit profile › Privacy settings, set \"My profile\" and \"Game details\" to Public, " +
                    "then enter your profile name, link or SteamID64."
            )
            Spacer(Modifier.height(8.dp))
            ShelfTextField(profile, { profile = it }, "Profile name, link or SteamID64")
            Spacer(Modifier.height(8.dp))
            if (withKey) {
                MutedNote(STEAM_KEY_WHY)
                Spacer(Modifier.height(6.dp))
                ShelfTextField(key, { key = it.trim() }, "Web API key", password = true)
                Spacer(Modifier.height(8.dp))
            }
            note?.let { MutedNote(it); Spacer(Modifier.height(6.dp)) }
            ChipFlow {
                Chip(if (busy) "Checking…" else "Connect", enabled = !busy && profile.isNotBlank()) { connect(if (withKey) key else "", profile) }
                if (!withKey) Chip("Use a Web API key (optional)", accent = false) { withKey = true }
                else Chip("Without a key", accent = false) { withKey = false; key = "" }
            }
        }
    }
}

/** A Steam game's achievements, unlocked first. */
@Composable
internal fun SteamAchievementsDialog(game: Game, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val list by produceKeyed<List<SteamAchievements.Achievement>?>(null, game.steamAppId, game.steamAppId) {
        value = game.steamAppId?.let { SteamAchievements.forGame(context, it) } ?: emptyList()
    }
    val l = list
    var wall by remember { mutableStateOf(com.shelf.launcher.data.AchievementLook.badges.value) }
    var open by remember { mutableStateOf<String?>(null) }
    ShelfDialog(
        icon = Icons.Filled.EmojiEvents,
        title = "Steam achievements",
        subtitle = gameDisplayName(game.name) + (l?.let { " · " + com.shelf.launcher.data.achievementProgress(it.count { a -> a.achieved }, it.size) } ?: ""),
        onDismiss = onDismiss,
        widthFraction = 0.8f
    ) {
        when {
            l == null -> MutedNote("Loading…")
            l.isEmpty() -> MutedNote("Steam has no achievements for this game, or your profile's game details aren't public.")
            else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              ChipFlow {
                  Chip("Badges", accent = wall) { wall = true }
                  Chip("List", accent = !wall) { wall = false }
              }
              if (wall) {
                val dateFmt = remember { java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM) }
                BadgeWall(
                    items = l.map { a ->
                        BadgeItem(
                            key = a.id, image = if (a.achieved) a.icon else a.iconGray ?: a.icon, title = a.name,
                            percent = a.percent, unlocked = a.achieved,
                            note = if (a.achieved && a.unlockedAt > 0) dateFmt.format(java.util.Date(a.unlockedAt)) else if (a.achieved) "Unlocked" else "Locked"
                        )
                    },
                    animKey = game.stableKey,
                    selected = open,
                    onClick = { item -> open = if (open == item.key) null else item.key as String },
                    below = { item ->
                        val a = l.first { it.id == item.key }
                        ShelfCard {
                            Text(a.name, color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            if (a.description.isNotBlank()) Text(a.description, color = ShelfTheme.textMuted, fontSize = 12.sp)
                            Text(
                                listOfNotNull(
                                    if (a.achieved && a.unlockedAt > 0) "Unlocked " + dateFmt.format(java.util.Date(a.unlockedAt)) else if (a.achieved) "Unlocked" else "Locked",
                                    a.percent?.let { "${if (it < 1f) "<1" else it.toInt().toString()} % of players have it" }
                                ).joinToString("  ·  "),
                                color = ShelfTheme.accent, fontSize = 11.sp
                            )
                        }
                    }
                )
              } else
                l.forEach { a ->
                    Row(
                        Modifier.shelfFocusable().alpha(if (a.achieved) 1f else 0.6f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = if (a.achieved) a.icon else a.iconGray ?: a.icon, contentDescription = null,
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.name, color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            if (a.description.isNotBlank()) Text(a.description, color = ShelfTheme.textMuted, fontSize = 11.sp)
                        }
                        if (a.achieved && a.unlockedAt > 0) {
                            Text(
                                java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(a.unlockedAt)),
                                color = ShelfTheme.textMuted, fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Whether a Steam account is connected (read once per process; updated by the card). */
internal object SteamAccountState {
    @Volatile var connected: Boolean = false
    fun init(context: android.content.Context) { connected = SteamAchievements.account(context) != null }
}
