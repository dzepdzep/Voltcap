package com.shelf.launcher.data.guide

/**
 * Data model for walkthroughs. Guides are parsed from user-visible sources (RetroAchievements'
 * public guides repo, or the user's own local files) and cached as JSON, so every field here
 * is plain and Gson-friendly.
 */

/** One heading-delimited part of a guide. */
data class GuideSection(
    /** Stable slug of the heading path, e.g. "pokemon-red/viridian-forest" (suffixed -2, -3 on duplicates). */
    val id: String,
    /** Heading depth: 1 = top level. 0 = text before the first heading. */
    val level: Int,
    val title: String,
    /** Titles of the ancestors plus this one. */
    val path: List<String>,
    /** Plain / markdown-lite body (images and HTML removed; list and link text kept). */
    val text: String,
    /** Position in reading order, 0-based. */
    val order: Int,
    /** RetroAchievements achievement IDs linked from this section's raw text. */
    val mentionedAchievementIds: List<Int> = emptyList()
)

data class Guide(
    /** Provider-specific stable id (also the cache key). */
    val id: String,
    val gameKey: String,
    val title: String,
    /** "retroachievements" or "local". */
    val source: String,
    val sections: List<GuideSection>,
    /** When the text was fetched/parsed (epoch ms). */
    val fetchedAt: Long,
    /** Credit line to show with the guide. */
    val attribution: String,
    val sourceUrl: String?
)

/** What the providers need to know about a game to find its guides. */
data class GuideGameRef(
    val gameKey: String,
    val title: String,
    /** RetroAchievements game id when the game is matched (null if not). */
    val raGameId: Int? = null,
    /** GuideURL from RA's API_GetGameExtended (see RaGameTips.guideUrl); null when unknown/none. */
    val guideUrl: String? = null,
    /** Voltcap's platform key (finds the right console's page on guide sites). */
    val platform: String? = null
)

/** A guide that could be opened for a game. */
data class GuideCandidate(
    /** Self-describing id ("ra:", "local:", "ext:" + base64 of [ref]) so a guide can be loaded from the id alone. */
    val id: String,
    val providerId: String,
    val title: String,
    /** e.g. the file name or "RetroAchievements guide". */
    val subtitle: String? = null,
    /** Page to credit / open in a browser. */
    val sourceUrl: String? = null,
    val kind: Kind,
    /** Raw URL (remote), or SAF uri / file path (local). */
    val ref: String,
    val gameKey: String = "",
    val raGameId: Int? = null
) {
    enum class Kind {
        /** Fetchable/readable text; parsed into sections. */
        TEXT,
        /** Only a link: the UI opens the browser. Never scraped. */
        EXTERNAL_LINK
    }
}

interface GuideProvider {
    val id: String
    suspend fun find(game: GuideGameRef): List<GuideCandidate>
    suspend fun load(candidate: GuideCandidate): Guide?
}

/** Position lookups on a guide (kept out of the class so Gson stays simple). */
fun Guide.orderOf(sectionId: String?): Int =
    if (sectionId == null) -1 else sections.indexOfFirst { it.id == sectionId }

fun Guide.section(sectionId: String?): GuideSection? =
    if (sectionId == null) null else sections.firstOrNull { it.id == sectionId }
