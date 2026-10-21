package com.shelf.launcher.data

import android.content.Context
import kotlinx.coroutines.launch

/**
 * Update 302: a case's back cover from TheGamesDB (its "boxart" image, side "back"), with your own TheGamesDB key —
 * only for games with no back scan of their own (ScreenScraper's back, the back cut from a full scan, or your pick),
 * and only once the case's back is actually looked at (turned round or inspected), so a big library doesn't use up
 * the key's allowance. Kept per game on this device (a game without one is asked again after two weeks).
 * TheGamesDB doesn't say which region a back is from.
 */
object TgdbBacks {
    const val LABEL = "Back of the box"
    private const val RECHECK_MS = 14L * 24 * 60 * 60 * 1000

    private var prefs: android.content.SharedPreferences? = null
    /** key → url, or "-|<when looked>" for none. */
    private val known = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val asking: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    /** After a failed look-up, TheGamesDB is left alone until then. */
    @Volatile private var pausedUntil = 0L
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    /** Set by the view model: the look-up (null without a TheGamesDB key) and what to do when a back was found. */
    @Volatile var fetcher: (suspend (Game) -> String?)? = null
    @Volatile var onFound: ((Game) -> Unit)? = null

    fun init(c: Context) {
        if (prefs != null) return
        // (Update 302 test build's list, which kept failed look-ups as "no back": dropped.)
        runCatching { c.applicationContext.deleteSharedPreferences("tgdb_backs") }
        val p = c.applicationContext.getSharedPreferences("tgdb_backs2", Context.MODE_PRIVATE)
        prefs = p
        for ((k, v) in p.all) (v as? String)?.let { known[k] = it }
    }

    /** The back found for [key], or null. */
    fun of(key: String): String? = known[key]?.takeUnless { it.startsWith("-|") }

    /** [list] with TheGamesDB's back added when it has none of its own (and one was found). */
    fun fill(key: String, list: List<Pair<String, String>>): List<Pair<String, String>> {
        // (Not over a back of its own, nor over a full case scan — its back is cut from that.)
        if (list.any { it.first == LABEL || it.first == com.shelf.launcher.ui.BoxTexture.LABEL }) return list
        val back = of(key) ?: return list
        return list + (LABEL to back)
    }

    /** Asks TheGamesDB for [game]'s back once (in the background) — when its case's back is wanted and it has none. */
    fun request(game: Game) {
        val f = fetcher ?: return
        val key = game.stableKey
        known[key]?.let { v ->
            if (!v.startsWith("-|")) return
            val at = v.removePrefix("-|").toLongOrNull() ?: 0L
            if (System.currentTimeMillis() - at < RECHECK_MS) return
        }
        if (System.currentTimeMillis() < pausedUntil) return
        if (!asking.add(key)) return
        scope.launch {
            try {
                val result = runCatching { f(game) }
                // (A failed look-up — no network, the key's allowance used up (HTTP 429) — isn't kept as "no back";
                // TheGamesDB isn't asked again for an hour.)
                if (result.isFailure) { pausedUntil = System.currentTimeMillis() + 60 * 60 * 1000L; return@launch }
                val url = result.getOrNull()
                val v = url ?: "-|${System.currentTimeMillis()}"
                known[key] = v
                prefs?.edit()?.putString(key, v)?.apply()
                if (url != null) onFound?.invoke(game)
            } finally { asking.remove(key) }
        }
    }

}
