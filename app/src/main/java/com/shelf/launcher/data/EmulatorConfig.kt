package com.shelf.launcher.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

enum class LaunchMode {
    /** Generic ACTION_VIEW on the ROM file — whatever's registered to open that
     * file type handles it (may show a chooser). Zero configuration, but not
     * every emulator registers file associations. */
    SYSTEM_CHOOSER,

    /** Explicit intent at a specific app/activity, e.g. RetroArch's frontend-
     * automation entry point. One tap straight into the right emulator + core,
     * but the package/activity/extras have to match that emulator's build. */
    EXPLICIT_INTENT
}

/** How the game's location is handed to the emulator. Emulators differ:
 * some read plain file paths, others only Android storage links. */
enum class RomFormat(val label: String) {
    AUTO("Automatic"),
    FILE_PATH("File path"),
    DOCUMENT_URI("Storage link"),
    SHARED_URI("Shared file link")
}

data class EmulatorConfig(
    val platform: String,
    val mode: LaunchMode = LaunchMode.SYSTEM_CHOOSER,
    val packageName: String? = null,
    val activityClassName: String? = null,
    val romExtraKey: String? = null,      // e.g. "ROM" for RetroArch
    val coreExtraKey: String? = null,     // e.g. "LIBRETRO" for RetroArch
    val coreExtraValue: String? = null,   // absolute path to the libretro core .so
    /** Null = automatic (what the emulator's launch profile expects). */
    val romFormat: RomFormat? = null,
    /** The folder this emulator was given access to (for storage links).
     * Null = Shelf's own ROM folder. */
    val accessFolder: String? = null
)

/**
 * Thin SharedPreferences store for per-platform emulator configs. Stored as
 * JSON, so new fields (like romFormat) simply read as null from older saves.
 */
class EmulatorSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("emulator_configs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, EmulatorConfig>>() {}.type

    fun loadAll(): Map<String, EmulatorConfig> {
        val json = prefs.getString(KEY, null) ?: return emptyMap()
        return runCatching { gson.fromJson<Map<String, EmulatorConfig>>(json, mapType) }
            .getOrDefault(emptyMap())
    }

    fun save(platform: String, config: EmulatorConfig) {
        val updated = loadAll() + (platform to config)
        prefs.edit().putString(KEY, gson.toJson(updated)).apply()
    }

    companion object {
        private const val KEY = "configs_json"
    }
}
