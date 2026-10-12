package com.shelf.launcher.data

import android.content.Context
import com.google.gson.Gson
import java.io.File

/** Highest award on a game, best first. */
enum class RaAward(val label: String) {
    MASTERED("Mastered"),
    COMPLETED("Completed"),
    BEATEN_HARDCORE("Beaten"),
    BEATEN("Beaten (softcore)");

    companion object {
        fun from(kind: String?): RaAward? = when (kind?.lowercase()) {
            "mastered" -> MASTERED
            "completed" -> COMPLETED
            "beaten-hardcore" -> BEATEN_HARDCORE
            "beaten-softcore" -> BEATEN
            else -> null
        }
    }
}

/** Totals for the profile page, worked out from the completion list. */
data class RaProfileStats(
    val gamesPlayed: Int = 0,
    val mastered: Int = 0,
    val beaten: Int = 0,
    val achievementsEarned: Int = 0,
    val achievementsPossible: Int = 0
) {
    companion object {
        fun from(entries: List<RaCompletionEntry>): RaProfileStats {
            val awards = entries.map { RaAward.from(it.HighestAwardKind) }
            return RaProfileStats(
                gamesPlayed = entries.size,
                mastered = awards.count { it == RaAward.MASTERED || it == RaAward.COMPLETED },
                beaten = awards.count { it != null },
                achievementsEarned = entries.sumOf { maxOf(it.NumAwarded ?: 0, it.NumAwardedHardcore ?: 0) },
                achievementsPossible = entries.sumOf { it.MaxPossible ?: 0 }
            )
        }
    }
}

/**
 * Everything fetched from RetroAchievements, kept on the device so profile,
 * progress, awards and achievement lists show instantly and offline. Refreshed
 * whenever the app is online and connected; clearable in Settings.
 */
class RaDataCache(context: Context) {
    private val file = AppStorage.file(context, "Data", "ra_data.json", File(context.filesDir, "ra_data.json"))
    private val gson = Gson()

    /** When one game's achievement list was fetched, and for which RA game.
     * Fields nullable because Gson ignores Kotlin defaults. */
    data class DetailsMeta(val gameId: Int? = null, val fetchedAt: Long? = null)

    data class Saved(
        val profile: RaUserProfile? = null,
        val completion: List<RaCompletionEntry>? = null,
        val progress: Map<String, RaGameProgress>? = null,
        val details: Map<String, List<RaAchievementDetail>>? = null,
        /** Version of the layout of [details]; a different one means it is dropped on load. */
        val schemaVersion: Int? = null,
        val detailsMeta: Map<String, DetailsMeta>? = null
    )

    @Synchronized
    fun load(): Saved {
        val saved = runCatching { gson.fromJson(file.readText(), Saved::class.java) }.getOrNull() ?: return Saved(schemaVersion = SCHEMA)
        // Lists saved by an older build (before the `type` field / fetch times
        // existed) have no missable info; keeping them would hide missables until
        // a refresh happened to succeed. Drop them so they're fetched again.
        return if (saved.schemaVersion == SCHEMA) saved
        else saved.copy(details = null, detailsMeta = null, schemaVersion = SCHEMA)
    }

    @Synchronized
    fun update(transform: (Saved) -> Saved) {
        runCatching {
            val json = gson.toJson(transform(load()).copy(schemaVersion = SCHEMA))
            // Write-then-rename so a crash mid-save can't leave a half-written file.
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json)
            if (!tmp.renameTo(file)) { file.writeText(json); tmp.delete() }
        }
    }

    @Synchronized
    fun clear() {
        file.delete()
    }

    companion object {
        /** Bump when the shape or meaning of cached details changes. */
        const val SCHEMA = 2

        /** Achievement lists older than this are refreshed whenever they're opened while online. */
        const val DETAILS_TTL_MS = 24L * 60 * 60 * 1000
    }
}
