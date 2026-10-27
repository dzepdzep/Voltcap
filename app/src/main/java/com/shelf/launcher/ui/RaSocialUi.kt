package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.RaCredentialsStore
import com.shelf.launcher.data.RaSocial
import com.shelf.launcher.ui.theme.ShelfTheme

/**
 * Settings › Profile: the people you follow on RetroAchievements — loaded straight away (kept for ten
 * minutes), with their points, what they're playing right now and the last game they mastered.
 */
@Composable
internal fun RaFriendsCard() {
    val context = LocalContext.current
    val creds = remember { RaCredentialsStore(context).load() } ?: return
    SettingsHeading("Friends")
    ShelfCard {
        // null = loading; a failed load is told apart from an empty list.
        var failed by remember { mutableStateOf(false) }
        val friends by produceState<List<RaSocial.Friend>?>(null) {
            val got = RaSocial.friendsCached(creds)
            failed = got == null
            value = got ?: emptyList()
        }
        val f = friends
        when {
            f == null -> MutedNote("Loading friends…")
            failed -> MutedNote("Couldn't reach RetroAchievements just now — your friends show up here when you're online.")
            f.isEmpty() -> MutedNote("You don't follow anyone yet — follow people on retroachievements.org and they show up here.")
            else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                f.sortedByDescending { it.points }.forEach { fr ->
                    Row(Modifier.shelfFocusable().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(fr.avatar, null, Modifier.size(38.dp).clip(CircleShape))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(fr.user + if (fr.followsYou) "  ·  follows you" else "", color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            fr.lastAward?.let { aw ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                                    if (aw.icon != null) {
                                        AsyncImage(aw.icon, null, Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)))
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Text(
                                        "${aw.kind}: ${aw.title}" + if (aw.console.isNotBlank()) " · ${aw.console}" else "",
                                        color = if (aw.kind == "Mastered") Color(0xFFF5C542) else ShelfTheme.colors.textPrimary.copy(alpha = 0.8f),
                                        fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp), maxLines = 2, overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            fr.playing?.takeIf { it.isNotBlank() }?.let { Text(it, color = ShelfTheme.textMuted, fontSize = 11.sp, maxLines = 2) }
                        }
                        Text("${fr.points} pts", color = ShelfTheme.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Settings › Profile: RetroAchievements' Achievement of the Week — its badge, the game and console, how
 * many players have it and whether you do. A (or tap) opens that game's achievements when it's in your
 * library. (RetroAchievements has no public feed for its monthly event games, so the site link covers those.)
 */
@Composable
internal fun RaWeeklyCard(onOpenGame: (Int) -> Unit) {
    val context = LocalContext.current
    val creds = remember { RaCredentialsStore(context).load() } ?: return
    val pick by produceState<RaSocial.WeeklyPick?>(null) { value = RaSocial.achievementOfTheWeek(creds) }
    val w = pick ?: return
    SettingsHeading("This week on RetroAchievements")
    ShelfCard {
        Row(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .then(if (w.gameId != null) Modifier.shelfClickable(RoundedCornerShape(12.dp)) { onOpenGame(w.gameId) } else Modifier.shelfFocusable())
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.layout.Box {
                AsyncImage(w.badge ?: w.gameIcon, null, Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)))
                if (w.gameIcon != null && w.badge != null) {
                    AsyncImage(w.gameIcon, null, Modifier.size(26.dp).align(Alignment.BottomEnd).clip(RoundedCornerShape(6.dp)))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("ACHIEVEMENT OF THE WEEK", color = ShelfTheme.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(w.title, color = ShelfTheme.colors.textPrimary, fontSize = 15.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(15.sp), maxFontSize = 15.sp), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (w.description.isNotBlank()) Text(w.description, color = ShelfTheme.textMuted, fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(w.gameTitle, w.console).filter { it.isNotBlank() }.joinToString(" · "),
                    color = ShelfTheme.colors.textPrimary.copy(alpha = 0.8f), fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp), maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${w.unlocks} of ${w.players} players have it" + if (w.youHaveIt) "  ·  you do!" else "",
                    color = if (w.youHaveIt) Color(0xFFF5C542) else ShelfTheme.textMuted, fontSize = 11.sp
                )
            }
            Text("${w.points} pts", color = ShelfTheme.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        ChipFlow {
            Chip("Events & Game of the Month on the site", accent = false) {
                runCatching {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://retroachievements.org/")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
    }
}

/** A game's RetroAchievements leaderboards: top score, your rank; A on one shows its top 25. */
@Composable
internal fun RaLeaderboardsDialog(game: Game, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val creds = remember { RaCredentialsStore(context).load() }
    var failed by remember { mutableStateOf(false) }
    val boards by produceState<List<RaSocial.Leaderboard>?>(null, game.raGameId) {
        val id = game.raGameId
        val got = if (creds == null || id == null) emptyList() else RaSocial.leaderboards(creds, id)
        failed = got == null
        value = got ?: emptyList()
    }
    var open by remember { mutableStateOf<Int?>(null) }
    ShelfDialog(icon = Icons.Filled.Leaderboard, title = "Leaderboards", subtitle = gameDisplayName(game.name), onDismiss = onDismiss, widthFraction = 0.8f) {
        val b = boards
        when {
            b == null -> MutedNote("Loading…")
            failed -> MutedNote("Couldn't reach RetroAchievements just now. Reopen this to try again.")
            b.isEmpty() -> MutedNote("This game has no leaderboards.")
            else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                b.forEach { lb ->
                    MenuRow(
                        Icons.Filled.Leaderboard, lb.title,
                        listOfNotNull(
                            lb.description.takeIf { it.isNotBlank() },
                            lb.top?.let { "Top: $it (${lb.topUser})" },
                            lb.myRank?.let { "You: #$it · ${lb.myScore}" }
                        ).joinToString("\n")
                    ) { open = if (open == lb.id) null else lb.id }
                    if (open == lb.id && creds != null) {
                        val entries by produceKeyed<List<RaSocial.Entry>?>(null, lb.id, lb.id) { value = RaSocial.entries(creds, lb.id) }
                        val e = entries
                        if (e == null) MutedNote("Loading…")
                        else Column(Modifier.padding(start = 16.dp)) {
                            e.forEach { en ->
                                Text(
                                    "#${en.rank}  ${en.user}  ·  ${en.score}",
                                    color = if (en.user.equals(creds.username, true)) ShelfTheme.accent else ShelfTheme.colors.textPrimary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
