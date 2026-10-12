package com.shelf.launcher.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult

/**
 * A pop-up for every achievement unlocked while a game runs: a heads-up
 * notification (it slides in over the emulator) with the badge, title,
 * description and points. Settings › Appearance › Achievements › "Unlock
 * pop-ups while playing". Android 13+ asks once for permission to notify.
 */
object UnlockNotifier {
    private const val CHANNEL = "achievement_unlocks"

    /** Really able to show one now: permission, the app's notification switch, and this channel (read live). */
    fun canNotify(context: Context, channel: String? = null): Boolean =
        Permissions.canPost(context, channel)

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Achievement unlocks", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "A pop-up when you unlock a RetroAchievements achievement while playing."
            }
        )
    }

    /** Shows one unlock. Loads the badge first (quietly skipped if it can't). */
    @android.annotation.SuppressLint("MissingPermission") // checked in canNotify()
    suspend fun show(context: Context, a: RaRecentAchievement) {
        if (!canNotify(context, CHANNEL)) return
        ensureChannel(context)
        val badge: Bitmap? = RaBadges.url(a.BadgeName)?.let { url ->
            runCatching {
                val result = Coil.imageLoader(context).execute(
                    ImageRequest.Builder(context).data(url).allowHardware(false).build()
                )
                (result as? SuccessResult)?.drawable?.toBitmap(128, 128)
            }.getOrNull()
        }
        val points = a.Points?.let { " · $it pts" } ?: ""
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(com.shelf.launcher.R.drawable.ic_stat_voltcap)
            .setContentTitle("🏆 ${a.Title ?: "Achievement unlocked"}")
            .setContentText((a.Description ?: "") + points)
            .setStyle(NotificationCompat.BigTextStyle().bigText((a.Description ?: "") + points))
            .setLargeIcon(badge)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setTimeoutAfter(15_000)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(a.AchievementID ?: a.hashCode(), notification)
        }
    }
}
