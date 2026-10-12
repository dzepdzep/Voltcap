package com.shelf.launcher.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.shelf.launcher.MainActivity

/** What a sync result means for the person — picks the corner notice's look and sound. */
enum class SyncNoticeKind { DONE, RESTORED, PROBLEM }

/**
 * Notifications for background save syncs: one per game, replaced in place, grouped under one
 * "Voltcap · Google Drive" stack when several games synced, tap opens Voltcap. Each kind has its own
 * small cloud icon, Voltcap's orange, and its own soft sound (res/raw/sync_done, sync_alert) — on
 * separate channels, so people can mute just the good news or just the problems in Android's settings.
 * The "working" notice WorkManager may need is on a silent channel. Only used for background work; with
 * Voltcap on screen the corner notice (and its in-app sound) does the job.
 */
object SyncNotifier {
    private const val CHANNEL_DONE = "save_sync_done"
    private const val CHANNEL_ALERT = "save_sync_alert"
    private const val CHANNEL_WORKING = "save_sync_working"
    private const val OLD_CHANNEL = "save_sync"
    private const val GROUP = "voltcap_save_sync"
    private const val SUMMARY_ID = 0x5A_4FFE
    private const val ID_BASE = 0x5A_5000
    private const val SIGN_IN_ID = 0x5A_4FFF
    private const val PREFS = "sync_status"
    private const val KEY_SIGN_IN_POSTED = "sign_in_notified"
    private const val VOLTCAP_ORANGE = 0xFFF59A2C.toInt()

    /**
     * True while Voltcap is the app in front (set by the view model: on when it resumes, off when a game
     * starts). Background syncs stay silent-in-the-shade while it's true. Not persisted: a fresh process
     * is "not in front" until the launcher resumes.
     */
    @Volatile var appInForeground = false

    fun enabled(context: Context): Boolean {
        CloudSync.load(context)
        return !CloudSync.state.value.settings.syncNotificationsOff
    }

    /** Which corner notice / sound a result gets (null: nothing to say). */
    fun kindFor(outcome: SyncOutcome): SyncNoticeKind? = when {
        outcome.needsSignIn || outcome.retry || outcome.conflicts > 0 -> SyncNoticeKind.PROBLEM
        !outcome.ok -> null
        outcome.downloaded > 0 -> SyncNoticeKind.RESTORED
        outcome.uploaded > 0 -> SyncNoticeKind.DONE
        else -> null
    }

    private fun soundUri(context: Context, raw: Int): android.net.Uri =
        android.net.Uri.parse("android.resource://${context.packageName}/$raw")

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_DONE) != null) return
        runCatching { manager.deleteNotificationChannel(OLD_CHANNEL) }
        val attrs = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, "Saves backed up", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When your game saves reached Google Drive or were restored to this device. A soft chime."
                setSound(soundUri(context, com.shelf.launcher.R.raw.sync_done), attrs)
                enableVibration(false)
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERT, "Save sync problems", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Offline, a save conflict, or Google Drive needs you to sign in again."
                setSound(soundUri(context, com.shelf.launcher.R.raw.sync_alert), attrs)
                enableVibration(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_WORKING, "Backing up saves", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Shown only while saves are being sent, on older Android versions."
                setSound(null, null)
                setShowBadge(false)
            }
        )
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun build(context: Context, kind: SyncNoticeKind?, title: String, text: String): Notification {
        ensureChannels(context)
        val (channel, icon, raw) = when (kind) {
            SyncNoticeKind.DONE -> Triple(CHANNEL_DONE, com.shelf.launcher.R.drawable.ic_stat_sync_done, com.shelf.launcher.R.raw.sync_done)
            SyncNoticeKind.RESTORED -> Triple(CHANNEL_DONE, com.shelf.launcher.R.drawable.ic_stat_sync_down, com.shelf.launcher.R.raw.sync_done)
            SyncNoticeKind.PROBLEM -> Triple(CHANNEL_ALERT, com.shelf.launcher.R.drawable.ic_stat_sync_alert, com.shelf.launcher.R.raw.sync_alert)
            null -> Triple(CHANNEL_WORKING, com.shelf.launcher.R.drawable.ic_stat_sync_up, 0)
        }
        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(icon)
            .setColor(VOLTCAP_ORANGE)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText("Google Drive")
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setShowWhen(kind != null)
            .setPriority(if (kind == null) NotificationCompat.PRIORITY_MIN else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (kind == SyncNoticeKind.PROBLEM) NotificationCompat.CATEGORY_ERROR else NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openApp(context))
            .setAutoCancel(kind != null)
            .setOnlyAlertOnce(true)
            .setSilent(kind == null)
            .apply {
                if (kind != null) setGroup(GROUP)
                // Android 7 doesn't use channels: the sound goes on the notification itself.
                if (Build.VERSION.SDK_INT < 26 && raw != 0) setSound(soundUri(context, raw))
            }
            .build()
    }

    /** A minimal notification for expedited work on older Android, where WorkManager runs it as a foreground service. */
    fun workingNotification(context: Context): Notification =
        build(context, null, "Backing up saves", "Sending your game saves to Google Drive…")

    /** Keeps several games' results stacked under one "Google Drive" heading. */
    @android.annotation.SuppressLint("MissingPermission") // checked in UnlockNotifier.canNotify
    private fun postSummary(context: Context) {
        if (!UnlockNotifier.canNotify(context)) return
        val summary = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(com.shelf.launcher.R.drawable.ic_stat_sync_done)
            .setColor(VOLTCAP_ORANGE)
            .setContentTitle("Save sync")
            .setSubText("Google Drive")
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(SUMMARY_ID, summary) }
    }

    @android.annotation.SuppressLint("MissingPermission") // checked in UnlockNotifier.canNotify
    private fun post(context: Context, id: Int, n: Notification) {
        if (!UnlockNotifier.canNotify(context)) return
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
        postSummary(context)
    }

    private fun idFor(key: String) = ID_BASE + (key.hashCode() and 0xFFFF)

    /** The one-line wording for the corner notice and notifications; null when there's nothing to say. */
    fun messageFor(name: String, outcome: SyncOutcome): String? = when {
        outcome.needsSignIn -> "Sign in to Google Drive again — saves can't be backed up until you do"
        outcome.retry -> "Saves not backed up yet — offline, Voltcap will try again"
        outcome.conflicts > 0 -> "Save conflict — $name · open its sync to choose"
        !outcome.ok -> null
        outcome.downloaded > 0 -> "Saves restored — $name"
        outcome.uploaded > 0 -> "Saves safe on Google Drive — $name · ${files(outcome.uploaded)}"
        else -> null
    }

    /**
     * Posts the result of a background scoped sync. [firstAttempt]: false on WorkManager retries, so an
     * offline/failed note isn't re-posted every time.
     */
    fun afterBackgroundSync(context: Context, key: String, name: String, outcome: SyncOutcome, firstAttempt: Boolean = true) {
        val app = context.applicationContext
        if (outcome.ok && outcome.conflicts == 0 && !outcome.needsSignIn) {
            // A good sync means any earlier sign-in complaint is over; a new one may notify again.
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_SIGN_IN_POSTED, false).apply()
        }
        if (appInForeground || !enabled(app)) return
        when {
            outcome.needsSignIn -> signInNeeded(app)
            outcome.retry -> if (firstAttempt) post(app, idFor(key), build(app, SyncNoticeKind.PROBLEM, "Saves not backed up yet", "$name · Offline — Voltcap will try again by itself"))
            outcome.conflicts > 0 -> post(app, idFor(key), build(app, SyncNoticeKind.PROBLEM, "Save conflict", "$name · Open Voltcap to choose which save to keep"))
            outcome.ok && outcome.downloaded > 0 -> post(
                app, idFor(key),
                build(app, SyncNoticeKind.RESTORED, "Saves restored", "$name · ${files(outcome.downloaded)} brought to this device")
            )
            outcome.ok && outcome.uploaded > 0 -> post(
                app, idFor(key),
                build(app, SyncNoticeKind.DONE, "Saves safe on Google Drive", "$name · ${files(outcome.uploaded)} backed up")
            )
        }
    }

    private fun files(n: Int) = "$n file${if (n == 1) "" else "s"}"

    /** "Sign in to Google Drive again" — once, until a sync works again. */
    fun signInNeeded(context: Context) {
        val app = context.applicationContext
        if (appInForeground || !enabled(app)) return
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_SIGN_IN_POSTED, false)) return
        prefs.edit().putBoolean(KEY_SIGN_IN_POSTED, true).apply()
        post(app, SIGN_IN_ID, build(app, SyncNoticeKind.PROBLEM, "Sign in to Google Drive again", "Your saves can't be backed up until you do — open Voltcap › Settings › Sync."))
    }
}
