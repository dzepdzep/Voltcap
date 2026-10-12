package com.shelf.launcher.data

import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Update 148: the preview clips saved on the device (Settings › Metadata › Everything at once › Save clips on the
 * device), so they play without Wi-Fi and start at once. One file per game in the app's own storage, up to the size
 * limit picked; past it, no more are saved (the others still stream). Cleared with the button next to it.
 */
object ClipStore {
    private var dir: File? = null
    private val client by lazy {
        okhttp3.OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    }

    fun init(context: Context) {
        if (dir == null) dir = File(context.filesDir, "clips").apply { mkdirs() }
    }

    private fun name(key: String): String =
        java.security.MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) } + ".mp4"

    /** The saved clip of game [key] as an address the players open ("file://…"), or null. */
    fun local(key: String): String? {
        val f = File(dir ?: return null, name(key))
        return if (f.isFile && f.length() > 0) "file://" + f.absolutePath else null
    }

    /** Space the saved clips take, in bytes. */
    fun usedBytes(): Long = dir?.listFiles()?.sumOf { it.length() } ?: 0L

    fun count(): Int = dir?.listFiles()?.count { it.name.endsWith(".mp4") } ?: 0

    fun clear() { dir?.listFiles()?.forEach { it.delete() } }

    /**
     * Saves game [key]'s clip from [url] (blocking: call off the main thread). Returns its local address, or null when it
     * couldn't be fetched or the size limit is reached.
     */
    fun save(key: String, url: String): String? {
        local(key)?.let { return it }
        val d = dir ?: return null
        val limit = Features.clipsMaxMb.value.toLong() * 1024L * 1024L
        if (usedBytes() >= limit) return null
        val out = File(d, name(key))
        val tmp = File(d, out.name + ".part")
        return runCatching {
            client.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) return@runCatching null
                val body = r.body ?: return@runCatching null
                tmp.outputStream().use { o -> body.byteStream().copyTo(o) }
            }
            // (Not a video — ScreenScraper answers some errors with a short text page.)
            if (tmp.length() < 20_000) { tmp.delete(); return@runCatching null }
            if (!tmp.renameTo(out)) { tmp.delete(); return@runCatching null }
            "file://" + out.absolutePath
        }.getOrElse { tmp.delete(); null }
    }
}
