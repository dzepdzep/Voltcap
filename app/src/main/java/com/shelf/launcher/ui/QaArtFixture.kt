package com.shelf.launcher.ui

import androidx.compose.runtime.mutableStateOf
import coil.memory.MemoryCache

/**
 * Update 503 (QA app only — com.shelf.launcher.qa, never the owner's Voltcap): artwork and achievements for the fixture
 * library, read late on purpose, to see what the home screen does while pictures arrive.
 *   `--es voltcap_test_artfix "delay=800"` (with `--ez voltcap_test_fixture_roms true` before it): every fixture game gets a
 *   generated cover (every third one none — the missing-art case), every second one a logo, three of four a synthetic
 *   achievement set whose badges are local files; every picture under qa_art is held back [delayMs] when it isn't in memory.
 *   `delay=0` keeps the pictures but stops holding them back.
 * The pictures are the QA app's own files (<its external files>/qa_art, put there by tools/qa/device/art503.sh); badges are
 * asked by their ordinary RetroAchievements address (qa_<n>) and answered from those files here, so nothing reaches the
 * network and the production badge code runs unchanged. Saved only in the QA app's own data.
 */
internal object QaArtFixture {
    const val BADGE = "https://media.retroachievements.org/Badge/qa_"
    @Volatile var delayMs = 0L
    val spec = mutableStateOf<Long?>(null)

    /**
     * Hardware QA, 12 Nov (`--es voltcap_test_scanfix on|off`, QA app only): the fixture games get scanned pictures for this
     * session only (never saved) - a box-front scan and a spine scan (qa_art/spine_<n>.png) for one game in three, the same
     * front with a custom spine (qa_art/spine_custom.png) for the next, none for the third (missing art). The Game shelf's 3D
     * case takes only games with their own spine and front (Shelf3D.sameArtSources), so without these it can't be seen on a
     * handheld whose QA library has no scans. Null: not asked.
     */
    val scans = mutableStateOf<Boolean?>(null)

    fun parse(v: String): Long? = if (v == "off") null
        else v.split(',').firstNotNullOfOrNull { p -> p.trim().removePrefix("delay=").toLongOrNull() }?.coerceIn(0, 5000) ?: 0L

    class Delay : coil.intercept.Interceptor {
        override suspend fun intercept(chain: coil.intercept.Interceptor.Chain): coil.request.ImageResult {
            val r = chain.request
            val s = r.data as? String ?: return chain.proceed(r)
            val badge = s.startsWith(BADGE)
            if (!badge && !s.contains("/qa_art/")) return chain.proceed(r)
            val ctx = r.context
            val key = r.memoryCacheKey ?: MemoryCache.Key(if (badge) s else "qa|$s|${chain.size}")
            val req = if (badge) {
                val n = s.removePrefix(BADGE).removeSuffix(".png").removeSuffix("_lock")
                r.newBuilder().data(java.io.File(ctx.getExternalFilesDir("qa_art"), "badge_$n.png")).memoryCacheKey(key).build()
            } else if (r.memoryCacheKey == null) r.newBuilder().memoryCacheKey(key).build() else r
            val hit = runCatching { coil.Coil.imageLoader(ctx).memoryCache?.get(key) != null }.getOrDefault(false)
            // (Badges come in one after another, as single reads from storage do: each one 90 ms later than the one before.)
            val stagger = if (badge) (s.removePrefix(BADGE).takeWhile { it.isDigit() }.toIntOrNull() ?: 0) % 6 * 90L else 0L
            if (!hit && delayMs > 0) kotlinx.coroutines.delay(delayMs + stagger)
            return chain.proceed(req)
        }
    }
}
