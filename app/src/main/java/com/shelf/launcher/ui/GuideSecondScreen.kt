package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.guide.GuideVisibility
import com.shelf.launcher.data.guide.SectionVisibility
import com.shelf.launcher.data.guide.SpoilerLevel
import com.shelf.launcher.ui.theme.ShelfTheme

/**
 * Walkthrough page for the second screen: a read-only view of the section the player is in,
 * following the same spoiler rules as the full guide. Touch scrolls; nothing here takes the D-pad.
 */
@Composable
internal fun GuideSecondScreenPage(game: Game?, state: GuideUiState?, onNeedGuide: (Game) -> Unit) {
    LaunchedEffect(game?.stableKey) { game?.let(onNeedGuide) }
    if (game == null) { GuideNote("Pick a game to see its guide."); return }
    val st = state?.takeIf { it.gameKey == game.stableKey }
    val pos = st?.position
    when {
        st == null || (st.loading && pos == null) -> GuideNote("Loading guide…")
        pos == null -> GuideNote(
            if (st.error != null) st.error
            else "No guide chosen for this game yet. Open Guide from the game's Start menu on the main screen to pick one."
        )
        else -> {
            val cur = pos.current
            val spoiler = st.spoiler
            Column(Modifier.fillMaxSize()) {
                Text(pos.guide.title, color = ShelfTheme.textMuted, fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    cur?.title?.ifBlank { "Introduction" } ?: "—",
                    color = ShelfTheme.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold
                )
                Text(
                    guideConfidenceLine(pos),
                    color = if (pos.keptPrevious) Color(0xFFE0A030) else ShelfTheme.accent,
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    val vis = if (cur == null) SectionVisibility.HIDDEN
                    else GuideVisibility.of(spoiler, cur.order, cur.order, pos.furthest?.order ?: cur.order)
                    if (cur != null && vis == SectionVisibility.FULL) {
                        Text(cur.text.ifBlank { "(This section has no text.)" }, color = ShelfTheme.colors.textPrimary, fontSize = 15.sp, lineHeight = 22.sp)
                    } else {
                        GuideNote("Hidden by your spoiler setting.")
                    }
                    val report = st.missableReport
                    if (st.missablesMode && report != null) {
                        val upcoming = report.missablesCurrent + report.missablesUpcoming
                        if (upcoming.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            SectionLabel("Missables here / coming up")
                            upcoming.forEach { h ->
                                Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    MissableIcon()
                                    Spacer(Modifier.width(6.dp))
                                    Text(h.achievement.title ?: "Achievement", color = ShelfTheme.colors.textPrimary, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
                if (spoiler != SpoilerLevel.NONE) {
                    Text("Spoiler level: ${spoiler.label}", color = ShelfTheme.textMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun GuideNote(text: String) {
    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.Center) {
        Text(text, color = ShelfTheme.textMuted, fontSize = 13.sp, lineHeight = 18.sp)
    }
}
