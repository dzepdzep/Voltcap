package com.shelf.launcher.data

import android.content.Context
import com.google.gson.Gson

/*
 * Per-game / per-console overrides, with "inherit" as the default.
 *
 * Every field is nullable: null means "not set here, ask the next level up".
 * Order, most specific first:  this game › its console (category) › the global setting.
 * Screen/display rules, app display and artwork keep their own stores; this one only
 * holds what had no home yet. Nothing here is device-specific (display ids are never
 * stored — the existing screen rules own those), so the file travels with backups.
 */

enum class LaunchExtraType(val label: String) { STRING("Text"), INT("Number"), BOOL("Yes/No") }

/** One extra put on the emulator's launch intent. */
data class LaunchExtra(val key: String, val value: String, val type: LaunchExtraType = LaunchExtraType.STRING)

data class GameProfile(
    /** Sync this game's saves around play; null = follow the Cloud sync setting. */
    val syncSaves: Boolean? = null,
    /** Second-screen page while this game is focused or running. */
    val secondScreenPage: SecondPage? = null,
    /** Stored only — read by the performance overlay. */
    val perfOverlay: Boolean? = null,
    /** Game level only (a console-wide extra would be wrong for most emulators). */
    val launchExtras: List<LaunchExtra> = emptyList(),
    /** Follow the walkthrough automatically while playing; null = on. */
    val guideAutoLocate: Boolean? = null
) {
    fun isEmpty() = syncSaves == null && secondScreenPage == null && perfOverlay == null &&
        launchExtras.isEmpty() && guideAutoLocate == null
}

enum class ProfileSource(val label: String) { GAME("this game"), CATEGORY("console"), GLOBAL("default") }

data class Resolved<T>(val value: T, val source: ProfileSource)

data class ResolvedProfile(
    val syncSaves: Resolved<Boolean>,
    val secondScreenPage: Resolved<SecondPage>,
    val perfOverlay: Resolved<Boolean>,
    val guideAutoLocate: Resolved<Boolean>,
    val launchExtras: List<LaunchExtra>
)

/** Which profile fields a "Apply to all <console> games" / reset touches. */
enum class ProfileField { SYNC_SAVES, SECOND_PAGE, PERF_OVERLAY, GUIDE_AUTO_LOCATE, LAUNCH_EXTRAS }

class GameProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("game_profiles", Context.MODE_PRIVATE)
    private val gson = Gson()

    private fun read(key: String): GameProfile = try {
        prefs.getString(key, null)?.let { gson.fromJson(it, GameProfile::class.java) }
            ?.let { it.copy(launchExtras = it.launchExtras ?: emptyList()) } // Gson skips Kotlin defaults
            ?: GameProfile()
    } catch (_: Exception) { GameProfile() }

    private fun write(key: String, p: GameProfile) {
        prefs.edit().apply { if (p.isEmpty()) remove(key) else putString(key, gson.toJson(p)) }.apply()
    }

    fun game(stableKey: String): GameProfile = read("g:$stableKey")
    fun category(platform: String): GameProfile = read("c:console:$platform")

    fun updateGame(stableKey: String, change: (GameProfile) -> GameProfile) =
        write("g:$stableKey", change(game(stableKey)))

    fun updateCategory(platform: String, change: (GameProfile) -> GameProfile) =
        write("c:console:$platform", change(category(platform)))

    fun clearGame(stableKey: String) { prefs.edit().remove("g:$stableKey").apply() }

    /**
     * game › console › global. [globalSaves]/[globalPage] come from the existing settings
     * (Cloud sync, appearance); perf overlay defaults to the global Performance setting, guide auto-locate on.
     */
    fun resolve(game: Game, globalSaves: Boolean, globalPage: SecondPage, globalPerf: Boolean = false): ResolvedProfile {
        val g = game(game.stableKey)
        val c = category(game.platform)
        fun <T : Any> pick(gv: T?, cv: T?, def: T): Resolved<T> = when {
            gv != null -> Resolved(gv, ProfileSource.GAME)
            cv != null -> Resolved(cv, ProfileSource.CATEGORY)
            else -> Resolved(def, ProfileSource.GLOBAL)
        }
        return ResolvedProfile(
            syncSaves = pick(g.syncSaves, c.syncSaves, globalSaves),
            secondScreenPage = pick(g.secondScreenPage, c.secondScreenPage, globalPage),
            perfOverlay = pick(g.perfOverlay, c.perfOverlay, globalPerf),
            guideAutoLocate = pick(g.guideAutoLocate, c.guideAutoLocate, true),
            launchExtras = g.launchExtras
        )
    }
}

/** Turning the user's text extras into intent extras, skipping what can't work. */
object LaunchExtras {
    /** Valid extras, plus one plain-English note per skipped one. */
    fun validate(extras: List<LaunchExtra>): Pair<List<LaunchExtra>, List<String>> {
        val ok = mutableListOf<LaunchExtra>()
        val notes = mutableListOf<String>()
        for (e in extras) {
            val key = e.key.trim()
            when {
                key.isEmpty() -> notes += "an extra with no name"
                e.type == LaunchExtraType.INT && e.value.trim().toIntOrNull() == null ->
                    notes += "\"$key\" (\"${e.value}\" isn't a whole number)"
                e.type == LaunchExtraType.BOOL && parseBool(e.value) == null ->
                    notes += "\"$key\" (use true or false)"
                else -> ok += e.copy(key = key)
            }
        }
        return ok to notes
    }

    fun parseBool(v: String): Boolean? = when (v.trim().lowercase()) {
        "true", "yes", "on", "1" -> true
        "false", "no", "off", "0" -> false
        else -> null
    }

    /** Call with [validate]d extras only. */
    fun apply(intent: android.content.Intent, extras: List<LaunchExtra>) {
        for (e in extras) when (e.type) {
            LaunchExtraType.STRING -> intent.putExtra(e.key, e.value)
            LaunchExtraType.INT -> e.value.trim().toIntOrNull()?.let { intent.putExtra(e.key, it) }
            LaunchExtraType.BOOL -> parseBool(e.value)?.let { intent.putExtra(e.key, it) }
        }
    }
}
