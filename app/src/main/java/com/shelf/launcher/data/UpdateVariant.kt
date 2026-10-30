package com.shelf.launcher.data

/**
 * Update 410: the two release variants of Voltcap (same package id, same signing key).
 *  - FULL   `Voltcap-X.Y.Z.apk`         the main app, with ShelfNotificationListener (notifications, other players' now-playing)
 *  - COMPAT `Voltcap-X.Y.Z-compat.apk`  optional, without the listener (for devices where Play Protect blocks the full one)
 * Releases up to 0.6.60 used the opposite naming (plain = no listener, `-notify` = with it); [pick] / [fallbackName] handle both
 * so an update never silently swaps a person's variant. Pure functions, unit-tested (UpdateVariantTest).
 */
enum class AppVariant(val key: String) {
    FULL("full"), COMPAT("compat");
    companion object { fun parse(s: String?): AppVariant? = entries.firstOrNull { it.key == s } }
}

object UpdateVariant {
    /** First release that uses the new naming (plain = full). */
    const val NEW_NAMING_FROM = "0.6.61"

    /** The variant that is installed now: the listener service is in the manifest (FULL) or it is not (COMPAT). */
    fun detect(listenerPresent: Boolean): AppVariant = if (listenerPresent) AppVariant.FULL else AppVariant.COMPAT

    /** What an update should fetch: only an explicit switch ([pending]) changes the variant, otherwise the installed one stays. */
    fun wanted(installed: AppVariant, pending: AppVariant?): AppVariant = pending ?: installed

    private fun parts(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V").split('.', '-', '+').takeWhile { it.toIntOrNull() != null }.map { it.toInt() }

    fun atLeast(tag: String, min: String): Boolean {
        val a = parts(tag); val b = parts(min)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return true
    }

    private fun isCompat(n: String) = n.contains("-compat", ignoreCase = true)
    private fun isLegacyFull(n: String) = n.contains("-notify", ignoreCase = true)

    /** Whether the release [tag] with these [names] uses the new naming. */
    fun newNaming(tag: String, names: List<String>): Boolean = names.any { isCompat(it) } || atLeast(tag, NEW_NAMING_FROM)

    /** The attached APK of [want] among [names], or null (never the other variant: no silent swap). */
    fun pick(tag: String, names: List<String>, want: AppVariant): String? {
        val apks = names.filter { it.endsWith(".apk", ignoreCase = true) && !it.contains("debug", ignoreCase = true) }
        val plain = apks.firstOrNull { !isCompat(it) && !isLegacyFull(it) }
        return if (newNaming(tag, names)) {
            if (want == AppVariant.FULL) plain else apks.firstOrNull { isCompat(it) }
        } else {
            if (want == AppVariant.COMPAT) plain else apks.firstOrNull { isLegacyFull(it) }
        }
    }

    /** The APK's file name when only the tag is known (release-page fallback, no asset list). */
    fun fallbackName(tag: String, want: AppVariant): String {
        val ver = tag.removePrefix("v")
        return if (atLeast(tag, NEW_NAMING_FROM)) { if (want == AppVariant.FULL) "Voltcap-$ver.apk" else "Voltcap-$ver-compat.apk" }
        else { if (want == AppVariant.COMPAT) "Voltcap-$ver.apk" else "Voltcap-$ver-notify.apk" }
    }
}
