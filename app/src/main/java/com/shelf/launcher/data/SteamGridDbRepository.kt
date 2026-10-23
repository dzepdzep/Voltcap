package com.shelf.launcher.data

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentHashMap

/** Holds the user-provided API key so the auth interceptor can read the latest
 * value without rebuilding the whole Retrofit/OkHttp stack every time it changes. */
object SgdbAuth {
    @Volatile var apiKey: String? = null
}

private class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val key = SgdbAuth.apiKey
        val request = if (key.isNullOrBlank()) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $key")
                .build()
        }
        return chain.proceed(request)
    }
}

fun createSteamGridDbApi(): SteamGridDbApi {
    val client = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor())
        // Update 182: firm limits (the defaults have no overall limit per request).
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    val retrofit = Retrofit.Builder()
        .baseUrl("https://www.steamgriddb.com/api/v2/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    return retrofit.create(SteamGridDbApi::class.java)
}

/**
 * Fetches grid (portrait cover) and hero (wide background) art for a game,
 * either by a manual SteamGridDB game ID (reliable — the same approach Vapor
 * Launcher uses) or by fuzzy name search (convenient default, can mismatch
 * on ambiguous titles). Unlike a browser page calling SteamGridDB directly,
 * a native app like this one isn't subject to CORS, so these calls work
 * as-is with a valid key.
 */
class SteamGridDbRepository(private val api: SteamGridDbApi) {

    private val cacheByName = ConcurrentHashMap<String, GameArt>()
    private val cacheById = ConcurrentHashMap<Int, GameArt>()
    private val cacheBySteamAppId = ConcurrentHashMap<Int, GameArt>()

    /** Verifies the key works without depending on the library having any
     * games in it. This exists because connection success used to be inferred
     * from "did we get art for the first game" — which silently reported a
     * perfectly good key as broken (and refused to save it) whenever the
     * library was empty, e.g. before the first ROM scan. */
    suspend fun testConnection(): Boolean = checkKey() == KeyCheck.OK

    /**
     * Update 144: like [testConnection], but tells a refused key (HTTP 401 / 403) apart from no connection, so a
     * launcher starting before Wi-Fi is up doesn't call a good key broken.
     */
    suspend fun checkKey(): KeyCheck = try {
        api.search("mario")
        KeyCheck.OK
    } catch (e: retrofit2.HttpException) {
        if (e.code() == 401 || e.code() == 403) KeyCheck.REJECTED else KeyCheck.UNREACHABLE
    } catch (e: kotlinx.coroutines.CancellationException) {
        // Update 144: a stopped check (connect() cancels the retry) stops; read as "no connection" it would plan another.
        throw e
    } catch (e: Exception) {
        KeyCheck.UNREACHABLE
    }

    enum class KeyCheck { OK, REJECTED, UNREACHABLE }

    /** All candidate assets for one game, for the artwork picker — unlike
     * fetchArt() which just takes the first of each, this returns the full
     * lists so the person can choose. */
    suspend fun fetchArtCandidates(gameId: Int): ArtCandidates = coroutineScope {
        // The picker lists animated covers and heroes too (they play when Animated artwork is on). The
        // animated ones are also asked for on their own: the mixed list can leave some out, and this way
        // the picker knows exactly which are animated (its "Animated only" filter and ▶ tag).
        val grids = async { runCatching { api.grids(gameId, PORTRAIT_DIMENSIONS, BOTH) }.getOrNull() }
        val wides = async { runCatching { api.grids(gameId, WIDE_DIMENSIONS, BOTH) }.getOrNull() }
        val heroes = async { runCatching { api.heroes(gameId, BOTH) }.getOrNull() }
        val logos = async { runCatching { api.logos(gameId) }.getOrNull() }
        val animGrids = async { runCatching { api.grids(gameId, PORTRAIT_DIMENSIONS, ANIMATED) }.getOrNull() }
        val animWides = async { runCatching { api.grids(gameId, WIDE_DIMENSIONS, ANIMATED) }.getOrNull() }
        val animHeroes = async { runCatching { api.heroes(gameId, ANIMATED) }.getOrNull() }
        val ag = animGrids.await()?.data?.map { it.url }.orEmpty()
        val aw = animWides.await()?.data?.map { it.url }.orEmpty()
        val ah = animHeroes.await()?.data?.map { it.url }.orEmpty()
        ArtCandidates(
            grids = (grids.await()?.data?.map { it.url }.orEmpty() + ag).distinct(),
            wideGrids = (wides.await()?.data?.map { it.url }.orEmpty() + aw).distinct(),
            heroes = (heroes.await()?.data?.map { it.url }.orEmpty() + ah).distinct(),
            logos = logos.await()?.data?.map { it.url }.orEmpty(),
            animated = (ag + aw + ah).toSet()
        )
    }

    /**
     * Update 113: the game's first SteamGridDB logo (the big logos default to SteamGridDB's): its address, "" when
     * SteamGridDB has no logo (or no such game), null when it couldn't be asked (no key, offline).
     */
    suspend fun fetchLogo(game: Game): String? {
        if (SgdbAuth.apiKey.isNullOrBlank()) return null
        return try {
            val id = resolveGameId(game) ?: return ""
            api.logos(id).data.firstOrNull()?.url ?: ""
        } catch (e: Exception) {
            null
        }
    }

    /** Candidates for a title the person typed, for when the auto-match
     * picked the wrong game and they want to search for the right one. */
    suspend fun fetchArtCandidatesByName(name: String): ArtCandidates? {
        if (SgdbAuth.apiKey.isNullOrBlank()) return null
        return try {
            val id = api.search(name).data.firstOrNull()?.id ?: return null
            fetchArtCandidates(id)
        } catch (e: Exception) {
            null
        }
    }

    /** Resolves a game to its SteamGridDB id using the same priority as
     * fetchArt (manual override > Steam AppID > name search). */
    suspend fun resolveGameId(game: Game): Int? = try {
        game.sgdbGameId
            ?: game.steamAppId?.let { api.gameBySteamId(it).data?.id }
            ?: searchTerm(game)?.let { (term, strict) -> bestMatch(term, strict)?.id }
    } catch (e: Exception) {
        null
    }

    /**
     * (Update 238, #111) What a name search for [game] asks SteamGridDB: its name (as before) — or, for a ROM hack
     * (RomHacks), the hack's own title, exact titles only, so "Pokemon Radical Red" finds Radical Red's entry and never
     * FireRed's; null for a hack known only by a "(Hack)" tag, whose title is the base game's (it keeps the placeholder
     * or another source's own art rather than the base game's). A SteamGridDB ID set by hand is used before this.
     */
    private fun searchTerm(game: Game): Pair<String, Boolean>? {
        val hack = RomHacks.fanHack(game) ?: return game.name to TitleMatch.strictFor(game)
        return if (hack.distinctive) hack.ownTitle to true else null
    }

    /**
     * The search result that really is [name] (TitleMatch): not simply the first one, which matched
     * apps with games of a similar name (Moonlight → Moonlighter). [strict]: exact titles only (apps).
     */
    private suspend fun bestMatch(name: String, strict: Boolean): SgdbSearchResult? {
        val results = api.search(name).data
        if (results.isEmpty()) return null
        val i = TitleMatch.best(name, results.map { it.name }, strict) ?: return null
        return results[i]
    }

    /** Preferred entry point. Priority: manual SteamGridDB ID override (most
     * exact) > Steam AppID lookup for STEAM-source games (exact, no fuzzy
     * matching, and the AppID is already known so it costs nothing extra to
     * try) > fuzzy name search (works for anything, can mismatch). */
    suspend fun fetchArt(game: Game): GameArt? {
        val manualId = game.sgdbGameId
        val steamAppId = game.steamAppId
        return when {
            manualId != null -> fetchArtById(manualId)
            game.sourceType == SourceType.STEAM && steamAppId != null -> fetchArtBySteamAppId(steamAppId)
            // (Update 238, #111: a ROM hack by its own title, or not by name at all — searchTerm.)
            else -> searchTerm(game)?.let { (term, strict) -> fetchArtByName(term, strict) }
        }
    }

    suspend fun fetchArtById(gameId: Int): GameArt? {
        cacheById[gameId]?.let { return it }
        if (SgdbAuth.apiKey.isNullOrBlank()) return null

        return try {
            val art = fetchAllAssets(gameId)
            if (art.hasAny()) cacheById[gameId] = art
            art
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchArtBySteamAppId(steamAppId: Int): GameArt? {
        cacheBySteamAppId[steamAppId]?.let { return it }
        if (SgdbAuth.apiKey.isNullOrBlank()) return null

        return try {
            val sgdbId = api.gameBySteamId(steamAppId).data?.id ?: run {
                val empty = GameArt(null, null, null, null)
                cacheBySteamAppId[steamAppId] = empty
                return empty
            }
            val art = fetchAllAssets(sgdbId)
            if (art.hasAny()) cacheBySteamAppId[steamAppId] = art
            art
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchArtByName(gameName: String, strict: Boolean = false): GameArt? {
        val cacheKey = (if (strict) "=" else "~") + gameName
        cacheByName[cacheKey]?.let { return it }
        if (SgdbAuth.apiKey.isNullOrBlank()) return null

        return try {
            val searchResult = bestMatch(gameName, strict) ?: run {
                val empty = GameArt(null, null, null, null)
                cacheByName[cacheKey] = empty
                return empty
            }
            val art = fetchAllAssets(searchResult.id)
            if (art.hasAny()) cacheByName[cacheKey] = art
            art
        } catch (e: Exception) {
            null
        }
    }

    /** Pulls all four SteamGridDB asset types in parallel, each filtered to
     * the dimensions that type actually uses:
     *   - grid 600x900  -> portrait cover for the dock's unfocused cards
     *   - grid 920x430  -> wide "capsule", used for the focused banner so it
     *                      isn't just the portrait cover stretched sideways
     *   - hero          -> full-bleed background behind everything
     *   - logo          -> transparent title logo, shown instead of plain text
     * Any individual call failing just leaves that one URL null rather than
     * losing the whole set. */
    private suspend fun fetchAllAssets(gameId: Int): GameArt = coroutineScope {
        // "Prefer animated artwork": SteamGridDB's animated covers / heroes first (animated WebP, GIF,
        // APNG), the still ones when a game has none.
        val animated = AnimatedArt.preferAnimatedScraping
        suspend fun gridsOf(dim: String) = if (animated) {
            runCatching { api.grids(gameId, dim, ANIMATED) }.getOrNull()?.takeIf { it.data.isNotEmpty() }
                ?: runCatching { api.grids(gameId, dim) }.getOrNull()
        } else runCatching { api.grids(gameId, dim) }.getOrNull()
        val gridDeferred = async { gridsOf(PORTRAIT_DIMENSIONS) }
        val wideGridDeferred = async { gridsOf(WIDE_DIMENSIONS) }
        // Animated heroes asked for once: with "Prefer animated" the first one is the hero, and the smart
        // artwork priority (Animated hero → Hero → Screenshot → Cover) keeps it beside the still one.
        val animHeroDeferred = async {
            if (animated || Features.artPriority.value == Features.ArtPriority.SMART)
                runCatching { api.heroes(gameId, ANIMATED) }.getOrNull()?.data?.firstOrNull()?.url
            else null
        }
        val heroDeferred = async {
            if (animated) animHeroDeferred.await() ?: runCatching { api.heroes(gameId) }.getOrNull()?.data?.firstOrNull()?.url
            else runCatching { api.heroes(gameId) }.getOrNull()?.data?.firstOrNull()?.url
        }
        val logoDeferred = async { runCatching { api.logos(gameId) }.getOrNull() }

        val grid = gridDeferred.await()?.data?.firstOrNull()?.url
        // (Update 317, Toni: an animated cover has its first still one kept beside it, for the cartridges and discs.)
        val stillGrid = if (grid != null && AnimatedArt.looksAnimated(grid))
            runCatching { api.grids(gameId, PORTRAIT_DIMENSIONS, STATIC) }.getOrNull()?.data?.firstOrNull()?.url else null
        GameArt(
            gridUrl = grid,
            wideGridUrl = wideGridDeferred.await()?.data?.firstOrNull()?.url,
            heroUrl = heroDeferred.await(),
            logoUrl = logoDeferred.await()?.data?.firstOrNull()?.url,
            animatedHeroUrl = animHeroDeferred.await(),
            stillGridUrl = stillGrid
        ).also { StillCovers.note(it) }
    }

    /** Every asset call failing (offline, a server hiccup) is not "this game has no art": not cached. */
    private fun GameArt.hasAny() = gridUrl != null || wideGridUrl != null || heroUrl != null || logoUrl != null || animatedHeroUrl != null

    companion object {
        private const val PORTRAIT_DIMENSIONS = "600x900"
        private const val WIDE_DIMENSIONS = "920x430"
        private const val BOTH = "static,animated"
        private const val ANIMATED = "animated"
        private const val STATIC = "static"
    }
}
