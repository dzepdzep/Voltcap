package com.shelf.launcher.ui

import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.ui.zIndex
import androidx.compose.foundation.basicMarquee
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import kotlinx.coroutines.launch
import androidx.compose.ui.composed
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.LauncherUiState
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.HomeLayout

/*
 * Update 330 (Toni: "Redesign the layout popups properly — each popup a natural extension of its layout, not a recoloured
 * generic window"): the game windows that layouts draw themselves. GameWindow keeps everything that makes the window
 * work — opening out of the game's place and going back into it, the inspected piece, the pages, Play's focus, the
 * banner page / motions / classic window overrides — and hands the layout a [WinSceneCtx] with the game and every action;
 * the layout's scene arranges them its own way:
 *  - Cardboard box: an insert sheet beside the case lifted out of the box (the case itself is the window's piece).
 *  - File select: the file block turned over — its back, grown big, with chunky buttons.
 *  - Storage boxes: the stored entry's record — its sprite on a stand, tabs for its pages, a menu with a pointer.
 *  - Memory card: the browser's info screen for a save — its icon big and turning, its title in yellow, words below.
 *  - Case flow: the case out on the left, its media beside it — video / backdrop, screenshot slides, glossy pills.
 *  - Orbit deck: the CD-player terminal — the disc spinning in its tray, an LCD, transport keys.
 *  - Airy home: a floating white system panel with big round buttons and a small pixel display.
 *  - Night menu: angular status panels sliding in, slanted command bars with a pointer.
 * Settings › Game window › "<layout>'s window": built into the layout, or the standard window.
 */

/** Update 330: the layouts with a window of their own. */
internal enum class WinScene { BOX_SHEET, FILE_CARD, STORAGE_RECORD, MEMCARD_INFO, CASE_MEDIA, ORBIT_TERMINAL, AIRY_PANEL, NIGHT_STATUS,
    /** Update 344 (WinScenesConsole.kt). */
    BLADE_GUIDE, BUBBLE_CARD, CAMERA_MENU, PANEL_DETAIL, MAG_PAGE, SHOP_TAG, TERM_RUN,
    /** Update 347: Arcade cabinets' Insert Coin screen (WinScenesArcade.kt). */
    ARCADE_MENU,
    /** Next version: Cinema Premiere and the Arctic info drawer (WinScenesHomes.kt). */
    CINEMA_PREMIERE, ARCTIC_DRAWER,
    /** Update 408: the Channel grid window, a Wii Channel preview (WinScenesWii.kt). */
    WII_PREVIEW,
    /** Update 421 (WinScenesMore.kt): the layouts that had no window of their own. */
    RECORD_SLEEVE, CARD_BACK, DISC_PANEL, CORE_PANEL, CUBE_FACE, POCKET_LCD, INDEX_CARD, TWIN_HINGE, GALLERY_PLACARD,
    /** Update 422 (WinScenesHomes2.kt): the last shared-window layouts. */
    CROSS_OPTIONS, LIVE_PANEL, DASH_CARD, SIDE_DRAWER, SPOT_POSTER, DOCK_FLYOUT, HUB_PLATE, DISCOVER_CARD, CHIP_SHEET, LIBRARY_PAGE,
    TIMELINE_ENTRY, HERO_BANNER, TILE_CARD, VOLT_PANEL,
    /** Update 506 (WinSceneStudio.kt): Layout Studio's own window. */
    STUDIO_PANEL }

internal fun winSceneFor(layout: HomeLayout): WinScene? = when (layout) {
    HomeLayout.BOX -> WinScene.BOX_SHEET
    HomeLayout.FILESELECT -> WinScene.FILE_CARD
    HomeLayout.STORAGE -> WinScene.STORAGE_RECORD
    HomeLayout.MEMCARD -> WinScene.MEMCARD_INFO
    HomeLayout.CASEFLOW -> WinScene.CASE_MEDIA
    HomeLayout.ORBIT -> WinScene.ORBIT_TERMINAL
    HomeLayout.AIRY -> WinScene.AIRY_PANEL
    HomeLayout.NIGHT -> WinScene.NIGHT_STATUS
    HomeLayout.BLADES -> WinScene.BLADE_GUIDE
    HomeLayout.BUBBLES -> WinScene.BUBBLE_CARD
    HomeLayout.CAMERA -> WinScene.CAMERA_MENU
    HomeLayout.PANELS -> WinScene.PANEL_DETAIL
    HomeLayout.MAGAZINE -> WinScene.MAG_PAGE
    HomeLayout.SHOP -> WinScene.SHOP_TAG
    HomeLayout.TERMINAL -> WinScene.TERM_RUN
    HomeLayout.ARCADE -> WinScene.ARCADE_MENU
    HomeLayout.CINEMA -> WinScene.CINEMA_PREMIERE
    HomeLayout.ARCTIC -> WinScene.ARCTIC_DRAWER
    HomeLayout.CHAN -> WinScene.WII_PREVIEW
    // Update 421: a window for each of the layouts that only had the generic one.
    HomeLayout.CRATE -> WinScene.RECORD_SLEEVE
    HomeLayout.CARDS -> WinScene.CARD_BACK
    HomeLayout.DISCS -> WinScene.DISC_PANEL
    HomeLayout.GREENCORE -> WinScene.CORE_PANEL
    HomeLayout.CUBE -> WinScene.CUBE_FACE
    HomeLayout.POCKET_CLASSIC, HomeLayout.POCKET_RIBBON, HomeLayout.POCKET -> WinScene.POCKET_LCD
    HomeLayout.SHELF, HomeLayout.STACKS, HomeLayout.CONSOLES, HomeLayout.COVERDETAILS -> WinScene.INDEX_CARD
    HomeLayout.TWIN_DESK, HomeLayout.TWIN_ICONS, HomeLayout.TWIN_SHOWCASE -> WinScene.TWIN_HINGE
    HomeLayout.ARTLIB -> WinScene.GALLERY_PLACARD
    // Update 422: the rest. (Layout Studio is built by the user: the standard window in its chosen look. The TV room opens on
    // the TV's own screen, GameWindow's tvRect.)
    HomeLayout.CROSS -> WinScene.CROSS_OPTIONS
    HomeLayout.LIVE -> WinScene.LIVE_PANEL
    HomeLayout.DASH -> WinScene.DASH_CARD
    HomeLayout.SIDE -> WinScene.SIDE_DRAWER
    HomeLayout.SPOT -> WinScene.SPOT_POSTER
    HomeLayout.BAR -> WinScene.DOCK_FLYOUT
    HomeLayout.HUB -> WinScene.HUB_PLATE
    HomeLayout.RESUME -> WinScene.DISCOVER_CARD
    HomeLayout.CIRCUIT -> WinScene.CHIP_SHEET
    HomeLayout.LIBRARY -> WinScene.LIBRARY_PAGE
    HomeLayout.TIMELINE -> WinScene.TIMELINE_ENTRY
    HomeLayout.HERO, HomeLayout.HERO_TILES -> WinScene.HERO_BANNER
    HomeLayout.TILES, HomeLayout.GRID -> WinScene.TILE_CARD
    HomeLayout.CLASSIC -> WinScene.VOLT_PANEL
    // (Update 506: Layout Studio's own window — its generic one wrote the name over a flattened cover.)
    HomeLayout.CUSTOM -> WinScene.STUDIO_PANEL
    else -> null
}

/** What's behind a scene's window: the layout stays in view, dimmed in its own colours. */
internal fun sceneVeil(scene: WinScene): Brush = when (scene) {
    WinScene.BOX_SHEET -> Brush.verticalGradient(listOf(Color(0xFF1E1810).copy(alpha = 0.38f), Color(0xFF0E0A06).copy(alpha = 0.72f)))
    WinScene.FILE_CARD -> Brush.verticalGradient(listOf(Color(0xFF0B1240).copy(alpha = 0.62f), Color(0xFF050826).copy(alpha = 0.84f)))
    WinScene.STORAGE_RECORD -> Brush.verticalGradient(listOf(Color(0xFF0C1C30).copy(alpha = 0.58f), Color(0xFF060E18).copy(alpha = 0.82f)))
    WinScene.CASE_MEDIA -> Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.88f)))
    WinScene.ORBIT_TERMINAL -> Brush.verticalGradient(listOf(Color(0xFF02040C).copy(alpha = 0.55f), Color(0xFF02040C).copy(alpha = 0.85f)))
    WinScene.AIRY_PANEL -> Brush.verticalGradient(listOf(Color(0xFFF4F8FC).copy(alpha = 0.55f), Color(0xFFDCE7F3).copy(alpha = 0.8f)))
    WinScene.NIGHT_STATUS -> Brush.linearGradient(listOf(Color(0xFF02060F).copy(alpha = 0.8f), Color(0xFF0A1C3F).copy(alpha = 0.75f)))
    WinScene.BLADE_GUIDE -> Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.82f), Color.Black.copy(alpha = 0.55f)))
    WinScene.BUBBLE_CARD -> Brush.verticalGradient(listOf(Color(0xFF0D2A4A).copy(alpha = 0.6f), Color(0xFF05101E).copy(alpha = 0.8f)))
    WinScene.PANEL_DETAIL -> Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.7f)))
    WinScene.MAG_PAGE -> Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.78f)))
    WinScene.TERM_RUN -> termColors(com.shelf.launcher.data.Features.tmTheme.value).let { t -> Brush.verticalGradient(listOf(t.bg.copy(alpha = 0.6f), t.bg.copy(alpha = 0.75f))) }
    WinScene.SHOP_TAG -> Brush.verticalGradient(listOf(Color(0xFF120A1F).copy(alpha = 0.7f), Color.Black.copy(alpha = 0.85f)))
    WinScene.ARCADE_MENU -> Brush.radialGradient(listOf(Color(0xFF0A0420).copy(alpha = 0.55f), Color.Black.copy(alpha = 0.9f)))
    WinScene.CINEMA_PREMIERE -> Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Black.copy(alpha = 0.86f)))
    WinScene.ARCTIC_DRAWER -> Brush.horizontalGradient(listOf(Color(0xFF0D141D).copy(alpha = 0.35f), Color(0xFF0D141D).copy(alpha = 0.8f)))
    // (Update 421: the veil in the grid's palette — Original Pale / OLED Black as before.)
    WinScene.WII_PREVIEW -> chanColors().let { pal ->
        if (chanLightLook()) Brush.verticalGradient(listOf(pal.bgTop.copy(alpha = 0.82f), pal.bgBottom.copy(alpha = 0.9f)))
        else if (pal.palette == com.shelf.launcher.data.Features.ChanPalette.CUSTOM && pal.darkBase != null)
            Brush.verticalGradient(listOf(pal.bgTop.copy(alpha = 0.86f), pal.bgBottom.copy(alpha = 0.92f)))
        else Brush.verticalGradient(listOf(Color(0xFF0E1116).copy(alpha = 0.86f), Color(0xFF0A0D12).copy(alpha = 0.92f)))
    }
    WinScene.RECORD_SLEEVE -> Brush.verticalGradient(listOf(Color(0xFF1A120B).copy(alpha = 0.55f), Color(0xFF0B0805).copy(alpha = 0.8f)))
    WinScene.CARD_BACK -> Brush.radialGradient(listOf(Color(0xFF1A1610).copy(alpha = 0.55f), Color.Black.copy(alpha = 0.82f)))
    // (Update 506: the player stays in view — light over the deck and its disc, the dim deepening over the info side the
    // panel opens on.)
    WinScene.DISC_PANEL -> Brush.horizontalGradient(0f to Color(0xFF05070B).copy(alpha = 0.16f), 0.42f to Color(0xFF05070B).copy(alpha = 0.22f), 0.6f to Color(0xFF05070B).copy(alpha = 0.66f), 1f to Color(0xFF05070B).copy(alpha = 0.8f))
    WinScene.CORE_PANEL -> Brush.verticalGradient(listOf(Color(0xFF01080A).copy(alpha = 0.7f), Color(0xFF000403).copy(alpha = 0.9f)))
    WinScene.CUBE_FACE -> Brush.radialGradient(listOf(Color(0xFF14163A).copy(alpha = 0.55f), Color(0xFF05060F).copy(alpha = 0.85f)))
    WinScene.POCKET_LCD -> Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.8f)))
    WinScene.INDEX_CARD -> Brush.verticalGradient(listOf(Color(0xFF15100A).copy(alpha = 0.45f), Color(0xFF0A0805).copy(alpha = 0.78f)))
    WinScene.TWIN_HINGE -> Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Black.copy(alpha = 0.85f)))
    WinScene.GALLERY_PLACARD -> Brush.verticalGradient(listOf(Color(0xFF1C1B19).copy(alpha = 0.6f), Color(0xFF0D0C0B).copy(alpha = 0.86f)))
    WinScene.CROSS_OPTIONS, WinScene.SIDE_DRAWER -> Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.7f)))
    WinScene.LIVE_PANEL, WinScene.DASH_CARD, WinScene.HUB_PLATE, WinScene.DISCOVER_CARD, WinScene.LIBRARY_PAGE, WinScene.TIMELINE_ENTRY, WinScene.VOLT_PANEL, WinScene.STUDIO_PANEL ->
        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.8f)))
    WinScene.SPOT_POSTER, WinScene.HERO_BANNER -> Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Black.copy(alpha = 0.9f)))
    WinScene.DOCK_FLYOUT -> Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.3f), Color.Black.copy(alpha = 0.7f)))
    WinScene.CHIP_SHEET -> Brush.verticalGradient(listOf(Color(0xFF02120B).copy(alpha = 0.6f), Color(0xFF010805).copy(alpha = 0.86f)))
    WinScene.TILE_CARD -> Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.7f)))
    WinScene.CAMERA_MENU -> com.shelf.launcher.data.Features.pcPalette.value.shades.let { s -> Brush.verticalGradient(listOf(Color(s[0]).copy(alpha = 0.8f), Color(s[0]).copy(alpha = 0.92f))) }
    // (The browser's own grey screen — the info screen takes it over, as the console's does; on your theme: dimmed.)
    WinScene.MEMCARD_INFO -> when (com.shelf.launcher.data.Features.mcBack.value) {
        com.shelf.launcher.data.Features.McBack.THEME -> Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Black.copy(alpha = 0.8f)))
        com.shelf.launcher.data.Features.McBack.BLUE -> Brush.radialGradient(listOf(Color(0xFFC6CFDC), Color(0xFF8B96A8), Color(0xFF3C4556)))
        else -> Brush.radialGradient(listOf(Color(0xFFCBCCCF), Color(0xFF909297), Color(0xFF444649)))
    }
}

/** Update 330: one action of the game window (a button, a tool, a chip). */
internal class WinAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    /** Its focus requester (Play's is the one the window opens on; Performance's is where the D-pad comes back to). */
    val focus: FocusRequester? = null,
    /** Play / Continue. */
    val primary: Boolean = false,
    /** Closes the window / back to its first page. */
    val back: Boolean = false,
    /** Needs you (a save sync problem). */
    val urgent: Boolean = false,
    /**
     * Popup motion work: drawn only - a page on its way out, the game blade under a page blade. Not focusable, not clickable:
     * the D-pad can't wander onto something covered or leaving.
     */
    val inert: Boolean = false
)

/** Update 330: everything a layout's own window gets from GameWindow (see SceneBody there). */
internal class WinSceneCtx(
    val g: Game,
    val state: LauncherUiState,
    val cover: String?,
    val wide: String?,
    val backdrop: String?,
    val logo: String?,
    val name: String,
    val console: String,
    val playtime: Long?,
    val lastPlayed: Long?,
    val year: String?,
    val developer: String?,
    val genres: String?,
    val summary: String?,
    val earned: Int,
    val total: Int,
    val page: WinPage,
    val setPage: (WinPage) -> Unit,
    val playFocus: FocusRequester,
    val listFocus: FocusRequester,
    /** Play / Continue first, then the page's own (Details and Back on the first page). */
    val buttons: List<WinAction>,
    /** Options, achievements, save sync, manual, inspect. */
    val tools: List<WinAction>,
    /** The save sync and performance lines. */
    val chips: List<WinAction>,
    val close: () -> Unit,
    /** The game's piece is out beside the window (GameWindow's InspectLayout). */
    val inspecting: Boolean,
    /** The window's colour for what's lit (the game's / the theme's when picked). */
    val accent: Color,
    val maxW: Dp,
    val maxH: Dp,
    /** How far the window is open, 0…1 (read while drawing). */
    val reveal: () -> Float,
    /** The details / options / achievements / performance page, in the layout's colours. */
    val pageBody: @Composable (Modifier) -> Unit,
    /** Update 347: the window is on its way out (a scene can leave differently from how it came — Bubble's peel). */
    val closing: () -> Boolean = { false },
    /** Popup motion work: any page's body; [live] false for a page on its way out (it gets no list focus). */
    val pageFor: @Composable (WinPage, Modifier, Boolean) -> Unit = { _, m, _ -> pageBody(m) },
    /** Popup motion work: open a page as its button does (B comes back onto that button). */
    val open: (WinPage) -> Unit = setPage,
    /** Popup motion work: the pages this window has as pages (L1 / R1 walk only these; the others open a list or a menu). */
    val pages: Set<WinPage> = setOf(WinPage.MAIN, WinPage.DETAILS)
) {
    val played: String get() = stylePlaytime(playtime, lastPlayed != null)
    val last: String? get() = lastPlayed?.let { lastPlayedLabel(it) }
}

@Composable
internal fun DrawWinScene(scene: WinScene, ctx: WinSceneCtx) {
    when (scene) {
        WinScene.BOX_SHEET -> BoxInsertSheet(ctx)
        WinScene.FILE_CARD -> FileCardBack(ctx)
        WinScene.STORAGE_RECORD -> StorageRecord(ctx)
        WinScene.MEMCARD_INFO -> MemCardWindow(ctx)
        WinScene.CASE_MEDIA -> CaseFlowMedia(ctx)
        WinScene.ORBIT_TERMINAL -> OrbitTerminal(ctx)
        WinScene.AIRY_PANEL -> AiryPanel(ctx)
        WinScene.NIGHT_STATUS -> NightStatus(ctx)
        WinScene.BLADE_GUIDE -> BladeGuide(ctx)
        WinScene.BUBBLE_CARD -> BubbleCard(ctx)
        WinScene.CAMERA_MENU -> CameraMenu(ctx)
        WinScene.PANEL_DETAIL -> PanelDetail(ctx)
        WinScene.MAG_PAGE -> MagazinePage(ctx)
        WinScene.SHOP_TAG -> RentalTag(ctx)
        WinScene.TERM_RUN -> TerminalDialog(ctx)
        WinScene.ARCADE_MENU -> ArcadeMenu(ctx)
        WinScene.CINEMA_PREMIERE -> CinemaPremiere(ctx)
        WinScene.ARCTIC_DRAWER -> ArcticDrawer(ctx)
        WinScene.WII_PREVIEW -> WiiChannelPreview(ctx)
        WinScene.RECORD_SLEEVE -> RecordSleeve(ctx)
        WinScene.CARD_BACK -> CardTurnOver(ctx)
        WinScene.DISC_PANEL -> DiscPlayerPanel(ctx)
        WinScene.CORE_PANEL -> CorePanel(ctx)
        WinScene.CUBE_FACE -> CubeTurnFace(ctx)
        WinScene.POCKET_LCD -> PocketPopup(ctx)
        WinScene.INDEX_CARD -> ShelfFamilyPopup(ctx)
        WinScene.TWIN_HINGE -> TwinHinge(ctx)
        WinScene.GALLERY_PLACARD -> GalleryPlacard(ctx)
        WinScene.CROSS_OPTIONS -> CrossOptions(ctx)
        WinScene.LIVE_PANEL -> LiveTilesPanel(ctx)
        WinScene.DASH_CARD -> DashCard(ctx)
        WinScene.SIDE_DRAWER -> SideDrawer(ctx)
        WinScene.SPOT_POSTER -> SpotPoster(ctx)
        WinScene.DOCK_FLYOUT -> DockFlyout(ctx)
        WinScene.HUB_PLATE -> HubNameplate(ctx)
        WinScene.DISCOVER_CARD -> DiscoverCard(ctx)
        WinScene.CHIP_SHEET -> ChipSheet(ctx)
        WinScene.LIBRARY_PAGE -> LibraryPage(ctx)
        WinScene.TIMELINE_ENTRY -> TimelineHang(ctx)
        WinScene.HERO_BANNER -> HeroBanner(ctx)
        WinScene.TILE_CARD -> TileLiftCard(ctx)
        WinScene.VOLT_PANEL -> VoltPanel(ctx)
        WinScene.STUDIO_PANEL -> StudioPanel(ctx)
    }
}

/**
 * A control of a scene: focusable (its requester attached), clickable, with the window's move / select sounds; [content]
 * draws it lit or not.
 */
@Composable
internal fun SceneControl(a: WinAction, modifier: Modifier, minTouch: Dp = 0.dp, content: @Composable BoxScope.(Boolean) -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val sounds = LocalUiSounds.current
    LaunchedEffect(focused) { if (focused) sounds?.play(UiSound.MOVE) }
    Box(
        modifier
            .then(if (a.focus != null) Modifier.focusRequester(a.focus) else Modifier)
            // (Update 470, D212: [minTouch] makes the touch box taller than what is drawn — the drawn row stays its size,
            // centred in it, and only the room between rows grows. Rows touching edge to edge can't share room, so the gap is it.)
            .then(if (minTouch > 0.dp) Modifier.heightIn(min = minTouch) else Modifier)
            .clickable(enabled = !a.inert, interactionSource = source, indication = null) { sounds?.play(if (a.back) UiSound.BACK else UiSound.SELECT); a.onClick() },
        contentAlignment = Alignment.Center
    ) { content(focused) }
}

/**
 * Update 428 (D107): a scene's text column that only scrolled by touch (Cinema's story, Arctic's plot) becomes a D-pad stop:
 * ▲ ▼ scroll it while it has focus, and at its top / bottom the press goes on to the next control. A thin ring shows it is
 * the one the D-pad is on. Put it before sceneFadeEdges / verticalScroll(state).
 */
internal fun Modifier.sceneReadable(state: androidx.compose.foundation.ScrollState, ring: Color, onlyWhenScrolls: Boolean = false,
    exitDown: androidx.compose.ui.focus.FocusRequester? = null): Modifier = this.composed {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val step = if (onlyWhenScrolls) 0.9f else 0.6f
    var on by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.ui.Modifier.onFocusChanged { on = it.isFocused }
        .onPreviewKeyEvent { e ->
            if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (e.key) {
                // (Update 471: at the end, [exitDown] — the window's Play — rather than whatever key is nearest below.)
                androidx.compose.ui.input.key.Key.DirectionDown -> if (state.value < state.maxValue) { scope.launch { state.animateScrollBy(state.viewportSize * step) }; true }
                    else if (exitDown != null) runCatching { exitDown.requestFocus() }.isSuccess else false
                androidx.compose.ui.input.key.Key.DirectionUp -> if (state.value > 0) { scope.launch { state.animateScrollBy(-state.viewportSize * step) }; true } else false
                else -> false
            }
        }
        // (Update 471: [onlyWhenScrolls] — a box whose text fits is no stop for the D-pad at all, and a short one moves a
        // whole view per press, so the way back to Play is one or two presses, not a page-by-page walk.)
        .then(if (!onlyWhenScrolls || state.maxValue > 0) androidx.compose.ui.Modifier.focusable() else androidx.compose.ui.Modifier)
        .border(if (on) 2.dp else 0.dp, if (on) ring else Color.Transparent, RoundedCornerShape(6.dp))
        .padding(4.dp) // (the same room with or without the ring: the text never jumps when it is reached)
}

/**
 * Update 346: a scene's scrolling list says when there's more — its edge fades out where rows run on (the brief: "no
 * clipped text"; a row cut in half by the window's edge read as a bug). Put it before verticalScroll(state).
 */
internal fun Modifier.sceneFadeEdges(state: androidx.compose.foundation.ScrollState, edge: Dp = 26.dp): Modifier =
    this.graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val e = edge.toPx().coerceAtMost(size.height / 3f)
            if (state.canScrollBackward) drawRect(
                Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = 0f, endY = e),
                size = Size(size.width, e), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
            )
            if (state.canScrollForward) drawRect(
                Brush.verticalGradient(0f to Color.Black, 1f to Color.Transparent, startY = size.height - e, endY = size.height),
                topLeft = Offset(0f, size.height - e), size = Size(size.width, e), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
            )
        }

private fun pageName(p: WinPage) = when (p) {
    WinPage.MAIN -> "Info"; WinPage.DETAILS -> "Details"; WinPage.OPTIONS -> "Options"; WinPage.TROPHIES -> "Achievements"; WinPage.PERF -> "Performance"
}

private fun mixS(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)

// ---------------------------------------------------------------------------------------------------------------------
// Cardboard box: the insert sheet
// ---------------------------------------------------------------------------------------------------------------------

private val SheetPaper = Color(0xFFF5EEDC)
private val SheetInk = Color(0xFF2B2117)
private val SheetMuted = Color(0xFF6E5C43)
private val SheetRule = Color(0xFFB9A784)
private val SheetRed = Color(0xFFB8322A)

/**
 * The Cardboard box's window: the case lifted out of the box stands on the left (the window's piece — turn it, open it),
 * and beside it the sheet that was tucked in with it: a typed inventory slip on cream paper, taped at its corner — the
 * game's name, its console and facts on dotted lines, a few lines of its story, and its actions as rubber-stamped boxes
 * (the one you're on stamped in red). It slides out from behind the case, settling a little crooked.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun BoxInsertSheet(ctx: WinSceneCtx) {
    val w = minOf(ctx.maxW, 560.dp)
    val mono = FontFamily.Monospace
    val accent = if (ctx.accent.luminance() > 0.6f) SheetRed else ctx.accent
    val scroll = rememberScrollState()
    Box(
        Modifier.widthIn(max = w).heightIn(max = ctx.maxH)
            .graphicsLayer {
                val k = ctx.reveal().coerceIn(0f, 1f)
                // Out from behind the case (its left), turning to lie a little crooked.
                rotationZ = -1.2f - 5f * (1f - k)
                translationX = -(1f - k) * size.width * 0.25f
            }
    ) {
        // The paper, with a soft shadow and its ruled margin.
        Column(
            Modifier
                .drawBehind {
                    drawRect(Color.Black.copy(alpha = 0.28f), Offset(6.dp.toPx(), 8.dp.toPx()), size)
                    drawRect(SheetPaper)
                    // Faint paper fibres.
                    var y = 0f
                    while (y < size.height) { drawLine(SheetRule.copy(alpha = 0.12f), Offset(0f, y), Offset(size.width, y), 1f); y += 22.dp.toPx() }
                    drawLine(SheetRed.copy(alpha = 0.35f), Offset(30.dp.toPx(), 0f), Offset(30.dp.toPx(), size.height), 1.5f)
                }
                .padding(start = 40.dp, end = 18.dp, top = 20.dp, bottom = 14.dp)
        ) {
            // (Update 469, D222: the whole sheet was one scroll — on the KONKR Play / Contents / Back sat under the story and the
            // tools and the Saves line started below the screen. Now the facts and the story scroll in the paper's middle; the
            // header, the title and the stamped actions stay put.)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("INSERT · " + (if (ctx.page == WinPage.MAIN) "CONTENTS" else pageName(ctx.page).uppercase()), color = SheetMuted, fontSize = 11.sp,
                    fontFamily = mono, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(ctx.console.uppercase(), color = SheetMuted, fontSize = 11.sp, fontFamily = mono, maxLines = 1)
            }
            Spacer(Modifier.height(4.dp))
            // (Update 424, rule 11: the sheet scrolls, so its title, values and description are never cut with "…" — seen on the Thor
            // with "Role-playing (RPG), Turn-based strat…" and the description's last words.)
            // (Update 478, D271: shrinks to the readable minimum, then slides — a long name was cut at 3 lines.)
            WinFullText(ctx.name.uppercase(), color = SheetInk, fontSize = 20.sp, lineHeight = 23.sp, fontWeight = FontWeight.Black, fontFamily = mono, maxLines = 3)
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(2.dp).background(SheetInk))
            Spacer(Modifier.height(8.dp))
            Column(Modifier.weight(1f, fill = false).sceneReadable(scroll, accent, onlyWhenScrolls = true, exitDown = ctx.playFocus).sceneFadeEdges(scroll).verticalScroll(scroll)) {
            if (ctx.page == WinPage.MAIN) {
                @Composable
                fun line(label: String, value: String?) {
                    if (value.isNullOrBlank()) return
                    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.Bottom) {
                        Text(label.uppercase(), color = SheetMuted, fontSize = 12.sp, fontFamily = mono, maxLines = 1)
                        // The dotted leader.
                        Canvas(Modifier.weight(1f).height(10.dp).padding(horizontal = 4.dp)) {
                            var x = 0f
                            while (x < size.width) { drawCircle(SheetRule, 1.1f, Offset(x, size.height - 2f)); x += 5f }
                        }
                        Text(value, color = SheetInk, fontSize = 13.sp, fontFamily = mono, fontWeight = FontWeight.Bold, maxLines = 3,
                            textAlign = TextAlign.End, modifier = Modifier.widthIn(max = w * 0.55f))
                    }
                }
                // (Update 471, owner review: the whole story under the facts left it a sliver of the sheet to be read in. The
                // sheet shows the story's opening sentences — on a short sheet first, above the facts, so it is what the window
                // opens on — and CONTENTS has all of it, said on the sheet, never cut silently. The facts follow in full.)
                val shortSheet = ctx.maxH < 480.dp
                @Composable
                fun story(gapTop: Boolean) {
                    ctx.summary?.let {
                        val (lead, more) = storyLead(it, if (shortSheet) 150 else 220)
                        if (gapTop) Spacer(Modifier.height(8.dp))
                        Text(lead, color = SheetInk.copy(alpha = 0.85f), fontSize = 12.sp, lineHeight = 16.sp, fontFamily = mono)
                        if (more) Text("THE WHOLE STORY: CONTENTS", color = SheetMuted, fontSize = 10.sp, fontFamily = mono, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp))
                        if (!gapTop) Spacer(Modifier.height(8.dp))
                    }
                }
                if (shortSheet) story(false)
                line("Released", ctx.year)
                line("Made by", ctx.developer)
                line("Kind", ctx.genres)
                line("Played", ctx.played)
                line("Last played", ctx.last)
                if (ctx.total > 0) line("Achievements", "${ctx.earned} / ${ctx.total}")
                if (!shortSheet) story(true)
            } else {
                // The other pages, on the same paper.
                ctx.pageBody(Modifier.fillMaxWidth().height((ctx.maxH * 0.5f).coerceIn(180.dp, 360.dp)).clip(RoundedCornerShape(2.dp)))
            }
            }
            Spacer(Modifier.height(12.dp))
            // The actions, stamped.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                ctx.buttons.forEach { a ->
                    SceneControl(a, Modifier) { on ->
                        val tilt by animateFloatAsState(if (on) -2.5f else 0f, spring(dampingRatio = 0.5f), label = "stampTilt")
                        Box(
                            Modifier.graphicsLayer { rotationZ = tilt; val s = if (on) 1.06f else 1f; scaleX = s; scaleY = s }
                                .heightIn(min = if (a.primary) 44.dp else 38.dp)
                                .background(if (on) accent else Color.Transparent)
                                .border(if (on) 3.dp else 2.dp, if (on) accent else SheetInk)
                                .padding(horizontal = if (a.primary) 18.dp else 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (a.primary) { Icon(a.icon, null, tint = if (on) SheetPaper else SheetInk, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
                                Text(a.label.uppercase(), color = if (on) SheetPaper else SheetInk, fontSize = if (a.primary) 16.sp else 13.sp,
                                    fontFamily = mono, fontWeight = FontWeight.Black, maxLines = 1)
                            }
                        }
                    }
                }
                // The small tools: round stickers, after the stamps.
                if (ctx.page == WinPage.MAIN) ctx.tools.forEach { a ->
                    SceneControl(a, Modifier.size(38.dp)) { on ->
                        Box(
                            Modifier.fillMaxSize().graphicsLayer { val s = if (on) 1.12f else 1f; scaleX = s; scaleY = s }
                                .clip(CircleShape).background(if (on) accent else Color(0xFFEADFC4))
                                .border(2.dp, if (on) accent else SheetInk.copy(alpha = 0.7f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) { Icon(a.icon, a.label, tint = if (on) SheetPaper else SheetInk, modifier = Modifier.size(19.dp)) }
                    }
                }
            }
            if (ctx.chips.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                // (Update 470: a gap between the status lines — the sheet lies tilted, and two lines touching edge to edge
                // crossed each other's box by up to 19 px on the Odin at font 1.3; 12 dp clears it.)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ctx.chips.forEach { a ->
                        SceneControl(a, Modifier) { on ->
                            Row(Modifier.background(if (on) accent.copy(alpha = 0.18f) else Color.Transparent).padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(a.icon, null, tint = if (a.urgent) SheetRed else SheetMuted, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(a.label, color = if (a.urgent) SheetRed else SheetMuted, fontSize = 11.sp, fontFamily = mono, maxLines = 1,
                                    textDecoration = if (on) androidx.compose.ui.text.style.TextDecoration.Underline else null)
                            }
                        }
                    }
                }
            }
        }
        // The tape holding it to the case's side.
        Box(
            Modifier.align(Alignment.TopStart).offset((-14).dp, (-8).dp).size(64.dp, 22.dp).graphicsLayer { rotationZ = -24f }
                .background(Color(0xFFE6D9B3).copy(alpha = 0.78f))
        )
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// File select: the file turned over
// ---------------------------------------------------------------------------------------------------------------------

private val FsOutline = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(2f, 2.5f), 2f))

/**
 * File select's window: the file block you opened, turned over and grown big — its back in the block's own colour,
 * chunky and bevelled, with its thickness under it. Its console on a tab, the stars it holds, the cover in a framed
 * window, the name in outlined capitals, the play time and a few lines of its story on an inset panel, and round,
 * fat buttons along the bottom (the lit one yellow and bouncing). Its other pages show on the inset panel.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun FileCardBack(ctx: WinSceneCtx) {
    val base = Color(FileSelectNav.openColor)
    val ink = if (base.luminance() > 0.62f) Color(0xFF1B1B1B) else Color.White
    val w = minOf(ctx.maxW * 0.94f, 820.dp)
    val h = minOf(ctx.maxH * 0.94f, 500.dp)
    val shape = RoundedCornerShape(28.dp)
    val yellow = Color(0xFFFFE04A)
    Box(Modifier.size(w, h)) {
        // Its thickness.
        Box(Modifier.fillMaxSize().offset(0.dp, 9.dp).clip(shape).background(mixS(base, Color.Black, 0.5f)))
        Column(
            Modifier.fillMaxSize().clip(shape)
                .background(Brush.verticalGradient(listOf(mixS(base, Color.White, 0.3f), base, mixS(base, Color.Black, 0.2f))))
                .border(5.dp, mixS(base, Color.White, 0.5f), shape)
                .padding(16.dp)
        ) {
            // The top: its console on a tab, and its stars.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(mixS(base, Color.Black, 0.35f))
                        .border(3.dp, mixS(base, Color.White, 0.45f), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 5.dp)
                ) { Text(ctx.console.uppercase(), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, style = FsOutline) }
                Spacer(Modifier.weight(1f))
                if (ctx.page != WinPage.MAIN) Text(pageName(ctx.page).uppercase(), color = ink, fontSize = 16.sp, fontWeight = FontWeight.Black, style = if (ink == Color.White) FsOutline else TextStyle())
                Spacer(Modifier.width(14.dp))
                Canvas(Modifier.size(26.dp)) { fsBigStar(yellow) }
                Text(" × ${ctx.earned}" + (if (ctx.total > 0) " / ${ctx.total}" else ""), color = ink, fontSize = 20.sp, fontWeight = FontWeight.Black,
                    style = if (ink == Color.White) FsOutline else TextStyle())
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().weight(1f)) {
                if (ctx.page == WinPage.MAIN) {
                    // The cover in its window (not while the game's piece is out beside the file — that's its picture).
                    if (!ctx.inspecting) Box(
                        Modifier.fillMaxHeight().aspectRatio(0.74f).clip(RoundedCornerShape(14.dp)).background(mixS(base, Color.Black, 0.45f))
                            .border(4.dp, mixS(base, Color.White, 0.6f), RoundedCornerShape(14.dp)).padding(4.dp).clip(RoundedCornerShape(10.dp))
                    ) {
                        if (ctx.cover != null) SmartArt(ctx.cover, ctx.name, Modifier.fillMaxSize(), request = rememberCoverRequest(ctx.cover))
                    }
                    if (!ctx.inspecting) Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        WinFullText(ctx.name.uppercase(), color = ink, fontSize = 26.sp, lineHeight = 29.sp, fontWeight = FontWeight.Black, maxLines = 3,
                            style = if (ink == Color.White) FsOutline else androidx.compose.material3.LocalTextStyle.current)
                        Spacer(Modifier.height(4.dp))
                        Text(listOfNotNull(ctx.year, ctx.developer, ctx.genres).joinToString("  ·  "), color = ink.copy(alpha = 0.9f), fontSize = 13.sp, maxLines = 2,
                            fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        // The inset panel: time played and the story.
                        Column(
                            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(14.dp)).background(mixS(base, Color.Black, 0.42f))
                                .border(3.dp, mixS(base, Color.Black, 0.6f), RoundedCornerShape(14.dp)).padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("TIME  ", color = yellow, fontSize = 13.sp, fontWeight = FontWeight.Black)
                                Text(ctx.played, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                ctx.last?.let { Text("   ·   $it", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, maxLines = 1) }
                            }
                            if (ctx.total > 0) {
                                Spacer(Modifier.height(6.dp))
                                Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color.Black.copy(alpha = 0.35f))) {
                                    Box(Modifier.fillMaxHeight().fillMaxWidth((ctx.earned.toFloat() / ctx.total).coerceIn(0f, 1f)).background(yellow))
                                }
                            }
                            ctx.summary?.let {
                                Spacer(Modifier.height(8.dp))
                                Text(it, color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp, lineHeight = 16.sp,
                                    maxLines = if (h > 420.dp) 5 else 3)
                            }
                        }
                    }
                } else {
                    Box(
                        Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(mixS(base, Color.Black, 0.42f))
                            .border(3.dp, mixS(base, Color.Black, 0.6f), RoundedCornerShape(14.dp)).padding(6.dp)
                    ) { ctx.pageBody(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))) }
                }
            }
            Spacer(Modifier.height(12.dp))
            // The fat buttons, then the round tools (on as many lines as they need).
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                ctx.buttons.forEach { a ->
                    SceneControl(a, Modifier) { on ->
                        val pop by animateFloatAsState(if (on) 1.1f else 1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium), label = "fsBtn")
                        Box(
                            Modifier.graphicsLayer { scaleX = pop; scaleY = pop }
                                .height(if (a.primary) 50.dp else 44.dp)
                                .clip(RoundedCornerShape(50)).background(if (on) yellow else mixS(base, Color.Black, 0.38f))
                                .border(3.dp, if (on) Color.White else mixS(base, Color.White, 0.4f), RoundedCornerShape(50))
                                .padding(horizontal = if (a.primary) 22.dp else 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(a.label.uppercase(), color = if (on) Color(0xFF2A2200) else Color.White, fontSize = if (a.primary) 19.sp else 15.sp,
                                fontWeight = FontWeight.Black, maxLines = 1, style = if (on) TextStyle() else FsOutline)
                        }
                    }
                }
                if (ctx.page == WinPage.MAIN) ctx.tools.forEach { a ->
                    SceneControl(a, Modifier.size(42.dp)) { on ->
                        val pop by animateFloatAsState(if (on) 1.15f else 1f, spring(dampingRatio = 0.35f), label = "fsTool")
                        Box(
                            Modifier.fillMaxSize().graphicsLayer { scaleX = pop; scaleY = pop }.clip(CircleShape)
                                .background(if (on) yellow else mixS(base, Color.Black, 0.38f))
                                .border(3.dp, if (on) Color.White else mixS(base, Color.White, 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) { Icon(a.icon, a.label, tint = if (on) Color(0xFF2A2200) else Color.White, modifier = Modifier.size(21.dp)) }
                    }
                }
            }
            if (ctx.chips.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ctx.chips.forEach { a ->
                        SceneControl(a, Modifier) { on ->
                            Row(Modifier.clip(RoundedCornerShape(50)).background(if (on) yellow else Color.Black.copy(alpha = 0.25f)).padding(horizontal = 10.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(a.icon, null, tint = if (on) Color(0xFF2A2200) else if (a.urgent) Color(0xFFFFB74D) else Color.White, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(a.label, color = if (on) Color(0xFF2A2200) else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.fsBigStar(color: Color) {
    val c = Offset(size.width / 2, size.height / 2); val r = size.minDimension / 2
    val p = Path()
    for (i in 0 until 10) {
        val a = Math.PI / 5 * i - Math.PI / 2
        val rr = if (i % 2 == 0) r else r * 0.45f
        val x = c.x + (rr * kotlin.math.cos(a)).toFloat(); val y = c.y + (rr * kotlin.math.sin(a)).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    drawPath(p, color)
    drawPath(p, Color(0xFF7A5A00), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
}

// ---------------------------------------------------------------------------------------------------------------------
// Storage boxes: the stored entry's record
// ---------------------------------------------------------------------------------------------------------------------

internal val RecNavy = Color(0xFF2C4766)
private val RecBand = Color(0xFF4C6A8E)
private val RecPanel = Color(0xFFF7F9FC)
private val RecInk = Color(0xFF1E2A3A)
private val RecMuted = Color(0xFF4C5E76)
private val RecRed = Color(0xFFFF5A4E)

/** A pixel-style frame: a dark edge, a white line inside it, cut corners. */
internal fun Modifier.pixelFrame(fill: Color, cut: Dp = 6.dp): Modifier = this
    .clip(CutCornerShape(cut)).background(RecNavy).padding(3.dp)
    .clip(CutCornerShape(cut - 1.dp)).background(Color.White).padding(2.dp)
    .clip(CutCornerShape(cut - 2.dp)).background(fill)

/**
 * Storage boxes' window: the entry's record, as opening a stored entry. Its sprite stands big on a stand on the left
 * (hopping), with its name plate and its console as a coloured tag; on the right the tabs — Info, Details, Achievements,
 * Options (L1 / R1, or tap) — and the page: on Info the entry's facts in pixel-framed cells and a menu of its actions with
 * a pointer at the one you're on. Pixel-framed, cut-cornered, the box's navy and white.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun StorageRecord(ctx: WinSceneCtx) {
    val w = minOf(ctx.maxW * 0.94f, 900.dp)
    val h = minOf(ctx.maxH * 0.94f, 520.dp)
    val red = if (ctx.accent.luminance() > 0.7f) RecRed else ctx.accent
    val labStorage = PopupLab.active.storage // (523t: approved - everyone)
    val tabs = STORAGE_TABS
    // (Popup motion work: a tab - tapped, or L1 / R1 - opens its page as its button does, so B comes back onto that button;
    // Achievements and Options still open the full list / the game's menu when there is no page for them.)
    fun openTab(t: WinPage) { if (t != ctx.page) ctx.open(t) }
    val anim = rememberAnimationsEnabled()
    Column(
        Modifier.size(w, h).pixelFrame(RecPanel, 10.dp)
            // L1 / R1 turn the tabs.
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val step = when (e.key) { Key.ButtonL1 -> -1; Key.ButtonR1 -> 1; else -> return@onPreviewKeyEvent false }
                openTab(pageStep(tabs.filter { it in ctx.pages }, ctx.page, step))
                true
            }
    ) {
        StorageTabs(ctx, tabs, w, red, anim, ::openTab)
        // Popup motion work (Storage boxes, owner-approved direction): the pages are sheets of the record. The new sheet files in
        // from the side the D-pad went, over the old one, which slides back a little and fades; going back runs it the other way.
        // The record, its band and its tabs stay put: the page never turns into another design.
        androidx.compose.animation.AnimatedContent(
            targetState = ctx.page,
            transitionSpec = {
                val dir = pageDirection(tabs, initialState, targetState)
                if (!anim) androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                else (androidx.compose.animation.slideInHorizontally(tween(NATIVE_PAGE_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it * dir * 3 / 5 } +
                    androidx.compose.animation.fadeIn(tween(90))) togetherWith
                    (androidx.compose.animation.slideOutHorizontally(tween(NATIVE_PAGE_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { -it * dir / 6 } +
                        androidx.compose.animation.fadeOut(tween(NATIVE_PAGE_MS)))
            },
            contentKey = { it },
            modifier = Modifier.fillMaxWidth().weight(1f),
            label = "storagePage"
        ) { p ->
            // (The leaving sheet keeps no focus requester: it can't take the D-pad back while it slides away.)
            val live = p == ctx.page
            Column(Modifier.fillMaxSize().background(RecPanel)) {
                if (p == WinPage.MAIN) StorageRecordMain(ctx, w, red, labStorage, live)
                else StorageRecordPage(ctx, p, red, live)
            }
        }
    }
}

/** Storage boxes' pages in tab order (Info, Details, Achievements, Options). */
internal val STORAGE_TABS = listOf(WinPage.MAIN, WinPage.DETAILS, WinPage.TROPHIES, WinPage.OPTIONS)

/** [this] as drawn on a live sheet, or with no focus requester on a sheet on its way out. */
private fun WinAction.live(live: Boolean): WinAction = if (live) this else inert()

/**
 * Storage's title band: STORED ENTRY and the tabs. The open tab's paper (the record's own) slides from tab to tab under the
 * labels (popup motion work), instead of the tabs swapping colours at once.
 */
@Composable
private fun StorageTabs(ctx: WinSceneCtx, tabs: List<WinPage>, w: Dp, red: Color, anim: Boolean, openTab: (WinPage) -> Unit) {
    val boxes = remember { mutableStateMapOf<WinPage, androidx.compose.ui.layout.LayoutCoordinates>() }
    var band by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    val mx = remember { androidx.compose.animation.core.Animatable(Float.NaN) }
    val mw = remember { androidx.compose.animation.core.Animatable(0f) }
    val mt = remember { androidx.compose.animation.core.Animatable(0f) }
    fun target(p: WinPage): androidx.compose.ui.geometry.Rect? {
        val b = band ?: return null; val c = boxes[p] ?: return null
        if (!b.isAttached || !c.isAttached) return null
        val o = b.localPositionOf(c, Offset.Zero)
        return androidx.compose.ui.geometry.Rect(o, Size(c.size.width.toFloat(), c.size.height.toFloat()))
    }
    val placed = boxes.size == tabs.size && band != null
    // (The paper slides only when animations are on; otherwise - and until the tabs are placed - the open tab draws its own
    // paper in the same frame its page changes. The first placement is not a change: nothing is drawn from the marker before it.)
    val slide = anim && !mx.value.isNaN()
    LaunchedEffect(ctx.page, placed, anim) { // qa-ok: SNAP-ANIMATE (snaps only when the marker is not drawn: first placement, animations off)
        val r = target(ctx.page) ?: return@LaunchedEffect
        if (!anim || mx.value.isNaN()) { mx.snapTo(r.left); mw.snapTo(r.width); mt.snapTo(r.top); return@LaunchedEffect } // qa-ok: SNAP-ANIMATE (first placement / animations off: the marker isn't drawn then, see [slide])
        val spec = tween<Float>(NATIVE_PAGE_MS - 40, easing = androidx.compose.animation.core.FastOutSlowInEasing)
        kotlinx.coroutines.coroutineScope {
            launch { mx.animateTo(r.left, spec) }; launch { mw.animateTo(r.width, spec) }; launch { mt.animateTo(r.top, spec) }
        }
    }
    // How much of tab [t] the sliding paper covers (0..1): its label turns dark once the paper is under most of it.
    fun covered(t: WinPage): Float {
        val r = target(t) ?: return 0f
        val l = maxOf(r.left, mx.value); val rr = minOf(r.right, mx.value + mw.value)
        return if (r.width <= 0f) 0f else ((rr - l) / r.width).coerceIn(0f, 1f)
    }
    val cut = with(androidx.compose.ui.platform.LocalDensity.current) { 6.dp.toPx() }
    Row(
        Modifier.fillMaxWidth().background(RecBand)
            .onGloballyPositioned { band = it }
            .drawBehind {
                if (!slide) return@drawBehind
                // The tabs' navy plates, then the open tab's paper over them (cut top corners, down into the record), then the
                // labels on top (drawn by the tabs). One layer, so the paper is never hidden behind a tab while it slides.
                tabs.forEach { t ->
                    val r = target(t) ?: return@forEach
                    val plate = androidx.compose.ui.graphics.Path().apply {
                        moveTo(r.left, r.bottom); lineTo(r.left, r.top + cut); lineTo(r.left + cut, r.top); lineTo(r.right - cut, r.top); lineTo(r.right, r.top + cut); lineTo(r.right, r.bottom); close()
                    }
                    drawPath(plate, RecNavy)
                }
                val x = mx.value; val wd = mw.value; val top = mt.value
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(x, size.height); lineTo(x, top + cut); lineTo(x + cut, top); lineTo(x + wd - cut, top); lineTo(x + wd, top + cut); lineTo(x + wd, size.height); close()
                }
                drawPath(path, RecPanel)
            }
            .padding(start = 12.dp, end = 8.dp, top = 6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        if (w >= 640.dp) Text("STORED ENTRY", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 8.dp))
        Spacer(Modifier.weight(1f))
        // (Update 467, popup gate: in a narrow window — beside the case — the four tabs ran off the band and Options was
        // squeezed to 6 px; there the tabs are tighter and the L1 / R1 marks give way.)
        val narrow = w < 560.dp
        if (!narrow) Text("L1", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 9.dp, end = 4.dp))
        tabs.forEach { t ->
            val sel = ctx.page == t
            val a = WinAction(pageName(t), androidx.compose.material.icons.Icons.Filled.Info, { openTab(t) })
            SceneControl(a, Modifier.padding(horizontal = 2.dp)) { on ->
                // (Sliding: the band draws the plates and the paper; the label is dark where the paper is. Not sliding: as before.)
                val dark = if (slide) covered(t) > 0.5f else sel
                Box(
                    Modifier.onGloballyPositioned { boxes[t] = it }
                        .clip(CutCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(if (on && !sel) red else if (slide) Color.Transparent else if (sel) RecPanel else RecNavy)
                        .padding(horizontal = if (narrow) 5.dp else 12.dp, vertical = 7.dp)
                ) {
                    Text(pageName(t).uppercase(), color = if (dark && !(on && !sel)) RecInk else Color.White, fontSize = if (narrow) 10.sp else 12.sp, fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
                }
            }
        }
        if (!narrow) Text("R1", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 9.dp, start = 4.dp))
    }
}

/** Storage's Info sheet: the sprite on its stand, the facts, the menu with its pointer (as before the popup motion work). */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ColumnScope.StorageRecordMain(ctx: WinSceneCtx, w: Dp, red: Color, labStorage: Boolean, live: Boolean) {
        Row(Modifier.fillMaxWidth().weight(1f).padding(12.dp)) {
            // The sprite on its stand, the name plate, the console tag.
            Column(Modifier.width(w * 0.33f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.fillMaxWidth().weight(1f).pixelFrame(Color(0xFFBFD9F2), 6.dp)
                        .background(Brush.verticalGradient(listOf(Color(0xFFCFE6FA), Color(0xFF8FB8DE)))),
                    contentAlignment = Alignment.Center
                ) {
                    val clock = rememberGcClock(rememberAnimationsEnabled(), 1f, 12)
                    Canvas(Modifier.fillMaxWidth(0.62f).height(16.dp).align(Alignment.BottomCenter).padding(bottom = 6.dp)) {
                        drawOval(Color.Black.copy(alpha = 0.22f), Offset.Zero, Size(size.width, size.height))
                    }
                    Box(
                        Modifier.fillMaxSize(0.66f).graphicsLayer {
                            // The sprite's idle hop: two steps, as sprites do.
                            val step = ((clock.floatValue * 2.2f).toInt() % 2)
                            translationY = -step * 4.dp.toPx() - 6.dp.toPx()
                            val k = ctx.reveal().coerceIn(0f, 1f)
                            val s = 0.6f + 0.4f * k
                            scaleX = s; scaleY = s
                        }
                    ) { StorageSprite(ctx.g, ctx.state, ctx.cover, Modifier.fillMaxSize()) }
                    // (Batch 523 design lab "storage": the console tag sits on the picture's corner, like a label on the box.)
                    if (labStorage) Box(Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(50)).background(red)
                        .padding(horizontal = 10.dp, vertical = 3.dp)) {
                        WinFullText(ctx.console.uppercase(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    }
                }
                Spacer(Modifier.height(if (labStorage) 10.dp else 8.dp))
                Box(Modifier.fillMaxWidth().pixelFrame(Color.White, 5.dp).padding(horizontal = 8.dp, vertical = if (labStorage) 8.dp else 5.dp)) {
                    WinFullText(ctx.name, color = RecInk, fontSize = 15.sp, lineHeight = if (labStorage) 19.sp else 17.sp, fontWeight = FontWeight.Black, maxLines = 3,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                if (!labStorage) {
                Spacer(Modifier.height(6.dp))
                Box(Modifier.clip(RoundedCornerShape(50)).background(red).padding(horizontal = 12.dp, vertical = 3.dp)) {
                    // (Batch 522, popup sweep: "SUPER NINTENDO" - the rest of the name was cut by the one line. Whole: smaller, then sliding.)
                    WinFullText(ctx.console.uppercase(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
                }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                    // The facts in cells.
                    @Composable
                    fun cell(label: String, value: String?) {
                        Row(Modifier.fillMaxWidth().padding(vertical = if (labStorage) 3.dp else 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(if (w < 560.dp) 100.dp else 118.dp).clip(CutCornerShape(4.dp)).background(RecBand).padding(horizontal = 8.dp, vertical = 3.dp)) {
                                Text(label.uppercase(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, maxLines = 1)
                            }
                            Spacer(Modifier.width(8.dp))
                            // (Batch 523: "Never playe" - the value took the room it wanted and was cut by the window's edge;
                            // it has the rest of the line now, whole words, two lines.)
                            WholeWordsText(value ?: "—", color = RecInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        }
                    }
                    // (Update 475, D246: whole rows only — on a short screen the facts that don't fit are left out whole (all of
                    // them are on the Details page), never squeezed: LAST PLAYED / ACHIEVED had no height and PLAYED 10 of its 36 px.)
                    WholeRows(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                        cell("Released", ctx.year)
                        cell("Made by", ctx.developer)
                        cell("Kind", ctx.genres)
                        cell("Played", ctx.played)
                        cell("Last played", ctx.last)
                        if (ctx.total > 0) Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(118.dp).clip(CutCornerShape(4.dp)).background(RecBand).padding(horizontal = 8.dp, vertical = 3.dp)) {
                                Text("ACHIEVED", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("${ctx.earned} / ${ctx.total}  ", color = RecInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            // A segmented bar, as the old screens drew them.
                            Canvas(Modifier.weight(1f).height(10.dp)) {
                                val n = 12; val gap = 2.dp.toPx(); val sw = (size.width - gap * (n - 1)) / n
                                val lit = ((ctx.earned.toFloat() / ctx.total) * n).toInt()
                                for (i in 0 until n) drawRect(if (i < lit) Color(0xFF3FAE5A) else Color(0xFFB9C6D6), Offset(i * (sw + gap), 0f), Size(sw, size.height))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    // The menu with its pointer.
                    // (Batch 523, owner: "the selectable options are visually scattered" - a FlowRow of auto-sized keys left
                    // "SAVE SYN" cut at 8 sp next to a full-size BACK on the same line, Play no bigger than the rest. Now: the
                    // primary action alone on the first line, a size up; the others in an even grid of equal cells (two a
                    // row beside the case, three in a wide window), one text size, whole words over two lines when needed,
                    // Back last. Rows of a plain Column / Row - D-pad order is reading order, ▲ ▼ between rows.)
                    Column(Modifier.fillMaxWidth().pixelFrame(Color.White, 6.dp).padding(horizontal = if (labStorage) 10.dp else 6.dp, vertical = if (labStorage) 8.dp else 6.dp),
                        verticalArrangement = Arrangement.spacedBy(if (labStorage) 6.dp else 2.dp)) {
                        val primary = ctx.buttons.filter { !it.back && it.primary }
                        // (Owner, 13 Nov: the round ••• is the last key of the row - Play, Details, Inspect, •••; what More opens
                        // comes in the grid under it.)
                        val inLine = if (labStorage) ctx.buttons.filter { !it.back && !it.primary } +
                            ctx.tools.filter { it.label == "Inspect" }.takeIf { ctx.tools.any(PopupLab::isMore) }.orEmpty() +
                            ctx.tools.filter(PopupLab::isMore) else emptyList()
                        val rest = ctx.buttons.filter { !it.back && !it.primary && it !in inLine } + ctx.tools.filter { it !in inLine } + ctx.buttons.filter { it.back }
                        // (Batch 523, GT78 lab shots: "ACHIEVEMENT S" - three cells a row in the menu's column (two thirds of the
                        // window) left a long word narrower than its smallest readable size, so it broke inside the word. The cells
                        // are counted from the menu's own width now: two a row unless the column is wide.)
                        // (Batch 523 design lab "storage": Play, Details and ••• on one line - the main choice and its two neighbours.)
                        if (labStorage) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            primary.forEach { a -> StorageKey(a.live(live), red, big = true, modifier = Modifier.weight(1.6f)) }
                            inLine.forEach { a ->
                                if (PopupLab.isMore(a)) SceneControl(a.live(live), Modifier) { on ->
                                    Box(Modifier.size(40.dp).clip(CircleShape).background(if (on) Color(0xFFE6EEF8) else Color.Transparent)
                                        .border(1.5.dp, if (on || a.label == "Less") red else RecInk.copy(alpha = 0.4f), CircleShape), contentAlignment = Alignment.Center) {
                                        Text("•••", color = RecInk, fontSize = 13.sp, fontWeight = FontWeight.Black)
                                    }
                                } else StorageKey(a.live(live), red, big = false, modifier = Modifier.weight(1f))
                            }
                        } else
                            primary.forEach { a -> StorageKey(a.live(live), red, big = true, modifier = Modifier.fillMaxWidth()) }
                        rest.chunked(storageKeysPerRow(w.value * 0.67f)).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                row.forEach { a -> StorageKey(a.live(live), red, big = false, modifier = Modifier.weight(1f)) }
                                repeat(storageKeysPerRow(w.value * 0.67f) - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                if (ctx.chips.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ctx.chips.forEach { a ->
                            SceneControl(a.live(live), Modifier) { on ->
                                // (523x, Flip 2 fp523u: the line was a 40 px target at 360 dpi - under 24 dp. At least 24 dp tall now.)
                                Row(Modifier.heightIn(min = 24.dp).background(if (on) Color(0xFFE6EEF8) else Color.Transparent).padding(horizontal = 4.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Icon(a.icon, null, tint = if (a.urgent) RecRed else RecMuted, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(4.dp))
                                    // (Batch 523 gate, p523all: "Saves: Save sync isn't set up" was cut at one line - whole now.)
                                    WinFullText(a.label, color = if (a.urgent) RecRed else RecMuted, fontSize = 11.sp, maxLines = 2)
                                }
                            }
                        }
                    }
                }
            }
        }
}

/**
 * Storage's Details / Achievements / Options sheet (popup motion work): the page fills the record's sheet under a slim name
 * line - the sprite small, the name in whole words, the console tag - with the page's keys along the bottom, all in the
 * record's pixel frames and navy. It was the shared expanded page in the window's colours: another design.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ColumnScope.StorageRecordPage(ctx: WinSceneCtx, p: WinPage, red: Color, live: Boolean) {
    Column(Modifier.fillMaxWidth().weight(1f).padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).pixelFrame(Color(0xFFCFE6FA), 4.dp), contentAlignment = Alignment.Center) {
                StorageSprite(ctx.g, ctx.state, ctx.cover, Modifier.fillMaxSize(0.8f))
            }
            Spacer(Modifier.width(10.dp))
            WholeWordsText(ctx.name, color = RecInk, fontSize = 15.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.clip(RoundedCornerShape(50)).background(red).padding(horizontal = 10.dp, vertical = 3.dp)) {
                WinFullText(ctx.console.uppercase(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().weight(1f).pixelFrame(Color.White, 6.dp)) { ctx.pageFor(p, Modifier.fillMaxSize(), live) }
        Spacer(Modifier.height(8.dp))
        // (The page's keys: Play first, the page's own, Back last - one row; whole words, never cut.)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ctx.buttons.forEach { a0 ->
                val a = a0.live(live)
                SceneControl(a, Modifier) { on ->
                    Box(Modifier.heightIn(min = 32.dp).pixelFrame(if (on) Color(0xFFE6EEF8) else Color.White, 5.dp).padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.CenterStart) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (on) "▶ " else "  ", color = red, fontSize = 13.sp, fontWeight = FontWeight.Black)
                            Text(a.label.uppercase(), color = if (a.primary) red else RecInk, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
        if (ctx.chips.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ctx.chips.forEach { a ->
                    SceneControl(a.live(live), Modifier) { on ->
                        // (523x, Flip 2 fp523u: the line was a 40 px target at 360 dpi - under 24 dp. At least 24 dp tall now.)
                        Row(Modifier.heightIn(min = 24.dp).background(if (on) Color(0xFFE6EEF8) else Color.Transparent).padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(a.icon, null, tint = if (a.urgent) RecRed else RecMuted, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            // (Batch 523 gate, p523all: "Saves: Save sync isn't set up" was cut at one line - whole now.)
                            WinFullText(a.label, color = if (a.urgent) RecRed else RecMuted, fontSize = 11.sp, maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}

/** Batch 523: Storage's secondary keys per grid row - two beside the case (a window under 560 dp), else three. Pure. */
internal fun storageKeysPerRow(windowDp: Float): Int = if (windowDp < 560f) 2 else 3

/** Batch 523: one key of Storage's menu - the pointer, the label in whole words (one size, two lines at most), an even height. */
@Composable
private fun StorageKey(a: WinAction, red: Color, big: Boolean, modifier: Modifier) {
    SceneControl(a, modifier) { on ->
        Row(
            Modifier.fillMaxWidth().heightIn(min = if (big) 40.dp else 36.dp).background(if (on) Color(0xFFE6EEF8) else Color.Transparent)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (on) "▶" else " ", color = red, fontSize = if (big) 15.sp else 13.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(16.dp))
            WholeWordsText(a.label.uppercase(), color = if (a.primary) red else RecInk, fontSize = if (big) 16.sp else 12.sp,
                fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Case flow: the case opened into its media
// ---------------------------------------------------------------------------------------------------------------------

/**
 * Case flow's window (Toni: "the case opens into a media-based details view"): the case comes forward on the left (the
 * window's piece), and beside it what's inside, on the layout's dark glass stage: the game's video (else its backdrop)
 * big, a strip of its screenshots under it — ◀ ▶ / a tap shows one big — its name and facts, and glossy round-ended
 * buttons reflected on the stage floor, as the cases are. Its other pages show in the media's place.
 */

/** Update 506: Case Flow's picture room for a game with no picture, no screenshots, no clip — a short band, never under 64 dp. */
internal fun caseFlowEmptyBand(mediaH: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp = minOf(mediaH, 92.dp).coerceAtLeast(64.dp)

/**
 * Update 506: what Case Flow's picture room shows with no art — the game's cover small and whole when there is one, else a drawn
 * case outline with the console's name: a purposeful empty state in the window's glass, not a black band.
 */
@Composable
private fun CaseFlowNoArt(ctx: WinSceneCtx, modifier: Modifier) {
    Row(modifier.background(Brush.horizontalGradient(listOf(ctx.accent.copy(alpha = 0.22f), Color(0xFF15171C), Color(0xFF0E0F13)))).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.fillMaxHeight().aspectRatio(0.7f).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.06f))
            .border(1.5.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(4.dp))) {
            if (ctx.cover != null) SmartArt(ctx.cover, null, Modifier.fillMaxSize(), well = Color(0xFF15171C))
            // The case's spine line, as the cases on the shelf have.
            else Box(Modifier.align(Alignment.CenterStart).padding(start = 5.dp).width(1.5.dp).fillMaxHeight(0.8f).background(Color.White.copy(alpha = 0.3f)))
        }
        Spacer(Modifier.width(12.dp))
        Text(ctx.console.uppercase(), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, maxLines = 2)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun CaseFlowMedia(ctx: WinSceneCtx) {
    val shots = ctx.state.screenshots[ctx.g.stableKey].orEmpty().take(6)
    var shown by androidx.compose.runtime.remember(ctx.g.stableKey) { androidx.compose.runtime.mutableIntStateOf(-1) }
    val accent = ctx.accent
    val onAccent = if (accent.luminance() > 0.5f) Color(0xFF111111) else Color.White
    val w = minOf(ctx.maxW, 760.dp)
    Column(
        Modifier.widthIn(max = w).heightIn(max = ctx.maxH)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF23262E).copy(alpha = 0.95f), Color(0xFF0E0F13).copy(alpha = 0.97f))))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        // (Update 467, owner's Pokémon Stadium 2 screenshot: the whole window scrolled, so on the KONKR (960×640) with a
        // three-line story the Play / Details / Back / More row sat below the window's bottom edge, cut in half, reachable
        // only by scrolling. Now only the picture, screenshots, facts and story scroll — in the room the action row leaves;
        // the actions are measured first and always shown whole. QA gate: tools/qa/tools/geomcheck.py --clip, R26.)
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
        // (Update 467: the picture gives way first — it leaves ~270 dp for the screenshots, facts, story and the action row —
        // so on a short screen the story still shows rather than scrolling away; on a tall one it's as before.)
        val mediaH = minOf(ctx.maxH * 0.42f, ctx.maxH - 270.dp).coerceIn(110.dp, 300.dp)
        // (Update 506, owner review of the 505 visual report: the name sat on the picture and fought with the words printed in
        // it — "Mario Kart 64" over "synthetic fixture" on the GT78. The picture now carries no writing; the name has its own
        // band under it. A game with nothing to show there gets a short case-shaped placeholder band, not a black void.)
        val artHere = shots.isNotEmpty() || (ctx.backdrop ?: ctx.wide) != null || ctx.state.videoUrls[ctx.g.stableKey] != null
        val mediaBox = if (ctx.page != WinPage.MAIN || artHere) mediaH else caseFlowEmptyBand(mediaH)
        Box(
            Modifier.fillMaxWidth().height(mediaBox)
                // (Update 505, batch 505: the case opens into the media — the picture slides out from the case's side and comes up
                // as the window does, then the pills rise under it. Read while drawing (no recomposition per frame); with reduced
                // motion the window's reveal is already whole, so nothing moves.)
                .graphicsLayer {
                    val e = sceneEase(((ctx.reveal() - 0.12f) / 0.7f).coerceIn(0f, 1f))
                    alpha = e
                    translationX = (1f - e) * -32.dp.toPx()
                }
                .clip(RoundedCornerShape(10.dp)).background(Color.Black)
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
        ) {
            if (ctx.page == WinPage.MAIN) {
                val pick = shots.getOrNull(shown)
                // (Update 471, owner decision on the soft Stadium 2 window: the gameplay clip (640x480 for an N64 game) was
                // cropped and zoomed into this wide band. The window now opens on the sharp still — decoded at the screen's
                // size — and plays the clip only when asked ("Watch clip"), whole, in its own shape with black bars, never
                // stretched. Nothing is decoded or played while it isn't asked for. The Video previews setting still decides
                // whether there is a clip at all.)
                val clipUrl = ctx.state.videoUrls[ctx.g.stableKey]?.takeIf { ctx.state.appearance.videoPreviews }
                var clipOn by androidx.compose.runtime.remember(ctx.g.stableKey) { androidx.compose.runtime.mutableStateOf(false) }
                if (pick != null && !clipOn) AsyncImage(pick, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else if (!clipOn) (ctx.backdrop ?: ctx.wide)?.let { SmartArt(it, null, Modifier.fillMaxSize(), request = rememberBackdropRequest(it), maxCrop = SmartFill.maxCropFor(it, ctx.cover)) }
                if (!artHere) CaseFlowNoArt(ctx, Modifier.fillMaxSize())
                if (clipOn && clipUrl != null) VideoBackdrop(clipUrl, ctx.state.appearance.videoSound, Modifier.fillMaxSize(), fit = true, shade = false)
                if (clipUrl != null) SceneControl(
                    WinAction(if (clipOn) "Stop clip" else "Watch clip", if (clipOn) Icons.Filled.Stop else Icons.Filled.PlayCircle, { clipOn = !clipOn }),
                    Modifier.align(Alignment.TopEnd).padding(8.dp).zIndex(2f)
                ) { on ->
                    Row(Modifier.clip(RoundedCornerShape(50)).background(if (on) accent else Color.Black.copy(alpha = 0.55f))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(50)).heightIn(min = 32.dp).padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (clipOn) Icons.Filled.Stop else Icons.Filled.PlayCircle, null, tint = if (on) onAccent else Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(if (clipOn) "Stop clip" else "Watch clip", color = if (on) onAccent else Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                // The glass's shine.
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent, Color.Transparent))))
            } else ctx.pageBody(Modifier.fillMaxSize())
        }
        if (ctx.page == WinPage.MAIN) {
            // The name band: its own room under the picture, an accent edge on the case's side.
            Row(Modifier.fillMaxWidth().padding(top = 10.dp).graphicsLayer {
                val e = sceneEase(((ctx.reveal() - 0.2f) / 0.6f).coerceIn(0f, 1f)); alpha = e; translationY = (1f - e) * 8.dp.toPx()
            }, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(3.dp).height(34.dp).clip(RoundedCornerShape(2.dp)).background(accent))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    WinFullText(ctx.name, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 3)
                    Text(listOfNotNull(ctx.console, ctx.year, ctx.developer).joinToString("  ·  "), color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp, maxLines = 2)
                }
            }
        }
        if (ctx.page == WinPage.MAIN && shots.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            // The screenshots, a strip of slides.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val all = listOf(-1) + shots.indices.toList()
                all.forEach { i ->
                    val a = WinAction(if (i < 0) "Video" else "Screenshot ${i + 1}", androidx.compose.material.icons.Icons.Filled.Info, { shown = i })
                    SceneControl(a, Modifier.weight(1f).aspectRatio(1.6f)) { on ->
                        Box(
                            Modifier.fillMaxSize().clip(RoundedCornerShape(5.dp)).background(Color(0xFF1B1D22))
                                .border(if (on || shown == i) 2.dp else 1.dp, if (on) accent else if (shown == i) Color.White.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.15f), RoundedCornerShape(5.dp))
                        ) {
                            val src = if (i < 0) (ctx.backdrop ?: ctx.wide) else shots[i]
                            if (src != null) AsyncImage(src, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = if (on || shown == i) 1f else 0.7f)
                            if (i < 0) Icon(androidx.compose.material.icons.Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(18.dp))
                        }
                    }
                }
            }
        }
        if (ctx.page == WinPage.MAIN) {
            Spacer(Modifier.height(8.dp))
            // The facts as small glass chips.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOfNotNull(ctx.genres, "Played " + ctx.played, ctx.last, if (ctx.total > 0) "★ ${ctx.earned} / ${ctx.total}" else null).forEach {
                    Box(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 3.dp)) { Text(it, color = Color(0xFFD5D9E2), fontSize = 12.sp, maxLines = 1) }
                }
            }
            ctx.summary?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = Color(0xFFB9BEC9), fontSize = 12.sp, lineHeight = 16.sp, maxLines = 3)
            }
        }
        }
        Spacer(Modifier.height(12.dp))
        // Glossy pills, each with its reflection on the stage's floor.
        FlowRow(Modifier.graphicsLayer {
            // (Update 505: they rise onto the stage after the picture; whole and in place from 0.75 of the reveal on.)
            val e = sceneEase(((ctx.reveal() - 0.3f) / 0.45f).coerceIn(0f, 1f))
            alpha = 0.25f + 0.75f * e
            translationY = (1f - e) * 14.dp.toPx()
        }, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            ctx.buttons.forEach { a ->
                SceneControl(a, Modifier) { on ->
                    // Update 487 (E7, owner-approved): Play is the glossy accent pill even while another key is lit (it looked
                    // like the glass keys), and the word on a lit pill is checked against its lighter top (white on lilac
                    // was 2.6 : 1); a lit Play gets a white ring.
                    val lit = on || a.primary
                    val top = mixS(accent, Color.White, if (on) 0.3f else 0.12f)
                    val inkLit = com.shelf.launcher.ui.theme.textOn(top, onAccent).let { c ->
                        if (com.shelf.launcher.ui.theme.contrastRatio(c, accent) >= 4.5f) c else com.shelf.launcher.ui.theme.textOn(accent, onAccent)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.height(if (a.primary) 46.dp else 40.dp).clip(RoundedCornerShape(50))
                                .background(if (lit) Brush.verticalGradient(listOf(top, if (on) accent else mixS(accent, Color.Black, 0.12f))) else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.05f))))
                                .border(if (on && a.primary) 2.5.dp else 1.dp, if (on) Color.White.copy(alpha = if (a.primary) 0.95f else 0.6f) else if (lit) accent.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                                .padding(horizontal = if (a.primary) 24.dp else 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(a.label, color = if (lit) inkLit else Color.White, fontSize = if (a.primary) 17.sp else 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                        // Its reflection.
                        Box(Modifier.width(if (a.primary) 70.dp else 50.dp).height(6.dp).graphicsLayer { alpha = if (on) 0.45f else 0.15f }
                            .background(Brush.verticalGradient(listOf(if (on) accent else Color.White, Color.Transparent)), RoundedCornerShape(50)))
                    }
                }
            }
            if (ctx.page == WinPage.MAIN) ctx.tools.forEach { a ->
                // Update 487 (E7 tiny controls, owner-approved): the round tool keys keep their 40 dp look in a 48 dp touch
                // box, and their word is written under them (where the pills' reflection is) — they were icon-only.
                SceneControl(a, Modifier.widthIn(min = 48.dp)) { on ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape)
                                    .background(if (on) accent else Color.White.copy(alpha = 0.09f)).border(1.dp, Color.White.copy(alpha = if (on) 0.6f else 0.18f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) { Icon(a.icon, a.label, tint = if (on) com.shelf.launcher.ui.theme.textOn(accent, onAccent) else Color.White, modifier = Modifier.size(19.dp)) }
                        }
                        Text(sceneLabel(a.label), color = Color.White.copy(alpha = if (on) 0.95f else 0.7f), fontSize = 10.sp, lineHeight = 12.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
        if (ctx.chips.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ctx.chips.forEach { a ->
                    SceneControl(a, Modifier) { on ->
                        Row(Modifier.clip(RoundedCornerShape(50)).background(if (on) accent.copy(alpha = 0.3f) else Color.Transparent).padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(a.icon, null, tint = if (a.urgent) Color(0xFFFFB74D) else Color(0xFFB9BEC9), modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(a.label, color = Color(0xFFB9BEC9), fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Orbit deck: the CD-player terminal
// ---------------------------------------------------------------------------------------------------------------------


/**
 * Update 330: where the game window's scene was opened from (the game's own picture on the home screen, window px), for a
 * scene that flies its own piece out of it (Orbit deck's disc); null otherwise.
 */
internal val LocalSceneOrigin = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.ui.geometry.Rect?> { null }

/** Corners cut off top left and bottom right (Orbit deck's console panels). */
private fun odChamfer(cut: Float) = androidx.compose.foundation.shape.GenericShape { sz, _ ->
    moveTo(cut, 0f); lineTo(sz.width, 0f); lineTo(sz.width, sz.height - cut); lineTo(sz.width - cut, sz.height); lineTo(0f, sz.height); lineTo(0f, cut); close()
}

/**
 * Orbit deck's window, after a mid-90s disc console's CD-player screen (Toni's reference): the disc you were on leaves the
 * orbit and comes forward to the left, still spinning; rings open round it (the achievements as a lit arc on one of them,
 * a turning dashed ring, a ring of marks); the actions fan out from it on an arc of round chrome keys; a dark glass console
 * with chrome edges slides out beside it — the name, the readouts, the achievements as numbered tracks, the save and
 * performance lines. Other pages show in the console. Closing runs it all back and the disc returns to its place on the
 * orbit. Narrow screens: the disc and the console side by side on top, the keys in rows under them.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun OrbitTerminal(ctx: WinSceneCtx) {
    val col = com.shelf.launcher.data.Features.odColor.state.value
    val glow = Color(col.glow)
    val origin = LocalSceneOrigin.current
    val anim = rememberAnimationsEnabled()
    val clock = rememberGcClock(anim, 1f, 30)
    val density = androidx.compose.ui.platform.LocalDensity.current
    fun stage(a: Float, b: Float): Float = ((ctx.reveal() - a) / (b - a)).coerceIn(0f, 1f)
    val acts = ctx.buttons.filter { !it.back } + (if (ctx.page == WinPage.MAIN) ctx.tools else emptyList()) + ctx.buttons.filter { it.back }
    val w = ctx.maxW; val h = ctx.maxH
    // The disc and its rings.
    val discD = minOf(h * 0.56f, w * 0.3f)
    val ringR = discD / 2 * 1.42f
    val arcR = discD / 2 * 1.56f
    val keyD = 38.dp
    val discCx = ringR + 6.dp
    // (The keys carry no words on the arc — the one you're on is named on the console's command line.)
    val consoleX = discCx + arcR + keyD / 2 + 18.dp
    val wideOk = w - consoleX >= 260.dp && h >= 280.dp
    val command = remember { androidx.compose.runtime.mutableStateOf("") }
    if (!wideOk) { OrbitTerminalNarrow(ctx, acts, glow, origin, clock); return }
    Box(Modifier.size(w, h)) {
        val discCy = h / 2
        // The rings round the disc.
        Canvas(Modifier.offset(discCx - ringR * 1.05f, discCy - ringR * 1.05f).size(ringR * 2.1f)) {
            val t = stage(0.3f, 0.8f)
            if (t <= 0f) return@Canvas
            val c = center
            val r = discD.toPx() / 2f
            val k = 0.86f + 0.14f * t
            drawCircle(glow.copy(alpha = 0.55f * t), r * 1.05f * k, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
            // The achievements as a lit arc.
            val pr = r * 1.17f * k
            drawCircle(glow.copy(alpha = 0.12f * t), pr, c, style = androidx.compose.ui.graphics.drawscope.Stroke(5.dp.toPx()))
            if (ctx.total > 0) drawArc(Brush.sweepGradient(listOf(glow, Color.White, glow), c), -90f, 360f * ctx.earned / ctx.total * t, false,
                Offset(c.x - pr, c.y - pr), Size(pr * 2, pr * 2), alpha = t, style = androidx.compose.ui.graphics.drawscope.Stroke(5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
            // A dashed ring turning slowly.
            val dr = r * 1.29f * k
            val turn = clock.floatValue * 9f
            for (i in 0 until 48) drawArc(glow.copy(alpha = (if (i % 8 == 0) 0.7f else 0.28f) * t), turn + i * 7.5f, 4.2f, false,
                Offset(c.x - dr, c.y - dr), Size(dr * 2, dr * 2), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
            // A ring of marks.
            val mr = r * 1.4f * k
            for (i in 0 until 72) {
                val a = Math.toRadians(i * 5.0 - turn * 0.25)
                val len = if (i % 6 == 0) r * 0.06f else r * 0.025f
                val d = Offset(kotlin.math.cos(a).toFloat(), kotlin.math.sin(a).toFloat())
                drawLine(Color.White.copy(alpha = (if (i % 6 == 0) 0.55f else 0.22f) * t), c + d * mr, c + d * (mr - len), 1.3f)
            }
        }
        // The disc, flown out of its place on the orbit.
        OdFlyingDisc(ctx, origin, clock, Modifier.offset(discCx - discD / 2, discCy - discD / 2).size(discD))
        // The keys, fanned out on an arc to the disc's right.
        val n = acts.size
        // (Update 470, D212: 22° between keys, was 17° — neighbours' touch boxes crossed by 9 x 18 px at the fan's ends.)
        val span = minOf(156f, (n - 1).coerceAtLeast(1) * 22f)
        val stagger = minOf(0.035f, 0.22f / n.coerceAtLeast(1))
        acts.forEachIndexed { i, a ->
            val ang = if (n == 1) 0f else -span / 2f + span * i / (n - 1)
            val rad = Math.toRadians(ang.toDouble())
            val dx = kotlin.math.cos(rad).toFloat(); val dy = kotlin.math.sin(rad).toFloat()
            SceneControl(a, Modifier
                .offset(discCx + arcR * dx - keyD / 2, discCy + arcR * dy - keyD / 2)
                .graphicsLayer {
                    val t = stage(0.42f + i * stagger, 0.72f + i * stagger)
                    // Out from behind the disc along its ray.
                    val back = (1f - t) * arcR.toPx() * 0.45f
                    translationX = -dx * back; translationY = -dy * back
                    alpha = t
                }
            ) { on ->
                if (on) androidx.compose.runtime.SideEffect { command.value = a.label }
                OdKey(a, on, glow, keyD, 0.dp)
            }
        }
        // The console.
        Box(
            Modifier.offset(consoleX, h * 0.06f).size(w - consoleX, h * 0.88f)
                .graphicsLayer {
                    val t = stage(0.28f, 0.82f)
                    alpha = t
                    translationX = -(1f - t) * 48.dp.toPx()
                    scaleY = 0.9f + 0.1f * t
                }
        ) { OdConsole(ctx, glow, clock, command.value) }
    }
}

/** Orbit deck's window on a narrow screen: the disc and the console side by side, the keys in rows under them. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun OrbitTerminalNarrow(ctx: WinSceneCtx, acts: List<WinAction>, glow: Color, origin: androidx.compose.ui.geometry.Rect?, clock: androidx.compose.runtime.FloatState) {
    val w = ctx.maxW; val h = ctx.maxH
    val discD = minOf(w * 0.34f, h * 0.3f)
    Column(Modifier.size(w, h), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OdFlyingDisc(ctx, origin, clock, Modifier.size(discD))
            Box(Modifier.weight(1f).fillMaxHeight().graphicsLayer { val t = ((ctx.reveal() - 0.28f) / 0.54f).coerceIn(0f, 1f); alpha = t }) { OdConsole(ctx, glow, clock) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.graphicsLayer { alpha = ((ctx.reveal() - 0.45f) / 0.4f).coerceIn(0f, 1f) }) {
            acts.forEach { a -> SceneControl(a, Modifier) { on -> OdKey(a, on, glow, 40.dp, 104.dp) } }
        }
    }
}

/**
 * The disc in the window: laid out in its place, and drawn flying there from where it stood on the orbit ([origin]) as the
 * window opens — the same size there, so it simply leaves the orbit and comes forward; back the same way on closing.
 */
@Composable
private fun OdFlyingDisc(ctx: WinSceneCtx, origin: androidx.compose.ui.geometry.Rect?, clock: androidx.compose.runtime.FloatState, modifier: Modifier) {
    val col = com.shelf.launcher.data.Features.odColor.state.value
    var target by remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    Box(modifier.then(Modifier.onGloballyPositioned { target = it.boundsInWindow() })) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val k = ctx.reveal().coerceIn(0f, 1f)
                val to = target
                if (origin != null && to != null && to.width > 0f) {
                    // (Its picture is the inner 86 % — the disc itself, not its glow.)
                    val s0 = (origin.width / (to.width * 0.86f)).coerceIn(0.05f, 2f)
                    val s = s0 + (1f - s0) * k
                    scaleX = s; scaleY = s
                    translationX = (origin.center.x - to.center.x) * (1f - k)
                    // (A slight arc on the way, as if lifted off the orbit toward you.)
                    translationY = (origin.center.y - to.center.y) * (1f - k) - kotlin.math.sin(k * Math.PI).toFloat() * to.height * 0.08f
                } else {
                    val s = 0.6f + 0.4f * k
                    scaleX = s; scaleY = s; alpha = k
                }
            }
        ) { OrbitDisc(null, ctx.g, true, { clock.floatValue * 40f }, col, ctx.cover, physical = ctx.state.physicalMedia[ctx.g.stableKey]) }
    }
}

/** A round chrome key with its word beside it; the one you're on lit in the orbit's colour. */
@Composable
private fun OdKey(a: WinAction, on: Boolean, glow: Color, keyD: Dp, labelW: Dp) {
    // (labelW 0: the key alone.)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(keyD)
                .drawBehind { if (on) drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.7f), Color.Transparent)), size.minDimension * 0.85f) }
                .clip(CircleShape)
                .background(Brush.verticalGradient(if (on) listOf(Color(0xFFF4FAFF), glow) else listOf(Color(0xFFF2F5F9), Color(0xFF9AA3B2), Color(0xFF5D6676))))
                .border(1.5.dp, Brush.verticalGradient(listOf(Color.White, Color(0xFF3B4250))), CircleShape)
                .padding(3.dp).clip(CircleShape)
                .background(Brush.verticalGradient(if (on) listOf(Color.Transparent, Color.Transparent) else listOf(Color(0xFF2A3140), Color(0xFF0E121A)))),
            contentAlignment = Alignment.Center
        ) {
            val icon = when {
                a.primary -> androidx.compose.material.icons.Icons.Filled.PlayArrow
                a.back -> androidx.compose.material.icons.Icons.Filled.Stop
                else -> a.icon
            }
            Icon(icon, a.label, tint = if (on) Color(0xFF07132A) else if (a.urgent) Color(0xFFFFB74D) else Color(0xFFDDE6F2), modifier = Modifier.size(keyD * 0.5f))
        }
        if (labelW > 0.dp) Spacer(Modifier.width(6.dp))
        if (labelW > 0.dp) Text(
            a.label.uppercase(), color = if (on) Color.White else Color(0xFFB8C4D6), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2,
            letterSpacing = 1.sp, modifier = Modifier.widthIn(max = labelW),
            style = if (on) TextStyle(shadow = Shadow(glow, Offset.Zero, 10f)) else TextStyle.Default
        )
    }
}

/** The dark glass console with chrome edges: the name, readouts, achievements as numbered tracks, the save / performance lines. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun OdConsole(ctx: WinSceneCtx, glow: Color, clock: androidx.compose.runtime.FloatState, command: String = "") {
    val col = com.shelf.launcher.data.Features.odColor.state.value
    val cut = with(androidx.compose.ui.platform.LocalDensity.current) { 18.dp.toPx() }
    val shape = remember(cut) { odChamfer(cut) }
    val chrome = Brush.linearGradient(listOf(Color.White, Color(0xFF8E98A8), Color(0xFFE8EDF4), Color(0xFF5E6878)))
    Box(
        Modifier.fillMaxSize()
            .drawBehind { drawRect(glow.copy(alpha = 0.10f)) }
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(col.top).copy(alpha = 0.92f), Color(0xFF03060E).copy(alpha = 0.94f))))
            .border(2.dp, chrome, shape)
            .drawBehind {
                // Fine scan lines and a sheen across the top.
                var y = 0f
                while (y < size.height) { drawLine(Color.White.copy(alpha = 0.025f), Offset(0f, y), Offset(size.width, y), 1f); y += 4f }
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.1f), Color.Transparent), endY = size.height * 0.18f))
            }
            .padding(start = 20.dp, end = 18.dp, top = 14.dp, bottom = 12.dp)
    ) {
        if (ctx.page != WinPage.MAIN) { ctx.pageBody(Modifier.fillMaxSize()); return@Box }
        val label = TextStyle(color = glow, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        val scroll = rememberScrollState()
        // (Whatever the window's first focus did, it opens at the top.)
        LaunchedEffect(Unit) { kotlinx.coroutines.delay(450); scroll.scrollTo(0) }
        Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("DISC", style = label)
                Spacer(Modifier.width(8.dp))
                // (Batch 522, popup sweep: two lines cut "SYSTEM" off. Whole: smaller, then sliding.)
                WinFullText(ctx.console.uppercase(), color = Color(0xFFDDE6F2), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, maxLines = 2,
                    modifier = Modifier.weight(1f))
                // A small LED, breathing.
                Canvas(Modifier.size(8.dp)) { drawCircle(glow.copy(alpha = 0.55f + 0.45f * kotlin.math.sin(clock.floatValue * 3f).let { (it + 1f) / 2f })) }
                Spacer(Modifier.width(5.dp))
                Text("READY", style = label)
            }
            Spacer(Modifier.height(2.dp))
            Text(ctx.name.uppercase(), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                maxLines = 1, modifier = Modifier.basicMarquee().semantics { this[QaClip.Slides] = true }, style = TextStyle(shadow = Shadow(glow.copy(alpha = 0.8f), Offset.Zero, 12f)))
            Box(Modifier.padding(vertical = 6.dp).fillMaxWidth().height(1.5.dp).background(Brush.horizontalGradient(listOf(glow, glow.copy(alpha = 0.05f)))))
            @Composable
            fun readout(k: String, v: String?) {
                if (v.isNullOrBlank()) return
                Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(k, style = label, modifier = Modifier.width(74.dp))
                    Text(v.uppercase(), color = Color(0xFFEAF2FF), fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, maxLines = 2,
                        )
                }
            }
            readout("TIME", ctx.played)
            readout("LAST", ctx.last ?: "—")
            readout("YEAR", ctx.year)
            readout("MAKER", ctx.developer)
            readout("KIND", ctx.genres)
            Spacer(Modifier.height(8.dp))
            if (ctx.total > 0) {
                // The achievements as the disc's tracks: numbered cells, the earned ones lit.
                val cells = minOf(ctx.total, 24)
                val lit = if (ctx.total <= 24) ctx.earned else kotlin.math.round(ctx.earned.toFloat() / ctx.total * cells).toInt()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("TRACKS", style = label)
                    Spacer(Modifier.weight(1f))
                    Text("${ctx.earned} / ${ctx.total}", color = Color(0xFFDDE6F2), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (i in 0 until cells) {
                        val on = i < lit
                        Box(
                            Modifier.size(26.dp, 16.dp).clip(RoundedCornerShape(4.dp))
                                .background(if (on) Brush.verticalGradient(listOf(Color.White, glow)) else Brush.verticalGradient(listOf(Color(0xFF1C2433), Color(0xFF0B0F18))))
                                .border(1.dp, if (on) Color.White.copy(alpha = 0.8f) else glow.copy(alpha = 0.25f), RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("%02d".format(i + 1), color = if (on) Color(0xFF07132A) else Color(0xFF6E7E96), fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (!ctx.summary.isNullOrBlank()) {
                Text(ctx.summary, color = Color(0xFFC5D0E0), fontSize = 12.sp, maxLines = 5, lineHeight = 16.sp)
            }
            if (ctx.chips.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                ctx.chips.forEach { a ->
                    SceneControl(a, Modifier.padding(vertical = 1.dp)) { on ->
                        Text(a.label, color = if (on) Color.White else if (a.urgent) Color(0xFFFFB74D) else Color(0xFF9FB0C8), fontSize = 11.sp, maxLines = 2,
                            textDecoration = if (on) androidx.compose.ui.text.style.TextDecoration.Underline else null)
                    }
                }
            }
        }
        // The command line: the key you're on, named.
        if (command.isNotEmpty()) Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("CMD", style = label)
            Spacer(Modifier.width(8.dp))
            Text("▸ " + command.uppercase(), color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, maxLines = 2,
                style = TextStyle(shadow = Shadow(glow, Offset.Zero, 8f)))
        }
        }
    }
}

private fun Float.roundToIntSafe() = if (isNaN()) 0 else kotlin.math.round(this).toInt()

// ---------------------------------------------------------------------------------------------------------------------
// Airy home: the floating system panel
// ---------------------------------------------------------------------------------------------------------------------

/**
 * Airy home's window: a white panel floating over the pale screen with a soft shadow — the cover rounded on the left, the
 * name in light type, its facts, the small pixel display showing the game; along the bottom big round buttons with
 * their words under them (Play in the accent colour), the one you're on ringed and lifted. Other pages in a pale well.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AiryPanel(ctx: WinSceneCtx) {
    val accent = Color(com.shelf.launcher.data.Features.ahAccent.state.value.argb)
    // (Update 477, D266: the keys along the bottom are the ones always shown — Play, Details, Back, Inspect, More / Less; with
    // More open the others follow them in the same row, a little smaller, instead of a second row that took the facts' room.)
    val tools = if (ctx.page == WinPage.MAIN) ctx.tools else emptyList()
    val (primaryTools, secondary) = airySplit(tools)
    val w = minOf(ctx.maxW * 0.92f, 860.dp)
    val h = minOf(ctx.maxH * 0.9f, 480.dp)
    Column(
        Modifier.size(w, h)
            .graphicsLayer { val k = ctx.reveal().coerceIn(0f, 1f); translationY = (1f - k) * 30.dp.toPx() }
            .drawBehind {
                drawRoundRect(Color(0xFF1C3150).copy(alpha = 0.18f), Offset(0f, 18f), size, androidx.compose.ui.geometry.CornerRadius(32.dp.toPx()))
            }
            .clip(RoundedCornerShape(30.dp)).background(Brush.verticalGradient(listOf(Color.White, Color(0xFFF2F6FB))))
            .padding(22.dp)
    ) {
        Row(Modifier.fillMaxWidth().weight(1f)) {
            Box(Modifier.fillMaxHeight().aspectRatio(0.74f).clip(RoundedCornerShape(20.dp)).background(Color(0xFFE6ECF3))) {
                if (ctx.cover != null) SmartArt(ctx.cover, ctx.name, Modifier.fillMaxSize(), request = rememberCoverRequest(ctx.cover))
            }
            Spacer(Modifier.width(22.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (ctx.page == WinPage.MAIN) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            // (Update 477: two lines at most — it shrinks, then slides, never cut (a long name stopped at 3 lines,
                            // k477f); no reserved height, so a short name leaves the facts their room.)
                            FullText(ctx.name, Color(0xFF2D3440), 28.sp, maxLines = 2, fontWeight = FontWeight.Light, lineHeight = 31.sp, reserve = false)
                            Text(ctx.console, color = accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                        // (Update 477, D266: a little smaller with More open — the facts under it keep their room on the KONKR.)
                        AiryLcd(ctx.cover, if (secondary.isEmpty()) Modifier.size(84.dp, 64.dp) else Modifier.size(74.dp, 56.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    // (Update 529i, KONKR q0672k2: a long maker and genre took the chips to a third row, which the FlowRow never
                    // placed - "★ 8 / 10" was gone, and with More open "Played" too. Played and ★ now share one chip, and with
                    // More open the facts are two plain lines (as Cover details' window), so every fact stays in view.)
                    val facts = airyFacts(ctx.year, ctx.developer, ctx.genres, ctx.played, ctx.earned, ctx.total)
                    if (secondary.isEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        facts.forEach {
                            Box(Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFE9EFF6)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                                Text(it, color = Color(0xFF55606F), fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    } else Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        airyFactLines(facts).forEach { FullText(it, Color(0xFF55606F), 12.sp, maxLines = 1, reserve = false) }
                    }
                    // (Update 475, D247: the story takes the room the chips leave, in whole lines — on the KONKR, where the keys
                    // wrap to a second row, none fit and it gives way (Details has it); before, it was laid out with no height.)
                    ctx.summary?.let {
                        Spacer(Modifier.height(10.dp))
                        StoryLines(it, Color(0xFF66707E), 13.sp, 18.sp, 4, Modifier.weight(1f, fill = false))
                    }
                } else Box(Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).background(Color(0xFFE9EFF6))) { ctx.pageBody(Modifier.fillMaxSize()) }
            }
        }
        Spacer(Modifier.height(if (secondary.isEmpty()) 14.dp else 10.dp))
        // (Update 477, D266: the round keys always shown along the bottom; with More open the others in one compact row of
        // rounded pills under them — word beside the icon, so the row is short and the facts above keep their room (477a put
        // them in the panel's well: squeezed to 14 px on the KONKR; 477b as small round keys in the same row: it wrapped).)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (ctx.buttons + primaryTools).forEach { a -> AiryKey(a, accent) }
        }
        if (secondary.isNotEmpty()) FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            secondary.forEach { a -> AiryPill(a, accent) }
        }
        if (ctx.chips.isNotEmpty()) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ctx.chips.forEach { a ->
                SceneControl(a, Modifier) { on ->
                    Text(a.label, color = if (on) accent else Color(0xFF8A94A2), fontSize = 11.sp, maxLines = 1)
                }
            }
        }
    }
}

/** Update 529i: Airy's facts - year, maker, genre, then what you've played with the trophies in the same fact. */
internal fun airyFacts(year: String?, developer: String?, genres: String?, played: String, earned: Int, total: Int): List<String> =
    listOfNotNull(year, developer, genres, "Played " + played + (if (total > 0) " · ★ $earned / $total" else ""))

/** Update 529i: with More open, the facts as two plain lines - everything but the last joined, then the last (played, ★). */
internal fun airyFactLines(facts: List<String>): List<String> =
    if (facts.size <= 1) facts else listOf(facts.dropLast(1).joinToString(" · "), facts.last())

/** Update 477 (D266): Airy's bottom-row tools (Inspect, More / Less) and the ones More shows (the pill row). */
internal fun airySplit(tools: List<WinAction>): Pair<List<WinAction>, List<WinAction>> {
    val more = tools.any { it.label == "More" || it.label == "Less" }
    if (!more) return tools to emptyList()   // (no More here — a window that isn't compact: every key along the bottom)
    return tools.partition { it.label == "Inspect" || it.label == "More" || it.label == "Less" }
}

/** Update 477 (D266): one of the keys More shows — a rounded pill, the word beside its icon (lit: ringed in the accent). */
@Composable
private fun AiryPill(a: WinAction, accent: Color) {
    SceneControl(a, Modifier) { on ->
        Row(
            Modifier.height(32.dp).clip(RoundedCornerShape(50)).background(Color.White)
                .border(if (on) 2.dp else 1.dp, if (on) accent else Color(0xFFDDE5EF), RoundedCornerShape(50))
                .padding(start = 10.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(a.icon, a.label, tint = if (on) accent else Color(0xFF55606F), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(a.label, color = if (on) Color(0xFF2D3440) else Color(0xFF55606F), fontSize = 12.sp, maxLines = 1)
        }
    }
}

/** One of Airy's round keys with its word under it (lit: ringed and lifted). [small]: a little smaller. */
@Composable
private fun AiryKey(a: WinAction, accent: Color, small: Boolean = false) {
    SceneControl(a, Modifier) { on ->
        val lift by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.6f), label = "ahBtn")
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { translationY = -lift * 4.dp.toPx() }) {
            Box(
                Modifier.size(if (a.primary) 58.dp else if (small) 40.dp else 50.dp)
                    .drawBehind { drawCircle(Color(0xFF1C3150).copy(alpha = 0.12f + 0.1f * lift), size.minDimension / 2f, Offset(size.width / 2f, size.height / 2f + 4f)) }
                    .clip(CircleShape).background(if (a.primary) accent else Color.White)
                    .border(if (on) 3.dp else 1.dp, if (on) accent.copy(alpha = if (a.primary) 0.5f else 1f) else Color(0xFFDDE5EF), CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(a.icon, a.label, tint = if (a.primary) Color.White else if (on) accent else Color(0xFF55606F), modifier = Modifier.size(if (small) 20.dp else 24.dp)) }
            Text(a.label, color = if (on) Color(0xFF2D3440) else Color(0xFF7A8493), fontSize = if (small) 10.sp else 11.sp, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Night menu: the status screen
// ---------------------------------------------------------------------------------------------------------------------

/**
 * Night menu's window: angular status panels sliding in one after another — the game's name on a slanted white bar
 * across the top, its cover in a slanted frame, a dark slanted panel of its stats (time played, achievements as a bar,
 * released, maker, kind), and the commands as slanted bars with a pointer at the one you're on (white, pulled out).
 */
@Composable
private fun NightStatus(ctx: WinSceneCtx) {
    val col = com.shelf.launcher.data.Features.nmColor.state.value
    val hi = Color(col.hi)
    val w = ctx.maxW; val h = ctx.maxH
    val anim = rememberAnimationsEnabled()
    val tabs = NIGHT_TABS
    // Update 529 (owner: Persona 3 Reload-like motion): the panels come in along the menu's slant, layer after layer (banner,
    // cover, stats, commands - 70 ms apart), each easing out with a small turn that settles; closing runs the same way back,
    // the last in leaving first. A light edge crosses the window as it opens and closes, over slanted ribbons of the night at
    // three depths (Full only). All of it is read from the window's own reveal: nothing runs once it is open.
    val lite = LocalLiteEffects.current && !com.shelf.launcher.data.Features.motionOnLite.state.value
    fun slide(order: Int): Modifier = Modifier.graphicsLayer {
        val e = NightMotion.ease(NightMotion.layer(ctx.reveal(), order.toFloat(), 0.1f, 0.58f))
        val side = if (order % 2 == 0) -1f else 1f
        val dx = (1f - e) * size.width * 0.45f * side
        translationX = dx
        translationY = -dx * 0.12f
        rotationZ = (1f - e) * 3f * side
        alpha = e
    }
    // The list on the home screen steps back while this is open (StyleNightMenu: NightMotion.window).
    LaunchedEffect(Unit) { androidx.compose.runtime.snapshotFlow { ctx.reveal() }.collect { NightMotion.window.floatValue = it.coerceIn(0f, 1f) } }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { NightMotion.window.floatValue = 0f } }
    Box(
        Modifier.size(w, h)
            // (Popup motion work: L1 / R1 walk Status, Details, Achievements, Options - as their commands open them.)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val step = when (e.key) { Key.ButtonL1 -> -1; Key.ButtonR1 -> 1; else -> return@onPreviewKeyEvent false }
                val to = pageStep(tabs.filter { it in ctx.pages }, ctx.page, step)
                if (to != ctx.page) ctx.open(to)
                true
            }
    ) {
        // Popup motion work (Night menu, owner-approved direction): Status's cover and stats, or the page's big slanted panel.
        // A page change sweeps the old panels out along the slant the way the D-pad went and the new ones in after them, solid,
        // over them; back runs the other way. The commands stay on the right.
        val slant = 0.105f
        if (!lite) androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            val r = ctx.reveal().coerceIn(0f, 1f)
            // Three slanted ribbons of the night, the farther ones arriving later and from farther off (depth).
            for (i in 0 until 3) {
                val e = NightMotion.ease(NightMotion.layer(r, i.toFloat(), 0.12f, 0.6f))
                if (e <= 0f) continue
                val y0 = size.height * (0.18f + i * 0.27f)
                val bandH = size.height * (0.16f - i * 0.03f)
                val shift = (1f - e) * size.width * (0.35f + i * 0.2f) * (if (i % 2 == 0) -1f else 1f)
                val lean = size.width * 0.18f
                val p = Path().apply { moveTo(shift, y0 + lean * 0.3f); lineTo(size.width + shift, y0 - lean * 0.3f); lineTo(size.width + shift, y0 - lean * 0.3f + bandH); lineTo(shift, y0 + lean * 0.3f + bandH); close() }
                drawPath(p, hi.copy(alpha = 0.06f * e * (1f - i * 0.25f)))
            }
            // The light edge crossing the window once while it opens or closes.
            if (r in 0.01f..0.99f) {
                val x = -size.width * 0.2f + size.width * 1.4f * NightMotion.ease(r)
                val lean = size.height * 0.3f
                drawLine(Color.White.copy(alpha = 0.55f * (1f - r)), Offset(x + lean, 0f), Offset(x, size.height), 3f)
                drawLine(hi.copy(alpha = 0.35f * (1f - r)), Offset(x + lean - 14f, 0f), Offset(x - 14f, size.height), 8f)
            }
        }
        androidx.compose.animation.AnimatedContent(
            targetState = ctx.page,
            transitionSpec = {
                val dir = pageDirection(tabs, initialState, targetState)
                if (!anim) androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                else androidx.compose.animation.slideIn(tween(NATIVE_PAGE_MS, delayMillis = 40, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { sz ->
                    val dx = (sz.width * 0.75f * dir); androidx.compose.ui.unit.IntOffset(dx.toInt(), (-dx * kotlin.math.tan(slant)).toInt())
                } togetherWith (androidx.compose.animation.slideOut(tween(NATIVE_PAGE_MS - 60, easing = androidx.compose.animation.core.FastOutLinearInEasing)) { sz ->
                    val dx = -(sz.width * 0.6f * dir); androidx.compose.ui.unit.IntOffset(dx.toInt(), (-dx * kotlin.math.tan(slant)).toInt())
                } + androidx.compose.animation.fadeOut(tween(110)))
            },
            contentKey = { it },
            modifier = Modifier.matchParentSize(),
            label = "nightPage"
        ) { p ->
            val live = p == ctx.page
            Box(Modifier.fillMaxSize()) {
                if (p == WinPage.MAIN) {
                // The cover in its slanted frame.
                Box(slide(1).padding(start = w * 0.03f, top = h * 0.2f).size(h * 0.52f, h * 0.68f).graphicsLayer { rotationZ = -3f }) {
                    Box(Modifier.fillMaxSize().offset(10.dp, 10.dp).clip(slantShape(0.12f)).background(hi.copy(alpha = 0.4f)))
                    Box(Modifier.fillMaxSize().clip(slantShape(0.12f)).background(Color.White).padding(5.dp).clip(slantShape(0.12f)).background(Color(col.bottom))) {
                        if (ctx.cover != null) SmartArt(ctx.cover, ctx.name, Modifier.fillMaxSize(), request = rememberCoverRequest(ctx.cover))
                    }
                }
                // The stats panel.
                // (Sprint 525, owner: at ×1.3 "the Saves line sits below the visible panel" - reachable by scrolling, but the
                // settled panel should show it. The panel keeps its size while everything fits (×1.0 unchanged) and grows down
                // to the cover frame's foot when the lines need it; only beyond that does it scroll.)
                Box(
                    slide(2).padding(start = w * 0.33f, top = h * 0.2f).width(w * 0.38f).heightIn(min = h * 0.56f, max = h * 0.70f).graphicsLayer { rotationZ = -2f }
                        .clip(slantShape(0.08f)).background(Brush.horizontalGradient(listOf(Color(col.top).copy(alpha = 0.95f), Color(col.bottom).copy(alpha = 0.92f))))
                        .border(2.dp, hi.copy(alpha = 0.7f), slantShape(0.08f)).padding(start = 34.dp, end = 26.dp, top = 14.dp, bottom = 14.dp)
                ) {
                    Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                        val statIdx = intArrayOf(0)
                        @Composable
                        fun stat(label: String, value: String?) {
                            // (Update 513, Retroid Pocket Classic / Nova: the 96 dp label left the value ~60 dp — "PlayStat / ion". The label
                            // column is at most 40 % of the panel now and the value keeps whole words, stepping its size down if it must.)
                            // (Update 529: the lines come in one after another, a short slide along the slant.)
                            val idx = statIdx[0]++
                            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().graphicsLayer {
                                val e = NightMotion.ease(NightMotion.layer(ctx.reveal(), 5.7f + idx, 0.035f, 0.4f))
                                translationX = (1f - e) * 36.dp.toPx(); alpha = e
                            }) {
                            val labelW = minOf(96.dp, maxWidth * 0.4f)
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(label, color = hi, fontSize = 12.sp, fontWeight = FontWeight.Black, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, modifier = Modifier.width(labelW))
                                // (Update 470, rule 11: "HAL Laboratory" broke as "HAL / Laborator" with the "y" on a third, hidden line on the
                                // KONKR — the value now shrinks a step or two to fit its two lines (down to the readable minimum). (A first try with
                                // StableFullText kept two lines' room for every value: the rows spread and the last ran under the panel's edge.)
                                WholeWordsText(value ?: "—", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            }
                            }
                        }
                        stat("PLAY TIME", ctx.played)
                        stat("LAST", ctx.last)
                        if (ctx.total > 0) {
                            stat("ACHIEVED", "${ctx.earned} / ${ctx.total}")
                            Box(Modifier.padding(start = 96.dp, top = 2.dp, bottom = 4.dp).fillMaxWidth().height(8.dp).clip(slantShape(0.8f)).background(Color.White.copy(alpha = 0.15f))) {
                                Box(Modifier.fillMaxHeight().fillMaxWidth((ctx.earned.toFloat() / ctx.total).coerceIn(0f, 1f)).background(hi))
                            }
                        }
                        stat("RELEASED", ctx.year)
                        stat("MAKER", ctx.developer)
                        stat("KIND", ctx.genres)
                        stat("SYSTEM", ctx.console)
                        Spacer(Modifier.height(6.dp))
                        ctx.chips.forEachIndexed { i, a ->
                            // (Update 470: 10 dp between the status lines — they touched edge to edge on the turned panel, and their boxes
                            // crossed by 19 px on the Odin, o470F.)
                            if (i > 0) Spacer(Modifier.height(10.dp))
                            SceneControl(a.live(live), Modifier) { on ->
                                Text(a.label, color = if (on) Color.White else if (a.urgent) Color(0xFFFFB74D) else hi.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 2,
                                    textDecoration = if (on) androidx.compose.ui.text.style.TextDecoration.Underline else null)
                            }
                        }
                    }
                }

                } else NightPagePanel(ctx, p, live, w, h, col, hi, Modifier.then(slide(1)))
            }
        }
        // (Drawn after the panels: a panel sweeping in along the slant passes under the banner, not over it.)
        // The name across the top (Status), or the page's name with the game under it; a page change wipes the banner's words
        // with a dark slanted band, so it is never empty and the old and new words never run together.
        Box(slide(0).padding(top = 6.dp).fillMaxWidth(0.82f).height(64.dp).graphicsLayer { rotationZ = -2f }
            .clip(slantShape(0.5f)).background(Color.White)) {
            NightBannerWipe(ctx.page, anim) { p -> NightBannerWords(ctx, p, hi) }
        }
        // The commands.
        Column(
            // (Update 470, QA only: the commands hang turned 4° — their straight boxes cross by ~13 px though the turned keys
            // stay 3 px apart (k470f). Marked tilted like the rental tag; the tap test checks the crossing strip.)
            slide(3).align(Alignment.TopEnd).padding(end = 6.dp, top = h * 0.2f).width(w * 0.26f)
                .then(if (QaTrace.on) Modifier.semantics { this[QaClip.Tilted] = true } else Modifier).graphicsLayer { rotationZ = -4f },
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            (ctx.buttons.filter { !it.back } + (if (ctx.page == WinPage.MAIN) ctx.tools else emptyList()) + ctx.buttons.filter { it.back }).forEachIndexed { ci, a ->
                SceneControl(a, Modifier.fillMaxWidth()) { on ->
                    // (Update 529: no bounce - the pointer and the white sweep in and settle; each command arrives just after the one above.)
                    val pull by animateFloatAsState(if (on) 1f else 0f, if (anim) spring(dampingRatio = 0.86f, stiffness = 700f) else tween(0), label = "nmCmd")
                    Row(Modifier.fillMaxWidth().graphicsLayer {
                        val e = NightMotion.ease(NightMotion.layer(ctx.reveal(), 2.8f + minOf(ci, 10) * 0.3f, 0.1f, 0.4f))
                        translationX = -pull * 24.dp.toPx() + (1f - e) * 70.dp.toPx(); alpha = e
                    }, verticalAlignment = Alignment.CenterVertically) {
                        Canvas(Modifier.size(14.dp).graphicsLayer { alpha = pull }) {
                            drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width, size.height / 2f); lineTo(0f, size.height); close() }, hi)
                        }
                        Spacer(Modifier.width(4.dp))
                        Box(
                            Modifier.weight(1f).height(if (a.primary) 32.dp else 26.dp).clip(slantShape(0.5f))
                                .background(Color(col.top).copy(alpha = 0.85f))
                                .drawBehind {
                                    if (pull > 0.001f) {
                                        val sl = size.height * 0.5f
                                        val r = -sl + (size.width + sl) * pull
                                        drawPath(Path().apply { moveTo(0f, 0f); lineTo(r + sl, 0f); lineTo(r, size.height); lineTo(0f, size.height); close() }, Color.White)
                                    }
                                }
                                .then(if (!on) Modifier.border(1.dp, hi.copy(alpha = 0.5f), slantShape(0.5f)) else Modifier)
                                .padding(start = 22.dp), contentAlignment = Alignment.CenterStart
                        ) {
                            // (Phase 7, ShelfTextRoboTest: at ×1.3 "ACHIEVEMENTS" wrapped to a second line the 26 dp key cut off. One
                            // line that shrinks to a readable size, then slides - rule 11 - in the key's fixed height.)
                            WinFullText(a.label.uppercase(), color = if (on) Color(0xFF0B1424) else if (a.urgent) Color(0xFFFFB74D) else Color.White,
                                fontSize = if (a.primary) 17.sp else 13.sp, fontWeight = FontWeight.Black, maxLines = 1,
                                style = androidx.compose.material3.LocalTextStyle.current.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                        }
                    }
                }
            }
        }
    }
}

/** Night menu's pages in command order (Status, Details, Achievements, Options). */
internal val NIGHT_TABS = listOf(WinPage.MAIN, WinPage.DETAILS, WinPage.TROPHIES, WinPage.OPTIONS)

/** The banner's words for page [p]: STATUS and the game's name, or the page's name with the game's name small under it. */
@Composable
private fun NightBannerWords(ctx: WinSceneCtx, p: WinPage, hi: Color) {
    val italic = androidx.compose.material3.LocalTextStyle.current.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
    Box(Modifier.fillMaxSize().padding(start = 40.dp, end = 30.dp), contentAlignment = Alignment.CenterStart) {
        if (p == WinPage.MAIN) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("STATUS", color = mixS(hi, Color.Black, 0.45f), fontSize = 14.sp, fontWeight = FontWeight.Black, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
            Spacer(Modifier.width(16.dp))
            WinFullText(ctx.name.uppercase(), color = Color(0xFF0B1424), fontSize = 28.sp, fontWeight = FontWeight.Black, maxLines = 2, style = italic)
        } else Column(verticalArrangement = Arrangement.Center) {
            WinFullText(winPageTitle(p).uppercase(), color = Color(0xFF0B1424), fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 1, style = italic)
            // (The game stays named on every page: whole, smaller, sliding slowly only when it can't fit one line.)
            WinFullText(ctx.name.uppercase(), color = mixS(hi, Color.Black, 0.45f), fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1, style = italic)
        }
    }
}

/**
 * Night menu's banner wipe: the new page's words are revealed from the left as the old ones are covered, behind a dark
 * slanted band at the wipe's edge (popup motion review: a plain wipe left the banner empty for a moment, a cross-wipe made
 * the two titles run together). Animations off: the new words at once.
 */
@Composable
private fun NightBannerWipe(page: WinPage, anim: Boolean, words: @Composable (WinPage) -> Unit) {
    var shown by remember { mutableStateOf(page) }
    var leaving by remember { mutableStateOf<WinPage?>(null) }
    val k = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(page) { // qa-ok: SNAP-ANIMATE (the old words are drawn whole until this catches up - see [cur])
        if (page == shown) return@LaunchedEffect
        if (!anim) { shown = page; leaving = null; return@LaunchedEffect } // (the new words in this frame's recomposition)
        leaving = shown; shown = page; k.snapTo(0f) // qa-ok: SNAP-ANIMATE (the band starts at the left edge with the old words whole)
        k.animateTo(1f, tween(NATIVE_PAGE_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        leaving = null
    }
    // (Until the effect catches up with a new page the old words stay whole: no frame shows the new words before the wipe.)
    val cur = if (page != shown && anim) shown else page
    val old = leaving
    val band = with(androidx.compose.ui.platform.LocalDensity.current) { 18.dp.toPx() }
    Box(Modifier.fillMaxSize()) {
        if (old != null) Box(Modifier.fillMaxSize().drawWithContent {
            val x = size.width * k.value + band / 2f
            clipRect(left = x) { this@drawWithContent.drawContent() }
        }) { words(old) }
        Box(Modifier.fillMaxSize().drawWithContent {
            if (old == null) drawContent() else {
                val x = size.width * k.value - band / 2f
                clipRect(right = x) { this@drawWithContent.drawContent() }
                drawRect(Color(0xFF0B1424), Offset(x, 0f), Size(band, size.height))
            }
        }) { words(cur) }
    }
}

/**
 * Night menu's page (popup motion work): Details / Achievements / Options in one big slanted panel left of the commands, in
 * the menu's colours and frame - the page has the room it needs instead of the stats panel's corner.
 */
@Composable
private fun NightPagePanel(ctx: WinSceneCtx, p: WinPage, live: Boolean, w: Dp, h: Dp, col: com.shelf.launcher.data.Features.NmColor, hi: Color, modifier: Modifier) {
    Box(
        modifier.padding(start = w * 0.03f, top = h * 0.2f).size(w * 0.68f, h * 0.74f).graphicsLayer { rotationZ = -2f }
            .clip(slantShape(0.06f)).background(Brush.horizontalGradient(listOf(Color(col.top), Color(col.bottom))))
            .border(2.dp, hi.copy(alpha = 0.7f), slantShape(0.06f)).padding(start = 30.dp, end = 26.dp, top = 12.dp, bottom = 12.dp)
    ) { ctx.pageFor(p, Modifier.fillMaxSize(), live) }
}

/**
 * Update 471: the opening of a story for a sheet with little room — whole sentences up to about [max] characters (at least
 * the first one), and whether more follows. Never cut mid-word and never with "…": the window says where the rest is.
 */
internal fun storyLead(story: String, max: Int = 200): Pair<String, Boolean> {
    val t = story.trim()
    if (t.length <= max) return t to false
    val ends = Regex("[.!?](\\s|$)").findAll(t).map { it.range.first + 1 }.toList()
    val cut = ends.lastOrNull { it <= max } ?: ends.firstOrNull { it <= max * 2 } ?: t.lastIndexOf(' ', max).takeIf { it > max / 2 } ?: return t to false
    val lead = t.substring(0, cut).trim()
    return lead to (lead.length < t.length)
}
