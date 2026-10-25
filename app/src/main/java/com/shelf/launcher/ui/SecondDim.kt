package com.shelf.launcher.ui

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import com.shelf.launcher.data.Features

/**
 * Update 188: the second screen dims when nobody uses it (Settings › Screens › Dim the second screen; also under
 * Settings › Device › Battery saver). After [Features.secondDimAfter] seconds without a touch there or a press on Voltcap,
 * a black layer fades part-way over it (update 189: the art goes black-and-white, see [secondDimArt]) and its backlight
 * is turned down. (Update 326: while a game is in front, Voltcap's second-screen window steps aside for the in-game one
 * and this waits — [hold].) The first touch on it only wakes it
 * (that touch doesn't press anything); a press on Voltcap wakes it too.
 *
 * Plain Android views, not Compose: while a game is in front Voltcap's Compose drawing is paused, but its second screen
 * window stays up, so the layer has to work without it.
 */
internal object SecondDim {
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private var layer: View? = null
    private var window: Window? = null
    @Volatile private var last = SystemClock.elapsedRealtime()
    @Volatile var dimmed = false
        private set
    private val check = Runnable { tick() }

    /** The second screen's window and its black layer (over everything else there). Main thread. */
    fun attach(w: Window?, v: View) {
        window = w
        layer = v
        dimmed = false
        v.alpha = 0f
        v.visibility = View.GONE
        v.isClickable = true
        // The touch that wakes it is used up by the layer (it doesn't press what's under it).
        v.setOnClickListener { poke() }
        poke()
    }

    /** The second screen went away. Main thread. */
    fun detach(v: View) {
        if (layer !== v) return
        main.removeCallbacks(check)
        layer = null
        window = null
        dimmed = false
    }

    private fun afterMs(): Long = Features.secondDimAfter.value * 1000L

    /** Someone is using Voltcap (a touch on the second screen, a press or touch on the main one). Any thread. */
    fun poke() {
        last = SystemClock.elapsedRealtime()
        if (Looper.myLooper() == Looper.getMainLooper()) wakeAndSchedule() else main.post { wakeAndSchedule() }
    }

    /** A setting changed: start over. */
    fun settingsChanged() = poke()

    /**
     * Update 326: Voltcap's second-screen window steps aside while Voltcap is stopped (a game in front — SecondScreenHost),
     * so its dimming stops too: no timer, its layer and the grey art cleared. The grey art is a shared value, and the
     * in-game second screen (CompanionActivity) reads it as well — it stayed black-and-white for the whole game, since
     * nothing there woke it. [poke] starts it again when Voltcap is back. Main thread.
     */
    fun hold() {
        main.removeCallbacks(check)
        if (dimmed) undim() else if (artGrey.floatValue != 0f) greyTo(0f, 0L)
    }

    private fun wakeAndSchedule() {
        if (dimmed) undim()
        main.removeCallbacks(check)
        val a = afterMs()
        if (a > 0 && layer != null) main.postDelayed(check, a)
    }

    private fun tick() {
        val a = afterMs()
        if (a <= 0 || layer == null) return
        val since = SystemClock.elapsedRealtime() - last
        if (since >= a) dim() else main.postDelayed(check, a - since)
    }

    /**
     * Update 189: how far the art has gone grey (0…1), read while drawing by [secondDimArt] — the game's pictures and
     * logos turn black-and-white and darker while the rest (buttons, text, icons) only dims with the layer, so the
     * screen stays readable and usable.
     */
    val artGrey = androidx.compose.runtime.mutableFloatStateOf(0f)
    private var greyAnim: android.animation.ValueAnimator? = null

    private fun greyTo(target: Float, ms: Long) {
        greyAnim?.cancel()
        if (ms <= 0L) { artGrey.floatValue = target; return }
        greyAnim = android.animation.ValueAnimator.ofFloat(artGrey.floatValue, target).apply {
            duration = ms
            addUpdateListener { artGrey.floatValue = it.animatedValue as Float }
            start()
        }
    }

    private fun dim() {
        val v = layer ?: return
        dimmed = true
        v.animate().cancel()
        v.visibility = View.VISIBLE
        // (Update 189: part-way dark, not blacked out: what's there stays readable and can be tapped.)
        v.animate().alpha(Features.secondDimLevel.value / 100f).setDuration(900L).start()
        greyTo(1f, 900L)
        backlight(true)
    }

    private fun undim() {
        dimmed = false
        layer?.let { v -> v.animate().cancel(); v.alpha = 0f; v.visibility = View.GONE }
        greyTo(0f, 0L)
        backlight(false)
    }

    /** The second screen's own backlight down while dimmed (this window's brightness: only its display). */
    private fun backlight(low: Boolean) {
        val w = window ?: return
        runCatching {
            val lp = w.attributes
            val want = if (low && Features.secondDimBacklight.value) 0.12f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            if (lp.screenBrightness != want) { lp.screenBrightness = want; w.attributes = lp }
        }
    }
}

/**
 * Update 189: a game picture or logo on the second screen: black-and-white and darker while the second screen is dimmed
 * ([SecondDim.artGrey], read while drawing — nothing recomposes). Android 12+: a colour filter on the layer; older: only
 * darker.
 */
internal fun androidx.compose.ui.Modifier.secondDimArt(): androidx.compose.ui.Modifier =
    if (android.os.Build.VERSION.SDK_INT >= 31) this.then(androidx.compose.ui.Modifier.graphicsLayerDimArt())
    else this.then(androidx.compose.ui.Modifier.drawWithContentDarken())

private fun androidx.compose.ui.Modifier.drawWithContentDarken(): androidx.compose.ui.Modifier =
    this.then(androidx.compose.ui.Modifier.drawWithContent {
        drawContent()
        val g = SecondDim.artGrey.floatValue
        if (g > 0.01f) drawRect(androidx.compose.ui.graphics.Color.Black, alpha = 0.35f * g)
    })

@androidx.annotation.RequiresApi(31)
private fun androidx.compose.ui.Modifier.graphicsLayerDimArt(): androidx.compose.ui.Modifier =
    this.then(androidx.compose.ui.Modifier.graphicsLayer {
        val g = SecondDim.artGrey.floatValue
        renderEffect = if (g < 0.02f) null else DimArtEffects.of(g)
    })

/** The art filter in 10 steps (made once each). */
@androidx.annotation.RequiresApi(31)
private object DimArtEffects {
    private val cache = arrayOfNulls<androidx.compose.ui.graphics.RenderEffect>(11)
    fun of(g: Float): androidx.compose.ui.graphics.RenderEffect {
        val i = (g.coerceIn(0f, 1f) * 10f).roundToIntSafe()
        cache[i]?.let { return it }
        val k = i / 10f
        val m = android.graphics.ColorMatrix().apply { setSaturation(1f - k) }
        val dark = 1f - 0.3f * k
        m.postConcat(android.graphics.ColorMatrix().apply { setScale(dark, dark, dark, 1f) })
        val e = android.graphics.RenderEffect.createColorFilterEffect(android.graphics.ColorMatrixColorFilter(m)).asComposeRenderEffect()
        cache[i] = e
        return e
    }
    private fun Float.roundToIntSafe(): Int = kotlin.math.round(this).toInt().coerceIn(0, 10)
}
