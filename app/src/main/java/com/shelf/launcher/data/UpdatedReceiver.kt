package com.shelf.launcher.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Update 274: after Voltcap updates itself (Settings › Updates & downloads › Download and install, or the home screen's
 * "Voltcap x is out"), Android stops the old Voltcap and left the handheld on its stock launcher (seen on the Flip 2:
 * the Retroid home with one Voltcap icon) until Home was pressed. Now, when Voltcap is the home app and the screen is
 * on, it opens itself again as soon as the new version is in. (Nothing happens on a locked or dark screen, or when
 * another launcher is the home app.)
 */
class UpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        runCatching {
            val pm = context.packageManager
            val home = pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            if (home?.activityInfo?.packageName != context.packageName) return
            val power = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            if (power?.isInteractive != true) return
            context.startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            )
        }
    }
}
