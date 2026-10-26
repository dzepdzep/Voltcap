package com.shelf.launcher.ui

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
 * Settings › Game window › "<layout>'s window": built into the layout, or the standard window.
 */

/** Update 330: the layouts with a window of their own. */
internal enum class WinScene { BOX_SHEET, FILE_CARD, STORAGE_RECORD, MEMCARD_INFO, CASE_MEDIA }

internal fun winSceneFor(layout: HomeLayout): WinScene? = when (layout) {
    HomeLayout.BOX -> WinScene.BOX_SHEET
    HomeLayout.FILESELECT -> WinScene.FILE_CARD
    HomeLayout.STORAGE -> WinScene.STORAGE_RECORD
    HomeLayout.MEMCARD -> WinScene.MEMCARD_INFO
    HomeLayout.CASEFLOW -> WinScene.CASE_MEDIA
    else -> null
}

/** What's behind a scene's window: the layout stays in view, dimmed in its own colours. */
internal fun sceneVeil(scene: WinScene): Brush = when (scene) {
    WinScene.BOX_SHEET -> Brush.verticalGradient(listOf(Color(0xFF1E1810).copy(alpha = 0.38f), Color(0xFF0E0A06).copy(alpha = 0.72f)))
    WinScene.FILE_CARD -> Brush.verticalGradient(listOf(Color(0xFF0B1240).copy(alpha = 0.62f), Color(0xFF050826).copy(alpha = 0.84f)))
    WinScene.STORAGE_RECORD -> Brush.verticalGradient(listOf(Color(0xFF0C1C30).copy(alpha = 0.58f), Color(0xFF060E18).copy(alpha = 0.82f)))
    WinScene.CASE_MEDIA -> Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.88f)))
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
