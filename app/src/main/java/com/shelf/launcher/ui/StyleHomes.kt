package com.shelf.launcher.ui

import androidx.compose.ui.graphics.asAndroidBitmap

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.roundToInt
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import com.shelf.launcher.LauncherUiState
import com.shelf.launcher.SpecialFilter
import com.shelf.launcher.data.ArtPick
import com.shelf.launcher.data.CardArtType
import com.shelf.launcher.data.CategorySettings
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.FocusEffect
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.GameArt
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.cardArtFor
import com.shelf.launcher.ui.theme.ShelfTheme

/*
 * The style homes: six whole-screen home layouts with their own menus (Appearance › Game row › Home layout):
 * Dashboard, Live tiles, Side menu, Cross menu, Spotlight and Icon bar. Each one is its own file
 * (StyleDashboard.kt, StyleLiveTiles.kt, StyleSideMenu.kt, StyleCrossMenu.kt, StyleSpotlight.kt,
 * StyleIconBar.kt); this file holds what they share: where the D-pad is ([StyleNav]) and its keys
 * ([styleHomeKey]), the data they draw from ([StyleCtx]), the card artwork, the focus look and the panels.
 * Their options live in StyleHomeSettings.kt. Everything else (details window, L2 / R2, R1 / L1, the status
 * bar, the music player, the second screen) works as on the other layouts.
 */

/** Which part of a style home the D-pad is on, and what each part's buttons do (set by the layout as it draws). */
internal object StyleNav {
    enum class Zone { GAMES, SIDE, BAR, TOP }

    var zone by mutableStateOf(Zone.GAMES)
    var index by mutableIntStateOf(0)

    /** Each zone's buttons as the layout drew them last (their actions, in order). */
    @Volatile var side: List<() -> Unit> = emptyList()
    @Volatile var bar: List<() -> Unit> = emptyList()
    @Volatile var top: List<() -> Unit> = emptyList()

    /** Live tiles: games per page (5, 3 or 6), for ◀ ▶ ▲ ▼. */
    @Volatile var livePage = 5

    /** The button of the top zone to start on (the tab of the category you're in), or -1 for where it was left. */
    @Volatile var topStart = -1

    /** Where each zone was left, so coming back lands there. */
    private val last = IntArray(Zone.values().size)

    // Update 130: when the game you're on last changed (the press that moved it), so ◀ on the first game only goes into
    // a menu on a new press, not on the stick's next nudge or the press that just landed there.
    internal var dirAt = 0L
    internal var dirFocus = -1
    internal var movedAt = 0L
    internal var enteredAt = 0L
    internal val keysDown = HashSet<Int>()

    // Update 151: ◀ on the first game first only knocks against the start (the game stays selected); a second fresh ◀
    // while still on it goes into the menu. A quick tapper's one extra press used to skip the first game into the menu.
    internal var edgeKnock = false
    /** Update 151: the Side menu's row goes round (Endless): ◀ on the first game slides on to the last instead. */
    @Volatile var sideLoops = false

    fun items(z: Zone): List<() -> Unit> = when (z) {
        Zone.SIDE -> side
        Zone.BAR -> bar
        Zone.TOP -> top
        Zone.GAMES -> emptyList()
    }

    /** Moves into [z] (on the button it was left on). False when it has no buttons. */
    fun enter(z: Zone, at: Long): Boolean {
        val n = items(z).size
        if (n == 0) return false
        zone = z
        index = (if (z == Zone.TOP && topStart >= 0) topStart else last[z.ordinal]).coerceIn(0, n - 1)
        enteredAt = at
        return true
    }

    /** The next [enter] into [z] starts on its first button. */
    fun forget(z: Zone) { last[z.ordinal] = 0 }

    fun leave() {
        last[zone.ordinal] = index
        zone = Zone.GAMES
    }

    fun reset() {
        zone = Zone.GAMES
        index = 0
        topStart = -1
    }

    /** True when the D-pad is on button [i] of [z]. */
    fun on(z: Zone, i: Int): Boolean = zone == z && index == i

    /** A tap on a button: the selection moves there too (so the D-pad carries on from it). */
    fun tapped(z: Zone, i: Int) {
        zone = z
        index = i
    }
}

/**
 * The style homes' keys, called by the home screen before its own D-pad handling. True = handled (the
 * home screen stops there). Menus: ◀ ▶ / ▲ ▼ along, A opens, B (or the way back) returns to the games.
 * On the games: ◀ ▶ are the home screen's own (move a game) except where a layout needs them.
 */
internal fun styleHomeKey(
    event: KeyEvent,
    layout: HomeLayout,
    count: Int,
    focus: Int,
    setFocus: (Int) -> Unit,
    category: (Boolean) -> Unit,
    sound: (UiSound) -> Unit,
    /** Past the layout's last menu / bar at the top ([Boolean] true) or the bottom: what lies there (HomeReach). */
    reach: (Boolean) -> Boolean = { false }
): Boolean {
    val code = event.nativeKeyEvent.keyCode
    val down = event.type == KeyEventType.KeyDown
    val up = event.type == KeyEventType.KeyUp
    if (up && StyleNav.keysDown.remove(code)) return true
    val first = event.nativeKeyEvent.repeatCount == 0
    val k = event.key
    val isDir = k == Key.DirectionLeft || k == Key.DirectionRight || k == Key.DirectionUp || k == Key.DirectionDown

    val zone = StyleNav.zone
    if (zone != StyleNav.Zone.GAMES) {
        val items = StyleNav.items(zone)
        if (items.isEmpty()) {
            StyleNav.reset()
            return false
        }
        if (isDir) {
            if (!down) return true
            // Resume's four shortcuts sit in a 2 × 2 block under the game: the arrows move across it. Update 119: ▲ from
            // its top row goes straight back up to the game (it used to be ▶ only, which was hard to find), ▶ from the
            // right-hand column on to the Discover cards.
            if (layout == HomeLayout.RESUME && zone == StyleNav.Zone.SIDE) {
                val idx = StyleNav.index
                var next = idx
                var back = false
                var toCards = false
                when (k) {
                    Key.DirectionUp -> if (idx >= 2) next = idx - 2 else back = first
                    Key.DirectionDown -> if (idx + 2 <= items.lastIndex) next = idx + 2
                    Key.DirectionLeft -> if (idx % 2 == 1) next = idx - 1
                    Key.DirectionRight -> if (idx % 2 == 0 && idx + 1 <= items.lastIndex) next = idx + 1
                        else if (first && StyleNav.bar.isNotEmpty()) toCards = true else back = first
                    else -> Unit
                }
                if (next != idx) { StyleNav.index = next; sound(UiSound.MOVE) }
                if (back) { StyleNav.leave(); sound(UiSound.MOVE) }
                if (toCards) { StyleNav.leave(); if (StyleNav.enter(StyleNav.Zone.BAR, event.nativeKeyEvent.eventTime)) sound(UiSound.MOVE) }
                StyleNav.keysDown += code
                return true
            }
            // Update 130, Circuit board: the power switch (Play) sits above Settings and Details, so the arrows follow that:
            // ▼ from Play to Settings, ▲ from Settings / Details back to Play, ◀ ▶ between those two; ▲ from Play back to the
            // board. (▼ from Play used to do nothing — only ◀ ▶ got away from it.)
            if (layout == HomeLayout.CIRCUIT && zone == StyleNav.Zone.BAR && items.size >= 3) {
                val idx = StyleNav.index
                var next = idx
                when (k) {
                    Key.DirectionUp -> if (idx == 0) { if (first) { StyleNav.leave(); sound(UiSound.MOVE) } } else next = 0
                    Key.DirectionDown -> if (idx == 0) next = 1 else if (first && reach(false)) StyleNav.reset()
                    Key.DirectionLeft -> if (idx == 2) next = 1
                    Key.DirectionRight -> if (idx == 1) next = 2
                    else -> Unit
                }
                if (next != idx) { StyleNav.index = next; sound(UiSound.MOVE) }
                StyleNav.keysDown += code
                return true
            }
            val vertical = zone == StyleNav.Zone.SIDE
            val along = if (vertical) k == Key.DirectionUp || k == Key.DirectionDown else k == Key.DirectionLeft || k == Key.DirectionRight
            // A tab that stands for the category you're in (Icon bar "only the category you're in", Spotlight's names):
            // ◀ ▶ step through the categories there, like L2 / R2.
            // (Update 125: Spotlight's Row & menu look has round buttons up there instead: ◀ ▶ walk along them.)
            val categoryTab = zone == StyleNav.Zone.TOP && ((layout == HomeLayout.SPOT && Features.spotLook.value == Features.SpotLook.CINEMA) || layout == HomeLayout.CIRCUIT ||
                (layout == HomeLayout.BAR && Features.barIcons.state.value && Features.barTabsCurrent.state.value))
            if (along && categoryTab) {
                category(k == Key.DirectionRight)
                sound(UiSound.MOVE)
            } else if (along) {
                val d = if (k == Key.DirectionRight || k == Key.DirectionDown) 1 else -1
                val next = (StyleNav.index + d).coerceIn(0, items.lastIndex)
                if (next != StyleNav.index) {
                    StyleNav.index = next
                    sound(UiSound.MOVE)
                } else if (first && layout == HomeLayout.RESUME && zone == StyleNav.Zone.BAR && d < 0 && StyleNav.side.isNotEmpty()) {
                    // Resume (update 119): ◀ from the first Discover card back to the shortcuts.
                    StyleNav.leave()
                    if (StyleNav.enter(StyleNav.Zone.SIDE, event.nativeKeyEvent.eventTime)) sound(UiSound.MOVE)
                } else if (first && layout == HomeLayout.DASH && zone == StyleNav.Zone.TOP && d < 0 && StyleNav.side.isNotEmpty()) {
                    // Dashboard: ◀ from Play goes on to the menu on the left.
                    StyleNav.leave()
                    if (StyleNav.enter(StyleNav.Zone.SIDE, event.nativeKeyEvent.eventTime)) sound(UiSound.MOVE)
                }
            } else if (first) {
                // The way back to the games: ▶ from a menu on the left, ▲ from a bar under them, ▼ from one above.
                val back = when (zone) {
                    StyleNav.Zone.SIDE -> k == Key.DirectionRight
                    // Spotlight with its tiles at the bottom has Play and the cards above them: back is ▼ there.
                    StyleNav.Zone.BAR -> if (layout == HomeLayout.SPOT && Features.spotTilesAt.state.value == Features.SpotSide.BOTTOM && Features.spotLook.value == Features.SpotLook.CINEMA) k == Key.DirectionDown else k == Key.DirectionUp
                    StyleNav.Zone.TOP -> k == Key.DirectionDown
                    StyleNav.Zone.GAMES -> false
                }
                if (back) {
                    StyleNav.leave()
                    sound(UiSound.MOVE)
                } else if (k == Key.DirectionUp || k == Key.DirectionDown) {
                    val goingUp = k == Key.DirectionUp
                    if (layout == HomeLayout.SPOT && zone == StyleNav.Zone.BAR && goingUp && StyleNav.top.isNotEmpty()) {
                        // Spotlight with the tiles at the bottom: from Play & the cards ▲ goes on up to the category names.
                        StyleNav.leave()
                        if (StyleNav.enter(StyleNav.Zone.TOP, event.nativeKeyEvent.eventTime)) sound(UiSound.MOVE)
                    } else if (reach(goingUp)) {
                        // Past the layout's last row of buttons: what lies beyond (round shortcuts, music player).
                        StyleNav.reset()
                    }
                }
            }
            StyleNav.keysDown += code
            return true
        }
        return when (k) {
            Key.ButtonA, Key.Enter, Key.DirectionCenter -> {
                if (up && event.nativeKeyEvent.downTime >= StyleNav.enteredAt) {
                    sound(UiSound.OPEN)
                    items.getOrNull(StyleNav.index)?.invoke()
                }
                true
            }
            Key.ButtonB, Key.Back -> {
                if (up) {
                    StyleNav.leave()
                    sound(UiSound.BACK)
                }
                true
            }
            else -> false
        }
    }

    // On the games.
    // Update 158: Card binder — Y (or a hold of A) turns the card over, A plays, B turns it back (StyleCardBinder.kt).
    if (layout == HomeLayout.CARDS && !isDir && cardBinderButton(event, sound)) return true
    if (!isDir || !down) return false
    val at = event.nativeKeyEvent.eventTime
    if (focus != StyleNav.dirFocus) { StyleNav.movedAt = StyleNav.dirAt; StyleNav.edgeKnock = false }
    StyleNav.dirAt = at
    StyleNav.dirFocus = focus
    // ◀ on the first game into the menu beside it: a fresh press, a moment after landing on that game (update 130: the
    // stick's quick repeats — and a fast second press — went straight past the first game into the menu, so it seemed
    // it could never be reached).
    // Update 151: and only after one ◀ has already knocked against the start while on it (see StyleNav.edgeKnock).
    val menuOk = first && at - StyleNav.movedAt > 280 && StyleNav.edgeKnock
    fun take(): Boolean {
        StyleNav.keysDown += code
        return true
    }
    fun enter(z: StyleNav.Zone): Boolean {
        if (first && StyleNav.enter(z, at)) sound(UiSound.MOVE)
        return take()
    }
    // Update 151: ◀ on the first game that doesn't go into the menu: it knocks against the start (the next fresh ◀ goes in).
    fun knock(): Boolean {
        // (Only a press made while resting on it counts, not a stray second event of the press that landed there.)
        if (first && !StyleNav.edgeKnock && at - StyleNav.movedAt > 280) { StyleNav.edgeKnock = true; EdgeBump.hit(-1) }
        return take()
    }
    fun leftToMenu(): Boolean = if (menuOk) { StyleNav.edgeKnock = false; enter(StyleNav.Zone.SIDE) } else knock()
    when (layout) {
        HomeLayout.SIDE -> return when (k) {
            Key.DirectionUp -> enter(StyleNav.Zone.SIDE)
            Key.DirectionDown -> { if (first) reach(false); take() }
            // Update 151: knock first, then the menu (leftToMenu); with Endless on ◀ slides on round to the last game.
            Key.DirectionLeft -> if (focus == 0 && StyleNav.side.isNotEmpty() && !StyleNav.sideLoops) leftToMenu() else false
            else -> false
        }
        HomeLayout.SPOT -> {
            // Tiles at the bottom: Play, the dots and the cards sit above them, so ▲ goes there and ▼ is the end of the screen.
            // Tiles on top: ▲ goes up to the category names (or past them).
            // (Update 125: the Row & menu look always has its tiles on top.)
            val bottom = Features.spotTilesAt.state.value == Features.SpotSide.BOTTOM && Features.spotLook.value == Features.SpotLook.CINEMA
            return when (k) {
                if (bottom) Key.DirectionUp else Key.DirectionDown -> enter(StyleNav.Zone.BAR)
                if (bottom) Key.DirectionDown else Key.DirectionUp -> {
                    if (first && !(k == Key.DirectionUp && StyleNav.enter(StyleNav.Zone.TOP, at).also { if (it) sound(UiSound.MOVE) })) reach(k == Key.DirectionUp)
                    take()
                }
                else -> false
            }
        }
        HomeLayout.BAR -> return when (k) {
            Key.DirectionUp -> { if (first && !StyleNav.enter(StyleNav.Zone.TOP, at).also { if (it) sound(UiSound.MOVE) }) reach(true); take() }
            // ▼: Play / Continue and the round buttons under the game's name (update 101), then past them.
            Key.DirectionDown -> { if (first && !StyleNav.enter(StyleNav.Zone.BAR, at).also { if (it) sound(UiSound.MOVE) }) reach(false); take() }
            else -> false
        }
        HomeLayout.HUB -> return when (k) {
            Key.DirectionUp -> if (StyleNav.top.isNotEmpty()) enter(StyleNav.Zone.TOP) else { if (first) reach(true); take() }
            Key.DirectionDown -> if (StyleNav.bar.isNotEmpty()) enter(StyleNav.Zone.BAR) else { if (first) reach(false); take() }
            else -> false
        }
        // Circuit board (update 106): ◀ ▶ along the board (the home screen's own), ▲ the category LCD, ▼ the switch.
        HomeLayout.CIRCUIT -> return when (k) {
            Key.DirectionUp -> if (StyleNav.top.isNotEmpty()) enter(StyleNav.Zone.TOP) else { if (first) reach(true); take() }
            // Update 125: ▼ always lands on the power switch (Play), not on the button it was left on.
            Key.DirectionDown -> if (StyleNav.bar.isNotEmpty()) { StyleNav.forget(StyleNav.Zone.BAR); enter(StyleNav.Zone.BAR) } else { if (first) reach(false); take() }
            else -> false
        }
        HomeLayout.RESUME -> return when (k) {
            Key.DirectionLeft -> if (focus == 0 && StyleNav.side.isNotEmpty()) leftToMenu() else false // Update 151: knock first
            // (Update 119: ▼ goes to the shortcuts right under the game; the Discover cards are ▶ from them.)
            Key.DirectionDown -> if (StyleNav.side.isNotEmpty()) enter(StyleNav.Zone.SIDE) else if (StyleNav.bar.isNotEmpty()) enter(StyleNav.Zone.BAR) else { if (first) reach(false); take() }
            Key.DirectionUp -> { if (first) reach(true); take() }
            else -> false
        }
        HomeLayout.DASH -> return when (k) {
            // ▲: Play in the Now playing card (update 101); ◀ from it (or ◀ on the first game) the menu.
            Key.DirectionUp -> if (StyleNav.top.isNotEmpty()) enter(StyleNav.Zone.TOP) else if (StyleNav.side.isNotEmpty()) enter(StyleNav.Zone.SIDE) else { if (first) reach(true); take() }
            Key.DirectionDown -> if (StyleNav.bar.isNotEmpty()) enter(StyleNav.Zone.BAR) else { if (first) reach(false); take() }
            Key.DirectionLeft -> if (focus == 0 && StyleNav.side.isNotEmpty()) leftToMenu() else false // Update 151: knock first
            else -> false
        }
        HomeLayout.CHAN -> {
            // Pages of a grid (update 104: each page stays put). ▲ ▼ move a row within the page — past its bottom row
            // the Channel grid's round buttons (then what lies beyond).
            // ◀ ▶ move along the row; past its end they turn the page, onto the same row of the next / previous page.
            val cols = Features.chanCols.state.value
            val rows = 3
            if (count == 0) return false
            if (Features.gridsContinuous.value) {
                // Update 112: one long strip of columns (the games run down each column): ◀ ▶ a column over, ▲ ▼ along it.
                val r = focus % rows
                // Update 156: Endless on: ◀ on the first column goes round to the last one, ▶ on the last to the first
                // (the strip carries on with the games again, see ContinuousGrid); [round] tells it which way.
                val loop = StyleLoop.on && count >= 2
                var round = 0
                val target: Int? = when (k) {
                    Key.DirectionUp -> if (r > 0) focus - 1 else null
                    Key.DirectionDown -> if (r < rows - 1 && focus + 1 < count) focus + 1 else null
                    Key.DirectionLeft -> if (focus - rows >= 0) focus - rows
                        else if (loop) { round = -1; ((count - 1) / rows * rows + r).coerceAtMost(count - 1) } else null
                    Key.DirectionRight -> when {
                        focus + rows < count -> focus + rows
                        // The last column is shorter: its last game.
                        (focus / rows + 1) * rows < count -> count - 1
                        loop -> { round = 1; r.coerceAtMost(count - 1) }
                        else -> null
                    }
                    else -> return false
                }
                if (target != null && target != focus) {
                    if (round != 0) StyleLoop.wrapped(round)
                    setFocus(target)
                    sound(UiSound.MOVE)
                } else if (first && (k == Key.DirectionUp || k == Key.DirectionDown)) {
                    val zoneThere = if (k == Key.DirectionUp) StyleNav.Zone.TOP else StyleNav.Zone.BAR
                    if (StyleNav.enter(zoneThere, at)) sound(UiSound.MOVE) else reach(k == Key.DirectionUp)
                }
                return take()
            }
            val per = cols * rows
            val pageStart = focus - focus % per
            val pos = focus - pageStart
            val r = pos / cols
            val col = pos % cols
            // Update 156: Endless on: ◀ ▶ past the first / last page go round (the last page's first game: [lastStart]).
            val loop = StyleLoop.on && count >= 2
            val lastStart = (count - 1) / per * per
            var round = 0
            val target: Int? = when (k) {
                Key.DirectionUp -> if (r > 0) focus - cols else null
                Key.DirectionDown -> when {
                    r >= rows - 1 -> null
                    focus + cols < count -> focus + cols
                    pageStart + (r + 1) * cols < count -> count - 1 // the row below is shorter: its last game
                    else -> null
                }
                Key.DirectionLeft -> when {
                    col > 0 -> focus - 1
                    pageStart > 0 -> (focus - per + cols - 1).coerceAtMost(count - 1)
                    // Update 156: Endless on: from the first page round to the same row of the last one.
                    loop -> { if (lastStart > 0) round = -1; (lastStart + r * cols + cols - 1).coerceAtMost(count - 1) }
                    else -> null
                }
                Key.DirectionRight -> when {
                    col < cols - 1 && focus + 1 < count -> focus + 1
                    pageStart + per < count -> (pageStart + per + r * cols).coerceAtMost(count - 1)
                    // Update 156: Endless on: from the last page round to the same row of the first one.
                    loop -> { if (lastStart > 0) round = 1; (r * cols).coerceAtMost(count - 1) }
                    else -> null
                }
                else -> return false
            }
            if (target != null && target in 0 until count && target != focus) {
                // Update 156: a page turned round slides in like any next / previous page (see StyleChannelGrid).
                if (round != 0) StyleLoop.wrapped(round)
                setFocus(target)
                sound(UiSound.MOVE)
            } else if (first && (k == Key.DirectionUp || k == Key.DirectionDown)) {
                val zoneThere = if (k == Key.DirectionUp) StyleNav.Zone.TOP else StyleNav.Zone.BAR
                if (StyleNav.enter(zoneThere, at)) sound(UiSound.MOVE) else reach(k == Key.DirectionUp)
            }
            return take()
        }
        HomeLayout.CROSS -> {
            // ◀ ▶ change the category (the icons along the top), ▲ ▼ the game in the list.
            when (k) {
                Key.DirectionLeft, Key.DirectionRight -> {
                    category(k == Key.DirectionRight)
                    sound(UiSound.MOVE)
                }
                else -> {
                    val next = (focus + if (k == Key.DirectionDown) 1 else -1).coerceIn(0, (count - 1).coerceAtLeast(0))
                    if (next != focus) {
                        setFocus(next)
                        sound(UiSound.MOVE)
                    } else if (k == Key.DirectionDown && first && count > 1) {
                        // Update 166: ▼ from the last game goes round to the first.
                        setFocus(0)
                        sound(UiSound.MOVE)
                    } else if (first) reach(k == Key.DirectionUp)
                }
            }
            return take()
        }
        HomeLayout.SHELF -> return when (k) {
            // ▲ ▼: the nearest game on the shelf above / below.
            Key.DirectionUp, Key.DirectionDown -> {
                val next = ShelfHome.vertical(focus, if (k == Key.DirectionDown) 1 else -1)
                if (next != null && next != focus) {
                    setFocus(next)
                    sound(UiSound.MOVE)
                } else if (first) reach(k == Key.DirectionUp)
                take()
            }
            else -> false
        }
        // Update 158: Hero tiles handles its own keys (StyleHeroTiles.kt).
        HomeLayout.HERO_TILES -> return heroTilesKey(
            StyleKeyCtx(
                key = k, first = first, focus = focus, count = count, setFocus = setFocus, sound = sound,
                category = category, enterZone = { z -> enter(z) }, reach = { up -> if (first) reach(up); take() }, take = { take() }
            )
        )
        // Update 158: the two-screen layouts handle their own keys (TwinDesk.kt / TwinIcons.kt).
        HomeLayout.TWIN_DESK, HomeLayout.TWIN_ICONS -> {
            val twinCtx = StyleKeyCtx(
                key = k, first = first, focus = focus, count = count, setFocus = setFocus, sound = sound,
                category = category, enterZone = { z -> enter(z) }, reach = { up -> if (first) reach(up); take() }, take = { take() }
            )
            return if (layout == HomeLayout.TWIN_DESK) twinDeskKey(twinCtx) else twinIconsKey(twinCtx)
        }
        // Update 158: Card binder handles its own ▲ ▼ (StyleCardBinder.kt); ◀ ▶ are the home screen's own.
        HomeLayout.CARDS -> return cardBinderKey(
            StyleKeyCtx(
                key = k, first = first, focus = focus, count = count, setFocus = setFocus, sound = sound,
                category = category, enterZone = { z -> enter(z) }, reach = { up -> if (first) reach(up); take() }, take = { take() }
            )
        )
        // Update 158: Record crate handles its own keys (StyleRecordCrate.kt).
        HomeLayout.CRATE -> return recordCrateKey(
            StyleKeyCtx(
                key = k, first = first, focus = focus, count = count, setFocus = setFocus, sound = sound,
                category = category, enterZone = { z -> enter(z) }, reach = { up -> if (first) reach(up); take() }, take = { take() }
            )
        )
        // Update 146: the newer layouts handle their own keys (in their own files) through [StyleKeyCtx].
        HomeLayout.LIBRARY, HomeLayout.STACKS, HomeLayout.TIMELINE, HomeLayout.ARCADE, HomeLayout.CONSOLES, HomeLayout.DISCS -> {
            val ctx = StyleKeyCtx(
                key = k, first = first, focus = focus, count = count, setFocus = setFocus, sound = sound,
                category = category, enterZone = { z -> enter(z) }, reach = { up -> if (first) reach(up); take() }, take = { take() }
            )
            return when (layout) {
                HomeLayout.LIBRARY -> libraryKey(ctx)
                HomeLayout.STACKS -> caseStacksKey(ctx)
                HomeLayout.TIMELINE -> timelineKey(ctx)
                HomeLayout.ARCADE -> arcadeKey(ctx)
                HomeLayout.DISCS -> discStationKey(ctx)
                else -> consoleStackKey(ctx)
            }
        }
        HomeLayout.LIVE -> {
            val move = liveMove(focus, k, StyleNav.livePage)
            if (move == null) {
                if (k == Key.DirectionDown) {
                    if (StyleNav.bar.isNotEmpty()) return enter(StyleNav.Zone.BAR)
                    if (first) reach(false)
                }
                return take()
            }
            // ▲ on the top row: the category names (update 101), then what lies above.
            if (k == Key.DirectionUp && move == focus) {
                if (first && !StyleNav.enter(StyleNav.Zone.TOP, at).also { if (it) sound(UiSound.MOVE) }) reach(true)
                return take()
            }
            // Update 156: Endless (R1): ▶ past the last page goes round to the first page, ◀ past the first to the last
            // (LiveLoop.kt); the page slides in like any next page.
            val round = liveWrap(focus, k, StyleNav.livePage, count, move)
            if (round >= 0) {
                if (round != focus) {
                    LiveLoop.wrapped(focus, round, if (k == Key.DirectionRight) 1 else -1)
                    setFocus(round)
                    sound(UiSound.MOVE)
                }
                return take()
            }
            val next = move.coerceIn(0, (count - 1).coerceAtLeast(0))
            if (next != focus && move >= 0 && (move < count || next / StyleNav.livePage != focus / StyleNav.livePage)) {
                setFocus(next)
                sound(UiSound.MOVE)
            }
            return take()
        }
        else -> return false
    }
}

/**
 * Update 146: what a newer style home's key handler gets (see [styleHomeKey]) — called for a D-pad direction press
 * (KeyDown) while the D-pad is on the games (menus / bars are handled by [styleHomeKey] itself).
 * Return true when handled, false to let the home screen do its default (◀ ▶ = previous / next game).
 */
internal class StyleKeyCtx(
    /** The direction pressed. */
    val key: Key,
    /** A fresh press (not the auto-repeat of a held one). */
    val first: Boolean,
    /** The game you're on (index in the category) and how many there are. */
    val focus: Int,
    val count: Int,
    /** Moves to another game (play [sound] UiSound.MOVE yourself). */
    val setFocus: (Int) -> Unit,
    val sound: (UiSound) -> Unit,
    /** L2 / R2: true = next category, false = previous. */
    val category: (Boolean) -> Unit,
    /** Moves the D-pad into a zone's buttons (StyleNav.top / side / bar, set by the layout as it draws); true = handled. */
    val enterZone: (StyleNav.Zone) -> Boolean,
    /** Past the layout's edge: up (true) or down — the status bar / player above or below (HomeReach); true = handled. */
    val reach: (Boolean) -> Boolean,
    /** Swallows the key (and its release); returns true. */
    val take: () -> Boolean
)

/**
 * Live tiles: where a key goes from game [i] on pages of [page] games (big tile first). Null = off the
 * bottom of the page (▼ goes to the shortcut tiles) or nowhere.
 */
internal fun liveMove(i: Int, key: Key, page: Int): Int? {
    val base = i - i % page
    val s = i % page
    return when (page) {
        3 -> when (key) {
            Key.DirectionRight -> when (s) { 0 -> i + 1; else -> base + 3 }
            Key.DirectionLeft -> when (s) { 0 -> if (base > 0) base - 2 else i; else -> base }
            Key.DirectionUp -> if (s == 2) i - 1 else i
            Key.DirectionDown -> if (s == 1) i + 1 else null
            else -> i
        }
        6 -> when (key) {
            Key.DirectionRight -> if (s % 3 < 2) i + 1 else base + 6 + (s - 2)
            Key.DirectionLeft -> if (s % 3 > 0) i - 1 else if (base > 0) base - 6 + s + 2 else i
            Key.DirectionUp -> if (s >= 3) i - 3 else i
            Key.DirectionDown -> if (s < 3) i + 3 else null
            else -> i
        }
        else -> when (key) {
            Key.DirectionRight -> when (s) { 0, 1, 3 -> i + 1; else -> base + 5 }
            Key.DirectionLeft -> when (s) { 0 -> if (base > 0) base - 3 else i; 1, 3 -> base; else -> i - 1 }
            Key.DirectionUp -> if (s >= 3) i - 2 else i
            Key.DirectionDown -> if (s == 1 || s == 2) i + 2 else null
            else -> i
        }
    }
}

/** What the style homes do with the home screen. */
internal class StyleActions(
    val select: (Int) -> Unit,
    /** The details window of the game you're on. */
    val open: () -> Unit,
    /** Starts a game straight away. */
    val play: (Game) -> Unit,
    /** Category steps (+ forward / − back), as L2 / R2. */
    val category: (Int) -> Unit,
    /** The game's achievements. */
    val achievements: (Game) -> Unit,
    /** Update 132: X on a game — closes it when it's still open in the background, else favourite on / off. */
    val favorite: (Game) -> Unit = {},
    val needPhysical: (Game) -> Unit,
    val needMedia: (Game) -> Unit,
    /** Game shelf: the game's scraped spine (fetched again once for lists saved before spines were scraped). */
    val needSpine: (Game) -> Unit = needPhysical,
    /** The built-in music player card (Dashboard, Live tiles). */
    val mediaPlayPause: () -> Unit = {},
    val mediaNext: () -> Unit = {},
    val mediaPrevious: () -> Unit = {},
    val mediaSeek: (Long) -> Unit = {},
    /** The game's own settings window, and its save sync (Icon bar's buttons). */
    val options: (Game) -> Unit = {},
    val sync: (Game) -> Unit = {},
    /** The corner buttons of the Channel grid (update 104): the profile, Settings, notifications and save sync. */
    val profile: () -> Unit = {},
    val settings: () -> Unit = {},
    val notifications: () -> Unit = {},
    val cloud: () -> Unit = {},
    /** The details window itself (the Channel grid's own game window opens it from its Details button). */
    val details: () -> Unit = open,
    /** The game's achievement list (update 104: Resume / Hub show the next ones to earn behind their card). */
    val needAchievements: (Game) -> Unit = {},
    /** Update 158: the R1 quick settings panel and the screensaver (Twin desk's round buttons). */
    val quickMenu: () -> Unit = {},
    val screensaver: () -> Unit = {}
)

/** One stop of the category cycle (as L2 / R2 go through them). */
internal class StyleStop(val special: SpecialFilter?, val platform: String?, val label: String, val logo: String?) {
    /** The icon for a stop without a console logo. */
    val icon: ImageVector get() = when (special) {
        null -> when {
            platform == null -> Icons.Filled.Apps
            com.shelf.launcher.data.isCollectionKey(platform) -> Icons.Filled.Folder
            else -> Icons.Filled.SportsEsports
        }
        SpecialFilter.FAVORITES -> Icons.Filled.Star
        SpecialFilter.RECENT -> Icons.Filled.History
        SpecialFilter.MOST_PLAYED -> Icons.Filled.TrendingUp
        SpecialFilter.MASTERED -> Icons.Filled.EmojiEvents
        SpecialFilter.IN_PROGRESS -> Icons.Filled.Timelapse
        SpecialFilter.ALMOST_MASTERED -> Icons.Filled.MilitaryTech
        SpecialFilter.NEVER_PLAYED -> Icons.Filled.FiberNew
        SpecialFilter.FORGOTTEN -> Icons.Filled.Bedtime
        SpecialFilter.EMULATORS -> Icons.Filled.Memory
        SpecialFilter.SMART_CONTINUE -> Icons.Filled.PlayCircle
        SpecialFilter.REDISCOVER -> Icons.Filled.Explore
        // Update 132: the Cross menu's Trophies category.
        SpecialFilter.TROPHIES -> Icons.Filled.WorkspacePremium
    }
}

/** Everything a style home draws from, read from the home screen's state. */
internal class StyleCtx(
    val state: LauncherUiState,
    val games: List<Game>,
    val focus: Int,
    val art: Map<String, GameArt>,
    val shortcuts: List<HomeShortcut>,
    val actions: StyleActions,
    /** This layout's card artwork (Appearance › Cards). */
    val type: CardArtType,
    val mixed: Boolean,
    val stops: List<StyleStop>,
    /** The stop you're in now (-1 when it isn't in the cycle, e.g. Rediscover). */
    val stop: Int,
    val categoryLabel: String
) {
    val game: Game? get() = games.getOrNull(focus)
    fun cover(g: Game): String? = state.artOverrides[g.stableKey]?.cover ?: art[g.stableKey]?.gridUrl
    fun wide(g: Game): String? = state.artOverrides[g.stableKey]?.wide ?: art[g.stableKey]?.wideGridUrl
        ?: state.artOverrides[g.stableKey]?.hero ?: art[g.stableKey]?.heroUrl ?: cover(g)
    fun backdrop(g: Game): String? = ArtPick.backdrop(g, state.artOverrides, art, state.screenshots)
    fun logo(g: Game): String? = state.artOverrides[g.stableKey]?.logo ?: art[g.stableKey]?.logoUrl
    fun playtime(g: Game): Long? = state.playtime[g.stableKey]?.takeIf { it > 0L }
    fun lastPlayed(g: Game): Long? = state.lastPlayedAt[g.stableKey]
    /** Earned / total achievements, when the game has any. */
    fun progress(g: Game): Pair<Int, Int>? {
        val list = state.raAchievementDetails[g.stableKey].orEmpty()
        val p = state.raProgress[g.stableKey]
        val total = if (list.isNotEmpty()) list.size else p?.NumPossibleAchievements ?: 0
        if (total <= 0) return null
        val earned = if (list.isNotEmpty()) list.count { it.isUnlocked } else p?.NumAchieved ?: 0
        return earned to total
    }
    /** The stop [d] steps away from the one you're in, wrapping round. */
    fun stopAt(d: Int): StyleStop? = if (stops.isEmpty() || stop < 0) null else stops[(stop + d).mod(stops.size)]
}

/** The category cycle (what L2 / R2 step through), the same order the view model uses. */
internal fun styleStops(state: LauncherUiState, titles: TitleOptions): Pair<List<StyleStop>, Int> {
    val stops = state.categoryOrder.flatMap { key ->
        when (key) {
            CategorySettings.ALL -> listOf(StyleStop(null, null, "All", null))
            CategorySettings.CONSOLES -> state.availablePlatforms.map {
                // Only the colour logos shipped with the app (your ES-DE set), never downloaded; else the category symbol.
                StyleStop(null, it, titles.console(it), com.shelf.launcher.data.SystemLogos.bundled(it))
            }
            CategorySettings.COLLECTIONS -> state.collections.map { StyleStop(null, it.key, it.name, null) }
            else -> runCatching { SpecialFilter.valueOf(key) }.getOrNull()?.let { listOf(StyleStop(it, null, it.label, null)) }.orEmpty()
        }
    }
    val current = stops.indexOfFirst { it.special == state.specialFilter && it.platform == state.platformFilter }
    return stops to current
}

/** "Never played", "45 min", "3 h 20 min". [played]: it has been started (a last-played date) — then no time
 *  recorded reads "Under a minute", not "Never played" next to its "Last played" date (update 136). */
internal fun stylePlaytime(ms: Long?, played: Boolean = false): String {
    if (ms == null || ms <= 0L) return if (played) "Under a minute" else "Never played"
    val min = (ms / 60_000L).toInt()
    return when {
        min < 1 -> "Under a minute"
        min < 60 -> "$min min"
        else -> "${min / 60} h" + if (min % 60 > 0) " ${min % 60} min" else ""
    }
}

/** Width / height of a card for an artwork type (style homes). */
internal fun styleAspect(type: CardArtType): Float = if (type == CardArtType.COVER && Features.spineLook.state.value) SPINE_ASPECT
    // Update 171: the Tile look in the other layouts too (Library panel and the rest): every cover a square tile.
    else if (type == CardArtType.COVER && carouselTiles()) 1f
    else when (type) {
    CardArtType.COVER, CardArtType.CASE, CardArtType.CASE_REAL, CardArtType.DISC -> 0.72f
    CardArtType.CAPSULE, CardArtType.HERO, CardArtType.SCREENSHOT -> 1.78f
    CardArtType.CARTRIDGE_DISC -> 1f
}

/** The text colour that reads on a style panel. */
@Composable
internal fun panelInk(look: Features.PanelLook): Color = when (look) {
    Features.PanelLook.LIGHT -> Color(0xFF243140)
    Features.PanelLook.DARK -> Color.White
    Features.PanelLook.THEME -> ShelfTheme.colors.textPrimary
}

/** A glass / dark / theme panel behind part of a style home. */
@Composable
internal fun Modifier.stylePanel(look: Features.PanelLook, opacity: Float, shape: Shape): Modifier {
    val (fill, edge) = when (look) {
        Features.PanelLook.LIGHT -> Color.White.copy(alpha = 0.72f * opacity) to Color.White.copy(alpha = 0.75f)
        // Update 151: the dark glass takes the theme's colour (it was one fixed grey whatever the theme).
        Features.PanelLook.DARK -> themeDark(Color(0xFF0A0E15)).copy(alpha = 0.85f * opacity) to ShelfTheme.accent.copy(alpha = 0.16f)
        Features.PanelLook.THEME -> ShelfTheme.colors.surface.copy(alpha = 0.9f * opacity) to ShelfTheme.accent.copy(alpha = 0.3f)
    }
    return this.clip(shape).background(fill).border(1.dp, edge, shape)
}

/** The focus look on a style home's card: the layout's own effect (soft glow / electric) and an outline. */
@Composable
internal fun Modifier.styleFocus(on: Boolean, corner: Dp, pulse: Float, color: Color = ShelfTheme.accent, outline: Dp = 2.dp): Modifier {
    if (!on) return this
    val look = LocalCardLook.current
    val lite = LocalLiteEffects.current
    val effect = if (lite && look.focusEffect == FocusEffect.ELECTRIC) FocusEffect.GLOW else look.focusEffect
    val shape = uiRounded(corner)
    return this
        .then(
            when (effect) {
                FocusEffect.GLOW -> Modifier.softGlow(color, corner, 0.55f + 0.6f * pulse)
                FocusEffect.ELECTRIC -> Modifier.softGlow(color, corner, 0.3f + 0.2f * pulse).electricFrame(color, corner, pulse = pulse)
                else -> Modifier
            }
        )
        .border(outline, color, shape)
}

/**
 * A game's picture for a style home, filling [modifier]: the layout's card artwork (cover, capsule, the drawn
 * cartridge / disc, the box…), or forced [square] (the cover cropped square) / [wide] (capsule → hero → cover).
 */
@Composable
internal fun StyleArt(
    c: StyleCtx,
    g: Game,
    modifier: Modifier,
    square: Boolean = false,
    wide: Boolean = false,
    corner: Dp = LocalCardLook.current.cornerDp.dp,
    focused: Boolean = false,
    /** The focus pulse: with [focused], the layout's focus effect is drawn (a pool of light under a free-standing
     *  cartridge / disc / box, the glow / electric frame and an outline round a card). Null: no effect. */
    fx: Float? = null,
    ring: Color = ShelfTheme.accent,
    outline: Dp = 2.dp
) {
    val titles = LocalTitleOptions.current
    val k = g.stableKey
    val o = c.state.artOverrides[k]
    val a = c.art[k]
    val cover = o?.cover ?: a?.gridUrl
    val shape = uiRounded(corner)
    val lit = focused && fx != null
    if (square || wide) {
        val url = if (wide) c.wide(g) else cover ?: c.wide(g)
        // (Update 113: the game you're on reports where it is, so the game window grows out of it.)
        Box(modifier.gameOrigin(k, k == c.game?.stableKey).then(if (lit) Modifier.styleFocus(true, corner, fx ?: 0f, ring, outline) else Modifier).clip(shape).background(placeholderBrush(g.hue))) {
            when {
                url != null -> AsyncImage(rememberStyleArtRequest(url), g.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().pixelArtwork()) // (Update 156: StyleArtRequests.kt)
                g.sourceType == com.shelf.launcher.data.SourceType.APP -> AppIconArt(g.packageName, wide = wide, modifier = Modifier.fillMaxSize())
                else -> StyleNamePlate(titles.display(g.name))
            }
            // Update 115: the console's badge.
            ConsoleBadge(g.platform, packageName = g.packageName)
            StyleProgressChip(c, g)
        }
        return
    }
    // Update 113, Spines look: the game as its spine.
    if (spinesOn(c.type)) {
        Box(modifier.gameOrigin(k, k == c.game?.stableKey).then(if (lit) Modifier.styleFocus(true, corner, fx ?: 0f, ring, outline) else Modifier)) {
            GameSpine(g, c.state.physicalMedia[k], Modifier.fillMaxSize(), fit = true)
        }
        return
    }
    val hiddenMedia = LocalHiddenMedia.current
    val gArt = artForGame(c.type, g.platform, hiddenMedia, c.mixed)
    val typed = typedArtFor(gArt, c.state.physicalMedia[k], c.state.screenshots[k], g.platform)
    val url = cardArtUrl(gArt, false, cover, o?.wide ?: a?.wideGridUrl, o?.hero ?: a?.heroUrl, typed)
    val free = isFreeMedia(gArt, g.platform) || (drawnMediaWanted(gArt, typed) && MediaShell.of(g.platform) != null)
    val pick = if (drawnMediaWanted(gArt, typed)) mediaFor(g.platform, c.mixed, c.type == CardArtType.DISC) else null
    val box3d = if (gArt == CardArtType.CASE) caseMarker(g.platform) else null
    // Update 173: an app without art wears its icon on its cartridge's label / case front (the window stayed black).
    val appIcon = if (url == null && cover == null && g.sourceType == com.shelf.launcher.data.SourceType.APP && (pick != null || box3d != null)) {
        rememberAppIcon(g.packageName)?.let { androidx.compose.runtime.remember(it) { it.asAndroidBitmap() } }
    } else null
    // Free-standing media get the pool of light under them, never a frame round their slot.
    val fxMod = if (!lit) Modifier else if (free) Modifier.mediaFloorGlow(true, ring, fx ?: 0f) else Modifier.styleFocus(true, corner, fx ?: 0f, ring, outline)
    val here = modifier.gameOrigin(k, k == c.game?.stableKey)
    Box(if (free) here.then(fxMod) else here.then(fxMod).clip(shape).background(placeholderBrush(g.hue))) {
        when {
            pick != null -> MediaFocus(g.stableKey == c.game?.stableKey) { PhysicalMedia(pick.shell, url ?: appIcon, Modifier.fillMaxSize().padding(4.dp).awardShine(k, focused), platform = pick.platform, labelIsScan = typed != null && url == typed, caseCover = cover ?: appIcon, title = g.name) }
            box3d != null -> MediaFocus(g.stableKey == c.game?.stableKey) { CaseArt(
                g.platform, cover ?: appIcon, typed, Modifier.fillMaxSize().padding(2.dp).awardShine(k, focused), title = gameDisplayName(g.name),
                caseModifier = if (focused) Modifier.border(2.dp, ring, RoundedCornerShape(2.dp)) else Modifier
            ) }
            // Update 129: "Real discs": the game's own scan, standing free.
            realScanOf(gArt, typed) != null -> MediaFocus(g.stableKey == c.game?.stableKey) {
                RealMediaPicture(typed!!, mediaRound(g.platform), Modifier.fillMaxSize().padding(4.dp).awardShine(k, focused))
            }
            url != null -> AsyncImage(rememberStyleArtRequest(url), g.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().pixelArtwork()) // (Update 156: StyleArtRequests.kt)
            g.sourceType == com.shelf.launcher.data.SourceType.APP -> AppIconArt(g.packageName, wide = styleAspect(c.type) > 1f, modifier = Modifier.fillMaxSize())
            else -> StyleNamePlate(titles.display(g.name))
        }
        // Update 115: the console's badge (update 141: on every look, cartridges, discs and cases too).
        ConsoleBadge(g.platform, packageName = g.packageName)
        StyleProgressChip(c, g)
    }
}

/**
 * Update 125: the achievement progress on the game you're on in every style home (the R1 Progress tile / Game row ›
 * progress bar switch it): a small bar with earned / total and the share, or the mastered trophy. Only on the game
 * you're on, so the rows stay clean; not on the Circuit board (its readout has the charge bar).
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.StyleProgressChip(c: StyleCtx, g: Game) {
    val look = LocalCardLook.current
    if (!look.achievementsOnCards) return
    val at = achChipAlign(look.trophyCorner) ?: return
    if (g.stableKey != c.game?.stableKey || c.state.appearance.homeLayout == com.shelf.launcher.data.HomeLayout.CIRCUIT) return
    val (earned, total) = c.progress(g) ?: return
    GameProgressChip(earned, total, Modifier.align(at))
}

/** Update 129: where the progress chip sits on a picture (R1 Achievements tile); null when it's off. */
internal fun achChipAlign(corner: com.shelf.launcher.data.CardCorner): Alignment? = when (com.shelf.launcher.data.Features.achPlace.value) {
    com.shelf.launcher.data.Features.AchPlace.OFF -> null
    com.shelf.launcher.data.Features.AchPlace.ABOVE -> Alignment.TopCenter
    com.shelf.launcher.data.Features.AchPlace.NAME -> Alignment.BottomStart
    com.shelf.launcher.data.Features.AchPlace.UNDER -> Alignment.BottomCenter
    com.shelf.launcher.data.Features.AchPlace.CORNER -> when (corner) {
        com.shelf.launcher.data.CardCorner.TOP_LEFT -> Alignment.TopStart
        com.shelf.launcher.data.CardCorner.TOP_RIGHT -> Alignment.TopEnd
        com.shelf.launcher.data.CardCorner.BOTTOM_LEFT -> Alignment.BottomStart
        com.shelf.launcher.data.CardCorner.BOTTOM_RIGHT -> Alignment.BottomEnd
    }
}

/** Update 125: the small progress chip (bar, earned / total and share — or the mastered trophy), for any layout. */
@Composable
internal fun GameProgressChip(earned: Int, total: Int, modifier: Modifier = Modifier) {
    if (total <= 0) return
    val mastered = earned >= total
    val pct = (earned * 100f / total).roundToInt()
    // Update 136: on a narrow card the chip keeps to what fits — the trophy alone, or earned / total without the bar
    // (the word was cut to "MASTE" on the Dashboard's covers).
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
    val compact = maxWidth < 112.dp
    Row(
        Modifier.padding(5.dp).clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.62f)).padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (mastered) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.EmojiEvents, "Mastered", tint = com.shelf.launcher.ui.theme.Ds.gold, modifier = Modifier.size(if (compact) 14.dp else 12.dp))
            if (!compact) {
                Spacer(Modifier.width(4.dp))
                Text("MASTERED", color = com.shelf.launcher.ui.theme.Ds.gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        } else {
            if (!compact) {
                Box(Modifier.width(34.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.25f))) {
                    Box(Modifier.fillMaxWidth(earned.toFloat() / total).fillMaxHeight().background(ShelfTheme.accent))
                }
                // Update 151: a small upright tick between the bar and "0/69", so they read as two parts.
                ProgressTick()
            }
            Text(if (compact) "$earned/$total" else "$earned/$total · $pct%", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        }
    }
    }
}

/** The game's name in the middle of a card that has no picture yet. */
@Composable
private fun StyleNamePlate(name: String) {
    Box(Modifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.Center) {
        Text(
            name, color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp), lineHeight = androidx.compose.ui.unit.TextUnit(1.167f, androidx.compose.ui.unit.TextUnitType.Em),
            textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis
        )
    }
}

/** A smooth 0 → 1 while [on] (the focus lift of a card). */
@Composable
internal fun styleLift(on: Boolean): Float {
    // Update 116: inside a part set to stay still, nothing lifts.
    if (LocalStillPart.current) return 0f
    // Update 112: how far the game you're on stands out (Home › Animations, per layout too): a share of the layout's own lift.
    MotionPrefs.observe()
    val pop = MotionPrefs.pop()
    val lift by animateFloatAsState(if (on) pop else 0f, tween(if (on) 120 else 170), label = "styleLift")
    return lift
}

/**
 * The style home for [layout]. Looks up box / disc / screenshot art round the game you're on when the
 * layout's card artwork needs it (like the grid does).
 */
@Composable
internal fun StyleHome(
    layout: HomeLayout,
    state: LauncherUiState,
    art: Map<String, GameArt>,
    shortcuts: List<HomeShortcut>,
    actions: StyleActions,
    modifier: Modifier = Modifier
) {
    val titles = LocalTitleOptions.current
    val games = state.visibleGames
    val focus = state.focusedIndex.coerceIn(0, (games.size - 1).coerceAtLeast(0))
    val type = state.appearance.cardArtFor(layout)
    val mixed = remember(games) { mixedConsoles(games) }
    val stopsPair = remember(state.categoryOrder, state.availablePlatforms, state.collections, state.specialFilter, state.platformFilter, titles) {
        styleStops(state, titles)
    }
    val label = state.specialFilter?.label ?: state.platformFilter?.let { titles.console(it) } ?: "All"
    val c = StyleCtx(state, games, focus, art, shortcuts, actions, type, mixed, stopsPair.first, stopsPair.second, label)
    // (Update 147: the Disc station always shows discs, so it wants the disc scans whatever the game look is.)
    val needsLookup = type.wantsMedia() || type == CardArtType.SCREENSHOT || layout == HomeLayout.DISCS
    LaunchedEffect(focus, type, games.size) {
        if (!needsLookup) return@LaunchedEffect
        kotlinx.coroutines.delay(120)
        // Update 156: with Endless on, the games past the last one are the first ones again (and before the first, the
        // last ones): their scans are asked for too, so the games coming round have their art.
        val round = loopRowOn(c)
        for (i in ((focus - 3)..(focus + 8)).map { if (round) it.mod(games.size) else it }.distinct()) {
            val g = games.getOrNull(i) ?: continue
            if (type == CardArtType.SCREENSHOT) {
                if (!state.screenshots.containsKey(g.stableKey)) { actions.needMedia(g); kotlinx.coroutines.delay(90) }
            } else if (!state.physicalMedia.containsKey(g.stableKey)) { actions.needPhysical(g); kotlinx.coroutines.delay(90) }
        }
    }
    // Game shelf: the spines of the games around the one you're on (a whole shelf or two each way).
    if (layout == HomeLayout.SHELF) LaunchedEffect(focus, games.size) {
        kotlinx.coroutines.delay(150)
        // The nearest games first: moving along keeps restarting this, and the far ones used to be reached before the near ones.
        // Update 156: with Endless on the bookcase goes on round, so the spines past the last game (the first ones again)
        // and before the first are asked for too, each once.
        val roundShelf = loopRowOn(c)
        val seenShelf = HashSet<Int>()
        for (j in ((focus - 30)..(focus + 60)).sortedBy { kotlin.math.abs(it - focus) }) {
            val i = if (roundShelf) j.mod(games.size) else j
            if (!seenShelf.add(i)) continue
            val g = games.getOrNull(i) ?: continue
            val list = state.physicalMedia[g.stableKey]
            // (Update 108: a game whose spine is only cut from its 3D box picture is asked again too, near the one you're
            // on — its real scans may have been added since; the look-up itself only runs when one is due.)
            val ownScan = list != null && list.any { it.first == "Spine" || it.first == BoxTexture.LABEL }
            if (list == null || BoxTexture.spine(list, g.platform) == null || (!ownScan && kotlin.math.abs(j - focus) <= 10)) { actions.needSpine(g); kotlinx.coroutines.delay(60) }
        }
    }
    // Art warm-up (the style homes used to load a card's picture only when it scrolled on screen, which showed as a
    // slow fill-in even for art that was already saved): the games round the one you're on get their card art, wide art
    // and backdrop fetched ahead, the way the Game dock does. Thumbnails for a wide window, a decoded copy for the near ones.
    val warmContext = androidx.compose.ui.platform.LocalContext.current
    val hiddenMediaWarm = LocalHiddenMedia.current
    LaunchedEffect(focus / 2, games.size, type, if (needsLookup) state.physicalMedia else null, if (needsLookup) state.screenshots else null) {
        // The scan loop updates the media maps about every 90 ms: wait for it to settle instead of starting again each time.
        // (Update 157: every second step and sooner — moving on quickly outran the warm-up, the games coming in blank.)
        kotlinx.coroutines.delay(150)
        val small = com.shelf.launcher.device.MemoryBudget.small(warmContext)
        val reach = if (small) 12 else 24
        val near = if (small) 4 else 8
        val thumbs = ArrayList<String>()
        val near1 = ArrayList<String>()
        val backdrops = ArrayList<String>() // (Update 156)
        // Update 156: with Endless on, the art of the games coming round (the first ones past the last game, the last ones
        // before the first) is warmed as well, so going round never shows blank cards. (Doubles are dropped below.)
        val round = loopRowOn(c)
        for (i in (focus - reach)..(focus + reach)) {
            val g = games.getOrNull(if (round) i.mod(games.size) else i) ?: continue
            val k = g.stableKey
            val o = state.artOverrides[k]
            val a = art[k]
            val cover = o?.cover ?: a?.gridUrl
            val gArt = artForGame(type, g.platform, hiddenMediaWarm, mixed)
            val typed = typedArtFor(gArt, state.physicalMedia[k], state.screenshots[k], g.platform)
            val shown = cardArtUrl(gArt, false, cover, o?.wide ?: a?.wideGridUrl, o?.hero ?: a?.heroUrl, typed)
            shown?.let { thumbs += it }
            if (i in (focus - near)..(focus + near)) {
                shown?.let { near1 += it }
                // Update 157: Disc station prints the game's own label scan on its disc when there is one: that too.
                if (layout == HomeLayout.DISCS && mediaKind(g.platform) == "disc") typedArtFor(com.shelf.launcher.data.CardArtType.CARTRIDGE_DISC, state.physicalMedia[k], state.screenshots[k], g.platform)?.let { near1 += it }
                // Update 158: Hero tiles' square tiles show the cover whatever the game look is: that too.
                if (layout == HomeLayout.HERO_TILES) cover?.let { near1 += it }
                // Update 158: so do the two-screen layouts' square icons (TwinDesk.kt / TwinIcons.kt).
                if (layout.isTwin) cover?.let { near1 += it }
                // Update 158: Card binder's cards show the cover whatever the game look is: that too.
                if (layout == HomeLayout.CARDS) cover?.let { near1 += it }
                c.wide(g)?.let { near1 += it }
                if (i in (focus - 1)..(focus + 1)) c.backdrop(g)?.let { backdrops += it }
            }
        }
        val handles = ArrayList<coil.request.Disposable>()
        handles += prefetchThumbs(warmContext, thumbs.distinct())
        // Update 156: the cards' pictures warmed exactly as the cards ask for them (StyleArtRequests.kt): the old
        // 420 px warm-up was a different shape, so the cards read every picture from storage again (a second empty).
        handles += warmStyleArt(warmContext, near1.distinct())
        val loader = warmContext.imageLoader
        backdrops.distinct().forEach { url ->
            handles += loader.enqueue(coil.request.ImageRequest.Builder(warmContext).data(url).size(coil.size.Size(420, 420)).build())
        }
        try { kotlinx.coroutines.awaitCancellation() } finally {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { kotlinx.coroutines.delay(1500) }
            handles.forEach { it.dispose() }
        }
    }
    // Tap handlers read this (a tap on the game you're on opens it), whatever zone the D-pad is in.
    SideEffect { GridHome.focus = focus }
    when (layout) {
        HomeLayout.DASH -> StyleDashboard(c, modifier)
        HomeLayout.LIVE -> StyleLiveTiles(c, modifier)
        HomeLayout.SIDE -> StyleSideMenu(c, modifier)
        HomeLayout.CROSS -> StyleCrossMenu(c, modifier)
        HomeLayout.SPOT -> StyleSpotlight(c, modifier)
        HomeLayout.BAR -> StyleIconBar(c, modifier)
        HomeLayout.SHELF -> StyleShelf(c, modifier)
        HomeLayout.HUB -> StyleConsoleHub(c, modifier)
        HomeLayout.RESUME -> StyleConsoleResume(c, modifier)
        HomeLayout.CHAN -> StyleChannelGrid(c, modifier)
        HomeLayout.CIRCUIT -> StyleCircuit(c, modifier)
        // Update 146.
        HomeLayout.LIBRARY -> StyleLibrary(c, modifier)
        HomeLayout.STACKS -> StyleCaseStacks(c, modifier)
        HomeLayout.TIMELINE -> StyleTimeline(c, modifier)
        HomeLayout.ARCADE -> StyleArcade(c, modifier)
        HomeLayout.CONSOLES -> StyleConsoleStack(c, modifier)
        // Update 147.
        HomeLayout.DISCS -> StyleDiscStation(c, modifier)
        // Update 158.
        HomeLayout.HERO_TILES -> StyleHeroTiles(c, modifier)
        // Update 158: the two-screen layouts (TwinDesk.kt / TwinIcons.kt, the halves shared out in TwinScreens.kt).
        HomeLayout.TWIN_DESK -> StyleTwinDesk(c, modifier)
        HomeLayout.TWIN_ICONS -> StyleTwinIcons(c, modifier)
        // Update 158: Record crate (StyleRecordCrate.kt).
        HomeLayout.CRATE -> StyleRecordCrate(c, modifier)
        // Update 158: Card binder (StyleCardBinder.kt).
        HomeLayout.CARDS -> StyleCardBinder(c, modifier)
        else -> Unit
    }
}

/**
 * What a style home draws across the WHOLE screen, behind the status bar and the games (the home screen puts it
 * right over the background): the game's art (Side menu / Spotlight / Icon bar, their option), Spotlight's shade,
 * Cross menu's colour wash and ribbons, Live tiles' pale backdrop (between the status bar and the legend, so their
 * white text stays readable). Drawn here rather than inside the layout so nothing stops short of the screen's edges.
 */
@Composable
internal fun StyleBackLayer(layout: HomeLayout, gameKey: String?, artUrl: String?, topInset: Dp, bottomInset: Dp) {
    if (styleArtBehind(layout)) {
        // Icon bar › Cut at the middle: the art fills the top half and fades out at the middle of the screen.
        if (layout == HomeLayout.BAR && Features.barArtCut.state.value) {
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color.Black, 0.44f to Color.Black, 0.56f to Color.Transparent),
                            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                        )
                    }
            ) { DualGridArt(gameKey = gameKey, url = artUrl) }
        } else DualGridArt(gameKey = gameKey, url = artUrl)
    }
    when (layout) {
        HomeLayout.SPOT -> {
            val shade = Features.spotShade.state.value / 100f
            if (shade > 0f) Box(
                Modifier.fillMaxSize().background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = shade * 0.45f), 0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = shade)
                    )
                )
            )
        }
        HomeLayout.CROSS -> CrossBackdrop()
        // The bookcase fills the whole screen (not just the padded home area).
        HomeLayout.SHELF -> if (Features.shelfBackdrop.state.value) ShelfBackdrop(Features.shelfLook.state.value, Modifier.fillMaxSize())
        HomeLayout.HUB, HomeLayout.RESUME -> Box(
            Modifier.fillMaxSize().background(
                androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.35f), 0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.72f))
            )
        )
        HomeLayout.CHAN -> if (Features.chanLight.state.value) ChannelBackdrop(Modifier.fillMaxSize())
        HomeLayout.CIRCUIT -> if (Features.circuitBackdrop.state.value) CircuitBackdrop(Modifier.fillMaxSize())
        // Update 158: Hero tiles' pale backdrop (its option; off: the theme's background).
        HomeLayout.HERO_TILES -> if (Features.heroTilesPale.state.value) HeroTilesBackdrop(Modifier.fillMaxSize())
        HomeLayout.LIVE -> if (Features.liveLight.state.value) LiveBackdrop(Modifier.fillMaxSize().padding(top = topInset, bottom = bottomInset))
        else -> Unit
    }
}

/** Whether [layout] already shows the round shortcuts in its own menu (then the corner ☰ copy stays out). */
internal fun styleHasShortcuts(layout: HomeLayout): Boolean = when (layout) {
    HomeLayout.DASH -> Features.dashMenu.state.value || Features.dashQuick.state.value > 0
    HomeLayout.LIVE -> Features.liveBar.state.value > 0
    HomeLayout.SIDE -> true
    HomeLayout.BAR -> Features.barIcons.state.value && !Features.barTabsCurrent.state.value
    HomeLayout.HUB -> Features.hubIcons.state.value
    HomeLayout.RESUME -> true
    // Update 158: Twin icons has them as its tool buttons.
    HomeLayout.TWIN_ICONS -> true
    else -> false
}

/**
 * Update 159: [layout] draws its own solid background over the whole screen (a pale backdrop, the bookcase, the circuit
 * board): the full-screen video preview stays off there — it played over it and hid it (Hero tiles showed the game's
 * clip instead of its pale backdrop).
 */
internal fun styleOwnsBackground(layout: HomeLayout): Boolean = when (layout) {
    HomeLayout.LIVE -> Features.liveLight.state.value && !Features.liveArt.state.value
    HomeLayout.CHAN -> Features.chanLight.state.value && !Features.chanArt.state.value
    HomeLayout.HERO_TILES -> Features.heroTilesPale.state.value
    HomeLayout.SHELF -> Features.shelfBackdrop.state.value
    HomeLayout.CIRCUIT -> Features.circuitBackdrop.state.value
    else -> false
}

/** Whether [layout] wants the game's art across the whole screen behind it (its own option). */
internal fun styleArtBehind(layout: HomeLayout): Boolean = when (layout) {
    HomeLayout.SIDE -> Features.sideArt.state.value
    HomeLayout.SPOT -> Features.spotArt.state.value
    HomeLayout.BAR -> Features.barArt.state.value
    HomeLayout.HUB -> Features.hubArt.state.value
    HomeLayout.RESUME -> Features.resArt.state.value
    // Update 146: the two pale layouts can show the game's art instead (Settings › Home › Layout options › Background).
    HomeLayout.LIVE -> Features.liveArt.state.value
    HomeLayout.CHAN -> Features.chanArt.state.value
    else -> false
}

/** "No games match." in a style home. */
@Composable
internal fun StyleEmpty(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("No games match.", color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp)
    }
}
