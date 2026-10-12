package com.shelf.launcher.data

/**
 * Which SharedPreferences files travel between devices, and which don't.
 *
 * - GLOBAL: portable look and behaviour (theme, categories, favourites…).
 *   Backed up, exported, restored anywhere.
 * - DEVICE: only makes sense on the handheld it was made on (folders,
 *   emulator paths, screen layouts). Exported only when asked, and skipped
 *   on import when the backup came from another device.
 * - AUTH: sign-ins, tokens, sync connection state. Never exported, never
 *   backed up, never restored.
 * - INTERNAL: bookkeeping (crash guard, migrations, session). Never exported.
 *
 * To classify a new prefs file: add its name to one of the sets below. A
 * file that isn't listed is GLOBAL unless its name looks like a credential
 * or internal file (see [AUTH_PATTERN] / [INTERNAL_PATTERN]) — so a new
 * credential store called "…_credentials"/"…_encrypted" is safe by default.
 */
enum class SettingsScope { GLOBAL, DEVICE, AUTH, INTERNAL }

object SettingsScopes {
    /** Portable settings, listed for documentation and tests; unknown names default here anyway. */
    val GLOBAL_FILES = setOf(
        "appearance", "theme", "categories", "category_images", "collections", "favorites", "playtime",
        "recently_played", "ui_prefs", "screensaver", "scrape_options", "hidden_apps", "library_apps",
        "shortcuts", "physical_media", "metadata_cache", "art_cache", "art_overrides", "game_overrides",
        "layout_presets", "game_profiles", "ra_matches", "ra_icons", "screenshots", "screenshots_v2", "video_urls", "steam_games",
        // Platform artwork / maker / year, and your own platform order.
        "platform_media", "platform_order"
    )

    /** Tied to this handheld's folders, emulators, screens or storage. */
    val DEVICE_FILES = setOf(
        "screen_layouts", "emulator_configs", "library_settings", "gamenative_settings",
        "art_storage", "hero_upscale", "first_run", "device_profile", "steam_import", "perf_settings",
        // Gesture hints already shown on this screen; CPU/GPU clocks, charge limit and hidden accessories
        // of this handheld; where this device keeps its files; this device's web remote (and its PIN).
        "gesture_hint", "tuner", "charge_limit", "accessories", "app_storage", "web_remote", "bios_check",
        // The face-button layout learned from this handheld's own controller.
        "system_button_layout",
        // Volume per app (the apps installed here), per game, and the mixer's method / pinned apps.
        "mixer", "mixer_games", "mixer_meta",
        // Shortcuts other apps added here (their IDs only exist on this device).
        "pinned_shortcuts",
        // Picked intro-video files (their addresses only exist on this device).
        "platform_intro",
        // Your own platform / game music (copied into this device's storage).
        "music_overrides",
        // (update 91) The R1 panel's tile order / hidden tiles / use counts: kept per device (its device tiles differ).
        "r1_tiles",
        // The battery watcher's measurements: only true of this handheld.
        "battery_watch",
        // Which RetroAchievements account is the one playing on this device.
        "ra_account_slot",
        // Update 144: sleep mode for the radios (it needs this handheld's system rights).
        "sleep_radios"
    )

    /** Credentials and sign-in state (plus the plain-text names older versions fell back to). */
    val AUTH_FILES = setOf(
        "ra_credentials", "ra_credentials_encrypted", "ra_credentials_b", "ra_credentials_b_encrypted", "sgdb_settings", "sgdb_settings_encrypted",
        "igdb_credentials", "igdb_credentials_encrypted", "tgdb_credentials", "tgdb_credentials_encrypted",
        "screenscraper_credentials", "screenscraper_credentials_encrypted",
        "metadata_source", "metadata_source_encrypted", "rawg", "rawg_encrypted",
        "discord", "discord_encrypted", "spotify_encrypted", "Spotify", "cloud_sync", "cloud_sync_folders", "secure_store_flags",
        // Steam Web API key + SteamID.
        "steam_account",
        // RomM server + Client API token (update 93).
        "romm_credentials", "romm_credentials_encrypted"
    )

    /** Bookkeeping that must never be copied. */
    val INTERNAL_FILES = setOf(
        "session", "crash_guard", "migrations", "sync_status", "launch_context",
        // What this device has already shown as NEW, and disc serials read from this device's ROMs.
        "achievement_seen", "disc_serials",
        // Where the theme rotation is up to; sync history; what's running / was last launched here.
        "theme_rotation_state", "sync_log", "running_games", "emulator_guard", "quick_resume",
        // What a game's profile changed (buttons, screen rate, charge limit), to put back afterwards.
        "game_extras",
        // The handheld's own RGB settings held off by "Off", to put back exactly.
        "system_lights",
        // Which Voltcap tiles Android's Quick Settings menu holds here.
        "qs_tiles",
        // Update 144: this device's own sync id and the other devices' play (copied, two handhelds shared one id), the
        // RomM downloads Android is running here, and which radios sleep mode switched off here.
        "other_devices", "romm_downloads", "sleep_radios_state"
    )

    private val AUTH_PATTERN = Regex("(_encrypted$|credential|secret|token|password|oauth|api_?key|_auth$)", RegexOption.IGNORE_CASE)
    private val INTERNAL_PATTERN = Regex(
        "^(WebView|androidx\\.|com\\.google\\.|_?_?androidx_security|leakcanary|MULTIDEX|__)", RegexOption.IGNORE_CASE
    )

    /**
     * Keys inside otherwise-portable files that belong to this device. The
     * appearance file mixes both: lights and second-screen hardware, plus
     * paths and picked folders.
     */
    private val DEVICE_KEYS: Map<String, (String) -> Boolean> = mapOf(
        "appearance" to { key ->
            key.startsWith("lights_") || key.startsWith("second_") || key == "wallpaper_uri" ||
                key == "screenshot_folders" || key == "music_custom_name" || key == "music_custom_stamp"
        },
        // The game open right now on this handheld: brought to another device it would read as "a game is
        // running" there for hours (save downloads held back, play time added to the wrong device).
        "playtime" to { key -> key == "open_game" || key == "open_since" }
    )

    fun scopeOf(name: String): SettingsScope = when {
        name in INTERNAL_FILES || INTERNAL_PATTERN.containsMatchIn(name) -> SettingsScope.INTERNAL
        name in AUTH_FILES || AUTH_PATTERN.containsMatchIn(name) -> SettingsScope.AUTH
        name in DEVICE_FILES -> SettingsScope.DEVICE
        else -> SettingsScope.GLOBAL
    }

    /** Device-specific keys inside a file that is otherwise [SettingsScope.GLOBAL] (e.g. some appearance keys). */
    fun isDeviceKey(file: String, key: String): Boolean = DEVICE_KEYS[file]?.invoke(key) == true

    /** True when [file] has any per-key device split. */
    fun hasDeviceKeys(file: String): Boolean = file in DEVICE_KEYS

    /** The scope of one entry: a device key inside a global file counts as DEVICE. */
    fun scopeOf(file: String, key: String): SettingsScope {
        val scope = scopeOf(file)
        return if (scope == SettingsScope.GLOBAL && isDeviceKey(file, key)) SettingsScope.DEVICE else scope
    }

    /** Friendly service name for an AUTH file, or null when it isn't a sign-in (e.g. metadata_source). */
    fun serviceOf(file: String): String? = when (file.removeSuffix("_encrypted")) {
        "ra_credentials" -> "RetroAchievements"
        "ra_credentials_b" -> "RetroAchievements (second account)"
        "sgdb_settings" -> "SteamGridDB"
        "igdb_credentials" -> "IGDB"
        "tgdb_credentials" -> "TheGamesDB"
        "screenscraper_credentials" -> "ScreenScraper"
        "rawg" -> "RAWG"
        "discord" -> "Discord"
        "steam_account" -> "Steam"
        "romm_credentials" -> "RomM"
        "cloud_sync" -> "Google Drive"
        else -> null
    }
}
