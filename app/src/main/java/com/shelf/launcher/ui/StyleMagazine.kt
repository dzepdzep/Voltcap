package com.shelf.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.HotkeyAction

/*
 * Update 344: Game magazine — Voltcap's own retro games magazine (original mastheads, type and layouts; nobody's
 * magazine copied). The category is an issue lying open on your desk, each game a two-page feature:
 * - left page: the game's big art full-bleed with its headline, a kicker (REVIEW · console), the game in your game look
 *   as a "product shot" in the corner;
 * - right page: the story (its summary set in columns), two screenshots with captions, a VERDICT box (its critics' score,
 *   or how long you've played) and an ACHIEVEMENT WATCH box with its badges; the issue's running foot and page number.
 * ◀ ▶ turn the page (it lifts at the spine and turns over), ▲ opens the CONTENTS page (▲ ▼ through it, A jumps, B shuts),
 * A the game's window — a torn-out page with callouts — L2 / R2 another issue. Three editorial styles: the bold 90s
 * kids' magazine, the glossy 2000s monthly and the 80s computer magazine. Touch: swipe or tap a page's outer edge.
 */

/** Update 344: where the controller is in Game magazine. */
internal object MagNav {
    var contents by mutableStateOf(false)
    var contentsSel by mutableIntStateOf(0)
    /** The contents page's games per page (two columns), as drawn. */
    @Volatile var per = 12
    /** The last page turn's direction (+1 forward, −1 back). */
    var dir = 1
    @Volatile var ctx: StyleCtx? = null
    internal val keysDown = HashSet<Int>()
    fun signature(): Any = listOf(contents, contentsSel)
}

/** Update 344: an editorial style — paper, ink, its accents, type, masthead. */
internal class MagStyle(
    val masthead: String, val paper: Color, val ink: Color, val muted: Color, val accent: Color, val accent2: Color,
    val head: FontFamily, val body: FontFamily, val headWeight: FontWeight, val italicHead: Boolean, val desk: Color
)

internal fun magStyle(s: Features.MgStyle): MagStyle = when (s) {
    Features.MgStyle.BOLD -> MagStyle("PRESS START", Color(0xFFFFFBF0), Color(0xFF15120C), Color(0xFF5E5646), Color(0xFFE2231A), Color(0xFFFFC90E),
        FontFamily.SansSerif, FontFamily.Serif, FontWeight.Black, true, Color(0xFF3A2416))
    Features.MgStyle.GLOSSY -> MagStyle("LEVEL UP", Color(0xFFFFFFFF), Color(0xFF111418), Color(0xFF636B75), Color(0xFFD1123F), Color(0xFF1B64D8),
        FontFamily.SansSerif, FontFamily.SansSerif, FontWeight.Bold, false, Color(0xFF1A1D22))
    Features.MgStyle.COMPUTER -> MagStyle("HIGH SCORE", Color(0xFFF2EAD6), Color(0xFF231F17), Color(0xFF6A604C), Color(0xFF1F6F5B), Color(0xFFC0571A),
        FontFamily.Monospace, FontFamily.Monospace, FontWeight.Bold, false, Color(0xFF27303A))
}

private fun mgSound(s: KitSound, fallback: UiSound?) = LayoutKitSounds.play(LayoutKit.MAGAZINE, s, fallback)

private fun mgHotkey(a: HotkeyAction): Boolean {
    val h = GreenCoreHooks.hotkey ?: return false
    h(a)
    return true
}

internal fun magTurn(c: StyleCtx, d: Int): Boolean {
    val n = c.games.size
    if (n == 0) return false
    val next = if (StyleLoop.on && n > 1) (c.focus + d).mod(n) else (c.focus + d).coerceIn(0, n - 1)
    if (next == c.focus) return false
    MagNav.dir = d
    c.actions.select(next)
    mgSound(KitSound.PAGE, UiSound.MOVE)
    return true
}

/** Update 344: Game magazine's keys (all of them while it's on). True = handled. */
internal fun magazineKey(event: KeyEvent, reach: (Boolean) -> Boolean): Boolean {
    val c = MagNav.ctx ?: return false
    val code = event.nativeKeyEvent.keyCode
    val down = event.type == KeyEventType.KeyDown
    val up = event.type == KeyEventType.KeyUp
    if (up && MagNav.keysDown.remove(code)) return true
    val k = event.key
    if (k == Key.ButtonStart || k == Key.Menu) { if (up) c.actions.quickMenu(); return up }
    if (k == Key.ButtonSelect) return up && mgHotkey(HotkeyAction.SORT)
    val isA = k == Key.ButtonA || k == Key.Enter || k == Key.DirectionCenter
    val n = c.games.size
    if (MagNav.contents) {
        if (isA) {
            if (up) {
                val to = MagNav.contentsSel.coerceIn(0, (n - 1).coerceAtLeast(0))
                MagNav.contents = false
                if (to != c.focus && n > 0) { MagNav.dir = if (to > c.focus) 1 else -1; c.actions.select(to) }
                mgSound(KitSound.PAGE, UiSound.SELECT)
            }
            return true
        }
        if (!down) return true
        val first = event.nativeKeyEvent.repeatCount == 0
        when (k) {
            Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight -> {
                val half = MagNav.per / 2
                val d = when (k) { Key.DirectionUp -> -1; Key.DirectionDown -> 1; Key.DirectionLeft -> -half; else -> half }
                val nx = (MagNav.contentsSel + d).coerceIn(0, (n - 1).coerceAtLeast(0))
                if (nx != MagNav.contentsSel) { MagNav.contentsSel = nx; mgSound(KitSound.MOVE, UiSound.MOVE) }
            }
            Key.ButtonB, Key.Back -> if (first) { MagNav.contents = false; mgSound(KitSound.BACK, UiSound.BACK) }
            else -> Unit
        }
        MagNav.keysDown += code
        return true
    }
    if (!down) return false
    val first = event.nativeKeyEvent.repeatCount == 0
    fun take(): Boolean { MagNav.keysDown += code; return true }
    when (k) {
        Key.DirectionLeft, Key.DirectionRight -> {
            val d = if (k == Key.DirectionRight) 1 else -1
            if (!magTurn(c, d) && first) EdgeBump.hit(d)
            return take()
        }
        Key.DirectionUp -> {
            if (first && n > 0) { MagNav.contentsSel = c.focus; MagNav.contents = true; mgSound(KitSound.OPEN, UiSound.OPEN) }
            return take()
        }
        Key.DirectionDown -> { if (first) reach(false); return take() }
        else -> return if (n == 0 && (isA || k == Key.ButtonX || k == Key.ButtonY)) take() else false
    }
}

// ---- The desk ----

/** Update 344: the desk the magazine lies on: dark wood grain, a lamp's pool of light. */
@Composable
internal fun MagazineBackdrop(modifier: Modifier) {
    val st = magStyle(Features.mgStyle.state.value)
    val wood = Features.mgDesk.state.value
    Canvas(modifier) {
        drawRect(st.desk)
        if (wood) {
            var y = 0f; var i = 0
            while (y < size.height) {
                val hgt = size.height * (0.05f + 0.03f * ((i * 37) % 5) / 5f)
                drawRect(lerp(st.desk, Color.Black, 0.12f + 0.1f * ((i * 53) % 7) / 7f).copy(alpha = 0.5f), Offset(0f, y), Size(size.width, hgt * 0.35f))
                y += hgt; i++
            }
        }
        drawCircle(Brush.radialGradient(listOf(Color(0x33FFE9B8), Color.Transparent), center = Offset(size.width * 0.5f, size.height * 0.45f), radius = size.width * 0.6f),
            size.width * 0.6f, Offset(size.width * 0.5f, size.height * 0.45f))
    }
}

// ---- The layout ----

@Composable
internal fun StyleMagazine(c: StyleCtx, modifier: Modifier = Modifier) {
    SideEffect {
        MagNav.ctx = c
        StyleNav.side = emptyList(); StyleNav.top = emptyList(); StyleNav.bar = emptyList()
        StyleLoop.on = loopRowOn(c)
    }
    rememberLayoutKit(LayoutKit.MAGAZINE, Features.mgSounds.state.value, c.state.appearance.soundsEnabled)
    DisposableEffect(Unit) { onDispose { MagNav.keysDown.clear(); MagNav.contents = false } }
    androidx.activity.compose.BackHandler(enabled = MagNav.contents && GamePopup.game == null) { MagNav.contents = false }
    val caption = CategoryTitle.captionOn(c.state.appearance)
    if (caption) CategoryTitle.Native(null)
    val st = magStyle(Features.mgStyle.state.value)
    val g = c.game
    // Its story, screenshots and achievements for the spread you're on (and the next).
    LaunchedEffect(g?.stableKey) {
        val gg = g ?: return@LaunchedEffect
        kotlinx.coroutines.delay(250)
        c.actions.needMetadata(gg)
        if (!c.state.screenshots.containsKey(gg.stableKey)) c.actions.needMedia(gg)
        if (c.state.raAchievementDetails[gg.stableKey].isNullOrEmpty() && c.progress(gg) != null) c.actions.needAchievements(gg)
    }
    if (g == null) { StyleEmpty(modifier); return }
    // The page turn: the spread you were on lifts at the spine and turns over onto the new one.
    val reduced = !rememberAnimationsEnabled()
    val prevFocus = remember { intArrayOf(c.focus) }
    val from = remember { mutableIntStateOf(c.focus) }
    val turn = remember { Animatable(1f) }
    LaunchedEffect(c.focus) {
        if (prevFocus[0] == c.focus) return@LaunchedEffect
        from.intValue = prevFocus[0]
        prevFocus[0] = c.focus
        if (reduced) { turn.snapTo(1f); return@LaunchedEffect }
        turn.snapTo(0f)
        turn.animateTo(1f, tween(620, easing = FastOutSlowInEasing))
    }
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    val latest by rememberUpdatedState(c)
    BoxWithConstraints(modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp)
        .pointerInput(Unit) {
            var acc = 0f
            detectHorizontalDragGestures(onDragStart = { acc = 0f }) { ch, dx ->
                ch.consume(); acc += dx
                val step = 80.dp.toPx()
                if (acc <= -step) { acc = 0f; magTurn(latest, 1) }
                if (acc >= step) { acc = 0f; magTurn(latest, -1) }
            }
        }, contentAlignment = Alignment.Center
    ) {
        // The spread: two pages of about 0.72 each, as big as the desk allows.
        // (Update 346: on a short screen the pages get wider instead — at 0.72 each the right page's two columns were
        // squeezed to a few words a line.)
        val spreadAspect = if (maxHeight < 380.dp) (maxWidth / maxHeight * 0.96f).coerceIn(1.46f, 1.9f) else 1.46f
        val sw = minOf(maxWidth, maxHeight * spreadAspect)
        val sh = sw / spreadAspect
        val pageW = sw / 2
        val compact = sh < 360.dp
        // (Update 348, Toni: "the next page's picture flashes before it turns": the frame the game changes in came before
        // the turn's effect started — the turn still at its end and the old page unknown, so the new spread showed whole for
        // a frame. A focus that moved on and isn't turning yet is the turn's first frame now.)
        val pending = prevFocus[0] != c.focus && !reduced
        val t = if (pending) 0f else turn.value
        val old = c.games.getOrNull(if (pending) prevFocus[0] else from.intValue) ?: g
        val forward = MagNav.dir >= 0
        val turning = t < 1f && old.stableKey != g.stableKey
        Box(Modifier.size(sw, sh)) {
            // The magazine's shadow on the desk and the spine's fold.
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Color.Black.copy(alpha = 0.35f), Offset(10f, 14f), size)
            }
            // Under the turning page: the left page of the old spread (going forward, until the turn passes the middle) and
            // the new right page; going back, the new left and the old right.
            val leftGame = if (!turning) g else if (forward) (if (t < 0.5f) old else g) else g
            val rightGame = if (!turning) g else if (forward) g else (if (t < 0.5f) old else g)
            Row(Modifier.fillMaxSize()) {
                MagLeftPage(c, leftGame, st, compact, Modifier.size(pageW, sh).pointerInput(Unit) { detectTapGestures { o -> if (o.x < size.width * 0.18f) magTurn(latest, -1) else open() } })
                MagRightPage(c, rightGame, st, compact, Modifier.size(pageW, sh).pointerInput(Unit) { detectTapGestures { o -> if (o.x > size.width * 0.82f) magTurn(latest, 1) } })
            }
            // The turning page.
            if (turning) {
                val firstHalf = t < 0.5f
                val k = if (firstHalf) t / 0.5f else (t - 0.5f) / 0.5f
                val shade = if (firstHalf) k else 1f - k
                if (forward) {
                    if (firstHalf) MagTurnPage(Modifier.offset(x = pageW).size(pageW, sh), TransformOrigin(0f, 0.5f), -90f * k, shade, false) { MagRightPage(c, old, st, compact, Modifier.fillMaxSize()) }
                    else MagTurnPage(Modifier.size(pageW, sh), TransformOrigin(1f, 0.5f), 90f * (1f - k), shade, true) { MagLeftPage(c, g, st, compact, Modifier.fillMaxSize()) }
                } else {
                    if (firstHalf) MagTurnPage(Modifier.size(pageW, sh), TransformOrigin(1f, 0.5f), 90f * k, shade, true) { MagLeftPage(c, old, st, compact, Modifier.fillMaxSize()) }
                    else MagTurnPage(Modifier.offset(x = pageW).size(pageW, sh), TransformOrigin(0f, 0.5f), -90f * (1f - k), shade, false) { MagRightPage(c, g, st, compact, Modifier.fillMaxSize()) }
                }
            }
            // The fold down the spine.
            Box(Modifier.offset(x = pageW - 14.dp).width(28.dp).fillMaxHeight().zIndex(5f)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.22f), Color.Black.copy(alpha = 0.34f), Color.Black.copy(alpha = 0.18f), Color.Transparent))))
            if (MagNav.contents) MagContents(c, st, Modifier.fillMaxSize().zIndex(10f), select)
        }
    }
}

/** A page part-way through its turn, its shading deepening as it stands up on edge. */
@Composable
private fun MagTurnPage(modifier: Modifier, origin: TransformOrigin, angle: Float, shade: Float, leftSide: Boolean, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    Box(modifier.zIndex(6f).graphicsLayer {
        transformOrigin = origin
        rotationY = angle
        cameraDistance = 9f * density.density
    }) {
        content()
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            if (leftSide) listOf(Color.Black.copy(alpha = 0.05f * shade), Color.Black.copy(alpha = 0.45f * shade))
            else listOf(Color.Black.copy(alpha = 0.45f * shade), Color.Black.copy(alpha = 0.05f * shade))
        )))
    }
}

/** The kicker over a headline: a coloured block of capitals. */
@Composable
private fun MagKicker(text: String, st: MagStyle, compact: Boolean) {
    Text(text.uppercase(), color = Color.White, fontFamily = st.head, fontWeight = FontWeight.Bold, fontSize = if (compact) 9.sp else 11.sp, letterSpacing = 1.sp,
        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.background(st.accent).padding(horizontal = 6.dp, vertical = 2.dp))
}

/** The left page: the game's big art, its headline and kicker, a product shot of the game in your look. */
@Composable
private fun MagLeftPage(c: StyleCtx, g: Game, st: MagStyle, compact: Boolean, modifier: Modifier) {
    val titles = LocalTitleOptions.current
    val art = c.backdrop(g) ?: c.wide(g) ?: c.cover(g)
    Box(modifier.clipToBounds().background(st.paper)) {
        if (art != null) AsyncImage(rememberStyleArtRequest(art), g.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Box(Modifier.fillMaxSize().background(placeholderBrush(g.hue)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.45f), 0.3f to Color.Transparent, 0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))))
        // The masthead strip along the top of the page.
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            // (Update 346: "Feature", not "Review" — there's no review of ours behind it.)
            MagKicker(if (c.games.indexOf(g) == 0) "Cover story" else "Feature", st, compact)
            Spacer(Modifier.width(6.dp))
            Text((if (g.sourceType == com.shelf.launcher.data.SourceType.APP) "Android" else titles.console(g.platform)).uppercase(), color = Color.White, fontFamily = st.head,
                fontWeight = FontWeight.Bold, fontSize = if (compact) 9.sp else 11.sp, letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // The product shot: the game in your game look, a little tilted, bottom right.
        if (Features.mgShot.state.value) Box(Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = if (compact) 64.dp else 86.dp)
            .size(if (compact) 74.dp else 104.dp).graphicsLayer { rotationZ = 6f }) {
            val aspect = styleAspect(c.type)
            Box(Modifier.align(Alignment.Center).size((if (compact) 74.dp else 104.dp) * minOf(1f, aspect), (if (compact) 74.dp else 104.dp) * minOf(1f, 1f / aspect))) {
                StyleArt(c, g, Modifier.fillMaxSize())
            }
        }
        // The headline.
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 14.dp, vertical = 12.dp)) {
            // (Update 346: the headline fits its page — long names step down instead of losing their end.)
            val name = titles.display(g.name).uppercase()
            val big = if (compact) 22.sp else 32.sp
            val hs = if (name.length > 34) big * 0.72f else if (name.length > 22) big * 0.85f else big
            Text(
                name, color = Color.White, fontFamily = st.head, fontWeight = st.headWeight,
                fontStyle = if (st.italicHead) FontStyle.Italic else FontStyle.Normal, fontSize = hs,
                lineHeight = hs * 1.04f, maxLines = 3, overflow = TextOverflow.Ellipsis,
                style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.6f), Offset(2f, 2f), 4f))
            )
            Box(Modifier.padding(top = 4.dp).width(60.dp).height(4.dp).background(st.accent2))
        }
    }
}

/** The right page: the story in columns, screenshots, the verdict and the achievement watch, the running foot. */
@Composable
private fun MagRightPage(c: StyleCtx, g: Game, st: MagStyle, compact: Boolean, modifier: Modifier) {
    val titles = LocalTitleOptions.current
    val meta = c.state.metadata[g.stableKey]
    val shots = c.state.screenshots[g.stableKey].orEmpty()
    val idx = c.games.indexOf(g).coerceAtLeast(0)
    Column(modifier.clipToBounds().background(st.paper).padding(start = 18.dp, end = 14.dp, top = 12.dp, bottom = 8.dp)) {
        Text(titles.display(g.name), color = st.ink, fontFamily = st.head, fontWeight = st.headWeight, fontSize = if (compact) 15.sp else 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontStyle = if (st.italicHead) FontStyle.Italic else FontStyle.Normal)
        Text(
            listOfNotNull(meta?.developer, meta?.releaseYear?.toString(), meta?.genres?.firstOrNull()).joinToString("  ·  ").ifEmpty { titles.console(g.platform) },
            color = st.muted, fontFamily = st.body, fontSize = if (compact) 10.sp else 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Box(Modifier.padding(vertical = 5.dp).fillMaxWidth().height(2.dp).background(st.ink))
        Row(Modifier.weight(1f)) {
            // The story: its summary, a drop cap, set in a column.
            Column(Modifier.weight(0.52f).fillMaxHeight()) {
                // (Update 346: the game's own summary only — no made-up "tester's notes" when there's none; the
                // screenshots take the column then.)
                val story = meta?.summary?.takeIf { it.isNotBlank() }
                if (story != null) Text(
                    androidx.compose.ui.text.buildAnnotatedString {
                        withStyle(androidx.compose.ui.text.SpanStyle(fontSize = if (compact) 22.sp else 28.sp, fontWeight = FontWeight.Black, color = st.accent, fontFamily = st.head)) { append(story.take(1)) }
                        append(story.drop(1))
                    },
                    color = st.ink, fontFamily = st.body, fontSize = if (compact) 10.5.sp else 11.sp, lineHeight = if (compact) 13.5.sp else 15.sp,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                ) else Text(
                    "No story on file for this game yet.", color = st.muted, fontFamily = st.body, fontStyle = FontStyle.Italic,
                    fontSize = if (compact) 10.sp else 11.sp, modifier = if (shots.isEmpty()) Modifier.weight(1f) else Modifier.padding(bottom = 6.dp)
                )
                if (shots.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    // (Update 346: kept on small pages too — one figure there — and as tall as the page allows, not a fixed strip.)
                    val n = if (compact) 1 else 2
                    val figH = if (story == null) 0.dp else if (compact) 54.dp else 64.dp
                    Row(Modifier.then(if (story == null) Modifier.weight(1f) else Modifier), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        shots.take(if (story == null) 4.coerceAtMost(shots.size).let { if (compact) minOf(it, 2) else it } else n).forEachIndexed { i, s ->
                            Column(Modifier.weight(1f).then(if (figH > 0.dp) Modifier else Modifier.fillMaxHeight())) {
                                Box(Modifier.fillMaxWidth().then(if (figH > 0.dp) Modifier.height(figH) else Modifier.weight(1f)).border(1.dp, st.ink).background(st.muted.copy(alpha = 0.18f))) {
                                    AsyncImage(rememberStyleArtRequest(s), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                }
                                Text("Fig. ${i + 1}", color = st.muted, fontFamily = st.body, fontSize = 9.sp, fontStyle = FontStyle.Italic, maxLines = 1)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            // The boxes: the verdict and the achievement watch.
            Column(Modifier.weight(0.48f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MagVerdict(c, g, st, compact)
                MagAchievements(c, g, st, compact)
                if (!compact) MagStats(c, g, st)
            }
        }
        // The running foot.
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(st.masthead + "  ·  " + c.categoryLabel.uppercase(), color = st.muted, fontFamily = st.head, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("${idx * 2 + 3}", color = st.ink, fontFamily = st.head, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * The score box: the critics' score in a burst (Metacritic's, else the critics' average the scrapers give), else your
 * time played. (Update 346: only what's known — the "verdicts" made from the score ("For fans only") are gone.)
 */
@Composable
private fun MagVerdict(c: StyleCtx, g: Game, st: MagStyle, compact: Boolean) {
    val meta = c.state.metadata[g.stableKey]
    val score = meta?.metacritic ?: meta?.criticScore
    // (Update 346: whose score it is runs across the top of the box — beside the burst it had no room in a narrow sidebar.)
    Column(Modifier.fillMaxWidth().border(2.dp, st.ink).padding(5.dp)) {
    FitText(magScoreTitle(meta?.metacritic != null, score != null), st.accent, maxSize = if (compact) 10.sp else 11.sp, minSize = 7.sp, maxLines = 1,
        fontWeight = FontWeight.Black, style = androidx.compose.ui.text.TextStyle(fontFamily = st.head, letterSpacing = 0.5.sp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(if (compact) 36.dp else 44.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                // A starburst.
                val p = Path()
                val n = 14
                for (i in 0 until n * 2) {
                    val r = if (i % 2 == 0) size.minDimension / 2f else size.minDimension / 2.6f
                    val a = Math.PI * i / n
                    val x = size.width / 2f + (r * kotlin.math.cos(a)).toFloat(); val y = size.height / 2f + (r * kotlin.math.sin(a)).toFloat()
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close()
                drawPath(p, st.accent)
            }
            val played = c.playtime(g)?.takeIf { it > 0 }
            Text(score?.toString() ?: (played?.let { ms -> if (ms >= 3_600_000L) "${ms / 3_600_000L}h" else "${(ms / 60_000L).coerceAtLeast(1)}m" } ?: "NEW"), color = Color.White, fontFamily = st.head, fontWeight = FontWeight.Black,
                fontSize = if (compact) 11.sp else 12.sp, maxLines = 1, softWrap = false)
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(
                magScoreLine(score != null, c.playtime(g)?.takeIf { it > 0 } != null),
                color = st.ink, fontFamily = st.body, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
    }
    }
}

/** Update 346: the score box's heading — whose score it is, or that it's your time. */
internal fun magScoreTitle(metacritic: Boolean, score: Boolean): String = when {
    metacritic -> "METASCORE"
    score -> "CRITICS"
    else -> "YOUR TIME"
}

/** Update 346: the line under it — what the number is, never a judgement of the game. */
internal fun magScoreLine(score: Boolean, played: Boolean): String = when {
    score -> "Out of 100"
    played -> "Time played"
    else -> "Not played yet"
}

/** ACHIEVEMENT WATCH: earned / total as a chart bar, a row of its badges. */
@Composable
private fun MagAchievements(c: StyleCtx, g: Game, st: MagStyle, compact: Boolean) {
    val pr = c.progress(g) ?: return
    val (e, t) = pr
    val list = c.state.raAchievementDetails[g.stableKey].orEmpty()
    Column(Modifier.fillMaxWidth().background(lerp(st.paper, st.accent2, 0.18f)).padding(6.dp)) {
        FitText("ACHIEVEMENTS", st.ink, maxSize = 10.sp, minSize = 7.sp, maxLines = 1, fontWeight = FontWeight.Black,
            style = androidx.compose.ui.text.TextStyle(fontFamily = st.head, letterSpacing = 0.5.sp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$e/$t", color = st.ink, fontFamily = st.head, fontWeight = FontWeight.Bold, fontSize = if (compact) 11.sp else 14.sp)
            Spacer(Modifier.width(6.dp))
            Box(Modifier.weight(1f).height(7.dp).border(1.dp, st.ink)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth((e.toFloat() / t).coerceIn(0f, 1f)).background(st.accent))
            }
        }
        if (list.isNotEmpty()) Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            (list.filter { it.isUnlocked } + list.filter { !it.isUnlocked }).take(if (compact) 4 else 5).forEach { a ->
                val url = com.shelf.launcher.data.RaBadges.url(a.badgeName)
                // (Update 346: badges no smaller than 24 dp; fewer of them on a small page instead.)
                Box(Modifier.size(24.dp).border(1.dp, st.ink)) {
                    if (url != null) AsyncImage(url, a.title, Modifier.fillMaxSize().graphicsLayer { alpha = if (a.isUnlocked) 1f else 0.35f }, contentScale = ContentScale.Crop)
                }
            }
        }
    }
}

/** THE STATS: time played, last played, how long it takes to beat. */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun MagStats(c: StyleCtx, g: Game, st: MagStyle) {
    val meta = c.state.metadata[g.stableKey]
    Column(Modifier.fillMaxWidth().border(1.dp, st.muted).padding(5.dp)) {
        Text("THE STATS", color = st.accent, fontFamily = st.head, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 1.sp, maxLines = 1)
        @Composable
        fun line(l: String, v: String?) {
            if (v.isNullOrBlank()) return
            // (Update 346: the label keeps its width and the value wraps under it when they don't fit side by side —
            // "Played  Never played" was cut to "Playe".)
            androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(l, color = st.muted, fontFamily = st.body, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(end = 6.dp))
                Text(v, color = st.ink, fontFamily = st.body, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        line("Played", stylePlaytime(c.playtime(g), c.lastPlayed(g) != null))
        line("Last played", c.lastPlayed(g)?.let { lastPlayedLabel(it) })
        line("To beat", meta?.normallyHours?.let { "${it.toInt()} h" })
    }
}

/** The CONTENTS page laid over the spread: the issue's games with their page numbers; ▲ ▼ / ◀ ▶ and A. */
@Composable
private fun MagContents(c: StyleCtx, st: MagStyle, modifier: Modifier, select: (Int) -> Unit) {
    val titles = LocalTitleOptions.current
    val n = c.games.size
    val sel = MagNav.contentsSel.coerceIn(0, (n - 1).coerceAtLeast(0))
    BoxWithConstraints(modifier) {
    val per = (((maxHeight - 110.dp) / 27.dp).toInt().coerceIn(3, 14)) * 2
    SideEffect { MagNav.per = per }
    val page = sel / per
    Column(Modifier.fillMaxSize().background(st.paper).border(3.dp, st.ink).padding(horizontal = 22.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(st.masthead, color = st.accent, fontFamily = st.head, fontWeight = FontWeight.Black, fontSize = 26.sp,
                fontStyle = if (st.italicHead) FontStyle.Italic else FontStyle.Normal)
            Spacer(Modifier.width(12.dp))
            Text("CONTENTS  ·  " + c.categoryLabel.uppercase(), color = st.ink, fontFamily = st.head, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 4.dp))
        }
        Box(Modifier.padding(vertical = 6.dp).fillMaxWidth().height(3.dp).background(st.ink))
        Row(Modifier.weight(1f)) {
            for (col in 0 until 2) {
                Column(Modifier.weight(1f)) {
                    for (r in 0 until per / 2) {
                        val i = page * per + col * (per / 2) + r
                        if (i >= n) break
                        val on = i == sel
                        Row(Modifier.fillMaxWidth().background(if (on) st.accent2.copy(alpha = 0.45f) else Color.Transparent)
                            .pointerInput(i) { detectTapGestures { MagNav.contents = false; if (i != c.focus) { MagNav.dir = if (i > c.focus) 1 else -1; select(i) } } }
                            .padding(vertical = 3.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i * 2 + 3}".padStart(3, ' '), color = st.accent, fontFamily = st.head, fontWeight = FontWeight.Black, fontSize = 15.sp, modifier = Modifier.width(38.dp))
                            Text(titles.display(c.games[i].name), color = st.ink, fontFamily = st.body, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (col == 0) Spacer(Modifier.width(16.dp))
            }
        }
        Text("▲ ▼ ◀ ▶ PICK   A TURN TO IT   B CLOSE", color = st.muted, fontFamily = st.head, fontSize = 10.sp, letterSpacing = 1.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
    }
    }
}

// ---- Options ----

/** Update 344: Settings › Home › Layout options (Game magazine). Each its own "mg_…" Features pref. */
@Composable
internal fun MagazineSettings() {
    MutedNote(
        "Each category an issue lying open on your desk, each game a two-page feature with its art, story, screenshots, " +
            "verdict and achievements. ◀ ▶ turn the page, ▲ the contents page, A the game's window. Swipe across or tap a " +
            "page's outer edge to turn."
    )
    Spacer(Modifier.height(10.dp))
    StyleChoice("Editorial style", Features.MgStyle.values(), Features.mgStyle.state.value, { it.label }) { Features.mgStyle.value = it }
    Toggle(Icons.Filled.MenuBook, "Product shot", "The game in your game look in the corner of its art page. Off: just the art.", Features.mgShot)
    Toggle(Icons.Filled.MenuBook, "Wooden desk", "Wood grain under the magazine. Off: a plain desk.", Features.mgDesk)
    Toggle(Icons.Filled.QueueMusic, "Interface sounds", "Page turns, a pencil tick and a rubber stamp. Off: Voltcap's usual sounds.", Features.mgSounds)
    if (Features.mgSounds.state.value) {
        PercentSlider("Sound volume", Features.kitVolume.state.value, { Features.kitVolume.value = it.coerceIn(0, 100) })
        Spacer(Modifier.height(6.dp))
    }
}
