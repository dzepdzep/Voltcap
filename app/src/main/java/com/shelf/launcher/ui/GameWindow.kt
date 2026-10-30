package com.shelf.launcher.ui

import androidx.compose.material.icons.filled.Speed
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import com.shelf.launcher.ui.theme.ShelfTheme
import com.shelf.launcher.LauncherUiState
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.GameArt
import com.shelf.launcher.data.HomeLayout
import kotlinx.coroutines.launch

/*
 * Update 113: the game window on every layout (it was the Channel grid's only). A or a tap on the game you're on opens
 * it — always on the main screen, growing out of the game you picked — with the game's picture (its preview video
 * once found), name and console, and three buttons: back, Details (turns the window over to the game's details, with
 * "Full details" for the big window) and Play. Each layout has its own look of it (below, [lookFor]).
 * Settings › Home › "Game window" off: A / a tap starts the game straight away.
 */

/** The game window, open while [game] is set. */
internal object GamePopup {
    var game by mutableStateOf<Game?>(null)
}

/**
 * Where the game you're on was last drawn (in its window), so the game window grows out of it. Plain fields (written
 * while laying out, read once when the window opens); [displayId] tells the main screen from the second one.
 */
internal object GameOrigin {
    /** Marked by the card the layout lifts for the game you're on (whichever game that is). */
    const val FOCUSED = "\u0000focused"
    var key: String? = null
    var rect: Rect? = null
    var displayId: Int = -1
    /** Update 294: when [rect] was last marked (uptime ms). */
    var markedAt = 0L
}

/**
 * Update 113: the big logo for [g] (Edit Home's logo part, Hero carousel, Spotlight, the second screen's showcase):
 * your own pick, else SteamGridDB's (Features.bigLogoSgdb, looked up once per game), else — with that off, or without
 * a SteamGridDB key — the artwork's logo from whichever source.
 */
internal fun bigLogo(g: Game, state: LauncherUiState): String? {
    val k = g.stableKey
    state.artOverrides[k]?.logo?.let { return it }
    val any = state.art[k]?.logoUrl
    if (!Features.bigLogoSgdb.state.value || com.shelf.launcher.data.SgdbAuth.apiKey.isNullOrBlank()) return any
    if (any != null && any.contains("steamgriddb", ignoreCase = true)) return any
    return state.sgdbLogos[k]?.takeIf { it.isNotEmpty() }
}

/** Update 400: storage layouts whose picked case opens a plain window (no piece, no scene of their own): it grows out of the case ([GameWindow]'s cardFlight). */
/** Update 403: how visible the window's veil is for a given flight progress: exactly the flight's own progress (0 = piece still in its slot, 1 = landed), so the screen never dims ahead of the movement. */
internal fun flightVeil(lift: Float): Float = lift.coerceIn(0f, 1f)

internal val PICKUP_CARD_LAYOUTS =setOf(HomeLayout.SHELF, HomeLayout.CONSOLES, HomeLayout.CARDS, HomeLayout.CRATE)

/** Marks [key]'s picture as the game you're on ([on]), so the game window can grow out of it. */
@Composable
internal fun Modifier.gameOrigin(key: String, on: Boolean): Modifier {
    // Update 294 (Toni: "closing a game window, whatever look you use goes back into its place, not somewhere on the
    // screen at random"): the game window flies back to the last picture that marked itself as the game you're on — and
    // a layout can draw the same game more than once: the Hub's cover flow draws its mirror on the floor (upside down,
    // under the real one), and a big spot and its row both draw the game you're on. Whichever was laid out last won, so
    // the piece flew back to its reflection or to the other copy. Now a mirror never marks, a copy that's off the
    // window or has no size doesn't either, and of two copies marked at the same moment the bigger one (the one you're
    // on) stays.
    if (!on || LocalArtMirror.current) return this
    val view = LocalView.current
    // (Update 325, Toni: "the Game Boy cartridges still have that ghost layer above and a bit on the left" — screenshots
    // of the game window's Inspect / Wobble: it was the game's own card on the home screen behind the window, still drawn
    // where the piece had come out of it, its edges showing past the piece. Only the 3D carousel hid it (MediaInspect);
    // every layout's card for the game you're on fades out now as its piece comes out, and back as it goes in.)
    // (Update 400: the focused-card marker (FocusedCard) fades out too: it is the card the piece / window comes out of.)
    return this.graphicsLayer { if (MediaInspect.key == key || (key == GameOrigin.FOCUSED && MediaInspect.key != null)) alpha = MediaInspect.cardAlpha(MediaInspect.amount.floatValue, MediaInspect.hold.value) }.onGloballyPositioned { c ->
        val r = c.boundsInWindow()
        if (r.width < 4f || r.height < 4f) return@onGloballyPositioned
        val win = c.findRootCoordinates().size
        if (r.right <= 0f || r.bottom <= 0f || r.left >= win.width || r.top >= win.height) return@onGloballyPositioned
        val now = android.os.SystemClock.uptimeMillis()
        val old = GameOrigin.rect
        if (GameOrigin.key == key && old != null && now - GameOrigin.markedAt < 120L && r != old &&
            r.width * r.height < old.width * old.height) return@onGloballyPositioned
        GameOrigin.key = key
        GameOrigin.rect = r
        GameOrigin.markedAt = now
        GameOrigin.displayId = runCatching { view.display?.displayId }.getOrNull() ?: -1
    }
}

/** Update 119: what the game window can show where it has room (Home › Layout options › Game window). */
internal enum class WinPart(val label: String) {
    MEDIA("Its disc / cartridge"), DESCRIPTION("Description"), FACTS("Facts"), ACHIEVEMENTS("Achievements"), ICONS("Options, trophies & sync buttons")
}

/**
 * Update 125: where each part of the game window goes, per layout (Home › Layout options › Game window): on the
 * picture, by the game's name (the window's info place), under the buttons, or not at all.
 */
internal enum class WinSlot(val label: String) { PICTURE("On the picture"), INFO("By the name"), BOTTOM("Under the buttons"), OFF("Off") }

/** Update 125: the window's pages — the picture, the details, the game's options and its achievements, all in its style. */
internal enum class WinPage { MAIN, DETAILS, OPTIONS, TROPHIES, PERF }

/**
 * Update 213: the picked game's performance and save sync for the window's parts (GamePopupStatus.kt): the Performance
 * page and button, and the small status line under the buttons on every page.
 */
internal class WinStatus(
    val perf: GamePerfState?,
    val sync: GameSyncView,
    val hasFan: Boolean?,
    val onSync: () -> Unit,
    val onSyncSetUp: () -> Unit,
    val onPerf: () -> Unit,
    val onPerfAdvanced: () -> Unit,
    val perfFocus: FocusRequester
)
internal val LocalWinStatus = androidx.compose.runtime.staticCompositionLocalOf<WinStatus?> { null }

/** The label of the window's Performance button (update 213). */
private const val PERF_LABEL = "Performance"

/** Update 125: one of the game's options, shown on the window's own options page. */
internal class WinOption(val label: String, val sub: String? = null, val destructive: Boolean = false, val onClick: () -> Unit)

/**
 * Update 125: each layout's own placement of the window's parts, kept in Features.gameWindowPlan as
 * "LAYOUT.PART=SLOT" entries. A layout without its own keeps the old every-layout choice (Features.gameWindowShows):
 * shown parts in their usual place, the others off.
 */
internal object WinPlan {
    fun usual(part: WinPart): WinSlot = if (part == WinPart.MEDIA) WinSlot.PICTURE else WinSlot.INFO

    private fun parse(raw: String): Map<String, WinSlot> = raw.split(',').mapNotNull { e ->
        val k = e.substringBefore('=', "").trim(); val v = e.substringAfter('=', "").trim()
        WinSlot.values().firstOrNull { it.name == v }?.let { if (k.isNotEmpty()) k to it else null }
    }.toMap()

    /** [layout]'s placement of every part (read in composition: redraws when it changes). */
    fun of(layout: HomeLayout): Map<WinPart, WinSlot> {
        val saved = parse(Features.gameWindowPlan.state.value)
        val shows = Features.gameWindowShows.state.value
        return WinPart.values().associateWith { p -> saved["${layout.name}.${p.name}"] ?: if (p.name in shows) usual(p) else WinSlot.OFF }
    }

    fun set(layout: HomeLayout, part: WinPart, slot: WinSlot) {
        val m = parse(Features.gameWindowPlan.value).toMutableMap()
        m["${layout.name}.${part.name}"] = slot
        Features.gameWindowPlan.value = m.entries.joinToString(",") { "${it.key}=${it.value.name}" }
    }
}

/** Update 119: where the TV room's screen is (in the window), so the game window opens on the TV itself. */
internal object TvScreen {
    var rect: Rect? = null
    /** The display it's on (the TV room drawn on the second screen, with the games below, doesn't count). */
    var displayId: Int = -1
}

/** SHOWCASE (update 119, the Grid): the game's disc / cartridge on top in the middle, its name and story under it. */
private enum class Arrange { STACK, SPLIT, LIST, FULL, SHOWCASE }
private enum class Btn { PILL, ROUNDED, SQUARE, LCD, TEXT, TAB }

/** How one layout draws its game window. */
private class WinLook(
    val arrange: Arrange,
    val btn: Btn,
    val veil: Brush,
    /** The window's own panel (null: the parts float on the veil). */
    val panel: Brush?,
    val frame: Color?,
    val frameW: Dp,
    val corner: Dp,
    val mediaCorner: Dp,
    /** A frame round the picture (the TV's bezel, the handheld's screen edge…). */
    val mediaFrame: Color?,
    val mediaFrameW: Dp,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val btnFill: Brush,
    val btnInk: Color,
    val btnOnFill: Brush,
    val btnOnInk: Color,
    val btnBorder: Color?,
    val back: String,
    val details: String,
    val play: String,
    val mono: Boolean = false,
    val caps: Boolean = false,
    /** The details page's own paper (null: the panel's). */
    val paper: Color? = null,
    /** Update 330: faint vertical pinstripes over the panel (Pocket classic's paper), null: none. */
    val stripes: Color? = null,
    /**
     * Update 330: the layout's own colours ARE its look (Pocket classic's paper, the memory-card screen's grey): "The
     * game's colours" / "Theme colours" then only colour its accent (the lit button, the picture's frame), never the panel.
     */
    val keepsPalette: Boolean = false
)

private fun solid(c: Color): Brush = Brush.verticalGradient(listOf(c, c))

private fun mix(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)

/**
 * Update 119: the window in [tint]'s colours (the game's, from its cover, or the theme's) — the panel, the veil, the
 * buttons and the details page — keeping the layout's shapes, words and type. The windows were mostly white or grey.
 */
private fun WinLook.tinted(tint: Color): WinLook {
    if (keepsPalette) {
        // A colour that reads on the layout's own panel: the tint itself, a little deeper on a pale panel.
        val a = if (ink.luminance() < 0.4f) mix(tint, Color.Black, 0.18f) else mix(tint, Color.White, 0.25f)
        val onA = if (a.luminance() > 0.55f) Color(0xFF1C2026) else Color.White
        return WinLook(
            arrange, btn, veil, panel, frame, frameW, corner, mediaCorner,
            mediaFrame = if (mediaFrame != null) a else null, mediaFrameW = mediaFrameW,
            ink = ink, muted = muted, accent = a, btnFill = btnFill, btnInk = btnInk, btnOnFill = solid(a), btnOnInk = onA,
            btnBorder = btnBorder, back = back, details = details, play = play, mono = mono, caps = caps, paper = paper,
            stripes = stripes, keepsPalette = true
        )
    }
    val deep = mix(tint, Color.Black, 0.55f)
    val rich = mix(tint, Color.Black, 0.25f)
    val glow = mix(tint, Color.White, 0.55f)
    return WinLook(
        arrange, btn,
        veil = Brush.verticalGradient(listOf(deep.copy(alpha = 0.78f), Color.Black.copy(alpha = 0.82f))),
        panel = if (panel != null) Brush.verticalGradient(listOf(rich, deep)) else null,
        frame = if (frame != null) glow.copy(alpha = 0.6f) else null, frameW = frameW, corner = corner, mediaCorner = mediaCorner,
        mediaFrame = if (mediaFrame != null) mix(tint, Color.Black, 0.75f) else null, mediaFrameW = mediaFrameW,
        ink = Color.White, muted = Color.White.copy(alpha = 0.74f), accent = glow,
        btnFill = solid(Color.White.copy(alpha = 0.13f)), btnInk = Color.White,
        btnOnFill = solid(glow), btnOnInk = deep,
        btnBorder = if (btnBorder != null) glow.copy(alpha = 0.45f) else null,
        back = back, details = details, play = play, mono = mono, caps = caps,
        paper = mix(tint, Color.Black, 0.7f)
    )
}

@Composable
private fun lookFor(layout: HomeLayout, resumed: Boolean): WinLook {
    val t = ShelfTheme.colors
    val acc = ShelfTheme.accent
    // Update 144: text on an accent fill is dark when the accent is light (Mono's white, pale accents), so
    // "The layout's own" colours don't put white words on a white button.
    val lightAcc = acc.luminance() > 0.5f
    val onAcc = if (lightAcc) Color(0xFF111111) else Color.White
    val playWord = if (resumed) "Continue" else "Play"
    return when (layout) {
        // The Channel grid: a rounded screen on a see-through white (or dark) veil, round-ended buttons.
        HomeLayout.CHAN -> {
            // (Update 329: the Dark version takes the dark window.)
            val light = Features.chanLight.state.value && !Features.chanDark.state.value
            val ink = if (light) Color(0xFF3B4450) else Color.White
            WinLook(
                Arrange.STACK, Btn.PILL,
                veil = solid(if (light) Color(0xFFF6F8FB).copy(alpha = 0.82f) else Color(0xFF0E1116).copy(alpha = 0.86f)),
                panel = null, frame = null, frameW = 0.dp, corner = 22.dp, mediaCorner = 22.dp,
                mediaFrame = if (light) Color(0xFFB9C3D0) else Color(0xFF3A4350), mediaFrameW = 4.dp,
                ink = ink, muted = ink.copy(alpha = 0.7f), accent = acc,
                btnFill = if (light) Brush.verticalGradient(listOf(Color.White, Color(0xFFDCE2EA))) else Brush.verticalGradient(listOf(Color(0xFF323A46), Color(0xFF1C2129))),
                btnInk = ink,
                btnOnFill = if (light) Brush.verticalGradient(listOf(Color.White, Color(0xFFCFE3F7))) else Brush.verticalGradient(listOf(Color(0xFF3B5470), Color(0xFF22303F))),
                btnOnInk = ink, btnBorder = Color(0xFF8FB4D8),
                back = "Home", details = "Details", play = "Start",
                paper = if (light) Color.White else Color(0xFF1A1F27)
            )
        }
        // The Circuit board: an LCD panel, monospace, buttons as LCD keys.
        HomeLayout.CIRCUIT -> {
            val ink = if (Features.circuitColors.state.value == Features.CircuitColors.GREEN) Color(0xFF7CE0A0) else acc
            val lcd = when (Features.circuitColors.state.value) {
                Features.CircuitColors.THEME -> t.surface
                Features.CircuitColors.GREEN -> Color(0xFF0D1410)
                Features.CircuitColors.DARK -> Color(0xFF0E1013)
            }
            WinLook(
                Arrange.SPLIT, Btn.LCD,
                veil = solid(Color.Black.copy(alpha = 0.78f)),
                panel = solid(lcd), frame = ink, frameW = 2.dp, corner = 6.dp, mediaCorner = 4.dp,
                mediaFrame = ink.copy(alpha = 0.6f), mediaFrameW = 1.dp,
                ink = Color.White, muted = Color.White.copy(alpha = 0.65f), accent = ink,
                btnFill = solid(lcd), btnInk = Color.White.copy(alpha = 0.8f), btnOnFill = solid(ink.copy(alpha = 0.35f)), btnOnInk = Color.White,
                btnBorder = ink.copy(alpha = 0.7f),
                back = "BACK", details = "DETAILS", play = if (resumed) "CONTINUE" else "PLAY", mono = true, caps = true
            )
        }
        // Update 202: Green Core — a dark green machine panel with a thin lit frame, letter-spaced caps and square keys
        // that light up in the layout's colour (Settings › Home › Layout options › Accent colour).
        HomeLayout.GREENCORE -> {
            val gc = greenCoreAccent()
            WinLook(
                Arrange.SPLIT, Btn.SQUARE,
                veil = Brush.verticalGradient(listOf(Color(0xE6020A05), Color(0xF0010402))),
                panel = Brush.verticalGradient(listOf(Color(0xFF0D1C12), Color(0xFF050D07))), frame = gc.copy(alpha = 0.7f), frameW = 1.5.dp,
                corner = 3.dp, mediaCorner = 2.dp, mediaFrame = gc.copy(alpha = 0.4f), mediaFrameW = 1.dp,
                ink = Color(0xFFE4FBE8), muted = Color(0xFF9FC7A8), accent = gc,
                btnFill = solid(Color(0xFF0F2416)), btnInk = Color(0xFFCDEED4),
                btnOnFill = Brush.horizontalGradient(listOf(gc.copy(alpha = 0.9f), gc.copy(alpha = 0.6f))), btnOnInk = Color(0xFF02110A),
                btnBorder = gc.copy(alpha = 0.5f),
                back = "BACK", details = "DETAILS", play = if (resumed) "RESUME" else "PLAY", caps = true,
                paper = Color(0xFF07120B)
            )
        }
        // The Cross menu: no panel, a soft wave of colour, the choices as a list of words that glow.
        HomeLayout.CROSS -> WinLook(
            Arrange.LIST, Btn.TEXT,
            veil = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), acc.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.75f))),
            panel = null, frame = null, frameW = 0.dp, corner = 0.dp, mediaCorner = 4.dp, mediaFrame = null, mediaFrameW = 0.dp,
            ink = Color.White, muted = Color.White.copy(alpha = 0.7f), accent = Color.White,
            btnFill = solid(Color.Transparent), btnInk = Color.White.copy(alpha = 0.6f), btnOnFill = solid(Color.Transparent), btnOnInk = Color.White, btnBorder = null,
            back = "Back", details = "Information", play = if (resumed) "Continue game" else "Start game"
        )
        // The side menu: the choices in a column on the left of the picture.
        HomeLayout.SIDE -> WinLook(
            Arrange.LIST, Btn.ROUNDED,
            veil = solid(Color.Black.copy(alpha = 0.7f)),
            panel = solid(t.surface.copy(alpha = 0.96f)), frame = t.surfaceRaised, frameW = 1.dp, corner = 18.dp, mediaCorner = 12.dp,
            mediaFrame = null, mediaFrameW = 0.dp,
            ink = t.textPrimary, muted = ShelfTheme.textMuted, accent = acc,
            btnFill = solid(t.surfaceRaised), btnInk = t.textPrimary, btnOnFill = solid(acc), btnOnInk = onAcc, btnBorder = null,
            back = "Back", details = "Details", play = playWord
        )
        // Live tiles: flat, square, one solid colour.
        HomeLayout.LIVE -> WinLook(
            Arrange.SPLIT, Btn.SQUARE,
            veil = solid(Color.Black.copy(alpha = 0.75f)),
            panel = solid(acc), frame = null, frameW = 0.dp, corner = 0.dp, mediaCorner = 0.dp, mediaFrame = null, mediaFrameW = 0.dp,
            // (The accent is also drawn on the dark details paper, so on a light accent it's a mid grey that shows on both.)
            ink = onAcc, muted = onAcc.copy(alpha = 0.78f), accent = if (lightAcc) Color(0xFF6A6A6A) else Color.White,
            // Update 144: on a light accent the buttons are darker and the focused one is near-black with the
            // accent's words, so neither is white on white. (White words stay readable on the dark details paper.)
            // The unfocused ones are a solid mid grey there, not a see-through black: on the dark paper of the
            // Options / Achievements pages that would be as black as the focused row, and you couldn't see the selection.
            btnFill = solid(if (lightAcc) Color(0xFF5E5E5E) else Color.Black.copy(alpha = 0.22f)), btnInk = Color.White,
            btnOnFill = solid(if (lightAcc) Color(0xFF111111) else Color.White), btnOnInk = acc, btnBorder = null,
            back = "Back", details = "Details", play = if (resumed) "Continue" else "Play",
            paper = Color(0xFF1D1D1D)
        )
        // The dashboard: a dark card with flat accent buttons.
        HomeLayout.DASH -> WinLook(
            Arrange.SPLIT, Btn.SQUARE,
            veil = solid(Color.Black.copy(alpha = 0.72f)),
            panel = Brush.verticalGradient(listOf(t.surfaceRaised, t.surface)), frame = null, frameW = 0.dp, corner = 6.dp, mediaCorner = 4.dp,
            mediaFrame = null, mediaFrameW = 0.dp,
            ink = t.textPrimary, muted = ShelfTheme.textMuted, accent = acc,
            btnFill = solid(t.background.copy(alpha = 0.6f)), btnInk = t.textPrimary, btnOnFill = solid(acc), btnOnInk = onAcc, btnBorder = null,
            back = "Back", details = "Details", play = playWord
        )
        // The grid: a rounded card — the game's disc / cartridge on top in the middle, then its name and story (update 119).
        HomeLayout.GRID -> WinLook(
            Arrange.SHOWCASE, Btn.ROUNDED,
            veil = solid(Color.Black.copy(alpha = 0.66f)),
            panel = solid(t.surface), frame = t.surfaceRaised, frameW = 1.dp, corner = 16.dp, mediaCorner = 10.dp,
            mediaFrame = null, mediaFrameW = 0.dp,
            ink = t.textPrimary, muted = ShelfTheme.textMuted, accent = acc,
            btnFill = solid(t.surfaceRaised), btnInk = t.textPrimary, btnOnFill = solid(acc), btnOnInk = onAcc, btnBorder = null,
            back = "Back", details = "Details", play = playWord
        )
        // The shelf: the box pulled out on a wooden board, the choices as wooden labels.
        HomeLayout.SHELF -> WinLook(
            Arrange.SPLIT, Btn.TAB,
            veil = solid(Color(0xFF120B06).copy(alpha = 0.78f)),
            panel = Brush.verticalGradient(listOf(Color(0xFF7A5232), Color(0xFF54351F))), frame = Color(0xFF24150B), frameW = 2.dp, corner = 8.dp,
            mediaCorner = 3.dp, mediaFrame = null, mediaFrameW = 0.dp,
            ink = Color(0xFFFFF3E2), muted = Color(0xFFFFE2BE).copy(alpha = 0.75f), accent = Color(0xFFFFD08A),
            btnFill = Brush.verticalGradient(listOf(Color(0xFFB98757), Color(0xFF9C6D43))), btnInk = Color(0xFF2A1A0E),
            btnOnFill = Brush.verticalGradient(listOf(Color(0xFFF2D3A6), Color(0xFFD9AE76))), btnOnInk = Color(0xFF2A1A0E), btnBorder = Color(0xFF24150B),
            back = "Put back", details = "Turn over", play = playWord,
            paper = Color(0xFF3A2516)
        )
        // Hero carousel: the picture fills the window, the name and big buttons over its foot.
        HomeLayout.HERO -> WinLook(
            Arrange.FULL, Btn.PILL,
            veil = solid(Color.Black.copy(alpha = 0.8f)),
            panel = solid(Color.Black), frame = null, frameW = 0.dp, corner = 14.dp, mediaCorner = 14.dp, mediaFrame = null, mediaFrameW = 0.dp,
            ink = Color.White, muted = Color.White.copy(alpha = 0.75f), accent = acc,
            btnFill = solid(Color.White.copy(alpha = 0.16f)), btnInk = Color.White, btnOnFill = solid(Color.White), btnOnInk = Color(0xFF111111), btnBorder = null,
            back = "Back", details = "Details", play = "▶  $playWord",
            paper = Color(0xFF14161B)
        )
        // The TV room: the picture on a TV with a thick bezel, round buttons like a remote's.
        HomeLayout.TV -> WinLook(
            Arrange.STACK, Btn.PILL,
            veil = solid(Color(0xFF07080A).copy(alpha = 0.85f)),
            panel = null, frame = null, frameW = 0.dp, corner = 16.dp, mediaCorner = 10.dp,
            mediaFrame = Color(0xFF1B1C20), mediaFrameW = 14.dp,
            ink = Color.White, muted = Color.White.copy(alpha = 0.7f), accent = acc,
            btnFill = Brush.verticalGradient(listOf(Color(0xFF2E3036), Color(0xFF1A1B1F))), btnInk = Color.White,
            btnOnFill = solid(acc), btnOnInk = onAcc, btnBorder = Color(0xFF45474F),
            back = "Back", details = "Info", play = playWord
        )
        // Resume & discover and the hub: a glass card.
        HomeLayout.RESUME, HomeLayout.HUB -> WinLook(
            Arrange.STACK, Btn.ROUNDED,
            veil = solid(Color.Black.copy(alpha = 0.7f)),
            panel = Brush.verticalGradient(listOf(t.surfaceRaised.copy(alpha = 0.92f), t.surface.copy(alpha = 0.95f))), frame = Color.White.copy(alpha = 0.14f), frameW = 1.dp,
            corner = 18.dp, mediaCorner = 12.dp, mediaFrame = null, mediaFrameW = 0.dp,
            ink = t.textPrimary, muted = ShelfTheme.textMuted, accent = acc,
            btnFill = solid(Color.White.copy(alpha = 0.08f)), btnInk = t.textPrimary, btnOnFill = solid(acc), btnOnInk = onAcc, btnBorder = null,
            back = "Back", details = "Details", play = if (layout == HomeLayout.RESUME && resumed) "Resume" else playWord
        )
        // Spotlight and the icon bar: a light, rounded panel.
        HomeLayout.SPOT, HomeLayout.BAR -> WinLook(
            Arrange.STACK, Btn.PILL,
            veil = solid(Color.Black.copy(alpha = 0.62f)),
            panel = solid(Color(0xFFF2F3F5)), frame = null, frameW = 0.dp, corner = 20.dp, mediaCorner = 14.dp, mediaFrame = null, mediaFrameW = 0.dp,
            ink = Color(0xFF1E2228), muted = Color(0xFF5E6672), accent = acc,
            btnFill = solid(Color(0xFFE1E4E9)), btnInk = Color(0xFF1E2228), btnOnFill = solid(acc), btnOnInk = onAcc, btnBorder = null,
            back = "Back", details = "Details", play = playWord,
            paper = Color.White
        )
        // Update 330 (from PR #12): Pocket classic — the 2004 pocket menu's pale paper screen with faint pinstripes, sharp
        // grey borders, monospace capital labels, the picture and the lit button in the layout's own accent
        // (Layout options › Accent colour). The whole window pipeline (Play, details, achievements, options, saves, Inspect,
        // performance) is the shared one.
        HomeLayout.POCKET_CLASSIC -> {
            val pc = pocketAccent(PocketAccent.values().firstOrNull { it.name == Features.classicAccent.state.value } ?: PocketAccent.BLUE)
            val onPc = if (pc.luminance() > 0.55f) Color(0xFF24303B) else Color.White
            WinLook(
                Arrange.SPLIT, Btn.LCD,
                veil = solid(Color(0xFF101823).copy(alpha = 0.79f)),
                panel = Brush.verticalGradient(listOf(Color(0xFFF7F8FA), Color(0xFFE8EBEF))),
                frame = Color(0xFF929AA5), frameW = 2.dp, corner = 3.dp, mediaCorner = 2.dp,
                mediaFrame = pc, mediaFrameW = 2.dp,
                ink = Color(0xFF303943), muted = Color(0xFF5B6674), accent = pc,
                btnFill = solid(Color(0xFFF7F8FA)), btnInk = Color(0xFF303943),
                btnOnFill = solid(pc), btnOnInk = onPc, btnBorder = Color(0xFF929AA5),
                back = "BACK", details = "GAME INFO", play = if (resumed) "CONTINUE" else "START",
                mono = true, caps = true, paper = Color(0xFFFFFFFF), stripes = Color(0xFFE3E7EC).copy(alpha = 0.55f),
                keepsPalette = true
            )
        }
        // Update 330: Memory card — the memory-card screen's own window: smoky grey-blue glass with a thin light edge, white
        // words with the browser's yellow for the lit button and the title's accent, plain text buttons.
        HomeLayout.MEMCARD -> WinLook(
            Arrange.SPLIT, Btn.ROUNDED,
            veil = Brush.verticalGradient(listOf(Color(0xFF3A3D44).copy(alpha = 0.55f), Color(0xFF0E1014).copy(alpha = 0.82f))),
            panel = Brush.verticalGradient(listOf(Color(0xFF5B6270).copy(alpha = 0.92f), Color(0xFF2A2F38).copy(alpha = 0.96f))),
            frame = Color.White.copy(alpha = 0.55f), frameW = 1.dp, corner = 8.dp, mediaCorner = 4.dp,
            mediaFrame = Color.White.copy(alpha = 0.35f), mediaFrameW = 1.dp,
            ink = Color.White, muted = Color(0xFFD4D8E0), accent = Color(0xFFF6DA4E),
            btnFill = solid(Color.White.copy(alpha = 0.10f)), btnInk = Color.White,
            btnOnFill = solid(Color(0xFFF6DA4E)), btnOnInk = Color(0xFF22252B), btnBorder = Color.White.copy(alpha = 0.3f),
            back = "Back", details = "Details", play = if (resumed) "Continue" else "Start",
            paper = Color(0xFF2A2F38), keepsPalette = true
        )
        // Update 330: Case flow — graphite glass under the case that came forward: the case beside the details, round-ended
        // buttons in your accent, a fine light edge.
        HomeLayout.CASEFLOW -> WinLook(
            Arrange.SPLIT, Btn.PILL,
            veil = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Black.copy(alpha = 0.86f))),
            panel = Brush.verticalGradient(listOf(Color(0xFF2A2D35).copy(alpha = 0.94f), Color(0xFF14161B).copy(alpha = 0.97f))),
            frame = Color.White.copy(alpha = 0.18f), frameW = 1.dp, corner = 16.dp, mediaCorner = 6.dp,
            mediaFrame = null, mediaFrameW = 0.dp,
            ink = Color.White, muted = Color(0xFFB9BEC9), accent = acc,
            btnFill = solid(Color.White.copy(alpha = 0.09f)), btnInk = Color.White, btnOnFill = solid(acc), btnOnInk = onAcc,
            btnBorder = Color.White.copy(alpha = 0.16f),
            back = "Back", details = "Details", play = playWord,
            paper = Color(0xFF16181D)
        )
        // Update 330: Cardboard box — a storage label: kraft paper, typewriter capitals, a red stamp for the lit button.
        HomeLayout.BOX -> WinLook(
            Arrange.SPLIT, Btn.SQUARE,
            veil = Brush.verticalGradient(listOf(Color(0xFF2A221A).copy(alpha = 0.6f), Color.Black.copy(alpha = 0.85f))),
            panel = Brush.verticalGradient(listOf(Color(0xFFE8D7B4), Color(0xFFD6C08F))),
            frame = Color(0xFF8A6A3C), frameW = 2.dp, corner = 3.dp, mediaCorner = 2.dp,
            mediaFrame = Color(0xFFF7F1E2), mediaFrameW = 4.dp,
            ink = Color(0xFF2B2117), muted = Color(0xFF5E4B33), accent = Color(0xFFB8322A),
            btnFill = solid(Color(0xFFF4EAD3)), btnInk = Color(0xFF2B2117),
            btnOnFill = solid(Color(0xFFB8322A)), btnOnInk = Color(0xFFFFF4E4), btnBorder = Color(0xFF8A6A3C),
            back = "BACK", details = "CONTENTS", play = if (resumed) "CONTINUE" else "PLAY",
            mono = true, caps = true, paper = Color(0xFFF1E6CC), keepsPalette = true
        )
        // Update 330: Storage boxes — the box's entry card: a pale panel under a coloured title band, rounded, friendly.
        HomeLayout.STORAGE -> WinLook(
            Arrange.SPLIT, Btn.ROUNDED,
            veil = Brush.verticalGradient(listOf(Color(0xFF10233A).copy(alpha = 0.6f), Color(0xFF07111D).copy(alpha = 0.86f))),
            panel = Brush.verticalGradient(0f to Color(0xFF4C6A8E), 0.13f to Color(0xFF4C6A8E), 0.13f to Color(0xFFF7F9FC), 1f to Color(0xFFDDE4EE)),
            frame = Color(0xFF2C4766), frameW = 3.dp, corner = 12.dp, mediaCorner = 8.dp,
            mediaFrame = Color(0xFF6E98C4), mediaFrameW = 3.dp,
            ink = Color(0xFF1E2A3A), muted = Color(0xFF4C5E76), accent = Color(0xFFFF5A4E),
            btnFill = solid(Color.White), btnInk = Color(0xFF1E2A3A), btnOnFill = solid(Color(0xFFFF5A4E)), btnOnInk = Color.White,
            btnBorder = Color(0xFF4C6A8E),
            back = "Back", details = "Summary", play = playWord,
            paper = Color(0xFFF7F9FC), keepsPalette = true
        )
        // Update 330: File select — the block opened out: chunky blue with a thick light bevel, yellow for the lit button.
        HomeLayout.FILESELECT -> WinLook(
            Arrange.SPLIT, Btn.ROUNDED,
            veil = Brush.verticalGradient(listOf(Color(0xFF101A52).copy(alpha = 0.6f), Color(0xFF070B2A).copy(alpha = 0.88f))),
            panel = Brush.verticalGradient(listOf(Color(0xFF5B8FF0), Color(0xFF2F63D0), Color(0xFF2350B0))),
            frame = Color(0xFFBFD6FF), frameW = 4.dp, corner = 20.dp, mediaCorner = 10.dp,
            mediaFrame = Color(0xFFBFD6FF), mediaFrameW = 4.dp,
            ink = Color.White, muted = Color(0xFFDDE8FF), accent = Color(0xFFFFE04A),
            btnFill = solid(Color(0xFF1C3F94)), btnInk = Color.White, btnOnFill = solid(Color(0xFFFFE04A)), btnOnInk = Color(0xFF2A2200),
            btnBorder = Color(0xFFBFD6FF),
            back = "Back", details = "Details", play = if (resumed) "Continue" else "Start",
            paper = Color(0xFF1C3F94), keepsPalette = true
        )
        // Update 202: Pocket Quest — a deep blue / indigo panel with a crisp accent (gold) edge and nearly square corners,
        // the picture in a thin accent frame, square buttons; the lit one solid accent with dark words (its theme's palette).
        HomeLayout.POCKET -> {
            val q = pqPalette()
            WinLook(
                Arrange.SPLIT, Btn.SQUARE,
                veil = Brush.verticalGradient(listOf(q.edgeDark.copy(alpha = 0.72f), Color.Black.copy(alpha = 0.84f))),
                panel = Brush.verticalGradient(listOf(q.top, q.bottom)), frame = q.accent, frameW = 2.dp, corner = 4.dp, mediaCorner = 2.dp,
                mediaFrame = q.accentDeep, mediaFrameW = 2.dp,
                ink = q.ink, muted = q.muted, accent = q.accent,
                btnFill = solid(q.deep), btnInk = q.ink,
                btnOnFill = Brush.verticalGradient(listOf(q.accentLight, q.accent, q.accentDeep)), btnOnInk = q.onAccent,
                btnBorder = q.accentDeep,
                back = "Back", details = "Status", play = if (resumed) "Continue" else "Play Game",
                paper = q.deep
            )
        }
        // ---- Update 330, phase 4: the older layouts that still took the Voltcap row's glass window get their own. ----
        // Arcade cabinets: the cabinet's black body under a neon marquee edge, square arcade buttons, capitals.
        HomeLayout.ARCADE -> WinLook(
            Arrange.SPLIT, Btn.SQUARE,
            veil = Brush.verticalGradient(listOf(Color(0xFF12001F).copy(alpha = 0.66f), Color.Black.copy(alpha = 0.88f))),
            panel = Brush.verticalGradient(listOf(Color(0xFF1A1A22), Color(0xFF0B0B10))),
            frame = Color(0xFFFF3DB4), frameW = 3.dp, corner = 6.dp, mediaCorner = 3.dp,
            mediaFrame = Color(0xFF3DE7FF), mediaFrameW = 3.dp,
            ink = Color.White, muted = Color(0xFFB9B4CC), accent = Color(0xFFFFE23D),
            btnFill = solid(Color(0xFF2A2833)), btnInk = Color.White,
            btnOnFill = Brush.verticalGradient(listOf(Color(0xFFFFF08A), Color(0xFFFFC21A))), btnOnInk = Color(0xFF231A00),
            btnBorder = Color(0xFF3DE7FF).copy(alpha = 0.7f),
            back = "EXIT", details = "GAME INFO", play = if (resumed) "CONTINUE" else "PRESS START",
            caps = true, paper = Color(0xFF0B0B18), keepsPalette = true
        )
        // Console stack: the console's matte plastic body, a thin power-LED edge, rounded keys like its front buttons.
        HomeLayout.CONSOLES -> WinLook(
            Arrange.SPLIT, Btn.ROUNDED,
            veil = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.86f))),
            panel = Brush.verticalGradient(listOf(Color(0xFF3A3E46), Color(0xFF23262C), Color(0xFF1A1C21))),
            frame = acc.copy(alpha = 0.7f), frameW = 2.dp, corner = 10.dp, mediaCorner = 4.dp,
            mediaFrame = Color(0xFF0E0F12), mediaFrameW = 3.dp,
            ink = Color(0xFFEDEFF3), muted = Color(0xFFAEB4BF), accent = acc,
            btnFill = solid(Color(0xFF2D3037)), btnInk = Color(0xFFEDEFF3), btnOnFill = solid(acc), btnOnInk = onAcc,
            btnBorder = Color.White.copy(alpha = 0.12f),
            back = "Back", details = "Details", play = if (resumed) "Continue" else "Power on",
            paper = Color(0xFF1A1C21)
        )
        // Disc station: the player's brushed-silver front with its blue display and LCD keys.
        HomeLayout.DISCS -> WinLook(
            Arrange.SPLIT, Btn.LCD,
            veil = Brush.verticalGradient(listOf(Color(0xFF0A1220).copy(alpha = 0.62f), Color.Black.copy(alpha = 0.86f))),
            panel = Brush.verticalGradient(listOf(Color(0xFFD9DEE5), Color(0xFFAEB5BF), Color(0xFF8D95A1))),
            frame = Color(0xFFF4F6F9), frameW = 2.dp, corner = 14.dp, mediaCorner = 6.dp,
            mediaFrame = Color(0xFF0F2742), mediaFrameW = 4.dp,
            ink = Color(0xFF1C222B), muted = Color(0xFF414A57), accent = Color(0xFF2E8BFF),
            btnFill = solid(Color(0xFFE9ECF1)), btnInk = Color(0xFF1C222B),
            btnOnFill = Brush.verticalGradient(listOf(Color(0xFF8CC4FF), Color(0xFF2E8BFF))), btnOnInk = Color.White,
            btnBorder = Color(0xFF6C7582),
            back = "■ Stop", details = "Info", play = if (resumed) "▶ Continue" else "▶ Play",
            paper = Color(0xFFF1F3F6), keepsPalette = true
        )
        // Hero tiles: the big picture on top, square tiles for the choices, flat dark acrylic.
        HomeLayout.HERO_TILES -> WinLook(
            Arrange.STACK, Btn.SQUARE,
            veil = solid(Color.Black.copy(alpha = 0.72f)),
            panel = Brush.verticalGradient(listOf(Color(0xFF1E2126).copy(alpha = 0.96f), Color(0xFF121418).copy(alpha = 0.98f))),
            frame = null, frameW = 0.dp, corner = 8.dp, mediaCorner = 6.dp, mediaFrame = null, mediaFrameW = 0.dp,
            ink = Color.White, muted = Color.White.copy(alpha = 0.7f), accent = acc,
            btnFill = solid(Color.White.copy(alpha = 0.1f)), btnInk = Color.White, btnOnFill = solid(acc), btnOnInk = onAcc, btnBorder = null,
            back = "Back", details = "Details", play = playWord,
            paper = Color(0xFF121418)
        )
        // Twin desk: the desk's light paper — white cards, soft grey keys, the lit one in the desk's blue.
        HomeLayout.TWIN_DESK -> WinLook(
            Arrange.SPLIT, Btn.ROUNDED,
            veil = solid(Color(0xFF1B2330).copy(alpha = 0.6f)),
            panel = Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF1F3F7))),
            frame = Color(0xFFDDE1E8), frameW = 1.dp, corner = 14.dp, mediaCorner = 10.dp, mediaFrame = null, mediaFrameW = 0.dp,
            ink = Color(0xFF1D2430), muted = Color(0xFF5D6676), accent = Color(0xFFE5484D),
            btnFill = solid(Color(0xFFEDEFF3)), btnInk = Color(0xFF1D2430), btnOnFill = solid(Color(0xFFE5484D)), btnOnInk = Color.White,
            btnBorder = null,
            back = "Back", details = "Details", play = playWord,
            paper = Color.White, keepsPalette = true
        )
        // Twin icons: the icon grid's pale shell — rounded squares, a light ring for the lit key.
        HomeLayout.TWIN_ICONS -> WinLook(
            Arrange.SPLIT, Btn.ROUNDED,
            veil = solid(Color(0xFF14181F).copy(alpha = 0.62f)),
            panel = Brush.verticalGradient(listOf(Color(0xFFF4F6F8), Color(0xFFDDE2E8))),
            frame = Color(0xFFB9C1CC), frameW = 2.dp, corner = 18.dp, mediaCorner = 14.dp,
            mediaFrame = Color.White, mediaFrameW = 4.dp,
            ink = Color(0xFF242A33), muted = Color(0xFF5A6370), accent = acc,
            btnFill = solid(Color.White), btnInk = Color(0xFF242A33), btnOnFill = solid(acc), btnOnInk = onAcc,
            btnBorder = Color(0xFFB9C1CC),
            back = "Back", details = "Details", play = playWord,
            paper = Color.White, keepsPalette = true
        )
        // Twin showcase: the showcase's dark stage, rounded icon keys.
        HomeLayout.TWIN_SHOWCASE -> WinLook(
            Arrange.SPLIT, Btn.ROUNDED,
            veil = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Black.copy(alpha = 0.88f))),
            panel = Brush.verticalGradient(listOf(Color(0xFF1B1E24).copy(alpha = 0.95f), Color(0xFF0E1013).copy(alpha = 0.97f))),
            frame = Color.White.copy(alpha = 0.16f), frameW = 1.dp, corner = 16.dp, mediaCorner = 12.dp, mediaFrame = null, mediaFrameW = 0.dp,
            ink = Color.White, muted = Color(0xFFB7BDC8), accent = acc,
            btnFill = solid(Color.White.copy(alpha = 0.1f)), btnInk = Color.White, btnOnFill = solid(acc), btnOnInk = onAcc, btnBorder = null,
            back = "Back", details = "Details", play = playWord,
            paper = Color(0xFF0E1013)
        )
        // Record crate: the sleeve's cream card in a wooden frame, black vinyl keys, the lit one the label's red.
        HomeLayout.CRATE -> WinLook(
            Arrange.SPLIT, Btn.PILL,
            veil = Brush.verticalGradient(listOf(Color(0xFF2A1A0E).copy(alpha = 0.6f), Color.Black.copy(alpha = 0.86f))),
            panel = Brush.verticalGradient(listOf(Color(0xFFF3E9D6), Color(0xFFE2D2B4))),
            frame = Color(0xFF7A4E2A), frameW = 5.dp, corner = 6.dp, mediaCorner = 2.dp,
            mediaFrame = Color(0xFF151515), mediaFrameW = 3.dp,
            ink = Color(0xFF2A1E14), muted = Color(0xFF5E4A36), accent = Color(0xFFD8402F),
            btnFill = Brush.radialGradient(listOf(Color(0xFF3A3A3A), Color(0xFF111111))), btnInk = Color(0xFFF3E9D6),
            btnOnFill = solid(Color(0xFFD8402F)), btnOnInk = Color(0xFFFFF4E4), btnBorder = Color(0xFF7A4E2A),
            back = "Back", details = "Sleeve notes", play = if (resumed) "Continue" else "Play",
            paper = Color(0xFFF3E9D6), keepsPalette = true
        )
        // Card binder: a collectible card — parchment inside a gold foil frame, capitals, the lit key foil gold.
        HomeLayout.CARDS -> WinLook(
            Arrange.SPLIT, Btn.ROUNDED,
            veil = Brush.verticalGradient(listOf(Color(0xFF1A1430).copy(alpha = 0.62f), Color.Black.copy(alpha = 0.86f))),
            panel = Brush.verticalGradient(listOf(Color(0xFFF7EFD9), Color(0xFFE6D6AE))),
            frame = Color(0xFFC9A23A), frameW = 5.dp, corner = 12.dp, mediaCorner = 6.dp,
            mediaFrame = Color(0xFF8C6A1C), mediaFrameW = 3.dp,
            ink = Color(0xFF2B2210), muted = Color(0xFF6A5A36), accent = Color(0xFFC9A23A),
            btnFill = solid(Color(0xFFFBF5E6)), btnInk = Color(0xFF2B2210),
            btnOnFill = Brush.linearGradient(listOf(Color(0xFFFFE9A3), Color(0xFFC9A23A), Color(0xFFFFE08A))), btnOnInk = Color(0xFF2B2210),
            btnBorder = Color(0xFFC9A23A),
            back = "BACK", details = "CARD TEXT", play = if (resumed) "CONTINUE" else "PLAY",
            caps = true, paper = Color(0xFFFBF5E6), keepsPalette = true
        )
        // Pocket ribbon: the 2008 pocket menu's white screen with its light-blue edges, a speech-bubble feel.
        HomeLayout.POCKET_RIBBON -> WinLook(
            Arrange.SPLIT, Btn.ROUNDED,
            veil = solid(Color(0xFF0E1620).copy(alpha = 0.66f)),
            panel = Brush.verticalGradient(listOf(Color.White, Color(0xFFEFF3F7))),
            frame = Color(0xFF8FA3B8), frameW = 3.dp, corner = 12.dp, mediaCorner = 4.dp,
            mediaFrame = Color(0xFF3FA2F0), mediaFrameW = 4.dp,
            ink = Color(0xFF2D3540), muted = Color(0xFF6B7684), accent = Color(0xFF3FA2F0),
            btnFill = solid(Color.White), btnInk = Color(0xFF2D3540), btnOnFill = solid(Color(0xFF3FA2F0)), btnOnInk = Color.White,
            btnBorder = Color(0xFF8FA3B8),
            back = "Back", details = "Info", play = if (resumed) "Continue" else "Start",
            paper = Color.White, keepsPalette = true
        )
        // Cube menu: the cube's indigo glass, a lilac edge, round-ended keys.
        HomeLayout.CUBE -> WinLook(
            Arrange.SPLIT, Btn.PILL,
            veil = Brush.verticalGradient(listOf(Color(0xFF140E33).copy(alpha = 0.6f), Color(0xFF06041A).copy(alpha = 0.88f))),
            panel = Brush.verticalGradient(listOf(Color(0xFF3B2F86).copy(alpha = 0.9f), Color(0xFF1A1450).copy(alpha = 0.95f))),
            frame = Color(0xFFB8A9FF).copy(alpha = 0.8f), frameW = 2.dp, corner = 18.dp, mediaCorner = 12.dp,
            mediaFrame = Color(0xFFB8A9FF).copy(alpha = 0.5f), mediaFrameW = 2.dp,
            ink = Color.White, muted = Color(0xFFD2CBFF), accent = Color(0xFFB8A9FF),
            btnFill = solid(Color.White.copy(alpha = 0.12f)), btnInk = Color.White, btnOnFill = solid(Color(0xFFE6E0FF)), btnOnInk = Color(0xFF1A1450),
            btnBorder = Color(0xFFB8A9FF).copy(alpha = 0.4f),
            back = "Back", details = "Details", play = playWord,
            paper = Color(0xFF1A1450), keepsPalette = true
        )
        // Library panel: the library's dark panel with its tab-shaped keys.
        HomeLayout.LIBRARY -> WinLook(
            Arrange.SPLIT, Btn.TAB,
            veil = solid(Color.Black.copy(alpha = 0.7f)),
            panel = Brush.verticalGradient(listOf(t.surfaceRaised.copy(alpha = 0.96f), t.surface.copy(alpha = 0.98f))),
            frame = Color.White.copy(alpha = 0.1f), frameW = 1.dp, corner = 8.dp, mediaCorner = 6.dp, mediaFrame = null, mediaFrameW = 0.dp,
            ink = t.textPrimary, muted = ShelfTheme.textMuted, accent = acc,
            btnFill = solid(Color.White.copy(alpha = 0.07f)), btnInk = t.textPrimary, btnOnFill = solid(acc), btnOnInk = onAcc, btnBorder = null,
            back = "Back", details = "Details", play = playWord
        )
        // Timeline: a slim card on the line of time — square-ish keys, the accent as the line.
        HomeLayout.TIMELINE -> WinLook(
            Arrange.SPLIT, Btn.ROUNDED,
            veil = solid(Color.Black.copy(alpha = 0.72f)),
            panel = Brush.verticalGradient(listOf(t.surfaceRaised.copy(alpha = 0.96f), t.surface.copy(alpha = 0.98f))),
            frame = acc, frameW = 2.dp, corner = 6.dp, mediaCorner = 4.dp, mediaFrame = acc.copy(alpha = 0.6f), mediaFrameW = 2.dp,
            ink = t.textPrimary, muted = ShelfTheme.textMuted, accent = acc,
            btnFill = solid(Color.White.copy(alpha = 0.08f)), btnInk = t.textPrimary, btnOnFill = solid(acc), btnOnInk = onAcc, btnBorder = acc.copy(alpha = 0.3f),
            back = "Back", details = "Details", play = playWord
        )
        // Case stacks: a case's hard plastic — square keys, a thin edge.
        HomeLayout.STACKS -> WinLook(
            Arrange.SPLIT, Btn.SQUARE,
            veil = solid(Color.Black.copy(alpha = 0.72f)),
            panel = Brush.verticalGradient(listOf(Color(0xFF24272D).copy(alpha = 0.96f), Color(0xFF15171B).copy(alpha = 0.98f))),
            frame = Color.White.copy(alpha = 0.2f), frameW = 1.dp, corner = 4.dp, mediaCorner = 2.dp, mediaFrame = null, mediaFrameW = 0.dp,
            ink = Color.White, muted = Color(0xFFB4BAC4), accent = acc,
            btnFill = solid(Color.White.copy(alpha = 0.09f)), btnInk = Color.White, btnOnFill = solid(acc), btnOnInk = onAcc,
            btnBorder = Color.White.copy(alpha = 0.14f),
            back = "Back", details = "Details", play = playWord,
            paper = Color(0xFF15171B)
        )
        // ---- Update 346: the update 344 layouts' own colours for the standard window and every page of their windows (Details,
        // Options, Achievements, Performance) — they took the Voltcap row's theme glass. Each follows its layout's colour option. ----
        // Blades: the lit blade's glossy colour, rounded-off tabs, the lit row the blade's pale glow.
        HomeLayout.BLADES -> {
            val bp = bladePalette()
            WinLook(
                Arrange.SPLIT, Btn.ROUNDED,
                veil = Brush.horizontalGradient(listOf(bp.bg.copy(alpha = 0.55f), bp.bg.copy(alpha = 0.9f))),
                panel = Brush.verticalGradient(listOf(bp.hi, lerp(bp.hi, bp.lo, 0.55f), bp.lo)),
                frame = bp.glow.copy(alpha = 0.55f), frameW = 2.dp, corner = 26.dp, mediaCorner = 4.dp,
                mediaFrame = Color.White.copy(alpha = 0.7f), mediaFrameW = 2.dp,
                ink = Color.White, muted = Color.White.copy(alpha = 0.78f), accent = bp.glow,
                btnFill = solid(Color.Black.copy(alpha = 0.16f)), btnInk = Color.White,
                btnOnFill = Brush.verticalGradient(listOf(Color.White, lerp(bp.glow, Color.White, 0.55f))), btnOnInk = bp.ink,
                btnBorder = Color.White.copy(alpha = 0.18f),
                back = "Back", details = "Details", play = playWord,
                paper = bp.lo, keepsPalette = true
            )
        }
        // Bubble home: the LiveArea's white glass with soft blue, round-ended glossy keys.
        HomeLayout.BUBBLES -> WinLook(
            Arrange.SPLIT, Btn.PILL,
            veil = Brush.verticalGradient(listOf(Color(0xFF1C4C8A).copy(alpha = 0.55f), Color(0xFF071A33).copy(alpha = 0.85f))),
            panel = Brush.verticalGradient(listOf(Color(0xFFFDFEFF), Color(0xFFE3ECF6))),
            frame = Color.White, frameW = 2.dp, corner = 24.dp, mediaCorner = 18.dp,
            mediaFrame = Color.White, mediaFrameW = 3.dp,
            ink = Color(0xFF16202C), muted = Color(0xFF55657A), accent = Color(0xFF2F86E0),
            btnFill = Brush.verticalGradient(listOf(Color.White, Color(0xFFE7EEF6))), btnInk = Color(0xFF16202C),
            btnOnFill = Brush.verticalGradient(listOf(Color(0xFF7CC0FF), Color(0xFF2F86E0))), btnOnInk = Color.White,
            btnBorder = Color(0xFFB9C9DB),
            back = "Back", details = "Details", play = if (resumed) "Continue" else "Start",
            paper = Color.White, keepsPalette = true
        )
        // Pocket camera: the camera's four shades only — the lightest paper, the darkest ink, square blocky keys, capitals.
        HomeLayout.CAMERA -> {
            val sh = cameraPalette(Features.pcPalette.state.value).map { Color(it) }
            WinLook(
                Arrange.SPLIT, Btn.SQUARE,
                veil = solid(sh[0].copy(alpha = 0.82f)),
                panel = solid(sh[3]), frame = sh[0], frameW = 4.dp, corner = 0.dp, mediaCorner = 0.dp,
                mediaFrame = sh[0], mediaFrameW = 3.dp,
                ink = sh[0], muted = sh[1], accent = sh[1],
                btnFill = solid(sh[3]), btnInk = sh[0], btnOnFill = solid(sh[0]), btnOnInk = sh[3], btnBorder = sh[1],
                back = "BACK", details = "INFO", play = if (resumed) "CONTINUE" else "START",
                mono = true, caps = true, paper = sh[2], keepsPalette = true
            )
        }
        // Panel dashboard: a white panel face on the channel's colour, flat square keys, the lit one the dashboard's green.
        HomeLayout.PANELS -> {
            val pp = panelPalette()
            WinLook(
                Arrange.SPLIT, Btn.SQUARE,
                veil = Brush.verticalGradient(listOf(pp.top.copy(alpha = 0.6f), pp.bottom.copy(alpha = 0.92f))),
                panel = Brush.verticalGradient(listOf(pp.panel, lerp(pp.panel, pp.bottom, 0.12f))),
                frame = Color.White.copy(alpha = 0.6f), frameW = 1.dp, corner = 2.dp, mediaCorner = 1.dp,
                mediaFrame = null, mediaFrameW = 0.dp,
                ink = pp.panelInk, muted = pp.panelInk.copy(alpha = 0.66f), accent = pp.accent,
                btnFill = solid(pp.panelInk.copy(alpha = 0.06f)), btnInk = pp.panelInk,
                btnOnFill = solid(lerp(pp.accent, pp.top, 0.35f)), btnOnInk = Color.White, btnBorder = null,
                back = "Back", details = "Details", play = playWord,
                paper = pp.panel, keepsPalette = true
            )
        }
        // Game magazine: the issue's paper and ink, its red (or green) for the lit button, the headline type.
        HomeLayout.MAGAZINE -> {
            val ms = magStyle(Features.mgStyle.state.value)
            WinLook(
                Arrange.SPLIT, Btn.SQUARE,
                veil = solid(ms.desk.copy(alpha = 0.82f)),
                panel = solid(ms.paper), frame = ms.ink.copy(alpha = 0.18f), frameW = 1.dp, corner = 1.dp, mediaCorner = 0.dp,
                mediaFrame = ms.ink, mediaFrameW = 2.dp,
                ink = ms.ink, muted = ms.muted, accent = ms.accent,
                btnFill = solid(ms.paper), btnInk = ms.ink, btnOnFill = solid(ms.accent), btnOnInk = Color.White, btnBorder = ms.ink,
                back = "BACK", details = "THE STORY", play = if (resumed) "CONTINUE!" else "PLAY IT!",
                mono = ms.body == androidx.compose.ui.text.font.FontFamily.Monospace, caps = true, paper = ms.paper, keepsPalette = true
            )
        }
        // Rental shop: the rental tag's yellow card, black keys, the lit one the shop's neon.
        HomeLayout.SHOP -> {
            val sc = shopColors(Features.rsWall.state.value)
            WinLook(
                Arrange.SPLIT, Btn.SQUARE,
                veil = Brush.verticalGradient(listOf(sc.wallTop.copy(alpha = 0.6f), sc.wallBottom.copy(alpha = 0.9f))),
                panel = Brush.verticalGradient(listOf(Color(0xFFFFE15C), Color(0xFFF5CF3A))),
                frame = Color(0xFF1A1508), frameW = 2.dp, corner = 3.dp, mediaCorner = 2.dp,
                mediaFrame = Color(0xFF1A1508), mediaFrameW = 2.dp,
                ink = Color(0xFF1A1508), muted = Color(0xFF4D4220), accent = sc.neon,
                btnFill = solid(Color(0xFF1A1508)), btnInk = Color(0xFFFFE15C), btnOnFill = solid(sc.neon), btnOnInk = Color(0xFF1A1508),
                btnBorder = null,
                back = "BACK", details = "CATALOGUE CARD", play = if (resumed) "CONTINUE" else "RENT & PLAY",
                caps = true, paper = Color(0xFFFFF4C2), keepsPalette = true
            )
        }
        // Terminal: the monitor itself — its phosphor on its glass, reverse video for the lit key, a fixed-width face.
        HomeLayout.TERMINAL -> {
            val tc = termColors(Features.tmTheme.state.value)
            WinLook(
                Arrange.SPLIT, Btn.TEXT,
                veil = solid(Color.Black.copy(alpha = 0.78f)),
                panel = solid(tc.bg), frame = tc.fg, frameW = 2.dp, corner = 0.dp, mediaCorner = 0.dp,
                mediaFrame = tc.dim, mediaFrameW = 1.dp,
                ink = tc.fg, muted = tc.dim, accent = tc.accent,
                btnFill = solid(tc.bg), btnInk = tc.fg, btnOnFill = solid(tc.hi), btnOnInk = tc.hiInk, btnBorder = null,
                back = "ESC", details = "TYPE NFO", play = if (resumed) "RESUME" else "RUN",
                mono = true, caps = true, paper = tc.bg, keepsPalette = true
            )
        }
        // The Voltcap row (and anything else): the theme's own glass panel, round-ended buttons.
        else -> WinLook(
            Arrange.STACK, Btn.PILL,
            veil = solid(Color.Black.copy(alpha = 0.68f)),
            panel = Brush.verticalGradient(listOf(t.surfaceRaised.copy(alpha = 0.95f), t.surface.copy(alpha = 0.97f))), frame = acc.copy(alpha = 0.45f), frameW = 1.dp,
            corner = 20.dp, mediaCorner = 14.dp, mediaFrame = null, mediaFrameW = 0.dp,
            ink = t.textPrimary, muted = ShelfTheme.textMuted, accent = acc,
            btnFill = solid(Color.White.copy(alpha = 0.08f)), btnInk = t.textPrimary, btnOnFill = solid(acc), btnOnInk = onAcc, btnBorder = acc.copy(alpha = 0.35f),
            back = "Back", details = "Details", play = playWord
        )
    }
}

/**
 * The game window for [layout]: [onPlay] starts [g], [onFullDetails] opens the big details window, [onClose] closes
 * this one. Drawn in its own window over the main screen.
 */
@Composable
internal fun GameWindow(
    layout: HomeLayout,
    g: Game,
    state: LauncherUiState,
    art: Map<String, GameArt>,
    onClose: () -> Unit,
    onPlay: () -> Unit,
    onFullDetails: () -> Unit,
    onNeedMedia: (Game) -> Unit,
    onOptions: () -> Unit = {},
    onAchievements: () -> Unit = {},
    onSync: () -> Unit = {},
    /** Update 125: the game's options, shown on the window's own page in its style (empty: [onOptions] opens the menu). */
    options: List<WinOption> = emptyList(),
    /** Update 132: loads an achievement's comments (the achievements page shows them under the one you open). */
    onLoadComments: (Int) -> Unit = {},
    /** Update 213: the emulator's (or app's) package, for its performance profile ("pkg:"); null: none. */
    perfPackage: String? = null,
    /** Update 213: the game's settings with the whole tuner (Performance › Advanced…), over the window. */
    onPerfAdvanced: () -> Unit = {},
    /** Update 213: Settings › Sync (save sync not set up, or no folder for the game). */
    onSyncSetUp: () -> Unit = {},
    /** Update 213: the game's own "Sync saves" (Game settings), null = as Settings › Sync. */
    syncOn: Boolean? = null
) {
    // Update 347: Play ignores the first moments after the window opens — a quick second press of A (or a double tap)
    // meant for opening the window started the game while it was still growing in (found testing rapid open / close).
    val openedAt = remember(g.stableKey) { android.os.SystemClock.uptimeMillis() }
    val playNow by rememberUpdatedState(onPlay)
    @Suppress("NAME_SHADOWING")
    // Update 412: a window that is already flying back (B pressed) never starts the game: a quick A after a B (rapid A B A)
    // reached the Play button of the closing window and launched the game instead of opening the case again.
    val closingFlag = remember(g.stableKey) { booleanArrayOf(false) }
    val onPlay: () -> Unit = { if (!closingFlag[0] && playAllowed(android.os.SystemClock.uptimeMillis() - openedAt)) playNow() }
    // Update 213: its performance profile and save sync (GamePopupStatus.kt), for every style of the window.
    val perfState = rememberGamePerf(g, perfPackage)
    val syncView = rememberGameSyncView(g, syncOn)
    val hasFan = if (perfState != null) rememberHasFan() else null
    // Update 249 (#119): the game window as a move of the home screen itself (Game window › Its motion, R1 › Popup) —
    // where the game you're on has a place on this screen; else the usual window below.
    val rowMotion = PopupStyle.motion(layout.name)
    // Update 285 (Toni: "every popup an option to inspect the media"): the Inspect button beside the window's icons — the
    // game's piece comes out in a column of the window, whatever the look draws (the menu's Inspect…, MediaInspect.force).
    // From the banner page and Game Glance it opens this window with the piece out.
    var askedInspect by remember(g.stableKey) { mutableStateOf(false) }
    val inspectOther: (() -> Unit)? = if (g.sourceType != com.shelf.launcher.data.SourceType.APP) ({
        MediaInspect.testShell = null; MediaInspect.testPose = null
        MediaInspect.force = g.stableKey
        askedInspect = true
    }) else null
    // (Update 250, #119: the shared move — Game Glance's open, GlanceWindow.kt.)
    if (rowMotion == Features.WinMotion.SHARED && !askedInspect) {
        GlanceWindow(g, state, art, onClose, onPlay, onFullDetails, onNeedMedia, onOptions, onAchievements, onInspect = inspectOther)
        return
    }
    if (rowMotion != Features.WinMotion.NONE && !askedInspect &&
        RowMotionWindow(rowMotion, g, state, art, onClose, onPlay, onFullDetails, onNeedMedia, onOptions, onAchievements, onInspect = inspectOther)) return
    // Update 146: the banner page (Settings › Game window › Style), the same for every layout.
    if (PopupStyle.banner(layout.name) && !askedInspect) {
        BannerGameWindow(
            layout, g, state, art, onClose, onPlay, onFullDetails, onNeedMedia, onOptions, onAchievements, onSync, options, onLoadComments,
            perf = perfState, sync = syncView, hasFan = hasFan, onPerfAdvanced = onPerfAdvanced, onSyncSetUp = onSyncSetUp,
            onInspect = inspectOther
        )
        return
    }
    val o = state.artOverrides[g.stableKey]
    val a = art[g.stableKey]
    val cover = o?.cover ?: a?.gridUrl
    val wide = o?.wide ?: a?.wideGridUrl ?: o?.hero ?: a?.heroUrl ?: cover
    val backdrop = com.shelf.launcher.data.ArtPick.backdrop(g, state.artOverrides, art, state.screenshots) ?: wide
    val resumed = state.lastPlayedAt[g.stableKey] != null
    // Update 119: in the game's colours (its cover's), the theme's, or the layout's own.
    val colors = Features.gameWindowColors.state.value
    val coverBmp = rememberSoftBitmap(if (colors == Features.WinColors.GAME) cover else null, 64)
    val gameTint = rememberArtColor(coverBmp, ShelfTheme.accent)
    val base = lookFor(layout, resumed)
    val look = when (colors) {
        Features.WinColors.LAYOUT -> base
        Features.WinColors.THEME -> base.tinted(ShelfTheme.accent)
        Features.WinColors.GAME -> base.tinted(if (coverBmp != null) gameTint else ShelfTheme.accent)
    }
    val plan = WinPlan.of(layout)
    // Update 330: the layout's own window (WinScenes.kt), unless it's switched off (Settings › Game window).
    val scene = if (Features.winScenes.state.value) winSceneFor(layout) else null
    // The TV room: the window opens on the TV's own screen (update 119).
    val hereView = LocalView.current
    val hereDisplay = runCatching { hereView.display?.displayId }.getOrNull() ?: -1
    val tvRect = if (layout == HomeLayout.TV && (TvScreen.displayId == hereDisplay || TvScreen.displayId < 0)) TvScreen.rect else null
    // Where it grows from: the game's picture on the main screen, else (picked on the second screen) up from the
    // bottom edge, else the middle.
    val view = LocalView.current
    val origin = remember(g.stableKey) {
        val here = runCatching { view.display?.displayId }.getOrNull() ?: -1
        if ((GameOrigin.key == g.stableKey || GameOrigin.key == GameOrigin.FOCUSED) && GameOrigin.rect != null) {
            if (GameOrigin.displayId == here || GameOrigin.displayId < 0) GameOrigin.rect else Rect(0f, Float.MAX_VALUE, 0f, Float.MAX_VALUE)
        } else null
    }
    // Update 162: a game the look draws as its physical piece (case, cartridge, card, disc, 3D model) opens by inspecting
    // it (MediaInspectView.kt): the piece comes out of its card, forward and bigger on one side of the window, and turns
    // to show every side; the window's parts sit beside it; closing flies it back to its place. Not on the TV room's
    // set, nor picked on the other screen (no card here to come out of); the other looks open as before.
    val fromOtherScreen = origin != null && origin.top == Float.MAX_VALUE
    // (Update 285, Toni: "if using the cartridge and stuff, it's always moved to the window to be inspected": picked on
    // the other screen too — there's no card here to fly from, so it grows in where it stands.)
    // (Update 330: File select's and Storage boxes' own windows open out of the file / the icon itself — their piece
    // comes out only when asked for, with the Inspect button.)
    val sceneOwnsOpen = (scene == WinScene.FILE_CARD || scene == WinScene.STORAGE_RECORD || scene == WinScene.MEMCARD_INFO ||
        scene == WinScene.ORBIT_TERMINAL || scene == WinScene.AIRY_PANEL || scene == WinScene.NIGHT_STATUS ||
        scene == WinScene.BLADE_GUIDE || scene == WinScene.BUBBLE_CARD || scene == WinScene.CAMERA_MENU || scene == WinScene.PANEL_DETAIL ||
        scene == WinScene.MAG_PAGE || scene == WinScene.TERM_RUN || scene == WinScene.ARCADE_MENU || scene == WinScene.WII_PREVIEW) && !askedInspect && MediaInspect.force != g.stableKey
    val pieceNow = if (tvRect == null && !sceneOwnsOpen) inspectPieceFor(g, state) else null
    val inspect = remember(g.stableKey, askedInspect) { pieceNow != null }
    // (Decided once as it opens; its details follow the game's scans as they come in.)
    val lastPiece = remember(g.stableKey) { arrayOfNulls<InspectPiece>(1) }
    if (pieceNow != null) lastPiece[0] = pieceNow
    val piece = if (inspect) pieceNow ?: lastPiece[0] else null
    val motion = remember(g.stableKey) { InspectMotion().also { it.from = origin } }
    // (Update 162: a piece turns all the way round. Update 200: the drawn shells of the 3D cartridges looks too — the
    // pre-rendered 3D models and their InspectModel are retired.)
    motion.limited = false
    // (Update 330: the Cardboard box's case comes out of the box spine first — it turns to face you as it comes forward.)
    motion.fromSpine = scene == WinScene.BOX_SHEET && piece?.kind == InspectKind.BOX && origin != null
    motion.spineYaw = BoxNav.startYaw // (Update 402: where the case really stands when A pulls it — spine-on, or as turned with Y.)
    // (Update 274: Settings › Game look › In the game window: Wobble — it only sways, no turning by hand.)
    motion.handsOff = com.shelf.launcher.data.Features.inspectMode.state.value == com.shelf.launcher.data.Features.InspectMode.WOBBLE
    val inspectScope = rememberCoroutineScope()
    // Update 200: the stick click for the piece, also from the home screen's key handler (MediaInspect.stickClick).
    val pieceForStick by rememberUpdatedState(piece)
    val hasPiece = piece != null
    androidx.compose.runtime.DisposableEffect(motion, hasPiece) {
        if (!hasPiece) return@DisposableEffect onDispose { }
        val hook: () -> Unit = {
            val pc = pieceForStick
            if (pc != null) { if (pc.kind == InspectKind.BOX) motion.stickClick(inspectScope, pc.content != null) else motion.spinOnce(inspectScope) }
        }
        MediaInspect.stickClick = hook
        onDispose { if (MediaInspect.stickClick === hook) MediaInspect.stickClick = null }
    }
    // The flight and the turn with the window's animation on (Home › Animations), the system's animations on and no
    // Lite effects; otherwise a short fade.
    val sysAnims = rememberAnimationsEnabled()
    val lite = LocalLiteEffects.current
    // (Update 162: Lite keeps it too with Effects & battery › Full animations on Lite, on by default.)
    // Update 400 ("selected games disappear from their original position and suddenly appear in the popup"): picking a
    // case up is a physical movement, not an info entrance. With Info on "Just appear" (the default since 393) the flight was
    // off: the card vanished at once and the piece popped in at its final place. The flight now follows only the system's
    // animation setting and Lite; "Just appear" only skips the showcase turn.
    val fullInspect = sysAnims && (!lite || Features.motionOnLite.state.value)
    val inspectTurn = fullInspect && MotionPrefs.info() != Features.InfoAnim.NONE
    // The grow: 0 = drawn over the game it came from, 1 = in its place. Starts once the window's place is known.
    // (Update 162: kept out here, so closing can fade the window out while the piece flies back.)
    val grow = remember { Animatable(0f) }
    val closeNow by rememberUpdatedState(onClose)
    // Update 162: closing turns the piece back and flies it to its place first, then the window goes.
    // (Update 330: a layout's own window without a piece goes back the way it came — into its game's place — first.)
    var shrinking by remember { mutableStateOf(false) }
    // Update 400: see [cardFlight] below (declared here: the close path needs it).
    val cardFlight = piece == null && scene == null && layout in PICKUP_CARD_LAYOUTS && sysAnims && origin != null && origin.top != Float.MAX_VALUE
    val close: () -> Unit = {
        closingFlag[0] = true
        if (piece == null && (scene != null || cardFlight) && (cardFlight || MotionPrefs.info() != Features.InfoAnim.NONE) && grow.value > 0.05f) {
            if (!shrinking) {
                shrinking = true
                inspectScope.launch {
                    grow.animateTo(0f, tween(sceneCloseMs(scene), easing = FastOutSlowInEasing))
                    closeNow()
                }
            }
        } else if (piece == null || !motion.started) closeNow()
        else if (!motion.closing) {
            motion.closing = true
            inspectScope.launch {
                if (fullInspect) launch { grow.animateTo(0f, tween(420, delayMillis = 80, easing = FastOutSlowInEasing)) }
                motion.close(fullInspect)
                if (MediaInspect.key == g.stableKey) MediaInspect.key = null
                closeNow()
            }
        }
    }
    // (However it closes — Play, Details, the options… — the carousel gets its games back.)
    val mySeq = remember(g.stableKey) { MediaInspect.forceSeq }
    DisposableEffect(g.stableKey) {
        val k = g.stableKey
        onDispose {
            if (motion.started) { MediaInspect.amount.floatValue = 0f; MediaInspect.hold.value = false }
            if (MediaInspect.key == k) MediaInspect.key = null
            // (Update 274: the menu's Inspect… is for this opening only — unless a newer one was asked for meanwhile.)
            if (MediaInspect.force == k && MediaInspect.forceSeq == mySeq) { MediaInspect.force = null; MediaInspect.testShell = null; MediaInspect.testPose = null }
        }
    }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = close,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        ImmersiveDialogWindow()
        val playFocus = remember { FocusRequester() }
        // (Update 233, #120: opened with a button after a touch — leave Android's touch mode first, so Play is lit at once
        // and the first ◀ / ▶ moves; it only lit Play, and A, ▶, A started the game.)
        val keyView = androidx.compose.ui.platform.LocalView.current
        LaunchedEffect(Unit) { kotlinx.coroutines.delay(20); KeyInput.leaveTouchMode(keyView) }
        // Update 122: always opens on Play / Continue. The window can take a moment to get the focus
        // (from a touch, or picked on the second screen): ask again until something in it has the focus.
        var focusInside by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            // (Update 125: the first three asks always go to Play — something else in the window, e.g. an icon, could
            // take the focus first, and then Play was never picked.)
            // (Update 347: one more ask a second later — a window opened while the app was taking its focus back could
            // still have nothing lit after 1.5 s.)
            for ((n, wait) in longArrayOf(60, 140, 250, 400, 700, 1000).withIndex()) {
                kotlinx.coroutines.delay(wait)
                if (focusInside && n >= 3) break
                runCatching { playFocus.requestFocus() }
            }
        }
        // Update 129: the popup's own window can get the key focus only after those asks (opened with A, from the second
        // screen, or a slow handheld) — then Play wasn't lit until another press. Ask once more the moment it gets it.
        val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
        var askedOnFocus by remember { mutableStateOf(false) }
        LaunchedEffect(windowInfo.isWindowFocused) {
            if (windowInfo.isWindowFocused && !askedOnFocus) {
                askedOnFocus = true
                KeyInput.leaveTouchMode(keyView) // (Update 233, #120)
                kotlinx.coroutines.delay(30)
                runCatching { playFocus.requestFocus() }
            }
        }
        LaunchedEffect(g.stableKey) { onNeedMedia(g) }
        // (Update 285: the Inspect button is gone once the piece is out — the D-pad back onto Play.)
        LaunchedEffect(askedInspect) { if (askedInspect) { kotlinx.coroutines.delay(80); runCatching { playFocus.requestFocus() } } }
        // Update 125: the picture, the details, the options and the achievements are pages of the same window.
        var page by remember { mutableStateOf(WinPage.MAIN) }
        val details = page != WinPage.MAIN
        val listFocus = remember { FocusRequester() }
        androidx.activity.compose.BackHandler(enabled = details) { page = WinPage.MAIN }
        // Update 213: back from the Performance page, the D-pad goes back onto its button.
        val perfFocus = remember { FocusRequester() }
        var fromPerf by remember { mutableStateOf(false) }
        val winStatus = WinStatus(
            perfState, syncView, hasFan, onSync, onSyncSetUp,
            onPerf = { fromPerf = true; page = WinPage.PERF }, onPerfAdvanced = onPerfAdvanced, perfFocus = perfFocus
        )
        val hasTrophies = state.raAchievementDetails[g.stableKey].orEmpty().isNotEmpty()
        // Update 202: a Manual button beside the window's small icons, only for a game that has a manual.
        val hasManual = rememberHasManual(g)
        val extras = WinExtras(
            plan,
            onOptions = { if (options.isNotEmpty()) page = WinPage.OPTIONS else onOptions() },
            // (Update 127: on the Cross menu the achievements open as the menu's own column, CrossTrophies.kt.)
            onAchievements = { if (hasTrophies && !crossTrophiesOn(layout)) page = WinPage.TROPHIES else onAchievements() },
            onSync = onSync,
            onFullAchievements = onAchievements,
            onLoadComments = onLoadComments,
            onManual = if (hasManual) ({ com.shelf.launcher.data.ManualLinks.open?.invoke(g); Unit }) else null,
            // (Update 285: only while no piece is out — not for apps, nor on the TV room's set / from the other screen.)
            onInspect = if (piece == null && tvRect == null && g.sourceType != com.shelf.launcher.data.SourceType.APP) ({
                MediaInspect.testShell = null; MediaInspect.testPose = null
                MediaInspect.force = g.stableKey
                askedInspect = true
            }) else null
        )
        // Turning the window over swaps the buttons: the D-pad's place goes back to Play (the button it was on may be gone),
        // or to the first row of the options / achievements.
        var turned by remember { mutableStateOf(false) }
        LaunchedEffect(page) {
            if (!turned) { turned = true; return@LaunchedEffect }
            kotlinx.coroutines.delay(60)
            if (page == WinPage.OPTIONS || page == WinPage.TROPHIES || page == WinPage.PERF) {
                if (runCatching { listFocus.requestFocus() }.isFailure) runCatching { playFocus.requestFocus() }
            } else if (page == WinPage.MAIN && fromPerf) {
                fromPerf = false
                if (runCatching { perfFocus.requestFocus() }.isFailure) runCatching { playFocus.requestFocus() }
            } else runCatching { playFocus.requestFocus() }
        }
        var target by remember { mutableStateOf<Rect?>(null) }
        // Update 400: a storage layout's window without a piece of its own (Game shelf, Console stack, Card binder, Record crate)
        // still grows out of the case that was picked and goes back into its slot, whatever Info is set to: [cardFlight].
        val anim = MotionPrefs.info() != Features.InfoAnim.NONE || cardFlight
        // (Update 330: Orbit deck's disc leaves the orbit and comes forward on its own, the window opening round it — the
        // scene draws that flight (WinScenes.kt OdFlyingDisc); the window itself isn't scaled out of the disc. Its disc
        // on the orbit fades as the flying one takes over, and back as it lands.)
        val discFlight = scene == WinScene.ORBIT_TERMINAL && piece == null && origin != null && origin.top != Float.MAX_VALUE && anim
        if (discFlight || cardFlight) {
            LaunchedEffect(Unit) {
                MediaInspect.key = g.stableKey
                androidx.compose.runtime.snapshotFlow { grow.value }.collect { MediaInspect.amount.floatValue = it.coerceIn(0f, 1f) }
            }
            DisposableEffect(Unit) { onDispose { MediaInspect.amount.floatValue = 0f; if (MediaInspect.key == g.stableKey) MediaInspect.key = null } }
        }
        LaunchedEffect(target != null) {
            if (target == null) return@LaunchedEffect
            if (!anim) grow.snapTo(1f) else grow.animateTo(1f, tween(if (discFlight) 620 else sceneOpenMs(scene), easing = if (scene == WinScene.TERM_RUN || scene == WinScene.CAMERA_MENU || scene == WinScene.ARCADE_MENU) LinearEasing else FastOutSlowInEasing))
        }
        // Update 162: the piece's place beside the window's parts (window px), known after the first layout; it comes
        // out once that's known, and the right stick turns it.
        var slot by remember { mutableStateOf<Rect?>(null) }
        if (piece != null) {
            InspectStick(motion)
            // (Update 162 (review): keyed on the motion too — another game shown in the open window gets its own.)
            LaunchedEffect(slot != null, motion) {
                if (slot == null) return@LaunchedEffect
                // Update 400: the cover is in memory before the case flies (else it flew as a blank slab and the picture popped in).
                if (fullInspect && piece.kind == InspectKind.BOX && piece.front is String) {
                    val t0 = android.os.SystemClock.uptimeMillis()
                    while (!motion.artReady && android.os.SystemClock.uptimeMillis() - t0 < 450L) kotlinx.coroutines.delay(16)
                }
                MediaInspect.key = g.stableKey
                motion.open(inspectScope, fullInspect, spin = inspectTurn) // (Update 164: the turn too on Lite, with Full animations on Lite)
            }
        }
        androidx.compose.runtime.CompositionLocalProvider(LocalWinStatus provides winStatus) {
        if (tvRect != null) {
            // On the TV: the room stays lit round it, the window fills the set's screen, switching on like an old TV.
            LaunchedEffect(Unit) { if (!anim) grow.snapTo(1f) else grow.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
            val d = androidx.compose.ui.platform.LocalDensity.current
            BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).pointerInput(Unit) { detectTapGestures { onClose() } }) {
                with(d) {
                    // Update 125: the name and the buttons go under the set when there's room, so nothing covers the
                    // game's picture (they sat over its lower half, over the faces); over its foot only when there isn't.
                    val below = maxHeight - tvRect.bottom.toDp()
                    val outside = below >= 74.dp
                    Box(
                        Modifier.offset(tvRect.left.toDp(), tvRect.top.toDp()).size(tvRect.width.toDp(), tvRect.height.toDp())
                            .graphicsLayer { val k = grow.value; scaleY = 0.04f + 0.96f * k; scaleX = 0.6f + 0.4f * k; alpha = (k * 3f).coerceAtMost(1f) }
                            .clip(RoundedCornerShape(10.dp)).background(Color.Black)
                            .pointerInput(Unit) { detectTapGestures { } }
                            .onFocusChanged { if (it.hasFocus) focusInside = true }
                    ) {
                        TvWindowBody(look, g, state, backdrop, cover, page, { page = it }, playFocus, listFocus, onClose, onPlay, onFullDetails, extras, options, controlsInside = !outside)
                    }
                    if (outside) Box(
                        Modifier.offset(tvRect.left.toDp() - 6.dp, tvRect.bottom.toDp() + 8.dp).width(tvRect.width.toDp() + 12.dp)
                            .graphicsLayer { alpha = grow.value.coerceIn(0f, 1f) }
                            .clip(RoundedCornerShape(12.dp)).background(Color(0xFF15161A).copy(alpha = 0.94f))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                            .pointerInput(Unit) { detectTapGestures { } }
                            .onFocusChanged { if (it.hasFocus) focusInside = true }
                            .padding(8.dp)
                    ) {
                        TvControls(look, g, page, { page = it }, playFocus, onClose, onPlay, onFullDetails, extras, roomy = tvRect.width.toDp() > 380.dp)
                    }
                }
            }
        } else Box(Modifier.fillMaxSize().onPreviewKeyEvent { e ->
            // Update 189: a click of the right stick (which turns the piece) opens / closes its case.
            val pc = piece
            val code = e.nativeKeyEvent.keyCode
            if (pc != null && (code == android.view.KeyEvent.KEYCODE_BUTTON_THUMBR || code == android.view.KeyEvent.KEYCODE_BUTTON_THUMBL)) {
                // (Update 199: a cartridge, disc or 3D piece turns once round instead.)
                // (Update 200: either stick's click, acted on as it goes down — one click a step: open, take out, put back
                // and shut. It took two clicks.)
                if (e.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN && e.nativeKeyEvent.repeatCount == 0) {
                    if (pc.kind == InspectKind.BOX) motion.stickClick(inspectScope, pc.content != null) else motion.spinOnce(inspectScope)
                }
                true
            } else false
        }) {
          Box(
            Modifier.fillMaxSize()
                // Update 403: with a flying piece the veil (and the window) fade in WITH the flight (lift), not on their own clock:
                // `grow` starts at the first layout, the flight only after the piece's slot is known and its cover is in memory
                // (up to 450 ms), so the screen dimmed for ~8 frames before anything moved.
                .graphicsLayer { alpha = grow.value.coerceIn(0f, 1f) * (if (piece != null) flightVeil(motion.lift.floatValue) else 1f) }
                .background(scene?.let { sceneVeil(it) } ?: look.veil)
                .pointerInput(Unit) { detectTapGestures { close() } },
            contentAlignment = Alignment.Center
        ) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
                val maxW = maxWidth
                val maxH = maxHeight
                if (piece != null) {
                    // Update 162: the piece's side, and the window's parts beside it (InspectLayout).
                    InspectLayout(
                        maxW, maxH, motion,
                        onSlot = { r -> if (r != slot) slot = r },
                        onPlaced = { r -> if (target == null) target = r },
                        onFocusIn = { focusInside = true }
                    ) { w, h ->
                        if (scene != null) SceneBody(scene, look, g, state, cover, wide, backdrop, page, { page = it }, w, h, playFocus, listFocus, close, onPlay, onFullDetails, extras, options, closing = { motion.closing }) { motion.lift.floatValue }
                        else WindowBody(look, g, state, cover, wide, backdrop, page, { page = it }, w, h, playFocus, listFocus, close, onPlay, onFullDetails, extras, options)
                    }
                } else Box(
                    Modifier
                        .onGloballyPositioned { if (target == null) target = it.boundsInWindow() }
                        .graphicsLayer {
                            val to = target
                            val from = origin
                            val k = grow.value
                            if (to == null) { alpha = 0f; return@graphicsLayer }
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                            if (discFlight) {
                                // (Its scene flies the disc and opens the rest round it.)
                            } else if (from == null || !anim) {
                                val s = 0.86f + 0.14f * k
                                scaleX = s; scaleY = s
                            } else if (from.top == Float.MAX_VALUE) {
                                // From the second screen: up from below the main screen's bottom edge.
                                translationY = (1f - k) * (size.height * 0.9f + to.height)
                                val s = 0.7f + 0.3f * k
                                scaleX = s; scaleY = s
                            } else {
                                val s0x = (from.width / to.width).coerceIn(0.05f, 1f)
                                val s0y = (from.height / to.height).coerceIn(0.05f, 1f)
                                scaleX = s0x + (1f - s0x) * k
                                scaleY = s0y + (1f - s0y) * k
                                translationX = (from.center.x - to.center.x) * (1f - k)
                                translationY = (from.center.y - to.center.y) * (1f - k)
                                // (Update 330: File select — the file block turns over (StyleFileSelect's FileBlock turns
                                // its front away) and its back grows out into this: hidden for the first half, then turning
                                // in from edge-on.)
                                if (scene == WinScene.FILE_CARD) {
                                    val t = ((k - 0.42f) / 0.58f).coerceIn(0f, 1f)
                                    alpha = if (k < 0.42f) 0f else 1f
                                    rotationY = -90f * (1f - t)
                                    cameraDistance = 18f * density
                                    flatTextFor3d()
                                }
                            }
                        }
                        // Taps inside the window never close it.
                        .pointerInput(Unit) { detectTapGestures { } }
                        .onFocusChanged { if (it.hasFocus) focusInside = true }
                ) {
                    if (scene != null) androidx.compose.runtime.CompositionLocalProvider(LocalSceneOrigin provides (if (discFlight) origin else null)) {
                        SceneBody(scene, look, g, state, cover, wide, backdrop, page, { page = it }, maxW, maxH, playFocus, listFocus, close, onPlay, onFullDetails, extras, options, closing = { shrinking }) { grow.value }
                    }
                    else WindowBody(look, g, state, cover, wide, backdrop, page, { page = it }, maxW, maxH, playFocus, listFocus, close, onPlay, onFullDetails, extras, options)
                }
            }
          }
            // Update 162: the piece itself, over the window (it flies from its card to its side and back).
            if (piece != null) MediaInspectPiece(piece, motion, slot, hereDisplay)
        }
        }
    }
}

/** Update 347: how long after the window opens Play starts accepting a press (see GameWindow). */
internal const val PLAY_GUARD_MS = 450L

internal fun playAllowed(sinceOpenMs: Long): Boolean = sinceOpenMs >= PLAY_GUARD_MS

/**
 * Update 347: how long a layout's own window takes to open / close — the ones that draw themselves in steps (the DOS box,
 * the camera's screen) a little longer, so their steps can be seen; everything else as before.
 */
internal fun sceneOpenMs(scene: WinScene?): Int = when (scene) {
    WinScene.TERM_RUN -> 560
    WinScene.ARCADE_MENU -> 440
    WinScene.CAMERA_MENU -> 420
    WinScene.MAG_PAGE, WinScene.BUBBLE_CARD -> 400
    WinScene.PANEL_DETAIL, WinScene.BLADE_GUIDE -> 380
    else -> 320
}

internal fun sceneCloseMs(scene: WinScene?): Int = when (scene) {
    WinScene.FILE_CARD -> 340
    WinScene.ORBIT_TERMINAL -> 480
    WinScene.TERM_RUN -> 420
    WinScene.ARCADE_MENU -> 340
    WinScene.CAMERA_MENU, WinScene.BUBBLE_CARD -> 340
    WinScene.MAG_PAGE, WinScene.PANEL_DETAIL, WinScene.BLADE_GUIDE -> 300
    else -> 260
}

/** Update 162: the window's parts are beside the inspected piece — no second disc / cartridge drawn among them. */
private val LocalWinInspect = androidx.compose.runtime.staticCompositionLocalOf { false }

/**
 * Update 162: the game window while its piece is out (MediaInspectView.kt): the piece's side ([onSlot]: its place, window
 * px) and the window's parts ([body], given their room) beside it, never over it — side by side, or the piece above on
 * a tall screen. The parts slide in as the piece comes out ([InspectMotion.lift]).
 */
@Composable
private fun InspectLayout(
    maxW: Dp, maxH: Dp, motion: InspectMotion, onSlot: (Rect) -> Unit, onPlaced: (Rect) -> Unit, onFocusIn: () -> Unit,
    body: @Composable (Dp, Dp) -> Unit
) {
    val gap = 14.dp
    val slotMod = Modifier.onGloballyPositioned { c -> onSlot(c.boundsInWindow()) }
    val bodyMod = Modifier
        .onGloballyPositioned { onPlaced(it.boundsInWindow()) }
        .graphicsLayer {
            val b = ((motion.lift.floatValue - 0.3f) / 0.7f).coerceIn(0f, 1f)
            alpha = b
            translationX = (1f - b) * 36.dp.toPx()
        }
        // Taps inside the window never close it.
        .pointerInput(Unit) { detectTapGestures { } }
        .onFocusChanged { if (it.hasFocus) onFocusIn() }
    if (maxW >= maxH * 0.95f) {
        val slotW = maxW * 0.4f
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(slotMod.width(slotW).fillMaxHeight())
            Spacer(Modifier.width(gap))
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Box(bodyMod) { CompositionLocalProvider(LocalWinInspect provides true) { body(maxW - slotW - gap, maxH) } }
            }
        }
    } else {
        val slotH = maxH * 0.38f
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(slotMod.fillMaxWidth().height(slotH))
            Spacer(Modifier.height(gap))
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(bodyMod) { CompositionLocalProvider(LocalWinInspect provides true) { body(maxW, maxH - slotH - gap) } }
            }
        }
    }
}

/**
 * Update 119: what the window shows besides its picture and buttons, and the small buttons' actions. Update 125: where
 * each part goes ([plan], this layout's own); the options and achievements turn the window to their page.
 */
private class WinExtras(
    val plan: Map<WinPart, WinSlot>, val onOptions: () -> Unit, val onAchievements: () -> Unit, val onSync: () -> Unit,
    /** The big achievement list (the button on the window's achievements page). */
    val onFullAchievements: () -> Unit = onAchievements,
    /** Update 132: loads an achievement's comments from RetroAchievements (shown under it on the achievements page). */
    val onLoadComments: (Int) -> Unit = {},
    /** Update 202: opens the game's manual (null: it has none — no Manual button). */
    val onManual: (() -> Unit)? = null,
    /** Update 285: brings the game's piece out to turn round (null: it's out already — no Inspect button). */
    val onInspect: (() -> Unit)? = null
) {
    fun has(p: WinPart) = (plan[p] ?: WinSlot.OFF) != WinSlot.OFF
    fun at(p: WinPart, slot: WinSlot) = (plan[p] ?: WinSlot.OFF) == slot
    /** Any of the text parts (facts, achievements, story) at [slot]. */
    fun textAt(slot: WinSlot) = at(WinPart.FACTS, slot) || at(WinPart.ACHIEVEMENTS, slot) || at(WinPart.DESCRIPTION, slot)
}

/**
 * Update 125: the window's buttons, Play / Continue always first (the D-pad starts on it), then the page's own: Details
 * and Back on the picture; Full details and Back on the details; the full list and Back on the achievements; Back on
 * the options.
 */
private fun winButtonItems(
    look: WinLook, page: WinPage, setPage: (WinPage) -> Unit, onClose: () -> Unit, onPlay: () -> Unit,
    onFullDetails: () -> Unit, extras: WinExtras,
    /** Update 213: the Performance button after Play (null: none here — no tuner, or a narrow row that has its icon). */
    onPerf: (() -> Unit)? = null
): List<Triple<String, () -> Unit, Boolean>> {
    val rest: List<Pair<String, () -> Unit>> = when (page) {
        WinPage.MAIN -> listOfNotNull(onPerf?.let { PERF_LABEL to it }, look.details to { setPage(WinPage.DETAILS) }, look.back to onClose)
        WinPage.DETAILS -> listOf("Full details" to onFullDetails, "Back" to { setPage(WinPage.MAIN) })
        WinPage.TROPHIES -> listOf("Full list" to extras.onFullAchievements, "Back" to { setPage(WinPage.MAIN) })
        WinPage.OPTIONS, WinPage.PERF -> listOf("Back" to { setPage(WinPage.MAIN) })
    }
    return listOf(Triple(look.play, onPlay, true)) + rest.map { (l, f) -> Triple(l, f, false) }
}

/**
 * The game's own disc / cartridge (its scan, else Voltcap's drawn one with its label), else its cover (update 119).
 * Update 122: a disc game shows its case with the disc half out of it by default (Home › Game window › Case with the disc).
 */
@Composable
private fun WinGameMedia(g: Game, state: LauncherUiState, cover: String?, modifier: Modifier) {
    val physical = state.physicalMedia[g.stableKey]
    val pick = mediaFor(g.platform, gameKey = g.stableKey)
    // Update 128: on your cartridge pack (Cartridges look) it's that cartridge, never its disc case or a disc scan.
    // (Update 200: the 3D cartridges looks' drawn shells count as custom pieces — the pre-rendered 3D models are retired.)
    val custom = pick?.platform?.let { isCustomPiece(it) } == true
    // (Update 286, Toni: "real cartridge artwork ignored for our custom ones" — Pokémon Silver's window showed the drawn
    // silver cartridge: every console's own authentic shell counts as a "custom piece" here, so a cartridge game never
    // showed its photo. The photo now follows the same rule as the cards: typedArtFor — a shell you picked for the game or
    // its console, Cartridges only and your pack still win; "Drawn only" still draws.)
    val realScan = realScanOf(com.shelf.launcher.data.CardArtType.CARTRIDGE_DISC,
        typedArtFor(com.shelf.launcher.data.CardArtType.CARTRIDGE_DISC, physical, null, g.platform, g.stableKey))
        ?.takeIf { physical?.any { p -> p.first == "Disc / cartridge" && p.second == it } == true }
    val tpl = MediaTemplates.of(g.platform)
    val caseOn = Features.gameWindowCase.state.value && tpl?.disc != null && tpl.box != null && pick != null && !custom
    Box(modifier, contentAlignment = Alignment.Center) {
        when {
            caseOn -> {
                val label = physical?.firstOrNull { it.first == "Disc label" }?.second
                val caseScan = BoxTexture.caseScan(physical, g.platform)
                val (front, frontIsBox) = boxFront(tpl!!.box!!, cover, caseScan)
                DiscInCase(
                    tpl, front, frontIsBox, label ?: cover, Modifier.fillMaxSize(),
                    realDisc = realScan.takeIf { !mediaShellsOn }, platform = g.platform,
                    // Update 202: the game's own spine on the case's side, as its closed case shows it (the Portal's
                    // game window showed the console's plain template spine instead).
                    realSpine = BoxTexture.realSpine(physical ?: HomeScans.map[g.stableKey], g.platform)
                )
            }
            pick != null && !mediaShellsOn && realScan != null ->
                AsyncImage(realScan, g.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            pick != null -> {
                val label = physical?.firstOrNull { it.first == "Disc label" }?.second
                MediaFocus(true) { PhysicalMedia(pick.shell, label ?: cover, Modifier.fillMaxSize(), platform = pick.platform, labelIsScan = label != null, caseCover = cover, title = g.name) }
            }
            // Update 202: the cover at its own shape (CoverAspects once it has loaded), as big as the place allows, filling
            // that box edge to edge (Crop, centred) — it was fitted into the square place (Fit), its rounded corners
            // clipping empty room instead of the art. Its file's flat strips trimmed (TrimMargins.kt).
            cover != null -> BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val boxA = if (maxHeight.value > 0f && maxHeight.value.isFinite()) maxWidth / maxHeight else 0.7f
                val a = CoverAspects.of(cover) ?: 0.7f
                val cw = if (a >= boxA) maxWidth else maxHeight * a
                val ch = if (a >= boxA) maxWidth / a else maxHeight
                AsyncImage(
                    rememberStyleArtRequest(cover, trim = true), g.name, contentScale = ContentScale.Crop, alignment = Alignment.Center,
                    onSuccess = { r -> r.painter.intrinsicSize.let { sz -> CoverAspects.note(cover, sz.width, sz.height) } },
                    modifier = Modifier.size(cw, ch).clip(RoundedCornerShape(8.dp))
                )
            }
        }
    }
}

/** Whether [platform]'s game shows as a case with its disc (a disc console with a case template). */
private fun discCaseFits(platform: String): Boolean = MediaTemplates.of(platform).let { it?.disc != null && it.box != null } &&
    mediaFor(platform).let { it != null && !isCustomPiece(it.platform) }

/** Facts, achievements and the story, as chosen (update 119). [descLines]: 0 = no story. */
@Composable
private fun WinInfo(
    look: WinLook, g: Game, state: LauncherUiState, extras: WinExtras, descLines: Int, ink: Color = look.ink, muted: Color = look.muted, center: Boolean = false,
    /** Update 125: only the parts placed here. */
    slot: WinSlot = WinSlot.INFO
) {
    val titles = LocalTitleOptions.current
    val meta = state.metadata[g.stableKey]
    val align = if (center) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start
    val family = if (look.mono) FontFamily.Monospace else null
    Column(horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start) {
        if (extras.at(WinPart.FACTS, slot)) {
            val facts = listOfNotNull(
                meta?.releaseYear?.toString(), meta?.developer, meta?.genres?.take(2)?.joinToString(", ")?.takeIf { it.isNotBlank() },
                state.lastPlayedAt[g.stableKey]?.let { "Last played " + lastPlayedLabel(it) }
            )
            if (facts.isNotEmpty()) androidx.compose.material3.Text(
                // (Update 129: two lines, so the year, maker, genres and last played aren't cut off with "…".)
                // (Update 285: up to three lines, a little smaller until it all fits — "Racing, …", "Last played …".)
                // (Update 329, seen on the AYN Thor: "Last / played Today" broke across two lines in the narrower windows —
                // each fact keeps its words together now (no-break spaces inside it), the line breaks between them.)
                facts.joinToString("  ·  ") { it.replace(' ', '\u00A0') }.let { if (look.caps) it.uppercase() else it }, color = muted, maxLines = 3,
                autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 12.sp),
                textAlign = align, fontFamily = family
            )
        }
        if (extras.at(WinPart.ACHIEVEMENTS, slot)) {
            val list = state.raAchievementDetails[g.stableKey].orEmpty()
            val p = state.raProgress[g.stableKey]
            val total = if (list.isNotEmpty()) list.size else p?.NumPossibleAchievements ?: 0
            val earned = if (list.isNotEmpty()) list.count { it.isUnlocked } else p?.NumAchieved ?: 0
            if (total > 0) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.EmojiEvents, null, tint = look.accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    androidx.compose.material3.Text(
                        (if (look.caps) "ACHIEVEMENTS" else "Achievements") + " $earned / $total", color = ink, fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, fontFamily = family
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.width(70.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(ink.copy(alpha = 0.2f))) {
                        Box(Modifier.fillMaxWidth((earned.toFloat() / total).coerceIn(0f, 1f)).fillMaxHeight().background(look.accent))
                    }
                }
            }
        }
        val summary = meta?.summary?.takeIf { it.isNotBlank() }
        if (descLines > 0 && extras.at(WinPart.DESCRIPTION, slot) && summary != null) {
            Spacer(Modifier.height(6.dp))
            androidx.compose.material3.Text(
                summary, color = ink.copy(alpha = 0.88f), fontSize = 12.sp, lineHeight = 16.sp, maxLines = descLines,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, textAlign = align, fontFamily = family
            )
        }
    }
}

/** A small round button in the window's style: the game's options, its achievements, its save sync (update 119). */
@Composable
private fun WinIconButton(look: WinLook, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, size: Dp, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val sounds = LocalUiSounds.current
    LaunchedEffect(focused) { if (focused) sounds?.play(UiSound.MOVE) }
    val shape: Shape = when (look.btn) { Btn.SQUARE -> RoundedCornerShape(0.dp); Btn.LCD -> RoundedCornerShape(6.dp); else -> CircleShape }
    Box(
        Modifier.size(size)
            .graphicsLayer { val k = if (focused) 1.08f else 1f; scaleX = k; scaleY = k }
            .clip(shape)
            .background(if (focused) look.btnOnFill else if (look.btn == Btn.TEXT) solid(Color.White.copy(alpha = 0.1f)) else look.btnFill)
            .then(if (look.btnBorder != null) Modifier.border(if (focused) 2.dp else 1.dp, if (focused) look.accent else look.btnBorder, shape) else Modifier)
            .clickable(interactionSource = source, indication = null) { sounds?.play(UiSound.SELECT); onClick() },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Icon(icon, label, tint = if (focused) look.btnOnInk else look.btnInk, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
private fun WinIcons(look: WinLook, extras: WinExtras, size: Dp = 40.dp, slot: WinSlot = WinSlot.INFO) {
    if (!extras.at(WinPart.ICONS, slot)) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        WinIconButton(look, androidx.compose.material.icons.Icons.Filled.Settings, "Game options", size, extras.onOptions)
        WinIconButton(look, androidx.compose.material.icons.Icons.Filled.EmojiEvents, "Achievements", size, extras.onAchievements)
        WinIconButton(look, androidx.compose.material.icons.Icons.Filled.CloudSync, "Save sync", size, extras.onSync)
        // Update 202: the game's manual (downloaded the first time it's opened).
        extras.onManual?.let { WinIconButton(look, androidx.compose.material.icons.Icons.Filled.MenuBook, "Manual", size, it) }
        // Update 285: Inspect — half a case, half a cartridge (InspectIcon).
        extras.onInspect?.let { WinIconButton(look, InspectIcon.vector, "Inspect", size, it) }
    }
}

/**
 * Update 119: the window on the TV room's set — the picture fills the screen. Update 125: the name and the buttons sit
 * under the set ([TvControls]) unless there's no room there ([controlsInside]: then over the picture's foot, as before).
 */
@Composable
private fun TvWindowBody(
    look: WinLook, g: Game, state: LauncherUiState, backdrop: String?, cover: String?, page: WinPage, setPage: (WinPage) -> Unit,
    playFocus: FocusRequester, listFocus: FocusRequester, onClose: () -> Unit, onPlay: () -> Unit, onFullDetails: () -> Unit,
    extras: WinExtras, options: List<WinOption>, controlsInside: Boolean
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val roomy = maxWidth > 380.dp
        when (page) {
            WinPage.MAIN -> {
                if (backdrop != null) AsyncImage(backdrop, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().pixelArtwork())
                val video = state.videoUrls[g.stableKey]
                VideoCrossfade(video, state.appearance.videoSound, Modifier.fillMaxSize())
                if (controlsInside) Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.88f)))))
                WinPicturePart(look, g, state, cover, extras, Modifier.fillMaxSize(), nameShown = false)
            }
            WinPage.DETAILS -> WindowDetails(look, g, state, cover, Modifier.fillMaxSize())
            WinPage.OPTIONS -> WindowOptions(look, options, listFocus, Modifier.fillMaxSize())
            WinPage.TROPHIES -> WindowTrophies(look, g, state, listFocus, Modifier.fillMaxSize(), extras.onLoadComments)
            WinPage.PERF -> WindowPerformance(look, listFocus, Modifier.fillMaxSize())
        }
        if (controlsInside) Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(10.dp)) {
            TvControls(look, g, page, setPage, playFocus, onClose, onPlay, onFullDetails, extras, roomy)
        }
        // The old set's glass: faint scanlines over everything.
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            var y = 0f
            val step = 3.dp.toPx()
            while (y < size.height) { drawRect(Color.Black.copy(alpha = 0.12f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, 1f)); y += step }
        }
    }
}

/** Update 125: the TV window's name and buttons (Play first), under the set or over the picture's foot. */
@Composable
private fun TvControls(
    look: WinLook, g: Game, page: WinPage, setPage: (WinPage) -> Unit, playFocus: FocusRequester, onClose: () -> Unit,
    onPlay: () -> Unit, onFullDetails: () -> Unit, extras: WinExtras, roomy: Boolean
) {
    val titles = LocalTitleOptions.current
    Column(Modifier.fillMaxWidth()) {
        if (page == WinPage.MAIN) {
            WinText(look, titles.display(g.name), Color.White, 17, FontWeight.Bold, shadow = true)
            Spacer(Modifier.height(6.dp))
        }
        val status = LocalWinStatus.current
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            winButtonItems(look, page, setPage, onClose, onPlay, onFullDetails, extras).forEach { (l, f, isPlay) ->
                WinButton(
                    look, if (look.caps) l.uppercase() else l,
                    Modifier.weight(1f).then(if (isPlay) Modifier.focusRequester(playFocus) else Modifier),
                    wideButton = false, stretch = true, compact = true, onClick = f
                )
            }
            // (Update 213: Performance as a round button in the TV's small strip.)
            if (page == WinPage.MAIN && status?.perf != null) Box(Modifier.focusRequester(status.perfFocus)) {
                WinIconButton(look, androidx.compose.material.icons.Icons.Filled.Speed, PERF_LABEL, 32.dp, status.onPerf)
            }
            // (The small buttons only when there's room for them.)
            if (roomy && page == WinPage.MAIN) {
                WinIcons(look, extras, 32.dp)
                WinIcons(look, extras, 32.dp, WinSlot.BOTTOM)
            }
        }
        if (roomy) WinStatusLine(look)
    }
}

/**
 * Update 125: the parts placed "On the picture": the disc / cartridge in its lower right corner, the facts,
 * achievements and story in its lower left (under the name when [nameShown]), the small buttons in its top right.
 */
@Composable
private fun WinPicturePart(look: WinLook, g: Game, state: LauncherUiState, cover: String?, extras: WinExtras, modifier: Modifier, nameShown: Boolean, showcase: Boolean = false) {
    Box(modifier) {
        // (Update 162: not while the piece itself is out beside the window.)
        if (!showcase && !LocalWinInspect.current && extras.at(WinPart.MEDIA, WinSlot.PICTURE) && mediaFor(g.platform, gameKey = g.stableKey) != null) WinGameMedia(
            g, state, cover, Modifier.align(Alignment.BottomEnd).padding(10.dp).fillMaxHeight(0.46f)
                .aspectRatio(if (Features.gameWindowCase.state.value && discCaseFits(g.platform)) 1.35f else 1f)
        )
        if (extras.textAt(WinSlot.PICTURE)) {
            if (!nameShown) Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.6f)))))
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(0.62f).padding(start = 14.dp, end = 14.dp, bottom = if (nameShown) 64.dp else 12.dp)) {
                WinInfo(look, g, state, extras, descLines = 2, ink = Color.White, muted = Color.White.copy(alpha = 0.82f), slot = WinSlot.PICTURE)
            }
        }
        if (extras.at(WinPart.ICONS, WinSlot.PICTURE)) Box(Modifier.align(Alignment.TopEnd).padding(10.dp)) {
            WinIcons(look, extras, 36.dp, WinSlot.PICTURE)
        }
    }
}

/** Update 125: the parts placed "Under the buttons". */
@Composable
private fun WinBottomPart(look: WinLook, g: Game, state: LauncherUiState, cover: String?, extras: WinExtras, width: Dp?, center: Boolean = false) {
    // (Update 162: not while the piece itself is out beside the window.)
    val media = !LocalWinInspect.current && extras.at(WinPart.MEDIA, WinSlot.BOTTOM) && mediaFor(g.platform, gameKey = g.stableKey) != null
    val text = extras.textAt(WinSlot.BOTTOM)
    val icons = extras.at(WinPart.ICONS, WinSlot.BOTTOM)
    if (!media && !text && !icons) return
    Spacer(Modifier.height(10.dp))
    Row((if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()), verticalAlignment = Alignment.CenterVertically) {
        if (media) {
            WinGameMedia(g, state, cover, Modifier.height(70.dp).aspectRatio(if (Features.gameWindowCase.state.value && discCaseFits(g.platform)) 1.35f else 1f))
            Spacer(Modifier.width(10.dp))
        }
        if (text) Box(Modifier.weight(1f)) { WinInfo(look, g, state, extras, descLines = 2, center = center && !media, slot = WinSlot.BOTTOM) }
        else Spacer(Modifier.weight(1f))
        if (icons) { Spacer(Modifier.width(8.dp)); WinIcons(look, extras, 38.dp, WinSlot.BOTTOM) }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun WindowBody(
    look: WinLook, g: Game, state: LauncherUiState, cover: String?, wide: String?, backdrop: String?,
    page: WinPage, setPage: (WinPage) -> Unit, maxW: Dp, maxH: Dp, playFocus: FocusRequester, listFocus: FocusRequester,
    onClose: () -> Unit, onPlay: () -> Unit, onFullDetails: () -> Unit, extras: WinExtras, options: List<WinOption>
) {
    val details = page != WinPage.MAIN
    val panelShape = RoundedCornerShape(look.corner)
    val panelMod = Modifier
        .then(if (look.panel != null) Modifier.clip(panelShape).background(look.panel) else Modifier)
        .then(look.stripes?.takeIf { look.panel != null }?.let { line ->
            Modifier.drawBehind {
                val step = 3.dp.toPx(); val w = (step / 3f).coerceAtLeast(1f)
                var x = 0f
                while (x < size.width) { drawRect(line, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Size(w, size.height)); x += step }
            }
        } ?: Modifier)
        .then(if (look.frame != null && look.frameW > 0.dp) Modifier.border(look.frameW, look.frame, panelShape) else Modifier)
        .padding(if (look.panel != null) 14.dp else 0.dp)
    val titles = LocalTitleOptions.current
    val name = titles.display(g.name)
    val sub = listOfNotNull(titles.console(g.platform), state.playtime[g.stableKey]?.takeIf { it > 0L }?.let { stylePlaytime(it) }).joinToString("  ·  ")
    // The buttons: Play / Continue first (update 125), then the page's own.
    val status = LocalWinStatus.current
    @Composable
    fun Buttons(vertical: Boolean, fill: Boolean = false) {
        // Update 213: Performance as a button after Play — or, where the buttons share a narrow row, as a round icon
        // at the row's end (the window's own action strip).
        val perfHere = status?.perf != null
        val all = winButtonItems(look, page, setPage, onClose, onPlay, onFullDetails, extras, if (perfHere && !fill) status!!.onPerf else null)
        fun btnMod(l: String, isPlay: Boolean): Modifier =
            if (isPlay) Modifier.focusRequester(playFocus) else if (l == PERF_LABEL && status != null) Modifier.focusRequester(status.perfFocus) else Modifier
        if (vertical) Column(verticalArrangement = Arrangement.spacedBy(if (look.btn == Btn.TEXT) 4.dp else 10.dp)) {
            all.forEach { (l, f, isPlay) -> WinButton(look, if (look.caps) l.uppercase() else l, btnMod(l, isPlay), wideButton = true, onClick = f) }
            // (Update 119: the game's options, achievements and save sync under them.)
            if (!details) WinIcons(look, extras)
            WinStatusLine(look)
        } else BoxWithConstraints(if (fill) Modifier.fillMaxWidth() else Modifier) {
          // (Update 261, #120: a row wider than the window ran off its right edge — Twin desk / Twin icons' icons, the
          // Disc station window's Details and Back on the RP Classic. Estimated from the words: when the buttons alone
          // don't fit they share the width (Performance becomes its round icon); when the icons don't fit beside them
          // they go to a row under them.)
          fun est(items: List<Triple<String, () -> Unit, Boolean>>) = items.sumOf { (l, _, _) -> l.length * 9 + 60 } + (items.size - 1) * 12
          val share = fill || maxWidth.value < est(all)
          val items = if (share && !fill) winButtonItems(look, page, setPage, onClose, onPlay, onFullDetails, extras, null) else all
          val iconsBelow = !share && !details && maxWidth.value < est(all) + (3 + (if (extras.onManual != null) 1 else 0) + (if (extras.onInspect != null) 1 else 0)) * 52
          Column(if (share) Modifier.fillMaxWidth() else Modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            if (share) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // [share]: the buttons share the width (a narrow column).
                items.forEach { (l, f, isPlay) ->
                    WinButton(
                        look, if (look.caps) l.uppercase() else l,
                        Modifier.weight(1f).then(btnMod(l, isPlay)),
                        wideButton = false, stretch = true, onClick = f
                    )
                }
            } else {
                // (Update 291, Toni: "the icon isn't reachable": the Inspect icon was the fifth round icon, and on the Cube
                // layout's window (Mario Party, Animal Crossing) it ran past the window's right edge — the estimate above is
                // only a guess from the words. A FlowRow puts whatever doesn't fit on a second line instead.)
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEach { (l, f, isPlay) ->
                        WinButton(look, if (look.caps) l.uppercase() else l, btnMod(l, isPlay), wideButton = false, stretch = false, onClick = f)
                    }
                    if (!details && !iconsBelow) WinIcons(look, extras, 44.dp)
                }
            }
            if (iconsBelow) Box(Modifier.padding(top = 8.dp)) { WinIcons(look, extras, 40.dp) }
            // (Update 261, #120 Thor: in a narrow window — the Grid's and the Dashboard's — the round icons shared the row
            // with the three buttons and squeezed their words to "Conti", "Detail"; they get a row of their own under them.)
            if (share && !details) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (perfHere) Box(Modifier.focusRequester(status!!.perfFocus)) {
                    WinIconButton(look, androidx.compose.material.icons.Icons.Filled.Speed, PERF_LABEL, 38.dp, status.onPerf)
                }
                WinIcons(look, extras, 38.dp)
            }
            WinStatusLine(look)
          }
        }
    }
    @Composable
    fun Media(m: Modifier, showName: Boolean) {
        val shape = RoundedCornerShape(look.mediaCorner)
        val framed = look.mediaFrame != null && look.mediaFrameW > 0.dp
        Box(
            m.then(if (framed) Modifier.clip(RoundedCornerShape(look.mediaCorner + look.mediaFrameW)).background(look.mediaFrame!!).padding(look.mediaFrameW) else Modifier)
                .clip(shape).background(if (details) (look.paper ?: Color.Black) else Color.Black)
                // A tap on the picture turns it over (picture ⇄ details); the options and achievements pages take their own taps.
                .then(
                    if (page == WinPage.MAIN || page == WinPage.DETAILS)
                        Modifier.pointerInput(page) { detectTapGestures { setPage(if (page == WinPage.MAIN) WinPage.DETAILS else WinPage.MAIN) } }
                    else Modifier
                )
        ) {
            AnimatedContent(
                targetState = page, label = "gameWindowPage",
                transitionSpec = { unlessCategoryChange { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) } }
            ) { pg ->
                when (pg) {
                    WinPage.MAIN -> Box(Modifier.fillMaxSize()) {
                        if (backdrop != null) AsyncImage(backdrop, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().pixelArtwork())
                        val video = state.videoUrls[g.stableKey]
                        VideoCrossfade(video, state.appearance.videoSound, Modifier.fillMaxSize())
                        if (showName) {
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.72f)))))
                            // (Update 199: the name stops short of the disc / cartridge drawn in the picture's lower right.)
                            val mediaHere = look.arrange != Arrange.SHOWCASE && !LocalWinInspect.current && extras.at(WinPart.MEDIA, WinSlot.PICTURE) &&
                                mediaFor(g.platform, gameKey = g.stableKey) != null
                            val mediaAspect = if (Features.gameWindowCase.state.value && discCaseFits(g.platform)) 1.35f else 1f
                            val nameW = if (mediaHere) (0.97f - 0.46f * mediaAspect / 1.78f).coerceIn(0.4f, 1f) else 1f
                            // (Update 329, seen on the AYN Thor: in the narrow window beside Case stacks' case / Disc station's
                            // disc the picture is short, and a two-line name at 22 sp pushed the console line out of it — cut
                            // in half at the bottom. The name steps down with the picture's height.)
                            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                                val nameSize = when { maxHeight < 125.dp -> 15; maxHeight < 175.dp -> 18; else -> 22 }
                                val pad = if (maxHeight < 125.dp) 10.dp else 16.dp
                                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(nameW).padding(pad)) {
                                    WinText(look, name, Color.White, nameSize, FontWeight.Bold, shadow = true)
                                    if (sub.isNotBlank()) WinText(look, sub, Color.White.copy(alpha = 0.85f), if (nameSize < 22) 11 else 13, FontWeight.Normal, shadow = true)
                                }
                            }
                        }
                        // Update 119 / 125: what's placed on the picture (the Grid's window has its disc on top instead).
                        WinPicturePart(look, g, state, cover, extras, Modifier.fillMaxSize(), nameShown = showName, showcase = look.arrange == Arrange.SHOWCASE)
                    }
                    WinPage.DETAILS -> WindowDetails(look, g, state, cover, Modifier.fillMaxSize())
                    WinPage.OPTIONS -> WindowOptions(look, options, listFocus, Modifier.fillMaxSize())
                    WinPage.TROPHIES -> WindowTrophies(look, g, state, listFocus, Modifier.fillMaxSize(), extras.onLoadComments)
                    WinPage.PERF -> WindowPerformance(look, listFocus, Modifier.fillMaxSize())
                }
            }
        }
    }
    when (look.arrange) {
        // The picture (16:9) on top, the buttons under it.
        Arrange.STACK -> {
            // (Update 119: facts, achievements and two lines of the story under the picture, when chosen and there's room.)
            val info = extras.textAt(WinSlot.INFO) && maxH > 300.dp
            val bottom = (extras.textAt(WinSlot.BOTTOM) || extras.at(WinPart.MEDIA, WinSlot.BOTTOM) || extras.at(WinPart.ICONS, WinSlot.BOTTOM)) && !details
            // (Update 260: + the status line under the buttons — "Saves: …" and the power mode — which had no room and
            // showed as two cut-off slivers at the window's bottom edge on the Thor and the RP Classic.)
            // (Update 274, Toni: "buttons a bit down / centred" — beside an inspected piece on the Flip 2 the window is
            // narrow, so the small round icons go to a row of their own under the buttons, and the facts + achievements +
            // two lines of story are ~100 dp, not 74: the status line was still squeezed to two empty slivers at the
            // bottom edge. Room for both now; the picture gives way.)
            val inner = (maxW - (if (look.panel != null) 28.dp else 0.dp)).coerceAtLeast(120.dp)
            val iconsRow = if (!details && inner < 600.dp) 52.dp else 0.dp
            val btnRoom = 100.dp + iconsRow + (if (info) 104.dp else 0.dp) + (if (bottom) 82.dp else 0.dp) + (if (status != null) 46.dp else 0.dp)
            val screenW = minOf(inner, (maxH - btnRoom - (if (look.panel != null) 28.dp else 0.dp)) * 1.78f).coerceAtLeast(120.dp)
            // (Update 318, Toni: "sometimes stuff gets cut off and I can't scroll down there": the room above is a guess — a
            // fact line that wraps, a long title — so the window now scrolls when it still doesn't fit; the D-pad's focus
            // brings the save / performance line into view.)
            // (Update 321: measured, not guessed — the picture is laid out last and takes the height the facts, buttons and
            // status line leave (never wider than the estimate), so nothing is pushed off the bottom and nothing scrolls.)
            Column(
                panelMod.heightIn(max = (maxH - (if (look.panel != null) 28.dp else 0.dp)).coerceAtLeast(120.dp)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Media(Modifier.weight(1f, fill = false).widthIn(max = screenW).aspectRatio(1.78f, matchHeightConstraintsFirst = true), showName = true)
                if (info && !details) {
                    Spacer(Modifier.height(8.dp))
                    // (Update 330, phase 4: never narrower than the window — beside an inspected case on Hero tiles the
                    // picture shrinks to a small tile, and the facts under it broke into a word per line.)
                    Box(Modifier.width(maxOf(screenW, minOf(inner, 380.dp)))) { WinInfo(look, g, state, extras, descLines = 2) }
                }
                Spacer(Modifier.height(14.dp))
                Buttons(vertical = false)
                if (!details) WinBottomPart(look, g, state, cover, extras, screenW)
            }
        }
        // Update 119 (the Grid): the game's disc / cartridge on top in the middle, its name, facts and story under it.
        Arrange.SHOWCASE -> {
            val w = minOf(maxW, 560.dp)
            val h = minOf(maxH, w * 1.05f)
            Column(panelMod.width(w).heightIn(max = h), horizontalAlignment = Alignment.CenterHorizontally) {
                if (!details) {
                    // (Update 162: the picture instead while the piece itself is out beside the window.)
                    if (!LocalWinInspect.current && (extras.at(WinPart.MEDIA, WinSlot.PICTURE) || extras.at(WinPart.MEDIA, WinSlot.INFO))) {
                        WinGameMedia(g, state, cover, Modifier.fillMaxWidth().height((h * 0.36f).coerceIn(90.dp, 220.dp)))
                        Spacer(Modifier.height(10.dp))
                    } else {
                        Media(Modifier.fillMaxWidth().height((h * 0.36f).coerceIn(90.dp, 220.dp)), showName = false)
                        Spacer(Modifier.height(10.dp))
                    }
                    WinText(look, name, look.ink, 22, FontWeight.Bold)
                    if (sub.isNotBlank()) WinText(look, sub, look.muted, 13, FontWeight.Normal)
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().weight(1f, fill = false)) { WinInfo(look, g, state, extras, descLines = if (h > 380.dp) 5 else 3, center = true) }
                } else Media(Modifier.fillMaxWidth().height((h * 0.62f).coerceAtLeast(140.dp)), showName = false)
                Spacer(Modifier.height(12.dp))
                Buttons(vertical = false, fill = true)
                if (!details) WinBottomPart(look, g, state, cover, extras, null, center = true)
            }
        }
        // The picture fills the window; the name and the buttons over its foot.
        Arrange.FULL -> {
            val w = minOf(maxW, maxH * 1.78f)
            // (Update 261, #120 Thor: on a handheld the picture is ~380 dp tall — the name, facts, story, buttons and status
            // line ran past it, cut at the top and the buttons cut in half at the bottom. The story only where there's
            // room, and the column scrolls (the buttons in view) if it still doesn't fit.)
            val roomy = w / 1.78f > 470.dp
            // (Update 261, #120 RP Classic: the game's cartridge / disc drawn on the picture's lower right sat over the
            // last buttons; the buttons stop short of it, sharing their width.)
            val mediaOnPicture = !LocalWinInspect.current && extras.at(WinPart.MEDIA, WinSlot.PICTURE) && mediaFor(g.platform, gameKey = g.stableKey) != null
            Box(panelMod.width(w).height(w / 1.78f).clip(RoundedCornerShape(look.corner))) {
                Media(Modifier.fillMaxSize(), showName = false)
                if (!details) {
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.85f)))))
                    val scroll = rememberScrollState()
                    LaunchedEffect(scroll.maxValue) { if (scroll.maxValue > 0) scroll.scrollTo(scroll.maxValue) }
                    Column(Modifier.align(Alignment.BottomStart).verticalScroll(scroll).padding(if (roomy) 22.dp else 14.dp)) {
                        WinText(look, name, Color.White, if (roomy) 28 else 22, FontWeight.Bold, shadow = true)
                        if (sub.isNotBlank()) WinText(look, sub, Color.White.copy(alpha = 0.85f), 14, FontWeight.Normal, shadow = true)
                        Spacer(Modifier.height(6.dp))
                        // (On a handheld the facts too would push the name off the top: the name, buttons and icons only.)
                        if (roomy) Box(Modifier.fillMaxWidth(0.7f)) { WinInfo(look, g, state, extras, descLines = 2, ink = Color.White, muted = Color.White.copy(alpha = 0.8f)) }
                        Spacer(Modifier.height(14.dp))
                        if (!roomy) Box(Modifier.fillMaxWidth(if (mediaOnPicture) 0.6f else 0.82f)) { Buttons(vertical = false, fill = true) }
                        else Buttons(vertical = false)
                        Box(Modifier.fillMaxWidth(0.7f)) { WinBottomPart(look, g, state, cover, extras, null) }
                    }
                } else Box(Modifier.align(Alignment.BottomEnd).padding(14.dp)) { Buttons(vertical = false) }
            }
        }
        // The cover (or the game's look) on the left, its facts and the buttons on the right.
        Arrange.SPLIT -> {
            val h = minOf(maxH, maxW * 0.56f).coerceAtLeast(160.dp)
            Row(panelMod.width(minOf(maxW, h * 1.9f)).height(h), verticalAlignment = Alignment.CenterVertically) {
                // (Update 162: no cover beside the info while the piece itself is out beside the window.)
                if (!LocalWinInspect.current) {
                    Box(Modifier.fillMaxHeight().aspectRatio(0.72f, matchHeightConstraintsFirst = true).clip(RoundedCornerShape(look.mediaCorner)).background(placeholderBrush(g.hue))) {
                        // (Update 202: Crop fills the place; the cover's file's flat strips trimmed — rememberCoverRequest.)
                        if (cover != null) AsyncImage(rememberCoverRequest(cover), g.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().pixelArtwork())
                    }
                    Spacer(Modifier.width(16.dp))
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    WinText(look, name, look.ink, 22, FontWeight.Bold)
                    if (sub.isNotBlank()) WinText(look, sub, look.muted, 13, FontWeight.Normal)
                    if (!details) { Spacer(Modifier.height(4.dp)); WinInfo(look, g, state, extras, descLines = if (h > 300.dp) 2 else 0) }
                    Spacer(Modifier.height(10.dp))
                    Media(Modifier.fillMaxWidth().weight(1f), showName = false)
                    Spacer(Modifier.height(12.dp))
                    Buttons(vertical = false, fill = true)
                    if (!details) WinBottomPart(look, g, state, cover, extras, null)
                }
            }
        }
        // A column of choices beside the picture.
        Arrange.LIST -> {
            val h = minOf(maxH, maxW * 0.5f).coerceAtLeast(160.dp)
            Row(panelMod.width(minOf(maxW, h * 2.2f)).height(h), verticalAlignment = Alignment.CenterVertically) {
                // (Update 199: the column scrolls when a long name and the facts leave no room for the last choice.)
                Box(Modifier.width(240.dp).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        WinText(look, name, look.ink, 20, FontWeight.Bold)
                        if (sub.isNotBlank()) WinText(look, sub, look.muted, 12, FontWeight.Normal)
                        if (!details) { Spacer(Modifier.height(4.dp)); WinInfo(look, g, state, extras, descLines = 0) }
                        Spacer(Modifier.height(if (h > 300.dp) 16.dp else 8.dp))
                        Buttons(vertical = true)
                        if (!details) WinBottomPart(look, g, state, cover, extras, null)
                    }
                }
                Spacer(Modifier.width(18.dp))
                Media(Modifier.weight(1f).fillMaxHeight(), showName = false)
            }
        }
    }
}

/**
 * Update 330: the window drawn by the layout itself (WinScenes.kt) — the same game, buttons, pages and focus as
 * [WindowBody], handed over as a [WinSceneCtx]: Play / Continue first (the D-pad starts on it), the page's own buttons,
 * the small tools (options, achievements, save sync, manual, inspect), the save / performance chips, and the details /
 * options / achievements / performance pages drawn in the layout's colours for the scene to frame. [reveal]: how far
 * the window is open (0…1), for the scene's own entrance.
 */
@Composable
private fun SceneBody(
    scene: WinScene, look: WinLook, g: Game, state: LauncherUiState, cover: String?, wide: String?, backdrop: String?,
    page: WinPage, setPage: (WinPage) -> Unit, maxW: Dp, maxH: Dp, playFocus: FocusRequester, listFocus: FocusRequester,
    onClose: () -> Unit, onPlay: () -> Unit, onFullDetails: () -> Unit, extras: WinExtras, options: List<WinOption>,
    closing: () -> Boolean = { false },
    reveal: () -> Float
) {
    val titles = LocalTitleOptions.current
    val status = LocalWinStatus.current
    val meta = state.metadata[g.stableKey]
    val I = androidx.compose.material.icons.Icons.Filled
    val perf = status?.perf != null
    val toMain = { setPage(WinPage.MAIN) }
    // Update 347: back from Details / Options / Achievements, the D-pad lands on the button that opened the page (it
    // went to Play). Performance has its own (WinStatus.perfFocus, see GameWindow's page effect).
    val detailsF = remember { FocusRequester() }
    val optionsF = remember { FocusRequester() }
    val trophiesF = remember { FocusRequester() }
    val returnTo = remember(g.stableKey) { arrayOfNulls<FocusRequester>(1) }
    fun opener(f: FocusRequester, act: () -> Unit): () -> Unit = { returnTo[0] = f; act() }
    LaunchedEffect(page) {
        if (page != WinPage.MAIN) return@LaunchedEffect
        val f = returnTo[0] ?: return@LaunchedEffect
        returnTo[0] = null
        // (After the window's own ask for Play, 60 ms after the page changes.)
        kotlinx.coroutines.delay(120)
        runCatching { f.requestFocus() }
    }
    val buttons = buildList {
        add(WinAction(look.play.lowercase().replaceFirstChar { it.uppercase() }, I.PlayArrow, onPlay, focus = playFocus, primary = true))
        when (page) {
            WinPage.MAIN -> {
                if (perf) add(WinAction(PERF_LABEL, I.Speed, status!!.onPerf, focus = status.perfFocus))
                add(WinAction(look.details.lowercase().replaceFirstChar { it.uppercase() }, I.Info, opener(detailsF) { setPage(WinPage.DETAILS) }, focus = detailsF))
                add(WinAction(look.back.lowercase().replaceFirstChar { it.uppercase() }, I.Close, onClose, back = true))
            }
            WinPage.DETAILS -> { add(WinAction("Full details", I.Description, onFullDetails)); add(WinAction("Back", I.Close, toMain, back = true)) }
            WinPage.TROPHIES -> { add(WinAction("Full list", I.FormatListBulleted, extras.onFullAchievements)); add(WinAction("Back", I.Close, toMain, back = true)) }
            WinPage.OPTIONS, WinPage.PERF -> add(WinAction("Back", I.Close, toMain, back = true))
        }
    }
    val tools = buildList {
        add(WinAction("Options", I.Settings, opener(optionsF, extras.onOptions), focus = optionsF))
        add(WinAction("Achievements", I.EmojiEvents, opener(trophiesF, extras.onAchievements), focus = trophiesF))
        add(WinAction("Save sync", I.CloudSync, extras.onSync))
        extras.onManual?.let { add(WinAction("Manual", I.MenuBook, it)) }
        extras.onInspect?.let { add(WinAction("Inspect", InspectIcon.vector, it)) }
    }
    val chips = buildList {
        status?.let { st ->
            val sv = st.sync
            add(WinAction("Saves: " + sv.text, sv.icon, if (sv.inert) ({}) else if (sv.setUp) st.onSyncSetUp else st.onSync, urgent = sv.urgent))
            st.perf?.let { p -> add(WinAction(p.perf.summary, I.Speed, st.onPerf)) }
        }
    }
    val list = state.raAchievementDetails[g.stableKey].orEmpty()
    val p = state.raProgress[g.stableKey]
    val total = if (list.isNotEmpty()) list.size else p?.NumPossibleAchievements ?: 0
    val earned = if (list.isNotEmpty()) list.count { it.isUnlocked } else p?.NumAchieved ?: 0
    val ctx = WinSceneCtx(
        g = g, state = state, cover = cover, wide = wide, backdrop = backdrop, logo = bigLogo(g, state),
        name = titles.display(g.name),
        console = if (g.sourceType == com.shelf.launcher.data.SourceType.APP) "Android" else titles.console(g.platform),
        playtime = state.playtime[g.stableKey]?.takeIf { it > 0L },
        lastPlayed = state.lastPlayedAt[g.stableKey],
        year = meta?.releaseYear?.toString(), developer = meta?.developer?.takeIf { it.isNotBlank() },
        genres = meta?.genres?.take(2)?.joinToString(", ")?.takeIf { it.isNotBlank() },
        summary = meta?.summary?.takeIf { it.isNotBlank() },
        earned = earned, total = total,
        page = page, setPage = setPage, playFocus = playFocus, listFocus = listFocus,
        buttons = buttons, tools = tools, chips = chips, close = onClose,
        inspecting = LocalWinInspect.current, accent = look.accent, maxW = maxW, maxH = maxH, reveal = reveal,
        closing = closing,
        pageBody = { m ->
            when (page) {
                WinPage.MAIN -> {}
                WinPage.DETAILS -> WindowDetails(look, g, state, cover, m)
                WinPage.OPTIONS -> WindowOptions(look, options, listFocus, m)
                WinPage.TROPHIES -> WindowTrophies(look, g, state, listFocus, m, extras.onLoadComments)
                WinPage.PERF -> WindowPerformance(look, listFocus, m)
            }
        }
    )
    DrawWinScene(scene, ctx)
}

/**
 * Update 213: the small line under the window's buttons, on every page: the game's save sync (a tap / A opens its
 * sync, or Settings › Sync when it isn't set up) and its performance (a tap / A opens the Performance page). An icon
 * with short words; stronger only when something needs you.
 */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun WinStatusLine(look: WinLook) {
    val st = LocalWinStatus.current ?: return
    Spacer(Modifier.height(6.dp))
    // (Update 285, Toni: "make sure everything is to be seen": the chips go onto a second line when they don't fit beside
    // each other, each in full — they were cut with "…" and, on the Thor's top screen, cut off by the window's edge.)
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val sv = st.sync
        WinStatusChip(look, sv.icon, "Saves: " + sv.text, urgent = sv.urgent, onClick = if (sv.inert) ({}) else if (sv.setUp) st.onSyncSetUp else st.onSync)
        st.perf?.let { p ->
            WinStatusChip(look, androidx.compose.material.icons.Icons.Filled.Speed, p.perf.summary, urgent = false, onClick = st.onPerf)
        }
    }
}

@Composable
private fun WinStatusChip(look: WinLook, icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, urgent: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val sounds = LocalUiSounds.current
    LaunchedEffect(focused) { if (focused) sounds?.play(UiSound.MOVE) }
    val shape = RoundedCornerShape(50)
    val warn = Color(0xFFFFB74D)
    Row(
        Modifier.clip(shape)
            .background(if (focused) look.btnOnFill else solid(if (urgent) warn.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.22f)))
            .then(if (urgent && !focused) Modifier.border(1.dp, warn.copy(alpha = 0.7f), shape) else Modifier)
            .clickable(interactionSource = source, indication = null) { sounds?.play(UiSound.SELECT); onClick() }
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val ink = if (focused) look.btnOnInk else if (urgent) warn else Color.White.copy(alpha = 0.82f)
        androidx.compose.material3.Icon(icon, null, tint = ink, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
        androidx.compose.material3.Text(
            text, color = ink, fontSize = 11.sp, fontWeight = if (urgent) FontWeight.Bold else FontWeight.Medium, maxLines = 2,
            fontFamily = if (look.mono) FontFamily.Monospace else null
        )
    }
}

/**
 * Update 213: the window's Performance page, in its style: For this game — power mode, AutoTDP (and its target),
 * underclock, fan, Advanced… (the whole tuner in the game's settings) and "Use … again" (its own settings removed).
 * Each row says what it is now and, until the game has its own, where that comes from. A / a tap steps a row on.
 */
@Composable
private fun WindowPerformance(look: WinLook, listFocus: FocusRequester, modifier: Modifier) {
    val st = LocalWinStatus.current
    val s = st?.perf
    val paper = look.paper ?: Color(0xFF15181E)
    val light = (paper.red * 0.3f + paper.green * 0.59f + paper.blue * 0.11f) > 0.6f
    val ink = if (light) Color(0xFF2E3640) else Color.White
    Column(
        modifier.background(paper).verticalScroll(rememberScrollState()).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // (If / else, never an early return out of the Column — see update 144.)
        if (s == null || st == null) {
            androidx.compose.material3.Text("Performance settings aren't available on this handheld.", color = ink, fontSize = 13.sp)
        } else {
        androidx.compose.material3.Text(
            if (look.caps) "PERFORMANCE · FOR THIS GAME" else "Performance · For this game", color = ink, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            fontFamily = if (look.mono) FontFamily.Monospace else null
        )
        androidx.compose.material3.Text("Now: " + s.perf.summary, color = ink.copy(alpha = 0.78f), fontSize = 12.sp, maxLines = 2)
        perfRows(s, st.hasFan, st.onPerfAdvanced).forEachIndexed { i, row ->
            WinRow(look, if (i == 0) Modifier.focusRequester(listFocus) else Modifier, onClick = { row.onClick?.invoke() }) { on ->
                Column(Modifier.weight(1f)) {
                    val c = when { on -> look.btnOnInk; row.destructive -> Color(0xFFFF8A80); else -> look.btnInk }
                    androidx.compose.material3.Text(
                        if (look.caps) row.label.uppercase() else row.label, color = c, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontFamily = if (look.mono) FontFamily.Monospace else null
                    )
                    androidx.compose.material3.Text(
                        row.value, color = c.copy(alpha = 0.72f), fontSize = 11.sp, maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontFamily = if (look.mono) FontFamily.Monospace else null
                    )
                }
            }
        }
        androidx.compose.material3.Text(perfNote(s), color = ink.copy(alpha = 0.7f), fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

/** Update 125: one row of the window's own lists (options, achievements), in its colours and button shape. */
@Composable
private fun WinRow(look: WinLook, modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.(Boolean) -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val sounds = LocalUiSounds.current
    LaunchedEffect(focused) { if (focused) sounds?.play(UiSound.MOVE) }
    val shape: Shape = when (look.btn) {
        Btn.PILL -> RoundedCornerShape(14.dp)
        Btn.ROUNDED -> RoundedCornerShape(10.dp)
        Btn.SQUARE -> RoundedCornerShape(0.dp)
        Btn.LCD -> RoundedCornerShape(6.dp)
        Btn.TEXT -> RoundedCornerShape(4.dp)
        Btn.TAB -> RoundedCornerShape(topStart = 4.dp, topEnd = 12.dp, bottomEnd = 4.dp, bottomStart = 4.dp)
    }
    Row(
        modifier.fillMaxWidth().heightIn(min = 44.dp)
            .clip(shape)
            .background(if (focused) look.btnOnFill else if (look.btn == Btn.TEXT) solid(Color.Transparent) else look.btnFill)
            .then(if (look.btnBorder != null) Modifier.border(if (focused) 2.dp else 1.dp, if (focused) look.accent else look.btnBorder, shape) else Modifier)
            .clickable(interactionSource = source, indication = null) { sounds?.play(UiSound.SELECT); onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { content(focused) }
}

/** Update 125: the game's options as a page of the window (they used to open a separate menu over it). */
@Composable
private fun WindowOptions(look: WinLook, options: List<WinOption>, listFocus: FocusRequester, modifier: Modifier) {
    val paper = look.paper ?: Color(0xFF15181E)
    Column(
        modifier.background(paper).verticalScroll(rememberScrollState()).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEachIndexed { i, opt ->
            WinRow(look, if (i == 0) Modifier.focusRequester(listFocus) else Modifier, onClick = opt.onClick) { on ->
                Column(Modifier.weight(1f)) {
                    val ink = when {
                        on -> look.btnOnInk
                        opt.destructive -> Color(0xFFFF8A80)
                        else -> look.btnInk
                    }
                    androidx.compose.material3.Text(
                        if (look.caps) opt.label.uppercase() else opt.label, color = ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontFamily = if (look.mono) FontFamily.Monospace else null
                    )
                    opt.sub?.let {
                        androidx.compose.material3.Text(
                            it, color = ink.copy(alpha = 0.72f), fontSize = 11.sp, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontFamily = if (look.mono) FontFamily.Monospace else null
                        )
                    }
                }
            }
        }
    }
}

private val LockedGrey = androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) })

/**
 * Update 125: the game's achievements as a page of the window, in its style: how many you have, then each one —
 * earned in colour, still to get in grey.
 * Update 132: the first row is the order (A steps through Set order, Still to get first, Earned first, Latest earned
 * first, Most points first, Rarest first; it stays for every game), and A on an achievement opens it under itself: all
 * of what it asks, when you earned it, how many players have it, and the comments people left on RetroAchievements.
 */
@Composable
private fun WindowTrophies(look: WinLook, g: Game, state: LauncherUiState, listFocus: FocusRequester, modifier: Modifier, onLoadComments: (Int) -> Unit = {}) {
    val paper = look.paper ?: Color(0xFF15181E)
    val light = (paper.red * 0.3f + paper.green * 0.59f + paper.blue * 0.11f) > 0.6f
    val ink = if (light) Color(0xFF2E3640) else Color.White
    val order = Features.winTrophyOrder.state.value
    val details = state.raAchievementDetails[g.stableKey]
    val list = remember(details, order) { trophyOrder(details.orEmpty(), order) }
    val earned = list.count { it.isUnlocked }
    var openId by remember(g.stableKey) { mutableStateOf<Int?>(null) }
    Column(modifier.background(paper).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.EmojiEvents, null, tint = look.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            androidx.compose.material3.Text(
                (if (look.caps) "ACHIEVEMENTS" else "Achievements") + "  $earned / ${list.size}", color = ink, fontSize = 15.sp,
                fontWeight = FontWeight.Bold, fontFamily = if (look.mono) FontFamily.Monospace else null
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(ink.copy(alpha = 0.18f))) {
                Box(Modifier.fillMaxWidth(if (list.isEmpty()) 0f else earned.toFloat() / list.size).fillMaxHeight().background(look.accent))
            }
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            // The order: A steps to the next one.
            WinRow(look, Modifier.focusRequester(listFocus), onClick = {
                val all = Features.TrophyOrder.values()
                Features.winTrophyOrder.value = all[(order.ordinal + 1) % all.size]
            }) { on ->
                val rowInk = if (on) look.btnOnInk else look.btnInk
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.SwapVert, null, tint = rowInk, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                androidx.compose.material3.Text(
                    (if (look.caps) "ORDER: " + order.label.uppercase() else "Order: " + order.label), color = rowInk, fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f), fontFamily = if (look.mono) FontFamily.Monospace else null
                )
                androidx.compose.material3.Text("A: next", color = rowInk.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1)
            }
            list.forEach { a ->
                androidx.compose.runtime.key(a.id) {
                    val opened = openId == a.id
                    WinRow(look, onClick = {
                        openId = if (opened) null else a.id
                        if (!opened) onLoadComments(a.id)
                    }) { on ->
                        val rowInk = if (on) look.btnOnInk else look.btnInk
                        AsyncImage(
                            com.shelf.launcher.data.RaBadges.url(a.badgeName), a.title, contentScale = ContentScale.Crop,
                            colorFilter = if (a.isUnlocked) null else LockedGrey, alpha = if (a.isUnlocked) 1f else 0.55f,
                            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            androidx.compose.material3.Text(
                                a.title ?: "Achievement", color = rowInk.copy(alpha = if (a.isUnlocked) 1f else 0.62f), fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                fontFamily = if (look.mono) FontFamily.Monospace else null
                            )
                            a.description?.let {
                                androidx.compose.material3.Text(
                                    it, color = rowInk.copy(alpha = if (a.isUnlocked) 0.78f else 0.5f), fontSize = 11.sp, maxLines = if (opened) 6 else 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                        a.points?.let {
                            Spacer(Modifier.width(8.dp))
                            androidx.compose.material3.Text("$it", color = rowInk.copy(alpha = if (a.isUnlocked) 0.9f else 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (opened) TrophyMore(look, ink, a, state.raComments[a.id], a.id in state.raCommentsLoading)
                }
            }
        }
    }
}

/** Update 132: the achievements page's order ([Features.TrophyOrder]); ties keep the set's own order. */
private fun trophyOrder(all: List<com.shelf.launcher.data.RaAchievementDetail>, order: Features.TrophyOrder): List<com.shelf.launcher.data.RaAchievementDetail> {
    val set = all.sortedWith(com.shelf.launcher.data.RA_DISPLAY_ORDER)
    fun earnedAt(a: com.shelf.launcher.data.RaAchievementDetail) = a.dateEarnedHardcore ?: a.dateEarned ?: ""
    return when (order) {
        Features.TrophyOrder.SET -> set
        Features.TrophyOrder.TO_GET -> set.sortedBy { it.isUnlocked }
        Features.TrophyOrder.EARNED -> set.sortedBy { !it.isUnlocked }
        Features.TrophyOrder.LATEST -> set.filter { it.isUnlocked }.sortedByDescending { earnedAt(it) } + set.filter { !it.isUnlocked }
        Features.TrophyOrder.POINTS -> set.sortedByDescending { it.points ?: 0 }
        Features.TrophyOrder.RARE -> set.sortedBy { it.unlockPercent ?: 101f }
    }
}

/** Update 132: under an opened achievement — when you earned it, how rare it is, and the comments people left. */
@Composable
private fun TrophyMore(look: WinLook, ink: Color, a: com.shelf.launcher.data.RaAchievementDetail, comments: List<com.shelf.launcher.data.RaComment>?, loading: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, bottom = 4.dp)
            .clip(RoundedCornerShape(8.dp)).background(ink.copy(alpha = 0.07f)).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val facts = buildList {
            (a.dateEarnedHardcore ?: a.dateEarned)?.let { add("Earned " + it.take(10) + if (a.dateEarnedHardcore != null) " (hardcore)" else "") }
            a.unlockPercent?.let { add("%.1f %% of players have it".format(it)) }
            if (a.isMissable) add("Missable")
        }
        if (facts.isNotEmpty()) androidx.compose.material3.Text(facts.joinToString("  ·  "), color = look.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        androidx.compose.material3.Text(if (look.caps) "COMMENTS" else "Comments", color = ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        when {
            comments == null && loading -> androidx.compose.material3.Text("Loading comments…", color = ink.copy(alpha = 0.6f), fontSize = 11.sp)
            comments == null -> androidx.compose.material3.Text("Couldn't load the comments (no connection?). Close it and open it again to try again.", color = ink.copy(alpha = 0.6f), fontSize = 11.sp)
            comments.isEmpty() -> androidx.compose.material3.Text("No comments yet.", color = ink.copy(alpha = 0.6f), fontSize = 11.sp)
            else -> comments.take(8).forEach { cm ->
                Column {
                    androidx.compose.material3.Text(
                        (cm.User ?: "Player") + (cm.Submitted?.let { "  ·  " + it.take(10) } ?: ""),
                        color = ink.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
                    )
                    androidx.compose.material3.Text(cm.CommentText.orEmpty().trim(), color = ink, fontSize = 11.sp, maxLines = 6, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun WinText(look: WinLook, text: String, color: Color, size: Int, weight: FontWeight, shadow: Boolean = false) {
    androidx.compose.material3.Text(
        if (look.caps) text.uppercase() else text, color = color, fontSize = size.sp, fontWeight = weight,
        // (Update 285, Toni: everything to be seen — "Pokemon Sil…", "4 h …": up to two lines, smaller until it fits.)
        maxLines = 2,
        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = (size * 0.55f).coerceAtLeast(9f).sp, maxFontSize = size.sp),
        fontFamily = if (look.mono) FontFamily.Monospace else null,
        style = if (shadow) nameTextStyle(com.shelf.launcher.data.NameBackdrop.SHADOW) else androidx.compose.ui.text.TextStyle.Default
    )
}

/** One of the window's buttons, drawn in the layout's own way; the one the D-pad is on takes the "on" colours. */
@Composable
private fun WinButton(look: WinLook, label: String, modifier: Modifier, wideButton: Boolean, stretch: Boolean = false, compact: Boolean = false, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val sounds = LocalUiSounds.current
    LaunchedEffect(focused) { if (focused) sounds?.play(UiSound.MOVE) }
    val shape: Shape = when (look.btn) {
        Btn.PILL -> RoundedCornerShape(50)
        Btn.ROUNDED -> RoundedCornerShape(10.dp)
        Btn.SQUARE -> RoundedCornerShape(0.dp)
        Btn.LCD -> RoundedCornerShape(6.dp)
        Btn.TEXT -> RoundedCornerShape(4.dp)
        Btn.TAB -> RoundedCornerShape(topStart = 4.dp, topEnd = 14.dp, bottomEnd = 4.dp, bottomStart = 4.dp)
    }
    val w = when {
        look.btn == Btn.TEXT || stretch -> null
        wideButton -> 200.dp
        look.btn == Btn.SQUARE -> 130.dp
        else -> 140.dp
    }
    val h = if (compact) 32.dp else when (look.btn) { Btn.TEXT -> 34.dp; Btn.LCD -> 36.dp; else -> 44.dp }
    Box(
        modifier
            // (Update 129: at least this wide, wider when its word needs it — "Achievements" used to end in "…".)
            .then(if (w != null) Modifier.widthIn(min = w) else Modifier)
            .height(h)
            .graphicsLayer { if (look.btn != Btn.TEXT) { val k = if (focused) 1.05f else 1f; scaleX = k; scaleY = k } }
            .clip(shape)
            .background(if (focused) look.btnOnFill else look.btnFill)
            .then(if (look.btnBorder != null) Modifier.border(if (focused && look.btn != Btn.SQUARE) 2.5.dp else 1.5.dp, if (focused) look.accent else look.btnBorder, shape) else Modifier)
            .clickable(interactionSource = source, indication = null) { sounds?.play(UiSound.SELECT); onClick() }
            .padding(horizontal = if (look.btn == Btn.TEXT) 6.dp else if (compact) 6.dp else 14.dp),
        contentAlignment = if (look.btn == Btn.TEXT) Alignment.CenterStart else Alignment.Center
    ) {
        if (look.btn == Btn.TEXT) {
            // The Cross menu's words: the one you're on bigger and glowing.
            androidx.compose.material3.Text(
                label, color = if (focused) look.btnOnInk else look.btnInk, fontSize = if (focused) 19.sp else 16.sp,
                fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1,
                style = if (focused) androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.White.copy(alpha = 0.9f), blurRadius = 14f)) else androidx.compose.ui.text.TextStyle.Default
            )
        } else androidx.compose.material3.Text(
            label, color = if (focused) look.btnOnInk else look.btnInk, fontSize = if (compact) 12.sp else if (look.btn == Btn.LCD) 13.sp else 15.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, fontFamily = if (look.mono) FontFamily.Monospace else null,
            // (Update 129: a little smaller rather than cut, where the button can't grow — e.g. buttons sharing a row.)
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(
                minFontSize = 9.sp, maxFontSize = if (compact) 12.sp else if (look.btn == Btn.LCD) 13.sp else 15.sp, stepSize = 0.5.sp
            )
        )
    }
}

/** The details page inside the window (its story, facts, play time and achievements), in the layout's colours. */
@Composable
private fun WindowDetails(look: WinLook, g: Game, state: LauncherUiState, cover: String?, modifier: Modifier) {
    val paper = look.paper ?: Color(0xFF15181E)
    val light = (paper.red * 0.3f + paper.green * 0.59f + paper.blue * 0.11f) > 0.6f
    val ink = if (light) Color(0xFF2E3640) else Color.White
    val muted = if (light) Color(0xFF6A7584) else Color.White.copy(alpha = 0.7f)
    val meta = state.metadata[g.stableKey]
    val titles = LocalTitleOptions.current
    Row(modifier.background(paper).padding(16.dp)) {
        Box(Modifier.fillMaxHeight().aspectRatio(0.72f, matchHeightConstraintsFirst = true).clip(RoundedCornerShape(8.dp)).background(placeholderBrush(g.hue))) {
            // (Update 202: the cover's file's flat strips trimmed — rememberCoverRequest; Crop fills the place.)
            if (cover != null) AsyncImage(rememberCoverRequest(cover), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            androidx.compose.material3.Text(titles.display(g.name), color = ink, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 2, fontFamily = if (look.mono) FontFamily.Monospace else null)
            val facts = listOfNotNull(
                titles.console(g.platform), meta?.releaseYear?.toString(), meta?.developer,
                meta?.genres?.take(2)?.joinToString(", ")?.takeIf { it.isNotBlank() }
            ).joinToString("  ·  ")
            androidx.compose.material3.Text(facts, color = muted, fontSize = 12.sp, maxLines = 2)
            Spacer(Modifier.height(8.dp))
            val list = state.raAchievementDetails[g.stableKey].orEmpty()
            val p = state.raProgress[g.stableKey]
            val total = if (list.isNotEmpty()) list.size else p?.NumPossibleAchievements ?: 0
            val earned = if (list.isNotEmpty()) list.count { it.isUnlocked } else p?.NumAchieved ?: 0
            val played = listOfNotNull(
                state.playtime[g.stableKey]?.takeIf { it > 0L }?.let { "Played " + stylePlaytime(it) },
                state.lastPlayedAt[g.stableKey]?.let { "Last played " + lastPlayedLabel(it) },
                if (total > 0) "Achievements $earned / $total" else null
            )
            if (played.isNotEmpty()) {
                androidx.compose.material3.Text(played.joinToString("  ·  "), color = if (light) ShelfTheme.accent else look.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
            }
            androidx.compose.material3.Text(meta?.summary?.takeIf { it.isNotBlank() } ?: "No description yet.", color = ink.copy(alpha = 0.9f), fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}
