package com.shelf.launcher.data

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Update 529 (save sync P0): the copy of a save made right before Drive's version replaces it.
 *
 * Before, this was best effort (`runCatching`): a backup that failed - storage full, folder not writable, an unreadable save -
 * was skipped silently and the save was replaced anyway. Now a backup is a precondition: [backup] copies the file while hashing
 * it, reads the copy back, and only returns when the copy's hash and size equal what was read. Anything else throws, and the
 * caller ([CloudSync.pull]) leaves the save on this device untouched.
 *
 * Every backup gets a small sidecar (`<name>.<stamp>.json`): folder, path, device, md5, size, the save's own date, when the
 * backup was made and why. Retention never removes a recent revision: at least [KEEP_NEWEST] per file are kept, and nothing
 * younger than [KEEP_DAYS] days is pruned. Pure JVM (no Android types) so it is unit-tested directly.
 */
internal object SaveBackups {
    const val KEEP_NEWEST = 10
    const val KEEP_DAYS = 30L
    private const val DAY_MS = 24L * 60 * 60 * 1000
    /** The sidecar's suffix; never counted as a backup itself. */
    const val META = ".json"

    data class Meta(
        val folder: String,
        val path: String,
        val device: String,
        val md5: String,
        val size: Long,
        val sourceModified: Long,
        val createdAt: Long,
        val reason: String,
        /** The Drive revision about to replace it (its md5), when known. */
        val replacedBy: String? = null
    )

    class BackupFailed(message: String) : Exception(message)

    /**
     * Copies [open]'s bytes to `<base>/<folder>/<path>.<stamp>` and verifies the copy. [base] must exist or be creatable.
     * Returns the backup file. Throws [BackupFailed] (and leaves no partial backup) when it can't be made or verified.
     */
    fun backup(
        base: File, folder: String, path: String, open: () -> InputStream, sourceModified: Long,
        device: String, reason: String, replacedBy: String? = null, now: Long = System.currentTimeMillis()
    ): File {
        val dir = File(File(base, safeSegment(folder)), path).parentFile ?: throw BackupFailed("No folder for $path")
        if (!dir.isDirectory && !dir.mkdirs()) throw BackupFailed("Couldn't create the backup folder for $path")
        val name = path.substringAfterLast('/')
        val out = uniqueTarget(dir, name, now)
        val part = File(dir, ".${out.name}.part")
        try {
            val md = MessageDigest.getInstance("MD5")
            var read = 0L
            open().use { input ->
                part.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        md.update(buf, 0, n); o.write(buf, 0, n); read += n
                    }
                    o.fd.sync()
                }
            }
            val want = hex(md.digest())
            if (part.length() != read) throw BackupFailed("Backup of $path is incomplete")
            if (!md5(part).equals(want, ignoreCase = true)) throw BackupFailed("Backup of $path doesn't match the save")
            if (!part.renameTo(out)) throw BackupFailed("Couldn't finish the backup of $path")
            // (Its date is when the backup was made: retention counts its age from that.)
            out.setLastModified(now)
            writeMeta(File(dir, out.name + META), Meta(folder, path, device, want, read, sourceModified, now, reason, replacedBy))
            prune(dir, name, now)
            return out
        } catch (e: BackupFailed) {
            part.delete(); throw e
        } catch (e: Exception) {
            part.delete(); throw BackupFailed("Backup of $path failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** Update 529c: one earlier version of a save, for "Earlier versions" (newest first). [meta] null: no readable sidecar. */
    data class Version(val file: File, val meta: Meta?) {
        val md5: String? get() = meta?.md5
        val createdAt: Long get() = meta?.createdAt ?: file.lastModified()
    }

    /** Every kept version of [path] in [folder] under [base], newest first. */
    fun versions(base: File, folder: String, path: String): List<Version> {
        val dir = File(File(base, safeSegment(folder)), path).parentFile ?: return emptyList()
        return revisions(dir, path.substringAfterLast('/')).map { Version(it, readMeta(File(dir, it.name + META))) }
    }

    class RestoreFailed(message: String) : Exception(message)

    /**
     * Update 529c (risk 5: rollback was manual): puts [version] back as the live save, the player's explicit choice. In order:
     * 1. the version itself is checked (its bytes against its sidecar md5) - a damaged copy is never restored;
     * 2. the live save, if there is one, is backed up first (verified, like any backup) - a restore never loses the current save;
     * 3. [write] puts the bytes in place; 4. [readBack] must return exactly the restored md5, or the backup from step 2 goes back.
     * Returns the md5 now live. Pure (I/O through the lambdas), unit-tested.
     */
    fun restore(
        base: File, folder: String, path: String, version: Version,
        live: (() -> InputStream)?, liveModified: Long, device: String,
        write: (File) -> Unit, readBack: () -> String?, now: Long = System.currentTimeMillis()
    ): String {
        val want = md5(version.file)
        version.meta?.md5?.let { if (!it.equals(want, ignoreCase = true)) throw RestoreFailed("That earlier version of $path is damaged - nothing was changed.") }
        val current = live?.let {
            backup(base, folder, path, it, liveModified, device, reason = "replaced by an earlier version (restored)", replacedBy = want, now = now)
        }
        try {
            write(version.file)
        } catch (e: Exception) {
            current?.let { runCatching { write(it) } }
            throw RestoreFailed("Couldn't put the earlier version of $path back: ${e.message ?: e.javaClass.simpleName}")
        }
        val got = readBack()
        if (got == null || !got.equals(want, ignoreCase = true)) {
            current?.let { runCatching { write(it) } }
            throw RestoreFailed("The restored $path didn't read back correctly - the save you had is back in place.")
        }
        return want
    }

    private fun unescape(v: String): String = buildString {
        var i = 0
        while (i < v.length) {
            val c = v[i]
            if (c == '\\' && i + 1 < v.length) { append(if (v[i + 1] == 'n') '\n' else v[i + 1]); i += 2 } else { append(c); i++ }
        }
    }

    /** Reads a sidecar written by [writeMeta]; null when missing or unreadable. */
    internal fun readMeta(f: File): Meta? = runCatching {
        if (!f.isFile) return null
        val t = f.readText()
        fun str(k: String) = Regex("\"" + Regex.escape(k) + "\":\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(t)?.groupValues?.get(1)?.let(::unescape)
        fun num(k: String) = Regex("\"" + Regex.escape(k) + "\":\\s*(-?\\d+)").find(t)?.groupValues?.get(1)?.toLong()
        Meta(str("folder") ?: return null, str("path") ?: return null, str("device") ?: "", str("md5") ?: return null,
            num("size") ?: 0L, num("sourceModified") ?: 0L, num("createdAt") ?: 0L, str("reason") ?: "", str("replacedBy"))
    }.getOrNull()

    /** The backups of [name] in [dir], newest first (sidecars and partial files left out). */
    fun revisions(dir: File, name: String): List<File> =
        dir.listFiles { f -> f.isFile && isRevisionOf(f.name, name) }?.sortedByDescending { it.name }.orEmpty()

    /** `name.yyyyMMdd-HHmmss` or `name.yyyyMMdd-HHmmss-N` - never another file that merely starts with the same name. */
    internal fun isRevisionOf(file: String, name: String): Boolean =
        file.startsWith("$name.") && !file.endsWith(META) && STAMP.matches(file.substring(name.length + 1))

    private val STAMP = Regex("\\d{8}-\\d{6}(-\\d+)?")

    /** Keeps the newest [KEEP_NEWEST] and everything younger than [KEEP_DAYS] days; older extras go (with their sidecar). */
    internal fun prune(dir: File, name: String, now: Long) {
        revisions(dir, name).drop(KEEP_NEWEST).forEach { f ->
            if (now - f.lastModified() > KEEP_DAYS * DAY_MS) {
                f.delete(); File(dir, f.name + META).delete()
            }
        }
    }

    private fun uniqueTarget(dir: File, name: String, now: Long): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(now))
        var f = File(dir, "$name.$stamp")
        var i = 1
        // (Two backups in one second never overwrite each other.)
        while (f.exists()) f = File(dir, "$name.$stamp-${i++}")
        return f
    }

    private fun writeMeta(f: File, m: Meta) {
        // (Plain text JSON, no Android types: the class is unit-tested on the JVM.)
        fun q(v: String) = "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
        val fields = listOf(
            "folder" to q(m.folder), "path" to q(m.path), "device" to q(m.device), "md5" to q(m.md5), "size" to m.size.toString(),
            "sourceModified" to m.sourceModified.toString(), "createdAt" to m.createdAt.toString(), "reason" to q(m.reason)
        ) + listOfNotNull(m.replacedBy?.let { "replacedBy" to q(it) })
        // (A missing sidecar never invalidates a verified backup: the bytes are what matter.)
        runCatching { f.writeText(fields.joinToString(",\n ", "{\n ", "\n}\n") { (k, v) -> q(k) + ": " + v }) }
    }

    private fun safeSegment(s: String) = s.replace(Regex("[\\\\/:*?\"<>|]"), "_")

    private fun md5(f: File): String {
        val md = MessageDigest.getInstance("MD5")
        f.inputStream().use { i ->
            val buf = ByteArray(64 * 1024)
            while (true) { val n = i.read(buf); if (n <= 0) break; md.update(buf, 0, n) }
        }
        return hex(md.digest())
    }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
}
