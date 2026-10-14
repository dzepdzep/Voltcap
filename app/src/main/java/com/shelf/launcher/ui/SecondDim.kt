package com.shelf.launcher.ui

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.Window
import android.view.WindowManager
import com.shelf.launcher.data.Features

/**
 * Update 188: the second screen dims when nobody uses it (Settings › Screens › Dim the second screen; also under
 * Settings › Device › Battery saver). After [Features.secondDimAfter] seconds without a touch there or a press on Voltcap,
 * a black layer fades over it (and its backlight is turned down), also while a game runs on the other screen — that's
 * when Voltcap's second screen otherwise sat at full brightness the whole game. The first touch on it only wakes it
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

    private fun dim() {
        val v = layer ?: return
        dimmed = true
        v.animate().cancel()
        v.visibility = View.VISIBLE
        v.animate().alpha(Features.secondDimLevel.value / 100f).setDuration(900L).start()
        backlight(true)
    }

    private fun undim() {
        dimmed = false
        layer?.let { v -> v.animate().cancel(); v.alpha = 0f; v.visibility = View.GONE }
        backlight(false)
    }

    /** The second screen's own backlight down while dimmed (this window's brightness: only its display). */
    private fun backlight(low: Boolean) {
        val w = window ?: return
        runCatching {
            val lp = w.attributes
            val want = if (low && Features.secondDimBacklight.value) 0.02f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            if (lp.screenBrightness != want) { lp.screenBrightness = want; w.attributes = lp }
        }
    }
}
