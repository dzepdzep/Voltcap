package com.shelf.launcher.device.tune

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import com.shelf.launcher.data.perf.PerfSettings

/**
 * ONE quiet notification while a game runs, shared by the overlays ([com.shelf.launcher.ui.PerfOverlayService])
 * and the performance tuner ([TuneService]) instead of one each. Both foreground services post it with
 * the same id, so it stays while either runs and goes when both stop.
 *
 * It shows what's active (power tier / AutoTDP with live watts / °C / fan, and the overlays with their
 * size and opacity) and has the same quick controls as the Quick Settings tiles (Power tier, Overlay):
 * Size, Opacity and Power, each one tap to cycle. The tiles stay the main place for these.
 */
object GameNotification {
    const val ID = 7710
    /**
     * Two channels, so each part can be hidden on its own (Settings › Device › Notifications, or Android's settings):
     * the power tuner (TDP) and the overlays. Android needs a notification while either service runs; a channel the
     * person switched off keeps it out of sight (the service keeps running). It goes on the power channel when the
     * power part is shown, else on the overlay one.
     */
    const val CHANNEL_POWER = "voltcap_ingame_power"
    const val CHANNEL_OVERLAY = "voltcap_ingame_overlay"
    /** Channels of older notifications (separate ones, then the single shared one), removed once. */
    private val OLD_CHANNELS = listOf("perf_overlay", "tuner", "voltcap_ingame")

    /** Which parts are running (set by the two services). */
    @Volatile var overlayOn = false
    @Volatile var tunerOn = false

    internal const val ACTION = "com.shelf.launcher.INGAME_CONTROL"
    internal const val EXTRA_WHAT = "what"

    /** Creates both channels (once). Public so Settings can open them before anything has run. */
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        // LOW, not MIN: Android raises a foreground service's MIN channel to LOW anyway.
        if (nm.getNotificationChannel(CHANNEL_POWER) == null) nm.createNotificationChannel(
            NotificationChannel(CHANNEL_POWER, "While playing: power tuner (TDP)", NotificationManager.IMPORTANCE_LOW).apply {
                description = "While the performance tuner holds your power tier / AutoTDP: live watts, °C, fan and a Power button. Switch it off to hide it; the tuner keeps working."
                setShowBadge(false)
            }
        )
        if (nm.getNotificationChannel(CHANNEL_OVERLAY) == null) nm.createNotificationChannel(
            NotificationChannel(CHANNEL_OVERLAY, "While playing: overlays", NotificationManager.IMPORTANCE_LOW).apply {
                description = "While the performance / music / trophy overlays run: their size and opacity buttons. Switch it off to hide it; the overlays keep working."
                setShowBadge(false)
            }
        )
        OLD_CHANNELS.forEach { id -> if (nm.getNotificationChannel(id) != null) runCatching { nm.deleteNotificationChannel(id) } }
    }

    /** False when the person switched [channel] off (in Voltcap's switch, which opens Android's page, or Android's settings). */
    fun shown(context: Context, channel: String): Boolean = runCatching {
        if (Build.VERSION.SDK_INT < 26) return@runCatching true
        val nm = context.getSystemService(NotificationManager::class.java) ?: return@runCatching true
        (nm.getNotificationChannel(channel)?.importance ?: NotificationManager.IMPORTANCE_LOW) != NotificationManager.IMPORTANCE_NONE
    }.getOrDefault(true)

    /** What the notification says right now. */
    fun build(context: Context): Notification {
        ensureChannels(context)
        // Only the parts whose channel is on are written in; it goes on the channel of a part that's shown.
        val showPower = tunerOn && shown(context, CHANNEL_POWER)
        val showOverlay = overlayOn && shown(context, CHANNEL_OVERLAY)
        val channel = when {
            showPower -> CHANNEL_POWER
            showOverlay -> CHANNEL_OVERLAY
            tunerOn -> CHANNEL_POWER   // both hidden: it stays out of sight on a switched-off channel
            else -> CHANNEL_OVERLAY
        }
        val powerPart = showPower || (!showOverlay && tunerOn)
        val overlayPart = showOverlay || (!showPower && overlayOn && !tunerOn)
        val cfg = PerfSettings.config(context)
        val tuner = Tuner.supported
        val st = Tuner.status.value
        val p = if (tuner) TuneStore(context).global() else null
        val powerLabel = p?.let {
            (if (it.autoTdp) "AutoTDP ${it.autoMode.label}" else it.tier.label) +
                (if (it.underclocked) " · Underclock ${it.effectiveUnderclock.label}" else "") +
                if (st.heatStep > 0) " · hot: ${st.heatStep} lower" else ""
        }
        val overlays = listOfNotNull(
            "Stats".takeIf { cfg.showDuringGames }, "Music".takeIf { cfg.musicDuringGames }, "Trophies".takeIf { cfg.raDuringGames }
        ).joinToString("+").ifEmpty { "off" }
        val title = when {
            powerPart && powerLabel != null -> "Playing · $powerLabel"
            else -> "Voltcap overlays"
        }
        // (Update 144: each reading can come first in the text, so each starts with a capital.)
        val live = if (powerPart) listOfNotNull(
            st.targetFps?.let { "Target $it fps" },
            st.powerW?.let { "%.1f W".format(it) },
            st.tempC?.let { "${it.toInt()} °C" },
            st.fanPercent?.let { "Fan $it %" }
        ) else emptyList()
        val overlayLine = if (overlayPart) "Overlay $overlays · size ${cfg.overlayScale}% · opacity ${cfg.overlayOpacity}%" else null
        val text = (live + listOfNotNull(overlayLine)).joinToString(" · ")
            .ifEmpty { if (powerPart) "Clock limits held against the system's boosts" else "Overlays ready" }
        val b = NotificationCompat.Builder(context, channel)
            .setSmallIcon(com.shelf.launcher.R.drawable.ic_stat_voltcap)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                listOfNotNull(
                    if (powerPart) "Power: $powerLabel" + (if (live.isNotEmpty()) " · " + live.joinToString(" · ") else "") else null,
                    overlayLine
                ).joinToString("\n").ifEmpty { text }
            ))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            // A tap opens the in-game panel: which overlays show, size, opacity, moving them, power.
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 9,
                    Intent(context, com.shelf.launcher.ui.OverlayControlActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setSubText("Tap for the overlay panel")
        // Three buttons at most (what Android shows): Size and Opacity for the overlays, Power for the tuner.
        if (overlayPart) {
            b.addAction(action(context, "size", "Size ${cfg.overlayScale}%", 1))
            b.addAction(action(context, "opacity", "Opacity ${cfg.overlayOpacity}%", 2))
        }
        if (powerPart && tuner && powerLabel != null) b.addAction(action(context, "power", "Power: $powerLabel", 3))
        return b.build()
    }

    private fun action(context: Context, what: String, label: String, code: Int) = NotificationCompat.Action(
        0, label,
        PendingIntent.getBroadcast(
            context, code,
            Intent(context, GameNotificationReceiver::class.java).setAction(ACTION).putExtra(EXTRA_WHAT, what),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    )

    /** Foreground with the shared notification (from either service). False if Android refuses. */
    fun foreground(service: Service): Boolean = runCatching {
        val n = build(service)
        if (Build.VERSION.SDK_INT >= 34) service.startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else service.startForeground(ID, n)
        true
    }.getOrElse {
        // (update 91) The full notification (or its service type) was refused: still go foreground with a plain one
        // (the manifest's type), so a service started with startForegroundService never stops without having
        // called startForeground — Android kills the whole app for that.
        runCatching {
            ensureChannels(service)
            val plain = NotificationCompat.Builder(service, if (tunerOn) CHANNEL_POWER else CHANNEL_OVERLAY)
                .setSmallIcon(com.shelf.launcher.R.drawable.ic_stat_voltcap)
                .setContentTitle("Voltcap")
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .setSilent(true)
                .build()
            service.startForeground(ID, plain)
            true
        }.getOrDefault(false)
    }

    /** Brings the text and buttons up to date (no-op when nothing runs). */
    fun refresh(context: Context) {
        if (!overlayOn && !tunerOn) return
        runCatching { context.getSystemService(NotificationManager::class.java)?.notify(ID, build(context)) }
    }

    /** One of the buttons: cycles that setting; overlays and tiles follow live. */
    internal fun handle(context: Context, what: String?) {
        when (what) {
            "size" -> PerfSettings.update(context) { c ->
                c.copy(overlayScale = when { c.overlayScale < 75 -> 75; c.overlayScale < 100 -> 100; c.overlayScale < 125 -> 125; c.overlayScale < 150 -> 150; else -> 75 })
            }
            "opacity" -> PerfSettings.update(context) { c ->
                c.copy(overlayOpacity = when { c.overlayOpacity > 75 -> 75; c.overlayOpacity > 50 -> 50; c.overlayOpacity > 30 -> 30; else -> 100 })
            }
            "power" -> if (Tuner.supported) runCatching { Tuner.cycleTile(context) }
        }
        // The Quick Settings tiles show the new state next time the shade opens.
        if (Build.VERSION.SDK_INT >= 24) runCatching {
            android.service.quicksettings.TileService.requestListeningState(context, android.content.ComponentName(context, TunerTileService::class.java))
            android.service.quicksettings.TileService.requestListeningState(context, android.content.ComponentName(context, OverlayTileService::class.java))
        }
        refresh(context)
    }
}

/** The shared notification's buttons (not exported: only Voltcap's own PendingIntents reach it). */
class GameNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != GameNotification.ACTION) return
        val pending = goAsync()
        // Tuner changes are root calls: off the main thread.
        Thread {
            try { GameNotification.handle(context.applicationContext, intent.getStringExtra(GameNotification.EXTRA_WHAT)) }
            finally { pending.finish() }
        }.start()
    }
}
