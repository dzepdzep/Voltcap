package com.shelf.launcher.data

import android.content.Context

/**
 * Update 305: where a picture you added came from — so a cover, wrap, label or side you imported keeps its provenance: the
 * site or source ("The Cover Project", "My own scan"…), whether it's a scan of real retail packaging or a custom / fan-made
 * design, who made it (credit, as you enter it), and what it was made for (a case format, e.g. "Universal Game Case").
 * Kept on this device (prefs "art_provenance", in backups like your other picks), by the picture's address. Nothing is
 * uploaded anywhere.
 */
object ArtProvenance {
    enum class Kind(val label: String) { SCAN("Scan of real packaging"), CUSTOM("Custom / fan-made design"), UNKNOWN("Not sure") }

    data class Info(val source: String = "", val kind: Kind = Kind.UNKNOWN, val credit: String = "", val madeFor: String = "") {
        /** "The Cover Project · custom design · by Name · for Universal Game Case" — what the picker shows under it. */
        val summary: String
            get() = listOfNotNull(
                source.takeIf { it.isNotBlank() },
                when (kind) { Kind.SCAN -> "retail scan"; Kind.CUSTOM -> "custom design"; Kind.UNKNOWN -> null },
                credit.takeIf { it.isNotBlank() }?.let { "by $it" },
                madeFor.takeIf { it.isNotBlank() }?.let { "made for $it" }
            ).joinToString(" · ")
    }

    /** Sources offered when you add a picture (anything else can be typed). */
    val SOURCES = listOf("The Cover Project", "My own scan", "Other website")

    private var prefs: android.content.SharedPreferences? = null
    private val map = java.util.concurrent.ConcurrentHashMap<String, Info>()
    val version = androidx.compose.runtime.mutableIntStateOf(0)

    fun init(c: Context) {
        if (prefs != null) return
        val p = c.applicationContext.getSharedPreferences("art_provenance", Context.MODE_PRIVATE)
        prefs = p
        for ((k, v) in p.all) decode(v as? String)?.let { map[k] = it }
    }

    fun of(url: String?): Info? { version.intValue; return url?.let { map[it] } }

    /** Whether [url] is marked as a scan of real packaging (Box sides › Authentic only shows only those). */
    fun isScan(url: String?): Boolean = of(url)?.kind == Kind.SCAN

    fun set(url: String, info: Info?) {
        if (info == null) { map.remove(url); prefs?.edit()?.remove(url)?.apply() }
        else { map[url] = info; prefs?.edit()?.putString(url, encode(info))?.apply() }
        version.intValue++
    }

    internal fun encode(i: Info): String = listOf(i.source, i.kind.name, i.credit, i.madeFor).joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') }

    internal fun decode(s: String?): Info? {
        val f = s?.split('\t') ?: return null
        if (f.size < 2) return null
        return Info(f[0], Kind.values().firstOrNull { it.name == f[1] } ?: Kind.UNKNOWN, f.getOrElse(2) { "" }, f.getOrElse(3) { "" })
    }
}
