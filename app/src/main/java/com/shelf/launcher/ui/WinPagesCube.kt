package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/*
 * P2 batch 2 (682q): the Cube menu window's own pages. The window is one face of the cube turned toward you (glass in the
 * cube's tint, its top and side faces in depth), so its pages are the cube's other faces - the face turns a quarter to the
 * next one - and they are built of the cube's own unit, the small square:
 *  - Details      = FACE 2 · SPECS: each fact on a square tile (label in the tint's light, value in white) set in a grid,
 *                   the story on a wide glass slab under them (a D-pad stop that scrolls).
 *  - Achievements = FACE 3 · CUBES: a wall of little cubes, one per achievement - lit (its top catching the light) for one you
 *                   have, a dark hollow cube for one to get - with the count; the list under it, each row led by its cube.
 *  - Options      = FACE 4 · CONTROLS: each option a square key on a slab; one that removes something is a red-edged key and
 *                   asks for a second A (NativeConfirm).
 * Motion: the next face turns in a quarter on its edge (rotationY, the side you went), the last one turns away.
 */

/** The face's name, as the cube's corner label shows it. Pure. */
internal fun cubePageName(p: WinPage): String = when (p) {
    WinPage.MAIN -> "FACE 1 · GAME"
    WinPage.DETAILS -> "FACE 2 · SPECS"
    WinPage.TROPHIES -> "FACE 3 · CUBES"
    WinPage.OPTIONS -> "FACE 4 · CONTROLS"
    WinPage.PERF -> "PERFORMANCE"
}

/** How many little cubes the wall shows per row for [n] achievements in [widthDp] (each cube 14 dp + 3 dp gap), at least 1. Pure. */
internal fun cubeWallColumns(widthDp: Float, cube: Float = 14f, gap: Float = 3f): Int = ((widthDp + gap) / (cube + gap)).toInt().coerceAtLeast(1)

internal fun cubeMove(@Suppress("UNUSED_PARAMETER") dir: Int): ContentTransform = fadeIn(tween(NATIVE_PAGE_MS / 2, delayMillis = NATIVE_PAGE_MS / 3)) togetherWith fadeOut(tween(NATIVE_PAGE_MS / 3))

private val CUBE_DANGER = Color(0xFFFF5A5F)

/** One of the cube's other faces (face 1 is the game itself, drawn by CubeTurnFace). [a] the tint's light, [glass] the face's dark. */
@Composable
internal fun AnimatedVisibilityScope.CubePage(ctx: WinSceneCtx, p: WinPage, live: Boolean, a: Color, glass: Color, modifier: Modifier) {
    val anim = rememberAnimationsEnabled()
    // The quarter turn: the face comes round on its left edge as it enters, goes round on its right as it leaves.
    val turn by transition.animateFloat(transitionSpec = { if (anim) tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing) else snap() }, label = "cubeTurn") {
        when (it) { EnterExitState.PreEnter -> -70f; EnterExitState.Visible -> 0f; EnterExitState.PostExit -> 70f }
    }
    val m = modifier.graphicsLayer {
        rotationY = turn
        transformOrigin = TransformOrigin(if (turn < 0f) 0f else 1f, 0.5f)
        cameraDistance = 10f * density
        flatTextFor3d()
    }
    when (p) {
        WinPage.DETAILS -> CubeSpecs(ctx, live, a, glass, m)
        WinPage.TROPHIES -> CubeWall(ctx, live, a, glass, m)
        WinPage.OPTIONS -> CubeControls(ctx, live, a, glass, m)
        WinPage.MAIN -> {}
        else -> ctx.pageFor(p, modifier, live) // (Performance: the shared page - not a face of the cube yet)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun CubeSpecs(ctx: WinSceneCtx, live: Boolean, a: Color, glass: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    Column(modifier.nativeScroll(a)) {
        // The fact tiles: square-cornered, the label in the tint's light, the value in white.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            nativeFacts(ctx, setOf("Trophies", "Console")).forEach { f ->
                Column(Modifier.width(118.dp).clip(RoundedCornerShape(3.dp)).background(Color.Black.copy(alpha = 0.28f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(3.dp)).padding(5.dp)) {
                    Text(f.label.uppercase(), color = a, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    Text(f.value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 15.sp)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            Text(ctx.summary ?: "No story on this face yet.", color = Color.White.copy(alpha = 0.88f), fontSize = 12.sp, lineHeight = 17.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(glass.copy(alpha = 0.55f))
                    .border(if (on) 2.dp else 1.dp, if (on) a else Color.White.copy(alpha = 0.15f), RoundedCornerShape(3.dp)).padding(8.dp))
        }
    }
}

/** A little cube: its front, a lighter top and a darker side (lit = earned), hollow when still to get. */
@Composable
private fun LittleCube(lit: Boolean, a: Color, modifier: Modifier) {
    Canvas(modifier) {
        val d = size.width * 0.28f; val s = size.width - d
        val top = Path().apply { moveTo(0f, d); lineTo(d, 0f); lineTo(d + s, 0f); lineTo(s, d); close() }
        val right = Path().apply { moveTo(s, d); lineTo(d + s, 0f); lineTo(d + s, s); lineTo(s, s + d); close() }
        if (lit) {
            drawPath(top, lerp(a, Color.White, 0.45f)); drawPath(right, lerp(a, Color.Black, 0.45f))
            drawRect(a, Offset(0f, d), androidx.compose.ui.geometry.Size(s, s))
        } else {
            val c = Color.White.copy(alpha = 0.35f); val w = 1.dp.toPx()
            drawPath(top, c, style = androidx.compose.ui.graphics.drawscope.Stroke(w)); drawPath(right, c, style = androidx.compose.ui.graphics.drawscope.Stroke(w))
            drawRect(c, Offset(0f, d), androidx.compose.ui.geometry.Size(s, s), style = androidx.compose.ui.graphics.drawscope.Stroke(w))
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun CubeWall(ctx: WinSceneCtx, live: Boolean, a: Color, glass: Color, modifier: Modifier) {
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${t.earned} / ${t.total} CUBES LIT", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
            NativeItem(onClick = t.nextOrder, Modifier, focus = focus, live = live) { on ->
                Text("ORDER · " + t.order.label.uppercase(), color = if (on) cubeInkOn(a) else a, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp,
                    modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(if (on) a else Color.Transparent).border(1.dp, a, RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        // The wall: one little cube per achievement in the list's order.
        if (t.total > 0) FlowRow(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            t.list.forEach { LittleCube(it.isUnlocked, a, Modifier.size(14.dp)) }
        }
        Spacer(Modifier.height(6.dp))
        Column(Modifier.weight(1f).fillMaxWidth().nativeScroll(a), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            t.list.forEach { ach ->
                val earned = ach.isUnlocked
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { on ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(if (on) a.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.18f))
                            .border(1.dp, if (on) a else Color.Transparent, RoundedCornerShape(3.dp)).padding(5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LittleCube(earned, a, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Box(Modifier.size(26.dp).clip(RoundedCornerShape(3.dp)).background(Color.Black)) {
                                    AsyncImage(com.shelf.launcher.data.RaBadges.url(ach.badgeName), ach.title, contentScale = ContentScale.Crop,
                                        colorFilter = if (earned) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
                                        alpha = if (earned) 1f else 0.55f, modifier = Modifier.fillMaxSize())
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(ach.title ?: "Untitled", color = Color.White.copy(alpha = if (earned) 1f else 0.72f), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    lineHeight = 15.sp, modifier = Modifier.weight(1f))
                                Text(ach.points?.let { "$it" } ?: "-", color = if (earned) a else Color.White.copy(alpha = 0.5f), fontSize = 11.sp,
                                    fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 6.dp))
                            }
                            ach.description?.let { d ->
                                if (opened) Text(d, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(start = 54.dp, top = 2.dp))
                                else WinFullText(d, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp, modifier = Modifier.padding(start = 54.dp, top = 2.dp))
                            }
                            if (opened) {
                                val facts = listOfNotNull(
                                    (ach.dateEarnedHardcore ?: ach.dateEarned)?.let { "lit " + it.take(10) },
                                    ach.unlockPercent?.let { "%.1f %% of players".format(it) },
                                    ach.type?.takeIf { it.isNotBlank() }
                                )
                                if (facts.isNotEmpty()) Text(facts.joinToString("  ·  "), color = a, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 54.dp, top = 2.dp))
                            }
                        }
                    }
                }
            }
            if (t.list.isEmpty()) Text("No cubes on this face - the game has no achievements.", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun CubeControls(ctx: WinSceneCtx, live: Boolean, a: Color, glass: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(a), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(), focus = if (i == 0) focus else null, live = live) { on ->
                androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                val armed = confirm.armed == i
                val c = if (o.destructive) CUBE_DANGER else a
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(Color.Black.copy(alpha = if (on) 0.34f else 0.2f))
                    .border(1.dp, if (on) c else Color.White.copy(alpha = 0.12f), RoundedCornerShape(3.dp)).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    // The square key: pressed in (filled) while it has the focus.
                    Box(Modifier.size(22.dp).clip(RoundedCornerShape(3.dp)).background(if (on || armed) c else Color.Transparent)
                        .border(2.dp, c, RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", color = if (on || armed) cubeInkOn(c) else c, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        WinFullText(o.label, color = if (o.destructive) CUBE_DANGER else Color.White, fontSize = 13.sp, maxLines = 2, fontWeight = FontWeight.Bold, lineHeight = 16.sp)
                        o.sub?.let { WinFullText(it, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp) }
                        if (armed) Text("Press A again to confirm  ·  move away to cancel", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black,
                            lineHeight = 14.sp, modifier = Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(3.dp)).background(CUBE_DANGER.copy(alpha = 0.85f))
                                .padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
        }
        if (ctx.options.isEmpty()) Text("No controls on this face.", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
    }
}


private fun cubeInkOn(c: Color): Color = if (c.luminance() > 0.5f) Color(0xFF111111) else Color.White
