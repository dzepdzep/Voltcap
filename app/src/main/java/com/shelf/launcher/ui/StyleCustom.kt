package com.shelf.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.shelf.launcher.data.CustomLayouts
import com.shelf.launcher.data.CustomScene
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.SceneEnter
import com.shelf.launcher.data.SceneLoop
import com.shelf.launcher.data.ScenePart
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Update 176: Custom layout — draws a layout made by anyone (CustomLayouts.kt, docs\CUSTOM_LAYOUTS.md): its parts placed
 * by share of the screen, each with its look, a loop that keeps going and an entrance played when you move to another
 * game. ◀ ▶ (▲ ▼ for a column) move through the games, ▼ (▶ for a column) goes to its Play / Details buttons.
 */

/** The custom layout on screen: what the key handler needs, and a count that rises when the layouts change. */
internal object CustomSceneNow {
    @Volatile var direction = "row"
    @Volatile var buttons = false
    /** Raised after an import / delete, so the layout on screen is read again. */
    var version by mutableIntStateOf(0)
    /** The layout last drawn (for [styleOwnsBackground], read outside composition). */
    var scene by mutableStateOf(CustomLayouts.example())
}

/** The custom layout picked in Settings (the example when none / gone). */
@Composable
internal fun rememberCustomScene(): CustomScene {
    val context = LocalContext.current.applicationContext
    val id = Features.customLayout.state.value
    val v = CustomSceneNow.version
    return remember(id, v) { runCatching { CustomLayouts.load(context, id) }.getOrElse { CustomLayouts.example() } }
}

@Composable
internal fun StyleCustom(c: StyleCtx, modifier: Modifier = Modifier) {
    val scene = rememberCustomScene()
    val g = c.game
    SideEffect {
        if (CustomSceneNow.scene !== scene) CustomSceneNow.scene = scene
        CustomSceneNow.direction = scene.games?.direction ?: "row"
        CustomSceneNow.buttons = scene.hasButtons
        StyleNav.side = emptyList()
        StyleNav.top = emptyList()
        // ▼ (▶ in a column): Play, then Details.
        StyleNav.bar = if (g == null || !scene.hasButtons) emptyList() else listOf({ c.actions.play(g) }, { c.actions.open() })
    }
    if (g == null) StyleEmpty(modifier) else BoxWithConstraints(modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        scene.parts.forEachIndexed { i, p ->
            key(scene.id, i) { ScenePartBox(c, scene, p, g, w, h) }
        }
    }
}

/** ◀ ▶ are the home screen's own (previous / next game); a column moves on ▲ ▼. */
internal fun customLayoutKey(ctx: StyleKeyCtx): Boolean {
    val column = CustomSceneNow.direction == "column"
    fun buttons(): Boolean = if (CustomSceneNow.buttons && StyleNav.bar.isNotEmpty()) {
        StyleNav.forget(StyleNav.Zone.BAR)
        ctx.enterZone(StyleNav.Zone.BAR)
    } else false
    fun step(d: Int): Boolean {
        val next = ctx.focus + d
        return if (next in 0 until ctx.count) {
            ctx.setFocus(next)
            ctx.sound(UiSound.MOVE)
            ctx.take()
        } else ctx.reach(d < 0)
    }
    return when (ctx.key) {
        Key.DirectionUp -> if (column) step(-1) else ctx.reach(true)
        Key.DirectionDown -> if (column) step(1) else buttons() || ctx.reach(false)
        Key.DirectionRight -> if (column) buttons() || ctx.take() else false
        Key.DirectionLeft -> if (column) ctx.take() else false
        else -> false
    }
}

/** What the custom layout draws over the whole screen, behind everything: its colour, the game's art, its picture. */
@Composable
internal fun CustomBackLayer(gameKey: String?, artUrl: String?) {
    val scene = rememberCustomScene()
    val bg = scene.background
    Box(Modifier.fillMaxSize()) {
        val fill = sceneBrush(bg.gradient, bg.color)
        if (fill != null) Box(Modifier.fillMaxSize().background(fill))
        if (bg.gameArt) {
            DualGridArt(gameKey = gameKey, url = artUrl)
            if (bg.shade > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = bg.shade)))
        }
        scene.file(bg.image)?.let { f ->
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
                Box(Modifier.fillMaxSize().sceneLoop(bg.loop, w, h)) { ScenePicture(f, bg.fit, Modifier.fillMaxSize()) }
            }
        }
    }
}

/** Whether the custom layout paints its own background (then the full-screen video preview stays off). */
internal fun customOwnsBackground(scene: CustomScene): Boolean =
    scene.background.color != null || scene.background.gradient.isNotEmpty() || scene.background.image != null

// ---- One part ----

@Composable
private fun ScenePartBox(c: StyleCtx, scene: CustomScene, p: ScenePart, g: Game, w: Float, h: Float) {
    val density = LocalDensity.current
    val pw = p.w * w; val ph = p.h * h
    val wDp = with(density) { pw.toDp() }; val hDp = with(density) { ph.toDp() }
    val enterKey: Any = if (p.enter?.on == "move") g.stableKey else Unit
    Box(
        Modifier
            .offset { IntOffset((p.x * w).roundToInt(), (p.y * h).roundToInt()) }
            .size(wDp, hDp)
            .sceneEnter(p.enter, enterKey, w, h)
            .sceneLoop(p.loop, w, h)
            .graphicsLayer { alpha = p.alpha; rotationZ = p.rotate }
    ) {
        when (p.type) {
            "games" -> SceneGames(c, p, pw, ph)
            "art" -> SceneArt(c, p, g, h)
            "image" -> scene.file(p.src)?.let { ScenePicture(it, p.fit, Modifier.fillMaxSize().clip(cornerShape(p))) }
            "shape" -> SceneShape(p)
            "buttons" -> SceneButtons(c, p, g, ph)
            else -> SceneText(c, p, g, h)
        }
    }
}

/** A picture from the layout's own files (animated GIF / WebP play). */
@Composable
private fun ScenePicture(file: java.io.File, fit: String, modifier: Modifier) {
    val context = LocalContext.current
    val req = remember(file.absolutePath, file.lastModified()) {
        coil.request.ImageRequest.Builder(context).data(file)
            .setParameter(com.shelf.launcher.data.AnimatedArt.PARAM_ANIMATE, true, memoryCacheKey = "anim")
            .build()
    }
    AsyncImage(req, null, contentScale = fitScale(fit), modifier = modifier)
}

private fun fitScale(fit: String): ContentScale = when (fit) {
    "fit" -> ContentScale.Fit
    "stretch" -> ContentScale.FillBounds
    else -> ContentScale.Crop
}

private fun cornerShape(p: ScenePart): Shape = when (p.shape) {
    "circle" -> CircleShape
    "rect" -> RectangleShape
    else -> RoundedCornerShape(p.corner.coerceAtLeast(0f).dp)
}

@Composable
private fun SceneShape(p: ScenePart) {
    val shape = if (p.type == "shape") cornerShape(p) else RoundedCornerShape(p.corner.dp)
    val fill = sceneBrush(p.gradient, p.color) ?: Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
    val edge = p.border?.let { sceneColor(it) }
    Box(
        Modifier.fillMaxSize().clip(shape).background(fill)
            .then(if (edge != null && p.borderWidth > 0f) Modifier.border(p.borderWidth.dp, edge, shape) else Modifier)
    )
}

/** The game you're on: its cover, wide art, backdrop, logo, screenshot or card. */
@Composable
private fun SceneArt(c: StyleCtx, p: ScenePart, g: Game, h: Float) {
    val shape = RoundedCornerShape(p.corner.coerceAtLeast(0f).dp)
    when (p.art) {
        "card" -> StyleArt(c, g, Modifier.fillMaxSize(), corner = p.corner.dp)
        else -> {
            val url: Any? = when (p.art) {
                "cover" -> coverOrAppIcon(g, c.cover(g))
                "wide" -> c.wide(g)
                "logo" -> c.logo(g)
                "screenshot" -> c.state.screenshots[g.stableKey]?.firstOrNull() ?: c.backdrop(g)
                else -> c.backdrop(g) ?: c.wide(g)
            }
            if (url == null && p.art == "logo") {
                // No logo: the game's name in its place.
                SceneText(c, p.copy(type = "title", size = (p.h * 0.32f).coerceAtMost(0.09f), align = if (p.align == "start") "center" else p.align, bold = true), g, h)
            } else if (url == null) {
                Box(Modifier.fillMaxSize().clip(shape).background(placeholderBrush(g.hue)))
            } else {
                HeldImage(url, Modifier.fillMaxSize().clip(shape), contentScale = fitScale(p.fit), imageModifier = Modifier.pixelArtwork())
            }
        }
    }
}

/** Play / Continue and Details. */
@Composable
private fun SceneButtons(c: StyleCtx, p: ScenePart, g: Game, ph: Float) {
    val density = LocalDensity.current
    val labels = listOf(
        p.labels.getOrNull(0) ?: if (c.lastPlayed(g) != null) "Continue" else "Play",
        p.labels.getOrNull(1) ?: "Details"
    )
    val fill = sceneColor(p.color ?: "accent")
    val ink = sceneColor(p.textColor ?: "#FFFFFF")
    val fontSp = with(density) { (ph * 0.36f).toSp() }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        labels.forEachIndexed { i, label ->
            val on = StyleNav.on(StyleNav.Zone.BAR, i)
            val shape = RoundedCornerShape(50)
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .graphicsLayer { val s = if (on) 1.06f else 1f; scaleX = s; scaleY = s }
                    .clip(shape)
                    .background(if (i == 0) fill else fill.copy(alpha = fill.alpha * 0.35f))
                    .then(if (on) Modifier.border(2.dp, Color.White, shape) else Modifier)
                    .clickable { if (i == 0) c.actions.play(g) else c.actions.open() },
                contentAlignment = Alignment.Center
            ) {
                FullText(label, color = ink, fontSize = fontSp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

// ---- Text ----

@Composable
private fun SceneText(c: StyleCtx, p: ScenePart, g: Game, h: Float) {
    val titles = LocalTitleOptions.current
    val density = LocalDensity.current
    val raw = when (p.type) {
        "title" -> p.text ?: "{name}"
        "clock" -> p.text ?: "{time}"
        "category" -> p.text ?: "{category}"
        "info" -> p.show.joinToString("  ·  ") { "{$it}" }
        else -> p.text ?: ""
    }
    // (Update 212: a text showing the category is the layout's own category title, CategoryTitle.kt.)
    if ("{category}" in raw) CategoryTitle.Native()
    val ticking = "{time}" in raw || "{date}" in raw
    val now = rememberSceneNow(ticking)
    val context = LocalContext.current
    val text = remember(raw, g.stableKey, now, c.focus, c.games.size, c.categoryLabel) {
        fun tok(t: String): String = when (t) {
            "name" -> titles.display(g.name)
            "console" -> titles.console(g.platform)
            "playtime" -> stylePlaytime(c.playtime(g), c.lastPlayed(g) != null)
            "lastPlayed" -> c.lastPlayed(g)?.let { "Last played " + android.text.format.DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), android.text.format.DateUtils.DAY_IN_MILLIS) } ?: "Not played yet"
            "achievements" -> c.progress(g)?.let { (e, t) -> "$e / $t achievements" } ?: ""
            "category" -> c.categoryLabel
            "position" -> "${c.focus + 1} / ${c.games.size}"
            "count" -> "${c.games.size} games"
            "time" -> android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(now))
            "date" -> java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(now))
            else -> "{$t}"
        }
        val out = Regex("\\{([A-Za-z]+)\\}").replace(raw) { tok(it.groupValues[1]) }
        // An info line drops the empty items (no achievements…).
        if (p.type == "info") out.split("  ·  ").filter { it.isNotBlank() }.joinToString("  ·  ") else out
    }
    val align = when (p.align) { "center" -> TextAlign.Center; "end" -> TextAlign.End; else -> TextAlign.Start }
    val family = when (p.font) { "serif" -> FontFamily.Serif; "mono" -> FontFamily.Monospace; "cursive" -> FontFamily.Cursive; else -> FontFamily.Default }
    val fontSp = with(density) { (p.size * h).toSp() }
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = when (p.align) { "center" -> Alignment.TopCenter; "end" -> Alignment.TopEnd; else -> Alignment.TopStart }
    ) {
        Text(
            text,
            style = TextStyle(
                color = sceneColor(p.color ?: "#FFFFFF"),
                fontSize = fontSp,
                lineHeight = fontSp * 1.15f,
                fontWeight = if (p.bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (p.italic) FontStyle.Italic else FontStyle.Normal,
                fontFamily = family,
                textAlign = align,
                shadow = if (p.shadow) Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 2f), 6f) else null
            ),
            maxLines = p.maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** The time now, moving on every 10 s while [ticking] (a clock on screen). */
@Composable
private fun rememberSceneNow(ticking: Boolean): Long {
    // qa-ok: STALE-PRODUCE (a clock, not a picture)
    val now by produceState(System.currentTimeMillis(), ticking) {
        while (ticking) {
            kotlinx.coroutines.delay(10_000L - System.currentTimeMillis() % 10_000L)
            value = System.currentTimeMillis()
        }
    }
    return now
}

// ---- The moving games ----

@Composable
private fun SceneGames(c: StyleCtx, p: ScenePart, pw: Float, ph: Float) {
    val density = LocalDensity.current
    val n = c.games.size
    // (QA pass 3, D35: a new category starts at its own game — animated, the row spun through every game between the old
    // category's place and the new one's for ~10 frames (layout sweep LC_CUSTOM / LE_CUSTOM after L2 / R2).)
    val faAnim = remember { Animatable(c.focus.toFloat()) }
    val seenCat = remember { arrayOf(c.categoryLabel) }
    LaunchedEffect(c.focus, c.categoryLabel) {
        if (seenCat[0] != c.categoryLabel) { faAnim.snapTo(c.focus.toFloat()); seenCat[0] = c.categoryLabel }
        else faAnim.animateTo(c.focus.toFloat(), spring(dampingRatio = 0.86f, stiffness = 380f))
    }
    val fa = if (seenCat[0] != c.categoryLabel) c.focus.toFloat() else faAnim.value
    val column = p.direction == "column"
    val arc = p.direction == "arc"
    val aspect = when (p.art) { "square" -> 1f; "wide" -> 1.78f; else -> styleAspect(c.type) }
    // Card size (across the row) and length (along it).
    val cross = if (column) pw else ph
    val mainLen = if (column) ph else pw
    val names = p.names && !column
    val cardCross = p.cardSize * cross * (if (names) 0.84f else 1f)
    val cardMain = if (column) cardCross / aspect else cardCross * aspect
    val k = (p.focusSize / p.cardSize).coerceIn(0.4f, 3f)
    val step = cardMain + p.gap * mainLen
    val focusMain = cardMain * k
    val anchorPx = (p.anchor * mainLen).coerceIn(minOf(focusMain / 2f, mainLen / 2f), maxOf(mainLen - focusMain / 2f, mainLen / 2f))
    val pulse by rememberInfiniteTransition(label = "scenePulse").animateFloat(0f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "scenePulseV")
    val each = ceil(mainLen / step).toInt() + 2
    val from = (floor(fa).toInt() - each).coerceAtLeast(0)
    val to = (ceil(fa).toInt() + each).coerceAtMost(n - 1)
    val cwDp = with(density) { (if (column) cardCross else cardMain).toDp() }
    val chDp = with(density) { (if (column) cardMain else cardCross).toDp() }
    val titles = LocalTitleOptions.current
    Box(Modifier.fillMaxSize()) {
        for (i in from..to) {
            val g = c.games[i]
            key(g.stableKey) {
                val d = i - fa
                val near = abs(d).coerceAtMost(1f)
                val scale = k + (1f - k) * near
                val centre = anchorPx + d * step + d.coerceIn(-1f, 1f) * (k - 1f) * cardMain / 2f
                val crossCentre = cross / 2f - (if (names) cross * 0.08f else 0f) +
                    if (arc) p.curve * cross * (1f - cos(d.coerceIn(-3f, 3f) * 0.45f)) * 2f else 0f
                val left = if (column) crossCentre - cardCross / 2f else centre - cardMain / 2f
                val top = if (column) centre - cardMain / 2f else crossCentre - cardCross / 2f
                val edgeFade = if (p.fadeEdges) {
                    val e = minOf(centre, mainLen - centre) / (mainLen * 0.12f)
                    e.coerceIn(0f, 1f)
                } else 1f
                val focused = i == c.focus
                Box(
                    Modifier
                        .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                        .size(cwDp, chDp)
                        .zIndex(-abs(d))
                        .graphicsLayer {
                            scaleX = scale; scaleY = scale
                            alpha = edgeFade
                            if (arc) rotationZ = (d * p.curve * 14f).coerceIn(-30f, 30f)
                        }
                        .clickable(remember { MutableInteractionSource() }, null) {
                            if (focused) c.actions.open() else c.actions.select(i)
                        }
                ) {
                    StyleArt(
                        c, g, Modifier.fillMaxSize(),
                        square = p.art == "square", wide = p.art == "wide",
                        corner = p.corner.dp,
                        focused = focused, fx = if (p.glow && focused) pulse else null
                    )
                }
                if (names && focused) {
                    val nameTop = top + cardCross * (1f + (k - 1f) / 2f) + cross * 0.03f
                    val nameW = with(density) { (cardMain * 2.4f).toDp() }
                    Box(
                        Modifier.offset { IntOffset((centre - cardMain * 1.2f).roundToInt(), nameTop.roundToInt()) }.size(nameW, with(density) { (cross * 0.14f).toDp() }),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        FullText(
                            titles.display(g.name), color = Color.White, maxLines = 1,
                            fontSize = with(density) { (cross * 0.075f).toSp() }, fontWeight = FontWeight.SemiBold,
                            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 2f), 6f))
                        )
                    }
                }
            }
        }
    }
}

// ---- Motion ----

/** The part's loop: float, sway, spin, rock, pulse, blink or drift. */
@Composable
private fun Modifier.sceneLoop(loop: SceneLoop?, w: Float, h: Float): Modifier =
    this.then(if (loop != null) Modifier.sceneLoopOn(loop, w, h) else Modifier)

@Composable
private fun Modifier.sceneLoopOn(loop: SceneLoop, w: Float, h: Float): Modifier {
    val t by rememberInfiniteTransition(label = "sceneLoop").animateFloat(
        0f, 1f, infiniteRepeatable(tween((loop.seconds * 1000).toInt(), easing = LinearEasing)), label = "sceneLoopT"
    )
    return this.graphicsLayer {
        val u = (t + loop.delay) % 1f
        val ph = u * 2f * PI.toFloat()
        val a = loop.amount
        when (loop.kind) {
            "float" -> translationY = sin(ph) * (a ?: 0.012f) * h
            "sway" -> translationX = sin(ph) * (a ?: 0.012f) * w
            "drift" -> translationX = (abs(u * 2f - 1f) * 2f - 1f) * (a ?: 0.03f) * w
            "spin" -> rotationZ = 360f * u * (if ((a ?: 1f) < 0f) -1f else 1f)
            "rock" -> rotationZ = sin(ph) * (a ?: 5f)
            "pulse", "breathe" -> { val s = 1f + sin(ph) * (a ?: 0.04f); scaleX = s; scaleY = s }
            "blink" -> { val lo = (a ?: 0.3f).coerceIn(0f, 1f); alpha = lo + (1f - lo) * (0.5f + 0.5f * cos(ph)) }
            "turn" -> { rotationY = sin(ph) * (a ?: 18f); cameraDistance = 12f * density }
        }
        flatTextFor3d() // Update 177: drawn flat, then turned (Android 13 text cache)
    }
}

/** The part's entrance: played when you move to another game ("move"), or once ("start"). */
@Composable
private fun Modifier.sceneEnter(enter: SceneEnter?, trigger: Any, w: Float, h: Float): Modifier =
    this.then(if (enter != null && enter.kind != "none") Modifier.sceneEnterOn(enter, trigger, w, h) else Modifier)

@Composable
private fun Modifier.sceneEnterOn(enter: SceneEnter, trigger: Any, w: Float, h: Float): Modifier {
    val e = remember { Animatable(0f) }
    // (QA pass 3, D33: the frame in which [trigger] changes is drawn at the entrance's start — the effect snaps to 0 only
    // a frame later, so that frame showed the new game's part fully, then it vanished and came in: Layout Studio flash.)
    val seen = remember { arrayOf<Any?>(trigger) }
    LaunchedEffect(trigger) {
        e.snapTo(0f)
        seen[0] = trigger
        if (enter.delay > 0) kotlinx.coroutines.delay(enter.delay.toLong())
        e.animateTo(1f, tween(enter.ms, easing = if (enter.kind == "pop") CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f) else FastOutSlowInEasing))
    }
    return this.graphicsLayer {
        val ev = e.value // (read always — LAYER-BRANCH: read only in the else-branch the entrance that follows never redrew the layer)
        val v = if (seen[0] != trigger) 0f else ev
        val r = 1f - v
        alpha = v.coerceIn(0f, 1f)
        when (enter.kind) {
            "slide-left" -> translationX = -r * 0.06f * w
            "slide-right" -> translationX = r * 0.06f * w
            "slide-up" -> translationY = r * 0.06f * h
            "slide-down" -> translationY = -r * 0.06f * h
            "zoom" -> { val s = 0.82f + 0.18f * v; scaleX = s; scaleY = s }
            "pop" -> { val s = 0.6f + 0.4f * v; scaleX = s; scaleY = s }
            "flip" -> { rotationY = r * 90f; cameraDistance = 12f * density }
            "spin" -> rotationZ = -r * 180f
        }
        flatTextFor3d() // Update 177: drawn flat, then turned (Android 13 text cache)
    }
}

// ---- Colours ----

/** "#RRGGBB", "#AARRGGBB", "accent", "text", "white", "black" or "transparent". */
@Composable
internal fun sceneColor(s: String): Color = when (s.lowercase()) {
    "accent" -> ShelfTheme.accent
    "text" -> ShelfTheme.colors.textPrimary
    "white" -> Color.White
    "black" -> Color.Black
    "transparent" -> Color.Transparent
    else -> runCatching { Color(android.graphics.Color.parseColor(s.trim())) }.getOrDefault(Color.Magenta)
}

/** A gradient (top to bottom) when [gradient] has two or more colours, else [color] alone, else null. */
@Composable
private fun sceneBrush(gradient: List<String>, color: String?): Brush? = when {
    gradient.size >= 2 -> Brush.verticalGradient(gradient.map { sceneColor(it) })
    gradient.size == 1 -> androidx.compose.ui.graphics.SolidColor(sceneColor(gradient[0]))
    color != null -> androidx.compose.ui.graphics.SolidColor(sceneColor(color))
    else -> null
}
