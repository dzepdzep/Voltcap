package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.RaGameProgress

/**
 * Living Library (Settings › Library, off by default): the library reflects how you play it — quietly.
 * Recently played games look a touch more alive, fresh unlocks get a small gold mark, games near completion
 * show their progress, games untouched for six months+ sit a little calmer. It only ever draws on top of
 * a card: it never hides, moves, re-sorts or dims a game you're on, and never-played games are left alone,
 * so the whole library stays just as easy to explore. Explore mode switches it all off for the moment.
 */
data class LivingLook(
    /** 0..1: how much calmer (a light scrim); only for long-unused games, never while focused. */
    val calm: Float = 0f,
    /** Played in the last week: a thin accent line under the art. */
    val alive: Boolean = false,
    /** Unlocks not looked at yet: a small gold mark. */
    val freshUnlock: Boolean = false,
    /** Progress to show as a thin bar (null = none). */
    val progress: Float? = null,
    /** Near completion: the bar in gold. */
    val near: Boolean = false
) {
    val isPlain get() = calm == 0f && !alive && !freshUnlock && progress == null
}

private val PLAIN = LivingLook()

/** The look for one game right now (plain when Living Library is off or Explore is on). */
internal fun livingLook(
    lastPlayed: Long?,
    progress: RaGameProgress?,
    freshUnlock: Boolean,
    now: Long = System.currentTimeMillis(),
    /** The game (Game.stableKey): a state you set yourself (Completed, Mastered, 100 %) ends its "unfinished" highlight. */
    gameKey: String? = null
): LivingLook {
    if (!Features.livingLibrary.state.value || Features.exploreMode.value) return PLAIN
    val days = lastPlayed?.let { ((now - it) / 86_400_000L).toInt() }
    val activity = Features.llActivityVisuals.state.value
    val total = progress?.NumPossibleAchievements ?: 0
    val earned = progress?.NumAchieved ?: 0
    val started = total > 0 && earned in 1 until total
    val near = Features.llHighlightNear.state.value && started && (earned * 100 / total >= 75 || total - earned <= 5)
    val done = com.shelf.launcher.data.GameLooks.of(gameKey).state.let {
        it == com.shelf.launcher.data.GameState.COMPLETED || it == com.shelf.launcher.data.GameState.MASTERED || it == com.shelf.launcher.data.GameState.PERFECT
    }
    val unfinished = Features.llHighlightUnfinished.state.value && started && !done
    return LivingLook(
        // Played before, then left for six months or more. Never-played games are never subdued.
        calm = if (Features.llSubdueInactive.state.value && days != null && days >= 182) 1f else 0f,
        alive = activity && Features.llHighlightRecent.state.value && days != null && days <= 7,
        freshUnlock = activity && freshUnlock,
        progress = if (near || unfinished) earned.toFloat() / total else null,
        near = near
    )
}

/**
 * Draws [look] over a card ([cornerDp] = the card's corners). Focused cards are never calmed. Nothing
 * animates: it's a few shapes drawn with the card.
 */
internal fun Modifier.livingLibrary(look: LivingLook, focused: Boolean, accentNow: () -> Color, cornerDp: Float): Modifier =
    if (look.isPlain) this else this.drawWithContent {
        drawContent()
        // (QA pass 2, D22: the accent is read here, while drawing — its glide no longer re-composes every card.)
        val accent = accentNow()
        val r = CornerRadius(cornerDp.dp.toPx())
        if (look.calm > 0f && !focused) drawRoundRect(Color.Black.copy(alpha = 0.24f * look.calm), cornerRadius = r)
        val h = size.height
        val w = size.width
        val bar = 3.dp.toPx()
        val inset = 6.dp.toPx()
        look.progress?.let { p ->
            val y = h - inset - bar
            drawRoundRect(Color.Black.copy(alpha = 0.45f), Offset(inset, y), Size(w - 2 * inset, bar), CornerRadius(bar / 2))
            drawRoundRect(
                if (look.near) com.shelf.launcher.ui.theme.Ds.gold else accent,
                Offset(inset, y), Size((w - 2 * inset) * p.coerceIn(0.03f, 1f), bar), CornerRadius(bar / 2)
            )
        }
        if (look.alive) {
            val y = h - (if (look.progress != null) inset + bar + 3.dp.toPx() else inset) - 2.dp.toPx()
            drawRoundRect(accent.copy(alpha = 0.9f), Offset(w * 0.3f, y), Size(w * 0.4f, 2.dp.toPx()), CornerRadius(1.dp.toPx()))
        }
        if (look.freshUnlock) {
            val c = Offset(inset + 4.dp.toPx(), inset + 4.dp.toPx())
            drawCircle(
                Brush.radialGradient(listOf(com.shelf.launcher.ui.theme.Ds.gold.copy(alpha = 0.6f), Color.Transparent), c, 9.dp.toPx()),
                9.dp.toPx(), c
            )
            drawCircle(com.shelf.launcher.ui.theme.Ds.gold, 3.5.dp.toPx(), c)
        }
    }

/** Asks the home screen to open Rediscover (Settings, L1); a counter it watches, like [SaveExplorerHook]. */
internal object RediscoverHook {
    val requests = androidx.compose.runtime.mutableIntStateOf(0)
    fun request() { requests.intValue++ }
}

/** Settings › Library: Smart Continue, Living Library (with its parts), Explore and Rediscover. */
@Composable
internal fun LivingLibrarySettings(onOpenRediscover: () -> Unit = { RediscoverHook.request() }) {
    val F = Features
    SettingsHeading("Smart Continue & Living Library")
    ShelfCard {
        SwitchRow(
            Icons.Filled.PlayCircle, "Smart Continue",
            "A \"Smart Continue\" category: what you're playing now, games close to completion, ones with a few achievements left, and what you were really into before.",
            F.smartContinue.state.value, onChange = { F.smartContinue.value = it }
        )
        Spacer(Modifier.height(8.dp))
        SwitchRow(
            Icons.Filled.AutoAwesome, "Living Library",
            "Games reflect how you play them, quietly: recent ones a touch more alive, fresh unlocks marked, near-complete ones show their progress. It never hides, moves or re-sorts anything.",
            F.livingLibrary.state.value, onChange = { F.livingLibrary.value = it }
        )
        if (F.livingLibrary.state.value) {
            Spacer(Modifier.height(8.dp))
            ChipFlow {
                listOf(
                    "Activity-based visuals" to F.llActivityVisuals,
                    "Highlight recent games" to F.llHighlightRecent,
                    "Highlight unfinished games" to F.llHighlightUnfinished,
                    "Highlight near-completion" to F.llHighlightNear,
                    "Subdue inactive games" to F.llSubdueInactive
                ).forEach { (label, p) ->
                    Chip((if (p.state.value) "✓ " else "") + label, accent = p.state.value) { p.value = !p.value }
                }
            }
            MutedNote("Sorting never follows activity. \"Subdue inactive\" only calms games you played before but not in 6+ months (never-played ones stay as they are), and never the one you're on.")
        }
        Spacer(Modifier.height(10.dp))
        Column {
            SwitchRow(
                Icons.Filled.Explore, "Explore mode (for now)",
                "Shows the whole collection equally: no highlights, no calmer games. Your library and settings stay as they are; it ends when Voltcap restarts. Also on L1 › Explore.",
                F.exploreMode.value, onChange = { F.exploreMode.value = it }
            )
            Spacer(Modifier.height(8.dp))
            ActionCard(Icons.Filled.History, "Rediscover", "Games you haven't played in 6+ months, oldest first — opened only from here or L1, never mixed into your rows.", onOpenRediscover)
        }
    }
    Spacer(Modifier.height(10.dp))
    ShelfCard {
        SwitchRow(
            Icons.Filled.AutoAwesome, "Game states",
            "A small mark on covers: NEW (just added, never played), PLAYING, ON HOLD (marked Playing but untouched for a month), " +
                "COMPLETED (beaten or marked Finished), MASTERED and 100%. Set one yourself in Game settings › Look for this game.",
            F.gameStates.state.value, onChange = { F.gameStates.value = it }
        )
        Spacer(Modifier.height(8.dp))
        SwitchRow(
            Icons.Filled.AutoAwesome, "Game ambience",
            "Particles, rain, snow, embers, CRT noise or art lighting over the background for games you gave one " +
                "(Game settings › Look for this game). Never plays sound.",
            F.gameAmbience.state.value, onChange = { F.gameAmbience.value = it }
        )
    }
}
