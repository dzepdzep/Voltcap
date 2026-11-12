package com.shelf.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import com.shelf.launcher.data.IntroStyle
import com.shelf.launcher.data.Profiles
import kotlinx.coroutines.launch

/*
 * Connected Ignition: the start-up when it asks "Who's playing?" (and the intro is Full or Short, system animations on).
 * ONE window, ONE clock: the intro is drawn exactly as VoltcapIntro draws it (drawIntro, same timing, sound and controller-light
 * hit), but it never fades to the home — at the final shine its outline opens into the profile row (IgnitionWire.kt) and the
 * picker settles on the same stage (ProfileSelectScreen in the activity, not a Dialog). Nothing of the home is composed until a
 * profile is chosen (LauncherScreen keeps contentVisible false), so no pop-up can come between. Settled = no frame is drawn.
 * Every other start-up (Categories, first run, 32-bit / Off intro, reduced motion, a restart from a game) is exactly as before.
 */
@Composable
internal fun IgnitionStage(
    style: IntroStyle,
    profiles: List<Profiles.Profile>,
    activeId: Int,
    raNames: List<String?>,
    steamAvatar: String?,
    onBolt: () -> Unit,
    /** The picker has settled (or was skipped to): heavy start-up work may go ahead (StartupGate). */
    onSettled: () -> Unit,
    /** A profile was chosen (its light ran in): the home is built under the stage, which then fades. */
    onPick: (Int) -> Unit,
    /** Sprint 527: the choice was accepted (its light starts): build the home under the stage now. */
    onAccept: (Int) -> Unit = {},
    /** B at start-up: keep the profile that played last. */
    onKeep: () -> Unit,
    /** The stage has faded out after [onPick] / [onKeep]: remove it. */
    onGone: () -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("IgnitionStage")
    val timing = if (style == IntroStyle.SHORT) SHORT_TIMING else FULL_TIMING
    val tm = if (style == IntroStyle.SHORT) IG_SHORT else IG_FULL
    val clock = remember { mutableFloatStateOf(0f) }
    var lite by remember { mutableStateOf(false) }
    val geo = remember { IntroGeo() }
    val sounds = LocalUiSounds.current
    val bolt by rememberUpdatedState(onBolt)
    val settledCb by rememberUpdatedState(onSettled)
    val once = remember { booleanArrayOf(false, false) }
    val settle = { if (!once[0]) { once[0] = true; StartupGate.release(); settledCb() } }
    // Sprint 527 (owner: the skip was a one-frame cut): a skip settles the picker at once (input goes to it straight away) and
    // the moment it was skipped crossfades into it over [SKIP_FADE_MS] - the intro as it was fading out, the picker fading in.
    val skippedAt = remember { mutableFloatStateOf(-1f) }
    val skipFade = remember { Animatable(1f) }
    val skipScope = rememberCoroutineScope()
    val skip = {
        if (clock.floatValue < tm.settled) {
            skippedAt.floatValue = clock.floatValue
            clock.floatValue = tm.settled
            if (!once[1]) { once[1] = true; bolt() }
            skipScope.launch { skipFade.snapTo(0f); skipFade.animateTo(1f, tween(SKIP_FADE_MS, easing = FastOutSlowInEasing)) }
        }
        settle()
    }
    LaunchedEffect(Unit) {
        var last = introWarmUp()
        sounds?.playWhenReady(if (timing.total >= 1500f) UiSound.INTRO else UiSound.LAUNCH)
        var avg = 16f
        var frames = 0
        // Runs until settled (or skipped), then stops: the settled picker draws nothing until a press.
        while (clock.floatValue < tm.settled) {
            val now = withFrameNanos { it }
            val dt = (now - last) / 1_000_000f
            last = now
            frames++
            avg = avg * 0.85f + dt * 0.15f
            if (!lite && frames > 8 && avg > 33f) lite = true
            if (clock.floatValue >= tm.settled) break
            clock.floatValue = (clock.floatValue + dt.coerceAtMost(100f)).coerceAtMost(tm.settled)
            if (!once[1] && clock.floatValue >= timing.hit) { once[1] = true; bolt() }
        }
        settle()
    }
    val reveal = remember { IgnitionReveal(clock, timing, tm) }
    val out = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    fun leave(then: () -> Unit) { scope.launch { then(); out.animateTo(1f, tween(380, easing = FastOutSlowInEasing)); onGone() } }
    val measurer = rememberTextMeasurer(cacheSize = 16)
    val density = LocalDensity.current
    val wordCache = remember { arrayOfNulls<Wordmark>(1) }
    val wordKey = remember { floatArrayOf(-1f) }
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - out.value }) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(androidx.compose.ui.graphics.Color(0xFF08070B))
            val sk = skippedAt.floatValue
            // (a skip: the intro as it was at that moment, fading out under the picker fading in)
            val t = if (sk >= 0f && skipFade.value < 1f) sk else clock.floatValue
            if (t > tm.recedeTo + 200f) return@Canvas // (the picker's own backdrop has taken over by now)
            val r = minOf(0.2f * size.width, 0.26f * size.height)
            val fs = 0.40f * r
            if (wordKey[0] != fs || wordCache[0] == null) { wordCache[0] = measureWordmark(measurer, density, fs); wordKey[0] = fs }
            if (sk >= 0f && skipFade.value < 1f) {
                val fadeA = 1f - skipFade.value
                drawContext.canvas.saveLayer(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height), androidx.compose.ui.graphics.Paint().apply { alpha = fadeA })
                drawIntro(geo, timing, t, r, wordCache[0]!!, lite, keep = true, rec = IgnK.ph(t, tm.recedeFrom, tm.recedeTo))
                drawContext.canvas.restore()
            } else drawIntro(geo, timing, t, r, wordCache[0]!!, lite, keep = true, rec = IgnK.ph(t, tm.recedeFrom, tm.recedeTo))
        }
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (skippedAt.floatValue >= 0f) skipFade.value else 1f }) {
        ProfileSelectScreen(
            profiles = profiles, activeId = activeId, raNames = raNames, steamAvatar = steamAvatar, atStart = true,
            onPick = { id -> leave { onPick(id) } },
            onAccept = onAccept,
            onSkip = { leave { onKeep() } },
            inStage = true, stage = reveal, onStageSkip = { skip() }
        )
        }
    }
}

/** Sprint 527: how long a skipped intro takes to crossfade into the settled picker (short: a skip must stay quick). */
internal const val SKIP_FADE_MS = 200

/** Connected Ignition runs for this start-up: the intro is Full or Short and system animations are on. Pure. */
internal fun ignitionIntro(style: IntroStyle, animatorScale: Float): Boolean =
    (style == IntroStyle.FULL || style == IntroStyle.SHORT) && animatorScale != 0f
