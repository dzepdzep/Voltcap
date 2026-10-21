package com.shelf.launcher.data

import android.content.Context

/**
 * Update 301: where a full case scan (back | spine | front side by side, "Box (full scan)") is cut, when you've set it
 * yourself (Artwork & details › Full case scan › Adjust the cuts…): the spine's left and right edge as shares of the
 * scan's width (0…1). Without one the cut is found automatically (BoxTexture.findSpine). Kept on this device only.
 */
object FullScanCuts {
    private var prefs: android.content.SharedPreferences? = null
    private val map = java.util.concurrent.ConcurrentHashMap<String, Pair<Float, Float>>()

    /** Bumped on every change: the cut pictures are made again (the cut is part of their address, BoxTexture.model). */
    val version = androidx.compose.runtime.mutableIntStateOf(0)

    fun init(c: Context) {
        if (prefs != null) return
        val p = c.applicationContext.getSharedPreferences("full_scan_cuts", Context.MODE_PRIVATE)
        prefs = p
        for ((k, v) in p.all) parse(v as? String)?.let { map[k] = it }
    }

    /** "l,r" → (l, r) when 0 ≤ l < r ≤ 1. */
    fun parse(s: String?): Pair<Float, Float>? {
        val bits = s?.split(',')?.takeIf { it.size == 2 } ?: return null
        val l = bits[0].trim().toFloatOrNull() ?: return null
        val r = bits[1].trim().toFloatOrNull() ?: return null
        return if (l >= 0f && r <= 1f && r - l > 0.002f) l to r else null
    }

    fun encode(l: Float, r: Float): String = String.format(java.util.Locale.US, "%.4f,%.4f", l, r)

    /** Your cut for the scan at [url], or null (automatic). */
    fun of(url: String): Pair<Float, Float>? {
        version.intValue // (read: whoever builds a cut's address looks again after a change)
        return map[url]
    }

    /** Sets (or with null, removes — back to automatic) your cut for the scan at [url]. */
    fun set(url: String, cut: Pair<Float, Float>?) {
        if (cut == null) { map.remove(url); prefs?.edit()?.remove(url)?.apply() }
        else {
            val l = cut.first.coerceIn(0f, 0.99f); val r = cut.second.coerceIn(l + 0.003f, 1f)
            map[url] = l to r
            prefs?.edit()?.putString(url, encode(l, r))?.apply()
        }
        version.intValue++
    }

    /** Update 302: forgets cuts kept for a picture of yours that's gone (UserArt.cleanup). */
    fun dropMissingFiles() {
        val gone = map.keys.filter { it.startsWith("file://") && runCatching { !java.io.File(android.net.Uri.parse(it).path!!).exists() }.getOrDefault(false) }
        if (gone.isEmpty()) return
        gone.forEach { map.remove(it) }
        prefs?.edit()?.apply { gone.forEach { remove(it) } }?.apply()
    }
}
