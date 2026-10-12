package com.shelf.launcher.data

import com.google.gson.annotations.SerializedName
import com.shelf.launcher.data.ra.RaError
import com.shelf.launcher.data.ra.RaErrorKind
import com.shelf.launcher.data.ra.RaResult
import com.shelf.launcher.data.ra.raCall
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface RetroAchievementsApi {

    // Used both as a cheap connection test and to populate the avatar +
    // points shown in the status bar.
    @GET("API_GetUserProfile.php")
    suspend fun getUserProfile(@Query("u") username: String, @Query("y") apiKey: String): RaUserProfile

    // Verified against RetroAchievements' own API docs: GET
    // API_GetUserProgress.php?u=<username>&y=<webApiKey>&i=<comma-separated game IDs>
    @GET("API_GetUserProgress.php")
    suspend fun getUserProgress(
        @Query("u") username: String,
        @Query("y") apiKey: String,
        @Query("i") gameIdsCsv: String
    ): Map<String, RaGameProgress>

    // Verified directly against api-docs.retroachievements.org: a single call
    // returns each achievement's metadata, its "type" (missable/progression/
    // win_condition, lowercase "type" in the actual JSON), AND this user's
    // unlock status (dateEarned/dateEarnedHardcore) together.
    // Params confirmed required: g (game ID), u (username), y (key).
    @GET("API_GetGameInfoAndUserProgress.php")
    suspend fun getGameInfoAndUserProgress(
        @Query("g") gameId: Int,
        @Query("u") username: String,
        @Query("y") apiKey: String
    ): RaGameProgressDetailResponse

    /** Comments people left on the site: t=1 game, t=2 achievement, t=3 user.
     * Newest first with sort=-submitted (api-docs.retroachievements.org). */
    @GET("API_GetComments.php")
    suspend fun getComments(
        @Query("i") id: Int,
        @Query("t") type: Int,
        @Query("y") apiKey: String,
        @Query("c") count: Int = 50,
        @Query("sort") sort: String = "-submitted",
        @Query("o") offset: Int = 0
    ): RaCommentsResponse

    /** A game's extended info — used for its official guide link (GuideURL). */
    @GET("API_GetGameExtended.php")
    suspend fun getGameExtended(@Query("i") gameId: Int, @Query("y") apiKey: String): RaGameExtended

    /** One game's basic info, including its images (icon, title screen, box art). */
    @GET("API_GetGame.php")
    suspend fun getGame(@Query("i") gameId: Int, @Query("y") apiKey: String): RaGameInfo

    /** All consoles RetroAchievements knows about. */
    @GET("API_GetConsoleIDs.php")
    suspend fun consoleIds(@Query("y") apiKey: String): List<RaConsole>

    /** Every game on a console that has achievements (f=1), with the ROM
     * hashes each one accepts (h=1). RetroAchievements asks for this to be
     * cached aggressively — see RaGameListCache. */
    @GET("API_GetGameList.php")
    suspend fun gameList(
        @Query("i") consoleId: Int,
        @Query("y") apiKey: String,
        @Query("f") onlyWithAchievements: Int = 1,
        @Query("h") withHashes: Int = 1
    ): List<RaListedGame>

    /** Every game the user has played, with progress and the highest award
     * earned (mastered / completed / beaten). Paged: `c` per page, `o` offset. */
    @GET("API_GetUserCompletionProgress.php")
    suspend fun getUserCompletionProgress(
        @Query("u") username: String,
        @Query("y") apiKey: String,
        @Query("c") count: Int = 500,
        @Query("o") offset: Int = 0
    ): RaCompletionProgressResponse

    // Defaults to the last 60 minutes server-side if `m` is omitted; passed
    // explicitly so the feed's lookback window is under our control.
    // Every unlock between two moments (seconds since 1970): the recap's achievements.
    @GET("API_GetAchievementsEarnedBetween.php")
    suspend fun getAchievementsEarnedBetween(
        @Query("u") username: String,
        @Query("y") apiKey: String,
        @Query("f") fromSeconds: Long,
        @Query("t") toSeconds: Long
    ): List<RaRecentAchievement>

    @GET("API_GetUserRecentAchievements.php")
    suspend fun getUserRecentAchievements(
        @Query("u") username: String,
        @Query("y") apiKey: String,
        @Query("m") minutesBack: Int
    ): List<RaRecentAchievement>
}

/**
 * Field names follow RetroAchievements' documented API convention for this
 * endpoint. If progress always comes back at zero despite a working
 * connection, check a real response and fix names here.
 */
data class RaGameProgress(
    val NumPossibleAchievements: Int? = null,
    val PossibleScore: Int? = null,
    val NumAchieved: Int? = null,
    val ScoreAchieved: Int? = null,
    val NumAchievedHardcore: Int? = null,
    val ScoreAchievedHardcore: Int? = null
)

data class RaGameProgressDetailResponse(
    val Achievements: Map<String, RaProgressAchievement>? = null,
    /** Everyone who has played this set — the base for each achievement's rarity. */
    val NumDistinctPlayers: Int? = null
)

/** Fields confirmed against a live example response on
 * api-docs.retroachievements.org. Note `type` is genuinely lowercase here —
 * every other field on this object is PascalCase, this one isn't, and
 * that's the actual API, not a typo to "fix". */
data class RaProgressAchievement(
    val ID: Int? = null,
    val Title: String? = null,
    val Description: String? = null,
    val Points: Int? = null,
    val BadgeName: String? = null,
    // Accept "Type" too: the API is inconsistent about this key's case between
    // endpoints, and a missed spelling silently means "nothing is missable".
    @SerializedName(value = "type", alternate = ["Type"])
    val type: String? = null,
    val DateEarned: String? = null,
    val DateEarnedHardcore: String? = null,
    /** How many players unlocked it (softcore or hardcore). */
    val NumAwarded: Int? = null,
    /** The set author's order — the order RetroAchievements' own game page shows. */
    val DisplayOrder: Int? = null
)

/** One entry from GetUserRecentAchievements — field names confirmed against
 * a live example response on api-docs.retroachievements.org. Unlike
 * RaProgressAchievement above, this endpoint's `Type` genuinely is
 * capitalized — the two endpoints are just inconsistent with each other. */
data class RaRecentAchievement(
    val Date: String? = null,
    val HardcoreMode: Int? = null,
    val AchievementID: Int? = null,
    val Title: String? = null,
    val Description: String? = null,
    val BadgeName: String? = null,
    val Points: Int? = null,
    val Type: String? = null,
    val GameTitle: String? = null,
    val GameIcon: String? = null,
    val GameID: Int? = null,
    val ConsoleName: String? = null
)

data class RaConsole(val ID: Int? = null, val Name: String? = null)

/** API_GetGame: images are site-relative ("/Images/012345.png"). */
data class RaGameInfo(
    val Title: String? = null,
    val ImageIcon: String? = null,
    val ImageTitle: String? = null,
    val ImageIngame: String? = null,
    val ImageBoxArt: String? = null
)

data class RaCompletionProgressResponse(
    val Count: Int? = null,
    val Total: Int? = null,
    val Results: List<RaCompletionEntry>? = null
)

/** One played game from GetUserCompletionProgress. HighestAwardKind is one of
 * "mastered", "completed", "beaten-hardcore", "beaten-softcore", or null. */
data class RaCompletionEntry(
    val GameID: Int? = null,
    val Title: String? = null,
    val ImageIcon: String? = null,
    val ConsoleName: String? = null,
    val MaxPossible: Int? = null,
    val NumAwarded: Int? = null,
    val NumAwardedHardcore: Int? = null,
    val HighestAwardKind: String? = null,
    val HighestAwardDate: String? = null,
    /** When the latest achievement in this game was unlocked — "last played". */
    val MostRecentAwardedDate: String? = null
)

data class RaListedGame(
    val ID: Int? = null,
    val Title: String? = null,
    val ConsoleID: Int? = null,
    val NumAchievements: Int? = null,
    /** Lowercase MD5s of every dump RetroAchievements accepts for this game
     * (present because the list is requested with h=1). */
    val Hashes: List<String>? = null
)

/** Profile fields as returned by API_GetUserProfile. UserPic comes back as a
 * site-relative path like "/UserPic/Name.png", so it needs the media host
 * prefixed — see RaBadges.mediaUrl. */
data class RaUserProfile(
    val User: String? = null,
    val UserPic: String? = null,
    val TotalPoints: Int? = null,
    val TotalTruePoints: Int? = null,
    val Rank: Int? = null,
    /** What the game last reported while you played ("Route 3 · 50 Pokémon caught"). */
    val RichPresenceMsg: String? = null,
    /** The RetroAchievements game that [RichPresenceMsg] belongs to. */
    val LastGameID: Int? = null
)

data class RaGameExtended(val Title: String? = null, val GuideURL: String? = null)

/** Tips for one game: comments on its page and the set's official guide. */
data class RaGameTips(
    val comments: List<RaComment> = emptyList(),
    val guideUrl: String? = null,
    /** Set when the comments or the guide link couldn't be loaded; the rest is still valid. */
    val commentsError: RaError? = null,
    val guideError: RaError? = null,
    /** Paging: offset to pass to load older comments, and whether there are any. */
    val nextOffset: Int = 0,
    val hasMore: Boolean = false,
    val fetchedAt: Long = 0L
) {
    /** Everything asked for arrived, so there's no reason to ask again. */
    val isComplete: Boolean get() = commentsError == null && guideError == null
}

/** One page of comments, newest first. [nextOffset] is raw (before hiding "Server" notes). */
data class RaCommentsPage(val comments: List<RaComment>, val nextOffset: Int, val hasMore: Boolean)

/** Progress for many games; [failedIds] are the ones whose request chunk failed (keep old values for them). */
data class RaProgressBatch(val progress: Map<Int, RaGameProgress>, val failedIds: Set<Int>, val firstError: RaError?)

/** The game's own status line ("Route 3 · 50 Pokémon caught") and which RA game it belongs to. */
data class RaRichPresence(val gameId: Int?, val text: String?, val fetchedAt: Long)

/** One comment on an achievement's (or game's) page. */
data class RaComment(
    val User: String? = null,
    val Submitted: String? = null,
    val CommentText: String? = null
)

data class RaCommentsResponse(
    val Count: Int? = null,
    val Total: Int? = null,
    val Results: List<RaComment>? = null
)

/** Badge images live on RetroAchievements' own media server and are loaded
 * over the network on demand. Badge names come back from the API as bare
 * numeric IDs, e.g. "108302" -> .../Badge/108302.png */
object RaBadges {
    private const val MEDIA_BASE = "https://media.retroachievements.org"

    fun url(badgeName: String?): String? =
        badgeName?.takeIf { it.isNotBlank() }?.let { "$MEDIA_BASE/Badge/$it.png" }

    /** Turns a site-relative media path ("/UserPic/Name.png") into a full URL. */
    fun mediaUrl(relativePath: String?): String? =
        relativePath?.takeIf { it.isNotBlank() }?.let {
            if (it.startsWith("http")) it else "$MEDIA_BASE${if (it.startsWith("/")) "" else "/"}$it"
        }
}

/** Merged view of one achievement for the per-game list dialog. */
data class RaAchievementDetail(
    val id: Int,
    val title: String?,
    val description: String?,
    val points: Int?,
    val badgeName: String?,
    val type: String?,
    val dateEarned: String?,
    val dateEarnedHardcore: String?,
    /** Share of this set's players who unlocked it, 0–100 (null when unknown). */
    val unlockPercent: Float? = null,
    /** The set's own display order on retroachievements.org (null in older saved data). */
    val displayOrder: Int? = null
) {
    val isUnlocked: Boolean get() = dateEarned != null || dateEarnedHardcore != null
    /** Strictly the set author's flag from RetroAchievements (`type = missable`).
     * Never derived from unlock state or how far someone has played: an
     * achievement stays missable while you're midway. RA has no "already missed"
     * signal — see ra/MissableState for the user-confirmed version. */
    val isMissable: Boolean get() = type?.trim().equals("missable", ignoreCase = true)
}

/** RetroAchievements' own order (display order, then ID) — how the set's page lists them. */
val RA_DISPLAY_ORDER: Comparator<RaAchievementDetail> =
    compareBy<RaAchievementDetail>({ it.displayOrder ?: Int.MAX_VALUE }, { it.id })

fun createRetroAchievementsApi(): RetroAchievementsApi {
    val retrofit = Retrofit.Builder()
        .baseUrl("https://retroachievements.org/API/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    return retrofit.create(RetroAchievementsApi::class.java)
}

/**
 * RetroAchievements achievements are unlocked by watching live emulator
 * memory during play — only the emulator core itself can do that (RetroArch
 * already does, natively, once you log into RetroAchievements in its own
 * settings). This repository only pulls progress/metadata for display in
 * this launcher; it can't unlock anything itself, and doesn't try to.
 */
class RetroAchievementsRepository(private val api: RetroAchievementsApi) {

    private companion object {
        const val PROGRESS_CHUNK = 50
        const val PAGE = 50
    }

    suspend fun testConnection(username: String, apiKey: String): Boolean =
        fetchProfileResult(username, apiKey) is RaResult.Ok

    /** Profile for the status-bar avatar and points total. */
    suspend fun fetchProfile(username: String, apiKey: String): RaUserProfile? =
        fetchProfileResult(username, apiKey).okValue

    /** Same, but says why it failed. A reply with no user name (RA answers 200 with an
     * empty object for an unknown user) counts as an auth failure. */
    suspend fun fetchProfileResult(username: String, apiKey: String): RaResult<RaUserProfile> =
        when (val r = raCall { api.getUserProfile(username, apiKey) }) {
            is RaResult.Ok -> if (r.value.User.isNullOrBlank())
                RaResult.Error(RaError(RaErrorKind.AUTH, "RetroAchievements didn't recognise that username / web API key."))
            else r
            is RaResult.Error -> r
        }

    /**
     * Lightest call that carries the game's Rich Presence: the profile
     * (RichPresenceMsg + LastGameID). Callers should check [RaRichPresence.gameId]
     * against the game they care about — it is whatever was played last.
     */
    suspend fun fetchRichPresence(username: String, apiKey: String): RaResult<RaRichPresence> =
        when (val r = fetchProfileResult(username, apiKey)) {
            is RaResult.Ok -> RaResult.Ok(
                RaRichPresence(r.value.LastGameID, r.value.RichPresenceMsg?.takeIf { it.isNotBlank() }, r.fetchedAt),
                r.fetchedAt
            )
            is RaResult.Error -> r
        }

    /** Returns progress keyed by RetroAchievements game ID (not stableKey —
     * the caller maps that back to whichever Game entries reference each ID).
     * Requested in chunks so one bad/oversized request can't lose everything;
     * a chunk that fails is reported in [RaProgressBatch.failedIds]. Only when
     * every chunk failed is the result an error. */
    suspend fun fetchProgress(username: String, apiKey: String, gameIds: List<Int>): RaResult<RaProgressBatch> {
        if (gameIds.isEmpty()) return RaResult.Ok(RaProgressBatch(emptyMap(), emptySet(), null))
        val merged = HashMap<Int, RaGameProgress>()
        val failed = HashSet<Int>()
        var firstError: RaError? = null
        gameIds.distinct().chunked(PROGRESS_CHUNK).forEach { chunk ->
            when (val r = raCall { api.getUserProgress(username, apiKey, chunk.joinToString(",")) }) {
                is RaResult.Ok -> r.value.forEach { (key, value) -> key.toIntOrNull()?.let { merged[it] = value } }
                is RaResult.Error -> { failed += chunk; if (firstError == null) firstError = r.error }
            }
        }
        val allFailed = failed.size >= gameIds.distinct().size
        return if (allFailed) RaResult.Error(firstError ?: RaError(RaErrorKind.UNKNOWN, "Progress request failed."))
        else RaResult.Ok(RaProgressBatch(merged, failed, firstError))
    }

    /** Full per-achievement list for one game — a single call, since
     * GetGameInfoAndUserProgress returns both the type
     * (missable/progression/win_condition) and this user's unlock status.
     * A failure is an [RaResult.Error], never an empty list. */
    suspend fun fetchAchievementDetails(username: String, apiKey: String, gameId: Int): RaResult<List<RaAchievementDetail>> =
        raCall {
            val response = api.getGameInfoAndUserProgress(gameId, username, apiKey)
            val players = response.NumDistinctPlayers ?: 0
            val achievements = response.Achievements ?: emptyMap()
            achievements.mapNotNull { (key, ach) ->
                val id = key.toIntOrNull() ?: ach.ID ?: return@mapNotNull null
                RaAchievementDetail(
                    id = id,
                    title = ach.Title,
                    description = ach.Description,
                    points = ach.Points,
                    badgeName = ach.BadgeName,
                    type = ach.type,
                    dateEarned = ach.DateEarned,
                    dateEarnedHardcore = ach.DateEarnedHardcore,
                    unlockPercent = ach.NumAwarded?.takeIf { players > 0 }?.let { (it * 100f / players).coerceIn(0f, 100f) },
                    displayOrder = ach.DisplayOrder
                )
            }.sortedWith(RA_DISPLAY_ORDER)
        }

    /** Every game (with its accepted hashes) for one console, or null if the
     * request failed — callers keep their cached copy in that case. */
    suspend fun fetchGameList(apiKey: String, consoleId: Int): List<RaListedGame>? = try {
        api.gameList(consoleId, apiKey)
    } catch (e: Exception) {
        null
    }

    /** All played games with their awards — follows the pages until done
     * (capped, so a huge account can't loop forever). Null on failure. */
    suspend fun fetchCompletionProgress(username: String, apiKey: String): List<RaCompletionEntry>? {
        val all = mutableListOf<RaCompletionEntry>()
        var offset = 0
        repeat(10) {
            val page = try {
                api.getUserCompletionProgress(username, apiKey, 500, offset)
            } catch (e: Exception) {
                return if (all.isEmpty()) null else all
            }
            val results = page.Results.orEmpty()
            all += results
            offset += results.size
            if (results.isEmpty() || offset >= (page.Total ?: 0)) return all
        }
        return all
    }

    /** Community comments on one achievement, newest first, [PAGE] at a time
     * (pass [offset] = the previous page's nextOffset for older ones). The
     * site's automatic notes ("Server") are left out. */
    suspend fun fetchAchievementComments(apiKey: String, achievementId: Int, offset: Int = 0): RaResult<RaCommentsPage> =
        comments(apiKey, achievementId, type = 2, offset = offset)

    /** Comments on a game's page (first page) plus its official guide link. Fails
     * only if both failed; a partial result carries the error for the missing part. */
    suspend fun fetchGameTips(apiKey: String, gameId: Int): RaResult<RaGameTips> {
        val guide = raCall { api.getGameExtended(gameId, apiKey).GuideURL?.takeIf { it.startsWith("http") } }
        val page = comments(apiKey, gameId, type = 1, offset = 0)
        if (guide is RaResult.Error && page is RaResult.Error) return page
        val p = (page as? RaResult.Ok)?.value
        return RaResult.Ok(
            RaGameTips(
                comments = p?.comments.orEmpty(),
                guideUrl = guide.okValue,
                commentsError = page.errorOrNull,
                guideError = guide.errorOrNull,
                nextOffset = p?.nextOffset ?: 0,
                hasMore = p?.hasMore ?: false,
                fetchedAt = System.currentTimeMillis()
            )
        )
    }

    /** More comments on a game's page (older than what's loaded). */
    suspend fun fetchGameComments(apiKey: String, gameId: Int, offset: Int): RaResult<RaCommentsPage> =
        comments(apiKey, gameId, type = 1, offset = offset)

    private suspend fun comments(apiKey: String, id: Int, type: Int, offset: Int): RaResult<RaCommentsPage> =
        raCall {
            val response = api.getComments(id, type, apiKey, count = PAGE, offset = offset)
            val raw = response.Results.orEmpty()
            val next = offset + raw.size
            RaCommentsPage(
                comments = raw.filter { !it.CommentText.isNullOrBlank() && !it.User.equals("Server", ignoreCase = true) },
                nextOffset = next,
                hasMore = raw.size >= PAGE && (response.Total == null || next < response.Total)
            )
        }

    suspend fun fetchGameInfo(apiKey: String, gameId: Int): RaGameInfo? = try {
        api.getGame(gameId, apiKey)
    } catch (e: Exception) {
        null
    }

    suspend fun consoles(apiKey: String): List<RaConsole> = try {
        api.consoleIds(apiKey)
    } catch (e: Exception) {
        emptyList()
    }

    /** Achievements unlocked in the last [minutesBack] minutes, across all
     * games — the "recently unlocked" feed, not scoped to one linked game. */
    /** Every unlock between [fromMs] and [toMs] (null when it couldn't be fetched). */
    suspend fun fetchEarnedBetween(username: String, apiKey: String, fromMs: Long, toMs: Long): List<RaRecentAchievement>? = try {
        api.getAchievementsEarnedBetween(username, apiKey, fromMs / 1000, toMs / 1000)
    } catch (e: Exception) {
        null
    }

    suspend fun fetchRecentAchievements(username: String, apiKey: String, minutesBack: Int): List<RaRecentAchievement> {
        return try {
            api.getUserRecentAchievements(username, apiKey, minutesBack)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
