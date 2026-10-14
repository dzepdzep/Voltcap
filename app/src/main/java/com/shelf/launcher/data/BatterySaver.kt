package com.shelf.launcher.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Update 187: Voltcap's battery saver (Settings › Device › Battery saver). Two kinds of rest, both on by default:
 *
 *  - **Away** ([away]): Voltcap is still on a screen but you're not using it — a game or another app has the controller
 *    (on a two-screen handheld a game can run on one screen while Voltcap stays "resumed" on the other one, so Android
 *    never paused it). Voltcap then behaves as if it were behind the game: the animated background, moving covers,
 *    video clips, the slideshows and the home screen's timers all stop, until you come back (a press or a touch on
 *    Voltcap, or it gets the controller back).
 *  - **Low battery** ([low]): below [Features.saverLowAt] % and not plugged in, or while Android's own Battery Saver is
 *    on: the theme animation holds a still frame, covers stop moving, video clips wait and the card effects hold still.
 *    Moving through your games stays as it is. Plugged in, everything comes back.
 *
 * Nothing here touches the games themselves. [Features.saverMode] ALWAYS keeps the low-battery rest on all the time.
 */
object BatterySaver {
    enum class Mode(val label: String) { AUTO("When the battery is low"), ALWAYS("Always"), OFF("Off") }

    /** The battery's level (0–100) and whether it's plugged in; kept up to date while Voltcap is on screen. */
    var level by mutableIntStateOf(100)
        private set
    var plugged by mutableStateOf(false)
        private set
    /** Android's own Battery Saver is on. */
    var systemSaver by mutableStateOf(false)
        private set

    /** Voltcap doesn't have the controller (another app or a game on the other screen does), for a few seconds now. */
    var notInFront by mutableStateOf(false)
        private set

    /** Low-battery rest on now (see the class notes). Read in composition: follows every change. */
    val low: Boolean
        get() = when (Features.saverMode.state.value) {
            Mode.OFF -> false
            Mode.ALWAYS -> true
            Mode.AUTO -> !plugged && ((level in 1..Features.saverLowAt.state.value) || (Features.saverFollowSystem.state.value && systemSaver))
        }

    /** Update 188: a game started from Voltcap is running (GameMode, as Compose state). */
    var gameRunning by mutableStateOf(false)

    /**
     * Away rest on now (see the class notes). Update 188: only while a game started from Voltcap runs — on the AYN Thor
     * the other screen's own launcher can hold the controller after any app there closes, and Voltcap then rested
     * while you were using it.
     */
    val away: Boolean get() = Features.saverAway.state.value && notInFront && gameRunning

    /** Either rest. */
    val resting: Boolean get() = low || away

    /** Why it's resting, for the little note in Settings (null: not resting). */
    fun reason(): String? = when {
        away -> "Resting: your game has the controller."
        Features.saverMode.state.value == Mode.ALWAYS -> "Saving battery all the time."
        low && systemSaver && level > Features.saverLowAt.state.value -> "Saving battery: Android's Battery Saver is on."
        low -> "Saving battery: $level % left."
        else -> null
    }

    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val goAway = Runnable { notInFront = true; sync() }
    @Volatile private var registered = false

    /** Listens for Android's Battery Saver being switched (a rare broadcast; one receiver for the app). */
    fun ensure(context: Context) {
        val app = context.applicationContext
        val pm = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
        systemSaver = pm?.isPowerSaveMode == true
        if (registered) return
        registered = true
        runCatching {
            app.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    systemSaver = (c.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
                    sync()
                }
            }, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        }.onFailure { registered = false }
    }

    /** The battery's level and plug (from the home screen's battery listener). */
    fun onBattery(percent: Int, isPlugged: Boolean) {
        if (percent != level) level = percent
        if (isPlugged != plugged) plugged = isPlugged
        sync()
    }

    /**
     * Voltcap has the controller (true) or another app has it (false; Android's "top resumed" activity, which on a
     * two-screen handheld is the one you're using even when both screens show an app). Going away waits a moment so
     * a quick switch doesn't flicker; coming back is at once.
     */
    fun onFront(front: Boolean) {
        inFront = front
        main.removeCallbacks(goAway)
        if (front) { if (notInFront) { notInFront = false; sync() } }
        else main.postDelayed(goAway, 3_000L)
    }

    /** Whether Voltcap had the controller at the last [onFront]. */
    @Volatile private var inFront = true

    /**
     * A press or touch reached Voltcap (a touch on its second screen too): you're here. Cheap: only writes state when it
     * was away. If another app still has the controller, it rests again after 20 seconds without one.
     */
    fun poke() {
        if (notInFront) { notInFront = false; sync() }
        if (!inFront) { main.removeCallbacks(goAway); main.postDelayed(goAway, 20_000L) }
    }

    /** Voltcap left the screen (stopped): it rests anyway, so "away" starts over when it's back. */
    fun onStop() {
        inFront = true
        main.removeCallbacks(goAway)
        if (notInFront) notInFront = false
        sync()
    }

    /** Moving pictures follow the rest (they aren't Compose; see [AnimatedArt.setResting]). */
    fun sync() {
        runCatching { AnimatedArt.setResting(resting) }
    }

    // ---- The one-time note about it (first start, or the first start after this update) ----

    private const val PREFS = "battery_saver"

    fun introDue(context: Context): Boolean =
        !context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("intro_seen", false)

    fun introSeen(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("intro_seen", true).apply()
    }
}
