package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

/*
 * Shared motion (update 104).
 */

/**
 * Update 112: the animation choices in force for the layout on screen (Home › Animations, and per layout). [layout] is
 * set by the home screen; everything reads through here, so a layout's own choice wins over the general one.
 */
internal object MotionPrefs {
    /** Update 317at: Background art motion and its zoom size (Settings), set by the home screen — for [slowZoom]. */
    var globalHero: com.shelf.launcher.data.HeroMotion by androidx.compose.runtime.mutableStateOf(com.shelf.launcher.data.HeroMotion.QUICK_ZOOM)
    var zoomPercent: Int by androidx.compose.runtime.mutableStateOf(15)
    // (Update 117: Compose state, so what follows the layout — the music player's look — redraws when it changes.)
    var layout: com.shelf.launcher.data.HomeLayout by androidx.compose.runtime.mutableStateOf(com.shelf.launcher.data.HomeLayout.CLASSIC)
    private val F = com.shelf.launcher.data.Features

    private var parsedFrom: String? = null
    private var parsed: Map<String, String> = emptyMap()

    /** The per-layout overrides ("LAYOUT.what" → value). */
    fun overrides(raw: String = F.layoutAnims.value): Map<String, String> {
        if (raw != parsedFrom) {
            parsed = raw.split(';').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 && it[0].isNotBlank() }?.let { it[0] to it[1] } }.toMap()
            parsedFrom = raw
        }
        return parsed
    }

    /**
     * A layout's own defaults where its look calls for one (the Cross menu fades: its categories and the game's info
     * fade in rather than slide). A stored "GLOBAL" means "same as everywhere" over these.
     */
    private val DEFAULTS = mapOf("CROSS.page" to "FADE", "CROSS.info" to "FADE")

    fun override(l: com.shelf.launcher.data.HomeLayout, what: String): String? =
        (overrides()["${l.name}.$what"] ?: DEFAULTS["${l.name}.$what"])?.takeIf { it != "GLOBAL" }

    fun setOverride(l: com.shelf.launcher.data.HomeLayout, what: String, value: String?) {
        val m = overrides().toMutableMap()
        if (value == null) m.remove("${l.name}.$what") else m["${l.name}.$what"] = value
        F.layoutAnims.value = m.entries.joinToString(";") { "${it.key}=${it.value}" }
    }

    fun info(l: com.shelf.launcher.data.HomeLayout = layout): com.shelf.launcher.data.Features.InfoAnim =
        override(l, "info")?.let { v -> com.shelf.launcher.data.Features.InfoAnim.values().firstOrNull { it.name == v } }
            ?: if (!F.animRiseIn.value) com.shelf.launcher.data.Features.InfoAnim.NONE else F.infoAnim.value

    fun page(l: com.shelf.launcher.data.HomeLayout = layout): com.shelf.launcher.data.Features.PageAnim =
        override(l, "page")?.let { v -> com.shelf.launcher.data.Features.PageAnim.values().firstOrNull { it.name == v } }
            ?: if (!F.animPageSlide.value) com.shelf.launcher.data.Features.PageAnim.NONE else F.pageAnim.value

    /** Update 119: how the game's picture (its hero / background) comes in, in this layout — [global] unless it has its own. */
    fun hero(global: com.shelf.launcher.data.HeroMotion, l: com.shelf.launcher.data.HomeLayout = layout): com.shelf.launcher.data.HeroMotion =
        override(l, "hero")?.let { v -> com.shelf.launcher.data.HeroMotion.values().firstOrNull { it.name == v } } ?: global

    /** How far the game you're on stands out: 1 = the layout's own lift. */
    fun pop(l: com.shelf.launcher.data.HomeLayout = layout): Float = (override(l, "pop")?.toIntOrNull() ?: F.focusPop.value) / 100f

    fun focusedOnlyMotion(l: com.shelf.launcher.data.HomeLayout = layout): Boolean =
        override(l, "motion")?.let { it == "1" } ?: F.motionFocusedOnly.value

    /** Reads the prefs as state, so a composable using these recomposes when a choice changes. */
    @Composable
    fun observe() {
        F.layoutAnims.state.value; F.infoAnim.state.value; F.pageAnim.state.value; F.focusPop.state.value
        F.motionFocusedOnly.state.value; F.animRiseIn.state.value; F.animPageSlide.state.value
    }
}

/** Whether the media drawn inside is the game you're on (update 112: "only the game you're on moves"). */
internal val LocalMediaFocused = androidx.compose.runtime.compositionLocalOf { true }

/** [content] knowing whether it's the game you're on ([on]). */
@Composable
internal fun MediaFocus(on: Boolean, content: @Composable () -> Unit) =
    androidx.compose.runtime.CompositionLocalProvider(LocalMediaFocused provides on, content = content)

/** A page of games turning: the new page slides in from the side it lies on (right for the next one), the old one out. */
internal fun AnimatedContentTransitionScope<Int>.pageSlide(): ContentTransform {
    // Off (Home › Animations): the page just changes; Fade: it fades over (update 112, per layout too).
    when (MotionPrefs.page()) {
        com.shelf.launcher.data.Features.PageAnim.NONE -> return androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
        com.shelf.launcher.data.Features.PageAnim.FADE -> return fadeIn(tween(260)) togetherWith fadeOut(tween(200))
        else -> Unit
    }
    val dir = if (targetState >= initialState) 1 else -1
    return (slideInHorizontally(tween(360, easing = FastOutSlowInEasing)) { it * dir } + fadeIn(tween(240))) togetherWith
        (slideOutHorizontally(tween(360, easing = FastOutSlowInEasing)) { -it * dir } + fadeOut(tween(240)))
}

private class Held<T : Any>(val item: T, shown: Boolean = false) {
    val alpha = Animatable(if (shown) 1f else 0f)
    var ready by mutableStateOf(false)
    var failed by mutableStateOf(false)
}

/**
 * Pictures that change without a black gap (update 104): the picture on screen stays fully there until the next one
 * has loaded, then the new one fades in over it and the old one goes. (A crossfade faded the old one out while the new
 * one was still loading — a quick black flash — and dimmed both half way.) [content] draws one picture and calls
 * ready() once it's loaded (failed() if it can't be).
 */
@Composable
internal fun <T : Any> HeldLayers(
    target: T?,
    fadeMs: Int,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(item: T, ready: () -> Unit, failed: () -> Unit) -> Unit
) {
    // The first picture is there from the first frame (no fade in from black coming back to the home screen); only a
    // change of picture fades.
    val layers = remember { mutableStateListOf<Held<T>>().apply { if (target != null) add(Held(target, shown = true)) } }
    LaunchedEffect(target) {
        if (target == null) { layers.clear(); return@LaunchedEffect }
        if (layers.lastOrNull()?.item == target) return@LaunchedEffect
        // Moving fast: the pictures still loading in between are dropped (the one on screen and the newest stay).
        if (layers.size >= 3) { val onScreen = layers.first(); layers.removeAll { it !== onScreen && !it.ready } }
        layers.add(Held(target, shown = layers.isEmpty()))
    }
    Box(modifier) {
        for (l in layers) key(l) {
            Box(Modifier.matchParentSize().graphicsLayer { alpha = l.alpha.value }) {
                content(l.item, { l.ready = true }, { l.failed = true })
            }
            LaunchedEffect(l.ready) {
                if (!l.ready) return@LaunchedEffect
                // Off (Home › Animations): the next picture replaces the last one at once (still never a black gap).
                if (com.shelf.launcher.data.Features.animHeldPictures.value) l.alpha.animateTo(1f, androidx.compose.animation.core.tween(fadeMs))
                else l.alpha.snapTo(1f)
                // Everything under it is covered now.
                val i = layers.indexOf(l)
                if (i > 0) repeat(i) { layers.removeAt(0) }
            }
            LaunchedEffect(l.failed) {
                if (!l.failed) return@LaunchedEffect
                // The newest one has no picture: nothing (not the game before's picture).
                if (layers.lastOrNull() === l) layers.clear() else layers.remove(l)
            }
        }
    }
}

/** One picture through [HeldLayers]. */
/**
 * Update 317at (Toni: "the slow zoom the Voltcap row uses should work on the other layouts that fit it"): a layout's own
 * big picture drifts slowly closer while you rest on the game ([key]), as the home background does with Background art
 * motion "Quick fade + slow zoom" / "Ken Burns" (that layout's own choice first), up to the same Zoom size. Paused while
 * Voltcap is in the background; off with animations off. The parent clips it.
 */
@Composable
internal fun Modifier.slowZoom(key: Any?): Modifier {
    val motion = MotionPrefs.hero(MotionPrefs.globalHero)
    val on = (motion == com.shelf.launcher.data.HeroMotion.QUICK_ZOOM || motion == com.shelf.launcher.data.HeroMotion.KEN_BURNS) &&
        rememberAnimationsEnabled()
    val active = rememberIsActive()
    val target = 1f + MotionPrefs.zoomPercent.coerceIn(5, 25) / 100f
    val z = remember { Animatable(1f) }
    val last = remember { arrayOfNulls<Any>(1) }
    LaunchedEffect(key, on, active, target) {
        if (!on) { z.snapTo(1f); return@LaunchedEffect }
        if (last[0] != key) { last[0] = key; z.snapTo(1f) }
        if (!active) return@LaunchedEffect
        val left = ((target - z.value) / (target - 1f)).coerceIn(0f, 1f)
        z.animateTo(target, tween((22_000 * left).toInt().coerceAtLeast(1), easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0.2f, 1f)))
    }
    return if (!on) this else this.graphicsLayer { scaleX = z.value; scaleY = z.value }
}

@Composable
internal fun HeldImage(
    model: Any?,
    modifier: Modifier = Modifier,
    fadeMs: Int = 260,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    imageModifier: Modifier = Modifier,
    /** Update 363: the slow zoom ([slowZoom]) belongs to each picture, not to the whole stack — see below. */
    zoom: Boolean = false
) {
    HeldLayers(model, fadeMs, modifier) { m, ready, failed ->
        AsyncImage(
            m, null, contentScale = contentScale, alignment = alignment,
            onSuccess = { ready() }, onError = { failed() },
            // (Update 363: with the zoom on the stack, one game change snapped the zoom back to 1 under the picture still on
            // screen while the next loaded — the old art jumped smaller, then the new one faded in. Each picture drifts on its own now.)
            modifier = Modifier.matchParentSize().then(if (zoom) Modifier.slowZoom(m) else Modifier).then(imageModifier)
        )
    }
}

/**
 * The next game's title / panel coming in (update 104, every layout): it rises a little into place as it fades in,
 * the one before fades away quicker — instead of just appearing.
 */
internal fun <S> AnimatedContentTransitionScope<S>.gameSwap(): ContentTransform =
    when (MotionPrefs.info()) {
        com.shelf.launcher.data.Features.InfoAnim.NONE -> androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
        com.shelf.launcher.data.Features.InfoAnim.FADE -> fadeIn(tween(260)) togetherWith fadeOut(tween(160)) using SizeTransform(clip = false)
        else -> (fadeIn(tween(240, delayMillis = 50)) + slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 6 }) togetherWith
        fadeOut(tween(130)) using SizeTransform(clip = false)
    }

/**
 * The game's info rising into place when the game changes (update 108): a light version of [gameSwap] that animates
 * the part itself instead of drawing it twice — for parts that publish things while they're drawn (the game header's
 * buttons for the D-pad, Edit Home's places), which mustn't exist twice.
 */
@Composable
internal fun Modifier.riseOnChange(key: Any?, floor: Float = 0f, ms: Int = 320): Modifier {
    // Update 116: a part set to stay still (Edit Home › Style) never rises in.
    if (LocalStillPart.current) return this
    val p = remember { Animatable(1f) }
    val seen = remember { arrayOf<Any?>(key) }
    LaunchedEffect(key) {
        if (seen[0] == key) return@LaunchedEffect
        seen[0] = key
        if (MotionPrefs.info() == com.shelf.launcher.data.Features.InfoAnim.NONE) return@LaunchedEffect
        p.snapTo(0f)
        p.animateTo(1f, tween(ms, easing = FastOutSlowInEasing))
    }
    val rise = MotionPrefs.info() == com.shelf.launcher.data.Features.InfoAnim.RISE
    val animOn = MotionPrefs.info() != com.shelf.launcher.data.Features.InfoAnim.NONE
    return this.graphicsLayer {
        // QA (V1): in the frame the game changes the effect hasn't snapped yet — the new info was drawn fully for that
        // frame, then hidden and risen in (a flicker on every move). Until it has, draw it at the start of the rise.
        // (RP Classic QA, Toni: "the names disappear" in Tile home.) Read the animation before the check: read only in the
        // else-branch, the frame drawn at 0 before the effect ran observed nothing, so the rise that followed never redrew
        // the layer and the name stayed invisible until something else recomposed it.
        val pv = p.value
        val v = if (animOn && seen[0] != key && !QaFault.on("rise_old")) 0f else pv
        // (Update 362: [floor] — the lowest it fades to; the Tile home's name never vanishes on a move, it settles in.)
        alpha = maxOf(floor, (if (rise) 0.25f else 0f) + (if (rise) 0.75f else 1f) * v)
        // (Update 362, Thor: drawn see-through through its own buffer, the buffer kept the old name's size for a few frames
        // and cut the new name short — "999 Nine Hc". With a floor the alpha goes straight onto what's drawn.)
        if (floor > 0f) compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
        translationY = if (rise) (1f - v) * 14f * density else 0f
    }
}

/** Rises into place when it first appears (update 108): for an info part drawn only on the game you're on. */
@Composable
internal fun Modifier.riseOnEnter(): Modifier {
    if (LocalStillPart.current) return this
    val anim = MotionPrefs.info()
    val p = remember { Animatable(if (anim != com.shelf.launcher.data.Features.InfoAnim.NONE) 0f else 1f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(300, easing = FastOutSlowInEasing)) }
    val rise = anim == com.shelf.launcher.data.Features.InfoAnim.RISE
    return this.graphicsLayer {
        val v = p.value
        alpha = v
        translationY = if (rise) (1f - v) * 10f * density else 0f
    }
}

/**
 * Update 119: a picture that fades in quickly once it has loaded (the big logos: SteamGridDB's come in with a short
 * fade instead of popping in), each time the picture changes.
 */
@Composable
internal fun FadeInImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    alignment: Alignment = Alignment.Center,
    fadeMs: Int = 220
) {
    val a = remember(model) { Animatable(0f) }
    var loaded by remember(model) { mutableStateOf(false) }
    // (Update 325, Toni: "the logos that appear shouldn't just spawn in — use the same animation the layout uses": a
    // logo comes in as the game's info does — Motion › The game's info: rises into place, fades in, or just appears —
    // once its picture has loaded, instead of popping in after the info has already moved.)
    val info = MotionPrefs.info()
    LaunchedEffect(model, loaded) {
        if (!loaded) return@LaunchedEffect
        if (info != com.shelf.launcher.data.Features.InfoAnim.NONE) a.animateTo(1f, tween(maxOf(fadeMs, 300), easing = FastOutSlowInEasing)) else a.snapTo(1f)
    }
    val rise = info == com.shelf.launcher.data.Features.InfoAnim.RISE && !LocalStillPart.current
    AsyncImage(
        model, contentDescription, contentScale = contentScale, alignment = alignment,
        onSuccess = { loaded = true },
        modifier = modifier.graphicsLayer {
            alpha = a.value
            if (rise) translationY = (1f - a.value) * 14f * density
        }
    )
}
