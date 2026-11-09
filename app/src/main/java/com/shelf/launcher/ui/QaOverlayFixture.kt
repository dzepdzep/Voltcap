package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shelf.launcher.data.RaOverlayData
import com.shelf.launcher.data.perf.PerfConfig

/**
 * Update 496 (QA only, staging): the real achievements overlay ([RaOverlayView]) drawn inside Voltcap with made-up content,
 * so its long titles, wrapping, pinned comments and text sizes can be checked WITHOUT a game:
 *   `--es voltcap_test_raoverlay "open=1,scale=1.3,width=360"` (only with the QA hooks file), `off` to close.
 *   open  = 0 (the small summary over the game) | 1 (the opened panel, the pinned row's comments open)
 *   scale = the text size (a font scale for this view only — the phone's own setting is not touched)
 *   width = the room the overlay gets, in dp (a narrow screen)
 * Nothing is requested from RetroAchievements, nothing pinned or saved; no game is started.
 */
internal object QaOverlayFixture {
    data class Spec(val open: Boolean, val scale: Float, val width: Int)

    val spec = mutableStateOf<Spec?>(null)

    fun parse(s: String): Spec? {
        if (s.isBlank() || s == "off") return null
        val m = s.split(',').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }.toMap()
        return Spec(m["open"] == "1", m["scale"]?.toFloatOrNull()?.coerceIn(0.8f, 2f) ?: 1f, m["width"]?.toIntOrNull()?.coerceIn(240, 1200) ?: 420)
    }

    private val LONG_T = "The Extraordinarily Long Achievement Name That Keeps Going Past Every Reasonable Width (Hardcore)"
    private val LONG_D = "Finish the lighthouse keeper's seventh clockwork island without taking damage, collecting every gear, " +
        "and before the second sunrise — in one sitting, on the hardest difficulty, with the original controls."

    val items = listOf(
        RaOverlayData.Item(1, LONG_T, LONG_D, 25, 3.4f, missable = true),
        RaOverlayData.Item(2, "SuperUltraMegaCompoundWordWithoutAnySpacesThatNeverEnds", "A single word longer than the panel.", 10, 12f, false),
        RaOverlayData.Item(3, "Ünïcödé Ädventure: Überflüssig Lange Titelzeile 完全版", "Unicode, accents and wide characters.", 5, 41f, false),
        RaOverlayData.Item(4, "Short", null, 1, 88f, false),
        RaOverlayData.Item(5, "Already Got This One With A Long Name As Well, To Wrap", LONG_D, 50, 7f, false, earned = "2026-11-01 18:22:10")
    )

    fun state() = RaOverlayData.State(
        gameTitle = "QA fixture · The Extraordinarily Long Chronicle of the Forgotten Lighthouse Keeper and the Seven Clockwork Islands",
        unlocked = 1, total = 5, points = 50, totalPoints = 91,
        missablesLeft = listOf(LONG_T, "Another Missable With A Long Name", "Third One"),
        nextTitle = LONG_T, nextDescription = LONG_D, nextIsMissable = true, nextWhy = "Missable · get it before it's gone",
        lastUnlock = items[4].title, left = items.take(4), unlockedList = listOf(items[4]), gameId = -496,
        all = items, unlockedIds = setOf(5)
    )

    val config = PerfConfig(raDuringGames = true, raNext = true, raMissables = true, raName = true, raDescription = true, raTotals = true,
        raLastUnlock = true, raBadges = false)

    val comments = listOf(
        "QA fixture  ·  2026-11-01" to "A long comment that wraps over several lines: " + LONG_D + " " + LONG_D,
        "Second commenter with a long name  ·  2026-10-30" to "Short one.",
        "Third  ·  2026-10-29" to (1..6).joinToString(" ") { "Paragraph $it of a comment long enough to need its own scrolling box. $LONG_D" }
    )
}

@Composable
internal fun QaOverlayFixtureHost() {
    val sp = QaOverlayFixture.spec.value ?: return
    val base = LocalContext.current
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = { QaOverlayFixture.spec.value = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF101418))) {
            Text("QA fixture · no game running · text ×${sp.scale} · ${sp.width} dp", color = Color(0xFF8A96A3), fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp))
            // A context with its own text size: the phone's font scale stays as it is.
            val ctx = remember(sp.scale) {
                val cfg = android.content.res.Configuration(base.resources.configuration).apply { fontScale = sp.scale }
                android.view.ContextThemeWrapper(base.createConfigurationContext(cfg), base.theme)
            }
            // (Keyed: a new text size or width builds the view again — AndroidView's factory runs once.)
            androidx.compose.runtime.key(sp.scale, sp.width) { AndroidView(
                factory = { RaOverlayView(ctx, scope) {}.apply {
                    fixture = OverlayFixture(pinnedId = 1, comments = QaOverlayFixture.comments, openPinned = true)
                } },
                update = { v ->
                    v.bind(QaOverlayFixture.state(), QaOverlayFixture.config)
                    if (sp.open) v.expand() else v.collapse()
                },
                modifier = Modifier.padding(16.dp).width(sp.width.dp)
            ) }
        }
    }
}
