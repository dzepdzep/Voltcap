package com.shelf.launcher.data

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.shelf.launcher.device.RootShell

/**
 * Everything Voltcap asks Android for, in one place: what it's for (in plain words), whether it's
 * given, and how to give it. Used by the first-start setup and Settings › Device › Permissions.
 */
enum class VoltPermission(
    val title: String,
    /** One line: what stops working without it. */
    val why: String,
    /** What to do on Android's screen once it opens. */
    val how: String,
    val required: Boolean = false,
    /** Asked only when its feature is first used: shown with its real state, but not nagged about. */
    val onDemand: Boolean = false
) {
    FILES(
        "Files on this device",
        "Finds your ROMs and syncs emulator saves.",
        "Switch on \"Allow access to manage all files\", then press Back.",
        required = true
    ),
    NOTIFICATIONS(
        "Show notifications",
        "Save-sync progress, achievement pop-ups and the weekly recap.",
        "Tap Allow."
    ),
    NOTIFICATION_ACCESS(
        "Notification access",
        "Music controls, the R1 notifications list, and pausing Voltcap's music when yours plays.",
        "Find Voltcap in the list, switch it on, confirm, then press Back."
    ),
    OVERLAY(
        "Display over other apps",
        "The FPS / performance readout on top of games.",
        "Switch on \"Allow display over other apps\", then press Back."
    ),
    SYSTEM_SETTINGS(
        "Modify system settings",
        "The brightness buttons in the R1 quick settings.",
        "Switch on \"Allow modifying system settings\", then press Back."
    ),
    DND(
        "Do Not Disturb access",
        "The Do Not Disturb tile in the R1 quick settings.",
        "Find Voltcap, switch it on, then press Back."
    ),
    BLUETOOTH(
        "Nearby devices (Bluetooth)",
        "Battery levels of Bluetooth controllers and headphones in the status bar.",
        "Tap Allow."
    ),
    USAGE_ACCESS(
        "Usage access",
        "Knows which game is really still open (Running games, play time after a switch, overlays).",
        "Find Voltcap, switch on \"Permit usage access\", then press Back."
    ),
    MICROPHONE(
        "Microphone (music-reactive themes)",
        "Lets music-reactive themes follow what's playing. Nothing is recorded or saved.",
        "Tap Allow.",
        onDemand = true
    ),
    FPS(
        "Read frame rate",
        "The FPS number in the performance readout. Android only gives this through a one-time grant.",
        "Done for you on AYN handhelds. Elsewhere: connect to a PC with USB debugging and run the command shown."
    );

    /** Runtime permissions Android asks for in its own small popup (several in one go). */
    val runtime: String?
        get() = when (this) {
            NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null
            BLUETOOTH -> if (Build.VERSION.SDK_INT >= 31) Manifest.permission.BLUETOOTH_CONNECT else null
            FILES -> if (Build.VERSION.SDK_INT < 30) Manifest.permission.READ_EXTERNAL_STORAGE else null
            MICROPHONE -> Manifest.permission.RECORD_AUDIO
            else -> null
        }
}

object Permissions {
    const val FPS_ADB_COMMAND = "adb shell pm grant com.shelf.launcher android.permission.DUMP"

    /** Whether this Android version even has the permission (older ones grant some automatically). */
    fun applies(p: VoltPermission): Boolean = when (p) {
        // Every version can have notifications switched off for the app, so this always applies.
        VoltPermission.BLUETOOTH -> Build.VERSION.SDK_INT >= 31
        VoltPermission.OVERLAY, VoltPermission.SYSTEM_SETTINGS, VoltPermission.DND -> Build.VERSION.SDK_INT >= 23
        else -> true
    }

    fun granted(context: Context, p: VoltPermission): Boolean = runCatching {
        if (!applies(p)) return@runCatching true
        // Update 410: the compatibility build has no notification listener, so there is nothing to grant.
        if (p == VoltPermission.NOTIFICATION_ACCESS && !ShelfNotificationListener.isBuiltIn(context)) return@runCatching true
        when (p) {
            VoltPermission.FILES ->
                if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
                else has(context, Manifest.permission.READ_EXTERNAL_STORAGE)
            // The real switch: Android 13's runtime grant AND the app's notifications not turned off in
            // Settings (any version) — a granted permission with notifications blocked shows nothing.
            VoltPermission.NOTIFICATIONS ->
                (Build.VERSION.SDK_INT < 33 || has(context, Manifest.permission.POST_NOTIFICATIONS)) &&
                    NotificationManagerCompat.from(context).areNotificationsEnabled()
            VoltPermission.NOTIFICATION_ACCESS -> ShelfNotificationListener.hasAccess(context)
            VoltPermission.OVERLAY -> Settings.canDrawOverlays(context) || opAllowed(context, "android:system_alert_window")
            VoltPermission.USAGE_ACCESS -> usageAccess(context)
            VoltPermission.MICROPHONE -> has(context, Manifest.permission.RECORD_AUDIO)
            VoltPermission.SYSTEM_SETTINGS -> Settings.System.canWrite(context)
            VoltPermission.DND -> (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .isNotificationPolicyAccessGranted
            VoltPermission.BLUETOOTH -> has(context, Manifest.permission.BLUETOOTH_CONNECT)
            VoltPermission.FPS -> has(context, "android.permission.DUMP")
        }
    }.getOrDefault(false)

    /** Everything not allowed right now, read fresh from Android every call (never cached). */
    fun missing(context: Context, includeOnDemand: Boolean = false): List<VoltPermission> =
        VoltPermission.values().filter { (includeOnDemand || !it.onDemand) && applies(it) && !granted(context, it) }

    /** The runtime popup still can do something for [p] (not yet granted); otherwise its settings screen. */
    fun needsPopup(context: Context, p: VoltPermission): Boolean = p.runtime?.let { !has(context, it) } == true

    /** A notification channel the person switched off in Android's settings (the app can't post there). */
    fun channelBlocked(context: Context, channel: String): Boolean = runCatching {
        if (Build.VERSION.SDK_INT < 26) return@runCatching false
        val nm = context.getSystemService(NotificationManager::class.java) ?: return@runCatching false
        nm.getNotificationChannel(channel)?.importance == NotificationManager.IMPORTANCE_NONE
    }.getOrDefault(false)

    /** Can Voltcap post to [channel] right now: permission, app switch and that channel all on. */
    fun canPost(context: Context, channel: String? = null): Boolean =
        granted(context, VoltPermission.NOTIFICATIONS) && (channel == null || !channelBlocked(context, channel))

    /** Usage access is an app-op; "default" falls back to the manifest permission like Android does. */
    fun usageAccess(context: Context): Boolean = runCatching {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        if (mode == AppOpsManager.MODE_DEFAULT) has(context, Manifest.permission.PACKAGE_USAGE_STATS)
        else mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    /** Android 8.0 reports a fresh overlay grant late through canDrawOverlays; the app-op is current. */
    private fun opAllowed(context: Context, op: String): Boolean = runCatching {
        if (Build.VERSION.SDK_INT < 23) return@runCatching true
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) ops.unsafeCheckOpNoThrow(op, Process.myUid(), context.packageName)
        else @Suppress("DEPRECATION") ops.checkOpNoThrow(op, Process.myUid(), context.packageName)
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    /** Android's screen for a special permission; null for the ones asked with a popup (or FPS). */
    fun settingsIntent(context: Context, p: VoltPermission): Intent? {
        val pkg = Uri.parse("package:${context.packageName}")
        return when (p) {
            VoltPermission.FILES -> if (Build.VERSION.SDK_INT >= 30)
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkg) else null
            VoltPermission.NOTIFICATION_ACCESS -> if (Build.VERSION.SDK_INT >= 30)
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    ComponentName(context, ShelfNotificationListener::class.java).flattenToString()
                )
            else Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            VoltPermission.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg)
            VoltPermission.SYSTEM_SETTINGS -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, pkg)
            VoltPermission.DND -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            VoltPermission.USAGE_ACCESS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).also {
                if (Build.VERSION.SDK_INT >= 29) it.data = pkg
            }
            // Granted (or older Android) but switched off for the app: only its notification page helps.
            VoltPermission.NOTIFICATIONS -> if (needsPopup(context, p)) null
                else if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
            // Asked "don't ask again": the app page is the only way left.
            VoltPermission.MICROPHONE, VoltPermission.BLUETOOTH -> if (needsPopup(context, p)) null
                else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
            else -> null
        }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Opens [p]'s screen; falls back to the general list if a device lacks the direct page. */
    fun openSettings(context: Context, p: VoltPermission): Boolean {
        val direct = settingsIntent(context, p) ?: return false
        if (runCatching { context.startActivity(direct) }.isSuccess) return true
        val fallback = when (p) {
            VoltPermission.FILES -> Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            VoltPermission.NOTIFICATION_ACCESS -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            VoltPermission.USAGE_ACCESS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(fallback) }.isSuccess
    }

    /** On firmware with a root command service (AYN), everything can be given in one go, no screens. */
    val canGrantDirectly: Boolean get() = RootShell.available

    /**
     * Grants every missing permission through the firmware's root service. Blocking (binder calls):
     * run off the main thread. Returns what is still missing afterwards.
     */
    fun grantAllDirectly(context: Context): List<VoltPermission> {
        if (!RootShell.available) return missing(context)
        val pkg = context.packageName
        val listener = ComponentName(context, ShelfNotificationListener::class.java).flattenToString()
        missing(context).forEach { p ->
            when (p) {
                VoltPermission.FILES -> RootShell.exec(
                    if (Build.VERSION.SDK_INT >= 30) "appops set --uid $pkg MANAGE_EXTERNAL_STORAGE allow"
                    else "pm grant $pkg android.permission.READ_EXTERNAL_STORAGE"
                )
                VoltPermission.NOTIFICATIONS -> RootShell.exec("pm grant $pkg android.permission.POST_NOTIFICATIONS")
                VoltPermission.NOTIFICATION_ACCESS -> RootShell.exec("cmd notification allow_listener $listener")
                VoltPermission.OVERLAY -> RootShell.exec("appops set $pkg SYSTEM_ALERT_WINDOW allow")
                VoltPermission.SYSTEM_SETTINGS -> RootShell.exec("appops set $pkg WRITE_SETTINGS allow")
                VoltPermission.DND -> RootShell.exec("cmd notification allow_dnd $pkg")
                VoltPermission.BLUETOOTH -> RootShell.exec("pm grant $pkg android.permission.BLUETOOTH_CONNECT")
                VoltPermission.USAGE_ACCESS -> RootShell.exec("appops set $pkg GET_USAGE_STATS allow")
                VoltPermission.MICROPHONE -> Unit // only when a music-reactive theme asks for it
                VoltPermission.FPS -> RootShell.exec("pm grant $pkg android.permission.DUMP")
            }
        }
        return missing(context)
    }

    private fun has(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
