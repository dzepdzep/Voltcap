package com.shelf.launcher.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Mid-game sync, update 529 - ISOLATED QA APP ONLY (com.shelf.launcher.qa; nothing happens in Voltcap itself). The isolated QA
 * app never talks to Google Drive, so this measures the part of a mid-game upload that happens on the handheld, with synthetic
 * saves only (`<QA files>/qa_saves`, written by tools/qa/device/midsave529.sh):
 *  - SaveObserver's real loop finds a change (file events or polling), waits for it to settle, and "uploads" it: a snapshot is
 *    copied to `qa_drive/` - refused when the bytes changed since the decision (the same rule as CloudSync.push), and checked
 *    after the copy the way Drive's checksum is;
 *  - [QaDryWatchWorker] does what SaveWatchWorker does for a game: every minute it brings the watcher back if Android ended
 *    the process.
 * Each step is logged by SaveSyncTrace (`adb logcat -s VoltcapSync`); the "uploaded" revisions are listed in qa_drive/log.txt.
 */
internal object QaSaveDryRun {
    const val KEY = "qa-dry-run"
    private val sent = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun dir(context: Context): File = File(context.getExternalFilesDir(null), "qa_saves")
    private fun drive(dir: File) = File(dir.parentFile, "qa_drive").apply { mkdirs() }

    private fun md5(f: File): String {
        val md = MessageDigest.getInstance("MD5")
        f.inputStream().use { i -> val b = ByteArray(64 * 1024); while (true) { val n = i.read(b); if (n <= 0) break; md.update(b, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** The synthetic saves whose content differs from the last revision "uploaded" (also after a process restart: from qa_drive). */
    fun changed(dir: File): List<CloudSync.ChangedSave> {
        val d = drive(dir)
        return dir.listFiles { f -> f.isFile && !f.name.startsWith(".") }.orEmpty().mapNotNull { f ->
            val m = runCatching { md5(f) }.getOrNull() ?: return@mapNotNull null
            val last = sent[f.name] ?: File(d, f.name).takeIf { it.isFile }?.let { md5(it) }?.also { sent[f.name] = it }
            if (m == last) null else CloudSync.ChangedSave("qa", f.name, f.length(), f.lastModified(), m)
        }
    }

    fun upload(dir: File) {
        val d = drive(dir)
        for (c in changed(dir)) {
            val src = File(dir, c.path)
            val tmp = File(d, ".${c.path}.part")
            runCatching {
                src.copyTo(tmp, overwrite = true)
                val snap = md5(tmp)
                if (snap != c.md5) { tmp.delete(); SaveSyncTrace.mark(KEY, "still-writing ${c.path}", 0L); return@runCatching }
                val out = File(d, c.path)
                if (!tmp.renameTo(out)) { out.delete(); tmp.renameTo(out) }
                val back = md5(out)
                if (back == snap) {
                    sent[c.path] = snap
                    File(d, "log.txt").appendText("${System.currentTimeMillis()} ${c.path} ${c.size} mtime=${c.modified} md5=$snap\n")
                    SaveSyncTrace.mark(KEY, "verified ${c.path} md5=${snap.take(12)}", 0L)
                }
            }.onFailure { tmp.delete() }
        }
    }

    fun start(context: Context) {
        val app = context.applicationContext
        if (!QaVariant.isolated(app)) return
        File(app.filesDir, FLAG).writeText("on")
        SaveObserver.startDry(app, dir(app))
        QaDryWatchWorker.enqueue(app, 60_000L)
    }

    fun stop(context: Context) {
        val app = context.applicationContext
        File(app.filesDir, FLAG).delete()
        runCatching { WorkManager.getInstance(app).cancelUniqueWork(QaDryWatchWorker.NAME) }
        if (SaveObserver.watchingKey() == KEY) SaveObserver.stop()
    }

    const val FLAG = "qa_dry_run_on"
}

/** Isolated QA app only: [QaSaveDryRun]'s stand-in for SaveWatchWorker - brings the watcher back every minute while the run is on. */
class QaDryWatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext
        if (!QaVariant.isolated(app) || !File(app.filesDir, QaSaveDryRun.FLAG).isFile) return Result.success()
        val wasWatching = SaveObserver.watchingKey() == QaSaveDryRun.KEY
        SaveObserver.startDry(app, QaSaveDryRun.dir(app))
        if (!wasWatching) SaveSyncTrace.mark(QaSaveDryRun.KEY, "watcher-restarted", 0L)
        enqueue(app, 60_000L)
        return Result.success()
    }

    companion object {
        const val NAME = "voltcap-qa-dry-watch"
        fun enqueue(context: Context, delayMs: Long) {
            val r = OneTimeWorkRequestBuilder<QaDryWatchWorker>().setInitialDelay(delayMs, TimeUnit.MILLISECONDS).build()
            runCatching { WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, r) }
        }
    }
}
