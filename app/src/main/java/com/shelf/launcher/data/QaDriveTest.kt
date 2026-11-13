package com.shelf.launcher.data

import android.content.Context
import java.io.File

/**
 * Update 529c: the real Google Drive test of save sync, in the isolated QA app only. The QA app never syncs ([CloudSync.qaPaused])
 * - except when ALL of this holds, checked before every sync:
 *  - it is the isolated QA app (`com.shelf.launcher.qa`, [QaVariant.isolated]);
 *  - the flag file `<QA app files>/voltcap_qa_drivetest` exists (made and removed by the QA script, never by the app);
 *  - saves only: Voltcap's own data (settings, profiles) is never synced in the test;
 *  - every save folder set up for sync lies inside the QA app's own files folder (synthetic saves the test made) - a real emulator
 *    folder anywhere in the list stops it.
 * The Google account is whatever the owner signed the QA app into (a spare one is recommended); nothing here signs in.
 */
internal object QaDriveTest {
    const val FLAG = "voltcap_qa_drivetest"

    fun allowed(context: Context, settings: CloudSyncSettings): Boolean = runCatching {
        if (!QaVariant.isolated(context)) return false
        val root = context.getExternalFilesDir(null) ?: return false
        if (!File(root, FLAG).isFile) return false
        confined(settings, root.canonicalPath)
    }.getOrDefault(false)

    /** Pure: saves only, no Voltcap data, at least one folder, and every folder a plain path inside [root]. */
    fun confined(settings: CloudSyncSettings, root: String): Boolean {
        if (!settings.syncSaves || settings.syncLauncherData) return false
        if (settings.folders.isEmpty()) return false
        val base = root.trimEnd('/') + "/"
        return settings.folders.all { f ->
            f.treeUri == null && runCatching { File(f.path).canonicalPath + "/" }.getOrNull()?.startsWith(base) == true
        }
    }
}
