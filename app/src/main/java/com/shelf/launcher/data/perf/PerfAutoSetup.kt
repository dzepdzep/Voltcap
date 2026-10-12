package com.shelf.launcher.data.perf

import android.content.Context
import com.shelf.launcher.device.RootShell

/**
 * On handhelds whose firmware has a root command service (AYN), the two permissions the overlay
 * needs — "display over other apps" and DUMP (for FPS) — can be given without a computer or a trip
 * through Android's settings. Elsewhere this does nothing and the manual steps stay.
 */
object PerfAutoSetup {
    val possible: Boolean get() = RootShell.available

    /** Grants what's missing. Blocking (binder calls): run off the main thread. True when both are in place. */
    fun run(context: Context): Boolean {
        if (!RootShell.available) return false
        val pkg = context.packageName
        if (!SurfaceFlingerFpsSource.hasDump(context)) {
            RootShell.exec("pm grant $pkg android.permission.DUMP")
        }
        if (!com.shelf.launcher.ui.PerfOverlayService.canDraw(context)) {
            RootShell.exec("appops set $pkg SYSTEM_ALERT_WINDOW allow")
        }
        return SurfaceFlingerFpsSource.hasDump(context) && com.shelf.launcher.ui.PerfOverlayService.canDraw(context)
    }
}
