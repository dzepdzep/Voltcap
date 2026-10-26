package com.shelf.launcher.ui

import androidx.compose.material.icons.filled.Stop
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
    BLADE_GUIDE, BUBBLE_CARD, CAMERA_MENU, PANEL_DETAIL, MAG_PAGE, SHOP_TAG, TERM_RUN }

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
    val urgent: Boolean = false
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
    val pageBody: @Composable (Modifier) -> Unit
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
    }
}

/**
 * A control of a scene: focusable (its requester attached), clickable, with the window's move / select sounds; [content]
 * draws it lit or not.
 */
@Composable
internal fun SceneControl(a: WinAction, modifier: Modifier, content: @Composable BoxScope.(Boolean) -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val sounds = LocalUiSounds.current
    LaunchedEffect(focused) { if (focused) sounds?.play(UiSound.MOVE) }
    Box(
        modifier
            .then(if (a.focus != null) Modifier.focusRequester(a.focus) else Modifier)
            .clickable(interactionSource = source, indication = null) { sounds?.play(if (a.back) UiSound.BACK else UiSound.SELECT); a.onClick() },
        contentAlignment = Alignment.Center
    ) { content(focused) }
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
                .verticalScroll(scroll)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("INSERT · " + (if (ctx.page == WinPage.MAIN) "CONTENTS" else pageName(ctx.page).uppercase()), color = SheetMuted, fontSize = 11.sp,
                    fontFamily = mono, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(ctx.console.uppercase(), color = SheetMuted, fontSize = 11.sp, fontFamily = mono, maxLines = 1)
            }
            Spacer(Modifier.height(4.dp))
            Text(ctx.name.uppercase(), color = SheetInk, fontSize = 20.sp, lineHeight = 23.sp, fontWeight = FontWeight.Black, fontFamily = mono, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(2.dp).background(SheetInk))
            Spacer(Modifier.height(8.dp))
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
                        Text(value, color = SheetInk, fontSize = 13.sp, fontFamily = mono, fontWeight = FontWeight.Bold, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.widthIn(max = w * 0.55f))
                    }
                }
                line("Released", ctx.year)
                line("Made by", ctx.developer)
                line("Kind", ctx.genres)
                line("Played", ctx.played)
                line("Last played", ctx.last)
                if (ctx.total > 0) line("Achievements", "${ctx.earned} / ${ctx.total}")
                ctx.summary?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = SheetInk.copy(alpha = 0.85f), fontSize = 12.sp, lineHeight = 16.sp, fontFamily = mono, maxLines = if (ctx.maxH > 480.dp) 3 else 2,
                        overflow = TextOverflow.Ellipsis)
                }
            } else {
                // The other pages, on the same paper.
                ctx.pageBody(Modifier.fillMaxWidth().height((ctx.maxH * 0.5f).coerceIn(180.dp, 360.dp)).clip(RoundedCornerShape(2.dp)))
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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
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
                        if (ctx.cover != null) AsyncImage(rememberCoverRequest(ctx.cover), ctx.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    if (!ctx.inspecting) Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Text(ctx.name.uppercase(), color = ink, fontSize = 26.sp, lineHeight = 29.sp, fontWeight = FontWeight.Black, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, style = if (ink == Color.White) FsOutline else TextStyle())
                        Spacer(Modifier.height(4.dp))
                        Text(listOfNotNull(ctx.year, ctx.developer, ctx.genres).joinToString("  ·  "), color = ink.copy(alpha = 0.9f), fontSize = 13.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
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
                                Text(it, color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp, lineHeight = 16.sp, overflow = TextOverflow.Ellipsis,
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
    val tabs = listOf(WinPage.MAIN, WinPage.DETAILS, WinPage.TROPHIES, WinPage.OPTIONS)
    // (Achievements and Options open as GameWindow opens them: their page, else the full list / the game's menu.)
    fun openTab(t: WinPage) = when (t) {
        WinPage.TROPHIES -> ctx.tools.firstOrNull { it.label == "Achievements" }?.onClick?.invoke() ?: Unit
        WinPage.OPTIONS -> ctx.tools.firstOrNull { it.label == "Options" }?.onClick?.invoke() ?: Unit
        else -> ctx.setPage(t)
    }
    Column(
        Modifier.size(w, h).pixelFrame(RecPanel, 10.dp)
            // L1 / R1 turn the tabs.
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val step = when (e.key) { Key.ButtonL1 -> -1; Key.ButtonR1 -> 1; else -> return@onPreviewKeyEvent false }
                val at = tabs.indexOf(ctx.page).coerceAtLeast(0)
                openTab(tabs[(at + step).mod(tabs.size)])
                true
            }
    ) {
        // The title band with the tabs.
        Row(
            Modifier.fillMaxWidth().background(RecBand).padding(start = 12.dp, end = 8.dp, top = 6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            if (w >= 640.dp) Text("STORED ENTRY", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 8.dp))
            Spacer(Modifier.weight(1f))
            Text("L1", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 9.dp, end = 4.dp))
            tabs.forEach { t ->
                val sel = ctx.page == t
                val a = WinAction(pageName(t), androidx.compose.material.icons.Icons.Filled.Info, { openTab(t) })
                SceneControl(a, Modifier.padding(horizontal = 2.dp)) { on ->
                    Box(
                        Modifier.clip(CutCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(if (sel) RecPanel else if (on) red else RecNavy)
                            .padding(horizontal = 12.dp, vertical = if (sel) 8.dp else 6.dp)
                    ) {
                        Text(pageName(t).uppercase(), color = if (sel) RecInk else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace, maxLines = 1)
                    }
                }
            }
            Text("R1", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 9.dp, start = 4.dp))
        }
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
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().pixelFrame(Color.White, 5.dp).padding(horizontal = 8.dp, vertical = 5.dp)) {
                    Text(ctx.name, color = RecInk, fontSize = 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.Black, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.clip(RoundedCornerShape(50)).background(red).padding(horizontal = 12.dp, vertical = 3.dp)) {
                    Text(ctx.console.uppercase(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (ctx.page == WinPage.MAIN) {
                    // The facts in cells.
                    @Composable
                    fun cell(label: String, value: String?) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(118.dp).clip(CutCornerShape(4.dp)).background(RecBand).padding(horizontal = 8.dp, vertical = 3.dp)) {
                                Text(label.uppercase(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, maxLines = 1)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(value ?: "—", color = RecInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Column(Modifier.fillMaxWidth().weight(1f, fill = false)) {
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
                    Box(Modifier.fillMaxWidth().pixelFrame(Color.White, 6.dp).padding(horizontal = 6.dp, vertical = 6.dp)) {
                        FlowRow(maxItemsInEachRow = 3, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            (ctx.buttons.filter { !it.back } + ctx.tools + ctx.buttons.filter { it.back }).forEach { a ->
                                SceneControl(a, Modifier.weight(1f)) { on ->
                                    Row(
                                        Modifier.fillMaxWidth().background(if (on) Color(0xFFE6EEF8) else Color.Transparent).padding(horizontal = 4.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(if (on) "▶" else " ", color = red, fontSize = 13.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(16.dp))
                                        Text(a.label.uppercase(), color = if (a.primary) red else RecInk, fontSize = 13.sp, fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxWidth().weight(1f).pixelFrame(Color.White, 6.dp)) { ctx.pageBody(Modifier.fillMaxSize()) }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ctx.buttons.forEach { a ->
                            SceneControl(a, Modifier) { on ->
                                Box(Modifier.pixelFrame(if (on) Color(0xFFE6EEF8) else Color.White, 5.dp).padding(horizontal = 10.dp, vertical = 5.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (on) "▶ " else "  ", color = red, fontSize = 13.sp, fontWeight = FontWeight.Black)
                                        Text(a.label.uppercase(), color = if (a.primary) red else RecInk, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                    }
                }
                if (ctx.chips.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ctx.chips.forEach { a ->
                            SceneControl(a, Modifier) { on ->
                                Row(Modifier.background(if (on) Color(0xFFE6EEF8) else Color.Transparent).padding(horizontal = 4.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Icon(a.icon, null, tint = if (a.urgent) RecRed else RecMuted, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(a.label, color = if (a.urgent) RecRed else RecMuted, fontSize = 11.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
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
            .verticalScroll(rememberScrollState())
    ) {
        val mediaH = (ctx.maxH * 0.42f).coerceIn(120.dp, 300.dp)
        Box(
            Modifier.fillMaxWidth().height(mediaH).clip(RoundedCornerShape(10.dp)).background(Color.Black)
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
        ) {
            if (ctx.page == WinPage.MAIN) {
                val pick = shots.getOrNull(shown)
                if (pick != null) AsyncImage(pick, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else {
                    (ctx.backdrop ?: ctx.wide)?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    VideoCrossfade(ctx.state.videoUrls[ctx.g.stableKey], ctx.state.appearance.videoSound, Modifier.fillMaxSize())
                }
                // The glass's shine.
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent, Color.Transparent))))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.7f)))))
                Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                    Text(ctx.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.8f), Offset(0f, 2f), 6f)))
                    Text(listOfNotNull(ctx.console, ctx.year, ctx.developer).joinToString("  ·  "), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
            } else ctx.pageBody(Modifier.fillMaxSize())
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
                Text(it, color = Color(0xFFB9BEC9), fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(12.dp))
        // Glossy pills, each with its reflection on the stage's floor.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            ctx.buttons.forEach { a ->
                SceneControl(a, Modifier) { on ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.height(if (a.primary) 46.dp else 40.dp).clip(RoundedCornerShape(50))
                                .background(if (on) Brush.verticalGradient(listOf(mixS(accent, Color.White, 0.3f), accent)) else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.05f))))
                                .border(1.dp, if (on) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                                .padding(horizontal = if (a.primary) 24.dp else 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(a.label, color = if (on) onAccent else Color.White, fontSize = if (a.primary) 17.sp else 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                        // Its reflection.
                        Box(Modifier.width(if (a.primary) 70.dp else 50.dp).height(6.dp).graphicsLayer { alpha = if (on) 0.45f else 0.15f }
                            .background(Brush.verticalGradient(listOf(if (on) accent else Color.White, Color.Transparent)), RoundedCornerShape(50)))
                    }
                }
            }
            if (ctx.page == WinPage.MAIN) ctx.tools.forEach { a ->
                SceneControl(a, Modifier.size(40.dp)) { on ->
                    Box(
                        Modifier.fillMaxSize().clip(CircleShape)
                            .background(if (on) accent else Color.White.copy(alpha = 0.09f)).border(1.dp, Color.White.copy(alpha = if (on) 0.6f else 0.18f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Icon(a.icon, a.label, tint = if (on) onAccent else Color.White, modifier = Modifier.size(19.dp)) }
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
        val span = minOf(150f, (n - 1).coerceAtLeast(1) * 17f)
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
            a.label.uppercase(), color = if (on) Color.White else Color(0xFFB8C4D6), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, letterSpacing = 1.sp, modifier = Modifier.widthIn(max = labelW),
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
                Text(ctx.console.uppercase(), color = Color(0xFFDDE6F2), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                // A small LED, breathing.
                Canvas(Modifier.size(8.dp)) { drawCircle(glow.copy(alpha = 0.55f + 0.45f * kotlin.math.sin(clock.floatValue * 3f).let { (it + 1f) / 2f })) }
                Spacer(Modifier.width(5.dp))
                Text("READY", style = label)
            }
            Spacer(Modifier.height(2.dp))
            Text(ctx.name.uppercase(), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                maxLines = 1, modifier = Modifier.basicMarquee(), style = TextStyle(shadow = Shadow(glow.copy(alpha = 0.8f), Offset.Zero, 12f)))
            Box(Modifier.padding(vertical = 6.dp).fillMaxWidth().height(1.5.dp).background(Brush.horizontalGradient(listOf(glow, glow.copy(alpha = 0.05f)))))
            @Composable
            fun readout(k: String, v: String?) {
                if (v.isNullOrBlank()) return
                Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(k, style = label, modifier = Modifier.width(74.dp))
                    Text(v.uppercase(), color = Color(0xFFEAF2FF), fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
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
                Text(ctx.summary, color = Color(0xFFC5D0E0), fontSize = 12.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
            }
            if (ctx.chips.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                ctx.chips.forEach { a ->
                    SceneControl(a, Modifier.padding(vertical = 1.dp)) { on ->
                        Text(a.label, color = if (on) Color.White else if (a.urgent) Color(0xFFFFB74D) else Color(0xFF9FB0C8), fontSize = 11.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, textDecoration = if (on) androidx.compose.ui.text.style.TextDecoration.Underline else null)
                    }
                }
            }
        }
        // The command line: the key you're on, named.
        if (command.isNotEmpty()) Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("CMD", style = label)
            Spacer(Modifier.width(8.dp))
            Text("▸ " + command.uppercase(), color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, style = TextStyle(shadow = Shadow(glow, Offset.Zero, 8f)))
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
                if (ctx.cover != null) AsyncImage(rememberCoverRequest(ctx.cover), ctx.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Spacer(Modifier.width(22.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (ctx.page == WinPage.MAIN) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(ctx.name, color = Color(0xFF2D3440), fontSize = 28.sp, lineHeight = 31.sp, fontWeight = FontWeight.Light, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(ctx.console, color = accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                        AiryLcd(ctx.cover, Modifier.size(84.dp, 64.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOfNotNull(ctx.year, ctx.developer, ctx.genres, "Played " + ctx.played, if (ctx.total > 0) "★ ${ctx.earned} / ${ctx.total}" else null).forEach {
                            Box(Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFE9EFF6)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                                Text(it, color = Color(0xFF55606F), fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                    ctx.summary?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, color = Color(0xFF66707E), fontSize = 13.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                } else Box(Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).background(Color(0xFFE9EFF6))) { ctx.pageBody(Modifier.fillMaxSize()) }
            }
        }
        Spacer(Modifier.height(14.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (ctx.buttons + if (ctx.page == WinPage.MAIN) ctx.tools else emptyList()).forEach { a ->
                SceneControl(a, Modifier) { on ->
                    val lift by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.6f), label = "ahBtn")
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { translationY = -lift * 4.dp.toPx() }) {
                        Box(
                            Modifier.size(if (a.primary) 58.dp else 50.dp)
                                .drawBehind { drawCircle(Color(0xFF1C3150).copy(alpha = 0.12f + 0.1f * lift), size.minDimension / 2f, Offset(size.width / 2f, size.height / 2f + 4f)) }
                                .clip(CircleShape).background(if (a.primary) accent else Color.White)
                                .border(if (on) 3.dp else 1.dp, if (on) accent.copy(alpha = if (a.primary) 0.5f else 1f) else Color(0xFFDDE5EF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) { Icon(a.icon, a.label, tint = if (a.primary) Color.White else if (on) accent else Color(0xFF55606F), modifier = Modifier.size(24.dp)) }
                        Text(a.label, color = if (on) Color(0xFF2D3440) else Color(0xFF7A8493), fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }
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
    fun slide(order: Int): Modifier = Modifier.graphicsLayer {
        val k = ((ctx.reveal() - order * 0.12f) / 0.6f).coerceIn(0f, 1f)
        val e = 1f - (1f - k) * (1f - k) * (1f - k)
        translationX = (1f - e) * size.width * (if (order % 2 == 0) -0.6f else 0.6f)
        alpha = e
    }
    Box(Modifier.size(w, h)) {
        // The name across the top.
        Box(slide(0).padding(top = 6.dp).fillMaxWidth(0.82f).height(64.dp).graphicsLayer { rotationZ = -2f }
            .clip(slantShape(0.5f)).background(Color.White).padding(start = 40.dp, end = 30.dp), contentAlignment = Alignment.CenterStart) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("STATUS", color = hi.copy(alpha = 1f).let { mixS(it, Color.Black, 0.45f) }, fontSize = 14.sp, fontWeight = FontWeight.Black, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                Spacer(Modifier.width(16.dp))
                Text(ctx.name.uppercase(), color = Color(0xFF0B1424), fontSize = 28.sp, fontWeight = FontWeight.Black, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // The cover in its slanted frame.
        Box(slide(1).padding(start = w * 0.03f, top = h * 0.2f).size(h * 0.52f, h * 0.68f).graphicsLayer { rotationZ = -3f }) {
            Box(Modifier.fillMaxSize().offset(10.dp, 10.dp).clip(slantShape(0.12f)).background(hi.copy(alpha = 0.4f)))
            Box(Modifier.fillMaxSize().clip(slantShape(0.12f)).background(Color.White).padding(5.dp).clip(slantShape(0.12f)).background(Color(col.bottom))) {
                if (ctx.cover != null) AsyncImage(rememberCoverRequest(ctx.cover), ctx.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        // The stats panel.
        Box(
            slide(2).padding(start = w * 0.33f, top = h * 0.2f).size(w * 0.38f, h * 0.56f).graphicsLayer { rotationZ = -2f }
                .clip(slantShape(0.08f)).background(Brush.horizontalGradient(listOf(Color(col.top).copy(alpha = 0.95f), Color(col.bottom).copy(alpha = 0.92f))))
                .border(2.dp, hi.copy(alpha = 0.7f), slantShape(0.08f)).padding(start = 34.dp, end = 26.dp, top = 14.dp, bottom = 14.dp)
        ) {
            if (ctx.page == WinPage.MAIN) Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                @Composable
                fun stat(label: String, value: String?) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, color = hi, fontSize = 12.sp, fontWeight = FontWeight.Black, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, modifier = Modifier.width(96.dp))
                        Text(value ?: "—", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                ctx.chips.forEach { a ->
                    SceneControl(a, Modifier) { on ->
                        Text(a.label, color = if (on) Color.White else if (a.urgent) Color(0xFFFFB74D) else hi.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, textDecoration = if (on) androidx.compose.ui.text.style.TextDecoration.Underline else null)
                    }
                }
            } else ctx.pageBody(Modifier.fillMaxSize())
        }
        // The commands.
        Column(
            slide(3).align(Alignment.TopEnd).padding(end = 6.dp, top = h * 0.2f).width(w * 0.26f).graphicsLayer { rotationZ = -4f },
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            (ctx.buttons.filter { !it.back } + (if (ctx.page == WinPage.MAIN) ctx.tools else emptyList()) + ctx.buttons.filter { it.back }).forEach { a ->
                SceneControl(a, Modifier.fillMaxWidth()) { on ->
                    val pull by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 600f), label = "nmCmd")
                    Row(Modifier.fillMaxWidth().graphicsLayer { translationX = -pull * 24.dp.toPx() }, verticalAlignment = Alignment.CenterVertically) {
                        Canvas(Modifier.size(14.dp).graphicsLayer { alpha = pull }) {
                            drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width, size.height / 2f); lineTo(0f, size.height); close() }, hi)
                        }
                        Spacer(Modifier.width(4.dp))
                        Box(
                            Modifier.weight(1f).height(if (a.primary) 32.dp else 26.dp).clip(slantShape(0.5f))
                                .background(if (on) Color.White else Color(col.top).copy(alpha = 0.85f))
                                .then(if (!on) Modifier.border(1.dp, hi.copy(alpha = 0.5f), slantShape(0.5f)) else Modifier)
                                .padding(start = 22.dp), contentAlignment = Alignment.CenterStart
                        ) {
                            Text(a.label.uppercase(), color = if (on) Color(0xFF0B1424) else if (a.urgent) Color(0xFFFFB74D) else Color.White,
                                fontSize = if (a.primary) 17.sp else 13.sp, fontWeight = FontWeight.Black, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
