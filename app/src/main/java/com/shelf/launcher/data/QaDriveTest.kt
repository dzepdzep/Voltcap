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
        return settings.folders.all { f -> f.treeUri == null && inside(f.path, base) }
    }

    /**
     * Update 529e: a folder counts as inside [base] only if BOTH hold:
     *  - written out as an Android path ("." and ".." resolved by hand, the same on any computer - so the unit test
     *    also means something on a Windows build machine, where File.canonicalPath turns "/storage/..." into "C:\storage\...");
     *  - on hosts with Android-style paths (the device, Linux CI), after symlinks are followed (canonicalPath, as before 529e).
     * Either one failing keeps the real-Drive test off; the rule on the device is never looser than before.
     */
    private fun inside(path: String, base: String): Boolean {
        val plain = plainAndroidPath(path) ?: return false
        if (!(plain + "/").startsWith(base)) return false
        if (File.separatorChar != '/') return true
        return runCatching { File(path).canonicalPath + "/" }.getOrNull()?.startsWith(base) == true
    }

    /** "/a/./b/../c" -> "/a/c". Null for a relative path or one that climbs above "/". */
    internal fun plainAndroidPath(path: String): String? {
        if (!path.startsWith("/")) return null
        val parts = mutableListOf<String>()
        for (part in path.split('/')) when (part) {
            "", "." -> Unit
            ".." -> if (parts.isEmpty()) return null else parts.removeAt(parts.lastIndex)
            else -> parts.add(part)
        }
        return "/" + parts.joinToString("/")
    }
}
