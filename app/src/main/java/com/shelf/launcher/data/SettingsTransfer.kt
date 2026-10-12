package com.shelf.launcher.data

import android.content.Context
import org.json.JSONObject

/**
 * Moving settings between devices: Google Drive (the existing sign-in), a nearby device with a PIN
 * (LocalTransfer) or a file. Whatever the source, nothing is applied straight away: [preview] lists
 * what would change, grouped (Appearance, Themes, Layout…), each group can be left out, and only then
 * [apply] writes it (with the usual safety snapshot). Device-only settings never come along
 * (SettingsScopes), sign-ins never move.
 */
object SettingsTransfer {

    enum class Group(val label: String) {
        ACCOUNTS("Accounts & sign-ins"),
        APPEARANCE("Appearance"),
        THEMES("Themes"),
        LAYOUT("Layout"),
        CATEGORIES("Categories"),
        ACHIEVEMENTS("Achievements"),
        SAVE_VAULT("Save Vault"),
        MUSIC("Music"),
        CONTROLS("Controls"),
        OTHER("Other")
    }

    /** Which group a setting belongs to (prefs file + key). One place, easy to extend. */
    fun groupOf(file: String, key: String): Group {
        val k = key.lowercase()
        if (file == "theme") return Group.THEMES
        if (file == "categories" || file == "category_images" || file == "collections") return Group.CATEGORIES
        if (file == "shortcuts") return Group.CONTROLS
        if (file == "save_capsules" || file == "save_explorer") return Group.SAVE_VAULT
        if (file == "ra_matches" || file == "ra_icons" || file == "achievement_seen") return Group.ACHIEVEMENTS
        if (file == "music_theme_palette") return Group.MUSIC
        if (file != "appearance" && file != "voltcap_features" && file != "ui_prefs") return Group.OTHER
        return when {
            k.startsWith("theme_") || k == "favorite_themes" || k.startsWith("scene_") || k == "category_scenes" ||
                k == "background_style" || k == "wallpaper_uri" || k.startsWith("dynamic_") || k.startsWith("custom_") -> Group.THEMES
            k.startsWith("save_") -> Group.SAVE_VAULT
            k.startsWith("ra_") || k.startsWith("achievement") || k.startsWith("badge_") || k == "backdrop_achievements" ||
                k.startsWith("unlock") || k.contains("trophy") || k.contains("mastered") || k == "show_rarity" ||
                k == "completion_celebration" || k == "animate_only_new" || k.startsWith("cover_achievements") || k == "progress_on_banner" ||
                k == "progress_above" -> Group.ACHIEVEMENTS
            k == "widget_positions" || k.startsWith("dock_") || k.startsWith("tile_") || k.startsWith("hero_") || k == "home_layout" ||
                k.startsWith("card_") || k.startsWith("focus") || k == "console_name_pin" || k.endsWith("_side") || k == "info_spacing" ||
                k == "safe_zone_extra" || k.startsWith("shortcut_") || k == "density" || k == "text_scale" -> Group.LAYOUT
            k.startsWith("category") || k == "start_category" || k.endsWith("_collections") || k == "play_status" -> Group.CATEGORIES
            k.startsWith("music") || k.startsWith("sound") || k == "theme_sounds" || k.startsWith("lyrics") || k.startsWith("mini_player") ||
                k.startsWith("video_sound") -> Group.MUSIC
            k.startsWith("hotkeys") || k == "music_buttons" || k.startsWith("button_") || k == "show_legend" || k.startsWith("haptic") -> Group.CONTROLS
            else -> Group.APPEARANCE
        }
    }

    /** One group in the preview: how many settings would change, and a few of them by name. */
    data class GroupPreview(val group: Group, val changes: Int, val examples: List<String>)

    data class Preview(
        val groups: List<GroupPreview>,
        val info: BackupInfo,
        val root: JSONObject,
        /**
         * Sign-ins the source device had that this file doesn't carry and this device doesn't have yet
         * (the file was made without "Include accounts", or by a Voltcap from before accounts could go along).
         */
        val missingAccounts: List<String> = emptyList(),
        /** Of those, the ones that never travel in any file (each device signs in itself). */
        val perDevice: List<String> = emptyList()
    )

    /**
     * What importing [root] would change on this device, grouped. Settings that are already the same
     * aren't counted; device-only and sign-in settings are never part of it.
     */
    fun preview(context: Context, root: JSONObject): Preview {
        val info = Backup.describeDocument(context, root)
        val plan = Backup.plan(root, importDevice = false)
        val counts = LinkedHashMap<Group, MutableList<String>>()
        plan.forEach { (file, values) ->
            val current = context.getSharedPreferences(file, Context.MODE_PRIVATE).all
            values.forEach { (key, entry) ->
                val now = current[key]
                val incoming = entry.opt("v")
                val same = when (now) {
                    null -> false
                    is Set<*> -> (incoming as? org.json.JSONArray)?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() == now.map { it.toString() }.toSet() } == true
                    is Float -> (incoming as? Number)?.toFloat() == now
                    is Long -> (incoming as? Number)?.toLong() == now
                    is Int -> (incoming as? Number)?.toInt() == now
                    else -> incoming?.toString() == now.toString()
                }
                if (!same) counts.getOrPut(groupOf(file, key)) { mutableListOf() } += readable(key)
            }
        }
        val accounts = Backup.accountServices(root)
        if (accounts.isNotEmpty()) counts[Group.ACCOUNTS] = accounts.toMutableList()
        val groups = Group.values().mapNotNull { g ->
            counts[g]?.let { GroupPreview(g, it.size, it.distinct().take(4)) }
        }
        // What the other device was signed in to but this file can't sign in here.
        val signedHere = Backup.signedInServices(context).toSet()
        val missing = info.signedInServices.filter { it !in accounts && it !in signedHere }.distinct()
        return Preview(groups, info, root, missing, missing.filter { it in Backup.PER_DEVICE_SERVICES })
    }

    /** "theme_rotation_minutes" → "Theme rotation minutes". */
    private fun readable(key: String): String =
        key.replace(Regex("_v\\d+$"), "").replace('_', ' ').replaceFirstChar { it.uppercase() }

    /** Applies the preview's settings, leaving the [excluded] groups exactly as they are here. */
    fun apply(context: Context, preview: Preview, excluded: Set<Group>): ImportReport =
        Backup.applyDocument(
            context, preview.root, importDeviceSetup = false, reason = "before import",
            skip = { file, key -> groupOf(file, key) in excluded },
            importAccounts = Group.ACCOUNTS !in excluded
        ).also {
            // Features are Compose state: read the imported values back in.
            Features.reload(context)
        }

    // ---- Google Drive ----

    private const val DRIVE_FOLDER = "Voltcap"
    private const val DRIVE_FILE = "voltcap-settings.json"

    /** Uploads this device's portable settings to the user's Drive (Voltcap/voltcap-settings.json). Blocking: IO. */
    fun sendToDrive(context: Context, includeAccounts: Boolean = true) {
        val drive = DriveClient(context)
        val folder = drive.folder(DRIVE_FOLDER)
        val existing = drive.list(folder).firstOrNull { !it.isFolder && it.name == DRIVE_FILE }
        drive.uploadBytes(Backup.exportText(context, "drive", includeDeviceSetup = false, includeAccounts = includeAccounts).toByteArray(), DRIVE_FILE, folder, existing?.id)
    }

    /** Downloads the settings saved on Drive (from any device) for the Import Preview. Blocking: IO. */
    fun fetchFromDrive(context: Context): JSONObject {
        val drive = DriveClient(context)
        val folder = drive.folder(DRIVE_FOLDER)
        val file = drive.list(folder).firstOrNull { !it.isFolder && it.name == DRIVE_FILE }
            ?: error("There are no Voltcap settings on your Google Drive yet. Send them from the other device first.")
        return Backup.readDocument(String(drive.downloadBytes(file.id)))
    }
}
