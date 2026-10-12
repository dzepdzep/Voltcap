package com.shelf.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** One trophy for the Trophy vault: a picture (game icon or achievement badge) and whether it's a mastered game. */
internal data class TrophyItem(val url: String, val mastered: Boolean)

/**
 * What the Trophy vault puts on its pedestals: the games you mastered most recently (gold frames, the newest
 * on the carpet) and then your latest unlocked achievements (silver frames), from RetroAchievements and Steam
 * together, newest first. Set by the home screen; empty = the vault's own drawn badges.
 */
internal object TrophyShelf {
    var items by mutableStateOf<List<TrophyItem>>(emptyList())

    /** Seven cells at most (the pedestals). */
    const val SLOTS = 7
    private const val CELL = 64

    /** The trophies side by side in one small picture (7 × 64 px cells), ready for the shader. */
    class Atlas(val items: List<TrophyItem>, val shader: android.graphics.BitmapShader, val w: Int, val h: Int, val count: Int, val mastered: Int)

    @Volatile private var last: Atlas? = null

    /**
     * Picks them: mastered games — RetroAchievements mastered / completed and Steam games at 100 % ([steamCover]
     * gives a Steam game's picture) — newest first, at most three so achievements get room too; then the
     * latest unlocked achievements from both, newest first, no repeats.
     */
    fun pick(
        completion: Collection<com.shelf.launcher.data.RaCompletionEntry>,
        recent: List<com.shelf.launcher.data.RaRecentAchievement>,
        steamMastered: List<com.shelf.launcher.data.SteamTrophies.Mastered> = emptyList(),
        steamRecent: List<com.shelf.launcher.data.SteamTrophies.Unlock> = emptyList(),
        /** How many at most (the vault's pedestals; Badge bounce takes more). */
        limit: Int = SLOTS,
        /** Update 119: more of your unlocked achievements (badge, when earned) — your games' own lists, past the last 30 days. */
        more: List<Pair<String, Long>> = emptyList(),
        /** Update 119: only mastered games (Theme › the trophy theme › Only mastered games). */
        masteredOnly: Boolean = false,
        steamCover: (Int) -> String? = { null }
    ): List<TrophyItem> {
        val games = (
            completion
                .filter { it.HighestAwardKind == "mastered" || it.HighestAwardKind == "completed" }
                .mapNotNull { e -> com.shelf.launcher.data.RaBadges.mediaUrl(e.ImageIcon)?.let { it to raTime(e.HighestAwardDate) } } +
                steamMastered.mapNotNull { m -> steamCover(m.appId)?.let { it to m.at } }
            )
            .sortedByDescending { it.second }
            .map { TrophyItem(it.first, true) }
            .distinctBy { it.url }
            .take(if (masteredOnly) limit else 3)
        if (masteredOnly) return games
        val badges = (
            recent.mapNotNull { a -> com.shelf.launcher.data.RaBadges.url(a.BadgeName)?.let { it to raTime(a.Date) } } +
                steamRecent.map { it.icon to it.at } + more
            )
            .sortedByDescending { it.second }
            .map { TrophyItem(it.first, false) }
            .distinctBy { it.url }
        return (games + badges).take(limit)
    }

    /** RetroAchievements' dates ("2026-10-06 18:30:00", UTC, sometimes with a T and a zone) in ms; 0 if unreadable. */
    fun raTime(s: String?): Long {
        val t = s?.take(19)?.replace('T', ' ') ?: return 0L
        return runCatching {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.parse(t)?.time
        }.getOrNull() ?: 0L
    }

    /** Loads the pictures (the mastered games first) and lays them side by side. Null when none loaded. */
    suspend fun atlas(context: android.content.Context, items: List<TrophyItem>): Atlas? {
        if (items.isEmpty()) return null
        last?.takeIf { it.items == items }?.let { return it }
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val loader = coil.Coil.imageLoader(context)
                val loaded = items.mapNotNull { item ->
                    val request = coil.request.ImageRequest.Builder(context)
                        .data(item.url)
                        .size(if (item.mastered) CELL * 2 else CELL, if (item.mastered) CELL * 3 else CELL)
                        .scale(coil.size.Scale.FILL)
                        .allowHardware(false)
                        .memoryCacheKey("trophy|${item.url}")
                        .build()
                    val bmp = ((loader.execute(request) as? coil.request.SuccessResult)?.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    if (bmp == null || bmp.isRecycled) null else item to bmp
                }
                if (loaded.isEmpty()) return@runCatching null
                // Mastered ones stay first (the shader frames the first [mastered] cells in gold).
                val out = android.graphics.Bitmap.createBitmap(CELL * SLOTS, CELL, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(out)
                canvas.drawColor(android.graphics.Color.rgb(20, 22, 40))
                val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
                loaded.forEachIndexed { i, (_, bmp) ->
                    // Square cells: a cover (taller than wide) shows its middle.
                    val side = minOf(bmp.width, bmp.height)
                    val src = android.graphics.Rect((bmp.width - side) / 2, (bmp.height - side) / 2, (bmp.width + side) / 2, (bmp.height + side) / 2)
                    canvas.drawBitmap(bmp, src, android.graphics.Rect(i * CELL, 0, (i + 1) * CELL, CELL), paint)
                }
                Atlas(
                    items,
                    android.graphics.BitmapShader(out, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP),
                    out.width, out.height, loaded.size, loaded.count { it.first.mastered }
                ).also { last = it }
            }.getOrNull()
        }
    }
}

/** The Trophy vault's trophies as they load (null until then, or when there are none). */
@Composable
internal fun rememberTrophyAtlas(): State<TrophyShelf.Atlas?> {
    val context = LocalContext.current
    val items = TrophyShelf.items
    return produceState<TrophyShelf.Atlas?>(null, items) { value = TrophyShelf.atlas(context, items) }
}
