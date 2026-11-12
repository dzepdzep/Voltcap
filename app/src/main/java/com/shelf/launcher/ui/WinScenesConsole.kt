package com.shelf.launcher.ui

import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.snap
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/*
 * Update 344: the game windows of the console-inspired layouts (Blades, …), drawn by the layouts themselves (see
 * WinScenes.kt for how a scene gets the game and its actions from GameWindow).
 */

/** Update 347: the pocket camera's screen moves in whole steps (12 a switch-on), as its little LCD would. */
internal fun cameraStep(k: Float): Float = (kotlin.math.floor(k.coerceIn(0f, 1f) * 12f) / 12f)

/** Update 347: the rental tag's swing as it opens — from 1 (swung away) past 0 (hanging) to a little the other way and back. */
internal fun tagSwing(k: Float): Float {
    val t = k.coerceIn(0f, 1f)
    // A damped swing: cos with the amplitude dying away, landing on 0 at t = 1.
    return ((1f - t) * (1f - t) * kotlin.math.cos(t * Math.PI.toFloat() * 2.5f)).let { if (t >= 1f) 0f else it }
}

/** Update 347: how far the terminal box has drawn itself at [k]: its frame (top, sides, bottom), the ground, the text. */
internal class TermFrame(val top: Float, val sides: Float, val bottom: Float, val fill: Float, val text: Float)

internal fun termFrame(k: Float): TermFrame {
    val t = k.coerceIn(0f, 1f)
    fun seg(a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
    return TermFrame(top = seg(0f, 0.18f), sides = seg(0.12f, 0.38f), bottom = seg(0.32f, 0.45f), fill = seg(0f, 0.3f), text = seg(0.45f, 1f))
}

/** Update 346: Play in the magazine's burst — one "!" (the look's own word may already shout: "CONTINUE!"). */
internal fun magBurstLabel(label: String): String = label.uppercase().let { if (it.endsWith("!")) it else "$it!" }

/** Update 346: a rental tag's key — Play reads "RENT & PLAY", never "RENT & RENT & PLAY" when the look says it already. */
internal fun rentalKeyLabel(label: String, primary: Boolean): String = label.uppercase().let { if (primary && !it.startsWith("RENT")) "RENT & $it" else it }

/** Update 346: a scrolling column whose edges fade where there's more (see sceneFadeEdges). */
@Composable
private fun fadeScroll(): Modifier { val s = rememberScrollState(); return Modifier.sceneFadeEdges(s).verticalScroll(s) }

private fun winPageName(p: WinPage) = when (p) {
    WinPage.MAIN -> "Info"; WinPage.DETAILS -> "Details"; WinPage.OPTIONS -> "Options"; WinPage.TROPHIES -> "Achievements"; WinPage.PERF -> "Performance"
}

// ---------------------------------------------------------------------------------------------------------------------
// Blades: the guide blade
// ---------------------------------------------------------------------------------------------------------------------

/**
 * Blades' window: one more blade slides in from the left over the dimmed stack — its curved edge and gloss, the game's
 * name big and light, its commands as a list with the lit bar (Play first), and on the right its cover with the facts
 * and the achievements under it. The other pages (details, options, achievements, performance) fill the right side.
 */
@Composable
internal fun BladeGuide(ctx: WinSceneCtx) {
    val p = bladePalette()
    val w = minOf(ctx.maxW, 820.dp)
    val h = minOf(ctx.maxH, 520.dp)
    val compact = h < 380.dp
    val anim = rememberAnimationsEnabled()
    val onMain = ctx.page == WinPage.MAIN
    // (A plain holder, not state: written while drawing the first page, read under a page blade - writing state while composing
    // would recompose without end.)
    val mainRows = androidx.compose.runtime.remember(ctx.g.stableKey) { arrayOf<List<WinAction>>(emptyList()) }
    // (Popup motion work: the visible strip of the game blade under a page blade - its curved edge and the name standing up.)
    val edgeW = w * 0.05f + 44.dp
    Box(
        Modifier.size(w, h).graphicsLayer {
            // (Update 347: the blade slides in edge first, turned a little away, and squares up as it lands; closing runs
            // it back out the same way.)
            val k = ctx.reveal().coerceIn(0f, 1f)
            val e = 1f - (1f - k) * (1f - k) * (1f - k)
            translationX = -(1f - e) * size.width * 0.6f
            rotationY = (1f - e) * 14f
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            cameraDistance = 14f * density
            alpha = (e * 1.6f).coerceIn(0f, 1f)
            // (Update 489, D285: turned text drawn flat — see flatTextFor3d.)
            flatTextFor3d()
        }.onPreviewKeyEvent { e ->
            // (Popup motion work: L1 / R1 walk the game, Details, Achievements, Options - as their commands open them.)
            if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val step = when (e.key) { androidx.compose.ui.input.key.Key.ButtonL1 -> -1; androidx.compose.ui.input.key.Key.ButtonR1 -> 1; else -> return@onPreviewKeyEvent false }
            val to = pageStep(BLADE_TABS.filter { it in ctx.pages }, ctx.page, step)
            if (to != ctx.page) ctx.open(to)
            true
        }
    ) {
        // The blade itself: a curved leading edge, its colour, a gloss and a lit rim.
        BladeBody(p, Modifier.fillMaxSize())
        // (Phase 6, 12 Nov: while a page blade lies over it, the game blade's words are hidden from accessibility services - a
        // screen reader read the covered text - and from the QA text checks; nothing changes on screen, its keys are inert.)
        Column(Modifier.fillMaxSize().padding(start = w * 0.08f, end = 18.dp, top = 14.dp, bottom = 12.dp)
            .then(if (ctx.page != WinPage.MAIN) Modifier.clearAndSetSemantics { } else Modifier)) {
            // (Update 478: one line that shrinks, then slides — a second line pushed the commands' Back under the blade's
            // edge on the Odin with a long name, T3 o478D.)
            WinFullText(ctx.name, color = Color.White, fontSize = if (compact) 22.sp else 30.sp, fontWeight = FontWeight.Light, maxLines = 1)
            Text(
                ctx.console,
                color = lerp(p.glow, Color.White, 0.45f), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1
            )
            Spacer(Modifier.height(if (compact) 6.dp else 12.dp))
            Row(Modifier.fillMaxSize()) {
                // The commands.
                Column(Modifier.weight(0.46f).fillMaxHeight().then(fadeScroll()), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    // (Popup motion work: under a page blade the game blade keeps its first page's commands, drawn and inert.)
                    val rows = if (onMain) (ctx.buttons.filter { !it.back } + ctx.tools + ctx.buttons.filter { it.back }).also { mainRows[0] = it.map { r -> r.inert() } }
                        else mainRows[0].map { it.copyInert() }
                    rows.forEach { a ->
                        SceneControl(a, Modifier.fillMaxWidth()) { on ->
                            val k by animateFloatAsState(if (on) 1f else 0f, tween(150), label = "bgRow")
                            Row(
                                // (Update 467, popup gate: on a short screen — the KONKR — the eight commands ran past the blade's
                                // bottom and Back was cut in half; shorter rows there keep the whole list on the blade.)
                                // (Update 469, display size larger — KONKR at 280 dpi, a 366 dp screen: Back cut by 14 px again;
                                // one more step there, rows still >= 24 dp.)
                                Modifier.fillMaxWidth().height(if (a.primary) (if (h < 380.dp) 32.dp else if (h < 440.dp) 38.dp else 44.dp) else if (h < 380.dp) 25.dp else if (h < 440.dp) 29.dp else 36.dp).clip(RoundedCornerShape(3.dp))
                                    .background(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.94f * k), lerp(p.glow, Color.White, 0.5f).copy(alpha = 0.86f * k))))
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(a.icon, null, tint = if (on) p.ink else Color.White, modifier = Modifier.size(if (a.primary) 22.dp else 18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(a.label, color = if (on) p.ink else if (a.urgent) Color(0xFFFFC46B) else Color.White,
                                    fontSize = if (a.primary) 18.sp else 15.sp, fontWeight = if (on || a.primary) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 2)
                            }
                        }
                    }
                    if (onMain && ctx.chips.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        ctx.chips.forEach { a ->
                            SceneControl(a, Modifier.fillMaxWidth()) { on ->
                                Text(a.label, color = if (on) Color.White else if (a.urgent) Color(0xFFFFC46B) else Color.White.copy(alpha = 0.72f), fontSize = 12.sp,
                                    maxLines = 2,
                                    textDecoration = if (on) androidx.compose.ui.text.style.TextDecoration.Underline else null,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
                // The game: its cover and facts, or the page.
                Column(Modifier.weight(0.54f).fillMaxHeight()) {
                    run {
                        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                        // (Batch 522, popup sweep on the RP Classic: a tall, narrow column - the cover took its height's 0.72 as width,
                        // the whole column, and the facts beside it were laid out 0 px wide ("Played" not shown). The cover now
                        // leaves the facts at least 45 % of the column; it keeps its shape, a little shorter there.)
                        val coverW = minOf(maxHeight * 0.72f, maxWidth * 0.55f)
                        Row(Modifier.fillMaxSize()) {
                            Box(Modifier.size(coverW, coverW / 0.72f).clip(RoundedCornerShape(3.dp)).background(Color.Black.copy(alpha = 0.3f))
                                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(3.dp))) {
                                if (ctx.cover != null) SmartArt(ctx.cover, ctx.name, Modifier.fillMaxSize(), request = rememberCoverRequest(ctx.cover))
                            }
                            Spacer(Modifier.width(12.dp))
                            // (Update 471, facts gate: beside the cover the facts ran past the column at rest — "KIND" shown, its
                            // value cut on the KONKR. When they don't fit, they step down in size (to 80 %), measured once more
                            // after each step; the column still scrolls if even that isn't enough.)
                            var viewH by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
                            var factsH by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
                            var k by androidx.compose.runtime.remember(ctx.g.stableKey) { androidx.compose.runtime.mutableFloatStateOf(1f) }
                            androidx.compose.runtime.LaunchedEffect(viewH, factsH, k) { if (viewH > 0 && factsH > viewH && k > 0.81f) k = (k - 0.05f).coerceAtLeast(0.8f) }
                            Column(Modifier.weight(1f).onSizeChanged { viewH = it.height }.then(fadeScroll())) {
                            Column(Modifier.onSizeChanged { factsH = it.height }) {
                                @Composable
                                fun fact(label: String, value: String?) {
                                    if (value.isNullOrBlank()) return
                                    Text(label.uppercase(), color = lerp(p.glow, Color.White, 0.4f), fontSize = 10.sp * k, lineHeight = 13.sp * k, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                    Text(value, color = Color.White, fontSize = 14.sp * k, lineHeight = 18.sp * k, maxLines = 3)
                                    Spacer(Modifier.height(6.dp * k))
                                }
                                fact("Played", ctx.played)
                                fact("Last played", ctx.last)
                                fact("Released", ctx.year)
                                fact("Made by", ctx.developer)
                                fact("Kind", ctx.genres)
                            }
                            }
                        }
                        }
                        if (ctx.total > 0) {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(androidx.compose.material.icons.Icons.Filled.EmojiEvents, null, tint = p.glow, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("${ctx.earned} / ${ctx.total}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(10.dp))
                                Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.18f))) {
                                    Box(Modifier.fillMaxHeight().fillMaxWidth((ctx.earned.toFloat() / ctx.total).coerceIn(0f, 1f)).background(Brush.horizontalGradient(listOf(p.glow, Color.White))))
                                }
                            }
                        }
                        if (!compact) ctx.summary?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, color = Color.White.copy(alpha = 0.82f), fontSize = 12.sp, lineHeight = 16.sp, maxLines = 4)
                        }
                    }
                }
            }
        }
        // The game blade's name, standing up on its visible strip while a page blade lies over it.
        val covered by animateFloatAsState(if (onMain) 0f else 1f, if (anim) tween(NATIVE_PAGE_MS) else snap(), label = "bladeEdge")
        if (covered > 0.01f) Box(Modifier.width(edgeW).fillMaxHeight().padding(start = w * 0.05f, top = 16.dp, bottom = 16.dp).graphicsLayer { alpha = covered }) {
            BladeEdgeName(ctx.name, Modifier.fillMaxSize())
        }
        // Popup motion work (Blades, owner-approved direction): each page is a blade of its own. Going forward the new blade
        // slides in from the right over the one you were on; going back (B, L1) the blade you were on slides away to the right
        // and the one under it is there. The page blade leaves the game blade's edge in view, its name readable on it.
        androidx.compose.animation.AnimatedContent(
            targetState = ctx.page,
            transitionSpec = {
                val dir = pageDirection(BLADE_TABS, initialState, targetState)
                if (!anim) androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                else if (dir > 0) (androidx.compose.animation.slideInHorizontally(tween(NATIVE_PAGE_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it } togetherWith
                    androidx.compose.animation.fadeOut(tween(1, delayMillis = NATIVE_PAGE_MS))).apply { targetContentZIndex = 1f }
                else (androidx.compose.animation.EnterTransition.None togetherWith
                    androidx.compose.animation.slideOutHorizontally(tween(NATIVE_PAGE_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it }).apply { targetContentZIndex = -1f }
            },
            contentKey = { it },
            modifier = Modifier.fillMaxSize(),
            label = "bladePage"
        ) { pg ->
            if (pg != WinPage.MAIN) BladePageBlade(ctx, pg, pg == ctx.page, p, Modifier.padding(start = edgeW).fillMaxSize(), compact)
            else Box(Modifier.fillMaxSize())
        }
    }
}

/** Blades' pages in command order (the game, Details, Achievements, Options). */
internal val BLADE_TABS = listOf(WinPage.MAIN, WinPage.DETAILS, WinPage.TROPHIES, WinPage.OPTIONS)

/** A copy of an inert row (fresh object, no focus, does nothing): the game blade's commands while a page lies over them. */
private fun WinAction.copyInert(): WinAction = WinAction(label, icon, {}, focus = null, primary = primary, back = back, urgent = urgent, inert = true)

/** One blade: a curved leading edge, its colour, a gloss and a lit rim (update 346), drawn in [modifier]'s box. */
@Composable
private fun BladeBody(p: BladePalette, modifier: Modifier) {
    Canvas(modifier) {
            val tab = size.width * 0.05f
            val corner = tab * 0.6f
            val path = Path().apply {
                moveTo(tab + corner, 0f)
                lineTo(size.width - 6f, 0f)
                quadraticTo(size.width, 0f, size.width, 6f)
                lineTo(size.width, size.height - 6f)
                quadraticTo(size.width, size.height, size.width - 6f, size.height)
                lineTo(tab + corner, size.height)
                quadraticTo(tab, size.height, tab * 0.8f, size.height - corner)
                quadraticTo(-tab * 0.5f, size.height / 2f, tab * 0.8f, corner)
                quadraticTo(tab, 0f, tab + corner, 0f)
                close()
            }
            // (Update 346: the same blade as the home's — a deep shadow on what it slides over, a bevel and a lit sheen
            // along its curved edge, the gloss band and a diagonal shine — so it reads as one more blade, not a card.)
            val edge = Path().apply {
                moveTo(tab + corner, size.height)
                quadraticTo(tab, size.height, tab * 0.8f, size.height - corner)
                quadraticTo(-tab * 0.5f, size.height / 2f, tab * 0.8f, corner)
                quadraticTo(tab, 0f, tab + corner, 0f)
            }
            translate(left = -tab * 0.5f, top = 6f) { drawPath(path, Color.Black.copy(alpha = 0.25f)) }
            translate(left = -tab * 0.22f, top = 10f) { drawPath(path, Color.Black.copy(alpha = 0.35f)) }
            drawPath(path, Brush.linearGradient(listOf(lerp(p.hi, Color.White, 0.08f), lerp(p.hi, p.lo, 0.55f), p.lo), start = Offset.Zero, end = Offset(size.width, size.height)))
            clipPath(path) {
                drawPath(edge, Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Transparent), startX = -tab * 0.5f, endX = tab * 2.2f), style = Stroke(tab * 0.9f))
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.04f), Color.Transparent), endY = size.height * 0.32f))
                drawRect(Brush.linearGradient(0f to Color.Transparent, 0.42f to Color.Transparent, 0.5f to Color.White.copy(alpha = 0.09f), 0.58f to Color.Transparent,
                    1f to Color.Transparent, start = Offset.Zero, end = Offset(size.width, size.height)))
            }
            drawPath(edge, Color.White.copy(alpha = 0.8f), style = Stroke(2.4f))
            drawPath(path, Color.White.copy(alpha = 0.3f), style = Stroke(1.5f))
    }
}

/**
 * The game blade's name standing up on its edge (popup motion work): whole, in up to two lines across the strip, a step smaller
 * when it must; only a name too long even then slides slowly along the edge (never cut, never "…").
 */
@Composable
private fun BladeEdgeName(name: String, modifier: Modifier) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        val along = maxHeight; val across = maxWidth
        Box(Modifier.requiredSize(along, across).graphicsLayer { rotationZ = -90f }, contentAlignment = Alignment.CenterStart) {
            WinFullText(name.uppercase(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, letterSpacing = 1.sp,
                modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * A page of Blades' window as its own blade (popup motion work): the page's name over the game's, the page in a darker well,
 * its keys as the blade's lit rows along the bottom.
 */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun BladePageBlade(ctx: WinSceneCtx, pg: WinPage, live: Boolean, p: BladePalette, modifier: Modifier, compact: Boolean) {
    Box(modifier) {
        BladeBody(p, Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().padding(start = 40.dp, end = 18.dp, top = 14.dp, bottom = 12.dp)) {
            WinFullText(winPageName(pg), color = Color.White, fontSize = if (compact) 20.sp else 26.sp, fontWeight = FontWeight.Light, maxLines = 1)
            WinFullText(ctx.name, color = lerp(p.glow, Color.White, 0.45f), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(Color.Black.copy(alpha = 0.22f))) {
                ctx.pageFor(pg, Modifier.fillMaxSize(), live)
            }
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ctx.buttons.forEach { a0 ->
                    val a = if (live) a0 else a0.inert()
                    SceneControl(a, Modifier) { on ->
                        val k by animateFloatAsState(if (on) 1f else 0f, tween(150), label = "bgKey")
                        Row(
                            Modifier.heightIn(min = 32.dp).clip(RoundedCornerShape(3.dp))
                                .background(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.94f * k), lerp(p.glow, Color.White, 0.5f).copy(alpha = 0.86f * k))))
                                .border(1.dp, Color.White.copy(alpha = 0.35f * (1f - k)), RoundedCornerShape(3.dp))
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(a.icon, null, tint = if (on) p.ink else Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(a.label, color = if (on) p.ink else Color.White, fontSize = 15.sp, fontWeight = if (on || a.primary) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Bubble home: a LiveArea card
// ---------------------------------------------------------------------------------------------------------------------

/**
 * Bubble home's window (when it opens from elsewhere — the second screen, a search — rather than as the layout's own
 * LiveArea): the same page in small — the game's art across the top, its bubble and name, the white Start plate, its
 * commands as round-ended white keys; the facts on a white panel, or the page.
 */
@Composable
internal fun BubbleCard(ctx: WinSceneCtx) {
    val w = minOf(ctx.maxW, 760.dp)
    val h = minOf(ctx.maxH, 500.dp)
    val compact = h < 380.dp
    // (Update 505: in the wallpaper you picked — its dark as the page's ground, its colour for the ring of the key you're on (the
    // LiveArea blue on the game's-art wallpaper; your accent when this layout has its own colours) — was always the Sky wall's.)
    val wallNow = com.shelf.launcher.data.Features.bvWall.state.value
    val ground = sceneDark(bubbleWallInk(wallNow), 0.03f)
    val blue = sceneLit(com.shelf.launcher.data.HomeLayout.BUBBLES, bubbleWallLit(wallNow), ground)
    val blueInk = legibleOn(blue, Color.White) // (on the white panels)
    Box(
        Modifier.size(w, h).graphicsLayer {
            // (Update 347: it opens as the bubble swelling into the page — a round window growing from its bubble's
            // place, the page swinging down as it unfolds — and leaves as the LiveArea does, peeled off from the corner.)
            val k = ctx.reveal().coerceIn(0f, 1f)
            if (!ctx.closing()) {
                val r = (k * 1.25f).coerceIn(0f, 1f)
                shape = androidx.compose.foundation.shape.GenericShape { sz, _ ->
                    val c = Offset(sz.width * 0.12f, sz.height * 0.14f)
                    val far = kotlin.math.hypot(sz.width, sz.height)
                    val rad = 26f * density + (far - 26f * density) * r * r
                    addOval(androidx.compose.ui.geometry.Rect(c, rad))
                }
                clip = true
                rotationX = (1f - k) * -22f
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                cameraDistance = 14f * density
                alpha = (k * 2.5f).coerceIn(0f, 1f)
            }
            // (Update 489, D285: turned text drawn flat — see flatTextFor3d.)
            flatTextFor3d()
        }.drawWithContent {
            if (!ctx.closing()) { drawContent(); return@drawWithContent }
            val pk = (1f - ctx.reveal()).coerceIn(0f, 1f)
            val o = peelShape(pk).createOutline(size, layoutDirection, this)
            if (o is androidx.compose.ui.graphics.Outline.Generic) clipPath(o.path) { this@drawWithContent.drawContent() } else drawContent()
            drawPeelFlap(pk)
        }.clip(RoundedCornerShape(10.dp)).background(ground)
    ) {
        (ctx.backdrop ?: ctx.wide)?.let { SmartArt(it, null, Modifier.fillMaxSize(), request = rememberBackdropRequest(it), maxCrop = SmartFill.maxCropFor(it, ctx.cover)) }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.75f), 0.6f to Color.Black.copy(alpha = 0.3f), 1f to Color.Black.copy(alpha = 0.5f))))
        Row(Modifier.fillMaxSize().padding(18.dp)) {
            Column(Modifier.weight(0.5f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(if (compact) 44.dp else 56.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.DarkGray)
                        .border(2.dp, Color.White, androidx.compose.foundation.shape.CircleShape)) {
                        if (ctx.cover != null) SmartArt(ctx.cover, null, Modifier.fillMaxSize(), request = rememberCoverRequest(ctx.cover))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        WinFullText(ctx.name, color = Color.White, fontSize = if (compact) 17.sp else 21.sp, fontWeight = FontWeight.Bold, maxLines = 3)
                        // (Batch 522, popup sweep: "Super Nintendo" - cut by the one line. Whole: smaller, then sliding.)
                        WinFullText(ctx.console, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, maxLines = 1)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Column(Modifier.weight(1f).then(fadeScroll()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val rows = ctx.buttons.filter { !it.back } + (if (ctx.page == WinPage.MAIN) ctx.tools else emptyList()) + ctx.buttons.filter { it.back }
                    rows.forEach { a ->
                        SceneControl(a, Modifier) { on ->
                            Row(
                                Modifier.height(if (a.primary) 50.dp else 34.dp).width(if (a.primary) 200.dp else 180.dp)
                                    .clip(RoundedCornerShape(if (a.primary) 10.dp else 50.dp))
                                    .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFDDE5EE))))
                                    .border(if (on) 3.dp else 1.dp, if (on) blue else Color.White.copy(alpha = 0.6f), RoundedCornerShape(if (a.primary) 10.dp else 50.dp))
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = if (a.primary) Arrangement.Center else Arrangement.Start
                            ) {
                                if (!a.primary) { Icon(a.icon, null, tint = Color(0xFF26364A), modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)) }
                                Text(if (a.primary) "Start" else a.label, color = if (a.urgent) Color(0xFFB0630A) else Color(0xFF1A2633),
                                    fontSize = if (a.primary) 20.sp else 13.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            // (Update 425, owner: half the window was a white panel even for a game with almost no details. A game with few
            // details shows its picture there — LiveArea-style — with the little it has on a strip under it; with no picture
            // either, the panel is narrower. Nothing is made up to fill it.)
            val rich = listOfNotNull(ctx.year, ctx.developer, ctx.genres, ctx.summary?.takeIf { it.isNotBlank() }).size >= 2
            val liveArt = ctx.wide ?: ctx.backdrop ?: ctx.cover
            if (ctx.page == WinPage.MAIN && !rich && liveArt != null) Box(Modifier.weight(0.5f).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(ground)) {
                // (Batch 523: Smart Fill for a cover too - it sat whole between bars in the wide panel.)
                if (liveArt == ctx.cover) SmartArt(liveArt, ctx.name, Modifier.fillMaxSize(), well = ground)
                else AsyncImage(rememberStyleArtRequest(liveArt), ctx.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.White.copy(alpha = 0.92f)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(ctx.played, color = Color(0xFF1A2633), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (ctx.total > 0) {
                            Icon(Icons.Filled.EmojiEvents, null, tint = Color(0xFFE0A400), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("${ctx.earned} / ${ctx.total}", color = Color(0xFF1A2633), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    ctx.last?.let { Text("Last played  $it", color = Color(0xFF5B6B7C), fontSize = 11.sp) }
                    ctx.chips.forEach { a ->
                        SceneControl(a, Modifier) { on ->
                            Text(a.label, color = if (on) blueInk else if (a.urgent) Color(0xFFB0630A) else Color(0xFF5B6B7C), fontSize = 11.sp, maxLines = 2,
                                textDecoration = if (on) androidx.compose.ui.text.style.TextDecoration.Underline else null)
                        }
                    }
                }
            } else Box(Modifier.weight(if (ctx.page == WinPage.MAIN && !rich) 0.36f else 0.5f).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(if (ctx.page == WinPage.MAIN) Color.White.copy(alpha = 0.92f) else Color.Black.copy(alpha = 0.55f))) {
                if (ctx.page == WinPage.MAIN) Column(Modifier.fillMaxSize().padding(12.dp).then(fadeScroll())) {
                    // (Update 424: the LiveArea's info panel stood mostly empty white beside the bubble's keys: the game's picture
                    // heads it, as on the console's page, and its description follows the facts.)
                    (ctx.wide ?: ctx.backdrop ?: ctx.cover)?.let { art ->
                        Box(Modifier.fillMaxWidth().height(if (compact) 96.dp else 140.dp).clip(RoundedCornerShape(6.dp)).background(ground)) {
                            if (art == ctx.cover) SmartArt(art, ctx.name, Modifier.fillMaxSize(), well = ground)
                            else AsyncImage(rememberStyleArtRequest(art), ctx.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    if (ctx.total > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.EmojiEvents, null, tint = Color(0xFFE0A400), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("${ctx.earned} / ${ctx.total}", color = Color(0xFF1A2633), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    @Composable
                    fun line(label: String, value: String?) {
                        if (value.isNullOrBlank()) return
                        Text(label, color = Color(0xFF5B6B7C), fontSize = 11.sp)
                        Text(value, color = Color(0xFF1A2633), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 3)
                        Spacer(Modifier.height(5.dp))
                    }
                    line("Played", ctx.played); line("Last played", ctx.last); line("Released", ctx.year); line("Made by", ctx.developer); line("Kind", ctx.genres)
                    ctx.summary?.takeIf { it.isNotBlank() }?.let { Text(it, color = Color(0xFF34404D), fontSize = 12.sp, lineHeight = 16.sp); Spacer(Modifier.height(6.dp)) }
                    ctx.chips.forEach { a ->
                        SceneControl(a, Modifier) { on ->
                            Text(a.label, color = if (on) blueInk else if (a.urgent) Color(0xFFB0630A) else Color(0xFF5B6B7C), fontSize = 12.sp, maxLines = 2,
                                textDecoration = if (on) androidx.compose.ui.text.style.TextDecoration.Underline else null)
                        }
                    }
                } else ctx.pageBody(Modifier.fillMaxSize())
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Pocket camera: the camera menu
// ---------------------------------------------------------------------------------------------------------------------

/**
 * Pocket camera's window: the camera cartridge's own menu screen in its four shades — a thick pixel frame, the game's
 * photo big on the left (dithered), its name and facts under it, and the commands as a pixel-font list on the right with
 * a ► cursor; the one you're on printed in reverse. Opens with the shutter.
 */
@Composable
internal fun CameraMenu(ctx: WinSceneCtx) {
    val pal = cameraPalette(com.shelf.launcher.data.Features.pcPalette.state.value)
    val c0 = Color(pal[0]); val c1 = Color(pal[1]); val c2 = Color(pal[2]); val c3 = Color(pal[3])
    val font = pocketFont()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val w = minOf(ctx.maxW, 780.dp)
    val h = minOf(ctx.maxH, 480.dp)
    val u = with(density) { kotlin.math.floor((h.toPx() / 120f)).coerceAtLeast(2f).toDp() }
    val fs: (Float) -> androidx.compose.ui.unit.TextUnit = { k -> with(density) { (u * k).toSp() } }
    // (Update 346: the same picture the layout shows — its cover, a screenshot or the art, as picked with ▲ ▼ there; a
    // screenshot / the art is a landscape photo.)
    val src = CameraNav.source
    val url = when (src) {
        1 -> ctx.state.screenshots[ctx.g.stableKey]?.firstOrNull() ?: ctx.cover
        2 -> ctx.backdrop ?: ctx.wide ?: ctx.cover
        else -> ctx.cover
    }
    val land = src != 0 && url != ctx.cover
    val bmp = if (land) rememberDithered(url, 128, 112, pal) else rememberDithered(url, 112, 128, pal)
    Box(
        Modifier.size(w, h).graphicsLayer {
            // (Update 347: the little screen switching on in steps, as the cartridge's would — a dot, a line across, then
            // the picture opening out of the line; switching off runs it back down to the dot.)
            val q = cameraStep(ctx.reveal())
            val line = (q / 0.3f).coerceIn(0f, 1f)
            val open = ((q - 0.3f) / 0.7f).coerceIn(0f, 1f)
            scaleX = 0.02f + 0.98f * line
            scaleY = 0.015f + 0.985f * open
            alpha = if (q <= 0f) 0f else 1f
        }
    ) {
        Canvas(Modifier.fillMaxSize()) { pixelFrame(u.toPx(), c3, c0, 3) }
        Row(Modifier.fillMaxSize().padding(u * 6)) {
            // (Update 513, Retroid Pocket Classic 1240 x 1080 / Nova 4:3: the portrait picture, sized by the window's height, took
            // most of the width and the commands broke inside words — "OPTION / S", "ACHIEV / EMENTS". It keeps its shape but
            // never takes more than 45 % of the window now; on 16:9 it never reached that.)
            Column(if (land) Modifier.fillMaxHeight().fillMaxWidth(0.46f) else Modifier.fillMaxHeight().widthIn(max = w * 0.45f)) {
                val pic = if (land) Modifier.fillMaxWidth().aspectRatio(128f / 112f) else Modifier.weight(1f).aspectRatio(112f / 128f, matchHeightConstraintsFirst = true)
                Box(pic.border(1.dp, c0).background(c2)) {
                    if (bmp != null) androidx.compose.foundation.Image(bmp, ctx.name, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds,
                        filterQuality = androidx.compose.ui.graphics.FilterQuality.None)
                }
                if (land) Spacer(Modifier.weight(1f))
                Spacer(Modifier.height(u * 2))
                // (Batch 522, popup sweep: two lines of the pixel font cut "SYSTEM" off. Whole: smaller, then sliding.)
                WinFullText(ctx.console.uppercase() + (ctx.year?.let { "  $it" } ?: ""), color = c1, fontFamily = font, fontSize = fs(4.5f), maxLines = 2)
                // (Update 346: the time on its own line — beside the achievements it was cut to "TROPHIES 0/…".)
                Text("TIME " + ctx.played.uppercase(), color = c1, fontFamily = font, fontSize = fs(4.5f), maxLines = 2)
            }
            Spacer(Modifier.width(u * 6))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                WinFullText(ctx.name.uppercase(), color = c0, fontFamily = font, fontSize = fs(6.5f), maxLines = 3, lineHeight = fs(8f))
                Spacer(Modifier.height(u))
                Box(Modifier.fillMaxWidth().height(u).background(c0))
                Spacer(Modifier.height(u * 3))
                if (ctx.page == WinPage.MAIN) {
                    Column(Modifier.weight(1f).then(fadeScroll()), verticalArrangement = Arrangement.spacedBy(u)) {
                        val rows = ctx.buttons.filter { !it.back } + ctx.tools + ctx.buttons.filter { it.back }
                        rows.forEach { a ->
                            SceneControl(a, Modifier.fillMaxWidth()) { on ->
                                // (523z, KONKR kp523y CAMERA_OWN: with the Back hint's room taken from the window the pixel unit fell a
                                // step and the rows were 32 px - under the 24 dp touch minimum. Each row keeps at least 24 dp; the list scrolls.)
                                Row(Modifier.fillMaxWidth().heightIn(min = 24.dp).background(if (on) c0 else Color.Transparent).padding(horizontal = u * 2, vertical = u * 0.5f),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Canvas(Modifier.size(u * 7)) { if (on) pixelIcon(PX_CURSOR, Offset.Zero, u.toPx(), c3) }
                                    Spacer(Modifier.width(u * 2))
                                    WholeWordsText(a.label.uppercase(), color = if (on) c3 else if (a.urgent) c1 else c0, fontFamily = font,
                                        fontSize = fs(if (a.primary) 6f else 5f), modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(u * 2))
                    if (ctx.total > 0) Text("TROPHIES ${ctx.earned}/${ctx.total}", color = c1, fontFamily = font, fontSize = fs(4.5f), maxLines = 2)
                } else {
                    Box(Modifier.weight(1f).fillMaxWidth().background(c0)) { ctx.pageBody(Modifier.fillMaxSize().padding(u * 2)) }
                    Spacer(Modifier.height(u * 2))
                    ctx.buttons.forEach { a ->
                        SceneControl(a, Modifier) { on ->
                            Text((if (on) "> " else "  ") + a.label.uppercase(), color = if (on) c0 else c1, fontFamily = font, fontSize = fs(5f), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Panel dashboard: the detail panel
// ---------------------------------------------------------------------------------------------------------------------

/**
 * Panel dashboard's window: one big panel swinging round to face you (it turns in from the angle the row's panels stand
 * at) — the game's wide art across its top with the name over it, its commands as a column of flat rows on the left (the
 * one you're on lit in the dashboard's colour), its cover, facts and achievements on the right.
 */
@Composable
internal fun PanelDetail(ctx: WinSceneCtx) {
    val p = panelPalette()
    val w = minOf(ctx.maxW, 800.dp)
    val h = minOf(ctx.maxH, 500.dp)
    val compact = h < 380.dp
    // (Update 346: on a 16:9 handheld the window is ~440 dp high — the art strip and the rows are sized to fit the list.)
    val short = h < 470.dp
    val ink = p.panelInk
    Box(
        Modifier.size(w, h).graphicsLayer {
            // (Update 347: the panel comes forward out of its row — standing at the row's angle, it turns to face you as it
            // grows, and swings back into the row when it closes.)
            val k = ctx.reveal().coerceIn(0f, 1f)
            val e = 1f - (1f - k) * (1f - k)
            rotationY = (1f - e) * -34f
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            cameraDistance = 12f * density
            translationX = -(1f - e) * size.width * 0.06f
            alpha = (k * 1.6f).coerceIn(0f, 1f)
            // (Update 489, D285: turned text drawn flat — see flatTextFor3d.)
            flatTextFor3d()
        }.clip(RoundedCornerShape(6.dp)).background(p.panel).border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
    ) {
        Column(Modifier.fillMaxSize()) {
            // The art strip with the name.
            Box(Modifier.fillMaxWidth().height(if (compact) 78.dp else if (short) 100.dp else 132.dp).background(p.bottom)) {
                (ctx.backdrop ?: ctx.wide)?.let { SmartArt(it, null, Modifier.fillMaxSize(), request = rememberBackdropRequest(it), maxCrop = SmartFill.maxCropFor(it, ctx.cover)) }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.3f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))))
                Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 18.dp, vertical = 10.dp)) {
                    WinFullText(ctx.name, color = Color.White, fontSize = if (compact) 20.sp else 26.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    Text(ctx.console + (if (ctx.page != WinPage.MAIN) "  ·  " + winPageName(ctx.page) else ""), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, maxLines = 1)
                }
                Box(Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter).background(p.accent))
            }
            Row(Modifier.fillMaxSize().padding(14.dp)) {
                // (Update 488, D284: the rows share the column's height — on the Thor (1080p) Back was cut 15 px at the bottom of the
                // scrolling list. Each row as tall as the design's when there is room, shorter (down to 26 dp) when not; the list
                // only scrolls when even that doesn't fit.)
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(0.45f).fillMaxHeight()) {
                val rows = ctx.buttons.filter { !it.back } + (if (ctx.page == WinPage.MAIN) ctx.tools else emptyList()) + ctx.buttons.filter { it.back }
                val primaryH = if (short) 38.dp else 44.dp
                val nPrimary = rows.count { it.primary }
                val restH = fitRowHeight(maxHeight - primaryH * nPrimary - 2.dp * nPrimary, (rows.size - nPrimary).coerceAtLeast(1), 2.dp, if (short) 30.dp else 36.dp, 26.dp)
                Column(Modifier.fillMaxSize().then(if (restH == null) fadeScroll() else Modifier), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    rows.forEach { a ->
                        SceneControl(a, Modifier.fillMaxWidth()) { on ->
                            Row(
                                // (Update 467, popup gate: Back's last 3 dp were cut on the KONKR — short rows a little shorter.)
                                Modifier.fillMaxWidth().height(if (a.primary) primaryH else restH ?: if (short) 30.dp else 36.dp)
                                    .background(if (on) lerp(p.accent, Color.Black, 0.28f) else Color.Transparent).padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(a.icon, null, tint = if (on) Color.White else ink.copy(alpha = 0.75f), modifier = Modifier.size(if (a.primary) 22.dp else 18.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(a.label, color = if (on) Color.White else if (a.urgent) Color(0xFFC0700A) else ink, fontSize = if (a.primary) 18.sp else if (short) 14.sp else 15.sp,
                                    fontWeight = if (a.primary || on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 2)
                            }
                        }
                    }
                }
                }
                Spacer(Modifier.width(14.dp))
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(0.55f).fillMaxHeight()) {
                    // (Update 513, Retroid Pocket Classic: the cover, sized by the height, left the facts ~55 dp — "Neve / r /
                    // playe". It keeps its shape but takes at most 60 % of this side; on 16:9 screens it never reached that.)
                    // (Sized here, not with aspectRatio under a width cap: that overflowed its slot and drew over the facts.)
                    val coverW = minOf(maxHeight * 0.72f, maxWidth * 0.6f)
                    if (ctx.page == WinPage.MAIN) Row(Modifier.fillMaxSize()) {
                        Box(Modifier.width(coverW).height(coverW / 0.72f).background(Color.Black.copy(alpha = 0.1f))) {
                            if (ctx.cover != null) SmartArt(ctx.cover, ctx.name, Modifier.fillMaxSize(), request = rememberCoverRequest(ctx.cover))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f).then(fadeScroll())) {
                            @Composable
                            fun fact(label: String, value: String?) {
                                if (value.isNullOrBlank()) return
                                Text(label, color = ink.copy(alpha = 0.6f), fontSize = 11.sp)
                                WholeWordsText(value, color = ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(6.dp))
                            }
                            if (ctx.total > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.EmojiEvents, null, tint = p.accent, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("${ctx.earned} / ${ctx.total}", color = ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                Box(Modifier.padding(vertical = 5.dp).fillMaxWidth().height(5.dp).background(ink.copy(alpha = 0.12f))) {
                                    Box(Modifier.fillMaxHeight().fillMaxWidth((ctx.earned.toFloat() / ctx.total).coerceIn(0f, 1f)).background(p.accent))
                                }
                                Spacer(Modifier.height(4.dp))
                            }
                            fact("Played", ctx.played); fact("Last played", ctx.last); fact("Released", ctx.year); fact("Made by", ctx.developer); fact("Kind", ctx.genres)
                            ctx.chips.forEach { a ->
                                SceneControl(a, Modifier) { on ->
                                    Text(a.label, color = if (on) p.accent else if (a.urgent) Color(0xFFC0700A) else ink.copy(alpha = 0.65f), fontSize = 12.sp, maxLines = 2,
                                        textDecoration = if (on) androidx.compose.ui.text.style.TextDecoration.Underline else null)
                                }
                            }
                        }
                    } else Box(Modifier.fillMaxSize().border(1.dp, ink.copy(alpha = 0.18f))) { ctx.pageBody(Modifier.fillMaxSize()) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Game magazine: the torn-out page
// ---------------------------------------------------------------------------------------------------------------------

/**
 * Game magazine's window: the feature torn out of the issue and pinned up — a page with a torn left edge, a little crooked,
 * the cover taped to it, the headline in the issue's type, and its commands as the magazine's callouts: PLAY in a burst,
 * the others as "▸ SEE PAGE" lines; the one you're on circled in marker.
 */
@Composable
internal fun MagazinePage(ctx: WinSceneCtx) {
    val st = magStyle(com.shelf.launcher.data.Features.mgStyle.state.value)
    val w = minOf(ctx.maxW, 720.dp)
    val h = minOf(ctx.maxH, 500.dp)
    val compact = h < 380.dp
    Box(
        // (Update 470, QA only: the page settles turned 2° — its rows' straight boxes cross by a few px though the turned rows
        // don't (k470f: laid-out boxes 2 px apart). Marked like the rental tag; the tap test checks the crossing strip.)
        Modifier.size(w, h).then(if (QaTrace.on) Modifier.semantics { this[QaClip.Tilted] = true } else Modifier).graphicsLayer {
            // (Update 347: the torn page is lifted off the issue — it comes up tilted back from its bottom edge, turning,
            // and settles crooked on top; closing lays it back down.)
            val k = ctx.reveal().coerceIn(0f, 1f)
            val e = 1f - (1f - k) * (1f - k)
            rotationX = (1f - e) * 42f
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
            cameraDistance = 16f * density
            rotationZ = -2f + (1f - e) * -9f
            translationY = (1f - e) * size.height * 0.12f
            alpha = (k * 1.8f).coerceIn(0f, 1f)
            // (Update 489, D285: turned text drawn flat — see flatTextFor3d.)
            flatTextFor3d()
        }
    ) {
        // The paper with its torn edge.
        Canvas(Modifier.fillMaxSize()) {
            val tear = Path().apply {
                moveTo(18f, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height); lineTo(18f, size.height)
                var y = size.height; var i = 0
                while (y > 0f) { y -= 14f; lineTo(if (i % 2 == 0) 4f else 18f + (i % 3) * 4f, y.coerceAtLeast(0f)); i++ }
                close()
            }
            translate(left = 8f, top = 10f) { drawPath(tear, Color.Black.copy(alpha = 0.35f)) }
            drawPath(tear, st.paper)
        }
        Row(Modifier.fillMaxSize().padding(start = 36.dp, end = 18.dp, top = 16.dp, bottom = 14.dp)) {
            // The cover, taped on.
            Box(Modifier.fillMaxHeight(0.78f).aspectRatio(0.72f, matchHeightConstraintsFirst = true).graphicsLayer { rotationZ = 3f }) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f))) {
                    if (ctx.cover != null) SmartArt(ctx.cover, ctx.name, Modifier.fillMaxSize(), request = rememberCoverRequest(ctx.cover))
                }
                Box(Modifier.align(Alignment.TopCenter).offset(y = (-8).dp).size(60.dp, 18.dp).graphicsLayer { rotationZ = -6f }.background(Color(0x99F3E9C6)))
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text(st.masthead + "  ·  " + (if (ctx.page == WinPage.MAIN) "FEATURE" else winPageName(ctx.page).uppercase()), color = st.accent, fontFamily = st.head,
                    fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp)
                // (Update 424, rule 11: the headline wraps and shrinks a step when long; update 478, D271: then slides — it was cut.)
                WinFullText(ctx.name.uppercase(), color = st.ink, fontFamily = st.head, fontWeight = st.headWeight, fontSize = if (compact) 20.sp else 26.sp, lineHeight = if (compact) 22.sp else 28.sp,
                    maxLines = 3, style = androidx.compose.material3.LocalTextStyle.current.copy(
                        fontStyle = if (st.italicHead) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal))
                Text(listOfNotNull(ctx.console, ctx.year, ctx.developer).joinToString("  ·  "), color = st.muted, fontFamily = st.body, fontSize = 11.sp, maxLines = 2)
                Box(Modifier.padding(vertical = 6.dp).fillMaxWidth().height(2.dp).background(st.ink))
                if (ctx.page == WinPage.MAIN) {
                    Column(Modifier.weight(1f).then(fadeScroll()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ctx.buttons.filter { it.primary }.forEach { a ->
                            SceneControl(a, Modifier) { on ->
                                Box(Modifier.size(if (compact) 88.dp else 112.dp, if (compact) 42.dp else 60.dp), contentAlignment = Alignment.Center) {
                                    Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = if (on) -4f else 0f; val s = if (on) 1.08f else 1f; scaleX = s; scaleY = s }) {
                                        val p = Path(); val n = 16
                                        for (i in 0 until n * 2) {
                                            val rx = if (i % 2 == 0) size.width / 2f else size.width / 2.5f
                                            val ry = if (i % 2 == 0) size.height / 2f else size.height / 2.5f
                                            val a2 = Math.PI * i / n
                                            val x = size.width / 2f + (rx * kotlin.math.cos(a2)).toFloat(); val y = size.height / 2f + (ry * kotlin.math.sin(a2)).toFloat()
                                            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                                        }
                                        p.close(); drawPath(p, if (on) st.accent else st.accent2)
                                    }
                                    Text(magBurstLabel(a.label), color = if (on) Color.White else st.ink, fontFamily = st.head, fontWeight = FontWeight.Black, fontSize = if (compact) 13.sp else 16.sp)
                                }
                            }
                        }
                        // (Update 346: the "see page" callouts run in two columns, down then across, as a contents box would —
                        // in one column the last ones ran off the bottom of the page on a 16:9 handheld.)
                        val callouts = ctx.buttons.filter { !it.primary && !it.back } + ctx.tools + ctx.buttons.filter { it.back }
                        val half = (callouts.size + 1) / 2
                        // (Update 470: a gutter between the two columns — at font 1.3 a wrapped callout reached the next column's edge.)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (col in listOf(callouts.take(half), callouts.drop(half))) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    col.forEach { a ->
                                        SceneControl(a, Modifier) { on ->
                                            // (Update 470, rule 11: at font 1.3 on the KONKR "▸ ACHIEVEMENTS" broke into "▸ / ACHIEVEMENT / S" with
                                            // the S on a hidden third line — a callout now shrinks a step or two to fit its two lines.)
                                            // (Update 471: the arrow stays beside the word — 470 left "▸" alone on a line above ACHIEVEMENTS — and
                                            // the word steps down a size rather than break; see WholeWordsText.)
                                            val cs = if (compact) 12.sp else 14.sp
                                            val ink = if (a.urgent) st.accent else st.ink
                                            Row(Modifier.then(if (on) Modifier.border(2.dp, st.accent, androidx.compose.foundation.shape.RoundedCornerShape(50)) else Modifier)
                                                .padding(horizontal = 10.dp, vertical = if (compact) 1.dp else 3.dp), verticalAlignment = Alignment.Top) {
                                                Text("▸ ", color = ink, fontFamily = st.head, fontWeight = FontWeight.Bold, fontSize = cs)
                                                WholeWordsText(a.label.uppercase(), color = ink, fontSize = cs, fontWeight = FontWeight.Bold, fontFamily = st.head)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Text(listOfNotNull(if (ctx.playtime == null && ctx.lastPlayed == null) "NOT PLAYED YET" else "PLAYED " + ctx.played.uppercase(), if (ctx.total > 0) "ACHIEVEMENTS ${ctx.earned}/${ctx.total}" else null).joinToString("   "),
                        color = st.muted, fontFamily = st.head, fontSize = 10.sp, letterSpacing = 1.sp, maxLines = 2)
                } else {
                    Box(Modifier.weight(1f).fillMaxWidth().border(1.dp, st.ink.copy(alpha = 0.35f))) { ctx.pageBody(Modifier.fillMaxSize()) }
                    ctx.buttons.forEach { a ->
                        SceneControl(a, Modifier) { on ->
                            Text("▸ " + a.label.uppercase(), color = if (on) st.accent else st.ink, fontFamily = st.head, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Rental shop: the rental tag
// ---------------------------------------------------------------------------------------------------------------------

/**
 * Rental shop's window, beside the case you picked up (GameWindow brings the case off the shelf to be turned): the shop's
 * bright yellow rental tag — its name in heavy black capitals, the console, its genre and maker (update 346: no made-up
 * rental period or due date — only the game's own facts; "BE KIND, PLAY IT AGAIN" is the shop's printed slogan), time
 * played and achievements, a barcode, a perforated tear-off; the commands as black punched keys, the one you're on
 * lit in the shop's neon.
 */
@Composable
internal fun RentalTag(ctx: WinSceneCtx) {
    val yellow = Color(0xFFFFE14A); val ink = Color(0xFF1C1C1C)
    // (Update 505: the key you're on lit in the shop's own neon — the wall you picked (purple / teal / blue), or your accent when
    // this layout has its own colours — instead of always the purple wall's pink. The tag stays the shop's yellow paper.)
    val neon = sceneLit(com.shelf.launcher.data.HomeLayout.SHOP, shopColors(com.shelf.launcher.data.Features.rsWall.state.value).neon, ink)
    val onNeon = com.shelf.launcher.ui.theme.textOn(neon, ink)
    val stamp = legibleOn(neon, yellow)
    val w = minOf(ctx.maxW, 460.dp)
    val h = minOf(ctx.maxH, 520.dp)
    // (Update 346: a short screen gets a short tag — one line of facts, the title on one line, a slimmer tear-off.)
    // (Update 469, D214: with larger text (1.3×) on a short screen — the KONKR — the title and facts grew and pushed BACK under
    // the tag's foot. Then the tag takes its short-screen layout: one line of facts, a two-line title, three keys across.)
    // (Update 469: 480 at 1.3× — the Odin's 445 dp screen with its extra Performance key cut BACK under the tag's foot.)
    val short = h < 380.dp || (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.1f && h < 480.dp)
    // (Update 469, owner review: the tag was always as tall as the screen allows — the keys sat at the top and a large yellow
    // field stayed empty under them. The tag is now as tall as its content (up to the screen), the tear-off a fixed strip.)
    val tearDp = if (short) 44.dp else 64.dp
    Box(
        // (Update 467, QA only: the tag hangs tilted — its rows' boxes cross a little — marked so the geometry gate allows it.)
        Modifier.width(w).heightIn(max = h).then(if (QaTrace.on) Modifier.semantics { this[QaClip.Tilted] = true } else Modifier).graphicsLayer {
            // (Update 347: the tag swings in on the string through its hole as the case comes off the shelf — past
            // straight and back, once — and swings away as the case goes back.)
            val k = ctx.reveal().coerceIn(0f, 1f)
            rotationZ = 2f + tagSwing(k) * 26f
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.04f)
            translationX = (1f - k) * (1f - k) * size.width * 0.35f
            alpha = (k * 2f).coerceIn(0f, 1f)
        }
    ) {
        Canvas(Modifier.matchParentSize()) {
            translate(left = 8f, top = 10f) { drawRect(Color.Black.copy(alpha = 0.35f)) }
            drawRect(yellow)
            // The hole it hung from and the perforation above the tear-off.
            drawCircle(ink, 9f, Offset(size.width / 2f, 22f))
            val t = tearDp.toPx()
            val perf = size.height - t + 0.13f * t
            var x = 10f
            while (x < size.width - 10f) { drawCircle(ink.copy(alpha = 0.6f), 2.5f, Offset(x, perf)); x += 12f }
            // The barcode.
            var bx = 18f; var i = 0
            while (bx < size.width * 0.6f) { val bw = if (i % 3 == 0) 6f else 3f; drawRect(ink, Offset(bx, perf + 0.17f * t), Size(bw, 0.55f * t)); bx += bw + 3f + (i % 2) * 2f; i++ }
        }
        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = if (short) 26.dp else 34.dp, bottom = tearDp)) {
            if (!short) Text("GAME RENTALS", color = ink, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            WinFullText(ctx.name.uppercase(), color = ink, fontSize = if (short) 18.sp else 22.sp, lineHeight = if (short) 20.sp else 24.sp, fontWeight = FontWeight.Black,
                maxLines = if (short) 2 else 3)
            Text(ctx.console.uppercase() + (ctx.year?.let { "  ·  $it" } ?: ""), color = ink.copy(alpha = 0.75f), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Box(Modifier.padding(vertical = if (short) 4.dp else 6.dp).fillMaxWidth().height(3.dp).background(ink))
            if (ctx.page == WinPage.MAIN) {
                // (Update 346: the facts two by two, label over value, so the keys below fit on the tag.)
                val facts = listOfNotNull(ctx.genres?.let { "GENRE" to it }, ctx.developer?.let { "MADE BY" to it }, "PLAYED" to ctx.played,
                    if (ctx.total > 0) "TROPHIES" to "${ctx.earned} / ${ctx.total}" else null)
                // (Update 347: on a short tag the facts are one line, so every key fits on the tag without scrolling.)
                if (short) Text(facts.takeLast(2).joinToString("   ·   ") { (l, v) -> "$l ${v.uppercase()}" }, color = ink, fontSize = 10.sp, fontWeight = FontWeight.Black,
                    maxLines = 2)
                else facts.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 3.dp)) {
                        pair.forEach { (l, v) ->
                            Column(Modifier.weight(1f).padding(end = 6.dp)) {
                                Text(l, color = ink.copy(alpha = 0.65f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1)
                                Text(v.uppercase(), color = ink, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Black, maxLines = 2)
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(if (short) 4.dp else 8.dp))
                // (Update 467, popup gate: on the KONKR the last row — BACK — ended 10 dp under the tag's foot; short tags
                // space the rows closer and make them 24 dp, still a whole touch size.)
                Column(Modifier.weight(1f, fill = false).then(fadeScroll()), verticalArrangement = Arrangement.spacedBy(if (h < 440.dp) 2.dp else 4.dp)) {
                    @Composable
                    fun key(a: WinAction, m: Modifier) {
                        SceneControl(a, m) { on ->
                            Row(Modifier.fillMaxWidth().height(if (a.primary) (if (h < 440.dp) 30.dp else 38.dp) else if (h < 440.dp) 24.dp else 30.dp).background(if (on) neon else ink).padding(horizontal = if (a.primary) 10.dp else 6.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                // (Narrow keys on a narrow tag: the words, no icon, so they read in full.)
                                val tight = !a.primary && w < 340.dp
                                if (!tight) {
                                    Icon(a.icon, null, tint = if (on) onNeon else yellow, modifier = Modifier.size(if (a.primary) 16.dp else 12.dp))
                                    Spacer(Modifier.width(if (a.primary) 8.dp else 4.dp))
                                }
                                // (Update 346: the window's look already says "RENT & PLAY" — it read "RENT & RENT & PLAY".)
                                val label = rentalKeyLabel(a.label, a.primary)
                                Text(label, color = if (on) onNeon else if (a.urgent) Color(0xFFFF8A3D) else yellow,
                                    fontSize = if (a.primary) 15.sp else if (tight) 9.sp else 10.sp, fontWeight = FontWeight.Black, maxLines = 2)
                            }
                        }
                    }
                    // Play across the tag; the others as a block of punched keys, two by two, so they all fit on the tag.
                    ctx.buttons.filter { it.primary }.forEach { key(it, Modifier.fillMaxWidth()) }
                    val rest = ctx.buttons.filter { !it.primary && !it.back } + ctx.tools + ctx.buttons.filter { it.back }
                    val across = if (w >= 340.dp || short) 3 else 2
                    rest.chunked(across).forEach { group ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            group.forEach { key(it, Modifier.weight(1f)) }
                            repeat(across - group.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    ctx.chips.forEach { a ->
                        SceneControl(a, Modifier) { on ->
                            Text(a.label.uppercase(), color = if (on) stamp else ink.copy(alpha = 0.75f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                        }
                    }
                }
            } else {
                Box(Modifier.weight(1f, fill = false).fillMaxWidth().heightIn(min = (h * 0.45f).coerceAtMost(260.dp)).border(2.dp, ink)) { ctx.pageBody(Modifier.fillMaxSize()) }
                Spacer(Modifier.height(4.dp))
                ctx.buttons.forEach { a ->
                    SceneControl(a, Modifier) { on ->
                        Text("▸ " + a.label.uppercase(), color = if (on) stamp else ink, fontSize = 13.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Terminal: the RUN dialog
// ---------------------------------------------------------------------------------------------------------------------

/**
 * Terminal's window: a DOS-style dialog drawn in the monitor's own colours — a double-line frame with its title in the
 * top border, the game's file (TYPE …NFO: its facts and a few lines of its story) on the left, the commands as a menu on
 * the right with the one you're on in reverse video and a ► cursor. Pops up line by line.
 */
@Composable
internal fun TerminalDialog(ctx: WinSceneCtx) {
    val tc = termColors(com.shelf.launcher.data.Features.tmTheme.state.value)
    val font: androidx.compose.ui.text.font.FontFamily = if (com.shelf.launcher.data.Features.tmPixel.state.value) pocketFont() else androidx.compose.ui.text.font.FontFamily.Monospace
    val w = minOf(ctx.maxW, 760.dp)
    val h = minOf(ctx.maxH, 380.dp)
    val compact = h < 340.dp
    val fs = if (compact) 12.sp else 14.sp
    val glow = com.shelf.launcher.data.Features.tmCrt.state.value != com.shelf.launcher.data.Features.TmCrt.OFF
    val style = androidx.compose.ui.text.TextStyle(fontFamily = font, fontSize = fs, color = tc.fg,
        shadow = if (glow) androidx.compose.ui.graphics.Shadow(tc.fg.copy(alpha = 0.7f), Offset.Zero, 8f) else null)
    Box(
        Modifier.size(w, h).graphicsLayer {
            // (Update 347: the box draws itself — the double frame runs out from the top centre, down the sides and along
            // the bottom; then the text comes up line by line; closing takes it away in reverse.)
            val k = ctx.reveal().coerceIn(0f, 1f)
            alpha = if (k < 0.02f) 0f else 1f
        }
    ) {
        val k = ctx.reveal().coerceIn(0f, 1f)
        Canvas(Modifier.fillMaxSize()) {
            val f = termFrame(k)
            // The box's ground fills in behind the frame as it goes round.
            drawRect(tc.bg, alpha = (f.fill).coerceIn(0f, 1f))
            for ((inset, sw) in listOf(6f to 2f, 11f to 2f)) {
                val l = inset; val t = inset; val r = size.width - inset; val b = size.height - inset
                val cx = size.width / 2f
                // top, from the middle out
                drawLine(tc.fg, Offset(cx - (cx - l) * f.top, t), Offset(cx + (r - cx) * f.top, t), sw)
                // sides, down
                if (f.sides > 0f) { drawLine(tc.fg, Offset(l, t), Offset(l, t + (b - t) * f.sides), sw); drawLine(tc.fg, Offset(r, t), Offset(r, t + (b - t) * f.sides), sw) }
                // bottom, in to the middle
                if (f.bottom > 0f) { drawLine(tc.fg, Offset(l, b), Offset(l + (cx - l) * f.bottom, b), sw); drawLine(tc.fg, Offset(r, b), Offset(r - (r - cx) * f.bottom, b), sw) }
            }
        }
        Text(" RUN " + TermNav.alias(ctx.g) + " ", style = style.copy(color = tc.hiInk, shadow = null), modifier = Modifier.align(Alignment.TopCenter)
            .graphicsLayer { alpha = if (termFrame(ctx.reveal()).top >= 0.6f) 1f else 0f }.background(tc.hi))
        Row(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 22.dp).drawWithContent {
            // The lines come up one at a time from the top (a whole text line at a step).
            val shown = termFrame(k).text
            if (shown <= 0f) return@drawWithContent
            val lineH = fs.toPx() * 1.35f
            val lines = kotlin.math.ceil(size.height / lineH)
            val cut = kotlin.math.floor(shown * lines) * lineH
            clipRect(0f, 0f, size.width, cut) { this@drawWithContent.drawContent() }
        }) {
            Column(Modifier.weight(0.55f).fillMaxHeight().then(fadeScroll())) {
                Text("C:\\> TYPE " + TermNav.alias(ctx.g) + ".NFO", style = style.copy(color = tc.dim))
                Spacer(Modifier.height(4.dp))
                WinFullText(ctx.name.uppercase(), color = tc.accent, fontSize = style.fontSize, lineHeight = style.lineHeight, maxLines = 3, style = style)
                @Composable
                fun line(l: String, v: String?) { if (!v.isNullOrBlank()) Text(l.padEnd(9) + v.uppercase(), style = style, maxLines = 2) }
                line("SYSTEM", ctx.console); line("YEAR", ctx.year); line("MAKER", ctx.developer); line("PLAYED", ctx.played); line("LAST", ctx.last)
                if (ctx.total > 0) {
                    val filled = ((ctx.earned.toFloat() / ctx.total) * 12).toInt().coerceIn(0, 12)
                    Text("TROPHY".padEnd(9) + "[" + "#".repeat(filled) + ".".repeat(12 - filled) + "] ${ctx.earned}/${ctx.total}", style = style, maxLines = 1)
                }
                if (ctx.page == WinPage.MAIN && !compact) ctx.summary?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it.uppercase(), style = style.copy(color = tc.dim, fontSize = fs * 0.85f), maxLines = 7)
                }
                if (ctx.page != WinPage.MAIN) Box(Modifier.fillMaxWidth().height(h * 0.5f).padding(top = 6.dp).border(1.dp, tc.dim)) { ctx.pageBody(Modifier.fillMaxSize()) }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(0.45f).fillMaxHeight().then(fadeScroll())) {
                val rows = ctx.buttons.filter { !it.back } + (if (ctx.page == WinPage.MAIN) ctx.tools else emptyList()) + ctx.buttons.filter { it.back }
                rows.forEach { a ->
                    SceneControl(a, Modifier.fillMaxWidth()) { on ->
                        Text((if (on) "► " else "  ") + a.label.uppercase(), style = if (on) style.copy(color = tc.hiInk, shadow = null) else style.copy(color = if (a.urgent) tc.accent else tc.fg),
                            // (Update 467, popup gate: 2 dp rows were 20 dp tall — under the 24 dp touch size; 4 dp now.)
                            maxLines = 2, modifier = Modifier.fillMaxWidth().background(if (on) tc.hi else Color.Transparent).padding(horizontal = 4.dp, vertical = 4.dp))
                    }
                }
                if (ctx.page == WinPage.MAIN) ctx.chips.forEach { a ->
                    SceneControl(a, Modifier.fillMaxWidth()) { on ->
                        // (Update 467: the status lines are keys too — 13 dp tall on the Odin; whole touch size now.)
                        Text("  " + a.label.uppercase(), style = style.copy(color = if (on) tc.accent else tc.dim, fontSize = fs * 0.8f), maxLines = 2,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp).wrapContentHeight(Alignment.CenterVertically))
                    }
                }
            }
        }
    }
}
