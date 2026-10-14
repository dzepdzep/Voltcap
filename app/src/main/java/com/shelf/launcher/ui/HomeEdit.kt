package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.height
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.border
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

    const val SCALE_MIN = 0.6f
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

    /** Update 202: opens Art position (HomeAdjust.kt) for [artLook], over Edit Home. */
    fun openArtPosition() {
        val look = artLook ?: return
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

    /** Update 133: every part back (Y, or Reset layout). */
    fun resetEvery() { live.clear(); liveScale.clear(); liveShift.clear(); liveStretch.clear() }

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
        liveShift.clear()
        liveShift.putAll(LayoutParts.parse(Features.partShifts.value))
        live.clear()
        live.putAll(HomeWidgets.parse(Features.widgetPositions.value))
        liveScale.clear()
        liveScale.putAll(HomeWidgets.parseScales(Features.widgetScales.value))
        liveStretch.clear()
        liveStretch.putAll(HomeWidgets.parseStretch(Features.widgetStretch.value))
        selected = HomeWidgets.all.firstOrNull { it in HomeWidgets.present } ?: HomeWidgets.present.firstOrNull() ?: HomeWidgets.all.first()
        active = true
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
        active = false
    }

    /** Controller keys while editing. Always consumes them (the home screen stays still meanwhile). */
    fun onKey(e: KeyEvent, defaultOf: (String) -> Pair<Float, Float>): Boolean {
        if (e.type != KeyEventType.KeyDown) return true
        val part = selected.startsWith(LayoutParts.PREFIX)
        val cur = if (part) 0f to 0f else live[selected] ?: defaultOf(selected)
        val step = 0.04f
        fun move(dx: Float, dy: Float) {
            if (part) {
                // A layout's own part moves in 8 dp steps from its place.
                val s = liveShift[selected] ?: (0f to 0f)
                liveShift[selected] = (s.first + dx / step * 8f) to (s.second + dy / step * 8f)
                return
            }
            live[selected] = HomeWidgets.snap((cur.first + dx).coerceIn(0f, 1f)) to HomeWidgets.snap((cur.second + dy).coerceIn(0f, 1f))
        }
        when (e.key) {
            Key.DirectionLeft -> move(-step, 0f)
            Key.DirectionRight -> move(step, 0f)
            Key.DirectionUp -> move(0f, -step)
            Key.DirectionDown -> move(0f, step)
            Key.ButtonL1, Key.ButtonR1 -> {
                val list = (HomeWidgets.all.filter { it in HomeWidgets.present } + HomeWidgets.present.filter { it !in HomeWidgets.all } +
                    LayoutParts.registered.keys.sorted())
                    .ifEmpty { HomeWidgets.all }
                val i = list.indexOf(selected).coerceAtLeast(0)
                val n = list.size
                selected = list[((i + if (e.key == Key.ButtonR1) 1 else -1) % n + n) % n]
            }
            // L2 / R2: smaller / bigger.
            Key.ButtonL2, Key.ButtonR2 -> {
                val now = liveScale[selected] ?: 1f
                liveScale[selected] = (now + if (e.key == Key.ButtonR2) 0.1f else -0.1f).coerceIn(HomeWidgets.SCALE_MIN, HomeWidgets.SCALE_MAX)
            }
            Key.ButtonX -> resetOne(selected)
            // Update 104: Select takes the part off the screen (its red ✕), Start opens + Add.
            Key.ButtonSelect -> HomeWidgets.remove(selected)
            Key.ButtonStart -> adding = true
            // Update 116: L3 opens the part's style.
            Key.ButtonThumbLeft -> styling = selected
            // Update 202: R3 opens Art position for the look that's on (covers, Box art, cartridges or discs).
            Key.ButtonThumbRight -> openArtPosition()
            Key.ButtonY -> resetEvery()
            Key.ButtonA, Key.Enter, Key.DirectionCenter, Key.ButtonB, Key.Back, Key.Escape -> done()
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
    if (stored == null && !editing && defaultFrac == null) {
        // (Update 125: the parts you add are always drawn over the games, never under them.)
        Box(Modifier.align(defaultAlign).zIndex(PartsLayer.z).padding(defaultPadding).onSizeChanged { size = it; HomeEditState.sizes[id] = it }) {
            Box(Modifier.stretched(sx, sy)) { ScaledWidget(scale) { StyledPart(id, content) } }
        }
        return
    }
    val w = with(density) { size.width.toDp() }
    val h = with(density) { size.height.toDp() }
    // Where it is now: the stored fractions, or (first time in edit mode) its normal corner.
    val f = stored ?: defaultFrac ?: defaultFractions(defaultAlign)
    val pos = zone.place(f.first, f.second, w, h)
    // Update 144: the drag reads these as they are now, so it isn't restarted (and stopped under the finger) when
    // the part changes size while it's being moved.
    val wNow by androidx.compose.runtime.rememberUpdatedState(w)
    val hNow by androidx.compose.runtime.rememberUpdatedState(h)
    val zoneNow by androidx.compose.runtime.rememberUpdatedState(zone)
    val fNow by androidx.compose.runtime.rememberUpdatedState(f)
    val selected = editing && HomeEditState.selected == id
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
    Box(
        Modifier
            .zIndex(if (PartsLayer.z > 0f && selected) PartsLayer.z + 2f else PartsLayer.z)
            .offset(pos.x, pos.y)
            .onSizeChanged { size = it; HomeEditState.sizes[id] = it }
            .then(
                if (editing) Modifier
                    .border(if (selected) 2.dp else 1.dp, if (selected) ShelfTheme.accent else ShelfTheme.accent.copy(alpha = 0.4f), shape)
                    // A tap picks it (then the D-pad, L2 / R2 and X work on it).
                    .pointerInput(id) { detectTapGestures { HomeEditState.selected = id } }
                    .pointerInput(id) {
                        // Update 116: it snaps while you drag (to the edges, thirds and the middle), with a guide line
                        // through it on the axis it snapped to; the finger's own place is followed underneath.
                        var raw = HomeEditState.live[id] ?: fNow
                        detectDragGestures(
                            onDragStart = { HomeEditState.selected = id; raw = HomeEditState.live[id] ?: fNow },
                            onDrag = { change, drag ->
                                change.consume()
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
                                HomeEditState.live[id] = sx to sy
                            },
                            onDragEnd = { HomeEditState.guideX = null; HomeEditState.guideY = null },
                            onDragCancel = { HomeEditState.guideX = null; HomeEditState.guideY = null }
                        )
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
        // (Update 116: in its own style — backing, see-through, outline, shape, still.)
        Box(Modifier.stretched(sx, sy)) {
            ScaledWidget(scale) { StyledPart(id) { if (fixedWidth != null) Box(Modifier.width(fixedWidth)) { content() } else content() } }
        }
        if (editing) {
            // The size handle (bottom left, update 125 — the ✕ keeps the top right): drag out to make it bigger, in to make it smaller.
            // Update 119: inside the part (a handle hanging outside it never got the touch), bigger, and the drag keeps
            // going while the part grows (the gesture used to restart — and stop — at every change of size).
            val sizeNow by androidx.compose.runtime.rememberUpdatedState(size)
            Box(
                Modifier.align(Alignment.BottomStart).size(30.dp).clip(RoundedCornerShape(50))
                    .background(if (selected) ShelfTheme.accent else ShelfTheme.colors.surfaceRaised)
                    .border(1.5.dp, ShelfTheme.colors.textPrimary.copy(alpha = 0.6f), RoundedCornerShape(50))
                    // Update 133: a tap on the handle opens the part's Style (a drag still resizes it).
                    .pointerInput(id) { detectTapGestures { HomeEditState.selected = id; HomeEditState.styling = id } }
                    .pointerInput(id) {
                        detectDragGestures(
                            onDragStart = { HomeEditState.selected = id },
                            onDrag = { change, drag ->
                                change.consume()
                                val diag = kotlin.math.max(1f, (sizeNow.width + sizeNow.height).toFloat())
                                val now = HomeEditState.liveScale[id] ?: 1f
                                // Out from the bottom left corner (left and down) makes it bigger.
                                val next = (now * (1f + (-drag.x + drag.y) / diag)).coerceIn(HomeWidgets.SCALE_MIN, HomeWidgets.SCALE_MAX)
                                HomeEditState.liveScale[id] = next
                                // Update 179: its top right corner stays still: it grows towards the handle, what's in it with it.
                                keepCorner(next / now, 1f, keepRight = true)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.OpenInFull, null, tint = if (selected) onAccentColor() else ShelfTheme.colors.textPrimary, modifier = Modifier.size(15.dp))
            }
            // Update 133: stretch it wider (right edge) or taller (bottom edge) on its own.
            // (Update 179: the left / top edge stays still while it's stretched.)
            StretchHandle(id, across = true, selected = selected, onStretch = { rx, ry -> keepCorner(rx, ry, keepRight = false) }) { sizeNow }
            StretchHandle(id, across = false, selected = selected, onStretch = { rx, ry -> keepCorner(rx, ry, keepRight = false) }) { sizeNow }
            // The red ✕ (update 104): takes the part off the screen (+ Add brings it back). (Inside the part too.)
            Box(
                Modifier.align(Alignment.TopEnd).size(26.dp).clip(RoundedCornerShape(50))
                    .background(androidx.compose.ui.graphics.Color(0xFFE5484D))
                    .border(1.5.dp, androidx.compose.ui.graphics.Color.White, RoundedCornerShape(50))
                    .pointerInput(id) { detectTapGestures { HomeWidgets.remove(id) } },
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
                    onDragStart = { HomeEditState.selected = id },
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

/** The bar along the bottom while editing: what the buttons do, Reset Position / Reset Layout / Done. */
@Composable
internal fun BoxScope.HomeEditBar() {
    if (!HomeEditState.active) return
    // Update 125: the bar folds away to the left or right with its arrows (a small tab brings it back), so nothing is
    // hidden under it while you place things at the bottom.
    val folded = HomeEditState.barFolded
    if (folded != 0) {
        Row(
            Modifier.align(if (folded < 0) Alignment.BottomStart else Alignment.BottomEnd).zIndex(9f).safeZonePadding()
                .clip(RoundedCornerShape(50)).background(ShelfTheme.colors.surfaceRaised.copy(alpha = 0.95f))
                .pointerInput(Unit) { detectTapGestures { HomeEditState.barFolded = 0 } }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (folded < 0) "Edit Home  ▶" else "◀  Edit Home", color = ShelfTheme.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    } else HomeEditBarOpen() // Update 151: the open legend, fitted to small screens (HomeEditLegend.kt).
    if (HomeEditState.adding) HomeAddSheet(HomeEditState.layout, onDismiss = { HomeEditState.adding = false })
    HomeEditState.styling?.let { id -> PartStyleSheet(id, onDismiss = { HomeEditState.styling = null }) }
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
                        Chip((if (sel) "✓ " else "+ ") + label, accent = sel) { Features.toggleOverlayPart(layout, id) }
                    }
                }
            }
            // (Update 202: only this layout's own parts and the ones every layout shares — another layout's parts aren't here.)
            val gone = Features.hiddenParts.state.value.filter { !it.startsWith(LayoutParts.PREFIX) || it.startsWith(LayoutParts.PREFIX + layout.name + ":") }
            if (gone.isNotEmpty()) {
                Spacer(androidx.compose.ui.Modifier.height(10.dp))
                SectionLabel("Taken off")
                Spacer(androidx.compose.ui.Modifier.height(6.dp))
                ChipFlow { gone.forEach { id -> Chip("↺ " + HomeWidgets.label(id), accent = false) { HomeWidgets.restore(id) } } }
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
        onDispose {
            val n = (LayoutParts.uses[id] ?: 1) - 1
            if (n <= 0) { LayoutParts.uses.remove(id); LayoutParts.registered.remove(id) } else LayoutParts.uses[id] = n
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
        } else m
    }
    val selected = HomeEditState.selected == id
    val accent = ShelfTheme.accent
    val density = LocalDensity.current.density
    return m
        .graphicsLayer { alpha = if (hidden) 0.3f else 1f }
        .drawWithContent {
            drawContent()
            val r = 9.dp.toPx()
            drawRoundRect(
                if (selected) accent else accent.copy(alpha = 0.45f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(if (selected) 2.dp.toPx() else 1.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
            )
            // The red ✕ in its top right corner.
            val c = androidx.compose.ui.geometry.Offset(size.width - r - 1.dp.toPx(), r + 1.dp.toPx())
            drawCircle(androidx.compose.ui.graphics.Color(0xFFE5484D), r, c)
            drawCircle(androidx.compose.ui.graphics.Color.White, r, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
            val a = r * 0.42f
            drawLine(androidx.compose.ui.graphics.Color.White, c.copy(x = c.x - a, y = c.y - a), c.copy(x = c.x + a, y = c.y + a), 2.dp.toPx())
            drawLine(androidx.compose.ui.graphics.Color.White, c.copy(x = c.x - a, y = c.y + a), c.copy(x = c.x + a, y = c.y - a), 2.dp.toPx())
            // Update 116: the size handle — in its bottom left corner (update 125; the ✕ keeps the top right).
            val hc = androidx.compose.ui.geometry.Offset(r + 1.dp.toPx(), size.height - r - 1.dp.toPx())
            drawCircle(if (selected) accent else androidx.compose.ui.graphics.Color(0xFF2A2E38), r, hc)
            drawCircle(androidx.compose.ui.graphics.Color.White, r, hc, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
            drawLine(androidx.compose.ui.graphics.Color.White, hc.copy(x = hc.x - r * 0.45f, y = hc.y + r * 0.45f), hc.copy(x = hc.x + r * 0.45f, y = hc.y - r * 0.45f), 1.8.dp.toPx())
            // Update 133: the stretch handles — the middle of the right edge (wider) and of the bottom edge (taller).
            val fill = if (selected) accent else androidx.compose.ui.graphics.Color(0xFF2A2E38)
            val rc = androidx.compose.ui.geometry.Offset(size.width - r * 0.6f, size.height / 2f)
            val bc = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height - r * 0.6f)
            for (c2 in listOf(rc, bc)) {
                val across = c2 == rc
                val hw = if (across) r * 0.6f else r * 1.3f
                val hh = if (across) r * 1.3f else r * 0.6f
                drawRoundRect(fill, androidx.compose.ui.geometry.Offset(c2.x - hw, c2.y - hh), androidx.compose.ui.geometry.Size(hw * 2, hh * 2), androidx.compose.ui.geometry.CornerRadius(r))
                drawRoundRect(androidx.compose.ui.graphics.Color.White, androidx.compose.ui.geometry.Offset(c2.x - hw, c2.y - hh), androidx.compose.ui.geometry.Size(hw * 2, hh * 2), androidx.compose.ui.geometry.CornerRadius(r),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
            }
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
        .onGloballyPositioned { coords[0] = it }
        .pointerInput(id) {
            // Ahead of the part's own buttons: while editing a touch picks / drags it, never presses what's in it.
            awaitPointerEventScope {
                while (true) {
                    val down = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    val first = down.changes.firstOrNull { it.pressed && !it.previousPressed } ?: continue
                    first.consume()
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
                    if (first.position.x > size.width - r && first.position.y < r) {
                        HomeWidgets.remove(id)
                        // (Update 202: the rest of that touch goes nowhere.)
                        var ch0 = pending?.changes?.firstOrNull()
                        pending = null
                        while (ch0 != null && ch0.pressed) {
                            ch0.consume()
                            ch0 = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial).changes.firstOrNull()
                        }
                        continue
                    }
                    // Update 116: the corner handle resizes it (drag out: bigger, in: smaller).
                    if (first.position.x < r && first.position.y > size.height - r) {
                        // Update 133: a tap on it (no drag) opens the part's Style.
                        var moved = 0f
                        while (true) {
                            val ev = pending ?: awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                            pending = null
                            val ch = ev.changes.firstOrNull() ?: break
                            if (!ch.pressed) { ch.consume(); break }
                            val d = ch.position - ch.previousPosition
                            moved += kotlin.math.abs(d.x) + kotlin.math.abs(d.y)
                            ch.consume()
                            val diag = kotlin.math.max(1f, (size.width + size.height).toFloat())
                            val now = HomeEditState.liveScale[id] ?: 1f
                            val next = (now * (1f + (-d.x + d.y) / diag)).coerceIn(HomeWidgets.SCALE_MIN, HomeWidgets.SCALE_MAX)
                            HomeEditState.liveScale[id] = next
                            // Update 179: the opposite (top right) corner stays still, so the part and what's in it
                            // follow the handle (it used to grow from its middle).
                            val (stx, sty) = HomeWidgets.stretch(id)
                            val ds = next - now
                            val sh = HomeEditState.liveShift[id] ?: (0f to 0f)
                            HomeEditState.liveShift[id] = (sh.first - ds * stx * size.width / 2f / density) to (sh.second + ds * sty * size.height / 2f / density)
                        }
                        if (moved < 8.dp.toPx()) HomeEditState.styling = id
                        continue
                    }
                    // Update 133: the stretch handles — the middle of the right edge (wider), of the bottom edge (taller).
                    val acrossHit = first.position.x > size.width - r && kotlin.math.abs(first.position.y - size.height / 2f) < r
                    val downHit = first.position.y > size.height - r && kotlin.math.abs(first.position.x - size.width / 2f) < r
                    if (acrossHit || downHit) {
                        while (true) {
                            val ev = pending ?: awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                            pending = null
                            val ch = ev.changes.firstOrNull() ?: break
                            if (!ch.pressed) { ch.consume(); break }
                            val d = ch.position - ch.previousPosition
                            ch.consume()
                            val before = HomeWidgets.stretch(id)
                            HomeEditState.stretchBy(id, if (acrossHit) d.x else 0f, if (downHit) d.y else 0f, size.width, size.height)
                            // Update 179: the left (or top) edge stays still: it grows towards the handle.
                            val after = HomeWidgets.stretch(id)
                            val sc = HomeWidgets.scale(id)
                            val sh = HomeEditState.liveShift[id] ?: (0f to 0f)
                            HomeEditState.liveShift[id] = (sh.first + sc * (after.first - before.first) * size.width / 2f / density) to
                                (sh.second + sc * (after.second - before.second) * size.height / 2f / density)
                        }
                        continue
                    }
                    // Update 202: moved by the finger's place on the screen (a part made bigger or smaller used to move
                    // slower or faster than the finger), snapping back to its own place and to the safe zone's edges and
                    // middle (a guide line through it), and kept inside the safe zone (SnapPart below).
                    var raw = HomeEditState.liveShift[id] ?: (0f to 0f)
                    var last = coords[0]?.takeIf { it.isAttached }?.localToRoot(first.position)
                    while (true) {
                        val ev = pending ?: awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        pending = null
                        val ch = ev.changes.firstOrNull() ?: break
                        if (!ch.pressed) { ch.consume(); break }
                        ch.consume()
                        val lc = coords[0]?.takeIf { it.isAttached }
                        val here = lc?.localToRoot(ch.position)
                        val d = if (here != null && last != null) here - last!! else ch.position - ch.previousPosition
                        if (here != null) last = here
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
