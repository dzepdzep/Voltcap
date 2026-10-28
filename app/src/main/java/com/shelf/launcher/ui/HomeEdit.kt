package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.height
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.Features
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.layout.layout
import androidx.compose.ui.zIndex

/**
 * Where moved home widgets sit (Features.widgetPositions: "id=x,y;…", fractions of the free room
 * inside the safe zone). Any home widget can use it: give it an id, wrap it in [MovableWidget].
 */
object HomeWidgets {
    const val MUSIC = "music"
    const val SHORTCUTS = "shortcuts"
    /** The game header above the row (title, stat boxes, Play and round buttons). */
    const val HEADER = "header"

    // Update 90: every part of the Voltcap row's home can be moved on its own — the header's pieces, the category
    // name, the game info card and the news card (they sit in the layout until moved; see FlowSlot).
    const val H_TITLE = "h_title"
    const val H_LINE = "h_line"
    const val H_STATS = "h_stats"
    const val H_ACTIONS = "h_actions"
    const val H_BADGE = "h_badge"
    const val CATEGORY = "category"
    const val INFO = "info"
    const val NEWS = "news"
    /** Update 104: the game's logo (no backing: it sits right on the art) and its description, on any layout. */
    const val LOGO = "logo"
    const val DESC = "desc"
    /** Update 116: Play / Continue on its own, the round buttons on their own (both still), and the achievements card. */
    const val H_PLAY = "h_play"
    const val H_BUTTONS = "h_buttons"
    const val ACH = "ach"
    /** Update 119: the achievements progress bar and the game's award badge, on their own, anywhere. */
    const val PROGRESS = "progress"
    const val AWARD = "award"
    /** Update 125: the game's logo from one source only — SteamGridDB's logo, or RetroAchievements' game icon. */
    const val LOGO_SGDB = "logo_sgdb"
    const val LOGO_RA = "logo_ra"

    /** The header's own parts (moved on their own, or with the header while they stay in it). */
    val headerParts = listOf(H_TITLE, H_LINE, H_STATS, H_ACTIONS, H_BADGE)

    /** Things that live in the home's layout until moved (FlowSlot), drawn floating by the home's float layer. */
    val flowIds = listOf(HEADER) + headerParts + listOf(CATEGORY, INFO, NEWS)

    /** Widgets that can be moved, in the order shoulder buttons step through them. */
    val all = listOf(MUSIC, SHORTCUTS, HEADER) + headerParts + listOf(CATEGORY, INFO, NEWS)

    // (Update 325, Toni: "some things in Edit Home won't scale down lower than a certain size": 60 % was the floor — 30 % now.)
    const val SCALE_MIN = 0.3f
    const val SCALE_MAX = 1.8f

    fun parseScales(s: String, clamp: Boolean = true): Map<String, Float> = s.split(';').mapNotNull { part ->
        val i = part.indexOf('=')
        if (i <= 0) return@mapNotNull null
        val v = part.substring(i + 1).toFloatOrNull() ?: return@mapNotNull null
        part.substring(0, i) to if (clamp) v.coerceIn(SCALE_MIN, SCALE_MAX) else v
    }.toMap()

    fun formatScales(m: Map<String, Float>): String =
        m.entries.filter { kotlin.math.abs(it.value - 1f) > 0.01f }.joinToString(";") { "${it.key}=${"%.2f".format(java.util.Locale.US, it.value)}" }

    /** The size of [id] (1 = normal; reads Compose state). While editing, the live value. */
    fun scale(id: String): Float =
        if (HomeEditState.active) HomeEditState.liveScale[id] ?: 1f else parseScales(Features.widgetScales.state.value)[id] ?: 1f

    // Update 133: stretched wider / taller on its own (the side and bottom handles), what's inside stretching with it.
    const val STRETCH_MIN = 0.5f
    const val STRETCH_MAX = 2.5f

    fun parseStretch(s: String): Map<String, Pair<Float, Float>> = s.split(';').mapNotNull { part ->
        val i = part.lastIndexOf('=')
        if (i <= 0) return@mapNotNull null
        val xy = part.substring(i + 1).split(',')
        val x = xy.getOrNull(0)?.toFloatOrNull() ?: return@mapNotNull null
        val y = xy.getOrNull(1)?.toFloatOrNull() ?: return@mapNotNull null
        part.substring(0, i) to (x.coerceIn(STRETCH_MIN, STRETCH_MAX) to y.coerceIn(STRETCH_MIN, STRETCH_MAX))
    }.toMap()

    fun formatStretch(m: Map<String, Pair<Float, Float>>): String =
        m.entries.filter { kotlin.math.abs(it.value.first - 1f) > 0.01f || kotlin.math.abs(it.value.second - 1f) > 0.01f }
            .joinToString(";") { "${it.key}=${"%.2f".format(java.util.Locale.US, it.value.first)},${"%.2f".format(java.util.Locale.US, it.value.second)}" }

    /** How far [id] is stretched across and down (1, 1 = as it is; reads Compose state). While editing, the live value. */
    fun stretch(id: String): Pair<Float, Float> =
        (if (HomeEditState.active) HomeEditState.liveStretch[id] else parseStretch(Features.widgetStretch.state.value)[id]) ?: (1f to 1f)

    /** The widgets on screen right now (the home screen sets it): L1 / R1 in Edit Home step through these. */
    var present by mutableStateOf(setOf(MUSIC))

    /** A part shown on another layout ("HERO:h_stats", update 93): its own place there, the same part. */
    fun overlayId(layout: com.shelf.launcher.data.HomeLayout, part: String) = "${layout.name}:$part"
    fun baseId(id: String): String = id.substringAfter(':')

    /** Update 202: the Voltcap row's game you're on, by the look that's on (Edit Home's name for it). */
    fun mediaLabel(): String = when (HomeEditState.artLook) {
        ArtPlace.Look.COVER -> "Cover you're on"
        ArtPlace.Look.BOX -> "Box you're on"
        ArtPlace.Look.CART -> "Cartridge you're on"
        ArtPlace.Look.DISC -> "Disc you're on"
        null -> "Game you're on"
    }

    fun label(id: String): String = when (baseId(id)) {
        MUSIC -> "Music player"
        SHORTCUTS -> "Round shortcuts"
        HEADER -> "Game header (all)"
        H_TITLE -> "Game title"
        H_LINE -> "Continue line"
        H_STATS -> "Stat boxes"
        H_ACTIONS -> "Play & buttons"
        H_BADGE -> "Console badge"
        CATEGORY -> "Category name"
        INFO -> "Game info"
        NEWS -> "News"
        LOGO -> "Game logo"
        DESC -> "Description"
        H_PLAY -> "Play / Continue"
        H_BUTTONS -> "Round buttons"
        ACH -> "Achievements"
        PROGRESS -> "Achievement progress bar"
        AWARD -> "Award badge"
        LOGO_SGDB -> "Logo (SteamGridDB)"
        LOGO_RA -> "Logo (RetroAchievements)"
        else -> if (id.startsWith(LayoutParts.PREFIX)) LayoutParts.registered[id] ?: id.substringAfterLast(':') else baseId(id)
    }

    /** Parts taken off the home screen with their ✕ in Edit Home (update 104; brought back with + Add). Reads state. */
    fun hidden(id: String): Boolean = id in Features.hiddenParts.state.value

    /**
     * Takes [id] off the home screen (its red ✕ in Edit Home): a part added to this layout is taken out of it, any
     * other part is hidden until it's brought back (Edit Home › + Add).
     */
    fun remove(id: String) {
        if (id.startsWith(LayoutParts.PREFIX)) {
            // A layout's own part: hidden (its place stays, so the layout doesn't jump), brought back with + Add.
            Features.hiddenParts.value = Features.hiddenParts.value + id
            return
        }
        val layout = id.substringBefore(':', "").let { n -> com.shelf.launcher.data.HomeLayout.values().firstOrNull { it.name == n } }
        if (layout != null) {
            if (baseId(id) in Features.overlayPartsFor(layout)) Features.toggleOverlayPart(layout, baseId(id))
            // (Update 212: the category title taken off is remembered, so the layout's fallback title (CategoryTitle.kt)
            // doesn't bring it back on its own; + Add › Taken off, or Elements › Show, puts it back.)
            if (baseId(id) == CATEGORY) Features.hiddenParts.value = Features.hiddenParts.value + id
        } else Features.hiddenParts.value = Features.hiddenParts.value + id
        HomeEditState.live.remove(id); HomeEditState.liveScale.remove(id); HomeEditState.liveStretch.remove(id)
        HomeEditState.selected =HomeWidgets.present.firstOrNull { it != id } ?: MUSIC
    }

    fun restore(id: String) { Features.hiddenParts.value = Features.hiddenParts.value - id }

    fun parse(s: String): Map<String, Pair<Float, Float>> = s.split(';').mapNotNull { part ->
        val i = part.indexOf('=')
        if (i <= 0) return@mapNotNull null
        val xy = part.substring(i + 1).split(',')
        val x = xy.getOrNull(0)?.toFloatOrNull() ?: return@mapNotNull null
        val y = xy.getOrNull(1)?.toFloatOrNull() ?: return@mapNotNull null
        part.substring(0, i) to (x.coerceIn(0f, 1f) to y.coerceIn(0f, 1f))
    }.toMap()

    fun format(m: Map<String, Pair<Float, Float>>): String =
        m.entries.joinToString(";") { "${it.key}=${"%.4f".format(java.util.Locale.US, it.value.first)},${"%.4f".format(java.util.Locale.US, it.value.second)}" }

    /** The stored position of [id] (reads Compose state), or null = its normal spot. */
    fun position(id: String): Pair<Float, Float>? = parse(Features.widgetPositions.state.value)[id]

    fun set(id: String, fx: Float, fy: Float) {
        Features.widgetPositions.value = format(parse(Features.widgetPositions.value) + (id to (snap(fx) to snap(fy))))
    }

    /** Reset Position: back to its normal spot. */
    fun reset(id: String) { Features.widgetPositions.value = format(parse(Features.widgetPositions.value) - id) }

    /** Reset Layout: every widget back to its normal spot and size. */
    fun resetAll() { Features.widgetPositions.value = ""; Features.widgetScales.value = ""; Features.widgetWidths.value = ""; Features.partShifts.value = ""; Features.widgetStretch.value = "" }

    /** The width a moved part keeps (its width where it sat in the layout, unscaled dp), or null. */
    fun width(id: String): Dp? =
        (if (HomeEditState.active) HomeEditState.flowWidth[id] else null)
            ?: parseScales(Features.widgetWidths.state.value, clamp = false)[id]?.takeIf { it > 0f }?.dp
            ?: HomeEditState.flowWidth[id]

    /** (Update 241, #05: the 2 % grid a dragged part settles on when nothing else is close, and the D-pad's step.) */
    const val GRID = 0.02f

    fun grid(f: Float): Float = ((f / GRID).roundToInt() * GRID).coerceIn(0f, 1f)

    /** Snaps to the edges, thirds and the middle when close. */
    fun snap(f: Float): Float {
        val targets = floatArrayOf(0f, 1f / 3f, 0.5f, 2f / 3f, 1f)
        val near = targets.minByOrNull { abs(it - f) } ?: f
        return if (abs(near - f) < 0.035f) near else f.coerceIn(0f, 1f)
    }
}

/**
 * Edit Home mode (from the home Options menu or Settings › Appearance › Music player): move widgets with
 * touch (drag) or the controller (D-pad moves the selected one, L1 / R1 pick another, X resets it,
 * Y resets the whole layout, A or B finishes). Never bound to R1 on its own.
 */
/**
 * Update 125: how high the floating parts are drawn: over the games (they used to be drawn under them when they came
 * first), but under the game popup, the category screen, the screensaver… (0 while one of those is up, set by the home
 * screen; the order they're drawn in decides then).
 */
internal object PartsLayer {
    var z by androidx.compose.runtime.mutableFloatStateOf(6f)
}

object HomeEditState {
    var active by mutableStateOf(false)
    /** The layout being edited (set by the home screen), for + Add. */
    var layout by mutableStateOf(com.shelf.launcher.data.HomeLayout.CLASSIC)
    /** The + Add list is open (update 104). */
    var adding by mutableStateOf(false)
    /** Update 116: the part whose ✎ Style sheet is open. */
    var styling by mutableStateOf<String?>(null)
    /** Update 116: while dragging, the axes the part snapped to (alignment guides): x / y fraction or null. */
    var guideX by mutableStateOf<Float?>(null)
    var guideY by mutableStateOf<Float?>(null)
    var selected by mutableStateOf(HomeWidgets.MUSIC)
    /** The next (1) or previous (-1) part to pick — L1 / R1, and (update 325) the toolbar's ◀ ▶. */
    fun pickStep(dir: Int) {
        val list = HomeEditSession.cycleList()
        val i = list.indexOf(selected).coerceAtLeast(0)
        val n = list.size
        if (n > 0) selected = list[((i + dir) % n + n) % n]
    }
    /** Update 325: what a two-finger pinch does to each part shown (by its id) — HomeEditPinch calls the picked one's. */
    val pinchers = HashMap<String, (Float) -> Unit>()
    /** Update 125: the Edit Home bar folded away to the left (-1) or right (1); 0 = open. */
    var barFolded by mutableStateOf(0)
    /**
     * Update 202: the look Art position works on for the games on this home screen now (Box art, covers, cartridges,
     * discs — set by the home screen), or null where there's none: Edit Home's R3 / "Art position" opens it.
     */
    internal var artLook by mutableStateOf<ArtPlace.Look?>(null)
    /**
     * Update 202: the part that owns the touch going on (a layout part inside another, like the game you're on inside the
     * game row: the innermost one under the finger — it sees the touch last, so it's the one written here).
     */
    internal var touchOwner: String? = null
    /** Update 212: Preview — Edit Home's frames, handles and bars are hidden until any button or a tap. */
    var preview by mutableStateOf(false)
    /** Update 212: the Elements list is open. */
    var elements by mutableStateOf(false)
    /** Update 212: Save / Discard / Keep editing is asked (leaving with changes). */
    var leaving by mutableStateOf(false)
    /** Update 212: the inspector's group (0 Position & size, 1 Appearance, 2 Visibility, 3 More). */
    var inspectorTab by mutableStateOf(0)
    /** Update 212: where each editable part is on the screen (root px), for the touch shield, Elements and the panels. */
    internal val bounds = HashMap<String, androidx.compose.ui.geometry.Rect>()
    /** Update 212: Edit Home's own bars on the home screen (root px): touches there aren't shielded. */
    internal val chrome = HashMap<String, androidx.compose.ui.geometry.Rect>()
    /** Update 212: gives the home screen the keys back once Edit Home's panels close (set by the home screen). */
    var refocus: (() -> Unit)? = null
    /** Update 212: the default place of a part not moved yet (set by the home screen), for the inspector's Move. */
    internal var defaultOf: ((String) -> Pair<Float, Float>)? = null
    /**
     * (Update 241, #05) Where each floating part is drawn now (fractions), moved or not: the D-pad and Line up start from
     * there (a part not moved yet used to start from the music player's corner, or the middle).
     */
    internal val shownAt = HashMap<String, Pair<Float, Float>>()

    /** (Update 241, #05) Where [id] is now: moved, drawn, or its default place. */
    internal fun placeOf(id: String): Pair<Float, Float> = live[id] ?: shownAt[id] ?: defaultOf?.invoke(id) ?: (0.5f to 0.5f)

    /** Update 202: opens Art position (HomeAdjust.kt) for [artLook], over Edit Home. */
    fun openArtPosition() {
        val look = artLook ?: return
        // (Update 212: one step of Undo; Discard puts it back too.)
        HomeEditSession.push("art")
        HomeAdjust.open = HomeAdjust.Target(HomeAdjust.Kind.ART_DROP, look = look)
    }

    /**
     * Update 125: where part [id] (a [w]×[h] box at fractions [raw]) lines up with another moved part — the same left /
     * right edge or middle, or right beside it — within a few dp: the fraction to use and which of its edges lines up
     * (0 left / top, 0.5 middle, 1 right / bottom), per axis; null where nothing is close.
     */
    fun snapToOthers(id: String, raw: Pair<Float, Float>, zone: SafeZone, w: Dp, h: Dp, density: androidx.compose.ui.unit.Density): Pair<Pair<Float, Float>?, Pair<Float, Float>?> {
        val gap = 8.dp
        val reach = 10.dp
        val freeW = (zone.usableWidth - w).coerceAtLeast(1.dp)
        val freeH = (zone.usableHeight - h).coerceAtLeast(1.dp)
        val x = zone.left + freeW * raw.first
        val y = zone.top + freeH * raw.second
        var bestX: Pair<Dp, Float>? = null
        var bestY: Pair<Dp, Float>? = null
        fun tryX(cand: Dp, edge: Float) { val d = abs((cand - x).value); if (d <= reach.value && (bestX == null || d < abs((bestX!!.first - x).value))) bestX = cand to edge }
        fun tryY(cand: Dp, edge: Float) { val d = abs((cand - y).value); if (d <= reach.value && (bestY == null || d < abs((bestY!!.first - y).value))) bestY = cand to edge }
        live.forEach { (other, f) ->
            if (other == id || HomeWidgets.hidden(other)) return@forEach
            val px = sizes[other] ?: return@forEach
            val ow = with(density) { px.width.toDp() }
            val oh = with(density) { px.height.toDp() }
            val o = zone.place(f.first, f.second, ow, oh)
            tryX(o.x, 0f); tryX(o.x + ow - w, 1f); tryX(o.x + ow / 2 - w / 2, 0.5f)
            tryX(o.x + ow + gap, 0f); tryX(o.x - w - gap, 1f)
            tryY(o.y, 0f); tryY(o.y + oh - h, 1f); tryY(o.y + oh / 2 - h / 2, 0.5f)
            tryY(o.y + oh + gap, 0f); tryY(o.y - h - gap, 1f)
        }
        val fx = bestX?.let { (((it.first - zone.left) / freeW).coerceIn(0f, 1f)) to it.second }
        val fy = bestY?.let { (((it.first - zone.top) / freeH).coerceIn(0f, 1f)) to it.second }
        return fx to fy
    }
    /** Positions while editing (fractions), written to Features when done. */
    internal val live = mutableStateMapOf<String, Pair<Float, Float>>()
    /** How far the layouts' own parts are moved while editing (dp), written to Features when done (update 108). */
    internal val liveShift = mutableStateMapOf<String, Pair<Float, Float>>()
    /** Sizes while editing (1 = normal), written to Features when done. */
    internal val liveScale = mutableStateMapOf<String, Float>()
    /** Update 133: stretches while editing (across, down; 1 = as it is), written to Features when done. */
    internal val liveStretch = mutableStateMapOf<String, Pair<Float, Float>>()

    /** Update 133: one part's place, size and stretch back to normal (X, or Reset position). */
    fun resetOne(id: String) { live.remove(id); liveScale.remove(id); liveShift.remove(id); liveStretch.remove(id) }


    /** Update 133: stretches [id] by a drag of [dx] / [dy] px over a part [w] × [h] px big (either can be 0). */
    fun stretchBy(id: String, dx: Float, dy: Float, w: Int, h: Int) {
        val now = liveStretch[id] ?: (1f to 1f)
        val nx = if (dx != 0f) (now.first * (1f + dx / kotlin.math.max(1, w))).coerceIn(HomeWidgets.STRETCH_MIN, HomeWidgets.STRETCH_MAX) else now.first
        val ny = if (dy != 0f) (now.second * (1f + dy / kotlin.math.max(1, h))).coerceIn(HomeWidgets.STRETCH_MIN, HomeWidgets.STRETCH_MAX) else now.second
        liveStretch[id] = nx to ny
    }
    /** Measured sizes of the widgets in px, and the zone, for controller steps. */
    internal val sizes = HashMap<String, IntSize>()
    /** Where each FlowSlot sits in the layout (root px) and its unscaled width (dp): a moved part starts from there. */
    internal val flowRect = mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>()
    internal val flowWidth = mutableStateMapOf<String, Dp>()

    fun open() {
        // (Update 212: already editing — an interruption, or Edit Home asked for again: the session carries on as it was.)
        if (active) return
        liveShift.clear()
        liveShift.putAll(LayoutParts.parse(Features.partShifts.value))
        live.clear()
        live.putAll(HomeWidgets.parse(Features.widgetPositions.value))
        liveScale.clear()
        liveScale.putAll(HomeWidgets.parseScales(Features.widgetScales.value))
        liveStretch.clear()
        liveStretch.putAll(HomeWidgets.parseStretch(Features.widgetStretch.value))
        selected = HomeWidgets.all.firstOrNull { it in HomeWidgets.present } ?: HomeWidgets.present.firstOrNull() ?: HomeWidgets.all.first()
        preview = false; elements = false; leaving = false; adding = false; styling = null; inspectorTab = 0
        bounds.clear(); chrome.clear()
        aDown = false; aUsed = false; HomeNest.dragging = null; HomeNest.hover = null
        active = true
        // Update 212: an edit session — Undo, Discard, Save (HomeEditSession.kt).
        HomeEditSession.begin()
    }

    fun done() {
        // Moved parts keep the width they had in the layout (a title or stat row would otherwise stretch or wrap).
        val widths = HomeWidgets.parseScales(Features.widgetWidths.value, clamp = false).toMutableMap()
        live.keys.forEach { id -> flowWidth[id]?.let { widths[id] = it.value } }
        Features.widgetWidths.value = widths.entries.joinToString(";") { "${it.key}=${"%.1f".format(java.util.Locale.US, it.value)}" }
        Features.widgetPositions.value = HomeWidgets.format(live.toMap())
        Features.widgetScales.value = HomeWidgets.formatScales(liveScale.toMap())
        Features.widgetStretch.value = HomeWidgets.formatStretch(liveStretch.toMap())
        Features.partShifts.value = LayoutParts.format(liveShift.toMap())
        close()
    }

    /** Update 212: Edit Home closes (after Done, or Discard): its panels with it. */
    internal fun close() {
        preview = false; elements = false; leaving = false; adding = false; styling = null
        guideX = null; guideY = null
        aDown = false; aUsed = false; HomeNest.dragging = null; HomeNest.hover = null
        active = false
    }

    /** Update 212: moves [id] one step ([dx], [dy]: -1, 0 or 1) — the D-pad, and the inspector's Move. One step of Undo. */
    fun nudge(id: String, dx: Int, dy: Int, fast: Boolean = false) {
        HomeEditSession.push("move:$id")
        // (Update 241, #05: on the 2 % grid — two steps at a time while the button is held — never pulled back to an
        // edge or the middle on the way (the step used to have to beat that pull); a part inside a hollow one moves
        // inside it, #06.)
        if (HomeNest.nudge(id, dx, dy, fast)) return
        val step = if (fast) HomeWidgets.GRID * 2 else HomeWidgets.GRID
        if (id.startsWith(LayoutParts.PREFIX)) {
            // A layout's own part moves in 8 dp steps from its place.
            val s = liveShift[id] ?: (0f to 0f)
            liveShift[id] = (s.first + dx * 8f) to (s.second + dy * 8f)
            return
        }
        val cur = placeOf(id)
        live[id] = HomeWidgets.grid(cur.first + dx * step) to HomeWidgets.grid(cur.second + dy * step)
    }

    /**
     * (Update 241, #05) Hold A + the D-pad: ▲ ▼ make the selected part bigger / smaller, ◀ ▶ narrower / wider (its
     * stretch). One step of Undo while it's held.
     */
    fun resizeStep(id: String, dx: Int, dy: Int) {
        if (dy != 0) {
            HomeEditSession.push("size:$id")
            val now = liveScale[id] ?: 1f
            liveScale[id] = (now - dy * 0.05f).coerceIn(HomeWidgets.SCALE_MIN, HomeWidgets.SCALE_MAX)
        }
        if (dx != 0) {
            HomeEditSession.push("stretch:$id")
            val now = liveStretch[id] ?: (1f to 1f)
            liveStretch[id] = (now.first + dx * 0.05f).coerceIn(HomeWidgets.STRETCH_MIN, HomeWidgets.STRETCH_MAX) to now.second
        }
    }

    /** (Update 241, #05) A is held down (hold A + D-pad resizes; A let go without that opens the inspector). */
    private var aDown = false
    private var aUsed = false

    /**
     * Controller keys while editing. Always consumes them (the home screen stays still meanwhile: no game starts, the
     * row doesn't move). Update 212: A adjusts (the inspector), B goes back a step (Save / Discard / Keep editing at the
     * top), Start is Done, Y is Undo, Select hides / shows, X resets the part; L3 (Elements) and R3 (Art position) are
     * shortcuts only — both are in the inspector too.
     */
    fun onKey(e: KeyEvent, defaultOf: (String) -> Pair<Float, Float>): Boolean {
        this.defaultOf = defaultOf
        if (preview) {
            // Any button ends Preview (and does nothing else).
            if (e.type == KeyEventType.KeyDown) preview = false
            return true
        }
        // (Update 241, #05: A acts when it's let go — held with the D-pad it resizes instead of opening the inspector.)
        if (e.key == Key.ButtonA || e.key == Key.Enter || e.key == Key.DirectionCenter) {
            if (e.type == KeyEventType.KeyDown) { if (!aDown) { aDown = true; aUsed = false } }
            else if (e.type == KeyEventType.KeyUp) {
                // (Only for an A pressed here: one let go after a panel closed on its press does nothing.)
                val mine = aDown && !aUsed
                aDown = false; aUsed = false
                if (mine) styling = selected
            }
            return true
        }
        if (e.type != KeyEventType.KeyDown) return true
        val fast = e.nativeKeyEvent.repeatCount > 2
        val dir = when (e.key) {
            Key.DirectionLeft -> -1 to 0
            Key.DirectionRight -> 1 to 0
            Key.DirectionUp -> 0 to -1
            Key.DirectionDown -> 0 to 1
            else -> null
        }
        if (dir == null) { aDown = false; aUsed = false }
        if (dir != null && aDown) { aUsed = true; resizeStep(selected, dir.first, dir.second); return true }
        when (e.key) {
            Key.DirectionLeft -> nudge(selected, -1, 0, fast)
            Key.DirectionRight -> nudge(selected, 1, 0, fast)
            Key.DirectionUp -> nudge(selected, 0, -1, fast)
            Key.DirectionDown -> nudge(selected, 0, 1, fast)
            Key.ButtonL1, Key.ButtonR1 -> pickStep(if (e.key == Key.ButtonR1) 1 else -1)
            // L2 / R2: smaller / bigger.
            Key.ButtonL2, Key.ButtonR2 -> {
                HomeEditSession.push("size:$selected")
                val now = liveScale[selected] ?: 1f
                liveScale[selected] = (now + if (e.key == Key.ButtonR2) 0.1f else -0.1f).coerceIn(HomeWidgets.SCALE_MIN, HomeWidgets.SCALE_MAX)
            }
            Key.ButtonX -> HomeEditSession.resetSelected(selected)
            Key.ButtonY -> HomeEditSession.undo()
            Key.ButtonSelect -> HomeEditSession.toggleShown(selected)
            Key.ButtonStart -> HomeEditSession.save()
            Key.ButtonThumbLeft -> elements = true
            Key.ButtonThumbRight -> openArtPosition()
            Key.ButtonB, Key.Back, Key.Escape -> HomeEditSession.back()
            else -> Unit
        }
        return true
    }
}

/**
 * A home widget that can be moved in Edit Home. Unmoved and not editing it sits where it always did
 * ([defaultAlign] + [defaultPadding]); once moved it sits at its stored spot inside the safe zone.
 */
@Composable
internal fun BoxScope.MovableWidget(
    id: String,
    defaultAlign: Alignment,
    defaultPadding: PaddingValues,
    /** Where it starts when it hasn't been moved (fractions), e.g. its place in the layout (FlowSlot); null = [defaultAlign]. */
    defaultFrac: Pair<Float, Float>? = null,
    /** Lays the content out at this width (unscaled), e.g. the width it had in the layout. */
    fixedWidth: Dp? = null,
    /**
     * Update 258 (#120, RP Classic): parts drawn in the same layer that this one, while it hasn't been moved, steps
     * out of the way of — just above or below them, whichever is nearer. The category title came back at its layout's
     * free spot right on top of an achievements card you had put there.
     */
    keepClearOf: List<String> = emptyList(),
    /**
     * Update 359 (D67): a part that must not cover the games row's name line (the game logo). Where you put it is kept;
     * only when its lower edge reaches into the row (a shorter screen than the one it was placed on) it's drawn smaller,
     * its top where it was, so it ends just above the row (down to half its size). Not in Edit Home: there it shows as it is.
     */
    clearOfDock: Boolean = false,
    content: @Composable () -> Unit
) {
    val editing = HomeEditState.active
    // Taken off with its ✕ (update 104): not drawn until brought back.
    if (HomeWidgets.hidden(id)) return
    val stored = if (editing) HomeEditState.live[id] else HomeWidgets.position(id)
    var size by remember { mutableStateOf(IntSize.Zero) }
    val zone = safeZone()
    val density = LocalDensity.current
    val scale = HomeWidgets.scale(id)
    // Update 133: stretched wider / taller on its own (what's inside stretches with it).
    val (sx, sy) = HomeWidgets.stretch(id)
    // (Update 241, #06: inside a hollow part — its middle kept where it was dropped in that part's inner area, so it
    // moves with it; not while it's being dragged, nor while that part isn't on the screen.)
    val nestedAt = if (HomeNest.dragging != id) HomeNest.centreOf(id) else null
    // Where the layer it's drawn in starts on the screen (root px), to put a nested part at a screen place.
    var origin by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    if (stored == null && !editing && defaultFrac == null && nestedAt == null) {
        // (Update 125: the parts you add are always drawn over the games, never under them.)
        Box(Modifier.align(defaultAlign).zIndex(PartsLayer.z).padding(defaultPadding).onSizeChanged { size = it; HomeEditState.sizes[id] = it }.rowRevealFade()) {
            Box(Modifier.stretched(sx, sy)) { ScaledWidget(scale) { StyledPart(id, content) } }
        }
        return
    }
    val w = with(density) { size.width.toDp() }
    val h = with(density) { size.height.toDp() }
    // (Update 258: not moved yet, and its spot is taken by a part you placed there — just above or below that part.)
    val clearOf = if (keepClearOf.isEmpty() || stored != null || nestedAt != null || size == IntSize.Zero) null
        else PartRects.clearSpot(defaultFrac ?: defaultFractions(defaultAlign), zone, w, h, keepClearOf.mapNotNull { PartRects.at[it] })
    // Where it is now: the stored fractions, or (first time in edit mode) its normal corner.
    val f = stored ?: clearOf ?: defaultFrac ?: defaultFractions(defaultAlign)
    val placed = zone.place(f.first, f.second, w, h)
    val pos = if (nestedAt != null) with(density) {
        val o = origin ?: androidx.compose.ui.geometry.Offset.Zero
        androidx.compose.ui.unit.DpOffset((nestedAt.x - o.x).toDp() - w / 2, (nestedAt.y - o.y).toDp() - h / 2)
    } else placed
    val posNow by androidx.compose.runtime.rememberUpdatedState(pos)
    if (editing) androidx.compose.runtime.SideEffect { HomeEditState.shownAt[id] = if (nestedAt != null) zone.fractionOf(pos.x, pos.y, w, h) else f }
    // (Update 258: where it's drawn in its layer, for the parts that keep clear of it; written only when it changed.)
    if (size != IntSize.Zero) {
        val r = androidx.compose.ui.geometry.Rect(pos.x.value, pos.y.value, (pos.x + w).value, (pos.y + h).value)
        androidx.compose.runtime.SideEffect { if (PartRects.at[id] != r) PartRects.at[id] = r }
    }
    androidx.compose.runtime.DisposableEffect(id) { onDispose { PartRects.at.remove(id) } }
    // Update 144: the drag reads these as they are now, so it isn't restarted (and stopped under the finger) when
    // the part changes size while it's being moved.
    val wNow by androidx.compose.runtime.rememberUpdatedState(w)
    val hNow by androidx.compose.runtime.rememberUpdatedState(h)
    val zoneNow by androidx.compose.runtime.rememberUpdatedState(zone)
    val fNow by androidx.compose.runtime.rememberUpdatedState(f)
    val selected = editing && HomeEditState.selected == id
    // Update 212: Preview hides the frames and handles; only the selected part has handles, the rest a faint outline.
    val chromeOn = editing && !HomeEditState.preview
    val shape = RoundedCornerShape(16.dp)
    val guideColor = ShelfTheme.accent
    /**
     * Update 179: after its size changed by [rx] across and [ry] down, moves it so one corner stays where it was: the top
     * right ([keepRight], the size handle) or the top left (the stretch handles) — it grows towards the handle.
     */
    fun keepCorner(rx: Float, ry: Float, keepRight: Boolean) {
        val z = zoneNow
        val f0 = HomeEditState.live[id] ?: fNow
        val o = z.place(f0.first, f0.second, wNow, hNow)
        val w2 = wNow * rx
        val h2 = hNow * ry
        val x2 = if (keepRight) o.x + wNow - w2 else o.x
        HomeEditState.live[id] = z.fractionOf(x2, o.y, w2, h2)
    }
    /** Update 325: after a pinch made it [r] times its size — its middle stays where it was. */
    fun keepMiddle(r: Float) {
        val z = zoneNow
        val f0 = HomeEditState.live[id] ?: fNow
        val o = z.place(f0.first, f0.second, wNow, hNow)
        val w2 = wNow * r
        val h2 = hNow * r
        HomeEditState.live[id] = z.fractionOf(o.x + (wNow - w2) / 2f, o.y + (hNow - h2) / 2f, w2, h2)
    }
    // Update 325: what a two-finger pinch does to this part while it's the one picked (HomeEditPinch).
    androidx.compose.runtime.DisposableEffect(id, editing) {
        if (editing) HomeEditState.pinchers[id] = { zoom ->
            val now = HomeEditState.liveScale[id] ?: 1f
            val next = (now * zoom).coerceIn(HomeWidgets.SCALE_MIN, HomeWidgets.SCALE_MAX)
            HomeEditState.liveScale[id] = next
            keepMiddle(next / now)
        }
        onDispose { HomeEditState.pinchers.remove(id) }
    }
    Box(
        Modifier
            .zIndex(if (PartsLayer.z > 0f && selected) PartsLayer.z + 2f else PartsLayer.z)
            .offset(pos.x, pos.y)
            .then(if (editing) Modifier else Modifier.rowRevealFade())
            .onSizeChanged { size = it; HomeEditState.sizes[id] = it }
            .onGloballyPositioned { c ->
                if (editing) HomeEditState.bounds[id] = c.boundsInRoot()
                // (Update 241, #06: written only when it changed.)
                val o = c.parentLayoutCoordinates?.takeIf { it.isAttached }?.localToRoot(androidx.compose.ui.geometry.Offset.Zero)
                if (o != null && o != origin) origin = o
            }
            .then(
                if (editing) Modifier
                    .then(if (chromeOn) Modifier.border(if (selected) 2.dp else 1.dp, if (selected) ShelfTheme.accent else ShelfTheme.accent.copy(alpha = 0.25f), shape) else Modifier)
                    // A tap picks it (then the D-pad, L2 / R2 and X work on it).
                    .pointerInput(id) { detectTapGestures { HomeEditState.selected = id } }
                    .pointerInput(id) {
                        // Update 116: it snaps while you drag (to the edges, thirds and the middle), with a guide line
                        // through it on the axis it snapped to; the finger's own place is followed underneath.
                        var raw = HomeEditState.live[id] ?: fNow
                        fun dropped() {
                            // (Update 241, #06: let go with its middle in a hollow part's inner area: it goes in (moves with
                            // it from now on); anywhere else it comes out.)
                            HomeEditState.bounds[id]?.let { HomeNest.drop(id, it.center) }
                            HomeNest.dragging = null; HomeNest.hover = null
                            HomeEditState.guideX = null; HomeEditState.guideY = null
                        }
                        // Update 325 (Toni: "sometimes I can't move things with a finger — it gets resized instead; maybe resize
                        // with two fingers"): one finger moves it, two fingers pinch it bigger or smaller (round its middle).
                        // The size handle is a tap now (its Style), and the stretch handles show only on a part big enough
                        // for them not to cover it.
                        fun dragStart() {
                                HomeEditState.selected = id; HomeEditSession.push("drag:$id", force = true)
                                // (Update 241, #06: a part inside a hollow one starts from where it's drawn now.)
                                if (HomeNest.centreOf(id) != null) HomeEditState.live[id] = zoneNow.fractionOf(posNow.x, posNow.y, wNow, hNow)
                                raw = HomeEditState.live[id] ?: fNow
                                HomeNest.dragging = id
                        }
                        fun dragBy(drag: androidx.compose.ui.geometry.Offset) {
                                val fw = (zoneNow.usableWidth - wNow).coerceAtLeast(1.dp).toPx()
                                val fh = (zoneNow.usableHeight - hNow).coerceAtLeast(1.dp).toPx()
                                raw = (raw.first + drag.x / fw).coerceIn(0f, 1f) to (raw.second + drag.y / fh).coerceIn(0f, 1f)
                                // Update 125: it snaps to the other parts too (lined up with their edges or middles, or
                                // right beside them), before the screen's edges, thirds and middle.
                                val near = HomeEditState.snapToOthers(id, raw, zoneNow, wNow, hNow, density)
                                val sx = near.first?.first ?: HomeWidgets.snap(raw.first)
                                val sy = near.second?.first ?: HomeWidgets.snap(raw.second)
                                HomeEditState.guideX = near.first?.second ?: sx.takeIf { it != raw.first }
                                HomeEditState.guideY = near.second?.second ?: sy.takeIf { it != raw.second }
                                // (Update 241, #05: on an axis nothing pulled it to, it settles on the 2 % grid.)
                                HomeEditState.live[id] = (if (near.first == null && sx == raw.first) HomeWidgets.grid(sx) else sx) to
                                    (if (near.second == null && sy == raw.second) HomeWidgets.grid(sy) else sy)
                                // (Update 241, #06: the hollow part its middle is over now, outlined.)
                                HomeNest.hover = HomeEditState.bounds[id]?.let { HomeNest.containerAt(id, it.center) }
                        }
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            // (Touching a part picks it — a pinch then sizes this one: HomeEditPinch.)
                            HomeEditState.selected = id
                            var moved = 0f
                            var started = false
                            var pinching = false
                            val slop = viewConfiguration.touchSlop
                            do {
                                val event = awaitPointerEvent()
                                val fingers = event.changes.count { it.pressed }
                                if (fingers >= 2) {
                                    // (Two fingers: the screen-wide pinch sizes it — HomeEditPinch; it stops moving.)
                                    if (!pinching) { pinching = true; if (started) { dropped(); started = false } }
                                } else if (fingers == 1 && !pinching) {
                                    val pan = event.calculatePan()
                                    if (!started) {
                                        moved += pan.getDistance()
                                        if (moved > slop) { started = true; dragStart() }
                                    }
                                    if (started) {
                                        dragBy(pan)
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                            if (started) dropped()
                        }
                    }
                    // The guide lines (across the whole screen) while it's snapped.
                    .then(if (selected) Modifier.drawWithContent {
                        drawContent()
                        val gx = HomeEditState.guideX
                        val gy = HomeEditState.guideY
                        val far = 6000f
                        val line = guideColor
                        if (gx != null) {
                            val x: Float = if (gx == 0f) 0f else if (gx == 1f) drawContext.size.width else drawContext.size.width / 2f
                            drawLine(line, androidx.compose.ui.geometry.Offset(x, -far), androidx.compose.ui.geometry.Offset(x, far), 1.5.dp.toPx(),
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                        }
                        if (gy != null) {
                            val y: Float = if (gy == 0f) 0f else if (gy == 1f) drawContext.size.height else drawContext.size.height / 2f
                            drawLine(line, androidx.compose.ui.geometry.Offset(-far, y), androidx.compose.ui.geometry.Offset(far, y), 1.5.dp.toPx(),
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                        }
                    } else Modifier)
                else Modifier
            )
    ) {
        // (Update 359, D67: drawn smaller when it would reach into the games row — see clearOfDock.)
        val dockTop = DockClear.topDp
        val fit = if (!clearOfDock || editing || dockTop <= 0f || size == IntSize.Zero) 1f else {
            val top = (origin?.y ?: 0f) / density.density + pos.y.value
            val bottom = top + h.value
            val room = dockTop - 6f - top
            if (bottom <= dockTop - 6f || room <= 0f) 1f else (room / h.value).coerceIn(0.5f, 1f)
        }
        // (Update 116: in its own style — backing, see-through, outline, shape, still.)
        Box(Modifier.stretched(sx, sy).then(if (fit < 1f) Modifier.graphicsLayer {
            scaleX = fit; scaleY = fit; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
        } else Modifier)) {
            ScaledWidget(scale) { StyledPart(id) { if (fixedWidth != null) Box(Modifier.width(fixedWidth)) { content() } else content() } }
        }
        if (selected && chromeOn) {
            // (Update 325, Toni: "sometimes I can't move things with a finger — it gets resized instead … the icons should
            // disappear, and what does what in the legend": no size and stretch handles any more — one finger moves a part,
            // two fingers pinch it bigger or smaller (HomeEditPinch), and its Adjust has the size and the stretch sliders.
            // The ✕ stays.)
            // The red ✕ (update 104): takes the part off the screen (+ Add brings it back). (Inside the part too.)
            // (Update 212: one step of Undo; Elements › Show brings it back too.)
            Box(
                Modifier.align(Alignment.TopEnd).size(26.dp).clip(RoundedCornerShape(50))
                    .background(androidx.compose.ui.graphics.Color(0xFFE5484D))
                    .border(1.5.dp, androidx.compose.ui.graphics.Color.White, RoundedCornerShape(50))
                    .pointerInput(id) { detectTapGestures { HomeEditSession.toggleShown(id) } },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Close, "Remove", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(14.dp))
            }
            // The name only on the one you've picked (with header pieces inside the header, several names sat on top of each other).
            if (selected) Row(
                Modifier.align(Alignment.TopStart).offset(y = (-17).dp).clip(RoundedCornerShape(50))
                    .background(if (selected) ShelfTheme.accent else ShelfTheme.colors.surfaceRaised)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.OpenWith, null, tint = if (selected) onAccentColor() else ShelfTheme.colors.textPrimary, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text(HomeWidgets.label(id), color = if (selected) onAccentColor() else ShelfTheme.colors.textPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Update 133: the content laid out as it is, then drawn [sx] times wider and [sy] times taller (from its top left), and
 * taking that much room — a stretched part, what's inside stretching with it.
 */
internal fun Modifier.stretched(sx: Float, sy: Float): Modifier =
    if (kotlin.math.abs(sx - 1f) < 0.01f && kotlin.math.abs(sy - 1f) < 0.01f) this
    else this.layout { m, c ->
        val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
        val w = (p.width * sx).roundToInt().coerceIn(c.minWidth, c.maxWidth)
        val h = (p.height * sy).roundToInt().coerceIn(c.minHeight, c.maxHeight)
        layout(w, h) {
            p.placeWithLayer(0, 0) {
                scaleX = sx; scaleY = sy
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
            }
        }
    }

/**
 * Update 133: a part's stretch handle, on its right edge ([across]) or its bottom edge: drag out to stretch it, in to
 * squeeze it. [sizeNow]: the part's size on screen.
 */
@Composable
internal fun BoxScope.StretchHandle(
    id: String, across: Boolean, selected: Boolean,
    /** Update 179: called with how much wider / taller it got (ratios), to keep an edge still. */
    onStretch: ((Float, Float) -> Unit)? = null,
    sizeNow: () -> IntSize
) {
    Box(
        Modifier.align(if (across) Alignment.CenterEnd else Alignment.BottomCenter)
            .size(if (across) 18.dp else 34.dp, if (across) 34.dp else 18.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) ShelfTheme.accent else ShelfTheme.colors.surfaceRaised)
            .border(1.5.dp, ShelfTheme.colors.textPrimary.copy(alpha = 0.6f), RoundedCornerShape(50))
            .pointerInput(id, across) {
                detectDragGestures(
                    onDragStart = { HomeEditState.selected = id; HomeEditSession.push("stretch:$id", force = true) },
                    onDrag = { change, drag ->
                        change.consume()
                        val s = sizeNow()
                        val before = HomeWidgets.stretch(id)
                        if (across) HomeEditState.stretchBy(id, drag.x, 0f, s.width, s.height)
                        else HomeEditState.stretchBy(id, 0f, drag.y, s.width, s.height)
                        val after = HomeWidgets.stretch(id)
                        onStretch?.invoke(after.first / before.first, after.second / before.second)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(if (across) "↔" else "↕", color = if (selected) onAccentColor() else ShelfTheme.colors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** The fractions matching a corner alignment (for widgets that haven't been moved yet). */
internal fun defaultFractions(a: Alignment): Pair<Float, Float> = when (a) {
    Alignment.TopStart -> 0f to 0.08f
    Alignment.TopEnd -> 1f to 0.08f
    Alignment.BottomStart -> 0f to 1f
    Alignment.BottomEnd -> 1f to 1f
    Alignment.Center -> 0.5f to 0.5f
    Alignment.BottomCenter -> 0.5f to 1f
    Alignment.TopCenter -> 0.5f to 0.08f
    Alignment.CenterStart -> 0f to 0.5f
    else -> 0f to 0f
}

/** What stands in for the music player in Edit Home when nothing is playing. */
@Composable
internal fun MusicPlayerPlaceholder() {
    Row(
        Modifier.width(240.dp).clip(RoundedCornerShape(16.dp)).background(ShelfTheme.colors.surface.copy(alpha = 0.85f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.MusicNote, null, tint = ShelfTheme.accent, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(10.dp))
        Text("Music player", color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Edit Home's bars and panels (update 212, HomeEditSession.kt): the toolbar (Elements · Add · Undo · Preview · Done) with
 * the button hints, the inspector of the selected part, the Elements list, + Add and Save / Discard / Keep editing.
 * Preview hides all of it. When every panel is closed the home screen gets the keys back (focus restored).
 */
@Composable
internal fun BoxScope.HomeEditBar() {
    if (!HomeEditState.active) return
    // Update 125: the bar folds away to the left or right with its arrows (a small tab brings it back), so nothing is
    // hidden under it while you place things at the bottom.
    val folded = HomeEditState.barFolded
    // (The bars' places, for the touch shield: only the one showing.)
    androidx.compose.runtime.SideEffect {
        if (HomeEditState.preview || folded != 0) HomeEditState.chrome.remove("bar")
        if (HomeEditState.preview || folded == 0) HomeEditState.chrome.remove("fold")
    }
    // Update 325: two fingers anywhere on the screen size the part picked (a part touched first is the one picked).
    if (!HomeEditState.preview) Box(Modifier.matchParentSize().then(HomeEditPinchElement))
    // (Update 241, #05: while a floating part is dragged, the grid it snaps to — faint lines every 10 % of the safe zone,
    // stronger at the thirds and the middle; no touches.)
    if (HomeNest.dragging != null && !HomeEditState.preview) {
        val zone = safeZone()
        val accent = ShelfTheme.accent
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            val l = zone.left.toPx(); val t = zone.top.toPx()
            val zw = zone.usableWidth.toPx(); val zh = zone.usableHeight.toPx()
            for (i in 1..9) {
                val k = i / 10f
                val c = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.07f)
                drawLine(c, androidx.compose.ui.geometry.Offset(l + zw * k, t), androidx.compose.ui.geometry.Offset(l + zw * k, t + zh), 1f)
                drawLine(c, androidx.compose.ui.geometry.Offset(l, t + zh * k), androidx.compose.ui.geometry.Offset(l + zw, t + zh * k), 1f)
            }
            for (k in floatArrayOf(1f / 3f, 0.5f, 2f / 3f)) {
                val c = accent.copy(alpha = if (k == 0.5f) 0.3f else 0.18f)
                drawLine(c, androidx.compose.ui.geometry.Offset(l + zw * k, t), androidx.compose.ui.geometry.Offset(l + zw * k, t + zh), 1.dp.toPx())
                drawLine(c, androidx.compose.ui.geometry.Offset(l, t + zh * k), androidx.compose.ui.geometry.Offset(l + zw, t + zh * k), 1.dp.toPx())
            }
            drawRect(accent.copy(alpha = 0.22f), androidx.compose.ui.geometry.Offset(l, t), androidx.compose.ui.geometry.Size(zw, zh), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        }
    }
    if (HomeEditState.preview) PreviewNote()
    else if (folded != 0) {
        Row(
            Modifier.align(if (folded < 0) Alignment.BottomStart else Alignment.BottomEnd).zIndex(9f).safeZonePadding()
                .onGloballyPositioned { HomeEditState.chrome["fold"] = it.boundsInRoot() }
                .clip(RoundedCornerShape(50)).background(ShelfTheme.colors.surfaceRaised.copy(alpha = 0.95f))
                .pointerInput(Unit) { detectTapGestures { HomeEditState.barFolded = 0 } }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (folded < 0) "Edit Home  ▶" else "◀  Edit Home", color = ShelfTheme.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    } else HomeEditToolbar() // (HomeEditLegend.kt)
    if (HomeEditState.adding) HomeAddSheet(HomeEditState.layout, onDismiss = { HomeEditState.adding = false })
    HomeEditState.styling?.let { id -> HomeInspector(id, onDismiss = { HomeEditState.styling = null }) }
    if (HomeEditState.elements) HomeElementsSheet(onDismiss = { HomeEditState.elements = false })
    if (HomeEditState.leaving) HomeLeavePrompt()
    val anyOpen = HomeEditState.adding || HomeEditState.styling != null || HomeEditState.elements || HomeEditState.leaving || HomeAdjust.open != null
    androidx.compose.runtime.LaunchedEffect(anyOpen) {
        if (!anyOpen) { kotlinx.coroutines.delay(60); HomeEditState.refocus?.invoke() }
    }
}

/**
 * Edit Home › + Add (update 104): a simple list of what can go on this layout's home screen — the game's logo, its
 * description, title, stat boxes, Play & buttons, console badge, category name, game info and news — and the parts
 * taken off with their ✕, to bring back. Everything added floats on its own and is moved / resized like the rest.
 */
@Composable
internal fun HomeAddSheet(layout: com.shelf.launcher.data.HomeLayout, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        ImmersiveDialogWindow()
        ShelfCard {
            SectionLabel("Add to the home screen")
            MutedNote("Pick what to show; it appears on this layout and can be moved and resized. Its red ✕ takes it off again.")
            Spacer(androidx.compose.ui.Modifier.height(8.dp))
            val on = Features.overlayPartsFor(layout)
            val first = remember { androidx.compose.ui.focus.FocusRequester() }
            androidx.compose.runtime.LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { first.requestFocus() } }
            ChipFlow {
                overlayChoices(layout).forEachIndexed { i, (id, label) ->
                    val sel = id in on
                    Box(if (i == 0) Modifier.focusRequester(first) else Modifier) {
                        Chip((if (sel) "✓ " else "+ ") + label, accent = sel) {
                            // (Update 212: one step of Undo; a category title taken off before comes back with it.)
                            HomeEditSession.push("add")
                            Features.toggleOverlayPart(layout, id)
                            if (!sel) HomeWidgets.restore(HomeWidgets.overlayId(layout, id))
                        }
                    }
                }
            }
            // (Update 202: only this layout's own parts and the ones every layout shares — another layout's parts aren't here.)
            val gone = Features.hiddenParts.state.value.filter { !it.startsWith(LayoutParts.PREFIX) || it.startsWith(LayoutParts.PREFIX + layout.name + ":") }
            if (gone.isNotEmpty()) {
                Spacer(androidx.compose.ui.Modifier.height(10.dp))
                SectionLabel("Taken off")
                Spacer(androidx.compose.ui.Modifier.height(6.dp))
                ChipFlow { gone.forEach { id -> Chip("↺ " + HomeWidgets.label(id), accent = false) { HomeEditSession.push("show"); HomeWidgets.restore(id) } } }
            }
            Spacer(androidx.compose.ui.Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth()) { Chip("Done", modifier = Modifier.align(Alignment.CenterEnd), onClick = onDismiss) }
        }
    }
}

/** What can be added to [layout]'s home screen (the Voltcap row has the header's parts built in: only logo and description). */
internal fun overlayChoices(layout: com.shelf.launcher.data.HomeLayout): List<Pair<String, String>> {
    val extra = listOf(
        HomeWidgets.LOGO to "Game logo", HomeWidgets.LOGO_SGDB to "Logo (SteamGridDB)", HomeWidgets.LOGO_RA to "Logo (RetroAchievements)",
        HomeWidgets.DESC to "Description",
        // Update 116.
        HomeWidgets.H_PLAY to "Play / Continue", HomeWidgets.H_BUTTONS to "Round buttons", HomeWidgets.ACH to "Achievements",
        // Update 119.
        HomeWidgets.PROGRESS to "Achievement progress bar", HomeWidgets.AWARD to "Award badge"
    )
    val rest = listOf(
        HomeWidgets.H_TITLE to "Title", HomeWidgets.H_LINE to "Continue line", HomeWidgets.H_STATS to "Stat boxes",
        HomeWidgets.H_ACTIONS to "Play & buttons", HomeWidgets.H_BADGE to "Console badge", HomeWidgets.CATEGORY to "Category name",
        HomeWidgets.INFO to "Game info", HomeWidgets.NEWS to "News"
    )
    // Update 202: the Voltcap row offers its own cards too — the news bar, game info, category name and the header's
    // pieces — whenever they aren't in its layout now (News off in Next to the row, the game header off, …); added, they
    // float like on any other layout. One that's in the layout already isn't offered twice.
    if (layout == com.shelf.launcher.data.HomeLayout.CLASSIC) {
        val added = Features.overlayPartsFor(layout)
        // (Not the category name: it's only missing there when Categories is off, and then it has nothing to show.)
        return extra + rest.filter { it.first != HomeWidgets.CATEGORY && (it.first !in HomeWidgets.present || it.first in added) }
    }
    return extra + rest
}

/**
 * [content] at [scale] times its size: laid out again at that size (a bigger density), so text and touch areas grow
 * with it instead of a blown-up picture of it.
 */
@Composable
internal fun ScaledWidget(scale: Float, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    if (kotlin.math.abs(scale - 1f) < 0.01f) content()
    else androidx.compose.runtime.CompositionLocalProvider(
        LocalDensity provides androidx.compose.ui.unit.Density(d.density * scale, d.fontScale)
    ) { content() }
}


/**
 * A part of the home's layout that can be moved in Edit Home (update 90). Not moved: drawn here as usual (and its place
 * remembered). Moved: nothing here, the home's float layer draws it where it was put. While editing: an empty space of
 * its size here (the layout doesn't jump), and the float layer draws it on top, starting from this place.
 */
@Composable
internal fun FlowSlot(id: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val editing = HomeEditState.active
    if (HomeWidgets.hidden(id)) return
    if (!editing && HomeWidgets.position(id) != null) return
    if (editing && HomeWidgets.position(id) != null) return
    val rect = HomeEditState.flowRect[id]
    val density = LocalDensity.current
    // Written only when it really changed (a state write every layout pass would keep recomposing its readers).
    val track = Modifier.onGloballyPositioned { c -> val r = c.boundsInRoot(); if (HomeEditState.flowRect[id] != r) HomeEditState.flowRect[id] = r }
    if (editing && rect != null && rect.width > 0f) {
        with(density) { Spacer(modifier.then(track).size(rect.width.toDp(), rect.height.toDp())) }
        return
    }
    // (Update 133: stretched too, where it sits in the layout.)
    val (sx, sy) = HomeWidgets.stretch(id)
    Box(modifier.then(track).stretched(sx, sy)) {
        ScaledWidget(HomeWidgets.scale(id)) {
            val d = LocalDensity.current
            Box(Modifier.onSizeChanged { val w = with(d) { it.width.toDp() }; if (HomeEditState.flowWidth[id] != w) HomeEditState.flowWidth[id] = w }) { content() }
        }
    }
}

/** Fractions (of the safe zone) for a part drawn floating at its place in the layout, relative to [origin] (root px). */
@Composable
internal fun flowFractions(id: String, origin: androidx.compose.ui.geometry.Offset): Pair<Float, Float>? {
    val r = HomeEditState.flowRect[id] ?: return null
    val zone = safeZone()
    return with(LocalDensity.current) {
        zone.fractionOf((r.left - origin.x).toDp(), (r.top - origin.y).toDp(), r.width.toDp(), r.height.toDp())
    }
}


/**
 * The layouts' own parts (update 108): the Channel grid's bar, the Spotlight title, the TV room's sticker and so on.
 * In Edit Home each has a frame and its red ✕: drag it (or pick it with L1 / R1 and use the D-pad) to move it from its
 * place, the ✕ (or Select) hides it, + Add brings it back. Its place in the layout stays, so nothing else moves.
 */
internal object LayoutParts {
    const val PREFIX = "part:"
    /** The parts on screen now: id → name. */
    val registered = mutableStateMapOf<String, String>()
    /** Update 202: how many times each of them is drawn now (see editablePart). */
    internal val uses = HashMap<String, Int>()

    fun id(layout: com.shelf.launcher.data.HomeLayout, name: String) = "$PREFIX${layout.name}:$name"

    fun parse(s: String): Map<String, Pair<Float, Float>> = s.split(';').mapNotNull { p ->
        val i = p.lastIndexOf('=')
        if (i <= 0) return@mapNotNull null
        val xy = p.substring(i + 1).split(',')
        val x = xy.getOrNull(0)?.toFloatOrNull() ?: return@mapNotNull null
        val y = xy.getOrNull(1)?.toFloatOrNull() ?: return@mapNotNull null
        p.substring(0, i) to (x to y)
    }.toMap()

    fun format(m: Map<String, Pair<Float, Float>>): String =
        m.entries.filter { kotlin.math.abs(it.value.first) > 0.5f || kotlin.math.abs(it.value.second) > 0.5f }
            .joinToString(";") { "${it.key}=${"%.1f".format(java.util.Locale.US, it.value.first)},${"%.1f".format(java.util.Locale.US, it.value.second)}" }

    /**
     * Update 109: [name] of [layout] is hidden (its ✕ in Edit Home), so the D-pad skips it — its buttons drop out of
     * the layout's StyleNav lists. Read during composition (reads state); never while editing.
     */
    fun off(layout: com.shelf.launcher.data.HomeLayout, name: String): Boolean =
        !HomeEditState.active && HomeWidgets.hidden(id(layout, name))

    /** How far [id] is moved (dp); reads state. */
    fun shift(id: String): Pair<Float, Float>? =
        if (HomeEditState.active) HomeEditState.liveShift[id] else parse(Features.partShifts.state.value)[id]
}

/** Makes a layout's own part movable / removable in Edit Home (see [LayoutParts]). */
@Composable
internal fun Modifier.editablePart(id: String, label: String): Modifier {
    androidx.compose.runtime.DisposableEffect(id, label) {
        LayoutParts.registered[id] = label
        // Update 202: counted — the same part drawn twice for a moment (the game you're on while the row slides to the
        // next one) stays listed until the last of them goes.
        LayoutParts.uses[id] = (LayoutParts.uses[id] ?: 0) + 1
        // (Update 325: a two-finger pinch sizes it round its middle — HomeEditPinch.)
        HomeEditState.pinchers[id] = { zoom ->
            val now = HomeEditState.liveScale[id] ?: 1f
            HomeEditState.liveScale[id] = (now * zoom).coerceIn(HomeWidgets.SCALE_MIN, HomeWidgets.SCALE_MAX)
        }
        onDispose {
            val n = (LayoutParts.uses[id] ?: 1) - 1
            if (n <= 0) { HomeEditState.pinchers.remove(id); LayoutParts.uses.remove(id); LayoutParts.registered.remove(id); HomeNest.untrack(id) } else LayoutParts.uses[id] = n
        }
    }
    // Update 202: where it is on the screen (the drag's snapping and the safe zone use it), and the screen's safe zone.
    // (Before any return: the same composable calls every time.)
    val coords = remember(id) { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    val zoneNow = androidx.compose.runtime.rememberUpdatedState(safeZone())
    val editing = HomeEditState.active
    val hidden = HomeWidgets.hidden(id)
    // Update 136: hidden and not editing: nothing of it shows, its own style's backing and frame included (the style
    // used to be drawn outside the faded layer: an empty frame stayed on screen, e.g. the TV room's achievements).
    if (!editing && hidden) return this.graphicsLayer { alpha = 0f }.pointerInput(Unit) {
        awaitPointerEventScope { while (true) awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial).changes.forEach { it.consume() } }
    }
    val shift = LayoutParts.shift(id)
    var m = this
    if (shift != null) m = m.offset(shift.first.dp, shift.second.dp)
    // Update 116: its size (L2 / R2, the corner handle or Style › Size) and its own style.
    val sc = HomeWidgets.scale(id)
    // Update 133: and stretched wider / taller on its own (its side and bottom handles), everything in it with it.
    val (sx, sy) = HomeWidgets.stretch(id)
    if (kotlin.math.abs(sc * sx - 1f) > 0.01f || kotlin.math.abs(sc * sy - 1f) > 0.01f) m = m.graphicsLayer { scaleX = sc * sx; scaleY = sc * sy }
    m = m.partStyle(id)
    if (!editing) {
        // Hidden: its place stays (the layout doesn't jump), nothing shows and it takes no touches.
        return if (hidden) m.graphicsLayer { alpha = 0f }.pointerInput(Unit) {
            awaitPointerEventScope { while (true) awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial).changes.forEach { it.consume() } }
        } else if (HomeNest.innerOf(id) != null) m.onGloballyPositioned { HomeNest.track(id, it.boundsInRoot()) } // (Update 241, #06)
        else m
    }
    val selected = HomeEditState.selected == id
    val accent = ShelfTheme.accent
    val density = LocalDensity.current.density
    // Update 212: Preview hides the frames; only the selected part has its handles (the others a faint outline).
    val preview = HomeEditState.preview
    return m
        .graphicsLayer { alpha = if (hidden) (if (preview) 0f else 0.3f) else 1f }
        .drawWithContent {
            drawContent()
            if (preview) return@drawWithContent
            // (Update 241, #06: a hollow part's inner area — dashed while a part that can go in is dragged (filled while
            // that part's middle is over it), and faint while the selected part is inside it.)
            HomeNest.innerOf(id)?.let { a ->
                val dragged = HomeNest.dragging
                val showIt = (dragged != null && HomeNest.fits(dragged, id)) || HomeNest.anchorOf(HomeEditState.selected)?.parent == id
                if (showIt) {
                    val tl = androidx.compose.ui.geometry.Offset(size.width * a.left, size.height * a.top)
                    val sz = androidx.compose.ui.geometry.Size(size.width * a.width, size.height * a.height)
                    if (dragged != null && HomeNest.hover == id) drawRect(accent.copy(alpha = 0.16f), tl, sz)
                    drawRect(
                        accent.copy(alpha = if (dragged != null) 0.9f else 0.45f), tl, sz,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
                    )
                }
            }
            val r = 9.dp.toPx()
            drawRoundRect(
                if (selected) accent else accent.copy(alpha = 0.25f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(if (selected) 2.dp.toPx() else 1.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
            )
            if (!selected) return@drawWithContent
            // The red ✕ in its top right corner.
            val c = androidx.compose.ui.geometry.Offset(size.width - r - 1.dp.toPx(), r + 1.dp.toPx())
            drawCircle(androidx.compose.ui.graphics.Color(0xFFE5484D), r, c)
            drawCircle(androidx.compose.ui.graphics.Color.White, r, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
            val a = r * 0.42f
            drawLine(androidx.compose.ui.graphics.Color.White, c.copy(x = c.x - a, y = c.y - a), c.copy(x = c.x + a, y = c.y + a), 2.dp.toPx())
            drawLine(androidx.compose.ui.graphics.Color.White, c.copy(x = c.x - a, y = c.y + a), c.copy(x = c.x + a, y = c.y - a), 2.dp.toPx())
            // (Update 325: no size or stretch handles any more — two fingers pinch it, its Adjust stretches it.)
            // Update 202: the guide lines while it's snapped to the safe zone's edge or middle (as the floating parts have).
            if (selected) {
                val gx = HomeEditState.guideX
                val gy = HomeEditState.guideY
                val far = 6000f
                val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                if (gx != null) {
                    val x = if (gx == 0f) 0f else if (gx == 1f) size.width else size.width / 2f
                    drawLine(accent, androidx.compose.ui.geometry.Offset(x, -far), androidx.compose.ui.geometry.Offset(x, far), 1.5.dp.toPx(), pathEffect = dash)
                }
                if (gy != null) {
                    val y = if (gy == 0f) 0f else if (gy == 1f) size.height else size.height / 2f
                    drawLine(accent, androidx.compose.ui.geometry.Offset(-far, y), androidx.compose.ui.geometry.Offset(far, y), 1.5.dp.toPx(), pathEffect = dash)
                }
            }
        }
        .onGloballyPositioned { coords[0] = it; val b = it.boundsInRoot(); HomeEditState.bounds[id] = b; HomeNest.track(id, b) }
        .pointerInput(id) {
            // Ahead of the part's own buttons: while editing a touch picks / drags it, never presses what's in it.
            awaitPointerEventScope {
                while (true) {
                    val down = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    val first = down.changes.firstOrNull { it.pressed && !it.previousPressed } ?: continue
                    first.consume()
                    // (Update 212: its handles are only drawn — and only work — once it's the selected part.)
                    val wasSelected = HomeEditState.selected == id
                    HomeEditState.selected = id
                    // Update 202: a part inside another (the game you're on inside the game row) sees the touch after the
                    // one round it, so the innermost one under the finger is the last to write itself here: only that one
                    // acts on it, from the next event on (the outer ones just wait for the finger to lift).
                    HomeEditState.touchOwner = id
                    var pending: androidx.compose.ui.input.pointer.PointerEvent? = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    if (HomeEditState.touchOwner != id) {
                        var ch0 = pending?.changes?.firstOrNull()
                        while (ch0 != null && ch0.pressed) {
                            ch0 = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial).changes.firstOrNull()
                        }
                        continue
                    }
                    // (Below, each loop takes [pending] first — the event already waiting — then new ones.)
                    // (Update 119: generous corners, so the ✕ and the size handle are easy to hit.)
                    val r = 30.dp.toPx()
                    if (wasSelected && first.position.x > size.width - r && first.position.y < r) {
                        HomeEditSession.toggleShown(id)
                        // (Update 202: the rest of that touch goes nowhere.)
                        var ch0 = pending?.changes?.firstOrNull()
                        pending = null
                        while (ch0 != null && ch0.pressed) {
                            ch0.consume()
                            ch0 = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial).changes.firstOrNull()
                        }
                        continue
                    }
                    // (Update 325: the corner and the edges no longer resize or stretch it — every drag moves it, two
                    // fingers pinch it: HomeEditPinch. Toni: "it gets resized instead of moved".)
                    // Update 202: moved by the finger's place on the screen (a part made bigger or smaller used to move
                    // slower or faster than the finger), snapping back to its own place and to the safe zone's edges and
                    // middle (a guide line through it), and kept inside the safe zone (SnapPart below).
                    var raw = HomeEditState.liveShift[id] ?: (0f to 0f)
                    var last = coords[0]?.takeIf { it.isAttached }?.localToRoot(first.position)
                    // (Update 212: one step of Undo, taken when it really starts moving — a tap only selects.)
                    var pushed = false
                    while (true) {
                        val ev = pending ?: awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        pending = null
                        val ch = ev.changes.firstOrNull() ?: break
                        if (!ch.pressed) { ch.consume(); break }
                        ch.consume()
                        // (Update 325: two fingers pinch it — it stays put meanwhile.)
                        if (ev.changes.count { it.pressed } >= 2) { last = coords[0]?.takeIf { it.isAttached }?.localToRoot(ch.position); continue }
                        val lc = coords[0]?.takeIf { it.isAttached }
                        val here = lc?.localToRoot(ch.position)
                        val d = if (here != null && last != null) here - last!! else ch.position - ch.previousPosition
                        if (here != null) last = here
                        if (!pushed && (d.x != 0f || d.y != 0f)) { HomeEditSession.push("drag:$id", force = true); pushed = true }
                        raw = (raw.first + d.x / density) to (raw.second + d.y / density)
                        HomeEditState.liveShift[id] = SnapPart.shift(id, raw, lc, zoneNow.value, density)
                    }
                    HomeEditState.guideX = null; HomeEditState.guideY = null
                }
            }
        }
}

/**
 * Update 202: where a layout part goes while it's dragged in Edit Home. [raw] is where the finger would put it (its shift
 * from its place, dp); it snaps back to its own place, or lines its left / right edge or middle up with the safe zone's
 * (setting the guide lines), and is kept inside the safe zone — a part that already reaches past it (a full-width bar,
 * a panel against the screen's edge) can stay where it is but goes no further out. [lc]: the part now; [density]: px per dp.
 */
internal object SnapPart {
    fun shift(id: String, raw: Pair<Float, Float>, lc: androidx.compose.ui.layout.LayoutCoordinates?, zone: SafeZone, density: Float): Pair<Float, Float> {
        val b = lc?.boundsInRoot()
        if (b == null || b.width <= 0f || b.height <= 0f) { HomeEditState.guideX = null; HomeEditState.guideY = null; return raw }
        val cur = HomeEditState.liveShift[id] ?: (0f to 0f)
        val x = axis(raw.first, cur.first, b.left, b.width, zone.left.value * density, (zone.width - zone.right).value * density, density)
        val y = axis(raw.second, cur.second, b.top, b.height, zone.top.value * density, (zone.height - zone.bottom).value * density, density)
        HomeEditState.guideX = x.second
        HomeEditState.guideY = y.second
        return x.first to y.first
    }

    /** One axis: the shift (dp) to use and the guide (0 start edge, 0.5 middle, 1 end edge lined up; null = none). */
    private fun axis(rawV: Float, curV: Float, start: Float, size: Float, zoneStart: Float, zoneEnd: Float, density: Float): Pair<Float, Float?> {
        val reach = 10f * density
        // Where its start edge would be (px) at [rawV], and at its own place.
        val at = start + (rawV - curV) * density
        val home = start - curV * density
        var best = at
        var guide: Float? = null
        var bestD = reach
        if (kotlin.math.abs(at - home) < bestD) { bestD = kotlin.math.abs(at - home); best = home }
        for ((target, g) in listOf(zoneStart to 0f, (zoneStart + zoneEnd - size) / 2f to 0.5f, zoneEnd - size to 1f)) {
            val dd = kotlin.math.abs(at - target)
            if (dd < bestD) { bestD = dd; best = target; guide = g }
        }
        // Inside the safe zone (a part bigger than it isn't held on that axis).
        if (size <= zoneEnd - zoneStart) {
            val lo = minOf(zoneStart, start)
            val hi = maxOf(zoneEnd - size, start)
            best = best.coerceIn(lo, maxOf(lo, hi))
        }
        return (curV + (best - start) / density) to guide
    }
}

/**
 * (Update 241, #06) Parts inside a hollow part. Some layout parts have an empty space inside them: the art with nothing
 * on it, a frame with a hole. Each one declares that space as its inner area, in fractions of its own box. A floating
 * part (+ Add) of the same layout let go with its middle inside an inner area goes in. It is then anchored there, at the
 * same place inside the area, so it moves (and is shifted or sized) with the hollow part. It stays drawn above it (the
 * floating parts' layer), and touches and the D-pad reach it before the part round it; L1 / R1 come to it right after
 * that part. Dragged out, or Reset this part, it comes out. While the hollow part is hidden or off the screen it sits at
 * its own place.
 *
 * Only the Hero tiles' big picture is hollow now: its right-hand side, under the clock pill and beside the title. To
 * make another part hollow, add its id and inner area to [inner]; its layout has to draw it with [editablePart].
 *
 * Kept in the "home_nest" prefs as "child=parent@x,y;…" (the child's middle as fractions of the inner area). It is part
 * of the edit session: Undo and Discard put it back, and so does an edit left unfinished.
 */
internal object HomeNest {
    private val inner: Map<String, androidx.compose.ui.geometry.Rect> = mapOf(
        LayoutParts.id(com.shelf.launcher.data.HomeLayout.HERO_TILES, "hero") to androidx.compose.ui.geometry.Rect(0.6f, 0.2f, 0.98f, 0.95f)
    )

    /** [id]'s inner area (fractions of its own box), or null when it isn't hollow. */
    fun innerOf(id: String): androidx.compose.ui.geometry.Rect? = inner[id]

    /** Where the hollow parts are on the screen now (root px), while editing and not. */
    private val rects = mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>()

    fun track(id: String, r: androidx.compose.ui.geometry.Rect) { if (id in inner && rects[id] != r) rects[id] = r }
    fun untrack(id: String) { if (id in rects) rects.remove(id) }

    data class Anchor(val parent: String, val x: Float, val y: Float)

    /** The anchors as stored text (Compose state). */
    var raw by mutableStateOf("")
        private set
    private var store: android.content.SharedPreferences? = null

    /** At start (HomeEditSession.recover): reads the anchors. */
    fun init(context: android.content.Context) {
        if (store != null) return
        store = runCatching { context.applicationContext.getSharedPreferences("home_nest", android.content.Context.MODE_PRIVATE) }.getOrNull()
        raw = runCatching { store?.getString("anchors", "") }.getOrNull().orEmpty()
    }

    fun set(v: String) {
        if (raw == v) return
        raw = v
        runCatching { store?.edit()?.putString("anchors", v)?.apply() }
    }

    private fun parse(s: String): Map<String, Anchor> = s.split(';').mapNotNull { e ->
        val i = e.indexOf('=')
        val j = e.lastIndexOf('@')
        if (i <= 0 || j <= i + 1) return@mapNotNull null
        val xy = e.substring(j + 1).split(',')
        val x = xy.getOrNull(0)?.toFloatOrNull() ?: return@mapNotNull null
        val y = xy.getOrNull(1)?.toFloatOrNull() ?: return@mapNotNull null
        e.substring(0, i) to Anchor(e.substring(i + 1, j), x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
    }.toMap()

    private fun format(m: Map<String, Anchor>): String = m.entries.joinToString(";") { (c, a) ->
        "$c=${a.parent}@${"%.4f".format(java.util.Locale.US, a.x)},${"%.4f".format(java.util.Locale.US, a.y)}"
    }

    /** Every anchor (reads state). */
    fun anchors(): Map<String, Anchor> = parse(raw)

    fun anchorOf(child: String): Anchor? = anchors()[child]

    /** The floating part being dragged now, and the hollow part its middle is over (for the outlines). */
    var dragging by mutableStateOf<String?>(null)
    var hover by mutableStateOf<String?>(null)

    /** [parent]'s inner area on the screen (root px), or null while it's hidden or not on the screen. */
    fun innerRect(parent: String): androidx.compose.ui.geometry.Rect? {
        val r = rects[parent] ?: return null
        val a = inner[parent] ?: return null
        if (HomeWidgets.hidden(parent) || r.width <= 0f || r.height <= 0f) return null
        return androidx.compose.ui.geometry.Rect(r.left + r.width * a.left, r.top + r.height * a.top, r.left + r.width * a.right, r.top + r.height * a.bottom)
    }

    /**
     * [child] can go into [parent]: a floating part added to the same layout (never a layout's own part, a hollow part,
     * or a part every layout shares, which would follow a part that isn't on the other layouts).
     */
    fun fits(child: String, parent: String): Boolean {
        if (child == parent || child in inner || child.startsWith(LayoutParts.PREFIX) || parent !in inner) return false
        val l = HomeEditSession.layoutOf(child) ?: return false
        return l == HomeEditSession.layoutOf(parent)
    }

    /** The hollow part whose inner area holds [p] (root px) and can take [child], or null. */
    fun containerAt(child: String, p: androidx.compose.ui.geometry.Offset): String? =
        inner.keys.firstOrNull { fits(child, it) && innerRect(it)?.contains(p) == true }

    /** Where [child]'s middle is now (root px) when it's inside a hollow part on the screen, else null. */
    fun centreOf(child: String): androidx.compose.ui.geometry.Offset? {
        val a = anchorOf(child) ?: return null
        val r = innerRect(a.parent) ?: return null
        return androidx.compose.ui.geometry.Offset(r.left + r.width * a.x, r.top + r.height * a.y)
    }

    /** [child] let go with its middle at [p] (root px): into the hollow part there, or out of the one it was in. */
    fun drop(child: String, p: androidx.compose.ui.geometry.Offset) {
        val m = anchors().toMutableMap()
        val c = containerAt(child, p)
        val r = c?.let { innerRect(it) }
        if (c != null && r != null) m[child] = Anchor(c, ((p.x - r.left) / r.width).coerceIn(0f, 1f), ((p.y - r.top) / r.height).coerceIn(0f, 1f))
        else m.remove(child)
        set(format(m))
    }

    /** Puts [child] in the middle of [parent]'s inner area (the inspector's "Put inside"). */
    fun putInside(child: String, parent: String) {
        if (!fits(child, parent)) return
        set(format(anchors() + (child to Anchor(parent, 0.5f, 0.5f))))
    }

    /** Takes [child] out of the hollow part it's in. */
    fun release(child: String) {
        val m = anchors()
        if (child in m) set(format(m - child))
    }

    /** Takes out every part of [layout] (Reset this layout). */
    fun releaseLayout(layout: com.shelf.launcher.data.HomeLayout) {
        val m = anchors()
        val keep = m.filterKeys { HomeEditSession.layoutOf(it) != layout }
        if (keep.size != m.size) set(format(keep))
    }

    /** The D-pad on a part inside a hollow one: moves it inside the inner area (4 % of it a step). False: not inside one. */
    fun nudge(child: String, dx: Int, dy: Int, fast: Boolean): Boolean {
        val a = anchorOf(child) ?: return false
        if (innerRect(a.parent) == null) return false
        val step = if (fast) 0.08f else 0.04f
        set(format(anchors() + (child to a.copy(x = (a.x + dx * step).coerceIn(0f, 1f), y = (a.y + dy * step).coerceIn(0f, 1f)))))
        return true
    }

    /** A hollow part on this home screen now that [child] can go into (the inspector's "Put inside"), or null. */
    fun containerFor(child: String): String? = inner.keys.firstOrNull { fits(child, it) && innerRect(it) != null }

    /** [list] with the parts inside a hollow part right after it (L1 / R1 and Elements go part, then what's in it). */
    fun order(list: List<String>): List<String> {
        val kids = anchors().filter { (c, a) -> c in list && a.parent in list }
        if (kids.isEmpty()) return list
        val out = ArrayList<String>(list.size)
        for (id in list) {
            if (id in kids) continue
            out += id
            kids.filterValues { it.parent == id }.keys.sorted().forEach { out += it }
        }
        return out
    }
}


/**
 * Update 258 (#120): where each floating part is drawn in its layer (dp, as a Rect), for the parts that step out of
 * another's way while they haven't been moved (MovableWidget's keepClearOf).
 */
/** Update 359 (D67): where the games row (with its name line) starts on screen, root dp; 0 = no row (set by the home screen). */
internal object DockClear {
    var topDp by mutableStateOf(0f)
}

internal object PartRects {
    val at = mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>()

    /**
     * Where a [w]×[h] part wanting [want] goes so it covers none of [others]: [want] if it's clear; else straight above or
     * below what it would cover (6 dp apart), the nearer one that's clear and inside [zone]; else [want] after all.
     */
    fun clearSpot(want: Pair<Float, Float>, zone: SafeZone, w: Dp, h: Dp, others: List<androidx.compose.ui.geometry.Rect>): Pair<Float, Float> {
        if (others.isEmpty()) return want
        val p = zone.place(want.first, want.second, w, h)
        fun box(y: Float) = androidx.compose.ui.geometry.Rect(p.x.value, y, (p.x + w).value, y + h.value)
        fun hits(y: Float) = others.filter { it.overlaps(box(y)) }
        val hit = hits(p.y.value)
        if (hit.isEmpty()) return want
        val gap = 6f
        val top = zone.top.value
        val bottom = (zone.height - zone.bottom).value
        val y = listOf(hit.minOf { it.top } - gap - h.value, hit.maxOf { it.bottom } + gap)
            .filter { it >= top && it + h.value <= bottom && hits(it).isEmpty() }
            .minByOrNull { abs(it - p.y.value) } ?: return want
        return zone.fractionOf(p.x, y.dp, w, h)
    }
}


/**
 * Update 325: Edit Home's two-finger pinch — screen-wide, so it works on a small part too (both fingers on it was hard):
 * it sizes the picked part round its middle. It shares every touch with what's under it (one finger still reaches the
 * parts to move them, the toolbar to tap it) and takes only the two-finger ones.
 */
private object HomeEditPinchElement : androidx.compose.ui.node.ModifierNodeElement<HomeEditPinchNode>() {
    override fun create() = HomeEditPinchNode()
    override fun update(node: HomeEditPinchNode) {}
    override fun hashCode() = 325
    override fun equals(other: Any?) = other === this
}

private class HomeEditPinchNode : Modifier.Node(), androidx.compose.ui.node.PointerInputModifierNode {
    private var pinching = false
    override fun onPointerEvent(pointerEvent: androidx.compose.ui.input.pointer.PointerEvent, pass: androidx.compose.ui.input.pointer.PointerEventPass, bounds: IntSize) {
        if (pass != androidx.compose.ui.input.pointer.PointerEventPass.Main) return
        val fingers = pointerEvent.changes.count { it.pressed }
        if (fingers < 2) { pinching = false; return }
        val id = HomeEditState.selected
        if (!pinching) { pinching = true; HomeEditSession.push("resize:$id", force = true) }
        val zoom = pointerEvent.calculateZoom()
        if (zoom != 1f && zoom.isFinite()) HomeEditState.pinchers[id]?.invoke(zoom)
        pointerEvent.changes.forEach { if (it.positionChanged()) it.consume() }
    }
    override fun onCancelPointerInput() { pinching = false }
    override fun sharePointerInputWithSiblings() = true
}
